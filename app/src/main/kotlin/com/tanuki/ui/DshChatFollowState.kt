package com.tanuki.ui

import com.tanuki.web.DshTimelineItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 跟随状态快照：跟随时锚点为空，脱离跟随时锚点固定为用户离开底部那一刻的最后一行。 */
data class DshChatFollowSnapshot(
    val following: Boolean = true,
    val anchor: DshChatAnchor? = null
)

/**
 * 会话级跟随状态。
 *
 * 每个会话一份，页面用 `remember(selectedId)` 重建；nested scroll 等长生命周期监听
 * 必须通过 `rememberUpdatedState` 读取当前实例，不能捕获某个会话的 Compose 状态委托，
 * 否则 A→B 切换后手势仍会读写 A 的状态，B 则永远贴底、没有胶囊也不会自动分页。
 */
class DshChatFollowState {
    private val mutableState = MutableStateFlow(DshChatFollowSnapshot())
    val state: StateFlow<DshChatFollowSnapshot> = mutableState.asStateFlow()

    /** 当前快照；供监听回调同步读取，避免再捕获一份可变状态。 */
    val snapshot: DshChatFollowSnapshot get() = mutableState.value

    /**
     * 用户手势离开底部：把当前最后一行冻结为锚点，后续流式增量/新消息只进入锚点下方。
     * 已在底部、已经脱离或列表为空时返回 false。
     */
    fun detach(timeline: List<DshTimelineItem>): Boolean {
        val current = mutableState.value
        if (!current.following || timeline.isEmpty()) return false
        mutableState.value = DshChatFollowSnapshot(
            following = false,
            anchor = timeline.last().let { DshChatAnchor(it.stableKey(), it.seq) }
        )
        return true
    }

    /**
     * 回到底部时按当前状态恢复跟随。滚动监听必须调用本方法而不是捕获一个 `following`
     * 局部值，否则监听只看到首次组合时的值，用户上翻后再滚回底部也不会恢复。
     */
    fun reattachIfDetached(): Boolean = if (snapshot.following) false else reattach()

    /** 回到底部：恢复跟随并清除锚点；返回是否发生状态变化。 */
    fun reattach(): Boolean {
        val current = mutableState.value
        if (current.following && current.anchor == null) return false
        mutableState.value = DshChatFollowSnapshot()
        return true
    }
}
