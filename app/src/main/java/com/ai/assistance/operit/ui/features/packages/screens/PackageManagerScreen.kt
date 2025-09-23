package com.ai.assistance.operit.ui.features.packages.screens

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.PackageTool
import com.ai.assistance.operit.core.tools.ToolPackage
import com.ai.assistance.operit.core.tools.automatic.UIFunction
import com.ai.assistance.operit.core.tools.automatic.UIRouter
import com.ai.assistance.operit.core.tools.automatic.config.AutomationPackageInfo
import com.ai.assistance.operit.core.tools.automatic.config.AutomationPackageManager
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.mcp.MCPRepository
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.ui.features.packages.components.EmptyState
import com.ai.assistance.operit.ui.features.packages.components.PackageTab
import com.ai.assistance.operit.ui.features.packages.dialogs.AutomationFunctionExecutionDialog
import com.ai.assistance.operit.ui.features.packages.dialogs.AutomationPackageDetailsDialog
import com.ai.assistance.operit.ui.features.packages.dialogs.PackageDetailsDialog
import com.ai.assistance.operit.ui.features.packages.dialogs.ScriptExecutionDialog
import com.ai.assistance.operit.ui.features.packages.lists.PackagesList
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.scale
import com.ai.assistance.operit.R


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PackageManagerScreen(
    onNavigateToMCPMarket: () -> Unit = {},
    onNavigateToMCPDetail: ((com.ai.assistance.operit.data.api.GitHubIssue) -> Unit)? = null
) {
    val context = LocalContext.current
    val packageManager = remember {
        PackageManager.getInstance(context, AIToolHandler.getInstance(context))
    }
    val automationManager = remember { AutomationPackageManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val mcpRepository = remember { MCPRepository(context) }

    // State for available and imported packages
    val availablePackages = remember { mutableStateOf<Map<String, ToolPackage>>(emptyMap()) }
    val importedPackages = remember { mutableStateOf<List<String>>(emptyList()) }
    // UI展示用的导入状态列表，与后端状态分离
    val visibleImportedPackages = remember { mutableStateOf<List<String>>(emptyList()) }

    // State for automation configs
    val automationConfigs = remember { mutableStateOf<List<AutomationPackageInfo>>(emptyList()) }

    // State for selected package and showing details
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var showDetails by remember { mutableStateOf(false) }

    // State for script execution
    var showScriptExecution by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf<PackageTool?>(null) }
    var scriptExecutionResult by remember { mutableStateOf<ToolResult?>(null) }

    // State for automation dialogs
    var selectedAutomationPackage by remember { mutableStateOf<AutomationPackageInfo?>(null) }
    var showAutomationDetails by remember { mutableStateOf(false) }
    var selectedAutomationFunction by remember { mutableStateOf<UIFunction?>(null) }
    var showAutomationExecution by remember { mutableStateOf(false) }

    // State for snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // Tab selection state
    var selectedTab by rememberSaveable { mutableStateOf(PackageTab.PACKAGES) }

    // File picker launcher for importing external packages
    val packageFilePicker =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri
                ->
                uri?.let {
                    scope.launch {
                        try {
                            var fileName: String? = null
                            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                val nameIndex = cursor.getColumnIndex("_display_name")
                                if (cursor.moveToFirst() && nameIndex >= 0) {
                                    fileName = cursor.getString(nameIndex)
                                }
                            }

                            if (fileName == null) {
                                snackbarHostState.showSnackbar(context.getString(R.string.no_filename))
                                return@launch
                            }

                            // 根据当前选中的标签页处理不同类型的文件
                            when (selectedTab) {
                                PackageTab.PACKAGES -> {
                            if (!fileName!!.endsWith(".js")) {
                                        snackbarHostState.showSnackbar(message = context.getString(R.string.package_js_only))
                                return@launch
                            }

                            // Copy the file to a temporary location
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val tempFile = File(context.cacheDir, fileName)

                            inputStream?.use { input ->
                                tempFile.outputStream().use { output -> input.copyTo(output) }
                            }

                            // Import the package from the temporary file
                            packageManager.importPackageFromExternalStorage(
                                    tempFile.absolutePath
                            )

                            // Refresh the lists
                            availablePackages.value = packageManager.getAvailablePackages()
                            importedPackages.value = packageManager.getImportedPackages()

                            snackbarHostState.showSnackbar(message = context.getString(R.string.external_package_imported))

                            // Clean up the temporary file
                            tempFile.delete()
                                }
                                /*
                                PackageTab.AUTOMATION_CONFIGS -> {
                                    if (!fileName!!.endsWith(".json")) {
                                        snackbarHostState.showSnackbar(message = context.getString(R.string.automation_json_only))
                                        return@launch
                                    }

                                    // Copy the file to a temporary location
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    val tempFile = File(context.cacheDir, fileName)

                                    inputStream?.use { input ->
                                        tempFile.outputStream().use { output -> input.copyTo(output) }
                                    }

                                    // Import the automation config
                                    val result = automationManager.importPackage(tempFile.absolutePath)
                                    
                                    if (result.startsWith("Successfully")) {
                                        // Refresh the automation configs list
                                        automationConfigs.value = automationManager.getAllPackageInfo()
                                        snackbarHostState.showSnackbar(message = "自动化配置导入成功")
                                    } else {
                                        snackbarHostState.showSnackbar(message = result)
                                    }

                                    // Clean up the temporary file
                                    tempFile.delete()
                                }
                                */
                                else -> {
                                    snackbarHostState.showSnackbar(context.getString(R.string.current_tab_not_support_import))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("PackageManagerScreen", "Failed to import file", e)
                            snackbarHostState.showSnackbar(message = context.getString(R.string.import_failed, e.message))
                        }
                    }
                }
            }

    // Initialize UIRouter for automation execution
    val uiRouter = remember {
        val toolHandler = AIToolHandler.getInstance(context)
        UIRouter(context, toolHandler)
            }

    // Load packages
    LaunchedEffect(Unit) {
        try {
            availablePackages.value = packageManager.getAvailablePackages()
            importedPackages.value = packageManager.getImportedPackages()
            // 初始化UI显示状态
            visibleImportedPackages.value = importedPackages.value.toList()

            automationConfigs.value = automationManager.getAllPackageInfo()
        } catch (e: Exception) {
            Log.e("PackageManagerScreen", "Failed to load packages or configs", e)
        }
    }

    CustomScaffold(
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                            modifier = Modifier.padding(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            snackbarData = data
                    )
                }
            },
            floatingActionButton = {
                if (selectedTab == PackageTab.PACKAGES) { // || selectedTab == PackageTab.AUTOMATION_CONFIGS) {
                    FloatingActionButton(
                            onClick = { packageFilePicker.launch("*/*") },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier =
                                    Modifier.shadow(
                                            elevation = 6.dp,
                                            shape = FloatingActionButtonDefaults.shape
                                    )
                    ) { 
                        Icon(
                            imageVector = Icons.Rounded.Add, 
                            contentDescription = when (selectedTab) {
                                PackageTab.PACKAGES -> context.getString(R.string.import_external_package)
                                // PackageTab.AUTOMATION_CONFIGS -> "导入自动化配置"
                                else -> context.getString(R.string.import_action)
                            }
                        ) 
                    }
                }
            }
    ) { paddingValues ->
        Column(
                modifier =
                        Modifier.fillMaxSize()
                                .padding(paddingValues)
                ) {
            // 优化标签栏布局 - 直接使用TabRow，不再使用Card包裹，移除边距完全贴满
            TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    modifier = Modifier.fillMaxWidth(),
                    divider = {
                        Divider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                        )
                    },
                    indicator = { tabPositions ->
                        if (selectedTab.ordinal < tabPositions.size) {
                            TabRowDefaults.PrimaryIndicator(
                                    modifier =
                                            Modifier.tabIndicatorOffset(
                                                    tabPositions[selectedTab.ordinal]
                                            ),
                                    height = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
            ) {
                // 包管理标签
                Tab(
                        selected = selectedTab == PackageTab.PACKAGES,
                        onClick = { selectedTab = PackageTab.PACKAGES },
                        modifier = Modifier.height(48.dp)
                ) {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                        imageVector = Icons.Default.Extension,
                                        contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == PackageTab.PACKAGES) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        Spacer(Modifier.width(6.dp))
                                Text(
                                        context.getString(R.string.packages),
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = false,
                            color = if (selectedTab == PackageTab.PACKAGES) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                )
                    }
                }

                // 自动化配置标签 - 临时隐藏
                /*
                Tab(
                    selected = selectedTab == PackageTab.AUTOMATION_CONFIGS,
                    onClick = { selectedTab = PackageTab.AUTOMATION_CONFIGS },
                    modifier = Modifier.height(48.dp)
                ) {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                            imageVector = Icons.Default.Build,
                                        contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == PackageTab.AUTOMATION_CONFIGS) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        Spacer(Modifier.width(6.dp))
                                Text(
                            "自动化配置",
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = false,
                            color = if (selectedTab == PackageTab.AUTOMATION_CONFIGS) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                )
                    }
                }
                */

                // MCP标签
                Tab(
                        selected = selectedTab == PackageTab.MCP,
                        onClick = { selectedTab = PackageTab.MCP },
                        modifier = Modifier.height(48.dp)
                ) {
                            Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                            imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == PackageTab.MCP) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        Spacer(Modifier.width(6.dp))
                                Text(
                            context.getString(R.string.mcp),
                            style = MaterialTheme.typography.bodySmall,
                            softWrap = false,
                            color = if (selectedTab == PackageTab.MCP) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
                }
            }

            // 内容区域添加水平padding
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)) {
                when (selectedTab) {
                    PackageTab.PACKAGES -> {
                        // 显示包列表
                        if (availablePackages.value.isEmpty()) {
                            EmptyState(message = context.getString(R.string.no_packages_available))
                        } else {
                            Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background,
                                    shape = MaterialTheme.shapes.medium
                            ) {
                                val packages = availablePackages.value

                                val automaticPackages = packages.filterKeys { it.lowercase().startsWith("automatic") }
                                val experimentalPackages = packages.filterKeys { it.lowercase().startsWith("experimental") }
                                val otherPackages = packages.filterKeys {
                                    !it.lowercase().startsWith("automatic") && !it.lowercase().startsWith("experimental")
                                }

                                val groupedPackages = linkedMapOf<String, Map<String, ToolPackage>>()
                                if (automaticPackages.isNotEmpty()) {
                                    groupedPackages["Automatic"] = automaticPackages
                                }
                                if (experimentalPackages.isNotEmpty()) {
                                    groupedPackages["Experimental"] = experimentalPackages
                                }
                                if (otherPackages.isNotEmpty()) {
                                    groupedPackages["Other"] = otherPackages
                                }

                                // 在Composable上下文中预先获取颜色
                                val automaticColor = MaterialTheme.colorScheme.primary
                                val experimentalColor = MaterialTheme.colorScheme.tertiary
                                val otherColor = MaterialTheme.colorScheme.secondary

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    groupedPackages.forEach { (category, packagesInCategory) ->
                                        val categoryColor = when (category) {
                                            "Automatic" -> automaticColor
                                            "Experimental" -> experimentalColor
                                            else -> otherColor
                                        }

                                        items(packagesInCategory.keys.toList(), key = { it }) { packageName ->
                                            val isFirstInCategory = packageName == packagesInCategory.keys.first()
                                            
                                            PackageListItemWithTag(
                                                packageName = packageName,
                                                toolPackage = packagesInCategory[packageName],
                                                isImported = visibleImportedPackages.value.contains(packageName),
                                                categoryTag = if (isFirstInCategory) category else null,
                                                category = category, // 传递完整的分类信息
                                                categoryColor = categoryColor,
                                                onPackageClick = {
                                                    selectedPackage = packageName
                                                    showDetails = true
                                                },
                                                onToggleImport = { isChecked ->
                                                    // 立即更新UI显示的导入状态列表，使开关立即响应
                                                    val currentImported = visibleImportedPackages.value.toMutableList()
                                                    if (isChecked) {
                                                        if (!currentImported.contains(packageName)) {
                                                            currentImported.add(packageName)
                                                        }
                                                    } else {
                                                        currentImported.remove(packageName)
                                                    }
                                                    visibleImportedPackages.value = currentImported

                                                    // 后台执行实际的导入/移除操作
                                                    scope.launch {
                                                        try {
                                                            if (isChecked) {
                                                                packageManager.importPackage(packageName)
                                                            } else {
                                                                packageManager.removePackage(packageName)
                                                            }
                                                            // 操作成功后，更新真实的导入状态
                                                            importedPackages.value = packageManager.getImportedPackages()
                                                        } catch (e: Exception) {
                                                            Log.e(
                                                                "PackageManagerScreen",
                                                                if (isChecked) "Failed to import package" else "Failed to remove package",
                                                                e
                                                            )
                                                            // 操作失败时恢复UI显示状态为实际状态
                                                            visibleImportedPackages.value = importedPackages.value
                                                            // 只在失败时显示提示
                                                            snackbarHostState.showSnackbar(
                                                                message = if (isChecked) context.getString(R.string.package_import_failed) else context.getString(R.string.package_remove_failed)
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    /*
                    PackageTab.AUTOMATION_CONFIGS -> {
                        if (automationConfigs.value.isEmpty()) {
                            EmptyState(message = "没有可用的自动化配置")
                        } else {
                            AutomationConfigList(
                                configs = automationConfigs.value,
                                onConfigClick = { config ->
                                    selectedAutomationPackage = config
                                    showAutomationDetails = true
                                }
                            )
                        }
                    }
                    */
                    PackageTab.MCP -> {
                        MCPConfigScreen(
                            onNavigateToMCPMarket = onNavigateToMCPMarket
                        )
                    }
                }
            }

            // Package Details Dialog
            if (showDetails && selectedPackage != null) {
                PackageDetailsDialog(
                        packageName = selectedPackage!!,
                        packageDescription = availablePackages.value[selectedPackage]?.description
                                        ?: "",
                        packageManager = packageManager,
                        onRunScript = { tool ->
                            selectedTool = tool
                            showScriptExecution = true
                        },
                        onDismiss = { showDetails = false },
                        onPackageDeleted = {
                            showDetails = false
                            scope.launch {
                                Log.d("PackageManagerScreen", "onPackageDeleted callback triggered. Refreshing package lists.")
                                // Refresh the package lists after deletion
                                availablePackages.value = packageManager.getAvailablePackages()
                                importedPackages.value = packageManager.getImportedPackages()
                                visibleImportedPackages.value = importedPackages.value.toList()
                                Log.d("PackageManagerScreen", "Lists refreshed. Available: ${availablePackages.value.keys}, Imported: ${importedPackages.value}")
                                snackbarHostState.showSnackbar("Package deleted successfully.")
                            }
                        }
                )
            }

            // Script Execution Dialog
            if (showScriptExecution && selectedTool != null && selectedPackage != null) {
                ScriptExecutionDialog(
                        packageName = selectedPackage!!,
                        tool = selectedTool!!,
                        packageManager = packageManager,
                        initialResult = scriptExecutionResult,
                        onExecuted = { result -> scriptExecutionResult = result },
                        onDismiss = {
                            showScriptExecution = false
                            scriptExecutionResult = null
                        }
                )
            }

            // Automation Package Details Dialog
            if (showAutomationDetails && selectedAutomationPackage != null) {
                AutomationPackageDetailsDialog(
                    packageInfo = selectedAutomationPackage!!,
                    packageManager = automationManager,
                    onExecuteFunction = { function ->
                        selectedAutomationFunction = function
                        showAutomationDetails = false
                        showAutomationExecution = true
                    },
                    onDismiss = { showAutomationDetails = false },
                    onPackageDeleted = {
                        scope.launch {
                            // Refresh automation configs list after deletion
                            automationConfigs.value = automationManager.getAllPackageInfo()
                            snackbarHostState.showSnackbar("自动化配置删除成功")
                        }
                    }
                )
            }

            // Automation Function Execution Dialog
            if (showAutomationExecution && selectedAutomationFunction != null) {
                AutomationFunctionExecutionDialog(
                    function = selectedAutomationFunction!!,
                    uiRouter = uiRouter,
                    packageManager = automationManager,
                    onDismiss = {
                        showAutomationExecution = false
                        selectedAutomationFunction = null
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationConfigList(
    configs: List<AutomationPackageInfo>,
    onConfigClick: (AutomationPackageInfo) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(configs) { config ->
            Card(
                onClick = { onConfigClick(config) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = config.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = config.description.takeIf { it.isNotBlank() } ?: stringResource(R.string.no_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (config.isBuiltIn) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (config.isBuiltIn) stringResource(R.string.builtin) else stringResource(R.string.external),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (config.isBuiltIn)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = config.packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                                                Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = stringResource(R.string.view_details),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                }
            }
        }
    }
}

@Composable
private fun PackageListItemWithTag(
    packageName: String,
    toolPackage: ToolPackage?,
    isImported: Boolean,
    categoryTag: String?,
    category: String, // 新增分类参数
    categoryColor: Color,
    onPackageClick: () -> Unit,
    onToggleImport: (Boolean) -> Unit
) {
    Surface(
        onClick = onPackageClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // 分类标签（仅在有标签时显示）
            if (categoryTag != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier
                            .width(3.dp)
                            .height(12.dp),
                        color = categoryColor,
                        shape = RoundedCornerShape(1.5.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = categoryTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // 主要内容行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp, 
                        vertical = if (categoryTag != null) 4.dp else 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (category) {
                        "Automatic" -> Icons.Default.AutoMode
                        "Experimental" -> Icons.Default.Science
                        "Other" -> Icons.Default.Widgets
                        else -> Icons.Default.Extension
                    },
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = categoryColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = toolPackage?.name ?: packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (toolPackage?.description?.isNotBlank() == true) {
                        Text(
                            text = toolPackage.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isImported,
                    onCheckedChange = onToggleImport,
                    modifier = Modifier.scale(0.8f)
                )
            }
        }
    }
}
