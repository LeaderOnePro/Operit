package com.ai.assistance.operit.data.mcp

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import com.ai.assistance.operit.data.mcp.plugins.MCPStarter
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * 统一的MCP配置管理中心
 * 
 * 负责管理所有MCP相关的配置，包括：
 * - 官方MCP配置格式的读写
 * - 插件配置管理
 * - 服务器状态管理
 * - 统一存储在下载/Operit/mcp_plugins目录
 */
class MCPLocalServer private constructor(private val context: Context) {
    companion object {
        private const val TAG = "MCPLocalServer"
        private const val PREFS_NAME = "mcp_local_server_prefs"
        private const val KEY_SERVER_PATH = "server_path"
        
        // 配置文件名称
        private const val MCP_CONFIG_FILE = "mcp_config.json"
        private const val PLUGIN_METADATA_FILE = "plugin_metadata.json"
        private const val SERVER_STATUS_FILE = "server_status.json"
        
        // 默认目录名称
        private const val OPERIT_DIR_NAME = "Operit"
        private const val MCP_PLUGINS_DIR_NAME = "mcp_plugins"

        @Volatile private var INSTANCE: MCPLocalServer? = null

        fun getInstance(context: Context): MCPLocalServer {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: MCPLocalServer(context.applicationContext).also { INSTANCE = it }
                    }
        }
    }

    // 持久化配置
    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 配置目录路径
    private val configBaseDir by lazy {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val operitDir = File(downloadsDir, OPERIT_DIR_NAME)
        val mcpPluginsDir = File(operitDir, MCP_PLUGINS_DIR_NAME)
        
        // 确保目录存在
        if (!operitDir.exists()) {
            operitDir.mkdirs()
        }
        if (!mcpPluginsDir.exists()) {
            mcpPluginsDir.mkdirs()
        }
        
        mcpPluginsDir
    }

    // 配置文件路径
    private val mcpConfigFile get() = File(configBaseDir, MCP_CONFIG_FILE)
    private val pluginMetadataFile get() = File(configBaseDir, PLUGIN_METADATA_FILE)
    private val serverStatusFile get() = File(configBaseDir, SERVER_STATUS_FILE)

    // 服务路径
    private val _serverPath = MutableStateFlow(configBaseDir.absolutePath)
    val serverPath: StateFlow<String> = _serverPath.asStateFlow()

    // 配置状态
    private val _mcpConfig = MutableStateFlow(MCPConfig())
    val mcpConfig: StateFlow<MCPConfig> = _mcpConfig.asStateFlow()

    // 插件元数据
    private val _pluginMetadata = MutableStateFlow<Map<String, PluginMetadata>>(emptyMap())
    val pluginMetadata: StateFlow<Map<String, PluginMetadata>> = _pluginMetadata.asStateFlow()

    // 服务器状态
    private val _serverStatus = MutableStateFlow<Map<String, ServerStatus>>(emptyMap())
    val serverStatus: StateFlow<Map<String, ServerStatus>> = _serverStatus.asStateFlow()

    // Gson实例 - 使用格式化输出
    private val gson = com.google.gson.GsonBuilder()
        .setPrettyPrinting()
        .create()

    init {
        // 初始化时加载所有配置
        loadAllConfigurations()
    }

    // ==================== 官方MCP配置格式支持 ====================

    /**
     * 官方MCP配置格式数据结构
     */
    @Serializable
    data class MCPConfig(
        @SerializedName("mcpServers")
        val mcpServers: MutableMap<String, ServerConfig> = mutableMapOf()
    ) {
        @Serializable
        data class ServerConfig(
            @SerializedName("command")
            val command: String,
            @SerializedName("args")
            val args: List<String> = emptyList(),
            @SerializedName("disabled")
            val disabled: Boolean = false,
            @SerializedName("autoApprove")
            val autoApprove: List<String> = emptyList(),
            @SerializedName("env")
            val env: Map<String, String> = emptyMap(),
            // 扩展字段，用于存储额外信息，使用String存储JSON
            @SerializedName("metadata")
            val metadata: String = "{}"
        )
    }

    /**
     * 插件元数据
     */
    data class PluginMetadata(
        @SerializedName("id")
        val id: String,
        @SerializedName("name")
        val name: String,
        @SerializedName("description")
        val description: String,
        @SerializedName("version")
        val version: String = "1.0.0",
        @SerializedName("author")
        val author: String = "Unknown",
        @SerializedName("category")
        val category: String = "General",
        @SerializedName("requiresApiKey")
        val requiresApiKey: Boolean = false,
        @SerializedName("isVerified")
        val isVerified: Boolean = false,
        @SerializedName("logoUrl")
        val logoUrl: String? = null,
        @SerializedName("repoUrl")
        val repoUrl: String = "",
        @SerializedName("longDescription")
        val longDescription: String = "",
        @SerializedName("type")
        val type: String = "local", // local, remote
        @SerializedName("endpoint")
        val endpoint: String? = null,
        @SerializedName("connectionType")
        val connectionType: String? = "httpStream",
        @SerializedName("installedPath")
        val installedPath: String? = null,
        @SerializedName("installedTime")
        val installedTime: Long = System.currentTimeMillis()
    )

    /**
     * 服务器运行状态
     */
    data class ServerStatus(
        @SerializedName("serverId")
        val serverId: String,
        @SerializedName("active")
        val active: Boolean = false,
        @SerializedName("isEnabled")
        val isEnabled: Boolean = true,
        @SerializedName("lastStartTime")
        val lastStartTime: Long = 0L,
        @SerializedName("lastStopTime")
        val lastStopTime: Long = 0L,
        @SerializedName("deploySuccess")
        val deploySuccess: Boolean = false,
        @SerializedName("lastDeployTime")
        val lastDeployTime: Long = 0L,
        @SerializedName("errorMessage")
        val errorMessage: String? = null
    )

    // ==================== 配置文件操作 ====================

    /**
     * 加载所有配置文件
     */
    private fun loadAllConfigurations() {
        try {
            // 加载MCP配置
            if (mcpConfigFile.exists()) {
                val configJson = mcpConfigFile.readText()
                val config = gson.fromJson(configJson, MCPConfig::class.java) ?: MCPConfig()
                _mcpConfig.value = config
            }

            // 加载插件元数据
            if (pluginMetadataFile.exists()) {
                val metadataJson = pluginMetadataFile.readText()
                val typeToken = object : TypeToken<Map<String, PluginMetadata>>() {}.type
                val metadata = gson.fromJson<Map<String, PluginMetadata>>(metadataJson, typeToken) ?: emptyMap()
                _pluginMetadata.value = metadata
            }

            // 加载服务器状态
            if (serverStatusFile.exists()) {
                val statusJson = serverStatusFile.readText()
                val typeToken = object : TypeToken<Map<String, ServerStatus>>() {}.type
                val status = gson.fromJson<Map<String, ServerStatus>>(statusJson, typeToken) ?: emptyMap()
                _serverStatus.value = status
            }

            Log.d(TAG, "配置加载完成 - MCP服务器: ${_mcpConfig.value.mcpServers.size}, 插件元数据: ${_pluginMetadata.value.size}")
        } catch (e: Exception) {
            Log.e(TAG, "加载配置时出错", e)
        }
    }

    /**
     * 保存MCP配置
     */
    suspend fun saveMCPConfig() {
        try {
            val configJson = gson.toJson(_mcpConfig.value)
            mcpConfigFile.writeText(configJson)
            Log.d(TAG, "MCP配置已保存")
        } catch (e: Exception) {
            Log.e(TAG, "保存MCP配置时出错", e)
        }
    }

    /**
     * 保存插件元数据
     */
    suspend fun savePluginMetadata() {
        try {
            val metadataJson = gson.toJson(_pluginMetadata.value)
            pluginMetadataFile.writeText(metadataJson)
            Log.d(TAG, "插件元数据已保存")
        } catch (e: Exception) {
            Log.e(TAG, "保存插件元数据时出错", e)
        }
    }

    /**
     * 保存服务器状态
     */
    suspend fun saveServerStatus() {
        try {
            val statusJson = gson.toJson(_serverStatus.value)
            serverStatusFile.writeText(statusJson)
            Log.d(TAG, "服务器状态已保存")
        } catch (e: Exception) {
            Log.e(TAG, "保存服务器状态时出错", e)
        }
    }

    // ==================== MCP服务器管理 ====================

    /**
     * 添加或更新MCP服务器配置
     */
    suspend fun addOrUpdateMCPServer(
        serverId: String,
        command: String,
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap(),
        disabled: Boolean = false,
        autoApprove: List<String> = emptyList(),
        metadata: Map<String, Any> = emptyMap()
    ) {
        val config = _mcpConfig.value
        config.mcpServers[serverId] = MCPConfig.ServerConfig(
            command = command,
            args = args,
            disabled = disabled,
            autoApprove = autoApprove,
            env = env,
            metadata = gson.toJson(metadata) // 将Map<String, Any>转换为JSON字符串
        )
        _mcpConfig.value = config
        saveMCPConfig()
        Log.d(TAG, "MCP服务器配置已更新: $serverId")
    }

    /**
     * 删除MCP服务器配置
     */
    suspend fun removeMCPServer(serverId: String) {
        val config = _mcpConfig.value
        config.mcpServers.remove(serverId)
        _mcpConfig.value = config
        saveMCPConfig()

        // 同时清理相关的元数据和状态
        removePluginMetadata(serverId)
        removeServerStatus(serverId)
        
        Log.d(TAG, "MCP服务器配置已删除: $serverId")
    }

    /**
     * 获取MCP服务器配置
     */
    fun getMCPServer(serverId: String): MCPConfig.ServerConfig? {
        return _mcpConfig.value.mcpServers[serverId]
    }

    /**
     * 获取所有MCP服务器配置
     */
    fun getAllMCPServers(): Map<String, MCPConfig.ServerConfig> {
        return _mcpConfig.value.mcpServers.toMap()
    }

    // ==================== 插件元数据管理 ====================

    /**
     * 添加或更新插件元数据
     */
    suspend fun addOrUpdatePluginMetadata(metadata: PluginMetadata) {
        val currentMetadata = _pluginMetadata.value.toMutableMap()
        currentMetadata[metadata.id] = metadata
        _pluginMetadata.value = currentMetadata
        savePluginMetadata()
        Log.d(TAG, "插件元数据已更新: ${metadata.id} - ${metadata.name}")
    }

    /**
     * 删除插件元数据
     */
    suspend fun removePluginMetadata(pluginId: String) {
        val currentMetadata = _pluginMetadata.value.toMutableMap()
        currentMetadata.remove(pluginId)
        _pluginMetadata.value = currentMetadata
        savePluginMetadata()
        Log.d(TAG, "插件元数据已删除: $pluginId")
    }

    /**
     * 获取插件元数据
     */
    fun getPluginMetadata(pluginId: String): PluginMetadata? {
        return _pluginMetadata.value[pluginId]
    }

    /**
     * 获取所有插件元数据
     */
    fun getAllPluginMetadata(): Map<String, PluginMetadata> {
        return _pluginMetadata.value.toMap()
    }

    // ==================== 服务器状态管理 ====================

    /**
     * 更新服务器状态
     */
    suspend fun updateServerStatus(
        serverId: String,
        active: Boolean? = null,
        isEnabled: Boolean? = null,
        deploySuccess: Boolean? = null,
        errorMessage: String? = null
    ) {
        val currentStatus = _serverStatus.value.toMutableMap()
        val existingStatus = currentStatus[serverId] ?: ServerStatus(serverId)
        
        val updatedStatus = existingStatus.copy(
            active = active ?: existingStatus.active,
            isEnabled = isEnabled ?: existingStatus.isEnabled,
            deploySuccess = deploySuccess ?: existingStatus.deploySuccess,
            errorMessage = errorMessage ?: existingStatus.errorMessage,
            lastStartTime = if (active == true) System.currentTimeMillis() else existingStatus.lastStartTime,
            lastStopTime = if (active == false) System.currentTimeMillis() else existingStatus.lastStopTime,
            lastDeployTime = if (deploySuccess == true) System.currentTimeMillis() else existingStatus.lastDeployTime
        )
        
        currentStatus[serverId] = updatedStatus
        _serverStatus.value = currentStatus
        saveServerStatus()
        Log.d(TAG, "服务器状态已更新: $serverId")
    }

    /**
     * 删除服务器状态
     */
    suspend fun removeServerStatus(serverId: String) {
        val currentStatus = _serverStatus.value.toMutableMap()
        currentStatus.remove(serverId)
        _serverStatus.value = currentStatus
        saveServerStatus()
        Log.d(TAG, "服务器状态已删除: $serverId")
    }

    /**
     * 获取服务器状态
     */
    fun getServerStatus(serverId: String): ServerStatus? {
        return _serverStatus.value[serverId]
    }

    /**
     * 获取所有服务器状态
     */
    fun getAllServerStatus(): Map<String, ServerStatus> {
        return _serverStatus.value.toMap()
    }

    // ==================== 兼容性方法 ====================

    /**
     * 获取插件配置（兼容旧接口）
     *
     * @param pluginId 插件ID
     * @return 配置内容JSON字符串，如果不存在返回空对象
     */
    fun getPluginConfig(pluginId: String): String {
        val serverConfig = getMCPServer(pluginId)
        return if (serverConfig != null) {
            val configForOnePlugin = MCPConfig(
                mcpServers = mutableMapOf(pluginId to serverConfig)
            )
            gson.toJson(configForOnePlugin)
        } else {
            gson.toJson(MCPConfig())
        }
    }

    /**
     * 保存插件配置（兼容旧接口）
     *
     * @param pluginId 插件ID
     * @param config 配置内容JSON字符串
     * @return 是否保存成功
     */
    suspend fun savePluginConfig(pluginId: String, config: String): Boolean {
        return try {
            // 尝试解析为ServerConfig
            val serverConfig = gson.fromJson(config, MCPConfig.ServerConfig::class.java)
            val currentConfig = _mcpConfig.value
            currentConfig.mcpServers[pluginId] = serverConfig
            _mcpConfig.value = currentConfig
            saveMCPConfig()
            true
        } catch (e: Exception) {
            Log.e(TAG, "保存插件配置失败: $pluginId", e)
            false
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 导出配置为JSON字符串
     */
    fun exportConfigAsJson(): String {
        val exportData = mapOf(
            "mcpConfig" to _mcpConfig.value,
            "pluginMetadata" to _pluginMetadata.value,
            "serverStatus" to _serverStatus.value,
            "exportTime" to System.currentTimeMillis(),
            "version" to "1.0"
        )
        return gson.toJson(exportData)
    }

    /**
     * 从JSON字符串导入配置
     */
    suspend fun importConfigFromJson(json: String): Boolean {
        return try {
            val typeToken = object : TypeToken<Map<String, Any>>() {}.type
            val importData = gson.fromJson<Map<String, Any>>(json, typeToken)
            
            importData["mcpConfig"]?.let { config ->
                val configJson = gson.toJson(config)
                val mcpConfig = gson.fromJson(configJson, MCPConfig::class.java)
                _mcpConfig.value = mcpConfig
                saveMCPConfig()
            }
            
            importData["pluginMetadata"]?.let { metadata ->
                val metadataJson = gson.toJson(metadata)
                val typeToken2 = object : TypeToken<Map<String, PluginMetadata>>() {}.type
                val pluginMetadata = gson.fromJson<Map<String, PluginMetadata>>(metadataJson, typeToken2)
                _pluginMetadata.value = pluginMetadata
                savePluginMetadata()
            }
            
            importData["serverStatus"]?.let { status ->
                val statusJson = gson.toJson(status)
                val typeToken3 = object : TypeToken<Map<String, ServerStatus>>() {}.type
                val serverStatus = gson.fromJson<Map<String, ServerStatus>>(statusJson, typeToken3)
                _serverStatus.value = serverStatus
                saveServerStatus()
            }
            
            Log.d(TAG, "配置导入成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "导入配置失败", e)
            false
        }
    }

    /**
     * 获取配置目录路径
     */
    fun getConfigDirectory(): String = configBaseDir.absolutePath

    /**
     * 清理无效配置
     */
    suspend fun cleanupInvalidConfigurations() {
        try {
            // 清理不存在的插件配置
            val validPluginIds = _pluginMetadata.value.keys
            val mcpConfig = _mcpConfig.value
            val serversToRemove = mcpConfig.mcpServers.keys.filter { it !in validPluginIds }
            
            serversToRemove.forEach { serverId ->
                mcpConfig.mcpServers.remove(serverId)
            }
            
            if (serversToRemove.isNotEmpty()) {
                _mcpConfig.value = mcpConfig
                saveMCPConfig()
                Log.d(TAG, "清理了 ${serversToRemove.size} 个无效的MCP服务器配置")
            }
            
            // 清理无效的服务器状态
            val statusToRemove = _serverStatus.value.keys.filter { it !in validPluginIds }
            if (statusToRemove.isNotEmpty()) {
                val currentStatus = _serverStatus.value.toMutableMap()
                statusToRemove.forEach { serverId ->
                    currentStatus.remove(serverId)
                }
                _serverStatus.value = currentStatus
                saveServerStatus()
                Log.d(TAG, "清理了 ${statusToRemove.size} 个无效的服务器状态")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "清理配置时出错", e)
        }
    }
}