package com.tanuki

import com.tanuki.web.DshQuestionDraft
import com.tanuki.web.DshQuestionKind
import com.tanuki.web.DshQuestionParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DshQuestionTest {
    @Test fun parsesSingleChoiceWithOptions() {
        val request = DshQuestionParser.parse(
            "event-1", "session-1",
            JSONObject().put(
                "questions",
                JSONArray().put(
                    JSONObject()
                        .put("id", "q1")
                        .put("question", "选择运行方式")
                        .put("options", JSONArray()
                            .put(JSONObject().put("id", "o1").put("label", "只读"))
                            .put(JSONObject().put("id", "o2").put("label", "工作区")))
                )
            )
        )!!
        assertEquals("event-1", request.eventId)
        assertEquals("session-1", request.sessionId)
        val question = request.questions.single()
        assertEquals(DshQuestionKind.SINGLE, question.kind)
        assertEquals(2, question.options.size)
        assertEquals("只读", question.options[0].label)
    }

    @Test fun parsesMultiChoiceAndCustomOption() {
        val request = DshQuestionParser.parse(
            "event-2", "session-1",
            JSONObject().put(
                "questions",
                JSONArray().put(
                    JSONObject()
                        .put("id", "q2")
                        .put("question", "选择要检查的项")
                        .put("multiSelect", true)
                        .put("allowCustom", true)
                        .put("options", JSONArray().put("构建").put("测试"))
                )
            )
        )!!
        val question = request.questions.single()
        assertEquals(DshQuestionKind.MULTI, question.kind)
        assertTrue(question.allowCustom)
        assertEquals(2, question.options.size)
    }

    @Test fun acceptsSingleQuestionObjectAsWellAsArray() {
        val request = DshQuestionParser.parse(
            "e", "s",
            JSONObject().put(
                "questions",
                JSONObject()
                    .put("question", "单一提问")
                    .put("options", JSONArray().put("确认").put("取消"))
            )
        )!!
        assertEquals("单一提问", request.questions.single().text)
        assertEquals(2, request.questions.single().options.size)
    }

    @Test fun draftSingleReplacesAndMultiToggles() {
        val questions = DshQuestionParser.parse(
            "e", "s",
            JSONObject().put(
                "questions",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("id", "single")
                            .put("question", "单选")
                            .put("options", JSONArray().put("A").put("B"))
                    )
                    .put(
                        JSONObject()
                            .put("id", "multi")
                            .put("question", "多选")
                            .put("multiSelect", true)
                            .put("options", JSONArray().put("C").put("D"))
                    )
            )
        )!!.questions
        var draft = DshQuestionDraft(questions)
        assertFalse(draft.canSubmit())
        draft = draft.toggle("single", "o1")
        draft = draft.toggle("single", "o2")
        assertEquals(listOf("o2"), draft.selections["single"])
        draft = draft.toggle("multi", "o1")
        draft = draft.toggle("multi", "o2")
        assertEquals(listOf("o1", "o2"), draft.selections["multi"])
        draft = draft.toggle("multi", "o1")
        assertEquals(listOf("o2"), draft.selections["multi"])
        assertTrue(draft.canSubmit())
        assertEquals(listOf("o2"), draft.answers().selections["single"])
    }

    @Test fun draftCustomTextSatisfiesRequiredAndTrimsBlank() {
        val questions = DshQuestionParser.parse(
            "e", "s",
            JSONObject().put(
                "question", "补充说明"
            )
        )!!.questions
        assertEquals(DshQuestionKind.TEXT, questions.single().kind)
        var draft = DshQuestionDraft(questions)
        assertFalse(draft.canSubmit())
        draft = draft.setCustom("q1", "  需要保留工作区  ")
        assertTrue(draft.canSubmit())
        assertEquals("  需要保留工作区  ", draft.customFor("q1"))
        assertEquals("需要保留工作区", draft.answers().custom["q1"])
        draft = draft.setCustom("q1", "   ")
        assertFalse(draft.canSubmit())
        assertTrue(draft.answers().custom.isEmpty())
    }

    @Test fun characterByCharacterEditingKeepsSpacesAndNewlines() {
        val questions = DshQuestionParser.parse("e", "s", JSONObject().put("question", "输入"))!!
            .questions
        var draft = DshQuestionDraft(questions)
        for (value in listOf("h", "he", "hel", "hell", "hello", "hello ", "hello w", "hello wo")) {
            draft = draft.setCustom("q1", value)
        }
        assertEquals("hello wo", draft.customFor("q1"))
        draft = draft.setCustom("q1", "hello wo\n")
        assertEquals("hello wo\n", draft.customFor("q1"))
        draft = draft.setCustom("q1", "hello wo\nworld")
        assertEquals("hello wo\nworld", draft.customFor("q1"))
        assertEquals("hello wo\nworld", draft.answers().custom["q1"])
    }

    @Test fun optionalQuestionDoesNotBlockSubmit() {
        val questions = DshQuestionParser.parse(
            "e", "s",
            JSONObject().put(
                "questions",
                JSONArray().put(
                    JSONObject()
                        .put("id", "opt")
                        .put("question", "可选")
                        .put("required", false)
                        .put("options", JSONArray().put("稍后"))
                )
            )
        )!!.questions
        assertTrue(DshQuestionDraft(questions).canSubmit())
    }

    @Test fun rejectsMalformedOrUnsafeRequestAndCapsText() {
        assertNull(DshQuestionParser.parse("bad id", "s", JSONObject()))
        assertNull(DshQuestionParser.parse("e", "s", JSONObject().put("question", "")))
        assertNull(DshQuestionParser.parse("e", "s", null))
        val long = "x".repeat(DshQuestionParser.MAX_TEXT_CHARS + 100)
        val request = DshQuestionParser.parse(
            "e", "s",
            JSONObject().put(
                "questions",
                JSONArray().put(
                    JSONObject()
                        .put("question", long)
                        .put("options", JSONArray().put(long).put(long).put(long))
                )
            )
        )!!
        assertTrue(request.questions.single().text.length <= DshQuestionParser.MAX_TEXT_CHARS)
        assertTrue(request.questions.single().options.size <= DshQuestionParser.MAX_OPTIONS)
    }
}
