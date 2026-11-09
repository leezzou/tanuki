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
                current += atom.copy(gapBefore = 0f)
                used = atom.height
                flush(overflow = true)
                continue
            }
            if (current.isNotEmpty()) used += atom.gapBefore.coerceAtLeast(0f)
            // 页首不绘制段前距：切片内保留清零后的 gap，保证分页计算与渲染一致。
            current += if (current.isEmpty()) atom.copy(gapBefore = 0f) else atom
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


/**
 * 表格列宽归一化：宽度来自文件，必须用 Long 求和，避免 Int 溢出后把列宽放大到
 * Compose Constraints 无法表示的范围；结果保证有限、非负且总和不超过正文宽度。
 */
object DocxTableWidths {
    const val MAX_COLUMNS = 256

    fun normalize(widthsTwips: List<Int>, columnCount: Int, contentWidthDp: Float): List<Float> {
        val columns = columnCount.coerceIn(1, MAX_COLUMNS)
        val safeWidth = contentWidthDp.coerceAtLeast(1f).toDouble()
        val weights = LongArray(columns) { 1L }
        if (widthsTwips.size == columns) {
            for (index in 0 until columns) weights[index] = widthsTwips[index].coerceAtLeast(1).toLong()
        }
        val total = weights.sum().coerceAtLeast(1L)
        var widths = weights.map { (safeWidth * it / total).toFloat().coerceIn(0f, safeWidth.toFloat()) }
        val sum = widths.sumOf { it.toDouble() }
        if (sum > safeWidth && sum > 0.0) {
            widths = widths.map { (it * safeWidth / sum).toFloat().coerceIn(0f, safeWidth.toFloat()) }
        }
        return widths
    }
}

/** 渲染预算；返回 false 时调用方必须停止继续测量并标记截断。 */
class DocxRenderBudget(val maxBlocks: Int = 1_200, val maxAtoms: Int = 12_000) {
    var blocks: Int = 0
        private set
    var atoms: Int = 0
        private set
    var exceeded: Boolean = false
        private set

    fun takeBlock(): Boolean {
        if (exceeded) return false
        if (blocks >= maxBlocks) {
            exceeded = true
            return false
        }
        blocks++
        return true
    }

    fun takeAtom(): Boolean {
        if (exceeded) return false
        if (atoms >= maxAtoms) {
            exceeded = true
            return false
        }
        atoms++
        return true
    }
}

/**
 * 解码图片的生命周期：只释放 effect 创建时捕获的快照，不能读取最新状态，
 * 否则首次发布新 Bitmap 会触发旧 effect 清理并回收刚显示的图片。
 */
class DocxBitmapLifetime<T>(private val release: (T) -> Unit) {
    private var active: Map<String, T> = emptyMap()

    fun publish(next: Map<String, T>): Map<String, T> {
        val previous = active
        active = next
        return previous
    }

    fun snapshot(): Map<String, T> = active

    fun release(snapshot: Map<String, T>) {
        snapshot.values.forEach(release)
    }
}


/**
 * 长段落按字符数有界分段：单次 TextMeasurer 只处理一个分段，避免 2M 字符段落
 * 在 UI 线程一次性排版；分段边界尽量落在空白处且不拆开代理对。
 */
object DocxTextChunking {
    const val MAX_CHARS = 8_000
    private const val LOOKBACK = 240

    fun ranges(text: CharSequence): List<IntRange> {
        val length = text.length
        if (length <= 0) return emptyList()
        val output = mutableListOf<IntRange>()
        var start = 0
        while (start < length) {
            var end = (start + MAX_CHARS - 1).coerceAtMost(length - 1)
            if (end < length - 1) {
                val floor = (start + MAX_CHARS - 1 - LOOKBACK).coerceAtLeast(start)
                var probe = end
                while (probe > floor && !text[probe].isWhitespace()) probe--
                if (probe > floor) end = probe
            }
            // 边界不能落在代理对中间：回退到完整字符之前；若起点本身就是高代理，
            // 至少把该代理对完整包含进当前分段。
            while (end > start && (text[end].isHighSurrogate() || text[end].isLowSurrogate())) end--
            if (end == start && text[end].isHighSurrogate() && start + 1 < length) end = start + 1
            output += start..end
            start = end + 1
        }
        return output
    }
}


/** 单元格文本分段计划：每个段落按 8,000 字切分，并为每个分段消耗一个渲染原子预算。 */
data class DocxCellPlan(
    val chunks: Map<Int, List<IntRange>>,
    val truncated: Boolean,
)

object DocxCellPlanner {
    const val MAX_CHUNK_CHARS = DocxTextChunking.MAX_CHARS
    const val MAX_CHUNKS = 12_000

    fun plan(paragraphLengths: List<Int>, maxChunks: Int = MAX_CHUNKS): DocxCellPlan {
        val chunks = linkedMapOf<Int, List<IntRange>>()
        var count = 0
        for ((index, length) in paragraphLengths.withIndex()) {
            val size = length.coerceAtLeast(1)
            var start = 0
            val ranges = mutableListOf<IntRange>()
            while (start < size) {
                if (count >= maxChunks) return DocxCellPlan(chunks, true)
                val end = (start + MAX_CHUNK_CHARS - 1).coerceAtMost(size - 1)
                ranges += start..end
                start = end + 1
                count++
            }
            chunks[index] = ranges
        }
        return DocxCellPlan(chunks, false)
    }
}

data class DocxRowHeight(val heightDp: Float, val truncated: Boolean)

/**
 * 表格行高必须落在可绘制的页面高度内；Compose 的 Constraints 不能表示数十万 dp 的行高，
 * 超限时截断并交由上层提示，而不是把原始行高传给 Modifier.height。
 */
object DocxTableRowPolicy {
    fun limit(rawHeightDp: Float, contentHeightDp: Float): DocxRowHeight {
        val cap = contentHeightDp.coerceAtLeast(1f)
        val safe = if (!rawHeightDp.isFinite() || rawHeightDp <= 0f) cap else rawHeightDp
        val limited = safe.coerceAtMost(cap)
        return DocxRowHeight(limited, limited < rawHeightDp || !rawHeightDp.isFinite() || rawHeightDp <= 0f)
    }
}


/** 宽屏居中框架：整张纸保持测量宽度，留白只加在纸张两侧，不能压窄页面内容。 */
data class DocxPageFrame(
    val widthDp: Float,
    val pageWidthDp: Float,
    val sidePaddingDp: Float,
)

object DocxPageFramePolicy {
    fun compute(availableWidthDp: Float, pageWidthDp: Float): DocxPageFrame {
        val available = availableWidthDp.coerceAtLeast(1f)
        val page = pageWidthDp.coerceAtLeast(1f)
        // 视口小于最小纸张时，框架至少容纳整张纸，否则页面会被压窄且横向滚动无法找回。
        val frameWidth = maxOf(available, page)
        return DocxPageFrame(
            widthDp = frameWidth,
            pageWidthDp = page,
            sidePaddingDp = ((frameWidth - page) / 2f).coerceAtLeast(0f),
        )
    }
}


/** 超高单元格的可见行前缀：只保留底部不超过剩余高度的行，后续内容标记截断。 */
object DocxCellPrefixPolicy {
    fun lastVisibleLine(lineBottomsDp: List<Float>, availableHeightDp: Float): Int {
        if (!availableHeightDp.isFinite() || availableHeightDp <= 0f) return -1
        var last = -1
        for ((index, bottom) in lineBottomsDp.withIndex()) {
            if (!bottom.isFinite() || bottom > availableHeightDp + 0.5f) break
            last = index
        }
        return last
    }
}
