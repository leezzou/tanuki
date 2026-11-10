package com.tanuki

import com.tanuki.ui.DshApprovalController
import com.tanuki.web.DshApprovalOutcome
import com.tanuki.web.DshApprovalRequest
import com.tanuki.web.DshApprovalSession
import com.tanuki.web.DshApprovalTransport
import com.tanuki.web.DshEndpoint
import com.tanuki.web.DshRiskAssessment
import com.tanuki.web.DshRiskLevel
import com.tanuki.web.DshToolItem
import com.tanuki.web.DshToolStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DshApprovalControllerTest {
    @Test fun startAnswersAndRemembersSameToolAndRisk() {
        val harness = Harness()
        harness.controller.start()
        assertTrue(harness.controller.state.value.ready)
        val transport = harness.transports.single()
        transport.listener.onApproval(request("e1"))
        assertEquals(1, harness.controller.state.value.pending.size)

        harness.controller.answer("e1", DshApprovalOutcome.ALLOWED_ONCE, remember = true)
        assertTrue(harness.controller.state.value.pending.isEmpty())
        assertEquals(1, harness.controller.state.value.remembered.size)
        assertEquals(listOf("e1" to DshApprovalOutcome.ALLOWED_ONCE), transport.answers)

        // 相同会话、工具与风险等级：自动走同一条官方回答路径，不重复展示卡片。
        transport.listener.onApproval(request("e2"))
        assertTrue(harness.controller.state.value.pending.isEmpty())
        assertEquals(2, transport.answers.size)
        assertEquals("e2" to DshApprovalOutcome.ALLOWED_ONCE, transport.answers[1])
    }

    @Test fun differentToolOrRiskIsNotAutoAnswered() {
        val harness = Harness()
        harness.controller.start()
        val transport = harness.transports.single()
        transport.listener.onApproval(request("e1"))
        harness.controller.answer("e1", DshApprovalOutcome.ALLOWED_ONCE, remember = true)

        transport.listener.onApproval(request("e2", toolName = "write"))
        assertEquals(1, harness.controller.state.value.pending.size)
        assertEquals("e2", harness.controller.state.value.pending.single().request.eventId)
        assertEquals(1, transport.answers.size)
    }

    @Test fun answerFailureKeepsPendingWithChineseError() {
        val harness = Harness()
        harness.controller.start()
        val transport = harness.transports.single()
        transport.answerError = IOException("官方审批回答失败")
        transport.listener.onApproval(request("e1", toolName = "write"))
        harness.controller.answer("e1", DshApprovalOutcome.REJECTED)
        val item = harness.controller.state.value.pending.single()
        assertFalse(item.answering)
        assertNotNull(item.error)
        assertTrue(item.error!!.contains("回答失败"))
    }

    @Test fun disconnectDisablesAnsweringAndClosesTransport() {
        val harness = Harness()
        harness.controller.start()
        val transport = harness.transports.single()
        transport.listener.onDisconnected("审批通道已断开")
        val state = harness.controller.state.value
        assertFalse(state.ready)
        assertFalse(state.connected)
        assertTrue(state.error!!.contains("断开"))
        assertTrue(transport.closed)
    }

    @Test fun originChangeReplacesOldTransport() {
        val harness = Harness()
        harness.controller.start()
        val first = harness.transports.single()
        harness.origin = "http://127.0.0.1:2"
        harness.controller.start()
        assertEquals(2, harness.transports.size)
        assertTrue(first.closed)
        assertFalse(harness.transports[1].closed)
        assertTrue(harness.controller.state.value.ready)
    }

    @Test fun disconnectDuringConnectDoesNotMarkConnected() {
        val harness = Harness()
        harness.connectBehavior = { listener -> listener.onDisconnected("审批通道已断开") }
        harness.controller.start()
        val state = harness.controller.state.value
        assertFalse(state.connected)
        assertFalse(state.ready)
        assertTrue(state.error!!.contains("断开"))
    }

    @Test fun closeClearsRememberedChoices() {
        val harness = Harness()
        harness.controller.start()
        val transport = harness.transports.single()
        transport.listener.onApproval(request("e1"))
        harness.controller.answer("e1", DshApprovalOutcome.ALLOWED_ONCE, remember = true)
        assertEquals(1, harness.controller.state.value.remembered.size)
        harness.controller.close()
        assertTrue(harness.controller.state.value.remembered.isEmpty())
        assertTrue(transport.closed)
    }

    private fun request(
        eventId: String,
        toolName: String = "bash",
        callId: String = "c1"
    ) = DshApprovalRequest(
        eventId = eventId,
        sessionId = "session-1",
        toolName = toolName,
        callId = callId,
        reason = "需要权限",
        displayReason = null
    )

    private class Harness {
        var origin = "http://127.0.0.1:1"
        var connectBehavior: ((DshApprovalSession.Listener) -> Unit)? = null
        val transports = mutableListOf<FakeTransport>()
        val controller = DshApprovalController(
            endpointProvider = { DshEndpoint(origin, "$origin/?token=t") },
            scope = CoroutineScope(Dispatchers.Unconfined),
            toolLookup = { callId -> toolItem(callId) },
            sessionFactory = { listener ->
                FakeTransport(listener).also {
                    it.connectBehavior = connectBehavior
                    transports += it
                }
            },
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    private class FakeTransport(val listener: DshApprovalSession.Listener) : DshApprovalTransport {
        val answers = mutableListOf<Pair<String, DshApprovalOutcome>>()
        var answerError: Throwable? = null
        var connectBehavior: ((DshApprovalSession.Listener) -> Unit)? = null
        var closed = false
        override fun connect() {
            connectBehavior?.invoke(listener) ?: listener.onReady()
        }
        override fun answer(eventId: String, outcome: DshApprovalOutcome): Result<Unit> {
            answerError?.let { return Result.failure(it) }
            answers += eventId to outcome
            return Result.success(Unit)
        }
        override fun close() {
            closed = true
        }
    }

    private companion object {
        fun toolItem(callId: String): DshToolItem = DshToolItem(
            seq = 1,
            time = 0L,
            callId = callId,
            name = "bash",
            arguments = """{"command":"ls"}""",
            status = DshToolStatus.RUNNING,
            output = null,
            error = null,
            command = "ls",
            cwd = null,
            diffs = emptyList(),
            files = emptyList(),
            outputTruncated = false,
            risk = DshRiskAssessment(DshRiskLevel.HIGH, "执行命令或进程操作")
        )
    }
}
