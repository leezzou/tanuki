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

    @Test fun renderPlanFallsBackToPreloadWindowWithoutVisibility() {
        assertEquals(listOf(5, 6, 4), PdfPreloadPolicy.renderPlan(emptyList(), 5, 20))
        assertEquals(emptyList<Int>(), PdfPreloadPolicy.renderPlan(listOf(0, 1), 0, 0))
    }

    @Test fun renderPlanLoadsEveryVisiblePageWhenMoreThanThreeAreOnScreen() {
        val visible = listOf(2, 3, 4, 5, 6)
        val plan = PdfPreloadPolicy.renderPlan(visible, current = 4, pageCount = 20)
        assertEquals(listOf(4, 3, 5, 2, 6, 7, 1), plan)
        assertTrue(plan.containsAll(visible))
        assertEquals(visible.size + 2, plan.size) // 仅补首尾各一页预加载
    }

    @Test fun renderPlanPrefersCenterThenNearbyVisibleThenOuterPreload() {
        val plan = PdfPreloadPolicy.renderPlan(listOf(7, 8, 9, 10), current = 9, pageCount = 20)
        assertEquals(listOf(9, 8, 10, 7, 11, 6), plan)
        assertEquals(listOf(11, 6), plan.drop(4))
    }

    @Test fun renderPlanDeduplicatesAndClampsEdges() {
        assertEquals(listOf(0, 1, 2), PdfPreloadPolicy.renderPlan(listOf(0, 1), 0, 5))
        assertEquals(listOf(4, 3, 2), PdfPreloadPolicy.renderPlan(listOf(4, 3), 4, 5))
        assertTrue(PdfPreloadPolicy.renderPlan(listOf(-1, 0, 99), 0, 5).none { it !in 0..4 })
    }

    @Test fun renderPlanStaysBoundedForLargeVisibleSets() {
        val visible = (100..107).toList()
        val plan = PdfPreloadPolicy.renderPlan(visible, current = 104, pageCount = 1_000)
        assertTrue(plan.containsAll(visible))
        assertTrue(plan.size <= visible.size + 2)
        assertEquals(99, plan.last { it < 100 })
        assertEquals(108, plan.last { it > 107 })
    }

    @Test fun negativeRadiusIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfPreloadPolicy.window(1, 10, ahead = -1)
        }
    }
}
