package com.ai.assistance.operit.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.floating.FloatingMode

/**
 * Voice Assistant Widget using Glance (Jetpack Compose for Widgets)
 * 
 * This widget provides a quick launcher for the voice assistant fullscreen mode.
 * Users can add it to their home screen and tap to instantly launch the voice assistant.
 */
class VoiceAssistantGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            VoiceAssistantWidgetContent(context)
        }
    }
}

@Composable
fun VoiceAssistantWidgetContent(context: Context) {
    GlanceTheme {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(
                    // 使用半透明背景，适配深色/浅色主题
                    ColorProvider(
                        day = Color(0xCC2196F3), // 蓝色，70% 不透明度
                        night = Color(0xCC1976D2)  // 深蓝色，70% 不透明度
                    )
                )
                .padding(16.dp)
                .clickable {
                    // 创建启动 MainActivity 的 Intent，并指示它启动全屏语音模式
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                        putExtra("LAUNCH_VOICE_ASSISTANT", true)
                        putExtra("INITIAL_MODE", FloatingMode.FULLSCREEN.name)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    
                    // 启动 MainActivity
                    if (intent != null) {
                        context.startActivity(intent)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier.fillMaxSize()
            ) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                
                // 麦克风图标
                Image(
                    provider = ImageProvider(R.drawable.ic_microphone),
                    contentDescription = "语音助手",
                    modifier = GlanceModifier.size(48.dp)
                )
                
                Spacer(modifier = GlanceModifier.height(12.dp))
                
                // 标题文字
                Text(
                    text = "Operit",
                    style = TextStyle(
                        fontSize = 16.sp,
                        color = ColorProvider(Color.White)
                    )
                )
                
                Spacer(modifier = GlanceModifier.height(4.dp))
                
                // 副标题
                Text(
                    text = "语音助手",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = ColorProvider(Color.White.copy(alpha = 0.9f))
                    )
                )
            }
        }
    }
}

