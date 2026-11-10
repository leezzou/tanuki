package com.tanuki.ui

/** 冷启动阶段；`INTERACTIVE` 表示首帧已可点击。 */
enum class TanukiStartupStage { BEFORE_FIRST_FRAME, AFTER_FIRST_FRAME, INTERACTIVE }

/** 冷启动期间可能被触发的初始化工作。 */
enum class TanukiStartupWork {
    RESTORE_VIEW_STATE,
    APPLY_THEME,
    READ_LOCAL_PREFERENCES,
    LOAD_SESSION_LIST,
    CONNECT_LIVE_STREAM,
    SCAN_WORKSPACE,
    CHECK_UPDATES,
    PRELOAD_WEBVIEW,
}

/**
 * 冷启动工作预算。
 *
 * 目标：从进程启动到界面可交互，只做恢复状态、应用主题、读取轻量偏好；
 * 会话列表/实时流放到首帧之后，磁盘扫描、更新检查、WebView 预热放到
 * 可交互之后。策略是纯函数，接线方在调度处调用即可。
 */
object TanukiStartupPolicy {
    fun allowed(stage: TanukiStartupStage, work: TanukiStartupWork): Boolean = when (work) {
        // 轻量、同步且直接影响首帧正确性的工作。
        TanukiStartupWork.RESTORE_VIEW_STATE,
        TanukiStartupWork.APPLY_THEME,
        TanukiStartupWork.READ_LOCAL_PREFERENCES,
        -> stage >= TanukiStartupStage.BEFORE_FIRST_FRAME

        // 需要网络或明显 I/O，但用户已经能看到页面骨架。
        TanukiStartupWork.LOAD_SESSION_LIST,
        TanukiStartupWork.CONNECT_LIVE_STREAM,
        -> stage >= TanukiStartupStage.AFTER_FIRST_FRAME

        // 重活，必须等首帧可交互后再做。
        TanukiStartupWork.SCAN_WORKSPACE,
        TanukiStartupWork.CHECK_UPDATES,
        TanukiStartupWork.PRELOAD_WEBVIEW,
        -> stage >= TanukiStartupStage.INTERACTIVE
    }

    fun shouldDefer(stage: TanukiStartupStage, work: TanukiStartupWork): Boolean = !allowed(stage, work)

    /** 到达该阶段才应启动的工作集合，供调度方批量登记。 */
    fun workFor(stage: TanukiStartupStage): Set<TanukiStartupWork> =
        TanukiStartupWork.entries.filterTo(LinkedHashSet()) { allowed(stage, it) }
}
