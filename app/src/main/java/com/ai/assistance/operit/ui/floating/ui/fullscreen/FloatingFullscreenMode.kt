package com.ai.assistance.operit.ui.floating.ui.fullscreen

import android.content.Context
import android.media.AudioManager
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.preferences.PromptFunctionType
import com.ai.assistance.operit.ui.common.WaveVisualizer
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.FloatingMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.absoluteValue

private const val TAG = "FloatingFullscreenMode"

@Composable
fun FloatingFullscreenMode(floatContext: FloatContext) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var isProcessingSpeech by remember { mutableStateOf(false) }
    var timeoutJob by remember { mutableStateOf<Job?>(null) }
    var userMessage by remember { mutableStateOf("") }
    var accumulatedText by remember { mutableStateOf("") }
    var latestPartialText by remember { mutableStateOf("") }
    var aiMessage by remember { mutableStateOf("长按下方麦克风开始说话") }
    val coroutineScope = rememberCoroutineScope()
    var activeMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val isInitialLoad = remember { mutableStateOf(true) }
    
    // 波浪可视化相关状态
    var isWaveActive by remember { mutableStateOf(false) }
    var showBottomControls by remember { mutableStateOf(true) }
    var silenceTimeoutJob by remember { mutableStateOf<Job?>(null) }
    
    // 添加输入法服务引用
    val inputMethodManager = remember { 
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager 
    }

    val speed = 1.2f
    
    var hasFocus by remember { mutableStateOf(false) }

    // 创建语音识别和TTS服务
    val speechService = remember {
        SpeechServiceFactory.getInstance(
                context,
                SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN
        )
    }
    
    // 获取音量流
    val volumeLevel by speechService.volumeLevelFlow.collectAsState()

    val voiceService = remember { VoiceServiceFactory.getInstance(context) }
    
    val sendCurrentUtteranceAndContinue = {
        coroutineScope.launch {
            val finalText = userMessage
            if (finalText.isNotBlank()) {
                Log.d(TAG, "Wave mode: sending utterance due to silence: '$finalText'")
                floatContext.onSendMessage?.invoke(finalText, PromptFunctionType.VOICE)
                aiMessage = "思考中..."
            }
            // Reset text buffers for the next utterance
            userMessage = ""
            accumulatedText = ""
            latestPartialText = ""
        }
    }

    // 启动和停止语音识别的通用函数
    val startVoiceCapture = {
        coroutineScope.launch {
            voiceService.stop()
            if (hasFocus) {
                timeoutJob?.cancel()
                isRecording = true
                userMessage = ""
                accumulatedText = ""
                latestPartialText = ""
                aiMessage = "正在聆听..."

                // Restore the check to only cancel if the AI is actually busy
                val lastMessage = floatContext.messages.lastOrNull()
                val isAiCurrentlyWorking =
                    lastMessage?.sender == "think" ||
                            (lastMessage?.sender == "ai" && lastMessage.contentStream != null)

                if (isAiCurrentlyWorking) {
                    floatContext.onCancelMessage?.invoke()
                }

                speechService.startRecognition(
                    languageCode = "zh-CN",
                    continuousMode = true,
                    partialResults = true
                )
            } else {
                aiMessage = "无法开始录音，无法获取焦点"
                voiceService.speak(aiMessage, rate = speed)
            }
        }
    }

    val stopVoiceCapture = { isCancel: Boolean ->
        coroutineScope.launch {
            if (isRecording) {
                isRecording = false
                
                silenceTimeoutJob?.cancel()

                if (isCancel) {
                    speechService.cancelRecognition()
                    isProcessingSpeech = false
                    userMessage = ""
                    accumulatedText = ""
                    latestPartialText = ""
                    aiMessage = "长按下方麦克风开始说话" // Revert to default prompt on cancel
                } else {
                    isProcessingSpeech = true
                    aiMessage = "识别中..."
                    speechService.stopRecognition()

                    // 设置一个备用超时，以防最终结果由于某种原因从未到达
                    timeoutJob = coroutineScope.launch {
                        delay(3000) // 3秒后超时
                        if (isProcessingSpeech) {
                            Log.w(TAG, "Fallback timeout: Final result not received. Sending current message.")
                            isProcessingSpeech = false
                            val finalText = userMessage
                            if (finalText.isNotBlank()) {
                                floatContext.onSendMessage?.invoke(finalText, PromptFunctionType.VOICE)
                                aiMessage = "思考中..."
                            } else {
                                aiMessage = "没有听清，请再试一次"
                                voiceService.speak(aiMessage, rate = speed)
                            }
                            accumulatedText = ""
                            latestPartialText = ""
                        }
                    }
                }
            }
        }
    }

    // 监听语音识别结果
    LaunchedEffect(speechService) {
        speechService.recognitionResultFlow.collectLatest { result ->
            if (isRecording) {
                if (result.text.isNotBlank()) {
                    // Barge-in: If the user starts speaking, stop the AI's TTS output.
                    if (userMessage.isBlank()) {
                        voiceService.stop()
                    }

                    if (latestPartialText.isNotEmpty() && !result.text.startsWith(latestPartialText)) {
                        if (accumulatedText.isNotEmpty()) {
                            accumulatedText += "。"
                        }
                        accumulatedText += latestPartialText
                    }
                    latestPartialText = result.text

                    // 在波浪模式下，每当有新文本时，重置静默超时
                    if (isWaveActive) {
                        silenceTimeoutJob?.cancel()
                        silenceTimeoutJob = coroutineScope.launch {
                            delay(2000) // 2秒静默后自动发送
                            Log.d(TAG, "Wave mode silence timeout. Sending message.")
                            sendCurrentUtteranceAndContinue()
                        }
                    }
                }
                val separator = if (accumulatedText.isEmpty() || latestPartialText.isEmpty()) "" else "。"
                userMessage = accumulatedText + separator + latestPartialText
            } else if (isProcessingSpeech) {
                if (result.isFinal) {
                    timeoutJob?.cancel() // 收到最终结果，取消备用超时
                    isProcessingSpeech = false

                    var finalText = accumulatedText
                    val finalSegment = result.text

                    if (finalSegment.isNotBlank()) {
                        if (finalText.isNotEmpty()) {
                            finalText += "。"
                        }
                        finalText += finalSegment
                    }

                    if (finalText.isNotBlank()) {
                        userMessage = finalText
                        Log.d(TAG, "Sending final text from collector: '$finalText'")
                        floatContext.onSendMessage?.invoke(finalText, PromptFunctionType.VOICE)
                        aiMessage = "思考中..."
                    } else {
                        Log.d(TAG, "Final text is blank.")
                        aiMessage = "没有听清，请再试一次"
                        voiceService.speak(aiMessage, rate = speed)
                    }
                    accumulatedText = ""
                    latestPartialText = ""
                }
            }
        }
    }

    // 在界面进入时，增加一个副作用来重置所有状态，确保一个干净的开始
    LaunchedEffect(Unit) {
        isRecording = false
        isProcessingSpeech = false
        userMessage = ""
        accumulatedText = ""
        latestPartialText = ""
        aiMessage = "长按下方麦克风开始说话"
        activeMessage = null
        isInitialLoad.value = true // 确保每次进入都重置
        timeoutJob?.cancel()
        silenceTimeoutJob?.cancel()
        isWaveActive = false
        showBottomControls = true

        // 初始化语音服务
        speechService.initialize()
        voiceService.initialize()

        // 请求输入法焦点以在后台保持录音能力
        val composeView = floatContext.chatService?.getComposeView()
        if (composeView != null) {
            composeView.requestFocus()
            // 请求显示输入法，然后立即隐藏，这是一种获取输入焦点的技巧
            inputMethodManager.showSoftInput(composeView, InputMethodManager.SHOW_FORCED)
            inputMethodManager.hideSoftInputFromWindow(composeView.windowToken, 0)
            hasFocus = true
            Log.d(TAG, "FloatingFullscreenMode 已获取输入法焦点")
        } else {
            hasFocus = false
            aiMessage = "无法获取输入法服务"
            Log.w(TAG, "无法获取 composeView 以请求输入法焦点")
        }
    }
    
    // 根据isWaveActive状态来控制语音识别的启停
    // DELETED: This effect was causing the aggressive start when entering wave mode.
    // The logic is now handled directly in the onToggleActive lambda.

    // 监听最新的AI消息
    LaunchedEffect(floatContext.messages) {
        val lastMessage = floatContext.messages.lastOrNull()

        // 只处理新消息
        if (lastMessage === activeMessage || lastMessage == null) return@LaunchedEffect
        activeMessage = lastMessage

        // 如果是首次加载，只更新UI，不朗读
        if (isInitialLoad.value) {
            isInitialLoad.value = false
            // 首次加载时，我们只将最后一条消息标记为"已处理"(通过更新activeMessage)，但不显示也不朗读它。
            // 这样可以确保屏幕是干净的，等待用户交互。
            return@LaunchedEffect
        }

        // 停止当前可能在播放的语音，为新消息做准备
        voiceService.stop()

        when (lastMessage.sender) {
            "think" -> {
                aiMessage = "思考中..."
            }
            "ai" -> {
                // 启动协程来收集流，使UI保持响应性
                coroutineScope.launch {
                    var didSpeak = false
                    aiMessage = "" // 清除之前的文本以进行流式处理
                    lastMessage.contentStream?.let { stream ->
                        val processedStream = XmlTextProcessor.processStreamToText(stream)

                        val sentenceEndChars = listOf('.', '。', '!', '！', '?', '？', '\n')
                        val sentenceBuffer = StringBuilder()
                        var isFirstSentence = true

                        processedStream.collect { char ->
                            aiMessage += char

                            // 将字符添加到句子缓冲区
                            sentenceBuffer.append(char)

                            // 如果遇到句子结束符号，或者缓冲区已经足够长，则执行TTS
                            if (char in sentenceEndChars || sentenceBuffer.length >= 50) {
                                val sentenceToSpeak = sentenceBuffer.toString().trim()
                                if (sentenceToSpeak.isNotBlank()) {
                                    didSpeak = true
                                    // 第一句中断播放，后续句子加入队列
                                    voiceService.speak(
                                            sentenceToSpeak,
                                            interrupt = isFirstSentence,
                                            rate = speed
                                    )
                                    isFirstSentence = false
                                }
                                sentenceBuffer.clear()
                            }
                        }

                        // 处理最后剩余的文本
                        val finalSentence = sentenceBuffer.toString().trim()
                        if (finalSentence.isNotBlank()) {
                            didSpeak = true
                            voiceService.speak(
                                    finalSentence,
                                    interrupt = isFirstSentence,
                                    rate = speed
                            )
                        }
                    }
                            ?: run {
                                // 如果没有流，则显示静态内容
                                aiMessage = lastMessage.content
                                // 一次性使用TTS播放AI回复
                                if (aiMessage.isNotBlank()) {
                                    didSpeak = true
                                    voiceService.speak(aiMessage, rate = speed)
                                }
                            }
                }
            }
        }
    }

    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            timeoutJob?.cancel()
            silenceTimeoutJob?.cancel()
            // 确保释放输入法焦点
            floatContext.chatService?.getComposeView()?.let { view ->
                inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
                Log.d(TAG, "组件销毁时释放输入法焦点")
            }
            
            coroutineScope.launch {
                speechService.cancelRecognition()
                voiceService.stop()
            }
        }
    }

    Box(
        modifier =
        Modifier.fillMaxSize()
            .background(
                brush =
                Brush.verticalGradient(
                    colorStops =
                    arrayOf(
                        0.0f to Color.Transparent,
                        0.6f to
                                Color.Black.copy(
                                    alpha = 0.7f
                                ),
                        1.0f to
                                Color.Black.copy(
                                    alpha = 0.9f
                                )
                    )
                )
            )
    ) {
        // Top right close button
        IconButton(
            onClick = { floatContext.onClose() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(42.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭悬浮窗",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // This is the content area, which changes based on wave mode.
        // We'll use a shared wave visualizer that animates position for a smoother transition
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (showBottomControls) 120.dp else 32.dp)
        ) {
            // Animated positions for the wave visualizer
            val waveSize by animateDpAsState(
                targetValue = if (isWaveActive) 200.dp else 120.dp,
                animationSpec = tween(500), label = "waveSize"
            )
            
            val waveOffsetY by animateDpAsState(
                targetValue = if (isWaveActive) 0.dp else (-100).dp,
                animationSpec = tween(500), label = "waveOffsetY"
            )
            
            // This Wave Visualizer stays persistent across modes for a smooth transition
            Box(
                modifier = Modifier
                    .align(if (isWaveActive) Alignment.Center else Alignment.Center)
                    .offset(y = waveOffsetY)
            ) {
                WaveVisualizer(
                    modifier = Modifier.size(waveSize),
                    isActive = isWaveActive,
                    volumeFlow = if (isWaveActive && isRecording) speechService.volumeLevelFlow else null,
                    waveColor = Color.White.copy(alpha = 0.7f),
                    activeWaveColor = MaterialTheme.colorScheme.primary,
                    onToggleActive = {
                        if (isWaveActive) {
                            // === User is EXITING wave mode ===
                            stopVoiceCapture(true) // true = isCancel
                            isWaveActive = false
                            showBottomControls = true
                        } else {
                            // === User is ENTERING wave mode ===
                            // Perform a "gentle" start that doesn't cancel the AI.
                            coroutineScope.launch {
                                if (hasFocus) {
                                    isRecording = true
                                    userMessage = ""
                                    accumulatedText = ""
                                    latestPartialText = ""
                                    aiMessage = "正在聆听..."
                                    // We do NOT call voiceService.stop() or floatContext.onCancelMessage?.invoke() here.
                                    // Barge-in logic will handle TTS cutoff when user speaks.
                                    speechService.startRecognition(
                                        languageCode = "zh-CN",
                                        continuousMode = true,
                                        partialResults = true
                                    )

                                    // Update the state to reflect the new mode
                                    isWaveActive = true
                                    showBottomControls = false
                                } else {
                                    aiMessage = "无法开始录音，无法获取焦点"
                                    voiceService.speak(aiMessage, rate = speed)
                                }
                            }
                        }
                    }
                )
            }
            
            // AnimatedContent for messages only
            AnimatedContent(
                targetState = isWaveActive,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300, 150)) togetherWith
                    fadeOut(animationSpec = tween(300))
                },
                label = "MessageTransition",
                modifier = Modifier.fillMaxSize()
            ) { targetIsWaveActive ->
                if (targetIsWaveActive) {
                    // WAVE MODE LAYOUT: Text at bottom
                    Box(modifier = Modifier.fillMaxSize()) {
                        MessageDisplay(
                            userMessage,
                            aiMessage,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp)
                                .padding(bottom = 64.dp)
                        )
                    }
                } else {
                    // NORMAL MODE LAYOUT: Text below wave
                    Box(modifier = Modifier.fillMaxSize()) {
                        MessageDisplay(
                            userMessage,
                            aiMessage,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(y = 80.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp)
                        )
                    }
                }
            }
        }

        // 底部控制栏 - 只在showBottomControls为true时显示
        AnimatedVisibility(
            visible = showBottomControls,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier =
                Modifier.fillMaxWidth()
                    .padding(bottom = 64.dp, start = 32.dp, end = 32.dp)
            ) {
                // 返回按钮 - 左侧 (纯图标)，切换到窗口模式
                IconButton(
                    onClick = {
                        val targetMode = if (floatContext.previousMode == FloatingMode.FULLSCREEN || floatContext.previousMode == FloatingMode.VOICE_BALL) {
                            FloatingMode.WINDOW
                        } else {
                            floatContext.previousMode
                        }
                        floatContext.onModeChange(targetMode)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "返回窗口模式",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // 麦克风按钮 - 中间
                var dragOffset by remember { mutableStateOf(0f) }
                val isDraggingToCancel = remember { mutableStateOf(false) }

                Box(
                    modifier =
                    Modifier.align(Alignment.Center)
                        .size(80.dp)
                        .shadow(elevation = 8.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(
                            brush =
                            Brush.radialGradient(
                                colors =
                                if (isRecording ||
                                    isProcessingSpeech
                                ) {
                                    listOf(
                                        MaterialTheme
                                            .colorScheme
                                            .secondary,
                                        MaterialTheme
                                            .colorScheme
                                            .secondaryContainer
                                    )
                                } else {
                                    listOf(
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                            .copy(
                                                alpha =
                                                0.8f
                                            ),
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                    )
                                }
                            )
                        )
                        .clickable(enabled = false, onClick = {}) // 为了让Box消费事件
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    // 添加单击事件以激活波浪模式
                                    isWaveActive = true
                                    showBottomControls = false
                                },
                                onLongPress = {
                                    // Reset drag state for the new gesture before starting.
                                    dragOffset = 0f
                                    isDraggingToCancel.value = false
                                    
                                    coroutineScope.launch {
                                        startVoiceCapture()
                                    }
                                },
                                onPress = {
                                    try {
                                        // 在onPress期间检测水平拖动，用于取消
                                        withTimeoutOrNull(Long.MAX_VALUE) {
                                            var previousPosition = Offset.Zero
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val position =
                                                        event.changes[0]
                                                            .position
                                                    if (previousPosition !=
                                                        Offset.Zero
                                                    ) {
                                                        // 计算水平拖动距离
                                                        val horizontalDrag =
                                                            position.x -
                                                                    previousPosition
                                                                        .x
                                                        dragOffset += horizontalDrag

                                                        // 如果水平拖动距离的绝对值超过阈值（60dp），标记为取消
                                                        isDraggingToCancel.value =
                                                            dragOffset.absoluteValue > 60f

                                                        if (isRecording &&
                                                            isDraggingToCancel
                                                                .value
                                                        ) {
                                                            // 不再显示文本提示，而是在UI中显示垃圾桶图标
                                                        }
                                                    }
                                                    previousPosition = position

                                                    // 检查是否抬起手指
                                                    if (event.changes[0].pressed
                                                            .not()
                                                    ) {
                                                        break
                                                    }
                                                }
                                            }
                                        }
                                    } finally {
                                        if (isRecording && !isWaveActive) { // 波浪模式下不响应抬起事件
                                            stopVoiceCapture(isDraggingToCancel.value)
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 根据状态显示不同的图标
                    if (isRecording && isDraggingToCancel.value) {
                        // 如果在拖动取消中，显示垃圾桶图标
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "取消录音",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    } else {
                        // 正常情况显示麦克风图标
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "按住说话",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // 缩小成悬浮球按钮 - 右侧 (纯图标)，切换到语音球模式
                IconButton(
                    onClick = { floatContext.onModeChange(FloatingMode.VOICE_BALL) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "缩小成语音球",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageDisplay(userMessage: String, aiMessage: String, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // 用户消息
        if (userMessage.isNotEmpty()) {
            item {
                Text(
                    text = userMessage,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        // AI消息
        if (aiMessage.isNotBlank()) {
            item {
                Text(
                    text = aiMessage,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    modifier = Modifier.animateContentSize()
                )
            }
        }
    }
}
