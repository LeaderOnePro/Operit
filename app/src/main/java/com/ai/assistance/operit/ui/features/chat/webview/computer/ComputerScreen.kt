package com.ai.assistance.operit.ui.features.chat.webview.computer

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.terminal.TerminalManager
import com.ai.assistance.operit.terminal.rememberTerminalEnv
import com.ai.assistance.operit.terminal.view.TerminalScreen

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ComputerScreen() {
    val context = LocalContext.current
    
    // Create a TerminalManager and TerminalEnv instance for the terminal
    val terminalManager = remember { TerminalManager.getInstance(context) }
    val terminalEnv = rememberTerminalEnv(terminalManager)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { }
    ) {
        // Show the terminal interface instead of the web desktop
        TerminalScreen(env = terminalEnv)
    }
} 