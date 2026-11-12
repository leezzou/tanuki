package com.tanuki

import com.tanuki.runtime.withStartAdmission
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class RuntimeStartAdmissionTest {
    @Test fun stopAfterWaitConditionBeforeAdmissionRejectsAllOldRequests() = runBlocking {
        for (origin in listOf("manual-after-recovery", "early-service-request", "automatic-retry", "update-install", "update-resume")) {
            val owner = Any()
            var generation = 0L
            var stopping = false
            var starts = 0
            val expected = synchronized(owner) { generation }
            val checked = CompletableDeferred<Unit>()
            val resume = CompletableDeferred<Unit>()
            val request = async(Dispatchers.Default) {
                assertTrue(synchronized(owner) { generation == expected && !stopping })
                checked.complete(Unit)
                resume.await()
                withStartAdmission(owner, expected, { generation }, { stopping }) { starts++ }
            }
            withTimeout(2000) { checked.await() }
            synchronized(owner) {
                stopping = true
                generation++
                stopping = false
            }
            resume.complete(Unit)
            withTimeout(2000) { request.await() }
            assertEquals(origin, 0, starts)
        }
    }

    @Test fun admissionAndStopShareOneLockRatherThanRecheckingThenCallingOutside() {
        val owner = Any()
        var generation = 0L
        var running = false
        val entered = CountDownLatch(1)
        val stopAttempted = CountDownLatch(1)
        val allowAdmissionToFinish = CountDownLatch(1)
        val starter = Thread {
            withStartAdmission(owner, 0, { generation }, { false }) {
                entered.countDown()
                check(allowAdmissionToFinish.await(2, TimeUnit.SECONDS))
                running = true
            }
        }
        val stopper = Thread {
            stopAttempted.countDown()
            synchronized(owner) { generation++; running = false }
        }
        val threadFailure = AtomicReference<Throwable?>()
        val handler = Thread.UncaughtExceptionHandler { _, error -> threadFailure.compareAndSet(null, error) }
        starter.uncaughtExceptionHandler = handler
        stopper.uncaughtExceptionHandler = handler
        starter.start()
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            stopper.start()
            assertTrue(stopAttempted.await(2, TimeUnit.SECONDS))
        } finally { allowAdmissionToFinish.countDown() }
        starter.join(2000)
        stopper.join(2000)
        assertFalse(starter.isAlive)
        assertFalse(stopper.isAlive)
        assertNull(threadFailure.get())
        assertEquals(1L, generation)
        assertFalse(running)
    }

    @Test fun freshUserRequestAfterStopCanStartButStoppingRequestCannot() {
        val owner = Any()
        var starts = 0
        withStartAdmission(owner, 4, { 4 }, { true }) { starts++ }
        assertEquals(0, starts)
        withStartAdmission(owner, 4, { 4 }, { false }) { starts++ }
        assertEquals(1, starts)
    }
}
