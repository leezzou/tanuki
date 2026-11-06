package com.tanuki

import com.tanuki.ui.PdfRenderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfRenderPolicyTest {
    @Test fun a4PageUsesBoundedTwoTimesRender() {
        val size = PdfRenderPolicy.targetSize(595, 842)
        assertEquals(1190, size.width)
        assertEquals(1684, size.height)
        assertTrue(size.width <= PdfRenderPolicy.MAX_DIMENSION)
        assertTrue(size.width.toLong() * size.height <= PdfRenderPolicy.MAX_PIXELS)
    }

    @Test fun hugePageIsDownscaledBelowDimensionAndPixelCaps() {
        val size = PdfRenderPolicy.targetSize(20_000, 20_000)
        assertTrue(size.width <= PdfRenderPolicy.MAX_DIMENSION)
        assertTrue(size.height <= PdfRenderPolicy.MAX_DIMENSION)
        assertTrue(size.width.toLong() * size.height <= PdfRenderPolicy.MAX_PIXELS)
    }

    @Test fun tinyPageDoesNotUpscaleBeyondDouble() {
        val size = PdfRenderPolicy.targetSize(10, 20)
        assertEquals(20, size.width)
        assertEquals(40, size.height)
    }

    @Test fun narrowPageKeepsPositiveDimensions() {
        val size = PdfRenderPolicy.targetSize(100_000, 1)
        assertEquals(PdfRenderPolicy.MAX_DIMENSION, size.width)
        assertEquals(1, size.height)
    }
}
