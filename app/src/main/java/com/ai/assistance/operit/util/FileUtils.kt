package com.ai.assistance.operit.util

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

object FileUtils {

    private const val TAG = "FileUtils"
    private const val BACKGROUND_IMAGES_DIR = "background_images"
    private const val BACKGROUND_VIDEOS_DIR = "background_videos"

    // List of common video file extensions
    private val VIDEO_EXTENSIONS = listOf("mp4", "3gp", "webm", "mkv", "avi", "mov", "flv", "wmv")

    private val TEXT_BASED_EXTENSIONS = setOf(
        // Common text files
        "txt", "md", "log", "ini", "env", "csv", "tsv", "text", "me",

        // Web files
        "html", "htm", "css", "js", "json", "xml", "yaml", "yml", "svg", "url",
        "sass", "scss", "less", "ejs", "hbs", "pug", "rss", "atom", "vtt", "webmanifest", "jsp", "asp", "aspx",

        // Programming language source files
        "java", "kt", "kts", "gradle",
        "c", "cpp", "h", "hpp", "cs", "m",
        "py", "rb", "php", "go", "swift",
        "ts", "tsx", "jsx",
        "sh", "bat", "ps1", "zsh",
        "sql", "groovy", "lua", "perl", "pl", "r", "dart", "rust", "rs", "scala",
        "asm", "pas", "f", "f90", "for", "lisp", "hs", "erl", "vb", "vbs", "tcl", "d", "nim", "sol", "zig", "vala", "cob", "cbl",

        // Config files
        "properties", "toml", "dockerfile", "gitignore", "gitattributes", "editorconfig", "conf", "cfg",
        "jsonc", "json5", "reg", "iml", "inf",

        // Document formats & Data Serialization
        "rtf", "tex", "srt", "sub", "asciidoc", "adoc", "rst", "org", "wiki", "mediawiki",
        "vcf", "ics", "gpx", "kml", "opml"
    )

    // Common text files without extensions
    private val TEXT_BASED_FILENAMES = setOf(
        "readme", "makefile", "dockerfile", "license", "changelog", "authors", 
        "contributors", "copying", "install", "news", "todo", "version",
        "gemfile", "rakefile", "vagrantfile", "buildfile"
    )

    /**
     * Checks if a file extension corresponds to a text-based file format.
     * @param extension The file extension without the dot (e.g., "txt", "java").
     * @return True if the extension is for a known text-based file, false otherwise.
     */
    fun isTextBasedExtension(extension: String): Boolean {
        return extension.lowercase() in TEXT_BASED_EXTENSIONS
    }

    /**
     * Checks if a file is a text-based file, considering both extension and filename.
     * @param file The file to check.
     * @return True if the file is likely a text-based file, false otherwise.
     */
    fun isTextBasedFile(file: File): Boolean {
        val extension = file.extension
        return if (extension.isEmpty()) {
            // Files without extension - check common text filenames
            file.name.lowercase() in TEXT_BASED_FILENAMES
        } else {
            isTextBasedExtension(extension)
        }
    }

    /**
     * Check if a URI points to a video file
     * @param context The application context
     * @param uri The URI to check
     * @return true if the URI is a video file, false otherwise
     */
    fun isVideoFile(context: Context, uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri) ?: return false
        // Check if MIME type starts with "video/"
        if (mimeType.startsWith("video/")) return true

        // If MIME type doesn't tell us, check the file extension
        val extension = getFileExtension(context, uri)
        return extension != null && extension.lowercase() in VIDEO_EXTENSIONS
    }

    /**
     * Get the file extension from a URI
     * @param context The application context
     * @param uri The URI to get the extension from
     * @return The file extension or null if it couldn't be determined
     */
    fun getFileExtension(context: Context, uri: Uri): String? {
        // First try to get from content resolver
        val mimeType = context.contentResolver.getType(uri)
        return if (mimeType != null) {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        } else {
            // Fallback to path parsing
            val path = uri.path
            return path?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
        }
    }

    /**
     * Copies a file from a given URI to the app's internal storage.
     * This makes the file private to the app and ensures persistent access.
     * @param context The context.
     * @param uri The URI of the file to copy.
     * @param uniqueName A unique name or prefix for the file to prevent overwriting.
     * @return The URI of the copied file in internal storage, or null on failure.
     */
    fun copyFileToInternalStorage(context: Context, uri: Uri, uniqueName: String): Uri? {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e("FileUtils", "Failed to open input stream for URI: $uri")
                return null
            }

            // Use the unique name to create a distinct file
            val file = File(context.filesDir, "${uniqueName}_${UUID.randomUUID()}.png")
            outputStream = FileOutputStream(file)

            val buffer = ByteArray(4 * 1024) // 4K buffer
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                outputStream.write(buffer, 0, read)
            }
            outputStream.flush()
            
            Log.d("FileUtils", "File copied successfully to internal storage: ${file.absolutePath}")
            return Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e("FileUtils", "Error copying file to internal storage", e)
            return null
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (e: Exception) {
                Log.e("FileUtils", "Error closing streams", e)
            }
        }
    }

    /**
     * Clean up old background media files to prevent using too much storage Keeps only the most
     * recent file
     */
    private fun cleanOldBackgroundFiles(directory: File, currentFileName: String) {
        try {
            val files = directory.listFiles()
            if (files != null && files.size > 1) {
                // Delete all files except the current one
                files.forEach { file ->
                    if (file.name != currentFileName) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old background files", e)
        }
    }

    /**
     * 检查视频文件大小是否超过限制
     * @param context 上下文
     * @param uri 视频文件URI
     * @param maxSizeMB 最大允许大小，单位MB
     * @return 如果视频大小在限制内返回true，否则返回false
     */
    fun checkVideoSize(context: Context, uri: Uri, maxSizeMB: Int = 30): Boolean {
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val fileSize = pfd.statSize
                val maxSizeBytes = maxSizeMB * 1024 * 1024L
                return fileSize <= maxSizeBytes
            }
        } catch (e: Exception) {
            Log.e("FileUtils", "检查视频大小时出错", e)
        }
        // 如果无法检查大小，返回true以避免阻止用户选择
        return true
    }
}
