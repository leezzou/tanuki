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
}
