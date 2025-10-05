package com.ai.assistance.operit.ui.features.memory.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.MemoryLink
import com.ai.assistance.operit.data.model.DocumentChunk
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Memory UI State Represents the current state of the Memory screen. */
data class MemoryUiState(
        val memories: List<Memory> = emptyList(), // Keep for potential list view
        val graph: Graph = Graph(emptyList(), emptyList()),
        val selectedMemory: Memory? = null,
        val selectedNodeId: String? = null,
        val isLoading: Boolean = false,
        val searchQuery: String = "",
        val error: String? = null,
        val editingMemory: Memory? = null, // 新增：用于编辑/新建
        val isEditing: Boolean = false, // 新增：是否处于编辑/新建状态
        val isLinkingMode: Boolean = false, // 是否处于连接模式
        val linkingNodeIds: List<String> = emptyList(), // 已选择的连接节点
        val selectedEdge: Edge? = null,
        val editingEdge: Edge? = null,
        val isEditingEdge: Boolean = false,
        val isBoxSelectionMode: Boolean = false, // 新增：是否处于框选模式
        val boxSelectedNodeIds: Set<String> = emptySet(), // 新增：框选中的节点ID
        val showBatchDeleteConfirm: Boolean = false, // 新增：是否显示批量删除确认对话框

        // --- 新增：文档相关状态 ---
        val selectedDocumentChunks: List<DocumentChunk> = emptyList(),
        val documentSearchQuery: String = "",
        val isDocumentViewOpen: Boolean = false,

        // --- 新增：工具测试相关状态 ---
        val isToolTestDialogVisible: Boolean = false,
        val toolTestResult: String = "",
        val isToolTestLoading: Boolean = false
)

/**
 * ViewModel for the Memory/Knowledge Base screen. It handles the business logic for interacting
 * with the MemoryRepository.
 */
class MemoryViewModel(private val repository: MemoryRepository, private val context: Context) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        // Initially load the graph
        loadMemoryGraph()
    }

    private suspend fun refreshGraph(): Graph {
        return repository.getMemoryGraph()
    }

    /** Loads the entire memory graph from the repository. */
    fun loadMemoryGraph() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val graphData = refreshGraph()
                _uiState.update { it.copy(graph = graphData, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load memory graph: ${e.message}")
                }
            }
        }
    }

    /**
     * Searches memories and updates the graph with the results. If the query is empty, it reloads
     * the full graph.
     */
    fun searchMemories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val query = _uiState.value.searchQuery
                val memories =
                    if (query.isBlank()) {
                        repository.searchMemories("") // Get all
                    } else {
                        repository.searchMemories(query)
                    }
                val graphData = repository.getGraphForMemories(memories)
                _uiState.update { it.copy(graph = graphData, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to search memories: ${e.message}")
                }
            }
        }
    }

    /** Updates the search query in the state. */
    fun onSearchQueryChange(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
    }

    /** Selects a memory to view its details. */
    fun selectMemory(memory: Memory) {
        _uiState.update { it.copy(selectedMemory = memory) }
    }

    /** Selects a node in the graph. Fetches the full memory details for the selected node. */
    fun selectNode(node: Node) {
        viewModelScope.launch {
            if (_uiState.value.isLinkingMode) {
                // 连接模式
                val currentLinkingIds = _uiState.value.linkingNodeIds.toMutableList()
                if (node.id !in currentLinkingIds) {
                    currentLinkingIds.add(node.id)
                }
                _uiState.update { it.copy(linkingNodeIds = currentLinkingIds) }
            } else if (_uiState.value.isBoxSelectionMode) {
                // 框选模式
                val currentSelectedIds = _uiState.value.boxSelectedNodeIds.toMutableSet()
                if (node.id in currentSelectedIds) {
                    currentSelectedIds.remove(node.id)
                } else {
                    currentSelectedIds.add(node.id)
                }
                _uiState.update { it.copy(boxSelectedNodeIds = currentSelectedIds) }
            } else {
                // 普通模式
                val memory = repository.getMemoryByUuid(node.id)
                if (memory?.isDocumentNode == true) {
                    // 如果是文档节点，检查是否有全局搜索词
                    val globalQuery = _uiState.value.searchQuery

                    val chunks = if (globalQuery.isNotBlank()) {
                        android.util.Log.d("MemoryVM", "Node click on doc, searching with global query: '$globalQuery'")
                        repository.searchChunksInDocument(memory.id, globalQuery)
                    } else {
                        android.util.Log.d("MemoryVM", "Node click on doc, no global query. Getting all chunks.")
                        repository.getChunksForMemory(memory.id)
                    }

                    _uiState.update {
                        it.copy(
                            selectedNodeId = node.id,
                            selectedMemory = memory,
                            selectedEdge = null,
                            isDocumentViewOpen = true,
                            selectedDocumentChunks = chunks,
                            documentSearchQuery = globalQuery // 预填内部搜索框
                        )
                    }
                } else {
                    _uiState.update { it.copy(selectedNodeId = node.id, selectedMemory = memory, selectedEdge = null, isDocumentViewOpen = false) }
                }
            }
        }
    }

    /** Selects an edge in the graph. */
    fun selectEdge(edge: Edge) {
        _uiState.update { it.copy(selectedEdge = edge, selectedNodeId = null, selectedMemory = null) }
    }

    /** Clears any selection (node or edge). */
    fun clearSelection() {
        _uiState.update { it.copy(selectedMemory = null, selectedNodeId = null, selectedEdge = null) }
    }

    /** 关闭文档视图 */
    fun closeDocumentView() {
        _uiState.update { it.copy(isDocumentViewOpen = false, documentSearchQuery = "", selectedDocumentChunks = emptyList(), selectedMemory = null, selectedNodeId = null) }
    }

    /** 更新文档内搜索的查询词 */
    fun onDocumentSearchQueryChange(query: String) {
        _uiState.update { it.copy(documentSearchQuery = query) }
    }

    /** 在选定文档中执行搜索 */
    fun performSearchInDocument() {
        val query = _uiState.value.documentSearchQuery
        // 如果查询为空，则显示所有块
        if (query.isBlank()) {
            val memoryId = _uiState.value.selectedMemory?.id ?: return
            viewModelScope.launch {
                val chunks = repository.getChunksForMemory(memoryId)
                _uiState.update { it.copy(selectedDocumentChunks = chunks) }
            }
            return
        }

        val memoryId = _uiState.value.selectedMemory?.id ?: return
        viewModelScope.launch {
            val chunks = repository.searchChunksInDocument(memoryId, query)
            _uiState.update { it.copy(selectedDocumentChunks = chunks) }
        }
    }

    /**
     * 显示或隐藏工具测试对话框
     */
    fun showToolTestDialog(visible: Boolean) {
        _uiState.update { it.copy(isToolTestDialogVisible = visible, toolTestResult = "") } // 打开时清空上次结果
    }

    /**
     * 执行记忆查询工具的测试
     */
    fun testQueryTool(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isToolTestLoading = true, toolTestResult = "") }
            try {
                val aiToolHandler = AIToolHandler.getInstance(context)
                // 确保工具已注册
                if (aiToolHandler.getToolExecutor("query_knowledge_library") == null) {
                    aiToolHandler.registerDefaultTools()
                }

                val tool = AITool(
                    name = "query_knowledge_library",
                    parameters = listOf(ToolParameter("query", query))
                )

                val result = aiToolHandler.executeTool(tool)

                val resultString = if (result.success) {
                    // 使用Gson进行格式化输出，更美观
                    Gson().newBuilder().setPrettyPrinting().create().toJson(result.result)
                } else {
                    "Error: ${result.error}"
                }
                _uiState.update { it.copy(isToolTestLoading = false, toolTestResult = resultString) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isToolTestLoading = false, toolTestResult = "An unexpected error occurred: ${e.message}") }
            }
        }
    }

    /**
     * 更新文档区块的内容。
     * @param chunkId 要更新的区块ID。
     * @param newContent 新内容。
     */
    fun updateChunkContent(chunkId: Long, newContent: String) {
        viewModelScope.launch {
            repository.updateChunk(chunkId, newContent)
            // 可选：更新后刷新当前文档的区块列表
            val memoryId = _uiState.value.selectedMemory?.id ?: return@launch
            val chunks = repository.getChunksForMemory(memoryId)
            _uiState.update { it.copy(selectedDocumentChunks = chunks) }
        }
    }

    /** 从外部文件导入记忆 */
    fun importDocument(title: String, filePath: String, fileContent: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.createMemoryFromDocument(title, filePath, fileContent)
                // 刷新图谱
                val updatedGraph = refreshGraph()
                _uiState.update { it.copy(graph = updatedGraph, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to import document: ${e.message}")
                }
            }
        }
    }

    /** 新建记忆 */
    fun createMemory(title: String, content: String, contentType: String = "text/plain") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.createMemory(title, content, contentType)
                val updatedGraph = refreshGraph()
                _uiState.update {
                    it.copy(isLoading = false, isEditing = false, editingMemory = null, graph = updatedGraph)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to create memory: ${e.message}")
                }
            }
        }
    }
    /** 编辑记忆 */
    fun updateMemory(
            memory: Memory,
            newTitle: String,
            newContent: String,
            newContentType: String = memory.contentType
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // 如果是文档节点，其内容是固定的格式
                val contentToUpdate = if (memory.isDocumentNode) "Document: $newTitle" else newContent
                repository.updateMemory(memory, newTitle, contentToUpdate, newContentType)
                val updatedGraph = refreshGraph()
                _uiState.update {
                    it.copy(isLoading = false, isEditing = false, editingMemory = null, graph = updatedGraph, isDocumentViewOpen = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to update memory: ${e.message}")
                }
            }
        }
    }
    /** 删除记忆 */
    fun deleteMemory(memoryId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.deleteMemoryAndIndex(memoryId)
                val updatedGraph = refreshGraph()
                _uiState.update {
                    it.copy(isLoading = false, selectedMemory = null, selectedNodeId = null, graph = updatedGraph, isDocumentViewOpen = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to delete memory: ${e.message}")
                }
            }
        }
    }

    /** 显示批量删除确认对话框 */
    fun showBatchDeleteConfirm() {
        val selectedIds = _uiState.value.boxSelectedNodeIds
        if (selectedIds.isEmpty()) {
            android.util.Log.d("MemoryViewModel", "No nodes selected, aborting delete.")
            return
        }
        _uiState.update { it.copy(showBatchDeleteConfirm = true) }
    }

    /** 隐藏批量删除确认对话框 */
    fun dismissBatchDeleteConfirm() {
        _uiState.update { it.copy(showBatchDeleteConfirm = false) }
    }

    /** 批量删除框选中的记忆（确认后执行） */
    fun deleteSelectedNodes() {
        viewModelScope.launch {
            val selectedIds = _uiState.value.boxSelectedNodeIds
            android.util.Log.d("MemoryViewModel", "deleteSelectedNodes called with ${selectedIds.size} nodes.")
            if (selectedIds.isEmpty()) {
                android.util.Log.d("MemoryViewModel", "No nodes selected, aborting delete.")
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, showBatchDeleteConfirm = false) }
            try {
                android.util.Log.d("MemoryViewModel", "Calling repository.deleteMemoriesByUuids with IDs: $selectedIds")
                repository.deleteMemoriesByUuids(selectedIds)
                val updatedGraph = refreshGraph()
                android.util.Log.d("MemoryViewModel", "Graph refreshed after deletion.")
                _uiState.update {
                    it.copy(
                            isLoading = false,
                            graph = updatedGraph,
                            isBoxSelectionMode = false,
                            boxSelectedNodeIds = emptySet()
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MemoryViewModel", "Failed to delete selected memories", e)
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to delete selected memories: ${e.message}")
                }
            }
        }
    }

    /** 将框选的节点添加到已选择集合中 */
    fun addNodesToSelection(nodeIds: Set<String>) {
        _uiState.update {
            it.copy(boxSelectedNodeIds = it.boxSelectedNodeIds + nodeIds)
        }
    }

    /** 进入新建/编辑状态 */
    fun startEditing(memory: Memory? = null) {
        _uiState.update { it.copy(isEditing = true, editingMemory = memory) }
    }
    /** 取消编辑 */
    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false, editingMemory = null) }
    }

    /** 进入/退出边的编辑状态 */
    fun startEditingEdge(edge: Edge) {
        _uiState.update { it.copy(isEditingEdge = true, editingEdge = edge) }
    }
    fun cancelEditingEdge() {
        _uiState.update { it.copy(isEditingEdge = false, editingEdge = null) }
    }

    /** 更新边的信息 */
    fun updateEdge(edge: Edge, type: String, weight: Float, description: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isEditingEdge = false, editingEdge = null) }
            try {
                repository.updateLink(edge.id, type, weight, description)
                val updatedGraph = refreshGraph()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        selectedEdge = null, // 彻底清空选中状态
                        graph = updatedGraph
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to update link: ${e.message}") }
            }
        }
    }

    /** 删除边 */
    fun deleteEdge(edgeId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.deleteLink(edgeId)
                val updatedGraph = refreshGraph()
                _uiState.update { it.copy(isLoading = false, selectedEdge = null, graph = updatedGraph) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to delete link: ${e.message}") }
            }
        }
    }

    /** 切换连接模式 */
    fun toggleLinkingMode(enabled: Boolean) {
        if (enabled) {
            // 进入连接模式，确保退出其他模式，并清理状态
            _uiState.update {
                it.copy(
                    isLinkingMode = true,
                    linkingNodeIds = emptyList(),
                    isBoxSelectionMode = false,
                    boxSelectedNodeIds = emptySet()
                )
            }
        } else {
            // 退出连接模式
            _uiState.update {
                it.copy(isLinkingMode = false, linkingNodeIds = emptyList())
            }
        }
    }

    /** 切换框选模式 */
    fun toggleBoxSelectionMode(enabled: Boolean) {
        if (enabled) {
            // 进入框选模式，确保退出其他模式，并清理状态
            _uiState.update {
                it.copy(
                    isBoxSelectionMode = true,
                    boxSelectedNodeIds = emptySet(),
                    isLinkingMode = false,
                    linkingNodeIds = emptyList()
                )
            }
        } else {
            // 退出框选模式
            _uiState.update {
                it.copy(isBoxSelectionMode = false, boxSelectedNodeIds = emptySet())
            }
        }
    }

    /** 创建两个记忆之间的连接 */
    fun linkMemories(
            sourceUuid: String,
            targetUuid: String,
            type: String = "related",
            weight: Float = 1.0f,
            description: String = ""
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val source = repository.findMemoryByUuid(sourceUuid)
                val target = repository.findMemoryByUuid(targetUuid)
                if (source != null && target != null) {
                    repository.linkMemories(source, target, type, weight, description)
                    val updatedGraph = refreshGraph()
                    _uiState.update { it.copy(isLoading = false, isLinkingMode = false, linkingNodeIds = emptyList(), graph = updatedGraph) }
                } else {
                    _uiState.update { it.copy(isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to link memories: ${e.message}")
                }
            }
        }
    }
}

/** Factory for creating MemoryViewModel instances with dependencies. */
class MemoryViewModelFactory(private val context: Context, private val profileId: String) :
        ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MemoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST") val repository = MemoryRepository(context, profileId)
            return MemoryViewModel(repository, context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
