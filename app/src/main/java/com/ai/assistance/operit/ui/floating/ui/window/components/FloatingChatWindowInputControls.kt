package com.ai.assistance.operit.ui.floating.ui.window.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.ui.features.chat.components.AttachmentChip
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.ui.window.viewmodel.FloatingChatWindowModeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FloatingChatWindowInputControls(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel
) {
    if (floatContext.showAttachmentPanel && !floatContext.showInputDialog) {
        AttachmentPanelOverlay(floatContext, viewModel)
    }
    if (!floatContext.showInputDialog && floatContext.onSendMessage != null) {
        BottomInputBar(floatContext, viewModel)
    }
}

/** 底部输入栏 */
@Composable
private fun BottomInputBar(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val hasContent = floatContext.userMessage.isNotBlank()
    var isInputFocused by remember { mutableStateOf(false) }
    
    // 检测 AI 是否正在处理消息 - 使用 chatService 的 isLoading 状态
    val isProcessing = floatContext.chatService?.getChatCore()?.isLoading?.collectAsState()?.value ?: false
    
    // 监听焦点状态变化，通知服务更新窗口焦点
    LaunchedEffect(isInputFocused) {
        floatContext.onInputFocusRequest?.invoke(isInputFocused)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        // 附件列表
        if (floatContext.attachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(floatContext.attachments) { attachmentInfo ->
                    AttachmentChip(
                        attachmentInfo = attachmentInfo,
                        onInsert = {},
                        onRemove = { floatContext.onRemoveAttachment?.invoke(attachmentInfo.filePath) }
                    )
                }
            }
        }
        
        // 输入栏（参考 ChatInputSection 的布局）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 输入框
            OutlinedTextField(
                value = floatContext.userMessage,
                onValueChange = { floatContext.userMessage = it },
                placeholder = { 
                    Text(
                        text = "输入消息...",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        isInputFocused = focusState.isFocused
                    },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                maxLines = 2,
                singleLine = false,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (hasContent || floatContext.attachments.isNotEmpty()) {
                            floatContext.onSendMessage?.invoke(floatContext.userMessage, PromptFunctionType.CHAT)
                            floatContext.userMessage = ""
                            floatContext.showAttachmentPanel = false
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 附件按钮 (+)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (floatContext.showAttachmentPanel)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { viewModel.toggleAttachmentPanel() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加附件",
                    tint = if (floatContext.showAttachmentPanel)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // 发送/取消按钮
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isProcessing -> MaterialTheme.colorScheme.error
                            hasContent || floatContext.attachments.isNotEmpty() -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .clickable(
                        enabled = isProcessing || hasContent || floatContext.attachments.isNotEmpty()
                    ) {
                        when {
                            isProcessing -> {
                                // 取消当前消息处理
                                floatContext.onCancelMessage?.invoke()
                            }
                            else -> {
                                // 发送消息
                                floatContext.onSendMessage?.invoke(floatContext.userMessage, PromptFunctionType.CHAT)
                                floatContext.userMessage = ""
                                floatContext.showAttachmentPanel = false
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isProcessing) Icons.Default.Close else Icons.Default.Send,
                    contentDescription = if (isProcessing) "取消" else "发送",
                    tint = when {
                        isProcessing -> MaterialTheme.colorScheme.onError
                        hasContent || floatContext.attachments.isNotEmpty() -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** 附件面板覆盖层 */
@SuppressLint("SuspiciousIndentation")
@Composable
private fun AttachmentPanelOverlay(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel
) {
    FloatingAttachmentPanel(
        visible = floatContext.showAttachmentPanel,
        onAttachScreenContent = {
            floatContext.coroutineScope.launch {
                floatContext.onAttachmentRequest?.invoke("screen_capture")
            delay(500)
                floatContext.showAttachmentPanel = false
            }
        },
        onAttachNotifications = {
            floatContext.coroutineScope.launch {
            floatContext.onAttachmentRequest?.invoke("notifications_capture")
            delay(500)
                floatContext.showAttachmentPanel = false
            }
        },
        onAttachLocation = {
            floatContext.coroutineScope.launch {
                floatContext.onAttachmentRequest?.invoke("location_capture")
            delay(500)
                floatContext.showAttachmentPanel = false
            }
        },
        onDismiss = { floatContext.showAttachmentPanel = false }
    )
}

