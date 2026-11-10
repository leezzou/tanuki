package com.tanuki.ui

/**
 * PDF 位图内存上限（纯 Kotlin）。
 *
 * 策略目标：
 * - 缓存预算随应用堆大小缩放，但限制在 24–64 MiB，避免低内存设备一次缓存过多页；
 * - 单页像素上限按「预算至少容纳 3 页」反推，同时不超过渲染策略的 4,000,000 像素；
 * - 一页渲染失败也不会因位图总量超过堆上限而拖垮进程。
 */
internal object PdfMemoryPolicy {
    const val BYTES_PER_PIXEL = 4
    const val MIN_CACHE_BYTES = 24L * 1024 * 1024
    const val MAX_CACHE_BYTES = 64L * 1024 * 1024
    const val CACHE_SLOTS = 3

    fun cacheBudgetBytes(maxHeapBytes: Long): Long {
        if (maxHeapBytes <= 0L) return MIN_CACHE_BYTES
        return (maxHeapBytes / 8).coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
    }

    fun pageBytes(width: Int, height: Int): Long {
        require(width > 0 && height > 0) { "PDF 页位图尺寸无效" }
        return width.toLong() * height.toLong() * BYTES_PER_PIXEL
    }

    /**
     * 单页可用的最大像素数。默认保证预算内至少放 3 页；连续阅读时由界面传入实际驻留页数
     * （可见页 + 预加载 + 一页在途位图），使受保护页面的总字节数不超过缓存预算。
     */
    fun maxPagePixels(
        cacheBudgetBytes: Long,
        residentPages: Int = CACHE_SLOTS,
    ): Int {
        val budget = cacheBudgetBytes.coerceAtLeast(1L)
        val slots = residentPages.coerceAtLeast(1)
        val pixels = budget / BYTES_PER_PIXEL / slots
        return pixels.coerceIn(1L, PdfRenderPolicy.MAX_PIXELS.toLong()).toInt()
    }
}

/** PDF 打开前的文件大小限制；超过时给出明确中文提示而不是尝试加载。 */
internal object PdfOpenPolicy {
    // 与外部 content:// 查看共用同一上限，避免两条入口给出不同大小边界。
    const val MAX_FILE_BYTES = com.tanuki.storage.ExternalDocumentPolicy.MAX_PDF_BYTES

    fun fileTooLarge(bytes: Long): Boolean = bytes > MAX_FILE_BYTES
}
