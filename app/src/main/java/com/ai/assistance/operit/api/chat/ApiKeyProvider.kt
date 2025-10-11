package com.ai.assistance.operit.api.chat

import android.util.Log
import com.ai.assistance.operit.data.model.ApiKeyInfo
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * API密钥提供程序接口
 * 抽象了API密钥的获取逻辑，以支持单个密钥和密钥池轮询。
 */
interface ApiKeyProvider {
    /** 获取当前可用的API Key */
    suspend fun getApiKey(): String
}

/**
 * 单个API Key的简单提供程序，用于兼容旧配置。
 */
class SingleApiKeyProvider(private val apiKey: String) : ApiKeyProvider {
    override suspend fun getApiKey(): String {
        Log.d("ApiKeyProvider", "Using single API key: ${apiKey.take(4)}...${apiKey.takeLast(4)}")
        return apiKey
    }
}

/**
 * 多API Key提供程序，实现密钥的轮询和状态管理。
 * @param configId 配置ID
 * @param modelConfigManager 用于读取和更新模型配置的管理器
 */
class MultiApiKeyProvider(
    private val configId: String,
    private val modelConfigManager: ModelConfigManager
) : ApiKeyProvider {
    private val mutex = Mutex()

    override suspend fun getApiKey(): String {
        return mutex.withLock {
            val config = modelConfigManager.getModelConfig(configId)
                ?: throw IllegalStateException("Config with ID $configId not found")
            
            // 筛选出启用的key
            val enabledKeys = config.apiKeyPool.filter { it.isEnabled }
            Log.d("ApiKeyProvider", "Config ${config.name}: Found ${enabledKeys.size} enabled keys out of ${config.apiKeyPool.size} total keys")
            
            if (enabledKeys.isEmpty()) {
                // 如果池为空，尝试回退到单key
                if (config.apiKey.isNotBlank()) {
                    Log.d("ApiKeyProvider", "Config ${config.name}: No enabled keys in pool, falling back to single API key: sk-...${config.apiKey.takeLast(4)}")
                    return@withLock config.apiKey
                }
                Log.e("ApiKeyProvider", "Config ${config.name}: API key pool is empty or all keys are disabled, and no fallback API key is available")
                throw IllegalStateException("API key pool for ${config.name} is empty or all keys are disabled, and no fallback API key is available.")
            }

            // 从当前索引开始寻找下一个有效的key
            val startIndex = config.currentKeyIndex % enabledKeys.size
            val selectedKey = enabledKeys[startIndex]
            
            Log.d("ApiKeyProvider", "Config ${config.name}: Using key ${startIndex + 1}/${enabledKeys.size} - '${selectedKey.name}' (sk-...${selectedKey.key.takeLast(4)})")

            // 更新并保存下一个索引
            val nextIndex = (startIndex + 1) % enabledKeys.size
            val updatedConfig = config.copy(currentKeyIndex = nextIndex)
            modelConfigManager.saveModelConfig(updatedConfig)

            selectedKey.key
        }
    }
} 