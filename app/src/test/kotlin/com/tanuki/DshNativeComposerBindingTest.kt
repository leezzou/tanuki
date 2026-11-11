package com.tanuki

import com.tanuki.ui.DshComposerController
import com.tanuki.ui.DshComposerState
import com.tanuki.ui.DshNativeController
import com.tanuki.ui.DshStopOutcome
import com.tanuki.ui.MemoryDshDraftStore
import com.tanuki.ui.bindDshComposerRunState
import com.tanuki.web.DshChatSource
import com.tanuki.web.DshComposerClient
import com.tanuki.web.DshComposerRemote
import com.tanuki.web.DshLiveEvent
import com.tanuki.web.DshSession
import com.tanuki.web.DshSessionPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 复用页面实际调用的持续绑定入口，覆盖两个真实控制器之间的接线；不冒充设备 UI 验收。 */
class DshNativeComposerBindingTest {
    @Test fun enteringRunningSessionCanStopAndOnlyOfficialIdleConfirmsStopped() = runBlocking {
        val fixture = Fixture(initiallyRunning = true)
        try {
            fixture.start()
            assertTrue(fixture.awaitComposer { it.sessionId == "A" }.generationRunning)
            fixture.composer.requestStop()
            val requested = fixture.awaitComposer { it.stopOutcome == DshStopOutcome.REQUESTED }
            assertTrue(requested.generationRunning)
            assertTrue(requested.stopping)
            assertEquals(listOf("A"), fixture.cancelled)

            fixture.source.emit("A", DshLiveEvent.Running(false))
            val idle = fixture.awaitComposer { it.stopOutcome == DshStopOutcome.STOPPED }
            assertFalse(idle.generationRunning)
            assertFalse(idle.stopping)
        } finally {
            fixture.close()
        }
    }

    @Test fun acceptedSendEnablesStopAndOfficialRunThenIdleResetsIt() = runBlocking {
        val fixture = Fixture(initiallyRunning = false)
        try {
            fixture.start()
            fixture.composer.updateText("运行任务")
            fixture.composer.send()
            val sent = fixture.awaitComposer { it.notice == "已发送" }
            assertTrue(sent.generationRunning)
            fixture.source.emit("A", DshLiveEvent.Running(true))
            fixture.composer.requestStop()
            fixture.awaitComposer { it.stopOutcome == DshStopOutcome.REQUESTED }
            assertEquals(listOf("A"), fixture.cancelled)
            fixture.source.emit("A", DshLiveEvent.Running(false))
            val idle = fixture.awaitComposer { it.stopOutcome == DshStopOutcome.STOPPED }
            assertFalse(idle.generationRunning)
        } finally {
            fixture.close()
        }
    }

    @Test fun sessionSwitchResetsStopStateAndDoesNotMixDraftsOrLateEvents() = runBlocking {
        val fixture = Fixture(initiallyRunning = true)
        try {
            fixture.start()
            fixture.composer.updateText("A 的草稿")
            fixture.composer.requestStop()
            fixture.awaitComposer { it.stopOutcome == DshStopOutcome.REQUESTED }

            fixture.native.select("B")
            val b = fixture.awaitComposer { it.sessionId == "B" }
            assertFalse(b.generationRunning)
            assertFalse(b.stopping)
            assertNull(b.stopOutcome)
            assertEquals("", b.text)
            fixture.composer.updateText("B 的草稿")
            fixture.source.emit("A", DshLiveEvent.Running(false))
            assertFalse(fixture.composer.state.value.generationRunning)
            assertEquals("B", fixture.composer.state.value.sessionId)

            fixture.native.select("A")
            val a = fixture.awaitComposer { it.sessionId == "A" && !it.generationRunning }
            assertFalse(a.stopping)
            assertEquals("A 的草稿", a.text)
            fixture.source.emit("A", DshLiveEvent.Running(true))
            fixture.awaitComposer { it.generationRunning }
            fixture.composer.requestStop()
            fixture.awaitComposer { it.stopOutcome == DshStopOutcome.REQUESTED }
            assertEquals(listOf("A", "A"), fixture.cancelled)
        } finally {
            fixture.close()
        }
    }

    @Test fun unrelatedNativeUpdatesDoNotClearAcceptedSendRunningState() = runBlocking {
        val fixture = Fixture(initiallyRunning = false)
        try {
            fixture.start()
            fixture.composer.updateText("继续任务")
            fixture.composer.send()
            fixture.awaitComposer { it.notice == "已发送" }
            // 与会话运行态无关的连接更新不能用旧 idle 覆盖已受理的发送状态。
            fixture.source.emit("A", DshLiveEvent.Connected)
            assertTrue(fixture.composer.state.value.generationRunning)
        } finally {
            fixture.close()
        }
    }

    @Test fun streamUpdatesRefreshWatchdogAndSilenceKeepsSendAndStopAvailable() = runBlocking {
        val fixture = Fixture(initiallyRunning = true, staleTimeoutMillis = 100)
        try {
            fixture.start()
            fixture.awaitComposer { it.runStateStale }
            fixture.source.emit("A", DshLiveEvent.Streaming("真实流式片段"))
            val refreshed = fixture.awaitComposer { !it.runStateStale && it.generationRunning }
            assertTrue(refreshed.generationRunning)
            fixture.awaitComposer { it.runStateStale }
            fixture.composer.updateText("可继续发送")
            assertTrue(fixture.composer.state.value.canSend)
            fixture.composer.requestStop()
            fixture.awaitComposer { it.stopOutcome == DshStopOutcome.REQUESTED }
            assertEquals(listOf("A"), fixture.cancelled)
        } finally {
            fixture.close()
        }
    }

    private class Fixture(initiallyRunning: Boolean, staleTimeoutMillis: Long = 30_000) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val source = Source(initiallyRunning)
        val native = DshNativeController(source, scope)
        val cancelled = mutableListOf<String>()
        val composer = DshComposerController(
            client = DshComposerClient(object : DshComposerRemote {
                override suspend fun call(method: String, args: JSONObject): Any? = when (method) {
                    "commands/list" -> JSONArray()
                    "session/projections" -> JSONObject().put("kind", "sequenced").put("values", JSONObject())
                    "session/prompt" -> JSONObject().put("accepted", true)
                    "session/cancel" -> {
                        cancelled += args.getJSONObject("request").getString("sessionId")
                        JSONObject().put("accepted", true)
                    }
                    else -> null
                }
            }),
            drafts = MemoryDshDraftStore(),
            scope = scope,
            onSent = { native.select(it, force = true) },
            runningStaleTimeoutMillis = staleTimeoutMillis
        )

        fun start() {
            scope.launch { bindDshComposerRunState(native.state, composer) }
            native.refresh()
        }

        suspend fun awaitComposer(predicate: (DshComposerState) -> Boolean): DshComposerState =
            withTimeout(5_000) { composer.state.first(predicate) }

        fun close() {
            native.close()
            composer.close()
            scope.cancel()
        }
    }

    private class Source(initiallyRunning: Boolean) : DshChatSource {
        private val events = mapOf(
            "A" to Channel<DshLiveEvent>(Channel.UNLIMITED),
            "B" to Channel<DshLiveEvent>(Channel.UNLIMITED)
        )
        private val sessions = listOf(
            DshSession("A", "会话 A", 2L, initiallyRunning, false, true, null),
            DshSession("B", "会话 B", 1L, false, false, true, null)
        )

        override suspend fun sessions(): Result<List<DshSession>> = Result.success(sessions)
        override suspend fun messages(sessionId: String): Result<DshSessionPage> =
            Result.success(DshSessionPage(emptyList(), false))
        override suspend fun messagesBefore(sessionId: String, beforeSeq: Int, maxMessages: Int): Result<DshSessionPage> =
            messages(sessionId)
        override suspend fun send(sessionId: String, text: String): Result<Unit> = Result.success(Unit)
        override suspend fun cancel(sessionId: String): Result<Unit> = Result.success(Unit)
        override fun live(sessionId: String): Flow<DshLiveEvent> = flow {
            for (event in events.getValue(sessionId)) emit(event)
        }

        fun emit(sessionId: String, event: DshLiveEvent) {
            check(events.getValue(sessionId).trySend(event).isSuccess)
        }
    }
}
