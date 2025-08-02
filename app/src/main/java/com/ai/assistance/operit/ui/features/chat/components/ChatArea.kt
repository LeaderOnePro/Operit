package com.ai.assistance.operit.ui.features.chat.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.AiReference
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.PlanItem
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.ui.unit.Dp

@Composable
fun ChatArea(
    chatHistory: List<ChatMessage>,
    scrollState: ScrollState,
    aiReferences: List<AiReference> = emptyList(),
    planItems: List<PlanItem> = emptyList(),
    enablePlanning: Boolean = false,
    isLoading: Boolean,
    userMessageColor: Color,
    aiMessageColor: Color,
    userTextColor: Color,
    aiTextColor: Color,
    systemMessageColor: Color,
    systemTextColor: Color,
    thinkingBackgroundColor: Color,
    thinkingTextColor: Color,
    hasBackgroundImage: Boolean = false,
    modifier: Modifier = Modifier,
    onSelectMessageToEdit: ((Int, ChatMessage, String) -> Unit)? = null,
    onCopyMessage: ((ChatMessage) -> Unit)? = null,
    onDeleteMessage: ((Int) -> Unit)? = null,
    onDeleteMessagesFrom: ((Int) -> Unit)? = null,
    messagesPerPage: Int = 10, // 每页显示的消息数量
    topPadding: Dp = 0.dp
) {
    // 记住当前深度状态，但当chatHistory发生变化时重置为1
    var currentDepth = remember(chatHistory) { mutableStateOf(1) }

    Column(modifier = modifier) {
        // 移除References display

        // Plan display when planning is enabled and there are plan items
        if (enablePlanning && planItems.isNotEmpty()) {
            PlanGraphDisplay(planItems = planItems, modifier = Modifier.fillMaxWidth())
        } else {
            // 删除全部不必要的诊断日志
        }

        // 改用普通Column替代LazyColumn，避免复杂的回收逻辑带来的性能问题
        Column(
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState) // 使用从外部传入的scrollState
                    .background(Color.Transparent)
                    .padding(top = topPadding),
        ) {
            val messagesCount = chatHistory.size
            val maxVisibleIndex = messagesCount - 1
            val minVisibleIndex =
                maxOf(0, maxVisibleIndex - currentDepth.value * messagesPerPage + 1)
            val hasMoreMessages = minVisibleIndex > 0

            // "加载更多"文本 - 改为灰色文本而非按钮
            if (hasMoreMessages) {
                Text(
                    text = "加载更多历史消息...",
                    modifier =
                        Modifier.fillMaxWidth()
                            .clickable { currentDepth.value += 1 }
                            .padding(vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 根据当前深度筛选显示的消息
            chatHistory.subList(minVisibleIndex, messagesCount).forEachIndexed {
                    relativeIndex,
                    message ->
                val actualIndex = minVisibleIndex + relativeIndex
                // 使用key组合函数为每个消息项设置单独的重组作用域
                key(message.timestamp) {
                    MessageItem(
                        index = actualIndex,
                        message = message,
                        userMessageColor = userMessageColor,
                        aiMessageColor = aiMessageColor,
                        userTextColor = userTextColor,
                        aiTextColor = aiTextColor,
                        systemMessageColor = systemMessageColor,
                        systemTextColor = systemTextColor,
                        thinkingBackgroundColor = thinkingBackgroundColor,
                        thinkingTextColor = thinkingTextColor,
                        onSelectMessageToEdit = onSelectMessageToEdit,
                        onCopyMessage = onCopyMessage,
                        onDeleteMessage = onDeleteMessage,
                        onDeleteMessagesFrom = onDeleteMessagesFrom
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // 当AI正在响应但尚未输出任何文本时，显示加载指示器
            val lastMessage = chatHistory.lastOrNull { it.sender != "think" }
            if (isLoading &&
                (lastMessage?.sender == "user" ||
                    (lastMessage?.sender == "ai" && lastMessage.content.isBlank()))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp)) {
                    // 加载指示器放在左侧，与标签对齐
                    Box(modifier = Modifier.padding(start = 16.dp)) {
                        LoadingDotsIndicator(aiTextColor)
                    }
                }
            }

            // 添加额外的空白区域，防止消息被输入框遮挡
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** 单个消息项组件 将消息渲染逻辑提取到单独的组件，减少重组范围 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    index: Int,
    message: ChatMessage,
    userMessageColor: Color,
    aiMessageColor: Color,
    userTextColor: Color,
    aiTextColor: Color,
    systemMessageColor: Color,
    systemTextColor: Color,
    thinkingBackgroundColor: Color,
    thinkingTextColor: Color,
    onSelectMessageToEdit: ((Int, ChatMessage, String) -> Unit)?,
    onCopyMessage: ((ChatMessage) -> Unit)?,
    onDeleteMessage: ((Int) -> Unit)?,
    onDeleteMessagesFrom: ((Int) -> Unit)?
) {
    val context = LocalContext.current
    var showContextMenu by remember { mutableStateOf(false) }
    var showSelectableCopyDialog by remember { mutableStateOf(false) }

    // 只有用户和AI的消息才能被操作
    val isActionable = message.sender == "user" || message.sender == "ai"

    Box(
        modifier =
            Modifier.combinedClickable(
                onClick = {},
                onLongClick = { if (isActionable) showContextMenu = true },
            ),
    ) {
        CursorStyleChatMessage(
            message = message,
            userMessageColor = userMessageColor,
            aiMessageColor = aiMessageColor,
            userTextColor = userTextColor,
            aiTextColor = aiTextColor,
            systemMessageColor = systemMessageColor,
            systemTextColor = systemTextColor,
            thinkingBackgroundColor = thinkingBackgroundColor,
            thinkingTextColor = thinkingTextColor,
            supportToolMarkup = true,
            initialThinkingExpanded = true,
        )

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier
                .width(140.dp)
                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(6.dp)),
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            // 复制选项
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(id = R.string.copy_message),
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp
                    )
                },
                onClick = {
                    val clipboardManager =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = ClipData.newPlainText("聊天消息", message.content)
                    clipboardManager.setPrimaryClip(clipData)
                    Toast.makeText(context, "消息已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    onCopyMessage?.invoke(message)
                    showContextMenu = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(id = R.string.copy_message),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.height(36.dp)
            )

            // 框选复制选项
            DropdownMenuItem(
                text = {
                    Text(
                        "框选复制",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp
                    )
                },
                onClick = {
                    showSelectableCopyDialog = true
                    showContextMenu = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = "框选复制",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.height(36.dp)
            )

            // 根据消息发送者显示不同的操作
            if (message.sender == "user") {
                // 编辑并重发选项
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(id = R.string.edit_and_resend),
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        onSelectMessageToEdit?.invoke(index, message, "user")
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(id = R.string.edit_and_resend),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.height(36.dp)
                )
            } else if (message.sender == "ai") {
                // 修改记忆选项
                DropdownMenuItem(
                    text = {
                        Text(
                            "修改记忆",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 13.sp
                        )
                    },
                    onClick = {
                        onSelectMessageToEdit?.invoke(index, message, "ai")
                        showContextMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = "修改记忆",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.height(36.dp)
                )
            }

            // 删除
            DropdownMenuItem(
                text = {
                    Text(
                        "删除",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp
                    )
                },
                onClick = {
                    onDeleteMessage?.invoke(index)
                    showContextMenu = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.height(36.dp)
            )

            // 删除到此
            DropdownMenuItem(
                text = {
                    Text(
                        "删除到此",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp
                    )
                },
                onClick = {
                    onDeleteMessagesFrom?.invoke(index)
                    showContextMenu = false
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "删除到此",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.height(36.dp)
            )
        }

        if (showSelectableCopyDialog) {
            SelectableCopyDialog(
                text = message.content,
                onDismiss = { showSelectableCopyDialog = false }
            )
        }
    }
}

@Composable
private fun SelectableCopyDialog(text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("框选复制") },
        text = {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                readOnly = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedText = textFieldValue.text.substring(
                        textFieldValue.selection.start,
                        textFieldValue.selection.end
                    )
                    if (selectedText.isNotEmpty()) {
                        val clipboardManager =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipData = ClipData.newPlainText("selected text", selectedText)
                        clipboardManager.setPrimaryClip(clipData)
                        Toast.makeText(context, "已复制选中内容", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                enabled = !textFieldValue.selection.collapsed
            ) {
                Text("复制")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun LoadingDotsIndicator(textColor: Color) {
    val infiniteTransition = rememberInfiniteTransition()

    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val jumpHeight = -5f
        val animationDelay = 160

        (0..2).forEach { index ->
            val offsetY by
                infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = jumpHeight,
                    animationSpec =
                        infiniteRepeatable(
                            animation =
                                keyframes {
                                    durationMillis = 600
                                    0f at 0
                                    jumpHeight * 0.4f at 100
                                    jumpHeight * 0.8f at 200
                                    jumpHeight at 300
                                    jumpHeight * 0.8f at 400
                                    jumpHeight * 0.4f at 500
                                    0f at 600
                                },
                            repeatMode = RepeatMode.Restart,
                            initialStartOffset = StartOffset(index * animationDelay),
                        ),
                    label = "",
                )

            Box(
                modifier =
                    Modifier.size(6.dp)
                        .offset(y = offsetY.dp)
                        .background(
                            color = textColor.copy(alpha = 0.6f),
                            shape = CircleShape,
                        ),
            )
        }
    }
}
