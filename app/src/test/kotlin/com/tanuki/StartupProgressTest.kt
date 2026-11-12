package com.tanuki

import com.tanuki.runtime.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class StartupProgressTest {
    @Test fun timingsMeasureRealTransitionsAndFreezeOnFailure() {
        var now = 1000L
        val updates = mutableListOf<StartupProgress>()
        val trace = StartupTrace(7, { now }, updates::add)
        now = 1500
        trace.phase(StartupStage.PROCESS)
        now = 1700
        trace.phase(StartupStage.OUTPUT)
        now = 3100
        trace.finish(StartupOutcome.FAILED)
        val frozen = trace.snapshot()
        assertEquals(7L, frozen.attemptId)
        assertEquals(2100L, frozen.elapsedMillis(90_000))
        assertEquals(1400L, frozen.stageElapsedMillis(90_000))
        assertEquals(listOf(StartupStageTiming(StartupStage.QUEUED, 500), StartupStageTiming(StartupStage.PROCESS, 200)), frozen.completedStages)
        trace.phase(StartupStage.HTTP)
        trace.finish(StartupOutcome.READY)
        assertEquals(frozen, trace.snapshot())
    }

    @Test fun lateOrDuplicatePhaseDoesNotResetTimingsAndSuccessEndsTrace() {
        var now = 100L
        val trace = StartupTrace(2, { now }) { }
        now = 200
        trace.phase(StartupStage.ENDPOINT)
        now = 500
        trace.phase(StartupStage.OUTPUT)
        trace.phase(StartupStage.ENDPOINT)
        assertEquals(200L, trace.snapshot().stageStartedAtMillis)
        now = 900
        trace.finish(StartupOutcome.READY)
        assertEquals(StartupStage.READY, trace.snapshot().stage)
        assertEquals(StartupOutcome.READY, trace.snapshot().outcome)
        assertEquals(800L, trace.snapshot().elapsedMillis(10_000))
    }

    @Test fun monotonicClockCannotProduceNegativeDurations() {
        var now = 100L
        val trace = StartupTrace(3, { now }) { }
        now = 50
        trace.phase(StartupStage.PROCESS)
        trace.finish(StartupOutcome.CANCELLED)
        assertEquals(0L, trace.snapshot().elapsedMillis(0))
        assertTrue(trace.snapshot().completedStages.all { it.durationMillis >= 0 })
    }

    @Test fun collectorFailureIsImmediateEvenWhenProcessStillLooksAlive() = runBlocking {
        val completion = CompletableDeferred<CommandResult>()
        val failure = IOException("synthetic output pipe failure")
        completion.completeExceptionally(failure)
        try {
            withTimeout(1000) {
                awaitStartupReady(completion, { true }, { false }, { error("HTTP must not start") }, timeoutMillis = 60_000)
            }
            fail("collector failure ignored")
        } catch (error: IOException) {
            assertEquals(failure.message, error.message)
            assertTrue(generateSequence<Throwable>(error) { it.cause }.any { it === failure })
        }
    }

    @Test fun endedCollectorDoesNotWaitForReadinessDeadline() = runBlocking {
        val completion = CompletableDeferred(CommandResult(42, "", ""))
        try {
            withTimeout(1000) { awaitStartupReady(completion, { true }, { false }, { false }, timeoutMillis = 60_000) }
            fail("ended process output ignored")
        } catch (error: IllegalStateException) { assertTrue(error.message.orEmpty().contains("exit=42")) }
    }

    @Test fun readyLineAndHttpAreBothRequired() = runBlocking {
        val completion = CompletableDeferred<CommandResult>()
        var endpoint = false
        var requests = 0
        var httpStage = 0
        val publish = launch { delay(20); endpoint = true }
        withTimeout(1000) {
            awaitStartupReady(completion, { true }, { endpoint }, { ++requests >= 2 },
                onHttpWait = { httpStage++ }, pollMillis = 5)
        }
        publish.join()
        assertEquals(2, requests)
        assertEquals(1, httpStage)
        assertFalse(completion.isCompleted)
    }

    @Test fun readinessTimeoutAndExplicitCancellationKeepDistinctSemantics() = runBlocking {
        val completion = CompletableDeferred<CommandResult>()
        try {
            awaitStartupReady(completion, { true }, { false }, { false }, timeoutMillis = 20, pollMillis = 1)
            fail("deadline ignored")
        } catch (_: TimeoutCancellationException) { }
        val cancelled = CancellationException("synthetic stop")
        try {
            awaitStartupReady(completion, { true }, { false }, { false }, checkEndpoint = { throw cancelled })
            fail("stop ignored")
        } catch (error: CancellationException) {
            assertEquals(cancelled.message, error.message)
            assertTrue(generateSequence<Throwable>(error) { it.cause }.any { it === cancelled })
        }
        assertFalse(completion.isCancelled)
    }

    @Test fun processExitAndEndpointIsolationFailurePreventHttpProbe() = runBlocking {
        val completion = CompletableDeferred<CommandResult>()
        var requests = 0
        try {
            awaitStartupReady(completion, { false }, { false }, { requests++; true })
            fail("dead process accepted")
        } catch (_: IllegalStateException) { }
        try {
            awaitStartupReady(completion, { true }, { true }, { requests++; true }, checkEndpoint = { error("synthetic isolation failure") })
            fail("isolation failure accepted")
        } catch (error: IllegalStateException) { assertTrue(error.message.orEmpty().contains("isolation")) }
        assertEquals(0, requests)
    }
}
