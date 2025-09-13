package com.ai.assistance.operit.ui.features.toolbox.screens.terminal.service

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.ai.assistance.operit.terminal.service.TerminalStateParcelable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model.TerminalLine
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model.TerminalSession

/**
 * 终端客户端管理器
 * 提供更高级的API，管理本地终端会话状态与远程服务的同步
 */
class TerminalClientManager private constructor() {
    
    companion object {
        private const val TAG = "TerminalClientManager"
        
        @Volatile
        private var INSTANCE: TerminalClientManager? = null
        
        fun getInstance(): TerminalClientManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TerminalClientManager().also { INSTANCE = it }
            }
        }
    }
    
    // 服务客户端
    private var serviceClient: TerminalServiceClient? = null
    
    // 本地会话管理
    private val _localSessions = mutableStateListOf<TerminalSession>()
    val localSessions: List<TerminalSession> = _localSessions
    
    private val _currentSessionId = mutableStateOf<String?>(null)
    val currentSessionId = _currentSessionId
    
    // 连接状态
    val isServiceConnected: StateFlow<Boolean>?
        get() = serviceClient?.isConnected
    
    // 远程状态
    val remoteTerminalState: StateFlow<TerminalStateParcelable?>?
        get() = serviceClient?.terminalState
    
    /**
     * 初始化客户端管理器
     */
    fun initialize(context: Context, scope: CoroutineScope) {
        if (serviceClient == null) {
            serviceClient = TerminalServiceClient(context)
            
                         // 监听远程状态变化
            scope.launch {
                serviceClient?.terminalState?.collect { remoteState ->
                    remoteState?.let { state ->
                        syncWithRemoteState(state)
                    }
                }
            }
        }
    }
    
    /**
     * 连接到远程终端服务
     */
    fun connectToService(): Boolean {
        return serviceClient?.connect() ?: false
    }
    
    /**
     * 断开与远程终端服务的连接
     */
    fun disconnectFromService() {
        serviceClient?.disconnect()
    }
    
    /**
     * 创建新会话
     */
    fun createSession(name: String = "Terminal ${_localSessions.size + 1}"): String? {
        // 如果连接到服务，使用远程会话
        serviceClient?.let { client ->
            if (client.isConnected.value) {
                return client.createSession()
            }
        }
        
        // 否则创建本地会话
        val session = TerminalSession(name = name)
        _localSessions.add(session)
        
        // 如果是第一个会话，设为当前会话
        if (_localSessions.size == 1) {
            _currentSessionId.value = session.id
        }
        
        return session.id
    }
    
    /**
     * 切换会话
     */
    fun switchToSession(sessionId: String): Boolean {
        // 如果连接到服务，使用远程操作
        serviceClient?.let { client ->
            if (client.isConnected.value) {
                return client.switchToSession(sessionId)
            }
        }
        
        // 否则切换本地会话
        if (_localSessions.any { it.id == sessionId }) {
            _currentSessionId.value = sessionId
            return true
        }
        
        return false
    }
    
    /**
     * 关闭会话
     */
    fun closeSession(sessionId: String): Boolean {
        // 如果连接到服务，使用远程操作
        serviceClient?.let { client ->
            if (client.isConnected.value) {
                return client.closeSession(sessionId)
            }
        }
        
        // 否则关闭本地会话
        val sessionIndex = _localSessions.indexOfFirst { it.id == sessionId }
        if (sessionIndex != -1) {
            _localSessions.removeAt(sessionIndex)
            
            // 如果关闭的是当前会话，选择新的当前会话
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = if (_localSessions.isNotEmpty()) {
                    val newIndex = (sessionIndex - 1).coerceAtLeast(0)
                    _localSessions[newIndex].id
                } else {
                    null
                }
            }
            return true
        }
        
        return false
    }
    
    /**
     * 发送命令
     */
    fun sendCommand(command: String): Boolean {
        // 如果连接到服务，使用远程操作
        serviceClient?.let { client ->
            if (client.isConnected.value) {
                return client.sendCommand(command)
            }
        }
        
        // 否则在本地会话中执行
        getCurrentSession()?.let { session ->
            // 这里应该调用本地的命令执行逻辑
            // 但由于这需要Context和CoroutineScope，暂时只添加到历史
            session.commandHistory.add(
                TerminalLine.Input(command, session.getPrompt())
            )
            return true
        }
        
        return false
    }
    
    /**
     * 发送中断信号
     */
    fun sendInterruptSignal(): Boolean {
        return serviceClient?.sendInterruptSignal() ?: false
    }
    
    /**
     * 获取当前会话
     */
    fun getCurrentSession(): TerminalSession? {
        val currentId = _currentSessionId.value ?: return null
        return _localSessions.find { it.id == currentId }
    }
    
    /**
     * 获取所有会话
     */
    fun getAllSessions(): List<TerminalSession> {
        return _localSessions.toList()
    }
    
    /**
     * 获取会话数量
     */
    fun getSessionCount(): Int {
        return _localSessions.size
    }
    
    /**
     * 与远程状态同步
     */
    private fun syncWithRemoteState(remoteState: TerminalStateParcelable) {
        Log.d(TAG, "同步远程状态: ${remoteState.sessions.size} 个会话")
        
        // 清空本地会话
        _localSessions.clear()
        
        // 从远程状态重建本地会话
        remoteState.sessions.forEach { sessionData ->
            val session = TerminalSession(
                id = sessionData.sessionId,
                name = sessionData.sessionName,
                initialDirectory = sessionData.currentDirectory
            )
            
            // 设置用户
            session.currentUser = sessionData.currentUser
            
            // 重建命令历史
            sessionData.commandHistory.forEach { historyItem ->
                session.commandHistory.add(
                    TerminalLine.Input(historyItem.command, "$ ")
                )
                if (historyItem.output.isNotEmpty()) {
                    session.commandHistory.add(
                        TerminalLine.Output(historyItem.output)
                    )
                }
            }
            
            _localSessions.add(session)
        }
        
        // 设置当前会话
        _currentSessionId.value = remoteState.currentSessionId
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        disconnectFromService()
        _localSessions.clear()
        _currentSessionId.value = null
        serviceClient = null
        INSTANCE = null
    }
} 