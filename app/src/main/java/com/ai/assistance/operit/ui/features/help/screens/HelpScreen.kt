package com.ai.assistance.operit.ui.features.help.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.ui.features.token.webview.WebViewConfig

@Composable
fun HelpScreen(onBackPressed: () -> Unit = {}) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    
    // 创建WebView实例
    val webView = remember { 
        WebViewConfig.createWebView(context).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    isLoading = true
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    isLoading = false
                }
                
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    // 让WebView处理所有链接
                    return false
                }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        webView.loadUrl("https://aaswordman.github.io/OperitWeb/")
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // WebView
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )
        
        // 加载指示器
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "正在加载使用手册...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
