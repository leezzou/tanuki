package com.tanuki.notify

/**
 * 通知标题和正文的固定最小文案。
 *
 * 不读取会话标题、消息、工具名、错误原文或文件路径；所有类型都只说明需要回到 Tanuki
 * 处理，避免锁屏/通知栏泄露任务内容。
 */
object ChatNotificationCopy {
    fun title(type: ChatEventType): String = when (type) {
        ChatEventType.TURN_COMPLETED -> "DSH 会话回合完成"
        ChatEventType.WAITING_CONFIRMATION -> "DSH 等待你的确认"
        ChatEventType.ERROR -> "DSH 会话出现错误"
        ChatEventType.PERMISSION_REQUEST -> "DSH 请求权限审批"
    }

    fun text(type: ChatEventType): String = when (type) {
        ChatEventType.TURN_COMPLETED -> "有会话已结束当前回合。点击返回 Tanuki 查看。"
        ChatEventType.WAITING_CONFIRMATION -> "有工具调用或问题在等待处理。点击返回对应会话。"
        ChatEventType.ERROR -> "会话报告了错误。点击返回 Tanuki 查看状态。"
        ChatEventType.PERMISSION_REQUEST -> "有工具调用在等待你的授权决定。点击返回对应会话。"
    }
}
