package com.tanuki.web

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * 把 DSH 官方的 `$events` 流接到原生通知策略。
 *
 * 使用 WebView 官方 DocumentStartScript + WebMessageListener，只允许当前精确 origin。
 * 页面没有 JavaScript Bridge 方法可调用，原生也不向页面暴露读取能力；消息只包含
 * 会话/事件标识和布尔状态。
 */
class DshChatBridge(private val onSignal: (ChatSignal) -> Unit) {
    private var attachedOrigin: String? = null
    private var script: ScriptHandler? = null

    val supported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    /** 在每次加载 DSH 文档前安装；origin 变化时替换旧监听。 */
    fun install(view: WebView, origin: String): Boolean {
        if (!supported || origin.isBlank()) return false
        if (attachedOrigin == origin && script != null) return true
        detach(view)
        return runCatching {
            WebViewCompat.addWebMessageListener(view, DshChatProtocol.JS_OBJECT, setOf(origin)) {
                    _, message, sourceOrigin, isMainFrame, _ ->
                // 只接受主框架和当前精确 origin；其他来源或子框架的消息直接丢弃。
                if (isMainFrame && sourceOrigin.toString().trimEnd('/') == origin) {
                    DshChatProtocol.parse(message.data)?.let(onSignal)
                }
            }
            script = WebViewCompat.addDocumentStartJavaScript(view, DshChatProtocol.SCRIPT, setOf(origin))
            attachedOrigin = origin
            true
        }.getOrElse {
            detach(view)
            false
        }
    }

    fun detach(view: WebView) {
        runCatching { script?.remove() }
        script = null
        attachedOrigin = null
        runCatching { WebViewCompat.removeWebMessageListener(view, DshChatProtocol.JS_OBJECT) }
    }

    fun dispose() {
        runCatching { script?.remove() }
        script = null
        attachedOrigin = null
    }
}
