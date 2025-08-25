package com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.mcp.InstallProgress
import com.ai.assistance.operit.data.mcp.InstallResult
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.net.Uri

/** ViewModel for MCP 服务器管理，包括安装、卸载等功能 */
class MCPViewModel(private val repository: MCPRepository) : ViewModel() {

    // 当前安装进度
    private val _installProgress = MutableStateFlow<InstallProgress?>(null)
    val installProgress: StateFlow<InstallProgress?> = _installProgress.asStateFlow()

    // 安装结果
    private val _installResult = MutableStateFlow<InstallResult?>(null)
    val installResult: StateFlow<InstallResult?> = _installResult.asStateFlow()

    // 当前正在操作的服务器
    private val _currentServer = MutableStateFlow<MCPServer?>(null)
    val currentServer: StateFlow<MCPServer?> = _currentServer.asStateFlow()

    // 插件已安装路径缓存
    private val installedPathsCache = mutableMapOf<String, String?>()
    
    // 存储选中的ZIP文件URI
    private var selectedZipUri: Uri? = null

    init {
        // 同步已安装状态
        viewModelScope.launch { repository.syncInstalledStatus() }
    }

    /** 安装服务器插件 */
    fun installServer(server: MCPServer) {
        viewModelScope.launch {
            _currentServer.value = server
            _installProgress.value = InstallProgress.Preparing
            _installResult.value = null

            val result =
                    repository.installMCPServer(server.id) { progress ->
                        _installProgress.value = progress
                    }

            _installResult.value = result

            // 清除缓存
            installedPathsCache.remove(server.id)
        }
    }
    
    /** 安装服务器插件 - 使用服务器对象（用于导入Git URL） */
    fun installServerWithObject(server: MCPServer) {
        viewModelScope.launch {
            _currentServer.value = server
            _installProgress.value = InstallProgress.Preparing
            _installResult.value = null

            val result =
                    repository.installMCPServerWithObject(server) { progress ->
                        _installProgress.value = progress
                    }

            _installResult.value = result

            // 清除缓存
            installedPathsCache.remove(server.id)
        }
    }
    
    /** 从ZIP文件安装服务器插件 */
    fun installServerFromZip(server: MCPServer, zipFilePath: String) {
        viewModelScope.launch {
            _currentServer.value = server
            _installProgress.value = InstallProgress.Preparing
            _installResult.value = null
            
            if (selectedZipUri == null) {
                _installResult.value = InstallResult.Error("未选择ZIP文件")
                _installProgress.value = InstallProgress.Finished
                return@launch
            }

            val result = repository.installMCPServerFromZip(
                server.id,
                selectedZipUri!!,
                server.name,
                server.description,
                server.author
            ) { progress ->
                _installProgress.value = progress
            }

            _installResult.value = result
            
            // 安装完成后清除URI
            selectedZipUri = null
            
            // 清除缓存
            installedPathsCache.remove(server.id)
        }
    }
    
    /** 设置选中的ZIP文件URI */
    fun setSelectedZipUri(uri: Uri) {
        selectedZipUri = uri
    }

    /** 卸载服务器插件 */
    fun uninstallServer(server: MCPServer) {
        viewModelScope.launch {
            _currentServer.value = server
            _installProgress.value = InstallProgress.Preparing
            _installResult.value = null

            val success = repository.uninstallMCPServer(server.id)

            _installResult.value =
                    if (success) {
                        InstallResult.Success("")
                    } else {
                        InstallResult.Error("卸载失败")
                    }

            _installProgress.value = InstallProgress.Finished

            // 清除缓存
            installedPathsCache.remove(server.id)
        }
    }

    /** Adds a remote server to the repository */
    fun addRemoteServer(server: com.ai.assistance.operit.data.mcp.MCPServer) {
        viewModelScope.launch {
            repository.addRemoteServer(server)
        }
    }

    /** Updates a remote server's metadata */
    fun updateRemoteServer(server: com.ai.assistance.operit.data.mcp.MCPServer) {
        viewModelScope.launch {
            repository.updateRemoteServer(server)
        }
    }

    /** 重置安装状态 */
    fun resetInstallState() {
        _installProgress.value = null
        _installResult.value = null
        _currentServer.value = null
    }

    /** 获取已安装插件的路径 */
    fun getInstalledPath(serverId: String): String? {
        // 先查缓存
        if (installedPathsCache.containsKey(serverId)) {
            return installedPathsCache[serverId]
        }

        // 从存储库查询
        val path = repository.getInstalledPluginPath(serverId)
        installedPathsCache[serverId] = path
        return path
    }

    /** 获取本地插件信息，无需网络请求 */
    fun getLocalPluginDetails(serverId: String): MCPServer? {
        // 如果插件没有安装，返回null
        if (!repository.isPluginInstalled(serverId)) {
            return null
        }

        // 从当前列表中查找，这里的信息已经通过updateInstalledStatus更新过，
        // 包含了本地元数据
        return repository.mcpServers.value.find { it.id == serverId }
    }

    /** 刷新本地插件列表 */
    fun refreshLocalPlugins() {
        viewModelScope.launch {
            repository.syncInstalledStatus()
            // 清除路径缓存，强制重新读取
            installedPathsCache.clear()
        }
    }

    /** 刷新插件列表 */
    fun refreshPluginList() {
        viewModelScope.launch {
            repository.syncInstalledStatus()
        }
    }

    /** 检查插件是否已安装 */
    fun isPluginInstalled(serverId: String): Boolean {
        return repository.isPluginInstalled(serverId)
    }

    /** 同步所有插件的安装状态 */
    fun syncInstalledStatus() {
        viewModelScope.launch { repository.syncInstalledStatus() }
    }

    /** ViewModel Factory */
    class Factory(private val repository: MCPRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MCPViewModel::class.java)) {
                return MCPViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
