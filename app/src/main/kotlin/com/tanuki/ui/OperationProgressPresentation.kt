package com.tanuki.ui

import com.tanuki.runtime.HealthState
import com.tanuki.runtime.ServiceState
import com.tanuki.runtime.EnvironmentState
import com.tanuki.runtime.Snapshot
import com.tanuki.runtime.UpdateState
import com.tanuki.runtime.measuredFraction
import com.tanuki.runtime.operationActive

/** 用户能直接理解的安装阶段；原始 stage 文案只作为抽屉里的补充说明。 */
internal enum class OperationStage(val label: String) {
    PREPARE("准备"),
    DOWNLOAD("下载"),
    EXTRACT("解压"),
    VERIFY("校验"),
    INSTALL("安装"),
    UNINSTALL("卸载"),
    START("启动"),
    CHECK("检查"),
    STOP("停止"),
}

/**
 * 进度条的语义。
 *
 * 只有拿到可信总量时才允许 [DETERMINATE]；未知总量只能用 [INDETERMINATE]，
 * 失败态单独使用 [FAILED]，避免把失败画成“已完成”或“进行中”。
 */
internal enum class OperationProgressMode { DETERMINATE, INDETERMINATE, FAILED }

/**
 * 全局安装进度的纯展示模型。
 *
 * 不依赖 Compose，便于在 JVM 锁定“百分比只属于当前阶段、未知总量不造数、
 * 阶段文案是人话、失败可重试”等规则。
 */
internal data class OperationProgressText(
    val active: Boolean,
    val mode: OperationProgressMode,
    val stage: OperationStage,
    val fraction: Float?,
    val percent: Int?,
    /** 顶部细进度条旁的短文案，例如“下载 42%”“正在准备”。 */
    val statusText: String,
    /** 运行时写入的原始阶段，作为抽屉里的补充信息。 */
    val stageText: String,
    /** 抽屉里的说明或失败原因。 */
    val detail: String,
    val elapsedText: String,
    val remainingText: String?,
    /** 超过一分钟没有新字节/阶段/输出时的提醒；没有静默时为空。 */
    val quietWarning: String?,
    val canCancel: Boolean,
    val canRetry: Boolean,
    val retryCommand: String?,
    val failureTitle: String?,
)

/** 失败态及可执行的重试命令；没有失败时返回 null。 */
internal data class OperationFailure(
    val title: String,
    val detail: String,
    val retryCommand: String?,
)

/**
 * 把运行时阶段文案归一到“下载 / 解压 / 校验 / 安装”等短标签。
 *
 * 启动、停止和健康检查先看真实状态；其余优先看第一个动作词，避免“下载 DSH 依赖（已校验 1/2）”被括号里的
 * “校验”误判；同时包含“校验与解压”时按耗时最长的“解压”展示。
 */
internal fun operationStage(
    stage: String,
    environment: EnvironmentState,
    update: UpdateState,
    service: ServiceState = ServiceState.STOPPED,
    health: HealthState = HealthState.UNKNOWN,
): OperationStage {
    // 启动和健康检查可能沿用安装完成后的阶段说明，真实运行状态优先。
    if (service == ServiceState.STARTING) return OperationStage.START
    if (service == ServiceState.STOPPING) return OperationStage.STOP
    if (health == HealthState.CHECKING || environment == EnvironmentState.CHECKING) return OperationStage.CHECK
    val text = stage.trim()
    if (text.contains("解压") || text.contains("展开")) return OperationStage.EXTRACT
    val candidates = listOf(
        OperationStage.DOWNLOAD to listOf("下载"),
        OperationStage.VERIFY to listOf("校验", "验证"),
        OperationStage.INSTALL to listOf("安装", "写入", "补齐"),
        OperationStage.UNINSTALL to listOf("卸载", "移除"),
        OperationStage.START to listOf("启动"),
        OperationStage.CHECK to listOf("检查", "检测", "解析", "测速", "自检"),
        OperationStage.PREPARE to listOf("准备"),
    ).mapNotNull { (stageKind, words) ->
        words.map { text.indexOf(it) }.filter { it >= 0 }.minOrNull()?.let { it to stageKind }
    }
    candidates.minByOrNull { it.first }?.let { return it.second }
    return when (environment) {
        EnvironmentState.DOWNLOADING -> OperationStage.DOWNLOAD
        EnvironmentState.INSTALLING -> OperationStage.INSTALL
        EnvironmentState.VERIFYING -> OperationStage.VERIFY
        EnvironmentState.CHECKING -> OperationStage.CHECK
        else -> when (update) {
            UpdateState.STAGING -> OperationStage.DOWNLOAD
            UpdateState.TESTING -> OperationStage.VERIFY
            UpdateState.BACKUP, UpdateState.SWITCHING, UpdateState.RESTORING -> OperationStage.INSTALL
            UpdateState.CHECKING -> OperationStage.CHECK
            else -> OperationStage.PREPARE
        }
    }
}

/** 只在运行时确实失败或取消时给出失败卡；普通空闲不制造常驻提示。 */
internal fun operationFailure(snapshot: Snapshot): OperationFailure? = when {
    snapshot.failedOperation != null -> OperationFailure(
        title = if (snapshot.failedOperation.cancelled) "操作已取消" else "操作失败",
        detail = snapshot.failedOperation.reason.ifBlank {
            if (snapshot.failedOperation.cancelled) "操作已停止，可以重试并复用已校验的下载。" else "操作没有完成，请检查当前阶段后重试。"
        } + if (!snapshot.failedOperation.retryAvailable) " 组件版本已更改，请从组件入口重新发起安装。" else "",
        retryCommand = if (snapshot.failedOperation.retryAvailable) "RETRY_OPERATION/${snapshot.failedOperation.retryId}"
            else "OPEN_COMPONENTS",
    )
    snapshot.update == UpdateState.RECOVERY_REQUIRED -> OperationFailure(
        title = "更新失败",
        detail = snapshot.error.ifBlank { "候选版本没有通过检查；可以查看日志后重试。" },
        retryCommand = "RESUME_UPDATE",
    )
    snapshot.service == com.tanuki.runtime.ServiceState.FAILED ||
        snapshot.service == com.tanuki.runtime.ServiceState.INTERRUPTED -> OperationFailure(
        title = "启动失败",
        detail = snapshot.error.ifBlank { "DSH 没有正常启动；可以检查运行环境后重试。" },
        retryCommand = "START",
    )
    snapshot.health == com.tanuki.runtime.HealthState.FAILED -> OperationFailure(
        title = "运行检查未通过",
        detail = snapshot.error.ifBlank { "后台自检没有通过；可以重新检查运行环境。" },
        retryCommand = "HEALTH",
    )
    snapshot.environment == EnvironmentState.FAILED || snapshot.update in setOf(UpdateState.FAILED, UpdateState.CHECK_FAILED) -> OperationFailure(
        title = "安装或检查失败",
        detail = snapshot.error.ifBlank { "操作没有完成；请返回组件入口选择需要重试的项目。" },
        retryCommand = "OPEN_COMPONENTS",
    )
    snapshot.environment == EnvironmentState.CANCELLED -> OperationFailure(
        title = "安装已取消",
        detail = snapshot.error.ifBlank { "取消后可以重试，已校验的下载会尽量复用。" },
        retryCommand = "OPEN_COMPONENTS",
    )
    else -> null
}

/**
 * 生成顶部入口和抽屉所需的全部文案。
 *
 * [appearedAtMillis] 是本次操作在界面首次出现的单调时钟，运行时没有写入
 * startedAt 时用它兜底，避免把“看到卡片的时间”伪造成真实开始时间。
 */
internal fun operationProgress(
    snapshot: Snapshot,
    nowMillis: Long,
    appearedAtMillis: Long,
): OperationProgressText? {
    val active = snapshot.operationActive
    // 进行中的操作优先于历史失败：旧失败状态不能把新安装显示成失败。
    val failure = if (active) null else operationFailure(snapshot)
    if (!active && failure == null) return null
    val start = snapshot.timing.startedAtMillis.takeIf { it > 0 } ?: appearedAtMillis
    val lastActivity = snapshot.timing.lastActivityAtMillis.takeIf { it > 0 } ?: start
    val elapsedSeconds = ((nowMillis - start).coerceAtLeast(0) / 1000)
    val quietSeconds = ((nowMillis - lastActivity).coerceAtLeast(0) / 1000)
    val stage = operationStage(if (active) snapshot.stage else snapshot.failedOperation?.stage ?: snapshot.stage,
        snapshot.environment, snapshot.update, snapshot.service, snapshot.health)
    if (failure != null) {
        return OperationProgressText(
            active = false,
            mode = OperationProgressMode.FAILED,
            stage = stage,
            fraction = null,
            percent = null,
            statusText = failure.title,
            stageText = snapshot.failedOperation?.stage ?: snapshot.stage.ifBlank { stage.label },
            detail = failure.detail,
            elapsedText = snapshot.failedOperation?.let { "已用 " + duration(it.elapsedMillis / 1000) }
                ?: "操作已结束",
            remainingText = null,
            quietWarning = null,
            canCancel = false,
            canRetry = failure.retryCommand != null,
            retryCommand = failure.retryCommand,
            failureTitle = failure.title,
        )
    }
    val runtimeStage = stage in setOf(OperationStage.START, OperationStage.CHECK, OperationStage.STOP)
    val stageText = when {
        snapshot.service == ServiceState.STARTING -> "正在启动助手"
        snapshot.service == ServiceState.STOPPING -> "正在停止助手"
        snapshot.health == HealthState.CHECKING -> "正在检查运行环境"
        snapshot.environment == EnvironmentState.CHECKING -> "正在检查运行环境"
        else -> snapshot.stage.ifBlank { stage.label }
    }
    // 启动和检查没有可信总量，不沿用之前下载留下的字节数。
    val fraction = if (runtimeStage) null else snapshot.measuredFraction()
    val percent = fraction?.let { (it * 100).toInt().coerceIn(0, 100) }
    return OperationProgressText(
        active = true,
        mode = if (fraction != null) OperationProgressMode.DETERMINATE else OperationProgressMode.INDETERMINATE,
        stage = stage,
        fraction = fraction,
        percent = percent,
        statusText = percent?.let { "${stage.label} $it%" } ?: "正在${stage.label}",
        stageText = stageText,
        detail = if (stage == OperationStage.DOWNLOAD) downloadDetail(snapshot) else stageText,
        elapsedText = "已用 " + duration(elapsedSeconds),
        remainingText = if (runtimeStage) null else remainingText(snapshot, fraction),
        quietWarning = if (quietSeconds >= 60) {
            "已 ${duration(quietSeconds)}没有新进展。" + when (stage) {
                OperationStage.DOWNLOAD -> "请检查网络，必要时取消后重试。"
                OperationStage.START -> "助手仍在启动，请稍候；若持续无响应，可查看运行诊断。"
                OperationStage.CHECK -> "运行环境仍在检查，请稍候；若持续无响应，可查看运行诊断。"
                OperationStage.STOP -> "助手仍在结束运行，请稍候。"
                else -> "当前操作仍在进行，请稍候；若持续无响应，可查看运行诊断。"
            }
        } else null,
        canCancel = snapshot.service !in setOf(
            com.tanuki.runtime.ServiceState.STARTING,
            com.tanuki.runtime.ServiceState.STOPPING,
        ) && snapshot.update !in setOf(UpdateState.SWITCHING, UpdateState.RESTORING),
        canRetry = false,
        retryCommand = null,
        failureTitle = null,
    )
}

/**
 * 大致剩余时间：下载阶段用实时速度和剩余字节估算；阶段没有速度样本时使用
 * “预计还需要几分钟”，不拿整次操作耗时推导新阶段的速度。
 */
internal fun remainingText(snapshot: Snapshot, fraction: Float?): String? {
    if (!snapshot.operationActive) return null
    if (fraction == null) return "预计还需要几分钟"
    if (fraction >= 0.999f) return "即将完成"
    val download = snapshot.download
    if (download != null && download.total > 0 && download.received > 0 && download.bytesPerSecond > 0) {
        val remainBytes = (download.total - download.received).coerceAtLeast(0)
        if (remainBytes == 0L) return "即将完成"
        val seconds = (remainBytes / download.bytesPerSecond).coerceAtLeast(1)
        return "预计还需要" + duration(seconds)
    }
    return "预计还需要几分钟"
}

/** 下载阶段补充来源、字节和速度；其他阶段只保留运行时自己的阶段说明。 */
private fun downloadDetail(snapshot: Snapshot): String {
    val download = snapshot.download ?: return snapshot.stage.ifBlank { "正在执行，完成后会自动更新" }
    val size = if (download.total > 0) "${bytes(download.received)} / ${bytes(download.total)}"
    else if (download.received > 0) "已下载 ${bytes(download.received)} · 总大小未知"
    else "正在连接下载源 · 总大小未知"
    val speed = if (download.bytesPerSecond > 0) " · ${bytes(download.bytesPerSecond)}/秒" else ""
    val cached = if (download.reused) " · 已复用并通过校验" else " · ${download.source}"
    return size + speed + cached
}

internal fun duration(seconds: Long): String = when {
    seconds >= 3600 -> "${seconds / 3600}时${seconds % 3600 / 60}分${seconds % 60}秒"
    seconds >= 60 -> "${seconds / 60}分${seconds % 60}秒"
    else -> "${seconds}秒"
}

private fun bytes(value: Long): String = when {
    value >= 1024L * 1024 -> String.format(java.util.Locale.ROOT, "%.1f MB", value / 1048576.0)
    value >= 1024 -> String.format(java.util.Locale.ROOT, "%.1f KB", value / 1024.0)
    else -> "$value B"
}
