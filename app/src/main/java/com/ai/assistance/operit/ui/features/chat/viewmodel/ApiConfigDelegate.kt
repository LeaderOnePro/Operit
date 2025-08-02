package com.ai.assistance.operit.ui.features.chat.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.api.chat.EnhancedAIService
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
    private val apiPreferences = ApiPreferences(context)
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

    private val _contextLength = MutableStateFlow(ApiPreferences.DEFAULT_CONTEXT_LENGTH)
    val contextLength: StateFlow<Float> = _contextLength.asStateFlow()

    private val _summaryTokenThreshold =
            MutableStateFlow(ApiPreferences.DEFAULT_SUMMARY_TOKEN_THRESHOLD)
    val summaryTokenThreshold: StateFlow<Float> = _summaryTokenThreshold.asStateFlow()

    // 为了兼容现有代码，添加API密钥状态流
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    init {
        // 初始化ModelConfigManager
        viewModelScope.launch {
            modelConfigManager.initializeIfNeeded()

            // 加载默认配置中的API密钥
            try {
                val defaultConfig = modelConfigManager.getModelConfigFlow(DEFAULT_CONFIG_ID).first()
                _apiKey.value = defaultConfig.apiKey

                Log.d(TAG, "已从ModelConfigManager加载默认API密钥")
            } catch (e: Exception) {
                Log.e(TAG, "加载默认API密钥失败: ${e.message}", e)
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

    /** 保存API设置 */
    fun saveApiSettings() {
        viewModelScope.launch {
            try {
                // 获取当前配置
                val currentConfig = modelConfigManager.getModelConfigFlow(DEFAULT_CONFIG_ID).first()

                // 只更新API密钥，保留其他现有配置
                modelConfigManager.updateModelConfig(
                        DEFAULT_CONFIG_ID,
                        _apiKey.value,
                        currentConfig.apiEndpoint, // 保留现有端点
                        currentConfig.modelName, // 保留现有模型名称
                        currentConfig.apiProviderType // 保留现有提供商类型
                )

                Log.d(TAG, "API密钥已保存到ModelConfigManager")

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
}
