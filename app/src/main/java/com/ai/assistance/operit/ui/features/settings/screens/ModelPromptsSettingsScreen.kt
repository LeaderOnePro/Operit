package com.ai.assistance.operit.ui.features.settings.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ai.assistance.operit.ui.components.CustomScaffold
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.PromptTag
import com.ai.assistance.operit.data.model.TagType
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.PromptTagManager
import com.ai.assistance.operit.data.preferences.PromptPreferencesManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.FileUtils
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.ui.features.settings.components.CharacterCardDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ModelPromptsSettingsScreen(
        onBackPressed: () -> Unit = {},
        onNavigateToMarket: () -> Unit = {},
        onNavigateToPersonaGeneration: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // 管理器
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val promptTagManager = remember { PromptTagManager.getInstance(context) }
    val oldPromptPreferencesManager = remember { PromptPreferencesManager(context) }
    val userPreferencesManager = remember { UserPreferencesManager(context) }
    
    // 获取当前活跃角色卡ID
    val activeCharacterCardId by characterCardManager.activeCharacterCardIdFlow.collectAsState(initial = "")
    
    // 状态
    var currentTab by remember { mutableStateOf(0) } // 0: 角色卡, 1: 标签, 2: 旧配置
    var showSaveSuccessMessage by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    // 角色卡相关状态
    val characterCardList by characterCardManager.characterCardListFlow.collectAsState(initial = emptyList())
    var showAddCharacterCardDialog by remember { mutableStateOf(false) }
    var showEditCharacterCardDialog by remember { mutableStateOf(false) }
    var editingCharacterCard by remember { mutableStateOf<CharacterCard?>(null) }
    
    // 酒馆角色卡导入相关状态
    var showImportSuccessMessage by remember { mutableStateOf(false) }
    var showImportErrorMessage by remember { mutableStateOf(false) }
    var importErrorMessage by remember { mutableStateOf("") }

    // Avatar picker and cropper launcher
    val cropAvatarLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            val croppedUri = result.uriContent
            if (croppedUri != null) {
                scope.launch {
                    editingCharacterCard?.let { card ->
                        val internalUri = FileUtils.copyFileToInternalStorage(context, croppedUri, "avatar_${card.id}")
                        if (internalUri != null) {
                            userPreferencesManager.saveAiAvatarForCharacterCard(card.id, internalUri.toString())
                            Toast.makeText(context, context.getString(R.string.avatar_updated), Toast.LENGTH_SHORT).show()
                            refreshTrigger++ 
                        } else {
                            Toast.makeText(context, context.getString(R.string.theme_copy_failed), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        } else if (result.error != null) {
            Toast.makeText(context, context.getString(R.string.avatar_crop_failed, result.error!!.message), Toast.LENGTH_LONG).show()
        }
    }

    fun launchAvatarCrop(uri: Uri) {
        val cropOptions = CropImageContractOptions(
            uri,
            CropImageOptions().apply {
                guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
                outputCompressFormat = android.graphics.Bitmap.CompressFormat.PNG
                outputCompressQuality = 90
                fixAspectRatio = true
                aspectRatioX = 1
                aspectRatioY = 1
                cropMenuCropButtonTitle = context.getString(R.string.theme_crop_done)
                activityTitle = "裁剪头像"
                toolbarColor = Color.Gray.toArgb()
                toolbarTitleColor = Color.White.toArgb()
            }
        )
        cropAvatarLauncher.launch(cropOptions)
    }

    val avatarImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            launchAvatarCrop(uri)
        }
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { fileUri ->
            scope.launch {
                try {
                    val mimeType = context.contentResolver.getType(fileUri)
                    val fileName = fileUri.lastPathSegment ?: ""

                    val result = when {
                        mimeType == "image/png" || fileName.lowercase().endsWith(".png") -> {
                            context.contentResolver.openInputStream(fileUri).use { inputStream ->
                                requireNotNull(inputStream) { "无法读取文件" }
                                characterCardManager.createCharacterCardFromTavernPng(inputStream)
                            }
                        }
                        mimeType == "application/json" || fileName.lowercase().endsWith(".json") -> {
                            context.contentResolver.openInputStream(fileUri).use { inputStream ->
                                requireNotNull(inputStream) { "无法读取文件" }
                                val jsonContent = inputStream.bufferedReader().readText()
                                characterCardManager.createCharacterCardFromTavernJson(jsonContent)
                            }
                        }
                        else -> {
                            // Fallback for unknown file types
                            try {
                                // Try JSON first
                                context.contentResolver.openInputStream(fileUri).use { inputStream ->
                                    requireNotNull(inputStream) { "无法读取文件" }
                                    val jsonContent = inputStream.bufferedReader().readText()
                                    characterCardManager.createCharacterCardFromTavernJson(jsonContent)
                                }
                            } catch (e: Exception) {
                                // If JSON fails, try PNG
                                context.contentResolver.openInputStream(fileUri).use { inputStream ->
                                    requireNotNull(inputStream) { "无法读取文件" }
                                    characterCardManager.createCharacterCardFromTavernPng(inputStream)
                                }
                            }
                        }
                    }

                    result.onSuccess {
                        showImportSuccessMessage = true
                        refreshTrigger++
                    }.onFailure { exception ->
                        importErrorMessage = exception.message ?: context.getString(R.string.unknown_error)
                        showImportErrorMessage = true
                    }
                } catch (e: Exception) {
                    importErrorMessage = context.getString(R.string.file_read_error, e.message ?: "")
                    showImportErrorMessage = true
                }
            }
        }
    }
    
    // 标签相关状态
    val tagList by promptTagManager.tagListFlow.collectAsState(initial = emptyList())
    var showAddTagDialog by remember { mutableStateOf(false) }
    var showEditTagDialog by remember { mutableStateOf(false) }
    var editingTag by remember { mutableStateOf<PromptTag?>(null) }

    // 旧配置相关状态
    val oldProfileList by oldPromptPreferencesManager.profileListFlow.collectAsState(initial = emptyList())
    var showOldConfigDialog by remember { mutableStateOf(false) }
    var selectedOldProfileId by remember { mutableStateOf("") }
    
    // 初始化
    LaunchedEffect(Unit) {
        characterCardManager.initializeIfNeeded()
    }
    
    // 获取所有标签
    var allTags by remember { mutableStateOf(emptyList<PromptTag>()) }
    LaunchedEffect(tagList) {
        scope.launch {
            val tags = promptTagManager.getAllTags()
            allTags = tags
        }
    }

    // 获取所有角色卡
    var allCharacterCards by remember { mutableStateOf(emptyList<CharacterCard>()) }
    LaunchedEffect(characterCardList, refreshTrigger) {
        scope.launch {
            val cards = characterCardManager.getAllCharacterCards()
            allCharacterCards = cards
        }
    }
    
    // 保存角色卡
    fun saveCharacterCard() {
        editingCharacterCard?.let { card ->
            scope.launch {
                if (card.id.isEmpty()) {
                    // 新建
                    val newCardId = characterCardManager.createCharacterCard(card)
                    userPreferencesManager.saveCustomChatTitleForCharacterCard(newCardId, card.name.ifEmpty { null })
                } else {
                    // 更新
                    characterCardManager.updateCharacterCard(card)
                    userPreferencesManager.saveCustomChatTitleForCharacterCard(card.id, card.name.ifEmpty { null })
                }
                showAddCharacterCardDialog = false
                showEditCharacterCardDialog = false
                editingCharacterCard = null
                showSaveSuccessMessage = true
                refreshTrigger++
            }
        }
    }

    // 保存标签
    fun saveTag() {
        editingTag?.let { tag ->
            scope.launch {
                if (tag.id.isEmpty()) {
                    // 新建
                    promptTagManager.createPromptTag(
                        name = tag.name,
                        description = tag.description,
                        promptContent = tag.promptContent,
                        tagType = tag.tagType
                    )
                } else {
                    // 更新
                    promptTagManager.updatePromptTag(
                        id = tag.id,
                        name = tag.name,
                        description = tag.description,
                        promptContent = tag.promptContent
                    )
                }
                showEditTagDialog = false
                editingTag = null
                showSaveSuccessMessage = true
            }
        }
    }

    // 删除角色卡
    fun deleteCharacterCard(id: String) {
        scope.launch {
            characterCardManager.deleteCharacterCard(id)
        }
    }
    
    // 删除标签
    fun deleteTag(id: String) {
        scope.launch {
            promptTagManager.deletePromptTag(id)
            }
        }
    
    CustomScaffold() { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 标签栏
                TabRow(selectedTabIndex = currentTab) {
                    Tab(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(stringResource(R.string.character_cards), fontSize = 12.sp)
                        }
                    }
                    Tab(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Label,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(stringResource(R.string.tags), fontSize = 12.sp)
                        }
                    }
                    Tab(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(stringResource(R.string.old_config), fontSize = 12.sp)
                        }
                    }
                }

                // 内容区域
                when (currentTab) {
                    0 -> CharacterCardTab(
                        characterCards = allCharacterCards,
                        activeCharacterCardId = activeCharacterCardId,
                        allTags = allTags,
                        onAddCharacterCard = {
                            editingCharacterCard = CharacterCard(
                                id = "",
                                name = "",
                                description = "",
                                characterSetting = "",
                                otherContent = "",
                                attachedTagIds = emptyList(),
                                advancedCustomPrompt = "",
                                marks = ""
                            )
                            showAddCharacterCardDialog = true
                        },
                        onEditCharacterCard = { card ->
                            editingCharacterCard = card.copy()
                            showEditCharacterCardDialog = true
                        },
                        onDeleteCharacterCard = { deleteCharacterCard(it) },
                        onSetActiveCharacterCard = { cardId ->
                            scope.launch {
                                characterCardManager.setActiveCharacterCard(cardId)
                            }
                        },
                        onNavigateToPersonaGeneration = onNavigateToPersonaGeneration,
                        onImportTavernCard = {
                            filePickerLauncher.launch("*/*")
                        }
                    )
                    1 -> TagTab(
                        tags = allTags,
                        onAddTag = {
                            editingTag = PromptTag(
                                id = "",
                                name = "",
                                description = "",
                                promptContent = "",
                                tagType = TagType.CUSTOM
                            )
                            showAddTagDialog = true
                        },
                        onEditTag = { tag ->
                            editingTag = tag.copy()
                            showEditTagDialog = true
                        },
                        onDeleteTag = { deleteTag(it) },
                        onNavigateToMarket = onNavigateToMarket
                    )
                    2 -> OldConfigTab(
                        profileList = oldProfileList,
                        promptPreferencesManager = oldPromptPreferencesManager,
                        onShowOldConfig = { profileId ->
                            selectedOldProfileId = profileId
                            showOldConfigDialog = true
                        }
                    )
                }
            }
            
                        // 成功保存消息
            if (showSaveSuccessMessage) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1500)
                    showSaveSuccessMessage = false
                }
                
                Card(
                            modifier = Modifier
                                .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                        ) {
                            Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                            imageVector = Icons.Default.Check,
                                        contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                                    )
                        Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                            text = stringResource(R.string.save_successful),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                                    )
                                }
                            }
                        }
                        
            // 导入成功消息
            if (showImportSuccessMessage) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(2000)
                    showImportSuccessMessage = false
                }
                
                Card(
                            modifier = Modifier
                                .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                        ) {
                            Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.tavern_card_import_success),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            // 导入失败消息
            if (showImportErrorMessage) {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(3000)
                    showImportErrorMessage = false
                }
                
                Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                            ) {
                                Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                                    Text(
                                text = stringResource(R.string.tavern_card_import_failed),
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (importErrorMessage.isNotBlank()) {
                                Text(
                                    text = importErrorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                    )
                                }
                        }
                    }
                }
            }
        }
    }
    
    // 新建角色卡对话框
    if (showAddCharacterCardDialog) {
        CharacterCardDialog(
            characterCard = editingCharacterCard ?: CharacterCard(
                id = "",
                name = "",
                description = "",
                characterSetting = "",
                otherContent = "",
                attachedTagIds = emptyList(),
                advancedCustomPrompt = ""
            ),
            allTags = allTags,
            userPreferencesManager = userPreferencesManager,
            onDismiss = {
                showAddCharacterCardDialog = false
                editingCharacterCard = null
            },
            onSave = { card ->
                editingCharacterCard = card
                saveCharacterCard()
            },
            onAvatarChange = {
                avatarImagePicker.launch("image/*")
            },
            onAvatarReset = {
                scope.launch {
                    editingCharacterCard?.let {
                        userPreferencesManager.saveAiAvatarForCharacterCard(it.id, null)
                        refreshTrigger++
                    }
                }
            }
        )
    }
    
    // 编辑角色卡对话框
    if (showEditCharacterCardDialog) {
        CharacterCardDialog(
            characterCard = editingCharacterCard ?: CharacterCard(
                id = "",
                name = "",
                description = "",
                characterSetting = "",
                otherContent = "",
                attachedTagIds = emptyList(),
                advancedCustomPrompt = ""
            ),
            allTags = allTags,
            userPreferencesManager = userPreferencesManager,
            onDismiss = {
                showEditCharacterCardDialog = false
                editingCharacterCard = null
            },
            onSave = { card ->
                editingCharacterCard = card
                saveCharacterCard()
                                },
            onAvatarChange = {
                avatarImagePicker.launch("image/*")
            },
            onAvatarReset = {
                scope.launch {
                    editingCharacterCard?.let {
                        userPreferencesManager.saveAiAvatarForCharacterCard(it.id, null)
                        refreshTrigger++
                    }
                }
            }
        )
                        }
                        
    // 新建标签对话框
    if (showAddTagDialog) {
        TagDialog(
            tag = editingTag ?: PromptTag(
                id = "",
                name = "",
                description = "",
                promptContent = "",
                tagType = TagType.CUSTOM
            ),
            onDismiss = {
                showAddTagDialog = false
                editingTag = null
            },
            onSave = {
                editingTag = it
                saveTag()
            }
        )
    }
    
    // 编辑标签对话框
    if (showEditTagDialog) {
        TagDialog(
            tag = editingTag ?: PromptTag(
                id = "",
                name = "",
                description = "",
                promptContent = "",
                tagType = TagType.CUSTOM
            ),
            onDismiss = {
                showEditTagDialog = false
                editingTag = null
            },
            onSave = {
                editingTag = it
                saveTag()
            }
        )
    }
    
    // 旧配置查看对话框
    if (showOldConfigDialog) {
        OldConfigDialog(
            profileId = selectedOldProfileId,
            promptPreferencesManager = oldPromptPreferencesManager,
            onDismiss = {
                showOldConfigDialog = false
                selectedOldProfileId = ""
                                        }
        )
    }
    
    // 成功保存消息
}

// 角色卡标签页
@Composable
fun CharacterCardTab(
    characterCards: List<CharacterCard>,
    activeCharacterCardId: String,
    allTags: List<PromptTag>,
    onAddCharacterCard: () -> Unit,
    onEditCharacterCard: (CharacterCard) -> Unit,
    onDeleteCharacterCard: (String) -> Unit,
    onSetActiveCharacterCard: (String) -> Unit,
    onNavigateToPersonaGeneration: () -> Unit,
    onImportTavernCard: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            // 标题和按钮区域
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 第一行：标题和新建按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                                    Text(
                        text = stringResource(R.string.character_card_management),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = onAddCharacterCard,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.create_new), fontSize = 13.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 第二行：功能按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToPersonaGeneration,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ai_creation), fontSize = 12.sp)
                    }
                    
                    OutlinedButton(
                        onClick = onImportTavernCard,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.import_tavern_card), fontSize = 12.sp)
                            }
                        }
                    }
                }

        // 角色卡列表
        items(characterCards) { characterCard ->
            CharacterCardItem(
                characterCard = characterCard,
                isActive = characterCard.id == activeCharacterCardId,
                allTags = allTags,
                onEdit = { onEditCharacterCard(characterCard) },
                onDelete = { onDeleteCharacterCard(characterCard.id) },
                onSetActive = { onSetActiveCharacterCard(characterCard.id) }
            )
        }
    }
}

// 角色卡项目
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterCardItem(
    characterCard: CharacterCard,
    isActive: Boolean,
    allTags: List<PromptTag>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetActive: () -> Unit
) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
        border = BorderStroke(
            width = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
                        ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 标题和操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = characterCard.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isActive) {
                            Spacer(Modifier.width(8.dp))
                            AssistChip(
                                onClick = { },
                                label = { Text(stringResource(R.string.currently_active), fontSize = 10.sp) },
                                leadingIcon = { 
                                    Icon(
                                        Icons.Default.Check, 
                                        contentDescription = stringResource(R.string.currently_active), 
                                        modifier = Modifier.size(14.dp)
                                    ) 
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                    }
                    if (characterCard.description.isNotBlank()) {
                                Text(
                            text = characterCard.description,
                            style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isActive) {
                        TextButton(
                            onClick = onSetActive,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(stringResource(R.string.set_active), fontSize = 13.sp)
                        }
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(16.dp))
                    }
                    
                    if (!characterCard.isDefault) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            
            // 角色设定预览
            if (characterCard.characterSetting.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                    text = stringResource(R.string.character_setting_preview, characterCard.characterSetting.take(40)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            
            // 其他内容预览
            if (characterCard.otherContent.isNotBlank()) {
                                    Text(
                    text = stringResource(R.string.other_content_preview, characterCard.otherContent.take(40)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            
            // 附着的标签
            if (characterCard.attachedTagIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    characterCard.attachedTagIds.take(3).forEach { tagId ->
                        val tag = allTags.find { it.id == tagId }
                        tag?.let {
                            AssistChip(
                                onClick = { },
                                label = { Text(it.name, fontSize = 10.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                modifier = Modifier.height(24.dp)
                                        )
                        }
                    }
                    if (characterCard.attachedTagIds.size > 3) {
                                        Text(
                            text = "+${characterCard.attachedTagIds.size - 3}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            
            // 高级自定义提示词预览
            if (characterCard.advancedCustomPrompt.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                    text = stringResource(R.string.advanced_custom_preview, characterCard.advancedCustomPrompt.take(40)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// 标签标签页
@Composable
fun TagTab(
    tags: List<PromptTag>,
    onAddTag: () -> Unit,
    onEditTag: (PromptTag) -> Unit,
    onDeleteTag: (String) -> Unit,
    onNavigateToMarket: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            // 标题和按钮区域
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 第一行：标题和新建按钮
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                    Text(
                        text = stringResource(R.string.tag_management),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = onAddTag,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.create_new_tag), fontSize = 13.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 第二行：标签市场按钮
                OutlinedButton(
                    onClick = onNavigateToMarket,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.tag_market), fontSize = 12.sp)
                }
            }
        }
        
        // 系统标签
        val systemTags = tags.filter { it.isSystemTag }
        if (systemTags.isNotEmpty()) {
            item {
                                                    Text(
                    text = stringResource(R.string.system_tags),
                    style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            
            items(systemTags) { tag ->
                TagItem(
                    tag = tag,
                    onEdit = { onEditTag(tag) },
                    onDelete = { onDeleteTag(tag.id) }
                )
            }
        }
        
        // 自定义标签
        val customTags = tags.filter { !it.isSystemTag }
        if (customTags.isNotEmpty()) {
            item {
                                Text(
                    text = stringResource(R.string.custom_tags),
                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            
            items(customTags) { tag ->
                TagItem(
                    tag = tag,
                    onEdit = { onEditTag(tag) },
                    onDelete = { onDeleteTag(tag.id) }
                )
            }
        }
    }
}

// 标签项目
@Composable
fun TagItem(
    tag: PromptTag,
    onEdit: () -> Unit,
    onDelete: () -> Unit
                                        ) {
                                            Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (tag.isSystemTag) 
                MaterialTheme.colorScheme.secondaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
                                                        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                    text = tag.name,
                                            style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                                        )
                if (tag.description.isNotBlank()) {
                                                        Text(
                        text = tag.description,
                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                if (tag.promptContent.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.content_preview, tag.promptContent.take(50)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                                        )
                                    }
                                }

                                    Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit), modifier = Modifier.size(16.dp))
                }
                
                if (!tag.isSystemTag) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(16.dp))
                                                            }
                                    }
                                }
                            }
                        }
                    }

// 旧配置标签页
@Composable
fun OldConfigTab(
    profileList: List<String>,
    promptPreferencesManager: PromptPreferencesManager,
    onShowOldConfig: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
                                                    Text(
                text = stringResource(R.string.old_prompt_config),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
                                                        Text(
                text = stringResource(R.string.old_prompt_config_desc),
                style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 13.sp
            )
        }
        
        items(profileList) { profileId ->
            OldConfigItem(
                profileId = profileId,
                promptPreferencesManager = promptPreferencesManager,
                onShowConfig = { onShowOldConfig(profileId) }
            )
        }
    }
}

// 旧配置项目
@Composable
fun OldConfigItem(
    profileId: String,
    promptPreferencesManager: PromptPreferencesManager,
    onShowConfig: () -> Unit
) {
    val profile by promptPreferencesManager.getPromptProfileFlow(profileId).collectAsState(
        initial = null
    )
    
    profile?.let { promptProfile ->
        Card(
                                            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp)
        ) {
                        Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShowConfig)
                    .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = promptProfile.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    if (promptProfile.introPrompt.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.intro_prompt_preview, promptProfile.introPrompt.take(50)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
                
                            Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.view_details),
                                tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                            )
            }
        }
    }
}

// 标签对话框
@Composable
fun TagDialog(
    tag: PromptTag,
    onDismiss: () -> Unit,
    onSave: (PromptTag) -> Unit
) {
    var name by remember { mutableStateOf(tag.name) }
    var description by remember { mutableStateOf(tag.description) }
    var promptContent by remember { mutableStateOf(tag.promptContent) }
    var tagType by remember { mutableStateOf(tag.tagType) }
    
        AlertDialog(
        onDismissRequest = onDismiss,
            title = {
                Text(
                if (tag.id.isEmpty()) stringResource(R.string.create_tag) else stringResource(R.string.edit_tag),
                style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tag_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.description_optional)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (!tag.isSystemTag) {
                    // 只有自定义标签可以选择类型
                    Text(
                        text = stringResource(R.string.tag_type),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TagType.values().filter { it != TagType.CUSTOM }.forEach { type ->
                            FilterChip(
                                selected = tagType == type,
                                onClick = { tagType = type },
                                label = { Text(type.name) }
                            )
                        }
                    }
                }
                
                    OutlinedTextField(
                    value = promptContent,
                    onValueChange = { promptContent = it },
                    label = { Text(stringResource(R.string.prompt_content)) },
                        modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                    onSave(
                        tag.copy(
                            name = name,
                            description = description,
                            promptContent = promptContent,
                            tagType = tagType
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.save))
            }
            },
            dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// 旧配置查看对话框
@Composable
fun OldConfigDialog(
    profileId: String,
    promptPreferencesManager: PromptPreferencesManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val profile by promptPreferencesManager.getPromptProfileFlow(profileId).collectAsState(
        initial = null
    )
    
    // 复制到剪贴板的函数
    fun copyToClipboard(text: String, label: String) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        // 这里可以添加一个 Toast 提示，但为了简洁我们先不添加
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.old_config_details),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            profile?.let { promptProfile ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.config_name_format, promptProfile.name),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    
                    if (promptProfile.introPrompt.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.intro_prompt_label),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            IconButton(
                                onClick = { copyToClipboard(promptProfile.introPrompt, context.getString(R.string.intro_prompt_label)) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.copy_intro_prompt),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        SelectionContainer {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = promptProfile.introPrompt,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                    
                    if (promptProfile.tonePrompt.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
        Text(
                                text = stringResource(R.string.tone_prompt_label),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            IconButton(
                                onClick = { copyToClipboard(promptProfile.tonePrompt, context.getString(R.string.tone_prompt_label)) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.copy_tone_prompt),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        SelectionContainer {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = promptProfile.tonePrompt,
            style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }

                    // 复制全部内容按钮
                    val allContent = buildString {
                        append(stringResource(R.string.config_name_format, promptProfile.name) + "\n\n")
                        if (promptProfile.introPrompt.isNotBlank()) {
                            append(stringResource(R.string.intro_prompt_label) + "\n${promptProfile.introPrompt}\n\n")
                        }
                        if (promptProfile.tonePrompt.isNotBlank()) {
                            append(stringResource(R.string.tone_prompt_label) + "\n${promptProfile.tonePrompt}")
                        }
                    }
                    
                    OutlinedButton(
                        onClick = { copyToClipboard(allContent, context.getString(R.string.old_config_details)) },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.copy_all_content), fontSize = 14.sp)
                    }
        
        Text(
                        text = stringResource(R.string.old_config_usage_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
    }
        }
    )
}
