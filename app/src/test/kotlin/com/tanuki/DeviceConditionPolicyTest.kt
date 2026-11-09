package com.tanuki

import com.tanuki.power.DeviceCondition
import com.tanuki.power.DeviceConditionConfig
import com.tanuki.power.DeviceConditionPolicy
import com.tanuki.power.DeviceConditionReason
import com.tanuki.power.DeviceSignals
import com.tanuki.power.DeviceWork
import com.tanuki.power.ThermalLevel
import com.tanuki.power.WorkDisposition
import com.tanuki.ui.PdfRenderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceConditionPolicyTest {
    private val config = DeviceConditionConfig()

    private fun signals(
        thermal: ThermalLevel = ThermalLevel.NONE,
        battery: Int = 100,
        charging: Boolean = false,
        lowMemory: Boolean = false,
        now: Long = 1_000L,
    ) = DeviceSignals(thermal, battery, charging, lowMemory, now)

    private fun evaluate(previous: com.tanuki.power.DeviceConditionState?, signals: DeviceSignals) =
        DeviceConditionPolicy.evaluate(previous, signals, config)

    @Test
    fun coolChargedDeviceStaysNormalWithFullBudget() {
        val result = evaluate(null, signals(now = 10_000L))

        assertEquals(DeviceCondition.NORMAL, result.state.condition)
        assertTrue(result.state.reasons.isEmpty())
        assertEquals(16, result.state.workerCap)
        assertEquals(80, result.state.logLineLimit)
        assertEquals(2048, result.state.previewMaxDimension)
        assertEquals(4_000_000, result.state.previewMaxPixels)
        assertTrue(result.state.backgroundChecksAllowed)
        assertTrue(result.state.deferredChecks.isEmpty())
        assertTrue(result.resumedChecks.isEmpty())
        assertEquals(10_000L, result.state.sinceMillis)
    }

    @Test
    fun thermalLevelsRiseImmediately() {
        var state = evaluate(null, signals(thermal = ThermalLevel.LIGHT, now = 1_000L)).state
        assertEquals(DeviceCondition.WARM, state.condition)
        assertEquals(8, state.workerCap)
        assertTrue(state.backgroundChecksAllowed)

        state = evaluate(state, signals(thermal = ThermalLevel.MODERATE, now = 2_000L)).state
        assertEquals(DeviceCondition.HOT, state.condition)
        assertEquals(4, state.workerCap)
        assertFalse(state.backgroundChecksAllowed)
        // 状态本身不登记待办；只有被跳过的具体 Worker 才会登记。
        assertTrue(state.deferredChecks.isEmpty())

        state = evaluate(state, signals(thermal = ThermalLevel.SEVERE, now = 3_000L)).state
        assertEquals(DeviceCondition.CRITICAL, state.condition)
        assertEquals(2, state.workerCap)
        assertFalse(state.backgroundChecksAllowed)

        state = evaluate(state, signals(thermal = ThermalLevel.EMERGENCY, now = 4_000L)).state
        assertEquals(DeviceCondition.CRITICAL, state.condition)
        assertEquals(2, state.workerCap)
    }

    /**
     * 从严重过热降级时必须先回到中热以下并持续 30 秒，不能因为一次采样降温直接回到正常。
     */
    @Test
    fun thermalDowngradeUsesLowerExitThresholdAndDwell() {
        var state = evaluate(null, signals(thermal = ThermalLevel.SEVERE, now = 1_000L)).state
        assertEquals(DeviceCondition.CRITICAL, state.condition)

        // 信号已回到 MODERATE，但仍在进入阈值边界上：先进入观察期。
        state = evaluate(state, signals(thermal = ThermalLevel.MODERATE, now = 2_000L)).state
        assertEquals(DeviceCondition.CRITICAL, state.condition)
        assertTrue(state.thermalHoldSinceMillis > 0L)

        state = evaluate(state, signals(thermal = ThermalLevel.MODERATE, now = 31_999L)).state
        assertEquals(DeviceCondition.CRITICAL, state.condition)

        state = evaluate(state, signals(thermal = ThermalLevel.MODERATE, now = 32_000L)).state
        assertEquals(DeviceCondition.HOT, state.condition)

        // HOT 的退出边界是 LIGHT，同样需要迟滞。
        state = evaluate(state, signals(thermal = ThermalLevel.LIGHT, now = 33_000L)).state
        assertEquals(DeviceCondition.HOT, state.condition)
        state = evaluate(state, signals(thermal = ThermalLevel.LIGHT, now = 63_000L)).state
        assertEquals(DeviceCondition.WARM, state.condition)

        state = evaluate(state, signals(thermal = ThermalLevel.NONE, now = 64_000L)).state
        assertEquals(DeviceCondition.WARM, state.condition)
        state = evaluate(state, signals(thermal = ThermalLevel.NONE, now = 94_000L)).state
        assertEquals(DeviceCondition.NORMAL, state.condition)
        assertTrue(state.backgroundChecksAllowed)
    }

    @Test
    fun batteryUsesEntryExitHysteresisAndChargingOverride() {
        var state = evaluate(null, signals(battery = 20, now = 1_000L)).state
        assertEquals(DeviceCondition.LOW_BATTERY, state.condition)
        assertEquals(DeviceConditionReason.LOW_BATTERY, state.reasons.single())
        assertFalse(state.backgroundChecksAllowed)

        // 24% 仍在退出阈值 25% 以下，保持低电，避免阈值附近抖动。
        state = evaluate(state, signals(battery = 24, now = 2_000L)).state
        assertEquals(DeviceCondition.LOW_BATTERY, state.condition)

        state = evaluate(state, signals(battery = 25, now = 3_000L)).state
        assertEquals(DeviceCondition.NORMAL, state.condition)
        assertTrue(state.reasons.isEmpty())

        // 充电时即使电量很低也视为可继续，不进入低电分支。
        state = evaluate(state, signals(battery = 5, charging = true, now = 4_000L)).state
        assertEquals(DeviceCondition.NORMAL, state.condition)

        // 拔掉充电器后重新按进入阈值判断。
        state = evaluate(state, signals(battery = 5, charging = false, now = 5_000L)).state
        assertEquals(DeviceCondition.LOW_BATTERY, state.condition)
    }

    @Test
    fun unknownBatteryDoesNotFalselyTriggerLowBattery() {
        val state = evaluate(null, signals(battery = -1, now = 1_000L)).state
        assertEquals(DeviceCondition.NORMAL, state.condition)
        assertTrue(state.reasons.isEmpty())
        assertTrue(state.backgroundChecksAllowed)
    }

    @Test
    fun lowMemoryRaisesWarmAndClearsOnlyAfterDwell() {
        var state = evaluate(null, signals(lowMemory = true, now = 1_000L)).state
        assertEquals(DeviceCondition.WARM, state.condition)
        assertTrue(state.reasons.contains(DeviceConditionReason.LOW_MEMORY))
        assertEquals(8, state.workerCap)
        assertTrue(state.backgroundChecksAllowed)

        state = evaluate(state, signals(lowMemory = false, now = 2_000L)).state
        assertEquals(DeviceCondition.WARM, state.condition)
        assertTrue(state.memoryWarm)

        state = evaluate(state, signals(lowMemory = false, now = 61_999L)).state
        assertEquals(DeviceCondition.WARM, state.condition)

        state = evaluate(state, signals(lowMemory = false, now = 62_000L)).state
        assertEquals(DeviceCondition.NORMAL, state.condition)
        assertFalse(state.memoryWarm)
    }

    @Test
    fun lowBatteryAndHeatKeepHotConditionWithBothReasonsAndStrictestBudget() {
        val result = evaluate(null, signals(thermal = ThermalLevel.LIGHT, battery = 15, now = 1_000L))
        val state = result.state

        assertEquals(DeviceCondition.WARM, state.condition)
        assertEquals(setOf(DeviceConditionReason.THERMAL, DeviceConditionReason.LOW_BATTERY), state.reasons)
        // 取热降级 8 路与低电 4 路中更严格的额度。
        assertEquals(4, state.workerCap)
        assertEquals(40, state.logLineLimit)
        assertEquals(1280, state.previewMaxDimension)
        assertEquals(1_600_000, state.previewMaxPixels)
        assertFalse(state.backgroundChecksAllowed)
        assertTrue(state.deferredChecks.isEmpty())
    }

    /**
     * 过热时延后后台检查；温度降到轻热后自动恢复并报告需要补跑的工作。
     */
    @Test
    fun deferredBackgroundCheckResumesAfterRecovery() {
        var state = evaluate(null, signals(thermal = ThermalLevel.MODERATE, now = 1_000L)).state
        assertFalse(state.backgroundChecksAllowed)
        assertTrue(state.deferredChecks.isEmpty())

        // 只有 APK Worker 实际被跳过时才登记 APK 待办，不会捎带 DSH。
        state = DeviceConditionPolicy.markCheckSkipped(state, DeviceWork.APK_UPDATE_CHECK)
        assertTrue(state.deferredChecks.contains(DeviceWork.APK_UPDATE_CHECK))

        // 降到 LIGHT，但 HOT -> WARM 仍需迟滞。
        var result = evaluate(state, signals(thermal = ThermalLevel.LIGHT, now = 2_000L))
        assertEquals(DeviceCondition.HOT, result.state.condition)
        assertTrue(result.resumedChecks.isEmpty())

        result = evaluate(result.state, signals(thermal = ThermalLevel.LIGHT, now = 32_000L))
        assertEquals(DeviceCondition.WARM, result.state.condition)
        assertTrue(result.state.backgroundChecksAllowed)
        assertEquals(setOf(DeviceWork.APK_UPDATE_CHECK), result.resumedChecks)
        // 入队确认前待办必须保留，避免进程在此窗口结束时丢失补跑。
        assertEquals(setOf(DeviceWork.APK_UPDATE_CHECK), result.state.deferredChecks)
        assertEquals(
            WorkDisposition.DEFER,
            DeviceConditionPolicy.decide(result.state, DeviceWork.APK_UPDATE_CHECK).disposition,
        )

        val acknowledged = DeviceConditionPolicy.acknowledgeChecks(result.state, result.resumedChecks)
        assertTrue(acknowledged.deferredChecks.isEmpty())
        assertEquals(
            WorkDisposition.RUN,
            DeviceConditionPolicy.decide(acknowledged, DeviceWork.APK_UPDATE_CHECK).disposition,
        )
    }

    /** 只进入受限状态、没有 Worker 被跳过时，恢复不得补跑任何检查。 */
    @Test
    fun conditionWithoutSkippedWorkerDoesNotResumeAnyCheck() {
        var state = evaluate(null, signals(thermal = ThermalLevel.MODERATE, now = 1_000L)).state
        assertTrue(state.deferredChecks.isEmpty())

        // HOT -> NORMAL 需要 30 秒迟滞；期间也没有 Worker 登记待办。
        var result = evaluate(state, signals(thermal = ThermalLevel.NONE, now = 2_000L))
        assertEquals(DeviceCondition.HOT, result.state.condition)
        assertTrue(result.resumedChecks.isEmpty())

        result = evaluate(result.state, signals(thermal = ThermalLevel.NONE, now = 32_000L))
        assertEquals(DeviceCondition.NORMAL, result.state.condition)
        assertTrue(result.resumedChecks.isEmpty())
        assertTrue(result.state.deferredChecks.isEmpty())
    }

    /** 只恢复实际被跳过的检查，不能因为共享状态把另一条检查也排入补跑。 */
    @Test
    fun onlyActuallySkippedCheckIsResumed() {
        var state = evaluate(null, signals(thermal = ThermalLevel.MODERATE, now = 1_000L)).state
        state = DeviceConditionPolicy.markCheckSkipped(state, DeviceWork.DSH_UPDATE_CHECK)

        var result = evaluate(state, signals(thermal = ThermalLevel.NONE, now = 2_000L))
        assertTrue(result.resumedChecks.isEmpty())

        result = evaluate(result.state, signals(thermal = ThermalLevel.NONE, now = 32_000L))
        assertEquals(setOf(DeviceWork.DSH_UPDATE_CHECK), result.resumedChecks)
        assertFalse(result.resumedChecks.contains(DeviceWork.APK_UPDATE_CHECK))
        assertEquals(setOf(DeviceWork.DSH_UPDATE_CHECK), result.state.deferredChecks)
    }

    /** 补跑入队失败时不确认清除；后续采样仍会再次报告同一待办。 */
    @Test
    fun failedResumeKeepsPendingForRetry() {
        val hot = DeviceConditionPolicy.markCheckSkipped(
            evaluate(null, signals(thermal = ThermalLevel.MODERATE, now = 1_000L)).state,
            DeviceWork.APK_UPDATE_CHECK,
        )
        // 先进入 HOT 的降级观察期，再等满 30 秒。
        var result = evaluate(hot, signals(thermal = ThermalLevel.NONE, now = 2_000L))
        assertEquals(DeviceCondition.HOT, result.state.condition)
        assertTrue(result.resumedChecks.isEmpty())

        result = evaluate(result.state, signals(thermal = ThermalLevel.NONE, now = 32_000L))
        assertEquals(setOf(DeviceWork.APK_UPDATE_CHECK), result.resumedChecks)
        assertEquals(setOf(DeviceWork.APK_UPDATE_CHECK), result.state.deferredChecks)

        // 没有调用 acknowledgeChecks，相当于入队失败：下一次采样仍待补跑。
        result = evaluate(result.state, signals(thermal = ThermalLevel.NONE, now = 33_000L))
        assertEquals(setOf(DeviceWork.APK_UPDATE_CHECK), result.resumedChecks)
        assertEquals(setOf(DeviceWork.APK_UPDATE_CHECK), result.state.deferredChecks)
    }

    /**
     * 确认回调阻塞期间同一检查被重新登记：旧代次的确认不能删除新待办。
     */
    @Test
    fun staleAcknowledgementKeepsCheckReRegisteredWhileConfirmationPending() {
        val hot = DeviceConditionPolicy.markCheckSkipped(
            evaluate(null, signals(thermal = ThermalLevel.MODERATE, now = 1_000L)).state,
            DeviceWork.APK_UPDATE_CHECK,
        )
        var result = evaluate(hot, signals(thermal = ThermalLevel.NONE, now = 2_000L))
        result = evaluate(result.state, signals(thermal = ThermalLevel.NONE, now = 32_000L))
        assertEquals(setOf(DeviceWork.APK_UPDATE_CHECK), result.resumedChecks)
        val expectedAtEnqueue = result.state.deferredGenerations
        assertEquals(1L, expectedAtEnqueue[DeviceWork.APK_UPDATE_CHECK])

        // 确认尚未返回时设备再次受限，Worker 重新登记同一检查，代次递增到 2。
        var reblocked = evaluate(result.state, signals(thermal = ThermalLevel.MODERATE, now = 33_000L)).state
        assertEquals(DeviceCondition.HOT, reblocked.condition)
        reblocked = DeviceConditionPolicy.markCheckSkipped(reblocked, DeviceWork.APK_UPDATE_CHECK)
        assertEquals(2L, reblocked.deferredGenerations[DeviceWork.APK_UPDATE_CHECK])

        // 旧确认只对应代次 1，必须保留代次 2 的新待办。
        val staleAck = DeviceConditionPolicy.acknowledgeChecks(
            reblocked,
            setOf(DeviceWork.APK_UPDATE_CHECK),
            expectedAtEnqueue,
        )
        assertTrue(staleAck.deferredChecks.contains(DeviceWork.APK_UPDATE_CHECK))
        assertEquals(2L, staleAck.deferredGenerations[DeviceWork.APK_UPDATE_CHECK])

        // 新代次确认后才能清除。
        val freshAck = DeviceConditionPolicy.acknowledgeChecks(
            reblocked,
            setOf(DeviceWork.APK_UPDATE_CHECK),
            reblocked.deferredGenerations,
        )
        assertTrue(freshAck.deferredChecks.isEmpty())
        assertTrue(freshAck.deferredGenerations.isEmpty())
    }

    /**
     * 旧确认悬停期间，另一确认先清除了同一待办，随后再次登记：下一轮代次不能从 1 重用。
     */
    @Test
    fun oldConfirmationCannotDeleteCheckRegisteredAfterAnotherConfirmation() {
        val hot = evaluate(null, signals(thermal = ThermalLevel.MODERATE, now = 1_000L)).state
        val first = DeviceConditionPolicy.markCheckSkipped(hot, DeviceWork.APK_UPDATE_CHECK)
        val oldExpected = first.deferredGenerations
        assertEquals(1L, oldExpected[DeviceWork.APK_UPDATE_CHECK])

        // 另一确认先清除代次 1；counters 必须保留已发放的最大代次。
        val cleared = DeviceConditionPolicy.acknowledgeChecks(
            first,
            setOf(DeviceWork.APK_UPDATE_CHECK),
            oldExpected,
        )
        assertTrue(cleared.deferredChecks.isEmpty())
        assertEquals(1L, cleared.deferredGenerationCounters[DeviceWork.APK_UPDATE_CHECK])

        // 设备再次受限，Worker 重新登记同类检查，代次必须递增到 2 而不是重用 1。
        val reRegistered = DeviceConditionPolicy.markCheckSkipped(cleared, DeviceWork.APK_UPDATE_CHECK)
        assertEquals(2L, reRegistered.deferredGenerations[DeviceWork.APK_UPDATE_CHECK])

        // 最早那条旧确认此时才返回，只能匹配代次 1，不能删除代次 2 的新待办。
        val stale = DeviceConditionPolicy.acknowledgeChecks(
            reRegistered,
            setOf(DeviceWork.APK_UPDATE_CHECK),
            oldExpected,
        )
        assertTrue(stale.deferredChecks.contains(DeviceWork.APK_UPDATE_CHECK))
        assertEquals(2L, stale.deferredGenerations[DeviceWork.APK_UPDATE_CHECK])

        // 代次 2 的新确认可以正常清除，counters 仍保留 2。
        val fresh = DeviceConditionPolicy.acknowledgeChecks(
            reRegistered,
            setOf(DeviceWork.APK_UPDATE_CHECK),
            reRegistered.deferredGenerations,
        )
        assertTrue(fresh.deferredChecks.isEmpty())
        assertEquals(2L, fresh.deferredGenerationCounters[DeviceWork.APK_UPDATE_CHECK])
    }

    @Test
    fun acknowledgementOnlyClearsConfirmedChecks() {
        val hot = evaluate(null, signals(thermal = ThermalLevel.MODERATE, now = 1_000L)).state
        val pending = DeviceConditionPolicy.markCheckSkipped(
            DeviceConditionPolicy.markCheckSkipped(hot, DeviceWork.APK_UPDATE_CHECK),
            DeviceWork.DSH_UPDATE_CHECK,
        )
        val acknowledged = DeviceConditionPolicy.acknowledgeChecks(pending, setOf(DeviceWork.APK_UPDATE_CHECK))
        assertEquals(setOf(DeviceWork.DSH_UPDATE_CHECK), acknowledged.deferredChecks)
    }

    /**
     * Worker 准入决定必须与本次评估绑定；评估后共享状态被另一刷新改变，也不能改写本次返回值。
     */
    @Test
    fun workerAdmissionIsBoundToEvaluationAndNotLaterState() {
        val normal = evaluate(null, signals(now = 1_000L)).state
        val admitted = DeviceConditionPolicy.admitCheck(normal, DeviceWork.APK_UPDATE_CHECK)
        assertTrue(admitted.allowed)
        assertTrue(admitted.state.deferredChecks.isEmpty())

        // 评估后另一刷新把共享状态改为低电，但本次决定仍为允许。
        val laterBlocked = evaluate(admitted.state, signals(battery = 5, now = 2_000L)).state
        assertFalse(laterBlocked.backgroundChecksAllowed)
        assertTrue(admitted.allowed)

        // 反向：本次评估受限并登记待办，之后即使共享状态恢复，本次决定仍为拒绝。
        val blocked = evaluate(null, signals(battery = 5, now = 3_000L)).state
        val denied = DeviceConditionPolicy.admitCheck(blocked, DeviceWork.APK_UPDATE_CHECK)
        assertFalse(denied.allowed)
        assertTrue(denied.state.deferredChecks.contains(DeviceWork.APK_UPDATE_CHECK))

        val laterAllowed = evaluate(denied.state, signals(battery = 100, now = 4_000L)).state
        assertTrue(laterAllowed.backgroundChecksAllowed)
        assertFalse(denied.allowed)
        assertTrue(laterAllowed.deferredChecks.contains(DeviceWork.APK_UPDATE_CHECK))
    }

    @Test
    fun skippedCheckRegistrationIsIdempotentAndLimitedToUpdates() {
        val hot = evaluate(null, signals(thermal = ThermalLevel.MODERATE, now = 1_000L)).state

        val once = DeviceConditionPolicy.markCheckSkipped(hot, DeviceWork.APK_UPDATE_CHECK)
        val twice = DeviceConditionPolicy.markCheckSkipped(once, DeviceWork.APK_UPDATE_CHECK)
        assertEquals(setOf(DeviceWork.APK_UPDATE_CHECK), twice.deferredChecks)

        val both = DeviceConditionPolicy.markCheckSkipped(twice, DeviceWork.DSH_UPDATE_CHECK)
        assertEquals(
            setOf(DeviceWork.APK_UPDATE_CHECK, DeviceWork.DSH_UPDATE_CHECK),
            both.deferredChecks,
        )
        assertThrows(IllegalArgumentException::class.java) {
            DeviceConditionPolicy.markCheckSkipped(hot, DeviceWork.NPM_DEPENDENCY_DOWNLOAD)
        }
    }

    @Test
    fun overheatWinsOverLowBatteryAndKeepsBothReasons() {
        val state = evaluate(null, signals(thermal = ThermalLevel.MODERATE, battery = 10, now = 1_000L)).state
        assertEquals(DeviceCondition.HOT, state.condition)
        assertEquals(setOf(DeviceConditionReason.THERMAL, DeviceConditionReason.LOW_BATTERY), state.reasons)
        assertEquals(4, state.workerCap)
        assertFalse(state.backgroundChecksAllowed)
    }

    @Test
    fun userDownloadKeepsRunningWhileDependencyDownloadsAreThrottled() {
        val state = evaluate(null, signals(thermal = ThermalLevel.LIGHT, now = 1_000L)).state
        assertEquals(WorkDisposition.RUN, DeviceConditionPolicy.decide(state, DeviceWork.USER_DOWNLOAD).disposition)
        assertEquals(WorkDisposition.THROTTLE, DeviceConditionPolicy.decide(state, DeviceWork.NPM_DEPENDENCY_DOWNLOAD).disposition)
        assertEquals(WorkDisposition.THROTTLE, DeviceConditionPolicy.decide(state, DeviceWork.LOG_RENDER).disposition)
        assertEquals(WorkDisposition.THROTTLE, DeviceConditionPolicy.decide(state, DeviceWork.PREVIEW_RENDER).disposition)
    }

    @Test
    fun protectedWorkIsNeverDeferredOrCancelledEvenAtCritical() {
        val state = evaluate(null, signals(thermal = ThermalLevel.SHUTDOWN, now = 1_000L)).state
        assertEquals(DeviceCondition.CRITICAL, state.condition)

        assertEquals(WorkDisposition.RUN, DeviceConditionPolicy.decide(state, DeviceWork.DSH_TASK).disposition)
        assertEquals(WorkDisposition.RUN, DeviceConditionPolicy.decide(state, DeviceWork.FOREGROUND_TASK).disposition)
        assertFalse(DeviceConditionPolicy.mayPauseOrCancel(DeviceWork.DSH_TASK))
        assertFalse(DeviceConditionPolicy.mayPauseOrCancel(DeviceWork.FOREGROUND_TASK))

        assertEquals(WorkDisposition.THROTTLE, DeviceConditionPolicy.decide(state, DeviceWork.NPM_DEPENDENCY_DOWNLOAD).disposition)
        assertEquals(WorkDisposition.THROTTLE, DeviceConditionPolicy.decide(state, DeviceWork.LOG_RENDER).disposition)
        assertEquals(WorkDisposition.THROTTLE, DeviceConditionPolicy.decide(state, DeviceWork.PREVIEW_RENDER).disposition)
        assertEquals(WorkDisposition.DEFER, DeviceConditionPolicy.decide(state, DeviceWork.APK_UPDATE_CHECK).disposition)
        assertEquals(WorkDisposition.DEFER, DeviceConditionPolicy.decide(state, DeviceWork.DSH_UPDATE_CHECK).disposition)
        assertTrue(DeviceConditionPolicy.mayPauseOrCancel(DeviceWork.APK_UPDATE_CHECK))
        assertTrue(DeviceConditionPolicy.mayPauseOrCancel(DeviceWork.DSH_UPDATE_CHECK))
    }

    @Test
    fun disablingPolicyRestoresNormalBudgetAndResumesDeferredWork() {
        val hot = evaluate(null, signals(thermal = ThermalLevel.MODERATE, now = 1_000L)).state
        assertTrue(hot.deferredChecks.isEmpty())
        val skipped = DeviceConditionPolicy.markCheckSkipped(hot, DeviceWork.APK_UPDATE_CHECK)

        val result = DeviceConditionPolicy.evaluate(
            skipped,
            signals(thermal = ThermalLevel.MODERATE, now = 2_000L),
            config.copy(enabled = false),
        )
        assertEquals(DeviceCondition.NORMAL, result.state.condition)
        assertFalse(result.state.enabled)
        assertEquals(setOf(DeviceWork.APK_UPDATE_CHECK), result.state.deferredChecks)
        assertEquals(setOf(DeviceWork.APK_UPDATE_CHECK), result.resumedChecks)
        assertEquals(16, result.state.workerCap)
        assertTrue(result.state.backgroundChecksAllowed)
        val acknowledged = DeviceConditionPolicy.acknowledgeChecks(result.state, result.resumedChecks)
        assertTrue(acknowledged.deferredChecks.isEmpty())
        assertEquals(
            WorkDisposition.RUN,
            DeviceConditionPolicy.decide(acknowledged, DeviceWork.APK_UPDATE_CHECK).disposition,
        )
    }

    /**
     * 进程重启时先恢复旧状态；即使新采样已经正常，也要满足迟滞后才会恢复后台工作。
     */
    @Test
    fun restoredHotStateKeepsDeferralUntilDwellElapses() {
        val hot = DeviceConditionPolicy.markCheckSkipped(
            evaluate(null, signals(thermal = ThermalLevel.MODERATE, now = 1_000L)).state,
            DeviceWork.APK_UPDATE_CHECK,
        )
        assertTrue(hot.deferredChecks.contains(DeviceWork.APK_UPDATE_CHECK))

        var result = evaluate(hot, signals(thermal = ThermalLevel.NONE, now = 2_000L))
        assertEquals(DeviceCondition.HOT, result.state.condition)
        assertTrue(result.state.deferredChecks.contains(DeviceWork.APK_UPDATE_CHECK))
        assertTrue(result.resumedChecks.isEmpty())

        result = evaluate(result.state, signals(thermal = ThermalLevel.NONE, now = 32_000L))
        assertEquals(DeviceCondition.NORMAL, result.state.condition)
        assertEquals(setOf(DeviceWork.APK_UPDATE_CHECK), result.resumedChecks)
        assertEquals(setOf(DeviceWork.APK_UPDATE_CHECK), result.state.deferredChecks)
        assertTrue(DeviceConditionPolicy.acknowledgeChecks(result.state, result.resumedChecks).deferredChecks.isEmpty())
    }

    @Test
    fun budgetsDecreaseMonotonicallyAcrossConditions() {
        val normal = evaluate(null, signals(now = 1_000L)).state
        val warm = evaluate(null, signals(thermal = ThermalLevel.LIGHT, now = 2_000L)).state
        val hot = evaluate(null, signals(thermal = ThermalLevel.MODERATE, now = 3_000L)).state
        val critical = evaluate(null, signals(thermal = ThermalLevel.SEVERE, now = 4_000L)).state
        val lowBattery = evaluate(null, signals(battery = 10, now = 5_000L)).state

        assertTrue(normal.workerCap > warm.workerCap)
        assertTrue(warm.workerCap > hot.workerCap)
        assertTrue(hot.workerCap > critical.workerCap)
        assertTrue(normal.logLineLimit > warm.logLineLimit)
        assertTrue(warm.logLineLimit > hot.logLineLimit)
        assertTrue(hot.logLineLimit > critical.logLineLimit)
        assertTrue(normal.previewMaxDimension > warm.previewMaxDimension)
        assertTrue(warm.previewMaxDimension > hot.previewMaxDimension)
        assertTrue(hot.previewMaxDimension > critical.previewMaxDimension)
        assertTrue(normal.previewMaxPixels > warm.previewMaxPixels)
        assertTrue(warm.previewMaxPixels > hot.previewMaxPixels)
        assertTrue(hot.previewMaxPixels > critical.previewMaxPixels)

        assertEquals(4, lowBattery.workerCap)
        assertFalse(lowBattery.backgroundChecksAllowed)
        assertEquals(DeviceConditionReason.LOW_BATTERY, lowBattery.reasons.single())
    }

    @Test
    fun sinceMillisOnlyChangesWhenPrimaryConditionChanges() {
        val first = evaluate(null, signals(thermal = ThermalLevel.LIGHT, now = 1_000L)).state
        assertEquals(1_000L, first.sinceMillis)

        val same = evaluate(first, signals(thermal = ThermalLevel.LIGHT, now = 2_000L)).state
        assertEquals(1_000L, same.sinceMillis)
        assertEquals(2_000L, same.updatedAtMillis)

        val hotter = evaluate(same, signals(thermal = ThermalLevel.MODERATE, now = 3_000L)).state
        assertEquals(3_000L, hotter.sinceMillis)
    }

    /** CRITICAL 状态下长边与像素两项额度都必须进入 PDF 渲染入口并同时生效。 */
    @Test
    fun criticalPreviewLimitsReachRenderEntryWithBothBounds() {
        val state = evaluate(null, signals(thermal = ThermalLevel.SEVERE, now = 1_000L)).state
        assertEquals(1024, state.previewMaxDimension)
        assertEquals(1_000_000, state.previewMaxPixels)

        val limits = PdfRenderPolicy.limits(state.previewMaxDimension, state.previewMaxPixels)
        assertEquals(1024, limits.maxDimension)
        assertEquals(1_000_000, limits.maxPixels)

        val size = PdfRenderPolicy.viewportSize(
            pageWidth = 595,
            pageHeight = 842,
            viewportWidth = 1080,
            maxDimension = limits.maxDimension,
            maxPixels = limits.maxPixels,
        )
        assertTrue("长边不能超过 CRITICAL 的 1024", maxOf(size.width, size.height) <= 1024)
        assertTrue("总像素不能超过 CRITICAL 的 1,000,000", size.width.toLong() * size.height <= 1_000_000L)
    }

    @Test
    fun invalidThresholdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceConditionConfig(lowBatteryEnterPercent = 30, lowBatteryExitPercent = 30)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceConditionConfig(warmThermalLevel = ThermalLevel.MODERATE, hotThermalLevel = ThermalLevel.LIGHT)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceConditionConfig(normalWorkerCap = 4, warmWorkerCap = 8)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceConditionConfig(thermalDowngradeDwellMillis = -1L)
        }
    }
}
