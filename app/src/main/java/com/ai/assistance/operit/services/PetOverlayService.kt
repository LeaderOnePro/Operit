package com.ai.assistance.operit.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.speech.SpeechService
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.core.avatar.common.state.AvatarEmotion
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.impl.AvatarRendererFactoryImpl
import com.ai.assistance.operit.core.avatar.impl.webp.control.WebPAvatarController
import com.ai.assistance.operit.core.avatar.impl.webp.model.WebPAvatarModel
import com.ai.assistance.operit.core.avatar.common.model.AvatarModel
import com.ai.assistance.operit.core.avatar.common.view.AvatarRendererFactory
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.services.floating.FloatingWindowState
import com.ai.assistance.operit.ui.features.pet.PetOverlay
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

/**
 * Minimal desktop-pet foreground overlay service.
 * Provides a small always-on-top Compose UI with mic button, short replies and TTS.
 */
class PetOverlayService : Service() {
    private val TAG = "PetOverlayService"

    private val CHANNEL_ID = "pet_overlay_channel"
    private val NOTIFICATION_ID = 12001

    // Window
    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private lateinit var windowState: FloatingWindowState
    private var overlayParams: WindowManager.LayoutParams? = null

    // Lifecycle bridge for Compose
    private lateinit var lifecycleOwner: ServiceLifecycleOwner

    // Speech services
    private var stt: SpeechService? = null
    // No TTS in desktop pet mode

    // AI
    private lateinit var ai: EnhancedAIService
    private val history = mutableListOf<Pair<String, String>>()

    // UI states (simple, internal to service)
    private var isListening by mutableStateOf(false)
    private var isThinking by mutableStateOf(false)
    private var petText by mutableStateOf("嗨，我是Operit娘~")
    // 最近一次交互（用户/AI）的时间戳，用于超时回退
    @Volatile private var lastActivityAt: Long = System.currentTimeMillis()
    private var isCollapsed by mutableStateOf(false)
    private var showTextInput by mutableStateOf(false)
    private var textInputValue by mutableStateOf("")
    @Volatile private var sttSessionId: Long = 0L

    // Avatar System
    private var avatarModel by mutableStateOf<AvatarModel?>(null)
    private var avatarController by mutableStateOf<AvatarController?>(null)
    private lateinit var avatarRendererFactory: AvatarRendererFactory

    // STT watchdog state
    @Volatile private var lastRecognizedText: String = ""
    @Volatile private var lastTextUpdateAt: Long = 0L
    @Volatile private var silenceStartAt: Long = 0L
    @Volatile private var hasDispatchedQuery: Boolean = false
    private var silenceWatchJob: kotlinx.coroutines.Job? = null
    private var inactivityJob: kotlinx.coroutines.Job? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            ai = EnhancedAIService.getInstance(this)
        } catch (e: Exception) {
            Log.e(TAG, "Init EnhancedAIService failed", e)
        }
        // 根据当前角色卡名称设置开场白
        try {
            val characterCardManager = CharacterCardManager.getInstance(this)
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val activeCard = characterCardManager.activeCharacterCardFlow.first()
                    val name = activeCard.name.ifBlank { "" }
                    withContext(Dispatchers.Main) {
                        val prefix = "嗨，我是Operit娘"
                        petText = if (name.isNotBlank()) "$prefix$name~" else "$prefix~"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load active character card name: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "CharacterCardManager init failed: ${e.message}")
        }

        lifecycleOwner = ServiceLifecycleOwner()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowState = FloatingWindowState(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // Initialize Avatar System
        avatarRendererFactory = AvatarRendererFactoryImpl()
        val model = WebPAvatarModel(
            id = "pet",
            name = "operit",
            basePath = "pets/emoji",
            emotionToFileMap = mapOf(
                AvatarEmotion.IDLE to "anime-smile-transparent.webp",
                AvatarEmotion.LISTENING to "anime-smile-talking-transparent.webp",
                AvatarEmotion.THINKING to "anime-smile-talking-transparent.webp",
                AvatarEmotion.HAPPY to "anime-happy-transparent.webp",
                AvatarEmotion.SAD to "anime-cry-transparent.webp"
            )
        )
        avatarModel = model
        avatarController = WebPAvatarController(model).apply {
            setEmotion(AvatarEmotion.IDLE)
        }

        showOverlay()

        // 启动闲置检测任务：超过1分钟无交互且未在听写/思考中，自动回到idle表情
        inactivityJob = serviceScope.launch(Dispatchers.Default) {
            try {
                while (true) {
                    kotlinx.coroutines.delay(5000)
                    val now = System.currentTimeMillis()
                    val inactive = now - lastActivityAt
                    if (!isListening && !isThinking && inactive > 60_000L) {
                        withContext(Dispatchers.Main) {
                            avatarController?.setEmotion(AvatarEmotion.IDLE)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        removeOverlay()
        serviceScope.cancel()
        try { stt?.shutdown() } catch (_: Exception) {}
        try { inactivityJob?.cancel() } catch (_: Exception) {}
    }

    private fun buildNotification() =
        androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.pet_service_notification_title))
            .setContentText(getString(R.string.pet_service_notification_text))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(getLaunchPendingIntent())
            .build()

    private fun getLaunchPendingIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.pet_service_notification_title)
            val descriptionText = getString(R.string.pet_service_notification_text)
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun showOverlay() {
        if (composeView != null) return
        composeView = ComposeView(this).apply {
            // Wire lifecycle owners so Compose viewModels work if needed
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                PetOverlay(
                    text = petText,
                    isListening = isListening,
                    isThinking = isThinking,
                    onMicClick = { toggleListening() },
                    onClose = { stopSelf() },
                    onDrag = { dx, dy, end -> handleDrag(dx, dy, end) },
                    avatarModel = avatarModel,
                    avatarController = avatarController,
                    avatarRendererFactory = avatarRendererFactory,
                    showTextInput = showTextInput,
                    textInputValue = textInputValue,
                    onTextInputClick = {
                        showTextInput = !showTextInput
                        setOverlayFocusable(showTextInput)
                    },
                    onTextInputChange = { textInputValue = it },
                    onSendText = {
                        val msg = textInputValue.trim()
                        if (msg.isNotEmpty() && !isThinking) {
                            textInputValue = ""
                            showTextInput = false
                            setOverlayFocusable(false)
                            stopStt() // 确保不在听写中
                            askAi(msg)
                        }
                    },
                    isCollapsed = isCollapsed,
                    onCollapseToggle = { isCollapsed = !isCollapsed }
                )
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = windowState.x
            y = windowState.y
        }
        try {
            windowManager.addView(composeView, params)
            overlayParams = params
            Log.d(TAG, "Overlay added. Initial position=(${params.x}, ${params.y})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
        }
    }

    private fun setOverlayFocusable(focusable: Boolean) {
        val v = composeView ?: return
        val p = overlayParams ?: return
        val oldFlags = p.flags
        val newFlags = if (focusable) {
            // 允许获得焦点，从而弹出输入法
            (oldFlags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv())
        } else {
            // 恢复为不获取焦点，避免拦截系统输入
            (oldFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        if (newFlags != oldFlags) {
            p.flags = newFlags
            try {
                windowManager.updateViewLayout(v, p)
                Log.d(TAG, "Overlay focusable -> $focusable (flags: ${oldFlags.toString(16)} -> ${newFlags.toString(16)})")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update overlay focusable: ${e.message}")
            }
        }
    }

    private fun removeOverlay() {
        composeView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        composeView = null
        overlayParams = null
    }

    private fun handleDrag(dx: Float, dy: Float, end: Boolean) {
        val p = overlayParams ?: return
        val v = composeView ?: return
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        if (!end) {
            var newX = p.x + dx.toInt()
            var newY = p.y + dy.toInt()
            val vw = if (v.width > 0) v.width else 1
            val vh = if (v.height > 0) v.height else 1
            newX = newX.coerceIn(0, screenW - vw)
            newY = newY.coerceIn(0, screenH - vh)
            if (newX != p.x || newY != p.y) {
                p.x = newX
                p.y = newY
                windowState.x = p.x
                windowState.y = p.y
                try {
                    windowManager.updateViewLayout(v, p)
                    Log.d(TAG, "Drag move -> (${p.x}, ${p.y}) dx=${dx}, dy=${dy}")
                } catch (e: Exception) {
                    Log.w(TAG, "updateViewLayout failed during drag: ${e.message}")
                }
            }
        } else {
            // Drag end: commit position and reset accumulator
            try { windowState.saveState() } catch (_: Exception) {}
            Log.d(TAG, "Drag end -> saved position (${p.x}, ${p.y})")
        }
    }

    private fun toggleListening() {
        Log.d(TAG, "toggleListening called. isListening=$isListening")
        if (!isListening) {
            startStt()
        } else {
            // 用户主动停止，如果已有非空识别文本且尚未派发，则带着文本停止并派发
            val pending = lastRecognizedText.takeIf { it.isNotBlank() && !hasDispatchedQuery }
            stopStt(userFinalText = pending)
        }
    }

    private fun startStt() {
        if (stt == null) stt = SpeechServiceFactory.getInstance(this)
        serviceScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "startStt: initializing STT...")
                stt?.initialize()
                withContext(Dispatchers.Main) {
                    isListening = true
                    avatarController?.setEmotion(AvatarEmotion.LISTENING)
                }
                // New STT session id to ignore stale emissions
                sttSessionId += 1
                val sessionId = sttSessionId
                Log.d(TAG, "startStt: new sessionId=$sessionId")
                // Clear any stale text state before launching flows
                lastRecognizedText = ""
                lastTextUpdateAt = 0L
                hasDispatchedQuery = false
                lastActivityAt = System.currentTimeMillis()

                stt?.startRecognition(languageCode = "zh-CN", continuousMode = false, partialResults = true)
                Log.d(TAG, "startStt: startRecognition returned. sessionId=$sessionId")
                Log.d(TAG, "startStt: recognition started (partialResults=true)")
                // Collect results on Main to update UI
                launch { collectSttResults(sessionId) }
                // Start silence watchdog to auto-finalize
                launch { startSilenceWatchdog(sessionId) }
                // TTS disabled in desktop pet mode
            } catch (e: Exception) {
                Log.e(TAG, "STT start failed", e)
                withContext(Dispatchers.Main) { isListening = false }
            }
        }
    }

    private suspend fun collectSttResults(expectedSessionId: Long) {
        val service = stt ?: return
        serviceScope.launch {
            // Use collect instead of collectLatest so final emissions triggered by stopStt()
            // don't cancel the in-flight handling that dispatches the AI query.
            service.recognitionResultFlow.collect { result ->
                if (expectedSessionId != sttSessionId) return@collect
                if (result.text.isNotBlank()) {
                    petText = result.text
                    lastRecognizedText = result.text
                    lastTextUpdateAt = System.currentTimeMillis()
                    Log.d(TAG, "STT partial: '${result.text}' (final=${result.isFinal})")
                    withContext(Dispatchers.Main) {
                        avatarController?.setEmotion(AvatarEmotion.LISTENING)
                    }
                    lastActivityAt = System.currentTimeMillis()
                }
                if (expectedSessionId == sttSessionId && !hasDispatchedQuery && result.isFinal && result.text.isNotBlank()) {
                    // Stop listening and ask AI
                    stopStt()
                    hasDispatchedQuery = true
                    Log.d(TAG, "STT final: dispatch to AI -> '${result.text}'")
                    withContext(Dispatchers.Main) {
                        avatarController?.setEmotion(inferEmotionFromText(result.text))
                    }
                    lastActivityAt = System.currentTimeMillis()
                    askAi(result.text)
                }
            }
        }
    }

    private fun stopStt(userFinalText: String? = null) {
        val service = stt ?: return
        serviceScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "stopStt: stopping recognition... currentSessionId=$sttSessionId")
                service.stopRecognition()
            } catch (e: Exception) {
                Log.w(TAG, "stopStt: stopRecognition failed: ${e.message}")
            }
            withContext(Dispatchers.Main) {
                isListening = false
                avatarController?.setEmotion(AvatarEmotion.IDLE)
            }
            // Cancel watchdog
            silenceWatchJob?.cancel()
            silenceWatchJob = null

            // 如果是用户主动停止，并且有文本尚未派发，则在停止后立即派发
            if (userFinalText != null && userFinalText.isNotBlank() && !hasDispatchedQuery) {
                Log.d(TAG, "stopStt: user-stop dispatching final text -> '$userFinalText'")
                hasDispatchedQuery = true
                askAi(userFinalText)
            }
            lastActivityAt = System.currentTimeMillis()
        }
    }

    /**
     * Watch volume and text stability; if user stopped speaking and we have
     * stable non-empty text but engine didn’t mark final, auto-dispatch.
     */
    private suspend fun startSilenceWatchdog(expectedSessionId: Long) {
        val service = stt ?: return
        hasDispatchedQuery = false
        lastRecognizedText = ""
        lastTextUpdateAt = 0L
        silenceStartAt = 0L

        silenceWatchJob = serviceScope.launch(Dispatchers.Default) {
            try {
                // Poll every 120ms, low overhead
                while (isListening) {
                    if (expectedSessionId != sttSessionId) break
                    val now = System.currentTimeMillis()
                    val vol = try { service.volumeLevelFlow.value } catch (e: Exception) { 0f }
                    val low = vol < 0.05f
                    if (low) {
                        if (silenceStartAt == 0L) silenceStartAt = now
                    } else {
                        silenceStartAt = 0L
                    }

                    val stableFor = if (lastTextUpdateAt > 0) now - lastTextUpdateAt else 0L
                    val silenceFor = if (silenceStartAt > 0) now - silenceStartAt else 0L

                    // Conditions to auto-finalize: some text, stable >= 900ms and silence >= 800ms
                    if (expectedSessionId == sttSessionId && !hasDispatchedQuery && lastRecognizedText.isNotBlank() &&
                        stableFor >= 900 && silenceFor >= 800) {
                        Log.d(TAG, "Silence watchdog auto-dispatching query: '$lastRecognizedText'")
                        stopStt()
                        hasDispatchedQuery = true
                        askAi(lastRecognizedText)
                        break
                    }
                    if ((now % 1000L) < 130L) {
                        val msg = String.format(
                            "Watchdog: vol=%.2f stableFor=%d silenceFor=%d text='%s'",
                            vol,
                            stableFor,
                            silenceFor,
                            lastRecognizedText
                        )
                        Log.d(TAG, msg)
                    }
                    kotlinx.coroutines.delay(120)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Silence watchdog stopped: ${e.message}")
            }
        }
    }

    // No TTS in desktop pet mode

    private fun askAi(userText: String) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                // Ensure AI service instance (guard against lazy init failure)
                if (!::ai.isInitialized) {
                    try { ai = EnhancedAIService.getInstance(this@PetOverlayService) } catch (e: Exception) {
                        Log.e(TAG, "EnhancedAIService init failed in askAi", e)
                        withContext(Dispatchers.Main) {
                            isThinking = false
                            petText = getString(R.string.error_occurred_simple)
                        }
                        return@launch
                    }
                }
                withContext(Dispatchers.Main) {
                    isThinking = true
                    petText = "…"
                    avatarController?.setEmotion(AvatarEmotion.THINKING)
                }
                Log.d(TAG, "askAi: streaming request. text='${userText}' history=${history.size}")
                // Stream response
                val stream = ai.sendMessage(
                    message = userText,
                    chatHistory = history,
                    workspacePath = null,
                    functionType = FunctionType.CHAT,
                    promptFunctionType = PromptFunctionType.DESKTOP_PET,
                    enableThinking = false,
                    thinkingGuidance = false,
                    enableMemoryAttachment = false,
                    maxTokens = 0,
                    tokenUsageThreshold = 1.0,
                    onNonFatalError = { err ->
                        // 将错误显示在桌宠气泡中，但不强制切换表情；留给闲置计时器或下一轮mood处理
                        val msg = formatFriendlyError(err)
                        Log.w(TAG, "AI error for pet: $msg (raw=$err)")
                        withContext(Dispatchers.Main) {
                            isThinking = false
                            petText = msg
                        }
                        lastActivityAt = System.currentTimeMillis()
                    },
                    customSystemPromptTemplate = null,
                    isSubTask = false
                )

                val buffer = StringBuilder()
                stream.collect { chunk ->
                    buffer.append(chunk)
                    val sanitized = stripXmlLikeTags(buffer.toString())
                    withContext(Dispatchers.Main) { petText = sanitized }
                    lastActivityAt = System.currentTimeMillis()
                }

                val finalTextRaw = buffer.toString().trim()
                lastActivityAt = System.currentTimeMillis()
                if (finalTextRaw.isEmpty()) {
                    // 没有收到任何内容，通常是错误或被取消，UI已通过回调更新
                    history.add("user" to userText)
                    withContext(Dispatchers.Main) { isThinking = false }
                    return@launch
                }
                val parsedMood = extractMoodTag(finalTextRaw)
                val finalText = stripXmlLikeTags(finalTextRaw)
                Log.d(TAG, "askAi: stream completed. replyLen=${finalText.length}")
                history.add("user" to userText)
                history.add("assistant" to finalText)

                withContext(Dispatchers.Main) {
                    isThinking = false
                    // 最终显示为去除<mood>标签后的文本
                    petText = finalText
                    // Prefer explicit mood -> set PetEmotion accordingly; else infer heuristically.
                    val finalEmotion = parsedMood?.let { moodToEmotion(it) } ?: inferEmotionFromText(finalText)
                    avatarController?.setEmotion(finalEmotion)
                }
            } catch (e: Exception) {
                Log.e(TAG, "askAi error", e)
                withContext(Dispatchers.Main) {
                    isThinking = false
                    petText = formatFriendlyError(e.message ?: getString(R.string.error_occurred_simple))
                }
                lastActivityAt = System.currentTimeMillis()
            }
        }
    }

    // Basic heuristic mapping from text to emotion
    private fun inferEmotionFromText(text: String): AvatarEmotion {
        val t = text.lowercase()
        // Chinese & emoji keywords
        val happyKeywords = listOf("开心", "高兴", "不错", "棒", "太好了", "😀", "🙂", "😊", "😄", "赞")
        val angryKeywords = listOf("生气", "愤怒", "气死", "讨厌", "糟糕", "😡", "怒")
        val cryKeywords = listOf("难过", "伤心", "沮丧", "忧伤", "哭", "😭", "😢")
        val shyKeywords = listOf("害羞", "羞", "脸红", "不好意思", "///")

        fun containsAny(keys: List<String>): Boolean = keys.any { t.contains(it) || text.contains(it) }

        return when {
            containsAny(happyKeywords) -> AvatarEmotion.HAPPY
            containsAny(angryKeywords) -> AvatarEmotion.SAD // No ANGRY, use SAD
            containsAny(cryKeywords) -> AvatarEmotion.SAD
            containsAny(shyKeywords) -> AvatarEmotion.CONFUSED // No SHY, use CONFUSED
            else -> AvatarEmotion.IDLE
        }
    }

    // ===== Mood tag parsing and asset mapping for video avatar =====
    private enum class Mood { ANGRY, HAPPY, SHY, AOJIAO, CRY }

    private fun extractMoodTag(text: String): Mood? {
        return try {
            val regex = Regex("<mood>([^<]+)</mood>", RegexOption.IGNORE_CASE)
            val all = regex.findAll(text).toList()
            if (all.isEmpty()) return null
            val raw = all.last().groupValues[1].trim().lowercase()
            when (raw) {
                "angry" -> Mood.ANGRY
                "happy" -> Mood.HAPPY
                "shy" -> Mood.SHY
                "aojiao" -> Mood.AOJIAO
                "cry" -> Mood.CRY
                else -> null // Only allow the specified moods
            }
        } catch (_: Exception) { null }
    }

    // 更通用的标签清理：移除所有形如 <tag>...</tag> 的成对标签及其内容，
    // 以及自闭合或悬空的标签，确保对话文本中不出现任意尖括号标签及其内容。
    private fun stripXmlLikeTags(text: String): String {
        var s = text
        // 先多轮移除成对标签及其内容（按相同标签名反向引用），处理常见嵌套场景
        val paired = Regex(
            pattern = "<([A-Za-z][A-Za-z0-9:_-]*)(\\s[^>]*)?>[\\s\\S]*?</\\1>",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        repeat(5) { _ ->
            val updated = s.replace(paired, "")
            if (updated == s) return@repeat
            s = updated
        }
        // 移除自闭合标签，如 <br/>、<img .../>
        s = s.replace(
            Regex("<[A-Za-z][A-Za-z0-9:_-]*(\\s[^>]*)?/\\s*>", RegexOption.IGNORE_CASE),
            ""
        )
        // 移除任意残留的起始或结束标签
        s = s.replace(
            Regex("</?[^>]+>", RegexOption.IGNORE_CASE),
            ""
        )
        return s.trim()
    }

    // ===== Error presentation helpers =====
    private fun formatFriendlyError(raw: String?): String {
        val s = (raw ?: "").trim()
        val core = extractErrorMessageFromJson(s)
        val low = core.lowercase()
        return when {
            // 402 或余额不足
            s.contains("402") ||
                    low.contains("insufficient balance") ||
                    low.contains("insufficient_funds") -> "余额不足，请检查账户额度或更换模型"
            // 鉴权问题
            s.contains("401") || s.contains("403") ||
                    low.contains("invalid api key") ||
                    low.contains("auth") -> "鉴权失败，请检查 API Key 或接口地址"
            // 频率限制
            s.contains("429") || low.contains("rate limit") -> "请求过多，稍后再试"
            // 超时/网络
            low.contains("timeout") || low.contains("timed out") -> "网络超时，请检查网络连接"
            low.contains("unknownhost") || low.contains("unable to resolve host") -> "网络不可用或接口地址错误"
            // 其他
            else -> "发送失败：" + core.take(120)
        }
    }

    private fun extractErrorMessageFromJson(s: String): String {
        // 尝试从常见的 {"error":{"message":"..."}} 结构提取 message
        // 使用原始字符串，避免转义冲突
        val regex = Regex("""\"message\"\s*:\s*\"([^\"]+)\"""")
        val m = regex.find(s)
        return m?.groupValues?.getOrNull(1)?.ifBlank { s } ?: s
    }

    private fun moodToEmotion(mood: Mood): AvatarEmotion = when (mood) {
        Mood.ANGRY -> AvatarEmotion.SAD // No ANGRY
        Mood.HAPPY -> AvatarEmotion.HAPPY
        Mood.SHY -> AvatarEmotion.CONFUSED // No SHY
        Mood.AOJIAO -> AvatarEmotion.CONFUSED // No SHY
        Mood.CRY -> AvatarEmotion.SAD
    }
}
