package com.tanuki.storage

/**
 * SAF 导出目标清理策略。只在本次导出未成功（取消或失败）时调用 [discard]；
 * 删除异常/返回 false 都视为未清理，由调用方提示用户检查目标位置，不能静默留下截断文件。
 */
object ExportCleanup {
    const val CANNOT_DELETE = "未完成的目标文件无法自动删除，请在目标位置检查"

    fun discard(delete: () -> Boolean): Boolean = runCatching { delete() }.getOrDefault(false)

    fun cancelledNotice(cleaned: Boolean): String =
        if (cleaned) "已取消导出，未完成的目标文件已删除"
        else "已取消导出，但未完成的目标文件无法自动删除，请在目标位置检查"

    fun failureNotice(cleaned: Boolean): String =
        if (cleaned) "导出失败，未完成的目标文件已删除；应用内原文件仍保留"
        else "导出失败，未完成的目标文件无法自动删除；应用内原文件仍保留"
}
