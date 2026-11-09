package com.tanuki

import com.tanuki.ui.PdfPageMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageMemoryTest {
    private class FakePage(val id: Int, val bytes: Long) {
        var released = false
    }

    private class Fixture(budget: Long) {
        val released = mutableListOf<Int>()
        val memory = PdfPageMemory<Int, FakePage>(
            budgetBytes = budget,
            sizeOf = { it.bytes },
            release = {
                it.released = true
                released += it.id
            },
        )

        fun page(id: Int, bytes: Long) = FakePage(id, bytes)
    }

    @Test fun evictedPageIsReleasedWhenNotDisplayed() {
        val fixture = Fixture(100)
        val first = fixture.page(1, 80)
        val second = fixture.page(2, 40)
        fixture.memory.store(1, first, setOf(1))
        val evicted = fixture.memory.store(2, second, setOf(2))

        assertEquals(setOf(1), evicted)
        assertTrue(first.released)
        assertFalse(second.released)
        assertFalse(fixture.memory.contains(1))
        assertTrue(fixture.memory.contains(2))
    }

    @Test fun displayedPageIsNotReleasedUntilHidden() {
        val fixture = Fixture(100)
        val first = fixture.page(1, 80)
        val second = fixture.page(2, 40)
        fixture.memory.store(1, first, setOf(1))
        fixture.memory.display(1)

        assertEquals(setOf(1), fixture.memory.store(2, second, setOf(2)))
        assertFalse("仍在组合中绘制的位图不能立即回收", first.released)
        assertTrue(fixture.memory.contains(1))
        assertEquals(first, fixture.memory.get(1))

        fixture.memory.hide(1)
        assertTrue(first.released)
        assertFalse(fixture.memory.contains(1))
    }

    @Test fun discardRemovesPageFromBudgetAndReleasesWhenHidden() {
        val fixture = Fixture(100)
        val hidden = fixture.page(1, 40)
        val shown = fixture.page(2, 40)
        fixture.memory.store(1, hidden, setOf(1))
        fixture.memory.store(2, shown, setOf(2))
        fixture.memory.display(2)

        fixture.memory.discard(1)
        assertTrue(hidden.released)
        assertFalse(fixture.memory.contains(1))
        assertTrue(fixture.memory.contains(2))

        fixture.memory.discard(2)
        assertFalse("仍显示的位图不能立即回收", shown.released)
        fixture.memory.hide(2)
        assertTrue(shown.released)
        assertFalse(fixture.memory.contains(2))
        assertEquals(0, fixture.memory.usedBytes())
    }

    @Test fun reservationEvictsUnprotectedCachedPagesBeforeAllocation() {
        val fixture = Fixture(100)
        val first = fixture.page(1, 40)
        val second = fixture.page(2, 40)
        fixture.memory.store(1, first, setOf(1))
        fixture.memory.store(2, second, setOf(2))

        val evicted = fixture.memory.tryReserve(40, setOf(1))
        assertEquals(listOf(2), evicted)
        assertTrue(second.released)
        assertFalse(first.released)
        assertEquals(80, fixture.memory.residentBytes())
        assertFalse(fixture.memory.contains(2))
    }

    @Test fun reservationFailsWhileDisplayedPagesCannotBeFreed() {
        val fixture = Fixture(100)
        val first = fixture.page(1, 40)
        fixture.memory.store(1, first, setOf(1))
        fixture.memory.store(2, fixture.page(2, 40), setOf(2))
        fixture.memory.display(1)
        fixture.memory.display(2)
        assertNull(fixture.memory.tryReserve(40, emptySet()))
        assertEquals(80, fixture.memory.residentBytes())

        fixture.memory.hide(1)
        assertEquals(listOf(1), fixture.memory.tryReserve(40, emptySet()))
        assertTrue(first.released)
    }

    @Test fun reservationIsConsumedByStoreWithoutDoubleCounting() {
        val fixture = Fixture(100)
        val page = fixture.page(1, 40)
        assertEquals(emptyList<Int>(), fixture.memory.tryReserve(40, emptySet()))
        assertEquals(40, fixture.memory.residentBytes())

        fixture.memory.store(1, page, setOf(1), reservedBytes = 40)
        assertEquals(40, fixture.memory.residentBytes())
        assertEquals(40, fixture.memory.usedBytes())
    }

    @Test fun cancelledReservationRestoresCapacity() {
        val fixture = Fixture(40)
        assertNotNull(fixture.memory.tryReserve(40, emptySet()))
        assertNull(fixture.memory.tryReserve(1, emptySet()))
        fixture.memory.cancelReservation(40)
        assertEquals(0, fixture.memory.residentBytes())
        assertNotNull(fixture.memory.tryReserve(40, emptySet()))
    }

    @Test fun fullLruThenNewPlanReservesBeforeAllocation() {
        val fixture = Fixture(100)
        val pages = (1..5).map { fixture.page(it, 20) }
        pages.forEach { fixture.memory.store(it.id, it, setOf(it.id)) }
        fixture.memory.display(1)
        assertEquals(100, fixture.memory.residentBytes())

        // LRU 已满：必须先淘汰未显示的页，才能为新计划页预留 20 字节。
        assertEquals(listOf(2), fixture.memory.tryReserve(20, setOf(1)))
        assertEquals(100, fixture.memory.residentBytes())
        assertFalse(fixture.memory.contains(2))

        fixture.memory.store(6, fixture.page(6, 20), setOf(1, 6), reservedBytes = 20)
        assertEquals(100, fixture.memory.residentBytes())
        assertEquals(100, fixture.memory.usedBytes())
    }

    @Test fun closeReleasesAllIncludingDeferredPages() {
        val fixture = Fixture(100)
        val first = fixture.page(1, 80)
        fixture.memory.store(1, first, setOf(1))
        fixture.memory.display(1)
        fixture.memory.store(2, fixture.page(2, 40), setOf(2))

        fixture.memory.close()

        assertTrue(first.released)
        assertEquals(setOf(1, 2), fixture.released.toSet())
        assertFalse(fixture.memory.contains(1))
    }

    @Test fun replacingDisplayedPageDefersOldRelease() {
        val fixture = Fixture(200)
        val old = fixture.page(1, 40)
        val fresh = fixture.page(11, 40)
        fixture.memory.store(1, old, setOf(1))
        fixture.memory.display(1)

        fixture.memory.store(1, fresh, setOf(1))
        assertFalse(old.released)
        assertEquals(fresh, fixture.memory.get(1))

        fixture.memory.hide(1)
        assertTrue(old.released)
        assertFalse(fresh.released)

        fixture.memory.close()
        assertTrue(fresh.released)
    }

    @Test fun touchKeepsRecentlyReadPage() {
        val fixture = Fixture(100)
        val first = fixture.page(1, 40)
        fixture.memory.store(1, first, emptySet())
        fixture.memory.store(2, fixture.page(2, 40), emptySet())
        assertNotNull(fixture.memory.get(1))

        assertEquals(setOf(2), fixture.memory.store(3, fixture.page(3, 40), emptySet()))
        assertTrue(fixture.memory.contains(1))
    }

    @Test fun storeIntoClosedMemoryReleasesImmediately() {
        val fixture = Fixture(100)
        fixture.memory.close()
        val late = fixture.page(9, 40)

        assertEquals(emptySet<Int>(), fixture.memory.store(9, late, setOf(9)))
        assertTrue(late.released)
        assertFalse(fixture.memory.contains(9))
        assertNull(fixture.memory.get(9))
    }

    @Test fun displayedFlagDropsAfterHide() {
        val fixture = Fixture(100)
        fixture.memory.store(1, fixture.page(1, 10), setOf(1))
        fixture.memory.display(1)
        assertTrue(fixture.memory.isDisplayed(1))
        fixture.memory.hide(1)
        assertFalse(fixture.memory.isDisplayed(1))
    }
}
