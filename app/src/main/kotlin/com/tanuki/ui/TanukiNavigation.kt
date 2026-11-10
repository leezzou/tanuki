package com.tanuki.ui

/**
 * 原生外壳的返回上下文。
 *
 * 返回顺序固定为：面板/弹层 → 全屏 → 网页历史 → 二级页 → 根页。
 * 这里只描述层级，不持有 Compose 或控制器状态，便于在 JVM 上回归。
 */
data class TanukiBackContext(
    /** 从底到顶排列；最后一项是当前最上层面板。 */
    val overlays: List<String> = emptyList(),
    val fullscreen: Boolean = false,
    val webCanGoBack: Boolean = false,
    /** 当前是否已经位于根页；根页再返回才退出应用。 */
    val atRootDestination: Boolean = false,
)

/** 返回键的纯决策结果，由页面负责执行。 */
sealed interface TanukiBackAction {
    /** 先关闭最上层页面。 */
    data class CloseOverlay(val overlayId: String) : TanukiBackAction

    /** 退出全屏，保留当前页面与列表位置。 */
    data object LeaveFullscreen : TanukiBackAction

    /** 官方页面内后退，不离开 DSH 页。 */
    data object WebHistoryBack : TanukiBackAction

    /** 离开二级页，回到上一级页面。 */
    data object LeavePage : TanukiBackAction

    /** 根页返回，交给系统退出。 */
    data object ExitApplication : TanukiBackAction
}

/**
 * 返回键优先级策略。
 *
 * 关键点：面板/弹层永远先于页面退出；页面退出不能顺带重置列表状态。
 * 页面状态由 `TanukiSecondaryPageState` 与 `rememberTanukiListState` 保持。
 */
object TanukiBackPolicy {
    fun decide(context: TanukiBackContext): TanukiBackAction = when {
        context.overlays.isNotEmpty() -> TanukiBackAction.CloseOverlay(context.overlays.last())
        context.fullscreen -> TanukiBackAction.LeaveFullscreen
        context.webCanGoBack -> TanukiBackAction.WebHistoryBack
        !context.atRootDestination -> TanukiBackAction.LeavePage
        else -> TanukiBackAction.ExitApplication
    }
}

/**
 * 可保存的面板栈。
 *
 * 审批、会话详情、模型目录等二级面板共用同一规则：返回只弹出当前层，
 * 不重建下层列表，也不清空搜索/筛选/滚动位置。
 */
class TanukiOverlayStack(initial: List<String> = emptyList()) {
    private val entries = ArrayDeque<String>()

    init {
        initial.forEach(::push)
    }

    val size: Int get() = entries.size
    val current: String? get() = entries.lastOrNull()

    fun snapshot(): List<String> = entries.toList()

    /** 空 id 与重复栈顶忽略；同一 id 再次打开会移到最上层。 */
    fun push(id: String): Boolean {
        val clean = id.trim()
        if (clean.isEmpty()) return false
        if (entries.lastOrNull() == clean) return false
        entries.remove(clean)
        entries.addLast(clean)
        return true
    }

    fun pop(): String? = entries.removeLastOrNull()

    fun clear() {
        entries.clear()
    }
}

/**
 * 二级页面的可保存状态。
 *
 * 关闭面板时只修改 [panels]；[listItems]、[listScrollIndex] 与
 * [listScrollOffset] 原样保留，避免返回后列表被重置到顶部。
 */
data class TanukiSecondaryPageState(
    val panels: List<String> = emptyList(),
    val listItems: List<String> = emptyList(),
    val listScrollIndex: Int = 0,
    val listScrollOffset: Int = 0,
) {
    init {
        require(listScrollIndex >= 0) { "列表滚动下标不能为负" }
        require(listScrollOffset >= 0) { "列表滚动偏移不能为负" }
    }

    fun openPanel(panelId: String): TanukiSecondaryPageState {
        val clean = panelId.trim()
        if (clean.isEmpty() || panels.lastOrNull() == clean) return this
        return copy(panels = panels.filterNot { it == clean } + clean)
    }

    fun closeTopPanel(): TanukiSecondaryPageState =
        if (panels.isEmpty()) this else copy(panels = panels.dropLast(1))
}

data class TanukiSecondaryPageTransition(
    val state: TanukiSecondaryPageState,
    val action: TanukiBackAction,
)

object TanukiSecondaryPagePolicy {
    fun back(state: TanukiSecondaryPageState): TanukiSecondaryPageTransition {
        val top = state.panels.lastOrNull() ?: return TanukiSecondaryPageTransition(state, TanukiBackAction.LeavePage)
        return TanukiSecondaryPageTransition(state.closeTopPanel(), TanukiBackAction.CloseOverlay(top))
    }
}

/**
 * 底部/侧边导航的返回历史。
 *
 * 通知点击会把 DSH 作为新目的地压栈，因此从设置页收到通知后，
 * 返回键先回到设置页，而不是直接回首页或退出。
 */
class TanukiTabHistory(
    initialTab: Int = HOME_TAB,
    private val maxEntries: Int = 8,
) {
    init {
        require(initialTab >= 0) { "初始页不能为负" }
        require(maxEntries >= 1) { "返回历史至少保留一项" }
    }

    private val previous = ArrayDeque<Int>()

    var current: Int = initialTab
        private set

    val canGoBack: Boolean get() = previous.isNotEmpty()

    /** 返回是否真的发生了切页；重复选择当前页为 no-op。 */
    fun select(tab: Int): Boolean {
        require(tab >= 0) { "目标页不能为负" }
        if (tab == current) return false
        previous.addLast(current)
        while (previous.size > maxEntries) previous.removeFirst()
        current = tab
        return true
    }

    /** 通知点击等外部入口使用；与手动切页共用同一返回栈。 */
    fun openNotificationTarget(tab: Int = DSH_TAB): Boolean = select(tab)

    /** 无历史时回到 [fallback]，不直接退出应用。 */
    fun back(fallback: Int = HOME_TAB): Int {
        require(fallback >= 0) { "兜底页不能为负" }
        current = previous.removeLastOrNull() ?: fallback
        return current
    }

    fun reset(tab: Int = HOME_TAB) {
        require(tab >= 0) { "重置页不能为负" }
        previous.clear()
        current = tab
    }

    fun history(): List<Int> = previous.toList()

    companion object {
        const val HOME_TAB = 0
        const val DSH_TAB = 1
        const val SETTINGS_TAB = 4
    }
}

/** 单个 DSH 视图（原生或官方）需要跨切换保留的 UI 状态。 */
data class TanukiDshPaneState(
    val sessionId: String? = null,
    val listScrollIndex: Int = 0,
    val listScrollOffset: Int = 0,
    val draft: String = "",
) {
    init {
        require(listScrollIndex >= 0) { "列表滚动下标不能为负" }
        require(listScrollOffset >= 0) { "列表滚动偏移不能为负" }
        require(draft.length <= MAX_DRAFT_LENGTH) { "草稿长度超出上限" }
    }

    companion object {
        const val MAX_DRAFT_LENGTH = 200_000
    }
}

/**
 * 原生/官方界面切换状态。
 *
 * 两个视图各自保留会话、滚动和草稿；切换只改 [native]，不复制也不清空
 * 另一侧状态。接线时把该对象放进 `rememberSaveable` 或控制器即可。
 */
data class TanukiDshSwitchState(
    val native: Boolean,
    val nativePane: TanukiDshPaneState = TanukiDshPaneState(),
    val officialPane: TanukiDshPaneState = TanukiDshPaneState(),
) {
    val activePane: TanukiDshPaneState get() = if (native) nativePane else officialPane
}

object TanukiDshSwitchPolicy {
    fun switch(state: TanukiDshSwitchState, toNative: Boolean): TanukiDshSwitchState =
        if (state.native == toNative) state else state.copy(native = toNative)

    fun updateActive(state: TanukiDshSwitchState, pane: TanukiDshPaneState): TanukiDshSwitchState =
        if (state.native) state.copy(nativePane = pane) else state.copy(officialPane = pane)

    /** 切换回来时应恢复的会话；调用方据此决定是否强制重读历史。 */
    fun restoredSessionId(state: TanukiDshSwitchState, toNative: Boolean): String? =
        (if (toNative) state.nativePane else state.officialPane).sessionId
}
