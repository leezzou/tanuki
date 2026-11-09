package com.tanuki.ui

/**
 * 连续纵向阅读时的预加载窗口策略（纯 Kotlin）。
 *
 * 只加载当前页和前后各一页：翻页时相邻页已在缓存中，快速滚动或跳页时也不会
 * 一次性渲染整个文档。返回顺序即渲染优先级，当前页始终排在最前。
 */
internal object PdfPreloadPolicy {
    const val AHEAD = 1
    const val BEHIND = 1

    fun window(
        current: Int,
        pageCount: Int,
        ahead: Int = AHEAD,
        behind: Int = BEHIND,
    ): List<Int> {
        require(ahead >= 0 && behind >= 0) { "PDF 预加载页数不能为负数" }
        if (pageCount <= 0) return emptyList()
        val center = current.coerceIn(0, pageCount - 1)
        return buildList {
            add(center)
            for (offset in 1..maxOf(ahead, behind)) {
                if (offset <= ahead && center + offset < pageCount) add(center + offset)
                if (offset <= behind && center - offset >= 0) add(center - offset)
            }
        }
    }

    /** 缓存中需要拒绝淘汰的页集合。 */
    fun protectedKeys(
        current: Int,
        pageCount: Int,
        ahead: Int = AHEAD,
        behind: Int = BEHIND,
    ): Set<Int> = window(current, pageCount, ahead, behind).toSet()
}
