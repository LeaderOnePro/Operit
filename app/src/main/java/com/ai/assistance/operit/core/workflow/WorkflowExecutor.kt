package com.ai.assistance.operit.core.workflow

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ExecuteNode
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.TriggerNode
import com.ai.assistance.operit.data.model.Workflow
import com.ai.assistance.operit.data.model.WorkflowNode
import com.ai.assistance.operit.data.model.WorkflowNodeConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.LinkedList
import java.util.Queue

/**
 * 节点执行状态
 */
sealed class NodeExecutionState {
    object Pending : NodeExecutionState()
    object Running : NodeExecutionState()
    data class Success(val result: String) : NodeExecutionState()
    data class Failed(val error: String) : NodeExecutionState()
}

/**
 * 工作流执行结果
 */
data class WorkflowExecutionResult(
    val workflowId: String,
    val success: Boolean,
    val nodeResults: Map<String, NodeExecutionState>,
    val message: String,
    val executionTime: Long = System.currentTimeMillis()
)

/**
 * 工作流执行器
 * 负责解析和执行工作流
 */
class WorkflowExecutor(private val context: Context) {
    
    private val toolHandler = AIToolHandler.getInstance(context)
    
    companion object {
        private const val TAG = "WorkflowExecutor"
    }
    
    /**
     * 执行工作流
     * @param workflow 要执行的工作流
     * @param triggerNodeId 指定要触发的节点ID，如果为null则触发所有触发节点
     * @param onNodeStateChange 节点状态变化回调
     * @return 工作流执行结果
     */
    suspend fun executeWorkflow(
        workflow: Workflow,
        triggerNodeId: String? = null,
        onNodeStateChange: (nodeId: String, state: NodeExecutionState) -> Unit
    ): WorkflowExecutionResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "开始执行工作流: ${workflow.name} (${workflow.id})")
        
        val nodeResults = mutableMapOf<String, NodeExecutionState>()
        
        try {
            // 1. 找到所有触发节点作为入口
            val allTriggerNodes = workflow.nodes.filterIsInstance<TriggerNode>()
            
            if (allTriggerNodes.isEmpty()) {
                Log.w(TAG, "工作流没有触发节点")
                return@withContext WorkflowExecutionResult(
                    workflowId = workflow.id,
                    success = false,
                    nodeResults = nodeResults,
                    message = "工作流没有触发节点，无法执行"
                )
            }
            
            // 2. 根据 triggerNodeId 决定要执行哪些触发节点
            val triggerNodes = if (triggerNodeId != null) {
                // 如果指定了触发节点ID（通常是定时任务），只执行该触发节点
                val specificNode = allTriggerNodes.find { it.id == triggerNodeId }
                if (specificNode == null) {
                    Log.w(TAG, "指定的触发节点不存在: $triggerNodeId")
                    return@withContext WorkflowExecutionResult(
                        workflowId = workflow.id,
                        success = false,
                        nodeResults = nodeResults,
                        message = "指定的触发节点不存在: $triggerNodeId"
                    )
                }
                Log.d(TAG, "定时触发: 只执行指定触发节点 ${specificNode.name}")
                listOf(specificNode)
            } else {
                // 如果没有指定触发节点ID（通常是手动触发），执行所有手动触发类型的节点
                val manualTriggers = allTriggerNodes.filter { it.triggerType == "manual" }
                if (manualTriggers.isEmpty()) {
                    Log.w(TAG, "没有手动触发类型的触发节点")
                    return@withContext WorkflowExecutionResult(
                        workflowId = workflow.id,
                        success = false,
                        nodeResults = nodeResults,
                        message = "没有手动触发类型的触发节点"
                    )
                }
                Log.d(TAG, "手动触发: 执行所有手动触发类型的节点")
                manualTriggers
            }
            
            Log.d(TAG, "将执行 ${triggerNodes.size} 个触发节点: ${triggerNodes.joinToString { it.name }}")
            
            // 3. 构建节点邻接表（用于遍历）
            val adjacencyList = buildAdjacencyList(workflow.connections)
            
            // 4. 从每个触发节点开始执行
            for (triggerNode in triggerNodes) {
                Log.d(TAG, "从触发节点开始: ${triggerNode.name} (${triggerNode.id})")
                
                // 标记触发节点为成功（触发节点本身不需要执行）
                nodeResults[triggerNode.id] = NodeExecutionState.Success("触发节点")
                onNodeStateChange(triggerNode.id, NodeExecutionState.Success("触发节点"))
                
                // 使用 BFS 遍历执行后续节点
                val executionResult = executeBFS(
                    startNodeId = triggerNode.id,
                    workflow = workflow,
                    adjacencyList = adjacencyList,
                    nodeResults = nodeResults,
                    onNodeStateChange = onNodeStateChange
                )
                
                // 如果执行失败，停止整个工作流
                if (!executionResult) {
                    return@withContext WorkflowExecutionResult(
                        workflowId = workflow.id,
                        success = false,
                        nodeResults = nodeResults,
                        message = "工作流执行失败"
                    )
                }
            }
            
            Log.d(TAG, "工作流执行完成: ${workflow.name}")
            
            return@withContext WorkflowExecutionResult(
                workflowId = workflow.id,
                success = true,
                nodeResults = nodeResults,
                message = "工作流执行成功"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "工作流执行异常", e)
            return@withContext WorkflowExecutionResult(
                workflowId = workflow.id,
                success = false,
                nodeResults = nodeResults,
                message = "工作流执行异常: ${e.message}"
            )
        }
    }
    
    /**
     * 构建邻接表
     */
    private fun buildAdjacencyList(connections: List<WorkflowNodeConnection>): Map<String, List<String>> {
        val adjacencyList = mutableMapOf<String, MutableList<String>>()
        
        for (connection in connections) {
            adjacencyList.getOrPut(connection.sourceNodeId) { mutableListOf() }
                .add(connection.targetNodeId)
        }
        
        return adjacencyList
    }
    
    /**
     * 使用 BFS 遍历执行节点
     * @return 是否执行成功
     */
    private suspend fun executeBFS(
        startNodeId: String,
        workflow: Workflow,
        adjacencyList: Map<String, List<String>>,
        nodeResults: MutableMap<String, NodeExecutionState>,
        onNodeStateChange: (nodeId: String, state: NodeExecutionState) -> Unit
    ): Boolean {
        val queue: Queue<String> = LinkedList()
        val visited = mutableSetOf<String>()
        
        // 将起始节点的所有后继节点加入队列
        adjacencyList[startNodeId]?.forEach { nextNodeId ->
            queue.offer(nextNodeId)
        }
        
        while (queue.isNotEmpty()) {
            val currentNodeId = queue.poll()
            
            // 避免在当前BFS中重复执行
            if (currentNodeId in visited) {
                continue
            }
            
            // 检查节点是否已经在全局被执行过（被其他触发节点执行）
            if (nodeResults.containsKey(currentNodeId)) {
                Log.d(TAG, "节点已被执行，跳过: $currentNodeId")
                visited.add(currentNodeId)
                continue
            }
            
            visited.add(currentNodeId)
            
            // 查找节点
            val node = workflow.nodes.find { it.id == currentNodeId }
            if (node == null) {
                Log.w(TAG, "节点不存在: $currentNodeId")
                continue
            }
            
            Log.d(TAG, "执行节点: ${node.name} (${node.id})")
            
            // 执行节点
            val executionSuccess = executeNode(node, nodeResults, onNodeStateChange)
            
            // 如果执行失败，停止整个流程
            if (!executionSuccess) {
                Log.e(TAG, "节点执行失败: ${node.name}")
                return false
            }
            
            // 将后继节点加入队列
            adjacencyList[currentNodeId]?.forEach { nextNodeId ->
                if (nextNodeId !in visited) {
                    queue.offer(nextNodeId)
                }
            }
        }
        
        return true
    }
    
    /**
     * 执行单个节点
     * @return 是否执行成功
     */
    private suspend fun executeNode(
        node: WorkflowNode,
        nodeResults: MutableMap<String, NodeExecutionState>,
        onNodeStateChange: (nodeId: String, state: NodeExecutionState) -> Unit
    ): Boolean {
        // 只执行 ExecuteNode
        if (node !is ExecuteNode) {
            Log.d(TAG, "跳过非执行节点: ${node.name}")
            nodeResults[node.id] = NodeExecutionState.Success("跳过")
            onNodeStateChange(node.id, NodeExecutionState.Success("跳过"))
            return true
        }
        
        // 标记为执行中
        nodeResults[node.id] = NodeExecutionState.Running
        onNodeStateChange(node.id, NodeExecutionState.Running)
        
        try {
            // 检查是否有 actionType
            if (node.actionType.isBlank()) {
                val errorMsg = "节点 ${node.name} 没有配置 actionType"
                Log.w(TAG, errorMsg)
                nodeResults[node.id] = NodeExecutionState.Failed(errorMsg)
                onNodeStateChange(node.id, NodeExecutionState.Failed(errorMsg))
                return false
            }
            
            // 构造工具参数
            val parameters = node.actionConfig.map { (key, value) ->
                ToolParameter(name = key, value = value)
            }
            
            // 构造 AITool
            val tool = AITool(
                name = node.actionType,
                parameters = parameters
            )
            
            Log.d(TAG, "调用工具: ${tool.name}, 参数: ${parameters.size} 个")
            
            // 执行工具
            val result = toolHandler.executeTool(tool)
            
            if (result.success) {
                val resultMessage = result.result.toString()
                Log.d(TAG, "节点执行成功: ${node.name}, 结果: $resultMessage")
                nodeResults[node.id] = NodeExecutionState.Success(resultMessage)
                onNodeStateChange(node.id, NodeExecutionState.Success(resultMessage))
                return true
            } else {
                val errorMsg = result.error ?: "未知错误"
                Log.e(TAG, "节点执行失败: ${node.name}, 错误: $errorMsg")
                nodeResults[node.id] = NodeExecutionState.Failed(errorMsg)
                onNodeStateChange(node.id, NodeExecutionState.Failed(errorMsg))
                return false
            }
            
        } catch (e: Exception) {
            val errorMsg = "节点执行异常: ${e.message}"
            Log.e(TAG, "节点执行异常: ${node.name}", e)
            nodeResults[node.id] = NodeExecutionState.Failed(errorMsg)
            onNodeStateChange(node.id, NodeExecutionState.Failed(errorMsg))
            return false
        }
    }
}

