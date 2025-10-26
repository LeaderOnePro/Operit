package com.ai.assistance.operit.ui.floating

import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.services.floating.FloatingWindowState
import com.ai.assistance.operit.ui.floating.ui.window.models.ResizeEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 简化后的FloatContext类，移除了路由耦合逻辑 */
@Composable
fun rememberFloatContext(
        messages: List<ChatMessage>,
        width: Dp,
        height: Dp,
        onClose: () -> Unit,
        onResize: (Dp, Dp) -> Unit,
        ballSize: Dp = 48.dp,
        windowScale: Float = 1.0f,
        onScaleChange: (Float) -> Unit,
        currentMode: FloatingMode,
        previousMode: FloatingMode = FloatingMode.WINDOW,
        onModeChange: (FloatingMode) -> Unit,
        onMove: (Float, Float, Float) -> Unit = { _, _, _ -> },
        snapToEdge: (Boolean) -> Unit = { _ -> },
        isAtEdge: Boolean = false,
        screenWidth: Dp = 1080.dp,
        screenHeight: Dp = 2340.dp,
        currentX: Float = 0f,
        currentY: Float = 0f,
        saveWindowState: (() -> Unit)? = null,
        onSendMessage: ((String, PromptFunctionType) -> Unit)? = null,
        onCancelMessage: (() -> Unit)? = null,
        onAttachmentRequest: ((String) -> Unit)? = null,
        attachments: List<AttachmentInfo> = emptyList(),
        onRemoveAttachment: ((String) -> Unit)? = null,
        onInputFocusRequest: ((Boolean) -> Unit)? = null,
        chatService: FloatingChatService? = null,
        windowState: FloatingWindowState? = null
): FloatContext {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val floatContext = remember(
            messages,
            width,
            height,
            onClose,
            onResize,
            ballSize,
            windowScale,
            onScaleChange,
            currentMode, // <-- 添加 currentMode
            previousMode, // <-- 添加 previousMode
            onModeChange,
            onMove,
            snapToEdge,
            isAtEdge,
            screenWidth,
            screenHeight,
            currentX,
            currentY,
            saveWindowState,
            onSendMessage,
            onCancelMessage,
            onAttachmentRequest,
            attachments,
            onRemoveAttachment,
            onInputFocusRequest,
            chatService,
            windowState
    ) {
        FloatContext(
                messages = messages,
                windowWidthState = width,
                windowHeightState = height,
                onClose = onClose,
                onResize = onResize,
                ballSize = ballSize,
                windowScale = windowScale,
                onScaleChange = onScaleChange,
                currentMode = currentMode,
                previousMode = previousMode,
                onModeChange = onModeChange,
                onMove = onMove,
                snapToEdge = snapToEdge,
                isAtEdge = isAtEdge,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                currentX = currentX,
                currentY = currentY,
                saveWindowState = saveWindowState,
                onSendMessage = onSendMessage,
                onCancelMessage = onCancelMessage,
                onAttachmentRequest = onAttachmentRequest,
                attachments = attachments,
                onRemoveAttachment = onRemoveAttachment,
                onInputFocusRequest = onInputFocusRequest,
                density = density,
                coroutineScope = scope,
                chatService = chatService,
                windowState = windowState
        )
    }

    return floatContext
}

/** 简化的悬浮窗状态与回调上下文 */
class FloatContext(
        val messages: List<ChatMessage>,
        var windowWidthState: Dp,
        var windowHeightState: Dp,
        val onClose: () -> Unit,
        val onResize: (Dp, Dp) -> Unit,
        val ballSize: Dp,
        val windowScale: Float,
        val onScaleChange: (Float) -> Unit,
        var currentMode: FloatingMode,
        var previousMode: FloatingMode,
        val onModeChange: (FloatingMode) -> Unit,
        val onMove: (Float, Float, Float) -> Unit,
        val snapToEdge: (Boolean) -> Unit,
        val isAtEdge: Boolean,
        val screenWidth: Dp,
        val screenHeight: Dp,
        val currentX: Float,
        val currentY: Float,
        val saveWindowState: (() -> Unit)?,
        val onSendMessage: ((String, PromptFunctionType) -> Unit)?,
        val onCancelMessage: (() -> Unit)?,
        val onAttachmentRequest: ((String) -> Unit)?,
        val attachments: List<AttachmentInfo>,
        val onRemoveAttachment: ((String) -> Unit)?,
        val onInputFocusRequest: ((Boolean) -> Unit)?,
        val density: Density,
        val coroutineScope: CoroutineScope,
        val chatService: FloatingChatService? = null,
        val windowState: FloatingWindowState? = null
) {
    // 动画与转换相关状态
    val animatedAlpha = Animatable(1f)
    val transitionFeedback = Animatable(0f)

    // 大小调整相关状态
    var isEdgeResizing: Boolean = false
    var activeEdge: ResizeEdge = ResizeEdge.NONE
    var initialWindowWidth: Float = 0f
    var initialWindowHeight: Float = 0f

    // 对话框与内容显示状态
    var showInputDialog: Boolean by mutableStateOf(false)
    var userMessage: String by mutableStateOf("")
    var contentVisible: Boolean by mutableStateOf(true)
    var showAttachmentPanel: Boolean by mutableStateOf(false)
}
