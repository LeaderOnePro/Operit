package com.ai.assistance.operit.ui.features.assistant.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.model.PromptFunctionType
import com.ai.assistance.operit.data.preferences.*
import com.ai.assistance.operit.ui.features.assistant.components.AvatarConfigSection
import com.ai.assistance.operit.ui.features.assistant.components.AvatarPreviewSection
import com.ai.assistance.operit.ui.features.assistant.components.HowToImportSection
import com.ai.assistance.operit.ui.features.assistant.components.SettingItem
import com.ai.assistance.operit.ui.features.assistant.viewmodel.AssistantConfigViewModel
import com.ai.assistance.operit.ui.features.settings.screens.getFunctionDisplayName

/** 助手配置屏幕 提供DragonBones模型预览和相关配置 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantConfigScreen(
        navigateToModelConfig: () -> Unit,
        navigateToModelPrompts: () -> Unit,
        navigateToFunctionalConfig: () -> Unit,
        navigateToUserPreferences: () -> Unit
) {
        val context = LocalContext.current
        val viewModel: AssistantConfigViewModel =
                viewModel(factory = AssistantConfigViewModel.Factory(context))
        val uiState by viewModel.uiState.collectAsState()

        // Preferences Managers
        val functionalConfigManager = remember { FunctionalConfigManager(context) }
        val modelConfigManager = remember { ModelConfigManager(context) }
        val userPrefsManager = remember { UserPreferencesManager(context) }

        // 启动文件选择器
        val zipFileLauncher =
                rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                        if (result.resultCode == Activity.RESULT_OK) {
                                result.data?.data?.let { uri ->
                                        // 导入选择的zip文件
                                        viewModel.importAvatarFromZip(uri)
                                }
                        }
                }

        // 打开文件选择器的函数
        val openZipFilePicker = {
                val intent =
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/zip"
                                putExtra(
                                        Intent.EXTRA_MIME_TYPES,
                                        arrayOf("application/zip", "application/x-zip-compressed")
                                )
                        }
                zipFileLauncher.launch(intent)
        }

        // State for the selected function type
        var selectedFunctionType by remember { mutableStateOf(PromptFunctionType.CHAT) }

        // 获取当前活跃的用户偏好/性格配置
        val activeUserPrefProfileId by
                userPrefsManager.activeProfileIdFlow.collectAsState(initial = "default")
        val activeUserPrefProfile by
                userPrefsManager
                        .getUserPreferencesFlow(activeUserPrefProfileId)
                        .collectAsState(initial = null)
        val activeUserPrefProfileName = activeUserPrefProfile?.name ?: stringResource(R.string.processing)

        val functionType =
                when (selectedFunctionType) {
                        PromptFunctionType.CHAT -> FunctionType.CHAT
                        PromptFunctionType.VOICE -> FunctionType.SUMMARY
                        PromptFunctionType.DESKTOP_PET -> FunctionType.PROBLEM_LIBRARY
                }
        val modelConfigId = remember { mutableStateOf(FunctionalConfigManager.DEFAULT_CONFIG_ID) }
        LaunchedEffect(selectedFunctionType) {
                modelConfigId.value = functionalConfigManager.getConfigIdForFunction(functionType)
        }
        val modelConfig by
                modelConfigManager
                        .getModelConfigFlow(modelConfigId.value)
                        .collectAsState(initial = null)

        val snackbarHostState = remember { SnackbarHostState() }
        val scrollState = rememberScrollState(initial = uiState.scrollPosition)

        // 在 Composable 函数中获取字符串资源，以便在 LaunchedEffect 中使用
        val operationSuccessString = context.getString(R.string.operation_success)
        val errorOccurredString = context.getString(R.string.error_occurred_simple)

        LaunchedEffect(scrollState) {
                snapshotFlow { scrollState.value }.collect { position ->
                        viewModel.updateScrollPosition(position)
                }
        }

        // 显示操作结果的 SnackBar
        LaunchedEffect(uiState.operationSuccess, uiState.errorMessage) {
                if (uiState.operationSuccess) {
                        snackbarHostState.showSnackbar(operationSuccessString)
                        viewModel.clearOperationSuccess()
                } else if (uiState.errorMessage != null) {
                        snackbarHostState.showSnackbar(uiState.errorMessage ?: errorOccurredString)
                        viewModel.clearErrorMessage()
                }
        }

        CustomScaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                        TopAppBar(
                                title = { Text(stringResource(R.string.assistant_config_title)) },
                                actions = {
                                        // 导入模型按钮
                                        IconButton(
                                                onClick = openZipFilePicker,
                                                enabled = !uiState.isImporting && !uiState.isLoading
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.FileUpload,
                                                        contentDescription = stringResource(R.string.import_model)
                                                )
                                        }

                                        // 刷新模型列表按钮
                                        IconButton(
                                                onClick = { viewModel.scanUserAvatars() },
                                                enabled =
                                                        !uiState.isImporting &&
                                                                !uiState.isLoading &&
                                                                !uiState.isScanning
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = stringResource(R.string.scan_user_models)
                                                )
                                        }
                                }
                        )
                }
        ) { paddingValues ->
                Box(modifier = Modifier.fillMaxSize()) {
                        // 主要内容
                        Column(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(paddingValues)
                                                .padding(horizontal = 12.dp)
                                                .verticalScroll(scrollState)
                        ) {
                                // Avatar预览区域
                                AvatarPreviewSection(
                                        modifier = Modifier.fillMaxWidth().height(300.dp),
                                        uiState = uiState,
                                        onDeleteCurrentModel =
                                                uiState.currentAvatarConfig?.let { model ->
                                                        { viewModel.deleteAvatar(model.id) }
                                                }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                AvatarConfigSection(
                                        viewModel = viewModel,
                                        uiState = uiState,
                                        onImportClick = { openZipFilePicker() }
                                )

                                HowToImportSection()

                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.1f
                                                )
                                ) {
                                        Column(
                                                modifier =
                                                        Modifier.padding(
                                                                vertical = 4.dp,
                                                                horizontal = 8.dp
                                                        )
                                        ) {

                                                SettingItem(
                                                        icon = Icons.Default.Face,
                                                        title = stringResource(R.string.user_personality),
                                                        value = activeUserPrefProfileName,
                                                        onClick = navigateToUserPreferences
                                                )

                                                SettingItem(
                                                        icon = Icons.Default.Api,
                                                        title = stringResource(R.string.function_model),
                                                        value = modelConfig?.name ?: stringResource(R.string.not_configured),
                                                        onClick = navigateToFunctionalConfig
                                                )
                                        }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // 底部空间
                                Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 加载指示器覆盖层
                        if (uiState.isLoading || uiState.isImporting) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxSize()
                                                        .background(
                                                                MaterialTheme.colorScheme.surface
                                                                        .copy(alpha = 0.7f)
                                                        ),
                                        contentAlignment = Alignment.Center
                                ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                CircularProgressIndicator()
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                        text =
                                                                if (uiState.isImporting) stringResource(R.string.importing_model)
                                                                else stringResource(R.string.processing),
                                                        style = MaterialTheme.typography.bodyMedium
                                                )
                                        }
                                }
                        }
                }
        }
}
