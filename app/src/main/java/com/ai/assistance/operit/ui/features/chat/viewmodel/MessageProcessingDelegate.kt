package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.model.InputProcessingState as EnhancedInputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.util.NetworkUtils
import com.ai.assistance.operit.util.stream.SharedStream
import com.ai.assistance.operit.util.stream.share
import com.ai.assistance.operit.util.WaifuMessageProcessor
import com.ai.assistance.operit.data.preferences.ApiPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 委托类，负责处理消息处理相关功能 */
class MessageProcessingDelegate(
        private val context: Context,
        private val viewModelScope: CoroutineScope,
        private val getEnhancedAiService: () -> EnhancedAIService?,
        private val getChatHistory: () -> List<ChatMessage>,
        private val addMessageToChat: (ChatMessage) -> Unit,
        private val saveCurrentChat: () -> Unit,
        private val showErrorMessage: (String) -> Unit,
        private val updateChatTitle: (chatId: String, title: String) -> Unit,
        private val onTurnComplete: () -> Unit
        // toolHandler is no longer needed here
) {
    companion object {
        private const val TAG = "MessageProcessingDelegate"
    }

    private val _userMessage = MutableStateFlow("")
    val userMessage: StateFlow<String> = _userMessage.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _inputProcessingState = MutableStateFlow<EnhancedInputProcessingState>(EnhancedInputProcessingState.Idle)
    val inputProcessingState: StateFlow<EnhancedInputProcessingState> = _inputProcessingState.asStateFlow()

    private val _scrollToBottomEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToBottomEvent = _scrollToBottomEvent.asSharedFlow()

    private val _nonFatalErrorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val nonFatalErrorEvent = _nonFatalErrorEvent.asSharedFlow()

    // 当前活跃的AI响应流
    private var currentResponseStream: SharedStream<String>? = null
    // 添加一个Job来跟踪流收集协程
    private var streamCollectionJob: Job? = null

    // 获取当前活跃的AI响应流
    fun getCurrentResponseStream(): SharedStream<String>? = currentResponseStream

    init {
        Log.d(TAG, "MessageProcessingDelegate初始化: 创建滚动事件流")
    }

    fun updateUserMessage(message: String) {
        _userMessage.value = message
    }

    fun scrollToBottom() {
        _scrollToBottomEvent.tryEmit(Unit)
    }

    fun sendUserMessage(
            attachments: List<AttachmentInfo> = emptyList(),
            chatId: String? = null,
            workspacePath: String? = null,
            promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
            enableThinking: Boolean = false,
            thinkingGuidance: Boolean = false,
            enableMemoryAttachment: Boolean = true, // 新增参数
            enableWorkspaceAttachment: Boolean = false, // 新增工作区附着参数
            maxTokens: Int,
            tokenUsageThreshold: Double
    ) {
        if (_userMessage.value.isBlank() && attachments.isEmpty()) return
        if (_isLoading.value) return

        val messageText = _userMessage.value.trim()
        _userMessage.value = ""
        _isLoading.value = true
        _inputProcessingState.value = EnhancedInputProcessingState.Processing("正在处理消息...")

        viewModelScope.launch(Dispatchers.IO) {
            // 检查这是否是聊天中的第一条用户消息
            val isFirstMessage = getChatHistory().none { it.sender == "user" || it.sender == "ai" }
            if (isFirstMessage && chatId != null) {
                val newTitle =
                    when {
                        messageText.isNotBlank() -> messageText
                        attachments.isNotEmpty() -> attachments.first().fileName
                        else -> context.getString(R.string.new_conversation)
                    }
                updateChatTitle(chatId, newTitle)
            }

            Log.d(TAG, "开始处理用户消息：附件数量=${attachments.size}")

            // 1. 使用 AIMessageManager 构建最终消息
            val finalMessageContent = AIMessageManager.buildUserMessageContent(
                messageText,
                attachments,
                enableMemoryAttachment,
                enableWorkspaceAttachment,
                workspacePath
            )

            addMessageToChat(ChatMessage(sender = "user", content = finalMessageContent))

            lateinit var aiMessage: ChatMessage
            try {
                // if (!NetworkUtils.isNetworkAvailable(context)) {
                //     withContext(Dispatchers.Main) { showErrorMessage("网络连接不可用") }
                //     _isLoading.value = false
                //     _inputProcessingState.value = EnhancedInputProcessingState.Idle
                //     return@launch
                // }

                val service =
                    getEnhancedAiService()
                        ?: run {
                            withContext(Dispatchers.Main) { showErrorMessage("AI服务未初始化") }
                            _isLoading.value = false
                            _inputProcessingState.value = EnhancedInputProcessingState.Idle
                            return@launch
                        }

                val startTime = System.currentTimeMillis()
                val deferred = CompletableDeferred<Unit>()

                // 2. 使用 AIMessageManager 发送消息
                val responseStream = AIMessageManager.sendMessage(
                    enhancedAiService = service,
                    messageContent = finalMessageContent,
                    chatHistory = getChatHistory(),
                    workspacePath = workspacePath,
                    promptFunctionType = promptFunctionType,
                    enableThinking = enableThinking,
                    thinkingGuidance = thinkingGuidance,
                    enableMemoryAttachment = enableMemoryAttachment, // Pass it here
                    maxTokens = maxTokens,
                    tokenUsageThreshold = tokenUsageThreshold,
                    onNonFatalError = { error ->
                        _nonFatalErrorEvent.emit(error)
                    }
                )

                // 将字符串流共享，以便多个收集器可以使用
                val sharedCharStream =
                    responseStream.share(
                        scope = viewModelScope,
                        replay = 0, // 不重放历史消息
                        onComplete = {
                            deferred.complete(Unit)
                            Log.d(
                                TAG,
                                "共享流完成，耗时: ${System.currentTimeMillis() - startTime}ms"
                            )
                            currentResponseStream = null // 清除本地引用
                        }
                    )

                // 更新当前响应流，使其可以被其他组件（如悬浮窗）访问
                currentResponseStream = sharedCharStream

                aiMessage = ChatMessage(
                    sender = "ai", 
                    contentStream = sharedCharStream,
                    timestamp = System.currentTimeMillis()+50
                )
                Log.d(
                    TAG,
                    "创建带流的AI消息, stream is null: ${aiMessage.contentStream == null}, timestamp: ${aiMessage.timestamp}"
                )

                // 检查是否启用waifu模式来决定是否显示流式过程
                val apiPreferences = ApiPreferences.getInstance(context)
                val isWaifuModeEnabled = apiPreferences.enableWaifuModeFlow.first()
                
                // 只有在非waifu模式下才添加初始的AI消息
                if (!isWaifuModeEnabled) {
                    withContext(Dispatchers.Main) { addMessageToChat(aiMessage) }
                }
                
                // 启动一个独立的协程来收集流内容并持续更新数据库
                streamCollectionJob =
                    viewModelScope.launch(Dispatchers.IO) {
                        val contentBuilder = StringBuilder()
                        sharedCharStream.collect { chunk ->
                            contentBuilder.append(chunk)
                            val content = contentBuilder.toString()
                            val updatedMessage = aiMessage.copy(content = content)
                            // 防止后续读取不到
                            aiMessage.content = content
                            
                            // 只有在非waifu模式下才显示流式更新
                            if (!isWaifuModeEnabled) {
                                addMessageToChat(updatedMessage)
                                _scrollToBottomEvent.tryEmit(Unit)
                            }
                        }
                    }

                // 等待流完成，以便finally块可以正确执行来更新UI状态
                deferred.await()

                Log.d(TAG, "AI响应处理完成，总耗时: ${System.currentTimeMillis() - startTime}ms")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "消息发送被取消")
                    throw e
                }
                Log.e(TAG, "发送消息时出错", e)
                withContext(Dispatchers.Main) { showErrorMessage("发送消息失败: ${e.message}") }
            } finally {
                // 修改为使用 try-catch 来检查变量是否已初始化，而不是使用 ::var.isInitialized
                try {
                    // 尝试访问 aiMessage，如果未初始化会抛出 UninitializedPropertyAccessException
                    val finalContent = aiMessage.content
                    
                    // 检查是否启用了waifu模式并且内容适合分句
                    withContext(Dispatchers.IO) {
                        val apiPreferences = ApiPreferences.getInstance(context)
                        val isWaifuModeEnabled = apiPreferences.enableWaifuModeFlow.first()
                        
                        if (isWaifuModeEnabled && WaifuMessageProcessor.shouldSplitMessage(finalContent)) {
                            Log.d(TAG, "Waifu模式已启用，开始创建独立消息，内容长度: ${finalContent.length}")
                            
                            // 获取配置的字符延迟时间和标点符号设置
                            val charDelay = apiPreferences.waifuCharDelayFlow.first().toLong()
                            val removePunctuation = apiPreferences.waifuRemovePunctuationFlow.first()
                            
                            // 删除原始的空消息（因为在waifu模式下我们没有显示流式过程）
                            // 不需要显示空的AI消息
                            
                            // 启动一个协程来创建独立的句子消息
                            viewModelScope.launch(Dispatchers.IO) {
                                Log.d(TAG, "开始Waifu独立消息创建，字符延迟: ${charDelay}ms/字符，移除标点: $removePunctuation")
                                
                                // 分割句子
                                val sentences = WaifuMessageProcessor.splitMessageBySentences(finalContent, removePunctuation)
                                Log.d(TAG, "分割出${sentences.size}个句子")
                                
                                // 为每个句子创建独立的消息
                                for ((index, sentence) in sentences.withIndex()) {
                                    // 根据当前句子字符数计算延迟（模拟说话时间）
                                    val characterCount = sentence.length
                                    val calculatedDelay = WaifuMessageProcessor.calculateSentenceDelay(characterCount, charDelay)
                                    
                                    if (index > 0) {
                                        // 如果不是第一句，先延迟再发送
                                        Log.d(TAG, "当前句字符数: $characterCount, 计算延迟: ${calculatedDelay}ms")
                                        delay(calculatedDelay)
                                    }
                                    
                                    Log.d(TAG, "创建第${index + 1}个独立消息: $sentence")
                                    
                                    // 创建独立的AI消息
                                    val sentenceMessage = ChatMessage(
                                        sender = "ai",
                                        content = sentence,
                                        contentStream = null,
                                        timestamp = System.currentTimeMillis() + index * 10 // 确保时间戳不同
                                    )
                                    
                                    withContext(Dispatchers.Main) {
                                        addMessageToChat(sentenceMessage)
                                        _scrollToBottomEvent.tryEmit(Unit)
                                    }
                                }
                                
                                Log.d(TAG, "Waifu独立消息创建完成")
                            }
                        } else {
                            // 普通模式，直接清理流
                            val finalMessage = aiMessage.copy(content = finalContent, contentStream = null)
                            withContext(Dispatchers.Main) { addMessageToChat(finalMessage) }
                        }
                    }
                } catch (e: UninitializedPropertyAccessException) {
                    // aiMessage 未初始化，忽略清理步骤
                    Log.d(TAG, "AI消息未初始化，跳过流清理步骤")
                } catch (e: Exception) {
                    Log.e(TAG, "处理waifu模式时出错", e)
                    // 如果waifu模式处理失败，回退到普通模式
                    try {
                        val finalContent = aiMessage.content
                        val finalMessage = aiMessage.copy(content = finalContent, contentStream = null)
                        withContext(Dispatchers.Main) { addMessageToChat(finalMessage) }
                    } catch (ex: Exception) {
                        Log.e(TAG, "回退到普通模式也失败", ex)
                    }
                }

                // 清理job引用
                streamCollectionJob = null

                // 添加一个短暂的延迟，以确保UI有足够的时间来渲染最后一个数据块
                // 这有助于解决因竞态条件导致的UI内容（如状态标签）有时无法显示的问题
                withContext(Dispatchers.IO) { delay(100) }
                withContext(Dispatchers.Main) {
                    // 状态现在由 EnhancedAIService 的 inputProcessingState 控制，这里不再重置
                    // _isLoading.value = false
                    // _isProcessingInput.value = false

                    // 即使流处理完成，也需要保存一次聊天记录
                    onTurnComplete()
                }
            }
        }
    }

    fun cancelCurrentMessage() {
        viewModelScope.launch {
            _isLoading.value = false
            _inputProcessingState.value = EnhancedInputProcessingState.Idle

            // 取消正在进行的流收集
            streamCollectionJob?.cancel()
            streamCollectionJob = null
            Log.d(TAG, "流收集任务已取消")

            withContext(Dispatchers.IO) {
                getEnhancedAiService()?.cancelConversation()
                saveCurrentChat()
            }
        }
    }

    fun setInputProcessingState(isProcessing: Boolean, message: String) {
        if(isProcessing) {
            _inputProcessingState.value = EnhancedInputProcessingState.Processing(message)
        } else {
            _inputProcessingState.value = EnhancedInputProcessingState.Idle
        }
    }

    /**
     * 处理来自 EnhancedAIService 的输入处理状态
     * @param state 输入处理状态
     */
    fun handleInputProcessingState(state: EnhancedInputProcessingState) {
        viewModelScope.launch(Dispatchers.Main) {
            _inputProcessingState.value = state
            _isLoading.value = state !is EnhancedInputProcessingState.Idle && state !is EnhancedInputProcessingState.Completed

            when (state) {
                is EnhancedInputProcessingState.Error -> {
                    showErrorMessage(state.message)
                }
                else -> {
                    // Do nothing for other states as they are handled by the state flow itself
                }
            }
        }
    }
}
