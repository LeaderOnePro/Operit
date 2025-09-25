package com.ai.assistance.operit.api.chat

import android.util.Log
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.json.JSONObject

/**
 * A factory for creating and managing a shared OkHttpClient instance.
 * Using a shared client allows for efficient reuse of connections and resources.
 */
private object SharedHttpClient {
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Increase the connection timeout to handle slow networks better.
            .connectTimeout(60, TimeUnit.SECONDS)
            // Set long read/write timeouts for streaming responses.
            .readTimeout(1000, TimeUnit.SECONDS)
            .writeTimeout(1000, TimeUnit.SECONDS)
            // Use a connection pool to reuse connections, improving latency and reducing resource usage.
            // Increased idle connections to 10 from the default of 5.
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            // Explicitly enable HTTP/2, which is the default but good to have declared.
            // OkHttp will use HTTP/2 if the server supports it, falling back to HTTP/1.1.
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .build()
    }
}

/** AI服务工厂，根据提供商类型创建相应的AIService实例 */
object AIServiceFactory {

    /**
     * 解析自定义请求头的JSON字符串为Map
     */
    private fun parseCustomHeaders(customHeadersJson: String): Map<String, String> {
        return try {
            val headers = mutableMapOf<String, String>()
            if (customHeadersJson.isNotEmpty() && customHeadersJson != "{}") {
                val jsonObject = JSONObject(customHeadersJson)
                for (key in jsonObject.keys()) {
                    headers[key] = jsonObject.getString(key)
                }
            }
            headers
        } catch (e: Exception) {
            Log.e("AIServiceFactory", "解析自定义请求头失败", e)
            emptyMap()
        }
    }

    /**
     * 创建AI服务实例
     *
     * @param config 模型配置数据
     * @param customHeadersJson 自定义请求头的JSON字符串
     * @param modelConfigManager 模型配置管理器，用于多API Key模式
     * @return 对应的AIService实现
     */
    fun createService(
        config: ModelConfigData,
        customHeadersJson: String,
        modelConfigManager: ModelConfigManager
    ): AIService {
        val httpClient = SharedHttpClient.instance
        val customHeaders = parseCustomHeaders(customHeadersJson)

        // 根据配置决定使用单个API Key还是多API Key轮询
        val apiKeyProvider = if (config.useMultipleApiKeys) {
            MultiApiKeyProvider(config.id, modelConfigManager)
        } else {
            SingleApiKeyProvider(config.apiKey)
        }

        return when (config.apiProviderType) {
            // OpenAI格式，支持原生和兼容OpenAI API的服务
            ApiProviderType.OPENAI -> OpenAIProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType)

            // Claude格式，支持Anthropic Claude系列
            ApiProviderType.ANTHROPIC -> ClaudeProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType)

            // Gemini格式，支持Google Gemini系列
            ApiProviderType.GOOGLE -> GeminiProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType)

            // LM Studio使用OpenAI兼容格式
            ApiProviderType.LMSTUDIO -> OpenAIProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType)

            // 阿里云（通义千问）使用专用的QwenProvider
            ApiProviderType.ALIYUN -> QwenAIProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType)

            // 其他中文服务商，当前使用OpenAI Provider (大多数兼容OpenAI格式)
            // 后续可根据需要实现专用Provider
            ApiProviderType.BAIDU -> OpenAIProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType)
            ApiProviderType.XUNFEI -> OpenAIProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType)
            ApiProviderType.ZHIPU -> OpenAIProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType)
            ApiProviderType.BAICHUAN -> OpenAIProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType)
            ApiProviderType.MOONSHOT -> OpenAIProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType)

            // 默认使用OpenAI格式（大多数服务商兼容）
            ApiProviderType.DEEPSEEK -> OpenAIProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType)
            ApiProviderType.SILICONFLOW -> QwenAIProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType)
            ApiProviderType.OPENROUTER -> OpenAIProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType)
            ApiProviderType.INFINIAI -> OpenAIProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType)
            ApiProviderType.OTHER -> OpenAIProvider(config.apiEndpoint, apiKeyProvider, config.modelName, httpClient, customHeaders, config.apiProviderType)
        }
    }
}
