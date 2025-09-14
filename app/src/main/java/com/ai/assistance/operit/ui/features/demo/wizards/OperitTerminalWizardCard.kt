package com.ai.assistance.operit.ui.features.demo.wizards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.util.GithubReleaseUtil

@Composable
fun OperitTerminalWizardCard(
    isInstalled: Boolean,
    installedVersion: String?,
    latestVersion: String?,
    releaseNotes: String?,
    updateNeeded: Boolean,
    showWizard: Boolean,
    downloadUrl: String? = null,
    onToggleWizard: (Boolean) -> Unit,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onOpen: () -> Unit,
    onDownloadFromUrl: (String) -> Unit = {}
) {
    var showDownloadSourceMenu by remember { mutableStateOf(false) }
    
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.operit_terminal_wizard_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                TextButton(
                    onClick = { onToggleWizard(!showWizard) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (showWizard) stringResource(R.string.wizard_collapse)
                        else stringResource(R.string.wizard_expand),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            
            // 状态信息
            val statusText = when {
                !isInstalled -> stringResource(R.string.operit_terminal_not_installed)
                updateNeeded -> stringResource(R.string.operit_terminal_update_available)
                else -> stringResource(R.string.operit_terminal_up_to_date)
            }
            
            val statusColor = when {
                 updateNeeded -> MaterialTheme.colorScheme.primary
                 isInstalled -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isInstalled && !updateNeeded) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = statusColor
                )
            }
            

            // 详细设置内容，仅在展开时显示
            AnimatedVisibility(visible = showWizard) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    when {
                        // 未安装
                        !isInstalled -> {
                            Text(
                                stringResource(R.string.operit_terminal_install_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { 
                                    if (downloadUrl != null) {
                                        showDownloadSourceMenu = true
                                    } else {
                                        onInstall()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.operit_terminal_install_button), fontSize = 14.sp)
                            }
                        }

                        // 需要更新
                        updateNeeded -> {
                            Text(
                                stringResource(R.string.operit_terminal_update_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            if (!releaseNotes.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                // Tip: Omit long text with ellipsis
                                Text(
                                    text = stringResource(R.string.operit_terminal_release_notes, releaseNotes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { 
                                    if (downloadUrl != null) {
                                        showDownloadSourceMenu = true
                                    } else {
                                        onUpdate()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.operit_terminal_update_button), fontSize = 14.sp)
                            }
                        }

                        // 已是最新
                        else -> {
                             Text(
                                stringResource(R.string.operit_terminal_latest_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = onOpen,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Text(stringResource(R.string.operit_terminal_open_button), fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    
    // 下载源选择对话框
    if (showDownloadSourceMenu && downloadUrl != null) {
        val mirroredUrls = remember(downloadUrl) {
            GithubReleaseUtil.getMirroredUrls(downloadUrl)
        }

        AlertDialog(
            onDismissRequest = { showDownloadSourceMenu = false },
            title = { Text(stringResource(id = R.string.select_download_source)) },
            text = {
                Column {
                    Text(
                        stringResource(id = R.string.select_download_source_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Use a scrollable column in case of many mirrors
                    val scrollState = rememberScrollState()
                    Column(Modifier.verticalScroll(scrollState)) {
                        if (mirroredUrls.isEmpty()) {
                            Text(
                                stringResource(id = R.string.no_mirrors_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            // Dynamically generate mirror download options
                            mirroredUrls.forEach { (name, url) ->
                                DownloadSourceRow(
                                    title = stringResource(id = R.string.mirror_download, name),
                                    description = stringResource(id = R.string.china_mirror_desc),
                                    icon = Icons.Default.Storage,
                                    onClick = {
                                        onDownloadFromUrl(url)
                                        showDownloadSourceMenu = false
                                    }
                                )
                            }
                        }
                        
                        // GitHub original link option
                        DownloadSourceRow(
                            title = stringResource(id = R.string.github_source),
                            description = stringResource(id = R.string.github_source_desc),
                            icon = Icons.Default.Language,
                            onClick = {
                                onDownloadFromUrl(downloadUrl)
                                showDownloadSourceMenu = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDownloadSourceMenu = false }) {
                    Text(stringResource(id = R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun DownloadSourceRow(
   title: String,
   description: String,
   icon: ImageVector,
   onClick: () -> Unit
) {
   Row(
       modifier = Modifier
           .fillMaxWidth()
           .clickable(onClick = onClick)
           .padding(vertical = 12.dp),
       verticalAlignment = Alignment.CenterVertically,
       horizontalArrangement = Arrangement.spacedBy(16.dp)
   ) {
       Icon(
           imageVector = icon,
           contentDescription = null,
           tint = MaterialTheme.colorScheme.primary,
           modifier = Modifier.size(24.dp)
       )
       Column(modifier = Modifier.weight(1f)) {
           Text(
               text = title,
               style = MaterialTheme.typography.bodyLarge,
               color = MaterialTheme.colorScheme.onSurface
           )
           Text(
               text = description,
               style = MaterialTheme.typography.bodySmall,
               color = MaterialTheme.colorScheme.onSurfaceVariant
           )
       }
   }
} 