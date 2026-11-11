package com.tanuki

import com.tanuki.ui.DshNativeController
import com.tanuki.ui.DshReadStore
import com.tanuki.ui.DshSessionManagerController
import com.tanuki.ui.DshSessionSync
import com.tanuki.ui.ReaderSessionState
import com.tanuki.ui.ReaderViewState
import com.tanuki.storage.FileLocation
import com.tanuki.web.DshAdminSession
import com.tanuki.web.DshChatSource
import com.tanuki.web.DshCommand
import com.tanuki.web.DshLiveEvent
import com.tanuki.web.DshMessage
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshModelCatalog
import com.tanuki.web.DshModelSelection
import com.tanuki.web.DshPermissionCatalog
import com.tanuki.web.DshSession
import com.tanuki.web.DshSessionAdminSource
import com.tanuki.web.DshSessionPage
import com.tanuki.web.DshSessionSearchResult
import com.tanuki.web.DshSkill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话列表共享同步回归。
 *
 * 聊天页与会话管理器各自保留界面状态，但任何一侧创建/重命名/归档后都必须让
 * 另一侧自动重读官方列表；测试不连接 DSH，只使用内存中的合成会话。
 */
private fun adminSession(id: String, title: String) = DshAdminSession(
    id = id, title = title, updatedAt = 1_000L, running = false, blank = false,
    agentAvailable = true, asOfSeq = 1, currentPermission = "workspace-write",
    model = null, cwd = null, parentSessionId = null, subagent = false, formatStatus = null,
)

class DshSessionSyncTest {
    @Test fun chatCreateImmediatelyAppearsInChatAndManagerWithoutManualRefresh() {
        val sync = DshSessionSync()
        val admin = FakeAdminSource(listOf(adminSession("a", "甲"), adminSession("b", "乙")))
        val chatSource = FakeChatSource { admin.chatSnapshot() }
        val chat = DshNativeController(
            client = chatSource,
            scope = CoroutineScope(Dispatchers.Unconfined),
            createRemote = { admin.createSession() },
            sessionSync = sync,
        )
        val manager = DshSessionManagerController(
            source = admin,
            readStore = MemoryReadStore(),
            scope = CoroutineScope(Dispatchers.Unconfined),
            sessionSync = sync,
        )
        chat.refresh()
        manager.refresh()
        assertEquals("a", chat.state.value.selectedId)
        assertEquals(listOf("a", "b"), manager.state.value.sessions.map { it.id })

        // 一次点击语义：直接创建，不经过标题对话框，也不手动刷新任何列表。
        chat.createSession()

        waitUntil { chat.state.value.selectedId == "new" }
        waitUntil { manager.state.value.sessions.any { it.id == "new" } }
        val chatState = chat.state.value
        assertEquals(listOf("a", "b", "new"), chatState.sessions.map { it.id })
        assertEquals("新会话", chatState.selectedSession?.displayTitle)
        assertTrue(chatState.sessions.any { it.id == "new" })
    }

    @Test fun managerCreateAutoRefreshesExistingChatList() {
        val sync = DshSessionSync()
        val admin = FakeAdminSource(listOf(adminSession("a", "甲")))
        val chatSource = FakeChatSource { admin.chatSnapshot() }
        val chat = DshNativeController(chatSource, CoroutineScope(Dispatchers.Unconfined), sessionSync = sync)
        val manager = DshSessionManagerController(
            admin, MemoryReadStore(), CoroutineScope(Dispatchers.Unconfined), sessionSync = sync
        )
        chat.refresh()
        manager.refresh()

        manager.createSession("评审会话")

        waitUntil { chat.state.value.sessions.any { it.id == "new" } }
        // 外部新建不应把用户正在阅读的会话切走。
        assertEquals("a", chat.state.value.selectedId)
        assertEquals("评审会话", chat.state.value.sessions.first { it.id == "new" }.title)
    }

    @Test fun managerArchiveRemovesSessionFromChatWithoutManualRefresh() {
        val sync = DshSessionSync()
        val admin = FakeAdminSource(listOf(adminSession("a", "甲"), adminSession("b", "乙")))
        val chatSource = FakeChatSource { admin.chatSnapshot() }
        val chat = DshNativeController(chatSource, CoroutineScope(Dispatchers.Unconfined), sessionSync = sync)
        val manager = DshSessionManagerController(
            admin, MemoryReadStore(), CoroutineScope(Dispatchers.Unconfined), sessionSync = sync
        )
        chat.refresh()
        manager.refresh()

        manager.archiveSession("b", stopActivity = false)

        waitUntil { chat.state.value.sessions.none { it.id == "b" } }
        assertEquals(listOf("a"), chat.state.value.sessions.map { it.id })
        assertEquals("a", chat.state.value.selectedId)
    }

    @Test fun readerStateNormalizesBadPersistedValues() {
        val state = ReaderSessionState(
            path = "/workspaces/${"x".repeat(5_000)}",
            name = "文".repeat(600),
            linked = true,
            location = FileLocation.WORKSPACE,
            view = ReaderViewState(encoding = "不存在的编码", scrollOffset = -8, showLineNumbers = false),
        ).normalized()

        assertEquals(4_096, state.path.length)
        assertEquals(512, state.name.length)
        assertTrue(state.linked)
        assertEquals("自动", state.view.encoding)
        assertEquals(0, state.view.scrollOffset)
        assertFalse(state.view.showLineNumbers)
        assertTrue(state.path.startsWith("/workspaces/"))
    }

    @Test fun readerStateKeepsSupportedEncodingAndOffset() {
        val state = ReaderViewState(encoding = "GB18030", scrollOffset = 1_280, showLineNumbers = true).normalized()
        assertEquals("GB18030", state.encoding)
        assertEquals(1_280, state.scrollOffset)
    }

    private fun waitUntil(timeoutMillis: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("等待条件超时", condition())
    }

    private class MemoryReadStore : DshReadStore {
        private val values = mutableMapOf<String, Long>()
        override fun lastRead(sessionId: String): Long = values[sessionId] ?: 0L
        override fun markRead(sessionId: String, updatedAt: Long) { values[sessionId] = updatedAt }
    }

    private class FakeAdminSource(initial: List<DshAdminSession>) : DshSessionAdminSource {
        private val items = initial.toMutableList()
        override suspend fun sessions(): Result<List<DshAdminSession>> = Result.success(items.toList())
        override suspend fun createSession(): Result<String> {
            items += adminSessionForTest("new", "")
            return Result.success("new")
        }
        override suspend fun renameSession(sessionId: String, title: String): Result<String> {
            val index = items.indexOfFirst { it.id == sessionId }
            if (index >= 0) items[index] = items[index].copy(title = title)
            return Result.success(title)
        }
        override suspend fun archiveSession(sessionId: String, stopActivity: Boolean): Result<List<String>> {
            items.removeAll { it.id == sessionId }
            return Result.success(listOf(sessionId))
        }
        override suspend fun search(query: String): Result<DshSessionSearchResult> =
            Result.success(DshSessionSearchResult(emptyList(), false))
        override suspend fun models(): Result<DshModelCatalog> =
            Result.success(DshModelCatalog(null, emptyList(), emptyList()))
        override suspend fun selectModel(sessionId: String, selection: DshModelSelection): Result<DshModelSelection> =
            Result.success(selection)
        override suspend fun permissions(): Result<DshPermissionCatalog> =
            Result.success(DshPermissionCatalog(emptyList(), emptyList(), "workspace-write"))
        override suspend fun selectPermission(sessionId: String, preset: String): Result<Unit> = Result.success(Unit)
        override suspend fun skills(sessionId: String): Result<List<DshSkill>> = Result.success(emptyList())
        override suspend fun commands(sessionId: String): Result<List<DshCommand>> = Result.success(emptyList())

        fun chatSnapshot(): List<DshSession> = items.map {
            DshSession(
                id = it.id, title = it.title, updatedAt = it.updatedAt, running = it.running,
                blank = it.blank, agentAvailable = it.agentAvailable, asOfSeq = it.asOfSeq,
            )
        }

        private fun adminSessionForTest(id: String, title: String) = adminSession(id, title.ifBlank { "新会话" })
    }

    private class FakeChatSource(private val snapshot: () -> List<DshSession>) : DshChatSource {
        override suspend fun sessions(): Result<List<DshSession>> = Result.success(snapshot())
        override suspend fun messages(sessionId: String): Result<DshSessionPage> = Result.success(
            DshSessionPage(listOf(DshMessage(1, DshMessageRole.USER, "历史", 1L)), false)
        )
        override suspend fun send(sessionId: String, text: String): Result<Unit> = Result.success(Unit)
        override suspend fun cancel(sessionId: String): Result<Unit> = Result.success(Unit)
        override fun live(sessionId: String): Flow<DshLiveEvent> = emptyFlow()
        override suspend fun messagesBefore(
            sessionId: String,
            beforeSeq: Int,
            maxMessages: Int,
        ): Result<DshSessionPage> = Result.success(DshSessionPage(emptyList(), false))
    }
}
