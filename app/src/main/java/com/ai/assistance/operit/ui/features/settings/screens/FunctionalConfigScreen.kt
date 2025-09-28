package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.api.chat.AIServiceFactory
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.ModelConfigSummary
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.FunctionalConfigManager
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunctionalConfigScreen(
        onBackPressed: () -> Unit = {},
        onNavigateToModelConfig: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 配置管理器
    val functionalConfigManager = remember { FunctionalConfigManager(context) }
    val modelConfigManager = remember { ModelConfigManager(context) }

    // 配置映射状态
    val configMapping =
            functionalConfigManager.functionConfigMappingFlow.collectAsState(initial = emptyMap())

    // 配置摘要列表
    var configSummaries by remember { mutableStateOf<List<ModelConfigSummary>>(emptyList()) }

    // UI状态
    var isLoading by remember { mutableStateOf(true) }
    var showSaveSuccess by remember { mutableStateOf(false) }

    // 加载配置摘要
    LaunchedEffect(Unit) {
        isLoading = true
        configSummaries = modelConfigManager.getAllConfigSummaries()
        isLoading = false
    }

    CustomScaffold() { paddingValues ->
        if (isLoading) {
            Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                    modifier =
                            Modifier.fillMaxSize()
                                    .padding(paddingValues)
                                    .padding(horizontal = 16.dp)
            ) {
                item {
                    Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor =
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                            alpha = 0.7f
                                                    )
                                    )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = stringResource(id = R.string.functional_model_config_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                    text = stringResource(id = R.string.functional_model_config_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Divider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )

                            Row(
                                    modifier =
                                            Modifier.fillMaxWidth()
                                                    .clickable { onNavigateToModelConfig() }
                                                    .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                    Text(
                                            text = stringResource(id = R.string.manage_all_model_configs),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                    )
                                    Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = stringResource(id = R.string.manage_model_configs_desc),
                                            tint = MaterialTheme.colorScheme.primary
                                    )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 功能类型列表
                items(FunctionType.values()) { functionType ->
                    val currentConfigId =
                            configMapping.value[functionType]
                                    ?: FunctionalConfigManager.DEFAULT_CONFIG_ID
                    val currentConfig = configSummaries.find { it.id == currentConfigId }

                    FunctionConfigCard(
                            functionType = functionType,
                            currentConfig = currentConfig,
                            availableConfigs = configSummaries,
                            onConfigSelected = { configId ->
                                scope.launch {
                                    functionalConfigManager.setConfigForFunction(
                                            functionType,
                                            configId
                                    )
                                    // 刷新服务实例
                                    EnhancedAIService.refreshServiceForFunction(
                                            context,
                                            functionType
                                    )
                                    showSaveSuccess = true
                                }
                            }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    // 重置按钮
                    OutlinedButton(
                            onClick = {
                                scope.launch {
                                    functionalConfigManager.resetAllFunctionConfigs()
                                    // 刷新所有服务实例
                                    EnhancedAIService.refreshAllServices(context)
                                    showSaveSuccess = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(id = R.string.reset_all_functions_to_default))
                    }

                    // 成功提示
                    androidx.compose.animation.AnimatedVisibility(
                            visible = showSaveSuccess,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                    ) {
                        Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.primaryContainer
                                        )
                        ) {
                            Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        text = stringResource(id = R.string.config_saved),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        LaunchedEffect(showSaveSuccess) {
                            kotlinx.coroutines.delay(2000)
                            showSaveSuccess = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FunctionConfigCard(
        functionType: FunctionType,
        currentConfig: ModelConfigSummary?,
        availableConfigs: List<ModelConfigSummary>,
        onConfigSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelConfigManager = remember { ModelConfigManager(context) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<String>?>(null) }

    LaunchedEffect(testResult) {
        if (testResult != null) {
            kotlinx.coroutines.delay(5000)
            testResult = null
        }
    }

    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border =
                    BorderStroke(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 功能标题和描述
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                        text = getFunctionDisplayName(functionType),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                )

                Text(
                        text = getFunctionDescription(functionType),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 当前配置
                Surface(
                        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    text = stringResource(
                                        id = R.string.current_config_label,
                                        currentConfig?.name ?: stringResource(id = R.string.default_config)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                            )

                            if (currentConfig != null) {
                                Text(
                                        text = stringResource(id = R.string.model_label, currentConfig.modelName),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // 测试结果显示
                            androidx.compose.animation.AnimatedVisibility(
                                visible = testResult != null,
                                enter = fadeIn() + slideInHorizontally(),
                                exit = fadeOut() + slideOutHorizontally()
                            ) {
                                testResult?.let { result ->
                                    val isSuccess = result.isSuccess
                                    val message =
                                            if (isSuccess) result.getOrNull() ?: stringResource(id = R.string.test_connection_success)
                                            else stringResource(id = R.string.test_connection_failed, result.exceptionOrNull()?.message?.take(30) ?: "")
                                    val color =
                                            if (isSuccess) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.error
                                    val icon =
                                            if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Icon(
                                                icon,
                                                contentDescription = null,
                                                tint = color,
                                                modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                                message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = color,
                                                maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 测试按钮
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isTestingConnection = true
                                        testResult = null
                                        try {
                                            val configId =
                                                    currentConfig?.id
                                                            ?: FunctionalConfigManager.DEFAULT_CONFIG_ID
                                            val fullConfig =
                                                    modelConfigManager.getModelConfigFlow(configId).first()
                                            
                                            // 异步获取自定义请求头
                                            val apiPreferences = ApiPreferences.getInstance(context)
                                            val customHeadersJson = apiPreferences.getCustomHeaders()

                                            val service =
                                                    AIServiceFactory.createService(
                                                            config = fullConfig,
                                                            customHeadersJson = customHeadersJson,
                                                            modelConfigManager = modelConfigManager
                                                    )
                                            testResult = service.testConnection()
                                        } catch (e: Exception) {
                                            testResult = Result.failure(e)
                                        }
                                        isTestingConnection = false
                                    }
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                            Icons.Default.Dns,
                                            contentDescription = stringResource(id = R.string.test_connection_desc),
                                            modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(id = R.string.test), style = MaterialTheme.typography.bodySmall)
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Icon(
                                    imageVector =
                                            if (expanded) Icons.Default.KeyboardArrowUp
                                            else Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(id = R.string.expand_desc),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 配置列表
            Box(modifier = Modifier.fillMaxWidth()) {
                androidx.compose.animation.AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                                text = stringResource(id = R.string.select_config),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(bottom = 8.dp)
                        )

                        availableConfigs.forEach { config ->
                            val isSelected =
                                    config.id ==
                                            (currentConfig?.id
                                                    ?: FunctionalConfigManager.DEFAULT_CONFIG_ID)

                            Surface(
                                    modifier =
                                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                                onConfigSelected(config.id)
                                                expanded = false
                                            },
                                    shape = RoundedCornerShape(8.dp),
                                    color =
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surface,
                                    border =
                                            BorderStroke(
                                                    width = if (isSelected) 0.dp else 0.5.dp,
                                                    color =
                                                            if (isSelected)
                                                                    MaterialTheme.colorScheme.primary
                                                            else
                                                                    MaterialTheme.colorScheme
                                                                            .outlineVariant.copy(
                                                                            alpha = 0.5f
                                                                    )
                                            )
                            ) {
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelected) {
                                        Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = stringResource(id = R.string.selected_desc),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                text = config.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight =
                                                        if (isSelected) FontWeight.Bold
                                                        else FontWeight.Normal,
                                                color =
                                                        if (isSelected)
                                                                MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurface
                                        )

                                        Text(
                                                text = config.modelName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Divider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(0.3f)
            )
        }
    }
}

// 获取功能类型的显示名称
@Composable
fun getFunctionDisplayName(functionType: FunctionType): String {
    return when (functionType) {
        FunctionType.CHAT -> stringResource(id = R.string.function_type_chat)
        FunctionType.SUMMARY -> stringResource(id = R.string.function_type_summary)
        FunctionType.PROBLEM_LIBRARY -> stringResource(id = R.string.function_type_problem_library)
        FunctionType.FILE_BINDING -> stringResource(id = R.string.function_type_file_binding)
        FunctionType.UI_CONTROLLER -> stringResource(id = R.string.function_type_ui_controller)
        FunctionType.TRANSLATION -> stringResource(id = R.string.function_type_translation)
    }
}

// 获取功能类型的描述
@Composable
fun getFunctionDescription(functionType: FunctionType): String {
    return  when (functionType) {
        FunctionType.CHAT -> stringResource(id = R.string.function_desc_chat)
        FunctionType.SUMMARY -> stringResource(id = R.string.function_desc_summary)
        FunctionType.PROBLEM_LIBRARY -> stringResource(id = R.string.function_desc_problem_library)
        FunctionType.FILE_BINDING -> stringResource(id = R.string.function_desc_file_binding)
        FunctionType.UI_CONTROLLER -> stringResource(id = R.string.function_desc_ui_controller)
        FunctionType.TRANSLATION -> stringResource(id = R.string.function_desc_translation)
    }
}
