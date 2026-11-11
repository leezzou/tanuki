package com.tanuki

import com.tanuki.runtime.*
import org.junit.Assert.*
import org.junit.Test

class RuntimeVersionSelectionTest {
    @Test fun `实际选择从A切到B后旧重装回调不能安装B`() {
        for (component in RuntimeComponent.entries) {
            val versions = RuntimeCatalog.entries(component)
            var selected = versions[0]
            val retry = OperationRetry()
            val installed = mutableListOf<Pair<String, Boolean>>()
            var old = 0L
            val selection = RuntimeVersionSelection({ selected }, { _, version ->
                assertFalse("保存新选择前，旧回调就必须失效", retry.retry(old))
                selected = RuntimeCatalog.find(component, version)!!
            }, retry::invalidate)
            // 与组件安装相同，回调到执行时才读所选目标，并保留重装标志。
            old = retry.begin { installed += selected.version to true }
            retry.fail(old)
            selection.select(component, versions[1].version)
            assertEquals(versions[1], selected)
            assertFalse("旧任务不能改为重装新版本", retry.retry(old))
            assertTrue(installed.isEmpty())
            val explicit = retry.begin { installed += selected.version to false }
            retry.fail(explicit)
            assertTrue(retry.retry(explicit))
            assertEquals(listOf(versions[1].version to false), installed)
        }
    }

    @Test fun `重新选择同一版本不破坏本次重试且未知版本不落盘`() {
        val artifact = RuntimeCatalog.node.first()
        val retry = OperationRetry()
        var persisted = ""
        var called = false
        val selection = RuntimeVersionSelection({ artifact }, { _, version -> persisted = version }, retry::invalidate)
        val id = retry.begin { called = true }
        retry.fail(id)
        selection.select(RuntimeComponent.NODE, artifact.version)
        assertEquals(artifact.version, persisted)
        assertTrue(retry.retry(id))
        assertTrue(called)
        try { selection.select(RuntimeComponent.NODE, "unknown"); fail("未知版本必须被拒绝") }
        catch (_: IllegalArgumentException) { assertEquals(artifact.version, persisted) }
    }
}
