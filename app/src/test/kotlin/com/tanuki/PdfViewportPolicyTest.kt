package com.tanuki

import com.tanuki.ui.PdfFitMode
import com.tanuki.ui.PdfViewportPolicy
import com.tanuki.ui.ZoomPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfViewportPolicyTest {
    @Test fun fitWidthAndFitPageDifferOnlyWhenHeightLimits() {
        val portrait = PdfViewportPolicy.pageDisplaySize(600f, 1200f, 1080f, 1000f, PdfFitMode.FIT_WIDTH)
        assertEquals(1080f, portrait.width, 0.01f)
        assertEquals(2160f, portrait.height, 0.01f)

        val fitted = PdfViewportPolicy.pageDisplaySize(600f, 1200f, 1080f, 1000f, PdfFitMode.FIT_PAGE)
        assertEquals(500f, fitted.width, 0.01f)
        assertEquals(1000f, fitted.height, 0.01f)
    }

    @Test fun invalidViewportOrPageReturnsZeroSize() {
        assertEquals(0f, PdfViewportPolicy.pageDisplaySize(0f, 100f, 100f, 100f, PdfFitMode.FIT_WIDTH).width, 0f)
        assertEquals(0f, PdfViewportPolicy.pageDisplaySize(100f, 100f, 0f, 100f, PdfFitMode.FIT_WIDTH).height, 0f)
    }

    @Test fun zoomStateUsesExistingOneToFiveRange() {
        assertFalse(PdfViewportPolicy.isZoomed(1f))
        assertFalse(PdfViewportPolicy.isZoomed(1.005f))
        assertTrue(PdfViewportPolicy.isZoomed(1.2f))
        assertEquals(5f, ZoomPolicy.clampScale(9f), 0f)
        assertEquals(1f, ZoomPolicy.clampScale(0.4f), 0f)
    }

    @Test fun horizontalPanIsBoundedByScaledContent() {
        assertEquals(0f, PdfViewportPolicy.maxPanX(1000f, 1f), 0.01f)
        assertEquals(500f, PdfViewportPolicy.maxPanX(1000f, 2f), 0.01f)
        assertEquals(500f, PdfViewportPolicy.clampPanX(900f, 1000f, 2f), 0.01f)
        assertEquals(-500f, PdfViewportPolicy.clampPanX(-900f, 1000f, 2f), 0.01f)
        assertEquals(0f, PdfViewportPolicy.clampPanX(Float.NaN, 1000f, 2f), 0f)
        assertEquals(0f, PdfViewportPolicy.maxPanX(1000f, Float.NaN), 0f)
    }

    @Test fun pinchZoomKeepsContentUnderFingerHorizontally() {
        // 视口 1000 宽，中心 500，双指在 750 处放大 2 倍：内容需左移 250px。
        assertEquals(
            -250f,
            PdfViewportPolicy.zoomPanX(0f, 750f, 1000f, 1f, 2f),
            0.01f,
        )
        // 缩回 1x 时平移自动归零。
        assertEquals(0f, PdfViewportPolicy.zoomPanX(-250f, 750f, 1000f, 2f, 1f), 0.01f)
        assertEquals(0f, PdfViewportPolicy.zoomPanX(10f, 750f, 0f, 1f, 2f), 0f)
    }

    @Test fun pinchZoomCompensatesScrollToKeepContentUnderFinger() {
        // 视口 1000 高，中心 500，手指在 750（下方 250px）放大 2 倍：
        // 内容视觉上移 250px，列表需向前滚动 125px。
        assertEquals(125f, PdfViewportPolicy.zoomScrollCompensation(750f, 1000f, 1f, 2f), 0.01f)
        assertEquals(-125f, PdfViewportPolicy.zoomScrollCompensation(250f, 1000f, 1f, 2f), 0.01f)
        // 非 1× 起点：屏幕位移 250px 在 oldScale=2 时只对应 125 列表像素，
        // 再放大到 4× 只需滚动 125 − 250/4 = 62.5 列表像素。
        assertEquals(62.5f, PdfViewportPolicy.zoomScrollCompensation(750f, 1000f, 2f, 4f), 0.01f)
        assertEquals(-62.5f, PdfViewportPolicy.zoomScrollCompensation(750f, 1000f, 4f, 2f), 0.01f)
        assertEquals(0f, PdfViewportPolicy.zoomScrollCompensation(750f, 1000f, 2f, 2f), 0f)
        assertEquals(0f, PdfViewportPolicy.zoomScrollCompensation(750f, 0f, 1f, 2f), 0f)
    }

    @Test fun zoomEdgePaddingIsZeroAtOneAndGrowsWithScale() {
        assertEquals(0f, PdfViewportPolicy.zoomEdgePadding(1000f, 1f), 0.01f)
        assertEquals(0f, PdfViewportPolicy.zoomEdgePadding(1000f, 1.005f), 0.01f)
        assertEquals(250f, PdfViewportPolicy.zoomEdgePadding(1000f, 2f), 0.01f)
        assertEquals(300f, PdfViewportPolicy.zoomEdgePadding(1000f, 2.5f), 0.01f)
        assertEquals(400f, PdfViewportPolicy.zoomEdgePadding(1000f, 5f), 0.01f)
        assertEquals(0f, PdfViewportPolicy.zoomEdgePadding(0f, 2f), 0f)
        assertEquals(0f, PdfViewportPolicy.zoomEdgePadding(1000f, Float.NaN), 0f)
        assertTrue(
            PdfViewportPolicy.zoomEdgePadding(1000f, 3f) >
                PdfViewportPolicy.zoomEdgePadding(1000f, 2f),
        )
    }

    @Test fun zoomedFirstAndLastTextStayReachableForSingleAndMultiPage() {
        val viewportHeight = 1000f
        val pageHeight = viewportHeight - 16f
        for (scale in listOf(1f, 1.5f, 2f, 2.5f, 4f, 5f)) {
            val padding = PdfViewportPolicy.zoomEdgePadding(viewportHeight, scale)
            // 第一页顶部在滚动 0 处贴住视口上沿。
            assertEquals(
                "首端不可达 scale=$scale",
                0f,
                PdfViewportPolicy.scaledScreenY(padding, 0f, viewportHeight, scale),
                0.05f,
            )
            for (pageCount in listOf(1, 3, 20)) {
                val contentHeight = pageHeight * pageCount
                val maxScroll = contentHeight + padding * 2f - viewportHeight
                // 最后一页底部在最大滚动处贴住视口下沿。
                assertEquals(
                    "末端不可达 scale=$scale pages=$pageCount",
                    viewportHeight,
                    PdfViewportPolicy.scaledScreenY(
                        padding + contentHeight,
                        maxScroll,
                        viewportHeight,
                        scale,
                    ),
                    0.6f,
                )
            }
        }
    }

    @Test fun currentPageFollowsViewportCenter() {
        val visible = listOf(
            PdfViewportPolicy.VisiblePage(0, -100, 500),
            PdfViewportPolicy.VisiblePage(1, 400, 600),
        )
        assertEquals(1, PdfViewportPolicy.pageAtCenter(visible, 0, 1000, 10))
        assertEquals(0, PdfViewportPolicy.pageAtCenter(visible, 0, 300, 10))
        assertEquals(1, PdfViewportPolicy.pageAtCenter(visible, 500, 1000, 10))
        assertNull(PdfViewportPolicy.pageAtCenter(emptyList(), 0, 1000, 10))
        assertNull(PdfViewportPolicy.pageAtCenter(visible, 0, 1000, 0))
    }

    @Test fun gapBetweenPagesFallsBackToNearestPage() {
        val visible = listOf(
            PdfViewportPolicy.VisiblePage(0, 0, 400),
            PdfViewportPolicy.VisiblePage(1, 600, 400),
        )
        assertEquals(0, PdfViewportPolicy.pageAtCenter(visible, 0, 1000, 2))
        assertEquals(1, PdfViewportPolicy.pageAtCenter(visible, 1000, 2000, 2))
    }
}
