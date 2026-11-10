package com.tanuki

import com.tanuki.ui.TanukiLayoutMetrics
import com.tanuki.ui.TanukiLayoutPolicy
import com.tanuki.ui.TanukiWindowClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 360dp、横屏、大字体与宽屏的布局策略回归。 */
class TanukiLayoutPolicyTest {
    @Test fun compact360UsesSingleColumnAndNoHorizontalOverflow() {
        val metrics = TanukiLayoutMetrics(widthDp = 360, heightDp = 800, fontScale = 1f)
        assertEquals(TanukiWindowClass.COMPACT, metrics.windowClass)
        assertTrue(metrics.isNarrow)
        assertFalse(metrics.isLandscape)
        assertEquals(1, metrics.actionColumns)
        assertEquals(360, metrics.contentMaxWidthDp)
        assertFalse(metrics.twoPane)
    }

    @Test fun narrow320StillKeepsSingleColumnAndFullWidth() {
        val metrics = TanukiLayoutMetrics(widthDp = 320, heightDp = 640)
        assertTrue(metrics.isNarrow)
        assertEquals(1, metrics.actionColumns)
        assertEquals(320, metrics.contentMaxWidthDp)
    }

    @Test fun phoneLandscapeUsesTwoColumnsWithoutTwoPane() {
        val metrics = TanukiLayoutMetrics(widthDp = 800, heightDp = 360)
        assertEquals(TanukiWindowClass.MEDIUM, metrics.windowClass)
        assertTrue(metrics.isLandscape)
        assertEquals(2, metrics.actionColumns)
        assertFalse(metrics.twoPane)
    }

    @Test fun expandedScreenCapsContentWidthAndEnablesTwoPane() {
        val metrics = TanukiLayoutMetrics(widthDp = 1280, heightDp = 800)
        assertEquals(TanukiWindowClass.EXPANDED, metrics.windowClass)
        assertEquals(3, metrics.actionColumns)
        assertEquals(TanukiLayoutPolicy.MAX_CONTENT_WIDTH_DP, metrics.contentMaxWidthDp)
        assertTrue(metrics.twoPane)
    }

    @Test fun largeFontForcesWrappingInsteadOfTruncatingKeyActions() {
        for (scale in listOf(1.3f, 2f)) {
            val metrics = TanukiLayoutMetrics(widthDp = 360, heightDp = 800, fontScale = scale)
            assertEquals("大字体在 360dp 必须单列，避免按钮挤压", 1, metrics.actionColumns)
            assertEquals("1.3x/2x 下关键按钮不限行", Int.MAX_VALUE, metrics.keyButtonMaxLines)
            assertTrue(
                "触控区不能小于 48dp",
                TanukiLayoutPolicy.minimumTouchTargetDp(scale) >= TanukiLayoutPolicy.MIN_TOUCH_TARGET_DP,
            )
            assertTrue(TanukiLayoutPolicy.iconVisualSizeDp(scale) >= 24)
        }
    }

    @Test fun mediumScreenAtLargeFontCapsAtTwoColumns() {
        assertEquals(2, TanukiLayoutPolicy.actionColumns(widthDp = 700, fontScale = 1.3f))
        assertEquals(1, TanukiLayoutPolicy.actionColumns(widthDp = 700, fontScale = 2f))
        assertEquals(2, TanukiLayoutPolicy.actionColumns(widthDp = 700, fontScale = 1f))
    }

    @Test fun drawerWidthFitsNarrowScreensWithoutOverflow() {
        assertEquals(288, TanukiLayoutPolicy.drawerWidthDp(320))
        assertEquals(324, TanukiLayoutPolicy.drawerWidthDp(360))
        assertEquals(360, TanukiLayoutPolicy.drawerWidthDp(600))
        assertTrue(TanukiLayoutPolicy.drawerWidthDp(320) < 320)
        assertTrue(TanukiLayoutPolicy.drawerWidthDp(360) < 360)
    }

    @Test fun touchTargetGrowsWithFontButNeverShrinks() {
        assertEquals(48, TanukiLayoutPolicy.minimumTouchTargetDp(0.85f))
        assertEquals(48, TanukiLayoutPolicy.minimumTouchTargetDp(1f))
        assertEquals(62, TanukiLayoutPolicy.minimumTouchTargetDp(1.3f))
        assertEquals(96, TanukiLayoutPolicy.minimumTouchTargetDp(2f))
    }

    @Test fun abnormalFontScaleIsClamped() {
        assertEquals(0.85f, TanukiLayoutPolicy.effectiveFontScale(0.1f), 0.001f)
        assertEquals(2f, TanukiLayoutPolicy.effectiveFontScale(4f), 0.001f)
        assertEquals(32, TanukiLayoutPolicy.iconVisualSizeDp(4f))
    }
}
