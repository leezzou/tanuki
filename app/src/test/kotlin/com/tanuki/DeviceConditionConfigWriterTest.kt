package com.tanuki

import com.tanuki.power.DeviceConditionConfig
import com.tanuki.power.DeviceConditionConfigWriter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DeviceConditionConfigWriterTest {
    /**
     * 第一次保存暂停在写锁内时，用户又选了新阈值并完成第二次保存；
     * 释放第一次后最终落盘必须是最后一次选择，模拟重启读取不倒退。
     */
    @Test
    fun pausedOlderSaveCannotOverwriteLatestChoice() = runBlocking {
        var latest = DeviceConditionConfig(lowBatteryEnterPercent = 20)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CountDownLatch(1)
        val secondStarted = CompletableDeferred<Unit>()
        val writes = Collections.synchronizedList(mutableListOf<Int>())

        val writer = DeviceConditionConfigWriter(
            readLatest = { latest },
            write = { config ->
                writes += config.lowBatteryEnterPercent
                if (config.lowBatteryEnterPercent == 20) {
                    firstStarted.complete(Unit)
                    releaseFirst.await(5, TimeUnit.SECONDS)
                }
            },
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val first = scope.launch { writer.save() }
            firstStarted.await()

            latest = DeviceConditionConfig(lowBatteryEnterPercent = 25, lowBatteryExitPercent = 30)
            val second = scope.launch {
                secondStarted.complete(Unit)
                writer.save()
            }
            secondStarted.await()
            delay(150)
            assertEquals("第二次保存必须等待第一次释放写锁", listOf(20), writes.toList())

            releaseFirst.countDown()
            first.join()
            second.join()

            assertEquals(listOf(20, 25), writes.toList())
            // 模拟重启读取：最终落盘值就是最后一次用户选择。
            assertEquals(25, writes.last())
        } finally {
            scope.cancel()
        }
    }
}
