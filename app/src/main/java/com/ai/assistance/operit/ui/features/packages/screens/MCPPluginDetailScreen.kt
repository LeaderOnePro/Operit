package com.ai.assistance.operit.ui.features.packages.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.api.GitHubIssue
import com.ai.assistance.operit.data.api.GitHubComment
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.preferences.GitHubAuthPreferences
import com.ai.assistance.operit.data.preferences.GitHubUser
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.features.packages.screens.mcp.viewmodel.MCPMarketViewModel
import com.ai.assistance.operit.ui.features.packages.utils.MCPPluginParser
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPPluginDetailScreen(
    issue: GitHubIssue,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mcpRepository = remember { MCPRepository(context.applicationContext) }
    val viewModel: MCPMarketViewModel = viewModel(
        factory = MCPMarketViewModel.Factory(context.applicationContext, mcpRepository)
    )

    // GitHub认证状态
    val githubAuth = remember { GitHubAuthPreferences.getInstance(context) }
    val isLoggedIn by githubAuth.isLoggedInFlow.collectAsState(initial = false)
    val currentUser by githubAuth.userInfoFlow.collectAsState(initial = null)

    // 评论状态
    val comments by viewModel.issueComments.collectAsState()
    val isLoadingComments by viewModel.isLoadingComments.collectAsState()
    val isPostingComment by viewModel.isPostingComment.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // 插件信息
    val pluginInfo = remember(issue) {
        MCPPluginParser.parsePluginInfo(issue)
    }

    // 评论输入状态
    var commentText by remember { mutableStateOf("") }
    var showCommentDialog by remember { mutableStateOf(false) }

    // 加载评论
    LaunchedEffect(issue.number) {
        viewModel.loadIssueComments(issue.number)
    }

    // 错误处理
    errorMessage?.let { error ->
        LaunchedEffect(error) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    CustomScaffold(
        floatingActionButton = {
            if (isLoggedIn) {
                FloatingActionButton(
                    onClick = { showCommentDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Comment, contentDescription = "添加评论")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 插件信息卡片
            item {
                PluginInfoCard(
                    issue = issue,
                    pluginInfo = pluginInfo,
                    onInstall = {
                        scope.launch {
                            viewModel.installMCPFromIssue(issue)
                        }
                    },
                    viewModel = viewModel
                )
            }

            // 评论区域标题
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "评论 (${comments[issue.number]?.size ?: 0})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (isLoadingComments.contains(issue.number)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    viewModel.loadIssueComments(issue.number)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新评论")
                        }
                    }
                }
            }

            // 评论列表
            val issueComments = comments[issue.number] ?: emptyList()
            if (issueComments.isEmpty() && !isLoadingComments.contains(issue.number)) {
                item {
                    EmptyCommentsCard()
                }
            } else {
                items(issueComments) { comment ->
                    CommentCard(comment = comment)
                }
            }
        }
    }

    // 评论输入对话框
    if (showCommentDialog) {
        CommentInputDialog(
            commentText = commentText,
            onCommentTextChange = { commentText = it },
            onDismiss = { 
                showCommentDialog = false
                commentText = ""
            },
            onPost = {
                if (commentText.isNotBlank()) {
                    scope.launch {
                        viewModel.postComment(issue.number, commentText)
                        showCommentDialog = false
                        commentText = ""
                    }
                }
            },
            isPosting = isPostingComment.contains(issue.number)
        )
    }
}

@Composable
private fun PluginInfoCard(
    issue: GitHubIssue,
    pluginInfo: MCPPluginParser.ParsedPluginInfo,
    onInstall: () -> Unit,
    viewModel: MCPMarketViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题和作者
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = issue.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 显示作者（仓库所有者）
                        if (pluginInfo.repositoryOwner.isNotBlank()) {
                            // 获取用户头像
                            LaunchedEffect(pluginInfo.repositoryOwner) {
                                viewModel.fetchUserAvatar(pluginInfo.repositoryOwner)
                            }
                            
                            val avatarUrl by viewModel.userAvatarCache.collectAsState()
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val userAvatarUrl = avatarUrl[pluginInfo.repositoryOwner]
                                if (userAvatarUrl != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(userAvatarUrl),
                                        contentDescription = "作者头像",
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "作者: ${pluginInfo.repositoryOwner}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        
                        // 显示分享者
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(issue.user.avatarUrl),
                                contentDescription = "分享者头像",
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "分享者: ${issue.user.login}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        // 如果有仓库链接，显示仓库信息
                        if (pluginInfo.repositoryUrl.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "仓库: ${pluginInfo.repositoryUrl.substringAfter("github.com/")}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                
                // 状态标签
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (issue.state) {
                        "open" -> Color(0xFF22C55E).copy(alpha = 0.1f)
                        else -> Color(0xFF64748B).copy(alpha = 0.1f)
                    }
                ) {
                    Text(
                        text = when (issue.state) {
                            "open" -> "可用"
                            else -> "已关闭"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when (issue.state) {
                            "open" -> Color(0xFF22C55E)
                            else -> Color(0xFF64748B)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // 描述
            if (pluginInfo.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = pluginInfo.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }



            // 时间信息
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "创建于: ${formatDate(issue.created_at)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "更新于: ${formatDate(issue.updated_at)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 安装按钮
            if (issue.state == "open") {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onInstall,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("安装插件")
                }
            }
        }
    }
}

@Composable
private fun CommentCard(comment: GitHubComment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 评论者信息
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = rememberAsyncImagePainter(comment.user.avatarUrl),
                    contentDescription = "评论者头像",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = comment.user.login,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatDate(comment.created_at),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 评论内容
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = comment.body,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun EmptyCommentsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Comment,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "暂无评论",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "成为第一个评论的人吧！",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun CommentInputDialog(
    commentText: String,
    onCommentTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onPost: () -> Unit,
    isPosting: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加评论") },
        text = {
            Column {
                Text("分享您的想法或提出问题：")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = commentText,
                    onValueChange = onCommentTextChange,
                    placeholder = { Text("输入评论内容...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 5,
                    enabled = !isPosting
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onPost,
                enabled = commentText.isNotBlank() && !isPosting
            ) {
                if (isPosting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("发布")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isPosting
            ) {
                Text("取消")
            }
        }
    )
}

private fun formatDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        dateString
    }
} 