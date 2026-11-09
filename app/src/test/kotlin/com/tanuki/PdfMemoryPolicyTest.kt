package com.tanuki

import com.tanuki.ui.PdfMemoryPolicy
import com.tanuki.ui.PdfOpenPolicy
import com.tanuki.ui.PdfRenderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfMemoryPolicyTest {
    @Test fun cacheBudgetScalesWithHeapButStaysBounded() {
        assertEquals(PdfMemoryPolicy.MIN_CACHE_BYTES, PdfMemoryPolicy.cacheBudgetBytes(0))
        assertEquals(PdfMemoryPolicy.MIN_CACHE_BYTES, PdfMemoryPolicy.cacheBudgetBytes(64L * 1024 * 1024))
        assertEquals(24L * 1024 * 1024, PdfMemoryPolicy.cacheBudgetBytes(192L * 1024 * 1024))
        assertEquals(64L * 1024 * 1024, PdfMemoryPolicy.cacheBudgetBytes(512L * 1024 * 1024))
        assertEquals(PdfMemoryPolicy.MAX_CACHE_BYTES, PdfMemoryPolicy.cacheBudgetBytes(4L * 1024 * 1024 * 1024))
    }

    @Test fun singlePagePixelCapKeepsThreePagesInBudget() {
        val small = PdfMemoryPolicy.maxPagePixels(24L * 1024 * 1024)
        assertEquals(2_097_152, small)
        assertTrue(small.toLong() * PdfMemoryPolicy.BYTES_PER_PIXEL * PdfMemoryPolicy.CACHE_SLOTS <= 24L * 1024 * 1024)

        val large = PdfMemoryPolicy.maxPagePixels(64L * 1024 * 1024)
        assertEquals(PdfRenderPolicy.MAX_PIXELS, large)
        assertTrue(large.toLong() * PdfMemoryPolicy.BYTES_PER_PIXEL <= 64L * 1024 * 1024)
    }

    @Test fun pageByteAccountingUsesFourBytesPerPixel() {
        assertEquals(8_000_000L, PdfMemoryPolicy.pageBytes(1_000, 2_000))
        assertThrows(IllegalArgumentException::class.java) { PdfMemoryPolicy.pageBytes(0, 10) }
        assertThrows(IllegalArgumentException::class.java) { PdfMemoryPolicy.pageBytes(10, -1) }
    }

    @Test fun fileSizeLimitIsExplicit() {
        assertFalse(PdfOpenPolicy.fileTooLarge(PdfOpenPolicy.MAX_FILE_BYTES))
        assertTrue(PdfOpenPolicy.fileTooLarge(PdfOpenPolicy.MAX_FILE_BYTES + 1))
        assertFalse(PdfOpenPolicy.fileTooLarge(0))
    }
}
