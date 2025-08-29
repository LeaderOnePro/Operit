package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 提示词标签数据模型
 */
@Entity(tableName = "prompt_tags")
data class PromptTag(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String = "",
    val promptContent: String = "", // 标签的提示词内容
    val tagType: TagType = TagType.CUSTOM, // 标签类型
    val isSystemTag: Boolean = false, // 是否为系统标签
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 标签类型枚举
 */
enum class TagType {
    SYSTEM_CHAT,      // 通用聊天
    SYSTEM_VOICE,     // 通用语音
    SYSTEM_DESKTOP_PET, // 通用桌宠
    TONE,             // 语气风格标签
    CHARACTER,        // 角色设定标签
    FUNCTION,         // 功能性标签
    CUSTOM            // 自定义标签
} 