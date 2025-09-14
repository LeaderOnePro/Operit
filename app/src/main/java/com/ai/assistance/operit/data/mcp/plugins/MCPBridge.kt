package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import android.os.Environment
import android.util.Log
import com.ai.assistance.operit.terminal.TerminalManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.UUID

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray

/**
 * MCPBridge - 用于与TCP桥接器通信的插件类 支持以下命令:
 * - spawn: 启动新的MCP服务
 * - shutdown: 关闭当前MCP服务
 * - describe: 获取MCP服务描述
 * - listtools: 列出所有可用工具
 * - toolcall: 调用特定工具
 * - ping: 健康检查
 * - status: 获取服务状态
 * - list: 列出已注册的MCP服务
 * - register: 注册新的MCP服务
 * - unregister: 取消注册MCP服务
 */
class MCPBridge private constructor(private val context: Context) {
    companion object {
        private const val TAG = "MCPBridge"
        private const val DEFAULT_HOST = "127.0.0.1"
        private const val DEFAULT_PORT = 8752
        private const val TERMUX_BRIDGE_PATH = "/data/data/com.termux/files/home/bridge"
        private var appContext: Context? = null
        
        @Volatile
        private var INSTANCE: MCPBridge? = null
        
        fun getInstance(context: Context): MCPBridge {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MCPBridge(context.applicationContext).also { 
                    INSTANCE = it
                    appContext = context.applicationContext
                }
            }
        }

        // 部署桥接器到Termux
        suspend fun deployBridge(context: Context): Boolean {
            appContext = context.applicationContext
            return withContext(Dispatchers.IO) {
                try {
                    // 1. 首先将桥接器从assets复制到Download/Operit/bridge目录
                    val downloadsDir =
                            Environment.getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS
                            )
                    val operitDir = File(downloadsDir, "Operit")
                    if (!operitDir.exists()) {
                        operitDir.mkdirs()
                    }

                    val publicBridgeDir = File(operitDir, "bridge")
                    if (!publicBridgeDir.exists()) {
                        publicBridgeDir.mkdirs()
                    }

                    // 复制index.js到公共目录
                    val inputStream = context.assets.open("bridge/index.js")
                    val indexJsContent = inputStream.bufferedReader().use { it.readText() }
                    val outputFile = File(publicBridgeDir, "index.js")
                    outputFile.writeText(indexJsContent)
                    inputStream.close()

                    // 创建package.json文件
                    val packageJsonContent =
                            """
                        {
                            "name": "mcp-tcp-bridge",
                            "version": "1.0.0",
                            "description": "将STDIO型MCP服务器桥接到TCP端口",
                            "main": "index.js",
                            "dependencies": {
                                "uuid": "^9.0.0",
                                "mcp-client": "^1.13.1"
                            }
                        }
                    """.trimIndent()
                    val packageJson = File(publicBridgeDir, "package.json")
                    packageJson.writeText(packageJsonContent)

                    Log.d(TAG, "桥接器文件已复制到公共目录: ${publicBridgeDir.absolutePath}")

                    // 2. 确保Termux目录存在并复制文件
                    // 获取终端管理器
                    val terminalManager = TerminalManager.getInstance(context)
                    
                    // 确保已连接到终端服务
                    if (!terminalManager.isConnected()) {
                        val connected = terminalManager.initialize()
                        if (!connected) {
                            Log.e(TAG, "无法连接到终端服务")
                            return@withContext false
                        }
                    }

                    // 创建会话
                    val sessionId = terminalManager.createSession()
                    if (sessionId == null) {
                        Log.e(TAG, "无法创建终端会话")
                        return@withContext false
                    }

                    // 以非阻塞方式创建目录并复制文件
                    val deployCommand =
                            """
                        mkdir -p $TERMUX_BRIDGE_PATH && 
                        cp -f ${outputFile.absolutePath} $TERMUX_BRIDGE_PATH/ && 
                        cp -f ${packageJson.absolutePath} $TERMUX_BRIDGE_PATH/ && 
                        cd $TERMUX_BRIDGE_PATH && 
                        ([ -d node_modules/uuid ] && [ -d node_modules/mcp-client ]) || npm install
                    """.trimIndent()

                    // 执行命令
                    terminalManager.executeCommand(sessionId, deployCommand)

                    // 等待命令执行完成
                    delay(5000) // 给命令执行时间

                        Log.d(TAG, "桥接器成功部署到Termux")
                    return@withContext true
                } catch (e: Exception) {
                    Log.e(TAG, "部署桥接器异常", e)
                    return@withContext false
                }
            }
        }

        // 在Termux中启动桥接器
        suspend fun startBridge(
                context: Context? = null,
                port: Int = DEFAULT_PORT,
                mcpCommand: String? = null,
                mcpArgs: List<String>? = null
        ): Boolean =
                withContext(Dispatchers.IO) {
                    try {
                        // 使用传入的context或保存的appContext
                        val ctx = context ?: appContext
                        if (ctx == null) {
                            Log.e(TAG, "没有可用的上下文，无法执行命令")
                            return@withContext false
                        }

                        // 首先检查桥接器是否已经在运行
                        val pingResult = ping(ctx)
                        if (pingResult != null) {
                            Log.d(TAG, "桥接器已经在运行，无需重新启动")
                            return@withContext true
                        }

                        // 获取终端管理器
                        val terminalManager = TerminalManager.getInstance(ctx)
                        
                        // 确保已连接到终端服务
                        if (!terminalManager.isConnected()) {
                            val connected = terminalManager.initialize()
                            if (!connected) {
                                Log.e(TAG, "无法连接到终端服务")
                                return@withContext false
                            }
                        }

                        // 创建会话
                        val sessionId = terminalManager.createSession()
                        if (sessionId == null) {
                            Log.e(TAG, "无法创建终端会话")
                            return@withContext false
                        }

                        // 清理日志文件
                        Log.d(TAG, "清理日志文件")

                        // 构建启动命令 - 使用后台方式运行
                        val command = StringBuilder("cd $TERMUX_BRIDGE_PATH && node index.js $port")
                        if (mcpCommand != null) {
                            command.append(" $mcpCommand")
                            if (mcpArgs != null && mcpArgs.isNotEmpty()) {
                                command.append(" ${mcpArgs.joinToString(" ")}")
                            }
                        }
                        command.append(" > bridge.log 2>&1 &")

                        Log.d(TAG, "发送启动命令: $command")

                        // 异步方式发送启动命令 - 不等待完成，因为它会作为后台进程一直运行
                        terminalManager.executeCommand(sessionId, command.toString())

                        // 等待一段时间让桥接器启动
                        Log.d(TAG, "等待桥接器启动...")
                        delay(2000)

                        // 验证桥接器是否成功启动 - 尝试三次
                        var isRunning = false
                        for (i in 1..3) {
                            val checkResult = ping(ctx)
                            if (checkResult != null) {
                                Log.d(TAG, "桥接器成功启动，ping响应: $checkResult")
                                isRunning = true
                                break
                            }
                            Log.d(TAG, "第${i}次尝试ping桥接器失败，等待1秒后重试")
                            delay(1000)
                        }

                        // 如果三次尝试后仍然无法ping通，检查日志
                        if (!isRunning) {
                            Log.e(TAG, "桥接器可能未成功启动，检查日志...")

                            // 异步读取日志而不阻塞
                            val logCmd = "tail -n 20 $TERMUX_BRIDGE_PATH/bridge.log"
                            terminalManager.executeCommand(sessionId, logCmd)
                        }

                        return@withContext isRunning
                    } catch (e: Exception) {
                        Log.e(TAG, "启动桥接器异常", e)
                        return@withContext false
                    }
                }

        // 简单的健康检查
        suspend fun ping(context: Context? = null): JSONObject? =
                withContext(Dispatchers.IO) {
                    try {
                        val command =
                                JSONObject().apply {
                                    put("command", "ping")
                                    put("id", UUID.randomUUID().toString())
                                }

                        return@withContext sendCommand(command)
                    } catch (e: Exception) {
                        Log.e(TAG, "Ping失败", e)
                        return@withContext null
                    }
                }

        // 发送命令到桥接器
        suspend fun sendCommand(
                command: JSONObject,
                host: String = DEFAULT_HOST,
                port: Int = DEFAULT_PORT
        ): JSONObject? =
                withContext(Dispatchers.IO) {
                    var socket: Socket? = null
                    var writer: PrintWriter? = null
                    var reader: BufferedReader? = null

                    try {
                        // Extract command details for better logging
                        val cmdType = command.optString("command", "unknown")
                        val cmdId = command.optString("id", "no-id")
                        val params = command.optJSONObject("params")

                        // Enhanced logging with special handling for commands with service names
                        val serviceName = params?.optString("name")
                        val logMessage =
                                if (serviceName != null && serviceName.isNotEmpty()) {
                                    "发送桥接器命令[$cmdId]: $cmdType 服务: $serviceName 其他参数: ${params.toString()}"
                                } else {
                                    "发送桥接器命令[$cmdId]: $cmdType ${if (params != null) "参数: $params" else ""}"
                                }

                        Log.d(TAG, logMessage)

                        // Create socket with timeout and proper options
                        socket = Socket()
                        socket.reuseAddress = true
                        socket.soTimeout = 60000 // 60 seconds read timeout
                        socket.connect(
                                java.net.InetSocketAddress(host, port),
                                5000
                        ) // 5 seconds connect timeout

                        writer = PrintWriter(socket.getOutputStream(), true)
                        reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                        // 发送命令
                        writer.println(command.toString())
                        writer.flush()

                        // 读取响应
                        val response = reader.readLine()
                        if (response != null) {
                            try {
                                val jsonResponse = JSONObject(response)
                                val success = jsonResponse.optBoolean("success", false)
                                val result = jsonResponse.optJSONObject("result")
                                val error = jsonResponse.optJSONObject("error")

                                // Log the raw JSON response first for detailed debugging
                                Log.d(TAG, "命令[$cmdId: $cmdType]原始JSON响应: $response")

                                // Enhanced response logging
                                val responseLog = StringBuilder()
                                responseLog.append("命令[$cmdId: $cmdType")
                                if (serviceName != null) responseLog.append(" 服务: $serviceName")
                                responseLog.append("]响应: ${if (success) "成功" else "失败"} ")

                                if (result != null) {
                                    // For listtools, show both the summary and full result
                                    if (cmdType == "listtools" && result.has("tools")) {
                                        val tools = result.optJSONArray("tools")
                                        val toolCount = tools?.length() ?: 0
                                        responseLog.append("获取到 $toolCount 个工具")
                                        // Add tool names if available
                                        if (toolCount > 0) {
                                            responseLog.append(" [")
                                            for (i in 0 until toolCount) {
                                                val tool = tools?.optJSONObject(i)
                                                val toolName = tool?.optString("name", "未命名工具")
                                                if (i > 0) responseLog.append(", ")
                                                responseLog.append(toolName)
                                                if (i >= 2 && toolCount > 3) {
                                                    responseLog.append("... (共 $toolCount 个)")
                                                    break
                                                }
                                            }
                                            responseLog.append("]")
                                        }
                                    } else {
                                        responseLog.append("结果: $result")
                                    }
                                }

                                if (error != null) responseLog.append(" 错误: $error")

                                Log.d(TAG, responseLog.toString())

                                return@withContext jsonResponse
                            } catch (e: Exception) {
                                Log.e(TAG, "解析响应失败: $response", e)
                                return@withContext null
                            }
                        } else {
                            Log.e(TAG, "命令[$cmdId: $cmdType]没有收到响应")
                            return@withContext null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "发送命令失败: ${command.optString("command")}", e)
                        return@withContext null
                    } finally {
                        // Close everything in the correct order with proper error handling
                        try {
                            writer?.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "关闭Writer异常", e)
                        }

                        try {
                            reader?.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "关闭Reader异常", e)
                        }

                        try {
                            if (socket?.isConnected == true && !socket.isClosed) {
                                socket.close()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "关闭Socket异常", e)
                        }
                    }
                }
    }

    // 注册新的MCP服务
    suspend fun registerMcpService(
            name: String,
            command: String,
            args: List<String> = emptyList(),
            description: String? = null,
            env: Map<String, String>? = null,
            cwd: String? = null
    ): JSONObject? {
        val params =
                JSONObject().apply {
                    put("type", "local") // Explicitly set type for local services
                    put("name", name)
                    put("command", command)
                    if (args.isNotEmpty()) {
                        // 显式创建JSONArray
                        val argsArray = JSONArray()
                        for (arg in args) {
                            argsArray.put(arg)
                        }
                        put("args", argsArray)
                    }
                    if (description != null) {
                        put("description", description)
                    }
                    if (env != null && env.isNotEmpty()) {
                        val envObj = JSONObject()
                        env.forEach { (key, value) -> envObj.put(key, value) }
                        put("env", envObj)
                    }
                    if (cwd != null) {
                        put("cwd", cwd)
                    }
                }

        val commandObj =
                JSONObject().apply {
                    put("command", "register")
                    put("id", UUID.randomUUID().toString())
                    put("params", params)
                }

        return sendCommand(commandObj)
    }

    // Overload for remote services
    suspend fun registerMcpService(
        name: String,
        type: String,
        endpoint: String,
        connectionType: String?,
        description: String? = null
    ): JSONObject? {
        val params =
            JSONObject().apply {
                put("type", type)
                put("name", name)
                put("endpoint", endpoint)
                if (connectionType != null) {
                    put("connectionType", connectionType)
                }
                if (description != null) {
                    put("description", description)
                }
            }

        val commandObj =
            JSONObject().apply {
                put("command", "register")
                put("id", UUID.randomUUID().toString())
                put("params", params)
            }

        return sendCommand(commandObj)
    }

    // 取消注册MCP服务
    suspend fun unregisterMcpService(name: String): JSONObject? {
        val command =
                JSONObject().apply {
                    put("command", "unregister")
                    put("id", UUID.randomUUID().toString())
                    put("params", JSONObject().apply { put("name", name) })
                }

        return sendCommand(command)
    }

    // 列出所有注册的MCP服务
    suspend fun listMcpServices(): JSONObject? {
        val command =
                JSONObject().apply {
                    put("command", "list")
                    put("id", UUID.randomUUID().toString())
                }

        return sendCommand(command)
    }

    // 启动MCP服务
    suspend fun spawnMcpService(
            name: String? = null,
            command: String? = null,
            args: List<String>? = null,
            env: Map<String, String>? = null,
            cwd: String? = null
    ): JSONObject? {
        val params = JSONObject()

        if (name != null) {
            params.put("name", name)
        }
        // Command and args are now only relevant for direct, unregistered local spawns
        if (command != null) {
            params.put("command", command)
        }
        if (args != null && args.isNotEmpty()) {
            // 使用JSONArray来正确处理数组
            val jsonArray = JSONArray()
            for (arg in args) {
                jsonArray.put(arg)
            }
            params.put("args", jsonArray)
        }
        if (env != null && env.isNotEmpty()) {
            val envObj = JSONObject()
            env.forEach { (key, value) -> envObj.put(key, value) }
            params.put("env", envObj)
        }
        if (cwd != null) {
            params.put("cwd", cwd)
        }

        val commandObj =
                JSONObject().apply {
                    put("command", "spawn")
                    put("id", UUID.randomUUID().toString())
                    put("params", params)
                }

        return sendCommand(commandObj)
    }

    // 获取工具列表
    suspend fun listTools(serviceName: String? = null): JSONObject? {
        val params = JSONObject()

        // Add service name if provided
        if (serviceName != null) {
            params.put("name", serviceName)
        }

        val command =
                JSONObject().apply {
                    put("command", "listtools")
                    put("id", UUID.randomUUID().toString())

                    // Only add params if we have any
                    if (params.length() > 0) {
                        put("params", params)
                    }
                }

        // Enhanced logging with service name
        Log.d(TAG, "获取工具列表${if (serviceName != null) " 服务: $serviceName" else " (默认服务)"}")

        return sendCommand(command)
    }

    // 获取MCP服务状态
    suspend fun getStatus(): JSONObject? {
        val command =
                JSONObject().apply {
                    put("command", "status")
                    put("id", UUID.randomUUID().toString())
                }

        return sendCommand(command)
    }

    // 调用工具
    suspend fun callTool(method: String, params: JSONObject): JSONObject? {
        val command =
                JSONObject().apply {
                    put("command", "toolcall")
                    put("id", UUID.randomUUID().toString())
                    put(
                            "params",
                            JSONObject().apply {
                                put("method", method)
                                put("params", params)
                            }
                    )
                }

        return sendCommand(command)
    }

    // 简化调用工具的方法
    suspend fun toolcall(method: String, params: Map<String, Any>): JSONObject? {
        val paramsJson = JSONObject()
        params.forEach { (key, value) -> paramsJson.put(key, value) }
        return callTool(method, paramsJson)
    }

    /**
     * pingMcpService - 专门针对服务的ping操作，用于验证特定服务的状态
     *
     * @param serviceName 要ping的服务名称
     * @return ping响应，如果失败则返回null
     */
    suspend fun pingMcpService(serviceName: String): JSONObject? =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "开始执行针对服务 $serviceName 的ping操作")

                    // 构建带有服务名参数的ping命令
                    val command =
                            JSONObject().apply {
                                put("command", "ping")
                                put("id", UUID.randomUUID().toString())
                                put("params", JSONObject().apply { put("name", serviceName) })
                            }

                    val response = sendCommand(command)

                    if (response?.optBoolean("success", false) == true) {
                        val result = response.optJSONObject("result")

                        // 检查服务状态
                        val status = result?.optString("status")
                        val active = result?.optBoolean("active", false) ?: false
                        val ready = result?.optBoolean("ready", false) ?: false

                        if (status == "ok") {
                            Log.d(TAG, "服务 $serviceName 正在运行并响应")
                            return@withContext response
                        } else if (active) {
                            Log.d(TAG, "服务 $serviceName 正在运行但可能尚未完全准备好")
                            return@withContext response
                        } else {
                            Log.d(TAG, "服务 $serviceName 已注册但未运行")
                            return@withContext response
                        }
                    }

                    Log.w(TAG, "Ping服务 $serviceName 失败")
                    return@withContext null
                } catch (e: Exception) {
                    Log.e(TAG, "Ping服务时出错: ${e.message}")
                    return@withContext null
                }
            }
}
