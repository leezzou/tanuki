package com.tanuki

import com.tanuki.ui.PdfPageCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageCacheTest {
    @Test fun evictsLeastRecentlyUsedPageWhenOverBudget() {
        val cache = PdfPageCache<Int>(100)
        assertEquals(emptyList<Int>(), cache.put(1, 40))
        assertEquals(emptyList<Int>(), cache.put(2, 40))
        assertEquals(listOf(1), cache.put(3, 40))
        assertEquals(80, cache.usedBytes)
        assertEquals(listOf(2), cache.put(4, 30))
        assertEquals(70, cache.usedBytes)
        assertEquals(2, cache.size)
    }

    @Test fun touchMovesPageToMostRecentlyUsedEnd() {
        val cache = PdfPageCache<Int>(100)
        cache.put(1, 40)
        cache.put(2, 40)
        cache.touch(1)
        assertEquals(listOf(2), cache.put(3, 40))
        assertTrue(cache.contains(1))
        assertFalse(cache.contains(2))
    }

    @Test fun protectedKeysSurviveBudgetEviction() {
        val cache = PdfPageCache<Int>(100)
        cache.put(1, 40, protectedKeys = setOf(1))
        cache.put(2, 40, protectedKeys = setOf(1))
        assertEquals(listOf(2), cache.put(3, 40, protectedKeys = setOf(1)))
        assertTrue(cache.contains(1))
        assertTrue(cache.contains(3))
        assertEquals(80, cache.usedBytes)
    }

    @Test fun singleOversizedEntryIsEvictedAndAccountingRestored() {
        val cache = PdfPageCache<Int>(50)
        assertEquals(listOf(7), cache.put(7, 80))
        assertEquals(0, cache.usedBytes)
        assertEquals(0, cache.size)
        assertFalse(cache.contains(7))
    }

    @Test fun replacingSameKeyUpdatesByteAccounting() {
        val cache = PdfPageCache<Int>(100)
        cache.put(1, 80)
        cache.put(1, 30)
        assertEquals(30, cache.usedBytes)
        assertEquals(30L, cache.bytesOf(1))
        assertEquals(emptyList<Int>(), cache.put(2, 60))
        assertEquals(90, cache.usedBytes)
    }

    @Test fun removeAndClearKeepAccountingConsistent() {
        val cache = PdfPageCache<Int>(100)
        cache.put(1, 30)
        cache.put(2, 20)
        assertTrue(cache.remove(1))
        assertFalse(cache.remove(1))
        assertEquals(20, cache.usedBytes)
        assertEquals(listOf(2), cache.clear())
        assertEquals(0, cache.usedBytes)
        assertNull(cache.bytesOf(2))
    }

    @Test fun invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { PdfPageCache<Int>(0) }
        val cache = PdfPageCache<Int>(10)
        assertThrows(IllegalArgumentException::class.java) { cache.put(1, 0) }
    }
}
