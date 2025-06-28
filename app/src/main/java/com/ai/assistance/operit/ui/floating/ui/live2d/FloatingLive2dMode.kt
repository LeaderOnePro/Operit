package com.ai.assistance.operit.ui.floating.ui.live2d

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.preferences.PromptFunctionType
import com.ai.assistance.operit.data.repository.Live2DRepository
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.floating.ui.window.ResizeEdge
import com.chatwaifu.live2d.JniBridgeJava
import com.chatwaifu.live2d.Live2DViewCompose

// A data class to hold the UI state that changes during drag
private data class DraggableWindowState(
        val width: Dp,
        val height: Dp,
        val scale: Float,
)

@Composable
fun FloatingLive2dMode(floatContext: FloatContext) {
    var isLocked by remember { mutableStateOf(false) }
    var showPetChatInput by remember { mutableStateOf(false) }
    val latestMessage = floatContext.messages.lastOrNull { it.sender == "ai" }
    var showPetChatBubble by remember { mutableStateOf(false) }
    var closeBubbleManually by remember { mutableStateOf(false) }

    LaunchedEffect(latestMessage) {
        if (latestMessage != null) {
            closeBubbleManually = false
            showPetChatBubble = true
        }
    }

    val cornerRadius = 12.dp
    val borderThickness = 3.dp
    val edgeHighlightColor = MaterialTheme.colorScheme.primary
    val backgroundColor = Color.Transparent // 透明背景

    var windowState by remember {
        mutableStateOf(
                DraggableWindowState(
                        width = floatContext.windowWidthState,
                        height = floatContext.windowHeightState,
                        scale = floatContext.windowScale
                )
        )
    }

    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(
            floatContext.windowWidthState,
            floatContext.windowHeightState,
            floatContext.windowScale
    ) {
        if (!isDragging) {
            windowState =
                    windowState.copy(
                            width = floatContext.windowWidthState,
                            height = floatContext.windowHeightState,
                            scale = floatContext.windowScale
                    )
        }
    }

    val context = LocalContext.current
    val repository = remember { Live2DRepository.getInstance(context) }
    val models by repository.models.collectAsState()
    val currentConfig by repository.currentConfig.collectAsState()
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
            modifier =
                    Modifier.width(windowState.width).height(windowState.height).graphicsLayer {
                        scaleX = windowState.scale
                        scaleY = windowState.scale
                        transformOrigin = TransformOrigin(0f, 0f)
                        alpha = floatContext.animatedAlpha.value
                    }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                    modifier =
                            Modifier.fillMaxSize()
                                    .shadow(
                                            if (isLocked) 0.dp else 8.dp,
                                            RoundedCornerShape(cornerRadius)
                                    )
                                    .border(
                                            width = if (isLocked) 0.dp else borderThickness,
                                            color =
                                                    if (floatContext.isEdgeResizing)
                                                            edgeHighlightColor
                                                    else Color.Transparent,
                                            shape = RoundedCornerShape(cornerRadius)
                                    )
                                    .clip(RoundedCornerShape(cornerRadius))
                                    .background(backgroundColor)
                                    .onSizeChanged {}
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(visible = !isLocked, enter = fadeIn(), exit = fadeOut()) {
                        val titleBarHover = remember { mutableStateOf(false) }
                        val closeButtonPressed = remember { mutableStateOf(false) }

                        LaunchedEffect(closeButtonPressed.value) {
                            if (closeButtonPressed.value) {
                                floatContext.animatedAlpha.animateTo(0f, animationSpec = tween(200))
                                floatContext.onClose()
                                closeButtonPressed.value = false
                            }
                        }

                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .height(48.dp)
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                                .copy(
                                                                        alpha =
                                                                                if (titleBarHover
                                                                                                .value
                                                                                )
                                                                                        0.3f
                                                                                else 0.2f
                                                                )
                                                )
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                                .pointerInput(Unit) {
                                                    awaitPointerEventScope {
                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            titleBarHover.value =
                                                                    event.changes.any { it.pressed }
                                                        }
                                                    }
                                                }
                                                .pointerInput(Unit) {
                                                    detectDragGestures(
                                                            onDragStart = { isDragging = true },
                                                            onDragEnd = {
                                                                isDragging = false
                                                                floatContext.saveWindowState
                                                                        ?.invoke()
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                floatContext.onMove(
                                                                        dragAmount.x,
                                                                        dragAmount.y,
                                                                        windowState.scale
                                                                )
                                                            }
                                                    )
                                                }
                        ) {
                            Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                            onClick = { isLocked = true },
                                            modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "锁定",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                            onClick = {
                                                floatContext.onModeChange(FloatingMode.WINDOW)
                                            },
                                            modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                                imageVector = Icons.Default.Chat,
                                                contentDescription = "聊天模式",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Text(
                                        text = "Live2D 模式",
                                        style =
                                                MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Medium
                                                ),
                                        color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                            onClick = {
                                                floatContext.onModeChange(FloatingMode.FULLSCREEN)
                                            },
                                            modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                                imageVector = Icons.Default.Fullscreen,
                                                contentDescription = "全屏",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    val minimizeHover = remember { mutableStateOf(false) }

                                    IconButton(
                                            onClick = {
                                                floatContext.onModeChange(FloatingMode.BALL)
                                            },
                                            modifier =
                                                    Modifier.size(30.dp)
                                                            .background(
                                                                    color =
                                                                            if (minimizeHover.value)
                                                                                    primaryColor
                                                                                            .copy(
                                                                                                    alpha =
                                                                                                            0.1f
                                                                                            )
                                                                            else Color.Transparent,
                                                                    shape = CircleShape
                                                            )
                                                            .pointerInput(Unit) {
                                                                awaitPointerEventScope {
                                                                    while (true) {
                                                                        val event =
                                                                                awaitPointerEvent()
                                                                        minimizeHover.value =
                                                                                event.changes.any {
                                                                                    it.pressed
                                                                                }
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

                                    val closeHover = remember { mutableStateOf(false) }

                                    IconButton(
                                            onClick = { closeButtonPressed.value = true },
                                            modifier =
                                                    Modifier.size(30.dp)
                                                            .background(
                                                                    color =
                                                                            if (closeHover.value)
                                                                                    errorColor.copy(
                                                                                            alpha =
                                                                                                    0.1f
                                                                                    )
                                                                            else Color.Transparent,
                                                                    shape = CircleShape
                                                            )
                                                            .pointerInput(Unit) {
                                                                awaitPointerEventScope {
                                                                    while (true) {
                                                                        val event =
                                                                                awaitPointerEvent()
                                                                        closeHover.value =
                                                                                event.changes.any {
                                                                                    it.pressed
                                                                                }
                                                                    }
                                                                }
                                                            }
                                    ) {
                                        Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "关闭",
                                                tint =
                                                        if (closeHover.value) errorColor
                                                        else onSurfaceVariantColor,
                                                modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(
                            modifier = Modifier.fillMaxSize().weight(1f),
                            contentAlignment = Alignment.Center
                    ) {
                        if (JniBridgeJava.isLibraryLoaded()) {
                            if (models.isEmpty()) {
                                Text(
                                        "没有可用的Live2D模型",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                )
                            } else if (errorMessage != null) {
                                Text(
                                        text = "加载失败: $errorMessage",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                val currentModel = models.find { it.id == currentConfig?.modelId }
                                if (currentModel != null && currentConfig != null) {
                                    Live2DViewCompose(
                                            modifier = Modifier.fillMaxSize(),
                                            model = currentModel,
                                            config = currentConfig?.copy(renderBack = false)
                                                            ?: currentConfig,
                                            onError = { error -> errorMessage = "加载失败: $error" }
                                    )
                                } else {
                                    Text(
                                            "未选择模型",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        } else {
                            Text(
                                    "Live2D库未正确加载",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        // 宠物对话气泡
                        Box(
                                modifier =
                                        Modifier.align(Alignment.CenterStart)
                                                .padding(start = 24.dp, bottom = 120.dp),
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                    visible =
                                            showPetChatBubble &&
                                                    latestMessage != null &&
                                                    !closeBubbleManually,
                                    enter = fadeIn(animationSpec = tween(300)),
                                    exit = fadeOut(animationSpec = tween(300))
                            ) {
                                Box(modifier = Modifier.clickable { closeBubbleManually = true }) {
                                    // 使用ChatMessage参数的PetChatBubble版本
                                    latestMessage?.let {
                                        PetChatBubble(
                                                chatMessage = it,
                                                onClose = { closeBubbleManually = true }
                                        )
                                    }
                                }
                            }
                        }

                        if (!isLocked) {
                            val scaleButtonHover = remember { mutableStateOf(false) }

                            Box(
                                    modifier =
                                            Modifier.size(48.dp)
                                                    .padding(6.dp)
                                                    .align(Alignment.BottomEnd)
                                                    .offset(x = (-8).dp, y = (-8).dp)
                                                    .background(
                                                            color =
                                                                    if (scaleButtonHover.value)
                                                                            primaryColor.copy(
                                                                                    alpha = 0.1f
                                                                            )
                                                                    else Color.Transparent,
                                                            shape = CircleShape
                                                    )
                                                    .pointerInput(Unit) {
                                                        awaitPointerEventScope {
                                                            while (true) {
                                                                val event = awaitPointerEvent()
                                                                scaleButtonHover.value =
                                                                        event.changes.any {
                                                                            it.pressed
                                                                        }
                                                            }
                                                        }
                                                    }
                                                    .pointerInput(Unit) {
                                                        detectDragGestures(
                                                                onDragStart = { isDragging = true },
                                                                onDragEnd = { isDragging = false },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    val scaleDelta =
                                                                            dragAmount.y * 0.001f
                                                                    val newScale =
                                                                            (windowState.scale +
                                                                                            scaleDelta)
                                                                                    .coerceIn(
                                                                                            0.5f,
                                                                                            1.0f
                                                                                    )

                                                                    windowState =
                                                                            windowState.copy(
                                                                                    scale = newScale
                                                                            )
                                                                    floatContext.onScaleChange(
                                                                            newScale
                                                                    )
                                                                }
                                                        )
                                                    }
                                                    .pointerInput(Unit) {
                                                        detectTapGestures {
                                                            val newScale =
                                                                    when {
                                                                        windowState.scale > 0.8f ->
                                                                                0.7f
                                                                        windowState.scale > 0.7f ->
                                                                                0.9f
                                                                        else -> 1.0f
                                                                    }
                                                            windowState =
                                                                    windowState.copy(
                                                                            scale = newScale
                                                                    )
                                                            floatContext.onScaleChange(newScale)
                                                        }
                                                    }
                            ) {
                                val lineColor =
                                        if (scaleButtonHover.value) primaryColor.copy(alpha = 1.0f)
                                        else primaryColor.copy(alpha = 0.7f)

                                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                    drawLine(
                                            color = lineColor,
                                            start = Offset(size.width * 0.2f, size.height * 0.8f),
                                            end = Offset(size.width * 0.8f, size.height * 0.2f),
                                            strokeWidth = 3.5f
                                    )
                                    drawLine(
                                            color = lineColor,
                                            start = Offset(size.width * 0.5f, size.height * 0.8f),
                                            end = Offset(size.width * 0.8f, size.height * 0.5f),
                                            strokeWidth = 3.5f
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!isLocked) {
                Box(
                        modifier =
                                Modifier.size(25.dp).align(Alignment.BottomEnd).pointerInput(Unit) {
                                    detectDragGestures(
                                            onDragStart = {
                                                isDragging = true
                                                floatContext.isEdgeResizing = true
                                            },
                                            onDragEnd = {
                                                isDragging = false
                                                floatContext.isEdgeResizing = false
                                                floatContext.saveWindowState?.invoke()
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val density = floatContext.density
                                                val newWidth =
                                                        (windowState.width +
                                                                        with(density) {
                                                                            dragAmount.x.toDp()
                                                                        })
                                                                .coerceAtLeast(150.dp)
                                                val newHeight =
                                                        (windowState.height +
                                                                        with(density) {
                                                                            dragAmount.y.toDp()
                                                                        })
                                                                .coerceAtLeast(200.dp)

                                                windowState =
                                                        windowState.copy(
                                                                width = newWidth,
                                                                height = newHeight
                                                        )
                                                floatContext.onResize(newWidth, newHeight)
                                            }
                                    )
                                }
                )

                if (floatContext.isEdgeResizing &&
                                floatContext.activeEdge == ResizeEdge.BOTTOM_RIGHT
                ) {
                    Box(
                            modifier =
                                    Modifier.size(8.dp)
                                            .background(
                                                    color = edgeHighlightColor,
                                                    shape = CircleShape
                                            )
                                            .align(Alignment.BottomEnd)
                    )
                }
            }
        }

        AnimatedVisibility(visible = isLocked, enter = fadeIn(), exit = fadeOut()) {
            Box(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    contentAlignment = Alignment.TopStart
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 解锁按钮
                    IconButton(onClick = { isLocked = false }) {
                        Icon(
                                Icons.Default.LockOpen,
                                contentDescription = "解锁",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(24.dp)
                        )
                    }
                    // 对话按钮
                    IconButton(
                            onClick = {
                                floatContext.onInputFocusRequest?.invoke(true)
                                showPetChatInput = true
                            }
                    ) {
                        Icon(
                                Icons.Default.Chat,
                                contentDescription = "宠物对话",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        if (showPetChatInput) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                PetChatInputDialog(
                        onDismiss = {
                            showPetChatInput = false
                            // 释放输入法焦点
                            floatContext.onInputFocusRequest?.invoke(false)
                        },
                        onSendMessage = { message ->
                            if (message.isNotBlank()) {
                                floatContext.onSendMessage?.invoke(message, PromptFunctionType.CHAT)
                                showPetChatInput = false
                                // 释放输入法焦点
                                floatContext.onInputFocusRequest?.invoke(false)
                            }
                        }
                )
            }
        }
    }
}
