package com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.plugins.MCPDeployer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * MCP部署ViewModel
 *
 * 负责处理插件部署业务逻辑
 */
class MCPDeployViewModel(private val context: Context, private val mcpRepository: MCPRepository) :
        ViewModel() {

    private val mcpDeployer = MCPDeployer(context)

    // 部署状态
    private val _deploymentStatus =
            MutableStateFlow<MCPDeployer.DeploymentStatus>(MCPDeployer.DeploymentStatus.NotStarted)
    val deploymentStatus: StateFlow<MCPDeployer.DeploymentStatus> = _deploymentStatus.asStateFlow()

    // 当前正在部署的插件ID
    private val _currentDeployingPlugin = MutableStateFlow<String?>(null)
    val currentDeployingPlugin: StateFlow<String?> = _currentDeployingPlugin.asStateFlow()

    // 部署输出消息
    private val _outputMessages = MutableStateFlow<List<String>>(emptyList())
    val outputMessages: StateFlow<List<String>> = _outputMessages.asStateFlow()

    // 生成的部署命令
    private val _generatedCommands = MutableStateFlow<List<String>>(emptyList())
    val generatedCommands: StateFlow<List<String>> = _generatedCommands.asStateFlow()

    // 环境变量
    private val _environmentVariables = MutableStateFlow<Map<String, String>>(emptyMap())
    val environmentVariables: StateFlow<Map<String, String>> = _environmentVariables.asStateFlow()

    /**
     * 获取部署命令 - 在部署前调用，用于显示和编辑
     *
     * @param pluginId 插件ID
     * @return 是否成功获取命令
     */
    fun getDeployCommands(pluginId: String): Boolean {
        // 获取插件路径
        val pluginPath = mcpRepository.getInstalledPluginPath(pluginId)
        if (pluginPath == null) {
            _deploymentStatus.value = MCPDeployer.DeploymentStatus.Error("无法获取插件路径: $pluginId")
            return false
        }

        // 在协程中调用挂起函数
        viewModelScope.launch {
            try {
                // 使用MCPDeployer的getDeployCommands方法
                val deployCommands = mcpDeployer.getDeployCommands(pluginId, pluginPath)
                
                if (deployCommands.isEmpty()) {
                    _deploymentStatus.value =
                            MCPDeployer.DeploymentStatus.Error("无法确定如何部署此插件，请查看README手动部署")
                } else {
                    // 保存生成的命令
                    _generatedCommands.value = deployCommands
                }
            } catch (e: Exception) {
                _deploymentStatus.value = MCPDeployer.DeploymentStatus.Error("分析插件时出错: ${e.message}")
            }
        }
        
        return true
    }

    /**
     * 设置环境变量
     *
     * @param envVars 环境变量键值对
     */
    fun setEnvironmentVariables(envVars: Map<String, String>) {
        _environmentVariables.value = envVars
    }

    /**
     * 添加单个环境变量
     *
     * @param key 变量名
     * @param value 变量值
     */
    fun addEnvironmentVariable(key: String, value: String) {
        val currentVars = _environmentVariables.value.toMutableMap()
        currentVars[key] = value
        _environmentVariables.value = currentVars
    }

    /**
     * 移除环境变量
     *
     * @param key 要移除的环境变量名
     */
    fun removeEnvironmentVariable(key: String) {
        val currentVars = _environmentVariables.value.toMutableMap()
        currentVars.remove(key)
        _environmentVariables.value = currentVars
    }

    /** 清除环境变量 */
    fun clearEnvironmentVariables() {
        _environmentVariables.value = emptyMap()
    }

    /**
     * 部署插件（使用默认生成的命令）
     *
     * @param pluginId 要部署的插件ID
     */
    fun deployPlugin(pluginId: String) {
        if (_generatedCommands.value.isEmpty()) {
            // 如果没有预先获取命令，先获取
            if (!getDeployCommands(pluginId)) {
                // 获取命令失败，状态已在getDeployCommands中更新
                return
            }
        }

        // 使用生成的命令部署
        deployPluginWithCommands(pluginId, _generatedCommands.value)
    }

    /**
     * 使用自定义命令部署插件
     *
     * @param pluginId 要部署的插件ID
     * @param customCommands 自定义命令列表
     */
    fun deployPluginWithCommands(pluginId: String, customCommands: List<String>) {
        // 获取插件路径
        val pluginPath = mcpRepository.getInstalledPluginPath(pluginId)
        if (pluginPath == null) {
            _deploymentStatus.value = MCPDeployer.DeploymentStatus.Error("无法获取插件路径: $pluginId")
            return
        }

        // 清除之前的输出
        _outputMessages.value = emptyList()

        // 设置当前部署的插件
        _currentDeployingPlugin.value = pluginId

        // 开始部署
        viewModelScope.launch {
            // 更新状态
            _deploymentStatus.value = MCPDeployer.DeploymentStatus.InProgress("正在准备部署插件")

            // 执行部署，包含环境变量
            mcpDeployer.deployPluginWithCommands(
                    pluginId = pluginId,
                    pluginPath = pluginPath,
                    customCommands = customCommands,
                    environmentVariables = _environmentVariables.value,
                    statusCallback = { status ->
                        _deploymentStatus.value = status

                        // 收集输出消息
                        if (status is MCPDeployer.DeploymentStatus.InProgress) {
                            if (status.message.startsWith("输出:")) {
                                val newMessage = status.message.removePrefix("输出:").trim()
                                if (newMessage.isNotEmpty()) {
                                    _outputMessages.value = _outputMessages.value + newMessage
                                }
                            }
                        }
                    }
            )
        }
    }

    /** 重置部署状态 */
    fun resetDeploymentState() {
        _deploymentStatus.value = MCPDeployer.DeploymentStatus.NotStarted
        _currentDeployingPlugin.value = null
        _outputMessages.value = emptyList()
        _generatedCommands.value = emptyList()
        // 不重置环境变量，因为可能需要在多次部署中重用
    }

    /** 工厂类 */
    class Factory(private val context: Context, private val mcpRepository: MCPRepository) :
            ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MCPDeployViewModel::class.java)) {
                return MCPDeployViewModel(context, mcpRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
