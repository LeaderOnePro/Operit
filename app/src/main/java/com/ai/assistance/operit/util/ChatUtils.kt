package com.ai.assistance.operit.util

/** Utility functions for chat message handling */
object ChatUtils {
    fun mapToStandardRole(role: String): String {
        return when (role) {
            "ai" -> "assistant"
            "tool" -> "user" // AI, assistant and tool messages map to assistant
            "user" -> "user" // User messages remain as user
            "system" -> "system" // System messages remain as system
            "summary" -> "user" // Summary messages are treated as user messages
            else -> role // Default to user for any other role
        }
    }

    /** 过滤掉内容中的思考部分 移除<think></think>和<thinking></thinking>标签及其中的内容，并处理未闭合的情况 */
    fun removeThinkingContent(content: String): String {
        // 使用正则表达式匹配<think>和<thinking>标签及其内容
        // 这个正则表达式会匹配四种情况：
        // 1. <think>...</think> (正常闭合的标签)
        // 2. <think>... (未闭合，直到字符串末尾)
        // 3. <thinking>...</thinking> (正常闭合的标签)
        // 4. <thinking>... (未闭合，直到字符串末尾)
        // \\z 匹配字符串的绝对末尾
        val thinkPattern = "<think(?:ing)?>.*?(</think(?:ing)?>|\\z)".toRegex(RegexOption.DOT_MATCHES_ALL)
        return content.replace(thinkPattern, "").trim()
    }

    /**
     * 估算给定文本的token数量
     * @param text 要估算token的文本
     * @return 估算的token数量
     */
    fun estimateTokenCount(text: String): Int {
        // 简单估算：中文每个字约1.5个token，英文每4个字符约1个token
        val chineseCharCount = text.count { it.code in 0x4E00..0x9FFF }
        val otherCharCount = text.length - chineseCharCount
        return (chineseCharCount * 1.5 + otherCharCount * 0.25).toInt()
    }

    fun mapChatHistoryToStandardRoles(
            chatHistory: List<Pair<String, String>>
    ): List<Pair<String, String>> {
        return chatHistory.map { (role, content) ->
            val standardRole = mapToStandardRole(role)
            // 对于assistant角色的消息，移除思考内容
            val processedContent =
                    if (standardRole == "assistant" || role == "ai") {
                        removeThinkingContent(content)
                    } else {
                        content
                    }
            Pair(standardRole, processedContent)
        }
    }

    /**
     * 从 AI 响应中提取 JSON 对象部分
     * AI 可能会在 JSON 前后添加说明文字或使用 ```json 代码块，需要提取出纯净的 JSON
     */
    fun extractJson(response: String): String {
        var text = response.trim()
        
        // 处理 markdown 代码块格式 ```json ... ```
        if (text.startsWith("```")) {
            val lines = text.lines()
            text = lines.drop(1).dropLast(1).joinToString("\n").trim()
        }
        
        // 寻找第一个 { 和最后一个 }
        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        
        return if (firstBrace != -1 && lastBrace != -1 && firstBrace < lastBrace) {
            text.substring(firstBrace, lastBrace + 1)
        } else {
            // 如果没找到完整的 JSON 结构，返回原始字符串
            text
        }
    }

    /**
     * 从 AI 响应中提取 JSON 数组部分
     * AI 可能会在 JSON 前后添加说明文字或使用 ```json 代码块，需要提取出纯净的 JSON
     */
    fun extractJsonArray(response: String): String {
        var text = response.trim()
        
        // 处理 markdown 代码块格式 ```json ... ```
        if (text.startsWith("```")) {
            val lines = text.lines()
            text = lines.drop(1).dropLast(1).joinToString("\n").trim()
        }
        
        // 寻找第一个 [ 和最后一个 ]
        val firstBracket = text.indexOf('[')
        val lastBracket = text.lastIndexOf(']')
        
        return if (firstBracket != -1 && lastBracket != -1 && firstBracket < lastBracket) {
            text.substring(firstBracket, lastBracket + 1)
        } else {
            // 如果没找到完整的 JSON 结构，返回原始字符串
            text
        }
    }
}
