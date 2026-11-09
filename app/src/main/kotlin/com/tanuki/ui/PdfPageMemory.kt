package com.tanuki.ui

/**
 * PDF 页位图的生命周期管理（纯 Kotlin，可在 JVM 用假资源测试）。
 *
 * 除预算淘汰外还要处理一个绘制安全约束：仍在 Compose 组合中显示的位图不能立刻回收，
 * 否则下一帧可能绘制到已回收的 Bitmap。因此被淘汰但仍显示的值会暂存到该页离开组合，
 * 由 [hide] 回收；[close] 统一回收全部剩余值。
 */
internal class PdfPageMemory<K : Any, V : Any>(
    budgetBytes: Long,
    private val sizeOf: (V) -> Long,
    private val release: (V) -> Unit,
) {
    private val cache = PdfPageCache<K>(budgetBytes)
    private val values = HashMap<K, V>()
    private val displayed = HashSet<K>()

    /** 已替换但仍显示、需要等离开组合后再回收的旧值。 */
    private val retired = HashMap<K, MutableList<V>>()
    private val lock = Any()
    private var closed = false

    fun contains(key: K): Boolean = synchronized(lock) { !closed && values.containsKey(key) }

    fun isDisplayed(key: K): Boolean = synchronized(lock) { key in displayed }

    fun get(key: K): V? = synchronized(lock) {
        if (closed) return null
        val value = values[key] ?: return null
        cache.touch(key)
        value
    }

    fun usedBytes(): Long = synchronized(lock) { cache.usedBytes }

    /** 页面进入组合：加入绘制保护，避免淘汰时回收仍在显示的位图。 */
    fun display(key: K) {
        synchronized(lock) {
            if (!closed) displayed += key
        }
    }

    /** 页面离开组合：若该页已被预算淘汰，立即回收，避免长期占用内存。 */
    fun hide(key: K) {
        val toRelease = mutableListOf<V>()
        synchronized(lock) {
            displayed -= key
            if (closed) return
            if (!cache.contains(key)) {
                values.remove(key)?.let { toRelease += it }
            }
            retired.remove(key)?.let { toRelease += it }
        }
        toRelease.forEach(release)
    }

    /**
     * 存入一页并返回被淘汰的键，调用方应同步移除界面中对应的页。
     * 返回值保证：传入值要么被保存，要么被 [release]（文档已关闭等边界情况）。
     */
    fun store(key: K, value: V, protectedKeys: Set<K>): Set<K> {
        val evicted = mutableSetOf<K>()
        val toRelease = mutableListOf<V>()
        val wasClosed = synchronized(lock) {
            if (closed) {
                toRelease += value
                return@synchronized true
            }
            val previous = values.put(key, value)
            if (previous != null) {
                // 同页重渲染：旧位图可能仍在绘制，延迟到离开组合后回收。
                if (key in displayed) {
                    retired.getOrPut(key) { mutableListOf() } += previous
                } else {
                    toRelease += previous
                }
            }
            val keys = cache.put(key, sizeOf(value), protectedKeys)
            evicted += keys
            toRelease += collectEvictedLocked(keys)
            false
        }
        if (wasClosed) {
            toRelease.forEach(release)
            return emptySet()
        }
        toRelease.forEach(release)
        return evicted
    }

    /**
     * 主动放弃一页：从预算记账中移除；若仍显示，位图保留到 [hide] 再回收。
     * 用于渲染分辨率收紧时先释放超出新上限的旧页，避免新旧位图同时驻留。
     */
    fun discard(key: K) {
        val toRelease = mutableListOf<V>()
        synchronized(lock) {
            if (closed) return
            cache.remove(key)
            if (key !in displayed) values.remove(key)?.let { toRelease += it }
        }
        toRelease.forEach(release)
    }

    /** 关闭文档：回收全部缓存值与延迟回收值。 */
    fun close() {
        val toRelease = mutableListOf<V>()
        synchronized(lock) {
            if (closed) return
            closed = true
            toRelease += values.values
            retired.values.forEach { toRelease += it }
            values.clear()
            retired.clear()
            displayed.clear()
            cache.clear()
        }
        toRelease.forEach(release)
    }

    /** 必须在持锁状态下调用；只收集被淘汰且未显示的页。 */
    private fun collectEvictedLocked(keys: List<K>): List<V> {
        val result = mutableListOf<V>()
        for (key in keys) {
            if (key in displayed) continue
            values.remove(key)?.let { result += it }
        }
        return result
    }
}
