package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.core.tools.system.Terminal
import com.google.gson.JsonParser
import java.io.File

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/**
 * MCP 插件部署工具类
 *
 * 负责协调项目分析、命令生成和配置生成等组件完成插件部署
 */
class MCPDeployer(private val context: Context) {

    // 创建一个协程作用域，用于处理异步操作
    private val deployerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "MCPDeployer"
    }

    /** Get or create shared session */
    private suspend fun getOrCreateSharedSession(): String? {
        return MCPSharedSession.getOrCreateSharedSession(context)
    }

    // 部署状态
    sealed class DeploymentStatus {
        object NotStarted : DeploymentStatus()
        data class InProgress(val message: String) : DeploymentStatus()
        data class Success(val message: String) : DeploymentStatus()
        data class Error(val message: String) : DeploymentStatus()
    }
    /**
     * 部署MCP插件（使用自定义命令）
     *
     * @param pluginId 插件ID
     * @param pluginPath 插件安装路径
     * @param customCommands 自定义部署命令列表
     * @param environmentVariables 环境变量键值对
     * @param statusCallback 部署状态回调
     */
    suspend fun deployPluginWithCommands(
            pluginId: String,
            pluginPath: String,
            customCommands: List<String>,
            environmentVariables: Map<String, String> = emptyMap(),
            statusCallback: (DeploymentStatus) -> Unit
    ): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    statusCallback(DeploymentStatus.InProgress("开始部署插件: $pluginId"))
                    Log.d(TAG, "开始部署插件(自定义命令): $pluginId, 路径: $pluginPath")

                    // 验证插件路径
                    val pluginDir = File(pluginPath)
                    if (!pluginDir.exists() || !pluginDir.isDirectory) {
                        Log.e(TAG, "插件目录不存在: $pluginPath")
                        statusCallback(DeploymentStatus.Error("插件目录不存在: $pluginPath"))
                        return@withContext false
                    }

                    // 创建项目分析器（仅用于分析项目类型和生成配置）
                    val projectAnalyzer = MCPProjectAnalyzer()
                    val readmeFile = projectAnalyzer.findReadmeFile(pluginDir)
                    val readmeContent = readmeFile?.readText() ?: ""
                    
                    // 分析项目结构以便生成配置
                    statusCallback(DeploymentStatus.InProgress("分析项目结构..."))
                    val projectStructure = projectAnalyzer.analyzeProjectStructure(pluginDir, readmeContent)
                    
                    // 创建配置生成器
                    val configGenerator = MCPConfigGenerator()
                    
                    // 生成MCP配置，包含环境变量
                    val mcpConfig = configGenerator.generateMcpConfig(pluginId, projectStructure, environmentVariables)
                    
                    // 保存MCP配置
                    statusCallback(DeploymentStatus.InProgress("保存MCP配置..."))
                    val mcpLocalServer = MCPLocalServer.getInstance(context)
                    
                    // 解析生成的MCP配置并保存为正确的格式
                    val configSaveResult = saveMCPConfigToLocalServer(mcpLocalServer, pluginId, mcpConfig)

                    if (!configSaveResult) {
                        Log.e(TAG, "保存MCP配置失败: $pluginId")
                        statusCallback(DeploymentStatus.Error("保存MCP配置失败"))
                        return@withContext false
                    }
                    
                    Log.d(TAG, "使用自定义命令: $customCommands")
                    
                    // 执行部署命令
                    return@withContext executeDeployCommands(
                            pluginId, 
                            pluginPath, 
                            customCommands, 
                            statusCallback,
                            configGenerator.extractServerNameFromConfig(mcpConfig)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "使用自定义命令部署插件时出错", e)
                    statusCallback(DeploymentStatus.Error("部署出错: ${e.message}"))
                    return@withContext false
                }
            }
        
    /**
     * 执行部署命令（提取的公共方法）
     */
    private suspend fun executeDeployCommands(
            pluginId: String,
            pluginPath: String,
            deployCommands: List<String>,
            statusCallback: (DeploymentStatus) -> Unit,
            serverName: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 获取或创建共享终端会话
            val sessionId = getOrCreateSharedSession()
            if (sessionId == null) {
                statusCallback(DeploymentStatus.Error("无法获取终端会话"))
                return@withContext false
            }
            
            val terminal = Terminal.getInstance(context)

            // 定义插件在 proot 环境中的主目录路径
            val pluginHomeDir = "~/mcp_plugins"
            val pluginDir = "$pluginHomeDir/${pluginId.split("/").last()}"

            // 首先创建插件目录
            statusCallback(DeploymentStatus.InProgress("创建插件目录: $pluginDir"))

            val mkdirExecuted = terminal.executeCommand(sessionId, "mkdir -p $pluginDir")
            if (mkdirExecuted == null) {
                statusCallback(DeploymentStatus.Error("创建插件目录失败"))
                return@withContext false
            }

            // 复制插件文件到目标目录
            statusCallback(DeploymentStatus.InProgress("复制插件文件到目标目录..."))

            // 将pluginPath转换为终端可访问的路径
            val terminalPluginPath = if (pluginPath.startsWith("/storage/")) {
                // 如果是Android storage路径，转换为sdcard路径
                pluginPath.replace("/storage/emulated/0", "/sdcard")
            } else if (pluginPath.startsWith("/sdcard")) {
                // 已经是sdcard路径，直接使用
                pluginPath
            } else {
                // 其他情况，假设是相对路径或需要检查的路径
                pluginPath
            }

            val copyExecuted = terminal.executeCommand(sessionId, "cp -r $terminalPluginPath/* $pluginDir/")
            if (copyExecuted == null) {
                statusCallback(DeploymentStatus.Error("复制文件到目标目录失败"))
                return@withContext false
            }

            // 切换到插件目录
            statusCallback(DeploymentStatus.InProgress("切换到插件目录"))

            val cdExecuted = terminal.executeCommand(sessionId, "cd $pluginDir")
            if (cdExecuted == null) {
                statusCallback(DeploymentStatus.Error("切换到插件目录失败"))
                return@withContext false
            }

            // 安装依赖并配置环境
            for ((index, command) in deployCommands.withIndex()) {
                // 跳过启动命令，只执行依赖安装命令
                if (command.contains("python -m") ||
                                (command.contains("node ") &&
                                        !command.contains(
                                                "node ./node_modules/typescript"
                                        )) ||
                                command.contains("npm start") ||
                                command.startsWith("#")
                ) {
                    continue
                }

                val cleanCommand = command.trim()
                if (cleanCommand.isBlank()) continue

                // 判断是否是非关键命令（如npm配置命令）
                val isNonCriticalCommand =
                        cleanCommand.contains("pnpm config set") ||
                                cleanCommand.contains("|| true") ||
                                cleanCommand.contains("pnpm install -g") ||
                                cleanCommand.startsWith("pnpm config")

                statusCallback(
                        DeploymentStatus.InProgress(
                                "执行命令 (${index + 1}/${deployCommands.size}): $cleanCommand"
                        )
                )
                Log.d(TAG, "执行命令 (${index + 1}/${deployCommands.size}): $cleanCommand")

                val commandExecuted = terminal.executeCommand(sessionId, cleanCommand)

                // 如果命令失败
                if (commandExecuted == null) {
                    if (isNonCriticalCommand) {
                        // 对于非关键命令，即使失败也继续
                        Log.w(TAG, "非关键命令执行失败，但将继续部署: $cleanCommand")
                        statusCallback(
                                DeploymentStatus.InProgress(
                                        "非关键命令执行失败，继续后续步骤: $cleanCommand"
                                )
                        )
                    } else {
                        // 关键命令失败，中止部署
                        Log.e(TAG, "命令执行失败: $cleanCommand")
                        statusCallback(DeploymentStatus.Error("命令执行失败: $cleanCommand"))
                        return@withContext false
                    }
                }
            }

            // 构建部署成功消息
            val successMessage = StringBuilder()
            successMessage.append("插件部署成功: $pluginId\n")
            successMessage.append("部署路径: $pluginDir\n")

            // 如果有MCP配置，添加服务器名称
            val finalServerName = serverName ?: pluginId.split("/").last().lowercase()
            successMessage.append("服务器名称: $finalServerName\n")

            // 保存当前时间作为部署时间
            val currentTime = System.currentTimeMillis()
            successMessage.append("部署时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(currentTime))}")

            // 保存部署成功状态到配置中
            try {
                val mcpLocalServer = MCPLocalServer.getInstance(context)
                mcpLocalServer.updateServerStatus(pluginId, deploySuccess = true)
                Log.d(TAG, "已保存部署成功状态: $pluginId")
            } catch (e: Exception) {
                Log.e(TAG, "保存部署成功状态失败: ${e.message}")
            }

            statusCallback(DeploymentStatus.Success(successMessage.toString()))
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "执行部署命令时出错", e)
            statusCallback(DeploymentStatus.Error("部署出错: ${e.message}"))
            return@withContext false
        }
    }

    /**
     * 将生成的MCP配置正确保存到MCPLocalServer
     */
    private suspend fun saveMCPConfigToLocalServer(
        mcpLocalServer: MCPLocalServer,
        pluginId: String,
        mcpConfigJson: String
    ): Boolean {
        return try {
            // 解析生成的MCP配置JSON
            val jsonObject = JsonParser.parseString(mcpConfigJson).asJsonObject
            val mcpServers = jsonObject.getAsJsonObject("mcpServers")
            
            if (mcpServers != null && mcpServers.size() > 0) {
                // 获取第一个服务器配置（通常是唯一的）
                val serverName = mcpServers.keySet().first()
                val serverConfig = mcpServers.getAsJsonObject(serverName)
                
                // 提取配置参数
                val command = serverConfig.get("command")?.asString ?: "python"
                val args = serverConfig.getAsJsonArray("args")?.map { it.asString } ?: emptyList()
                val disabled = serverConfig.get("disabled")?.asBoolean ?: false
                val autoApprove = serverConfig.getAsJsonArray("autoApprove")?.map { it.asString } ?: emptyList()
                
                // 提取环境变量
                val env = mutableMapOf<String, String>()
                serverConfig.getAsJsonObject("env")?.let { envObject ->
                    envObject.keySet().forEach { key ->
                        env[key] = envObject.get(key).asString
                    }
                }
                
                Log.d(TAG, "解析MCP配置 - 服务器: $serverName, 命令: $command, 参数: $args")
                
                // 保存到MCPLocalServer
                mcpLocalServer.addOrUpdateMCPServer(
                    serverId = pluginId,
                    command = command,
                    args = args,
                    env = env,
                    disabled = disabled,
                    autoApprove = autoApprove,
                    metadata = mapOf(
                        "serverName" to serverName,
                        "deployedTime" to System.currentTimeMillis().toString()
                    )
                )
                
                true
            } else {
                Log.e(TAG, "MCP配置中没有找到服务器配置")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析和保存MCP配置失败", e)
            false
        }
    }
}
