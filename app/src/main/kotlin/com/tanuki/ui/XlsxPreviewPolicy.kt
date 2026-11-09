package com.tanuki.ui

import java.util.Locale
import kotlin.math.roundToInt

/**
 * XLSX 预览的纯尺寸/缩放策略，方便 JVM 回归。
 * 缩放通过重新计算列宽、行高和字号实现，而不是对整张表做图形层缩放，保证滚动范围与内容一致。
 */
internal object XlsxPreviewPolicy {
    const val MIN_SCALE = 0.5f
    const val MAX_SCALE = 2.0f
    const val STEP = 0.25f
    const val DEFAULT_SCALE = 1.0f

    fun clampScale(value: Float): Float =
        if (!value.isFinite()) DEFAULT_SCALE else value.coerceIn(MIN_SCALE, MAX_SCALE)

    fun zoomIn(value: Float): Float = clampScale(value + STEP)

    fun zoomOut(value: Float): Float = clampScale(value - STEP)

    /** 0 -> A、25 -> Z、26 -> AA；负数返回空串。 */
    fun columnName(index: Int): String {
        if (index < 0) return ""
        var remaining = index
        val output = StringBuilder()
        while (remaining >= 0) {
            output.append(('A' + remaining % 26))
            remaining = remaining / 26 - 1
        }
        return output.reverse().toString()
    }

    /** Excel 列宽（字符数）转 dp：保留默认列宽，限制过窄/过宽列。 */
    fun columnWidthDp(characters: Float?): Float {
        val chars = characters?.takeIf { it > 0f } ?: 8.43f
        return (chars * 7f + 8f).coerceIn(40f, 320f)
    }

    /** Excel 行高（磅）转 dp；默认 15 磅约 20dp。 */
    fun rowHeightDp(points: Float?): Float {
        val value = points?.takeIf { it > 0f } ?: 15f
        return (value * 2.2f).coerceIn(18f, 240f)
    }

    fun percentLabel(scale: Float): String =
        String.format(Locale.ROOT, "%d%%", (clampScale(scale) * 100).roundToInt())
}
