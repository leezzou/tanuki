package com.tanuki.runtime

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * 默认 8 路，连续成功后逐步升到 16；出现可重试故障时回落到 8。
 *
 * [capProvider] 由设备温控/省电策略提供外部上限。上限降低时，[NpmAdaptiveWorkerPool]
 * 只让多出的 worker 在完成当前归档后退休，不会中断已开始的下载。
 */
internal class NpmConcurrencyPolicy(
    private val initial: Int = 8,
    private val maximum: Int = 16,
    private val step: Int = 2,
    private val growAfter: Int = 8,
    private val capProvider: () -> Int = { Int.MAX_VALUE },
) {
    init {
        require(initial in 1..maximum && maximum <= 16 && step > 0 && growAfter > 0) { "并发策略参数无效" }
    }

    private var target = initial
    private var streak = 0

    @Synchronized fun current(): Int = minOf(target, capProvider().coerceAtLeast(1))

    @Synchronized fun recordSuccess() {
        streak++
        if (streak >= growAfter) {
            target = minOf(maximum, target + step)
            streak = 0
        }
    }

    @Synchronized fun recordFailure() {
        streak = 0
        target = maxOf(initial, target - step)
    }
}

/** 每个 host 独立限流，避免自适应并发把所有连接压到同一个镜像。 */
internal class NpmHostLimiter(val perHostLimit: Int = 8) {
    init { require(perHostLimit in 1..16) { "单 host 并发上限无效" } }

    private val permits = ConcurrentHashMap<String, Semaphore>()

    suspend fun <T> withPermit(host: String, block: suspend () -> T): T =
        permits.computeIfAbsent(host.lowercase()) { Semaphore(perHostLimit) }.withPermit { block() }
}

/** 无锁时国内镜像只生成候选；官方元数据必须逐项核验传递依赖版本、路径与 SRI。 */
internal object NpmMetadataPolicy {
    const val FETCH_TIMEOUT_MS = 15_000L
    const val COMMAND_TIMEOUT_MS = 180_000L

    val SOURCES = listOf(
        NpmArchiveSource("npmmirror", "npmmirror", "https://registry.npmmirror.com"),
        NpmArchiveSource("official", "npm 官方", NpmArchiveMapping.OFFICIAL_REGISTRY),
    )
}
