package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.ai.assistance.operit.data.preferences.CharacterCardManager

/** 委托类，负责管理聊天历史相关功能 */
class ChatHistoryDelegate(
        private val context: Context,
        private val viewModelScope: CoroutineScope,
        private val onChatHistoryLoaded: (List<ChatMessage>) -> Unit,
        private val onTokenStatisticsLoaded: (inputTokens: Int, outputTokens: Int, windowSize: Int) -> Unit,

        private val getEnhancedAiService: () -> EnhancedAIService?,
        private val ensureAiServiceAvailable: () -> Unit = {}, // 确保AI服务可用的回调
        private val getChatStatistics: () -> Triple<Int, Int, Int> = { Triple(0, 0, 0) }, // 获取（输入token, 输出token, 窗口大小）
        private val onScrollToBottom: () -> Unit = {} // 滚动到底部事件回调
) {
    companion object {
        private const val TAG = "ChatHistoryDelegate"
        // This constant is now in AIMessageManager
        // private const val SUMMARY_CHUNK_SIZE = 8
    }

    private val chatHistoryManager = ChatHistoryManager.getInstance(context)
    private val characterCardManager = CharacterCardManager.getInstance(context) // 新增
    private val isInitialized = AtomicBoolean(false)
    private val historyUpdateMutex = Mutex()

    // This is no longer needed here as summary logic is moved.
    // private val apiPreferences = ApiPreferences(context)

    // State flows
    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<ChatMessage>> = _chatHistory.asStateFlow()

    private val _showChatHistorySelector = MutableStateFlow(false)
    val showChatHistorySelector: StateFlow<Boolean> = _showChatHistorySelector.asStateFlow()

    private val _chatHistories = MutableStateFlow<List<ChatHistory>>(emptyList())
    val chatHistories: StateFlow<List<ChatHistory>> = _chatHistories.asStateFlow()

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId.asStateFlow()

    // This is no longer the responsibility of this delegate
    // private var summarizationPerformed = false

    init {
        initialize()
    }

    private fun initialize() {
        if (!isInitialized.compareAndSet(false, true)) {
            return
        }

        viewModelScope.launch {
            chatHistoryManager.chatHistoriesFlow.collect { histories ->
                _chatHistories.value = histories
            }
        }

        viewModelScope.launch {
            val initialChatId = chatHistoryManager.currentChatIdFlow.first()
            if (initialChatId != null) {
                _currentChatId.value = initialChatId
                loadChatMessages(initialChatId)
            }
        }
    }

    private suspend fun loadChatMessages(chatId: String) {
        try {
            // 直接从数据库加载消息
            val messages = chatHistoryManager.loadChatMessages(chatId)
            Log.d(TAG, "加载聊天 $chatId 的消息：${messages.size} 条")

            // 无论消息是否为空，都更新聊天历史
            _chatHistory.value = messages
            onChatHistoryLoaded(messages)

            // 查找聊天元数据，更新token统计
            val selectedChat = _chatHistories.value.find { it.id == chatId }
            if (selectedChat != null) {
                onTokenStatisticsLoaded(selectedChat.inputTokens, selectedChat.outputTokens, selectedChat.currentWindowSize)


            }
        } catch (e: Exception) {
            Log.e(TAG, "加载聊天消息失败", e)
        }
    }

    /** 检查是否应该创建新聊天，确保同步 */
    fun checkIfShouldCreateNewChat(): Boolean {
        // 只有当历史记录和当前对话ID都已加载，且未创建过初始对话时才检查
        if (!isInitialized.get() || _currentChatId.value == null) {
            return false
        }
        return true
    }

    /** 创建新的聊天 */
    fun createNewChat() {
        viewModelScope.launch {
            val (inputTokens, outputTokens, windowSize) = getChatStatistics()
            saveCurrentChat(inputTokens, outputTokens, windowSize) // 使用获取到的完整统计数据

            val newChat = chatHistoryManager.createNewChat()
            _currentChatId.value = newChat.id
            
            // --- 新增：检查并添加开场白 ---
            val activeCard = characterCardManager.activeCharacterCardFlow.first()
            if (activeCard.openingStatement.isNotBlank()) {
                val openingMessage = ChatMessage(
                    sender = "ai",
                    content = activeCard.openingStatement,
                    timestamp = System.currentTimeMillis()
                )
                // 将开场白添加到新聊天的消息列表中
                val messagesWithOpening = listOf(openingMessage)
                _chatHistory.value = messagesWithOpening
                // 保存带开场白的消息到数据库
                chatHistoryManager.addMessage(newChat.id, openingMessage)
                onChatHistoryLoaded(messagesWithOpening)
            } else {
                _chatHistory.value = newChat.messages
                onChatHistoryLoaded(newChat.messages)
            }
            // --- 结束 ---

            onTokenStatisticsLoaded(0, 0, 0)
        }
    }

    /** 切换聊天 */
    fun switchChat(chatId: String) {
        viewModelScope.launch {
            val (inputTokens, outputTokens, windowSize) = getChatStatistics()
            saveCurrentChat(inputTokens, outputTokens, windowSize) // 切换前使用正确的窗口大小保存

            chatHistoryManager.setCurrentChatId(chatId)
            _currentChatId.value = chatId

            loadChatMessages(chatId)

            delay(200)
            onScrollToBottom()
        }
    }

    /** 删除聊天历史 */
    fun deleteChatHistory(chatId: String) {
        viewModelScope.launch {
            if (chatId == _currentChatId.value) {
                chatHistoryManager.deleteChatHistory(chatId)
                createNewChat()
            } else {
                chatHistoryManager.deleteChatHistory(chatId)
            }
        }
    }

    /** 删除单条消息 */
    fun deleteMessage(index: Int) {
        viewModelScope.launch {
            historyUpdateMutex.withLock {
                _currentChatId.value?.let { chatId ->
                    val currentMessages = _chatHistory.value.toMutableList()
                    if (index >= 0 && index < currentMessages.size) {
                        val messageToDelete = currentMessages[index]

                        // 从数据库删除
                        chatHistoryManager.deleteMessage(chatId, messageToDelete.timestamp)

                        // 从内存中删除
                        currentMessages.removeAt(index)
                        _chatHistory.value = currentMessages
                        onChatHistoryLoaded(currentMessages)
                    }
                }
            }
        }
    }

    /** 从指定索引删除后续所有消息 */
    fun deleteMessagesFrom(index: Int) {
        viewModelScope.launch {
            historyUpdateMutex.withLock {
                val currentMessages = _chatHistory.value
                if (index >= 0 && index < currentMessages.size) {
                    val messageToStartDeletingFrom = currentMessages[index]
                    val newHistory = currentMessages.subList(0, index)

                    // 这个方法会处理数据库和内存的更新
                    truncateChatHistory(newHistory, messageToStartDeletingFrom.timestamp)
                }
            }
        }
    }

    /** 清空当前聊天 */
    fun clearCurrentChat() {
        viewModelScope.launch {
            _currentChatId.value?.let { chatHistoryManager.deleteChatHistory(it) }
            createNewChat()
        }
    }

    /** 保存当前聊天到持久存储 */
    fun saveCurrentChat(
        inputTokens: Int = 0,
        outputTokens: Int = 0,
        actualContextWindowSize: Int = 0
    ) {
        viewModelScope.launch {
            _currentChatId.value?.let { chatId ->
                if (_chatHistory.value.isNotEmpty()) {
                    chatHistoryManager.updateChatTokenCounts(
                        chatId,
                        inputTokens,
                        outputTokens,
                        actualContextWindowSize
                    )
                }
            }
        }
    }

    /** 绑定聊天到工作区 */
    fun bindChatToWorkspace(chatId: String, workspace: String) {
        viewModelScope.launch {
            // 1. Update the database
            chatHistoryManager.updateChatWorkspace(chatId, workspace)

            // 2. Manually update the UI state to reflect the change immediately
            val updatedHistories = _chatHistories.value.map {
                if (it.id == chatId) {
                    it.copy(workspace = workspace, updatedAt = LocalDateTime.now())
                } else {
                    it
                }
            }
            _chatHistories.value = updatedHistories
        }
    }

    /** 解绑聊天的工作区 */
    fun unbindChatFromWorkspace(chatId: String) {
        viewModelScope.launch {
            // 1. Update the database (set workspace to null)
            chatHistoryManager.updateChatWorkspace(chatId, null)

            // 2. Manually update the UI state to reflect the change immediately
            val updatedHistories = _chatHistories.value.map {
                if (it.id == chatId) {
                    it.copy(workspace = null, updatedAt = LocalDateTime.now())
                } else {
                    it
                }
            }
            _chatHistories.value = updatedHistories
        }
    }

    /** 更新聊天标题 */
    fun updateChatTitle(chatId: String, title: String) {
        viewModelScope.launch {
            // 更新数据库
            chatHistoryManager.updateChatTitle(chatId, title)

            // 更新UI状态
            val updatedHistories =
                    _chatHistories.value.map {
                        if (it.id == chatId) {
                            it.copy(title = title, updatedAt = LocalDateTime.now())
                        } else {
                            it
                        }
                    }
            _chatHistories.value = updatedHistories
        }
    }

    /** 根据第一条用户消息生成聊天标题 */
    private fun generateChatTitle(): String {
        val firstUserMessage = _chatHistory.value.firstOrNull { it.sender == "user" }?.content
        return if (firstUserMessage != null) {
            // 截取前20个字符作为标题，并添加省略号
            if (firstUserMessage.length > 20) {
                "${firstUserMessage.take(20)}..."
            } else {
                firstUserMessage
            }
        } else {
            context.getString(R.string.new_conversation)
        }
    }

    /** 添加消息到当前聊天 */
    fun addMessageToChat(message: ChatMessage) {
        viewModelScope.launch {
            historyUpdateMutex.withLock {
                val chatId = _currentChatId.value ?: return@withLock

                val currentMessages = _chatHistory.value
                val existingMessageIndex =
                        currentMessages.indexOfFirst { it.timestamp == message.timestamp }

                if (existingMessageIndex != -1) {
                    // val updatedMessages = currentMessages.toMutableList().apply {
                    //     this[existingMessageIndex] = message
                    // }
                    // // 更新StateFlow以触发UI重组
                    // _chatHistory.value = updatedMessages
                    // // 通知监听者（包括悬浮窗）历史记录已更新
                    // onChatHistoryLoaded(updatedMessages)
                    // // 在后台更新数据库
                    chatHistoryManager.updateMessage(chatId, message)
                } else {
                    Log.d(
                            TAG,
                            "添加新消息, stream is null: ${message.contentStream == null}, timestamp: ${message.timestamp}"
                    )
                    val newMessages = currentMessages + message
                    _chatHistory.value = newMessages
                    onChatHistoryLoaded(newMessages) // 通知UI更新
                    chatHistoryManager.addMessage(chatId, message)
                }
            }
        }
    }

    /**
     * 截断聊天记录，会同步删除数据库中指定时间戳之后的消息，并更新内存中的消息列表。
     *
     * @param newHistory 截断后保留的消息列表。
     * @param timestampOfFirstDeletedMessage 用于删除数据库记录的起始时间戳。如果为null，则清空所有消息。
     */
    fun truncateChatHistory(newHistory: List<ChatMessage>, timestampOfFirstDeletedMessage: Long?) {
        viewModelScope.launch {
            historyUpdateMutex.withLock {
                _currentChatId.value?.let { chatId ->
                    if (timestampOfFirstDeletedMessage != null) {
                        // 从数据库中删除指定时间戳之后的消息
                        chatHistoryManager.deleteMessagesFrom(
                                chatId,
                                timestampOfFirstDeletedMessage
                        )
                    } else {
                        // 如果时间戳为空，则清除该聊天的所有消息
                        chatHistoryManager.clearChatMessages(chatId)
                    }

                    // 更新内存中的聊天记录
                    _chatHistory.value = newHistory
                    onChatHistoryLoaded(newHistory)
                }
            }
        }
    }

    /** 更新整个聊天历史 用于编辑或回档等操作 */
    fun updateChatHistory(newHistory: List<ChatMessage>) {
        _chatHistory.value = newHistory.toList()
        onChatHistoryLoaded(_chatHistory.value)
    }

    /**
     * 更新聊天记录的顺序和分组
     * @param reorderedHistories 重新排序后的完整聊天历史列表
     * @param movedItem 移动的聊天项
     * @param targetGroup 目标分组的名称，如果拖拽到分组上
     */
    fun updateChatOrderAndGroup(
        reorderedHistories: List<ChatHistory>,
        movedItem: ChatHistory,
        targetGroup: String?
    ) {
        viewModelScope.launch {
            try {
                // The list is already reordered. We just need to update displayOrder and group.
                val updatedList = reorderedHistories.mapIndexed { index, history ->
                    var newGroup = history.group
                    if (history.id == movedItem.id && targetGroup != null) {
                        newGroup = targetGroup
                    }
                    history.copy(displayOrder = index.toLong(), group = newGroup)
                }

                // Update UI immediately
                _chatHistories.value = updatedList

                // Persist changes
                chatHistoryManager.updateChatOrderAndGroup(updatedList)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to update chat order and group", e)
                // Optionally revert UI changes or show an error
            }
        }
    }

    /** 重命名分组 */
    fun updateGroupName(oldName: String, newName: String) {
        viewModelScope.launch {
            chatHistoryManager.updateGroupName(oldName, newName)
        }
    }

    /** 删除分组 */
    fun deleteGroup(groupName: String, deleteChats: Boolean) {
        viewModelScope.launch {
            chatHistoryManager.deleteGroup(groupName, deleteChats)
        }
    }

    /** 创建新分组（通过创建新聊天实现） */
    fun createGroup(groupName: String) {
        viewModelScope.launch {
            val (inputTokens, outputTokens, windowSize) = getChatStatistics()
            saveCurrentChat(inputTokens, outputTokens, windowSize)

            val newChat = chatHistoryManager.createNewChat(group = groupName)
            _currentChatId.value = newChat.id
            _chatHistory.value = newChat.messages

            onChatHistoryLoaded(newChat.messages)
            onTokenStatisticsLoaded(0, 0, 0)
        }
    }

    /**
     * 添加一条总结消息到预先计算好的位置。
     *
     * @param summaryMessage 要添加的总结消息。
     * @param insertPosition 预先计算好的插入索引。
     */
    fun addSummaryMessage(summaryMessage: ChatMessage, insertPosition: Int) {
        viewModelScope.launch {
            historyUpdateMutex.withLock {
                val chatId = _currentChatId.value ?: return@withLock
                val currentMessages = _chatHistory.value.toMutableList()

                // 在预先计算好的位置插入总结消息
                currentMessages.add(insertPosition, summaryMessage)
                Log.d(TAG, "在预计算索引 $insertPosition 处添加总结消息，更新后总消息数量: ${currentMessages.size}")

                // 更新消息列表
                _chatHistory.value = currentMessages
                onChatHistoryLoaded(_chatHistory.value)

                // 使用带有索引的重载方法更新数据库
                chatHistoryManager.addMessage(chatId, summaryMessage, insertPosition)
            }
        }
    }

    // This function is moved to AIMessageManager
    /*
    fun shouldGenerateSummary(
        messages: List<ChatMessage>,
        currentTokens: Int,
        maxTokens: Int
    ): Boolean { ... }
    */

    // This function is moved to AIMessageManager
    /*
    suspend fun summarizeMemory(messages: List<ChatMessage>) { ... }
    */
    
    /**
     * 找到合适的总结插入位置。
     * 新的逻辑是，总结应该插入在上一个已完成对话轮次的末尾，
     * 即最后一条AI消息之后。
     */
    fun findProperSummaryPosition(messages: List<ChatMessage>): Int {
        // 从后往前找，找到最近的一条AI消息的索引。
        val lastAiMessageIndex = messages.indexOfLast { it.sender == "ai" }

        // 摘要应该被放置在最后一条AI消息之后，这标志着一个完整对话轮次的结束。
        // 如果没有找到AI消息（例如，在聊天的开始），lastAiMessageIndex将是-1，
        // 我们将在索引0处插入，这是正确的行为。
        return lastAiMessageIndex + 1
    }

    /** 切换是否显示聊天历史选择器 */
    fun toggleChatHistorySelector() {
        _showChatHistorySelector.value = !_showChatHistorySelector.value
    }

    /** 显示或隐藏聊天历史选择器 */
    fun showChatHistorySelector(show: Boolean) {
        _showChatHistorySelector.value = show
    }

    // This function is moved to AIMessageManager and renamed to getMemoryFromMessages
    /*
    fun getMemory(includePlanInfo: Boolean = true): List<Pair<String, String>> { ... }
    */

    /** 获取EnhancedAIService实例 */
    private fun getEnhancedAiService(): EnhancedAIService? {
        // 使用构造函数中传入的callback获取EnhancedAIService实例
        return getEnhancedAiService.invoke()
    }

    /** 通过回调获取当前token统计数据 */
    private fun getCurrentTokenCounts(): Pair<Int, Int> {
        // 使用构造函数中传入的回调获取当前token统计数据
        val stats = getChatStatistics()
        return Pair(stats.first, stats.second)
    }
}
