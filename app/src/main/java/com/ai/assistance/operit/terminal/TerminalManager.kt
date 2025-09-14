package com.ai.assistance.operit.terminal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * 终端管理器
 * 提供应用程序级别的终端服务管理和访问
 */
class TerminalManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var INSTANCE: TerminalManager? = null
        
        fun getInstance(context: Context): TerminalManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TerminalManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        private const val TAG = "TerminalManager"
        private const val SERVICE_PACKAGE = "com.ai.assistance.operit.terminal"
        private const val SERVICE_ACTION = "com.ai.assistance.operit.terminal.ITerminal"
        private val WAKEUP_URI = Uri.parse("content://com.ai.assistance.operit.terminal.wakeup")
    }

    private var terminalInterface: ITerminalService? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _commandEvents = MutableStateFlow<CommandExecutionEvent?>(null)
    val commandEvents: StateFlow<CommandExecutionEvent?> = _commandEvents.asStateFlow()
    
    private val _directoryEvents = MutableStateFlow<SessionDirectoryEvent?>(null)
    val directoryEvents: StateFlow<SessionDirectoryEvent?> = _directoryEvents.asStateFlow()

    private val commandCompletionNotifiers = ConcurrentHashMap<String, CompletableDeferred<String>>()

    private val terminalCallback = object : ITerminalCallback.Stub() {
        override fun onCommandExecutionUpdate(event: CommandExecutionEvent?) {
            event?.let {
                _commandEvents.value = it
                if (it.isCompleted) {
                    val key = "${it.sessionId}:${it.commandId}"
                    commandCompletionNotifiers[key]?.let { deferred ->
                        deferred.complete(it.outputChunk)
                        commandCompletionNotifiers.remove(key)
                    }
                }
            }
        }

        override fun onSessionDirectoryChanged(event: SessionDirectoryEvent?) {
            event?.let {
                _directoryEvents.value = it
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected")
            terminalInterface = ITerminalService.Stub.asInterface(service)
            _connectionState.value = true
            
            try {
                terminalInterface?.registerCallback(terminalCallback)
                scope.launch {
                    requestStateUpdate()
                }
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to register callback", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected")
            terminalInterface = null
            _connectionState.value = false
        }
    }

    /**
     * 初始化终端管理器
     */
    suspend fun initialize(): Boolean {
        return connect()
    }

    /**
     * 销毁终端管理器
     */
    fun destroy() {
        disconnect()
    }

    private suspend fun connect(): Boolean {
        if (_connectionState.value) {
            return true
        }

        val intent = Intent(SERVICE_ACTION).apply {
            setPackage(SERVICE_PACKAGE)
        }

        // 检查服务是否存在
        /* if (context.packageManager.resolveService(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            Log.e(TAG, "Terminal service not found. Is the terminal app ($SERVICE_PACKAGE) installed and the service exported with action ($SERVICE_ACTION)?")
            return false
        } */

        return withTimeoutOrNull(15000L) { // 15秒超时
            suspendCancellableCoroutine<Boolean> { continuation ->
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        Log.d(TAG, "Service connected")
                        terminalInterface = ITerminalService.Stub.asInterface(service)
                        _connectionState.value = true
                        
                        try {
                            terminalInterface?.registerCallback(terminalCallback)
                            scope.launch { requestStateUpdate() }
                            if (continuation.isActive) {
                                continuation.resume(true)
                            }
                        } catch (e: RemoteException) {
                            Log.e(TAG, "Failed to register callback", e)
                            if (continuation.isActive) {
                                continuation.resume(false)
                            }
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        Log.d(TAG, "Service disconnected")
                        terminalInterface = null
                        _connectionState.value = false
                        if (continuation.isActive) {
                            // This might be called if the service crashes, which is a failed connection.
                            continuation.resume(false)
                        }
                    }
                }

                var bound = false
                scope.launch {
                    try {
                        Log.d(TAG, "Attempting to wake up the service app via ContentProvider...")
                        // 使用ContentProvider唤醒目标应用进程
                        withContext(Dispatchers.IO) {
                            try {
                                context.contentResolver.query(WAKEUP_URI, null, null, null, null)?.use {
                                    // The query itself is enough to wake the app. We don't need to read data.
                                    // The 'use' block ensures the cursor is closed automatically.
                                    Log.d(TAG, "ContentProvider queried successfully.")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to query WakeUpProvider. The service app might not be installed or the provider is misconfigured.", e)
                                // We can still try to bind; maybe the app is already running.
                            }

                            Log.d(TAG, "Attempting to bind service...")
                            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to bind service", e)
                    }

                    if (!bound) {
                        Log.e(TAG, "bindService returned false. The service might not be running or is misconfigured.")
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    Log.d(TAG, "Connection attempt cancelled.")
                    // If we successfully bound, unbind
                    if (bound) {
                        try {
                            context.unbindService(connection)
                        } catch (e: IllegalArgumentException) {
                            Log.w(TAG, "Tried to unbind a service that was not registered.", e)
                        }
                    }
                }
            }
        } ?: false // Timeout returns null, so we convert it to false
    }

    private fun disconnect() {
        if (terminalInterface != null) {
            try {
                terminalInterface?.unregisterCallback(terminalCallback)
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to unregister state listener", e)
            }
        }
        
        if (_connectionState.value) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unbind service", e)
            }
        }

        terminalInterface = null
        _connectionState.value = false
    }

    /**
     * 创建新的终端会话
     */
    suspend fun createSession(): String? {
        return try {
            terminalInterface?.createSession()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to create session", e)
            null
        }
    }

    /**
     * 关闭终端会話
     */
    suspend fun closeSession(sessionId: String) {
        try {
            terminalInterface?.closeSession(sessionId)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to close session", e)
        }
    }

    /**
     * 执行命令
     */
    suspend fun executeCommand(sessionId: String, command: String): String? {
        if (terminalInterface == null) {
            Log.e(TAG, "Terminal service not connected")
            return null
        }
        return try {
            terminalInterface?.switchToSession(sessionId)
            
            val commandId = terminalInterface?.sendCommand(command) ?: return null
            
            val deferred = CompletableDeferred<String>()
            val key = "$sessionId:$commandId"
            commandCompletionNotifiers[key] = deferred
            
            val result = withTimeoutOrNull(300000) { // 5 minutes timeout
                deferred.await()
            }

            commandCompletionNotifiers.remove(key)
            
            result
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to execute command", e)
            null
        }
    }

    /**
     * 执行命令 - Flow版本
     * 返回命令执行过程中的所有事件，直到命令完成
     */
    suspend fun executeCommandFlow(sessionId: String, command: String): kotlinx.coroutines.flow.Flow<CommandExecutionEvent>? {
        if (terminalInterface == null) {
            Log.e(TAG, "Terminal service not connected")
            return null
        }
        
        return try {
            terminalInterface?.switchToSession(sessionId)
            val commandId = terminalInterface?.sendCommand(command) ?: return null
            
            commandEvents
                .filterNotNull()
                .filter { it.sessionId == sessionId && it.commandId == commandId }
                .takeWhile { !it.isCompleted }
                .onCompletion { 
                    commandEvents.value?.takeIf { 
                        it.sessionId == sessionId && it.commandId == commandId && it.isCompleted 
                    }?.let { emit(it) }
                }
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to execute command", e)
            null
        }
    }


    private suspend fun requestStateUpdate() {
        try {
            terminalInterface?.requestStateUpdate()
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to request state update", e)
        }
    }

    /**
     * 检查服务是否已连接
     */
    fun isConnected(): Boolean {
        return _connectionState.value && terminalInterface != null
    }
} 