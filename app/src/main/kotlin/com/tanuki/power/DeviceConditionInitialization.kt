package com.tanuki.power

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 设备状态监控的一次性初始化屏障。
 *
 * 冷启动时设置与磁盘状态需要在后台读取；WorkManager 可能同时唤醒 Worker。
 * 所有运行期入口先等待 [awaitReady]，保证 restore 完成后才开始评估和写盘，
 * 避免 Worker 用默认状态覆盖磁盘上尚未读取的待办，或 restore 覆盖 Worker 刚登记的待办。
 */
internal class DeviceConditionInitialization(
    private val initialize: suspend () -> Unit,
) {
    private val ready = CompletableDeferred<Unit>()
    private val started = AtomicBoolean(false)

    /** 只启动一次初始化；初始化失败也必须释放等待者，避免 Worker 永久挂起。 */
    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            try {
                initialize()
            } catch (_: Throwable) {
                // 初始化失败时仍进入运行期；后续采样会按可用信号重新建立状态。
            } finally {
                ready.complete(Unit)
            }
        }
    }

    suspend fun awaitReady() {
        ready.await()
    }
}
