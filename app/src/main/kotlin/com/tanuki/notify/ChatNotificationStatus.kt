package com.tanuki.notify

/** 设置页展示的通知状态；不包含任何会话内容。 */
data class ChatNotificationStatus(
    val permission: ChatPermissionState = ChatPermissionState(0, false, false, false, true),
    val lastSuppression: ChatSuppression? = null,
    val delivered: Int = 0,
) {
    val canNotify: Boolean get() = permission.canNotify

    fun summaryText(): String {
        val current = when {
            !permission.userEnabled -> "已关闭"
            permission.sdkInt >= 33 && !permission.runtimeGranted -> "未授予通知权限"
            !permission.appNotificationsEnabled -> "系统已关闭本应用通知"
            !permission.channelEnabled -> "系统已关闭 DSH 会话通知渠道"
            else -> "已开启"
        }
        val suppressed = lastSuppression?.takeUnless { it == ChatSuppression.NONE }
        return if (suppressed == null) current else "$current；上次事件未提醒：${suppressed.description()}"
    }
}
