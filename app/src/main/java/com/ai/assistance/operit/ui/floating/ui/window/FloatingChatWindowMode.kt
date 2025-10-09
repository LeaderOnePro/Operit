package com.ai.assistance.operit.ui.floating.ui.window

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.ui.features.chat.components.AttachmentChip
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.FloatingMode
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// A data class to hold the UI state that changes during drag
private data class DraggableWindowState(
    val width: Dp,
    val height: Dp,
    val scale: Float,
)

/** 渲染悬浮窗的窗口模式界面 - 简化版 */
@Composable
fun FloatingChatWindowMode(floatContext: FloatContext) {
    val cornerRadius = 12.dp
    val borderThickness = 3.dp
    val edgeHighlightColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.background
    val handleDrag = { dx: Float, dy: Float ->
        floatContext.onMove(dx, dy, floatContext.windowScale)
    }

    // Encapsulate draggable state into a single object to simplify state management
    var windowState by remember {
        mutableStateOf(
            DraggableWindowState(
                width = floatContext.windowWidthState,
                height = floatContext.windowHeightState,
                scale = floatContext.windowScale
            )
        )
    }
    
    // Single flag to indicate user is dragging to resize or scale
    var isDragging by remember { mutableStateOf(false) }
    
    // 获取isUiBusy的当前值
    // 删除 isUiBusy 相关内容

    // Effect to sync local state with the source of truth from floatContext
    // when the user is not actively dragging.
        LaunchedEffect(
                floatContext.windowWidthState,
                floatContext.windowHeightState,
                floatContext.windowScale
        ) {
        if (!isDragging) {
                        windowState =
                                DraggableWindowState(
                width = floatContext.windowWidthState,
                height = floatContext.windowHeightState,
                scale = floatContext.windowScale
            )
        }
    }
    
    // 预先提取颜色用于消息显示
    val userMessageColor = MaterialTheme.colorScheme.primaryContainer
    val aiMessageColor = MaterialTheme.colorScheme.surface
    val userTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val aiTextColor = MaterialTheme.colorScheme.onSurface
    val systemMessageColor = MaterialTheme.colorScheme.surfaceVariant
    val systemTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val thinkingBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val thinkingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

    val density = LocalDensity.current
    
    Layout(
        content = {
        // 创建一个呈现边缘视觉反馈的覆盖层
            Box(modifier = Modifier.fillMaxSize()) {
            // 主要内容区域，含边框
            Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .shadow(8.dp, RoundedCornerShape(cornerRadius))
                            .border(
                                width = borderThickness,
                                color =
                                    if (floatContext.isEdgeResizing)
                                        edgeHighlightColor
                                    else Color.Transparent,
                                shape = RoundedCornerShape(cornerRadius)
                            )
                            .clip(RoundedCornerShape(cornerRadius))
                            .background(backgroundColor)
                            .onSizeChanged {
                                // 此处不再需要更新
                                // initialWindowWidth/Height，避免与拖动状态冲突
                            }
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 窗口模式下的顶部工具栏
                    val titleBarHover = remember { mutableStateOf(false) }
                    // 添加按钮事件状态
                    val closeButtonPressed = remember { mutableStateOf(false) }

                    // 处理关闭按钮副作用
                    LaunchedEffect(closeButtonPressed.value) {
                        if (closeButtonPressed.value) {
                                floatContext.animatedAlpha.animateTo(
                                    0f,
                                    animationSpec = tween(200)
                                )
                            floatContext.onClose()
                            closeButtonPressed.value = false // 重置状态
                        }
                    }

                            // 标题栏
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
                                                    event.changes.any {
                                                        it.pressed
                                                    }
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
                        // 显示内容
                        if (floatContext.contentVisible) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.ai_assistant),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // DragonBones按钮
                                    IconButton(
                                            onClick = {
                                                        floatContext.onModeChange(
                                                                FloatingMode.DragonBones
                                                        )
                                            },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "DragonBones",
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // 全屏按钮
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

                                    // 最小化按钮
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

                                    // 关闭按钮
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
                    
                    // 聊天内容区域 - 使用 modifier.weight(1f) 让它占据 Column 中的剩余空间
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        // 这里放置聊天内容...
                        
                                // 条件性显示聊天内容或输入框
                        if (!floatContext.showInputDialog) {
                                    val scrollState = rememberScrollState()

                                    // 用于追踪流式内容变化的状态
                                    var streamUpdateTrigger by remember { mutableStateOf(0) }
                                    
                                    // 收集最后一条AI消息的流内容，触发滚动
                                    LaunchedEffect(floatContext.messages.size) {
                                        val lastAiMessage = floatContext.messages.lastOrNull { it.sender == "ai" }
                                        val stream = lastAiMessage?.contentStream
                                        
                                        if (stream != null) {
                                            // 启动流收集，每次有新内容都触发滚动
                                            launch {
                                                try {
                                                    stream.collect { _ ->
                                                        // 每次流发出新内容，更新触发器
                                                        streamUpdateTrigger++
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("FloatingChatWindow", "Stream collection error", e)
                                                }
                                            }
                                        }
                                    }
                                    
                                    // Auto-scroll to bottom - 监听消息数量和流更新触发器
                                    LaunchedEffect(floatContext.messages.size, streamUpdateTrigger) {
                                        if (floatContext.messages.isNotEmpty()) {
                                            scrollState.animateScrollTo(scrollState.maxValue)
                                        }
                                    }

                                    val isLoading =
                                            floatContext.messages.lastOrNull()?.sender == "think"

                                    Column(
                                            modifier =
                                                    Modifier.fillMaxSize()
                                                            .verticalScroll(scrollState)
                                                            .padding(
                                                                    horizontal = 16.dp,
                                                                    vertical = 16.dp
                                                            )
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
                                                            thinkingBackgroundColor =
                                                                    thinkingBackgroundColor,
                                                            thinkingTextColor = thinkingTextColor,
                                                            onSelectMessageToEdit = null,
                                                            onCopyMessage = null
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                        }

                                        val lastMessage =
                                                floatContext.messages.lastOrNull {
                                                    it.sender != "think"
                                                }
                                        if (isLoading &&
                                                        (lastMessage?.sender == "user" ||
                                                                (lastMessage?.sender == "ai" &&
                                                                        lastMessage.content.isBlank()))
                                        ) {
                                            Column(
                                                    modifier =
                                                            Modifier.fillMaxWidth()
                                                                    .padding(vertical = 0.dp)
                                            ) {
                                                Box(modifier = Modifier.padding(start = 16.dp)) {
                                                    LoadingDotsIndicator(aiTextColor)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // 集成的输入区域
                                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                        // 增加顶部间距，避免与标题栏重叠
                                        Spacer(modifier = Modifier.height(32.dp))

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

                                            IconButton(
                                                    onClick = {
                                                        floatContext.showInputDialog = false
                                                        floatContext.showAttachmentPanel =
                                                                false // 确保关闭附件选择面板
                                                    }
                                            ) {
                                                Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "关闭",
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // 显示已添加的附件
                                        if (floatContext.attachments.isNotEmpty()) {
                                            LazyRow(
                                                    modifier =
                                                            Modifier.fillMaxWidth()
                                                                    .padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                items(floatContext.attachments) { attachment ->
                                                    AttachmentChip(
                                                            attachmentInfo = attachment,
                                                            onRemove = {
                                                                floatContext.onRemoveAttachment?.invoke(
                                                                        attachment.filePath
                                                                )
                                                            },
                                                            onInsert = { /* 在悬浮窗中不支持插入操作 */}
                                                    )
                                        }
                                    }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }

                                        val focusRequester = remember { FocusRequester() }
                                        val keyboardController = LocalSoftwareKeyboardController.current
                                        val focusManager = LocalFocusManager.current

                                        // 添加一个当输入区域显示时的DisposableEffect
                                        DisposableEffect(floatContext.showInputDialog) {
                                            if (floatContext.showInputDialog) {
                                                floatContext.coroutineScope.launch {
                                                    // 增加延迟，确保视图完全渲染
                                                    delay(300)

                                                    // 请求焦点 - 但不主动显示键盘，让服务层处理键盘显示
                                                    try {
                                                        focusRequester.requestFocus()
                                                        // 移除直接显示键盘的代码，避免重复显示
                                                    } catch (e: Exception) {
                                                        Log.e(
                                                                "FloatingChatWindow",
                                                                "Failed to request focus",
                                                                e
                                                        )
                                                    }
                                                }
                                            }

                                            // 清理
                                            onDispose {}
                                        }

                                        // 改成Box包装输入框，使其填充剩余空间
                                        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                                            // 输入框 - 占满可用空间
                                            OutlinedTextField(
                                                    value = floatContext.userMessage,
                                                    onValueChange = { floatContext.userMessage = it },
                                                    placeholder = { Text("请输入您的问题...") },
                                                    modifier =
                                                            Modifier.fillMaxSize()
                                                                    .focusRequester(focusRequester),
                                                    textStyle = TextStyle.Default,
                                                    maxLines = Int.MAX_VALUE, // 允许多行
                                                    keyboardOptions =
                                                            KeyboardOptions(
                                                                    imeAction = ImeAction.Send,
                                                                    autoCorrect = true
                                                            ),
                                                    keyboardActions =
                                                            KeyboardActions(
                                                                    onSend = {
                                                                        if (floatContext.userMessage
                                                                                        .isNotBlank() ||
                                                                                floatContext
                                                                                        .attachments
                                                                                        .isNotEmpty()
                                                                        ) {
                                                                            floatContext.onSendMessage
                                                                                    ?.invoke(
                                                                                            floatContext
                                                                                                    .userMessage,
                                                                                            PromptFunctionType
                                                                                                    .CHAT
                                                                                    )
                                                                            floatContext.userMessage =
                                                                                    ""
                                                                            floatContext
                                                                                    .showInputDialog =
                                                                                    false
                                                                            floatContext
                                                                                    .showAttachmentPanel =
                                                                                    false
                                                                        }
                                                                    }
                                                            ),
                                                    colors =
                                                            OutlinedTextFieldDefaults.colors(
                                                                    focusedBorderColor =
                                                                            MaterialTheme.colorScheme
                                                                                    .primary,
                                                                    unfocusedBorderColor =
                                                                            MaterialTheme.colorScheme
                                                                                    .outline
                                                            ),
                                                    shape = RoundedCornerShape(12.dp)
                                            )

                                            // 发送按钮 - 放在右下角
                                            FloatingActionButton(
                                                    onClick = {
                                                        if (floatContext.userMessage.isNotBlank() ||
                                                                        floatContext.attachments
                                                                                .isNotEmpty()
                                                        ) {
                                                            floatContext.onSendMessage?.invoke(
                                                                    floatContext.userMessage,
                                                                    PromptFunctionType.CHAT
                                                            )
                                                            floatContext.userMessage = ""
                                                            floatContext.showInputDialog = false
                                                            floatContext.showAttachmentPanel = false
                                                        }
                                                    },
                                                    modifier =
                                                            Modifier.align(Alignment.BottomEnd)
                                                                    .padding(8.dp)
                                                                    .size(46.dp),
                                                    containerColor = MaterialTheme.colorScheme.primary
                                            ) {
                                                Icon(
                                                        imageVector = Icons.Default.Send,
                                                        contentDescription = "发送",
                                                        tint = MaterialTheme.colorScheme.onPrimary
                                                )
                                            }
                                        }
                                }
                            }
                        }
                    }
                }

                // 右下角 - 调整宽度和高度
                Box(
                            modifier =
                                    Modifier.size(25.dp).align(Alignment.BottomEnd).pointerInput(
                                                    Unit
                                            ) {
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

                // 显示边缘缩放指示器 - 只保留右下角
                    if (floatContext.isEdgeResizing &&
                                    floatContext.activeEdge == ResizeEdge.BOTTOM_RIGHT
                    ) {
                    // 右下角调整指示器
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

                    // 缩放控制手柄 - 只在不显示输入框时显示
                    if (!floatContext.showInputDialog) {
                        // 添加缩放按钮悬停状态
                        val scaleButtonHover = remember { mutableStateOf(false) }

                        Box(
                                modifier =
                                        Modifier.size(48.dp)
                                                .padding(6.dp)
                                                .align(Alignment.BottomEnd)
                                                .offset(x = (-8).dp, y = (-8).dp)
                                                // 添加点击涟漪效果的背景
                                                .background(
                                                        color =
                                                                if (scaleButtonHover.value)
                                                                        primaryColor.copy(
                                                                                alpha = 0.1f
                                                                        )
                                                                else Color.Transparent,
                                                        shape = CircleShape
                                                )
                                                // 监听悬停状态
                                                .pointerInput(Unit) {
                                                    awaitPointerEventScope {
                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            scaleButtonHover.value =
                                                                    event.changes.any { it.pressed }
                                                        }
                                                    }
                                                }
                                                // 拖动调整缩放
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
                                                                floatContext.onScaleChange(newScale)
                                                            }
                                                    )
                                                }
                                                // 点击切换缩放比例
                                                .pointerInput(Unit) {
                                                    detectTapGestures {
                                                        val newScale =
                                                                when {
                                                                    windowState.scale > 0.8f -> 0.7f
                                                                    windowState.scale > 0.7f -> 0.9f
                                                                    else -> 1.0f
                                                                }
                                                        windowState =
                                                                windowState.copy(scale = newScale)
                                                        floatContext.onScaleChange(newScale)
                                                    }
                                                }
                        ) {
                            val lineColor =
                                    if (scaleButtonHover.value) primaryColor.copy(alpha = 1.0f)
                                    else primaryColor.copy(alpha = 0.7f)

                            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                // 绘制缩放图标
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
            },
            modifier = Modifier.graphicsLayer { alpha = floatContext.animatedAlpha.value }
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

    // 输入按钮 - 只有在不显示输入框时才显示
    if (!floatContext.showInputDialog && floatContext.onSendMessage != null) {
        Layout(
            content = {
                Box(modifier = Modifier.fillMaxSize()) {
                    // 添加附件按钮 - 放在编辑按钮上方
                    SmallFloatingActionButton(
                        onClick = {
                            // 修改为切换附件面板显示/隐藏
                            floatContext.showAttachmentPanel =
                                !floatContext.showAttachmentPanel
                        },
                        modifier =
                            Modifier.align(Alignment.BottomStart)
                                .padding(start = 12.dp, bottom = 60.dp)
                                .size(34.dp),
                        containerColor =
                            if (floatContext.showAttachmentPanel)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                            else
                                MaterialTheme.colorScheme.secondaryContainer.copy(
                                    alpha = 0.75f
                                ),
                        contentColor =
                            if (floatContext.showAttachmentPanel)
                                MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription =
                                if (floatContext.showAttachmentPanel) "关闭附件面板"
                                else "添加附件",
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // 编辑消息按钮 - 保持原有功能
                    SmallFloatingActionButton(
                        onClick = {
                            // 直接显示输入框，因为当前已经是窗口模式
                            floatContext.showInputDialog = true
                        },
                        modifier =
                            Modifier.align(Alignment.BottomStart)
                                .padding(12.dp)
                                .size(34.dp),
                        containerColor =
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "发送消息",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
                modifier = Modifier.graphicsLayer { alpha = floatContext.animatedAlpha.value }
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

    // 附件面板 - 在点击附件按钮时显示
    if (floatContext.showAttachmentPanel && !floatContext.showInputDialog) {
        Layout(
            content = {
                Box(
                            modifier =
                                    Modifier.fillMaxSize()
                        // 修改pointerInput逻辑以检测附件按钮区域
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                // 计算附件面板的大概位置（底部区域）
                                val panelHeight = 220.dp.toPx() // 附件面板的大致高度
                                val screenHeight = size.height

                                // 计算附件按钮的区域（左下角+按钮位置）- 使用与实际按钮一致的值
                                val buttonSize = 34.dp.toPx() // 使用实际按钮尺寸34dp
                                                    val buttonPaddingStart =
                                                            12.dp.toPx() // 使用实际的开始内边距12dp
                                                    val buttonPaddingBottom =
                                                            60.dp.toPx() // 使用实际的底部内边距60dp
                                val buttonLeft = buttonPaddingStart
                                val buttonTop =
                                    screenHeight -
                                        buttonPaddingBottom -
                                        buttonSize
                                val buttonRight = buttonLeft + buttonSize
                                val buttonBottom = buttonTop + buttonSize

                                // 增加一点点点击区域，使按钮更容易点到
                                val expandedClickArea = 6.dp.toPx()
                                val isButtonClicked =
                                    offset.x >=
                                        (buttonLeft -
                                            expandedClickArea) &&
                                        offset.x <=
                                            (buttonRight +
                                                expandedClickArea) &&
                                        offset.y >=
                                            (buttonTop -
                                                expandedClickArea) &&
                                        offset.y <=
                                            (buttonBottom +
                                                expandedClickArea)

                                if (isButtonClicked) {
                                    // 点击了附件按钮，切换面板显示状态
                                    floatContext.showAttachmentPanel =
                                                                !floatContext.showAttachmentPanel
                                }
                                // 检查点击是否在面板外部区域
                                                    else if (offset.y < screenHeight - panelHeight
                                ) {
                                    // 点击面板外部区域，关闭面板
                                                        floatContext.showAttachmentPanel = false
                                }
                                // 点击在面板内部区域，不做处理
                            }
                        }
                ) {
                    // 使用自定义的悬浮窗专用附件面板
                    FloatingAttachmentPanel(
                        visible = floatContext.showAttachmentPanel,
                        onAttachScreenContent = {
                            floatContext.coroutineScope.launch {
                                // 屏幕内容附件 - 在service层处理
                                        floatContext.onAttachmentRequest?.invoke("screen_capture")
                                // 允许附件面板关闭，但稍后再刷新附件列表
                                delay(500) // 给Service一点时间处理附件
                                // 保持附件面板关闭状态，但内容已更新
                                floatContext.showAttachmentPanel = false
                            }
                        },
                        onAttachNotifications = {
                            floatContext.coroutineScope.launch {
                                // 通知附件 - 在service层处理
                                floatContext.onAttachmentRequest?.invoke(
                                    "notifications_capture"
                                )
                                // 允许附件面板关闭，但稍后再刷新附件列表
                                delay(500) // 给Service一点时间处理附件
                                // 保持附件面板关闭状态，但内容已更新
                                floatContext.showAttachmentPanel = false
                            }
                        },
                        onAttachLocation = {
                            floatContext.coroutineScope.launch {
                                // 位置附件 - 在service层处理
                                        floatContext.onAttachmentRequest?.invoke("location_capture")
                                // 允许附件面板关闭，但稍后再刷新附件列表
                                delay(500) // 给Service一点时间处理附件
                                // 保持附件面板关闭状态，但内容已更新
                                floatContext.showAttachmentPanel = false
                            }
                        },
                        onDismiss = { floatContext.showAttachmentPanel = false }
                    )
                }
            },
                modifier = Modifier.graphicsLayer { alpha = floatContext.animatedAlpha.value }
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
}
