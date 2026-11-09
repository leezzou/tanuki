package com.tanuki.ui

import java.util.Locale

/**
 * 快速跳页的输入解析与展示（纯 Kotlin）。
 *
 * 接受「12」「12/50」「第12页」「第 12 页」等常见写法，统一返回从 0 开始的页索引；
 * 超出范围、空输入、小数、负数或非数字一律返回 null，由界面给出中文提示。
 */
internal object PdfJumpPolicy {
    private const val MAX_DIGITS = 9

    fun parse(input: String, pageCount: Int): Int? {
        if (pageCount <= 0) return null
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val pageText = trimmed
            .removePrefix("第")
            .removeSuffix("页")
            .substringBefore('/')
            .trim()
        if (pageText.isEmpty() || pageText.length > MAX_DIGITS) return null
        if (pageText.any { it !in '0'..'9' }) return null
        val page = pageText.toIntOrNull() ?: return null
        return if (page in 1..pageCount) page - 1 else null
    }

    fun label(pageIndex: Int, pageCount: Int): String =
        if (pageCount <= 0) "正在读取页数…" else "第 ${pageIndex + 1} / $pageCount 页"

    fun zoomLabel(scale: Float): String = String.format(Locale.ROOT, "%.1f×", scale)

    fun hint(pageCount: Int): String =
        if (pageCount <= 0) "请输入页码" else "请输入 1–$pageCount 之间的页码"
}
