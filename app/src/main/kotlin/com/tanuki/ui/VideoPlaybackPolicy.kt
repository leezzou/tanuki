package com.tanuki.ui

/** 快进只在真实时长内移动，使用长整数计算以免长视频进度溢出。 */
object VideoPlaybackPolicy {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    fun skip(position: Int, delta: Int, duration: Int): Int =
        (position.toLong() + delta).coerceIn(0L, duration.coerceAtLeast(0).toLong()).toInt()
    fun acceptsSpeed(speed: Float): Boolean = speed in speeds
}
