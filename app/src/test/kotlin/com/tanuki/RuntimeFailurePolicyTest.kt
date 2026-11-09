package com.tanuki

import com.tanuki.runtime.FailureEvidence
import com.tanuki.runtime.FailureKind
import com.tanuki.runtime.RecoveryMode
import com.tanuki.runtime.RuntimeFailurePolicy
import com.tanuki.runtime.RuntimeRecoveryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeFailurePolicyTest {
    private val policy = RuntimeFailurePolicy(
        earlyCrashWindowMillis = 120_000L,
        probationWindowMillis = 120_000L,
        safeModeThreshold = 2,
        maxRestartAttempts = 3,
        baseBackoffMillis = 2_000L,
        maxBackoffMillis = 30_000L,
        diskReserveBytes = 64L * 1024 * 1024
    )

    private fun evidence(
        launched: Boolean = true,
        failedAt: Long = 1_000L,
        readyAt: Long? = null,
        exitCode: Int? = 1,
        output: String = "",
        errorText: String = "",
        timedOut: Boolean = false,
        diskFreeBytes: Long? = 8L * 1024 * 1024 * 1024
    ) = FailureEvidence(launched, failedAt, readyAt, exitCode, output, errorText, timedOut, diskFreeBytes)

    @Test fun classifiesLaunchFailureWhenProcessWasNeverStarted() {
        assertEquals(FailureKind.LAUNCH_FAILED, policy.classify(evidence(launched = false, errorText = "mount failed")))
    }

    @Test fun classifiesStartupTimeoutAndFatalBeforeReady() {
        assertEquals(FailureKind.STARTUP_FAILED, policy.classify(evidence(timedOut = true)))
        assertEquals(FailureKind.STARTUP_FAILED, policy.classify(evidence(output = "Error: Cannot find module '@deepseek-ai/dsh-web-app'")))
        assertEquals(FailureKind.STARTUP_FAILED, policy.classify(evidence(output = "UnhandledPromiseRejection: startup failed")))
    }

    @Test fun startupTimeoutKeepsStartupFailedDespiteCleanupSignal() {
        listOf(143, 137).forEach { exit ->
            assertEquals(
                FailureKind.STARTUP_FAILED,
                policy.classify(evidence(timedOut = true, exitCode = exit, output = "Killed"))
            )
        }
        assertEquals(
            FailureKind.STARTUP_FAILED,
            policy.classify(evidence(timedOut = true, exitCode = 143, output = "SIGKILL"))
        )
    }

    @Test fun httpReadinessTimeoutAfterReadyLineRemainsStartupFailed() {
        assertEquals(
            FailureKind.STARTUP_FAILED,
            policy.classify(
                evidence(
                    timedOut = true,
                    readyAt = 1_000L,
                    failedAt = 181_000L,
                    exitCode = 143,
                    output = "dsh web: http://127.0.0.1:43210/?token=redacted\nKilled"
                )
            )
        )
    }

    @Test fun startupTimeoutStillYieldsToNoSpaceEvidence() {
        assertEquals(
            FailureKind.NO_SPACE,
            policy.classify(evidence(timedOut = true, exitCode = 143, output = "ENOSPC: no space left on device"))
        )
    }

    @Test fun classifiesEarlyAndLateCrashesByReadyLineWindow() {
        val fatal = "FATAL ERROR: plugin failed to start"
        assertEquals(FailureKind.CRASHED_EARLY, policy.classify(evidence(failedAt = 121_000L, readyAt = 1_000L, output = fatal)))
        assertEquals(FailureKind.CRASHED_LATE, policy.classify(evidence(failedAt = 121_001L, readyAt = 1_000L, output = fatal)))
    }

    @Test fun signalOrLowMemoryIsKilledEvenAfterReady() {
        assertEquals(FailureKind.KILLED, policy.classify(evidence(readyAt = 1_000L, failedAt = 2_000L, exitCode = 137)))
        assertEquals(FailureKind.KILLED, policy.classify(evidence(readyAt = 1_000L, failedAt = 2_000L, exitCode = 1, output = "OutOfMemoryError: Java heap space")))
    }

    @Test fun exitWithoutDshFatalIsKilled() {
        assertEquals(FailureKind.KILLED, policy.classify(evidence(readyAt = 1_000L, failedAt = 2_000L, exitCode = 0)))
        assertEquals(FailureKind.KILLED, policy.classify(evidence(readyAt = null, exitCode = 1, output = "bye")))
    }

    @Test fun noSpaceWinsOverFatalAndLowDisk() {
        assertEquals(FailureKind.NO_SPACE, policy.classify(evidence(output = "FATAL ERROR\nENOSPC: no space left on device")))
        assertEquals(FailureKind.NO_SPACE, policy.classify(evidence(diskFreeBytes = 1L * 1024 * 1024)))
        assertEquals(FailureKind.NO_SPACE, policy.classify(evidence(errorText = "设备剩余空间不足 64 MiB，请先清理存储")))
    }

    @Test fun twoStartupFailuresEnterSafeModeWithSafeRestart() {
        val first = policy.onFailure(RuntimeRecoveryState(), FailureKind.STARTUP_FAILED, 10_000L, 1)
        assertEquals(RecoveryMode.NORMAL, first.state.mode)
        assertTrue(first.autoRestart)
        assertEquals(1, first.state.startupFailureStreak)
        assertEquals(1, first.state.restartAttempts)

        val second = policy.onFailure(first.state, FailureKind.STARTUP_FAILED, 11_000L, 1)
        assertEquals(RecoveryMode.SAFE, second.state.mode)
        assertTrue(second.autoRestart)
        assertEquals(1, second.state.restartAttempts)
        assertTrue(second.summary.contains("安全模式"))
    }

    @Test fun startupFailureThenEarlyCrashAlsoEntersSafeMode() {
        val first = policy.onFailure(RuntimeRecoveryState(), FailureKind.STARTUP_FAILED, 10_000L, 1)
        val early = first.state.copy(lastReadyAtMillis = 20_000L)
        val second = policy.onFailure(early, FailureKind.CRASHED_EARLY, 30_000L, 1)
        assertEquals(RecoveryMode.SAFE, second.state.mode)
        assertEquals(2, second.state.startupFailureStreak)
    }

    @Test fun nonStartupFailuresBreakStartupFailureStreak() {
        val first = policy.onFailure(RuntimeRecoveryState(), FailureKind.STARTUP_FAILED, 1_000L, 1)
        val killed = policy.onFailure(first.state, FailureKind.KILLED, 2_000L, 137)
        assertEquals(0, killed.state.startupFailureStreak)
        val again = policy.onFailure(killed.state, FailureKind.STARTUP_FAILED, 3_000L, 1)
        assertEquals(RecoveryMode.NORMAL, again.state.mode)
        assertEquals(1, again.state.startupFailureStreak)

        val launch = policy.onFailure(first.state, FailureKind.LAUNCH_FAILED, 4_000L, 1)
        assertEquals(0, launch.state.startupFailureStreak)
        assertEquals(RecoveryMode.NORMAL, launch.state.mode)
    }

    @Test fun lateCrashResetsStreakAndRestartsNormalMode() {
        val failedOnce = RuntimeRecoveryState(startupFailureStreak = 1)
        val late = policy.onFailure(failedOnce, FailureKind.CRASHED_LATE, 10_000L, 1)
        assertEquals(RecoveryMode.NORMAL, late.state.mode)
        assertEquals(0, late.state.startupFailureStreak)
        assertEquals(1, late.state.restartAttempts)
        assertTrue(late.autoRestart)
    }

    @Test fun stableRunClearsStreakAndRestartBudget() {
        val failed = RuntimeRecoveryState(mode = RecoveryMode.NORMAL, startupFailureStreak = 2, restartAttempts = 3)
        val stable = policy.onStable(failed, 50_000L)
        assertEquals(0, stable.startupFailureStreak)
        assertEquals(0, stable.restartAttempts)
    }

    @Test fun safeModeFailureDoesNotAutoRestartOrLeaveSafeMode() {
        val safe = policy.toSafeByUser(RuntimeRecoveryState(), 10_000L)
        val decision = policy.onFailure(safe, FailureKind.STARTUP_FAILED, 20_000L, 1)
        assertEquals(RecoveryMode.SAFE, decision.state.mode)
        assertFalse(decision.autoRestart)
        assertEquals(0, decision.state.restartAttempts)
    }

    @Test fun probationStartupFailureReturnsToSafeMode() {
        val probation = policy.toProbation(policy.toSafeByUser(RuntimeRecoveryState(), 1_000L), 2_000L)
        val decision = policy.onFailure(probation, FailureKind.STARTUP_FAILED, 3_000L, 1)
        assertEquals(RecoveryMode.SAFE, decision.state.mode)
        assertTrue(decision.autoRestart)
        assertTrue(decision.summary.contains("试用"))
    }

    @Test fun probationEarlyCrashWithinTwoMinutesReturnsToSafeMode() {
        val probation = policy.toProbation(RuntimeRecoveryState(), 1_000L)
        val ready = policy.onReady(probation, 2_000L)
        assertEquals(2_000L, ready.probationStartedAtMillis)
        val decision = policy.onFailure(ready, FailureKind.CRASHED_EARLY, 121_000L, 1)
        assertEquals(RecoveryMode.SAFE, decision.state.mode)
        assertTrue(decision.autoRestart)
    }

    @Test fun probationLateCrashBecomesNormalAndRestartsNormalMode() {
        val probation = policy.toProbation(RuntimeRecoveryState(), 1_000L)
        val ready = policy.onReady(probation, 2_000L)
        val decision = policy.onFailure(ready, FailureKind.CRASHED_LATE, 200_000L, 1)
        assertEquals(RecoveryMode.NORMAL, decision.state.mode)
        assertTrue(decision.autoRestart)
    }

    @Test fun probationKilledOrNoSpaceReturnsSafeWithoutAutoRestart() {
        listOf(FailureKind.KILLED, FailureKind.NO_SPACE).forEach { kind ->
            val probation = policy.toProbation(RuntimeRecoveryState(), 1_000L)
            val ready = policy.onReady(probation, 2_000L)
            val decision = policy.onFailure(ready, kind, 30_000L, 1)
            assertEquals(RecoveryMode.SAFE, decision.state.mode)
            assertFalse("$kind 不应自动重启", decision.autoRestart)
        }
    }

    @Test fun normalKilledOrNoSpaceNeverAutoRestarts() {
        listOf(FailureKind.KILLED, FailureKind.NO_SPACE).forEach { kind ->
            val decision = policy.onFailure(RuntimeRecoveryState(), kind, 10_000L, 137)
            assertEquals(RecoveryMode.NORMAL, decision.state.mode)
            assertFalse("$kind 不应自动重启", decision.autoRestart)
            assertEquals(0, decision.state.restartAttempts)
        }
    }

    @Test fun restartBackoffGrowsAndStopsAtLimit() {
        assertEquals(2_000L, policy.backoffMillis(1))
        assertEquals(4_000L, policy.backoffMillis(2))
        assertEquals(8_000L, policy.backoffMillis(3))
        assertEquals(30_000L, policy.backoffMillis(10))

        // LAUNCH_FAILED 不累计 startupFailureStreak，适合单独验证自动重启上限。
        var state = RuntimeRecoveryState()
        repeat(3) { index ->
            val decision = policy.onFailure(state, FailureKind.LAUNCH_FAILED, 10_000L + index, 1)
            assertTrue("第 ${index + 1} 次应自动重启", decision.autoRestart)
            state = decision.state
        }
        assertEquals(3, state.restartAttempts)
        val exhausted = policy.onFailure(state, FailureKind.LAUNCH_FAILED, 20_000L, 1)
        assertFalse(exhausted.autoRestart)
        assertEquals(3, exhausted.state.restartAttempts)
        assertTrue(exhausted.summary.contains("上限"))
    }

    @Test fun manualStartResetsRestartBudgetAndProbationTimer() {
        val probation = RuntimeRecoveryState(
            mode = RecoveryMode.PROBATION,
            probationStartedAtMillis = 1_000L,
            restartAttempts = 3,
            lastReadyAtMillis = 1_500L
        )
        val prepared = policy.prepareManualStart(probation, 5_000L)
        assertEquals(0, prepared.restartAttempts)
        assertEquals(5_000L, prepared.probationStartedAtMillis)
        assertEquals(0L, prepared.lastReadyAtMillis)

        val normal = policy.prepareManualStart(RuntimeRecoveryState(restartAttempts = 3), 6_000L)
        assertEquals(0, normal.restartAttempts)
        assertEquals(RecoveryMode.NORMAL, normal.mode)
    }

    @Test fun automaticStartKeepsUnprovenProbation() {
        val probation = RuntimeRecoveryState(
            mode = RecoveryMode.PROBATION,
            probationStartedAtMillis = 1_000L,
            lastReadyAtMillis = 2_000L
        )
        val prepared = policy.prepareAutomaticStart(probation, 200_000L)
        // 只有存活稳定计时器能提交 NORMAL；墙钟过期不能证明进程实际稳定运行。
        assertEquals(RecoveryMode.PROBATION, prepared.mode)
        assertEquals(0L, prepared.lastReadyAtMillis)
    }

    @Test fun manualStartAfterExpiredProbationStartsFreshTrial() {
        val probation = RuntimeRecoveryState(
            mode = RecoveryMode.PROBATION,
            probationStartedAtMillis = 1_000L,
            lastReadyAtMillis = 1_500L
        )
        val prepared = policy.prepareManualStart(probation, 200_000L)
        assertEquals(RecoveryMode.PROBATION, prepared.mode)
        assertEquals(200_000L, prepared.probationStartedAtMillis)
        assertEquals(0L, prepared.lastReadyAtMillis)
    }

    @Test fun processRestartDuringProbationReturnsSafeMode() {
        val probation = policy.toProbation(RuntimeRecoveryState(), 1_000L)
        val ready = policy.onReady(probation, 2_000L)
        val interrupted = policy.afterProcessRestart(ready, serviceWasActive = true, nowMillis = 50_000L)
        assertEquals(RecoveryMode.SAFE, interrupted.mode)

        val reopenedLater = policy.afterProcessRestart(ready, serviceWasActive = true, nowMillis = 700_000L)
        assertEquals(RecoveryMode.SAFE, reopenedLater.mode)
    }

    @Test fun processRestartWhenServiceWasNotActiveKeepsProbation() {
        val probation = policy.toProbation(RuntimeRecoveryState(), 1_000L)
        val ready = policy.onReady(probation, 2_000L)
        val restarted = policy.afterProcessRestart(ready, serviceWasActive = false, nowMillis = 700_000L)
        assertEquals(RecoveryMode.PROBATION, restarted.mode)
        assertEquals(0L, restarted.lastReadyAtMillis)
    }

    @Test fun processRestartDuringProbationStartupReturnsSafeModeEvenAfterWindow() {
        val probation = policy.toProbation(RuntimeRecoveryState(), 1_000L)
        val interrupted = policy.afterProcessRestart(probation, serviceWasActive = true, nowMillis = 200_000L)
        assertEquals(RecoveryMode.SAFE, interrupted.mode)
    }

    @Test fun restoreVerificationRequiresRestoredUserDataMode() {
        assertFalse(policy.canVerifyRestoredData(RecoveryMode.SAFE))
        assertTrue(policy.canVerifyRestoredData(RecoveryMode.NORMAL))
        assertTrue(policy.canVerifyRestoredData(RecoveryMode.PROBATION))
    }

    @Test fun persistenceRoundTripsAndRejectsInvalidData() {
        val state = RuntimeRecoveryState(
            mode = RecoveryMode.PROBATION,
            lastFailure = FailureKind.CRASHED_EARLY,
            lastFailureAtMillis = 123L,
            lastReadyAtMillis = 456L,
            startupFailureStreak = 2,
            restartAttempts = 3,
            safeModeEnteredAtMillis = 789L,
            probationStartedAtMillis = 456L,
            lastExitCode = 1
        )
        assertEquals(state, policy.decode(policy.encode(state)))

        assertEquals(RuntimeRecoveryState(), policy.decode(null))
        assertEquals(RuntimeRecoveryState(), policy.decode("not json"))
        assertEquals(RuntimeRecoveryState(), policy.decode("""{"schema":99,"mode":"SAFE"}"""))
        val clamped = policy.decode("""{"schema":1,"mode":"SAFE","startupFailureStreak":-3,"restartAttempts":-1,"lastFailure":"UNKNOWN"}""")
        assertEquals(RecoveryMode.SAFE, clamped.mode)
        assertEquals(0, clamped.startupFailureStreak)
        assertEquals(0, clamped.restartAttempts)
        assertNull(clamped.lastFailure)
    }

    @Test fun startArgumentsKeepOfficialWebAndUseRescueOnlyInSafeMode() {
        val normal = policy.startArguments("0.2.1-rc.5", safe = false, rescueProfileExists = false)
        assertTrue(normal.contains("web"))
        assertFalse(normal.contains("rescue"))
        assertTrue(normal.contains("--host"))
        assertTrue(normal.contains("127.0.0.1"))

        val firstSafe = policy.startArguments("0.2.1-rc.5", safe = true, rescueProfileExists = false)
        assertTrue(firstSafe.contains("rescue"))
        assertTrue(firstSafe.contains("--from-default-profile"))
        assertTrue(firstSafe.contains("web"))

        val existingSafe = policy.startArguments("0.2.1-rc.5", safe = true, rescueProfileExists = true)
        assertTrue(existingSafe.contains("rescue"))
        assertFalse(existingSafe.contains("--from-default-profile"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun startArgumentsRejectVersionInjection() {
        policy.startArguments("../escape", safe = false, rescueProfileExists = false)
    }
}
