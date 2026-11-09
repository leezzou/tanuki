package com.tanuki

import com.tanuki.ui.XlsxPreviewPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XlsxPreviewPolicyTest {
    @Test fun mapsColumnIndexesToSpreadsheetLetters() {
        assertEquals("A", XlsxPreviewPolicy.columnName(0))
        assertEquals("Z", XlsxPreviewPolicy.columnName(25))
        assertEquals("AA", XlsxPreviewPolicy.columnName(26))
        assertEquals("AB", XlsxPreviewPolicy.columnName(27))
        assertEquals("AZ", XlsxPreviewPolicy.columnName(51))
        assertEquals("BA", XlsxPreviewPolicy.columnName(52))
        assertEquals("", XlsxPreviewPolicy.columnName(-1))
    }

    @Test fun clampsAndStepsZoomWithinPreviewRange() {
        assertEquals(1f, XlsxPreviewPolicy.clampScale(Float.NaN))
        assertEquals(XlsxPreviewPolicy.MIN_SCALE, XlsxPreviewPolicy.clampScale(0.1f))
        assertEquals(XlsxPreviewPolicy.MAX_SCALE, XlsxPreviewPolicy.clampScale(9f))
        assertEquals(1.25f, XlsxPreviewPolicy.zoomIn(1f))
        assertEquals(0.75f, XlsxPreviewPolicy.zoomOut(1f))
        assertEquals(XlsxPreviewPolicy.MAX_SCALE, XlsxPreviewPolicy.zoomIn(XlsxPreviewPolicy.MAX_SCALE))
        assertEquals(XlsxPreviewPolicy.MIN_SCALE, XlsxPreviewPolicy.zoomOut(XlsxPreviewPolicy.MIN_SCALE))
        assertEquals("125%", XlsxPreviewPolicy.percentLabel(1.25f))
    }

    @Test fun derivesReadableWidthsAndHeightsWithBounds() {
        assertEquals(67.01f, XlsxPreviewPolicy.columnWidthDp(null), 0.01f)
        assertEquals(148f, XlsxPreviewPolicy.columnWidthDp(20f), 0.01f)
        assertEquals(40f, XlsxPreviewPolicy.columnWidthDp(0.1f), 0.01f)
        assertEquals(320f, XlsxPreviewPolicy.columnWidthDp(100f), 0.01f)
        assertEquals(33f, XlsxPreviewPolicy.rowHeightDp(null), 0.01f)
        assertEquals(33f, XlsxPreviewPolicy.rowHeightDp(15f), 0.01f)
        assertEquals(18f, XlsxPreviewPolicy.rowHeightDp(1f), 0.01f)
        assertEquals(240f, XlsxPreviewPolicy.rowHeightDp(500f), 0.01f)
        assertTrue(XlsxPreviewPolicy.columnWidthDp(8.43f) > 0f)
    }

    @Test fun shrinksFrozenPanesToKeepVisibleScrollArea() {
        assertEquals(3, XlsxPreviewPolicy.visibleFrozenColumns(3, List(3) { 67f }, 44f, 360f, 96f))
        // 2x 下 3 个默认宽列加行号区超过 360dp，只保留 1 个冻结列。
        assertEquals(1, XlsxPreviewPolicy.visibleFrozenColumns(3, List(3) { 134f }, 80f, 360f, 96f))
        assertEquals(1, XlsxPreviewPolicy.visibleFrozenColumns(3, listOf(200f, 200f, 200f), 44f, 360f, 96f))
        assertEquals(0, XlsxPreviewPolicy.visibleFrozenColumns(3, List(3) { 134f }, 80f, 100f, 96f))
        assertEquals(8, XlsxPreviewPolicy.visibleFrozenRows(8, List(8) { 33f }, 28f, 400f, 96f))
        // 2x 行高下 8 个冻结行会把表体挤空，收缩到 3 行。
        assertEquals(3, XlsxPreviewPolicy.visibleFrozenRows(8, List(8) { 66f }, 56f, 400f, 96f))
        assertEquals(0, XlsxPreviewPolicy.visibleFrozenRows(8, List(8) { 66f }, 56f, 100f, 96f))
    }

    @Test fun appliesPerCellFontSizeWithinReadableBounds() {
        assertEquals(11f, XlsxPreviewPolicy.cellFontSizeSp(11f, 1f), 0.01f)
        assertEquals(22f, XlsxPreviewPolicy.cellFontSizeSp(11f, 2f), 0.01f)
        assertEquals(36f, XlsxPreviewPolicy.cellFontSizeSp(36f, 1f), 0.01f)
        assertEquals(72f, XlsxPreviewPolicy.cellFontSizeSp(36f, 2f), 0.01f)
        assertEquals(8f, XlsxPreviewPolicy.cellFontSizeSp(4f, 0.5f), 0.01f)
    }
}
