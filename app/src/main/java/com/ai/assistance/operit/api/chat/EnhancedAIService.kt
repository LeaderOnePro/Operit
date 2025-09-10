package com.ai.assistance.operit.api.chat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.WindowManager
import com.ai.assistance.operit.api.chat.enhance.ConversationMarkupManager
import com.ai.assistance.operit.api.chat.enhance.ConversationRoundManager
import com.ai.assistance.operit.api.chat.enhance.ConversationService
import com.ai.assistance.operit.api.chat.enhance.FileBindingService
import com.ai.assistance.operit.api.chat.enhance.InputProcessor
import com.ai.assistance.operit.api.chat.enhance.MultiServiceManager
import com.ai.assistance.operit.api.chat.enhance.ToolExecutionManager
import com.ai.assistance.operit.core.application.ActivityLifecycleManager
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.InputProcessingState
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.model.ToolInvocation
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.ui.permissions.ToolCategory
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamCollector
import com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

/**
 * Enhanced AI service that provides advanced conversational capabilities by integrating various
 * components like tool execution, conversation management, user preferences, and problem library.
 */
class EnhancedAIService private constructor(private val context: Context) {
    companion object {
        private const val TAG = "EnhancedAIService"

        @Volatile private var INSTANCE: EnhancedAIService? = null

        /**
         * 获取EnhancedAIService实例
         * @param context 应用上下文
         * @return EnhancedAIService实.
         */
        fun getInstance(context: Context): EnhancedAIService {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: EnhancedAIService(context.applicationContext).also {
                                    INSTANCE = it
                                }
                    }
        }

        /**
         * 获取指定功能类型的 AIService 实例（非实例化方式）
         * @param context 应用上下文
         * @param functionType 功能类型
         * @return AIService 实例
         */
        suspend fun getAIServiceForFunction(
                context: Context,
                functionType: FunctionType
        ): AIService {
            return getInstance(context).multiServiceManager.getServiceForFunction(functionType)
        }

        /**
         * 刷新指定功能类型的 AIService 实例（非实例化方式）
         * @param context 应用上下文
         * @param functionType 功能类型
         */
        suspend fun refreshServiceForFunction(context: Context, functionType: FunctionType) {
            getInstance(context).multiServiceManager.refreshServiceForFunction(functionType)
        }

        /**
         * 刷新所有 AIService 实例（非实例化方式）
         * @param context 应用上下文
         */
        suspend fun refreshAllServices(context: Context) {
            getInstance(context).multiServiceManager.refreshAllServices()
        }

        /**
         * 获取指定功能类型的当前输入token计数（非实例化方式）
         * @param context 应用上下文
         * @param functionType 功能类型
         * @return 输入token计数
         */
        suspend fun getCurrentInputTokenCountForFunction(
                context: Context,
                functionType: FunctionType
        ): Int {
            return getInstance(context)
                    .multiServiceManager
                    .getServiceForFunction(functionType)
                    .inputTokenCount
        }

        /**
         * 获取指定功能类型的当前输出token计数（非实例化方式）
         * @param context 应用上下文
         * @param functionType 功能类型
         * @return 输出token计数
         */
        suspend fun getCurrentOutputTokenCountForFunction(
                context: Context,
                functionType: FunctionType
        ): Int {
            return getInstance(context)
                    .multiServiceManager
                    .getServiceForFunction(functionType)
                    .outputTokenCount
        }

        /**
         * 重置指定功能类型或所有功能类型的token计数器（非实例化方式）
         * @param context 应用上下文
         * @param functionType 功能类型，如果为null则重置所有功能类型
         */
        suspend fun resetTokenCountersForFunction(
                context: Context,
                functionType: FunctionType? = null
        ) {
            val instance = getInstance(context)
            if (functionType == null) {
                // 重置所有服务实例的token计数
                FunctionType.values().forEach { type ->
                    try {
                        val service = instance.multiServiceManager.getServiceForFunction(type)
                        service.resetTokenCounts()
                    } catch (e: Exception) {
                        Log.e(TAG, "重置${type}功能的token计数失败", e)
                    }
                }
            } else {
                // 只重置指定功能类型的token计数
                val service = instance.multiServiceManager.getServiceForFunction(functionType)
                service.resetTokenCounts()
            }
        }

        /**
         * 重置所有token计数器（非实例化方式）
         * @param context 应用上下文
         */
        fun resetTokenCounters(context: Context) {
            val instance = getInstance(context)
            instance.aiService.resetTokenCounts()
            instance.accumulatedInputTokenCount = 0
            instance.accumulatedOutputTokenCount = 0
        }

        /**
         * 处理文件绑定操作（非实例化方式）
         * @param context 应用上下文
         * @param originalContent 原始文件内容
         * @param aiGeneratedCode AI生成的代码（包含"//existing code"标记）
         * @return 混合后的文件内容
         */
        suspend fun applyFileBinding(
                context: Context,
                originalContent: String,
                aiGeneratedCode: String
        ): Pair<String, String> {
            // 获取EnhancedAIService实例
            val instance = getInstance(context)

            // 委托给FileBindingService处理
            return instance.fileBindingService.processFileBinding(
                    originalContent,
                    aiGeneratedCode,
                    instance.multiServiceManager
            )
        }
    }

    // MultiServiceManager 管理不同功能的 AIService 实例
    private val multiServiceManager = MultiServiceManager(context)

    // 添加ConversationService实例
    private val conversationService = ConversationService(context)

    // 添加FileBindingService实例
    private val fileBindingService = FileBindingService(context)

    // AIService 实例 - 保留为兼容现有代码，但实际使用 MultiServiceManager
    private val aiService: AIService by lazy {
        runBlocking { multiServiceManager.getDefaultService() }
    }

    // Tool handler for executing tools
    private val toolHandler = AIToolHandler.getInstance(context)

    // 初始化问题库
    init {
        com.ai.assistance.operit.api.chat.library.ProblemLibrary.initialize(context)
        // 初始化 MultiServiceManager
        runBlocking { multiServiceManager.initialize() }
    }

    // State flows for UI updates
    private val _inputProcessingState =
            MutableStateFlow<InputProcessingState>(InputProcessingState.Idle)
    val inputProcessingState = _inputProcessingState.asStateFlow()

    /**
     * 设置当前的输入处理状态
     * @param newState 新的状态
     */
    fun setInputProcessingState(newState: com.ai.assistance.operit.data.model.InputProcessingState) {
        _inputProcessingState.value = newState
    }

    // Per-request token counts
    private val _perRequestTokenCounts = MutableStateFlow<Pair<Int, Int>?>(null)
    val perRequestTokenCounts: StateFlow<Pair<Int, Int>?> = _perRequestTokenCounts.asStateFlow()

    // Conversation management
    // private val streamBuffer = StringBuilder() // Moved to MessageExecutionContext
    // private val roundManager = ConversationRoundManager() // Moved to MessageExecutionContext
    // private val isConversationActive = AtomicBoolean(false) // Moved to MessageExecutionContext

    // Api Preferences for settings
    private val apiPreferences = ApiPreferences.getInstance(context)

    // Execution context for a single sendMessage call to achieve concurrency
    private data class MessageExecutionContext(
        val streamBuffer: StringBuilder = StringBuilder(),
        val roundManager: ConversationRoundManager = ConversationRoundManager(),
        val isConversationActive: AtomicBoolean = AtomicBoolean(true),
        val conversationHistory: MutableList<Pair<String, String>>,
    )

    // Coroutine management
    private val toolProcessingScope = CoroutineScope(Dispatchers.IO)
    private val toolExecutionJobs = ConcurrentHashMap<String, Job>()
    // private val conversationHistory = mutableListOf<Pair<String, String>>() // Moved to MessageExecutionContext
    // private val conversationMutex = Mutex() // Moved to MessageExecutionContext

    // 为服务管理创建独立的Intent实例，避免重复创建
    private val serviceIntent by lazy { Intent(context, AIForegroundService::class.java) }

    private var accumulatedInputTokenCount = 0
    private var accumulatedOutputTokenCount = 0

    // Callbacks
    private var currentResponseCallback: ((content: String, thinking: String?) -> Unit)? = null
    private var currentCompleteCallback: (() -> Unit)? = null

    // Package manager for handling tool packages
    private val packageManager = PackageManager.getInstance(context, toolHandler)

    init {
        toolHandler.registerDefaultTools()
    }

    /**
     * 获取指定功能类型的 AIService 实例
     * @param functionType 功能类型
     * @return AIService 实例
     */
    suspend fun getAIServiceForFunction(functionType: FunctionType): AIService {
        return Companion.getAIServiceForFunction(context, functionType)
    }

    /**
     * 刷新指定功能类型的 AIService 实例 当配置发生更改时调用
     * @param functionType 功能类型
     */
    suspend fun refreshServiceForFunction(functionType: FunctionType) {
        Companion.refreshServiceForFunction(context, functionType)
    }

    /** 刷新所有 AIService 实例 当全局配置发生更改时调用 */
    suspend fun refreshAllServices() {
        Companion.refreshAllServices(context)
    }

    /** Process user input with a delay for UI feedback */
    suspend fun processUserInput(input: String): String {
        _inputProcessingState.value = InputProcessingState.Processing("正在处理输入...")
        return InputProcessor.processUserInput(input)
    }

    /** Send a message to the AI service */
    suspend fun sendMessage(
        message: String,
        chatHistory: List<Pair<String, String>> = emptyList(),
        workspacePath: String? = null,
        functionType: FunctionType = FunctionType.CHAT,
        promptFunctionType: PromptFunctionType = PromptFunctionType.CHAT,
        enableThinking: Boolean = false,
        thinkingGuidance: Boolean = false,
        enableMemoryAttachment: Boolean = true,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        onNonFatalError: suspend (error: String) -> Unit = {},
        customSystemPromptTemplate: String? = null,
        isSubTask: Boolean = false
    ): Stream<String> {
        Log.d(
                TAG,
                "sendMessage调用开始: 功能类型=$functionType, 提示词类型=$promptFunctionType, 思考引导=$thinkingGuidance"
        )
        accumulatedInputTokenCount = 0
        accumulatedOutputTokenCount = 0

        return stream {
            val context = MessageExecutionContext(conversationHistory = chatHistory.toMutableList())
            try {
                // 确保所有操作都在IO线程上执行
                withContext(Dispatchers.IO) {
                    // 仅当会话首次启动时开启服务
                    if (!isSubTask) {
                        startAiService()
                    }

                    // Process the input message for any conversation markup (e.g., for AI planning)
                    val startTime = System.currentTimeMillis()
                    val processedInput = InputProcessor.processUserInput(message)

                    // 将当前用户输入添加到内部历史记录中，以便进行后续处理
                    context.conversationHistory.add(Pair("user", processedInput))

                    // Update state to show we're processing
                    if (!isSubTask) {
                        withContext(Dispatchers.Main) {
                            _inputProcessingState.value = InputProcessingState.Processing("正在处理消息...")
                        }
                    }

                    // Prepare conversation history with system prompt
                    val preparedHistory =
                            prepareConversationHistory(
                                    context.conversationHistory, // 始终使用内部历史记录
                                    processedInput,
                                    workspacePath,
                                    promptFunctionType,
                                    thinkingGuidance,
                                    customSystemPromptTemplate
                            )
                    
                    // 关键修复：用准备好的历史记录（包含了系统提示）去同步更新内部的 conversationHistory 状态
                    context.conversationHistory.clear()
                    context.conversationHistory.addAll(preparedHistory)

                    // Update UI state to connecting
                    if (!isSubTask) {
                        withContext(Dispatchers.Main) {
                            _inputProcessingState.value = InputProcessingState.Connecting("正在连接AI服务...")
                        }
                    }

                    // Get all model parameters from preferences (with enabled state)
                    val modelParameters = multiServiceManager.getModelParametersForFunction(functionType)

                    // 获取对应功能类型的AIService实例
                    val serviceForFunction = getAIServiceForFunction(functionType)

                    // 清空之前的单次请求token计数
                    _perRequestTokenCounts.value = null

                    // 使用新的Stream API
                    Log.d(TAG, "调用AI服务，处理时间: ${System.currentTimeMillis() - startTime}ms")
                    val stream =
                            serviceForFunction.sendMessage(
                                    message = processedInput,
                                    chatHistory = preparedHistory,
                                    modelParameters = modelParameters,
                                    enableThinking = enableThinking,
                                    onTokensUpdated = { input, output ->
                                        _perRequestTokenCounts.value = Pair(input, output)
                                    },
                                    onNonFatalError = onNonFatalError
                            )

                    // 收到第一个响应，更新状态
                    var isFirstChunk = true

                    // 创建一个新的轮次来管理内容
                    context.roundManager.startNewRound()
                    context.streamBuffer.clear()

                    // 从原始stream收集内容并处理
                    var chunkCount = 0
                    var totalChars = 0
                    var lastLogTime = System.currentTimeMillis()
                    val streamStartTime = System.currentTimeMillis()

                    stream.collect { content ->
                        // 第一次收到响应，更新状态
                        if (isFirstChunk) {
                            if (!isSubTask) {
                                withContext(Dispatchers.Main) {
                                    _inputProcessingState.value =
                                            InputProcessingState.Receiving("正在接收AI响应...")
                                }
                            }
                            isFirstChunk = false
                            Log.d(TAG, "首次响应耗时: ${System.currentTimeMillis() - streamStartTime}ms")
                        }

                        // 累计统计
                        chunkCount++
                        totalChars += content.length

                        // 周期性日志
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastLogTime > 5000) { // 每5秒记录一次
                            Log.d(TAG, "已接收 $chunkCount 个内容块，总计 $totalChars 个字符")
                            lastLogTime = currentTime
                        }

                        // 更新streamBuffer，保持与原有逻辑一致
                        context.streamBuffer.append(content)

                        // 更新内容到轮次管理器
                        context.roundManager.updateContent(context.streamBuffer.toString())

                        // 发射当前内容片段
                        emit(content)
                    }

                    // Update accumulated token counts and persist them
                    val inputTokens = serviceForFunction.inputTokenCount
                    val outputTokens = serviceForFunction.outputTokenCount
                    accumulatedInputTokenCount += inputTokens
                    accumulatedOutputTokenCount += outputTokens
                    apiPreferences.updateTokensForFunction(functionType, inputTokens, outputTokens)

                    Log.d(
                            TAG,
                            "Token count updated for $functionType. Input: $inputTokens, Output: $outputTokens. Turn Accumulated: $accumulatedInputTokenCount, $accumulatedOutputTokenCount"
                    )
                    Log.d(
                            TAG,
                            "流收集完成，总计 $totalChars 字符，耗时: ${System.currentTimeMillis() - streamStartTime}ms"
                    )
                }
            } catch (e: Exception) {
                // 对于协程取消异常，这是正常流程，应当向上抛出以停止流
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "sendMessage流被取消")
                    throw e
                }

                // 用户取消导致的 Socket closed 是预期行为，不应作为错误处理
                if (e.message?.contains("Socket closed", ignoreCase = true) == true) {
                    Log.d(TAG, "Stream was cancelled by the user (Socket closed).")
                } else {
                    // Handle any exceptions
                    Log.e(TAG, "发送消息时发生错误: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        _inputProcessingState.value =
                                InputProcessingState.Error(message = "错误: ${e.message}")
                    }
                }

                // 发生无法处理的错误时，也应停止服务，但用户取消除外
                if (e.message?.contains("Socket closed", ignoreCase = true) != true) {
                    if (!isSubTask) stopAiService()
                }
            } finally {
                // 确保流处理完成后调用
                val collector = this
                withContext(Dispatchers.IO) { processStreamCompletion(context, functionType, collector, enableThinking, enableMemoryAttachment, onNonFatalError, maxTokens, tokenUsageThreshold, isSubTask) }
            }
        }
    }

    /**
     * 使用流处理技术增强工具调用检测能力 这个方法通过流式XML解析来辅助识别工具调用，比单纯依赖正则表达式更可靠
     * @param content 需要检测工具调用的内容
     * @return 经过增强检测的内容，可能会修复格式问题
     */
    private suspend fun enhanceToolDetection(content: String): String {
        try {
            // 检查内容是否包含可能的工具调用标记
            if (!content.contains("<tool") && !content.contains("</tool>")) {
                return content
            }

            // 创建字符流以应用流处理，使用 stream() 替代 asCharStream()
            val charStream = content.stream()

            // 使用XML插件来拆分流
            val plugins = listOf(StreamXmlPlugin())

            // 保存增强后的内容
            val enhancedContent = StringBuilder()

            // 追踪是否发现了工具标签
            var foundToolTag = false

            // 处理拆分的结果
            charStream.splitBy(plugins).collect { group ->
                when (val tag = group.tag) {
                    // 匹配到XML标签
                    is StreamXmlPlugin -> {
                        val xmlContent = StringBuilder()
                        group.stream.collect { char -> xmlContent.append(char) }

                        val xml = xmlContent.toString()
                        // 检查是否是工具标签
                        if (xml.contains("<tool") && xml.contains("</tool>")) {
                            foundToolTag = true
                            // 格式标准化，使其符合工具调用的正则表达式预期格式
                            val normalizedXml = normalizeToolXml(xml)
                            enhancedContent.append(normalizedXml)
                            Log.d(TAG, "工具调用XML被增强流处理检测到并标准化")
                        } else {
                            // 保留其他XML标签
                            enhancedContent.append(xml)
                        }
                    }
                    // 纯文本内容
                    null -> {
                        val textContent = StringBuilder()
                        group.stream.collect { char -> textContent.append(char) }
                        enhancedContent.append(textContent.toString())
                    }
                    // 添加必要的else分支
                    else -> {
                        val textContent = StringBuilder()
                        group.stream.collect { char -> textContent.append(char) }
                        enhancedContent.append(textContent.toString())
                        Log.w(TAG, "未知标签类型: ${tag::class.java.simpleName}")
                    }
                }
            }

            // 如果找到了工具标签，返回增强的内容；否则返回原始内容
            return if (foundToolTag) {
                Log.d(TAG, "增强的XML工具检测完成")
                enhancedContent.toString()
            } else {
                content
            }
        } catch (e: Exception) {
            // 如果流处理失败，返回原始内容并记录错误
            Log.e(TAG, "增强工具检测失败: ${e.message}", e)
            return content
        }
    }

    /**
     * 规范化工具XML以符合正则表达式预期
     * @param xml 原始XML文本
     * @return 标准化后的XML
     */
    private fun normalizeToolXml(xml: String): String {
        var result = xml.trim()

        // 确保工具名称格式正确
        result = result.replace(Regex("<tool\\s+name\\s*="), "<tool name=")

        // 确保参数格式正确
        result = result.replace(Regex("<param\\s+name\\s*="), "<param name=")

        return result
    }

    /** 在处理完流后调用，使用增强的工具检测功能 */
    private suspend fun processStreamCompletion(
            context: MessageExecutionContext,
            functionType: FunctionType = FunctionType.CHAT,
            collector: StreamCollector<String>,
            enableThinking: Boolean = false,
            enableMemoryAttachment: Boolean = true,
            onNonFatalError: suspend (error: String) -> Unit,
            maxTokens: Int,
            tokenUsageThreshold: Double,
            isSubTask: Boolean
    ) {
        try {
            val startTime = System.currentTimeMillis()
            // If conversation is no longer active, return immediately
            if (!context.isConversationActive.get()) {
                return
            }

            // Get response content
            val content = context.streamBuffer.toString().trim()

            // If content is empty, it means an error likely occurred or the model returned nothing.
            // We must still finalize the conversation to reset the state correctly.
            if (content.isEmpty()) {
                Log.d(TAG, "Stream content is empty. Finalizing conversation state.")
                // We call handleTaskCompletion to properly set the conversation as inactive and update the UI state.
                // We pass enableMemoryAttachment = false because there's no content to analyze or save.
                handleWaitForUserNeed(context, content, isSubTask)
                return
            }

            // If content is empty, finish immediately
            if (content.isEmpty()) {
                return
            }

            // 使用增强的工具检测功能处理内容
            val enhancedContent = enhanceToolDetection(content)
            // 如果内容被增强修改了，更新到streamBuffer
            if (enhancedContent != content) {
                context.streamBuffer.setLength(0)
                context.streamBuffer.append(enhancedContent)
                // 更新轮次管理器显示内容
                context.roundManager.updateContent(enhancedContent)
            }

            // Handle task completion marker
            if (ConversationMarkupManager.containsTaskCompletion(enhancedContent)) {
                handleTaskCompletion(context, enhancedContent, enableMemoryAttachment, isSubTask)
                return
            }

            // Check again if conversation is active
            if (!context.isConversationActive.get()) {
                return
            }

            // Add current assistant message to conversation history
            try {
                context.conversationHistory.add(Pair("assistant", context.roundManager.getCurrentRoundContent()))
            } catch (e: Exception) {
                Log.e(TAG, "添加助手消息到历史记录失败", e)
                return
            }

            // Check again if conversation is active
            if (!context.isConversationActive.get()) {
                return
            }

            // Main flow: Detect and process tool invocations
            // Try to detect tool invocations
            val toolInvocations = toolHandler.extractToolInvocations(enhancedContent)

            if (toolInvocations.isNotEmpty()) {
                // Handle wait for user need marker
                if (ConversationMarkupManager.containsWaitForUserNeed(enhancedContent)) {
                    val userNeedContent =
                            ConversationMarkupManager.createWarningStatus(
                                    "警告：工具调用和等待用户响应不能同时存在。工具调用被处理了，但这是极具危险性的。",
                            )
                    context.roundManager.appendContent(userNeedContent)
                    collector.emit(userNeedContent)
                    try {
                        context.conversationHistory.add(Pair("tool", userNeedContent))
                    } catch (e: Exception) {
                        Log.e(TAG, "添加工具调用警告到历史记录失败", e)
                    }
                }

                // Add current assistant message to conversation history

                Log.d(
                        TAG,
                        "检测到 ${toolInvocations.size} 个工具调用，处理时间: ${System.currentTimeMillis() - startTime}ms"
                )
                handleToolInvocation(
                        toolInvocations,
                        context,
                        functionType,
                        collector,
                        enableThinking,
                        enableMemoryAttachment,
                        onNonFatalError,
                        maxTokens,
                        tokenUsageThreshold,
                        isSubTask
                )
                return
            }

            // 修改默认行为：如果没有特殊标记或工具调用，默认等待用户输入
            // 而不是直接标记为完成

            // 创建等待用户输入的内容
            val userNeedContent =
                    ConversationMarkupManager.createWaitForUserNeedContent(
                            context.roundManager.getDisplayContent()
                    )

            // 处理为等待用户输入模式
            handleWaitForUserNeed(context, userNeedContent, isSubTask)
            Log.d(TAG, "流完成处理耗时: ${System.currentTimeMillis() - startTime}ms")
        } catch (e: Exception) {
            // Catch any exceptions in the processing flow
            Log.e(TAG, "处理流完成时发生错误", e)
            withContext(Dispatchers.Main) {
                _inputProcessingState.value = InputProcessingState.Idle
            }
        }
    }

    /** Handle task completion logic - simplified version without callbacks */
    private suspend fun handleTaskCompletion(context: MessageExecutionContext, content: String, enableMemoryAttachment: Boolean, isSubTask: Boolean) {
        // Mark conversation as complete
        context.isConversationActive.set(false)

        // 清除内容池
        // roundManager.clearContent()

        // Ensure input processing state is updated to completed
        if (!isSubTask) {
            withContext(Dispatchers.Main) {
                _inputProcessingState.value = InputProcessingState.Completed
            }
        }

        if (enableMemoryAttachment) {
            // 保存问题记录到库
            toolProcessingScope.launch {
                com.ai.assistance.operit.api.chat.library.ProblemLibrary.saveProblemAsync(
                        this@EnhancedAIService.context,
                        toolHandler,
                        context.conversationHistory,
                        content,
                        multiServiceManager.getServiceForFunction(FunctionType.PROBLEM_LIBRARY)
                )
            }
        }

        if (!isSubTask) {
            // 在会话结束后停止服务
            stopAiService()
        }
    }

    /** Handle wait for user need logic - simplified version without callbacks */
    private suspend fun handleWaitForUserNeed(context: MessageExecutionContext, content: String, isSubTask: Boolean) {
        // Mark conversation as complete
        context.isConversationActive.set(false)

        // 清除内容池
        // roundManager.clearContent()

        // Ensure input processing state is updated to completed
        if (!isSubTask) {
            withContext(Dispatchers.Main) {
                _inputProcessingState.value = InputProcessingState.Completed
            }
        }

        Log.d(TAG, "Wait for user need - skipping problem library analysis")
        if (!isSubTask) {
            // 在会话结束后停止服务
            stopAiService()
        }
    }

    /** Handle tool invocation processing - simplified version without callbacks */
    private suspend fun handleToolInvocation(
            toolInvocations: List<ToolInvocation>,
            context: MessageExecutionContext,
            functionType: FunctionType = FunctionType.CHAT,
            collector: StreamCollector<String>,
            enableThinking: Boolean = false,
            enableMemoryAttachment: Boolean = true,
            onNonFatalError: suspend (error: String) -> Unit,
            maxTokens: Int,
            tokenUsageThreshold: Double,
            isSubTask: Boolean
    ) {
        val startTime = System.currentTimeMillis()
        
        if (!isSubTask) {
            withContext(Dispatchers.Main) {
                val toolNames = toolInvocations.joinToString(", ") { it.tool.name }
                val firstCategory = toolHandler.getToolExecutor(toolInvocations.first().tool.name)?.getCategory() ?: ToolCategory.SYSTEM_OPERATION
                _inputProcessingState.value = InputProcessingState.ExecutingTool(toolNames, firstCategory)
            }
        }

        val processToolJob = toolProcessingScope.launch {
            // 权限检查前置，串行进行
            val permittedInvocations = mutableListOf<ToolInvocation>()
            val permissionDeniedResults = mutableListOf<ToolResult>()

            for (invocation in toolInvocations) {
                val (hasPermission, errorResult) = ToolExecutionManager.checkToolPermission(toolHandler, invocation)
                if (hasPermission) {
                    permittedInvocations.add(invocation)
                } else {
                    permissionDeniedResults.add(errorResult!!)
                    val errorStatusContent = ConversationMarkupManager.createErrorStatus("权限拒绝", "操作 '${invocation.tool.name}' 未授权")
                    context.roundManager.appendContent(errorStatusContent)
                    collector.emit(errorStatusContent)
                    val toolResultStatusContent = ConversationMarkupManager.formatToolResultForMessage(errorResult)
                    context.roundManager.appendContent(toolResultStatusContent)
                    collector.emit(toolResultStatusContent)
                }
            }
            
            val executionResults = mutableListOf<ToolResult>()
            for (invocation in permittedInvocations) {
                val executor = toolHandler.getToolExecutor(invocation.tool.name)
                if (executor == null) {
                    val toolName = invocation.tool.name
                    val errorMessage = when {
                        toolName.contains('.') && !toolName.contains(':') -> {
                            val parts = toolName.split('.', limit = 2)
                            "工具调用语法错误: 对于工具包中的工具，应使用 'packName:toolName' 格式，而不是 '${toolName}'。您可能想调用 '${parts.getOrNull(0)}:${parts.getOrNull(1)}'。"
                        }
                        toolName.contains(':') -> {
                            val parts = toolName.split(':', limit = 2)
                            val packName = parts[0]
                            val packageManager = toolHandler.getOrCreatePackageManager()
                            val isAvailable = packageManager.getAvailablePackages().containsKey(packName)
                            if (isAvailable) "工具包 '$packName' 已导入但未在当前会话中激活。请先使用 'use_package' 命令来激活它。"
                            else "工具包 '$packName' 不存在。"
                        }
                        else -> "工具 '${toolName}' 不可用或不存在。如果这是一个工具包中的工具，请使用 'packName:toolName' 格式调用。"
                    }

                    val notAvailableContent = ConversationMarkupManager.createToolNotAvailableError(toolName, errorMessage)
                    context.roundManager.appendContent(notAvailableContent)
                    collector.emit(notAvailableContent)

                    executionResults.add(ToolResult(toolName = toolName, success = false, result = StringResultData(""), error = errorMessage))
                    continue
                }

                val collectedResults = mutableListOf<ToolResult>()
                ToolExecutionManager.executeToolSafely(invocation, executor).collect { result ->
                    collectedResults.add(result)
                    val toolResultStatusContent = ConversationMarkupManager.formatToolResultForMessage(result)
                    context.roundManager.appendContent(toolResultStatusContent)
                    collector.emit(toolResultStatusContent)
                }

                if (collectedResults.isNotEmpty()) {
                    val lastResult = collectedResults.last()
                    val combinedResultString = collectedResults.joinToString("\n") { res ->
                        if (res.success) res.result.toString() else "Error in step: ${res.error ?: "Unknown error"}"
                    }
                    executionResults.add(ToolResult(
                            toolName = invocation.tool.name,
                            success = lastResult.success,
                            result = StringResultData(combinedResultString),
                            error = lastResult.error
                    ))
                }
            }

            val allToolResults = permissionDeniedResults + executionResults

            if (allToolResults.isNotEmpty()) {
                Log.d(TAG, "所有工具结果收集完毕，准备最终处理。")
                processToolResults(
                        allToolResults,
                        context,
                        functionType,
                        collector,
                        enableThinking,
                        enableMemoryAttachment,
                        onNonFatalError,
                        maxTokens,
                        tokenUsageThreshold,
                        isSubTask
                )
            }
        }

        val invocationId = java.util.UUID.randomUUID().toString()
        toolExecutionJobs[invocationId] = processToolJob

        try {
            processToolJob.join()
        } finally {
            toolExecutionJobs.remove(invocationId)
        }

        Log.d(TAG, "工具调用处理耗时: ${System.currentTimeMillis() - startTime}ms")
    }

    /** Process tool execution result - simplified version without callbacks */
    private suspend fun processToolResults(
            results: List<ToolResult>,
            context: MessageExecutionContext,
            functionType: FunctionType = FunctionType.CHAT,
            collector: StreamCollector<String>,
            enableThinking: Boolean = false,
            enableMemoryAttachment: Boolean = true,
            onNonFatalError: suspend (error: String) -> Unit,
            maxTokens: Int,
            tokenUsageThreshold: Double,
            isSubTask: Boolean
    ) {
        val startTime = System.currentTimeMillis()
        val toolNames = results.joinToString(", ") { it.toolName }
        Log.d(TAG, "开始处理工具结果: $toolNames, 成功: ${results.all { it.success }}")

        // Add transition state
        if (!isSubTask) {
            withContext(Dispatchers.Main) {
                _inputProcessingState.value = InputProcessingState.ProcessingToolResult(toolNames)
            }
        }

        // Check if conversation is still active
        if (!context.isConversationActive.get()) {
            return
        }

        // Tool result processing and subsequent AI request
        val toolResultMessage = results.joinToString("\n") {
            ConversationMarkupManager.formatToolResultForMessage(it)
        }

        // Add tool result to conversation history
        context.conversationHistory.add(Pair("tool", toolResultMessage))

        // Get current conversation history is now just the context's history
        val currentChatHistory = context.conversationHistory

        // Append tool result to current round
        context.roundManager.appendContent(toolResultMessage)

        // Start new round - ensure tool execution response will be shown in a new message
        context.roundManager.startNewRound()
        context.streamBuffer.clear() // Clear buffer to ensure a new message will be created

        // Clearly show we're preparing to send tool result to AI
        if (!isSubTask) {
            withContext(Dispatchers.Main) {
                _inputProcessingState.value = InputProcessingState.ProcessingToolResult(toolNames)
            }
        }

        // Add short delay to make state change more visible
        delay(300)

        // Get all model parameters from preferences (with enabled state)
        val modelParameters = multiServiceManager.getModelParametersForFunction(functionType)

        // 获取对应功能类型的AIService实例
        val serviceForFunction = getAIServiceForFunction(functionType)

        // After a tool call, check if token usage exceeds the threshold
        if (maxTokens > 0) {
            // 精确计算下一次调用（空消息，只依赖历史）将产生的token
            val currentTokens = serviceForFunction.calculateInputTokens("", currentChatHistory)
            val usageRatio = currentTokens.toDouble() / maxTokens.toDouble()

            if (usageRatio >= tokenUsageThreshold) {
                Log.w(TAG, "Token usage ($usageRatio) exceeds threshold ($tokenUsageThreshold) after tool call. Terminating turn.")

                val warningMessage = ConversationMarkupManager.createWarningStatus("已达到单次对话Token限制。请继续对话以触发历史压缩")
                collector.emit(warningMessage)
                context.roundManager.appendContent(warningMessage)
                context.conversationHistory.add(Pair("assistant", warningMessage))

                context.isConversationActive.set(false)
                if (!isSubTask) {
                    withContext(Dispatchers.Main) {
                        _inputProcessingState.value = InputProcessingState.Completed
                    }
                    stopAiService()
                }
                return // Stop further processing
            }
        }

        // 清空之前的单次请求token计数
        _perRequestTokenCounts.value = null

        // 使用新的Stream API处理工具执行结果
        withContext(Dispatchers.IO) {
            try {
                // 发送消息并获取响应流
                val aiStartTime = System.currentTimeMillis()
                val stream =
                        serviceForFunction.sendMessage(
                                message = toolResultMessage,
                                chatHistory = currentChatHistory,
                                modelParameters = modelParameters,
                                enableThinking = enableThinking,
                                onTokensUpdated = { input, output ->
                                    _perRequestTokenCounts.value = Pair(input, output)
                                },
                                onNonFatalError = onNonFatalError
                        )

                // 更新状态为接收中
                if (!isSubTask) {
                    withContext(Dispatchers.Main) {
                        _inputProcessingState.value =
                                InputProcessingState.Receiving("正在接收工具执行后的AI响应...")
                    }
                }

                // 处理流
                var chunkCount = 0
                var totalChars = 0
                var lastLogTime = System.currentTimeMillis()

                stream.collect { content ->
                    // 更新streamBuffer
                    context.streamBuffer.append(content)

                    // 更新内容到轮次管理器
                    context.roundManager.updateContent(context.streamBuffer.toString())

                    // 累计统计
                    chunkCount++
                    totalChars += content.length

                    // 定期记录日志
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastLogTime > 5000) { // 每5秒记录一次
                        lastLogTime = currentTime
                    }

                    // 通过收集器将内容发射出去，让UI可以接收到
                    collector.emit(content)
                }

                // Update accumulated token counts and persist them
                val inputTokens = serviceForFunction.inputTokenCount
                val outputTokens = serviceForFunction.outputTokenCount
                accumulatedInputTokenCount += inputTokens
                accumulatedOutputTokenCount += outputTokens
                apiPreferences.updateTokensForFunction(functionType, inputTokens, outputTokens)

                Log.d(
                        TAG,
                        "Token count updated after tool result for $functionType. Input: $inputTokens, Output: $outputTokens."
                )

                val processingTime = System.currentTimeMillis() - aiStartTime
                Log.d(TAG, "工具结果AI处理完成，收到 $totalChars 字符，耗时: ${processingTime}ms")

                // 流处理完成，处理完成逻辑
                processStreamCompletion(context, functionType, collector, enableThinking, enableMemoryAttachment, onNonFatalError, maxTokens, tokenUsageThreshold, isSubTask)
            } catch (e: Exception) {
                Log.e(TAG, "处理工具执行结果时出错", e)
                withContext(Dispatchers.Main) {
                    _inputProcessingState.value =
                            InputProcessingState.Error("处理工具执行结果失败: ${e.message}")
                }
            }
        }
        Log.d(TAG, "工具结果处理总耗时: ${System.currentTimeMillis() - startTime}ms")
    }

    // Expose base AIService for token counting
    fun getBaseAIService(): AIService = aiService

    /**
     * Get the current input token count from the last API call
     * @return The number of input tokens used in the most recent request
     */
    fun getCurrentInputTokenCount(): Int {
        return accumulatedInputTokenCount
    }

    /**
     * Get the current output token count from the last API call
     * @return The number of output tokens generated in the most recent response
     */
    fun getCurrentOutputTokenCount(): Int {
        return accumulatedOutputTokenCount
    }

    /** Reset token counters to zero Use this when starting a new conversation */
    fun resetTokenCounters() {
        Companion.resetTokenCounters(context)
    }

    /**
     * 重置指定功能类型或所有功能类型的token计数器
     * @param functionType 功能类型，如果为null则重置所有功能类型
     */
    suspend fun resetTokenCountersForFunction(functionType: FunctionType? = null) {
        Companion.resetTokenCountersForFunction(context, functionType)
    }

    /**
     * 生成对话总结
     * @param messages 要总结的消息列表
     * @return 生成的总结文本
     */
    suspend fun generateSummary(messages: List<Pair<String, String>>): String {
        return generateSummary(messages, null)
    }

    /**
     * 生成对话总结，并且包含上一次的总结内容
     * @param messages 要总结的消息列表
     * @param previousSummary 上一次的总结内容，可以为null
     * @return 生成的总结文本
     */
    suspend fun generateSummary(
            messages: List<Pair<String, String>>,
            previousSummary: String?
    ): String {
        // 调用ConversationService中的方法
        return conversationService.generateSummary(messages, previousSummary, multiServiceManager)
    }

    /**
     * 获取指定功能类型的当前输入token计数
     * @param functionType 功能类型
     * @return 输入token计数
     */
    suspend fun getCurrentInputTokenCountForFunction(functionType: FunctionType): Int {
        return Companion.getCurrentInputTokenCountForFunction(context, functionType)
    }

    /**
     * 获取指定功能类型的当前输出token计数
     * @param functionType 功能类型
     * @return 输出token计数
     */
    suspend fun getCurrentOutputTokenCountForFunction(functionType: FunctionType): Int {
        return Companion.getCurrentOutputTokenCountForFunction(context, functionType)
    }

    /** Prepare the conversation history with system prompt */
    private suspend fun prepareConversationHistory(
            chatHistory: List<Pair<String, String>>,
            processedInput: String,
            workspacePath: String?,
            promptFunctionType: PromptFunctionType,
            thinkingGuidance: Boolean,
            customSystemPromptTemplate: String? = null
    ): List<Pair<String, String>> {
        return conversationService.prepareConversationHistory(
                chatHistory,
                processedInput,
                workspacePath,
                packageManager,
                promptFunctionType,
                thinkingGuidance,
                customSystemPromptTemplate
        )
    }

    /** Cancel the current conversation */
    fun cancelConversation() {
        // Set conversation inactive
        // isConversationActive.set(false) // This is now per-context, can't set a global one

        // Cancel underlying AIService streaming
        aiService.cancelStreaming()

        // Cancel all tool executions
        cancelAllToolExecutions()

        // Clean up current conversation content
        // roundManager.clearContent() // This is now per-context, can't clear a global one
        Log.d(TAG, "Conversation canceled")

        // Reset input processing state
        _inputProcessingState.value = InputProcessingState.Idle

        // Reset per-request token counts
        _perRequestTokenCounts.value = null

        // Clear callback references
        currentResponseCallback = null
        currentCompleteCallback = null

        // 停止AI服务并关闭屏幕常亮
        stopAiService()

        Log.d(TAG, "Conversation cancellation complete")
    }

    /** Cancel all tool executions */
    private fun cancelAllToolExecutions() {
        toolProcessingScope.coroutineContext.cancelChildren()
    }

    // --- Service Lifecycle Management ---

    /** 启动前台服务以保持应用活跃 */
    private fun startAiService() {
        if (!AIForegroundService.isRunning.get()) {
            Log.d(TAG, "请求启动AI前台服务...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            Log.d(TAG, "AI前台服务已在运行，无需重复启动。")
        }
        
        // 使用管理器来应用屏幕常亮设置
        ActivityLifecycleManager.checkAndApplyKeepScreenOn(true)
    }

    /** 停止前台服务 */
    private fun stopAiService() {
        if (AIForegroundService.isRunning.get()) {
            Log.d(TAG, "请求停止AI前台服务...")
            context.stopService(serviceIntent)
        } else {
            Log.d(TAG, "AI前台服务未在运行，无需重复停止。")
        }
        
        // 使用管理器来恢复屏幕常亮设置
        ActivityLifecycleManager.checkAndApplyKeepScreenOn(false)
    }

    /**
     * 处理文件绑定操作（实例方法）
     * @param originalContent 原始文件内容
     * @param aiGeneratedCode AI生成的代码（包含"//existing code"标记）
     * @return 混合后的文件内容
     */
    suspend fun applyFileBinding(
            originalContent: String,
            aiGeneratedCode: String
    ): Pair<String, String> {
        return fileBindingService.processFileBinding(
                originalContent,
                aiGeneratedCode,
                multiServiceManager
        )
    }

    /**
     * 翻译文本功能
     * @param text 要翻译的文本
     * @return 翻译后的文本
     */
    suspend fun translateText(text: String): String {
        return conversationService.translateText(text, multiServiceManager)
    }




}
