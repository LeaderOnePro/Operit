package com.ai.assistance.operit.ui.features.toolbox.screens.terminal.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.ai.assistance.operit.terminal.service.ITerminalService
import com.ai.assistance.operit.terminal.service.ITerminalCallback
import com.ai.assistance.operit.terminal.service.TerminalStateParcelable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 终端服务客户端
 * 负责与终端服务进行AIDL通信，管理连接状态和回调
 */
class TerminalServiceClient(private val context: Context) {
    
    companion object {
        private const val TAG = "TerminalServiceClient"
        private const val SERVICE_PACKAGE = "com.ai.assistance.operit"
        private const val SERVICE_CLASS = "com.ai.assistance.operit.terminal.TerminalService"
    }
    
    // 服务连接状态
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // 终端状态
    private val _terminalState = MutableStateFlow<TerminalStateParcelable?>(null)
    val terminalState: StateFlow<TerminalStateParcelable?> = _terminalState.asStateFlow()
    
    // AIDL服务接口
    private var terminalService: ITerminalService? = null
    
    // 服务连接
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "终端服务已连接")
            terminalService = ITerminalService.Stub.asInterface(service)
            _isConnected.value = true
            
            // 注册回调
            registerCallback()
            
            // 请求初始状态
            requestStateUpdate()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "终端服务已断开连接")
            terminalService = null
            _isConnected.value = false
        }
    }
    
    // 终端回调实现
    private val terminalCallback = object : ITerminalCallback.Stub() {
        override fun onStateUpdated(state: TerminalStateParcelable?) {
            Log.d(TAG, "收到终端状态更新: ${state?.sessions?.size ?: 0} 个会话")
            _terminalState.value = state
        }
    }
    
    /**
     * 连接到终端服务
     */
    fun connect(): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(SERVICE_PACKAGE, SERVICE_CLASS)
            }
            
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                Log.e(TAG, "无法绑定到终端服务")
            }
            bound
        } catch (e: Exception) {
            Log.e(TAG, "连接终端服务时出错", e)
            false
        }
    }
    
    /**
     * 断开与终端服务的连接
     */
    fun disconnect() {
        try {
            // 取消注册回调
            unregisterCallback()
            
            // 解绑服务
            context.unbindService(serviceConnection)
            _isConnected.value = false
        } catch (e: Exception) {
            Log.e(TAG, "断开终端服务连接时出错", e)
        }
    }
    
    /**
     * 创建新的终端会话
     */
    fun createSession(): String? {
        return try {
            terminalService?.createSession()
        } catch (e: RemoteException) {
            Log.e(TAG, "创建会话时出错", e)
            null
        }
    }
    
    /**
     * 切换到指定会话
     */
    fun switchToSession(sessionId: String): Boolean {
        return try {
            terminalService?.switchToSession(sessionId)
            true
        } catch (e: RemoteException) {
            Log.e(TAG, "切换会话时出错", e)
            false
        }
    }
    
    /**
     * 关闭指定会话
     */
    fun closeSession(sessionId: String): Boolean {
        return try {
            terminalService?.closeSession(sessionId)
            true
        } catch (e: RemoteException) {
            Log.e(TAG, "关闭会话时出错", e)
            false
        }
    }
    
    /**
     * 发送命令到当前会话
     */
    fun sendCommand(command: String): Boolean {
        return try {
            terminalService?.sendCommand(command)
            true
        } catch (e: RemoteException) {
            Log.e(TAG, "发送命令时出错", e)
            false
        }
    }
    
    /**
     * 发送中断信号 (Ctrl+C)
     */
    fun sendInterruptSignal(): Boolean {
        return try {
            terminalService?.sendInterruptSignal()
            true
        } catch (e: RemoteException) {
            Log.e(TAG, "发送中断信号时出错", e)
            false
        }
    }
    
    /**
     * 获取会话列表
     */
    fun getSessionList(): List<String>? {
        return try {
            terminalService?.sessionList
        } catch (e: RemoteException) {
            Log.e(TAG, "获取会话列表时出错", e)
            null
        }
    }
    
    /**
     * 获取当前会话ID
     */
    fun getCurrentSessionId(): String? {
        return try {
            terminalService?.currentSessionId
        } catch (e: RemoteException) {
            Log.e(TAG, "获取当前会话ID时出错", e)
            null
        }
    }
    
    /**
     * 请求状态更新
     */
    private fun requestStateUpdate() {
        try {
            terminalService?.requestStateUpdate()
        } catch (e: RemoteException) {
            Log.e(TAG, "请求状态更新时出错", e)
        }
    }
    
    /**
     * 注册回调
     */
    private fun registerCallback() {
        try {
            terminalService?.registerCallback(terminalCallback)
        } catch (e: RemoteException) {
            Log.e(TAG, "注册回调时出错", e)
        }
    }
    
    /**
     * 取消注册回调
     */
    private fun unregisterCallback() {
        try {
            terminalService?.unregisterCallback(terminalCallback)
        } catch (e: RemoteException) {
            Log.e(TAG, "取消注册回调时出错", e)
        }
    }
} 