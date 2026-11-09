package com.tanuki

import com.tanuki.power.DeviceConditionInitialization
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class DeviceConditionInitializationTest {
    /**
     * 模拟冷启动：restore 被暂停时提前启动 Worker，Worker 必须等初始化完成后才写状态，
     * 不能先用默认状态覆盖磁盘上尚未读取的待办。
     */
    @Test
    fun workerWaitsUntilRestoreCompletesBeforeWriting() = runBlocking {
        val restoreStarted = CompletableDeferred<Unit>()
        val releaseRestore = CompletableDeferred<Unit>()
        val order = Collections.synchronizedList(mutableListOf<String>())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val initialization = DeviceConditionInitialization {
                order += "restore-start"
                restoreStarted.complete(Unit)
                releaseRestore.await()
                order += "restore-end"
            }
            initialization.start(scope)
            restoreStarted.await()

            val workerStarted = CompletableDeferred<Unit>()
            val worker = scope.launch {
                workerStarted.complete(Unit)
                initialization.awaitReady()
                order += "worker-write"
            }
            workerStarted.await()
            delay(150)

            // restore 仍未完成时，提前启动的 Worker 不能写盘。
            assertEquals(listOf("restore-start"), order.toList())

            releaseRestore.complete(Unit)
            worker.join()
            assertEquals(listOf("restore-start", "restore-end", "worker-write"), order.toList())
        } finally {
            scope.cancel()
        }
    }

    /** 重复 start 只初始化一次；初始化失败也必须释放等待者，避免 Worker 永久挂起。 */
    @Test
    fun startRunsOnceAndFailureStillReleasesWaiters() = runBlocking {
        val attempts = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val initialization = DeviceConditionInitialization {
                attempts.incrementAndGet()
                error("synthetic restore failure")
            }
            initialization.start(scope)
            initialization.start(scope)
            initialization.awaitReady()
            initialization.awaitReady()
            assertEquals(1, attempts.get())
        } finally {
            scope.cancel()
        }
    }
}
