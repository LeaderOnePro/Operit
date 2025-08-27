package com.ai.assistance.operit.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.ai.assistance.operit.data.preferences.preferencesManager
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** 语言工具类，用于管理应用的国际化设置 */
object LocaleUtils {

    const val AUTO_LANGUAGE_CODE = "system"

    /**
     * 语言信息数据类
     * @param code 语言代码（如zh、en）
     * @param displayName 显示名称（英文）
     * @param nativeName 本地名称（语言自身的称呼）
     */
    data class Language(val code: String, val displayName: String, val nativeName: String)

    /** 获取支持的语言列表 */
    fun getSupportedLanguages(): List<Language> {
        return listOf(
                Language(AUTO_LANGUAGE_CODE, "Follow system", "跟随系统"),
                Language("zh", "Chinese", "中文"),
                Language("en", "English", "English")
                // 可以添加更多支持的语言
                )
    }

    /**
     * 获取包含当前应用语言设置的上下文。
     * 对于使用applicationContext的单例或服务，这非常有用，
     * 因为它可以确保获取到最新的本地化资源。
     *
     * @param context 基础上下文.
     * @return 带有更新后语言配置的新上下文.
     */
    fun getLocalizedContext(context: Context): Context {
        val lang = getCurrentLanguage(context)
        val locale = if (lang.isNotEmpty() && lang != AUTO_LANGUAGE_CODE) {
            Locale(lang)
        } else {
            // 如果是“跟随系统”或为空，则回退到系统默认语言
            val systemLanguage = getCurrentSystemLanguage(context)
            Locale(systemLanguage)
        }

        val configuration = Configuration(context.resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
            val localeList = LocaleList(locale)
            configuration.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            configuration.setLocale(locale)
        }

        return context.createConfigurationContext(configuration)
    }

    /**
     * 获取当前应用设置的语言
     * @param context 上下文
     * @return 当前语言代码，如zh、en
     */
    fun getCurrentLanguage(context: Context): String {
        // 优先从全局初始化的preferencesManager获取
        try {
            // 使用更安全的方式检查preferencesManager是否已初始化
            val manager = runCatching { preferencesManager }.getOrNull()
            if (manager != null) {
                val savedLanguage = manager.getCurrentLanguage()
                // 如果不是“跟随系统”，则返回保存的语言
                if (savedLanguage.isNotEmpty() && savedLanguage != AUTO_LANGUAGE_CODE) {
                    return savedLanguage
                }
            }
        } catch (e: Exception) {
            // 错误时静默处理
        }

        // 如果是“跟随系统”或无法获取，则从系统中获取
        return getCurrentSystemLanguage(context)
    }

    /** 获取系统当前语言 */
    private fun getCurrentSystemLanguage(context: Context): String {
        val currentLocale =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.resources.configuration.locales.get(0)
                } else {
                    context.resources.configuration.locale
                }

        return currentLocale.language
    }

    /**
     * 设置应用语言
     * @param context 上下文
     * @param languageCode 语言代码，如zh、en
     */
    fun setAppLanguage(context: Context, languageCode: String) {
        
        // 保存到偏好设置 - 只使用全局已初始化的实例
        try {
            // 使用更安全的方式检查preferencesManager是否已初始化
            val manager = runCatching { preferencesManager }.getOrNull()
            if (manager != null) {
                runBlocking(Dispatchers.IO) {
                    manager.saveAppLanguage(languageCode)
                }
            }
        } catch (e: Exception) {
            // 错误时静默处理
        }

        // 根据 languageCode 获取相应的 Locale
        val localeToSet = if (languageCode == AUTO_LANGUAGE_CODE) {
            // 跟随系统语言
            val systemLanguage = getCurrentSystemLanguage(context)
            Locale(systemLanguage)
        } else {
            // 设置为特定语言
            Locale(languageCode)
        }
        
        // 设置默认语言
        Locale.setDefault(localeToSet)
        
        // 根据Android版本应用语言设置
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用AppCompatDelegate API
            val localeList = LocaleListCompat.create(localeToSet)
            AppCompatDelegate.setApplicationLocales(localeList)
        } else {
            // 较旧版本Android使用资源配置
            try {
                val config = Configuration(context.resources.configuration)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val localeList = LocaleList(localeToSet)
                    LocaleList.setDefault(localeList)
                    config.setLocales(localeList)
                } else {
                    config.locale = localeToSet
                }
                
                // 更新上下文资源配置
                @Suppress("DEPRECATION")
                context.resources.updateConfiguration(config, context.resources.displayMetrics)
                
                // 尝试更新Activity
                try {
                    val ctx = context.applicationContext
                    if (ctx is ContextWrapper) {
                        val baseContext = ctx.baseContext
                        if (baseContext != null) {
                            @Suppress("DEPRECATION")
                            baseContext.resources.updateConfiguration(
                                config, 
                                baseContext.resources.displayMetrics
                            )
                        }
                    }
                } catch (e: Exception) {
                    // 忽略无法更新的上下文
                }
            } catch (e: Exception) {
                // 错误时静默处理
            }
        }
    }
}
