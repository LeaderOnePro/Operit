package com.ai.assistance.operit.ui.common.markdown

import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.collection.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Divider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.ui.common.displays.LatexCache
import com.ai.assistance.operit.util.markdown.MarkdownNode
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import com.ai.assistance.operit.util.markdown.NestedMarkdownProcessor
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.StreamInterceptor
import com.ai.assistance.operit.util.stream.splitBy as streamSplitBy
import com.ai.assistance.operit.util.stream.stream
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.noties.jlatexmath.JLatexMathDrawable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation

private const val TAG = "MarkdownRenderer"
private const val RENDER_INTERVAL_MS = 100L // 渲染间隔 0.1 秒
private const val FADE_IN_DURATION_MS = 800 // 淡入动画持续时间

// XML内容渲染器接口，用于自定义XML渲染
interface XmlContentRenderer {
    @Composable fun RenderXmlContent(xmlContent: String, modifier: Modifier, textColor: Color)
}

// 默认XML渲染器
class DefaultXmlRenderer : XmlContentRenderer {
    @Composable
    override fun RenderXmlContent(xmlContent: String, modifier: Modifier, textColor: Color) {
        Surface(
                modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
        ) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(2.dp)
                                    .border(
                                            width = 1.dp,
                                            color =
                                                    MaterialTheme.colorScheme.outline.copy(
                                                            alpha = 0.5f
                                                    ),
                                            shape = RoundedCornerShape(2.dp)
                                    )
                                    .padding(8.dp)
            ) {
                Text(
                        text = "XML内容",
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor,
                        fontWeight = FontWeight.Bold
                )

                Text(
                        text = xmlContent,
                        style =
                                MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = FontFamily.Monospace
                                ),
                        color = textColor,
                        modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** 扩展函数：去除字符串首尾的所有空白字符（包括空格、制表符、换行符等） 与标准trim()相比，这个函数更明确地处理所有类型的空白字符 */
private fun String.trimAll(): String {
    return this.trim { it.isWhitespace() }
}

/** 高性能流式Markdown渲染组件 通过Jetpack Compose实现，支持流式渲染Markdown内容 使用Stream处理系统，实现高效的异步处理 */
@Composable
fun StreamMarkdownRenderer(
        markdownStream: Stream<Char>,
        modifier: Modifier = Modifier,
        textColor: Color = LocalContentColor.current,
        backgroundColor: Color = MaterialTheme.colorScheme.surface,
        onLinkClick: ((String) -> Unit)? = null,
        xmlRenderer: XmlContentRenderer = remember { DefaultXmlRenderer() }
) {
    // 原始数据收集列表
    val nodes = remember { mutableStateListOf<MarkdownNode>() }
    // 用于UI渲染的列表
    val renderNodes = remember { mutableStateListOf<MarkdownNode>() }
    // 节点动画状态映射表
    val nodeAnimationStates = remember { mutableStateMapOf<String, Boolean>() }
    // 用于在`finally`块中启动协程
    val scope = rememberCoroutineScope()

    // 当流实例变化时，获得一个稳定的渲染器ID
    val rendererId =
            remember(markdownStream) { "renderer-${System.identityHashCode(markdownStream)}" }

    // 创建一个中间流，用于拦截和批处理渲染更新
    val interceptedStream =
            remember(markdownStream) {
                // 移除时间计算变量和日志
                // 先创建拦截器
                val processor =
                        StreamInterceptor<Char, Char>(
                                sourceStream = markdownStream,
                                onEach = { it } // 先使用简单的转发函数，后面再设置
                        )

                // 然后创建批处理更新器
                val batchUpdater =
                        BatchNodeUpdater(
                                nodes = nodes,
                                renderNodes = renderNodes,
                                nodeAnimationStates = nodeAnimationStates,
                                rendererId = rendererId,
                                isInterceptedStream = processor.interceptedStream,
                                scope = scope
                        )

                // 最后设置拦截器的onEach函数
                processor.setOnEach {
                    batchUpdater.startBatchUpdates()
                    it
                }

                processor.interceptedStream
            }

    // 处理Markdown流的变化
    LaunchedEffect(interceptedStream) {
        // 移除时间计算变量和日志

        // 重置状态
        nodes.clear()
        renderNodes.clear()

        try {
            interceptedStream.streamSplitBy(NestedMarkdownProcessor.getBlockPlugins()).collect {
                    blockGroup ->
                // 移除时间计算变量和日志
                val blockType = NestedMarkdownProcessor.getTypeForPlugin(blockGroup.tag)

                // 对于水平分割线，内容无关紧要，直接添加节点
                if (blockType == MarkdownProcessorType.HORIZONTAL_RULE) {
                    nodes.add(MarkdownNode(type = blockType, initialContent = "---"))
                    return@collect
                }

                // 判断是否为LaTeX块，如果是，先作为文本节点处理
                val isLatexBlock = blockType == MarkdownProcessorType.BLOCK_LATEX
                // 临时类型：如果是LaTeX块，先作为纯文本处理
                val tempBlockType =
                        if (isLatexBlock) MarkdownProcessorType.PLAIN_TEXT else blockType

                val isInlineContainer =
                        tempBlockType != MarkdownProcessorType.CODE_BLOCK &&
                                tempBlockType != MarkdownProcessorType.BLOCK_LATEX &&
                                tempBlockType != MarkdownProcessorType.XML_BLOCK

                // 为新块创建并添加节点
                val newNode = MarkdownNode(type = tempBlockType)
                nodes.add(newNode)
                val nodeIndex = nodes.lastIndex
                if (isInlineContainer) {
                    // Stream-parse the block stream for inline elements
                    blockGroup.stream.streamSplitBy(NestedMarkdownProcessor.getInlinePlugins())
                            .collect { inlineGroup ->
                                val originalInlineType =
                                        NestedMarkdownProcessor.getTypeForPlugin(inlineGroup.tag)
                                val isInlineLatex =
                                        originalInlineType == MarkdownProcessorType.INLINE_LATEX
                                val tempInlineType =
                                        if (isInlineLatex) MarkdownProcessorType.PLAIN_TEXT
                                        else originalInlineType

                                var childNode: MarkdownNode? = null
                                var lastCharWasNewline = false // 跟踪上一个字符是否为换行符

                                inlineGroup.stream.collect { str ->
                                    // 检查是否为空白内容
                                    val isCurrentCharNewline = str == "\n" || str == "\r\n"

                                    // 处理连续换行符逻辑
                                    if (isCurrentCharNewline) {
                                        lastCharWasNewline = true
                                        return@collect
                                    }

                                    if (childNode == null) {
                                        childNode = MarkdownNode(type = tempInlineType)
                                        newNode.children.add(childNode!!)
                                    }

                                    if (lastCharWasNewline) {
                                        // 更新父节点和子节点内容
                                        newNode.content + ("\n" + str)
                                        childNode!!.content + ("\n" + str)
                                        lastCharWasNewline = false
                                    } else {
                                        newNode.content + str
                                        childNode!!.content + str
                                    }

                                    // 更新lastCharWasNewline状态
                                    lastCharWasNewline = isCurrentCharNewline
                                }

                                // 如果是内联LaTeX，在收集完内容后，将节点替换为INLINE_LATEX类型
                                if (isInlineLatex && childNode != null) {
                                    val latexContent = childNode!!.content.toString()
                                    val latexChildNode =
                                            MarkdownNode(
                                                    type = MarkdownProcessorType.INLINE_LATEX,
                                                    initialContent = latexContent
                                            )
                                    val childIndex = newNode.children.lastIndexOf(childNode)
                                    if (childIndex != -1) {
                                        newNode.children[childIndex] = latexChildNode
                                    }
                                }

                                // 优化：如果子节点内容经过trim后为空，则移除该子节点
                                if (childNode != null &&
                                                childNode!!.content.toString().trimAll().isEmpty() &&
                                                originalInlineType ==
                                                        MarkdownProcessorType.PLAIN_TEXT
                                ) {
                                    val lastIndex = newNode.children.lastIndex
                                    if (lastIndex >= 0 && newNode.children[lastIndex] == childNode
                                    ) {
                                        newNode.children.removeAt(lastIndex)
                                    }
                                }
                            }
                } else {
                    // 对于没有内联格式的代码块，直接流式传输内容。
                    blockGroup.stream.collect { contentChunk ->
                        newNode.content + contentChunk
                    }
                }

                // 如果原始类型是LaTeX块，现在收集完毕，将其转换回LaTeX节点
                if (isLatexBlock) {
                    val latexContent = newNode.content.toString()
                    // 创建新的LaTeX节点
                    val latexNode =
                            MarkdownNode(
                                    type = MarkdownProcessorType.BLOCK_LATEX, initialContent = latexContent
                            )
                    // 原地替换节点，以保持索引的稳定性，避免不必要的重组
                    nodes[nodeIndex] = latexNode
                }

                // 移除块处理时间日志
            }

            // 移除收集完成时间日志
        } catch (e: Exception) {
            Log.e(TAG, "【流渲染】Markdown流处理异常: ${e.message}", e)
        } finally {
            // 移除时间计算变量和日志
            synchronizeRenderNodes(nodes, renderNodes, nodeAnimationStates, rendererId, scope)
            // 移除最终同步耗时日志
        }
    }

    // 渲染Markdown内容
    Surface(modifier = modifier, color = Color.Transparent, shape = RoundedCornerShape(4.dp)) {
        Column {
            key(rendererId) {
                // 只渲染节点列表，内容更新不会触发重建
                renderNodes.forEachIndexed { index, node ->
                    // 使用节点的唯一标识符作为key，与内容无关
                    val nodeKey = "node-$rendererId-$index-${node.type}"
                    key(nodeKey) {
                        // 获取节点动画状态，默认为显示状态
                        val isVisible = nodeAnimationStates[nodeKey] ?: true
                        // 创建淡入动画
                        val alpha by
                                animateFloatAsState(
                                        targetValue = if (isVisible) 1f else 0f,
                                        animationSpec = tween(durationMillis = FADE_IN_DURATION_MS),
                                        label = "fadeIn"
                                )

                        Box(modifier = Modifier.alpha(alpha)) {
                            StableMarkdownNodeRenderer(
                                    node = node,
                                    textColor = textColor,
                                    modifier = Modifier,
                                    onLinkClick = onLinkClick,
                                    index = index,
                                    xmlRenderer = xmlRenderer
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A cache for parsed markdown nodes to improve performance. */
private object MarkdownNodeCache {
    // Cache up to 100 parsed messages
    private val cache = LruCache<String, List<MarkdownNode>>(100)

    fun get(key: String): List<MarkdownNode>? {
        return cache.get(key)
    }

    fun put(key: String, value: List<MarkdownNode>) {
        cache.put(key, value)
    }
}

/** 高性能静态Markdown渲染组件 接受一个完整的字符串，一次性解析和渲染，适用于静态内容显示。 */
@Composable
fun StreamMarkdownRenderer(
        content: String,
        modifier: Modifier = Modifier,
        textColor: Color = LocalContentColor.current,
        backgroundColor: Color = MaterialTheme.colorScheme.surface,
        onLinkClick: ((String) -> Unit)? = null,
        xmlRenderer: XmlContentRenderer = remember { DefaultXmlRenderer() }
) {
    // 移除渲染时间相关的变量和日志

    // 使用流式版本相同的渲染器ID生成逻辑
    val rendererId = remember(content) { "static-renderer-${content.hashCode()}" }

    // 使用与流式版本相同的节点列表结构
    val nodes = remember(content) { mutableStateListOf<MarkdownNode>() }
    // 添加节点动画状态映射表，与流式版本保持一致
    val nodeAnimationStates = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()

    // 当content字符串变化时，一次性完成解析
    LaunchedEffect(content) {
        // 移除时间计算相关变量
        val cachedNodes = MarkdownNodeCache.get(content)

        if (cachedNodes != null) {
            // 移除时间计算相关的日志
            // 移除时间计算变量
            nodes.clear()
            nodes.addAll(cachedNodes)
            // 确保动画状态也被设置
            val newStates = mutableMapOf<String, Boolean>()
            cachedNodes.forEachIndexed { index, node ->
                val nodeKey = "static-node-$rendererId-$index-${node.type}"
                newStates[nodeKey] = true
            }
            nodeAnimationStates.putAll(newStates)
            // 移除应用缓存节点相关时间日志
            return@LaunchedEffect
        }

        launch(Dispatchers.IO) {
            try {
                val parsedNodes = mutableListOf<MarkdownNode>()
                content.stream().streamSplitBy(NestedMarkdownProcessor.getBlockPlugins()).collect {
                        blockGroup ->
                    val blockType = NestedMarkdownProcessor.getTypeForPlugin(blockGroup.tag)

                    // 对于水平分割线，内容无关紧要，直接添加节点
                    if (blockType == MarkdownProcessorType.HORIZONTAL_RULE) {
                        parsedNodes.add(MarkdownNode(type = blockType, initialContent = "---"))
                        return@collect
                    }

                    // 判断是否为LaTeX块，如果是，先作为文本节点处理
                    val isLatexBlock = blockType == MarkdownProcessorType.BLOCK_LATEX
                    // 临时类型：如果是LaTeX块，先作为纯文本处理
                    val tempBlockType =
                            if (isLatexBlock) MarkdownProcessorType.PLAIN_TEXT else blockType

                    val isInlineContainer =
                            tempBlockType != MarkdownProcessorType.CODE_BLOCK &&
                                    tempBlockType != MarkdownProcessorType.BLOCK_LATEX &&
                                    tempBlockType != MarkdownProcessorType.XML_BLOCK
                    // 为新块创建并添加节点
                    val newNode = MarkdownNode(type = tempBlockType)
                    parsedNodes.add(newNode)
                    val nodeIndex = parsedNodes.lastIndex

                    // 移除内联处理的时间相关变量

                    if (isInlineContainer) {
                        // Stream-parse the block stream for inline elements
                        blockGroup.stream.streamSplitBy(NestedMarkdownProcessor.getInlinePlugins())
                                .collect { inlineGroup ->
                                    val originalInlineType =
                                            NestedMarkdownProcessor.getTypeForPlugin(
                                                    inlineGroup.tag
                                            )
                                    val isInlineLatex =
                                            originalInlineType ==
                                                    MarkdownProcessorType.INLINE_LATEX
                                    val tempInlineType =
                                            if (isInlineLatex) MarkdownProcessorType.PLAIN_TEXT
                                            else originalInlineType

                                    var childNode: MarkdownNode? = null
                                    var lastCharWasNewline = false // 跟踪上一个字符是否为换行符

                                    inlineGroup.stream.collect { str ->
                                        // 检查是否为空白内容
                                        val isCurrentCharNewline = str == "\n" || str == "\r\n"

                                        // 处理连续换行符逻辑
                                        if (isCurrentCharNewline) {
                                            lastCharWasNewline = true
                                            return@collect
                                        }

                                        if (childNode == null) {
                                            childNode = MarkdownNode(type = tempInlineType)
                                            newNode.children.add(childNode!!)
                                        }

                                        if (lastCharWasNewline) {
                                            // 更新父节点和子节点内容
                                            newNode.content + ("\n" + str)
                                            childNode!!.content + ("\n" + str)
                                            lastCharWasNewline = false
                                        } else {
                                            newNode.content + str
                                            childNode!!.content + str
                                        }

                                        // 更新lastCharWasNewline状态
                                        lastCharWasNewline = isCurrentCharNewline
                                    }

                                    // 如果是内联LaTeX，在收集完内容后，将节点替换为INLINE_LATEX类型
                                    if (isInlineLatex && childNode != null) {
                                        val latexContent = childNode!!.content.toString()
                                        val latexChildNode =
                                                MarkdownNode(
                                                        type = MarkdownProcessorType.INLINE_LATEX,
                                                        initialContent = latexContent
                                                )
                                        val childIndex = newNode.children.lastIndexOf(childNode)
                                        if (childIndex != -1) {
                                            newNode.children[childIndex] = latexChildNode
                                        }
                                    }

                                    // 优化：如果子节点内容经过trim后为空，则移除该子节点
                                    if (childNode != null &&
                                                    childNode!!.content.toString().trimAll().isEmpty() &&
                                                    originalInlineType ==
                                                            MarkdownProcessorType.PLAIN_TEXT
                                    ) {
                                        val lastIndex = newNode.children.lastIndex
                                        if (lastIndex >= 0 &&
                                                        newNode.children[lastIndex] == childNode
                                        ) {
                                            newNode.children.removeAt(lastIndex)
                                        }
                                    }
                                }

                        // 移除内联处理耗时相关日志
                    } else {
                        // 对于没有内联格式的代码块，直接流式传输内容。
                        blockGroup.stream.collect { contentChunk ->
                            newNode.content + contentChunk
                        }
                    }

                    // 如果原始类型是LaTeX块，现在收集完毕，将其转换回LaTeX节点
                    if (isLatexBlock) {
                        val latexContent = newNode.content.toString()
                        // 创建新的LaTeX节点
                        val latexNode =
                                MarkdownNode(
                                        type = MarkdownProcessorType.BLOCK_LATEX,
                                        initialContent = latexContent
                                )
                        // 原地替换节点，以保持索引的稳定性，避免不必要的重组
                        parsedNodes[nodeIndex] = latexNode
                    }
                }

                // 移除解析耗时相关日志

                // 将解析完成的节点添加到节点列表，并更新动画状态
                withContext(Dispatchers.Main) {
                    // 保存到缓存，这样下次渲染同样内容时可以直接使用
                    MarkdownNodeCache.put(content, parsedNodes)

                    // 更新UI状态
                    // 清除现有节点
                    nodes.clear()
                    // 批量添加所有节点以减少UI重组次数
                    nodes.addAll(parsedNodes)

                    // 更新所有节点的动画状态为可见
                    val newStates = mutableMapOf<String, Boolean>()
                    parsedNodes.forEachIndexed { index, node ->
                        val nodeKey = "static-node-$rendererId-$index-${node.type}"
                        newStates[nodeKey] = true
                    }
                    nodeAnimationStates.putAll(newStates)

                    // 移除UI更新时间相关日志
                }
            } catch (e: Exception) {
                Log.e(TAG, "【静态渲染】解析Markdown内容出错: ${e.message}", e)
            }
        }
    }

    // 渲染Markdown内容 - 这里保持原样，与流式渲染使用相同的组件结构
    Surface(modifier = modifier, color = Color.Transparent, shape = RoundedCornerShape(4.dp)) {
        Column {
            key(rendererId) {
                nodes.forEachIndexed { index, node ->
                    val nodeKey = "static-node-$rendererId-$index-${node.type}"
                    key(nodeKey) {
                        val isVisible = nodeAnimationStates[nodeKey] ?: true
                        val alpha by
                                animateFloatAsState(
                                        targetValue = if (isVisible) 1f else 0f,
                                        animationSpec = tween(durationMillis = FADE_IN_DURATION_MS),
                                        label = "fadeIn"
                                )

                        Box(modifier = Modifier.alpha(alpha)) {
                            StableMarkdownNodeRenderer(
                                    node = node,
                                    textColor = textColor,
                                    modifier = Modifier,
                                    onLinkClick = onLinkClick,
                                    index = index,
                                    xmlRenderer = xmlRenderer
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 批量节点更新器 - 负责将原始节点列表的更新批量应用到渲染节点列表 */
private class BatchNodeUpdater(
        private val nodes: SnapshotStateList<MarkdownNode>,
        private val renderNodes: SnapshotStateList<MarkdownNode>,
        private val nodeAnimationStates: MutableMap<String, Boolean>,
        private val rendererId: String,
        private val isInterceptedStream: Stream<Char>,
        private val scope: CoroutineScope
) {
    private var updateJob: Job? = null

    fun startBatchUpdates() {
        if (updateJob?.isActive == true) {
            return
        }

        // 创建新的更新任务
        updateJob =
                scope.launch {
                    isInterceptedStream.lock()
                    delay(RENDER_INTERVAL_MS)
                    isInterceptedStream.unlock()

                    performBatchUpdate()
                    updateJob = null
                }
    }

    private fun performBatchUpdate() {
        // 使用synchronizeRenderNodes函数进行节点同步
        synchronizeRenderNodes(nodes, renderNodes, nodeAnimationStates, rendererId, scope)
    }
}

/** 同步渲染节点 - 确保所有节点都被渲染 在流处理完成或出现异常时调用，确保最终状态一致 */
private fun synchronizeRenderNodes(
        nodes: SnapshotStateList<MarkdownNode>,
        renderNodes: SnapshotStateList<MarkdownNode>,
        nodeAnimationStates: MutableMap<String, Boolean>,
        rendererId: String,
        scope: CoroutineScope
) {

    val keysToAnimate = mutableListOf<String>()

    // 智能同步：只处理新增或被替换的节点，避免全局重绘
    val maxCommonIndex = minOf(nodes.size, renderNodes.size)

    // 1. 检查并处理被替换的节点（例如LaTeX块）
    for (i in 0 until maxCommonIndex) {
        if (nodes[i] !== renderNodes[i]) { // 使用引用比较
            val nodeKey = "node-$rendererId-$i-${nodes[i].type}"
            nodeAnimationStates[nodeKey] = false // 准备播放动画
            keysToAnimate.add(nodeKey)
            renderNodes[i] = nodes[i]
            Log.d(TAG, "【渲染性能】最终同步：替换节点 at index $i")
        }
    }

    // 2. 添加在最后一次定时渲染后产生的新节点
    if (nodes.size > renderNodes.size) {
        for (i in renderNodes.size until nodes.size) {
            val nodeKey = "node-$rendererId-$i-${nodes[i].type}"
            nodeAnimationStates[nodeKey] = false // 准备播放动画
            keysToAnimate.add(nodeKey)
            renderNodes.add(nodes[i])
        }
    }
    // 3. (安全校验) 如果源节点变少，则同步删除
    else if (nodes.size < renderNodes.size) {
        val removeCount = renderNodes.size - nodes.size
        repeat(removeCount) { renderNodes.removeLast() }
    }

    // 启动所有新标记节点的动画
    if (keysToAnimate.isNotEmpty()) {
        scope.launch {
            // 等待下一帧，让 isVisible = false 的状态先生效
            delay(16.milliseconds)
            keysToAnimate.forEach { key ->
                // 检查以防万一节点在此期间被移除
                if (nodeAnimationStates.containsKey(key)) {
                    nodeAnimationStates[key] = true
                }
            }
        }
    }
}

/** 稳定的Markdown节点渲染器 可以处理节点内容的更新而不重建组件 */
@Composable
fun StableMarkdownNodeRenderer(
        node: MarkdownNode,
        textColor: Color,
        modifier: Modifier = Modifier,
        onLinkClick: ((String) -> Unit)? = null,
        index: Int,
        xmlRenderer: XmlContentRenderer = DefaultXmlRenderer()
) {
    // 使用remember创建每个渲染器的唯一ID，用于调试
    val rendererId = remember { "node-${node.type}-$index-${System.identityHashCode(node)}" }

    // 直接从node读取状态，确保在节点被替换时能获取到最新状态
    val content = node.content.toString()
    val children = node.children

    when (node.type) {
        // 块级元素
        MarkdownProcessorType.HEADER -> {
            val level = determineHeaderLevel(content)
            val headerText = content.trimStart('#', ' ')
            val headerStyle =
                    when (level) {
                        1 -> MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp)
                        2 -> MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp)
                        3 -> MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp)
                        4 -> MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp)
                        5 -> MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp)
                        else -> MaterialTheme.typography.titleSmall.copy(fontSize = 18.sp)
                    }

            Text(
                    text = headerText.trimAll(),
                    style = headerStyle,
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(
                                            bottom = if (level <= 2) 4.dp else 2.dp,
                                            top =
                                                    when (level) {
                                                        1 -> 14.dp
                                                        2 -> 12.dp
                                                        3 -> 10.dp
                                                        else -> 8.dp
                                                    }
                                    )
            )
        }
        MarkdownProcessorType.BLOCK_QUOTE -> {
            Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
            ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .border(
                                                width = 2.dp,
                                                color =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.15f
                                                        ),
                                                shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(
                                                start = 8.dp,
                                                top = 8.dp,
                                                end = 8.dp,
                                                bottom = 8.dp
                                        )
                ) {
                    val inlineContentMap = remember { mutableMapOf<String, InlineTextContent>() }
                    val inlineContent =
                            if (children.isEmpty()) {
                                buildAnnotatedString {
                                    append(
                                            content.lines().joinToString("\n") {
                                                it.removePrefix("> ").removePrefix(">")
                                            }
                                    )
                                }
                            } else {
                                buildAnnotatedString {
                                    appendStyledText(
                                            children,
                                            textColor,
                                            onLinkClick,
                                            inlineContentMap
                                    )
                                }
                            }
                    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                    Text(
                            text = inlineContent,
                            modifier = Modifier.fillMaxWidth().handleLinkClicks(onLinkClick, textLayoutResult, inlineContent),
                            inlineContent = inlineContentMap,
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium,
                            onTextLayout = { textLayoutResult = it }
                    )
                }
            }
        }
        MarkdownProcessorType.CODE_BLOCK -> {
            // Log.d(TAG, "【渲染性能】渲染代码块: id=$rendererId, 内容长度=${content.length}")

            // 提取代码内容和语言
            val codeLines = content.trimAll().lines()

            // 尝试从第一行提取语言标识符
            val firstLine = codeLines.firstOrNull() ?: ""
            val language =
                    if (firstLine.startsWith("```")) {
                        firstLine.removePrefix("```").trim()
                    } else {
                        ""
                    }

            // 提取纯代码内容
            val codeContent =
                    codeLines
                            .dropWhile { it.startsWith("```") }
                            .dropLastWhile { it.endsWith("```") }
                            .joinToString("\n")

            // 使用整个代码块的稳定标识符作为key
            // 这确保即使内容更新，组件也不会重建
            EnhancedCodeBlock(
                    code = codeContent,
                    language = language,
                    modifier = Modifier.fillMaxWidth()
            )
        }
        MarkdownProcessorType.ORDERED_LIST -> {
            val itemContent = content.trimAll()
            val numberMatch = Regex("""^(\d+)\.\s*""").find(itemContent)
            val numberStr = numberMatch?.groupValues?.getOrNull(1) ?: ""

            Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.Top
            ) {
                Text(
                        text = "$numberStr.",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                )

                val inlineContentMap = remember { mutableMapOf<String, InlineTextContent>() }
                val inlineContent =
                        if (children.isEmpty()) {
                            val streamingText =
                                    numberMatch?.let { itemContent.substring(it.range.last + 1) }
                                            ?: itemContent
                            buildAnnotatedString { append(streamingText) }
                        } else {
                            val modifiedChildren = children.toMutableList()
                            if (modifiedChildren.isNotEmpty()) {
                                val firstChild = modifiedChildren[0]
                                val newContent =
                                        numberMatch?.let {
                                            firstChild.content.toString().substring(it.range.last + 1)
                                        }
                                                ?: firstChild.content.toString()
                                // 创建一个更新后的新节点替换旧节点
                                val newFirstChild =
                                        MarkdownNode(firstChild.type, initialContent = newContent)
                                newFirstChild.children.addAll(firstChild.children)
                                modifiedChildren[0] = newFirstChild
                            }
                            buildAnnotatedString {
                                appendStyledText(
                                        modifiedChildren,
                                        textColor,
                                        onLinkClick,
                                        inlineContentMap
                                )
                            }
                        }

                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                Text(
                        text = inlineContent,
                        modifier = Modifier.weight(1f).handleLinkClicks(onLinkClick, textLayoutResult, inlineContent),
                        inlineContent = inlineContentMap,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        onTextLayout = { textLayoutResult = it }
                )
            }
        }
        MarkdownProcessorType.UNORDERED_LIST -> {
            val itemContent = content.trimAll()
            val markerMatch = Regex("""^[-*+]\s+""").find(itemContent)

            Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.Top
            ) {
                Text(
                        text = "•",
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                )
                val inlineContentMap = remember { mutableMapOf<String, InlineTextContent>() }
                val inlineContent =
                        if (children.isEmpty()) {
                            val streamingText =
                                    markerMatch?.let { itemContent.substring(it.range.last + 1) }
                                            ?: itemContent
                            buildAnnotatedString { append(streamingText) }
                        } else {
                            val modifiedChildren = children.toMutableList()
                            if (modifiedChildren.isNotEmpty()) {
                                val firstChild = modifiedChildren[0]
                                val newContent =
                                        markerMatch?.let {
                                            firstChild.content.toString().substring(it.range.last + 1)
                                        }
                                                ?: firstChild.content.toString()
                                val newFirstChild =
                                        MarkdownNode(firstChild.type, initialContent = newContent)
                                newFirstChild.children.addAll(firstChild.children)
                                modifiedChildren[0] = newFirstChild
                            }
                            buildAnnotatedString {
                                appendStyledText(
                                        modifiedChildren,
                                        textColor,
                                        onLinkClick,
                                        inlineContentMap
                                )
                            }
                        }

                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                Text(
                        text = inlineContent,
                        modifier = Modifier.weight(1f).handleLinkClicks(onLinkClick, textLayoutResult, inlineContent),
                        inlineContent = inlineContentMap,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        onTextLayout = { textLayoutResult = it }
                )
            }
        }
        MarkdownProcessorType.HORIZONTAL_RULE -> {
            Divider(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        }
        MarkdownProcessorType.BLOCK_LATEX -> {
            // 块级LaTeX公式渲染
            Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    color = Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
            ) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(vertical = 0.dp, horizontal = 8.dp), // 减少内边距
                        contentAlignment = Alignment.Center
                ) {
                    // 提取LaTeX内容，移除$$分隔符
                    val latexContent = content.trimAll().removeSurrounding("$$", "$$")

                    // 使用AndroidView和JLatexMath渲染LaTeX公式
                    AndroidView(
                            factory = { context ->
                                TextView(context).apply {
                                    // 设置初始空白状态
                                    text = ""
                                }
                            },
                            update = { textView ->
                                // 在update回调中渲染LaTeX公式
                                try {
                                    val drawable =
                                            LatexCache.getDrawable(
                                                    latexContent.trim(),
                                                    JLatexMathDrawable.builder(latexContent)
                                                            .textSize(
                                                                    14f *
                                                                            textView.resources
                                                                                    .displayMetrics
                                                                                    .density
                                                            )
                                                            .padding(2) // 减少内边距
                                                            .background(0x00000000)
                                                            .align(JLatexMathDrawable.ALIGN_CENTER)
                                                            .color(textColor.toArgb())
                                            )

                                    // 设置边界并添加到TextView
                                    drawable.setBounds(
                                            0,
                                            0,
                                            drawable.intrinsicWidth,
                                            drawable.intrinsicHeight
                                    )
                                    textView.setCompoundDrawables(null, drawable, null, null)
                                } catch (e: Exception) {
                                    // 渲染失败时回退到纯文本显示
                                    textView.text = e.message ?: "渲染失败"
                                    textView.setTextColor(textColor.toArgb())
                                    textView.textSize = 16f
                                    textView.typeface = android.graphics.Typeface.MONOSPACE
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        MarkdownProcessorType.TABLE -> {
            Log.d(TAG, "【渲染性能】渲染表格: id=$rendererId")

            // 使用增强型表格组件
            EnhancedTableBlock(
                    tableContent = content,
                    textColor = textColor,
                    modifier = Modifier.fillMaxWidth()
            )
        }

        // 添加XML块渲染支持
        MarkdownProcessorType.XML_BLOCK -> {
            // Log.d(TAG, "【渲染性能】渲染XML块: id=$rendererId, 内容长度=${content.length}")
            xmlRenderer.RenderXmlContent(
                    xmlContent = content,
                    modifier = Modifier.fillMaxWidth(),
                    textColor = textColor
            )
        }

        // 添加对图片的专门处理
        MarkdownProcessorType.IMAGE -> {
            val imageContent = content.trimAll()
            if (isCompleteImageMarkdown(imageContent)) {
                // 使用更紧凑的图片渲染
                Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp) // 最小的垂直内边距
                ) {
                    MarkdownImageRenderer(
                            imageMarkdown = imageContent,
                            modifier = Modifier.fillMaxWidth(),
                            maxImageHeight = 140 // 更小的最大高度
                    )
                }
            } else {
                // 不完整的图片标记作为普通文本处理
                Text(
                        text = node.content.toString(),
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }
        }

        // 内联元素通常不会直接渲染，只在父节点中应用样式
        MarkdownProcessorType.PLAIN_TEXT -> {
            if (content.trimAll().isEmpty()) {
                return
            }

            val inlineContentMap = remember { mutableMapOf<String, InlineTextContent>() }
            val inlineContent =
                    if (children.isEmpty()) {
                        buildAnnotatedString { append(content.trimAll()) }
                    } else {
                        buildAnnotatedString {
                            appendStyledText(children, textColor, onLinkClick, inlineContentMap)
                        }
                    }

            if (inlineContent.isEmpty()) return

            var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                    text = inlineContent,
                    modifier = Modifier.padding(vertical = 1.dp).handleLinkClicks(onLinkClick, textLayoutResult, inlineContent),
                    inlineContent = inlineContentMap,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    onTextLayout = { textLayoutResult = it }
            )
        }

        // 如果节点类型不在上述处理范围内，则作为普通文本处理
        else -> {
            if (content.trimAll().isEmpty()) {
                return
            }

            val inlineContentMap = remember { mutableMapOf<String, InlineTextContent>() }
            val inlineContent =
                    if (children.isEmpty()) {
                        buildAnnotatedString { append(content.trimAll()) }
                    } else {
                        buildAnnotatedString {
                            appendStyledText(children, textColor, onLinkClick, inlineContentMap)
                        }
                    }

            if (inlineContent.isEmpty()) return

            var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                    text = inlineContent,
                    modifier = Modifier.padding(vertical = 1.dp).handleLinkClicks(onLinkClick, textLayoutResult, inlineContent),
                    inlineContent = inlineContentMap,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium,
                    onTextLayout = { textLayoutResult = it }
            )
        }
    }
}

/** 扩展函数，用于处理Markdown文本中的链接点击事件，现在会安全地处理链接点击 */
private fun Modifier.handleLinkClicks(
    onLinkClick: ((String) -> Unit)?,
    textLayoutResult: TextLayoutResult?,
    annotatedString: AnnotatedString
): Modifier = if (onLinkClick != null) {
    this.pointerInput(onLinkClick, textLayoutResult, annotatedString) {
        forEachGesture {
            awaitPointerEventScope {
                val down = awaitFirstDown(requireUnconsumed = false)
                val up = waitForUpOrCancellation()
                if (up != null) {
                    textLayoutResult?.let { layoutResult ->
                        val position = layoutResult.getOffsetForPosition(up.position)
                        annotatedString.getStringAnnotations("URL", position, position)
                            .firstOrNull()?.let { annotation ->
                                up.consume()
                                // 使用回调处理链接点击，现在会显示预览弹窗
                                onLinkClick(annotation.item)
                            }
                    }
                }
            }
        }
    }
} else {
    this
}

/** 将文本及其子节点添加到AnnotatedString中，应用适当的样式 */
@Composable
private fun AnnotatedString.Builder.appendStyledText(
        children: List<MarkdownNode>,
        textColor: Color,
        onLinkClick: ((String) -> Unit)?,
        inlineContentMap: MutableMap<String, InlineTextContent>
) {
    // 如果一个块有子节点，那么这些子节点就代表了全部内容。
    // 我们只需按顺序渲染每个子节点，并应用其特定的样式。
    children.forEach { child ->
        when (child.type) {
            MarkdownProcessorType.BOLD -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(child.content.toString()) }
            }
            MarkdownProcessorType.ITALIC -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(child.content.toString()) }
            }
            MarkdownProcessorType.INLINE_CODE -> {
                withStyle(
                        SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color.LightGray.copy(alpha = 0.15f),
                                fontSize = 14.sp
                        )
                ) { append(child.content.toString()) }
            }
            MarkdownProcessorType.STRIKETHROUGH -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(child.content.toString())
                }
            }
            MarkdownProcessorType.UNDERLINE -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(child.content.toString())
                }
            }
            MarkdownProcessorType.LINK -> {
                val linkText = extractLinkText(child.content.toString())
                val linkUrl = extractLinkUrl(child.content.toString())

                pushStringAnnotation("URL", linkUrl)
                withStyle(
                        SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                        )
                ) { append(linkText) }
                pop()
            }
            MarkdownProcessorType.INLINE_LATEX -> {
                val latexContent = child.content.toString().trimAll().removeSurrounding("$$", "$$")

                val context = LocalContext.current
                val density = LocalDensity.current
                val color = textColor.toArgb()
                val textSize = MaterialTheme.typography.bodyMedium.fontSize

                if (latexContent.isBlank()) return@forEach

                // 在remember块内处理异常，而不是在Composable函数调用外部
                val latexRenderResult = remember(latexContent, textColor, textSize) {
                    try {
                        val drawable = LatexCache.getDrawable(
                                latexContent,
                                JLatexMathDrawable.builder(latexContent)
                                        .textSize(with(density) { textSize.toPx() })
                                        .padding(2) // 减少内边距
                                        .color(color)
                                        .background(0x00000000)
                                        .align(JLatexMathDrawable.ALIGN_LEFT)
                        )
                        // 成功时返回drawable
                        Result.success(drawable)
                    } catch (e: Exception) {
                        Log.e(TAG, "行内LaTeX渲染失败: $latexContent", e)
                        // 失败时返回null
                        Result.failure<JLatexMathDrawable>(e)
                    }
                }

                // 根据渲染结果选择不同的渲染方式
                if (latexRenderResult.isSuccess) {
                    val drawable = latexRenderResult.getOrNull()
                    if (drawable != null) {
                        val width = with(density) { drawable.intrinsicWidth.toSp() }
                        val height = with(density) { drawable.intrinsicHeight.toSp() }

                        val inlineContentId = "ilatex_${latexContent.hashCode()}_${System.nanoTime()}"

                        appendInlineContent(inlineContentId, "[latex]")

                        inlineContentMap[inlineContentId] =
                                InlineTextContent(
                                        Placeholder(
                                                width = width,
                                                height = height,
                                                placeholderVerticalAlign =
                                                        PlaceholderVerticalAlign.TextCenter
                                        )
                                ) {
                                    AndroidView(
                                            factory = { ctx ->
                                                ImageView(ctx).apply { setImageDrawable(drawable) }
                                            }
                                    )
                                }
                    }
                } else {
                    // 渲染失败时回退到纯文本显示
                    withStyle(
                            SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = Color.LightGray.copy(alpha = 0.15f),
                                    fontSize = 14.sp
                            )
                    ) {
                        append("$$")
                        append(child.content.toString())
                        append("$$")
                    }
                }
            }
            else -> {
                // 默认情况，通常是 PLAIN_TEXT
                append(child.content.toString())
            }
        }
    }
}

/** 确定标题级别 */
private fun determineHeaderLevel(content: String): Int {
    val headerMarkers = content.takeWhile { it == '#' }.count()
    val level = minOf(headerMarkers, 6) // 标题级别最高到6
    return level
}

/** 从链接Markdown中提取链接文本 例如：从 [链接文本](https://example.com) 中提取 "链接文本" */
private fun extractLinkText(linkContent: String): String {
    val startBracket = linkContent.indexOf('[')
    val endBracket = linkContent.indexOf(']')
    val result =
            if (startBracket != -1 && endBracket != -1 && startBracket < endBracket) {
                linkContent.substring(startBracket + 1, endBracket)
            } else {
                linkContent
            }
    return result
}

/** 从链接Markdown中提取链接URL 例如：从 [链接文本](https://example.com) 中提取 "https://example.com" */
private fun extractLinkUrl(linkContent: String): String {
    val startParenthesis = linkContent.indexOf('(')
    val endParenthesis = linkContent.indexOf(')')
    val result =
            if (startParenthesis != -1 && endParenthesis != -1 && startParenthesis < endParenthesis
            ) {
                linkContent.substring(startParenthesis + 1, endParenthesis)
            } else {
                ""
            }
    return result
}

/** 从图片Markdown中提取替代文本 例如：从 ![替代文本](https://example.com/image.jpg) 中提取 "替代文本" */
private fun extractImageAlt(imageContent: String): String {
    val startBracket = imageContent.indexOf('[')
    val endBracket = imageContent.indexOf(']')
    val result =
            if (startBracket != -1 && endBracket != -1 && startBracket < endBracket) {
                imageContent.substring(startBracket + 1, endBracket)
            } else {
                "图片"
            }
    return result
}

/**
 * 从图片Markdown中提取图片URL 例如：从 ![替代文本](https://example.com/image.jpg) 中提取
 * "https://example.com/image.jpg"
 */
private fun extractImageUrl(imageContent: String): String {
    val startParenthesis = imageContent.indexOf('(')
    val endParenthesis = imageContent.indexOf(')')
    val result =
            if (startParenthesis != -1 && endParenthesis != -1 && startParenthesis < endParenthesis
            ) {
                imageContent.substring(startParenthesis + 1, endParenthesis)
            } else {
                ""
            }
    return result
}
