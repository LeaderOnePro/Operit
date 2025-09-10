package com.ai.assistance.operit.ui.common.markdown

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.api.chat.plan.ExecutionGraph
import com.ai.assistance.operit.api.chat.plan.PlanParser
import com.ai.assistance.operit.api.chat.plan.TaskNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PlanExecutionRenderer(
    modifier: Modifier = Modifier,
    content: String
) {
    val executionGraph = remember(content) {
        PlanParser.parseExecutionGraph(content)
    }

    if (executionGraph != null) {
        ExecutionGraphView(graph = executionGraph, modifier = modifier)
    } else {
        // Fallback for parsing error
        Text(
            text = "Failed to parse execution plan.\nRaw content:\n$content",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier.padding(8.dp)
        )
    }
}

@Composable
fun ExecutionGraphView(
    graph: ExecutionGraph,
    modifier: Modifier = Modifier
) {
    val nodePositions = remember { mutableStateMapOf<String, Offset>() }
    val nodeSizes = remember { mutableStateMapOf<String, IntSize>() }
    val density = LocalDensity.current

    // Simple topological sort for layout
    val sortedTasks = remember(graph) {
        try {
            PlanParser.topologicalSort(graph)
        } catch (e: Exception) {
            graph.tasks // fallback to original order
        }
    }
    
    val levels = remember(sortedTasks) {
        calculateNodeLevels(sortedTasks, graph)
    }

    Card(
        modifier = modifier.padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Execution Plan",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val canvasWidth = with(density) { maxWidth.toPx() }
                val canvasHeight = with(density) { 400.dp.toPx() } // Fixed height for simplicity

                // Position nodes
                LaunchedEffect(levels, canvasWidth) {
                    withContext(Dispatchers.Default) {
                        positionNodes(
                            levels,
                            canvasWidth,
                            nodePositions,
                            nodeSizes,
                            density.density
                        )
                    }
                }

                Canvas(modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp) // Fixed height
                ) {
                    // Draw dependency lines
                    graph.tasks.forEach { task ->
                        val endPos = nodePositions[task.id]
                        if (endPos != null) {
                            task.dependencies.forEach { depId ->
                                val startPos = nodePositions[depId]
                                if (startPos != null) {
                                    drawLine(
                                        color = Color.Gray,
                                        start = startPos,
                                        end = endPos,
                                        strokeWidth = 2f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Draw nodes
                sortedTasks.forEach { task ->
                    val position = nodePositions[task.id]
                    if (position != null) {
                        TaskNodeView(
                            task = task,
                            modifier = Modifier
                                .offset {
                                    val size = nodeSizes[task.id] ?: IntSize.Zero
                                    IntOffset(
                                        (position.x - size.width / 2).toInt(),
                                        (position.y - size.height / 2).toInt()
                                    )
                                }
                                .onSizeChanged { size ->
                                    nodeSizes[task.id] = size
                                }
                        )
                    }
                }
            }

            Text(
                text = "Final Summary: ${graph.finalSummaryInstruction}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

private fun calculateNodeLevels(sortedTasks: List<TaskNode>, graph: ExecutionGraph): Map<String, Int> {
    val levels = mutableMapOf<String, Int>()
    val taskMap = graph.tasks.associateBy { it.id }

    for (task in sortedTasks) {
        if (task.dependencies.isEmpty()) {
            levels[task.id] = 0
        } else {
            val maxDepLevel = task.dependencies.map { levels[it] ?: -1 }.maxOrNull() ?: -1
            levels[task.id] = maxDepLevel + 1
        }
    }
    return levels
}

private fun positionNodes(
    levels: Map<String, Int>,
    canvasWidth: Float,
    nodePositions: MutableMap<String, Offset>,
    nodeSizes: Map<String, IntSize>,
    density: Float
) {
    val maxLevel = levels.values.maxOrNull() ?: 0
    val levelCounts = mutableMapOf<Int, Int>()
    val levelIndices = mutableMapOf<String, Int>()

    levels.entries.sortedBy { it.value }.forEach { (id, level) ->
        val count = levelCounts.getOrDefault(level, 0)
        levelIndices[id] = count
        levelCounts[level] = count + 1
    }
    
    val yPadding = 120 * density
    val xPadding = 40 * density

    levels.forEach { (id, level) ->
        val tasksInLevel = levelCounts[level] ?: 1
        val indexInLevel = levelIndices[id] ?: 0

        val x = (canvasWidth - 2 * xPadding) * (indexInLevel + 1) / (tasksInLevel + 1) + xPadding
        val y = yPadding + level * yPadding

        nodePositions[id] = Offset(x, y)
    }
}

@Composable
fun TaskNodeView(
    task: TaskNode,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "Task: ${task.name} (ID: ${task.id})",
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = "Instruction: ${task.instruction}",
                style = MaterialTheme.typography.bodySmall
            )
            if (task.dependencies.isNotEmpty()) {
                Text(
                    text = "Depends on: ${task.dependencies.joinToString()}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
} 