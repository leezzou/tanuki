package com.tanuki

import com.tanuki.ui.DshNativeController
import com.tanuki.web.ChatNotificationDecision
import com.tanuki.web.ChatNotificationRoute
import com.tanuki.web.ChatNotificationTargetPolicy
import com.tanuki.web.DshMessage
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshSession
import com.tanuki.web.DshSessionPage
import com.tanuki.web.DshRiskAssessment
import com.tanuki.web.DshRiskLevel
import com.tanuki.web.DshToolItem
import com.tanuki.web.DshToolStatus
import com.tanuki.web.DshTimelineItem
import com.tanuki.web.DshChatSource
import com.tanuki.web.DshLiveEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DshNativeControllerTest {
    @Test fun coldStartNotificationTargetSelectsTargetSession() {
        val source = FakeSource(listOf(session("A"), session("B")))
        val controller = controller(source)
        controller.openSession("B")
        val state = awaitSelection(controller, "B")
        assertEquals("B", state.selectedId)
        assertEquals(listOf("message-B"), state.messages.map { it.text })
    }

    @Test fun loadedNativePageNotificationTargetReplacesCurrentSelection() {
        val source = FakeSource(listOf(session("A"), session("B")))
        val controller = controller(source)
        controller.refresh()
        awaitSelection(controller, "A")
        controller.openSession("B")
        val state = awaitSelection(controller, "B")
        assertEquals("B", state.selectedId)
        assertEquals(listOf("message-B"), state.messages.map { it.text })
    }

    @Test fun nativeListFailureLeavesControllerUnavailableForOfficialFallback() {
        val source = FakeSource(listOf(session("A")), failSessions = true)
        val controller = controller(source)
        controller.openSession("B")
        val state = runBlocking {
            withTimeout(2_000) { controller.state.first { it.unavailableReason != null } }
        }
        assertNull(state.selectedId)
        assertTrue(state.unavailableReason!!.contains("DSH 接口"))
    }

    @Test fun notificationDecisionForSelectedSessionForcesReloadBeforeConsuming() {
        val source = FakeSource(listOf(session("A")), messages = mapOf("A" to "message-A-old"))
        val controller = controller(source)
        controller.refresh()
        awaitSelection(controller, "A")
        source.setMessage("A", "message-A-new")

        val before = controller.state.value
        var requestIssued = false
        // 通知到达前 A 已选中且空闲，但本次通知尚未发起刷新：必须请求原生强制重载。
        assertEquals(
            ChatNotificationDecision.REQUEST_NATIVE,
            ChatNotificationTargetPolicy.decide(
                "A", ChatNotificationRoute.NATIVE, before.selectedId, before.loading,
                nativeUnavailable = false, nativeRequestIssued = requestIssued
            )
        )
        requestIssued = true
        controller.openSession("A")
        val after = runBlocking {
            withTimeout(2_000) {
                controller.state.first { it.selectedId == "A" && !it.loading && it.messages.any { m -> m.text == "message-A-new" } }
            }
        }
        assertEquals(listOf("message-A-new"), after.messages.map { it.text })
        assertEquals(
            ChatNotificationDecision.CONSUMED,
            ChatNotificationTargetPolicy.decide(
                "A", ChatNotificationRoute.NATIVE, after.selectedId, after.loading,
                nativeUnavailable = false, nativeRequestIssued = requestIssued
            )
        )
    }

    @Test fun notificationForCurrentSessionReloadsNewHistory() {
        val source = FakeSource(listOf(session("A")), messages = mapOf("A" to "message-A-old"))
        val controller = controller(source)
        controller.refresh()
        awaitSelection(controller, "A")
        source.setMessage("A", "message-A-new")
        controller.openSession("A")
        val state = runBlocking {
            withTimeout(2_000) {
                controller.state.first { it.selectedId == "A" && !it.loading && it.messages.any { m -> m.text == "message-A-new" } }
            }
        }
        assertEquals(listOf("message-A-new"), state.messages.map { it.text })
        assertFalse(state.messages.any { it.text == "message-A-old" })
    }

    @Test fun notificationTargetMissingFromListIsStillSelectedAndShown() {
        val source = FakeSource(listOf(session("A")))
        val controller = controller(source)
        controller.openSession("C")
        val state = awaitSelection(controller, "C")
        assertEquals("C", state.selectedId)
        assertTrue(state.sessions.any { it.id == "C" })
        assertEquals(listOf("message-C"), state.messages.map { it.text })
    }


    @Test fun sendKeepsPendingBubbleUntilOfficialMessageAndClearsOnSuccess() = runBlocking {
        val source = FakeSource(listOf(session("A")))
        val controller = controller(source)
        controller.refresh()
        awaitSelection(controller, "A")
        assertTrue(controller.send("你好 DSH"))
        val state = controller.state.value
        assertTrue(state.messages.any { it.pending && it.text == "你好 DSH" })
        assertFalse(state.sending)
        assertTrue(state.notice!!.contains("官方"))
    }

    @Test fun sendFailureRemovesPendingBubbleAndShowsOfficialCode() = runBlocking {
        val source = FakeSource(listOf(session("A")), sendFailure = com.tanuki.web.DshApiException("session/agent-busy"))
        val controller = controller(source)
        controller.refresh()
        awaitSelection(controller, "A")
        assertFalse(controller.send("会失败"))
        val state = controller.state.value
        assertFalse(state.messages.any { it.pending })
        assertTrue(state.sendError!!.contains("session/agent-busy"))
        assertFalse(state.running)
    }

    @Test fun stopAsksOfficialCancelWithoutStoppingNativePage() = runBlocking {
        val source = FakeSource(listOf(session("A")))
        val controller = controller(source)
        controller.refresh()
        awaitSelection(controller, "A")
        assertTrue(controller.stop())
        val state = controller.state.value
        assertTrue(state.notice!!.contains("停止"))
        assertFalse(state.stopping)
    }

    @Test fun liveSnapshotAndStreamingTextAreVisible() = runBlocking {
        val source = FakeSource(
            listOf(session("A")),
            liveEvents = listOf(
                DshLiveEvent.Connected,
                DshLiveEvent.Snapshot(
                    messages = listOf(DshMessage(5, DshMessageRole.ASSISTANT, "已收到", 2L)),
                    hasMore = false,
                    liveText = ""
                ),
                DshLiveEvent.Streaming("正在"),
                DshLiveEvent.Streaming("正在回复")
            )
        )
        val controller = controller(source)
        controller.refresh()
        // 流式文本按帧合并，断言最终发布的完整文本而不是某一次中间事件。
        val state = awaitState(controller) { it.streamingText == "正在回复" }
        assertEquals("已收到", state.messages.last().text)
        assertEquals("正在回复", controller.state.value.streamingText)
    }

    @Test fun collapsingManyStreamingDeltasKeepsOnlyLatestFrame() = runBlocking {
        val events = ArrayList<DshLiveEvent>()
        events += DshLiveEvent.Connected
        events += DshLiveEvent.Snapshot(emptyList(), hasMore = false, liveText = "")
        for (index in 1..200) events += DshLiveEvent.Streaming("增量".repeat(index))
        val source = FakeSource(listOf(session("A")), liveEvents = events)
        val controller = controller(source)
        controller.refresh()
        val state = awaitState(controller) { it.streamingText == "增量".repeat(200) }
        assertEquals("增量".repeat(200), state.streamingText)
        assertEquals(400, state.streamingText.length)
    }

    @Test fun committedAssistantMessageCancelsPendingStreamFlush() = runBlocking {
        val source = FakeSource(
            listOf(session("A")),
            liveEvents = listOf(
                DshLiveEvent.Connected,
                DshLiveEvent.Snapshot(emptyList(), hasMore = false, liveText = ""),
                DshLiveEvent.Streaming("最终回复"),
                DshLiveEvent.Message(DshMessage(9, DshMessageRole.ASSISTANT, "最终回复", 2L))
            )
        )
        val controller = controller(source)
        controller.refresh()
        val state = awaitState(controller) { it.messages.any { message -> message.text == "最终回复" } }
        assertEquals(1, state.messages.size)
        assertEquals("", state.streamingText)
        // 给延迟刷新的旧任务留出时间；它不能把已落盘消息再插入成流式气泡。
        kotlinx.coroutines.delay(120)
        assertEquals("", controller.state.value.streamingText)
        assertEquals(1, controller.state.value.messages.size)
    }

    @Test fun sendFailureKeepsPromptForRetryAndCopy() = runBlocking {
        val source = FakeSource(listOf(session("A")))
        source.setSendFailure(com.tanuki.web.DshApiException("session/agent-busy"))
        val controller = controller(source)
        controller.refresh()
        awaitSelection(controller, "A")
        assertFalse(controller.send("需要重试的原文"))
        val failed = controller.state.value.failedPrompt
        assertTrue(failed != null)
        assertEquals("需要重试的原文", failed!!.text)
        assertTrue(failed.reason.contains("session/agent-busy"))
        assertFalse(controller.state.value.messages.any { it.pending })

        source.setSendFailure(null)
        assertTrue(controller.retryFailedPrompt())
        assertNull(controller.state.value.failedPrompt)
        // 重发成功后官方落盘前仍保留“发送中”占位，由 follow/历史页替换。
        assertTrue(controller.state.value.messages.any { it.pending })
    }

    @Test fun switchingSessionClearsFailedPromptCard() = runBlocking {
        val source = FakeSource(listOf(session("A"), session("B")))
        source.setSendFailure(com.tanuki.web.DshApiException("session/agent-busy"))
        val controller = controller(source)
        controller.refresh()
        awaitSelection(controller, "A")
        assertFalse(controller.send("失败内容"))
        assertTrue(controller.state.value.failedPrompt != null)
        controller.select("B", force = true)
        val state = awaitSelection(controller, "B")
        assertNull(state.failedPrompt)
        assertNull(state.sendError)
    }

    @Test fun liveToolSnapshotAndResultAreMergedByCallId() = runBlocking {
        val running = toolItem("call-1", DshToolStatus.RUNNING, 7)
        val completed = toolItem("call-1", DshToolStatus.SUCCESS, 7, output = "完成")
        val source = FakeSource(
            listOf(session("A")),
            liveEvents = listOf(
                DshLiveEvent.Connected,
                DshLiveEvent.Snapshot(emptyList(), hasMore = false, liveText = "", tools = listOf(running)),
                DshLiveEvent.Timeline(listOf(completed))
            )
        )
        val controller = controller(source)
        controller.refresh()
        val state = awaitState(controller) { it.tools.singleOrNull()?.status == DshToolStatus.SUCCESS }
        assertEquals("call-1", state.tools.single().callId)
        assertEquals("Bash", state.tools.single().name)
        assertEquals("完成", state.tools.single().output)
    }

    @Test fun liveFailureDoesNotForceOfficialFallback() = runBlocking {
        val source = FakeSource(
            listOf(session("A")),
            liveEvents = listOf(DshLiveEvent.Failure("实时连接失败"))
        )
        val controller = controller(source)
        controller.refresh()
        val state = awaitState(controller) { it.liveError != null }
        assertNull(state.unavailableReason)
        assertTrue(state.sessions.any { it.id == "A" })
    }

    @Test fun delayedSendSuccessCannotRefreshAnotherSelectedSession() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val source = FakeSource(
            listOf(session("A"), session("B")),
            messages = mapOf("A" to "A-message", "B" to "B-message"),
            sendGate = gate
        )
        val controller = controller(source)
        controller.refresh()
        awaitSelection(controller, "A")
        val job = launch { controller.send("A prompt") }
        awaitState(controller) { it.sending }
        controller.select("B", force = true)
        awaitSelection(controller, "B")
        gate.complete(Unit)
        job.join()

        val state = controller.state.value
        assertEquals("B", state.selectedId)
        assertEquals(listOf("B-message"), state.messages.map { it.text })
        assertFalse(state.messages.any { it.text == "A prompt" })
        assertNull(state.sendError)
    }

    @Test fun delayedSendFailureCannotOverwriteAnotherSelectedSession() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val source = FakeSource(
            listOf(session("A"), session("B")),
            messages = mapOf("A" to "A-message", "B" to "B-message"),
            sendGate = gate,
            sendFailure = com.tanuki.web.DshApiException("session/agent-busy")
        )
        val controller = controller(source)
        controller.refresh()
        awaitSelection(controller, "A")
        val job = launch { controller.send("A prompt") }
        awaitState(controller) { it.sending }
        controller.select("B", force = true)
        awaitSelection(controller, "B")
        gate.complete(Unit)
        job.join()

        val state = controller.state.value
        assertEquals("B", state.selectedId)
        assertEquals(listOf("B-message"), state.messages.map { it.text })
        assertNull(state.sendError)
        assertFalse(state.running)
    }

    @Test fun retryResendsLastUserPromptOnlyWhenIdle() = runBlocking {
        val source = FakeSource(
            listOf(session("A")),
            messages = mapOf("A" to "上一条用户消息"),
            sendGate = CompletableDeferred<Unit>().also { it.complete(Unit) }
        )
        val controller = controller(source)
        controller.refresh()
        awaitSelection(controller, "A")
        assertTrue(controller.retryLastPrompt())
        assertTrue(controller.state.value.messages.any { it.text == "上一条用户消息" })
        assertTrue(controller.state.value.notice!!.contains("官方"))
    }

    private fun controller(source: DshChatSource): DshNativeController =
        DshNativeController(source, CoroutineScope(Dispatchers.Unconfined))

    private suspend fun awaitState(
        controller: DshNativeController,
        predicate: (com.tanuki.ui.DshNativeState) -> Boolean
    ): com.tanuki.ui.DshNativeState = withTimeout(2_000) {
        controller.state.first(predicate)
    }

    private fun awaitSelection(controller: DshNativeController, id: String) = runBlocking {
        withTimeout(2_000) {
            controller.state.first { it.selectedId == id && !it.loading }
        }
    }

    private fun session(id: String): DshSession = DshSession(
        id = id,
        title = "会话 $id",
        updatedAt = 0L,
        running = false,
        blank = false,
        agentAvailable = false,
        asOfSeq = 12
    )

    private class FakeSource(
        private val items: List<DshSession>,
        private val failSessions: Boolean = false,
        messages: Map<String, String> = emptyMap(),
        private val liveEvents: List<DshLiveEvent> = emptyList(),
        private var sendFailure: Throwable? = null,
        private val sendGate: CompletableDeferred<Unit>? = null,
        private val pageGate: CompletableDeferred<Unit>? = null,
        private val timelines: Map<String, List<DshTimelineItem>> = emptyMap()
    ) : DshChatSource {
        private val current = messages.toMutableMap()

        fun setMessage(sessionId: String, text: String) {
            current[sessionId] = text
        }

        fun setSendFailure(error: Throwable?) {
            sendFailure = error
        }

        override suspend fun sessions(): Result<List<DshSession>> =
            if (failSessions) Result.failure(IOException("DSH 接口认证失败")) else Result.success(items)

        override suspend fun messages(sessionId: String): Result<DshSessionPage> {
            pageGate?.await()
            return Result.success(
                DshSessionPage(
                    messages = listOf(
                        DshMessage(
                            seq = 1,
                            role = DshMessageRole.USER,
                            text = current[sessionId] ?: "message-$sessionId",
                            time = 1L
                        )
                    ),
                    hasMore = false,
                    timeline = timelines[sessionId].orEmpty()
                )
            )
        }

        override suspend fun send(sessionId: String, text: String): Result<Unit> {
            sendGate?.await()
            return sendFailure?.let { Result.failure(it) } ?: Result.success(Unit)
        }

        override suspend fun cancel(sessionId: String): Result<Unit> = Result.success(Unit)

        override fun live(sessionId: String): Flow<DshLiveEvent> =
            if (liveEvents.isEmpty()) emptyFlow() else flowOf(*liveEvents.toTypedArray())
    }

    @Test fun lateHistoryPageCannotRegressCompletedTool() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val running = toolItem("call-1", DshToolStatus.RUNNING, 7)
        val completed = toolItem("call-1", DshToolStatus.SUCCESS, 7, output = "完成")
        val source = FakeSource(
            listOf(session("A")),
            messages = mapOf("A" to "历史页"),
            liveEvents = listOf(
                DshLiveEvent.Connected,
                DshLiveEvent.Snapshot(
                    messages = listOf(DshMessage(1, DshMessageRole.USER, "实时页", 1L)),
                    hasMore = false,
                    liveText = "",
                    tools = listOf(running)
                ),
                DshLiveEvent.Timeline(listOf(completed))
            ),
            pageGate = gate,
            timelines = mapOf("A" to listOf(DshTimelineItem.Tool(running)))
        )
        val controller = controller(source)
        controller.refresh()
        awaitState(controller) { it.tools.singleOrNull()?.status == DshToolStatus.SUCCESS }
        gate.complete(Unit)
        val state = awaitState(controller) { state ->
            !state.loading && state.messages.any { it.text == "历史页" }
        }
        assertEquals(DshToolStatus.SUCCESS, state.tools.single().status)
        assertEquals("完成", state.tools.single().output)
    }

    private fun toolItem(
        callId: String,
        status: DshToolStatus,
        seq: Int,
        output: String? = null
    ) = DshToolItem(
        seq = seq,
        time = 1L,
        callId = callId,
        name = "Bash",
        arguments = "{\"command\":\"ls\"}",
        status = status,
        output = output,
        error = null,
        command = "ls",
        cwd = "/tmp",
        diffs = emptyList(),
        files = emptyList(),
        outputTruncated = false,
        risk = DshRiskAssessment(DshRiskLevel.LOW, "只读命令")
    )
}
