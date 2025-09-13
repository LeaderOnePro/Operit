package com.ai.assistance.operit.ui.features.toolbox.screens.terminal.service

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model.TerminalSessionManager

/**
 * 终端服务集成工具类
 * 提供现有终端功能与新AIDL服务的集成
 */
object TerminalServiceIntegration {
    
    private const val TAG = "TerminalServiceIntegration"
    
    /**
     * 初始化终端服务集成
     * 尝试连接到AIDL服务，如果连接失败则使用本地会话管理
     */
    fun initializeTerminalService(context: Context, scope: CoroutineScope): TerminalClientManager {
        val clientManager = TerminalClientManager.getInstance()
        
        // 初始化客户端管理器
        clientManager.initialize(context, scope)
        
        // 尝试连接到服务
        val connected = clientManager.connectToService()
        Log.d(TAG, "终端服务连接状态: $connected")
        
        return clientManager
    }
    
    /**
     * 检查是否应该使用远程服务
     */
    fun shouldUseRemoteService(clientManager: TerminalClientManager): Boolean {
        return clientManager.isServiceConnected?.value == true
    }
    
    /**
     * 获取混合会话管理器
     * 根据服务连接状态返回适当的会话管理器
     */
    fun getHybridSessionManager(clientManager: TerminalClientManager): Any {
        return if (shouldUseRemoteService(clientManager)) {
            clientManager // 使用客户端管理器
        } else {
            TerminalSessionManager // 使用本地会话管理器
        }
    }
    
    /**
     * 创建会话（混合模式）
     */
    fun createSession(
        clientManager: TerminalClientManager, 
        sessionName: String = "Terminal"
    ): String? {
        return if (shouldUseRemoteService(clientManager)) {
            clientManager.createSession(sessionName)
        } else {
            TerminalSessionManager.createSession(sessionName).id
        }
    }
    
    /**
     * 切换会话（混合模式）
     */
    fun switchToSession(clientManager: TerminalClientManager, sessionId: String): Boolean {
        return if (shouldUseRemoteService(clientManager)) {
            clientManager.switchToSession(sessionId)
        } else {
            TerminalSessionManager.switchSession(sessionId)
            true
        }
    }
    
    /**
     * 关闭会话（混合模式）
     */
    fun closeSession(clientManager: TerminalClientManager, sessionId: String): Boolean {
        return if (shouldUseRemoteService(clientManager)) {
            clientManager.closeSession(sessionId)
        } else {
            TerminalSessionManager.closeSession(sessionId)
            true
        }
    }
    
    /**
     * 获取当前会话（混合模式）
     */
    fun getCurrentSession(clientManager: TerminalClientManager) = 
        if (shouldUseRemoteService(clientManager)) {
            clientManager.getCurrentSession()
        } else {
            TerminalSessionManager.getActiveSession()
        }
    
    /**
     * 获取所有会话（混合模式）
     */
    fun getAllSessions(clientManager: TerminalClientManager) = 
        if (shouldUseRemoteService(clientManager)) {
            clientManager.getAllSessions()
        } else {
            TerminalSessionManager.sessions.toList()
        }
    
    /**
     * 获取会话数量（混合模式）
     */
    fun getSessionCount(clientManager: TerminalClientManager): Int {
        return if (shouldUseRemoteService(clientManager)) {
            clientManager.getSessionCount()
        } else {
            TerminalSessionManager.getSessionCount()
        }
    }
    
    /**
     * 发送命令（混合模式）
     */
    fun sendCommand(clientManager: TerminalClientManager, command: String): Boolean {
        return if (shouldUseRemoteService(clientManager)) {
            clientManager.sendCommand(command)
        } else {
            // 本地模式下，需要通过现有的TerminalSessionManager执行
            // 这里只是标记，实际执行仍需要Context和CoroutineScope
            false
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup(clientManager: TerminalClientManager) {
        clientManager.cleanup()
    }
}

/**
 * Compose辅助函数：管理终端服务生命周期
 */
@Composable
fun rememberTerminalServiceManager(context: Context): TerminalClientManager {
    val scope = rememberCoroutineScope()
    
    val clientManager = remember {
        TerminalServiceIntegration.initializeTerminalService(context, scope)
    }
    
    // 监听连接状态变化
    val isConnected by (clientManager.isServiceConnected?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(false) })
    
    // 在组件销毁时清理资源
    DisposableEffect(clientManager) {
        onDispose {
            TerminalServiceIntegration.cleanup(clientManager)
        }
    }
    
    // 记录连接状态变化
    LaunchedEffect(isConnected) {
        Log.d("TerminalServiceIntegration", "服务连接状态变化: $isConnected")
    }
    
    return clientManager
} 