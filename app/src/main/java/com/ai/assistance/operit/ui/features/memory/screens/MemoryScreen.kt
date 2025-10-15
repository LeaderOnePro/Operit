package com.ai.assistance.operit.ui.features.memory.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.BatchDeleteConfirmDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.DocumentViewDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.EditMemorySheet
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.LinkMemoryDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.MemoryInfoDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.EdgeInfoDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.EditEdgeDialog
import com.ai.assistance.operit.ui.features.memory.screens.dialogs.ToolTestDialog
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModel
import com.ai.assistance.operit.ui.features.memory.viewmodel.MemoryViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.ImeAction

@Composable
fun MemorySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onTestToolClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                Icons.Default.Folder, 
                contentDescription = "Toggle Folders",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("搜索记忆...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboardController?.hide()
                onSearch()
            })
        )
        IconButton(onClick = onTestToolClick) {
            Icon(
                Icons.Default.Psychology, 
                contentDescription = "Test AI Memory",
                tint = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoryScreen() {
    val context = LocalContext.current
    val profileList by preferencesManager.profileListFlow.collectAsState(initial = emptyList())
    val activeProfileId by
    preferencesManager.activeProfileIdFlow.collectAsState(initial = "default")

    // 获取所有配置文件的名称映射(id -> name)
    val profileNameMap = remember { mutableStateMapOf<String, String>() }

    // 加载所有配置文件名称
    LaunchedEffect(profileList) {
        profileList.forEach { profileId ->
            val profile = preferencesManager.getUserPreferencesFlow(profileId).first()
            profileNameMap[profileId] = profile.name
        }
    }

    var selectedProfileId by remember { mutableStateOf(activeProfileId) }
    var showFolderNavigator by remember { mutableStateOf(false) }

    LaunchedEffect(activeProfileId) { selectedProfileId = activeProfileId }

    val viewModel: MemoryViewModel =
        viewModel(
            key = selectedProfileId, // Recreate ViewModel when profile changes
            factory = MemoryViewModelFactory(context, selectedProfileId)
        )
    val uiState by viewModel.uiState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { fileUri ->
                scope.launch {
                    var tempFile: File? = null
                    try {
                        // More robust file name extraction
                        var fileName = "Untitled"
                        context.contentResolver.query(fileUri, null, null, null, null)
                            ?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val displayNameIndex =
                                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    if (displayNameIndex != -1) {
                                        fileName = cursor.getString(displayNameIndex)
                                    }
                                }
                            }

                        val mimeType = context.contentResolver.getType(fileUri)
                        val content: String

                        if (mimeType != null && mimeType.startsWith("text")) {
                            val inputStream = context.contentResolver.openInputStream(fileUri)
                            val reader = BufferedReader(InputStreamReader(inputStream))
                            content = reader.readText()
                            viewModel.importDocument(fileName, fileUri.toString(), content)
                        } else {
                            // For binary files, use the tool
                            val inputStream = context.contentResolver.openInputStream(fileUri)
                            tempFile = File(context.cacheDir, fileName)
                            val outputStream = FileOutputStream(tempFile)
                            inputStream?.use { input ->
                                outputStream.use { output ->
                                    input.copyTo(
                                        output
                                    )
                                }
                            }

                            val toolHandler = AIToolHandler.getInstance(context)
                            val tool = AITool(
                                name = "read_file_full",
                                parameters = listOf(ToolParameter("path", tempFile.absolutePath))
                            )
                            val result = toolHandler.executeTool(tool)

                            if (result.success) {
                                // Assuming result.result can be cast to StringResultData
                                val resultData = result.result
                                content = if (resultData is StringResultData) {
                                    resultData.value
                                } else {
                                    resultData.toString()
                                }
                                viewModel.importDocument(fileName, fileUri.toString(), content)
                            } else {
                                Log.e("MemoryScreen", "Tool execution failed: ${result.error}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MemoryScreen", "Error processing file: $fileUri", e)
                    } finally {
                        tempFile?.delete()
                    }
                }
            }
        }
    )

    CustomScaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 只有在框选模式下才显示"确认删除"按钮
                if (uiState.isBoxSelectionMode) {
                    FloatingActionButton(
                        onClick = { viewModel.showBatchDeleteConfirm() },
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                    }
                }

                // 框选模式切换按钮
                FloatingActionButton(
                    onClick = {
                        android.util.Log.d(
                            "MemoryScreen",
                            "Box selection button clicked. Current mode: ${uiState.isBoxSelectionMode}, toggling to ${!uiState.isBoxSelectionMode}"
                        )
                        viewModel.toggleBoxSelectionMode(!uiState.isBoxSelectionMode)
                    },
                    containerColor = if (uiState.isBoxSelectionMode) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.SelectAll, contentDescription = "Toggle Box Selection Mode")
                }

                FloatingActionButton(
                    onClick = {
                        android.util.Log.d(
                            "MemoryScreen",
                            "Linking button clicked. Current mode: ${uiState.isLinkingMode}, toggling to ${!uiState.isLinkingMode}"
                        )
                        viewModel.toggleLinkingMode(!uiState.isLinkingMode)
                    },
                    containerColor = if (uiState.isLinkingMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = "Toggle Linking Mode")
                }
                FloatingActionButton(
                    onClick = {
                        filePickerLauncher.launch(
                            arrayOf(
                                "text/*",
                                "application/pdf",
                                "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            )
                        )
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = "Import Document")
                }
                FloatingActionButton(
                    onClick = { viewModel.startEditing(null) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Memory")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                MemorySearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    onSearch = {
                        keyboardController?.hide()
                        viewModel.searchMemories()
                    },
                    onTestToolClick = { viewModel.showToolTestDialog(true) },
                    onMenuClick = { showFolderNavigator = !showFolderNavigator }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
                    // 图谱区域（底层，占满整个空间）
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        GraphVisualizer(
                            graph = uiState.graph,
                            modifier = Modifier.fillMaxSize(),
                            selectedNodeId = uiState.selectedNodeId,
                            boxSelectedNodeIds = uiState.boxSelectedNodeIds, // 传递框选节点
                            isBoxSelectionMode = uiState.isBoxSelectionMode, // 传递模式状态
                            linkingNodeIds = uiState.linkingNodeIds,
                            selectedEdgeId = uiState.selectedEdge?.id,
                            onNodeClick = { node -> viewModel.selectNode(node) },
                            onEdgeClick = { edge -> viewModel.selectEdge(edge) },
                            onNodesSelected = { nodeIds -> viewModel.addNodesToSelection(nodeIds) } // 传递回调
                        )
                    }
                }
            }
            // 左侧文件夹导航 (Overlay)
            AnimatedVisibility(
                visible = showFolderNavigator,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it })
            ) {
                FolderNavigator(
                    folderPaths = uiState.folderPaths,
                    selectedFolderPath = uiState.selectedFolderPath,
                    onFolderSelected = { folderPath -> viewModel.selectFolder(folderPath) },
                    onFolderRename = { oldPath, newPath ->
                        viewModel.renameFolder(
                            oldPath,
                            newPath
                        )
                    },
                    onFolderDelete = { folderPath -> viewModel.deleteFolder(folderPath) },
                    onFolderCreate = { folderPath -> viewModel.createFolder(folderPath) },
                    onRefresh = { viewModel.refreshFolderList() },
                    profileList = profileList,
                    profileNameMap = profileNameMap,
                    selectedProfileId = selectedProfileId,
                    onProfileSelected = { selectedProfileId = it },
                    onDismissRequest = { showFolderNavigator = false }
                )
            }

            // 对话框层
            if (uiState.isToolTestDialogVisible) {
                ToolTestDialog(
                    onDismiss = { viewModel.showToolTestDialog(false) },
                    onExecute = { query -> viewModel.testQueryTool(query) },
                    result = uiState.toolTestResult,
                    isLoading = uiState.isToolTestLoading
                )
            }

            if (uiState.isDocumentViewOpen && uiState.selectedMemory != null) {
                var memoryTitle by remember { mutableStateOf(uiState.selectedMemory!!.title) }
                val chunkStates = remember {
                    mutableStateMapOf<Long, String>().apply {
                        uiState.selectedDocumentChunks.forEach { put(it.id, it.content) }
                    }
                }
                // 当chunks列表变化时，同步状态
                LaunchedEffect(uiState.selectedDocumentChunks) {
                    chunkStates.clear()
                    uiState.selectedDocumentChunks.forEach { chunk ->
                        chunkStates[chunk.id] = chunk.content
                    }
                }

                DocumentViewDialog(
                    memoryTitle = memoryTitle,
                    onTitleChange = { memoryTitle = it },
                    chunks = uiState.selectedDocumentChunks,
                    chunkStates = chunkStates,
                    onChunkChange = { id, content -> chunkStates[id] = content },
                    searchQuery = uiState.documentSearchQuery,
                    onSearchQueryChange = { viewModel.onDocumentSearchQueryChange(it) },
                    onPerformSearch = { viewModel.performSearchInDocument() },
                    onDismiss = { viewModel.closeDocumentView() },
                    onSave = {
                        // 保存标题
                        if (memoryTitle != uiState.selectedMemory!!.title) {
                            viewModel.updateMemory(
                                memory = uiState.selectedMemory!!,
                                newTitle = memoryTitle,
                                newContent = uiState.selectedMemory!!.content,
                                newContentType = uiState.selectedMemory!!.contentType,
                                newSource = uiState.selectedMemory!!.source,
                                newCredibility = uiState.selectedMemory!!.credibility,
                                newImportance = uiState.selectedMemory!!.importance,
                                newFolderPath = uiState.selectedMemory!!.folderPath ?: "",
                                newTags = uiState.selectedMemory!!.tags.map { it.name }
                            )
                        }
                        // 保存有变动的chunks
                        chunkStates.forEach { (id, content) ->
                            val originalContent =
                                uiState.selectedDocumentChunks.find { it.id == id }?.content
                            if (content != originalContent) {
                                viewModel.updateChunkContent(id, content)
                            }
                        }
                        viewModel.closeDocumentView()
                    },
                    onDelete = { viewModel.deleteMemory(uiState.selectedMemory!!.id) },
                    folderPath = uiState.selectedMemory?.folderPath ?: ""
                )
            } else if (uiState.selectedMemory != null) {
                MemoryInfoDialog(
                    memory = uiState.selectedMemory!!,
                    onDismiss = { viewModel.clearSelection() },
                    onEdit = {
                        viewModel.startEditing(uiState.selectedMemory)
                        viewModel.clearSelection() // 关闭当前对话框
                    },
                    onDelete = { viewModel.deleteMemory(uiState.selectedMemory!!.id) }
                )
            }

            val selectedEdge = uiState.selectedEdge
            if (selectedEdge != null) {
                EdgeInfoDialog(
                    edge = selectedEdge,
                    graph = uiState.graph,
                    onDismiss = { viewModel.clearSelection() },
                    onEdit = {
                        viewModel.startEditingEdge(selectedEdge)
                        viewModel.clearSelection() // 同样, 点击编辑后关闭
                    },
                    onDelete = { viewModel.deleteEdge(selectedEdge.id) }
                )
            }

            if (uiState.linkingNodeIds.size == 2) {
                val sourceNode = uiState.graph.nodes.find { it.id == uiState.linkingNodeIds[0] }
                val targetNode = uiState.graph.nodes.find { it.id == uiState.linkingNodeIds[1] }
                if (sourceNode != null && targetNode != null) {
                    LinkMemoryDialog(
                        sourceNodeLabel = sourceNode.label,
                        targetNodeLabel = targetNode.label,
                        onDismiss = { viewModel.toggleLinkingMode(false) },
                        onLink = { type, weight, description ->
                            viewModel.linkMemories(
                                sourceNode.id,
                                targetNode.id,
                                type,
                                weight,
                                description
                            )
                        }
                    )
                }
            }

            if (uiState.isEditing) {
                EditMemorySheet(
                    memory = uiState.editingMemory,
                    allFolderPaths = uiState.folderPaths,
                    onDismiss = { viewModel.cancelEditing() },
                    onSave = { memory, title, content, contentType, source, credibility, importance, folderPath, tags ->
                        if (memory == null) {
                            // 创建新记忆的逻辑（如果需要的话）
                             viewModel.createMemory(title, content, contentType)
                        } else {
                            viewModel.updateMemory(
                                memory = memory,
                                newTitle = title,
                                newContent = content,
                                newContentType = contentType,
                                newSource = source,
                                newCredibility = credibility,
                                newImportance = importance,
                                newFolderPath = folderPath,
                                newTags = tags
                            )
                        }
                    }
                )
            }

            val editingEdge = uiState.editingEdge
            if (uiState.isEditingEdge && editingEdge != null) {
                EditEdgeDialog(
                    edge = editingEdge,
                    onDismiss = { viewModel.cancelEditingEdge() },
                    onSave = { type, weight, description ->
                        viewModel.updateEdge(editingEdge, type, weight, description)
                    }
                )
            }

            if (uiState.showBatchDeleteConfirm) {
                BatchDeleteConfirmDialog(
                    selectedCount = uiState.boxSelectedNodeIds.size,
                    onDismiss = { viewModel.dismissBatchDeleteConfirm() },
                    onConfirm = { viewModel.deleteSelectedNodes() }
                )
            }
        }
    }}