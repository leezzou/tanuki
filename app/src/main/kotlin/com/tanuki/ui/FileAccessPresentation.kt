package com.tanuki.ui

import com.tanuki.storage.FileLocation

/** 文件页权限状态；只描述界面可见语义，不改变实际授权。 */
internal enum class FileAccessMode { READ_ONLY, WRITABLE }

/**
 * 权限 chip 与底部 sheet 的展示状态。
 *
 * 写入仍由 WorkspaceFileManager 和 PhoneFileService 校验；这里只把“当前是否可写、
 * 为什么不可写、能执行哪种授权操作”集中成可在 JVM 回归的纯状态。
 */
internal data class FileAccessState(
    val mode: FileAccessMode,
    val label: String,
    val summary: String,
    val detail: String,
    val needsStorageGrant: Boolean = false,
    val canGrantPhoneWrite: Boolean = false,
    val canRevokePhoneWrite: Boolean = false,
    val canStopRuntime: Boolean = false,
)

internal object FileAccessPresentation {
    fun state(
        location: FileLocation,
        runtimeRunning: Boolean,
        phoneStorageGranted: Boolean,
        phoneWriteAllowed: Boolean,
        phoneWritingAvailable: Boolean,
        environmentBusy: Boolean = false,
    ): FileAccessState = when (location) {
        FileLocation.WORKSPACE -> {
            // DSH 运行时工作区修改被暂停；chip 必须反映“当前不可写”，而不是永久能力。
            if (environmentBusy) FileAccessState(
                mode = FileAccessMode.READ_ONLY,
                label = "只读",
                summary = "环境操作中，修改已暂停",
                detail = FILE_INSTALL_WRITE_NOTICE,
            ) else if (runtimeRunning) FileAccessState(
                mode = FileAccessMode.READ_ONLY,
                label = "只读",
                summary = "DSH 运行中，修改已暂停",
                detail = "工作区本身可写，但 DSH 运行期间会暂停新建、重命名、编辑、复制、移动和删除。停止 DSH 后恢复写入；浏览、打开、导入和导出不受影响。",
                canStopRuntime = true,
            ) else FileAccessState(
                mode = FileAccessMode.WRITABLE,
                label = "可写",
                summary = "工作区可写",
                detail = "可以在工作区新建文件夹或文本文件，也可以重命名、编辑、复制、移动和删除文件。同名文件不会被覆盖。",
            )
        }
        FileLocation.PHONE -> when {
            !phoneStorageGranted -> FileAccessState(
                mode = FileAccessMode.READ_ONLY,
                label = "只读",
                summary = "尚未授权手机存储",
                detail = "授权后可浏览手机共享存储和可用存储卡中的文件。DSH 默认只能读取；写入需要在本次运行中单独授权。",
                needsStorageGrant = true,
            )
            phoneWriteAllowed -> FileAccessState(
                mode = FileAccessMode.WRITABLE,
                label = "可写",
                summary = "本次 DSH 运行已获写入权限",
                detail = "手机共享存储由 Android 保护。写入授权只对本次 DSH 运行有效，停止 DSH 或撤销后恢复只读。",
                canRevokePhoneWrite = true,
            )
            else -> FileAccessState(
                mode = FileAccessMode.READ_ONLY,
                label = "只读",
                summary = "手机存储当前只读",
                detail = if (phoneWritingAvailable)
                    "DSH 默认只能读取手机文件。写入需要在本次运行中单独授权，停止 DSH 或撤销后恢复只读。"
                else
                    "DSH 默认只能读取手机文件。启动 DSH 后可单独授权写入；当前未运行，所以不能授权。",
                canGrantPhoneWrite = phoneWritingAvailable,
            )
        }
        FileLocation.HOME, FileLocation.UBUNTU -> FileAccessState(
            mode = FileAccessMode.READ_ONLY,
            label = "只读",
            summary = "此位置按只读方式展示",
            detail = "该位置按 Agent 实际挂载路径只读展示，可打开或导出文件，不能在这里修改。需要修改时请复制到工作区。",
        )
    }
}
