package com.ai.assistance.operit.ui.main

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.invitation.InvitationManager
import com.ai.assistance.operit.core.invitation.ProcessInvitationResult
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.migration.ChatHistoryMigrationManager
import com.ai.assistance.operit.data.preferences.AgreementPreferences
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.androidPermissionPreferences
import com.ai.assistance.operit.data.updates.UpdateManager
import com.ai.assistance.operit.data.updates.UpdateStatus
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.features.agreement.screens.AgreementScreen
import com.ai.assistance.operit.ui.features.migration.screens.MigrationScreen
import com.ai.assistance.operit.ui.features.permission.screens.PermissionGuideScreen
import com.ai.assistance.operit.ui.features.startup.screens.PluginLoadingScreenWithState
import com.ai.assistance.operit.ui.features.startup.screens.PluginLoadingState
import com.ai.assistance.operit.ui.theme.OperitTheme
import com.ai.assistance.operit.util.AnrMonitor
import com.ai.assistance.operit.util.LocaleUtils
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.ClipboardManager
import android.content.ClipData
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.preferences.GitHubAuthBus
import android.content.Intent

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    // ======== 工具和管理器 ========
    private lateinit var toolHandler: AIToolHandler
    private lateinit var preferencesManager: UserPreferencesManager
    private lateinit var agreementPreferences: AgreementPreferences
    private lateinit var invitationManager: InvitationManager // Add InvitationManager instance
    private var updateCheckPerformed = false
    private lateinit var anrMonitor: AnrMonitor
    private lateinit var mcpRepository: MCPRepository

    // ======== 对话框状态 ========
    private var showConfirmationDialogState by mutableStateOf<String?>(null)
    private var showReminderDialogState by mutableStateOf<String?>(null)

    // ======== 导航状态 ========
    private var showPreferencesGuide by mutableStateOf(false)

    // ======== MCP插件状态 ========
    private val pluginLoadingState = PluginLoadingState()

    // ======== 数据迁移状态 ========
    private lateinit var migrationManager: ChatHistoryMigrationManager
    private var showMigrationScreen by mutableStateOf(false)

    // ======== 双击返回退出相关变量 ========
    private var backPressedTime: Long = 0
    private val backPressedInterval: Long = 2000 // 两次点击的时间间隔，单位为毫秒

    // UpdateManager实例
    private lateinit var updateManager: UpdateManager

    // 是否显示权限引导界面
    private var showPermissionGuide by mutableStateOf(false)

    // 是否已完成权限和迁移检查
    private var initialChecksDone = false

    override fun attachBaseContext(newBase: Context) {
        // 获取当前设置的语言
        val code = LocaleUtils.getCurrentLanguage(newBase)
        val locale = Locale(code)
        val config = Configuration(newBase.resources.configuration)

        // 设置语言配置
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        } else {
            config.locale = locale
            Locale.setDefault(locale)
        }

        // 使用createConfigurationContext创建新的本地化上下文
        val context = newBase.createConfigurationContext(config)
        super.attachBaseContext(context)
        Log.d(TAG, "MainActivity应用语言设置: $code")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Android SDK version: ${Build.VERSION.SDK_INT}")

        // Set window background to solid color to prevent system theme leaking through
        window.setBackgroundDrawableResource(android.R.color.black)

        // Handle the intent that started the activity
        handleIntent(intent)

        // 语言设置已在Application中初始化，这里无需重复

        initializeComponents()
        cleanTemporaryFiles()
        anrMonitor.start()
        setupPreferencesListener()
        configureDisplaySettings()

        // 设置上下文以便获取插件元数据
        pluginLoadingState.setAppContext(applicationContext)

        // 设置跳过加载的回调
        pluginLoadingState.setOnSkipCallback {
            Log.d(TAG, "用户跳过了插件加载过程")
            Toast.makeText(this, getString(R.string.plugin_loading_skipped), Toast.LENGTH_SHORT).show()
        }

        // 设置初始界面 - 显示加载占位符
        setAppContent()

        // 初始化并设置更新管理器
        setupUpdateManager()

        // 只在首次创建时执行检查（非配置变更）
        if (savedInstanceState == null) {
            // 进行必要的初始检查
            performInitialChecks()
        } else {
            // 配置变更时不重新检查，直接显示主界面
            initialChecksDone = true
        }

        // 设置双击返回退出
        setupBackPressHandler()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent: Received intent")
        intent?.data?.let { uri ->
            if (uri.scheme == "operit" && uri.host == "github-oauth-callback") {
                val code = uri.getQueryParameter("code")
                if (code != null) {
                    Log.d(TAG, "GitHub OAuth code received: $code")
                    GitHubAuthBus.postAuthCode(code)
                } else {
                    val error = uri.getQueryParameter("error")
                    Log.e(TAG, "GitHub OAuth error: $error")
                }
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            if (uri.scheme == "operit" && uri.host == "github-oauth-callback") {
                val code = uri.getQueryParameter("code")
                if (code != null) {
                    Log.d(TAG, "GitHub OAuth code received from onCreate: $code")
                    GitHubAuthBus.postAuthCode(code)
                } else {
                    val error = uri.getQueryParameter("error")
                    Log.e(TAG, "GitHub OAuth error from onCreate: $error")
                }
            }
        }
    }

    // ======== 设置初始占位内容 ========

    // ======== 执行初始化检查 ========
    private fun performInitialChecks() {
        lifecycleScope.launch {
            // 1. 检查权限级别设置
            checkPermissionLevelSet()

            // 2. 检查是否需要数据迁移
            if (!showPermissionGuide && agreementPreferences.isAgreementAccepted()) {
                try {
                    val needsMigration = migrationManager.needsMigration()
                    Log.d(TAG, "数据迁移检查: 需要迁移=$needsMigration")

                    showMigrationScreen = needsMigration

                    // 如果不需要迁移，直接启动插件加载
                    if (!needsMigration) {
                        startPluginLoading()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "数据迁移检查失败", e)
                    // 检查失败，跳过迁移直接加载插件
                    startPluginLoading()
                }
            }

            // 标记完成初始检查
            initialChecksDone = true

            // 设置应用内容
            setAppContent()
        }
    }

    // ======== 检查数据迁移 ========
    private fun checkMigrationNeeded() {
        lifecycleScope.launch {
            try {
                // 检查是否需要迁移数据
                val needsMigration = migrationManager.needsMigration()
                Log.d(TAG, "数据迁移检查: 需要迁移=$needsMigration")

                if (needsMigration) {
                    showMigrationScreen = true
                    setAppContent()
                } else {
                    // 不需要迁移，显示插件加载界面
                    startPluginLoading()
                }
            } catch (e: Exception) {
                Log.e(TAG, "数据迁移检查失败", e)
                // 检查失败，跳过迁移直接加载插件
                startPluginLoading()
            }
        }
    }

    // ======== 启动插件加载 ========
    private fun startPluginLoading() {
        // 显示插件加载界面
        pluginLoadingState.show()

        // 启动超时检测（30秒）
        pluginLoadingState.startTimeoutCheck(30000L, lifecycleScope)

        // 初始化MCP服务器并启动插件
        pluginLoadingState.initializeMCPServer(applicationContext, lifecycleScope)
    }

    // 配置双击返回退出的处理器
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(
                this,
                object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        val currentTime = System.currentTimeMillis()

                        if (currentTime - backPressedTime > backPressedInterval) {
                            // 第一次点击，显示提示
                            backPressedTime = currentTime
                            Toast.makeText(this@MainActivity, getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show()
                        } else {
                            // 第二次点击，退出应用
                            finish()
                        }
                    }
                }
        )
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")

        // Check clipboard for invitation code when the app resumes
        checkClipboardForInvitation()
    }

    private fun checkClipboardForInvitation() {
        lifecycleScope.launch {
            // 等待一小段时间，确保应用完全获得焦点，避免因Android 10+剪贴板限制导致读取失败
            delay(500)

            Log.d(TAG, "检查剪贴板中的邀请码...")
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipData = clipboard?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val text = clipData.getItemAt(0).coerceToText(this@MainActivity).toString()
                if (text.isNotBlank()) {
                    Log.d(TAG, "剪贴板内容: '$text'")
                    when (val result = invitationManager.processInvitationFromText(text)) {
                        is ProcessInvitationResult.Success -> {
                            Log.d(TAG, "邀请码处理成功: ${result.confirmationCode}")
                            // Clear clipboard to prevent re-triggering
                            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                            showConfirmationDialogState = result.confirmationCode
                        }
                        is ProcessInvitationResult.Reminder -> {
                            Log.d(TAG, "邀请码提醒: ${result.confirmationCode}")
                            // Clear clipboard to prevent re-triggering
                            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                            showReminderDialogState = result.confirmationCode
                        }
                        is ProcessInvitationResult.Failure -> Log.d(TAG, "Clipboard check failed: ${result.reason}")
                        is ProcessInvitationResult.AlreadyInvited -> Log.d(TAG, "Device already invited by someone else.")
                    }
                } else {
                    Log.d(TAG, "剪贴板内容为空白。")
                }
            } else {
                Log.d(TAG, "剪贴板为空或无项目。")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")

        // 确保隐藏加载界面
        pluginLoadingState.hide()

        anrMonitor.stop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "onConfigurationChanged: orientation=${newConfig.orientation}")

        // 屏幕方向变化时，确保加载界面不可见
        pluginLoadingState.hide()
    }

    // ======== 初始化组件 ========
    private fun initializeComponents() {
        // 初始化工具处理器
        toolHandler = AIToolHandler.getInstance(this)
        toolHandler.registerDefaultTools()

        // 初始化MCP仓库
        mcpRepository = MCPRepository(this)

        // Initialize InvitationManager
        invitationManager = InvitationManager(this)

        anrMonitor = AnrMonitor(this, lifecycleScope)

        // 初始化用户偏好管理器并直接检查初始化状态
        preferencesManager = UserPreferencesManager(this)
        showPreferencesGuide = !preferencesManager.isPreferencesInitialized()
        Log.d(
                TAG,
                "初始化检查: 用户偏好已初始化=${!showPreferencesGuide}，将${if(showPreferencesGuide) "" else "不"}显示引导界面"
        )

        // 初始化协议偏好管理器
        agreementPreferences = AgreementPreferences(this)

        // 初始化数据迁移管理器
        migrationManager = ChatHistoryMigrationManager(this)
    }

    // ======== 检查权限级别设置 ========
    private fun checkPermissionLevelSet() {
        // 检查是否已设置权限级别
        val permissionLevel = androidPermissionPreferences.getPreferredPermissionLevel()
        Log.d(TAG, "当前权限级别: $permissionLevel")
        showPermissionGuide = permissionLevel == null
        Log.d(
                TAG,
                "权限级别检查: 已设置=${!showPermissionGuide}, 将${if(showPermissionGuide) "" else "不"}显示权限引导界面"
        )
    }

    // ======== 偏好监听器设置 ========
    private fun setupPreferencesListener() {
        // 监听偏好变化
        lifecycleScope.launch {
            preferencesManager.getUserPreferencesFlow().collect { profile ->
                // 只有当状态变化时才更新UI
                val newValue = !profile.isInitialized
                if (showPreferencesGuide != newValue) {
                    Log.d(TAG, "偏好变更: 从 $showPreferencesGuide 变为 $newValue")
                    showPreferencesGuide = newValue
                    setAppContent()
                }
            }
        }
    }

    // ======== 显示与性能配置 ========
    private fun configureDisplaySettings() {
        // 1. 请求持续的高性能模式 (API 31+)
        // 这会提示系统为应用提供持续的高性能，避免CPU/GPU降频。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                window.setSustainedPerformanceMode(true)
                Log.d(TAG, "已成功请求持续高性能模式。")
            } catch (e: Exception) {
                // 在某些设备上，此模式可能不可用或不支持。
                Log.w(TAG, "请求持续高性能模式失败。", e)
            }
        }

        // 2. 设置应用以最高刷新率运行
        // 高刷新率优化：通过设置窗口属性确保流畅
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 为Android 11+设备优化高刷新率
            val highestMode = getHighestRefreshRate()
            if (highestMode > 0) {
                window.attributes.preferredDisplayModeId = highestMode
                Log.d(TAG, "设置窗口首选显示模式ID: $highestMode")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 为Android 6.0-10设备优化高刷新率
            val refreshRate = getDeviceRefreshRate()
            if (refreshRate > 60f) {
                window.attributes.preferredRefreshRate = refreshRate
                Log.d(TAG, "设置窗口首选刷新率: $refreshRate Hz")
            }
        }

        // 启用硬件加速以提高渲染性能
        window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
    }

    // ======== 设置应用内容 ========
    private fun setAppContent() {
        setContent {
            OperitTheme {
                Box {
                    // 如果初始化检查未完成，则显示一个占位符，避免在检查完成前显示不完整的界面
                    if (!initialChecksDone) {
                        // 在这里可以放置一个加载指示器，或者一个空白屏幕
                        // 为了简单起见，我们暂时留空，因为检查过程很快
                    } else {
                        // 检查是否需要显示用户协议
                        if (!agreementPreferences.isAgreementAccepted()) {
                            AgreementScreen(
                                    onAgreementAccepted = {
                                        agreementPreferences.setAgreementAccepted(true)
                                        // 协议接受后，检查权限级别设置
                                        lifecycleScope.launch {
                                            // 确保使用非阻塞方式更新UI
                                            delay(300) // 短暂延迟确保UI状态更新
                                            checkPermissionLevelSet()
                                            // 重新设置应用内容
                                            setAppContent()
                                        }
                                    }
                            )
                        }
                        // 检查是否需要显示数据迁移界面
                        else if (showMigrationScreen) {
                            MigrationScreen(
                                    migrationManager = migrationManager,
                                    onComplete = {
                                        showMigrationScreen = false
                                        // 迁移完成后，启动插件加载
                                        startPluginLoading()
                                        // 重新设置应用内容
                                        setAppContent()
                                    }
                            )
                        }
                        // 检查是否需要显示权限引导界面
                        else if (showPermissionGuide) {
                            PermissionGuideScreen(
                                    onComplete = {
                                        showPermissionGuide = false
                                        // 权限设置完成后，重新设置应用内容
                                        setAppContent()
                                    }
                            )
                        }
                        // 显示主应用界面
                        else {
                            // 主应用界面 (始终存在于底层)
                            OperitApp(
                                    initialNavItem =
                                            when {
                                                showPreferencesGuide -> NavItem.UserPreferencesGuide
                                                else -> NavItem.AiChat
                                            },
                                    toolHandler = toolHandler
                            )
                        }
                    }
                    // 插件加载界面 (带有淡出效果) - 始终在最上层
                    PluginLoadingScreenWithState(
                            loadingState = pluginLoadingState,
                            modifier = Modifier.zIndex(10f) // 确保加载界面在最上层
                    )

                    // 显示邀请结果对话框
                    showConfirmationDialogState?.let { code ->
                        InvitationResultDialog(
                            title = "邀请已接受！",
                            message = "请将以下返回码发送给你的朋友，以完成最终邀请步骤：\n\n$code",
                            confirmationCode = code,
                            onDismiss = { showConfirmationDialogState = null }
                        )
                    }

                    showReminderDialogState?.let { code ->
                        InvitationResultDialog(
                            title = "是不是忘记了什么？",
                            message = "你好像又被同一个人邀请了呢，是不是忘记把下面的返回码发给他了？拿稳！\n\n$code",
                            confirmationCode = code,
                            onDismiss = { showReminderDialogState = null }
                        )
                    }
                }
            }
        }
    }

    // ======== 设置更新管理器 ========
    private fun setupUpdateManager() {
        // 获取UpdateManager实例
        updateManager = UpdateManager.getInstance(this)

        // 观察更新状态
        updateManager.updateStatus.observe(
                this,
                Observer { status ->
                    if (status is UpdateStatus.Available) {
                        showUpdateNotification(status)
                    }
                }
        )

        // 自动检查更新
        lifecycleScope.launch {
            // 延迟几秒，等待应用完全启动
            delay(3000)
            checkForUpdates()
        }
    }

    private fun checkForUpdates() {
        if (updateCheckPerformed) return
        updateCheckPerformed = true

        val appVersion =
                try {
                    packageManager.getPackageInfo(packageName, 0).versionName
                } catch (e: PackageManager.NameNotFoundException) {
                    "未知"
                }

        // 使用UpdateManager检查更新
        lifecycleScope.launch {
            try {
                updateManager.checkForUpdates(appVersion)
                // 不需要显式处理更新状态，因为我们已经设置了观察者
            } catch (e: Exception) {
                Log.e(TAG, "更新检查失败: ${e.message}")
            }
        }
    }

    private fun showUpdateNotification(updateInfo: UpdateStatus.Available) {
        val currentVersion =
                try {
                    packageManager.getPackageInfo(packageName, 0).versionName
                } catch (e: Exception) {
                    "未知"
                }

        Log.d(TAG, "发现新版本: ${updateInfo.newVersion}，当前版本: $currentVersion")

        // 显示更新提示
        val updateMessage = "发现新版本 ${updateInfo.newVersion}，请前往「关于」页面查看详情"
        Toast.makeText(this, updateMessage, Toast.LENGTH_LONG).show()
    }

    private fun getHighestRefreshRate(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val displayModes = display?.supportedModes ?: return 0
            var maxRefreshRate = 60f // Default to 60Hz
            var highestModeId = 0

            for (mode in displayModes) {
                if (mode.refreshRate > maxRefreshRate) {
                    maxRefreshRate = mode.refreshRate
                    highestModeId = mode.modeId
                }
            }
            Log.d(TAG, "Selected display mode with refresh rate: $maxRefreshRate Hz")
            return highestModeId
        }
        return 0
    }

    private fun getDeviceRefreshRate(): Float {
        val windowManager = getSystemService(WINDOW_SERVICE) as android.view.WindowManager
        val display =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display
                } else {
                    @Suppress("DEPRECATION") windowManager.defaultDisplay
                }

        var refreshRate = 60f // Default refresh rate

        if (display != null) {
            try {
                @Suppress("DEPRECATION") val modes = display.supportedModes
                for (mode in modes) {
                    if (mode.refreshRate > refreshRate) {
                        refreshRate = mode.refreshRate
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting refresh rate", e)
            }
        }

        Log.d(TAG, "Selected refresh rate: $refreshRate Hz")
        return refreshRate
    }

    /** 清理临时文件目录 删除Download/Operit/cleanOnExit目录中的所有文件 */
    private fun cleanTemporaryFiles() {
        lifecycleScope.launch {
            try {
                val tempDir = java.io.File("/sdcard/Download/Operit/cleanOnExit")
                if (tempDir.exists() && tempDir.isDirectory) {
                    // 确保.nomedia文件存在
                    val noMediaFile = java.io.File(tempDir, ".nomedia")
                    if (!noMediaFile.exists()) {
                        noMediaFile.createNewFile()
                    }
                    
                    Log.d(TAG, "开始清理临时文件目录: ${tempDir.absolutePath}")
                    val files = tempDir.listFiles()
                    var deletedCount = 0

                    files?.forEach { file ->
                        if (file.isFile && file.name != ".nomedia" && file.delete()) {
                            deletedCount++
                        }
                    }

                    Log.d(TAG, "已删除${deletedCount}个临时文件")
                }
            } catch (e: Exception) {
                Log.e(TAG, "清理临时文件失败", e)
            }
        }
    }
}

@Composable
private fun InvitationResultDialog(
    title: String,
    message: String,
    confirmationCode: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Confirmation Code", confirmationCode)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, context.getString(R.string.confirmation_code_copied), Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            ) {
                Text("复制返回码")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
