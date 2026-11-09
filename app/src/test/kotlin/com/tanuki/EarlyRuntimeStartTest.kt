package com.tanuki

import com.tanuki.runtime.EarlyRuntimeStart
import com.tanuki.runtime.EarlyStartGate
import com.tanuki.runtime.EnvironmentState
import com.tanuki.runtime.ServiceState
import com.tanuki.runtime.Snapshot
import com.tanuki.runtime.UpdateState
import org.junit.Assert.*
import org.junit.Test

class EarlyRuntimeStartTest {
    private fun conditions(
        enabled: Boolean = true,
        environment: EnvironmentState = EnvironmentState.READY,
        service: ServiceState = ServiceState.STOPPED,
        update: UpdateState = UpdateState.UNCHECKED,
        operationActive: Boolean = false,
        liveChild: Boolean = false,
        stopping: Boolean = false,
    ) = EarlyRuntimeStart.Conditions(
        enabled = enabled,
        environment = environment,
        service = service,
        update = update,
        operationActive = operationActive,
        liveChild = liveChild,
        stopping = stopping,
    )

    private fun snapshotConditions(
        snapshot: Snapshot,
        runtimeOperationActive: Boolean = false,
        independentOperationActive: Boolean = false,
        liveChild: Boolean = false,
        stopping: Boolean = false,
        enabled: Boolean = true,
    ) = EarlyRuntimeStart.conditionsFrom(
        enabled = enabled,
        snapshot = snapshot,
        runtimeOperationActive = runtimeOperationActive,
        independentOperationActive = independentOperationActive,
        liveChild = liveChild,
        stopping = stopping,
    )

    @Test fun startsOnlyWhenInstalledAndCleanlyStopped() {
        assertTrue(EarlyRuntimeStart.shouldStart(conditions()))
    }

    @Test fun realReadyAndStoppedSnapshotStarts() {
        assertTrue(EarlyRuntimeStart.shouldStart(snapshotConditions(
            Snapshot(environment = EnvironmentState.READY, service = ServiceState.STOPPED)
        )))
    }

    @Test fun realUpdateStatesBlockEarlyStart() {
        // 独立 UpdateManager 的 job 不在 RuntimeManager.job 中，必须由 snapshot.operationActive 覆盖。
        for (update in listOf(
            UpdateState.CHECKING,
            UpdateState.STAGING,
            UpdateState.TESTING,
            UpdateState.BACKUP,
            UpdateState.SWITCHING,
            UpdateState.RESTORING,
        )) {
            val snapshot = Snapshot(environment = EnvironmentState.READY, service = ServiceState.STOPPED, update = update)
            assertFalse("update=$update", EarlyRuntimeStart.shouldStart(snapshotConditions(snapshot)))
        }
    }

    @Test fun realInstallingOrDownloadingEnvironmentBlocksEarlyStart() {
        assertFalse(EarlyRuntimeStart.shouldStart(snapshotConditions(
            Snapshot(environment = EnvironmentState.INSTALLING, service = ServiceState.STOPPED)
        )))
        assertFalse(EarlyRuntimeStart.shouldStart(snapshotConditions(
            Snapshot(environment = EnvironmentState.DOWNLOADING, service = ServiceState.STOPPED)
        )))
        assertFalse(EarlyRuntimeStart.shouldStart(snapshotConditions(
            Snapshot(environment = EnvironmentState.READY, service = ServiceState.STOPPED, operation = "initialize")
        )))
    }

    @Test fun independentUpdateJobBlocksEarlyStart() {
        // 状态尚未写入 STAGING 的窗口也必须由独立 job 信号拦截。
        assertFalse(EarlyRuntimeStart.shouldStart(snapshotConditions(
            Snapshot(environment = EnvironmentState.READY, service = ServiceState.STOPPED),
            independentOperationActive = true,
        )))
    }

    @Test fun runtimeJobBlocksEarlyStart() {
        assertFalse(EarlyRuntimeStart.shouldStart(snapshotConditions(
            Snapshot(environment = EnvironmentState.READY, service = ServiceState.STOPPED),
            runtimeOperationActive = true,
        )))
    }

    @Test fun userSettingCanDisableEarlyStart() {
        assertFalse(EarlyRuntimeStart.shouldStart(conditions(enabled = false)))
    }

    @Test fun environmentMustBeReady() {
        assertFalse(EarlyRuntimeStart.shouldStart(conditions(environment = EnvironmentState.ABSENT)))
        assertFalse(EarlyRuntimeStart.shouldStart(conditions(environment = EnvironmentState.INSTALLING)))
        assertFalse(EarlyRuntimeStart.shouldStart(conditions(environment = EnvironmentState.REPAIR)))
    }

    @Test fun everyNonIdleServiceStateIsSkipped() {
        // 启动中/运行中/停止中不能重复拉起；失败或上次中断也不自动重试，避免掩盖真实问题。
        for (state in listOf(
            ServiceState.STARTING,
            ServiceState.RUNNING,
            ServiceState.STOPPING,
            ServiceState.FAILED,
            ServiceState.INTERRUPTED,
        )) {
            assertFalse("service=$state", EarlyRuntimeStart.shouldStart(conditions(service = state)))
        }
    }

    @Test fun activeInstallOrOperationBlocksEarlyStart() {
        assertFalse(EarlyRuntimeStart.shouldStart(conditions(operationActive = true)))
    }

    @Test fun liveChildBlocksEarlyStart() {
        assertFalse(EarlyRuntimeStart.shouldStart(conditions(liveChild = true)))
    }

    @Test fun stoppingServiceBlocksEarlyStart() {
        assertFalse(EarlyRuntimeStart.shouldStart(conditions(stopping = true)))
    }

    @Test fun recoveryRequiredBlocksEarlyStart() {
        assertFalse(EarlyRuntimeStart.shouldStart(conditions(update = UpdateState.RECOVERY_REQUIRED)))
    }

    @Test fun onlyLauncherMainIntentQualifies() {
        assertTrue(EarlyRuntimeStart.launchedFromLauncher("android.intent.action.MAIN", true))
        // 仪器测试/通知/内部显式 Component 启动通常没有 MAIN+LAUNCHER，不能意外拉起服务。
        assertFalse(EarlyRuntimeStart.launchedFromLauncher("android.intent.action.MAIN", false))
        assertFalse(EarlyRuntimeStart.launchedFromLauncher(null, true))
        assertFalse(EarlyRuntimeStart.launchedFromLauncher("com.tanuki.OPEN_BROWSER", true))
    }

    @Test fun gateStartsAtMostOncePerProcess() {
        val gate = EarlyStartGate()
        var starts = 0
        assertTrue(gate.request(conditions()) { starts++ })
        assertFalse(gate.request(conditions()) { starts++ })
        assertEquals(1, starts)
    }

    @Test fun gateDoesNotInvokeStartWhenConditionsFail() {
        val gate = EarlyStartGate()
        var starts = 0
        assertFalse(gate.request(conditions(enabled = false)) { starts++ })
        assertEquals(0, starts)
    }

    @Test fun gateReleasesSlotAfterStartFailureSoUserCanRetry() {
        val gate = EarlyStartGate()
        var attempts = 0
        assertFalse(gate.request(conditions()) { attempts++; throw IllegalStateException("系统拒绝前台服务") })
        assertEquals(1, attempts)
        // 自动发起失败后释放名额；手动启动入口仍独立可用，下一次请求也能重试。
        assertTrue(gate.request(conditions()) { attempts++ })
        assertEquals(2, attempts)
    }
}
