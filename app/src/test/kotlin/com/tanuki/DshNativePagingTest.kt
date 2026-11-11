package com.tanuki

import com.tanuki.ui.DshNativeController
import com.tanuki.ui.DshNativeState
import com.tanuki.ui.DshChatWindowPolicy
import com.tanuki.web.DshChatSource
import com.tanuki.web.DshLiveEvent
import com.tanuki.web.DshMessage
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshSession
import com.tanuki.web.DshSessionPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 控制器层回归：向前分页、重复页防护、内存窗口释放与重连对账。
 * 设备/Compose 手势与真实官方服务未覆盖，这里只验证控制器状态机。
 */
class DshNativePagingTest {
    private val session = DshSession(
        id = "A",
        title = "会话 A",
        updatedAt = 0L,
        running = false,
        blank = false,
        agentAvailable = false,
        asOfSeq = 999
    )

    @Test fun loadOlderPrependsPageSortedWithoutDuplicates() {
        val source = pagedSource(
            page = page((100..120).map { message(it) }, hasMore = true),
            older = { Result.success(page((50..99).map { message(it) }, hasMore = true)) }
        )
        val controller = controller(source)
        controller.refresh()
        awaitLoaded(controller)
        controller.loadOlder()
        val state = awaitState(controller) { !it.loadingOlder && it.messages.size > 21 }

        assertEquals((50..120).toList(), state.messages.map { it.seq })
        assertEquals(state.messages.size, state.messages.map { it.seq }.distinct().size)
        assertTrue(state.hasMore)
        assertEquals(1, state.historyRevision)
        assertEquals(1, source.releasedRequests.size)
    }

    @Test fun loadOlderUsesOldestSeqAndProtocolPageSize() {
        val source = pagedSource(
            page = page((80..120).map { message(it) }, hasMore = true),
            older = { Result.success(page((30..79).map { message(it) }, hasMore = false)) }
        )
        val controller = controller(source)
        controller.refresh()
        awaitLoaded(controller)
        controller.loadOlder()
        awaitState(controller) { !it.loadingOlder }

        val request = source.releasedRequests.single()
        assertEquals("A", request.first)
        assertEquals(79, request.second)
        assertEquals(DshChatWindowPolicy.PAGE_SIZE, request.third)
    }

    @Test fun loadOlderFailureKeepsLoadedHistoryAndReportsReason() {
        val source = pagedSource(
            page = page((1..20).map { message(it) }, hasMore = true),
            older = { Result.failure(IOException("network")) }
        )
        val controller = controller(source)
        controller.refresh()
        awaitLoaded(controller)
        controller.loadOlder()
        val state = awaitState(controller) { it.olderError != null }

        assertEquals((1..20).toList(), state.messages.map { it.seq })
        assertTrue(state.hasMore)
        assertFalse(state.loadingOlder)
        assertTrue(state.olderError!!.contains("network"))
    }

    @Test fun emptyOlderPageStopsFurtherPaging() {
        val source = pagedSource(
            page = page((1..20).map { message(it) }, hasMore = true),
            older = { Result.success(page(emptyList(), hasMore = true)) }
        )
        val controller = controller(source)
        controller.refresh()
        awaitLoaded(controller)
        controller.loadOlder()
        val state = awaitState(controller) { !it.loadingOlder }

        assertFalse(state.hasMore)
        assertNull(state.olderError)
        assertEquals(20, state.messages.size)
    }

    @Test fun duplicateOlderPageDoesNotGrowOrLoop() {
        val source = pagedSource(
            page = page((1..20).map { message(it) }, hasMore = true),
            // 服务端忽略游标时重复返回当前页；控制器不能把它插成重复行，也不能反复请求。
            older = { Result.success(page((1..20).map { message(it) }, hasMore = true)) }
        )
        val controller = controller(source)
        controller.refresh()
        awaitLoaded(controller)
        controller.loadOlder()
        val state = awaitState(controller) { !it.loadingOlder }

        assertEquals((1..20).toList(), state.messages.map { it.seq })
        assertFalse(state.hasMore)
        assertEquals(0, state.historyRevision)
        assertEquals(1, source.releasedRequests.size)
    }

    @Test fun windowTrimReleasesOnlyRowsAboveViewport() {
        val source = pagedSource(page = page((1..600).map { message(it) }, hasMore = false))
        val controller = controller(source)
        controller.refresh()
        awaitLoaded(controller)

        controller.onViewportTop(100)
        var state = controller.state.value
        assertEquals(99, state.releasedOlder)
        assertEquals(100, state.messages.first().seq)
        assertEquals(600, state.messages.last().seq)
        assertEquals(501, state.messages.size)
        assertTrue(state.hasMore)
        assertEquals(1, state.historyRevision)

        controller.onViewportTop(101)
        state = controller.state.value
        assertEquals(100, state.releasedOlder)
        assertEquals(101, state.messages.first().seq)
        assertEquals(DshChatWindowPolicy.MAX_MESSAGES, state.messages.size)
    }

    @Test fun windowTrimKeepsRowsBelowViewport() {
        val source = pagedSource(page = page((1..600).map { message(it) }, hasMore = false))
        val controller = controller(source)
        controller.refresh()
        awaitLoaded(controller)

        controller.onViewportTop(550)
        val state = controller.state.value
        assertTrue(state.messages.any { it.seq == 550 })
        assertEquals(600, state.messages.last().seq)
        assertTrue(state.messages.size <= DshChatWindowPolicy.MAX_MESSAGES)
        assertTrue(state.messages.first().seq > 1)
    }

    @Test fun windowTrimAtOldestLoadedRowReleasesNothing() {
        val source = pagedSource(page = page((1..600).map { message(it) }, hasMore = false))
        val controller = controller(source)
        controller.refresh()
        awaitLoaded(controller)

        controller.onViewportTop(1)
        val state = controller.state.value
        assertEquals(0, state.releasedOlder)
        assertEquals(600, state.messages.size)
        assertEquals(0, state.historyRevision)
    }

    @Test fun sameSeqFromSnapshotAndLiveIsInsertedOnce() {
        val source = pagedSource(
            page = page(emptyList(), hasMore = false),
            liveEvents = listOf(
                DshLiveEvent.Connected,
                DshLiveEvent.Snapshot(listOf(message(5, "一次")), hasMore = false, liveText = ""),
                DshLiveEvent.Message(message(5, "一次")),
                DshLiveEvent.Message(message(5, "一次"))
            )
        )
        val controller = controller(source)
        controller.refresh()
        val state = awaitState(controller) { !it.loading && it.messages.isNotEmpty() }

        assertEquals(listOf(5), state.messages.map { it.seq })
    }

    @Test fun reconnectTriggersHistoryReconcile() {
        val source = pagedSource(
            page = page(listOf(message(1), message(2), message(3)), hasMore = false),
            liveEvents = listOf(
                DshLiveEvent.Connected,
                DshLiveEvent.Snapshot(listOf(message(2)), hasMore = false, liveText = ""),
                DshLiveEvent.Connected
            )
        )
        val controller = controller(source)
        controller.refresh()
        val state = awaitState(controller) { !it.loading && it.messages.size == 3 }

        assertEquals(listOf(1, 2, 3), state.messages.map { it.seq })
        assertTrue(source.messageRequests >= 2)
    }

    @Test fun olderPageSurvivesReconnectSnapshot() {
        val source = pagedSource(
            page = page((100..120).map { message(it) }, hasMore = true),
            older = { Result.success(page((50..99).map { message(it) }, hasMore = true)) }
        )
        val controller = controller(source)
        controller.refresh()
        awaitLoaded(controller)
        controller.loadOlder()
        awaitState(controller) { it.messages.size == 71 }

        // 重连快照只覆盖最近窗口，不能把已经加载的更早分页删掉。
        source.emit(DshLiveEvent.Snapshot((110..120).map { message(it) }, hasMore = true, liveText = ""))
        val state = awaitState(controller) { it.messages.size == 71 }
        assertEquals((50..120).toList(), state.messages.map { it.seq })
    }

    private fun controller(source: PagedSource): DshNativeController =
        DshNativeController(source, CoroutineScope(Dispatchers.Unconfined))

    private fun awaitLoaded(controller: DshNativeController) {
        awaitState(controller) { !it.loading && it.messages.isNotEmpty() }
    }

    private fun awaitState(
        controller: DshNativeController,
        predicate: (DshNativeState) -> Boolean
    ): DshNativeState = runBlocking {
        withTimeout(2_000) { controller.state.first(predicate) }
    }

    private fun message(seq: Int, text: String = "m$seq") =
        DshMessage(seq = seq, role = DshMessageRole.ASSISTANT, text = text, time = seq.toLong())

    private fun page(messages: List<DshMessage>, hasMore: Boolean) =
        DshSessionPage(messages = messages, hasMore = hasMore)

    private fun pagedSource(
        page: DshSessionPage,
        older: (Int) -> Result<DshSessionPage> = { Result.success(DshSessionPage(emptyList(), false)) },
        liveEvents: List<DshLiveEvent> = emptyList()
    ) = PagedSource(listOf(session), page, older, liveEvents)

    /** 记录向前分页请求的合成数据源；`emit` 允许测试在运行中注入实时事件。 */
    private class PagedSource(
        private val sessions: List<DshSession>,
        private val page: DshSessionPage,
        private val older: (Int) -> Result<DshSessionPage>,
        liveEvents: List<DshLiveEvent>
    ) : DshChatSource {
        val releasedRequests = mutableListOf<Triple<String, Int, Int>>()
        var messageRequests = 0
        private val injected = Channel<DshLiveEvent>(Channel.UNLIMITED).also { channel ->
            liveEvents.forEach(channel::trySend)
        }

        override suspend fun sessions(): Result<List<DshSession>> = Result.success(sessions)

        override suspend fun messages(sessionId: String): Result<DshSessionPage> {
            messageRequests++
            return Result.success(page)
        }

        override suspend fun messagesBefore(
            sessionId: String,
            beforeSeq: Int,
            maxMessages: Int
        ): Result<DshSessionPage> {
            releasedRequests += Triple(sessionId, beforeSeq, maxMessages)
            return older(beforeSeq)
        }

        override suspend fun send(sessionId: String, text: String): Result<Unit> = Result.success(Unit)

        override suspend fun cancel(sessionId: String): Result<Unit> = Result.success(Unit)

        override fun live(sessionId: String): Flow<DshLiveEvent> = flow {
            // 通道允许测试在运行中补发事件；消费完预置事件后挂起等待，不结束实时流。
            for (event in injected) emit(event)
        }

        fun emit(event: DshLiveEvent) {
            injected.trySend(event)
        }
    }
}
