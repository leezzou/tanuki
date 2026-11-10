package com.tanuki

import com.tanuki.ui.TanukiLayoutPolicy
import com.tanuki.ui.TanukiMinTouchTarget
import com.tanuki.ui.TanukiStateKind
import com.tanuki.ui.TanukiStateText
import com.tanuki.ui.tanukiActionDescription
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 三态文案与无障碍契约的纯回归。 */
class TanukiStateAndAccessibilityTest {
    @Test fun loadingCopyIsChineseAndDoesNotFakeAction() {
        val copy = TanukiStateText.of(TanukiStateKind.LOADING, subject = "会话")
        assertTrue(copy.title.contains("正在加载"))
        assertTrue(copy.title.contains("会话"))
        assertTrue(copy.detail.isNotBlank())
        assertNull("加载态不能显示假按钮", copy.actionLabel)
    }

    @Test fun emptyCopyDefaultsToRefreshAction() {
        val copy = TanukiStateText.of(TanukiStateKind.EMPTY, subject = "会话")
        assertTrue(copy.title.contains("暂无"))
        assertTrue(copy.detail.isNotBlank())
        assertEquals("刷新", copy.actionLabel)
    }

    @Test fun errorCopyDefaultsToRetryAction() {
        val copy = TanukiStateText.of(TanukiStateKind.ERROR, subject = "审批")
        assertTrue(copy.title.contains("审批"))
        assertTrue(copy.title.contains("失败"))
        assertTrue(copy.detail.contains("重试"))
        assertEquals("重试", copy.actionLabel)
    }

    @Test fun customDetailAndActionArePreserved() {
        val copy = TanukiStateText.of(
            TanukiStateKind.ERROR,
            subject = "文件",
            detail = "文件已被移动，请返回列表刷新。",
            actionLabel = "返回列表",
        )
        assertEquals("文件加载失败", copy.title)
        assertEquals("文件已被移动，请返回列表刷新。", copy.detail)
        assertEquals("返回列表", copy.actionLabel)
    }

    @Test fun blankSubjectAndActionFallBackToReadableChinese() {
        val copy = TanukiStateText.of(TanukiStateKind.EMPTY, subject = "  ", actionLabel = " ")
        assertEquals("暂无内容", copy.title)
        assertEquals("刷新", copy.actionLabel)
    }

    @Test fun actionDescriptionFallsBackToVisibleLabelOrGenericText() {
        assertEquals("返回", tanukiActionDescription("返回", "列表"))
        assertEquals("刷新", tanukiActionDescription("  ", "刷新"))
        assertEquals("操作", tanukiActionDescription("", "  "))
    }

    @Test fun minimumTouchTargetIsAtLeast48DpAtEveryFontScale() {
        assertTrue(TanukiMinTouchTarget.value >= 48f)
        assertTrue(TanukiLayoutPolicy.minimumTouchTargetDp(1f) >= 48)
        assertTrue(TanukiLayoutPolicy.minimumTouchTargetDp(1.3f) >= 48)
        assertTrue(TanukiLayoutPolicy.minimumTouchTargetDp(2f) >= 48)
    }

    @Test fun stateCopyNeverReturnsEmptyText() {
        for (kind in TanukiStateKind.entries) {
            val copy = TanukiStateText.of(kind)
            assertTrue(copy.title.isNotBlank())
            assertTrue(copy.detail.isNotBlank())
            if (kind != TanukiStateKind.LOADING) assertNotNull(copy.actionLabel)
        }
    }
}
