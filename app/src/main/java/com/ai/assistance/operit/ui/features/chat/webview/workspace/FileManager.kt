package com.ai.assistance.operit.ui.features.chat.webview.workspace

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// 目录条目数据类
data class DirectoryEntry(
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: String,
        val permissions: String
)

// 打开的文件信息
@Serializable
data class OpenFileInfo(
        val path: String,
        val content: String,
        val name: String = File(path).name
)

/** 文件浏览器组件 - VSCode风格 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowser(
        initialPath: String,
        onBindWorkspace: ((String) -> Unit)? = null,
        onCancel: () -> Unit,
        isManageMode: Boolean = false,
        onFileOpen: ((OpenFileInfo) -> Unit)? = null
) {
    val context = LocalContext.current
    val toolHandler = remember { AIToolHandler.getInstance(context) }
    var currentPath by remember { mutableStateOf(initialPath) }
    var fileList by remember { mutableStateOf<List<DirectoryEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    // 用于控制长按上下文菜单的状态
    var contextMenuExpandedFor by remember { mutableStateOf<DirectoryEntry?>(null) }

    fun loadDirectory(path: String) {
        if (isLoading) return // 防止并发加载
        coroutineScope.launch {
            isLoading = true
            try {
                val tool = AITool("list_files", listOf(ToolParameter("path", path)))
                val result = toolHandler.executeTool(tool)
                if (result.success && result.result is DirectoryListingData) {
                    val entries = (result.result as DirectoryListingData).entries
                    fileList =
                            entries.map {
                                DirectoryEntry(
                                        name = it.name,
                                        isDirectory = it.isDirectory,
                                        size = it.size,
                                        lastModified = it.lastModified,
                                        permissions = it.permissions
                                )
                            }
                    currentPath = path // 仅在成功时更新路径
                } else {
                    // 加载失败，不改变任何状态，用户停留在当前页面
                }
            } catch (e: Exception) {
                // 发生异常，同样不改变状态
            } finally {
                isLoading = false
            }
        }
    }

    fun createNewFile(fileName: String, isDirectory: Boolean) {
        coroutineScope.launch {
            isLoading = true
            try {
                val filePath = File(currentPath, fileName).path
                val tool =
                        if (isDirectory) {
                            AITool("create_directory", listOf(ToolParameter("path", filePath)))
                        } else {
                            AITool(
                                    "write_file",
                                    listOf(
                                            ToolParameter("path", filePath),
                                            ToolParameter("content", "")
                                    )
                            )
                        }
                toolHandler.executeTool(tool)
                loadDirectory(currentPath) // 刷新目录
            } catch (e: Exception) {
                // 处理错误
            } finally {
                isLoading = false
            }
        }
    }

    fun deleteFile(filePath: String) {
        coroutineScope.launch {
            isLoading = true
            try {
                val tool = AITool("delete_file", listOf(ToolParameter("path", filePath)))
                toolHandler.executeTool(tool)
                loadDirectory(currentPath) // 刷新目录
            } catch (e: Exception) {
                // 处理错误
            } finally {
                isLoading = false
            }
        }
    }

    fun openFile(filePath: String) {
        coroutineScope.launch {
            isLoading = true
            try {
                val tool = AITool("read_file", listOf(ToolParameter("path", filePath)))
                val result = toolHandler.executeTool(tool)
                if (result.success && result.result is FileContentData) {
                    val content = (result.result as FileContentData).content
                    val openFileInfo = OpenFileInfo(path = filePath, content = content)
                    onFileOpen?.invoke(openFileInfo)
                }
            } catch (e: Exception) {
                // 处理错误
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadDirectory(currentPath) }

    if (showCreateFileDialog) {
        AlertDialog(
                onDismissRequest = { showCreateFileDialog = false },
                title = { Text("创建新文件") },
                text = {
                    Column {
                        TextField(
                                value = newFileName,
                                onValueChange = { newFileName = it },
                                label = { Text("文件名") },
                                singleLine = true,
                                colors =
                                        TextFieldDefaults.colors(
                                                focusedContainerColor =
                                                        MaterialTheme.colorScheme.surface,
                                                unfocusedContainerColor =
                                                        MaterialTheme.colorScheme.surface
                                        )
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                                onClick = {
                                    if (newFileName.isNotEmpty()) {
                                        createNewFile(newFileName, false)
                                        showCreateFileDialog = false
                                        newFileName = ""
                                    }
                                }
                        ) { Text("创建文件") }
                        TextButton(
                                onClick = {
                                    if (newFileName.isNotEmpty()) {
                                        createNewFile(newFileName, true)
                                        showCreateFileDialog = false
                                        newFileName = ""
                                    }
                                }
                        ) { Text("创建文件夹") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateFileDialog = false }) { Text("取消") }
                }
        )
    }

    Box(
            modifier =
                    Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface) // 设置不透明背景
                            .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null, // 移除点击时的涟漪效果
                                    enabled = true,
                                    onClick = {}
                            ) // 拦截点击事件，防止穿透
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 路径导航栏 - 移除背景使其更简洁
            Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = currentPath,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                )

                if (isManageMode) {
                    IconButton(
                            onClick = { showCreateFileDialog = true },
                            modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                                Icons.Default.Add,
                                contentDescription = "新建",
                                modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                            onClick = { loadDirectory(currentPath) },
                            modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                                Icons.Default.Refresh,
                                contentDescription = "刷新",
                                modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 文件列表
            if (isLoading) {
                Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            } else {
                LazyColumn(
                        modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 8.dp)
                ) {
                    // 使用更健壮的方式来判断是否应该显示返回上一级的选项
                    if (File(currentPath).parent != null) {
                        item {
                            FileListItem(
                                    name = "..",
                                    icon = Icons.Default.FolderOpen,
                                    isDirectory = true,
                                    onClick = {
                                        File(currentPath).parent?.let { parentPath ->
                                            loadDirectory(parentPath)
                                        }
                                    }
                            )
                        }
                    }

                    items(fileList.sortedWith(compareBy({ !it.isDirectory }, { it.name }))) { item
                        ->
                        Box { // 使用Box来定位上下文菜单
                            FileListItem(
                                    name = item.name,
                                    icon =
                                            if (item.isDirectory) Icons.Default.Folder
                                            else getFileIcon(item.name),
                                    isDirectory = item.isDirectory,
                                    onClick = {
                                        if (item.isDirectory) {
                                            val newPath = File(currentPath, item.name).path
                                            loadDirectory(newPath)
                                        } else {
                                            val filePath = File(currentPath, item.name).path
                                            openFile(filePath)
                                        }
                                    },
                                    onLongPress = {
                                        if (isManageMode && !item.name.startsWith(".")) {
                                            contextMenuExpandedFor = item
                                        }
                                    }
                            )

                            // 上下文菜单
                            DropdownMenu(
                                    expanded = contextMenuExpandedFor == item,
                                    onDismissRequest = { contextMenuExpandedFor = null }
                            ) {
                                DropdownMenuItem(
                                        text = { Text("删除") },
                                        onClick = {
                                            val filePath = File(currentPath, item.name).path
                                            deleteFile(filePath)
                                            contextMenuExpandedFor = null
                                        },
                                        leadingIcon = {
                                            Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "删除",
                                                    tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                )
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }

            // 底部操作栏
            if (onBindWorkspace != null) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onCancel) { Text(if (isManageMode) "返回" else "取消") }
                    Button(onClick = { onBindWorkspace(currentPath) }) {
                        Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("绑定当前文件夹")
                    }
                }
            }
        }
    }
}

/** 抽取出的文件列表项，实现紧凑布局和长按手势 */
@Composable
private fun FileListItem(
        name: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        isDirectory: Boolean,
        onClick: () -> Unit,
        onLongPress: (() -> Unit)? = null
) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            // 统一处理单击和长按手势，并使用 onClick 和 onLongPress 作为 key
                            // 确保当 item 重用时，手势处理器能获取到最新的回调函数
                            .pointerInput(onClick, onLongPress) {
                                detectTapGestures(
                                        onTap = { onClick() },
                                        onLongPress = { onLongPress?.invoke() }
                                )
                            }
                            .padding(vertical = 8.dp, horizontal = 12.dp), // 减少垂直边距
            verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp), // 缩小图标
                tint =
                        if (isDirectory) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.width(12.dp)) // 减少间距
        Text(
                name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium // 使用更小的字号
        )
    }
}

private fun formatSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "%.1f KB".format(size / 1024.0)
        else -> "%.1f MB".format(size / (1024.0 * 1024.0))
    }
}

@SuppressLint("SimpleDateFormat")
private fun formatLastModified(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(dateString)
        val outputFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        outputFormat.timeZone = TimeZone.getDefault()
        date?.let { outputFormat.format(it) } ?: dateString
    } catch (e: Exception) {
        dateString
    }
}

/** 根据文件名获取对应的图标 */
@Composable
fun getFileIcon(fileName: String) =
        when {
            fileName.endsWith(".html", true) || fileName.endsWith(".htm", true) ->
                    Icons.Default.Code
            fileName.endsWith(".css", true) -> Icons.Default.Brush
            fileName.endsWith(".js", true) -> Icons.Default.Code // 使用通用的代码图标替代Javascript
            fileName.endsWith(".json", true) -> Icons.Default.DataObject
            fileName.endsWith(".jpg", true) ||
                    fileName.endsWith(".png", true) ||
                    fileName.endsWith(".gif", true) ||
                    fileName.endsWith(".jpeg", true) -> Icons.Default.Image
            fileName.endsWith(".txt", true) -> Icons.Default.TextSnippet
            fileName.endsWith(".md", true) -> Icons.Default.Article
            else -> Icons.Default.InsertDriveFile
        }
