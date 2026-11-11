package com.tanuki

import com.tanuki.runtime.AppFailure
import com.tanuki.runtime.EnvironmentState
import com.tanuki.runtime.FailureActionKind
import com.tanuki.runtime.FailureCatalog
import com.tanuki.runtime.FailureCause
import com.tanuki.runtime.FailureDomain
import com.tanuki.runtime.FailureInput
import com.tanuki.runtime.FailureKind
import com.tanuki.runtime.RecoveryMode
import com.tanuki.runtime.RuntimeRecoveryState
import com.tanuki.runtime.ServiceState
import com.tanuki.runtime.Snapshot
import com.tanuki.runtime.UpdateState
import com.tanuki.runtime.afterDismiss
import com.tanuki.runtime.visibleIn
import com.tanuki.update.AppUpdateFailure
import com.tanuki.update.AppUpdatePhase
import com.tanuki.update.AppUpdateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 统一错误出口的 JVM 回归：分类映射、主行动选择、人话文案与恢复后清除。
 * 这些用例不依赖 Android，避免把设备结果当作分类逻辑的证明。
 */
class FailureCatalogTest {

    @Test fun mapsInstallNetworkFailureToRetryAndSourceSwitch() {
        val failure = FailureCatalog.describe(
            FailureInput(
                message = "下载中断（已重试 2 次）：Connection reset",
                operation = "component-ubuntu",
                stage = "下载 Ubuntu 24.04",
                environment = EnvironmentState.DOWNLOADING,
                occurredAtMillis = 1_000L
            )
        )
        assertEquals(FailureDomain.INSTALL, failure.domain)
        assertEquals(FailureCause.NETWORK, failure.cause)
        assertEquals("安装没有完成", failure.title)
        assertEquals(FailureActionKind.RETRY, failure.primaryAction.kind)
        assertEquals(FailureActionKind.SWITCH_SOURCE, failure.secondaryAction?.kind)
        assertEquals("ubuntu", failure.componentId)
        assertEquals("UBUNTU_INSTALL", failure.retryCommand)
    }

    @Test fun mapsDiskPermissionChecksumAndExtractToDifferentActions() {
        val disk = FailureCatalog.describe(
            FailureInput(message = "ENOSPC: no space left on device", operation = "component-node")
        )
        assertEquals(FailureCause.DISK, disk.cause)
        assertEquals(FailureActionKind.CLEAN_CACHE, disk.primaryAction.kind)
        assertEquals(FailureActionKind.OPEN_STORAGE, disk.secondaryAction?.kind)

        val permission = FailureCatalog.describe(
            FailureInput(message = "EACCES: permission denied", operation = "component-ubuntu")
        )
        assertEquals(FailureCause.PERMISSION, permission.cause)
        assertEquals(FailureActionKind.OPEN_SETTINGS, permission.primaryAction.kind)

        val checksum = FailureCatalog.describe(
            FailureInput(message = "下载内容 SHA-256 校验失败", operation = "component-node")
        )
        assertEquals(FailureCause.CHECKSUM, checksum.cause)
        assertEquals(FailureActionKind.RETRY, checksum.primaryAction.kind)
        assertEquals(FailureActionKind.SWITCH_SOURCE, checksum.secondaryAction?.kind)

        val extract = FailureCatalog.describe(
            FailureInput(message = "解压失败：gzip: invalid header", operation = "component-node")
        )
        assertEquals(FailureCause.EXTRACT, extract.cause)
        assertEquals(FailureActionKind.RETRY, extract.primaryAction.kind)
    }

    @Test fun runtimeFailureUsesRecoveryModeForPrimaryAction() {
        val startup = FailureCatalog.fromRuntimeFailure(
            FailureKind.STARTUP_FAILED,
            RuntimeRecoveryState(mode = RecoveryMode.NORMAL),
            "启动未就绪，已停止自动重启",
            2_000L
        )
        assertEquals(FailureDomain.RUNTIME, startup.domain)
        assertEquals(FailureCause.STARTUP, startup.cause)
        assertEquals(FailureActionKind.RESTART, startup.primaryAction.kind)
        assertEquals("START", startup.retryCommand)

        val noSpace = FailureCatalog.fromRuntimeFailure(
            FailureKind.NO_SPACE,
            RuntimeRecoveryState(),
            "输出或磁盘空间不足",
            2_000L
        )
        assertEquals(FailureCause.DISK, noSpace.cause)
        assertEquals(FailureActionKind.CLEAN_CACHE, noSpace.primaryAction.kind)

        val safe = FailureCatalog.fromRuntimeFailure(
            FailureKind.CRASHED_EARLY,
            RuntimeRecoveryState(mode = RecoveryMode.SAFE),
            "安全模式启动失败",
            2_000L
        )
        assertEquals(FailureActionKind.TRY_NORMAL, safe.primaryAction.kind)

        val probation = FailureCatalog.fromRuntimeFailure(
            FailureKind.CRASHED_EARLY,
            RuntimeRecoveryState(mode = RecoveryMode.PROBATION),
            "试用正常模式未通过",
            2_000L
        )
        assertEquals(FailureActionKind.SAFE_MODE, probation.primaryAction.kind)
    }

    @Test fun dshUpdateFailurePrefersVersionPickerAndAppUpdatePrefersRetry() {
        val dsh = FailureCatalog.describe(
            FailureInput(
                message = "网络连接超时",
                operation = "dsh-install",
                update = UpdateState.STAGING,
                componentId = "dsh"
            )
        )
        assertEquals(FailureDomain.UPDATE, dsh.domain)
        assertEquals(FailureCause.NETWORK, dsh.cause)
        assertEquals(FailureActionKind.VIEW_VERSIONS, dsh.primaryAction.kind)
        assertEquals(FailureActionKind.CLEAN_CACHE, dsh.secondaryAction?.kind)

        val app = AppUpdateFailure.from(
            AppUpdateState(
                phase = AppUpdatePhase.FAILED,
                message = "网络或校验超时，可重试；现有应用与环境未改动",
                startedAt = 5_000L
            )
        )
        assertNotNull(app)
        assertEquals(FailureDomain.UPDATE, app!!.domain)
        assertEquals("app", app.componentId)
        assertEquals(FailureActionKind.RETRY, app.primaryAction.kind)
    }

    @Test fun userFacingCopyNeverExposesInternalImplementationTerms() {
        val forbidden = listOf(
            "interop", "proot", "transport", "binder", "namespace",
            "workspace-write", "loopback", "webview", "sha-256", "enospc",
            "eacces", "npm", "http", "elf"
        )
        val samples = listOf(
            FailureCatalog.describe(FailureInput(message = "Connection reset", operation = "component-ubuntu")),
            FailureCatalog.describe(FailureInput(message = "ENOSPC: no space left on device", operation = "component-node")),
            FailureCatalog.describe(FailureInput(message = "EACCES: permission denied", operation = "component-ubuntu")),
            FailureCatalog.describe(FailureInput(message = "SHA-256 校验失败", operation = "component-node")),
            FailureCatalog.describe(FailureInput(message = "解压失败：gzip invalid header", operation = "component-node")),
            FailureCatalog.describe(FailureInput(message = "所选版本不兼容", operation = "component-node")),
            FailureCatalog.describe(FailureInput(message = "另一个操作正在进行，请稍后重试", operation = "initialize")),
            FailureCatalog.fromRuntimeFailure(FailureKind.LAUNCH_FAILED, RuntimeRecoveryState(), "启动命令失败", 1_000L),
            FailureCatalog.fromRuntimeFailure(FailureKind.STARTUP_FAILED, RuntimeRecoveryState(), "启动未就绪", 1_000L),
            FailureCatalog.fromRuntimeFailure(FailureKind.CRASHED_EARLY, RuntimeRecoveryState(), "早期崩溃", 1_000L),
            FailureCatalog.fromRuntimeFailure(FailureKind.CRASHED_LATE, RuntimeRecoveryState(), "晚崩溃", 1_000L),
            FailureCatalog.fromRuntimeFailure(FailureKind.KILLED, RuntimeRecoveryState(), "进程被终止", 1_000L),
            FailureCatalog.fromRuntimeFailure(FailureKind.NO_SPACE, RuntimeRecoveryState(), "空间不足", 1_000L),
            AppUpdateFailure.from(AppUpdateState(AppUpdatePhase.FAILED, "网络或校验超时，可重试", startedAt = 1_000L))!!
        )
        val visibleCopy = samples.flatMap { failure ->
            listOf(failure.title, failure.reason, failure.primaryAction.label, failure.secondaryAction?.label.orEmpty())
        }.map { it.lowercase() }
        forbidden.forEach { term ->
            assertFalse("用户文案不应出现内部术语：$term", visibleCopy.any { it.contains(term) })
        }
    }

    @Test fun recoveredStateHidesOldFailureWithoutDeletingItFromState() {
        val runtime = FailureCatalog.fromRuntimeFailure(
            FailureKind.STARTUP_FAILED,
            RuntimeRecoveryState(),
            "启动未就绪",
            1_000L
        )
        assertNull(runtime.visibleIn(Snapshot(service = ServiceState.RUNNING)))
        assertNotNull(runtime.visibleIn(Snapshot(service = ServiceState.FAILED, error = "启动未就绪")))

        val install = FailureCatalog.describe(
            FailureInput(message = "网络连接中断", operation = "component-ubuntu")
        )
        assertNull(install.visibleIn(Snapshot(environment = EnvironmentState.READY)))
        assertNotNull(install.visibleIn(Snapshot(environment = EnvironmentState.FAILED, error = "网络连接中断")))

        val update = FailureCatalog.describe(
            FailureInput(
                message = "网络连接中断",
                operation = "dsh-install",
                update = UpdateState.STAGING,
                componentId = "dsh"
            )
        )
        assertNull(update.visibleIn(Snapshot(update = UpdateState.SUCCESS)))
        assertNotNull(update.visibleIn(Snapshot(update = UpdateState.FAILED, error = "网络连接中断")))

        assertNull(
            AppUpdateFailure.from(
                AppUpdateState(phase = AppUpdatePhase.CURRENT, message = "已是最新版本")
            )
        )
    }

    @Test fun dismissingOneFailureDoesNotHideANewerOne() {
        val failure = FailureCatalog.fromRuntimeFailure(
            FailureKind.CRASHED_LATE,
            RuntimeRecoveryState(),
            "服务稳定运行后崩溃",
            1_000L
        )
        assertNull(failure.afterDismiss(failure.stableKey))
        val newer: AppFailure = failure.copy(occurredAtMillis = 2_000L)
        assertNotNull(newer.afterDismiss(failure.stableKey))
    }

    @Test fun copyTextCarriesTimeAndTechnicalDetailForFeedback() {
        val failure = FailureCatalog.describe(
            FailureInput(
                message = "下载内容 SHA-256 校验失败",
                operation = "component-node",
                occurredAtMillis = 1_700_000_000_000L,
                attempt = 2
            )
        )
        val text = failure.copyText("0.1.12")
        assertTrue(text.contains("安装没有完成"))
        assertTrue(text.contains("应用版本：0.1.12"))
        assertTrue(text.contains("已尝试：2 次"))
        assertTrue(text.contains("技术详情"))
        assertTrue(text.contains("SHA-256"))
    }
}
