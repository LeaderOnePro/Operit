package com.ai.assistance.operit.ui.features.settings.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.CustomEmoji
import com.ai.assistance.operit.data.preferences.CustomEmojiPreferences
import com.ai.assistance.operit.ui.features.settings.viewmodels.CustomEmojiViewModel
import kotlinx.coroutines.launch

/**
 * 自定义表情管理页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomEmojiManagementScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = remember { CustomEmojiViewModel(context) }
    val scope = rememberCoroutineScope()

    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val emojis by viewModel.emojisInCategory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()

    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteCategoryDialog by remember { mutableStateOf(false) }
    var showDeleteEmojiDialog by remember { mutableStateOf<CustomEmoji?>(null) }
    var showImagePreview by remember { mutableStateOf<Uri?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    // 图片选择器
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addEmojis(selectedCategory, uris)
        }
    }

    // 显示消息
    LaunchedEffect(successMessage) {
        successMessage?.let {
            // 显示成功提示
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { imagePickerLauncher.launch("image/*") }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_emoji))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 类别选择器
            CategorySelector(
                categories = categories,
                selectedCategory = selectedCategory,
                onCategorySelected = { viewModel.selectCategory(it) },
                modifier = Modifier.padding(16.dp)
            )
            
            // 分组管理和重置按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：分组管理按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 创建分组按钮
                    OutlinedButton(
                        onClick = { showCreateCategoryDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "创建分组",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("创建分组", fontSize = 12.sp)
                    }
                    
                    // 删除分组按钮
                    OutlinedButton(
                        onClick = { showDeleteCategoryDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除分组",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除分组", fontSize = 12.sp)
                    }
                }
                
                // 右侧：重置按钮
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "重置为默认",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("重置为默认", fontSize = 12.sp)
                }
            }

            // 提示信息
            if (emojis.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "点击右下角按钮添加表情",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 表情网格
                EmojiGrid(
                    emojis = emojis,
                    onEmojiClick = { emoji ->
                        showImagePreview = viewModel.getEmojiUri(emoji)
                    },
                    onEmojiLongClick = { emoji ->
                        showDeleteEmojiDialog = emoji
                    },
                    getEmojiUri = { viewModel.getEmojiUri(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 加载指示器
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // 新建类别对话框
    if (showCreateCategoryDialog) {
        CreateCategoryDialog(
            onDismiss = { showCreateCategoryDialog = false },
            onCreate = { categoryName ->
                scope.launch {
                    if (viewModel.createCategory(categoryName)) {
                        showCreateCategoryDialog = false
                    }
                }
            }
        )
    }

    // 删除类别确认对话框
    if (showDeleteCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCategoryDialog = false },
            title = { Text(stringResource(R.string.delete_category)) },
            text = { Text(stringResource(R.string.confirm_delete_category, selectedCategory)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCategory(selectedCategory)
                        showDeleteCategoryDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCategoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 删除表情确认对话框
    showDeleteEmojiDialog?.let { emoji ->
        AlertDialog(
            onDismissRequest = { showDeleteEmojiDialog = null },
            title = { Text(stringResource(R.string.delete_emoji)) },
            text = { Text(stringResource(R.string.confirm_delete_emoji)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEmoji(emoji.id)
                        showDeleteEmojiDialog = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteEmojiDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 重置确认对话框
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("重置为默认表情") },
            text = { Text("此操作将删除所有自定义表情，并恢复为默认表情。此操作不可撤销，确定要继续吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetToDefault()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("确定重置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 图片预览对话框
    showImagePreview?.let { uri ->
        Dialog(onDismissRequest = { showImagePreview = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                AsyncImage(
                    model = uri,
                    contentDescription = "表情预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

    // 显示错误和成功消息
    errorMessage?.let {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { viewModel.clearErrorMessage() }) {
                    Text("关闭")
                }
            }
        ) {
            Text(it)
        }
    }

    successMessage?.let {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = { viewModel.clearSuccessMessage() }) {
                    Text("关闭")
                }
            }
        ) {
            Text(it)
        }
    }
}

/**
 * 类别选择器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelector(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedCategory,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.select_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(category)
                            if (category !in CustomEmojiPreferences.BUILTIN_EMOTIONS) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "自定义",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * 表情网格
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiGrid(
    emojis: List<CustomEmoji>,
    onEmojiClick: (CustomEmoji) -> Unit,
    onEmojiLongClick: (CustomEmoji) -> Unit,
    getEmojiUri: (CustomEmoji) -> Uri,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        items(emojis, key = { it.id }) { emoji ->
            EmojiCard(
                emoji = emoji,
                onClick = { onEmojiClick(emoji) },
                onLongClick = { onEmojiLongClick(emoji) },
                uri = getEmojiUri(emoji),
                showDeleteIcon = true
            )
        }
    }
}

/**
 * 表情卡片
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiCard(
    emoji: CustomEmoji,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    uri: Uri,
    showDeleteIcon: Boolean
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = uri,
                contentDescription = emoji.emotionCategory,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            if (showDeleteIcon) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 新建类别对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateCategoryDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_category)) },
        text = {
            Column {
                Text(stringResource(R.string.category_name_hint))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = {
                        categoryName = it.lowercase()
                        isError = !it.matches(Regex("^[a-z0-9_]*$"))
                    },
                    label = { Text(stringResource(R.string.category_name)) },
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(stringResource(R.string.invalid_category_name))
                        }
                    },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(categoryName) },
                enabled = categoryName.isNotBlank() && !isError
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

