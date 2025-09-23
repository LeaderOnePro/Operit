package com.ai.assistance.operit.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.data.model.ParameterValueType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Define the DataStore at the module level
private val Context.apiDataStore: DataStore<Preferences> by
        preferencesDataStore(name = "api_settings")

class ApiPreferences private constructor(private val context: Context) {

    // Define our preferences keys
    companion object {
        @Volatile
        private var INSTANCE: ApiPreferences? = null

        fun getInstance(context: Context): ApiPreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = ApiPreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
        // 动态生成供应商:模型的Token键
        fun getTokenInputKey(providerModel: String) =
                intPreferencesKey("token_input_${providerModel.replace(":", "_")}")

        fun getTokenCachedInputKey(providerModel: String) =
                intPreferencesKey("token_cached_input_${providerModel.replace(":", "_")}")

        fun getTokenOutputKey(providerModel: String) =
                intPreferencesKey("token_output_${providerModel.replace(":", "_")}")

        // 模型定价键
        fun getModelInputPriceKey(providerModel: String) =
                floatPreferencesKey("model_input_price_${providerModel.replace(":", "_")}")

        fun getModelCachedInputPriceKey(providerModel: String) =
                floatPreferencesKey("model_cached_input_price_${providerModel.replace(":", "_")}")

        fun getModelOutputPriceKey(providerModel: String) =
                floatPreferencesKey("model_output_price_${providerModel.replace(":", "_")}")

        val SHOW_FPS_COUNTER = booleanPreferencesKey("show_fps_counter")
        val ENABLE_AI_PLANNING = booleanPreferencesKey("enable_ai_planning")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")

        // Keys for Thinking Mode and Thinking Guidance
        val ENABLE_THINKING_MODE = booleanPreferencesKey("enable_thinking_mode")
        val ENABLE_THINKING_GUIDANCE = booleanPreferencesKey("enable_thinking_guidance")

        // Key for Memory Attachment
        val ENABLE_MEMORY_ATTACHMENT = booleanPreferencesKey("enable_memory_attachment")

        // Key for Auto Read
        val ENABLE_AUTO_READ = booleanPreferencesKey("enable_auto_read")

        // Key for Waifu Mode
        val ENABLE_WAIFU_MODE = booleanPreferencesKey("enable_waifu_mode")
        val WAIFU_CHAR_DELAY = intPreferencesKey("waifu_char_delay") // 每字符延迟（毫秒）
        val WAIFU_REMOVE_PUNCTUATION = booleanPreferencesKey("waifu_remove_punctuation") // 是否移除标点符号
        val WAIFU_DISABLE_ACTIONS = booleanPreferencesKey("waifu_disable_actions") // 是否禁止动作表情
        val WAIFU_ENABLE_EMOTICONS = booleanPreferencesKey("waifu_enable_emoticons") // 是否启用表情包
        val WAIFU_ENABLE_SELFIE = booleanPreferencesKey("waifu_enable_selfie") // 是否启用自拍功能
        val WAIFU_SELFIE_PROMPT = stringPreferencesKey("waifu_selfie_prompt") // 自拍功能的外貌提示词

        // Keys for Summary Settings
        val ENABLE_SUMMARY = booleanPreferencesKey("enable_summary")
        val ENABLE_SUMMARY_BY_MESSAGE_COUNT = booleanPreferencesKey("enable_summary_by_message_count")
        val SUMMARY_MESSAGE_COUNT_THRESHOLD = intPreferencesKey("summary_message_count_threshold")

        // Key for Context Length
        val CONTEXT_LENGTH = floatPreferencesKey("context_length")

        // Key for Summary Token Threshold
        val SUMMARY_TOKEN_THRESHOLD = floatPreferencesKey("summary_token_threshold")

        // Custom Prompt Settings
        val CUSTOM_INTRO_PROMPT = stringPreferencesKey("custom_intro_prompt")
        
        // Custom System Prompt Template (Advanced Configuration)
        val CUSTOM_SYSTEM_PROMPT_TEMPLATE = stringPreferencesKey("custom_system_prompt_template")

        const val DEFAULT_SHOW_FPS_COUNTER = false
        const val DEFAULT_ENABLE_AI_PLANNING = false
        const val DEFAULT_KEEP_SCREEN_ON = true

        // Default values for Thinking Mode and Thinking Guidance
        const val DEFAULT_ENABLE_THINKING_MODE = false
        const val DEFAULT_ENABLE_THINKING_GUIDANCE = false

        // Default value for Memory Attachment
        const val DEFAULT_ENABLE_MEMORY_ATTACHMENT = true

        // Default value for Auto Read
        const val DEFAULT_ENABLE_AUTO_READ = false

        // Default value for Waifu Mode
        const val DEFAULT_ENABLE_WAIFU_MODE = false
        const val DEFAULT_WAIFU_CHAR_DELAY = 500 // 500ms per character (2 chars per second)
        const val DEFAULT_WAIFU_REMOVE_PUNCTUATION = false // 默认保留标点符号
        const val DEFAULT_WAIFU_DISABLE_ACTIONS = false // 默认允许动作表情
        const val DEFAULT_WAIFU_ENABLE_EMOTICONS = false // 默认不启用表情包
        const val DEFAULT_WAIFU_ENABLE_SELFIE = false // 默认不启用自拍功能
        const val DEFAULT_WAIFU_SELFIE_PROMPT = "kipfel (vrchat), long hair, Matcha color hair, purple eyes, sweater vest,  black skirt, black necktie, collared shirt, long sleeves, black headwear, beanie, pleated skirt, hair bun, white shirt, hair ribbon,  hairclip, hair between eyes, black footwear, blush, hair ornament, cat hat,  very long hair,sweater, animal ear headwear, bag, bandaid on leg, socks" // 默认外貌提示词

        // Default values for Summary Settings
        const val DEFAULT_ENABLE_SUMMARY = true
        const val DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT = true
        const val DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD = 4

        // Default value for Context Length (in K)
        const val DEFAULT_CONTEXT_LENGTH = 48.0f

        // Default value for Summary Token Threshold
        const val DEFAULT_SUMMARY_TOKEN_THRESHOLD = 0.70f

        // Default values for custom prompts
        const val DEFAULT_INTRO_PROMPT = "你是Operit，一个全能AI助手，旨在解决用户提出的任何任务。你有各种工具可以调用，以高效完成复杂的请求。"
        
        // Default system prompt template (empty means use built-in template)
        const val DEFAULT_SYSTEM_PROMPT_TEMPLATE = ""

        // 自定义参数存储键
        val CUSTOM_PARAMETERS = stringPreferencesKey("custom_parameters")

        // 自定义请求头存储键
        val CUSTOM_HEADERS = stringPreferencesKey("custom_headers")

        // 默认空的自定义参数列表
        const val DEFAULT_CUSTOM_PARAMETERS = "[]"
        const val DEFAULT_CUSTOM_HEADERS = "{}"

        // API 配置默认值
        const val DEFAULT_API_ENDPOINT = "https://api.deepseek.com/v1/chat/completions"
        const val DEFAULT_MODEL_NAME = "deepseek-chat"
        private const val ENCODED_API_KEY = "c2stNmI4NTYyMjUzNmFjNDhjMDgwYzUwNDhhYjVmNWQxYmQ="
        val DEFAULT_API_KEY: String by lazy { decodeApiKey(ENCODED_API_KEY) }

        private fun decodeApiKey(encodedKey: String): String {
            return try {
                android.util.Base64.decode(encodedKey, android.util.Base64.NO_WRAP)
                    .toString(Charsets.UTF_8)
            } catch (e: Exception) {
                android.util.Log.e("ApiPreferences", "Failed to decode API key", e)
                ""
            }
        }
    }
    // Get FPS Counter Display setting as Flow
    val showFpsCounterFlow: Flow<Boolean> =
            context.apiDataStore.data.map { preferences ->
                preferences[SHOW_FPS_COUNTER] ?: DEFAULT_SHOW_FPS_COUNTER
            }

    // Get AI Planning setting as Flow
    val enableAiPlanningFlow: Flow<Boolean> =
            context.apiDataStore.data.map { preferences ->
                preferences[ENABLE_AI_PLANNING] ?: DEFAULT_ENABLE_AI_PLANNING
            }

    // Get Keep Screen On setting as Flow
    val keepScreenOnFlow: Flow<Boolean> =
            context.apiDataStore.data.map { preferences ->
                preferences[KEEP_SCREEN_ON] ?: DEFAULT_KEEP_SCREEN_ON
            }

    // Flow for Thinking Mode
    val enableThinkingModeFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_THINKING_MODE] ?: DEFAULT_ENABLE_THINKING_MODE
        }

    // Flow for Thinking Guidance
    val enableThinkingGuidanceFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_THINKING_GUIDANCE] ?: DEFAULT_ENABLE_THINKING_GUIDANCE
            }

    // Flow for Memory Attachment
    val enableMemoryAttachmentFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_MEMORY_ATTACHMENT] ?: DEFAULT_ENABLE_MEMORY_ATTACHMENT
        }

    // Flow for Auto Read
    val enableAutoReadFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_AUTO_READ] ?: DEFAULT_ENABLE_AUTO_READ
        }

    // Flow for Waifu Mode
    val enableWaifuModeFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_WAIFU_MODE] ?: DEFAULT_ENABLE_WAIFU_MODE
        }

    val waifuCharDelayFlow: Flow<Int> =
        context.apiDataStore.data.map { preferences ->
            preferences[WAIFU_CHAR_DELAY] ?: DEFAULT_WAIFU_CHAR_DELAY
        }

    val waifuRemovePunctuationFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[WAIFU_REMOVE_PUNCTUATION] ?: DEFAULT_WAIFU_REMOVE_PUNCTUATION
        }

    val waifuDisableActionsFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[WAIFU_DISABLE_ACTIONS] ?: DEFAULT_WAIFU_DISABLE_ACTIONS
        }

    val waifuEnableEmoticonsFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[WAIFU_ENABLE_EMOTICONS] ?: DEFAULT_WAIFU_ENABLE_EMOTICONS
        }

    val waifuEnableSelfieFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[WAIFU_ENABLE_SELFIE] ?: DEFAULT_WAIFU_ENABLE_SELFIE
        }

    val waifuSelfiePromptFlow: Flow<String> =
        context.apiDataStore.data.map { preferences ->
            preferences[WAIFU_SELFIE_PROMPT] ?: DEFAULT_WAIFU_SELFIE_PROMPT
        }

    // Flows for Summary Settings
    val enableSummaryFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_SUMMARY] ?: DEFAULT_ENABLE_SUMMARY
        }

    val enableSummaryByMessageCountFlow: Flow<Boolean> =
        context.apiDataStore.data.map { preferences ->
            preferences[ENABLE_SUMMARY_BY_MESSAGE_COUNT] ?: DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT
        }

    val summaryMessageCountThresholdFlow: Flow<Int> =
        context.apiDataStore.data.map { preferences ->
            preferences[SUMMARY_MESSAGE_COUNT_THRESHOLD] ?: DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD
        }

    // Flow for Context Length
    val contextLengthFlow: Flow<Float> =
        context.apiDataStore.data.map { preferences ->
            preferences[CONTEXT_LENGTH] ?: DEFAULT_CONTEXT_LENGTH
        }

    // Flow for Summary Token Threshold
    val summaryTokenThresholdFlow: Flow<Float> =
        context.apiDataStore.data.map { preferences ->
            preferences[SUMMARY_TOKEN_THRESHOLD] ?: DEFAULT_SUMMARY_TOKEN_THRESHOLD
        }

    // Custom Prompt Flows
    val customIntroPromptFlow: Flow<String> =
            context.apiDataStore.data.map { preferences ->
                preferences[CUSTOM_INTRO_PROMPT] ?: DEFAULT_INTRO_PROMPT
            }



    // Custom System Prompt Template Flow
    val customSystemPromptTemplateFlow: Flow<String> =
            context.apiDataStore.data.map { preferences ->
                preferences[CUSTOM_SYSTEM_PROMPT_TEMPLATE] ?: DEFAULT_SYSTEM_PROMPT_TEMPLATE
            }

    // Flow for Custom Headers
    val customHeadersFlow: Flow<String> =
        context.apiDataStore.data.map { preferences ->
            preferences[CUSTOM_HEADERS] ?: DEFAULT_CUSTOM_HEADERS
        }

    // Save FPS Counter Display setting
    suspend fun saveShowFpsCounter(showFpsCounter: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[SHOW_FPS_COUNTER] = showFpsCounter }
    }

    // Save AI Planning setting
    suspend fun saveEnableAiPlanning(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_AI_PLANNING] = isEnabled }
    }

    // Save Keep Screen On setting
    suspend fun saveKeepScreenOn(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[KEEP_SCREEN_ON] = isEnabled }
    }

    // Save Thinking Mode setting
    suspend fun saveEnableThinkingMode(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences -> preferences[ENABLE_THINKING_MODE] = isEnabled }
    }

    // Save Thinking Guidance setting
    suspend fun saveEnableThinkingGuidance(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[ENABLE_THINKING_GUIDANCE] = isEnabled
        }
    }

    // Save Memory Attachment setting
    suspend fun saveEnableMemoryAttachment(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[ENABLE_MEMORY_ATTACHMENT] = isEnabled
        }
    }

    // Save Auto Read setting
    suspend fun saveEnableAutoRead(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[ENABLE_AUTO_READ] = isEnabled
        }
    }

    // Save Waifu Mode setting
    suspend fun saveEnableWaifuMode(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[ENABLE_WAIFU_MODE] = isEnabled
        }
    }

    suspend fun saveWaifuCharDelay(delayMs: Int) {
        context.apiDataStore.edit { preferences ->
            preferences[WAIFU_CHAR_DELAY] = delayMs
        }
    }

    suspend fun saveWaifuRemovePunctuation(removePunctuation: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[WAIFU_REMOVE_PUNCTUATION] = removePunctuation
        }
    }

    suspend fun saveWaifuDisableActions(disableActions: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[WAIFU_DISABLE_ACTIONS] = disableActions
        }
    }

    suspend fun saveWaifuEnableEmoticons(enableEmoticons: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[WAIFU_ENABLE_EMOTICONS] = enableEmoticons
        }
    }

    suspend fun saveWaifuEnableSelfie(enableSelfie: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[WAIFU_ENABLE_SELFIE] = enableSelfie
        }
    }

    suspend fun saveWaifuSelfiePrompt(selfiePrompt: String) {
        context.apiDataStore.edit { preferences ->
            preferences[WAIFU_SELFIE_PROMPT] = selfiePrompt
        }
    }

    // Save Summary Settings
    suspend fun saveEnableSummary(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[ENABLE_SUMMARY] = isEnabled
        }
    }

    suspend fun saveEnableSummaryByMessageCount(isEnabled: Boolean) {
        context.apiDataStore.edit { preferences ->
            preferences[ENABLE_SUMMARY_BY_MESSAGE_COUNT] = isEnabled
        }
    }

    suspend fun saveSummaryMessageCountThreshold(threshold: Int) {
        context.apiDataStore.edit { preferences ->
            preferences[SUMMARY_MESSAGE_COUNT_THRESHOLD] = threshold
        }
    }

    // Save Context Length
    suspend fun saveContextLength(length: Float) {
        context.apiDataStore.edit { preferences ->
            preferences[CONTEXT_LENGTH] = length
        }
    }

    // Save Summary Token Threshold
    suspend fun saveSummaryTokenThreshold(threshold: Float) {
        context.apiDataStore.edit { preferences ->
            preferences[SUMMARY_TOKEN_THRESHOLD] = threshold
        }
    }

    // 保存显示和行为设置的方法，不会影响模型参数
    suspend fun saveDisplaySettings(
            showFpsCounter: Boolean,
            keepScreenOn: Boolean
    ) {
        context.apiDataStore.edit { preferences ->
            preferences[SHOW_FPS_COUNTER] = showFpsCounter
            preferences[KEEP_SCREEN_ON] = keepScreenOn
        }
    }

    // 保存自定义请求头
    suspend fun saveCustomHeaders(headersJson: String) {
        context.apiDataStore.edit { preferences ->
            preferences[CUSTOM_HEADERS] = headersJson
        }
    }

    // 读取自定义请求头
    suspend fun getCustomHeaders(): String {
        val preferences = context.apiDataStore.data.first()
        return preferences[CUSTOM_HEADERS] ?: DEFAULT_CUSTOM_HEADERS
    }

    /**
     * 更新指定供应商:模型的token计数
     * @param providerModel 供应商:模型标识符，格式如"DEEPSEEK:deepseek-chat"
     * @param inputTokens 新增的输入token
     * @param outputTokens 新增的输出token
     * @param cachedInputTokens 新增的缓存命中token
     */
    suspend fun updateTokensForProviderModel(
            providerModel: String,
            inputTokens: Int,
            outputTokens: Int,
            cachedInputTokens: Int = 0
    ) {
        context.apiDataStore.edit { preferences ->
            val inputKey = getTokenInputKey(providerModel)
            val cachedInputKey = getTokenCachedInputKey(providerModel)
            val outputKey = getTokenOutputKey(providerModel)

            val currentInputTokens = preferences[inputKey] ?: 0
            val currentCachedInputTokens = preferences[cachedInputKey] ?: 0
            val currentOutputTokens = preferences[outputKey] ?: 0

            preferences[inputKey] = currentInputTokens + inputTokens
            preferences[cachedInputKey] = currentCachedInputTokens + cachedInputTokens
            preferences[outputKey] = currentOutputTokens + outputTokens
        }
    }

    /**
     * 获取指定供应商:模型的输入token数量
     */
    suspend fun getInputTokensForProviderModel(providerModel: String): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[getTokenInputKey(providerModel)] ?: 0
    }

    /**
     * 获取指定供应商:模型的缓存输入token数量
     */
    suspend fun getCachedInputTokensForProviderModel(providerModel: String): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[getTokenCachedInputKey(providerModel)] ?: 0
    }

    /**
     * 获取指定供应商:模型的输出token数量
     */
    suspend fun getOutputTokensForProviderModel(providerModel: String): Int {
        val preferences = context.apiDataStore.data.first()
        return preferences[getTokenOutputKey(providerModel)] ?: 0
    }

    /**
     * 获取所有供应商:模型的token统计
     * @return Map<供应商:模型, Triple<输入tokens, 输出tokens, 缓存tokens>>
     */
    suspend fun getAllProviderModelTokens(): Map<String, Triple<Int, Int, Int>> {
        val preferences = context.apiDataStore.data.first()
        val result = mutableMapOf<String, Triple<Int, Int, Int>>()
        
        // 遍历所有preferences，查找token相关的key
        preferences.asMap().forEach { (key, value) ->
            val keyName = key.name
            if (keyName.startsWith("token_input_")) {
                val providerModel = keyName.removePrefix("token_input_").replace("_", ":")
                val inputTokens = value as? Int ?: 0
                val outputTokens = preferences[getTokenOutputKey(providerModel)] ?: 0
                val cachedInputTokens = preferences[getTokenCachedInputKey(providerModel)] ?: 0
                if (inputTokens > 0 || outputTokens > 0 || cachedInputTokens > 0) {
                    result[providerModel] = Triple(inputTokens, outputTokens, cachedInputTokens)
                }
            }
        }
        
        return result
    }

    /**
     * 获取所有供应商:模型的token统计的Flow
     * @return Flow<Map<供应商:模型, Triple<输入tokens, 输出tokens, 缓存tokens>>>
     */
    val allProviderModelTokensFlow: Flow<Map<String, Triple<Int, Int, Int>>> =
        context.apiDataStore.data.map { preferences ->
            val result = mutableMapOf<String, Triple<Int, Int, Int>>()
            
            // 遍历所有preferences，查找token相关的key
            preferences.asMap().forEach { (key, value) ->
                val keyName = key.name
                if (keyName.startsWith("token_input_")) {
                    val providerModel = keyName.removePrefix("token_input_").replace("_", ":")
                    val inputTokens = value as? Int ?: 0
                    val outputTokens = preferences[getTokenOutputKey(providerModel)] ?: 0
                    val cachedInputTokens = preferences[getTokenCachedInputKey(providerModel)] ?: 0
                    if (inputTokens > 0 || outputTokens > 0 || cachedInputTokens > 0) {
                        result[providerModel] = Triple(inputTokens, outputTokens, cachedInputTokens)
                    }
                }
            }
            
            result
        }

    // Save custom prompts
    suspend fun saveCustomPrompts(introPrompt: String) {
        context.apiDataStore.edit { preferences ->
            preferences[CUSTOM_INTRO_PROMPT] = introPrompt
        }
    }

    // Save custom system prompt template
    suspend fun saveCustomSystemPromptTemplate(template: String) {
        context.apiDataStore.edit { preferences ->
            preferences[CUSTOM_SYSTEM_PROMPT_TEMPLATE] = template
        }
    }

    // Reset custom prompts to default values
    suspend fun resetCustomPrompts() {
        context.apiDataStore.edit { preferences ->
            preferences[CUSTOM_INTRO_PROMPT] = DEFAULT_INTRO_PROMPT
        }
    }

    // Reset custom system prompt template to default
    suspend fun resetCustomSystemPromptTemplate() {
        context.apiDataStore.edit { preferences ->
            preferences[CUSTOM_SYSTEM_PROMPT_TEMPLATE] = DEFAULT_SYSTEM_PROMPT_TEMPLATE
        }
    }

    // 重置所有供应商:模型的token计数
    suspend fun resetAllProviderModelTokenCounts() {
        context.apiDataStore.edit { preferences ->
            val keysToRemove = mutableListOf<Preferences.Key<*>>()
            preferences.asMap().forEach { (key, _) ->
                val keyName = key.name
                if (keyName.startsWith("token_input_") || keyName.startsWith("token_output_") || keyName.startsWith("token_cached_input_")) {
                    keysToRemove.add(key)
                }
            }
            keysToRemove.forEach { key ->
                preferences.remove(key)
            }
        }
    }

    // 重置指定供应商:模型的token计数
    suspend fun resetProviderModelTokenCounts(providerModel: String) {
        context.apiDataStore.edit { preferences ->
            preferences[getTokenInputKey(providerModel)] = 0
            preferences[getTokenCachedInputKey(providerModel)] = 0
            preferences[getTokenOutputKey(providerModel)] = 0
        }
    }

    // 获取模型输入价格（每百万tokens的美元价格）
    suspend fun getModelInputPrice(providerModel: String): Double {
        val preferences = context.apiDataStore.data.first()
        return preferences[getModelInputPriceKey(providerModel)]?.toDouble() ?: 0.0
    }

    // 获取模型缓存输入价格（每百万tokens的美元价格）
    suspend fun getModelCachedInputPrice(providerModel: String): Double {
        val preferences = context.apiDataStore.data.first()
        return preferences[getModelCachedInputPriceKey(providerModel)]?.toDouble() ?: 0.0
    }

    // 获取模型输出价格（每百万tokens的美元价格）
    suspend fun getModelOutputPrice(providerModel: String): Double {
        val preferences = context.apiDataStore.data.first()
        return preferences[getModelOutputPriceKey(providerModel)]?.toDouble() ?: 0.0
    }

    // 设置模型输入价格（每百万tokens的美元价格）
    suspend fun setModelInputPrice(providerModel: String, price: Double) {
        context.apiDataStore.edit { preferences ->
            preferences[getModelInputPriceKey(providerModel)] = price.toFloat()
        }
    }

    // 设置模型缓存输入价格（每百万tokens的美元价格）
    suspend fun setModelCachedInputPrice(providerModel: String, price: Double) {
        context.apiDataStore.edit { preferences ->
            preferences[getModelCachedInputPriceKey(providerModel)] = price.toFloat()
        }
    }

    // 设置模型输出价格（每百万tokens的美元价格）
    suspend fun setModelOutputPrice(providerModel: String, price: Double) {
        context.apiDataStore.edit { preferences ->
            preferences[getModelOutputPriceKey(providerModel)] = price.toFloat()
        }
    }
}