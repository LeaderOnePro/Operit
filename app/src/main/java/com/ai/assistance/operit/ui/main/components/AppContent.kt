package com.ai.assistance.operit.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.repository.ChatHistoryManager
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.common.displays.FpsCounter
import com.ai.assistance.operit.ui.main.screens.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.RowScope

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun AppContent(
        currentScreen: Screen,
        selectedItem: NavItem,
        useTabletLayout: Boolean,
        isTabletSidebarExpanded: Boolean,
        isLoading: Boolean,
        navController: NavController,
        scope: CoroutineScope,
        drawerState: androidx.compose.material3.DrawerState,
        showFpsCounter: Boolean,
        onScreenChange: (Screen) -> Unit,
        onNavItemChange: (NavItem) -> Unit,
        onToggleSidebar: () -> Unit,
        navigateToTokenConfig: () -> Unit,
        onLoading: (Boolean) -> Unit = {},
        onError: (String) -> Unit = {},
        onGestureConsumed: (Boolean) -> Unit = {},
        canGoBack: Boolean,
        onGoBack: () -> Unit,
        isNavigatingBack: Boolean = false,
        actions: @Composable RowScope.() -> Unit = {}
) {
    // Get background image state
    val context = LocalContext.current
    val preferencesManager = UserPreferencesManager(context)
    val useBackgroundImage =
            preferencesManager.useBackgroundImage.collectAsState(initial = false).value
    val backgroundImageUri =
            preferencesManager.backgroundImageUri.collectAsState(initial = null).value
    val hasBackgroundImage = useBackgroundImage && backgroundImageUri != null

    // 获取聊天历史管理器
    val chatHistoryManager = ChatHistoryManager.getInstance(context)
    val currentChatId = chatHistoryManager.currentChatIdFlow.collectAsState(initial = null).value
    val chatHistories =
            chatHistoryManager.chatHistoriesFlow.collectAsState(initial = emptyList()).value

    // 当前聊天标题
    val currentChatTitle =
            if (currentChatId != null) {
                chatHistories.find { it.id == currentChatId }?.title ?: ""
            } else {
                ""
            }

    // 内容区域仅包含顶部应用栏和主内容，不再使用Scaffold
    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .background(Color.Transparent) // Explicitly set transparent background
    ) {
        // 单一工具栏 - 使用小型化的设计
        SmallTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 使用Screen的标题或导航项的标题
                        Text(
                                text =
                                        when {
                                            // 优先使用Screen的标题
                                            currentScreen.getTitle().isNotBlank() ->
                                                    currentScreen.getTitle()
                                            // 回退到导航项的标题资源
                                            selectedItem.titleResId != 0 ->
                                                    stringResource(id = selectedItem.titleResId)
                                            // 最后的默认值
                                            else -> ""
                                        },
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                        )

                        // 显示当前聊天标题（仅在AI对话页面)
                        if (currentScreen is Screen.AiChat && currentChatTitle.isNotBlank()) {
                            Text(
                                    text = "- $currentChatTitle",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    // 导航按钮逻辑
                    IconButton(
                            onClick = {
                                if (canGoBack) {
                                    onGoBack()
                                } else {
                                    // 平板模式下切换侧边栏展开/收起状态
                                    if (useTabletLayout) {
                                        onToggleSidebar()
                                    } else {
                                        // 手机模式下打开抽屉
                                        scope.launch { drawerState.open() }
                                    }
                                }
                            }
                    ) {
                        Icon(
                                if (canGoBack) Icons.Default.ArrowBack
                                else if (useTabletLayout)
                                // 平板模式下使用开关图标表示收起/展开
                                if (isTabletSidebarExpanded) Icons.Filled.ChevronLeft
                                        else Icons.Default.Menu
                                else Icons.Default.Menu,
                                contentDescription =
                                        when {
                                            canGoBack -> "返回"
                                            useTabletLayout ->
                                                    if (isTabletSidebarExpanded) "收起侧边栏"
                                                    else "展开侧边栏"
                                            else -> stringResource(id = R.string.menu)
                                        },
                                tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                actions = actions,
                colors =
                        TopAppBarDefaults.smallTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                        )
        )

        // 主内容区域
        Surface(
                modifier = Modifier.fillMaxSize(),
                color =
                        if (hasBackgroundImage) Color.Transparent
                        else MaterialTheme.colorScheme.background
        ) {
            if (isLoading) {
                // 加载中状态
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                                modifier = Modifier.size(48.dp),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                            ) {
                                Text(
                                        text = "...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                                text = "正在加载...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                // 主要内容 - 使用Screen的Content方法直接渲染
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        // 根据导航方向决定动画方向
                        if (isNavigatingBack) {
                            // 返回操作：新屏幕从左侧滑入，旧屏幕向右侧滑出
                            slideInHorizontally { width -> -width } + fadeIn() with
                                    slideOutHorizontally { width -> width } + fadeOut()
                        } else {
                            // 前进操作：新屏幕从右侧滑入，旧屏幕向左侧滑出
                            slideInHorizontally { width -> width } + fadeIn() with
                                    slideOutHorizontally { width -> -width } + fadeOut()
                        }
                    },
                    label = "ScreenAnimation"
                ) { screen ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 统一调用当前屏幕的内容渲染方法
                        screen.Content(
                                navController = navController,
                                navigateTo = onScreenChange,
                                updateNavItem = onNavItemChange,
                                onGoBack = onGoBack,
                                hasBackgroundImage = hasBackgroundImage,
                                onLoading = onLoading,
                                onError = onError,
                                onGestureConsumed =
                                if (screen is Screen.AiChat) onGestureConsumed
                                else { _ -> },
                        )

                        // 帧率计数器 - 放在右上角
                        if (showFpsCounter) {
                            FpsCounter(
                                    modifier =
                                    Modifier.align(Alignment.TopEnd)
                                            .padding(top = 80.dp, end = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
