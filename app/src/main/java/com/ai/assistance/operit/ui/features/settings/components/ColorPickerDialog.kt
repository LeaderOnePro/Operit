package com.ai.assistance.operit.ui.features.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.theme.getTextColorForBackground
import com.ai.assistance.operit.ui.theme.isHighContrast
import com.github.skydoves.colorpicker.compose.*
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Material colors for the color picker
 */
val materialColors = listOf(
    // Material Design primary colors
    Color(0xFF6200EE), // Purple 500 (Material primary)
    Color(0xFF3700B3), // Purple 700 (Material primary variant)
    Color(0xFF03DAC6), // Teal 200 (Material secondary)
    Color(0xFF018786), // Teal 700 (Material secondary variant)
    Color(0xFF1976D2), // Blue 700
    Color(0xFF0D47A1), // Blue 900
    Color(0xFF1E88E5), // Blue 600

    // More Material colors with good contrast
    Color(0xFFD32F2F), // Red 700
    Color(0xFF7B1FA2), // Purple 700
    Color(0xFF388E3C), // Green 700
    Color(0xFFE64A19), // Deep Orange 700
    Color(0xFFF57C00), // Orange 700
    Color(0xFF5D4037), // Brown 700
    Color(0xFF455A64)  // Blue Grey 700
)

/**
 * Convert Color to HSV values
 */
fun Color.toHsv(): FloatArray {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    return hsv
}

/**
 * Create Color from HSV values
 */
fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val hsv = floatArrayOf(h, s, v)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Parse hex color string to Color
 */
fun parseHexColor(hex: String): Color? {
    return try {
        val cleanHex = hex.trim().removePrefix("#")
        when (cleanHex.length) {
            6 -> Color(android.graphics.Color.parseColor("#$cleanHex"))
            8 -> Color(android.graphics.Color.parseColor("#$cleanHex"))
            3 -> {
                // Convert 3-digit hex to 6-digit
                val r = cleanHex[0].toString().repeat(2)
                val g = cleanHex[1].toString().repeat(2)
                val b = cleanHex[2].toString().repeat(2)
                Color(android.graphics.Color.parseColor("#$r$g$b"))
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * A dialog with HSV color picker
 */
@Composable
fun ColorPickerDialog(
    showColorPicker: Boolean,
    currentColorPickerMode: String,
    primaryColorInput: Int,
    secondaryColorInput: Int,
    statusBarColorInput: Int,
    historyIconColorInput: Int,
    pipIconColorInput: Int,
    recentColors: List<Int>,
    onColorSelected:
            (
                    primaryColor: Int?,
                    secondaryColor: Int?,
                    statusBarColor: Int?,
                    historyIconColor: Int?,
                    pipIconColor: Int?
            ) -> Unit,
    onDismiss: () -> Unit
) {
    if (!showColorPicker) return

    val currentColorForPicker =
            when (currentColorPickerMode) {
                "primary" -> primaryColorInput
                "secondary" -> secondaryColorInput
                "statusBar" -> statusBarColorInput
                "historyIcon" -> historyIconColorInput
                "pipIcon" -> pipIconColorInput
                else -> primaryColorInput
            }
    val currentColor = Color(currentColorForPicker)

    // This is the definitive fix for the initial color bug.
    // We create a controller that is remembered based on the current color.
    // When the dialog is opened for a different color, a new controller is created
    // and initialized correctly.
    val pickerController = remember(currentColor) {
        ColorPickerController().apply {
            // Programmatically set the color of the controller upon creation.
            selectByColor(currentColor, fromUser = false)
        }
    }

    // The controller's selected color is the source of truth for our UI.
    val pickedColor by pickerController.selectedColor

    // Manual input states
    var inputMode by remember { mutableStateOf("HEX") } // HEX, RGB, HSV
    var hexInput by remember { mutableStateOf("") }
    var rgbR by remember { mutableStateOf("") }
    var rgbG by remember { mutableStateOf("") }
    var rgbB by remember { mutableStateOf("") }
    var hsvH by remember { mutableStateOf("") }
    var hsvS by remember { mutableStateOf("") }
    var hsvV by remember { mutableStateOf("") }
    
    val clipboardManager = LocalClipboardManager.current

    // Update input fields when picked color changes
    LaunchedEffect(pickedColor) {
        val color = pickedColor
        // Update HEX
        hexInput = String.format("#%06X", (0xFFFFFF and color.toArgb()))
        
        // Update RGB
        rgbR = (color.red * 255).toInt().toString()
        rgbG = (color.green * 255).toInt().toString()
        rgbB = (color.blue * 255).toInt().toString()
        
        // Update HSV
        val hsv = color.toHsv()
        hsvH = hsv[0].toInt().toString()
        hsvS = (hsv[1] * 100).toInt().toString()
        hsvV = (hsv[2] * 100).toInt().toString()
    }

    // Function to apply color from manual input
    fun applyManualColor() {
        val newColor = when (inputMode) {
            "HEX" -> parseHexColor(hexInput)
            "RGB" -> {
                try {
                    val r = rgbR.toIntOrNull()?.coerceIn(0, 255) ?: return
                    val g = rgbG.toIntOrNull()?.coerceIn(0, 255) ?: return
                    val b = rgbB.toIntOrNull()?.coerceIn(0, 255) ?: return
                    Color(r / 255f, g / 255f, b / 255f)
                } catch (e: Exception) { null }
            }
            "HSV" -> {
                try {
                    val h = hsvH.toFloatOrNull()?.coerceIn(0f, 360f) ?: return
                    val s = (hsvS.toFloatOrNull()?.coerceIn(0f, 100f) ?: return) / 100f
                    val v = (hsvV.toFloatOrNull()?.coerceIn(0f, 100f) ?: return) / 100f
                    hsvToColor(h, s, v)
                } catch (e: Exception) { null }
            }
            else -> null
        }
        
        newColor?.let { color ->
            pickerController.selectByColor(color, fromUser = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                    text =
                            when (currentColorPickerMode) {
                                "primary" -> "选择主色"
                                "secondary" -> "选择次色"
                                "statusBar" -> "选择状态栏颜色"
                                "historyIcon" -> "选择历史记录图标颜色"
                                "pipIcon" -> "选择悬浮窗图标颜色"
                                else -> "选择颜色"
                            },
                    style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Live color preview - use solid backgrounds
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Color sample
                        Box(
                            modifier = Modifier.size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(pickedColor)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(8.dp)
                                )
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Text preview
                        Column {
                            // Show contrast example
                            val textColor = getTextColorForBackground(pickedColor)
                            Surface(
                                modifier = Modifier.width(120.dp).height(40.dp),
                                color = pickedColor,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "示例文本",
                                        color = textColor,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            // Add contrast rating
                            val contrastRating =
                                if (isHighContrast(pickedColor)) "高对比度 ✓" else "低对比度 ⚠"
                            val contrastColor =
                                if (isHighContrast(pickedColor)) Color(0xFF388E3C)
                                else Color(0xFFD32F2F)

                            Text(
                                text = contrastRating,
                                style = MaterialTheme.typography.bodySmall,
                                color = contrastColor,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                // Manual input section
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "手动输入颜色",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Input mode selection
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("HEX", "RGB", "HSV").forEach { mode ->
                                FilterChip(
                                    onClick = { inputMode = mode },
                                    label = { Text(mode) },
                                    selected = inputMode == mode,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Input fields based on selected mode
                        when (inputMode) {
                            "HEX" -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = hexInput,
                                        onValueChange = { hexInput = it.uppercase() },
                                        label = { Text("HEX") },
                                        placeholder = { Text("#FF0000") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        trailingIcon = {
                                            IconButton(
                                                onClick = {
                                                    clipboardManager.getText()?.text?.let { text ->
                                                        hexInput = text.trim()
                                                        applyManualColor()
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.ContentPaste, "粘贴")
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(onClick = { applyManualColor() }) {
                                        Text("应用")
                                    }
                                }
                            }
                            "RGB" -> {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = rgbR,
                                            onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 3) rgbR = it },
                                            label = { Text("R") },
                                            placeholder = { Text("255") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                        )
                                        OutlinedTextField(
                                            value = rgbG,
                                            onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 3) rgbG = it },
                                            label = { Text("G") },
                                            placeholder = { Text("0") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                        )
                                        OutlinedTextField(
                                            value = rgbB,
                                            onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 3) rgbB = it },
                                            label = { Text("B") },
                                            placeholder = { Text("0") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                        )
                                    }
                                    TextButton(
                                        onClick = { applyManualColor() },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("应用 RGB")
                                    }
                                }
                            }
                            "HSV" -> {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = hsvH,
                                            onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 3) hsvH = it },
                                            label = { Text("H") },
                                            placeholder = { Text("360") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                        )
                                        OutlinedTextField(
                                            value = hsvS,
                                            onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 3) hsvS = it },
                                            label = { Text("S%") },
                                            placeholder = { Text("100") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                        )
                                        OutlinedTextField(
                                            value = hsvV,
                                            onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 3) hsvV = it },
                                            label = { Text("V%") },
                                            placeholder = { Text("100") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                        )
                                    }
                                    Text(
                                        text = "H: 0-360°, S: 0-100%, V: 0-100%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    TextButton(
                                        onClick = { applyManualColor() },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("应用 HSV")
                                    }
                                }
                            }
                        }
                    }
                }

                // Color display preview with alpha tiles
                AlphaTile(
                    modifier = Modifier.fillMaxWidth()
                        .height(60.dp)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    controller = pickerController
                )

                // HSV Color Picker
                HsvColorPicker(
                    modifier = Modifier.fillMaxWidth()
                        .height(220.dp)
                        .padding(vertical = 8.dp),
                    controller = pickerController,
                    onColorChanged = {
                        // Intentionally left blank. The controller updates its own state.
                        // We observe the state via `pickerController.selectedColor`.
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Brightness slider
                BrightnessSlider(
                    modifier = Modifier.fillMaxWidth()
                        .height(30.dp)
                        .padding(vertical = 4.dp),
                    controller = pickerController
                )

                // Alpha slider
                AlphaSlider(
                    modifier = Modifier.fillMaxWidth()
                        .height(30.dp)
                        .padding(vertical = 4.dp),
                    controller = pickerController,
                    tileOddColor = Color.White,
                    tileEvenColor = Color.LightGray
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Recent colors section
                if (recentColors.isNotEmpty()) {
                    Text(
                        text = "最近使用",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(
                            space = 8.dp,
                            alignment = Alignment.Start
                        )
                    ) {
                        recentColors.take(7).forEach { colorInt ->
                            val color = Color(colorInt)
                            PresetColorItem(color) {
                                pickerController.selectByColor(it, fromUser = true)
                            }
                        }
                    }
                    if (recentColors.size > 7) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(
                                space = 8.dp,
                                alignment = Alignment.Start
                            )
                        ) {
                            recentColors.drop(7).take(7).forEach { colorInt ->
                                val color = Color(colorInt)
                                PresetColorItem(color) {
                                    pickerController.selectByColor(it, fromUser = true)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Preset colors title
                Text(
                    text = "推荐颜色",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Preset colors grid
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    materialColors.take(7).forEach { color ->
                        PresetColorItem(color) {
                            pickerController.selectByColor(it, fromUser = true)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    materialColors.takeLast(7).forEach { color ->
                        PresetColorItem(color) {
                            pickerController.selectByColor(it, fromUser = true)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newColor = pickedColor.toArgb()
                    when (currentColorPickerMode) {
                        "primary" -> onColorSelected(newColor, null, null, null, null)
                        "secondary" -> onColorSelected(null, newColor, null, null, null)
                        "statusBar" -> onColorSelected(null, null, newColor, null, null)
                        "historyIcon" -> onColorSelected(null, null, null, newColor, null)
                        "pipIcon" -> onColorSelected(null, null, null, null, newColor)
                    }
                    onDismiss()
                },
                shape = RoundedCornerShape(8.dp)
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
} 