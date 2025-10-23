package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.util.ImagePoolManager

/**
 * 图片链接数据类
 */
data class ImageLink(
    val type: String,
    val id: String,
    val base64Data: String?,
    val mimeType: String
)

/**
 * 图片链接解析工具
 */
object ImageLinkParser {
    private val LINK_PATTERN = Regex("""<link\s+type="image"\s+id="([^"]+)"\s*>.*?</link>""", RegexOption.DOT_MATCHES_ALL)
    
    /**
     * 提取消息中的所有图片链接并获取其base64数据
     * 如果图片不存在或已过期，会被静默跳过
     */
    fun extractImageLinks(message: String): List<ImageLink> {
        return LINK_PATTERN.findAll(message).mapNotNull { match ->
            val id = match.groupValues[1]
            
            // 跳过error标记
            if (id == "error") {
                return@mapNotNull null
            }
            
            // 从池中获取图片数据
            val imageData = ImagePoolManager.getImage(id)
            if (imageData == null) {
                // 图片已过期，静默跳过
                return@mapNotNull null
            }
            
            ImageLink(
                type = "image",
                id = id,
                base64Data = imageData.base64,
                mimeType = imageData.mimeType
            )
        }.toList()
    }
    
    /**
     * 移除消息中的所有图片链接标签
     */
    fun removeImageLinks(message: String): String {
        return message.replace(LINK_PATTERN, "")
    }
    
    /**
     * 检查消息是否包含图片链接
     */
    fun hasImageLinks(message: String): Boolean {
        return LINK_PATTERN.containsMatchIn(message)
    }
}
