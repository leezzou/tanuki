package com.tanuki.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tanuki.ui.design.TanukiDesign
import com.tanuki.ui.design.TanukiMotion
import com.tanuki.ui.design.TanukiMotionEasing

/** 宿主拥有页面入场；子页不能再播放一次挂载动画。 */
internal val LocalTanukiPageMotionOwned = staticCompositionLocalOf { false }

/** 嵌套层级只在外层闲置时接管，避免同一次导航叠播。 */
private val LocalTanukiPageMotionActive = staticCompositionLocalOf { false }

internal enum class TanukiPageNavigation { Peer, Forward, Back, Instant }

/** 纯策略供宿主和回归共用；层级方向按审计约定在 RTL 中镜像。 */
internal data class TanukiPageMotion(
    val durationMs: Int,
    val initialOffsetDp: Float,
    val spec: TweenSpec<Float>,
) {
    fun alpha(progress: Float): Float = progress.coerceIn(0f, 1f)
    fun offsetDp(progress: Float): Float = initialOffsetDp * (1f - progress.coerceIn(0f, 1f))
}

internal fun tanukiPageMotion(
    navigation: TanukiPageNavigation,
    reducedMotion: Boolean,
    layoutDirection: LayoutDirection,
    webView: Boolean = false,
): TanukiPageMotion {
    val duration = when {
        reducedMotion || navigation == TanukiPageNavigation.Instant -> 0
        navigation == TanukiPageNavigation.Peer -> TanukiMotion.toggle.durationMs
        navigation == TanukiPageNavigation.Forward -> TanukiMotion.page.durationMs
        else -> TanukiMotion.sheetDismiss.durationMs
    }
    val easing = when (navigation) {
        TanukiPageNavigation.Forward -> TanukiMotionEasing.enter
        TanukiPageNavigation.Back -> TanukiMotionEasing.exit
        else -> TanukiMotionEasing.standard
    }
    val direction = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
    val offset = if (duration == 0 || webView) 0f else when (navigation) {
        TanukiPageNavigation.Forward -> 16f * direction
        TanukiPageNavigation.Back -> -16f * direction
        else -> 0f
    }
    return TanukiPageMotion(duration, offset, tween(durationMillis = duration, easing = easing))
}

/** 设置页是主目的地的子层级，分区是设置页的下一层；不维护第二份返回栈。 */
internal data class TanukiPageRoute(val destination: Int, val section: String? = null) {
    val depth: Int get() = if (destination == 4) if (section == null) 1 else 2 else 0
}

internal fun tanukiPageNavigation(from: TanukiPageRoute, to: TanukiPageRoute): TanukiPageNavigation = when {
    from == to -> TanukiPageNavigation.Instant
    to.depth > from.depth -> TanukiPageNavigation.Forward
    to.depth < from.depth -> TanukiPageNavigation.Back
    else -> TanukiPageNavigation.Peer
}

/** 文件详情 key 只表达当前页面身份，不把目录刷新、预览加载或滚动当导航。 */
internal fun tanukiDetailNavigation(from: String?, to: String?, restoring: Boolean = false): TanukiPageNavigation = when {
    restoring || from == to -> TanukiPageNavigation.Instant
    to == null -> TanukiPageNavigation.Back
    from == null -> TanukiPageNavigation.Forward
    else -> TanukiPageNavigation.Peer
}

/** 按浏览器已有 back() 关系判方向；历史/收藏返回网页，不另建一份层级栈。 */
internal fun tanukiBrowserPageNavigation(from: String?, to: String?): TanukiPageNavigation {
    val settingsChildren = setOf("通用", "权限与后台", "数据管理", "关于浏览器")
    return when {
        from == to -> TanukiPageNavigation.Instant
        to == null -> TanukiPageNavigation.Back
        from == null -> TanukiPageNavigation.Forward
        to == "浏览器设置" && from in settingsChildren -> TanukiPageNavigation.Back
        from == "浏览器设置" && to in settingsChildren -> TanukiPageNavigation.Forward
        from == "数据管理" && to in setOf("历史记录", "收藏夹", "下载与截图") -> TanukiPageNavigation.Forward
        else -> TanukiPageNavigation.Peer
    }
}

/**
 * 单槽页面宿主：导航立即提交，只组合当前页，旧页不会继续注册返回处理器或发请求。
 * 一个进度同时控制 alpha/位移；快速切换取消旧协程，不排队，也不保留两个活页面。
 * 活 WebView 仅由原生背景遮罩揭示，不进入 graphicsLayer，不移动、缩放或复制网页。
 */
@Composable
internal fun <T> TanukiPageTransitionHost(
    target: T,
    navigation: (T, T) -> TanukiPageNavigation,
    modifier: Modifier = Modifier,
    webView: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    val reduced = TanukiDesign.motion.reducedMotion
    val parentActive = LocalTanukiPageMotionActive.current
    val direction = LocalLayoutDirection.current
    var committed by remember { mutableStateOf(target) }
    var kind by remember { mutableStateOf(TanukiPageNavigation.Instant) }
    val progress = remember { Animatable(1f) }
    val changed = committed != target
    val nextKind = if (changed) navigation(committed, target) else kind
    val policy = tanukiPageMotion(nextKind, reduced || parentActive, direction, webView)
    // 目标变化的首帧即使用起点，避免 LaunchedEffect 启动前先闪出完整的新页。
    val fraction = when {
        policy.durationMs == 0 -> 1f
        changed -> 0f
        else -> progress.value
    }
    val active = fraction < 1f
    LaunchedEffect(target, reduced, parentActive) {
        if (committed != target) {
            kind = navigation(committed, target)
            progress.snapTo(0f)
            committed = target
        }
        val current = tanukiPageMotion(kind, reduced || parentActive, direction, webView)
        if (current.durationMs == 0) progress.snapTo(1f)
        else progress.animateTo(1f, current.spec)
    }
    Box(modifier) {
        CompositionLocalProvider(
            LocalTanukiPageMotionOwned provides true,
            LocalTanukiPageMotionActive provides (parentActive || active),
        ) {
            val pageModifier = if (webView) Modifier.fillMaxSize() else Modifier.fillMaxSize().graphicsLayer {
                alpha = policy.alpha(fraction)
                translationY = policy.offsetDp(fraction).dp.toPx()
            }
            content(pageModifier)
        }
        if (webView && active) {
            // 遮罩不接收输入、没有语义；页面状态、权限与返回处理始终立即生效。
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 1f - fraction)))
        }
    }
}
