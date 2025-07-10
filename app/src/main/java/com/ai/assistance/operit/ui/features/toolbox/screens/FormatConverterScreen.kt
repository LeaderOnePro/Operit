package com.ai.assistance.operit.ui.features.toolbox.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.FileFormatConversionsResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 根据文件扩展名获取分类
fun getCategoryFromExtension(extension: String): String? {
    return when (extension) {
        in listOf("txt", "pdf", "doc", "docx", "html") -> "document"
        in listOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "ico") -> "image"
        in listOf("mp3", "wav", "ogg", "m4a", "flac", "aac") -> "audio"
        in listOf("mp4", "avi", "mov", "webm", "mkv") -> "video"
        in listOf("zip", "tar", "7z", "rar") -> "archive"
        else -> null
    }
}

// 获取文件路径 - 修改为挂起函数在IO线程执行
suspend fun getFilePathFromUri(context: android.content.Context, uri: Uri): Pair<String?, String?> = withContext(Dispatchers.IO) {
    return@withContext try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val fileName = it.getString(columnIndex)
                val file = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                // 返回原始URI的显示路径和缓存文件路径
                Pair(fileName, file.absolutePath)
            } else {
                Pair(null, null)
            }
        }
                ?: Pair(null, null)
    } catch (e: Exception) {
        Log.e("FormatConverterScreen", "Error getting file path", e)
        Pair(null, null)
    }
}

// 获取目标文件路径
fun getTargetFilePath(
        context: android.content.Context,
        originalFileName: String,
        originalFilePath: String,
        targetFormat: String
): String {
    // 获取文件名（不含扩展名）
    val fileNameWithoutExt = originalFileName.substringBeforeLast('.', originalFileName)
    val newFileName = "$fileNameWithoutExt.$targetFormat"

    // 直接使用/sdcard/Document目录
    val documentPath = "/sdcard/Document/Operit"

    // 返回目标文件路径
    return "$documentPath/$newFileName"
}

// 根据文件扩展名获取MIME类型
fun getMimeType(extension: String): String {
    return when (extension.lowercase()) {
        // 文档
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "txt" -> "text/plain"
        "html" -> "text/html"

        // 图片
        "jpg",
        "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"

        // 音频
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "m4a" -> "audio/m4a"
        "flac" -> "audio/flac"
        "aac" -> "audio/aac"

        // 视频
        "mp4" -> "video/mp4"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"

        // 压缩文件
        "zip" -> "application/zip"
        "tar" -> "application/x-tar"
        "7z" -> "application/x-7z-compressed"
        "rar" -> "application/x-rar-compressed"

        // 默认
        else -> "application/octet-stream"
    }
}

// 替换文件扩展名
fun replaceFileExtension(filePath: String, newExtension: String): String {
    val lastDotIndex = filePath.lastIndexOf('.')
    return if (lastDotIndex != -1) {
        filePath.substring(0, lastDotIndex + 1) + newExtension
    } else {
        "$filePath.$newExtension"
    }
}

// 清理缓存文件 - 修改为挂起函数在IO线程执行
suspend fun cleanupCacheFile(context: android.content.Context, filePath: String?) = withContext(Dispatchers.IO) {
    if (filePath != null) {
        try {
            val file = File(filePath)
            if (file.exists() && file.absolutePath.startsWith(context.cacheDir.absolutePath)) {
                if (file.delete()) {
                    Log.d("FormatConverterScreen", "已删除缓存文件: $filePath")
                } else {
                    Log.w("FormatConverterScreen", "无法删除缓存文件: $filePath")
                }
            }
        } catch (e: Exception) {
            Log.e("FormatConverterScreen", "清理缓存文件时出错", e)
        }
    }
}

// 清理所有缓存文件 - 修改为挂起函数在IO线程执行
suspend fun cleanupAllCacheFiles(context: android.content.Context) = withContext(Dispatchers.IO) {
    try {
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.delete()
            }
        }
    } catch (e: Exception) {
        Log.e("FormatConverterScreen", "清理缓存目录时出错", e)
    }
}

/** 万能格式转换屏幕，用于各种文件格式之间的转换 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormatConverterScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val toolHandler = AIToolHandler.getInstance(context)

    // 状态变量
    var originalFileName by remember { mutableStateOf<String?>(null) }
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedTargetFormat by remember { mutableStateOf<String?>(null) }
    var supportedFormats by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var supportedCategories by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var convertedFile by remember { mutableStateOf<String?>(null) }

    // 组件销毁时清理缓存
    DisposableEffect(Unit) {
        onDispose {
            coroutineScope.launch {
                selectedFile?.let { cleanupCacheFile(context, it) }
                cleanupAllCacheFiles(context)
            }
        }
    }

    // 文件选择器
    val filePickerLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                result.data?.data?.let { uri ->
                    // 显示加载状态
                    isLoading = true
                    error = null
                    
                    coroutineScope.launch {
                        try {
                            // 获取文件路径 - 在IO线程中处理
                            val (origFileName, cachePath) = getFilePathFromUri(context, uri)
                            if (origFileName != null && cachePath != null) {
                                originalFileName = origFileName
                                selectedFile = cachePath
                                // 根据文件扩展名自动选择分类
                                val extension = origFileName.substringAfterLast('.', "").lowercase()
                                selectedCategory = getCategoryFromExtension(extension)
                            } else {
                                error = "无法获取文件路径"
                            }
                        } catch (e: Exception) {
                            error = "选择文件失败: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            }

    // 文件类型分类
    val categories =
            listOf(
                    "document" to "文档",
                    "image" to "图片",
                    "audio" to "音频",
                    "video" to "视频",
                    "archive" to "压缩包"
            )

    // 获取支持的格式转换
    LaunchedEffect(Unit) {
        try {
            val tool = AITool(name = "get_supported_conversions", parameters = emptyList())
            // 使用withContext确保在IO线程执行
            val result = withContext(Dispatchers.IO) {
                toolHandler.executeTool(tool)
            }
            if (result.success) {
                val formatData = result.result as? FileFormatConversionsResultData
                supportedFormats = formatData?.conversions ?: emptyMap()
                supportedCategories = formatData?.fileTypes ?: emptyMap()
            } else {
                error = result.error ?: "Unknown error"
            }
        } catch (e: Exception) {
            error = e.message
        }
    }

    // 执行文件转换
    fun convertFile() {
        if (selectedFile == null || selectedTargetFormat == null || originalFileName == null) {
            error = "请完成所有必要的选择"
            return
        }

        isLoading = true
        error = null
        convertedFile = null
        val currentCacheFile = selectedFile // 保存当前缓存文件的引用以便后续清理

        coroutineScope.launch {
            try {
                // 创建一个合适的输出文件路径
                val targetPath =
                        getTargetFilePath(
                                context,
                                originalFileName!!,
                                selectedFile!!,
                                selectedTargetFormat!!
                        )

                // 确保目标目录存在 - 在IO线程中执行
                val makeDirectoryTool =
                        AITool(
                                name = "make_directory",
                                parameters =
                                        listOf(ToolParameter("path", "/sdcard/Document/Operit"))
                        )
                withContext(Dispatchers.IO) {
                    toolHandler.executeTool(makeDirectoryTool)
                }

                // 执行文件转换 - 在IO线程中执行
                val convertFileTool =
                        AITool(
                                name = "convert_file",
                                parameters =
                                        listOf(
                                                ToolParameter("source_path", selectedFile!!),
                                                ToolParameter("target_path", targetPath),
                                                ToolParameter(
                                                        "conversion_type",
                                                        "${selectedCategory}_to_${selectedTargetFormat}"
                                                )
                                        )
                        )

                val result = withContext(Dispatchers.IO) {
                    toolHandler.executeTool(convertFileTool)
                }

                if (result.success) {
                    convertedFile = targetPath
                } else {
                    error = result.error ?: "转换失败，请重试"
                }
            } catch (e: Exception) {
                Log.e("FormatConverterScreen", "Error converting file", e)
                error = "Error: ${e.message}"
            } finally {
                isLoading = false
                // 无论成功还是失败，都清理缓存文件
                cleanupCacheFile(context, currentCacheFile)
            }
        }
    }

    // 打开文件
    fun openFile(filePath: String) {
        coroutineScope.launch {
            try {
                val openFileTool =
                        AITool(
                                name = "open_file",
                                parameters = listOf(ToolParameter("path", filePath))
                        )
                // 在IO线程中执行
                val result = withContext(Dispatchers.IO) {
                    toolHandler.executeTool(openFileTool)
                }

                if (!result.success) {
                    error = "无法打开文件: ${result.error}"
                }
            } catch (e: Exception) {
                Log.e("FormatConverterScreen", "Error opening file", e)
                error = "Error: ${e.message}"
            }
        }
    }

    // 选择文件
    fun selectFile() {
        val intent =
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*" // 允许选择所有类型的文件
                }
        filePickerLauncher.launch(intent)
    }

    // 格式转换界面
    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
    ) {
        // 文件选择卡片
        Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                        text = "选择文件",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                        onClick = { selectFile() },
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                                ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                        shape = RoundedCornerShape(8.dp),
                        enabled = !isLoading
                ) {
                    Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "选择文件",
                            modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("选择文件")
                }

                selectedFile?.let { file ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                            alpha = 0.5f
                                                    ),
                                                    RoundedCornerShape(4.dp)
                                            )
                                            .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // 显示原始文件名
                        Text(
                                text = originalFileName ?: file.substringAfterLast('/', "未知文件"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 格式选择卡片
        Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                        text = "选择转换格式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 类别选择
                FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 3,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    categories.forEach { (id, name) ->
                        CategoryItem(
                                category = name,
                                isSelected = selectedCategory == id,
                                onClick = {
                                    selectedCategory = id
                                    selectedTargetFormat = null
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 目标格式选择
                if (selectedCategory != null) {
                    Text(
                            text = "目标格式",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier =
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        val targetFormats = supportedCategories[selectedCategory] ?: emptyList()
                        targetFormats.forEach { format ->
                            FormatItem(
                                    format = format,
                                    isSelected = selectedTargetFormat == format,
                                    onClick = { selectedTargetFormat = format }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 转换按钮
        Button(
                onClick = { convertFile() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = selectedFile != null && selectedTargetFormat != null && !isLoading,
                colors =
                        ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                shape = RoundedCornerShape(8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                        imageVector = Icons.Default.Transform,
                        contentDescription = "转换",
                        modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("开始转换", style = MaterialTheme.typography.titleMedium)
            }
        }

        error?.let { errorMessage ->
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(4.dp)
            ) {
                Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        convertedFile?.let { file ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                            CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                            ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                            text = "转换成功",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                            modifier =
                                    Modifier.fillMaxWidth()
                                            .background(
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                                            alpha = 0.5f
                                                    ),
                                                    RoundedCornerShape(4.dp)
                                            )
                                            .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = file,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                        )
                        IconButton(
                                onClick = { openFile(file) },
                                modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = "打开文件",
                                    tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 文件类别列表项 */
@Composable
fun CategoryItem(category: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
            modifier =
                    Modifier.fillMaxWidth()
                            .background(
                                    when {
                                        isSelected ->
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                        else -> Color.Transparent
                                    }
                            )
                            .clickable(onClick = onClick)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        // 类别图标
        Icon(
                imageVector = getCategoryIcon(category),
                contentDescription = null,
                tint =
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        },
                modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 类别名称
        Text(
                text = getCategoryDisplayName(category),
                style = MaterialTheme.typography.bodyMedium,
                color =
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
        )
    }
}

/** 格式选择项 */
@Composable
fun FormatItem(format: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
            modifier =
                    Modifier.aspectRatio(1f)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                                        else ->
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.5f
                                                )
                                    }
                            )
                            .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color =
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(onClick = onClick)
                            .padding(4.dp),
            contentAlignment = Alignment.Center
    ) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
        ) {
            Icon(
                    imageVector = getFormatIcon(format),
                    contentDescription = null,
                    tint =
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                    text = format.uppercase(),
                    style =
                            MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight =
                                            if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                    color =
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
            )
        }
    }
}

/** 获取类别图标 */
fun getCategoryIcon(category: String): ImageVector {
    return when (category.lowercase()) {
        "document" -> Icons.Default.Description
        "image" -> Icons.Default.Image
        "audio" -> Icons.Default.AudioFile
        "video" -> Icons.Default.VideoFile
        "archive" -> Icons.Default.FolderZip
        else -> Icons.Default.InsertDriveFile
    }
}

/** 获取类别显示名称 */
fun getCategoryDisplayName(category: String): String {
    return when (category.lowercase()) {
        "document" -> "文档"
        "image" -> "图片"
        "audio" -> "音频"
        "video" -> "视频"
        "archive" -> "压缩包"
        else -> category
    }
}

/** 获取格式图标 */
fun getFormatIcon(format: String): ImageVector {
    return when (format.lowercase()) {
        "pdf" -> Icons.Default.PictureAsPdf
        "docx" -> Icons.Default.Description
        "txt" -> Icons.Default.TextSnippet
        else -> Icons.Default.InsertDriveFile
    }
}
