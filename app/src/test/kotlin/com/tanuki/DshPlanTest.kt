package com.tanuki

import com.tanuki.web.DshPlanParser
import com.tanuki.web.DshPlanReviewState
import com.tanuki.web.DshPlanStepStatus
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DshPlanTest {
    @Test fun parsesPlanToolCallAndProgress() {
        val records = JSONArray().put(
            call(
                1, "c1", "update_plan",
                """{"title":"修复审批 UI","plan":[{"step":"分析","status":"completed"},{"step":"实现","status":"in_progress"},{"step":"验证","status":"pending"}]}"""
            )
        )
        val extraction = DshPlanParser.extract(records)
        assertEquals(setOf("c1"), extraction.planCallIds)
        val plan = extraction.latest!!
        assertEquals("修复审批 UI", plan.title)
        assertEquals(3, plan.total)
        assertEquals(1, plan.completed)
        assertEquals("实现", plan.currentStep?.title)
        assertEquals(DshPlanStepStatus.COMPLETED, plan.steps[0].status)
        assertEquals(DshPlanStepStatus.IN_PROGRESS, plan.steps[1].status)
        assertEquals(DshPlanStepStatus.PENDING, plan.steps[2].status)
        assertEquals(DshPlanReviewState.NONE, plan.review)
        assertEquals(1f / 3f, plan.progress, 0.0001f)
        assertEquals("执行中", plan.statusLabel)
    }

    @Test fun parsesPlanReviewEventAndMarksAwaiting() {
        val records = JSONArray().put(
            event(
                "plan/review", 7,
                JSONObject()
                    .put("title", "发布计划")
                    .put("steps", JSONArray().put("构建").put("测试").put("发布"))
            )
        )
        val plan = DshPlanParser.extract(records).latest!!
        assertEquals("发布计划", plan.title)
        assertEquals(DshPlanReviewState.AWAITING, plan.review)
        assertTrue(plan.awaitingReview)
        assertEquals("待审阅", plan.statusLabel)
        assertEquals("plan-7", plan.id)
    }

    @Test fun requiresApprovalAndExplicitReviewStatesAreHonored() {
        val awaiting = DshPlanParser.extract(
            JSONArray().put(
                call(
                    1, "c1", "update_plan",
                    """{"requiresApproval":true,"steps":[{"title":"确认","status":"pending"}]}"""
                )
            )
        ).latest!!
        assertTrue(awaiting.awaitingReview)
        val approved = DshPlanParser.extract(
            JSONArray().put(
                event(
                    "plan/update", 2,
                    JSONObject()
                        .put("reviewStatus", "approved")
                        .put("steps", JSONArray().put("执行"))
                )
            )
        ).latest!!
        assertEquals(DshPlanReviewState.APPROVED, approved.review)
        val changes = DshPlanParser.extract(
            JSONArray().put(
                event(
                    "plan/update", 3,
                    JSONObject()
                        .put("reviewStatus", "changes-requested")
                        .put("steps", JSONArray().put("执行"))
                )
            )
        ).latest!!
        assertEquals(DshPlanReviewState.CHANGES_REQUESTED, changes.review)
    }

    @Test fun reviewPayloadDefaultsToAwaiting() {
        val plan = DshPlanParser.fromReviewPayload(
            9, "review-1",
            JSONObject()
                .put("title", "计划审阅")
                .put("steps", JSONArray().put(JSONObject().put("step", "确认范围").put("status", "pending")))
        )!!
        assertEquals(DshPlanReviewState.AWAITING, plan.review)
        assertTrue(plan.awaitingReview)
        assertEquals("review-1", plan.id)
    }

    @Test fun laterUpdateReplacesSamePlanId() {
        val records = JSONArray()
            .put(
                withEventId(
                    event(
                        "plan/update", 3,
                        JSONObject()
                            .put("title", "旧计划")
                            .put("steps", JSONArray().put(JSONObject().put("step", "旧步骤")))
                    ),
                    "plan-1"
                )
            )
            .put(
                withEventId(
                    event(
                        "plan/update", 8,
                        JSONObject()
                            .put("title", "新计划")
                            .put(
                                "steps",
                                JSONArray().put(JSONObject().put("step", "新步骤").put("status", "done"))
                            )
                    ),
                    "plan-1"
                )
            )
        val extraction = DshPlanParser.extract(records)
        assertEquals(1, extraction.plans.size)
        assertEquals("新计划", extraction.latest!!.title)
        assertEquals(DshPlanStepStatus.COMPLETED, extraction.latest!!.steps.single().status)
    }

    @Test fun mapsBlockedAndSkippedAndCapsSteps() {
        val steps = JSONArray()
        for (index in 0 until DshPlanParser.MAX_STEPS + 5) {
            steps.put(
                JSONObject()
                    .put("title", "步骤 $index")
                    .put("status", if (index == 1) "blocked" else if (index == 2) "skipped" else "pending")
            )
        }
        val plan = DshPlanParser.extract(
            JSONArray().put(call(1, "c1", "todo_write", JSONObject().put("todos", steps).toString()))
        ).latest!!
        assertEquals(DshPlanParser.MAX_STEPS, plan.steps.size)
        assertEquals(DshPlanParser.MAX_STEPS + 5, plan.total)
        assertTrue(plan.truncated)
        assertEquals(DshPlanStepStatus.BLOCKED, plan.steps[1].status)
        assertEquals(DshPlanStepStatus.SKIPPED, plan.steps[2].status)
    }

    @Test fun truncatedPlanDoesNotClaimCompletionForHiddenSteps() {
        val steps = JSONArray()
        for (index in 0 until 20) {
            steps.put(JSONObject().put("title", "步骤 $index").put("status", "completed"))
        }
        steps.put(JSONObject().put("title", "隐藏的最后一步").put("status", "pending"))
        val plan = DshPlanParser.extract(
            JSONArray().put(call(1, "c1", "update_plan", JSONObject().put("steps", steps).toString()))
        ).latest!!
        assertEquals(21, plan.total)
        assertEquals(20, plan.completed)
        assertEquals(20f / 21f, plan.progress, 0.0001f)
        assertEquals("执行中", plan.statusLabel)
        assertTrue(plan.truncated)
    }

    @Test fun hiddenInProgressStepRemainsVisibleAsCurrent() {
        val steps = JSONArray()
        for (index in 0 until 20) {
            steps.put(JSONObject().put("title", "步骤 $index").put("status", "completed"))
        }
        steps.put(JSONObject().put("title", "隐藏的进行中步骤").put("status", "in_progress"))
        val plan = DshPlanParser.extract(
            JSONArray().put(call(1, "c1", "update_plan", JSONObject().put("steps", steps).toString()))
        ).latest!!
        assertEquals("隐藏的进行中步骤", plan.currentStepTitle)
        assertEquals("执行中", plan.statusLabel)
    }

    @Test fun nestedExplicitReviewStateBeatsDefaultAwaiting() {
        val eventPlan = DshPlanParser.extract(
            JSONArray().put(
                event(
                    "plan/review", 4,
                    JSONObject().put(
                        "plan",
                        JSONObject()
                            .put("reviewStatus", "approved")
                            .put("steps", JSONArray().put("执行"))
                    )
                )
            )
        ).latest!!
        assertEquals(DshPlanReviewState.APPROVED, eventPlan.review)

        val payloadPlan = DshPlanParser.fromReviewPayload(
            5, "review-2",
            JSONObject().put(
                "plan",
                JSONObject()
                    .put("reviewStatus", "changes-requested")
                    .put("steps", JSONArray().put("执行"))
            )
        )!!
        assertEquals(DshPlanReviewState.CHANGES_REQUESTED, payloadPlan.review)
    }

    @Test fun ignoresNonPlanToolsAndMalformedPayloads() {
        val records = JSONArray()
            .put(call(1, "c1", "bash", """{"plan":[{"step":"伪装"}]}"""))
            .put(call(2, "c2", "update_plan", "not-json"))
            .put(call(3, "c3", "update_plan", """{"title":"没有步骤"}"""))
        val extraction = DshPlanParser.extract(records)
        assertTrue(extraction.plans.isEmpty())
        assertTrue(extraction.planCallIds.isEmpty())
        assertNull(extraction.latest)
        assertFalse(DshPlanParser.isPlanTool("bash"))
        assertTrue(DshPlanParser.isPlanTool("update_plan"))
    }

    private fun call(seq: Int, callId: String, name: String, arguments: String): JSONObject = event(
        "tool/call", seq,
        JSONObject().put("callId", callId).put("name", name).put("arguments", arguments)
    )

    private fun event(type: String, seq: Int, data: JSONObject): JSONObject = JSONObject().put(
        "event",
        JSONObject().put("type", type).put("seq", seq).put("time", 1_000L + seq).put("data", data)
    )

    private fun withEventId(record: JSONObject, eventId: String): JSONObject =
        record.getJSONObject("event").put("eventId", eventId).let { record }
}
