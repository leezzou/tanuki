package com.tanuki.ui.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/** 曲线只在设计层声明；普通点击不用弹簧，settle 仅供手势释放。 */
internal object TanukiMotionEasing {
    val enter = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val exit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    val linear = LinearEasing
    const val SETTLE_DAMPING = 1f
    const val SETTLE_STIFFNESS = 500f
}

internal data class TanukiListMotion(
    val placement: FiniteAnimationSpec<IntOffset>?,
    val fadeIn: FiniteAnimationSpec<Float>?,
    val fadeOut: FiniteAnimationSpec<Float>?,
)

/** 语义工厂不依赖组合，便于 JVM 验证；主题和原生 View 共享同一套策略。 */
internal class TanukiMotionSpecs(val reducedMotion: Boolean) {
    fun duration(token: TanukiMotionToken): Int = TanukiMotion.durationMs(token, reducedMotion)
    fun pressScale(pressed: Boolean): Float = if (pressed && !reducedMotion) PRESS_SCALE else 1f
    fun <T> toggle(): TweenSpec<T> = tween(duration(TanukiMotion.toggle), easing = TanukiMotionEasing.standard)
    fun <T> press(pressed: Boolean): TweenSpec<T> = tween(
        duration(if (pressed) TanukiMotion.pressIn else TanukiMotion.pressOut),
        easing = if (pressed) TanukiMotionEasing.standard else TanukiMotionEasing.enter,
    )
    fun <T> icon(entering: Boolean): TweenSpec<T> = tween(
        duration(if (entering) TanukiMotion.toggle else TanukiMotion.pressIn),
        easing = if (entering) TanukiMotionEasing.enter else TanukiMotionEasing.exit,
    )
    fun <T> state(): TweenSpec<T> = toggle()
    fun <T> expand(opening: Boolean, longDetail: Boolean = false): TweenSpec<T> = tween(
        duration(if (opening && longDetail) TanukiMotion.expand else TanukiMotion.layout),
        easing = when {
            !opening -> TanukiMotionEasing.exit
            longDetail -> TanukiMotionEasing.enter
            else -> TanukiMotionEasing.standard
        },
    )
    fun list(): TanukiListMotion = if (reducedMotion) TanukiListMotion(null, null, null) else TanukiListMotion(
        tween(TanukiMotion.layout.durationMs, easing = TanukiMotionEasing.standard),
        tween(TanukiMotion.toggle.durationMs, easing = TanukiMotionEasing.enter),
        tween(TanukiMotion.pressOut.durationMs, easing = TanukiMotionEasing.exit),
    )
    fun <T> count(): TweenSpec<T> = tween(duration(TanukiMotion.toggle), easing = TanukiMotionEasing.enter)
    fun <T> copy(): TweenSpec<T> = state()
    fun <T> progress(): TweenSpec<T> = tween(duration(TanukiMotion.pressIn), easing = TanukiMotionEasing.linear)
    fun <T> settle(): FiniteAnimationSpec<T> = if (reducedMotion) snap() else spring(
        dampingRatio = TanukiMotionEasing.SETTLE_DAMPING,
        stiffness = TanukiMotionEasing.SETTLE_STIFFNESS,
    )

    companion object {
        const val PRESS_SCALE = 0.98f
        const val ICON_SCALE = 0.92f
        const val COPY_CONFIRMATION_MS = 1_500L
    }
}
