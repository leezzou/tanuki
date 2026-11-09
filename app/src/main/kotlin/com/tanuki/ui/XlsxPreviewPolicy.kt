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
    const val MAX_FROZEN_COLUMNS = 3
    const val MAX_FROZEN_ROWS = 8
    const val MIN_VISIBLE_SCROLL_DP = 96f

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

    /**
     * 依据实际视口宽度收缩冻结列：冻结列、行号区和分隔线占完后，
     * 必须给横向滚动区留下至少约 [minScrollWidth]，否则把最右侧冻结列退回滚动体。
     */
    fun visibleFrozenColumns(
        requested: Int,
        widths: List<Float>,
        gutterWidth: Float,
        availableWidth: Float,
        minScrollWidth: Float = MIN_VISIBLE_SCROLL_DP,
    ): Int {
        val limit = minOf(widths.size, MAX_FROZEN_COLUMNS)
        var count = requested.coerceIn(0, limit)
        if (availableWidth.isNaN() || availableWidth <= 0f) return 0
        if (availableWidth.isInfinite()) return count
        fun occupied(columns: Int): Float {
            var total = gutterWidth + if (columns > 0) 1f else 0f
            for (index in 0 until columns) total += widths[index]
            return total
        }
        while (count > 0 && occupied(count) + minScrollWidth > availableWidth) count--
        return count
    }

    /**
     * 依据实际视口高度收缩冻结行：表头、冻结行和分隔线之后必须给 LazyColumn
     * 留下至少约 [minScrollHeight]，否则把最下方冻结行退回滚动体。
     */
    fun visibleFrozenRows(
        requested: Int,
        heights: List<Float>,
        headerHeight: Float,
        availableHeight: Float,
        minScrollHeight: Float = MIN_VISIBLE_SCROLL_DP,
    ): Int {
        val limit = minOf(heights.size, MAX_FROZEN_ROWS)
        var count = requested.coerceIn(0, limit)
        if (availableHeight.isNaN() || availableHeight <= 0f) return 0
        if (availableHeight.isInfinite()) return count
        fun occupied(rows: Int): Float {
            var total = headerHeight + if (rows > 0) 1f else 0f
            for (index in 0 until rows) total += heights[index]
            return total
        }
        while (count > 0 && occupied(count) + minScrollHeight > availableHeight) count--
        return count
    }

    /** 单元格字号使用文件中的磅值并按当前缩放换算，限制在可读范围内。 */
    fun cellFontSizeSp(fontPoints: Float?, scale: Float): Float {
        val base = fontPoints?.takeIf { it > 0f } ?: 11f
        return (base * clampScale(scale)).coerceIn(8f, 72f)
    }
}
