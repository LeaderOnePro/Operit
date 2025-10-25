package com.ai.assistance.operit.services.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.AttachmentInfo
import com.ai.assistance.operit.data.model.ToolParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import android.provider.DocumentsContract
import java.io.File

/**
 * Manages attachment operations for the chat feature Handles adding, removing, and referencing
 * attachments
 */
class AttachmentDelegate(private val context: Context, private val toolHandler: AIToolHandler) {
    companion object {
        private const val TAG = "AttachmentDelegate"
    }

    // State for attachments
    private val _attachments = MutableStateFlow<List<AttachmentInfo>>(emptyList())
    val attachments: StateFlow<List<AttachmentInfo>> = _attachments

    // Events
    private val _toastEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toastEvent: SharedFlow<String> = _toastEvent

    /**
     * Inserts a reference to an attachment at the current cursor position in the user's message
     * @return the formatted reference string
     */
    fun createAttachmentReference(attachment: AttachmentInfo): String {
        // Generate XML reference for the attachment
        val attachmentRef = StringBuilder("<attachment ")
        attachmentRef.append("id=\"${attachment.filePath}\" ")
        attachmentRef.append("filename=\"${attachment.fileName}\" ")
        attachmentRef.append("type=\"${attachment.mimeType}\" ")

        // Add size property
        if (attachment.fileSize > 0) {
            attachmentRef.append("size=\"${attachment.fileSize}\" ")
        }

        // Add content property (if exists)
        if (attachment.content.isNotEmpty()) {
            attachmentRef.append("content=\"${attachment.content}\" ")
        }

        attachmentRef.append("/>")

        return attachmentRef.toString()
    }

    /** Handles a photo taken by the camera */
    suspend fun handleTakenPhoto(uri: Uri) =
            withContext(Dispatchers.IO) {
                try {
                    val fileName = "camera_${System.currentTimeMillis()}.jpg"
                    val tempFile = createTempFileFromUri(uri, fileName)

                    if (tempFile != null) {
                        Log.d(TAG, "成功从相机URI创建临时文件: ${tempFile.absolutePath}")

                        val attachmentInfo =
                                AttachmentInfo(
                                        filePath = tempFile.absolutePath,
                                        fileName = fileName,
                                        mimeType = "image/jpeg",
                                        fileSize = tempFile.length()
                                )

                        val currentList = _attachments.value
                        if (!currentList.any { it.filePath == tempFile.absolutePath }) {
                            _attachments.value = currentList + attachmentInfo
                        }

                        _toastEvent.emit("已添加照片附件: $fileName")
                    } else {
                        Log.e(TAG, "无法从相机URI创建临时文件")
                        _toastEvent.emit("无法处理拍摄的照片，请重试")
                    }
                } catch (e: Exception) {
                    _toastEvent.emit("添加照片附件失败: ${e.message}")
                    Log.e(TAG, "添加照片附件错误", e)
                }
            }

    /** Handles a file or image attachment selected by the user 确保在IO线程执行所有文件操作 */
    suspend fun handleAttachment(filePath: String) =
            withContext(Dispatchers.IO) {
                try {
                    // 检查是否是媒体选择器特殊路径
                    if (filePath.contains("/sdcard/.transforms/synthetic/picker/") ||
                                    filePath.contains("/com.android.providers.media.photopicker/")
                    ) {
                        Log.d(TAG, "检测到媒体选择器特殊路径: $filePath")

                        try {
                            // 尝试从特殊路径提取实际URI
                            val actualUri = extractMediaStoreUri(filePath)
                            if (actualUri != null) {
                                // 使用提取出的URI创建临时文件
                                val fileName = filePath.substringAfterLast('/')
                                val tempFile = createTempFileFromUri(actualUri, fileName)

                                if (tempFile != null) {
                                    Log.d(TAG, "成功从媒体选择器路径创建临时文件: ${tempFile.absolutePath}")

                                    // 创建附件对象
                                    val mimeType =
                                            getMimeTypeFromPath(tempFile.name) ?: "image/jpeg"
                                    val attachmentInfo =
                                            AttachmentInfo(
                                                    filePath = tempFile.absolutePath,
                                                    fileName = fileName,
                                                    mimeType = mimeType,
                                                    fileSize = tempFile.length()
                                            )

                                    // 添加到附件列表
                                    val currentList = _attachments.value
                                    if (!currentList.any { it.filePath == tempFile.absolutePath }) {
                                        _attachments.value = currentList + attachmentInfo
                                    }

                                    _toastEvent.emit("已添加附件: $fileName")
                                    return@withContext
                                } else {
                                    Log.e(TAG, "无法从媒体路径创建临时文件")
                                    _toastEvent.emit("无法处理媒体文件，请尝试其他方式")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "处理媒体选择器路径失败", e)
                            // 继续尝试常规处理方法
                        }
                    }

                    // Check if it's a content URI path
                    if (filePath.startsWith("content://")) {
                        // Handle as URI
                        val uri = Uri.parse(filePath)
                        val contentResolver = context.contentResolver

                        // Get file name from ContentResolver
                        val fileName = getFileNameFromUri(uri)

                        // Get file size from ContentResolver
                        val fileSize = getFileSizeFromUri(uri)

                        // Infer MIME type
                        val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

                        // 对所有文件类型尝试获取实际路径
                        var actualFilePath = getFilePathFromUri(uri)

                        // 如果无法获取实际路径，则创建临时文件以确保可用性
                        if (actualFilePath == null) {
                            Log.w(TAG, "无法从URI '$uri' 获取实际文件路径，将复制到临时文件。")
                            // 创建一个临时文件来保存内容
                            val tempFile = createTempFileFromUri(uri, fileName)
                            actualFilePath = tempFile?.absolutePath
                                    ?: filePath // fallback to original uri string if copying fails
                        }

                        val attachmentInfo =
                                AttachmentInfo(
                                        filePath = actualFilePath,
                                        fileName = fileName,
                                        mimeType = mimeType,
                                        fileSize = fileSize
                                )

                        // Add to attachment list
                        val currentList = _attachments.value
                        if (!currentList.any { it.filePath == actualFilePath }) {
                            _attachments.value = currentList + attachmentInfo
                        }

                        _toastEvent.emit("已添加附件: $fileName")
                    } else {
                        // Handle as regular file path
                        val file = java.io.File(filePath)
                        if (!file.exists()) {
                            _toastEvent.emit("文件不存在")
                            return@withContext
                        }

                        val fileName = file.name
                        val fileSize = file.length()
                        val mimeType = getMimeTypeFromPath(filePath) ?: "application/octet-stream"

                        // 图片文件使用绝对路径
                        val actualFilePath =
                                if (mimeType.startsWith("image/")) {
                                    file.absolutePath
                                } else {
                                    filePath
                                }

                        val attachmentInfo =
                                AttachmentInfo(
                                        filePath = actualFilePath,
                                        fileName = fileName,
                                        mimeType = mimeType,
                                        fileSize = fileSize
                                )

                        // Add to attachment list
                        val currentList = _attachments.value
                        if (!currentList.any { it.filePath == actualFilePath }) {
                            _attachments.value = currentList + attachmentInfo
                        }

                        _toastEvent.emit("已添加附件: $fileName")
                    }
                } catch (e: Exception) {
                    _toastEvent.emit("添加附件失败: ${e.message}")
                    Log.e(TAG, "添加附件错误", e)
                }
            }

    /** 从媒体选择器路径提取真实的MediaStore URI */
    private fun extractMediaStoreUri(filePath: String): Uri? {
        try {
            // 从文件名中提取媒体ID
            val mediaId = filePath.substringAfterLast('/').substringBefore('.')
            if (mediaId.toLongOrNull() != null) {
                // 构造MediaStore URI
                return Uri.parse("content://media/external/images/media/$mediaId")
            }

            // 尝试通过直接构造content URI
            if (filePath.contains("com.android.providers.media.photopicker")) {
                val path = "content://com.android.providers.media.photopicker/media/$mediaId"
                return Uri.parse(path)
            }

            // 最后尝试直接将路径转为URI
            return Uri.parse("file://$filePath")
        } catch (e: Exception) {
            Log.e(TAG, "提取媒体URI失败: $filePath", e)
            return null
        }
    }

    /** 从URI创建临时文件 */
    private suspend fun createTempFileFromUri(uri: Uri, fileName: String): java.io.File? =
            withContext(Dispatchers.IO) {
                try {
                    val fileExtension = fileName.substringAfterLast('.', "jpg")

                    // 使用外部存储Download/Operit/cleanOnExit目录，而不是缓存目录
                    val externalDir = java.io.File("/sdcard/Download/Operit/cleanOnExit")

                    // 确保目录存在
                    if (!externalDir.exists()) {
                        externalDir.mkdirs()
                    }

                    // 确保.nomedia文件存在，防止媒体扫描
                    val noMediaFile = java.io.File(externalDir, ".nomedia")
                    if (!noMediaFile.exists()) {
                        noMediaFile.createNewFile()
                    }

                    val tempFile =
                            java.io.File(
                                    externalDir,
                                    "img_${System.currentTimeMillis()}.$fileExtension"
                            )

                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    if (tempFile.exists() && tempFile.length() > 0) {
                        Log.d(TAG, "成功创建临时图片文件: ${tempFile.absolutePath}")
                        return@withContext tempFile
                    }

                    return@withContext null
                } catch (e: Exception) {
                    Log.e(TAG, "创建临时文件失败", e)
                    return@withContext null
                }
            }

    /** Removes an attachment by its file path */
    fun removeAttachment(filePath: String) {
        val currentList = _attachments.value
        _attachments.value = currentList.filter { it.filePath != filePath }
    }

    /** Clear all attachments */
    fun clearAttachments() {
        _attachments.value = emptyList()
    }

    /** Update attachments with a new list */
    fun updateAttachments(newAttachments: List<AttachmentInfo>) {
        _attachments.value = newAttachments
    }

    /**
     * Captures the current screen content and attaches it to the message Uses the get_page_info
     * AITool to retrieve UI structure 确保在IO线程中执行
     */
    suspend fun captureScreenContent() =
            withContext(Dispatchers.IO) {
                try {
                    // Create a tool to get page info
                    val pageInfoTool = AITool(name = "get_page_info", parameters = emptyList())

                    // Execute the tool
                    val result = toolHandler.executeTool(pageInfoTool)

                    if (result.success) {
                        // Generate a unique ID for this screen capture
                        val captureId = "screen_${System.currentTimeMillis()}"
                        val screenContent = result.result.toString()

                        // Create attachment info with content as filePath
                        val attachmentInfo =
                                AttachmentInfo(
                                        filePath = captureId, // Use ID as virtual path
                                        fileName = "screen_content.json",
                                        mimeType = "text/json",
                                        fileSize = screenContent.length.toLong(),
                                        content = screenContent // Add content field to store actual
                                        // data
                                        )

                        // Add to attachments list
                        val currentList = _attachments.value
                        _attachments.value = currentList + attachmentInfo

                        _toastEvent.emit("已添加屏幕内容")
                    } else {
                        _toastEvent.emit("获取屏幕内容失败: ${result.error ?: "未知错误"}")
                    }
                } catch (e: Exception) {
                    _toastEvent.emit("获取屏幕内容失败: ${e.message}")
                    Log.e(TAG, "Error capturing screen content", e)
                }
            }

    /** 获取设备当前通知并作为附件添加到消息 使用get_notifications AITool获取通知数据 确保在IO线程中执行 */
    suspend fun captureNotifications(limit: Int = 10) =
            withContext(Dispatchers.IO) {
                try {
                    // 创建工具参数
                    val toolParams =
                            listOf(
                                    ToolParameter("limit", limit.toString()),
                                    ToolParameter("include_ongoing", "true")
                            )

                    // 创建工具
                    val notificationsToolTask =
                            AITool(name = "get_notifications", parameters = toolParams)

                    // 执行工具
                    val result = toolHandler.executeTool(notificationsToolTask)

                    if (result.success) {
                        // 生成唯一ID
                        val captureId = "notifications_${System.currentTimeMillis()}"
                        val notificationsContent = result.result.toString()

                        // 创建附件信息
                        val attachmentInfo =
                                AttachmentInfo(
                                        filePath = captureId,
                                        fileName = "notifications.json",
                                        mimeType = "application/json",
                                        fileSize = notificationsContent.length.toLong(),
                                        content = notificationsContent
                                )

                        // 添加到附件列表
                        val currentList = _attachments.value
                        _attachments.value = currentList + attachmentInfo

                        _toastEvent.emit("已添加当前通知")
                    } else {
                        _toastEvent.emit("获取通知失败: ${result.error ?: "未知错误"}")
                    }
                } catch (e: Exception) {
                    _toastEvent.emit("获取通知失败: ${e.message}")
                    Log.e(TAG, "Error capturing notifications", e)
                }
            }

    /** 获取设备当前位置并作为附件添加到消息 使用get_device_location AITool获取位置数据 确保在IO线程中执行 */
    suspend fun captureLocation(highAccuracy: Boolean = true) =
            withContext(Dispatchers.IO) {
                try {
                    // 创建工具参数
                    val toolParams =
                            listOf(
                                    ToolParameter("high_accuracy", highAccuracy.toString()),
                                    ToolParameter("timeout", "10") // 10秒超时
                            )

                    // 创建工具
                    val locationToolTask =
                            AITool(name = "get_device_location", parameters = toolParams)

                    // 执行工具
                    val result = toolHandler.executeTool(locationToolTask)

                    if (result.success) {
                        // 生成唯一ID
                        val captureId = "location_${System.currentTimeMillis()}"
                        val locationContent = result.result.toString()

                        // 创建附件信息
                        val attachmentInfo =
                                AttachmentInfo(
                                        filePath = captureId,
                                        fileName = "location.json",
                                        mimeType = "application/json",
                                        fileSize = locationContent.length.toLong(),
                                        content = locationContent
                                )

                        // 添加到附件列表
                        val currentList = _attachments.value
                        _attachments.value = currentList + attachmentInfo

                        _toastEvent.emit("已添加当前位置")
                    } else {
                        _toastEvent.emit("获取位置失败: ${result.error ?: "未知错误"}")
                    }
                } catch (e: Exception) {
                    _toastEvent.emit("获取位置失败: ${e.message}")
                    Log.e(TAG, "Error capturing location", e)
                }
            }

    /** 
     * 捕获记忆文件夹并作为附件添加到消息
     * @param folderPaths 选中的记忆文件夹路径列表
     */
    suspend fun captureMemoryFolders(folderPaths: List<String>) =
            withContext(Dispatchers.IO) {
                try {
                    if (folderPaths.isEmpty()) {
                        _toastEvent.emit("未选择任何记忆文件夹")
                        return@withContext
                    }

                    // 生成唯一ID
                    val captureId = "memory_context_${System.currentTimeMillis()}"
                    
                    // 构建XML格式的记忆上下文
                    val memoryContext = buildMemoryContextXml(folderPaths)

                    // 创建附件信息
                    val attachmentInfo =
                            AttachmentInfo(
                                    filePath = captureId,
                                    fileName = "memory_context.xml",
                                    mimeType = "application/xml",
                                    fileSize = memoryContext.length.toLong(),
                                    content = memoryContext
                            )

                    // 添加到附件列表
                    val currentList = _attachments.value
                    _attachments.value = currentList + attachmentInfo

                    val folderCountText = if (folderPaths.size == 1) {
                        "已添加记忆文件夹: ${folderPaths[0]}"
                    } else {
                        "已添加 ${folderPaths.size} 个记忆文件夹"
                    }
                    _toastEvent.emit(folderCountText)
                } catch (e: Exception) {
                    _toastEvent.emit("添加记忆文件夹失败: ${e.message}")
                    Log.e(TAG, "Error capturing memory folders", e)
                }
            }

    /**
     * 构建记忆上下文XML字符串
     */
    private fun buildMemoryContextXml(folderPaths: List<String>): String {
        val foldersText = folderPaths.joinToString("\n") { "  - $it" }
        val examplePath = folderPaths.firstOrNull() ?: "some/folder/path"

        return """
<memory_context>
 <available_folders>
$foldersText
 </available_folders>
 <instruction>
- **CRITICAL**: To search within the folders listed above, you **MUST** use the `query_memory` tool and provide the `folder_path` parameter.
- The `folder_path` parameter's value **MUST** be one of the paths from the `<available_folders>` list.
- Autonomously decide whether a search is needed and what query to use based on the user's question.
- Example: `<tool name="query_memory"><param name="query">search query</param><param name="folder_path">$examplePath</param></tool>`
 </instruction>
</memory_context>""".trimIndent()
    }

    /** 从Content URI获取文件的实际路径，不复制文件内容 */
    private fun getFilePathFromUri(uri: Uri): String? {
        try {
            // 尝试直接从URI获取路径
            if (uri.scheme == "file") {
                return uri.path
            }
            
            // 对于content URI，使用不同的方法尝试获取实际路径
            if (uri.scheme == "content") {
                // 特殊处理: Downloads提供程序URI
                if (uri.authority == "com.android.providers.downloads.documents") {
                    val id = android.provider.DocumentsContract.getDocumentId(uri)
                    
                    // 处理raw:前缀，直接解码路径
                    if (id.startsWith("raw:")) {
                        val decodedPath = java.net.URLDecoder.decode(id.substring(4), "UTF-8")
                        Log.d(TAG, "Downloads文档URI解析为: $decodedPath")
                        return decodedPath
                    }
                    
                    // 处理msf:前缀
                    else if (id.startsWith("msf:")) {
                        // MediaStore format.
                        val mediaId = id.substring(4)
                        // We can't know from the URI alone if it's an image, video, or audio file.
                        // So we'll use the generic files table.
                        val contentUri = android.provider.MediaStore.Files.getContentUri("external")
                        val selection = "_id=?"
                        val selectionArgs = arrayOf(mediaId)
                        return getDataColumn(contentUri, selection, selectionArgs)
                    }
                    
                    // 普通ID，使用下载内容URI
                    else {
                        val contentUri = android.content.ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), 
                            id.toLong()
                        )
                        return getDataColumn(contentUri, null, null)
                    }
                }
                
                // 方法1: 通过DocumentsContract获取路径 (API 19+)
                try {
                    val docId = android.provider.DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":")
                    val type = split[0]
                    
                    // 对于外部存储文件
                    if ("primary".equals(type, ignoreCase = true)) {
                        return "/storage/emulated/0/${split[1]}"
                    }
                    
                    // 对于SD卡
                    if ("sdcard".equals(type, ignoreCase = true) && split.size > 1) {
                        return "/storage/sdcard1/${split[1]}"
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "通过DocumentsContract获取路径失败", e)
                }
                
                // 方法2: 通过MediaStore查询
                return getDataColumn(uri, null, null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取文件实际路径失败: ${e.message}", e)
        }
        
        return null
    }
    
    /** 从URI获取数据列(DATA)的值 */
    private fun getDataColumn(uri: Uri, selection: String?, selectionArgs: Array<String>?): String? {
        val projection = arrayOf(android.provider.MediaStore.MediaColumns.DATA)
        
        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DATA)
                    return cursor.getString(columnIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "查询URI数据列失败: ${e.message}", e)
        }
        
        return null
    }

    /** Get file name from content URI */
    private suspend fun getFileNameFromUri(uri: Uri): String =
            withContext(Dispatchers.IO) {
                val contentResolver = context.contentResolver
                var fileName = "未知文件"

                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val displayNameIndex =
                                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (displayNameIndex != -1) {
                                fileName = cursor.getString(displayNameIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting file name from URI", e)
                }

                return@withContext fileName
            }

    /** Get file size from content URI */
    private suspend fun getFileSizeFromUri(uri: Uri): Long =
            withContext(Dispatchers.IO) {
                val contentResolver = context.contentResolver
                var fileSize = 0L

                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIndex != -1) {
                                fileSize = cursor.getLong(sizeIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting file size from URI", e)
                }

                return@withContext fileSize
            }

    /** Get MIME type from file path */
    private fun getMimeTypeFromPath(filePath: String): String? {
        val extension = filePath.substringAfterLast('.', "")
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "zip" -> "application/zip"
            else -> null
        }
    }
}
