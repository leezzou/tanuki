package com.tanuki

import com.tanuki.runtime.EarlyRuntimeStart
import com.tanuki.runtime.EarlyStartGate
import com.tanuki.runtime.EnvironmentState
import com.tanuki.runtime.ServiceState
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

    @Test fun startsOnlyWhenInstalledAndCleanlyStopped() {
        assertTrue(EarlyRuntimeStart.shouldStart(conditions()))
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
