package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import java.text.DecimalFormat
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

// 保存滑动状态变量，使其跨重组保持
private val SettingsScreenScrollPosition = mutableStateOf(0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
        onNavigateToUserPreferences: () -> Unit,
        navigateToToolPermissions: () -> Unit,
        navigateToModelConfig: () -> Unit,
        navigateToThemeSettings: () -> Unit,
        navigateToModelPrompts: () -> Unit,
        navigateToFunctionalConfig: () -> Unit,
        navigateToChatHistorySettings: () -> Unit,
        navigateToLanguageSettings: () -> Unit,
        navigateToSpeechServicesSettings: () -> Unit,
        navigateToCustomHeadersSettings: () -> Unit,
        navigateToPersonaCardGeneration: () -> Unit,
        navigateToWaifuModeSettings: () -> Unit
) {
        val context = LocalContext.current
        val apiPreferences = remember { ApiPreferences.getInstance(context) }
        val userPreferences = remember { UserPreferencesManager(context) }
        val scope = rememberCoroutineScope()

        // 创建和记住滚动状态，设置为上次保存的位置
        val scrollState = rememberScrollState(SettingsScreenScrollPosition.value)

        // 当滚动状态改变时更新保存的位置
        LaunchedEffect(scrollState) {
                snapshotFlow { scrollState.value }.collect { position ->
                        SettingsScreenScrollPosition.value = position
                }
        }

        // Collect API settings as state
        val showFpsCounter = apiPreferences.showFpsCounterFlow.collectAsState(initial = ApiPreferences.DEFAULT_SHOW_FPS_COUNTER).value
        val keepScreenOn = apiPreferences.keepScreenOnFlow.collectAsState(initial = ApiPreferences.DEFAULT_KEEP_SCREEN_ON).value
        val summaryTokenThreshold = apiPreferences.summaryTokenThresholdFlow.collectAsState(initial = ApiPreferences.DEFAULT_SUMMARY_TOKEN_THRESHOLD).value
        val contextLength = apiPreferences.contextLengthFlow.collectAsState(initial = ApiPreferences.DEFAULT_CONTEXT_LENGTH).value
        
        // Collect Summary settings as state
        val enableSummary = apiPreferences.enableSummaryFlow.collectAsState(initial = ApiPreferences.DEFAULT_ENABLE_SUMMARY).value
        val enableSummaryByMessageCount = apiPreferences.enableSummaryByMessageCountFlow.collectAsState(initial = ApiPreferences.DEFAULT_ENABLE_SUMMARY_BY_MESSAGE_COUNT).value
        val summaryMessageCountThreshold = apiPreferences.summaryMessageCountThresholdFlow.collectAsState(initial = ApiPreferences.DEFAULT_SUMMARY_MESSAGE_COUNT_THRESHOLD).value

        val hasBackgroundImage = userPreferences.useBackgroundImage.collectAsState(initial = false).value

        // Mutable state for editing
        var showFpsCounterInput by remember { mutableStateOf(showFpsCounter) }
        var keepScreenOnInput by remember { mutableStateOf(keepScreenOn) }
        var summaryTokenThresholdInput by remember { mutableStateOf(summaryTokenThreshold) }
        var contextLengthInput by remember { mutableStateOf(contextLength) }
        
        // Mutable state for Summary settings
        var enableSummaryInput by remember { mutableStateOf(enableSummary) }
        var enableSummaryByMessageCountInput by remember { mutableStateOf(enableSummaryByMessageCount) }
        var summaryMessageCountThresholdInput by remember { mutableStateOf(summaryMessageCountThreshold) }
        
        var showSaveSuccessMessage by remember { mutableStateOf(false) }

        // Update local state when preferences change
        LaunchedEffect(showFpsCounter, keepScreenOn, summaryTokenThreshold, contextLength, enableSummary, enableSummaryByMessageCount, summaryMessageCountThreshold) {
                showFpsCounterInput = showFpsCounter
                keepScreenOnInput = keepScreenOn
                summaryTokenThresholdInput = summaryTokenThreshold
                contextLengthInput = contextLength
                enableSummaryInput = enableSummary
                enableSummaryByMessageCountInput = enableSummaryByMessageCount
                summaryMessageCountThresholdInput = summaryMessageCountThreshold
        }

        val cardContainerColor = if (hasBackgroundImage) {
                MaterialTheme.colorScheme.surface
        } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        }

        val componentBackgroundColor = if (hasBackgroundImage) {
                MaterialTheme.colorScheme.surface
        } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        }

        Column(
                modifier = Modifier.fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(scrollState)
        ) {
                // ======= 个性化配置 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_personalization),
                        icon = Icons.Default.Person,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_user_preferences),
                                subtitle = stringResource(id = R.string.settings_user_preferences_subtitle),
                                icon = Icons.Default.Face,
                                onClick = onNavigateToUserPreferences
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(R.string.language_settings),
                                subtitle = stringResource(id = R.string.settings_language_subtitle),
                                icon = Icons.Default.Language,
                                onClick = navigateToLanguageSettings
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_theme_appearance),
                                subtitle = stringResource(id = R.string.settings_theme_subtitle),
                                icon = Icons.Default.Palette,
                                onClick = navigateToThemeSettings
                        )
                }

                // ======= AI模型配置 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_ai_model),
                        icon = Icons.Default.Settings,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_model_parameters),
                                subtitle = stringResource(id = R.string.settings_model_params_subtitle),
                                icon = Icons.Default.Api,
                                onClick = navigateToModelConfig
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_functional_model),
                                subtitle = stringResource(id = R.string.settings_functional_model_subtitle),
                                icon = Icons.Default.Tune,
                                onClick = navigateToFunctionalConfig
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_speech_services),
                                subtitle = stringResource(id = R.string.settings_speech_services_subtitle),
                                icon = Icons.Default.RecordVoiceOver,
                                onClick = navigateToSpeechServicesSettings
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_custom_headers),
                                subtitle = stringResource(id = R.string.settings_custom_headers_subtitle),
                                icon = Icons.Default.AddModerator,
                                onClick = navigateToCustomHeadersSettings
                        )
                }

                // ======= 提示词配置 =======
                SettingsSection(
                        title = stringResource(R.string.settings_prompt_section),
                        icon = Icons.Default.Message,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(R.string.settings_prompt_title),
                                subtitle = stringResource(id = R.string.settings_system_prompts_subtitle),
                                icon = Icons.Default.ChatBubble,
                                onClick = navigateToModelPrompts
                        )
                        
                        // 新增：人设卡生成
                        CompactSettingsItem(
                                title = stringResource(R.string.persona_card_generation),
                                subtitle = stringResource(R.string.persona_card_generation_desc),
                                icon = Icons.Default.Face,
                                onClick = navigateToPersonaCardGeneration
                        )
                        
                        // 新增：Waifu模式设置
                        CompactSettingsItem(
                                title = stringResource(R.string.waifu_mode_settings),
                                subtitle = stringResource(R.string.waifu_mode_settings_desc),
                                icon = Icons.Default.EmojiEmotions,
                                onClick = navigateToWaifuModeSettings
                        )
                }

                // ======= 上下文和总结设置 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_context_summary),
                        icon = Icons.Default.Analytics,
                        containerColor = cardContainerColor
                ) {
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

                        // 总结令牌阈值滑块
                        CompactSlider(
                                title = stringResource(id = R.string.settings_summary_threshold),
                                subtitle = stringResource(id = R.string.settings_summary_threshold_subtitle),
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
                                backgroundColor = componentBackgroundColor
                        )

                        // 总结设置开关
                        Column(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(componentBackgroundColor)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
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
                                        }
                                )
                                Divider(modifier = Modifier.padding(vertical = 4.dp))
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
                                        }
                                )
                        }

                        // 消息条数阈值滑块
                        CompactSlider(
                                title = stringResource(id = R.string.settings_summary_message_count_threshold),
                                subtitle = stringResource(id = R.string.settings_summary_message_count_threshold_subtitle),
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
                                backgroundColor = componentBackgroundColor
                        )
                }

                // ======= 显示和行为设置 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_section_display),
                        icon = Icons.Default.Visibility,
                        containerColor = cardContainerColor
                ) {
                        // 开关控件
                        Column(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(componentBackgroundColor)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                                CompactToggleWithDescription(
                                        title = stringResource(id = R.string.settings_keep_screen_on),
                                        description = stringResource(id = R.string.settings_keep_screen_on_desc),
                                        checked = keepScreenOnInput,
                                        onCheckedChange = {
                                                keepScreenOnInput = it
                                                scope.launch {
                                                        apiPreferences.saveKeepScreenOn(it)
                                                        showSaveSuccessMessage = true
                                                }
                                        }
                                )
                                Divider(modifier = Modifier.padding(vertical = 4.dp))
                                CompactToggleWithDescription(
                                        title = stringResource(id = R.string.show_fps_counter),
                                        description = stringResource(id = R.string.fps_counter_description),
                                        checked = showFpsCounterInput,
                                        onCheckedChange = {
                                                showFpsCounterInput = it
                                                scope.launch {
                                                        apiPreferences.saveShowFpsCounter(it)
                                                        showSaveSuccessMessage = true
                                                }
                                        }
                                )
                        }
                }

                // ======= 数据和权限 =======
                SettingsSection(
                        title = stringResource(id = R.string.settings_data_permissions),
                        icon = Icons.Default.Security,
                        containerColor = cardContainerColor
                ) {
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_tool_permissions),
                                subtitle = stringResource(id = R.string.settings_tool_permissions_subtitle),
                                icon = Icons.Default.AdminPanelSettings,
                                onClick = navigateToToolPermissions
                        )
                        
                        CompactSettingsItem(
                                title = stringResource(id = R.string.settings_data_backup),
                                subtitle = stringResource(id = R.string.settings_data_backup_subtitle),
                                icon = Icons.Default.History,
                                onClick = navigateToChatHistorySettings
                        )
                }

                // ======= Token使用统计 =======
                TokenUsageCompactCard(context, apiPreferences, scope, cardContainerColor) {
                        showSaveSuccessMessage = true
                }

                // 底部间距
                Spacer(modifier = Modifier.height(16.dp))
        }
}

@Composable
private fun SettingsSection(
        title: String,
        icon: ImageVector,
        containerColor: Color,
        content: @Composable ColumnScope.() -> Unit
) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                // 分组标题
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp)
                ) {
                        Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                        )
                }
                
                // 内容区域
                Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                                containerColor = containerColor
                        )
                ) {
                        Column(
                                modifier = Modifier.padding(12.dp),
                                content = content
                        )
                }
        }
}

@Composable
private fun CompactSettingsItem(
        title: String,
        subtitle: String,
        icon: ImageVector,
        onClick: () -> Unit
) {
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onClick() }
                        .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                        Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                        )
                }
                
                Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                )
        }
}

@Composable
private fun CompactToggleWithDescription(
        title: String,
        description: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
) {
        Row(
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
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
                        onCheckedChange = onCheckedChange,
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
        backgroundColor: Color
) {
        val focusManager = LocalFocusManager.current
        val df = remember(decimalFormatPattern) { DecimalFormat(decimalFormatPattern) }

        var sliderValue by remember(value) { mutableStateOf(value) }
        var textValue by remember(value) { mutableStateOf(df.format(value)) }

        Column(
                modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(backgroundColor)
                        .padding(8.dp)
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
                                                textValue = newText
                                                newText.toFloatOrNull()?.let {
                                                        sliderValue = it.coerceIn(valueRange)
                                                }
                                        },
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
                                                        val finalValue = textValue.toFloatOrNull()?.coerceIn(valueRange) ?: sliderValue
                                                        onValueChange(finalValue)
                                                        textValue = df.format(finalValue)
                                                        focusManager.clearFocus()
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

@Composable
private fun TokenUsageCompactCard(
        context: android.content.Context,
        apiPreferences: ApiPreferences,
        scope: kotlinx.coroutines.CoroutineScope,
        containerColor: Color,
        onSuccess: () -> Unit
) {
    // State to hold token data for all function types
    val functionTokenUsage = remember { mutableStateMapOf<FunctionType, Pair<Int, Int>>() }
    var expanded by remember { mutableStateOf(false) }

    // Collect tokens for EACH function type from ApiPreferences
    LaunchedEffect(Unit) {
        for (functionType in FunctionType.values()) {
            scope.launch {
                apiPreferences.getTokensForFunctionFlow(functionType).collect { tokens ->
                    if (tokens.first > 0 || tokens.second > 0) {
                        functionTokenUsage[functionType] = tokens
                    } else {
                        // This is important to remove if it becomes 0 after reset
                        functionTokenUsage.remove(functionType)
                    }
                }
            }
        }
    }

    val usdToRmbRate = 7.2

    // Calculate costs for each function
    val functionCosts = functionTokenUsage.mapValues { (_, tokens) ->
        val inputCost = tokens.first * 0.27 / 1_000_000 * usdToRmbRate
        val outputCost = tokens.second * 1.10 / 1_000_000 * usdToRmbRate
        inputCost + outputCost
    }

    val totalInputTokens = functionTokenUsage.values.sumOf { it.first }
    val totalOutputTokens = functionTokenUsage.values.sumOf { it.second }
    val totalCost = functionCosts.values.sum()
    val totalTokens = totalInputTokens + totalOutputTokens

    SettingsSection(
            title = stringResource(id = R.string.settings_section_usage),
            icon = Icons.Default.Analytics,
            containerColor = containerColor
    ) {
        Column(modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = !expanded }) {
            Row(
                    modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = stringResource(id = R.string.settings_token_usage_stats),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                    )
                    Text(
                            text = "$totalTokens tokens · ¥${String.format("%.2f", totalCost)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    // Header Row
                    TokenStatRow(
                            label = stringResource(id = R.string.settings_function_label),
                            inputTokens = -1, // Use negative as a flag for header
                            outputTokens = -1,
                            cost = -1.0,
                            isHighlighted = true
                    )
                    Divider(modifier = Modifier.padding(vertical = 2.dp))

                    val sortedFunctions = functionTokenUsage.entries.sortedBy { it.key.name }

                    if (sortedFunctions.isEmpty()) {
                        Text(
                                text = stringResource(id = R.string.settings_no_token_records),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 8.dp).align(Alignment.CenterHorizontally)
                        )
                    } else {
                        for ((functionType, tokens) in sortedFunctions) {
                            val (input, output) = tokens
                            val cost = functionCosts[functionType] ?: 0.0
                            TokenStatRow(
                                    label = functionType.name.replace("_", " ").lowercase()
                                            .replaceFirstChar { it.uppercase() },
                                    inputTokens = input,
                                    outputTokens = output,
                                    cost = cost
                            )
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    TokenStatRow(
                            label = stringResource(id = R.string.settings_total),
                            inputTokens = totalInputTokens,
                            outputTokens = totalOutputTokens,
                            cost = totalCost,
                            isHighlighted = true
                    )

                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                                onClick = {
                                    scope.launch {
                                        apiPreferences.resetAllFunctionTokenCounts()
                                        onSuccess()
                                    }
                                }
                        ) {
                            Text(stringResource(id = R.string.settings_reset_all_counts), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenStatRow(
        label: String,
        inputTokens: Int,
        outputTokens: Int,
        cost: Double,
        isHighlighted: Boolean = false
) {
    val textStyle = if (isHighlighted)
        MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    else
        MaterialTheme.typography.bodySmall

    Row(
            modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
                text = label,
                style = textStyle,
                modifier = Modifier.weight(0.3f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
        )
        if (inputTokens >= 0) { // Check for header flag
            Text(
                    text = inputTokens.toString(),
                    style = textStyle,
                    modifier = Modifier.weight(0.25f),
                    textAlign = TextAlign.End
            )
            Text(
                    text = outputTokens.toString(),
                    style = textStyle,
                    modifier = Modifier.weight(0.25f),
                    textAlign = TextAlign.End
            )
            Text(
                    text = "¥${String.format(if (cost > 0 && cost < 0.01) "%.3f" else "%.2f", cost)}",
                    style = textStyle,
                    modifier = Modifier.weight(0.2f),
                    textAlign = TextAlign.End
            )
        } else { // Header text
            Text(text = stringResource(id = R.string.settings_input_tokens), style = textStyle, modifier = Modifier.weight(0.25f), textAlign = TextAlign.End)
            Text(text = stringResource(id = R.string.settings_output_tokens), style = textStyle, modifier = Modifier.weight(0.25f), textAlign = TextAlign.End)
            Text(text = stringResource(id = R.string.settings_cost), style = textStyle, modifier = Modifier.weight(0.2f), textAlign = TextAlign.End)
        }
    }
}
