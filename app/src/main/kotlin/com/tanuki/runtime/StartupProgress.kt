package com.tanuki.runtime

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/** 阶段只由真实调用推进，不代表整体百分比。 */
enum class StartupStage(val label: String) {
    QUEUED("等待运行环境操作完成"),
    CHECKING("检查启动条件"),
    PNPM("准备插件管理工具"),
    WORKSPACE("检查工作区支持"),
    COMPATIBILITY("检查 Node.js 与 DSH 兼容性"),
    TOOLS("准备启动支持"),
    PROCESS("创建 DSH 进程"),
    OUTPUT("等待 DSH 官方就绪信息"),
    ENDPOINT("发布本地连接端点"),
    HTTP("检查本地 HTTP 服务"),
    READY("DSH 本机服务已就绪"),
}

enum class StartupOutcome { ACTIVE, READY, FAILED, CANCELLED }
data class StartupStageTiming(val stage: StartupStage, val durationMillis: Long)
data class StartupProgress(
    val attemptId: Long,
    val stage: StartupStage,
    val startedAtMillis: Long,
    val stageStartedAtMillis: Long,
    val finishedAtMillis: Long? = null,
    val completedStages: List<StartupStageTiming> = emptyList(),
    val outcome: StartupOutcome = StartupOutcome.ACTIVE,
) {
    fun elapsedMillis(nowMillis: Long): Long = ((finishedAtMillis ?: nowMillis) - startedAtMillis).coerceAtLeast(0)
    fun stageElapsedMillis(nowMillis: Long): Long = ((finishedAtMillis ?: nowMillis) - stageStartedAtMillis).coerceAtLeast(0)
}

/** 单次启动的有界计时；串行发布，避免就绪行与等待协程相互覆盖。 */
internal class StartupTrace(
    attemptId: Long,
    private val clock: () -> Long,
    private val publish: (StartupProgress) -> Unit,
) {
    private val started = clock()
    private var progress = StartupProgress(attemptId, StartupStage.QUEUED, started, started)
    @Synchronized fun snapshot(): StartupProgress = progress

    @Synchronized fun phase(stage: StartupStage) {
        if (progress.outcome != StartupOutcome.ACTIVE || stage.ordinal <= progress.stage.ordinal) return
        val now = clock().coerceAtLeast(progress.stageStartedAtMillis)
        progress = progress.copy(stage = stage, stageStartedAtMillis = now,
            completedStages = progress.completedStages + StartupStageTiming(progress.stage, now - progress.stageStartedAtMillis))
        publish(progress)
    }

    @Synchronized fun finish(outcome: StartupOutcome) {
        require(outcome != StartupOutcome.ACTIVE)
        if (progress.outcome != StartupOutcome.ACTIVE) return
        if (outcome == StartupOutcome.READY) phase(StartupStage.READY)
        progress = progress.copy(finishedAtMillis = clock().coerceAtLeast(progress.stageStartedAtMillis), outcome = outcome)
        publish(progress)
    }
}

/** 日志采集失败立即传播；期限仍同时覆盖官方就绪行与本地 HTTP 存活。 */
internal suspend fun awaitStartupReady(
    completion: Deferred<CommandResult>,
    alive: () -> Boolean,
    endpointPublished: () -> Boolean,
    httpReady: suspend () -> Boolean,
    onHttpWait: () -> Unit = {},
    checkEndpoint: () -> Unit = {},
    timeoutMillis: Long = 180_000L,
    pollMillis: Long = 200L,
) = withTimeout(timeoutMillis) {
    suspend fun requireProcess() {
        if (completion.isCompleted) {
            val result = completion.await()
            error("DSH 在就绪检查完成前结束 exit=${result.exitCode}")
        }
        check(alive()) { "DSH 在就绪检查完成前退出" }
        checkEndpoint()
    }
    while (!endpointPublished()) {
        requireProcess()
        delay(pollMillis)
    }
    onHttpWait()
    while (true) {
        requireProcess()
        if (httpReady()) break
        delay(pollMillis)
    }
}
