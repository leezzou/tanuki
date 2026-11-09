package com.tanuki.ui

import kotlin.math.abs
import kotlin.math.min

/** PDF 阅读布局：适应宽度或整页适配视口。 */
internal enum class PdfFitMode { FIT_WIDTH, FIT_PAGE }

/**
 * 连续纵向阅读的视口策略（纯 Kotlin，可在 JVM 测试）。
 *
 * 缩放采用「对视口内容层做相机缩放 + 横向平移」：纵向翻页仍由列表滚动负责，
 * 因此放大后可以继续滚动到任意页面；横向平移限制在放大后内容的左右边界内。
 */
internal object PdfViewportPolicy {
    const val ZOOM_EPSILON = 0.01f

    data class Size(val width: Float, val height: Float)

    fun pageDisplaySize(
        pageWidth: Float,
        pageHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        mode: PdfFitMode,
    ): Size {
        if (pageWidth <= 0f || pageHeight <= 0f || viewportWidth <= 0f || viewportHeight <= 0f) {
            return Size(0f, 0f)
        }
        return when (mode) {
            PdfFitMode.FIT_WIDTH -> {
                val scale = viewportWidth / pageWidth
                Size(viewportWidth, pageHeight * scale)
            }
            PdfFitMode.FIT_PAGE -> {
                val scale = min(viewportWidth / pageWidth, viewportHeight / pageHeight)
                Size(pageWidth * scale, pageHeight * scale)
            }
        }
    }

    fun isZoomed(scale: Float): Boolean = scale > ZoomPolicy.MIN_SCALE + ZOOM_EPSILON

    /** 放大后内容层相对视口中心的最大横向偏移。 */
    fun maxPanX(viewportWidth: Float, scale: Float): Float {
        if (viewportWidth <= 0f || !scale.isFinite()) return 0f
        val bounded = ZoomPolicy.clampScale(scale)
        return ((viewportWidth * bounded - viewportWidth) / 2f).coerceAtLeast(0f)
    }

    fun clampPanX(panX: Float, viewportWidth: Float, scale: Float): Float {
        val max = maxPanX(viewportWidth, scale)
        if (!panX.isFinite()) return 0f
        return panX.coerceIn(-max, max)
    }

    /**
     * 围绕手指横向位置缩放时保持指下内容不动所需的平移量。
     * 视口中心为原点，因此以缩放中心与视口中心的偏移做补偿。
     */
    fun zoomPanX(
        panX: Float,
        centroidX: Float,
        viewportWidth: Float,
        oldScale: Float,
        newScale: Float,
    ): Float {
        if (viewportWidth <= 0f || oldScale <= 0f || !oldScale.isFinite() || !newScale.isFinite()) return 0f
        val ratio = newScale / oldScale
        val offset = centroidX - viewportWidth / 2f
        return clampPanX(panX * ratio + offset * (1f - ratio), viewportWidth, newScale)
    }

    /**
     * 围绕视口中心缩放时，为保持指下内容纵向不动，列表需要滚动的像素数。
     *
     * 屏幕坐标 = 视口中心 + scale × (列表坐标 − 滚动量 − 视口中心)，由此解出
     * 滚动增量 = (centroidY − 视口中心) × (1/oldScale − 1/newScale)。
     * 除以旧缩放是必要的：列表滚动量使用未缩放的列表像素。
     */
    fun zoomScrollCompensation(
        centroidY: Float,
        viewportHeight: Float,
        oldScale: Float,
        newScale: Float,
    ): Float {
        if (viewportHeight <= 0f || oldScale <= 0f || !oldScale.isFinite() || !newScale.isFinite()) return 0f
        return (centroidY - viewportHeight / 2f) * (1f / oldScale - 1f / newScale)
    }

    /**
     * 放大后可滚动内容的首尾边缘留白（上下各一份）。
     *
     * LazyColumn 的测量高度与滚动边界按 1× 计算，仅用 graphicsLayer 放大时首尾页会
     * 移出可达范围。补上与缩放对应的留白后，第一页顶部在滚动 0 处正好贴住视口上沿，
     * 最后一页底部在最大滚动处正好贴住视口下沿；1× 时留白为 0，不改变原有布局。
     */
    fun zoomEdgePadding(viewportHeight: Float, scale: Float): Float {
        if (viewportHeight <= 0f || !scale.isFinite() || !isZoomed(scale)) return 0f
        val bounded = ZoomPolicy.clampScale(scale)
        return viewportHeight * (bounded - 1f) / (2f * bounded)
    }

    /** 便于推导与回归测试：列表坐标在缩放后的屏幕纵坐标（视口顶部为 0）。 */
    fun scaledScreenY(
        listY: Float,
        scroll: Float,
        viewportHeight: Float,
        scale: Float,
    ): Float {
        if (viewportHeight <= 0f || !scale.isFinite()) return 0f
        return viewportHeight / 2f + scale * (listY - scroll - viewportHeight / 2f)
    }

    data class VisiblePage(val index: Int, val offset: Int, val size: Int)

    /**
     * 连续滚动时根据布局信息判断当前页：优先取覆盖视口中心的那一页；
     * 页面间隙或拉伸布局下退化为离中心最近的页。
     */
    fun pageAtCenter(
        visible: List<VisiblePage>,
        viewportStart: Int,
        viewportEnd: Int,
        pageCount: Int,
    ): Int? {
        if (visible.isEmpty() || pageCount <= 0) return null
        val center = (viewportStart + viewportEnd) / 2
        visible.firstOrNull { it.offset <= center && it.offset + it.size > center }?.let {
            return it.index.coerceIn(0, pageCount - 1)
        }
        return visible
            .minByOrNull { abs(it.offset + it.size / 2 - center) }
            ?.index
            ?.coerceIn(0, pageCount - 1)
    }
}
