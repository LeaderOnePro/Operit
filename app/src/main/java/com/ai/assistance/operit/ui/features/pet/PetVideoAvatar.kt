package com.ai.assistance.operit.ui.features.pet

import android.graphics.BitmapFactory
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.ImageDecoder
import android.os.Build
import android.util.Log
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer

///**
// * Animated WebP avatar for the desktop pet.
// * Decodes from android assets (assets/pets/emoji/*.webp) using ImageDecoder.
// * On API < 28, falls back to first frame (static BitmapDrawable).
// */
@Composable
fun PetVideoAvatar(
    assetPath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val drawableState: MutableState<android.graphics.drawable.Drawable?> = remember(assetPath) {
        mutableStateOf(null)
    }

    // Decode on composition
    DisposableEffect(assetPath) {
        val assets = context.assets
        Log.d("PetWebPAvatar", "Decode start: $assetPath")
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                // Always decode from bytes to support compressed assets in APK
                val bytes = assets.open(assetPath).use { it.readBytes() }
                val src = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                val drawable = ImageDecoder.decodeDrawable(src)
                drawableState.value = drawable
                if (drawable is AnimatedImageDrawable) {
                    drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    drawable.start()
                    Log.d("PetWebPAvatar", "Animated start: $assetPath")
                } else {
                    Log.d("PetWebPAvatar", "Decoded non-animated drawable for: $assetPath")
                }
            } else {
                // API < 28: show first frame as static
                val input = assets.open(assetPath)
                val bmp = input.use { BitmapFactory.decodeStream(it) }
                drawableState.value = BitmapDrawable(context.resources, bmp)
                Log.d("PetWebPAvatar", "Static fallback (API<28): $assetPath")
            }
        } catch (e: Exception) {
            Log.e("PetWebPAvatar", "Decode error for $assetPath: ${e.message}", e)
            drawableState.value = null
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
