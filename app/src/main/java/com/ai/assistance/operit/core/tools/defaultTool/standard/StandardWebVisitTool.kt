package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.ToolExecutor
import com.ai.assistance.operit.core.tools.VisitWebResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient

/** Tool for web page visiting and content extraction */
class StandardWebVisitTool(private val context: Context) : ToolExecutor {

    companion object {
        private const val TAG = "WebVisitTool"
        private const val USER_AGENT =
                "Mozilla/5.0 (Linux; Android 12; SM-G998B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/113.0.0.0 Mobile Safari/537.36"
    }

    // 创建OkHttpClient实例，配置超时
    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()

    // 存储WebView引用，用于在不同方法间共享
    private var webViewReference: WebView? = null

    override fun invoke(tool: AITool): ToolResult {
        val url = tool.parameters.find { it.name == "url" }?.value ?: ""

        if (url.isBlank()) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "URL parameter cannot be empty"
            )
        }

        return try {
            val pageContent = visitWebPage(url)
            ToolResult(toolName = tool.name, success = true, result = pageContent, error = null)
        } catch (e: Exception) {
            Log.e(TAG, "Error visiting web page", e)

            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Error visiting web page: ${e.message}"
            )
        }
    }

    override fun validateParameters(tool: AITool): ToolValidationResult {
        val url = tool.parameters.find { it.name == "url" }?.value

        return if (url.isNullOrBlank()) {
            ToolValidationResult(valid = false, errorMessage = "URL parameter is required")
        } else {
            ToolValidationResult(valid = true)
        }
    }

    /** Visit web page and extract content */
    private fun visitWebPage(url: String): VisitWebResultData {
        // Use WebView to visit the page and extract content
        val pageContent = runBlocking { loadWebPageAndExtractContent(url) }

        // 将内容解析为结构化数据
        // 在这里，我们从完整内容中提取标题和元数据
        val lines = pageContent.split("\n")
        var title = "无标题"
        val metadata = mutableMapOf<String, String>()
        var contentStartIndex = 0

        // 寻找标题，假设格式是"# 标题"
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (line.startsWith("# ")) {
                title = line.substring(2).trim()
                contentStartIndex = i + 1
                break
            }
        }

        // 寻找元数据部分
        var inMetadata = false
        var metadataEndIndex = contentStartIndex

        for (i in contentStartIndex until lines.size) {
            val line = lines[i].trim()

            if (line == "---METADATA---") {
                inMetadata = true
                metadataEndIndex = i + 1
                continue
            }

            if (inMetadata) {
                if (line == "---CONTENT---") {
                    metadataEndIndex = i + 1
                    break
                }

                // 解析元数据，格式为"key: value"
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        metadata[key] = value
                    }
                }
            }
        }

        // 提取实际内容
        val content =
                if (metadataEndIndex < lines.size) {
                    lines.subList(metadataEndIndex, lines.size).joinToString("\n")
                } else {
                    // 如果没有找到元数据/内容分隔符，使用整个内容
                    pageContent
                }

        return VisitWebResultData(url = url, title = title, content = content, metadata = metadata)
    }

    /** 使用WebView加载页面并提取内容 */
    private suspend fun loadWebPageAndExtractContent(url: String): String {
        return suspendCancellableCoroutine { continuation ->
            Log.d(TAG, "Starting to load web page: $url")

            // 确保UI操作在主线程上执行
            Handler(Looper.getMainLooper()).post {
                try {
                    val windowManager =
                            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    val params = setupWindowParams()

                    // 创建生命周期管理器
                    val lifecycleOwner =
                            ServiceLifecycleOwner().apply {
                                handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                                handleLifecycleEvent(Lifecycle.Event.ON_START)
                                handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                            }

                    val composeView =
                            ComposeView(context).apply {
                                // 设置视图组合策略
                                setViewCompositionStrategy(
                                        ViewCompositionStrategy.DisposeOnDetachedFromWindow
                                )

                                // 使用自定义的生命周期所有者
                                setViewTreeLifecycleOwner(lifecycleOwner)
                                setViewTreeViewModelStoreOwner(lifecycleOwner)
                                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                            }

                    composeView.setContent {
                        WebVisitUI(
                                url = url,
                                onWebViewCreated = { webView ->
                                    // 存储WebView引用以便清理
                                    webViewReference = webView
                                },
                                onContentExtracted = { content ->
                                    Log.d(TAG, "Content extracted, length: ${content.length}")

                                    // 在完成内容提取后进行资源清理
                                    try {
                                        // 清理生命周期资源
                                        lifecycleOwner.handleLifecycleEvent(
                                                Lifecycle.Event.ON_PAUSE
                                        )
                                        lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                                        lifecycleOwner.handleLifecycleEvent(
                                                Lifecycle.Event.ON_DESTROY
                                        )

                                        // 清理WebView
                                        cleanupWebView(webViewReference)

                                        // 清理Compose View并移除窗口
                                        CoroutineScope(Dispatchers.Main).launch {
                                            // 首先设置内容为空，释放组合资源
                                            composeView.setContent {}

                                            // 使用try-catch以确保即使移除窗口失败也能继续
                                            try {
                                                windowManager.removeView(composeView)
                                                Log.d(TAG, "ComposeView removed from window")
                                            } catch (e: Exception) {
                                                Log.e(
                                                        TAG,
                                                        "Error removing ComposeView: ${e.message}"
                                                )
                                            }

                                            // 恢复协程
                                            if (continuation.isActive) {
                                                continuation.resume(content)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error cleaning up resources: ${e.message}")
                                        if (continuation.isActive) {
                                            continuation.resume(
                                                    "Error extracting content: ${e.message}"
                                            )
                                        }
                                    }
                                }
                        )
                    }

                    try {
                        windowManager.addView(composeView, params)
                        Log.d(TAG, "Web browser window added")

                        // 添加取消回调以处理协程取消
                        continuation.invokeOnCancellation {
                            // 确保在主线程上执行UI操作
                            Handler(Looper.getMainLooper()).post {
                                try {
                                    // 清理生命周期资源
                                    lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                                    lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                                    lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

                                    // 清理WebView资源
                                    cleanupWebView(webViewReference)

                                    // 移除视图
                                    composeView.setContent {}
                                    windowManager.removeView(composeView)
                                    Log.d(TAG, "Web browser window removed on cancellation")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error removing view on cancellation: ${e.message}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing web browser: ${e.message}")
                        continuation.resume("Error displaying web browser: ${e.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in loadWebPageAndExtractContent: ${e.message}")
                    continuation.resume("General error: ${e.message}")
                }
            }
        }
    }

    /** Setup window parameters for the WebView dialog */
    private fun setupWindowParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams().apply {
            // Set window type
            type =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_PHONE
                    }

            // Important: Do not set FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE
            // as they would prevent interaction with the WebView
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

            // Make window transparent to properly display the Compose UI
            format = PixelFormat.TRANSLUCENT

            // Full screen window
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT

            // Center the window
            gravity = Gravity.CENTER
        }
    }

    /** 清理WebView资源的辅助方法 */
    private fun cleanupWebView(webView: WebView?) {
        webView?.apply {
            // 先停止加载
            stopLoading()

            // 清理WebView状态
            clearHistory()
            clearCache(true)
            clearFormData()
            clearSslPreferences()

            // 清理JavaScript状态
            evaluateJavascript("javascript:void(0);", null)

            // 加载空白页以释放资源
            loadUrl("about:blank")

            // 暂停WebView
            onPause()

            // 移除回调和消息
            handler?.removeCallbacksAndMessages(null)

            // 销毁WebView
            destroy()
        }

        // 请求GC回收资源
        System.gc()
    }

    /** Compose UI for the web browser */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun WebVisitUI(
            url: String,
            onWebViewCreated: (WebView) -> Unit,
            onContentExtracted: (String) -> Unit
    ) {
        // 页面状态
        val isLoading = remember { mutableStateOf(true) } // 页面是否正在加载
        val pageLoaded = remember { mutableStateOf(false) } // 页面是否已加载完成
        val currentUrl = remember { mutableStateOf(url) } // 当前URL
        val pageTitle = remember { mutableStateOf("") } // 页面标题

        // 内容状态
        val pageContent = remember { mutableStateOf("") } // 提取的页面内容
        val hasExtractedContent = remember { mutableStateOf(false) } // 是否已提取内容

        // 自动模式状态
        val autoModeEnabled = remember { mutableStateOf(true) } // 是否启用自动模式
        val autoCountdownActive = remember { mutableStateOf(false) } // 倒计时是否激活
        val autoCountdownSeconds = remember { mutableStateOf(4) } // 倒计时秒数

        // 修改LaunchedEffect部分，使滚动和倒计时同时进行
        LaunchedEffect(autoCountdownActive.value) {
            if (autoCountdownActive.value) {
                autoCountdownSeconds.value = 4
                
                // 开始4秒倒计时
                for (i in 4 downTo 1) {
                    autoCountdownSeconds.value = i
                    delay(1000) // 延迟1秒
                    
                    // 如果倒计时被取消，中断倒计时
                    if (!autoCountdownActive.value) {
                        break
                    }
                }
                
                // 倒计时结束且未被取消，自动继续
                if (autoCountdownActive.value) {
                    autoCountdownActive.value = false
                    if (pageContent.value.isNotEmpty()) {
                        onContentExtracted(pageContent.value)
                    }
                }
            }
        }

        MaterialTheme {
            Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
            ) {
                Card(
                        modifier =
                                Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
                        shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header
                        Text(
                                text = "网页访问中",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // URL info
                        Box(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .border(
                                                        width = 1.dp,
                                                        color = MaterialTheme.colorScheme.outline,
                                                        shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                        text = "访问网址:",
                                        fontSize = 16.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                        text = currentUrl.value,
                                        fontSize = 14.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // 状态文本根据当前状态动态变化
                                val statusText =
                                        when {
                                            !pageLoaded.value -> "正在加载页面..."
                                            isLoading.value -> "等待页面完全加载..."
                                            autoCountdownActive.value ->
                                                    "正在提取内容，${autoCountdownSeconds.value}秒后自动继续..."
                                            hasExtractedContent.value -> "内容已提取，等待手动继续..."
                                            else -> "页面已加载，等待提取内容..."
                                        }

                                Text(
                                        text = statusText,
                                        fontSize = 16.sp
                                )

                                if (pageTitle.value.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                            text = "页面标题: ${pageTitle.value}",
                                            fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // WebView
                        AndroidView(
                                factory = { context ->
                                    createOptimizedWebView(context, url) { webView ->
                                        // Register created WebView instance for later cleanup
                                        onWebViewCreated(webView)

                                        // Configure WebViewClient
                                        webView.webViewClient =
                                                object : WebViewClient() {
                                                    // 追踪上一个URL，用于检测重定向
                                                    private var lastLoadedUrl: String = ""

                                                    override fun onPageFinished(
                                                            view: WebView,
                                                            loadedUrl: String
                                                    ) {
                                                        super.onPageFinished(view, loadedUrl)
                                                        Log.d(TAG, "Loaded URL: $loadedUrl")

                                                        // 检查是否是重定向或新页面
                                                        val isNewPage = lastLoadedUrl != loadedUrl
                                                        lastLoadedUrl = loadedUrl

                                                        currentUrl.value = loadedUrl
                                                        pageTitle.value = view.title ?: ""
                                                        pageLoaded.value = true

                                                        // 如果是重定向或新页面，重置提取状态
                                                        if (isNewPage) {
                                                            Log.d(TAG, "页面发生重定向或加载了新页面，重置提取状态")
                                                            isLoading.value = true
                                                            hasExtractedContent.value = false
                                                            pageContent.value = ""
                                                            // 重置自动状态
                                                            autoCountdownActive.value = false
                                                            autoModeEnabled.value = true
                                                        }

                                                        // 页面加载完成后，延迟自动提取内容
                                                        if (autoModeEnabled.value) {
                                                            Handler(Looper.getMainLooper())
                                                                    .postDelayed(
                                                                            {
                                                                                if (!view.isAttachedToWindow
                                                                                ) {
                                                                                    Log.d(
                                                                                            TAG,
                                                                                            "WebView不再附加到窗口，跳过提取"
                                                                                    )
                                                                                    return@postDelayed
                                                                                }
                                                                                
                                                                                // 直接提取内容，不先滚动
                                                                                Log.d(
                                                                                        TAG,
                                                                                        "页面加载完成，开始提取内容"
                                                                                )
                                                                                
                                                                                if (!hasExtractedContent.value && 
                                                                                        autoModeEnabled.value
                                                                                ) {
                                                                                    isLoading.value = true
                                                                                    
                                                                                    extractPageContent(
                                                                                            view
                                                                                    ) { content ->
                                                                                        isLoading.value = false
                                                                                        hasExtractedContent.value = true
                                                                                        pageContent.value = content
                                                                                        
                                                                                        // 如果仍处于自动模式，自动开始倒计时
                                                                                        if (autoModeEnabled.value) {
                                                                                            autoCountdownActive.value = true
                                                                                            
                                                                                            // 在开始倒计时的同时滚动页面
                                                                                            Log.d(TAG, "开始倒计时，同时滚动页面")
                                                                                            autoScrollToBottom(view) {
                                                                                                Log.d(TAG, "页面滚动完成")
                                                                                            }
                                                                                        }
                                                                                    }
                                                                                }
                                                                            },
                                                                            800
                                                                    ) // 缩短延迟到800毫秒
                                                        }
                                                    }

                                                    override fun shouldOverrideUrlLoading(
                                                            view: WebView,
                                                            request: WebResourceRequest
                                                    ): Boolean {
                                                        // 记录URL变化，但允许所有导航
                                                        val newUrl = request.url.toString()
                                                        Log.d(TAG, "URL变化: $newUrl")
                                                        currentUrl.value = newUrl

                                                        // 设置页面正在加载状态
                                                        isLoading.value = true
                                                        pageLoaded.value = false

                                                        return false // 允许WebView处理URL加载
                                                    }

                                                    override fun onReceivedError(
                                                            view: WebView,
                                                            errorCode: Int,
                                                            description: String,
                                                            failingUrl: String
                                                    ) {
                                                        Log.e(
                                                                TAG,
                                                                "WebView error: $errorCode - $description"
                                                        )
                                                        super.onReceivedError(
                                                                view,
                                                                errorCode,
                                                                description,
                                                                failingUrl
                                                        )
                                                    }
                                                }

                                        return@createOptimizedWebView webView
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                update = { webView ->
                                    // 存储最新的WebView引用
                                    webViewReference = webView
                                    // 只更新重要属性以避免重新创建
                                    webView.requestFocus()
                                }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 按钮区域 - 根据当前状态显示不同按钮
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 左侧按钮 - 根据状态变化
                            OutlinedButton(
                                    onClick = {
                                        if (autoCountdownActive.value) {
                                            // 如果正在倒计时，则取消倒计时
                                            autoCountdownActive.value = false
                                        } else if (pageLoaded.value &&
                                                        !hasExtractedContent.value &&
                                                        autoModeEnabled.value
                                        ) {
                                            // 如果页面已加载但未提取内容，且处于自动模式，则切换到手动模式
                                            autoModeEnabled.value = false
                                        } else {
                                            // 其他情况为取消操作
                                            onContentExtracted("操作已取消")
                                        }
                                    }
                            ) {
                                Text(
                                        when {
                                            autoCountdownActive.value -> "取消倒计时"
                                            pageLoaded.value &&
                                                    !hasExtractedContent.value &&
                                                    autoModeEnabled.value -> "取消自动"
                                            else -> "取消"
                                        }
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // 右侧按钮 - 根据状态变化
                            Button(
                                    onClick = {
                                        if (autoCountdownActive.value) {
                                            // 如果正在倒计时，立即继续
                                            autoCountdownActive.value = false
                                            if (pageContent.value.isNotEmpty()) {
                                                onContentExtracted(pageContent.value)
                                            }
                                        } else if (hasExtractedContent.value) {
                                            // 如果已提取内容，直接继续
                                            onContentExtracted(pageContent.value)
                                        } else {
                                            // 否则提取内容
                                            webViewReference?.let { webView ->
                                                // 显示正在提取提示
                                                isLoading.value = true

                                                Log.d(TAG, "触发内容提取，当前页面: ${currentUrl.value}")

                                                extractPageContent(webView) { content ->
                                                    isLoading.value = false
                                                    hasExtractedContent.value = true
                                                    pageContent.value = content

                                                    // 如果是自动模式，开始倒计时
                                                    if (autoModeEnabled.value) {
                                                        autoCountdownActive.value = true
                                                    }
                                                }
                                            }
                                                    ?: onContentExtracted("WebView不可用，无法提取内容。")
                                        }
                                    },
                                    enabled = pageLoaded.value,
                                    colors =
                                            ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                    modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                        when {
                                            autoCountdownActive.value ->
                                                    "立即继续 (${autoCountdownSeconds.value}s)"
                                            hasExtractedContent.value -> "继续处理"
                                            !autoModeEnabled.value -> "手动提取"
                                            else -> "提取内容" // 移除"(自动)"后缀，因为这是默认行为
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 帮助文本
                        Text(
                                text =
                                        when {
                                            !pageLoaded.value -> "正在加载页面，请稍候..."
                                            autoCountdownActive.value -> "正在自动倒计时，点击\"取消倒计时\"可停止自动处理"
                                            !hasExtractedContent.value && autoModeEnabled.value ->
                                                    "点击\"取消自动\"可切换到手动模式"
                                            !hasExtractedContent.value && !autoModeEnabled.value ->
                                                    "已切换到手动模式，点击\"手动提取\"继续"
                                            else -> "内容已提取完成，点击\"继续处理\"继续"
                                        },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }

    /** 创建优化的WebView实例，配置所有必要的设置 */
    private fun createOptimizedWebView(
            context: Context,
            url: String,
            configure: (WebView) -> WebView
    ): WebView {
        return WebView(context).apply {
            layoutParams =
                    LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1.0f
                    )

            // 配置WebView设置
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                builtInZoomControls = true
                displayZoomControls = false
                userAgentString = USER_AGENT

                // 添加额外的设置以帮助内存管理
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                mediaPlaybackRequiresUserGesture = true // 减少自动媒体加载
            }

            // 启用交互
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()

            // 改善内存管理
            setWillNotDraw(false)

            // 加载URL
            loadUrl(url)

            // 应用额外配置
            configure(this)
        }
    }

    /** 从加载的WebView提取页面内容 */
    private fun extractPageContent(webView: WebView, callback: (String) -> Unit) {
        // 检查WebView是否有效
        if (!webView.isAttachedToWindow) {
            Log.e(TAG, "WebView is not attached to window, cannot extract content")
            callback("Error: WebView is not attached to window")
            return
        }

        // 记录提取开始
        Log.d(TAG, "Starting to extract content from web page")

        // 用于提取页面内容的JavaScript
        val extractionScript =
                """
            (function() {
                try {
                    console.log("Starting content extraction script");
                    
                    // 页面基本信息
                    var result = {
                        title: document.title || "No Title",
                        url: window.location.href,
                        content: ""
                    };
                    
                    // 直接获取整个文档的HTML和文本内容
                    var fullHtml = document.documentElement.outerHTML;
                    var fullText = document.body.innerText;
                    
                    // 添加元数据
                    var metadata = {};
                    
                    // 提取所有meta标签的信息
                    var metaTags = document.querySelectorAll('meta');
                    for (var i = 0; i < metaTags.length; i++) {
                        var name = metaTags[i].getAttribute('name') || 
                                   metaTags[i].getAttribute('property') || 
                                   metaTags[i].getAttribute('itemprop');
                        var content = metaTags[i].getAttribute('content');
                        
                        if (name && content) {
                            metadata[name] = content;
                        }
                    }
                    
                    // 添加特别关注的元数据
                    var importantMetadata = ['description', 'keywords', 'author', 'og:title', 'og:description'];
                    var metaStr = "---METADATA---\\n";
                    importantMetadata.forEach(function(key) {
                        if (metadata[key]) {
                            metaStr += key + ": " + metadata[key] + "\\n";
                        }
                    });
                    
                    // 组合最终结果
                    var finalResult = "# " + result.title + "\\n\\n" +
                                      metaStr + "\\n" +
                                      "---CONTENT---\\n" +
                                      fullText;
                                      
                    console.log("Content extraction complete, length: " + finalResult.length);
                    return finalResult;
                } catch(e) {
                    return "Error extracting content: " + e.toString();
                }
            })();
        """.trimIndent()

        try {
            webView.evaluateJavascript(extractionScript) { resultContent ->
                try {
                    Log.d(
                            TAG,
                            "Content extraction result received. Length: ${resultContent.length}"
                    )

                    // 处理JavaScript结果，去掉引号包装
                    val processedContent =
                            if (resultContent.startsWith("\"") && resultContent.endsWith("\"")) {
                                // 解码JavaScript字符串到Kotlin字符串
                                resultContent
                                        .substring(1, resultContent.length - 1)
                                        .replace("\\\"", "\"")
                                        .replace("\\\\", "\\")
                                        .replace("\\n", "\n")
                            } else {
                                resultContent
                            }

                    callback(processedContent)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing extracted content", e)
                    callback("Error processing content: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during JavaScript evaluation", e)
            callback("Error evaluating JavaScript: ${e.message}")
        }
    }

    /** 自动滚动页面到底部以触发可能的懒加载内容 */
    private fun autoScrollToBottom(webView: WebView, onScrollComplete: () -> Unit) {
        val scrollScript =
                """
            (function() {
                try {
                    // 初始位置
                    var initialHeight = document.body.scrollHeight;
                    var scrollAttempts = 0;
                    var maxScrollAttempts = 3;
                    var scrollInterval;
                    var lastScrollHeight = 0;
                    
                    function smoothScroll() {
                        // 检查是否到达尝试次数上限
                        if (scrollAttempts >= maxScrollAttempts) {
                            clearInterval(scrollInterval);
                            console.log('自动滚动完成 - 达到最大尝试次数');
                            return true; // 滚动完成
                        }
                        
                        // 获取当前文档高度
                        var currentHeight = document.body.scrollHeight;
                        
                        // 如果两次滚动后高度没有变化，认为已经滚动到底部
                        if (currentHeight === lastScrollHeight && scrollAttempts > 0) {
                            scrollAttempts++;
                            console.log('内容高度未变化，尝试次数: ' + scrollAttempts);
                        } else {
                            // 更新上次高度
                            lastScrollHeight = currentHeight;
                        }
                        
                        // 执行滚动
                        var currentPosition = window.pageYOffset || document.documentElement.scrollTop;
                        var targetPosition = currentHeight - window.innerHeight;
                        var distance = targetPosition - currentPosition;
                        
                        if (Math.abs(distance) < 10) {
                            // 已接近底部，增加尝试次数
                            scrollAttempts++;
                            console.log('已接近底部，尝试次数: ' + scrollAttempts);
                            
                            // 额外触发一次滚动确保触发所有加载
                            window.scrollTo(0, targetPosition + 1);
                            
                            // 短暂等待后滚回正常位置
                            setTimeout(function() {
                                window.scrollTo(0, targetPosition);
                            }, 100);
                        } else {
                            // 平滑滚动到目标位置
                            window.scrollTo({
                                top: targetPosition,
                                behavior: 'smooth'
                            });
                            console.log('滚动到位置: ' + targetPosition + '，总高度: ' + currentHeight);
                        }
                        
                        return scrollAttempts >= maxScrollAttempts;
                    }
                    
                    // 立即执行一次初始滚动
                    var isComplete = smoothScroll();
                    if (isComplete) {
                        return 'scroll-complete';
                    }
                    
                    // 设置定时滚动
                    scrollInterval = setInterval(function() {
                        var isComplete = smoothScroll();
                        if (isComplete) {
                            clearInterval(scrollInterval);
                            console.log('自动滚动完成');
                            return 'scroll-complete';
                        }
                    }, 1000); // 每秒滚动一次
                    
                    // 无论如何，最多滚动8秒
                    setTimeout(function() {
                        clearInterval(scrollInterval);
                        console.log('自动滚动超时完成');
                    }, 8000);
                    
                    return 'scrolling-started';
                } catch(e) {
                    console.error('滚动过程出错: ' + e);
                    return 'scroll-error: ' + e;
                }
            })();
        """.trimIndent()

        try {
            Log.d(TAG, "开始执行自动滚动脚本")
            webView.evaluateJavascript(scrollScript) { result -> Log.d(TAG, "滚动脚本初始执行结果: $result") }

            // 等待滚动完成后执行回调
            Handler(Looper.getMainLooper())
                    .postDelayed(
                            {
                                Log.d(TAG, "自动滚动等待完成，继续处理")
                                onScrollComplete()
                            },
                            5000
                    ) // 给页面5秒钟的滚动时间
        } catch (e: Exception) {
            Log.e(TAG, "执行滚动脚本时出错", e)
            // 出错时也要继续后续流程
            onScrollComplete()
        }
    }
}

/** 服务生命周期所有者 - 为Compose UI提供生命周期支持 必须在主线程上初始化 */
private class ServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreField = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        // 确保在主线程上初始化
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // 在主线程上，直接初始化
            savedStateRegistryController.performRestore(null)
        } else {
            // 如果不在主线程上，则记录警告（我们应该确保在主线程上创建实例）
            Log.w(
                    "ServiceLifecycleOwner",
                    "Initializing not on main thread. This may cause issues."
            )
            // 在实际使用时应该避免这种情况，代码已经通过 Handler(Looper.getMainLooper()).post 确保在主线程上创建
        }
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = viewModelStoreField

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        // 确保生命周期事件在主线程上处理
        if (Looper.myLooper() == Looper.getMainLooper()) {
            lifecycleRegistry.handleLifecycleEvent(event)
        } else {
            // 如果不在主线程上，使用Handler将调用转到主线程
            Handler(Looper.getMainLooper()).post { lifecycleRegistry.handleLifecycleEvent(event) }
        }
    }
}
