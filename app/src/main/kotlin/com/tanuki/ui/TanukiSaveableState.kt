package com.tanuki.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver

/**
 * 原生/官方切换状态的 Bundle Saver。
 *
 * data class 本身不实现 Parcelable/Serializable，直接放进 `rememberSaveable`
 * 会在进程重建时失败；这里显式映射为可保存的字符串、整数和布尔值。
 */
val TanukiDshSwitchStateSaver: Saver<TanukiDshSwitchState, Any> = mapSaver(
    save = { state ->
        buildMap {
            put("native", state.native)
            putPane("nativePane", state.nativePane)
            putPane("officialPane", state.officialPane)
        }
    },
    restore = { saved ->
        TanukiDshSwitchState(
            native = saved["native"] as? Boolean ?: false,
            nativePane = saved.pane("nativePane"),
            officialPane = saved.pane("officialPane"),
        )
    },
)

/** 会话管理二级页面状态 Saver；关闭面板后列表与滚动位置可跨重建恢复。 */
val TanukiSecondaryPageStateSaver: Saver<TanukiSecondaryPageState, Any> = mapSaver(
    save = { state ->
        mapOf(
            "panels" to ArrayList(state.panels),
            "items" to ArrayList(state.listItems),
            "index" to state.listScrollIndex,
            "offset" to state.listScrollOffset,
        )
    },
    restore = { saved ->
        TanukiSecondaryPageState(
            panels = saved.stringList("panels"),
            listItems = saved.stringList("items"),
            listScrollIndex = (saved["index"] as? Number)?.toInt() ?: 0,
            listScrollOffset = (saved["offset"] as? Number)?.toInt() ?: 0,
        )
    },
)

private fun MutableMap<String, Any?>.putPane(prefix: String, pane: TanukiDshPaneState) {
    put("$prefix.session", pane.sessionId)
    put("$prefix.index", pane.listScrollIndex)
    put("$prefix.offset", pane.listScrollOffset)
    put("$prefix.draft", pane.draft)
}

private fun Map<String, Any?>.pane(prefix: String): TanukiDshPaneState = TanukiDshPaneState(
    sessionId = this["$prefix.session"] as? String,
    listScrollIndex = (this["$prefix.index"] as? Number)?.toInt() ?: 0,
    listScrollOffset = (this["$prefix.offset"] as? Number)?.toInt() ?: 0,
    draft = this["$prefix.draft"] as? String ?: "",
)

private fun Map<String, Any?>.stringList(key: String): List<String> =
    (this[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
