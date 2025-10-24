package com.ai.assistance.operit.ui.features.workflow.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.core.workflow.NodeExecutionState
import com.ai.assistance.operit.data.model.WorkflowNode
import com.ai.assistance.operit.data.model.WorkflowNodeConnection
import kotlin.math.roundToInt

// 画布配置常量
private val CANVAS_WIDTH = 4000.dp
private val CANVAS_HEIGHT = 3000.dp
private val CELL_SIZE = 40.dp  // 更小的网格单元
private val NODE_WIDTH = 120.dp  // 节点宽度
private val NODE_HEIGHT = 80.dp  // 节点高度

/**
 * 网格工作流画布组件
 * 提供固定网格布局，支持节点拖放和连接线绘制
 */
@Composable
fun GridWorkflowCanvas(
    nodes: List<WorkflowNode>,
    connections: List<WorkflowNodeConnection>,
    nodeExecutionStates: Map<String, NodeExecutionState> = emptyMap(),
    onNodePositionChanged: (nodeId: String, x: Float, y: Float) -> Unit,
    onNodeLongPress: (nodeId: String) -> Unit,
    onNodeClick: (nodeId: String) -> Unit,
    modifier: Modifier = Modifier,
    cellSize: Dp = CELL_SIZE
) {
    val density = LocalDensity.current
    val cellSizePx = with(density) { cellSize.toPx() }
    val nodeWidthPx = with(density) { NODE_WIDTH.toPx() }
    val nodeHeightPx = with(density) { NODE_HEIGHT.toPx() }
    val canvasWidthPx = with(density) { CANVAS_WIDTH.toPx() }
    val canvasHeightPx = with(density) { CANVAS_HEIGHT.toPx() }
    
    // 维护节点位置状态（像素坐标）
    val nodePositions = remember(nodes) {
        mutableStateMapOf<String, Offset>().apply {
            nodes.forEach { node ->
                this[node.id] = Offset(node.position.x, node.position.y)
            }
        }
    }

    // 拖动状态
    var draggingNodeId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    
    // 画布缩放和平移状态
    var scale by remember { mutableStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .pointerInput(Unit) {
                // 检测双指缩放和平移手势
                detectTransformGestures { centroid, pan, zoom, rotation ->
                    // 应用缩放（限制在 0.5x 到 3x 之间）
                    val newScale = (scale * zoom).coerceIn(0.5f, 3f)
                    
                    // 计算缩放中心点的偏移补偿
                    if (newScale != scale) {
                        val scaleChange = newScale / scale
                        // 以触摸中心点为缩放中心
                        panOffset = (panOffset - centroid) * scaleChange + centroid
                        scale = newScale
                    }
                    
                    // 应用平移
                    panOffset += pan
                }
            }
            .pointerInput(Unit) {
                // 检测双击重置视图
                detectTapGestures(
                    onDoubleTap = {
                        scale = 1f
                        panOffset = Offset.Zero
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .width(CANVAS_WIDTH)
                .height(CANVAS_HEIGHT)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = panOffset.x
                    translationY = panOffset.y
                }
        ) {
            // 绘制网格背景和连接线
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val width = size.width
                val height = size.height
                
                // 绘制网格点背景
                val gridDotColor = Color(0xFF888888) // 更深、对比度更高的颜色
                val points = mutableListOf<Offset>()
                var x = 0f
                while (x <= width) {
                    var y = 0f
                    while (y <= height) {
                        points.add(Offset(x, y))
                        y += cellSizePx
                    }
                    x += cellSizePx
                }

                drawPoints(
                    points = points,
                    pointMode = PointMode.Points,
                    color = gridDotColor,
                    strokeWidth = 6f, // 增大点的尺寸
                    cap = StrokeCap.Round
                )
            
                // 绘制连接线（贝塞尔曲线）
                connections.forEach { connection ->
                    val sourcePos = nodePositions[connection.sourceNodeId]
                    val targetPos = nodePositions[connection.targetNodeId]
                    
                    if (sourcePos != null && targetPos != null) {
                        // 计算节点中心点
                        val startCenter = Offset(
                            sourcePos.x + nodeWidthPx / 2,
                            sourcePos.y + nodeHeightPx / 2
                        )
                        val endCenter = Offset(
                            targetPos.x + nodeWidthPx / 2,
                            targetPos.y + nodeHeightPx / 2
                        )
                        
                        val dx = endCenter.x - startCenter.x
                        val dy = endCenter.y - startCenter.y

                        // 绘制贝塞尔曲线
                        val path = Path().apply {
                            moveTo(startCenter.x, startCenter.y)
                            
                            // 计算控制点以创建平滑的曲线
                            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                            val controlDistance = distance * 0.4f
                            
                            val controlPoint1 = Offset(
                                startCenter.x + controlDistance,
                                startCenter.y
                            )
                            val controlPoint2 = Offset(
                                endCenter.x - controlDistance,
                                endCenter.y
                            )
                            
                            cubicTo(
                                controlPoint1.x, controlPoint1.y,
                                controlPoint2.x, controlPoint2.y,
                                endCenter.x, endCenter.y
                            )
                        }
                        
                        // 绘制连接线阴影
                        drawPath(
                            path = path,
                            color = Color(0x30000000),
                            style = Stroke(
                                width = 4f,
                                cap = StrokeCap.Round
                            )
                        )
                        
                        // 绘制连接线主体
                        drawPath(
                            path = path,
                            color = Color(0xFF4285F4),
                            style = Stroke(
                                width = 2.5f,
                                cap = StrokeCap.Round
                            )
                        )
                        
                        // 绘制箭头
                        val arrowSize = 10f
                        val angle = kotlin.math.atan2(dy, dx)
                        
                        val arrowPath = Path().apply {
                            moveTo(endCenter.x, endCenter.y)
                            lineTo(
                                endCenter.x - arrowSize * kotlin.math.cos(angle - Math.PI / 6).toFloat(),
                                endCenter.y - arrowSize * kotlin.math.sin(angle - Math.PI / 6).toFloat()
                            )
                            moveTo(endCenter.x, endCenter.y)
                            lineTo(
                                endCenter.x - arrowSize * kotlin.math.cos(angle + Math.PI / 6).toFloat(),
                                endCenter.y - arrowSize * kotlin.math.sin(angle + Math.PI / 6).toFloat()
                            )
                        }
                        
                        drawPath(
                            path = arrowPath,
                            color = Color(0xFF4285F4),
                            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        
            // 放置节点卡片
            nodes.forEach { node ->
                val position = nodePositions[node.id] ?: Offset.Zero
                val displayPosition = if (node.id == draggingNodeId) {
                    position + dragOffset
                } else {
                    position
                }
                
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                displayPosition.x.roundToInt(),
                                displayPosition.y.roundToInt()
                            )
                        }
                ) {
                    DraggableNodeCard(
                        node = node,
                        isDragging = node.id == draggingNodeId,
                        executionState = nodeExecutionStates[node.id],
                        onDragStart = {
                            draggingNodeId = node.id
                        },
                        onDrag = { amount ->
                            if (node.id == draggingNodeId) {
                                // 根据缩放比例调整拖动量
                                dragOffset += amount / scale
                            }
                        },
                        onDragEnd = {
                            draggingNodeId?.let { nodeId ->
                                val startPosition = nodePositions[nodeId] ?: Offset.Zero
                                val finalPosition = startPosition + dragOffset
                                
                                // --- 吸附逻辑：吸附左上角 ---
                                val snappedX = (finalPosition.x / cellSizePx).roundToInt() * cellSizePx
                                val snappedY = (finalPosition.y / cellSizePx).roundToInt() * cellSizePx
                                
                                val finalX = snappedX.toFloat()
                                val finalY = snappedY.toFloat()
                                
                                nodePositions[nodeId] = Offset(finalX, finalY)
                                onNodePositionChanged(nodeId, finalX, finalY)
                            }
                            
                            // 重置拖动状态
                            draggingNodeId = null
                            dragOffset = Offset.Zero
                        },
                        onDragCancel = {
                            // 重置拖动状态
                            draggingNodeId = null
                            dragOffset = Offset.Zero
                        },
                        onLongPress = {
                            onNodeLongPress(node.id)
                        },
                        onClick = {
                            onNodeClick(node.id)
                        }
                    )
                }
            }
        }
        
        // 缩放指示器
        if (scale != 1f || panOffset != Offset.Zero) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xE0FFFFFF)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${(scale * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        color = Color(0xFF1A73E8)
                    )
                )
            }
        }
    }
}

