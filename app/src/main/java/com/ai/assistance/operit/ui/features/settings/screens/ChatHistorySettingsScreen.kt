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

@Composable
fun ChatHistorySettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val chatHistoryManager = remember { ChatHistoryManager.getInstance(context) }

    var totalChatCount by remember { mutableStateOf(0) }
    var operationState by remember { mutableStateOf(ChatHistoryOperation.IDLE) }
    var operationMessage by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

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

    LaunchedEffect(Unit) {
        chatHistoryManager.chatHistoriesFlow.collect { chatHistories ->
            totalChatCount = chatHistories.size
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
