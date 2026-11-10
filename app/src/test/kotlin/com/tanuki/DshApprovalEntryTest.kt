package com.tanuki

import com.tanuki.ui.DshApprovalActions
import com.tanuki.ui.DshApprovalEntry
import com.tanuki.ui.DshApprovalState
import com.tanuki.ui.DshPendingApproval
import com.tanuki.web.DshApprovalOutcome
import com.tanuki.web.DshApprovalRequest
import com.tanuki.web.DshRememberedChoice
import com.tanuki.web.DshRiskAssessment
import com.tanuki.web.DshRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DshApprovalEntryTest {
    @Test fun emptyStateHidesEntryBar() {
        val entry = DshApprovalEntry.of(DshApprovalState(), "session-1")
        assertFalse(entry.visible)
        assertEquals("", entry.label)
    }

    @Test fun pendingForSelectedSessionShowsCountAndHighRisk() {
        val state = DshApprovalState(
            pending = listOf(
                pending("e1", "session-1", DshRiskLevel.HIGH),
                pending("e2", "session-1", DshRiskLevel.LOW),
                pending("e3", "session-2", DshRiskLevel.HIGH)
            )
        )
        val entry = DshApprovalEntry.of(state, "session-1")
        assertTrue(entry.visible)
        assertEquals(2, entry.pendingCount)
        assertEquals(1, entry.highRiskCount)
        assertEquals("⚑ 待审批 2 项", entry.label)
    }

    @Test fun rememberedOnlyAndErrorStatesRemainReachable() {
        val remembered = DshApprovalState(
            remembered = listOf(
                DshRememberedChoice("session-1", "bash", DshRiskLevel.HIGH, DshApprovalOutcome.ALLOWED_ONCE)
            )
        )
        val memoryEntry = DshApprovalEntry.of(remembered, "session-1")
        assertTrue(memoryEntry.visible)
        assertEquals("审批记忆 1 项", memoryEntry.label)

        val errorEntry = DshApprovalEntry.of(DshApprovalState(error = "审批通道已断开"), "session-1")
        assertTrue(errorEntry.visible)
        assertEquals("审批通道异常", errorEntry.label)
        assertTrue(errorEntry.hasError)
    }

    @Test fun noticeAloneIsReachableButNotLabeledPending() {
        val entry = DshApprovalEntry.of(
            DshApprovalState(notice = "DSH 服务地址已变化，1 个旧待审批已失效"),
            "session-1"
        )
        assertTrue(entry.visible)
        assertEquals("审批提示", entry.label)
        assertTrue(entry.hasNotice)
        assertEquals(0, entry.pendingCount)
    }

    @Test fun defaultPanelWiringCannotShowDeadRevokeAction() {
        // 生产默认调用不传 onForgetRemembered；组件必须隐藏单条撤销，只保留全部清除。
        assertFalse(DshApprovalActions.canForget(null))
        assertTrue(DshApprovalActions.canForget { })
    }

    private fun pending(eventId: String, sessionId: String, risk: DshRiskLevel): DshPendingApproval =
        DshPendingApproval(
            request = DshApprovalRequest(
                eventId = eventId,
                sessionId = sessionId,
                toolName = "bash",
                callId = "call-$eventId"
            ),
            risk = DshRiskAssessment(risk, "测试风险")
        )
}
