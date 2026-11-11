package com.tanuki

import com.tanuki.runtime.FileTransferProgress
import com.tanuki.runtime.OperationTiming
import com.tanuki.storage.FileLocation
import com.tanuki.ui.FileAccessMode
import com.tanuki.ui.FileAccessPresentation
import com.tanuki.ui.FileTransferPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 文件页权限 chip 与写入 sheet 的状态回归。 */
class FileAccessPresentationTest {
    @Test fun workspaceIsWritableOnlyWhenDshIsNotRunning() {
        val idle = FileAccessPresentation.state(FileLocation.WORKSPACE, false, true, false, false)
        assertEquals(FileAccessMode.WRITABLE, idle.mode)
        assertEquals("可写", idle.label)
        assertFalse(idle.canStopRuntime)

        val busy = FileAccessPresentation.state(FileLocation.WORKSPACE, true, true, false, false)
        assertEquals(FileAccessMode.READ_ONLY, busy.mode)
        assertEquals("只读", busy.label)
        assertTrue(busy.canStopRuntime)
        assertTrue("运行中应说明修改暂停而不是永久只读", busy.detail.contains("暂停"))
    }

    @Test fun installationPausesOnlyWritingAndExplainsThatBrowsingRemainsAvailable() {
        val state = FileAccessPresentation.state(FileLocation.WORKSPACE, false, true, false, false, environmentBusy = true)
        assertEquals(FileAccessMode.READ_ONLY, state.mode)
        assertTrue(state.detail.contains("可继续浏览"))
        assertTrue(state.detail.contains("完成后再写入"))
        assertFalse("环境安装不能诱导用户去停止未运行的DSH", state.canStopRuntime)
    }

    @Test fun phoneWithoutStorageGrantOffersPermissionInsteadOfWrite() {
        val state = FileAccessPresentation.state(FileLocation.PHONE, false, false, false, false)
        assertEquals(FileAccessMode.READ_ONLY, state.mode)
        assertTrue(state.needsStorageGrant)
        assertFalse(state.canGrantPhoneWrite)
        assertFalse(state.canRevokePhoneWrite)
    }

    @Test fun phoneWriteFollowsThisRunAndCanBeRevoked() {
        val readOnly = FileAccessPresentation.state(FileLocation.PHONE, true, true, false, true)
        assertEquals(FileAccessMode.READ_ONLY, readOnly.mode)
        assertTrue(readOnly.canGrantPhoneWrite)
        assertFalse(readOnly.canRevokePhoneWrite)

        val writable = FileAccessPresentation.state(FileLocation.PHONE, true, true, true, true)
        assertEquals(FileAccessMode.WRITABLE, writable.mode)
        assertEquals("可写", writable.label)
        assertTrue(writable.canRevokePhoneWrite)
        assertFalse(writable.canGrantPhoneWrite)
    }

    @Test fun phoneWriteCannotBeGrantedBeforeDshRuns() {
        val state = FileAccessPresentation.state(FileLocation.PHONE, false, true, false, false)
        assertEquals(FileAccessMode.READ_ONLY, state.mode)
        assertFalse(state.canGrantPhoneWrite)
        assertTrue(state.detail.contains("启动 DSH"))
    }

    @Test fun mountedLocationsStayReadOnlyAndExplainTheBoundary() {
        for (location in listOf(FileLocation.HOME, FileLocation.UBUNTU)) {
            val state = FileAccessPresentation.state(location, false, true, false, false)
            assertEquals(FileAccessMode.READ_ONLY, state.mode)
            assertEquals("只读", state.label)
            assertTrue(state.detail.contains("挂载路径"))
        }
    }
}

/** 传输百分比、大小与剩余时间的回归；未知总量绝不能变成伪百分比。 */
class FileTransferPresentationTest {
    private fun transfer(id: Long = 1, label: String = "复制大文件", received: Long = 0,
                         total: Long = -1, started: Long = 1_000, last: Long = 1_000) =
        FileTransferProgress(id, label, received, total, OperationTiming(started, last))

    @Test fun percentRequiresPositiveTotalAndClampsReceived() {
        assertNull(FileTransferPresentation.percent(10, -1))
        assertNull(FileTransferPresentation.percent(10, 0))
        assertEquals(0, FileTransferPresentation.percent(0, 100))
        assertEquals(25, FileTransferPresentation.percent(25, 100))
        assertEquals(100, FileTransferPresentation.percent(120, 100))
    }

    @Test fun summaryUsesByteWeightedPercentOnlyWhenEveryTotalIsKnown() {
        val known = FileTransferPresentation.summary(listOf(
            transfer(id = 1, received = 25, total = 100),
            transfer(id = 2, received = 75, total = 100),
        ))!!
        assertEquals(2, known.count)
        assertEquals(50, known.percent)
        assertEquals(100L, known.received)
        assertEquals(200L, known.total)
        assertTrue(known.allTotalsKnown)

        val mixed = FileTransferPresentation.summary(listOf(
            transfer(id = 1, received = 50, total = 100),
            transfer(id = 2, received = 10, total = -1),
        ))!!
        assertNull("任一总量未知就不能显示整体百分比", mixed.percent)
        assertFalse(mixed.allTotalsKnown)
        assertEquals(2, mixed.count)

        assertNull(FileTransferPresentation.summary(emptyList()))
    }

    @Test fun sizeTextNeverInventsTotalAndFormatsBinaryUnits() {
        assertEquals("512 B", FileTransferPresentation.bytes(512))
        assertEquals("1.0 KB", FileTransferPresentation.bytes(1024))
        assertEquals("1.5 MB", FileTransferPresentation.bytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", FileTransferPresentation.bytes(2L * 1024 * 1024 * 1024))
        assertEquals("25.0 KB / 100.0 KB", FileTransferPresentation.sizeText(25_600, 102_400))
        assertEquals("已传输 1.0 KB · 总大小未知", FileTransferPresentation.sizeText(1024, -1))
        assertEquals("正在准备文件 · 总大小未知", FileTransferPresentation.sizeText(0, 0))
    }

    @Test fun remainingTimeUsesObservedRateAndHidesWeakEstimates() {
        val measured = transfer(received = 50, total = 200, started = 1_000)
        assertEquals(3L, FileTransferPresentation.remainingSeconds(measured, 2_000))
        assertEquals("剩余约 3秒", FileTransferPresentation.remainingText(measured, 2_000))

        // 刚开始或还没有字节时不能给出虚假剩余时间。
        assertNull(FileTransferPresentation.remainingSeconds(measured, 1_500))
        assertNull(FileTransferPresentation.remainingSeconds(transfer(received = 0, total = 200), 2_000))
        assertNull(FileTransferPresentation.remainingSeconds(transfer(received = 50, total = -1), 2_000))
        assertEquals("正在估算剩余时间", FileTransferPresentation.remainingText(measured, 1_500))
        assertEquals("剩余时间未知", FileTransferPresentation.remainingText(transfer(received = 50, total = -1), 2_000))
        assertEquals("即将完成", FileTransferPresentation.remainingText(transfer(received = 200, total = 200), 2_000))
    }

    @Test fun durationFormatsMinutesAndHoursWithoutLocaleDependence() {
        assertEquals("9秒", FileTransferPresentation.duration(9))
        assertEquals("1分20秒", FileTransferPresentation.duration(80))
        assertEquals("2时3分4秒", FileTransferPresentation.duration(7384))
    }
}
