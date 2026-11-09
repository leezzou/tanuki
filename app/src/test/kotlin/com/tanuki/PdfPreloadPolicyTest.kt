package com.tanuki

import com.tanuki.ui.PdfPreloadPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPreloadPolicyTest {
    @Test fun windowLoadsCurrentThenAheadThenBehind() {
        assertEquals(listOf(5, 6, 4), PdfPreloadPolicy.window(5, 20))
    }

    @Test fun windowIsClampedAtDocumentEdges() {
        assertEquals(listOf(0, 1), PdfPreloadPolicy.window(0, 20))
        assertEquals(listOf(19, 18), PdfPreloadPolicy.window(19, 20))
        assertEquals(listOf(0), PdfPreloadPolicy.window(0, 1))
        assertEquals(emptyList<Int>(), PdfPreloadPolicy.window(0, 0))
        assertEquals(listOf(0), PdfPreloadPolicy.window(-5, 1))
        assertEquals(listOf(9, 8), PdfPreloadPolicy.window(99, 10))
    }

    @Test fun customRadiusKeepsCurrentPageFirst() {
        assertEquals(listOf(5, 6, 4, 7, 3), PdfPreloadPolicy.window(5, 10, ahead = 2, behind = 2))
    }

    @Test fun largeDocumentOnlyPrefetchesSmallWindow() {
        val window = PdfPreloadPolicy.window(5_000, 50_000)
        assertEquals(3, window.size)
        assertTrue(window.all { it in 4_999..5_001 })
        assertEquals(setOf(4_999, 5_000, 5_001), PdfPreloadPolicy.protectedKeys(5_000, 50_000))
    }

    @Test fun negativeRadiusIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfPreloadPolicy.window(1, 10, ahead = -1)
        }
    }
}
