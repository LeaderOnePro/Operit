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

        // Collect settings as state
        val summaryTokenThreshold = apiPreferences.summaryTokenThresholdFlow.collectAsState(initial = ApiPreferences.DEFAULT_SUMMARY_TOKEN_THRESHOLD).value
        val contextLength = apiPreferences.contextLengthFlow.collectAsState(initial = ApiPreferences.DEFAULT_CONTEXT_LENGTH).value
        val enableSummary = apiPreferences.enableSummaryFlow.collectAsState(initial = ApiPreferences.DEFAULT_ENABLE_SUMMARY).value
        val enableSummaryByMessageCount = apiPreferences.enableSummaryByMessageCountFlow.collectAsState(initial = ApiPreferences.DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT).value
        val summaryMessageCountThreshold = apiPreferences.summaryMessageCountThresholdFlow.collectAsState(initial = ApiPreferences.DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD).value
        val maxFileSizeBytes = apiPreferences.maxFileSizeBytesFlow.collectAsState(initial = ApiPreferences.DEFAULT_MAX_FILE_SIZE_BYTES).value
        val partSize = apiPreferences.partSizeFlow.collectAsState(initial = ApiPreferences.DEFAULT_PART_SIZE).value
        val maxTextResultLength = apiPreferences.maxTextResultLengthFlow.collectAsState(initial = ApiPreferences.DEFAULT_MAX_TEXT_RESULT_LENGTH).value
        val maxHttpResponseLength = apiPreferences.maxHttpResponseLengthFlow.collectAsState(initial = ApiPreferences.DEFAULT_MAX_HTTP_RESPONSE_LENGTH).value

        val hasBackgroundImage = userPreferences.useBackgroundImage.collectAsState(initial = false).value

        // Mutable state for editing
        var summaryTokenThresholdInput by remember { mutableStateOf(summaryTokenThreshold) }
        var contextLengthInput by remember { mutableStateOf(contextLength) }
        var enableSummaryInput by remember { mutableStateOf(enableSummary) }
        var enableSummaryByMessageCountInput by remember { mutableStateOf(enableSummaryByMessageCount) }
        var summaryMessageCountThresholdInput by remember { mutableStateOf(summaryMessageCountThreshold) }
        var maxFileSizeBytesInput by remember { mutableStateOf(maxFileSizeBytes) }
        var partSizeInput by remember { mutableStateOf(partSize) }
        var maxTextResultLengthInput by remember { mutableStateOf(maxTextResultLength) }
        var maxHttpResponseLengthInput by remember { mutableStateOf(maxHttpResponseLength) }

        var showSaveSuccessMessage by remember { mutableStateOf(false) }

        // Update local state when preferences change
        LaunchedEffect(
                summaryTokenThreshold, contextLength, enableSummary, enableSummaryByMessageCount,
                summaryMessageCountThreshold, maxFileSizeBytes, partSize, maxTextResultLength, maxHttpResponseLength
        ) {
                summaryTokenThresholdInput = summaryTokenThreshold
                contextLengthInput = contextLength
                enableSummaryInput = enableSummary
                enableSummaryByMessageCountInput = enableSummaryByMessageCount
                summaryMessageCountThresholdInput = summaryMessageCountThreshold
                maxFileSizeBytesInput = maxFileSizeBytes
                partSizeInput = partSize
                maxTextResultLengthInput = maxTextResultLength
                maxHttpResponseLengthInput = maxHttpResponseLength
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
                        // ======= 上下文设置 =======
                        SectionTitle(
                                text = stringResource(id = R.string.settings_context_title),
                                icon = Icons.Default.Storage
                        )

                        // 上下文长度滑块
                        CompactSlider(
                                title = stringResource(id = R.string.settings_context_length),
                                subtitle = stringResource(id = R.string.settings_context_length_subtitle),
                                value = contextLengthInput,
                                onValueChange = {
                                        contextLengthInput = it
                                        scope.launch {
                                                apiPreferences.saveContextLength(it)
                                                showSaveSuccessMessage = true
                                        }
                                },
                                valueRange = 1f..1024f,
                                steps = 1022,
                                decimalFormatPattern = "#.#",
                                unitText = "k",
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
                                checked = enableSummaryInput,
                                onCheckedChange = {
                                        enableSummaryInput = it
                                        scope.launch {
                                                apiPreferences.saveEnableSummary(it)
                                                showSaveSuccessMessage = true
                                        }
                                },
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
                        CompactSlider(
                                title = stringResource(id = R.string.settings_summary_threshold),
                                subtitle = "当上下文使用超过此比例时触发总结",
                                value = summaryTokenThresholdInput,
                                onValueChange = {
                                        summaryTokenThresholdInput = it
                                        scope.launch {
                                                apiPreferences.saveSummaryTokenThreshold(it)
                                                showSaveSuccessMessage = true
                                        }
                                },
                                valueRange = 0.1f..0.95f,
                                steps = 84,
                                decimalFormatPattern = "#.##",
                                backgroundColor = componentBackgroundColor,
                                enabled = enableSummaryInput
                        )

                        // 按消息条数触发开关
                        CompactToggleWithDescription(
                                title = stringResource(id = R.string.settings_enable_summary_by_message_count),
                                description = stringResource(id = R.string.settings_enable_summary_by_message_count_desc),
                                checked = enableSummaryByMessageCountInput,
                                onCheckedChange = {
                                        enableSummaryByMessageCountInput = it
                                        scope.launch {
                                                apiPreferences.saveEnableSummaryByMessageCount(it)
                                                showSaveSuccessMessage = true
                                        }
                                },
                                backgroundColor = componentBackgroundColor,
                                enabled = enableSummaryInput
                        )

                        // 消息条数阈值滑块
                        CompactSlider(
                                title = stringResource(id = R.string.settings_summary_message_count_threshold),
                                subtitle = "当消息条数超过此值时触发总结",
                                value = summaryMessageCountThresholdInput.toFloat(),
                                onValueChange = {
                                        summaryMessageCountThresholdInput = it.toInt()
                                        scope.launch {
                                                apiPreferences.saveSummaryMessageCountThreshold(it.toInt())
                                                showSaveSuccessMessage = true
                                        }
                                },
                                valueRange = 1f..20f,
                                steps = 19,
                                decimalFormatPattern = "#",
                                unitText = "条",
                                backgroundColor = componentBackgroundColor,
                                enabled = enableSummaryInput && enableSummaryByMessageCountInput
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ======= 截断设置 =======
                        SectionTitle(
                                text = stringResource(id = R.string.settings_truncation_title),
                                icon = Icons.Default.ContentCut
                        )

                        // 文件读取最大字节数滑块
                        CompactSlider(
                                title = stringResource(id = R.string.settings_max_file_size),
                                subtitle = stringResource(id = R.string.settings_max_file_size_subtitle),
                                value = (maxFileSizeBytesInput / 1000f),
                                onValueChange = {
                                        maxFileSizeBytesInput = (it * 1000).toInt()
                                        scope.launch {
                                                apiPreferences.saveMaxFileSizeBytes((it * 1000).toInt())
                                                showSaveSuccessMessage = true
                                        }
                                },
                                valueRange = 1f..256f,
                                steps = 254,
                                decimalFormatPattern = "#",
                                unitText = "k",
                                backgroundColor = componentBackgroundColor
                        )

                        // 分段读取行数滑块
                        CompactSlider(
                                title = stringResource(id = R.string.settings_part_size),
                                subtitle = stringResource(id = R.string.settings_part_size_subtitle),
                                value = partSizeInput.toFloat(),
                                onValueChange = {
                                        partSizeInput = it.toInt()
                                        scope.launch {
                                                apiPreferences.savePartSize(it.toInt())
                                                showSaveSuccessMessage = true
                                        }
                                },
                                valueRange = 50f..1000f,
                                steps = 18,
                                decimalFormatPattern = "#",
                                unitText = "行",
                                backgroundColor = componentBackgroundColor
                        )

                        // 文本结果最大长度滑块
                        CompactSlider(
                                title = stringResource(id = R.string.settings_max_text_result),
                                subtitle = stringResource(id = R.string.settings_max_text_result_subtitle),
                                value = (maxTextResultLengthInput / 1000f),
                                onValueChange = {
                                        maxTextResultLengthInput = (it * 1000).toInt()
                                        scope.launch {
                                                apiPreferences.saveMaxTextResultLength((it * 1000).toInt())
                                                showSaveSuccessMessage = true
                                        }
                                },
                                valueRange = 10f..200f,
                                steps = 189,
                                decimalFormatPattern = "#",
                                unitText = "k",
                                backgroundColor = componentBackgroundColor
                        )

                        // HTTP响应最大长度滑块
                        CompactSlider(
                                title = stringResource(id = R.string.settings_max_http_response),
                                subtitle = stringResource(id = R.string.settings_max_http_response_subtitle),
                                value = (maxHttpResponseLengthInput / 1000f),
                                onValueChange = {
                                        maxHttpResponseLengthInput = (it * 1000).toInt()
                                        scope.launch {
                                                apiPreferences.saveMaxHttpResponseLength((it * 1000).toInt())
                                                showSaveSuccessMessage = true
                                        }
                                },
                                valueRange = 10f..500f,
                                steps = 489,
                                decimalFormatPattern = "#",
                                unitText = "k",
                                backgroundColor = componentBackgroundColor
                        )

                        // 重置截断设置按钮
                        Button(
                                onClick = {
                                        scope.launch {
                                                apiPreferences.resetTruncationSettings()
                                                showSaveSuccessMessage = true
                                        }
                                },
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                        ) {
                                Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = stringResource(id = R.string.settings_reset_truncation),
                                        style = MaterialTheme.typography.bodyMedium
                                )
                        }

                        // 底部间距
                        Spacer(modifier = Modifier.height(16.dp))
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
private fun CompactSlider(
        title: String,
        subtitle: String,
        value: Float,
        onValueChange: (Float) -> Unit,
        valueRange: ClosedFloatingPointRange<Float>,
        steps: Int,
        decimalFormatPattern: String,
        unitText: String? = null,
        backgroundColor: Color,
        enabled: Boolean = true
) {
        val focusManager = LocalFocusManager.current
        val df = remember(decimalFormatPattern) { DecimalFormat(decimalFormatPattern) }

        var sliderValue by remember(value) { mutableStateOf(value) }
        var textValue by remember(value) { mutableStateOf(df.format(value)) }

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
                                        value = textValue,
                                        onValueChange = { newText ->
                                                if (enabled) {
                                                        textValue = newText
                                                        newText.toFloatOrNull()?.let {
                                                                sliderValue = it.coerceIn(valueRange)
                                                        }
                                                }
                                        },
                                        enabled = enabled,
                                        modifier = Modifier
                                                .width(40.dp)
                                                .background(
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                        RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                        textStyle = TextStyle(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center
                                        ),
                                        keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number,
                                                imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                                onDone = {
                                                        if (enabled) {
                                                                val finalValue = textValue.toFloatOrNull()?.coerceIn(valueRange) ?: sliderValue
                                                                onValueChange(finalValue)
                                                                textValue = df.format(finalValue)
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
                                                        fontSize = 10.sp
                                                ),
                                                modifier = Modifier.padding(start = 2.dp)
                                        )
                                }
                        }
                }
        }
}

