package com.tanuki

import com.tanuki.ui.ZoomPolicy
import com.tanuki.ui.ZoomPolicy.Pan
import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomPolicyTest {
    @Test fun clampsScaleToRangeAndRecoversFromInvalidValues() {
        assertEquals(1f, ZoomPolicy.clampScale(0.2f), 0f)
        assertEquals(1f, ZoomPolicy.clampScale(Float.NaN), 0f)
        assertEquals(1f, ZoomPolicy.clampScale(Float.NEGATIVE_INFINITY), 0f)
        assertEquals(5f, ZoomPolicy.clampScale(9f), 0f)
        assertEquals(2f, ZoomPolicy.clampScale(2f), 0f)
        assertEquals(1f, ZoomPolicy.MIN_SCALE, 0f)
        assertEquals(1f, ZoomPolicy.RESET_SCALE, 0f)
    }

    @Test fun doubleTapTogglesBetweenOneAndTwoPointFive() {
        assertEquals(2.5f, ZoomPolicy.nextDoubleTapScale(1f), 0f)
        assertEquals(2.5f, ZoomPolicy.nextDoubleTapScale(1.001f), 0f)
        assertEquals(1f, ZoomPolicy.nextDoubleTapScale(2.5f), 0f)
        assertEquals(1f, ZoomPolicy.nextDoubleTapScale(5f), 0f)
    }

    @Test fun fittedSizePreservesAspectAndNeverExceedsViewport() {
        val wide = ZoomPolicy.fittedSize(400f, 200f, 1000f, 1000f)
        assertEquals(1000f, wide.x, 0.01f)
        assertEquals(500f, wide.y, 0.01f)
        val tall = ZoomPolicy.fittedSize(200f, 400f, 1000f, 1000f)
        assertEquals(500f, tall.x, 0.01f)
        assertEquals(1000f, tall.y, 0.01f)
        assertEquals(Pan.Zero, ZoomPolicy.fittedSize(0f, 100f, 1000f, 1000f))
    }

    @Test fun zoomKeepsThePointUnderTheCentroidStable() {
        // 视口 1000x1000，中心 (500,500)，在 (750,500) 双指放大 2 倍：
        // 内容需左移 250px，手指下的点才保持在原位置。
        val pan = ZoomPolicy.zoomAround(Pan.Zero, 750f, 500f, 500f, 500f, 2f)
        assertEquals(-250f, pan.x, 0.01f)
        assertEquals(0f, pan.y, 0.01f)
        assertEquals(Pan.Zero, ZoomPolicy.zoomAround(Pan.Zero, 750f, 500f, 500f, 500f, 0f))
    }

    @Test fun panIsClampedToTheVisibleScaledContent() {
        val bounded = ZoomPolicy.clampPan(Pan(500f, -500f), 2000f, 1000f, 1000f, 1000f)
        assertEquals(500f, bounded.x, 0.01f)
        assertEquals(0f, bounded.y, 0.01f)
        val smaller = ZoomPolicy.clampPan(Pan(80f, 80f), 800f, 900f, 1000f, 1000f)
        assertEquals(Pan.Zero, smaller)
        val invalid = ZoomPolicy.clampPan(Pan(Float.NaN, 10f), 2000f, 2000f, 1000f, 1000f)
        assertEquals(Pan.Zero, invalid)
    }

    @Test fun maximumZoomStaysWithinFiveTimesTheFittedContent() {
        val fitted = ZoomPolicy.fittedSize(595f, 842f, 1000f, 1000f)
        val maxScale = ZoomPolicy.clampScale(10f)
        val maxX = (fitted.x * maxScale - 1000f) / 2f
        val maxY = (fitted.y * maxScale - 1000f) / 2f
        val scaled = ZoomPolicy.clampPan(Pan(-10_000f, 10_000f), fitted.x * maxScale, fitted.y * maxScale, 1000f, 1000f)
        assertEquals(-maxX, scaled.x, 0.01f)
        assertEquals(maxY, scaled.y, 0.01f)
        assertEquals(5f, maxScale, 0f)
    }
}
