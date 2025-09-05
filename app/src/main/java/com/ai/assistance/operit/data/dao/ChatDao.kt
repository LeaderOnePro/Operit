package com.ai.assistance.operit.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ai.assistance.operit.data.model.ChatEntity
import kotlinx.coroutines.flow.Flow

/** 聊天DAO接口，定义对聊天表的数据访问方法 */
@Dao
interface ChatDao {
    /** 获取所有聊天，按显示顺序排列 */
    @Query("SELECT * FROM chats ORDER BY displayOrder ASC")
    fun getAllChats(): Flow<List<ChatEntity>>

    /** 获取所有聊天（挂起函数版本） */
    @Query("SELECT * FROM chats ORDER BY displayOrder ASC")
    suspend fun getAllChatsDirectly(): List<ChatEntity>

    /** 根据ID获取单个聊天 */
    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: String): ChatEntity?

    /** 插入或更新聊天 */
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChat(chat: ChatEntity)

    /** 删除聊天 */
    @Query("DELETE FROM chats WHERE id = :chatId") suspend fun deleteChat(chatId: String)

    /** 更新聊天元数据 */
    @Query(
            "UPDATE chats SET updatedAt = :timestamp, title = :title, inputTokens = :inputTokens, outputTokens = :outputTokens, currentWindowSize = :currentWindowSize WHERE id = :chatId"
    )
    suspend fun updateChatMetadata(
            chatId: String,
            title: String,
            timestamp: Long,
            inputTokens: Int,
            outputTokens: Int,
            currentWindowSize: Int
    )

    /** 更新聊天标题 */
    @Query("UPDATE chats SET title = :title, updatedAt = :timestamp WHERE id = :chatId")
    suspend fun updateChatTitle(chatId: String, title: String, timestamp: Long = System.currentTimeMillis())

    /** 更新聊天工作区 */
    @Query("UPDATE chats SET `workspace` = :workspace, updatedAt = :timestamp WHERE id = :chatId")
    suspend fun updateChatWorkspace(chatId: String, workspace: String?, timestamp: Long = System.currentTimeMillis())

    /** 更新聊天分组 */
    @Query("UPDATE chats SET `group` = :group, updatedAt = :timestamp WHERE id = :chatId")
    suspend fun updateChatGroup(chatId: String, group: String?, timestamp: Long = System.currentTimeMillis())

    /** 更新单个聊天的顺序和分组 */
    @Query("UPDATE chats SET displayOrder = :displayOrder, `group` = :group, updatedAt = :timestamp WHERE id = :chatId")
    suspend fun updateChatOrderAndGroup(chatId: String, displayOrder: Long, group: String?, timestamp: Long = System.currentTimeMillis())

    /** 批量更新聊天的顺序和分组 */
    @Update
    suspend fun updateChats(chats: List<ChatEntity>)

    /** 重命名分组 */
    @Query("UPDATE chats SET `group` = :newName WHERE `group` = :oldName")
    suspend fun updateGroupName(oldName: String, newName: String)

    /** 删除分组下的所有聊天 */
    @Query("DELETE FROM chats WHERE `group` = :groupName")
    suspend fun deleteChatsInGroup(groupName: String)

    /** 将分组下的所有聊天移动到“未分组” */
    @Query("UPDATE chats SET `group` = NULL, updatedAt = :timestamp WHERE `group` = :groupName")
    suspend fun removeGroupFromChats(groupName: String, timestamp: Long = System.currentTimeMillis())
}
