package com.tanuki

import com.tanuki.onboarding.DeviceProfile
import com.tanuki.onboarding.OnboardingEnvironment
import com.tanuki.onboarding.RequirementStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 首次引导的环境检测门槛与建议文案回归。 */
class OnboardingEnvironmentTest {
    private val good = DeviceProfile(
        totalMemoryBytes = 6L * 1024 * 1024 * 1024,
        usableStorageBytes = 20L * 1024 * 1024 * 1024,
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        sdkInt = 34,
    )

    @Test fun roomyArm64DeviceCanContinue() {
        val report = OnboardingEnvironment.evaluate(good)
        assertTrue(report.ready)
        assertFalse(report.blocking)
        assertTrue(report.canContinue)
        assertEquals(RequirementStatus.OK, report.requirement("storage")?.status)
        assertEquals(RequirementStatus.OK, report.requirement("memory")?.status)
        assertEquals(RequirementStatus.OK, report.requirement("architecture")?.status)
    }

    @Test fun storageBelowMinimumBlocksWithActionableAdvice() {
        val report = OnboardingEnvironment.evaluate(good.copy(usableStorageBytes = 900L * 1024 * 1024))
        val storage = report.requirement("storage")!!
        assertEquals(RequirementStatus.BLOCKING, storage.status)
        assertTrue(storage.advice.orEmpty().contains("清理"))
        assertTrue(storage.advice.orEmpty().contains("1.5 GB"))
        assertFalse(report.canContinue)
    }

    @Test fun storageBetweenMinimumAndRecommendedOnlyWarns() {
        val report = OnboardingEnvironment.evaluate(good.copy(usableStorageBytes = 2L * 1024 * 1024 * 1024))
        assertEquals(RequirementStatus.WARNING, report.requirement("storage")?.status)
        assertTrue(report.hasWarning)
        assertFalse(report.blocking)
        assertTrue(report.canContinue)
        assertNotNull(report.requirement("storage")?.advice)
    }

    @Test fun lowMemoryBlocksAndSuggestsRemote() {
        val report = OnboardingEnvironment.evaluate(good.copy(totalMemoryBytes = 1536L * 1024 * 1024))
        val memory = report.requirement("memory")!!
        assertEquals(RequirementStatus.BLOCKING, memory.status)
        assertTrue(memory.advice.orEmpty().contains("远程电脑"))
        assertFalse(report.canContinue)
    }

    @Test fun memoryBetweenMinimumAndRecommendedOnlyWarns() {
        val report = OnboardingEnvironment.evaluate(good.copy(totalMemoryBytes = 3L * 1024 * 1024 * 1024))
        assertEquals(RequirementStatus.WARNING, report.requirement("memory")?.status)
        assertTrue(report.canContinue)
    }

    @Test fun nonArm64DeviceBlocksLocalRuntime() {
        val report = OnboardingEnvironment.evaluate(
            good.copy(supportedAbis = listOf("x86_64"))
        )
        val arch = report.requirement("architecture")!!
        assertEquals(RequirementStatus.BLOCKING, arch.status)
        assertEquals("x86_64", arch.actual)
        assertTrue(arch.advice.orEmpty().contains("远程电脑"))
        assertFalse(report.canContinue)
    }

    @Test fun oldAndroidVersionBlocks() {
        val report = OnboardingEnvironment.evaluate(good.copy(sdkInt = 25))
        assertEquals(RequirementStatus.BLOCKING, report.requirement("system")?.status)
        assertFalse(report.canContinue)
    }

    @Test fun byteFormattingKeepsSubGigabyteValuesReadable() {
        assertEquals("512 MB", OnboardingEnvironment.formatBytes(512L * 1024 * 1024))
        assertEquals("1.5 GB", OnboardingEnvironment.formatBytes(1536L * 1024 * 1024))
        assertEquals("4.0 GB", OnboardingEnvironment.formatBytes(4L * 1024 * 1024 * 1024))
        assertEquals("未知", OnboardingEnvironment.formatBytes(-1))
    }
}
