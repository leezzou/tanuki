package com.tanuki

import android.app.Application
import com.tanuki.power.DeviceConditionMonitor
import com.tanuki.runtime.RuntimeManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class TanukiApp : Application() {
    lateinit var runtime: RuntimeManager
        private set
    /** 设备温控/省电状态；应用启动时开始采样，进程重启后先恢复上次状态。 */
    val deviceConditions by lazy { DeviceConditionMonitor(this, runtime.paths) }
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
    /** DSH 会话通知：页面事件只做最小化分类，正文不含会话内容。 */
    val chatNotifications by lazy {
        com.tanuki.notify.ChatNotificationCenter(this) { runtime.state.value.dsh }
    }
    /** 聊天页与会话管理器共享的会话列表失效广播；只传变更信号，不复制会话数据。 */
    val dshSessions by lazy { com.tanuki.ui.DshSessionSync() }
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
        // 恢复可能递归清理中断组件；后台持独占锁完成，首帧不等待磁盘树遍历。
        runtime = RuntimeManager(this, backgroundRecovery = true)
        // 先绑定恢复回调再开始采样：进程重启后若设备已恢复，会立即补跑被延后的后台检查。
        deviceConditions.onBackgroundWorkResumed = { checks ->
            com.tanuki.update.UpdateCheckScheduler.resumeDeferred(this, checks)
        }
        deviceConditions.start()
        // 独立更新/恢复任务有自己的 job 与状态；提前启动必须把它们计入忙碌判定。
        runtime.registerBusySignal { updates.hasActiveOperation || appUpdates.hasActiveOperation }
        // 先在系统设置中建立渠道，用户才能在权限/渠道被关闭时找到入口。
        chatNotifications.ensureChannel()
        // 安装、切换或恢复 DSH 后只重算本地快照，不联网，也不覆盖已知远端 latest。
        runtime.scope.launch {
            runtime.state.map { it.dsh }.distinctUntilChanged().collect { version ->
                dshUpdateChecks.syncInstalledVersion(version)
            }
        }
        // 两个唯一周期任务由 WorkManager 持久化；UPDATE 保证重复启动不会堆积同名任务。
        com.tanuki.update.UpdateCheckScheduler.schedule(this)
        // 安装器可能跨进程预读APK；恢复有期限的只读授权，不恢复任何安装动作。
        com.tanuki.update.ApkInstallAccess.restore(this)
    }
}
