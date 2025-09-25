package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.config.SystemPromptConfig
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.core.tools.SimplifiedUINode
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.preferences.PromptTagManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import com.ai.assistance.operit.core.tools.ComputerDesktopActionResultData
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.api.chat.enhance.MultiServiceManager


/** 处理会话相关功能的服务类，包括会话总结、偏好处理和对话切割准备 */
class ConversationService(private val context: Context) {

    companion object {
        private const val TAG = "ConversationService"
    }

    private val apiPreferences = ApiPreferences.getInstance(context)
    private val characterCardManager = CharacterCardManager.getInstance(context)
    private val userPreferencesManager = preferencesManager
    private val conversationMutex = Mutex()

    /**
     * 生成对话总结
     * @param messages 要总结的消息列表
     * @return 生成的总结文本
     */
    suspend fun generateSummary(
            messages: List<Pair<String, String>>,
            multiServiceManager: MultiServiceManager
    ): String {
        return generateSummary(messages, null, multiServiceManager)
    }

    /**
     * 生成对话总结，并且包含上一次的总结内容
     * @param messages 要总结的消息列表
     * @param previousSummary 上一次的总结内容，可以为null
     * @return 生成的总结文本
     */
    suspend fun generateSummary(
            messages: List<Pair<String, String>>,
            previousSummary: String?,
            multiServiceManager: MultiServiceManager
    ): String {
        try {
            // 使用更结构化、更详细的提示词
            var systemPrompt = FunctionalPrompts.SUMMARY_PROMPT.trimIndent()

            // 如果存在上一次的摘要，将其添加到系统提示中，为模型提供更明确的上下文。
            if (previousSummary != null && previousSummary.isNotBlank()) {
                systemPrompt +=
                        """

                上一次的摘要（用于继承上下文）：
                ${previousSummary.trim()}
                请将以上摘要中的关键信息，与本次新的对话内容相融合，生成一份全新的、更完整的摘要。
                """
                Log.d(TAG, "添加上一条摘要内容到系统提示")
            }

            val finalMessages = listOf(Pair("system", systemPrompt)) + messages

            // Get all model parameters from preferences (with enabled state)
            val modelParameters = multiServiceManager.getModelParametersForFunction(FunctionType.SUMMARY)

            // 获取SUMMARY功能类型的AIService实例
            val summaryService = multiServiceManager.getServiceForFunction(FunctionType.SUMMARY)

            // 使用summaryService发送请求，收集完整响应
            val contentBuilder = StringBuilder()

            // 使用新的Stream API
            val stream =
                    summaryService.sendMessage(
                            message = "请按照要求总结对话内容",
                            chatHistory = finalMessages,
                            modelParameters = modelParameters
                    )

            // 收集流中的所有内容
            stream.collect { content -> contentBuilder.append(content) }

            // 获取完整的总结内容
            val summaryContent = ChatUtils.removeThinkingContent(contentBuilder.toString().trim())

            // 如果内容为空，返回默认消息
            if (summaryContent.isBlank()) {
                return "对话摘要：未能生成有效摘要。"
            }

            // 获取本次总结生成的token统计
            val inputTokens = summaryService.inputTokenCount
            val cachedInputTokens = summaryService.cachedInputTokenCount
            val outputTokens = summaryService.outputTokenCount

            // 将总结token计数添加到用户偏好分析的token统计中
            try {
                Log.d(TAG, "总结生成使用了输入token: $inputTokens, 缓存token: $cachedInputTokens, 输出token: $outputTokens")
                apiPreferences.updateTokensForProviderModel(summaryService.providerModel, inputTokens, outputTokens, cachedInputTokens)
                Log.d(TAG, "已将总结token统计添加到用户偏好分析token计数中")
            } catch (e: Exception) {
                Log.e(TAG, "更新token统计失败", e)
            }

            return summaryContent
        } catch (e: Exception) {
            Log.e(TAG, "生成总结时出错", e)
            // return "对话摘要：生成摘要时出错，但对话仍在继续。"
            throw e
        }
    }

    /**
     * 为聊天准备对话历史记录
     * @param chatHistory 原始聊天历史
     * @param processedInput 处理后的用户输入
     * @param workspacePath 当前绑定的工作区路径，可以为null
     * @param packageManager 包管理器
     * @param promptFunctionType 提示函数类型
     * @param thinkingGuidance 是否需要思考指导
     * @return 准备好的对话历史列表
     */
    suspend fun prepareConversationHistory(
            chatHistory: List<Pair<String, String>>,
            processedInput: String,
            workspacePath: String?,
            packageManager: PackageManager,
            promptFunctionType: PromptFunctionType,
            thinkingGuidance: Boolean = false,
            customSystemPromptTemplate: String? = null
    ): List<Pair<String, String>> {
        val preparedHistory = mutableListOf<Pair<String, String>>()
        conversationMutex.withLock {
            // Add system prompt if not already present
            if (!chatHistory.any { it.first == "system" }) {
                val activeProfile = preferencesManager.getUserPreferencesFlow().first()
                val preferencesText = buildPreferencesText(activeProfile)

                // Check if planning is enabled
                val planningEnabled = apiPreferences.enableAiPlanningFlow.first()

                // 根据功能类型获取对应的提示词
                val activeCard = characterCardManager.activeCharacterCardFlow.first()
                val systemTagId =
                        when (promptFunctionType) {
                            PromptFunctionType.VOICE -> PromptTagManager.SYSTEM_VOICE_TAG_ID
                            PromptFunctionType.DESKTOP_PET ->
                                    PromptTagManager.SYSTEM_DESKTOP_PET_TAG_ID
                            else -> PromptTagManager.SYSTEM_CHAT_TAG_ID
                        }
                
                val introPrompt =
                        characterCardManager.combinePrompts(
                                activeCard.id,
                                listOf(systemTagId)
                        )

                // 获取自定义系统提示模板
                val finalCustomSystemPromptTemplate = customSystemPromptTemplate ?: apiPreferences.customSystemPromptTemplateFlow.first()

                // 获取系统提示词，现在传入workspacePath
                val systemPrompt =
                        SystemPromptConfig.getSystemPromptWithCustomPrompts(
                        packageManager,
                        workspacePath,
                        planningEnabled,
                        introPrompt,
                                thinkingGuidance,
                                finalCustomSystemPromptTemplate
                )

                // 构建waifu特殊规则
                val waifuRulesText = if(apiPreferences.enableWaifuModeFlow.first()) buildWaifuRulesText() else ""

                // 构建最终的系统提示词
                val finalSystemPrompt = if (preferencesText.isNotEmpty()) {
                    "$systemPrompt$waifuRulesText\n\nUser preference description: $preferencesText"
                } else {
                    "$systemPrompt$waifuRulesText"
                }

                // 替换提示词中的占位符
                val finalSystemPromptWithReplacements = replacePromptPlaceholders(
                    finalSystemPrompt,
                    activeCard.name
                )
                preparedHistory.add(0, Pair("system", finalSystemPromptWithReplacements))
            }

            // Process each message in chat history
            chatHistory.forEachIndexed { index, message ->
                val role = message.first
                val content = message.second

                // If it's an assistant message, check for tool results
                if (role == "assistant") {
                    val xmlTags = splitXmlTag(content)
                    if (xmlTags.isNotEmpty()) {
                        // Process the message with tool results
                        processChatMessageWithTools(content, xmlTags, preparedHistory, index, chatHistory.size)
                    } else {
                        // Add the message as is
                        preparedHistory.add(message)
                    }
                } else {
                    // Add user or system messages as is
                    preparedHistory.add(message)
                }
            }
        }
        return preparedHistory
    }

    /**
     * 提取内容中的XML标签
     * @param content 要处理的内容
     * @return 提取的XML标签列表，每项包含[标签名称, 标签内容]
     */
    fun splitXmlTag(content: String): List<List<String>> {
        val results = mutableListOf<List<String>>()

        // 使用StreamXmlPlugin处理XML标签
        val plugins = listOf(StreamXmlPlugin(includeTagsInOutput = true))

        try {
            // 将内容转换为Stream<Char>然后用插件拆分
            val contentStream = content.stream()
            val tagContents = mutableListOf<String>() // 标签内容
            val tagNames = mutableListOf<String>() // 标签名称

            // 使用协程作用域收集拆分结果
            kotlinx.coroutines.runBlocking {
                contentStream.splitBy(plugins).collect { group ->
                    if (group.tag is StreamXmlPlugin) {
                        val sb = StringBuilder()
                        var isFirstChar = true

                        // 收集完整的XML元素内容
                        group.stream.collect { charString ->
                            if (isFirstChar) {
                                isFirstChar = false
                            }
                            sb.append(charString)
                        }

                        val fullContent = sb.toString()

                        // 提取标签名称
                        val tagNameMatch = Regex("<([a-zA-Z0-9_]+)[\\s>]").find(fullContent)
                        val tagName = tagNameMatch?.groupValues?.getOrNull(1) ?: "unknown"

                        tagNames.add(tagName)
                        tagContents.add(fullContent)
                    } else {
                        // 处理纯文本内容
                        val sb = StringBuilder()

                        // 收集纯文本内容
                        group.stream.collect { charString -> sb.append(charString) }

                        val textContent = sb.toString()
                        if (textContent.isNotBlank()) {
                            // 对于纯文本，将其作为text标签处理
                            tagNames.add("text")
                            tagContents.add(textContent)
                        }
                    }
                }
            }

            // 将收集到的XML标签转换为二维列表
            for (i in tagNames.indices) {
                results.add(listOf(tagNames[i], tagContents[i]))
            }
        } catch (e: Exception) {
            Log.e(TAG, "使用Stream解析XML标签时出错", e)
        }

        return results
    }

    /** 处理包含工具结果的聊天消息，并按顺序重新组织消息 任务完成和等待用户响应的status标签算作AI消息，其他status和warning算作用户消息 工具结果为用户消息 */
    suspend fun processChatMessageWithTools(
            content: String,
            xmlTags: List<List<String>>,
            conversationHistory: MutableList<Pair<String, String>>,
            messageIndex: Int,
            totalMessages: Int
    ) {
        if (xmlTags.isEmpty()) {
            // 如果没有XML标签，直接添加为AI消息
            conversationHistory.add(Pair("assistant", content))
            return
        }

        // 按顺序处理标签
        val segments = mutableListOf<Pair<String, String>>() // 角色, 内容

        for (tag in xmlTags) {
            val tagName = tag[0]
            var tagContent = tag[1]

            // 对于text类型（纯文本），直接作为AI消息
            if (tagName == "text") {
                if (tagContent.isNotBlank()) {
                    segments.add(Pair("assistant", tagContent))
                }
                continue
            }

            // 应用内存优化: 只有当消息不是最近25条时才触发
            val distanceFromEnd = totalMessages - 1 - messageIndex
            if (distanceFromEnd > 25 && tagContent.length > 1000 && tagName == "tool_result") {
                 Log.d(TAG, "Optimizing tool result for message at index $messageIndex (distance from end: $distanceFromEnd)")
                tagContent = optimizeToolResult(tagContent)
            }

            // 根据标签类型分配角色
            when (tagName) {
                "status" -> {
                    // 判断status类型
                    if (tagContent.contains("type=\"complete\"") ||
                                    tagContent.contains("type=\"wait_for_user_need\"")
                    ) {
                        segments.add(Pair("assistant", tagContent))
                    } else {
                        segments.add(Pair("user", tagContent))
                    }
                }
                "tool_result" -> {
                    segments.add(Pair("user", tagContent))
                }
                else -> {
                    segments.add(Pair("assistant", tagContent))
                }
            }
        }

        // 合并连续的相同角色消息
        val mergedSegments = mutableListOf<Pair<String, String>>()
        var currentRole = ""
        var currentContent = StringBuilder()

        for (segment in segments) {
            if (segment.first == currentRole) {
                // 如果角色与当前角色相同，则合并内容
                currentContent.append("\n").append(segment.second)
            } else {
                // 角色不同，先保存当前内容（如果有）
                if (currentContent.isNotEmpty()) {
                    mergedSegments.add(Pair(currentRole, currentContent.toString().trim()))
                    currentContent.clear()
                }
                // 更新当前角色和内容
                currentRole = segment.first
                currentContent.append(segment.second)
            }
        }

        // 添加最后一条消息
        if (currentContent.isNotEmpty()) {
            mergedSegments.add(Pair(currentRole, currentContent.toString().trim()))
        }

        // 将合并后的消息添加到对话历史
        conversationHistory.addAll(mergedSegments)
    }

    /**
     * Optimize tool result by selecting the most important parts This helps with memory management
     * for long tool outputs
     */
    fun optimizeToolResult(toolResult: String): String {
        // 如果结果不够长，直接返回
        if (toolResult.length <= 1000) return toolResult

        // 提取工具名称
        val nameMatch = Regex("name=\"([^\"]+)\"").find(toolResult)
        val toolName = nameMatch?.groupValues?.getOrNull(1) ?: "unknown"

        // 为特定工具类型保留完整内容
        if (toolName == "use_package") {
            return toolResult
        }

        // 提取内容
        val tagContent =
                Regex("<[^>]*>(.*?)</[^>]*>", RegexOption.DOT_MATCHES_ALL)
                        .find(toolResult)
                        ?.groupValues
                        ?.getOrNull(1)

        val sb = StringBuilder()

        // 添加工具名称前缀
        sb.append("<tool_result name=\"$toolName\">")

        // 处理提取的内容
        if (!tagContent.isNullOrEmpty()) {
            // 对于XML内容，最多保留800个字符
            val maxContentLength = 800
            val content =
                    if (tagContent.length > maxContentLength) {
                        tagContent.substring(0, 400) +
                                "\n... [content truncated for memory optimization] ...\n" +
                                tagContent.substring(tagContent.length - 400)
                    } else {
                        tagContent
                    }
            sb.append(content)
        } else {
            // 对于非XML内容，从头部和尾部保留重要部分
            sb.append(toolResult.substring(0, 400))
            sb.append("\n... [content truncated for memory optimization] ...\n")
            sb.append(toolResult.substring(toolResult.length - 400))
        }

        // 添加结束标签
        sb.append("</tool_result>")

        return sb.toString()
    }

    /** Build a formatted preferences text string from a PreferenceProfile */
    fun buildPreferencesText(profile: PreferenceProfile): String {
        val parts = mutableListOf<String>()

        if (profile.gender.isNotEmpty()) {
            parts.add("性别: ${profile.gender}")
        }

        if (profile.birthDate > 0) {
            // Convert timestamp to age and format as text
            val today = Calendar.getInstance()
            val birthCal = Calendar.getInstance().apply { timeInMillis = profile.birthDate }
            var age = today.get(Calendar.YEAR) - birthCal.get(Calendar.YEAR)
            // Adjust age if birthday hasn't occurred yet this year
            if (today.get(Calendar.MONTH) < birthCal.get(Calendar.MONTH) ||
                            (today.get(Calendar.MONTH) == birthCal.get(Calendar.MONTH) &&
                                    today.get(Calendar.DAY_OF_MONTH) <
                                            birthCal.get(Calendar.DAY_OF_MONTH))
            ) {
                age--
            }
            parts.add("年龄: ${age}岁")

            // Also add birth date for more precise information
            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            parts.add("出生日期: ${dateFormat.format(java.util.Date(profile.birthDate))}")
        }

        if (profile.personality.isNotEmpty()) {
            parts.add("性格特点: ${profile.personality}")
        }

        if (profile.identity.isNotEmpty()) {
            parts.add("身份认同: ${profile.identity}")
        }

        if (profile.occupation.isNotEmpty()) {
            parts.add("职业: ${profile.occupation}")
        }

        if (profile.aiStyle.isNotEmpty()) {
            parts.add("期待的AI风格: ${profile.aiStyle}")
        }

        return parts.joinToString("; ")
    }

    /** Data class for search-replace operations, used for JSON deserialization. */
    private data class SearchReplaceOperation(val search: String, val replace: String)

    /**
     * Flattens the hierarchical UI node structure into a simple, flat list of key elements.
     * This provides a much cleaner context for the AI to make decisions.
     */
    private fun flattenUiInfo(pageInfo: UIPageResultData): String {
        val clickableElements = mutableListOf<String>()
        val screenTexts = mutableListOf<String>()

        fun traverse(node: SimplifiedUINode) {
            // If the node is clickable, treat it as an atomic unit. We'll gather all text from
            // its entire subtree to form a comprehensive description for the AI.
            if (node.isClickable) {
                val parts = mutableListOf<String>()
                
                // Start by collecting standard properties like resource ID, class, and bounds.
                node.resourceId?.takeIf { it.isNotBlank() }?.let { parts.add("id: $it") }

                // --- NEW: Recursively find all text and content descriptions in the subtree ---
                val descriptiveTexts = mutableListOf<String>()
                fun findTextsRecursively(n: SimplifiedUINode) {
                    n.text?.takeIf { it.isNotBlank() }?.let { descriptiveTexts.add(it) }
                    n.contentDesc?.takeIf { it.isNotBlank() }?.let { descriptiveTexts.add(it) }
                    n.children.forEach(::findTextsRecursively)
                }
                findTextsRecursively(node)

                // Combine all found texts into a single descriptive string. This is crucial for
                // elements where the text label is in a child node of the clickable area.
                val combinedText = descriptiveTexts.distinct().joinToString(" | ")
                if (combinedText.isNotBlank()) {
                    // Using "desc" to signify this is a constructed description. Increased length.
                    parts.add("desc: \"${combinedText.replace("\"", "'").take(80)}\"")
                }
                // --- END NEW ---

                node.className?.let { parts.add("class: ${it.substringAfterLast('.')}") }
                node.bounds?.let { parts.add("bounds: ${it.replace(' ', ',')}") }

                // Only add the element if it has some identifiable information.
                if (parts.isNotEmpty()) {
                    clickableElements.add("[${parts.joinToString(", ")}]")
                }
                // Once an element is identified as clickable, we don't process its children separately.
            } else {
                // If the node is not clickable, add its text for general context and continue traversal.
                node.text?.takeIf { it.isNotBlank() }?.let {
                    screenTexts.add("\"${it.replace("\"", "'").take(70)}\"")
                }
                node.children.forEach(::traverse)
            }
        }

        traverse(pageInfo.uiElements)

        // Use distinct to remove duplicate text entries from non-clickable elements.
        val distinctScreenTexts = screenTexts.distinct()

        return """
        Package: ${pageInfo.packageName}
        Activity: ${pageInfo.activityName}
        Clickable Elements:
        ${clickableElements.joinToString("\n")}
        Screen Text (
        for context):
        ${distinctScreenTexts.joinToString("\n")}
        """.trimIndent()
    }

    private fun JSONObject.toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keysItr = this.keys()
        while (keysItr.hasNext()) {
            val key = keysItr.next()
            var value = this.get(key)
            if (value is JSONObject) {
                value = value.toMap()
            }
            if (value is JSONArray) {
                value = value.toList()
            }
            map[key] = value
        }
        return map
    }

    private fun JSONArray.toList(): List<Any> {
        val list = mutableListOf<Any>()
        for (i in 0 until this.length()) {
            var value = this.get(i)
            if (value is JSONObject) {
                value = value.toMap()
            }
            if (value is JSONArray) {
                value = value.toList()
            }
            list.add(value)
        }
        return list
    }

    private fun createToolFromJson(type: String, arg: Any): AITool {
        val parameters = mutableListOf<ToolParameter>()
        when (arg) {
            is Map<*, *> -> {
                arg.forEach { (key, value) ->
                    val stringValue = when (value) {
                        is Double -> {
                            // If the double has no fractional part, convert to Int string to avoid parse errors.
                            if (value % 1.0 == 0.0) {
                                value.toInt().toString()
                            } else {
                                value.toString()
                            }
                        }
                        else -> value.toString()
                    }
                    parameters.add(ToolParameter(key.toString(), stringValue))
                }
            }
            is String -> {
                 // Fallback for when the AI returns a raw string instead of a JSON object.
                 when (type) {
                     "press_key" -> parameters.add(ToolParameter("key_code", arg))
                     "set_input_text" -> parameters.add(ToolParameter("text", arg))
                     "start_app" -> parameters.add(ToolParameter("package_name", arg))
                 }
            }
        }
        return AITool(type, parameters)
    }

    /**
     * 构建waifu模式的特殊规则文本
     * @return 格式化的waifu规则文本，如果没有规则则返回空字符串
     */
    private suspend fun buildWaifuRulesText(): String {
        val waifuDisableActions = apiPreferences.waifuDisableActionsFlow.first()
        val waifuEnableEmoticons = apiPreferences.waifuEnableEmoticonsFlow.first()
        val waifuEnableSelfie = apiPreferences.waifuEnableSelfieFlow.first()
        val waifuSelfiePrompt = apiPreferences.waifuSelfiePromptFlow.first()
        val waifuRules = mutableListOf<String>()
        
        if (waifuDisableActions) {
            waifuRules.add("**你必须遵守:禁止使用动作表情，禁止描述动作表情，只允许使用纯文本进行对话，禁止使用括号将动作表情包裹起来，禁止输出括号'()',但是会使用更多'呐，嘛~，诶？，嗯…，唔…，昂？，哦'等语气词**")
        }
        
        if (waifuEnableEmoticons) {
            waifuRules.add("**表达情绪规则：你必须在每个句末判断句中包含的情绪或增强语气，并使用<emotion>标签在句末插入情绪状态。后续会根据情绪生成表情包。可用情绪包括：crying, like_you, happy, surprised, miss_you, speechless, angry, confused, sad。例如：<emotion>happy</emotion>、<emotion>miss_you</emotion>等。如果没有这些情绪则不插入。**")
        }
        
        if (waifuEnableSelfie) {
            val selfieRule = buildString {
                append("**绘图（自拍）**: 当你需要自拍时，你会调用绘图功能。")
                append("\n*   **基础关键词**: `$waifuSelfiePrompt`。")
                append("\n*   **自定义内容**: 你会根据主人的要求，在基础关键词后添加表情、动作、穿着、背景等描述。")
                append("\n*   **合影**: 如果需要主人出镜，你会根据指令明确包含`2 girl` （2 girl 代表2个女孩主人也是女孩，主人为黑色长发可爱女生）等关键词。")
            }
            waifuRules.add(selfieRule)
        }
        
        return if (waifuRules.isNotEmpty()) {
            buildString {
                append("\n\n[Extra Rules]")
                waifuRules.forEach { rule ->
                    append("\n- $rule")
                }
            }
        } else ""
    }

    /**
     * Replaces placeholders in the system prompt with actual values.
     * This is necessary because the AI might return placeholders like {{user}} or {{char}}.
     *
     * @param prompt The system prompt containing placeholders.
     * @param aiName The actual AI name to replace {{char}}.
     * @return The prompt with placeholders replaced.
     */
    private suspend fun replacePromptPlaceholders(prompt: String, aiName: String): String {
        var finalPrompt = prompt
        
        // 获取全局用户名
        val globalUserName = userPreferencesManager.globalUserName.first() ?: "User"
        
        // 替换占位符
        finalPrompt = finalPrompt.replace("{{user}}", globalUserName)
        finalPrompt = finalPrompt.replace("{{char}}", aiName)
        
        return finalPrompt
    }

    /**
     * 翻译文本功能
     * @param text 要翻译的文本
     * @param multiServiceManager 多服务管理器
     * @return 翻译后的文本
     */
    suspend fun translateText(text: String, multiServiceManager: MultiServiceManager): String {
        val currentLanguage = LocaleUtils.getCurrentLanguage(context)
        
        // 根据当前语言确定目标语言
        val targetLanguage = when (currentLanguage) {
            "zh" -> "中文"
            "en" -> "English"
            else -> "中文" // 默认翻译为中文
        }
        
        val translationPrompt = """
请将以下文本翻译为$targetLanguage，保持原文的语气和风格：

$text

只返回翻译结果，不要添加任何解释或额外内容。
        """.trim()
        
        val chatHistory = listOf(
            Pair("system", "你是一个专业的翻译助手，能够准确翻译各种语言，并保持原文的语气和风格。")
        )
        
        val contentBuilder = StringBuilder()
        
        try {
            // 获取翻译功能的AIService实例
            val translationService = multiServiceManager.getServiceForFunction(FunctionType.TRANSLATION)
            
            // 获取模型参数
            val modelParameters = multiServiceManager.getModelParametersForFunction(FunctionType.TRANSLATION)
            
            val stream = translationService.sendMessage(
                message = translationPrompt,
                chatHistory = chatHistory,
                modelParameters = modelParameters
            )
            
            stream.collect { content ->
                contentBuilder.append(content)
            }
            
            return contentBuilder.toString().trim()
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * 自动生成工具包描述
     * @param pluginName 工具包名称
     * @param toolDescriptions 工具描述列表
     * @param multiServiceManager 多服务管理器
     * @return 生成的工具包描述
     */
    suspend fun generatePackageDescription(
        pluginName: String,
        toolDescriptions: List<String>,
        multiServiceManager: MultiServiceManager
    ): String {
        if (toolDescriptions.isEmpty()) {
            return ""
        }
        
        val toolList = toolDescriptions.joinToString("\n") { "- $it" }
        
        val descriptionPrompt = """
请为名为"$pluginName"的MCP工具包生成一个简洁的描述。这个工具包包含以下工具：

$toolList

要求：
1. 描述应该简洁明了，不超过100字
2. 重点说明工具包的主要功能和用途
3. 使用中文
4. 不要包含技术细节，要通俗易懂
5. 只返回描述内容，不要添加任何其他文字

请生成描述：
        """.trim()
        
        val chatHistory = listOf(
            Pair("system", "你是一个专业的技术文档撰写助手，擅长为软件工具包编写简洁清晰的功能描述。")
        )
        
        val contentBuilder = StringBuilder()
        
        try {
            // 获取总结功能的AIService实例
            val summaryService = multiServiceManager.getServiceForFunction(FunctionType.SUMMARY)
            
            // 获取模型参数
            val modelParameters = multiServiceManager.getModelParametersForFunction(FunctionType.SUMMARY)
            
            val stream = summaryService.sendMessage(
                message = descriptionPrompt,
                chatHistory = chatHistory,
                modelParameters = modelParameters
            )
            
            stream.collect { content ->
                contentBuilder.append(content)
            }
            
            val result = contentBuilder.toString().trim()
            
            // 如果生成失败或内容为空，返回空字符串表示生成失败
            return if (result.isBlank()) {
                ""
            } else {
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "生成工具包描述时出错", e)
            return ""
        }
    }
}

