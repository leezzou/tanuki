package com.tanuki.runtime

import org.json.JSONObject

/** DSH 运行失败分类；分类只描述本次失败，不直接触发删除或恢复用户数据。 */
enum class FailureKind {
    /** 进程尚未创建，或启动前的前置命令失败。 */
    LAUNCH_FAILED,
    /** 已创建进程，但在官方就绪行出现前失败或超时。 */
    STARTUP_FAILED,
    /** 官方就绪行出现后 2 分钟内出现致命错误。 */
    CRASHED_EARLY,
    /** 官方就绪行出现并运行满 2 分钟后出现致命错误。 */
    CRASHED_LATE,
    /** 无 DSH 致命错误退出，或低内存/信号终止。 */
    KILLED,
    /** 输出或磁盘空间不足（ENOSPC、空间检查失败）。 */
    NO_SPACE
}

/** 启动方式：normal 使用用户配置；safe 使用独立最小救援配置；probation 试用 normal。 */
enum class RecoveryMode { NORMAL, SAFE, PROBATION }

/** 需要跨进程恢复的失败与安全模式状态；只含策略字段，不含日志、路径或凭据。 */
data class RuntimeRecoveryState(
    val mode: RecoveryMode = RecoveryMode.NORMAL,
    val lastFailure: FailureKind? = null,
    val lastFailureAtMillis: Long = 0L,
    val lastReadyAtMillis: Long = 0L,
    val startupFailureStreak: Int = 0,
    val restartAttempts: Int = 0,
    val safeModeEnteredAtMillis: Long = 0L,
    val probationStartedAtMillis: Long = 0L,
    val lastExitCode: Int? = null
)

/** 分类所需的运行证据；由 RuntimeManager 从启动、退出码和脱敏输出组装。 */
data class FailureEvidence(
    val launched: Boolean,
    val failedAtMillis: Long,
    val readyAtMillis: Long? = null,
    val exitCode: Int? = null,
    val output: String = "",
    val errorText: String = "",
    val timedOut: Boolean = false,
    val diskFreeBytes: Long? = null
)

/** 一次失败处理结果；autoRestart 为 false 时不会由策略自动拉起进程。 */
data class RecoveryDecision(
    val state: RuntimeRecoveryState,
    val autoRestart: Boolean,
    val restartDelayMillis: Long,
    val summary: String
)

/**
 * DSH 失败分类与安全模式/试用恢复的纯策略。
 *
 * 约束：
 * - 连续两次 STARTUP_FAILED/CRASHED_EARLY 才自动进入安全模式；
 * - 进入安全模式后只使用最小救援启动参数，不复用用户配置；
 * - 从安全模式显式试用 normal 时进入 probation，2 分钟内失败回到安全模式；
 * - KILLED/NO_SPACE 不自动重启，也不在 normal 模式下触发安全模式；
 * - 自动重启次数有上限，等待时间按 2s、4s、8s…… 指数退避并封顶。
 */
class RuntimeFailurePolicy(
    val earlyCrashWindowMillis: Long = 120_000L,
    val probationWindowMillis: Long = 120_000L,
    val safeModeThreshold: Int = 2,
    val maxRestartAttempts: Int = 3,
    val baseBackoffMillis: Long = 2_000L,
    val maxBackoffMillis: Long = 30_000L,
    val diskReserveBytes: Long = 64L * 1024 * 1024
) {
    init {
        require(earlyCrashWindowMillis > 0) { "早崩溃窗口必须为正" }
        require(probationWindowMillis > 0) { "试用窗口必须为正" }
        require(safeModeThreshold >= 1) { "安全模式阈值必须为正" }
        require(maxRestartAttempts >= 0) { "自动重启上限不能为负" }
        require(baseBackoffMillis >= 0 && maxBackoffMillis >= baseBackoffMillis) { "退避参数无效" }
        require(diskReserveBytes >= 0) { "磁盘保留空间不能为负" }
    }

    /** 按证据分类；NO_SPACE 与低内存/信号终止优先于通用致命错误。 */
    fun classify(evidence: FailureEvidence): FailureKind {
        val text = buildString {
            append(evidence.output)
            if (evidence.errorText.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append(evidence.errorText)
            }
        }
        val lowDisk = evidence.diskFreeBytes?.let { it in 0 until diskReserveBytes } == true
        if (looksLikeNoSpace(text) || lowDisk) return FailureKind.NO_SPACE
        if (!evidence.launched) return FailureKind.LAUNCH_FAILED
        val killed = looksLikeKilled(text, evidence.exitCode)
        val readyAt = evidence.readyAtMillis?.takeIf { it > 0L }
        if (readyAt == null) {
            if (killed) return FailureKind.KILLED
            return if (evidence.timedOut || hasFatalOutput(text)) FailureKind.STARTUP_FAILED else FailureKind.KILLED
        }
        if (killed) return FailureKind.KILLED
        if (!hasFatalOutput(text)) return FailureKind.KILLED
        return if (evidence.failedAtMillis - readyAt <= earlyCrashWindowMillis) FailureKind.CRASHED_EARLY
        else FailureKind.CRASHED_LATE
    }

    /** 用户手动启动：清空本轮自动重启次数；仍在试用时重新计时。 */
    fun prepareManualStart(state: RuntimeRecoveryState, nowMillis: Long): RuntimeRecoveryState {
        val current = settleExpiredProbation(state, nowMillis)
        return current.copy(
            restartAttempts = 0,
            lastReadyAtMillis = 0L,
            probationStartedAtMillis = if (current.mode == RecoveryMode.PROBATION) nowMillis else current.probationStartedAtMillis
        )
    }

    /** 自动重启：保留重启次数；试用已过窗口则先转为 normal。 */
    fun prepareAutomaticStart(state: RuntimeRecoveryState, nowMillis: Long): RuntimeRecoveryState =
        settleExpiredProbation(state, nowMillis).copy(lastReadyAtMillis = 0L)

    /** 就绪后记录时间；试用窗口从真正就绪开始计算，避免启动耗时吞掉试用期。 */
    fun onReady(state: RuntimeRecoveryState, nowMillis: Long): RuntimeRecoveryState {
        val adjusted = if (state.mode == RecoveryMode.PROBATION) state.copy(probationStartedAtMillis = nowMillis) else state
        return adjusted.copy(lastReadyAtMillis = nowMillis)
    }

    /** 稳定运行满窗口后清空启动失败计数；安全模式不会自动退出，需用户显式试用 normal。 */
    fun onStable(state: RuntimeRecoveryState, nowMillis: Long): RuntimeRecoveryState = when (state.mode) {
        RecoveryMode.NORMAL -> state.copy(startupFailureStreak = 0, restartAttempts = 0)
        RecoveryMode.PROBATION -> state.copy(mode = RecoveryMode.NORMAL, startupFailureStreak = 0, restartAttempts = 0, probationStartedAtMillis = 0L)
        RecoveryMode.SAFE -> state.copy(startupFailureStreak = 0, restartAttempts = 0)
    }

    /** 用户显式从安全模式进入试用；清空连续失败计数，保留最近失败用于界面说明。 */
    fun toProbation(state: RuntimeRecoveryState, nowMillis: Long): RuntimeRecoveryState = state.copy(
        mode = RecoveryMode.PROBATION,
        safeModeEnteredAtMillis = 0L,
        probationStartedAtMillis = nowMillis,
        restartAttempts = 0,
        startupFailureStreak = 0,
        lastReadyAtMillis = 0L
    )

    /** 用户显式退出试用，回到安全模式。 */
    fun toSafeByUser(state: RuntimeRecoveryState, nowMillis: Long): RuntimeRecoveryState = state.copy(
        mode = RecoveryMode.SAFE,
        safeModeEnteredAtMillis = nowMillis,
        probationStartedAtMillis = 0L,
        restartAttempts = 0,
        startupFailureStreak = 0,
        lastReadyAtMillis = 0L
    )

    /**
     * 处理一次失败。调用方需保证 state.mode 是本次启动实际使用的模式。
     * 返回的 state 必须持久化；summary 用于前台错误文案，不包含原始日志。
     */
    fun onFailure(state: RuntimeRecoveryState, kind: FailureKind, nowMillis: Long, exitCode: Int? = null): RecoveryDecision {
        val base = state.copy(lastFailure = kind, lastFailureAtMillis = nowMillis, lastExitCode = exitCode)
        return when (kind) {
            FailureKind.NO_SPACE, FailureKind.KILLED -> handleResourceFailure(base, kind, nowMillis)
            FailureKind.CRASHED_LATE -> handleLateCrash(base, nowMillis)
            FailureKind.LAUNCH_FAILED -> handleLaunchFailure(base, nowMillis)
            FailureKind.STARTUP_FAILED, FailureKind.CRASHED_EARLY -> handleStartupFailure(base, nowMillis)
        }
    }

    /** 进程重启后恢复状态：试用窗口内进程消失则回到安全模式；窗口已过则视为试用通过。 */
    fun afterProcessRestart(state: RuntimeRecoveryState, serviceWasActive: Boolean, nowMillis: Long): RuntimeRecoveryState {
        if (state.mode != RecoveryMode.PROBATION) {
            return if (serviceWasActive && state.mode == RecoveryMode.NORMAL) state.copy(restartAttempts = 0) else state
        }
        // 尚未出现就绪行时无法证明试用通过，进程消失一律按试用失败处理。
        val within = state.lastReadyAtMillis <= 0L || probationStartedAtMillis(state) == 0L ||
            nowMillis - probationStartedAtMillis(state) <= probationWindowMillis
        return when {
            serviceWasActive && within -> state.copy(
                mode = RecoveryMode.SAFE,
                safeModeEnteredAtMillis = nowMillis,
                probationStartedAtMillis = 0L,
                restartAttempts = 0,
                lastFailure = state.lastFailure ?: FailureKind.KILLED,
                lastFailureAtMillis = if (state.lastFailure == null) nowMillis else state.lastFailureAtMillis
            )
            !serviceWasActive && within -> state.copy(restartAttempts = 0, lastReadyAtMillis = 0L)
            else -> onStable(state, nowMillis)
        }
    }

    /** 当前是否处于试用窗口内；未就绪或时间戳缺失时保守视为仍在试用。 */
    fun inProbationWindow(state: RuntimeRecoveryState, nowMillis: Long): Boolean {
        if (state.mode != RecoveryMode.PROBATION) return false
        if (state.lastReadyAtMillis <= 0L) return true
        val started = probationStartedAtMillis(state)
        return started <= 0L || nowMillis - started <= probationWindowMillis
    }

    /** 后退避时间：2s、4s、8s…… 不超过 maxBackoffMillis。 */
    fun backoffMillis(attempt: Int): Long {
        if (attempt <= 0) return 0L
        var delay = baseBackoffMillis
        repeat(attempt - 1) {
            delay = if (delay >= maxBackoffMillis / 2) maxBackoffMillis else delay * 2
        }
        return delay.coerceAtMost(maxBackoffMillis)
    }

    /**
     * 正常启动参数保持既有官方 CLI 语义；安全模式使用独立 rescue profile。
     * 首次没有 rescue profile 时用官方 --from-default-profile web 初始化；
     * 之后直接 boot rescue，避免每次救援启动都删除上一次安全模式目录。
     */
    fun startArguments(version: String, safe: Boolean, rescueProfileExists: Boolean): List<String> {
        require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?"))) { "DSH 版本格式无效" }
        val entry = "/opt/engines/$version/node_modules/@deepseek-ai/dsh/lib/bin.js"
        val webArguments = listOf("--no-open", "--host", "127.0.0.1", "--port", "0")
        if (!safe) return listOf(entry, "web") + webArguments
        return buildList {
            add(entry)
            add("rescue")
            if (!rescueProfileExists) {
                add("--from-default-profile")
                add("web")
            }
            addAll(webArguments)
        }
    }

    /** 序列化为小状态文件；只写策略字段，便于进程重启后恢复安全模式。 */
    fun encode(state: RuntimeRecoveryState): String = JSONObject()
        .put("schema", SCHEMA)
        .put("mode", state.mode.name)
        .put("lastFailure", state.lastFailure?.name ?: JSONObject.NULL)
        .put("lastFailureAtMillis", state.lastFailureAtMillis)
        .put("lastReadyAtMillis", state.lastReadyAtMillis)
        .put("startupFailureStreak", state.startupFailureStreak)
        .put("restartAttempts", state.restartAttempts)
        .put("safeModeEnteredAtMillis", state.safeModeEnteredAtMillis)
        .put("probationStartedAtMillis", state.probationStartedAtMillis)
        .put("lastExitCode", state.lastExitCode ?: JSONObject.NULL)
        .toString()

    /** 解码失败或字段非法时回退到 normal，绝不据此删除数据或自动重启。 */
    fun decode(raw: String?): RuntimeRecoveryState {
        if (raw.isNullOrBlank()) return RuntimeRecoveryState()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return RuntimeRecoveryState()
        if (json.optInt("schema", -1) != SCHEMA) return RuntimeRecoveryState()
        val mode = runCatching { RecoveryMode.valueOf(json.optString("mode", RecoveryMode.NORMAL.name)) }.getOrDefault(RecoveryMode.NORMAL)
        val failure = json.optString("lastFailure").takeIf { it.isNotBlank() && it != "null" }?.let {
            runCatching { FailureKind.valueOf(it) }.getOrNull()
        }
        val exitCode = if (json.has("lastExitCode") && !json.isNull("lastExitCode")) json.optInt("lastExitCode") else null
        fun nonNegative(name: String): Long = json.optLong(name, 0L).coerceAtLeast(0L)
        return RuntimeRecoveryState(
            mode = mode,
            lastFailure = failure,
            lastFailureAtMillis = nonNegative("lastFailureAtMillis"),
            lastReadyAtMillis = nonNegative("lastReadyAtMillis"),
            startupFailureStreak = json.optInt("startupFailureStreak", 0).coerceAtLeast(0),
            restartAttempts = json.optInt("restartAttempts", 0).coerceAtLeast(0),
            safeModeEnteredAtMillis = nonNegative("safeModeEnteredAtMillis"),
            probationStartedAtMillis = nonNegative("probationStartedAtMillis"),
            lastExitCode = exitCode
        )
    }

    private fun handleStartupFailure(state: RuntimeRecoveryState, nowMillis: Long): RecoveryDecision {
        if (state.mode == RecoveryMode.SAFE) return safeModeFailed(state, "安全模式启动失败")
        if (inProbationWindow(state, nowMillis)) return enterSafeAfterFailure(state, nowMillis, "试用正常模式未通过")
        val streak = state.startupFailureStreak + 1
        if (streak >= safeModeThreshold) {
            return enterSafeAfterFailure(state.copy(startupFailureStreak = streak), nowMillis, "连续 $streak 次启动失败")
        }
        return restartOrStop(
            state.copy(
                mode = RecoveryMode.NORMAL,
                startupFailureStreak = streak,
                probationStartedAtMillis = 0L
            ),
            nowMillis,
            "启动未就绪或就绪后早期崩溃"
        )
    }

    private fun handleLaunchFailure(state: RuntimeRecoveryState, nowMillis: Long): RecoveryDecision {
        if (state.mode == RecoveryMode.SAFE) return safeModeFailed(state, "安全模式启动命令失败")
        if (inProbationWindow(state, nowMillis)) return enterSafeAfterFailure(state, nowMillis, "试用正常模式启动失败")
        // 启动命令失败不是 startupFailed/crashedEarly，不能累计后两类失败的连续计数。
        return restartOrStop(
            state.copy(mode = RecoveryMode.NORMAL, probationStartedAtMillis = 0L, startupFailureStreak = 0),
            nowMillis,
            "启动命令失败"
        )
    }

    private fun handleLateCrash(state: RuntimeRecoveryState, nowMillis: Long): RecoveryDecision {
        val stable = onStable(state, nowMillis).copy(
            lastFailure = state.lastFailure,
            lastFailureAtMillis = state.lastFailureAtMillis,
            lastExitCode = state.lastExitCode
        )
        // 安全模式运行稳定后崩溃仍重启安全模式，不借自动重启切回用户配置。
        val next = if (state.mode == RecoveryMode.SAFE) stable.copy(mode = RecoveryMode.SAFE) else stable.copy(mode = RecoveryMode.NORMAL)
        return restartOrStop(next, nowMillis, "DSH 稳定运行后崩溃")
    }

    private fun handleResourceFailure(state: RuntimeRecoveryState, kind: FailureKind, nowMillis: Long): RecoveryDecision {
        val reason = if (kind == FailureKind.NO_SPACE) "输出或磁盘空间不足" else "DSH 进程被终止或低内存"
        if (inProbationWindow(state, nowMillis)) {
            val safe = state.copy(
                mode = RecoveryMode.SAFE,
                safeModeEnteredAtMillis = nowMillis,
                probationStartedAtMillis = 0L,
                restartAttempts = 0,
                startupFailureStreak = 0
            )
            return RecoveryDecision(
                safe,
                autoRestart = false,
                restartDelayMillis = 0L,
                summary = "试用正常模式失败（$reason）；已回到安全模式，此类失败不会自动重启。请先检查存储或内存后手动启动"
            )
        }
        val settled = if (state.mode == RecoveryMode.PROBATION) onStable(state, nowMillis).copy(
            lastFailure = state.lastFailure,
            lastFailureAtMillis = state.lastFailureAtMillis,
            lastExitCode = state.lastExitCode
        ) else state
        return RecoveryDecision(
            settled.copy(restartAttempts = 0, startupFailureStreak = 0),
            autoRestart = false,
            restartDelayMillis = 0L,
            summary = "$reason；已停止自动重启。请先检查存储或内存，确认后再手动启动"
        )
    }

    private fun enterSafeAfterFailure(state: RuntimeRecoveryState, nowMillis: Long, reason: String): RecoveryDecision {
        val safe = state.copy(
            mode = RecoveryMode.SAFE,
            safeModeEnteredAtMillis = nowMillis,
            probationStartedAtMillis = 0L,
            restartAttempts = 1,
            startupFailureStreak = maxOf(state.startupFailureStreak, 1)
        )
        return RecoveryDecision(
            safe,
            autoRestart = true,
            restartDelayMillis = baseBackoffMillis,
            summary = "$reason；已进入安全模式，将用最小救援配置重启（不加载你的插件和配置），项目、会话和程序均不会删除"
        )
    }

    private fun safeModeFailed(state: RuntimeRecoveryState, reason: String): RecoveryDecision = RecoveryDecision(
        state.copy(restartAttempts = 0, probationStartedAtMillis = 0L),
        autoRestart = false,
        restartDelayMillis = 0L,
        summary = "$reason；安全模式已停止自动重启。请检查运行环境后手动重试，或从组件维护入口重新检查"
    )

    private fun restartOrStop(state: RuntimeRecoveryState, nowMillis: Long, reason: String): RecoveryDecision {
        val attempt = state.restartAttempts + 1
        if (attempt > maxRestartAttempts) {
            return RecoveryDecision(
                state.copy(restartAttempts = maxRestartAttempts),
                autoRestart = false,
                restartDelayMillis = 0L,
                summary = "$reason；自动重启已达上限 $maxRestartAttempts 次，已停止自动重启。请检查环境后手动启动"
            )
        }
        val delay = backoffMillis(attempt)
        return RecoveryDecision(
            state.copy(restartAttempts = attempt),
            autoRestart = true,
            restartDelayMillis = delay,
            summary = "$reason；将在 ${delay / 1000} 秒后自动重启（$attempt/$maxRestartAttempts 次），已中断任务不会自动重放"
        )
    }

    private fun settleExpiredProbation(state: RuntimeRecoveryState, nowMillis: Long): RuntimeRecoveryState {
        if (state.mode != RecoveryMode.PROBATION) return state
        val started = probationStartedAtMillis(state)
        if (started > 0L && nowMillis - started >= probationWindowMillis) return onStable(state, nowMillis)
        return state
    }

    private fun probationStartedAtMillis(state: RuntimeRecoveryState): Long =
        state.probationStartedAtMillis.coerceAtLeast(0L)

    private fun hasFatalOutput(text: String): Boolean = text.isNotBlank() && fatalPatterns.any { it.containsMatchIn(text) }

    private fun looksLikeNoSpace(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        return noSpaceMarkers.any { lower.contains(it) }
    }

    private fun looksLikeKilled(text: String, exitCode: Int?): Boolean {
        if (exitCode == 9 || exitCode == 137 || exitCode == 143) return true
        if (text.isBlank()) return false
        val lower = text.lowercase()
        return killedMarkers.any { lower.contains(it) }
    }

    private companion object {
        const val SCHEMA = 1

        val fatalPatterns = listOf(
            Regex("(?m)^\\s*(?:FATAL|FATAL ERROR)\\b"),
            Regex("(?m)^\\s*(?:Uncaught|UnhandledPromiseRejection)\\w*\\b"),
            Regex("(?m)^\\s*(?:Error|TypeError|SyntaxError|ReferenceError|RangeError|StartupError):"),
            Regex("(?m)^\\s*(?:dsh:\\s*)?error:"),
            Regex("(?m)\\b(?:StartupError|startup failed|failed to start|Cannot find module|ERR_MODULE_NOT_FOUND|EADDRINUSE|EACCES)\\b")
        )

        val noSpaceMarkers = listOf(
            "enospc", "no space left", "not enough space", "disk full", "quota exceeded",
            "out of disk", "insufficient space", "空间不足", "磁盘已满", "磁盘空间不足"
        )

        val killedMarkers = listOf(
            "sigkill", "signal 9", "signal: killed", "outofmemory", "out of memory",
            "oom-kill", "oom kill", "low memory", "enomem", "cannot allocate memory", "killed"
        )
    }
}
