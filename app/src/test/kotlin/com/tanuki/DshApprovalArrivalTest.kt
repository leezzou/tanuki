package com.tanuki

import com.tanuki.ui.DshApprovalArrivalPolicy
import com.tanuki.ui.DshApprovalArrivalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DshApprovalArrivalTest {
    @Test fun increaseShowsHintAndStartsNewToken() {
        val initial = DshApprovalArrivalState(2)
        val next = DshApprovalArrivalPolicy.onCount(initial, 3)
        assertTrue(next.visible)
        assertEquals(3, next.knownCount)
        assertEquals(1, next.token)
    }

    @Test fun decreaseDuringHintKeepsVisibleAndTimerToken() {
        val shown = DshApprovalArrivalPolicy.onCount(DshApprovalArrivalState(2), 3)
        // 2→3 后 3 秒内回答一项回到 2：不能被取消成永久显示，也不重置计时。
        val reduced = DshApprovalArrivalPolicy.onCount(shown, 2)
        assertTrue(reduced.visible)
        assertEquals(1, reduced.token)
        assertEquals(2, reduced.knownCount)
        val hidden = DshApprovalArrivalPolicy.hidden(reduced)
        assertFalse(hidden.visible)
    }

    @Test fun anotherIncreaseBeforeHideRestartsTimer() {
        val first = DshApprovalArrivalPolicy.onCount(DshApprovalArrivalState(2), 3)
        val second = DshApprovalArrivalPolicy.onCount(first, 4)
        assertTrue(second.visible)
        assertEquals(2, second.token)
    }

    @Test fun noIncreaseDoesNotShowHint() {
        val state = DshApprovalArrivalPolicy.onCount(DshApprovalArrivalState(3), 3)
        assertFalse(state.visible)
        assertEquals(0, state.token)
        val lower = DshApprovalArrivalPolicy.onCount(DshApprovalArrivalState(3), 1)
        assertFalse(lower.visible)
        assertEquals(1, lower.knownCount)
    }
}
