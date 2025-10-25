package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 自定义表情数据模型
 * 
 * @property id 唯一标识符（UUID）
 * @property emotionCategory 情绪类别（如 "happy", "sad" 或用户自定义）
 * @property fileName 文件名（如 "uuid.jpg"），不包含路径
 * @property isBuiltInCategory 是否为内置类别
 * @property createdAt 创建时间戳
 * 
 * 注意：只存储fileName，完整路径在使用时动态构建为 custom_emoji/{category}/{fileName}
 */
@Serializable
data class CustomEmoji(
    val id: String = UUID.randomUUID().toString(),
    val emotionCategory: String,
    val fileName: String,
    val isBuiltInCategory: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

