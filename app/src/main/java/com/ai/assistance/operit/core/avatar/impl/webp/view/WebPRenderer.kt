package com.ai.assistance.operit.core.avatar.impl.webp.view

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.Log
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.core.avatar.common.control.AvatarController
import com.ai.assistance.operit.core.avatar.common.model.IFrameSequenceAvatarModel
import com.ai.assistance.operit.core.avatar.impl.webp.control.WebPAvatarController
import java.nio.ByteBuffer

/**
 * A Composable function responsible for rendering a WebP avatar.
 * It uses Android's ImageDecoder to display animated WebP files from assets.
 *
 * @param modifier The modifier to be applied to the view.
 * @param model The frame sequence avatar model containing the animation path.
 * @param controller The avatar controller that manages the avatar's state. It must be a
 *   [WebPAvatarController] for this renderer to function.
 * @param onError A callback for handling rendering errors.
 */
@Composable
fun WebPRenderer(
    modifier: Modifier,
    model: IFrameSequenceAvatarModel,
    controller: AvatarController,
    onError: (String) -> Unit
) {
    // This renderer requires a specific controller implementation
    val webpController = controller as? WebPAvatarController
        ?: throw IllegalArgumentException("WebPRenderer requires a WebPAvatarController")

    val context = LocalContext.current

    // Listen to controller state changes to get the current animation path
    val controllerState by webpController.state.collectAsState()
    val currentModel by webpController.currentModel.collectAsState()
    
    val animationPath = currentModel.animationPath

    val drawableState = remember(animationPath) {
        mutableStateOf<android.graphics.drawable.Drawable?>(null)
    }

    // Decode animation when path changes
    DisposableEffect(animationPath) {
        if (animationPath.isBlank()) {
            drawableState.value = null
            return@DisposableEffect onDispose { }
        }

        val assets = context.assets
        Log.d("WebPRenderer", "Decode start: $animationPath")
        
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                // Always decode from bytes to support compressed assets in APK
                val bytes = assets.open(animationPath).use { it.readBytes() }
                val src = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                val drawable = ImageDecoder.decodeDrawable(src)
                drawableState.value = drawable
                
                if (drawable is AnimatedImageDrawable) {
                    drawable.repeatCount = if (model.shouldLoop) {
                        AnimatedImageDrawable.REPEAT_INFINITE
                    } else {
                        model.repeatCount
                    }
                    drawable.start()
                    Log.d("WebPRenderer", "Animated start: $animationPath")
                } else {
                    Log.d("WebPRenderer", "Decoded non-animated drawable for: $animationPath")
                }
            } else {
                // API < 28: show first frame as static
                val input = assets.open(animationPath)
                val bmp = input.use { BitmapFactory.decodeStream(it) }
                drawableState.value = BitmapDrawable(context.resources, bmp)
                Log.d("WebPRenderer", "Static fallback (API<28): $animationPath")
            }
        } catch (e: Exception) {
            Log.e("WebPRenderer", "Decode error for $animationPath: ${e.message}", e)
            drawableState.value = null
            onError("Failed to load animation: ${e.message}")
        }

        onDispose {
            try {
                val d = drawableState.value
                if (Build.VERSION.SDK_INT >= 28 && d is AnimatedImageDrawable) {
                    d.stop()
                }
            } catch (_: Exception) {}
        }
    }

    val drawable = drawableState.value
    if (drawable != null) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setImageDrawable(drawable)
                }
            },
            update = { imageView ->
                imageView.setImageDrawable(drawable)
            }
        )
    }
} 