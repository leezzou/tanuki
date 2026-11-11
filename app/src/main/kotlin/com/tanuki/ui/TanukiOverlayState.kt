package com.tanuki.ui

/** 仅保存最后展示的 UI 模型；业务许可与可操作性始终取当前 open 状态。 */
internal data class TanukiOverlayState<T : Any>(val shown: T? = null, val open: Boolean = false) {
    fun update(value: T?): TanukiOverlayState<T> =
        if (value != null) TanukiOverlayState(value, true) else copy(open = false)

    /** 旧退出完成不能清除已经重新打开的内容。 */
    fun hidden(): TanukiOverlayState<T> = if (open) this else TanukiOverlayState()
}
