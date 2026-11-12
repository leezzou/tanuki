package com.tanuki

import com.tanuki.runtime.*
import com.tanuki.ui.OperationProgressMode
import com.tanuki.ui.operationProgress
import com.tanuki.ui.TanukiUiCallbacks
import com.tanuki.ui.performFailureAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.*
import org.junit.Test

/** 合成停止确认与竞争快照，直接验证分类器及真实进度呈现，不把纯状态测试当设备终止验收。 */
class UserStopStateTest {
    private val history = RuntimeRecoveryState(lastFailure = FailureKind.STARTUP_FAILED, lastFailureAtMillis = 1000)
    private val failure = FailureCatalog.fromRuntimeFailure(FailureKind.STARTUP_FAILED, history, "synthetic old runtime failure", 1000)
    private val trace = StartupProgress(7, StartupStage.OUTPUT, 10, 20, 30, outcome = StartupOutcome.FAILED)
    private val diagnostic = NetworkDiagnostic(NetworkDiagnosticState.FAILED, "synthetic offline diagnostic")
    private val failed = Snapshot(environment = EnvironmentState.READY, service = ServiceState.FAILED,
        stage = "下载词不能改变错误来源", error = failure.detail, failure = failure, recovery = history,
        startup = trace, networkDiagnostic = diagnostic, events = listOf("synthetic historical failure event"))

    private fun finish(before: Snapshot, current: Snapshot = before.copy(service = ServiceState.STOPPING),
                       confirmed: Boolean = true, user: Boolean = true, sameGeneration: Boolean = true): UserStopResult {
        val boundary = UserStopState(before)
        val state = MutableStateFlow(current)
        var result: UserStopResult? = null
        state.update {
            boundary.finish(it, confirmed, user, sameGeneration).also { next -> result = next }.snapshot
        }
        return result!!.also { assertSame(state.value, it.snapshot) }
    }

    @Test fun successfulUserStopCannotReconstructOldRuntimeFailureInCatalogOrPresentation() {
        assertSame(failure, failed.displayFailure())
        assertEquals(OperationProgressMode.FAILED, operationProgress(failed, 50, 0)!!.mode)
        val result = finish(failed)
        val next = result.snapshot
        assertEquals(ServiceState.STOPPED, next.service)
        assertEquals("", next.error)
        assertNull(next.failure)
        assertNull(next.displayFailure())
        assertNull(operationProgress(next, 50, 0))
        assertEquals("助手已停止", next.stage)
        assertEquals(failure.detail, result.persistedRuntimeError)
        assertSame(history, next.recovery)
        assertSame(trace, next.startup)
        assertSame(diagnostic, next.networkDiagnostic)
        assertSame(failed.events, next.events)
    }

    @Test fun aKnownRuntimeRecordWithoutAppFailureCannotRebuildTheOldRawError() {
        val record = FailedOperation(4, "old runtime", failure.detail, 30, domain = FailureDomain.RUNTIME, operation = "runtime-stop")
        val before = failed.copy(failure = null, failedOperation = record)
        assertNotNull(before.displayFailure())
        val result = finish(before)
        assertNull(result.snapshot.displayFailure())
        assertNull(operationProgress(result.snapshot, 50, 0))
        assertEquals(before.error, result.persistedRuntimeError)
        assertSame(before.recovery, result.snapshot.recovery)
    }

    @Test fun ownedRuntimeRetryRecordIsRetiredTogetherWithItsFailure() {
        val retry = FailureRetry("START")
        val before = failed.copy(operationRetry = retry, failedOperation = FailedOperation(5, "old", failure.detail, 40,
            domain = FailureDomain.RUNTIME, operation = "runtime-stop"))
        val next = finish(before).snapshot
        assertNull(next.failedOperation)
        assertNull(next.operationRetry)
        assertNull(next.displayFailure())
        assertNull(operationProgress(next, 50, 0))
    }

    @Test fun installationAndUpdateFailuresKeepTheirRealClassificationAndRetryPresentation() {
        for ((operation, domain) in listOf("component-node" to FailureDomain.INSTALL, "dsh-install" to FailureDomain.UPDATE)) {
            val error = FailureCatalog.describe(FailureInput("DSH startup failure is only synthetic copy", operation = operation))
            val record = FailedOperation(6, "启动失败也不能猜归属", error.detail, 30,
                domain = FailureCatalog.operationDomain(operation)!!, operation = operation)
            val before = failed.copy(error = error.detail, failure = error, failedOperation = record,
                stage = record.stage, update = if (domain == FailureDomain.UPDATE) UpdateState.FAILED else UpdateState.UNCHECKED,
                environment = if (domain == FailureDomain.INSTALL) EnvironmentState.FAILED else EnvironmentState.READY)
            val result = finish(before)
            val next = result.snapshot
            assertEquals(ServiceState.STOPPED, next.service)
            assertSame(error, next.displayFailure())
            assertEquals(domain, next.displayFailure()!!.domain)
            assertSame(record, next.failedOperation)
            assertEquals(record.stage, next.stage)
            assertEquals(OperationProgressMode.FAILED, operationProgress(next, 50, 0)!!.mode)
            assertTrue(operationProgress(next, 50, 0)!!.detail.contains(record.reason))
            assertNull(result.persistedRuntimeError)
        }
    }

    @Test fun diagnosticsAreNotHealedByStoppingTheService() {
        for (operation in listOf("health", "background-probe")) {
            val error = FailureCatalog.describe(FailureInput("synthetic diagnostic failure", operation = operation))
            val record = FailedOperation(8, "诊断", error.detail, 20, domain = FailureDomain.RUNTIME, operation = operation)
            val before = failed.copy(health = HealthState.FAILED, error = error.detail, failure = error, failedOperation = record)
            val next = finish(before).snapshot
            assertSame(error, next.displayFailure())
            assertSame(record, next.failedOperation)
            assertEquals(HealthState.FAILED, next.health)
            assertSame(diagnostic, next.networkDiagnostic)
            assertEquals(OperationProgressMode.FAILED, operationProgress(next, 50, 0)!!.mode)
        }
    }

    @Test fun unknownRawErrorAndUnknownRetryOwnershipStayVisible() {
        val record = FailedOperation(9, "unknown", "synthetic raw error", 20)
        val before = failed.copy(error = record.reason, failure = null, failedOperation = record)
        val result = finish(before)
        assertEquals(record.reason, result.snapshot.displayFailure()!!.detail)
        assertSame(record, result.snapshot.failedOperation)
        assertEquals(OperationProgressMode.FAILED, operationProgress(result.snapshot, 50, 0)!!.mode)
        assertNull(result.persistedRuntimeError)
    }

    @Test fun aSameValueFailurePublicationThroughStateFlowHasANewRevision() {
        val error = IllegalStateException("synthetic same-value failure")
        val before = runtimeFailureSnapshot(failed.copy(service = ServiceState.STOPPING), error, "runtime-stop", 7, error.message!!, 1000)
        val state = MutableStateFlow(before)
        state.update { runtimeFailureSnapshot(it, error, "runtime-stop", 7, error.message!!, 1000) }
        val published = state.value
        assertEquals(before.error, published.error)
        assertEquals(before.failure, published.failure)
        assertNotSame(before.failure, published.failure)
        assertEquals(before.failureRevision + 1, published.failureRevision)
        val result = finish(before, published)
        assertSame(published.failure, result.snapshot.displayFailure())
        assertEquals(published.error, result.snapshot.error)
        assertNull(result.persistedRuntimeError)
    }

    @Test fun newFailureWithIdenticalDataIsNotTheCapturedErrorEvent() {
        val newer = failure.copy()
        assertEquals(failure, newer)
        assertNotSame(failure, newer)
        val current = failed.copy(service = ServiceState.STOPPING, failure = newer)
        val result = finish(failed, current)
        assertSame(newer, result.snapshot.displayFailure())
        assertEquals(failure.detail, result.snapshot.error)
        assertNull(result.persistedRuntimeError)
    }

    @Test fun aNewDirectErrorIsNotHiddenByTheOldStructuredFailure() {
        val current = failed.copy(service = ServiceState.STOPPING, error = "synthetic new failure", stage = "新的阶段")
        val result = finish(failed, current)
        assertEquals(current.error, result.snapshot.displayFailure()!!.detail)
        assertEquals(current.stage, result.snapshot.stage)
        assertNull(result.persistedRuntimeError)
    }

    @Test fun replacementRetryRecordAlsoProtectsTheNewErrorEvent() {
        val record = FailedOperation(10, "old stage", failure.detail, 30, domain = FailureDomain.RUNTIME, operation = "runtime-stop")
        val before = failed.copy(failedOperation = record)
        val replacement = record.copy()
        val result = finish(before, before.copy(service = ServiceState.STOPPING, failedOperation = replacement))
        assertSame(replacement, result.snapshot.failedOperation)
        assertSame(failure, result.snapshot.displayFailure())
        assertEquals(OperationProgressMode.FAILED, operationProgress(result.snapshot, 50, 0)!!.mode)
        assertNull(result.persistedRuntimeError)
    }

    @Test fun aNewInstallFailureDuringStopKeepsItsStageAndPresentation() {
        val error = FailureCatalog.describe(FailureInput("synthetic new install failure", operation = "component-ubuntu"))
        val current = failed.copy(service = ServiceState.STOPPING, environment = EnvironmentState.FAILED,
            failure = error, error = error.detail, stage = "新的安装阶段")
        val next = finish(failed, current).snapshot
        assertSame(error, next.displayFailure())
        assertEquals(FailureDomain.INSTALL, next.displayFailure()!!.domain)
        assertEquals(current.stage, next.stage)
        assertEquals(OperationProgressMode.FAILED, operationProgress(next, 50, 0)!!.mode)
    }

    @Test fun stopFailureCannotRunTheSuccessCleanup() {
        val error = FailureCatalog.describe(FailureInput("synthetic termination not confirmed", operation = "runtime-stop", service = ServiceState.STOPPING))
        val current = failed.copy(service = ServiceState.RUNNING, error = error.detail, failure = error)
        val result = finish(failed, current, confirmed = false)
        assertSame(current, result.snapshot)
        assertSame(error, result.snapshot.displayFailure())
        assertEquals("停止助手未完成", result.snapshot.displayFailure()!!.title)
        assertEquals("STOP", result.snapshot.displayFailure()!!.retryCommand)
        assertNull(result.persistedRuntimeError)
    }

    @Test fun outerErrorReportingCannotReclassifyAnUnconfirmedStopAsTheActiveInstall() {
        val error = RuntimeStopException("synthetic stop not confirmed", IllegalStateException("synthetic child"), 7)
        val before = failed.copy(service = ServiceState.RUNNING, operation = "component-node", operationRetry = FailureRetry("NODE_REINSTALL"),
            environment = EnvironmentState.FAILED, update = UpdateState.FAILED)
        val current = runtimeFailureSnapshot(before, error, "dsh-install", 7, error.message!!, 1000)
        assertEquals(FailureDomain.RUNTIME, current.displayFailure()!!.domain)
        assertEquals("停止助手未完成", current.displayFailure()!!.title)
        assertEquals("STOP", current.displayFailure()!!.retryCommand)
    }

    @Test fun aLateOuterStopExceptionCannotPublishIntoTheNewRuntime() {
        val error = RuntimeStopException("synthetic old stop", IllegalStateException("synthetic child"), 7)
        val current = failed.copy(service = ServiceState.RUNNING, error = "", failure = null,
            startup = trace.copy(attemptId = 8, outcome = StartupOutcome.READY))
        val state = MutableStateFlow(current)
        state.update { runtimeFailureSnapshot(it, error, null, 8, error.message!!, 1000) }
        assertSame(current, state.value)
        assertEquals(ServiceState.RUNNING, state.value.service)
        assertNull(state.value.displayFailure())
        assertNull(operationProgress(state.value, 50, 0))
    }

    @Test fun savingFailureAfterConfirmedStopStaysVisibleAndRetriesStopInsteadOfRestarting() {
        val stopped = finish(failed).snapshot
        val error = FailureCatalog.describe(FailureInput("synthetic state save failure", operation = "runtime-stop", service = stopped.service))
        val current = stopped.copy(error = error.detail, failure = error)
        val shown = current.displayFailure()!!
        assertEquals(ServiceState.STOPPED, current.service)
        assertEquals("停止状态保存未完成", shown.title)
        assertEquals(FailureActionKind.RETRY, shown.primaryAction.kind)
        var retry: FailureRetry? = null
        val commands = mutableListOf<String>()
        val callbacks = TanukiUiCallbacks(command = { commands += it }, install = {}, checkUpdates = {}, importFiles = {},
            measureStorage = {}, toggleLegacy = {}, toggleBlob = {}, showNotices = {}, updateWebView = {},
            webViewSettings = {}, appSettings = {}, notifications = {}, connectDsh = {}, retryFailure = { retry = it })
        performFailureAction(shown, shown.primaryAction.kind, callbacks, {}, {})
        assertEquals(FailureRetry("STOP"), retry)
        assertTrue(commands.isEmpty())
        assertSame(history, current.recovery)
    }

    @Test fun automaticInterruptionRetainsTheActualFailureAndHistory() {
        val error = FailureCatalog.describe(FailureInput("synthetic budget interruption", operation = "runtime-stop"))
        val current = failed.copy(service = ServiceState.INTERRUPTED, error = error.detail, failure = error)
        val result = finish(failed, current, user = false)
        assertSame(current, result.snapshot)
        assertSame(error, result.snapshot.displayFailure())
        assertEquals(OperationProgressMode.FAILED, operationProgress(result.snapshot, 50, 0)!!.mode)
        assertSame(history, result.snapshot.recovery)
    }

    @Test fun cancellingStartupKeepsCancelledTimingAsHistoryButDoesNotShowOldFailure() {
        val before = failed.copy(service = ServiceState.STARTING, error = "", startup = trace.copy(outcome = StartupOutcome.ACTIVE, finishedAtMillis = null))
        val cancelled = before.startup!!.copy(outcome = StartupOutcome.CANCELLED, finishedAtMillis = 40)
        val next = finish(before, before.copy(service = ServiceState.STOPPING, startup = cancelled)).snapshot
        assertSame(cancelled, next.startup)
        assertEquals(StartupOutcome.CANCELLED, next.startup!!.outcome)
        assertSame(history, next.recovery)
        assertNull(next.displayFailure())
        assertNull(operationProgress(next, 50, 0))
    }

    @Test fun successfulOldStartupTraceDoesNotBecomeCurrentServiceReadiness() {
        val before = failed.copy(service = ServiceState.RUNNING, error = "", failure = null,
            startup = trace.copy(outcome = StartupOutcome.READY))
        val next = finish(before).snapshot
        assertEquals(ServiceState.STOPPED, next.service)
        assertEquals(StartupOutcome.READY, next.startup!!.outcome)
        assertNull(next.displayFailure())
        assertNull(operationProgress(next, 50, 0))
    }

    @Test fun aNewGenerationCannotBeStoppedOrHaveItsFailureRemovedByTheOldCompletion() {
        val current = failed.copy(service = ServiceState.RUNNING, startup = trace.copy(attemptId = 8), failure = failure.copy())
        val result = finish(failed, current, sameGeneration = false)
        assertSame(current, result.snapshot)
        assertSame(current.failure, result.snapshot.displayFailure())
        assertNull(result.persistedRuntimeError)
    }

    @Test fun aNewOperationDuringStopKeepsItsActivePhase() {
        val current = failed.copy(service = ServiceState.STOPPING, operation = "component-node", error = "", failure = null,
            environment = EnvironmentState.DOWNLOADING, stage = "新的组件下载")
        val next = finish(failed, current).snapshot
        assertEquals(current.stage, next.stage)
        assertEquals(current.operation, next.operation)
        assertTrue(operationProgress(next, 50, 0)!!.active)
        assertNull(next.displayFailure())
    }

    @Test fun operationOwnershipUsesCommandsAndKeepsSwitchingInTheUpdateDomain() {
        assertEquals(FailureDomain.INSTALL, FailureCatalog.operationDomain("component-node"))
        assertEquals(FailureDomain.UPDATE, FailureCatalog.operationDomain("component-switch-dsh"))
        assertEquals(FailureDomain.RUNTIME, FailureCatalog.operationDomain("runtime-stop"))
        assertNull(FailureCatalog.operationDomain("synthetic unknown command"))
    }
}
