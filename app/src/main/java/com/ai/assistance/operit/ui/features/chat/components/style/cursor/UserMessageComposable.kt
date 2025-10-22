package com.ai.assistance.operit.ui.features.chat.components.style.cursor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatMessage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource

/**
 * A composable function for rendering user messages in a Cursor IDE style. Supports text selection
 * and copy on long press.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserMessageComposable(message: ChatMessage, backgroundColor: Color, textColor: Color) {
        val clipboardManager = LocalClipboardManager.current
        val haptic = LocalHapticFeedback.current
        val scope = rememberCoroutineScope()

        // 添加状态控制内容预览
        val showContentPreview = remember { mutableStateOf(false) }
        val selectedAttachmentContent = remember { mutableStateOf("") }
        val selectedAttachmentName = remember { mutableStateOf("") }

        // Parse message content to separate text and attachments
        val parseResult = remember(message.content) { parseMessageContent(message.content) }
        val textContent = parseResult.processedText
        val trailingAttachments = parseResult.trailingAttachments
        val replyInfo = parseResult.replyInfo

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                // Display reply info above attachments if present
                replyInfo?.let { reply ->
                        Surface(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                        ) {
                                Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Icon(
                                                imageVector = Icons.Default.Reply,
                                                contentDescription = "回复",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp)
                                        )
                                        
                                        Spacer(modifier = Modifier.width(4.dp))
                                        
                                        Text(
                                                text = "${reply.sender}: ${reply.content}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                        )
                                }
                        }
                }

                // Display trailing attachments above the message bubble
                if (trailingAttachments.isNotEmpty()) {
                        // Display attachment row above the bubble
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                trailingAttachments.forEach { attachment ->
                                        AttachmentTag(
                                                attachment = attachment,
                                                textColor = textColor,
                                                backgroundColor = backgroundColor,
                                                onClick = { attachmentData ->
                                                        // 当点击附件标签时，显示内容预览
                                                        if (attachmentData.content.isNotEmpty()) {
                                                                selectedAttachmentContent.value =
                                                                        attachmentData.content
                                                                selectedAttachmentName.value =
                                                                        attachmentData.filename
                                                                showContentPreview.value = true
                                                        } else if (attachmentData.id.startsWith("/storage/")) {
                                                                scope.launch(Dispatchers.IO) {
                                                                        try {
                                                                                val fileContent =
                                                                                        File(attachmentData.id)
                                                                                                .readText()
                                                                                withContext(Dispatchers.Main) {
                                                                                        selectedAttachmentContent
                                                                                                .value =
                                                                                                fileContent
                                                                                        selectedAttachmentName
                                                                                                .value =
                                                                                                attachmentData
                                                                                                        .filename
                                                                                        showContentPreview
                                                                                                .value = true
                                                                                }
                                                                        } catch (e: Exception) {
                                                                                android.util.Log.e(
                                                                                        "UserMessageComposable",
                                                                                        "Error reading attachment file",
                                                                                        e
                                                                                )
                                                                        }
                                                                }
                                                        }
                                                }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                }
                        }
                }

                // Message bubble
                Card(
                        modifier =
                                Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = backgroundColor),
                        shape = RoundedCornerShape(8.dp)
                ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(
                                        text = "Prompt",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = textColor.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                )

                                // Display main text content with inline attachments
                                Text(text = textContent, color = textColor,style = MaterialTheme.typography.bodyMedium)
                        }
                }
        }

        // 内容预览对话框
        if (showContentPreview.value) {
                Dialog(onDismissRequest = { showContentPreview.value = false }) {
                        Surface(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 4.dp
                        ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                        // 头部
                                        Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Row(
                                                        verticalAlignment =
                                                                Alignment.CenterVertically,
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(8.dp)
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.Code,
                                                                contentDescription = null,
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                        )
                                                        Text(
                                                                text = selectedAttachmentName.value,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleMedium,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface
                                                        )
                                                }

                                                IconButton(
                                                        onClick = {
                                                                showContentPreview.value = false
                                                        }
                                                ) {
                                                        Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "关闭",
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurface
                                                        )
                                                }
                                        }

                                        Divider(modifier = Modifier.padding(vertical = 8.dp))

                                        // 内容区域
                                        Box(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .wrapContentHeight(
                                                                        align = Alignment.Top
                                                                )
                                                                .weight(1f, fill = false)
                                                                .border(
                                                                        width = 1.dp,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .surfaceVariant,
                                                                        shape =
                                                                                RoundedCornerShape(
                                                                                        4.dp
                                                                                )
                                                                )
                                                                .padding(8.dp)
                                                                .verticalScroll(
                                                                        rememberScrollState()
                                                                )
                                        ) {
                                                Text(
                                                        text = selectedAttachmentContent.value,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontFamily = FontFamily.Monospace
                                                )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // 复制按钮
                                        Button(
                                                onClick = {
                                                        clipboardManager.setText(
                                                                AnnotatedString(
                                                                        selectedAttachmentContent
                                                                                .value
                                                                )
                                                        )
                                                        showContentPreview.value = false
                                                },
                                                modifier = Modifier.align(Alignment.End)
                                        ) { Text("复制内容") }
                                }
                        }
                }
        }
}

/** Result of parsing message content, containing processed text and trailing attachments */
data class MessageParseResult(
        val processedText: String,
        val trailingAttachments: List<AttachmentData>,
        val replyInfo: ReplyInfo? = null // 新增回复信息
)

/** Data class for reply information */
data class ReplyInfo(
    val sender: String,
    val timestamp: Long,
    val content: String
)

/**
 * Parses the message content to extract text and attachments Keeps inline attachments as @filename
 * in the text Extracts trailing attachments that appear at the end of the message
 */
private fun parseMessageContent(content: String): MessageParseResult {
        // First, strip out any <memory> tags so they are not displayed in the UI.
        var cleanedContent =
                content.replace(Regex("<memory>.*?</memory>", RegexOption.DOT_MATCHES_ALL), "").trim()

        // Extract reply information
        val replyRegex = Regex("<reply_to\\s+sender=\"([^\"]+)\"\\s+timestamp=\"([^\"]+)\">([^<]*)</reply_to>")
        val replyMatch = replyRegex.find(cleanedContent)
        val replyInfo = replyMatch?.let { match ->
            val fullContent = match.groupValues[3]
            // 指示语，用于从回复内容中提取纯净的预览文本
            val instruction = "用户正在回复你之前的这条消息："
            val displayContent = fullContent
                .removePrefix(instruction)
                .trim()
                .removeSurrounding("\"")

            ReplyInfo(
                sender = match.groupValues[1],
                timestamp = match.groupValues[2].toLongOrNull() ?: 0L,
                content = displayContent
            )
        }
        
        // Remove reply tag from content
        cleanedContent = replyMatch?.let { 
            cleanedContent.replace(it.value, "").trim() 
        } ?: cleanedContent

        val workspaceAttachments = mutableListOf<AttachmentData>()
        // Extract workspace context as a special attachment
        val workspaceRegex =
                Regex("<workspace_attachment>.*?</workspace_attachment>", RegexOption.DOT_MATCHES_ALL)
        val workspaceMatch = workspaceRegex.find(cleanedContent)
        if (workspaceMatch != null) {
                val workspaceContent = workspaceMatch.value
                workspaceAttachments.add(
                        AttachmentData(
                                id = "workspace_context",
                                filename = "工作区状态",
                                type = "application/vnd.workspace-context+xml",
                                size = workspaceContent.length.toLong(),
                                content = workspaceContent
                        )
                )
                cleanedContent = cleanedContent.replace(workspaceContent, "").trim()
        }

        val attachments = mutableListOf<AttachmentData>()
        val trailingAttachments = mutableListOf<AttachmentData>()
        val messageText = StringBuilder()

        // 先用简单的分割方式检测有没有附件标签
        if (!cleanedContent.contains("<attachment")) {
                return MessageParseResult(cleanedContent, workspaceAttachments, replyInfo)
        }

        try {
                // Enhanced regex pattern to find attachments in both formats:
                // 1. New format (paired tags): <attachment ...>content</attachment>
                // 2. Old format (self-closing): <attachment ... content="..." />
                // 注意：优先匹配新格式（配对标签），回退到旧格式（自闭合标签）
                val pairedTagPattern =
                        "<attachment\\s+id=\"([^\"]+)\"\\s+filename=\"([^\"]+)\"\\s+type=\"([^\"]+)\"(?:\\s+size=\"([^\"]+)\")?\\s*>([\\s\\S]*?)</attachment>".toRegex()
                val selfClosingPattern =
                        "<attachment\\s+id=\"([^\"]+)\"\\s+filename=\"([^\"]+)\"\\s+type=\"([^\"]+)\"(?:\\s+size=\"([^\"]+)\")?(?:\\s+content=\"(.*?)\")?\\s*/>".toRegex(
                                RegexOption.DOT_MATCHES_ALL
                        )

                // Try to find matches with both patterns
                val pairedMatches = pairedTagPattern.findAll(cleanedContent).toList()
                val selfClosingMatches = selfClosingPattern.findAll(cleanedContent).toList()
                
                // Combine and sort all matches by position
                val allMatches = (pairedMatches.map { it to true } + selfClosingMatches.map { it to false })
                        .sortedBy { it.first.range.first }
                
                // Remove overlapping matches (prefer paired tag format)
                val matches = mutableListOf<Pair<MatchResult, Boolean>>()
                var lastEnd = -1
                allMatches.forEach { (match, isPaired) ->
                        if (match.range.first > lastEnd) {
                                matches.add(match to isPaired)
                                lastEnd = match.range.last
                        }
                }
                
                if (matches.isEmpty()) {
                        return MessageParseResult(cleanedContent, workspaceAttachments, replyInfo)
                }

                // Determine which attachments form a contiguous block at the end
                val trailingAttachmentIndices = mutableSetOf<Int>()
                if (matches.isNotEmpty()) {
                        val contentAfterLast = cleanedContent.substring(matches.last().first.range.last + 1)
                        if (contentAfterLast.isBlank()) {
                                trailingAttachmentIndices.add(matches.size - 1)
                                for (i in matches.size - 2 downTo 0) {
                                        val textBetween = cleanedContent.substring(matches[i].first.range.last + 1, matches[i + 1].first.range.first)
                                        if (textBetween.isBlank()) {
                                                trailingAttachmentIndices.add(i)
                                        } else {
                                                break
                                        }
                                }
                        }
                }

                // Process all attachments
                var lastIndex = 0
                matches.forEachIndexed { index, (matchResult, isPaired) ->
                        // Add text before this attachment
                        val startIndex = matchResult.range.first

                        // Extract attachment data
                        val id = matchResult.groupValues[1]
                        val filename = matchResult.groupValues[2]
                        val type = matchResult.groupValues[3]
                        val size = matchResult.groupValues[4].toLongOrNull() ?: 0L
                        // For paired tags, content is in group 5; for self-closing, it's also in group 5
                        val attachmentContent = matchResult.groupValues[5]

                        // Create attachment data object, including content if available
                        val attachment =
                                AttachmentData(
                                        id = id,
                                        filename = filename,
                                        type = type,
                                        size = size,
                                        content = attachmentContent
                                )

                        val isTrailingAttachment = trailingAttachmentIndices.contains(index)

                        // 特殊处理屏幕内容附件，始终将其作为trailing attachment
                        val isScreenContent =
                                (type == "text/json" && filename == "screen_content.json")

                        val shouldBeTrailing = isTrailingAttachment || isScreenContent

                        if (startIndex > lastIndex) {
                                val textBefore = cleanedContent.substring(lastIndex, startIndex)
                                // Only append text if it's before an inline attachment,
                                // or if it's before the very first trailing attachment.
                                if (!shouldBeTrailing || (trailingAttachmentIndices.isNotEmpty() && index == trailingAttachmentIndices.minOrNull())) {
                                        messageText.append(textBefore)
                                }
                        }

                        if (shouldBeTrailing) {
                                // This is a trailing attachment, extract it
                                trailingAttachments.add(attachment)
                        } else {
                                // This is an inline attachment, keep it in the text as @filename
                                messageText.append("@${filename}")
                                // Also add to general attachments list for reference
                                attachments.add(attachment)
                        }

                        lastIndex = matchResult.range.last + 1
                }

                // Add any remaining text if the last part of the message was not a trailing attachment
                if (lastIndex < cleanedContent.length) {
                        messageText.append(cleanedContent.substring(lastIndex))
                }

                trailingAttachments.addAll(0, workspaceAttachments)
                return MessageParseResult(messageText.toString(), trailingAttachments, replyInfo)
        } catch (e: Exception) {
                // 如果解析失败，返回原始内容
                android.util.Log.e("UserMessageComposable", "解析消息内容失败", e)
                return MessageParseResult(cleanedContent, workspaceAttachments, replyInfo)
        }
}

/** Data class for attachment information */
data class AttachmentData(
        val id: String,
        val filename: String,
        val type: String,
        val size: Long = 0,
        val content: String = "" // Added content field
)

/** Compact attachment tag component for displaying in user messages */
@Composable
private fun AttachmentTag(
        attachment: AttachmentData,
        textColor: Color,
        backgroundColor: Color,
        onClick: (AttachmentData) -> Unit = {}
) {
        // 根据附件类型选择图标
        val icon: ImageVector =
                when {
                        attachment.type.startsWith("image/") -> Icons.Default.Image
                        attachment.type == "text/json" && attachment.filename == "screen_content.json" ->
                                Icons.Default.ScreenshotMonitor
                        attachment.type == "application/vnd.workspace-context+xml" -> Icons.Default.Code
                        else -> Icons.Default.Description
                }

        // 根据附件类型调整显示标签
        val displayLabel =
                when {
                        attachment.type == "text/json" && attachment.filename == "screen_content.json" -> "屏幕内容"
                        attachment.type == "application/vnd.workspace-context+xml" -> "工作区"
                        else -> attachment.filename
                }

        Surface(
                modifier =
                        Modifier.height(24.dp)
                                .padding(vertical = 2.dp)
                                .clickable(
                                        enabled =
                                                attachment.content.isNotEmpty() ||
                                                        attachment.id.startsWith("/storage/"),
                                        onClick = { onClick(attachment) }
                                ),
                shape = RoundedCornerShape(12.dp),
                color = backgroundColor.copy(alpha = 0.5f)
        ) {
                Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = textColor.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                                text = displayLabel,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = textColor,
                                modifier = Modifier.widthIn(max = 120.dp)
                        )
                }
        }
}
