package com.tanuki.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** 窄屏抽屉是否打开；供页面内的二级返回处理器让位。 */
private val LocalTanukiDrawerOpen = staticCompositionLocalOf { false }

/**
 * 自适应外壳：同一套导航与内容在宽屏和窄屏复用。
 *
 * - 宽屏（>=840dp）：左侧导航栏 + 右侧内容，不启用抽屉；
 * - 窄屏：内容全宽，导航以 ModalNavigationDrawer 打开；返回键先关抽屉。
 *
 * [navigation] 与 [content] 只写一份。窄屏时内容通过 [content] 的
 * `openNavigation` 打开抽屉；宽屏时该回调为空操作，页面无需分支。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TanukiAdaptiveShell(
    navigation: @Composable (closeNavigation: () -> Unit) -> Unit,
    content: @Composable (modifier: Modifier, openNavigation: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    metrics: TanukiLayoutMetrics = rememberTanukiLayoutMetrics(),
    navigationWidth: Dp = 280.dp,
) {
    val scope = rememberCoroutineScope()
    if (metrics.twoPane) {
        Row(modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .width(navigationWidth)
                    .fillMaxHeight()
                    // 导航栏自己处理系统栏与刘海，不把 inset 责任推给业务页。
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .displayCutoutPadding(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                navigation {}
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                content(Modifier.fillMaxSize(), {})
            }
        }
    } else {
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        // 窄屏抽屉最多占 90% 宽度，360dp/320dp 下不会横向溢出。
        val drawerWidth = TanukiLayoutPolicy.drawerWidthDp(metrics.widthDp).dp
        ModalNavigationDrawer(
            drawerState = drawerState,
            modifier = modifier.fillMaxSize(),
            drawerContent = {
                ModalDrawerSheet(modifier = Modifier.fillMaxHeight().width(drawerWidth)) {
                    navigation { scope.launch { drawerState.close() } }
                }
            },
        ) {
            CompositionLocalProvider(LocalTanukiDrawerOpen provides drawerState.isOpen) {
                content(
                    Modifier.fillMaxSize(),
                    { scope.launch { drawerState.open() } },
                )
            }
        }
        // 必须在 content 之后组合：Compose 让最后注册的 BackHandler 优先，
        // 这样详情页打开抽屉后，返回只关抽屉，不会先关详情。
        BackHandler(enabled = drawerState.isOpen) {
            scope.launch { drawerState.close() }
        }
    }
}

/**
 * 列表 + 详情自适应容器。
 *
 * - 宽屏（>=840dp）：列表与详情并排，同一套 composable 渲染；
 * - 窄屏：只显示其中一页；详情可见时返回键先回调 [onDetailBack]。
 *
 * 窄屏要到详情页时把 [detailVisible] 设为 true；关闭详情后回到列表。
 * 宽屏始终保留列表，[detailVisible] 为 false 时由 [detail] 自己显示空态。
 */
@Composable
fun TanukiTwoPane(
    list: @Composable (modifier: Modifier) -> Unit,
    detail: @Composable (modifier: Modifier) -> Unit,
    detailVisible: Boolean,
    modifier: Modifier = Modifier,
    metrics: TanukiLayoutMetrics = rememberTanukiLayoutMetrics(),
    listWidth: Dp = 360.dp,
    onDetailBack: (() -> Unit)? = null,
) {
    if (metrics.twoPane) {
        Row(modifier.fillMaxSize()) {
            Box(Modifier.width(listWidth).fillMaxHeight()) {
                list(Modifier.fillMaxSize())
            }
            VerticalDivider()
            Box(Modifier.weight(1f).fillMaxHeight()) {
                detail(Modifier.fillMaxSize())
            }
        }
    } else {
        val drawerOpen = LocalTanukiDrawerOpen.current
        // 抽屉打开时详情处理器必须让位，不能抢先关闭下层详情。
        BackHandler(enabled = detailVisible && onDetailBack != null && !drawerOpen) {
            onDetailBack?.invoke()
        }
        // 窄屏也要保留调用方的 padding/尺寸约束，不能换成裸 fillMaxSize。
        TanukiPageTransitionHost(
            target = detailVisible,
            navigation = { _, showingDetail ->
                if (showingDetail) TanukiPageNavigation.Forward else TanukiPageNavigation.Back
            },
            modifier = modifier.fillMaxSize(),
        ) { pageModifier ->
            if (detailVisible) detail(pageModifier) else list(pageModifier)
        }
    }
}
