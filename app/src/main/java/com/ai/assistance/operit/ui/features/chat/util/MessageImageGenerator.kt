package com.ai.assistance.operit.ui.features.chat.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.request.CachePolicy
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.ui.features.chat.components.ChatStyle
import com.ai.assistance.operit.ui.features.chat.components.style.bubble.BubbleStyleChatMessage
import com.ai.assistance.operit.ui.features.chat.components.style.cursor.CursorStyleChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 消息图片生成器
 * 
 * 将选中的消息渲染为图片，用于分享
 * 使用 ScrollView + ComposeView 方案支持任意长度的内容
 */
object MessageImageGenerator {
    
    private const val TAG = "MessageImageGenerator"
    
    /**
     * 生成消息图片
     * 
     * @param context Android 上下文
     * @param messages 要渲染的消息列表
     * @param userMessageColor 用户消息背景色
     * @param aiMessageColor AI消息背景色
     * @param userTextColor 用户消息文字颜色
     * @param aiTextColor AI消息文字颜色
     * @param systemMessageColor 系统消息背景色
     * @param systemTextColor 系统消息文字颜色
     * @param thinkingBackgroundColor 思考块背景色
     * @param thinkingTextColor 思考块文字颜色
     * @param chatStyle 聊天风格
     * @param width 图片宽度（像素）
     * @return 生成的图片文件
     */
    suspend fun generateMessageImage(
        context: Context,
        messages: List<ChatMessage>,
        userMessageColor: Color,
        aiMessageColor: Color,
        userTextColor: Color,
        aiTextColor: Color,
        systemMessageColor: Color,
        systemTextColor: Color,
        thinkingBackgroundColor: Color,
        thinkingTextColor: Color,
        chatStyle: ChatStyle = ChatStyle.CURSOR,
        width: Int = 1080
    ): File {
        try {
            Log.d(TAG, "开始生成消息图片（使用 Capturable），消息数量: ${messages.size}, 宽度: $width, 风格: $chatStyle")
            
            if (messages.isEmpty()) {
                throw IllegalArgumentException("消息列表不能为空")
            }
            
            // 获取 Activity 和根视图，用于临时附加 ComposeView
            val activity = context.findActivity() ?: throw IllegalStateException("Context is not an Activity.")
            val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
            
            // 在主线程上创建、附加和捕获 Composable 内容
            val bitmap = withContext(Dispatchers.Main) {
                // 创建专门用于软件渲染的 ImageLoader，禁用硬件位图
                val softwareImageLoader = ImageLoader.Builder(context)
                    .allowHardware(false) // 关键：禁用硬件位图
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build()
                
                // 创建 ComposeView，包含所有消息内容
                val composeView = ComposeView(context).apply {
                    setContent {
                        // 提供自定义的 ImageLoader
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalImageLoader provides softwareImageLoader
                        ) {
                            MaterialTheme {
                            // 不再使用 Capturable，直接渲染内容
                            val density = LocalDensity.current
                            val widthDp = with(density) { width.toDp() }
                            
                            // 外层容器：白色背景 + 内边距
                            Column(
                                modifier = Modifier
                                    .width(widthDp)
                                    .wrapContentHeight()
                                    .background(Color.White)
                                    .padding(12.dp) // 减少外层边距：24dp -> 12dp
                            ) {
                                // 内容卡片：圆角边框 + 阴影效果
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp)) // 减少圆角：16dp -> 12dp
                                        .border(
                                            width = 1.5.dp, // 减少边框宽度：2dp -> 1.5dp
                                            color = Color(0xFFE0E0E0),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .background(Color.White)
                                ) {
                                    // 顶部品牌栏：Logo + "Operit AI"
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFF5F5F5))
                                            .padding(horizontal = 12.dp, vertical = 8.dp), // 减少品牌栏边距
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        // Logo
                                        Image(
                                            painter = painterResource(id = com.ai.assistance.operit.R.drawable.ic_launcher_foreground),
                                            contentDescription = "Operit Logo",
                                            modifier = Modifier.size(48.dp) // 减少 Logo 尺寸：64dp -> 48dp
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        // 品牌名称
                                        Text(
                                            text = "Operit AI",
                                            fontSize = 16.sp, // 减少字体大小：18sp -> 16sp
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF333333)
                                        )
                                    }
                                    
                                    // 分隔线
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Color(0xFFE0E0E0))
                                    )
                                    
                                    // 消息内容区域
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight()
                                            .background(Color.White)
                                            .padding(12.dp), // 减少内容区域边距：16dp -> 12dp
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // 创建消息副本，清除 contentStream，确保只使用 content 字段
                                        val staticMessages = messages.map { message ->
                                            message.copy(contentStream = null)
                                        }
                                        
                                        staticMessages.forEach { message ->
                                            when (chatStyle) {
                                                ChatStyle.BUBBLE -> {
                                                    BubbleStyleChatMessage(
                                                        message = message,
                                                        userMessageColor = userMessageColor,
                                                        aiMessageColor = aiMessageColor,
                                                        userTextColor = userTextColor,
                                                        aiTextColor = aiTextColor,
                                                        systemMessageColor = systemMessageColor,
                                                        systemTextColor = systemTextColor,
                                                        enableDialogs = false // 禁用弹窗，因为这是静态图片
                                                    )
                                                }
                                                ChatStyle.CURSOR -> {
                                                    CursorStyleChatMessage(
                                                        message = message,
                                                        userMessageColor = userMessageColor,
                                                        aiMessageColor = aiMessageColor,
                                                        userTextColor = userTextColor,
                                                        aiTextColor = aiTextColor,
                                                        systemMessageColor = systemMessageColor,
                                                        systemTextColor = systemTextColor,
                                                        thinkingBackgroundColor = thinkingBackgroundColor,
                                                        thinkingTextColor = thinkingTextColor,
                                                        supportToolMarkup = true,
                                                        initialThinkingExpanded = false,
                                                        enableDialogs = false // 禁用弹窗，因为这是静态图片
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            }
                        }
                    }
                }
                
                // 将 ComposeView 包装在 ScrollView 中，以支持任意高度
                val scrollView = ScrollView(context).apply {
                    // 隐藏滚动条
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    addView(composeView, ViewGroup.LayoutParams(
                        width,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ))
                }
                
                // 设置 ScrollView 布局参数
                scrollView.layoutParams = ViewGroup.LayoutParams(
                    width,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                
                // 添加到根视图（视图在屏幕外，用户看不到）
                rootView.addView(scrollView)
                
                // 手动触发测量和布局，确保内容完全展开
                // 使用 EXACTLY 模式指定宽度，UNSPECIFIED 模式让高度自由扩展
                val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
                val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                scrollView.measure(widthMeasureSpec, heightMeasureSpec)
                scrollView.layout(0, 0, scrollView.measuredWidth, scrollView.measuredHeight)
                
                Log.d(TAG, "ScrollView 测量完成，尺寸: ${scrollView.measuredWidth}x${scrollView.measuredHeight}")
                
                // 等待 Compose 完成布局（给它一些时间）
                delay(500)
                
                val capturedBitmap: Bitmap
                try {
                    // 使用 ScrollView 子视图的完整高度创建 Bitmap
                    // 这是关键：getChildAt(0).height 获取完整的内容高度
                    val contentHeight = scrollView.getChildAt(0).height
                    Log.d(TAG, "内容完整高度: $contentHeight")
                    
                    capturedBitmap = Bitmap.createBitmap(
                        scrollView.width,
                        contentHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    val canvas = Canvas(capturedBitmap)
                    scrollView.draw(canvas)
                    
                    Log.d(TAG, "捕获成功，图片尺寸: ${capturedBitmap.width}x${capturedBitmap.height}")
                } catch (e: Throwable) {
                    Log.e(TAG, "捕获失败", e)
                    throw RuntimeException("图片捕获失败: ${e.message}", e)
                } finally {
                    Log.d(TAG, "从窗口移除 ScrollView")
                    // 确保无论成功还是失败，都将视图移除
                    rootView.removeView(scrollView)
                }
                capturedBitmap
            }
            
            // 在 IO 线程上保存文件
            return withContext(Dispatchers.IO) {
                val outputDir = File(context.cacheDir, "shared_images")
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }
                
                val timestamp = System.currentTimeMillis()
                val outputFile = File(outputDir, "messages_$timestamp.png")
                
                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
                
                Log.d(TAG, "图片已保存到: ${outputFile.absolutePath}, 大小: ${outputFile.length()} bytes")
                
                // 回收 Bitmap
                bitmap.recycle()
                
                outputFile
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "生成消息图片失败", e)
            throw e
        }
    }
}


private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

