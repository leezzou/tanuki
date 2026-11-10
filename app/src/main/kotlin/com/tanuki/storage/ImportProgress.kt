package com.tanuki.storage

/**
 * 导入进度只使用完整的真实总量：只有所有文件都提供有效大小时才显示确定百分比，
 * 任一大小未知就返回 -1 交给界面显示不定进度，绝不估算或伪造总量。
 */
object ImportProgress {
    const val UNKNOWN_TOTAL = -1L

    fun totalBytes(sizes: List<Long?>): Long {
        if (sizes.isEmpty()) return UNKNOWN_TOTAL
        var total = 0L
        for (size in sizes) {
            if (size == null || size < 0) return UNKNOWN_TOTAL
            if (total > Long.MAX_VALUE - size) return UNKNOWN_TOTAL
            total += size
        }
        return total
    }

    fun fraction(received: Long, total: Long): Float? =
        if (total > 0) (received.toDouble() / total).toFloat().coerceIn(0f, 1f) else null
}
