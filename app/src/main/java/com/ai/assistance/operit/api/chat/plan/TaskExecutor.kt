package com.ai.assistance.operit.api.chat.plan

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * 任务执行器，负责执行计划图中的任务
 */
class TaskExecutor(
    private val context: Context,
    private val enhancedAIService: EnhancedAIService
) {
    companion object {
        private const val TAG = "TaskExecutor"
    }
    
    // 任务结果存储
    private val taskResults = ConcurrentHashMap<String, String>()
    // 任务状态锁
    private val taskMutex = Mutex()
    // 正在执行的任务
    private val runningTasks = ConcurrentHashMap<String, Job>()
    
    /**
     * 执行整个执行图
     * @param graph 执行图
     * @param originalMessage 原始用户消息
     * @param chatHistory 聊天历史
     * @param workspacePath 工作区路径
     * @param maxTokens 最大 token 数
     * @param tokenUsageThreshold token 使用阈值
     * @param onNonFatalError 非致命错误回调
     * @return 流式返回执行过程和最终结果
     */
    suspend fun executeSubtasks(
        graph: ExecutionGraph,
        originalMessage: String,
        chatHistory: List<Pair<String, String>>,
        workspacePath: String?,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        onNonFatalError: suspend (error: String) -> Unit
    ): Stream<String> = stream {
        try {
            taskResults.clear()
            runningTasks.clear()

            val (isValid, errorMessage) = PlanParser.validateExecutionGraph(graph)
            if (!isValid) {
                emit("<error>❌ 执行图验证失败: $errorMessage</error>\n")
                return@stream
            }

            val sortedTasks = PlanParser.topologicalSort(graph)
            if (sortedTasks.isEmpty()) {
                emit("<error>❌ 无法对任务进行拓扑排序，可能存在循环依赖</error>\n")
                return@stream
            }

            emit("<log>📋 开始执行计划，共 ${sortedTasks.size} 个任务</log>\n")

            coroutineScope {
                val job = SupervisorJob()
                val scope = CoroutineScope(Dispatchers.IO + job)

                try {
                    executeTasksInOrder(scope, sortedTasks, originalMessage, chatHistory, workspacePath, maxTokens, tokenUsageThreshold, onNonFatalError) { message ->
                        emit(message)
                    }
                } finally {
                    job.cancel() // 只取消与子任务相关的 Job
                    runningTasks.clear()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行子任务时发生错误", e)
            emit("<error>❌ 执行子任务时发生错误: ${e.message}</error>\n")
        }
    }

    suspend fun summarize(
        graph: ExecutionGraph,
        originalMessage: String,
        chatHistory: List<Pair<String, String>>,
        workspacePath: String?,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        onNonFatalError: suspend (error: String) -> Unit
    ): Stream<String> = stream {
        try {
            val summaryStream = executeFinalSummary(
                graph.finalSummaryInstruction,
                originalMessage,
                chatHistory,
                workspacePath,
                maxTokens,
                tokenUsageThreshold,
                onNonFatalError
            )

            summaryStream.collect { chunk ->
                emit(chunk)
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行最终汇总时发生错误", e)
        }
    }

    /**
     * 按依赖关系顺序执行任务
     */
    private suspend fun executeTasksInOrder(
        scope: CoroutineScope,
        sortedTasks: List<TaskNode>,
        originalMessage: String,
        chatHistory: List<Pair<String, String>>,
        workspacePath: String?,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        onNonFatalError: suspend (error: String) -> Unit,
        onMessage: suspend (String) -> Unit
    ) {
        val completedTasks = mutableSetOf<String>()
        val taskMap = sortedTasks.associateBy { it.id }
        
        // 使用队列来管理待执行的任务
        val pendingTasks = sortedTasks.toMutableList()
        
        while (pendingTasks.isNotEmpty()) {
            // 找到所有依赖已完成的任务
            val readyTasks = pendingTasks.filter { task ->
                task.dependencies.all { depId -> completedTasks.contains(depId) }
            }
            
            if (readyTasks.isEmpty()) {
                // 如果没有就绪的任务，说明存在问题
                onMessage("<error>❌ 无法找到可执行的任务，可能存在依赖问题</error>\n")
                break
            }
            
            // 并发执行所有就绪的任务
            val jobs = readyTasks.map { task ->
                scope.async {
                    executeTask(task, originalMessage, chatHistory, workspacePath, maxTokens, tokenUsageThreshold, onNonFatalError, onMessage)
                }
            }
            
            // 等待所有任务完成
            jobs.awaitAll()
            
            // 标记任务为已完成并从待执行列表中移除
            readyTasks.forEach { task ->
                completedTasks.add(task.id)
                pendingTasks.remove(task)
            }
        }
    }
    
    /**
     * 执行单个任务
     */
    private suspend fun executeTask(
        task: TaskNode,
        originalMessage: String,
        chatHistory: List<Pair<String, String>>,
        workspacePath: String?,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        onNonFatalError: suspend (error: String) -> Unit,
        onMessage: suspend (String) -> Unit
    ) {
        try {
            onMessage("""<update id="${task.id}" status="IN_PROGRESS"/>""" + "\n")
            
            // 构建任务的上下文信息
            val contextInfo = buildTaskContext(task, originalMessage)
            
            // 构建任务的完整指令
            val fullInstruction = buildFullInstruction(task, contextInfo)
            
            val resultBuilder = StringBuilder()
            
            // 调用 EnhancedAIService 执行任务
            val stream = enhancedAIService.sendMessage(
                message = fullInstruction,
                chatHistory = chatHistory,
                workspacePath = workspacePath,
                functionType = FunctionType.CHAT,
                promptFunctionType = PromptFunctionType.CHAT,
                enableThinking = false,
                thinkingGuidance = false,
                enableMemoryAttachment = false,
                maxTokens = maxTokens,
                tokenUsageThreshold = tokenUsageThreshold,
                onNonFatalError = onNonFatalError,
                customSystemPromptTemplate = com.ai.assistance.operit.core.config.SystemPromptConfig.SUBTASK_AGENT_PROMPT_TEMPLATE
            )
            
            // 收集流式响应
            stream.collect { chunk ->
                resultBuilder.append(chunk)
                // 可以选择实时输出任务进度
                // onMessage(chunk)
            }
            
            val result = resultBuilder.toString().trim()
            
            // 存储任务结果
            taskMutex.withLock {
                taskResults[task.id] = result
            }
            
            onMessage("""<update id="${task.id}" status="COMPLETED"/>""" + "\n")
            
        } catch (e: Exception) {
            Log.e(TAG, "执行任务 ${task.id} 时发生错误", e)
            val errorMessage = e.message ?: "Unknown error"
            val escapedError = errorMessage.replace("\"", "&quot;")
            onMessage("""<update id="${task.id}" status="FAILED" error="$escapedError"/>""" + "\n")
            
            // 即使失败也要存储结果，避免阻塞其他任务
            taskMutex.withLock {
                taskResults[task.id] = "任务执行失败: ${e.message}"
            }
        }
    }
    
    /**
     * 构建任务上下文信息
     */
    private suspend fun buildTaskContext(task: TaskNode, originalMessage: String): String {
        val contextBuilder = StringBuilder()
        
        contextBuilder.appendLine("原始用户请求: $originalMessage")
        contextBuilder.appendLine("当前任务: ${task.name}")
        
        // 如果有依赖任务，添加其结果作为上下文
        if (task.dependencies.isNotEmpty()) {
            contextBuilder.appendLine("依赖任务结果:")
            taskMutex.withLock {
                task.dependencies.forEach { depId ->
                    val depResult = taskResults[depId]
                    if (depResult != null) {
                        contextBuilder.appendLine("- 任务 $depId 结果: $depResult")
                    }
                }
            }
        }
        
        return contextBuilder.toString()
    }
    
    /**
     * 构建任务的完整指令
     */
    private fun buildFullInstruction(task: TaskNode, contextInfo: String): String {
        return """
$contextInfo

请根据以上上下文信息，执行以下具体任务:
${task.instruction}

请专注于完成这个特定的子任务，你的回答将作为整个计划的一部分。
        """.trim()
    }
    
    /**
     * 执行最终汇总任务
     */
    private suspend fun executeFinalSummary(
        summaryInstruction: String,
        originalMessage: String,
        chatHistory: List<Pair<String, String>>,
        workspacePath: String?,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        onNonFatalError: suspend (error: String) -> Unit
    ): Stream<String> {
        try {
            // 构建汇总上下文
            val summaryContext = buildSummaryContext(originalMessage)
            
            // 构建完整的汇总指令
            val fullSummaryInstruction = """
$summaryContext

请根据以上所有子任务的执行结果，完成以下汇总任务:
$summaryInstruction

请提供一个完整、连贯的最终回答。
            """.trim()

            // 调用 EnhancedAIService 执行汇总并直接返回流
            return enhancedAIService.sendMessage(
                message = fullSummaryInstruction,
                chatHistory = chatHistory,
                workspacePath = workspacePath,
                functionType = FunctionType.CHAT,
                promptFunctionType = PromptFunctionType.CHAT,
                enableThinking = false,
                thinkingGuidance = false,
                enableMemoryAttachment = false,
                maxTokens = maxTokens,
                tokenUsageThreshold = tokenUsageThreshold,
                onNonFatalError = onNonFatalError
            )

        } catch (e: Exception) {
            Log.e(TAG, "执行最终汇总时发生错误", e)
            return stream { emit("汇总执行失败: ${e.message}") }
        }
    }
    
    /**
     * 构建汇总上下文
     */
    private suspend fun buildSummaryContext(originalMessage: String): String {
        val contextBuilder = StringBuilder()
        
        contextBuilder.appendLine("原始用户请求: $originalMessage")
        contextBuilder.appendLine("各子任务执行结果:")
        
        taskMutex.withLock {
            taskResults.forEach { (taskId, result) ->
                contextBuilder.appendLine("- 任务 $taskId: $result")
                contextBuilder.appendLine()
            }
        }
        
        return contextBuilder.toString()
    }
    
    /**
     * 取消所有正在执行的任务
     */
    fun cancelAllTasks() {
        runningTasks.values.forEach { job ->
            job.cancel()
        }
        runningTasks.clear()
        taskResults.clear()
    }
} 