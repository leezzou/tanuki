package com.tanuki

import com.tanuki.runtime.OperationRetry
import org.junit.Assert.*
import org.junit.Test

class OperationRetryTest {
    @Test fun `重试只执行原操作一次且保存其版本参数`() {
        val calls = mutableListOf<String>()
        val retry = OperationRetry()
        val version = "1.2.3"
        val id = retry.begin { calls += "install:$version" }
        assertFalse(retry.retry(id))
        assertTrue(retry.fail(id))
        assertTrue(retry.retry(id))
        assertFalse(retry.retry(id))
        assertEquals(listOf("install:1.2.3"), calls)
    }

    @Test fun `新操作和成功结果使旧重试入口失效`() {
        val retry = OperationRetry()
        val calls = mutableListOf<String>()
        val first = retry.begin { calls += "first" }
        retry.fail(first)
        val second = retry.begin { calls += "second" }
        assertFalse(retry.fail(first))
        assertFalse(retry.retry(first))
        retry.complete(second)
        assertFalse(retry.fail(second))
        assertFalse(retry.retry(second))
        assertTrue(calls.isEmpty())
    }

    @Test fun `耗时只使用本次操作的单调时钟`() {
        var now = 1_000L
        val retry = OperationRetry { now }
        val first = retry.begin {}
        now = 6_000
        assertEquals(5_000L, retry.elapsedMillis(first))
        val second = retry.begin {}
        now = 7_000
        assertEquals(0L, retry.elapsedMillis(first))
        assertEquals(1_000L, retry.elapsedMillis(second))
    }
}
