package com.tanuki

import androidx.compose.ui.unit.dp
import com.tanuki.ui.FileViewerLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 查看器底部操作栏与传输胶囊的几何回归。 */
class FileViewerLayoutTest {
    @Test fun listPageKeepsOriginalTwelveDpGap() {
        // 列表页刚做过瘦身，不能因为查看器修复而新增固定块；无导航 inset 时仍只留 12dp。
        assertEquals(12.dp, FileViewerLayout.transferPillBottomPadding(bottomInset = 0.dp, actionBarVisible = false))
        assertEquals(36.dp, FileViewerLayout.transferPillBottomPadding(bottomInset = 24.dp, actionBarVisible = false))
    }

    @Test fun viewerCapsuleSitsAboveActionBarWithFullGap() {
        val bottomInset = 24.dp
        val pillBottom = FileViewerLayout.transferPillBottomPadding(bottomInset, actionBarVisible = true)
        val actionBarTop = bottomInset + FileViewerLayout.ActionBarHeight

        assertEquals(84.dp, pillBottom) // 24dp 导航 inset + 48dp 操作栏 + 12dp 间距
        assertTrue("胶囊底边必须不低于操作栏顶边", pillBottom >= actionBarTop)
        assertEquals("胶囊与操作栏之间保留 12dp", FileViewerLayout.FloatingGap, pillBottom - actionBarTop)
    }

    @Test fun legacyPlacementOverlapsActionBarByThirtySixDp() {
        // 历史实现只上移 12dp：48dp 胶囊压住 48dp 操作栏，垂直重叠 48 - 12 = 36dp。
        val bottomInset = 24.dp
        val legacyPillBottom = bottomInset + FileViewerLayout.FloatingGap
        val actionBarTop = bottomInset + FileViewerLayout.ActionBarHeight
        val overlap = actionBarTop - legacyPillBottom

        assertEquals(36.dp, overlap)
        assertTrue("旧定位必须被判定为重叠", overlap > 0.dp)
        assertEquals(FileViewerLayout.TransferPillHeight - FileViewerLayout.FloatingGap, overlap)
        assertTrue(
            "新定位必须完全避开操作栏",
            FileViewerLayout.transferPillBottomPadding(bottomInset, actionBarVisible = true) >= actionBarTop,
        )
    }

    @Test fun landscapeIsCompactButPortraitIsNot() {
        // 2400×1080、约 411dp 高的横屏进入紧凑布局；同尺寸竖屏继续原布局。
        assertTrue(FileViewerLayout.isCompact(width = 914.dp, height = 411.dp))
        assertFalse(FileViewerLayout.isCompact(width = 411.dp, height = 914.dp))
        assertFalse("正方形不应误判为横屏", FileViewerLayout.isCompact(width = 500.dp, height = 500.dp))
    }

    @Test fun landscapeContentKeepsAtLeastSixtyPercentWithEncodingHint() {
        // 常见横屏高度 411dp：48dp 顶栏 + 24dp 编码提示 + 48dp 底栏，正文应约 70.8%。
        val budget = FileViewerLayout.compactHeightBudget(availableHeight = 411.dp, showEncodingHint = true)

        assertEquals(FileViewerLayout.CompactHeaderHeight, budget.header)
        assertEquals(FileViewerLayout.CompactEncodingHintHeight, budget.hint)
        assertEquals(FileViewerLayout.CompactBottomBarHeight, budget.bottomBar)
        assertEquals(291.dp, budget.content)
        assertTrue("正文必须至少占 60%", budget.contentFraction >= FileViewerLayout.CompactMinContentFraction)
        assertEquals(291f / 411f, budget.contentFraction, 0.0001f)
    }

    @Test fun shortLandscapeStillMeetsSixtyPercent() {
        // 高密度横屏可能只有约 360dp 高；仍要满足 60% 的可读面积下限。
        val budget = FileViewerLayout.compactHeightBudget(availableHeight = 360.dp, showEncodingHint = true)

        assertEquals(240.dp, budget.content)
        assertTrue("360dp 横屏正文仍需 ≥ 60%", budget.contentFraction >= FileViewerLayout.CompactMinContentFraction)
    }

    @Test fun dismissingEncodingHintReturnsHeightToContent() {
        val withHint = FileViewerLayout.compactHeightBudget(availableHeight = 411.dp, showEncodingHint = true)
        val withoutHint = FileViewerLayout.compactHeightBudget(availableHeight = 411.dp, showEncodingHint = false)

        assertEquals(0.dp, withoutHint.hint)
        assertEquals(FileViewerLayout.CompactEncodingHintHeight, withoutHint.content - withHint.content)
    }

    @Test fun veryShortLandscapeCollapsesHintBeforeDroppingBelowSixtyPercent() {
        // 360dp 高度可保留一行编码提示；280dp 时提示会让正文跌破 60%，应先收起提示。
        assertTrue(FileViewerLayout.shouldShowCompactEncodingHint(360.dp, hasEncodingHint = true))
        assertFalse(FileViewerLayout.shouldShowCompactEncodingHint(280.dp, hasEncodingHint = true))
        assertFalse(FileViewerLayout.shouldShowCompactEncodingHint(360.dp, hasEncodingHint = false))
    }

    @Test fun tinyHeightNeverProducesNegativeContent() {
        val budget = FileViewerLayout.compactHeightBudget(availableHeight = 80.dp, showEncodingHint = true)
        assertEquals(0.dp, budget.content)
        assertEquals(0f, budget.contentFraction, 0.0001f)
    }
}
