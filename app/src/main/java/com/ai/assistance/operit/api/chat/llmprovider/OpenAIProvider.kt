package com.ai.assistance.operit.api.chat.llmprovider

import android.util.Log
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.TokenCacheManager
import com.ai.assistance.operit.util.exceptions.UserCancellationException
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** OpenAI API格式的实现，支持标准OpenAI接口和兼容此格式的其他提供商 */
open class OpenAIProvider(
    private val apiEndpoint: String,
    private val apiKeyProvider: ApiKeyProvider,
    private val modelName: String,
    private val client: OkHttpClient,
    private val customHeaders: Map<String, String> = emptyMap(),
    private val providerType: ApiProviderType = ApiProviderType.OPENAI,
    protected val supportsVision: Boolean = false // 是否支持图片处理
) : AIService {
    // private val client: OkHttpClient = HttpClientFactory.instance

    protected val JSON = "application/json; charset=utf-8".toMediaType()

    // 当前活跃的Call对象，用于取消流式传输
    private var activeCall: Call? = null
    @Volatile private var isManuallyCancelled = false

    /**
     * 不可重试的异常，通常由客户端错误（如4xx状态码）引起
     */
    class NonRetriableException(message: String, cause: Throwable? = null) : IOException(message, cause)

    // Token缓存管理器
    private val tokenCacheManager = TokenCacheManager()

    // 公开token计数
    override val inputTokenCount: Int
        get() = tokenCacheManager.totalInputTokenCount
    override val outputTokenCount: Int
        get() = tokenCacheManager.outputTokenCount
    override val cachedInputTokenCount: Int
        get() = tokenCacheManager.cachedInputTokenCount

    // 供应商:模型标识符
    override val providerModel: String
        get() = "${providerType.name}:$modelName"

    // 重置token计数
    override fun resetTokenCounts() {
        tokenCacheManager.resetTokenCounts()
    }

    // 工具函数：分块打印大型文本日志
    protected fun logLargeString(tag: String, message: String, prefix: String = "") {
        // 设置单次日志输出的最大长度（Android日志上限约为4000字符）
        val maxLogSize = 3000

        // 如果消息长度超过限制，分块打印
        if (message.length > maxLogSize) {
            // 计算需要分多少块打印
            val chunkCount = message.length / maxLogSize + 1

            for (i in 0 until chunkCount) {
                val start = i * maxLogSize
                val end = minOf((i + 1) * maxLogSize, message.length)
                val chunkMessage = message.substring(start, end)

                // 打印带有编号的日志
                Log.d(tag, "$prefix Part ${i+1}/$chunkCount: $chunkMessage")
            }
        } else {
            // 消息长度在限制之内，直接打印
            Log.d(tag, "$prefix$message")
        }
    }

    // 取消当前流式传输
    override fun cancelStreaming() {
        isManuallyCancelled = true
        activeCall?.let {
            if (!it.isCanceled()) {
                it.cancel()
                Log.d("AIService", "已取消当前流式传输")
            }
        }
        activeCall = null
    }

    /**
     * 获取模型列表 注意：此方法直接调用ModelListFetcher获取模型列表
     * @return 模型列表结果
     */
    override suspend fun getModelsList(): Result<List<ModelOption>> {
        // 调用ModelListFetcher获取模型列表
        return ModelListFetcher.getModelsList(
            apiKey = apiKeyProvider.getApiKey(),
            apiEndpoint = apiEndpoint,
            apiProviderType = ApiProviderType.OPENAI // 默认为OpenAI类型
        )
    }

    override suspend fun testConnection(): Result<String> {
        return try {
            // 通过发送一条短消息来测试完整的连接、认证和API端点。
            // 这比getModelsList更可靠，因为它直接命中了聊天API。
            // 提供一个通用的系统提示，以防止某些需要它的模型出现错误。
            val testHistory = listOf("system" to "You are a helpful assistant.")
            val stream = sendMessage("Hi", testHistory, emptyList(), enableThinking = false, onTokensUpdated = { _, _, _ -> }, onNonFatalError = {})

            // 消耗流以确保连接有效。
            // 对 "Hi" 的响应应该很短，所以这会很快完成。
            stream.collect { _ -> }

            Result.success("连接成功！")
        } catch (e: Exception) {
            Log.e("AIService", "连接测试失败", e)
            Result.failure(IOException("连接测试失败: ${e.message}", e))
        }
    }

    // 解析服务器返回的内容，不再需要处理<think>标签
    private fun parseResponse(content: String): String {
        return content
    }

    // 创建请求体
    protected open fun createRequestBody(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>> = emptyList(),
            enableThinking: Boolean = false
    ): RequestBody {
        val jsonString = createRequestBodyInternal(message, chatHistory, modelParameters)
        return jsonString.toRequestBody(JSON)
    }

    /**
     * 内部方法，用于构建请求体的JSON字符串，以便子类可以重用和扩展。
     */
    protected fun createRequestBodyInternal(
        message: String,
        chatHistory: List<Pair<String, String>>,
        modelParameters: List<ModelParameter<*>> = emptyList()
    ): String {
        val jsonObject = JSONObject()
        jsonObject.put("model", modelName)
        jsonObject.put("stream", true) // 启用流式响应

        // 添加已启用的模型参数
        for (param in modelParameters) {
            if (param.isEnabled) {
                when (param.valueType) {
                    com.ai.assistance.operit.data.model.ParameterValueType.INT ->
                            jsonObject.put(param.apiName, param.currentValue as Int)
                    com.ai.assistance.operit.data.model.ParameterValueType.FLOAT ->
                            jsonObject.put(param.apiName, param.currentValue as Float)
                    com.ai.assistance.operit.data.model.ParameterValueType.STRING ->
                            jsonObject.put(param.apiName, param.currentValue as String)
                    com.ai.assistance.operit.data.model.ParameterValueType.BOOLEAN ->
                            jsonObject.put(param.apiName, param.currentValue as Boolean)
                }
                Log.d("AIService", "添加参数 ${param.apiName} = ${param.currentValue}")
            }
        }
        
        // 使用新的核心逻辑构建消息并获取token计数
        val (messagesArray, tokenCount) = buildMessagesAndCountTokens(message, chatHistory)
        jsonObject.put("messages", messagesArray)

        // 使用分块日志函数记录完整的请求体
        logLargeString("AIService", jsonObject.toString(4), "请求体: ")
        return jsonObject.toString()
    }

    /**
     * 构建content字段（可能是字符串或数组）
     * @param text 要处理的文本内容
     * @return 纯文本字符串或包含图片和文本的JSONArray
     */
    private fun buildContentField(text: String): Any {
        // 如果模型不支持图片，移除所有图片链接，只保留文本
        if (!supportsVision) {
            if (ImageLinkParser.hasImageLinks(text)) {
                val textWithoutLinks = ImageLinkParser.removeImageLinks(text).trim()
                Log.w("AIService", "模型不支持图片处理，已移除图片链接。原始文本长度: ${text.length}, 处理后: ${textWithoutLinks.length}")
                return if (textWithoutLinks.isEmpty()) "[图片内容已省略，当前模型不支持图片处理]" else textWithoutLinks
            }
            return text
        }
        
        // 模型支持图片，正常处理图片链接
        if (ImageLinkParser.hasImageLinks(text)) {
            val imageLinks = ImageLinkParser.extractImageLinks(text)
            val textWithoutLinks = ImageLinkParser.removeImageLinks(text).trim()
            
            val contentArray = JSONArray()
            
            // 添加图片
            imageLinks.forEach { link ->
                contentArray.put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:${link.mimeType};base64,${link.base64Data}")
                    })
                })
            }
            
            // 添加文本（如果有）
            if (textWithoutLinks.isNotEmpty()) {
                contentArray.put(JSONObject().apply {
                    put("type", "text")
                    put("text", textWithoutLinks)
                })
            }
            
            return contentArray
        } else {
            // 纯文本消息
            return text
        }
    }

    /**
     * 构建消息列表并计算token（核心逻辑）
     * @param message 用户消息
     * @param chatHistory 聊天历史
     * @return Pair(消息列表JSONArray, 输入token计数)
     */
    protected fun buildMessagesAndCountTokens(
            message: String,
            chatHistory: List<Pair<String, String>>
    ): Pair<JSONArray, Int> {
        val messagesArray = JSONArray()

        // 使用TokenCacheManager计算token数量
        val tokenCount = tokenCacheManager.calculateInputTokens(message, chatHistory)

        // 添加聊天历史
        if (chatHistory.isNotEmpty()) {
            val standardizedHistory = ChatUtils.mapChatHistoryToStandardRoles(chatHistory)
            val mergedHistory = mutableListOf<Pair<String, String>>()

            for ((role, content) in standardizedHistory) {
                if (mergedHistory.isNotEmpty() &&
                                role == mergedHistory.last().first &&
                                role != "system"
                ) {
                    val lastMessage = mergedHistory.last()
                    mergedHistory[mergedHistory.size - 1] =
                            Pair(lastMessage.first, lastMessage.second + "\n" + content)
                    Log.d("AIService", "合并连续的 $role 消息")
                } else {
                    mergedHistory.add(Pair(role, content))
                }
            }

            for ((role, content) in mergedHistory) {
                val historyMessage = JSONObject()
                historyMessage.put("role", role)
                historyMessage.put("content", buildContentField(content))
                messagesArray.put(historyMessage)
            }
        }

        // 添加当前消息
        val lastMessageRole =
                if (messagesArray.length() > 0) {
                    messagesArray.getJSONObject(messagesArray.length() - 1).getString("role")
                } else null

        if (lastMessageRole != "user") {
            val messageObject = JSONObject()
            messageObject.put("role", "user")
            messageObject.put("content", buildContentField(message))
            messagesArray.put(messageObject)
        } else {
            val lastMessage = messagesArray.getJSONObject(messagesArray.length() - 1)
            val lastContent = lastMessage.get("content")
            val lastText = if (lastContent is String) lastContent else ""
            if (lastText != message) {
                val combinedContent = lastText + "\n" + message
                lastMessage.put("content", buildContentField(combinedContent))
            }
        }
        return Pair(messagesArray, tokenCount)
    }

    override suspend fun calculateInputTokens(
            message: String,
            chatHistory: List<Pair<String, String>>
    ): Int {
        // 使用TokenCacheManager计算token数量
        return tokenCacheManager.calculateInputTokens(message, chatHistory)
    }

    // 创建请求
    private suspend fun createRequest(requestBody: RequestBody): Request {
        val currentApiKey = apiKeyProvider.getApiKey()
        val builder = Request.Builder()
                .url(EndpointCompleter.completeEndpoint(apiEndpoint))
                .addHeader("Authorization", "Bearer $currentApiKey")
                .addHeader("Content-Type", "application/json")

        // 添加自定义请求头
        customHeaders.forEach { (key, value) ->
            builder.addHeader(key, value)
        }

        val request = builder.post(requestBody).build()
        logLargeString("AIService", "请求头: \n${request.headers}")
        return request
    }

    override suspend fun sendMessage(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
            onNonFatalError: suspend (error: String) -> Unit
    ): Stream<String> = stream {
        isManuallyCancelled = false
        // 重置输出token计数（输入token由TokenCacheManager管理）
        tokenCacheManager.addOutputTokens(-tokenCacheManager.outputTokenCount)
        onTokensUpdated(tokenCacheManager.totalInputTokenCount, tokenCacheManager.cachedInputTokenCount, tokenCacheManager.outputTokenCount)

        Log.d(
                "AIService",
                "【发送消息】开始处理sendMessage请求，消息长度: ${message.length}，历史记录数量: ${chatHistory.size}"
        )

        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null

        // 用于保存已接收到的内容，以便在重试时使用
        val receivedContent = StringBuilder()

        while (retryCount < maxRetries) {
            try {
                // 如果是重试，我们需要构建一个新的请求
                val currentMessage: String
                val currentHistory: List<Pair<String, String>>

                if (retryCount > 0 && receivedContent.isNotEmpty()) {
                    Log.d("AIService", "【重试】准备续写请求，已接收内容长度: ${receivedContent.length}")
                    // 在用户消息后附加续写指令
                    currentMessage = message + "\n\n[SYSTEM NOTE] The previous response was cut off by a network error. You MUST continue from the exact point of interruption. Do not repeat any content. If you were in the middle of a code block or XML tag, complete it. Just output the text that would have come next."
                    // 将已接收的内容作为AI的上一条消息
                    val resumeHistory = chatHistory.toMutableList()
                    resumeHistory.add("assistant" to receivedContent.toString())
                    currentHistory = resumeHistory
                } else {
                    currentMessage = message
                    currentHistory = chatHistory
                }


                Log.d("AIService", "【发送消息】标准化聊天历史记录，原始大小: ${currentHistory.size}")
                val standardizedHistory = ChatUtils.mapChatHistoryToStandardRoles(currentHistory)
                Log.d("AIService", "【发送消息】历史记录标准化完成，标准化后大小: ${standardizedHistory.size}")

                Log.d(
                        "AIService",
                        "【发送消息】准备构建请求体，模型参数数量: ${modelParameters.size}，已启用参数: ${modelParameters.count { it.isEnabled }}"
                )
                val requestBody = createRequestBody(currentMessage, standardizedHistory, modelParameters, enableThinking)
                onTokensUpdated(tokenCacheManager.totalInputTokenCount, tokenCacheManager.cachedInputTokenCount, tokenCacheManager.outputTokenCount)
                val request = createRequest(requestBody)
                Log.d("AIService", "【发送消息】请求体构建完成，目标模型: $modelName，API端点: $apiEndpoint")

                Log.d("AIService", "【发送消息】准备连接到AI服务...")

                // 创建Call对象并保存到activeCall中，以便可以取消
                val call = client.newCall(request)
                activeCall = call

                Log.d("AIService", "【发送消息】正在建立连接到服务器...")

                // 确保在IO线程执行网络请求
                Log.d("AIService", "【发送消息】切换到IO线程执行网络请求")
                val response = withContext(Dispatchers.IO) { call.execute() }

                try {
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "No error details"
                        Log.e("AIService", "【发送消息】API请求失败，状态码: ${response.code}，错误信息: $errorBody")
                        // 对于4xx这类明确的客户端错误，直接抛出，不进行重试
                        if (response.code in 400..499) {
                            throw NonRetriableException("API请求失败，状态码: ${response.code}，错误信息: $errorBody")
                        }
                        // 对于5xx等服务端错误，允许重试
                        throw IOException("API请求失败，状态码: ${response.code}，错误信息: $errorBody")
                    }

                    Log.d("AIService", "【发送消息】连接成功(状态码: ${response.code})，准备处理流式响应...")
                    val responseBody = response.body ?: throw IOException("API响应为空")

                    // 在IO线程中读取响应
                    withContext(Dispatchers.IO) {
                        Log.d("AIService", "【发送消息】开始读取流式响应")
                        val reader = responseBody.charStream().buffered()
                        var currentContent = StringBuilder()
                        var isFirstResponse = true
                        var wasCancelled = false
                        var chunkCount = 0
                        var lastLogTime = System.currentTimeMillis()

                        // 跟踪思考内容的状态
                        var isInReasoningMode = false
                        var hasEmittedThinkStart = false

                        try {
                            reader.useLines { lines ->
                                lines.forEach { line ->
                                    // 如果call已被取消，提前退出
                                    if (activeCall?.isCanceled() == true) {
                                        Log.d("AIService", "【发送消息】流式传输已被取消，提前退出处理")
                                        wasCancelled = true
                                        return@forEach
                                    }

                                    // 兼容 "data:" 和 "data: " 两种格式
                                    if (line.startsWith("data:")) {
                                        val data = line.substring(5).trim() // 跳过 "data:"
                                        if (data != "[DONE]") {
                                            chunkCount++
                                            // 每10个块或500ms记录一次日志
                                            val currentTime = System.currentTimeMillis()
                                            if (chunkCount % 10 == 0 ||
                                                            currentTime - lastLogTime > 500
                                            ) {
                                                // Log.d("AIService", "【发送消息】已处理数据块: $chunkCount")
                                                lastLogTime = currentTime
                                            }

                                            try {
                                                val jsonResponse = JSONObject(data)
                                                val choices = jsonResponse.getJSONArray("choices")

                                                if (choices.length() > 0) {
                                                    val choice = choices.getJSONObject(0)

                                                    // 处理delta格式（流式响应）
                                                    val delta = choice.optJSONObject("delta")
                                                    if (delta != null) {
                                                        // 检查是否有思考内容
                                                        val reasoningContent =
                                                                delta.optString(
                                                                        "reasoning_content",
                                                                        ""
                                                                )
                                                        val regularContent =
                                                                delta.optString("content", "")

                                                        // 处理思考内容
                                                        if (reasoningContent.isNotEmpty() &&
                                                                        reasoningContent != "null"
                                                        ) {
                                                            if (!isInReasoningMode) {
                                                                isInReasoningMode = true
                                                                // 第一次发现思考内容，发射<think>开始标签
                                                                if (!hasEmittedThinkStart) {
                                                                    emit("<think>")
                                                                    receivedContent.append("<think>")
                                                                    hasEmittedThinkStart = true
                                                                }
                                                            }
                                                            // 发射思考内容
                                                            emit(reasoningContent)
                                                            receivedContent.append(reasoningContent)
                                                            tokenCacheManager.addOutputTokens(
                                                                    ChatUtils.estimateTokenCount(
                                                                            reasoningContent
                                                                    )
                                                            )
                                                            onTokensUpdated(tokenCacheManager.totalInputTokenCount, tokenCacheManager.cachedInputTokenCount, tokenCacheManager.outputTokenCount)
                                                        }
                                                        // 处理常规内容
                                                        else if (regularContent.isNotEmpty() &&
                                                                        regularContent != "null"
                                                        ) {
                                                            // 如果之前在思考模式，现在切换到了常规内容，需要关闭思考标签
                                                            if (isInReasoningMode) {
                                                                isInReasoningMode = false
                                                                emit("</think>")
                                                                receivedContent.append("</think>")
                                                            }

                                                            // 当收到第一个有效内容时，标记不再是首次响应
                                                            if (isFirstResponse) {
                                                                isFirstResponse = false
                                                                Log.d(
                                                                        "AIService",
                                                                        "【发送消息】收到首个有效内容片段"
                                                                )
                                                            }

                                                            // 更新内容
                                                            currentContent.append(regularContent)
                                                            receivedContent.append(regularContent)

                                                            // 计算输出tokens
                                                            tokenCacheManager.addOutputTokens(
                                                                    ChatUtils.estimateTokenCount(
                                                                            regularContent
                                                                    )
                                                            )
                                                            onTokensUpdated(tokenCacheManager.totalInputTokenCount, tokenCacheManager.cachedInputTokenCount, tokenCacheManager.outputTokenCount)

                                                            // 发射内容
                                                            emit(regularContent)
                                                        }
                                                    }
                                                    // 处理message格式（非流式响应）
                                                    else {
                                                        val message =
                                                                choice.optJSONObject("message")
                                                        if (message != null) {
                                                            val reasoningContent =
                                                                    message.optString(
                                                                            "reasoning_content",
                                                                            ""
                                                                    )
                                                            val regularContent =
                                                                    message.optString("content", "")

                                                            // 先处理思考内容（如果有）
                                                            if (reasoningContent.isNotEmpty() &&
                                                                            reasoningContent !=
                                                                                    "null"
                                                            ) {
                                                                val thinkContent = "<think>" +
                                                                        reasoningContent +
                                                                        "</think>"
                                                                emit(thinkContent)
                                                                receivedContent.append(thinkContent)
                                                                tokenCacheManager.addOutputTokens(
                                                                        ChatUtils.estimateTokenCount(
                                                                                reasoningContent
                                                                        )
                                                                )
                                                                onTokensUpdated(tokenCacheManager.totalInputTokenCount, tokenCacheManager.cachedInputTokenCount, tokenCacheManager.outputTokenCount)
                                                            }

                                                            // 然后处理常规内容
                                                            if (regularContent.isNotEmpty() &&
                                                                            regularContent != "null"
                                                            ) {
                                                                emit(regularContent)
                                                                receivedContent.append(regularContent)
                                                                tokenCacheManager.addOutputTokens(
                                                                        ChatUtils.estimateTokenCount(
                                                                                regularContent
                                                                        )
                                                                )
                                                                onTokensUpdated(tokenCacheManager.totalInputTokenCount, tokenCacheManager.cachedInputTokenCount, tokenCacheManager.outputTokenCount)
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                // 忽略解析错误，继续处理下一行
                                                Log.w("AIService", "【发送消息】JSON解析错误: ${e.message}")
                                            }
                                        } else {
                                            // 收到流结束标记，如果还在思考模式，确保关闭思考标签
                                            if (isInReasoningMode) {
                                                isInReasoningMode = false
                                                emit("</think>")
                                                receivedContent.append("</think>")
                                            }
                                            Log.d("AIService", "【发送消息】收到流结束标记[DONE]")
                                        }
                                    }
                                }
                            }
                            Log.d(
                                    "AIService",
                                    "【发送消息】响应流处理完成，总块数: $chunkCount，输出token: ${tokenCacheManager.outputTokenCount}"
                            )
                        } catch (e: IOException) {
                            // 捕获IO异常，可能是由于取消Call导致的，也可能是网络中断
                            if (isManuallyCancelled) {
                                Log.d("AIService", "【发送消息】流式传输已被用户取消，停止后续操作。")
                                throw UserCancellationException("请求已被用户取消", e)
                            } else {
                                // 这是网络中断的关键点，我们将在这里处理重试逻辑，而不是直接抛出异常
                                Log.e("AIService", "【发送消息】流式读取时发生IO异常，准备重试", e)
                                lastException = e
                                // 跳出useLines和try-catch，进入外层while循环进行重试
                                throw e
                            }
                        }
                    }

                    // 清理活跃Call引用
                    activeCall = null
                    Log.d("AIService", "【发送消息】响应处理完成，已清理活跃Call引用")
                } finally {
                    response.close()
                    Log.d("AIService", "【发送消息】关闭响应连接")
                }

                // 成功处理后返回
                Log.d(
                        "AIService",
                        "【发送消息】请求成功完成，输入token: ${tokenCacheManager.totalInputTokenCount}(缓存:${tokenCacheManager.cachedInputTokenCount})，输出token: ${tokenCacheManager.outputTokenCount}"
                )
                return@stream
            } catch (e: NonRetriableException) {
                Log.e("AIService", "【发送消息】发生不可重试错误", e)
                throw e // 直接抛出，不重试
            } catch (e: SocketTimeoutException) {
                if (isManuallyCancelled) {
                    Log.d("AIService", "请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    Log.e("AIService", "【发送消息】连接超时且达到最大重试次数", e)
                    throw IOException("AI响应获取失败，连接超时且已达最大重试次数: ${e.message}")
                }
                Log.w("AIService", "【发送消息】连接超时，正在进行第 $retryCount 次重试...", e)
                onNonFatalError("【网络超时，正在进行第 $retryCount 次重试...】")
                // 指数退避重试
                delay(1000L * (1 shl (retryCount - 1)))
            } catch (e: UnknownHostException) {
                if (isManuallyCancelled) {
                    Log.d("AIService", "请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                // 对于无法解析主机这类错误，也应该重试，因为网络可能会恢复（例如切换wifi）
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    Log.e("AIService", "【发送消息】无法解析主机且达到最大重试次数", e)
                throw IOException("无法连接到服务器，请检查网络连接或API地址是否正确")
                }
                Log.w("AIService", "【发送消息】无法解析主机，正在进行第 $retryCount 次重试...", e)
                onNonFatalError("【网络不稳定，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            } catch (e: IOException) {
                if (isManuallyCancelled) {
                    Log.d("AIService", "请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                // 这个catch块现在主要处理来自流读取中断的IO异常
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    Log.e("AIService", "【发送消息】达到最大重试次数，无法恢复", e)
                    throw IOException("AI响应获取失败，已达最大重试次数: ${e.message}")
                }
                Log.w("AIService", "【发送消息】网络中断，正在进行第 $retryCount 次重试...", e)
                onNonFatalError("【网络中断，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1))) // 增加延迟
            } catch (e: Exception) {
                if (isManuallyCancelled) {
                    Log.d("AIService", "请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                 // 其他未知异常，不应重试
                Log.e("AIService", "【发送消息】发生未知异常，停止重试", e)
                throw IOException("AI响应获取失败: ${e.message}", e)
            }
        }

        // 所有重试都失败
        Log.e("AIService", "【发送消息】重试失败，请检查网络连接，最大重试次数: $maxRetries", lastException)
        throw IOException("连接超时或中断，已重试 $maxRetries 次: ${lastException?.message}")
    }
}
