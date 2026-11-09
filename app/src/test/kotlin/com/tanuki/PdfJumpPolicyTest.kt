package com.tanuki

import com.tanuki.ui.PdfJumpPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfJumpPolicyTest {
    @Test fun parsesPlainPageNumbers() {
        assertEquals(11, PdfJumpPolicy.parse("12", 50))
        assertEquals(11, PdfJumpPolicy.parse(" 12 ", 50))
        assertEquals(0, PdfJumpPolicy.parse("1", 50))
        assertEquals(49, PdfJumpPolicy.parse("50", 50))
    }

    @Test fun parsesCommonPageNumberFormats() {
        assertEquals(11, PdfJumpPolicy.parse("12/50", 50))
        assertEquals(11, PdfJumpPolicy.parse("第12页", 50))
        assertEquals(11, PdfJumpPolicy.parse("第 12 页", 50))
        assertEquals(11, PdfJumpPolicy.parse("第12/50", 50))
        assertEquals(11, PdfJumpPolicy.parse("12页", 50))
    }

    @Test fun rejectsOutOfRangeAndMalformedInput() {
        assertNull(PdfJumpPolicy.parse("0", 50))
        assertNull(PdfJumpPolicy.parse("51", 50))
        assertNull(PdfJumpPolicy.parse("", 50))
        assertNull(PdfJumpPolicy.parse("   ", 50))
        assertNull(PdfJumpPolicy.parse("abc", 50))
        assertNull(PdfJumpPolicy.parse("1.5", 50))
        assertNull(PdfJumpPolicy.parse("-1", 50))
        assertNull(PdfJumpPolicy.parse("１２", 50))
        assertNull(PdfJumpPolicy.parse("9999999999", 50))
        assertNull(PdfJumpPolicy.parse("12", 0))
    }

    @Test fun labelsCurrentPageAndLimits() {
        assertEquals("第 3 / 120 页", PdfJumpPolicy.label(2, 120))
        assertEquals("正在读取页数…", PdfJumpPolicy.label(0, 0))
        assertEquals("请输入 1–120 之间的页码", PdfJumpPolicy.hint(120))
        assertEquals("2.5×", PdfJumpPolicy.zoomLabel(2.5f))
        assertEquals("1.0×", PdfJumpPolicy.zoomLabel(1f))
    }
}
