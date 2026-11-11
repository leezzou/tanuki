package com.tanuki

import com.tanuki.onboarding.OnboardingFailure
import com.tanuki.onboarding.OnboardingPermissionAction
import com.tanuki.onboarding.OnboardingPermissionNeed
import com.tanuki.onboarding.OnboardingPermissions
import com.tanuki.onboarding.OnboardingPrepare
import com.tanuki.onboarding.OnboardingPreparePhase
import com.tanuki.runtime.EnvironmentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** “一键准备”阶段映射、真实进度与失败原因回归。 */
class OnboardingPrepareTest {
    @Test fun downloadUsesRealByteFraction() {
        val state = OnboardingPrepare.from(
            environment = EnvironmentState.DOWNLOADING,
            stage = "Ubuntu 24.04.5 下载",
            receivedBytes = 50,
            totalBytes = 200,
        )
        assertEquals(OnboardingPreparePhase.DOWNLOAD, state.phase)
        assertEquals(0.25f, state.fraction)
        assertTrue(state.active)
    }

    @Test fun unknownDownloadTotalKeepsIndeterminateProgress() {
        val state = OnboardingPrepare.from(
            environment = EnvironmentState.DOWNLOADING,
            stage = "正在选择下载源",
            receivedBytes = 1024,
            totalBytes = -1,
        )
        assertNull(state.fraction)
        assertEquals(OnboardingPreparePhase.DOWNLOAD, state.phase)
    }

    @Test fun extractAndVerifyNeverInventPercentages() {
        val extract = OnboardingPrepare.from(
            environment = EnvironmentState.INSTALLING,
            stage = "正在解压 Ubuntu（已写入 128 MB）",
        )
        assertEquals(OnboardingPreparePhase.EXTRACT, extract.phase)
        assertNull(extract.fraction)
        assertTrue(extract.detail.contains("128 MB"))

        val verify = OnboardingPrepare.from(
            environment = EnvironmentState.VERIFYING,
            stage = "验证暂存 Ubuntu 启动能力",
        )
        assertEquals(OnboardingPreparePhase.VERIFY, verify.phase)
        assertNull(verify.fraction)
    }

    @Test fun aptPhaseUsesRealReportedPercentage() {
        val state = OnboardingPrepare.from(
            environment = EnvironmentState.INSTALLING,
            stage = "补齐基础组件：curl",
            phasePercent = 42,
        )
        assertEquals(OnboardingPreparePhase.INSTALL, state.phase)
        assertEquals(0.42f, state.fraction)
    }

    @Test fun readyEnvironmentIsComplete() {
        val state = OnboardingPrepare.from(
            environment = EnvironmentState.READY,
            stage = "初始化完成",
        )
        assertEquals(OnboardingPreparePhase.READY, state.phase)
        assertEquals(1f, state.fraction)
        assertFalse(state.active)
    }

    @Test fun failureIsTranslatedToChineseActionableReason() {
        val network = OnboardingPrepare.from(
            environment = EnvironmentState.FAILED,
            stage = "下载失败",
            error = "java.net.UnknownHostException: cdimage.ubuntu.com",
        )
        assertEquals(OnboardingPreparePhase.FAILED, network.phase)
        assertTrue(network.retryable)
        assertTrue(network.failure.orEmpty().contains("网络"))
        assertFalse(network.failure.orEmpty().contains("UnknownHostException"))

        val space = OnboardingPrepare.from(
            environment = EnvironmentState.FAILED,
            stage = "解包空间不足",
            error = "No space left on device",
        )
        assertTrue(space.failure.orEmpty().contains("存储空间不足"))

        val digest = OnboardingPrepare.from(
            environment = EnvironmentState.FAILED,
            stage = "校验失败",
            error = "SHA-256 mismatch",
        )
        assertTrue(digest.failure.orEmpty().contains("校验未通过"))
    }

    @Test fun cancellationCanRetryAndReuseDownloads() {
        val state = OnboardingPrepare.from(
            environment = EnvironmentState.CANCELLED,
            stage = "已取消",
        )
        assertEquals(OnboardingPreparePhase.CANCELLED, state.phase)
        assertTrue(state.retryable)
        assertTrue(state.detail.contains("复用"))
    }

    @Test fun unknownFailureKeepsRawTextOnlyInTechnicalDetail() {
        val raw = "java.io.IOException: unexpected runtime failure"
        val state = OnboardingPrepare.from(EnvironmentState.FAILED, "准备失败", error = raw)
        val reason = state.failure.orEmpty()
        assertTrue(reason.length < 260)
        assertTrue(reason.contains("重试"))
        assertFalse(reason.contains("IOException"))
        assertEquals(raw, state.detail)
    }

    @Test fun onlyPreparationRequestsNotificationOnAndroid13() {
        assertEquals(
            OnboardingPermissionNeed.NOTIFICATIONS,
            OnboardingPermissions.needed(OnboardingPermissionAction.PREPARE_ENVIRONMENT, 33, false, true),
        )
        assertEquals(
            OnboardingPermissionNeed.NONE,
            OnboardingPermissions.needed(OnboardingPermissionAction.PREPARE_ENVIRONMENT, 33, true, false),
        )
        assertEquals(
            OnboardingPermissionNeed.NONE,
            OnboardingPermissions.needed(OnboardingPermissionAction.PREPARE_ENVIRONMENT, 32, false, false),
        )
    }

    @Test fun welcomeAndRemoteEntriesDoNotPromptForPermissions() {
        for (sdk in listOf(26, 32, 33, 34)) {
            assertEquals(
                OnboardingPermissionNeed.NONE,
                OnboardingPermissions.needed(OnboardingPermissionAction.SEND_FIRST_MESSAGE, sdk, false, false),
            )
            assertEquals(
                OnboardingPermissionNeed.NONE,
                OnboardingPermissions.needed(OnboardingPermissionAction.CONNECT_REMOTE, sdk, false, false),
            )
        }
    }

    @Test fun storageIsOnlyRequestedWhenFilesAreActuallyOpened() {
        assertEquals(
            OnboardingPermissionNeed.STORAGE,
            OnboardingPermissions.needed(OnboardingPermissionAction.OPEN_FILES, 34, false, false),
        )
        assertEquals(
            OnboardingPermissionNeed.NONE,
            OnboardingPermissions.needed(OnboardingPermissionAction.OPEN_FILES, 34, false, true),
        )
    }
}
