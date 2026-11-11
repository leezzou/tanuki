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

/** 时间线条目的稳定 key：LazyColumn、锚点与滚动恢复共用同一身份。 */
fun DshTimelineItem.stableKey(): String = when (this) {
    is DshTimelineItem.Text ->
        if (message.pending) "pending-${message.seq}" else "message-${message.seq}"
    is DshTimelineItem.Tool -> tool.key
}

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
     * 按 LazyColumn 的实际渲染顺序拼出可定位行的 key：顶部提示、历史段、锚点分隔行、
     * 新消息段。恢复视口时必须使用这份顺序，不能只按时间线索引加顶部行数，
     * 否则锚点之后的消息会少算一行而跳到前一条。
     */
    fun renderKeys(
        historyKeys: List<String>,
        liveKeys: List<String>,
        topKey: String?,
        anchorKey: String?
    ): List<String> = buildList {
        topKey?.let(::add)
        addAll(historyKeys)
        anchorKey?.let(::add)
        addAll(liveKeys)
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

/** 一次内存窗口裁剪的结果；消息与工具按同一视口边界一起释放。 */
data class DshChatTrim(
    val messages: List<DshMessage>,
    val tools: List<DshToolItem>,
    val releasedMessages: Int,
    val releasedTools: Int,
    val messageCutSeq: Int,
    val toolCutSeq: Int
) {
    val trimmed: Boolean get() = releasedMessages > 0 || releasedTools > 0

    /** 消息与工具两条释放边界中较大的 seq；只用于判断窗口是否发生变化。 */
    val cutSeq: Int get() = maxOf(messageCutSeq, toolCutSeq)
}

/**
 * 内存窗口策略。
 *
 * 官方历史允许向前分页后，不能把整段会话一直留在内存里：消息保留最近 [MAX_MESSAGES] 条、
 * 工具保留最近 [MAX_TOOLS] 张，超出时只释放**视口上方**的行。正在阅读的那一屏不会被释放，
 * 因此不需要用“补位滚动”掩盖内容消失；界面再按行 key 恢复头部插入/释放造成的索引变化。
 *
 * 工具不能用“合并时截断最新 N 张”来实现上限：那样向前分页返回的更早工具会在合并瞬间
 * 被丢弃，文本却保留下来。合并阶段必须完整保留，统一在这里按视口与预算释放。
 */
object DshChatWindowPolicy {
    /** 内存中保留的消息条数上限；更早的行按需重新读取官方历史页。 */
    const val MAX_MESSAGES = 500

    /** 内存中保留的工具卡上限；与消息共用视口边界，只释放视口上方的旧卡片。 */
    const val MAX_TOOLS = 200

    /** 向前分页每页条数；与官方 `session/page` 的 `maxMessages` 对齐。 */
    const val PAGE_SIZE = 50

    /**
     * 同时裁剪消息与工具。`keepFromSeq` 是视口内第一条时间线行的 seq；
     * 为 null 表示没有可参考的视口（例如列表尚未布局），此时只按预算保留最新行。
     */
    fun trim(
        messages: List<DshMessage>,
        tools: List<DshToolItem> = emptyList(),
        keepFromSeq: Int?,
        messageBudget: Int = MAX_MESSAGES,
        toolBudget: Int = MAX_TOOLS
    ): DshChatTrim {
        val durable = messages.filterNot { it.pending }
        val releasableMessages = if (keepFromSeq == null) durable else durable.filter { it.seq < keepFromSeq }
        val releaseMessages = if (durable.size <= messageBudget) {
            0
        } else {
            minOf(durable.size - messageBudget, releasableMessages.size)
        }
        val messageCut = if (releaseMessages > 0) releasableMessages[releaseMessages - 1].seq else Int.MIN_VALUE
        val keptMessages = if (releaseMessages > 0) {
            messages.filterNot { !it.pending && it.seq <= messageCut }
        } else {
            messages
        }

        // 先释放与已释放消息同段或更早的工具，再按工具预算释放视口上方的旧卡片。
        var keptTools = if (messageCut == Int.MIN_VALUE) tools else tools.filter { it.seq > messageCut }
        var releasedTools = tools.size - keptTools.size
        var toolCut = messageCut
        if (keptTools.size > toolBudget) {
            val releasableTools = if (keepFromSeq == null) {
                keptTools
            } else {
                keptTools.filter { it.seq < keepFromSeq }
            }
            val count = minOf(keptTools.size - toolBudget, releasableTools.size)
            if (count > 0) {
                toolCut = releasableTools[count - 1].seq
                keptTools = keptTools.filter { it.seq > toolCut }
                releasedTools += count
            }
        }
        return DshChatTrim(
            messages = keptMessages,
            tools = keptTools,
            releasedMessages = releaseMessages,
            releasedTools = releasedTools,
            messageCutSeq = messageCut,
            toolCutSeq = toolCut
        )
    }

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
