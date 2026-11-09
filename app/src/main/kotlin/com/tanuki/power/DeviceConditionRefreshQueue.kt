package com.tanuki.power

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * 设备状态刷新请求的后台合并队列。
 *
 * [request] 只向 conflated channel 投递一次请求，不做采样、不加锁、不写盘，供主线程的
 * 生命周期/电池/热回调调用；[start] 启动的后台 collector 串行执行 [worker]，
 * 连续请求在 worker 忙时合并为一次，避免热回调或电池广播造成主线程磁盘 I/O 和写盘风暴。
 */
internal class DeviceConditionRefreshQueue(
    private val scope: CoroutineScope,
    private val worker: () -> Unit,
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)

    /** 非阻塞投递；调用方可以在主线程安全调用。 */
    fun request() {
        requests.trySend(Unit)
    }

    /** 启动唯一后台 collector；重复调用不会创建第二个消费者。 */
    fun start() {
        scope.launch {
            for (ignored in requests) worker()
        }
    }
}
