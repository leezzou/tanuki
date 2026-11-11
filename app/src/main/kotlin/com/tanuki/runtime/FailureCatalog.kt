package com.tanuki.runtime

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 面向用户的失败域。只决定展示位置与动作，不参与运行状态机。
 */
enum class FailureDomain { INSTALL, UPDATE, RUNTIME, FILE, UNKNOWN }

/**
 * 失败原因分类。分类结果只用于选一句人话原因和下一步动作，
 * 原始异常仍保留在可折叠详情里，供反馈问题时复制。
 */
enum class FailureCause {
    NETWORK,
    DISK,
    PERMISSION,
    CHECKSUM,
    EXTRACT,
    COMPATIBILITY,
    BUSY,
    STARTUP,
    CRASH,
    KILLED,
    FILE_ACCESS,
    UNKNOWN
}

/** 失败卡上的动作类型；具体命令由界面层按上下文映射。 */
enum class FailureActionKind {
    RETRY,
    SWITCH_SOURCE,
    CLEAN_CACHE,
    OPEN_STORAGE,
    OPEN_SETTINGS,
    RESTART,
    SAFE_MODE,
    TRY_NORMAL,
    VIEW_VERSIONS
}

data class FailureAction(val kind: FailureActionKind, val label: String)

/**
 * 统一错误模型：标题、一句话原因、可折叠技术细节、主行动和次行动。
 *
 * 约束：
 * - 标题与原因只写用户能理解、能行动的内容，不出现内部实现术语；
 * - detail 保留脱敏后的原始异常，默认折叠；
 * - componentId/operation 用于把错误放回对应组件卡，并决定重试命令。
 */
data class AppFailure(
    val domain: FailureDomain,
    val cause: FailureCause,
    val title: String,
    val reason: String,
    val detail: String,
    val primaryAction: FailureAction,
    val secondaryAction: FailureAction? = null,
    val componentId: String? = null,
    val operation: String? = null,
    val retryCommand: String? = null,
    val occurredAtMillis: Long = 0L,
    val attempt: Int = 0
) {
    /** 同一秒内重复出现的同一错误合并展示；新的失败时间会生成新的 key。 */
    val stableKey: String get() = listOf(
        domain.name, cause.name, componentId ?: "", operation ?: "",
        detail.hashCode().toString(), occurredAtMillis.toString()
    ).joinToString("|")

    /** 复制给开发者或反馈问题时使用的完整文本。 */
    fun copyText(versionName: String = ""): String = buildString {
        appendLine(title)
        appendLine(reason)
        if (versionName.isNotBlank()) appendLine("应用版本：$versionName")
        // 应用更新状态用的是开机计时；只在明显是墙上时间时才写日期。
        if (occurredAtMillis > 1_000_000_000_000L) {
            val time = runCatching {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(occurredAtMillis))
            }.getOrNull()
            if (!time.isNullOrBlank()) appendLine("发生时间：$time")
        }
        if (attempt > 0) appendLine("已尝试：$attempt 次")
        if (detail.isNotBlank()) {
            appendLine("技术详情：")
            append(detail)
        }
    }.trim()
}

/** 交给分类器的现场信息；调用方只填已知字段。 */
data class FailureInput(
    val message: String,
    val operation: String? = null,
    val stage: String = "",
    val environment: EnvironmentState? = null,
    val update: UpdateState? = null,
    val service: ServiceState? = null,
    val recovery: RuntimeRecoveryState = RuntimeRecoveryState(),
    val failureKind: FailureKind? = null,
    val domainHint: FailureDomain? = null,
    val componentId: String? = null,
    val occurredAtMillis: Long = 0L,
    val attempt: Int = 0
)

/**
 * 纯 Kotlin 的失败分类与文案/动作选择。
 *
 * 这里不依赖 Android，JVM 测试可直接覆盖：错误分类映射、主行动选择、
 * 文案不出现内部术语，以及恢复后隐藏旧错误。
 */
object FailureCatalog {
    private val installOperations = setOf("initialize", "component-tools")
    private val updateOperations = setOf(
        "dsh-install", "dsh-stage", "dsh-switch", "dsh-restore",
        "dsh-resume", "dsh-update", "app-update", "component-switch-dsh"
    )

    private val networkMarkers = listOf(
        "超时", "timeout", "timed out", "网络", "断网", "连接中断", "连接失败",
        "无法连接", "连接被重置", "connection reset", "unknownhost", "unable to resolve host",
        "connectexception", "sockettimeout", "ssl", "tls handshake", "http 5", "http 4",
        "eof", "unexpected end of stream", "no route to host", "network is unreachable"
    )
    private val diskMarkers = listOf(
        "enospc", "no space left", "not enough space", "disk full", "空间不足", "磁盘已满",
        "磁盘空间不足", "存储空间不足", "剩余空间不足", "容量不足", "usablespace"
    )
    private val permissionMarkers = listOf(
        "eacces", "permission denied", "access denied", "accessdeniedexception", "权限",
        "被拒绝", "未授权", "授权失效", "securityexception", "operation not permitted"
    )
    private val checksumMarkers = listOf(
        "sha-256", "sha256", "校验失败", "校验不一致", "完整性", "integrity", "digest",
        "签名", "长度不符", "长度与声明", "内容长度"
    )
    private val extractMarkers = listOf(
        "解压", "解包", "extract", "tar ", "gzip", "压缩包", "archive", "损坏的压缩"
    )
    private val compatibilityMarkers = listOf(
        "不兼容", "版本要求", "requires node", "node.js 要求", "仅支持 arm64", "版本格式无效"
    )
    private val busyMarkers = listOf(
        "正在进行", "请稍后", "请先停止", "请等待", "占用", "忙碌"
    )
    private val fileMarkers = listOf("文件", "复制", "导入", "导出", "目录")

    /** 将一次失败收敛成统一结构。 */
    fun describe(input: FailureInput): AppFailure {
        val message = input.message.trim()
        val domain = resolveDomain(input, message)
        val cause = resolveCause(input, message)
        val componentId = resolveComponentId(input)
        val mode = input.recovery.mode
        val actions = resolveActions(domain, cause, componentId, mode)
        val title = titleFor(domain)
        val reason = reasonFor(domain, cause)
        return AppFailure(
            domain = domain,
            cause = cause,
            title = title,
            reason = reason,
            detail = message.ifBlank { reason },
            primaryAction = actions.first,
            secondaryAction = actions.second,
            componentId = componentId,
            operation = input.operation,
            retryCommand = retryCommand(domain, cause, input.operation, componentId),
            occurredAtMillis = input.occurredAtMillis,
            attempt = input.attempt
        )
    }

    /** 运行失败由运行策略直接给出分类，不再靠文本猜测。 */
    fun fromRuntimeFailure(
        kind: FailureKind,
        recovery: RuntimeRecoveryState,
        message: String,
        occurredAtMillis: Long,
        attempt: Int = recovery.restartAttempts
    ): AppFailure = describe(
        FailureInput(
            message = message,
            domainHint = FailureDomain.RUNTIME,
            recovery = recovery,
            failureKind = kind,
            componentId = "dsh",
            occurredAtMillis = occurredAtMillis,
            attempt = attempt
        )
    )

    private fun resolveDomain(input: FailureInput, message: String): FailureDomain {
        input.domainHint?.let { return it }
        if (input.failureKind != null) return FailureDomain.RUNTIME
        val operation = input.operation.orEmpty()
        if (operation in updateOperations) return FailureDomain.UPDATE
        if (input.update in setOf(
                UpdateState.CHECKING, UpdateState.CHECK_FAILED, UpdateState.STAGING,
                UpdateState.TESTING, UpdateState.WAITING, UpdateState.BACKUP,
                UpdateState.SWITCHING, UpdateState.RESTORING, UpdateState.FAILED,
                UpdateState.RECOVERY_REQUIRED
            )
        ) return FailureDomain.UPDATE
        if (operation in installOperations || operation.startsWith("component-uninstall-")) {
            return FailureDomain.INSTALL
        }
        if (operation.startsWith("component-")) return FailureDomain.INSTALL
        if (input.environment in setOf(
                EnvironmentState.CHECKING, EnvironmentState.DOWNLOADING,
                EnvironmentState.INSTALLING, EnvironmentState.VERIFYING,
                EnvironmentState.REPAIR, EnvironmentState.FAILED
            )
        ) return FailureDomain.INSTALL
        if (input.service in setOf(ServiceState.FAILED, ServiceState.INTERRUPTED)) {
            return FailureDomain.RUNTIME
        }
        if (input.componentId != null) return FailureDomain.INSTALL
        if (fileMarkers.any { message.contains(it, ignoreCase = true) }) return FailureDomain.FILE
        return FailureDomain.UNKNOWN
    }

    private fun resolveCause(input: FailureInput, message: String): FailureCause {
        when (input.failureKind) {
            FailureKind.NO_SPACE -> return FailureCause.DISK
            FailureKind.LAUNCH_FAILED, FailureKind.STARTUP_FAILED -> return FailureCause.STARTUP
            FailureKind.CRASHED_EARLY, FailureKind.CRASHED_LATE -> return FailureCause.CRASH
            FailureKind.KILLED -> return FailureCause.KILLED
            null -> Unit
        }
        // 顺序即优先级：先排除磁盘/权限/网络这些必须换动作的原因，再看校验与解压。
        val groups = listOf(
            FailureCause.DISK to diskMarkers,
            FailureCause.PERMISSION to permissionMarkers,
            FailureCause.NETWORK to networkMarkers,
            FailureCause.CHECKSUM to checksumMarkers,
            FailureCause.EXTRACT to extractMarkers,
            FailureCause.COMPATIBILITY to compatibilityMarkers,
            FailureCause.BUSY to busyMarkers
        )
        groups.forEach { (cause, markers) ->
            if (markers.any { message.contains(it, ignoreCase = true) }) return cause
        }
        if (message.contains("文件", ignoreCase = true) ||
            message.contains("无法读取") || message.contains("无法写入") ||
            message.contains("读取失败") || message.contains("写入失败")
        ) return FailureCause.FILE_ACCESS
        return FailureCause.UNKNOWN
    }

    private fun resolveComponentId(input: FailureInput): String? {
        input.componentId?.let { return it }
        val operation = input.operation.orEmpty().lowercase(Locale.ROOT)
        val stage = input.stage
        return when {
            operation.contains("ubuntu") || stage.contains("Ubuntu") -> "ubuntu"
            operation.contains("node") || stage.contains("Node.js") -> "node"
            operation.contains("dsh") || stage.contains("DSH") -> "dsh"
            else -> null
        }
    }

    private fun titleFor(domain: FailureDomain): String = when (domain) {
        FailureDomain.INSTALL -> "安装没有完成"
        FailureDomain.UPDATE -> "更新没有完成"
        FailureDomain.RUNTIME -> "DSH 没有正常运行"
        FailureDomain.FILE -> "文件操作没有完成"
        FailureDomain.UNKNOWN -> "操作没有完成"
    }

    private fun reasonFor(domain: FailureDomain, cause: FailureCause): String = when (cause) {
        FailureCause.NETWORK -> if (domain == FailureDomain.UPDATE)
            "网络连接中断，更新已经停下；现有应用和环境没有改动，恢复网络后可以重试。"
        else "网络连接中断，操作已经停下；已下载的部分会保留，恢复网络后可以重试。"
        FailureCause.DISK -> "设备空间不足，操作已经停止；项目、会话和已安装的组件都还在。清理下载缓存或设备空间后可以重试。"
        FailureCause.PERMISSION -> "系统没有允许访问需要的文件，操作因此停止；原有文件没有被覆盖。可以到系统设置授权后重试。"
        FailureCause.CHECKSUM -> "下载内容没有通过完整性检查，损坏的文件已经丢弃；可以重新下载或换一个下载源。"
        FailureCause.EXTRACT -> "下载内容无法解压，可能没有下载完整；可以重试或换一个下载源。"
        FailureCause.COMPATIBILITY -> "所选版本与当前环境不匹配，现有组件没有被替换；请选择其他版本再试。"
        FailureCause.BUSY -> "另一个安装或更新还在进行，请等它结束后再试。"
        FailureCause.STARTUP -> "DSH 在启动完成前停下了，所以页面暂时打不开；项目和会话都还在，可以重新启动。"
        FailureCause.CRASH -> "DSH 运行中意外停止，当前任务被中断；项目和会话都还在，可以重新启动，已中断的任务不会自动重放。"
        FailureCause.KILLED -> "系统回收了 DSH，可能是内存紧张；项目和会话都还在，可以清理下载缓存后重新启动。"
        FailureCause.FILE_ACCESS -> "文件没有读取或写入成功，原有文件没有被覆盖；请检查文件是否还在，然后重试。"
        FailureCause.UNKNOWN -> "操作没有完成，现有环境、项目和会话都没有被删除；可以重试或清理下载缓存后再试。"
    }

    private fun resolveActions(
        domain: FailureDomain,
        cause: FailureCause,
        componentId: String?,
        mode: RecoveryMode
    ): Pair<FailureAction, FailureAction?> = when (domain) {
        FailureDomain.RUNTIME -> when {
            cause == FailureCause.DISK -> action(FailureActionKind.CLEAN_CACHE, "清理下载缓存") to
                action(FailureActionKind.OPEN_STORAGE, "查看存储占用")
            mode == RecoveryMode.SAFE -> action(FailureActionKind.TRY_NORMAL, "试用正常模式") to
                action(FailureActionKind.CLEAN_CACHE, "清理下载缓存")
            mode == RecoveryMode.PROBATION -> action(FailureActionKind.SAFE_MODE, "回到安全模式") to
                action(FailureActionKind.CLEAN_CACHE, "清理下载缓存")
            else -> action(FailureActionKind.RESTART, "重新启动") to
                action(FailureActionKind.CLEAN_CACHE, "清理下载缓存")
        }
        FailureDomain.INSTALL -> when (cause) {
            FailureCause.DISK -> action(FailureActionKind.CLEAN_CACHE, "清理下载缓存") to
                action(FailureActionKind.OPEN_STORAGE, "查看存储占用")
            FailureCause.PERMISSION -> action(FailureActionKind.OPEN_SETTINGS, "去系统设置") to
                action(FailureActionKind.RETRY, "重试")
            FailureCause.COMPATIBILITY -> action(FailureActionKind.VIEW_VERSIONS, "选择其他版本") to
                action(FailureActionKind.RETRY, "重试")
            FailureCause.BUSY -> action(FailureActionKind.RETRY, "重试") to
                action(FailureActionKind.CLEAN_CACHE, "清理下载缓存")
            else -> action(FailureActionKind.RETRY, "重试") to
                if (componentId != null) action(FailureActionKind.SWITCH_SOURCE, "换一个下载源")
                else action(FailureActionKind.CLEAN_CACHE, "清理下载缓存")
        }
        FailureDomain.UPDATE -> when (cause) {
            FailureCause.DISK -> action(FailureActionKind.CLEAN_CACHE, "清理下载缓存") to
                action(FailureActionKind.OPEN_STORAGE, "查看存储占用")
            FailureCause.PERMISSION -> action(FailureActionKind.OPEN_SETTINGS, "去系统设置") to
                action(FailureActionKind.RETRY, "重试")
            FailureCause.COMPATIBILITY -> action(FailureActionKind.VIEW_VERSIONS, "选择其他版本") to
                action(FailureActionKind.CLEAN_CACHE, "清理下载缓存")
            else -> if (componentId == "dsh")
                action(FailureActionKind.VIEW_VERSIONS, "选择版本重试") to
                    action(FailureActionKind.CLEAN_CACHE, "清理下载缓存")
            else action(FailureActionKind.RETRY, "重试") to
                action(FailureActionKind.CLEAN_CACHE, "清理下载缓存")
        }
        FailureDomain.FILE -> when (cause) {
            FailureCause.DISK -> action(FailureActionKind.OPEN_STORAGE, "查看存储占用") to
                action(FailureActionKind.RETRY, "重试")
            FailureCause.PERMISSION -> action(FailureActionKind.OPEN_SETTINGS, "去系统设置") to
                action(FailureActionKind.RETRY, "重试")
            else -> action(FailureActionKind.RETRY, "重试") to
                action(FailureActionKind.CLEAN_CACHE, "清理下载缓存")
        }
        FailureDomain.UNKNOWN -> action(FailureActionKind.RETRY, "重试") to
            action(FailureActionKind.CLEAN_CACHE, "清理下载缓存")
    }

    private fun retryCommand(
        domain: FailureDomain,
        cause: FailureCause,
        operation: String?,
        componentId: String?
    ): String? = when {
        domain == FailureDomain.UPDATE -> null
        domain == FailureDomain.RUNTIME -> "START"
        cause == FailureCause.COMPATIBILITY -> null
        operation == "initialize" -> "INIT"
        operation == "health" -> "HEALTH"
        operation == "background-probe" -> "BACKGROUND"
        operation == "component-tools" -> "TOOLS"
        operation == "component-uninstall-ubuntu" -> "UBUNTU_INSTALL"
        operation == "component-uninstall-node" -> "NODE_INSTALL"
        operation == "component-uninstall-dsh" -> "DSH_REINSTALL"
        componentId == "ubuntu" -> "UBUNTU_INSTALL"
        componentId == "node" -> "NODE_INSTALL"
        else -> null
    }

    private fun action(kind: FailureActionKind, label: String) = FailureAction(kind, label)
}

/**
 * 恢复后隐藏旧错误：即使状态里还留着上一次失败，只要服务重新运行、
 * 环境已就绪或更新已回到可用状态，界面就不再显示。
 */
/** 用户关闭顶部提示后，同一条失败不再重复出现；新的失败时间会重新显示。 */
fun AppFailure?.afterDismiss(dismissedKey: String?): AppFailure? =
    this?.takeIf { it.stableKey != dismissedKey }

fun AppFailure?.visibleIn(snapshot: Snapshot): AppFailure? {
    if (this == null) return null
    // 正在操作或仍有错误原文时，新的失败优先展示。
    if (snapshot.error.isNotBlank() || snapshot.operation != null) return this
    return when (domain) {
        FailureDomain.RUNTIME -> takeUnless { snapshot.service == ServiceState.RUNNING }
        FailureDomain.INSTALL -> takeUnless { snapshot.environment == EnvironmentState.READY }
        FailureDomain.UPDATE -> takeUnless {
            snapshot.update in setOf(
                UpdateState.UNCHECKED, UpdateState.AVAILABLE, UpdateState.CURRENT,
                UpdateState.SUCCESS, UpdateState.RESTORED_PENDING_CHECK
            )
        }
        FailureDomain.FILE, FailureDomain.UNKNOWN -> this
    }
}
