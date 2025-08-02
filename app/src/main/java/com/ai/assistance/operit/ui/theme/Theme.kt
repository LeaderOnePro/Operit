package com.ai.assistance.operit.ui.theme

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
        darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
        lightColorScheme(
                primary = Purple40,
                secondary = PurpleGrey40,
                tertiary = Pink40,

                /* Other default colors to override
                background = Color(0xFFFFFBFE),
                surface = Color(0xFFFFFBFE),
                onPrimary = Color.White,
                onSecondary = Color.White,
                onTertiary = Color.White,
                onBackground = Color(0xFF1C1B1F),
                onSurface = Color(0xFF1C1B1F),
                */
                )

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun OperitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferencesManager = remember { UserPreferencesManager(context) }
    val coroutineScope = rememberCoroutineScope()

    // 获取主题设置
    val useSystemTheme by preferencesManager.useSystemTheme.collectAsState(initial = true)
    val themeMode by
            preferencesManager.themeMode.collectAsState(
                    initial = UserPreferencesManager.THEME_MODE_LIGHT
            )
    val useCustomColors by preferencesManager.useCustomColors.collectAsState(initial = false)
    val customPrimaryColor by preferencesManager.customPrimaryColor.collectAsState(initial = null)
    val customSecondaryColor by
            preferencesManager.customSecondaryColor.collectAsState(initial = null)

    // 获取背景图片设置
    val useBackgroundImage by preferencesManager.useBackgroundImage.collectAsState(initial = false)
    val backgroundImageUri by preferencesManager.backgroundImageUri.collectAsState(initial = null)
    val backgroundImageOpacity by
            preferencesManager.backgroundImageOpacity.collectAsState(initial = 0.3f)

    // 获取背景媒体类型和视频设置
    val backgroundMediaType by
            preferencesManager.backgroundMediaType.collectAsState(
                    initial = UserPreferencesManager.MEDIA_TYPE_IMAGE
            )
    val videoBackgroundMuted by
            preferencesManager.videoBackgroundMuted.collectAsState(initial = true)
    val videoBackgroundLoop by preferencesManager.videoBackgroundLoop.collectAsState(initial = true)

    // 获取状态栏颜色设置
    val useCustomStatusBarColor by
            preferencesManager.useCustomStatusBarColor.collectAsState(initial = false)
    val customStatusBarColorValue by
            preferencesManager.customStatusBarColor.collectAsState(initial = null)
    val statusBarTransparent by
            preferencesManager.statusBarTransparent.collectAsState(initial = false)

    // 获取背景模糊设置
    val useBackgroundBlur by preferencesManager.useBackgroundBlur.collectAsState(initial = false)
    val backgroundBlurRadius by preferencesManager.backgroundBlurRadius.collectAsState(initial = 10f)

    // 确定是否使用暗色主题
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme =
            if (useSystemTheme) {
                systemDarkTheme
            } else {
                themeMode == UserPreferencesManager.THEME_MODE_DARK
            }

    // Dynamic color is available on Android 12+
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    // 基础主题色调
    var colorScheme =
            when {
                dynamicColor -> {
                    if (darkTheme) dynamicDarkColorScheme(context)
                    else dynamicLightColorScheme(context)
                }
                darkTheme -> DarkColorScheme
                else -> LightColorScheme
            }

    // 应用自定义颜色
    if (useCustomColors) {
        customPrimaryColor?.let { primaryArgb ->
            val primaryColor = Color(primaryArgb)
            val customTertiaryColor =
                    customSecondaryColor?.let { Color(it) } ?: colorScheme.tertiary

            colorScheme =
                    if (darkTheme) {
                        // 为暗色主题生成一套完整的颜色方案
                        generateDarkColorScheme(primaryColor, customTertiaryColor)
                    } else {
                        // 为亮色主题生成一套完整的颜色方案
                        generateLightColorScheme(primaryColor, customTertiaryColor)
                    }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            if (statusBarTransparent) {
                window.statusBarColor = Color.Transparent.toArgb()
                WindowCompat.setDecorFitsSystemWindows(window, false)
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val statusBarColor =
                        if (useCustomStatusBarColor && customStatusBarColorValue != null) {
                            customStatusBarColorValue!!.toInt()
                        } else {
                            colorScheme.primary.toArgb()
                        }
                window.statusBarColor = statusBarColor
                ViewCompat.getWindowInsetsController(view)?.isAppearanceLightStatusBars =
                        !isColorLight(Color(statusBarColor))
            }
        }
    }

    // 视频播放器状态
    val exoPlayer =
            remember(
                    useBackgroundImage,
                    backgroundImageUri,
                    backgroundMediaType,
                    videoBackgroundLoop,
                    videoBackgroundMuted
            ) {
                if (useBackgroundImage &&
                                backgroundImageUri != null &&
                                backgroundMediaType == UserPreferencesManager.MEDIA_TYPE_VIDEO
                ) {
                    ExoPlayer.Builder(context)
                            // Add memory optimizations
                            .setLoadControl(
                                    DefaultLoadControl.Builder()
                                            .setBufferDurationsMs(
                                                    5000,  // 最小缓冲时间，减少到5秒
                                                    10000, // 最大缓冲时间，减少到10秒
                                                    500,   // 回放所需的最小缓冲
                                                    1000   // 重新缓冲后回放所需的最小缓冲
                                            )
                                            .setTargetBufferBytes(5 * 1024 * 1024) // 将缓冲限制为5MB
                                            .setPrioritizeTimeOverSizeThresholds(true)
                                            .build()
                            )
                            .build()
                            .apply {
                                // 设置循环播放
                                repeatMode =
                                        if (videoBackgroundLoop) Player.REPEAT_MODE_ALL
                                        else Player.REPEAT_MODE_OFF
                                // 设置静音
                                volume = if (videoBackgroundMuted) 0f else 1f
                                playWhenReady = true

                                // 加载视频
                                try {
                                    val mediaItem = MediaItem.fromUri(Uri.parse(backgroundImageUri))
                                    setMediaItem(mediaItem)
                                    prepare()
                                } catch (e: Exception) {
                                    Log.e(
                                            "OperitTheme",
                                            "Error loading video background: ${e.message}",
                                            e
                                    )
                                    // Fallback to no background if video can't be loaded
                                    coroutineScope.launch {
                                        preferencesManager.saveThemeSettings(
                                                useBackgroundImage = false
                                        )
                                    }
                                }
                            }
                } else {
                    null
                }
            }

    // 释放ExoPlayer资源
    DisposableEffect(key1 = Unit) { 
        onDispose { 
            try {
                exoPlayer?.stop()
                exoPlayer?.clearMediaItems()
                exoPlayer?.release() 
            } catch (e: Exception) {
                Log.e("OperitTheme", "ExoPlayer释放错误", e)
            }
        } 
    }

    // 监听应用生命周期，控制视频播放
    if (exoPlayer != null) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        exoPlayer.pause()
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        exoPlayer.play()
                    }
                    else -> {}
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    // 应用主题和自定义背景
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // First, create a solid barrier background to prevent system theme colors from showing
        // through
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                .background(
                                        if (darkTheme) Color.Black else Color.White
                                ) // Solid barrier background
        )

        // 如果使用背景图片且URI不为空，则显示背景图片
        if (useBackgroundImage && backgroundImageUri != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                val uri = Uri.parse(backgroundImageUri)
                val coroutineScope = rememberCoroutineScope()

                // 根据媒体类型显示不同的背景
                if (backgroundMediaType == UserPreferencesManager.MEDIA_TYPE_IMAGE) {
                    // 显示背景图片
                    val painter =
                            rememberAsyncImagePainter(
                                    model = uri,
                                    error =
                                            rememberAsyncImagePainter(
                                                    if (darkTheme) Color.Black
                                                    else Color.White // Use solid colors for
                                                    // error fallback
                                                    )
                            )

                    // 监听图片加载失败时的逻辑
                    LaunchedEffect(painter) {
                        if (painter.state is AsyncImagePainter.State.Error) {
                            Log.e(
                                    "OperitTheme",
                                    "Error loading background image from URI: $backgroundImageUri"
                            )

                            // Check if it's a file:// URI pointing to our internal storage
                            if (uri.scheme == "file") {
                                val file = uri.path?.let { File(it) }
                                if (file == null || !file.exists()) {
                                    Log.e(
                                            "OperitTheme",
                                            "Internal file doesn't exist: ${file?.absolutePath}"
                                    )
                                } else {
                                    Log.e(
                                            "OperitTheme",
                                            "File exists but couldn't be loaded: ${file.absolutePath}, size: ${file.length()}"
                                    )
                                }
                            }

                            coroutineScope.launch {
                                preferencesManager.saveThemeSettings(useBackgroundImage = false)
                            }
                        }
                    }

                    // 显示背景图片
                    Image(
                            painter = painter,
                            contentDescription = "Background Image",
                            modifier =
                                    Modifier.fillMaxSize()
                                            .alpha(backgroundImageOpacity) // 使用设置的不透明度
                                            .then(
                                                    if (useBackgroundBlur)
                                                            Modifier.blur(radius = backgroundBlurRadius.dp)
                                                    else Modifier
                                            ),
                            contentScale = ContentScale.Crop
                    )
                } else {
                    // 显示背景视频
                    exoPlayer?.let { player ->
                        AndroidView(
                                factory = { ctx ->
                                    StyledPlayerView(ctx).apply {
                                        this.player = player
                                        useController = false
                                        layoutParams =
                                                ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                                        // Set scale type to fill the view without distortion
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        // Apply background color matching the current theme
                                        setBackgroundColor(
                                                if (darkTheme) android.graphics.Color.BLACK
                                                else android.graphics.Color.WHITE
                                        )
                                        // Create a semi-transparent overlay to control opacity
                                        foreground =
                                                android.graphics.drawable.ColorDrawable(
                                                        android.graphics.Color.argb(
                                                                ((1f - backgroundImageOpacity) *
                                                                                255)
                                                                        .toInt(),
                                                                if (darkTheme) 0 else 255, // R: Black(0) or White(255)
                                                                if (darkTheme) 0 else 255, // G: Black(0) or White(255)
                                                                if (darkTheme) 0 else 255  // B: Black(0) or White(255)
                                                        )
                                                )
                                    }
                                },
                                update = { view ->
                                    // Update player reference if needed
                                    if (view.player != player) {
                                        view.player = player
                                    }

                                    // Update the foreground transparency when opacity changes
                                    view.foreground =
                                            android.graphics.drawable.ColorDrawable(
                                                    android.graphics.Color.argb(
                                                            ((1f - backgroundImageOpacity) * 255)
                                                                    .toInt(),
                                                            if (darkTheme) 0 else 255, // R: Black(0) or White(255)
                                                            if (darkTheme) 0 else 255, // G: Black(0) or White(255)
                                                            if (darkTheme) 0 else 255  // B: Black(0) or White(255)
                                                    )
                                            )
                                },
                                modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 内容层 - Make sure it's not transparent
                MaterialTheme(
                        colorScheme =
                                colorScheme.copy(
                                        // Make surfaces more opaque
                                        surface = colorScheme.surface.copy(alpha = 1f),
                                        surfaceVariant =
                                                colorScheme.surfaceVariant.copy(alpha = 1f),
                                        background = colorScheme.background.copy(alpha = 1f),
                                        surfaceContainer =
                                                colorScheme.surfaceContainer.copy(alpha = 1f),
                                        surfaceContainerHigh =
                                                colorScheme.surfaceContainerHigh.copy(alpha = 1f),
                                        surfaceContainerHighest =
                                                colorScheme.surfaceContainerHighest.copy(
                                                        alpha = 1f
                                                ),
                                        surfaceContainerLow =
                                                colorScheme.surfaceContainerLow.copy(alpha = 1f),
                                        surfaceContainerLowest =
                                                colorScheme.surfaceContainerLowest.copy(alpha = 1f)
                                ),
                        typography = Typography,
                        content = content
                )
            }
        } else {
            // 不使用背景图片时，直接应用主题
            MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
        }
    }
}

/** 为亮色主题生成基于主色的完整颜色方案 */
private fun generateLightColorScheme(
        primaryColor: Color,
        secondaryColor: Color
): androidx.compose.material3.ColorScheme {
    // 生成主色衍生色
    val primaryContainer = lightenColor(primaryColor, 0.7f)
    val onPrimary = getContrastingTextColor(primaryColor)
    val onPrimaryContainer = getContrastingTextColor(primaryContainer)

    // 生成次色衍生色
    val secondaryContainer = lightenColor(secondaryColor, 0.7f)
    val onSecondary = getContrastingTextColor(secondaryColor)
    val onSecondaryContainer = getContrastingTextColor(secondaryContainer)

    // 生成三级色（基于主色和次色的混合）
    val tertiary = blendColors(primaryColor, secondaryColor, 0.5f)
    val tertiaryContainer = lightenColor(tertiary, 0.7f)
    val onTertiary = getContrastingTextColor(tertiary)
    val onTertiaryContainer = getContrastingTextColor(tertiaryContainer)

    // 为背景和表面使用浅灰色调
    val background = Color(0xFFF8F8F8)
    val surface = Color.White
    val surfaceVariant = Color(0xFFE7E0EB)

    // 确保文本颜色具有足够的对比度
    val onBackground = getContrastingTextColor(background, forceDark = true)
    val onSurface = getContrastingTextColor(surface, forceDark = true)
    val onSurfaceVariant = getContrastingTextColor(surfaceVariant, forceDark = true)

    // 添加Material 3所需的新表面相关颜色
    val surfaceTint = primaryColor // 使用主色作为表面色调
    val surfaceBright = Color(0xFFFCFCFC) // 亮表面色
    val surfaceDim = Color(0xFFECECEC) // 暗表面色
    val surfaceContainer = Color(0xFFF3F3F3) // 表面容器色
    val surfaceContainerHigh = Color(0xFFEBEBEB) // 高表面容器色
    val surfaceContainerHighest = Color(0xFFE3E3E3) // 最高表面容器色
    val surfaceContainerLow = Color(0xFFF7F7F7) // 低表面容器色
    val surfaceContainerLowest = Color(0xFFFFFFFF) // 最低表面容器色

    // 错误颜色保持一致
    val error = Color(0xFFB3261E)
    val errorContainer = Color(0xFFF9DEDC)
    val onError = Color.White
    val onErrorContainer = Color(0xFF410E0B)

    // 轮廓颜色
    val outline = Color(0xFF79747E)
    val outlineVariant = Color(0xFFCAC4D0)

    // 反向颜色
    val inverseSurface = Color(0xFF313033)
    val inverseOnSurface = Color(0xFFF4EFF4)
    val inversePrimary = lightenColor(primaryColor, 0.2f)

    // 创建完整的ColorScheme
    return androidx.compose.material3.ColorScheme(
            primary = primaryColor,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondaryColor,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            outlineVariant = outlineVariant,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            inversePrimary = inversePrimary,
            scrim = Color(0x99000000),
            surfaceTint = surfaceTint,
            surfaceBright = surfaceBright,
            surfaceDim = surfaceDim,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = surfaceContainerLowest
    )
}

/** 为暗色主题生成基于主色的完整颜色方案 */
private fun generateDarkColorScheme(
        primaryColor: Color,
        secondaryColor: Color
): androidx.compose.material3.ColorScheme {
    // 暗色主题中基础色需要变亮一些
    val adjustedPrimaryColor = lightenColor(primaryColor, 0.2f)
    val adjustedSecondaryColor = lightenColor(secondaryColor, 0.2f)

    // 生成主色衍生色
    val primaryContainer = darkenColor(primaryColor, 0.3f)
    val onPrimary = getContrastingTextColor(adjustedPrimaryColor)
    val onPrimaryContainer = getContrastingTextColor(primaryContainer, forceLight = true)

    // 生成次色衍生色
    val secondaryContainer = darkenColor(secondaryColor, 0.3f)
    val onSecondary = getContrastingTextColor(adjustedSecondaryColor)
    val onSecondaryContainer = getContrastingTextColor(secondaryContainer, forceLight = true)

    // 生成三级色（基于主色和次色的混合）
    val tertiary = blendColors(adjustedPrimaryColor, adjustedSecondaryColor, 0.5f)
    val tertiaryContainer = darkenColor(tertiary, 0.3f)
    val onTertiary = getContrastingTextColor(tertiary)
    val onTertiaryContainer = getContrastingTextColor(tertiaryContainer, forceLight = true)

    // 为背景和表面使用深色
    val background = Color(0xFF1C1B1F)
    val surface = Color(0xFF121212)
    val surfaceVariant = Color(0xFF49454F)

    // 确保文本颜色具有足够的对比度
    val onBackground = getContrastingTextColor(background, forceLight = true)
    val onSurface = getContrastingTextColor(surface, forceLight = true)
    val onSurfaceVariant = getContrastingTextColor(surfaceVariant, forceLight = true)

    // 添加Material 3所需的新表面相关颜色
    val surfaceTint = adjustedPrimaryColor // 使用调整后的主色作为表面色调
    val surfaceBright = Color(0xFF3B383C) // 亮表面色（暗色主题中稍微亮一点）
    val surfaceDim = Color(0xFF121212) // 暗表面色（暗色主题中最暗）
    val surfaceContainer = Color(0xFF211F26) // 表面容器色
    val surfaceContainerHigh = Color(0xFF2B2930) // 高表面容器色
    val surfaceContainerHighest = Color(0xFF36343B) // 最高表面容器色
    val surfaceContainerLow = Color(0xFF1D1B20) // 低表面容器色
    val surfaceContainerLowest = Color(0xFF0F0D13) // 最低表面容器色

    // 错误颜色调整为暗色主题
    val error = Color(0xFFF2B8B5)
    val errorContainer = Color(0xFF8C1D17)
    val onError = Color(0xFF601410)
    val onErrorContainer = Color(0xFFF9DEDC)

    // 轮廓颜色
    val outline = Color(0xFF938F96)
    val outlineVariant = Color(0xFF444147)

    // 反向颜色
    val inverseSurface = Color(0xFFE6E1E5)
    val inverseOnSurface = Color(0xFF313033)
    val inversePrimary = darkenColor(primaryColor, 0.2f)

    // 创建完整的ColorScheme
    return androidx.compose.material3.ColorScheme(
            primary = adjustedPrimaryColor,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = adjustedSecondaryColor,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            outlineVariant = outlineVariant,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            inversePrimary = inversePrimary,
            scrim = Color(0x99000000),
            surfaceTint = surfaceTint,
            surfaceBright = surfaceBright,
            surfaceDim = surfaceDim,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = surfaceContainerLowest
    )
}

/** Add a new helper function to determine appropriate text color based on background color */
private fun getContrastingTextColor(
        backgroundColor: Color,
        forceDark: Boolean = false,
        forceLight: Boolean = false
): Color {
    // If forced, return the specified color
    if (forceDark) return Color.Black
    if (forceLight) return Color.White

    // Calculate color contrast and return appropriate color
    // Using luminance formula from Web Content Accessibility Guidelines (WCAG)
    val luminance =
            0.299 * backgroundColor.red +
                    0.587 * backgroundColor.green +
                    0.114 * backgroundColor.blue

    // Use a threshold of 0.5 for deciding between white and black text
    // Higher threshold (e.g., 0.6) would use white text more often
    return if (luminance > 0.5) Color.Black else Color.White
}

/** 使颜色变亮 */
private fun lightenColor(color: Color, factor: Float): Color {
    val r = color.red + (1f - color.red) * factor
    val g = color.green + (1f - color.green) * factor
    val b = color.blue + (1f - color.blue) * factor
    return Color(r, g, b, color.alpha)
}

/** 使颜色变暗 */
private fun darkenColor(color: Color, factor: Float): Color {
    val r = color.red * (1f - factor)
    val g = color.green * (1f - factor)
    val b = color.blue * (1f - factor)
    return Color(r, g, b, color.alpha)
}

/** 混合两种颜色 */
private fun blendColors(color1: Color, color2: Color, ratio: Float): Color {
    val r = color1.red * (1 - ratio) + color2.red * ratio
    val g = color1.green * (1 - ratio) + color2.green * ratio
    val b = color1.blue * (1 - ratio) + color2.blue * ratio
    return Color(r, g, b)
}

/** 判断颜色是否较浅 */
private fun isColorLight(color: Color): Boolean {
    // 计算颜色亮度 (0.0-1.0)
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return luminance > 0.5
}

/** 判断颜色是否较深 */
private fun isColorDark(color: Color): Boolean {
    return !isColorLight(color)
}
