package com.tanuki.update

import android.content.Context
import com.tanuki.BuildConfig
import com.tanuki.runtime.Paths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** 有界、只允许 HTTPS 固定主机的更新元数据读取；不经过 WebView 或 Cookie。 */
internal object UpdateCheckHttp {
    fun fetch(
        initial: String,
        allowedHosts: Set<String>,
        maxBytes: Int,
        followRedirects: Boolean,
        userAgent: String
    ): String {
        var url = URL(initial)
        val attempts = if (followRedirects) 6 else 1
        repeat(attempts) { redirect ->
            val uri = url.toURI()
            require(
                uri.scheme == "https" && uri.host in allowedHosts && uri.port in setOf(-1, 443) &&
                    uri.rawUserInfo == null && uri.rawFragment == null
            ) { "更新元数据地址不可信" }
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = BackgroundUpdateChecks.CONNECT_TIMEOUT_MS
                connection.readTimeout = BackgroundUpdateChecks.READ_TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("User-Agent", userAgent)
                val code = connection.responseCode
                if (followRedirects && code in setOf(301, 302, 303, 307, 308)) {
                    require(redirect < attempts - 1) { "更新元数据重定向过多" }
                    val location = connection.getHeaderField("Location") ?: error("更新元数据缺少跳转地址")
                    url = URL(url, location)
                    return@repeat
                }
                require(code == 200) { "更新元数据 HTTP $code" }
                val length = connection.contentLengthLong
                require(length <= 0L || length <= maxBytes) { "更新元数据超过大小限制" }
                return connection.inputStream.use { it.readLimited(maxBytes) }.toString(Charsets.UTF_8)
            } finally {
                connection.disconnect()
            }
        }
        error("更新元数据重定向过多")
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(65536)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            require(out.size() + read <= limit) { "更新元数据超过大小限制" }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}

/**
 * Tanuki APK 的后台检查入口。
 *
 * 只读取 GitHub Release 的 app-update.json 并写入 state/apk-update-check.json；
 * 不触碰 ApplicationUpdateManager 的下载/安装状态，也不自动下载或安装。
 */
class ApkUpdateCheckRepository(private val context: Context, private val paths: Paths) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private val mutable = MutableStateFlow(BackgroundUpdateChecks.decodeApk(paths.read(FILE_NAME)))
    val state: StateFlow<ApkUpdateCheckState> = mutable.asStateFlow()
    private var job: Job? = null

    /** 界面触发的非阻塞检查；已有请求进行中时直接复用，不排队第二次请求。 */
    fun startCheck(force: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch { checkNow(force) }
    }

    suspend fun checkNow(force: Boolean = false): ApkUpdateCheckState = gate.withLock {
        val now = System.currentTimeMillis()
        val previous = mutable.value
        if (!BackgroundUpdateChecks.shouldQuery(now, previous.lastCheckedAt, force)) return@withLock previous
        mutable.value = previous.copy(status = UpdateCheckStatus.CHECKING)
        val installedVersion = BuildConfig.VERSION_NAME
        val installedCode = BuildConfig.VERSION_CODE.toLong()
        val result = try {
            withContext(Dispatchers.IO) {
                val raw = UpdateCheckHttp.fetch(
                    initial = "https://github.com/${AppUpdatePolicy.REPOSITORY}/releases/latest/download/app-update.json",
                    allowedHosts = setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com"),
                    maxBytes = BackgroundUpdateChecks.MAX_APK_METADATA_BYTES,
                    followRedirects = true,
                    userAgent = "Tanuki-Update-Check/${BuildConfig.VERSION_NAME}"
                )
                BackgroundUpdateChecks.evaluateApk(
                    manifest = raw,
                    installedPackage = context.packageName,
                    installedVersion = installedVersion,
                    installedCode = installedCode,
                    now = System.currentTimeMillis(),
                    previous = previous
                )
            }
        } catch (cancelled: CancellationException) {
            mutable.value = previous
            throw cancelled
        } catch (error: Throwable) {
            BackgroundUpdateChecks.apkFailure(
                previous = previous,
                installedVersion = installedVersion,
                installedCode = installedCode,
                now = System.currentTimeMillis(),
                error = error.message ?: "GitHub 发布页不可用"
            )
        }
        mutable.value = result
        persist(result)
        result
    }

    /**
     * 手动检查已经拿到候选时同步后台快照，避免同一张卡片出现两套相反结论。
     * 只接受不早于现有后台快照的检查；开始时间更早的旧结果（Activity 回放或慢请求）被丢弃。
     */
    suspend fun recordManual(candidate: AppUpdateAsset?, startedAt: Long) = gate.withLock {
        val installedVersion = BuildConfig.VERSION_NAME
        val installedCode = BuildConfig.VERSION_CODE.toLong()
        val updated = BackgroundUpdateChecks.applyManualApk(
            candidateVersion = candidate?.versionName,
            candidateCode = candidate?.versionCode,
            startedAt = startedAt,
            previous = mutable.value,
            installedVersion = installedVersion,
            installedCode = installedCode
        )
        mutable.value = updated
        persist(updated)
    }

    /** 同一版本只允许后台通知一次；持久化后进程重启也不会重复提示。 */
    suspend fun claimNotification(version: String): Boolean = gate.withLock {
        val current = mutable.value
        if (!BackgroundUpdateChecks.shouldNotifyApk(current) || current.latestVersion != version) {
            return@withLock false
        }
        val updated = BackgroundUpdateChecks.markApkNotified(current, version)
        mutable.value = updated
        persist(updated)
        true
    }

    private fun persist(state: ApkUpdateCheckState) {
        runCatching { paths.atomic(FILE_NAME, BackgroundUpdateChecks.encodeApk(state)) }
    }

    companion object {
        const val FILE_NAME = "apk-update-check.json"
    }
}

/**
 * DSH 引擎的后台检查入口。
 *
 * 只读取 npmmirror（失败回退官方 npm）元数据并写入 state/dsh-update-check.json；
 * 不下载、不安装、不修改 engines 或 RuntimeManager 的候选版本。
 */
class DshUpdateCheckRepository(private val paths: Paths) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Mutex()
    private val mutable = MutableStateFlow(BackgroundUpdateChecks.decodeDsh(paths.read(FILE_NAME)))
    val state: StateFlow<DshUpdateCheckState> = mutable.asStateFlow()
    private var job: Job? = null

    fun startCheck(force: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch { checkNow(force) }
    }

    suspend fun checkNow(force: Boolean = false): DshUpdateCheckState = gate.withLock {
        val now = System.currentTimeMillis()
        val previous = mutable.value
        if (!BackgroundUpdateChecks.shouldQuery(now, previous.lastCheckedAt, force)) return@withLock previous
        mutable.value = previous.copy(status = UpdateCheckStatus.CHECKING)
        val installedVersion = installedVersion()
        val result = try {
            withContext(Dispatchers.IO) {
                BackgroundUpdateChecks.evaluateDsh(installedVersion, System.currentTimeMillis(), previous) { registry ->
                    UpdateCheckHttp.fetch(
                        initial = registry.url,
                        allowedHosts = setOf(URI(registry.url).host),
                        maxBytes = BackgroundUpdateChecks.MAX_DSH_METADATA_BYTES,
                        followRedirects = false,
                        userAgent = "Tanuki-DSH-Update-Check/${BuildConfig.VERSION_NAME}"
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            mutable.value = previous
            throw cancelled
        } catch (error: Throwable) {
            previous.copy(
                status = UpdateCheckStatus.FAILED,
                installedVersion = installedVersion,
                available = BackgroundUpdateChecks.isUpdateAvailable(installedVersion, previous.latestVersion),
                lastCheckedAt = System.currentTimeMillis(),
                lastErrorAt = System.currentTimeMillis(),
                lastError = (error.message ?: "DSH 版本检查失败").take(160)
            )
        }
        mutable.value = result
        persist(result)
        result
    }

    /** 手动刷新官方版本列表后同步后台快照；不发起额外请求，旧开始时间不覆盖新后台结果。 */
    suspend fun recordManualLatest(latest: String, startedAt: Long) {
        gate.withLock {
            val updated = BackgroundUpdateChecks.applyManualDsh(
                latest = latest,
                startedAt = startedAt,
                previous = mutable.value,
                installedVersion = installedVersion()
            )
            mutable.value = updated
            persist(updated)
        }
    }

    suspend fun claimNotification(version: String): Boolean = gate.withLock {
        val current = mutable.value
        if (!BackgroundUpdateChecks.shouldNotifyDsh(current) || current.latestVersion != version) {
            return@withLock false
        }
        val updated = BackgroundUpdateChecks.markDshNotified(current, version)
        mutable.value = updated
        persist(updated)
        true
    }

    private fun installedVersion(): String =
        paths.metadata().optString("dsh").takeIf(BackgroundUpdateChecks::isValidVersion).orEmpty()

    private fun persist(state: DshUpdateCheckState) {
        runCatching { paths.atomic(FILE_NAME, BackgroundUpdateChecks.encodeDsh(state)) }
    }

    companion object {
        const val FILE_NAME = "dsh-update-check.json"
    }
}
