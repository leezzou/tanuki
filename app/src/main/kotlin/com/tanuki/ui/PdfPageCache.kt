package com.tanuki.ui

/**
 * PDF 页位图的 LRU 记账缓存（纯 Kotlin，可在 JVM 测试）。
 *
 * 只保存「页索引 → 字节数」并决定淘汰顺序；真正的 Bitmap 由 [PdfPageMemory] 持有和回收。
 * 迭代顺序为最近最少使用在前，因此超预算时先淘汰最久未访问的页。
 */
internal class PdfPageCache<K : Any>(val budgetBytes: Long) {
    private val sizes = LinkedHashMap<K, Long>(16, 0.75f, true)
    private var used = 0L

    init {
        require(budgetBytes > 0L) { "PDF 页缓存预算必须为正数" }
    }

    val usedBytes: Long get() = used
    val size: Int get() = sizes.size

    fun contains(key: K): Boolean = sizes.containsKey(key)

    /** 按最近最少使用到最近的顺序返回键，供实际驻留字节预留时选择淘汰对象。 */
    fun keysInLruOrder(): List<K> = sizes.keys.toList()

    fun bytesOf(key: K): Long? = sizes[key]

    /** 标记一次访问，使该页排到 LRU 队尾。 */
    fun touch(key: K) {
        if (sizes.containsKey(key)) sizes[key]
    }

    /**
     * 放入或更新一页并立即按预算淘汰。
     * @return 被淘汰的键，可能包含刚放入的键（单页本身就超过预算时）。
     */
    fun put(key: K, bytes: Long, protectedKeys: Set<K> = emptySet()): List<K> {
        require(bytes > 0L) { "PDF 页位图字节数必须为正数" }
        sizes.remove(key)?.let { used -= it }
        sizes[key] = bytes
        used += bytes
        return evictToBudget(protectedKeys)
    }

    /**
     * 淘汰到预算以内。保护键（当前页与预加载窗口）即使超预算也不淘汰，
     * 由调用方通过单页像素上限保证窗口本身不会无限增长。
     */
    fun evictToBudget(protectedKeys: Set<K>): List<K> {
        if (used <= budgetBytes) return emptyList()
        val victims = mutableListOf<K>()
        val iterator = sizes.entries.iterator()
        while (used > budgetBytes && iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key in protectedKeys) continue
            used -= entry.value
            iterator.remove()
            victims += entry.key
        }
        return victims
    }

    fun remove(key: K): Boolean {
        val bytes = sizes.remove(key) ?: return false
        used -= bytes
        return true
    }

    fun clear(): List<K> {
        val keys = sizes.keys.toList()
        sizes.clear()
        used = 0L
        return keys
    }
}
