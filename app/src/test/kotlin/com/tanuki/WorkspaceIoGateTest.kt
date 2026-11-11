package com.tanuki

import com.tanuki.storage.WorkspaceIoGate
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Test

class WorkspaceIoGateTest {
    @Test fun `安装占用事务时读取列表与预览无需等安装结束`() = runBlocking {
        val mutation = Mutex(locked = true)
        val gate = WorkspaceIoGate(mutation) { error("只读浏览不应执行写入检查") }
        try {
            assertEquals(listOf("项目"), withTimeout(2_000) { gate.withAccess(false) { listOf("项目") } })
            assertEquals("预览内容", withTimeout(2_000) { gate.withAccess(false) { "预览内容" } })
        } finally { mutation.unlock() }
    }

    @Test fun `安装时写入立即给出原因而不挂起整页`() = runBlocking {
        val mutation = Mutex(locked = true)
        val reason = "环境操作进行中，请稍后写入"
        val gate = WorkspaceIoGate(mutation) { error(reason) }
        var written = false
        try {
            val error = runCatching { withTimeout(2_000) { gate.withAccess(true) { written = true } } }.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertEquals(reason, error?.message)
            assertFalse(written)
        } finally { mutation.unlock() }
    }

    @Test fun `等待写入锁期间新开始的安装仍会阻止落盘`() = runBlocking {
        supervisorScope {
            val mutation = Mutex(locked = true)
            var installing = false
            var written = false
            val gate = WorkspaceIoGate(mutation) { check(!installing) { "请等待安装完成" } }
            val write = async(start = CoroutineStart.UNDISPATCHED) { gate.withAccess(true) { written = true } }
            installing = true
            mutation.unlock()
            assertTrue(runCatching { write.await() }.exceptionOrNull() is IllegalStateException)
            assertFalse(written)
        }
    }
}
