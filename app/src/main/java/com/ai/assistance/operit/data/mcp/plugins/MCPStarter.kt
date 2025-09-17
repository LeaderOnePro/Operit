package com.ai.assistance.operit.data.mcp.plugins

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.mcp.MCPManager
import com.ai.assistance.operit.core.tools.mcp.MCPServerConfig
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.core.tools.system.Terminal
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * MCP Plugin Starter
 *
 * Handles starting deployed MCP plugins via the bridge
 */
class MCPStarter(private val context: Context) {
    companion object {
        private const val TAG = "MCPStarter"
        private var bridgeInitialized = false
    }

    // Coroutine scope for async operations
    private val starterScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val terminal = Terminal.getInstance(context)
    private var pnpmInstalled: Boolean? = null
    private var sharedSessionId: String? = null

    /** Plugin initialization status enum */
    enum class PluginInitStatus {
        SUCCESS,
        TERMINAL_SERVICE_UNAVAILABLE,
        NODEJS_MISSING,
        BRIDGE_FAILED,
        OTHER_ERROR
    }

    /** Plugin start progress listener interface */
    interface PluginStartProgressListener {
        fun onPluginStarting(pluginId: String, index: Int, total: Int) {}
        fun onPluginStarted(pluginId: String, success: Boolean, index: Int, total: Int) {}
        fun onAllPluginsStarted(successCount: Int, totalCount: Int, status: PluginInitStatus = PluginInitStatus.SUCCESS) {}
        fun onAllPluginsVerified(verificationResults: List<VerificationResult>) {}
    }

    /** Get or create shared session */
    private suspend fun getOrCreateSharedSession(): String? {
        if (sharedSessionId != null) {
            return sharedSessionId
        }

        if (!terminal.isConnected()) {
            if (!terminal.initialize()) {
                Log.e(TAG, "Failed to initialize TerminalManager")
                return null
            }
        }

        // Create a shared session for all MCP operations
        sharedSessionId = terminal.createSessionAndWait("mcp-operations")
        if (sharedSessionId == null) {
            Log.e(TAG, "Failed to create shared terminal session or session initialization timeout")
        }
        return sharedSessionId
    }

    /** Check if pnpm is installed in terminal */
    private suspend fun isPnpmInstalled(): Boolean {
        if (pnpmInstalled != null) return pnpmInstalled == true

        val sessionId = getOrCreateSharedSession()
        if (sessionId == null) {
            pnpmInstalled = false
            return false
        }

        try {
            val result = terminal.executeCommand(sessionId, "command -v pnpm")
            val installed = result != null && result.contains("pnpm")
            pnpmInstalled = installed
            return installed
        } catch (e: Exception) {
            Log.e(TAG, "Error checking pnpm installation: ${e.message}")
            pnpmInstalled = false
            return false
        }
    }

    /** Check if terminal service is connected and initialized */
    private suspend fun isTerminalServiceConnected(): Boolean {
        if (terminal.isConnected()) return true
        return terminal.initialize()
    }

    /** Initialize and start the bridge */
    private suspend fun initBridge(): Boolean {
        if (bridgeInitialized) {
            val pingResult = MCPBridge.ping()
            if (pingResult != null) return true
        }

        // Check if terminal service is available
        if (!isTerminalServiceConnected()) {
            Log.e(TAG, "Terminal service is not connected. Please start it first.")
            return false
        }

        // Check if pnpm is installed
        if (!isPnpmInstalled()) {
            Log.e(TAG, "pnpm is not installed in terminal. Please install pnpm first.")
            return false
        }

        // Get shared session for deployment and starting
        val sessionId = getOrCreateSharedSession()
        if (sessionId == null) {
            Log.e(TAG, "Failed to get shared session for bridge initialization")
            return false
        }

        // Deploy bridge to terminal
        if (!MCPBridge.deployBridge(context, sessionId)) {
            Log.e(TAG, "Failed to deploy bridge")
            return false
        }

        // Start bridge
        if (!MCPBridge.startBridge(
            context = context,
            sessionId = sessionId
        )) {
            Log.e(TAG, "Failed to start bridge")
            return false
        }

        bridgeInitialized = true
        return true
    }

    /** Check if a server is running */
    private fun isServerRunning(serverName: String): Boolean {
        try {
            // Create a bridge instance
            val bridge = MCPBridge.getInstance(context)

            // Get ping response
            val pingResult = kotlinx.coroutines.runBlocking { bridge.pingMcpService(serverName) }

            if (pingResult != null) {
                val result = pingResult.optJSONObject("result")
                // 直接检查running状态
                return result?.optBoolean("running", false) ?: false
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking server status: ${e.message}")
            return false
        }
    }

    /** Start a plugin using the bridge */
    suspend fun startPlugin(pluginId: String, statusCallback: (StartStatus) -> Unit): Boolean {
        try {
            val mcpLocalServer = MCPLocalServer.getInstance(context)
            val mcpRepository = MCPRepository(context)

            val pluginInfo = mcpRepository.getInstalledPluginInfo(pluginId)
            if (pluginInfo == null) {
                statusCallback(StartStatus.Error("Plugin info not found: $pluginId"))
                return false
            }

            val serviceType = pluginInfo.type

            // For local plugins, check if they are deployed
            if (serviceType == "local") {
                val serverStatus = mcpLocalServer.getServerStatus(pluginId)
                val isDeployed = serverStatus?.deploySuccess == true
                if (!isDeployed) {
                    statusCallback(StartStatus.Error("Plugin not deployed: $pluginId"))
                    return false
                }
            }

            // Check if plugin is enabled by the user
            val serverStatus = mcpLocalServer.getServerStatus(pluginId)
            val isEnabled = serverStatus?.isEnabled != false // 默认为true
            if (!isEnabled) {
                statusCallback(StartStatus.Error("Plugin not enabled by user: $pluginId"))
                return false
            }

            statusCallback(StartStatus.InProgress("Starting plugin: $pluginId"))

            val serverName = pluginInfo.name.replace(" ", "_").lowercase().ifEmpty { pluginId.split("/").last().lowercase() }

            // Handle remote services differently
            if (serviceType == "remote") {
                val endpoint = pluginInfo.endpoint
                val connectionType = pluginInfo.connectionType

                if (endpoint == null) {
                    statusCallback(StartStatus.Error("Remote service is missing endpoint: $pluginId"))
                    return false
                }
                
                if (!initBridge()) {
                    statusCallback(StartStatus.Error("Failed to initialize bridge for remote service"))
                    return false
                }

                val bridge = MCPBridge.getInstance(context)

                // Register remote service with the bridge
                val registerResult = bridge.registerMcpService(
                    name = serverName,
                    type = "remote",
                    endpoint = endpoint,
                    connectionType = connectionType,
                    description = "Remote MCP Server: $pluginId"
                )

                if (registerResult == null || !registerResult.optBoolean("success", false)) {
                    statusCallback(StartStatus.Error("Failed to register remote MCP service"))
                    return false
                }
                
                // "Spawn" the remote service to trigger a connection
                val spawnResult = bridge.spawnMcpService(serverName)
                if (spawnResult == null || !spawnResult.optBoolean("success", false)) {
                     statusCallback(StartStatus.Error("Failed to connect to remote MCP service"))
                    return false
                }

                statusCallback(StartStatus.Success("Remote service $pluginId connected successfully"))
                return true
            }

            // --- Existing logic for local plugins ---
            val pluginConfig = mcpLocalServer.getPluginConfig(pluginId)
            val config = parseConfigJson(pluginConfig)
            val extractedServerName = extractServerNameFromConfig(pluginConfig) ?: serverName

            // Get server command and args
            val serverConfig = config?.mcpServers?.get(extractedServerName)
            if (serverConfig == null) {
                statusCallback(StartStatus.Error("Invalid plugin config: $pluginId"))
                return false
            }

            // 不再需要旧的注册方法
            // registerServerIfNeeded(extractedServerName, serverConfig, pluginId)

            // Check if plugin service is already running
            if (isServerRunning(extractedServerName)) {
                statusCallback(StartStatus.Success("Plugin $pluginId is already running"))
                return true
            }

            // Initialize bridge
            if (!initBridge()) {
                // Check specifically if Termux is not running, not authorized, or Node.js is missing
                when {
                    !isTerminalServiceConnected() -> {
                        statusCallback(StartStatus.TerminalServiceUnavailable())
                    }
                    !isPnpmInstalled() -> {
                        statusCallback(StartStatus.PnpmMissing())
                    }
                    else -> {
                        statusCallback(StartStatus.Error("Failed to initialize bridge"))
                    }
                }
                return false
            }

            statusCallback(StartStatus.InProgress("Starting plugin via bridge..."))

            // Use MCPBridge instance
            val bridge = MCPBridge.getInstance(context)
            val termuxPluginDir =
                    "~/mcp_plugins/${pluginId.split("/").last()}"

            // Register MCP service
            val registerResult =
                    bridge.registerMcpService(
                            name = extractedServerName,
                            command = serverConfig.command,
                            args = serverConfig.args,
                            description = "MCP Server: $pluginId",
                            env = serverConfig.env,
                            cwd = termuxPluginDir
                    )

            if (registerResult == null || !registerResult.optBoolean("success", false)) {
                statusCallback(StartStatus.Error("Failed to register MCP service"))
                return false
            }

            // Start MCP service
            val spawnResult = bridge.spawnMcpService(extractedServerName)

            if (spawnResult == null || !spawnResult.optBoolean("success", false)) {
                statusCallback(StartStatus.Error("Failed to start MCP service"))
                return false
            }

            // Wait a moment for the service to initialize
            delay(3000)

            val finalStatus = bridge.pingMcpService(extractedServerName)
            if (
                    finalStatus != null &&
                            finalStatus.optJSONObject("result")?.optBoolean("active", false) ==
                                    true
            ) {
                statusCallback(StartStatus.Success("Service $pluginId started successfully"))
                return true
            } else {
                statusCallback(StartStatus.Error("Service $pluginId started but is not active"))
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting plugin", e)
            statusCallback(StartStatus.Error("Start error: ${e.message}"))
            return false
        }
    }

    /** Start all deployed plugins */
    fun startAllDeployedPlugins(
            progressListener: PluginStartProgressListener = object : PluginStartProgressListener {}
    ) {
        starterScope.launch {
            try {
                // Check if terminal service is available
                if (!isTerminalServiceConnected()) {
                    Log.e(TAG, "Terminal service is not connected. Please start it first.")
                    progressListener.onAllPluginsStarted(0, 0, PluginInitStatus.TERMINAL_SERVICE_UNAVAILABLE)
                    return@launch
                }

                // Initialize bridge, which also checks for pnpm
                if (!initBridge()) {
                    // initBridge now handles setting the correct status
                    val status = if (pnpmInstalled == false) PluginInitStatus.NODEJS_MISSING else PluginInitStatus.BRIDGE_FAILED
                    progressListener.onAllPluginsStarted(0, 0, status)
                    return@launch
                }

                val mcpRepository = MCPRepository(context)
                val mcpLocalServer = MCPLocalServer.getInstance(context)

                // Get plugins to start: enabled remote plugins, or enabled and deployed local plugins
                val pluginList = mcpRepository.installedPluginIds.first()
                val pluginsToStart =
                        pluginList.filter { pluginId ->
                            val serverStatus = mcpLocalServer.getServerStatus(pluginId)
                            val isEnabled = serverStatus?.isEnabled != false // 默认为true
                            if (!isEnabled) {
                                false
                            } else {
                                val pluginInfo = mcpRepository.getInstalledPluginInfo(pluginId)
                                when (pluginInfo?.type) {
                                    "remote" -> true // Remote plugins only need to be enabled
                                    else -> // Local plugins must also be deployed
                                    serverStatus?.deploySuccess == true
                                }
                            }
                        }

                if (pluginsToStart.isEmpty()) {
                    progressListener.onAllPluginsStarted(0, 0, PluginInitStatus.SUCCESS)
                    return@launch
                }

                // 并行启动所有插件
                val deferreds =
                        pluginsToStart.mapIndexed { index, pluginId ->
                            starterScope.async {
                                progressListener.onPluginStarting(
                                        pluginId,
                                        index + 1,
                                        pluginsToStart.size
                                )
                                val success =
                                        startPlugin(pluginId) {
                                            // Status callback is not used here
                                        }
                                progressListener.onPluginStarted(
                                        pluginId,
                                        success,
                                        index + 1,
                                        pluginsToStart.size
                                )
                                success
                            }
                        }

                val results = deferreds.awaitAll()
                val successCount = results.count { it }

                // Notify all plugins started
                progressListener.onAllPluginsStarted(
                        successCount,
                        pluginsToStart.size,
                        PluginInitStatus.SUCCESS
                )

                // Verify plugins
                verifyPlugins(progressListener)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting plugins", e)
                progressListener.onAllPluginsStarted(0, 0, PluginInitStatus.OTHER_ERROR)
            }
        }
    }

    /** Verify plugin statuses */
    private fun verifyPlugins(progressListener: PluginStartProgressListener) {
        starterScope.launch {
            try {
                delay(5000) // Wait for services to initialize
                val results = verifyAllMcpPlugins()
                progressListener.onAllPluginsVerified(results)
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying plugins", e)
                progressListener.onAllPluginsVerified(emptyList())
            }
        }
    }

    /** Verify all MCP plugins */
    suspend fun verifyAllMcpPlugins(): List<VerificationResult> {
        val results = mutableListOf<VerificationResult>()

        try {
            val mcpRepository = MCPRepository(context)
            val mcpLocalServer = MCPLocalServer.getInstance(context)

            // Get deployed plugins
            val pluginList = mcpRepository.installedPluginIds.first()
            val deployedPlugins =
                    pluginList.filter { 
                        val serverStatus = mcpLocalServer.getServerStatus(it)
                        serverStatus?.deploySuccess == true
                    }

            // Get registered services
            val bridge = MCPBridge.getInstance(context)
            val listResponse = bridge.listMcpServices()
            val servicesList = mutableListOf<String>()

            if (listResponse?.optBoolean("success", false) == true) {
                val services = listResponse.optJSONObject("result")?.optJSONArray("services")
                if (services != null) {
                    for (i in 0 until services.length()) {
                        val name = services.optJSONObject(i)?.optString("name", "")
                        if (!name.isNullOrEmpty()) {
                            servicesList.add(name)
                        }
                    }
                }
            }

            // Verify each plugin
            for (pluginId in deployedPlugins) {
                val pluginConfig = mcpLocalServer.getPluginConfig(pluginId)
                val serverName =
                        extractServerNameFromConfig(pluginConfig)
                                ?: pluginId.split("/").last().lowercase()

                if (!servicesList.contains(serverName)) {
                    results.add(
                            VerificationResult(
                                    pluginId = pluginId,
                                    serviceName = serverName,
                                    isResponding = false,
                                    responseTime = 0,
                                    details = "Service not registered"
                            )
                    )
                    continue
                }

                // Verify service status
                val client = MCPBridgeClient(context, serverName)
                val startTime = System.currentTimeMillis()
                val pingSuccess = client.pingSync()
                val responseTime = System.currentTimeMillis() - startTime

                if (pingSuccess) {
                    results.add(
                            VerificationResult(
                                    pluginId = pluginId,
                                    serviceName = serverName,
                                    isResponding = true,
                                    responseTime = responseTime,
                                    details = "Service is responding"
                            )
                    )
                } else {
                    results.add(
                            VerificationResult(
                                    pluginId = pluginId,
                                    serviceName = serverName,
                                    isResponding = false,
                                    responseTime = 0,
                                    details = "Service not responding"
                            )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying plugins", e)
        }

        return results
    }

    /** Extract server name from config */
    private fun extractServerNameFromConfig(configJson: String): String? {
        if (configJson.isBlank()) return null

        try {
            val jsonObject = JsonParser.parseString(configJson).asJsonObject
            val mcpServers = jsonObject.getAsJsonObject("mcpServers")
            return mcpServers?.keySet()?.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "解析配置JSON失败", e)
            return null
        }
    }

    /** Parse config JSON to MCPConfig */
    private fun parseConfigJson(configJson: String): MCPLocalServer.MCPConfig? {
        if (configJson.isBlank()) return null

        try {
            return Gson().fromJson(configJson, MCPLocalServer.MCPConfig::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "解析配置JSON失败", e)
            return null
        }
    }

    /** Register server if needed */
    private fun registerServerIfNeeded(
            serverName: String,
            serverConfig: MCPLocalServer.MCPConfig.ServerConfig,
            pluginId: String
    ) {
        try {
            val mcpManager = MCPManager.getInstance(context)

            val extraDataMap = mutableMapOf<String, String>()
            extraDataMap["command"] = serverConfig.command
            extraDataMap["args"] = serverConfig.args.joinToString(",")

            serverConfig.env.forEach { (key, value) -> extraDataMap["env_$key"] = value }

            val mcpServerConfig =
                    MCPServerConfig(
                            name = serverName,
                            endpoint = "mcp://plugin/$serverName",
                            description = "MCP Server from plugin: $pluginId",
                            capabilities = listOf("tools", "resources"),
                            extraData = extraDataMap
                    )

            mcpManager.registerServer(serverName, mcpServerConfig)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register server", e)
        }
    }

    /** Start status */
    sealed class StartStatus {
        object NotStarted : StartStatus()
        data class InProgress(val message: String) : StartStatus()
        data class Success(val message: String) : StartStatus()
        data class Error(val message: String) : StartStatus()
        data class TerminalServiceUnavailable(
                val message: String = "终端服务不可用，请先启动它"
        ) : StartStatus()
        data class PnpmMissing(
                val message: String = "终端中未安装pnpm，请先安装"
        ) : StartStatus()
    }

    /** Verification result */
    data class VerificationResult(
            val pluginId: String,
            val serviceName: String,
            val isResponding: Boolean,
            val responseTime: Long,
            val details: String = ""
    )
}

