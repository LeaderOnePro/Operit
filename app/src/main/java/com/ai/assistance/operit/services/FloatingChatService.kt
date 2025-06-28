package com.ai.assistance.operit.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.View
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.SerializableColorScheme
import com.ai.assistance.operit.data.model.SerializableTypography
import com.ai.assistance.operit.data.model.toComposeColorScheme
import com.ai.assistance.operit.data.model.toComposeTypography
import com.ai.assistance.operit.data.preferences.PromptFunctionType
import com.ai.assistance.operit.services.floating.FloatingWindowCallback
import com.ai.assistance.operit.services.floating.FloatingWindowManager
import com.ai.assistance.operit.services.floating.FloatingWindowState
import com.ai.assistance.operit.ui.features.chat.attachments.AttachmentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

class FloatingChatService : Service(), FloatingWindowCallback {
    private val TAG = "FloatingChatService"
    private val binder = LocalBinder()

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "floating_chat_channel"

    private lateinit var windowState: FloatingWindowState
    private lateinit var windowManager: FloatingWindowManager
    private lateinit var prefs: SharedPreferences
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var lifecycleOwner: ServiceLifecycleOwner
    private val chatMessages = mutableStateOf<List<ChatMessage>>(emptyList())
    private val attachments = mutableStateOf<List<AttachmentInfo>>(emptyList())
    private lateinit var attachmentManager: AttachmentManager

    private var lastCrashTime = 0L
    private var crashCount = 0
    private val defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val customExceptionHandler =
            Thread.UncaughtExceptionHandler { thread, throwable ->
                handleServiceCrash(thread, throwable)
            }

    private val colorScheme = mutableStateOf<ColorScheme?>(null)
    private val typography = mutableStateOf<Typography?>(null)
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _attachmentRequest = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val attachmentRequest: SharedFlow<String>
        get() = _attachmentRequest

    private val _attachmentRemoveRequest = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val attachmentRemoveRequest: SharedFlow<String> = _attachmentRemoveRequest

    private val _messageToSend =
            MutableSharedFlow<Pair<String, PromptFunctionType>>(extraBufferCapacity = 1)
    val messageToSend: SharedFlow<Pair<String, PromptFunctionType>>
        get() = _messageToSend

    private val _cancelMessageRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cancelMessageRequest: SharedFlow<Unit>
        get() = _cancelMessageRequest

    inner class LocalBinder : Binder() {
        private var closeCallback: (() -> Unit)? = null
        fun getService(): FloatingChatService = this@FloatingChatService
        fun setCloseCallback(callback: () -> Unit) {
            this.closeCallback = callback
        }
        fun notifyClose() {
            closeCallback?.invoke()
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun handleServiceCrash(thread: Thread, throwable: Throwable) {
        try {
            Log.e(TAG, "Service crashed: ${throwable.message}", throwable)
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCrashTime > 60000) {
                crashCount = 0
            }
            lastCrashTime = currentTime
            crashCount++

            if (crashCount > 3) {
                Log.e(TAG, "Too many crashes in short time, stopping service")
                prefs.edit().putBoolean("service_disabled_due_to_crashes", true).apply()
                stopSelf()
                return
            }

            saveState()
            val intent = Intent(applicationContext, FloatingChatService::class.java)
            intent.setPackage(packageName)
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling crash", e)
        } finally {
            defaultExceptionHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        Thread.setDefaultUncaughtExceptionHandler(customExceptionHandler)

        prefs = getSharedPreferences("floating_chat_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("service_disabled_due_to_crashes", false)) {
            Log.w(TAG, "Service was disabled due to frequent crashes")
            stopSelf()
            return
        }

        try {
            acquireWakeLock()
            
            lifecycleOwner = ServiceLifecycleOwner()
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            attachmentManager = AttachmentManager(this, AIToolHandler.getInstance(this))
            windowState = FloatingWindowState(this)
            windowManager =
                    FloatingWindowManager(
                            this,
                            windowState,
                            lifecycleOwner,
                            lifecycleOwner,
                            lifecycleOwner,
                            this
                    )
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            stopSelf()
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock =
                        powerManager.newWakeLock(
                                PowerManager.PARTIAL_WAKE_LOCK,
                                "OperitApp:FloatingChatServiceWakeLock"
                        )
                wakeLock?.setReferenceCounted(false)
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(10 * 60 * 1000L)
                Log.d(TAG, "WakeLock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "AI助手悬浮窗"
            val descriptionText = "显示AI助手的悬浮窗服务"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel =
                    NotificationChannel(CHANNEL_ID, name, importance).apply {
                        description = descriptionText
                        setShowBadge(false)
                    }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification() =
            NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("AI助手悬浮窗")
                    .setContentText("AI助手正在后台运行")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(true)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setContentIntent(getPendingIntent())
                    .build()

    private fun getPendingIntent(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        return PendingIntent.getActivity(
                this,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE
                else 0
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        try {
            acquireWakeLock()
            if (intent?.hasExtra("CHAT_MESSAGES") == true) {
                val messagesArray = intent.getParcelableArrayExtra("CHAT_MESSAGES")
                if (messagesArray != null) {
                    val messages = mutableListOf<ChatMessage>()
                    messagesArray.forEach { if (it is ChatMessage) messages.add(it) }
                    updateChatMessages(messages)
                }
            }
            if (intent?.hasExtra("COLOR_SCHEME") == true) {
                val serializableColorScheme =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                    "COLOR_SCHEME",
                                    SerializableColorScheme::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra<SerializableColorScheme>("COLOR_SCHEME")
                        }
                serializableColorScheme?.let { colorScheme.value = it.toComposeColorScheme() }
            }
            if (intent?.hasExtra("TYPOGRAPHY") == true) {
                val serializableTypography =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(
                                    "TYPOGRAPHY",
                                    SerializableTypography::class.java
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra<SerializableTypography>("TYPOGRAPHY")
                        }
                serializableTypography?.let { typography.value = it.toComposeTypography() }
            }
            windowManager.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved")
        val restartServiceIntent =
                Intent(applicationContext, this.javaClass).apply { setPackage(packageName) }
        startService(restartServiceIntent)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.d(TAG, "onLowMemory: 系统内存不足")
        saveState()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "onTrimMemory: level=$level")
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE
        ) {
            saveState()
        }
    }

    private fun handleAttachmentRequest(request: String) {
        Log.d(TAG, "Attachment request received: $request")
        serviceScope.launch {
            try {
                _attachmentRequest.emit(request)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling attachment request", e)
            }
        }
    }

    fun removeAttachment(filePath: String) {
        serviceScope.launch {
            try {
                attachments.value = attachments.value.filterNot { it.filePath == filePath }
                _attachmentRemoveRequest.emit(filePath)
                Log.d(TAG, "Attachment removed: $filePath, remaining: ${attachments.value.size}")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing attachment", e)
            }
        }
    }

    fun updateAttachments(newAttachments: List<AttachmentInfo>) {
        serviceScope.launch { attachments.value = newAttachments }
    }

    fun updateChatMessages(messages: List<ChatMessage>) {
        serviceScope.launch {
            Log.d(
                    TAG,
                    "服务收到消息更新: ${messages.size} 条. 最后一条消息的 stream is null: ${messages.lastOrNull()?.contentStream == null}"
            )
            chatMessages.value = messages
        }
    }

    override fun onDestroy() {
        try {
            releaseWakeLock()
            
            serviceScope.cancel()
            saveState()
            super.onDestroy()
            Log.d(TAG, "onDestroy")
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            windowManager.destroy()
            Thread.setDefaultUncaughtExceptionHandler(defaultExceptionHandler)
            prefs.edit().putInt("view_creation_retry", 0).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy", e)
        }
    }

    override fun onClose() {
        Log.d(TAG, "Close request from window manager")
        binder.notifyClose()
        stopSelf()
    }

    override fun onSendMessage(message: String, promptType: PromptFunctionType) {
        serviceScope.launch { _messageToSend.tryEmit(Pair(message, promptType)) }
    }

    override fun onCancelMessage() {
        serviceScope.launch { _cancelMessageRequest.tryEmit(Unit) }
    }

    override fun onAttachmentRequest(request: String) {
        handleAttachmentRequest(request)
    }

    override fun onRemoveAttachment(filePath: String) {
        removeAttachment(filePath)
    }

    override fun getMessages(): List<ChatMessage> = chatMessages.value

    override fun getAttachments(): List<AttachmentInfo> = attachments.value

    override fun getColorScheme(): ColorScheme? = colorScheme.value

    override fun getTypography(): Typography? = typography.value

    override fun saveState() {
        windowState.saveState()
    }

    /**
     * 获取悬浮窗的ComposeView实例，用于申请输入法焦点
     * @return ComposeView? 当前悬浮窗的ComposeView实例，如果未初始化则返回null
     */
    fun getComposeView(): View? {
        return if (::windowManager.isInitialized) {
            windowManager.getComposeView()
        } else {
            null
        }
    }
}
