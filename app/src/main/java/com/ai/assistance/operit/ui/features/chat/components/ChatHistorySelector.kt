package com.ai.assistance.operit.ui.features.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.ui.common.rememberLocal
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay
import androidx.compose.material3.CircularProgressIndicator

private sealed interface HistoryListItem {
    data class Header(val name: String) : HistoryListItem
    data class Item(val history: ChatHistory) : HistoryListItem
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatHistorySelector(
        modifier: Modifier = Modifier,
        onNewChat: () -> Unit,
        onSelectChat: (String) -> Unit,
        onDeleteChat: (String) -> Unit,
        onUpdateChatTitle: (chatId: String, newTitle: String) -> Unit,
        onCreateGroup: (groupName: String) -> Unit,
    onUpdateChatOrderAndGroup: (reorderedHistories: List<ChatHistory>, movedItem: ChatHistory, targetGroup: String?) -> Unit,
    onUpdateGroupName: (oldName: String, newName: String) -> Unit,
    onDeleteGroup: (groupName: String, deleteChats: Boolean) -> Unit,
        chatHistories: List<ChatHistory>,
        currentId: String?,
        lazyListState: LazyListState? = null,
        onBack: (() -> Unit)? = null,
        searchQuery: String,
        onSearchQueryChange: (String) -> Unit
) {
    var chatToEdit by remember { mutableStateOf<ChatHistory?>(null) }
    var showNewGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var collapsedGroups by rememberLocal("chat_history_collapsed_groups", emptySet<String>())

    var groupActionTarget by remember { mutableStateOf<String?>(null) }
    var groupToRename by remember { mutableStateOf<String?>(null) }
    var groupToDelete by remember { mutableStateOf<String?>(null) }
    var hasLongPressedGroup by rememberLocal("has_long_pressed_group", defaultValue = false)
    
    // 搜索相关状态
    var showSearchBox by remember { mutableStateOf(false) }
    var matchedChatIdsByContent by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSearching by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val chatHistoryManager = remember { ChatHistoryManager.getInstance(context) }
    val actualLazyListState = lazyListState ?: rememberLazyListState()
    val ungroupedText = stringResource(R.string.ungrouped)

    // 当搜索查询改变时，执行内容搜索（带防抖延迟）
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            // 延迟400ms，如果用户继续输入则取消本次搜索（LaunchedEffect会自动取消）
            delay(400)
            // 延迟后再次检查，确保 searchQuery 仍然有效
            // 注意：如果 searchQuery 在延迟期间改变，LaunchedEffect 会重新启动，这里检查的是当前值
            isSearching = true
            try {
                matchedChatIdsByContent = chatHistoryManager.searchChatIdsByContent(searchQuery)
            } catch (e: Exception) {
                // 如果搜索出错，清空结果
                matchedChatIdsByContent = emptySet()
            } finally {
                isSearching = false
            }
        } else {
            matchedChatIdsByContent = emptySet()
            isSearching = false
        }
    }

    val flatItems = remember(chatHistories, collapsedGroups, ungroupedText, searchQuery, matchedChatIdsByContent) {
        // 根据搜索关键词过滤聊天历史
        val filteredHistories = if (searchQuery.isNotBlank()) {
            chatHistories.filter { history ->
                // 搜索标题或分组
                val matchesTitleOrGroup = history.title.contains(searchQuery, ignoreCase = true) ||
                        (history.group?.contains(searchQuery, ignoreCase = true) == true)
                // 搜索聊天内容
                val matchesContent = matchedChatIdsByContent.contains(history.id)
                // 如果匹配标题、分组或内容中的任意一项，就包含在结果中
                matchesTitleOrGroup || matchesContent
            }
        } else {
            chatHistories
        }
        
        filteredHistories
            .groupBy { it.group ?: ungroupedText }
            .flatMap { (group, histories) ->
                val header = HistoryListItem.Header(group)
                val items =
                    if (collapsedGroups.contains(group)) {
                        emptyList()
                    } else {
                        histories.map { HistoryListItem.Item(it) }
                    }
                listOf(header) + items
            }
    }

    val reorderableState = rememberReorderableLazyListState(actualLazyListState) { from, to ->
        val movedItem = flatItems.getOrNull(from.index) as? HistoryListItem.Item ?: return@rememberReorderableLazyListState

        val reorderedFlatList = flatItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }

        var newGroup: String? = null
        val newOrderedHistories = reorderedFlatList
            .mapNotNull {
                when (it) {
                    is HistoryListItem.Header -> {
                        newGroup = it.name.takeIf { name -> name != ungroupedText }
                        null
                    }
                    is HistoryListItem.Item -> it.history.copy(group = newGroup)
                }
            }
            .mapIndexed { index, history -> history.copy(displayOrder = index.toLong()) }

        val finalMovedItem = newOrderedHistories.find { it.id == movedItem.history.id } ?: return@rememberReorderableLazyListState

        onUpdateChatOrderAndGroup(newOrderedHistories, finalMovedItem, finalMovedItem.group)
    }

    if (groupActionTarget != null) {
        Dialog(onDismissRequest = { groupActionTarget = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 6.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.manage_group),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Text(
                        text = groupActionTarget!!,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 重命名选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                groupToRename = groupActionTarget
                                groupActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DriveFileRenameOutline,
                                contentDescription = stringResource(R.string.rename),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.rename_group), 
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // 删除选项
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                groupToDelete = groupActionTarget
                                groupActionTarget = null
                            },
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                stringResource(R.string.delete_group), 
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { groupActionTarget = null },
                        modifier = Modifier.align(Alignment.End).padding(horizontal = 16.dp)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }

    if (groupToRename != null) {
        var newGroupNameText by remember(groupToRename) { mutableStateOf(groupToRename!!) }
        AlertDialog(
            onDismissRequest = { groupToRename = null },
            title = { Text(stringResource(R.string.rename_group)) },
            text = {
                OutlinedTextField(
                    value = newGroupNameText,
                    onValueChange = { newGroupNameText = it },
                    label = { Text(stringResource(R.string.new_group_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newGroupNameText.isNotBlank() && newGroupNameText != groupToRename) {
                            onUpdateGroupName(groupToRename!!, newGroupNameText)
                        }
                        groupToRename = null
                    }
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { groupToRename = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (groupToDelete != null) {
        Dialog(onDismissRequest = { groupToDelete = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 6.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.confirm_delete_group),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = groupToDelete!!,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.choose_delete_method),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = {
                            onDeleteGroup(groupToDelete!!, true)
                            groupToDelete = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.delete_group_and_chats),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.delete_operation_irreversible),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                                )
                            }
            }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            onDeleteGroup(groupToDelete!!, false)
                            groupToDelete = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.delete_group_only),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(R.string.chats_move_to_ungrouped),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(
                        onClick = { groupToDelete = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }

    if (chatToEdit != null) {
        var newTitle by remember { mutableStateOf(chatToEdit!!.title) }
        AlertDialog(
                onDismissRequest = { chatToEdit = null },
                title = { Text(stringResource(R.string.edit_title)) },
                text = {
                    OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            label = { Text(stringResource(R.string.new_title)) },
                            modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                            onClick = {
                                onUpdateChatTitle(chatToEdit!!.id, newTitle)
                                chatToEdit = null
                            }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { chatToEdit = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
        )
    }

    if (showNewGroupDialog) {
        AlertDialog(
                onDismissRequest = { showNewGroupDialog = false },
                title = { Text(stringResource(R.string.new_group)) },
                text = {
                    OutlinedTextField(
                            value = newGroupName,
                            onValueChange = { newGroupName = it },
                            label = { Text(stringResource(R.string.group_name)) },
                            modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                            onClick = {
                                if (newGroupName.isNotBlank()) {
                                    onCreateGroup(newGroupName)
                                    newGroupName = ""
                                    showNewGroupDialog = false
                                }
                            }
                    ) {
                        Text(stringResource(R.string.create))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewGroupDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
        )
    }

    Column(modifier = modifier) {
        // 标题行，左侧返回按钮和标题，右侧放置搜索和分组按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：标题
            Text(
                text = stringResource(R.string.chat_history),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            // 右侧：分组创建、搜索和返回按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showNewGroupDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.AddCircleOutline,
                        contentDescription = stringResource(R.string.new_group),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = {
                        showSearchBox = !showSearchBox
                        // 不再重置搜索查询
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (showSearchBox) Icons.Default.SearchOff else Icons.Default.Search,
                        contentDescription = stringResource(R.string.search),
                        tint = if (showSearchBox) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 新建对话按钮独占一行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { onNewChat() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_chat))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.new_chat))
            }
        }

        // 搜索框
        if (showSearchBox) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text(stringResource(R.string.search)) },
                placeholder = { Text(stringResource(R.string.search_chat_history_hint)) },
                leadingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank() && !isSearching) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.SearchOff, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        var showSwipeHint by rememberLocal(key = "show_swipe_hint", defaultValue = true)

        if (showSwipeHint) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { showSwipeHint = false },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.swipe_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        Divider()
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            state = actualLazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        ) {
            items(
                items = flatItems,
                key = {
                    when (it) {
                        is HistoryListItem.Header -> it.name
                        is HistoryListItem.Item -> it.history.id
                    }
                }
            ) { item ->
                when (item) {
                    is HistoryListItem.Header -> {
                    Surface(
                            modifier = Modifier
                                    .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            collapsedGroups = if (collapsedGroups.contains(item.name)) {
                                                collapsedGroups - item.name
                                        } else {
                                                collapsedGroups + item.name
                                            }
                                        },
                                        onLongPress = {
                                            if (item.name != ungroupedText) {
                                                groupActionTarget = item.name
                                                hasLongPressedGroup = true
                                            }
                                        }
                                    )
                                    },
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shadowElevation = 2.dp,
                            shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = "Group",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (item.name != ungroupedText && !hasLongPressedGroup) {
                                        Text(
                                            text = " (" + stringResource(R.string.long_press_manage) + ")",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                            Icon(
                                    imageVector = if (collapsedGroups.contains(item.name)) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = if (collapsedGroups.contains(item.name)) stringResource(R.string.expand) else stringResource(R.string.collapse)
                            )
                        }
                    }
                }
                    is HistoryListItem.Item -> {
                        val deleteAction = SwipeAction(
                            onSwipe = { onDeleteChat(item.history.id) },
                            icon = {
                                Icon(
                                    modifier = Modifier.padding(16.dp),
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = Color.White
                                )
                            },
                            background = MaterialTheme.colorScheme.error
                        )

                        val editAction = SwipeAction(
                            onSwipe = { chatToEdit = item.history },
                            icon = {
                                Icon(
                                    modifier = Modifier.padding(16.dp),
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.edit_title),
                                    tint = Color.White
                                )
                            },
                            background = MaterialTheme.colorScheme.primary
                        )

                        ReorderableItem(reorderableState, key = item.history.id) { isDragging ->
                            val isSelected = item.history.id == currentId
                            val containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                            val contentColor = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }

                            SwipeableActionsBox(
                                startActions = listOf(editAction),
                                endActions = listOf(deleteAction),
                                swipeThreshold = 100.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(MaterialTheme.shapes.medium)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    color = containerColor,
                                    shape = MaterialTheme.shapes.medium,
                                    shadowElevation = if (isDragging) 8.dp else 0.dp
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp)
                                                .pointerInput(Unit) {
                                                    detectTapGestures(onTap = { onSelectChat(item.history.id) })
                                                },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                modifier = Modifier.draggableHandle(),
                                                onClick = {}
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DragHandle,
                                                    contentDescription = "Reorder",
                                                    tint = contentColor
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = item.history.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = contentColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
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
}
