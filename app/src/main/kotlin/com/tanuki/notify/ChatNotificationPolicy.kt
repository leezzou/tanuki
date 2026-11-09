package com.tanuki.notify

/**
 * DSH 会话通知的纯策略层。
 *
 * 这里不引用 Android 类型，只处理“事件是否值得提醒”，便于在 JVM 上覆盖前台抑制、
 * 去重和权限/渠道状态。真正的通知渠道、PendingIntent 和系统设置入口由
 * [ChatNotificationCenter] / [ChatNotifier] 负责。
 */
enum class ChatEventType {
    /** 一个 Agent 回合从运行中回到空闲。 */
    TURN_COMPLETED,
    /** 工具调用等待用户确认或回答。 */
    WAITING_CONFIRMATION,
    /** 会话级错误。 */
    ERROR,
    /** 工具调用等待权限审批。 */
    PERMISSION_REQUEST,
}

/** 一次待判断的提醒。eventId 必须能区分同一会话内的多次事件。 */
data class ChatEvent(
    val type: ChatEventType,
    val sessionId: String,
    val eventId: String,
    val dshVersion: String,
)

/**
 * 通知是否具备投递条件。
 *
 * sdkInt 单独保存，测试才能在没有 Android 框架的 JVM 上覆盖 Android 13 权限分支。
 * userEnabled 是应用内总开关；channelEnabled 只表示渠道没有被系统关闭。
 */
data class ChatPermissionState(
    val sdkInt: Int,
    val runtimeGranted: Boolean,
    val appNotificationsEnabled: Boolean,
    val channelEnabled: Boolean,
    val userEnabled: Boolean,
) {
    val canNotify: Boolean
        get() = userEnabled &&
            (sdkInt < 33 || runtimeGranted) &&
            appNotificationsEnabled &&
            channelEnabled

    fun blockedReason(): ChatSuppression? = when {
        !userEnabled -> ChatSuppression.USER_DISABLED
        sdkInt >= 33 && !runtimeGranted -> ChatSuppression.RUNTIME_PERMISSION_MISSING
        !appNotificationsEnabled -> ChatSuppression.APP_NOTIFICATIONS_DISABLED
        !channelEnabled -> ChatSuppression.CHANNEL_DISABLED
        else -> null
    }
}

enum class ChatSuppression {
    NONE,
    INVALID_EVENT,
    USER_DISABLED,
    RUNTIME_PERMISSION_MISSING,
    APP_NOTIFICATIONS_DISABLED,
    CHANNEL_DISABLED,
    FOREGROUND_SESSION_VISIBLE,
    DUPLICATE_EVENT,
}

data class ChatNotificationDecision(
    val notify: Boolean,
    val suppression: ChatSuppression,
    val dedupeKey: String,
)

/**
 * 同一 session、同一 DSH 版本、同一事件只提醒一次。
 *
 * 去重键不包含会话标题、模型输出或错误原文，只保存不可逆的键和有限条数；应用进程重启
 * 后重新开始，这与通知本身的生命周期一致。
 */
class ChatNotificationPolicy(private val maxRemembered: Int = 256) {
    private val remembered = LinkedHashSet<String>()

    fun decide(
        event: ChatEvent,
        appForeground: Boolean,
        visibleSessionId: String,
        permission: ChatPermissionState,
    ): ChatNotificationDecision {
        if (!isSafeIdentity(event.sessionId) || !isSafeIdentity(event.eventId) ||
            !isSafeIdentity(event.dshVersion, maxLength = 64)
        ) {
            return ChatNotificationDecision(false, ChatSuppression.INVALID_EVENT, "")
        }
        val key = "${event.dshVersion}\u0000${event.sessionId}\u0000${event.type.name}\u0000${event.eventId}"
        permission.blockedReason()?.let { return ChatNotificationDecision(false, it, key) }
        // 前台且对应会话已打开时不重复弹；离开该会话或退到后台后才会提醒。
        if (appForeground && visibleSessionId == event.sessionId) {
            return ChatNotificationDecision(false, ChatSuppression.FOREGROUND_SESSION_VISIBLE, key)
        }
        if (remembered.contains(key)) return ChatNotificationDecision(false, ChatSuppression.DUPLICATE_EVENT, key)
        remember(key)
        return ChatNotificationDecision(true, ChatSuppression.NONE, key)
    }

    private fun remember(key: String) {
        remembered.remove(key)
        remembered.add(key)
        while (remembered.size > maxRemembered.coerceAtLeast(1)) {
            val eldest = remembered.iterator()
            if (!eldest.hasNext()) break
            eldest.next()
            eldest.remove()
        }
    }

    private fun isSafeIdentity(value: String, maxLength: Int = 128): Boolean =
        value.isNotBlank() && value.length <= maxLength && value.all { it.code in 0x21..0x7e }
}

/**
 * 从 `api-session/status` 的 running 布尔值识别回合结束。
 *
 * DSH 没有为状态切换提供事件 id；因此只有亲眼看到 true 后再看到 false 才算一次结束，
 * 页面首次加载时的 false 基线不会误报。重复 false 也不会重复提醒。
 */
class ChatTurnState {
    private val running = HashSet<String>()

    fun observeStatus(sessionId: String, running: Boolean): Boolean {
        if (running) {
            this.running.add(sessionId)
            return false
        }
        return this.running.remove(sessionId)
    }

    fun isRunning(sessionId: String): Boolean = running.contains(sessionId)
}

fun ChatSuppression.description(): String = when (this) {
    ChatSuppression.NONE -> "通知已开启"
    ChatSuppression.INVALID_EVENT -> "事件格式无效，已忽略"
    ChatSuppression.USER_DISABLED -> "应用内聊天通知已关闭"
    ChatSuppression.RUNTIME_PERMISSION_MISSING -> "未授予通知权限"
    ChatSuppression.APP_NOTIFICATIONS_DISABLED -> "系统已关闭本应用通知"
    ChatSuppression.CHANNEL_DISABLED -> "系统已关闭 DSH 会话通知渠道"
    ChatSuppression.FOREGROUND_SESSION_VISIBLE -> "应用在前台且对应会话可见，不重复提醒"
    ChatSuppression.DUPLICATE_EVENT -> "同一事件已提醒过"
}
