package com.tanuki.runtime

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import java.io.File

enum class EnvironmentState { ABSENT, CHECKING, DOWNLOADING, INSTALLING, VERIFYING, READY, CANCELLED, FAILED, REPAIR }
enum class Confinement { UNKNOWN, FULL, PARTIAL, UNAVAILABLE }
enum class HealthState { UNKNOWN, CHECKING, HEALTHY, FAILED }
enum class ServiceState { STOPPED, STARTING, RUNNING, STOPPING, FAILED, INTERRUPTED }
enum class WebState { DISCONNECTED, CONNECTING, AUTHENTICATING, CONNECTED, AUTH_EXPIRED, INCOMPATIBLE, RENDER_ERROR }
enum class UpdateState { UNCHECKED, CHECKING, CHECK_FAILED, AVAILABLE, CURRENT, STAGING, TESTING, WAITING, BACKUP, SWITCHING, RESTORING, RESTORED_PENDING_CHECK, SUCCESS, FAILED, RECOVERY_REQUIRED }
data class RuntimeDownloadProgress(val component: String, val source: String, val received: Long, val total: Long, val bytesPerSecond: Long, val reused: Boolean = false)
data class RequiredToolsStatus(
    val checked: Boolean = false,
    val missing: List<String> = emptyList(),
    val ubuntuMarker: String = "",
    val checkedAt: Long = 0
) {
    val passed: Boolean get() = checked && missing.isEmpty()
}
data class Snapshot(
    val environment: EnvironmentState = EnvironmentState.ABSENT,
    val health: HealthState = HealthState.UNKNOWN,
    val confinement: Confinement = Confinement.UNKNOWN,
    val service: ServiceState = ServiceState.STOPPED,
    val web: WebState = WebState.DISCONNECTED,
    val webHealthy: Boolean = false,
    val update: UpdateState = UpdateState.UNCHECKED,
    val stage: String = "尚未初始化",
    val bytes: Long = 0, val total: Long = -1,
    val download: RuntimeDownloadProgress? = null,
    val error: String = "", val ubuntu: String = "未安装", val node: String = "未安装",
    val dsh: String = "未安装", val candidate: String = "", val events: List<String> = emptyList(),
    val phasePercent: Int? = null,
    val operation: String? = null,
    val timing: OperationTiming = OperationTiming(),
    val remote: RemoteHostStatus = RemoteHostStatus(),
    val recovery: RuntimeRecoveryState = RuntimeRecoveryState(),
    /** 最近一次失败的统一展示模型；原始异常仍在 error 字段，恢复后清空。 */
    val failure: AppFailure? = null,
    /** 仅当前操作持有；失败时复制到错误模型，结束后不污染下一次失败。 */
    val operationRetry: FailureRetry? = null
)
class Paths(context: Context) {
    val base = context.filesDir
    val runtime = File(base, "runtime").apply { mkdirs() }
    val linkStore = File(base, "link-store")
    val guestTmp = File(base, "guest-tmp")
    val prootTmp = File(base, "proot-tmp")
    val sharedMemory = File(base, "shared-memory")
    val root = File(runtime, "ubuntu")
    val stage = File(runtime, "ubuntu.staging")
    val node = File(runtime, "node")
    val pnpm = File(runtime, "pnpm")
    val nodeCache = File(runtime, "node-cache")
    val engines = File(base, "engines").apply { mkdirs() }
    val data = File(base, "userdata").apply { mkdirs() }
    val home = File(data, "home").apply { mkdirs() }
    val dshData = File(data, "dsh").apply { mkdirs() }
    // 安全模式使用独立的最小 home/DSH_HOME，不读取用户插件与配置；目录只在本模式内复用。
    val safeHome = File(runtime, "safe-home")
    val safeDshData = File(runtime, "safe-dsh")
    val workspaces = File(base, "workspaces").apply { mkdirs() }
    val downloads = File(base, "downloads").apply { mkdirs() }
    val npmArchives = File(downloads, "npm")
    val backups = File(base, "backups").apply { mkdirs() }
    val logs = File(base, "logs").apply { mkdirs() }
    val state = File(base, "state").apply { mkdirs() }
    fun atomic(name: String, text: String) {
        val f = AtomicFile(File(state, name)); val out = f.startWrite()
        try { out.write(text.toByteArray()); f.finishWrite(out) } catch (t: Throwable) { f.failWrite(out); throw t }
    }
    fun read(name: String): String? = runCatching { AtomicFile(File(state, name)).openRead().bufferedReader().use { it.readText() } }.getOrNull()

    /** 同时删除 AtomicFile 的备份，避免下次读取把已完成的组件事务恢复回来。 */
    fun deleteState(name: String) {
        runCatching { AtomicFile(File(state, name)).delete() }
    }
    fun metadata(): JSONObject = runCatching { JSONObject(read("installed.json") ?: "{}") }.getOrDefault(JSONObject())
}
