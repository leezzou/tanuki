package com.tanuki.web

/**
 * DSH 主界面的用户选择。默认是官方界面；原生界面只是可切换的展示层，
 * 不改变 DSH 进程、数据目录或官方网页。
 */
enum class DshUiMode(val storageValue: String) {
    OFFICIAL("official"),
    NATIVE("native");

    val isNative: Boolean get() = this == NATIVE

    companion object {
        const val PREFERENCE_FILE = "ui"
        const val PREFERENCE_KEY = "dsh-ui"

        /** 未知或损坏的偏好一律回到官方界面，避免把用户锁在不可用界面。 */
        fun parse(raw: String?): DshUiMode =
            entries.firstOrNull { it.storageValue == raw } ?: OFFICIAL
    }
}

/**
 * 原生界面的纯展示策略。只有用户明确选择原生、DSH 服务正在运行，且没有已记录的
 * 接口失败时才显示原生页；其余情况都显示官方界面。偏好本身不因回退而改写。
 */
/** 通知点击后实际应进入的界面；原生模式不依赖 WebView 是否已连接。 */
enum class ChatNotificationRoute { NATIVE, OFFICIAL_READY, OFFICIAL_WAIT }

object ChatNotificationRoutePolicy {
    fun route(
        preference: DshUiMode,
        serviceRunning: Boolean,
        fallbackReason: String?,
        officialWebConnected: Boolean
    ): ChatNotificationRoute = when {
        DshUiSwitchPolicy.useNative(preference, serviceRunning, fallbackReason) -> ChatNotificationRoute.NATIVE
        officialWebConnected -> ChatNotificationRoute.OFFICIAL_READY
        else -> ChatNotificationRoute.OFFICIAL_WAIT
    }
}

/** 通知目标在原生/官方切换过程中的下一步动作。 */
enum class ChatNotificationDecision {
    REQUEST_NATIVE,
    CONSUMED,
    WAIT_FOR_FALLBACK,
    OPEN_OFFICIAL,
    WAIT_FOR_WEB
}

object ChatNotificationTargetPolicy {
    fun decide(
        target: String,
        route: ChatNotificationRoute,
        nativeSelectedId: String?,
        nativeLoading: Boolean,
        nativeUnavailable: Boolean,
        nativeRequestIssued: Boolean
    ): ChatNotificationDecision = when (route) {
        ChatNotificationRoute.NATIVE -> when {
            nativeUnavailable -> ChatNotificationDecision.WAIT_FOR_FALLBACK
            // 必须由本次通知发起过刷新，不能仅凭通知到达前的 selectedId 直接消费。
            nativeSelectedId == target && !nativeLoading && nativeRequestIssued -> ChatNotificationDecision.CONSUMED
            else -> ChatNotificationDecision.REQUEST_NATIVE
        }
        ChatNotificationRoute.OFFICIAL_READY -> ChatNotificationDecision.OPEN_OFFICIAL
        ChatNotificationRoute.OFFICIAL_WAIT -> ChatNotificationDecision.WAIT_FOR_WEB
    }
}

object DshUiSwitchPolicy {
    fun useNative(preference: DshUiMode, serviceRunning: Boolean, fallbackReason: String?): Boolean =
        preference.isNative && serviceRunning && fallbackReason.isNullOrBlank()

    fun effective(preference: DshUiMode, serviceRunning: Boolean, fallbackReason: String?): DshUiMode =
        if (useNative(preference, serviceRunning, fallbackReason)) DshUiMode.NATIVE else DshUiMode.OFFICIAL
}
