package com.tanuki

import com.tanuki.ui.DshDockKind
import com.tanuki.ui.DshDockLayout
import com.tanuki.ui.DshDockMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DshDockLayoutTest {
    @Test fun emptyDockShowsNothing() {
        val layout = DshDockLayout.resolve(
            approvalVisible = false,
            goalVisible = false,
            todoVisible = false
        )
        assertTrue(layout.eligible.isEmpty())
        assertTrue(layout.visible.isEmpty())
        assertTrue(layout.folded.isEmpty())
        assertEquals(0, layout.overflowCount)
    }

    @Test fun threeBarsKeepApprovalAndGoalAndFoldTodo() {
        val layout = DshDockLayout.resolve(
            approvalVisible = true,
            goalVisible = true,
            todoVisible = true
        )
        assertEquals(
            listOf(DshDockKind.APPROVAL, DshDockKind.GOAL, DshDockKind.TODO),
            layout.eligible
        )
        assertEquals(listOf(DshDockKind.APPROVAL, DshDockKind.GOAL), layout.visible)
        assertEquals(listOf(DshDockKind.TODO), layout.folded)
        assertEquals(1, layout.overflowCount)
        assertFalse(layout.overflowExpanded)
    }

    @Test fun twoEligibleBarsAreBothVisibleWithoutOverflow() {
        val approvalAndTodo = DshDockLayout.resolve(
            approvalVisible = true,
            goalVisible = false,
            todoVisible = true
        )
        assertEquals(listOf(DshDockKind.APPROVAL, DshDockKind.TODO), approvalAndTodo.visible)
        assertEquals(0, approvalAndTodo.overflowCount)

        val goalAndTodo = DshDockLayout.resolve(
            approvalVisible = false,
            goalVisible = true,
            todoVisible = true
        )
        assertEquals(listOf(DshDockKind.GOAL, DshDockKind.TODO), goalAndTodo.visible)
        assertEquals(0, goalAndTodo.overflowCount)
    }

    @Test fun onlyTodoStillOccupiesItsOwnBar() {
        val layout = DshDockLayout.resolve(
            approvalVisible = false,
            goalVisible = false,
            todoVisible = true
        )
        assertEquals(listOf(DshDockKind.TODO), layout.visible)
        assertEquals(0, layout.overflowCount)
    }

    @Test fun priorityOrderIsStableRegardlessOfInputOrder() {
        // 审批 > 目标 > 待办：即使只显示两条，也必须先保留高优先级。
        val layout = DshDockLayout.resolve(
            approvalVisible = true,
            goalVisible = true,
            todoVisible = true
        )
        assertEquals(DshDockKind.APPROVAL, layout.visible[0])
        assertEquals(DshDockKind.GOAL, layout.visible[1])
        assertEquals(DshDockKind.TODO, layout.folded.single())
    }

    @Test fun expandingOverflowRevealsAllButKeepsFoldedCount() {
        val layout = DshDockLayout.resolve(
            approvalVisible = true,
            goalVisible = true,
            todoVisible = true,
            overflowExpanded = true
        )
        assertEquals(
            listOf(DshDockKind.APPROVAL, DshDockKind.GOAL, DshDockKind.TODO),
            layout.visible
        )
        assertEquals(listOf(DshDockKind.TODO), layout.folded)
        assertEquals(1, layout.overflowCount)
        assertTrue(layout.overflowExpanded)
    }

    @Test fun expandingWithoutOverflowIsIgnored() {
        val layout = DshDockLayout.resolve(
            approvalVisible = true,
            goalVisible = true,
            todoVisible = false,
            overflowExpanded = true
        )
        assertEquals(listOf(DshDockKind.APPROVAL, DshDockKind.GOAL), layout.visible)
        assertTrue(layout.folded.isEmpty())
        assertFalse(layout.overflowExpanded)
    }

    @Test fun heightAnimationStaysWithinRequestedWindow() {
        assertTrue(DshDockMotion.MIN_HEIGHT_MS >= 200)
        assertTrue(DshDockMotion.MAX_HEIGHT_MS <= 300)
        assertTrue(DshDockMotion.HEIGHT_MS in DshDockMotion.MIN_HEIGHT_MS..DshDockMotion.MAX_HEIGHT_MS)
        assertEquals(0, DshDockMotion.heightDurationMs(reducedMotion = true))
        assertEquals(DshDockMotion.HEIGHT_MS, DshDockMotion.heightDurationMs(reducedMotion = false))
    }
}
