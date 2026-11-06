package com.tanuki.ui

/**
 * 视频全屏方向策略：只在视频全屏期间临时允许随设备旋转，退出时恢复进入前的方向。
 * 纯 Kotlin 状态机，不依赖 Android 框架，便于在 JVM 上回归方向取舍。
 */
object VideoFullscreenPolicy {
    // 数值与 ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR 一致；全屏期间允许竖屏/横屏随设备旋转，不强制横屏。
    const val FULL_SENSOR = 10

    // 数值与 ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED 一致；未显式设置过方向时的系统值。
    const val UNSPECIFIED = -1

    /**
     * 一次视频全屏的方向会话。进入时记录原方向，重复进入不覆盖；
     * 退出时返回需要恢复的方向，未进入过全屏则返回 null 表示不改动方向。
     */
    class Session {
        private var restoreTo: Int? = null

        /** 返回进入全屏后应请求的方向；已处于全屏时保持可旋转且不改写恢复值。 */
        fun enter(current: Int): Int {
            if (restoreTo == null) restoreTo = current
            return FULL_SENSOR
        }

        /** 返回进入全屏前的方向；没有可恢复状态时返回 null。 */
        fun exit(): Int? {
            val previous = restoreTo ?: return null
            restoreTo = null
            return previous
        }

        /** 是否仍处于视频全屏方向会话中。 */
        val active: Boolean get() = restoreTo != null
    }
}
