package com.tanuki.runtime

/** 运行实例只检查本机HTTP；环境检查只用于停止状态，不混入安装/迁移命令。 */
internal suspend fun executeRuntimeHealthCheck(
    running: Boolean,
    checkRunning: suspend () -> Unit,
    checkStopped: suspend () -> Unit,
) {
    if (running) checkRunning() else checkStopped()
}

/** 探测可在锁外等待网络，但结果发布必须与停止/替换实例共用同步边界。 */
internal suspend fun probeRunningService(
    owner: Any,
    expectedGeneration: Long,
    generation: () -> Long,
    currentInstance: () -> Boolean,
    probe: suspend () -> Boolean,
    publish: (Boolean) -> Unit,
) {
    if (!synchronized(owner) { generation() == expectedGeneration && currentInstance() }) return
    val healthy = probe()
    synchronized(owner) {
        if (generation() == expectedGeneration && currentInstance()) publish(healthy)
    }
}
