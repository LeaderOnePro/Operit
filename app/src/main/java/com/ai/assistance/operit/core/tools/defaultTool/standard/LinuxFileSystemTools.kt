package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.FilePartContentData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.FileUtils

/**
 * Linux文件系统工具类，专门处理Linux环境下的文件操作
 * 使用SSH/本地文件系统提供者来操作Linux文件系统
 */
class LinuxFileSystemTools(context: Context) : StandardFileSystemTools(context) {
    companion object {
        private const val TAG = "LinuxFileSystemTools"
    }

    /** Adds line numbers to a string of content, starting from a specific line number. */
    private fun addLineNumbers(content: String, startLine: Int, totalLines: Int): String {
        val lines = content.lines()
        if (lines.isEmpty()) return ""
        val maxDigits = if (totalLines > 0) totalLines.toString().length else lines.size.toString().length
        return lines.mapIndexed { index, line ->
            "${(startLine + index + 1).toString().padStart(maxDigits, ' ')}| $line"
        }.joinToString("\n")
    }

    /** 列出Linux目录中的文件 */
    override suspend fun listFiles(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        return try {
            if (!linuxFileSystem.exists(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Directory does not exist: $path"
                )
            }

            if (!linuxFileSystem.isDirectory(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path is not a directory: $path"
                )
            }

            val fileInfoList = linuxFileSystem.listDirectory(path)
            if (fileInfoList == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to list directory: $path"
                )
            }

            val entries = fileInfoList.map { fileInfo ->
                DirectoryListingData.FileEntry(
                    name = fileInfo.name,
                    isDirectory = fileInfo.isDirectory,
                    size = fileInfo.size,
                    permissions = fileInfo.permissions,
                    lastModified = fileInfo.lastModified
                )
            }

            Log.d(TAG, "Listed ${entries.size} entries in directory $path")

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = DirectoryListingData(path, entries),
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error listing directory", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error listing directory: ${e.message}"
            )
        }
    }

    /** 读取Linux文件的完整内容 */
    override suspend fun readFileFull(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        try {
            if (!linuxFileSystem.exists(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "File does not exist: $path"
                )
            }

            if (!linuxFileSystem.isFile(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path is not a file: $path"
                )
            }

            val fileExt = path.substringAfterLast('.', "").lowercase()
            
            // 特殊文件类型处理（图片、PDF等）暂时不支持在Linux环境
            // 因为这些需要Android本地文件访问
            if (fileExt in listOf("doc", "docx", "pdf", "jpg", "jpeg", "png", "gif", "bmp")) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Special file types (images, PDF, Word) are not supported in Linux environment. Please use local environment."
                )
            }

            // 检查文件是否是文本文件
            val sample = linuxFileSystem.readFileSample(path, 512)
            if (sample == null || !FileUtils.isTextLike(sample)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "File does not appear to be a text file. Use specialized tools for binary files."
                )
            }

            val content = linuxFileSystem.readFile(path)
            if (content == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to read file: $path"
                )
            }

            val fileSize = linuxFileSystem.getFileSize(path)
            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileContentData(
                    path = path,
                    content = content,
                    size = fileSize
                ),
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file (full)", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error reading file: ${e.message}"
            )
        }
    }

    /** 检查Linux文件是否存在 */
    override suspend fun fileExists(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        return try {
            val exists = linuxFileSystem.exists(path)

            if (!exists) {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileExistsData(path = path, exists = false),
                    error = ""
                )
            }

            val isDirectory = linuxFileSystem.isDirectory(path)
            val size = linuxFileSystem.getFileSize(path)

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileExistsData(
                    path = path,
                    exists = true,
                    isDirectory = isDirectory,
                    size = size
                ),
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking file existence", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileExistsData(
                    path = path,
                    exists = false,
                    isDirectory = false,
                    size = 0
                ),
                error = "Error checking file existence: ${e.message}"
            )
        }
    }

    /** 读取Linux文件（基础版本，带大小限制） */
    override suspend fun readFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        try {
            if (!linuxFileSystem.exists(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "File does not exist: $path"
                )
            }

            if (!linuxFileSystem.isFile(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path is not a file: $path"
                )
            }

            val fileExt = path.substringAfterLast('.', "").lowercase()

            // 特殊文件类型不支持
            if (fileExt in listOf("doc", "docx", "pdf", "jpg", "jpeg", "png", "gif", "bmp")) {
                // 对于特殊类型，先尝试读取完整文件
                return readFileFull(tool)
            }

            // 检查文件大小
            val fileSize = linuxFileSystem.getFileSize(path)
            val maxFileSizeBytes = apiPreferences.getMaxFileSizeBytes()

            if (fileSize > maxFileSizeBytes) {
                // 文件过大，读取限制大小
                val content = linuxFileSystem.readFileWithLimit(path, maxFileSizeBytes.toInt())
                if (content == null) {
                    return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to read file: $path"
                    )
                }

                val truncatedMsg = "\n\n[File truncated. Size: $fileSize bytes, showing first $maxFileSizeBytes bytes]"
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = FileContentData(
                        path = path,
                        content = content + truncatedMsg,
                        size = fileSize
                    ),
                    error = ""
                )
            } else {
                // 文件大小合适，读取完整内容
                return readFileFull(tool)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error reading file: ${e.message}"
            )
        }
    }

    /** 读取Linux文件的部分内容（分页） */
    override suspend fun readFilePart(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val partIndex = tool.parameters.find { it.name == "partIndex" }?.value?.toIntOrNull() ?: 0

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        return try {
            if (!linuxFileSystem.exists(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "File does not exist or is not a regular file: $path"
                )
            }

            if (!linuxFileSystem.isFile(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Path is not a file: $path"
                )
            }

            // 获取分段大小
            val partSize = apiPreferences.getPartSize()

            // 获取总行数
            val totalLines = linuxFileSystem.getLineCount(path)
            val totalParts = (totalLines + partSize - 1) / partSize
            val validPartIndex = partIndex.coerceIn(0, if (totalParts > 0) totalParts - 1 else 0)

            val startLine = validPartIndex * partSize + 1 // 1-indexed for sed
            val endLine = minOf(startLine + partSize - 1, totalLines)

            val partContent = if (totalLines > 0) {
                linuxFileSystem.readFileLines(path, startLine, endLine) ?: ""
            } else {
                ""
            }

            val contentWithLineNumbers = addLineNumbers(partContent, startLine - 1, totalLines)

            ToolResult(
                toolName = tool.name,
                success = true,
                result = FilePartContentData(
                    path = path,
                    content = contentWithLineNumbers,
                    partIndex = validPartIndex,
                    totalParts = totalParts,
                    startLine = startLine,
                    endLine = endLine,
                    totalLines = totalLines
                ),
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file part", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error reading file part: ${e.message}"
            )
        }
    }

    /** 写入Linux文件 */
    override suspend fun writeFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val content = tool.parameters.find { it.name == "content" }?.value ?: ""
        val append = tool.parameters.find { it.name == "append" }?.value?.toBoolean() ?: false

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "write",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            val result = linuxFileSystem.writeFile(path, content, append)

            if (!result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = if (append) "append" else "write",
                        path = path,
                        successful = false,
                        details = result.message
                    ),
                    error = result.message
                )
            }

            val operation = if (append) "append" else "write"
            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileOperationData(
                    operation = operation,
                    path = path,
                    successful = true,
                    details = result.message
                ),
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error writing to file", e)
            val errorMessage = "Error writing to file: ${e.message}"

            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = if (append) "append" else "write",
                    path = path,
                    successful = false,
                    details = errorMessage
                ),
                error = errorMessage
            )
        }
    }

    /** 写入二进制文件到Linux */
    override suspend fun writeFileBinary(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val base64Content = tool.parameters.find { it.name == "base64Content" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "write_binary",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        if (base64Content.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "write_binary",
                    path = path,
                    successful = false,
                    details = "base64Content parameter is required"
                ),
                error = "base64Content parameter is required"
            )
        }

        return try {
            // 解码base64内容
            val bytes = android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT)
            val result = linuxFileSystem.writeFileBytes(path, bytes)

            if (!result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "write_binary",
                        path = path,
                        successful = false,
                        details = result.message
                    ),
                    error = result.message
                )
            }

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileOperationData(
                    operation = "write_binary",
                    path = path,
                    successful = true,
                    details = result.message
                ),
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error writing binary file", e)
            val errorMessage = "Error writing binary file: ${e.message}"

            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "write_binary",
                    path = path,
                    successful = false,
                    details = errorMessage
                ),
                error = errorMessage
            )
        }
    }

    /** 删除Linux文件或目录 */
    override suspend fun deleteFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val recursive = tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: false

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "delete",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            if (!linuxFileSystem.exists(path)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "delete",
                        path = path,
                        successful = false,
                        details = "Path does not exist: $path"
                    ),
                    error = "Path does not exist: $path"
                )
            }

            val result = linuxFileSystem.delete(path, recursive)

            if (!result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "delete",
                        path = path,
                        successful = false,
                        details = result.message
                    ),
                    error = result.message
                )
            }

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileOperationData(
                    operation = "delete",
                    path = path,
                    successful = true,
                    details = result.message
                ),
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file", e)
            val errorMessage = "Error deleting file: ${e.message}"

            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "delete",
                    path = path,
                    successful = false,
                    details = errorMessage
                ),
                error = errorMessage
            )
        }
    }

    /** 移动/重命名Linux文件 */
    override suspend fun moveFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "sourcePath" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destPath" }?.value ?: ""

        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "move",
                    path = sourcePath,
                    successful = false,
                    details = "Both sourcePath and destPath parameters are required"
                ),
                error = "Both sourcePath and destPath parameters are required"
            )
        }

        return try {
            if (!linuxFileSystem.exists(sourcePath)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "move",
                        path = sourcePath,
                        successful = false,
                        details = "Source path does not exist: $sourcePath"
                    ),
                    error = "Source path does not exist: $sourcePath"
                )
            }

            val result = linuxFileSystem.move(sourcePath, destPath)

            if (!result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "move",
                        path = sourcePath,
                        successful = false,
                        details = result.message
                    ),
                    error = result.message
                )
            }

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileOperationData(
                    operation = "move",
                    path = sourcePath,
                    successful = true,
                    details = result.message
                ),
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error moving file", e)
            val errorMessage = "Error moving file: ${e.message}"

            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "move",
                    path = sourcePath,
                    successful = false,
                    details = errorMessage
                ),
                error = errorMessage
            )
        }
    }

    /** 复制Linux文件或目录 */
    override suspend fun copyFile(tool: AITool): ToolResult {
        val sourcePath = tool.parameters.find { it.name == "sourcePath" }?.value ?: ""
        val destPath = tool.parameters.find { it.name == "destPath" }?.value ?: ""
        val recursive = tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: true

        if (sourcePath.isBlank() || destPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = false,
                    details = "Both sourcePath and destPath parameters are required"
                ),
                error = "Both sourcePath and destPath parameters are required"
            )
        }

        return try {
            val result = linuxFileSystem.copy(sourcePath, destPath, recursive)

            if (!result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "copy",
                        path = sourcePath,
                        successful = false,
                        details = result.message
                    ),
                    error = result.message
                )
            }

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = true,
                    details = result.message
                ),
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error copying file", e)
            val errorMessage = "Error copying file: ${e.message}"

            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "copy",
                    path = sourcePath,
                    successful = false,
                    details = errorMessage
                ),
                error = errorMessage
            )
        }
    }

    /** 创建Linux目录 */
    override suspend fun makeDirectory(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val createParents = tool.parameters.find { it.name == "createParents" }?.value?.toBoolean() ?: false

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "mkdir",
                    path = "",
                    successful = false,
                    details = "Path parameter is required"
                ),
                error = "Path parameter is required"
            )
        }

        return try {
            val result = linuxFileSystem.createDirectory(path, createParents)

            if (!result.success) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = FileOperationData(
                        operation = "mkdir",
                        path = path,
                        successful = false,
                        details = result.message
                    ),
                    error = result.message
                )
            }

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = FileOperationData(
                    operation = "mkdir",
                    path = path,
                    successful = true,
                    details = result.message
                ),
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating directory", e)
            val errorMessage = "Error creating directory: ${e.message}"

            return ToolResult(
                toolName = tool.name,
                success = false,
                result = FileOperationData(
                    operation = "mkdir",
                    path = path,
                    successful = false,
                    details = errorMessage
                ),
                error = errorMessage
            )
        }
    }

    /** 在Linux文件系统中查找文件 */
    override suspend fun findFiles(tool: AITool): ToolResult {
        val basePath = tool.parameters.find { it.name == "basePath" }?.value ?: ""
        val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""

        if (basePath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "basePath parameter is required"
            )
        }

        if (pattern.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "pattern parameter is required"
            )
        }

        return try {
            if (!linuxFileSystem.exists(basePath)) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Base path does not exist: $basePath"
                )
            }

            val files = linuxFileSystem.findFiles(
                basePath = basePath,
                pattern = pattern,
                maxDepth = -1,
                caseInsensitive = false
            )

            val resultText = if (files.isEmpty()) {
                "No files found matching pattern '$pattern' in $basePath"
            } else {
                "Found ${files.size} file(s):\n" + files.joinToString("\n")
            }

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(resultText),
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error finding files", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error finding files: ${e.message}"
            )
        }
    }

    /** 获取Linux文件信息 */
    override suspend fun fileInfo(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        return try {
            val fileInfo = linuxFileSystem.getFileInfo(path)
            if (fileInfo == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "File does not exist: $path"
                )
            }

            val infoText = buildString {
                appendLine("File Information for: $path")
                appendLine("Name: ${fileInfo.name}")
                appendLine("Type: ${if (fileInfo.isDirectory) "Directory" else "File"}")
                appendLine("Size: ${fileInfo.size} bytes")
                appendLine("Permissions: ${fileInfo.permissions}")
                appendLine("Last Modified: ${fileInfo.lastModified}")
            }

            return ToolResult(
                toolName = tool.name,
                success = true,
                result = StringResultData(infoText),
                error = ""
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file info", e)
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Error getting file info: ${e.message}"
            )
        }
    }

    /** 打开Linux文件（暂不支持） */
    override suspend fun openFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        return ToolResult(
            toolName = tool.name,
            success = false,
            result = StringResultData(""),
            error = "Opening files is not supported in Linux environment. Use readFile instead."
        )
    }

    /** 在Linux代码中搜索（grep） */
    override suspend fun grepCode(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""
        val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""
        val maxResults = tool.parameters.find { it.name == "max_results" }?.value?.toIntOrNull() ?: 100

        if (path.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Path parameter is required"
            )
        }

        if (pattern.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Pattern parameter is required"
            )
        }

        return ToolResult(
            toolName = tool.name,
            success = false,
            result = StringResultData(""),
            error = "Code search (grep) is not yet implemented for Linux environment"
        )
    }

    /** 分享Linux文件（暂不支持） */
    override suspend fun shareFile(tool: AITool): ToolResult {
        val path = tool.parameters.find { it.name == "path" }?.value ?: ""

        return ToolResult(
            toolName = tool.name,
            success = false,
            result = StringResultData(""),
            error = "File sharing is not supported in Linux environment"
        )
    }
}

