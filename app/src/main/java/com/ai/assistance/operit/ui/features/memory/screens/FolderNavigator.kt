package com.ai.assistance.operit.ui.features.memory.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width

/**
 * 配置文件选择器
 */
@Composable
private fun ProfileSelector(
    profileList: List<String>,
    profileNameMap: Map<String, String>,
    selectedProfileId: String,
    onProfileSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // It's possible selectedProfileId is not in profileNameMap yet if things are loading.
    val selectedProfileName = profileNameMap[selectedProfileId] ?: selectedProfileId

    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selectedProfileName, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Profile")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // Make dropdown same width as button
            modifier = Modifier.width(218.dp) // 250 - 16*2 = 218
        ) {
            profileList.forEach { profileId ->
                val profileName = profileNameMap[profileId] ?: profileId
                DropdownMenuItem(
                    text = { Text(profileName) },
                    onClick = {
                        onProfileSelected(profileId)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 文件夹树节点数据结构
 */
data class FolderNode(
    val name: String,
    val fullPath: String,
    val children: MutableList<FolderNode> = mutableListOf(),
    var isExpanded: Boolean = true
)

/**
 * 左侧文件夹导航组件
 * @param folderPaths 所有文件夹路径列表（扁平结构，如 ["工作/项目A", "工作/项目B", "生活"]）
 * @param selectedFolderPath 当前选中的文件夹路径
 * @param onFolderSelected 文件夹选中回调
 * @param onFolderRename 重命名文件夹回调
 * @param onFolderDelete 删除文件夹回调
 * @param onFolderCreate 创建文件夹回调
 */
@Composable
fun FolderNavigator(
    folderPaths: List<String>,
    selectedFolderPath: String,
    onFolderSelected: (String) -> Unit,
    onFolderRename: ((String, String) -> Unit)? = null,
    onFolderDelete: ((String) -> Unit)? = null,
    onFolderCreate: ((String) -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    // New parameters for profile selection
    profileList: List<String>,
    profileNameMap: Map<String, String>,
    selectedProfileId: String,
    onProfileSelected: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 构建文件夹树
    val rootNode = remember(folderPaths) {
        buildFolderTree(folderPaths)
    }

    // 用于管理展开/折叠状态（默认全部收起）
    var expandedPaths by remember { mutableStateOf(setOf<String>()) }
    
    // 对话框状态
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var contextMenuFolder by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(250.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp
    ) {
            // 展开状态：显示完整文件夹树
            Column(modifier = Modifier.fillMaxSize()) {
                // 标题和收起按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "文件夹",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // 收起按钮
                    IconButton(
                        onClick = onDismissRequest
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "关闭侧边栏",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                ProfileSelector(
                    profileList = profileList,
                    profileNameMap = profileNameMap,
                    selectedProfileId = selectedProfileId,
                    onProfileSelected = onProfileSelected
                )
                
                // 新建文件夹按钮和刷新按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    // 刷新按钮
                    if (onRefresh != null) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新文件夹列表",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    
                    // 新建文件夹按钮
                    IconButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = "新建文件夹",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // "全部"选项
                FolderItem(
                    name = "全部",
                    fullPath = "",
                    level = 0,
                    isSelected = selectedFolderPath.isEmpty(),
                    isExpanded = false,
                    hasChildren = false,
                    onToggleExpand = {},
                    onClick = { onFolderSelected("") },
                    onLongClick = null
                )

                // 文件夹树
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    renderFolderTree(
                        nodes = rootNode.children,
                        level = 0,
                        selectedPath = selectedFolderPath,
                        expandedPaths = expandedPaths,
                        onToggleExpand = { path ->
                            expandedPaths = if (path in expandedPaths) {
                                expandedPaths - path
                            } else {
                                expandedPaths + path
                            }
                        },
                        onFolderSelected = onFolderSelected,
                        onFolderLongClick = { path ->
                            contextMenuFolder = path
                        }
                    )
                }
            }
    }
    
    // 对话框
    if (showCreateDialog) {
        FolderCreateDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { newPath ->
                onFolderCreate?.invoke(newPath)
                showCreateDialog = false
            }
        )
    }
    
    if (showRenameDialog && contextMenuFolder != null) {
        FolderRenameDialog(
            currentPath = contextMenuFolder!!,
            onDismiss = {
                showRenameDialog = false
                contextMenuFolder = null
            },
            onRename = { newPath ->
                onFolderRename?.invoke(contextMenuFolder!!, newPath)
                showRenameDialog = false
                contextMenuFolder = null
            }
        )
    }
    
    if (showDeleteDialog && contextMenuFolder != null) {
        FolderDeleteDialog(
            folderPath = contextMenuFolder!!,
            onDismiss = {
                showDeleteDialog = false
                contextMenuFolder = null
            },
            onConfirm = {
                onFolderDelete?.invoke(contextMenuFolder!!)
                showDeleteDialog = false
                contextMenuFolder = null
            }
        )
    }
    
    // 右键菜单（不与其他对话框同时显示）
    if (contextMenuFolder != null && !showRenameDialog && !showDeleteDialog) {
        FolderContextMenu(
            folderPath = contextMenuFolder!!,
            onDismiss = { contextMenuFolder = null },
            onRename = {
                showRenameDialog = true
            },
            onDelete = {
                showDeleteDialog = true
            }
        )
    }
}

/**
 * 递归渲染文件夹树
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.renderFolderTree(
    nodes: List<FolderNode>,
    level: Int,
    selectedPath: String,
    expandedPaths: Set<String>,
    onToggleExpand: (String) -> Unit,
    onFolderSelected: (String) -> Unit,
    onFolderLongClick: (String) -> Unit
) {
    nodes.forEach { node ->
        val isExpanded = node.fullPath in expandedPaths
        val hasChildren = node.children.isNotEmpty()

        item(key = node.fullPath) {
            FolderItem(
                name = node.name,
                fullPath = node.fullPath,
                level = level,
                isSelected = selectedPath == node.fullPath,
                isExpanded = isExpanded,
                hasChildren = hasChildren,
                onToggleExpand = { onToggleExpand(node.fullPath) },
                onClick = { onFolderSelected(node.fullPath) },
                onLongClick = { onFolderLongClick(node.fullPath) },
                modifier = Modifier.animateItemPlacement(
                    animationSpec = tween(durationMillis = 300)
                )
            )
        }

        // 如果展开且有子节点，递归渲染
        if (isExpanded && hasChildren) {
            renderFolderTree(
                nodes = node.children,
                level = level + 1,
                selectedPath = selectedPath,
                expandedPaths = expandedPaths,
                onToggleExpand = onToggleExpand,
                onFolderSelected = onFolderSelected,
                onFolderLongClick = onFolderLongClick
            )
        }
    }
}

/**
 * 单个文件夹项
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderItem(
    name: String,
    fullPath: String,
    level: Int,
    isSelected: Boolean,
    isExpanded: Boolean,
    hasChildren: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
    } else {
        Color.Transparent
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    
    // 展开箭头的旋转动画
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "expand_rotation"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier.clickable { onClick() }
                }
            )
            .padding(start = (8 + level * 16).dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 展开/折叠图标
        if (hasChildren) {
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) "折叠" else "展开",
                    tint = textColor.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(rotationAngle)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }

        // 文件夹图标
        Icon(
            imageVector = if (hasChildren && isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
            contentDescription = "文件夹",
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // 文件夹名称
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * 从扁平路径列表构建文件夹树
 * @param paths 路径列表，如 ["工作/项目A", "工作/项目B", "生活/健康"]
 * @return 根节点（虚拟根节点，不显示）
 */
private fun buildFolderTree(paths: List<String>): FolderNode {
    val root = FolderNode("", "")

    paths.forEach { path ->
        if (path.isBlank()) return@forEach

        val parts = path.split("/").filter { it.isNotBlank() }
        var currentNode = root
        var currentPath = ""

        parts.forEachIndexed { index, part ->
            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"

            // 查找是否已有此节点
            var childNode = currentNode.children.find { it.name == part }
            if (childNode == null) {
                // 创建新节点
                childNode = FolderNode(
                    name = part,
                    fullPath = currentPath,
                    isExpanded = false // 默认收起
                )
                currentNode.children.add(childNode)
            }
            currentNode = childNode
        }
    }

    return root
}

/**
 * 文件夹右键菜单
 */
@Composable
private fun FolderContextMenu(
    folderPath: String,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文件夹操作") },
        text = {
            Column {
                Text("文件夹: $folderPath", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    onRename()
                    // 不调用 onDismiss()，让菜单自动隐藏
                }) {
                    Text("重命名")
                }
                TextButton(onClick = {
                    onDelete()
                    // 不调用 onDismiss()，让菜单自动隐藏
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 创建文件夹对话框
 */
@Composable
private fun FolderCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建文件夹") },
        text = {
            Column {
                Text("请输入文件夹路径（可用 / 分隔层级）", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("文件夹路径") },
                    placeholder = { Text("例如: 工作/项目A") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (folderName.isNotBlank()) onCreate(folderName.trim()) },
                enabled = folderName.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 重命名文件夹对话框
 */
@Composable
private fun FolderRenameDialog(
    currentPath: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentPath) }
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名文件夹") },
        text = {
            Column {
                Text("当前路径: $currentPath", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新路径") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (newName.isNotBlank() && newName != currentPath) onRename(newName.trim()) },
                enabled = newName.isNotBlank() && newName != currentPath
            ) {
                Text("重命名")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 删除文件夹确认对话框
 */
@Composable
private fun FolderDeleteDialog(
    folderPath: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除文件夹") },
        text = {
            Column {
                Text("确定要删除文件夹吗？", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("文件夹: $folderPath", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "注意：该文件夹下的所有记忆将被移至\"未分类\"文件夹。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

