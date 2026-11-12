package com.tanuki

import com.tanuki.runtime.*
import com.tanuki.ui.*
import org.junit.Assert.*
import org.junit.Test

/** 覆盖首启真实安装边界，而非把单个组件就绪或下载完成当作可聊天。 */
class SetupJourneyPresentationTest {
    private val components = ComponentInventory(ubuntuInstalled = true, nodeInstalled = true,
        dshInstalled = true, ubuntuVersion = "24.04")
    private val tools = RequiredToolsStatus(checked = true, ubuntuMarker = "24.04 profile")
    private val ready = Snapshot(environment = EnvironmentState.READY)
    private fun present(s: Snapshot, c: ComponentInventory = components, t: RequiredToolsStatus = tools) =
        setupJourney(s, c, t, nowMillis = 90_000, appearedAtMillis = 30_000)

    @Test fun ubuntuReadyDoesNotFinishMissingNodeOrAssistant() {
        assertEquals(SetupJourneyPhase.PREPARE, present(ready, components.copy(nodeInstalled = false)).phase)
        assertEquals(SetupJourneyPhase.PREPARE, present(ready, components.copy(dshInstalled = false)).phase)
    }

    @Test fun toolsFromAnotherUbuntuVersionCannotFinishPreparation() {
        assertEquals(SetupJourneyPhase.PREPARE, present(ready, t = tools.copy(ubuntuMarker = "22.04 profile")).phase)
        assertEquals(SetupJourneyPhase.PREPARE, present(ready, t = tools.copy(checked = false)).phase)
    }

    @Test fun completeComponentsStillNeedStartup() {
        val state = present(ready)
        assertEquals(SetupJourneyPhase.READY, state.phase)
        assertEquals("START", state.command)
    }

    @Test fun runningRequiresHealthyWebEndpoint() {
        assertEquals(SetupJourneyPhase.READY, present(ready.copy(service = ServiceState.RUNNING)).phase)
        assertEquals(SetupJourneyPhase.RUNNING, present(ready.copy(service = ServiceState.RUNNING, webHealthy = true)).phase)
    }

    @Test fun activeInstallWinsOverUbuntuReadyAndOldFailure() {
        val state = present(ready.copy(operation = "initialize", stage = "安装助手", error = "旧错误"))
        assertEquals(SetupJourneyPhase.WORKING, state.phase)
        assertNull(state.action)
        assertTrue(state.progress!!.active)
    }

    @Test fun startupNeverUsesOldDownloadPercent() {
        val state = present(ready.copy(service = ServiceState.STARTING, bytes = 100, total = 100))
        assertEquals(SetupJourneyPhase.WORKING, state.phase)
        assertNull(state.progress!!.fraction)
        assertFalse(state.progress.canCancel)
    }

    @Test fun failedStartupExposesRetryInsteadOfReady() {
        val state = present(ready.copy(service = ServiceState.FAILED, error = "startup timeout"))
        assertEquals(SetupJourneyPhase.FAILED, state.phase)
        assertEquals("START", state.command)
    }

    @Test fun updateLookupFailureKeepsExistingRuntimeUsableAndErrorVisible() {
        val running = present(ready.copy(service = ServiceState.RUNNING, webHealthy = true,
            update = UpdateState.CHECK_FAILED, error = "获取新版本失败"))
        assertEquals(SetupJourneyPhase.RUNNING, running.phase)
        assertNotNull(running.notice)
        val stopped = present(ready.copy(update = UpdateState.CHECK_FAILED, error = "获取新版本失败"))
        assertEquals("START", stopped.command)
        assertNotNull(stopped.notice)
    }

    @Test fun recoveryBlocksLaunchEvenWithAllComponents() {
        val state = present(ready.copy(update = UpdateState.RECOVERY_REQUIRED))
        assertEquals(SetupJourneyPhase.RECOVERY, state.phase)
        assertEquals("RESUME_UPDATE", state.command)
    }

    @Test fun cancellationKeepsResumeActionWithoutClaimingRunning() {
        val state = present(Snapshot(environment = EnvironmentState.CANCELLED))
        assertEquals(SetupJourneyPhase.CANCELLED, state.phase)
        assertNotNull(state.action)
        assertNull(state.command)
    }

    @Test fun retryRetainsOriginalOperationIdentity() {
        val state = present(ready.copy(failedOperation = FailedOperation(42, "卸载组件", "失败", 1234)))
        assertEquals(SetupJourneyPhase.FAILED, state.phase)
        assertEquals("RETRY_OPERATION/42", state.command)
    }

    @Test fun invalidatedRetryGoesToComponentsInsteadOfStartingAnotherInstall() {
        val state = present(ready.copy(failedOperation = FailedOperation(42, "切换组件", "失败", 1234, retryAvailable = false)))
        assertEquals("OPEN_COMPONENTS", state.command)
    }

    @Test fun repairErrorCannotDisappearBehindPreparationAction() {
        val state = present(Snapshot(environment = EnvironmentState.REPAIR, error = "ENOSPC"))
        assertEquals(SetupJourneyPhase.FAILED, state.phase)
        assertTrue(state.detail.contains("空间"))
    }

    @Test fun unhealthyRunningServiceOffersHealthCheckInsteadOfDuplicateStart() {
        val state = present(ready.copy(service = ServiceState.RUNNING))
        assertEquals("HEALTH", state.command)
    }

    @Test fun elapsedComesFromRuntimeClockAndUnknownTotalStaysIndeterminate() {
        val state = present(Snapshot(operation = "initialize", stage = "解压系统组件",
            environment = EnvironmentState.INSTALLING, timing = OperationTiming(10_000, 20_000)))
        assertEquals("已用 1分20秒", state.progress!!.elapsedText)
        assertNull(state.progress.fraction)
        assertNotNull(state.progress.quietWarning)
    }
}
