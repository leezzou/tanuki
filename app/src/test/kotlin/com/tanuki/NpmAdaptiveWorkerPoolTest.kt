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
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class NpmAdaptiveWorkerPoolTest {
    /**
     * 初始 16 路，启动后目标降到 8：超出的 8 个 worker 只能在当前归档结束后退出，
     * 不能同时全退；剩余 8 个继续消费队列，最终全部归档完成。
     */
    @Test
    fun downshiftRetiresExcessWorkersAfterCurrentTaskAndKeepsQueueDraining() = runBlocking {
        val policy = NpmConcurrencyPolicy(initial = 8, maximum = 16, step = 8, growAfter = 1)
        policy.recordSuccess()
        assertEquals(16, policy.current())

        val tasks = 96
        val pool = NpmAdaptiveWorkerPool(tasks, policy, pollMillis = 5)
        val started = AtomicInteger()
        val finished = AtomicInteger()
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val dropped = AtomicBoolean()
        val settled = CompletableDeferred<Int>()

        val observer = launch(Dispatchers.Default) {
            while (isActive) {
                if (dropped.get() && pool.retiredWorkers == 8 && pool.activeWorkers == 8) {
                    settled.complete(pool.activeWorkers)
                    return@launch
                }
                delay(1)
            }
        }

        pool.run { index ->
            started.incrementAndGet()
            val now = active.incrementAndGet()
            peak.updateAndGet { maxOf(it, now) }
            if (index == 0) {
                // 等所有 16 个 worker 都进入任务后再降低目标。
                while (started.get() < 16) delay(1)
                policy.recordFailure()
                dropped.set(true)
            }
            delay(12)
            active.decrementAndGet()
            finished.incrementAndGet()
        }
        observer.cancelAndJoin()

        assertEquals(8, policy.current())
        assertEquals(tasks, finished.get())
        assertEquals(tasks, pool.completedTasks)
        assertEquals("只有超出目标的 8 个 worker 退休", 8, pool.retiredWorkers)
        assertEquals(16, peak.get())
        assertEquals("降目标后活跃 worker 应回落到 8", 8, withTimeout(2000) { settled.await() })
        assertEquals(0, pool.activeWorkers)
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
