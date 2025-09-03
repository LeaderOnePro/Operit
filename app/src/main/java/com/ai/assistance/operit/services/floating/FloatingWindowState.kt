package com.ai.assistance.operit.services.floating

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.floating.FloatingMode

class FloatingWindowState(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("floating_chat_prefs", Context.MODE_PRIVATE)
    private val screenWidthDp: Dp
    private val screenHeightDp: Dp

    // Window position
    var x: Int = 0
    var y: Int = 100

    // Window size
    val windowWidth = mutableStateOf(300.dp)
    val windowHeight = mutableStateOf(400.dp)
    val windowScale = mutableStateOf(1.0f)
    var lastWindowScale: Float = 1.0f

    // Mode state
    val currentMode = mutableStateOf(FloatingMode.WINDOW)
    var previousMode: FloatingMode = FloatingMode.WINDOW
    val ballSize = mutableStateOf(40.dp)

    // DragonBones pet mode lock state
    var isPetModeLocked = mutableStateOf(false)

    // Transition state
    var lastWindowPositionX: Int = 0
    var lastWindowPositionY: Int = 0
    var lastBallPositionX: Int = 0
    var lastBallPositionY: Int = 0
    var isTransitioning = false
    val transitionDebounceTime = 500L // 防抖时间

    init {
        val displayMetrics = context.resources.displayMetrics
        screenWidthDp = (displayMetrics.widthPixels / displayMetrics.density).dp
        screenHeightDp = (displayMetrics.heightPixels / displayMetrics.density).dp
        restoreState()
    }

    fun saveState() {
        prefs.edit().apply {
            putInt("window_x", x)
            putInt("window_y", y)
            putFloat("window_width", windowWidth.value.value.coerceAtLeast(200f))
            putFloat("window_height", windowHeight.value.value.coerceAtLeast(250f))
            putString("current_mode", currentMode.value.name)
            putString("previous_mode", previousMode.name)
            putFloat("window_scale", windowScale.value.coerceIn(0.5f, 1.0f))
            putFloat("last_window_scale", lastWindowScale.coerceIn(0.5f, 1.0f))
            apply()
        }
    }

    fun restoreState() {
        x = prefs.getInt("window_x", 0)
        y = prefs.getInt("window_y", 100)

        // 读取已保存的宽高，并限制在屏幕范围内
        val savedWidth = Dp(prefs.getFloat("window_width", 300f))
        val savedHeight = Dp(prefs.getFloat("window_height", 400f))

        windowWidth.value = savedWidth.coerceIn(200.dp, screenWidthDp * 0.95f)
        windowHeight.value = savedHeight.coerceIn(250.dp, screenHeightDp * 0.95f)

        val currentModeName =
            prefs.getString("current_mode", FloatingMode.WINDOW.name) ?: FloatingMode.WINDOW.name
        val previousModeName =
            prefs.getString("previous_mode", FloatingMode.WINDOW.name) ?: FloatingMode.WINDOW.name

        currentMode.value =
            try {
                FloatingMode.valueOf(
                    if (currentModeName == "LIVE2D") "DragonBones" else currentModeName
                )
            } catch (e: IllegalArgumentException) {
                FloatingMode.WINDOW // Fallback to default
            }

        previousMode =
            try {
                FloatingMode.valueOf(
                    if (previousModeName == "LIVE2D") "DragonBones" else previousModeName
                )
            } catch (e: IllegalArgumentException) {
                FloatingMode.WINDOW // Fallback to default
            }

        windowScale.value = prefs.getFloat("window_scale", 1.0f).coerceIn(0.5f, 1.0f)
        lastWindowScale = prefs.getFloat("last_window_scale", 1.0f).coerceIn(0.5f, 1.0f)
    }
} 