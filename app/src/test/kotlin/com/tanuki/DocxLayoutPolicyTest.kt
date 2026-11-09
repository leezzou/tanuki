package com.tanuki

import com.tanuki.storage.docx.DocxPageSetup
import com.tanuki.ui.DocxImagePolicy
import com.tanuki.ui.DocxImageSize
import com.tanuki.ui.DocxPageGeometryPolicy
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
    fun decodedPixelBudgetRejectsOversizedImage() {
        assertTrue(DocxImagePolicy.withinBudget(0, 100, 100))
        assertFalse(DocxImagePolicy.withinBudget(DocxImagePolicy.MAX_TOTAL_PIXELS - 10, 100, 100))
        assertFalse(DocxImagePolicy.withinBudget(0, 0, 100))
    }
}
