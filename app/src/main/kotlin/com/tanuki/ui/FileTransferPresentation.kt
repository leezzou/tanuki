package com.tanuki.ui

import com.tanuki.runtime.FileTransferProgress
import java.util.Locale

/** 多个文件传输的汇总；percent 只在所有任务都有正总量时给出。 */
internal data class FileTransferSummary(
    val count: Int,
    val percent: Int?,
    val received: Long,
    val total: Long,
    val allTotalsKnown: Boolean,
)

/**
 * 文件传输浮层与详情 sheet 的纯格式化逻辑。
 *
 * 百分比只由实际字节计算；总量未知时返回 null，由界面显示不定进度，绝不估造数字。
 * 剩余时间以单调时钟的已用时间估算，数据不足时返回 null。
 */
internal object FileTransferPresentation {
    fun imports(items: List<FileTransferProgress>): List<FileTransferProgress> =
        items.filter { it.kind == com.tanuki.runtime.FileTransferKind.IMPORT }

    fun percent(received: Long, total: Long): Int? =
        if (total > 0) ((received.coerceIn(0, total).toDouble() / total) * 100).toInt().coerceIn(0, 100) else null

    fun summary(items: List<FileTransferProgress>): FileTransferSummary? {
        if (items.isEmpty()) return null
        val known = items.filter { it.total > 0 }
        val allKnown = known.size == items.size
        val total = known.sumOf { it.total }
        val received = known.sumOf { it.received.coerceIn(0, it.total) }
        return FileTransferSummary(
            count = items.size,
            percent = if (allKnown && total > 0) percent(received, total) else null,
            received = received,
            total = total,
            allTotalsKnown = allKnown,
        )
    }

    fun bytes(value: Long): String = when {
        value >= 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f GB", value / (1024.0 * 1024 * 1024))
        value >= 1024L * 1024 -> String.format(Locale.ROOT, "%.1f MB", value / (1024.0 * 1024))
        value >= 1024 -> String.format(Locale.ROOT, "%.1f KB", value / 1024.0)
        else -> "$value B"
    }

    fun sizeText(received: Long, total: Long): String = when {
        total > 0 -> "${bytes(received.coerceIn(0, total))} / ${bytes(total)}"
        received > 0 -> "已传输 ${bytes(received)} · 总大小未知"
        else -> "正在准备文件 · 总大小未知"
    }

    /** 剩余秒数；已用时间不足一秒或还没有收到字节时无法可靠估算。 */
    fun remainingSeconds(item: FileTransferProgress, now: Long): Long? {
        val started = item.timing.startedAtMillis
        if (item.total <= 0 || started <= 0 || item.received <= 0) return null
        val elapsedMillis = (now - started).coerceAtLeast(0)
        if (elapsedMillis < 1000) return null
        val elapsedSeconds = elapsedMillis / 1000.0
        val rate = item.received / elapsedSeconds
        if (rate <= 0.0) return null
        return ((item.total - item.received).coerceAtLeast(0) / rate).toLong().coerceAtLeast(0)
    }

    fun remainingText(item: FileTransferProgress, now: Long): String = when {
        item.total <= 0 -> "剩余时间未知"
        item.received >= item.total -> "即将完成"
        else -> remainingSeconds(item, now)?.let { "剩余约 ${duration(it)}" } ?: "正在估算剩余时间"
    }

    fun duration(seconds: Long): String = when {
        seconds >= 3600 -> "${seconds / 3600}时${seconds % 3600 / 60}分${seconds % 60}秒"
        seconds >= 60 -> "${seconds / 60}分${seconds % 60}秒"
        else -> "${seconds}秒"
    }
}
