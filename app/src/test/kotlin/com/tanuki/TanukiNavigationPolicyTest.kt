package com.tanuki

import com.tanuki.ui.TanukiBackAction
import com.tanuki.ui.TanukiBackContext
import com.tanuki.ui.TanukiBackPolicy
import com.tanuki.ui.TanukiDshPaneState
import com.tanuki.ui.TanukiDshSwitchPolicy
import com.tanuki.ui.TanukiDshSwitchState
import com.tanuki.ui.TanukiOverlayStack
import com.tanuki.ui.TanukiSecondaryPagePolicy
import com.tanuki.ui.TanukiSecondaryPageState
import com.tanuki.ui.TanukiTabHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 原生界面返回栈、通知目标与原生/官方切换的纯策略回归。 */
class TanukiNavigationPolicyTest {
    @Test fun panelClosesBeforeFullscreenHistoryOrPage() {
        val context = TanukiBackContext(
            overlays = listOf("会话详情", "模型选择"),
            fullscreen = true,
            webCanGoBack = true,
            atRootDestination = false,
        )
        assertEquals(TanukiBackAction.CloseOverlay("模型选择"), TanukiBackPolicy.decide(context))
    }

    @Test fun fullscreenExitsBeforeWebHistory() {
        val context = TanukiBackContext(fullscreen = true, webCanGoBack = true)
        assertEquals(TanukiBackAction.LeaveFullscreen, TanukiBackPolicy.decide(context))
    }

    @Test fun webHistoryRunsBeforeLeavingTheDshPage() {
        val context = TanukiBackContext(webCanGoBack = true)
        assertEquals(TanukiBackAction.WebHistoryBack, TanukiBackPolicy.decide(context))
    }

    @Test fun secondaryPageLeavesWithoutExitingApplication() {
        val context = TanukiBackContext(atRootDestination = false)
        assertEquals(TanukiBackAction.LeavePage, TanukiBackPolicy.decide(context))
    }

    @Test fun onlyRootDestinationExitsApplication() {
        val context = TanukiBackContext(atRootDestination = true)
        assertEquals(TanukiBackAction.ExitApplication, TanukiBackPolicy.decide(context))
    }

    @Test fun overlayStackIsLifoAndIgnoresBlankOrRepeatedTop() {
        val stack = TanukiOverlayStack(listOf("列表", "审批"))
        assertEquals("审批", stack.current)
        assertFalse("重复打开栈顶不应叠加", stack.push("审批"))
        assertFalse("空 id 不应入栈", stack.push("  "))
        assertEquals(listOf("列表", "审批"), stack.snapshot())
        assertTrue(stack.push("模型"))
        assertEquals(listOf("列表", "审批", "模型"), stack.snapshot())
        assertEquals("模型", stack.pop())
        assertEquals("审批", stack.pop())
        assertEquals("列表", stack.current)
        stack.clear()
        assertNull(stack.current)
        assertEquals(0, stack.size)
    }

    @Test fun closingSecondaryPanelKeepsListAndScrollPosition() {
        val state = TanukiSecondaryPageState(
            panels = listOf("会话详情"),
            listItems = listOf("会话 A", "会话 B", "会话 C"),
            listScrollIndex = 3,
            listScrollOffset = 42,
        )
        val transition = TanukiSecondaryPagePolicy.back(state)
        assertEquals(TanukiBackAction.CloseOverlay("会话详情"), transition.action)
        assertTrue(transition.state.panels.isEmpty())
        assertSame("关闭面板不能复制或清空列表", state.listItems, transition.state.listItems)
        assertEquals(3, transition.state.listScrollIndex)
        assertEquals(42, transition.state.listScrollOffset)
    }

    @Test fun backWithoutPanelLeavesPageAndStillKeepsList() {
        val state = TanukiSecondaryPageState(
            listItems = listOf("会话 A"),
            listScrollIndex = 1,
            listScrollOffset = 8,
        )
        val transition = TanukiSecondaryPagePolicy.back(state)
        assertEquals(TanukiBackAction.LeavePage, transition.action)
        assertSame(state, transition.state)
        assertEquals(1, transition.state.listScrollIndex)
    }

    @Test fun notificationFromSettingsReturnsToSettings() {
        val history = TanukiTabHistory(TanukiTabHistory.HOME_TAB)
        history.select(TanukiTabHistory.SETTINGS_TAB)
        assertTrue(history.openNotificationTarget())
        assertEquals(TanukiTabHistory.DSH_TAB, history.current)
        assertEquals(TanukiTabHistory.SETTINGS_TAB, history.back())
        assertEquals(TanukiTabHistory.HOME_TAB, history.back())
    }

    @Test fun repeatedNotificationTargetDoesNotGrowHistory() {
        val history = TanukiTabHistory(TanukiTabHistory.HOME_TAB)
        history.openNotificationTarget()
        assertFalse(history.openNotificationTarget())
        assertEquals(TanukiTabHistory.DSH_TAB, history.current)
        assertEquals(TanukiTabHistory.HOME_TAB, history.back())
        assertEquals("兜底页不应直接退出", TanukiTabHistory.HOME_TAB, history.back())
    }

    @Test fun notificationFromSettingsToSameDshTargetKeepsPreviousDestination() {
        val history = TanukiTabHistory(TanukiTabHistory.DSH_TAB)
        history.select(TanukiTabHistory.SETTINGS_TAB)
        assertTrue(history.openNotificationTarget())
        assertEquals(TanukiTabHistory.SETTINGS_TAB, history.back())
    }

    @Test fun tabHistoryIsBounded() {
        val history = TanukiTabHistory(TanukiTabHistory.HOME_TAB, maxEntries = 2)
        listOf(1, 2, 3, 4).forEach(history::select)
        assertEquals(listOf(2, 3), history.history())
        assertEquals(3, history.back())
        assertEquals(listOf(2), history.history())
    }

    @Test fun switchingBetweenNativeAndOfficialKeepsBothSides() {
        val native = TanukiDshPaneState("native-session", listScrollIndex = 4, listScrollOffset = 20, draft = "原生草稿")
        val official = TanukiDshPaneState("official-session", listScrollIndex = 1, listScrollOffset = 2, draft = "官方草稿")
        var state = TanukiDshSwitchState(native = false, nativePane = native, officialPane = official)
        state = TanukiDshSwitchPolicy.switch(state, toNative = true)
        assertEquals(native, state.activePane)
        state = TanukiDshSwitchPolicy.updateActive(state, native.copy(draft = "继续编辑"))
        state = TanukiDshSwitchPolicy.switch(state, toNative = false)
        assertEquals(official, state.activePane)
        assertEquals("official-session", TanukiDshSwitchPolicy.restoredSessionId(state, toNative = false))
        state = TanukiDshSwitchPolicy.switch(state, toNative = true)
        assertEquals("继续编辑", state.activePane.draft)
        assertEquals("native-session", TanukiDshSwitchPolicy.restoredSessionId(state, toNative = true))
    }

    @Test fun switchingToCurrentModeIsNoOp() {
        val state = TanukiDshSwitchState(native = true, nativePane = TanukiDshPaneState("A"))
        assertSame(state, TanukiDshSwitchPolicy.switch(state, toNative = true))
    }
}
