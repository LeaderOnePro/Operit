package com.ai.assistance.operit.api.chat

import android.util.Log
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.util.stream.Stream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import okhttp3.OkHttpClient

/**
 * 针对阿里巴巴Qwen（通义千问）模型的特定API Provider。
 * 继承自OpenAIProvider，以重用大部分兼容逻辑，但特别处理了`enable_thinking`参数。
 */
class QwenAIProvider(
    apiEndpoint: String,
    apiKeyProvider: ApiKeyProvider,
    modelName: String,
    client: OkHttpClient,
    customHeaders: Map<String, String> = emptyMap(),
    providerType: com.ai.assistance.operit.data.model.ApiProviderType = com.ai.assistance.operit.data.model.ApiProviderType.ALIYUN
) : OpenAIProvider(apiEndpoint, apiKeyProvider, modelName, client, customHeaders, providerType) {

    /**
     * 重写创建请求体的方法，以支持Qwen的`enable_thinking`参数。
     */
    override fun createRequestBody(
        message: String,
        chatHistory: List<Pair<String, String>>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean
    ): RequestBody {
        // 首先，调用父类的实现来获取一个标准的OpenAI格式的请求体JSON对象
        val baseRequestBodyJson = super.createRequestBodyInternal(message, chatHistory, modelParameters)
        val jsonObject = JSONObject(baseRequestBodyJson)

        // 如果启用了思考模式，则为Qwen模型添加特定的`enable_thinking`参数
        if (enableThinking) {
            jsonObject.put("enable_thinking", true)
            Log.d("QwenAIProvider", "已为Qwen模型启用“思考模式”。")
        }

        // 记录最终的请求体
        logLargeString("QwenAIProvider", jsonObject.toString(4), "最终Qwen请求体: ")

        // 使用更新后的JSONObject创建新的RequestBody
        return jsonObject.toString().toRequestBody(JSON)
    }

    override suspend fun sendMessage(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
            onNonFatalError: suspend (error: String) -> Unit
    ): Stream<String> {
        // 直接调用父类的sendMessage实现，它已经包含了续写逻辑
        return super.sendMessage(message, chatHistory, modelParameters, enableThinking, onTokensUpdated, onNonFatalError)
    }
} 