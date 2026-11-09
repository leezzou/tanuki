package com.tanuki

import com.tanuki.ui.PdfMemoryPolicy
import com.tanuki.ui.PdfPreloadPolicy
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

    @Test fun residentPageCountShrinksPerPagePixelCap() {
        val budget = 24L * 1024 * 1024
        val threePages = PdfMemoryPolicy.maxPagePixels(budget, residentPages = 3)
        val fourPages = PdfMemoryPolicy.maxPagePixels(budget, residentPages = 4)
        val eightPages = PdfMemoryPolicy.maxPagePixels(budget, residentPages = 8)
        assertTrue(threePages > fourPages)
        assertTrue(fourPages > eightPages)
        assertEquals(1_048_576, PdfMemoryPolicy.maxPagePixels(budget, residentPages = 6))
        assertEquals(1, PdfMemoryPolicy.maxPagePixels(budget, residentPages = 100_000_000))
    }

    @Test fun visibleRenderPlanFitsBudgetIncludingInFlightPage() {
        val budget = 24L * 1024 * 1024
        val scenarios = listOf(
            listOf(0, 1, 2, 3),
            listOf(10, 11, 12, 13, 14, 15, 16, 17),
            (100..111).toList(),
        )
        for (visible in scenarios) {
            val plan = PdfPreloadPolicy.renderPlan(visible, current = visible.first(), pageCount = 1_000)
            val resident = (plan.size + 1).coerceAtLeast(PdfMemoryPolicy.CACHE_SLOTS)
            val capPixels = PdfMemoryPolicy.maxPagePixels(budget, residentPages = resident)
            val capBytes = capPixels.toLong() * PdfMemoryPolicy.BYTES_PER_PIXEL
            assertTrue(
                "plan=${plan.size} resident=$resident 超过预算",
                plan.size.toLong() * capBytes <= budget,
            )
            assertTrue(
                "含在途位图后超过预算",
                (plan.size + 1L) * capBytes <= budget,
            )
        }
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
