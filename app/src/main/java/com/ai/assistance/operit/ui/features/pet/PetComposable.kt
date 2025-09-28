package com.ai.assistance.operit.ui.features.pet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.pet.PetEmotion

/**
 * Minimal pet overlay UI: bubble with short text, mic button and close.
 * DragonBones integration can be added later as an avatar beside the bubble.
 */
@Composable
fun PetOverlay(
    text: String,
    isListening: Boolean,
    isThinking: Boolean,
    onMicClick: () -> Unit,

    onClose: () -> Unit,
    onDrag: (dx: Float, dy: Float, end: Boolean) -> Unit = { _, _, _ -> },
    emotion: PetEmotion = PetEmotion.IDLE,
    // When provided, renders a video avatar from android assets instead of the vector avatar
    videoAssetPath: String? = null,
    // Text input extensions
    showTextInput: Boolean = false,
    textInputValue: String = "",
    onTextInputClick: () -> Unit = {},
    onTextInputChange: (String) -> Unit = {},
    onSendText: () -> Unit = {},
    // Collapse toggle
    isCollapsed: Boolean = false,
    onCollapseToggle: () -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
        modifier = Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { _ -> /* drag start */ },
                onDragEnd = { onDrag(0f, 0f, true) },
                onDragCancel = { onDrag(0f, 0f, true) },
                onDrag = { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y, false)
                }
            )
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Animated avatar
            Box(
                modifier = Modifier
                    .size(96.dp)
            ) {
                // Always use media-based avatar; if path is null/blank, display nothing
                videoAssetPath?.takeIf { it.isNotBlank() }?.let { path ->
                    PetVideoAvatar(
                        assetPath = path,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // When collapsed, show an expand control over the avatar (aligned to bottom-end)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isCollapsed,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    IconButton(
                        onClick = onCollapseToggle,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "expand",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (!isCollapsed) Card(
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 200.dp, max = 360.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (text.isBlank()) "…" else text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onMicClick) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Pause else Icons.Default.Mic,
                                contentDescription = "mic",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onTextInputClick) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "input",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        AnimatedVisibility(visible = isThinking) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .height(8.dp)
                                        .widthIn(min = 80.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                )
                            }
                        }

                        IconButton(onClick = onCollapseToggle) {
                            Icon(
                                imageVector = Icons.Default.ExpandLess,
                                contentDescription = "collapse",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterVertically)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "close",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    AnimatedVisibility(visible = showTextInput) {
                        val focusRequester = remember { FocusRequester() }
                        val keyboard = LocalSoftwareKeyboardController.current
                        LaunchedEffect(showTextInput) {
                            if (showTextInput) {
                                // 延迟1帧以确保视图已附着
                                kotlinx.coroutines.yield()
                                focusRequester.requestFocus()
                                keyboard?.show()
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = textInputValue,
                                onValueChange = onTextInputChange,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester),
                                singleLine = true,
                                placeholder = { Text("输入消息…") }
                            )
                            IconButton(onClick = onSendText) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "send",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
