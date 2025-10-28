package com.ai.assistance.operit.ui.floating.ui.ball

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.floating.FloatContext
import com.ai.assistance.operit.ui.floating.FloatingMode
import kotlin.math.*

/**
 * 绘制流动的彩色光斑（类似Siri的色块）
 */
private fun DrawScope.drawColorBlob(
    center: Offset,
    radius: Float,
    angle: Float,
    distance: Float,
    color: Color,
    size: Float
) {
    val rad = angle * PI / 180f
    val blobCenter = Offset(
        center.x + distance * cos(rad).toFloat(),
        center.y + distance * sin(rad).toFloat()
    )
    
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = 0.8f),
                color.copy(alpha = 0.5f),
                color.copy(alpha = 0.2f),
                Color.Transparent
            ),
            center = blobCenter,
            radius = radius * size
        ),
        center = blobCenter,
        radius = radius * size,
        blendMode = BlendMode.Screen
    )
}

/** 
 * 渲染悬浮窗的聊天球模式界面 - Siri风格动感球体（Canvas绘制版）
 * 使用Canvas直接绘制，实现更真实的Siri效果
 * 点击进入聊天窗口模式
 */
@Composable
fun FloatingChatBallMode(floatContext: FloatContext) {
    // Siri配色 - 精简为4个主色调（蓝、紫、粉、青）
    val mainColor = Color(0xFF0A84FF)      // 主蓝色
    val accentColor1 = Color(0xFFBF5AF2)   // 紫色
    val accentColor2 = Color(0xFFFF375F)   // 粉红色
    val accentColor3 = Color(0xFF00D4FF)   // 青色
    
    // 淡出动画状态
    val isFadingOut = floatContext.windowState?.ballExploding?.value ?: false
    val fadeOutProgress = remember { Animatable(0f) }
    
    // 监听淡出触发
    LaunchedEffect(isFadingOut) {
        if (isFadingOut) {
            fadeOutProgress.snapTo(0f)
            fadeOutProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 100, easing = LinearEasing)
            )
        } else {
            fadeOutProgress.snapTo(0f)
        }
    }
    
    // 动画
    val infiniteTransition = rememberInfiniteTransition(label = "siri")
    
    // 慢速旋转 - 更优雅
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    // 柔和呼吸
    val breathe by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )
    
    // 外圈音波 - 第一层
    val ripple1Scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1Scale"
    )
    
    val ripple1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple1Alpha"
    )
    
    // 外圈音波 - 第二层
    val ripple2Scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(833)
        ),
        label = "ripple2Scale"
    )
    
    val ripple2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(833)
        ),
        label = "ripple2Alpha"
    )
    
    // 外圈音波 - 第三层
    val ripple3Scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(1666)
        ),
        label = "ripple3Scale"
    )
    
    val ripple3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(1666)
        ),
        label = "ripple3Alpha"
    )
    
    Box(
        modifier = Modifier
            .size(floatContext.ballSize * 3.0f)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        floatContext.saveWindowState?.invoke()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        floatContext.onMove(
                            dragAmount.x,
                            dragAmount.y,
                            floatContext.windowScale
                        )
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        floatContext.onModeChange(FloatingMode.WINDOW)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(floatContext.ballSize * 3.0f)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension / 2.0f
            
            // 淡出效果：只改变透明度，不改变缩放
            val fadeAlpha = if (isFadingOut) 1f - fadeOutProgress.value else 1f
            
            if (fadeAlpha <= 0.01f) {
                // 淡出完成，不绘制任何内容
                return@Canvas
            }
            
            // 1. 外圈音波扩散（3层，简洁的圆环）
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        mainColor.copy(alpha = ripple3Alpha * 0.15f * fadeAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * ripple3Scale
                ),
                center = center,
                radius = baseRadius * ripple3Scale
            )
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        accentColor1.copy(alpha = ripple2Alpha * 0.2f * fadeAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * ripple2Scale
                ),
                center = center,
                radius = baseRadius * ripple2Scale
            )
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        accentColor3.copy(alpha = ripple1Alpha * 0.25f * fadeAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * ripple1Scale
                ),
                center = center,
                radius = baseRadius * ripple1Scale
            )
            
            // 2. 底部光晕（柔和的蓝紫光）
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        mainColor.copy(alpha = 0.3f * fadeAlpha),
                        accentColor1.copy(alpha = 0.2f * fadeAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * breathe * 0.7f
                ),
                center = center,
                radius = baseRadius * breathe * 0.7f,
                blendMode = BlendMode.Screen
            )
            
            // 3. 流动的彩色光斑（4个色块，慢速旋转）
            // 主蓝色光斑
            drawColorBlob(
                center = center,
                radius = baseRadius,
                angle = rotation,
                distance = baseRadius * 0.2f * breathe,
                color = mainColor.copy(alpha = fadeAlpha),
                size = 0.7f
            )
            
            // 紫色光斑（相位差90度）
            drawColorBlob(
                center = center,
                radius = baseRadius,
                angle = rotation + 90f,
                distance = baseRadius * 0.25f * breathe,
                color = accentColor1.copy(alpha = fadeAlpha),
                size = 0.65f
            )
            
            // 粉红色光斑（相位差180度）
            drawColorBlob(
                center = center,
                radius = baseRadius,
                angle = rotation + 180f,
                distance = baseRadius * 0.22f * breathe,
                color = accentColor2.copy(alpha = fadeAlpha),
                size = 0.6f
            )
            
            // 青色光斑（相位差270度）
            drawColorBlob(
                center = center,
                radius = baseRadius,
                angle = rotation + 270f,
                distance = baseRadius * 0.23f * breathe,
                color = accentColor3.copy(alpha = fadeAlpha),
                size = 0.68f
            )
            
            // 4. 中心明亮核心
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f * fadeAlpha),
                        Color.White.copy(alpha = 0.6f * fadeAlpha),
                        Color.White.copy(alpha = 0.3f * fadeAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 0.45f
                ),
                center = center,
                radius = baseRadius * 0.45f
            )
            
            // 5. 细微的边缘高光
            drawCircle(
                color = Color.White.copy(alpha = 0.15f * fadeAlpha),
                center = center,
                radius = baseRadius * breathe,
                style = Stroke(width = 2f)
            )
        }
    }
}
