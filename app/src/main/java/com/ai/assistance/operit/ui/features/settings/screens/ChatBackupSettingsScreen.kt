package com.ai.assistance.operit.ui.features.settings.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ImportStrategy
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.repository.MemoryRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
fun ChatBackupSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val chatHistoryManager = remember { ChatHistoryManager.getInstance(context) }
    val userPreferencesManager = remember { UserPreferencesManager(context) }
    val activeProfileId by userPreferencesManager.activeProfileIdFlow.collectAsState(initial = "default")
    var memoryRepo by remember { mutableStateOf<MemoryRepository?>(null) }

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

    val profileIds by userPreferencesManager.profileListFlow.collectAsState(initial = listOf("default"))
    var allProfiles by remember { mutableStateOf<List<PreferenceProfile>>(emptyList()) }
    var selectedExportProfileId by remember { mutableStateOf(activeProfileId) }
    var selectedImportProfileId by remember { mutableStateOf(activeProfileId) }
    var showExportProfileDialog by remember { mutableStateOf(false) }
    var showImportProfileDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeProfileId) {
        memoryRepo = MemoryRepository(context, activeProfileId)
        selectedExportProfileId = activeProfileId
        selectedImportProfileId = activeProfileId
    }

    LaunchedEffect(profileIds) {
        val profiles = profileIds.mapNotNull { profileId ->
            try {
                userPreferencesManager.getUserPreferencesFlow(profileId).first()
            } catch (_: Exception) {
                null
            }
        }
        allProfiles = profiles
    }

    LaunchedEffect(Unit) {
        chatHistoryManager.chatHistoriesFlow.collect { chatHistories ->
            totalChatCount = chatHistories.size
        }
    }

    LaunchedEffect(memoryRepo) {
        memoryRepo?.let { repo ->
            val memories = repo.searchMemories("*")
            totalMemoryCount = memories.count { !it.isDocumentNode }
            val graph = repo.getMemoryGraph()
            totalMemoryLinkCount = graph.edges.size
        }
    }

    val chatFilePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    scope.launch {
                        operationState = ChatHistoryOperation.IMPORTING
                        try {
                            val importResult = chatHistoryManager.importChatHistoriesFromUri(uri)
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
                    showImportProfileDialog = true
                }
            }
        }

    val activeProfileName =
        allProfiles.find { it.id == activeProfileId }?.name ?: "默认配置"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OverviewCard(
                totalChatCount = totalChatCount,
                totalMemoryCount = totalMemoryCount,
                totalLinkCount = totalMemoryLinkCount,
                activeProfileName = activeProfileName
            )
        }
        item {
            DataManagementCard(
                totalChatCount = totalChatCount,
                operationState = operationState,
                operationMessage = operationMessage,
                onExport = {
                    scope.launch {
                        operationState = ChatHistoryOperation.EXPORTING
                        try {
                            val filePath = chatHistoryManager.exportChatHistoriesToDownloads()
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
        }
        item {
            MemoryManagementCard(
                totalMemoryCount = totalMemoryCount,
                totalLinkCount = totalMemoryLinkCount,
                operationState = memoryOperationState,
                operationMessage = memoryOperationMessage,
                onExport = { showExportProfileDialog = true },
                onImport = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                    }
                    memoryFilePickerLauncher.launch(intent)
                }
            )
        }
        item {
            FaqCard()
        }
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
                            val importRepo = MemoryRepository(context, selectedImportProfileId)
                            val result = importMemoriesFromUri(context, importRepo, uri, strategy)
                            memoryOperationState = MemoryOperation.IMPORTED
                            val profileName = allProfiles.find { it.id == selectedImportProfileId }?.name
                                ?: selectedImportProfileId
                            memoryOperationMessage = "导入到配置「$profileName」成功：\n" +
                                "- 新增记忆：${result.newMemories}条\n" +
                                "- 更新记忆：${result.updatedMemories}条\n" +
                                "- 跳过记忆：${result.skippedMemories}条\n" +
                                "- 新增链接：${result.newLinks}个"

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
                        val exportRepo = MemoryRepository(context, selectedExportProfileId)
                        val filePath = exportMemories(context, exportRepo)
                        if (filePath != null) {
                            memoryOperationState = MemoryOperation.EXPORTED
                            val profileName = allProfiles.find { it.id == selectedExportProfileId }?.name
                                ?: selectedExportProfileId
                            val memories = exportRepo.searchMemories("")
                            val memoryCount = memories.count { !it.isDocumentNode }
                            val graph = exportRepo.getMemoryGraph()
                            val linkCount = graph.edges.size
                            memoryOperationMessage =
                                "成功从配置「$profileName」导出 $memoryCount 条记忆和 $linkCount 个链接到：\n$filePath"
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
                showMemoryImportStrategyDialog = true
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewCard(
    totalChatCount: Int,
    totalMemoryCount: Int,
    totalLinkCount: Int,
    activeProfileName: String
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "数据概览",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "当前配置：$activeProfileName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatChip(
                    icon = Icons.Default.History,
                    title = "$totalChatCount",
                    subtitle = "聊天记录"
                )
                StatChip(
                    icon = Icons.Default.Psychology,
                    title = "$totalMemoryCount",
                    subtitle = "记忆条目"
                )
                StatChip(
                    icon = Icons.Default.Link,
                    title = "$totalLinkCount",
                    subtitle = "记忆关联"
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(10.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataManagementCard(
    totalChatCount: Int,
    operationState: ChatHistoryOperation,
    operationMessage: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = "聊天记录",
                subtitle = "备份、恢复或清空历史记录",
                icon = Icons.Default.History
            )

            Text(
                text = "当前共有 $totalChatCount 条聊天记录。导出的文件会保存在「下载/Operit」文件夹中。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ManagementButton(
                    text = "导出",
                    icon = Icons.Default.CloudDownload,
                    onClick = onExport,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ManagementButton(
                    text = "导入",
                    icon = Icons.Default.CloudUpload,
                    onClick = onImport,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ManagementButton(
                    text = "清除所有记录",
                    icon = Icons.Default.Delete,
                    onClick = onDelete,
                    isDestructive = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(visible = operationState != ChatHistoryOperation.IDLE) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        else -> {}
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
    val colors = if (isDestructive) {
        ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.error
        )
    } else {
        ButtonDefaults.filledTonalButtonColors()
    }

    FilledTonalButton(
        onClick = onClick,
        modifier = modifier,
        colors = colors,
        shape = RoundedCornerShape(14.dp)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryManagementCard(
    totalMemoryCount: Int,
    totalLinkCount: Int,
    operationState: MemoryOperation,
    operationMessage: String,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = "记忆库",
                subtitle = "跨配置备份与恢复，保持思维链一致",
                icon = Icons.Default.Psychology
            )

            Text(
                text = "当前共有 $totalMemoryCount 条记忆和 $totalLinkCount 个链接。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ManagementButton(
                    text = "导出",
                    icon = Icons.Default.CloudDownload,
                    onClick = onExport,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ManagementButton(
                    text = "导入",
                    icon = Icons.Default.CloudUpload,
                    onClick = onImport,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            AnimatedVisibility(visible = operationState != MemoryOperation.IDLE) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "常见问题",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "了解备份与导入时的注意事项，避免常见误区。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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


