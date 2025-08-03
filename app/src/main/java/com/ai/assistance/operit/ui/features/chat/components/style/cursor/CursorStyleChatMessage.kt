package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.util.stream.Stream

/**
 * A composable function that renders chat messages in a Cursor IDE style. Delegates to specialized
 * composables based on message type.
 */
@Composable
fun CursorStyleChatMessage(
        message: ChatMessage,
        userMessageColor: Color,
        aiMessageColor: Color,
        userTextColor: Color,
        aiTextColor: Color,
        systemMessageColor: Color,
        systemTextColor: Color,
        thinkingBackgroundColor: Color,
        thinkingTextColor: Color,
        supportToolMarkup: Boolean = true,
        initialThinkingExpanded: Boolean = false,
        overrideStream: Stream<String>? = null
) {
    when (message.sender) {
        "user" -> {
            UserMessageComposable(
                    message = message,
                    backgroundColor = userMessageColor,
                    textColor = userTextColor
            )
        }
        "ai" -> {
            AiMessageComposable(
                    message = message,
                    backgroundColor = aiMessageColor,
                    textColor = aiTextColor,
                    overrideStream = overrideStream
            )
        }
        "system" -> {
            SystemMessageComposable(
                    message = message,
                    backgroundColor = systemMessageColor,
                    textColor = systemTextColor
            )
        }
        "summary" -> {
            SummaryMessageComposable(
                    message = message,
                    backgroundColor = systemMessageColor.copy(alpha = 0.7f),
                    textColor = systemTextColor
            )
        }
    }
}
