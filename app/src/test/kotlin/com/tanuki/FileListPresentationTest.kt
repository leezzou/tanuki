package com.tanuki

import com.tanuki.storage.FileLocation
import com.tanuki.storage.WorkspaceFileManager
import com.tanuki.ui.FileDisplayRequest
import com.tanuki.ui.FileDisplayResult
import com.tanuki.ui.FileListPresentation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 文件列表在刷新/搜索/换目录时的显示策略回归，防止闪空、串目录和滚动归零。 */
class FileListPresentationTest {
    private fun entry(path: String, directory: Boolean = false, bytes: Long = 0, modified: Long = 0) =
        WorkspaceFileManager.Entry(path, path.substringAfterLast('/'), directory, bytes, false, modified)

    private fun request(source: String = "WORKSPACE:", entries: List<WorkspaceFileManager.Entry> = emptyList(),
                        query: String = "", hidden: Boolean = false, sort: Int = 0) =
        FileDisplayRequest(source, entries, query, hidden, sort)

    @Test fun sameDirectoryRefreshKeepsPreviousListUntilTheNewResultIsReady() {
        val entries = listOf(entry("b.txt"), entry("a-dir", directory = true))
        val previous = FileDisplayResult(request(entries = entries), entries.sortedBy { it.name })
        // 同目录重新列举得到等值但不同实例的列表。
        val refreshed = request(entries = entries.toList())
        assertTrue(FileListPresentation.isStale(previous, refreshed))
        assertEquals("刷新期间必须保留旧列表，不能闪成空列表", previous.entries,
            FileListPresentation.visible(previous, refreshed))
    }

    @Test fun searchAndSortChangesWithinTheSameDirectoryNeverFlashEmpty() {
        val entries = listOf(entry("report.txt"), entry("photo.png"))
        val previous = FileDisplayResult(request(entries = entries), entries)
        for (changed in listOf(request(entries = entries, query = "re"), request(entries = entries, sort = 2),
            request(entries = entries, hidden = true))) {
            assertEquals(previous.entries, FileListPresentation.visible(previous, changed))
        }
    }

    @Test fun anotherDirectoryNeverShowsThePreviousDirectoryResults() {
        val old = listOf(entry("old.txt"))
        val previous = FileDisplayResult(request(source = "WORKSPACE:old", entries = old), old)
        val next = request(source = "WORKSPACE:new", entries = listOf(entry("new.txt")))
        assertTrue(FileListPresentation.visible(previous, next).isEmpty())
    }

    @Test fun completedRequestShowsItsOwnEntries() {
        val request = request(entries = listOf(entry("a.txt")))
        val result = FileDisplayResult(request, listOf(entry("a.txt")))
        assertFalse(FileListPresentation.isStale(result, request))
        assertEquals(result.entries, FileListPresentation.visible(result, request))
    }

    @Test fun equalContentReusesItemListButChangedContentDoesNot() {
        val oldItems = listOf(entry("a.txt", bytes = 1))
        val previous = FileDisplayResult(request(entries = oldItems), oldItems)
        val same = FileListPresentation.result(previous,
            FileDisplayResult(request(entries = oldItems.toList()), oldItems.toList()))
        assertSame("内容未变时复用旧条目列表，保持列表项身份稳定", previous.entries, same.entries)
        val changedItems = listOf(entry("a.txt", bytes = 2))
        val changed = FileListPresentation.result(previous,
            FileDisplayResult(request(entries = changedItems), changedItems))
        assertNotSame(previous.entries, changed.entries)
        assertEquals(changedItems, changed.entries)
    }

    @Test fun prepareFiltersHiddenAndQueryThenSortsFoldersFirst() = runBlocking {
        val b = entry("b.txt", bytes = 2)
        val a = entry("a.txt", bytes = 3)
        val hidden = entry(".hidden.txt")
        val folder = entry("folder", directory = true)
        val mixed = listOf(b, folder, hidden, a)
        val visible = FileListPresentation.prepare(request(entries = mixed))
        assertEquals(listOf("folder", "a.txt", "b.txt"), visible.map { it.name })
        assertSame("排序不能复制条目对象，key 必须稳定", b, visible[2])

        assertEquals(listOf("b.txt"), FileListPresentation.prepare(request(entries = mixed, query = "B.")).map { it.name })
        assertEquals(listOf("folder", ".hidden.txt", "a.txt", "b.txt"),
            FileListPresentation.prepare(request(entries = mixed, hidden = true)).map { it.name })
    }

    @Test fun prepareSortsByModifiedAndSizeWhenRequested() = runBlocking {
        val old = entry("old.txt", modified = 10)
        val fresh = entry("fresh.txt", modified = 20)
        val small = entry("small.txt", bytes = 1)
        val large = entry("large.txt", bytes = 99)
        assertEquals(listOf("fresh.txt", "old.txt"),
            FileListPresentation.prepare(request(entries = listOf(old, fresh), sort = 1)).map { it.name })
        assertEquals(listOf("large.txt", "small.txt"),
            FileListPresentation.prepare(request(entries = listOf(small, large), sort = 2)).map { it.name })
    }

    @Test fun sourceSeparatesLocationsWithTheSameDirectoryName() {
        assertEquals("PHONE:", FileListPresentation.source(FileLocation.PHONE, ""))
        assertEquals("WORKSPACE:inbox", FileListPresentation.source(FileLocation.WORKSPACE, "inbox"))
        assertTrue(FileListPresentation.source(FileLocation.PHONE, "inbox") !=
            FileListPresentation.source(FileLocation.WORKSPACE, "inbox"))
    }
}
