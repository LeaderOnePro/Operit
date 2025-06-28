package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable

/** API提供商类型枚举 */
@Serializable
enum class ApiProviderType {
        OPENAI, // OpenAI (GPT系列)
        ANTHROPIC, // Anthropic (Claude系列)
        GOOGLE, // Google (Gemini系列)
        BAIDU, // 百度 (文心一言系列)
        ALIYUN, // 阿里云 (通义千问系列)
        XUNFEI, // 讯飞 (星火认知系列)
        ZHIPU, // 智谱AI (ChatGLM系列)
        BAICHUAN, // 百川大模型
        MOONSHOT, // 月之暗面大模型
        DEEPSEEK, // Deepseek大模型
        SILICONFLOW, // 硅基流动
        OPENROUTER, // OpenRouter (多模型聚合)
        INFINIAI, // 无问芯穹
        LMSTUDIO, // LM Studio本地模型服务
        OTHER // 其他提供商
}

/** 表示完整的模型配置，包括API设置和模型参数 */
@Serializable
data class ModelConfigData(
        val id: String,
        val name: String,

        // API设置
        val apiKey: String = "",
        val apiEndpoint: String = "",
        val modelName: String = "",
        val apiProviderType: ApiProviderType = ApiProviderType.DEEPSEEK,

        // 是否包含自定义参数
        val hasCustomParameters: Boolean = false,

        // 模型参数的enabled状态
        val maxTokensEnabled: Boolean = false,
        val temperatureEnabled: Boolean = false,
        val topPEnabled: Boolean = false,
        val topKEnabled: Boolean = false,
        val presencePenaltyEnabled: Boolean = false,
        val frequencyPenaltyEnabled: Boolean = false,
        val repetitionPenaltyEnabled: Boolean = false,

        // 模型参数值
        val maxTokens: Int = 4096,
        val temperature: Float = 1.0f,
        val topP: Float = 1.0f,
        val topK: Int = 0,
        val presencePenalty: Float = 0.0f,
        val frequencyPenalty: Float = 0.0f,
        val repetitionPenalty: Float = 1.0f,

        // 自定义参数JSON字符串
        val customParameters: String = "[]"
)

/** 简化版的模型配置数据，用于列表显示 */
@Serializable
data class ModelConfigSummary(
        val id: String,
        val name: String,
        val modelName: String = "",
        val apiEndpoint: String = "",
        val apiProviderType: ApiProviderType = ApiProviderType.DEEPSEEK
)
