package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.AIService
import com.ai.assistance.operit.api.chat.AIServiceFactory
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.preferences.ApiPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 管理多个AIService实例，根据功能类型提供不同的服务配置 */
class MultiServiceManager(private val context: Context) {
    companion object {
        private const val TAG = "MultiServiceManager"
    }

    // 配置管理器
    private val functionalConfigManager = FunctionalConfigManager(context)
    private val modelConfigManager = ModelConfigManager(context)

    // 服务实例缓存
    private val serviceInstances = mutableMapOf<FunctionType, AIService>()
    private val serviceMutex = Mutex()

    // 默认AIService，用于兼容现有代码
    private var defaultService: AIService? = null

    /** 初始化服务管理器，确保配置已经准备好 */
    suspend fun initialize() {
        functionalConfigManager.initializeIfNeeded()
    }

    /** 获取指定功能类型的AIService */
    suspend fun getServiceForFunction(functionType: FunctionType): AIService {
        return serviceMutex.withLock {
            // 如果缓存中已有该服务实例，直接返回
            serviceInstances[functionType]?.let {
                return@withLock it
            }

            // 否则，创建新的服务实例
            val configId = functionalConfigManager.getConfigIdForFunction(functionType)
            val config = modelConfigManager.getModelConfigFlow(configId).first()

            val service = createServiceFromConfig(config)
            serviceInstances[functionType] = service

            // 如果是CHAT功能类型，也设置为默认服务
            if (functionType == FunctionType.CHAT) {
                defaultService = service
            }

            Log.d(TAG, "已为功能${functionType}创建服务实例，使用配置${config.name}")
            service
        }
    }

    /** 获取默认服务（通常是CHAT功能的服务） */
    suspend fun getDefaultService(): AIService {
        return serviceMutex.withLock {
            defaultService ?: getServiceForFunction(FunctionType.CHAT).also { defaultService = it }
        }
    }

    /** 刷新指定功能类型的服务实例 当配置更改时调用此方法 */
    suspend fun refreshServiceForFunction(functionType: FunctionType) {
        serviceMutex.withLock {
            // 移除旧实例
            serviceInstances.remove(functionType)

            // 如果是默认服务，也清除默认服务缓存
            if (functionType == FunctionType.CHAT) {
                defaultService = null
            }

            // 不立即创建新实例，而是等到需要时再创建
            Log.d(TAG, "已移除功能${functionType}的服务实例缓存")
        }
    }

    /** 刷新所有服务实例 当全局设置更改时调用此方法 */
    suspend fun refreshAllServices() {
        serviceMutex.withLock {
            serviceInstances.clear()
            defaultService = null
            Log.d(TAG, "已清除所有服务实例缓存")
        }
    }

    /** 根据配置创建AIService实例 */
    private suspend fun createServiceFromConfig(config: ModelConfigData): AIService {
        // 从ApiPreferences中异步获取自定义请求头
        val apiPreferences = ApiPreferences.getInstance(context)
        val customHeadersJson = apiPreferences.getCustomHeaders()

        return AIServiceFactory.createService(
            config = config,
            customHeadersJson = customHeadersJson,
            modelConfigManager = modelConfigManager
        )
    }

    /**
     * 获取指定功能类型的模型参数列表
     * @param functionType 功能类型
     * @return 模型参数列表
     */
    suspend fun getModelParametersForFunction(
            functionType: FunctionType
    ): List<com.ai.assistance.operit.data.model.ModelParameter<*>> {
        val configId = functionalConfigManager.getConfigIdForFunction(functionType)
        return modelConfigManager.getModelParametersForConfig(configId)
    }
}
