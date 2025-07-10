package com.ai.assistance.operit.ui.features.assistant.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dragonbones.DragonBonesModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.components.ManagedDragonBonesView
import com.ai.assistance.operit.ui.features.assistant.viewmodel.AssistantConfigViewModel
import java.io.File
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DragonBonesPreviewSection(
        modifier: Modifier = Modifier,
        controller: com.dragonbones.DragonBonesController,
        uiState: AssistantConfigViewModel.UiState,
        onDeleteCurrentModel: (() -> Unit)? = null
) {
        var showDeleteDialog by remember { mutableStateOf(false) }
        Surface(
                modifier = modifier,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border =
                        BorderStroke(
                                width = 1.dp,
                                brush =
                                        Brush.verticalGradient(
                                                colors =
                                                        listOf(
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.5f),
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.2f)
                                                        )
                                        )
                        )
        ) {
                Box(modifier = Modifier.fillMaxSize()) {
                        val currentModel = uiState.currentModel
                        if (currentModel != null) {
                                val model =
                                        remember(currentModel) {
                                                DragonBonesModel(
                                                        skeletonPath =
                                                                File(
                                                                                currentModel
                                                                                        .folderPath,
                                                                                currentModel
                                                                                        .skeletonFile
                                                                        )
                                                                        .absolutePath,
                                                        textureJsonPath =
                                                                File(
                                                                                currentModel
                                                                                        .folderPath,
                                                                                currentModel
                                                                                        .textureJsonFile
                                                                        )
                                                                        .absolutePath,
                                                        textureImagePath =
                                                                File(
                                                                                currentModel
                                                                                        .folderPath,
                                                                                currentModel
                                                                                        .textureImageFile
                                                                        )
                                                                        .absolutePath
                                                )
                                        }

                                ManagedDragonBonesView(
                                        modifier = Modifier.fillMaxSize(),
                                        model = model,
                                        controller = controller,
                                        onError = { error -> println("DragonBones error: $error") }
                                )
                        } else {
                                Text(
                                        text =
                                                if (uiState.models.isEmpty()) stringResource(R.string.no_models_available)
                                                else stringResource(R.string.please_select_model),
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.align(Alignment.Center)
                                )
                        }

                        // 动画控制区域
                        if (controller.animationNames.isNotEmpty()) {
                                var selectedAnim by remember { mutableStateOf<String?>(null) }

                                // A quick effect to de-select the chip after a short time
                                LaunchedEffect(selectedAnim) {
                                        if (selectedAnim != null) {
                                                delay(500) // Keep it highlighted for half a second
                                                selectedAnim = null
                                        }
                                }

                                FlowRow(
                                        modifier =
                                                Modifier.align(Alignment.BottomCenter)
                                                        .padding(8.dp)
                                                        .background(
                                                                MaterialTheme.colorScheme.surface
                                                                        .copy(alpha = 0.3f),
                                                                RoundedCornerShape(16.dp)
                                                        )
                                                        .padding(
                                                                horizontal = 8.dp,
                                                                vertical = 4.dp
                                                        ),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalArrangement = Arrangement.Center,
                                        maxItemsInEachRow = 4
                                ) {
                                        controller.animationNames.forEach { name ->
                                                FilterChip(
                                                        modifier =
                                                                Modifier.padding(horizontal = 4.dp),
                                                        selected = selectedAnim == name,
                                                        onClick = {
                                                                selectedAnim = name
                                                                // Use a safe, higher layer for
                                                                // manual playback.
                                                                controller.fadeInAnimation(
                                                                        name,
                                                                        layer = 3,
                                                                        loop = 1
                                                                )
                                                        },
                                                        label = { Text(name) }
                                                )
                                        }
                                }
                        }
                }
        }
}
