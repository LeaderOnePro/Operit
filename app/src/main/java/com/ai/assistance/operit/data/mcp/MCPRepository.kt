package com.ai.assistance.operit.data.mcp

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.mcp.MCPManager
import com.ai.assistance.operit.core.tools.mcp.MCPPackage
import com.ai.assistance.operit.core.tools.mcp.MCPToolExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.permissions.ToolCategory

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking

/**
 * 统一的MCP仓库管理类
 * 
 * 职责：
 * - 管理MCP服务器的UI状态和数据
 * - 处理插件的安装、卸载
 * - 管理已安装插件的状态跟踪
 * - 处理远程服务器的添加和管理
 * 
 * 配置管理由MCPLocalServer单独处理
 */
class MCPRepository(private val context: Context) {
    private val mcpLocalServer = MCPLocalServer.getInstance(context)

    companion object {
        private const val TAG = "MCPRepository"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 10000
        private const val READ_TIMEOUT = 15000
        private const val PLUGINS_DIR_NAME = "mcp_plugins"
        private const val OPERIT_DIR_NAME = "Operit"
    }

    // UI状态管理
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _mcpServers = MutableStateFlow<List<MCPLocalServer.PluginMetadata>>(emptyList())
    val mcpServers: StateFlow<List<MCPLocalServer.PluginMetadata>> = _mcpServers.asStateFlow()

    // 已安装插件ID管理
    private val _installedPluginIds = MutableStateFlow<Set<String>>(emptySet())
    val installedPluginIds: StateFlow<Set<String>> = _installedPluginIds.asStateFlow()

    // 插件安装目录
    private val pluginsBaseDir by lazy {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val operitDir = File(downloadsDir, OPERIT_DIR_NAME)
        val pluginsDir = File(operitDir, PLUGINS_DIR_NAME)

        if (!operitDir.exists()) operitDir.mkdirs()
        if (!pluginsDir.exists()) pluginsDir.mkdirs()

        if (pluginsDir.exists() && pluginsDir.canWrite()) {
            pluginsDir
        } else {
            val fallbackDir = context.getExternalFilesDir(PLUGINS_DIR_NAME)
                ?: File(context.filesDir, PLUGINS_DIR_NAME).also {
                    if (!it.exists()) it.mkdirs()
                }
            Log.w(TAG, "使用应用私有目录: ${fallbackDir.path}")
            fallbackDir
        }
    }

    init {
        loadPluginsFromMCPLocalServer()
        
        // 监听MCPLocalServer的配置变化
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            mcpLocalServer.pluginMetadata.collect {
                // 当插件元数据发生变化时，重新加载插件列表
                loadPluginsFromMCPLocalServer()
            }
        }
    }

    // ==================== 插件状态管理 ====================

    /**
     * 从MCPLocalServer加载插件信息（主要数据源）
     */
    private fun loadPluginsFromMCPLocalServer() {
        try {
            val pluginMetadata = mcpLocalServer.getAllPluginMetadata()
            val mcpServers = mcpLocalServer.getAllMCPServers()
            
            // 构建插件列表
            val servers = mutableListOf<MCPLocalServer.PluginMetadata>()
            val installedIds = mutableSetOf<String>()
            
            pluginMetadata.values.forEach { metadata ->
                val isPhysicallyInstalled = when (metadata.type) {
                    "remote" -> true // 远程服务器配置后即为"已安装"
                    "local" -> isPluginPhysicallyInstalled(metadata.id)
                    else -> isPluginPhysicallyInstalled(metadata.id)
                }
                
                if (isPhysicallyInstalled || metadata.type == "remote") {
                    installedIds.add(metadata.id)
                }
                
                // 创建更新的metadata，确保isInstalled字段正确
                val updatedMetadata = metadata.copy(
                    isInstalled = isPhysicallyInstalled || metadata.type == "remote"
                )
                
                servers.add(updatedMetadata)
            }
            
            _mcpServers.value = servers.sortedBy { it.name }
            _installedPluginIds.value = installedIds
            
        } catch (e: Exception) {
            Log.e(TAG, "从MCPLocalServer加载插件失败", e)
        }
    }

    /**
     * 扫描文件系统中实际安装的插件（辅助验证）
     */
    private fun scanPhysicallyInstalledPlugins(): Set<String> {
        val installedIds = mutableSetOf<String>()
        try {
            if (pluginsBaseDir.exists() && pluginsBaseDir.isDirectory) {
                pluginsBaseDir.listFiles()?.forEach { pluginDir ->
                    if (pluginDir.isDirectory && isPluginPhysicallyInstalled(pluginDir.name)) {
                        installedIds.add(pluginDir.name)
                    }
                }
            }
            Log.d(TAG, "文件系统扫描到已安装插件: ${installedIds.size}")
        } catch (e: Exception) {
            Log.e(TAG, "扫描文件系统插件失败", e)
        }
        return installedIds
    }

    /**
     * 检查插件是否在文件系统中物理存在
     */
    private fun isPluginPhysicallyInstalled(serverId: String): Boolean {
        val pluginDir = File(pluginsBaseDir, serverId)
        return if (pluginDir.exists() && pluginDir.isDirectory) {
            val hasContent = pluginDir.listFiles()?.isNotEmpty() ?: false
            if (hasContent) checkForRequiredFiles(pluginDir) else false
        } else false
    }

    /**
     * 检查插件是否已安装（优先从MCPLocalServer检查）
     */
    fun isPluginInstalled(serverId: String): Boolean {
        val metadata = mcpLocalServer.getPluginMetadata(serverId)
        return when (metadata?.type) {
            "remote" -> true // 远程服务器配置后即为已安装
            "local" -> isPluginPhysicallyInstalled(serverId)
            null -> false // 没有元数据记录
            else -> isPluginPhysicallyInstalled(serverId)
        }
    }

    /**
     * 获取已安装插件的路径
     */
    fun getInstalledPluginPath(serverId: String): String? {
        val pluginDir = File(pluginsBaseDir, serverId)
        if (!pluginDir.exists() || !pluginDir.isDirectory) return null

        val subdirs = pluginDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        return if (subdirs.isNotEmpty()) {
            val repoDir = subdirs.find { it.name.contains(serverId, ignoreCase = true) }
            repoDir?.path ?: subdirs.first().path
                                } else {
            if (pluginDir.listFiles()?.isNotEmpty() == true) pluginDir.path else null
        }
    }

    // ==================== 插件安装功能 ====================

    /**
     * 安装MCP插件
     */
    suspend fun installMCPServer(
        pluginId: String,
        progressCallback: (InstallProgress) -> Unit = {}
    ): InstallResult {
        return withContext(Dispatchers.IO) {
            val metadata = _mcpServers.value.find { it.id == pluginId }
            if (metadata == null) {
                Log.e(TAG, "找不到服务器信息: $pluginId")
                return@withContext InstallResult.Error("找不到对应的服务器信息")
            }

            val result = installPluginInternal(metadata, progressCallback)
            
            if (result is InstallResult.Success) {
                // 保存插件元数据到MCPLocalServer
                savePluginMetadata(metadata, result.pluginPath)
                // 重新加载插件状态
                loadPluginsFromMCPLocalServer()
            }

            result
        }
    }

    /**
     * 安装MCP插件 - 使用服务器对象
     */
    suspend fun installMCPServerWithObject(
        server: MCPLocalServer.PluginMetadata,
        progressCallback: (InstallProgress) -> Unit = {}
    ): InstallResult {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "安装服务器插件: ${server.name} (ID: ${server.id})")

            val result = installPluginInternal(server, progressCallback)
            
            if (result is InstallResult.Success) {
                // 保存插件元数据到MCPLocalServer
                savePluginMetadata(server, result.pluginPath)
                // 重新加载插件状态
                loadPluginsFromMCPLocalServer()
            }

            result
        }
    }

    /**
     * 从本地ZIP文件安装MCP插件
     */
    suspend fun installMCPServerFromZip(
            serverId: String,
        zipUri: Uri,
            name: String,
            description: String,
            author: String,
            progressCallback: (InstallProgress) -> Unit = {}
    ): InstallResult {
        return withContext(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                Log.d(TAG, "从本地ZIP安装插件, ID: $serverId, Name: $name")

                val server = MCPLocalServer.PluginMetadata(
                                id = serverId,
                                name = name,
                                description = description,
                                logoUrl = "",
                                author = author,
                                isInstalled = false,
                                version = "1.0.0",
                                updatedAt = "",
                                longDescription = description,
                                repoUrl = "",
                                type = "local"
                        )

                val result = installPluginFromZipInternal(server, zipUri, progressCallback)

                if (result is InstallResult.Success) {
                    savePluginMetadata(server, result.pluginPath)
                    // 重新加载插件状态
                    loadPluginsFromMCPLocalServer()
                }

                result
            } catch (e: Exception) {
                Log.e(TAG, "从本地ZIP安装插件失败", e)
                InstallResult.Error("安装时出错: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 卸载MCP插件
     */
    suspend fun uninstallMCPServer(pluginId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val pluginDir = File(pluginsBaseDir, pluginId)
                val result = if (pluginDir.exists()) {
                    pluginDir.deleteRecursively()
                } else {
                    true // 目录不存在，认为卸载成功
                }

                if (result) {
                    // 从MCPLocalServer中移除配置
                    mcpLocalServer.removeMCPServer(pluginId)
                    // 重新加载插件状态
                    loadPluginsFromMCPLocalServer()
                    Log.d(TAG, "插件卸载成功: $pluginId")
                } else {
                    Log.e(TAG, "插件卸载失败: $pluginId")
                }

                result
            } catch (e: Exception) {
                Log.e(TAG, "卸载插件时发生错误: $pluginId", e)
                false
            }
        }
    }

    // ==================== 内部安装实现 ====================

    /**
     * 内部安装插件实现
     */
    private suspend fun installPluginInternal(
        server: MCPLocalServer.PluginMetadata,
        progressCallback: (InstallProgress) -> Unit
    ): InstallResult {
        progressCallback(InstallProgress.Preparing)

        try {
            Log.d(TAG, "安装插件 - 名称: ${server.name}, URL: ${server.repoUrl}")

            val pluginDir = File(pluginsBaseDir, server.id)
            if (pluginDir.exists()) {
                Log.d(TAG, "删除已存在的插件目录: ${pluginDir.path}")
                pluginDir.deleteRecursively()
            }
            pluginDir.mkdirs()

            val repoOwnerAndName = extractOwnerAndRepo(server.repoUrl)
            if (repoOwnerAndName == null) {
                Log.e(TAG, "无法从 URL 提取仓库信息: ${server.repoUrl}")
                return InstallResult.Error("无效的 GitHub 仓库 URL")
            }

            val (owner, repoName) = repoOwnerAndName
            Log.d(TAG, "准备下载仓库: $owner/$repoName")

            progressCallback(InstallProgress.Downloading(0))
            val zipFile = downloadRepositoryZip(owner, repoName, server.id, progressCallback)

            if (zipFile == null || !zipFile.exists()) {
                return InstallResult.Error("下载仓库 ZIP 文件失败")
            }

            progressCallback(InstallProgress.Extracting(0))
            val extractSuccess = extractZipFile(zipFile, pluginDir, progressCallback)
            zipFile.delete()

            if (!extractSuccess) {
                pluginDir.deleteRecursively()
                return InstallResult.Error("解压仓库文件失败")
            }

            val extractedDirs = pluginDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            if (extractedDirs.isEmpty()) {
                return InstallResult.Error("解压后没有找到仓库目录")
            }

            val mainDir = extractedDirs.first()
            Log.d(TAG, "插件解压成功，主目录: ${mainDir.path}")

            progressCallback(InstallProgress.Finished)
            return InstallResult.Success(mainDir.path)

        } catch (e: Exception) {
            Log.e(TAG, "安装插件失败", e)
            return InstallResult.Error("安装插件时出错: ${e.message}")
        }
    }

    /**
     * 从ZIP文件安装插件的内部实现
     */
    private suspend fun installPluginFromZipInternal(
        server: MCPLocalServer.PluginMetadata,
        zipUri: Uri,
        progressCallback: (InstallProgress) -> Unit
    ): InstallResult {
        progressCallback(InstallProgress.Preparing)

        try {
            Log.d(TAG, "从本地ZIP安装插件 - 名称: ${server.name}, URI: $zipUri")

            val pluginDir = File(pluginsBaseDir, server.id)
            if (pluginDir.exists()) {
                Log.d(TAG, "删除已存在的插件目录: ${pluginDir.path}")
                pluginDir.deleteRecursively()
            }
            pluginDir.mkdirs()

            val tempFile = File(context.cacheDir, "mcp_${server.id}_local.zip")
            if (tempFile.exists()) tempFile.delete()

            progressCallback(InstallProgress.Downloading(0))

            // 从URI读取ZIP文件
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        progressCallback(InstallProgress.Downloading(-1))
                    }
                }
            } ?: return InstallResult.Error("无法读取ZIP文件")

            progressCallback(InstallProgress.Extracting(0))
            val extractSuccess = extractZipFile(tempFile, pluginDir, progressCallback)
            tempFile.delete()

            if (!extractSuccess) {
                pluginDir.deleteRecursively()
                return InstallResult.Error("解压本地ZIP文件失败")
            }

            val extractedDirs = pluginDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
            val mainDir = if (extractedDirs.isEmpty()) pluginDir else extractedDirs.first()
            
            Log.d(TAG, "本地插件解压成功，主目录: ${mainDir.path}")

            progressCallback(InstallProgress.Finished)
            return InstallResult.Success(mainDir.path)

        } catch (e: Exception) {
            Log.e(TAG, "安装本地ZIP插件失败", e)
            return InstallResult.Error("安装本地ZIP插件时出错: ${e.message}")
        }
    }

    // ==================== 下载和解压工具方法 ====================

    /**
     * 下载仓库ZIP文件
     */
    private suspend fun downloadRepositoryZip(
        owner: String,
        repoName: String,
        serverId: String,
        progressCallback: (InstallProgress) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val defaultBranch = getGithubDefaultBranch(owner, repoName)

        if (defaultBranch == null) {
            Log.e(TAG, "无法确定 $owner/$repoName 的默认分支，下载失败")
            return@withContext null
        }
        
        val zipUrl = "https://github.com/$owner/$repoName/archive/refs/heads/$defaultBranch.zip"
        Log.d(TAG, "从确定的默认分支 '$defaultBranch' 下载: $zipUrl")
            
            val file = downloadFromUrl(zipUrl, serverId, progressCallback)
            if (file != null && file.exists() && file.length() > 0) {
            Log.d(TAG, "从默认分支 '$defaultBranch' 下载成功")
                return@withContext file
            }
        
        Log.e(TAG, "从默认分支 '$defaultBranch' 下载失败")
        null
    }

    /**
     * 使用 GitHub API 获取仓库的默认分支
     */
    private suspend fun getGithubDefaultBranch(owner: String, repoName: String): String? = withContext(Dispatchers.IO) {
        val apiUrl = "https://api.github.com/repos/$owner/$repoName"
        Log.d(TAG, "从 GitHub API 获取仓库信息: $apiUrl")
        try {
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = connection.inputStream.bufferedReader()
                val response = reader.readText()
                reader.close()

                val jsonObject = JsonParser.parseString(response).asJsonObject
                val defaultBranch = jsonObject.get("default_branch")?.asString

                if (!defaultBranch.isNullOrBlank()) {
                    Log.d(TAG, "找到 $owner/$repoName 的默认分支: $defaultBranch")
                    return@withContext defaultBranch
                } else {
                    Log.e(TAG, "在 $owner/$repoName 的 API 响应中找不到 'default_branch'")
                }
            } else {
                Log.e(TAG, "GitHub API 请求失败，响应码: ${connection.responseCode}，URL: $apiUrl")
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取 $owner/$repoName 的默认分支时出错", e)
        }
        null
    }

    /**
     * 从URL下载文件
     */
    private suspend fun downloadFromUrl(
        zipUrl: String,
        serverId: String,
        progressCallback: (InstallProgress) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "mcp_${serverId}_repo.zip")
        
        try {
            val url = URL(zipUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.doInput = true
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
            )
            
            connection.connect()
            
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "下载失败，HTTP响应码: ${connection.responseCode}")
                return@withContext null
            }
            
            val contentLength = connection.contentLength.toLong()
            Log.d(TAG, "开始下载，文件大小: $contentLength 字节")
            
            val inputStream = BufferedInputStream(connection.inputStream)
            val outputStream = FileOutputStream(tempFile)
            
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesRead: Long = 0
            var lastReportedProgress = -1
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                val progress = if (contentLength > 0) {
                    (totalBytesRead * 100 / contentLength).toInt()
                } else -1
                
                if (progress != lastReportedProgress) {
                    progressCallback(InstallProgress.Downloading(progress))
                    lastReportedProgress = progress
                }
            }
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            Log.d(TAG, "下载完成，保存到: ${tempFile.path}")
            return@withContext tempFile
            
        } catch (e: Exception) {
            Log.e(TAG, "下载ZIP文件失败: ${e.message}", e)
            if (tempFile.exists()) tempFile.delete()
            return@withContext null
        }
    }

    /**
     * 解压ZIP文件
     */
    private suspend fun extractZipFile(
        zipFile: File,
        targetDir: File,
        progressCallback: (InstallProgress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            targetDir.mkdirs()
            Log.d(TAG, "开始从${zipFile.path}提取文件到${targetDir.path}")
            
            ZipFile(zipFile).use { zip ->
                val inputStream = zipFile.inputStream()
                val zipInputStream = ZipInputStream(BufferedInputStream(inputStream))
                
                var entry = zipInputStream.nextEntry
                val totalEntries = countZipEntries(zipFile)
                var extractedCount = 0
                var lastReportedProgress = -1
                
                while (entry != null) {
                    val entryName = entry.name
                    
                    if (entryName.contains("__MACOSX") || entryName.endsWith(".DS_Store")) {
                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                        continue
                    }
                    
                    val outFile = File(targetDir, entryName)
                    
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        
                        val outputStream = FileOutputStream(outFile)
                        val buffer = ByteArray(BUFFER_SIZE)
                        var len: Int
                        
                        while (zipInputStream.read(buffer).also { len = it } > 0) {
                            outputStream.write(buffer, 0, len)
                        }
                        
                        outputStream.close()
                    }
                    
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                    
                    extractedCount++
                    val progress = if (totalEntries > 0) {
                        (extractedCount * 100 / totalEntries).toInt()
                    } else -1
                    
                    if (progress != lastReportedProgress) {
                        progressCallback(InstallProgress.Extracting(progress))
                        lastReportedProgress = progress
                    }
                }
                
                zipInputStream.close()
                inputStream.close()
            }
            
            Log.d(TAG, "解压完成，文件解压到: ${targetDir.path}")
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "解压ZIP文件失败", e)
            return@withContext false
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 计算ZIP文件中的条目数量
     */
    private fun countZipEntries(zipFile: File): Int {
        var count = 0
        try {
            val inputStream = zipFile.inputStream()
            val zipInputStream = ZipInputStream(BufferedInputStream(inputStream))
            
            while (zipInputStream.nextEntry != null) {
                count++
                zipInputStream.closeEntry()
            }
            
            zipInputStream.close()
            inputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, "计算ZIP条目数量失败", e)
        }
        return count
    }

    /**
     * 从GitHub仓库URL中提取所有者和仓库名
     */
    @SuppressLint("SuspiciousIndentation")
    private fun extractOwnerAndRepo(repoUrl: String): Pair<String, String>? {
        val regex = "(?:https?://)?(?:www\\.)?github\\.com/([\\w.-]+)/([\\w.-]+)(?:\\.git)?/?.*".toRegex()
        val matchResult = regex.find(repoUrl)
        
        if (matchResult != null && matchResult.groupValues.size >= 3) {
        val owner = matchResult.groupValues[1]
        val repo = matchResult.groupValues[2]
        
            if (owner.isNotBlank() && repo.isNotBlank()) {
                return owner to repo
            }
        }
        
        return null
    }

    /**
     * 检查插件目录中是否包含必要的关键文件
     */
    private fun checkForRequiredFiles(pluginDir: File): Boolean {
        val subdirs = pluginDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
        
        return if (subdirs.isNotEmpty()) {
            hasPluginRequiredFiles(subdirs.first())
        } else {
            hasPluginRequiredFiles(pluginDir)
        }
    }

    /**
     * 检查目录是否包含插件所需的必要文件
     */
    private fun hasPluginRequiredFiles(dir: File): Boolean {
        val requiredFiles = listOf(
            "mcp.config.json", "README.md", "package.json",
            "index.js", "index.py", "main.py", "main.js"
        )
        
        val dirFiles = dir.listFiles() ?: return false
        
        val hasAnyRequiredFile = requiredFiles.any { requiredFile ->
            dirFiles.any { it.name.equals(requiredFile, ignoreCase = true) }
        }
        
        if (!hasAnyRequiredFile) {
            val subDirs = dirFiles.filter { it.isDirectory }
            if (subDirs.isNotEmpty()) {
                return subDirs.any { hasPluginRequiredFiles(it) }
            }
        }
        
        return hasAnyRequiredFile
    }

    /**
     * 保存插件元数据到MCPLocalServer
     */
    private suspend fun savePluginMetadata(server: MCPLocalServer.PluginMetadata, pluginPath: String) {
        val metadata = server.copy(
            type = "local",
            installedPath = pluginPath,
            installedTime = System.currentTimeMillis()
        )
        
        mcpLocalServer.addOrUpdatePluginMetadata(metadata)
    }
    // ==================== 远程服务器管理 ====================

    /**
     * 添加远程服务器
     */
    suspend fun addRemoteServer(server: MCPLocalServer.PluginMetadata) {
        withContext(Dispatchers.IO) {
            if (server.type != "remote" || server.endpoint == null) {
                Log.e(TAG, "addRemoteServer调用了无效的远程服务器: ${server.id}")
                return@withContext
            }

            // For remote servers, we no longer create a local process.
            // We just store the metadata. The bridge will handle the connection.

            // 保存远程服务器元数据
            val metadata = server.copy(
                type = "remote",
                installedTime = System.currentTimeMillis()
            )
            
            mcpLocalServer.addOrUpdatePluginMetadata(metadata)

            mcpLocalServer.updateServerStatus(
                server.id,
                active = false, // The bridge will report the active status
                deploySuccess = true
            )

            // 重新加载插件状态
            loadPluginsFromMCPLocalServer()
        }
    }

    /**
     * 更新远程服务器
     */
    suspend fun updateRemoteServer(server: MCPLocalServer.PluginMetadata) {
        withContext(Dispatchers.IO) {
            val metadata = mcpLocalServer.getPluginMetadata(server.id)
            if (metadata == null) {
                Log.e(TAG, "无法找到要更新的插件元数据: ${server.id}")
                return@withContext
            }

            // 更新元数据
            val updatedMetadata = metadata.copy(
                name = server.name,
                description = server.description,
                longDescription = server.longDescription,
                author = server.author,
                endpoint = if (server.type == "remote") server.endpoint else metadata.endpoint,
                connectionType = if (server.type == "remote") server.connectionType else metadata.connectionType
            )
            mcpLocalServer.addOrUpdatePluginMetadata(updatedMetadata)

            // For remote servers, we don't need to update MCPServer config in the same way,
            // as the bridge handles connection details directly from metadata.
            // If any specific server config were needed, it would be updated here.

            // 重新加载插件状态以更新UI
            loadPluginsFromMCPLocalServer()
        }
    }

    /**
     * 删除远程服务器
     */
    suspend fun removeRemoteServer(serverId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                mcpLocalServer.removeMCPServer(serverId)
                // 重新加载插件状态
                loadPluginsFromMCPLocalServer()
                Log.d(TAG, "远程服务器 $serverId 删除成功")
                true
            } catch (e: Exception) {
                Log.e(TAG, "删除远程服务器 $serverId 时出错", e)
                false
            }
        }
    }

    // ==================== 状态同步和管理 ====================

    /**
     * 同步桥接器中服务的实时运行状态
     */
    suspend fun syncBridgeStatus() {
        withContext(Dispatchers.IO) {
            Log.d(TAG, "开始从桥接器同步服务状态...")
            try {
                val bridge = com.ai.assistance.operit.data.mcp.plugins.MCPBridge.getInstance(context)
                val listResponse = bridge.listMcpServices()

                if (listResponse?.optBoolean("success", false) == true) {
                    val services = listResponse.optJSONObject("result")?.optJSONArray("services")
                    val activeServices = mutableSetOf<String>()
                    
                    if (services != null) {
                        for (i in 0 until services.length()) {
                            val service = services.optJSONObject(i)
                            val serviceName = service?.optString("name")
                            val isActive = service?.optBoolean("active", false) ?: false

                            if (!serviceName.isNullOrEmpty()) {
                                if (isActive) {
                                    activeServices.add(serviceName)
                                }
                                // 更新MCPLocalServer中的状态
                                mcpLocalServer.updateServerStatus(serverId = serviceName, active = isActive)
                            }
                        }
                    }
                    
                    // 对于已安装但不在活跃列表中的插件，确保其状态为 inactive
                    _installedPluginIds.value.forEach { pluginId ->
                        if (!activeServices.contains(pluginId)) {
                            mcpLocalServer.updateServerStatus(serverId = pluginId, active = false)
                        }
                    }
                    Log.d(TAG, "桥接器状态同步完成。活跃服务: ${activeServices.joinToString()}")
                } else {
                    Log.w(TAG, "从桥接器获取服务列表失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "同步桥接器状态时出错", e)
            }
        }
    }

    /**
     * 同步已安装状态
     */
    suspend fun syncInstalledStatus() {
        withContext(Dispatchers.IO) {
            try {
                // 重新从MCPLocalServer加载插件信息
                loadPluginsFromMCPLocalServer()
                Log.d(TAG, "同步插件安装状态完成，${_installedPluginIds.value.size} 个已安装插件")
            } catch (e: Exception) {
                Log.e(TAG, "同步安装状态失败", e)
            }
        }
    }

    /**
     * 更新所有服务器的安装状态（已废弃，使用loadPluginsFromMCPLocalServer代替）
     */
    @Deprecated("使用loadPluginsFromMCPLocalServer代替")
    private suspend fun updateInstalledStatus() {
        // 该方法已被loadPluginsFromMCPLocalServer替代
        loadPluginsFromMCPLocalServer()
    }

    /**
     * 初始化仓库
     */
    suspend fun initialize() {
        withContext(Dispatchers.IO) {
            // 重新加载插件状态
            loadPluginsFromMCPLocalServer()
        }
    }

    /**
     * 获取已安装插件的信息
     */
    fun getInstalledPluginInfo(pluginId: String): MCPLocalServer.PluginMetadata? {
        return mcpLocalServer.getPluginMetadata(pluginId)
    }

    /**
     * 手动刷新插件列表
     */
    suspend fun refreshPluginList() {
        withContext(Dispatchers.IO) {
            loadPluginsFromMCPLocalServer()
        }
    }

    /**
     * 为加载成功的插件注册工具
     *
     * @param successfulPluginIds 加载成功的插件ID列表
     */
    fun registerToolsForLoadedPlugins(successfulPluginIds: List<String>) {
        if (successfulPluginIds.isEmpty()) {
            Log.d(TAG, "没有成功加载的插件，无需注册工具")
            return
        }

        Log.d(TAG, "开始为 ${successfulPluginIds.size} 个插件注册工具: ${successfulPluginIds.joinToString()}")

        val mcpManager = MCPManager.getInstance(context)
        val toolHandler = AIToolHandler.getInstance(context)
        val mcpToolExecutor = MCPToolExecutor(context, mcpManager)

        successfulPluginIds.forEach { pluginId ->
            try {
                Log.d(TAG, "正在为插件 $pluginId 注册工具...")

                // 1. 获取插件元数据以确定类型
                val pluginMetadata = mcpLocalServer.getPluginMetadata(pluginId)
                if (pluginMetadata == null) {
                    Log.w(TAG, "在MCPLocalServer中找不到插件 $pluginId 的元数据")
                    return@forEach
                }

                val mcpPackage: MCPPackage?

                if (pluginMetadata.type == "remote") {
                    // --- 远程插件处理逻辑 ---
                    Log.d(TAG, "插件 $pluginId 是远程插件，使用端点 ${pluginMetadata.endpoint} 进行连接")
                    val remoteServerConfig = com.ai.assistance.operit.core.tools.mcp.MCPServerConfig(
                        name = pluginId,
                        endpoint = pluginMetadata.endpoint ?: "",
                        description = pluginMetadata.description,
                        capabilities = listOf("tools"),
                        extraData = emptyMap() // 远程服务器不需要本地执行参数
                    )
                    mcpManager.registerServer(pluginId, remoteServerConfig)
                    mcpPackage = MCPPackage.fromServer(context, remoteServerConfig)

                } else {
                    // --- 本地插件处理逻辑 (保持不变) ---
                    val localServerConfig = mcpLocalServer.getMCPServer(pluginId)
                    if (localServerConfig == null) {
                        Log.w(TAG, "在MCPLocalServer中找不到本地插件 $pluginId 的配置")
                        return@forEach
                    }
                    
                    val coreServerConfig = com.ai.assistance.operit.core.tools.mcp.MCPServerConfig(
                        name = pluginId,
                        endpoint = "", // 本地服务无固定端点
                        description = pluginMetadata.description,
                        capabilities = listOf("tools"),
                        extraData = mapOf(
                            "command" to localServerConfig.command,
                            "args" to localServerConfig.args.joinToString(" ")
                        )
                    )

                    mcpManager.registerServer(pluginId, coreServerConfig)
                    Log.d(TAG, "已在MCPManager中注册本地服务器: $pluginId")
                    mcpPackage = MCPPackage.fromServer(context, coreServerConfig)
                }

                if (mcpPackage == null) {
                    Log.w(TAG, "无法从服务器 $pluginId 获取MCP包，可能连接失败或服务未就绪")
                    return@forEach
                }

                // --- 通用工具注册逻辑 (保持不变) ---
                val toolPackage = mcpPackage.toToolPackage()

                toolPackage.tools.forEach { packageTool ->
                    val prefixedToolName = "$pluginId:${packageTool.name}"
                    
                    if (toolHandler.getToolExecutor(prefixedToolName) != null) {
                        Log.d(TAG, "工具 $prefixedToolName 已注册，跳过")
                        return@forEach
                    }

                    runBlocking {
                        toolHandler.registerTool(
                            name = prefixedToolName,
                            category = ToolCategory.FILE_READ,
                            executor = mcpToolExecutor,
                            descriptionGenerator = { tool ->
                                val baseDescription = packageTool.description
                                val paramsString = if (tool.parameters.isNotEmpty()) {
                                    "\nParameters: " + tool.parameters.joinToString(", ") { "${it.name}='${it.value}'" }
                                } else ""
                                baseDescription + paramsString
                            }
                        )
                    }
                    Log.i(TAG, "成功注册工具: $prefixedToolName")
                }
                Log.d(TAG, "插件 $pluginId 的工具注册完成，共 ${toolPackage.tools.size} 个")

            } catch (e: Exception) {
                Log.e(TAG, "为插件 $pluginId 注册工具时发生异常", e)
            }
        }
        Log.d(TAG, "所有插件的工具注册流程完成")
    }
}

// ==================== 数据类定义 ====================

/** 安装进度状态 */
sealed class InstallProgress {
    object Preparing : InstallProgress()
    data class Downloading(val progress: Int) : InstallProgress() // -1 表示未知进度
    data class Extracting(val progress: Int) : InstallProgress() // -1 表示未知进度
    object Finished : InstallProgress()
}

/** 安装结果 */
sealed class InstallResult {
    data class Success(val pluginPath: String) : InstallResult()
    data class Error(val message: String) : InstallResult()
}
