package com.tanuki.runtime

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.InputStream
import java.io.OutputStream

/** Independent of installation state: a web download must not overwrite a runtime operation. */
data class FileTransferProgress(val id: Long, val label: String, val received: Long, val total: Long,
    val timing: OperationTiming)

/**
 * 文件传输进度的唯一发布点。
 *
 * 复制大文件时调用方会按 64 KiB 分块回调；若每个分块都发布一个新列表，界面会在主线程上
 * 被高频重组拖垮，进而出现列表跳动/闪烁。这里按 [publishIntervalMillis] 合并中间进度，
 * 只保证开始、总量变化与最终结果立即发布；进度在同一总量下单调前进。
 */
class FileTransfers(
    private val publishIntervalMillis: Long = 160,
    private val clock: () -> Long,
) {
    val state = MutableStateFlow<List<FileTransferProgress>>(emptyList())
    private var sequence = 0L
    private class Published(val received: Long, val total: Long, val at: Long)
    private val published = HashMap<Long, Published>()
    private val running = HashMap<Long, Job>()
    private val cancelled = HashSet<Long>()

    @Synchronized fun begin(label: String, total: Long = -1): Long {
        val id = ++sequence
        val now = clock()
        published[id] = Published(0, total, now)
        state.value = state.value + FileTransferProgress(id, label, 0, total, OperationTiming(now, now))
        return id
    }

    /** 中间进度；高频率调用会被合并，不必由调用方自己节流。 */
    @Synchronized fun update(id: Long, received: Long, total: Long = -1) {
        publish(id, received, total, force = false)
    }

    /** 传输结束前的最后一帧必须发布；未知总大小也要显示最终字节数。 */
    @Synchronized fun complete(id: Long, received: Long, total: Long = -1) {
        publish(id, received, total, force = true)
    }

    private fun publish(id: Long, received: Long, total: Long, force: Boolean) {
        if (state.value.none { it.id == id }) return
        val next = received.coerceAtLeast(0)
        val previous = published[id]
        val now = clock()
        // 同一总量下进度只前进：重复或乱序到达的旧分块不能把进度条拉回去。
        if (previous != null && total == previous.total && next < previous.received) return
        val final = total >= 0 && next >= total
        val newTotal = previous == null || total != previous.total
        val sinceLast = if (previous == null) Long.MAX_VALUE else now - previous.at
        if (!force && !final && !newTotal && sinceLast < publishIntervalMillis) return
        published[id] = Published(next, total, now)
        state.value = state.value.map { item ->
            if (item.id != id) item else item.copy(received = next, total = total,
                timing = if (next != item.received || total != item.total) item.timing.copy(lastActivityAtMillis = now) else item.timing)
        }
    }

    @Synchronized fun finish(id: Long) {
        published.remove(id)
        running.remove(id)
        cancelled.remove(id)
        state.value = state.value.filterNot { it.id == id }
    }

    /**
     * 协作取消一次传输。协程在下一个 [ensureActive] 检查点抛出 CancellationException，
     * 由原调用方的 finally 清理暂存目录；界面只收到 finish 后的稳定状态。
     */
    @Synchronized fun cancel(id: Long) {
        if (state.value.none { it.id == id }) return
        cancelled += id
        running[id]?.cancel()
    }

    suspend fun <T> track(label: String, total: Long = -1, block: suspend (Long) -> T): T = coroutineScope {
        val id = begin(label, total)
        val job = checkNotNull(currentCoroutineContext()[Job]) { "文件传输必须在协程中启动" }
        synchronized(this@FileTransfers) {
            if (id in cancelled) job.cancel()
            running[id] = job
        }
        try { return@coroutineScope block(id) } finally { finish(id) }
    }

    /** Caller owns stream close/flush; progress stays active until track() exits. */
    suspend fun copy(input: InputStream, output: OutputStream, id: Long, total: Long = -1) {
        val buffer = ByteArray(64 * 1024)
        var received = 0L
        update(id, 0, total)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
            received += count
            update(id, received, total)
        }
        currentCoroutineContext().ensureActive()
        check(total < 0 || received == total) { "文件长度与声明不一致，请重新下载" }
        complete(id, received, total)
    }
}
