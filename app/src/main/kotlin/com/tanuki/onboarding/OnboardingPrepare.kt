package com.tanuki.onboarding

import com.tanuki.runtime.EnvironmentState

/**
 * “一键准备”的阶段。
 *
 * 只使用运行时真实上报的状态，不把未知总量伪装成百分比：
 * 下载和系统组件安装有真实总量时才给 [OnboardingPrepareState.fraction]；
 * 解压/校验总量未知时保持 null，由界面显示不定进度和已写入量。
 */
enum class OnboardingPreparePhase {
    IDLE,
    DOWNLOAD,
    EXTRACT,
    VERIFY,
    INSTALL,
    READY,
    FAILED,
    CANCELLED,
}

data class OnboardingPrepareState(
    val phase: OnboardingPreparePhase,
    /** 仅当总量可信时非空，范围 0..1。 */
    val fraction: Float?,
    val label: String,
    val detail: String,
    val receivedBytes: Long = 0,
    val totalBytes: Long = -1,
    val retryable: Boolean = false,
    val failure: String? = null,
) {
    val active: Boolean
        get() = phase in setOf(
            OnboardingPreparePhase.DOWNLOAD,
            OnboardingPreparePhase.EXTRACT,
            OnboardingPreparePhase.VERIFY,
            OnboardingPreparePhase.INSTALL,
        )
}

/**
 * 把运行时的 [EnvironmentState] 与阶段文案映射成引导页进度。
 *
 * `stage` 是既有安装流程的中文阶段说明，可能包含“已写入 N MB”等实时信息；
 * 这里只识别稳定关键词，未知文案不猜测百分比。
 */
object OnboardingPrepare {
    fun from(
        environment: EnvironmentState,
        stage: String,
        receivedBytes: Long = 0,
        totalBytes: Long = -1,
        phasePercent: Int? = null,
        error: String = "",
    ): OnboardingPrepareState {
        val detail = stage.trim()
        val failed = environment == EnvironmentState.FAILED ||
            (environment == EnvironmentState.REPAIR && error.isNotBlank())
        if (failed) return OnboardingPrepareState(
            phase = OnboardingPreparePhase.FAILED,
            fraction = null,
            label = "准备未完成",
            detail = detail,
            retryable = true,
            failure = OnboardingFailure.describe(error.ifBlank { detail }),
        )
        if (environment == EnvironmentState.CANCELLED) return OnboardingPrepareState(
            phase = OnboardingPreparePhase.CANCELLED,
            fraction = null,
            label = "已取消",
            detail = detail.ifBlank { "已停止准备" } + "；可以随时重试，完整下载会被复用。",
            retryable = true,
        )
        if (environment == EnvironmentState.READY) return OnboardingPrepareState(
            phase = OnboardingPreparePhase.READY,
            fraction = 1f,
            label = "环境已就绪",
            detail = detail.ifBlank { "安装与自检已完成，可以开始使用。" },
            retryable = false,
        )
        return when (environment) {
            EnvironmentState.DOWNLOADING -> OnboardingPrepareState(
                phase = OnboardingPreparePhase.DOWNLOAD,
                fraction = fraction(receivedBytes, totalBytes),
                label = "下载运行环境",
                detail = detail.ifBlank { "正在下载，完成后会自动解压并校验。" },
                receivedBytes = receivedBytes,
                totalBytes = totalBytes,
            )
            EnvironmentState.INSTALLING -> when {
                stage.contains("解压") -> OnboardingPrepareState(
                    phase = OnboardingPreparePhase.EXTRACT,
                    fraction = null,
                    label = "解压运行环境",
                    detail = detail,
                )
                stage.contains("校验") -> OnboardingPrepareState(
                    phase = OnboardingPreparePhase.VERIFY,
                    fraction = null,
                    label = "校验运行环境",
                    detail = detail,
                )
                else -> OnboardingPrepareState(
                    phase = OnboardingPreparePhase.INSTALL,
                    fraction = phasePercent?.coerceIn(0, 100)?.let { it / 100f },
                    label = "补齐系统组件",
                    detail = detail,
                    receivedBytes = receivedBytes,
                    totalBytes = totalBytes,
                )
            }
            EnvironmentState.VERIFYING -> OnboardingPrepareState(
                phase = OnboardingPreparePhase.VERIFY,
                fraction = null,
                label = "校验运行环境",
                detail = detail.ifBlank { "正在做启动与完整性检查。" },
            )
            else -> OnboardingPrepareState(
                phase = OnboardingPreparePhase.IDLE,
                fraction = null,
                label = "等待开始",
                detail = detail.ifBlank { "准备过程会下载、解压并校验必要组件。" },
                retryable = environment in setOf(EnvironmentState.ABSENT, EnvironmentState.REPAIR),
            )
        }
    }

    /** 总量未知或数据异常时不显示百分比。 */
    private fun fraction(received: Long, total: Long): Float? {
        if (total <= 0 || received < 0) return null
        return (received.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }
}

/** 把底层异常翻译成用户能执行的中文原因；不暴露内部路径和堆栈。 */
object OnboardingFailure {
    fun describe(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return "准备没有完成，但系统没有返回具体原因。请重试；若仍失败，可在设置 → 关于与诊断中查看运行诊断。"
        val lower = text.lowercase()
        return when {
            listOf("空间不足", "enospc", "no space", "disk full").any { it in lower } ->
                "存储空间不足。请清理手机存储，至少留出 1.5 GB 后重试；已下载的完整文件会复用。"
            listOf("sha", "校验", "digest", "integrity").any { it in lower } ->
                "下载文件校验未通过，内容可能不完整。已保留可用缓存，请重试；反复失败可更换网络或下载源。"
            listOf("权限", "eacces", "accessdenied", "access denied", "拒绝访问").any { it in lower } ->
                "文件访问被拒绝。请检查系统存储权限后重试；必要时在系统设置中允许本应用访问存储。"
            listOf("取消", "cancel").any { it in lower } ->
                "操作已取消。可以随时重试，已完成的下载会继续复用。"
            listOf("timeout", "timed out", "超时", "unknownhost", "unable to resolve", "connect", "网络", "下载源", "下载失败", "连接失败").any { it in lower } ->
                "网络连接失败或下载源不可用。请切换网络后重试；准备过程会自动尝试其他可用下载源。"
            else -> "准备没有完成：" + text.lineSequence().firstOrNull { it.isNotBlank() }
                .orEmpty().take(160) + "。请重试；若仍失败，可在设置 → 关于与诊断中查看运行诊断。"
        }
    }
}

/** 引导过程中会触发的、需要运行时授权的动作。 */
enum class OnboardingPermissionAction {
    PREPARE_ENVIRONMENT,
    SEND_FIRST_MESSAGE,
    CONNECT_REMOTE,
    OPEN_FILES,
}

enum class OnboardingPermissionNeed { NONE, NOTIFICATIONS, STORAGE }

/**
 * 按需申请权限。
 *
 * 引导本身不一次性弹窗：
 * - 只有开始“一键准备”时才申请通知权限，用于显示前台安装进度；
 * - 存储权限仍由文件页在真正读写共享存储时申请；
 * - 本版本没有使用附近设备/蓝牙能力，因此清单不声明、引导也不申请。
 */
object OnboardingPermissions {
    fun needed(
        action: OnboardingPermissionAction,
        sdkInt: Int,
        notificationsGranted: Boolean,
        storageGranted: Boolean,
    ): OnboardingPermissionNeed = when (action) {
        OnboardingPermissionAction.PREPARE_ENVIRONMENT ->
            if (sdkInt >= 33 && !notificationsGranted) OnboardingPermissionNeed.NOTIFICATIONS
            else OnboardingPermissionNeed.NONE
        OnboardingPermissionAction.OPEN_FILES ->
            if (!storageGranted) OnboardingPermissionNeed.STORAGE
            else OnboardingPermissionNeed.NONE
        // 发消息和连接远程只使用网络；联网是普通权限，不需要运行时弹窗。
        OnboardingPermissionAction.SEND_FIRST_MESSAGE,
        OnboardingPermissionAction.CONNECT_REMOTE,
        -> OnboardingPermissionNeed.NONE
    }
}
