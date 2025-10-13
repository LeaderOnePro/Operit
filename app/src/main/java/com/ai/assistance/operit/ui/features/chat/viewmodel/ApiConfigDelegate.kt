package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 委托类，负责管理用户偏好配置和API密钥 */
class ApiConfigDelegate(
        private val context: Context,
        private val viewModelScope: CoroutineScope,
        private val onConfigChanged: (EnhancedAIService) -> Unit
) {
    companion object {
        private const val TAG = "ApiConfigDelegate"
        private const val DEFAULT_CONFIG_ID = "default" // 默认配置ID
    }

    // Preferences
    private val apiPreferences = ApiPreferences.getInstance(context)
    private val modelConfigManager = ModelConfigManager(context)

    // State flows
    private val _isConfigured = MutableStateFlow(true) // 默认已配置
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _enableAiPlanning = MutableStateFlow(ApiPreferences.DEFAULT_ENABLE_AI_PLANNING)
    val enableAiPlanning: StateFlow<Boolean> = _enableAiPlanning.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(ApiPreferences.DEFAULT_KEEP_SCREEN_ON)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _enableThinkingMode = MutableStateFlow(ApiPreferences.DEFAULT_ENABLE_THINKING_MODE)
    val enableThinkingMode: StateFlow<Boolean> = _enableThinkingMode.asStateFlow()

    private val _enableThinkingGuidance =
            MutableStateFlow(ApiPreferences.DEFAULT_ENABLE_THINKING_GUIDANCE)
    val enableThinkingGuidance: StateFlow<Boolean> = _enableThinkingGuidance.asStateFlow()

    private val _enableMemoryAttachment =
            MutableStateFlow(ApiPreferences.DEFAULT_ENABLE_MEMORY_ATTACHMENT)
    val enableMemoryAttachment: StateFlow<Boolean> = _enableMemoryAttachment.asStateFlow()

    private val _enableAutoRead =
            MutableStateFlow(ApiPreferences.DEFAULT_ENABLE_AUTO_READ)
    val enableAutoRead: StateFlow<Boolean> = _enableAutoRead.asStateFlow()

    private val _contextLength = MutableStateFlow(ApiPreferences.DEFAULT_CONTEXT_LENGTH)
    val contextLength: StateFlow<Float> = _contextLength.asStateFlow()

    private val _summaryTokenThreshold =
            MutableStateFlow(ApiPreferences.DEFAULT_SUMMARY_TOKEN_THRESHOLD)
    val summaryTokenThreshold: StateFlow<Float> = _summaryTokenThreshold.asStateFlow()

    private val _enableSummary = MutableStateFlow(ApiPreferences.DEFAULT_ENABLE_SUMMARY)
    val enableSummary: StateFlow<Boolean> = _enableSummary.asStateFlow()

    private val _enableSummaryByMessageCount = MutableStateFlow(ApiPreferences.DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT)
    val enableSummaryByMessageCount: StateFlow<Boolean> = _enableSummaryByMessageCount.asStateFlow()

    private val _summaryMessageCountThreshold = MutableStateFlow(ApiPreferences.DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD)
    val summaryMessageCountThreshold: StateFlow<Int> = _summaryMessageCountThreshold.asStateFlow()

    private val _enableTools = MutableStateFlow(ApiPreferences.DEFAULT_ENABLE_TOOLS)
    val enableTools: StateFlow<Boolean> = _enableTools.asStateFlow()

    // 为了兼容现有代码，添加API密钥状态流
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _apiEndpoint = MutableStateFlow("")
    val apiEndpoint: StateFlow<String> = _apiEndpoint.asStateFlow()

    private val _modelName = MutableStateFlow("")
    val modelName: StateFlow<String> = _modelName.asStateFlow()

    private val _apiProviderType = MutableStateFlow(ApiProviderType.DEEPSEEK)
    val apiProviderType: StateFlow<ApiProviderType> = _apiProviderType.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    init {
        // 异步初始化ModelConfigManager和加载配置
        viewModelScope.launch {
            try {
                modelConfigManager.initializeIfNeeded()
                
                // 获取默认配置并设置到当前状态
                val defaultConfig = modelConfigManager.getModelConfigFlow(ModelConfigManager.DEFAULT_CONFIG_ID).first()
                _apiKey.value = defaultConfig.apiKey
                _apiEndpoint.value = defaultConfig.apiEndpoint
                _modelName.value = defaultConfig.modelName
                _apiProviderType.value = defaultConfig.apiProviderType
                
                // 标记初始化完成
                _isInitialized.value = true
            } catch (e: Exception) {
                Log.e(TAG, "初始化ApiConfigDelegate时出错", e)
                // 即使出错也标记为已初始化，避免无限等待
                _isInitialized.value = true
            }
        }

        // 加载用户偏好设置
        initializeSettingsCollection()

        // 异步创建AI服务实例，避免在主线程上执行阻塞操作
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "开始在后台线程创建EnhancedAIService")
            val enhancedAiService = EnhancedAIService.getInstance(context)
            Log.d(TAG, "EnhancedAIService创建完成")
            withContext(Dispatchers.Main) {
                onConfigChanged(enhancedAiService)
            }
        }
    }

    private fun initializeSettingsCollection() {
        // Collect AI planning setting
        viewModelScope.launch {
            apiPreferences.enableAiPlanningFlow.collect { enableAiPlanningValue ->
                _enableAiPlanning.value = enableAiPlanningValue
            }
        }

        // Collect thinking mode setting
        viewModelScope.launch {
            apiPreferences.enableThinkingModeFlow.collect { enabled ->
                _enableThinkingMode.value = enabled
            }
        }

        // Collect thinking guidance setting
        viewModelScope.launch {
            apiPreferences.enableThinkingGuidanceFlow.collect { enabled ->
                _enableThinkingGuidance.value = enabled
            }
        }

        // Collect memory attachment setting
        viewModelScope.launch {
            apiPreferences.enableMemoryAttachmentFlow.collect { enabled ->
                _enableMemoryAttachment.value = enabled
            }
        }

        // Collect auto read setting
        viewModelScope.launch {
            apiPreferences.enableAutoReadFlow.collect { enabled ->
                _enableAutoRead.value = enabled
            }
        }

        // Collect keep screen on setting
        viewModelScope.launch {
            apiPreferences.keepScreenOnFlow.collect { enabled ->
                _keepScreenOn.value = enabled
            }
        }

        // Collect context length setting
        viewModelScope.launch {
            apiPreferences.contextLengthFlow.collect { length ->
                _contextLength.value = length
            }
        }

        // Collect summary token threshold setting
        viewModelScope.launch {
            apiPreferences.summaryTokenThresholdFlow.collect { threshold ->
                _summaryTokenThreshold.value = threshold
            }
        }

        // Collect enable summary setting
        viewModelScope.launch {
            apiPreferences.enableSummaryFlow.collect { enabled ->
                _enableSummary.value = enabled
            }
        }

        // Collect enable summary by message count setting
        viewModelScope.launch {
            apiPreferences.enableSummaryByMessageCountFlow.collect { enabled ->
                _enableSummaryByMessageCount.value = enabled
            }
        }

        // Collect summary message count threshold setting
        viewModelScope.launch {
            apiPreferences.summaryMessageCountThresholdFlow.collect { threshold ->
                _summaryMessageCountThreshold.value = threshold
            }
        }

        // Collect enable tools setting
        viewModelScope.launch {
            apiPreferences.enableToolsFlow.collect { enabled ->
                _enableTools.value = enabled
            }
        }
    }

    /**
     * 使用默认配置继续
     * @return 总是返回true，因为无需特定配置
     */
    fun useDefaultConfig(): Boolean {
        // 异步创建服务，避免阻塞
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "使用默认配置初始化服务")
            val enhancedAiService = EnhancedAIService.getInstance(context)
            withContext(Dispatchers.Main) {
                // 通知ViewModel配置已更改
                onConfigChanged(enhancedAiService)
            }
        }
        return true
    }

    /** 更新API密钥 */
    fun updateApiKey(key: String) {
        _apiKey.value = key
    }

    /** 更新API端点 */
    fun updateApiEndpoint(endpoint: String) {
        _apiEndpoint.value = endpoint
    }

    /** 更新模型名称 */
    fun updateModelName(modelName: String) {
        _modelName.value = modelName
    }

    /** 更新API提供商类型 */
    fun updateApiProviderType(providerType: ApiProviderType) {
        _apiProviderType.value = providerType
    }

    /** 保存API设置 */
    fun saveApiSettings() {
        viewModelScope.launch {
            try {
                // 获取当前配置
                val currentConfig = modelConfigManager.getModelConfigFlow(DEFAULT_CONFIG_ID).first()

                // 更新所有API相关配置
                modelConfigManager.updateModelConfig(
                        DEFAULT_CONFIG_ID,
                        _apiKey.value,
                        _apiEndpoint.value,
                        _modelName.value,
                        _apiProviderType.value
                )

                Log.d(TAG, "API配置已保存到ModelConfigManager")

                // 在IO线程上创建服务，避免阻塞
                val enhancedAiService = withContext(Dispatchers.IO) {
                    EnhancedAIService.getInstance(context)
                }

                // 通知ViewModel配置已更改
                onConfigChanged(enhancedAiService)

                // 更新已配置状态
                _isConfigured.value = true
            } catch (e: Exception) {
                Log.e(TAG, "保存API密钥失败: ${e.message}", e)
            }
        }
    }

    /** 切换AI计划功能 */
    fun toggleAiPlanning() {
        viewModelScope.launch {
            val newValue = !_enableAiPlanning.value
            apiPreferences.saveEnableAiPlanning(newValue)
            _enableAiPlanning.value = newValue
        }
    }

    /** 切换思考模式 */
    fun toggleThinkingMode() {
        viewModelScope.launch {
            val newValue = !_enableThinkingMode.value
            apiPreferences.saveEnableThinkingMode(newValue)
            _enableThinkingMode.value = newValue
        }
    }

    /** 切换思考引导 */
    fun toggleThinkingGuidance() {
        viewModelScope.launch {
            val newValue = !_enableThinkingGuidance.value
            apiPreferences.saveEnableThinkingGuidance(newValue)
            _enableThinkingGuidance.value = newValue
        }
    }

    /** 切换记忆附着 */
    fun toggleMemoryAttachment() {
        viewModelScope.launch {
            val newValue = !_enableMemoryAttachment.value
            apiPreferences.saveEnableMemoryAttachment(newValue)
            _enableMemoryAttachment.value = newValue
        }
    }

    /** 切换自动朗读 */
    fun toggleAutoRead() {
        viewModelScope.launch {
            val newValue = !_enableAutoRead.value
            apiPreferences.saveEnableAutoRead(newValue)
            _enableAutoRead.value = newValue
        }
    }

    /** 更新上下文长度 */
    fun updateContextLength(length: Float) {
        viewModelScope.launch {
            apiPreferences.saveContextLength(length)
            _contextLength.value = length
        }
    }

    fun updateSummaryTokenThreshold(threshold: Float) {
        viewModelScope.launch {
            apiPreferences.saveSummaryTokenThreshold(threshold)
            _summaryTokenThreshold.value = threshold
        }
    }

    /** 切换启用总结功能 */
    fun toggleEnableSummary() {
        viewModelScope.launch {
            val newValue = !_enableSummary.value
            apiPreferences.saveEnableSummary(newValue)
            _enableSummary.value = newValue
        }
    }

    /** 切换按消息数量启用总结 */
    fun toggleEnableSummaryByMessageCount() {
        viewModelScope.launch {
            val newValue = !_enableSummaryByMessageCount.value
            apiPreferences.saveEnableSummaryByMessageCount(newValue)
            _enableSummaryByMessageCount.value = newValue
        }
    }

    /** 更新总结消息数量阈值 */
    fun updateSummaryMessageCountThreshold(threshold: Int) {
        viewModelScope.launch {
            apiPreferences.saveSummaryMessageCountThreshold(threshold)
            _summaryMessageCountThreshold.value = threshold
        }
    }

    /** 切换工具启用/禁用 */
    fun toggleTools() {
        viewModelScope.launch {
            val newValue = !_enableTools.value
            apiPreferences.saveEnableTools(newValue)
            _enableTools.value = newValue
        }
    }
}
