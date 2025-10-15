package com.ai.assistance.operit.core.chat

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.plan.PlanModeManager
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.MemoryQueryResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.ui.features.chat.webview.workspace.process.WorkspaceAttachmentProcessor
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.util.stream.share
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 单例对象，负责管理与 EnhancedAIService 的所有通信。
 *
 * 主要职责:
 * - 构建发送给AI的消息请求。
 * - 发送消息并处理流式响应。
 * - 请求生成对话总结。
 *
 * 设计原则:
 * - **无状态**: 本身不持有任何特定聊天的状态。所有需要的数据都通过方法参数传入。
 * - **职责明确**: 仅处理与AI服务的交互，UI更新和数据持久化由调用方负责。
 * - **封装逻辑**: 内部封装了与AI交互的策略，如是否需要总结、如何从历史中提取记忆等。
 */
object AIMessageManager {
    private const val TAG = "AIMessageManager"
    // 聊天总结的消息数量阈值 - 移除硬编码，改用动态设置
    // private const val SUMMARY_CHUNK_SIZE = 4

    // 使用独立的协程作用域，确保AI操作的生命周期独立于任何特定的ViewModel
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var activeEnhancedAiService: EnhancedAIService? = null
    private var activePlanModeManager: PlanModeManager? = null

    private lateinit var toolHandler: AIToolHandler
    private lateinit var context: Context
    private lateinit var apiPreferences: ApiPreferences

    fun initialize(context: Context) {
        this.context = context
        toolHandler = AIToolHandler.getInstance(context)
        apiPreferences = ApiPreferences.getInstance(context)
    }

    /**
     * 构建用户消息的完整内容，包括附件和记忆标签。
     *
     * @param messageText 用户输入的原始文本。
     * @param attachments 附件列表。
     * @param enableMemoryAttachment 是否启用记忆附着功能。
     * @param enableWorkspaceAttachment 是否启用工作区附着功能。
     * @param workspacePath 工作区路径。
     * @return 格式化后的完整消息字符串。
     */
    suspend fun buildUserMessageContent(
        messageText: String,
        attachments: List<AttachmentInfo>,
        enableMemoryAttachment: Boolean,
        enableWorkspaceAttachment: Boolean = false,
        workspacePath: String? = null,
        replyToMessage: ChatMessage? = null // 新增回复消息参数
    ): String {
        // 1. 构建回复标签（如果有回复消息）
        val replyTag = replyToMessage?.let { message ->
            val cleanContent = message.content
                .replace(Regex("<[^>]*>"), "") // 移除XML标签
                .trim()
                .let { if (it.length > 100) it.take(100) + "..." else it }
            
            val roleName = message.roleName ?: if (message.sender == "ai") "AI" else "用户"
            val instruction = "用户正在回复你之前的这条消息："
            "<reply_to sender=\"${roleName}\" timestamp=\"${message.timestamp}\">${instruction}\"${cleanContent}\"</reply_to>"
        } ?: ""

        // 2. 根据开关决定是否查询知识库
        val memoryTag = if (enableMemoryAttachment && messageText.isNotBlank() && !messageText.contains("<memory>", ignoreCase = true)) {
            val queryTool = AITool(
                name = "query_knowledge_library",
                parameters = listOf(ToolParameter("query", messageText))
            )
            val result = toolHandler.executeTool(queryTool)
            if (result.success && result.result is MemoryQueryResultData) {
                val memoryData = result.result as MemoryQueryResultData
                if (memoryData.memories.isNotEmpty()) {
                    val instruction = "RAG Memory"
                    val memoryContent = memoryData.toString()
                    "<memory>${instruction}\n---\n${memoryContent}</memory>"
                } else ""
            } else ""
        } else ""

        // 3. 根据开关决定是否生成工作区附着
        val workspaceTag = if (enableWorkspaceAttachment && !workspacePath.isNullOrBlank() && !messageText.contains("<workspace_attachment>", ignoreCase = true)) {
            try {
                val workspaceContent = WorkspaceAttachmentProcessor.generateWorkspaceAttachment(
                    context = context,
                    workspacePath = workspacePath
                )
                "<workspace_attachment>$workspaceContent</workspace_attachment>"
            } catch (e: Exception) {
                Log.e(TAG, "生成工作区附着失败", e)
                ""
            }
        } else ""

        // 4. 构建附件标签
        val attachmentTags = if (attachments.isNotEmpty()) {
            attachments.joinToString(" ") { attachment ->
                "<attachment " +
                        "id=\"${attachment.filePath}\" " +
                        "filename=\"${attachment.fileName}\" " +
                        "type=\"${attachment.mimeType}\" " +
                        (if (attachment.fileSize > 0) "size=\"${attachment.fileSize}\" " else "") +
                        (if (attachment.content.isNotEmpty()) "content=\"${attachment.content}\" " else "") +
                        "/>"
            }
        } else ""

        // 5. 组合最终消息
        return listOf(messageText, attachmentTags, memoryTag, workspaceTag, replyTag)
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    /**
     * 发送消息给AI服务。
     *
     * @param enhancedAiService AI服务实例。
     * @param messageContent 已经构建好的完整消息内容。
     * @param chatHistory 完整的聊天历史记录。
     * @param workspacePath 当前工作区路径。
     * @param promptFunctionType 提示功能类型。
     * @param enableThinking 是否启用思考过程。
     * @param thinkingGuidance 是否启用思考引导。
     * @param enableMemoryAttachment 是否启用记忆附着功能。
     * @return 包含AI响应流的ChatMessage对象。
     */
    suspend fun sendMessage(
        enhancedAiService: EnhancedAIService,
        messageContent: String,
        chatHistory: List<ChatMessage>,
        workspacePath: String?,
        promptFunctionType: PromptFunctionType,
        enableThinking: Boolean,
        thinkingGuidance: Boolean,
        enableMemoryAttachment: Boolean, // Add this parameter
        maxTokens: Int,
        tokenUsageThreshold: Double,
        onNonFatalError: suspend (error: String) -> Unit
    ): SharedStream<String> {
        activeEnhancedAiService = enhancedAiService // Keep a reference to the service for cancellation
        val memory = getMemoryFromMessages(chatHistory)
        
        // 检查是否启用了深度搜索模式（计划模式）
        val isDeepSearchEnabled = apiPreferences.enableAiPlanningFlow.first()
        
        return withContext(Dispatchers.IO) {
            if (isDeepSearchEnabled) {
                // 创建计划模式管理器
                val planModeManager = PlanModeManager(context, enhancedAiService)
                
                // 检查消息是否适合使用深度搜索模式
                val shouldUseDeepSearch = planModeManager.shouldUseDeepSearchMode(messageContent)
                
                if (shouldUseDeepSearch) {
                    activePlanModeManager = planModeManager // Store active manager
                    Log.d(TAG, "启用深度搜索模式处理消息")
                    
                    // 设置执行计划的特定UI状态
                    enhancedAiService.setInputProcessingState(
                        com.ai.assistance.operit.data.model.InputProcessingState.ExecutingPlan("正在执行深度搜索...")
                    )
                    
                    // 使用深度搜索模式
                    return@withContext planModeManager.executeDeepSearchMode(
                        userMessage = messageContent,
                        chatHistory = memory,
                        workspacePath = workspacePath,
                        maxTokens = maxTokens,
                        tokenUsageThreshold = tokenUsageThreshold,
                        onNonFatalError = onNonFatalError
                    ).share(scope)
                } else {
                    activePlanModeManager = null // Clear manager if not used
                    Log.d(TAG, "消息不适合深度搜索模式，使用普通模式")
                }
            } else {
                activePlanModeManager = null // Clear manager if disabled
            }
            
            // 使用普通模式
            enhancedAiService.sendMessage(
                message = messageContent,
                chatHistory = memory, // Correct parameter name is chatHistory
                workspacePath = workspacePath,
                promptFunctionType = promptFunctionType,
                enableThinking = enableThinking,
                thinkingGuidance = thinkingGuidance,
                enableMemoryAttachment = enableMemoryAttachment, // Pass it here
                maxTokens = maxTokens,
                tokenUsageThreshold = tokenUsageThreshold,
                onNonFatalError = onNonFatalError
            ).share(scope) // 使用.share()将其转换为共享流
        }
    }

    /**
     * 取消当前正在进行的AI操作。
     * 这会同时尝试取消计划执行（如果正在进行）和底层的AI流。
     */
    fun cancelCurrentOperation() {
        Log.d(TAG, "请求取消当前AI操作...")

        // 1. 取消计划模式（如果正在运行）
        activePlanModeManager?.let {
            Log.d(TAG, "正在取消计划模式执行...")
            it.cancel()
            activePlanModeManager = null // 取消后清除引用
        }

        // 2. 取消底层的 EnhancedAIService（处理普通流和工具调用）
        activeEnhancedAiService?.let {
            Log.d(TAG, "正在取消 EnhancedAIService 对话...")
            it.cancelConversation()
        }

        Log.d(TAG, "AI操作取消请求已发送。")
    }

    /**
     * 请求AI服务生成对话总结。
     *
     * @param enhancedAiService AI服务实例。
     * @param messages 需要总结的消息列表。
     * @return 包含总结内容的ChatMessage对象，如果无需总结或总结失败则返回null。
     */
    suspend fun summarizeMemory(
        enhancedAiService: EnhancedAIService,
        messages: List<ChatMessage>
    ): ChatMessage? {
        val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }
        val previousSummary = if (lastSummaryIndex != -1) messages[lastSummaryIndex].content.trim() else null

        val messagesToSummarize = when {
            lastSummaryIndex == -1 -> messages.filter { it.sender == "user" || it.sender == "ai" }
            else -> messages.subList(lastSummaryIndex + 1, messages.size)
                .filter { it.sender == "user" || it.sender == "ai" }
        }

        if (messagesToSummarize.isEmpty()) {
            Log.d(TAG, "没有新消息需要总结")
            return null
        }

        val conversationToSummarize = messagesToSummarize.mapIndexed { index, message ->
            val role = if (message.sender == "user") "user" else "assistant"
            val content = if (role == "user") {
                message.content.replace(Regex("<memory>.*?</memory>", RegexOption.DOT_MATCHES_ALL), "").trim()
            } else {
                message.content
            }
            Pair(role, "#${index + 1}: ${content}")
        }

        return try {
            Log.d(TAG, "开始使用AI生成对话总结：总结 ${messagesToSummarize.size} 条消息")
            val summary = enhancedAiService.generateSummary(conversationToSummarize, previousSummary)
            Log.d(TAG, "AI生成总结完成: ${summary.take(50)}...")

            if (summary.isBlank()) {
                Log.e(TAG, "AI生成的总结内容为空，放弃本次总结")
                null
            } else {
                ChatMessage(
                    sender = "summary",
                    content = summary.trim(),
                    timestamp = System.currentTimeMillis(),
                    roleName = "system" // 总结消息的角色名
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "AI生成总结过程中发生异常", e)
            null
        }
    }

    /**
     * 判断是否应该生成对话总结。
     *
     * @param messages 完整的消息列表。
     * @param currentTokens 当前上下文的token数量。
     * @param maxTokens 上下文窗口的最大token数量。
     * @return 如果应该生成总结，则返回true。
     */
    fun shouldGenerateSummary(
        messages: List<ChatMessage>,
        currentTokens: Int,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        enableSummary: Boolean,
        enableSummaryByMessageCount: Boolean,
        summaryMessageCountThreshold: Int
    ): Boolean {
        // 首先检查总结功能是否启用
        if (!enableSummary) {
            return false
        }

        // 检查Token阈值
        if (maxTokens > 0) {
            val usageRatio = currentTokens.toDouble() / maxTokens.toDouble()
            if (usageRatio >= tokenUsageThreshold) {
                Log.d(TAG, "Token usage ($usageRatio) exceeds threshold ($tokenUsageThreshold). Triggering summary.")
                return true
            }
        }

        // 检查消息条数阈值（如果启用）
        if (enableSummaryByMessageCount) {
            val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }
            val relevantMessages = if (lastSummaryIndex != -1) {
                messages.subList(lastSummaryIndex + 1, messages.size)
            } else {
                messages
            }
            val userAiMessagesSinceLastSummary = relevantMessages.count { it.sender == "user"}

            if (userAiMessagesSinceLastSummary >= summaryMessageCountThreshold) {
                Log.d(TAG, "自上次总结后新消息数量达到阈值 ($userAiMessagesSinceLastSummary)，生成总结.")
                return true
            }
        }

        Log.d(TAG, "未达到生成总结的条件. Token使用率: ${if (maxTokens > 0) currentTokens.toDouble() / maxTokens else 0.0}")
        return false
    }

    /**
     * 从完整的聊天记录中提取用于AI上下文的“记忆”。
     * 这会获取上次总结之后的所有消息。
     *
     * @param messages 完整的聊天记录。
     * @return 一个Pair列表，包含角色和内容，用于AI请求。
     */
    fun getMemoryFromMessages(messages: List<ChatMessage>): List<Pair<String, String>> {
        val lastSummaryIndex = messages.indexOfLast { it.sender == "summary" }
        val relevantMessages = if (lastSummaryIndex != -1) {
            messages.subList(lastSummaryIndex, messages.size)
        } else {
            messages
        }
        return relevantMessages
            .filter { it.sender == "user" || it.sender == "ai" || it.sender == "summary" }
            .map {
                val role = if (it.sender == "ai") "assistant" else "user" // "summary" is treated as user-side context
                Pair(role, it.content)
            }
    }
} 