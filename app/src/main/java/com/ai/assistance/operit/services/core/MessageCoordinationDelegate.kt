package com.ai.assistance.operit.services.core

import android.util.Log
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.ui.features.chat.viewmodel.UiStateDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 消息协调委托类
 * 负责消息发送、自动总结、附件清理等核心协调逻辑
 */
class MessageCoordinationDelegate(
    private val coroutineScope: CoroutineScope,
    private val chatHistoryDelegate: ChatHistoryDelegate,
    private val messageProcessingDelegate: MessageProcessingDelegate,
    private val tokenStatsDelegate: TokenStatisticsDelegate,
    private val apiConfigDelegate: ApiConfigDelegate,
    private val attachmentDelegate: AttachmentDelegate,
    private val uiStateDelegate: UiStateDelegate,
    private val getEnhancedAiService: () -> EnhancedAIService?,
    private val updateWebServerForCurrentChat: (String) -> Unit,
    private val resetAttachmentPanelState: () -> Unit,
    private val clearReplyToMessage: () -> Unit,
    private val getReplyToMessage: () -> ChatMessage?
) {
    companion object {
        private const val TAG = "MessageCoordinationDelegate"
    }

    // 总结状态
    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()

    /**
     * 发送用户消息
     * 检查是否有当前对话，如果没有则自动创建新对话
     */
    fun sendUserMessage(promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT) {
        // 检查是否有当前对话，如果没有则创建一个新对话
        if (chatHistoryDelegate.currentChatId.value == null) {
            Log.d(TAG, "当前没有活跃对话，自动创建新对话")

            // 使用 coroutineScope 启动协程
            coroutineScope.launch {
                // 使用现有的createNewChat方法创建新对话
                chatHistoryDelegate.createNewChat()

                // 等待对话ID更新
                var waitCount = 0
                while (chatHistoryDelegate.currentChatId.value == null && waitCount < 10) {
                    delay(100) // 短暂延迟等待对话创建完成
                    waitCount++
                }

                if (chatHistoryDelegate.currentChatId.value == null) {
                    Log.e(TAG, "创建新对话超时，无法发送消息")
                    uiStateDelegate.showErrorMessage("无法创建新对话，请重试")
                    return@launch
                }

                Log.d(TAG, "新对话创建完成，ID: ${chatHistoryDelegate.currentChatId.value}，现在发送消息")

                // 对话创建完成后，发送消息
                sendMessageInternal(promptFunctionType)
            }
        } else {
            // 已有对话，直接发送消息
            sendMessageInternal(promptFunctionType)
        }
    }

    /**
     * 内部发送消息的逻辑
     */
    private fun sendMessageInternal(promptFunctionType: PromptFunctionType) {
        // 获取当前聊天ID和工作区路径
        val chatId = chatHistoryDelegate.currentChatId.value
        val currentChat = chatHistoryDelegate.chatHistories.value.find { it.id == chatId }
        val workspacePath = currentChat?.workspace

        // 更新本地Web服务器的聊天ID
        chatId?.let { updateWebServerForCurrentChat(it) }

        // 获取当前附件列表
        val currentAttachments = attachmentDelegate.attachments.value

        // 使用 AIMessageManager 检查是否应该生成总结
        val currentMessages = chatHistoryDelegate.chatHistory.value
        val currentTokens = tokenStatsDelegate.currentWindowSizeFlow.value
        val maxTokens = (apiConfigDelegate.contextLength.value * 1024).toInt()

        val isShouldGenerateSummary = AIMessageManager.shouldGenerateSummary(
            messages = currentMessages,
            currentTokens = currentTokens,
            maxTokens = maxTokens,
            tokenUsageThreshold = apiConfigDelegate.summaryTokenThreshold.value.toDouble(),
            enableSummary = apiConfigDelegate.enableSummary.value,
            enableSummaryByMessageCount = apiConfigDelegate.enableSummaryByMessageCount.value,
            summaryMessageCountThreshold = apiConfigDelegate.summaryMessageCountThreshold.value
        )

        if (isShouldGenerateSummary) {
            _isSummarizing.value = true
            // 1. 在调用挂起函数之前，根据当前的消息快照预先计算好插入位置
            val insertPosition = chatHistoryDelegate.findProperSummaryPosition(currentMessages)

            // 2. 异步触发总结生成
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    getEnhancedAiService()?.let { service ->
                        // 传入快照进行总结
                        val summaryMessage = AIMessageManager.summarizeMemory(service, currentMessages)
                        summaryMessage?.let {
                            // 3. 使用预先计算好的位置插入总结消息
                            chatHistoryDelegate.addSummaryMessage(it, insertPosition)

                            // 4. 更新窗口大小
                            val newHistoryForTokens = AIMessageManager.getMemoryFromMessages(chatHistoryDelegate.chatHistory.value)
                            val chatService = service.getAIServiceForFunction(FunctionType.CHAT)
                            val newWindowSize = chatService.calculateInputTokens("", newHistoryForTokens)
                            val inputTokens = tokenStatsDelegate.cumulativeInputTokensFlow.value
                            val outputTokens = tokenStatsDelegate.cumulativeOutputTokensFlow.value
                            chatHistoryDelegate.saveCurrentChat(inputTokens, outputTokens, newWindowSize)
                            // 更新UI上的显示
                            withContext(Dispatchers.Main) {
                                tokenStatsDelegate.setTokenCounts(inputTokens, outputTokens, newWindowSize)
                            }
                            Log.d(TAG, "总结完成，更新窗口大小为: $newWindowSize")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "生成总结时出错: ${e.message}", e)
                    uiStateDelegate.showErrorMessage("生成聊天总结时出错: ${e.message}")
                } finally {
                    _isSummarizing.value = false
                    // 如果UI还在显示总结状态，则更新为完成
                    if (messageProcessingDelegate.inputProcessingState.value is InputProcessingState.Summarizing) {
                        messageProcessingDelegate.handleInputProcessingState(
                            InputProcessingState.Completed
                        )
                    }
                }
            }
        }

        // 调用messageProcessingDelegate发送消息，并传递附件信息和工作区路径
        messageProcessingDelegate.sendUserMessage(
            attachments = currentAttachments,
            chatId = chatId,
            workspacePath = workspacePath,
            promptFunctionType = promptFunctionType,
            enableThinking = apiConfigDelegate.enableThinkingMode.value,
            thinkingGuidance = apiConfigDelegate.enableThinkingGuidance.value,
            enableMemoryQuery = apiConfigDelegate.enableMemoryQuery.value,
            enableWorkspaceAttachment = !workspacePath.isNullOrBlank(),
            maxTokens = maxTokens,
            //如果记忆总结没开，直接调1；如果已经在生成总结了，那么这个值可以宽松一点，让下一次对话不会被截断
            tokenUsageThreshold = if (!apiConfigDelegate.enableSummary.value) 1.0 
                                 else if (isShouldGenerateSummary) apiConfigDelegate.summaryTokenThreshold.value.toDouble() + 0.5 
                                 else apiConfigDelegate.summaryTokenThreshold.value.toDouble(),
            replyToMessage = getReplyToMessage()
        )

        // 在sendMessageInternal中，添加对nonFatalErrorEvent的收集
        coroutineScope.launch {
            messageProcessingDelegate.nonFatalErrorEvent.collect { errorMessage ->
                uiStateDelegate.showToast(errorMessage)
            }
        }

        // 发送后清空附件列表
        if (currentAttachments.isNotEmpty()) {
            attachmentDelegate.clearAttachments()
        }

        // 重置附件面板状态 - 在发送消息后关闭附件面板
        resetAttachmentPanelState()

        // 清除回复状态
        clearReplyToMessage()
    }

    /**
     * 手动更新记忆
     */
    fun manuallyUpdateMemory() {
        coroutineScope.launch {
            val enhancedAiService = getEnhancedAiService()
            if (enhancedAiService == null) {
                uiStateDelegate.showToast("AI服务不可用，无法更新记忆")
                return@launch
            }
            if (chatHistoryDelegate.chatHistory.value.isEmpty()) {
                uiStateDelegate.showToast("聊天历史为空，无需更新记忆")
                return@launch
            }

            try {
                // Convert ChatMessage list to List<Pair<String, String>>
                val history = chatHistoryDelegate.chatHistory.value.map { it.sender to it.content }
                // Get the last message content
                val lastMessageContent = chatHistoryDelegate.chatHistory.value.lastOrNull()?.content ?: ""

                enhancedAiService.saveConversationToMemory(
                    history,
                    lastMessageContent
                )
                uiStateDelegate.showToast("记忆已手动更新")
            } catch (e: Exception) {
                Log.e(TAG, "手动更新记忆失败", e)
                uiStateDelegate.showErrorMessage("手动更新记忆失败: ${e.message}")
            }
        }
    }
}

