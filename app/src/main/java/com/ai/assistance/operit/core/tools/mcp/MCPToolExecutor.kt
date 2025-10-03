package com.ai.assistance.operit.core.tools.mcp

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.data.mcp.plugins.MCPBridgeClient
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

/**
 * MCP工具执行器
 *
 * 处理MCP工具的调用，类似于已有的PackageToolExecutor
 */
class MCPToolExecutor(private val context: Context, private val mcpManager: MCPManager) :
        ToolExecutor {
    companion object {
        private const val TAG = "MCPToolExecutor"
        private const val MAX_RESULT_LENGTH = 2000 // 最大返回长度，约2000个token
    }

    /** 截断过长的结果字符串 */
    private fun truncateResult(result: String): String {
        if (result.length <= MAX_RESULT_LENGTH) {
            return result
        }
        val truncated = result.substring(0, MAX_RESULT_LENGTH)
        val remainingLength = result.length - MAX_RESULT_LENGTH
        return "$truncated\n\n[... 结果过长，已截断 $remainingLength 个字符。建议使用文件操作或分页查询。]"
    }

    override fun invoke(tool: AITool): ToolResult {
        // 从工具名称中提取服务器名称和工具名称
        // 格式：服务器名称:工具名称
        val toolNameParts = tool.name.split(":")
        if (toolNameParts.size < 2) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "无效的MCP工具名称格式，应为 '服务器名称:工具名称'"
            )
        }

        val serverName = toolNameParts[0]
        val actualToolName = toolNameParts.subList(1, toolNameParts.size).joinToString(":")

        // 获取MCP桥接客户端
        val mcpClient = mcpManager.getOrCreateClient(serverName)
        if (mcpClient == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "无法连接到MCP服务器: $serverName"
            )
        }

        // 在调用工具前，检查服务是否处于激活状态
        val isActive = kotlinx.coroutines.runBlocking { mcpClient.isActive() }
        if (!isActive) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error =
                            "MCP服务 '$serverName' 未激活。请先使用 'use_package' 工具并指定包名 '$serverName' 来激活它。"
            )
        }

        Log.d(TAG, "准备调用MCP工具: $serverName:$actualToolName")

        // 将AITool参数转换为Map
        val parameters = tool.parameters.associate { it.name to it.value }

        // 获取工具参数类型信息 (如果可用)
        val toolInfo = getToolInfo(serverName, actualToolName)

        // 自动类型转换处理
        val convertedParameters = convertParameterTypes(parameters, toolInfo)

        // 调用MCP工具 - 使用同步版本
        val result =
                try {
                    // 直接调用工具，返回完整的响应（包括 success, result, error）
                    val response = mcpClient.callToolSync(actualToolName, convertedParameters)

                    if (response == null) {
                        // 如果响应为空（不应该发生，但做个保护）
                        Log.e(TAG, "MCP工具调用返回空响应: $serverName:$actualToolName")
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "工具调用返回空响应"
                        )
                    } else {
                        val success = response.optBoolean("success", false)
                        
                        if (success) {
                            // 成功：提取 result 字段
                            val resultData = response.optJSONObject("result")
                            val resultString = resultData?.toString() ?: "{}"
                            val truncatedResult = truncateResult(resultString)
                            Log.d(TAG, "MCP工具调用成功: $serverName:$actualToolName")
                            ToolResult(
                                    toolName = tool.name,
                                    success = true,
                                    result = StringResultData(truncatedResult),
                                    error = null
                            )
                        } else {
                            // 失败：提取 error 字段
                            val errorObj = response.optJSONObject("error")
                            val errorMessage = if (errorObj != null) {
                                val code = errorObj.optInt("code", -1)
                                val message = errorObj.optString("message", "未知错误")
                                "[$code] $message"
                            } else {
                                "工具调用失败，但未返回错误信息"
                            }
                            
                            Log.w(TAG, "MCP工具调用失败: $serverName:$actualToolName - $errorMessage")
                            ToolResult(
                                    toolName = tool.name,
                                    success = false,
                                    result = StringResultData(""),
                                    error = errorMessage
                            )
                        }
                    }
                } catch (e: Exception) {
                    val errorMessage = "调用工具时发生异常: ${e.message}"
                    Log.e(TAG, "调用MCP工具时发生异常: $errorMessage", e)
                    ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = errorMessage
                    )
                }

        return result
    }

    /** 尝试获取工具的参数类型信息 */
    private fun getToolInfo(serverName: String, toolName: String): JSONObject? {
        try {
            val client = mcpManager.getOrCreateClient(serverName) ?: return null
            val tools = kotlinx.coroutines.runBlocking { client.getTools() }

            return tools.find { it.optString("name") == toolName }
        } catch (e: Exception) {
            Log.w(TAG, "获取工具信息失败: ${e.message}")
            return null
        }
    }

    /**
     * 自动转换参数类型
     *
     * 将字符串参数转换为适当的类型（包括 number、boolean、array 等）
     * 支持递归处理数组内的元素
     */
    private fun convertParameterTypes(
            parameters: Map<String, Any>,
            toolInfo: JSONObject?
    ): Map<String, Any> {
        val result = mutableMapOf<String, Any>()

        parameters.forEach { (name, value) ->
            // 尝试从工具定义中获取参数类型（从 inputSchema.properties 中获取）
            val expectedType =
                    toolInfo?.optJSONObject("inputSchema")?.optJSONObject("properties")?.let {
                                properties ->
                        properties.optJSONObject(name)?.optString("type")
                    }

            // 使用 MCPToolParameter.smartConvert 进行智能类型转换
            val convertedValue = MCPToolParameter.smartConvert(value, expectedType)

            if (convertedValue != value) {
                Log.d(
                        TAG,
                        "参数 $name 从 ${value::class.java.simpleName} 转换为 ${convertedValue::class.java.simpleName}: $value -> $convertedValue"
                )
            }

            result[name] = convertedValue
        }

        return result
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        // 验证工具名称格式
        val toolNameParts = tool.name.split(":")
        if (toolNameParts.size < 2) {
            return ToolValidationResult(
                    valid = false,
                    errorMessage = "无效的MCP工具名称格式，应为 '服务器名称:工具名称'"
            )
        }

        // 这里可以添加更多验证逻辑，但目前简单返回成功
        return ToolValidationResult(valid = true)
    }
}

/**
 * MCP管理器
 *
 * 管理MCP客户端的创建和缓存 注意：此版本使用MCPBridgeClient作为底层客户端，替代了原有的MCPClient
 */
class MCPManager(private val context: Context) {
    companion object {
        private const val TAG = "MCPManager"

        @Volatile private var INSTANCE: MCPManager? = null

        fun getInstance(context: Context): MCPManager {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE ?: MCPManager(context.applicationContext).also { INSTANCE = it }
                    }
        }
    }

    // 缓存已创建的MCP桥接客户端，避免重复创建
    private val clientCache =
            ConcurrentHashMap<String, com.ai.assistance.operit.data.mcp.plugins.MCPBridgeClient>()

    // 缓存服务器配置
    private val serverConfigCache = ConcurrentHashMap<String, MCPServerConfig>()

    /**
     * 检查服务器是否已注册
     *
     * @param serverName 服务器名称
     * @return 如果服务器已注册则返回true
     */
    fun isServerRegistered(serverName: String): Boolean {
        return serverConfigCache.containsKey(serverName)
    }

    /**
     * 获取所有已注册的服务器配置
     *
     * @return 服务器名称到服务器配置的映射
     */
    fun getRegisteredServers(): Map<String, MCPServerConfig> {
        return serverConfigCache.toMap()
    }

    /**
     * 获取或创建MCP桥接客户端
     *
     * @param serverName 服务器名称
     * @return MCP桥接客户端，如果服务器不存在或无法连接则返回null
     */
    fun getOrCreateClient(
            serverName: String
    ): com.ai.assistance.operit.data.mcp.plugins.MCPBridgeClient? {
        // 检查缓存中是否已有客户端
        val cachedClient = clientCache[serverName]
        if (cachedClient != null) {
            // 检查客户端连接状态 - 只做轻量检查，不要过早断开
            if (cachedClient.isConnected()) {
                Log.d(TAG, "使用已缓存的客户端: $serverName")
                return cachedClient
            } else {
                // 尝试重新连接现有客户端
                Log.d(TAG, "尝试重新连接缓存的客户端: $serverName")
                val reconnected = kotlinx.coroutines.runBlocking { cachedClient.connect() }
                if (reconnected) {
                    Log.d(TAG, "成功重新连接到服务: $serverName")
                    return cachedClient
                }
                // 客户端不再可用，从缓存移除
                Log.w(TAG, "无法重新连接到服务: $serverName，将创建新的连接")
                clientCache.remove(serverName)
            }
        }

        // 获取服务器配置
        val serverConfig = serverConfigCache[serverName] ?: return null

        try {
            // 创建新的桥接客户端
            val client =
                    com.ai.assistance.operit.data.mcp.plugins.MCPBridgeClient(context, serverName)

            // 尝试连接 - 带详细日志
            Log.d(TAG, "正在创建新的连接到服务: $serverName")
            val connectResult = kotlinx.coroutines.runBlocking { client.connect() }

            if (connectResult) {
                // 连接成功，在会话期间保持此连接
                Log.d(TAG, "成功连接到服务: $serverName，将在会话期间保持连接")
                clientCache[serverName] = client
                return client
            } else {
                Log.w(TAG, "无法连接到服务: $serverName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建桥接客户端时出错: ${e.message}", e)
        }

        return null
    }

    /**
     * 注册MCP服务器配置
     *
     * @param serverName 服务器名称
     * @param serverConfig 服务器配置
     */
    fun registerServer(serverName: String, serverConfig: MCPServerConfig) {
        serverConfigCache[serverName] = serverConfig

        // 如果已有缓存的客户端，需要更新或移除
        if (clientCache.containsKey(serverName)) {
            // 移除旧客户端，下次需要时会重新创建
            val oldClient = clientCache.remove(serverName)
            oldClient?.disconnect()
        }
    }

    /**
     * 注册MCP服务器（简化版）
     *
     * @param serverName 服务器名称
     * @param endpoint 服务器端点URL
     * @param description 服务器描述
     */
    fun registerServer(serverName: String, endpoint: String, description: String = "") {
        val serverConfig =
                MCPServerConfig(
                        name = serverName,
                        endpoint = endpoint,
                        description = description,
                        capabilities = listOf("tools"),
                        extraData = emptyMap()
                )
        registerServer(serverName, serverConfig)
    }

    /** 关闭所有MCP客户端连接 */
    fun shutdown() {
        clientCache.values.forEach { it.disconnect() }
        clientCache.clear()
    }
}
