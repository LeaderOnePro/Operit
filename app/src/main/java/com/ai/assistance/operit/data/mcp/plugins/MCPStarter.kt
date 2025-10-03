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
import org.json.JSONObject

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
        fun onPluginRegistered(pluginId: String, serviceName: String, success: Boolean) {}
        fun onPluginStarted(pluginId: String, success: Boolean, index: Int, total: Int) {}
        fun onAllPluginsStarted(
            successCount: Int,
            totalCount: Int,
            status: PluginInitStatus = PluginInitStatus.SUCCESS
        ) {
        }

        fun onAllPluginsVerified(verificationResults: List<VerificationResult>) {}
    }

    /** Get or create shared session */
    private suspend fun getOrCreateSharedSession(): String? {
        return MCPSharedSession.getOrCreateSharedSession(context)
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
        // 检查 bridge 是否已经在运行
        val bridge = MCPBridge.getInstance(context)
        val listResult = bridge.listMcpServices()
        if (listResult != null && listResult.optBoolean("success", false)) {
            Log.d(
                TAG,
                "Bridge is already running, resetting to clear all services and child processes..."
            )

            // 重置桥接器，清空所有服务、杀死子进程和清空池子
            val resetResult = MCPBridge.reset(context)

            if (resetResult != null) {
                Log.i(
                    TAG,
                    "Bridge reset successful: all services closed, child processes killed, registry cleared"
                )
                bridgeInitialized = true
                return true
            } else {
                Log.w(TAG, "Bridge reset failed, but bridge is running. Proceeding anyway...")
                bridgeInitialized = true
                return true
            }
        }

        // Bridge 未运行，需要启动
        Log.d(TAG, "Bridge is not running, starting fresh...")

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
                sessionId = null // Use a dedicated session for the bridge server
            )
        ) {
            Log.e(TAG, "Failed to start bridge")
            return false
        }

        bridgeInitialized = true
        return true
    }

    /** Start a plugin without initializing bridge (for batch operations) */
    private suspend fun startPluginWithoutBridgeInit(
        pluginId: String,
        statusCallback: (StartStatus) -> Unit
    ): Boolean {
        return startPluginInternal(pluginId, statusCallback, initBridgeFirst = false)
    }

    /** Start a plugin using the bridge */
    suspend fun startPlugin(pluginId: String, statusCallback: (StartStatus) -> Unit): Boolean {
        return startPluginInternal(pluginId, statusCallback, initBridgeFirst = true)
    }

    /** Internal plugin start logic */
    private suspend fun startPluginInternal(
        pluginId: String,
        statusCallback: (StartStatus) -> Unit,
        initBridgeFirst: Boolean
    ): Boolean {
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
                    // 自动部署未部署的插件
                    statusCallback(StartStatus.InProgress("插件未部署，开始自动部署: $pluginId"))
                    Log.d(TAG, "插件 $pluginId 未部署，开始自动部署")

                    val pluginPath = mcpRepository.installedPluginIds.first()
                        .find { it == pluginId }
                        ?.let { mcpRepository.getInstalledPluginPath(it) }

                    if (pluginPath == null) {
                        statusCallback(StartStatus.Error("无法获取插件路径: $pluginId"))
                        return false
                    }

                    // 使用MCPDeployer自动部署
                    val deployer = MCPDeployer(context)

                    // 对于虚拟路径（npx/uvx 插件），直接使用空命令列表
                    val deployCommands = if (pluginPath.startsWith("virtual://")) {
                        emptyList()
                    } else {
                        deployer.getDeployCommands(pluginId, pluginPath)
                    }

                    // 只有非虚拟路径且命令为空时才报错
                    if (deployCommands.isEmpty() && !pluginPath.startsWith("virtual://")) {
                        statusCallback(StartStatus.Error("无法确定如何部署插件: $pluginId"))
                        return false
                    }

                    // 执行部署
                    var deploySuccess = false
                    deployer.deployPluginWithCommands(
                        pluginId = pluginId,
                        pluginPath = pluginPath,
                        customCommands = deployCommands,
                        environmentVariables = emptyMap(),
                        statusCallback = { deployStatus ->
                            when (deployStatus) {
                                is MCPDeployer.DeploymentStatus.Success -> {
                                    deploySuccess = true
                                    statusCallback(StartStatus.InProgress("插件部署成功: $pluginId"))
                                }

                                is MCPDeployer.DeploymentStatus.Error -> {
                                    statusCallback(StartStatus.Error("部署失败: ${deployStatus.message}"))
                                }

                                is MCPDeployer.DeploymentStatus.InProgress -> {
                                    statusCallback(StartStatus.InProgress(deployStatus.message))
                                }

                                else -> {}
                            }
                        }
                    )

                    if (!deploySuccess) {
                        statusCallback(StartStatus.Error("插件部署失败: $pluginId"))
                        return false
                    }

                    statusCallback(StartStatus.InProgress("插件部署完成，继续启动: $pluginId"))
                }
            }

            // Check if plugin is enabled by the user
            val isEnabled = mcpLocalServer.isServerEnabled(pluginId) // 从配置读取
            if (!isEnabled) {
                statusCallback(StartStatus.Error("Plugin not enabled by user: $pluginId"))
                return false
            }

            statusCallback(StartStatus.InProgress("Starting plugin: $pluginId"))

            val serverName = pluginInfo.name.replace(" ", "_").lowercase()
                .ifEmpty { pluginId.split("/").last().lowercase() }

            // Handle remote services differently
            if (serviceType == "remote") {
                val endpoint = pluginInfo.endpoint
                val connectionType = pluginInfo.connectionType

                if (endpoint == null) {
                    statusCallback(StartStatus.Error("Remote service is missing endpoint: $pluginId"))
                    return false
                }

                // Initialize bridge only if requested
                if (initBridgeFirst && !initBridge()) {
                    statusCallback(StartStatus.Error("Failed to initialize bridge for remote service"))
                    return false
                }

                val bridge = MCPBridge.getInstance(context)

                // Register remote service with the bridge
                val registerResult =
                    bridge.registerMcpService(
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

                // "Connect" the remote service to trigger a connection and verify
                val client = MCPBridgeClient(context, serverName)
                val connectSuccess = client.connect() // connect will try to spawn if not active
                if (!connectSuccess) {
                    statusCallback(StartStatus.Error("Failed to connect to remote MCP service"))
                    return false
                }

                statusCallback(StartStatus.Success("Remote service $pluginId connected successfully"))
                return true
            }

            // --- Existing logic for local plugins ---
            val pluginConfig = mcpLocalServer.getPluginConfig(pluginId)
            val config = parseConfigJson(pluginConfig)
            val extractedServerName =
                extractServerNameFromConfig(pluginConfig) ?: serverName

            // Get server command and args
            val serverConfig = config?.mcpServers?.get(extractedServerName)
            if (serverConfig == null) {
                statusCallback(StartStatus.Error("Invalid plugin config: $pluginId"))
                return false
            }

            // Check if plugin service is already running
            val clientForCheck = MCPBridgeClient(context, extractedServerName)
            if (clientForCheck.isActive()) {
                statusCallback(StartStatus.Success("Plugin $pluginId is already running"))
                return true
            }

            // Initialize bridge only if requested (for single plugin start)
            if (initBridgeFirst) {
                if (!initBridge()) {
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
            }

            statusCallback(StartStatus.InProgress("Starting plugin via bridge..."))

            // Use MCPBridge instance
            val bridge = MCPBridge.getInstance(context)
            val termuxPluginDir = "~/mcp_plugins/${pluginId.split("/").last()}"

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

            // Start and verify MCP service using the client
            val client = MCPBridgeClient(context, extractedServerName)
            val connectSuccess = client.connect()

            if (connectSuccess) {
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
                    progressListener.onAllPluginsStarted(
                        0,
                        0,
                        PluginInitStatus.TERMINAL_SERVICE_UNAVAILABLE
                    )
                    return@launch
                }

                // Initialize bridge ONCE, this will also reset existing services
                if (!initBridge()) {
                    val status =
                        if (pnpmInstalled == false) PluginInitStatus.NODEJS_MISSING else PluginInitStatus.BRIDGE_FAILED
                    progressListener.onAllPluginsStarted(0, 0, status)
                    return@launch
                }

                val mcpRepository = MCPRepository(context)
                val mcpLocalServer = MCPLocalServer.getInstance(context)

                // Get plugins to start: all enabled plugins
                val pluginList = mcpRepository.installedPluginIds.first()
                val pluginsToStart =
                    pluginList.filter { pluginId ->
                        mcpLocalServer.isServerEnabled(pluginId)
                    }

                if (pluginsToStart.isEmpty()) {
                    progressListener.onAllPluginsStarted(0, 0, PluginInitStatus.SUCCESS)
                    return@launch
                }

                // --- STAGE 1: Register all plugins ---
                val registrationJobs =
                    pluginsToStart.mapIndexed { index, pluginId ->
                        async {
                            progressListener.onPluginStarting(
                                pluginId,
                                index + 1,
                                pluginsToStart.size
                            )
                            val serviceName = registerPlugin(pluginId)
                            serviceName?.let {
                                progressListener.onPluginRegistered(pluginId, it, true)
                                pluginId to it // Return a pair of pluginId to serviceName
                            } ?: run {
                                progressListener.onPluginRegistered(pluginId, "", false)
                                null
                            }
                        }
                    }
                val registrationResults = registrationJobs.awaitAll().filterNotNull().toMap()
                val servicesToSpawn = registrationResults.values.toList()

                if (servicesToSpawn.isEmpty()) {
                    Log.w(TAG, "No plugins were successfully registered.")
                    progressListener.onAllPluginsStarted(
                        0,
                        pluginsToStart.size,
                        PluginInitStatus.SUCCESS
                    )
                    return@launch
                }

                // --- STAGE 2, 3, 4: Spawn, Verify, Process, and Unspawn in batches ---
                val allVerificationResults = mutableListOf<VerificationResult>()
                val batchSize = 4
                val serviceChunks = registrationResults.toList().chunked(batchSize)

                for ((chunkIndex, chunk) in serviceChunks.withIndex()) {
                    Log.d(TAG, "Processing plugin chunk ${chunkIndex + 1}/${serviceChunks.size}...")

                    // Spawn batch
                    val spawnJobs = chunk.map { (_, serviceName) ->
                        async {
                            MCPBridgeClient(context, serviceName).spawn()
                        }
                    }
                    spawnJobs.awaitAll()
                    Log.d(TAG, "Chunk ${chunkIndex + 1} spawned.")

                    // Give a moment for services to initialize after spawning
                    delay(1000)

                    // Verify batch
                    val verificationJobs = chunk.map { (pluginId, serviceName) ->
                        async {
                            val client = MCPBridgeClient(context, serviceName)
                            val startTime = System.currentTimeMillis()
                            val pingSuccess = client.ping()
                            val responseTime = System.currentTimeMillis() - startTime
                            VerificationResult(
                                pluginId = pluginId,
                                serviceName = serviceName,
                                isResponding = pingSuccess,
                                responseTime = responseTime,
                                details = if (pingSuccess) "Service is responding" else "Service not responding"
                            )
                        }
                    }
                    val chunkResults = verificationJobs.awaitAll()
                    allVerificationResults.addAll(chunkResults)
                    Log.d(TAG, "Chunk ${chunkIndex + 1} verified.")

                    // Process and Unspawn successful ones in the current batch
                    val successfulInChunk = chunkResults.filter { it.isResponding }
                    if (successfulInChunk.isNotEmpty()) {
                        Log.d(TAG, "Processing ${successfulInChunk.size} successful services in chunk ${chunkIndex + 1}.")
                        // 1. Generate descriptions for the current successful batch
                        generateMissingDescriptions(successfulInChunk)
                        // 2. Register tools for the current successful batch
                        registerToolsForVerifiedPlugins(successfulInChunk)
                        // 3. Unspawn the current successful batch
                        val unspawnJobs = successfulInChunk.map {
                            async {
                                MCPBridgeClient(context, it.serviceName).unspawn()
                            }
                        }
                        unspawnJobs.awaitAll()
                        Log.d(TAG, "Chunk ${chunkIndex + 1} processed and unspawned.")
                    }
                }

                // --- Final UI Update and Notification ---
                val successfulPlugins = allVerificationResults.filter { it.isResponding }

                pluginsToStart.forEachIndexed { index, pluginId ->
                    val isVerified = successfulPlugins.any { it.pluginId == pluginId }
                    progressListener.onPluginStarted(
                        pluginId,
                        isVerified,
                        index + 1,
                        pluginsToStart.size
                    )
                }

                val successCount = successfulPlugins.size
                Log.i(TAG, "All plugin batches processed. Total successful: $successCount")

                // --- FINAL: Notify completion ---
                progressListener.onAllPluginsStarted(
                    successCount,
                    pluginsToStart.size,
                    PluginInitStatus.SUCCESS
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error starting plugins", e)
                progressListener.onAllPluginsStarted(0, 0, PluginInitStatus.OTHER_ERROR)
            }
        }
    }

    /**
     * Registers a single plugin and returns its service name if successful.
     * This function contains the logic for auto-deployment and registration.
     */
    private suspend fun registerPlugin(pluginId: String): String? {
        try {
            val mcpRepository = MCPRepository(context)
            val pluginInfo = mcpRepository.getInstalledPluginInfo(pluginId) ?: return null

            // Auto-deploy if it's a local plugin and not deployed yet
            if (pluginInfo.type == "local") {
                val mcpLocalServer = MCPLocalServer.getInstance(context)
                if (mcpLocalServer.getServerStatus(pluginId)?.deploySuccess != true) {
                    val deployer = MCPDeployer(context)
                    val pluginPath = mcpRepository.getInstalledPluginPath(pluginId) ?: return null
                    val deployCommands =
                        if (pluginPath.startsWith("virtual://")) emptyList() else deployer.getDeployCommands(
                            pluginId,
                            pluginPath
                        )

                    if (deployCommands.isEmpty() && !pluginPath.startsWith("virtual://")) return null

                    var deploySuccess = false
                    deployer.deployPluginWithCommands(
                        pluginId,
                        pluginPath,
                        deployCommands,
                        emptyMap()
                    ) { status ->
                        if (status is MCPDeployer.DeploymentStatus.Success) deploySuccess = true
                    }
                    if (!deploySuccess) return null
                }
            }

            val bridge = MCPBridge.getInstance(context)
            val serverName = pluginInfo.name.replace(" ", "_").lowercase()
                .ifEmpty { pluginId.split("/").last().lowercase() }

            // 用于存储实际注册到Bridge的服务名
            var actualServiceName = serverName

            val registerResult = when (pluginInfo.type) {
                "remote" -> {
                    if (pluginInfo.endpoint == null) {
                        JSONObject().apply {
                            put("success", false)
                            put("error", "Endpoint is missing for remote plugin $pluginId")
                        }
                    } else {
                        bridge.registerMcpService(
                            name = serverName,
                            type = "remote",
                            endpoint = pluginInfo.endpoint,
                            connectionType = pluginInfo.connectionType,
                            description = "Remote MCP Server: $pluginId"
                        )
                    }
                }

                "local" -> {
                    val mcpLocalServer = MCPLocalServer.getInstance(context)
                    val pluginConfig = mcpLocalServer.getPluginConfig(pluginId)
                    val config = parseConfigJson(pluginConfig)
                    val extractedServerName =
                        extractServerNameFromConfig(pluginConfig) ?: serverName
                    val serverConfig = config?.mcpServers?.get(extractedServerName) ?: return null
                    val termuxPluginDir = "~/mcp_plugins/${pluginId.split("/").last()}"

                    // 更新实际使用的服务名
                    actualServiceName = extractedServerName

                    bridge.registerMcpService(
                        name = extractedServerName,
                        command = serverConfig.command,
                        args = serverConfig.args,
                        description = "MCP Server: $pluginId",
                        env = serverConfig.env,
                        cwd = termuxPluginDir
                    )
                }

                else -> null
            }

            return if (registerResult?.optBoolean("success", false) == true) actualServiceName else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register plugin $pluginId", e)
            return null
        }
    }

    /** Verify plugin statuses */
    private fun verifyPlugins(progressListener: PluginStartProgressListener) {
        starterScope.launch {
            try {
                delay(5000) // Wait for services to initialize
                val results = verifyAllMcpPlugins()

                // 自动生成空描述的工具包描述
                generateMissingDescriptions(results)

                // 注册验证成功的插件的工具
                registerToolsForVerifiedPlugins(results)

                progressListener.onAllPluginsVerified(results)
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying plugins", e)
                progressListener.onAllPluginsVerified(emptyList())
            }
        }
    }

    /**
     * 为验证成功的插件注册工具
     * 这确保只有真正就绪并响应的插件才会注册其工具
     */
    private suspend fun registerToolsForVerifiedPlugins(results: List<VerificationResult>) {
        try {
            val mcpRepository = MCPRepository(context)
            val successfulPluginIds = results
                .filter { it.isResponding }
                .map { it.pluginId }

            if (successfulPluginIds.isNotEmpty()) {
                Log.d(
                    TAG,
                    "开始为 ${successfulPluginIds.size} 个验证成功的插件注册工具: $successfulPluginIds"
                )
                mcpRepository.registerToolsForLoadedPlugins(successfulPluginIds)
                Log.d(TAG, "工具注册流程已完成")
            } else {
                Log.d(TAG, "没有验证成功的插件，跳过工具注册")
            }
        } catch (e: Exception) {
            Log.e(TAG, "注册验证成功插件的工具时出错", e)
        }
    }

    /**
     * 为没有描述的工具包自动生成描述
     */
    private suspend fun generateMissingDescriptions(results: List<VerificationResult>) {
        try {
            val mcpRepository = MCPRepository(context)
            val mcpLocalServer = MCPLocalServer.getInstance(context)

            // 筛选出成功响应的插件
            val respondingPlugins = results.filter { it.isResponding }

            for (result in respondingPlugins) {
                try {
                    // 获取插件信息
                    val pluginInfo = mcpRepository.getInstalledPluginInfo(result.pluginId)

                    // 检查描述是否为空
                    if (pluginInfo != null && pluginInfo.description.isBlank()) {
                        Log.d(TAG, "为插件 ${result.pluginId} 生成描述，当前描述为空")

                        // 获取工具描述
                        val client = MCPBridgeClient(context, result.serviceName)
                        val toolDescriptions = client.getToolDescriptions()

                        if (toolDescriptions.isNotEmpty()) {
                            // 调用EnhancedAIService生成描述
                            val generatedDescription =
                                com.ai.assistance.operit.api.chat.EnhancedAIService.generatePackageDescription(
                                    context = context,
                                    pluginName = pluginInfo.name,
                                    toolDescriptions = toolDescriptions
                                )

                            // 只有在AI成功生成描述时才保存，失败时保持原有的空描述
                            if (generatedDescription.isNotBlank()) {
                                val updatedMetadata =
                                    pluginInfo.copy(description = generatedDescription)
                                mcpLocalServer.addOrUpdatePluginMetadata(updatedMetadata)
                                Log.i(
                                    TAG,
                                    "已为插件 ${result.pluginId} 生成描述: $generatedDescription"
                                )
                            } else {
                                Log.w(TAG, "插件 ${result.pluginId} 的描述生成失败，保持原有空描述")
                            }
                        } else {
                            Log.w(TAG, "插件 ${result.pluginId} 没有可用的工具描述")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "为插件 ${result.pluginId} 生成描述时出错: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "生成缺失描述时出错", e)
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

