package com.tanuki.ui

import com.tanuki.web.DshMessage
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshTimelineItem
import com.tanuki.web.DshToolItem

/**
 * 聊天列表的锚点：`key` 用于在头部插入/释放后定位同一行，`seq` 是官方事件序号。
 * 锚点只描述“用户离开底部那一刻的最后一行”，不是屏幕上的滚动位置。
 */
data class DshChatAnchor(val key: String, val seq: Int)

/** 两段结构：历史段（含锚点行）向上增长，新消息段从锚点下方向下增长。 */
data class DshChatSplit<T>(
    val history: List<T>,
    val live: List<T>
) {
    val hasLive: Boolean get() = live.isNotEmpty()
}

/**
 * 锚定与跟随策略。
 *
 * 列表被逻辑上切成两段：历史段只会在头部插入更早历史，新消息段只在尾部追加。
 * 用户脱离底部后，视口停在历史段里，新增内容全部落在锚点下方，因此流式增量不会把
 * 正在阅读的位置拉走；用户回到底部时才恢复跟随。
 */
object DshChatAnchorPolicy {
    /**
     * 按锚点 key 把列表切成两段。锚点行属于历史段。
     * 锚点已被窗口释放或切换会话时返回 null，调用方回退为“整段历史”并重新贴底。
     */
    fun <T> split(items: List<T>, anchorKey: String?, keyOf: (T) -> String): DshChatSplit<T>? {
        if (anchorKey == null) return null
        val index = items.indexOfFirst { keyOf(it) == anchorKey }
        if (index < 0) return null
        return DshChatSplit(
            history = items.subList(0, index + 1).toList(),
            live = items.subList(index + 1, items.size).toList()
        )
    }

    /** 新消息段中的真实文本消息数；本地发送占位和工具卡不计入「N 条新消息」。 */
    fun unseenMessages(live: List<DshTimelineItem>): Int =
        live.count { it is DshTimelineItem.Text && !it.message.pending }

    /**
     * 头部插页/释放后按行 key 计算新的列表索引；key 已不在列表时返回 null。
     * [leadingRows] 是列表顶部不属于时间线内容的固定行数（顶部历史提示行）。
     */
    fun restoreIndex(keys: List<String>, key: String, leadingRows: Int = 1): Int? {
        if (key.isBlank()) return null
        val index = keys.indexOf(key)
        return if (index < 0) null else index + leadingRows.coerceAtLeast(0)
    }

    /**
     * 用户手势开始时是否脱离跟随：只有用户输入产生、且已经离开底部的滚动才冻结锚点。
     * 程序滚动（自动贴底、恢复视口）和底部小幅回弹都不会误触发，避免留下空胶囊。
     */
    fun shouldDetachOnUserScroll(
        userInput: Boolean,
        delta: Float,
        atBottom: Boolean,
        following: Boolean
    ): Boolean = userInput && delta != 0f && following && !atBottom
}

/** 一次内存窗口裁剪的结果；`kept` 保持原顺序，`cutSeq` 是本次释放行中最大的 seq。 */
data class DshMessageWindow(
    val kept: List<DshMessage>,
    val released: Int,
    val cutSeq: Int
) {
    val trimmed: Boolean get() = released > 0
}

/**
 * 内存窗口策略。
 *
 * 官方历史允许向前分页后，不能把整段会话一直留在内存里：保留最近 [MAX_MESSAGES] 条，
 * 超出时只释放**视口上方**的行。正在阅读的那一屏不会被释放，因此不需要用“补位滚动”
 * 掩盖内容消失；界面再按行 key 恢复头部插入/释放造成的索引变化。
 */
object DshChatWindowPolicy {
    /** 内存中保留的消息条数上限；更早的行按需重新读取官方历史页。 */
    const val MAX_MESSAGES = 500

    /** 向前分页每页条数；与官方 `session/page` 的 `maxMessages` 对齐。 */
    const val PAGE_SIZE = 50

    /**
     * 只释放视口上方的行。`keepFromSeq` 是视口内第一条时间线行的 seq；
     * 为 null 表示没有可参考的视口（例如列表尚未布局），此时只按预算保留最新行。
     */
    fun trim(messages: List<DshMessage>, keepFromSeq: Int?, budget: Int = MAX_MESSAGES): DshMessageWindow {
        val durable = messages.filterNot { it.pending }
        if (durable.size <= budget) return DshMessageWindow(messages, 0, Int.MIN_VALUE)
        val releasable = if (keepFromSeq == null) durable else durable.filter { it.seq < keepFromSeq }
        val count = minOf(durable.size - budget, releasable.size)
        if (count <= 0) return DshMessageWindow(messages, 0, Int.MIN_VALUE)
        val cutSeq = releasable[count - 1].seq
        return DshMessageWindow(
            kept = messages.filterNot { !it.pending && it.seq <= cutSeq },
            released = count,
            cutSeq = cutSeq
        )
    }

    /** 头部消息释放后同步丢弃更早的工具行，保持文本与工具时间线一致。 */
    fun trimTools(tools: List<DshToolItem>, cutSeq: Int): List<DshToolItem> =
        if (cutSeq == Int.MIN_VALUE) tools else tools.filter { it.seq > cutSeq }

    /** 当前窗口内最早的行 seq；文本与工具共用官方事件序号。 */
    fun oldestSeq(messages: List<DshMessage>, tools: List<DshToolItem>): Int? {
        val oldestMessage = messages.filterNot { it.pending }.minOfOrNull { it.seq }
        val oldestTool = tools.minOfOrNull { it.seq }
        return when {
            oldestMessage == null -> oldestTool
            oldestTool == null -> oldestMessage
            else -> minOf(oldestMessage, oldestTool)
        }
    }

    /** 向前分页的 `throughSeq` 上界；没有更早空间（最早的 seq 为 0）时返回 null。 */
    fun beforeSeq(oldest: Int?): Int? = oldest?.takeIf { it > 0 }?.minus(1)
}

/**
 * 官方消息账本：历史页、follow 快照与实时事件都经这里合并。
 *
 * - 以官方 seq 为唯一键，同 seq 只保留一份并按 seq 排序，重复投递/重连重放不会插入第二条；
 * - 同 seq 再次到达时以新内容为准（官方落盘消息会补全文本）；
 * - 本地发送占位只在官方出现相同文本的用户消息后移除；
 * - 快照只覆盖自己的 seq 区间，不删除已经加载的更早分页，也不删除比快照更新的实时行。
 */
object DshMessageLedger {
    fun merge(current: List<DshMessage>, incoming: List<DshMessage>): List<DshMessage> {
        val durable = LinkedHashMap<Int, DshMessage>()
        current.filterNot { it.pending }.forEach { durable[it.seq] = it }
        incoming.filterNot { it.pending }.forEach { durable[it.seq] = it }
        val officialUserTexts = durable.values
            .filter { it.role == DshMessageRole.USER }
            .mapTo(HashSet()) { it.text }
        val pending = current.filter { it.pending && it.text !in officialUserTexts }
        return assemble(durable.values, pending)
    }

    /**
     * 用实时快照对账：快照区间内的旧副本更新为新内容，区间外更早的分页与更新的实时行保留。
     * 空快照不删除任何已加载内容，避免重连瞬间清空历史。
     */
    fun reconcile(current: List<DshMessage>, snapshot: List<DshMessage>): List<DshMessage> {
        val durableSnapshot = snapshot.filterNot { it.pending }
        if (durableSnapshot.isEmpty()) return merge(current, emptyList())
        val minSeq = durableSnapshot.minOf { it.seq }
        val maxSeq = durableSnapshot.maxOf { it.seq }
        val outside = current.filterNot { !it.pending && it.seq in minSeq..maxSeq }
        return merge(outside, durableSnapshot)
    }

    /** 本地占位永远排在官方消息之后，并按发送时间排序。 */
    private fun assemble(
        durable: Collection<DshMessage>,
        pending: List<DshMessage>
    ): List<DshMessage> = (durable + pending).sortedWith(
        compareBy<DshMessage> { it.pending }.thenBy { if (it.pending) it.time else it.seq.toLong() }
    )
}
