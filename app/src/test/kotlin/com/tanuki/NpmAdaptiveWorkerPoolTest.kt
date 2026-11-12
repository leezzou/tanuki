package com.tanuki

import com.tanuki.runtime.NpmAdaptiveWorkerPool
import com.tanuki.runtime.NpmConcurrencyPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray

class NpmAdaptiveWorkerPoolTest {
    /**
     * 初始 16 路，启动后目标降到 8：超出的 8 个 worker 只能在当前归档结束后退出，
     * 不能同时全退；剩余 8 个继续消费队列，最终全部归档完成。
     */
    @Test
    fun downshiftRetiresExcessWorkersAfterCurrentTaskAndKeepsQueueDraining() = runBlocking {
        assertGatedDownshiftContract()
    }

    /** 多轮同步放行当前任务，验证并行完成时不会重复领取退役名额。 */
    @Test
    fun simultaneousTaskCompletionDoesNotOverRetireWorkers() = runBlocking {
        repeat(32) { assertGatedDownshiftContract() }
    }

    private suspend fun assertGatedDownshiftContract() = coroutineScope {
        val policy = NpmConcurrencyPolicy(initial = 8, maximum = 16, step = 8, growAfter = 1)
        policy.recordSuccess()
        assertEquals(16, policy.current())

        val tasks = 96
        val pool = NpmAdaptiveWorkerPool(tasks, policy, pollMillis = 5)
        val visits = AtomicIntegerArray(tasks)
        val firstStarted = AtomicInteger()
        val secondStarted = AtomicInteger()
        val finished = AtomicInteger()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val firstReady = CompletableDeferred<Unit>()
        val secondReady = CompletableDeferred<Unit>()
        val finishFirst = CompletableDeferred<Unit>()
        val finishSecond = CompletableDeferred<Unit>()
        val run = launch {
            pool.run { index ->
                visits.incrementAndGet(index)
                val now = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, now) }
                try {
                    when {
                        index < 16 -> {
                            if (firstStarted.incrementAndGet() == 16) firstReady.complete(Unit)
                            finishFirst.await()
                        }
                        index < 24 -> {
                            if (secondStarted.incrementAndGet() == 8) secondReady.complete(Unit)
                            finishSecond.await()
                        }
                    }
                    finished.incrementAndGet()
                } finally {
                    active.decrementAndGet()
                }
            }
        }
        try {
            // 首轮任务全部挂起，累计任务数不可能被同一 worker 的后续任务凑满。
            withTimeout(2000) { firstReady.await() }
            assertEquals(16, active.get())
            assertEquals(16, pool.activeWorkers)
            assertEquals(0, pool.completedTasks)
            policy.recordFailure()
            assertEquals(8, policy.current())
            assertEquals("当前任务未完成时不能提前退役", 0, pool.retiredWorkers)
            assertEquals(16, pool.activeWorkers)

            finishFirst.complete(Unit)
            withTimeout(2000) {
                // 次轮八个任务保持挂起，降档状态不会在队列排空前一闪而过。
                secondReady.await()
                while (pool.activeWorkers != 8) yield()
            }
            assertEquals("只有超出目标的 8 个 worker 退休", 8, pool.retiredWorkers)
            assertEquals("降目标后仍保留 8 路实际任务", 8, active.get())
            assertEquals(16, finished.get())
            assertEquals(16, pool.completedTasks)
            assertEquals(8, secondStarted.get())

            finishSecond.complete(Unit)
            withTimeout(2000) { run.join() }
            assertEquals(8, policy.current())
            assertEquals(tasks, finished.get())
            assertEquals(tasks, pool.completedTasks)
            assertEquals(8, pool.retiredWorkers)
            assertEquals(16, peak.get())
            assertEquals(0, pool.activeWorkers)
            for (index in 0 until tasks) {
                assertEquals("任务 $index 必须恰好完成一次", 1, visits.get(index))
            }
        } finally {
            // 断言失败或超时时也释放夹具，避免挂起 worker 泄漏到下一项测试。
            finishFirst.complete(Unit)
            finishSecond.complete(Unit)
            run.cancelAndJoin()
        }
    }

    /** 取消已开始的任务后，finally 必须归还活跃计数，不能因状态锁而挂住。 */
    @Test
    fun cancellationReleasesActiveWorkerAccounting() = runBlocking {
        val pool = NpmAdaptiveWorkerPool(96, NpmConcurrencyPolicy(), pollMillis = 5)
        val started = AtomicInteger()
        val ready = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val run = launch {
            pool.run {
                if (started.incrementAndGet() == 8) ready.complete(Unit)
                release.await()
            }
        }
        try {
            withTimeout(2000) { ready.await() }
            assertEquals(8, pool.activeWorkers)
            assertEquals(0, pool.completedTasks)
            withTimeout(2000) { run.cancelAndJoin() }
            assertEquals(0, pool.activeWorkers)
            assertEquals(0, pool.completedTasks)
            assertEquals(0, pool.retiredWorkers)
        } finally {
            release.complete(Unit)
            run.cancelAndJoin()
        }
    }

    /** 目标从 8 升到 16 时，调度协程补足 worker，而不是只改策略数字。 */
    @Test
    fun upshiftLaunchesWorkersUpToTheNewTarget() = runBlocking {
        val policy = NpmConcurrencyPolicy(initial = 8, maximum = 16, step = 8, growAfter = 1)
        assertEquals(8, policy.current())

        val tasks = 96
        val pool = NpmAdaptiveWorkerPool(tasks, policy, pollMillis = 5)
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val reached = CompletableDeferred<Int>()

        val observer = launch(Dispatchers.Default) {
            while (isActive) {
                if (pool.activeWorkers >= 16) {
                    reached.complete(pool.activeWorkers)
                    return@launch
                }
                delay(1)
            }
        }

        pool.run { index ->
            val now = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, now) }
            if (index == 0) policy.recordSuccess()
            delay(12)
            active.decrementAndGet()
        }
        observer.cancelAndJoin()

        assertEquals(16, policy.current())
        assertEquals("扩容后应实际达到 16 路", 16, withTimeout(2000) { reached.await() })
        assertTrue(peak.get() >= 16)
        assertEquals(tasks, pool.completedTasks)
        assertEquals(0, pool.activeWorkers)
    }
}
