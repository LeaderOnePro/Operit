package com.ai.assistance.operit.ui.features.chat.components.part

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Web
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 简洁样式的工具调用显示组件 使用箭头图标+工具名+参数的简洁行样式 */
@Composable
fun CompactToolDisplay(
        toolName: String,
        params: String = "",
        textColor: Color,
        modifier: Modifier = Modifier
) {
    // 弹窗状态
    var showDetailDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val hasParams = params.isNotBlank()

    // 显示详细内容的弹窗
    if (showDetailDialog && hasParams) {
        ToolParamsDetailDialog(
                toolName = toolName,
                params = params,
                onDismiss = { showDetailDialog = false },
                onCopy = { clipboardManager.setText(AnnotatedString(params)) }
        )
    }

    Row(
            modifier =
                    modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = hasParams) {
                                if (hasParams) showDetailDialog = true
                            }
                            .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // 工具图标
        Icon(
                imageVector = getToolIcon(toolName),
                contentDescription = "工具调用",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 工具名称
        Text(
                text = toolName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.widthIn(min = 80.dp, max = 120.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
        )

        // 参数内容（如果有）
        if (params.isNotBlank()) {
            Text(
                    text = params,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 卡片式工具显示组件 用于显示较长内容的工具调用，支持流式渲染，美化版 */
@Composable
fun DetailedToolDisplay(
        toolName: String,
        params: String = "",
        textColor: Color,
        modifier: Modifier = Modifier
) {
    // 弹窗状态
    var showDetailDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val hasParams = params.isNotBlank()

    // 显示详细内容的弹窗
    if (showDetailDialog && hasParams) {
        ToolParamsDetailDialog(
                toolName = toolName,
                params = params,
                onDismiss = { showDetailDialog = false },
                onCopy = { clipboardManager.setText(AnnotatedString(params)) }
        )
    }

    Card(
            modifier =
                    modifier.fillMaxWidth().padding(top = 4.dp).clickable(enabled = hasParams) {
                        if (hasParams) showDetailDialog = true
                    },
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
            border =
                    BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
            shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 工具标题行
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
            ) {
                // 工具图标 - 与CompactToolDisplay保持一致的大小和位置
                Icon(
                        imageVector = getToolIcon(toolName),
                        contentDescription = "工具调用",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 工具名称
                Text(
                        text = toolName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.weight(1f))

                // 参数行数指示
                if (hasParams) {
                    val lineCount = remember(params) { params.lines().size }
                    Text(
                            text = "$lineCount 行",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // 参数内容 - 使用代码风格显示
            if (params.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))

                // 按行拆分参数文本，并用remember缓存，仅在params改变时重新计算
                val lines = remember(params) { params.lines() }

                // 创建LazyListState以控制滚动
                val listState = rememberLazyListState()

                // 当内容更新时，自动滚动到底部
                LaunchedEffect(lines.size) {
                    if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
                }

                // 使用有高度限制的代码显示区域
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .heightIn(min = 10.dp, max = 200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.5f
                                                )
                                        )
                ) {
                    // 显示带行号的代码内容
                    CodeContentWithLineNumbers(
                            lines = lines,
                            textColor = textColor,
                            listState = listState,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/** 显示带行号的代码内容 */
@Composable
private fun CodeContentWithLineNumbers(
        lines: List<String>,
        textColor: Color,
        listState: LazyListState,
        modifier: Modifier = Modifier
) {
    val isXml =
            remember(lines) { lines.any { it.trim().startsWith("<") && it.trim().endsWith(">") } }

    LazyColumn(modifier = modifier, state = listState) {
        itemsIndexed(items = lines, key = { index, _ -> index }) { index, line ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                // 行号列，使用固定宽度以保证代码对齐
                Box(
                        modifier = Modifier.width(40.dp).padding(end = 8.dp),
                        contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                            text = "${index + 1}",
                            style =
                                    MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                    ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }

                // 代码内容列 - 移除水平滚动，改为自动换行
                Box(modifier = Modifier.weight(1f)) {
                    // 当行太长时，高亮解析会很卡，所以设置一个阈值
                    val lineLengthLimit = 300
                    if (isXml && line.length < lineLengthLimit) {
                        // XML内容使用语法高亮显示
                        FormattedXmlText(text = line, modifier = Modifier.padding(vertical = 2.dp))
                    } else {
                        // 普通文本或过长的XML文本（不进行高亮）
                        Text(
                                text = line,
                                style =
                                        MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                        ),
                                color = textColor.copy(alpha = 0.8f),
                                softWrap = true
                        )
                    }
                }
            }
        }
    }
}

/** XML语法高亮文本 */
@Composable
private fun FormattedXmlText(text: String, modifier: Modifier = Modifier) {
    val formattedText = buildAnnotatedString {
        val trimmedText = text.trim()

        // 简单的XML语法高亮
        when {
            // XML标签
            trimmedText.startsWith("<") && trimmedText.contains(">") -> {
                val parts = trimmedText.split("<", ">", "=", "\"")
                var inTag = false
                var inAttr = false

                for (i in trimmedText.indices) {
                    val char = trimmedText[i]
                    when {
                        char == '<' -> {
                            inTag = true
                            withStyle(SpanStyle(color = Color(0xFF9C27B0))) { // 紫色
                                append(char)
                            }
                        }
                        char == '>' -> {
                            inTag = false
                            withStyle(SpanStyle(color = Color(0xFF9C27B0))) { // 紫色
                                append(char)
                            }
                        }
                        char == '=' -> {
                            inAttr = true
                            withStyle(SpanStyle(color = Color(0xFF757575))) { // 灰色
                                append(char)
                            }
                        }
                        char == '"' -> {
                            if (inAttr) {
                                withStyle(SpanStyle(color = Color(0xFF4CAF50))) { // 绿色
                                    append(char)
                                }
                            } else {
                                append(char)
                            }
                            inAttr = !inAttr
                        }
                        inTag && char.isLetterOrDigit() -> {
                            withStyle(SpanStyle(color = Color(0xFF2196F3))) { // 蓝色
                                append(char)
                            }
                        }
                        inAttr -> {
                            withStyle(SpanStyle(color = Color(0xFF4CAF50))) { // 绿色
                                append(char)
                            }
                        }
                        else -> {
                            append(char)
                        }
                    }
                }
            }
            // 普通文本
            else -> {
                append(trimmedText)
            }
        }
    }

    Text(
            text = formattedText,
            style =
                    MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                    ),
            softWrap = true,
            modifier = modifier
    )
}

/** 工具参数详情弹窗 美观的弹窗显示完整的工具参数内容 */
@Composable
private fun ToolParamsDetailDialog(
        toolName: String,
        params: String,
        onDismiss: () -> Unit,
        onCopy: () -> Unit
) {
    Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 标题栏
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    // 工具图标
                    Icon(
                            imageVector = getToolIcon(toolName),
                            contentDescription = "工具调用",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // 工具名称
                    Text(
                            text = "$toolName 调用参数",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // 复制按钮
                    IconButton(onClick = onCopy) {
                        Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "复制参数",
                                tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 分隔线
                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                Spacer(modifier = Modifier.height(16.dp))

                // 参数内容
                Box(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .heightIn(min = 50.dp, max = 300.dp)
                                        .background(
                                                color =
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                                .copy(alpha = 0.5f),
                                                shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(8.dp)
                ) {
                    // 按行拆分参数文本
                    val lines = remember(params) { params.lines() }

                    // 为弹窗内的代码视图创建独立的LazyListState
                    val dialogListState = rememberLazyListState()

                    // 当内容更新时，自动滚动到底部
                    LaunchedEffect(lines.size) {
                        if (lines.isNotEmpty()) dialogListState.animateScrollToItem(lines.lastIndex)
                    }

                    // 显示带行号的代码内容
                    CodeContentWithLineNumbers(
                            lines = lines,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            listState = dialogListState,
                            modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 关闭按钮
                Button(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                )
                ) { Text("关闭") }
            }
        }
    }
}

/** 根据工具名称选择合适的图标 */
private fun getToolIcon(toolName: String): ImageVector {
    return when {
        // 文件工具
        toolName.contains("file") || toolName.contains("read") || toolName.contains("write") ->
                Icons.Default.FileOpen

        // 搜索工具
        toolName.contains("search") || toolName.contains("find") || toolName.contains("query") ->
                Icons.Default.Search

        // 命令行工具
        toolName.contains("terminal") ||
                toolName.contains("exec") ||
                toolName.contains("command") ||
                toolName.contains("shell") -> Icons.Default.Terminal

        // 代码工具
        toolName.contains("code") || toolName.contains("ffmpeg") -> Icons.Default.Code

        // 网络工具
        toolName.contains("http") || toolName.contains("web") || toolName.contains("visit") ->
                Icons.Default.Web

        // 默认图标
        else -> Icons.Default.ArrowForward
    }
}
