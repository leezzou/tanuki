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
     * 内容层按比例放大后，列表只需滚动补偿值的旧比例部分。
     */
    fun zoomScrollCompensation(
        centroidY: Float,
        viewportHeight: Float,
        oldScale: Float,
        newScale: Float,
    ): Float {
        if (viewportHeight <= 0f || oldScale <= 0f || !oldScale.isFinite() || !newScale.isFinite()) return 0f
        val ratio = newScale / oldScale
        return (centroidY - viewportHeight / 2f) * (1f - 1f / ratio)
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
