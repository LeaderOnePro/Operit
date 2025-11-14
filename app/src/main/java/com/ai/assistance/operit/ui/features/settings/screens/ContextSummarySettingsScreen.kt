package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextSummarySettingsScreen(
        onBackPressed: () -> Unit
) {
        val context = LocalContext.current
        val apiPreferences = remember { ApiPreferences.getInstance(context) }
        val userPreferences = remember { UserPreferencesManager(context) }
        val scope = rememberCoroutineScope()
        val scrollState = rememberScrollState()

        // State for UI components - using String to hold input
        var contextLengthInput by remember { mutableStateOf("") }
        var maxContextLengthInput by remember { mutableStateOf("") }
        var enableMaxContextMode by remember { mutableStateOf(false) }
        var summaryTokenThresholdInput by remember { mutableStateOf("") }
        var enableSummary by remember { mutableStateOf(false) }
        var enableSummaryByMessageCount by remember { mutableStateOf(false) }
        var summaryMessageCountThresholdInput by remember { mutableStateOf("") }
        var maxFileSizeBytesInput by remember { mutableStateOf("") }
        var partSizeInput by remember { mutableStateOf("") }
        var maxTextResultLengthInput by remember { mutableStateOf("") }
        var maxHttpResponseLengthInput by remember { mutableStateOf("") }

        val hasBackgroundImage by userPreferences.useBackgroundImage.collectAsState(initial = false)

        // Load initial values once and convert to String
        LaunchedEffect(Unit) {
            contextLengthInput = (apiPreferences.contextLengthFlow.first()).toString()
            maxContextLengthInput = (apiPreferences.maxContextLengthFlow.first()).toString()
            enableMaxContextMode = apiPreferences.enableMaxContextModeFlow.first()
            summaryTokenThresholdInput = apiPreferences.summaryTokenThresholdFlow.first().toString()
            enableSummary = apiPreferences.enableSummaryFlow.first()
            enableSummaryByMessageCount = apiPreferences.enableSummaryByMessageCountFlow.first()
            summaryMessageCountThresholdInput = (apiPreferences.summaryMessageCountThresholdFlow.first()).toString()
            maxFileSizeBytesInput = (apiPreferences.maxFileSizeBytesFlow.first() / 1000).toString() // Display as KB
            partSizeInput = (apiPreferences.partSizeFlow.first()).toString()
            maxTextResultLengthInput = (apiPreferences.maxTextResultLengthFlow.first() / 1000).toString() // Display as KB
            maxHttpResponseLengthInput = (apiPreferences.maxHttpResponseLengthFlow.first() / 1000).toString() // Display as KB
        }

        var showSaveSuccessMessage by remember { mutableStateOf(false) }
        var showValidationError by remember { mutableStateOf(false) }
        var validationErrorMessage by remember { mutableStateOf("") }

        // 验证输入是否为有效数字
        fun validateInputs(): Boolean {
            fun validateFloat(value: String, name: String): Boolean {
                val floatVal = value.toFloatOrNull()
                if (floatVal == null || floatVal <= 0f) {
                    validationErrorMessage = "$name 必须是大于 0 的有效数字"
                    return false
                }
                return true
            }

            fun validateInt(value: String, name: String): Boolean {
                val intVal = value.toIntOrNull()
                if (intVal == null || intVal <= 0) {
                    validationErrorMessage = "$name 必须是大于 0 的有效整数"
                    return false
                }
                return true
            }

            if (!validateFloat(contextLengthInput, "上下文长度")) return false
            if (!validateFloat(maxContextLengthInput, "最大上下文长度")) return false
            if (!validateFloat(summaryTokenThresholdInput, "总结触发阈值")) return false
            if (!validateInt(summaryMessageCountThresholdInput, "消息数量阈值")) return false
            if (!validateInt(maxFileSizeBytesInput, "最大文件大小")) return false
            if (!validateInt(partSizeInput, "分片大小")) return false
            if (!validateInt(maxTextResultLengthInput, "最大文本结果长度")) return false
            if (!validateInt(maxHttpResponseLengthInput, "最大HTTP响应长度")) return false

            showValidationError = false
            return true
        }

        // 保存所有设置的函数
        fun saveAllSettings() {
            if (!validateInputs()) {
                showValidationError = true
                return
            }

            scope.launch {
                // All values are already validated, so toFloat/toInt is safe
                apiPreferences.saveContextLength(contextLengthInput.toFloat())
                apiPreferences.saveMaxContextLength(maxContextLengthInput.toFloat())
                apiPreferences.saveEnableMaxContextMode(enableMaxContextMode)
                apiPreferences.saveSummaryTokenThreshold(summaryTokenThresholdInput.toFloat())
                apiPreferences.saveEnableSummary(enableSummary)
                apiPreferences.saveEnableSummaryByMessageCount(enableSummaryByMessageCount)
                apiPreferences.saveSummaryMessageCountThreshold(summaryMessageCountThresholdInput.toInt())
                apiPreferences.saveMaxFileSizeBytes(maxFileSizeBytesInput.toInt() * 1000) // Convert KB to Bytes
                apiPreferences.savePartSize(partSizeInput.toInt())
                apiPreferences.saveMaxTextResultLength(maxTextResultLengthInput.toInt() * 1000) // Convert KB to Bytes
                apiPreferences.saveMaxHttpResponseLength(maxHttpResponseLengthInput.toInt() * 1000) // Convert KB to Bytes
                showSaveSuccessMessage = true
            }
        }

        val componentBackgroundColor = if (hasBackgroundImage) {
                MaterialTheme.colorScheme.surface
        } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        }

        CustomScaffold() { paddingValues ->
                Column(
                        modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .verticalScroll(scrollState)
                ) {

                        Card(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 12.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = stringResource(id = R.string.settings_context_card_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(id = R.string.settings_context_card_content),
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                        // ======= 上下文设置 =======
                        SectionTitle(
                                text = stringResource(id = R.string.settings_context_title),
                                icon = Icons.Default.Storage
                        )

                        // 上下文长度滑块
                        SettingsInputField(
                            title = stringResource(id = R.string.settings_context_length),
                            subtitle = stringResource(id = R.string.settings_context_length_subtitle),
                            value = contextLengthInput,
                            onValueChange = { contextLengthInput = it },
                            unitText = "k",
                            backgroundColor = componentBackgroundColor
                        )

                        SettingsInputField(
                            title = "最大上下文长度",
                            subtitle = "在Max模式下使用的上下文长度",
                            value = maxContextLengthInput,
                            onValueChange = { maxContextLengthInput = it },
                            unitText = "k",
                            backgroundColor = componentBackgroundColor
                        )

                        CompactToggleWithDescription(
                            title = "启用Max模式",
                            description = "使用更大的上下文窗口",
                            checked = enableMaxContextMode,
                            onCheckedChange = { enableMaxContextMode = it },
                            backgroundColor = componentBackgroundColor
                        )
 
                         Spacer(modifier = Modifier.height(16.dp))
 
                         // ======= 总结设置 =======
                        SectionTitle(
                                text = stringResource(id = R.string.settings_summary_title),
                                icon = Icons.Default.Summarize
                        )

                        // 总结设置开关
                        // 启用对话总结开关
                        CompactToggleWithDescription(
                                title = stringResource(id = R.string.settings_enable_summary),
                                description = stringResource(id = R.string.settings_enable_summary_desc),
                                checked = enableSummary,
                                onCheckedChange = { enableSummary = it },
                                backgroundColor = componentBackgroundColor
                        )

                        // 触发条件标题
                        Text(
                                text = "触发条件",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, start = 4.dp)
                        )

                        // 总结令牌阈值滑块
                        SettingsInputField(
                            title = stringResource(id = R.string.settings_summary_threshold),
                            subtitle = "当上下文使用超过此比例时触发总结 (0.1 ~ 0.95)",
                            value = summaryTokenThresholdInput,
                            onValueChange = { summaryTokenThresholdInput = it },
                            backgroundColor = componentBackgroundColor,
                            enabled = enableSummary
                        )


                        // 按消息条数触发开关
                        CompactToggleWithDescription(
                                title = stringResource(id = R.string.settings_enable_summary_by_message_count),
                                description = stringResource(id = R.string.settings_enable_summary_by_message_count_desc),
                                checked = enableSummaryByMessageCount,
                                onCheckedChange = { enableSummaryByMessageCount = it },
                                backgroundColor = componentBackgroundColor,
                                enabled = enableSummary
                        )

                        // 消息条数阈值滑块
                        SettingsInputField(
                            title = stringResource(id = R.string.settings_summary_message_count_threshold),
                            subtitle = "当消息条数超过此值时触发总结",
                            value = summaryMessageCountThresholdInput,
                            onValueChange = { summaryMessageCountThresholdInput = it },
                            unitText = "条",
                            backgroundColor = componentBackgroundColor,
                            enabled = enableSummary && enableSummaryByMessageCount
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ======= 截断设置 =======
                        SectionTitle(
                                text = stringResource(id = R.string.settings_truncation_title),
                                icon = Icons.Default.ContentCut
                        )

                        // 文件读取最大字节数滑块
                        SettingsInputField(
                            title = stringResource(id = R.string.settings_max_file_size),
                            subtitle = stringResource(id = R.string.settings_max_file_size_subtitle),
                            value = maxFileSizeBytesInput,
                            onValueChange = { maxFileSizeBytesInput = it },
                            unitText = "k",
                            backgroundColor = componentBackgroundColor
                        )

                        // 分段读取行数滑块
                        SettingsInputField(
                            title = stringResource(id = R.string.settings_part_size),
                            subtitle = stringResource(id = R.string.settings_part_size_subtitle),
                            value = partSizeInput,
                            onValueChange = { partSizeInput = it },
                            unitText = "行",
                            backgroundColor = componentBackgroundColor
                        )

                        // 文本结果最大长度滑块
                        SettingsInputField(
                            title = stringResource(id = R.string.settings_max_text_result),
                            subtitle = stringResource(id = R.string.settings_max_text_result_subtitle),
                            value = maxTextResultLengthInput,
                            onValueChange = { maxTextResultLengthInput = it },
                            unitText = "k",
                            backgroundColor = componentBackgroundColor
                        )

                        // HTTP响应最大长度滑块
                        SettingsInputField(
                            title = stringResource(id = R.string.settings_max_http_response),
                            subtitle = stringResource(id = R.string.settings_max_http_response_subtitle),
                            value = maxHttpResponseLengthInput,
                            onValueChange = { maxHttpResponseLengthInput = it },
                            unitText = "k",
                            backgroundColor = componentBackgroundColor
                        )

                        // 重置按钮
                        Button(
                                onClick = {
                                        scope.launch {
                                                apiPreferences.resetContextSummaryAndTruncationSettings()
                                                // Reload all settings after reset
                                                contextLengthInput = (apiPreferences.contextLengthFlow.first()).toString()
                                                maxContextLengthInput = (apiPreferences.maxContextLengthFlow.first()).toString()
                                                enableMaxContextMode = apiPreferences.enableMaxContextModeFlow.first()
                                                summaryTokenThresholdInput = apiPreferences.summaryTokenThresholdFlow.first().toString()
                                                enableSummary = apiPreferences.enableSummaryFlow.first()
                                                enableSummaryByMessageCount = apiPreferences.enableSummaryByMessageCountFlow.first()
                                                summaryMessageCountThresholdInput = (apiPreferences.summaryMessageCountThresholdFlow.first()).toString()
                                                maxFileSizeBytesInput = (apiPreferences.maxFileSizeBytesFlow.first() / 1000).toString()
                                                partSizeInput = apiPreferences.partSizeFlow.first().toString()
                                                maxTextResultLengthInput = (apiPreferences.maxTextResultLengthFlow.first() / 1000).toString()
                                                maxHttpResponseLengthInput = (apiPreferences.maxHttpResponseLengthFlow.first() / 1000).toString()
                                                showSaveSuccessMessage = true
                                        }
                                },
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                        ) {
                                Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = "重置所有设置",
                                        style = MaterialTheme.typography.bodyLarge
                                )
                        }

                        // ======= 保存按钮 =======
                        Button(
                                onClick = { saveAllSettings() },
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                )
                        ) {
                                Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = stringResource(id = R.string.settings_save),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                )
                        }

                        // 底部间距
                        Spacer(modifier = Modifier.height(16.dp))
                }

                // 验证错误提示
                if (showValidationError) {
                        AlertDialog(
                                onDismissRequest = { showValidationError = false },
                                title = { Text("验证失败") },
                                text = { Text(validationErrorMessage) },
                                confirmButton = {
                                        TextButton(onClick = { showValidationError = false }) {
                                                Text(stringResource(id = android.R.string.ok))
                                        }
                                }
                        )
                }

                // 保存成功提示
                if (showSaveSuccessMessage) {
                        LaunchedEffect(Unit) {
                                kotlinx.coroutines.delay(1500)
                                showSaveSuccessMessage = false
                        }
                        Snackbar(
                                modifier = Modifier.padding(16.dp),
                                action = {
                                        TextButton(onClick = { showSaveSuccessMessage = false }) {
                                                Text(stringResource(id = android.R.string.ok))
                                        }
                                }
                        ) {
                                Text(stringResource(id = R.string.settings_saved))
                        }
                }
        }
}

@Composable
private fun SectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
        ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                )
        }
}

@Composable
private fun CompactToggleWithDescription(
        title: String,
        description: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        backgroundColor: Color = Color.Transparent,
        enabled: Boolean = true
) {
        val contentAlpha = if (enabled) 1f else 0.38f
        
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(backgroundColor)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .alpha(contentAlpha),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
        ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                        )
                        Text(
                                text = description,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                        )
                }
                Switch(
                        checked = checked,
                        onCheckedChange = if (enabled) onCheckedChange else { {} },
                        enabled = enabled,
                        modifier = Modifier.scale(0.8f)
                )
        }
}

@Composable
private fun SettingsInputField(
    title: String,
    subtitle: String,
    value: String,
    onValueChange: (String) -> Unit,
    unitText: String? = null,
    backgroundColor: Color,
    enabled: Boolean = true
) {
    val focusManager = LocalFocusManager.current
    val contentAlpha = if (enabled) 1f else 0.38f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .padding(8.dp)
            .alpha(contentAlpha)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = value,
                    onValueChange = { newText ->
                        if (enabled) {
                            onValueChange(newText.filter { it.isDigit() || it == '.' })
                        }
                    },
                    enabled = enabled,
                    modifier = Modifier
                        .width(50.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (enabled) {
                                focusManager.clearFocus()
                            }
                        }
                    ),
                    singleLine = true
                )

                if (unitText != null) {
                    Text(
                        text = unitText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

