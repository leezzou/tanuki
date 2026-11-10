package com.tanuki.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.tanuki.TanukiApp
import com.tanuki.runtime.RecoveryMode
import com.tanuki.runtime.ServiceState
import com.tanuki.web.DshEndpoint
import com.tanuki.web.DshHttpAdminTransport
import com.tanuki.web.DshSessionAdminClient

/**
 * 原生会话管理入口。
 *
 * 独立 Activity 复用 `TanukiApp.runtime` 的同一个 DSH 实例与精确 origin，
 * 因此本地、远程 SSH 隧道、安全模式都读取同一运行状态；不重建 DSH、不改
 * 聊天页或官方 WebView。安全模式/试用模式下只读。
 */
class DshSessionManagerActivity : ComponentActivity() {
    private val runtime get() = (application as TanukiApp).runtime

    private val controller: DshSessionManagerController by lazy {
        DshSessionManagerController(
            source = DshSessionAdminClient(
                DshHttpAdminTransport {
                    val origin = runtime.origin
                    val launchUrl = runtime.authenticatedUrl
                    if (origin != null && launchUrl != null) DshEndpoint(origin, launchUrl) else null
                }
            ),
            readStore = DshPreferencesReadStore(this),
            pinStore = DshPreferencesPinStore(this),
            scope = lifecycleScope
        )
    }

    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val requestedSession = intent?.getStringExtra(EXTRA_SESSION_ID)
        val themeMode = TanukiThemePreference.load(this)
        setContent {
            TanukiTheme(themeMode) {
                val snapshot by runtime.state.collectAsState()
                // 远程 DSH 由 SSH 隧道提供 origin 时本地服务可以已停止，仍应可用。
                val running = snapshot.service == ServiceState.RUNNING || snapshot.remote.connected
                // 远程端点有自己的恢复状态；本地安全模式只约束本地会话。
                val readOnly = !snapshot.remote.connected && snapshot.recovery.mode != RecoveryMode.NORMAL
                // origin 是端点身份：远程断开回退本地时端口会变化，必须清空旧目录缓存。
                val endpoint = runtime.origin
                LaunchedEffect(running, readOnly, snapshot.remote.connected, endpoint, resumeTick) {
                    controller.setReadOnly(readOnly)
                    controller.onEndpointChanged(endpoint)
                    if (running) controller.refresh() else controller.clearForStopped()
                }
                LaunchedEffect(requestedSession, running) {
                    if (running && !requestedSession.isNullOrBlank()) controller.openSession(requestedSession)
                }
                DshSessionManagerPage(
                    controller = controller,
                    running = running,
                    remote = snapshot.remote.connected,
                    readOnly = readOnly,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        resumeTick++
    }

    override fun onStop() {
        // 页面不可见时丢弃内存中的 API Cookie；再次进入由当前 origin 重新认证。
        controller.close()
        super.onStop()
    }

    companion object {
        const val EXTRA_SESSION_ID = "com.tanuki.extra.NATIVE_SESSION_ID"

        fun intent(context: Context, sessionId: String? = null): Intent =
            Intent(context, DshSessionManagerActivity::class.java).apply {
                sessionId?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_SESSION_ID, it) }
            }
    }
}

/** 本机最后查看时间；只用于原生列表未读提示，不写回 DSH 数据。 */
private class DshPreferencesReadStore(context: Context) : DshReadStore {
    private val preferences = context.getSharedPreferences("native-session-read", Context.MODE_PRIVATE)

    override fun lastRead(sessionId: String): Long = preferences.getLong(key(sessionId), 0L)

    override fun markRead(sessionId: String, updatedAt: Long) {
        if (updatedAt <= 0L) return
        preferences.edit().putLong(key(sessionId), updatedAt).apply()
    }

    private fun key(sessionId: String): String = "session:$sessionId"
}

/** 本机置顶；只改变原生列表顺序，不调用官方写接口。 */
private class DshPreferencesPinStore(context: Context) : DshSessionPinStore {
    private val preferences = context.getSharedPreferences("native-session-pins", Context.MODE_PRIVATE)

    override fun pinnedIds(): Set<String> =
        preferences.getStringSet(KEY, emptySet()).orEmpty().toSet()

    override fun setPinned(sessionId: String, pinned: Boolean) {
        val next = pinnedIds().toMutableSet()
        if (pinned) next += sessionId else next -= sessionId
        preferences.edit().putStringSet(KEY, next).apply()
    }

    private companion object {
        const val KEY = "pinned"
    }
}
