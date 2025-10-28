package com.ai.assistance.operit.ui.features.settings.sections

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.chat.llmprovider.EndpointCompleter
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.ModelListFetcher
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.util.LocationUtils
import kotlinx.coroutines.launch

val TAG = "ModelApiSettings"

@Composable
@SuppressLint("MissingPermission")
fun ModelApiSettingsSection(
        config: ModelConfigData,
        configManager: ModelConfigManager,
        showNotification: (String) -> Unit,
        navigateToMnnModelDownload: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 区域告警可见性
    var showRegionWarning by remember { mutableStateOf(false) }

    // 获取每个提供商的默认模型名称
    fun getDefaultModelName(providerType: ApiProviderType): String {
        return when (providerType) {
            ApiProviderType.OPENAI -> "gpt-4o"
            ApiProviderType.ANTHROPIC -> "claude-3-opus-20240229"
            ApiProviderType.GOOGLE -> "gemini-2.0-flash"
            ApiProviderType.DEEPSEEK -> "deepseek-chat"
            ApiProviderType.BAIDU -> "ernie-bot-4"
            ApiProviderType.ALIYUN -> "qwen-max"
            ApiProviderType.XUNFEI -> "spark3.5"
            ApiProviderType.ZHIPU -> "glm-4.5"
            ApiProviderType.BAICHUAN -> "baichuan4"
            ApiProviderType.MOONSHOT -> "moonshot-v1-128k"
            ApiProviderType.SILICONFLOW -> "yi-1.5-34b"
            ApiProviderType.OPENROUTER -> "google/gemini-pro"
            ApiProviderType.INFINIAI -> "infini-mini"
            ApiProviderType.ALIPAY_BAILING -> "Ling-1T"
            ApiProviderType.LMSTUDIO -> "meta-llama-3.1-8b-instruct"
            ApiProviderType.MNN -> ""
            ApiProviderType.PPINFRA -> "gpt-4o-mini"
            ApiProviderType.OTHER -> ""
        }
    }

    // 检查当前模型名称是否是某个提供商的默认值
    fun isDefaultModelName(modelName: String): Boolean {
        return ApiProviderType.values().any { getDefaultModelName(it) == modelName }
    }

    // API编辑状态
    var apiEndpointInput by remember(config.id) { mutableStateOf(config.apiEndpoint) }
    var apiKeyInput by remember(config.id) { mutableStateOf(config.apiKey) }
    var modelNameInput by remember(config.id) { mutableStateOf(config.modelName) }
    var selectedApiProvider by remember(config.id) { mutableStateOf(config.apiProviderType) }
    
    // MNN特定配置状态
    var mnnForwardTypeInput by remember(config.id) { mutableStateOf(config.mnnForwardType) }
    var mnnThreadCountInput by remember(config.id) { mutableStateOf(config.mnnThreadCount.toString()) }
    
    // 图片处理配置状态
    var enableDirectImageProcessingInput by remember(config.id) { mutableStateOf(config.enableDirectImageProcessing) }

    // 根据API提供商获取默认的API端点URL
    fun getDefaultApiEndpoint(providerType: ApiProviderType): String {
        return when (providerType) {
            ApiProviderType.OPENAI -> "https://api.openai.com/v1/chat/completions"
            ApiProviderType.ANTHROPIC -> "https://api.anthropic.com/v1/messages"
            ApiProviderType.GOOGLE -> "https://generativelanguage.googleapis.com/v1beta/models"
            ApiProviderType.DEEPSEEK -> "https://api.deepseek.com/v1/chat/completions"
            ApiProviderType.BAIDU ->
                    "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions"
            ApiProviderType.ALIYUN ->
                    "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
            ApiProviderType.XUNFEI -> "https://spark-api-open.xf-yun.com/v2/chat/completions"
            ApiProviderType.ZHIPU ->
                    "https://open.bigmodel.cn/api/paas/v4/chat/completions"
            ApiProviderType.BAICHUAN -> "https://api.baichuan-ai.com/v1/chat/completions"
            ApiProviderType.MOONSHOT -> "https://api.moonshot.cn/v1/chat/completions"
            ApiProviderType.SILICONFLOW -> "https://api.siliconflow.cn/v1/chat/completions"
            ApiProviderType.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
            ApiProviderType.INFINIAI -> "https://cloud.infini-ai.com/maas/v1/chat/completions"
            ApiProviderType.ALIPAY_BAILING -> "https://api.tbox.cn/api/llm/v1/chat/completions"
            ApiProviderType.LMSTUDIO -> "http://localhost:1234/v1/chat/completions"
            ApiProviderType.MNN -> "" // MNN本地推理不需要endpoint
            ApiProviderType.PPINFRA -> "https://api.ppinfra.com/openai/v1/chat/completions"
            ApiProviderType.OTHER -> ""
        }
    }

    // 添加一个函数检查当前API端点是否为某个提供商的默认端点
    fun isDefaultApiEndpoint(endpoint: String): Boolean {
        return ApiProviderType.values().any { getDefaultApiEndpoint(it) == endpoint }
    }

    // 当API提供商改变时更新端点
    LaunchedEffect(selectedApiProvider) {
        Log.d("ModelApiSettingsSection", "API提供商改变")
        if (selectedApiProvider == ApiProviderType.OPENAI || selectedApiProvider == ApiProviderType.GOOGLE
            || selectedApiProvider == ApiProviderType.ANTHROPIC) {
            val inChina = LocationUtils.isDeviceInMainlandChina(context)
            showRegionWarning = inChina
            if (inChina) {
                Log.d("ModelApiSettingsSection", "检测到位于中国大陆")
                showNotification(context.getString(R.string.overseas_provider_warning))
            } else {
                Log.d("ModelApiSettingsSection", "检测到位于海外")
            }
        } else {
            showRegionWarning = false
        }
        if (apiEndpointInput.isEmpty() || isDefaultApiEndpoint(apiEndpointInput)) {
            apiEndpointInput = getDefaultApiEndpoint(selectedApiProvider)
        }
    }

    // 模型列表状态
    var isLoadingModels by remember { mutableStateOf(false) }
    var showModelsDialog by remember { mutableStateOf(false) }
    var modelsList by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
    var modelLoadError by remember { mutableStateOf<String?>(null) }

    // 检查是否使用默认API密钥
    val isUsingDefaultApiKey = apiKeyInput == ApiPreferences.DEFAULT_API_KEY
    var showModelRestrictionInfo by remember { mutableStateOf(isUsingDefaultApiKey) }

    // 当使用默认API密钥时限制模型名称
    LaunchedEffect(apiKeyInput) {
        if (apiKeyInput == ApiPreferences.DEFAULT_API_KEY) {
            modelNameInput = ApiPreferences.DEFAULT_MODEL_NAME
            showModelRestrictionInfo = true
        } else {
            showModelRestrictionInfo = false
        }
    }

    // API设置卡片
    Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        // 区域警告（与外部容器边缘贴合）
        AnimatedVisibility(visible = showRegionWarning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ),
                shape = RoundedCornerShape(0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.overseas_provider_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                        imageVector = Icons.Default.Api,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = stringResource(R.string.api_settings),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                )
            }

            // MNN特定配置（仅在选择MNN提供商时显示）
            if (selectedApiProvider == ApiProviderType.MNN) {
                // MNN使用提示
                Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = stringResource(R.string.mnn_local_model_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                lineHeight = 18.sp
                        )
                    }
                }
                
                // 下载模型按钮
                navigateToMnnModelDownload?.let { navigate ->
                    Button(
                            onClick = navigate,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                    ) {
                        Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.mnn_model_download))
                    }
                }
                
                // MNN前向计算类型选择
                var showForwardTypeDialog by remember { mutableStateOf(false) }
                
                Text(
                        stringResource(R.string.mnn_forward_type),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                )
                
                Surface(
                        modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showForwardTypeDialog = true }
                                .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                                text = when (mnnForwardTypeInput) {
                                    0 -> "CPU"
                                    3 -> "OpenCL"
                                    4 -> "Auto"
                                    6 -> "OpenGL"
                                    7 -> "Vulkan"
                                    else -> "CPU"
                                },
                                style = MaterialTheme.typography.bodyLarge
                        )
                        Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.select),
                                tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                if (showForwardTypeDialog) {
                    Dialog(onDismissRequest = { showForwardTypeDialog = false }) {
                        Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                        stringResource(R.string.mnn_forward_type),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                )
                                
                                listOf(
                                    0 to "CPU",
                                    3 to "OpenCL",
                                    4 to "Auto",
                                    6 to "OpenGL",
                                    7 to "Vulkan"
                                ).forEach { (type, name) ->
                                    Surface(
                                            modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        mnnForwardTypeInput = type
                                                        showForwardTypeDialog = false
                                                    },
                                            color = if (mnnForwardTypeInput == type) 
                                                    MaterialTheme.colorScheme.primaryContainer 
                                                else MaterialTheme.colorScheme.surface
                                    ) {
                                        Text(
                                                text = name,
                                                modifier = Modifier.padding(16.dp),
                                                style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // MNN线程数输入
                OutlinedTextField(
                        value = mnnThreadCountInput,
                        onValueChange = { 
                            // 只允许输入数字
                            if (it.isEmpty() || it.toIntOrNull() != null) {
                                mnnThreadCountInput = it
                            }
                        },
                        label = { Text(stringResource(R.string.mnn_thread_count)) },
                        placeholder = { Text("4") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        singleLine = true
                )
            }

            // API端点输入（MNN提供商不显示）
            if (selectedApiProvider != ApiProviderType.MNN) {
            OutlinedTextField(
                    value = apiEndpointInput,
                    onValueChange = { apiEndpointInput = it },
                    label = { Text(stringResource(R.string.api_endpoint)) },
                    placeholder = { Text(stringResource(R.string.api_endpoint_placeholder)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
            )
            }

            // 显示自动补全后的URL
            val completedEndpoint = EndpointCompleter.completeEndpoint(apiEndpointInput)
            if (completedEndpoint != apiEndpointInput) {
                Text(
                    text = stringResource(R.string.actual_request_url, completedEndpoint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                Text(
                    text = stringResource(R.string.endpoint_completion_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    lineHeight = 16.sp
                )
            }
 
             // API密钥输入（MNN提供商不显示）
             if (selectedApiProvider != ApiProviderType.MNN) {
             OutlinedTextField(
                     value = if (isUsingDefaultApiKey) "" else apiKeyInput,
                     onValueChange = {
                         apiKeyInput = it

                         // 当API密钥改变时检查是否需要限制模型
                         if (it == ApiPreferences.DEFAULT_API_KEY) {
                             modelNameInput = ApiPreferences.DEFAULT_MODEL_NAME
                             showModelRestrictionInfo = true
                         } else {
                             showModelRestrictionInfo = false
                         }
                     },
                     label = { Text(stringResource(R.string.api_key)) },
                     placeholder = { Text(if (isUsingDefaultApiKey) stringResource(R.string.api_key_placeholder_default) else stringResource(R.string.api_key_placeholder_custom)) },
                     visualTransformation =
                             if (isUsingDefaultApiKey) VisualTransformation.None
                             else PasswordVisualTransformation(),
                     modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                     singleLine = true
             )
             }

            // 模型名称输入和模型列表按钮（MNN提供商不显示，因为MNN只需要选择模型，不需要手动输入）
            if (selectedApiProvider != ApiProviderType.MNN) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                OutlinedTextField(
                        value = modelNameInput,
                        onValueChange = {
                            // 只有在不使用默认API密钥时才允许更改
                            if (!isUsingDefaultApiKey) {
                                modelNameInput = it
                            }
                        },
                        label = { Text(stringResource(R.string.model_name)) },
                        placeholder = { Text(stringResource(R.string.model_name_placeholder)) },
                        modifier = Modifier.weight(1f),
                        enabled = !isUsingDefaultApiKey,
                        colors =
                                OutlinedTextFieldDefaults.colors(
                                        disabledTextColor =
                                                if (isUsingDefaultApiKey)
                                                        MaterialTheme.colorScheme.primary
                                                else
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                                alpha = 0.38f
                                                        ),
                                        disabledBorderColor =
                                                if (isUsingDefaultApiKey)
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.3f
                                                        )
                                                else
                                                        MaterialTheme.colorScheme.outline.copy(
                                                                alpha = 0.12f
                                                        ),
                                        disabledLabelColor =
                                                if (isUsingDefaultApiKey)
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.7f
                                                        )
                                                else
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                                .copy(alpha = 0.38f)
                                ),
                        singleLine = true
                )

                Spacer(modifier = Modifier.width(8.dp))

                // 获取模型列表按钮
                IconButton(
                        onClick = {
                            Log.d(
                                    TAG,
                                    "模型列表按钮被点击 - API端点: $apiEndpointInput, API类型: ${selectedApiProvider.name}"
                            )
                            val gettingModelsText = context.getString(R.string.getting_models_list)
                            val unknownErrorText = context.getString(R.string.unknown_error)
                            val getModelsFailedText = context.getString(R.string.get_models_list_failed)
                            val defaultConfigNoModelsText = context.getString(R.string.default_config_no_models_list)
                            val fillEndpointKeyText = context.getString(R.string.fill_endpoint_and_key)
                            val modelsListSuccessText = context.getString(R.string.models_list_success)
                            val refreshModelsFailedText = context.getString(R.string.refresh_models_failed)
                            
                            showNotification(gettingModelsText)

                            scope.launch {
                                if (apiEndpointInput.isNotBlank() &&
                                                apiKeyInput.isNotBlank() &&
                                                !isUsingDefaultApiKey
                                ) {
                                    isLoadingModels = true
                                    modelLoadError = null
                                    Log.d(
                                            TAG,
                                            "开始获取模型列表: 端点=$apiEndpointInput, API类型=${selectedApiProvider.name}"
                                    )

                                    try {
                                        val result =
                                                ModelListFetcher.getModelsList(
                                                        apiKeyInput,
                                                        apiEndpointInput,
                                                        selectedApiProvider
                                                )
                                        if (result.isSuccess) {
                                            val models = result.getOrThrow()
                                            Log.d(TAG, "模型列表获取成功，共 ${models.size} 个模型")
                                            modelsList = models
                                            showModelsDialog = true
                                            showNotification(modelsListSuccessText.format(models.size))
                                        } else {
                                            val errorMsg =
                                                    result.exceptionOrNull()?.message ?: unknownErrorText
                                            Log.e(TAG, "模型列表获取失败: $errorMsg")
                                            modelLoadError = getModelsFailedText.format(errorMsg)
                                            showNotification(modelLoadError ?: getModelsFailedText.format(""))
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "获取模型列表发生异常", e)
                                        modelLoadError = getModelsFailedText.format(e.message ?: "")
                                        showNotification(modelLoadError ?: getModelsFailedText.format(""))
                                    } finally {
                                        isLoadingModels = false
                                        Log.d(TAG, "模型列表获取流程完成")
                                    }
                                } else if (isUsingDefaultApiKey) {
                                    Log.d(TAG, "使用默认配置，不获取模型列表")
                                    showNotification(defaultConfigNoModelsText)
                                } else {
                                    Log.d(TAG, "API端点或密钥为空")
                                    showNotification(fillEndpointKeyText)
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        colors =
                                IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                ),
                        enabled = !isUsingDefaultApiKey
                ) {
                    if (isLoadingModels) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                                imageVector = Icons.Default.FormatListBulleted,
                                contentDescription = stringResource(R.string.get_models_list),
                                tint =
                                        if (!isUsingDefaultApiKey) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
            } else {
                // MNN提供商：显示获取本地模型列表按钮
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    OutlinedTextField(
                            value = modelNameInput,
                            onValueChange = { /* MNN模型名称从列表选择，不允许手动编辑 */ },
                            label = { Text(stringResource(R.string.model_name)) },
                            placeholder = { Text(stringResource(R.string.mnn_select_downloaded_model)) },
                            modifier = Modifier.weight(1f),
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            singleLine = true
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // 获取MNN本地模型列表按钮
                    IconButton(
                            onClick = {
                                Log.d(TAG, "获取MNN本地模型列表")
                                val gettingModelsText = context.getString(R.string.getting_models_list)
                                val modelsListSuccessText = context.getString(R.string.models_list_success)
                                showNotification(gettingModelsText)

                                scope.launch {
                                    isLoadingModels = true
                                    modelLoadError = null

                                    try {
                                        val result = ModelListFetcher.getMnnLocalModels(context)
                                        if (result.isSuccess) {
                                            val models = result.getOrThrow()
                                            Log.d(TAG, "MNN模型列表获取成功，共 ${models.size} 个模型")
                                            modelsList = models
                                            showModelsDialog = true
                                            showNotification(modelsListSuccessText.format(models.size))
                                        } else {
                                            val errorMsg = result.exceptionOrNull()?.message ?: context.getString(R.string.unknown_error)
                                            Log.e(TAG, "MNN模型列表获取失败: $errorMsg")
                                            modelLoadError = context.getString(R.string.get_models_list_failed, errorMsg)
                                            showNotification(modelLoadError!!)
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "获取MNN模型列表发生异常", e)
                                        modelLoadError = context.getString(R.string.get_models_list_failed, e.message ?: "")
                                        showNotification(modelLoadError!!)
                                    } finally {
                                        isLoadingModels = false
                                    }
                                }
                            },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                            )
                    ) {
                        if (isLoadingModels) {
                            CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                    imageVector = Icons.Default.FormatListBulleted,
                                    contentDescription = stringResource(R.string.get_models_list),
                                    tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 添加API提供商选择
            var showApiProviderDialog by remember { mutableStateOf(false) }

            // API提供商选择
            Text(
                    stringResource(R.string.api_provider),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
            )

            // 美化后的选择器按钮
            Surface(
                    modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showApiProviderDialog = true }
                            .padding(bottom = 16.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
                Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                                text = getProviderDisplayName(selectedApiProvider, context),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                                text = stringResource(R.string.select_api_provider),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.select),
                            tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (showApiProviderDialog) {
                ApiProviderDialog(
                        onDismissRequest = { showApiProviderDialog = false },
                        onProviderSelected = { provider ->
                            selectedApiProvider = provider
                            if (modelNameInput.isEmpty() || isDefaultModelName(modelNameInput)) {
                                modelNameInput = getDefaultModelName(provider)
                            }
                            showApiProviderDialog = false
                        }
                )
            }

            // 图片直接处理开关（仅在非MNN提供商时显示）
            if (selectedApiProvider != ApiProviderType.MNN) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                                text = stringResource(R.string.enable_direct_image_processing),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                        )
                        Text(
                                text = stringResource(R.string.enable_direct_image_processing_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                        )
                    }
                    Switch(
                            checked = enableDirectImageProcessingInput,
                            onCheckedChange = { enableDirectImageProcessingInput = it }
                    )
                }
            }

            // 显示模型限制信息
            if (showModelRestrictionInfo) {
                Text(
                        text = stringResource(R.string.default_config_model_restriction),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp),
                        fontSize = 12.sp
                )
            }
            // 保存按钮
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                        onClick = {
                            scope.launch {
                                // 强制在使用默认API密钥时使用默认模型
                                val modelToSave =
                                        if (apiKeyInput == ApiPreferences.DEFAULT_API_KEY) {
                                            ApiPreferences.DEFAULT_MODEL_NAME
                                        } else {
                                            modelNameInput
                                        }

                                Log.d(
                                        TAG,
                                        "保存API设置: apiKey=${apiKeyInput.take(5)}..., endpoint=$apiEndpointInput, model=$modelToSave, providerType=${selectedApiProvider.name}"
                                )

                                // 更新配置
                                configManager.updateModelConfig(
                                        configId = config.id,
                                        apiKey = apiKeyInput,
                                        apiEndpoint = apiEndpointInput,
                                        modelName = modelToSave,
                                        apiProviderType = selectedApiProvider,
                                        mnnForwardType = mnnForwardTypeInput,
                                        mnnThreadCount = mnnThreadCountInput.toIntOrNull() ?: 4
                                )

                                // 更新图片直接处理配置
                                configManager.updateDirectImageProcessing(
                                        configId = config.id,
                                        enableDirectImageProcessing = enableDirectImageProcessingInput
                                )

                                // 刷新所有AI服务实例，确保使用最新配置
                                EnhancedAIService.refreshAllServices(
                                        configManager.appContext
                                )

                                Log.d(TAG, "API设置保存完成并刷新服务")
                                showNotification(context.getString(R.string.api_settings_saved))
                            }
                        }
                ) { Text(stringResource(R.string.save_api_settings)) }
            }
        }
    }

    // 模型列表对话框
    if (showModelsDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredModelsList =
                remember(searchQuery, modelsList) {
                    if (searchQuery.isEmpty()) modelsList
                    else modelsList.filter { it.id.contains(searchQuery, ignoreCase = true) }
                }

        Dialog(onDismissRequest = { showModelsDialog = false }) {
            Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 标题栏
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                                stringResource(R.string.available_models_list),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                        )

                        FilledIconButton(
                                onClick = {
                                    scope.launch {
                                        if (apiEndpointInput.isNotBlank() &&
                                                        apiKeyInput.isNotBlank() &&
                                                        !isUsingDefaultApiKey
                                        ) {
                                            isLoadingModels = true
                                            try {
                                                val result =
                                                        ModelListFetcher.getModelsList(
                                                                apiKeyInput,
                                                                apiEndpointInput,
                                                                selectedApiProvider
                                                        )
                                                if (result.isSuccess) {
                                                    modelsList = result.getOrThrow()
                                                } else {
                                                    val errorMsg = result.exceptionOrNull()?.message ?: context.getString(R.string.unknown_error)
                                                    modelLoadError = context.getString(R.string.refresh_models_list_failed, errorMsg)
                                                    showNotification(modelLoadError ?: context.getString(R.string.refresh_models_failed))
                                                }
                                            } catch (e: Exception) {
                                                val errorMsg = e.message ?: context.getString(R.string.unknown_error)
                                                modelLoadError = context.getString(R.string.refresh_models_list_failed, errorMsg)
                                                showNotification(modelLoadError ?: context.getString(R.string.refresh_models_failed))
                                            } finally {
                                                isLoadingModels = false
                                            }
                                        }
                                    }
                                },
                                colors =
                                        IconButtonDefaults.filledIconButtonColors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                contentColor =
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                modifier = Modifier.size(36.dp)
                        ) {
                            if (isLoadingModels) {
                                CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.refresh_models_list),
                                        modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // 搜索框 - 用普通的OutlinedTextField替代实验性的SearchBar
                    OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(stringResource(R.string.search_models), fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(
                                        Icons.Default.Search,
                                        contentDescription = stringResource(R.string.search),
                                        modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                                Icons.Default.Clear,
                                                contentDescription = stringResource(R.string.clear),
                                                modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            modifier =
                                    Modifier.fillMaxWidth().padding(bottom = 12.dp).height(48.dp),
                            colors =
                                    OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor =
                                                    MaterialTheme.colorScheme.outline,
                                            focusedLeadingIconColor =
                                                    MaterialTheme.colorScheme.primary,
                                            unfocusedLeadingIconColor =
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                    )

                    // 模型列表
                    if (modelsList.isEmpty()) {
                        Box(
                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                contentAlignment = Alignment.Center
                        ) {
                            Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                        imageVector = Icons.Default.FormatListBulleted,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint =
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.6f
                                                )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                        text = modelLoadError ?: stringResource(R.string.no_models_found),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            items(filteredModelsList.size) { index ->
                                val model = filteredModelsList[index]
                                // 使用自定义Row替代ListItem以使布局更紧凑
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .clickable {
                                                            modelNameInput = model.id
                                                            // MNN提供商：model.id 就是文件夹名称，直接使用即可
                                                            if (selectedApiProvider == ApiProviderType.MNN) {
                                                                Log.d(TAG, "选择MNN模型: ${model.id}")
                                                            }
                                                            showModelsDialog = false
                                                        }
                                                        .padding(
                                                                horizontal = 12.dp,
                                                                vertical = 6.dp
                                                        ),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                            text = model.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                    )
                                }

                                if (index < filteredModelsList.size - 1) {
                                    Divider(
                                            thickness = 0.5.dp,
                                            color =
                                                    MaterialTheme.colorScheme.outlineVariant.copy(
                                                            alpha = 0.5f
                                                    ),
                                            modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 底部信息
                    if (filteredModelsList.isNotEmpty()) {
                        Text(
                                text =
                                        stringResource(R.string.models_displayed, filteredModelsList.size) +
                                                (if (searchQuery.isNotEmpty()) stringResource(R.string.models_displayed_filtered) else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                                fontSize = 12.sp
                        )
                    }

                    // 底部按钮
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.End
                    ) {
                        FilledTonalButton(
                                onClick = { showModelsDialog = false },
                                modifier = Modifier.height(36.dp)
                        ) { Text(stringResource(R.string.close), fontSize = 14.sp) }
                    }
                }
            }
        }
    }
}

private fun getProviderDisplayName(provider: ApiProviderType, context: android.content.Context): String {
    return when (provider) {
        ApiProviderType.OPENAI -> context.getString(R.string.provider_openai)
        ApiProviderType.ANTHROPIC -> context.getString(R.string.provider_anthropic)
        ApiProviderType.GOOGLE -> context.getString(R.string.provider_google)
        ApiProviderType.BAIDU -> context.getString(R.string.provider_baidu)
        ApiProviderType.ALIYUN -> context.getString(R.string.provider_aliyun)
        ApiProviderType.XUNFEI -> context.getString(R.string.provider_xunfei)
        ApiProviderType.ZHIPU -> context.getString(R.string.provider_zhipu)
        ApiProviderType.BAICHUAN -> context.getString(R.string.provider_baichuan)
        ApiProviderType.MOONSHOT -> context.getString(R.string.provider_moonshot)
        ApiProviderType.DEEPSEEK -> context.getString(R.string.provider_deepseek)
        ApiProviderType.SILICONFLOW -> context.getString(R.string.provider_siliconflow)
        ApiProviderType.OPENROUTER -> context.getString(R.string.provider_openrouter)
        ApiProviderType.INFINIAI -> context.getString(R.string.provider_infiniai)
        ApiProviderType.ALIPAY_BAILING -> context.getString(R.string.provider_alipay_bailing)
        ApiProviderType.LMSTUDIO -> context.getString(R.string.provider_lmstudio)
        ApiProviderType.MNN -> context.getString(R.string.provider_mnn)
        ApiProviderType.PPINFRA -> context.getString(R.string.provider_ppinfra)
        ApiProviderType.OTHER -> context.getString(R.string.provider_other)
    }
}

@Composable
private fun ApiProviderDialog(
        onDismissRequest: () -> Unit,
        onProviderSelected: (ApiProviderType) -> Unit
) {
    val providers = ApiProviderType.values()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredProviders = remember(searchQuery) {
        if (searchQuery.isEmpty()) {
            providers.toList()
        } else {
            providers.filter { provider ->
                getProviderDisplayName(provider, context).contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 标题和搜索框
                Text(
                        stringResource(R.string.select_api_provider_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // 搜索框
                OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_providers), fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search),
                                    modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                            Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.clear),
                                            modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(8.dp)
                )

                // 提供商列表
                androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.weight(1f)
                ) {
                    items(filteredProviders.size) { index ->
                        val provider = filteredProviders[index]
                        // 美化的提供商选项
                        Surface(
                                modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { onProviderSelected(provider) },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ) {
                            Row(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 提供商图标（使用圆形背景色）
                                Box(
                                        modifier = Modifier
                                                .size(32.dp)
                                                .background(
                                                        getProviderColor(provider),
                                                        CircleShape
                                                ),
                                        contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                            text = getProviderDisplayName(provider, context).first().toString(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Spacer(modifier = Modifier.width(16.dp))
                                
                                Text(
                                        text = getProviderDisplayName(provider, context),
                                        style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
                
                // 底部按钮
                Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

// 为不同提供商生成不同的颜色
@Composable
private fun getProviderColor(provider: ApiProviderType): androidx.compose.ui.graphics.Color {
    return when (provider) {
        ApiProviderType.OPENAI -> MaterialTheme.colorScheme.primary
        ApiProviderType.ANTHROPIC -> MaterialTheme.colorScheme.tertiary
        ApiProviderType.GOOGLE -> MaterialTheme.colorScheme.secondary
        ApiProviderType.BAIDU -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        ApiProviderType.ALIYUN -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f)
        ApiProviderType.XUNFEI -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
        ApiProviderType.ZHIPU -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        ApiProviderType.BAICHUAN -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
        ApiProviderType.MOONSHOT -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
        ApiProviderType.DEEPSEEK -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        ApiProviderType.SILICONFLOW -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
        ApiProviderType.OPENROUTER -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
        ApiProviderType.INFINIAI -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ApiProviderType.ALIPAY_BAILING -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
        ApiProviderType.LMSTUDIO -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
        ApiProviderType.MNN -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        ApiProviderType.PPINFRA -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
        ApiProviderType.OTHER -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    }
}
