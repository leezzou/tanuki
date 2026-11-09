package com.tanuki.ui

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * PDF 页面位图尺寸策略：长边与总像素都有上限，避免高 DPI 文档或异常页面在渲染时 OOM。
 * 小页面最多放大两倍以适配屏幕，大页面按比例缩小。
 */
internal object PdfRenderPolicy {
    const val MAX_DIMENSION = 2048
    const val MAX_PIXELS = 4_000_000
    const val MAX_UPSCALE = 2.0

    data class Size(val width: Int, val height: Int)

    /** 渲染入口统一携带长边与总像素两项上限，避免只传其中一项。 */
    data class Limits(val maxDimension: Int, val maxPixels: Int)

    fun limits(maxDimension: Int = MAX_DIMENSION, maxPixels: Int = MAX_PIXELS): Limits {
        require(maxDimension > 0 && maxPixels > 0) { "PDF 渲染上限无效" }
        return Limits(maxDimension, maxPixels)
    }

    fun targetSize(
        pageWidth: Int,
        pageHeight: Int,
        maxDimension: Int = MAX_DIMENSION,
        maxPixels: Int = MAX_PIXELS,
    ): Size {
        require(pageWidth > 0 && pageHeight > 0) { "PDF 页面尺寸无效" }
        require(maxDimension > 0 && maxPixels > 0) { "PDF 渲染上限无效" }
        val byDimension = min(maxDimension.toDouble() / pageWidth, maxDimension.toDouble() / pageHeight)
        val byPixels = sqrt(maxPixels.toDouble() / (pageWidth.toDouble() * pageHeight))
        val scale = min(min(byDimension, byPixels), MAX_UPSCALE)
        return Size(
            floor(pageWidth * scale).toInt().coerceAtLeast(1),
            floor(pageHeight * scale).toInt().coerceAtLeast(1),
        )
    }

    /**
     * 按阅读视口计算渲染尺寸：以视口宽度乘超采样倍率作为目标清晰度，
     * 再受长边、总像素和单页内存预算约束。视口未知时退回 [targetSize]。
     */
    fun viewportSize(
        pageWidth: Int,
        pageHeight: Int,
        viewportWidth: Int,
        maxDimension: Int = MAX_DIMENSION,
        maxPixels: Int = MAX_PIXELS,
        oversample: Double = 1.5,
    ): Size {
        require(pageWidth > 0 && pageHeight > 0) { "PDF 页面尺寸无效" }
        require(maxDimension > 0 && maxPixels > 0) { "PDF 渲染上限无效" }
        if (viewportWidth <= 0) return targetSize(pageWidth, pageHeight, maxDimension, maxPixels)
        val desiredWidth = max(pageWidth.toDouble(), viewportWidth * oversample.coerceAtLeast(1.0))
        val scale = min(
            desiredWidth / pageWidth,
            min(
                maxDimension.toDouble() / pageWidth,
                maxDimension.toDouble() / pageHeight,
            ),
        ).coerceAtMost(sqrt(maxPixels.toDouble() / (pageWidth.toDouble() * pageHeight)))
        return Size(
            floor(pageWidth * scale).toInt().coerceAtLeast(1),
            floor(pageHeight * scale).toInt().coerceAtLeast(1),
        )
    }
}
