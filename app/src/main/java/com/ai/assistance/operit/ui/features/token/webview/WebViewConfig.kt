package com.ai.assistance.operit.ui.features.token.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView

/** WebView配置相关工具类 */
object WebViewConfig {
    /** 创建一个预配置的WebView实例 */
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    fun createWebView(context: Context): WebView {
        // Initialize the WebView
        return WebView(context).apply {
            // Configure WebView settings
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                allowContentAccess = true
                allowFileAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true

                // 显式允许混合内容（对于支付场景很重要）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                // 允许通用第三方应用访问
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true

                // 设置一个常见的移动浏览器User-Agent，以避免被某些服务（如Google登录）阻止
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

                // 启用缩放控制
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false

                // 对于Android 8.0及以上版本的安全Webview优化
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
            }

            // 配置Cookie
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            // 确保WebView可以处理跳转到外部应用
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            }

            // Enable WebView debugging
            WebView.setWebContentsDebuggingEnabled(true)

            // 添加触摸事件拦截，防止父视图拦截WebView的垂直滑动
            setOnTouchListener { v, event ->
                // 检测垂直滚动
                if (event.action == MotionEvent.ACTION_MOVE) {
                    // 当检测到垂直移动时，告诉父视图不要拦截事件
                    if (Math.abs(event.y) > Math.abs(event.x)) {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                } else if (event.action == MotionEvent.ACTION_UP ||
                                event.action == MotionEvent.ACTION_CANCEL
                ) {
                    // 触摸结束时恢复正常事件传递
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }

                // 返回false表示WebView仍然需要处理这个事件
                false
            }

            // 为了确保正确处理滚动，设置嵌套滚动启用
            isNestedScrollingEnabled = true

            // 设置WebView布局参数，确保它可以正常滚动
            layoutParams =
                    ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    )

            // Add console logger
            setWebChromeClient(
                    object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            Log.d(
                                    "WebViewConsole",
                                    "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}"
                            )
                            return true
                        }

                        // 支持HTML5视频全屏播放
                        override fun onShowCustomView(
                                view: android.view.View,
                                callback: CustomViewCallback
                        ) {
                            super.onShowCustomView(view, callback)
                        }

                        override fun onHideCustomView() {
                            super.onHideCustomView()
                        }

                        // 支持地理位置请求
                        override fun onGeolocationPermissionsShowPrompt(
                                origin: String,
                                callback: android.webkit.GeolocationPermissions.Callback
                        ) {
                            callback.invoke(origin, true, false)
                        }

                        // 处理JavaScript警告和错误
                        override fun onJsAlert(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: android.webkit.JsResult?
                        ): Boolean {
                            Log.d("WebViewJS", "Alert: $message")
                            result?.confirm()
                            return true
                        }

                        override fun onJsConfirm(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: android.webkit.JsResult?
                        ): Boolean {
                            Log.d("WebViewJS", "Confirm: $message")
                            result?.confirm()
                            return true
                        }

                        override fun onJsPrompt(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                defaultValue: String?,
                                result: android.webkit.JsPromptResult?
                        ): Boolean {
                            Log.d("WebViewJS", "Prompt: $message, Default: $defaultValue")
                            result?.confirm(defaultValue)
                            return true
                        }
                    }
            )
        }
    }
}
