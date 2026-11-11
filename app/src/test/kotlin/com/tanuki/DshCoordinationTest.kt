package com.tanuki

import com.tanuki.web.DshCoordinationParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 目标/待办事件投影回归：只认白名单事件与真实字段，缺失/损坏时保持空而不是造数据。
 * 真实 DSH 返回字段未在设备验证，失败路径以“忽略并隐藏”为准。
 */
class DshCoordinationTest {
    private fun event(type: String, seq: Int, data: JSONObject) = JSONObject()
        .put("type", type)
        .put("seq", seq)
        .put("time", seq.toLong())
        .put("data", data)

    private fun records(vararg events: JSONObject) = JSONArray().also { array ->
        events.forEach { array.put(JSONObject().put("event", it)) }
    }

    @Test fun goalEventProjectsOneLineSummaryStatusAndDetail() {
        val coordination = DshCoordinationParser.parse(
            records(
                event(
                    "goal/update", 5,
                    JSONObject()
                        .put("summary", "  补齐目标条\n并跑回归  ")
                        .put("status", "running")
                        .put("detail", "剩余 JVM + lint")
                )
            )
        )
        val goal = coordination.goal!!
        assertEquals("补齐目标条 并跑回归", goal.summary)
        assertEquals("running", goal.status)
        assertEquals("剩余 JVM + lint", goal.detail)
        assertNull(coordination.todos)
    }

    @Test fun goalEventWithoutTextIsIgnored() {
        val coordination = DshCoordinationParser.parse(
            records(
                event("goal/update", 1, JSONObject().put("summary", "   ").put("status", "running")),
                event("goal/update", 2, JSONObject().put("status", "running"))
            )
        )
        assertNull(coordination.goal)
        assertTrue(coordination.isEmpty)
    }

    @Test fun latestGoalAndTodoInSamePageWin() {
        val coordination = DshCoordinationParser.parse(
            records(
                event("goal/update", 1, JSONObject().put("summary", "旧目标").put("status", "paused")),
                event("goal/update", 3, JSONObject().put("summary", "新目标").put("status", "running")),
                event(
                    "todos/update", 2,
                    JSONObject().put(
                        "todos",
                        JSONArray().put(JSONObject().put("id", "t1").put("text", "旧待办"))
                    )
                ),
                event(
                    "todo/update", 4,
                    JSONObject().put(
                        "items",
                        JSONArray()
                            .put(JSONObject().put("id", "t2").put("text", "新待办").put("status", "completed"))
                    )
                )
            )
        )
        assertEquals("新目标", coordination.goal!!.summary)
        assertEquals("t2", coordination.todos!!.items.single().id)
        assertEquals("completed", coordination.todos!!.items.single().status)
    }

    @Test fun todoToolCallProjectsItemsFromArguments() {
        val coordination = DshCoordinationParser.parse(
            records(
                event(
                    "tool/call", 7,
                    JSONObject()
                        .put("name", "todo_write")
                        .put("callId", "call-1")
                        .put(
                            "arguments",
                            JSONObject()
                                .put(
                                    "todos",
                                    JSONArray()
                                        .put(
                                            JSONObject().put("id", "a").put("content", "写回归")
                                                .put("status", "completed")
                                        )
                                        .put(
                                            JSONObject().put("content", "跑 lint").put("status", "in_progress")
                                        )
                                )
                                .toString()
                        )
                )
            )
        )
        assertEquals(listOf("写回归", "跑 lint"), coordination.todos!!.items.map { it.text })
        assertEquals("completed", coordination.todos!!.items[0].status)
        assertEquals("todo-1", coordination.todos!!.items[1].id)
    }

    @Test fun bareArrayToolArgumentsAreAccepted() {
        val coordination = DshCoordinationParser.parse(
            records(
                event(
                    "tool/call", 8,
                    JSONObject()
                        .put("name", "update_todos")
                        .put("arguments", JSONArray().put(JSONObject().put("text", "a")).toString())
                )
            )
        )
        assertEquals(listOf("a"), coordination.todos!!.items.map { it.text })
    }

    @Test fun unrelatedOrUnknownEventsAreIgnored() {
        val coordination = DshCoordinationParser.parse(
            records(
                event("assistant/message", 1, JSONObject().put("message", JSONObject())),
                event("goal/unknown", 2, JSONObject().put("summary", "不认识的类型")),
                event("tool/call", 3, JSONObject().put("name", "Bash").put("arguments", "{}"))
            )
        )
        assertTrue(coordination.isEmpty)
    }

    @Test fun emptyTodoListDoesNotCreateABar() {
        val coordination = DshCoordinationParser.parse(
            records(
                event("todos/update", 1, JSONObject().put("todos", JSONArray())),
                event("todo/update", 2, JSONObject().put("items", JSONArray()))
            )
        )
        assertNull(coordination.todos)
    }
}
