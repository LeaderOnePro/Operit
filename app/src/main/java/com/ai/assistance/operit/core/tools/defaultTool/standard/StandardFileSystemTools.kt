package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.FileApplyResultData
import com.ai.assistance.operit.core.tools.FileContentData
import com.ai.assistance.operit.core.tools.FileExistsData
import com.ai.assistance.operit.core.tools.FileInfoData
import com.ai.assistance.operit.core.tools.FileOperationData
import com.ai.assistance.operit.core.tools.FilePartContentData
import com.ai.assistance.operit.core.tools.FindFilesResultData
import com.ai.assistance.operit.core.tools.GrepResultData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.ai.assistance.operit.util.FileUtils
import com.ai.assistance.operit.util.SyntaxCheckUtil
import com.ai.assistance.operit.util.PathMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.content.FileProvider
import android.webkit.MimeTypeMap
import com.ai.assistance.operit.api.chat.enhance.FileBindingService
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.provider.filesystem.FileSystemProvider
import com.ai.assistance.operit.core.tools.defaultTool.PathValidator

/**
 * Collection of file system operation tools for the AI assistant These tools use Java File APIs for
 * file operations
 */
open class StandardFileSystemTools(protected val context: Context) {
        companion object {
                private const val TAG = "FileSystemTools"
        }

        // ApiPreferences 实例，用于动态获取配置
        protected val apiPreferences: ApiPreferences by lazy {
                ApiPreferences.getInstance(context)
        }

        // Linux文件系统提供者，从TerminalManager获取
        protected val linuxFileSystem: FileSystemProvider by lazy {
                TerminalManager.getInstance(context).getFileSystemProvider()
        }

        // Linux文件系统工具实例
        private val linuxTools: LinuxFileSystemTools by lazy {
                LinuxFileSystemTools(context)
        }

        /** 检查是否是Linux环境 */
        protected fun isLinuxEnvironment(environment: String?): Boolean {
                return environment?.lowercase() == "linux"
        }

        /** Adds line numbers to a string of content. */
        private fun addLineNumbers(content: String): String {
                val lines = content.lines()
                if (lines.isEmpty()) return ""
                val maxDigits = lines.size.toString().length
                return lines.mapIndexed { index, line ->
                        "${(index + 1).toString().padStart(maxDigits, ' ')}| $line"
                }.joinToString("\n")
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

        /** List files in a directory */
        open suspend fun listFiles(tool: AITool): ToolResult {
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value

                // 如果是Linux环境，委托给LinuxFileSystemTools
                if (isLinuxEnvironment(environment)) {
                        return linuxTools.listFiles(tool)
                }
                PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

                if (path.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Path parameter is required"
                        )
                }

                return try {
                        val directory = File(path)

                        if (!directory.exists()) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = StringResultData(""),
                                        error = "Directory does not exist: $path"
                                )
                        }

                        if (!directory.isDirectory) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = StringResultData(""),
                                        error = "Path is not a directory: $path"
                                )
                        }

                        val entries = mutableListOf<DirectoryListingData.FileEntry>()
                        val files = directory.listFiles() ?: emptyArray()

                        val dateFormat = SimpleDateFormat("MMM dd HH:mm", Locale.US)

                        for (file in files) {
                                if (file.name != "." && file.name != "..") {
                                        entries.add(
                                                DirectoryListingData.FileEntry(
                                                        name = file.name,
                                                        isDirectory = file.isDirectory,
                                                        size = file.length(),
                                                        permissions = getFilePermissions(file),
                                                        lastModified =
                                                                dateFormat.format(
                                                                        Date(file.lastModified())
                                                                )
                                                )
                                        )
                                }
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

        /** Get file permissions as a string like "rwxr-xr-x" */
        protected fun getFilePermissions(file: File): String {
                // Java has limited capabilities for getting Unix-style file permissions
                // This is a simplified version that checks basic permissions
                val canRead = if (file.canRead()) 'r' else '-'
                val canWrite = if (file.canWrite()) 'w' else '-'
                val canExecute = if (file.canExecute()) 'x' else '-'

                // For simplicity, we'll use the same permissions for user, group, and others
                return "$canRead$canWrite$canExecute$canRead-$canExecute$canRead-$canExecute"
        }

        /**
         * Handles reading special file types that require conversion or OCR. Returns a ToolResult
         * if the file type is special, otherwise null.
         */
        protected open suspend fun handleSpecialFileRead(
                tool: AITool,
                path: String,
                fileExt: String
        ): ToolResult? {
                return when (fileExt) {
                        "doc", "docx" -> {
                                Log.d(
                                        TAG,
                                        "Detected Word document, attempting to extract text"
                                )
                                val tempFilePath =
                                        "${path}_converted_${System.currentTimeMillis()}.txt"
                                try {
                                        val sourceFile = File(path)
                                        val tempFile = File(tempFilePath)
                                        val success = com.ai.assistance.operit.util.DocumentConversionUtil
                                                .extractTextFromWord(sourceFile, tempFile, fileExt)

                                        if (success && tempFile.exists()) {
                                                Log.d(
                                                        TAG,
                                                        "Successfully extracted text from Word document"
                                                )
                                                val content = tempFile.readText()
                                                tempFile.delete() // Clean up
                                                ToolResult(
                                                        toolName = tool.name,
                                                        success = true,
                                                        result =
                                                                FileContentData(
                                                                        path = path,
                                                                        content = content,
                                                                        size = content.length.toLong(),
                                                                ),
                                                        error = ""
                                                )
                                        } else {
                                                Log.w(
                                                        TAG,
                                                        "Word text extraction failed, returning error"
                                                )
                                                ToolResult(
                                                        toolName = tool.name,
                                                        success = false,
                                                        result = StringResultData(""),
                                                        error = "Failed to extract text from Word document"
                                                )
                                        }
                                } catch (e: Exception) {
                                        Log.e(TAG, "Error during Word document text extraction", e)
                                        ToolResult(
                                                toolName = tool.name,
                                                success = false,
                                                result = StringResultData(""),
                                                error = "Error extracting text from Word document: ${e.message}"
                                        )
                                }
                        }
                        "pdf" -> {
                                Log.d(
                                        TAG,
                                        "Detected PDF document, attempting to extract text"
                                )
                                val tempFilePath =
                                        "${path}_converted_${System.currentTimeMillis()}.txt"
                                try {
                                        val sourceFile = File(path)
                                        val tempFile = File(tempFilePath)
                                        val success = com.ai.assistance.operit.util.DocumentConversionUtil
                                                .extractTextFromPdf(context, sourceFile, tempFile)

                                        if (success && tempFile.exists()) {
                                                Log.d(
                                                        TAG,
                                                        "Successfully extracted text from PDF document"
                                                )
                                                val content = tempFile.readText()
                                                tempFile.delete() // Clean up
                                                ToolResult(
                                                        toolName = tool.name,
                                                        success = true,
                                                        result =
                                                                FileContentData(
                                                                        path = path,
                                                                        content = content,
                                                                        size = content.length.toLong(),
                                                                ),
                                                        error = ""
                                                )
                                        } else {
                                                Log.w(
                                                        TAG,
                                                        "PDF text extraction failed, returning error"
                                                )
                                                ToolResult(
                                                        toolName = tool.name,
                                                        success = false,
                                                        result = StringResultData(""),
                                                        error = "Failed to extract text from PDF document"
                                                )
                                        }
                                } catch (e: Exception) {
                                        Log.e(TAG, "Error during PDF document text extraction", e)
                                        ToolResult(
                                                toolName = tool.name,
                                                success = false,
                                                result = StringResultData(""),
                                                error = "Error extracting text from PDF document: ${e.message}"
                                        )
                                }
                        }
                        "jpg", "jpeg", "png", "gif", "bmp" -> {
                                // 获取可选的intent参数
                                val intent = tool.parameters.find { it.name == "intent" }?.value
                                
                                Log.d(
                                        TAG,
                                        "Detected image file, intent=${intent ?: "无"}"
                                )
                                
                                // 如果提供了intent，使用识图模型
                                if (!intent.isNullOrBlank()) {
                                        try {
                                                val enhancedService = com.ai.assistance.operit.api.chat.EnhancedAIService.getInstance(context)
                                                val analysisResult = kotlinx.coroutines.runBlocking {
                                                        enhancedService.analyzeImageWithIntent(path, intent)
                                                }
                                                
                                                return ToolResult(
                                                        toolName = tool.name,
                                                        success = true,
                                                        result = FileContentData(
                                                                path = path,
                                                                content = analysisResult,
                                                                size = analysisResult.length.toLong()
                                                        ),
                                                        error = ""
                                                )
                                        } catch (e: Exception) {
                                                Log.e(TAG, "识图模型调用失败，回退到OCR", e)
                                                // 回退到默认OCR处理
                                        }
                                }
                                
                                // 默认OCR处理
                                try {
                                        val bitmap = android.graphics.BitmapFactory.decodeFile(path)
                                        if (bitmap != null) {
                                                val ocrText =
                                                        kotlinx.coroutines.runBlocking {
                                                                com.ai.assistance.operit.util
                                                                        .OCRUtils.recognizeText(
                                                                        context,
                                                                        bitmap
                                                                )
                                                        }
                                                if (ocrText.isNotBlank()) {
                                                        Log.d(
                                                                TAG,
                                                                "Successfully extracted text from image using OCR"
                                                        )
                                                        ToolResult(
                                                                toolName = tool.name,
                                                                success = true,
                                                                result =
                                                                        FileContentData(
                                                                                path = path,
                                                                                content = ocrText,
                                                                                size =
                                                                                        ocrText.length
                                                                                                .toLong()
                                                                        ),
                                                                error = ""
                                                        )
                                                } else {
                                                        Log.w(
                                                                TAG,
                                                                "OCR extraction returned empty text, returning no text detected message"
                                                        )
                                                        ToolResult(
                                                                toolName = tool.name,
                                                                success = true,
                                                                result =
                                                                        FileContentData(
                                                                                path = path,
                                                                                content =
                                                                                        "No text detected in image.",
                                                                                size =
                                                                                        "No text detected in image."
                                                                                                .length
                                                                                                .toLong()
                                                                        ),
                                                                error = ""
                                                        )
                                                }
                                        } else {
                                                Log.w(
                                                        TAG,
                                                        "Failed to decode image file, returning error message"
                                                )
                                                ToolResult(
                                                        toolName = tool.name,
                                                        success = true,
                                                        result =
                                                                FileContentData(
                                                                        path = path,
                                                                        content =
                                                                                "Failed to decode image file.",
                                                                        size =
                                                                                "Failed to decode image file."
                                                                                        .length
                                                                                        .toLong()
                                                                ),
                                                        error = ""
                                                )
                                        }
                                } catch (e: Exception) {
                                        Log.e(TAG, "Error during OCR text extraction", e)
                                        ToolResult(
                                                toolName = tool.name,
                                                success = true,
                                                result =
                                                        FileContentData(
                                                                path = path,
                                                                content =
                                                                        "Error extracting text from image: ${e.message}",
                                                                size =
                                                                        "Error extracting text from image: ${e.message}"
                                                                                .length.toLong()
                                                        ),
                                                error = ""
                                        )
                                }
                        }
                        else -> null
                }
        }

        /**
         * Reads the full content of a file as a new tool, handling different file types. This
         * function does not enforce a size limit.
         */
        open suspend fun readFileFull(tool: AITool): ToolResult {
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value

                // 如果是Linux环境，委托给LinuxFileSystemTools
                if (isLinuxEnvironment(environment)) {
                        return linuxTools.readFileFull(tool)
                }
                PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
                
                if (path.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Path parameter is required"
                        )
                }

                try {
                        val file = File(path)

                        if (!file.exists()) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = StringResultData(""),
                                        error = "File does not exist: $path"
                                )
                        }

                        if (!file.isFile) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = StringResultData(""),
                                        error = "Path is not a file: $path"
                                )
                        }

                        val fileExt = file.extension.lowercase()
                        val specialReadResult = handleSpecialFileRead(tool, path, fileExt)
                        if (specialReadResult != null) {
                                return specialReadResult
                        }

                        // Check if file is text-like by analyzing content
                        if (FileUtils.isTextLike(file)) {
                                val content = file.readText()
                                return ToolResult(
                                        toolName = tool.name,
                                        success = true,
                                        result =
                                                FileContentData(
                                                        path = path,
                                                        content = content,
                                                        size = file.length()
                                                ),
                                        error = ""
                                )
                        } else {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = StringResultData(""),
                                        error = "File does not appear to be a text file. Use specialized tools for binary files."
                                )
                        }
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

        /** Read file content, truncated to configured max size */
        open suspend fun readFile(tool: AITool): ToolResult {
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value

                // 如果是Linux环境，委托给LinuxFileSystemTools
                if (isLinuxEnvironment(environment)) {
                        return linuxTools.readFile(tool)
                }
                PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

                if (path.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Path parameter is required"
                        )
                }

                try {
                        // 从配置中获取最大文件大小
                        val maxFileSizeBytes = apiPreferences.getMaxFileSizeBytes()
                        
                        val file = File(path)
                        if (!file.exists() || !file.isFile) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = StringResultData(""),
                                        error = "Path is not a file: $path"
                                )
                        }

                        val fileExt = file.extension.lowercase()

                        // For special types, full read then truncate text is the only way.
                        if (fileExt in
                                        listOf(
                                                "doc",
                                                "docx",
                                                "pdf",
                                                "jpg",
                                                "jpeg",
                                                "png",
                                                "gif",
                                                "bmp"
                                        )
                        ) {
                                val fullResult = readFileFull(tool)
                                if (!fullResult.success) return fullResult

                                val contentData = fullResult.result as FileContentData
                                var content = contentData.content
                                val isTruncated = content.length > maxFileSizeBytes
                                if (isTruncated) {
                                        content = content.substring(0, maxFileSizeBytes)
                                }

                                var contentWithLineNumbers = addLineNumbers(content)
                                if (isTruncated) {
                                        contentWithLineNumbers += "\n\n... (file content truncated) ..."
                                }
                                return ToolResult(
                                        toolName = tool.name,
                                        success = true,
                                        result =
                                                FileContentData(
                                                        path = path,
                                                        content = contentWithLineNumbers,
                                                        size = contentWithLineNumbers.length.toLong()
                                                ),
                                        error = ""
                                )
                        }

                        // For text-based files, read only the beginning.
                        // Check if file is text-like by analyzing content
                        if (!FileUtils.isTextLike(file)) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = StringResultData(""),
                                        error =
                                                "File does not appear to be a text file. Use readFileFull tool for special file types."
                                )
                        }

                        val content =
                                file.bufferedReader().use {
                                        val buffer = CharArray(maxFileSizeBytes)
                                        val charsRead = it.read(buffer, 0, maxFileSizeBytes)
                                        if (charsRead > 0) String(buffer, 0, charsRead) else ""
                                }

                        val truncated = file.length() > maxFileSizeBytes
                        var finalContent = addLineNumbers(content)
                        if (truncated) {
                                finalContent += "\n\n... (file content truncated) ..."
                        }

                        return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        FileContentData(
                                                path = path,
                                                content = finalContent,
                                                size = finalContent.length.toLong()
                                        ),
                                error = ""
                        )
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

        /** 分段读取文件内容，每次读取指定部分（默认每部分从配置中获取） */
        open suspend fun readFilePart(tool: AITool): ToolResult {
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val partIndex =
                        tool.parameters.find { it.name == "partIndex" }?.value?.toIntOrNull() ?: 0

                // 如果是Linux环境，委托给LinuxFileSystemTools
                if (isLinuxEnvironment(environment)) {
                        return linuxTools.readFilePart(tool)
                }
                PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

                if (path.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Path parameter is required"
                        )
                }

                return try {
                        // 从配置中获取分段大小
                        val partSize = apiPreferences.getPartSize()
                        
                        val file = File(path)
                        if (!file.exists() || !file.isFile) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = StringResultData(""),
                                        error =
                                                "File does not exist or is not a regular file: $path"
                                )
                        }

                        // First, count total lines without loading the file into memory.
                        var totalLines = 0
                        file.bufferedReader().use { reader ->
                                while (reader.readLine() != null) {
                                        totalLines++
                                }
                        }

                        val totalParts = (totalLines + partSize - 1) / partSize
                        val validPartIndex =
                                partIndex.coerceIn(0, if (totalParts > 0) totalParts - 1 else 0)

                        val startLine = validPartIndex * partSize // 0-indexed
                        val endLine = minOf(startLine + partSize, totalLines) // exclusive

                        val partContent = StringBuilder()
                        if (totalLines > 0) {
                                var currentLine = 0
                                file.bufferedReader().useLines { lines ->
                                        lines.forEach { line ->
                                                if (currentLine >= endLine) {
                                                        return@useLines // early exit
                                                }
                                                if (currentLine >= startLine) {
                                                        partContent.append(line).append('\n')
                                                }
                                                currentLine++
                                        }
                                }
                                // Remove last newline if content is not empty
                                if (partContent.isNotEmpty()) {
                                        partContent.setLength(partContent.length - 1)
                                }
                        }

                        val contentWithLineNumbers = addLineNumbers(partContent.toString(), startLine, totalLines)

                        ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        FilePartContentData(
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
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Error reading file part: ${e.message}"
                        )
                }
        }

        /** Write content to a file */
        open suspend fun writeFile(tool: AITool): ToolResult {
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val content = tool.parameters.find { it.name == "content" }?.value ?: ""
                val append =
                        tool.parameters.find { it.name == "append" }?.value?.toBoolean() ?: false

                // 如果是Linux环境，委托给LinuxFileSystemTools
                if (isLinuxEnvironment(environment)) {
                        return linuxTools.writeFile(tool)
                }
                PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

                if (path.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "write",
                                                path = "",
                                                successful = false,
                                                details = "Path parameter is required"
                                        ),
                                error = "Path parameter is required"
                        )
                }

                return try {
                        val file = File(path)

                        // Create parent directories if needed
                        val parentDir = file.parentFile
                        if (parentDir != null && !parentDir.exists()) {
                                if (!parentDir.mkdirs()) {
                                        Log.w(
                                                TAG,
                                                "Failed to create parent directory: ${parentDir.absolutePath}"
                                        )
                                }
                        }

                        // Write content to file
                        if (append && file.exists()) {
                                file.appendText(content)
                        } else {
                                file.writeText(content)
                        }

                        // Verify write was successful
                        if (!file.exists()) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation =
                                                                if (append) "append" else "write",
                                                        path = path,
                                                        successful = false,
                                                        details =
                                                                "Write completed but file does not exist. Possible permission issue."
                                                ),
                                        error =
                                                "Write completed but file does not exist. Possible permission issue."
                                )
                        }

                        if (file.length() == 0L && content.isNotEmpty()) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation =
                                                                if (append) "append" else "write",
                                                        path = path,
                                                        successful = false,
                                                        details =
                                                                "File was created but appears to be empty. Possible write failure."
                                                ),
                                        error =
                                                "File was created but appears to be empty. Possible write failure."
                                )
                        }

                        val operation = if (append) "append" else "write"
                        val details =
                                if (append) "Content appended to $path"
                                else "Content written to $path"

                        return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        FileOperationData(
                                                operation = operation,
                                                path = path,
                                                successful = true,
                                                details = details
                                        ),
                                error = ""
                        )
                } catch (e: Exception) {
                        Log.e(TAG, "Error writing to file", e)

                        val errorMessage =
                                when {
                                        e is IOException ->
                                                "File I/O error: ${e.message}. Please check if the path has write permissions."
                                        e.message?.contains("permission", ignoreCase = true) ==
                                                true ->
                                                "Permission denied, cannot write to file: ${e.message}. Please check if the app has proper permissions."
                                        else -> "Error writing to file: ${e.message}"
                                }

                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = if (append) "append" else "write",
                                                path = path,
                                                successful = false,
                                                details = errorMessage
                                        ),
                                error = errorMessage
                        )
                }
        }

        /** Write base64 encoded content to a binary file */
        open suspend fun writeFileBinary(tool: AITool): ToolResult {
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val base64Content = tool.parameters.find { it.name == "base64Content" }?.value ?: ""

                // 如果是Linux环境，委托给LinuxFileSystemTools
                if (isLinuxEnvironment(environment)) {
                        return linuxTools.writeFileBinary(tool)
                }
                PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

                if (path.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
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
                                result =
                                        FileOperationData(
                                                operation = "write_binary",
                                                path = path,
                                                successful = false,
                                                details = "base64Content parameter is required"
                                        ),
                                error = "base64Content parameter is required"
                        )
                }

                return try {
                        val file = File(path)

                        // Create parent directories if needed
                        val parentDir = file.parentFile
                        if (parentDir != null && !parentDir.exists()) {
                                if (!parentDir.mkdirs()) {
                                        Log.w(
                                                TAG,
                                                "Failed to create parent directory: ${parentDir.absolutePath}"
                                        )
                                }
                        }

                        // Decode base64 and write bytes
                        val decodedBytes =
                                android.util.Base64.decode(base64Content, android.util.Base64.DEFAULT)
                        file.writeBytes(decodedBytes)

                        // Verify write was successful
                        if (!file.exists()) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation = "write_binary",
                                                        path = path,
                                                        successful = false,
                                                        details =
                                                                "Write completed but file does not exist. Possible permission issue."
                                                ),
                                        error =
                                                "Write completed but file does not exist. Possible permission issue."
                                )
                        }

                        if (file.length() == 0L && decodedBytes.isNotEmpty()) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation = "write_binary",
                                                        path = path,
                                                        successful = false,
                                                        details =
                                                                "File was created but appears to be empty. Possible write failure."
                                                ),
                                        error =
                                                "File was created but appears to be empty. Possible write failure."
                                )
                        }

                        val details = "Binary content written to $path"

                        return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        FileOperationData(
                                                operation = "write_binary",
                                                path = path,
                                                successful = true,
                                                details = details
                                        ),
                                error = ""
                        )
                } catch (e: Exception) {
                        Log.e(TAG, "Error writing binary file", e)
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "write_binary",
                                                path = path,
                                                successful = false,
                                                details = "Error writing binary file: ${e.message}"
                                        ),
                                error = "Error writing binary file: ${e.message}"
                        )
                }
        }

        /** Delete a file or directory */
        open suspend fun deleteFile(tool: AITool): ToolResult {
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val recursive =
                        tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: false

                // 如果是Linux环境，委托给LinuxFileSystemTools
                if (isLinuxEnvironment(environment)) {
                        return linuxTools.deleteFile(tool)
                }
                PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

                if (path.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "delete",
                                                path = "",
                                                successful = false,
                                                details = "Path parameter is required"
                                        ),
                                error = "Path parameter is required"
                        )
                }

                // Don't allow deleting system directories
                val restrictedPaths = listOf("/system", "/data", "/proc", "/dev")
                if (restrictedPaths.any { path.startsWith(it) }) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "delete",
                                                path = path,
                                                successful = false,
                                                details =
                                                        "Deleting system directories is not allowed"
                                        ),
                                error = "Deleting system directories is not allowed"
                        )
                }

                return try {
                        val file = File(path)

                        if (!file.exists()) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation = "delete",
                                                        path = path,
                                                        successful = false,
                                                        details =
                                                                "File or directory does not exist: $path"
                                                ),
                                        error = "File or directory does not exist: $path"
                                )
                        }

                        var success = false

                        if (file.isDirectory) {
                                if (recursive) {
                                        success = file.deleteRecursively()
                                } else {
                                        // Only delete if directory is empty
                                        val files = file.listFiles() ?: emptyArray()
                                        if (files.isEmpty()) {
                                                success = file.delete()
                                        } else {
                                                return ToolResult(
                                                        toolName = tool.name,
                                                        success = false,
                                                        result =
                                                                FileOperationData(
                                                                        operation = "delete",
                                                                        path = path,
                                                                        successful = false,
                                                                        details =
                                                                                "Directory is not empty and recursive flag is not set"
                                                                ),
                                                        error =
                                                                "Directory is not empty and recursive flag is not set"
                                                )
                                        }
                                }
                        } else {
                                success = file.delete()
                        }

                        if (success) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = true,
                                        result =
                                                FileOperationData(
                                                        operation = "delete",
                                                        path = path,
                                                        successful = true,
                                                        details = "Successfully deleted $path"
                                                ),
                                        error = ""
                                )
                        } else {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation = "delete",
                                                        path = path,
                                                        successful = false,
                                                        details =
                                                                "Failed to delete: permission denied or file in use"
                                                ),
                                        error = "Failed to delete: permission denied or file in use"
                                )
                        }
                } catch (e: Exception) {
                        Log.e(TAG, "Error deleting file/directory", e)
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "delete",
                                                path = path,
                                                successful = false,
                                                details =
                                                        "Error deleting file/directory: ${e.message}"
                                        ),
                                error = "Error deleting file/directory: ${e.message}"
                        )
                }
        }

        /** Check if a file or directory exists */
        open suspend fun fileExists(tool: AITool): ToolResult {
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value

                // 如果是Linux环境，委托给LinuxFileSystemTools
                if (isLinuxEnvironment(environment)) {
                        return linuxTools.fileExists(tool)
                }
                PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

                if (path.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Path parameter is required"
                        )
                }

                return try {
                        val file = File(path)
                        val exists = file.exists()

                        if (!exists) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = true,
                                        result = FileExistsData(path = path, exists = false),
                                        error = ""
                                )
                        }

                        val isDirectory = file.isDirectory
                        val size = file.length()

                        return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        FileExistsData(
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
                                result =
                                        FileExistsData(
                                                path = path,
                                                exists = false,
                                                isDirectory = false,
                                                size = 0
                                        ),
                                error = "Error checking file existence: ${e.message}"
                        )
                }
        }

        /** Move or rename a file or directory */
        open suspend fun moveFile(tool: AITool): ToolResult {
                val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value

                // 如果是Linux环境，委托给LinuxFileSystemTools
                if (isLinuxEnvironment(environment)) {
                        return linuxTools.moveFile(tool)
                }
                PathValidator.validateAndroidPath(sourcePath, tool.name, "source")?.let { return it }
                PathValidator.validateAndroidPath(destPath, tool.name, "destination")?.let { return it }

                if (sourcePath.isBlank() || destPath.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "move",
                                                path = sourcePath,
                                                successful = false,
                                                details =
                                                        "Source and destination parameters are required"
                                        ),
                                error = "Source and destination parameters are required"
                        )
                }

                // Don't allow moving system directories
                val restrictedPaths = listOf("/system", "/data", "/proc", "/dev")
                if (restrictedPaths.any { sourcePath.startsWith(it) }) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "move",
                                                path = sourcePath,
                                                successful = false,
                                                details = "Moving system directories is not allowed"
                                        ),
                                error = "Moving system directories is not allowed"
                        )
                }

                return try {
                        val sourceFile = File(sourcePath)
                        val destFile = File(destPath)

                        if (!sourceFile.exists()) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation = "move",
                                                        path = sourcePath,
                                                        successful = false,
                                                        details =
                                                                "Source file does not exist: $sourcePath"
                                                ),
                                        error = "Source file does not exist: $sourcePath"
                                )
                        }

                        // Create parent directory if needed
                        val destParent = destFile.parentFile
                        if (destParent != null && !destParent.exists()) {
                                destParent.mkdirs()
                        }

                        // Perform move operation
                        if (sourceFile.renameTo(destFile)) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = true,
                                        result =
                                                FileOperationData(
                                                        operation = "move",
                                                        path = sourcePath,
                                                        successful = true,
                                                        details =
                                                                "Successfully moved $sourcePath to $destPath"
                                                ),
                                        error = ""
                                )
                        } else {
                                // If simple rename fails, try copy and delete (could be across
                                // filesystems)
                                if (sourceFile.isDirectory) {
                                        // For directories, use directory copy utility
                                        val copySuccess = copyDirectory(sourceFile, destFile)
                                        if (copySuccess && sourceFile.deleteRecursively()) {
                                                return ToolResult(
                                                        toolName = tool.name,
                                                        success = true,
                                                        result =
                                                                FileOperationData(
                                                                        operation = "move",
                                                                        path = sourcePath,
                                                                        successful = true,
                                                                        details =
                                                                                "Successfully moved $sourcePath to $destPath (via copy and delete)"
                                                                ),
                                                        error = ""
                                                )
                                        }
                                } else {
                                        // For files, copy the content then delete original
                                        sourceFile.inputStream().use { input ->
                                                destFile.outputStream().use { output ->
                                                        input.copyTo(output)
                                                }
                                        }

                                        if (destFile.exists() &&
                                                        destFile.length() == sourceFile.length() &&
                                                        sourceFile.delete()
                                        ) {
                                                return ToolResult(
                                                        toolName = tool.name,
                                                        success = true,
                                                        result =
                                                                FileOperationData(
                                                                        operation = "move",
                                                                        path = sourcePath,
                                                                        successful = true,
                                                                        details =
                                                                                "Successfully moved $sourcePath to $destPath (via copy and delete)"
                                                                ),
                                                        error = ""
                                                )
                                        }
                                }

                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation = "move",
                                                        path = sourcePath,
                                                        successful = false,
                                                        details =
                                                                "Failed to move file: possibly a permissions issue or destination already exists"
                                                ),
                                        error =
                                                "Failed to move file: possibly a permissions issue or destination already exists"
                                )
                        }
                } catch (e: Exception) {
                        Log.e(TAG, "Error moving file", e)
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "move",
                                                path = sourcePath,
                                                successful = false,
                                                details = "Error moving file: ${e.message}"
                                        ),
                                error = "Error moving file: ${e.message}"
                        )
                }
        }

        /** Helper method to recursively copy a directory */
        private fun copyDirectory(sourceDir: File, destDir: File): Boolean {
                try {
                        if (!destDir.exists()) {
                                destDir.mkdirs()
                        }

                        sourceDir.listFiles()?.forEach { file ->
                                val destFile = File(destDir, file.name)
                                if (file.isDirectory) {
                                        copyDirectory(file, destFile)
                                } else {
                                        file.inputStream().use { input ->
                                                destFile.outputStream().use { output ->
                                                        input.copyTo(output)
                                                }
                                        }
                                }
                        }

                        return true
                } catch (e: Exception) {
                        Log.e(TAG, "Error copying directory", e)
                        return false
                }
        }

        /**
         * 跨环境复制文件或目录
         * 支持 Android <-> Linux 之间的文件复制
         */
        private suspend fun copyFileCrossEnvironment(
                toolName: String,
                sourcePath: String,
                destPath: String,
                sourceEnvironment: String,
                destEnvironment: String,
                recursive: Boolean
        ): ToolResult {
                // 目标路径保持原样，让 Linux 文件系统提供者处理 ~ 的展开
                val finalDestPath = destPath

                return try {
                        Log.d(TAG, "Cross-environment copy: $sourceEnvironment:$sourcePath -> $destEnvironment:$finalDestPath")

                        // 1. 检查源文件是否存在
                        val sourceExists = if (isLinuxEnvironment(sourceEnvironment)) {
                                linuxFileSystem.exists(sourcePath)
                        } else {
                                File(sourcePath).exists()
                        }

                        if (!sourceExists) {
                                return ToolResult(
                                        toolName = toolName,
                                        success = false,
                                        result = FileOperationData(operation = "copy", path = sourcePath, successful = false, details = "Failed to read source file"),
                                        error = "Failed to read source file"
                                )
                        }

                        // 2. 检查是否是目录
                        val isDirectory = if (isLinuxEnvironment(sourceEnvironment)) {
                                linuxFileSystem.isDirectory(sourcePath)
                        } else {
                                File(sourcePath).isDirectory
                        }

                        if (isDirectory) {
                                if (!recursive) {
                                        return ToolResult(
                                                toolName = toolName,
                                                success = false,
                                                result = FileOperationData(operation = "copy", path = sourcePath, successful = false, details = "Cannot copy directory without recursive flag"),
                                                error = "Cannot copy directory without recursive flag"
                                        )
                                }
                                
                                // 目录复制：递归复制所有文件
                                return copyDirectoryCrossEnvironment(
                                        toolName,
                                        sourcePath,
                                        finalDestPath,
                                        sourceEnvironment,
                                        destEnvironment
                                )
                        }

                        // 3. 获取文件大小
                        val fileSize = if (isLinuxEnvironment(sourceEnvironment)) {
                                linuxFileSystem.getFileSize(sourcePath)
                        } else {
                                File(sourcePath).length()
                        }

                        // 4. 统一分块传输（10MB 缓冲）
                        val BUFFER_SIZE = 10 * 1024 * 1024
                        var totalBytes = 0L
                        
                        if (isLinuxEnvironment(sourceEnvironment)) {
                                // 从 Linux 读取并写入
                                val content = linuxFileSystem.readFile(sourcePath) ?: return ToolResult(
                                        toolName = toolName,
                                        success = false,
                                        result = FileOperationData(operation = "copy", path = sourcePath, successful = false, details = "Failed to read source file"),
                                        error = "Failed to read source file"
                                )
                                val bytes = content.toByteArray(Charsets.UTF_8)
                                
                                if (isLinuxEnvironment(destEnvironment)) {
                                        val result = linuxFileSystem.writeFileBytes(finalDestPath, bytes)
                                        if (!result.success) {
                                                return ToolResult(toolName = toolName, success = false, result = FileOperationData(operation = "copy", path = sourcePath, successful = false, details = result.message), error = result.message)
                                        }
                                } else {
                                        File(finalDestPath).apply { parentFile?.mkdirs() }.writeBytes(bytes)
                                }
                                totalBytes = bytes.size.toLong()
                        } else {
                                // 从 Android 读取并写入
                                val sourceFile = File(sourcePath)
                                sourceFile.inputStream().use { input ->
                                        val buffer = ByteArray(BUFFER_SIZE)
                                        val outputStream = if (isLinuxEnvironment(destEnvironment)) {
                                                java.io.ByteArrayOutputStream()
                                        } else {
                                                File(finalDestPath).apply { parentFile?.mkdirs() }.outputStream()
                                        }
                                        
                                        outputStream.use { output ->
                                                var bytesRead: Int
                                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                                        output.write(buffer, 0, bytesRead)
                                                        totalBytes += bytesRead
                                                }
                                        }
                                        
                                        if (isLinuxEnvironment(destEnvironment)) {
                                                val bytes = (outputStream as java.io.ByteArrayOutputStream).toByteArray()
                                                val result = linuxFileSystem.writeFileBytes(finalDestPath, bytes)
                                                if (!result.success) {
                                                        return ToolResult(toolName = toolName, success = false, result = FileOperationData(operation = "copy", path = sourcePath, successful = false, details = result.message), error = result.message)
                                                }
                                        }
                                }
                        }

                        // 5. 验证成功
                        Log.d(TAG, "Successfully copied file cross-environment: $totalBytes bytes")
                        return ToolResult(
                                toolName = toolName,
                                success = true,
                                result = FileOperationData(
                                        operation = "copy",
                                        path = sourcePath,
                                        successful = true,
                                        details = "Successfully copied file from $sourceEnvironment:$sourcePath to $destEnvironment:$finalDestPath ($totalBytes bytes)"
                                ),
                                error = ""
                        )
                } catch (e: Exception) {
                        Log.e(TAG, "Error copying file cross-environment", e)
                        return ToolResult(
                                toolName = toolName,
                                success = false,
                                result = FileOperationData(
                                        operation = "copy",
                                        path = sourcePath,
                                        successful = false,
                                        details = "Error copying file cross-environment: ${e.message}"
                                ),
                                error = "Error copying file cross-environment: ${e.message}"
                        )
                }
        }

        /**
         * 跨环境递归复制目录
         */
        private suspend fun copyDirectoryCrossEnvironment(
                toolName: String,
                sourcePath: String,
                destPath: String,
                sourceEnvironment: String,
                destEnvironment: String
        ): ToolResult {
                // 目标路径保持原样，让 Linux 文件系统提供者处理 ~ 的展开
                val finalDestPath = destPath

                return try {
                        Log.d(TAG, "Cross-environment directory copy: $sourceEnvironment:$sourcePath -> $destEnvironment:$finalDestPath")

                        // 1. 创建目标目录
                        if (isLinuxEnvironment(destEnvironment)) {
                                val result = linuxFileSystem.createDirectory(finalDestPath, createParents = true)
                                if (!result.success) {
                                        return ToolResult(
                                                toolName = toolName,
                                                success = false,
                                                result = FileOperationData(
                                                        operation = "copy",
                                                        path = sourcePath,
                                                        successful = false,
                                                        details = "Failed to create destination directory: ${result.message}"
                                                ),
                                                error = "Failed to create destination directory: ${result.message}"
                                        )
                                }
                        } else {
                                val destDir = File(finalDestPath)
                                if (!destDir.exists()) {
                                        destDir.mkdirs()
                                }
                        }

                        // 2. 列出源目录内容
                        val entries = if (isLinuxEnvironment(sourceEnvironment)) {
                                linuxFileSystem.listDirectory(sourcePath)?.map { fileInfo ->
                                        Pair(fileInfo.name, fileInfo.isDirectory)
                                } ?: emptyList()
                        } else {
                                File(sourcePath).listFiles()?.map { file ->
                                        Pair(file.name, file.isDirectory)
                                } ?: emptyList()
                        }

                        // 3. 递归复制每个条目
                        var copiedFiles = 0
                        var copiedDirs = 0
                        for ((name, isDir) in entries) {
                                val srcFullPath = if (sourcePath.endsWith("/")) "$sourcePath$name" else "$sourcePath/$name"
                                val dstFullPath = if (finalDestPath.endsWith("/")) "$finalDestPath$name" else "$finalDestPath/$name"

                                if (isDir) {
                                        val result = copyDirectoryCrossEnvironment(
                                                toolName,
                                                srcFullPath,
                                                dstFullPath,
                                                sourceEnvironment,
                                                destEnvironment
                                        )
                                        if (result.success) {
                                                copiedDirs++
                                        } else {
                                                Log.w(TAG, "Failed to copy directory: $srcFullPath")
                                        }
                                } else {
                                        val result = copyFileCrossEnvironment(
                                                toolName,
                                                srcFullPath,
                                                dstFullPath,
                                                sourceEnvironment,
                                                destEnvironment,
                                                recursive = false
                                        )
                                        if (result.success) {
                                                copiedFiles++
                                        } else {
                                                Log.w(TAG, "Failed to copy file: $srcFullPath")
                                        }
                                }
                        }

                        Log.d(TAG, "Successfully copied directory: $copiedFiles files, $copiedDirs subdirectories")
                        return ToolResult(
                                toolName = toolName,
                                success = true,
                                result = FileOperationData(
                                        operation = "copy",
                                        path = sourcePath,
                                        successful = true,
                                        details = "Successfully copied directory from $sourceEnvironment:$sourcePath to $destEnvironment:$finalDestPath ($copiedFiles files, $copiedDirs subdirectories)"
                                ),
                                error = ""
                        )
                } catch (e: Exception) {
                        Log.e(TAG, "Error copying directory cross-environment", e)
                        return ToolResult(
                                toolName = toolName,
                                success = false,
                                result = FileOperationData(
                                        operation = "copy",
                                        path = sourcePath,
                                        successful = false,
                                        details = "Error copying directory cross-environment: ${e.message}"
                                ),
                                error = "Error copying directory cross-environment: ${e.message}"
                        )
                }
        }

        /** Copy a file or directory */
        open suspend fun copyFile(tool: AITool): ToolResult {
                val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val sourceEnvironment = tool.parameters.find { it.name == "source_environment" }?.value
                val destEnvironment = tool.parameters.find { it.name == "dest_environment" }?.value
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val recursive =
                        tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: true

                if (sourcePath.isBlank() || destPath.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "copy",
                                                path = sourcePath,
                                                successful = false,
                                                details =
                                                        "Source and destination parameters are required"
                                        ),
                                error = "Source and destination parameters are required"
                        )
                }

                // 确定源和目标环境
                val srcEnv = sourceEnvironment ?: environment ?: "android"
                val dstEnv = destEnvironment ?: environment ?: "android"

                // 检查是否是跨环境复制
                val isCrossEnvironment = srcEnv.lowercase() != dstEnv.lowercase()

                // 如果是跨环境复制，使用特殊处理
                if (isCrossEnvironment) {
                        return copyFileCrossEnvironment(
                                toolName = tool.name,
                                sourcePath = sourcePath,
                                destPath = destPath,
                                sourceEnvironment = srcEnv,
                                destEnvironment = dstEnv,
                                recursive = recursive
                        )
                }

                // 同环境复制 - 如果是Linux环境，委托给LinuxFileSystemTools
                if (isLinuxEnvironment(srcEnv)) {
                        return linuxTools.copyFile(
                                AITool(
                                        name = tool.name,
                                        parameters = listOf(
                                                ToolParameter("source", sourcePath),
                                                ToolParameter("destination", destPath),
                                                ToolParameter("recursive", recursive.toString())
                                        )
                                )
                        )
                }
                PathValidator.validateAndroidPath(sourcePath, tool.name, "source")?.let { return it }
                PathValidator.validateAndroidPath(destPath, tool.name, "destination")?.let { return it }

                // Android环境内复制
                return try {
                        val sourceFile = File(sourcePath)
                        val destFile = File(destPath)

                        if (!sourceFile.exists()) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation = "copy",
                                                        path = sourcePath,
                                                        successful = false,
                                                        details =
                                                                "Source path does not exist: $sourcePath"
                                                ),
                                        error = "Source path does not exist: $sourcePath"
                                )
                        }

                        // Create parent directory if needed
                        val destParent = destFile.parentFile
                        if (destParent != null && !destParent.exists()) {
                                destParent.mkdirs()
                        }

                        if (sourceFile.isDirectory) {
                                if (recursive) {
                                        val success = copyDirectory(sourceFile, destFile)
                                        if (success) {
                                                return ToolResult(
                                                        toolName = tool.name,
                                                        success = true,
                                                        result =
                                                                FileOperationData(
                                                                        operation = "copy",
                                                                        path = sourcePath,
                                                                        successful = true,
                                                                        details =
                                                                                "Successfully copied directory $sourcePath to $destPath"
                                                                ),
                                                        error = ""
                                                )
                                        } else {
                                                return ToolResult(
                                                        toolName = tool.name,
                                                        success = false,
                                                        result =
                                                                FileOperationData(
                                                                        operation = "copy",
                                                                        path = sourcePath,
                                                                        successful = false,
                                                                        details =
                                                                                "Failed to copy directory: possible permission issue"
                                                                ),
                                                        error =
                                                                "Failed to copy directory: possible permission issue"
                                                )
                                        }
                                } else {
                                        return ToolResult(
                                                toolName = tool.name,
                                                success = false,
                                                result =
                                                        FileOperationData(
                                                                operation = "copy",
                                                                path = sourcePath,
                                                                successful = false,
                                                                details =
                                                                        "Cannot copy directory without recursive flag"
                                                        ),
                                                error =
                                                        "Cannot copy directory without recursive flag"
                                        )
                                }
                        } else {
                                // Copy file
                                sourceFile.inputStream().use { input ->
                                        destFile.outputStream().use { output ->
                                                input.copyTo(output)
                                        }
                                }

                                // Verify copy was successful
                                if (destFile.exists() && destFile.length() == sourceFile.length()) {
                                        return ToolResult(
                                                toolName = tool.name,
                                                success = true,
                                                result =
                                                        FileOperationData(
                                                                operation = "copy",
                                                                path = sourcePath,
                                                                successful = true,
                                                                details =
                                                                        "Successfully copied file $sourcePath to $destPath"
                                                        ),
                                                error = ""
                                        )
                                } else {
                                        return ToolResult(
                                                toolName = tool.name,
                                                success = false,
                                                result =
                                                        FileOperationData(
                                                                operation = "copy",
                                                                path = sourcePath,
                                                                successful = false,
                                                                details =
                                                                        "Copy operation completed but verification failed"
                                                        ),
                                                error =
                                                        "Copy operation completed but verification failed"
                                        )
                                }
                        }
                } catch (e: Exception) {
                        Log.e(TAG, "Error copying file/directory", e)
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "copy",
                                                path = sourcePath,
                                                successful = false,
                                                details =
                                                        "Error copying file/directory: ${e.message}"
                                        ),
                                error = "Error copying file/directory: ${e.message}"
                        )
                }
        }

        /** Create a directory */
        open suspend fun makeDirectory(tool: AITool): ToolResult {
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val createParents =
                        tool.parameters.find { it.name == "create_parents" }?.value?.toBoolean()
                                ?: false

                // 如果是Linux环境，委托给LinuxFileSystemTools
                if (isLinuxEnvironment(environment)) {
                        return linuxTools.makeDirectory(tool)
                }
                PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

                if (path.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "mkdir",
                                                path = "",
                                                successful = false,
                                                details = "Path parameter is required"
                                        ),
                                error = "Path parameter is required"
                        )
                }

                return try {
                        val directory = File(path)

                        // Check if directory already exists
                        if (directory.exists()) {
                                if (directory.isDirectory) {
                                        return ToolResult(
                                                toolName = tool.name,
                                                success = true,
                                                result =
                                                        FileOperationData(
                                                                operation = "mkdir",
                                                                path = path,
                                                                successful = true,
                                                                details =
                                                                        "Directory already exists: $path"
                                                        ),
                                                error = ""
                                        )
                                } else {
                                        return ToolResult(
                                                toolName = tool.name,
                                                success = false,
                                                result =
                                                        FileOperationData(
                                                                operation = "mkdir",
                                                                path = path,
                                                                successful = false,
                                                                details =
                                                                        "Path exists but is not a directory: $path"
                                                        ),
                                                error = "Path exists but is not a directory: $path"
                                        )
                                }
                        }

                        // Create directory
                        val success =
                                if (createParents) {
                                        directory.mkdirs()
                                } else {
                                        directory.mkdir()
                                }

                        if (success) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = true,
                                        result =
                                                FileOperationData(
                                                        operation = "mkdir",
                                                        path = path,
                                                        successful = true,
                                                        details =
                                                                "Successfully created directory $path"
                                                ),
                                        error = ""
                                )
                        } else {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation = "mkdir",
                                                        path = path,
                                                        successful = false,
                                                        details =
                                                                "Failed to create directory: parent directory may not exist or permission denied"
                                                ),
                                        error =
                                                "Failed to create directory: parent directory may not exist or permission denied"
                                )
                        }
                } catch (e: Exception) {
                        Log.e(TAG, "Error creating directory", e)
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "mkdir",
                                                path = path,
                                                successful = false,
                                                details = "Error creating directory: ${e.message}"
                                        ),
                                error = "Error creating directory: ${e.message}"
                        )
                }
        }

        /** Search for files matching a pattern */
        open suspend fun findFiles(tool: AITool): ToolResult {
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""

                // 如果是Linux环境，委托给LinuxFileSystemTools
                if (isLinuxEnvironment(environment)) {
                        return linuxTools.findFiles(tool)
                }
                PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

                if (path.isBlank() || pattern.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FindFilesResultData(
                                                path = path,
                                                pattern = pattern,
                                                files = emptyList()
                                        ),
                                error = "Path and pattern parameters are required"
                        )
                }

                return try {
                        val rootDir = File(path)

                        if (!rootDir.exists() || !rootDir.isDirectory) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FindFilesResultData(
                                                        path = path,
                                                        pattern = pattern,
                                                        files = emptyList()
                                                ),
                                        error = "Path does not exist or is not a directory: $path"
                                )
                        }

                        // Get search options
                        val usePathPattern =
                                tool.parameters
                                        .find { it.name == "use_path_pattern" }
                                        ?.value
                                        ?.toBoolean()
                                        ?: false
                        val caseInsensitive =
                                tool.parameters
                                        .find { it.name == "case_insensitive" }
                                        ?.value
                                        ?.toBoolean()
                                        ?: false
                        val maxDepth =
                                tool.parameters
                                        .find { it.name == "max_depth" }
                                        ?.value
                                        ?.toIntOrNull()
                                        ?: -1

                        // Convert glob pattern to regex
                        val regex = globToRegex(pattern, caseInsensitive)

                        // Recursively find matching files
                        val matchingFiles = mutableListOf<String>()
                        findMatchingFiles(
                                rootDir,
                                regex,
                                matchingFiles,
                                usePathPattern,
                                maxDepth,
                                0,
                                rootDir.absolutePath
                        )

                        return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        FindFilesResultData(
                                                path = path,
                                                pattern = pattern,
                                                files = matchingFiles
                                        ),
                                error = ""
                        )
                } catch (e: Exception) {
                        Log.e(TAG, "Error searching for files", e)
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FindFilesResultData(
                                                path = path,
                                                pattern = pattern,
                                                files = emptyList()
                                        ),
                                error = "Error searching for files: ${e.message}"
                        )
                }
        }

        /** Helper method to convert glob pattern to regex */
        private fun globToRegex(glob: String, caseInsensitive: Boolean): Regex {
                val regex = StringBuilder("^")

                for (i in glob.indices) {
                        val c = glob[i]
                        when (c) {
                                '*' -> regex.append(".*")
                                '?' -> regex.append(".")
                                '.' -> regex.append("\\.")
                                '\\' -> regex.append("\\\\")
                                '[' -> regex.append("[")
                                ']' -> regex.append("]")
                                '(' -> regex.append("\\(")
                                ')' -> regex.append("\\)")
                                '{' -> regex.append("(")
                                '}' -> regex.append(")")
                                ',' -> regex.append("|")
                                else -> regex.append(c)
                        }
                }

                regex.append("$")

                return if (caseInsensitive) {
                        Regex(regex.toString(), RegexOption.IGNORE_CASE)
                } else {
                        Regex(regex.toString())
                }
        }

        /** Helper method to recursively find files matching a pattern */
        private fun findMatchingFiles(
                dir: File,
                regex: Regex,
                results: MutableList<String>,
                usePathPattern: Boolean,
                maxDepth: Int,
                currentDepth: Int,
                rootPath: String
        ) {
                if (maxDepth >= 0 && currentDepth > maxDepth) {
                        return
                }

                val files = dir.listFiles() ?: return

                for (file in files) {
                        val relativePath = file.absolutePath.substring(rootPath.length + 1)

                        val testString = if (usePathPattern) relativePath else file.name

                        if (regex.matches(testString)) {
                                results.add(file.absolutePath)
                        }

                        if (file.isDirectory) {
                                findMatchingFiles(
                                        file,
                                        regex,
                                        results,
                                        usePathPattern,
                                        maxDepth,
                                        currentDepth + 1,
                                        rootPath
                                )
                        }
                }
        }

        /** Get file information */
        open suspend fun fileInfo(tool: AITool): ToolResult {
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value

                // 如果是Linux环境，委托给LinuxFileSystemTools
                if (isLinuxEnvironment(environment)) {
                        return linuxTools.fileInfo(tool)
                }
                PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

                if (path.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileInfoData(
                                                path = "",
                                                exists = false,
                                                fileType = "",
                                                size = 0,
                                                permissions = "",
                                                owner = "",
                                                group = "",
                                                lastModified = "",
                                                rawStatOutput = ""
                                        ),
                                error = "Path parameter is required"
                        )
                }

                return try {
                        val file = File(path)

                        if (!file.exists()) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileInfoData(
                                                        path = path,
                                                        exists = false,
                                                        fileType = "",
                                                        size = 0,
                                                        permissions = "",
                                                        owner = "",
                                                        group = "",
                                                        lastModified = "",
                                                        rawStatOutput = ""
                                                ),
                                        error = "File or directory does not exist: $path"
                                )
                        }

                        // Get file type
                        val fileType =
                                when {
                                        file.isDirectory -> "directory"
                                        file.isFile -> "file"
                                        else -> "other"
                                }

                        // Get permissions
                        val permissions = getFilePermissions(file)

                        // Owner and group info are not easily available in Java
                        val owner = System.getProperty("user.name") ?: ""
                        val group = ""

                        // Last modified time
                        val lastModified =
                                SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                                        .format(Date(file.lastModified()))

                        // Size
                        val size = if (file.isFile) file.length() else 0

                        // Collect all file info into a raw string
                        val rawInfo = StringBuilder()
                        rawInfo.append("File: $path\n")
                        rawInfo.append("Size: $size bytes\n")
                        rawInfo.append("Type: $fileType\n")
                        rawInfo.append("Permissions: $permissions\n")
                        rawInfo.append("Last Modified: $lastModified\n")
                        rawInfo.append("Owner: $owner\n")
                        if (file.canRead()) rawInfo.append("Access: Readable\n")
                        if (file.canWrite()) rawInfo.append("Access: Writable\n")
                        if (file.canExecute()) rawInfo.append("Access: Executable\n")
                        if (file.isHidden()) rawInfo.append("Hidden: Yes\n")

                        return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        FileInfoData(
                                                path = path,
                                                exists = true,
                                                fileType = fileType,
                                                size = size,
                                                permissions = permissions,
                                                owner = owner,
                                                group = group,
                                                lastModified = lastModified,
                                                rawStatOutput = rawInfo.toString()
                                        ),
                                error = ""
                        )
                } catch (e: Exception) {
                        Log.e(TAG, "Error getting file information", e)
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileInfoData(
                                                path = path,
                                                exists = false,
                                                fileType = "",
                                                size = 0,
                                                permissions = "",
                                                owner = "",
                                                group = "",
                                                lastModified = "",
                                                rawStatOutput = ""
                                        ),
                                error = "Error getting file information: ${e.message}"
                        )
                }
        }

        /** Zip files or directories */
        open suspend fun zipFiles(tool: AITool): ToolResult {
                val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
                val zipPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                PathValidator.validateAndroidPath(sourcePath, tool.name, "source")?.let { return it }
                PathValidator.validateAndroidPath(zipPath, tool.name, "destination")?.let { return it }

                val actualSourcePath = PathMapper.resolvePath(context, sourcePath, environment)
                val actualZipPath = PathMapper.resolvePath(context, zipPath, environment)

                if (sourcePath.isBlank() || zipPath.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Source and destination parameters are required"
                        )
                }

                return try {
                        val sourceFile = File(actualSourcePath)
                        val destZipFile = File(actualZipPath)

                        if (!sourceFile.exists()) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = StringResultData(""),
                                        error =
                                                "Source file or directory does not exist: $sourcePath"
                                )
                        }

                        // Create parent directory for zip file if needed
                        val zipDir = destZipFile.parentFile
                        if (zipDir != null && !zipDir.exists()) {
                                zipDir.mkdirs()
                        }

                        // Initialize buffer for file operations
                        val buffer = ByteArray(1024)

                        ZipOutputStream(BufferedOutputStream(FileOutputStream(destZipFile))).use {
                                zos ->
                                if (sourceFile.isDirectory) {
                                        // For directories, add all files recursively
                                        addDirectoryToZip(sourceFile, sourceFile.name, zos)
                                } else {
                                        // For a single file, add it directly
                                        val entryName = sourceFile.name
                                        zos.putNextEntry(ZipEntry(entryName))

                                        FileInputStream(sourceFile).use { fis ->
                                                BufferedInputStream(fis).use { bis ->
                                                        var len: Int
                                                        while (bis.read(buffer).also { len = it } >
                                                                0) {
                                                                zos.write(buffer, 0, len)
                                                        }
                                                }
                                        }

                                        zos.closeEntry()
                                }
                        }

                        if (destZipFile.exists()) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = true,
                                        result =
                                                FileOperationData(
                                                        operation = "zip",
                                                        path = sourcePath,
                                                        successful = true,
                                                        details =
                                                                "Successfully compressed $sourcePath to $zipPath"
                                                ),
                                        error = ""
                                )
                        } else {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = StringResultData(""),
                                        error = "Failed to create zip file"
                                )
                        }
                } catch (e: Exception) {
                        Log.e(TAG, "Error compressing files", e)
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Error compressing files: ${e.message}"
                        )
                }
        }

        /** Helper method to add directory contents to zip */
        private fun addDirectoryToZip(dir: File, baseName: String, zos: ZipOutputStream) {
                val buffer = ByteArray(1024)
                val files = dir.listFiles() ?: return

                for (file in files) {
                        if (file.isDirectory) {
                                addDirectoryToZip(file, "$baseName/${file.name}", zos)
                                continue
                        }

                        val entryName = "$baseName/${file.name}"
                        zos.putNextEntry(ZipEntry(entryName))

                        FileInputStream(file).use { fis ->
                                BufferedInputStream(fis).use { bis ->
                                        var len: Int
                                        while (bis.read(buffer).also { len = it } > 0) {
                                                zos.write(buffer, 0, len)
                                        }
                                }
                        }

                        zos.closeEntry()
                }
        }

        /** Unzip a zip file */
        open suspend fun unzipFiles(tool: AITool): ToolResult {
                val zipPath = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                PathValidator.validateAndroidPath(zipPath, tool.name, "source")?.let { return it }
                PathValidator.validateAndroidPath(destPath, tool.name, "destination")?.let { return it }

                val actualZipPath = PathMapper.resolvePath(context, zipPath, environment)
                val actualDestPath = PathMapper.resolvePath(context, destPath, environment)

                if (zipPath.isBlank() || destPath.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Source and destination parameters are required"
                        )
                }

                return try {
                        val zipFile = File(actualZipPath)
                        val destDir = File(actualDestPath)

                        if (!zipFile.exists()) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = StringResultData(""),
                                        error = "Zip file does not exist: $zipPath"
                                )
                        }

                        if (!zipFile.isFile) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = StringResultData(""),
                                        error = "Source path is not a file: $zipPath"
                                )
                        }

                        // Create destination directory if needed
                        if (!destDir.exists()) {
                                destDir.mkdirs()
                        }

                        val buffer = ByteArray(1024)

                        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                                var zipEntry: ZipEntry? = zis.nextEntry

                                while (zipEntry != null) {
                                        val fileName = zipEntry.name
                                        val newFile = File(destDir, fileName)

                                        // Create parent directories if needed
                                        val parentDir = newFile.parentFile
                                        if (parentDir != null && !parentDir.exists()) {
                                                parentDir.mkdirs()
                                        }

                                        if (zipEntry.isDirectory) {
                                                // Create directory if it doesn't exist
                                                if (!newFile.exists()) {
                                                        newFile.mkdirs()
                                                }
                                        } else {
                                                // Extract file
                                                FileOutputStream(newFile).use { fos ->
                                                        BufferedOutputStream(fos).use { bos ->
                                                                var len: Int
                                                                while (zis.read(buffer).also {
                                                                        len = it
                                                                } > 0) {
                                                                        bos.write(buffer, 0, len)
                                                                }
                                                        }
                                                }
                                        }

                                        zis.closeEntry()
                                        zipEntry = zis.nextEntry
                                }
                        }

                        return ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        FileOperationData(
                                                operation = "unzip",
                                                path = zipPath,
                                                successful = true,
                                                details =
                                                        "Successfully extracted $zipPath to $destPath"
                                        ),
                                error = ""
                        )
                } catch (e: Exception) {
                        Log.e(TAG, "Error extracting zip file", e)
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Error extracting zip file: ${e.message}"
                        )
                }
        }

        /**
         * 智能应用文件绑定，将AI生成的代码与原始文件内容智能合并 该工具会读取原始文件内容，应用AI生成的代码（通常包含//existing code标记）， 然后将合并后的内容写回文件
         */
        open fun applyFile(tool: AITool): Flow<ToolResult> = flow {
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val aiGeneratedCode = tool.parameters.find { it.name == "content" }?.value ?: ""
                PathValidator.validateAndroidPath(path, tool.name)?.let {
                        emit(it)
                        return@flow
                }

                if (path.isBlank()) {
                        emit(
                                ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation = "apply",
                                                        path = "",
                                                        successful = false,
                                                        details = "Path parameter is required"
                                                ),
                                        error = "Path parameter is required"
                                )
                        )
                        return@flow
                }

                if (aiGeneratedCode.isBlank()) {
                        emit(
                                ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation = "apply",
                                                        path = path,
                                                        successful = false,
                                                        details = "Content parameter is required"
                                                ),
                                        error = "Content parameter is required"
                                )
                        )
                        return@flow
                }

                // 1. 检查文件是否存在
                val fileExistsResult =
                        fileExists(
                                AITool(name = "file_exists", parameters = listOf(
                                        ToolParameter("path", path),
                                        ToolParameter("environment", environment ?: "")
                                ))
                        )

                if (!fileExistsResult.success ||
                                !(fileExistsResult.result as FileExistsData).exists
                ) {
                        // 文件不存在，直接创建并写入内容
                        Log.d(TAG, "File does not exist. Creating new file '$path'...")
                        emit(
                            ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = StringResultData("File does not exist. Creating new file '$path'...")
                            )
                        )

                        val writeResult = writeFile(
                            AITool(
                                name = "write_file",
                                parameters = listOf(
                                    ToolParameter("path", path),
                                    ToolParameter("content", aiGeneratedCode),
                                    ToolParameter("environment", environment ?: "")
                                )
                            )
                        )

                        val diffContent = FileBindingService(context).generateUnifiedDiff("", aiGeneratedCode)

                        if (writeResult.success) {
                            emit(
                                ToolResult(
                                    toolName = tool.name,
                                    success = true,
                                    result = FileApplyResultData(
                                        operation = FileOperationData(
                                            operation = "create",
                                            path = path,
                                            successful = true,
                                            details = "Successfully created new file: $path"
                                        ),
                                        aiDiffInstructions = "",
                                        syntaxCheckResult = null,
                                        diffContent = diffContent
                                    )
                                )
                            )
                        } else {
                            emit(
                                ToolResult(
                                    toolName = tool.name,
                                    success = false,
                                    result = FileOperationData(
                                        operation = "create",
                                        path = path,
                                        successful = false,
                                        details = "Failed to create new file: ${writeResult.error}"
                                    ),
                                    error = "Failed to create new file: ${writeResult.error}"
                                )
                            )
                        }
                        return@flow
                }

                // 2. 读取原始文件内容
                val readResult =
                        readFileFull(AITool(name = "read_file_full", parameters = listOf(
                                ToolParameter("path", path),
                                ToolParameter("environment", environment ?: "")
                        )))

                if (!readResult.success) {
                        emit(
                                ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation = "apply",
                                                        path = path,
                                                        successful = false,
                                                        details =
                                                                "Failed to read original file: ${readResult.error}"
                                                ),
                                        error = "Failed to read original file: ${readResult.error}"
                                )
                        )
                        return@flow
                }

                emit(
                        ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        StringResultData(
                                                "Read original file. Now merging changes and writing back..."
                                        )
                        )
                )

                // 提取原始文件内容
                val originalContent = (readResult.result as? FileContentData)?.content ?: ""

                // 2. 使用EnhancedAIService处理文件绑定
                val enhancedAIService = EnhancedAIService.getInstance(context)
                val bindingResult = enhancedAIService.applyFileBinding(originalContent, aiGeneratedCode)
                val mergedContent = bindingResult.first
                val aiInstructions = bindingResult.second

                // 检查文件绑定是否返回错误
                if (aiInstructions.startsWith("Error", ignoreCase = true)) {
                        Log.e(TAG, "File binding failed: $aiInstructions")
                        emit(
                                ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation = "apply",
                                                        path = path,
                                                        successful = false,
                                                        details = "File binding failed: $aiInstructions"
                                                ),
                                        error = aiInstructions
                                )
                        )
                        return@flow
                }

                // 3. 将合并后的内容写回文件
                val writeResult =
                        writeFile(
                                AITool(
                                        name = "write_file",
                                        parameters =
                                                listOf(
                                                        ToolParameter("path", path),
                                                        ToolParameter("content", mergedContent),
                                                        ToolParameter("append", "false"),
                                                        ToolParameter("environment", environment ?: "")
                                                )
                                )
                        )

                if (!writeResult.success) {
                        emit(
                                ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation = "apply",
                                                        path = path,
                                                        successful = false,
                                                        details =
                                                                "Failed to write merged content: ${writeResult.error}"
                                                ),
                                        error = "Failed to write merged content: ${writeResult.error}"
                                )
                        )
                        return@flow
                }

                // 成功完成
                val operationData =
                        FileOperationData(
                                operation = "apply",
                                path = path,
                                successful = true,
                                details = "Successfully applied AI code to file: $path"
                        )

                // 执行语法检查
                val syntaxCheckResult = performSyntaxCheck(path, mergedContent)
                val diffContent = FileBindingService(context).generateUnifiedDiff(originalContent, mergedContent)

                emit(
                        ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        FileApplyResultData(
                                                operation = operationData,
                                                aiDiffInstructions = aiInstructions,
                                                syntaxCheckResult = syntaxCheckResult,
                                                diffContent = diffContent
                                        ),
                                error = ""
                        )
                )
        }
                .catch { e ->
                        Log.e(TAG, "Error applying file binding", e)
                        val path = tool.parameters.find { it.name == "path" }?.value ?: "unknown"
                        emit(
                                ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation = "apply",
                                                        path = path,
                                                        successful = false,
                                                        details = "Error applying file binding: ${e.message}"
                                                ),
                                        error = "Error applying file binding: ${e.message}"
                                )
                        )
                }

        /** Download file from URL */
        open suspend fun downloadFile(tool: AITool): ToolResult {
                val url = tool.parameters.find { it.name == "url" }?.value ?: ""
                val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                PathValidator.validateAndroidPath(destPath, tool.name, "destination")?.let { return it }

                val actualDestPath = PathMapper.resolvePath(context, destPath, environment)

                if (url.isBlank() || destPath.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "download",
                                                path = destPath,
                                                successful = false,
                                                details =
                                                        "URL and destination parameters are required"
                                        ),
                                error = "URL and destination parameters are required"
                        )
                }

                // Validate URL format
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "download",
                                                path = destPath,
                                                successful = false,
                                                details = "URL must start with http:// or https://"
                                        ),
                                error = "URL must start with http:// or https://"
                        )
                }

                return try {
                        val destFile = File(actualDestPath)

                        // Create parent directory if needed
                        val destParent = destFile.parentFile
                        if (destParent != null && !destParent.exists()) {
                                destParent.mkdirs()
                        }

                        // Open connection to URL
                        val connection = URL(url).openConnection() as HttpURLConnection
                        connection.connectTimeout = 15000
                        connection.readTimeout = 30000
                        connection.instanceFollowRedirects = true

                        val responseCode = connection.responseCode
                        if (responseCode != HttpURLConnection.HTTP_OK) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation = "download",
                                                        path = destPath,
                                                        successful = false,
                                                        details =
                                                                "Failed to download: HTTP error code $responseCode"
                                                ),
                                        error = "Failed to download: HTTP error code $responseCode"
                                )
                        }

                        // Get file size for progress tracking
                        val fileSize = connection.contentLength

                        // Download the file
                        connection.inputStream.use { input ->
                                FileOutputStream(destFile).use { output ->
                                        val buffer = ByteArray(4096)
                                        var bytesRead: Int
                                        var totalBytesRead = 0L

                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                                output.write(buffer, 0, bytesRead)
                                                totalBytesRead += bytesRead
                                        }
                                }
                        }

                        // Verify download was successful
                        if (destFile.exists()) {
                                val fileSize = destFile.length()
                                val formattedSize =
                                        when {
                                                fileSize > 1024 * 1024 ->
                                                        String.format(
                                                                "%.2f MB",
                                                                fileSize / (1024.0 * 1024.0)
                                                        )
                                                fileSize > 1024 ->
                                                        String.format("%.2f KB", fileSize / 1024.0)
                                                else -> "$fileSize bytes"
                                        }

                                return ToolResult(
                                        toolName = tool.name,
                                        success = true,
                                        result =
                                                FileOperationData(
                                                        operation = "download",
                                                        path = destPath,
                                                        successful = true,
                                                        details =
                                                                "File downloaded successfully: $url -> $destPath (file size: $formattedSize)"
                                                ),
                                        error = ""
                                )
                        } else {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        operation = "download",
                                                        path = destPath,
                                                        successful = false,
                                                        details =
                                                                "Download completed but file was not created"
                                                ),
                                        error = "Download completed but file was not created"
                                )
                        }
                } catch (e: Exception) {
                        Log.e(TAG, "Error downloading file", e)
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "download",
                                                path = destPath,
                                                successful = false,
                                                details = "Error downloading file: ${e.message}"
                                        ),
                                error = "Error downloading file: ${e.message}"
                        )
                }
        }

        /** Open file with system default app */
        open suspend fun openFile(tool: AITool): ToolResult {
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value

                // 如果是Linux环境，委托给LinuxFileSystemTools
                if (isLinuxEnvironment(environment)) {
                        return linuxTools.openFile(tool)
                }
                PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

                if (path.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "open",
                                                path = "",
                                                successful = false,
                                                details = "Path parameter is required"
                                        ),
                                error = "Path parameter is required"
                        )
                }

                return try {
                        val file = File(path)
                        if (!file.exists()) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        "open",
                                                        path,
                                                        false,
                                                        "File does not exist: $path"
                                                ),
                                        error = "File does not exist: $path"
                                )
                        }

                        val authority = "${context.packageName}.fileprovider"
                        val uri = FileProvider.getUriForFile(context, authority, file)
                        val mimeType =
                                MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
                                        ?: "*/*"

                        val intent =
                                Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }

                        context.startActivity(intent)

                        ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        FileOperationData(
                                                "open",
                                                path,
                                                true,
                                                "Request to open file sent to system: $path"
                                        ),
                                error = ""
                        )
                } catch (e: ActivityNotFoundException) {
                        Log.e(TAG, "No activity found to handle opening file: $path", e)
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                "open",
                                                path,
                                                false,
                                                "No application found to open this file type."
                                        ),
                                error = "No application found to open this file type."
                        )
                } catch (e: Exception) {
                        Log.e(TAG, "Error opening file", e)
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                "open",
                                                path,
                                                false,
                                                "Error opening file: ${e.message}"
                                        ),
                                error = "Error opening file: ${e.message}"
                        )
                }
        }

        /** 
         * Grep代码搜索工具 - 在指定目录中搜索包含指定模式的代码
         * 依赖 findFiles 和 readFileFull 函数，不直接使用 File 类
         */
        open suspend fun grepCode(tool: AITool): ToolResult {
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""
                val filePattern = tool.parameters.find { it.name == "file_pattern" }?.value ?: "*"
                val caseInsensitive = tool.parameters.find { it.name == "case_insensitive" }?.value?.toBoolean() ?: false
                val contextLines = tool.parameters.find { it.name == "context_lines" }?.value?.toIntOrNull() ?: 3
                val maxResults = tool.parameters.find { it.name == "max_results" }?.value?.toIntOrNull() ?: 100
                
                // 如果是Linux环境，委托给LinuxFileSystemTools
                if (isLinuxEnvironment(environment)) {
                        return linuxTools.grepCode(tool)
                }
                PathValidator.validateAndroidPath(path, tool.name)?.let { return it }
                
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
                
                return try {
                        // 1. 使用 findFiles 查找所有匹配的文件
                        val findFilesResult = findFiles(
                                AITool(
                                        name = "find_files",
                                        parameters = listOf(
                                                ToolParameter("path", path),
                                                ToolParameter("pattern", filePattern),
                                                ToolParameter("use_path_pattern", "false"),
                                                ToolParameter("case_insensitive", "false"),
                                                ToolParameter("environment", environment ?: "")
                                        )
                                )
                        )
                        
                        if (!findFilesResult.success) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = StringResultData(""),
                                        error = "Failed to find files: ${findFilesResult.error}"
                                )
                        }
                        
                        val foundFiles = (findFilesResult.result as FindFilesResultData).files
                        
                        if (foundFiles.isEmpty()) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = true,
                                        result = GrepResultData(
                                                searchPath = path,
                                                pattern = pattern,
                                                matches = emptyList(),
                                                totalMatches = 0,
                                                filesSearched = 0
                                        ),
                                        error = ""
                                )
                        }
                        
                        // 2. 创建正则表达式用于匹配
                        val regex = if (caseInsensitive) {
                                Regex(pattern, RegexOption.IGNORE_CASE)
                        } else {
                                Regex(pattern)
                        }
                        
                        // 3. 遍历每个文件，搜索匹配的行
                        val fileMatches = mutableListOf<GrepResultData.FileMatch>()
                        var totalMatches = 0
                        var filesSearched = 0
                        
                        for (filePath in foundFiles) {
                                if (totalMatches >= maxResults) {
                                        break
                                }
                                
                                filesSearched++
                                
                                // 读取文件内容
                                val readResult = readFileFull(
                                        AITool(
                                                name = "read_file_full",
                                                parameters = listOf(ToolParameter("path", filePath))
                                        )
                                )
                                
                                if (!readResult.success) {
                                        // 如果读取失败（可能是二进制文件或权限问题），跳过该文件
                                        continue
                                }
                                
                                val fileContent = (readResult.result as FileContentData).content
                                val lines = fileContent.lines()
                                val lineMatches = mutableListOf<GrepResultData.LineMatch>()
                                
                                // 搜索每一行
                                lines.forEachIndexed { index, line ->
                                        if (regex.containsMatchIn(line)) {
                                                val lineNumber = index + 1
                                                
                                                // 获取上下文（前后几行）
                                                val context = if (contextLines > 0) {
                                                        val startIdx = maxOf(0, index - contextLines)
                                                        val endIdx = minOf(lines.size - 1, index + contextLines)
                                                        lines.subList(startIdx, endIdx + 1).joinToString("\n")
                                                } else {
                                                        null
                                                }
                                                
                                                lineMatches.add(
                                                        GrepResultData.LineMatch(
                                                                lineNumber = lineNumber,
                                                                lineContent = line.trim(),
                                                                matchContext = context
                                                        )
                                                )
                                                
                                                totalMatches++
                                                
                                                if (totalMatches >= maxResults) {
                                                        return@forEachIndexed
                                                }
                                        }
                                }
                                
                                // 如果该文件有匹配，添加到结果中
                                if (lineMatches.isNotEmpty()) {
                                        fileMatches.add(
                                                GrepResultData.FileMatch(
                                                        filePath = filePath,
                                                        lineMatches = lineMatches
                                                )
                                        )
                                }
                        }
                        
                        // 4. 返回结果
                        ToolResult(
                                toolName = tool.name,
                                success = true,
                                result = GrepResultData(
                                        searchPath = path,
                                        pattern = pattern,
                                        matches = fileMatches.take(20), // 最多显示20个文件
                                        totalMatches = totalMatches,
                                        filesSearched = filesSearched
                                ),
                                error = ""
                        )
                        
                } catch (e: Exception) {
                        Log.e(TAG, "Error performing grep search", e)
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Error performing grep search: ${e.message}"
                        )
                }
        }

        /**
         * 执行语法检查
         * @param filePath 文件路径
         * @param content 文件内容
         * @return 语法检查结果的字符串表示，如果不支持该文件类型则返回null
         */
        protected fun performSyntaxCheck(filePath: String, content: String): String? {
                return try {
                        val result = SyntaxCheckUtil.checkSyntax(filePath, content)
                        result?.toString()
                } catch (e: Exception) {
                        Log.e(TAG, "Error performing syntax check", e)
                        "Syntax check failed: ${e.message}"
                }
        }

        /** Share file via system share dialog */
        open suspend fun shareFile(tool: AITool): ToolResult {
                val path = tool.parameters.find { it.name == "path" }?.value ?: ""
                val environment = tool.parameters.find { it.name == "environment" }?.value
                val title = tool.parameters.find { it.name == "title" }?.value ?: "Share File"

                // 如果是Linux环境，委托给LinuxFileSystemTools
                if (isLinuxEnvironment(environment)) {
                        return linuxTools.shareFile(tool)
                }
                PathValidator.validateAndroidPath(path, tool.name)?.let { return it }

                if (path.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                operation = "share",
                                                path = "",
                                                successful = false,
                                                details = "Path parameter is required"
                                        ),
                                error = "Path parameter is required"
                        )
                }

                return try {
                        val file = File(path)
                        if (!file.exists()) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result =
                                                FileOperationData(
                                                        "share",
                                                        path,
                                                        false,
                                                        "File does not exist: $path"
                                                ),
                                        error = "File does not exist: $path"
                                )
                        }

                        val authority = "${context.packageName}.fileprovider"
                        val uri = FileProvider.getUriForFile(context, authority, file)
                        val mimeType =
                                MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
                                        ?: "*/*"

                        val intent =
                                Intent(Intent.ACTION_SEND).apply {
                                        type = mimeType
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, title)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }

                        val chooser =
                                Intent.createChooser(intent, title).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                        context.startActivity(chooser)

                        ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        FileOperationData(
                                                "share",
                                                path,
                                                true,
                                                "Share dialog for file opened: $path"
                                        ),
                                error = ""
                        )
                } catch (e: ActivityNotFoundException) {
                        Log.e(TAG, "No activity found to handle sharing file: $path", e)
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                "share",
                                                path,
                                                false,
                                                "No application found to share this file type."
                                        ),
                                error = "No application found to share this file type."
                        )
                } catch (e: Exception) {
                        Log.e(TAG, "Error sharing file", e)
                        ToolResult(
                                toolName = tool.name,
                                success = false,
                                result =
                                        FileOperationData(
                                                "share",
                                                path,
                                                false,
                                                "Error sharing file: ${e.message}"
                                        ),
                                error = "Error sharing file: ${e.message}"
                        )
                }
        }
}
