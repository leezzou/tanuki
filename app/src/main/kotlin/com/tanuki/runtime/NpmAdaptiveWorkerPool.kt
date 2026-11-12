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
 * 在领取下一个归档前退出。注册、退役判定与退出计数共用短锁，防止过期活跃快照
 * 或提前归还名额导致多退；下载与挂起操作均在锁外。队列仍由剩余 worker 继续消费。
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

    private val workerStateLock = Any()
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
        synchronized(workerStateLock) { active.incrementAndGet() }
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
            synchronized(workerStateLock) {
                active.decrementAndGet()
                if (claimedRetirement) retiring.decrementAndGet()
            }
        }
    }

    /** 活跃数、退役名额和判定必须来自同一临界区，不能用过期快照申请新名额。 */
    private fun shouldRetire(): Boolean {
        // 外部上限回调在状态锁外读取，锁内只处理 worker 计数。
        val target = policy.current()
        return synchronized(workerStateLock) {
            if (active.get() - retiring.get() <= target) {
                false
            } else {
                retiring.incrementAndGet()
                true
            }
        }
    }
}
