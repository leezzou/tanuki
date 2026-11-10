package com.tanuki

import com.tanuki.runtime.FileTransfers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 复制大文件时进度发布策略：合并中间帧、始终发布最终帧、取消可协作终止。 */
class FileTransferProgressTest {
    @Test fun rapidChunkUpdatesAreCoalescedAndFinalBytesAlwaysPublish() = runBlocking {
        var now = 1_000L
        val transfers = FileTransfers(publishIntervalMillis = 160) { now }
        val observed = mutableListOf<Long>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            transfers.state.collect { list -> list.singleOrNull()?.let { observed += it.received } }
        }
        try {
            transfers.track("导入项目文件", 10_000) { id ->
                repeat(50) { index -> transfers.update(id, (index + 1) * 100L, 10_000) }
                // 让收集器追上合并后的状态；固定时钟内 50 个分块只允许开始帧。
                yield()
                assertEquals(listOf(0L), observed.distinct())
                assertEquals(0L, transfers.state.value.single().received)
                transfers.complete(id, 10_000, 10_000)
                yield()
                assertEquals(10_000L, transfers.state.value.single().received)
            }
        } finally {
            collector.cancel()
        }
        assertTrue("传输结束后不能残留进度", transfers.state.value.isEmpty())
    }

    @Test fun knownTotalPublishesImmediatelyAndProgressNeverGoesBackwards() = runBlocking {
        var now = 5_000L
        val transfers = FileTransfers(publishIntervalMillis = 160) { now }
        transfers.track("导入项目文件", -1) { id ->
            transfers.update(id, 0, 4_000)
            assertEquals("总量确定后立即发布真实总量", 4_000L, transfers.state.value.single().total)
            now += 10
            transfers.update(id, 2_000, 4_000)
            assertEquals("间隔不足的中间帧合并，不按分块刷新", 0L, transfers.state.value.single().received)
            now += 200
            transfers.update(id, 2_100, 4_000)
            assertEquals(2_100L, transfers.state.value.single().received)
            now += 10
            transfers.update(id, 1_000, 4_000)
            assertEquals("乱序/回退的分块不能把进度条拉回去", 2_100L, transfers.state.value.single().received)
        }
    }

    @Test fun cancellingATransferRunsFinallyAndClearsProgressWithoutLeavingLoading() = runBlocking {
        val transfers = FileTransfers { 1_000 }
        var cleaned = false
        val worker = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                transfers.track("大文件导入", 10_000) {
                    try {
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            delay(5)
                        }
                    } finally {
                        // 与真实导入一致：取消后在 finally 清理暂存目录。
                        cleaned = true
                    }
                }
            } catch (_: CancellationException) {
                // 取消是正常路径，不产生错误弹窗。
            }
        }
        val id = transfers.state.value.single().id
        transfers.cancel(id)
        transfers.cancel(id)
        worker.join()
        assertTrue("取消必须走原调用的 finally 清理", cleaned)
        assertTrue("取消后进度条必须消失，不能永久 loading", transfers.state.value.isEmpty())
    }

    @Test fun cancellingAnUnknownTransferIsANoOp() = runBlocking {
        val transfers = FileTransfers { 1_000 }
        val id = transfers.begin("其他任务")
        transfers.cancel(id + 1)
        assertEquals(1, transfers.state.value.size)
        assertEquals(id, transfers.state.value.single().id)
    }
}
