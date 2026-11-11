package com.tanuki

import com.tanuki.ui.TodoDockItem
import com.tanuki.ui.TodoDockItemStatus
import com.tanuki.ui.TodoDockLayout
import com.tanuki.ui.TodoDockScrollPolicy
import com.tanuki.ui.TodoDockState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DshTodoDockTest {
    @Test fun emptyChecklistHidesWholeBar() {
        val state = TodoDockState()
        assertFalse(state.isVisible)
        assertEquals(0, state.total)
        assertEquals(0, state.completed)
        assertEquals(0f, state.progress, 0.0001f)
        assertEquals(0, state.progressPercent)
        assertEquals("", state.summary)
        assertNull(TodoDockScrollPolicy.request(state, expanded = true))
    }

    @Test fun progressUsesCompletedOverTotalWithoutPartialCredit() {
        val state = todoState(
            TodoDockItem("1", "一", TodoDockItemStatus.COMPLETED),
            TodoDockItem("2", "二", TodoDockItemStatus.COMPLETED),
            TodoDockItem("3", "三", TodoDockItemStatus.COMPLETED),
            TodoDockItem("4", "四", TodoDockItemStatus.PENDING),
            TodoDockItem("5", "写回归", TodoDockItemStatus.IN_PROGRESS),
            TodoDockItem("6", "六", TodoDockItemStatus.PENDING),
            TodoDockItem("7", "七", TodoDockItemStatus.PENDING)
        )
        assertEquals(7, state.total)
        assertEquals(3, state.completed)
        assertEquals(3f / 7f, state.progress, 0.0001f)
        assertEquals(42, state.progressPercent)
        assertEquals("☑ 3/7 · 正在：写回归", state.summary)
    }

    @Test fun currentPrefersInProgressOverEarlierPending() {
        val state = todoState(
            TodoDockItem("1", "待开始", TodoDockItemStatus.PENDING),
            TodoDockItem("2", "正在做", TodoDockItemStatus.IN_PROGRESS)
        )
        assertEquals(1, state.activeIndex)
        assertEquals("正在做", state.current?.text)
        assertEquals("☑ 0/2 · 正在：正在做", state.summary)
    }

    @Test fun pendingOnlyFallsBackToNextStep() {
        val state = todoState(
            TodoDockItem("1", "已完成", TodoDockItemStatus.COMPLETED),
            TodoDockItem("2", "下一步", TodoDockItemStatus.PENDING)
        )
        assertEquals(1, state.activeIndex)
        assertEquals("☑ 1/2 · 下一步：下一步", state.summary)
    }

    @Test fun allCompletedShowsFullProgressAndNoCurrent() {
        val state = todoState(
            TodoDockItem("1", "一", TodoDockItemStatus.COMPLETED),
            TodoDockItem("2", "二", TodoDockItemStatus.COMPLETED)
        )
        assertEquals(1f, state.progress, 0.0001f)
        assertEquals(100, state.progressPercent)
        assertEquals("☑ 2/2 · 已全部完成", state.summary)
        assertNull(state.current)
        assertEquals(1, state.scrollIndex)
    }

    @Test fun expandedListScrollsToCurrentItemOnlyWhenOpen() {
        val state = todoState(
            TodoDockItem("1", "一", TodoDockItemStatus.COMPLETED),
            TodoDockItem("2", "二", TodoDockItemStatus.COMPLETED),
            TodoDockItem("3", "三", TodoDockItemStatus.COMPLETED),
            TodoDockItem("4", "四", TodoDockItemStatus.PENDING),
            TodoDockItem("5", "当前项", TodoDockItemStatus.IN_PROGRESS)
        )
        assertEquals(4, state.activeIndex)
        assertEquals(4, state.scrollIndex)
        assertEquals(4, TodoDockScrollPolicy.request(state, expanded = true)?.index)
        assertNull(TodoDockScrollPolicy.request(state, expanded = false))
    }

    @Test fun expandedHeightNeverExceedsThirtyPercent() {
        assertEquals(192, TodoDockLayout.maxExpandedHeightDp(640))
        assertEquals(240, TodoDockLayout.maxExpandedHeightDp(800))
        assertEquals(300, TodoDockLayout.maxExpandedHeightDp(1000))
        assertEquals(0, TodoDockLayout.maxExpandedHeightDp(0))
        assertTrue(TodoDockLayout.MAX_SCREEN_PERCENT == 30)
    }

    @Test fun itemTextIsCollapsedToOneLine() {
        val item = TodoDockItem("1", "  第一行\n第二行  ", TodoDockItemStatus.PENDING)
        assertEquals("第一行 第二行", item.oneLineText)
    }

    private fun todoState(vararg items: TodoDockItem) = TodoDockState(items.toList())
}
