package com.tanuki.ui

/** 仅保存最后展示的 UI 模型；业务许可与可操作性始终取当前 open 状态。 */
internal data class TanukiOverlayState<T : Any>(
    val shown: T? = null,
    val open: Boolean = false,
    val generation: Long = 0,
) {
    fun update(value: T?): TanukiOverlayState<T> {
        val nextOpen = value != null
        return copy(
            shown = value ?: shown,
            open = nextOpen,
            generation = if (open != nextOpen) generation + 1 else generation,
        )
    }

    /** 旧退出不能清除快速重开或重开后再次关闭的新代次。 */
    fun hidden(expectedGeneration: Long = generation): TanukiOverlayState<T> =
        if (open || generation != expectedGeneration) this else copy(shown = null)
}

/** 即使系统手势取消 hide，也同步释放同一关闭代次的窗口；不吞掉协程取消。 */
internal suspend fun <T : Any> finishTanukiOverlayExit(
    generation: Long,
    hide: suspend () -> Unit,
    current: () -> TanukiOverlayState<T>,
    update: (TanukiOverlayState<T>) -> Unit,
) {
    try {
        hide()
    } finally {
        val before = current()
        val after = before.hidden(generation)
        if (after != before) update(after)
    }
}
