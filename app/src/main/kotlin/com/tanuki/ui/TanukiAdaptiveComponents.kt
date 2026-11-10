package com.tanuki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 按当前宽度与字体缩放自动换行的操作区。
 *
 * 用于“允许一次/拒绝/记住”这类关键按钮：窄屏或大字体时按钮换行，
 * 不压缩文字，也不横向溢出。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TanukiResponsiveActionRow(
    modifier: Modifier = Modifier,
    fontScale: Float = LocalDensity.current.fontScale,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: @Composable FlowRowScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = maxWidth.value.takeIf { it.isFinite() }?.roundToInt() ?: 360
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = TanukiLayoutPolicy.actionColumns(width, fontScale),
            horizontalArrangement = horizontalArrangement,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * 统一处理状态栏、导航栏、刘海与输入法的页面骨架。
 *
 * 页面内部若已有滚动容器，应通过 [PaddingValues] 给尾部留白，
 * 不要重复叠加 `statusBarsPadding()`/`navigationBarsPadding()`。
 */
@Composable
fun TanukiInsetScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = topBar,
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

/** 当前视口的适配度量；旋转、分屏或字体变化时自动更新。 */
@Composable
fun rememberTanukiLayoutMetrics(): TanukiLayoutMetrics {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val widthPx = windowInfo.containerSize.width
    val heightPx = windowInfo.containerSize.height
    return remember(widthPx, heightPx, fontScale) {
        // 以当前窗口容器而不是物理屏幕为准，分屏/悬浮窗也能得到正确断点。
        val widthDp = with(density) { widthPx.toDp().value }.roundToInt().coerceAtLeast(1)
        val heightDp = with(density) { heightPx.toDp().value }.roundToInt().coerceAtLeast(1)
        TanukiLayoutMetrics(widthDp = widthDp, heightDp = heightDp, fontScale = fontScale)
    }
}

/**
 * 跨原生/官方切换或二级页返回时保存滚动位置。
 *
 * key 必须包含页面与会话身份；同一 key 在销毁重建后恢复，不同会话互不串位。
 */
@Composable
fun rememberTanukiListState(key: String): LazyListState =
    // 使用位置作用域 + key 作为输入；不要用已弃用的自定义 key 参数。
    rememberSaveable(key, saver = LazyListState.Saver) { LazyListState() }
