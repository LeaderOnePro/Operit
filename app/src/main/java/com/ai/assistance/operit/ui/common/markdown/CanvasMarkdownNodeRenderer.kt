package com.ai.assistance.operit.ui.common.markdown

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.util.markdown.MarkdownNode
import com.ai.assistance.operit.util.markdown.MarkdownProcessorType
import java.util.concurrent.ConcurrentHashMap
import android.graphics.Typeface

private const val TAG = "CanvasMarkdownRenderer"

/**
 * 通用性能优化 Modifier：仅在组件进入屏幕可见区域时才进行绘制。
 * 
 * 实现原理：
 * 1.  使用 `onGloballyPositioned` 监听组件的布局位置和大小。
 * 2.  获取 `LocalView.current.getGlobalVisibleRect()` 来确定当前窗口的可见区域。
 * 3.  通过 `layoutCoordinates.boundsInWindow()` 获取组件在窗口中的边界。
 * 4.  比较组件边界和窗口可见边界，判断组件是否应该被渲染。
 * 5.  使用 `drawWithContent`，如果组件不可见，则跳过其内容的绘制阶段，但其空间占用不变。
 *
 * @return 返回一个配置好的 Modifier。
 */
@Composable
private fun Modifier.drawOnlyWhenVisible(): Modifier {
    var isVisible by remember { mutableStateOf(false) }
    val view = LocalView.current

    return this
            .onGloballyPositioned { layoutCoordinates ->
                val windowRect = android.graphics.Rect()
                // getGlobalVisibleRect 提供了视图在全局坐标系中的可见部分。
                // 对于根视图，这给了我们应用窗口的可见部分。
                view.getGlobalVisibleRect(windowRect)

                val componentBounds = layoutCoordinates.boundsInWindow()

                // 检查组件的垂直边界是否与窗口的可见垂直边界重叠。
                // 这是检查可滚动列表中可见性的一个简单而有效的方法。
                val newVisibility = componentBounds.top < windowRect.bottom && componentBounds.bottom > windowRect.top

                if (newVisibility != isVisible) {
                    isVisible = newVisibility
                }
            }
            .drawWithContent {
                // 仅当可组合项可见时才绘制内容。
                if (isVisible) {
                    drawContent()
                }
            }
}

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
        val typeface: Typeface
    )
    
    private val paintCache = ConcurrentHashMap<PaintKey, android.graphics.Paint>()
    private val textPaintCache = ConcurrentHashMap<PaintKey, TextPaint>()
    
    fun getPaint(color: Color, textSize: Float, typeface: Typeface): android.graphics.Paint {
        val key = PaintKey(color.toArgb(), textSize, typeface)
        return paintCache.getOrPut(key) {
            android.graphics.Paint().apply {
                this.color = key.colorArgb
                this.textSize = textSize
                this.isAntiAlias = true
                this.typeface = key.typeface
            }
        }
    }
    
    fun getTextPaint(color: Color, textSize: Float, typeface: Typeface): TextPaint {
        val key = PaintKey(color.toArgb(), textSize, typeface)
        return textPaintCache.getOrPut(key) {
            TextPaint().apply {
                this.color = key.colorArgb
                this.textSize = textSize
                this.isAntiAlias = true
                this.typeface = key.typeface
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
        val width: Int,
        val typeface: Typeface
    )
    
    private val cache = LruCache<LayoutKey, StaticLayout>(100)
    
    fun getLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        color: Color,
        typeface: Typeface
    ): StaticLayout {
        val key = LayoutKey(
            text = text,
            colorArgb = color.toArgb(),
            textSize = paint.textSize,
            width = width,
            typeface = paint.typeface
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
        val y: Float,
        val text: CharSequence? = null // 添加字段以存储Spannable
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
                modifier = modifier,
                onLinkClick = onLinkClick
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
    modifier: Modifier = Modifier,
    onLinkClick: ((String) -> Unit)?
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    val fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
    val resolver = LocalFontFamilyResolver.current

    val normalTypeface = remember(resolver, fontFamily) {
        (resolver.resolve(fontFamily = fontFamily, fontWeight = FontWeight.Normal).value as? android.graphics.Typeface)
            ?: android.graphics.Typeface.DEFAULT
    }
    val boldTypeface = remember(resolver, fontFamily) {
        (resolver.resolve(fontFamily = fontFamily, fontWeight = FontWeight.Bold).value as? android.graphics.Typeface)
            ?: android.graphics.Typeface.DEFAULT_BOLD
    }

    BoxWithConstraints(modifier = modifier) {
        val availableWidthPx = with(density) { maxWidth.toPx() }.toInt()
        
        // 使用 node.content.length 作为 key 来感知内容变化
        // 这样在流式渲染时，内容追加会触发重新计算
        val contentLength = node.content.length
        
        // 计算布局和绘制指令
        val (totalHeight, drawInstructions) = remember(contentLength, textColor, availableWidthPx, node.type, normalTypeface, boldTypeface) {
            calculateLayout(
                node = node,
                textColor = textColor,
                primaryColor = primaryColor,
                bodyMediumSize = bodyMediumSize,
                headlineLargeSize = headlineLargeSize,
                headlineMediumSize = headlineMediumSize,
                headlineSmallSize = headlineSmallSize,
                titleLargeSize = titleLargeSize,
                titleMediumSize = titleMediumSize,
                titleSmallSize = titleSmallSize,
                normalTypeface = normalTypeface,
                boldTypeface = boldTypeface,
                density = density,
                availableWidthPx = availableWidthPx
            )
        }
        
        // 使用单个 Canvas 绘制所有内容
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { totalHeight.toDp() })
                .pointerInput(drawInstructions, onLinkClick) {
                    detectTapGestures { offset ->
                        drawInstructions.forEach { instruction ->
                            if (instruction is DrawInstruction.TextLayout) {
                                val layout = instruction.layout
                                val text = instruction.text
                                if (text is Spanned) {
                                    val bounds = android.graphics.RectF(
                                        instruction.x,
                                        instruction.y,
                                        instruction.x + layout.width,
                                        instruction.y + layout.height
                                    )
                                    if (bounds.contains(offset.x, offset.y)) {
                                        val relativeX = offset.x - instruction.x
                                        val relativeY = offset.y - instruction.y
                                        val line = layout.getLineForVertical(relativeY.toInt())
                                        val lineOffset = layout.getOffsetForHorizontal(line, relativeX)

                                        val spans = text.getSpans(lineOffset, lineOffset, URLSpan::class.java)
                                        spans.firstOrNull()?.let { span ->
                                            onLinkClick?.invoke(span.url)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
    primaryColor: Color,
    bodyMediumSize: TextUnit,
    headlineLargeSize: TextUnit,
    headlineMediumSize: TextUnit,
    headlineSmallSize: TextUnit,
    titleLargeSize: TextUnit,
    titleMediumSize: TextUnit,
    titleSmallSize: TextUnit,
    normalTypeface: Typeface,
    boldTypeface: Typeface,
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
            
            // 减小标题字号：使用更小一级的字体
            val fontSize = when (level) {
                1 -> headlineMediumSize  // 原：headlineLargeSize
                2 -> headlineSmallSize   // 原：headlineMediumSize
                3 -> titleLargeSize      // 原：headlineSmallSize
                4 -> titleMediumSize     // 原：titleLargeSize
                5 -> titleSmallSize      // 原：titleMediumSize
                else -> bodyMediumSize   // 原：titleSmallSize
            }
            
            // 增大上下间距，提高可读性
            val topPadding = when (level) {
                1 -> 12f  // 原：8f
                2 -> 10f  // 原：6f
                3 -> 8f   // 原：4f
                else -> 6f // 原：3f
            } * density.density
            
            val bottomPadding = when (level) {
                1, 2 -> 4f  // 原：2f
                else -> 2f  // 原：1f
            }
            val bottomPaddingPx = bottomPadding * density.density
            
            currentY += topPadding
            
            val textSizePx = with(density) { fontSize.toPx() }
            val textPaint = PaintCache.getTextPaint(textColor, textSizePx, boldTypeface)

            val layout = if (node.children.isNotEmpty()) {
                val spannable = buildSpannableFromChildren(node.children, textColor, primaryColor)
                createStaticLayout(spannable, textPaint, availableWidthPx)
            } else {
                LayoutCache.getLayout(headerText, textPaint, availableWidthPx, textColor, boldTypeface)
            }
            
            instructions.add(DrawInstruction.TextLayout(layout, 0f, currentY, layout.text))
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
            val boldPaint = PaintCache.getPaint(textColor, textSizePx, boldTypeface)
            val textPaint = PaintCache.getTextPaint(textColor, textSizePx, normalTypeface)
            
            // 测量标记宽度
            val markerWidth = boldPaint.measureText("$numberStr.")
            val contentX = startPadding + markerWidth + markerEndPadding
            
            // 绘制标记
            val markerY = currentY + textSizePx
            instructions.add(DrawInstruction.Text("$numberStr.", startPadding, markerY, boldPaint))
            
            // 使用 StaticLayout 绘制内容（支持自动换行）
            val contentWidth = availableWidthPx - contentX.toInt()
            
            val layout = if (node.children.isNotEmpty()) {
                val spannable = buildSpannableFromChildren(node.children, textColor, primaryColor)
                createStaticLayout(spannable, textPaint, contentWidth)
            } else {
                LayoutCache.getLayout(itemText, textPaint, contentWidth, textColor, normalTypeface)
            }

            instructions.add(DrawInstruction.TextLayout(layout, contentX, currentY, layout.text))
            currentY += layout.height
            
            // 列表项底部间距
            currentY += 2f * density.density
        }
        
        MarkdownProcessorType.UNORDERED_LIST -> {
            val itemContent = content.trimAll()
            val markerMatch = Regex("""^[-*+]\s+""").find(itemContent)
            val itemText = markerMatch?.let { itemContent.substring(it.range.last + 1) } ?: itemContent
            
            val startPadding = 4f * density.density
            val markerEndPadding = 4f * density.density
            
            val textSizePx = with(density) { bodyMediumSize.toPx() }
            val boldPaint = PaintCache.getPaint(textColor, textSizePx, boldTypeface)
            val textPaint = PaintCache.getTextPaint(textColor, textSizePx, normalTypeface)
            
            // 测量标记宽度
            val markerWidth = boldPaint.measureText("•")
            val contentX = startPadding + markerWidth + markerEndPadding
            
            // 绘制标记
            val markerY = currentY + textSizePx + (1f * density.density)
            instructions.add(DrawInstruction.Text("•", startPadding, markerY, boldPaint))
            
            // 使用 StaticLayout 绘制内容（支持自动换行）
            val contentWidth = availableWidthPx - contentX.toInt()
            
            val layout = if (node.children.isNotEmpty()) {
                val spannable = buildSpannableFromChildren(node.children, textColor, primaryColor)
                createStaticLayout(spannable, textPaint, contentWidth)
            } else {
                LayoutCache.getLayout(itemText, textPaint, contentWidth, textColor, normalTypeface)
            }
            instructions.add(DrawInstruction.TextLayout(layout, contentX, currentY, layout.text))
            currentY += layout.height
            
            // 列表项底部间距
            currentY += 2f * density.density
        }
        
        MarkdownProcessorType.PLAIN_TEXT -> {
            if (content.trimAll().isEmpty()) return Pair(0f, emptyList())
            
            val textSizePx = with(density) { bodyMediumSize.toPx() }
            val textPaint = PaintCache.getTextPaint(textColor, textSizePx, normalTypeface)
            
            val layout = if (node.children.isNotEmpty()) {
                val spannable = buildSpannableFromChildren(node.children, textColor, primaryColor)
                createStaticLayout(spannable, textPaint, availableWidthPx)
            } else {
                LayoutCache.getLayout(content.trimAll(), textPaint, availableWidthPx, textColor, normalTypeface)
            }

            instructions.add(DrawInstruction.TextLayout(layout, 0f, currentY, layout.text))
            currentY += layout.height
            
            // 段落底部间距
            currentY += 6f * density.density
        }
        
        else -> {
            // 其他类型暂不处理
        }
    }
    
    return Pair(currentY, instructions)
}


/**
 * 从 MarkdownNode 的子节点构建 SpannableStringBuilder
 */
private fun buildSpannableFromChildren(
    children: List<MarkdownNode>,
    textColor: Color,
    primaryColor: Color
): SpannableStringBuilder {
    val builder = SpannableStringBuilder()
    children.forEach { child ->
        val content = child.content.toString()
        when (child.type) {
            MarkdownProcessorType.LINK -> {
                val linkText = extractLinkText(content)
                val linkUrl = extractLinkUrl(content)
                val start = builder.length
                builder.append(linkText)
                val end = builder.length
                builder.setSpan(URLSpan(linkUrl), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(ForegroundColorSpan(primaryColor.toArgb()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            MarkdownProcessorType.BOLD -> {
                val start = builder.length
                builder.append(content)
                val end = builder.length
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            MarkdownProcessorType.ITALIC -> {
                val start = builder.length
                builder.append(content)
                val end = builder.length
                builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            else -> { // PLAIN_TEXT, INLINE_CODE etc.
                builder.append(content)
            }
        }
    }
    return builder
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

    val fontFamily = MaterialTheme.typography.bodyMedium.fontFamily
    val resolver = LocalFontFamilyResolver.current
    val typeface = remember(resolver, fontFamily, fontWeight) {
        (resolver.resolve(fontFamily = fontFamily, fontWeight = fontWeight).value as? android.graphics.Typeface)
            ?: if (fontWeight == FontWeight.Bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    BoxWithConstraints(modifier = modifier) {
        val availableWidthPx = with(density) { maxWidth.toPx() }.toInt()
        val textSizePx = with(density) { fontSize.toPx() }
        
        val textPaint = remember(textColor, textSizePx, typeface) {
            PaintCache.getTextPaint(textColor, textSizePx, typeface)
        }
        
        val layout = remember(text, textPaint, availableWidthPx, textColor, typeface) {
            LayoutCache.getLayout(text, textPaint, availableWidthPx, textColor, typeface)
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

/**
 * 直接创建 StaticLayout (用于Spannable, 不走缓存)
 */
private fun createStaticLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout {
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
