package com.ai.assistance.operit.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatEntity
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.util.LocaleUtils
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// 仅保留这个DataStore用于存储当前聊天ID
private val Context.currentChatIdDataStore by preferencesDataStore(name = "current_chat_id")

class ChatHistoryManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "ChatHistoryManager"

        @Volatile private var INSTANCE: ChatHistoryManager? = null

        fun getInstance(context: Context): ChatHistoryManager {
            return INSTANCE
                    ?: synchronized(this) {
                        val instance = ChatHistoryManager(context.applicationContext)
                        INSTANCE = instance
                        instance
                    }
        }
    }

    // 使用Room数据库
    private val database = AppDatabase.getDatabase(context)
    private val chatDao = database.chatDao()
    private val messageDao = database.messageDao()

    init {
        // 确保数据库被初始化
        Log.d(TAG, "ChatHistoryManager初始化，预加载数据库")
        // 使用独立的协程作用域触发数据库初始化
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 预先尝试执行一个简单查询
                val chats = chatDao.getAllChats().first()
                Log.d(TAG, "数据库预加载完成，现有聊天数：${chats.size}")
            } catch (e: Exception) {
                Log.e(TAG, "数据库预加载失败", e)
            }
        }
    }

    // 互斥锁用于同步操作
    private val mutex = Mutex()

    // DataStore键
    private object PreferencesKeys {
        val CURRENT_CHAT_ID = stringPreferencesKey("current_chat_id")
    }

    // 获取所有聊天历史（转换为UI层需要的ChatHistory对象）
    private val _chatHistoriesFlow: Flow<List<ChatHistory>> =
    // 使用原始的Flow方式，这样可以确保数据库变化时会自动刷新
    chatDao.getAllChats().map { chatEntities ->
                // Log.d(TAG, "加载聊天列表，共 ${chatEntities.size} 个聊天")

                // 使用withContext将处理移至IO线程
                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    chatEntities.map { chatEntity ->
                        // 不再加载消息，只转换元数据
                        // 将时间戳转换为LocalDateTime
                        val createdAt =
                                Instant.ofEpochMilli(chatEntity.createdAt)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDateTime()

                        val updatedAt =
                                Instant.ofEpochMilli(chatEntity.updatedAt)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDateTime()

                        // 创建ChatHistory对象（消息为空列表）
                        ChatHistory(
                                id = chatEntity.id,
                                title = chatEntity.title,
                                messages = emptyList(), // 关键改动：不加载完整消息，以提高侧边栏性能
                                createdAt = createdAt,
                                updatedAt = updatedAt,
                                inputTokens = chatEntity.inputTokens,
                                outputTokens = chatEntity.outputTokens,
                                currentWindowSize = chatEntity.currentWindowSize,
                                group = chatEntity.group, // 映射group字段
                                displayOrder = chatEntity.displayOrder,
                                workspace = chatEntity.workspace // 映射workspace字段
                        )
                    }
                }
            }

    // 转换为StateFlow以便共享
    val chatHistoriesFlow =
            _chatHistoriesFlow.stateIn(
                    CoroutineScope(Dispatchers.IO + SupervisorJob()),
                    SharingStarted.Lazily,
                    emptyList()
            )

    // 获取当前聊天ID
    private val _currentChatIdFlow: Flow<String?> =
            context.currentChatIdDataStore.data
                    .catch { exception ->
                        if (exception is IOException) {
                            emit(emptyPreferences())
                        } else {
                            throw exception
                        }
                    }
                    .map { preferences -> preferences[PreferencesKeys.CURRENT_CHAT_ID] }

    // 转换为StateFlow以便共享
    val currentChatIdFlow =
            _currentChatIdFlow.stateIn(
                    CoroutineScope(Dispatchers.IO + SupervisorJob()),
                    SharingStarted.Lazily,
                    null
            )

    // 保存聊天历史
    suspend fun saveChatHistory(history: ChatHistory) {
        mutex.withLock {
            try {
                // 创建聊天实体
                val chatEntity = ChatEntity.fromChatHistory(history)

                // 保存聊天实体
                chatDao.insertChat(chatEntity)

                // 先删除该聊天的所有现有消息
                messageDao.deleteAllMessagesForChat(chatEntity.id)

                // 批量插入所有消息
                val messageEntities =
                        history.messages.mapIndexed { index, message ->
                            MessageEntity.fromChatMessage(chatEntity.id, message, index)
                        }
                messageDao.insertMessages(messageEntities)
            } catch (e: Exception) {
                throw e
            }
        }
    }

    // 添加单条消息
    suspend fun addMessage(chatId: String, message: ChatMessage, position: Int? = null) {
        mutex.withLock {
            try {
                val messageToPersist =
                        if (position != null) {
                            val messages =
                                    messageDao.getMessagesForChat(
                                            chatId
                                    ) // Already ordered by timestamp
                            if (messages.isEmpty()) {
                                message
                            } else {
                                val validPosition = position.coerceIn(0, messages.size)
                                val newTimestamp =
                                        when {
                                            validPosition == 0 -> messages.first().timestamp - 1
                                            validPosition >= messages.size ->
                                                    messages.last().timestamp + 1
                                            else -> {
                                                val before = messages[validPosition - 1].timestamp
                                                val after = messages[validPosition].timestamp
                                                // Take the average to find a point in between.
                                                // This assumes timestamps have enough space.
                                                before + (after - before) / 2
                                            }
                                        }
                                message.copy(timestamp = newTimestamp)
                            }
                        } else {
                            message
                        }

                // Create message entity, orderIndex is no longer used for ordering.
                val messageEntity =
                        MessageEntity.fromChatMessage(
                                chatId = chatId,
                                message = messageToPersist,
                                orderIndex = 0
                        )
                messageDao.insertMessage(messageEntity)

                // Update chat metadata
                chatDao.getChatById(chatId)?.let { chat ->
                    chatDao.updateChatMetadata(
                            chatId = chatId,
                            title = chat.title,
                            timestamp = System.currentTimeMillis(),
                            inputTokens = chat.inputTokens,
                            outputTokens = chat.outputTokens,
                            currentWindowSize = chat.currentWindowSize
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add message for chat $chatId", e)
                throw e
            }
        }
    }

    /**
     * 批量更新聊天记录的顺序和分组
     * @param updatedHistories 包含更新信息的ChatHistory列表
     */
    suspend fun updateChatOrderAndGroup(updatedHistories: List<ChatHistory>) {
        mutex.withLock {
            try {
                val timestamp = System.currentTimeMillis()
                val entitiesToUpdate = updatedHistories.map { history ->
                    // Find the original entity to keep other fields intact
                    val originalEntity = chatDao.getChatById(history.id)
                    originalEntity?.copy(
                        displayOrder = history.displayOrder,
                        group = history.group,
                        updatedAt = timestamp
                    ) ?: ChatEntity.fromChatHistory(history.copy(updatedAt = LocalDateTime.now()))
                }
                chatDao.updateChats(entitiesToUpdate)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update chat order and group", e)
                throw e
            }
        }
    }

    /**
     * 重命名分组
     * @param oldName 旧的分组名称
     * @param newName 新的分组名称
     */
    suspend fun updateGroupName(oldName: String, newName: String) {
        mutex.withLock {
            try {
                chatDao.updateGroupName(oldName, newName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rename group from $oldName to $newName", e)
                throw e
            }
        }
    }

    /**
     * 删除分组
     * @param groupName 要删除的分组名称
     * @param deleteChats 是否同时删除分组下的聊天记录
     */
    suspend fun deleteGroup(groupName: String, deleteChats: Boolean) {
        mutex.withLock {
            try {
                if (deleteChats) {
                    chatDao.deleteChatsInGroup(groupName)
                } else {
                    chatDao.removeGroupFromChats(groupName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete group $groupName (deleteChats: $deleteChats)", e)
                throw e
            }
        }
    }

    /**
     * 删除单条消息.
     * @param chatId 聊天ID
     * @param timestamp 消息时间戳
     */
    suspend fun deleteMessage(chatId: String, timestamp: Long) {
        mutex.withLock {
            try {
                Log.d(TAG, "正在从数据库删除消息. ChatId: $chatId, Timestamp: $timestamp")
                messageDao.deleteMessageByTimestamp(chatId, timestamp)
                Log.d(TAG, "消息从数据库删除成功.")

                // Update chat metadata
                chatDao.getChatById(chatId)?.let { chat ->
                    chatDao.updateChatMetadata(
                            chatId = chatId,
                            title = chat.title,
                            timestamp = System.currentTimeMillis(),
                            inputTokens = chat.inputTokens,
                            outputTokens = chat.outputTokens,
                            currentWindowSize = chat.currentWindowSize
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete message with timestamp $timestamp for chat $chatId", e)
                throw e
            }
        }
    }

    // 更新现有消息
    suspend fun updateMessage(chatId: String, message: ChatMessage) {
        mutex.withLock {
            try {
                // 找到相应的消息实体
                val existingMessage = messageDao.getMessageByTimestamp(chatId, message.timestamp)

                if (existingMessage != null) {
                    // 更新现有消息
                    messageDao.updateMessageContent(existingMessage.messageId, message.content)

                    // 更新聊天元数据时间戳
                    val chat = chatDao.getChatById(chatId)
                    if (chat != null) {
                        chatDao.updateChatMetadata(
                                chatId = chatId,
                                title = chat.title,
                                timestamp = System.currentTimeMillis(),
                                inputTokens = chat.inputTokens,
                                outputTokens = chat.outputTokens,
                                currentWindowSize = chat.currentWindowSize
                        )
                    }
                } else {
                    // 如果找不到现有消息，则添加新消息
                    addMessage(chatId, message)
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    /**
     * 从数据库中删除指定时间戳之后的所有消息。 这需要您在MessageDao中添加相应的@Query。
     *
     * 示例:
     * ```
     * @Query("DELETE FROM messages WHERE chatId = :chatId AND timestamp >= :timestamp")
     * suspend fun deleteMessagesFrom(chatId: String, timestamp: Long)
     * ```
     */
    suspend fun deleteMessagesFrom(chatId: String, timestamp: Long) {
        mutex.withLock {
            try {
                Log.d(TAG, "正在从数据库删除消息. ChatId: $chatId, Timestamp >=: $timestamp")
                messageDao.deleteMessagesFrom(chatId, timestamp)
                Log.d(TAG, "后续消息从数据库删除成功.")
                // 更新聊天元数据时间戳
                chatDao.getChatById(chatId)?.let { chat ->
                    chatDao.updateChatMetadata(
                            chatId = chatId,
                            title = chat.title,
                            timestamp = System.currentTimeMillis(),
                            inputTokens = chat.inputTokens,
                            outputTokens = chat.outputTokens,
                            currentWindowSize = chat.currentWindowSize
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "从 $timestamp 开始为聊天 $chatId 删除消息失败", e)
                throw e
            }
        }
    }

    /**
     * 清除一个聊天中的所有消息，但保留聊天本身。
     *
     * 这需要您在MessageDao中添加相应的@Query。
     * ```
     * @Query("DELETE FROM messages WHERE chatId = :chatId")
     * suspend fun deleteAllMessagesForChat(chatId: String)
     * ```
     */
    suspend fun clearChatMessages(chatId: String) {
        mutex.withLock {
            try {
                messageDao.deleteAllMessagesForChat(chatId)
                // 更新聊天元数据
                chatDao.getChatById(chatId)?.let { chat ->
                    chatDao.updateChatMetadata(
                            chatId = chatId,
                            title = chat.title,
                            timestamp = System.currentTimeMillis(),
                            inputTokens = 0,
                            outputTokens = 0,
                            currentWindowSize = 0
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "为聊天 $chatId 清除消息失败", e)
                throw e
            }
        }
    }

    // 更新聊天标题
    suspend fun updateChatTitle(chatId: String, title: String) {
        mutex.withLock {
            try {
                chatDao.updateChatTitle(chatId, title)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update chat title for chat $chatId", e)
                throw e
            }
        }
    }

    // 更新聊天的token计数
    suspend fun updateChatTokenCounts(
        chatId: String,
        inputTokens: Int,
        outputTokens: Int,
        currentWindowSize: Int
    ) {
        mutex.withLock {
            try {
                val chat = chatDao.getChatById(chatId)
                if (chat != null) {
                    chatDao.updateChatMetadata(
                            chatId = chatId,
                            title = chat.title,
                            timestamp = System.currentTimeMillis(),
                            inputTokens = inputTokens,
                            outputTokens = outputTokens,
                            currentWindowSize = currentWindowSize
                    )
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    // 设置当前聊天ID
    suspend fun setCurrentChatId(chatId: String) {
        context.currentChatIdDataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENT_CHAT_ID] = chatId
        }
    }

    // 删除聊天历史
    suspend fun deleteChatHistory(chatId: String) {
        mutex.withLock {
            try {
                // 删除聊天实体（级联删除所有消息）
                chatDao.deleteChat(chatId)

                // 如果删除的是当前聊天，清除当前聊天ID
                val currentChatId = currentChatIdFlow.first()
                if (currentChatId == chatId) {
                    context.currentChatIdDataStore.edit { preferences ->
                        preferences.remove(PreferencesKeys.CURRENT_CHAT_ID)
                    }
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }

    // 创建新对话
    suspend fun createNewChat(group: String? = null, inheritGroupFromChatId: String? = null): ChatHistory {
        val dateTime = LocalDateTime.now()
        val formattedTime =
                "${dateTime.hour}:${dateTime.minute.toString().padStart(2, '0')}:${dateTime.second.toString().padStart(2, '0')}"

        val localizedContext = LocaleUtils.getLocalizedContext(context)
        
        // 确定新对话的分组
        val finalGroup = when {
            // 如果显式指定了分组，使用指定的分组
            group != null -> group
            // 如果要继承分组，尝试从指定的对话获取分组
            inheritGroupFromChatId != null -> {
                chatDao.getChatById(inheritGroupFromChatId)?.group
                    ?: localizedContext.getString(R.string.ungrouped)
            }
            // 默认使用未分组
            else -> localizedContext.getString(R.string.ungrouped)
        }
        
        val newHistory =
                ChatHistory(
                        title = "${localizedContext.getString(R.string.new_conversation)} $formattedTime",
                        messages = listOf<ChatMessage>(),
                        inputTokens = 0,
                        outputTokens = 0,
                        group = finalGroup
                )

        // 保存新聊天
        val chatEntity = ChatEntity.fromChatHistory(newHistory)
        chatDao.insertChat(chatEntity)

        // 设置为当前聊天
        setCurrentChatId(newHistory.id)

        return newHistory
    }

    /** 更新聊天工作区 */
    suspend fun updateChatWorkspace(chatId: String, workspace: String?) {
        mutex.withLock {
            try {
                chatDao.updateChatWorkspace(chatId, workspace)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update chat workspace for chat $chatId", e)
                throw e
            }
        }
    }

    // 更新聊天分组
    suspend fun updateChatGroup(chatId: String, group: String?) {
        mutex.withLock {
            try {
                chatDao.updateChatGroup(chatId, group)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update chat group for chat $chatId", e)
                throw e
            }
        }
    }

    // 直接加载聊天消息
    suspend fun loadChatMessages(chatId: String): List<ChatMessage> {
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                // Log.d(TAG, "直接从数据库加载聊天 $chatId 的消息")
                val messages = messageDao.getMessagesForChat(chatId)
                // Log.d(TAG, "聊天 $chatId 共加载 ${messages.size} 条消息")
                messages.map { it.toChatMessage() }
            } catch (e: Exception) {
                Log.e(TAG, "加载聊天消息失败", e)
                emptyList()
            }
        }
    }
}
