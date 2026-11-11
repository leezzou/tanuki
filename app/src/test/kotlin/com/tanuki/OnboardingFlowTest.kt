package com.tanuki

import com.tanuki.onboarding.OnboardingBannerKind
import com.tanuki.onboarding.OnboardingGate
import com.tanuki.onboarding.OnboardingLaunch
import com.tanuki.onboarding.OnboardingMachine
import com.tanuki.onboarding.OnboardingRecord
import com.tanuki.onboarding.OnboardingStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 首次启动引导的状态、版本门控与提示条回归。 */
class OnboardingFlowTest {
    private val version = "0.1.12"

    @Test fun firstLaunchStartsAtWelcomeAndRecordsVersion() {
        val record = OnboardingRecord()
        assertEquals(OnboardingLaunch.FIRST_RUN, OnboardingGate.decide(record, version))

        val state = OnboardingMachine.start(record, version)
        assertTrue(state.open)
        assertEquals(OnboardingStep.WELCOME, state.step)
        assertEquals(version, state.record.lastSeenVersion)
        assertTrue(state.record.started)
    }

    @Test fun upgradedInstallIsMarkedCompleteWithoutAutoGuide() {
        val record = OnboardingMachine.completedForExistingInstall(version)
        assertTrue(record.finished)
        assertTrue(record.started)
        assertEquals(OnboardingLaunch.NONE, OnboardingGate.decide(record, version))
        assertNull(OnboardingMachine.banner(record, version))
    }

    @Test fun completedRecordNeverAutoRepeatsTheWholeGuide() {
        var state = OnboardingMachine.start(OnboardingRecord(), version)
        repeat(OnboardingStep.entries.size) { state = OnboardingMachine.advance(state, version) }

        assertFalse(state.open)
        assertTrue(state.record.finished)
        assertEquals(version, state.record.completedVersion)
        assertEquals(OnboardingLaunch.NONE, OnboardingGate.decide(state.record, version))
        assertNull(OnboardingMachine.banner(state.record, version))
    }

    @Test fun majorUpgradeOnlyShowsOneDismissibleNotice() {
        val completed = OnboardingMachine.complete(
            OnboardingMachine.start(OnboardingRecord(), version),
            "0.1.12",
        ).record

        assertEquals(OnboardingLaunch.UPGRADE_NOTICE, OnboardingGate.decide(completed, "0.2.0"))
        val banner = OnboardingMachine.banner(completed, "0.2.0")
        assertEquals(OnboardingBannerKind.UPGRADE, banner?.kind)
        assertNull(banner?.step)

        val dismissed = OnboardingMachine.dismissUpgradeNotice(completed, "0.2.0")
        assertNull(OnboardingMachine.banner(dismissed, "0.2.0"))
        assertEquals(OnboardingLaunch.NONE, OnboardingGate.decide(dismissed, "0.2.0"))
    }

    @Test fun patchUpgradeDoesNotShowNotice() {
        val completed = OnboardingMachine.complete(OnboardingMachine.start(OnboardingRecord(), "0.1.12"), "0.1.12").record
        assertEquals(OnboardingLaunch.NONE, OnboardingGate.decide(completed, "0.1.13"))
        assertNull(OnboardingMachine.banner(completed, "0.1.13"))
    }

    @Test fun malformedVersionDoesNotRepeatUpgradeNotice() {
        val completed = OnboardingMachine.complete(OnboardingMachine.start(OnboardingRecord(), version), version).record
        assertEquals(OnboardingLaunch.NONE, OnboardingGate.decide(completed, "dev-build"))
        assertNull(OnboardingMachine.banner(completed, "dev-build"))
    }

    @Test fun skipLeavesResumeBannerAtFirstIncompleteStep() {
        var state = OnboardingMachine.start(OnboardingRecord(), version)
        state = OnboardingMachine.advance(state, version) // 欢迎完成，进入环境
        state = OnboardingMachine.skip(state, version)

        assertFalse(state.open)
        assertTrue(state.record.skipped)
        assertEquals(OnboardingStep.ENVIRONMENT, state.record.firstIncomplete)
        assertEquals(OnboardingLaunch.BANNER, OnboardingGate.decide(state.record, version))

        val banner = OnboardingMachine.banner(state.record, version)
        assertEquals(OnboardingBannerKind.RESUME, banner?.kind)
        assertEquals(OnboardingStep.ENVIRONMENT, banner?.step)
        assertEquals(1, banner?.completedSteps)
        assertEquals(OnboardingStep.entries.size, banner?.totalSteps)
    }

    @Test fun partialCompletionResumesAtFirstIncompleteStep() {
        var state = OnboardingMachine.start(OnboardingRecord(), version)
        state = OnboardingMachine.advance(state, version)
        state = OnboardingMachine.advance(state, version) // 环境完成，界面未完成

        assertEquals(OnboardingStep.INTERFACE, state.record.firstIncomplete)
        assertEquals(OnboardingLaunch.RESUME, OnboardingGate.decide(state.record, version))

        val resumed = OnboardingMachine.start(state.record, version)
        assertEquals(OnboardingStep.INTERFACE, resumed.step)
        assertTrue(resumed.open)
        assertTrue(resumed.record.completedSteps.containsAll(
            listOf(OnboardingStep.WELCOME, OnboardingStep.ENVIRONMENT)
        ))
    }

    @Test fun completingGuideClearsSkipAndBanner() {
        var state = OnboardingMachine.start(OnboardingRecord(), version)
        state = OnboardingMachine.skip(state, version)
        assertTrue(state.record.skipped)

        state = OnboardingMachine.start(state.record, version)
        repeat(OnboardingStep.entries.size) { state = OnboardingMachine.advance(state, version) }

        assertTrue(state.record.finished)
        assertFalse(state.record.skipped)
        assertNull(state.record.skippedAt)
        assertNull(OnboardingMachine.banner(state.record, version))
    }

    @Test fun dismissingResumeBannerKeepsProgressForSettingsEntry() {
        var state = OnboardingMachine.start(OnboardingRecord(), version)
        state = OnboardingMachine.advance(state, version)
        state = OnboardingMachine.skip(state, version)

        val dismissed = OnboardingMachine.dismissBanner(state.record)
        assertNull(OnboardingMachine.banner(dismissed, version))
        assertEquals(OnboardingStep.ENVIRONMENT, dismissed.firstIncomplete)

        // 从设置重新打开会恢复提示条状态，但不清空已完成步骤。
        val reopened = OnboardingMachine.start(dismissed, version)
        assertEquals(OnboardingStep.ENVIRONMENT, reopened.step)
        assertFalse(reopened.record.bannerDismissed)
        assertEquals(1, reopened.record.completedSteps.size)
    }

    @Test fun backFromWelcomeActsAsSkipInsteadOfTrappingUser() {
        val state = OnboardingMachine.start(OnboardingRecord(), version)
        val back = OnboardingMachine.back(state, version)
        assertFalse(back.open)
        assertTrue(back.record.skipped)
    }

    @Test fun backFromLaterStepReturnsToPreviousStep() {
        var state = OnboardingMachine.start(OnboardingRecord(), version)
        state = OnboardingMachine.advance(state, version)
        state = OnboardingMachine.back(state, version)
        assertEquals(OnboardingStep.WELCOME, state.step)
        assertTrue(state.open)
    }

    @Test fun finishedRecordReopensForReviewWithoutLosingCompletion() {
        val completed = OnboardingMachine.complete(OnboardingMachine.start(OnboardingRecord(), version), version).record
        val review = OnboardingMachine.start(completed, version)
        assertTrue(review.open)
        assertEquals(OnboardingStep.WELCOME, review.step)
        assertTrue(review.record.finished)
    }
}
