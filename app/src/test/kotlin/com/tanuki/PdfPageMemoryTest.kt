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
