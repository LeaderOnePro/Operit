package com.ai.assistance.operit.ui.features.startup.screens

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.mcp.MCPLocalServer
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.mcp.plugins.MCPStarter
import com.ai.assistance.operit.ui.features.startup.components.SmoothLinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 表示插件加载状态的枚举 */
enum class PluginStatus {
    WAITING, // 等待加载
    LOADING, // 正在加载
    SUCCESS, // 加载成功
    FAILED // 加载失败
}

/** 表示单个插件的加载信息 */
data class PluginInfo(
        val id: String,
        val displayName: String,
        var status: PluginStatus = PluginStatus.WAITING,
        var message: String = ""
) {
    val shortName: String
        get() = id.split("/").lastOrNull() ?: id
}

/**
 * 插件加载屏幕
 *
 * 在应用启动时显示插件加载进度的全屏界面
 */
@Composable
fun PluginLoadingScreen(
        isVisible: Boolean,
        progress: Float,
        message: String,
        pluginsStarted: Int,
        pluginsTotal: Int,
        pluginsList: List<PluginInfo>,
        isExpanded: Boolean,
        onToggleExpansion: () -> Unit,
        onSkip: () -> Unit = {},
        modifier: Modifier = Modifier
) {
    AnimatedVisibility(
            visible = isVisible,
            enter =
                    fadeIn(
                            initialAlpha = 0f,
                            animationSpec = androidx.compose.animation.core.tween(500)
                    ),
            exit =
                    fadeOut(
                            targetAlpha = 0f,
                            animationSpec = androidx.compose.animation.core.tween(800)
                    )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            AnimatedContent(targetState = isExpanded, modifier = Modifier.align(if(isExpanded) Alignment.BottomCenter else Alignment.TopEnd), label = "") { expanded ->
                if (expanded) {
                    ExpandedLoadingView(progress, message, pluginsStarted, pluginsTotal, pluginsList, onSkip, onCollapse = onToggleExpansion)
                } else {
                    DraggableCollapsedIndicator(progress, pluginsStarted, pluginsTotal, onClick = onToggleExpansion)
                }
            }
        }
    }
}

@Composable
private fun DraggableCollapsedIndicator(
    progress: Float,
    pluginsStarted: Int,
    pluginsTotal: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offset += dragAmount
                }
            }
    ) {
        CollapsedLoadingIndicator(
            progress = progress,
            pluginsStarted = pluginsStarted,
            pluginsTotal = pluginsTotal,
            onClick = onClick
        )
    }
}

@Composable
private fun CollapsedLoadingIndicator(
    progress: Float,
    pluginsStarted: Int,
    pluginsTotal: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp)
        ) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 3.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = "$pluginsStarted/$pluginsTotal",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ExpandedLoadingView(
    progress: Float,
    message: String,
    pluginsStarted: Int,
    pluginsTotal: Int,
    pluginsList: List<PluginInfo>,
    onSkip: () -> Unit,
    onCollapse: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 450.dp), // Constrain height
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)) {
            // 折叠按钮
            IconButton(onClick = onCollapse, modifier = Modifier
                .align(Alignment.TopStart)
                .padding(0.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Collapse")
            }
            // 跳过加载文本 - 放在右上角
            Text(
                    text = stringResource(id = R.string.plugin_skip),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clickable {
                                    onSkip()
                                }
            )

            // 主要内容区域
            Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
            ) {
                // 应用名称/Logo
                Text(
                        text = stringResource(id = R.string.plugin_app_name),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 32.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 使用平滑过渡的进度条组件
                SmoothLinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        height = 8.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        progressColor = MaterialTheme.colorScheme.primary,
                        intermediateSteps = 20, // 增加中间步骤数量，使过渡更加平滑
                        stepDuration = 50 // 减少每步时长，保持总体流畅感
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 简洁的状态消息
                Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 总插件统计
                Text(
                        text =
                                stringResource(
                                        id = R.string.plugin_status,
                                        pluginsStarted,
                                        pluginsTotal
                                ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                )

                // 移除此处的跳过按钮
                Spacer(modifier = Modifier.height(16.dp))

                // 插件列表
                if (pluginsList.isNotEmpty()) {
                    Text(
                            text = stringResource(id = R.string.plugin_loading_status_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 插件加载状态列表
                    LazyColumn(
                            modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .heightIn(max = 200.dp) // reduce height for smaller card
                    ) {
                        items(pluginsList) { plugin ->
                            PluginStatusItem(
                                    plugin = plugin,
                                    modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 底部版权信息
                Text(
                        text = stringResource(id = R.string.plugin_copyright),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 单个插件状态项 */
@Composable
fun PluginStatusItem(plugin: PluginInfo, modifier: Modifier = Modifier) {
    val animatedProgress by
            animateFloatAsState(
                    targetValue = if (plugin.status == PluginStatus.LOADING) 1f else 0f,
                    label = "loading_progress"
            )

    Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.padding(vertical = 4.dp)
    ) {
        // 状态图标或加载指示器
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
            when (plugin.status) {
                PluginStatus.WAITING -> {
                    Box(
                            modifier =
                                    Modifier.size(10.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
                PluginStatus.LOADING -> {
                    CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                    )
                }
                PluginStatus.SUCCESS -> {
                    Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription =
                                    stringResource(id = R.string.plugin_loading_success),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                    )
                }
                PluginStatus.FAILED -> {
                    Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription =
                                    stringResource(id = R.string.plugin_loading_failed),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 插件名称和状态
        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = plugin.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )

            if (plugin.message.isNotEmpty()) {
                Text(
                        text = plugin.message,
                        style = MaterialTheme.typography.bodySmall,
                        color =
                                when (plugin.status) {
                                    PluginStatus.FAILED -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// 跳过加载的回调函数接口
interface SkipLoadingCallback {
    fun onSkip()
}

/**
 * 插件加载状态管理器
 *
 * 用于管理插件加载过程中的各种状态
 */
class PluginLoadingState {
    // 进度值 (0.0f - 1.0f)
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    // 当前状态消息
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    // 已启动的插件数量
    private val _pluginsStarted = MutableStateFlow(0)
    val pluginsStarted: StateFlow<Int> = _pluginsStarted

    // 总插件数量
    private val _pluginsTotal = MutableStateFlow(0)
    val pluginsTotal: StateFlow<Int> = _pluginsTotal

    // 是否显示加载屏幕
    private val _isVisible = MutableStateFlow(false)
    val isVisible: StateFlow<Boolean> = _isVisible

    // 是否展开
    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded

    // 插件列表及其状态
    private val _plugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val plugins: StateFlow<List<PluginInfo>> = _plugins

    // 应用上下文，用于获取MCP相关服务
    private var appContext: Context? = null

    // 是否已超时
    private val _hasTimedOut = MutableStateFlow(false)
    val hasTimedOut: StateFlow<Boolean> = _hasTimedOut

    // 用于取消超时计时器
    private var timeoutJob: kotlinx.coroutines.Job? = null

    // 跳过加载事件回调
    private var onSkipCallback: (() -> Unit)? = null

    // 插件加载完成事件回调
    private var onPluginsLoadedCallback: ((List<String>) -> Unit)? = null

    // 设置应用上下文
    fun setAppContext(context: Context) {
        this.appContext = context
    }

    fun toggleExpansion() {
        _isExpanded.value = !_isExpanded.value
    }

    /** 更新进度信息 */
    fun updateProgress(progress: Float) {
        _progress.value = progress
    }

    /** 更新状态消息 */
    fun updateMessage(message: String) {
        _message.value = message
    }

    /** 更新插件统计 */
    fun updatePluginStats(started: Int, total: Int) {
        _pluginsStarted.value = started
        _pluginsTotal.value = total
    }

    /** 设置插件列表 */
    fun setPlugins(pluginIds: List<String>) {
        val context = appContext
        val plugins =
                pluginIds.map { id ->
                    // 尝试从metadata获取插件名称
                    var displayName = id.split("/").lastOrNull() ?: id

                    // 如果上下文可用，尝试从元数据获取名称
                    if (context != null) {
                        try {
                            val mcpLocalServer = MCPLocalServer.getInstance(context)
                            val pluginInfo = mcpLocalServer.getPluginMetadata(id)
                            if (pluginInfo != null) {
                                displayName = pluginInfo.name
                            }
                        } catch (e: Exception) {
                            // 获取元数据失败，使用默认名称
                        }
                    }

                    PluginInfo(id = id, displayName = displayName)
                }
        _plugins.value = plugins
        _pluginsTotal.value = plugins.size
    }

    /** 更新插件状态 */
    fun updatePluginStatus(pluginId: String, status: PluginStatus, message: String = "") {
        val currentPlugins = _plugins.value.toMutableList()
        val pluginIndex = currentPlugins.indexOfFirst { it.id == pluginId }

        if (pluginIndex >= 0) {
            val plugin = currentPlugins[pluginIndex].copy(status = status, message = message)
            currentPlugins[pluginIndex] = plugin
            _plugins.value = currentPlugins

            // 更新已启动计数
            if (status == PluginStatus.SUCCESS) {
                _pluginsStarted.value = _plugins.value.count { it.status == PluginStatus.SUCCESS }
            }
        }
    }

    /** 开始加载指定插件 */
    fun startLoadingPlugin(pluginId: String) {
        updatePluginStatus(
                pluginId,
                PluginStatus.LOADING,
                appContext?.getString(R.string.plugin_loading) ?: "正在加载..."
        )
    }

    /** 标记插件加载成功 */
    fun setPluginSuccess(pluginId: String, message: String = "") {
        updatePluginStatus(
                pluginId,
                PluginStatus.SUCCESS,
                message.ifEmpty { appContext?.getString(R.string.plugin_loading_success) ?: "加载成功" }
        )
    }

    /** 标记插件加载失败 */
    fun setPluginFailed(pluginId: String, message: String = "") {
        updatePluginStatus(
                pluginId,
                PluginStatus.FAILED,
                message.ifEmpty { appContext?.getString(R.string.plugin_loading_failed) ?: "加载失败" }
        )
    }

    // 设置跳过回调
    fun setOnSkipCallback(callback: () -> Unit) {
        onSkipCallback = callback
    }

    // 设置插件加载完成回调
    fun setOnPluginsLoadedCallback(callback: (List<String>) -> Unit) {
        onPluginsLoadedCallback = callback
    }

    // 触发跳过操作
    fun skip() {
        timeoutJob?.cancel()
        hide()
        onSkipCallback?.invoke()
    }

    // 启动超时检测
    fun startTimeoutCheck(timeoutMillis: Long = 30000L, scope: kotlinx.coroutines.CoroutineScope) {
        timeoutJob?.cancel()
        timeoutJob =
                scope.launch {
                    delay(timeoutMillis)
                    _hasTimedOut.value = true
                    updateMessage(
                            appContext?.getString(R.string.plugin_loading_timeout)
                                    ?: "加载超时，您可以点击右上角的\"跳过\"继续"
                    )
                }
    }

    /** 显示加载屏幕 */
    fun show() {
        _isVisible.value = true
        _hasTimedOut.value = false
        // _isExpanded.value = true // 默认展开
    }

    /** 隐藏加载屏幕 */
    fun hide() {
        timeoutJob?.cancel()
        _isVisible.value = false
        // _isExpanded.value = false // 关闭时重置为折叠状态
    }

    /** 重置所有状态 */
    fun reset() {
        timeoutJob?.cancel()
        _progress.value = 0f
        _message.value = ""
        _pluginsStarted.value = 0
        _pluginsTotal.value = 0
        _plugins.value = emptyList()
        _isVisible.value = false
        _hasTimedOut.value = false
        _isExpanded.value = false
    }

    // 添加方法来初始化MCP服务器并启动插件
    fun initializeMCPServer(context: Context, lifecycleScope: kotlinx.coroutines.CoroutineScope) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 更新初始状态
                updateMessage(context.getString(R.string.plugin_initializing))
                updateProgress(0.05f)

                // 获取MCPLocalServer实例
                val mcpLocalServer = MCPLocalServer.getInstance(context)

                // 更新状态
                updateMessage(context.getString(R.string.plugin_starting_server))
                updateProgress(0.1f)

                // 服务器配置阶段
                updateMessage(context.getString(R.string.plugin_configuring_server))
                updateProgress(0.15f)

                // 服务器启动成功，更新状态
                updateMessage(context.getString(R.string.plugin_server_success))
                updateProgress(0.2f)

                // 服务器初始化中
                updateMessage(context.getString(R.string.plugin_server_initializing))
                updateProgress(0.25f)

                try {
                    // 获取MCPRepository实例
                    val mcpRepository = MCPRepository(context)

                    // 设置回调，以便在插件成功加载后注册其工具
                    setOnPluginsLoadedCallback { successfulPluginIds ->
                        Log.d("PluginLoadingState", "所有插件加载完成，开始注册 ${successfulPluginIds.size} 个插件的工具...")
                        mcpRepository.registerToolsForLoadedPlugins(successfulPluginIds)
                        Log.d("PluginLoadingState", "工具注册流程已触发")
                    }

                    // 获取已安装的插件列表 (这是一个Set<String>)
                    updateMessage(context.getString(R.string.plugin_loading_list))
                    updateProgress(0.28f)
                    val installedPluginsSet = mcpRepository.installedPluginIds.first()

                    // 显式转换为List<String>
                    val installedPluginsList = installedPluginsSet.toList()

                    if (installedPluginsSet.isEmpty()) {
                        // 没有安装的插件，直接进入主界面
                        Log.d("PluginLoadingState", "没有检测到已安装的插件，直接进入主界面")
                        updateMessage(context.getString(R.string.plugin_no_plugins))
                        updateProgress(1.0f)

                        // 立即隐藏插件加载界面
                        hide()
                        return@launch
                    }

                    // 设置插件列表，传入List<String>
                    updateMessage(
                            context.getString(R.string.plugin_preparing, installedPluginsList.size)
                    )
                    updateProgress(0.32f)
                    setPlugins(installedPluginsList)

                    // 有安装的插件，使用MCPStarter启动
                    updateMessage(context.getString(R.string.plugin_checking_env))
                    updateProgress(0.35f)

                    val mcpStarter = MCPStarter(context)
                    val mcpLocalServer = MCPLocalServer.getInstance(context)

                    // 创建一个适配器匿名类实现插件启动监听器
                    updateMessage(context.getString(R.string.plugin_starting_plugins))
                    updateProgress(0.38f)

                    val progressListener =
                            createPluginStartProgressListener(
                                    mcpLocalServer,
                                    lifecycleScope,
                                    context
                            )

                    // 启动所有插件 - MCPStarter会处理各种检查逻辑
                    mcpStarter.startAllDeployedPlugins(progressListener)
                } catch (e: Exception) {
                    // 处理插件加载过程中的异常
                    Log.e("PluginLoadingState", "加载插件过程中出错", e)
                    updateMessage(e.message ?: context.getString(R.string.plugin_loading_failed))
                    updateProgress(1.0f)

                    // 延迟后隐藏
                    lifecycleScope.launch {
                        delay(5000)
                        if (isVisible.value) {
                            hide()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PluginLoadingState", "启动MCP服务器和插件时出错", e)
                updateMessage(e.message ?: context.getString(R.string.plugin_other_error))
                updateProgress(1.0f)

                // 延迟一会儿后如果用户未跳过，则自动隐藏进度条
                lifecycleScope.launch {
                    delay(5000) // 等待5秒
                    if (isVisible.value) {
                        hide()
                    }
                }
            }
        }
    }

    // 创建插件启动进度监听器
    private fun createPluginStartProgressListener(
            mcpLocalServer: MCPLocalServer,
            lifecycleScope: kotlinx.coroutines.CoroutineScope,
            context: Context
    ): MCPStarter.PluginStartProgressListener {
        return object : MCPStarter.PluginStartProgressListener {
            override fun onPluginStarting(pluginId: String, index: Int, total: Int) {
                // 在这里检查插件是否被启用
                val serverStatus = mcpLocalServer.getServerStatus(pluginId)
                val isEnabled = serverStatus?.isEnabled != false // 默认为true

                // 更新总体状态
                val disabledSuffix =
                        if (!isEnabled) context.getString(R.string.plugin_disabled_suffix) else ""
                updateMessage(
                        context.getString(
                                R.string.plugin_starting_number,
                                index,
                                total,
                                disabledSuffix
                        )
                )
                updateProgress(0.4f + 0.6f * (index.toFloat() / total))

                // 更新特定插件状态
                startLoadingPlugin(pluginId)
            }

            override fun onPluginStarted(
                    pluginId: String,
                    success: Boolean,
                    index: Int,
                    total: Int
            ) {
                // 记录插件加载结果
                if (success) {
                    setPluginSuccess(pluginId)
                } else {
                    setPluginFailed(pluginId)
                }

                // 更新总体进度
                updateProgress(0.4f + 0.6f * (index.toFloat() / total))
            }

            override fun onAllPluginsStarted(
                    successCount: Int,
                    totalCount: Int,
                    status: MCPStarter.PluginInitStatus
            ) {
                // 根据初始化状态显示不同的消息
                when (status) {
                    MCPStarter.PluginInitStatus.TERMUX_NOT_RUNNING -> {
                        updateMessage(context.getString(R.string.plugin_termux_not_running))
                    }
                    MCPStarter.PluginInitStatus.TERMUX_NOT_AUTHORIZED -> {
                        updateMessage(context.getString(R.string.plugin_termux_not_authorized))
                    }
                    MCPStarter.PluginInitStatus.NODEJS_MISSING -> {
                        updateMessage(context.getString(R.string.plugin_nodejs_missing))
                    }
                    MCPStarter.PluginInitStatus.BRIDGE_FAILED -> {
                        updateMessage(context.getString(R.string.plugin_bridge_failed))
                    }
                    MCPStarter.PluginInitStatus.OTHER_ERROR -> {
                        updateMessage(context.getString(R.string.plugin_other_error))
                    }
                    else -> {
                        // 所有插件加载完成
                        val successRate =
                                if (totalCount > 0) {
                                    (successCount * 100) / totalCount
                                } else {
                                    0 // 当没有部署的插件时，成功率为0
                                }

                        // 触发插件加载完成的回调
                        if (status == MCPStarter.PluginInitStatus.SUCCESS && successCount > 0) {
                            val successfulPlugins = _plugins.value
                                .filter { it.status == PluginStatus.SUCCESS }
                                .map { it.id }
                            onPluginsLoadedCallback?.invoke(successfulPlugins)
                        }

                        // 如果有插件加载失败，则特别提示可以跳过
                        if (successCount < totalCount && totalCount > 0) {
                            updateMessage(
                                    context.getString(
                                            R.string.plugin_complete_with_failures,
                                            successRate
                                    )
                            )
                        } else if (totalCount > 0) {
                            updateMessage(
                                    context.getString(R.string.plugin_complete_success, successRate)
                            )
                        } else {
                            updateMessage(context.getString(R.string.plugin_no_plugins_to_start))
                        }
                    }
                }

                updateProgress(1.0f)

                // 对于错误状态，延长显示时间让用户看清消息
                val delayTime = if (status != MCPStarter.PluginInitStatus.SUCCESS) 5000L else 3000L

                // 延迟一会儿后隐藏进度条
                lifecycleScope.launch {
                    delay(delayTime)
                    // 检查是否已经通过跳过按钮关闭了界面
                    if (isVisible.value) {
                        hide()
                    }
                }
            }

            override fun onAllPluginsVerified(
                    verificationResults: List<MCPStarter.VerificationResult>
            ) {
                // 不需要修改这部分
            }
        }
    }
}

/** 插件加载屏幕的预览视图 */
@Composable
fun PluginLoadingScreenWithState(loadingState: PluginLoadingState, modifier: Modifier = Modifier) {
    val isVisible by loadingState.isVisible.collectAsState()
    val progress by loadingState.progress.collectAsState()
    val message by loadingState.message.collectAsState()
    val pluginsStarted by loadingState.pluginsStarted.collectAsState()
    val pluginsTotal by loadingState.pluginsTotal.collectAsState()
    val plugins by loadingState.plugins.collectAsState()
    val isExpanded by loadingState.isExpanded.collectAsState()

    PluginLoadingScreen(
            isVisible = isVisible,
            progress = progress,
            message = message,
            pluginsStarted = pluginsStarted,
            pluginsTotal = pluginsTotal,
            pluginsList = plugins,
            isExpanded = isExpanded,
            onToggleExpansion = { loadingState.toggleExpansion() },
            onSkip = { loadingState.skip() },
            modifier = modifier
    )
}
