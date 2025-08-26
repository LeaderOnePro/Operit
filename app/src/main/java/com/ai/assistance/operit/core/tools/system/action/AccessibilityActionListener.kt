package com.ai.assistance.operit.core.tools.system.action

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import com.ai.assistance.operit.provider.IAccessibilityEventCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 基于无障碍服务的UI操作监听器 实现ACCESSIBILITY权限级别的操作监听
 * 通过UIHierarchyManager与系统的无障碍服务进行通信，监听系统级的UI事件和用户操作
 */
class AccessibilityActionListener(private val context: Context) : ActionListener {
    companion object {
        private const val TAG = "AccessibilityActionListener"
    }

    private val isListening = AtomicBoolean(false)
    private var actionCallback: ((ActionListener.ActionEvent) -> Unit)? = null

    // AIDL回调的实现。当无障碍服务捕获到事件时，会通过这个回调通知我们
    private val remoteListener = object : IAccessibilityEventCallback.Stub() {
        override fun onAccessibilityEvent(event: AccessibilityEvent) {
            handleAccessibilityEvent(event)
        }
    }

    override fun getPermissionLevel(): AndroidPermissionLevel = AndroidPermissionLevel.ACCESSIBILITY

    override suspend fun isAvailable(): Boolean {
        // 使用UIHierarchyManager检查无障碍服务是否启用并连接
        return UIHierarchyManager.isAccessibilityServiceEnabled(context)
    }

    override suspend fun hasPermission(): ActionListener.PermissionStatus {
        return if (UIHierarchyManager.isAccessibilityServiceEnabled(context)) {
            ActionListener.PermissionStatus.granted()
        } else {
            ActionListener.PermissionStatus.denied("无障碍服务未启用")
        }
    }

    override fun initialize() {
        Log.d(TAG, "无障碍UI操作监听器已初始化")
    }

    override suspend fun requestPermission(onResult: (Boolean) -> Unit) {
        if (isAvailable()) {
            onResult(true)
            return
        }

        // 引导用户打开无障碍服务设置
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)

            // 由于无法知道用户是否启用了服务，返回false，让调用者自行处理后续检查
            onResult(false)
        } catch (e: Exception) {
            Log.e(TAG, "打开无障碍设置失败", e)
            onResult(false)
        }
    }

    override fun isListening(): Boolean = isListening.get()

    override suspend fun startListening(onAction: (ActionListener.ActionEvent) -> Unit): ActionListener.ListeningResult =
        withContext(Dispatchers.IO) {
            try {
                val permStatus = hasPermission()
                if (!permStatus.granted) {
                    return@withContext ActionListener.ListeningResult.failure(permStatus.reason)
                }

                if (!isListening.compareAndSet(false, true)) {
                    Log.w(TAG, "启动监听失败：已在监听中")
                    return@withContext ActionListener.ListeningResult.failure("已在监听中")
                }

                actionCallback = onAction

                // 通过UIHierarchyManager注册AIDL回调
                val registered = UIHierarchyManager.registerAccessibilityEventListener(context, remoteListener)
                if (registered) {
                    Log.d(TAG, "无障碍UI操作监听已启动")
                    ActionListener.ListeningResult.success("无障碍UI操作监听已启动")
                } else {
                    isListening.set(false)
                    actionCallback = null
                    Log.w(TAG, "通过UIHierarchyManager注册监听器失败")
                    ActionListener.ListeningResult.failure("注册无障碍事件监听器失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动无障碍UI操作监听失败", e)
                isListening.set(false)
                actionCallback = null
                ActionListener.ListeningResult.failure("启动失败: ${e.message}")
            }
        }

    override suspend fun stopListening(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isListening.compareAndSet(true, false)) {
                Log.d(TAG, "监听器未在运行，无需停止")
                return@withContext true
            }

            // 通过UIHierarchyManager注销AIDL回调
            UIHierarchyManager.unregisterAccessibilityEventListener(context, remoteListener)
            actionCallback = null

            Log.d(TAG, "无障碍UI操作监听已停止")
            true
        } catch (e: Exception) {
            Log.e(TAG, "停止无障碍UI操作监听失败", e)
            // Even if unregistering fails, we consider the listener stopped from our side.
            actionCallback = null
            false
        }
    }

    /**
     * 处理从远程无障碍服务通过AIDL回调传来的事件
     * @param event 无障碍事件
     */
    private fun handleAccessibilityEvent(event: AccessibilityEvent) {
        if (!isListening.get()) return

        val callback = actionCallback ?: return

        // 过滤掉不需要的事件类型，避免产生噪音
        // 2048 = TYPE_TOUCH_INTERACTION_START - 触摸交互开始事件，频繁触发
        if (event.eventType == 2048) {
            return
        }

        try {
            val actionType = when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_CLICKED -> ActionListener.ActionType.CLICK
                AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> ActionListener.ActionType.LONG_CLICK
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> ActionListener.ActionType.TEXT_INPUT
                AccessibilityEvent.TYPE_VIEW_SCROLLED -> ActionListener.ActionType.SCROLL
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> ActionListener.ActionType.SCREEN_CHANGE
                else -> ActionListener.ActionType.SYSTEM_EVENT
            }

            val elementInfo = ActionListener.ElementInfo(
                className = event.className?.toString(),
                text = event.text?.joinToString(" "),
                contentDescription = event.contentDescription?.toString(),
                packageName = event.packageName?.toString()
            )

            val actionEvent = ActionListener.ActionEvent(
                timestamp = event.eventTime,
                actionType = actionType,
                elementInfo = elementInfo,
                additionalData = mapOf(
                    "eventType" to event.eventType,
                    "source" to "accessibility_service"
                )
            )

            callback.invoke(actionEvent)
        } catch (e: Exception) {
            Log.e(TAG, "处理无障碍事件失败", e)
        }
    }
} 