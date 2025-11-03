package com.ai.assistance.operit.ui.common.markdown

import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.util.markdown.MarkdownNode
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "CanvasMarkdownRenderer"

/** 扩展函数：去除字符串首尾的所有空白字符 */
private fun String.trimAll(): String {
    return this.trim { it.isWhitespace() }
}

/**
 * Paint 对象池 - 避免重复创建相同样式的 Paint
 */
private object PaintCache {
    private data class PaintKey(
        val colorArgb: Int,
        val textSize: Float,
        val isBold: Boolean
    )
    
    private val paintCache = ConcurrentHashMap<PaintKey, android.graphics.Paint>()
    private val textPaintCache = ConcurrentHashMap<PaintKey, TextPaint>()
    
    fun getPaint(color: Color, textSize: Float, fontWeight: FontWeight): android.graphics.Paint {
        val key = PaintKey(color.toArgb(), textSize, fontWeight == FontWeight.Bold)
        return paintCache.getOrPut(key) {
            android.graphics.Paint().apply {
                this.color = key.colorArgb
                this.textSize = textSize
                this.isAntiAlias = true
                this.typeface = if (key.isBold) {
                    android.graphics.Typeface.DEFAULT_BOLD
                } else {
                    android.graphics.Typeface.DEFAULT
                }
            }
        }
    }
    
    fun getTextPaint(color: Color, textSize: Float, fontWeight: FontWeight): TextPaint {
        val key = PaintKey(color.toArgb(), textSize, fontWeight == FontWeight.Bold)
        return textPaintCache.getOrPut(key) {
            TextPaint().apply {
                this.color = key.colorArgb
                this.textSize = textSize
                this.isAntiAlias = true
                this.typeface = if (key.isBold) {
                    android.graphics.Typeface.DEFAULT_BOLD
                } else {
                    android.graphics.Typeface.DEFAULT
                }
            }
        }
    }
    
    fun clear() {
        paintCache.clear()
        textPaintCache.clear()
    }
}

/**
 * StaticLayout 缓存 - 避免重复创建相同的 StaticLayout
 * 使用 LRU 策略，最多缓存 100 个
 */
private object LayoutCache {
    private data class LayoutKey(
        val text: String,
        val colorArgb: Int,
        val textSize: Float,
        val isBold: Boolean,
        val width: Int
    )
    
    private val cache = LruCache<LayoutKey, StaticLayout>(100)
    
    fun getLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        color: Color,
        fontWeight: FontWeight
    ): StaticLayout {
        val key = LayoutKey(
            text = text,
            colorArgb = color.toArgb(),
            textSize = paint.textSize,
            isBold = fontWeight == FontWeight.Bold,
            width = width
        )
        
        return cache.get(key) ?: createStaticLayout(text, paint, width).also {
            cache.put(key, it)
        }
    }
    
    fun clear() {
        cache.evictAll()
    }
    
    private fun createStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.3f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(
                text,
                paint,
                width,
                android.text.Layout.Alignment.ALIGN_NORMAL,
                1.3f,
                0f,
                false
            )
        }
    }
}

/**
 * 绘制指令 - 用于描述如何在 Canvas 上绘制内容
 */
private sealed class DrawInstruction {
    data class Text(
        val text: String,
        val x: Float,
        val y: Float,
        val paint: android.graphics.Paint
    ) : DrawInstruction()
    
    data class Line(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val paint: android.graphics.Paint
    ) : DrawInstruction()
    
    data class TextLayout(
        val layout: StaticLayout,
        val x: Float,
        val y: Float
    ) : DrawInstruction()
}

/**
 * Canvas 版本的 Markdown 节点渲染器
 * 
 * 优化策略：
 * - 使用单个大 Canvas 绘制所有简单文本（标题、段落、列表项）
 * - 复杂组件（代码块、表格、LaTeX）保留原有的 Compose 组件
 * - 最大程度减少组件数量，提高流式渲染性能
 * 
 * 稳定性优化：
 * - 使用 remember 缓存字体大小，避免每次从 MaterialTheme 读取
 * - 稳定化 lambda 参数，减少不必要的 recompose
 */
@Composable
fun CanvasMarkdownNodeRenderer(
    node: MarkdownNode,
    textColor: Color,
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)? = null,
    index: Int,
    xmlRenderer: XmlContentRenderer
) {
    // 监听整个 CanvasMarkdownNodeRenderer 的 recompose
    SideEffect {
        if (node.type == MarkdownProcessorType.XML_BLOCK) {
            Log.d(TAG, "【全局Recompose】CanvasMarkdownNodeRenderer: index=$index, type=${node.type}, " +
                    "textColor=${textColor.hashCode()}, xmlRenderer=${xmlRenderer.hashCode()}")
        }
    }
    
    val density = LocalDensity.current
    
    // 缓存字体大小 - 避免每次 recompose 都从 MaterialTheme 读取
    // 只有当 MaterialTheme 真正变化时才会重新计算
    val typography = MaterialTheme.typography
    val fontSizes = remember(typography) {
        FontSizes(
            bodyMedium = typography.bodyMedium.fontSize,
            headlineLarge = typography.headlineLarge.fontSize,
            headlineMedium = typography.headlineMedium.fontSize,
            headlineSmall = typography.headlineSmall.fontSize,
            titleLarge = typography.titleLarge.fontSize,
            titleMedium = typography.titleMedium.fontSize,
            titleSmall = typography.titleSmall.fontSize
        )
    }
    
    // 【关键优化】稳定化 xmlRenderer 和 onLinkClick
    // 这两个参数虽然每次传入的引用可能不同，但实际功能是相同的
    // 使用 rememberUpdatedState 确保我们总是使用最新的值，但不会因为引用变化而触发不必要的重组
    val currentXmlRenderer = rememberUpdatedState(xmlRenderer)
    val currentOnLinkClick = rememberUpdatedState(onLinkClick)
    
    // 直接从 node 读取内容，不使用外层 key()
    // 让 Compose 根据节点的实际变化自然地触发 recompose，而不是强制重建
    // 这样可以保持 XML 渲染器等组件的内部状态（如折叠/展开状态）
    val content = node.content.toString()
    
    // 【不使用 key() 包裹】直接调用 renderNodeContent
    // 让内部的 remember 和组件自己根据 content 的变化来决定是否重组
    // 这样 xmlRenderer 和 onLinkClick 的引用变化不会导致重组
    renderNodeContent(
        node = node,
        content = content,
        textColor = textColor,
        fontSizes = fontSizes,
        density = density,
        modifier = modifier,
        onLinkClick = currentOnLinkClick.value,
        xmlRenderer = currentXmlRenderer.value,
        index = index
    )
}

/** 字体大小数据类 */
private data class FontSizes(
    val bodyMedium: TextUnit,
    val headlineLarge: TextUnit,
    val headlineMedium: TextUnit,
    val headlineSmall: TextUnit,
    val titleLarge: TextUnit,
    val titleMedium: TextUnit,
    val titleSmall: TextUnit
)

@Composable
private fun renderNodeContent(
    node: MarkdownNode,
    content: String,
    textColor: Color,
    fontSizes: FontSizes,
    density: Density,
    modifier: Modifier,
    onLinkClick: ((String) -> Unit)?,
    xmlRenderer: XmlContentRenderer,
    index: Int
) {
    when (node.type) {
        // ========== 简单文本类型：使用单个大 Canvas 绘制 ==========
        MarkdownProcessorType.HEADER,
        MarkdownProcessorType.PLAIN_TEXT,
        MarkdownProcessorType.ORDERED_LIST,
        MarkdownProcessorType.UNORDERED_LIST -> {
            UnifiedCanvasRenderer(
                node = node,
                textColor = textColor,
                bodyMediumSize = fontSizes.bodyMedium,
                headlineLargeSize = fontSizes.headlineLarge,
                headlineMediumSize = fontSizes.headlineMedium,
                headlineSmallSize = fontSizes.headlineSmall,
                titleLargeSize = fontSizes.titleLarge,
                titleMediumSize = fontSizes.titleMedium,
                titleSmallSize = fontSizes.titleSmall,
                density = density,
                modifier = modifier
            )
        }
        
        // ========== 代码块：保留原组件 ==========
        MarkdownProcessorType.CODE_BLOCK -> {
            val codeLines = content.trimAll().lines()
            val firstLine = codeLines.firstOrNull() ?: ""
            val language = if (firstLine.startsWith("```")) {
                firstLine.removePrefix("```").trim()
            } else ""
            
            val codeContent = codeLines
                .dropWhile { it.startsWith("```") }
                .dropLastWhile { it.endsWith("```") }
                .joinToString("\n")
            
            // 不使用 key()，让 Compose 根据位置自然识别组件
            // 这样可以保留内部状态（如"已复制"提示、Mermaid 渲染状态）
            EnhancedCodeBlock(
                code = codeContent,
                language = language,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // ========== 表格：保留原组件 ==========
        MarkdownProcessorType.TABLE -> {
            // 不使用 key()，让 Compose 根据位置自然识别组件
            EnhancedTableBlock(
                tableContent = content,
                textColor = textColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // ========== 引用块：使用 Canvas 绘制文本 + 边框 ==========
        MarkdownProcessorType.BLOCK_QUOTE -> {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(4.dp)
                ) {
                    val quoteText = content.lines().joinToString("\n") {
                        it.removePrefix("> ").removePrefix(">")
                    }
                    
                    SingleTextCanvas(
                        text = quoteText,
                        textColor = textColor,
                        fontSize = fontSizes.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        density = density,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // ========== 分隔线 ==========
        MarkdownProcessorType.HORIZONTAL_RULE -> {
            Divider(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        }
        
        // ========== XML块：保留原组件 ==========
        MarkdownProcessorType.XML_BLOCK -> {
            // 不使用 key()，让 Compose 根据位置自然识别组件
            // 这样可以在流式渲染时保持组件实例不变，从而保留内部状态
            SideEffect {
                Log.d(TAG, "【XML渲染】XML块 Recompose: index=$index, 内容长度=${content.length}, content_hash=${content.hashCode()}")
            }
            xmlRenderer.RenderXmlContent(
                xmlContent = content,
                modifier = Modifier.fillMaxWidth(),
                textColor = textColor
            )
        }
        
        // ========== 计划执行：保留原组件 ==========
        MarkdownProcessorType.PLAN_EXECUTION -> {
            // 不使用 key()，让 Compose 根据位置自然识别组件
            // 这样可以保留用户交互状态（如缩放、拖动偏移量）
            PlanExecutionRenderer(
                content = content,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        // ========== 图片：保留原组件 ==========
        MarkdownProcessorType.IMAGE -> {
            val imageContent = content.trimAll()
            if (isCompleteImageMarkdown(imageContent)) {
                // 不使用 key()，让 Compose 根据位置自然识别组件
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                ) {
                    MarkdownImageRenderer(
                        imageMarkdown = imageContent,
                        modifier = Modifier.fillMaxWidth(),
                        maxImageHeight = 140
                    )
                }
            } else {
                SingleTextCanvas(
                    text = content,
                    textColor = textColor,
                    fontSize = fontSizes.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    density = density,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp)
                )
            }
        }
        
        // ========== 其他：Canvas 绘制 ==========
        else -> {
            if (content.trimAll().isEmpty()) return
            
            SingleTextCanvas(
                text = content.trimAll(),
                textColor = textColor,
                fontSize = fontSizes.bodyMedium,
                fontWeight = FontWeight.Normal,
                density = density,
                modifier = Modifier.fillMaxWidth().padding(vertical = 0.dp)
            )
        }
    }
}

/**
 * 统一的 Canvas 渲染器
 * 在一个大 Canvas 中绘制标题、段落、列表等简单文本
 */
@Composable
private fun UnifiedCanvasRenderer(
    node: MarkdownNode,
    textColor: Color,
    bodyMediumSize: TextUnit,
    headlineLargeSize: TextUnit,
    headlineMediumSize: TextUnit,
    headlineSmallSize: TextUnit,
    titleLargeSize: TextUnit,
    titleMediumSize: TextUnit,
    titleSmallSize: TextUnit,
    density: Density,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val availableWidthPx = with(density) { maxWidth.toPx() }.toInt()
        
        // 使用 node.content.length 作为 key 来感知内容变化
        // 这样在流式渲染时，内容追加会触发重新计算
        val contentLength = node.content.length
        
        // 计算布局和绘制指令
        val (totalHeight, drawInstructions) = remember(contentLength, textColor, availableWidthPx, node.type) {
            calculateLayout(
                node = node,
                textColor = textColor,
                bodyMediumSize = bodyMediumSize,
                headlineLargeSize = headlineLargeSize,
                headlineMediumSize = headlineMediumSize,
                headlineSmallSize = headlineSmallSize,
                titleLargeSize = titleLargeSize,
                titleMediumSize = titleMediumSize,
                titleSmallSize = titleSmallSize,
                density = density,
                availableWidthPx = availableWidthPx
            )
        }
        
        // 使用单个 Canvas 绘制所有内容
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { totalHeight.toDp() })
        ) {
            drawIntoCanvas { canvas ->
                // 获取可见区域（屏幕内区域）
                val clipBounds = android.graphics.Rect()
                canvas.nativeCanvas.getClipBounds(clipBounds)
                
                // 只绘制在可见区域内的指令
                drawInstructions.forEach { instruction ->
                    when (instruction) {
                        is DrawInstruction.Text -> {
                            // 判断文本是否在可见区域内
                            val textTop = instruction.y - instruction.paint.textSize
                            val textBottom = instruction.y + instruction.paint.descent()
                            
                            if (textBottom >= clipBounds.top && textTop <= clipBounds.bottom) {
                                canvas.nativeCanvas.drawText(
                                    instruction.text,
                                    instruction.x,
                                    instruction.y,
                                    instruction.paint
                                )
                            }
                        }
                        is DrawInstruction.Line -> {
                            // 判断线条是否在可见区域内
                            val lineTop = minOf(instruction.startY, instruction.endY)
                            val lineBottom = maxOf(instruction.startY, instruction.endY)
                            
                            if (lineBottom >= clipBounds.top && lineTop <= clipBounds.bottom) {
                                canvas.nativeCanvas.drawLine(
                                    instruction.startX,
                                    instruction.startY,
                                    instruction.endX,
                                    instruction.endY,
                                    instruction.paint
                                )
                            }
                        }
                        is DrawInstruction.TextLayout -> {
                            // 使用 StaticLayout 绘制（自动换行）
                            val layoutTop = instruction.y
                            val layoutBottom = instruction.y + instruction.layout.height
                            
                            if (layoutBottom >= clipBounds.top && layoutTop <= clipBounds.bottom) {
                                canvas.nativeCanvas.save()
                                canvas.nativeCanvas.translate(instruction.x, instruction.y)
                                instruction.layout.draw(canvas.nativeCanvas)
                                canvas.nativeCanvas.restore()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 计算布局和生成绘制指令
 */
private fun calculateLayout(
    node: MarkdownNode,
    textColor: Color,
    bodyMediumSize: TextUnit,
    headlineLargeSize: TextUnit,
    headlineMediumSize: TextUnit,
    headlineSmallSize: TextUnit,
    titleLargeSize: TextUnit,
    titleMediumSize: TextUnit,
    titleSmallSize: TextUnit,
    density: Density,
    availableWidthPx: Int
): Pair<Float, List<DrawInstruction>> {
    val content = node.content.toString()
    val instructions = mutableListOf<DrawInstruction>()
    var currentY = 0f
    
    when (node.type) {
        MarkdownProcessorType.HEADER -> {
            val level = determineHeaderLevel(content)
            val headerText = content.trimStart('#', ' ').trimAll()
            
            val fontSize = when (level) {
                1 -> headlineLargeSize
                2 -> headlineMediumSize
                3 -> headlineSmallSize
                4 -> titleLargeSize
                5 -> titleMediumSize
                else -> titleSmallSize
            }
            
            val topPadding = when (level) {
                1 -> 8f
                2 -> 6f
                3 -> 4f
                else -> 3f
            } * density.density
            
            val bottomPadding = if (level <= 2) 2f else 1f
            val bottomPaddingPx = bottomPadding * density.density
            
            currentY += topPadding
            
            val textSizePx = with(density) { fontSize.toPx() }
            val textPaint = PaintCache.getTextPaint(textColor, textSizePx, FontWeight.Bold)
            val layout = LayoutCache.getLayout(headerText, textPaint, availableWidthPx, textColor, FontWeight.Bold)
            
            instructions.add(DrawInstruction.TextLayout(layout, 0f, currentY))
            currentY += layout.height
            
            currentY += bottomPaddingPx
        }
        
        MarkdownProcessorType.ORDERED_LIST -> {
            val itemContent = content.trimAll()
            val numberMatch = Regex("""^(\d+)\.\s*""").find(itemContent)
            val numberStr = numberMatch?.groupValues?.getOrNull(1) ?: ""
            val itemText = numberMatch?.let { itemContent.substring(it.range.last + 1) } ?: itemContent
            
            val startPadding = 4f * density.density
            val markerEndPadding = 4f * density.density
            
            val textSizePx = with(density) { bodyMediumSize.toPx() }
            val boldPaint = PaintCache.getPaint(textColor, textSizePx, FontWeight.Bold)
            val textPaint = PaintCache.getTextPaint(textColor, textSizePx, FontWeight.Normal)
            
            // 测量标记宽度
            val markerWidth = boldPaint.measureText("$numberStr.")
            val contentX = startPadding + markerWidth + markerEndPadding
            
            // 绘制标记
            val markerY = currentY + textSizePx
            instructions.add(DrawInstruction.Text("$numberStr.", startPadding, markerY, boldPaint))
            
            // 使用 StaticLayout 绘制内容（支持自动换行）
            val contentWidth = availableWidthPx - contentX.toInt()
            val layout = LayoutCache.getLayout(itemText, textPaint, contentWidth, textColor, FontWeight.Normal)
            instructions.add(DrawInstruction.TextLayout(layout, contentX, currentY))
            currentY += layout.height
        }
        
        MarkdownProcessorType.UNORDERED_LIST -> {
            val itemContent = content.trimAll()
            val markerMatch = Regex("""^[-*+]\s+""").find(itemContent)
            val itemText = markerMatch?.let { itemContent.substring(it.range.last + 1) } ?: itemContent
            
            val startPadding = 4f * density.density
            val markerEndPadding = 4f * density.density
            
            val textSizePx = with(density) { bodyMediumSize.toPx() }
            val boldPaint = PaintCache.getPaint(textColor, textSizePx, FontWeight.Bold)
            val textPaint = PaintCache.getTextPaint(textColor, textSizePx, FontWeight.Normal)
            
            // 测量标记宽度
            val markerWidth = boldPaint.measureText("•")
            val contentX = startPadding + markerWidth + markerEndPadding
            
            // 绘制标记
            val markerY = currentY + textSizePx + (1f * density.density)
            instructions.add(DrawInstruction.Text("•", startPadding, markerY, boldPaint))
            
            // 使用 StaticLayout 绘制内容（支持自动换行）
            val contentWidth = availableWidthPx - contentX.toInt()
            val layout = LayoutCache.getLayout(itemText, textPaint, contentWidth, textColor, FontWeight.Normal)
            instructions.add(DrawInstruction.TextLayout(layout, contentX, currentY))
            currentY += layout.height
        }
        
        MarkdownProcessorType.PLAIN_TEXT -> {
            if (content.trimAll().isEmpty()) return Pair(0f, emptyList())
            
            val textSizePx = with(density) { bodyMediumSize.toPx() }
            val textPaint = PaintCache.getTextPaint(textColor, textSizePx, FontWeight.Normal)
            val layout = LayoutCache.getLayout(content.trimAll(), textPaint, availableWidthPx, textColor, FontWeight.Normal)
            
            instructions.add(DrawInstruction.TextLayout(layout, 0f, currentY))
            currentY += layout.height
        }
        
        else -> {
            // 其他类型暂不处理
        }
    }
    
    return Pair(currentY, instructions)
}


/**
 * 单个文本的 Canvas 渲染器（用于引用块等简单场景）
 */
@Composable
private fun SingleTextCanvas(
    text: String,
    textColor: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    density: Density,
    modifier: Modifier = Modifier
) {
    if (text.isEmpty()) return
    
    BoxWithConstraints(modifier = modifier) {
        val availableWidthPx = with(density) { maxWidth.toPx() }.toInt()
        val textSizePx = with(density) { fontSize.toPx() }
        
        val textPaint = remember(textColor, textSizePx, fontWeight) {
            PaintCache.getTextPaint(textColor, textSizePx, fontWeight)
        }
        
        val layout = remember(text, textPaint, availableWidthPx, textColor, fontWeight) {
            LayoutCache.getLayout(text, textPaint, availableWidthPx, textColor, fontWeight)
        }
        
        val totalHeight = layout.height.toFloat()
        
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { totalHeight.toDp() })
        ) {
            drawIntoCanvas { canvas ->
                // 获取可见区域
                val clipBounds = android.graphics.Rect()
                canvas.nativeCanvas.getClipBounds(clipBounds)
                
                // 判断是否在可见区域内
                if (totalHeight >= clipBounds.top && 0f <= clipBounds.bottom) {
                    layout.draw(canvas.nativeCanvas)
                }
            }
        }
    }
}

/** 判断标题级别 */
private fun determineHeaderLevel(content: String): Int {
    val headerPrefix = content.takeWhile { it == '#' }
    return headerPrefix.length.coerceIn(1, 6)
}
