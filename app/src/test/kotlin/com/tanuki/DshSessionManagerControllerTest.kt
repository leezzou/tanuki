package com.tanuki

import com.tanuki.ui.DshReadStore
import com.tanuki.ui.DshSessionFilter
import com.tanuki.ui.DshSessionFiltering
import com.tanuki.ui.DshSessionManagerController
import com.tanuki.web.DshAdminSession
import com.tanuki.web.DshApiException
import com.tanuki.web.DshCommand
import com.tanuki.web.DshModelCatalog
import com.tanuki.web.DshModelGroup
import com.tanuki.web.DshModelSelection
import com.tanuki.web.DshPermissionCatalog
import com.tanuki.web.DshPermissionOption
import com.tanuki.web.DshSessionAdminSource
import com.tanuki.web.DshSessionSearchResult
import com.tanuki.web.DshSkill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DshSessionManagerControllerTest {
    @Test fun refreshPublishesSessionsAndLocalUnread() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 2_000L), session("s2", "乙", 1_000L)))
        val read = MemoryReadStore(mutableMapOf("s1" to 2_000L))
        val controller = controller(source, read)

        controller.refresh()

        val state = controller.state.value
        assertFalse(state.loading)
        assertTrue(state.available)
        assertEquals(listOf("s1", "s2"), state.sessions.map { it.id })
        assertEquals(setOf("s2"), state.unreadIds)
    }

    @Test fun localFilterMatchesTitleIdAndRunningOrUnread() {
        val all = listOf(
            session("s1", "修复登录", 2_000L, running = true),
            session("s2", "整理文档", 1_000L, running = false)
        )
        assertEquals(listOf("s1"), DshSessionFiltering.visible(all, "登录", DshSessionFilter.ALL, emptySet()).map { it.id })
        assertEquals(listOf("s2"), DshSessionFiltering.visible(all, "s2", DshSessionFilter.ALL, emptySet()).map { it.id })
        assertEquals(listOf("s1"), DshSessionFiltering.visible(all, "", DshSessionFilter.RUNNING, emptySet()).map { it.id })
        assertEquals(listOf("s2"), DshSessionFiltering.visible(all, "", DshSessionFilter.UNREAD, setOf("s2")).map { it.id })
    }

    @Test fun openingSessionMarksItReadWithoutTouchingOfficialData() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 2_000L)))
        val read = MemoryReadStore()
        val controller = controller(source, read)
        controller.refresh()
        assertEquals(setOf("s1"), controller.state.value.unreadIds)

        controller.openSession("s1")

        assertEquals("s1", controller.state.value.selectedId)
        assertEquals(2_000L, read.map["s1"])
        assertTrue(controller.state.value.unreadIds.isEmpty())
    }

    @Test fun createSessionThenRenameUsesOfficialCallsAndSelectsResult() {
        val source = FakeSource()
        source.sessionsResult = Result.success(emptyList())
        source.createResult = Result.success("new")
        source.renameResult = Result.success("新会话")
        val controller = controller(source, MemoryReadStore())
        controller.refresh()

        controller.createSession("新会话")

        assertEquals(listOf("create", "rename:new:新会话"), source.calls)
        val state = controller.state.value
        assertFalse(state.busy)
        assertEquals("new", state.selectedId)
        assertEquals("已创建并命名会话", state.notice)
        assertEquals(listOf("new"), state.sessions.map { it.id })
        assertEquals("新会话", state.sessions.single().title)
    }

    @Test fun invalidTitlesFailBeforeOfficialWrite() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 1_000L)))
        val controller = controller(source, MemoryReadStore())
        controller.refresh()

        controller.renameSession("s1", "   ")
        assertTrue(source.calls.isEmpty())
        assertTrue(controller.state.value.error.orEmpty().contains("标题"))

        controller.createSession("坏\u0000标题")
        assertTrue(source.calls.isEmpty())
        assertTrue(controller.state.value.error.orEmpty().contains("标题"))
    }

    @Test fun archiveRunningSessionRequestsStopAndRemovesIt() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 2_000L, running = true)))
        val controller = controller(source, MemoryReadStore())
        controller.refresh()

        controller.archiveSession("s1", stopActivity = true)

        assertEquals("archive:s1:true", source.calls.single())
        assertTrue(controller.state.value.sessions.isEmpty())
        assertEquals("已停止运行并归档", controller.state.value.notice)
        assertNull(controller.state.value.selectedId)
    }

    @Test fun safeModeRejectsEveryWrite() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 1_000L)))
        val controller = controller(source, MemoryReadStore())
        controller.refresh()
        controller.setReadOnly(true)

        controller.createSession("新会话")
        controller.renameSession("s1", "乙")
        controller.archiveSession("s1", false)
        controller.selectModel("s1", DshModelSelection("p", "m"))
        controller.selectPermission("s1", "read-only")

        assertTrue(source.calls.isEmpty())
        assertTrue(controller.state.value.error.orEmpty().contains("只读"))
    }

    @Test fun contentSearchPublishesHitsAndClears() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 1_000L)))
        source.searchResult = Result.success(DshSessionSearchResult(listOf(
            com.tanuki.web.DshSessionSearchHit("s1", "命中片段")
        ), hasMore = true))
        val controller = controller(source, MemoryReadStore())
        controller.refresh()
        controller.setQuery("登录")
        controller.searchContent()

        assertTrue(controller.state.value.contentSearched)
        assertFalse(controller.state.value.contentSearching)
        assertEquals("命中片段", controller.state.value.contentHits.single().snippet)
        assertTrue(controller.state.value.contentHasMore)

        controller.setQuery("其他")
        assertTrue(controller.state.value.contentHits.isEmpty())
        assertFalse(controller.state.value.contentSearched)

        controller.clearContentSearch()
        assertTrue(controller.state.value.contentHits.isEmpty())
        assertFalse(controller.state.value.contentSearched)
    }

    @Test fun fullAccessPermissionRequiresExplicitConfirmation() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 1_000L)))
        val controller = controller(source, MemoryReadStore())
        controller.refresh()

        controller.selectPermission("s1", "danger-full-access")
        assertTrue(source.calls.isEmpty())
        assertTrue(controller.state.value.error.orEmpty().contains("完全权限"))

        controller.selectPermission("s1", "danger-full-access", allowFullAccess = true)
        assertEquals("selectPermission:s1:danger-full-access", source.calls.single())
    }

    @Test fun customPermissionIsRejectedBeforeNetwork() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 1_000L)))
        val controller = controller(source, MemoryReadStore())
        controller.refresh()

        controller.selectPermission("s1", "custom")

        assertTrue(source.calls.isEmpty())
        assertTrue(controller.state.value.error.orEmpty().contains("权限模式"))
    }

    @Test fun sessionCatalogKeepsSuccessfulHalfOnPartialFailure() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 1_000L)))
        source.skillsResult = Result.success(listOf(DshSkill("review", "审查", null, true, null)))
        source.commandsResult = Result.failure(DshApiException("commands/unavailable"))
        val controller = controller(source, MemoryReadStore())
        controller.refresh()

        controller.loadSessionCatalog("s1")

        val state = controller.state.value
        assertFalse(state.catalogLoading)
        assertEquals("review", state.skills.single().name)
        assertTrue(state.commands.isEmpty())
        assertTrue(state.catalogError.orEmpty().contains("命令目录"))
    }

    @Test fun modelsAndPermissionsLoadAndSelectionReloadsList() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 1_000L)))
        source.modelsResult = Result.success(DshModelCatalog(
            default = DshModelSelection("deepseek", "chat"),
            groups = listOf(DshModelGroup("deepseek", "DeepSeek", emptyList())),
            failures = emptyList()
        ))
        source.permissionsResult = Result.success(DshPermissionCatalog(
            options = listOf(DshPermissionOption("read-only", "只读", null)),
            defaultOptions = emptyList(),
            defaultPreset = "read-only"
        ))
        val controller = controller(source, MemoryReadStore())
        controller.refresh()

        controller.loadModels()
        controller.loadPermissions()
        assertEquals("deepseek", controller.state.value.models?.default?.provider)
        assertEquals("read-only", controller.state.value.permissions?.defaultPreset)

        controller.selectModel("s1", DshModelSelection("deepseek", "chat", "high"))
        controller.selectPermission("s1", "read-only")

        assertEquals(listOf(
            "selectModel:s1:deepseek/chat/high",
            "selectPermission:s1:read-only"
        ), source.calls)
        assertEquals("已切换权限模式", controller.state.value.notice)
    }

    @Test fun missingNotificationTargetReportsInsteadOfInventingSession() {
        val source = FakeSource()
        source.sessionsResult = Result.success(emptyList())
        val controller = controller(source, MemoryReadStore())

        controller.openSession("missing")

        assertTrue(controller.state.value.sessions.isEmpty())
        assertTrue(controller.state.value.error.orEmpty().contains("目标会话不在当前列表"))
    }

    @Test fun closeDropsSourceCredentialState() {
        val source = FakeSource()
        val controller = controller(source, MemoryReadStore())
        controller.close()
        assertTrue(source.resetCalled)
        assertFalse(controller.state.value.loading)
        assertFalse(controller.state.value.busy)
    }

    private fun controller(source: DshSessionAdminSource, read: DshReadStore) =
        DshSessionManagerController(source, read, CoroutineScope(Dispatchers.Unconfined))

    private fun session(
        id: String,
        title: String,
        updatedAt: Long,
        running: Boolean = false,
        blank: Boolean = false
    ) = DshAdminSession(
        id = id,
        title = title,
        updatedAt = updatedAt,
        running = running,
        blank = blank,
        agentAvailable = true,
        asOfSeq = 9,
        currentPermission = "workspace-write",
        model = DshModelSelection("deepseek", "chat"),
        cwd = "/workspaces/demo",
        parentSessionId = null,
        subagent = false,
        formatStatus = "current"
    )

    private class MemoryReadStore(val map: MutableMap<String, Long> = mutableMapOf()) : DshReadStore {
        override fun lastRead(sessionId: String): Long = map[sessionId] ?: 0L
        override fun markRead(sessionId: String, updatedAt: Long) {
            map[sessionId] = updatedAt
        }
    }

    private class FakeSource : DshSessionAdminSource {
        val calls = mutableListOf<String>()
        var sessionsResult: Result<List<DshAdminSession>> = Result.success(emptyList())
        var createResult: Result<String> = Result.success("new")
        var renameResult: Result<String> = Result.success("renamed")
        var archiveResult: Result<List<String>> = Result.success(emptyList())
        var searchResult: Result<DshSessionSearchResult> = Result.success(DshSessionSearchResult(emptyList(), false))
        var modelsResult: Result<DshModelCatalog> = Result.success(DshModelCatalog(null, emptyList(), emptyList()))
        var selectModelResult: Result<DshModelSelection> = Result.success(DshModelSelection("deepseek", "chat"))
        var permissionsResult: Result<DshPermissionCatalog> = Result.success(DshPermissionCatalog(emptyList(), emptyList(), "read-only"))
        var selectPermissionResult: Result<Unit> = Result.success(Unit)
        var skillsResult: Result<List<DshSkill>> = Result.success(emptyList())
        var commandsResult: Result<List<DshCommand>> = Result.success(emptyList())
        var resetCalled = false

        override suspend fun sessions(): Result<List<DshAdminSession>> = sessionsResult

        override suspend fun createSession(): Result<String> {
            calls += "create"
            return createResult.onSuccess { id ->
                sessionsResult = Result.success(sessionsResult.getOrNull().orEmpty() + session(id, "新会话", 3_000L))
            }
        }

        override suspend fun renameSession(sessionId: String, title: String): Result<String> {
            calls += "rename:$sessionId:$title"
            return renameResult.map {
                sessionsResult = Result.success(sessionsResult.getOrNull().orEmpty().map { item ->
                    if (item.id == sessionId) item.copy(title = title) else item
                })
                title
            }
        }

        override suspend fun archiveSession(sessionId: String, stopActivity: Boolean): Result<List<String>> {
            calls += "archive:$sessionId:$stopActivity"
            return archiveResult.map {
                sessionsResult = Result.success(sessionsResult.getOrNull().orEmpty().filterNot { item -> item.id == sessionId })
                it
            }
        }

        override suspend fun search(query: String): Result<DshSessionSearchResult> {
            calls += "search:$query"
            return searchResult
        }

        override suspend fun models(): Result<DshModelCatalog> = modelsResult

        override suspend fun selectModel(sessionId: String, selection: DshModelSelection): Result<DshModelSelection> {
            calls += "selectModel:$sessionId:${selection.provider}/${selection.model}/${selection.reasoningEffort.orEmpty()}"
            return selectModelResult.map {
                sessionsResult = Result.success(sessionsResult.getOrNull().orEmpty().map { item ->
                    if (item.id == sessionId) item.copy(model = selection) else item
                })
                it
            }
        }

        override suspend fun permissions(): Result<DshPermissionCatalog> = permissionsResult

        override suspend fun selectPermission(sessionId: String, preset: String): Result<Unit> {
            calls += "selectPermission:$sessionId:$preset"
            return selectPermissionResult.map {
                sessionsResult = Result.success(sessionsResult.getOrNull().orEmpty().map { item ->
                    if (item.id == sessionId) item.copy(currentPermission = preset) else item
                })
            }
        }

        override suspend fun skills(sessionId: String): Result<List<DshSkill>> = skillsResult

        override suspend fun commands(sessionId: String): Result<List<DshCommand>> = commandsResult

        override fun reset() {
            resetCalled = true
        }

        private fun session(id: String, title: String, updatedAt: Long) = DshAdminSession(
            id = id,
            title = title,
            updatedAt = updatedAt,
            running = false,
            blank = false,
            agentAvailable = true,
            asOfSeq = 9,
            currentPermission = "workspace-write",
            model = DshModelSelection("deepseek", "chat"),
            cwd = "/workspaces/demo",
            parentSessionId = null,
            subagent = false,
            formatStatus = "current"
        )
    }
}
