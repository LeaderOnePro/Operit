package com.ai.assistance.operit.ui.floating.ui.window.screen

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import com.ai.assistance.operit.ui.features.chat.components.AttachmentChip
import com.ai.assistance.operit.ui.floating.ui.window.components.*
import com.ai.assistance.operit.ui.floating.ui.window.models.*
import com.ai.assistance.operit.ui.floating.ui.window.viewmodel.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.FloatingMode
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 渲染悬浮窗的窗口模式界面 - 简化版 */
@Composable
fun FloatingChatWindowMode(floatContext: FloatContext) {
    val viewModel = rememberFloatingChatWindowModeViewModel(floatContext)

    LaunchedEffect(
        floatContext.windowWidthState,
        floatContext.windowHeightState,
        floatContext.windowScale
    ) {
        viewModel.syncWindowState()
    }

    FloatingChatWindowContent(floatContext, viewModel)
}

/** 主窗口内容 */
@Composable
private fun FloatingChatWindowContent(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel
) {
    val density = LocalDensity.current
    val cornerRadius = 12.dp
    val borderThickness = 3.dp
    val edgeHighlightColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background

    Layout(
        content = {
            Box(modifier = Modifier.fillMaxSize()) {
                MainWindowBox(
                    floatContext = floatContext,
                    viewModel = viewModel,
                    cornerRadius = cornerRadius,
                    borderThickness = borderThickness,
                    edgeHighlightColor = edgeHighlightColor,
                    backgroundColor = backgroundColor
                )
            }
        },
        modifier = Modifier.graphicsLayer { alpha = floatContext.animatedAlpha.value }
    ) { measurables, _ ->
        val widthInPx = with(density) { viewModel.windowState.width.toPx() }
        val heightInPx = with(density) { viewModel.windowState.height.toPx() }
        val scale = viewModel.windowState.scale

        val placeable = measurables.first().measure(
            androidx.compose.ui.unit.Constraints.fixed(
                width = widthInPx.roundToInt(),
                height = heightInPx.roundToInt()
            )
        )

        layout(
            width = (widthInPx * scale).roundToInt(),
            height = (heightInPx * scale).roundToInt()
        ) {
            placeable.placeRelativeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
                alpha = floatContext.animatedAlpha.value
            }
        }
    }
}

/** 主窗口盒子 */
@Composable
private fun MainWindowBox(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel,
    cornerRadius: Dp,
    borderThickness: Dp,
    edgeHighlightColor: Color,
    backgroundColor: Color
) {
    var isResizingHeight by remember { mutableStateOf(false) }
    var isScaling by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
                        .fillMaxSize()
                        .shadow(8.dp, RoundedCornerShape(cornerRadius))
                        .border(
                            width = borderThickness,
                color = if (floatContext.isEdgeResizing || isResizingHeight || isScaling) edgeHighlightColor else Color.Transparent,
                            shape = RoundedCornerShape(cornerRadius)
                        )
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(backgroundColor)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
            CloseButtonEffect(floatContext, viewModel)
            TitleBar(floatContext, viewModel)
            ChatContentArea(floatContext, viewModel)
            FloatingChatWindowInputControls(floatContext, viewModel)
        }

        // 底部高度调整分隔线
        BottomResizeHandle(
            floatContext = floatContext,
            viewModel = viewModel,
            onResizingChange = { isResizingHeight = it }
        )

        // 左右边框缩放手柄
        if (!floatContext.showInputDialog) {
            RightEdgeScaleHandle(
                floatContext = floatContext,
                viewModel = viewModel,
                onScalingChange = { isScaling = it }
            )
        }

        // 四个角落的缩放手柄
        if (!floatContext.showInputDialog) {
            CornerScaleHandle(
                floatContext = floatContext,
                viewModel = viewModel,
                alignment = Alignment.BottomEnd,
                onScalingChange = { isScaling = it }
            )
        }
    }
}

/** 关闭按钮效果 */
@Composable
private fun CloseButtonEffect(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel
) {
                        LaunchedEffect(viewModel.closeButtonPressed) {
                            if (viewModel.closeButtonPressed) {
            floatContext.animatedAlpha.animateTo(0f, animationSpec = tween(200))
                                floatContext.onClose()
            viewModel.closeButtonPressed = false
        }
    }
}

/** 标题栏 */
@Composable
private fun TitleBar(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (viewModel.titleBarHover) 0.3f else 0.2f
                                        )
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                        viewModel.titleBarHover = event.changes.any { it.pressed }
                    }
                }
            }
    ) {
        if (floatContext.contentVisible) {
            // 左侧按钮组
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TitleBarButton(
                    icon = Icons.Default.Fullscreen,
                    description = "全屏",
                    onClick = { floatContext.onModeChange(FloatingMode.FULLSCREEN) }
                )
                MinimizeButton(viewModel, primaryColor) {
                    floatContext.onModeChange(FloatingMode.BALL)
                }
            }
            
            // 中间可拖动区域
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 80.dp, end = 50.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = { viewModel.startDragging() },
                                        onDragEnd = {
                                            viewModel.endDragging()
                                            floatContext.saveWindowState?.invoke()
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                viewModel.handleMove(dragAmount.x, dragAmount.y)
                            }
                        )
                    }
            )
            
            // 右侧关闭按钮
            CloseButton(
                viewModel = viewModel,
                errorColor = errorColor,
                onSurfaceVariantColor = onSurfaceVariantColor,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                viewModel.closeButtonPressed = true
            }
        }
    }
}

/** 标题栏按钮 */
@Composable
private fun TitleBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
                                        IconButton(
        onClick = onClick,
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
    }
}

/** 最小化按钮 */
@Composable
private fun MinimizeButton(
    viewModel: FloatingChatWindowModeViewModel,
    primaryColor: Color,
    onClick: () -> Unit
) {
                                        IconButton(
        onClick = onClick,
        modifier = Modifier
                                                .size(30.dp)
                                                .background(
                color = if (viewModel.minimizeHover) primaryColor.copy(alpha = 0.1f) else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .pointerInput(Unit) {
                                                    awaitPointerEventScope {
                                                        while (true) {
                        val event = awaitPointerEvent()
                        viewModel.minimizeHover = event.changes.any { it.pressed }
                                                        }
                                                    }
                                                }
                                        ) {
                                            Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "最小化",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
    }
}

/** 关闭按钮 */
@Composable
private fun CloseButton(
    viewModel: FloatingChatWindowModeViewModel,
    errorColor: Color,
    onSurfaceVariantColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
                                        IconButton(
        onClick = onClick,
        modifier = modifier
                                                .size(30.dp)
                                                .background(
                color = if (viewModel.closeHover) errorColor.copy(alpha = 0.1f) else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .pointerInput(Unit) {
                                                    awaitPointerEventScope {
                                                        while (true) {
                        val event = awaitPointerEvent()
                        viewModel.closeHover = event.changes.any { it.pressed }
                                                        }
                                                    }
                                                }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "关闭",
            tint = if (viewModel.closeHover) errorColor else onSurfaceVariantColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

/** 聊天内容区域 */
@Composable
private fun ColumnScope.ChatContentArea(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        if (!floatContext.showInputDialog) {
            ChatMessagesView(floatContext, viewModel)
        } else {
            InputDialogView(floatContext, viewModel)
        }
    }
}

/** 聊天消息视图 */
@SuppressLint("SuspiciousIndentation")
@Composable
private fun ChatMessagesView(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel
) {
                                val scrollState = rememberScrollState()
    val userMessageColor = MaterialTheme.colorScheme.primaryContainer
    val aiMessageColor = MaterialTheme.colorScheme.surface
    val userTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val aiTextColor = MaterialTheme.colorScheme.onSurface
    val systemMessageColor = MaterialTheme.colorScheme.surfaceVariant
    val systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val thinkingBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val thinkingTextColor = MaterialTheme.colorScheme.onSurfaceVariant

                                LaunchedEffect(floatContext.messages.size) {
        val lastAiMessage = floatContext.messages.lastOrNull { it.sender == "ai" }
                                    val stream = lastAiMessage?.contentStream
                                    if (stream != null) {
                                        launch {
                                            try {
                                                stream.collect { _ ->
                                                    viewModel.incrementStreamUpdateTrigger()
                                                }
                                            } catch (e: Exception) {
                    Log.e("FloatingChatWindow", "Stream collection error", e)
                }
            }
        }
    }

                                LaunchedEffect(floatContext.messages.size, viewModel.streamUpdateTrigger) {
                                    if (floatContext.messages.isNotEmpty()) {
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                }

    val isLoading = floatContext.messages.lastOrNull()?.sender == "think"

                                Column(
        modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp)
                                ) {
                                    floatContext.messages.forEachIndexed { index, message ->
                                        if (message.sender != "think") {
                                            key(message.timestamp) {
                                                MessageItem(
                                                    index = index,
                                                    message = message,
                                                    allMessages = floatContext.messages,
                                                    userMessageColor = userMessageColor,
                                                    aiMessageColor = aiMessageColor,
                                                    userTextColor = userTextColor,
                                                    aiTextColor = aiTextColor,
                                                    systemMessageColor = systemMessageColor,
                                                    systemTextColor = systemTextColor,
                        thinkingBackgroundColor = thinkingBackgroundColor,
                                                    thinkingTextColor = thinkingTextColor,
                                                    onSelectMessageToEdit = null,
                                                    onCopyMessage = null
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }

        val lastMessage = floatContext.messages.lastOrNull { it.sender != "think" }
        if (isLoading && (lastMessage?.sender == "user" || (lastMessage?.sender == "ai" && lastMessage.content.isBlank()))) {
                                        Column(
                modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 0.dp)
                                        ) {
                                            Box(modifier = Modifier.padding(start = 16.dp)) {
                    LoadingDotsIndicator(MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/** 输入对话框视图 */
@Composable
private fun InputDialogView(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel
) {
    Column(
        modifier = Modifier
                                    .fillMaxSize()
            .padding(16.dp)
    ) {
                                    Spacer(modifier = Modifier.height(32.dp))

        InputDialogHeader(viewModel)
        Spacer(modifier = Modifier.height(8.dp))

        if (floatContext.attachments.isNotEmpty()) {
            AttachmentsList(floatContext)
            Spacer(modifier = Modifier.height(8.dp))
        }

        InputTextField(floatContext, viewModel)
    }
}

/** 输入对话框头部 */
@Composable
private fun InputDialogHeader(viewModel: FloatingChatWindowModeViewModel) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.send_message),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
        IconButton(onClick = { viewModel.hideInputDialog() }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "关闭",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 附件列表 */
@Composable
private fun AttachmentsList(floatContext: FloatContext) {
    Row(
        modifier = Modifier
                                                .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
        floatContext.attachments.forEach { attachment ->
                                                AttachmentChip(
                                                    attachmentInfo = attachment,
                onRemove = { floatContext.onRemoveAttachment?.invoke(attachment.filePath) },
                onInsert = { }
            )
        }
    }
}

/** 输入文本框 */
@Composable
private fun ColumnScope.InputTextField(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel
) {
                                    val focusRequester = remember { FocusRequester() }

                                    // 检测 AI 是否正在处理消息 - 使用 chatService 的 isLoading 状态
                                    val isProcessing = floatContext.chatService?.getChatCore()?.isLoading?.collectAsState()?.value ?: false

                                    DisposableEffect(floatContext.showInputDialog) {
                                        if (floatContext.showInputDialog) {
                                            floatContext.coroutineScope.launch {
                                                delay(300)
                                                try {
                                                    focusRequester.requestFocus()
                                                } catch (e: Exception) {
                    Log.e("FloatingChatWindow", "Failed to request focus", e)
                }
            }
        }
                                        onDispose {}
                                    }

    Box(
        modifier = Modifier
                                        .fillMaxSize()
            .weight(1f)
    ) {
                                        OutlinedTextField(
                                            value = floatContext.userMessage,
                                            onValueChange = { floatContext.userMessage = it },
                                            placeholder = { Text("请输入您的问题...") },
            modifier = Modifier
                                                .fillMaxSize()
                                                .focusRequester(focusRequester),
                                            textStyle = TextStyle.Default,
            maxLines = Int.MAX_VALUE,
            keyboardOptions = KeyboardOptions(
                                                imeAction = ImeAction.Send,
                                                autoCorrect = true
                                            ),
            keyboardActions = KeyboardActions(
                                                onSend = {
                    if (floatContext.userMessage.isNotBlank() || floatContext.attachments.isNotEmpty()) {
                        floatContext.onSendMessage?.invoke(
                            floatContext.userMessage,
                            PromptFunctionType.CHAT
                        )
                        floatContext.userMessage = ""
                        floatContext.showInputDialog = false
                        floatContext.showAttachmentPanel = false
                    }
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        )

                                        // 发送/取消按钮
                                        FloatingActionButton(
                                            onClick = {
                                                when {
                                                    isProcessing -> {
                                                        // 取消当前消息处理
                                                        floatContext.onCancelMessage?.invoke()
                                                    }
                                                    floatContext.userMessage.isNotBlank() || floatContext.attachments.isNotEmpty() -> {
                                                        // 发送消息
                                                        floatContext.onSendMessage?.invoke(
                                                            floatContext.userMessage,
                                                            PromptFunctionType.CHAT
                                                        )
                                                        floatContext.userMessage = ""
                                                        floatContext.showInputDialog = false
                                                        floatContext.showAttachmentPanel = false
                                                    }
                                                }
                                            },
            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp)
                                                .size(46.dp),
                                            containerColor = if (isProcessing) 
                                                MaterialTheme.colorScheme.error 
                                            else 
                                                MaterialTheme.colorScheme.primary
                                        ) {
                                            Icon(
                                                imageVector = if (isProcessing) Icons.Default.Close else Icons.Default.Send,
                                                contentDescription = if (isProcessing) "取消" else "发送",
                                                tint = if (isProcessing)
                                                    MaterialTheme.colorScheme.onError
                                                else
                                                    MaterialTheme.colorScheme.onPrimary
                                            )
                                        }
                                    }
                                }

/** 右边框缩放手柄 */
@Composable
private fun BoxScope.RightEdgeScaleHandle(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel,
    onScalingChange: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    var dragStartScale by remember { mutableStateOf(1f) }
    var baseWidthInPx by remember { mutableStateOf(0f) }
    var totalDragX by remember { mutableStateOf(0f) }
    var isHovering by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(16.dp)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                        isHovering = event.changes.any { it.pressed }
                                    }
                                }
                            }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                    onDragStart = {
                                        viewModel.startDragging()
                                        onScalingChange(true)
                                        dragStartScale = viewModel.windowState.scale
                                        baseWidthInPx = with(density) { viewModel.windowState.width.toPx() }
                                        totalDragX = 0f
                                    },
                                    onDragEnd = {
                                        viewModel.endDragging()
                                        onScalingChange(false)
                                    },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                        totalDragX += dragAmount.x

                                        if (baseWidthInPx > 0) {
                                            val scaleDelta = totalDragX / baseWidthInPx
                                            val newScale = dragStartScale + scaleDelta
                                        viewModel.handleScaleChange(newScale)
                                        }
                                    }
                                )
                            }
    ) {
        // 高亮显示
        if (isHovering) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .align(Alignment.CenterEnd)
                    .background(primaryColor.copy(alpha = 0.6f))
            )
        }
    }
}

/** 角落缩放手柄 */
@Composable
private fun BoxScope.CornerScaleHandle(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel,
    alignment: Alignment,
    onScalingChange: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    var dragStartScale by remember { mutableStateOf(1f) }
    var baseWidthInPx by remember { mutableStateOf(0f) }
    var baseHeightInPx by remember { mutableStateOf(0f) }
    var totalDragX by remember { mutableStateOf(0f) }
    var totalDragY by remember { mutableStateOf(0f) }
    var isHovering by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .align(alignment)
            .size(24.dp)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                        isHovering = event.changes.any { it.pressed }
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                    onDragStart = {
                        viewModel.startDragging()
                        onScalingChange(true)
                        dragStartScale = viewModel.windowState.scale
                        baseWidthInPx = with(density) { viewModel.windowState.width.toPx() }
                        baseHeightInPx = with(density) { viewModel.windowState.height.toPx() }
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDragEnd = {
                        viewModel.endDragging()
                        onScalingChange(false)
                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        val alignmentFactorX = if (alignment == Alignment.TopStart || alignment == Alignment.BottomStart) -1 else 1
                        val alignmentFactorY = if (alignment == Alignment.TopStart || alignment == Alignment.TopEnd) -1 else 1

                        val effectiveDragX = totalDragX * alignmentFactorX
                        val effectiveDragY = totalDragY * alignmentFactorY

                        val scaleDelta = if (kotlin.math.abs(effectiveDragX) > kotlin.math.abs(effectiveDragY)) {
                            if (baseWidthInPx > 0) effectiveDragX / baseWidthInPx else 0f
                        } else {
                            if (baseHeightInPx > 0) effectiveDragY / baseHeightInPx else 0f
                        }
                        val newScale = dragStartScale + scaleDelta
                        viewModel.handleScaleChange(newScale)
                    }
                )
            }
    ) {
        // 高亮显示
        if (isHovering) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.Center)
                    .background(primaryColor.copy(alpha = 0.6f), CircleShape)
            )
        }
    }
}

/** 底部高度调整手柄 */
@Composable
private fun BoxScope.BottomResizeHandle(
    floatContext: FloatContext,
    viewModel: FloatingChatWindowModeViewModel,
    onResizingChange: (Boolean) -> Unit
) {
    val density = LocalDensity.current
    var isHovering by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(12.dp)
                        .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                        isHovering = event.changes.any { it.pressed }
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                    onDragStart = {
                        viewModel.startEdgeResize()
                        onResizingChange(true)
                    },
                    onDragEnd = {
                        viewModel.endEdgeResize()
                        onResizingChange(false)
                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                        // 直接使用拖动距离，不需要除以 scale
                        val heightDelta = with(density) { dragAmount.y.toDp() }
                        val newHeight = viewModel.windowState.height + heightDelta
                        viewModel.handleResize(viewModel.windowState.width, newHeight)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // 拖动手柄的视觉表示（三条横线）
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(6.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lineColor = if (isHovering || floatContext.isEdgeResizing) {
                    primaryColor.copy(alpha = 0.8f)
                } else {
                    primaryColor.copy(alpha = 0.4f)
                }
                val lineSpacing = size.height / 4

                // 绘制三条横线
                            drawLine(
                                color = lineColor,
                    start = Offset(0f, lineSpacing),
                    end = Offset(size.width, lineSpacing),
                    strokeWidth = 2f
                            )
                            drawLine(
                                color = lineColor,
                    start = Offset(0f, lineSpacing * 2),
                    end = Offset(size.width, lineSpacing * 2),
                    strokeWidth = 2f
                )
                drawLine(
                    color = lineColor,
                    start = Offset(0f, lineSpacing * 3),
                    end = Offset(size.width, lineSpacing * 3),
                    strokeWidth = 2f
                )
            }
        }
    }
}
