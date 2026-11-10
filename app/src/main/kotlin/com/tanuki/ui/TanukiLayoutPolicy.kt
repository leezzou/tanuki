package com.tanuki.ui

import kotlin.math.roundToInt

enum class TanukiWindowClass { COMPACT, MEDIUM, EXPANDED }

/**
 * 当前视口的纯布局度量。
 *
 * 使用 Int dp 而不是 Compose Dp，便于 JVM 直接覆盖 360dp、横屏和
 * 1.3x/2x 字体场景；Composable 接线见 `TanukiAdaptiveComponents.kt`。
 */
data class TanukiLayoutMetrics(
    val widthDp: Int,
    val heightDp: Int,
    val fontScale: Float = 1f,
) {
    init {
        require(widthDp > 0) { "视口宽度必须为正" }
        require(heightDp > 0) { "视口高度必须为正" }
        require(fontScale > 0f) { "字体缩放必须为正" }
    }

    val windowClass: TanukiWindowClass get() = TanukiLayoutPolicy.windowClass(widthDp)
    val isLandscape: Boolean get() = widthDp > heightDp
    val isNarrow: Boolean get() = widthDp <= TanukiLayoutPolicy.NARROW_MAX_WIDTH_DP
    val contentMaxWidthDp: Int get() = TanukiLayoutPolicy.contentMaxWidthDp(widthDp)
    val actionColumns: Int get() = TanukiLayoutPolicy.actionColumns(widthDp, fontScale)
    val keyButtonMaxLines: Int get() = TanukiLayoutPolicy.keyButtonMaxLines(fontScale)
    val twoPane: Boolean get() = TanukiLayoutPolicy.twoPane(widthDp)
}

/**
 * 原生页适配策略。
 *
 * 约束：
 * - 关键按钮触控区不小于 48dp；
 * - 360dp/横屏不横向溢出；
 * - 1.3x/2x 字体时按钮文字不限行，优先换行而不是截断；
 * - 宽屏正文限制最大宽度，避免长行难以阅读。
 */
object TanukiLayoutPolicy {
    const val MIN_TOUCH_TARGET_DP = 48
    const val NARROW_MAX_WIDTH_DP = 360
    const val COMPACT_MIN_WIDTH_DP = 360
    const val MEDIUM_MIN_WIDTH_DP = 600
    const val EXPANDED_MIN_WIDTH_DP = 840
    const val MAX_CONTENT_WIDTH_DP = 760
    const val MAX_FONT_SCALE = 2.0f
    const val LARGE_FONT_SCALE = 1.3f
    private const val MIN_FONT_SCALE = 0.85f

    fun windowClass(widthDp: Int): TanukiWindowClass = when {
        widthDp >= EXPANDED_MIN_WIDTH_DP -> TanukiWindowClass.EXPANDED
        widthDp >= MEDIUM_MIN_WIDTH_DP -> TanukiWindowClass.MEDIUM
        else -> TanukiWindowClass.COMPACT
    }

    /** 上下限保护系统异常字体缩放，避免除零或无限放大。 */
    fun effectiveFontScale(fontScale: Float): Float = fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)

    fun contentMaxWidthDp(widthDp: Int): Int = widthDp.coerceAtMost(MAX_CONTENT_WIDTH_DP).coerceAtLeast(0)

    /**
     * 关键操作按钮一行可放几个。
     *
     * 360dp 一份字体时单列，600dp 两列，840dp 三列；
     * 1.3x 起最多两列，2x 强制单列，让按钮通过换行而非缩字避免截断。
     */
    fun actionColumns(widthDp: Int, fontScale: Float): Int {
        val base = when (windowClass(widthDp)) {
            TanukiWindowClass.COMPACT -> 1
            TanukiWindowClass.MEDIUM -> 2
            TanukiWindowClass.EXPANDED -> 3
        }
        return when {
            effectiveFontScale(fontScale) >= MAX_FONT_SCALE -> 1
            effectiveFontScale(fontScale) >= LARGE_FONT_SCALE -> base.coerceAtMost(2)
            else -> base
        }
    }

    /**
     * 关键按钮文字最大行数。
     *
     * 常规字体最多两行；1.3x/2x 改为不限行，配合换行布局保证“允许一次”
     * 等关键操作不被截断。
     */
    fun keyButtonMaxLines(fontScale: Float): Int =
        if (effectiveFontScale(fontScale) >= LARGE_FONT_SCALE) Int.MAX_VALUE else 2

    /** 内容页两栏（列表 + 详情）只在大屏启用，窄屏与横屏手机保持单栏。 */
    fun twoPane(widthDp: Int): Boolean = widthDp >= EXPANDED_MIN_WIDTH_DP

    /** 窄屏抽屉宽度：占视口 90%，限制在 240–360dp，避免 320/360dp 溢出。 */
    fun drawerWidthDp(widthDp: Int): Int {
        val width = (widthDp.coerceAtLeast(1) * 0.9f).roundToInt()
        return width.coerceIn(240, 360)
    }

    /** 触控区至少 48dp；大字体时按比例增大，但绝不缩小。 */
    fun minimumTouchTargetDp(fontScale: Float): Int =
        (MIN_TOUCH_TARGET_DP * effectiveFontScale(fontScale)).roundToInt()
            .coerceAtLeast(MIN_TOUCH_TARGET_DP)

    /** 图标视觉尺寸可随字体略微增大，但触控区仍由 minimumTouchTargetDp 保证。 */
    fun iconVisualSizeDp(fontScale: Float): Int =
        (24f * effectiveFontScale(fontScale)).roundToInt().coerceIn(24, 32)
}
