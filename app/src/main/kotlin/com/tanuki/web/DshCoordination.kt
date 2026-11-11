package com.tanuki.web

import org.json.JSONArray
import org.json.JSONObject

/** 目标投影；只保留单行摘要、官方状态码与短说明，不保留原始事件。 */
data class DshGoalProjection(
    val seq: Int,
    val summary: String,
    val status: String? = null,
    val detail: String? = null
)

/** 单条待办投影；`id` 用于列表键，缺失时由展示层回退到下标。 */
data class DshTodoItemProjection(
    val id: String,
    val text: String,
    val status: String? = null
)

/** 一份待办清单投影；空清单不产生停靠条。 */
data class DshTodoProjection(
    val seq: Int,
    val items: List<DshTodoItemProjection>
)

/**
 * 一次历史/实时解析得到的目标与待办投影。
 *
 * `null` 表示该类数据在本次来源中不存在，调用方必须保持原值或隐藏停靠条，
 * 不能用默认文案、示例目标或空清单占位。
 */
data class DshCoordination(
    val goal: DshGoalProjection? = null,
    val todos: DshTodoProjection? = null
) {
    val isEmpty: Boolean get() = goal == null && todos == null
}

/**
 * 官方目标/待办事件的保守投影。
 *
 * - 只识别白名单事件类型与待办工具调用；其余事件原样跳过，不猜字段。
 * - 目标必须有非空摘要才产生投影；待办必须有至少一条非空文本。
 * - 同一批记录里同类数据取 seq 最大的一条；后到的空值不覆盖已有内容。
 * - 字段长度与条数都有上限，不保留原始 JSON、参数或凭据。
 *
 * 真实字段仍以设备/官方返回为准；识别失败时停靠条保持隐藏，而不是显示假数据。
 */
object DshCoordinationParser {
    const val MAX_GOAL_CHARS = 240
    const val MAX_DETAIL_CHARS = 320
    const val MAX_TODO_ITEMS = 200
    const val MAX_TODO_TEXT_CHARS = 240
    const val MAX_ID_CHARS = 128

    private val GOAL_EVENTS = setOf(
        "goal/update", "goal", "goals/update", "session/goal"
    )
    private val TODO_EVENTS = setOf("todo/update", "todos/update")
    private val TODO_TOOLS = setOf(
        "todo_write", "write_todo", "todowrite", "update_todos"
    )
    private val ONE_LINE = Regex("\\s+")
    private val TODO_ARRAY_KEYS = listOf("todos", "items", "tasks", "steps", "plan")

    /** 解析历史页/快照的 `records[].event`，同类数据只保留 seq 最大者。 */
    fun parse(records: JSONArray): DshCoordination {
        var goal: DshGoalProjection? = null
        var todos: DshTodoProjection? = null
        for (index in 0 until records.length()) {
            val event = records.optJSONObject(index)?.optJSONObject("event") ?: continue
            val parsed = parseEvent(event) ?: continue
            val nextGoal = parsed.goal
            if (nextGoal != null && (goal == null || nextGoal.seq >= goal.seq)) goal = nextGoal
            val nextTodos = parsed.todos
            if (nextTodos != null && (todos == null || nextTodos.seq >= todos.seq)) todos = nextTodos
        }
        return DshCoordination(goal = goal, todos = todos)
    }

    /** 解析单条 follow `event`；不是目标/待办事件时返回 null。 */
    fun parseEvent(event: JSONObject): DshCoordination? {
        val type = event.optString("type")
        val data = event.optJSONObject("data")
        val seq = event.optInt("seq", 0)
        return when {
            type in GOAL_EVENTS -> data?.let { goalOf(seq, it) }?.let { DshCoordination(goal = it) }
            type in TODO_EVENTS -> data?.let { todosOf(seq, it) }?.let { DshCoordination(todos = it) }
            type == "tool/call" -> data?.let { todosFromToolCall(seq, it) }?.let { DshCoordination(todos = it) }
            else -> null
        }
    }

    private fun goalOf(seq: Int, data: JSONObject): DshGoalProjection? {
        val goal = data.optJSONObject("goal") ?: data
        val summary = firstText(goal, "summary", "title", "text", "description", "name", "goal")
            ?: return null
        val status = firstText(goal, "status", "state", "phase")
            ?: firstText(data, "status", "state")
        val detail = firstText(goal, "detail", "reason", "blockedReason", "statusText")
            ?: firstText(data, "detail", "reason", "blockedReason")
        return DshGoalProjection(
            seq = seq,
            summary = oneLine(summary, MAX_GOAL_CHARS),
            status = status?.lowercase()?.take(64),
            detail = detail?.let { oneLine(it, MAX_DETAIL_CHARS) }?.takeIf { it.isNotBlank() }
        )
    }

    private fun todosFromToolCall(seq: Int, data: JSONObject): DshTodoProjection? {
        val toolName = data.optString("name").trim().lowercase()
        if (toolName !in TODO_TOOLS) return null
        val args = argumentsOf(data.opt("arguments")) ?: return null
        return todosOf(seq, args)
    }

    /** 工具参数既可能是 JSON 对象，也可能是只含数组的 JSON 字符串。 */
    private fun argumentsOf(raw: Any?): JSONObject? = when (raw) {
        is JSONObject -> raw
        is JSONArray -> JSONObject().put("items", raw)
        is String -> {
            val text = raw.trim()
            if (text.isEmpty()) {
                null
            } else {
                runCatching { JSONObject(text) }.getOrNull()
                    ?: runCatching { JSONObject().put("items", JSONArray(text)) }.getOrNull()
            }
        }
        else -> null
    }

    private fun todosOf(seq: Int, container: JSONObject): DshTodoProjection? {
        val array = TODO_ARRAY_KEYS.firstNotNullOfOrNull { key ->
            container.optJSONArray(key)
        } ?: container.optJSONObject("todo")?.let { todo ->
            TODO_ARRAY_KEYS.firstNotNullOfOrNull { key -> todo.optJSONArray(key) }
        } ?: return null
        if (array.length() == 0) return null
        val items = ArrayList<DshTodoItemProjection>(minOf(array.length(), MAX_TODO_ITEMS))
        for (index in 0 until array.length()) {
            if (items.size >= MAX_TODO_ITEMS) break
            when (val value = array.opt(index)) {
                is String -> {
                    val text = oneLine(value, MAX_TODO_TEXT_CHARS)
                    if (text.isNotBlank()) {
                        items += DshTodoItemProjection(id = "todo-$index", text = text)
                    }
                }
                is JSONObject -> {
                    val text = firstText(value, "text", "title", "content", "step", "summary", "task")
                        ?.let { oneLine(it, MAX_TODO_TEXT_CHARS) }
                        ?.takeIf { it.isNotBlank() } ?: continue
                    val id = firstText(value, "id", "todoId", "stepId", "key")
                        ?.take(MAX_ID_CHARS)
                        ?.takeIf { it.isNotBlank() }
                        ?: "todo-$index"
                    items += DshTodoItemProjection(
                        id = id,
                        text = text,
                        status = firstText(value, "status", "state")?.lowercase()?.take(64)
                    )
                }
            }
        }
        return if (items.isEmpty()) null else DshTodoProjection(seq = seq, items = items)
    }

    /** 取第一个非空字符串字段；只读取白名单键，避免把任意字段当文案。 */
    private fun firstText(source: JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (!source.has(key)) continue
            val value = source.opt(key)
            val text = when (value) {
                is String -> value
                is Number, is Boolean -> value.toString()
                else -> null
            }?.trim()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    private fun oneLine(text: String, limit: Int): String =
        text.replace(ONE_LINE, " ").trim().take(limit)
}
