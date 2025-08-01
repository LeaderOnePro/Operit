package com.ai.assistance.operit.ui.features.chat.webview.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.ui.features.chat.viewmodel.ChatViewModel
import java.io.File

/**
 * 主工作区屏幕组件
 * 根据聊天状态显示不同的工作区界面
 */
@Composable
fun WorkspaceScreen(
    actualViewModel: ChatViewModel,
    currentChat: ChatHistory?,
    isVisible: Boolean,
    onExportClick: (workDir: File) -> Unit
) {
    if (currentChat?.workspace != null) {
        WorkspaceManager(
            actualViewModel = actualViewModel,
            currentChat = currentChat,
            workspacePath = currentChat.workspace,
            isVisible = isVisible,
            onExportClick = onExportClick
        )
    } else if (currentChat != null) {
        WorkspaceSetup(
            chatId = currentChat.id,
            onBindWorkspace = { workspacePath ->
                actualViewModel.bindChatToWorkspace(currentChat.id, workspacePath)
            }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "请先选择或创建一个对话", style = MaterialTheme.typography.headlineMedium)
        }
    }
}