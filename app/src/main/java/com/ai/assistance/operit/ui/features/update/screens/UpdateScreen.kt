package com.ai.assistance.operit.ui.features.update.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class UpdateInfo(
    val version: String,
    val date: String,
    val title: String,
    val description: String,
    val highlights: List<String>,
    val allChanges: List<String>,
    val isLatest: Boolean = false,
    val downloadUrl: String = "",
    val releaseUrl: String = ""
)

val updates = listOf(
    UpdateInfo(
        version = "v1.2.3",
        date = "2025-07-25",
        title = "记忆库升级与AI能力增强",
        description = "使用记忆库替代了问题库，让AI拥有了更高级的记忆检索和记录功能，用户也可以自行编辑和链接记忆节点。",
        highlights = listOf(
            "🧠 记忆库功能：替代问题库，提供高级记忆检索",
            "🎯 AI强制思考：提高工具调用能力，支持qwen3和Claude",
            "🎭 提示词市场：预设提示咒语",
            "🎙️ 语音悬浮窗：全自动语音对话模式",
            "🔧 界面优化：大幅改进对话界面按钮"
        ),
        allChanges = listOf(
            "增加tts使用外部http接口",
            "增加单个消息删除功能", 
            "更新软件包的ts type部分",
            "修复对话回溯和对话中记忆错乱的问题",
            "修复标准权限下文件打开和分享的问题",
            "修复文件apply出现的截断问题",
            "阻止http请求大文件导致的闪退问题",
            "修复ai输出的链接无法点击的问题",
            "修复在安卓8的版本上闪退的问题",
            "修复部分情况ai输出消息不全的问题",
            "统计消息的窗口大小计算修复",
            "增加临时文件夹的nomedia"
        ),
        isLatest = true,
        downloadUrl = "https://github.com/AAswordman/Operit/releases/tag/v1.2.3",
        releaseUrl = "https://github.com/AAswordman/Operit/releases/tag/v1.2.3"
    ),
    UpdateInfo(
        version = "v1.2.2",
        date = "2025-07-14", 
        title = "UI自动化与无障碍功能优化",
        description = "UI自动化能力显著增强，修复了多项关键Bug，提升了整体稳定性。",
        highlights = listOf(
            "🤖 UI自动化增强：新增高效UI操作工具",
            "♿ 无障碍功能：回归与优化无障碍点击功能",
            "🐛 Bug修复：修复邀请码识别和首次启动闪退",
            "⚡ 性能优化：大幅提升操作速度，节省Token消耗"
        ),
        allChanges = listOf(
            "修复邀请码在进软件的识别失败",
            "修复第一次启动时切换进聊天界面闪退的bug", 
            "增加无障碍模式下也能通过工具正常启动应用和自动操作",
            "修复无障碍模式获取UI的bug",
            "新增高效UI操作工具：引入全新的UI工具，能够智能执行一连串的点击操作",
            "加回与主包隔离的无障碍点击功能，用户可自行选择安装使用",
            "修复无障碍模式下的输入文本异常",
            "美化点击和滑动操作的视觉反馈效果"
        ),
        downloadUrl = "https://github.com/AAswordman/Operit/releases/tag/v1.2.2",
        releaseUrl = "https://github.com/AAswordman/Operit/releases/tag/v1.2.2"
    ),
    UpdateInfo(
        version = "v1.1.5",
        date = "2025-06-04",
        title = "Web开发支持与多模型兼容",
        description = "新增Web开发工作空间，支持多种AI模型，增加更多扩展包功能。",
        highlights = listOf(
            "🌐 Web开发支持：新增对话工作空间，AI生成网页",
            "📱 一键打包：将AI生成的Web内容打包为APP",
            "🤖 多模型支持：新增Gemini、OpenRouter、硅基流动等",
            "🔍 搜索引擎扩展：支持Bing、Baidu、Sogou、Quark",
            "📝 Writer插件：更高级的写入操作"
        ),
        allChanges = listOf(
            "新增对话工作空间，让ai编辑生成网页",
            "将AI生成的Web内容一键打包为APP的功能，支持Android & Windows",
            "新增Gemini模型支持，集成OpenRouter、硅基流动等模型供应商选项",
            "支持为不同聊天场景配置独立模型设置",
            "新增支持的搜索引擎：Bing、Baidu、Sogou、Quark",
            "新增Writer插件用于更高级的写入操作", 
            "新增AI直接执行Termux命令和Shell脚本的功能包",
            "重构路由，修复界面异常导航",
            "增加滑动打开历史记录",
            "修复文件管理器里文件显示日期错误",
            "修复思考过程闪烁"
        ),
        downloadUrl = "https://github.com/AAswordman/Operit/releases/tag/v1.1.5",
        releaseUrl = "https://github.com/AAswordman/Operit/releases/tag/v1.1.5"
    ),
    UpdateInfo(
        version = "v1.1.3",
        date = "2025-05-28",
        title = "提示词配置与历史记录功能",
        description = "自定义多个提示词配置，增强历史记录功能，大幅优化界面体验。",
        highlights = listOf(
            "💬 提示词功能：自定义多个提示词配置",
            "📚 历史记录：编辑重发、备份导入功能", 
            "🎨 界面优化：手势支持、平板显示改进",
            "🔐 权限分级：权限层次分级和root支持",
            "🛠️ 工具增强：时间包、shell执行器、ffmpeg执行器"
        ),
        allChanges = listOf(
            "自定义多个提示词配置",
            "模型参数自定义和开启关闭",
            "历史记录编辑与重发",
            "修改聊天历史储存逻辑（更稳定，支持旧版本迁移）",
            "新增聊天记录备份和导入",
            "菜单界面手势支持",
            "平板显示改进", 
            "设置界面改进",
            "提升聊天界面和侧边栏流畅度",
            "点击返回按钮支持分层返回",
            "处理消息时可继续输入",
            "包管理界面更直观"
        ),
        downloadUrl = "https://github.com/AAswordman/Operit/releases/tag/v1.1.3",
        releaseUrl = "https://github.com/AAswordman/Operit/releases/tag/v1.1.3"
    ),
    UpdateInfo(
        version = "v1.1.2", 
        date = "2025-05-19",
        title = "界面优化与功能增强",
        description = "优化配置界面，增加记忆总结功能，支持自定义主题和系统提示词。",
        highlights = listOf(
            "🎨 主题自定义：自定义主题色和背景",
            "🧠 记忆总结：在对话中体现记忆总结功能",
            "📸 图片解析：增加输出对图片的解析功能",
            "🐍 Python支持：mcp插件支持python包",
            "⚙️ 界面优化：配置界面和侧边栏修改"
        ),
        allChanges = listOf(
            "优化了进入的配置界面",
            "修复执行工具的显示错误",
            "现在双击才能退出应用",
            "输入换行支持", 
            "用户偏好设置增加引导界面自定义",
            "增加记忆总结功能，在对话中体现",
            "自定义模型参数和自定义系统提示词",
            "自定义主题色和背景",
            "api接口地址现在不作强制要求了",
            "界面侧边栏修改"
        ),
        downloadUrl = "https://github.com/AAswordman/Operit/releases/tag/v1.1.2",
        releaseUrl = "https://github.com/AAswordman/Operit/releases/tag/v1.1.2"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen() {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        itemsIndexed(updates) { index, update ->
            UpdateCard(
                updateInfo = update,
                isFirst = index == 0,
                onOpenRelease = { url ->
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(url)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun UpdateCard(
    updateInfo: UpdateInfo,
    isFirst: Boolean = false,
    onOpenRelease: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (updateInfo.isLatest) 8.dp else 4.dp,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (updateInfo.isLatest) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (updateInfo.isLatest) 6.dp else 2.dp
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // 版本头部
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 版本标签
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (updateInfo.isLatest) 
                            MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (updateInfo.isLatest) Icons.Default.Star else Icons.Default.Tag,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (updateInfo.isLatest) 
                                    MaterialTheme.colorScheme.onPrimary 
                                else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = updateInfo.version,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (updateInfo.isLatest) 
                                    MaterialTheme.colorScheme.onPrimary 
                                else MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    
                    if (updateInfo.isLatest) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.padding(0.dp)
                        ) {
                            Text(
                                text = "最新",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // 日期
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = updateInfo.date,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 标题和描述
            Text(
                text = updateInfo.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = updateInfo.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 主要亮点
            Text(
                text = "✨ 主要亮点",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            updateInfo.highlights.forEach { highlight ->
                Text(
                    text = highlight,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // 展开查看更多
            if (updateInfo.allChanges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isExpanded) "收起详细更新" else "查看详细更新 (${updateInfo.allChanges.size} 项)",
                        fontWeight = FontWeight.Medium
                    )
                }
                
                if (isExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "📋 完整更新内容",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    updateInfo.allChanges.forEach { change ->
                        Text(
                            text = "• $change",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 4.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            // 底部操作按钮
            if (updateInfo.releaseUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onOpenRelease(updateInfo.releaseUrl) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "查看发布",
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    if (updateInfo.downloadUrl.isNotEmpty()) {
                        Button(
                            onClick = { onOpenRelease(updateInfo.downloadUrl) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "下载",
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}