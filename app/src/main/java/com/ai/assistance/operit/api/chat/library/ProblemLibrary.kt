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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val mutex = Mutex()

    @Volatile private var isInitialized = false

    // --- Data classes for parsing the new structured analysis ---
    private data class ParsedLink(val sourceTitle: String, val targetTitle: String, val type: String, val description: String, val weight: Float = 1.0f)
    private data class ParsedEntity(val title: String, val content: String, val tags: List<String>, val aliasFor: String?, val folderPath: String?)
    private data class ParsedUpdate(val titleToUpdate: String, val newContent: String, val reason: String, val newCredibility: Float?, val newImportance: Float?)
    private data class ParsedMerge(val sourceTitles: List<String>, val newTitle: String, val newContent: String, val newTags: List<String>, val folderPath: String, val reason: String)
    private data class ParsedAnalysis(
        val mainProblem: ParsedEntity?,
        val extractedEntities: List<ParsedEntity> = emptyList(),
        val links: List<ParsedLink> = emptyList(),
        val updatedEntities: List<ParsedUpdate> = emptyList(),
        val mergedEntities: List<ParsedMerge> = emptyList(),
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
        mutex.withLock {
            val profileId = preferencesManager.activeProfileIdFlow.first()
            val memoryRepository = MemoryRepository(context, profileId)

            // Prune tool results to reduce token usage
            val prunedContent = pruneToolResultContent(content)

            // Process conversation history: remove system messages and clean user messages
            val processedHistory = conversationHistory
                .filter { it.first != "system" }
                .map { (role, msgContent) ->
                    val cleanedContent = if (role == "user") {
                        msgContent.replace(Regex("<memory>.*?</memory>", RegexOption.DOT_MATCHES_ALL), "").trim()
                    } else {
                        msgContent
                    }
                    role to pruneToolResultContent(cleanedContent)
                }

            if (processedHistory.isEmpty()) {
                Log.w(TAG, "处理后的会話历史为空，跳过保存问题记录")
                return@withLock
            }

            val query = processedHistory.lastOrNull { it.first == "user" }?.second ?: ""
            if (query.isEmpty()) {
                Log.w(TAG, "未找到用户查询消息，跳过保存")
                return@withLock
            }

            // Generate the graph analysis from the conversation
            val analysis = generateAnalysis(aiService, query, prunedContent, processedHistory, memoryRepository)

            // If analysis is empty (trivial conversation), abort early.
            if (analysis.mainProblem == null && analysis.extractedEntities.isEmpty() && analysis.updatedEntities.isEmpty() && analysis.mergedEntities.isEmpty()) {
                Log.d(TAG, "分析结果为空，判断为无需记忆的对话，跳过保存。")
                return@withLock
            }


            // Create a map to track all memories (new and updated) for linking
            val createdMemories = mutableMapOf<String, Memory>()

            // First, apply any merges to existing memories
            if (analysis.mergedEntities.isNotEmpty()) {
                Log.d(TAG, "开始合并 ${analysis.mergedEntities.size} 组记忆...")
                analysis.mergedEntities.forEach { merge ->
                    Log.d(TAG, "正在合并: ${merge.sourceTitles.joinToString(", ")} -> '${merge.newTitle}'. 原因: ${merge.reason}")
                    val mergedMemory = memoryRepository.mergeMemories(
                        sourceTitles = merge.sourceTitles,
                        newTitle = merge.newTitle,
                        newContent = merge.newContent,
                        newTags = merge.newTags,
                        folderPath = merge.folderPath
                    )
                    if (mergedMemory != null) {
                        createdMemories[mergedMemory.title] = mergedMemory
                    }
                }
            }

            // Second, apply any updates to existing memories
            if (analysis.updatedEntities.isNotEmpty()) {
                Log.d(TAG, "开始更新 ${analysis.updatedEntities.size} 个现有记忆...")
                analysis.updatedEntities.forEach { update ->
                    val memoryToUpdate = memoryRepository.findMemoryByTitle(update.titleToUpdate)
                    if (memoryToUpdate != null) {
                        Log.d(TAG, "正在更新记忆: '${update.titleToUpdate}'. 原因: ${update.reason}")
                        val updatedMemory = memoryRepository.updateMemory(
                                memory = memoryToUpdate,
                                newTitle = memoryToUpdate.title, // For now, let's not change the title
                                newContent = update.newContent,
                                newCredibility = update.newCredibility ?: memoryToUpdate.credibility,
                                newImportance = update.newImportance ?: memoryToUpdate.importance
                        )
                        if (updatedMemory != null) {
                            createdMemories[updatedMemory.title] = updatedMemory
                        }
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
                return@withLock
            }

            Log.d(TAG, "开始构建记忆图谱...")
            Log.d(TAG, "AI分析结果 - 主要问题: '${analysis.mainProblem.title}', 实体: ${analysis.extractedEntities.size}, 链接: ${analysis.links.size}, 文件夹: '${analysis.mainProblem.folderPath}'")


            try {
                // 1. Create main problem memory
                Log.d(TAG, "1. 创建主要问题记忆节点: '${analysis.mainProblem.title}'")
                val mainProblemMemory = Memory(
                    title = analysis.mainProblem.title,
                    content = analysis.mainProblem.content,
                    source = "problem_library_analysis",
                    folderPath = analysis.mainProblem.folderPath ?: "" // 使用分析出的文件夹路径
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

                    // If it's not an alias, or if the original for the alias wasn't found, try local deduplication before creating.
                    if (memory == null) {
                        // Conservative Strategy: Before creating a new entity, perform a high-similarity local search.
                        val similarMemories = memoryRepository.searchMemoriesPrecise(entity.title, similarityThreshold = 0.92f)
                        val bestMatch = similarMemories.firstOrNull()

                        if (bestMatch != null) {
                            // If a very similar memory is found locally, treat it as an alias and reuse it.
                            Log.d(TAG, "   -> 本地查重：发现与 '${bestMatch.title}' 高度相似的记忆。复用现有记忆节点。")
                            memory = bestMatch
                        } else {
                            // Only create a new memory if no close match is found.
                            Log.d(TAG, "   -> 本地查重未发现相似项。创建新的记忆节点。")
                            memory = Memory(
                                title = entity.title,
                                content = entity.content,
                                source = "problem_library_analysis",
                                folderPath = entity.folderPath ?: analysis.mainProblem.folderPath ?: ""
                            )
                            memoryRepository.saveMemory(memory)
                            entity.tags.forEach { tagName ->
                                memoryRepository.addTagToMemory(memory, tagName)
                            }
                        }
                    }

                    // Map the title of the entity (whether it's an alias or new) to the resolved memory object.
                    // This ensures that links pointing to the alias title will resolve to the correct canonical memory.
                    createdMemories[entity.title] = memory
                }

                // 3. Create links between the memories
                Log.d(TAG, "3. 开始创建记忆链接...")
                analysis.links.forEach { link ->
                    // Try to find source: first in newly created/updated memories, then in existing DB
                    val source = createdMemories[link.sourceTitle] 
                        ?: memoryRepository.findMemoryByTitle(link.sourceTitle)
                    
                    // Try to find target: first in newly created/updated memories, then in existing DB
                    val target = createdMemories[link.targetTitle] 
                        ?: memoryRepository.findMemoryByTitle(link.targetTitle)
                    
                    if (source != null && target != null) {
                        Log.d(TAG, "   -> 正在链接: '${link.sourceTitle}' --(${link.type}, weight=${link.weight})--> '${link.targetTitle}'")
                        memoryRepository.linkMemories(source, target, link.type, weight = link.weight, description = link.description)
                    } else {
                        Log.w(TAG, "   -> 无法创建链接，源或目标实体未找到: ${link.sourceTitle} -> ${link.targetTitle}")
                        if (source == null) Log.w(TAG, "      源节点 '${link.sourceTitle}' 未找到")
                        if (target == null) Log.w(TAG, "      目标节点 '${link.targetTitle}' 未找到")
                    }
                }

                Log.d(TAG, "成功从对话中提取并保存了记忆图谱")

            } catch (e: Exception) {
                Log.e(TAG, "保存记忆图谱失败", e)
            }
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
            val candidateMemories = memoryRepository.searchMemories(contextQuery).take(15)

            // 2. Proactively find duplicates among candidates and instruct LLM to merge them
            val duplicatesPromptPart = findAndDescribeDuplicates(candidateMemories, memoryRepository)

            val existingMemoriesPrompt = if (candidateMemories.isNotEmpty()) {
                "为避免重复，请参考以下知识库中可能相关的已有记忆。在提取实体时，如果发现与下列记忆语义相同的实体，请使用`alias_for`字段进行标注：\n" +
                        candidateMemories.joinToString("\n") { "- \"${it.title}\": ${it.content.take(150).replace("\n", " ")}..." }
            } else {
                "知识库目前为空或没有找到相关记忆，请自由提取实体。"
            }

            // 获取现有文件夹列表
            val existingFolders = memoryRepository.getAllFolderPaths()
            val existingFoldersPrompt = if (existingFolders.isNotEmpty()) {
                "当前已存在的文件夹分类如下，请优先使用或参考它们来决定新知识的分类：\n${existingFolders.joinToString(", ")}"
            } else {
                "当前还没有文件夹分类，请根据内容创建一个合适的分类。"
            }


            val systemPrompt = """
                你是一个知识图谱构建专家。你的任务是分析一段对话，并从中提取AI自己学到的关键知识，用于构建一个记忆图谱。同时，你还需要分析用户偏好。

                $duplicatesPromptPart
                $existingMemoriesPrompt

                $existingFoldersPrompt

                你的目标是：
                1.  **识别核心实体和概念**: 从对话中找出关键的人物、地点、项目、概念、技术等。每个实体都应该是一个独立的、可复用的知识单元。
                2.  **定义实体间的关系**: 找出这些实体之间是如何关联的。
                3.  **总结核心知识**: 将本次对话学习到的最核心的知识点作为一个中心记忆节点。
                4.  **为知识分类**: 为所有新创建的知识（包括核心知识和实体）建议一个合适的文件夹路径（`folder_path`），以便于管理。
                5.  **更新用户偏好**: 根据对话内容，增量更新对用户的了解。
                6.  **批判性地更新和完善现有记忆**: 如果对话中的新信息可以纠正、补充或深化 `$existingMemoriesPrompt` 中列出的任何记忆，请优先更新它们，而不是创建重复的实体。

                【记忆属性定义】:
                - `credibility` (可信度): 代表该条记忆内容的准确性。取值范围 0.0 ~ 1.0。1.0代表完全可信，0.0代表完全不可信。**此值会影响记忆在被检索时的内容表示**。
                - `importance` (重要性): 代表该条记忆对于整个知识网络的重要性。取值范围 0.0 ~ 1.0。1.0代表核心知识，0.0代表非常边缘的信息。**此值会作为搜索时的权重，直接影响其被检索到的概率**。
                - `edge.weight` (连接权重): 代表两个记忆节点之间关联的强度。取值范围 0.0 ~ 1.0。

                **【输出格式】: 你必须返回一个使用数组的紧凑型JSON，以减少Token消耗。**
                - **键名**: 必须使用缩写: "main" (核心知识), "new" (新实体), "update" (更新实体), "merge" (合并实体), "links" (关系), "user" (用户偏好)。
                - **值**: 必须是数组形式，并严格按照以下顺序和类型排列元素。可选字段如果不存在，请使用 `null` 占位。

                ```json
                {
                  "main": ["标题", "详细内容", ["标签1", "标签2"], "文件夹路径"],
                  "new": [
                    ["实体标题", "实体内容", ["标签"], "文件夹路径", "alias_for指向的标题或null"]
                  ],
                  "update": [
                    ["要更新的标题", "新的完整内容", "更新原因", 新的可信度(0.0-1.0)或null, 新的重要性(0.0-1.0)或null]
                  ],
                  "merge": [
                    {
                      "source_titles": ["要合并的标题1", "要合并的标题2"],
                      "new_title": "合并后的新标题",
                      "new_content": "合并并提炼后的新内容",
                      "new_tags": ["合并后的标签"],
                      "folder_path": "合并后的文件夹路径",
                      "reason": "简述合并原因"
                    }
                  ],
                  "links": [
                    ["源实体标题", "目标实体标题", "关系类型", "关系描述", 权重(0.0-1.0)]
                  ],
                  "user": {
                    "personality": "更新后的人格",
                    "occupation": "<UNCHANGED>"
                  }
                }
                ```

                【重要指南】:
                - 【**最重要**】如果本次对话内容非常简单、属于日常寒暄、没有包含任何新的、有价值的、值得长期记忆的知识点，或只是对已有知识的简单重复应用，请直接返回一个空的 JSON 对象 `{}`。这是控制知识库质量的关键。
                - `main`: 这是AI学到的核心知识，作为一个中心记忆节点。它的 `title` 和 `content` 应该聚焦于知识本身，而不是用户的提问行为。
                - `folder_path`: 为所有新知识指定一个有意义的、层级化的文件夹路径。尽量复用已有的文件夹。如果实体与`main`主题紧密相关，它们的`folder_path`应该一致。
                - `new`: 【极其重要】为每个提取的实体做出判断。如果它与提供的“已有记忆”列表中的某一项实质上是同一个东西，必须在数组的第5个元素提供已有记忆的标题。否则，此元素的值必须是 JSON null。
                - `update`: **【优先更新】** 你的首要任务是维护一个准确、丰富的知识库。当新信息可以改进现有记忆时（无论是纠正错误、补充细节、还是提供更新的视角），请大胆地使用此字段进行更新。**优先更新和合并，而不是创建大量相似或零散的新记忆。** 如果你认为新信息影响了某条记忆的【可信度】或【重要性】，请务必在数组的第4和第5个元素中给出新的评估值。
                - `merge`: **【合并相似项】** 当你发现多个现有记忆（在`$existingMemoriesPrompt`中提供）实际上描述的是同一个核心概念时，使用此字段将它们合并成一个更完整、更准确的单一记忆。这对于保持知识库的整洁至关重要。
                - `links`: 定义实体之间的关系。`source_title` 和 `target_title` 必须对应 `main` 或 `new` 中的实体标题。关系类型 (type) 应该使用大写字母和下划线 (e.g., `IS_A`, `PART_OF`, `LEADS_TO`)。`weight` 字段表示关系的强度 (0.0-1.0)，【强烈推荐】只使用以下三个标准值：
                  - `1.0`: 代表强关联 (例如: "A 是 B 的一部分", "A 导致了 B")
                  - `0.7`: 代表中等关联 (例如: "A 和 B 相关", "A 影响了 B")
                  - `0.3`: 代表弱关联 (例如: "A 有时会和 B 一起提及")
                - `user`: 【特别重要】用结构化JSON格式表示，在现有偏好的基础上进行小幅增量更新。
                  现有用户偏好：$currentPreferences
                  对于没有新发现的字段，使用"<UNCHANGED>"特殊标记表示保持不变。

                【规则补充】: 当对话的核心结论仅仅是对一个现有概念的**深化**、**确认**或**补充**时（例如，从一次失败的工具调用中学会了‘激活机制很重要’），你**必须**通过 `update` 数组来增强现有记忆的`content`或调整其`importance`值，并且**禁止**在这种情况下使用 `main` 字段创建重复的新记忆。

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

    /**
     * Finds duplicates within a list of candidate memories and creates a prompt instruction for the LLM.
     */
    private suspend fun findAndDescribeDuplicates(candidateMemories: List<Memory>, memoryRepository: MemoryRepository): String {
        val titles = candidateMemories.map { it.title }.distinct()
        val duplicatesFound = mutableListOf<String>()

        for (title in titles) {
            val memoriesWithSameTitle = memoryRepository.findMemoriesByTitle(title)
            if (memoriesWithSameTitle.size > 1) {
                duplicatesFound.add("发现 ${memoriesWithSameTitle.size} 个标题完全相同的记忆: \"$title\"。请在本次分析中使用 `merge` 功能将它们合并成一个单一、更完善的记忆。")
            }
        }

        return if (duplicatesFound.isNotEmpty()) {
            "【重要指令：清理重复记忆】\n" + duplicatesFound.joinToString("\n") + "\n"
        } else {
            ""
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
                if (startIndex >= 0 && endIndex > startIndex) it.substring(startIndex, endIndex + 1) else return ParsedAnalysis(null)
            }

            // Handle the case where AI decides not to extract any knowledge
            if (cleanJson == "{}") {
                return ParsedAnalysis(null)
            }

            val json = JSONObject(cleanJson)

            // Parse main_problem from "main" array
            val mainProblem = json.optJSONArray("main")?.let {
                val tags = it.optJSONArray(2)?.let { tagsArray -> List(tagsArray.length()) { i -> tagsArray.getString(i) } } ?: emptyList()
                ParsedEntity(
                    title = it.getString(0),
                    content = it.getString(1),
                    tags = tags,
                    aliasFor = null,
                    folderPath = it.optString(3, "")
                )
            }

            // Parse extracted_entities from "new" array
            val extractedEntities = json.optJSONArray("new")?.let { entitiesArray ->
                List(entitiesArray.length()) { i ->
                    val entityArr = entitiesArray.getJSONArray(i)
                    val tags = entityArr.optJSONArray(2)?.let { tagsArray -> List(tagsArray.length()) { j -> tagsArray.getString(j) } } ?: emptyList()
                    val aliasFor = if (!entityArr.isNull(4)) entityArr.getString(4) else null
                    ParsedEntity(
                        title = entityArr.getString(0),
                        content = entityArr.getString(1),
                        tags = tags,
                        aliasFor = aliasFor,
                        folderPath = entityArr.optString(3, "")
                    )
                }
            } ?: emptyList()

            // Parse links from "links" array
            val links = json.optJSONArray("links")?.let { linksArray ->
                List(linksArray.length()) { i ->
                    val linkArr = linksArray.getJSONArray(i)
                    ParsedLink(
                        sourceTitle = linkArr.getString(0),
                        targetTitle = linkArr.getString(1),
                        type = linkArr.getString(2),
                        description = linkArr.optString(3, ""),
                        weight = linkArr.optDouble(4, 1.0).toFloat()
                    )
                }
            } ?: emptyList()

            // Parse updated_entities from "update" array
            val updatedEntities = json.optJSONArray("update")?.let { updatesArray ->
                List(updatesArray.length()) { i ->
                    val updateArr = updatesArray.getJSONArray(i)
                    val credibility = if (!updateArr.isNull(3)) updateArr.getDouble(3).toFloat() else null
                    val importance = if (!updateArr.isNull(4)) updateArr.getDouble(4).toFloat() else null
                    ParsedUpdate(
                        titleToUpdate = updateArr.getString(0),
                        newContent = updateArr.getString(1),
                        reason = updateArr.getString(2),
                        newCredibility = credibility,
                        newImportance = importance
                    )
                }
            } ?: emptyList()

            // Parse merge_entities from "merge" array
            val mergedEntities = json.optJSONArray("merge")?.let { mergeArray ->
                List(mergeArray.length()) { i ->
                    val mergeObj = mergeArray.getJSONObject(i)
                    val sourceTitles = mergeObj.getJSONArray("source_titles").let { titles ->
                        List(titles.length()) { j -> titles.getString(j) }
                    }
                    ParsedMerge(
                        sourceTitles = sourceTitles,
                        newTitle = mergeObj.getString("new_title"),
                        newContent = mergeObj.getString("new_content"),
                        newTags = mergeObj.optJSONArray("new_tags")?.let { tags ->
                            List(tags.length()) { k -> tags.getString(k) }
                        } ?: emptyList(),
                        folderPath = mergeObj.optString("folder_path"),
                        reason = mergeObj.optString("reason")
                    )
                }
            } ?: emptyList()


            val userPreferences = json.optJSONObject("user")?.let {
                parseUserPreferences(it)
            } ?: ""

            ParsedAnalysis(
                mainProblem = mainProblem,
                extractedEntities = extractedEntities,
                links = links,
                updatedEntities = updatedEntities,
                mergedEntities = mergedEntities,
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

    /**
     * Replaces the content of <tool_result> tags with a placeholder to reduce token count.
     */
    private fun pruneToolResultContent(message: String): String {
        val regex = Regex("<tool_result (.*? status=[\"'](.*?)[\"'])>(.*?)</tool_result>", RegexOption.DOT_MATCHES_ALL)
        return regex.replace(message) { matchResult ->
            val attributes = matchResult.groupValues[1]
            "<tool_result $attributes>[...工具结果已省略...]</tool_result>"
        }
    }
}
