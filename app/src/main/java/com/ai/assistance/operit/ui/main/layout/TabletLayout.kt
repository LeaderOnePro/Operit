package com.ai.assistance.operit.ui.main.layout

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ai.assistance.operit.ui.common.NavItem
import com.ai.assistance.operit.ui.main.NavGroup
import com.ai.assistance.operit.ui.main.components.AppContent
import com.ai.assistance.operit.ui.main.components.CollapsedDrawerContent
import com.ai.assistance.operit.ui.main.components.DrawerContent
import com.ai.assistance.operit.ui.main.screens.Screen
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.layout.RowScope

/** Layout for tablet devices with a permanent side navigation drawer */
@Composable
fun TabletLayout(
        currentScreen: Screen,
        selectedItem: NavItem,
        isTabletSidebarExpanded: Boolean,
        isLoading: Boolean,
        navGroups: List<NavGroup>,
        navItems: List<NavItem>,
        isNetworkAvailable: Boolean,
        networkType: String,
        navController: androidx.navigation.NavController,
        scope: CoroutineScope,
        drawerState: androidx.compose.material3.DrawerState,
        showFpsCounter: Boolean,
        tabletSidebarWidth: androidx.compose.ui.unit.Dp,
        collapsedTabletSidebarWidth: androidx.compose.ui.unit.Dp,
        onScreenChange: (Screen) -> Unit,
        onNavItemChange: (NavItem) -> Unit,
        onToggleSidebar: () -> Unit,
        navigateToTokenConfig: () -> Unit,
        canGoBack: Boolean,
        onGoBack: () -> Unit,
        isNavigatingBack: Boolean = false,
        topBarActions: @Composable RowScope.() -> Unit = {}
) {
        // 计算侧边栏的动画宽度，轻微调整动画时间为280ms，保持原有效果但稍快
        val animatedSidebarWidth by
                animateDpAsState(
                        targetValue =
                                if (isTabletSidebarExpanded) tabletSidebarWidth
                                else collapsedTabletSidebarWidth,
                        animationSpec = tween(durationMillis = 280),
                        label = "sidebarWidth"
                )

        // 使用Box作为顶层容器，这样可以允许子元素重叠
        Box(modifier = Modifier.fillMaxSize()) {
                // 计算主内容区域的宽度（屏幕宽度减去侧边栏宽度），轻微调整动画时间
                val contentWidth by
                        animateDpAsState(
                                targetValue =
                                        if (isTabletSidebarExpanded)
                                                androidx.compose.ui.platform.LocalConfiguration
                                                        .current
                                                        .screenWidthDp
                                                        .dp - tabletSidebarWidth
                                        else
                                                androidx.compose.ui.platform.LocalConfiguration
                                                        .current
                                                        .screenWidthDp
                                                        .dp - collapsedTabletSidebarWidth,
                                animationSpec = tween(durationMillis = 280),
                                label = "contentWidth"
                        )

                // 侧边栏区域，使用动画宽度
                Surface(
                        modifier =
                                Modifier.width(animatedSidebarWidth)
                                        .fillMaxHeight()
                                        .zIndex(2f), // 确保侧边栏在主内容之上
                        shape =
                                MaterialTheme.shapes.medium.copy(
                                        topEnd = CornerSize(12.dp),
                                        bottomEnd = CornerSize(12.dp),
                                        topStart = CornerSize(0.dp),
                                        bottomStart = CornerSize(0.dp)
                                ),
                        shadowElevation = 4.dp,
                        color = MaterialTheme.colorScheme.surface
                ) {
                        // 根据展开状态显示不同内容，保持原有逻辑稳定性
                        if (isTabletSidebarExpanded) {
                                DrawerContent(
                                        navGroups = navGroups,
                                        currentScreen = currentScreen,
                                        selectedItem = selectedItem,
                                        isNetworkAvailable = isNetworkAvailable,
                                        networkType = networkType,
                                        scope = scope,
                                        drawerState = drawerState,
                                        onScreenSelected = { screen, item ->
                                                onScreenChange(screen)
                                                onNavItemChange(item)
                                        }
                                )
                        } else {
                                CollapsedDrawerContent(
                                        navItems = navItems,
                                        selectedItem = selectedItem,
                                        isNetworkAvailable = isNetworkAvailable,
                                        onScreenSelected = { screen, item ->
                                                onScreenChange(screen)
                                                onNavItemChange(item)
                                        }
                                )
                        }
                }

                // 主内容区域 - 使用width+offset方式，保留稳定的布局
                Box(
                        modifier =
                                Modifier.width(contentWidth)
                                        .fillMaxHeight()
                                        .offset(x = animatedSidebarWidth)
                                        .zIndex(1f)
                ) {
                        AppContent(
                                currentScreen = currentScreen,
                                selectedItem = selectedItem,
                                useTabletLayout = true,
                                isTabletSidebarExpanded = isTabletSidebarExpanded,
                                isLoading = isLoading,
                                navController = navController,
                                scope = scope,
                                drawerState = drawerState,
                                showFpsCounter = showFpsCounter,
                                onScreenChange = onScreenChange,
                                onNavItemChange = onNavItemChange,
                                onToggleSidebar = onToggleSidebar,
                                navigateToTokenConfig = navigateToTokenConfig,
                                onGestureConsumed = { consumed ->
                                        // 当聊天页面的手势被消费时，这里不需要特别处理
                                        // 因为平板模式不像手机模式那样有侧滑抽屉
                                },
                                canGoBack = canGoBack,
                                onGoBack = onGoBack,
                                isNavigatingBack = isNavigatingBack,
                                actions = topBarActions
                        )
                }

                // 添加一个小方块，填充圆角和工具栏之间的空隙
                Box(
                        modifier =
                                Modifier.width(16.dp)
                                        .height(64.dp)
                                        .offset(x = animatedSidebarWidth - 16.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .zIndex(0f)
                )
        }
}
