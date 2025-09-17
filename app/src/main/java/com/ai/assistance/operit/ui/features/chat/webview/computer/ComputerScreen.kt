package com.ai.assistance.operit.ui.features.chat.webview.computer

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    
    // Show the terminal interface instead of the web desktop
    TerminalScreen(env = terminalEnv)
} 