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
                summary = "运行环境准备中，修改已暂停",
                detail = FILE_INSTALL_WRITE_NOTICE,
            ) else if (runtimeRunning) FileAccessState(
                mode = FileAccessMode.READ_ONLY,
                label = "只读",
                summary = "助手运行中，修改已暂停",
                detail = "助手运行时暂停修改；浏览、打开、导入和导出仍可用。\n停止助手后可新建、编辑、重命名、复制、移动和删除文件。",
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
                detail = "允许后可浏览手机共享文件和存储卡；拒绝仍可用系统文件选择器导入。\n可在系统设置修改文件访问权限；助手写入需另行授权。",
                needsStorageGrant = true,
            )
            phoneWriteAllowed -> FileAccessState(
                mode = FileAccessMode.WRITABLE,
                label = "可写",
                summary = "助手本次可写入手机文件",
                detail = "助手本次可新建、修改和删除手机共享文件。\n可在这里撤销写入，或停止助手以恢复只读。",
                canRevokePhoneWrite = true,
            )
            else -> FileAccessState(
                mode = FileAccessMode.READ_ONLY,
                label = "只读",
                summary = "手机存储当前只读",
                detail = if (phoneWritingAvailable)
                    "助手当前只能读取手机文件；如需修改，请允许本次写入。\n可在这里撤销，停止助手后恢复只读。"
                else
                    "请先启动助手，再允许本次写入；也可保持只读。",
                canGrantPhoneWrite = phoneWritingAvailable,
            )
        }
        FileLocation.HOME, FileLocation.UBUNTU -> FileAccessState(
            mode = FileAccessMode.READ_ONLY,
            label = "只读",
            summary = "此位置按只读方式展示",
            detail = "此位置只能查看、打开或导出；需要修改时请复制到工作区。",
        )
    }
}
