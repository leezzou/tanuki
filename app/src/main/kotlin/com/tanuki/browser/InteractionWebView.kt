package com.tanuki.browser

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.webkit.WebView
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** 软键盘常不产生 KeyEvent；在原生输入连接上记录真实输入，不能靠网页脚本延长授权计时。 */
internal class InteractionWebView(context: Context, private val input: () -> Boolean) : WebView(context) {
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val delegate = super.onCreateInputConnection(outAttrs) ?: return null
        return InteractionInputConnection(delegate, input)
    }
}

/** 只把权限判断与计时切回主线程；WebView 的输入连接继续在其原有调用线程执行。 */
internal class InteractionInputConnection(delegate: InputConnection, private val input: () -> Boolean) :
    InputConnectionWrapper(delegate, false) {
    private val main = Handler(Looper.getMainLooper())

    private fun allowed(): Boolean {
        if (Looper.myLooper() == main.looper) return input()
        val decision = FutureTask { input() }
        if (!main.post(decision)) return false
        return try {
            // 主线程繁忙时拒绝本次输入，不能阻塞输入法或让尚未检查权限的输入先行。
            decision.get(500, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            false
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        } finally {
            // 超时或中断后，尚未执行的判断不能迟到触发接管；不打断主线程。
            decision.cancel(false)
            main.removeCallbacks(decision)
        }
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
        allowed() && super.commitText(text, newCursorPosition)
    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean =
        allowed() && super.setComposingText(text, newCursorPosition)
    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean =
        allowed() && super.deleteSurroundingText(beforeLength, afterLength)
    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
        allowed() && super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
    override fun sendKeyEvent(event: KeyEvent?): Boolean = allowed() && super.sendKeyEvent(event)
    override fun performEditorAction(editorAction: Int): Boolean = allowed() && super.performEditorAction(editorAction)
}
