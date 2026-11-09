package com.tanuki

import android.app.Application
import com.tanuki.runtime.RuntimeManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TanukiApp : Application() {
    lateinit var runtime: RuntimeManager
        private set
    val updates by lazy {
        com.tanuki.update.UpdateManager(runtime) { latest, startedAt ->
            dshUpdateChecks.recordManualLatest(latest, startedAt)
        }
    }
    val appUpdates by lazy {
        com.tanuki.update.ApplicationUpdateManager(this) { candidate, startedAt ->
            apkUpdateChecks.recordManual(candidate, startedAt)
        }
    }
    /** Tanuki APK 的后台检查状态，独立于 ApplicationUpdateManager 的下载/安装流程。 */
    val apkUpdateChecks by lazy { com.tanuki.update.ApkUpdateCheckRepository(this, runtime.paths) }
    /** DSH 引擎的后台检查状态，独立于 UpdateManager 的版本列表与安装事务。 */
    val dshUpdateChecks by lazy { com.tanuki.update.DshUpdateCheckRepository(runtime.paths) }
    var browserController: com.tanuki.browser.AgentBrowserController? = null
        private set
    fun acquireBrowser(): com.tanuki.browser.AgentBrowserController {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        return browserController?.takeUnless { it.state.value.closed } ?: com.tanuki.browser.AgentBrowserController(
            this, com.tanuki.browser.BrowserLibrary(this, runtime.fileTransfers)) { setOfNotNull(runtime.origin) }.also { controller ->
            browserController = controller
            controller.onBackgroundNeeded = { startBrowserBackground() }
            controller.activateDefaultControl()
        }
    }
    fun showBrowserOverlay() {
        runCatching {
            startForegroundService(android.content.Intent(this, com.tanuki.browser.BrowserService::class.java)
                .setAction(com.tanuki.browser.BrowserService.SHOW_OVERLAY))
        }.onFailure { browserController?.notify("系统悬浮窗无法启动，请保持应用在前台后重试") }
    }
    fun hideBrowserOverlay() {
        if (browserController?.state?.value?.overlayFrame != null) runCatching {
            startService(android.content.Intent(this, com.tanuki.browser.BrowserService::class.java)
                .setAction(com.tanuki.browser.BrowserService.HIDE_OVERLAY))
        }
    }
    fun startBrowserBackground() {
        runCatching {
            startForegroundService(android.content.Intent(this, com.tanuki.browser.BrowserService::class.java))
        }.onFailure {
            browserController?.setBackgroundEnabled(false)
            android.widget.Toast.makeText(this, "后台浏览器无法启动，AI 授权已撤销；请保持应用在前台后重试", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    override fun onCreate() {
        super.onCreate()
        // Must precede EVERY WebView, including DSH. Otherwise capturePicture can have
        // full document dimensions but blank pixels outside the human window's viewport.
        android.webkit.WebView.enableSlowWholeDocumentDraw()
        cacheDir.listFiles()?.filter { it.isFile && it.name.startsWith("tanuki-blob-") }?.forEach { it.delete() }
        runtime = RuntimeManager(this)
        // 安装、切换或恢复 DSH 后只重算本地快照，不联网，也不覆盖已知远端 latest。
        runtime.scope.launch {
            runtime.state.map { it.dsh }.distinctUntilChanged().collect {
                dshUpdateChecks.refreshInstalled()
            }
        }
        // 两个唯一周期任务由 WorkManager 持久化；UPDATE 保证重复启动不会堆积同名任务。
        com.tanuki.update.UpdateCheckScheduler.schedule(this)
        // 安装器可能跨进程预读APK；恢复有期限的只读授权，不恢复任何安装动作。
        com.tanuki.update.ApkInstallAccess.restore(this)
    }
}
