package com.tanuki.ui

import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import com.tanuki.ui.design.TanukiMotionSpecs
import com.tanuki.web.DshMessage
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshTimelineItem
import com.tanuki.web.DshToolItem
import com.tanuki.web.DshToolStatus
import com.tanuki.web.DshRiskAssessment
import com.tanuki.web.DshRiskLevel
import org.junit.Assert.*
import org.junit.Test

/** 新行资格独立于历史/live 分段，不干预已有锚定策略。 */
class DshMessageMotionTest {
    private val normal = TanukiMotionSpecs(false)
    private fun text(seq: Int, role: DshMessageRole = DshMessageRole.ASSISTANT, pending: Boolean = false) =
        DshTimelineItem.Text(DshMessage(seq, role, "合成消息 $seq", seq.toLong(), pending))
    private fun rows(vararg seqs: Int) = DshChatRenderPolicy.rows(seqs.map { text(it) })
    private fun project(previous: DshMessageMotionSnapshot, rows: List<DshChatRow>, revision: Int = 0,
                        loading: Boolean = false, sending: Boolean = false, streaming: Boolean = false) =
        previous.next(rows, revision, loading, sending, streaming)

    @Test fun initialRestoreAndLoadingCompletionNeverEnterButNextCommittedAppendDoes() {
        val first = project(DshMessageMotionSnapshot(), rows(10, 11))
        assertTrue(first.enteringKeys.isEmpty())
        val loading = project(first, rows(10, 11), loading = true)
        val restored = project(loading, rows(10, 11, 12))
        assertTrue(restored.enteringKeys.isEmpty())
        val appended = project(restored, rows(10, 11, 12, 13))
        assertEquals(setOf("message-13"), appended.enteringKeys)
        val specs = appended.motion("message-13", normal)
        assertNull(specs.placement)
        assertNull(specs.fadeOut)
        val fade = TargetBasedAnimation(specs.fadeIn!!, Float.VectorConverter, 0f, 1f)
        assertEquals(160_000_000L, fade.durationNanos)
        assertTrue(fade.getValueFromNanos(80_000_000L) in 0.001f..0.999f)
        assertEquals(1f, fade.getValueFromNanos(160_000_000L), 0f)
        assertNull(appended.motion("message-10", normal).fadeIn)
        // 普通重组不会移除入场 spec、中途截断，也不会把旧行重新认作新增。
        assertEquals(appended, project(appended, rows(10, 11, 12, 13)))
    }

    @Test fun prependAndWindowReleaseNeverEnterEvenWhenAnAppendArrivesInTheSameRevision() {
        var state = project(DshMessageMotionSnapshot(), rows(10, 11, 12))
        state = project(state, rows(5, 6, 10, 11, 12, 13), revision = 1)
        assertTrue(state.enteringKeys.isEmpty())
        state = project(state, rows(10, 11, 12, 13), revision = 2)
        assertTrue(state.enteringKeys.isEmpty())
        state = project(state, rows(5, 6, 10, 11, 12, 13), revision = 3)
        assertTrue(state.enteringKeys.isEmpty())
        state = project(state, rows(5, 6, 10, 11, 12, 13, 14), revision = 3)
        assertEquals(setOf("message-14"), state.enteringKeys)
    }

    @Test fun emptyNewConversationCanFadeItsFirstCommittedRow() {
        val ready = project(DshMessageMotionSnapshot(), emptyList())
        assertEquals(setOf("message-1"), project(ready, rows(1)).enteringKeys)
        // 另一个会话的新快照必须重新初始化，不沿用上一会话的序号或资格。
        assertTrue(project(DshMessageMotionSnapshot(), rows(1)).enteringKeys.isEmpty())
    }

    @Test fun pendingAndComposerPlaceholderReplacementDoNotReplayEntranceAcrossAGap() {
        var state = project(DshMessageMotionSnapshot(), rows(1))
        val pending = DshChatRenderPolicy.rows(listOf(text(1), text(-1, DshMessageRole.USER, pending = true)))
        state = project(state, pending, sending = true)
        assertTrue(state.enteringKeys.isEmpty())
        state = project(state, rows(1)) // 发送回执先结束，本地占位已撤下，官方消息尚未到。
        val durable = DshChatRenderPolicy.rows(listOf(text(1), text(2, DshMessageRole.USER)))
        state = project(state, durable)
        assertTrue(state.enteringKeys.isEmpty())
        state = project(state, durable + DshChatRow(text(3, DshMessageRole.USER)))
        assertEquals(setOf("message-3"), state.enteringKeys)
    }

    @Test fun streamingDeltaAndCommittedReplacementNeverEnterButLaterReplyCan() {
        var state = project(DshMessageMotionSnapshot(), rows(1))
        state = project(state, rows(1), streaming = true)
        val same = project(state, rows(1), streaming = true)
        assertEquals(state, same)
        state = project(same, rows(1)) // Finished 可能先清空流式行。
        state = project(state, rows(1, 2))
        assertTrue(state.enteringKeys.isEmpty())
        state = project(state, rows(1, 2, 3))
        assertEquals(setOf("message-3"), state.enteringKeys)
    }

    @Test fun placeholderSignalClearingAfterCommitDoesNotSuppressTheFollowingMessage() {
        for (role in DshMessageRole.entries) {
            val sending = role == DshMessageRole.USER
            val streaming = role == DshMessageRole.ASSISTANT
            var state = project(DshMessageMotionSnapshot(), rows(1))
            state = project(state, rows(1), sending = sending, streaming = streaming)
            val committed = rows(1) + DshChatRow(text(2, role))
            state = project(state, committed, sending = sending, streaming = streaming)
            assertTrue(state.enteringKeys.isEmpty())
            assertEquals(state, project(state, committed, sending = sending, streaming = streaming))
            state = project(state, committed)
            state = project(state, committed + DshChatRow(text(3, role)))
            assertEquals(setOf("message-3"), state.enteringKeys)
        }
    }

    @Test fun groupingAndReadingAnchorChangesDoNotReplayExistingRows() {
        val grouping = DshChatGroupState()
        val first = listOf(text(1), tool(2))
        val before = grouping.project(first)
        var state = project(DshMessageMotionSnapshot(), before.history)
        val appended = first + tool(3)
        val grouped = grouping.project(appended)
        state = project(state, grouped.history)
        assertTrue(state.enteringKeys.isEmpty()) // 已有工具组追加成员，只更新内容。
        val split = grouping.project(appended.take(1), appended.drop(1))
        assertEquals(state, project(state, split.history + split.live))
        state = project(state, grouping.project(appended + text(4) + tool(5)).history)
        assertEquals(setOf("message-4", "tool:call-5"), state.enteringKeys)
        assertNull(state.motion("tool:call-5", normal).placement)
    }

    @Test fun reducedMotionUsesNullSpecsAndDoesNotReplayWhenRestored() {
        val ready = project(DshMessageMotionSnapshot(), rows(1))
        val next = project(ready, rows(1, 2))
        for (key in listOf("message-1", "message-2")) {
            val spec = next.motion(key, TanukiMotionSpecs(true))
            assertNull(spec.placement)
            assertNull(spec.fadeIn)
            assertNull(spec.fadeOut)
        }
        assertEquals(next, project(next, rows(1, 2)))
    }

    private fun tool(seq: Int) = DshTimelineItem.Tool(DshToolItem(
        seq, seq.toLong(), "call-$seq", "Bash", null, DshToolStatus.RUNNING,
        null, null, null, null, emptyList(), emptyList(), false,
        DshRiskAssessment(DshRiskLevel.LOW, "只读"),
    ))
}
