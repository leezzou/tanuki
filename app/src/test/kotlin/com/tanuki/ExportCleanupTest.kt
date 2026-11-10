package com.tanuki

import com.tanuki.runtime.FileTransfers
import com.tanuki.storage.ExportCleanup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream

/** 取消/失败后的 SAF 目标清理：不能留下空文件或截断文件，清理失败必须如实提示。 */
class ExportCleanupTest {
    @Test fun discardOnlyReportsSuccessWhenTheTargetWasActuallyRemoved() {
        assertTrue(ExportCleanup.discard { true })
        assertFalse(ExportCleanup.discard { false })
        assertFalse("删除抛异常也必须视为未清理", ExportCleanup.discard { throw SecurityException("synthetic") })
    }

    @Test fun noticesNeverPretendAnUncleanedTargetWasDeleted() {
        assertEquals("已取消导出，未完成的目标文件已删除", ExportCleanup.cancelledNotice(true))
        assertTrue(ExportCleanup.cancelledNotice(false).contains("无法自动删除"))
        assertTrue(ExportCleanup.failureNotice(true).contains("已删除"))
        assertTrue(ExportCleanup.failureNotice(false).contains("无法自动删除"))
    }

    @Test fun cancellingAfterPartialWriteDeletesTheTruncatedTarget() = runBlocking {
        val transfers = FileTransfers(publishIntervalMillis = 0) { 1L }
        val target = File.createTempFile("tanuki-export-", ".partial")
        val total = 128 * 1024
        var wrote = 0
        val input = object : InputStream() {
            override fun read(): Int = error("按块读取")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (wrote >= total) return -1
                // 拖慢分块，给取消留出“已写入部分数据”的窗口。
                if (wrote > 0) Thread.sleep(15)
                val count = minOf(length, 4096, total - wrote)
                buffer.fill(1.toByte(), offset, offset + count)
                wrote += count
                return count
            }
        }
        var notice = ""
        val job = launch(Dispatchers.Default) {
            try {
                transfers.track("导出浏览器文件", total.toLong()) { id ->
                    try {
                        target.outputStream().use { output -> transfers.copy(input, output, id, total.toLong()) }
                    } catch (cancelled: CancellationException) {
                        val cleaned = ExportCleanup.discard { target.delete() }
                        notice = ExportCleanup.cancelledNotice(cleaned)
                        throw cancelled
                    }
                }
            } catch (_: CancellationException) { }
        }
        try {
            var waited = 0
            while ((transfers.state.value.firstOrNull()?.received ?: 0L) == 0L && waited < 400) {
                delay(5); waited++
            }
            val id = transfers.state.value.single().id
            assertTrue("取消前应已写入部分数据", target.length() > 0)
            transfers.cancel(id)
            job.join()
            assertFalse("取消后不能留下空或截断的 SAF 目标", target.exists())
            assertEquals(ExportCleanup.cancelledNotice(true), notice)
            assertTrue("取消后进度条必须消失", transfers.state.value.isEmpty())
        } finally {
            target.delete(); job.cancel()
        }
    }
}
