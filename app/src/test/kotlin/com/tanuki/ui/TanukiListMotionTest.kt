package com.tanuki.ui

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.unit.IntOffset
import com.tanuki.storage.WorkspaceFileManager
import com.tanuki.web.DshAdminSession
import com.tanuki.ui.design.TanukiListMotion
import com.tanuki.ui.design.TanukiMotionEasing
import com.tanuki.ui.design.TanukiMotionSpecs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** 验证列表身份和实际 spec；Host 时间采样不能替代设备列表帧验证。 */
class TanukiListMotionTest {
    private val normal = TanukiMotionSpecs(false)
    private val reduced = TanukiMotionSpecs(true)
    private val entries = listOf(
        WorkspaceFileManager.Entry("a.txt", "a.txt", false, 1, false, 10),
        WorkspaceFileManager.Entry("b.txt", "b.txt", false, 9, false, 20),
    )

    private fun request(query: String = "", sort: Int = 0, source: String = "WORKSPACE:", hidden: Boolean = false,
                        items: List<WorkspaceFileManager.Entry> = entries) =
        FileDisplayRequest(source, items, query, hidden, sort)

    @Test fun sortDeleteAndInsertKeepDatasetIdentityAndAnimatePosition() = runBlocking {
        val original = request()
        val result = FileDisplayResult(original, FileListPresentation.prepare(original))
        val dataset = fileListMotionDataset(result, original)
        for (changed in listOf(request(sort = 2), request(items = entries.take(1)),
            request(items = entries + entries.first().copy(path = "c.txt", name = "c.txt")))) {
            assertEquals(dataset, fileListMotionDataset(result, changed))
            val prepared = FileDisplayResult(changed, FileListPresentation.prepare(changed))
            assertEquals(dataset, fileListMotionDataset(prepared, changed))
        }
        val sorted = FileListPresentation.prepare(request(sort = 2))
        assertEquals(listOf("b.txt", "a.txt"), sorted.map { it.path })
        val specs = dataset.motion(normal)
        val placement = specs.placement!!
        assertEquals(TanukiMotionEasing.standard, (placement as TweenSpec<*>).easing)
        val animation = TargetBasedAnimation(placement, IntOffset.VectorConverter, IntOffset.Zero, IntOffset(0, 100))
        assertEquals(240_000_000L, animation.durationNanos)
        assertTrue(animation.getValueFromNanos(120_000_000L).y in 1..99)
        assertEquals(IntOffset(0, 100), animation.getValueFromNanos(240_000_000L))
        assertEquals(160, (specs.fadeIn as TweenSpec<*>).durationMillis)
        assertEquals(TanukiMotionEasing.enter, specs.fadeIn.easing)
        assertEquals(140, (specs.fadeOut as TweenSpec<*>).durationMillis)
        assertEquals(TanukiMotionEasing.exit, specs.fadeOut.easing)
    }

    @Test fun searchIdentityChangesAtAsyncResultCommitIncludingClearingQuery() = runBlocking {
        val all = request()
        val allResult = FileDisplayResult(all, entries)
        val search = request(query = "a")
        val oldDataset = fileListMotionDataset(allResult, search)
        // 后台过滤未完成时，画面仍是旧结果；请求本身已关闭行动画。
        assertEquals(fileListMotionDataset(allResult, all), oldDataset)
        assertImmediate(oldDataset.motion(normal, filtering = search.query.isNotEmpty()))
        val searchResult = FileDisplayResult(search, FileListPresentation.prepare(search))
        val searchDataset = fileListMotionDataset(searchResult, search)
        assertNotEquals(oldDataset, searchDataset)
        assertImmediate(searchDataset.motion(normal))
        // 清空查询后旧筛选结果仍保留；新结果提交时才换宿主身份，不能给整批结果补位移。
        assertEquals(searchDataset, fileListMotionDataset(searchResult, all))
        assertImmediate(fileListMotionDataset(searchResult, all).motion(normal))
        assertNotEquals(searchDataset, fileListMotionDataset(allResult, all))
        for (query in listOf("a", "ab", "abc")) {
            val next = request(query)
            val dataset = fileListMotionDataset(FileDisplayResult(next, emptyList()), next)
            assertImmediate(dataset.motion(normal))
        }
    }

    @Test fun directoryLocationAndHiddenFilterReplaceDatasetWhileSortDoesNot() {
        val original = request()
        val previous = FileDisplayResult(original, entries)
        val old = fileListMotionDataset(previous, original)
        for (changed in listOf(request(source = "WORKSPACE:other"), request(source = "PHONE:"))) {
            assertTrue(FileListPresentation.visible(previous, changed).isEmpty())
            assertNotEquals(old, fileListMotionDataset(previous, changed))
        }
        val hidden = request(hidden = true)
        assertNotEquals(old, fileListMotionDataset(FileDisplayResult(hidden, entries), hidden))
    }

    @Test fun sessionPinAndArchiveKeepIdentityButQueryAndFilterReplaceIt() {
        val sessions = listOf(
            DshAdminSession("a", "合成会话 A", 10, false, false, true, 1, null, null, null, null, false, null),
            DshAdminSession("b", "合成会话 B", 20, false, false, true, 2, null, null, null, null, false, null),
        )
        val state = DshSessionManagerState(sessions = sessions)
        val original = sessionListMotionDataset(state)
        val pinned = state.copy(pinnedIds = setOf("a"))
        val archived = pinned.copy(sessions = sessions.drop(1), batchDone = 1, batchTotal = 2)
        assertEquals(listOf("b", "a"), state.visibleSessions.map { it.id })
        assertEquals(listOf("a", "b"), pinned.visibleSessions.map { it.id })
        assertEquals(listOf("b"), archived.visibleSessions.map { it.id })
        for (changed in listOf(pinned, archived)) {
            assertEquals(original, sessionListMotionDataset(changed))
            assertNotNull(sessionListMotionDataset(changed).motion(normal).placement)
        }
        assertNotEquals(original, sessionListMotionDataset(state.copy(query = "test")))
        assertNotEquals(original, sessionListMotionDataset(state.copy(filter = DshSessionFilter.UNREAD)))
        assertImmediate(sessionListMotionDataset(state.copy(query = "test")).motion(normal))
    }

    @Test fun reducedMotionDisablesAllSpecsForEveryDataset() {
        for (dataset in listOf(TanukiListDataset("files"), TanukiListDataset("files", "query"),
            sessionListMotionDataset(DshSessionManagerState()))) assertImmediate(dataset.motion(reduced))
    }

    private fun assertImmediate(specs: TanukiListMotion) {
        assertNull(specs.placement)
        assertNull(specs.fadeIn)
        assertNull(specs.fadeOut)
    }
}
