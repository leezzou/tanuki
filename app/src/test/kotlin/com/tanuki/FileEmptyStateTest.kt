package com.tanuki

import com.tanuki.ui.FileEmptyCommand
import com.tanuki.ui.TanukiFileVisuals
import com.tanuki.ui.dispatchFileEmptyCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileEmptyStateTest {
    @Test fun `可写空目录提供导入和新建文件夹两个按钮`() {
        val actions = TanukiFileVisuals.emptyActions(query = "", hasEntries = false, writable = true)
        assertEquals("从手机导入", actions.primaryLabel)
        assertEquals(FileEmptyCommand.IMPORT, actions.primary)
        assertEquals("新建文件夹", actions.secondaryLabel)
        assertEquals(FileEmptyCommand.CREATE_FOLDER, actions.secondary)
    }

    @Test fun `搜索隐藏文件与只读位置仍只有一个真实动作`() {
        val searching = TanukiFileVisuals.emptyActions(query = "hello", hasEntries = true, writable = true)
        assertEquals("清除搜索", searching.primaryLabel)
        assertEquals(FileEmptyCommand.CLEAR_QUERY, searching.primary)
        assertNull(searching.secondary)

        val hiddenOnly = TanukiFileVisuals.emptyActions(query = "", hasEntries = true, writable = true)
        assertEquals("显示隐藏文件", hiddenOnly.primaryLabel)
        assertEquals(FileEmptyCommand.SHOW_HIDDEN, hiddenOnly.primary)
        assertNull(hiddenOnly.secondary)

        val readOnly = TanukiFileVisuals.emptyActions(query = "", hasEntries = false, writable = false)
        assertEquals("刷新", readOnly.primaryLabel)
        assertEquals(FileEmptyCommand.RELOAD, readOnly.primary)
        assertNull(readOnly.secondary)
    }

    @Test fun `导入任务不能与普通网页下载混淆`() {
        val download = com.tanuki.runtime.FileTransferProgress(1, "下载网页文件", 30, 100, com.tanuki.runtime.OperationTiming())
        val importing = com.tanuki.runtime.FileTransferProgress(2, "复制到工作区", 2, -1,
            com.tanuki.runtime.OperationTiming(), com.tanuki.runtime.FileTransferKind.IMPORT)
        assertEquals(listOf(importing), com.tanuki.ui.FileTransferPresentation.imports(listOf(download, importing)))
    }

    @Test fun `瞬间结束的导入保留刷新信号且普通下载不触发`() {
        val transfers = com.tanuki.runtime.FileTransfers { 1_000L }
        val download = transfers.begin("网页下载")
        transfers.finish(download)
        assertEquals(0L, transfers.importRevision.value)
        // 没有订阅开始帧，直接开始并完成；后来的订阅者仍能取得完成序号。
        val importing = transfers.begin("导入项目", kind = com.tanuki.runtime.FileTransferKind.IMPORT)
        transfers.finish(importing)
        transfers.finish(importing)
        assertEquals(1L, transfers.importRevision.value)
        assertEquals(emptyList<com.tanuki.runtime.FileTransferProgress>(), transfers.state.value)
    }

    @Test fun `空态按钮回调按命令分派`() {
        val calls = mutableListOf<String>()
        fun dispatch(command: FileEmptyCommand) = dispatchFileEmptyCommand(
            command = command,
            onImport = { calls += "从手机导入" },
            onCreateFolder = { calls += "新建文件夹" },
            onClearQuery = { calls += "清除搜索" },
            onShowHidden = { calls += "显示隐藏文件" },
            onReload = { calls += "刷新" },
        )
        dispatch(FileEmptyCommand.IMPORT)
        dispatch(FileEmptyCommand.CREATE_FOLDER)
        dispatch(FileEmptyCommand.CLEAR_QUERY)
        dispatch(FileEmptyCommand.SHOW_HIDDEN)
        dispatch(FileEmptyCommand.RELOAD)
        assertEquals(listOf("从手机导入", "新建文件夹", "清除搜索", "显示隐藏文件", "刷新"), calls)
    }
}
