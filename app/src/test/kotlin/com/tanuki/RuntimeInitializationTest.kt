package com.tanuki

import com.tanuki.runtime.RuntimeInitialization
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RuntimeInitializationTest {
    @Test fun backgroundRecoveryDoesNotBlockCallerAndKeepsWritesBehindBarrier() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val mutex = Mutex()
        val entered = CompletableDeferred<Thread>()
        val release = CompletableDeferred<Unit>()
        val caller = Thread.currentThread()
        val barrier = RuntimeInitialization(scope, mutex, true, {
            entered.complete(Thread.currentThread())
            runBlocking { release.await() }
        }, { throw AssertionError(it) })
        try {
            assertNotSame(caller, withTimeout(2000) { entered.await() })
            assertTrue(mutex.isLocked)
            assertFalse(barrier.isComplete)
            val waiter = async { barrier.await(); assertFalse(mutex.isLocked); "ready" }
            assertFalse(waiter.isCompleted)
            release.complete(Unit)
            assertEquals("ready", withTimeout(2000) { waiter.await() })
            assertTrue(barrier.isComplete)
            assertFalse(mutex.isLocked)
        } finally { release.complete(Unit); scope.cancel() }
    }

    @Test fun recoveryFailureIsSharedAndUnlocksWithoutPretendingReady() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val mutex = Mutex()
        val failure = IOException("synthetic journal failure")
        val reported = CompletableDeferred<Throwable>()
        val barrier = RuntimeInitialization(scope, mutex, true, { throw failure }, { reported.complete(it) })
        try {
            repeat(2) {
                try { withTimeout(2000) { barrier.await() }; fail("recovery failure ignored") }
                catch (error: IOException) {
                    assertEquals(failure.message, error.message)
                    assertTrue(generateSequence<Throwable>(error) { it.cause }.any { it === failure })
                }
            }
            assertSame(failure, reported.await())
            assertFalse(mutex.isLocked)
        } finally { scope.cancel() }
    }

    @Test fun cancelledScopeBeforeDispatchCannotLeaveEnvironmentLocked() = runBlocking {
        val parent = SupervisorJob().also { it.cancel() }
        val scope = CoroutineScope(parent + Dispatchers.IO)
        val mutex = Mutex()
        val barrier = RuntimeInitialization(scope, mutex, true, { fail("cancelled scope ran recovery") }, { })
        try { withTimeout(2000) { barrier.await() }; fail("cancelled recovery accepted") }
        catch (_: CancellationException) { }
        assertFalse(mutex.isLocked)
    }

    @Test fun synchronousFixtureModePreservesImmediateRecoverySemantics() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val mutex = Mutex()
        var recovered = false
        val barrier = RuntimeInitialization(scope, mutex, false, { recovered = true }, { fail("fixture unexpectedly failed") })
        try {
            assertTrue(recovered)
            assertTrue(barrier.isComplete)
            barrier.await()
            assertFalse(mutex.isLocked)
        } finally { scope.cancel() }
    }
}
