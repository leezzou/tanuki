package com.tanuki

import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.browser.AgentBrowserController
import com.tanuki.browser.InteractionInputConnection
import org.json.JSONTokener
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** 使用输入法线程和本地合成表单验证，不访问真实登录页或账号。 */
class BrowserInputThreadTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun ui(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun <T> worker(block: () -> T): T {
        val task = FutureTask(block)
        val thread = Thread(task, "InputConnectionHandlerThread-test").apply { isDaemon = true; start() }
        return try { task.get(5, TimeUnit.SECONDS) } finally { thread.join(1000) }
    }
    private val callbacks: List<(InputConnection) -> Boolean> = listOf(
        { it.commitText("synthetic", 1) },
        { it.setComposingText("拼音", 1) },
        { it.deleteSurroundingText(1, 0) },
        { it.deleteSurroundingTextInCodePoints(1, 0) },
        { it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)) },
        { it.performEditorAction(EditorInfo.IME_ACTION_DONE) },
    )
    private fun delegate(called: () -> Boolean): InputConnection {
        lateinit var view: View
        ui { view = View(instrumentation.targetContext) }
        return object : BaseInputConnection(view, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int) = called()
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int) = called()
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int) = called()
            override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int) = called()
            override fun sendKeyEvent(event: KeyEvent?) = called()
            override fun performEditorAction(editorAction: Int) = called()
        }
    }

    @Test fun allInputCallbacksCheckOnMainAndDelegateOnOriginalThread() {
        val checks = AtomicInteger()
        val forwards = AtomicInteger()
        val caller = AtomicReference<Thread>()
        val connection = InteractionInputConnection(delegate {
            assertSame(caller.get(), Thread.currentThread())
            forwards.incrementAndGet(); true
        }) {
            assertSame(Looper.getMainLooper(), Looper.myLooper())
            checks.incrementAndGet(); true
        }
        worker {
            caller.set(Thread.currentThread())
            callbacks.forEach { assertTrue(it(connection)) }
        }
        assertEquals(6, checks.get()); assertEquals(6, forwards.get())
    }

    @Test fun deniedInputNeverReachesDelegate() {
        val connection = InteractionInputConnection(delegate { fail("禁止输入必须阻止下传"); true }) {
            assertSame(Looper.getMainLooper(), Looper.myLooper()); false
        }
        worker { callbacks.forEach { assertFalse(it(connection)) } }
    }

    @Test fun mainThreadInputRunsInlineAndPreservesDelegateResult() {
        val calls = AtomicInteger()
        val connection = InteractionInputConnection(delegate { calls.incrementAndGet(); false }) { true }
        ui { callbacks.forEach { assertFalse(it(connection)) } }
        assertEquals(6, calls.get())
    }

    @Test fun busyMainThreadRejectsInputAndCancelsQueuedDecision() {
        val checks = AtomicInteger()
        val connection = InteractionInputConnection(delegate { fail("超时输入不得下传"); true }) {
            checks.incrementAndGet(); true
        }
        // 故意占用主线程直到后台调用超时，随后必须移除排队的授权判断。
        ui { assertFalse(worker { connection.commitText("blocked", 1) }) }
        instrumentation.waitForIdleSync()
        assertEquals(0, checks.get())
    }

    @Test fun interruptedWaitRejectsInputWithoutLateTakeover() {
        val checks = AtomicInteger()
        val connection = InteractionInputConnection(delegate { fail("中断输入不得下传"); true }) {
            checks.incrementAndGet(); true
        }
        ui {
            worker {
                Thread.currentThread().interrupt()
                assertFalse(connection.commitText("blocked", 1))
                assertTrue(Thread.currentThread().isInterrupted)
            }
        }
        instrumentation.waitForIdleSync()
        assertEquals(0, checks.get())
    }

    private fun js(view: WebView, source: String): Any? {
        val done = CountDownLatch(1); var result: Any? = null
        ui { view.evaluateJavascript(source) { result = JSONTokener(it).nextValue(); done.countDown() } }
        assertTrue(done.await(5, TimeUnit.SECONDS)); return result
    }
    private fun await(message: String, condition: () -> Boolean) {
        repeat(80) { if (condition()) return; SystemClock.sleep(100) }
        assertTrue(message, condition())
    }

    @Test fun realWebViewInputConnectionTypesAfterTakeoverAndBlocksAfterClose() {
        val target = instrumentation.targetContext
        check(target.packageName.endsWith(".workspacecheck"))
        val scratch = File(target.cacheDir, "input-thread-${System.nanoTime()}").apply { check(mkdirs()) }
        val context = object : ContextWrapper(target) { override fun getFilesDir() = scratch }
        val activity = instrumentation.startActivitySync(Intent(target, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var browser: AgentBrowserController
        lateinit var view: WebView
        var created = false
        val imeThread = HandlerThread("InputConnectionHandlerThread-fixture").apply { start() }
        val ime = Handler(imeThread.looper)
        fun onIme(action: () -> Boolean): Boolean {
            val task = FutureTask(action); assertTrue(ime.post(task)); return task.get(5, TimeUnit.SECONDS)
        }
        BrowserFixtureServer().use { server ->
            try {
                ui {
                    browser = AgentBrowserController(context); created = true
                    val host = FrameLayout(activity)
                    activity.setContentView(host); browser.attachBackgroundHost(host)
                    browser.setVisible(true); browser.navigate(server.base)
                    view = browser.webView
                }
                await("合成表单尚未加载") { js(view, "!!document.querySelector('#draft')") == true }
                ui { view.requestFocus() }
                js(view, "document.querySelector('#draft').focus();true")
                await("输入框未获得焦点") { js(view, "document.activeElement.id") == "draft" }
                lateinit var connection: InputConnection
                ui { connection = requireNotNull(view.onCreateInputConnection(EditorInfo())) }
                ui { browser.activateDefaultControl() }
                assertNotNull(browser.state.value.authorizedClient)
                // 授权期间从真实输入连接触发暂停；返回前状态必须已提交。
                assertFalse(onIme { connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)) })
                assertTrue(browser.state.value.takeoverRequested)
                assertNull(browser.state.value.authorizedClient)
                assertFalse(onIme { connection.commitText("blocked", 1) })
                assertEquals("", js(view, "document.querySelector('#draft').value"))
                ui { browser.resolveTakeover(true) }
                assertTrue(onIme { connection.setComposingText("syn", 1) })
                assertTrue(onIme { connection.commitText("synthetic", 1) })
                await("真实输入连接未写入页面") { js(view, "document.querySelector('#draft').value") == "synthetic" }
                assertEquals(30, browser.state.value.idleReturnSeconds)
                assertTrue(onIme { connection.deleteSurroundingText(1, 0) })
                await("退格未生效") { js(view, "document.querySelector('#draft').value") == "syntheti" }
                ui { browser.takeControl() }
                assertFalse(browser.library.state.value.aiControlEnabled)
                assertTrue(onIme { connection.commitText("c", 1) })
                await("显式撤销后仍应允许用户输入") { js(view, "document.querySelector('#draft').value") == "synthetic" }
                assertNull(browser.state.value.authorizedClient)
                ui { browser.close(); created = false }
                assertFalse(onIme { connection.commitText("after-close", 1) })
            } finally {
                ui { if (created) browser.close(); activity.finish() }
                imeThread.quitSafely(); imeThread.join(2000)
                scratch.deleteRecursively()
            }
        }
    }
}
