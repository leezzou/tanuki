package com.tanuki

import com.tanuki.runtime.FailureRetry
import com.tanuki.runtime.OperationRetry
import com.tanuki.runtime.RuntimeArtifact
import com.tanuki.runtime.RuntimeCatalog
import com.tanuki.runtime.RuntimeComponent
import com.tanuki.runtime.operationRetryAction
import org.junit.Assert.*
import org.junit.Test

class OperationRetryTest {
    @Test fun `失败后已登记回调只执行一次`() {
        var calls = 0
        val retry = OperationRetry()
        val id = retry.begin { calls++ }
        assertFalse(retry.retry(id))
        assertTrue(retry.fail(id))
        assertTrue(retry.retry(id))
        assertFalse(retry.retry(id))
        assertEquals(1, calls)
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

    @Test fun `Node安装失败后改选版本重试仍恢复原版本`() =
        assertVersionRestored(RuntimeComponent.NODE, force = false)

    @Test fun `Node重装失败后改选版本重试仍恢复原版本和force`() =
        assertVersionRestored(RuntimeComponent.NODE, force = true)

    @Test fun `Ubuntu安装失败后改选版本重试仍恢复原版本`() =
        assertVersionRestored(RuntimeComponent.UBUNTU, force = false)

    @Test fun `Ubuntu重装失败后改选版本重试仍恢复原版本和force`() =
        assertVersionRestored(RuntimeComponent.UBUNTU, force = true)

    @Test fun `卸载切换和无组件上下文的操作仍重放原任务`() {
        val requests = listOf(null, FailureRetry("NODE_UNINSTALL"), FailureRetry("UBUNTU_UNINSTALL"),
            FailureRetry("DSH_UNINSTALL", "1.2.3"), FailureRetry("DSH_SWITCH", "1.2.4"))
        for (request in requests) {
            var replays = 0
            val retry = OperationRetry()
            val id = retry.begin(operationRetryAction(request,
                installComponent = { _, _, _ -> fail("非组件安装不能进入安装入口") },
                replay = { replays++ },
            ))
            assertTrue(retry.fail(id))
            assertTrue(retry.retry(id))
            assertEquals(1, replays)
        }
    }

    private fun assertVersionRestored(component: RuntimeComponent, force: Boolean) {
        val original = RuntimeCatalog.entries(component)[0]
        val changed = RuntimeCatalog.entries(component)[1]
        var selected = original
        var failNext = true
        val attempts = mutableListOf<Triple<RuntimeComponent, Boolean, RuntimeArtifact>>()
        // 安装端口保留真实入口的顺序：有目标版本时先恢复选择，主体再读取当前 artifact。
        fun install(target: RuntimeComponent, reinstall: Boolean, version: String?) {
            assertEquals(component, target)
            if (version != null) selected = requireNotNull(RuntimeCatalog.find(target, version))
            attempts += Triple(target, reinstall, selected)
            if (failNext) {
                failNext = false
                error("合成安装失败")
            }
        }
        val retry = OperationRetry()
        val request = FailureRetry.component(component, force, selected.version)
        // 调用 RuntimeManager 实际使用的路由；原主体故意读可变选择，不能捕获常量来掩盖漂移。
        val id = retry.begin(operationRetryAction(request, ::install) { install(component, force, null) })
        try {
            install(component, force, null)
            fail("首次安装必须失败")
        } catch (expected: IllegalStateException) {
            assertEquals("合成安装失败", expected.message)
        }
        assertTrue(retry.fail(id))
        selected = changed
        assertTrue(retry.retry(id))
        assertEquals("重试必须先恢复失败任务版本", original, selected)
        assertEquals(listOf(Triple(component, force, original), Triple(component, force, original)), attempts)
        assertFalse("同一个失败编号不能再执行第二次", retry.retry(id))
    }
}
