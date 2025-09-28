package com.ai.assistance.operit.api.chat.library

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.AIService
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.services.EmbeddingService
import com.ai.assistance.operit.util.ChatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import com.ai.assistance.operit.data.model.FunctionType
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.*
/**
 * 问题库管理类 - 提供分析对话内容并存储为结构化记忆图谱的功能。
 */
object ProblemLibrary {
    private const val TAG = "ProblemLibrary"
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var apiPreferences: ApiPreferences? = null

    @Volatile private var isInitialized = false

    // --- Data classes for parsing the new structured analysis ---
    private data class ParsedLink(val sourceTitle: String, val targetTitle: String, val type: String, val description: String)
    private data class ParsedEntity(val title: String, val content: String, val tags: List<String>, val aliasFor: String?)
    private data class ParsedUpdate(val titleToUpdate: String, val newContent: String, val reason: String)
    private data class ParsedAnalysis(
        val mainProblem: ParsedEntity?,
        val extractedEntities: List<ParsedEntity> = emptyList(),
        val links: List<ParsedLink> = emptyList(),
        val updatedEntities: List<ParsedUpdate> = emptyList(),
        val userPreferences: String = ""
    )


    fun initialize(context: Context) {
        synchronized(ProblemLibrary::class.java) {
            if (isInitialized) return
            Log.d(TAG, "正在初始化 ProblemLibrary")
            apiPreferences = ApiPreferences.getInstance(context.applicationContext)
            isInitialized = true
            Log.d(TAG, "ProblemLibrary 初始化完成")
        }
    }

    fun saveProblemAsync(
            context: Context,
            toolHandler: AIToolHandler,
            conversationHistory: List<Pair<String, String>>,
            content: String,
            aiService: AIService
    ) {
        ensureInitialized(context)

        coroutineScope.launch {
            try {
                saveProblem(
                    context,
                    toolHandler,
                    conversationHistory,
                    content,
                    aiService
                )
            } catch (e: Exception) {
                Log.e(TAG, "保存问题记录失败", e)
            }
        }
    }

    private fun ensureInitialized(context: Context) {
        if (!isInitialized) {
            initialize(context)
        }
    }

    /**
     * Analyzes conversation and saves it as a structured Memory graph.
     */
    private suspend fun saveProblem(
            context: Context,
            toolHandler: AIToolHandler,
            conversationHistory: List<Pair<String, String>>,
            content: String,
            aiService: AIService
    ) {
        val profileId = preferencesManager.activeProfileIdFlow.first()
        val memoryRepository = MemoryRepository(context, profileId)

        // Process conversation history: remove system messages and clean user messages
        val processedHistory = conversationHistory
            .filter { it.first != "system" }
            .map { (role, msgContent) ->
                if (role == "user") {
                    // Remove <memory> tags from user messages, similar to UserMessageComposable
                    role to msgContent.replace(Regex("<memory>.*?</memory>", RegexOption.DOT_MATCHES_ALL), "").trim()
                } else {
                    role to msgContent
                }
            }

        if (processedHistory.isEmpty()) {
            Log.w(TAG, "处理后的会话历史为空，跳过保存问题记录")
            return
        }

        val query = processedHistory.lastOrNull { it.first == "user" }?.second ?: ""
        if (query.isEmpty()) {
            Log.w(TAG, "未找到用户查询消息，跳过保存")
            return
        }

        // Generate the graph analysis from the conversation
        val analysis = generateAnalysis(aiService, query, content, processedHistory, memoryRepository)

        // First, apply any updates to existing memories
        if (analysis.updatedEntities.isNotEmpty()) {
            Log.d(TAG, "开始更新 ${analysis.updatedEntities.size} 个现有记忆...")
            analysis.updatedEntities.forEach { update ->
                val memoryToUpdate = memoryRepository.findMemoryByTitle(update.titleToUpdate)
                if (memoryToUpdate != null) {
                    Log.d(TAG, "正在更新记忆: '${update.titleToUpdate}'. 原因: ${update.reason}")
                    memoryRepository.updateMemory(
                            memory = memoryToUpdate,
                            newTitle = memoryToUpdate.title, // For now, let's not change the title
                            newContent = update.newContent
                    )
                } else {
                    Log.w(TAG, "想要更新的记忆未找到: '${update.titleToUpdate}'")
                }
            }
        }

        // Update user preferences (this logic remains)
        if (analysis.userPreferences.isNotEmpty()) {
            try {
                withContext(Dispatchers.IO) {
                    updateUserPreferencesFromAnalysis(analysis.userPreferences)
                    Log.d(TAG, "用户偏好已更新")
                }
            } catch (e: Exception) {
                Log.e(TAG, "更新用户偏好失败", e)
            }
        }

        // Save the graph structure to the MemoryRepository
        if (analysis.mainProblem == null) {
            Log.w(TAG, "分析结果中缺少main_problem，跳过保存记忆图谱")
            return
        }

        Log.d(TAG, "开始构建记忆图谱...")
        Log.d(TAG, "AI分析结果 - 主要问题: '${analysis.mainProblem.title}', 实体: ${analysis.extractedEntities.size}, 链接: ${analysis.links.size}")


        try {
            val createdMemories = mutableMapOf<String, Memory>()

            // 1. Create main problem memory
            Log.d(TAG, "1. 创建主要问题记忆节点: '${analysis.mainProblem.title}'")
            val mainProblemMemory = Memory(
                title = analysis.mainProblem.title,
                content = analysis.mainProblem.content,
                source = "problem_library_analysis"
            )
            memoryRepository.saveMemory(mainProblemMemory)
            analysis.mainProblem.tags.forEach { tagName ->
                memoryRepository.addTagToMemory(mainProblemMemory, tagName)
            }
            createdMemories[mainProblemMemory.title] = mainProblemMemory

            // 2. Process entities with new LLM-driven deduplication logic
            analysis.extractedEntities.forEach { entity ->
                Log.d(TAG, "2. 处理实体: '${entity.title}'")
                var memory: Memory? = null

                if (!entity.aliasFor.isNullOrBlank()) {
                    // This entity is an alias for an existing one, as determined by the LLM.
                    Log.d(TAG, "   -> LLM 识别此实体为 '${entity.aliasFor}' 的别名。")
                    // Try to find the canonical memory, first in the ones we just created, then in the DB.
                    memory = createdMemories[entity.aliasFor] ?: memoryRepository.findMemoryByTitle(entity.aliasFor)

                    if (memory != null) {
                        Log.d(TAG, "   -> 复用已存在的记忆节点 (ID: ${memory.id}).")
                    } else {
                        // This is an edge case: LLM said it's an alias, but we can't find the original.
                        // We will treat it as a new entity.
                        Log.w(TAG, "   -> 无法找到别名 '${entity.aliasFor}' 的原始记忆。将其作为新实体处理。")
                    }
                }

                // If it's not an alias, or if the original for the alias wasn't found, create a new memory.
                if (memory == null) {
                    // We now trust the LLM's judgment that this is a new entity. No more local similarity checks.
                    Log.d(TAG, "   -> 创建新的记忆节点。")
                    memory = Memory(
                        title = entity.title,
                        content = entity.content,
                        source = "problem_library_analysis"
                    )
                    memoryRepository.saveMemory(memory)
                    entity.tags.forEach { tagName ->
                        memoryRepository.addTagToMemory(memory, tagName)
                    }
                }

                // Map the title of the entity (whether it's an alias or new) to the resolved memory object.
                // This ensures that links pointing to the alias title will resolve to the correct canonical memory.
                createdMemories[entity.title] = memory
            }

            // 3. Create links between the memories
            Log.d(TAG, "3. 开始创建记忆链接...")
            analysis.links.forEach { link ->
                val source = createdMemories[link.sourceTitle]
                val target = createdMemories[link.targetTitle]
                if (source != null && target != null) {
                    Log.d(TAG, "   -> 正在链接: '${link.sourceTitle}' --(${link.type})--> '${link.targetTitle}'")
                    memoryRepository.linkMemories(source, target, link.type, description = link.description)
                } else {
                    Log.w(TAG, "   -> 无法创建链接，源或目标实体未找到: ${link.sourceTitle} -> ${link.targetTitle}")
                }
            }

            Log.d(TAG, "成功从对话中提取并保存了记忆图谱")

        } catch (e: Exception) {
            Log.e(TAG, "保存记忆图谱失败", e)
        }
    }

    /**
     * Generates a structured analysis of the conversation for graph creation.
     */
    private suspend fun generateAnalysis(
            aiService: AIService,
            query: String,
            solution: String,
            conversationHistory: List<Pair<String, String>>,
            memoryRepository: MemoryRepository
    ): ParsedAnalysis {
        try {
            val currentPreferences = withContext(Dispatchers.IO) {
                var preferences = ""
                preferencesManager.getUserPreferencesFlow().take(1).collect { profile ->
                    preferences = buildPreferencesText(profile)
                }
                preferences
            }

            // --- Hybrid Strategy: Local rough search + LLM final decision ---
            // 1. Use local embedding search for a "rough" candidate selection.
            val contextQuery = query + "\n" + solution.take(1000) // Combine query and solution for context
            val candidateMemories = memoryRepository.searchMemories(contextQuery).take(10)
            val existingMemoriesPrompt = if (candidateMemories.isNotEmpty()) {
                "为避免重复，请参考以下知识库中可能相关的已有记忆。在提取实体时，如果发现与下列记忆语义相同的实体，请使用`alias_for`字段进行标注：\n" +
                        candidateMemories.joinToString("\n") { "- \"${it.title}\": ${it.content.take(150).replace("\n", " ")}..." }
            } else {
                "知识库目前为空或没有找到相关记忆，请自由提取实体。"
            }


            val systemPrompt = """
                你是一个知识图谱构建专家。你的任务是分析一段对话，并从中提取AI自己学到的关键知识，用于构建一个记忆图谱。同时，你还需要分析用户偏好。

                $existingMemoriesPrompt

                你的目标是：
                1.  **识别核心实体和概念**: 从对话中找出关键的人物、地点、项目、概念、技术等。每个实体都应该是一个独立的、可复用的知识单元。
                2.  **定义实体间的关系**: 找出这些实体之间是如何关联的。
                3.  **总结核心知识**: 将本次对话学习到的最核心的知识点作为一个中心记忆节点。
                4.  **更新用户偏好**: 根据对话内容，增量更新对用户的了解。
                5.  **修正现有记忆**: 如果你发现 `$existingMemoriesPrompt` 中列出的某个记忆包含事实性错误或过时的信息，请提出修改建议。

                你需要返回一个固定格式的JSON对象，包含以下字段：
                {
                  "main_problem": {
                    "title": "本次对话学习到的核心知识点",
                    "content": "详细描述本次对话中学习到的最核心的知识，说明其背景、关键属性和重要性。",
                    "tags": ["核心知识", "其他相关标签"]
                  },
                  "extracted_entities": [
                    {
                      "title": "实体1的标题",
                      "content": "关于此实体的具体信息、定义或关键属性的详细描述。",
                      "tags": ["实体类型", "例如: Concept, Person, Technology"],
                      "alias_for": "如果此实体与上方列出的“已有记忆”中的某一个语义完全相同，请在此处填入那个已有记忆的【确切标题】。如果这是一个全新的实体，【必须】将此字段的值设为 JSON null，或者完全省略此字段，【绝对禁止】使用字符串 \"null\"。"
                    }
                  ],
                  "updated_entities": [
                    {
                      "title_to_update": "需要更新的现有记忆的【确切标题】",
                      "updated_content": "修正后的完整、详细的内容。",
                      "reason_for_update": "简要说明为什么需要更新 (例如：'信息已过时，XX已更新为YY')。"
                    }
                  ],
                  "links": [
                    {
                      "source_title": "实体1的标题",
                      "target_title": "实体2的标题",
                      "type": "关系类型 (例如: CAUSES, EXPLAINS, RELATED_TO)",
                      "description": "对关系的简短描述"
                    }
                  ],
                  "user_preferences": {
                    "age": "保持不变用'<UNCHANGED>'，有新发现则更新为数字",
                    "gender": "保持不变用'<UNCHANGED>'，有新发现则更新为具体值",
                    "personality": "保持不变用'<UNCHANGED>'，有新发现则更新为具体值",
                    "identity": "保持不变用'<UNCHANGED>'，有新发现则更新为具体值",
                    "occupation": "保持不变用'<UNCHANGED>'，有新发现则更新为具体值",
                    "aiStyle": "保持不变用'<UNCHANGED>'，有新发现则更新为具体值"
                  }
                }

                【重要指南】:
                - `main_problem`: 这是AI学到的核心知识，作为一个中心记忆节点。它的 `title` 和 `content` 应该聚焦于知识本身，而不是用户的提问行为。
                - `extracted_entities`: 【极其重要】为每个提取的实体做出判断。如果它与提供的“已有记忆”列表中的某一项实质上是同一个东西，必须在 `alias_for` 字段中填写已有记忆的标题。否则，此字段的值必须是 JSON null 或被省略，【严禁】使用字符串 "null"。
                - `updated_entities`: **【谨慎操作】** 只有在你对信息的正确性有**高度自信**时才提出修改。优先创建新记忆。如果只是补充信息，请创建一个新的相关实体并与之链接，而不是修改现有实体。仅在现有实体内容**明确错误**时才进行修改。
                - `links`: 定义实体之间的关系。`source_title` 和 `target_title` 必须对应 `main_problem` 或 `extracted_entities` 中的 `title`。关系类型 (type) 应该使用大写字母和下划线 (e.g., `IS_A`, `PART_OF`, `LEADS_TO`)。
                - `user_preferences`: 【特别重要】用结构化JSON格式表示，在现有偏好的基础上进行小幅增量更新。
                  现有用户偏好：$currentPreferences
                  对于没有新发现的字段，使用"<UNCHANGED>"特殊标记表示保持不变。

                只返回格式正确的JSON对象，不要添加任何其他内容。
                """.trimIndent()

            val analysisMessage = buildAnalysisMessage(query, solution, conversationHistory)
            val messages = listOf(Pair("system", systemPrompt), Pair("user", analysisMessage))
            val result = StringBuilder()

            withContext(Dispatchers.IO) {
                val stream = aiService.sendMessage(message = analysisMessage, chatHistory = messages)
                stream.collect { content -> result.append(content) }
            }

            apiPreferences?.updateTokensForProviderModel(
                    aiService.providerModel,
                    aiService.inputTokenCount,
                    aiService.outputTokenCount,
                    aiService.cachedInputTokenCount
            )

            return parseAnalysisResult(ChatUtils.removeThinkingContent(result.toString()))
        } catch (e: Exception) {
            Log.e(TAG, "生成分析失败", e)
            return ParsedAnalysis(null)
        }
    }

    private fun buildAnalysisMessage(
            query: String,
            solution: String,
            conversationHistory: List<Pair<String, String>>
    ): String {
        val messageBuilder = StringBuilder()
        messageBuilder.appendLine("问题:")
        messageBuilder.appendLine(query)
        messageBuilder.appendLine()
        messageBuilder.appendLine("解决方案:")
        messageBuilder.appendLine(solution.take(3000))
        messageBuilder.appendLine()
        val recentHistory = conversationHistory.takeLast(10)
        if (recentHistory.isNotEmpty()) {
            messageBuilder.appendLine("历史记录:")
            recentHistory.forEachIndexed { index, (role, content) ->
                messageBuilder.appendLine("#${index + 1} $role: ${content.take(4000)}")
            }
        }
        return messageBuilder.toString()
    }

    /**
     * Parses the JSON response from the AI into a ParsedAnalysis object.
     */
    private fun parseAnalysisResult(jsonString: String): ParsedAnalysis {
        return try {
            val cleanJson = jsonString.trim().let {
                val startIndex = it.indexOf("{")
                val endIndex = it.lastIndexOf("}")
                if (startIndex >= 0 && endIndex > startIndex) it.substring(startIndex, endIndex + 1) else it
            }

            val json = JSONObject(cleanJson)

            // Parse main_problem
            val mainProblemJson = json.optJSONObject("main_problem")
            val mainProblem = mainProblemJson?.let {
                val tagsJson = it.optJSONArray("tags")
                val tags = if (tagsJson != null) List(tagsJson.length()) { i -> tagsJson.getString(i) } else emptyList()
                ParsedEntity(
                    title = it.optString("title"),
                    content = it.optString("content"),
                    tags = tags,
                    aliasFor = null // Main problem is not an alias
                )
            }

            // Parse extracted_entities
            val entitiesJson = json.optJSONArray("extracted_entities")
            val extractedEntities = if (entitiesJson != null) {
                List(entitiesJson.length()) { i ->
                    val entityJson = entitiesJson.getJSONObject(i)
                    val tagsJson = entityJson.optJSONArray("tags")
                    val tags = if (tagsJson != null) List(tagsJson.length()) { j -> tagsJson.getString(j) } else emptyList()
                    // Robustly handle the alias_for field
                    val aliasFor = if (entityJson.has("alias_for") && !entityJson.isNull("alias_for")) {
                        val value = entityJson.getString("alias_for")
                        if (value.equals("null", ignoreCase = true)) null else value
                    } else {
                        null
                    }
                    ParsedEntity(
                        title = entityJson.getString("title"),
                        content = entityJson.getString("content"),
                        tags = tags,
                        aliasFor = aliasFor
                    )
                }
            } else emptyList()

            // Parse links
            val linksJson = json.optJSONArray("links")
            val links = if (linksJson != null) {
                List(linksJson.length()) { i ->
                    val linkJson = linksJson.getJSONObject(i)
                    ParsedLink(
                        sourceTitle = linkJson.getString("source_title"),
                        targetTitle = linkJson.getString("target_title"),
                        type = linkJson.getString("type"),
                        description = linkJson.optString("description", "")
                    )
                }
            } else emptyList()

            // Parse updated_entities
            val updatesJson = json.optJSONArray("updated_entities")
            val updatedEntities = if (updatesJson != null) {
                List(updatesJson.length()) { i ->
                    val updateJson = updatesJson.getJSONObject(i)
                    ParsedUpdate(
                            titleToUpdate = updateJson.getString("title_to_update"),
                            newContent = updateJson.getString("updated_content"),
                            reason = updateJson.getString("reason_for_update")
                    )
                }
            } else emptyList()


            val userPreferences = if (json.has("user_preferences") && json.get("user_preferences") is JSONObject) {
                val preferencesObj = json.getJSONObject("user_preferences")
                // (The existing detailed user preference parsing logic remains unchanged)
                parseUserPreferences(preferencesObj)
            } else {
                json.optString("user_preferences", "")
            }

            ParsedAnalysis(
                mainProblem = mainProblem,
                extractedEntities = extractedEntities,
                links = links,
                updatedEntities = updatedEntities,
                userPreferences = userPreferences
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析分析结果失败: $jsonString", e)
            ParsedAnalysis(null)
        }
    }

    private fun parseUserPreferences(preferencesObj: JSONObject): String {
        val preferenceParts = mutableListOf<String>()
        // Helper to add preference if it exists and is not "<UNCHANGED>"
        fun addPref(key: String, prefix: String) {
            if (preferencesObj.has(key) && preferencesObj.get(key) != "<UNCHANGED>") {
                val value = preferencesObj.get(key).toString()
                if (value.isNotEmpty()) preferenceParts.add("$prefix: $value")
            }
        }
        addPref("age", "出生年份")
        addPref("gender", "性别")
        addPref("personality", "性格特点")
        addPref("identity", "身份认同")
        addPref("occupation", "职业")
        addPref("aiStyle", "期待的AI风格")
        return preferenceParts.joinToString("; ")
    }


    private fun buildPreferencesText(profile: com.ai.assistance.operit.data.model.PreferenceProfile): String {
        val parts = mutableListOf<String>()
        if (profile.gender.isNotEmpty()) parts.add("性别: ${profile.gender}")
        if (profile.birthDate > 0) {
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            parts.add("出生日期: ${dateFormat.format(java.util.Date(profile.birthDate))}")
            val today = java.util.Calendar.getInstance()
            val birthCal = java.util.Calendar.getInstance().apply { timeInMillis = profile.birthDate }
            var age = today.get(java.util.Calendar.YEAR) - birthCal.get(java.util.Calendar.YEAR)
            if (today.get(java.util.Calendar.DAY_OF_YEAR) < birthCal.get(java.util.Calendar.DAY_OF_YEAR)) {
                age--
            }
            parts.add("年龄: ${age}岁")
        }
        if (profile.personality.isNotEmpty()) parts.add("性格特点: ${profile.personality}")
        if (profile.identity.isNotEmpty()) parts.add("身份认同: ${profile.identity}")
        if (profile.occupation.isNotEmpty()) parts.add("职业: ${profile.occupation}")
        if (profile.aiStyle.isNotEmpty()) parts.add("期待的AI风格: ${profile.aiStyle}")
        return parts.joinToString("; ")
    }

    private suspend fun updateUserPreferencesFromAnalysis(preferencesText: String) {
        if (preferencesText.isEmpty()) return

        val birthDateMatch = """(出生日期|出生年月日)[:：\s]+([\d-]+)""".toRegex().find(preferencesText)
        val birthYearMatch = """(出生年份|年龄)[:：\s]+(\d+)""".toRegex().find(preferencesText)
        val genderMatch = """性别[:：\s]+([\u4e00-\u9fa5]+)""".toRegex().find(preferencesText)
        val personalityMatch = """性格(特点)?[:：\s]+([\u4e00-\u9fa5、，,]+)""".toRegex().find(preferencesText)
        val identityMatch = """身份(认同)?[:：\s]+([\u4e00-\u9fa5、，,]+)""".toRegex().find(preferencesText)
        val occupationMatch = """职业[:：\s]+([\u4e00-\u9fa5、，,]+)""".toRegex().find(preferencesText)
        val aiStyleMatch = """(AI风格|期待的AI风格|偏好的AI风格)[:：\s]+([\u4e00-\u9fa5、，,]+)""".toRegex().find(preferencesText)

        var birthDateTimestamp: Long? = null
        if (birthDateMatch != null) {
            try {
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val date = dateFormat.parse(birthDateMatch.groupValues[2])
                if (date != null) birthDateTimestamp = date.time
            } catch (e: Exception) {
                Log.e(TAG, "解析出生日期失败: ${e.message}")
            }
        } else if (birthYearMatch != null) {
            try {
                val year = birthYearMatch.groupValues[2].toInt()
                val calendar = java.util.Calendar.getInstance()
                calendar.set(year, java.util.Calendar.JANUARY, 1, 0, 0, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                birthDateTimestamp = calendar.timeInMillis
            } catch (e: Exception) {
                Log.e(TAG, "解析出生年份失败: ${e.message}")
            }
        }

        preferencesManager.updateProfileCategory(
                birthDate = birthDateTimestamp,
                gender = genderMatch?.groupValues?.getOrNull(1),
                personality = personalityMatch?.groupValues?.getOrNull(2),
                identity = identityMatch?.groupValues?.getOrNull(2),
                occupation = occupationMatch?.groupValues?.getOrNull(1),
                aiStyle = aiStyleMatch?.groupValues?.getOrNull(2)
        )
    }
}
