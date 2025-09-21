package com.ai.assistance.operit.ui.features.chat.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.ChatMessage

import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.InputChip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.res.stringResource
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.chat.components.MessageEditor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenContent(
        modifier: Modifier = Modifier,
        paddingValues: PaddingValues,
        actualViewModel: ChatViewModel,
        showChatHistorySelector: Boolean,
        chatHistory: List<ChatMessage>,
        enableAiPlanning: Boolean,
        isLoading: Boolean,
        userMessageColor: Color,
        aiMessageColor: Color,
        userTextColor: Color,
        aiTextColor: Color,
        systemMessageColor: Color,
        systemTextColor: Color,
        thinkingBackgroundColor: Color,
        thinkingTextColor: Color,
        hasBackgroundImage: Boolean,
        editingMessageIndex: MutableState<Int?>,
        editingMessageContent: MutableState<String>,
        chatScreenGestureConsumed: Boolean,
        onChatScreenGestureConsumed: (Boolean) -> Unit,
        currentDrag: Float,
        onCurrentDragChange: (Float) -> Unit,
        verticalDrag: Float,
        onVerticalDragChange: (Float) -> Unit,
        dragThreshold: Float,
        scrollState: ScrollState,
        showScrollButton: Boolean,
        onShowScrollButtonChange: (Boolean) -> Unit,
        autoScrollToBottom: Boolean,
        onAutoScrollToBottomChange: (Boolean) -> Unit,
        coroutineScope: CoroutineScope,
        chatHistories: List<ChatHistory>,
        currentChatId: String,
        chatHeaderTransparent: Boolean,
        chatHeaderHistoryIconColor: Int?,
        chatHeaderPipIconColor: Int?,
        chatHeaderOverlayMode: Boolean,
        chatStyle: ChatStyle, // Add chatStyle parameter
        onSwitchCharacter: (String) -> Unit
) {
    val density = LocalDensity.current
    var headerHeight by remember { mutableStateOf(0.dp) }
    var showCharacterSelector by remember { mutableStateOf(false) }

    // 获取WebView状态
    val showWebView = actualViewModel.showWebView.collectAsState().value
    val currentChat = chatHistories.find { it.id == currentChatId }

    // 导出相关状态
    val context = LocalContext.current
    var showExportPlatformDialog by remember { mutableStateOf(false) }
    var showAndroidExportDialog by remember { mutableStateOf(false) }
    var showWindowsExportDialog by remember { mutableStateOf(false) }
    var showExportProgressDialog by remember { mutableStateOf(false) }
    var showExportCompleteDialog by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var exportStatus by remember { mutableStateOf("") }
    var exportSuccess by remember { mutableStateOf(false) }
    var exportFilePath by remember { mutableStateOf<String?>(null) }
    var exportErrorMessage by remember { mutableStateOf<String?>(null) }
    var webContentDir by remember { mutableStateOf<File?>(null) }
    var editingMessageType by remember { mutableStateOf<String?>(null) }
    
    // 监听朗读状态
    val isPlaying by actualViewModel.isPlaying.collectAsState()
    val isAutoReadEnabled by actualViewModel.isAutoReadEnabled.collectAsState()

    val onSelectMessageToEditCallback = remember(editingMessageIndex, editingMessageContent, editingMessageType) {
        { index: Int, message: ChatMessage, senderType: String ->
            editingMessageIndex.value = index
            editingMessageContent.value = message.content
            editingMessageType = senderType
        }
    }

    Box(modifier = modifier.fillMaxSize().padding(paddingValues)) {
        if (chatHeaderOverlayMode && chatHeaderTransparent) {
            // 覆盖模式：Header浮动在ChatArea之上
            Box(modifier = Modifier.fillMaxSize()) {
                ChatArea(
                        chatHistory = chatHistory,
                        scrollState = scrollState,
                        isLoading = isLoading,
                        userMessageColor = userMessageColor,
                        aiMessageColor = aiMessageColor,
                        userTextColor = userTextColor,
                        aiTextColor = aiTextColor,
                        systemMessageColor = systemMessageColor,
                        systemTextColor = systemTextColor,
                        thinkingBackgroundColor = thinkingBackgroundColor,
                        thinkingTextColor = thinkingTextColor,
                        hasBackgroundImage = hasBackgroundImage,
                        modifier = Modifier.fillMaxSize(),
                        onSelectMessageToEdit = onSelectMessageToEditCallback,
                        onDeleteMessage = { index -> actualViewModel.deleteMessage(index) },
                        onDeleteMessagesFrom = { index -> actualViewModel.deleteMessagesFrom(index) },
                        onSpeakMessage = { content -> actualViewModel.speakMessage(content) }, // 添加朗读回调
                        onAutoReadMessage = { content -> actualViewModel.enableAutoReadAndSpeak(content) }, // 添加自动朗读回调
                        onReplyToMessage = { message -> actualViewModel.setReplyToMessage(message) }, // 添加回复回调
                        topPadding = headerHeight,
                        chatStyle = chatStyle // Pass chat style
                )
                ChatScreenHeader(
                        modifier =
                                Modifier.onGloballyPositioned { coordinates ->
                                    headerHeight = with(density) { coordinates.size.height.toDp() }
                                },
                        actualViewModel = actualViewModel,
                        showChatHistorySelector = showChatHistorySelector,
                        chatHistories = chatHistories,
                        currentChatId = currentChatId,
                        chatHeaderTransparent = chatHeaderTransparent,
                        chatHeaderHistoryIconColor = chatHeaderHistoryIconColor,
                        chatHeaderPipIconColor = chatHeaderPipIconColor,
                        onCharacterSwitcherClick = { showCharacterSelector = true }
                )
            }
        } else {
            // 正常模式：Header和ChatArea在Column中顺序排列
            Column(modifier = Modifier.fillMaxSize()) {
                ChatScreenHeader(
                        actualViewModel = actualViewModel,
                        showChatHistorySelector = showChatHistorySelector,
                        chatHistories = chatHistories,
                        currentChatId = currentChatId,
                        chatHeaderTransparent = chatHeaderTransparent,
                        chatHeaderHistoryIconColor = chatHeaderHistoryIconColor,
                        chatHeaderPipIconColor = chatHeaderPipIconColor,
                        onCharacterSwitcherClick = { showCharacterSelector = true }
                )
                ChatArea(
                        chatHistory = chatHistory,
                        scrollState = scrollState,
                        isLoading = isLoading,
                        userMessageColor = userMessageColor,
                        aiMessageColor = aiMessageColor,
                        userTextColor = userTextColor,
                        aiTextColor = aiTextColor,
                        systemMessageColor = systemMessageColor,
                        systemTextColor = systemTextColor,
                        thinkingBackgroundColor = thinkingBackgroundColor,
                        thinkingTextColor = thinkingTextColor,
                        hasBackgroundImage = hasBackgroundImage,
                        modifier = Modifier.fillMaxSize(),
                        onSelectMessageToEdit = onSelectMessageToEditCallback,
                        onDeleteMessage = { index -> actualViewModel.deleteMessage(index) },
                        onDeleteMessagesFrom = { index -> actualViewModel.deleteMessagesFrom(index) },
                        onSpeakMessage = { content -> actualViewModel.speakMessage(content) }, // 添加朗读回调
                        onReplyToMessage = { message -> actualViewModel.setReplyToMessage(message) }, // 添加回复回调
                        onAutoReadMessage = { content -> actualViewModel.enableAutoReadAndSpeak(content) }, // 添加自动朗读回调
                        chatStyle = chatStyle // Pass chat style
                )
            }
        }

        // 停止朗读按钮
        AnimatedVisibility(
            visible = isPlaying || isAutoReadEnabled,
            enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
            exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 80.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    if (isAutoReadEnabled) {
                        actualViewModel.disableAutoRead()
                    } else {
                        actualViewModel.stopSpeaking()
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = stringResource(R.string.stop_reading),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 角色选择器
        CharacterSelectorPanel(
            isVisible = showCharacterSelector,
            onDismiss = { showCharacterSelector = false },
            onSelectCharacter = onSwitchCharacter
        )

        // 历史选择器作为浮动层，使用AnimatedVisibility保持动画效果
        AnimatedVisibility(
                visible = showChatHistorySelector,
                enter =
                        slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) +
                                fadeIn(animationSpec = tween(300)),
                exit =
                        slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) +
                                fadeOut(animationSpec = tween(300)),
                modifier = Modifier.align(Alignment.TopStart)
        ) {
            ChatHistorySelectorPanel(
                    actualViewModel = actualViewModel,
                    chatHistories = chatHistories,
                    currentChatId = currentChatId,
                    showChatHistorySelector = showChatHistorySelector
            )
        }

        // 滚动到底部按钮
        AnimatedVisibility(
            visible = showScrollButton,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            IconButton(
                onClick = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                    onAutoScrollToBottomChange(true) // 重新启用自动滚动
                    onShowScrollButtonChange(false) // 点击后隐藏按钮
                },
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(50)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Scroll to bottom",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }


        // 导出平台选择对话框
        if (showExportPlatformDialog) {
            ExportPlatformDialog(
                    onDismiss = { showExportPlatformDialog = false },
                    onSelectAndroid = { showAndroidExportDialog = true },
                    onSelectWindows = { showWindowsExportDialog = true }
            )
        }

        // Android导出设置对话框
        if (showAndroidExportDialog && webContentDir != null) {
            AndroidExportDialog(
                    workDir = webContentDir!!,
                    onDismiss = { showAndroidExportDialog = false },
                    onExport = { packageName, appName, iconUri ->
                        showAndroidExportDialog = false
                        showExportProgressDialog = true
                        exportProgress = 0f
                        exportStatus = "开始导出..."

                        // 启动导出过程
                        coroutineScope.launch {
                            exportAndroidApp(
                                    context = context,
                                    packageName = packageName,
                                    appName = appName,
                                    iconUri = iconUri,
                                    webContentDir = webContentDir!!,
                                    onProgress = { progress, status ->
                                        exportProgress = progress
                                        exportStatus = status
                                    },
                                    onComplete = { success, filePath, errorMessage ->
                                        showExportProgressDialog = false
                                        exportSuccess = success
                                        exportFilePath = filePath
                                        exportErrorMessage = errorMessage
                                        showExportCompleteDialog = true
                                    }
                            )
                        }
                    }
            )
        }

        // Windows导出设置对话框
        if (showWindowsExportDialog && webContentDir != null) {
            WindowsExportDialog(
                    workDir = webContentDir!!,
                    onDismiss = { showWindowsExportDialog = false },
                    onExport = { appName, iconUri ->
                        showWindowsExportDialog = false
                        showExportProgressDialog = true
                        exportProgress = 0f
                        exportStatus = "开始导出..."

                        // 启动导出过程
                        coroutineScope.launch {
                            exportWindowsApp(
                                    context = context,
                                    appName = appName,
                                    iconUri = iconUri,
                                    webContentDir = webContentDir!!,
                                    onProgress = { progress, status ->
                                        exportProgress = progress
                                        exportStatus = status
                                    },
                                    onComplete = { success, filePath, errorMessage ->
                                        showExportProgressDialog = false
                                        exportSuccess = success
                                        exportFilePath = filePath
                                        exportErrorMessage = errorMessage
                                        showExportCompleteDialog = true
                                    }
                            )
                        }
                    }
            )
        }

        // 导出进度对话框
        if (showExportProgressDialog) {
            ExportProgressDialog(
                    progress = exportProgress,
                    status = exportStatus,
                    onCancel = { showExportProgressDialog = false }
            )
        }

        // 导出完成对话框
        if (showExportCompleteDialog) {
            ExportCompleteDialog(
                    success = exportSuccess,
                    filePath = exportFilePath,
                    errorMessage = exportErrorMessage,
                    onDismiss = { showExportCompleteDialog = false },
                    onOpenFile = { filePath ->
                        try {
                            val file = File(filePath)
                            val fileUri =
                                    FileProvider.getUriForFile(
                                            context,
                                            context.applicationContext.packageName +
                                                    ".fileprovider",
                                            file
                                    )
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setDataAndType(
                                    fileUri,
                                    "application/vnd.android.package-archive"
                            )
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Log.e("ChatScreenContent", "无法打开文件: $filePath", e)
                        } catch (e: Exception) {
                            Log.e("ChatScreenContent", "文件操作错误: ${e.message}", e)
                        }
                    }
            )
        }

        // 当需要编辑消息时，显示消息编辑器
        if (editingMessageIndex.value != null) {
            MessageEditor(
                editingMessageContent = editingMessageContent,
                onCancel = {
                    editingMessageIndex.value = null
                    editingMessageContent.value = ""
                },
                onSave = {
                    val index = editingMessageIndex.value
                    if (index != null) {
                        val editedMessage =
                            chatHistory[index].copy(
                                content = editingMessageContent.value,
                                contentStream = null // 修复：清除stream，强制UI使用content
                            )
                        actualViewModel.updateMessage(index, editedMessage)
                    }
                    editingMessageIndex.value = null
                    editingMessageContent.value = ""
                },
                onResend = {
                    val index = editingMessageIndex.value
                    if (index != null) {
                        actualViewModel.rewindAndResendMessage(index, editingMessageContent.value)
                    }
                    editingMessageIndex.value = null
                    editingMessageContent.value = ""
                },
                showResendButton = editingMessageType == "user"
            )
        }
    }
}

@Composable
fun ChatHistorySelectorPanel(
        actualViewModel: ChatViewModel,
        chatHistories: List<ChatHistory>,
        currentChatId: String,
        showChatHistorySelector: Boolean
) {
    // 添加一个覆盖整个屏幕的半透明点击区域，用于关闭历史选择器
    Box(modifier = Modifier.fillMaxSize()) {
        // 透明遮罩层，点击右侧空白处关闭历史选择器 - 修改为不拦截滑动事件
        Box(
                modifier =
                        Modifier.fillMaxSize()
                                // 使用pointerInput替代clickable，以便只处理点击事件而不拦截滑动
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        actualViewModel.toggleChatHistorySelector()
                                    }
                                }
                                .background(Color.Black.copy(alpha = 0.1f))
        )

        // 历史选择器面板
        Box(
                modifier =
                        Modifier.width(280.dp)
                                .fillMaxHeight()
                                .background(
                                        color =
                                                MaterialTheme.colorScheme.surface.copy(
                                                        alpha = 0.95f
                                                ),
                                        shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                                )
        ) {
            // 直接使用ChatHistorySelector
            ChatHistorySelector(
                    modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                    onNewChat = {
                        actualViewModel.createNewChat()
                        // 创建新对话后自动收起侧边框
                        actualViewModel.showChatHistorySelector(false)
                    },
                    onSelectChat = { chatId ->
                        actualViewModel.switchChat(chatId)
                        // 切换聊天后也自动收起侧边框
                        actualViewModel.showChatHistorySelector(false)
                    },
                    onDeleteChat = { chatId -> actualViewModel.deleteChatHistory(chatId) },
                    onUpdateChatTitle = { chatId, newTitle ->
                        actualViewModel.updateChatTitle(chatId, newTitle)
                    },
                    onCreateGroup = { groupName -> actualViewModel.createGroup(groupName) },
                    onUpdateChatOrderAndGroup = { reorderedHistories, movedItem, targetGroup ->
                        actualViewModel.updateChatOrderAndGroup(
                                reorderedHistories,
                                movedItem,
                                targetGroup
                        )
                    },
                    onUpdateGroupName = { oldName, newName ->
                        actualViewModel.updateGroupName(oldName, newName)
                    },
                    onDeleteGroup = { groupName, deleteChats ->
                        actualViewModel.deleteGroup(groupName, deleteChats)
                    },
                    chatHistories = chatHistories,
                    currentId = currentChatId
            )

            // 在右侧添加浮动返回按钮
            OutlinedButton(
                    onClick = { actualViewModel.toggleChatHistorySelector() },
                    modifier =
                            Modifier.align(Alignment.TopEnd)
                                    .padding(top = 16.dp, end = 8.dp)
                                    .height(28.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors =
                            ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                            ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp),
                    shape = RoundedCornerShape(4.dp)
            ) {
                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                    )
                    Text("返回", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
