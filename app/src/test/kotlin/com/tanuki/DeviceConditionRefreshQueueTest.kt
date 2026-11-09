package com.tanuki

import com.tanuki.power.DeviceConditionRefreshQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DeviceConditionRefreshQueueTest {
    /**
     * 主线程/广播回调只调用 request()，不得在调用线程执行 worker（真实实现中的采样与写盘）。
     */
    @Test
    fun requestDoesNotRunPersistenceWorkerOnCallerThread() {
        val calls = AtomicInteger()
        val ran = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val queue = DeviceConditionRefreshQueue(scope) {
                calls.incrementAndGet()
                ran.countDown()
            }

            queue.request()
            assertEquals("request() 必须只投递，不能在调用线程执行写盘 worker", 0, calls.get())

            queue.start()
            assertTrue("后台 collector 应执行持久化 worker", ran.await(5, TimeUnit.SECONDS))
            assertEquals(1, calls.get())
        } finally {
            scope.cancel()
        }
    }

    /** worker 忙时的连续请求合并，恢复/热状态抖动不会堆积逐次写盘。 */
    @Test
    fun burstRequestsAreCoalescedBehindRunningWorker() {
        val calls = AtomicInteger()
        val firstStarted = CountDownLatch(1)
        val release = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val queue = DeviceConditionRefreshQueue(scope) {
                calls.incrementAndGet()
                firstStarted.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
            queue.start()
            queue.request()
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS))
            repeat(100) { queue.request() }
            release.countDown()

            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (calls.get() < 2 && System.nanoTime() < deadline) Thread.sleep(10)
            assertTrue("合并后的请求应至少执行一次", calls.get() >= 2)
            assertTrue("突发请求不能逐个执行", calls.get() <= 3)
        } finally {
            scope.cancel()
        }
    }
}
