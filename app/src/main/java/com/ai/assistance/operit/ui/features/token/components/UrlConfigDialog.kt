package com.ai.assistance.operit.ui.features.token.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.ui.features.token.model.TabConfig
import com.ai.assistance.operit.ui.features.token.model.UrlConfig

@Composable
fun UrlConfigDialog(
    currentConfig: UrlConfig,
    onSave: (UrlConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentConfig.name) }
    var signInUrl by remember { mutableStateOf(currentConfig.signInUrl) }
    var tab1Title by remember { mutableStateOf(currentConfig.tabs.getOrNull(0)?.title ?: "") }
    var tab1Url by remember { mutableStateOf(currentConfig.tabs.getOrNull(0)?.url ?: "") }
    var tab2Title by remember { mutableStateOf(currentConfig.tabs.getOrNull(1)?.title ?: "") }
    var tab2Url by remember { mutableStateOf(currentConfig.tabs.getOrNull(1)?.url ?: "") }
    var tab3Title by remember { mutableStateOf(currentConfig.tabs.getOrNull(2)?.title ?: "") }
    var tab3Url by remember { mutableStateOf(currentConfig.tabs.getOrNull(2)?.url ?: "") }
    var tab4Title by remember { mutableStateOf(currentConfig.tabs.getOrNull(3)?.title ?: "") }
    var tab4Url by remember { mutableStateOf(currentConfig.tabs.getOrNull(3)?.url ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("自定义URL配置")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("配置名称") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = signInUrl,
                    onValueChange = { signInUrl = it },
                    label = { Text("登录页面URL") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Tab配置",
                    style = MaterialTheme.typography.titleMedium
                )

                // Tab 1
                Text("Tab 1", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = tab1Title,
                    onValueChange = { tab1Title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tab1Url,
                    onValueChange = { tab1Url = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Tab 2
                Text("Tab 2", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = tab2Title,
                    onValueChange = { tab2Title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tab2Url,
                    onValueChange = { tab2Url = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Tab 3
                Text("Tab 3", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = tab3Title,
                    onValueChange = { tab3Title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tab3Url,
                    onValueChange = { tab3Url = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Tab 4
                Text("Tab 4", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = tab4Title,
                    onValueChange = { tab4Title = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tab4Url,
                    onValueChange = { tab4Url = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newConfig = UrlConfig(
                        name = name,
                        signInUrl = signInUrl,
                        tabs = listOf(
                            TabConfig(tab1Title, tab1Url),
                            TabConfig(tab2Title, tab2Url),
                            TabConfig(tab3Title, tab3Url),
                            TabConfig(tab4Title, tab4Url)
                        )
                    )
                    onSave(newConfig)
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
} 