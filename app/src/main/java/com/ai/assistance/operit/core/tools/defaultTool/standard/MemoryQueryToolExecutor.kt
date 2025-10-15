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
 * Executes queries against the AI's memory graph.
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
        val query = tool.parameters.find { it.name == "query" }?.value ?: ""
        if (query.isBlank()) {
            return@runBlocking ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Query parameter cannot be empty.")
        }

        Log.d(TAG, "Executing memory query: $query")

        return@runBlocking try {
            val results = memoryRepository.searchMemories(query) // 改用更强大的混合搜索
            
            val formattedResult = buildResultData(results.take(5), query) // 取前5个结果
            Log.d(TAG, "Memory query result for '$query':\n$formattedResult")
            ToolResult(toolName = tool.name, success = true, result = formattedResult)
        } catch (e: Exception) {
            Log.e(TAG, "Memory query failed", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Failed to execute memory query: ${e.message}")
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