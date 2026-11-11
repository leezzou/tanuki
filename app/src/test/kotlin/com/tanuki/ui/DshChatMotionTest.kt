package com.tanuki.ui

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import com.tanuki.ui.design.TanukiMotionEasing
import com.tanuki.ui.design.TanukiMotionSpecs
import com.tanuki.web.DshQuestionDraft
import com.tanuki.web.DshQuestionParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Host 仅采样真实 Compose spec 和业务策略；实际控件计时另有 Android 仪器用例。 */
class DshChatMotionTest {
    @Test fun shortAndLongDetailsUseFrozenTimingAndTopRevealCurves() {
        val motion = TanukiMotionSpecs(false)
        for ((longDetail, expected) in listOf(false to 240, true to 300)) {
            val spec = motion.expand<Float>(true, longDetail)
            assertEquals(expected, spec.durationMillis)
            assertEquals(if (longDetail) TanukiMotionEasing.enter else TanukiMotionEasing.standard, spec.easing)
            val animation = TargetBasedAnimation(spec, Float.VectorConverter, 0f, 1f)
            assertEquals(expected * 1_000_000L, animation.durationNanos)
            assertEquals(0f, animation.getValueFromNanos(0), 0f)
            assertTrue(animation.getValueFromNanos((expected - 16) * 1_000_000L) < 1f)
            assertEquals(1f, animation.getValueFromNanos(animation.durationNanos), 0f)
            val exit = motion.expand<Float>(false, longDetail)
            assertEquals(240, exit.durationMillis)
            assertEquals(TanukiMotionEasing.exit, exit.easing)
        }
        assertEquals(160, motion.toggle<Float>().durationMillis)
    }

    @Test fun restoredOrReducedDetailsNeedNoAnimationWait() {
        for (longDetail in listOf(false, true)) for (opening in listOf(false, true)) {
            val animation = TargetBasedAnimation(TanukiMotionSpecs(true).expand<Float>(opening, longDetail),
                Float.VectorConverter, 0f, 1f)
            assertEquals(0L, animation.durationNanos)
            assertEquals(1f, animation.getValueFromNanos(0L), 0f)
        }
    }

    @Test fun longDiffViewportStaysBoundedRegardlessOfIncomingLineCount() {
        assertEquals(24, DshChatMotionPolicy.DIFF_PREVIEW_LINES)
        assertEquals(240, DshChatMotionPolicy.diffViewportDp(false))
        assertEquals(480, DshChatMotionPolicy.diffViewportDp(true))
    }

    @Test fun onlyLongUserDetailsReceiveThreeHundredMillis() {
        assertFalse(DshChatMotionPolicy.isLongDetail(null, "短参数", "短输出"))
        assertTrue(DshChatMotionPolicy.isLongDetail("x".repeat(601)))
        assertTrue(DshChatMotionPolicy.isLongDetail(null, "行\n".repeat(6)))
    }

    @Test fun submitAvailabilityChangesWithDraftWithoutAdvancingAnimationClock() {
        val question = DshQuestionParser.parse("motion-question", "synthetic-session",
            JSONObject().put("questions", JSONArray().put(JSONObject().put("id", "q")
                .put("question", "选择验证项").put("multiSelect", true)
                .put("options", JSONArray().put("构建")))))!!
        var draft = DshQuestionDraft(question.questions)
        assertFalse(DshChatMotionPolicy.canSubmit(true, draft.canSubmit(), false))
        draft = draft.toggle("q", "o1")
        assertTrue(DshChatMotionPolicy.canSubmit(true, draft.canSubmit(), false))
        assertFalse(DshChatMotionPolicy.canSubmit(true, draft.canSubmit(), true))
        assertFalse(DshChatMotionPolicy.canSubmit(false, draft.canSubmit(), false))
        draft = draft.toggle("q", "o1")
        assertFalse(DshChatMotionPolicy.canSubmit(true, draft.canSubmit(), false))
    }
}
