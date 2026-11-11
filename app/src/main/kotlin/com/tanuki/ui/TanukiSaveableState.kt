package com.tanuki.ui

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.mapSaver
import com.tanuki.storage.WorkspaceFileManager

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

/** 阅读页显示状态 Saver；编码、滚动偏移和行号开关随 Activity 重建恢复。 */
val ReaderViewStateSaver: Saver<ReaderViewState, Any> = listSaver(
    save = { state -> listOf(state.encoding, state.scrollOffset, state.showLineNumbers) },
    restore = { saved ->
        ReaderViewState(
            encoding = saved.getOrNull(0) as? String ?: "自动",
            scrollOffset = (saved.getOrNull(1) as? Number)?.toInt() ?: 0,
            showLineNumbers = saved.getOrNull(2) as? Boolean ?: true,
        ).normalized()
    },
)

/**
 * 当前打开文件的 Saver。
 *
 * 文件条目是普通 data class，不能直接放进 Bundle；保存恢复阅读所需字段，
 * 特别是 `linked`，避免重建后把符号链接误判为可编辑文件。
 */
val WorkspaceEntrySaver: Saver<WorkspaceFileManager.Entry?, Any> = Saver(
    save = { entry ->
        entry?.let {
            listOf(it.path, it.name, it.directory, it.bytes, it.linked, it.modified)
        }
    },
    restore = { saved ->
        (saved as? List<*>)?.takeIf { it.size >= 6 }?.let { values ->
            WorkspaceFileManager.Entry(
                path = values[0] as? String ?: return@let null,
                name = values[1] as? String ?: return@let null,
                directory = values[2] as? Boolean ?: false,
                bytes = (values[3] as? Number)?.toLong() ?: 0L,
                linked = values[4] as? Boolean ?: false,
                modified = (values[5] as? Number)?.toLong() ?: 0L,
            )
        }
    },
)
