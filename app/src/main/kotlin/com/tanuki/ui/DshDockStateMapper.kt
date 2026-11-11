package com.tanuki.ui

import com.tanuki.web.DshGoalProjection
import com.tanuki.web.DshTodoProjection
import java.util.Locale

/**
 * 官方目标/待办投影到停靠条纯状态的唯一映射。
 *
 * - 只映射已知状态；未知/已完成目标返回 null，停靠条隐藏，不猜“进行中”。
 * - 待办只保留有文本的行；完成/进行中之外的未知状态按未完成展示，不伪造完成度。
 * - 空数据返回 null，组件因此不占高度。
 */
object DshDockStateMapper {
    const val MAX_TODO_ITEMS = 200

    fun goal(projection: DshGoalProjection?): GoalDockState? {
        val source = projection ?: return null
        val summary = source.summary.trim()
        if (summary.isEmpty()) return null
        val status = when (source.status?.lowercase(Locale.ROOT)) {
            "running", "active", "in_progress", "in-progress", "started", "open" ->
                GoalDockStatus.RUNNING
            "paused", "pause", "suspended", "on_hold", "on-hold" ->
                GoalDockStatus.PAUSED
            "blocked", "error", "failed", "stalled" ->
                GoalDockStatus.BLOCKED
            // 已完成、已清除或未知状态不显示成进行中，避免误导。
            else -> return null
        }
        return GoalDockState(
            summary = summary,
            status = status,
            detail = source.detail?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    fun todo(projection: DshTodoProjection?): TodoDockState? {
        val items = projection?.items.orEmpty().asSequence()
            .mapIndexedNotNull { index, item ->
                val text = item.text.trim()
                if (text.isEmpty()) return@mapIndexedNotNull null
                TodoDockItem(
                    id = item.id.trim().ifEmpty { "todo-$index" },
                    text = text,
                    status = when (item.status?.lowercase(Locale.ROOT)) {
                        "completed", "complete", "done", "finished", "success" ->
                            TodoDockItemStatus.COMPLETED
                        "in_progress", "in-progress", "running", "active", "started", "doing" ->
                            TodoDockItemStatus.IN_PROGRESS
                        else -> TodoDockItemStatus.PENDING
                    }
                )
            }
            .take(MAX_TODO_ITEMS)
            .toList()
        return if (items.isEmpty()) null else TodoDockState(items)
    }
}
