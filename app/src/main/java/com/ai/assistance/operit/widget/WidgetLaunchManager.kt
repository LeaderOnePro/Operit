package com.ai.assistance.operit.widget

import com.ai.assistance.operit.ui.floating.FloatingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Widget启动管理器
 * 
 * 用于在Widget和MainActivity之间传递启动请求。
 * 当Widget启动MainActivity时，会设置待处理的启动请求。
 * MainActivity启动后，会检查并处理这个请求。
 */
object WidgetLaunchManager {
    private val _pendingLaunchRequest = MutableStateFlow<FloatingMode?>(null)
    val pendingLaunchRequest: StateFlow<FloatingMode?> = _pendingLaunchRequest.asStateFlow()
    
    /**
     * 设置待处理的启动请求
     */
    fun setPendingLaunch(mode: FloatingMode) {
        _pendingLaunchRequest.value = mode
    }
    
    /**
     * 清除待处理的启动请求（通常在处理完成后调用）
     */
    fun clearPendingLaunch() {
        _pendingLaunchRequest.value = null
    }
    
    /**
     * 检查是否有待处理的启动请求
     */
    fun hasPendingLaunch(): Boolean = _pendingLaunchRequest.value != null
}

