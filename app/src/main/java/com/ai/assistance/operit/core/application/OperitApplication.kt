package com.ai.assistance.operit.core.application

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import coil.ImageLoader
import coil.disk.DiskCache
import coil.request.CachePolicy
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.chat.AIMessageManager
import com.ai.assistance.operit.core.config.SystemPromptConfig
import com.ai.assistance.operit.core.invitation.InvitationManager
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.initAndroidPermissionPreferences
import com.ai.assistance.operit.data.preferences.initUserPreferencesManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.data.repository.CustomEmojiRepository
import com.ai.assistance.operit.services.OnnxEmbeddingService
import com.ai.assistance.operit.ui.features.chat.webview.LocalWebServer
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.language.LanguageFactory
import com.ai.assistance.operit.util.GlobalExceptionHandler
import com.ai.assistance.operit.util.ImagePoolManager
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.util.SerializationSetup
import com.ai.assistance.operit.util.TextSegmenter
import com.ai.assistance.operit.util.WaifuMessageProcessor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/** Application class for Operit */
class OperitApplication : Application() {

    companion object {
        /** Global JSON instance with custom serializers */
        lateinit var json: Json
            private set

        // 全局应用实例
        lateinit var instance: OperitApplication
            private set

        // 全局ImageLoader实例，用于高效缓存图片
        lateinit var globalImageLoader: ImageLoader
            private set

        private const val TAG = "OperitApplication"
    }

    // 应用级协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 懒加载数据库实例
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "【启动计时】应用启动开始")
        
        instance = this
        Log.d(TAG, "【启动计时】实例初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // Initialize ActivityLifecycleManager to track the current activity
        ActivityLifecycleManager.initialize(this)
        Log.d(TAG, "【启动计时】ActivityLifecycleManager初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // Initialize AIMessageManager
        AIMessageManager.initialize(this)
        Log.d(TAG, "【启动计时】AIMessageManager初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // Initialize Embedding Service asynchronously in background
        // Using ONNX-based multilingual model for better Chinese support
        applicationScope.launch {
            val embeddingStartTime = System.currentTimeMillis()
            OnnxEmbeddingService.initialize(this@OperitApplication)
            Log.d(TAG, "【启动计时】OnnxEmbeddingService初始化完成（异步） - ${System.currentTimeMillis() - embeddingStartTime}ms")
        }

        // Initialize ANR monitor
        // AnrMonitor.start() // This line was removed from the new_code, so it's removed here.

        // 在所有其他初始化之前设置全局异常处理器
        Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(this))
        Log.d(TAG, "【启动计时】全局异常处理器设置完成 - ${System.currentTimeMillis() - startTime}ms")

        // Initialize the JSON serializer with our custom module
        json = Json {
            serializersModule = SerializationSetup.module
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
            encodeDefaults = true
        }
        Log.d(TAG, "【启动计时】JSON序列化器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化用户偏好管理器
        val defaultProfileName = applicationContext.getString(R.string.default_profile)
        initUserPreferencesManager(applicationContext, defaultProfileName)
        Log.d(TAG, "【启动计时】用户偏好管理器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化Android权限偏好管理器
        initAndroidPermissionPreferences(applicationContext)
        Log.d(TAG, "【启动计时】Android权限偏好管理器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化功能提示词管理器
        applicationScope.launch {
            val characterStartTime = System.currentTimeMillis()
            CharacterCardManager.getInstance(applicationContext).initializeIfNeeded()
            Log.d(TAG, "【启动计时】功能提示词管理器初始化完成（异步） - ${System.currentTimeMillis() - characterStartTime}ms")
        }

        // 初始化自定义表情
        applicationScope.launch {
            val emojiStartTime = System.currentTimeMillis()
            CustomEmojiRepository.getInstance(applicationContext).initializeBuiltinEmojis()
            Log.d(TAG, "【启动计时】自定义表情初始化完成（异步） - ${System.currentTimeMillis() - emojiStartTime}ms")
        }

        // 在最早时机初始化并应用语言设置
        initializeAppLanguage()
        Log.d(TAG, "【启动计时】语言设置初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化AndroidShellExecutor上下文
        AndroidShellExecutor.setContext(applicationContext)
        Log.d(TAG, "【启动计时】AndroidShellExecutor初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化PDFBox资源加载器
        PDFBoxResourceLoader.init(getApplicationContext());
        Log.d(TAG, "【启动计时】PDFBox资源加载器初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化语言支持
        LanguageFactory.init()
        Log.d(TAG, "【启动计时】语言工厂初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 初始化TextSegmenter
        applicationScope.launch {
            val segmenterStartTime = System.currentTimeMillis()
            TextSegmenter.initialize(applicationContext)
            Log.d(TAG, "【启动计时】TextSegmenter初始化完成（异步） - ${System.currentTimeMillis() - segmenterStartTime}ms")
        }
        
        // Initialize WaifuMessageProcessor
        WaifuMessageProcessor.initialize(applicationContext)
        Log.d(TAG, "【启动计时】WaifuMessageProcessor初始化完成 - ${System.currentTimeMillis() - startTime}ms")

        // 预加载数据库
        applicationScope.launch {
            val dbStartTime = System.currentTimeMillis()
            // 简单访问数据库以触发初始化
            database.problemDao().getProblemCount()
            Log.d(TAG, "【启动计时】数据库预加载完成（异步） - ${System.currentTimeMillis() - dbStartTime}ms")
        }

        // 初始化全局图片加载器，设置强大的缓存策略
        globalImageLoader =
                ImageLoader.Builder(this)
                        .crossfade(true)
                        .respectCacheHeaders(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .diskCache {
                            DiskCache.Builder()
                                    .directory(filesDir.resolve("image_cache"))
                                    .maxSizeBytes(50 * 1024 * 1024) // 50MB磁盘缓存上限，比百分比更精确
                                    .build()
                        }
                        .memoryCache {
                            // 设置内存缓存最大大小为应用可用内存的15%
                            coil.memory.MemoryCache.Builder(this).maxSizePercent(0.15).build()
                        }
                        .build()
        Log.d(TAG, "【启动计时】全局图片加载器初始化完成 - ${System.currentTimeMillis() - startTime}ms")
        
        // 初始化图片池管理器，支持本地持久化缓存
        ImagePoolManager.initialize(filesDir)
        Log.d(TAG, "【启动计时】图片池管理器初始化完成 - ${System.currentTimeMillis() - startTime}ms")
        
        val totalTime = System.currentTimeMillis() - startTime
        Log.d(TAG, "【启动计时】应用启动全部完成 - 总耗时: ${totalTime}ms")
    }

    /** 初始化应用语言设置 */
    private fun initializeAppLanguage() {
        try {
            // 同步获取已保存的语言设置
            val languageCode = runBlocking {
                try {
                    // 使用更安全的方式检查preferencesManager
                    val manager = runCatching { preferencesManager }.getOrNull()
                    if (manager != null) {
                        manager.appLanguage.first()
                    } else {
                        UserPreferencesManager.DEFAULT_LANGUAGE
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "获取语言设置失败", e)
                    UserPreferencesManager.DEFAULT_LANGUAGE
                }
            }

            Log.d(TAG, "获取语言设置: $languageCode")

            // 立即应用语言设置
            val locale = Locale(languageCode)
            // 设置默认语言
            Locale.setDefault(locale)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13+ 使用AppCompatDelegate API
                val localeList = LocaleListCompat.create(locale)
                AppCompatDelegate.setApplicationLocales(localeList)
                Log.d(TAG, "使用AppCompatDelegate设置语言: $languageCode")
            } else {
                // 较旧版本Android - 此处使用的部分更新将在attachBaseContext中完成更完整更新
                val config = Configuration()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val localeList = LocaleList(locale)
                    LocaleList.setDefault(localeList)
                    config.setLocales(localeList)
                } else {
                    config.locale = locale
                }

                resources.updateConfiguration(config, resources.displayMetrics)
                Log.d(TAG, "使用Configuration设置语言: $languageCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化语言设置失败", e)
        }
    }

    override fun attachBaseContext(base: Context) {
        // 在基础上下文附加前应用语言设置
        try {
            val code = LocaleUtils.getCurrentLanguage(base)
            val locale = Locale(code)
            val config = Configuration(base.resources.configuration)

            // 设置语言配置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val localeList = LocaleList(locale)
                LocaleList.setDefault(localeList)
                config.setLocales(localeList)
            } else {
                config.locale = locale
                Locale.setDefault(locale)
            }

            // 使用createConfigurationContext创建新的上下文
            val context = base.createConfigurationContext(config)
            super.attachBaseContext(context)
            Log.d(TAG, "成功应用基础上下文语言: $code")
        } catch (e: Exception) {
            Log.e(TAG, "应用基础上下文语言失败", e)
            super.attachBaseContext(base)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        // 在应用终止时关闭LocalWebServer服务器
        try {
            val webServer = LocalWebServer.getInstance(applicationContext, LocalWebServer.ServerType.WORKSPACE)
            if (webServer.isRunning()) {
                webServer.stop()
                Log.d(TAG, "应用终止，已关闭本地Web服务器")
            }
            val computerServer = LocalWebServer.getInstance(applicationContext, LocalWebServer.ServerType.COMPUTER)
            if (computerServer.isRunning()) {
                computerServer.stop()
                Log.d(TAG, "应用终止，已关闭AI电脑服务器")
            }
        } catch (e: Exception) {
            Log.e(TAG, "关闭本地Web服务器失败: ${e.message}", e)
        }
    }
}
