package com.tanuki

import com.tanuki.ui.DocxPageAtom
import com.tanuki.ui.DocxPaginator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxPaginatorTest {
    private fun atom(key: Int, height: Float, gap: Float = 0f, breakBefore: Boolean = false) =
        DocxPageAtom(key, height, gap, breakBefore)

    @Test
    fun greedyPacksAtomsUsingGapBefore() {
        val pages = DocxPaginator.paginate(
            listOf(atom(0, 40f), atom(1, 40f, gap = 5f), atom(2, 40f, gap = 5f)),
            pageHeight = 100f,
        )
        assertEquals(2, pages.size)
        assertEquals(listOf(0, 1), pages[0].atoms.map { it.key })
        assertEquals(85f, pages[0].usedHeight, 0.001f)
        assertEquals(listOf(2), pages[1].atoms.map { it.key })
    }

    @Test
    fun gapIsDroppedAtPageStart() {
        val pages = DocxPaginator.paginate(
            listOf(atom(0, 60f), atom(1, 40f, gap = 20f), atom(2, 70f, gap = 30f)),
            pageHeight = 100f,
        )
        assertEquals(3, pages.size)
        assertEquals(40f, pages[1].usedHeight, 0.001f)
        assertEquals(70f, pages[2].usedHeight, 0.001f)
    }

    @Test
    fun explicitBreakForcesNewPageEvenWhenSpaceRemains() {
        val pages = DocxPaginator.paginate(
            listOf(atom(0, 10f), atom(1, 10f, breakBefore = true)),
            pageHeight = 100f,
        )
        assertEquals(2, pages.size)
        assertEquals(listOf(0), pages[0].atoms.map { it.key })
        assertEquals(listOf(1), pages[1].atoms.map { it.key })
    }

    @Test
    fun oversizedAtomTakesOwnPageAndIsFlagged() {
        val pages = DocxPaginator.paginate(
            listOf(atom(0, 20f), atom(1, 160f), atom(2, 20f)),
            pageHeight = 100f,
        )
        assertEquals(3, pages.size)
        assertFalse(pages[0].overflow)
        assertTrue(pages[1].overflow)
        assertEquals(listOf(1), pages[1].atoms.map { it.key })
        assertEquals(listOf(2), pages[2].atoms.map { it.key })
    }

    @Test
    fun exactlyFillingPageDoesNotSpill() {
        val pages = DocxPaginator.paginate(listOf(atom(0, 100f)), pageHeight = 100f)
        assertEquals(1, pages.size)
        assertFalse(pages[0].overflow)
    }

    @Test
    fun emptyInputProducesSingleEmptyPage() {
        val pages = DocxPaginator.paginate(emptyList(), pageHeight = 100f)
        assertEquals(1, pages.size)
        assertTrue(pages[0].atoms.isEmpty())
    }

    @Test
    fun zeroHeightBreakAtomsAreSkippedAfterFlushing() {
        val pages = DocxPaginator.paginate(
            listOf(atom(0, 30f), atom(1, 0f, breakBefore = true), atom(2, 30f)),
            pageHeight = 100f,
        )
        assertEquals(2, pages.size)
        assertEquals(listOf(2), pages[1].atoms.map { it.key })
    }

    @Test
    fun firstAtomBreakBeforeDoesNotCreateLeadingEmptyPage() {
        val pages = DocxPaginator.paginate(listOf(atom(0, 30f, breakBefore = true)), pageHeight = 100f)
        assertEquals(1, pages.size)
        assertEquals(listOf(0), pages[0].atoms.map { it.key })
    }
}
