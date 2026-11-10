package com.tanuki.ui

/**
 * 对比度计算（WCAG 2.1 相对亮度）。
 *
 * 只接受不透明 ARGB；Alpha 会被忽略。用于 JVM 回归主题中
 * 正文、按钮、错误容器等关键前景/背景组合。
 */
object TanukiContrast {
    const val NORMAL_TEXT_MIN = 4.5
    const val LARGE_TEXT_MIN = 3.0

    fun ratio(foregroundArgb: Long, backgroundArgb: Long): Double {
        val first = relativeLuminance(foregroundArgb)
        val second = relativeLuminance(backgroundArgb)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun passesNormalText(foregroundArgb: Long, backgroundArgb: Long): Boolean =
        ratio(foregroundArgb, backgroundArgb) >= NORMAL_TEXT_MIN

    fun passesLargeText(foregroundArgb: Long, backgroundArgb: Long): Boolean =
        ratio(foregroundArgb, backgroundArgb) >= LARGE_TEXT_MIN

    fun relativeLuminance(argb: Long): Double {
        val red = channel(argb, 16)
        val green = channel(argb, 8)
        val blue = channel(argb, 0)
        return 0.2126 * linearize(red) + 0.7152 * linearize(green) + 0.0722 * linearize(blue)
    }

    private fun channel(argb: Long, shift: Int): Double =
        ((argb ushr shift) and 0xFF).toDouble() / 255.0

    private fun linearize(channel: Double): Double =
        if (channel <= 0.04045) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
}
