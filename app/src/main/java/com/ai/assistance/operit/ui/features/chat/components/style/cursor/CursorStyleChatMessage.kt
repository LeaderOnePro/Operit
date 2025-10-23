package com.ai.assistance.operit.ui.features.chat.components.style.cursor

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
        overrideStream: Stream<String>? = null,
        onDeleteMessage: ((Int) -> Unit)? = null,
        index: Int = -1,
        enableDialogs: Boolean = true  // 新增参数：是否启用弹窗功能，默认启用
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
                    overrideStream = overrideStream,
                    enableDialogs = enableDialogs  // 传递弹窗启用状态
            )
        }
        "summary" -> {
            SummaryMessageComposable(
                    message = message,
                    backgroundColor = systemMessageColor.copy(alpha = 0.7f),
                    textColor = systemTextColor,
                    onDelete = {
                        if (index != -1) {
                            onDeleteMessage?.invoke(index)
                        }
                    },
                    enableDialog = enableDialogs  // 传递弹窗启用状态
            )
        }
    }
}
