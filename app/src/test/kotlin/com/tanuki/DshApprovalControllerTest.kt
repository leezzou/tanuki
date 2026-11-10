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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test fun approvalLookupUsesRequestSessionAndUpdatesArguments() {
        val harness = Harness()
        harness.toolLookupResult = toolItem("c1", """{"command":"rm -rf /workspaces/tmp"}""")
        harness.controller.start()
        val transport = harness.transports.single()
        transport.listener.onApproval(request("e1", sessionId = "session-2"))
        assertEquals(listOf("session-2" to "c1"), harness.lookupCalls)
        val item = harness.controller.state.value.pending.single()
        assertEquals("""{"command":"rm -rf /workspaces/tmp"}""", item.arguments)
        assertEquals(DshRiskLevel.HIGH, item.risk.level)
        assertFalse(item.lookupPending)
    }

    @Test fun approvalFirstThenHistoryUpdatesAndAutoAnswersRemembered() {
        val harness = Harness()
        harness.controller.start()
        val transport = harness.transports.single()
        // 先建立 bash/HIGH 的本次运行记忆。
        transport.listener.onApproval(request("e1"))
        harness.controller.answer("e1", DshApprovalOutcome.ALLOWED_ONCE, remember = true)
        assertEquals(1, harness.controller.state.value.remembered.size)

        // 审批先到、历史尚未包含该 callId：先显示“参数未读取”，不自动放行。
        harness.toolLookupResult = null
        transport.listener.onApproval(request("e2"))
        val pending = harness.controller.state.value.pending.single()
        assertTrue(pending.lookupError!!.contains("未读取"))
        assertEquals(1, transport.answers.size)

        // 历史后到，由 42 推送工具调用；参数/风险更新后才按记忆自动回答。
        harness.controller.updateTool("session-1", toolItem("c1", """{"command":"ls"}"""))
        assertTrue(harness.controller.state.value.pending.isEmpty())
        assertEquals(2, transport.answers.size)
        assertEquals("e2" to DshApprovalOutcome.ALLOWED_ONCE, transport.answers[1])
    }

    @Test fun unknownApprovalCanOnlyBeRejected() {
        val harness = Harness()
        harness.controller.start()
        val transport = harness.transports.single()
        transport.listener.onApproval(request("e1", toolName = "未知审批", callId = null, unknownTool = true))
        val pending = harness.controller.state.value.pending.single()
        assertTrue(pending.request.unknownTool)
        assertEquals(DshRiskLevel.HIGH, pending.risk.level)

        harness.controller.answer("e1", DshApprovalOutcome.ALLOWED_ONCE)
        assertEquals(0, transport.answers.size)
        assertTrue(harness.controller.state.value.pending.single().error!!.contains("只能拒绝"))

        harness.controller.answer("e1", DshApprovalOutcome.REJECTED)
        assertTrue(harness.controller.state.value.pending.isEmpty())
        assertEquals(listOf("e1" to DshApprovalOutcome.REJECTED), transport.answers)
    }

    @Test fun reconnectRestartsPendingLookupAndIgnoresOldResult() {
        val harness = Harness()
        val deferreds = mutableListOf<CompletableDeferred<DshToolItem?>>()
        harness.lookup = { _, _ ->
            CompletableDeferred<DshToolItem?>().also { deferreds += it }.await()
        }
        harness.controller.start()
        val first = harness.transports.single()
        first.listener.onApproval(request("e1"))
        assertEquals(1, harness.lookupCalls.size)
        assertTrue(harness.controller.state.value.pending.single().lookupPending)

        first.listener.onDisconnected("审批通道已断开")
        harness.controller.start()
        assertEquals(2, harness.lookupCalls.size)
        assertTrue(harness.controller.state.value.ready)

        deferreds[1].complete(toolItem("c1", """{"command":"ls"}"""))
        val item = harness.controller.state.value.pending.single()
        assertFalse(item.lookupPending)
        assertEquals("""{"command":"ls"}""", item.arguments)

        // 旧代次查询即使完成也不能覆盖新连接的结果。
        deferreds[0].complete(toolItem("c1", """{"command":"rm -rf /"}"""))
        assertEquals("""{"command":"ls"}""", harness.controller.state.value.pending.single().arguments)
    }

    @Test fun unsupportedWaterfallIsAnsweredNextWithoutApprovalCard() {
        val harness = Harness()
        harness.controller.start()
        val transport = harness.transports.single()
        transport.listener.onUnsupportedWaterfall("question-1")
        assertTrue(harness.controller.state.value.pending.isEmpty())
        assertEquals(listOf("question-1"), transport.nextAnswers)
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

    @Test fun disconnectDuringAnswerReleasesMarkerAndAllowsRetryAfterReconnect() {
        val harness = Harness()
        harness.controller.start()
        val first = harness.transports.single()
        first.listener.onApproval(request("e1"))
        first.answerBehavior = {
            first.listener.onDisconnected("审批通道已断开")
            Result.failure(IOException("审批通道已断开"))
        }
        harness.controller.answer("e1", DshApprovalOutcome.ALLOWED_ONCE)
        val item = harness.controller.state.value.pending.single()
        assertFalse(item.answering)
        assertTrue(item.error!!.contains("断开"))

        // 重连后同一 eventId 必须还能回答，不能被遗留的 answering 标记吞掉。
        harness.controller.start()
        val second = harness.transports[1]
        assertTrue(harness.controller.state.value.ready)
        harness.controller.answer("e1", DshApprovalOutcome.REJECTED)
        assertTrue(harness.controller.state.value.pending.isEmpty())
        assertEquals(listOf("e1" to DshApprovalOutcome.REJECTED), second.answers)
    }

    @Test fun originChangeReplacesOldTransportAndReportsDroppedApproval() {
        val harness = Harness()
        harness.controller.start()
        val first = harness.transports.single()
        first.listener.onApproval(request("e1"))
        harness.origin = "http://127.0.0.1:2"
        harness.controller.start()
        assertEquals(2, harness.transports.size)
        assertTrue(first.closed)
        assertFalse(harness.transports[1].closed)
        assertTrue(harness.controller.state.value.ready)
        assertTrue(harness.controller.state.value.pending.isEmpty())
        assertTrue(harness.controller.state.value.notice!!.contains("1 个旧待审批"))
        harness.controller.dismissNotice()
        assertNull(harness.controller.state.value.notice)
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

    @Test fun rememberedChoicesKeepPanelVisibleUntilCleared() {
        val harness = Harness()
        harness.controller.start()
        val transport = harness.transports.single()
        transport.listener.onApproval(request("e1"))
        harness.controller.answer("e1", DshApprovalOutcome.ALLOWED_ONCE, remember = true)
        assertTrue(harness.controller.state.value.pending.isEmpty())
        assertTrue(harness.controller.state.value.shouldShowPanel())
        harness.controller.clearRemembered()
        assertFalse(harness.controller.state.value.shouldShowPanel())
    }

    @Test fun forgettingRememberedChoiceStopsAutoAnswerImmediately() {
        val harness = Harness()
        harness.controller.start()
        val transport = harness.transports.single()
        transport.listener.onApproval(request("e1"))
        harness.controller.answer("e1", DshApprovalOutcome.ALLOWED_ONCE, remember = true)
        val choice = harness.controller.state.value.remembered.single()

        harness.controller.forgetRemembered(choice)
        assertTrue(harness.controller.state.value.remembered.isEmpty())
        transport.listener.onApproval(request("e2"))
        assertEquals("e2", harness.controller.state.value.pending.single().request.eventId)
        assertEquals(1, transport.answers.size)
    }

    @Test fun disconnectKeepsPendingAndRetryCanAnswerSameEvent() {
        val harness = Harness()
        harness.controller.start()
        val first = harness.transports.single()
        first.listener.onApproval(request("e1"))
        first.listener.onDisconnected("审批通道已断开")
        val state = harness.controller.state.value
        assertEquals(1, state.pending.size)
        assertTrue(state.errorRetryable)
        assertFalse(state.ready)

        harness.controller.retry()
        val second = harness.transports[1]
        assertTrue(harness.controller.state.value.ready)
        assertEquals(1, harness.controller.state.value.pending.size)
        harness.controller.answer("e1", DshApprovalOutcome.REJECTED)
        assertTrue(harness.controller.state.value.pending.isEmpty())
        assertEquals(listOf("e1" to DshApprovalOutcome.REJECTED), second.answers)
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
        callId: String? = "c1",
        sessionId: String = "session-1",
        unknownTool: Boolean = false
    ) = DshApprovalRequest(
        eventId = eventId,
        sessionId = sessionId,
        toolName = toolName,
        callId = callId,
        reason = "需要权限",
        displayReason = null,
        unknownTool = unknownTool
    )

    private class Harness {
        var origin = "http://127.0.0.1:1"
        var connectBehavior: ((DshApprovalSession.Listener) -> Unit)? = null
        var toolLookupResult: DshToolItem? = toolItem("c1")
        var lookup: (suspend (String, String) -> DshToolItem?)? = null
        val lookupCalls = mutableListOf<Pair<String, String>>()
        val transports = mutableListOf<FakeTransport>()
        val controller = DshApprovalController(
            endpointProvider = { DshEndpoint(origin, "$origin/?token=t") },
            scope = CoroutineScope(Dispatchers.Unconfined),
            toolLookup = { sessionId, callId ->
                lookupCalls += sessionId to callId
                lookup?.invoke(sessionId, callId) ?: toolLookupResult?.takeIf { it.callId == callId }
            },
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
        val nextAnswers = mutableListOf<String>()
        var answerError: Throwable? = null
        var answerBehavior: (() -> Result<Unit>)? = null
        var connectBehavior: ((DshApprovalSession.Listener) -> Unit)? = null
        var closed = false
        override fun connect() {
            connectBehavior?.invoke(listener) ?: listener.onReady()
        }
        override fun answer(eventId: String, outcome: DshApprovalOutcome): Result<Unit> {
            answerBehavior?.let { return it() }
            answerError?.let { return Result.failure(it) }
            answers += eventId to outcome
            return Result.success(Unit)
        }
        override fun answerNext(eventId: String): Result<Unit> {
            nextAnswers += eventId
            return Result.success(Unit)
        }
        override fun close() {
            closed = true
        }
    }

    private companion object {
        fun toolItem(callId: String, arguments: String = """{"command":"ls"}"""): DshToolItem = DshToolItem(
            seq = 1,
            time = 0L,
            callId = callId,
            name = "bash",
            arguments = arguments,
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
