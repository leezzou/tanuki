package com.tanuki.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * 自适应下载 worker 池。
 *
 * 目标并发提高时，调度协程只补到目标；目标降低时，已经完成当前归档的 worker
 * 在领取下一个归档前退出。退出名额用 CAS 限制为“活跃数 - 目标”，因此不会在
 * 同一时刻全部退出，队列仍由剩余 worker 继续消费。退出后的协程不会被重新拉起。
 */
internal class NpmAdaptiveWorkerPool(
    private val taskCount: Int,
    private val policy: NpmConcurrencyPolicy,
    private val pollMillis: Long = 300L,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    init {
        require(taskCount >= 0) { "任务数量不能为负" }
        require(pollMillis > 0) { "调度轮询间隔必须为正" }
    }

    private val active = AtomicInteger()
    private val retiring = AtomicInteger()
    private val nextIndex = AtomicInteger()
    private val completed = AtomicInteger()
    private val retired = AtomicInteger()

    /** 当前仍在运行的 worker 数（包含已领取退休名额、尚未退出的 worker）。 */
    val activeWorkers: Int get() = active.get()
    val completedTasks: Int get() = completed.get()
    val retiredWorkers: Int get() = retired.get()

    suspend fun run(task: suspend (Int) -> Unit) = coroutineScope {
        repeat(minOf(policy.current(), taskCount)) { launchWorker(task) }
        launch(dispatcher) {
            while (completed.get() < taskCount) {
                delay(pollMillis)
                while (active.get() < policy.current() && nextIndex.get() < taskCount) {
                    launchWorker(task)
                }
            }
        }
    }

    private fun CoroutineScope.launchWorker(task: suspend (Int) -> Unit) {
        active.incrementAndGet()
        launch(dispatcher) { worker(task) }
    }

    private suspend fun worker(task: suspend (Int) -> Unit) {
        var claimedRetirement = false
        try {
            while (true) {
                if (shouldRetire()) {
                    claimedRetirement = true
                    retired.incrementAndGet()
                    return
                }
                val index = nextIndex.getAndIncrement()
                if (index >= taskCount) return
                task(index)
                completed.incrementAndGet()
            }
        } finally {
            if (claimedRetirement) retiring.decrementAndGet()
            active.decrementAndGet()
        }
    }

    /**
     * 只有在“活跃数 - 已领取退休名额”仍高于目标时才领取一个退休名额。
     * 多出的 worker 会竞争同一组名额，CAS 失败者继续领取任务。
     */
    private fun shouldRetire(): Boolean {
        while (true) {
            val running = active.get()
            val target = policy.current()
            if (running <= target) return false
            val claimed = retiring.get()
            if (claimed >= running - target) return false
            if (retiring.compareAndSet(claimed, claimed + 1)) return true
        }
    }
}
