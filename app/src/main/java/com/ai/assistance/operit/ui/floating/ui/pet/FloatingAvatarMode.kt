package com.ai.assistance.operit.ui.floating.ui.pet

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.core.avatar.common.view.AvatarView
import com.ai.assistance.operit.core.avatar.impl.factory.AvatarControllerFactoryImpl
import com.ai.assistance.operit.core.avatar.impl.factory.AvatarModelFactoryImpl
import com.ai.assistance.operit.core.avatar.impl.factory.AvatarRendererFactoryImpl
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.repository.AvatarRepository
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.floating.ui.window.ResizeEdge
import kotlin.math.roundToInt

// A data class to hold the UI state that changes during drag
private data class DraggableWindowState(
    val width: Dp,
    val height: Dp,
    val scale: Float,
)

@Composable
fun FloatingAvatarMode(floatContext: FloatContext) {
    val isLocked by floatContext.windowState?.isPetModeLocked ?: remember { mutableStateOf(false) }

    var showPetChatInput by remember { mutableStateOf(false) }
    val latestMessage = floatContext.messages.lastOrNull { it.sender == "ai" }
    var showPetChatBubble by remember { mutableStateOf(false) }
    var closeBubbleManually by remember { mutableStateOf(false) }
    
    // 用于触发表情更新的状态
    var emotionTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(latestMessage) {
        if (latestMessage != null) {
            closeBubbleManually = false
            showPetChatBubble = true
        }
    }
    
    // 监听最新消息的流完成事件
    LaunchedEffect(latestMessage?.contentStream) {
        latestMessage?.contentStream?.let { stream ->
            Log.d("FloatingAvatarMode", "开始监听消息流...")
            try {
                // 收集整个流（但不处理内容，只等待完成）
                val contentBuilder = StringBuilder()
                stream.collect { chunk ->
                    contentBuilder.append(chunk)
                }
                // 流完成后，分析完整内容并更新表情
                val finalContent = contentBuilder.toString()
                Log.d("FloatingAvatarMode", "消息流完成，最终内容长度: ${finalContent.length}")
                Log.d("FloatingAvatarMode", "内容预览: ${finalContent.take(100)}")
                
                val emotion = AvatarEmotionManager.analyzeEmotion(finalContent)
                Log.d("FloatingAvatarMode", "分析出的情感: $emotion")
                
                // 更新触发器，让下面的LaunchedEffect更新表情
                emotionTrigger++
            } catch (e: Exception) {
                Log.e("FloatingAvatarMode", "监听消息流时出错", e)
            }
        }
    }

    val cornerRadius = 12.dp
    val backgroundColor = Color.Transparent // 透明背景

    var windowState by remember {
        mutableStateOf<DraggableWindowState>(
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
    val modelFactory = remember { AvatarModelFactoryImpl() }
    val repository = remember { AvatarRepository.getInstance(context, modelFactory) }
    val currentAvatar by repository.currentAvatar.collectAsState()
    val instanceSettings by repository.instanceSettings.collectAsState()
    
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    val rendererFactory = remember { AvatarRendererFactoryImpl() }
    val controllerFactory = remember { AvatarControllerFactoryImpl() }

    val density = LocalDensity.current
    Layout(
            content = {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                            modifier =
                                    Modifier.fillMaxSize()
                                            .clip(RoundedCornerShape(cornerRadius))
                                            .background(backgroundColor)
                                            .onSizeChanged {}
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            AnimatedVisibility(
                                    visible = !isLocked,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                            ) {
                                val titleBarHover = remember { mutableStateOf(false) }
                                val closeButtonPressed = remember { mutableStateOf(false) }

                                LaunchedEffect(closeButtonPressed.value) {
                                    if (closeButtonPressed.value) {
                                        floatContext.animatedAlpha.animateTo(
                                                0f,
                                                animationSpec = tween(200)
                                        )
                                        floatContext.onClose()
                                        closeButtonPressed.value = false
                                    }
                                }

                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .height(48.dp)
                                                        .background(
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant.copy(
                                                                        alpha =
                                                                                if (titleBarHover
                                                                                                .value
                                                                                )
                                                                                        0.3f
                                                                                else 0.2f
                                                                )
                                                        )
                                                        .padding(
                                                                horizontal = 16.dp,
                                                                vertical = 8.dp
                                                        )
                                                        .pointerInput(Unit) {
                                                            awaitPointerEventScope {
                                                                while (true) {
                                                                    val event = awaitPointerEvent()
                                                                    titleBarHover.value =
                                                                            event.changes.any {
                                                                                it.pressed
                                                                            }
                                                                }
                                                            }
                                                        }
                                                        .pointerInput(Unit) {
                                                            detectDragGestures(
                                                                    onDragStart = {
                                                                        isDragging = true
                                                                    },
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
                                                    onClick = { floatContext.windowState?.isPetModeLocked?.value = true },
                                                    modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                        imageVector = Icons.Default.Lock,
                                                        contentDescription = "锁定",
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            IconButton(
                                                    onClick = {
                                                        floatContext.onModeChange(
                                                                FloatingMode.WINDOW
                                                        )
                                                    },
                                                    modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                        imageVector = Icons.Default.Chat,
                                                        contentDescription = "聊天模式",
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Text(
                                                text = "虚拟形象模式",
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
                                                        floatContext.onModeChange(
                                                                FloatingMode.FULLSCREEN
                                                        )
                                                    },
                                                    modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                        imageVector = Icons.Default.Fullscreen,
                                                        contentDescription = "全屏",
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
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
                                                                                    if (minimizeHover
                                                                                                    .value
                                                                                    )
                                                                                            primaryColor
                                                                                                    .copy(
                                                                                                            alpha =
                                                                                                                    0.1f
                                                                                                    )
                                                                                    else
                                                                                            Color.Transparent,
                                                                            shape = CircleShape
                                                                    )
                                                                    .pointerInput(Unit) {
                                                                        awaitPointerEventScope {
                                                                            while (true) {
                                                                                val event =
                                                                                        awaitPointerEvent()
                                                                                minimizeHover
                                                                                        .value =
                                                                                        event.changes
                                                                                                .any {
                                                                                                    it.pressed
                                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                            ) {
                                                Icon(
                                                        imageVector =
                                                                Icons.Default.KeyboardArrowDown,
                                                        contentDescription = "最小化",
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
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
                                                                                    if (closeHover
                                                                                                    .value
                                                                                    )
                                                                                            errorColor
                                                                                                    .copy(
                                                                                                            alpha =
                                                                                                                    0.1f
                                                                                                    )
                                                                                    else
                                                                                            Color.Transparent,
                                                                            shape = CircleShape
                                                                    )
                                                                    .pointerInput(Unit) {
                                                                        awaitPointerEventScope {
                                                                            while (true) {
                                                                                val event =
                                                                                        awaitPointerEvent()
                                                                                closeHover.value =
                                                                                        event.changes
                                                                                                .any {
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
                                if (currentAvatar == null) {
                                    Text(
                                            "没有可用的虚拟形象",
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
                                    currentAvatar?.let { avatarModel ->
                                        val controller = controllerFactory.createController(model = avatarModel)
                                        
                                        if (controller != null) {
                                            val currentSettings = instanceSettings[avatarModel.id]
                                            
                                            LaunchedEffect(currentSettings) {
                                                 currentSettings?.let {
                                                    controller.updateSettings(mapOf(
                                                        "scale" to it.scale,
                                                        "translateX" to it.translateX,
                                                        "translateY" to it.translateY
                                                    ))
                                                 }
                                            }
                                            
                                            // 监听情感触发器并更新Avatar表情
                                            LaunchedEffect(emotionTrigger) {
                                                if (emotionTrigger > 0 && latestMessage != null) {
                                                    val emotion = AvatarEmotionManager.analyzeEmotion(latestMessage.content)
                                                    Log.d("FloatingAvatarMode", "更新Avatar表情为: $emotion")
                                                    controller.setEmotion(emotion)
                                                }
                                            }
                                            
                                            AvatarView(
                                                modifier = Modifier.fillMaxSize(),
                                                model = avatarModel,
                                                controller = controller,
                                                rendererFactory = rendererFactory,
                                                onError = { error -> errorMessage = "加载失败: $error" }
                                            )
                                        } else {
                                             Text(
                                                "不支持的虚拟形象类型: ${avatarModel.type}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                    }
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
                                        Box(
                                                modifier =
                                                        Modifier.clickable {
                                                            closeBubbleManually = true
                                                        }
                                        ) {
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
                                                                            if (scaleButtonHover
                                                                                            .value
                                                                            )
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
                                                                        scaleButtonHover.value =
                                                                                event.changes.any {
                                                                                    it.pressed
                                                                                }
                                                                    }
                                                                }
                                                            }
                                                            .pointerInput(Unit) {
                                                                detectDragGestures(
                                                                        onDragStart = {
                                                                            isDragging = true
                                                                        },
                                                                        onDragEnd = {
                                                                            isDragging = false
                                                                        },
                                                                        onDrag = {
                                                                                change,
                                                                                dragAmount ->
                                                                            change.consume()
                                                                            val scaleDelta =
                                                                                    dragAmount.y *
                                                                                            0.001f
                                                                            val newScale =
                                                                                    (windowState
                                                                                                    .scale +
                                                                                                    scaleDelta)
                                                                                            .coerceIn(
                                                                                                    0.5f,
                                                                                                    1.0f
                                                                                            )
                                                                            windowState =
                                                                                    windowState
                                                                                            .copy(
                                                                                                    scale =
                                                                                                            newScale
                                                                                            )
                                                                            floatContext
                                                                                    .onScaleChange(
                                                                                            newScale
                                                                                    )
                                                                        }
                                                                )
                                                            }
                                                            .pointerInput(Unit) {
                                                                detectTapGestures {
                                                                    val newScale =
                                                                            when {
                                                                                windowState.scale >
                                                                                        0.8f -> 0.7f
                                                                                windowState.scale >
                                                                                        0.7f -> 0.9f
                                                                                else -> 1.0f
                                                                            }
                                                                    windowState =
                                                                            windowState.copy(
                                                                                    scale = newScale
                                                                            )
                                                                    floatContext.onScaleChange(
                                                                            newScale
                                                                    )
                                                                }
                                                            }
                                    ) {
                                        val lineColor =
                                                if (scaleButtonHover.value)
                                                        primaryColor.copy(alpha = 1.0f)
                                                else primaryColor.copy(alpha = 0.7f)

                                        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                            drawLine(
                                                    color = lineColor,
                                                    start =
                                                            Offset(
                                                                    size.width * 0.2f,
                                                                    size.height * 0.8f
                                                            ),
                                                    end =
                                                            Offset(
                                                                    size.width * 0.8f,
                                                                    size.height * 0.2f
                                                            ),
                                                    strokeWidth = 3.5f
                                            )
                                            drawLine(
                                                    color = lineColor,
                                                    start =
                                                            Offset(
                                                                    size.width * 0.5f,
                                                                    size.height * 0.8f
                                                            ),
                                                    end =
                                                            Offset(
                                                                    size.width * 0.8f,
                                                                    size.height * 0.5f
                                                            ),
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
                                        Modifier.size(25.dp)
                                                .align(Alignment.BottomEnd)
                                                .pointerInput(Unit) {
                                                    detectDragGestures(
                                                            onDragStart = {
                                                                isDragging = true
                                                                floatContext.isEdgeResizing = true
                                                            },
                                                            onDragEnd = {
                                                                isDragging = false
                                                                floatContext.isEdgeResizing = false
                                                                floatContext.saveWindowState
                                                                        ?.invoke()
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()

                                                                val newWidth =
                                                                        (windowState.width +
                                                                                        with(
                                                                                                density
                                                                                        ) {
                                                                                            dragAmount
                                                                                                    .x
                                                                                                    .toDp()
                                                                                        })
                                                                                .coerceAtLeast(
                                                                                        150.dp
                                                                                )
                                                                val newHeight =
                                                                        (windowState.height +
                                                                                        with(
                                                                                                density
                                                                                        ) {
                                                                                            dragAmount
                                                                                                    .y
                                                                                                    .toDp()
                                                                                        })
                                                                                .coerceAtLeast(
                                                                                        200.dp
                                                                                )
                                                                windowState =
                                                                        windowState.copy(
                                                                                width = newWidth,
                                                                                height = newHeight
                                                                        )
                                                                floatContext.onResize(
                                                                        newWidth,
                                                                        newHeight
                                                                )
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
                                                            color = primaryColor,
                                                            shape = CircleShape
                                                    )
                                                    .align(Alignment.BottomEnd)
                            )
                        }
                    }

                    AnimatedVisibility(visible = isLocked, enter = fadeIn(), exit = fadeOut()) {
                        Box(
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                contentAlignment = Alignment.TopStart
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // 解锁按钮
                                IconButton(onClick = { floatContext.windowState?.isPetModeLocked?.value = false }) {
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
                                // 折叠按钮
                                IconButton(onClick = { floatContext.onModeChange(FloatingMode.BALL) }) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "折叠",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                // 全屏按钮
                                IconButton(onClick = { floatContext.onModeChange(FloatingMode.FULLSCREEN) }) {
                                    Icon(
                                        Icons.Default.Fullscreen,
                                        contentDescription = "全屏",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (showPetChatInput) {
                        Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.TopCenter
                        ) {
                            PetChatInputDialog(
                                    onDismiss = {
                                        showPetChatInput = false
                                        // 释放输入法焦点
                                        floatContext.onInputFocusRequest?.invoke(false)
                                    },
                                    onSendMessage = { message ->
                                        if (message.isNotBlank()) {
                                            floatContext.onSendMessage?.invoke(
                                                    message,
                                                    PromptFunctionType.DESKTOP_PET
                                            )
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
    ) { measurables, _ ->
        val widthInPx = with(density) { windowState.width.toPx() }
        val heightInPx = with(density) { windowState.height.toPx() }
        val scale = windowState.scale

        val placeable =
                measurables
                        .first()
                        .measure(
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