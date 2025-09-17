package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.core.invitation.InvitationManager
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.InvitationRepository
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.ui.features.chat.attachments.AttachmentManager
import com.ai.assistance.operit.ui.features.chat.webview.LocalWebServer
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import com.ai.assistance.operit.ui.permissions.ToolPermissionSystem
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.ai.assistance.operit.api.voice.VoiceService
import com.ai.assistance.operit.api.voice.VoiceServiceFactory

class ChatViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    // Invitation and feature unlock management
    private val invitationRepository = InvitationRepository(context)
    private val invitationManager = InvitationManager(context)

    val isWorkspaceUnlocked = invitationRepository.isWorkspaceUnlockedFlow
    val isFloatingWindowUnlocked = invitationRepository.isFloatingWindowUnlockedFlow
    val invitationCount = invitationRepository.invitationCountFlow

    private val _showInvitationExplanation = MutableStateFlow(false)
    val showInvitationExplanation = _showInvitationExplanation.asStateFlow()

    private val _showInvitationPanel = MutableStateFlow(false)
    val showInvitationPanel = _showInvitationPanel.asStateFlow()

    private val _generatedInvitationMessage = MutableStateFlow("")
    val generatedInvitationMessage = _generatedInvitationMessage.asStateFlow()

    // 添加语音服务
    private var voiceService: VoiceService? = null

    // 添加语音播放状态
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // 添加自动朗读状态
    private val _isAutoReadEnabled = MutableStateFlow(false)
    val isAutoReadEnabled: StateFlow<Boolean> = _isAutoReadEnabled.asStateFlow()

    // 添加回复相关状态
    private val _replyToMessage = MutableStateFlow<ChatMessage?>(null)
    val replyToMessage: StateFlow<ChatMessage?> = _replyToMessage.asStateFlow()

    fun showInvitationPanel() {
        _generatedInvitationMessage.value = invitationManager.generateInvitationMessage()
        _showInvitationPanel.value = true
    }

    fun dismissInvitationPanel() {
        _showInvitationPanel.value = false
    }

    fun onInvitationExplanationConfirmed() {
        // After user confirms, hide explanation and show the main panel
        _showInvitationExplanation.value = false
        showInvitationPanel()
    }

    fun dismissInvitationExplanation() {
        _showInvitationExplanation.value = false
    }

    fun shareInvitationMessage(message: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, message)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    fun verifyAndHandleConfirmationCode(code: String) {
        viewModelScope.launch {
            val success = invitationManager.verifyConfirmationCode(code)
            if (success) {
                showToast("邀请成功！感谢你的助力！")
                // Dismiss the panel on success
                dismissInvitationPanel()
            } else {
                showToast("返回码无效，请检查后重试。")
            }
        }
    }


    // 服务收集器设置状态跟踪
    private var serviceCollectorSetupComplete = false

    // API服务
    private var enhancedAiService: EnhancedAIService? = null

    // 工具处理器
    private val toolHandler = AIToolHandler.getInstance(context)

    // 工具权限系统
    private val toolPermissionSystem = ToolPermissionSystem.getInstance(context)

    // 附件管理器
    private val attachmentManager = AttachmentManager(context, toolHandler)

    // 委托类
    val uiStateDelegate = UiStateDelegate()
    private val tokenStatsDelegate =
            TokenStatisticsDelegate(
                    viewModelScope = viewModelScope,
                    getEnhancedAiService = { enhancedAiService }
            )
    private val apiConfigDelegate =
            ApiConfigDelegate(
                    context = context,
                    viewModelScope = viewModelScope,
                    onConfigChanged = { service ->
                        enhancedAiService = service
                        // API配置变更后，异步设置服务收集器
                        viewModelScope.launch {
                            // 重置服务收集器状态，因为服务实例已变更
                            serviceCollectorSetupComplete = false
                            Log.d(TAG, "API配置变更，重置服务收集器状态并重新设置")
                            setupServiceCollectors()
                            tokenStatsDelegate.setupCollectors()
                        }
                    }
            )


    // Break circular dependency with lateinit
    private lateinit var chatHistoryDelegate: ChatHistoryDelegate
    private lateinit var messageProcessingDelegate: MessageProcessingDelegate
    private lateinit var floatingWindowDelegate: FloatingWindowDelegate

    // Use lazy initialization for exposed properties to avoid circular reference issues
    // API配置相关
    val apiKey: StateFlow<String> by lazy { apiConfigDelegate.apiKey }
    val apiEndpoint: StateFlow<String> by lazy { apiConfigDelegate.apiEndpoint }
    val modelName: StateFlow<String> by lazy { apiConfigDelegate.modelName }
    val apiProviderType: StateFlow<ApiProviderType> by lazy { apiConfigDelegate.apiProviderType }
    val isConfigured: StateFlow<Boolean> by lazy { apiConfigDelegate.isConfigured }
    val isApiConfigInitialized: StateFlow<Boolean> by lazy { apiConfigDelegate.isInitialized }

    private val _shouldShowConfigDialog = MutableStateFlow(false)
    val shouldShowConfigDialog: StateFlow<Boolean> = _shouldShowConfigDialog.asStateFlow()

    fun onConfigDialogConfirmed() {
        _shouldShowConfigDialog.value = false
    }

    fun showConfigurationScreen() {
        _shouldShowConfigDialog.value = true
    }

    val enableAiPlanning: StateFlow<Boolean> by lazy { apiConfigDelegate.enableAiPlanning }
    val keepScreenOn: StateFlow<Boolean> by lazy { apiConfigDelegate.keepScreenOn }

    // 思考模式和思考引导状态现在由ApiConfigDelegate管理
    val enableThinkingMode: StateFlow<Boolean> by lazy { apiConfigDelegate.enableThinkingMode }
    val enableThinkingGuidance: StateFlow<Boolean> by lazy { apiConfigDelegate.enableThinkingGuidance }
    val enableMemoryAttachment: StateFlow<Boolean> by lazy { apiConfigDelegate.enableMemoryAttachment }

    val summaryTokenThreshold: StateFlow<Float> by lazy { apiConfigDelegate.summaryTokenThreshold }

    // 上下文长度
    val maxWindowSizeInK: StateFlow<Float> by lazy { apiConfigDelegate.contextLength }

    // 聊天历史相关
    val chatHistory: StateFlow<List<ChatMessage>> by lazy { chatHistoryDelegate.chatHistory }
    val showChatHistorySelector: StateFlow<Boolean> by lazy {
        chatHistoryDelegate.showChatHistorySelector
    }
    val chatHistories: StateFlow<List<ChatHistory>> by lazy { chatHistoryDelegate.chatHistories }
    val currentChatId: StateFlow<String?> by lazy { chatHistoryDelegate.currentChatId }

    // 消息处理相关
    val userMessage: StateFlow<String> by lazy { messageProcessingDelegate.userMessage }
    val isLoading: StateFlow<Boolean> by lazy { messageProcessingDelegate.isLoading }
    val inputProcessingState: StateFlow<com.ai.assistance.operit.data.model.InputProcessingState> by lazy {
        messageProcessingDelegate.inputProcessingState
    }

    val scrollToBottomEvent: SharedFlow<Unit> by lazy {
        messageProcessingDelegate.scrollToBottomEvent
    }

    // UI状态相关
    val errorMessage: StateFlow<String?> by lazy { uiStateDelegate.errorMessage }
    val popupMessage: StateFlow<String?> by lazy { uiStateDelegate.popupMessage }
    val toastEvent: StateFlow<String?> by lazy { uiStateDelegate.toastEvent }
    val masterPermissionLevel: StateFlow<PermissionLevel> by lazy {
        uiStateDelegate.masterPermissionLevel
    }

    // 聊天统计相关
    val currentWindowSize: StateFlow<Int> by lazy { tokenStatsDelegate.currentWindowSizeFlow }
    val inputTokenCount: StateFlow<Int> by lazy { tokenStatsDelegate.cumulativeInputTokensFlow }
    val outputTokenCount: StateFlow<Int> by lazy { tokenStatsDelegate.cumulativeOutputTokensFlow }
    val perRequestTokenCount: StateFlow<Pair<Int, Int>?> by lazy { tokenStatsDelegate.perRequestTokenCountFlow }



    // 悬浮窗相关
    val isFloatingMode: StateFlow<Boolean> by lazy { floatingWindowDelegate.isFloatingMode }

    // 附件相关
    val attachments: StateFlow<List<AttachmentInfo>> by lazy { attachmentManager.attachments }

    // 添加一个用于跟踪附件面板状态的变量
    private val _attachmentPanelState = MutableStateFlow(false)
    val attachmentPanelState: StateFlow<Boolean> = _attachmentPanelState

    // 添加WebView显示状态的状态流
    private val _showWebView = MutableStateFlow(false)
    val showWebView: StateFlow<Boolean> = _showWebView

    // 添加AI电脑显示状态的状态流
    private val _showAiComputer = MutableStateFlow(false)
    val showAiComputer: StateFlow<Boolean> = _showAiComputer

    private val _isSummarizing = MutableStateFlow(false)

    // 添加WebView刷新控制流 - 使用Int计数器避免重复刷新问题
    private val _webViewRefreshCounter = MutableStateFlow(0)
    val webViewRefreshCounter: StateFlow<Int> = _webViewRefreshCounter

    // 文件选择相关回调
    private var fileChooserCallback: ((Int, Intent?) -> Unit)? = null

    init {
        // Initialize delegates in correct order to avoid circular references
        initializeDelegates()

        // Setup additional components
        setupPermissionSystemCollection()
        setupAttachmentManagerToastCollection()

        // 初始化语音服务
        initializeVoiceService()

        // 观察ApiConfigDelegate的初始化状态
        viewModelScope.launch {
            isApiConfigInitialized.collect { initialized ->
                if (initialized) {
                    checkConfigAndShowDialog()
                }
            }
        }
    }

    private fun initializeDelegates() {
        // First initialize chat history delegate
        chatHistoryDelegate =
                ChatHistoryDelegate(
                        context = context,
                        viewModelScope = viewModelScope,
                        onChatHistoryLoaded = { messages: List<ChatMessage> ->
                            if (::floatingWindowDelegate.isInitialized &&
                                            floatingWindowDelegate.isFloatingMode.value
                            ) {
                                floatingWindowDelegate.updateFloatingWindowMessages(messages)
                            }

                            // 当聊天记录加载时，更新实际的上下文窗口大小
                            // 修复：直接使用从数据库加载的窗口大小，即使是0也不回退到最大值
                            val currentChat = chatHistories.value.find { it.id == currentChatId.value }
                            val currentSize = currentChat?.currentWindowSize ?: 0
                            // uiStateDelegate.updateCurrentWindowSize(currentSize) // Removed
                        },
                        onTokenStatisticsLoaded = { inputTokens, outputTokens, windowSize ->
                            tokenStatsDelegate.setTokenCounts(inputTokens, outputTokens, windowSize)
                        },

                        getEnhancedAiService = { enhancedAiService },
                        ensureAiServiceAvailable = { ensureAiServiceAvailable() },
                        getChatStatistics = {
                            val (inputTokens, outputTokens) = tokenStatsDelegate.getCumulativeTokenCounts()
                            val windowSize = tokenStatsDelegate.getLastCurrentWindowSize()
                            Triple(inputTokens, outputTokens, windowSize)
                        },
                        onScrollToBottom = { messageProcessingDelegate.scrollToBottom() }
                )

        // Then initialize message processing delegate
        messageProcessingDelegate =
                MessageProcessingDelegate(
                        context = context,
                        viewModelScope = viewModelScope,
                        getEnhancedAiService = { enhancedAiService },
                        getChatHistory = { chatHistoryDelegate.chatHistory.value },
                        addMessageToChat = { message ->
                            chatHistoryDelegate.addMessageToChat(message)
                        },
                        saveCurrentChat = {
                            val (inputTokens, outputTokens) =
                                    tokenStatsDelegate.getCumulativeTokenCounts()
                            val currentWindowSize =
                                    tokenStatsDelegate.getLastCurrentWindowSize()
                            chatHistoryDelegate.saveCurrentChat(
                                inputTokens,
                                outputTokens,
                                currentWindowSize
                            )
                            // 立即更新UI上的实际窗口大小
                            if (currentWindowSize > 0) {
                                // uiStateDelegate.updateCurrentWindowSize(currentWindowSize)
                            }
                        },
                        showErrorMessage = { message -> uiStateDelegate.showErrorMessage(message) },
                        updateChatTitle = { chatId, title ->
                            chatHistoryDelegate.updateChatTitle(chatId, title)
                        },
                        onTurnComplete = {
                            // 轮次完成后，更新累计统计并保存聊天
                            tokenStatsDelegate.updateCumulativeStatistics()
                            val (inputTokens, outputTokens) =
                                tokenStatsDelegate.getCumulativeTokenCounts()
                            val windowSize = tokenStatsDelegate.getLastCurrentWindowSize()
                            chatHistoryDelegate.saveCurrentChat(inputTokens, outputTokens, windowSize)
                        },
                        // 传递自动朗读状态和方法
                        getIsAutoReadEnabled = { isAutoReadEnabled.value },
                        speakMessage = ::speakMessage
                )

        // Finally initialize floating window delegate
        floatingWindowDelegate =
                FloatingWindowDelegate(
                        context = context,
                        viewModelScope = viewModelScope,
                        onMessageReceived = { message, type ->
                            updateUserMessage(message)
                            sendUserMessage(type)
                        },
                        onAttachmentRequested = { type -> processAttachmentRequest(type) },
                        onAttachmentRemoveRequested = { filePath -> removeAttachment(filePath) },
                        onCancelMessageRequested = {
                            // 取消当前消息
                            cancelCurrentMessage()
                        },
                        inputProcessingState = this.inputProcessingState
                )
    }

    private fun setupPermissionSystemCollection() {
        viewModelScope.launch {
            toolPermissionSystem.masterSwitchFlow.collect { level ->
                uiStateDelegate.updateMasterPermissionLevel(level)
            }
        }
    }

    private fun setupAttachmentManagerToastCollection() {
        viewModelScope.launch {
            attachmentManager.toastEvent.collect { message -> uiStateDelegate.showToast(message) }
        }
    }

    private fun checkIfShouldCreateNewChat() {
        viewModelScope.launch {
            // 检查历史记录加载后是否需要创建新聊天
            if (chatHistoryDelegate.checkIfShouldCreateNewChat() && isConfigured.value) {
                chatHistoryDelegate.createNewChat()
            }
        }
    }

    /** 设置服务相关的流收集逻辑 */
    private fun setupServiceCollectors() {
        // 避免重复设置服务收集器
        if (serviceCollectorSetupComplete) {
            Log.d(TAG, "服务收集器已经设置完成，跳过重复设置")
            return
        }

        // 确保enhancedAiService不为null
        if (enhancedAiService == null) {
            Log.d(TAG, "EnhancedAIService尚未初始化，跳过服务收集器设置")
            return
        }

        // 设置输入处理状态收集
        viewModelScope.launch {
            try {
                enhancedAiService?.inputProcessingState?.collect { state ->
                    if (state is com.ai.assistance.operit.data.model.InputProcessingState.Completed && _isSummarizing.value) {
                        messageProcessingDelegate.handleInputProcessingState(
                            com.ai.assistance.operit.data.model.InputProcessingState.Summarizing("正在总结记忆...")
                        )
                    } else if (::messageProcessingDelegate.isInitialized) {
                        messageProcessingDelegate.handleInputProcessingState(state)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "输入处理状态收集出错: ${e.message}", e)
                uiStateDelegate.showErrorMessage("输入处理状态收集失败: ${e.message}")
            }
        }

        // 设置单次请求Token计数器收集
        viewModelScope.launch {
            try {
                enhancedAiService?.perRequestTokenCounts?.collect { counts ->
                    // uiStateDelegate.updatePerRequestTokenCount(counts) // Removed
                    // 当收到新的单次请求token数时，更新TokenStatisticsDelegate中的lastCurrentWindowSize
                    counts?.let {
                        // tokenStatsDelegate.updateLastWindowSize(it.first) // Removed
                        // uiStateDelegate.updateCurrentWindowSize(it.first) // Removed
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "单次请求Token计数收集出错: ${e.message}", e)
                uiStateDelegate.showErrorMessage("单次请求Token计数收集失败: ${e.message}")
            }
        }

        // 标记服务收集器设置为已完成
        serviceCollectorSetupComplete = true
        Log.d(TAG, "服务收集器设置已标记为完成")
    }

    // API配置相关方法
    fun updateApiKey(key: String) = apiConfigDelegate.updateApiKey(key)

    fun updateApiEndpoint(endpoint: String) = apiConfigDelegate.updateApiEndpoint(endpoint)

    fun updateModelName(modelName: String) = apiConfigDelegate.updateModelName(modelName)

    fun updateApiProviderType(providerType: ApiProviderType) = apiConfigDelegate.updateApiProviderType(providerType)
    fun saveApiSettings() = apiConfigDelegate.saveApiSettings()
    fun useDefaultConfig() {
        if (apiConfigDelegate.useDefaultConfig()) {
            uiStateDelegate.showToast("使用默认配置继续")
        } else {
            // 修改：使用错误弹窗而不是Toast显示配置错误
            uiStateDelegate.showErrorMessage("默认配置不完整，请填写必要信息")
        }
    }
    fun toggleAiPlanning() {
        apiConfigDelegate.toggleAiPlanning()
        // 移除Toast提示
    }

    // 切换思考模式的方法现在委托给ApiConfigDelegate
    fun toggleThinkingMode() {
        apiConfigDelegate.toggleThinkingMode()
    }

    // 切换思考引导的方法现在委托给ApiConfigDelegate
    fun toggleThinkingGuidance() {
        apiConfigDelegate.toggleThinkingGuidance()
    }

    // 切换记忆附着的方法现在委托给ApiConfigDelegate
    fun toggleMemoryAttachment() {
        apiConfigDelegate.toggleMemoryAttachment()
    }

    // 更新上下文长度
    fun updateContextLength(length: Float) {
        apiConfigDelegate.updateContextLength(length)
    }

    fun updateSummaryTokenThreshold(threshold: Float) {
        apiConfigDelegate.updateSummaryTokenThreshold(threshold)
    }

    // 聊天历史相关方法
    fun createNewChat() {
        chatHistoryDelegate.createNewChat()
    }

    fun switchChat(chatId: String) {
        chatHistoryDelegate.switchChat(chatId)

        // 如果当前WebView正在显示，则更新工作区并触发刷新
        if (_showWebView.value) {
            updateWebServerForCurrentChat(chatId)
            // 延迟一点时间再触发刷新，等待服务器工作区更新完成
            viewModelScope.launch {
                refreshWebView()
            }
        }
    }

    fun deleteChatHistory(chatId: String) = chatHistoryDelegate.deleteChatHistory(chatId)
    fun clearCurrentChat() {
        chatHistoryDelegate.clearCurrentChat()
        uiStateDelegate.showToast("聊天记录已清空")
    }
    fun toggleChatHistorySelector() = chatHistoryDelegate.toggleChatHistorySelector()
    fun showChatHistorySelector(show: Boolean) {
        chatHistoryDelegate.showChatHistorySelector(show)
    }

    /** 删除单条消息 */
    fun deleteMessage(index: Int) {
        chatHistoryDelegate.deleteMessage(index)
    }

    /** 从指定索引删除后续所有消息 */
    fun deleteMessagesFrom(index: Int) {
        chatHistoryDelegate.deleteMessagesFrom(index)
    }

    fun saveCurrentChat() {
        val (inputTokens, outputTokens) = tokenStatsDelegate.getCumulativeTokenCounts()
        val currentWindowSize = tokenStatsDelegate.getLastCurrentWindowSize()
        chatHistoryDelegate.saveCurrentChat(inputTokens, outputTokens, currentWindowSize)
    }

    // 添加消息编辑方法
    fun updateMessage(index: Int, editedMessage: ChatMessage) {
        viewModelScope.launch {
            try {
                // 获取当前聊天历史
                val currentHistory = chatHistoryDelegate.chatHistory.value.toMutableList()

                // 确保索引有效
                if (index < 0 || index >= currentHistory.size) {
                    uiStateDelegate.showErrorMessage("无效的消息索引")
                    return@launch
                }

                // 更新消息
                currentHistory[index] = editedMessage

                // 将更新后的历史记录保存到ChatHistoryDelegate
                // 注意：这里仅更新内存，因为此方法只用于单个消息内容的修改，不涉及历史截断
                chatHistoryDelegate.updateChatHistory(currentHistory)

                // 直接在数据库中更新该条消息
                chatHistoryDelegate.addMessageToChat(editedMessage)

                // 更新统计信息并保存
                tokenStatsDelegate.updateCumulativeStatistics()
                val (inputTokens, outputTokens) = tokenStatsDelegate.getCumulativeTokenCounts()
                val currentWindowSize = tokenStatsDelegate.getLastCurrentWindowSize()
                chatHistoryDelegate.saveCurrentChat(
                    inputTokens,
                    outputTokens,
                    currentWindowSize
                )

                // 显示成功提示
                uiStateDelegate.showToast("消息已更新")
            } catch (e: Exception) {
                Log.e(TAG, "更新消息失败", e)
                uiStateDelegate.showErrorMessage("更新消息失败: ${e.message}")
            }
        }
    }

    /**
     * 回档到指定消息并重新发送
     * @param index 要回档到的消息索引
     * @param editedContent 编辑后的消息内容（如果有）
     */
    fun rewindAndResendMessage(index: Int, editedContent: String) {
        viewModelScope.launch {
            try {
                // 获取当前聊天历史
                val currentHistory = chatHistoryDelegate.chatHistory.value.toMutableList()

                // 确保索引有效
                if (index < 0 || index >= currentHistory.size) {
                    uiStateDelegate.showErrorMessage("无效的消息索引")
                    return@launch
                }

                // 获取目标消息
                val targetMessage = currentHistory[index]

                // 检查目标消息是否是用户消息，如果不是，选择前一条用户消息
                val finalIndex: Int
                val finalMessage: ChatMessage

                if (targetMessage.sender == "user") {
                    finalIndex = index
                    finalMessage = targetMessage.copy(content = editedContent)
                } else {
                    // 查找该消息前最近的用户消息
                    var userMessageIndex = index - 1
                    while (userMessageIndex >= 0 &&
                            currentHistory[userMessageIndex].sender != "user") {
                        userMessageIndex--
                    }

                    if (userMessageIndex < 0) {
                        uiStateDelegate.showErrorMessage("找不到有效的用户消息进行回档")
                        return@launch
                    }

                    finalIndex = userMessageIndex
                    finalMessage = currentHistory[userMessageIndex].copy(content = editedContent)
                }

                // 截取到指定消息的历史记录（不包含该消息本身）
                val rewindHistory = currentHistory.subList(0, finalIndex)
                // 获取要删除的第一条消息的时间戳
                val timestampOfFirstDeletedMessage =
                        if (finalIndex < currentHistory.size) {
                            currentHistory[finalIndex].timestamp
                        } else {
                            // 如果finalIndex是列表末尾，则没有消息需要删除
                            null
                        }

                // **核心修复**：调用新的委托方法，原子性地更新数据库和内存
                chatHistoryDelegate.truncateChatHistory(
                        rewindHistory,
                        timestampOfFirstDeletedMessage
                )

                // 显示重新发送的消息准备状态
                uiStateDelegate.showToast("正在准备重新发送消息")

                // 使用修改后的消息内容来发送
                chatHistoryDelegate.updateChatHistory(rewindHistory)

                messageProcessingDelegate.updateUserMessage(finalMessage.content)
                sendUserMessage()
            } catch (e: Exception) {
                Log.e(TAG, "回档并重新发送消息失败", e)
                uiStateDelegate.showErrorMessage("回档失败: ${e.message}")
            }
        }
    }

    // 消息处理相关方法
    fun updateUserMessage(message: String) = messageProcessingDelegate.updateUserMessage(message)

    fun sendUserMessage(promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT) {
        // 检查是否有当前对话，如果没有则创建一个新对话
        if (currentChatId.value == null) {
            Log.d(TAG, "当前没有活跃对话，自动创建新对话")

            // 使用viewModelScope启动协程
            viewModelScope.launch {
                // 使用现有的createNewChat方法创建新对话
                chatHistoryDelegate.createNewChat()

                // 等待对话ID更新
                var waitCount = 0
                while (currentChatId.value == null && waitCount < 10) {
                    delay(100) // 短暂延迟等待对话创建完成
                    waitCount++
                }

                if (currentChatId.value == null) {
                    Log.e(TAG, "创建新对话超时，无法发送消息")
                    uiStateDelegate.showErrorMessage("无法创建新对话，请重试")
                    return@launch
                }

                Log.d(TAG, "新对话创建完成，ID: ${currentChatId.value}，现在发送消息")

                // 对话创建完成后，发送消息
                sendMessageInternal(promptFunctionType)
            }
        } else {
            // 已有对话，直接发送消息
            sendMessageInternal(promptFunctionType)
        }
    }

    // 提取内部发送消息的逻辑为一个私有方法
    private fun sendMessageInternal(promptFunctionType: PromptFunctionType) {
        // 获取当前聊天ID和工作区路径
        val chatId = currentChatId.value
        val currentChat = chatHistories.value.find { it.id == chatId }
        val workspacePath = currentChat?.workspace

        // 更新本地Web服务器的聊天ID
        chatId?.let { updateWebServerForCurrentChat(it) }

        // 获取当前附件列表
        val currentAttachments = attachmentManager.attachments.value

        
        // 使用 AIMessageManager 检查是否应该生成总结
        val currentMessages = chatHistoryDelegate.chatHistory.value
        val currentTokens = currentWindowSize.value
        val maxTokens = (maxWindowSizeInK.value * 1024).toInt()

        val isShouldGenerateSummary = AIMessageManager.shouldGenerateSummary(
                messages = currentMessages,
                currentTokens = currentTokens,
                maxTokens = maxTokens,
                tokenUsageThreshold = summaryTokenThreshold.value.toDouble()
            )
        
        if (isShouldGenerateSummary) {
            _isSummarizing.value = true
            // 1. 在调用挂起函数之前，根据当前的消息快照预先计算好插入位置
            val insertPosition = chatHistoryDelegate.findProperSummaryPosition(currentMessages)

            // 2. 异步触发总结生成
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    enhancedAiService?.let { service ->
                        // 传入快照进行总结
                        val summaryMessage = AIMessageManager.summarizeMemory(service, currentMessages)
                        summaryMessage?.let {
                            // 3. 使用预先计算好的位置插入总结消息
                            chatHistoryDelegate.addSummaryMessage(it, insertPosition)

                            // 4. 更新窗口大小
                            val newHistoryForTokens = AIMessageManager.getMemoryFromMessages(chatHistoryDelegate.chatHistory.value)
                            val chatService = service.getAIServiceForFunction(com.ai.assistance.operit.data.model.FunctionType.CHAT)
                            val newWindowSize = chatService.calculateInputTokens("", newHistoryForTokens)
                            val inputTokens = tokenStatsDelegate.cumulativeInputTokensFlow.value
                            val outputTokens = tokenStatsDelegate.cumulativeOutputTokensFlow.value
                            chatHistoryDelegate.saveCurrentChat(inputTokens, outputTokens, newWindowSize)
                            // 更新UI上的显示
                            withContext(Dispatchers.Main){
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
                    if (messageProcessingDelegate.inputProcessingState.value is com.ai.assistance.operit.data.model.InputProcessingState.Summarizing) {
                        messageProcessingDelegate.handleInputProcessingState(
                            com.ai.assistance.operit.data.model.InputProcessingState.Completed
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
                enableThinking = enableThinkingMode.value, // 传递思考模式的状态
                thinkingGuidance = enableThinkingGuidance.value, // 传递思考引导的状态
                enableMemoryAttachment = enableMemoryAttachment.value, // 传递记忆附着的状态
                enableWorkspaceAttachment = !workspacePath.isNullOrBlank(), // 当工作区绑定了路径时启用工作区附着
                maxTokens = maxTokens,
                //如果已经在生成总结了，那么这个值可以宽松一点，让下一次对话不会被截断
                tokenUsageThreshold = if (isShouldGenerateSummary) summaryTokenThreshold.value.toDouble() + 0.5 else summaryTokenThreshold.value.toDouble(),
                replyToMessage = replyToMessage.value // 传递回复消息
        )

        // 在sendMessageInternal中，添加对nonFatalErrorEvent的收集
        viewModelScope.launch {
            messageProcessingDelegate.nonFatalErrorEvent.collect { errorMessage ->
                uiStateDelegate.showToast(errorMessage)
            }
        }


        

        // 发送后清空附件列表
        if (currentAttachments.isNotEmpty()) {
            attachmentManager.clearAttachments()
            // 更新悬浮窗附件列表
            updateFloatingWindowAttachments()
        }

        // 重置附件面板状态 - 在发送消息后关闭附件面板
        resetAttachmentPanelState()

        // 清除回复状态
        clearReplyToMessage()
    }

    fun cancelCurrentMessage() {
        messageProcessingDelegate.cancelCurrentMessage()
        uiStateDelegate.showToast("已取消当前对话")
    }

    // UI状态相关方法
    fun showErrorMessage(message: String) = uiStateDelegate.showErrorMessage(message)
    fun clearError() = uiStateDelegate.clearError()
    fun popupMessage(message: String) = uiStateDelegate.showPopupMessage(message)
    fun clearPopupMessage() = uiStateDelegate.clearPopupMessage()
    fun showToast(message: String) = uiStateDelegate.showToast(message)
    fun clearToastEvent() = uiStateDelegate.clearToastEvent()

    // 悬浮窗相关方法
    fun onFloatingButtonClick(mode: FloatingMode, permissionLauncher: ActivityResultLauncher<String>, colorScheme: ColorScheme, typography: Typography) {
        viewModelScope.launch {
            // 如果悬浮窗已经开启，则关闭它
            if (isFloatingMode.value) {
                toggleFloatingMode()
                return@launch
            }

            when(mode) {
                FloatingMode.WINDOW -> launchFloatingWindowWithPermissionCheck(permissionLauncher) {
                    launchFloatingModeIn(FloatingMode.WINDOW, colorScheme, typography)
                }
                FloatingMode.FULLSCREEN -> launchFullscreenVoiceModeWithPermissionCheck(permissionLauncher, colorScheme, typography)
                FloatingMode.BALL,
                FloatingMode.VOICE_BALL,
                FloatingMode.DragonBones -> {
                    // 这些模式暂时不处理，或者可以添加默认行为
                    Log.d(TAG, "未实现的悬浮窗模式: $mode")
                }
            }
        }
    }


    fun toggleFloatingMode(colorScheme: ColorScheme? = null, typography: Typography? = null) {
        floatingWindowDelegate.toggleFloatingMode(colorScheme, typography)
    }

    fun updateFloatingWindowMessages(messages: List<ChatMessage>) {
        floatingWindowDelegate.updateFloatingWindowMessages(messages)
    }

    fun updateFloatingWindowAttachments() {
        floatingWindowDelegate.updateFloatingWindowAttachments(attachments.value)
    }

    // 权限相关方法
    fun toggleMasterPermission() {
        viewModelScope.launch {
            val newLevel =
                    if (masterPermissionLevel.value == PermissionLevel.ASK) {
                        PermissionLevel.ALLOW
                    } else {
                        PermissionLevel.ASK
                    }
            toolPermissionSystem.saveMasterSwitch(newLevel)

            // 移除Toast提示
        }
    }

    // 附件相关方法
    /** 处理从悬浮窗接收的附件请求 */
    private fun processAttachmentRequest(request: String) {
        viewModelScope.launch {
            try {
                // 显示附件请求处理进度
                messageProcessingDelegate.setInputProcessingState(true, "正在处理附件请求...")

                when {
                    request == "screen_capture" -> {
                        // 捕获屏幕内容
                        captureScreenContent()
                    }
                    request == "notifications_capture" -> {
                        // 捕获通知
                        captureNotifications()
                    }
                    request == "location_capture" -> {
                        // 捕获位置
                        captureLocation()
                    }
                    else -> {
                        // 处理普通文件附件
                        handleAttachment(request)
                    }
                }

                // 在各子方法中都已经有设置进度条状态的代码，不需要在这里重复清除
            } catch (e: Exception) {
                Log.e(TAG, "Error processing attachment request", e)
                // 修改: 使用错误弹窗而不是 Toast 显示附件处理错误
                uiStateDelegate.showErrorMessage("处理附件失败: ${e.message}")
                // 确保出错时清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            }
        }
    }

    /** Handles a file or image attachment selected by the user */
    fun handleAttachment(filePath: String) {
        viewModelScope.launch {
            try {
                // 显示附件处理进度
                messageProcessingDelegate.setInputProcessingState(true, "正在处理附件...")

                attachmentManager.handleAttachment(filePath)

                // 处理完附件后立即更新悬浮窗中的附件列表
                updateFloatingWindowAttachments()

                // 清除附件处理进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            } catch (e: Exception) {
                Log.e(TAG, "处理附件失败", e)
                // 修改: 使用错误弹窗而不是 Toast 显示附件处理错误
                uiStateDelegate.showErrorMessage("处理附件失败: ${e.message}")
                // 发生错误时也需要清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            }
        }
    }

    /** Removes an attachment by its file path */
    fun removeAttachment(filePath: String) {
        attachmentManager.removeAttachment(filePath)
        // 移除附件后立即更新悬浮窗中的附件列表
        updateFloatingWindowAttachments()
    }

    /** Inserts a reference to an attachment at the current cursor position in the user's message */
    fun insertAttachmentReference(attachment: AttachmentInfo) {
        val currentMessage = userMessage.value
        val attachmentRef = attachmentManager.createAttachmentReference(attachment)

        // Insert at the end of the current message
        updateUserMessage("$currentMessage $attachmentRef ")

        // Show a toast to confirm insertion
        uiStateDelegate.showToast("已插入附件引用: ${attachment.fileName}")
    }

    /** Captures the current screen content and attaches it to the message */
    fun captureScreenContent() {
        viewModelScope.launch {
            try {
                messageProcessingDelegate.updateUserMessage("")
                // 显示屏幕内容获取进度
                messageProcessingDelegate.setInputProcessingState(true, "正在获取屏幕内容...")
                uiStateDelegate.showToast("正在获取屏幕内容...")

                // 直接委托给attachmentManager执行
                attachmentManager.captureScreenContent()

                // 完成后立即更新悬浮窗中的附件列表
                updateFloatingWindowAttachments()

                // 清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            } catch (e: Exception) {
                Log.e(TAG, "截取屏幕内容失败", e)
                uiStateDelegate.showErrorMessage("截取屏幕内容失败: ${e.message}")
                messageProcessingDelegate.setInputProcessingState(false, "")
            }
        }
    }

    /** 获取设备当前通知数据并添加为附件 */
    fun captureNotifications() {
        viewModelScope.launch {
            try {
                messageProcessingDelegate.updateUserMessage("")
                // 显示通知获取进度
                messageProcessingDelegate.setInputProcessingState(true, "正在获取当前通知...")
                uiStateDelegate.showToast("正在获取当前通知...")

                // 直接委托给attachmentManager执行
                attachmentManager.captureNotifications()

                // 完成后立即更新悬浮窗中的附件列表
                updateFloatingWindowAttachments()

                // 清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            } catch (e: Exception) {
                Log.e(TAG, "获取通知数据失败", e)
                uiStateDelegate.showErrorMessage("获取通知数据失败: ${e.message}")
                messageProcessingDelegate.setInputProcessingState(false, "")
            }
        }
    }

    /** 获取设备当前位置数据并添加为附件 */
    fun captureLocation() {
        viewModelScope.launch {
            try {
                messageProcessingDelegate.updateUserMessage("")
                // 显示位置获取进度
                messageProcessingDelegate.setInputProcessingState(true, "正在获取位置信息...")
                uiStateDelegate.showToast("正在获取位置信息...")

                // 直接委托给attachmentManager执行
                attachmentManager.captureLocation()

                // 完成后立即更新悬浮窗中的附件列表
                updateFloatingWindowAttachments()

                // 清除进度显示
                messageProcessingDelegate.setInputProcessingState(false, "")
            } catch (e: Exception) {
                Log.e(TAG, "获取位置数据失败", e)
                uiStateDelegate.showErrorMessage("获取位置数据失败: ${e.message}")
                messageProcessingDelegate.setInputProcessingState(false, "")
            }
        }
    }

    /** 确保AI服务可用，如果当前实例为空则创建一个默认实例 */
    fun ensureAiServiceAvailable() {
        if (enhancedAiService == null) {
            viewModelScope.launch {
                try {
                    // 使用默认配置或保存的配置创建一个新实例
                    Log.d(TAG, "创建默认EnhancedAIService实例")
                    apiConfigDelegate.useDefaultConfig()

                    // 等待服务实例创建完成
                    var retryCount = 0
                    while (enhancedAiService == null && retryCount < 3) {
                        kotlinx.coroutines.delay(500)
                        retryCount++
                    }

                    if (enhancedAiService == null) {
                        Log.e(TAG, "无法创建EnhancedAIService实例")
                        // 修改: 使用错误弹窗而不是 Toast 显示服务初始化错误
                        uiStateDelegate.showErrorMessage("无法初始化AI服务，请检查网络和API设置")
                    } else {
                        Log.d(TAG, "成功创建EnhancedAIService实例")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "创建EnhancedAIService实例时出错", e)
                    // 修改: 使用错误弹窗而不是 Toast 显示服务初始化错误
                    uiStateDelegate.showErrorMessage("初始化AI服务失败: ${e.message}")
                }
            }
        }
    }

    /** 重置附件面板状态 - 在发送消息后关闭附件面板 */
    fun resetAttachmentPanelState() {
        _attachmentPanelState.value = false
    }

    /** 更新附件面板状态 */
    fun updateAttachmentPanelState(isExpanded: Boolean) {
        _attachmentPanelState.value = isExpanded
    }

    // WebView控制方法
    fun toggleWebView() {
        // 如果要显示WebView，先关闭AI电脑
        if (!_showWebView.value && _showAiComputer.value) {
            _showAiComputer.value = false
            Log.d(TAG, "AI电脑已关闭（由于打开工作区）")
        }
        
        // 如果要显示WebView，确保本地Web服务器已启动
        if (!_showWebView.value) {
            // Get the WORKSPACE server instance and ensure it's running
            val workspaceServer = LocalWebServer.getInstance(context, LocalWebServer.ServerType.WORKSPACE)
            if (!workspaceServer.isRunning()) {
                try {
                    workspaceServer.start()
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to start workspace web server", e)
                    showErrorMessage("Failed to start workspace server.")
                    return
                }
            }

            // 获取当前聊天ID
            val chatId = currentChatId.value
            if (chatId != null) {
                // 更新Web服务器工作区
                updateWebServerForCurrentChat(chatId)
            } else {
                // 如果没有聊天ID，先创建一个新对话
                viewModelScope.launch {
                    createNewChat()

                    // 等待聊天ID创建完成
                    var waitCount = 0
                    while (currentChatId.value == null && waitCount < 10) {
                        delay(100)
                        waitCount++
                    }

                    // 使用新创建的聊天ID更新Web服务器
                    currentChatId.value?.let { newChatId ->
                        updateWebServerForCurrentChat(newChatId)
                    }
                }
            }
        }

        // 切换WebView显示状态
        val newShowState = !_showWebView.value
        _showWebView.value = newShowState

        // 每次切换时，增加刷新计数器
        if (_showWebView.value) {
            _webViewRefreshCounter.value += 1
        }
    }



    // 初始化本地Web服务器
    private fun initLocalWebServer() {
        try {
            // 使用单例模式获取LocalWebServer实例
            val webServer = LocalWebServer.getInstance(context, LocalWebServer.ServerType.WORKSPACE)
            // 只有当服务器未运行时才启动
            if (!webServer.isRunning()) {
                webServer.start()
                Log.d(TAG, "本地Web服务器已启动")
            } else {
                Log.d(TAG, "本地Web服务器已经在运行中")
            }
        } catch (e: IOException) {
            Log.e(TAG, "初始化本地Web服务器失败", e)
            uiStateDelegate.showErrorMessage("无法启动Web服务器: ${e.message}")
        }
    }

    // 更新当前聊天ID的Web服务器工作空间
    fun updateWebServerForCurrentChat(chatId: String) {
        try {
            // Find the chat and its workspace
            val chat = chatHistories.value.find { it.id == chatId }
            val workspacePath = chat?.workspace

            if (workspacePath == null) {
                Log.w(TAG, "Chat $chatId has no workspace bound. Web server not updated.")
                return
            }

            // 使用单例模式获取LocalWebServer实例
            val webServer = LocalWebServer.getInstance(context, LocalWebServer.ServerType.WORKSPACE)
            // 确保服务器已启动
            if (!webServer.isRunning()) {
                webServer.start()
            }
            webServer.updateChatWorkspace(workspacePath)
            Log.d(TAG, "Web服务器工作空间已更新为: $workspacePath for chat $chatId")
        } catch (e: Exception) {
            Log.e(TAG, "更新Web服务器工作空间失败", e)
            uiStateDelegate.showErrorMessage("更新Web工作空间失败: ${e.message}")
        }
    }

    // 强制WebView刷新
    fun refreshWebView() {
        _webViewRefreshCounter.value += 1
    }

    // 判断是否正在使用默认API配置
    private suspend fun checkConfigAndShowDialog() {
        // 初始化ModelConfigManager以检查所有配置
        val modelConfigManager = ModelConfigManager(context)
        var hasDefaultKey = false

        // 异步检查所有配置
        withContext(Dispatchers.IO) {
            // 获取所有配置ID
            val configIds = modelConfigManager.configListFlow.first()

            // 检查每个配置是否使用默认API key
            for (id in configIds) {
                val config = modelConfigManager.getModelConfigFlow(id).first()
                if (config.apiKey == ApiPreferences.DEFAULT_API_KEY) {
                    hasDefaultKey = true
                    break
                }
            }
        }

        _shouldShowConfigDialog.value = hasDefaultKey
    }
    
    // 用于启动文件选择器并处理结果
    fun startFileChooserForResult(intent: Intent, callback: (Int, Intent?) -> Unit) {
        fileChooserCallback = callback
        // 通过UIStateDelegate广播一个请求，让Activity处理文件选择
        uiStateDelegate.requestFileChooser(intent)
    }

    // 供Activity调用，处理文件选择结果
    fun handleFileChooserResult(resultCode: Int, data: Intent?) {
        fileChooserCallback?.invoke(resultCode, data)
        fileChooserCallback = null
    }

    /** 设置权限系统的颜色方案 */
    fun setPermissionSystemColorScheme(colorScheme: ColorScheme?) {
        toolPermissionSystem.setColorScheme(colorScheme)
    }

    fun launchFloatingModeIn(
            mode: FloatingMode,
            colorScheme: ColorScheme? = null,
            typography: Typography? = null
    ) {
        floatingWindowDelegate.launchInMode(mode, colorScheme, typography)
    }

    fun launchFloatingWindowWithPermissionCheck(
            launcher: ActivityResultLauncher<String>,
            onPermissionGranted: () -> Unit
    ) {
        val hasMicPermission =
                android.content.pm.PackageManager.PERMISSION_GRANTED ==
                        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        val canDrawOverlays = Settings.canDrawOverlays(context)

        if (!hasMicPermission) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (!canDrawOverlays) {
            val intent =
                    Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                    )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            showToast("需要悬浮窗权限才能启动语音助手")
        } else {
            onPermissionGranted()
        }
    }

    fun launchFullscreenVoiceModeWithPermissionCheck(
            launcher: ActivityResultLauncher<String>,
            colorScheme: ColorScheme? = null,
            typography: Typography? = null
    ) {
        val hasMicPermission =
                android.content.pm.PackageManager.PERMISSION_GRANTED ==
                        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
        val canDrawOverlays = Settings.canDrawOverlays(context)

        if (!hasMicPermission) {
            launcher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (!canDrawOverlays) {
            val intent =
                    Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                    )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            showToast("需要悬浮窗权限才能启动语音助手")
        } else {
            // Directly launch fullscreen voice mode
            launchFloatingModeIn(FloatingMode.FULLSCREEN, colorScheme, typography)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 清理悬浮窗资源
        floatingWindowDelegate.cleanup()
        
        // 清理语音服务资源
        voiceService?.shutdown()

        // 不再在这里停止Web服务器，因为使用的是单例模式
        // 服务器应在应用退出时由Application类或专门的服务管理类关闭
        // 这样可以在界面切换时保持服务器的连续运行
    }

    /** 更新指定聊天的标题 */
    fun updateChatTitle(chatId: String, newTitle: String) {
        chatHistoryDelegate.updateChatTitle(chatId, newTitle)
    }

    /** 更新指定聊天的标题 */
    fun bindChatToWorkspace(chatId: String, workspace: String) {
        // 1. Persist the change
        chatHistoryDelegate.bindChatToWorkspace(chatId, workspace)

        // 2. Update the web server with the new path and refresh
        viewModelScope.launch {
            try {
                val webServer = LocalWebServer.getInstance(context, LocalWebServer.ServerType.WORKSPACE)
                if (!webServer.isRunning()) {
                    webServer.start()
                }
                webServer.updateChatWorkspace(workspace)
                Log.d(TAG, "Web server workspace updated to: $workspace for chat $chatId")

                // 3. Trigger a refresh of the WebView
                refreshWebView()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update web server workspace after binding", e)
                uiStateDelegate.showErrorMessage("更新Web工作空间失败: ${e.message}")
            }
        }
    }

    /** 解绑聊天的工作区 */
    fun unbindChatFromWorkspace(chatId: String) {
        // 1. Persist the change
        chatHistoryDelegate.unbindChatFromWorkspace(chatId)

        // 2. Stop the web server or clear workspace
        viewModelScope.launch {
            try {
                val webServer = LocalWebServer.getInstance(context, LocalWebServer.ServerType.WORKSPACE)
                if (webServer.isRunning()) {
                    webServer.stop()
                }
                Log.d(TAG, "Web server stopped after unbinding workspace for chat $chatId")

                // 3. Trigger a refresh of the WebView
                refreshWebView()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop web server after unbinding", e)
                uiStateDelegate.showErrorMessage("停止Web工作空间失败: ${e.message}")
            }
        }
    }

    /** 更新聊天顺序和分组 */
    fun updateChatOrderAndGroup(
        reorderedHistories: List<ChatHistory>,
        movedItem: ChatHistory,
        targetGroup: String?
    ) {
        chatHistoryDelegate.updateChatOrderAndGroup(reorderedHistories, movedItem, targetGroup)
    }

    /** 创建新分组（通过创建新聊天实现） */
    fun createGroup(groupName: String) {
        chatHistoryDelegate.createGroup(groupName)
    }

    /** 重命名分组 */
    fun updateGroupName(oldName: String, newName: String) {
        chatHistoryDelegate.updateGroupName(oldName, newName)
    }

    /** 删除分组 */
    fun deleteGroup(groupName: String, deleteChats: Boolean) {
        chatHistoryDelegate.deleteGroup(groupName, deleteChats)
    }

    fun onWorkspaceButtonClick() {
        toggleWebView()
        refreshWebView()
    }

    fun onAiComputerButtonClick() {
        toggleAiComputer()
    }

    // AI电脑控制方法
    fun toggleAiComputer() {
        viewModelScope.launch {
            // 如果要显示AI电脑，先关闭工作区
            if (!_showAiComputer.value && _showWebView.value) {
                _showWebView.value = false
                Log.d(TAG, "工作区已关闭（由于打开AI电脑）")
            }
            
            val newShowState = !_showAiComputer.value
            _showAiComputer.value = newShowState
            
            if (newShowState) {
                // 初始化AI电脑管理器
                try {
                    Log.d(TAG, "AI电脑已启动")
                } catch (e: Exception) {
                    Log.e(TAG, "启动AI电脑失败", e)
                    _showAiComputer.value = false
                    uiStateDelegate.showErrorMessage("启动AI电脑失败: ${e.message}")
                }
            } else {
                Log.d(TAG, "AI电脑已关闭")
            }
        }
    }



    /** 初始化语音服务 */
    private fun initializeVoiceService() {
        viewModelScope.launch {
            try {
                voiceService = VoiceServiceFactory.getInstance(context)
                val initialized = voiceService?.initialize() ?: false
                if (!initialized) {
                    Log.w(TAG, "语音服务初始化失败")
                }
                
                // 监听语音播放状态
                voiceService?.speakingStateFlow?.collect { isSpeaking ->
                    _isPlaying.value = isSpeaking
                }
            } catch (e: Exception) {
                Log.e(TAG, "初始化语音服务时出错", e)
            }
        }
    }

    /** 朗读消息内容 */
    fun speakMessage(message: String) {
        viewModelScope.launch {
            try {
                if (voiceService == null) {
                    initializeVoiceService()
                    // 等待初始化完成
                    delay(500)
                }
                
                val success = voiceService?.speak(
                    text = message,
                    interrupt = true, // 中断当前播放
                    rate = 1.0f,
                    pitch = 1.0f
                ) ?: false
                
                if (!success) {
                    uiStateDelegate.showToast("朗读失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "朗读消息失败", e)
                uiStateDelegate.showToast("朗读消息失败: ${e.message}")
            }
        }
    }

    /** 停止朗读 */
    fun stopSpeaking() {
        viewModelScope.launch {
            try {
                voiceService?.stop()
            } catch (e: Exception) {
                Log.e(TAG, "停止朗读失败", e)
            }
        }
    }

    fun toggleAutoRead() {
        _isAutoReadEnabled.value = !_isAutoReadEnabled.value
        // 如果关闭，则也停止当前语音
        if (!_isAutoReadEnabled.value) {
            stopSpeaking()
        }
    }

    fun disableAutoRead() {
        if (_isAutoReadEnabled.value) {
            _isAutoReadEnabled.value = false
            stopSpeaking()
        }
    }

    fun enableAutoReadAndSpeak(content: String) {
        if (!_isAutoReadEnabled.value) {
            _isAutoReadEnabled.value = true
            speakMessage(content)
        }
    }

    /** 设置回复目标消息 */
    fun setReplyToMessage(message: ChatMessage) {
        _replyToMessage.value = message
    }

    /** 清除回复状态 */
    fun clearReplyToMessage() {
        _replyToMessage.value = null
    }

    /** 生成回复消息的摘要 */
    private fun generateReplyPreview(message: ChatMessage): String {
        val content = message.content
        val cleanContent = content
            .replace(Regex("<[^>]*>"), "") // 移除XML标签
            .trim()
        
        // 限制长度为50个字符
        return if (cleanContent.length > 50) {
            cleanContent.take(50) + "..."
        } else {
            cleanContent
        }
    }
}
