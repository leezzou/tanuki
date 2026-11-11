package com.tanuki.storage

import com.tanuki.runtime.Snapshot
import com.tanuki.runtime.operationActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 常驻服务和当前文件事务不是安装；门禁只消费真实操作状态与更新任务。 */
internal fun environmentBlocksFileWrites(snapshot: Snapshot, updaterActive: Boolean): Boolean =
    snapshot.operationActive || updaterActive

/** 环境安装只阻止写入；目录和预览不等待安装事务。写入排队前与执行前均复核。 */
internal class WorkspaceIoGate(private val mutation: Mutex, private val requireIdle: () -> Unit) {
    private val files = Mutex()

    suspend fun <T> withAccess(writing: Boolean, block: suspend () -> T): T {
        if (!writing) return files.withLock { block() }
        requireIdle()
        return mutation.withLock {
            requireIdle()
            files.withLock { block() }
        }
    }
}
