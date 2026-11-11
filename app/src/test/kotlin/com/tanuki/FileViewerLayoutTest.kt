package com.tanuki

import androidx.compose.ui.unit.dp
import com.tanuki.ui.FileViewerLayout
import org.junit.Assert.assertEquals
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
}
