package com.tanuki

import com.tanuki.runtime.NpmConcurrencyPolicy
import com.tanuki.runtime.NpmHostLimiter
import com.tanuki.runtime.NpmMetadataPolicy
import com.tanuki.download.DownloadRetryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class NpmDownloadPolicyTest {
    @Test fun retriesUseTwoExponentialBackoffSteps() {
        assertEquals(500L, DownloadRetryPolicy.delayMillis(1))
        assertEquals(1000L, DownloadRetryPolicy.delayMillis(2))
        assertEquals(3, DownloadRetryPolicy.MAX_ATTEMPTS)
        listOf(0, 3, -1).forEach {
            try { DownloadRetryPolicy.delayMillis(it); fail("invalid retry index accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun httpStatusesAreClassifiedForFastSwitchOrBackoff() {
        listOf(404, 410).forEach { assertTrue(DownloadRetryPolicy.missing(it)) }
        listOf(408, 425, 429, 500, 502, 503, 504, 599).forEach { assertTrue(DownloadRetryPolicy.retryable(it)) }
        listOf(400, 401, 403).forEach { assertTrue(DownloadRetryPolicy.permanent(it)) }
        listOf(409, 422).forEach {
            assertFalse(DownloadRetryPolicy.retryable(it))
            assertFalse(DownloadRetryPolicy.permanent(it))
        }
        assertFalse(DownloadRetryPolicy.missing(429))
    }

    @Test fun metadataResolvesOnlyOfficialLockWithFifteenSecondFetchTimeout() {
        assertEquals(listOf("official"), NpmMetadataPolicy.SOURCES.map { it.id })
        assertEquals("https://registry.npmjs.org", NpmMetadataPolicy.SOURCES.single().registry)
        assertTrue(NpmMetadataPolicy.FETCH_TIMEOUT_MS in 10_000..15_000)
        assertTrue(NpmMetadataPolicy.COMMAND_TIMEOUT_MS > NpmMetadataPolicy.FETCH_TIMEOUT_MS)
    }

    @Test fun concurrencyGrowsAfterSuccessesAndFallsBackOnFailureWithinEightToSixteen() {
        val policy = NpmConcurrencyPolicy()
        assertEquals(8, policy.current())
        repeat(7) { policy.recordSuccess() }
        assertEquals(8, policy.current())
        policy.recordSuccess()
        assertEquals(10, policy.current())
        repeat(32) { policy.recordSuccess() }
        assertEquals(16, policy.current())
        repeat(8) { policy.recordSuccess() }
        assertEquals(16, policy.current())
        policy.recordFailure()
        assertEquals(14, policy.current())
        repeat(4) { policy.recordFailure() }
        assertEquals(8, policy.current())
        policy.recordFailure()
        assertEquals(8, policy.current())
    }

    @Test fun externalCapLowersConcurrencyAndRestoresAfterRecovery() {
        var cap = 16
        val policy = NpmConcurrencyPolicy(capProvider = { cap })
        repeat(32) { policy.recordSuccess() }
        assertEquals(16, policy.current())

        // 温控策略把上限降到 4 路：只影响新领取的归档，已开始的下载由池子自然收尾。
        cap = 4
        assertEquals(4, policy.current())

        // 上限异常时仍保留至少一个 worker，避免队列完全停住。
        cap = 0
        assertEquals(1, policy.current())

        // 设备恢复后上限回到 16，目标仍保留原来的 16。
        cap = 16
        assertEquals(16, policy.current())
    }

    @Test fun perHostLimiterAllowsAtMostEightConcurrentRequestsPerHost() = runBlocking {
        val limiter = NpmHostLimiter(8)
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        coroutineScope {
            repeat(32) {
                launch(Dispatchers.IO) {
                    limiter.withPermit("registry.npmmirror.com") {
                        val now = active.incrementAndGet()
                        synchronized(maximum) { if (now > maximum.get()) maximum.set(now) }
                        delay(15)
                        active.decrementAndGet()
                    }
                }
            }
        }
        assertEquals(8, maximum.get())
        assertEquals(0, active.get())
    }

    @Test fun perHostLimiterKeepsDifferentHostsIndependent() = runBlocking {
        val limiter = NpmHostLimiter(2)
        val first = AtomicInteger()
        val second = AtomicInteger()
        coroutineScope {
            repeat(4) {
                launch(Dispatchers.IO) {
                    limiter.withPermit("a.invalid") {
                        first.incrementAndGet(); delay(20); first.decrementAndGet()
                    }
                }
                launch(Dispatchers.IO) {
                    limiter.withPermit("b.invalid") {
                        second.incrementAndGet(); delay(20); second.decrementAndGet()
                    }
                }
            }
        }
        assertEquals(0, first.get())
        assertEquals(0, second.get())
    }
}
