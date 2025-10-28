package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 工作流模型
 * 代表一个完整的自动化工作流程
 */
@Serializable
data class Workflow(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var description: String = "",
    var nodes: List<WorkflowNode> = emptyList(),
    var connections: List<WorkflowNodeConnection> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var enabled: Boolean = true,
    // 执行统计信息
    var lastExecutionTime: Long? = null,  // 上次执行时间
    var lastExecutionStatus: ExecutionStatus? = null,  // 上次执行状态
    var totalExecutions: Int = 0,  // 总执行次数
    var successfulExecutions: Int = 0,  // 成功执行次数
    var failedExecutions: Int = 0  // 失败执行次数
)

/**
 * 执行状态枚举
 */
@Serializable
enum class ExecutionStatus {
    SUCCESS,  // 成功
    FAILED,   // 失败
    RUNNING   // 运行中
}

/**
 * 工作流节点基类
 */
@Serializable
sealed class WorkflowNode {
    abstract val id: String
    abstract val type: String
    abstract var name: String
    abstract var description: String
    abstract var position: NodePosition
}

/**
 * 触发节点
 * 定义工作流的触发条件（暂时为占位符）
 */
@Serializable
data class TriggerNode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = "trigger",
    override var name: String = "触发器",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    var triggerType: String = "manual", // manual, schedule, event
    var triggerConfig: Map<String, String> = emptyMap() // 触发器配置参数
) : WorkflowNode()

/**
 * 执行节点
 * 定义工作流的执行动作
 */
@Serializable
data class ExecuteNode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = "execute",
    override var name: String = "执行动作",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    var actionType: String = "", // 工具名称，如 "http_request", "list_files", "click_element"
    var actionConfig: Map<String, ParameterValue> = emptyMap(), // 工具参数：支持静态值或节点引用
    var jsCode: String? = null // 可选：直接执行 JavaScript 代码
) : WorkflowNode()

/**
 * 参数值类型
 * 支持静态值或引用其他节点的输出
 */
@Serializable
sealed class ParameterValue {
    @Serializable
    data class StaticValue(val value: String) : ParameterValue()
    
    @Serializable
    data class NodeReference(val nodeId: String) : ParameterValue()
}

/**
 * 节点位置信息（用于将来的可视化编辑器）
 */
@Serializable
data class NodePosition(
    var x: Float = 0f,
    var y: Float = 0f
)

/**
 * 工作流节点连接
 * 定义节点之间的连接关系
 */
@Serializable
data class WorkflowNodeConnection(
    val id: String = UUID.randomUUID().toString(),
    val sourceNodeId: String,
    val targetNodeId: String,
    var condition: String? = null // 连接条件（可选）
)

