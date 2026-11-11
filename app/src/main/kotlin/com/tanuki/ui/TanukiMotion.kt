package com.tanuki.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.tanuki.ui.design.TanukiDesign
import com.tanuki.ui.design.TanukiMotion as TanukiMotionTokens
import com.tanuki.ui.design.TanukiMotionToken
import com.tanuki.ui.design.TanukiSpace

private val TanukiEnterEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * 读取动效令牌时长。
 *
 * 系统开启“移除动画”时统一返回 0，调用方不需要各自判断无障碍设置。
 */
@Composable
internal fun tanukiMotionMs(token: TanukiMotionToken): Int {
    val tokens = TanukiDesign.tokens
    return if (tokens.reducedMotion) 0 else token.durationMs
}

/** 反馈类动画（按压、选中、状态切换），时长走 feedback/toggle 令牌。 */
@Composable
internal fun tanukiToggleSpec(): FiniteAnimationSpec<Float> =
    tween(durationMillis = tanukiMotionMs(TanukiMotionTokens.toggle))

/** 原生内容的一次性入场；不包含也不能包住任何 WebView。 */
internal fun Modifier.tanukiEnter(): Modifier = composed {
    // 结构动画走 page 令牌（320ms，落在 240–340ms 区间）；移除动画时直接显示。
    val durationMs = tanukiMotionMs(TanukiMotionTokens.page)
    val progress = remember { Animatable(if (durationMs == 0) 1f else 0f) }
    LaunchedEffect(durationMs) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = durationMs, easing = TanukiEnterEasing))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * TanukiSpace.MD_DP.dp.toPx()
    }
}

/**
 * 折叠面板的展开/收起。
 *
 * 结构动画使用 expand 令牌；系统移除动画时时长为 0，内容立即出现/消失，
 * 不会留下半透明残影。
 */
@Composable
internal fun TanukiExpandVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val durationMs = tanukiMotionMs(TanukiMotionTokens.expand)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(animationSpec = tween(durationMillis = durationMs)) +
            fadeIn(animationSpec = tween(durationMillis = durationMs)),
        exit = shrinkVertically(animationSpec = tween(durationMillis = durationMs)) +
            fadeOut(animationSpec = tween(durationMillis = durationMs)),
    ) {
        content()
    }
}

/** 顶部仅保留低透明度烟灰层次，向背景色收敛以维持长页面阅读对比度。 */
@Composable
internal fun tanukiPageBrush(): Brush {
    val colors = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        listOf(
            colors.surfaceContainerHigh.copy(alpha = 0.30f),
            colors.surfaceContainerLow.copy(alpha = 0.12f),
            colors.background,
        )
    )
}

/** 首屏使用低反差灰阶静态渐变，不循环、不随时间变化。 */
@Composable
internal fun tanukiHeroBrush(): Brush {
    val colors = MaterialTheme.colorScheme
    return Brush.linearGradient(
        listOf(
            colors.surfaceContainerLow,
            colors.surfaceContainerHigh,
        )
    )
}
