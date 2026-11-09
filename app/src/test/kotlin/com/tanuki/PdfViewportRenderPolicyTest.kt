package com.tanuki

import com.tanuki.ui.PdfRenderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfViewportRenderPolicyTest {
    @Test fun a4AtPhoneWidthStaysWithinCapsAndCoversViewport() {
        val size = PdfRenderPolicy.viewportSize(595, 842, 1080)
        assertTrue(size.width >= 1080)
        assertTrue(size.width <= PdfRenderPolicy.MAX_DIMENSION)
        assertTrue(size.height <= PdfRenderPolicy.MAX_DIMENSION)
        assertTrue(size.width.toLong() * size.height <= PdfRenderPolicy.MAX_PIXELS)
        assertEquals(595.0 / 842.0, size.width.toDouble() / size.height, 0.002)
    }

    @Test fun unknownViewportFallsBackToBoundedTargetSize() {
        assertEquals(
            PdfRenderPolicy.targetSize(595, 842),
            PdfRenderPolicy.viewportSize(595, 842, 0),
        )
    }

    @Test fun hugePageIsDownscaledToPixelCap() {
        val size = PdfRenderPolicy.viewportSize(20_000, 20_000, 1080)
        assertTrue(size.width <= PdfRenderPolicy.MAX_DIMENSION)
        assertTrue(size.height <= PdfRenderPolicy.MAX_DIMENSION)
        assertTrue(size.width.toLong() * size.height <= PdfRenderPolicy.MAX_PIXELS)
    }

    @Test fun narrowPageKeepsPositiveDimensions() {
        val size = PdfRenderPolicy.viewportSize(100_000, 1, 1080)
        assertEquals(PdfRenderPolicy.MAX_DIMENSION, size.width)
        assertEquals(1, size.height)
    }

    @Test fun tinyPageRendersAtLeastOnceWithoutZeroSides() {
        val size = PdfRenderPolicy.viewportSize(10, 20, 1080)
        assertTrue(size.width > 0)
        assertTrue(size.height > 0)
        assertTrue(size.width.toLong() * size.height <= PdfRenderPolicy.MAX_PIXELS)
    }

    @Test fun pagePixelBudgetFromCacheIsRespected() {
        val pixels = 100_000
        val size = PdfRenderPolicy.viewportSize(595, 842, 1080, maxPixels = pixels)
        assertTrue(size.width.toLong() * size.height <= pixels)
    }

    @Test fun oversampleRaisesRenderResolution() {
        val normal = PdfRenderPolicy.viewportSize(300, 400, 400, oversample = 1.0)
        val sharp = PdfRenderPolicy.viewportSize(300, 400, 400, oversample = 2.0)
        assertEquals(400, normal.width)
        assertEquals(800, sharp.width)
        assertEquals(1066, sharp.height)
    }
}
