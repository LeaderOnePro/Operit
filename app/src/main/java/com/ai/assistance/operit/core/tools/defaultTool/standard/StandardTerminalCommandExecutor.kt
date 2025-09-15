package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.core.tools.system.Terminal
import kotlinx.coroutines.*

/** 终端命令执行工具 - 非流式输出版本 执行终端命令并一次性收集全部输出后返回 */
class StandardTerminalCommandExecutor(private val context: Context) {

    private val TAG = "TerminalCommandExecutor"

    /** 执行指定的AI工具 */
    fun invoke(tool: AITool): ToolResult {
        return runBlocking {
            try {
                val command = tool.parameters.find { param -> param.name == "command" }?.value ?: ""
                val timeout =
                        tool.parameters
                                .find { param -> param.name == "timeout_ms" }
                                ?.value
                                ?.toLongOrNull()
                                ?: 30000L
                val withSessionId =
                        tool.parameters.find { param -> param.name == "session_id" }?.value

                // 获取终端管理器
                Log.d(TAG, "Getting TerminalManager instance")
                val terminal = Terminal.getInstance(context)
                
                // 确保已连接到终端服务
                Log.d(TAG, "Checking if terminal service is connected")
                if (!terminal.isConnected()) {
                    Log.d(TAG, "Terminal service not connected, initializing...")
                    val connected = terminal.initialize()
                    if (!connected) {
                        Log.e(TAG, "Failed to connect to terminal service")
                        return@runBlocking ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "无法连接到终端服务"
                        )
                    }
                }

                // 获取或创建会话
                val sessionId = if (!withSessionId.isNullOrEmpty()) {
                    withSessionId
                        } else {
                    // 创建新会话
                    terminal.createSession()
                        }

                if (sessionId == null) {
                    return@runBlocking ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "无法找到或创建终端会话"
                    )
                }

                // 执行命令并收集Flow输出
                val outputFlow = terminal.executeCommandFlow(sessionId, command)
                
                if (outputFlow != null) {
                    // 收集所有输出事件
                    val events = mutableListOf<String>()
                    var exitCode = 0
                    var hasCompleted = false
                    
                    // 使用withTimeout防止无限等待
                    try {
                        withTimeout(timeout) {
                            outputFlow.collect { event ->
                                if (event.outputChunk.isNotEmpty()) {
                                    events.add(event.outputChunk)
                                }
                                if (event.isCompleted) {
                                    exitCode = 0
                                    hasCompleted = true
                                }
                            }
                        }
                    } catch (e: TimeoutCancellationException) {
                        Log.w(TAG, "Command execution timed out after ${timeout}ms")
                        hasCompleted = true
                        exitCode = -1
                    }
                    
                    val fullOutput = events.joinToString("")
                    Log.d(TAG, "Command output collected: '$fullOutput', exitCode: $exitCode")
                    
                    // 成功执行
                    ToolResult(
                            toolName = tool.name,
                            success = hasCompleted && exitCode == 0,
                            result = TerminalCommandResultData(
                                    command = command,
                                    output = fullOutput,
                                    exitCode = exitCode,
                                    sessionId = sessionId
                            )
                    )
                } else {
                    // 命令执行失败
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "终端命令执行失败或超时"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "执行终端命令时出错", e)
                ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "执行终端命令时出错: ${e.message}"
                )
            }
        }
    }
}
