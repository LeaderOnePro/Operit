package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.MemoryQueryResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.permissions.ToolCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*
import com.ai.assistance.operit.data.preferences.preferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes queries against the AI's memory graph and manages user preferences.
 */
class MemoryQueryToolExecutor(private val context: Context) : ToolExecutor {

    companion object {
        private const val TAG = "MemoryQueryToolExecutor"
    }

    private val memoryRepository by lazy {
        val profileId = runBlocking { preferencesManager.activeProfileIdFlow.first() }
        MemoryRepository(context, profileId)
    }

    override fun invoke(tool: AITool): ToolResult = runBlocking {
        return@runBlocking when (tool.name) {
            "query_memory" -> executeQueryMemory(tool)
            "get_memory_by_title" -> executeGetMemoryByTitle(tool)
            "create_memory" -> executeCreateMemory(tool)
            "update_memory" -> executeUpdateMemory(tool)
            "delete_memory" -> executeDeleteMemory(tool)
            "update_user_preferences" -> executeUpdateUserPreferences(tool)
            else -> ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Unknown tool: ${tool.name}"
            )
        }
    }

    private suspend fun executeQueryMemory(tool: AITool): ToolResult {
        val query = tool.parameters.find { it.name == "query" }?.value ?: ""
        if (query.isBlank()) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Query parameter cannot be empty.")
        }

        Log.d(TAG, "Executing memory query: $query")

        return try {
            val results = memoryRepository.searchMemories(query) // 改用更强大的混合搜索
            
            val formattedResult = buildResultData(results.take(5), query) // 取前5个结果
            Log.d(TAG, "Memory query result for '$query':\n$formattedResult")
            ToolResult(toolName = tool.name, success = true, result = formattedResult)
        } catch (e: Exception) {
            Log.e(TAG, "Memory query failed", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Failed to execute memory query: ${e.message}")
        }
    }

    private suspend fun executeGetMemoryByTitle(tool: AITool): ToolResult {
        val title = tool.parameters.find { it.name == "title" }?.value
        if (title.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "title parameter is required"
            )
        }

        Log.d(TAG, "Getting memory by title: $title")

        return try {
            val memory = memoryRepository.findMemoryByTitle(title)
            if (memory == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Memory not found with title: $title"
                )
            }

            val formattedResult = buildResultData(listOf(memory), title)
            Log.d(TAG, "Found memory by title '$title':\n$formattedResult")
            ToolResult(
                toolName = tool.name,
                success = true,
                result = formattedResult
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get memory by title", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to get memory by title: ${e.message}"
            )
        }
    }

    private suspend fun executeCreateMemory(tool: AITool): ToolResult {
        val title = tool.parameters.find { it.name == "title" }?.value ?: ""
        val content = tool.parameters.find { it.name == "content" }?.value ?: ""
        
        if (title.isBlank() || content.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Both title and content parameters are required"
            )
        }

        Log.d(TAG, "Creating memory: $title")

        return try {
            val contentType = tool.parameters.find { it.name == "content_type" }?.value ?: "text/plain"
            val source = tool.parameters.find { it.name == "source" }?.value ?: "ai_created"
            val folderPath = tool.parameters.find { it.name == "folder_path" }?.value ?: ""
            
            val memory = memoryRepository.createMemory(
                title = title,
                content = content,
                contentType = contentType,
                source = source,
                folderPath = folderPath
            )
            
            if (memory != null) {
                val message = "Successfully created memory: '$title' (UUID: ${memory.uuid})"
                Log.d(TAG, message)
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(message)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to create memory"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create memory", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to create memory: ${e.message}"
            )
        }
    }

    private suspend fun executeUpdateMemory(tool: AITool): ToolResult {
        val oldTitle = tool.parameters.find { it.name == "old_title" }?.value
        
        if (oldTitle.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "old_title parameter is required to identify the memory"
            )
        }

        Log.d(TAG, "Updating memory with title: $oldTitle")

        return try {
            val memory = memoryRepository.findMemoryByTitle(oldTitle)
            if (memory == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Memory not found with title: $oldTitle"
                )
            }

            // 获取要更新的字段，如果没有提供则使用原值
            val newTitle = tool.parameters.find { it.name == "new_title" }?.value ?: memory.title
            val newContent = tool.parameters.find { it.name == "content" }?.value ?: memory.content
            val newContentType = tool.parameters.find { it.name == "content_type" }?.value ?: memory.contentType
            val newSource = tool.parameters.find { it.name == "source" }?.value ?: memory.source
            val newCredibility = tool.parameters.find { it.name == "credibility" }?.value?.toFloatOrNull() ?: memory.credibility
            val newImportance = tool.parameters.find { it.name == "importance" }?.value?.toFloatOrNull() ?: memory.importance
            val newFolderPath = tool.parameters.find { it.name == "folder_path" }?.value ?: memory.folderPath
            val tagsParam = tool.parameters.find { it.name == "tags" }?.value
            val newTags = tagsParam?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            
            val updatedMemory = memoryRepository.updateMemory(
                memory = memory,
                newTitle = newTitle,
                newContent = newContent,
                newContentType = newContentType,
                newSource = newSource,
                newCredibility = newCredibility,
                newImportance = newImportance,
                newFolderPath = newFolderPath,
                newTags = newTags
            )
            
            if (updatedMemory != null) {
                val message = "Successfully updated memory from '$oldTitle' to '$newTitle'"
                Log.d(TAG, message)
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(message)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to update memory"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update memory", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to update memory: ${e.message}"
            )
        }
    }

    private suspend fun executeDeleteMemory(tool: AITool): ToolResult {
        val title = tool.parameters.find { it.name == "title" }?.value
        
        if (title.isNullOrBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "title parameter is required to identify the memory"
            )
        }

        Log.d(TAG, "Deleting memory with title: $title")

        return try {
            val memory = memoryRepository.findMemoryByTitle(title)
            if (memory == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Memory not found with title: $title"
                )
            }

            val deleted = memoryRepository.deleteMemory(memory.id)
            
            if (deleted) {
                val message = "Successfully deleted memory: '$title'"
                Log.d(TAG, message)
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(message)
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to delete memory"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete memory", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to delete memory: ${e.message}"
            )
        }
    }

    private suspend fun executeUpdateUserPreferences(tool: AITool): ToolResult {
        Log.d(TAG, "Executing update user preferences")

        return try {
            // 从参数中提取各项偏好设置
            val birthDate = tool.parameters.find { it.name == "birth_date" }?.value?.toLongOrNull()
            val gender = tool.parameters.find { it.name == "gender" }?.value
            val personality = tool.parameters.find { it.name == "personality" }?.value
            val identity = tool.parameters.find { it.name == "identity" }?.value
            val occupation = tool.parameters.find { it.name == "occupation" }?.value
            val aiStyle = tool.parameters.find { it.name == "ai_style" }?.value

            // 检查是否至少有一个参数
            if (birthDate == null && gender == null && personality == null && 
                identity == null && occupation == null && aiStyle == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "At least one preference parameter must be provided"
                )
            }

            // 更新用户偏好
            withContext(Dispatchers.IO) {
                preferencesManager.updateProfileCategory(
                    birthDate = birthDate,
                    gender = gender,
                    personality = personality,
                    identity = identity,
                    occupation = occupation,
                    aiStyle = aiStyle
                )
            }

            val updatedFields = mutableListOf<String>()
            birthDate?.let { updatedFields.add("birth_date") }
            gender?.let { updatedFields.add("gender") }
            personality?.let { updatedFields.add("personality") }
            identity?.let { updatedFields.add("identity") }
            occupation?.let { updatedFields.add("occupation") }
            aiStyle?.let { updatedFields.add("ai_style") }

            val message = "Successfully updated user preferences: ${updatedFields.joinToString(", ")}"
            Log.d(TAG, message)
            
            ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(message)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update user preferences", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to update user preferences: ${e.message}"
            )
        }
    }

    private suspend fun buildResultData(memories: List<Memory>, query: String): MemoryQueryResultData = withContext(Dispatchers.IO) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val memoryInfos = memories.map { memory ->
            val content: String
            if (memory.isDocumentNode) {
                // 对于文档节点，执行“二次探查”，获取匹配的区块内容
                Log.d(TAG, "Memory result is a document ('${memory.title}'). Fetching specific matching chunks for query: '$query'")
                val matchingChunks = memoryRepository.searchChunksInDocument(memory.id, query)

                content = if (matchingChunks.isNotEmpty()) {
                    // 将匹配的区块内容拼接起来
                    "Matching content from document '${memory.title}':\n" +
                    matchingChunks.take(5) // 最多取5个最相关的区块
                        .joinToString("\n---\n") { chunk -> chunk.content }
                } else {
                    // 如果二次探cha未找到（理论上很少见，因为全局搜索已经认为它相关），提供一个回退信息
                    "Document '${memory.title}' was found, but no specific chunks strongly matched the query '$query'. The document's general content is: ${memory.content}"
                }
            } else {
                // 对于普通记忆，直接使用其内容
                content = memory.content
            }

            MemoryQueryResultData.MemoryInfo(
                title = memory.title,
                content = content, // 使用新生成的、包含具体区块的内容
                source = memory.source,
                tags = memory.tags.map { it.name },
                createdAt = sdf.format(memory.createdAt)
            )
        }
        MemoryQueryResultData(memories = memoryInfos)
    }


    override fun validateParameters(tool: AITool): ToolValidationResult {
        val query = tool.parameters.find { it.name == "query" }?.value
        if (query.isNullOrBlank()) {
            return ToolValidationResult(valid = false, errorMessage = "Missing or empty required parameter: query")
        }
        return ToolValidationResult(valid = true)
    }

    override fun getCategory(): ToolCategory {
        return ToolCategory.FILE_READ
    }
} 