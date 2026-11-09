package com.tanuki

import com.tanuki.runtime.DshStartupConfig
import org.junit.Assert.*
import org.junit.Test

class DshStartupConfigTest {
    @Test fun defaultsEnableCacheAndUseThirtySecondTimeout() {
        val config = DshStartupConfig.fromEnvironment { null }
        assertTrue(config.enabled)
        assertEquals(30_000L, config.timeoutMs)
        assertEquals(
            listOf("TANUKI_DSH_PATH_CACHE=1", "TANUKI_DSH_PATH_CACHE_TIMEOUT_MS=30000"),
            config.environment(),
        )
    }

    @Test fun offValuesDisableCache() {
        for (value in listOf("0", "false", "off", "no", " OFF ")) {
            val config = DshStartupConfig.fromEnvironment { name ->
                if (name == "TANUKI_DSH_PATH_CACHE") value else null
            }
            assertFalse("value=$value", config.enabled)
            assertTrue(config.environment().contains("TANUKI_DSH_PATH_CACHE=0"))
        }
    }

    @Test fun explicitOnValuesKeepCacheEnabled() {
        for (value in listOf("1", "true", "on", "yes", "")) {
            val config = DshStartupConfig.fromEnvironment { name ->
                if (name == "TANUKI_DSH_PATH_CACHE") value else null
            }
            assertTrue("value=$value", config.enabled)
            assertTrue(config.environment().contains("TANUKI_DSH_PATH_CACHE=1"))
        }
    }

    @Test fun timeoutIsValidatedAndBounded() {
        fun timeout(value: String?) = DshStartupConfig.fromEnvironment { name ->
            if (name == "TANUKI_DSH_PATH_CACHE_TIMEOUT_MS") value else null
        }.timeoutMs

        assertEquals(500L, timeout("500"))
        assertEquals(0L, timeout("0"))
        assertEquals(180_000L, timeout("180000"))
        // 负数、过大值和非数字都回退默认值，不能把异常配置传给 guest。
        assertEquals(30_000L, timeout("-1"))
        assertEquals(30_000L, timeout("180001"))
        assertEquals(30_000L, timeout("abc"))
        assertEquals(30_000L, timeout(null))
    }

    @Test fun environmentFailureFallsBackToDefaults() {
        // 读取环境异常不能阻塞 DSH 启动；默认开启并使用 30 秒超时。
        val config = DshStartupConfig.fromEnvironment { throw SecurityException("denied") }
        assertTrue(config.enabled)
        assertEquals(30_000L, config.timeoutMs)
    }

    @Test fun environmentIsEmptyWhenShimIsNotBound() {
        val config = DshStartupConfig(enabled = false, timeoutMs = 1234L)
        assertTrue(config.environment(bound = false).isEmpty())
        assertEquals(
            listOf("TANUKI_DSH_PATH_CACHE=0", "TANUKI_DSH_PATH_CACHE_TIMEOUT_MS=1234"),
            config.environment(bound = true),
        )
    }
}
