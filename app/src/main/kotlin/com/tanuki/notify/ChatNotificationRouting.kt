package com.tanuki.notify

/**
 * 通知与点击 Intent 的稳定身份。
 *
 * 不能使用进程内自增 id：系统通知和 PendingIntent 会在进程回收后保留，重建后若重新从
 * 1000 计数，会覆盖另一个会话尚未处理的提醒。这里用会话 tag + 固定通知 id，让同一会话
 * 跨进程更新同一条通知，不同会话互不覆盖；Intent 的 data 也绑定会话，作为 PendingIntent
 * 的匹配身份，而不是只靠 extras。
 */
data class ChatNotificationTarget(
    val tag: String,
    val id: Int,
    val action: String,
    val dataUri: String,
    val requestCode: Int,
    val onlyAlertOnce: Boolean,
    val groupSummary: Boolean,
)

object ChatNotificationRouting {
    const val CHILD_ID = 1000
    const val SUMMARY_ID = 1001
    const val SUMMARY_TAG = "dsh-chat:summary"
    const val SUMMARY_ACTION = "com.tanuki.action.DSH_CHAT_SUMMARY"
    const val SUMMARY_URI = "tanuki://dsh-chat/summary"
    const val SESSION_ACTION = "com.tanuki.action.DSH_CHAT_SESSION"
    const val SESSION_URI_PREFIX = "tanuki://dsh-chat/session/"

    private val safeSessionId = Regex("[A-Za-z0-9_.:-]{1,128}")

    fun child(sessionId: String): ChatNotificationTarget {
        require(safeSessionId.matches(sessionId)) { "invalid session id" }
        return ChatNotificationTarget(
            tag = "dsh-chat:$sessionId",
            id = CHILD_ID,
            action = SESSION_ACTION,
            dataUri = SESSION_URI_PREFIX + sessionId,
            requestCode = CHILD_ID,
            // 每个通过策略去重的新事件都应再次发声；重复事件已在策略层丢弃。
            onlyAlertOnce = false,
            groupSummary = false,
        )
    }

    fun summary(): ChatNotificationTarget = ChatNotificationTarget(
        tag = SUMMARY_TAG,
        id = SUMMARY_ID,
        action = SUMMARY_ACTION,
        dataUri = SUMMARY_URI,
        requestCode = SUMMARY_ID,
        onlyAlertOnce = true,
        groupSummary = true,
    )
}
