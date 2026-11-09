package com.tanuki.power

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 设备设置的单一后台写入器。
 *
 * 一次保存的“读取最新配置 -> 原子写入”必须在同一互斥区内完成，避免旧快照在并发保存中
 * 后写覆盖新选择。调用方只能在后台协程中调用 [save]，主线程不等待磁盘。
 */
internal class DeviceConditionConfigWriter(
    private val readLatest: () -> DeviceConditionConfig,
    private val write: (DeviceConditionConfig) -> Unit,
) {
    private val mutex = Mutex()

    suspend fun save() {
        mutex.withLock {
            // 必须在获得写锁后再读取，保证后到的保存总能拿到最新值。
            write(readLatest())
        }
    }
}
