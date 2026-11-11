package com.tanuki.update

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.content.FileProvider
import com.tanuki.BuildConfig
import com.tanuki.TanukiApp
import com.tanuki.download.Downloader
import com.tanuki.runtime.RuntimeService
import com.tanuki.runtime.ServiceState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** 此状态只描述 Tanuki APK，不与 Ubuntu/Node/DSH 的更新状态混用。 */
enum class AppUpdatePhase { IDLE, CHECKING, AVAILABLE, CURRENT, NO_RELEASE, DOWNLOADING, VERIFYING, READY, FAILED, CANCELLED }
data class AppUpdateState(val phase: AppUpdatePhase = AppUpdatePhase.IDLE,
    val message: String = "检查应用新版本，不上传运行环境或账号信息",
    val candidate: AppUpdateAsset? = null, val received: Long = 0, val total: Long = 0, val startedAt: Long = 0) {
    val busy: Boolean get() = phase in setOf(AppUpdatePhase.CHECKING, AppUpdatePhase.DOWNLOADING, AppUpdatePhase.VERIFYING)
}

class ApplicationUpdateManager(
    private val app: TanukiApp,
    /** 手动检查成功时通知后台快照；附带检查开始时间，避免旧结果覆盖较新的后台结果。 */
    private val onCheckCompleted: suspend (AppUpdateAsset?, Long) -> Unit = { _, _ -> }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutable = MutableStateFlow(AppUpdateState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    @Volatile private var readyFile: File? = null
    val hasActiveOperation: Boolean get() = job?.isActive == true || state.value.busy
    private val directory get() = AppUpdateFiles.directory(app.cacheDir)

    fun check() = operation(AppUpdateState(AppUpdatePhase.CHECKING, "正在检查应用新版本")) {
        val startedAt = System.currentTimeMillis()
        ApkInstallAccess.revoke(app)
        readyFile = null
        val installed = installedIdentity()
        val root = "https://github.com/${AppUpdatePolicy.REPOSITORY}"
        // 测试渠道包含公开预发布；正式渠道只跟随 GitHub 的 latest 稳定发布，均不调用 API。
        val sources: List<Pair<String, String?>> = if (app.packageName.endsWith(".workspacecheck") || BuildConfig.DEBUG) {
            AppReleaseManifest.releaseTags(text("$root/releases.atom")).map { "$root/releases/download/$it/app-update.json" to it }
        } else listOf("$root/releases/latest/download/app-update.json" to null)
        var supportedManifest = false
        var invalidManifest = false
        var best: AppUpdateAsset? = null
        for ((address, expectedTag) in sources) {
            currentCoroutineContext().ensureActive()
            var tag = expectedTag
            val raw = try { text(address) { tag = it } }
                catch (error: MetadataHttpException) { if (error.status == 404) continue else throw error }
            val candidate = try { AppReleaseManifest.selectDirect(JSONObject(raw), tag ?: error("稳定发布未返回明确的版本地址"), installed, Build.VERSION.SDK_INT) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { invalidManifest = true; continue }
            supportedManifest = true
            if (candidate != null && (best == null || candidate.versionCode > best.versionCode)) best = candidate
        }
        val result = when {
            best != null -> AppUpdateState(AppUpdatePhase.AVAILABLE, "发现新版本，可以下载更新", best)
            invalidManifest -> AppUpdateState(AppUpdatePhase.FAILED, "发布清单不完整或没有同包、同签名的兼容 APK；现有环境未改动")
            supportedManifest -> AppUpdateState(AppUpdatePhase.CURRENT, "没有适用于当前应用的更高版本")
            else -> AppUpdateState(AppUpdatePhase.NO_RELEASE, "暂时没有可用更新，请稍后再检查")
        }
        if (result.phase == AppUpdatePhase.AVAILABLE || result.phase == AppUpdatePhase.CURRENT) {
            try { onCheckCompleted(result.candidate, startedAt) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Throwable) { }
        }
        mutable.value = result
    }

    fun download() {
        val candidate = state.value.candidate ?: return
        operation(AppUpdateState(AppUpdatePhase.DOWNLOADING, "正在下载 Tanuki 更新", candidate, total = candidate.bytes)) {
            ApkInstallAccess.revoke(app)
            AppUpdatePolicy.requireCompatible(candidate, installedIdentity(), Build.VERSION.SDK_INT)
            check(directory.mkdirs() || directory.isDirectory) { "不能创建更新缓存目录" }
            directory.listFiles()?.filter { it.isFile }?.forEach { check(it.delete()) { "不能清理旧更新缓存" } }
            val target = File(directory, "update.apk")
            readyFile = null
            var lastNotice = 0L
            try {
                Downloader.applicationUpdates(progress = { done, total ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastNotice >= 100 || done == total) {
                        mutable.value = mutable.value.copy(received = done, total = total)
                        lastNotice = now
                    }
                }).fetch(candidate.url, target, candidate.sha256, candidate.bytes)
                mutable.value = mutable.value.copy(phase = AppUpdatePhase.VERIFYING, message = "正在检查更新文件")
                verify(target, candidate)
                readyFile = target
                mutable.value = AppUpdateState(AppUpdatePhase.READY, "更新已准备好，请点击“安装更新”并在系统中确认", candidate,
                    candidate.bytes, candidate.bytes)
            } catch (error: Throwable) { target.delete(); throw error }
        }
    }

    /** 校验完成不等于允许中断真实任务；下载可以后台进行，安装必须等环境空闲。 */
    fun installAllowed(): Boolean {
        val runtime = app.runtime
        return !runtime.hasActiveOperation && !runtime.hasLiveChild && !app.updates.hasActiveOperation &&
            runtime.fileTransfers.state.value.isEmpty() && runtime.state.value.service in
            setOf(ServiceState.STOPPED, ServiceState.FAILED, ServiceState.INTERRUPTED)
    }

    fun prepareInstall(onReady: (Intent) -> Unit) {
        if (state.value.phase != AppUpdatePhase.READY || !installAllowed()) {
            mutable.value = state.value.copy(message = "请先停止助手，等待运行环境和文件操作结束后再安装更新")
            return
        }
        val candidate = state.value.candidate ?: return
        val file = readyFile ?: return
        operation(AppUpdateState(AppUpdatePhase.VERIFYING, "安装前再次校验更新文件", candidate)) {
            verify(file, candidate)
            check(installAllowed()) { "运行环境已变为忙碌，请停止 DSH 后重试" }
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
            val installer = ApkInstallAccess.prepare(app, uri)
            withContext(Dispatchers.Main.immediate) {
                mutable.value = AppUpdateState(AppUpdatePhase.READY, "请在系统中确认安装；取消不会清除运行环境", candidate,
                    candidate.bytes, candidate.bytes)
                if (installAllowed()) onReady(installer) else ApkInstallAccess.revokeAsync(app)
            }
        }
    }

    fun installerFailed() {
        ApkInstallAccess.revokeAsync(app)
        mutable.value = state.value.copy(message = "无法打开安装器，请在系统设置中允许 Tanuki 安装未知应用后重试")
    }
    fun cancel() { if (hasActiveOperation) job?.cancel() }

    private fun operation(initial: AppUpdateState, work: suspend () -> Unit) {
        if (hasActiveOperation) return
        mutable.value = initial.copy(startedAt = SystemClock.elapsedRealtime())
        try {
            val intent = Intent(app, RuntimeService::class.java).setAction("APP_UPDATE")
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent) else app.startService(intent)
        } catch (_: Exception) {
            mutable.value = initial.copy(phase = AppUpdatePhase.FAILED, message = "更新未能开始，请保持应用在前台后重试")
            return
        }
        job = scope.launch {
            try { withContext(Dispatchers.IO) { withTimeout(if (initial.phase == AppUpdatePhase.CHECKING) 120_000L else 1_800_000L) { work() } } }
            catch (_: TimeoutCancellationException) { mutable.value = initial.copy(phase = AppUpdatePhase.FAILED, message = "网络或校验超时，可重试；现有应用与环境未改动") }
            catch (cancelled: CancellationException) { mutable.value = initial.copy(phase = AppUpdatePhase.CANCELLED, message = "已取消应用更新，现有环境未改动"); throw cancelled }
            catch (error: Exception) { mutable.value = initial.copy(phase = AppUpdatePhase.FAILED, message = error.message?.take(160) ?: "检查或下载失败，请重试") }
        }
    }

    private suspend fun verify(file: File, candidate: AppUpdateAsset) {
        currentCoroutineContext().ensureActive()
        check(file.isFile && file.canonicalFile.parentFile == directory.canonicalFile && file.length() == candidate.bytes) { "更新文件丢失或长度不符，请重新下载" }
        check(Downloader.hash(file) == candidate.sha256) { "更新文件 SHA-256 不符，请重新下载" }
        currentCoroutineContext().ensureActive()
        val archive = app.packageManager.getPackageArchiveInfo(file.absolutePath, signatureFlags()) ?: error("无法解析更新 APK")
        AppUpdatePolicy.requireArchive(candidate, installedIdentity(), identity(archive), Build.VERSION.SDK_INT)
    }

    private fun installedIdentity(): AppInstallIdentity = identity(app.packageManager.getPackageInfo(app.packageName, signatureFlags()))
    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION")
    private fun identity(info: PackageInfo): AppInstallIdentity {
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        val signers = signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
        return AppInstallIdentity(info.packageName, info.versionName.orEmpty(),
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),
            info.applicationInfo?.minSdkVersion ?: error("APK 缺少 Android 兼容信息"), signers)
    }

    private class MetadataHttpException(val status: Int) : Exception("GitHub 公开发布页暂不可访问（HTTP $status），可稍后重试")

    private suspend fun text(initial: String, onReleaseTag: ((String) -> Unit)? = null): String {
        var url = URL(initial)
        val hosts = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
        repeat(6) { redirects ->
            currentCoroutineContext().ensureActive()
            val uri = url.toURI()
            check(uri.scheme == "https" && uri.host in hosts && uri.port in setOf(-1, 443) && uri.rawUserInfo == null && uri.rawFragment == null) { "更新元数据跳转至不可信地址" }
            AppReleaseManifest.manifestTag(url.toString())?.let { onReleaseTag?.invoke(it) }
            val request = url.openConnection() as HttpURLConnection
            try {
                request.instanceFollowRedirects = false
                request.connectTimeout = 10_000; request.readTimeout = 25_000
                request.setRequestProperty("Accept", "application/atom+xml, application/json")
                request.setRequestProperty("Accept-Encoding", "identity")
                request.setRequestProperty("User-Agent", "Tanuki-Update/${BuildConfig.VERSION_NAME}")
                val code = request.responseCode
                if (code in setOf(301, 302, 303, 307, 308)) {
                    check(redirects < 5) { "更新元数据重定向过多" }
                    url = URL(url, request.getHeaderField("Location") ?: error("更新元数据缺少跳转地址"))
                } else {
                    if (code != 200) throw MetadataHttpException(code)
                    check(request.contentLengthLong <= 1_048_576L) { "更新元数据超过大小限制" }
                    val bytes = ByteArrayOutputStream()
                    request.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buffer); if (n < 0) break
                            check(bytes.size() + n <= 1_048_576) { "更新元数据超过大小限制" }
                            bytes.write(buffer, 0, n)
                        }
                    }
                    return bytes.toString("UTF-8")
                }
            } finally { request.disconnect() }
        }
        error("更新元数据重定向过多")
    }
}
