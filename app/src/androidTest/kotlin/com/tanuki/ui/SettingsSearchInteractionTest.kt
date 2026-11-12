package com.tanuki.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.dp
import com.tanuki.ui.design.TanukiDesignTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** 只用合成状态，不访问持久偏好、账号、运行环境或主Activity。 */
@RunWith(AndroidJUnit4::class)
class SettingsSearchInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun noResultsAndClearReturnToSearchHint() {
        var query by mutableStateOf("")
        compose.setContent {
            TanukiDesignTheme {
                SettingsSearch(query, { query = it }, {})
            }
        }
        compose.onNodeWithText("搜索设置").performTextInput("不存在的设置")
        compose.onNodeWithText("没有找到对应设置").assertIsDisplayed()
        compose.onNodeWithText("清除").performClick()
        compose.onNodeWithText("没有找到对应设置").assertDoesNotExist()
        compose.onNodeWithText("输入设置名称或用途，例如“夜间”“自动启动”。").assertIsDisplayed()
    }

    @Test fun restoreResultOnlyReportsTypedNavigation() {
        val selected = AtomicReference<SettingsTarget?>(null)
        compose.setContent {
            TanukiDesignTheme {
                SettingsSearch("恢复", {}, { selected.set(it) })
            }
        }
        assertNull(selected.get())
        compose.onNodeWithText("恢复应用数据").performClick()
        assertEquals(SettingsTarget.DATA_RESTORE, selected.get())
    }

    @Test fun selectingBelowFoldTargetScrollsToControlWithoutExecutingIt() {
        var requestedTarget by mutableStateOf<SettingsTarget?>(null)
        val actions = AtomicInteger()
        compose.setContent {
            TanukiDesignTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(1600.dp))
                    SettingsSearchAnchor(SettingsTarget.DATA_RESTORE, requestedTarget) {
                        TextButton(onClick = { actions.incrementAndGet() }) { Text("恢复入口（合成）") }
                    }
                }
            }
        }
        compose.onNodeWithText("恢复入口（合成）").assertIsNotDisplayed()
        compose.runOnIdle { requestedTarget = SettingsTarget.DATA_RESTORE }
        compose.onNodeWithText("恢复入口（合成）").assertIsDisplayed()
        assertEquals(0, actions.get())
    }

    @Test fun savingKeepsOriginalSelectionAndDisablesAnotherChoice() {
        val selections = AtomicInteger()
        compose.setContent {
            TanukiDesignTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    AppearanceSettingsContent(
                        AppearancePreferenceState(mode = AppearanceMode.LIGHT, loading = false, saving = true),
                        { selections.incrementAndGet() }, {}, {},
                    )
                }
            }
        }
        compose.onNodeWithText("浅色").assertIsSelected()
        compose.onNodeWithText("深色").assertIsNotEnabled()
        compose.onNodeWithText("正在保存外观设置…").assertIsDisplayed()
        assertEquals(0, selections.get())
    }

    @Test fun readFailureOffersRetryWithoutChangingPreference() {
        val retries = AtomicInteger()
        val selections = AtomicInteger()
        compose.setContent {
            TanukiDesignTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    AppearanceSettingsContent(
                        AppearancePreferenceState(loading = false, error = AppearanceSaveError.LOAD),
                        { selections.incrementAndGet() }, { retries.incrementAndGet() }, {},
                    )
                }
            }
        }
        compose.onNodeWithText("深色").assertIsNotEnabled()
        compose.onNodeWithText("重新读取").performClick()
        assertEquals(1, retries.get())
        assertEquals(0, selections.get())
    }
}
