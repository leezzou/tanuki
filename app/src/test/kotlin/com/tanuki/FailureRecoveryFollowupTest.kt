package com.tanuki

import com.tanuki.runtime.*
import com.tanuki.ui.TanukiUiCallbacks
import com.tanuki.ui.performFailureAction
import org.junit.Assert.*
import org.junit.Test

/** 错误恢复评审回归；调用展示兜底和实际按钮分发，不执行设备或真实安装。 */
class FailureRecoveryFollowupTest {
    @Test fun restoredAndDirectErrorsAlwaysHaveAChinesePresentation() {
        val snapshots = listOf(
            Snapshot(environment = EnvironmentState.REPAIR, stage = "Ubuntu 环境已安装，健康状态待检查", error = "AccessDeniedException: component recovery failed"),
            Snapshot(update = UpdateState.RECOVERY_REQUIRED, error = "Interrupted update transaction"),
            Snapshot(service = ServiceState.INTERRUPTED, error = "Fatal: process exited",
                recovery = RuntimeRecoveryState(lastFailure = FailureKind.CRASHED_LATE)),
            Snapshot(error = "另一个安装操作正在进行，请稍后重试"),
            Snapshot(web = WebState.RENDER_ERROR, error = "Cookie 隔离失败")
        )
        snapshots.forEach { snapshot ->
            val shown = snapshot.displayFailure()
            assertNotNull(snapshot.error, shown)
            assertEquals(snapshot.error, shown!!.detail)
            if (snapshot.recovery.lastFailure == null) assertNull(shown.retry)
            assertFalse(shown.reason.contains(snapshot.error))
            assertTrue(shown.reason.any { it in '\u4e00'..'\u9fff' })
            assertNull(snapshot.copy(error = "", failure = null).displayFailure())
        }
    }

    @Test fun aNewDirectErrorCannotBeHiddenByAnOlderStructuredFailure() {
        val old = FailureCatalog.describe(FailureInput("download timeout", operation = "component-node"))
        val shown = Snapshot(failure = old, error = "Permission denied: new failure").displayFailure()!!
        assertEquals("Permission denied: new failure", shown.detail)
        assertEquals(FailureCause.PERMISSION, shown.cause)
    }

    @Test fun updateErrorsNeverExposeOfflineTimeoutOrHostExceptionsAsVisibleCopy() {
        listOf(
            "java.net.ConnectException: Network is unreachable",
            "java.net.SocketTimeoutException: Read timed out",
            "java.net.UnknownHostException: Unable to resolve host registry.example.invalid"
        ).forEach { raw ->
            val shown = Snapshot(update = UpdateState.CHECK_FAILED, stage = "官方版本获取失败，可重试", error = raw).displayFailure()!!
            assertEquals(FailureCause.NETWORK, shown.cause)
            val visible = listOf(shown.title, shown.reason, shown.primaryAction.label, shown.secondaryAction?.label.orEmpty()).joinToString()
            assertFalse(visible.contains(raw))
            assertFalse(Regex("[A-Za-z]{3,}").containsMatchIn(visible))
            assertTrue(shown.reason.contains("重试"))
            assertEquals(raw, shown.detail)
            val apk = com.tanuki.update.AppUpdateFailure.from(com.tanuki.update.AppUpdateState(
                phase = com.tanuki.update.AppUpdatePhase.FAILED, message = raw
            ))!!
            assertEquals(raw, apk.detail)
            assertFalse(Regex("[A-Za-z]{3,}").containsMatchIn(apk.reason))
            val onboarding = com.tanuki.onboarding.OnboardingPrepare.from(
                EnvironmentState.FAILED, "准备未完成", error = raw
            )
            assertEquals(raw, onboarding.detail)
            assertFalse(Regex("[A-Za-z]{3,}").containsMatchIn(onboarding.failure.orEmpty()))
        }
    }

    @Test fun explicitComponentOperationWinsAfterUpdateCheckFailure() {
        listOf(UpdateState.CHECK_FAILED, UpdateState.FAILED).forEach { staleUpdate ->
            listOf("node", "ubuntu").forEach { component ->
                val shown = FailureCatalog.describe(FailureInput(
                    message = "Connection reset", operation = "component-$component",
                    update = staleUpdate, stage = "旧 DSH 版本检查失败"
                ))
                assertEquals(FailureDomain.INSTALL, shown.domain)
                assertEquals(component, shown.componentId)
                assertEquals("${component.uppercase()}_INSTALL", shown.retryCommand)
            }
        }
    }

    @Test fun reinstallationKeepsForceCommandAndChosenVersionEvenWhenOldComponentRemains() {
        listOf(RuntimeComponent.NODE, RuntimeComponent.UBUNTU).forEach { component ->
            val request = FailureRetry.component(component, force = true, version = "selected-version")
            val failed = FailureCatalog.describe(FailureInput(
                message = "download timeout; old component retained",
                operation = "component-${component.name.lowercase()}", retry = request,
                update = UpdateState.CHECK_FAILED
            ))
            val record = Recorder()
            performFailureAction(failed, FailureActionKind.RETRY, record.callbacks(), {}, {})
            assertEquals(FailureRetry("${component.name}_REINSTALL", "selected-version"), record.retry)
            assertTrue(record.commands.isEmpty())
        }
    }

    @Test fun uninstallAndSwitchRetainOriginalCommandAndTarget() {
        listOf(
            Triple("component-uninstall-node", "NODE_UNINSTALL", null),
            Triple("component-uninstall-ubuntu", "UBUNTU_UNINSTALL", null),
            Triple("component-uninstall-dsh", "DSH_UNINSTALL", "1.2.3"),
            Triple("component-switch-dsh", "DSH_SWITCH", "1.2.4")
        ).forEach { (operation, command, version) ->
            val shown = FailureCatalog.describe(FailureInput(
                message = "operation failed", operation = operation,
                retry = FailureRetry(command, version)
            ))
            assertEquals(FailureRetry(command, version), shown.retry)
            val record = Recorder()
            performFailureAction(shown, FailureActionKind.RETRY, record.callbacks(), {}, {})
            assertEquals(FailureRetry(command, version), record.retry)
        }
    }

    @Test fun compatibilityFailureOpensVersionPickerAndDoesNotOfferDeadRetry() {
        val shown = FailureCatalog.describe(FailureInput("所选版本不兼容", operation = "component-node"))
        assertEquals(FailureActionKind.VIEW_VERSIONS, shown.primaryAction.kind)
        assertNotEquals(FailureActionKind.RETRY, shown.secondaryAction?.kind)
        var opened = false
        val record = Recorder()
        performFailureAction(shown, shown.primaryAction.kind, record.callbacks(), {}, { opened = true })
        assertTrue(opened)
        assertNull(record.retry)
        assertTrue(record.commands.isEmpty())
    }

    @Test fun firstVersionCheckFailureFetchesListInsteadOfOpeningEmptyPicker() {
        val shown = FailureCatalog.describe(FailureInput(
            "UnknownHostException: registry.example.invalid", operation = "dsh-check", update = UpdateState.CHECKING,
            componentId = "dsh"
        ))
        val record = Recorder()
        var opened = false
        performFailureAction(shown, shown.primaryAction.kind, record.callbacks(), {}, { opened = true }, versionsAvailable = false)
        assertEquals(1, record.checks)
        assertFalse(opened)
        // 同时兼容旧模型生成的“选择版本”动作，空列表时也必须先获取。
        performFailureAction(shown, FailureActionKind.VIEW_VERSIONS, record.callbacks(), {}, { opened = true }, versionsAvailable = false)
        assertEquals(2, record.checks)
        assertFalse(opened)
    }

    @Test fun availableVersionsOpenPickerWithoutStartingAnotherCheck() {
        val shown = FailureCatalog.describe(FailureInput("candidate failed", operation = "dsh-install", componentId = "dsh"))
        val record = Recorder()
        var opened = false
        performFailureAction(shown, FailureActionKind.VIEW_VERSIONS, record.callbacks(), {}, { opened = true }, versionsAvailable = true)
        assertTrue(opened)
        assertEquals(0, record.checks)
    }

    @Test fun legacyErrorWithoutReplayContextDoesNotOfferADeadRetry() {
        val shown = Snapshot(error = "Unknown runtime error").displayFailure()!!
        assertNull(shown.retry)
        assertNotEquals(FailureActionKind.RETRY, shown.primaryAction.kind)
        assertNotEquals(FailureActionKind.RETRY, shown.secondaryAction?.kind)
    }

    @Test fun storageActionInvokesProvidedNavigationInsteadOfAnEmptyDefault() {
        val shown = FailureCatalog.describe(FailureInput("ENOSPC", operation = "component-ubuntu"))
        var navigated = false
        performFailureAction(shown, FailureActionKind.OPEN_STORAGE, Recorder().callbacks(), { navigated = true }, {})
        assertTrue(navigated)
    }

    private class Recorder {
        var retry: FailureRetry? = null
        var checks = 0
        val commands = mutableListOf<String>()
        fun callbacks() = TanukiUiCallbacks(
            command = { commands += it }, install = {}, checkUpdates = { checks++ },
            importFiles = {}, measureStorage = {}, toggleLegacy = {}, toggleBlob = {},
            showNotices = {}, updateWebView = {}, webViewSettings = {}, appSettings = {},
            notifications = {}, connectDsh = {}, retryFailure = { retry = it }
        )
    }
}
