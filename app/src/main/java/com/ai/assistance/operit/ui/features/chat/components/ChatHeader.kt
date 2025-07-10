package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ChatHeader(
        showChatHistorySelector: Boolean,
        onToggleChatHistorySelector: () -> Unit,
        currentChatTitle: String?,
        modifier: Modifier = Modifier,
        onLaunchFloatingWindow: () -> Unit = {},
        isFloatingMode: Boolean = false
) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier
        ) {
                Box(
                        modifier =
                                Modifier.size(32.dp)
                                        .background(
                                                color =
                                                        if (showChatHistorySelector)
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.15f)
                                                        else Color.Transparent,
                                                shape = CircleShape
                                        )
                ) {
                        IconButton(
                                onClick = onToggleChatHistorySelector,
                                modifier = Modifier.matchParentSize()
                        ) {
                                Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription =
                                                if (showChatHistorySelector) "隐藏历史" else "显示历史",
                                        tint =
                                                if (showChatHistorySelector)
                                                        MaterialTheme.colorScheme.primary
                                                else
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.7f
                                                        ),
                                        modifier = Modifier.size(20.dp)
                                )
                        }
                }

                Box(
                        modifier =
                                Modifier.size(32.dp)
                                        .background(
                                                color =
                                                        if (isFloatingMode)
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.15f)
                                                        else Color.Transparent,
                                                shape = CircleShape
                                        )
                ) {
                        IconButton(
                                onClick = onLaunchFloatingWindow,
                                modifier = Modifier.matchParentSize()
                        ) {
                                Icon(
                                        imageVector = Icons.Default.PictureInPicture,
                                        contentDescription =
                                                if (isFloatingMode) "关闭悬浮窗" else "开启悬浮窗",
                                        tint =
                                                if (isFloatingMode)
                                                        MaterialTheme.colorScheme.primary
                                                else
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.7f
                                                        ),
                                        modifier = Modifier.size(20.dp)
                                )
                        }
                }
        }
}
