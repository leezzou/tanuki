package com.tanuki

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.ui.design.TanukiBottomSheet
import com.tanuki.ui.design.TanukiDesignTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模态弹层的焦点隔离回归。
 *
 * 只使用合成输入，不联网、不启动真实任务。先让底层输入框获得焦点并接收一个
 * 字符，再打开弹层，发送字符与 Tab/Enter；底层文本必须保持不变。返回键关闭
 * 弹层后，焦点应回到 Activity 窗口。
 */
class TanukiBottomSheetFocusTest {

    @Test
    fun modalSheetIsolatesFocusedInputAndRestoresFocus() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".workspacecheck"))
        val activity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        ) as MainActivity
        val underlying = mutableStateOf("")
        val sheetValue = mutableStateOf("")
        val sheetOpen = mutableStateOf(false)
        val underlyingFocused = mutableStateOf(false)
        val requester = FocusRequester()
        try {
            instrumentation.runOnMainSync {
                activity.setContent {
                    TanukiDesignTheme {
                        Box(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxSize()) {
                                BasicTextField(
                                    value = underlying.value,
                                    onValueChange = { underlying.value = it },
                                    singleLine = true,
                                    modifier = Modifier
                                        .focusRequester(requester)
                                        .onFocusChanged { underlyingFocused.value = it.isFocused },
                                )
                            }
                            TanukiBottomSheet(
                                visible = sheetOpen.value,
                                onDismissRequest = { sheetOpen.value = false },
                                title = "焦点隔离测试",
                            ) {
                                BasicTextField(
                                    value = sheetValue.value,
                                    onValueChange = { sheetValue.value = it },
                                    singleLine = true,
                                )
                            }
                        }
                    }
                }
            }
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync { requester.requestFocus() }
            instrumentation.waitForIdleSync()
            assertTrue("底层输入框应先获得焦点", underlyingFocused.value)

            typeKey(instrumentation, KeyEvent.KEYCODE_A)
            instrumentation.waitForIdleSync()
            assertEquals("底层输入框应接收焦点输入", "A", underlying.value)

            instrumentation.runOnMainSync {
                underlying.value = ""
                sheetOpen.value = true
            }
            awaitWindowFocus(activity, expected = false)

            typeKey(instrumentation, KeyEvent.KEYCODE_B)
            typeKey(instrumentation, KeyEvent.KEYCODE_TAB)
            typeKey(instrumentation, KeyEvent.KEYCODE_ENTER)
            instrumentation.waitForIdleSync()
            assertEquals("弹层可见时底层输入框不能被按键修改", "", underlying.value)

            instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
            instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            instrumentation.waitForIdleSync()
            awaitWindowFocus(activity, expected = true)
            assertFalse("返回键应关闭弹层", sheetOpen.value)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun typeKey(instrumentation: android.app.Instrumentation, keyCode: Int) {
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        instrumentation.sendKeySync(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun awaitWindowFocus(activity: MainActivity, expected: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (activity.window.decorView.hasWindowFocus() == expected) return
            SystemClock.sleep(100)
        }
        assertEquals(
            "窗口焦点未达到预期：$expected",
            expected,
            activity.window.decorView.hasWindowFocus(),
        )
    }
}
