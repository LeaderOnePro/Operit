package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MicExternalOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.data.preferences.SpeechServicesPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.foundation.layout.Arrangement
import com.ai.assistance.operit.api.voice.VoiceServiceFactory.VoiceServiceType
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.api.voice.SiliconFlowVoiceProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechServicesSettingsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { SpeechServicesPreferences(context) }
    
    var showSaveSuccessMessage by remember { mutableStateOf(false) }

    // --- State for TTS Settings ---
    val ttsServiceType by prefs.ttsServiceTypeFlow.collectAsState(initial = VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS)
    val httpConfig by prefs.ttsHttpConfigFlow.collectAsState(initial = SpeechServicesPreferences.DEFAULT_HTTP_TTS_PRESET)

    var ttsServiceTypeInput by remember(ttsServiceType) { mutableStateOf(ttsServiceType) }
    var ttsUrlTemplateInput by remember(httpConfig) { mutableStateOf(httpConfig.urlTemplate) }
    var ttsApiKeyInput by remember(httpConfig) { mutableStateOf(httpConfig.apiKey) }
    var ttsHeadersInput by remember(httpConfig) { mutableStateOf(Json.encodeToString(httpConfig.headers)) }
    var ttsHttpMethodInput by remember(httpConfig) { mutableStateOf(httpConfig.httpMethod) }
    var ttsRequestBodyInput by remember(httpConfig) { mutableStateOf(httpConfig.requestBody) }
    var ttsContentTypeInput by remember(httpConfig) { mutableStateOf(httpConfig.contentType) }
    var ttsVoiceIdInput by remember(httpConfig) { mutableStateOf(httpConfig.voiceId) }
    var ttsJsonError by remember { mutableStateOf<String?>(null) }
    var httpMethodDropdownExpanded by remember { mutableStateOf(false) }

    // --- State for STT Settings ---
    val sttServiceType by prefs.sttServiceTypeFlow.collectAsState(initial = SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN)
    var sttServiceTypeInput by remember(sttServiceType) { mutableStateOf(sttServiceType) }

    // 验证配置是否有效
    fun isConfigurationValid(): Boolean {
        return when (ttsServiceTypeInput) {
            VoiceServiceFactory.VoiceServiceType.HTTP_TTS -> {
                val basicValid = ttsJsonError == null && ttsUrlTemplateInput.isNotBlank()
                if (ttsHttpMethodInput == "POST") {
                    basicValid && ttsRequestBodyInput.isNotBlank() && ttsContentTypeInput.isNotBlank()
                } else {
                    basicValid
                }
            }
            VoiceServiceFactory.VoiceServiceType.SILICONFLOW_TTS -> {
                ttsApiKeyInput.isNotBlank() && ttsVoiceIdInput.isNotBlank()
            }
            VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS -> true
        }
    }

    // 保存设置的函数
    fun saveSettings() {
        val headersMap = try {
            Json.decodeFromString<Map<String, String>>(ttsHeadersInput)
        } catch (e: Exception) {
            ttsJsonError = "无效的 JSON 格式"
            return
        }
        
        ttsJsonError = null
        
        scope.launch {
            when (ttsServiceTypeInput) {
                VoiceServiceFactory.VoiceServiceType.HTTP_TTS -> {
                    prefs.saveTtsSettings(
                        serviceType = ttsServiceTypeInput,
                        httpConfig = SpeechServicesPreferences.TtsHttpConfig(
                            urlTemplate = ttsUrlTemplateInput,
                            apiKey = ttsApiKeyInput,
                            headers = headersMap,
                            httpMethod = ttsHttpMethodInput,
                            requestBody = ttsRequestBodyInput,
                            contentType = ttsContentTypeInput
                        )
                    )
                }
                VoiceServiceFactory.VoiceServiceType.SILICONFLOW_TTS -> {
                    prefs.saveTtsSettings(
                        serviceType = ttsServiceTypeInput,
                        httpConfig = SpeechServicesPreferences.TtsHttpConfig(
                            urlTemplate = "",
                            apiKey = ttsApiKeyInput,
                            headers = emptyMap(),
                            httpMethod = "",
                            requestBody = "",
                            contentType = "",
                            voiceId = ttsVoiceIdInput
                        )
                    )
                }
                VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS -> {
                    prefs.saveTtsSettings(serviceType = ttsServiceTypeInput)
                }
            }
            
            prefs.saveSttSettings(
                serviceType = sttServiceTypeInput
            )
            
            VoiceServiceFactory.resetInstance()
            SpeechServiceFactory.resetInstance()
            
            showSaveSuccessMessage = true
            delay(3000)
            showSaveSuccessMessage = false
        }
    }

    CustomScaffold { paddingValues ->
        Box(modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // --- TTS Section ---
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                             Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "文本转语音 (TTS) 设置",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        Text(
                            text = "配置如何将文本转换为语音输出",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = "服务类型",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        var ttsDropdownExpanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = ttsDropdownExpanded,
                            onExpandedChange = { ttsDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = when(ttsServiceTypeInput) {
                                    VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS -> "系统 TTS (简单)"
                                    VoiceServiceFactory.VoiceServiceType.HTTP_TTS -> "HTTP API (远程)"
                                    VoiceServiceFactory.VoiceServiceType.SILICONFLOW_TTS -> "硅基流动 TTS"
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("TTS 引擎") },
                                trailingIcon = { 
                                    Icon(Icons.Default.ArrowDropDown, "展开下拉菜单")
                                },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = ttsDropdownExpanded,
                                onDismissRequest = { ttsDropdownExpanded = false }
                            ) {
                                VoiceServiceFactory.VoiceServiceType.values().forEach { type ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                text = when(type) {
                                                    VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS -> "系统 TTS (简单)"
                                                    VoiceServiceFactory.VoiceServiceType.HTTP_TTS -> "HTTP API (远程)"
                                                    VoiceServiceFactory.VoiceServiceType.SILICONFLOW_TTS -> "硅基流动 TTS"
                                                },
                                                fontWeight = if (ttsServiceTypeInput == type) FontWeight.Medium else FontWeight.Normal
                                            ) 
                                        },
                                        onClick = {
                                            ttsServiceTypeInput = type
                                            ttsDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.HTTP_TTS) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = "HTTP TTS 配置",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsUrlTemplateInput,
                                    onValueChange = { ttsUrlTemplateInput = it },
                                    label = { Text("URL 模板") },
                                    placeholder = { Text("例如: https://api.example.com/tts?text={text}") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsApiKeyInput,
                                    onValueChange = { ttsApiKeyInput = it },
                                    label = { Text("API 密钥") },
                                    placeholder = { Text("可选，某些服务需要") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsHeadersInput,
                                    onValueChange = { 
                                        ttsHeadersInput = it
                                        try {
                                            Json.decodeFromString<Map<String, String>>(it)
                                            ttsJsonError = null
                                        } catch (e: Exception) {
                                            if (it.isNotBlank() && it != "{}") {
                                                ttsJsonError = "无效的 JSON 格式"
                                            } else {
                                                ttsJsonError = null
                                            }
                                        }
                                    },
                                    label = { Text("HTTP 头部 (JSON)") },
                                    placeholder = { Text("{\"Content-Type\": \"application/json\"}") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    isError = ttsJsonError != null
                                )

                                if (ttsJsonError != null) {
                                    Text(
                                        text = ttsJsonError!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                                                 Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = ttsHttpMethodInput,
                                        onValueChange = { },
                                        label = { Text("HTTP 方法") },
                                        readOnly = true,
                                        modifier = Modifier.weight(1f),
                                        trailingIcon = {
                                            DropdownMenu(
                                                expanded = httpMethodDropdownExpanded,
                                                onDismissRequest = { httpMethodDropdownExpanded = false }
                                            ) {
                                                listOf("GET", "POST").forEach { method ->
                                                    DropdownMenuItem(
                                                        text = { Text(method) },
                                                        onClick = {
                                                            ttsHttpMethodInput = method
                                                            httpMethodDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                            IconButton(onClick = { httpMethodDropdownExpanded = true }) {
                                                Icon(Icons.Default.ArrowDropDown, "选择HTTP方法")
                                            }
                                        }
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    OutlinedTextField(
                                        value = ttsContentTypeInput,
                                        onValueChange = { ttsContentTypeInput = it },
                                        label = { Text("Content-Type") },
                                        placeholder = { Text("application/json") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }

                                if (ttsHttpMethodInput == "POST") {
                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = ttsRequestBodyInput,
                                        onValueChange = { ttsRequestBodyInput = it },
                                        label = { Text("请求体模板") },
                                        placeholder = { Text("{\"text\": \"{text}\"}") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 3
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = ttsServiceTypeInput == VoiceServiceFactory.VoiceServiceType.SILICONFLOW_TTS) {
                            Column(modifier = Modifier.padding(top = 16.dp)) {
                                Text(
                                    text = "硅基流动 TTS 配置",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = ttsApiKeyInput,
                                    onValueChange = { ttsApiKeyInput = it },
                                    label = { Text("API 密钥") },
                                    placeholder = { Text("请输入您的硅基流动API密钥") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // 音色选择
                                var voiceDropdownExpanded by remember { mutableStateOf(false) }
                                val availableVoices = remember { SiliconFlowVoiceProvider.AVAILABLE_VOICES }
                                val selectedVoiceName = remember(ttsVoiceIdInput) {
                                    availableVoices.find { it.id == ttsVoiceIdInput }?.name ?: "自定义音色"
                                }

                                Text(
                                    text = "音色设置",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // 预设音色选择下拉菜单
                                ExposedDropdownMenuBox(
                                    expanded = voiceDropdownExpanded,
                                    onExpandedChange = { voiceDropdownExpanded = it }
                                ) {
                                    OutlinedTextField(
                                        value = selectedVoiceName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("选择预设音色") },
                                        trailingIcon = {
                                            Icon(Icons.Default.ArrowDropDown, "选择音色")
                                        },
                                        modifier = Modifier.menuAnchor().fillMaxWidth()
                                    )
                                    ExposedDropdownMenu(
                                        expanded = voiceDropdownExpanded,
                                        onDismissRequest = { voiceDropdownExpanded = false }
                                    ) {
                                        availableVoices.forEach { voice ->
                                            DropdownMenuItem(
                                                text = { Text(voice.name) },
                                                onClick = {
                                                    ttsVoiceIdInput = voice.id
                                                    voiceDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // 自定义音色ID输入框
                                OutlinedTextField(
                                    value = ttsVoiceIdInput,
                                    onValueChange = { ttsVoiceIdInput = it },
                                    label = { Text("音色ID（可直接输入）") },
                                    placeholder = { Text("例如：FunAudioLLM/CosyVoice2-0.5B:charles 或用户自定义音色ID") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    supportingText = {
                                        Text(
                                            text = "支持系统预置音色和用户上传的自定义音色",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // --- STT Section ---
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MicExternalOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "语音转文本 (STT) 设置",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Text(
                            text = "配置如何将语音转换为文本",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Text(
                            text = "服务类型",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        var sttDropdownExpanded by remember { mutableStateOf(false) }

                        ExposedDropdownMenuBox(
                            expanded = sttDropdownExpanded,
                            onExpandedChange = { sttDropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = when(sttServiceTypeInput) {
                                    SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN -> "Sherpa NCNN (本地)"
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("STT 引擎") },
                                trailingIcon = { 
                                    Icon(Icons.Default.ArrowDropDown, "展开下拉菜单")
                                },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = sttDropdownExpanded,
                                onDismissRequest = { sttDropdownExpanded = false }
                            ) {
                                SpeechServiceFactory.SpeechServiceType.values().forEach { type ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                text = when(type) {
                                                    SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN -> "Sherpa NCNN (本地)"
                                                },
                                                fontWeight = if (sttServiceTypeInput == type) FontWeight.Medium else FontWeight.Normal
                                            ) 
                                        },
                                        onClick = {
                                            sttServiceTypeInput = type
                                            sttDropdownExpanded = false
                                        },
                                        // 未来支持HTTP STT时，可以移除这里的enabled逻辑
                                        enabled = type == SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "目前仅支持本地 Sherpa-NCNN 引擎。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 信息卡片
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "关于语音服务",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsInfoRow(
                                title = "文本转语音 (TTS)",
                                description = "TTS 服务将文本转换为语音。您可以使用内置的系统 TTS 引擎或连接到远程 HTTP API。"
                            )
                            
                            Divider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                            
                            SettingsInfoRow(
                                title = "语音转文本 (STT)",
                                description = "STT 服务将语音转换为文本。目前仅支持内置的 Sherpa NCNN 引擎，该引擎在本地设备上运行，不需要网络连接。"
                            )
                        }
                    }
                }
                
                // 显示保存成功的消息
                AnimatedVisibility(visible = showSaveSuccessMessage) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
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
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "设置已保存",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // 保存按钮
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { saveSettings() },
                    enabled = isConfigurationValid(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("保存设置")
                }
                
                // 底部空间
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SettingsInfoRow(title: String, description: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
    }
} 