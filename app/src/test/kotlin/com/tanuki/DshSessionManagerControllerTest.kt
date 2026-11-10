package com.tanuki

import com.tanuki.ui.DshModelPresentation
import com.tanuki.ui.DshPermissionPresentation
import com.tanuki.ui.DshReadStore
import com.tanuki.ui.DshSessionCapabilities
import com.tanuki.ui.DshSessionFilter
import com.tanuki.ui.DshSessionFiltering
import com.tanuki.ui.DshSessionGrouping
import com.tanuki.ui.DshSessionHosts
import com.tanuki.ui.DshSessionManagerController
import com.tanuki.ui.DshSessionPinStore
import com.tanuki.ui.NoopDshSessionPinStore
import com.tanuki.runtime.RemoteHostConfig
import com.tanuki.runtime.RemoteHostStatus
import com.tanuki.runtime.RemotePhase
import com.tanuki.web.DshAdminSession
import com.tanuki.web.DshApiException
import com.tanuki.web.DshCatalogModel
import com.tanuki.web.DshCommand
import com.tanuki.web.DshModelCatalog
import com.tanuki.web.DshModelGroup
import com.tanuki.web.DshModelSelection
import com.tanuki.web.DshPermissionCatalog
import com.tanuki.web.DshPermissionOption
import com.tanuki.web.DshReasoningEffort
import com.tanuki.web.DshSessionAdminSource
import com.tanuki.web.DshSessionSearchResult
import com.tanuki.web.DshSkill
import kotlinx.coroutines.CompletableDeferred
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

    @Test fun pinnedSessionsSortFirstAndSurviveRefresh() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 2_000L), session("s2", "乙", 1_000L)))
        val pins = MemoryPinStore()
        val controller = controller(source, MemoryReadStore(), pins)
        controller.refresh()
        assertEquals(listOf("s1", "s2"), controller.state.value.visibleSessions.map { it.id })

        controller.togglePinned("s2")

        assertEquals(setOf("s2"), controller.state.value.pinnedIds)
        assertEquals(listOf("s2", "s1"), controller.state.value.visibleSessions.map { it.id })
        assertTrue(controller.state.value.notice.orEmpty().contains("置顶"))
        controller.refresh()
        assertEquals(setOf("s2"), controller.state.value.pinnedIds)
        assertEquals(listOf("s2", "s1"), controller.state.value.visibleSessions.map { it.id })

        controller.togglePinned("s2")
        assertEquals(listOf("s1", "s2"), controller.state.value.visibleSessions.map { it.id })
    }

    @Test fun pinStoreFailureDoesNotShowUnsavedPin() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 2_000L)))
        val pins = object : DshSessionPinStore {
            override fun pinnedIds(): Set<String> = emptySet()
            override fun setPinned(sessionId: String, pinned: Boolean) = throw IllegalStateException("磁盘失败")
        }
        val controller = controller(source, MemoryReadStore(), pins)
        controller.refresh()

        controller.togglePinned("s1")

        assertTrue(controller.state.value.pinnedIds.isEmpty())
        assertTrue(controller.state.value.error.orEmpty().contains("置顶保存失败"))
    }

    @Test fun refreshWithExistingListUsesRefreshingInsteadOfFullLoading() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 1_000L)))
        val controller = controller(source, MemoryReadStore())
        controller.refresh()
        source.sessionsAwait = CompletableDeferred()

        controller.refresh()

        val refreshing = controller.state.value
        assertTrue(refreshing.refreshing)
        assertFalse(refreshing.loading)
        assertEquals(listOf("s1"), refreshing.sessions.map { it.id })

        source.sessionsAwait?.complete(Unit)
        source.sessionsAwait = null
        assertFalse(controller.state.value.refreshing)
        assertEquals(listOf("s1"), controller.state.value.sessions.map { it.id })
    }

    @Test fun initialLoadFailureExposesRetryableErrorState() {
        val source = FakeSource()
        source.sessionsResult = Result.failure(DshApiException("session/list-unavailable"))
        val controller = controller(source, MemoryReadStore())

        controller.refresh()

        val failed = controller.state.value
        assertTrue(failed.initialLoadFailed)
        assertFalse(failed.hasLoaded)
        assertFalse(failed.loading)
        assertTrue(failed.loadError.orEmpty().contains("官方接口返回"))
        assertTrue(failed.sessions.isEmpty())

        source.sessionsResult = Result.success(emptyList())
        controller.refresh()

        val recovered = controller.state.value
        assertFalse(recovered.initialLoadFailed)
        assertTrue(recovered.hasLoaded)
        assertNull(recovered.loadError)
    }

    @Test fun modelSwitchFailureShowsChineseReasonAndKeepsSelection() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 1_000L)))
        source.selectModelResult = Result.failure(DshApiException("session/model-unavailable"))
        val controller = controller(source, MemoryReadStore())
        controller.refresh()

        controller.selectModel("s1", DshModelSelection("deepseek", "other", "high"))

        val state = controller.state.value
        assertFalse(state.busy)
        assertEquals(DshModelSelection("deepseek", "chat"), state.sessions.single().model)
        assertEquals("所选模型不可用", state.modelSelectionError?.message)
        assertEquals("s1", state.modelSelectionError?.sessionId)
        assertTrue(state.error.orEmpty().contains("模型切换失败"))
    }

    @Test fun permissionSwitchFailureShowsChineseReasonAndKeepsCurrentPreset() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 1_000L)))
        source.selectPermissionResult = Result.failure(DshApiException("permission/denied"))
        val controller = controller(source, MemoryReadStore())
        controller.refresh()

        controller.selectPermission("s1", "read-only")

        val state = controller.state.value
        assertFalse(state.busy)
        assertEquals("workspace-write", state.sessions.single().currentPermission)
        assertEquals("官方拒绝了权限切换", state.permissionSelectionError?.message)
        assertEquals("s1", state.permissionSelectionError?.sessionId)
        assertTrue(state.error.orEmpty().contains("权限切换失败"))
    }

    @Test fun delayedModelFailureAfterClosingDetailStaysVisibleAndBoundToSession() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(
            session("s1", "甲", 2_000L),
            session("s2", "乙", 1_000L)
        ))
        source.selectModelAwait = CompletableDeferred()
        source.selectModelResult = Result.failure(DshApiException("session/model-unavailable"))
        val controller = controller(source, MemoryReadStore())
        controller.refresh()
        controller.openSession("s1")
        controller.selectModel("s1", DshModelSelection("deepseek", "other"))

        controller.closeDetail()
        assertNull(controller.state.value.selectedId)
        source.selectModelAwait?.complete(Unit)
        source.selectModelAwait = null

        val failed = controller.state.value
        assertFalse(failed.busy)
        assertEquals("s1", failed.modelSelectionError?.sessionId)
        assertEquals("所选模型不可用", failed.modelSelectionError?.message)
        assertTrue(failed.error.orEmpty().contains("会话 s1"))
        assertTrue(failed.error.orEmpty().contains("模型切换失败"))

        // 打开另一会话后，面板错误仍只属于发起会话，不会显示在新会话下。
        controller.openSession("s2")
        assertEquals("s1", controller.state.value.modelSelectionError?.sessionId)
        assertEquals("s2", controller.state.value.selectedId)
    }

    @Test fun delayedPermissionFailureAfterClosingDetailStaysVisibleAndBoundToSession() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(
            session("s1", "甲", 2_000L),
            session("s2", "乙", 1_000L)
        ))
        source.selectPermissionAwait = CompletableDeferred()
        source.selectPermissionResult = Result.failure(DshApiException("permission/denied"))
        val controller = controller(source, MemoryReadStore())
        controller.refresh()
        controller.openSession("s1")
        controller.selectPermission("s1", "read-only")

        controller.closeDetail()
        source.selectPermissionAwait?.complete(Unit)
        source.selectPermissionAwait = null

        val failed = controller.state.value
        assertFalse(failed.busy)
        assertEquals("s1", failed.permissionSelectionError?.sessionId)
        assertEquals("官方拒绝了权限切换", failed.permissionSelectionError?.message)
        assertTrue(failed.error.orEmpty().contains("会话 s1"))
        assertTrue(failed.error.orEmpty().contains("权限切换失败"))

        controller.openSession("s2")
        assertEquals("s1", controller.state.value.permissionSelectionError?.sessionId)
        assertEquals("s2", controller.state.value.selectedId)
    }

    @Test fun permissionGuidanceIsPlainChineseAndCustomIsReadOnly() {
        assertTrue(DshPermissionPresentation.guidance("read-only").contains("只读"))
        assertTrue(DshPermissionPresentation.guidance("workspace-write").contains("工作区"))
        assertTrue(DshPermissionPresentation.guidance("danger-full-access").contains("工作区边界"))
        assertFalse(DshPermissionPresentation.canSelect("custom"))
        assertTrue(DshPermissionPresentation.canSelect("workspace-write"))
        assertEquals(3, DshPermissionPresentation.fullAccessRisks().size)
    }

    @Test fun modelSelectionLabelUsesCatalogEffortName() {
        val catalog = DshModelCatalog(
            default = DshModelSelection("deepseek", "chat", "high"),
            groups = listOf(
                DshModelGroup(
                    id = "deepseek",
                    name = "DeepSeek",
                    models = listOf(
                        DshCatalogModel(
                            id = "chat",
                            name = "Chat",
                            description = null,
                            efforts = listOf(DshReasoningEffort("high", "高", "更慢但更深入")),
                            defaultEffort = "high"
                        )
                    )
                )
            ),
            failures = emptyList()
        )
        assertEquals(
            "deepseek / chat · 高",
            DshModelPresentation.selectionLabel(catalog, DshModelSelection("deepseek", "chat", "high"))
        )
    }

    @Test fun deleteIsExplicitlyMarkedUnsupported() {
        assertFalse(DshSessionCapabilities.DELETE_SUPPORTED)
        assertTrue(DshSessionCapabilities.DELETE_UNAVAILABLE_REASON.contains("没有会话硬删除"))
        assertTrue(DshSessionCapabilities.archiveSupported())
    }

    @Test fun groupingUsesCwdAndKeepsPinnedFirstInsideFolder() {
        val all = listOf(
            session("s1", "甲", 2_000L).copy(cwd = "/work/a"),
            session("s2", "乙", 1_000L).copy(cwd = "/work/b"),
            session("s3", "丙", 1_500L).copy(cwd = null),
            session("s4", "丁", 3_000L).copy(cwd = "/work/a")
        )
        val visible = DshSessionFiltering.visible(all, "", DshSessionFilter.ALL, emptySet(), setOf("s4"))

        val groups = DshSessionGrouping.groups(visible)

        assertEquals(listOf("a", "b", "未分组"), groups.map { it.title })
        assertEquals(listOf("s4", "s1"), groups.first { it.key == "/work/a" }.sessions.map { it.id })
        assertEquals("/work/b", groups.first { it.title == "b" }.detail)
        assertEquals("官方未返回工作目录", groups.last().detail)
    }

    @Test fun batchArchiveStopsRunningSessionsAndReportsPartialFailure() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(
            session("s1", "甲", 3_000L, running = true),
            session("s2", "乙", 2_000L),
            session("s3", "丙", 1_000L, running = true)
        ))
        source.archiveFailureIds = setOf("s3")
        val controller = controller(source, MemoryReadStore())
        controller.refresh()

        controller.archiveSessions(listOf("s1", "s2", "s3"), stopRunning = true)

        assertEquals(
            listOf("archive:s1:true", "archive:s2:false", "archive:s3:true"),
            source.calls
        )
        val state = controller.state.value
        assertFalse(state.busy)
        assertEquals(0, state.batchDone)
        assertEquals(0, state.batchTotal)
        assertEquals("已归档 2 个会话，1 个失败", state.notice)
        assertTrue(state.error.orEmpty().contains("未归档"))
        assertEquals(listOf("s3"), state.sessions.map { it.id })
    }

    @Test fun batchArchiveRejectedInSafeMode() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 1_000L)))
        val controller = controller(source, MemoryReadStore())
        controller.refresh()
        controller.setReadOnly(true)

        controller.archiveSessions(listOf("s1"), stopRunning = true)

        assertTrue(source.calls.isEmpty())
        assertTrue(controller.state.value.error.orEmpty().contains("只读"))
    }

    @Test fun hostListShowsLocalAndSavedRemoteStates() {
        val localOnly = DshSessionHosts.list(
            localRunning = true,
            saved = null,
            status = RemoteHostStatus(),
            busy = false
        )
        assertEquals(listOf("本机", "远程主机"), localOnly.map { it.label })
        assertTrue(localOnly[0].active)
        assertEquals("运行中", localOnly[0].detail)
        assertFalse(localOnly[1].available)
        assertEquals("未保存配置", localOnly[1].detail)

        val config = RemoteHostConfig(host = "pc.local", user = "dev")
        val connected = DshSessionHosts.list(
            localRunning = true,
            saved = config,
            status = RemoteHostStatus(phase = RemotePhase.REMOTE, host = "pc.local"),
            busy = false
        )
        assertEquals("pc.local", connected[1].label)
        assertEquals("已连接", connected[1].detail)
        assertTrue(connected[1].active)
        assertFalse(connected[0].active)
    }

    @Test fun hostListMarksConnectingRemoteBusyAndUnselectable() {
        val connecting = DshSessionHosts.list(
            localRunning = false,
            saved = RemoteHostConfig(host = "pc.local", user = "dev"),
            status = RemoteHostStatus(phase = RemotePhase.CONNECTING, host = "pc.local"),
            busy = true
        )
        val remote = connecting[1]
        assertEquals("连接中", remote.detail)
        assertTrue(remote.busy)
        assertFalse(remote.available)
        assertFalse(remote.active)

        val pending = DshSessionHosts.list(
            localRunning = true,
            saved = RemoteHostConfig(host = "pc.local", user = "dev"),
            status = RemoteHostStatus(),
            busy = false,
            pendingHostKey = true
        )[1]
        assertEquals("等待主机密钥确认", pending.detail)
        assertTrue(pending.busy)
        assertFalse(pending.available)
    }

    @Test fun openingSessionMarksItReadWithoutTouchingOfficialData() {        val source = FakeSource()
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

    @Test fun closeResetsInFlightDirectoryLoadingAndAllowsRetry() {
        val source = FakeSource()
        source.modelsAwait = CompletableDeferred()
        source.permissionsAwait = CompletableDeferred()
        source.skillsAwait = CompletableDeferred()
        val controller = controller(source, MemoryReadStore())

        controller.loadModels()
        controller.loadPermissions()
        controller.loadSessionCatalog("s1")
        assertTrue(controller.state.value.modelsLoading)
        assertTrue(controller.state.value.permissionsLoading)
        assertTrue(controller.state.value.catalogLoading)

        controller.close()
        assertFalse(controller.state.value.modelsLoading)
        assertFalse(controller.state.value.permissionsLoading)
        assertFalse(controller.state.value.catalogLoading)

        source.modelsAwait?.complete(Unit)
        source.permissionsAwait?.complete(Unit)
        source.skillsAwait?.complete(Unit)
        source.modelsAwait = null
        source.permissionsAwait = null
        source.skillsAwait = null
        source.modelsResult = Result.success(DshModelCatalog(DshModelSelection("local", "chat"), emptyList(), emptyList()))
        source.permissionsResult = Result.success(DshPermissionCatalog(emptyList(), emptyList(), "read-only"))
        source.skillsResult = Result.success(listOf(DshSkill("local-review", "本地", null, true, null)))

        controller.loadModels()
        controller.loadPermissions()
        controller.loadSessionCatalog("s1")
        assertEquals("local", controller.state.value.models?.default?.provider)
        assertEquals("read-only", controller.state.value.permissions?.defaultPreset)
        assertEquals("local-review", controller.state.value.skills.single().name)
    }

    @Test fun slowListCreateFailureClearsLoadingAndUnlocksRefresh() {
        val source = FakeSource()
        source.sessionsAwait = CompletableDeferred()
        val controller = controller(source, MemoryReadStore())
        controller.refresh()
        assertTrue(controller.state.value.loading)

        source.createResult = Result.failure(DshApiException("session/conflict"))
        controller.createSession("")

        assertFalse(controller.state.value.loading)
        assertFalse(controller.state.value.busy)
        assertTrue(controller.state.value.error.orEmpty().contains("会话目录冲突"))
        source.sessionsAwait?.complete(Unit)
        source.sessionsAwait = null
    }

    @Test fun writeRefreshFailureClearsBusyAndLoading() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 1_000L)))
        source.createResult = Result.success("new")
        val controller = controller(source, MemoryReadStore())
        controller.refresh()
        source.sessionsFailureAtCall = 2

        controller.createSession("")

        assertFalse(controller.state.value.busy)
        assertFalse(controller.state.value.loading)
        assertTrue(controller.state.value.error.orEmpty().contains("列表刷新失败"))
    }

    @Test fun endpointChangeClearsRemoteCatalogsAndDropsInFlightResponse() {
        val source = FakeSource()
        source.sessionsResult = Result.success(listOf(session("s1", "甲", 1_000L)))
        source.modelsResult = Result.success(DshModelCatalog(DshModelSelection("remote", "chat"), emptyList(), emptyList()))
        source.permissionsResult = Result.success(DshPermissionCatalog(
            options = listOf(DshPermissionOption("read-only", "只读", null)),
            defaultOptions = emptyList(),
            defaultPreset = "read-only"
        ))
        val controller = controller(source, MemoryReadStore())
        controller.onEndpointChanged("remote-origin")
        controller.refresh()
        controller.loadModels()
        controller.loadPermissions()
        assertEquals("remote", controller.state.value.models?.default?.provider)

        source.skillsAwait = CompletableDeferred()
        controller.loadSessionCatalog("s1")
        assertTrue(controller.state.value.catalogLoading)
        source.resetCalled = false

        controller.onEndpointChanged("local-origin")

        assertNull(controller.state.value.models)
        assertNull(controller.state.value.permissions)
        assertTrue(controller.state.value.skills.isEmpty())
        assertFalse(controller.state.value.catalogLoading)
        assertTrue(source.resetCalled)

        source.skillsAwait?.complete(Unit)
        source.skillsAwait = null
        assertTrue(controller.state.value.skills.isEmpty())

        source.modelsResult = Result.success(DshModelCatalog(DshModelSelection("local", "chat"), emptyList(), emptyList()))
        source.permissionsResult = Result.success(DshPermissionCatalog(emptyList(), emptyList(), "workspace-write"))
        source.skillsResult = Result.success(listOf(DshSkill("local-review", "本地", null, true, null)))
        controller.loadModels()
        controller.loadPermissions()
        controller.loadSessionCatalog("s1")
        assertEquals("local", controller.state.value.models?.default?.provider)
        assertEquals("workspace-write", controller.state.value.permissions?.defaultPreset)
        assertEquals("local-review", controller.state.value.skills.single().name)
    }

    private fun controller(
        source: DshSessionAdminSource,
        read: DshReadStore,
        pins: DshSessionPinStore = NoopDshSessionPinStore
    ) = DshSessionManagerController(source, read, CoroutineScope(Dispatchers.Unconfined), pins)

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

    private class MemoryPinStore(val map: MutableSet<String> = mutableSetOf()) : DshSessionPinStore {
        override fun pinnedIds(): Set<String> = map.toSet()
        override fun setPinned(sessionId: String, pinned: Boolean) {
            if (pinned) map += sessionId else map -= sessionId
        }
    }

    private class FakeSource : DshSessionAdminSource {
        val calls = mutableListOf<String>()
        var sessionsResult: Result<List<DshAdminSession>> = Result.success(emptyList())
        var createResult: Result<String> = Result.success("new")
        var renameResult: Result<String> = Result.success("renamed")
        var archiveResult: Result<List<String>> = Result.success(emptyList())
        var archiveFailureIds: Set<String> = emptySet()
        var searchResult: Result<DshSessionSearchResult> = Result.success(DshSessionSearchResult(emptyList(), false))
        var modelsResult: Result<DshModelCatalog> = Result.success(DshModelCatalog(null, emptyList(), emptyList()))
        var selectModelResult: Result<DshModelSelection> = Result.success(DshModelSelection("deepseek", "chat"))
        var permissionsResult: Result<DshPermissionCatalog> = Result.success(DshPermissionCatalog(emptyList(), emptyList(), "read-only"))
        var selectPermissionResult: Result<Unit> = Result.success(Unit)
        var skillsResult: Result<List<DshSkill>> = Result.success(emptyList())
        var commandsResult: Result<List<DshCommand>> = Result.success(emptyList())
        var resetCalled = false
        var sessionsAwait: CompletableDeferred<Unit>? = null
        var modelsAwait: CompletableDeferred<Unit>? = null
        var permissionsAwait: CompletableDeferred<Unit>? = null
        var selectModelAwait: CompletableDeferred<Unit>? = null
        var selectPermissionAwait: CompletableDeferred<Unit>? = null
        var skillsAwait: CompletableDeferred<Unit>? = null
        var sessionsFailureAtCall: Int? = null
        private var sessionsCalls = 0

        override suspend fun sessions(): Result<List<DshAdminSession>> {
            sessionsCalls++
            sessionsAwait?.await()
            if (sessionsFailureAtCall == sessionsCalls) return Result.failure(DshApiException("session/conflict"))
            return sessionsResult
        }

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
            if (sessionId in archiveFailureIds) return Result.failure(DshApiException("workspace/session-active"))
            return archiveResult.map {
                sessionsResult = Result.success(sessionsResult.getOrNull().orEmpty().filterNot { item -> item.id == sessionId })
                it
            }
        }

        override suspend fun search(query: String): Result<DshSessionSearchResult> {
            calls += "search:$query"
            return searchResult
        }

        override suspend fun models(): Result<DshModelCatalog> {
            modelsAwait?.await()
            return modelsResult
        }

        override suspend fun selectModel(sessionId: String, selection: DshModelSelection): Result<DshModelSelection> {
            calls += "selectModel:$sessionId:${selection.provider}/${selection.model}/${selection.reasoningEffort.orEmpty()}"
            selectModelAwait?.await()
            return selectModelResult.map {
                sessionsResult = Result.success(sessionsResult.getOrNull().orEmpty().map { item ->
                    if (item.id == sessionId) item.copy(model = selection) else item
                })
                it
            }
        }

        override suspend fun permissions(): Result<DshPermissionCatalog> {
            permissionsAwait?.await()
            return permissionsResult
        }

        override suspend fun selectPermission(sessionId: String, preset: String): Result<Unit> {
            calls += "selectPermission:$sessionId:$preset"
            selectPermissionAwait?.await()
            return selectPermissionResult.map {
                sessionsResult = Result.success(sessionsResult.getOrNull().orEmpty().map { item ->
                    if (item.id == sessionId) item.copy(currentPermission = preset) else item
                })
            }
        }

        override suspend fun skills(sessionId: String): Result<List<DshSkill>> {
            skillsAwait?.await()
            return skillsResult
        }

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
