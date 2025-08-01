package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 消息编辑器组件，用于编辑包含XML标签的消息
 */
data class ParsedMessagePart(val type: PartType, val content: String, val tag: String? = null, val attributes: String? = null)
enum class PartType { TEXT, XML }

fun parseMessageContentForEditor(content: String): List<ParsedMessagePart> {
    val parts = mutableListOf<ParsedMessagePart>()
    // 支持带属性的标签
    val regex = "<([a-zA-Z0-9_-]+)([^>]*)>([\\s\\S]*?)</\\1>".toRegex(RegexOption.DOT_MATCHES_ALL)
    var lastIndex = 0

    regex.findAll(content).forEach { matchResult ->
        val startIndex = matchResult.range.first
        if (startIndex > lastIndex) {
            val textPart = content.substring(lastIndex, startIndex)
            if (textPart.isNotBlank()) {
                parts.add(ParsedMessagePart(PartType.TEXT, textPart, null, null))
            }
        }
        val tag = matchResult.groupValues[1]
        val attributes = matchResult.groupValues[2]
        val tagContent = matchResult.groupValues[3]
        parts.add(ParsedMessagePart(PartType.XML, tagContent, tag, attributes))
        lastIndex = matchResult.range.last + 1
    }

    if (lastIndex < content.length) {
        val trailingText = content.substring(lastIndex)
        if (trailingText.isNotBlank()) {
            parts.add(ParsedMessagePart(PartType.TEXT, trailingText, null, null))
        }
    }
    return parts
}

fun recomposeMessageFromParts(parts: List<ParsedMessagePart>): String {
    return parts.joinToString(separator = "") { part ->
        if (part.type == PartType.TEXT) {
            part.content
        } else {
            "<${part.tag}${part.attributes ?: ""}>${part.content}</${part.tag}>"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MessageEditor(
    editingMessageContent: MutableState<String>,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onResend: () -> Unit,
    showResendButton: Boolean
) {
    val initialParts = remember(editingMessageContent.value) {
        parseMessageContentForEditor(editingMessageContent.value)
    }
    var partsState by remember { mutableStateOf(initialParts) }
    var partToEdit by remember { mutableStateOf<Pair<Int, ParsedMessagePart>?>(null) }
    var showCreateTagDialog by remember { mutableStateOf(false) }
    var isRawEditMode by remember { mutableStateOf(false) }

    LaunchedEffect(partsState) {
        if (!isRawEditMode) {
            editingMessageContent.value = recomposeMessageFromParts(partsState)
        }
    }

    LaunchedEffect(isRawEditMode) {
        if (!isRawEditMode) {
            // Just switched from raw to visual editor, re-parse the content
            partsState = parseMessageContentForEditor(editingMessageContent.value)
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.9f)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (showResendButton) "编辑消息" else "修改记忆",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { isRawEditMode = !isRawEditMode }) {
                            Text(if (isRawEditMode) "可视化" else "纯文本")
                        }

                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "取消",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 450.dp)
                ) {
                    if (isRawEditMode) {
                        OutlinedTextField(
                            value = editingMessageContent.value,
                            onValueChange = { editingMessageContent.value = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            label = { Text("纯文本内容") },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f)
                            )
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            // Section label
                            Row(
                                modifier = Modifier.padding(bottom = 8.dp, top = 4.dp, start = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "内容片段",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Message parts
                            partsState.forEachIndexed { index, part ->
                                when (part.type) {
                                    PartType.TEXT -> {
                                        Box(modifier = Modifier.padding(bottom = 8.dp)) {
                                            OutlinedTextField(
                                                value = part.content,
                                                onValueChange = { newText ->
                                                    val updatedParts = partsState.toMutableList()
                                                    updatedParts[index] = part.copy(content = newText)
                                                    partsState = updatedParts
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth(),
                                                label = { Text("文本", style = MaterialTheme.typography.bodySmall) },
                                                placeholder = { Text("输入文本内容...") },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f)
                                                ),
                                                textStyle = MaterialTheme.typography.bodyMedium
                                            )
                                            ActionIconButton(
                                                icon = Icons.Default.Delete,
                                                contentDescription = "删除",
                                                onClick = {
                                                    val updatedParts = partsState.toMutableList()
                                                    updatedParts.removeAt(index)
                                                    partsState = updatedParts
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(top = 6.dp, end = 4.dp)
                                            )
                                        }
                                    }
                                    PartType.XML -> {
                                        XmlTagItem(
                                            part = part,
                                            onClick = { partToEdit = index to part },
                                            onDelete = {
                                                val updatedParts = partsState.toMutableList()
                                                updatedParts.removeAt(index)
                                                partsState = updatedParts
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }

                            // Add part buttons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                            ) {
                                // Add text button
                                OutlinedButton(
                                    onClick = { partsState = partsState + ParsedMessagePart(PartType.TEXT, "") },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "添加文本",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "添加文本",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                // Add tag button
                                OutlinedButton(
                                    onClick = { showCreateTagDialog = true },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Tag,
                                        contentDescription = "添加标签",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "添加标签",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "取消",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    if (showResendButton) {
                        OutlinedButton(
                            onClick = onSave,
                            shape = RoundedCornerShape(16.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                width = 1.dp
                            )
                        ) {
                            Text(
                                "保存",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = onResend,
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                "保存并重发",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = onSave,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                "更新记忆",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateTagDialog || partToEdit != null) {
        val editingPart = partToEdit?.second
        TagEditorDialog(
            part = editingPart,
            onDismiss = {
                partToEdit = null
                showCreateTagDialog = false
            },
            onSave = { updatedPart ->
                if (partToEdit != null) {
                    partsState = partsState.toMutableList().apply { set(partToEdit!!.first, updatedPart) }
                } else {
                    partsState = partsState + updatedPart
                }
                partToEdit = null
                showCreateTagDialog = false
            }
        )
    }
}

@Composable
private fun XmlTagItem(
    part: ParsedMessagePart,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotationState by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "rotation"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            // Tag header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = rememberRipple(bounded = true),
                        onClick = { expanded = !expanded }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = part.tag ?: "XML",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (part.attributes?.isNotBlank() == true) {
                        Text(
                            text = part.attributes,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionIconButton(
                        icon = Icons.Default.Edit,
                        contentDescription = "编辑",
                        onClick = onClick
                    )

                    ActionIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "删除",
                        onClick = onDelete
                    )

                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "展开",
                        modifier = Modifier
                            .rotate(rotationState)
                            .size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tag content
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = tween(150)) + expandVertically(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(animationSpec = tween(150))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = part.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagEditorDialog(
    part: ParsedMessagePart?,
    onDismiss: () -> Unit,
    onSave: (ParsedMessagePart) -> Unit
) {
    var tagName by remember { mutableStateOf(part?.tag ?: "") }
    var attributes by remember { mutableStateOf(part?.attributes ?: "") }
    var content by remember { mutableStateOf(part?.content ?: "") }
    val isNewTag = part == null

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isNewTag) "新建标签" else "编辑标签",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = if (isNewTag) "创建一个新的XML标签" else "修改XML标签内容",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.Tag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tag name
                OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it },
                    label = { Text("标签名", style=MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("例如: code, image, bold") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Attributes
                OutlinedTextField(
                    value = attributes,
                    onValueChange = { attributes = it },
                    label = { Text("属性 (可选)", style=MaterialTheme.typography.bodySmall) },
                    placeholder = { Text("例如: id=\"example\" class=\"test\"") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Content
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("内容", style=MaterialTheme.typography.bodySmall) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(16.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.dp
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "取消",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            onSave(ParsedMessagePart(PartType.XML, content, tagName, attributes))
                        },
                        enabled = tagName.isNotBlank(),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "保存",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
} 