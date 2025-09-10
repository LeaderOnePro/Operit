package com.ai.assistance.operit.api.chat.plan

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import com.google.gson.Gson

/**
 * 计划模式管理器，负责协调整个深度搜索模式的执行
 */
class PlanModeManager(
    private val context: Context,
    private val enhancedAIService: EnhancedAIService
) {
    companion object {
        private const val TAG = "PlanModeManager"
        
        // 用于生成执行计划的系统提示词
        private const val PLAN_GENERATION_PROMPT = """
你是一个任务规划专家。用户将向你描述一个复杂的任务或问题，你需要将其分解为多个可以并发或顺序执行的子任务。

请按照以下JSON格式返回执行计划：

```json
{
  "tasks": [
    {
      "id": "task_1",
      "name": "任务描述",
      "instruction": "具体的执行指令，这将被发送给AI执行",
      "dependencies": [],
      "type": "chat"
    },
    {
      "id": "task_2", 
      "name": "任务描述",
      "instruction": "具体的执行指令",
      "dependencies": ["task_1"],
      "type": "chat"
    }
  ],
  "final_summary_instruction": "根据所有子任务的结果，提供最终的完整回答"
}
```

规划原则：
1. 将复杂任务分解为3-6个相对独立的子任务
2. 确保每个子任务都有明确的执行指令
3. 合理设置任务间的依赖关系，优先支持并发执行
4. 所有任务类型都设为"chat"
5. 每个instruction应该是一个完整的、可以独立执行的指令
6. 最终汇总指令应该能够整合所有子任务的结果

请分析用户的请求并生成相应的执行计划。
        """
    }
    
    private val taskExecutor = TaskExecutor(context, enhancedAIService)
    
    /**
     * 执行深度搜索模式
     * @param userMessage 用户消息
     * @param chatHistory 聊天历史
     * @param workspacePath 工作区路径
     * @param maxTokens 最大 token 数
     * @param tokenUsageThreshold token 使用阈值
     * @param onNonFatalError 非致命错误回调
     * @return 流式返回整个执行过程
     */
    suspend fun executeDeepSearchMode(
        userMessage: String,
        chatHistory: List<Pair<String, String>>,
        workspacePath: String?,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        onNonFatalError: suspend (error: String) -> Unit
    ): Stream<String> = stream {
        
        try {
            emit("<log>🧠 启动深度搜索模式...</log>\n")
            emit("<log>📊 正在分析您的请求并生成执行计划...</log>\n")
            
            // 第一步：生成执行计划
            val executionGraph = generateExecutionPlan(
                userMessage, 
                chatHistory, 
                workspacePath, 
                maxTokens, 
                tokenUsageThreshold, 
                onNonFatalError
            )
            
            if (executionGraph == null) {
                emit("<error>❌ 无法生成有效的执行计划，切换回普通模式</error>\n")
                return@stream
            }
            
            emit("<plan>\n")
            
            val gson = Gson()
            val planJson = gson.toJson(executionGraph)
            emit("<graph><![CDATA[$planJson]]></graph>\n")

            // emit("\n" + "=".repeat(50) + "\n")
            
            // 第二步：执行计划
            val (executionStream, summaryDeferred) = taskExecutor.executeGraph(
                executionGraph,
                userMessage,
                chatHistory,
                workspacePath,
                maxTokens,
                tokenUsageThreshold,
                onNonFatalError
            )
            
            // 转发执行过程的所有输出
            executionStream.collect { message ->
                emit(message)
            }
            
            emit("</plan>\n")
            
            // 等待并输出最终总结
            val summary = summaryDeferred.await()
            if (summary != null) {
                emit(summary)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "深度搜索模式执行失败", e)
            emit("<error>❌ 深度搜索模式执行失败: ${e.message}</error>\n")
        }
    }
    
    /**
     * 生成执行计划
     */
    private suspend fun generateExecutionPlan(
        userMessage: String,
        chatHistory: List<Pair<String, String>>,
        workspacePath: String?,
        maxTokens: Int,
        tokenUsageThreshold: Double,
        onNonFatalError: suspend (error: String) -> Unit
    ): ExecutionGraph? {
        try {
            // 构建规划请求
            val planningRequest = buildPlanningRequest(userMessage)
            
            // 调用 AI 生成计划
            val planningStream = enhancedAIService.sendMessage(
                message = planningRequest,
                chatHistory = emptyList(), // 规划阶段使用空历史，专注于当前任务
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
            
            // 收集规划结果
            val planBuilder = StringBuilder()
            planningStream.collect { chunk ->
                planBuilder.append(chunk)
            }
            
            val planResponse = planBuilder.toString().trim()
            Log.d(TAG, "AI生成的执行计划: $planResponse")
            
            // 解析执行计划
            val executionGraph = PlanParser.parseExecutionGraph(planResponse)
            if (executionGraph == null) {
                Log.e(TAG, "解析执行计划失败")
                return null
            }
            
            // 验证执行计划
            val (isValid, errorMessage) = PlanParser.validateExecutionGraph(executionGraph)
            if (!isValid) {
                Log.e(TAG, "执行计划验证失败: $errorMessage")
                return null
            }
            
            Log.d(TAG, "执行计划生成并验证成功，包含 ${executionGraph.tasks.size} 个任务")
            return executionGraph
            
        } catch (e: Exception) {
            Log.e(TAG, "生成执行计划时发生错误", e)
            return null
        }
    }
    
    /**
     * 构建规划请求
     */
    private fun buildPlanningRequest(userMessage: String): String {
        return """
$PLAN_GENERATION_PROMPT

用户请求：
$userMessage

请为这个请求生成详细的执行计划。
        """.trim()
    }
    
    /**
     * 取消当前执行
     */
    fun cancel() {
        taskExecutor.cancelAllTasks()
    }
    
    /**
     * 检查消息是否适合使用深度搜索模式
     * 这是一个简单的启发式检查，可以根据需要进行优化
     */
    fun shouldUseDeepSearchMode(message: String): Boolean {
        val messageLength = message.length
        val complexityIndicators = listOf(
            "分析", "比较", "研究", "调查", "总结", "评估", "计划", "设计", "开发",
            "详细", "全面", "深入", "系统", "综合", "多角度", "步骤", "方案",
            "分几个", "多个方面", "详细解释", "具体分析", "如何实现", "实施方案"
        )
        
        val hasComplexityIndicators = complexityIndicators.any { indicator ->
            message.contains(indicator, ignoreCase = true)
        }
        
        // 消息长度超过50字符或包含复杂性指标
        return messageLength > 50 || hasComplexityIndicators
    }
} 