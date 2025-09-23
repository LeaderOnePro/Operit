package com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.api.GitHubApiService
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.api.GitHubComment
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubAuthBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import android.util.Log
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import com.ai.assistance.operit.ui.features.packages.utils.MCPPluginParser
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

/**
 * MCP市场ViewModel
 * 处理GitHub认证、MCP浏览、安装和发布
 */
class MCPMarketViewModel(
    private val context: Context,
    private val mcpRepository: MCPRepository
) : ViewModel() {

    @Serializable
    private data class MCPMetadata(
        val repositoryUrl: String,
        val installCommand: String,
        val category: String,
        val tags: String,
        val version: String
    )

    private val githubApiService = GitHubApiService(context)
    private val githubAuth = GitHubAuthPreferences.getInstance(context)

    // UI状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 安装进度状态
    private val _installingPlugins = MutableStateFlow<Set<String>>(emptySet())
    val installingPlugins: StateFlow<Set<String>> = _installingPlugins.asStateFlow()

    private val _installProgress = MutableStateFlow<Map<String, com.ai.assistance.operit.data.mcp.InstallProgress>>(emptyMap())
    val installProgress: StateFlow<Map<String, com.ai.assistance.operit.data.mcp.InstallProgress>> = _installProgress.asStateFlow()

    // 已安装插件
    val installedPluginIds: StateFlow<Set<String>> = mcpRepository.installedPluginIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // MCP市场数据
    private val _mcpIssues = MutableStateFlow<List<GitHubIssue>>(emptyList())

    // 搜索查询
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val mcpIssues: StateFlow<List<GitHubIssue>> =
        combine(_mcpIssues, _searchQuery) { issues, query ->
            if (query.isBlank()) {
                issues
            } else {
                val lowerCaseQuery = query.lowercase()
                issues.filter { issue ->
                    issue.title.lowercase().contains(lowerCaseQuery) ||
                    (issue.body?.lowercase()?.contains(lowerCaseQuery) == true) ||
                    issue.user.login.lowercase().contains(lowerCaseQuery) ||
                    issue.labels.any { it.name.lowercase().contains(lowerCaseQuery) }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 用户已发布的插件
    private val _userPublishedPlugins = MutableStateFlow<List<GitHubIssue>>(emptyList())
    val userPublishedPlugins: StateFlow<List<GitHubIssue>> = _userPublishedPlugins.asStateFlow()

    // 评论相关状态
    private val _issueComments = MutableStateFlow<Map<Int, List<GitHubComment>>>(emptyMap())
    val issueComments: StateFlow<Map<Int, List<GitHubComment>>> = _issueComments.asStateFlow()

    private val _isLoadingComments = MutableStateFlow<Set<Int>>(emptySet())
    val isLoadingComments: StateFlow<Set<Int>> = _isLoadingComments.asStateFlow()

    private val _isPostingComment = MutableStateFlow<Set<Int>>(emptySet())
    val isPostingComment: StateFlow<Set<Int>> = _isPostingComment.asStateFlow()

    // 用户头像缓存
    private val _userAvatarCache = MutableStateFlow<Map<String, String>>(emptyMap())
    val userAvatarCache: StateFlow<Map<String, String>> = _userAvatarCache.asStateFlow()

    // 草稿保存
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("mcp_publish_draft", Context.MODE_PRIVATE)
    
    // 发布草稿数据类
    data class PublishDraft(
        val title: String = "",
        val description: String = "",
        val repositoryUrl: String = "",
        val tags: String = "",
        val installCommand: String = "",
        val category: String = ""
    )
    
    // 当前草稿
    val publishDraft: PublishDraft
        get() = PublishDraft(
            title = sharedPrefs.getString("title", "") ?: "",
            description = sharedPrefs.getString("description", "") ?: "",
            repositoryUrl = sharedPrefs.getString("repositoryUrl", "") ?: "",
            tags = sharedPrefs.getString("tags", "") ?: "",
            installCommand = sharedPrefs.getString("installCommand", "") ?: "",
            category = sharedPrefs.getString("category", "") ?: ""
        )

    init {
        viewModelScope.launch {
            GitHubAuthBus.authCode.collect { code ->
                code?.let {
                    handleGitHubCallback(it)
                    // Reset the code in the bus to prevent re-triggering
                    GitHubAuthBus.postAuthCode(null)
                }
            }
        }
    }

    class Factory(
        private val context: Context,
        private val mcpRepository: MCPRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MCPMarketViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MCPMarketViewModel(context, mcpRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    companion object {
        private const val TAG = "MCPMarketViewModel"
        private const val MARKET_REPO_OWNER = "AAswordman"
        private const val MARKET_REPO_NAME = "OperitMCPMarket"
        private const val MCP_PLUGIN_LABEL = "mcp-plugin"
    }

    /**
     * 更新搜索查询
     */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    /**
     * 加载MCP市场数据
     */
    fun loadMCPMarketData() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val result = githubApiService.getRepositoryIssues(
                    owner = MARKET_REPO_OWNER,
                    repo = MARKET_REPO_NAME,
                    state = "open",
                    labels = MCP_PLUGIN_LABEL,
                    perPage = 50
                )

                result.fold(
                    onSuccess = { issues ->
                        _mcpIssues.value = issues
                    },
                    onFailure = { error ->
                        _errorMessage.value = "加载MCP市场数据失败: ${error.message}"
                        Log.e(TAG, "Failed to load MCP market data", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
                Log.e(TAG, "Network error while loading MCP market data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 从Issue安装MCP
     */
    fun installMCPFromIssue(issue: GitHubIssue) {
        viewModelScope.launch {
            try {
                // 解析Issue中的安装信息
                val installInfo = parseInstallationInfo(issue)
                Log.d(TAG, "Parsed installation info: $installInfo")
                
                if (installInfo != null) {
                    val pluginInfo = MCPPluginParser.parsePluginInfo(issue)
                    val pluginId = generateMCPId(issue)
                    
                    // 标记插件开始安装
                    _installingPlugins.value = _installingPlugins.value + pluginId
                    
                    // 获取作者头像，如果缓存中没有，则使用分享者的头像作为备用
                    val authorAvatarUrl = _userAvatarCache.value[pluginInfo.repositoryOwner] ?: issue.user.avatarUrl
                    
                    // 创建MCP服务器对象
                    val server = MCPLocalServer.PluginMetadata(
                        id = pluginId,
                        name = issue.title,
                        description = pluginInfo.description.ifBlank { issue.body?.take(200) ?: "" },
                        logoUrl = authorAvatarUrl,
                        author = pluginInfo.repositoryOwner.ifBlank { issue.user.login },
                        isInstalled = false,
                        version = "1.0.0",
                        updatedAt = issue.updated_at,
                        longDescription = issue.body ?: "",
                        repoUrl = installInfo.repoUrl ?: "",
                        type = "local"
                    )

                    // 安装MCP，带进度回调
                    val result = mcpRepository.installMCPServerWithObject(server) { progress ->
                        // 更新安装进度
                        _installProgress.value = _installProgress.value + (pluginId to progress)
                    }
                    
                    // 清除安装状态
                    _installingPlugins.value = _installingPlugins.value - pluginId
                    _installProgress.value = _installProgress.value - pluginId
                    
                    when (result) {
                        is com.ai.assistance.operit.data.mcp.InstallResult.Success -> {
                            Toast.makeText(
                                context,
                                "成功安装 ${issue.title}",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.i(TAG, "Successfully installed MCP: ${issue.title}")
                        }
                        is com.ai.assistance.operit.data.mcp.InstallResult.Error -> {
                            _errorMessage.value = "安装失败: ${result.message}"
                            Log.e(TAG, "Failed to install MCP ${issue.title}: ${result.message}")
                        }
                    }
                } else {
                    _errorMessage.value = "无法解析安装信息，请查看Issue详情手动安装"
                    Log.w(TAG, "Could not parse installation info from issue #${issue.number} ('${issue.title}'). URL: ${issue.html_url}")
                    Log.d(TAG, "Issue body that failed to parse:\n${issue.body}")
                }
            } catch (e: Exception) {
                // 确保清除安装状态
                val pluginId = generateMCPId(issue)
                _installingPlugins.value = _installingPlugins.value - pluginId
                _installProgress.value = _installProgress.value - pluginId
                
                _errorMessage.value = "安装失败: ${e.message}"
                Log.e(TAG, "Failed to install MCP from issue #${issue.number}", e)
            }
        }
    }

    /**
     * 发布MCP到市场
     */
    fun publishMCP(
        title: String,
        description: String,
        repoUrl: String,
        labels: List<String>
    ) {
        viewModelScope.launch {
            try {
                if (!githubAuth.isLoggedIn()) {
                    _errorMessage.value = "请先登录GitHub"
                    return@launch
                }

                _isLoading.value = true

                // 构建Issue内容
                val issueBody = buildMCPIssueBody(description, repoUrl)
                val issueLabels = (labels + MCP_PLUGIN_LABEL).distinct()

                val result = githubApiService.createIssue(
                    owner = MARKET_REPO_OWNER,
                    repo = MARKET_REPO_NAME,
                    title = title,
                    body = issueBody,
                    labels = issueLabels
                )

                result.fold(
                    onSuccess = { issue ->
                        Log.d(TAG, "Successfully created issue #${issue.number}")
                        Toast.makeText(
                            context,
                            "MCP插件发布成功！",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // 刷新市场数据
                        loadMCPMarketData()
                        
                        // 打开创建的Issue
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(issue.html_url))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to create issue", error)
                        _errorMessage.value = "发布失败: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "发布失败: ${e.message}"
                Log.e(TAG, "Failed to publish MCP", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 启动GitHub登录流程
     */
    fun initiateGitHubLogin(context: Context) {
        try {
            val authUrl = githubAuth.getAuthorizationUrl()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            _errorMessage.value = "启动登录失败: ${e.message}"
            Log.e(TAG, "Failed to initiate GitHub login", e)
        }
    }

    /**
     * 处理GitHub OAuth回调
     */
    fun handleGitHubCallback(code: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d(TAG, "Handling GitHub callback with code: $code")

                // 获取访问令牌
                val tokenResult = githubApiService.getAccessToken(code)
                
                tokenResult.fold(
                    onSuccess = { tokenResponse ->
                        Log.d(TAG, "Successfully obtained access token.")
                        // 获取用户信息
                        githubAuth.updateAccessToken(tokenResponse.access_token, tokenResponse.token_type)
                        
                        val userResult = githubApiService.getCurrentUser()
                        userResult.fold(
                            onSuccess = { user ->
                                Log.d(TAG, "Successfully fetched user info for ${user.login}")
                                githubAuth.saveAuthInfo(
                                    accessToken = tokenResponse.access_token,
                                    tokenType = tokenResponse.token_type,
                                    userInfo = user
                                )
                                
                                Toast.makeText(
                                    context,
                                    "登录成功，欢迎 ${user.login}！",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onFailure = { error ->
                                Log.e(TAG, "Failed to get user info", error)
                                _errorMessage.value = "获取用户信息失败: ${error.message}"
                            }
                        )
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to get access token", error)
                        _errorMessage.value = "登录失败: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception during GitHub callback handling", e)
                _errorMessage.value = "登录过程中发生错误: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 退出GitHub登录
     */
    fun logoutFromGitHub() {
        viewModelScope.launch {
            try {
                githubAuth.logout()
                Toast.makeText(context, "已退出登录", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                _errorMessage.value = "退出登录失败: ${e.message}"
                Log.e(TAG, "Failed to logout from GitHub", e)
            }
        }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 加载用户已发布的插件
     */
    fun loadUserPublishedPlugins() {
        viewModelScope.launch {
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = "请先登录GitHub"
                return@launch
            }

            _isLoading.value = true
            _errorMessage.value = null

            try {
                val userInfo = githubAuth.getCurrentUserInfo()
                if (userInfo == null) {
                    _errorMessage.value = "无法获取用户信息"
                    return@launch
                }

                val result = githubApiService.getRepositoryIssues(
                    owner = MARKET_REPO_OWNER,
                    repo = MARKET_REPO_NAME,
                    state = "all", // 获取所有状态的Issue
                    labels = MCP_PLUGIN_LABEL,
                    creator = userInfo.login, // 只获取当前用户创建的Issue
                    perPage = 100
                )

                result.fold(
                    onSuccess = { issues ->
                        _userPublishedPlugins.value = issues
                    },
                    onFailure = { error ->
                        _errorMessage.value = "加载已发布插件失败: ${error.message}"
                        Log.e(TAG, "Failed to load user published plugins", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
                Log.e(TAG, "Network error while loading user published plugins", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 更新已发布的插件信息
     */
    fun updatePublishedPlugin(
        issueNumber: Int,
        title: String,
        description: String,
        repositoryUrl: String,
        category: String,
        tags: String,
        installCommand: String,
        version: String = "v1"
    ) {
        viewModelScope.launch {
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = "请先登录GitHub"
                return@launch
            }

            _isLoading.value = true
            _errorMessage.value = null

            try {
                val body = buildMCPPublishIssueBody(
                    description = description,
                    repositoryUrl = repositoryUrl,
                    category = category,
                    tags = tags,
                    installCommand = installCommand,
                    version = version
                )

                val result = githubApiService.updateIssue(
                    owner = MARKET_REPO_OWNER,
                    repo = MARKET_REPO_NAME,
                    issueNumber = issueNumber,
                    title = title,
                    body = body
                )

                result.fold(
                    onSuccess = { updatedIssue ->
                        Log.d(TAG, "Successfully updated issue #${issueNumber}")
                        Toast.makeText(
                            context,
                            "插件信息更新成功！",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // 刷新用户发布的插件列表
                        loadUserPublishedPlugins()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to update issue #${issueNumber}", error)
                        _errorMessage.value = "更新失败: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "更新失败: ${e.message}"
                Log.e(TAG, "Failed to update published plugin", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 删除已发布的插件（关闭Issue）
     */
    fun deletePublishedPlugin(issueNumber: Int, title: String) {
        viewModelScope.launch {
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = "请先登录GitHub"
                return@launch
            }

            _isLoading.value = true
            _errorMessage.value = null

            try {
                val result = githubApiService.updateIssue(
                    owner = MARKET_REPO_OWNER,
                    repo = MARKET_REPO_NAME,
                    issueNumber = issueNumber,
                    state = "closed"
                )

                result.fold(
                    onSuccess = { _ ->
                        Log.d(TAG, "Successfully closed issue #${issueNumber}")
                        Toast.makeText(
                            context,
                            "插件 \"$title\" 已从市场移除",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // 立即更新本地状态，不需要重新请求服务器
                        val currentPlugins = _userPublishedPlugins.value.toMutableList()
                        val pluginIndex = currentPlugins.indexOfFirst { it.number == issueNumber }
                        if (pluginIndex != -1) {
                            currentPlugins[pluginIndex] = currentPlugins[pluginIndex].copy(state = "closed")
                            _userPublishedPlugins.value = currentPlugins
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to close issue #${issueNumber}", error)
                        _errorMessage.value = "删除失败: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "删除失败: ${e.message}"
                Log.e(TAG, "Failed to delete published plugin", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 重新开放已关闭的插件
     */
    fun reopenPublishedPlugin(issueNumber: Int, title: String) {
        viewModelScope.launch {
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = "请先登录GitHub"
                return@launch
            }

            _isLoading.value = true
            _errorMessage.value = null

            try {
                val result = githubApiService.updateIssue(
                    owner = MARKET_REPO_OWNER,
                    repo = MARKET_REPO_NAME,
                    issueNumber = issueNumber,
                    state = "open"
                )

                result.fold(
                    onSuccess = { _ ->
                        Log.d(TAG, "Successfully reopened issue #${issueNumber}")
                        Toast.makeText(
                            context,
                            "插件 \"$title\" 已重新发布到市场",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // 立即更新本地状态，不需要重新请求服务器
                        val currentPlugins = _userPublishedPlugins.value.toMutableList()
                        val pluginIndex = currentPlugins.indexOfFirst { it.number == issueNumber }
                        if (pluginIndex != -1) {
                            currentPlugins[pluginIndex] = currentPlugins[pluginIndex].copy(state = "open")
                            _userPublishedPlugins.value = currentPlugins
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to reopen issue #${issueNumber}", error)
                        _errorMessage.value = "重新发布失败: ${error.message}"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "重新发布失败: ${e.message}"
                Log.e(TAG, "Failed to reopen published plugin", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 从Issue内容解析插件信息用于编辑
     */
    fun parsePluginInfoFromIssue(issue: GitHubIssue): PublishDraft {
        val body = issue.body ?: return PublishDraft(title = issue.title)

        // 优先尝试解析隐藏在评论中的JSON元数据
        parseMCPMetadata(body)?.let { metadata ->
            val descriptionPattern = Regex("""\*\*描述:\*\*\s*(.+?)(?=\n\*\*|\n##|\Z)""", RegexOption.DOT_MATCHES_ALL)
            val description = descriptionPattern.find(body)?.groupValues?.get(1)?.trim() ?: ""

            return PublishDraft(
                title = issue.title,
                description = description,
                repositoryUrl = metadata.repositoryUrl,
                tags = metadata.tags,
                installCommand = metadata.installCommand,
                category = metadata.category
            )
        }
        
        // 如果JSON不存在，说明是格式错误或非常旧的Issue，直接返回一个基础的草稿用于编辑
        Log.w(TAG, "Could not parse plugin info from issue #${issue.number}. No valid JSON metadata found.")
        return PublishDraft(title = issue.title, description = "无法解析插件描述，请手动填写。")
    }

    /**
     * 保存发布草稿
     */
    fun saveDraft(
        title: String,
        description: String,
        repositoryUrl: String,
        tags: String,
        installCommand: String,
        category: String
    ) {
        sharedPrefs.edit().apply {
            putString("title", title)
            putString("description", description)
            putString("repositoryUrl", repositoryUrl)
            putString("tags", tags)
            putString("installCommand", installCommand)
            putString("category", category)
            apply()
        }
    }

    /**
     * 清空草稿
     */
    fun clearDraft() {
        sharedPrefs.edit().clear().apply()
    }

    /**
     * 发布MCP到市场
     */
    suspend fun publishMCP(
        title: String,
        description: String,
        repositoryUrl: String,
        category: String,
        tags: String,
        installCommand: String,
        version: String = "v1"
    ): Boolean {
        return try {
            val body = buildMCPPublishIssueBody(
                description = description,
                repositoryUrl = repositoryUrl,
                category = category,
                tags = tags,
                installCommand = installCommand,
                version = version
            )
            
            val result = githubApiService.createIssue(
                owner = MARKET_REPO_OWNER,
                repo = MARKET_REPO_NAME,
                title = title,
                body = body,
                labels = listOf(MCP_PLUGIN_LABEL)
            )
            
            result.isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Failed to publish MCP", e)
            false
        }
    }

    /**
     * 构建MCP发布Issue内容
     */
    private fun buildMCPPublishIssueBody(
        description: String,
        repositoryUrl: String,
        category: String,
        tags: String,
        installCommand: String,
        version: String = "v1"
    ): String {
        return buildString {
            // 嵌入包含所有机器可读信息的JSON数据块
            val metadata = MCPMetadata(
                repositoryUrl = repositoryUrl,
                installCommand = installCommand,
                category = category,
                tags = tags,
                version = version
            )
            try {
                val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
                val metadataJson = json.encodeToString(metadata)
                appendLine("<!-- operit-mcp-json: $metadataJson -->")
            } catch(e: Exception) {
                Log.e(TAG, "Failed to serialize MCP metadata", e)
            }

            // 软件解析版本号标记
            appendLine("<!-- operit-parser-version: $version -->")
            appendLine()
            
            appendLine("## 📋 插件信息")
            appendLine()
            appendLine("**描述:** $description")
            appendLine()
            if (repositoryUrl.isNotBlank()) {
                appendLine("## 🔗 仓库信息")
                appendLine()
                appendLine("**仓库地址:** $repositoryUrl")
                appendLine()
            }

            if (installCommand.isNotBlank()) {
                appendLine("## ⚡ 快速安装")
                appendLine()
                appendLine("```bash")
                appendLine(installCommand)
                appendLine("```")
                appendLine()
            }
            
            if (repositoryUrl.isNotBlank()) {
                appendLine("## 📦 安装方式")
                appendLine()
                appendLine("### 方式一：从仓库导入")
                appendLine("1. 打开 Operit MCP 配置页面")
                appendLine("2. 点击「导入」→「从仓库导入」")
                appendLine("3. 输入仓库地址：`$repositoryUrl`")
                appendLine("4. 配置插件名称并导入")
                appendLine()
            }

            if (installCommand.isNotBlank()) {
                appendLine("### 方式二：命令行安装")
                appendLine("```bash")
                appendLine(installCommand)
                appendLine("```")
                appendLine()
            }
            
            appendLine("## 🛠️ 技术信息")
            appendLine()
            appendLine("| 项目 | 值 |")
            appendLine("|------|-----|")
            appendLine("| 发布平台 | Operit MCP 市场 |")
            appendLine("| 解析版本 | 1.0 |")
            appendLine("| 发布时间 | ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))} |")
            appendLine("| 状态 | ⏳ Pending Review |")
            appendLine()
            appendLine("---")
            appendLine("*🤖 此Issue由 Operit 自动生成，等待管理员审核通过*")
        }
    }

    /**
     * 解析Issue中的安装信息
     */
    private fun parseInstallationInfo(issue: GitHubIssue): InstallationInfo? {
        val body = issue.body ?: return null

        // 优先尝试解析隐藏的JSON元数据
        val metadata = parseMCPMetadata(body)
        if (metadata != null) {
            val repoUrlValid = metadata.repositoryUrl.startsWith("http")
            // 校验安装命令，确保不为空且包含有效字符（不只是符号）
            val installCommandValid = metadata.installCommand.any { it.isLetterOrDigit() }

            if (repoUrlValid || installCommandValid) {
                Log.d(TAG, "Parsed installation info from JSON for issue #${issue.number}")
                return InstallationInfo(
                    repoUrl = if (repoUrlValid) metadata.repositoryUrl else null,
                    installCommand = if (installCommandValid) metadata.installCommand else null,
                    installationType = if (repoUrlValid) "github" else "command"
                )
            } else {
                Log.w(TAG, "Found JSON metadata in issue #${issue.number}, but both repositoryUrl ('${metadata.repositoryUrl}') and installCommand ('${metadata.installCommand}') are invalid.")
                return null
            }
        }
        
        Log.w(TAG, "Could not parse installation info from issue #${issue.number}. No valid JSON metadata found in body.")
        return null
    }

    /**
     * 解析隐藏在Issue Body中的MCP元数据JSON
     */
    private fun parseMCPMetadata(body: String): MCPMetadata? {
        val metadataPattern = Regex("""<!-- operit-mcp-json: (\{.*?\}) -->""", RegexOption.DOT_MATCHES_ALL)
        val match = metadataPattern.find(body)
        
        return match?.let {
            val jsonString = it.groupValues[1]
            try {
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString<MCPMetadata>(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse MCP metadata JSON from issue body.", e)
                null
            }
        }
    }

    /**
     * 生成MCP ID
     */
    private fun generateMCPId(issue: GitHubIssue): String {
        val pluginInfo = MCPPluginParser.parsePluginInfo(issue)
        return pluginInfo.title.replace("[^a-zA-Z0-9_]".toRegex(), "_")
    }

    /**
     * 构建MCP Issue内容
     */
    private fun buildMCPIssueBody(description: String, repoUrl: String): String {
        return buildString {
            appendLine("## MCP 插件描述")
            appendLine()
            appendLine(description)
            appendLine()
            
            if (repoUrl.isNotBlank()) {
                appendLine("## 安装信息")
                appendLine()
                appendLine("**仓库地址:** $repoUrl")
                appendLine()
                appendLine("### 安装方式")
                appendLine("1. 在 Operit MCP 配置页面点击「导入」")
                appendLine("2. 选择「从仓库导入」")
                appendLine("3. 输入仓库地址: `$repoUrl`")
                appendLine("4. 设置插件名称并点击导入")
                appendLine()
            }
            
            appendLine("## 技术信息")
            appendLine("- **发布平台:** Operit MCP 市场")
            appendLine("- **发布时间:** ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}")
            appendLine()
            appendLine("---")
            appendLine("*此Issue由 Operit 自动生成*")
        }
    }

    /**
     * 安装信息数据类
     */
    private data class InstallationInfo(
        val repoUrl: String? = null,
        val installCommand: String? = null,
        val installationType: String
    )

    /**
     * 加载Issue评论
     */
    fun loadIssueComments(issueNumber: Int) {
        viewModelScope.launch {
            try {
                _isLoadingComments.value = _isLoadingComments.value + issueNumber
                
                val result = githubApiService.getIssueComments(
                    owner = MARKET_REPO_OWNER,
                    repo = MARKET_REPO_NAME,
                    issueNumber = issueNumber,
                    perPage = 100
                )

                result.fold(
                    onSuccess = { comments ->
                        val currentComments = _issueComments.value.toMutableMap()
                        currentComments[issueNumber] = comments
                        _issueComments.value = currentComments
                        Log.d(TAG, "Successfully loaded ${comments.size} comments for issue #$issueNumber")
                    },
                    onFailure = { error ->
                        _errorMessage.value = "加载评论失败: ${error.message}"
                        Log.e(TAG, "Failed to load comments for issue #$issueNumber", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "加载评论时发生错误: ${e.message}"
                Log.e(TAG, "Exception while loading comments for issue #$issueNumber", e)
            } finally {
                _isLoadingComments.value = _isLoadingComments.value - issueNumber
            }
        }
    }

    /**
     * 发布评论
     */
    fun postComment(issueNumber: Int, commentBody: String) {
        viewModelScope.launch {
            if (!githubAuth.isLoggedIn()) {
                _errorMessage.value = "请先登录GitHub"
                return@launch
            }

            if (commentBody.isBlank()) {
                _errorMessage.value = "评论内容不能为空"
                return@launch
            }

            try {
                _isPostingComment.value = _isPostingComment.value + issueNumber
                
                val result = githubApiService.createIssueComment(
                    owner = MARKET_REPO_OWNER,
                    repo = MARKET_REPO_NAME,
                    issueNumber = issueNumber,
                    body = commentBody
                )

                result.fold(
                    onSuccess = { newComment ->
                        // 将新评论添加到现有评论列表
                        val currentComments = _issueComments.value.toMutableMap()
                        val existingComments = currentComments[issueNumber] ?: emptyList()
                        currentComments[issueNumber] = existingComments + newComment
                        _issueComments.value = currentComments
                        
                        Toast.makeText(
                            context,
                            "评论发布成功！",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.d(TAG, "Successfully posted comment to issue #$issueNumber")
                    },
                    onFailure = { error ->
                        _errorMessage.value = "发布评论失败: ${error.message}"
                        Log.e(TAG, "Failed to post comment to issue #$issueNumber", error)
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = "发布评论时发生错误: ${e.message}"
                Log.e(TAG, "Exception while posting comment to issue #$issueNumber", e)
            } finally {
                _isPostingComment.value = _isPostingComment.value - issueNumber
            }
        }
    }

    /**
     * 获取Issue的评论列表
     */
    fun getCommentsForIssue(issueNumber: Int): List<GitHubComment> {
        return _issueComments.value[issueNumber] ?: emptyList()
    }

    /**
     * 检查是否正在加载评论
     */
    fun isLoadingCommentsForIssue(issueNumber: Int): Boolean {
        return issueNumber in _isLoadingComments.value
    }

    /**
     * 检查是否正在发布评论
     */
    fun isPostingCommentForIssue(issueNumber: Int): Boolean {
        return issueNumber in _isPostingComment.value
    }

    /**
     * 获取用户头像URL
     */
    fun getUserAvatarUrl(username: String): String? {
        return _userAvatarCache.value[username]
    }

    /**
     * 缓存用户头像URL
     */
    fun fetchUserAvatar(username: String) {
        if (username.isBlank() || _userAvatarCache.value.containsKey(username)) {
            return // 已经缓存或用户名为空
        }

        viewModelScope.launch {
            try {
                val result = githubApiService.getUser(username)
                result.fold(
                    onSuccess = { user ->
                        val currentCache = _userAvatarCache.value.toMutableMap()
                        currentCache[username] = user.avatarUrl
                        _userAvatarCache.value = currentCache
                    },
                    onFailure = { error ->
                        Log.w(TAG, "Failed to fetch avatar for user $username: ${error.message}")
                        // 可以设置一个默认头像URL或者不做任何操作
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "Exception while fetching avatar for user $username", e)
            }
        }
    }
} 