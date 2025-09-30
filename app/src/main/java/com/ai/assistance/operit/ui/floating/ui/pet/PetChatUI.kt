package com.ai.assistance.operit.ui.floating.ui.pet

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.floating.ui.fullscreen.XmlTextProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PetChatBubble(message: String) {
    var displayedMessage by remember { mutableStateOf("") }
    var showClose by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // 使用LaunchedEffect来实现打字机效果
    LaunchedEffect(message) {
        displayedMessage = ""
        // 先对消息进行trim和XML标签清理
        val cleanedMessage = AvatarEmotionManager.stripXmlLikeTags(message).trim()
        for (char in cleanedMessage) {
            displayedMessage += char
            delay(15) // 字符显示的延迟，可以调整以改变速度
        }
        // 消息完全显示后显示关闭按钮
        delay(500)
        showClose = true
    }

    LaunchedEffect(displayedMessage) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box {
        Box(
                modifier =
                        Modifier.shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
                                .background(
                                        color =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.9f
                                                ),
                                        shape = RoundedCornerShape(12.dp)
                                )
                                .padding(
                                        start = 16.dp,
                                        end = if (showClose) 28.dp else 16.dp,
                                        top = 10.dp,
                                        bottom = 10.dp
                                )
                                .animateContentSize()
                                .heightIn(max = 300.dp)
                                .verticalScroll(scrollState)
        ) {
            Text(
                    text = displayedMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 将关闭按钮移到对话框内部
        if (showClose) {
            Box(
                    modifier =
                            Modifier.align(Alignment.TopEnd)
                                    .offset(x = (-4).dp, y = 4.dp)
                                    .size(16.dp)
                                    .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            CircleShape
                                    )
                                    .padding(2.dp)
            ) {
                Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭对话气泡",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun PetChatBubble(chatMessage: ChatMessage, onClose: () -> Unit = {}) {
    var displayedMessage by remember { mutableStateOf("") }
    var showClose by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // 处理流式消息
    LaunchedEffect(chatMessage) {
        displayedMessage = ""
        showClose = false

        // 如果有内容流，按照流式处理
        chatMessage.contentStream?.let { stream ->
            val processedStream = XmlTextProcessor.processStreamToText(stream)

            // 用于跟踪是否已经跳过了开头的空白字符
            var hasSkippedLeadingWhitespace = false
            // 用于缓存尾部可能的空白字符
            val trailingBuffer = StringBuilder()
            // 最大缓冲区大小，超过这个大小就确定不是尾部空白了
            val maxBufferSize = 10

            processedStream.collect { char ->
                if (!hasSkippedLeadingWhitespace) {
                    // 跳过开头的空白字符
                    if (!char.isWhitespace()) {
                        hasSkippedLeadingWhitespace = true
                        displayedMessage += char
                        delay(15)
                    }
                } else {
                    if (char.isWhitespace()) {
                        // 可能是尾部空白，先加入缓冲区
                        trailingBuffer.append(char)
                        if (trailingBuffer.length > maxBufferSize) {
                            // 缓冲区太大，不太可能是尾部空白了，显示之前的内容
                            displayedMessage +=
                                    trailingBuffer.substring(
                                            0,
                                            trailingBuffer.length - maxBufferSize
                                    )
                            // 保留最后的maxBufferSize个字符在缓冲区
                            trailingBuffer.delete(0, trailingBuffer.length - maxBufferSize)
                        }
                    } else {
                        // 非空白字符，显示之前缓冲的所有内容
                        if (trailingBuffer.isNotEmpty()) {
                            displayedMessage += trailingBuffer.toString()
                            trailingBuffer.clear()
                        }
                        displayedMessage += char
                        delay(15)
                    }
                }
            }

            // 流结束后，丢弃尾部的空白字符
            // 不显示trailingBuffer中的内容
        }
                ?: run {
                    // 如果没有流，则显示静态内容，但仍然使用打字机效果
                    // 先对消息进行trim和XML标签清理
                    val cleanedContent = AvatarEmotionManager.stripXmlLikeTags(chatMessage.content).trim()
                    for (char in cleanedContent) {
                        displayedMessage += char
                        delay(15)
                    }
                }

        // 消息完全显示后显示关闭按钮
        delay(500)
        showClose = true
    }

    LaunchedEffect(displayedMessage) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box {
        Box(
                modifier =
                        Modifier.shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp))
                                .background(
                                        color =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.9f
                                                ),
                                        shape = RoundedCornerShape(12.dp)
                                )
                                .padding(
                                        start = 16.dp,
                                        end = if (showClose) 28.dp else 16.dp,
                                        top = 10.dp,
                                        bottom = 10.dp
                                )
                                .animateContentSize()
                                .heightIn(max = 300.dp)
                                .verticalScroll(scrollState)
        ) {
            Text(
                    text = displayedMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 将关闭按钮移到对话框内部
        if (showClose) {
            Box(
                    modifier =
                            Modifier.align(Alignment.TopEnd)
                                    .offset(x = (-4).dp, y = 4.dp)
                                    .size(16.dp)
                                    .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            CircleShape
                                    )
                                    .clickable { onClose() } // 调用外部传入的onClose回调
                                    .padding(2.dp)
            ) {
                Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭对话气泡",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun PetChatInputDialog(onDismiss: () -> Unit, onSendMessage: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // 创建一个完全透明的背景，不再使用半透明黑色
    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .zIndex(10f) // 确保在DragonBones模型上方
                            // 移除背景色
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    // 点击空白区域关闭对话框
                                    onDismiss()
                                }
                            },
            contentAlignment = Alignment.TopCenter // 放在顶部中央
    ) {
        // 对话框内容 - 放在顶部，避免被模型遮挡
        Surface(
                modifier =
                        Modifier.fillMaxWidth(0.9f)
                                .padding(top = 40.dp) // 从顶部留出空间
                                .heightIn(max = 160.dp) // 减小高度，更紧凑
                                .clickable(
                                        enabled = true,
                                        onClick = {},
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                ),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp // 增加阴影，提高可见度
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // 标题和关闭按钮
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text = "和宠物对话",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                    )

                    // 减小关闭按钮尺寸
                    IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                        Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 输入框区域
                Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // 输入框
                    OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = {
                                Text("输入消息...", style = MaterialTheme.typography.bodyMedium)
                            },
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions =
                                    KeyboardActions(
                                            onSend = {
                                                if (text.isNotBlank()) {
                                                    onSendMessage(text)
                                                    text = ""
                                                }
                                            }
                                    ),
                            colors =
                                    OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 发送按钮
                    IconButton(
                            onClick = {
                                if (text.isNotBlank()) {
                                    onSendMessage(text)
                                    text = ""
                                }
                            },
                            modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "发送",
                                tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // 添加一个当输入区域显示时的DisposableEffect，确保获取焦点和调起键盘
    DisposableEffect(Unit) {
        coroutineScope.launch {
            // 增加延迟，确保视图完全渲染
            delay(300)
            try {
                focusRequester.requestFocus()
                // 直接尝试显示键盘
                keyboardController?.show()

                // 再次尝试显示键盘，增加成功率
                delay(100)
                keyboardController?.show()
            } catch (e: Exception) {
                Log.e("PetChatInputDialog", "Failed to request focus", e)
            }
        }

        onDispose {
            // 确保在对话框关闭时隐藏键盘
            keyboardController?.hide()
        }
    }
}
