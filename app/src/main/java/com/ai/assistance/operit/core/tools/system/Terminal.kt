package com.ai.assistance.operit.core.tools.system

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.terminal.CommandExecutionEvent
import com.ai.assistance.operit.terminal.SessionDirectoryEvent
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.data.CommandHistoryItem
import com.ai.assistance.operit.terminal.data.TerminalState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect

/**
 * 终端管理器
 * 提供应用程序级别的终端服务管理和访问
 */
@RequiresApi(Build.VERSION_CODES.O)
class Terminal private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: Terminal? = null

        fun getInstance(context: Context): Terminal {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Terminal(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val TAG = "Terminal"
    }

    private val terminalManager = TerminalManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    // 从 TerminalManager 暴露状态和事件流
    val commandEvents: SharedFlow<CommandExecutionEvent> = terminalManager.commandExecutionEvents
    val directoryEvents: SharedFlow<SessionDirectoryEvent> = terminalManager.directoryChangeEvents
    val terminalState: StateFlow<TerminalState> = terminalManager.terminalState
    val sessions = terminalManager.sessions
    val currentSessionId = terminalManager.currentSessionId
    val commandHistory: Flow<List<CommandHistoryItem>> = terminalManager.commandHistory
    val currentDirectory = terminalManager.currentDirectory
    val isInteractiveMode = terminalManager.isInteractiveMode
    val interactivePrompt = terminalManager.interactivePrompt
    val isFullscreen = terminalManager.isFullscreen
    val screenContent = terminalManager.screenContent

    /**
     * 初始化终端管理器
     */
    suspend fun initialize(): Boolean {
        return terminalManager.initializeEnvironment()
    }

    /**
     * 销毁终端管理器
     */
    fun destroy() {
        terminalManager.cleanup()
    }

    /**
     * 创建新的终端会话 - 同步等待初始化完成
     */
    suspend fun createSession(title: String? = null): String {
        Log.d(TAG, "Creating new terminal session and waiting for initialization")
        val newSession = terminalManager.createNewSession(title)
        Log.d(TAG, "Session ${newSession.id} initialized successfully")
        return newSession.id
    }
    
    /**
     * 创建新的终端会话并等待初始化完成
     * @deprecated 使用 createSession 代替，现在所有会话创建都是同步的
     */
    suspend fun createSessionAndWait(title: String? = null): String? {
        return try {
            createSession(title)
        } catch (e: Exception) {
            Log.e(TAG, "Session creation failed", e)
            null
        }
    }
    
    /**
     * 切换到指定会话
     */
    fun switchToSession(sessionId: String) {
        terminalManager.switchToSession(sessionId)
    }

    /**
     * 关闭终端会话
     */
    fun closeSession(sessionId: String) {
        terminalManager.closeSession(sessionId)
    }

    /**
     * 执行命令并等待其完成（不切换当前会话）
     */
    suspend fun executeCommand(sessionId: String, command: String): String? {
        val deferred = CompletableDeferred<String>()
        val output = StringBuilder()
        
        // 生成命令ID
        val commandId = java.util.UUID.randomUUID().toString()
        
        val collectorReady = CompletableDeferred<Unit>()
        
        // 先开始订阅事件流，然后再发送命令
        val job = scope.launch {
            commandEvents
                .filter { it.sessionId == sessionId && it.commandId == commandId }
                .onStart { collectorReady.complete(Unit) } // 发出信号，表示已准备好收集
                .collect { event ->
                    output.append(event.outputChunk)
                    if (event.isCompleted) {
                        deferred.complete(output.toString())
                    }
                }
        }

        collectorReady.await() // 等待收集器准备就绪
        
        // 直接向指定会话发送命令，不切换当前会话
        terminalManager.sendCommandToSession(sessionId, command, commandId)

        val result = withTimeoutOrNull(300000) { // 5 分钟超时
            deferred.await()
        }
        
        job.cancel()
        
        return result
    }

    /**
     * 执行命令 - Flow版本
     * 返回命令执行过程中的所有事件，直到命令完成
     */
    fun executeCommandFlow(sessionId: String, command: String): Flow<CommandExecutionEvent> {
        terminalManager.switchToSession(sessionId)
        return channelFlow {
            val commandId = terminalManager.sendCommand(command)
            commandEvents
                .filter { it.sessionId == sessionId && it.commandId == commandId }
                .transformWhile { event ->
                    emit(event)
                    !event.isCompleted
                }
                .collect { sentEvent ->
                    send(sentEvent)
                }
        }
    }
    
    /**
     * 发送输入到当前会话
     */
    fun sendInput(sessionId: String, input: String) {
        terminalManager.switchToSession(sessionId)
        terminalManager.sendInput(input)
    }

    /**
     * 发送中断信号 (Ctrl+C)
     */
    fun sendInterruptSignal(sessionId: String) {
        terminalManager.switchToSession(sessionId)
        terminalManager.sendInterruptSignal()
    }

    /**
     * 检查服务是否已连接 (现在总是返回 true)
     */
    fun isConnected(): Boolean {
        return true
    }
} 