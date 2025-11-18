package com.ai.assistance.operit.ui.features.settings.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.data.model.ChatHistory
import com.ai.assistance.operit.data.model.CharacterCard
import com.ai.assistance.operit.data.model.CharacterCardChatStats
import com.ai.assistance.operit.data.model.ImportStrategy
import com.ai.assistance.operit.data.model.PreferenceProfile
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.data.repository.MemoryRepository
import com.ai.assistance.operit.ui.features.settings.components.CharacterCardAssignDialog
import java.util.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Composable
fun ChatHistorySettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val chatHistoryManager = remember { ChatHistoryManager.getInstance(context) }
    val characterCardManager = remember { CharacterCardManager.getInstance(context) }
    val userPreferencesManager = remember { UserPreferencesManager(context) }
    val activeProfileId by userPreferencesManager.activeProfileIdFlow.collectAsState(initial = "default")

    val characterCardStatsState by chatHistoryManager.characterCardStatsFlow
        .collectAsState(initial = null as List<CharacterCardChatStats>?)
    val characterCardStats = characterCardStatsState ?: emptyList()
    val isCharacterCardStatsLoading = characterCardStatsState == null

    var availableCharacterCards by remember { mutableStateOf<List<CharacterCard>>(emptyList()) }
    var characterCardsLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        characterCardManager.characterCardListFlow.collectLatest { ids ->
            val cards = ids.mapNotNull { id ->
                runCatching { characterCardManager.getCharacterCard(id) }.getOrNull()
            }
            availableCharacterCards = cards
            if (characterCardsLoading) {
                characterCardsLoading = false
            }
        }
    }

    var totalChatCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        chatHistoryManager.chatHistoriesFlow.collect { chatHistories ->
            totalChatCount = chatHistories.size
        }
    }

    val profileIds by userPreferencesManager.profileListFlow.collectAsState(initial = listOf("default"))
    var allProfiles by remember { mutableStateOf<List<PreferenceProfile>>(emptyList()) }
    
    LaunchedEffect(profileIds) {
        val profiles = profileIds.mapNotNull { profileId ->
            try {
                userPreferencesManager.getUserPreferencesFlow(profileId).first()
            } catch (_: Exception) {
                null
            }
        }
        allProfiles = profiles
    }
    
    val activeProfileName =
        allProfiles.find { it.id == activeProfileId }?.name ?: "默认配置"

    var showAssignCharacterDialog by remember { mutableStateOf(false) }
    var pendingAssignStat by remember { mutableStateOf<CharacterCardChatStats?>(null) }
    var selectedCharacterCardId by remember { mutableStateOf<String?>(null) }
    var assignInProgress by remember { mutableStateOf(false) }

    val isScreenLoading = isCharacterCardStatsLoading || characterCardsLoading

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ChatManagementOverviewCard(
                    totalChatCount = totalChatCount,
                    activeProfileName = activeProfileName
                )
            }
            item {
                CharacterCardStatsCard(
                    stats = characterCardStats,
                    characterCards = availableCharacterCards,
                    isLoading = isCharacterCardStatsLoading || characterCardsLoading,
                    onAssignMissing = { stat ->
                        if (availableCharacterCards.isEmpty()) {
                            Toast.makeText(context, "暂无可用角色卡，请先创建一个角色卡", Toast.LENGTH_SHORT).show()
                            return@CharacterCardStatsCard
                        }
                        pendingAssignStat = stat
                        selectedCharacterCardId = availableCharacterCards.firstOrNull()?.id
                        showAssignCharacterDialog = true
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = isScreenLoading,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
        modifier = Modifier
            .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "正在加载数据，请稍候…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showAssignCharacterDialog && pendingAssignStat != null) {
        CharacterCardAssignDialog(
            missingChatCount = pendingAssignStat?.chatCount ?: 0,
            characterCards = availableCharacterCards,
            selectedCardId = selectedCharacterCardId,
            onCardSelected = { selectedCharacterCardId = it },
            onDismiss = {
                if (!assignInProgress) {
                    showAssignCharacterDialog = false
                    pendingAssignStat = null
                    selectedCharacterCardId = null
                }
            },
            onConfirm = {
                val stat = pendingAssignStat
                val targetCard = availableCharacterCards.firstOrNull { it.id == selectedCharacterCardId }

                if (assignInProgress) {
                    return@CharacterCardAssignDialog
                }

                if (stat == null) {
                    Toast.makeText(context, "未找到需要归类的聊天统计", Toast.LENGTH_SHORT).show()
                    showAssignCharacterDialog = false
                    return@CharacterCardAssignDialog
                }

                if (targetCard == null) {
                    Toast.makeText(context, "请选择一个角色卡", Toast.LENGTH_SHORT).show()
                    return@CharacterCardAssignDialog
                }

                assignInProgress = true
                scope.launch {
                    try {
                        chatHistoryManager.reassignChatsToCharacterCard(
                            sourceCharacterCardName = stat.characterCardName,
                            targetCharacterCardName = targetCard.name
                        )
                        Toast.makeText(
                            context,
                            "已归类到角色卡「${targetCard.name}」",
                            Toast.LENGTH_SHORT
                        ).show()
                        showAssignCharacterDialog = false
                        pendingAssignStat = null
                        selectedCharacterCardId = null
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "归类失败：${e.localizedMessage ?: e}",
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        assignInProgress = false
                    }
                }
            },
            inProgress = assignInProgress
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatManagementOverviewCard(
    totalChatCount: Int,
    activeProfileName: String
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                    text = "聊天记录概览",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "当前配置：$activeProfileName",
                style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatChip(
                icon = Icons.Default.History,
                title = "$totalChatCount",
                subtitle = "聊天记录"
            )
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CharacterCardStatsCard(
    stats: List<CharacterCardChatStats>,
    characterCards: List<CharacterCard>,
    isLoading: Boolean,
    onAssignMissing: (CharacterCardChatStats) -> Unit
) {
    val context = LocalContext.current
    val userPreferencesManager = remember { UserPreferencesManager(context) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = "角色卡统计",
                subtitle = "查看每个角色卡下的对话与消息数量",
                icon = Icons.Default.AssignmentInd
            )

            if (isLoading) {
                Column(
        modifier = Modifier
            .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "正在统计聊天数据…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (stats.isEmpty()) {
            Text(
                    text = "暂无聊天数据可供统计。",
                style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val sortedStats = remember(stats) {
                    stats.sortedWith(
                        compareByDescending<CharacterCardChatStats> { it.characterCardName.isNullOrBlank() }
                            .thenBy { it.characterCardName ?: "" }
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    sortedStats.forEach { stat ->
                        key(stat.characterCardName ?: "missing-${stat.hashCode()}") {
                            val matchedCard = characterCards.firstOrNull { card ->
                                card.name == stat.characterCardName
                            }
                            CharacterCardStatRow(
                                stat = stat,
                                characterCard = matchedCard,
                                userPreferencesManager = userPreferencesManager,
                                onAssignMissing = if (matchedCard == null) {
                                    { onAssignMissing(stat) }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCardStatRow(
    stat: CharacterCardChatStats,
    characterCard: CharacterCard?,
    userPreferencesManager: UserPreferencesManager,
    onAssignMissing: (() -> Unit)?
) {
    val isMissing = stat.characterCardName.isNullOrBlank()
    val needsAttention = characterCard == null
    val iconBackground = if (needsAttention) {
        MaterialTheme.colorScheme.errorContainer
                        } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val iconTint = if (needsAttention) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }

    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .let {
            if (needsAttention && onAssignMissing != null) {
                it.clickable { onAssignMissing() }
                } else {
                it
            }
        }
        .padding(12.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (characterCard != null) {
            val avatarUri by userPreferencesManager
                .getAiAvatarForCharacterCardFlow(characterCard.id)
                .collectAsState(initial = null)

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (!avatarUri.isNullOrBlank()) Color.Transparent
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUri.isNullOrBlank()) {
                    Image(
                        painter = rememberAsyncImagePainter(model = Uri.parse(avatarUri)),
                        contentDescription = "角色卡头像",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
            Text(
                        text = characterCard.name.firstOrNull()?.toString() ?: "角",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = iconBackground.copy(alpha = 0.6f)
            ) {
                Icon(
                    imageVector = Icons.Default.PriorityHigh,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
                    Text(
                text = stat.characterCardName ?: "未绑定角色卡",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
                    )
                    Text(
                text = "${stat.chatCount} 个对话 · ${stat.messageCount} 条消息",
                style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (needsAttention && onAssignMissing != null) {
                Text(
                    text = "点击归类到现有角色卡",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (needsAttention && onAssignMissing != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector
    ) {
        Row(
                modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ) {
        Icon(
            imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(10.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                style = MaterialTheme.typography.titleMedium
                )
                Text(
                text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

