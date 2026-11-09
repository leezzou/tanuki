package com.tanuki

import com.tanuki.storage.docx.DocxPageSetup
import com.tanuki.ui.DocxImagePolicy
import com.tanuki.ui.DocxImageSize
import com.tanuki.ui.DocxBitmapLifetime
import com.tanuki.ui.DocxPageFramePolicy
import com.tanuki.ui.DocxPageGeometryPolicy
import com.tanuki.ui.DocxRenderBudget
import com.tanuki.ui.DocxCellPlanner
import com.tanuki.ui.DocxCellPrefixPolicy
import com.tanuki.ui.DocxTableWidths
import com.tanuki.ui.DocxTableRowPolicy
import com.tanuki.ui.DocxTextChunking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxLayoutPolicyTest {
    @Test
    fun a4GeometryFitsWidthAndKeepsPageAspect() {
        val geometry = DocxPageGeometryPolicy.compute(DocxPageSetup(), availableWidthDp = 360f)
        assertEquals(360f, geometry.pageWidthDp, 0.01f)
        assertEquals(16838f / 11906f * 360f, geometry.pageHeightDp, 0.2f)
        assertEquals((11906 - 2880f) / 11906f * 360f, geometry.contentWidthDp, 0.2f)
        assertTrue(geometry.contentHeightDp < geometry.pageHeightDp)
        // 11 磅正文放大后可读，且保持相对字号比例。
        assertEquals(12.63f, geometry.fontDp(22), 0.2f)
        assertEquals(geometry.fontDp(22) * 2, geometry.fontDp(44), 0.01f)
    }

    @Test
    fun wideViewportCentersFullPageWithoutCompressingIt() {
        val geometry = DocxPageGeometryPolicy.compute(DocxPageSetup(), availableWidthDp = 1200f)
        val frame = DocxPageFramePolicy.compute(1200f, geometry.pageWidthDp)
        assertEquals(1200f, frame.widthDp, 0.01f)
        assertEquals(720f, frame.pageWidthDp, 0.01f)
        assertEquals(240f, frame.sidePaddingDp, 0.01f)
        assertEquals(geometry.pageWidthDp, frame.pageWidthDp, 0.01f)
        assertTrue(frame.widthDp - frame.sidePaddingDp * 2f >= geometry.pageWidthDp)
    }

    @Test
    fun narrowViewportFrameUsesFullWidthWithoutSidePadding() {
        val geometry = DocxPageGeometryPolicy.compute(DocxPageSetup(), availableWidthDp = 360f)
        val frame = DocxPageFramePolicy.compute(360f, geometry.pageWidthDp)
        assertEquals(360f, frame.widthDp, 0.01f)
        assertEquals(0f, frame.sidePaddingDp, 0.01f)
    }

    @Test
    fun pageWidthIsCappedOnWideScreens() {
        val geometry = DocxPageGeometryPolicy.compute(DocxPageSetup(), availableWidthDp = 1200f)
        assertEquals(DocxPageGeometryPolicy.MAX_PAGE_WIDTH_DP, geometry.pageWidthDp, 0.01f)
    }

    @Test
    fun veryNarrowViewportUsesMinimumWidth() {
        val geometry = DocxPageGeometryPolicy.compute(DocxPageSetup(), availableWidthDp = 100f)
        assertEquals(DocxPageGeometryPolicy.MIN_PAGE_WIDTH_DP, geometry.pageWidthDp, 0.01f)
    }

    @Test
    fun landscapeSectionUsesWiderPageAndSmallerHeight() {
        val portrait = DocxPageGeometryPolicy.compute(DocxPageSetup(), 360f)
        val landscape = DocxPageGeometryPolicy.compute(DocxPageSetup(16838, 11906), 360f)
        assertTrue(landscape.pageHeightDp < portrait.pageHeightDp)
        assertTrue(landscape.contentWidthDp > landscape.contentHeightDp)
    }

    @Test
    fun absurdMarginsCannotConsumeWholePage() {
        val geometry = DocxPageGeometryPolicy.compute(
            DocxPageSetup(11906, 16838, 11906, 11906, 16838, 16838),
            360f,
        )
        assertTrue(geometry.contentWidthDp > 0f)
        assertTrue(geometry.contentHeightDp > 0f)
    }

    @Test
    fun imageSampleSizeCapsSideAndPixels() {
        assertEquals(1, DocxImagePolicy.sampleSize(800, 600))
        assertEquals(2, DocxImagePolicy.sampleSize(8000, 100))
        assertEquals(16, DocxImagePolicy.sampleSize(20000, 20000))
        assertEquals(1, DocxImagePolicy.sampleSize(0, 100))
        val width = 12000
        val height = 9000
        val sample = DocxImagePolicy.sampleSize(width, height)
        assertTrue(width / sample <= DocxImagePolicy.MAX_DECODE_SIDE)
        assertTrue((width / sample).toLong() * (height / sample) <= DocxImagePolicy.MAX_DECODE_PIXELS)
    }

    @Test
    fun imageDisplayUsesExtentAndContentWidth() {
        val size = DocxImagePolicy.displaySize(
            bitmapWidth = 1200,
            bitmapHeight = 600,
            widthEmu = 914400,
            heightEmu = 457200,
            contentWidthDp = 300f,
            contentHeightDp = 400f,
            contentWidthTwips = 9026,
            density = 1f,
        )
        assertEquals(300f * (914400.0 / (9026 * 635.0)).toFloat(), size.widthDp, 0.2f)
        assertEquals(size.widthDp * 0.5f, size.heightDp, 0.2f)
    }

    @Test
    fun imageDisplayFallsBackToBitmapSizeAndNeverUpscales() {
        val size = DocxImagePolicy.displaySize(
            bitmapWidth = 100,
            bitmapHeight = 50,
            widthEmu = null,
            heightEmu = null,
            contentWidthDp = 300f,
            contentHeightDp = 400f,
            contentWidthTwips = 9026,
            density = 2f,
        )
        assertEquals(50f, size.widthDp, 0.01f)
        assertEquals(25f, size.heightDp, 0.01f)
    }

    @Test
    fun tallImageIsScaledDownToContentHeight() {
        val size = DocxImagePolicy.displaySize(
            bitmapWidth = 400,
            bitmapHeight = 2000,
            widthEmu = 914400,
            heightEmu = 4572000,
            contentWidthDp = 300f,
            contentHeightDp = 200f,
            contentWidthTwips = 9026,
            density = 1f,
        )
        assertEquals(DocxImageSize(size.widthDp, 200f), size)
        assertTrue(size.widthDp < 300f)
    }

    @Test
    fun longParagraphChunksAreBoundedAndContiguous() {
        val text = "词".repeat(DocxTextChunking.MAX_CHARS * 2 + 123)
        val ranges = DocxTextChunking.ranges(text)
        assertTrue(ranges.size >= 3)
        assertEquals(0, ranges.first().first)
        assertEquals(text.lastIndex, ranges.last().last)
        ranges.zipWithNext().forEach { (left, right) -> assertEquals(left.last + 1, right.first) }
        assertTrue(ranges.all { it.last - it.first + 1 <= DocxTextChunking.MAX_CHARS })
    }

    @Test
    fun paragraphChunkingKeepsShortTextAndSkipsSurrogateSplit() {
        assertEquals(listOf(0..2), DocxTextChunking.ranges("abc"))
        val emoji = "a" + "\uD83D\uDE00".repeat(DocxTextChunking.MAX_CHARS)
        val ranges = DocxTextChunking.ranges(emoji)
        ranges.forEach { range ->
            val last = emoji[range.last]
            val first = emoji[range.first]
            assertFalse(last.isHighSurrogate())
            assertFalse(first.isLowSurrogate())
        }
    }

    @Test
    fun cellPlanUsesSameGlobalBudgetForManyParagraphs() {
        val plan = DocxCellPlanner.plan(List(40_000) { 1 })
        assertTrue(plan.truncated)
        assertEquals(DocxCellPlanner.MAX_CHUNKS, plan.chunks.values.sumOf { it.size })
        assertTrue(plan.chunks.values.flatten().all { it.first == 0 && it.last == 0 })
    }

    @Test
    fun cellPrefixKeepsLinesThatFitRemainingHeight() {
        val bottoms = listOf(24f, 48f, 72f, 96f)
        assertEquals(1, DocxCellPrefixPolicy.lastVisibleLine(bottoms, 50f))
        assertEquals(2, DocxCellPrefixPolicy.lastVisibleLine(bottoms, 72f))
        assertEquals(-1, DocxCellPrefixPolicy.lastVisibleLine(bottoms, 0f))
        assertEquals(-1, DocxCellPrefixPolicy.lastVisibleLine(bottoms, Float.NaN))
        assertEquals(3, DocxCellPrefixPolicy.lastVisibleLine(bottoms, 200f))
    }

    @Test
    fun cellPlanChunksLongParagraphAndPreservesBounds() {
        val plan = DocxCellPlanner.plan(listOf(20_000))
        assertFalse(plan.truncated)
        val ranges = plan.chunks.getValue(0)
        assertEquals(0, ranges.first().first)
        assertEquals(19_999, ranges.last().last)
        ranges.zipWithNext().forEach { (left, right) -> assertEquals(left.last + 1, right.first) }
        assertTrue(ranges.all { it.last - it.first + 1 <= DocxCellPlanner.MAX_CHUNK_CHARS })
    }

    @Test
    fun tableRowHeightIsClampedToContentHeight() {
        val normal = DocxTableRowPolicy.limit(200f, 500f)
        assertEquals(200f, normal.heightDp, 0.01f)
        assertFalse(normal.truncated)
        val huge = DocxTableRowPolicy.limit(400_000f, 500f)
        assertEquals(500f, huge.heightDp, 0.01f)
        assertTrue(huge.truncated)
        assertTrue(DocxTableRowPolicy.limit(Float.NaN, 500f).truncated)
        assertTrue(DocxTableRowPolicy.limit(Float.POSITIVE_INFINITY, 500f).truncated)
        assertTrue(DocxTableRowPolicy.limit(-1f, 500f).truncated)
    }

    @Test
    fun tableWidthsNormalizeHugeColumnsWithoutIntOverflow() {
        val widths = DocxTableWidths.normalize(
            listOf(Int.MAX_VALUE, Int.MAX_VALUE, 10_000),
            columnCount = 3,
            contentWidthDp = 300f,
        )
        assertEquals(3, widths.size)
        assertTrue(widths.all { it.isFinite() && it >= 0f && it <= 300f })
        assertTrue(widths.sum() <= 300.5f)
        assertEquals(widths[0], widths[1], 0.01f)
    }

    @Test
    fun tableWidthsFallBackToEqualColumnsForMismatchedGrid() {
        val widths = DocxTableWidths.normalize(listOf(2000), columnCount = 4, contentWidthDp = 200f)
        assertEquals(4, widths.size)
        widths.forEach { assertEquals(50f, it, 0.01f) }
    }

    @Test
    fun tableColumnCountIsCapped() {
        val widths = DocxTableWidths.normalize(emptyList(), columnCount = 10_000, contentWidthDp = 300f)
        assertEquals(DocxTableWidths.MAX_COLUMNS, widths.size)
        assertTrue(widths.sum() <= 300.5f)
    }

    @Test
    fun renderBudgetStopsExactlyAtLimits() {
        val blocks = DocxRenderBudget(maxBlocks = 2, maxAtoms = 2)
        assertTrue(blocks.takeBlock())
        assertTrue(blocks.takeBlock())
        assertFalse(blocks.takeBlock())
        assertTrue(blocks.exceeded)
        assertFalse(blocks.takeAtom())
    }

    @Test
    fun renderBudgetReportsAtomOverflowInsideOneBlock() {
        val atoms = DocxRenderBudget(maxBlocks = 100, maxAtoms = 3)
        assertTrue(atoms.takeBlock())
        assertTrue(atoms.takeAtom())
        assertTrue(atoms.takeAtom())
        assertTrue(atoms.takeAtom())
        assertFalse(atoms.takeAtom())
        assertTrue(atoms.exceeded)
        assertEquals(1, atoms.blocks)
    }

    @Test
    fun bitmapLifetimeReleasesOnlyCapturedSnapshot() {
        val released = mutableListOf<String>()
        val lifetime = DocxBitmapLifetime<String> { released += it }
        val initial = lifetime.snapshot()
        val first = mapOf("a" to "A")
        val replaced = lifetime.publish(first)
        assertEquals(initial, replaced)
        lifetime.release(replaced)
        assertTrue(released.isEmpty())
        assertEquals(first, lifetime.snapshot())

        val second = mapOf("b" to "B")
        val old = lifetime.publish(second)
        lifetime.release(old)
        assertEquals(listOf("A"), released)
        assertEquals(second, lifetime.snapshot())
        lifetime.release(lifetime.snapshot())
        assertEquals(listOf("A", "B"), released)
    }

    @Test
    fun decodedPixelBudgetRejectsOversizedImage() {
        assertTrue(DocxImagePolicy.withinBudget(0, 100, 100))
        assertFalse(DocxImagePolicy.withinBudget(DocxImagePolicy.MAX_TOTAL_PIXELS - 10, 100, 100))
        assertFalse(DocxImagePolicy.withinBudget(0, 0, 100))
    }
}
