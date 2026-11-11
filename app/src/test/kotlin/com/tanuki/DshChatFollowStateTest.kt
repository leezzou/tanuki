package com.tanuki

import com.tanuki.ui.DshChatFollowState
import com.tanuki.web.DshMessage
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshTimelineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话级跟随状态回归：A→B 切换后互不影响；上翻阅读期间的流式增量/新消息不改变锚点。
 * Compose 手势与真实渲染未覆盖，这里验证页面依赖的状态机语义。
 */
class DshChatFollowStateTest {
    private fun text(seq: Int, pending: Boolean = false) = DshTimelineItem.Text(
        DshMessage(
            seq = seq,
            role = DshMessageRole.ASSISTANT,
            text = "m$seq",
            time = seq.toLong(),
            pending = pending
        )
    )

    @Test fun switchingSessionsUsesIndependentFollowState() {
        val sessionA = DshChatFollowState()
        assertTrue(sessionA.detach(listOf(text(1), text(2))))
        assertFalse(sessionA.snapshot.following)
        assertEquals("message-2", sessionA.snapshot.anchor!!.key)

        // 切到 B 时页面会新建一份状态；B 不受 A 上翻影响，仍贴底且没有胶囊锚点。
        val sessionB = DshChatFollowState()
        assertTrue(sessionB.snapshot.following)
        assertNull(sessionB.snapshot.anchor)

        // B 自己上翻才脱离，锚点取 B 的最后一行；A 的状态不被改写。
        assertTrue(sessionB.detach(listOf(text(10), text(11))))
        assertEquals("message-11", sessionB.snapshot.anchor!!.key)
        assertEquals("message-2", sessionA.snapshot.anchor!!.key)
        assertFalse(sessionA.snapshot.following)
        assertFalse(sessionB.snapshot.following)
    }

    @Test fun streamingAndNewMessagesAfterUserScrollDoNotResumeFollowing() {
        val follow = DshChatFollowState()
        val history = listOf(text(1), text(2))
        assertTrue(follow.detach(history))
        val frozen = follow.snapshot.anchor

        // 流式增量与后续消息只让时间线变长；跟随状态与锚点保持不变，不会被拉回底部。
        val grown = history + listOf(text(3), text(4))
        assertFalse(follow.snapshot.following)
        assertEquals(frozen, follow.snapshot.anchor)

        // 已经脱离时重复手势不再改写锚点。
        assertFalse(follow.detach(grown))
        assertEquals(frozen, follow.snapshot.anchor)

        // 只有回到底部才恢复跟随并清除锚点。
        assertTrue(follow.reattach())
        assertTrue(follow.snapshot.following)
        assertNull(follow.snapshot.anchor)
        assertFalse(follow.reattach())
    }

    @Test fun returningToBottomAfterScrollResumesFollowForSameSession() {
        val follow = DshChatFollowState()
        val timeline = listOf(text(1), text(2))
        assertTrue(follow.detach(timeline))
        assertFalse(follow.snapshot.following)

        // 监听按当前状态恢复，不依赖首次组合时捕获的 following=true。
        assertTrue(follow.reattachIfDetached())
        assertTrue(follow.snapshot.following)
        assertNull(follow.snapshot.anchor)

        // 恢复后新消息只增长时间线，不需要再点胶囊，也不会重新脱离。
        val grown = timeline + listOf(text(3), text(4))
        assertTrue(follow.snapshot.following)
        assertNull(follow.snapshot.anchor)
        assertFalse(follow.reattachIfDetached())
        assertTrue(follow.snapshot.following)
    }

    @Test fun detachIgnoresEmptyTimelineAndKeepsFollowing() {
        val follow = DshChatFollowState()
        assertFalse(follow.detach(emptyList()))
        assertTrue(follow.snapshot.following)
        assertNull(follow.snapshot.anchor)
    }

    @Test fun pendingMessageCanBeTheAnchor() {
        val follow = DshChatFollowState()
        assertTrue(follow.detach(listOf(text(5), text(-1, pending = true))))
        assertEquals("pending--1", follow.snapshot.anchor!!.key)
    }
}
