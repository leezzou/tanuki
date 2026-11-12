package com.tanuki.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tanuki.onboarding.*
import com.tanuki.runtime.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 真实引导控件与合成后端回调；不启动真实DSH，不读取账号或修改用户引导记录。 */
@RunWith(AndroidJUnit4::class)
class OnboardingStartEntryTest {
    @get:Rule val compose = createComposeRule()

    private class Fixture(initial: OnboardingStep) {
        val step = mutableStateOf(initial)
        val conversationReady = mutableStateOf(false)
        val snapshot = mutableStateOf(Snapshot(environment = EnvironmentState.READY,
            service = ServiceState.STOPPED, health = HealthState.HEALTHY))
        val components = ComponentInventory(true, true, true, ubuntuVersion = "24.04.5")
        val tools = RequiredToolsStatus(true, emptyList(), "24.04.5 synthetic", 1)
        val commands = mutableListOf<String>()
        var finished: OnboardingFinish? = null
        var generation = 0L
        val owner = Any()

        fun prepare() {
            assertEquals(LocalPreparationAction.START, localPreparationAction(snapshot.value, components, tools))
            val expected = generation
            withStartAdmission(owner, expected, { generation }, { false }) {
                commands += "START"
                generation++
                snapshot.value = snapshot.value.copy(service = ServiceState.RUNNING, webHealthy = true)
            }
        }
    }

    private fun render(initial: OnboardingStep): Fixture {
        val fixture = Fixture(initial)
        compose.setContent {
            TanukiTheme {
                OnboardingScreen(
                    presentation = OnboardingPresentation(true, fixture.step.value, OnboardingRecord(),
                        DeviceProfile(8L * 1024 * 1024 * 1024, 64L * 1024 * 1024 * 1024, listOf("arm64-v8a"), 34), false, null),
                    snapshot = fixture.snapshot.value,
                    conversationReady = fixture.conversationReady.value,
                    onContinue = { fixture.step.value.next?.let { fixture.step.value = it } },
                    onBack = { fixture.step.value = OnboardingStep.ENVIRONMENT }, onSkip = {},
                    onPrepare = fixture::prepare, onCancelPrepare = {}, onRefreshDevice = {}, onSelectInterface = {},
                    onComplete = { finish ->
                        assertTrue(fixture.conversationReady.value)
                        assertEquals(LocalPreparationAction.OPEN_CHAT,
                            localPreparationAction(fixture.snapshot.value, fixture.components, fixture.tools))
                        fixture.finished = finish
                    },
                )
            }
        }
        return fixture
    }

    @Test fun readyStoppedDonePageCanStartBeforeFirstMessage() {
        val fixture = render(OnboardingStep.DONE)
        compose.onNodeWithText("发第一条消息").assertIsNotEnabled()
        compose.onNodeWithText("继续准备或启动助手").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("发第一条消息").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { fixture.conversationReady.value = true }
        compose.onNodeWithText("发第一条消息").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf("START"), fixture.commands)
            assertEquals(OnboardingFinish.FIRST_MESSAGE, fixture.finished)
        }
    }

    @Test fun readyStoppedEnvironmentPageHasAnExecutableEntryAndCanFinishTheJourney() {
        val fixture = render(OnboardingStep.ENVIRONMENT)
        compose.onNodeWithText("继续准备或启动助手").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("继续").performClick()
        compose.onNodeWithText("继续").performClick()
        compose.onNodeWithText("发第一条消息").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { fixture.conversationReady.value = true }
        compose.onNodeWithText("发第一条消息").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(listOf("START"), fixture.commands)
            assertEquals(OnboardingFinish.FIRST_MESSAGE, fixture.finished)
        }
    }
}
