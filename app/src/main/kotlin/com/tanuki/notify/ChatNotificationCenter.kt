package com.tanuki.notify

import android.content.Context
import com.tanuki.web.ChatSignal
import com.tanuki.web.ChatSignalKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DSH 会话通知的运行时编排。
 *
 * 页面桥只送来最小事件；这里维护“是否在前台”“对应会话是否可见”“回合是否曾运行”以及
 * 去重/权限判断。所有状态都在应用进程内，不写入日志、会话或通知正文。
 */
class ChatNotificationCenter(
    context: Context,
    private val versionProvider: () -> String,
) {
    private val notifier = ChatNotifier(context.applicationContext)
    private val policy = ChatNotificationPolicy()
    private val turns = ChatTurnState()
    private var appForeground = false
    private var lastSuppression: ChatSuppression? = null
    private var delivered = 0
    private val _status = MutableStateFlow(notifier.status())
    val status: StateFlow<ChatNotificationStatus> = _status.asStateFlow()

    fun ensureChannel() {
        notifier.ensureChannel()
        refresh()
    }

    fun setAppForeground(value: Boolean) {
        if (appForeground == value) return
        appForeground = value
        refresh()
    }

    fun onSignal(signal: ChatSignal) {
        val version = safeVersion(versionProvider())
        val sessionId = signal.sessionId
        when (signal.kind) {
            ChatSignalKind.STATUS -> {
                val running = signal.running ?: return
                if (!turns.observeStatus(sessionId, running)) return
                emit(
                    ChatEvent(ChatEventType.TURN_COMPLETED, sessionId,
                        "turn:${signal.pageId}:${signal.sequence}", version),
                    signal.visibleSessionId,
                )
            }
            ChatSignalKind.ERROR -> emit(
                ChatEvent(ChatEventType.ERROR, sessionId,
                    "error:${signal.pageId}:${signal.sequence}", version),
                signal.visibleSessionId,
            )
            ChatSignalKind.APPROVAL -> emit(
                ChatEvent(ChatEventType.PERMISSION_REQUEST, sessionId,
                    "event:${signal.eventId ?: "${signal.pageId}:${signal.sequence}"}", version),
                signal.visibleSessionId,
            )
            ChatSignalKind.QUESTION -> emit(
                ChatEvent(ChatEventType.WAITING_CONFIRMATION, sessionId,
                    "event:${signal.eventId ?: "${signal.pageId}:${signal.sequence}"}", version),
                signal.visibleSessionId,
            )
        }
    }

    fun refresh() {
        _status.value = ChatNotificationStatus(
            permission = notifier.permissionState(),
            lastSuppression = lastSuppression,
            delivered = delivered,
        )
    }

    private fun emit(event: ChatEvent, visibleSessionId: String?) {
        val decision = policy.decide(
            event = event,
            appForeground = appForeground,
            visibleSessionId = visibleSessionId.orEmpty(),
            permission = notifier.permissionState(),
        )
        if (decision.notify) {
            notifier.post(event)
            delivered += 1
            lastSuppression = null
        } else {
            lastSuppression = decision.suppression
        }
        refresh()
    }

    private fun safeVersion(value: String): String =
        value.takeIf { it.isNotBlank() && it.length <= 64 && it.all { c -> c.code in 0x21..0x7e } }
            ?: "unknown"
}
