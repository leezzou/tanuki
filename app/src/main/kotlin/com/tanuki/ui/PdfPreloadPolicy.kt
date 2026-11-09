package com.tanuki.ui

import kotlin.math.abs

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

    /**
     * 实际渲染集合：先保证同屏可见页全部进入，再在可见范围外预加载相邻页。
     *
     * 短横页、长屏或窄窗口可能同屏出现三页以上；只按中心页 ±1 取窗口会让其余
     * 可见页永远停在占位状态。可见页按距离中心页由近到远排序，首尾各补 [ahead]/
     * [behind] 页预加载，仍只加载视口附近而非整个文档。
     */
    fun renderPlan(
        visible: List<Int>,
        current: Int,
        pageCount: Int,
        ahead: Int = AHEAD,
        behind: Int = BEHIND,
    ): List<Int> {
        require(ahead >= 0 && behind >= 0) { "PDF 预加载页数不能为负数" }
        if (pageCount <= 0) return emptyList()
        val center = current.coerceIn(0, pageCount - 1)
        val visiblePages = visible.asSequence()
            .filter { it in 0 until pageCount }
            .toSet()
        val ordered = LinkedHashSet<Int>()
        ordered += center
        visiblePages.asSequence()
            .filter { it != center }
            .sortedBy { abs(it - center) }
            .forEach { ordered += it }
        val firstVisible = visiblePages.minOrNull() ?: center
        val lastVisible = visiblePages.maxOrNull() ?: center
        // 与 window 的顺序一致：先下方（后续页）再上方（前序页）。
        for (offset in 1..maxOf(ahead, behind)) {
            if (offset <= ahead && lastVisible + offset < pageCount) ordered += lastVisible + offset
            if (offset <= behind && firstVisible - offset >= 0) ordered += firstVisible - offset
        }
        window(center, pageCount, ahead, behind).forEach { ordered += it }
        return ordered.toList()
    }
}
