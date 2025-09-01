package com.ai.assistance.operit.ui.features.chat.components.style.bubble

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.common.markdown.StreamMarkdownRenderer
import com.ai.assistance.operit.ui.features.chat.components.part.CustomXmlRenderer
import com.ai.assistance.operit.util.markdown.toCharStream
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale

@Composable
fun BubbleAiMessageComposable(
    message: ChatMessage,
    backgroundColor: Color,
    textColor: Color,
    onLinkClick: ((String) -> Unit)? = null,
    isHidden: Boolean = false
) {
    val context = LocalContext.current
    val preferencesManager = remember { UserPreferencesManager(context) }
    val showThinkingProcess by preferencesManager.showThinkingProcess.collectAsState(initial = true)
    val showStatusTags by preferencesManager.showStatusTags.collectAsState(initial = true)
    val aiAvatarUri by preferencesManager.customAiAvatarUri.collectAsState(initial = null)
    val avatarShapePref by preferencesManager.avatarShape.collectAsState(initial = UserPreferencesManager.AVATAR_SHAPE_CIRCLE)
    val avatarCornerRadius by preferencesManager.avatarCornerRadius.collectAsState(initial = 8f)

    val avatarShape = remember(avatarShapePref, avatarCornerRadius) {
        if (avatarShapePref == UserPreferencesManager.AVATAR_SHAPE_SQUARE) {
            RoundedCornerShape(avatarCornerRadius.dp)
        } else {
            CircleShape
        }
    }

    val xmlRenderer = remember(showThinkingProcess, showStatusTags) {
        CustomXmlRenderer(
            showThinkingProcess = showThinkingProcess,
            showStatusTags = showStatusTags
        )
    }
    val rememberedOnLinkClick = remember(context, onLinkClick) {
        onLinkClick ?: { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    }

    val targetAlpha = if (isHidden) 0f else 1f
    val targetOffsetY = if (isHidden) 100f else 0f

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 300)
    )
    val offsetY by animateFloatAsState(
        targetValue = targetOffsetY,
        animationSpec = tween(durationMillis = 300)
    )

    val imageUrl = remember(message.content, message.contentStream) {
        if (message.contentStream == null) {
            val regex = """^\s*!\[[^\]]*\]\(([^)]+)\)\s*$""".toRegex()
            regex.find(message.content)?.groups?.get(1)?.value
        } else {
            null
        }
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .alpha(alpha)
            .offset(y = offsetY.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Avatar
        if (!aiAvatarUri.isNullOrEmpty()) {
            Image(
                painter = rememberAsyncImagePainter(model = Uri.parse(aiAvatarUri)),
                contentDescription = "AI Avatar",
                modifier = Modifier
                    .size(32.dp)
                    .clip(avatarShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.Assistant,
                contentDescription = "AI Avatar",
                modifier = Modifier
                    .size(32.dp)
                    .clip(avatarShape),
                tint = MaterialTheme.colorScheme.secondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        if (imageUrl != null) {
            AsyncImage(
                model = Uri.parse(imageUrl),
                contentDescription = "Image from AI",
                modifier = Modifier
                    .padding(end = 64.dp)
                    .heightIn(max = 80.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            // Message bubble
            Surface(
                modifier = Modifier
                    .padding(end = 64.dp)
                    .defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp),
                color = backgroundColor,
                tonalElevation = 2.dp
            ) {
                // 使用 message.timestamp 作为 key，确保在重组期间，
                // 只要是同一条消息，StreamMarkdownRenderer就不会被销毁和重建。
                key(message.timestamp) {
                    val stream = message.contentStream
                    if (stream != null) {
                        val charStream = remember(stream) { stream.toCharStream() }
                        StreamMarkdownRenderer(
                            markdownStream = charStream,
                            textColor = textColor,
                            backgroundColor = backgroundColor,
                            onLinkClick = rememberedOnLinkClick,
                            xmlRenderer = xmlRenderer,
                            modifier = Modifier.padding(12.dp)
                        )
                    } else {
                        // 对于已完成的静态消息，使用 content 参数的渲染器以支持Markdown
                        StreamMarkdownRenderer(
                            content = message.content,
                            textColor = textColor,
                            backgroundColor = backgroundColor,
                            onLinkClick = rememberedOnLinkClick,
                            xmlRenderer = xmlRenderer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}
