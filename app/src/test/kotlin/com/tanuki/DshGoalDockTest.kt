package com.tanuki

import com.tanuki.ui.DshGoalAction
import com.tanuki.ui.DshGoalCapabilities
import com.tanuki.ui.DshGoalActions
import com.tanuki.ui.GoalDockState
import com.tanuki.ui.GoalDockStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DshGoalDockTest {
    @Test fun blankSummaryHidesTheBar() {
        assertFalse(GoalDockState(summary = "").isVisible)
        assertFalse(GoalDockState(summary = "   \n  ").isVisible)
        assertTrue(GoalDockState(summary = "补齐目标条").isVisible)
    }

    @Test fun summaryIsCollapsedToOneLine() {
        val state = GoalDockState(summary = "  完成目标条\n并补 JVM 回归  ")
        assertEquals("完成目标条 并补 JVM 回归", state.oneLineSummary)
    }

    @Test fun statusLabelsAndBlockedWarningAreExplicit() {
        assertEquals("进行中", GoalDockStatus.RUNNING.label)
        assertEquals("已暂停", GoalDockStatus.PAUSED.label)
        assertEquals("受阻", GoalDockStatus.BLOCKED.label)
        assertTrue(GoalDockState(summary = "x", status = GoalDockStatus.BLOCKED).blocked)
        assertFalse(GoalDockState(summary = "x", status = GoalDockStatus.RUNNING).blocked)
    }

    @Test fun unwiredCapabilitiesDisableEveryActionWithReason() {
        val state = GoalDockState(summary = "目标", status = GoalDockStatus.RUNNING)
        val availability = DshGoalActions.availability(state, DshGoalCapabilities())
        assertEquals(4, availability.size)
        assertTrue(availability.none { it.enabled })
        availability.forEach { action ->
            assertFalse(action.enabled)
            assertFalse(action.reason.isNullOrBlank())
        }
        assertFalse(DshGoalCapabilities().anyAvailable)
    }

    @Test fun runningGoalAllowsPauseEditClearButNotResume() {
        val capabilities = DshGoalCapabilities(
            canPause = true,
            canResume = true,
            canEdit = true,
            canClear = true
        )
        val availability = DshGoalActions.availability(
            GoalDockState(summary = "目标", status = GoalDockStatus.RUNNING),
            capabilities
        )
        assertTrue(availability.first { it.action == DshGoalAction.PAUSE }.enabled)
        assertTrue(availability.first { it.action == DshGoalAction.EDIT }.enabled)
        assertTrue(availability.first { it.action == DshGoalAction.CLEAR }.enabled)
        val resume = availability.first { it.action == DshGoalAction.RESUME }
        assertFalse(resume.enabled)
        assertEquals("目标正在进行中，无需恢复", resume.reason)
    }

    @Test fun pausedGoalAllowsResumeAndExplainsDisabledPause() {
        val capabilities = DshGoalCapabilities(canPause = true, canResume = true)
        val availability = DshGoalActions.availability(
            GoalDockState(summary = "目标", status = GoalDockStatus.PAUSED),
            capabilities
        )
        val pause = availability.first { it.action == DshGoalAction.PAUSE }
        val resume = availability.first { it.action == DshGoalAction.RESUME }
        assertFalse(pause.enabled)
        assertTrue(resume.enabled)
        assertNull(resume.reason)
        assertEquals("目标已暂停，无需再次暂停", pause.reason)
    }

    @Test fun blockedGoalDisablesPauseAndResumeButKeepsEditReachable() {
        val capabilities = DshGoalCapabilities(
            canPause = true,
            canResume = true,
            canEdit = true,
            canClear = true
        )
        val availability = DshGoalActions.availability(
            GoalDockState(summary = "目标", status = GoalDockStatus.BLOCKED),
            capabilities
        )
        assertFalse(availability.first { it.action == DshGoalAction.PAUSE }.enabled)
        assertFalse(availability.first { it.action == DshGoalAction.RESUME }.enabled)
        assertTrue(availability.first { it.action == DshGoalAction.EDIT }.enabled)
        assertTrue(availability.first { it.action == DshGoalAction.CLEAR }.enabled)
    }
}
