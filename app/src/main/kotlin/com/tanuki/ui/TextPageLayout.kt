package com.tanuki.ui

import com.tanuki.storage.TextFileReader

/**
 * 文本查看器的行号排版、搜索位置换算与分段上限。
 *
 * 纯 Kotlin，不依赖 Compose 或 Android，便于 JVM 回归：
 * - 行号与正文放进同一段文本，自动换行时行号始终贴在逻辑行首，不会与正文错位；
 * - 搜索匹配仍以原始文本下标计算，再换算为带行号文本的下标；
 * - 超过 [MAX_VIEW_BYTES] 后停止继续翻页，由界面提示已截断，避免超大日志无限占用内存。
 */
internal object TextPageLayout {
    /** 单次查看最多显示 4 MiB；更早的 64 KiB 分页上限保持不变。 */
    const val MAX_VIEW_BYTES = TextFileReader.MAX_VIEW_BYTES

    /** 带行号文本；[numberStarts] 是每行行号在 [text] 中的起始下标。 */
    data class Numbered(val text: String, val lineCount: Int, val prefixLength: Int, val numberStarts: List<Int>)

    /** 逻辑行数：空文本算 1 行，末尾换行会多出一个空行（与常见编辑器一致）。 */
    fun lineCount(text: String): Int = text.count { it == '\n' } + 1

    /**
     * 为每行加“宽行号 + 两个空格”的前缀。
     * [startLine] 是当前段第一行的行号（从 1 开始），跨段时由 [nextStartLine] 延续。
     */
    fun number(text: String, startLine: Int, enabled: Boolean = true): Numbered {
        val lines = lineCount(text)
        if (!enabled) return Numbered(text, lines, 0, emptyList())
        val first = startLine.coerceAtLeast(1)
        val last = first + lines - 1
        val digits = last.toString().length.coerceAtLeast(1)
        val prefix = digits + 2
        val numbered = StringBuilder(text.length + prefix * lines)
        val starts = ArrayList<Int>(lines)
        var line = first
        var index = 0
        while (true) {
            starts += numbered.length
            numbered.append(line.toString().padStart(digits)).append("  ")
            val newline = text.indexOf('\n', index)
            if (newline < 0) {
                numbered.append(text, index, text.length)
                break
            }
            numbered.append(text, index, newline + 1)
            index = newline + 1
            line++
        }
        return Numbered(numbered.toString(), lines, prefix, starts)
    }

    /**
     * 原始文本下标换算成带行号文本下标。
     * 原因：每个逻辑行前都插入了等长前缀，所以偏移 = 原始偏移 + 前缀长度 × (所在行序号 + 1)。
     */
    fun displayOffset(rawOffset: Int, text: String, prefixLength: Int): Int {
        if (prefixLength == 0) return rawOffset
        val bounded = rawOffset.coerceIn(0, text.length)
        var line = 0
        var newline = text.indexOf('\n')
        while (newline in 0 until bounded) {
            line++
            newline = text.indexOf('\n', newline + 1)
        }
        return bounded + prefixLength * (line + 1)
    }

    /**
     * 批量换算按升序排列的原始匹配下标；一次线性推进，避免每个匹配都从文本开头数换行。
     * 单字符搜索可能产生数千个匹配，逐个调用 [displayOffset] 会退化成 O(n×m)。
     */
    fun displayOffsets(rawOffsets: List<Int>, text: String, prefixLength: Int): List<Int> {
        if (prefixLength == 0) return rawOffsets
        val result = ArrayList<Int>(rawOffsets.size)
        var line = 0
        var newline = text.indexOf('\n')
        for (raw in rawOffsets) {
            val bounded = raw.coerceIn(0, text.length)
            while (newline >= 0 && newline < bounded) {
                line++
                newline = text.indexOf('\n', newline + 1)
            }
            result += bounded + prefixLength * (line + 1)
        }
        return result
    }

    /** 是否还能继续下一页；到达文件末尾或显示上限时停止。 */
    fun canAdvance(displayedBytes: Long, totalBytes: Long): Boolean =
        canAdvance(displayedBytes, totalBytes, hasMore = totalBytes > displayedBytes)

    /**
     * 外部顺序流可能不返回总大小（totalBytes < 0），此时以本次分页的 hasMore 为准，
     * 仍保留 4 MiB 显示上限。
     */
    fun canAdvance(displayedBytes: Long, totalBytes: Long, hasMore: Boolean): Boolean =
        displayedBytes > 0 && hasMore && displayedBytes < MAX_VIEW_BYTES &&
            (totalBytes < 0 || displayedBytes < totalBytes)

    /**
     * 是否还有未显示内容：停止翻页且总大小大于已显示游标，或大小未知时已到显示上限。
     * 多字节字符横跨 4 MiB 边界时会在完整字符处停下，此时 displayed 可能略小于上限，
     * 仍需提示已截断。
     */
    fun isTruncated(displayedBytes: Long, totalBytes: Long, hasMore: Boolean): Boolean = when {
        hasMore -> false
        totalBytes < 0 -> true
        else -> totalBytes > displayedBytes
    }

    /** 下一段第一行的行号：段内换行数即下一段的起始行（跨段拆开的同一行保持相同行号）。 */
    fun nextStartLine(currentStartLine: Int, pageText: String): Int =
        currentStartLine.coerceAtLeast(1) + pageText.count { it == '\n' }
}
