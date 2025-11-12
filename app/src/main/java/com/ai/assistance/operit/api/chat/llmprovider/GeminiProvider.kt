package com.ai.assistance.operit.api.chat.llmprovider

import android.util.Log
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ParameterCategory
import com.ai.assistance.operit.util.ChatUtils
import com.ai.assistance.operit.util.TokenCacheManager
import com.ai.assistance.operit.util.exceptions.UserCancellationException
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.util.stream.stream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Google Gemini API的实现 支持标准Gemini接口流式传输 */
class GeminiProvider(
    private val apiEndpoint: String,
    private val apiKeyProvider: ApiKeyProvider,
    private val modelName: String,
    private val client: OkHttpClient,
    private val customHeaders: Map<String, String> = emptyMap(),
    private val providerType: ApiProviderType = ApiProviderType.GOOGLE
) : AIService {
    companion object {
        private const val TAG = "GeminiProvider"
        private const val DEBUG = true // 开启调试日志
    }

    // HTTP客户端
    // private val client: OkHttpClient = HttpClientFactory.instance

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // 活跃请求，用于取消流式请求
    private var activeCall: Call? = null
    @Volatile private var isManuallyCancelled = false

    /**
     * 不可重试的异常，通常由客户端错误（如4xx状态码）引起
     */
    class NonRetriableException(message: String, cause: Throwable? = null) : IOException(message, cause)

    // Token计数
    private val tokenCacheManager = TokenCacheManager()
    
    // 思考状态跟踪
    private var isInThinkingMode = false

    override val inputTokenCount: Int
        get() = tokenCacheManager.totalInputTokenCount
    override val cachedInputTokenCount: Int
        get() = tokenCacheManager.cachedInputTokenCount
    override val outputTokenCount: Int
        get() = tokenCacheManager.outputTokenCount

    // 供应商:模型标识符
    override val providerModel: String
        get() = "${providerType.name}:$modelName"

    // 取消当前流式传输
    override fun cancelStreaming() {
        isManuallyCancelled = true
        activeCall?.let {
            if (!it.isCanceled()) {
                it.cancel()
                Log.d(TAG, "已取消当前流式传输")
            }
        }
        activeCall = null
    }

    // 重置Token计数
    override fun resetTokenCounts() {
        tokenCacheManager.resetTokenCounts()
        isInThinkingMode = false
    }

    override suspend fun calculateInputTokens(
            message: String,
            chatHistory: List<Pair<String, String>>
    ): Int {
        return tokenCacheManager.calculateInputTokens(message, chatHistory)
    }

    /**
     * 构建包含文本和图片的parts数组
     */
    private fun buildPartsArray(text: String): JSONArray {
        val partsArray = JSONArray()
        
        if (ImageLinkParser.hasImageLinks(text)) {
            val imageLinks = ImageLinkParser.extractImageLinks(text)
            val textWithoutLinks = ImageLinkParser.removeImageLinks(text).trim()
            
            // 添加图片
            imageLinks.forEach { link ->
                partsArray.put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", link.mimeType)
                        put("data", link.base64Data)
                    })
                })
            }
            
            // 添加文本（如果有）
            if (textWithoutLinks.isNotEmpty()) {
                partsArray.put(JSONObject().apply {
                    put("text", textWithoutLinks)
                })
            }
        } else {
            // 纯文本消息
            partsArray.put(JSONObject().apply {
                put("text", text)
            })
        }
        
        return partsArray
    }

    private fun buildContentsAndCountTokens(
            message: String,
            chatHistory: List<Pair<String, String>>
    ): Pair<Pair<JSONArray, JSONObject?>, Int> {
        var tokenCount = 0
        val contentsArray = JSONArray()
        var systemInstruction: JSONObject? = null

        val standardizedHistory = ChatUtils.mapChatHistoryToStandardRoles(chatHistory)

        // Find and process system message first
        val systemMessage = standardizedHistory.find { it.first == "system" }
        if (systemMessage != null) {
            val systemContent = systemMessage.second
            logDebug("发现系统消息: ${systemContent.take(50)}...")
            tokenCount += ChatUtils.estimateTokenCount(systemContent) + 20 // Extra for formatting

            systemInstruction = JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemContent) })
                })
            }
        }

        // Process the rest of the history
        val historyWithoutSystem = standardizedHistory.filter { it.first != "system" }
        val mergedHistory = mutableListOf<Pair<String, String>>()
        for ((role, content) in historyWithoutSystem) {
            if (mergedHistory.isNotEmpty() && mergedHistory.last().first == role) {
                val lastMessage = mergedHistory.last()
                mergedHistory[mergedHistory.size - 1] =
                    Pair(role, lastMessage.second + "\n" + content)
                logDebug("合并连续的 $role 消息")
            } else {
                mergedHistory.add(Pair(role, content))
            }
        }

        for ((role, content) in mergedHistory) {
            val contentObject =
                JSONObject().apply {
                    put("role", if (role == "assistant") "model" else role)
                    put("parts", buildPartsArray(content))
                }
            contentsArray.put(contentObject)
            tokenCount += ChatUtils.estimateTokenCount(content)
        }

        // Add current user message (with duplicate prevention like OpenAIProvider)
        val lastMessageRole = if (contentsArray.length() > 0) {
            contentsArray.getJSONObject(contentsArray.length() - 1).getString("role")
        } else null

        if (lastMessageRole != "user") {
            // Last message is not user, safe to add
            val userContentObject = JSONObject().apply {
                put("role", "user")
                put("parts", buildPartsArray(message))
            }
            contentsArray.put(userContentObject)
            tokenCount += ChatUtils.estimateTokenCount(message)
        } else {
            // Last message is already user, try to merge
            val lastMessage = contentsArray.getJSONObject(contentsArray.length() - 1)
            val lastParts = lastMessage.getJSONArray("parts")

            // Find the text part by searching backwards from the end of the parts array.
            // This is because image parts are added first, followed by a single optional text part.
            val textPart = (lastParts.length() - 1 downTo 0)
                .map { lastParts.getJSONObject(it) }
                .find { it.has("text") }

            val lastText = textPart?.optString("text", "") ?: ""

            if (lastText != message) {
                tokenCount += ChatUtils.estimateTokenCount(message)
                if (textPart != null) {
                    // Found an existing text part, so we'll merge the new message into it.
                    val combinedText = "$lastText\n$message"
                    textPart.put("text", combinedText)
                    logDebug("合并连续的user消息")
                } else {
                    // No text part was found in the previous message, so add a new one.
                    // This handles cases where the last user message contained only an image.
                    lastParts.put(JSONObject().apply { put("text", message) })
                    logDebug("为连续的user消息添加新的文本部分")
                }
            } else {
                // The new message is identical to the last one, so we can skip it.
                logDebug("跳过重复的user消息")
            }
        }

        return Pair(Pair(contentsArray, systemInstruction), tokenCount)
    }

    // 工具函数：分块打印大型文本日志
    private fun logLargeString(tag: String, message: String, prefix: String = "") {
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

    // 日志辅助方法
    private fun logDebug(message: String) {
        if (DEBUG) {
            Log.d(TAG, message)
        }
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }

    /** 发送消息到Gemini API */
    override suspend fun sendMessage(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean,
            stream: Boolean,
            onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
            onNonFatalError: suspend (error: String) -> Unit
    ): Stream<String> = stream {
        isManuallyCancelled = false
        val requestId = System.currentTimeMillis().toString()
        // 重置token计数和思考状态
        resetTokenCounts()
        onTokensUpdated(
                tokenCacheManager.totalInputTokenCount,
                tokenCacheManager.cachedInputTokenCount,
                tokenCacheManager.outputTokenCount
        )

        Log.d(TAG, "发送消息到Gemini API, 模型: $modelName")

        val maxRetries = 3
        var retryCount = 0
        var lastException: Exception? = null

        // 用于保存已接收到的内容，以便在重试时使用
        val receivedContent = StringBuilder()

        // 捕获stream collector的引用
        val streamCollector = this

        // 状态更新函数 - 在Stream中我们使用emit来传递连接状态
        val emitConnectionStatus: (String) -> Unit = { status ->
            // 这里可以根据需要处理连接状态，例如记录日志
            logDebug("连接状态: $status")
        }

        emitConnectionStatus("连接到Gemini服务...")

        while (retryCount < maxRetries) {
            try {
                // 如果是重试，我们需要构建一个新的请求
                val currentMessage: String
                val currentHistory: List<Pair<String, String>>

                if (retryCount > 0 && receivedContent.isNotEmpty()) {
                    Log.d(TAG, "【Gemini 重试】准备续写请求，已接收内容长度: ${receivedContent.length}")
                    currentMessage = message + "\n\n[SYSTEM NOTE] The previous response was cut off by a network error. You MUST continue from the exact point of interruption. Do not repeat any content. If you were in the middle of a code block or XML tag, complete it. Just output the text that would have come next."
                    val resumeHistory = chatHistory.toMutableList()
                    resumeHistory.add("model" to receivedContent.toString()) // Gemini uses 'model' role for assistant
                    currentHistory = resumeHistory
                } else {
                    currentMessage = message
                    currentHistory = chatHistory
                }

                val requestBody = createRequestBody(currentMessage, currentHistory, modelParameters, enableThinking)
                onTokensUpdated(
                        tokenCacheManager.totalInputTokenCount,
                        tokenCacheManager.cachedInputTokenCount,
                        tokenCacheManager.outputTokenCount
                )
                val request = createRequest(requestBody, stream, requestId) // 根据stream参数决定使用流式还是非流式

                val call = client.newCall(request)
                activeCall = call

                emitConnectionStatus("建立连接中...")

                val startTime = System.currentTimeMillis()
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    call.execute().use { response ->
                        val duration = System.currentTimeMillis() - startTime
                        Log.d(TAG, "收到初始响应, 耗时: ${duration}ms, 状态码: ${response.code}")

                        emitConnectionStatus("连接成功，处理响应...")

                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string() ?: "无错误详情"
                            logError("API请求失败: ${response.code}, $errorBody")
                            // 对于4xx这类明确的客户端错误，直接抛出，不进行重试
                            if (response.code in 400..499) {
                                throw NonRetriableException("API请求失败: ${response.code}, $errorBody")
                            }
                            // 对于5xx等服务端错误，允许重试
                            throw IOException("API请求失败: ${response.code}, $errorBody")
                        }

                        // 根据stream参数处理响应
                        if (stream) {
                            // 处理流式响应
                            processStreamingResponse(response, streamCollector, requestId, onTokensUpdated, receivedContent)
                        } else {
                            // 处理非流式响应并转换为Stream
                            processNonStreamingResponse(response, streamCollector, requestId, onTokensUpdated, receivedContent)
                        }
                    }
                }

                activeCall = null
                return@stream
            } catch (e: NonRetriableException) {
                logError("发生不可重试错误", e)
                throw e // 直接抛出，不重试
            } catch (e: SocketTimeoutException) {
                if (isManuallyCancelled) {
                    logError("请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    logError("连接超时且达到最大重试次数", e)
                    throw IOException("AI响应获取失败，连接超时且已达最大重试次数: ${e.message}")
                }
                logError("连接超时，尝试重试 $retryCount/$maxRetries", e)
                onNonFatalError("【网络超时，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            } catch (e: UnknownHostException) {
                if (isManuallyCancelled) {
                    logError("请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    logError("无法解析主机且达到最大重试次数", e)
                    throw IOException("无法连接到服务器，请检查网络连接或API地址是否正确")
                }
                logError("无法解析主机，尝试重试 $retryCount/$maxRetries", e)
                onNonFatalError("【网络不稳定，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            } catch (e: IOException) {
                if (isManuallyCancelled) {
                    logError("请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                // 捕获所有其他IO异常，包括流读取中断
                lastException = e
                retryCount++
                if (retryCount >= maxRetries) {
                    logError("达到最大重试次数后仍然失败", e)
                    throw IOException("AI响应获取失败，已达最大重试次数: ${e.message}")
                }
                logError("IO异常，尝试重试 $retryCount/$maxRetries", e)
                onNonFatalError("【网络中断，正在进行第 $retryCount 次重试...】")
                delay(1000L * (1 shl (retryCount - 1)))
            } catch (e: Exception) {
                if (isManuallyCancelled) {
                    logError("请求被用户取消，停止重试。")
                    throw UserCancellationException("请求已被用户取消", e)
                }
                lastException = e
                logError("发送消息时发生未知异常，不进行重试", e)
                throw IOException("AI响应获取失败: ${e.message}", e)
            }
        }

        logError("重试${maxRetries}次后仍然失败", lastException)
        throw IOException("连接超时或中断，已重试 $maxRetries 次: ${lastException?.message}")
    }

    /** 创建请求体 */
    private fun createRequestBody(
            message: String,
            chatHistory: List<Pair<String, String>>,
            modelParameters: List<ModelParameter<*>>,
            enableThinking: Boolean
    ): RequestBody {
        val json = JSONObject()

        tokenCacheManager.calculateInputTokens(message, chatHistory)
        val (contentsResult, _) = buildContentsAndCountTokens(message, chatHistory)
        val (contentsArray, systemInstruction) = contentsResult

        if (systemInstruction != null) {
            json.put("systemInstruction", systemInstruction)
        }
        json.put("contents", contentsArray)

        // 添加生成配置
        val generationConfig = JSONObject()

        // 如果启用了思考模式，则为Gemini模型添加特定的`thinkingConfig`参数
        if (enableThinking) {
            val thinkingConfig = JSONObject()
            thinkingConfig.put("includeThoughts", true)
            generationConfig.put("thinkingConfig", thinkingConfig)
            logDebug("已为Gemini模型启用“思考模式”。")
        }

        // 添加模型参数
        for (param in modelParameters) {
            if (param.isEnabled) {
                when (param.apiName) {
                    "temperature" ->
                            generationConfig.put(
                                    "temperature",
                                    (param.currentValue as Number).toFloat()
                            )
                    "top_p" ->
                            generationConfig.put("topP", (param.currentValue as Number).toFloat())
                    "top_k" -> generationConfig.put("topK", (param.currentValue as Number).toInt())
                    "max_tokens" ->
                            generationConfig.put(
                                    "maxOutputTokens",
                                    (param.currentValue as Number).toInt()
                            )
                    else -> {
                        when (param.valueType) {
                            com.ai.assistance.operit.data.model.ParameterValueType.OBJECT -> {
                                val raw = param.currentValue.toString().trim()
                                val parsed: Any? = try {
                                    when {
                                        raw.startsWith("{") -> JSONObject(raw)
                                        raw.startsWith("[") -> JSONArray(raw)
                                        else -> null
                                    }
                                } catch (e: Exception) {
                                    logError("Gemini OBJECT参数解析失败: ${param.apiName}", e)
                                    null
                                }
                                if (param.category == ParameterCategory.OTHER) {
                                    if (parsed != null) {
                                        json.put(param.apiName, parsed)
                                    } else {
                                        json.put(param.apiName, raw)
                                    }
                                } else {
                                    if (parsed != null) {
                                        generationConfig.put(param.apiName, parsed)
                                    } else {
                                        generationConfig.put(param.apiName, raw)
                                    }
                                }
                            }
                            else -> generationConfig.put(param.apiName, param.currentValue)
                        }
                    }
                }
            }
        }

        json.put("generationConfig", generationConfig)

        val jsonString = json.toString()
        // 使用分块日志函数记录完整的请求体
        logLargeString(TAG, jsonString, "请求体JSON: ")

        return jsonString.toRequestBody(JSON)
    }

    /** 创建HTTP请求 */
    private suspend fun createRequest(
            requestBody: RequestBody,
            isStreaming: Boolean,
            requestId: String
    ): Request {
        // 确定请求URL
        val baseUrl = determineBaseUrl(apiEndpoint)
        val method = if (isStreaming) "streamGenerateContent" else "generateContent"
        val requestUrl = "$baseUrl/v1beta/models/$modelName:$method"

        Log.d(TAG, "请求URL: $requestUrl")

        // 创建Request Builder
        val builder = Request.Builder()

        // 添加自定义请求头
        customHeaders.forEach { (key, value) ->
            builder.addHeader(key, value)
        }

        // 添加API密钥
        val currentApiKey = apiKeyProvider.getApiKey()
        val finalUrl =
                if (requestUrl.contains("?")) {
                    "$requestUrl&key=$currentApiKey"
                } else {
                    "$requestUrl?key=$currentApiKey"
                }

        val request = builder.url(finalUrl)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

        logLargeString(TAG, "请求头: \n${request.headers}")
        return request
    }

    /** 确定基础URL */
    private fun determineBaseUrl(endpoint: String): String {
        return try {
            val url = URL(endpoint)
            val port = if (url.port != -1) ":${url.port}" else ""
            "${url.protocol}://${url.host}${port}"
        } catch (e: Exception) {
            logError("解析API端点失败", e)
            "https://generativelanguage.googleapis.com"
        }
    }

    /** 处理API流式响应 */
    private suspend fun processStreamingResponse(
            response: Response,
            streamCollector: StreamCollector<String>,
            requestId: String,
            onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
            receivedContent: StringBuilder
    ) {
        Log.d(TAG, "开始处理响应流")
        val responseBody = response.body ?: throw IOException("响应为空")
        val reader = responseBody.charStream().buffered()

        // 注意：不再使用fullContent累积所有内容
        var lineCount = 0
        var dataCount = 0
        var jsonCount = 0
        var contentCount = 0

        // 恢复JSON累积逻辑，用于处理分段JSON
        val completeJsonBuilder = StringBuilder()
        var isCollectingJson = false
        var jsonDepth = 0
        var jsonStartSymbol = ' ' // 记录JSON是以 { 还是 [ 开始的

        try {
            reader.useLines { lines ->
                lines.forEach { line ->
                    lineCount++
                    // 检查是否已取消
                    if (activeCall?.isCanceled() == true) {
                        return@forEach
                    }

                    // 处理SSE数据
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6).trim()
                        dataCount++

                        // 跳过结束标记
                        if (data == "[DONE]") {
                            logDebug("收到流结束标记 [DONE]")
                            return@forEach
                        }

                        try {
                            // 立即解析每个SSE数据行的JSON
                            val json = JSONObject(data)
                            jsonCount++

                            val content = extractContentFromJson(json, requestId, onTokensUpdated)
                            if (content.isNotEmpty()) {
                                contentCount++
                                logDebug("提取SSE内容，长度: ${content.length}")
                                receivedContent.append(content)

                                // 只发送新增的内容
                                streamCollector.emit(content)
                            }
                        } catch (e: Exception) {
                            logError("解析SSE响应数据失败: ${e.message}", e)
                        }
                    } else if (line.trim().isNotEmpty()) {
                        // 处理可能分段的JSON数据
                        val trimmedLine = line.trim()

                        // 检查是否开始收集JSON
                        if (!isCollectingJson &&
                                        (trimmedLine.startsWith("{") || trimmedLine.startsWith("["))
                        ) {
                            isCollectingJson = true
                            jsonDepth = 0
                            completeJsonBuilder.clear()
                            jsonStartSymbol = trimmedLine[0]
                            logDebug("开始收集JSON，起始符号: $jsonStartSymbol")
                        }

                        if (isCollectingJson) {
                            completeJsonBuilder.append(trimmedLine)

                            // 更新JSON深度
                            for (char in trimmedLine) {
                                if (char == '{' || char == '[') jsonDepth++
                                if (char == '}' || char == ']') jsonDepth--
                            }

                            // 尝试作为完整JSON解析
                            val possibleComplete = completeJsonBuilder.toString()
                            try {
                                if (jsonDepth == 0) {
                                    logDebug("尝试解析完整JSON: ${possibleComplete.take(50)}...")
                                    val jsonContent =
                                            if (jsonStartSymbol == '[') {
                                                JSONArray(possibleComplete)
                                            } else {
                                                JSONObject(possibleComplete)
                                            }

                                    // 解析成功，处理内容
                                    logDebug("成功解析完整JSON，长度: ${possibleComplete.length}")

                                    when (jsonContent) {
                                        is JSONArray -> {
                                            // 处理JSON数组
                                            for (i in 0 until jsonContent.length()) {
                                                val jsonObject = jsonContent.optJSONObject(i)
                                                if (jsonObject != null) {
                                                    jsonCount++
                                                    val content =
                                                            extractContentFromJson(
                                                                    jsonObject,
                                                                    requestId,
                                                                    onTokensUpdated
                                                            )
                                                    if (content.isNotEmpty()) {
                                                        contentCount++
                                                        logDebug(
                                                                "从JSON数组[$i]提取内容，长度: ${content.length}"
                                                        )
                                                        receivedContent.append(content)

                                                        // 只发送这个单独对象产生的内容
                                                        streamCollector.emit(content)
                                                    }
                                                }
                                            }
                                        }
                                        is JSONObject -> {
                                            // 处理JSON对象
                                            jsonCount++
                                            val content =
                                                    extractContentFromJson(jsonContent, requestId, onTokensUpdated)
                                            if (content.isNotEmpty()) {
                                                contentCount++
                                                logDebug("从JSON对象提取内容，长度: ${content.length}")
                                                receivedContent.append(content)

                                                // 只发送新提取的内容
                                                streamCollector.emit(content)
                                            }
                                        }
                                    }

                                    // 解析成功后重置收集器
                                    isCollectingJson = false
                                    completeJsonBuilder.clear()
                                }
                            } catch (e: Exception) {
                                // JSON尚未完整，继续收集
                                if (jsonDepth > 0) {
                                    // 仍在收集，这是预期的
                                    logDebug("继续收集JSON，当前深度: $jsonDepth")
                                } else {
                                    // 深度为0但解析失败，可能是无效JSON
                                    logError("JSON解析失败: ${e.message}", e)
                                    isCollectingJson = false
                                    completeJsonBuilder.clear()
                                }
                            }
                        }
                    }
                }
            }

            Log.d(TAG, "响应处理完成: 共${lineCount}行, ${jsonCount}个JSON块, 提取${contentCount}个内容块")

            // 检查是否还有未解析完的JSON
            if (isCollectingJson && completeJsonBuilder.isNotEmpty()) {
                try {
                    val finalJson = completeJsonBuilder.toString()
                    Log.d(TAG, "处理最终收集的JSON，长度: ${finalJson.length}")

                    val jsonContent =
                            if (jsonStartSymbol == '[') {
                                JSONArray(finalJson)
                            } else {
                                JSONObject(finalJson)
                            }
                    // 处理内容
                    when (jsonContent) {
                        is JSONArray -> {
                            for (i in 0 until jsonContent.length()) {
                                val jsonObject = jsonContent.optJSONObject(i) ?: continue
                                jsonCount++
                                val content = extractContentFromJson(jsonObject, requestId, onTokensUpdated)
                                if (content.isNotEmpty()) {
                                    contentCount++
                                    logDebug("从最终JSON数组[$i]提取内容，长度: ${content.length}")
                                    receivedContent.append(content)
                                    streamCollector.emit(content)
                                }
                            }
                        }
                        is JSONObject -> {
                            jsonCount++
                            val content = extractContentFromJson(jsonContent, requestId, onTokensUpdated)
                            if (content.isNotEmpty()) {
                                contentCount++
                                logDebug("从最终JSON对象提取内容，长度: ${content.length}")
                                receivedContent.append(content)
                                streamCollector.emit(content)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logError("解析最终收集的JSON失败: ${e.message}", e)
                }
            }

            // 确保思考模式正确结束
            if (isInThinkingMode) {
                logDebug("流结束时仍在思考模式，添加结束标签")
                streamCollector.emit("</think>")
                isInThinkingMode = false
            }
            
            // 确保至少发送一次内容
            if (contentCount == 0) {
                logDebug("未检测到内容，发送空格")
                streamCollector.emit(" ")
            }
        } catch (e: Exception) {
            logError("处理响应时发生异常: ${e.message}", e)
            throw e
        } finally {
            activeCall = null
        }
    }

    /** 处理API非流式响应 */
    private suspend fun processNonStreamingResponse(
            response: Response,
            streamCollector: StreamCollector<String>,
            requestId: String,
            onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
            receivedContent: StringBuilder
    ) {
        Log.d(TAG, "开始处理非流式响应")
        val responseBody = response.body ?: throw IOException("响应为空")
        
        try {
            val responseText = responseBody.string()
            logDebug("收到完整响应，长度: ${responseText.length}")
            
            // 解析JSON响应
            val json = JSONObject(responseText)
            
            // 提取内容
            val content = extractContentFromJson(json, requestId, onTokensUpdated)
            
            if (content.isNotEmpty()) {
                receivedContent.append(content)
                
                // 直接发送整个内容块，下游会自己处理
                streamCollector.emit(content)
                
                logDebug("非流式响应处理完成，总长度: ${content.length}")
            } else {
                logDebug("未检测到内容，发送空格")
                streamCollector.emit(" ")
            }
            
            // 确保思考模式正确结束
            if (isInThinkingMode) {
                logDebug("非流式响应结束时仍在思考模式，添加结束标签")
                streamCollector.emit("</think>")
                isInThinkingMode = false
            }
        } catch (e: Exception) {
            logError("处理非流式响应时发生异常: ${e.message}", e)
            throw e
        } finally {
            activeCall = null
        }
    }

    /** 从Gemini响应JSON中提取内容 */
    private suspend fun extractContentFromJson(
        json: JSONObject,
        requestId: String,
        onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit
    ): String {
        val contentBuilder = StringBuilder()

        try {
            // 检查是否有错误信息
            if (json.has("error")) {
                val error = json.getJSONObject("error")
                val errorMsg = error.optString("message", "未知错误")
                logError("API返回错误: $errorMsg")
                return "" // 有错误时返回空字符串
            }

            // 提取候选项
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                logDebug("未找到候选项")
                return ""
            }

            // 处理第一个candidate
            val candidate = candidates.getJSONObject(0)

            // 检查finish_reason
            val finishReason = candidate.optString("finishReason", "")
            if (finishReason.isNotEmpty() && finishReason != "STOP") {
                logDebug("收到完成原因: $finishReason")
            }

            // 提取content对象
            val content = candidate.optJSONObject("content")
            if (content == null) {
                logDebug("未找到content对象")
                return ""
            }

            // 提取parts数组
            val parts = content.optJSONArray("parts")
            if (parts == null || parts.length() == 0) {
                logDebug("未找到parts数组或为空")
                return ""
            }

            // 遍历parts，提取text内容
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val text = part.optString("text", "")
                val isThought = part.optBoolean("thought", false)

                if (text.isNotEmpty()) {
                    // 处理思考模式状态切换
                    if (isThought && !isInThinkingMode) {
                        // 开始思考模式
                        contentBuilder.append("<think>")
                        isInThinkingMode = true
                        logDebug("开始思考模式")
                    } else if (!isThought && isInThinkingMode) {
                        // 结束思考模式
                        contentBuilder.append("</think>")
                        isInThinkingMode = false
                        logDebug("结束思考模式")
                    }
                    
                    // 添加文本内容
                    contentBuilder.append(text)
                    
                    if (isThought) {
                        logDebug("提取思考内容，长度=${text.length}")
                    } else {
                        logDebug("提取文本，长度=${text.length}")
                    }

                    // 估算token
                    val tokens = ChatUtils.estimateTokenCount(text)
                    tokenCacheManager.addOutputTokens(tokens)
                    onTokensUpdated(
                            tokenCacheManager.totalInputTokenCount,
                            tokenCacheManager.cachedInputTokenCount,
                            tokenCacheManager.outputTokenCount
                    )
                }
            }

            // 提取实际的token使用数据
            val usageMetadata = json.optJSONObject("usageMetadata")
            if (usageMetadata != null) {
                val promptTokenCount = usageMetadata.optInt("promptTokenCount", 0)
                val cachedContentTokenCount = usageMetadata.optInt("cachedContentTokenCount", 0)
                val candidatesTokenCount = usageMetadata.optInt("candidatesTokenCount", 0)
                
                if (promptTokenCount > 0) {
                    // 更新实际的token计数
                    val actualInputTokens = promptTokenCount - cachedContentTokenCount
                    tokenCacheManager.updateActualTokens(actualInputTokens, cachedContentTokenCount)
                    
                    logDebug("API实际Token使用: 输入=$actualInputTokens, 缓存=$cachedContentTokenCount, 输出=$candidatesTokenCount")
                    
                    // 更新回调，使用实际的token统计
                    onTokensUpdated(
                        promptTokenCount,
                        cachedContentTokenCount,
                        candidatesTokenCount
                    )
                }
            }

            return contentBuilder.toString()
        } catch (e: Exception) {
            logError("提取内容时发生错误: ${e.message}", e)
            return ""
        }
    }

    /** 获取模型列表 */
    override suspend fun getModelsList(): Result<List<ModelOption>> {
        return ModelListFetcher.getModelsList(
            apiKey = apiKeyProvider.getApiKey(),
            apiEndpoint = apiEndpoint,
            apiProviderType = ApiProviderType.GOOGLE
        )
    }

    override suspend fun testConnection(): Result<String> {
        return try {
            // 通过发送一条短消息来测试完整的连接、认证和API端点。
            // 这比getModelsList更可靠，因为它直接命中了聊天API。
            // 提供一个通用的系统提示，以防止某些需要它的模型出现错误。
            val testHistory = listOf("system" to "You are a helpful assistant.")
            val stream = sendMessage("Hi", testHistory, emptyList(), false, onTokensUpdated = { _, _, _ -> }, onNonFatalError = {})

            // 消耗流以确保连接有效。
            // 对 "Hi" 的响应应该很短，所以这会很快完成。
            var hasReceivedData = false
            stream.collect {
                hasReceivedData = true
            }

            // 某些情况下，即使连接成功，也可能不会返回任何数据（例如，如果模型只处理了提示而没有生成响应）。
            // 因此，只要不抛出异常，我们就认为连接成功。
            Result.success("连接成功！")
        } catch (e: Exception) {
            logError("连接测试失败", e)
            Result.failure(IOException("连接测试失败: ${e.message}", e))
        }
    }
}
