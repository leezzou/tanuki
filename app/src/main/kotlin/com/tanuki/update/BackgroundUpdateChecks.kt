package com.tanuki.update

import org.json.JSONObject
import java.util.concurrent.CancellationException

/** 后台检查动作的结果；它与 APK 下载、DSH 安装等既有更新状态完全分离。 */
enum class UpdateCheckStatus { NEVER, CHECKING, SUCCEEDED, FAILED }

/** Tanuki APK 的后台检查快照，只描述 GitHub Release，不读取 DSH 的 npm 元数据。 */
data class ApkUpdateCheckState(
    val status: UpdateCheckStatus = UpdateCheckStatus.NEVER,
    val installedVersion: String = "",
    val installedCode: Long = 0L,
    val latestVersion: String = "",
    val latestCode: Long = 0L,
    val available: Boolean = false,
    val lastCheckedAt: Long = 0L,
    val lastErrorAt: Long = 0L,
    val lastError: String = "",
    val notifiedVersion: String = ""
) {
    val checking: Boolean get() = status == UpdateCheckStatus.CHECKING

    /** 界面只展示结论，不展示可能包含网络细节的 lastError。 */
    fun badgeText(): String? =
        if (available && latestVersion.isNotBlank()) "有更新：$latestVersion" else null

    fun statusText(): String = when (status) {
        UpdateCheckStatus.NEVER -> "尚未后台检查"
        UpdateCheckStatus.CHECKING -> "正在后台检查"
        UpdateCheckStatus.SUCCEEDED -> badgeText() ?: "已是最新版本"
        UpdateCheckStatus.FAILED -> "上次后台检查未成功"
    }
}

/** DSH 引擎的后台检查快照，只描述 npm 元数据，不回写 RuntimeManager 的 update 状态。 */
data class DshUpdateCheckState(
    val status: UpdateCheckStatus = UpdateCheckStatus.NEVER,
    val installedVersion: String = "",
    val latestVersion: String = "",
    val available: Boolean = false,
    val lastCheckedAt: Long = 0L,
    val lastErrorAt: Long = 0L,
    val lastError: String = "",
    val source: String = "",
    val notifiedVersion: String = ""
) {
    val checking: Boolean get() = status == UpdateCheckStatus.CHECKING

    fun badgeText(): String? =
        if (available && latestVersion.isNotBlank()) "有更新：$latestVersion" else null

    fun statusText(): String = when (status) {
        UpdateCheckStatus.NEVER -> "尚未后台检查"
        UpdateCheckStatus.CHECKING -> "正在后台检查"
        UpdateCheckStatus.SUCCEEDED -> badgeText() ?: "已是最新版本"
        UpdateCheckStatus.FAILED -> "上次后台检查未成功"
    }
}

/** DSH 元数据源顺序：镜像优先，失败或结构不完整时回退官方。 */
enum class DshRegistry(val label: String, val url: String) {
    MIRROR("npmmirror 镜像", "https://registry.npmmirror.com/@deepseek-ai%2Fdsh"),
    OFFICIAL("npm 官方", "https://registry.npmjs.org/@deepseek-ai%2Fdsh")
}

/** Worker 只消费这个纯配置，测试无需启动 WorkManager 即可核对间隔和联网约束。 */
data class BackgroundUpdateScheduleSpec(
    val apkUniqueName: String,
    val apkIntervalMinutes: Long,
    val dshUniqueName: String,
    val dshIntervalMinutes: Long,
    val requiresNetwork: Boolean
)

/**
 * 后台更新检查的纯 Kotlin 策略。
 *
 * 这里不引用 Android、WorkManager 或网络实现；Worker 与仓库只负责取字节和持久化，
 * 版本判断、去重、镜像回退与状态编解码都在本对象内完成，便于 JVM 回归。
 */
object BackgroundUpdateChecks {
    const val APK_UNIQUE_WORK_NAME = "tanuki-apk-update-check"
    const val DSH_UNIQUE_WORK_NAME = "dsh-engine-update-check"
    const val APK_INTERVAL_MINUTES = 30L
    const val DSH_INTERVAL_MINUTES = 24L * 60L

    /** 单次网络请求的连接/读取上限，落在任务要求的 10–15 秒范围内。 */
    const val CONNECT_TIMEOUT_MS = 12_000
    const val READ_TIMEOUT_MS = 12_000

    /** 手动入口与后台任务共享的最小查询间隔，避免同时点击或调度造成重复请求。 */
    const val MIN_QUERY_INTERVAL_MS = 60_000L

    const val MAX_APK_METADATA_BYTES = 1_048_576
    const val MAX_DSH_METADATA_BYTES = 8 * 1024 * 1024

    val schedule = BackgroundUpdateScheduleSpec(
        apkUniqueName = APK_UNIQUE_WORK_NAME,
        apkIntervalMinutes = APK_INTERVAL_MINUTES,
        dshUniqueName = DSH_UNIQUE_WORK_NAME,
        dshIntervalMinutes = DSH_INTERVAL_MINUTES,
        requiresNetwork = true
    )

    private val VERSION = Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?")

    fun isValidVersion(version: String): Boolean = VERSION.matches(version)

    fun isUpdateAvailable(installed: String, latest: String): Boolean =
        isValidVersion(installed) && isValidVersion(latest) && VersionOrder.compare(latest, installed) > 0

    /** 只有仍在最小间隔之外才发请求；force 供用户明确点击时使用。 */
    fun shouldQuery(now: Long, lastCheckedAt: Long, force: Boolean = false): Boolean =
        force || lastCheckedAt <= 0L || now - lastCheckedAt >= MIN_QUERY_INTERVAL_MS

    /**
     * 解析 Release 清单并选出当前包名下的最高版本。
     *
     * 这里只做“是否有更新”的判断；下载与安装仍由 ApplicationUpdateManager 校验
     * 包名、签名、大小与 SHA-256，后台检查不会触发任何下载或安装。
     */
    fun evaluateApk(
        manifest: String?,
        installedPackage: String,
        installedVersion: String,
        installedCode: Long,
        now: Long,
        previous: ApkUpdateCheckState = ApkUpdateCheckState()
    ): ApkUpdateCheckState {
        val json = runCatching { JSONObject(manifest ?: error("更新清单为空")) }
            .getOrElse { return apkFailure(previous, installedVersion, installedCode, now, "更新清单格式无效") }
        if (json.optInt("schemaVersion", 0) != 1) {
            return apkFailure(previous, installedVersion, installedCode, now, "更新清单版本不支持")
        }
        val packages = json.optJSONArray("packages")
            ?: return apkFailure(previous, installedVersion, installedCode, now, "更新清单缺少 packages")
        if (packages.length() !in 1..8) {
            return apkFailure(previous, installedVersion, installedCode, now, "更新清单条目数量无效")
        }
        var latestVersion = installedVersion
        var latestCode = installedCode
        for (index in 0 until packages.length()) {
            val entry = packages.optJSONObject(index) ?: continue
            if (entry.optString("packageName") != installedPackage) continue
            val version = entry.optString("versionName")
            val code = entry.optLong("versionCode", -1L)
            if (!isValidVersion(version) || code <= 0L) continue
            if (code > latestCode) {
                latestCode = code
                latestVersion = version
            }
        }
        return previous.copy(
            status = UpdateCheckStatus.SUCCEEDED,
            installedVersion = installedVersion,
            installedCode = installedCode,
            latestVersion = latestVersion,
            latestCode = latestCode,
            available = latestCode > installedCode,
            lastCheckedAt = now,
            lastErrorAt = 0L,
            lastError = ""
        )
    }

    /** 失败只记录时间和简短原因，并保留最近一次成功结果供界面继续展示。 */
    fun apkFailure(
        previous: ApkUpdateCheckState,
        installedVersion: String,
        installedCode: Long,
        now: Long,
        error: String
    ): ApkUpdateCheckState = previous.copy(
        status = UpdateCheckStatus.FAILED,
        installedVersion = installedVersion,
        installedCode = installedCode,
        // APK 覆盖更新只由 versionCode 决定；同 versionName、更高 code 仍是有效更新。
        available = previous.latestCode > installedCode,
        lastCheckedAt = now,
        lastErrorAt = now,
        lastError = error.take(160)
    )

    /**
     * 用当前安装身份重算 APK 可用性。
     *
     * 覆盖安装保留 state 目录，文件中的 installedVersion/installedCode 可能属于旧 APK；
     * 恢复时保留远端 latest 与 notifiedVersion，只按当前 versionCode 重算 available。
     */
    fun rebaseApkSnapshot(
        saved: ApkUpdateCheckState,
        installedVersion: String,
        installedCode: Long
    ): ApkUpdateCheckState = saved.copy(
        installedVersion = installedVersion,
        installedCode = installedCode,
        available = saved.latestCode > installedCode
    )

    /**
     * 合并手动检查结果。
     *
     * [startedAt] 是手动检查的开始时间；如果它早于最近一次后台检查完成时间，
     * 说明这是 Activity 重建回放的旧结果或较慢的旧请求，必须保留较新的后台快照。
     */
    fun applyManualApk(
        candidateVersion: String?,
        candidateCode: Long?,
        startedAt: Long,
        previous: ApkUpdateCheckState,
        installedVersion: String,
        installedCode: Long
    ): ApkUpdateCheckState {
        if (startedAt < previous.lastCheckedAt) return previous
        if (candidateVersion != null && candidateCode != null &&
            isValidVersion(candidateVersion) && candidateCode > installedCode
        ) {
            return previous.copy(
                status = UpdateCheckStatus.SUCCEEDED,
                installedVersion = installedVersion,
                installedCode = installedCode,
                latestVersion = candidateVersion,
                latestCode = candidateCode,
                available = true,
                lastCheckedAt = startedAt,
                lastErrorAt = 0L,
                lastError = ""
            )
        }
        return previous.copy(
            status = UpdateCheckStatus.SUCCEEDED,
            installedVersion = installedVersion,
            installedCode = installedCode,
            latestVersion = installedVersion,
            latestCode = installedCode,
            available = false,
            lastCheckedAt = startedAt,
            lastErrorAt = 0L,
            lastError = ""
        )
    }

    /** 解析 npm 的 dist-tags.latest，并要求它真实出现在 versions 中。 */
    fun latestFromMetadata(raw: String): String? = runCatching {
        val metadata = JSONObject(raw)
        val latest = metadata.optJSONObject("dist-tags")?.optString("latest").orEmpty()
        if (!isValidVersion(latest)) return@runCatching null
        val versions = metadata.optJSONObject("versions") ?: return@runCatching null
        latest.takeIf { versions.has(it) }
    }.getOrNull()

    /**
     * 依次尝试镜像与官方；某源网络失败或返回不完整结构时继续下一个源。
     * 两个源都失败时保留上一次的 latest/available，只把动作状态标为 FAILED。
     */
    fun evaluateDsh(
        installedVersion: String,
        now: Long,
        previous: DshUpdateCheckState = DshUpdateCheckState(),
        fetch: (DshRegistry) -> String
    ): DshUpdateCheckState {
        for (registry in DshRegistry.values()) {
            val raw = try {
                fetch(registry)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            } ?: continue
            val latest = latestFromMetadata(raw) ?: continue
            return previous.copy(
                status = UpdateCheckStatus.SUCCEEDED,
                installedVersion = installedVersion,
                latestVersion = latest,
                available = isUpdateAvailable(installedVersion, latest),
                lastCheckedAt = now,
                lastErrorAt = 0L,
                lastError = "",
                source = registry.label
            )
        }
        return previous.copy(
            status = UpdateCheckStatus.FAILED,
            installedVersion = installedVersion,
            available = isUpdateAvailable(installedVersion, previous.latestVersion),
            lastCheckedAt = now,
            lastErrorAt = now,
            lastError = "镜像与官方源均不可用"
        )
    }

    fun shouldNotifyApk(state: ApkUpdateCheckState): Boolean =
        state.available && state.latestVersion.isNotBlank() && state.notifiedVersion != state.latestVersion

    /**
     * 合并手动刷新得到的官方 latest；与 APK 一样，开始时间早于现有后台结果时拒绝覆盖。
     */
    fun applyManualDsh(
        latest: String,
        startedAt: Long,
        previous: DshUpdateCheckState,
        installedVersion: String
    ): DshUpdateCheckState {
        if (!isValidVersion(latest) || startedAt < previous.lastCheckedAt) return previous
        return previous.copy(
            status = UpdateCheckStatus.SUCCEEDED,
            installedVersion = installedVersion,
            latestVersion = latest,
            available = isUpdateAvailable(installedVersion, latest),
            lastCheckedAt = startedAt,
            lastErrorAt = 0L,
            lastError = "",
            source = DshRegistry.OFFICIAL.label
        )
    }

    fun shouldNotifyDsh(state: DshUpdateCheckState): Boolean =
        state.available && state.latestVersion.isNotBlank() && state.notifiedVersion != state.latestVersion

    fun markApkNotified(state: ApkUpdateCheckState, version: String): ApkUpdateCheckState =
        state.copy(notifiedVersion = version.takeIf(::isValidVersion).orEmpty())

    fun markDshNotified(state: DshUpdateCheckState, version: String): DshUpdateCheckState =
        state.copy(notifiedVersion = version.takeIf(::isValidVersion).orEmpty())

    fun encodeApk(state: ApkUpdateCheckState): String = JSONObject()
        .put("schema", 1)
        .put("kind", "apk")
        .put("status", persistedStatus(state.status))
        .put("installedVersion", state.installedVersion)
        .put("installedCode", state.installedCode)
        .put("latestVersion", state.latestVersion)
        .put("latestCode", state.latestCode)
        .put("available", state.available)
        .put("lastCheckedAt", state.lastCheckedAt)
        .put("lastErrorAt", state.lastErrorAt)
        .put("lastError", state.lastError.take(160))
        .put("notifiedVersion", state.notifiedVersion)
        .toString()

    fun encodeDsh(state: DshUpdateCheckState): String = JSONObject()
        .put("schema", 1)
        .put("kind", "dsh")
        .put("status", persistedStatus(state.status))
        .put("installedVersion", state.installedVersion)
        .put("latestVersion", state.latestVersion)
        .put("available", state.available)
        .put("lastCheckedAt", state.lastCheckedAt)
        .put("lastErrorAt", state.lastErrorAt)
        .put("lastError", state.lastError.take(160))
        .put("source", state.source.take(64))
        .put("notifiedVersion", state.notifiedVersion)
        .toString()

    /** 损坏、旧 schema 或错误种类一律回落到空状态，绝不影响应用启动。 */
    fun decodeApk(raw: String?): ApkUpdateCheckState {
        if (raw.isNullOrBlank()) return ApkUpdateCheckState()
        return runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schema", 0) != 1 || json.optString("kind") != "apk") return@runCatching ApkUpdateCheckState()
            val installedVersion = json.optString("installedVersion").takeIf(::isValidVersion).orEmpty()
            val installedCode = json.optLong("installedCode", 0L).coerceAtLeast(0L)
            val latestVersion = json.optString("latestVersion").takeIf(::isValidVersion).orEmpty()
            val latestCode = json.optLong("latestCode", 0L).coerceAtLeast(0L)
            ApkUpdateCheckState(
                status = decodeStatus(json.optString("status")),
                installedVersion = installedVersion,
                installedCode = installedCode,
                latestVersion = latestVersion,
                latestCode = latestCode,
                // 与 evaluateApk 保持一致：APK 只用 versionCode 判定，不额外比较 SemVer。
                available = latestCode > installedCode,
                lastCheckedAt = json.optLong("lastCheckedAt", 0L).coerceAtLeast(0L),
                lastErrorAt = json.optLong("lastErrorAt", 0L).coerceAtLeast(0L),
                lastError = json.optString("lastError").take(160),
                notifiedVersion = json.optString("notifiedVersion").takeIf(::isValidVersion).orEmpty()
            )
        }.getOrDefault(ApkUpdateCheckState())
    }

    fun decodeDsh(raw: String?): DshUpdateCheckState {
        if (raw.isNullOrBlank()) return DshUpdateCheckState()
        return runCatching {
            val json = JSONObject(raw)
            if (json.optInt("schema", 0) != 1 || json.optString("kind") != "dsh") return@runCatching DshUpdateCheckState()
            val installedVersion = json.optString("installedVersion").takeIf(::isValidVersion).orEmpty()
            val latestVersion = json.optString("latestVersion").takeIf(::isValidVersion).orEmpty()
            DshUpdateCheckState(
                status = decodeStatus(json.optString("status")),
                installedVersion = installedVersion,
                latestVersion = latestVersion,
                available = isUpdateAvailable(installedVersion, latestVersion),
                lastCheckedAt = json.optLong("lastCheckedAt", 0L).coerceAtLeast(0L),
                lastErrorAt = json.optLong("lastErrorAt", 0L).coerceAtLeast(0L),
                lastError = json.optString("lastError").take(160),
                source = json.optString("source").take(64),
                notifiedVersion = json.optString("notifiedVersion").takeIf(::isValidVersion).orEmpty()
            )
        }.getOrDefault(DshUpdateCheckState())
    }

    private fun persistedStatus(status: UpdateCheckStatus): String =
        if (status == UpdateCheckStatus.CHECKING) UpdateCheckStatus.FAILED.name else status.name

    private fun decodeStatus(raw: String): UpdateCheckStatus {
        val status = runCatching { UpdateCheckStatus.valueOf(raw) }.getOrDefault(UpdateCheckStatus.NEVER)
        return if (status == UpdateCheckStatus.CHECKING) UpdateCheckStatus.FAILED else status
    }
}
