package com.ai.assistance.operit.data.mnn

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Serializable
data class MnnModel(
    val modelName: String,
    val size_gb: Double,
    val tags: List<String> = emptyList(),
    val sources: Map<String, String> = emptyMap(),
    val description: String = ""
)

@Serializable
data class ModelMarketData(
    val models: List<MnnModel> = emptyList()
)

// ModelScope API 响应数据结构
@Serializable
data class MsRepoInfo(
    val Code: Int = 0,
    val Data: MsResponseData? = null,
    val Message: String? = null,
    val Success: Boolean = false
)

@Serializable
data class MsResponseData(
    val Files: List<MsFileInfo>? = null
)

@Serializable
data class MsFileInfo(
    val Name: String? = null,
    val Path: String? = null,
    val Size: Long = 0,
    val Type: String? = null
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(
        val progress: Float, 
        val speed: String, 
        val downloadedBytes: Long, 
        val totalBytes: Long,
        val currentFile: String = "",
        val currentFileIndex: Int = 0,
        val totalFiles: Int = 1
    ) : DownloadState()
    data class Paused(val progress: Float, val downloadedBytes: Long) : DownloadState()
    object Completed : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

class MnnModelDownloadManager(private val context: Context) {
    
    companion object {
        private const val TAG = "MnnModelDownloadMgr"
        private const val MODEL_MARKET_URL = "https://meta.alicdn.com/data/mnn/apis/model_market.json"
        private const val CACHE_FILE_NAME = "mnn_model_market_cache.json"
        private const val TEMP_SUFFIX = ".tmp"
        
        val MODEL_DIR = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Operit/models/mnn"
        )
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val downloadStateMap = ConcurrentHashMap<String, MutableStateFlow<DownloadState>>()
    private val pauseFlags = ConcurrentHashMap<String, Boolean>()
    
    init {
        if (!MODEL_DIR.exists()) {
            MODEL_DIR.mkdirs()
        }
    }
    
    fun getDownloadState(modelName: String): StateFlow<DownloadState> {
        return downloadStateMap.getOrPut(modelName) {
            MutableStateFlow(DownloadState.Idle)
        }
    }
    
    suspend fun fetchModelList(): Result<List<MnnModel>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(MODEL_MARKET_URL).build()
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext loadFromCache()
            }
            
            val jsonString = response.body?.string() ?: ""
            val marketData = json.decodeFromString<ModelMarketData>(jsonString)
            saveToCache(jsonString)
            return@withContext Result.success(marketData.models)
        } catch (e: Exception) {
            Log.e(TAG, "获取模型列表失败", e)
            val cachedResult = loadFromCache()
            return@withContext if (cachedResult.isSuccess) cachedResult else Result.failure(e)
        }
    }
    
    private fun saveToCache(jsonString: String) {
        try {
            File(context.filesDir, CACHE_FILE_NAME).writeText(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "保存缓存失败", e)
        }
    }
    
    private fun loadFromCache(): Result<List<MnnModel>> {
        return try {
            val cacheFile = File(context.filesDir, CACHE_FILE_NAME)
            if (!cacheFile.exists()) {
                return Result.failure(Exception("无缓存数据"))
            }
            val jsonString = cacheFile.readText()
            val marketData = json.decodeFromString<ModelMarketData>(jsonString)
            Result.success(marketData.models)
        } catch (e: Exception) {
            Log.e(TAG, "从缓存加载失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 获取 ModelScope 仓库的文件列表
     * 参考: MsApiService.java:11-15
     */
    private suspend fun fetchRepoFiles(modelScopeId: String): Result<List<MsFileInfo>> = withContext(Dispatchers.IO) {
        try {
            val parts = modelScopeId.split("/")
            if (parts.size != 2) {
                return@withContext Result.failure(Exception("Invalid ModelScope ID format: $modelScopeId"))
            }
            
            val url = "https://modelscope.cn/api/v1/models/${parts[0]}/${parts[1]}/repo/files?Recursive=1"
            Log.d(TAG, "获取文件列表: $url")
            
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val error = "HTTP ${response.code}: ${response.message}"
                Log.e(TAG, "获取文件列表失败: $error")
                return@withContext Result.failure(Exception(error))
            }
            
            val jsonString = response.body?.string() ?: ""
            Log.d(TAG, "文件列表响应: $jsonString")
            
            val repoInfo = json.decodeFromString<MsRepoInfo>(jsonString)
            
            if (!repoInfo.Success) {
                return@withContext Result.failure(Exception(repoInfo.Message ?: "Unknown error"))
            }
            
            val files = repoInfo.Data?.Files?.filter { it.Type != "tree" } ?: emptyList()
            Result.success(files)
        } catch (e: Exception) {
            Log.e(TAG, "获取文件列表异常", e)
            Result.failure(e)
        }
    }
    
    suspend fun downloadModel(
        modelName: String,
        sourceUrl: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // 创建模型文件夹（参考 MsModelDownloader.kt:174-176）
            val modelFolderName = getLastFileName(sourceUrl)
            val modelFolder = File(MODEL_DIR, modelFolderName)
            
            if (!modelFolder.exists()) {
                modelFolder.mkdirs()
            }
            
            Log.d(TAG, "========== 开始下载模型 ==========")
            Log.d(TAG, "模型名称: $modelName")
            Log.d(TAG, "源地址: $sourceUrl")
            Log.d(TAG, "模型文件夹: ${modelFolder.absolutePath}")
            
            // 构建下载URL（参考 MsModelDownloader.kt:244-248）
            val downloadUrl = if (sourceUrl.startsWith("http")) {
                sourceUrl
            } else {
                // ModelScope仓库格式: owner/repo
                // 先获取仓库文件列表，找到实际的文件路径
                val filesResult = fetchRepoFiles(sourceUrl)
                if (filesResult.isFailure) {
                    val error = "获取仓库文件列表失败: ${filesResult.exceptionOrNull()?.message}"
                    updateDownloadState(modelName, DownloadState.Failed(error))
                    return@withContext Result.failure(Exception(error))
                }
                
                val files = filesResult.getOrNull() ?: emptyList()
                Log.d(TAG, "仓库文件列表 (${files.size} 个文件):")
                files.forEach { file ->
                    Log.d(TAG, "  - ${file.Path} (${file.Size} 字节, ${file.Type})")
                }
                
                // 下载所有非目录文件（参考 MsModelDownloader.kt:236-241）
                return@withContext downloadAllFiles(modelName, sourceUrl, files, modelFolder)
            }
            
            // 单文件下载（直接URL）
            val fileName = getFileName(modelName)
            val targetFile = File(modelFolder, fileName)
            val tempFile = File(modelFolder, "$fileName$TEMP_SUFFIX")
            
            Log.d(TAG, "目标文件路径: ${targetFile.absolutePath}")
            Log.d(TAG, "临时文件路径: ${tempFile.absolutePath}")
            
            // 获取服务器文件大小
            Log.d(TAG, "发送 HEAD 请求获取文件大小...")
            try {
                val headRequest = Request.Builder().url(downloadUrl).head().build()
                val headResponse = okHttpClient.newCall(headRequest).execute()
                val serverFileSize = headResponse.header("Content-Length")?.toLongOrNull() ?: -1L
                val responseCode = headResponse.code
                headResponse.close()
                
                Log.d(TAG, "HEAD 响应码: $responseCode")
                Log.d(TAG, "服务器文件大小: $serverFileSize 字节 (${formatFileSize(serverFileSize)})")
                
                // 检查文件是否已完整下载
                if (targetFile.exists()) {
                    val localFileSize = targetFile.length()
                    Log.d(TAG, "发现本地文件，大小: $localFileSize 字节 (${formatFileSize(localFileSize)})")
                    
                    if (serverFileSize > 0 && localFileSize == serverFileSize) {
                        Log.d(TAG, "✅ 文件大小匹配，已完整下载，跳过下载")
                        updateDownloadState(modelName, DownloadState.Completed)
                        return@withContext Result.success(targetFile)
                    } else {
                        Log.w(TAG, "❌ 文件大小不匹配！期望: $serverFileSize, 实际: $localFileSize")
                        Log.w(TAG, "删除损坏的文件...")
                        val deleted = targetFile.delete()
                        Log.d(TAG, "删除${if (deleted) "成功" else "失败"}")
                    }
                } else {
                    Log.d(TAG, "本地文件不存在，需要下载")
                }
            } catch (e: Exception) {
                Log.e(TAG, "HEAD 请求失败: ${e.message}", e)
                // HEAD 失败不影响继续下载，只是无法验证已存在的文件
                if (targetFile.exists()) {
                    Log.w(TAG, "无法验证文件完整性，删除后重新下载")
                    targetFile.delete()
                }
            }
            
            // 断点续传
            pauseFlags[modelName] = false
            val downloadedBytes = if (tempFile.exists()) tempFile.length() else 0L
            
            if (downloadedBytes > 0) {
                Log.d(TAG, "发现临时文件，使用断点续传，已下载: $downloadedBytes 字节 (${formatFileSize(downloadedBytes)})")
            } else {
                Log.d(TAG, "从头开始下载")
            }
            
            val requestBuilder = Request.Builder().url(downloadUrl)
            if (downloadedBytes > 0) {
                requestBuilder.header("Range", "bytes=$downloadedBytes-")
                Log.d(TAG, "添加 Range 请求头: bytes=$downloadedBytes-")
            }
            
            Log.d(TAG, "发送下载请求...")
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            Log.d(TAG, "响应码: ${response.code}")
            
            if (!response.isSuccessful && response.code != 206) {
                val error = "下载失败: HTTP ${response.code} ${response.message}"
                Log.e(TAG, error)
                updateDownloadState(modelName, DownloadState.Failed(error))
                return@withContext Result.failure(Exception(error))
            }
            
            val contentLength = response.body?.contentLength() ?: 0L
            val totalBytes = if (response.code == 206) downloadedBytes + contentLength else contentLength
            
            Log.d(TAG, "Content-Length: $contentLength 字节")
            Log.d(TAG, "总大小: $totalBytes 字节 (${formatFileSize(totalBytes)})")
            Log.d(TAG, "开始写入数据...")
            
            val inputStream = response.body?.byteStream() ?: run {
                updateDownloadState(modelName, DownloadState.Failed("响应体为空"))
                return@withContext Result.failure(Exception("响应体为空"))
            }
            
            val outputStream = FileOutputStream(tempFile, downloadedBytes > 0)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var currentDownloaded = downloadedBytes
            var lastUpdateTime = System.currentTimeMillis()
            var lastDownloaded = downloadedBytes
            var loopCount = 0
            
            Log.d(TAG, "进入下载循环...")
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                loopCount++
                
                if (pauseFlags[modelName] == true) {
                    Log.w(TAG, "检测到暂停标志，暂停下载")
                    outputStream.close()
                    inputStream.close()
                    val progress = if (totalBytes > 0) currentDownloaded.toFloat() / totalBytes else 0f
                    updateDownloadState(modelName, DownloadState.Paused(progress, currentDownloaded))
                    return@withContext Result.failure(Exception("下载已暂停"))
                }
                
                outputStream.write(buffer, 0, bytesRead)
                currentDownloaded += bytesRead
                
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime >= 500) {
                    val speedBytesPerSec = (currentDownloaded - lastDownloaded) / ((currentTime - lastUpdateTime) / 1000.0)
                    val progress = if (totalBytes > 0) currentDownloaded.toFloat() / totalBytes else 0f
                    
                    Log.d(TAG, "下载进度: ${String.format("%.2f", progress * 100)}% " +
                            "(${formatFileSize(currentDownloaded)}/${formatFileSize(totalBytes)}) " +
                            "速度: ${formatSpeed(speedBytesPerSec)} " +
                            "循环次数: $loopCount")
                    
                    updateDownloadState(
                        modelName,
                        DownloadState.Downloading(progress, formatSpeed(speedBytesPerSec), currentDownloaded, totalBytes)
                    )
                    lastUpdateTime = currentTime
                    lastDownloaded = currentDownloaded
                }
            }
            
            outputStream.close()
            inputStream.close()
            
            Log.d(TAG, "下载完成！总循环次数: $loopCount, 最终大小: ${formatFileSize(currentDownloaded)}")
            Log.d(TAG, "重命名临时文件: ${tempFile.name} -> ${targetFile.name}")
            
            if (tempFile.renameTo(targetFile)) {
                Log.d(TAG, "✅ 重命名成功，下载完成")
                updateDownloadState(modelName, DownloadState.Completed)
                Result.success(targetFile)
            } else {
                Log.e(TAG, "❌ 重命名失败！")
                updateDownloadState(modelName, DownloadState.Failed("重命名失败"))
                Result.failure(Exception("重命名失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "========== 下载异常 ==========")
            Log.e(TAG, "模型: $modelName", e)
            Log.e(TAG, "错误: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            updateDownloadState(modelName, DownloadState.Failed(e.message ?: "未知错误"))
            Result.failure(e)
        }
    }
    
    fun pauseDownload(modelName: String) {
        pauseFlags[modelName] = true
    }
    
    fun deleteModel(modelName: String): Boolean {
        return try {
            val folderName = getLastFileName(modelName)
            val modelFolder = File(MODEL_DIR, folderName)
            
            val deleted = if (modelFolder.exists() && modelFolder.isDirectory) {
                // 递归删除文件夹及其所有内容
                modelFolder.deleteRecursively()
            } else {
                // 兼容旧的单文件模式
                val fileName = getFileName(modelName)
                val targetFile = File(MODEL_DIR, fileName)
                val tempFile = File(MODEL_DIR, "$fileName$TEMP_SUFFIX")
                targetFile.delete() or tempFile.delete()
            }
            
            updateDownloadState(modelName, DownloadState.Idle)
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "删除模型失败", e)
            false
        }
    }
    
    fun getDownloadedModels(): List<File> {
        // 返回所有模型文件夹（不是临时文件）
        return MODEL_DIR.listFiles { file ->
            file.isDirectory && !file.name.endsWith(TEMP_SUFFIX)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    fun isModelDownloaded(modelName: String): Boolean {
        // 检查模型文件夹是否存在
        val folderName = getLastFileName(modelName)
        val modelFolder = File(MODEL_DIR, folderName)
        return modelFolder.exists() && modelFolder.isDirectory
    }
    
    private fun updateDownloadState(modelName: String, state: DownloadState) {
        downloadStateMap.getOrPut(modelName) {
            MutableStateFlow(DownloadState.Idle)
        }.value = state
    }
    
    private fun getFileName(modelName: String): String {
        return if (modelName.endsWith(".mnn")) modelName else "$modelName.mnn"
    }
    
    private fun formatSpeed(bytesPerSecond: Double): String {
        return when {
            bytesPerSecond < 1024 -> String.format("%.0f B/s", bytesPerSecond)
            bytesPerSecond < 1024 * 1024 -> String.format("%.2f KB/s", bytesPerSecond / 1024)
            else -> String.format("%.2f MB/s", bytesPerSecond / (1024 * 1024))
        }
    }
    
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
    
    private fun getLastFileName(path: String): String {
        // 从路径中提取最后一部分作为文件夹名
        // 例如: "moxin-org/moxin-chat-7b" -> "moxin-chat-7b"
        return path.substringAfterLast('/')
    }
    
    /**
     * 下载仓库中的所有文件到模型文件夹
     * 参考 MsModelDownloader.kt:162-219
     */
    private suspend fun downloadAllFiles(
        modelName: String,
        repositoryPath: String,
        files: List<MsFileInfo>,
        modelFolder: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // 计算总大小
            var totalBytes = 0L
            val downloadTasks = mutableListOf<Pair<MsFileInfo, File>>()
            
            for (file in files) {
                if (file.Type == "tree") continue // 跳过目录
                
                val targetFile = File(modelFolder, file.Path ?: continue)
                targetFile.parentFile?.mkdirs()
                
                downloadTasks.add(file to targetFile)
                totalBytes += file.Size
            }
            
            Log.d(TAG, "准备下载 ${downloadTasks.size} 个文件，总大小: ${formatFileSize(totalBytes)}")
            
            // 下载每个文件
            var downloadedBytes = 0L
            var currentFileIndex = 0
            
            for ((fileInfo, targetFile) in downloadTasks) {
                currentFileIndex++
                val fileName = fileInfo.Path ?: continue
                
                Log.d(TAG, "下载文件 [$currentFileIndex/${downloadTasks.size}]: $fileName")
                
                // 如果文件已存在且大小匹配，跳过
                if (targetFile.exists() && targetFile.length() == fileInfo.Size) {
                    Log.d(TAG, "文件已存在，跳过: $fileName")
                    downloadedBytes += fileInfo.Size
                    
                    val progress = downloadedBytes.toFloat() / totalBytes
                    updateDownloadState(
                        modelName,
                        DownloadState.Downloading(
                            progress = progress,
                            speed = "0 B/s",
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            currentFile = fileName,
                            currentFileIndex = currentFileIndex,
                            totalFiles = downloadTasks.size
                        )
                    )
                    continue
                }
                
                // 构建下载URL
                val downloadUrl = String.format(
                    "https://modelscope.cn/api/v1/models/%s/repo?FilePath=%s",
                    repositoryPath,
                    fileName
                )
                
                val tempFile = File(targetFile.parentFile, "${targetFile.name}$TEMP_SUFFIX")
                
                // 断点续传支持
                val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
                downloadedBytes += existingBytes
                
                val request = Request.Builder()
                    .url(downloadUrl)
                    .apply {
                        if (existingBytes > 0) {
                            addHeader("Range", "bytes=$existingBytes-")
                        }
                    }
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                
                if (!response.isSuccessful && response.code != 206) {
                    response.close()
                    val error = "下载文件失败: $fileName (HTTP ${response.code})"
                    updateDownloadState(modelName, DownloadState.Failed(error))
                    return@withContext Result.failure(Exception(error))
                }
                
                // 下载文件内容
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(tempFile, existingBytes > 0).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var lastUpdateTime = System.currentTimeMillis()
                        var lastDownloadedBytes = downloadedBytes
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            // 检查是否暂停
                            if (pauseFlags[modelName] == true) {
                                response.close()
                                val progress = downloadedBytes.toFloat() / totalBytes
                                updateDownloadState(
                                    modelName,
                                    DownloadState.Paused(progress, downloadedBytes)
                                )
                                return@withContext Result.failure(Exception("下载已暂停"))
                            }
                            
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastUpdateTime >= 500) {
                                val deltaTime = (currentTime - lastUpdateTime) / 1000.0
                                val deltaBytes = downloadedBytes - lastDownloadedBytes
                                val speed = formatSpeed(deltaBytes / deltaTime)
                                val progress = downloadedBytes.toFloat() / totalBytes
                                
                                updateDownloadState(
                                    modelName,
                                    DownloadState.Downloading(
                                        progress = progress,
                                        speed = speed,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes,
                                        currentFile = fileName,
                                        currentFileIndex = currentFileIndex,
                                        totalFiles = downloadTasks.size
                                    )
                                )
                                
                                lastUpdateTime = currentTime
                                lastDownloadedBytes = downloadedBytes
                            }
                        }
                    }
                }
                
                response.close()
                
                // 将临时文件重命名为目标文件
                if (tempFile.exists()) {
                    tempFile.renameTo(targetFile)
                }
                
                Log.d(TAG, "文件下载完成: $fileName")
            }
            
            Log.d(TAG, "所有文件下载完成！模型文件夹: ${modelFolder.absolutePath}")
            updateDownloadState(modelName, DownloadState.Completed)
            
            Result.success(modelFolder)
        } catch (e: Exception) {
            Log.e(TAG, "下载失败", e)
            updateDownloadState(modelName, DownloadState.Failed(e.message ?: "未知错误"))
            Result.failure(e)
        }
    }
}

