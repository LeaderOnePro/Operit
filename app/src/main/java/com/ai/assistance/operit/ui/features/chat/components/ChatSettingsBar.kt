package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Portrait
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.model.PromptProfile
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.preferences.PromptPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.permissions.PermissionLevel
import java.text.DecimalFormat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.ai.assistance.operit.R

@Composable
fun ChatSettingsBar(
    modifier: Modifier = Modifier,
    enableAiPlanning: Boolean,
    onToggleAiPlanning: () -> Unit,
    permissionLevel: PermissionLevel,
    onTogglePermission: () -> Unit,
    enableThinkingMode: Boolean,
    onToggleThinkingMode: () -> Unit,
    enableThinkingGuidance: Boolean,
    onToggleThinkingGuidance: () -> Unit,
    maxWindowSizeInK: Float,
    onContextLengthChange: (Float) -> Unit,
    enableMemoryAttachment: Boolean,
        onToggleMemoryAttachment: () -> Unit,
        summaryTokenThreshold: Float,
        onSummaryTokenThresholdChange: (Float) -> Unit,
        onNavigateToUserPreferences: () -> Unit,
        onNavigateToModelConfig: () -> Unit,
        onNavigateToModelPrompts: () -> Unit,
    isAutoReadEnabled: Boolean,
    onToggleAutoRead: () -> Unit,
    onManualMemoryUpdate: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val iconScale by
            animateFloatAsState(targetValue = if (showMenu) 1.2f else 1f, label = "iconScale")

    // 用于显示详情说明的状态，现在使用一个Pair来保存标题和内容
    var infoPopupContent by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showModelDropdown by remember { mutableStateOf(false) }
    var showPromptDropdown by remember { mutableStateOf(false) }
    var showMemoryDropdown by remember { mutableStateOf(false) }

    // 将模型选择逻辑封装到组件内部
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val functionalConfigManager = remember { FunctionalConfigManager(context) }
    val modelConfigManager = remember { ModelConfigManager(context) }
    val configMapping by
            functionalConfigManager.functionConfigMappingFlow.collectAsState(initial = emptyMap())
    var configSummaries by remember { mutableStateOf<List<ModelConfigSummary>>(emptyList()) }
    LaunchedEffect(Unit) { configSummaries = modelConfigManager.getAllConfigSummaries() }
    val currentConfigId =
            configMapping[FunctionType.CHAT] ?: FunctionalConfigManager.DEFAULT_CONFIG_ID
            
    // 新增：用户偏好（记忆）选择逻辑
    val userPreferencesManager = remember { UserPreferencesManager(context) }
    val activeProfileId by
            userPreferencesManager.activeProfileIdFlow.collectAsState(initial = "default")
    var preferenceProfiles by remember { mutableStateOf<List<PreferenceProfile>>(emptyList()) }
    LaunchedEffect(Unit) {
        val profileIds = userPreferencesManager.profileListFlow.first()
        preferenceProfiles =
                profileIds.map { id -> userPreferencesManager.getUserPreferencesFlow(id).first() }
    }

    val onSelectModel: (String) -> Unit = { selectedId ->
        scope.launch {
            functionalConfigManager.setConfigForFunction(FunctionType.CHAT, selectedId)
            EnhancedAIService.refreshServiceForFunction(context, FunctionType.CHAT)
        }
    }

    val onSelectMemory: (String) -> Unit = { selectedId ->
        scope.launch {
            userPreferencesManager.setActiveProfile(selectedId)
            // 用户偏好和记忆库绑定，可能影响AI行为，所以刷新服务
            EnhancedAIService.refreshServiceForFunction(context, FunctionType.CHAT)
        }
    }

    // The passed modifier will align this Box within its parent.
    Box(modifier = modifier) {
        Row(
            // This modifier just adds padding. The Row will wrap its content.
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.Bottom, // Align the icon column to the bottom.
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedVisibility(visible = enableMemoryAttachment) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = stringResource(R.string.memory_attachment_active),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                AnimatedVisibility(visible = enableThinkingMode) {
                    Icon(
                        imageVector = Icons.Rounded.Psychology,
                        contentDescription = stringResource(R.string.thinking_mode_active),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                AnimatedVisibility(visible = enableThinkingGuidance) {
                    Icon(
                        imageVector = Icons.Rounded.TipsAndUpdates,
                        contentDescription = stringResource(R.string.thinking_guidance_active),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                AnimatedVisibility(visible = enableAiPlanning) {
                    Icon(
                        imageVector = Icons.Outlined.Hub,
                        contentDescription = stringResource(R.string.ai_planning_active),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                AnimatedVisibility(visible = permissionLevel == PermissionLevel.ALLOW) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = stringResource(R.string.auto_approve_active),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                AnimatedVisibility(visible = isAutoReadEnabled) {
                    Icon(
                        imageVector = Icons.Rounded.VolumeUp,
                        contentDescription = stringResource(R.string.auto_read_active),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = { showMenu = !showMenu }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = stringResource(R.string.settings_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp).scale(iconScale)
                    )
                }
            }
        }

        if (showMenu) {
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = {
                    showMenu = false
                    showModelDropdown = false // 关闭主菜单时也关闭模型菜单
                    showPromptDropdown = false
                    showMemoryDropdown = false
                },
                    properties =
                            PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(modifier = Modifier.padding(top = 0.dp, bottom = 76.dp)) {
                    Card(
                        modifier = Modifier.width(280.dp), // 加宽一级菜单以适应英文显示
                        shape = RoundedCornerShape(8.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.surface.copy(
                                                            alpha = 0.95f
                                                    )
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                                modifier =
                                        Modifier.padding(vertical = 4.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // 模型选择器
                            ModelSelectorItem(
                                configSummaries = configSummaries,
                                currentConfigId = currentConfigId,
                                onSelectModel = onSelectModel,
                                expanded = showModelDropdown,
                                    onExpandedChange = { showModelDropdown = it },
                                    onManageClick = {
                                        onNavigateToModelConfig()
                                        showMenu = false
                                    },
                                    onInfoClick = {
                                        infoPopupContent =
                                                context.getString(R.string.model_config) to context.getString(R.string.model_config_desc)
                                        showMenu = false
                                    }
                            )

                            // 记忆选择器
                            MemorySelectorItem(
                                preferenceProfiles = preferenceProfiles,
                                currentProfileId = activeProfileId,
                                onSelectMemory = onSelectMemory,
                                expanded = showMemoryDropdown,
                                onExpandedChange = { showMemoryDropdown = it },
                                onInfoClick = {
                                        infoPopupContent =
                                                context.getString(R.string.memory) to context.getString(R.string.memory_desc)
                                        showMenu = false
                                    },
                                    onManageClick = {
                                        onNavigateToUserPreferences()
                                    showMenu = false
                                }
                            )

                            // 记忆附着
                            SettingItem(
                                title = stringResource(R.string.memory_attachment),
                                    icon =
                                            if (enableMemoryAttachment) Icons.Rounded.Link
                                            else Icons.Outlined.LinkOff,
                                    iconTint =
                                            if (enableMemoryAttachment)
                                                    MaterialTheme.colorScheme.primary
                                            else
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.7f
                                                    ),
                                isChecked = enableMemoryAttachment,
                                onToggle = onToggleMemoryAttachment,
                                onInfoClick = {
                                        infoPopupContent =
                                                context.getString(R.string.memory_attachment) to context.getString(R.string.memory_attachment_desc)
                                    showMenu = false
                                }
                            )

                            // 上下文长度设置
                            // SettingSliderItem(
                            //     label = "上下文长度",
                            //     icon = Icons.Outlined.History,
                            //     value = maxWindowSizeInK,
                            //     onValueChange = onContextLengthChange,
                            //     onInfoClick = {
                            //         infoPopupContent =
                            //             "上下文长度" to
                            //                 "控制模型记忆的对话长度（单位：千tokens）。较短的长度可以节省Token，但可能会忘记早期对话内容。"
                            //         showMenu = false
                            //     },
                            //     valueRange = 1f..1024f,
                            //     steps = 1022,
                            //     decimalFormatPattern = "#.#",
                            //     unitText = "k"
                            // )

                            // // 摘要阈值设置
                            // SettingSliderItem(
                            //     label = "摘要阈值",
                            //     icon = Icons.Outlined.Speed,
                            //     value = summaryTokenThreshold,
                            //     onValueChange = onSummaryTokenThresholdChange,
                            //     onInfoClick = {
                            //         infoPopupContent =
                            //             "摘要阈值" to
                            //                 "当上下文Token使用率超过此阈值时，自动触发聊天摘要。范围 0.1-0.95。"
                            //         showMenu = false
                            //     },
                            //     valueRange = 0.1f..0.95f,
                            //     steps = 84,
                            //     decimalFormatPattern = "#.##"
                            // )

                            Divider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                thickness = 0.5.dp,
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.2f
                                            )
                            )

                            // 自动朗读
                            SettingItem(
                                title = stringResource(R.string.auto_read_message),
                                    icon =
                                            if (isAutoReadEnabled) Icons.Rounded.VolumeUp
                                            else Icons.Outlined.VolumeOff,
                                    iconTint =
                                            if (isAutoReadEnabled)
                                                    MaterialTheme.colorScheme.primary
                                            else
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.7f
                                                    ),
                                isChecked = isAutoReadEnabled,
                                onToggle = onToggleAutoRead,
                                onInfoClick = {
                                        infoPopupContent =
                                                context.getString(R.string.auto_read_message) to context.getString(R.string.auto_read_desc)
                                    showMenu = false
                                }
                            )
                            
                            Divider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                thickness = 0.5.dp,
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.2f
                                            )
                            )

                            // AI计划模式
                            SettingItem(
                                title = stringResource(R.string.ai_planning_mode),
                                    icon =
                                            if (enableAiPlanning) Icons.Outlined.Hub
                                            else Icons.Outlined.Hub,
                                    iconTint =
                                            if (enableAiPlanning) MaterialTheme.colorScheme.primary
                                            else
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.7f
                                                    ),
                                isChecked = enableAiPlanning,
                                onToggle = onToggleAiPlanning,
                                onInfoClick = {
                                        infoPopupContent =
                                                context.getString(R.string.ai_planning_mode) to context.getString(R.string.ai_planning_desc)
                                    showMenu = false
                                }
                            )

                            Divider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                thickness = 0.5.dp,
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.2f
                                            )
                            )

                            // 自动批准
                            SettingItem(
                                title = stringResource(R.string.auto_approve),
                                    icon =
                                            if (permissionLevel == PermissionLevel.ALLOW)
                                                    Icons.Rounded.Security
                                            else Icons.Outlined.Security,
                                    iconTint =
                                            if (permissionLevel == PermissionLevel.ALLOW)
                                                    MaterialTheme.colorScheme.primary
                                            else
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.7f
                                                    ),
                                isChecked = permissionLevel == PermissionLevel.ALLOW,
                                onToggle = onTogglePermission,
                                onInfoClick = {
                                        infoPopupContent =
                                                context.getString(R.string.auto_approve) to context.getString(R.string.auto_approve_desc)
                                    showMenu = false
                                }
                            )

                            Divider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                thickness = 0.5.dp,
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.2f
                                            )
                            )

                            // 思考模式
                            SettingItem(
                                title = stringResource(R.string.thinking_mode),
                                    icon =
                                            if (enableThinkingMode) Icons.Rounded.Psychology
                                            else Icons.Outlined.Psychology,
                                    iconTint =
                                            if (enableThinkingMode)
                                                    MaterialTheme.colorScheme.primary
                                            else
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.7f
                                                    ),
                                isChecked = enableThinkingMode,
                                onToggle = onToggleThinkingMode,
                                onInfoClick = {
                                    infoPopupContent = context.getString(R.string.thinking_mode) to context.getString(R.string.thinking_mode_desc)
                                    showMenu = false
                                }
                            )

                            Divider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                thickness = 0.5.dp,
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.2f
                                            )
                            )

                            // 思考引导
                            SettingItem(
                                title = stringResource(R.string.thinking_guidance),
                                    icon =
                                            if (enableThinkingGuidance) Icons.Rounded.TipsAndUpdates
                                            else Icons.Outlined.TipsAndUpdates,
                                    iconTint =
                                            if (enableThinkingGuidance)
                                                    MaterialTheme.colorScheme.primary
                                            else
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.7f
                                                    ),
                                isChecked = enableThinkingGuidance,
                                onToggle = onToggleThinkingGuidance,
                                onInfoClick = {
                                        infoPopupContent =
                                                context.getString(R.string.thinking_guidance) to context.getString(R.string.thinking_guidance_desc)
                                    showMenu = false
                                }
                            )

                            // 手动更新记忆
                            ActionSettingItem(
                                title = stringResource(R.string.manual_memory_update),
                                icon = Icons.Outlined.Save,
                                iconTint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                onClick = {
                                    onManualMemoryUpdate()
                                    showMenu = false
                                },
                                onInfoClick = {
                                    infoPopupContent =
                                        context.getString(R.string.manual_memory_update) to context.getString(R.string.manual_memory_update_desc)
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 详情说明弹窗
        if (infoPopupContent != null) {
            Popup(
                alignment = Alignment.TopStart, // 将弹窗对齐到父布局的左上角
                onDismissRequest = { infoPopupContent = null },
                    properties =
                            PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                        modifier =
                                Modifier.padding(
                                        top = 0.dp,
                                        bottom = 76.dp,
                                        end = 40.dp
                                ) // 调整边距，使其显示在左侧
                ) {
                    Card(
                        modifier = Modifier.width(220.dp),
                        shape = RoundedCornerShape(8.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.surface.copy(
                                                            alpha = 0.95f
                                                    )
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = infoPopupContent!!.first,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Divider(
                                thickness = 0.5.dp,
                                    color =
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = 0.3f
                                            )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = infoPopupContent!!.second,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    isChecked: Boolean,
    onToggle: () -> Unit,
    onInfoClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        // 详情按钮（左侧）
        IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
        // 文本
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        // 开关
        Switch(
            checked = isChecked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.scale(0.65f),
                colors =
                        SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

@Composable
private fun SettingSliderItem(
    label: String,
    icon: ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
    onInfoClick: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    decimalFormatPattern: String,
    unitText: String? = null
) {
    var sliderValue by remember { mutableStateOf(value) }
    val df = remember(decimalFormatPattern) { DecimalFormat(decimalFormatPattern) }
    var textValue by remember { mutableStateOf(df.format(value)) }
    val focusManager = LocalFocusManager.current

    // When the external value changes, sync the internal state
    LaunchedEffect(value) {
        sliderValue = value
        textValue = df.format(value)
    }

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            // Info button
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            BasicTextField(
                value = textValue,
                onValueChange = { newText ->
                    textValue = newText
                    newText.toFloatOrNull()?.let {
                        sliderValue = it.coerceIn(valueRange)
                    }
                },
                modifier = Modifier
                    .width(50.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
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

            // Here is the fix for alignment
            Box(modifier = Modifier.width(24.dp), contentAlignment = Alignment.CenterStart) {
                if (unitText != null) {
                    Text(
                        text = unitText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }

        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                textValue = df.format(it)
            },
            onValueChangeFinished = { onValueChange(sliderValue) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth().height(24.dp)
        )
    }
}

@Composable
private fun MemorySelectorItem(
    preferenceProfiles: List<PreferenceProfile>,
    currentProfileId: String,
    onSelectMemory: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onInfoClick: () -> Unit,
    onManageClick: () -> Unit
) {
    val currentProfile = preferenceProfiles.find { it.id == currentProfileId }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                .height(36.dp)
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Portrait,
                contentDescription = stringResource(R.string.memory_selection),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            // 详情按钮（左侧）
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Row(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.memory) + ":",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentProfile?.name ?: stringResource(R.string.not_selected),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                    imageVector =
                            if (expanded) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) stringResource(R.string.collapse_verb) else stringResource(R.string.expand_verb),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (expanded) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                preferenceProfiles.forEach { profile ->
                    val isSelected = profile.id == currentProfileId
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                    if (isSelected)
                                                            MaterialTheme.colorScheme.primary.copy(
                                                                    alpha = 0.1f
                                                            )
                                                    else Color.Transparent
                                            )
                            .clickable {
                                onSelectMemory(profile.id)
                                onExpandedChange(false)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = profile.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color =
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (preferenceProfiles.last() != profile) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                Divider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clickable(onClick = onManageClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.manage_config), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ModelSelectorItem(
    configSummaries: List<ModelConfigSummary>,
    currentConfigId: String,
    onSelectModel: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onManageClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val currentConfig = configSummaries.find { it.id == currentConfigId }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
                modifier =
                        Modifier.fillMaxWidth()
                .height(36.dp)
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.DataObject,
                contentDescription = stringResource(R.string.model_selection),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            // 详情按钮（左侧）
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.model) + ":",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentConfig?.name ?: stringResource(R.string.not_selected),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) stringResource(R.string.collapse_verb) else stringResource(R.string.expand_verb),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (expanded) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                configSummaries.forEach { config ->
                    val isSelected = config.id == currentConfigId
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                    if (isSelected)
                                                            MaterialTheme.colorScheme.primary.copy(
                                                                    alpha = 0.1f
                                                            )
                                                    else Color.Transparent
                                            )
                            .clickable {
                                onSelectModel(config.id)
                                onExpandedChange(false)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = config.name,
                                    fontWeight =
                                            if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color =
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = config.modelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (configSummaries.last() != config) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                Divider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clickable(onClick = onManageClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.manage_config), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun PromptSelectorItem(
    promptProfiles: List<PromptProfile>,
    currentProfileId: String,
    onSelectPrompt: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onManageClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val currentProfile = promptProfiles.find { it.id == currentProfileId }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp).clickable { onExpandedChange(!expanded) }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Message,
                contentDescription = stringResource(R.string.prompt_selection),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            // 详情按钮（左侧）
            IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.details),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Row(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.prompt) + ":",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = currentProfile?.name ?: stringResource(R.string.not_selected),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) stringResource(R.string.collapse_verb) else stringResource(R.string.expand_verb),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }

        if (expanded) {
            Column(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                promptProfiles.forEach { profile ->
                    val isSelected = profile.id == currentProfileId
                    Box(
                            modifier =
                                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                    if (isSelected)
                                                            MaterialTheme.colorScheme.primary.copy(
                                                                    alpha = 0.1f
                                                            )
                                                    else Color.Transparent
                                            )
                            .clickable {
                                onSelectPrompt(profile.id)
                                onExpandedChange(false)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = profile.name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color =
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (promptProfiles.last() != profile) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                Divider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clickable(onClick = onManageClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.manage_config), color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ActionSettingItem(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    Row(
        modifier =
                Modifier.fillMaxWidth()
                        .height(36.dp)
                        .padding(vertical = 2.dp)
                        .padding(horizontal = 3.dp)
                        .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClick)
                        .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 图标
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        // 详情按钮（左侧）
        IconButton(onClick = onInfoClick, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.details),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
        // 文本
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
    }
}
