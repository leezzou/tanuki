package com.tanuki.ui

import com.tanuki.storage.docx.DocxPageSetup

/**
 * DOCX 预览的纯布局策略：页面几何、分页、图片尺寸与解码采样。
 * 不依赖 Android/Compose，便于 JVM 回归；界面只负责按这些结果测量和绘制。
 */
data class DocxPageGeometry(
    val pageWidthDp: Float,
    val pageHeightDp: Float,
    val contentWidthDp: Float,
    val contentHeightDp: Float,
    val marginLeftDp: Float,
    val marginTopDp: Float,
    /** 纸张几何比例：1 缇对应的 dp。 */
    val pageDpPerTwip: Float,
    /** 文字/缩进/段距使用的比例；为提高手机可读性，正文按固定倍数放大。 */
    val textDpPerTwip: Float,
) {
    fun fontDp(halfPoints: Int): Float = halfPoints / 2f * 20f * textDpPerTwip
    fun textLengthDp(twips: Int): Float = twips * textDpPerTwip
}

object DocxPageGeometryPolicy {
    /** 纸张按屏幕宽度显示时正文字号会过小，统一放大到接近手机正文字号。 */
    const val TEXT_SCALE = 1.9f
    const val MAX_PAGE_WIDTH_DP = 720f
    const val MIN_PAGE_WIDTH_DP = 240f

    fun compute(
        page: DocxPageSetup,
        availableWidthDp: Float,
        maxPageWidthDp: Float = MAX_PAGE_WIDTH_DP,
    ): DocxPageGeometry {
        val width = availableWidthDp.coerceIn(MIN_PAGE_WIDTH_DP, maxPageWidthDp)
        val pageWidthTwips = page.widthTwips.coerceAtLeast(1)
        val pageDpPerTwip = width / pageWidthTwips
        val left = page.marginLeftTwips.coerceAtLeast(0) * pageDpPerTwip
        val right = page.marginRightTwips.coerceAtLeast(0) * pageDpPerTwip
        val top = page.marginTopTwips.coerceAtLeast(0) * pageDpPerTwip
        val bottom = page.marginBottomTwips.coerceAtLeast(0) * pageDpPerTwip
        val contentWidth = (width - left - right).coerceAtLeast(width * 0.3f)
        val height = page.heightTwips.coerceAtLeast(1) * pageDpPerTwip
        val contentHeight = (height - top - bottom).coerceAtLeast(height * 0.3f)
        return DocxPageGeometry(
            pageWidthDp = width,
            pageHeightDp = height,
            contentWidthDp = contentWidth,
            contentHeightDp = contentHeight,
            marginLeftDp = left,
            marginTopDp = top,
            pageDpPerTwip = pageDpPerTwip,
            textDpPerTwip = pageDpPerTwip * TEXT_SCALE,
        )
    }
}

/** 分页原子：一行文字、一行表格或一个图片块；[gapBefore] 已合并上一块段后距。 */
data class DocxPageAtom(
    val key: Int,
    val height: Float,
    val gapBefore: Float = 0f,
    val pageBreakBefore: Boolean = false,
)

data class DocxPageSlice(
    val atoms: List<DocxPageAtom>,
    val usedHeight: Float,
    val overflow: Boolean = false,
)

object DocxPaginator {
    private const val EPSILON = 0.5f

    /** 贪心装箱：显式分页符必换页；单块高于整页时独占一页并标记 overflow。 */
    fun paginate(atoms: List<DocxPageAtom>, pageHeight: Float): List<DocxPageSlice> {
        val height = pageHeight.coerceAtLeast(1f)
        val pages = mutableListOf<DocxPageSlice>()
        var current = mutableListOf<DocxPageAtom>()
        var used = 0f

        fun flush(overflow: Boolean = false) {
            if (current.isNotEmpty()) {
                pages += DocxPageSlice(current.toList(), used, overflow)
                current = mutableListOf()
                used = 0f
            }
        }

        for (atom in atoms) {
            if (atom.pageBreakBefore) flush()
            if (atom.height <= 0f) continue
            val gap = if (current.isEmpty()) 0f else atom.gapBefore.coerceAtLeast(0f)
            if (current.isNotEmpty() && used + gap + atom.height > height + EPSILON) flush()
            if (current.isEmpty() && atom.height > height + EPSILON) {
                current += atom
                used = atom.height
                flush(overflow = true)
                continue
            }
            if (current.isNotEmpty()) used += atom.gapBefore.coerceAtLeast(0f)
            current += atom
            used += atom.height
        }
        flush()
        if (pages.isEmpty()) pages += DocxPageSlice(emptyList(), 0f)
        return pages
    }
}

data class DocxImageSize(val widthDp: Float, val heightDp: Float)

object DocxImagePolicy {
    const val MAX_DECODE_SIDE = 4096
    const val MAX_DECODE_PIXELS = 4_000_000L
    const val MAX_IMAGES_PER_PAGE = 64
    const val MAX_TOTAL_PIXELS = 12_000_000L
    private const val EMU_PER_TWIP = 635.0

    /** 按 2 的幂采样，保证长边和总像素都在解码上限内；非法尺寸按 1 处理。 */
    fun sampleSize(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / sample > MAX_DECODE_SIDE || height / sample > MAX_DECODE_SIDE ||
            (width / sample).toLong() * (height / sample) > MAX_DECODE_PIXELS
        ) {
            sample *= 2
        }
        return sample
    }

    /**
     * 计算图片在正文宽度内的显示尺寸：优先使用 OOXML extent（EMU），缺失时用解码后的像素尺寸；
     * 超过正文高度时等比缩小，绝不放大到超过正文宽度。
     */
    fun displaySize(
        bitmapWidth: Int,
        bitmapHeight: Int,
        widthEmu: Long?,
        heightEmu: Long?,
        contentWidthDp: Float,
        contentHeightDp: Float,
        contentWidthTwips: Int,
        density: Float,
    ): DocxImageSize {
        val safeWidth = contentWidthDp.coerceAtLeast(1f)
        val safeHeight = contentHeightDp.coerceAtLeast(1f)
        val contentEmu = contentWidthTwips.coerceAtLeast(1) * EMU_PER_TWIP
        val aspect: Double
        val desiredWidth: Float
        if (widthEmu != null && widthEmu > 0 && heightEmu != null && heightEmu > 0) {
            aspect = heightEmu.toDouble() / widthEmu.toDouble()
            desiredWidth = safeWidth * (widthEmu.toDouble() / contentEmu).toFloat()
        } else {
            aspect = if (bitmapWidth > 0 && bitmapHeight > 0) bitmapHeight.toDouble() / bitmapWidth else 1.0
            desiredWidth = if (bitmapWidth > 0) bitmapWidth / density.coerceAtLeast(0.1f) else 64f
        }
        var width = desiredWidth.coerceIn(1f, safeWidth)
        var height = (width * aspect).toFloat()
        if (height > safeHeight) {
            height = safeHeight
            width = (height / aspect).toFloat().coerceAtMost(safeWidth)
        }
        return DocxImageSize(width.coerceAtLeast(1f), height.coerceAtLeast(1f))
    }

    /** 解码后的总像素预算；超出后剩余图片只用占位符，避免大文档 OOM。 */
    fun withinBudget(usedPixels: Long, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        return usedPixels + width.toLong() * height <= MAX_TOTAL_PIXELS
    }
}
