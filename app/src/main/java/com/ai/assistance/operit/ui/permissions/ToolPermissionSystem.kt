package com.ai.assistance.operit.ui.permissions

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.material3.ColorScheme
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.data.model.AITool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

// Define DataStore
private val Context.toolPermissionsDataStore: DataStore<Preferences> by preferencesDataStore(name = "tool_permissions")

/**
 * Permission levels for tool operations
 */
enum class PermissionLevel {
    ALLOW,      // Allow automatically without asking
    CAUTION,    // Ask for dangerous operations, allow others
    ASK,        // Always ask
    FORBID;     // Never allow

    companion object {
        fun fromString(value: String?): PermissionLevel {
            return when (value) {
                "ALLOW" -> ALLOW
                "CAUTION" -> CAUTION
                "ASK" -> ASK
                "FORBID" -> FORBID
                else -> ASK  // Default to ASK
            }
        }
    }
}

/**
 * Tool categories with different security implications
 */
enum class ToolCategory {
    SYSTEM_OPERATION,    // System operations (settings modifications)
    NETWORK,             // Network operations (HTTP requests)
    UI_AUTOMATION,       // UI automation (clicks, touches)
    FILE_READ,           // File reading operations
    FILE_WRITE;          // File writing/deletion operations

    companion object {
        fun getDefaultPermissionLevel(category: ToolCategory): PermissionLevel {
            return when (category) {
                SYSTEM_OPERATION -> PermissionLevel.ASK
                NETWORK -> PermissionLevel.ALLOW
                UI_AUTOMATION -> PermissionLevel.CAUTION
                FILE_READ -> PermissionLevel.ALLOW
                FILE_WRITE -> PermissionLevel.ASK
            }
        }
    }
}

/**
 * Centralized tool permission system that manages both permission storage and checking
 */
class ToolPermissionSystem private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ToolPermissionSystem"
        private const val PERMISSION_REQUEST_TIMEOUT_MS = 60000L // 60 seconds timeout
        
        // DataStore keys
        private val MASTER_SWITCH = stringPreferencesKey("master_switch")
        private val SYSTEM_OPERATION_PERMISSION = stringPreferencesKey("system_operation_permission")
        private val NETWORK_PERMISSION = stringPreferencesKey("network_permission")
        private val UI_AUTOMATION_PERMISSION = stringPreferencesKey("ui_automation_permission")
        private val FILE_READ_PERMISSION = stringPreferencesKey("file_read_permission")
        private val FILE_WRITE_PERMISSION = stringPreferencesKey("file_write_permission")
        
        // Default permission setting
        private val DEFAULT_MASTER_SWITCH = PermissionLevel.ASK.name
        
        @Volatile
        private var INSTANCE: ToolPermissionSystem? = null
        
        fun getInstance(context: Context): ToolPermissionSystem {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ToolPermissionSystem(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Permission request management
    private val mainHandler = Handler(Looper.getMainLooper())
    private val permissionRequestOverlay = PermissionRequestOverlay(context)
    private var currentPermissionCallback: ((PermissionRequestResult) -> Unit)? = null
    private var permissionRequestInfo: Pair<AITool, String>? = null
    
    // 存储当前颜色方案
    private var currentColorScheme: ColorScheme? = null
    
    /**
     * 设置当前使用的颜色方案
     */
    fun setColorScheme(colorScheme: ColorScheme?) {
        this.currentColorScheme = colorScheme
        permissionRequestOverlay.setColorScheme(colorScheme)
    }
    
    // Permission request state flow
    private val _permissionRequestState = MutableStateFlow<Pair<AITool, String>?>(null)
    val permissionRequestState = _permissionRequestState.asStateFlow()
    
    // Permission level flows
    val masterSwitchFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(preferences[MASTER_SWITCH] ?: DEFAULT_MASTER_SWITCH)
    }
    
    val systemOperationPermissionFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(
            preferences[SYSTEM_OPERATION_PERMISSION]
                ?: ToolCategory.getDefaultPermissionLevel(ToolCategory.SYSTEM_OPERATION).name
        )
    }
    
    val networkPermissionFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(
            preferences[NETWORK_PERMISSION]
                ?: ToolCategory.getDefaultPermissionLevel(ToolCategory.NETWORK).name
        )
    }
    
    val uiAutomationPermissionFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(
            preferences[UI_AUTOMATION_PERMISSION]
                ?: ToolCategory.getDefaultPermissionLevel(ToolCategory.UI_AUTOMATION).name
        )
    }
    
    val fileReadPermissionFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(
            preferences[FILE_READ_PERMISSION]
                ?: ToolCategory.getDefaultPermissionLevel(ToolCategory.FILE_READ).name
        )
    }
    
    val fileWritePermissionFlow: Flow<PermissionLevel> = context.toolPermissionsDataStore.data.map { preferences ->
        PermissionLevel.fromString(
            preferences[FILE_WRITE_PERMISSION]
                ?: ToolCategory.getDefaultPermissionLevel(ToolCategory.FILE_WRITE).name
        )
    }
    
    // Registry of dangerous operations by tool name
    private val dangerousOperationsRegistry = mutableMapOf<String, (AITool) -> Boolean>()
    
    // Registry of operation descriptions by tool name
    private val operationDescriptionRegistry = mutableMapOf<String, (AITool) -> String>()
    
    /**
     * Register a tool as potentially dangerous with custom danger check logic
     */
    fun registerDangerousOperation(toolName: String, dangerCheck: (AITool) -> Boolean) {
        dangerousOperationsRegistry[toolName] = dangerCheck
    }
    
    /**
     * Register a description generator for a tool
     */
    fun registerOperationDescription(toolName: String, descriptionGenerator: (AITool) -> String) {
        operationDescriptionRegistry[toolName] = descriptionGenerator
    }
    
    /**
     * Initialize default dangerous operations and descriptions
     */
    fun initializeDefaultRules() {
        // 不需要在这里预先注册工具的危险操作检查和描述生成器
        // 所有工具相关的信息都应该在AIToolHandler中通过统一的registerTool方法完成
        // 这个方法保留为空，以便在必要时进行一些全局初始化操作
        Log.d(TAG, "工具权限系统已初始化 - 所有工具定义现在都在AIToolHandler中")
    }
    
    /**
     * Save permission level settings
     */
    suspend fun saveMasterSwitch(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[MASTER_SWITCH] = level.name
        }
    }
    
    suspend fun saveSystemOperationPermission(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[SYSTEM_OPERATION_PERMISSION] = level.name
        }
    }
    
    suspend fun saveNetworkPermission(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[NETWORK_PERMISSION] = level.name
        }
    }
    
    suspend fun saveUIAutomationPermission(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[UI_AUTOMATION_PERMISSION] = level.name
        }
    }
    
    suspend fun saveFileReadPermission(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[FILE_READ_PERMISSION] = level.name
        }
    }
    
    suspend fun saveFileWritePermission(level: PermissionLevel) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[FILE_WRITE_PERMISSION] = level.name
        }
    }
    
    suspend fun saveAllPermissions(
        masterSwitch: PermissionLevel,
        systemOperation: PermissionLevel,
        network: PermissionLevel,
        uiAutomation: PermissionLevel,
        fileRead: PermissionLevel,
        fileWrite: PermissionLevel
    ) {
        context.toolPermissionsDataStore.edit { preferences ->
            preferences[MASTER_SWITCH] = masterSwitch.name
            preferences[SYSTEM_OPERATION_PERMISSION] = systemOperation.name
            preferences[NETWORK_PERMISSION] = network.name
            preferences[UI_AUTOMATION_PERMISSION] = uiAutomation.name
            preferences[FILE_READ_PERMISSION] = fileRead.name
            preferences[FILE_WRITE_PERMISSION] = fileWrite.name
        }
    }
    
    /**
     * Check if a tool operation is dangerous
     */
    fun isDangerousOperation(tool: AITool): Boolean {
        return dangerousOperationsRegistry[tool.name]?.invoke(tool) ?: false
    }
    
    /**
     * Get human-readable description of an operation
     */
    fun getOperationDescription(tool: AITool): String {
        return operationDescriptionRegistry[tool.name]?.invoke(tool) ?: "${tool.name} 操作"
    }
    
    /**
     * Check if a tool is allowed to execute
     */
    suspend fun checkToolPermission(tool: AITool): Boolean {
        Log.d(TAG, "Starting permission check: ${tool.name}")
        
        // Check global permission switch
        val masterSwitch = masterSwitchFlow.first()
        
        // If globally forbidden, all tools are denied
        if (masterSwitch == PermissionLevel.FORBID) {
            return false
        }
        
        // If global ask, prompt for all tools
        if (masterSwitch == PermissionLevel.ASK) {
            return requestPermission(tool)
        }
        
        // Get tool category
        val toolCategory = tool.category ?: ToolCategory.UI_AUTOMATION
        
        // Get permission level for the category
        val permissionLevel = when (toolCategory) {
            ToolCategory.SYSTEM_OPERATION -> systemOperationPermissionFlow.first()
            ToolCategory.NETWORK -> networkPermissionFlow.first()
            ToolCategory.UI_AUTOMATION -> uiAutomationPermissionFlow.first()
            ToolCategory.FILE_READ -> fileReadPermissionFlow.first()
            ToolCategory.FILE_WRITE -> fileWritePermissionFlow.first()
            // This case is actually unreachable since we cover all enum values above,
            // but the Kotlin compiler needs this for type safety
            else -> PermissionLevel.ASK
        }
        
        return when (permissionLevel) {
            PermissionLevel.ALLOW -> true
            PermissionLevel.CAUTION -> {
                val isDangerous = isDangerousOperation(tool)
                if (isDangerous) requestPermission(tool) else true
            }
            PermissionLevel.ASK -> requestPermission(tool)
            PermissionLevel.FORBID -> false
        }
    }
    
    /**
     * Request permission from the user to execute a tool
     */
    private suspend fun requestPermission(tool: AITool): Boolean {
        // Get operation description
        val operationDescription = getOperationDescription(tool)
        
        Log.d(TAG, "Requesting permission: ${tool.name}")
        
        // Clear existing request
        currentPermissionCallback = null
        permissionRequestInfo = null
        _permissionRequestState.value = null
        
        // Set up new request
        val requestInfo = Pair(tool, operationDescription)
        permissionRequestInfo = requestInfo
        _permissionRequestState.value = requestInfo
        
        Log.d(TAG, "Permission request state updated: ${tool.name}")
        
        return withTimeoutOrNull(PERMISSION_REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                // Set callback
                currentPermissionCallback = { result ->
                    Log.d(TAG, "Permission result received: $result for ${tool.name}")
                    // Clean up state
                    currentPermissionCallback = null
                    permissionRequestInfo = null
                    _permissionRequestState.value = null
                    
                    // Handle result
                    when (result) {
                        PermissionRequestResult.ALLOW -> continuation.resume(true)
                        PermissionRequestResult.DENY -> continuation.resume(false)
                        PermissionRequestResult.DISCONNECT -> {
                            if (continuation.isActive) continuation.cancel()
                            continuation.resume(false)
                        }
                    }
                }
                
                // Start permission request on main thread
                mainHandler.post {
                    // Use overlay to show permission request
                    if (!permissionRequestOverlay.hasOverlayPermission()) {
                        Log.w(TAG, "No overlay permission, requesting...")
                        permissionRequestOverlay.requestOverlayPermission()
                        currentPermissionCallback?.invoke(PermissionRequestResult.DENY)
                    } else {
                        permissionRequestOverlay.show(tool, operationDescription) { result ->
                            handlePermissionResult(result)
                        }
                    }
                }
            }
        } ?: run {
            // Timeout handling
            Log.d(TAG, "Permission request timed out: ${tool.name}")
            currentPermissionCallback = null
            permissionRequestInfo = null
            _permissionRequestState.value = null
            false
        }
    }
    
    /**
     * Handle permission request result
     */
    fun handlePermissionResult(result: PermissionRequestResult) {
        currentPermissionCallback?.invoke(result)
    }
    
    /**
     * Get current permission request info
     */
    fun getCurrentPermissionRequest(): Pair<AITool, String>? {
        return permissionRequestInfo
    }
    
    /**
     * Check if there is an active permission request
     */
    fun hasActivePermissionRequest(): Boolean {
        return permissionRequestInfo != null && currentPermissionCallback != null
    }
    
    /**
     * Refresh permission request state
     */
    fun refreshPermissionRequestState(): Boolean {
        return hasActivePermissionRequest()
    }
} 