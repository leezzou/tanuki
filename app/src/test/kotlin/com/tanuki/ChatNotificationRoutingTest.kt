package com.tanuki

import com.tanuki.notify.ChatNotificationRouting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNotificationRoutingTest {
    @Test fun sameSessionKeepsStableTagAndFixedIdAcrossProcesses() {
        val first = ChatNotificationRouting.child("session-1")
        val second = ChatNotificationRouting.child("session-1")
        // 固定 id + 会话 tag，进程重建后不会重新分配 1000 去覆盖其他会话。
        assertEquals(ChatNotificationRouting.CHILD_ID, first.id)
        assertEquals(first.id, second.id)
        assertEquals(first.tag, second.tag)
        assertEquals(first.dataUri, second.dataUri)
        assertEquals(first.requestCode, second.requestCode)
    }

    @Test fun differentSessionsHaveDifferentNotificationAndPendingIntentIdentity() {
        val a = ChatNotificationRouting.child("session-a")
        val b = ChatNotificationRouting.child("session-b")
        assertNotEquals(a.tag, b.tag)
        assertNotEquals(a.dataUri, b.dataUri)
        assertTrue(a.dataUri.endsWith("session-a"))
        assertTrue(b.dataUri.endsWith("session-b"))
        assertNotEquals(a.tag, ChatNotificationRouting.summary().tag)
    }

    @Test fun newEventCanAlertAgainButSummaryStaysQuiet() {
        val child = ChatNotificationRouting.child("session-1")
        val summary = ChatNotificationRouting.summary()
        assertFalse("每个通过策略去重的新事件都必须重新提醒", child.onlyAlertOnce)
        assertFalse(child.groupSummary)
        assertTrue("摘要只承载分组，不应重复响铃", summary.onlyAlertOnce)
        assertTrue(summary.groupSummary)
        assertTrue(summary.dataUri.endsWith("/summary"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsafeSessionIdIsRejectedBeforeBuildingIdentity() {
        ChatNotificationRouting.child("session with space")
    }
}
