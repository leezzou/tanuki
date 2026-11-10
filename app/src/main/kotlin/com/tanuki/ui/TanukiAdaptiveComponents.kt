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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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

/** 列表滚动位置；下标与偏移都必须非负。 */
data class TanukiListPosition(val index: Int, val offset: Int) {
    init {
        require(index >= 0) { "列表滚动下标不能为负" }
        require(offset >= 0) { "列表滚动偏移不能为负" }
    }

    companion object {
        val Start = TanukiListPosition(0, 0)
    }
}

/**
 * 按页面/会话显式保存列表滚动位置。
 *
 * 必须由页面宿主持有（`TanukiPageStateHost` 或控制器），不要在每个列表
 * 组合内部临时创建；否则页面移出组合后仍会丢位置。
 */
class TanukiListStateStore {
    private val positions = mutableMapOf<String, TanukiListPosition>()

    fun position(key: String): TanukiListPosition {
        require(key.isNotBlank()) { "列表状态 key 不能为空白" }
        return positions[key] ?: TanukiListPosition.Start
    }

    fun save(key: String, firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        require(key.isNotBlank()) { "列表状态 key 不能为空白" }
        positions[key] = TanukiListPosition(
            index = firstVisibleItemIndex.coerceAtLeast(0),
            offset = firstVisibleItemScrollOffset.coerceAtLeast(0),
        )
    }

    fun clear(key: String? = null) {
        if (key == null) positions.clear() else positions.remove(key)
    }

    fun keys(): Set<String> = positions.keys.toSet()
}

private val LocalTanukiListStateStore = staticCompositionLocalOf<TanukiListStateStore?> { null }

/**
 * 页面状态宿主。
 *
 * 放在导航壳的稳定位置，`pageKey` 切换时保存旧页的 `rememberSaveable` 状态，
 * 同时保留一个跨页面存活的 [TanukiListStateStore]。DSH 原生/官方切换、
 * 会话管理返回列表时都应包在这一层里。
 */
@Composable
fun TanukiPageStateHost(
    pageKey: String,
    content: @Composable () -> Unit,
) {
    val holder: SaveableStateHolder = rememberSaveableStateHolder()
    // store 必须位于 pageKey 之外，才能跨页面切换保留各会话滚动位置。
    val listStore = remember { TanukiListStateStore() }
    holder.SaveableStateProvider(pageKey) {
        CompositionLocalProvider(LocalTanukiListStateStore provides listStore) {
            content()
        }
    }
}

/**
 * 按页面/会话保存并恢复滚动位置。
 *
 * 优先使用 `TanukiPageStateHost` 提供的 store；没有宿主时退化为列表自身的
 * 局部 store（只能保证同一页面内 A→B→A）。key 必须包含页面与会话身份，
 * 例如 `dsh-native:<sessionId>`。
 */
@Composable
fun rememberTanukiListState(
    key: String,
    store: TanukiListStateStore? = null,
): LazyListState {
    val localStore = LocalTanukiListStateStore.current
    val fallbackStore = remember { TanukiListStateStore() }
    val resolved = store ?: localStore ?: fallbackStore
    val state = remember(key, resolved) {
        val position = resolved.position(key)
        LazyListState(position.index, position.offset)
    }
    DisposableEffect(state, key, resolved) {
        onDispose {
            // key 变化或页面移出时保存；回到同一 key 由上面的 remember 恢复。
            resolved.save(key, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
        }
    }
    return state
}
