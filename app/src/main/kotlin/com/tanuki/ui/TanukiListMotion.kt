package com.tanuki.ui

import com.tanuki.ui.design.TanukiListMotion
import com.tanuki.ui.design.TanukiMotionSpecs
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshTimelineItem

private val ImmediateListMotion = TanukiListMotion(null, null, null)

/** 数据集身份不含排序和条目；换来源或筛选时重建 Lazy 动效宿主，滚动状态留在宿主之外。 */
internal data class TanukiListDataset(val source: String, val query: String = "", val filter: String = "") {
    fun motion(specs: TanukiMotionSpecs, filtering: Boolean = query.isNotEmpty()): TanukiListMotion =
        if (filtering) ImmediateListMotion else specs.list()
}

/** 异步搜索保留旧列表时也保留旧身份；结果真正提交时才重置行动画，清空查询同样适用。 */
internal fun fileListMotionDataset(display: FileDisplayResult?, request: FileDisplayRequest): TanukiListDataset {
    val shown = display?.request?.takeIf { it.source == request.source } ?: request
    return TanukiListDataset(request.source, shown.query, shown.hidden.toString())
}

internal fun sessionListMotionDataset(state: DshSessionManagerState): TanukiListDataset =
    TanukiListDataset("sessions", state.query.trim(), state.filter.name)

/**
 * 只记录展示资格与序号，不保存正文、不参与分组/锚点/滚动计算。
 * 首屏、加载完成与历史 revision 变化全量立即显示；序号水位防止重读已释放历史时重新入场。
 */
internal data class DshMessageMotionSnapshot(
    val ready: Boolean = false,
    val newestSeq: Int? = null,
    val historyRevision: Int = 0,
    val enteringKeys: Set<String> = emptySet(),
    val suppressNextUser: Boolean = false,
    val suppressNextAssistant: Boolean = false,
    val userPlaceholder: Boolean = false,
    val assistantPlaceholder: Boolean = false,
) {
    fun next(
        rows: List<DshChatRow>,
        revision: Int,
        loading: Boolean,
        sending: Boolean,
        streaming: Boolean,
    ): DshMessageMotionSnapshot {
        val canEnter = ready && !loading && revision == historyRevision
        val entering = if (canEnter) enteringKeys.intersect(rows.mapTo(HashSet()) { it.key }).toMutableSet()
            else mutableSetOf()
        val hasUserPlaceholder = sending || rows.any {
            (it.item as? DshTimelineItem.Text)?.message?.pending == true
        }
        // 只在占位开始时建立一次抑制；正式消息先到、占位信号稍后清除时不能反复补记。
        var skipUser = suppressNextUser || (hasUserPlaceholder && !userPlaceholder)
        var skipAssistant = suppressNextAssistant || (streaming && !assistantPlaceholder)
        var latest = newestSeq
        for (row in rows) {
            val item = row.item
            if (item is DshTimelineItem.Text && item.message.pending) continue
            val isNew = newestSeq == null || item.seq > newestSeq
            if (isNew) {
                val skipReplacement = when ((item as? DshTimelineItem.Text)?.message?.role) {
                    DshMessageRole.USER -> skipUser.also { skipUser = false }
                    DshMessageRole.ASSISTANT -> skipAssistant.also { skipAssistant = false }
                    null -> false
                }
                if (canEnter && !skipReplacement) entering += row.key
            }
            // 工具组的后续成员也推进水位，但不把已有组当作新行。
            val rowMax = maxOf(item.seq, row.tools.maxOfOrNull { it.seq } ?: item.seq)
            latest = latest?.let { maxOf(it, rowMax) } ?: rowMax
        }
        return DshMessageMotionSnapshot(
            ready = !loading,
            newestSeq = latest,
            historyRevision = revision,
            enteringKeys = entering,
            suppressNextUser = skipUser,
            suppressNextAssistant = skipAssistant,
            userPlaceholder = hasUserPlaceholder,
            assistantPlaceholder = streaming,
        )
    }

    /** 聊天永不做 placement 或退出补间，只允许本次追加的已提交行淡入。 */
    fun motion(key: String, specs: TanukiMotionSpecs): TanukiListMotion =
        TanukiListMotion(null, specs.list().fadeIn.takeIf { key in enteringKeys }, null)
}
