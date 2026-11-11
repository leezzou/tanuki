package com.tanuki.ui

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.ui.design.TanukiDesignTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 真实挂载 Material 窗口并注入系统触摸；只用合成内容，不启动 MainActivity/DSH。 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
class TanukiModalSheetInteractionTest {
    // 测试组合固定正常时长，避免设备的动画设置把“退出中触摸”退化为瞬切。
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float = 1f
    })

    private class Fixture {
        var value by mutableStateOf<String?>("第一份内容")
        val mounted = AtomicInteger()
        val backgroundClicks = AtomicInteger()
        val handle = AtomicReference(Offset.Zero)
        val background = AtomicReference(Offset.Zero)
    }

    private fun mount(): Fixture {
        check(InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".workspacecheck"))
        val fixture = Fixture()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TanukiDesignTheme(reducedMotion = false) {
                val handleHalfHeight = with(LocalDensity.current) { 24.dp.toPx() }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    TextButton(
                        onClick = { fixture.backgroundClicks.incrementAndGet() },
                        modifier = Modifier.padding(top = 64.dp).onGloballyPositioned {
                            fixture.background.set(it.positionOnScreen() + Offset(it.size.width / 2f, it.size.height / 2f))
                        },
                    ) { Text("下层操作") }
                    TanukiModalSheet(fixture.value, onDismissRequest = { fixture.value = null }) { shown ->
                        DisposableEffect(Unit) {
                            fixture.mounted.incrementAndGet()
                            onDispose { fixture.mounted.decrementAndGet() }
                        }
                        Box(Modifier.fillMaxWidth().height(240.dp).onGloballyPositioned {
                            // 默认系统拖拽柄占 48dp；内容顶边上方 24dp 即柄的实际触摸中心。
                            fixture.handle.set(it.positionOnScreen() + Offset(it.size.width / 2f, -handleHalfHeight))
                        }) { Text(shown) }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onNodeWithText("第一份内容").assertIsDisplayed()
        assertEquals(1, fixture.mounted.get())
        return fixture
    }

    private fun beginClose(fixture: Fixture) {
        compose.runOnIdle { fixture.value = null }
        // 第一帧应用业务关闭，第二帧启动系统 hide；之后按住柄必须不能取消退出。
        repeat(2) { compose.mainClock.advanceTimeByFrame() }
        compose.waitForIdle()
        assertEquals("必须在内容仍实际挂载时触摸，而不是等动画结束", 1, fixture.mounted.get())
    }

    private fun touch(action: Int, point: Offset, downTime: Long): Boolean {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, point.x, point.y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        return try {
            InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true)
        } finally {
            event.recycle()
        }
    }

    private fun assertBackgroundCanReceiveRealTouch(fixture: Fixture) {
        val point = fixture.background.get()
        val time = SystemClock.uptimeMillis()
        assertTrue(touch(MotionEvent.ACTION_DOWN, point, time))
        assertTrue(touch(MotionEvent.ACTION_UP, point, time))
        compose.waitForIdle()
        assertEquals("退出后的窗口不能继续拦截下层页面", 1, fixture.backgroundClicks.get())
    }

    @Test fun touchingSystemHandleDuringCloseDisposesWindowAndRestoresBackgroundInput() {
        val fixture = mount()
        beginClose(fixture)
        val point = fixture.handle.get()
        val downTime = SystemClock.uptimeMillis()
        assertTrue(touch(MotionEvent.ACTION_DOWN, point, downTime))
        try {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertEquals("拖拽柄不能取消正在正常进行的 hide", 1, fixture.mounted.get())
            compose.mainClock.advanceTimeBy(2_000)
            compose.waitForIdle()
            assertEquals("按住系统拖拽柄时，旧内容和窗口也必须完成卸载", 0, fixture.mounted.get())
        } finally {
            // 原窗口此时应已消失，系统可能已取消该触摸序列，不要求 UP 仍被接收。
            touch(MotionEvent.ACTION_UP, point, downTime)
        }
        assertBackgroundCanReceiveRealTouch(fixture)
    }

    @Test fun rapidReopenSurvivesOldExitCleanupAndCanCloseAgain() {
        val fixture = mount()
        beginClose(fixture)
        compose.runOnIdle { fixture.value = "重新打开的内容" }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.onNodeWithText("重新打开的内容").assertIsDisplayed()
        assertEquals("旧 hide 的 finally 不能卸载重开的内容", 1, fixture.mounted.get())
        beginClose(fixture)
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals(0, fixture.mounted.get())
        assertBackgroundCanReceiveRealTouch(fixture)
    }
}
