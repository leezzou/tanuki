package com.tanuki.ui

import com.tanuki.storage.WorkspaceTransferMode
import com.tanuki.storage.WorkspaceTransferReport
import java.util.Locale

/**
 * 复制/移动失败的可恢复动作。
 *
 * 结果提示不直接猜测原因，而是由 [FileOperationReason] 分类后给出对应入口；
 * 「查看」只用于需要列出具体文件或混合原因的情况。
 */
internal enum class FileOperationRecovery { VIEW, FREE_SPACE, GRANT_PERMISSION }

/**
 * 复制/移动失败原因。
 *
 * 文案集中在这里，避免页面只输出“检查是否同名、链接或空间不足”这类无法行动的通用排查。
 */
internal enum class FileOperationReason(
    val title: String,
    val actionLabel: String,
    val recovery: FileOperationRecovery,
) {
    DUPLICATE("同名已存在", "查看", FileOperationRecovery.VIEW),
    NO_SPACE("空间不足", "清理空间", FileOperationRecovery.FREE_SPACE),
    PERMISSION("没有写入权限", "去授权", FileOperationRecovery.GRANT_PERMISSION),
    OTHER("其他原因", "查看", FileOperationRecovery.VIEW),
}

/** 单个未完成条目；详情保留真实错误，未知原因时用户仍能看到具体信息。 */
internal data class FileOperationFailure(
    val name: String,
    val reason: FileOperationReason,
    val detail: String? = null,
) {
    /** 详情只取单行并限制长度，错位换行或超长底层异常不能撑破结果弹层。 */
    val shortDetail: String? = detail
        ?.replace('\n', ' ')
        ?.replace('\r', ' ')
        ?.trim()
        ?.take(160)
        ?.takeIf { it.isNotEmpty() }
}

/** 同一原因的文件分组，弹层按组列出文件名和处理入口。 */
internal data class FileOperationGroup(
    val reason: FileOperationReason,
    val failures: List<FileOperationFailure>,
) {
    val actionLabel: String get() = reason.actionLabel
    val recovery: FileOperationRecovery get() = reason.recovery
}

/**
 * 一次批量复制/移动的用户可读结果。
 *
 * @param action 「复制」或「移动」
 * @param target 结果中展示的目标位置
 * @param completed 已成功完成的条目数
 * @param total 本批选择的条目总数
 * @param failures 未完成条目；同名跳过、空间不足、权限不足分别归类
 */
internal data class FileOperationOutcome(
    val action: String,
    val target: String,
    val completed: Int,
    val total: Int,
    val failures: List<FileOperationFailure>,
) {
    val reasons: List<FileOperationReason> = FileOperationReason.entries.filter { reason ->
        failures.any { it.reason == reason }
    }

    val groups: List<FileOperationGroup> = reasons.map { reason ->
        FileOperationGroup(reason, failures.filter { it.reason == reason })
    }

    /** 单一原因直接给专属入口；混合原因先进入详情，再由详情提供各自入口。 */
    val recovery: FileOperationRecovery? = when {
        reasons.isEmpty() -> null
        reasons.size == 1 -> reasons.single().recovery
        else -> FileOperationRecovery.VIEW
    }

    val actionLabel: String? = when {
        reasons.isEmpty() -> null
        reasons.size == 1 -> reasons.single().actionLabel
        else -> "查看"
    }

    /** 同名跳过使用完整句式，明确“已跳过（未覆盖）”，不让用户猜本次原因。 */
    val message: String = listOfNotNull(
        "已完成 $completed 项".takeIf { completed > 0 },
        groups.takeIf { it.isNotEmpty() }?.joinToString("；") { group ->
            when (group.reason) {
                FileOperationReason.DUPLICATE ->
                    "${group.failures.size} 个文件同名已存在，已跳过（未覆盖）"
                FileOperationReason.NO_SPACE ->
                    "${group.failures.size} 个文件因空间不足未处理，请清理空间后重试"
                FileOperationReason.PERMISSION ->
                    "${group.failures.size} 个文件因没有写入权限未处理，请授权后重试"
                FileOperationReason.OTHER ->
                    "${group.failures.size} 个文件未处理，原因见详情"
            }
        },
    ).joinToString("；")

    /** 结果弹层的标题行；不把成功和失败混成一句模糊的“已完成 0 项”。 */
    val summary: String = buildString {
        if (completed > 0) append("已完成 $completed 项") else append("没有完成项")
        append("，共 $total 项")
    }

    companion object {
        /** 只按异常链里的类型和文案做保守分类，未知原因保留为 OTHER，不猜测。 */
        fun classify(error: Throwable): FileOperationReason {
            val chain = generateSequence(error) { it.cause }.take(8).toList()
            val type = chain.joinToString(" ") { it.javaClass.name.lowercase(Locale.ROOT) }
            val text = chain.joinToString(" ") { it.message.orEmpty() }
            return when {
                type.contains("filealreadyexists") ||
                    text.containsAny("同名", "未覆盖", "already exists", "file exists") ->
                    FileOperationReason.DUPLICATE
                type.contains("enospc") ||
                    text.containsAny("空间不足", "剩余空间", "容量不足", "容量预算", "no space", "enospc", "disk full") ->
                    FileOperationReason.NO_SPACE
                type.contains("accessdenied") || type.contains("securityexception") ||
                    text.containsAny("权限", "只读", "permission denied", "eacces", "eperm", "operation not permitted") ->
                    FileOperationReason.PERMISSION
                else -> FileOperationReason.OTHER
            }
        }

        fun classify(message: String?): FileOperationReason =
            classify(IllegalStateException(message.orEmpty()))

        /** 手机存储到工作区的批量结果复用同一文案模型，跳过项按同名冲突展示。 */
        fun fromTransfer(mode: WorkspaceTransferMode, report: WorkspaceTransferReport): FileOperationOutcome {
            val action = if (mode == WorkspaceTransferMode.MOVE) "移动" else "复制"
            val target = if (report.targetDirectory.isEmpty()) "工作区根目录"
            else "工作区「${report.targetDirectory}」"
            val failures = report.results.mapNotNull { result ->
                when {
                    result.skipped -> FileOperationFailure(
                        result.sourceName,
                        FileOperationReason.DUPLICATE,
                        "已在冲突处理中选择跳过",
                    )
                    result.error != null -> FileOperationFailure(
                        result.sourceName,
                        classify(result.error),
                        result.error,
                    )
                    else -> null
                }
            }
            val completed = report.results.count { !it.skipped && it.error == null }
            return FileOperationOutcome(action, target, completed, report.results.size, failures)
        }

        private fun String.containsAny(vararg needles: String): Boolean =
            needles.any { contains(it, ignoreCase = true) }
    }
}
