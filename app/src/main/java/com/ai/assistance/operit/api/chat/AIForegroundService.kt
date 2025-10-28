package com.ai.assistance.operit.api.chat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.data.preferences.ApiPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.InputStream

/** 前台服务，用于在AI进行长时间处理时保持应用活跃，防止被系统杀死。 该服务不执行实际工作，仅通过显示一个持久通知来提升应用的进程优先级。 */
class AIForegroundService : Service() {

    companion object {
        private const val TAG = "AIForegroundService"
        private const val NOTIFICATION_ID = 1
        private const val REPLY_NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "AI_SERVICE_CHANNEL"
        private const val CHANNEL_NAME = "AI Service"

        // 静态标志，用于从外部检查服务是否正在运行
        val isRunning = java.util.concurrent.atomic.AtomicBoolean(false)
        
        // Intent extras keys
        const val EXTRA_CHARACTER_NAME = "extra_character_name"
        const val EXTRA_REPLY_CONTENT = "extra_reply_content"
        const val EXTRA_AVATAR_URI = "extra_avatar_uri"
    }
    
    // 存储通知信息
    private var characterName: String? = null
    private var replyContent: String? = null
    private var avatarUri: String? = null

    override fun onCreate() {
        super.onCreate()
        isRunning.set(true)
        Log.d(TAG, "AI 前台服务创建。")
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "AI 前台服务已启动。")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 从Intent中提取通知信息
        intent?.let {
            characterName = it.getStringExtra(EXTRA_CHARACTER_NAME)
            replyContent = it.getStringExtra(EXTRA_REPLY_CONTENT)
            avatarUri = it.getStringExtra(EXTRA_AVATAR_URI)
            Log.d(TAG, "收到通知数据 - 角色: $characterName, 内容长度: ${replyContent?.length}, 头像: $avatarUri")
        }
        
        // 返回 START_NOT_STICKY 表示如果服务被杀死，系统不需要尝试重启它。
        // 因为服务的生命周期由 EnhancedAIService 精确控制。
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.set(false)
        Log.d(TAG, "AI 前台服务已销毁。")
        
        // 检查是否需要发送回复完成通知
        sendReplyNotificationIfEnabled()
    }

    override fun onBind(intent: Intent?): IBinder? {
        // 该服务是启动服务，不提供绑定功能。
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel =
                    NotificationChannel(
                                    CHANNEL_ID,
                                    CHANNEL_NAME,
                                    NotificationManager.IMPORTANCE_LOW // 低重要性，避免打扰用户
                            )
                            .apply {
                                description = "Keeps the AI assistant running in the background."
                            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        // 为了简单起见，使用一个安卓内置图标。
        // 在实际项目中，应替换为应用的自定义图标。
        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("AI 助手")
                .setContentText("正在处理您的请求...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true) // 使通知不可被用户清除
                .build()
    }
    
    /**
     * 如果用户启用了回复通知，则发送AI回复完成通知
     */
    private fun sendReplyNotificationIfEnabled() {
        try {
            Log.d(TAG, "检查是否需要发送回复通知...")
            
            // 检查应用是否在前台
            val isAppInForeground = ActivityLifecycleManager.getCurrentActivity() != null
            if (isAppInForeground) {
                Log.d(TAG, "应用在前台，无需发送通知")
                return
            }
            
            // 检查用户是否启用了回复通知
            val apiPreferences = ApiPreferences.getInstance(applicationContext)
            val enableReplyNotification = runBlocking {
                apiPreferences.enableReplyNotificationFlow.first()
            }
            
            if (!enableReplyNotification) {
                Log.d(TAG, "回复通知已禁用，跳过发送")
                return
            }
            
            Log.d(TAG, "准备发送AI回复通知...")
            
            // 创建点击通知后打开应用的Intent
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )
            
            // 构建通知
            val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(characterName ?: getString(R.string.notification_ai_reply_title))
                .setContentText(replyContent?.take(100) ?: getString(R.string.notification_ai_reply_content))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true) // 点击后自动消失
            
            // 如果有完整内容，使用BigTextStyle显示更多文本
            if (!replyContent.isNullOrEmpty()) {
                notificationBuilder.setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(replyContent)
                        .setBigContentTitle(characterName ?: getString(R.string.notification_ai_reply_title))
                )
            }
            
            // 如果有头像，设置大图标
            val avatarUriString = avatarUri
            if (!avatarUriString.isNullOrEmpty()) {
                try {
                    val bitmap = loadBitmapFromUri(avatarUriString)
                    if (bitmap != null) {
                        notificationBuilder.setLargeIcon(bitmap)
                        Log.d(TAG, "成功加载头像到通知")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "加载头像失败: ${e.message}", e)
                }
            }
            
            val notification = notificationBuilder.build()
            
            // 发送通知
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(REPLY_NOTIFICATION_ID, notification)
            Log.d(TAG, "AI回复通知已发送 (ID: $REPLY_NOTIFICATION_ID)")
        } catch (e: Exception) {
            Log.e(TAG, "发送AI回复通知失败: ${e.message}", e)
        }
    }
    
    /**
     * 从URI加载Bitmap
     */
    private fun loadBitmapFromUri(uriString: String): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "从URI加载Bitmap失败: ${e.message}", e)
            null
        }
    }
}
