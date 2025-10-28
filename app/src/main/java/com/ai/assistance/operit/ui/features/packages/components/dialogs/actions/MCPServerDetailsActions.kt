package com.ai.assistance.operit.ui.features.packages.components.dialogs.actions

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.mcp.MCPLocalServer

/**
 * Actions component for the MCP server details dialog.
 * 
 * @param server The MCP server to display actions for
 * @param isInstalled Whether the server is installed
 * @param onInstall Callback to be invoked when the install button is clicked
 * @param onUninstall Callback to be invoked when the uninstall button is clicked
 */
@Composable
fun MCPServerDetailsActions(
    server: MCPLocalServer.PluginMetadata,
    isInstalled: Boolean,
    onInstall: (MCPLocalServer.PluginMetadata) -> Unit,
    onUninstall: (MCPLocalServer.PluginMetadata) -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Repository link button (if available)
        if (server.repoUrl.isNotBlank()) {
            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(server.repoUrl))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("MCPServerDetailsDialog", "打开仓库链接失败", e)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null
                )
                Text(text = stringResource(R.string.repo))
            }
        }

        // Install/Uninstall button
        if (isInstalled) {
            Button(
                onClick = { onUninstall(server) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null
                )
                Text(text = stringResource(R.string.uninstall))
            }
        } else {
            Button(
                onClick = { onInstall(server) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null
                )
                Text(text = stringResource(R.string.install))
            }
        }
    }
} 