package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.CustomParameterData
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.data.model.ParameterValueType
import com.ai.assistance.operit.data.model.StandardModelParameters
import com.ai.assistance.operit.data.model.ApiProviderType
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// 为ModelConfig创建专用的DataStore
private val Context.modelConfigDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "model_configs")

// 获取ApiPreferences的DataStore
private val Context.apiDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "api_settings")

class ModelConfigManager(private val context: Context) {

    // 提供context访问器
    val appContext: Context
        get() = context

    // 定义key
    companion object {
        // 配置相关key
        val CONFIG_LIST_KEY = stringPreferencesKey("config_list")

        // 默认值
        const val DEFAULT_CONFIG_ID = "default"
        const val DEFAULT_CONFIG_NAME = "默认配置"

        // Default API provider type
        private val DEFAULT_API_PROVIDER_TYPE = ApiProviderType.DEEPSEEK
    }

    // Json解析器，支持宽松模式
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // 获取所有配置ID列表
    val configListFlow: Flow<List<String>> =
            context.modelConfigDataStore.data.map { preferences ->
                val configList = preferences[CONFIG_LIST_KEY] ?: ""
                if (configList.isEmpty()) emptyList()
                else json.decodeFromString<List<String>>(configList)
            }

    // 删除获取当前活跃配置ID的流

    // 初始化，确保至少有一个默认配置
    suspend fun initializeIfNeeded() {
        // 检查配置列表，如果为空则创建默认配置
        // This is important for first-time users
        val configList = configListFlow.first()
        if (configList.isEmpty()) {
            val defaultConfig = createFreshDefaultConfig()
            saveConfigToDataStore(defaultConfig)

            // 保存配置列表，移除活跃ID
            context.modelConfigDataStore.edit { preferences ->
                preferences[CONFIG_LIST_KEY] = json.encodeToString(listOf(DEFAULT_CONFIG_ID))
            }
        } else {
            Log.d("CONFIG_TIMING", "配置列表不为空，跳过初始化")
        }
    }

    // 从原有ApiPreferences创建默认配置
    private fun createFreshDefaultConfig(): ModelConfigData {
        return ModelConfigData(
                id = DEFAULT_CONFIG_ID,
                name = DEFAULT_CONFIG_NAME,
                apiKey = ApiPreferences.DEFAULT_API_KEY,
                apiEndpoint = ApiPreferences.DEFAULT_API_ENDPOINT,
                modelName = ApiPreferences.DEFAULT_MODEL_NAME,
                apiProviderType = DEFAULT_API_PROVIDER_TYPE,
                hasCustomParameters = false,
                maxTokensEnabled = false,
                temperatureEnabled = false,
                topPEnabled = false,
                topKEnabled = false,
                presencePenaltyEnabled = false,
                frequencyPenaltyEnabled = false,
                repetitionPenaltyEnabled = false,
                maxTokens = StandardModelParameters.DEFAULT_MAX_TOKENS,
                temperature = StandardModelParameters.DEFAULT_TEMPERATURE,
                topP = StandardModelParameters.DEFAULT_TOP_P,
                topK = StandardModelParameters.DEFAULT_TOP_K,
                presencePenalty = StandardModelParameters.DEFAULT_PRESENCE_PENALTY,
                frequencyPenalty = StandardModelParameters.DEFAULT_FREQUENCY_PENALTY,
                repetitionPenalty = StandardModelParameters.DEFAULT_REPETITION_PENALTY,
                customParameters = "[]"
        )
    }

    // 保存配置
    suspend fun saveModelConfig(config: ModelConfigData) {
        val configKey = stringPreferencesKey("config_${config.id}")
        context.modelConfigDataStore.edit { preferences ->
            preferences[configKey] = json.encodeToString(config)
        }
    }

    // 从DataStore加载配置
    private suspend fun loadConfigFromDataStore(configId: String): ModelConfigData? {
        val configKey = stringPreferencesKey("config_${configId}")
        return context.modelConfigDataStore.data.first().let { preferences ->
            val configJson = preferences[configKey]
            if (configJson != null) {
                try {
                    json.decodeFromString<ModelConfigData>(configJson)
                } catch (e: Exception) {
                    // 如果解析失败，回退到创建一个新配置
                    if (configId == DEFAULT_CONFIG_ID) {
                        createFreshDefaultConfig()
                    } else {
                        ModelConfigData(id = configId, name = "配置 $configId")
                    }
                }
            } else {
                if (configId == DEFAULT_CONFIG_ID) {
                    createFreshDefaultConfig()
                } else {
                    ModelConfigData(id = configId, name = "配置 $configId")
                }
            }
        }
    }

    // 将配置保存到DataStore
    private suspend fun saveConfigToDataStore(config: ModelConfigData) {
        val configKey = stringPreferencesKey("config_${config.id}")
        context.modelConfigDataStore.edit { preferences ->
            preferences[configKey] = json.encodeToString(config)
        }
    }

    // 获取指定ID的配置
    fun getModelConfigFlow(configId: String): Flow<ModelConfigData> {
        return context.modelConfigDataStore.data.map { preferences ->
            val config = loadConfigFromDataStore(configId) ?: ModelConfigData(id = configId, name = "配置 $configId")
            Log.d("CONFIG_TIMING", "getModelConfigFlow($configId) 返回配置，apiKey: '${config.apiKey}'")
            config
        }
    }

    // 获取指定ID的配置的非Flow版本
    suspend fun getModelConfig(configId: String): ModelConfigData? {
        return loadConfigFromDataStore(configId)
    }

    // 更新API Key池的当前索引
    suspend fun updateConfigKeyIndex(configId: String, newIndex: Int) {
        val config = getModelConfig(configId)
        if (config != null) {
            val updatedConfig = config.copy(currentKeyIndex = newIndex)
            saveConfigToDataStore(updatedConfig)
        }
    }

    // 获取所有配置的摘要信息
    suspend fun getAllConfigSummaries(): List<ModelConfigSummary> {
        val configIds = configListFlow.first()
        val summaries = mutableListOf<ModelConfigSummary>()

        for (id in configIds) {
            val config = getModelConfigFlow(id).first()
            summaries.add(
                    ModelConfigSummary(
                            id = config.id,
                            name = config.name,
                            modelName = config.modelName,
                            apiEndpoint = config.apiEndpoint
                    )
            )
        }

        return summaries
    }

    // 创建新配置
    suspend fun createConfig(name: String): String {
        val configId = UUID.randomUUID().toString()
        val configList = configListFlow.first().toMutableList()

        // 复制当前活跃配置作为新配置的基础
        val baseConfig = getModelConfigFlow(DEFAULT_CONFIG_ID).first()

        val newConfig = baseConfig.copy(id = configId, name = name)

        // 保存新配置
        saveConfigToDataStore(newConfig)

        // 更新配置列表
        configList.add(configId)
        context.modelConfigDataStore.edit { preferences ->
            preferences[CONFIG_LIST_KEY] = json.encodeToString(configList)
        }

        return configId
    }

    // 删除配置
    suspend fun deleteConfig(configId: String) {
        if (configId == DEFAULT_CONFIG_ID) {
            // 不允许删除默认配置
            return
        }

        val configList = configListFlow.first().toMutableList()

        // 从列表中移除
        configList.remove(configId)
        context.modelConfigDataStore.edit { preferences ->
            // 删除配置记录 - 修复null赋值问题
            preferences.remove(stringPreferencesKey("config_${configId}"))
            // 更新配置列表
            preferences[CONFIG_LIST_KEY] = json.encodeToString(configList)
                                }
    }

    // 更新配置基本信息（名称等）
    suspend fun updateConfigBase(configId: String, name: String): ModelConfigData {
        val config = getModelConfigFlow(configId).first()
        val updatedConfig = config.copy(name = name)
        saveConfigToDataStore(updatedConfig)
        return updatedConfig
    }

    // 更新模型配置
    suspend fun updateModelConfig(
            configId: String,
            apiKey: String,
            apiEndpoint: String,
            modelName: String
    ): ModelConfigData {
        val config = getModelConfigFlow(configId).first()
        val updatedConfig =
                config.copy(apiKey = apiKey, apiEndpoint = apiEndpoint, modelName = modelName)

        // 保存更新后的配置
        saveConfigToDataStore(updatedConfig)

        return updatedConfig
    }

    // 更新模型配置 - 包含API提供商类型
    suspend fun updateModelConfig(
            configId: String,
            apiKey: String,
            apiEndpoint: String,
            modelName: String,
            apiProviderType: com.ai.assistance.operit.data.model.ApiProviderType
    ): ModelConfigData {
        val config = getModelConfigFlow(configId).first()
        val updatedConfig =
                config.copy(
                        apiKey = apiKey,
                        apiEndpoint = apiEndpoint,
                        modelName = modelName,
                        apiProviderType = apiProviderType
                )

        // 保存更新后的配置
        saveConfigToDataStore(updatedConfig)

        return updatedConfig
    }

    // 更新模型配置 - 包含API提供商类型和MNN配置
    suspend fun updateModelConfig(
            configId: String,
            apiKey: String,
            apiEndpoint: String,
            modelName: String,
            apiProviderType: com.ai.assistance.operit.data.model.ApiProviderType,
            mnnForwardType: Int,
            mnnThreadCount: Int
    ): ModelConfigData {
        val config = getModelConfigFlow(configId).first()
        val updatedConfig =
                config.copy(
                        apiKey = apiKey,
                        apiEndpoint = apiEndpoint,
                        modelName = modelName,
                        apiProviderType = apiProviderType,
                        mnnForwardType = mnnForwardType,
                        mnnThreadCount = mnnThreadCount
                )

        // 保存更新后的配置
        saveConfigToDataStore(updatedConfig)

        return updatedConfig
    }

    // 更新自定义参数
    suspend fun updateCustomParameters(configId: String, parametersJson: String): ModelConfigData {
        val config = getModelConfigFlow(configId).first()
        val updatedConfig =
                config.copy(
                        customParameters = parametersJson,
                        hasCustomParameters = parametersJson.isNotBlank() && parametersJson != "[]"
                )

        // 保存更新后的配置
        saveConfigToDataStore(updatedConfig)
        return updatedConfig
    }

    // 更新参数 - 新增方法
    suspend fun updateParameters(configId: String, parameters: List<ModelParameter<*>>) {
        val config = getModelConfigFlow(configId).first()

        // 提取自定义参数并序列化
        val customParams = parameters.filter { it.isCustom }
        val customParamsJson = if (customParams.isNotEmpty()) {
            val customParamsData = customParams.map { it.toCustomParameterData() }
            json.encodeToString(customParamsData)
        } else {
            "[]"
        }

        // 从参数列表更新配置，包括自定义参数
        val updatedConfig = config.copy(
            maxTokens = parameters.find { it.id == "max_tokens" }?.currentValue as Int? ?: config.maxTokens,
            maxTokensEnabled = parameters.find { it.id == "max_tokens" }?.isEnabled ?: config.maxTokensEnabled,
            temperature = parameters.find { it.id == "temperature" }?.currentValue as Float? ?: config.temperature,
            temperatureEnabled = parameters.find { it.id == "temperature" }?.isEnabled ?: config.temperatureEnabled,
            topP = parameters.find { it.id == "top_p" }?.currentValue as Float? ?: config.topP,
            topPEnabled = parameters.find { it.id == "top_p" }?.isEnabled ?: config.topPEnabled,
            topK = parameters.find { it.id == "top_k" }?.currentValue as Int? ?: config.topK,
            topKEnabled = parameters.find { it.id == "top_k" }?.isEnabled ?: config.topKEnabled,
            presencePenalty = parameters.find { it.id == "presence_penalty" }?.currentValue as Float? ?: config.presencePenalty,
            presencePenaltyEnabled = parameters.find { it.id == "presence_penalty" }?.isEnabled ?: config.presencePenaltyEnabled,
            frequencyPenalty = parameters.find { it.id == "frequency_penalty" }?.currentValue as Float? ?: config.frequencyPenalty,
            frequencyPenaltyEnabled = parameters.find { it.id == "frequency_penalty" }?.isEnabled ?: config.frequencyPenaltyEnabled,
            repetitionPenalty = parameters.find { it.id == "repetition_penalty" }?.currentValue as Float? ?: config.repetitionPenalty,
            repetitionPenaltyEnabled = parameters.find { it.id == "repetition_penalty" }?.isEnabled ?: config.repetitionPenaltyEnabled,
            customParameters = customParamsJson,
            hasCustomParameters = customParams.isNotEmpty()
        )

        saveConfigToDataStore(updatedConfig)
    }

    /**
     * 根据配置ID获取完整的模型参数列表（包括标准和自定义参数）
     * @param configId 配置ID
     * @return 模型参数列表
     */
    suspend fun getModelParametersForConfig(configId: String): List<ModelParameter<*>> {
        val config = getModelConfigFlow(configId).first()
        val parameters = mutableListOf<ModelParameter<*>>()

        // 映射标准参数
        StandardModelParameters.DEFINITIONS.forEach { def ->
            val (currentValue, isEnabled) =
                    when (def.id) {
                        "max_tokens" -> config.maxTokens to config.maxTokensEnabled
                        "temperature" -> config.temperature to config.temperatureEnabled
                        "top_p" -> config.topP to config.topPEnabled
                        "top_k" -> config.topK to config.topKEnabled
                        "presence_penalty" -> config.presencePenalty to config.presencePenaltyEnabled
                        "frequency_penalty" ->
                                config.frequencyPenalty to config.frequencyPenaltyEnabled
                        "repetition_penalty" ->
                                config.repetitionPenalty to config.repetitionPenaltyEnabled
                        else -> null to null
                    }

            if (currentValue != null && isEnabled != null) {
                parameters.add(
                        ModelParameter(
                                id = def.id,
                                name = def.name,
                                apiName = def.apiName,
                                description = def.description,
                                defaultValue = def.defaultValue,
                                currentValue = currentValue,
                                isEnabled = isEnabled,
                                valueType = def.valueType,
                                minValue = def.minValue,
                                maxValue = def.maxValue,
                                category = def.category
                        )
                )
            }
        }

        // 添加自定义参数
        if (config.hasCustomParameters &&
                        config.customParameters.isNotBlank() &&
                        config.customParameters != "[]"
        ) {
            try {
                val customParamsData =
                        json.decodeFromString<List<com.ai.assistance.operit.data.model.CustomParameterData>>(
                                config.customParameters
                        )
                customParamsData.forEach { data ->
                    val valueType = ParameterValueType.valueOf(data.valueType)
                    val category = ParameterCategory.valueOf(data.category)

                    val convertedParam =
                            when (valueType) {
                                ParameterValueType.INT ->
                                        ModelParameter(
                                                id = data.id,
                                                name = data.name,
                                                apiName = data.apiName,
                                                description = data.description,
                                                defaultValue = data.defaultValue.toInt(),
                                                currentValue = data.currentValue.toInt(),
                                                isEnabled = data.isEnabled,
                                                valueType = valueType,
                                                minValue = data.minValue?.toInt(),
                                                maxValue = data.maxValue?.toInt(),
                                                category = category,
                                                isCustom = true
                                        )
                                ParameterValueType.FLOAT ->
                                        ModelParameter(
                                                id = data.id,
                                                name = data.name,
                                                apiName = data.apiName,
                                                description = data.description,
                                                defaultValue = data.defaultValue.toFloat(),
                                                currentValue = data.currentValue.toFloat(),
                                                isEnabled = data.isEnabled,
                                                valueType = valueType,
                                                minValue = data.minValue?.toFloat(),
                                                maxValue = data.maxValue?.toFloat(),
                                                category = category,
                                                isCustom = true
                                        )
                                ParameterValueType.BOOLEAN ->
                                        ModelParameter(
                                                id = data.id,
                                                name = data.name,
                                                apiName = data.apiName,
                                                description = data.description,
                                                defaultValue = data.defaultValue.toBoolean(),
                                                currentValue = data.currentValue.toBoolean(),
                                                isEnabled = data.isEnabled,
                                                valueType = valueType,
                                                category = category,
                                                isCustom = true
                                        )
                                ParameterValueType.STRING ->
                                        ModelParameter(
                                                id = data.id,
                                                name = data.name,
                                                apiName = data.apiName,
                                                description = data.description,
                                                defaultValue = data.defaultValue,
                                                currentValue = data.currentValue,
                                                isEnabled = data.isEnabled,
                                                valueType = valueType,
                                                category = category,
                                                isCustom = true
                                        )
                            }
                    parameters.add(convertedParam)
                }
            } catch (e: Exception) {
                Log.e("ModelConfigManager", "Failed to parse or convert custom parameters", e)
            }
        }

        return parameters
    }
}

// 扩展函数，用于将ModelParameter转换为CustomParameterData
private fun ModelParameter<*>.toCustomParameterData(): com.ai.assistance.operit.data.model.CustomParameterData {
    return com.ai.assistance.operit.data.model.CustomParameterData(
        id = this.id,
        name = this.name,
        apiName = this.apiName,
        description = this.description,
        defaultValue = this.defaultValue.toString(),
        currentValue = this.currentValue.toString(),
        isEnabled = this.isEnabled,
        valueType = this.valueType.name,
        minValue = this.minValue?.toString(),
        maxValue = this.maxValue?.toString(),
        category = this.category.name
    )
}
