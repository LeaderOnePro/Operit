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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.content.FileProvider
import android.webkit.MimeTypeMap
import com.ai.assistance.operit.data.preferences.ApiPreferences

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
                                        "Detected Word document, attempting to convert to text before reading"
                                )
                                val tempFilePath =
                                        "${path}_converted_${System.currentTimeMillis()}.txt"
                                try {
                                        val fileConverterTool =
                                                AITool(
                                                        name = "convert_file",
                                                        parameters =
                                                                listOf(
                                                                        ToolParameter(
                                                                                "source_path",
                                                                                path
                                                                        ),
                                                                        ToolParameter(
                                                                                "target_path",
                                                                                tempFilePath
                                                                        )
                                                                )
                                                )
                                        val toolHandler = AIToolHandler.getInstance(context)
                                        val conversionResult =
                                                toolHandler.executeTool(fileConverterTool)

                                        if (conversionResult.success) {
                                                Log.d(
                                                        TAG,
                                                        "Successfully converted Word document to text"
                                                )
                                                val tempFile = File(tempFilePath)
                                                if (tempFile.exists()) {
                                                        val content = tempFile.readText()
                                                        tempFile.delete() // Clean up
                                                        ToolResult(
                                                                toolName = tool.name,
                                                                success = true,
                                                                result =
                                                                        FileContentData(
                                                                                path = path,
                                                                                content = content,
                                                                                size =
                                                                                        content.length
                                                                                                .toLong()
                                                                        ),
                                                                error = ""
                                                        )
                                                } else {
                                                        ToolResult(
                                                                toolName = tool.name,
                                                                success = false,
                                                                result = StringResultData(""),
                                                                error =
                                                                        "Conversion produced no output."
                                                        )
                                                }
                                        } else {
                                                Log.w(
                                                        TAG,
                                                        "Word conversion failed: ${conversionResult.error}, falling back to raw content"
                                                )
                                                ToolResult(
                                                        toolName = tool.name,
                                                        success = false,
                                                        result = StringResultData(""),
                                                        error =
                                                                "Failed to convert Word document: ${conversionResult.error}"
                                                )
                                        }
                                } catch (e: Exception) {
                                        Log.e(TAG, "Error during Word document conversion", e)
                                        ToolResult(
                                                toolName = tool.name,
                                                success = false,
                                                result = StringResultData(""),
                                                error =
                                                        "Error converting Word document: ${e.message}"
                                        )
                                }
                        }
                        "pdf" -> {
                                Log.d(
                                        TAG,
                                        "Detected PDF document, attempting to convert to text before reading"
                                )
                                val tempFilePath =
                                        "${path}_converted_${System.currentTimeMillis()}.txt"
                                try {
                                        val fileConverterTool =
                                                AITool(
                                                        name = "convert_file",
                                                        parameters =
                                                                listOf(
                                                                        ToolParameter(
                                                                                "source_path",
                                                                                path
                                                                        ),
                                                                        ToolParameter(
                                                                                "target_path",
                                                                                tempFilePath
                                                                        )
                                                                )
                                                )
                                        val toolHandler = AIToolHandler.getInstance(context)
                                        val conversionResult =
                                                toolHandler.executeTool(fileConverterTool)

                                        if (conversionResult.success) {
                                                Log.d(
                                                        TAG,
                                                        "Successfully converted PDF document to text"
                                                )
                                                val tempFile = File(tempFilePath)
                                                if (tempFile.exists()) {
                                                        val content = tempFile.readText()
                                                        tempFile.delete() // Clean up
                                                        ToolResult(
                                                                toolName = tool.name,
                                                                success = true,
                                                                result =
                                                                        FileContentData(
                                                                                path = path,
                                                                                content = content,
                                                                                size =
                                                                                        content.length
                                                                                                .toLong()
                                                                        ),
                                                                error = ""
                                                        )
                                                } else {
                                                        ToolResult(
                                                                toolName = tool.name,
                                                                success = false,
                                                                result = StringResultData(""),
                                                                error =
                                                                        "Conversion produced no output."
                                                        )
                                                }
                                        } else {
                                                Log.w(
                                                        TAG,
                                                        "PDF conversion failed: ${conversionResult.error}, falling back to raw content"
                                                )
                                                ToolResult(
                                                        toolName = tool.name,
                                                        success = false,
                                                        result = StringResultData(""),
                                                        error =
                                                                "Failed to convert PDF document: ${conversionResult.error}"
                                                )
                                        }
                                } catch (e: Exception) {
                                        Log.e(TAG, "Error during PDF document conversion", e)
                                        ToolResult(
                                                toolName = tool.name,
                                                success = false,
                                                result = StringResultData(""),
                                                error =
                                                        "Error converting PDF document: ${e.message}"
                                        )
                                }
                        }
                        "jpg", "jpeg", "png", "gif", "bmp" -> {
                                Log.d(
                                        TAG,
                                        "Detected image file, attempting to extract text using OCR"
                                )
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

                        if (FileUtils.isTextBasedExtension(fileExt)) {
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
                                        error = "Unsupported file format: .$fileExt"
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
                        if (!FileUtils.isTextBasedExtension(fileExt)) {
                                return ToolResult(
                                        toolName = tool.name,
                                        success = false,
                                        result = StringResultData(""),
                                        error =
                                                "Unsupported file format for partial read: .$fileExt. Use readFileFull tool for full content."
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
                val partIndex =
                        tool.parameters.find { it.name == "partIndex" }?.value?.toIntOrNull() ?: 0

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
                val content = tool.parameters.find { it.name == "content" }?.value ?: ""
                val append =
                        tool.parameters.find { it.name == "append" }?.value?.toBoolean() ?: false

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
                val base64Content = tool.parameters.find { it.name == "base64Content" }?.value ?: ""

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
                val recursive =
                        tool.parameters.find { it.name == "recursive" }?.value?.toBoolean() ?: false

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

        /** Copy a file or directory */
        open suspend fun copyFile(tool: AITool): ToolResult {
                val sourcePath = tool.parameters.find { it.name == "source" }?.value ?: ""
                val destPath = tool.parameters.find { it.name == "destination" }?.value ?: ""
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
                val createParents =
                        tool.parameters.find { it.name == "create_parents" }?.value?.toBoolean()
                                ?: false

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
                val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""

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

                if (sourcePath.isBlank() || zipPath.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Source and destination parameters are required"
                        )
                }

                return try {
                        val sourceFile = File(sourcePath)
                        val destZipFile = File(zipPath)

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

                if (zipPath.isBlank() || destPath.isBlank()) {
                        return ToolResult(
                                toolName = tool.name,
                                success = false,
                                result = StringResultData(""),
                                error = "Source and destination parameters are required"
                        )
                }

                return try {
                        val zipFile = File(zipPath)
                        val destDir = File(destPath)

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
                val aiGeneratedCode = tool.parameters.find { it.name == "content" }?.value ?: ""

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
                                AITool(name = "file_exists", parameters = listOf(ToolParameter("path", path)))
                        )

                // 如果文件不存在，直接写入内容而不是合并
                if (!fileExistsResult.success ||
                                !(fileExistsResult.result as FileExistsData).exists
                ) {
                        emit(
                                ToolResult(
                                        toolName = tool.name,
                                        success = true,
                                        result =
                                                StringResultData(
                                                        "File does not exist. Creating new file '$path'..."
                                                )
                                )
                        )

                        // 直接调用writeFile写入内容
                        val writeResult =
                                writeFile(
                                        AITool(
                                                name = "write_file",
                                                parameters =
                                                        listOf(
                                                                ToolParameter("path", path),
                                                                ToolParameter(
                                                                        "content",
                                                                        aiGeneratedCode
                                                                ),
                                                                ToolParameter("append", "false")
                                                        )
                                        )
                                )

                        if (writeResult.success) {
                                // 成功写入新文件
                                val operationData =
                                        FileOperationData(
                                                operation = "apply",
                                                path = path,
                                                successful = true,
                                                details = "Successfully created new file with AI code: $path"
                                        )

                                // 执行语法检查
                                val syntaxCheckResult = performSyntaxCheck(path, aiGeneratedCode)

                                emit(
                                        ToolResult(
                                                toolName = tool.name,
                                                success = true,
                                                result =
                                                        FileApplyResultData(
                                                                operation = operationData,
                                                                aiDiffInstructions = aiGeneratedCode,
                                                                syntaxCheckResult = syntaxCheckResult
                                                        ),
                                                error = ""
                                        )
                                )
                        } else {
                                // 写入失败，返回写入工具的错误
                                emit(writeResult)
                        }
                        return@flow
                }

                // 2. 读取原始文件内容
                val readResult =
                        readFileFull(AITool(name = "read_file_full", parameters = listOf(ToolParameter("path", path))))

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
                                                        ToolParameter("append", "false")
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

                emit(
                        ToolResult(
                                toolName = tool.name,
                                success = true,
                                result =
                                        FileApplyResultData(
                                                operation = operationData,
                                                aiDiffInstructions = aiInstructions,
                                                syntaxCheckResult = syntaxCheckResult
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
                        val destFile = File(destPath)

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
                val pattern = tool.parameters.find { it.name == "pattern" }?.value ?: ""
                val filePattern = tool.parameters.find { it.name == "file_pattern" }?.value ?: "*"
                val caseInsensitive = tool.parameters.find { it.name == "case_insensitive" }?.value?.toBoolean() ?: false
                val contextLines = tool.parameters.find { it.name == "context_lines" }?.value?.toIntOrNull() ?: 3
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
                
                return try {
                        // 1. 使用 findFiles 查找所有匹配的文件
                        val findFilesResult = findFiles(
                                AITool(
                                        name = "find_files",
                                        parameters = listOf(
                                                ToolParameter("path", path),
                                                ToolParameter("pattern", filePattern),
                                                ToolParameter("use_path_pattern", "false"),
                                                ToolParameter("case_insensitive", "false")
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
                val title = tool.parameters.find { it.name == "title" }?.value ?: "Share File"

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
