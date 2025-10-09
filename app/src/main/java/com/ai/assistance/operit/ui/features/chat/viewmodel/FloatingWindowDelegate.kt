package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.toSerializable
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.ui.floating.FloatingMode
import com.ai.assistance.operit.ui.permissions.ToolCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 委托类，负责管理悬浮窗交互 */
class FloatingWindowDelegate(
    private val context: Context,
    private val viewModelScope: CoroutineScope,
    private val onMessageReceived: (String, PromptFunctionType) -> Unit,
    private val onAttachmentRequested: (String) -> Unit,
    private val onAttachmentRemoveRequested: (String) -> Unit,
    private val onCancelMessageRequested: () -> Unit,
    private val inputProcessingState: StateFlow<InputProcessingState>,
    private val chatHistoryFlow: StateFlow<List<ChatMessage>>? = null // 新增：聊天历史流
) {
    companion object {
        private const val TAG = "FloatingWindowDelegate"
    }

    // 悬浮窗状态
    private val _isFloatingMode = MutableStateFlow(false)
    val isFloatingMode: StateFlow<Boolean> = _isFloatingMode.asStateFlow()

    // 悬浮窗服务
    private var floatingService: FloatingChatService? = null

    // 服务连接
    private val serviceConnection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val binder = service as FloatingChatService.LocalBinder
                    floatingService = binder.getService()
                    // 设置回调，允许服务通知委托关闭
                    binder.setCloseCallback {
                        closeFloatingWindow()
                    }
                    // 设置消息收集
                    setupMessageCollection()
                    // 订阅聊天历史更新
                    setupChatHistoryCollection()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    floatingService = null
                }
            }

    init {
        // 不再需要注册广播接收器
        setupInputStateCollection()
    }

    /** 切换悬浮窗模式 */
    fun toggleFloatingMode(colorScheme: ColorScheme? = null, typography: Typography? = null) {
        val newMode = !_isFloatingMode.value

        if (newMode) {
            _isFloatingMode.value = true
            // 启动并绑定服务
            val intent = Intent(context, FloatingChatService::class.java)
            colorScheme?.let {
                intent.putExtra("COLOR_SCHEME", it.toSerializable())
            }
            typography?.let {
                intent.putExtra("TYPOGRAPHY", it.toSerializable())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            // 统一调用关闭逻辑，确保服务被正确关闭
            floatingService?.onClose()
        }
    }

    /**
     * 启动悬浮窗并指定一个初始模式
     */
    fun launchInMode(
            mode: FloatingMode,
            colorScheme: ColorScheme? = null,
            typography: Typography? = null
    ) {
        if (_isFloatingMode.value && floatingService != null) {
            // 如果服务已在运行，直接切换模式
            floatingService?.switchToMode(mode)
            Log.d(TAG, "悬浮窗已在运行，直接切换到模式: $mode")
            return
        }

        _isFloatingMode.value = true

        val intent = Intent(context, FloatingChatService::class.java)
        // 添加初始模式参数
        intent.putExtra("INITIAL_MODE", mode.name)

        colorScheme?.let {
            intent.putExtra("COLOR_SCHEME", it.toSerializable())
        }
        typography?.let {
            intent.putExtra("TYPOGRAPHY", it.toSerializable())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * 由服务回调或用户操作调用，用于关闭悬浮窗并更新状态
     */
    private fun closeFloatingWindow() {
        if (_isFloatingMode.value) {
            _isFloatingMode.value = false
            // 停止并解绑服务
            try {
                context.unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "服务可能已解绑: ${e.message}")
            }
            floatingService = null
        }
    }

    private fun setupInputStateCollection() {
        viewModelScope.launch {
            inputProcessingState.collect { state ->
                val isUiToolExecuting = state is InputProcessingState.ExecutingTool &&
                        state.category == ToolCategory.UI_AUTOMATION

                // Update UI busy state directly on the window state
                // floatingService?.windowState?.isUiBusy?.value = isUiToolExecuting

                if (isUiToolExecuting) {
                    Log.d(TAG, "UI tool executing, disabling window interaction.")
                    floatingService?.setWindowInteraction(false)
                } else {
                    Log.d(TAG, "State is ${state::class.simpleName}, enabling window interaction.")
                    floatingService?.setWindowInteraction(true)
                }
            }
        }
    }

    /** 设置消息收集 */
    private fun setupMessageCollection() {
        floatingService?.let { service ->
            // 收集消息
            viewModelScope.launch {
                try {
                    service.messageToSend.collect { messagePair ->
                        Log.d(TAG, "从悬浮窗接收到消息: ${messagePair.first}，模式: ${messagePair.second}")
                        // 处理消息
                        onMessageReceived(messagePair.first, messagePair.second)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "从悬浮窗收集消息时出错", e)
                }
            }
            
            // 收集取消消息请求
            viewModelScope.launch {
                try {
                    service.cancelMessageRequest.collect {
                        Log.d(TAG, "从悬浮窗接收到取消消息请求")
                        // 处理取消消息请求
                        onCancelMessageRequested()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "从悬浮窗收集取消消息请求时出错", e)
                }
            }

            // 收集附件请求
            viewModelScope.launch {
                try {
                    service.attachmentRequest.collect { request ->
                        Log.d(TAG, "从悬浮窗接收到附件请求: $request")
                        // 处理附件请求
                        onAttachmentRequested(request)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "从悬浮窗收集附件请求时出错", e)
                }
            }

            // 收集附件删除请求
            viewModelScope.launch {
                try {
                    service.attachmentRemoveRequest.collect { filePath ->
                        Log.d(TAG, "从悬浮窗接收到附件删除请求: $filePath")
                        // 处理附件删除请求
                        onAttachmentRemoveRequested(filePath)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "从悬浮窗收集附件删除请求时出错", e)
                }
            }
        }
    }

    /** 设置聊天历史收集 - 订阅ChatHistoryDelegate的chatHistory流 */
    private fun setupChatHistoryCollection() {
        chatHistoryFlow?.let { flow ->
            viewModelScope.launch {
                try {
                    // 先立即同步当前的消息历史（服务刚连接时）
                    val currentMessages = flow.value
                    if (currentMessages.isNotEmpty()) {
                        Log.d(TAG, "悬浮窗服务连接，立即同步当前消息: ${currentMessages.size} 条")
                        floatingService?.updateChatMessages(currentMessages)
                    }
                    
                    // 然后订阅后续的更新
                    flow.collect { messages ->
                        // 只在悬浮窗模式激活时同步消息
                        if (_isFloatingMode.value) {
                            Log.d(TAG, "从ChatHistoryDelegate收到消息更新: ${messages.size} 条")
                            floatingService?.updateChatMessages(messages)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "收集聊天历史时出错", e)
                }
            }
        } ?: Log.w(TAG, "chatHistoryFlow为空，无法订阅聊天历史更新")
    }

    /** 更新悬浮窗消息 */
    fun updateFloatingWindowMessages(messages: List<ChatMessage>) {
        Log.d(TAG, "更新悬浮窗消息: ${messages.size} 条. 最后一条消息的 stream is null: ${messages.lastOrNull()?.contentStream == null}")
        floatingService?.updateChatMessages(messages)
    }

    /** 更新悬浮窗附件 */
    fun updateFloatingWindowAttachments(attachments: List<AttachmentInfo>) {
        floatingService?.let { service ->
            Log.d(TAG, "更新悬浮窗附件: ${attachments.size}项")
            service.updateAttachments(attachments)
        }
    }

    /** 清理资源 */
    fun cleanup() {
        // 解绑服务
        if (floatingService != null) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "在清理时解绑服务失败", e)
            }
        }
    }
}
