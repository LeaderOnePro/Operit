package com.ai.assistance.operit.ui.features.demo.state

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.ai.assistance.operit.core.tools.system.OperitTerminalManager
import com.ai.assistance.operit.core.tools.system.RootAuthorizer
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "DemoStateManager"

/**
 * Consolidated state management for the demo screens. Handles state initialization, updates, and
 * listeners for Shizuku and other features.
 */
class DemoStateManager(private val context: Context, private val coroutineScope: CoroutineScope) {
    // Main UI state holder
    private val _uiState = MutableStateFlow(DemoScreenState())
    val uiState: StateFlow<DemoScreenState> = _uiState.asStateFlow()

    // OperitTerminal states
    var operitTerminalInstalledVersion = mutableStateOf<String?>(null)
    var operitTerminalLatestVersion = mutableStateOf<String?>(null)
    var operitTerminalDownloadUrl = mutableStateOf<String?>(null)
    var operitTerminalReleaseNotes = mutableStateOf<String?>(null)
    var isOperitTerminalUpdateNeeded = mutableStateOf(false)

    // Shizuku state change listeners
    private val shizukuListener: () -> Unit = { refreshStatus() }

    // Root state change listener
    private val rootListener: () -> Unit = { refreshStatus() }

    init {
        // Register listeners for Shizuku and Root state changes
        ShizukuAuthorizer.addStateChangeListener(shizukuListener)
        RootAuthorizer.addStateChangeListener(rootListener)
    }

    /** Initialize state */
    fun initialize() {
        coroutineScope.launch {
            Log.d(TAG, "初始化状态...")
            registerStateChangeListeners()
            refreshStatusAsync()
        }
    }

    /** Refresh permissions and component status */
    fun refreshStatus() {
       coroutineScope.launch {
           refreshStatusAsync()
       }
    }

    /** Update root status */
    fun updateRootStatus(isDeviceRooted: Boolean, hasRootAccess: Boolean) {
        _uiState.update { currentState ->
            currentState.copy(
                    isDeviceRooted = mutableStateOf(isDeviceRooted),
                    hasRootAccess = mutableStateOf(hasRootAccess)
            )
        }

        // 如果设备已Root但未获取权限，则显示Root向导
        if (isDeviceRooted && !hasRootAccess) {
            _uiState.update { currentState ->
                currentState.copy(showRootWizard = mutableStateOf(true))
            }
        }
    }

    /** Update UI state */
    fun updateOutputText(text: String) {
        // This function is kept for compatibility but might be repurposed or removed.
    }

    /** Dialog management */
    fun showResultDialog(title: String, content: String) {
        _uiState.update { currentState ->
            currentState.copy(
                    resultDialogTitle = mutableStateOf(title),
                    resultDialogContent = mutableStateOf(content),
                    showResultDialogState = mutableStateOf(true)
            )
        }
    }

    fun hideResultDialog() {
        _uiState.update { currentState ->
            currentState.copy(showResultDialogState = mutableStateOf(false))
        }
    }

    /** Toggle UI visibility */
    fun toggleShizukuWizard() {
        _uiState.update { currentState ->
            currentState.copy(
                    showShizukuWizard = mutableStateOf(!currentState.showShizukuWizard.value)
            )
        }
    }

    fun toggleOperitTerminalWizard() {
        _uiState.update { currentState ->
            currentState.copy(
                showOperitTerminalWizard = mutableStateOf(!currentState.showOperitTerminalWizard.value)
            )
        }
    }

    fun toggleRootWizard() {
        _uiState.update { currentState ->
            currentState.copy(showRootWizard = mutableStateOf(!currentState.showRootWizard.value))
        }
    }

    fun toggleAccessibilityWizard() {
        _uiState.update { currentState ->
            currentState.copy(
                showAccessibilityWizard = mutableStateOf(!currentState.showAccessibilityWizard.value)
            )
        }
    }

    fun toggleAdbCommandExecutor() {
        _uiState.update { currentState ->
            currentState.copy(
                    showAdbCommandExecutor =
                            mutableStateOf(!currentState.showAdbCommandExecutor.value)
            )
        }
    }

    fun toggleSampleCommands() {
        _uiState.update { currentState ->
            currentState.copy(
                    showSampleCommands = mutableStateOf(!currentState.showSampleCommands.value)
            )
        }
    }

    /** Command handling */
    fun updateCommandText(text: String) {
        _uiState.update { currentState -> currentState.copy(commandText = mutableStateOf(text)) }
    }

    fun updateResultText(text: String) {
        _uiState.update { currentState -> currentState.copy(resultText = mutableStateOf(text)) }
    }

    /** Clean up resources */
    fun cleanup() {
        // Remove listeners
        ShizukuAuthorizer.removeStateChangeListener(shizukuListener)
        RootAuthorizer.removeStateChangeListener(rootListener)
    }

    private fun registerStateChangeListeners() {
        // Implementation of registerStateChangeListeners method
    }

    /** Set loading state */
    fun setLoading(isLoading: Boolean) {
        _uiState.update { currentState -> currentState.copy(isLoading = mutableStateOf(isLoading)) }
    }

    /** Initialize state asynchronously */
    suspend fun initializeAsync() {
        Log.d(TAG, "异步初始化状态...")
        registerStateChangeListeners()
        refreshStatusAsync()
    }

    /** Refresh permissions and component status asynchronously */
    private suspend fun refreshStatusAsync() {
        _uiState.update { currentState -> currentState.copy(isRefreshing = mutableStateOf(true)) }

        try {
            // Refresh permissions and status
            refreshPermissionsAndStatus(
                    context = context,
                    updateShizukuInstalled = { _uiState.value.isShizukuInstalled.value = it },
                    updateShizukuRunning = { _uiState.value.isShizukuRunning.value = it },
                    updateShizukuPermission = { _uiState.value.hasShizukuPermission.value = it },
                    updateOperitTerminalInstalled = { _uiState.value.isOperitTerminalInstalled.value = it },
                    updateOperitTerminalRunning = { isOperitTerminalRunning -> 
                        // Add logic if needed for OperitTerminal running state
                    },
                    updateStoragePermission = { _uiState.value.hasStoragePermission.value = it },
                    updateLocationPermission = { _uiState.value.hasLocationPermission.value = it },
                    updateOverlayPermission = { _uiState.value.hasOverlayPermission.value = it },
                    updateBatteryOptimizationExemption = {
                        _uiState.value.hasBatteryOptimizationExemption.value = it
                    },
                    updateAccessibilityProviderInstalled = {
                        _uiState.value.isAccessibilityProviderInstalled.value = it
                    },
                    updateAccessibilityServiceEnabled = {
                        _uiState.value.hasAccessibilityServiceEnabled.value = it
                    }
            )

            // Check Shizuku API_V23 permission
            if (_uiState.value.isShizukuInstalled.value && _uiState.value.isShizukuRunning.value) {
                _uiState.value.hasShizukuPermission.value = ShizukuAuthorizer.hasShizukuPermission()

                if (!_uiState.value.hasShizukuPermission.value) {
                    Log.d(TAG, "缺少Shizuku API_V23权限，显示Shizuku向导卡片")
                    _uiState.value.showShizukuWizard.value = true
                }
            } else {
                _uiState.value.hasShizukuPermission.value = false
                _uiState.value.showShizukuWizard.value = true
            }

            // Check OperitTerminal status
            refreshOperitTerminalStatus()

            // 延迟300ms以确保UI能够刷新
            delay(300)
        } catch (e: Exception) {
            Log.e(TAG, "刷新权限状态时出错: ${e.message}", e)
        } finally {
            _uiState.update { currentState ->
                currentState.copy(isRefreshing = mutableStateOf(false))
            }
        }
    }

    private suspend fun refreshOperitTerminalStatus() {
        val isInstalled = OperitTerminalManager.isInstalled(context)
        _uiState.value.isOperitTerminalInstalled.value = isInstalled

        val installedVersion = if (isInstalled) OperitTerminalManager.getInstalledVersion(context) else null
        operitTerminalInstalledVersion.value = installedVersion

        val releaseInfo = OperitTerminalManager.fetchLatestReleaseInfo()
        val latestVersion = releaseInfo?.version
        operitTerminalLatestVersion.value = latestVersion
        operitTerminalDownloadUrl.value = releaseInfo?.downloadUrl
        operitTerminalReleaseNotes.value = releaseInfo?.releaseNotes

        isOperitTerminalUpdateNeeded.value = if (installedVersion != null && latestVersion != null) {
            try {
                // "1.10" vs "1.2"
                val installedParts = installedVersion.split('.').map { it.toInt() }
                val latestParts = latestVersion.split('.').map { it.toInt() }
                val installedMajor = installedParts.getOrElse(0) { 0 }
                val installedMinor = installedParts.getOrElse(1) { 0 }
                val latestMajor = latestParts.getOrElse(0) { 0 }
                val latestMinor = latestParts.getOrElse(1) { 0 }

                latestMajor > installedMajor || (latestMajor == installedMajor && latestMinor > installedMinor)
            } catch (e: NumberFormatException) {
                Log.w(TAG, "版本号格式错误: installed='$installedVersion', latest='$latestVersion'", e)
                false // Fallback to no update if versions are weird
            }
        } else {
            false
        }
    }
}

/** 刷新应用权限和组件状态 */
suspend fun refreshPermissionsAndStatus(
    context: Context,
    updateShizukuInstalled: (Boolean) -> Unit,
    updateShizukuRunning: (Boolean) -> Unit,
    updateShizukuPermission: (Boolean) -> Unit,
    updateOperitTerminalInstalled: (Boolean) -> Unit,
    updateOperitTerminalRunning: (Boolean) -> Unit,
    updateStoragePermission: (Boolean) -> Unit,
    updateLocationPermission: (Boolean) -> Unit,
    updateOverlayPermission: (Boolean) -> Unit,
    updateBatteryOptimizationExemption: (Boolean) -> Unit,
    updateAccessibilityProviderInstalled: (Boolean) -> Unit,
    updateAccessibilityServiceEnabled: (Boolean) -> Unit
) {
    Log.d(TAG, "刷新应用权限状态...")

    // 检查Shizuku安装、运行和权限状态
    val isShizukuInstalled = ShizukuAuthorizer.isShizukuInstalled(context)
    val isShizukuRunning = ShizukuAuthorizer.isShizukuServiceRunning()
    updateShizukuInstalled(isShizukuInstalled)
    updateShizukuRunning(isShizukuRunning)

    // Shizuku权限检查
    val hasShizukuPermission =
        if (isShizukuInstalled && isShizukuRunning) {
            ShizukuAuthorizer.hasShizukuPermission()
        } else {
            false
        }
    updateShizukuPermission(hasShizukuPermission)

    // 检查OperitTerminal是否安装
    val isOperitTerminalInstalled = OperitTerminalManager.isInstalled(context)
    updateOperitTerminalInstalled(isOperitTerminalInstalled)

    // 检查存储权限
    val hasStoragePermission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    updateStoragePermission(hasStoragePermission)

    // 检查位置权限
    val hasLocationPermission =
        context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    updateLocationPermission(hasLocationPermission)

    // 检查悬浮窗权限
    val hasOverlayPermission = Settings.canDrawOverlays(context)
    updateOverlayPermission(hasOverlayPermission)

    // 检查电池优化豁免
    val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
    val hasBatteryOptimizationExemption =
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    updateBatteryOptimizationExemption(hasBatteryOptimizationExemption)

    // 检查无障碍服务提供者和服务的状态
    val isProviderInstalled = UIHierarchyManager.isProviderAppInstalled(context)
    updateAccessibilityProviderInstalled(isProviderInstalled)

    // 只有在提供者安装后才尝试绑定并检查服务状态
    if (isProviderInstalled) {
        // 确保服务已绑定
        UIHierarchyManager.bindToService(context)
    }

    val hasAccessibilityServiceEnabled =
        UIHierarchyManager.isAccessibilityServiceEnabled(context)
    updateAccessibilityServiceEnabled(hasAccessibilityServiceEnabled)
}

/** Data class to hold all UI state */
data class DemoScreenState(
        // Permission states
        val isShizukuInstalled: MutableState<Boolean> = mutableStateOf(false),
        val isShizukuRunning: MutableState<Boolean> = mutableStateOf(false),
        val hasShizukuPermission: MutableState<Boolean> = mutableStateOf(false),
        val isOperitTerminalInstalled: MutableState<Boolean> = mutableStateOf(false),
        val hasStoragePermission: MutableState<Boolean> = mutableStateOf(false),
        val hasOverlayPermission: MutableState<Boolean> = mutableStateOf(false),
        val hasBatteryOptimizationExemption: MutableState<Boolean> = mutableStateOf(false),
        val hasAccessibilityServiceEnabled: MutableState<Boolean> = mutableStateOf(false),
        val isAccessibilityProviderInstalled: MutableState<Boolean> = mutableStateOf(false),
        val hasLocationPermission: MutableState<Boolean> = mutableStateOf(false),
        val isDeviceRooted: MutableState<Boolean> = mutableStateOf(false),
        val hasRootAccess: MutableState<Boolean> = mutableStateOf(false),

        // UI states
        val isRefreshing: MutableState<Boolean> = mutableStateOf(false),
        val showHelp: MutableState<Boolean> = mutableStateOf(false),
        val permissionErrorMessage: MutableState<String?> = mutableStateOf(null),
        val showSampleCommands: MutableState<Boolean> = mutableStateOf(false),
        val showAdbCommandExecutor: MutableState<Boolean> = mutableStateOf(false),
        val showShizukuWizard: MutableState<Boolean> = mutableStateOf(false),
        val showOperitTerminalWizard: MutableState<Boolean> = mutableStateOf(false),
        val showRootWizard: MutableState<Boolean> = mutableStateOf(false),
        val showAccessibilityWizard: MutableState<Boolean> = mutableStateOf(false),
        val showResultDialogState: MutableState<Boolean> = mutableStateOf(false),

        // Command execution
        val commandText: MutableState<String> = mutableStateOf(""),
        val resultText: MutableState<String> = mutableStateOf("结果将显示在这里"),
        val resultDialogTitle: MutableState<String> = mutableStateOf(""),
        val resultDialogContent: MutableState<String> = mutableStateOf(""),
        val isLoading: MutableState<Boolean> = mutableStateOf(false)
)

// Sample command lists that can be reused
val sampleAdbCommands =
        listOf(
                "getprop ro.build.version.release" to "获取Android版本",
                "pm list packages" to "列出已安装的应用包名",
                "dumpsys battery" to "查看电池状态",
                "settings list system" to "列出系统设置",
                "am start -a android.intent.action.VIEW -d https://www.example.com" to "打开网页",
                "dumpsys activity activities" to "查看活动的Activity",
                "service list" to "列出系统服务",
                "wm size" to "查看屏幕分辨率"
        )

// Predefined OperitTerminal commands (previously Termux)
val operitTerminalSampleCommands =
        listOf(
                "echo 'Hello OperitTerminal'" to "打印Hello OperitTerminal",
                "ls -la" to "列出文件和目录",
                "whoami" to "显示当前用户",
                "apt update" to "更新包管理器 (Ubuntu)",
                "apt install python3" to "安装Python (Ubuntu)",
                "ip addr" to "显示网络信息"
        )

// Root命令示例
val rootSampleCommands =
        listOf(
                "mount -o rw,remount /system" to "重新挂载系统分区为可写",
                "cat /proc/version" to "查看内核版本信息",
                "ls -la /data" to "查看/data目录内容",
                "getenforce" to "查看SELinux状态",
                "ps -A" to "列出所有进程",
                "cat /proc/meminfo" to "查看内存信息",
                "pm list features" to "列出系统功能",
                "dumpsys power" to "查看电源管理状态"
        )
