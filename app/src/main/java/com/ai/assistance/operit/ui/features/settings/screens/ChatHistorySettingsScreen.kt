package com.ai.assistance.operit.ui.features.settings.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.data.model.ImportStrategy
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ChatHistoryOperation {
    IDLE,
    EXPORTING,
    EXPORTED,
    IMPORTING,
    IMPORTED,
    DELETING,
    DELETED,
    FAILED
}

enum class MemoryOperation {
    IDLE,
    EXPORTING,
    EXPORTED,
    IMPORTING,
    IMPORTED,
    FAILED
}

@Composable
fun ChatHistorySettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val chatHistoryManager = remember { ChatHistoryManager.getInstance(context) }
    val userPreferencesManager = remember { UserPreferencesManager(context) }
    val activeProfileId by userPreferencesManager.activeProfileIdFlow.collectAsState(initial = "default")
    var memoryRepo by remember { mutableStateOf<MemoryRepository?>(null) }
    
    // Initialize MemoryRepository with the current profile ID
    LaunchedEffect(activeProfileId) {
        memoryRepo = MemoryRepository(context, activeProfileId)
    }
    
    // Profile list and selection states
    val profileIds by userPreferencesManager.profileListFlow.collectAsState(initial = listOf("default"))
    var allProfiles by remember { mutableStateOf<List<PreferenceProfile>>(emptyList()) }
    
    // Load all profile details
    LaunchedEffect(profileIds) {
        val profiles = profileIds.mapNotNull { profileId ->
            try {
                userPreferencesManager.getUserPreferencesFlow(profileId).first()
            } catch (e: Exception) {
                null
            }
        }
        allProfiles = profiles
    }
    
    // Export and import profile selection
    var selectedExportProfileId by remember { mutableStateOf(activeProfileId) }
    var selectedImportProfileId by remember { mutableStateOf(activeProfileId) }
    var showExportProfileDialog by remember { mutableStateOf(false) }
    var showImportProfileDialog by remember { mutableStateOf(false) }
    
    // Update selected profiles when active profile changes
    LaunchedEffect(activeProfileId) {
        selectedExportProfileId = activeProfileId
        selectedImportProfileId = activeProfileId
    }

    var totalChatCount by remember { mutableStateOf(0) }
    var totalMemoryCount by remember { mutableStateOf(0) }
    var totalMemoryLinkCount by remember { mutableStateOf(0) }
    var operationState by remember { mutableStateOf(ChatHistoryOperation.IDLE) }
    var operationMessage by remember { mutableStateOf("") }
    var memoryOperationState by remember { mutableStateOf(MemoryOperation.IDLE) }
    var memoryOperationMessage by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showMemoryImportStrategyDialog by remember { mutableStateOf(false) }
    var pendingMemoryImportUri by remember { mutableStateOf<Uri?>(null) }

    val chatFilePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    scope.launch {
                        operationState = ChatHistoryOperation.IMPORTING
                        try {
                            val importResult = importChatHistoriesFromUri(context, uri)
                            operationMessage = if (importResult.total > 0) {
                                operationState = ChatHistoryOperation.IMPORTED
                                "导入成功：\n" +
                                        "- 新增记录：${importResult.new}条\n" +
                                        "- 更新记录：${importResult.updated}条\n" +
                                        (if (importResult.skipped > 0) "- 跳过无效记录：${importResult.skipped}条" else "")
                            } else {
                                operationState = ChatHistoryOperation.FAILED
                                "导入失败：未找到有效的聊天记录，请确保选择了正确的备份文件"
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            operationState = ChatHistoryOperation.FAILED
                            operationMessage =
                                "导入失败：${e.localizedMessage ?: e.toString()}\n" +
                                        "请确保选择了有效的Operit聊天记录备份文件"
                        }
                    }
                }
            }
        }
    
    val memoryFilePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    pendingMemoryImportUri = uri
                    // First show profile selection dialog
                    showImportProfileDialog = true
                }
            }
        }

    LaunchedEffect(Unit) {
        chatHistoryManager.chatHistoriesFlow.collect { chatHistories ->
            totalChatCount = chatHistories.size
        }
    }
    
    LaunchedEffect(memoryRepo) {
        memoryRepo?.let { repo ->
            val memories = repo.searchMemories("")
            totalMemoryCount = memories.count { !it.isDocumentNode }
            val graph = repo.getMemoryGraph()
            totalMemoryLinkCount = graph.edges.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        DataManagementCard(
            totalChatCount = totalChatCount,
            operationState = operationState,
            operationMessage = operationMessage,
            onExport = {
                scope.launch {
                    operationState = ChatHistoryOperation.EXPORTING
                    try {
                        val filePath = exportChatHistories(context)
                        if (filePath != null) {
                            operationState = ChatHistoryOperation.EXPORTED
                            val chatCount = chatHistoryManager.chatHistoriesFlow.first().size
                            operationMessage = "成功导出 $chatCount 条聊天记录到：\n$filePath"
                        } else {
                            operationState = ChatHistoryOperation.FAILED
                            operationMessage = "导出失败：无法创建文件"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        operationState = ChatHistoryOperation.FAILED
                        operationMessage = "导出失败：${e.localizedMessage ?: e.toString()}"
                    }
                }
            },
            onImport = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                chatFilePickerLauncher.launch(intent)
            },
            onDelete = { showDeleteConfirmDialog = true }
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        MemoryManagementCard(
            totalMemoryCount = totalMemoryCount,
            totalLinkCount = totalMemoryLinkCount,
            operationState = memoryOperationState,
            operationMessage = memoryOperationMessage,
            onExport = {
                // Show profile selection dialog
                showExportProfileDialog = true
            },
            onImport = {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                }
                memoryFilePickerLauncher.launch(intent)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        FaqCard()
    }

    if (showDeleteConfirmDialog) {
        DeleteConfirmationDialog(
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirm = {
                showDeleteConfirmDialog = false
                scope.launch {
                    operationState = ChatHistoryOperation.DELETING
                    try {
                        val deletedCount = deleteAllChatHistories(context)
                        operationState = ChatHistoryOperation.DELETED
                        operationMessage = "成功清除 $deletedCount 条聊天记录"
                    } catch (e: Exception) {
                        operationState = ChatHistoryOperation.FAILED
                        operationMessage = "清除失败：${e.localizedMessage ?: e.toString()}"
                    }
                }
            }
        )
    }
    
    if (showMemoryImportStrategyDialog) {
        MemoryImportStrategyDialog(
            onDismiss = { 
                showMemoryImportStrategyDialog = false
                pendingMemoryImportUri = null
            },
            onConfirm = { strategy ->
                showMemoryImportStrategyDialog = false
                val uri = pendingMemoryImportUri
                pendingMemoryImportUri = null
                
                if (uri != null) {
                    scope.launch {
                        memoryOperationState = MemoryOperation.IMPORTING
                        try {
                            // Create MemoryRepository for the selected profile
                            val importRepo = MemoryRepository(context, selectedImportProfileId)
                            val result = importMemoriesFromUri(context, importRepo, uri, strategy)
                            memoryOperationState = MemoryOperation.IMPORTED
                            val profileName = allProfiles.find { it.id == selectedImportProfileId }?.name ?: selectedImportProfileId
                            memoryOperationMessage = "导入到配置「$profileName」成功：\n" +
                                    "- 新增记忆：${result.newMemories}条\n" +
                                    "- 更新记忆：${result.updatedMemories}条\n" +
                                    "- 跳过记忆：${result.skippedMemories}条\n" +
                                    "- 新增链接：${result.newLinks}个"
                            
                            // 更新统计信息（如果导入的是当前激活的 profile）
                            if (selectedImportProfileId == activeProfileId) {
                                val repo = memoryRepo
                                if (repo != null) {
                                    val memories = repo.searchMemories("")
                                    totalMemoryCount = memories.count { !it.isDocumentNode }
                                    val graph = repo.getMemoryGraph()
                                    totalMemoryLinkCount = graph.edges.size
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            memoryOperationState = MemoryOperation.FAILED
                            memoryOperationMessage = "导入失败：${e.localizedMessage ?: e.toString()}"
                        }
                    }
                }
            }
        )
    }
    
    // Export profile selection dialog
    if (showExportProfileDialog) {
        ProfileSelectionDialog(
            title = "选择要导出的配置",
            profiles = allProfiles,
            selectedProfileId = selectedExportProfileId,
            onProfileSelected = { selectedExportProfileId = it },
            onDismiss = { showExportProfileDialog = false },
            onConfirm = {
                showExportProfileDialog = false
                scope.launch {
                    memoryOperationState = MemoryOperation.EXPORTING
                    try {
                        // Create MemoryRepository for the selected profile
                        val exportRepo = MemoryRepository(context, selectedExportProfileId)
                        val filePath = exportMemories(context, exportRepo)
                        if (filePath != null) {
                            memoryOperationState = MemoryOperation.EXPORTED
                            val profileName = allProfiles.find { it.id == selectedExportProfileId }?.name ?: selectedExportProfileId
                            val memories = exportRepo.searchMemories("")
                            val memoryCount = memories.count { !it.isDocumentNode }
                            val graph = exportRepo.getMemoryGraph()
                            val linkCount = graph.edges.size
                            memoryOperationMessage = "成功从配置「$profileName」导出 $memoryCount 条记忆和 $linkCount 个链接到：\n$filePath"
                        } else {
                            memoryOperationState = MemoryOperation.FAILED
                            memoryOperationMessage = "导出失败：无法创建文件"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        memoryOperationState = MemoryOperation.FAILED
                        memoryOperationMessage = "导出失败：${e.localizedMessage ?: e.toString()}"
                    }
                }
            }
        )
    }
    
    // Import profile selection dialog
    if (showImportProfileDialog) {
        ProfileSelectionDialog(
            title = "选择要导入到的配置",
            profiles = allProfiles,
            selectedProfileId = selectedImportProfileId,
            onProfileSelected = { selectedImportProfileId = it },
            onDismiss = { 
                showImportProfileDialog = false
                pendingMemoryImportUri = null
            },
            onConfirm = {
                showImportProfileDialog = false
                // Now show the strategy dialog
                showMemoryImportStrategyDialog = true
            }
        )
    }
}

@Composable
private fun DataManagementCard(
    totalChatCount: Int,
    operationState: ChatHistoryOperation,
    operationMessage: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "数据管理",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "当前共有 $totalChatCount 条聊天记录",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = "您可以备份聊天记录，或从备份文件中恢复。导出的文件将保存在「下载/Operit」文件夹中。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ManagementButton(
                        text = "导出",
                        icon = Icons.Default.CloudDownload,
                        onClick = onExport,
                        modifier = Modifier.weight(1f)
                    )
                    ManagementButton(
                        text = "导入",
                        icon = Icons.Default.CloudUpload,
                        onClick = onImport,
                        modifier = Modifier.weight(1f)
                    )
                }
                ManagementButton(
                    text = "清除所有记录",
                    icon = Icons.Default.Delete,
                    onClick = onDelete,
                    isDestructive = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(visible = operationState != ChatHistoryOperation.IDLE) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    when (operationState) {
                        ChatHistoryOperation.EXPORTING -> OperationProgressView(message = "正在导出聊天记录...")
                        ChatHistoryOperation.IMPORTING -> OperationProgressView(message = "正在导入聊天记录...")
                        ChatHistoryOperation.DELETING -> OperationProgressView(message = "正在删除聊天记录...")
                        ChatHistoryOperation.EXPORTED -> OperationResultCard(
                            title = "导出成功",
                            message = operationMessage,
                            icon = Icons.Default.CloudDownload
                        )
                        ChatHistoryOperation.IMPORTED -> OperationResultCard(
                            title = "导入成功",
                            message = operationMessage,
                            icon = Icons.Default.CloudUpload
                        )
                        ChatHistoryOperation.DELETED -> OperationResultCard(
                            title = "删除成功",
                            message = operationMessage,
                            icon = Icons.Default.Delete
                        )
                        ChatHistoryOperation.FAILED -> OperationResultCard(
                            title = "操作失败",
                            message = operationMessage,
                            icon = Icons.Default.Info,
                            isError = true
                        )
                        else -> {} // IDLE case is handled by visibility
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagementButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    val contentColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.5f))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
        Text(text)
    }
}

@Composable
private fun FaqCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "常见问题",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Divider()
            FaqItem(
                question = "为什么要备份数据？",
                answer = "备份聊天记录可以防止应用卸载或数据丢失时，您的重要内容丢失。定期备份是个好习惯！"
            )
            FaqItem(
                question = "导出的文件保存在哪里？",
                answer = "导出的备份文件会保存在您手机的「下载/Operit」文件夹中，文件名包含导出的数据类型、日期和时间。"
            )
            FaqItem(
                question = "导入后会出现重复的数据吗？",
                answer = "系统会根据记录ID判断，相同ID的记录会被更新而不是重复导入。不同ID的记录会作为新记录添加。"
            )
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = answer,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认清除聊天记录") },
        text = { Text("您确定要清除所有聊天记录吗？此操作无法撤销，建议先备份数据。") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("确认清除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun OperationResultCard(
    title: String,
    message: String,
    icon: ImageVector,
    isError: Boolean = false
) {
    val containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.padding(end = 16.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun OperationProgressView(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

// 导出聊天记录
private suspend fun exportChatHistories(context: Context): String? =
    withContext(Dispatchers.IO) {
        try {
            val chatHistoryManager = ChatHistoryManager.getInstance(context)
            val chatHistoriesBasic = chatHistoryManager.chatHistoriesFlow.first()

            val completeHistories = mutableListOf<ChatHistory>()
            for (chatHistory in chatHistoriesBasic) {
                val messages = chatHistoryManager.loadChatMessages(chatHistory.id)
                val completeHistory = chatHistory.copy(messages = messages)
                completeHistories.add(completeHistory)
            }

            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val exportDir = File(downloadDir, "Operit")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val exportFile = File(exportDir, "chat_backup_$timestamp.json")

            val json = Json {
                prettyPrint = true
                encodeDefaults = true
            }

            val jsonString = json.encodeToString(completeHistories)
            exportFile.writeText(jsonString)

            return@withContext exportFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

// 导入结果数据类
data class ImportResult(
    val new: Int,
    val updated: Int,
    val skipped: Int
) {
    val total: Int
        get() = new + updated
}

// 从URI导入聊天记录
private suspend fun importChatHistoriesFromUri(context: Context, uri: Uri): ImportResult =
    withContext(Dispatchers.IO) {
        try {
            val chatHistoryManager = ChatHistoryManager.getInstance(context)

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult(0, 0, 0)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()

            if (jsonString.isBlank()) {
                throw Exception("导入的文件为空")
            }

            val json = Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            }

            val chatHistories =
                try {
                    json.decodeFromString<List<ChatHistory>>(jsonString)
                } catch (e: Exception) {
                    Log.e("ChatHistorySettings", "使用kotlinx.serialization解析失败", e)
                    try {
                        val gson = GsonBuilder()
                            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
                            .create()
                        val type = object : TypeToken<List<ChatHistory>>() {}.type
                        gson.fromJson<List<ChatHistory>>(jsonString, type)
                    } catch (e2: Exception) {
                        Log.e("ChatHistorySettings", "使用Gson解析也失败", e2)
                        throw Exception("无法解析备份文件：${e.message}\n备份文件可能已损坏或格式不兼容")
                    }
                }

            if (chatHistories.isEmpty()) {
                return@withContext ImportResult(0, 0, 0)
            }

            val existingIds = chatHistoryManager.chatHistoriesFlow.first().map { it.id }.toSet()

            var newCount = 0
            var updatedCount = 0
            var skippedCount = 0

            for (chatHistory in chatHistories) {
                if (chatHistory.messages.isEmpty()) {
                    skippedCount++
                    continue
                }

                if (existingIds.contains(chatHistory.id)) {
                    updatedCount++
                } else {
                    newCount++
                }

                chatHistoryManager.saveChatHistory(chatHistory)
            }

            return@withContext ImportResult(newCount, updatedCount, skippedCount)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

// 删除所有聊天记录
private suspend fun deleteAllChatHistories(context: Context): Int =
    withContext(Dispatchers.IO) {
        try {
            val chatHistoryManager = ChatHistoryManager.getInstance(context)
            val chatHistories = chatHistoryManager.chatHistoriesFlow.first()
            val count = chatHistories.size

            for (chatHistory in chatHistories) {
                chatHistoryManager.deleteChatHistory(chatHistory.id)
            }

            return@withContext count
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

@Composable
private fun MemoryManagementCard(
    totalMemoryCount: Int,
    totalLinkCount: Int,
    operationState: MemoryOperation,
    operationMessage: String,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "记忆库管理",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "当前共有 $totalMemoryCount 条记忆",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "$totalLinkCount 个链接关系",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = "您可以备份记忆库数据（不包括文档），或从备份文件中恢复。导出的文件将保存在「下载/Operit」文件夹中。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ManagementButton(
                    text = "导出",
                    icon = Icons.Default.CloudDownload,
                    onClick = onExport,
                    modifier = Modifier.weight(1f)
                )
                ManagementButton(
                    text = "导入",
                    icon = Icons.Default.CloudUpload,
                    onClick = onImport,
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(visible = operationState != MemoryOperation.IDLE) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    when (operationState) {
                        MemoryOperation.EXPORTING -> OperationProgressView(message = "正在导出记忆库...")
                        MemoryOperation.IMPORTING -> OperationProgressView(message = "正在导入记忆库...")
                        MemoryOperation.EXPORTED -> OperationResultCard(
                            title = "导出成功",
                            message = operationMessage,
                            icon = Icons.Default.CloudDownload
                        )
                        MemoryOperation.IMPORTED -> OperationResultCard(
                            title = "导入成功",
                            message = operationMessage,
                            icon = Icons.Default.CloudUpload
                        )
                        MemoryOperation.FAILED -> OperationResultCard(
                            title = "操作失败",
                            message = operationMessage,
                            icon = Icons.Default.Info,
                            isError = true
                        )
                        else -> {} // IDLE case is handled by visibility
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryImportStrategyDialog(
    onDismiss: () -> Unit,
    onConfirm: (ImportStrategy) -> Unit
) {
    var selectedStrategy by remember { mutableStateOf(ImportStrategy.SKIP) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择导入策略") },
        text = {
            Column {
                Text(
                    text = "遇到重复的记忆（UUID相同）时如何处理？",
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StrategyOption(
                        title = "跳过（推荐）",
                        description = "保留现有记忆，不导入重复数据",
                        selected = selectedStrategy == ImportStrategy.SKIP,
                        onClick = { selectedStrategy = ImportStrategy.SKIP }
                    )
                    
                    StrategyOption(
                        title = "更新",
                        description = "用导入的数据更新现有记忆",
                        selected = selectedStrategy == ImportStrategy.UPDATE,
                        onClick = { selectedStrategy = ImportStrategy.UPDATE }
                    )
                    
                    StrategyOption(
                        title = "创建新记录",
                        description = "即使UUID相同也创建新记忆（可能导致重复）",
                        selected = selectedStrategy == ImportStrategy.CREATE_NEW,
                        onClick = { selectedStrategy = ImportStrategy.CREATE_NEW }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedStrategy) }) {
                Text("开始导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun StrategyOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surface
        ),
        border = if (selected) 
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else 
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// 导出记忆库
private suspend fun exportMemories(context: Context, memoryRepository: MemoryRepository): String? =
    withContext(Dispatchers.IO) {
        try {
            val jsonString = memoryRepository.exportMemoriesToJson()

            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val exportDir = File(downloadDir, "Operit")
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val exportFile = File(exportDir, "memory_backup_$timestamp.json")

            exportFile.writeText(jsonString)

            return@withContext exportFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

// 导入记忆库
private suspend fun importMemoriesFromUri(
    context: Context,
    memoryRepository: MemoryRepository,
    uri: Uri,
    strategy: ImportStrategy
) = withContext(Dispatchers.IO) {
    val inputStream = context.contentResolver.openInputStream(uri)
        ?: throw Exception("无法打开文件")
    val jsonString = inputStream.bufferedReader().use { it.readText() }
    inputStream.close()

    if (jsonString.isBlank()) {
        throw Exception("导入的文件为空")
    }

    memoryRepository.importMemoriesFromJson(jsonString, strategy)
}

@Composable
private fun ProfileSelectionDialog(
    title: String,
    profiles: List<PreferenceProfile>,
    selectedProfileId: String,
    onProfileSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                profiles.forEach { profile ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { onProfileSelected(profile.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedProfileId == profile.id) 
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            else 
                                MaterialTheme.colorScheme.surface
                        ),
                        border = if (selectedProfileId == profile.id) 
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else 
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProfileId == profile.id,
                                onClick = { onProfileSelected(profile.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = profile.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selectedProfileId == profile.id) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
