package com.ai.assistance.operit.ui.features.toolbox.screens.speechtotext

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

/** 语音识别工具屏幕包装器 用于在路由系统中显示SpeechToTextScreen */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechToTextToolScreen(navController: NavController) {
    Scaffold { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            SpeechToTextScreen(navController = navController)
        }
    }
} 