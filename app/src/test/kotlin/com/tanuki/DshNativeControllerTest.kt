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
import com.tanuki.web.DshCoordination
import com.tanuki.web.DshGoalProjection
import com.tanuki.web.DshLiveEvent
import com.tanuki.web.DshTodoItemProjection
import com.tanuki.web.DshTodoProjection
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


    @Test fun composerAcceptanceReconcilesInPlaceWithoutRestartingLiveOrClearingHistory() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var holdPage = false
        var reads = 0
        var liveStarts = 0
        val tool = toolItem("kept-tool", DshToolStatus.RUNNING, 2)
        val source = object : DshChatSource by FakeSource(listOf(session("A"), session("B"))) {
            override suspend fun messages(sessionId: String): Result<DshSessionPage> {
                reads++
                if (holdPage) gate.await()
                return Result.success(DshSessionPage(listOf(
                    DshMessage(1, DshMessageRole.USER, "当前消息", 1L)
                ), false))
            }
            override fun live(sessionId: String): Flow<DshLiveEvent> = kotlinx.coroutines.flow.flow {
                liveStarts++
                emit(DshLiveEvent.Connected)
                emit(DshLiveEvent.Snapshot(listOf(
                    DshMessage(0, DshMessageRole.USER, "已加载的更早历史", 0L),
                    DshMessage(1, DshMessageRole.USER, "当前消息", 1L)
                ), true, "正在生成的前缀", listOf(tool)))
                kotlinx.coroutines.awaitCancellation()
            }
        }
        val controller = controller(source)
        controller.refresh()
        val before = awaitState(controller) { !it.loading && it.live && it.streamingText.isNotEmpty() }
        holdPage = true
        controller.onPromptAccepted("A")
        assertTrue(controller.state.value.loading)
        assertEquals(before.messages, controller.state.value.messages)
        assertEquals(before.tools, controller.state.value.tools)
        assertEquals(before.streamingText, controller.state.value.streamingText)
        assertEquals(1, liveStarts)
        gate.complete(Unit)
        val reconciled = awaitState(controller) { !it.loading }
        assertEquals(before.messages, reconciled.messages)
        assertTrue(reconciled.hasMore)
        assertEquals(before.tools, reconciled.tools)
        assertEquals(1, liveStarts)
        controller.select("B")
        awaitSelection(controller, "B")
        val readsBeforeStaleReceipt = reads
        controller.onPromptAccepted("A")
        assertEquals(readsBeforeStaleReceipt, reads)
        assertEquals("B", controller.state.value.selectedId)
        controller.close()
    }

    @Test fun terminalFailureRestoresFollowEvenWhileFlowIsStillOpen() = runBlocking {
        val events = kotlinx.coroutines.channels.Channel<DshLiveEvent>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        var starts = 0
        val source = object : DshChatSource by FakeSource(listOf(session("A"))) {
            override fun live(sessionId: String): Flow<DshLiveEvent> = kotlinx.coroutines.flow.flow {
                starts++
                emit(DshLiveEvent.Connected)
                if (starts == 1) for (event in events) emit(event) else kotlinx.coroutines.awaitCancellation()
            }
        }
        val controller = controller(source)
        try {
            controller.refresh()
            awaitState(controller) { it.live && !it.loading }
            events.send(DshLiveEvent.Failure("普通Agent业务错误"))
            awaitState(controller) { it.liveError == "普通Agent业务错误" }
            controller.onPromptAccepted("A")
            assertEquals(1, starts)
            assertTrue(controller.state.value.live)
            events.send(DshLiveEvent.Failure("follow终止", terminal = true))
            awaitState(controller) { !it.live && it.liveError == "follow终止" }
            controller.onPromptAccepted("A")
            awaitState(controller) { it.live && it.liveError == null }
            assertEquals(2, starts)
        } finally { controller.close(); events.close() }
    }

    @Test fun composerAcceptanceRestoresFailedLiveWithoutClearingHistory() =
        verifyAcceptanceRestoresEndedLive(failFirstLive = true)

    @Test fun composerAcceptanceRestoresNormallyEndedLiveWithoutClearingHistory() =
        verifyAcceptanceRestoresEndedLive(failFirstLive = false)

    private fun verifyAcceptanceRestoresEndedLive(failFirstLive: Boolean) = runBlocking {
        val endFirst = CompletableDeferred<Unit>()
        val connectAgain = CompletableDeferred<Unit>()
        val historyGate = CompletableDeferred<Unit>()
        val events = kotlinx.coroutines.channels.Channel<DshLiveEvent>(kotlinx.coroutines.channels.Channel.UNLIMITED)
        val starts = mutableListOf<String>()
        var holdHistory = false
        var reads = 0
        val runningTool = toolItem("kept-tool", DshToolStatus.RUNNING, 2)
        val source = object : DshChatSource by FakeSource(listOf(session("A"), session("B"))) {
            override suspend fun messages(sessionId: String): Result<DshSessionPage> {
                reads++
                if (holdHistory) historyGate.await()
                return Result.success(DshSessionPage(listOf(DshMessage(1, DshMessageRole.USER, "当前消息", 1L)), false))
            }
            override fun live(sessionId: String): Flow<DshLiveEvent> = kotlinx.coroutines.flow.flow {
                starts += sessionId
                if (starts.size == 1) {
                    emit(DshLiveEvent.Connected)
                    emit(DshLiveEvent.Snapshot(listOf(
                        DshMessage(0, DshMessageRole.USER, "已加载的更早历史", 0L),
                        DshMessage(1, DshMessageRole.USER, "当前消息", 1L)
                    ), true, "断流前的前缀", listOf(runningTool)))
                    endFirst.await()
                    if (failFirstLive) throw IOException("合成连接中断")
                } else if (sessionId == "B") {
                    emit(DshLiveEvent.Connected)
                    kotlinx.coroutines.awaitCancellation()
                } else {
                    // 刻意保持重连尚未 ready；连续受理也只能保留这一条在途连接。
                    connectAgain.await()
                    emit(DshLiveEvent.Connected)
                    for (event in events) emit(event)
                }
            }
        }
        val controller = controller(source)
        try {
            controller.refresh()
            val before = awaitState(controller) { !it.loading && it.live && it.streamingText.isNotEmpty() }
            endFirst.complete(Unit)
            assertFalse(controller.state.value.live)
            if (failFirstLive) assertTrue(controller.state.value.liveError.orEmpty().contains("合成连接中断"))
            holdHistory = true
            controller.onPromptAccepted("A")
            controller.onPromptAccepted("A")
            assertEquals(listOf("A", "A"), starts)
            assertEquals(before.messages, controller.state.value.messages)
            assertEquals(before.tools, controller.state.value.tools)
            assertEquals(before.streamingText, controller.state.value.streamingText)
            connectAgain.complete(Unit)
            val completedTool = toolItem("kept-tool", DshToolStatus.SUCCESS, 2, output = "完成")
            events.send(DshLiveEvent.Running(true))
            events.send(DshLiveEvent.Timeline(listOf(completedTool)))
            events.send(DshLiveEvent.Message(DshMessage(3, DshMessageRole.ASSISTANT, "恢复后的正文", 3L)))
            events.send(DshLiveEvent.Streaming("恢复后的流式文本"))
            val resumed = awaitState(controller) { it.live && it.running && it.streamingText == "恢复后的流式文本" }
            assertNull(resumed.liveError)
            assertEquals(DshToolStatus.SUCCESS, resumed.tools.single().status)
            assertTrue(resumed.messages.any { it.seq == 0 })
            assertTrue(resumed.messages.any { it.text == "恢复后的正文" })
            historyGate.complete(Unit)
            awaitState(controller) { !it.loading }
            assertTrue(controller.state.value.hasMore)
            assertEquals(DshToolStatus.SUCCESS, controller.state.value.tools.single().status)
            events.send(DshLiveEvent.Running(false))
            awaitState(controller) { !it.running }
            assertEquals(listOf("A", "A"), starts)
            holdHistory = false
            controller.select("B")
            awaitSelection(controller, "B")
            val readsBeforeLateAcceptance = reads
            controller.onPromptAccepted("A")
            assertEquals(listOf("A", "A", "B"), starts)
            assertEquals(readsBeforeLateAcceptance, reads)
            assertEquals("B", controller.state.value.selectedId)
        } finally { controller.close(); events.close() }
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
        assertEquals("正在结束本次回复", state.notice)
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

    @Test fun newStreamResetWithinOneFrameDropsSnapshotPrefix() = runBlocking {
        val source = FakeSource(
            listOf(session("A")),
            liveEvents = listOf(
                DshLiveEvent.Connected,
                DshLiveEvent.Snapshot(emptyList(), hasMore = false, liveText = "历史前缀"),
                DshLiveEvent.Streaming(""),
                DshLiveEvent.Streaming("回答")
            )
        )
        val controller = controller(source)
        controller.refresh()
        val state = awaitState(controller) { it.streamingText == "回答" }
        assertEquals("回答", state.streamingText)
        assertFalse(state.streamingText.contains("历史前缀"))
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
        // 空快照不能清掉 HTTP 历史页；落盘消息只出现一次，且不残留流式气泡。
        assertEquals(1, state.messages.count { it.text == "最终回复" })
        assertEquals("", state.streamingText)
        // 给延迟刷新的旧任务留出时间；它不能把已落盘消息再插入成流式气泡。
        kotlinx.coroutines.delay(120)
        assertEquals("", controller.state.value.streamingText)
        assertEquals(1, controller.state.value.messages.count { it.text == "最终回复" })
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

    @Test fun liveToolMergeKeepsStartedAndFinishedTimestamps() = runBlocking {
        val running = toolItem("call-time", DshToolStatus.RUNNING, 7, startedAt = 100L)
        val completed = toolItem(
            "call-time", DshToolStatus.SUCCESS, 7,
            output = "完成", startedAt = 0L, finishedAt = 250L
        )
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
        val state = awaitState(controller) { it.tools.singleOrNull()?.finishedAt == 250L }
        assertEquals(100L, state.tools.single().startedAt)
        assertEquals(250L, state.tools.single().finishedAt)
        assertEquals(150L, state.tools.single().durationMs)
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

    @Test fun renameUsesOfficialReturnedTitleAndKeepsMessages() = runBlocking {
        val requests = mutableListOf<Pair<String, String>>()
        val controller = DshNativeController(
            FakeSource(listOf(session("A"))), CoroutineScope(Dispatchers.Unconfined),
            renameRemote = { id, title -> requests += id to title; Result.success("官方标题") }
        )
        controller.refresh()
        val original = awaitSelection(controller, "A").messages
        assertTrue(controller.renameSession("A", "  新标题  ").isSuccess)
        assertEquals(listOf("A" to "新标题"), requests)
        assertEquals("官方标题", controller.state.value.selectedSession!!.displayTitle)
        assertEquals(original, controller.state.value.messages)
    }

    @Test fun renameFailureKeepsOriginalTitleAndRejectsInvalidInput() = runBlocking {
        var calls = 0
        val controller = DshNativeController(
            FakeSource(listOf(session("A"))), CoroutineScope(Dispatchers.Unconfined),
            renameRemote = { _, _ -> calls++; Result.failure(IOException("网络不可用")) }
        )
        controller.refresh()
        awaitSelection(controller, "A")
        assertTrue(controller.renameSession("A", " ").isFailure)
        assertTrue(controller.renameSession("A", "x".repeat(201)).isFailure)
        assertTrue(controller.renameSession("A", "带\n换行").isFailure)
        assertEquals(0, calls)
        assertTrue(controller.renameSession("A", "新标题").isFailure)
        assertEquals(1, calls)
        assertEquals("会话 A", controller.state.value.selectedSession!!.displayTitle)
    }

    @Test fun lateRenameCannotUpdateAnotherSession() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val controller = DshNativeController(
            FakeSource(listOf(session("A"), session("B"))), CoroutineScope(Dispatchers.Unconfined),
            renameRemote = { _, _ -> gate.await(); Result.success("仅 A 的新标题") }
        )
        controller.refresh()
        awaitSelection(controller, "A")
        val request = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            controller.renameSession("A", "修改 A")
        }
        controller.select("B")
        awaitSelection(controller, "B")
        gate.complete(Unit)
        request.join()
        assertEquals("B", controller.state.value.selectedId)
        assertEquals("会话 B", controller.state.value.selectedSession!!.displayTitle)
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
        private val timelines: Map<String, List<DshTimelineItem>> = emptyMap(),
        private val coordinations: Map<String, DshCoordination> = emptyMap()
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
                    timeline = timelines[sessionId].orEmpty(),
                    coordination = coordinations[sessionId] ?: DshCoordination()
                )
            )
        }

        // 本测试类不覆盖向前分页；分页用例在 DshNativePagingTest。
        override suspend fun messagesBefore(
            sessionId: String,
            beforeSeq: Int,
            maxMessages: Int
        ): Result<DshSessionPage> = Result.success(DshSessionPage(emptyList(), false))

        override suspend fun send(sessionId: String, text: String): Result<Unit> {
            sendGate?.await()
            return sendFailure?.let { Result.failure(it) } ?: Result.success(Unit)
        }

        override suspend fun cancel(sessionId: String): Result<Unit> = Result.success(Unit)

        override fun live(sessionId: String): Flow<DshLiveEvent> =
            if (liveEvents.isEmpty()) emptyFlow() else flowOf(*liveEvents.toTypedArray())
    }

    @Test fun historyPageCoordinationDrivesGoalAndTodoDocks() = runBlocking {
        val source = FakeSource(
            listOf(session("A")),
            coordinations = mapOf(
                "A" to DshCoordination(
                    goal = DshGoalProjection(1, "补齐停靠条", "running", "剩余回归"),
                    todos = DshTodoProjection(
                        2,
                        listOf(
                            DshTodoItemProjection("t1", "接线", "completed"),
                            DshTodoItemProjection("t2", "跑测试", "in_progress")
                        )
                    )
                )
            )
        )
        val controller = controller(source)
        controller.refresh()
        val state = awaitSelection(controller, "A")
        assertEquals("补齐停靠条", state.goal!!.summary)
        assertEquals(com.tanuki.ui.GoalDockStatus.RUNNING, state.goal!!.status)
        assertEquals(2, state.todos!!.total)
        assertEquals(1, state.todos!!.completed)
    }

    @Test fun liveCoordinationEventsUpdateGoalAndTodoDocks() = runBlocking {
        val source = FakeSource(
            listOf(session("A")),
            liveEvents = listOf(
                DshLiveEvent.Connected,
                DshLiveEvent.Coordination(
                    DshCoordination(goal = DshGoalProjection(3, "实时目标", "paused"))
                ),
                DshLiveEvent.Coordination(
                    DshCoordination(
                        todos = DshTodoProjection(
                            4,
                            listOf(DshTodoItemProjection("t1", "实时待办", "pending"))
                        )
                    )
                )
            )
        )
        val controller = controller(source)
        controller.refresh()
        val state = awaitState(controller) { it.goal != null && it.todos != null }
        assertEquals("实时目标", state.goal!!.summary)
        assertEquals(com.tanuki.ui.GoalDockStatus.PAUSED, state.goal!!.status)
        assertEquals("实时待办", state.todos!!.items.single().text)
    }

    @Test fun sessionWithoutCoordinationKeepsDocksHidden() = runBlocking {
        val source = FakeSource(
            listOf(session("A"), session("B")),
            coordinations = mapOf(
                "A" to DshCoordination(goal = DshGoalProjection(1, "仅 A 的目标", "running"))
            )
        )
        val controller = controller(source)
        controller.refresh()
        assertEquals("仅 A 的目标", awaitSelection(controller, "A").goal!!.summary)

        controller.select("B")
        val state = awaitSelection(controller, "B")
        assertNull(state.goal)
        assertNull(state.todos)
    }

    @Test fun unknownGoalStatusDoesNotClearExistingDock() = runBlocking {
        val source = FakeSource(
            listOf(session("A")),
            liveEvents = listOf(
                DshLiveEvent.Connected,
                DshLiveEvent.Coordination(
                    DshCoordination(goal = DshGoalProjection(2, "有效目标", "running"))
                ),
                DshLiveEvent.Coordination(
                    DshCoordination(goal = DshGoalProjection(3, "未知状态目标", "unrecognized"))
                )
            )
        )
        val controller = controller(source)
        controller.refresh()
        val state = awaitState(controller) { it.goal != null }
        // 未知状态保持已知状态，且不推进序号；明确完成状态由单独用例验证清除。
        assertEquals("有效目标", state.goal!!.summary)
        assertEquals(com.tanuki.ui.GoalDockStatus.RUNNING, state.goal!!.status)
        assertEquals(2, state.lastGoalSeq)
    }

    @Test fun completedAndClearedGoalsHideOldDockAndRejectStaleSnapshot() = runBlocking {
        for (status in listOf("completed", "cleared")) {
            val running = DshCoordination(goal = DshGoalProjection(2, "当前目标", "running"))
            val source = FakeSource(listOf(session("A")), liveEvents = listOf(
                DshLiveEvent.Connected,
                DshLiveEvent.Coordination(running),
                DshLiveEvent.Coordination(DshCoordination(
                    goal = DshGoalProjection(3, if (status == "completed") "当前目标" else "", status)
                )),
                DshLiveEvent.Snapshot(emptyList(), false, "", coordination = running)
            ))
            val controller = controller(source)
            controller.refresh()
            val state = awaitSelection(controller, "A")
            assertEquals(3, state.lastGoalSeq)
            assertNull(state.goal)
        }
    }

    @Test fun liveEmptyTodosHideOldDockAndRejectStaleSnapshot() = runBlocking {
        val todos = DshCoordination(todos = DshTodoProjection(2,
            listOf(DshTodoItemProjection("t", "当前待办", "pending"))))
        val source = FakeSource(listOf(session("A")), liveEvents = listOf(
            DshLiveEvent.Connected,
            DshLiveEvent.Coordination(todos),
            DshLiveEvent.Coordination(DshCoordination(todos = DshTodoProjection(3, emptyList()))),
            DshLiveEvent.Snapshot(emptyList(), false, "", coordination = todos)
        ))
        val controller = controller(source)
        controller.refresh()
        val state = awaitSelection(controller, "A")
        assertEquals(3, state.lastTodoSeq)
        assertNull(state.todos)
    }

    @Test fun delayedHistoryCannotRegressNewLiveProjectionsOrUndoTheirClearing() = runBlocking {
        for (cleared in listOf(false, true)) {
            val gate = CompletableDeferred<Unit>()
            val old = DshCoordination(
                goal = DshGoalProjection(10, "当前目标", "running"),
                todos = DshTodoProjection(11, listOf(DshTodoItemProjection("t", "当前待办", "pending")))
            )
            val latest = DshCoordination(
                goal = DshGoalProjection(20, "当前目标", if (cleared) "completed" else "paused"),
                todos = DshTodoProjection(21,
                    if (cleared) emptyList() else listOf(DshTodoItemProjection("t", "当前待办", "completed")))
            )
            val source = FakeSource(
                listOf(session("A")), messages = mapOf("A" to "历史页"), pageGate = gate,
                coordinations = mapOf("A" to old),
                liveEvents = listOf(
                    DshLiveEvent.Connected,
                    DshLiveEvent.Snapshot(listOf(DshMessage(1, DshMessageRole.USER, "实时页", 1)),
                        false, "", coordination = old),
                    DshLiveEvent.Coordination(latest)
                )
            )
            val controller = controller(source)
            controller.refresh()
            awaitState(controller) { it.lastTodoSeq == 21 }
            gate.complete(Unit)
            val state = awaitState(controller) { !it.loading && it.messages.any { row -> row.text == "历史页" } }
            assertEquals(20, state.lastGoalSeq)
            assertEquals(21, state.lastTodoSeq)
            if (cleared) {
                assertNull(state.goal)
                assertNull(state.todos)
            } else {
                assertEquals(com.tanuki.ui.GoalDockStatus.PAUSED, state.goal!!.status)
                assertEquals(1, state.todos!!.completed)
            }
        }
    }

    @Test fun olderTextPageStillAppliesItsNewerCoordination() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val source = FakeSource(
            listOf(session("A")), pageGate = gate,
            coordinations = mapOf("A" to DshCoordination(goal = DshGoalProjection(60, "更新目标", "paused"))),
            liveEvents = listOf(DshLiveEvent.Connected, DshLiveEvent.Snapshot(
                listOf(DshMessage(5, DshMessageRole.USER, "更新文本", 5)), false, "",
                coordination = DshCoordination(goal = DshGoalProjection(40, "旧目标", "running"))
            ))
        )
        val controller = controller(source)
        controller.refresh()
        awaitState(controller) { it.lastGoalSeq == 40 }
        gate.complete(Unit)
        val state = awaitState(controller) { it.lastGoalSeq == 60 }
        assertEquals("更新目标", state.goal!!.summary)
        assertEquals(listOf("更新文本"), state.messages.map { it.text })
    }

    @Test fun goalAndTodoSequencesAreIndependentAcrossLiveAndSnapshotUpdates() = runBlocking {
        val initial = DshCoordination(
            goal = DshGoalProjection(40, "新目标", "paused"),
            todos = DshTodoProjection(5, listOf(DshTodoItemProjection("t", "待办", "pending")))
        )
        val mixed = DshCoordination(
            goal = DshGoalProjection(39, "旧目标", "running"),
            todos = DshTodoProjection(6, listOf(DshTodoItemProjection("t", "待办", "completed")))
        )
        val source = FakeSource(listOf(session("A")), liveEvents = listOf(
            DshLiveEvent.Connected,
            DshLiveEvent.Coordination(initial),
            DshLiveEvent.Snapshot(emptyList(), false, "", coordination = mixed),
            DshLiveEvent.Coordination(initial)
        ))
        val controller = controller(source)
        controller.refresh()
        val state = awaitSelection(controller, "A")
        assertEquals("新目标", state.goal!!.summary)
        assertEquals(com.tanuki.ui.GoalDockStatus.PAUSED, state.goal!!.status)
        assertEquals(1, state.todos!!.completed)
        assertEquals(40, state.lastGoalSeq)
        assertEquals(6, state.lastTodoSeq)
    }

    @Test fun sessionSwitchResetsBothProjectionSequences() = runBlocking {
        val source = FakeSource(listOf(session("A"), session("B")), coordinations = mapOf(
            "A" to DshCoordination(DshGoalProjection(40, "A", "running"), DshTodoProjection(41, emptyList())),
            "B" to DshCoordination(DshGoalProjection(1, "B", "paused"),
                DshTodoProjection(2, listOf(DshTodoItemProjection("b", "B 待办", "pending"))))
        ))
        val controller = controller(source)
        controller.refresh()
        awaitSelection(controller, "A")
        controller.select("B")
        val state = awaitSelection(controller, "B")
        assertEquals("B", state.goal!!.summary)
        assertEquals("B 待办", state.todos!!.items.single().text)
        assertEquals(1, state.lastGoalSeq)
        assertEquals(2, state.lastTodoSeq)
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
        output: String? = null,
        startedAt: Long = 0L,
        finishedAt: Long? = null
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
        risk = DshRiskAssessment(DshRiskLevel.LOW, "只读命令"),
        startedAt = startedAt,
        finishedAt = finishedAt
    )
}
