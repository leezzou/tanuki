package com.tanuki

import com.tanuki.notify.ChatEvent
import com.tanuki.notify.ChatEventType
import com.tanuki.notify.ChatNotificationPolicy
import com.tanuki.notify.ChatNotificationStatus
import com.tanuki.notify.ChatPermissionState
import com.tanuki.notify.ChatSuppression
import com.tanuki.notify.ChatTurnState
import com.tanuki.notify.description
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNotificationPolicyTest {
    private fun permission(
        sdk: Int = 33,
        runtime: Boolean = true,
        app: Boolean = true,
        channel: Boolean = true,
        user: Boolean = true,
    ) = ChatPermissionState(sdk, runtime, app, channel, user)

    private fun event(
        type: ChatEventType = ChatEventType.TURN_COMPLETED,
        session: String = "session-1",
        eventId: String = "turn-1",
        version: String = "0.2.1-rc.5",
    ) = ChatEvent(type, session, eventId, version)

    @Test fun turnCompletionNeedsObservedRunningAndDoesNotRepeat() {
        val turns = ChatTurnState()
        // 页面首次加载的 false 基线不是完成事件。
        assertFalse(turns.observeStatus("session-1", false))
        assertFalse(turns.observeStatus("session-1", true))
        assertTrue(turns.observeStatus("session-1", false))
        // 同一回合的重复 false（例如重绘/重放）不能再次提醒。
        assertFalse(turns.observeStatus("session-1", false))
        assertFalse(turns.observeStatus("session-2", false))
    }

    @Test fun nextTurnCanNotifyAgain() {
        val turns = ChatTurnState()
        turns.observeStatus("session-1", true)
        assertTrue(turns.observeStatus("session-1", false))
        turns.observeStatus("session-1", true)
        assertTrue(turns.observeStatus("session-1", false))
    }

    @Test fun foregroundVisibleSessionSuppressesOnlyMatchingSession() {
        val policy = ChatNotificationPolicy()
        val visible = policy.decide(event(), appForeground = true, visibleSessionId = "session-1", permission = permission())
        assertFalse(visible.notify)
        assertEquals(ChatSuppression.FOREGROUND_SESSION_VISIBLE, visible.suppression)

        val other = policy.decide(event(session = "session-2"), appForeground = true,
            visibleSessionId = "session-1", permission = permission())
        assertTrue(other.notify)
    }

    @Test fun backgroundNotifiesEvenWhenSessionStayedVisible() {
        val policy = ChatNotificationPolicy()
        val decision = policy.decide(event(), appForeground = false,
            visibleSessionId = "session-1", permission = permission())
        assertTrue(decision.notify)
        assertEquals(ChatSuppression.NONE, decision.suppression)
    }

    @Test fun sameSessionVersionAndEventNotifiesOnce() {
        val policy = ChatNotificationPolicy()
        assertTrue(policy.decide(event(), false, "", permission()).notify)
        assertEquals(ChatSuppression.DUPLICATE_EVENT,
            policy.decide(event(), false, "", permission()).suppression)
        // 版本变化、事件 id 变化或会话变化都必须重新判断。
        assertTrue(policy.decide(event(version = "0.2.2"), false, "", permission()).notify)
        assertTrue(policy.decide(event(eventId = "turn-2"), false, "", permission()).notify)
        assertTrue(policy.decide(event(session = "session-2"), false, "", permission()).notify)
    }

    @Test fun newEventForSameSessionStillNotifiesWhenOldNotificationMayRemain() {
        val policy = ChatNotificationPolicy()
        // 旧通知未清除时，后续不同 eventId 仍必须再次提醒；只有真正重复的事件被拦截。
        assertTrue(policy.decide(event(eventId = "event-1"), false, "", permission()).notify)
        assertTrue(policy.decide(event(type = ChatEventType.PERMISSION_REQUEST, eventId = "event-2"),
            false, "", permission()).notify)
        assertFalse(policy.decide(event(eventId = "event-1"), false, "", permission()).notify)
    }

    @Test fun permissionAndChannelStatesBlockDelivery() {
        val policy = ChatNotificationPolicy()
        assertEquals(ChatSuppression.RUNTIME_PERMISSION_MISSING,
            policy.decide(event(), false, "", permission(runtime = false)).suppression)
        assertEquals(ChatSuppression.APP_NOTIFICATIONS_DISABLED,
            policy.decide(event(), false, "", permission(app = false)).suppression)
        assertEquals(ChatSuppression.CHANNEL_DISABLED,
            policy.decide(event(), false, "", permission(channel = false)).suppression)
        assertEquals(ChatSuppression.USER_DISABLED,
            policy.decide(event(), false, "", permission(user = false)).suppression)
        // 被权限拦下的事件不写入已提醒集合，授权后同一事件仍可提醒一次。
        assertTrue(policy.decide(event(), false, "", permission()).notify)
        assertFalse(policy.decide(event(), false, "", permission()).notify)
    }

    @Test fun preAndroid13DoesNotRequireRuntimePermission() {
        val state = permission(sdk = 32, runtime = false)
        assertTrue(state.canNotify)
        assertEquals(null, state.blockedReason())
        assertTrue(ChatNotificationPolicy().decide(event(), false, "", state).notify)
    }

    @Test fun invalidIdentityIsRejectedWithoutRemembering() {
        val policy = ChatNotificationPolicy()
        val blankSession = policy.decide(event(session = " "), false, "", permission())
        assertEquals(ChatSuppression.INVALID_EVENT, blankSession.suppression)
        assertEquals(ChatSuppression.INVALID_EVENT,
            policy.decide(event(eventId = ""), false, "", permission()).suppression)
        assertEquals(ChatSuppression.INVALID_EVENT,
            policy.decide(event(version = "0.2.1 rc"), false, "", permission()).suppression)
        // 非法事件没有占据去重键，修正后仍能提醒。
        assertTrue(policy.decide(event(), false, "", permission()).notify)
    }

    @Test fun statusSummaryDistinguishesPermissionChannelAndSuppression() {
        val enabled = ChatNotificationStatus(permission())
        assertEquals("已开启", enabled.summaryText())
        assertEquals("未授予通知权限", ChatNotificationStatus(permission(runtime = false)).summaryText())
        assertEquals("系统已关闭 DSH 会话通知渠道",
            ChatNotificationStatus(permission(channel = false)).summaryText())
        assertEquals("已关闭", ChatNotificationStatus(permission(user = false)).summaryText())
        assertTrue(ChatNotificationStatus(permission(), ChatSuppression.DUPLICATE_EVENT, 2)
            .summaryText().contains(ChatSuppression.DUPLICATE_EVENT.description()))
    }
}
