package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Rect
import android.util.Log

@OptIn(ExperimentalTextApi::class)
@Composable
fun GraphVisualizer(
    graph: Graph,
    modifier: Modifier = Modifier,
    selectedNodeId: String? = null,
    boxSelectedNodeIds: Set<String> = emptySet(),
    isBoxSelectionMode: Boolean = false, // 新增：是否处于框选模式
    linkingNodeIds: List<String> = emptyList(),
    selectedEdgeId: Long? = null,
    onNodeClick: (Node) -> Unit,
    onEdgeClick: (Edge) -> Unit,
    onNodesSelected: (Set<String>) -> Unit // 新增：框选完成后的回调
) {
    Log.d("GraphVisualizer", "Recomposing. isBoxSelectionMode: $isBoxSelectionMode")
    val textMeasurer = rememberTextMeasurer()
    val colorScheme = MaterialTheme.colorScheme
    var nodePositions by remember { mutableStateOf(mapOf<String, Offset>()) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var selectionRect by remember { mutableStateOf<Rect?>(null) } // 用于绘制选择框

    // 当退出框选模式时，确保清除选框
    LaunchedEffect(isBoxSelectionMode) {
        if (!isBoxSelectionMode) {
            selectionRect = null
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        // Force-directed layout simulation
        LaunchedEffect(graph.nodes, width, height) {
            if (graph.nodes.isNotEmpty()) {
                // 智能地更新位置：保留现有节点位置，只为新节点分配位置
                val currentPositions = nodePositions
                val newPositions = mutableMapOf<String, Offset>()
                val center = Offset(width / 2, height / 2)

                graph.nodes.forEach { node ->
                    // 如果节点已经存在，则保留其位置；否则，在中心附近随机放置新节点
                    newPositions[node.id] = currentPositions[node.id] ?: Offset(
                        (center.x + (Math.random() * 200 - 100)).toFloat(),
                        (center.y + (Math.random() * 200 - 100)).toFloat()
                    )
                }

                withContext(Dispatchers.Main) {
                    nodePositions = newPositions
                }

                // Run simulation in a background coroutine
                launch(Dispatchers.Default) {
                    val positions = newPositions.toMutableMap()
                    val velocities = mutableMapOf<String, Offset>()
                    graph.nodes.forEach { node -> velocities[node.id] = Offset.Zero }

                    // Tuned parameters for a more stable and visually pleasing layout
                    val iterations = 300
                    val repulsionStrength = 150000f  // Stronger repulsion to prevent clumping
                    val attractionStrength = 0.05f   // Weaker spring force
                    val idealEdgeLength = 300f       // Longer ideal distance for edges
                    val gravityStrength = 0.02f      // Weaker gravity to allow graph to spread out
                    val maxSpeed = 50f
                    val damping = 0.95f

                    for (i in 0 until iterations) {
                        val forces = mutableMapOf<String, Offset>()
                        graph.nodes.forEach { node -> forces[node.id] = Offset.Zero }

                        // Repulsive forces between all pairs of nodes
                        for (n1 in graph.nodes) {
                            for (n2 in graph.nodes) {
                                if (n1.id == n2.id) continue
                                val p1 = positions[n1.id] ?: continue
                                val p2 = positions[n2.id] ?: continue
                                val delta = p1 - p2
                                val distance = delta.getDistance().coerceAtLeast(1f)
                                val force = repulsionStrength / (distance * distance)
                                val direction = if (distance > 0) delta / distance else Offset.Zero
                                forces[n1.id] = forces[n1.id]!! + direction * force
                            }
                        }

                        // Attractive forces (spring model) along edges
                        for (edge in graph.edges) {
                            val sourcePos = positions[edge.sourceId]
                            val targetPos = positions[edge.targetId]
                            if (sourcePos != null && targetPos != null) {
                                val delta = targetPos - sourcePos
                                val distance = delta.getDistance().coerceAtLeast(1f)
                                // Spring force: k * (x - ideal_length)
                                val force = attractionStrength * (distance - idealEdgeLength)
                                val direction = if (distance > 0) delta / distance else Offset.Zero
                                forces[edge.sourceId] = forces[edge.sourceId]!! + direction * force
                                forces[edge.targetId] = forces[edge.targetId]!! - direction * force
                            }
                        }
                        
                        // Gravity force towards the center
                        for (node in graph.nodes) {
                            val p = positions[node.id] ?: continue
                            val delta = center - p
                            val distance = delta.getDistance().coerceAtLeast(1f)
                            val direction = if (distance > 0) delta / distance else Offset.Zero
                            forces[node.id] = forces[node.id]!! + direction * gravityStrength * distance
                        }

                        // Update positions based on forces
                        for (node in graph.nodes) {
                            val nodeForce = forces[node.id]!!
                            var nodeVelocity = velocities[node.id]!!
                            nodeVelocity = (nodeVelocity + nodeForce) * damping

                            val speed = nodeVelocity.getDistance()
                            if (speed > maxSpeed) {
                                nodeVelocity = nodeVelocity * (maxSpeed / speed)
                            }

                            velocities[node.id] = nodeVelocity
                            positions[node.id] = positions[node.id]!! + nodeVelocity
                        }

                        // Update UI
                        withContext(Dispatchers.Main) {
                            nodePositions = positions.toMap()
                        }
                        delay(16)
                    }
                }
            } else {
                 withContext(Dispatchers.Main) {
                    nodePositions = emptyMap()
                }
            }
        }

        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(isBoxSelectionMode) { // KEY CHANGE: Relaunch gestures when mode changes
                Log.d("GraphVisualizer", "pointerInput recomposed/restarted. NEW MODE: ${if (isBoxSelectionMode) "BoxSelect" else "Normal"}")
                coroutineScope {
                    if (isBoxSelectionMode) {
                        Log.d("GraphVisualizer", "Setting up GESTURES FOR BOX SELECTION mode.")
                        // --- 框选模式下的手势 ---
                        // 1. 拖拽框选 (排他性，禁用平移/缩放)
                        launch {
                            var dragStart: Offset? = null
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    Log.d("GraphVisualizer", "BoxSelect: onDragStart")
                                    dragStart = startOffset
                                    selectionRect = createNormalizedRect(startOffset, startOffset)
                                },
                                onDrag = { change, _ ->
                                    dragStart?.let { start ->
                                        selectionRect =
                                            createNormalizedRect(start, change.position)
                                    }
                                },
                                onDragEnd = {
                                    Log.d("GraphVisualizer", "BoxSelect: onDragEnd")
                                    selectionRect?.let { rect ->
                                        val selectedIds = nodePositions.filter { (_, pos) ->
                                            val viewPos = pos * scale + offset
                                            rect.contains(viewPos)
                                        }.keys
                                        onNodesSelected(selectedIds)
                                    }
                                    selectionRect = null
                                    dragStart = null
                                },
                                onDragCancel = {
                                    Log.d("GraphVisualizer", "BoxSelect: onDragCancel")
                                    selectionRect = null
                                    dragStart = null
                                }
                            )
                        }
                        // 2. 点击单选/取消
                        launch {
                            detectTapGestures(onTap = { tapOffset ->
                                Log.d("GraphVisualizer", "BoxSelect: onTap")
                                val clickedNode = graph.nodes.findLast { node ->
                                    nodePositions[node.id]?.let { pos ->
                                        val viewPos = pos * scale + offset
                                        (tapOffset - viewPos).getDistance() <= 60f * scale
                                    } ?: false
                                }
                                if (clickedNode != null) {
                                    onNodeClick(clickedNode)
                                }
                            })
                        }
                    } else {
                        Log.d("GraphVisualizer", "Setting up GESTURES FOR NORMAL mode.")
                        // --- 普通模式下的手势 ---
                        // 1. 平移和缩放
                        launch {
                            Log.d("GraphVisualizer", "Launching detectTransformGestures (Pan/Zoom).")
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val oldScale = scale
                                val newScale = (scale * zoom).coerceIn(0.2f, 5f)
                                offset = (offset - centroid) * (newScale / oldScale) + centroid + pan
                                scale = newScale
                            }
                        }
                        // 2. 点击
                        launch {
                            detectTapGestures(onTap = { tapOffset ->
                                Log.d("GraphVisualizer", "Normal Mode: onTap")
                                val clickedNode = graph.nodes.findLast { node ->
                                    nodePositions[node.id]?.let { pos ->
                                        val viewPos = pos * scale + offset
                                        (tapOffset - viewPos).getDistance() <= 60f * scale
                                    } ?: false
                                }
                                val clickedEdge = if (clickedNode == null) {
                                    graph.edges.find { edge ->
                                        val sourcePos = nodePositions[edge.sourceId]
                                        val targetPos = nodePositions[edge.targetId]
                                        if (sourcePos != null && targetPos != null) {
                                            val start = sourcePos * scale + offset
                                            val end = targetPos * scale + offset
                                            distanceToSegment(tapOffset, start, end) < 20f
                                        } else false
                                    }
                                } else null

                                if (clickedNode != null) {
                                    onNodeClick(clickedNode)
                                } else if (clickedEdge != null) {
                                    onEdgeClick(clickedEdge)
                                }
                            })
                        }
                    }
                }
            }
        ) {
            // 绘制选框
            selectionRect?.let { rect ->
                drawRect(
                    color = colorScheme.primary.copy(alpha = 0.3f),
                    topLeft = rect.topLeft,
                    size = rect.size
                )
                drawRect(
                    color = colorScheme.primary,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = 6f)
                )
            }

            // Edges
            graph.edges.forEach { edge ->
                val sourcePos = nodePositions[edge.sourceId]
                val targetPos = nodePositions[edge.targetId]
                if (sourcePos != null && targetPos != null) {
                    val start = sourcePos * scale + offset
                    val end = targetPos * scale + offset
                    val strokeWidth = (edge.weight * 3f).coerceIn(1f, 12f)
                    
                    // 如果是跨文件夹连接，使用虚线绘制
                    if (edge.isCrossFolderLink) {
                        drawLine(
                            color = if (edge.id == selectedEdgeId) colorScheme.error else colorScheme.outline,
                            start = start,
                            end = end,
                            strokeWidth = strokeWidth,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    } else {
                        drawLine(
                            color = if (edge.id == selectedEdgeId) colorScheme.error else colorScheme.outline,
                            start = start,
                            end = end,
                            strokeWidth = strokeWidth
                        )
                    }
                    
                    edge.label?.let { label ->
                        val center = (start + end) / 2f
                        val angle = atan2(end.y - start.y, end.x - start.x)
                        
                        val textLayoutResult = textMeasurer.measure(
                            text = AnnotatedString(label),
                            style = TextStyle(fontSize = 10.sp, color = colorScheme.onSurfaceVariant)
                        )
                        
                        // Simple collision avoidance for label, could be improved
                        val labelOffset = if (angle > -Math.PI / 2 && angle < Math.PI / 2) -textLayoutResult.size.height.toFloat() else 0f
                        
                        drawText(
                            textLayoutResult = textLayoutResult,
                            topLeft = Offset(
                                x = center.x - textLayoutResult.size.width / 2,
                                y = center.y + labelOffset
                            )
                        )
                    }
                }
            }

            // Nodes
            graph.nodes.forEach { node ->
                val position = nodePositions[node.id]
                if (position != null) {
                    val isSelected = node.id == selectedNodeId
                    val isLinkingCandidate = node.id in linkingNodeIds
                    val isBoxSelected = node.id in boxSelectedNodeIds // 新增：检查是否被框选
                    drawNode(
                        node = node,
                        position = position * scale + offset,
                        radius = 60f * scale,
                        textMeasurer = textMeasurer,
                        colorScheme = colorScheme,
                        isSelected = isSelected,
                        isLinkingCandidate = isLinkingCandidate,
                        isBoxSelected = isBoxSelected // 新增：传递框选状态
                    )
                }
            }
        }
    }
}

/**
 * 根据起始点和结束点创建标准化的矩形，确保left <= right, top <= bottom。
 */
private fun createNormalizedRect(start: Offset, end: Offset): Rect {
    return Rect(
        left = min(start.x, end.x),
        top = min(start.y, end.y),
        right = max(start.x, end.x),
        bottom = max(start.y, end.y)
    )
}

private fun distanceToSegment(p: Offset, start: Offset, end: Offset): Float {
    val l2 = (start - end).getDistanceSquared()
    if (l2 == 0f) return (p - start).getDistance()
    val t = ((p.x - start.x) * (end.x - start.x) + (p.y - start.y) * (end.y - start.y)) / l2
    val tClamped = t.coerceIn(0f, 1f)
    val projection = start + (end - start) * tClamped
    return (p - projection).getDistance()
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawNode(
    node: Node,
    position: Offset,
    radius: Float,
    textMeasurer: TextMeasurer,
    colorScheme: androidx.compose.material3.ColorScheme,
    isSelected: Boolean,
    isLinkingCandidate: Boolean,
    isBoxSelected: Boolean // 新增：接收框选状态
) {
    val color = if (isSelected) colorScheme.secondary else node.color
    drawCircle(
        color = color,
        radius = radius,
        center = position
    )

    // 框选高亮
    if (isBoxSelected) {
        drawCircle(
            color = colorScheme.tertiary, // 使用主题的第三色作为高亮
            radius = radius + 10f, // 比节点稍大
            center = position,
            style = Stroke(width = 8f)
        )
    }

    if (isLinkingCandidate) {
        drawCircle(
            color = colorScheme.error,
            radius = radius,
            center = position,
            style = Stroke(width = 8f, cap = StrokeCap.Round)
        )
    }
    
    val textLayoutResult = textMeasurer.measure(
        text = AnnotatedString(node.label),
        style = TextStyle(fontSize = 12.sp, color = colorScheme.onSurface)
    )
    
    val textPosition = Offset(
        x = position.x - textLayoutResult.size.width / 2,
        y = position.y - textLayoutResult.size.height / 2
    )
    
    drawText(textLayoutResult, topLeft = textPosition)
} 