package com.tanuki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.tanuki.runtime.Snapshot
import com.tanuki.runtime.operationActive

/** 文件页负责定位唯一的进度浮层；运行时和文件任务共用抽屉，避开查看器操作栏。 */
internal val LocalFileOperationOverlay = compositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }

internal enum class FileProgressHost { NONE, FILE_TRANSFER, RUNTIME }

internal fun fileProgressHost(snapshot: Snapshot, hasTransfers: Boolean): FileProgressHost = when {
    snapshot.operationActive || operationFailure(snapshot) != null -> FileProgressHost.RUNTIME
    hasTransfers -> FileProgressHost.FILE_TRANSFER
    else -> FileProgressHost.NONE
}

internal const val FILE_INSTALL_WRITE_NOTICE = "环境操作正在进行，文件可继续浏览；请等待操作完成后再写入。"
