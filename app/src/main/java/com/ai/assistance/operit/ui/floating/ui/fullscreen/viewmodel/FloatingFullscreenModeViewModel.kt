package com.ai.assistance.operit.ui.floating.ui.fullscreen.viewmodel

import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.*
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.util.TtsCleaner
import com.ai.assistance.operit.util.WaifuMessageProcessor
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "FloatingFullscreenViewModel"

/**
 * FloatingFullscreenMode 的 ViewModel
 * 管理语音识别、TTS、状态和业务逻辑
 */
class FloatingFullscreenModeViewModel(
    private val context: Context,
    private val floatContext: FloatContext,
    private val coroutineScope: CoroutineScope
) {
    // ===== 语音识别和TTS状态 =====
    var isRecording by mutableStateOf(false)
        private set
    var isProcessingSpeech by mutableStateOf(false)
        private set
    var userMessage by mutableStateOf("")
    var aiMessage by mutableStateOf("长按下方麦克风开始说话")
    
    // 内部状态
    private var accumulatedText by mutableStateOf("")
    private var latestPartialText by mutableStateOf("")
    private var timeoutJob: Job? = null
    private var silenceTimeoutJob: Job? = null
    
    // ===== UI状态 =====
    var isWaveActive by mutableStateOf(false)
    var showBottomControls by mutableStateOf(true)
    var isEditMode by mutableStateOf(false)
    var editableText by mutableStateOf("")
    var showDragHints by mutableStateOf(false)
    var hasFocus by mutableStateOf(false)
        private set
    
    val isInitialLoad = mutableStateOf(true)
    
    // ===== 服务 =====
    val speechService = SpeechServiceFactory.getInstance(context)
    val voiceService = VoiceServiceFactory.getInstance(context)
    private val inputMethodManager = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
    
    // ===== TTS 清理函数 =====
    fun cleanTextForTts(text: String, ttsCleanerRegexs: List<String>): String {
        val regexCleaned = TtsCleaner.clean(text, ttsCleanerRegexs)
        return WaifuMessageProcessor.cleanContentForWaifu(regexCleaned)
    }
    
    // ===== 安全的 TTS 播放 =====
    fun safeSpeak(text: String, interrupt: Boolean, rate: Float, pitch: Float) {
        coroutineScope.launch {
            try {
                voiceService.speak(text, interrupt, rate, pitch)
            } catch (e: Exception) {
                Log.e(TAG, "TTS播放失败", e)
            }
        }
    }
    
    // ===== 发送当前语音并继续 =====
    fun sendCurrentUtteranceAndContinue() {
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
    
    // ===== 启动语音捕获 =====
    fun startVoiceCapture() {
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
            }
        }
    }
    
    // ===== 停止语音捕获 =====
    fun stopVoiceCapture(isCancel: Boolean) {
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
                    aiMessage = "长按下方麦克风开始说话"
                } else {
                    isProcessingSpeech = true
                    aiMessage = "识别中..."
                    speechService.stopRecognition()

                    // 设置一个备用超时
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
                            }
                            accumulatedText = ""
                            latestPartialText = ""
                        }
                    }
                }
            }
        }
    }
    
    // ===== 进入波浪模式（温和启动） =====
    fun enterWaveMode() {
        coroutineScope.launch {
            if (hasFocus) {
                isRecording = true
                userMessage = ""
                accumulatedText = ""
                latestPartialText = ""
                aiMessage = "正在聆听..."
                
                speechService.startRecognition(
                    languageCode = "zh-CN",
                    continuousMode = true,
                    partialResults = true
                )

                isWaveActive = true
                showBottomControls = false
            } else {
                aiMessage = "无法开始录音，无法获取焦点"
            }
        }
    }
    
    // ===== 退出波浪模式 =====
    fun exitWaveMode() {
        stopVoiceCapture(true)
        isWaveActive = false
        showBottomControls = true
    }
    
    // ===== 处理语音识别结果 =====
    fun handleRecognitionResult(resultText: String, isFinal: Boolean) {
        if (isRecording) {
            if (resultText.isNotBlank()) {
                // Barge-in: If the user starts speaking, stop the AI's TTS output.
                if (userMessage.isBlank()) {
                    coroutineScope.launch {
                        voiceService.stop()
                    }
                }

                if (latestPartialText.isNotEmpty() && !resultText.startsWith(latestPartialText)) {
                    if (accumulatedText.isNotEmpty()) {
                        accumulatedText += "。"
                    }
                    accumulatedText += latestPartialText
                }
                latestPartialText = resultText

                // 在波浪模式下，重置静默超时
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
            if (isFinal) {
                timeoutJob?.cancel()
                isProcessingSpeech = false

                var finalText = accumulatedText
                val finalSegment = resultText

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
                }
                accumulatedText = ""
                latestPartialText = ""
            }
        }
    }
    
    // ===== 初始化 =====
    suspend fun initialize() {
        isRecording = false
        isProcessingSpeech = false
        userMessage = ""
        accumulatedText = ""
        latestPartialText = ""
        aiMessage = "长按下方麦克风开始说话"
        isInitialLoad.value = true
        timeoutJob?.cancel()
        silenceTimeoutJob?.cancel()
        isWaveActive = false
        showBottomControls = true
        isEditMode = false
        editableText = ""

        // 初始化语音服务
        try {
            speechService.initialize()
            voiceService.initialize()
        } catch (e: Exception) {
            Log.e(TAG, "初始化语音服务失败", e)
        }

        // 请求输入法焦点
        val composeView = floatContext.chatService?.getComposeView()
        if (composeView != null) {
            composeView.requestFocus()
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
    
    // ===== 清理资源 =====
    fun cleanup() {
        timeoutJob?.cancel()
        silenceTimeoutJob?.cancel()
        
        floatContext.chatService?.getComposeView()?.let { view ->
            inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
            Log.d(TAG, "组件销毁时释放输入法焦点")
        }
        
        coroutineScope.launch {
            speechService.cancelRecognition()
            voiceService.stop()
        }
    }
    
    // ===== 编辑模式相关 =====
    fun enterEditMode(text: String) {
        isRecording = false
        coroutineScope.launch {
            speechService.cancelRecognition()
        }
        editableText = text
        isEditMode = true
        aiMessage = "编辑您的消息"
    }
    
    fun exitEditMode() {
        isEditMode = false
        editableText = ""
        aiMessage = "长按下方麦克风开始说话"
    }
    
    fun sendEditedMessage() {
        if (editableText.isNotBlank()) {
            floatContext.onSendMessage?.invoke(editableText, PromptFunctionType.VOICE)
            isEditMode = false
            editableText = ""
            aiMessage = "思考中..."
            // 注意：不更新 userMessage，保持显示之前的录音文本
        }
    }
}

/**
 * 创建并记住 ViewModel 实例
 */
@Composable
fun rememberFloatingFullscreenModeViewModel(
    context: Context,
    floatContext: FloatContext,
    coroutineScope: CoroutineScope
): FloatingFullscreenModeViewModel {
    // 不要将 floatContext 作为 key，因为它的属性（如 messages）会频繁变化
    // 我们希望 ViewModel 在整个组件生命周期内保持稳定
    return remember(context) {
        FloatingFullscreenModeViewModel(context, floatContext, coroutineScope)
    }
}

