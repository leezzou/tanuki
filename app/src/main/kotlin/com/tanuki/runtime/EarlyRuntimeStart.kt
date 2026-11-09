package com.tanuki.runtime

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 启动首帧前的运行时提前启动判定。
 *
 * 只复用既有 [RuntimeManager.start] / 前台服务链路，不另建进程、不重复启动：
 * - 环境已安装且就绪；
 * - 服务处于干净的 STOPPED（STARTING/RUNNING/STOPPING/FAILED/INTERRUPTED 都跳过）；
 * - 没有安装、下载、恢复等互斥操作，也没有存活子进程；
 * - 没有待恢复的版本事务；
 * - 用户没有关闭“启动应用时自动运行 DSH”。
 */
object EarlyRuntimeStart {
    /** 与 MainActivity 设置页共享的偏好键。 */
    const val PREFERENCE = "earlyRuntimeStart"

    data class Conditions(
        val enabled: Boolean,
        val environment: EnvironmentState,
        val service: ServiceState,
        val update: UpdateState,
        val operationActive: Boolean,
        val liveChild: Boolean,
        val stopping: Boolean,
    )

    fun shouldStart(conditions: Conditions): Boolean =
        conditions.enabled &&
            conditions.environment == EnvironmentState.READY &&
            conditions.service == ServiceState.STOPPED &&
            conditions.update != UpdateState.RECOVERY_REQUIRED &&
            !conditions.operationActive &&
            !conditions.liveChild &&
            !conditions.stopping

    /**
     * 只对 Launcher 冷启动提前拉起。
     *
     * 仪器测试、通知和内部显式 Component 启动通常没有 MAIN/LAUNCHER，
     * 不应因为测试环境已安装 DSH 而意外启动服务。
     */
    fun launchedFromLauncher(action: String?, hasLauncherCategory: Boolean): Boolean =
        action == "android.intent.action.MAIN" && hasLauncherCategory
}

/**
 * 每个应用进程只允许自动发起一次启动；Activity 重建、重复回调不会重复拉起。
 * 发起失败时释放名额，仍可由用户手动启动。
 */
class EarlyStartGate {
    private val requested = AtomicBoolean(false)

    fun request(conditions: EarlyRuntimeStart.Conditions, start: () -> Unit): Boolean {
        if (!EarlyRuntimeStart.shouldStart(conditions)) return false
        if (!requested.compareAndSet(false, true)) return false
        return try {
            start()
            true
        } catch (_: Throwable) {
            requested.set(false)
            false
        }
    }
}
