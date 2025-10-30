package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.animation.*
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.llmprovider.AIServiceFactory
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.ui.features.settings.sections.AdvancedSettingsSection
import com.ai.assistance.operit.ui.features.settings.sections.ModelApiSettingsSection
import com.ai.assistance.operit.ui.features.settings.sections.ModelParametersSection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelConfigScreen(
    onBackPressed: () -> Unit = {},
    navigateToMnnModelDownload: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val configManager = remember { ModelConfigManager(context) }
            val apiPreferences = remember { ApiPreferences.getInstance(context) }
    val scope = rememberCoroutineScope()

    // 配置状态
    val configList = configManager.configListFlow.collectAsState(initial = listOf("default")).value
    // 不再使用activeConfigIdFlow，默认选择第一个配置
    var selectedConfigId by remember { mutableStateOf(configList.firstOrNull() ?: "default") }
    val selectedConfig = remember { mutableStateOf<ModelConfigData?>(null) }

    // 配置名称映射
    val configNameMap = remember { mutableStateMapOf<String, String>() }

    // UI状态
    var showAddConfigDialog by remember { mutableStateOf(false) }
    var showSaveSuccessMessage by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var newConfigName by remember { mutableStateOf("") }
    var confirmMessage by remember { mutableStateOf("") }

    // 连接测试状态
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<String>?>(null) }
    
    // 保存API设置的函数引用
    var saveApiSettings: (() -> Unit)? by remember { mutableStateOf(null) }

    // 初始化配置管理器
    LaunchedEffect(Unit) { configManager.initializeIfNeeded() }

    // 加载所有配置名称
    LaunchedEffect(configList) {
        configList.forEach { id ->
            val config = configManager.getModelConfigFlow(id).first()
            configNameMap[id] = config.name
        }
    }

    // 加载选中的配置
    LaunchedEffect(selectedConfigId) {
        configManager.getModelConfigFlow(selectedConfigId).collect { config ->
            selectedConfig.value = config
        }
    }

    // 自动隐藏测试结果
    LaunchedEffect(testResult) {
        if (testResult != null) {
            kotlinx.coroutines.delay(5000)
            testResult = null
        }
    }

    // 显示通知消息
    fun showNotification(message: String) {
        confirmMessage = message
        showSaveSuccessMessage = true
        scope.launch {
            kotlinx.coroutines.delay(3000)
            showSaveSuccessMessage = false
        }
    }

    // 主界面内容
    CustomScaffold() { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp).verticalScroll(rememberScrollState())) {

            // 配置选择区域
            Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border =
                            BorderStroke(
                                    0.7.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    // 配置选择标题行
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 选择配置标题
                        Text(
                                stringResource(R.string.select_model_config),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                        )

                        // 新建按钮
                        OutlinedButton(
                                onClick = { showAddConfigDialog = true },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp),
                                colors =
                                        ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.primary
                                        )
                        ) {
                            Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(stringResource(R.string.new_action), fontSize = 12.sp, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    val selectedConfigName = configNameMap[selectedConfigId] ?: stringResource(R.string.default_profile)

                    // 当前选中配置显示框
                    Surface(
                            modifier = Modifier.fillMaxWidth().clickable { isDropdownExpanded = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            tonalElevation = 0.5.dp,
                    ) {
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                        text = selectedConfigName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // 下拉箭头动画
                            @OptIn(ExperimentalAnimationApi::class)
                            AnimatedContent(
                                    targetState = isDropdownExpanded,
                                    transitionSpec = {
                                        fadeIn() + scaleIn() with fadeOut() + scaleOut()
                                    }
                            ) { expanded ->
                                Icon(
                                        if (expanded) Icons.Default.KeyboardArrowUp
                                        else Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(R.string.select_config),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 配置操作按钮
                    Row(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 删除按钮 - 不能删除默认配置
                        if (selectedConfigId != "default") {
                            TextButton(
                                    onClick = {
                                        scope.launch {
                                            configManager.deleteConfig(selectedConfigId)
                                            selectedConfigId = configList.firstOrNull() ?: "default"
                                            showNotification(context.getString(R.string.config_deleted))
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    colors =
                                            ButtonDefaults.textButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.error
                                            ),
                                    modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.delete_action), fontSize = 14.sp)
                            }
                        }

                        // 测试连接按钮
                        TextButton(
                                onClick = {
                                    scope.launch {
                                        // 先保存API设置
                                        saveApiSettings?.invoke()
                                        // 延迟以确保保存操作完成
                                        kotlinx.coroutines.delay(300)
                                        
                                        isTestingConnection = true
                                        testResult = null
                                        try {
                                            selectedConfig.value?.let { config ->
                                                // 异步获取自定义请求头
                                                val customHeadersJson = apiPreferences.getCustomHeaders()
                                                val service =
                                                        AIServiceFactory.createService(
                                                                config = config,
                                                                customHeadersJson = customHeadersJson,
                                                                modelConfigManager = configManager,
                                                                context = context
                                                        )
                                                testResult = service.testConnection()
                                            } ?: run {
                                                testResult = Result.failure(Exception(context.getString(R.string.no_config_selected)))
                                            }
                                        } catch (e: Exception) {
                                            testResult = Result.failure(e)
                                        }
                                        isTestingConnection = false
                                    }
                                },
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                        Icons.Default.Dns,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.test_connection_desc), fontSize = 14.sp)
                        }
                    }

                    // 显示测试结果
                    AnimatedVisibility(
                            visible = testResult != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                    ) {
                        testResult?.let { result ->
                            val isSuccess = result.isSuccess
                            val message =
                                    if (isSuccess) result.getOrNull() ?: context.getString(R.string.connection_test_success)
                                    else context.getString(R.string.connection_test_failed, result.exceptionOrNull()?.message ?: "")
                            val containerColor =
                                    if (isSuccess)
                                            MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.errorContainer
                            val contentColor =
                                    if (isSuccess)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onErrorContainer
                            val icon =
                                    if (isSuccess) Icons.Default.CheckCircle
                                    else Icons.Default.Warning

                            Card(
                                    modifier =
                                            Modifier.fillMaxWidth().padding(top = 12.dp),
                                    colors = CardDefaults.cardColors(containerColor = containerColor),
                                    shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = contentColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                            text = message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = contentColor
                                    )
                                }
                            }
                        }
                    }
                }

                // 默认配置警告提示
                AnimatedVisibility(visible = selectedConfigId == "default") {
                    Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors =
                                    CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                    ),
                            shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                    text = stringResource(R.string.default_config_warning),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // 配置下拉菜单
                DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false },
                        modifier = Modifier.width(280.dp),
                        properties = PopupProperties(focusable = true)
                ) {
                    configList.forEach { configId ->
                        val configName = configNameMap[configId] ?: stringResource(R.string.unnamed_profile)
                        val isSelected = configId == selectedConfigId

                        DropdownMenuItem(
                                text = {
                                    Text(
                                            text = configName,
                                            fontWeight =
                                                    if (isSelected) FontWeight.SemiBold
                                                    else FontWeight.Normal,
                                            color =
                                                    if (isSelected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon =
                                        if (isSelected) {
                                            {
                                                Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = stringResource(R.string.selected_desc),
                                                        modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        } else null,
                                onClick = {
                                    selectedConfigId = configId
                                    isDropdownExpanded = false
                                },
                                colors =
                                        MenuDefaults.itemColors(
                                                textColor =
                                                        if (isSelected)
                                                                MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.onSurface
                                        ),
                                modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        if (configId != configList.last()) {
                            Divider(modifier = Modifier.padding(horizontal = 8.dp), thickness = 0.5.dp)
                        }
                    }
                }
            }

            // API设置区域
            selectedConfig.value?.let { config ->
                ModelApiSettingsSection(
                    config = config,
                    configManager = configManager,
                    showNotification = { message -> showNotification(message) },
                    onSaveRequested = { saveFunction -> saveApiSettings = saveFunction },
                    navigateToMnnModelDownload = navigateToMnnModelDownload
                )
            }

            // 高级设置区域（API密钥池配置）
            selectedConfig.value?.let { config ->
                AdvancedSettingsSection(
                    config = config,
                    configManager = configManager,
                    showNotification = { message -> showNotification(message) }
                )
            }

            // 模型参数区域
            selectedConfig.value?.let { config ->
                ModelParametersSection(
                        config = config,
                        configManager = configManager,
                        showNotification = { message -> showNotification(message) }
                )
            }

            // 操作成功消息显示
            AnimatedVisibility(
                    visible = showSaveSuccessMessage,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                ) {
                    Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = confirmMessage,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // 新建配置对话框
        if (showAddConfigDialog) {
            AlertDialog(
                    onDismissRequest = {
                        showAddConfigDialog = false
                        newConfigName = ""
                    },
                    title = {
                        Text(
                                stringResource(R.string.new_model_config),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                    stringResource(R.string.new_model_config_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                    value = newConfigName,
                                    onValueChange = { newConfigName = it },
                                    label = { Text(stringResource(R.string.model_config_name), fontSize = 12.sp) },
                                    placeholder = { Text(stringResource(R.string.model_config_name_placeholder), fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                                onClick = {
                                    if (newConfigName.isNotBlank()) {
                                        scope.launch {
                                            val configId = configManager.createConfig(newConfigName)
                                            selectedConfigId = configId
                                            showAddConfigDialog = false
                                            newConfigName = ""
                                            showNotification(context.getString(R.string.new_config_created))
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp)
                        ) { Text(stringResource(R.string.create_action), fontSize = 13.sp) }
                    },
                    dismissButton = {
                        TextButton(
                                onClick = {
                                    showAddConfigDialog = false
                                    newConfigName = ""
                                }
                        ) { Text(stringResource(R.string.cancel_action), fontSize = 13.sp) }
                    },
                    shape = RoundedCornerShape(12.dp)
            )
        }
    }
}
