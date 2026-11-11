package com.tanuki.ui

import android.content.Intent
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tanuki.ui.design.TanukiDesign
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import com.tanuki.ui.design.TanukiTonalButton as FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.tanuki.ui.design.TanukiOutlinedButton as OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanuki.ui.design.TanukiPillButton
import com.tanuki.ui.design.TanukiPillButtonTone
import com.tanuki.web.DshApprovalOutcome
import com.tanuki.web.DshRememberedChoice
import com.tanuki.web.DshMessage
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshSession
import com.tanuki.web.DshTimelineItem
import com.tanuki.web.DshToolItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 原生页：官方会话、历史、实时回复、发送/停止与审批都直接使用官方 DSH 协议。
 * 渲染层只做展示与输入状态，不另建聊天协议；无法连接实时流时仍可读历史、发送与停止。
 */
@Composable
fun DshNativePage(
    controller: DshNativeController,
    composer: DshComposerController,
    modifier: Modifier = Modifier,
    onFallback: (String) -> Unit,
    onSwitchOfficial: () -> Unit,
    approvalState: DshApprovalState = DshApprovalState(),
    onApprovalAnswer: (String, DshApprovalOutcome, Boolean) -> Unit = { _, _, _ -> },
    onApprovalRetry: () -> Unit = {},
    onClearRemembered: () -> Unit = {},
    onForgetRemembered: (DshRememberedChoice) -> Unit = {},
    onDismissNotice: () -> Unit = {},
    /** 输入栏按会话持久化的失败结果；有它时页面重入也能恢复失败卡片。 */
    composerSendFailures: StateFlow<Map<String, DshComposerSendResult>>? = null
) {
    val state by controller.state.collectAsState()
    val composerState by composer.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(controller, composer) {
        bindDshComposerRunState(controller.state, composer)
    }
    val sendTracker = remember(composer) { DshComposerSendTracker() }
    var fallbackFailure by remember { mutableStateOf<DshFailedPrompt?>(null) }
    var composerPendingText by remember { mutableStateOf("") }
    val emptyFailures = remember { MutableStateFlow<Map<String, DshComposerSendResult>>(emptyMap()) }
    val recordedFailures by (composerSendFailures ?: emptyFailures).collectAsState()
    val composerFailure = fallbackFailure ?: recordedFailures[state.selectedId]?.let {
        DshFailedPrompt(it.text, it.reason, it.time, it.attachmentCount)
    }
    var showApprovalSheet by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember(state.selectedId) { mutableStateOf<DshSession?>(null) }
    var renameTitle by remember(renameTarget?.id) { mutableStateOf(renameTarget?.displayTitle.orEmpty()) }
    var renameBusy by remember(renameTarget?.id) { mutableStateOf(false) }
    var renameError by remember(renameTarget?.id) { mutableStateOf<String?>(null) }
    // 直接收集 composer 状态（不经过重组）；recorder 存在时失败由持久化结果提供。
    LaunchedEffect(composer, composerSendFailures) {
        var wasSending = false
        composer.state.collect { current ->
            if (current.sending) {
                if (composerPendingText.isBlank()) composerPendingText = current.text
            } else {
                composerPendingText = ""
                if (wasSending && current.error == null) fallbackFailure = null
            }
            if (composerSendFailures == null) {
                sendTracker.observe(
                    current.sending,
                    current.text,
                    current.error,
                    current.attachments.size
                )?.let { fallbackFailure = it }
                if (current.sending) fallbackFailure = null
            }
            wasSending = current.sending
        }
    }
    LaunchedEffect(state.selectedId) {
        fallbackFailure = null
        composerPendingText = ""
        sendTracker.reset()
    }
    LaunchedEffect(state.unavailableReason) {
        state.unavailableReason?.let(onFallback)
    }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val activity = DshChatRenderPolicy.activity(state.running, state.live)
    ModalNavigationDrawer(
        modifier = modifier.fillMaxSize(),
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color(TanukiDesign.tokens.colors.surface)) {
                Text("对话", Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = controller::refresh, enabled = !state.loading) { Text("刷新对话") }
                TextButton(onClick = onSwitchOfficial) { Text("打开官方界面") }
                LazyColumn {
                    items(state.sessions, key = { it.id }) { session ->
                        TextButton(onClick = {
                            controller.select(session.id)
                            scope.launch { drawer.close() }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(session.displayTitle, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (session.id == state.selectedId) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    ) {
        Column(Modifier.fillMaxSize().imePadding().background(Color(TanukiDesign.tokens.colors.canvas))) {
            Header(
                activity = activity,
                onDrawer = { scope.launch { drawer.open() } },
                state = state,
                canRename = controller.canRenameSession,
                onRename = { renameTarget = state.selectedSession },
                canCreate = controller.canCreateSession,
                onCreate = controller::createSession,
            )
            state.error?.let { ErrorBanner("原生界面读取失败", it) }
            state.actionError?.let { ErrorBanner("操作未完成", it) }
            state.liveError?.let {
                ErrorBanner("实时连接提示", it, retryLabel = "重新连接", onRetry = controller::refresh)
            }
            state.notice?.let { NoticeBanner(it) }
            if (state.loading && state.sessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            if (state.sessions.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(top = 8.dp)) {
                    EmptyNativeState(
                        title = "还没有对话",
                        detail = "新建一个对话，开始与助手交流。",
                        onRefresh = controller::refresh,
                        onCreate = if (controller.canCreateSession) controller::createSession else null,
                    )
                }
                return@Column
            }
            Box(Modifier.weight(1f)) {
                MessageList(
                    state = state,
                    pendingApprovalCalls = approvalState.pendingFor(state.selectedId).mapNotNull { it.request.callId }.toSet(),
                    onOpenApproval = { showApprovalSheet = true },
                    onRetryFailed = { scope.launch { controller.retryFailedPrompt() } },
                    onFillFailed = { text -> composer.updateText(text) },
                    composerFailure = composerFailure,
                    composerPendingText = composerPendingText,
                    composerSending = composerState.sending,
                    composerRetryEnabled = composerFailure?.let {
                        DshComposerDraftPolicy.matches(it, composerState.text, composerState.attachments.size)
                    } == true,
                    composerRetryHint = composerFailure?.let {
                        DshComposerDraftPolicy.retryHint(it, composerState.text, composerState.attachments.size)
                    },
                    onRetryComposer = {
                        val failed = composerFailure
                        if (failed != null &&
                            DshComposerDraftPolicy.matches(failed, composerState.text, composerState.attachments.size)
                        ) {
                            composer.send()
                        }
                    },
                    onFillComposer = { text -> composer.updateText(text) },
                    onLoadOlder = controller::loadOlder,
                    onViewportTop = controller::onViewportTop
                )
                if (state.loading && state.messages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            if (state.liveError != null && state.messages.any { it.role == DshMessageRole.USER && !it.pending }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { scope.launch { controller.retryLastPrompt() } },
                        enabled = !state.running && !state.sending
                    ) { Text("重发上一条") }
                }
            }
            if (DshChatRenderPolicy.needsStopFallback(state, composerState)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = { scope.launch { controller.stop() } }, enabled = !state.stopping) {
                        Text(if (state.stopping) "停止中…" else "停止生成")
                    }
                }
            }
            // 失败原文已用卡片展示；这里的 sendError 只处理没有失败原文的校验/前置错误。
            state.sendError?.takeIf { state.failedPrompt == null }?.let { message ->
                ErrorBanner("发送未完成", message)
            }
            // 三条停靠条：审批 > 目标 > 待办，同屏最多两条，其余折叠为 +N。
            // 目标/待办只来自真实事件，缺失时对应条不占高度；高度动画由组件内部吸收到消息区，
            // 输入栏始终是页面纵向栈的最后一项（见 DshDockPlacement）。
            DshDockColumn(
                approvalVisible = DshApprovalEntry.of(approvalState, state.selectedId).visible,
                goal = state.goal,
                todo = state.todos,
                approvalBar = {
                    DshApprovalEntryBar(
                        state = approvalState,
                        sessionId = state.selectedId,
                        onClick = { showApprovalSheet = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            )
            ChatActivityBar(state.selectedId, activity, state.running)
            DshComposerBar(composer, Modifier.fillMaxWidth())
        }
    }
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!renameBusy) renameTarget = null },
            title = { Text("重命名对话") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameTitle,
                        onValueChange = { renameTitle = it.take(200); renameError = null },
                        label = { Text("对话名称") },
                        singleLine = true,
                        enabled = !renameBusy,
                        isError = renameError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    renameError?.let { Text(it, color = Color(TanukiDesign.tokens.colors.danger)) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !renameBusy && renameTitle.isNotBlank(),
                    onClick = {
                        renameBusy = true
                        scope.launch {
                            controller.renameSession(target.id, renameTitle).fold(
                                onSuccess = { renameTarget = null },
                                onFailure = { renameError = it.message ?: "重命名失败，请重试" }
                            )
                            renameBusy = false
                        }
                    }
                ) { Text(if (renameBusy) "保存中…" else "保存") }
            },
            dismissButton = {
                TextButton(enabled = !renameBusy, onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }
    DshApprovalSheet(
        visible = showApprovalSheet,
        onDismiss = { showApprovalSheet = false },
        state = approvalState,
        sessionId = state.selectedId,
        sessionTitle = state.selectedSession?.displayTitle,
        onAnswer = onApprovalAnswer,
        onRetry = onApprovalRetry,
        onClearRemembered = onClearRemembered,
        onForgetRemembered = onForgetRemembered,
        onDismissNotice = onDismissNotice
    )
}

@Composable
private fun Header(
    state: DshNativeState,
    activity: String,
    onDrawer: () -> Unit,
    canRename: Boolean,
    onRename: () -> Unit,
    canCreate: Boolean,
    onCreate: () -> Unit,
) {
    val colors = TanukiDesign.tokens.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = CircleShape, color = Color(colors.surface), border = BorderStroke(1.dp, Color(colors.outline))) {
            IconButton(onClick = onDrawer, modifier = Modifier.semantics { contentDescription = "打开对话列表" }) { Text("☰", color = Color(colors.textPrimary), fontSize = 20.sp) }
        }
        Surface(
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .clickable(enabled = canRename, onClickLabel = "重命名对话", onClick = onRename),
            shape = RoundedCornerShape(24.dp), color = Color(colors.surface),
            border = BorderStroke(1.dp, Color(colors.outline)), shadowElevation = 1.dp
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 5.dp), verticalArrangement = Arrangement.Center) {
                Text(state.selectedSession?.displayTitle ?: "新对话", fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium, color = Color(colors.textPrimary),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(5.dp).background(Color(colors.accent), CircleShape))
                    Text("$activity · ${state.selectedSession?.displayWorkspace ?: "工作区"}", fontSize = 11.sp, color = Color(colors.textSecondary),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Surface(shape = CircleShape, color = Color(colors.surface), border = BorderStroke(1.dp, Color(colors.outline))) {
            IconButton(onClick = onCreate, enabled = canCreate && !state.loading && !state.creating) {
                if (state.creating) CircularProgressIndicator(Modifier.size(18.dp), color = Color(colors.primary), strokeWidth = 2.dp)
                else Icon(Icons.Default.Add, "新建对话", tint = Color(colors.textPrimary))
            }
        }
    }
}

/** 本次观察计时不是服务端回合耗时；切换会话或状态后重新开始。 */
@Composable
private fun ChatActivityBar(sessionId: String?, activity: String, running: Boolean) {
    if (!running) return
    val colors = TanukiDesign.tokens.colors
    var seconds by remember(sessionId, activity) { mutableStateOf(0L) }
    LaunchedEffect(sessionId, activity) {
        val started = SystemClock.elapsedRealtime()
        while (true) {
            seconds = (SystemClock.elapsedRealtime() - started) / 1000
            delay(1000)
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TanukiIconSwap(activity, Modifier.size(20.dp)) {
            Text(if (it == "思考中") "◌" else "○", color = Color(colors.accent), fontSize = 18.sp)
        }
        Text(activity, fontSize = 12.sp, color = Color(colors.textPrimary))
        Text(when (activity) { "思考中" -> "它在酝酿"; "等你回答" -> "请选择…"; else -> "等待实时连接" }, modifier = Modifier.weight(1f),
            fontSize = 12.sp, color = Color(colors.textSecondary))
        TanukiAnimatedCount("观察 $seconds 秒", minWidth = 64.dp,
            style = MaterialTheme.typography.labelSmall, color = Color(colors.textSecondary))
    }
}

@Composable
private fun MessageList(
    state: DshNativeState,
    pendingApprovalCalls: Set<String>,
    onOpenApproval: () -> Unit,
    onRetryFailed: () -> Unit,
    onFillFailed: (String) -> Unit,
    composerFailure: DshFailedPrompt?,
    composerPendingText: String,
    composerSending: Boolean,
    composerRetryEnabled: Boolean,
    composerRetryHint: String?,
    onRetryComposer: () -> Unit,
    onFillComposer: (String) -> Unit,
    onLoadOlder: () -> Unit,
    onViewportTop: (Int?) -> Unit
) {
    // 只在消息或工具列表变化时重排时间线；流式文本更新不会重建整段历史。
    val timeline = remember(state.messages, state.tools) {
        buildTimeline(state.messages, state.tools)
    }
    val byKey = remember(timeline) { timeline.associateBy { it.stableKey() } }
    val activeFailure = composerFailure ?: state.failedPrompt
    val failureFromComposer = composerFailure != null
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 跟随状态按会话重建；长生命周期监听通过 currentFollow 读取当前会话实例，不能捕获旧状态委托。
    val follow = remember(state.selectedId) { DshChatFollowState() }
    val followSnapshot by follow.state.collectAsState()
    val following = followSnapshot.following
    val anchor = followSnapshot.anchor
    // 头部插页/释放前的视口行；revision 变化后按该行恢复，索引变化不会带动内容跳动。
    var restore by remember(state.selectedId) { mutableStateOf<DshViewportRestore?>(null) }
    var restoring by remember(state.selectedId) { mutableStateOf(false) }
    val currentTimeline by rememberUpdatedState(timeline)
    val currentByKey by rememberUpdatedState(byKey)
    val currentAnchor by rememberUpdatedState(anchor)
    val currentFollow by rememberUpdatedState(follow)

    // 两段结构：历史段（含锚点行）向上增长，新消息段从锚点下方向下增长。
    val split = remember(timeline, anchor) {
        anchor?.let { frozen -> DshChatAnchorPolicy.split(timeline, frozen.key) { it.stableKey() } }
    }
    val history = split?.history ?: timeline
    val live = split?.live ?: emptyList()
    val grouping = rememberSaveable(state.selectedId, saver = listSaver<DshChatGroupState, String>(
        save = { it.savedStarts }, restore = { DshChatGroupState(it.toSet()) }
    )) { DshChatGroupState() }
    val grouped = remember(grouping, history, live) { grouping.project(history, live) }
    val historyRows = grouped.history
    val liveRows = grouped.live
    // 展示快照独立于阅读锚点；资格跨普通重组保留，历史 revision 变化时全量立即显示。
    var previousMotion by remember(state.selectedId) { mutableStateOf(DshMessageMotionSnapshot()) }
    val streaming = state.streamingText.isNotBlank()
    val messageMotion = remember(previousMotion, historyRows, liveRows, state.historyRevision,
        state.loading, composerSending, streaming) {
        previousMotion.next(historyRows + liveRows, state.historyRevision, state.loading, composerSending, streaming)
    }
    SideEffect { previousMotion = messageMotion }
    val motionSpecs = TanukiDesign.motion
    val unseen = remember(split) { split?.let { DshChatAnchorPolicy.unseenMessages(it.live) } ?: 0 }
    // 与 LazyColumn 完全一致的渲染 key 顺序：顶部提示、历史段、锚点分隔行、新消息段。
    val renderKeys = remember(historyRows, liveRows, split) {
        DshChatAnchorPolicy.renderKeys(
            historyKeys = historyRows.map { it.key },
            liveKeys = liveRows.map { it.key },
            topKey = HISTORY_TOP_KEY.takeIf { timeline.isNotEmpty() },
            anchorKey = ANCHOR_ITEM_KEY.takeIf { split != null }
        )
    }
    val totalItems by remember { derivedStateOf { listState.layoutInfo.totalItemsCount } }
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }

    // 用户一动手就脱离跟随：拖动/惯性都会经过 nested scroll，程序滚动不会误触发。
    // 监听按会话重建，并始终通过 currentFollow 读写当前会话的状态。
    val detachOnUserScroll = remember(state.selectedId) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val detach = DshChatAnchorPolicy.shouldDetachOnUserScroll(
                    userInput = source == NestedScrollSource.UserInput,
                    delta = available.y,
                    atBottom = listState.isAtBottom(),
                    following = currentFollow.snapshot.following
                )
                // 冻结当前最后一行作为锚点；之后的新内容只进入下方段。
                if (detach) currentFollow.detach(currentTimeline)
                return Offset.Zero
            }
        }
    }
    // 回到底部才恢复跟随；上翻过程中不会自动拉回。
    LaunchedEffect(listState, state.selectedId) {
        snapshotFlow { listState.isAtBottom() }
            .collect { bottom ->
                // 监听不读取组合期的 following 局部值；始终按当前会话状态决定是否恢复。
                if (bottom && !restoring) follow.reattachIfDetached()
            }
    }
    // 跟随只负责贴底；用户手势进行中且已离开底部时让出手势，避免一帧抢滚动。
    LaunchedEffect(following, totalItems, timeline.size, state.streamingText.length, state.historyRevision) {
        if (!following || totalItems <= 0) return@LaunchedEffect
        if (listState.isScrollInProgress && !listState.isAtBottom()) return@LaunchedEffect
        listState.scrollToBottom()
    }
    // 持续报告视口顶部行：窗口释放只用它判断“哪些行在视口上方”。
    LaunchedEffect(listState, state.selectedId) {
        snapshotFlow {
            val info = listState.layoutInfo
            val row = info.visibleItemsInfo.firstOrNull { item ->
                val key = item.key as? String
                key == ANCHOR_ITEM_KEY || currentByKey.containsKey(key)
            }
            if (row == null) {
                DshViewportProbe(null, 0, currentTimeline.firstOrNull()?.seq)
            } else {
                val key = row.key as String
                val seq = if (key == ANCHOR_ITEM_KEY) currentAnchor?.seq else currentByKey[key]?.seq
                // 只有“目标行就是当前首行”时才能沿用 firstVisibleItemScrollOffset；
                // 顶部提示行之上的内容只把目标行对齐到视口顶部，避免负偏移被内容内边距截断。
                val offset = if (row.index == listState.firstVisibleItemIndex) {
                    listState.firstVisibleItemScrollOffset
                } else {
                    0
                }
                DshViewportProbe(key, offset, seq)
            }
        }.distinctUntilChanged().collect { probe ->
            if (restoring) return@collect
            probe.key?.let { restore = DshViewportRestore(it, probe.offset) }
            onViewportTop(probe.seq)
        }
    }
    // 头部插入更早页或释放窗口后，按行 key 回到同一内容；跟随时由贴底逻辑接管。
    LaunchedEffect(state.historyRevision, state.selectedId) {
        val request = restore ?: return@LaunchedEffect
        // 用当前状态而不是组合期快照，避免脱离跟随后仍按旧值跳过恢复。
        if (follow.snapshot.following) return@LaunchedEffect
        // 按实际渲染顺序定位：锚点之后的消息也要计入锚点分隔行，不能只按时间线索引 +1。
        val rowKey = DshChatRenderPolicy.rowKey(historyRows + liveRows, request.key) ?: request.key
        val target = DshChatAnchorPolicy.restoreIndex(renderKeys, rowKey, leadingRows = 0)
            ?: return@LaunchedEffect
        restore = null
        restoring = true
        try {
            listState.scrollToItem(target, request.offset)
        } finally {
            restoring = false
        }
    }
    // 用户上翻触顶才自动续页；首屏贴底时不会一次性把全部历史拉进内存。
    LaunchedEffect(
        atTop, following, state.loading, state.hasMore, state.loadingOlder, state.olderError, state.selectedId
    ) {
        if (!atTop || following || state.loading || !state.hasMore ||
            state.loadingOlder || state.olderError != null
        ) return@LaunchedEffect
        onLoadOlder()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(detachOnUserScroll),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (timeline.isNotEmpty()) {
                item(key = HISTORY_TOP_KEY, contentType = "history-top") {
                    HistoryTopRow(state = state, onLoadOlder = onLoadOlder)
                }
            }
            if (timeline.isEmpty() && activeFailure == null && !composerSending && !state.loading &&
                state.error == null && state.streamingText.isBlank()
            ) {
                item(key = "empty", contentType = "empty") {
                    EmptyNativeState(
                        title = "开始对话",
                        detail = "这个对话还没有消息，在下面输入就能开始"
                    )
                }
            }
            items(
                items = historyRows,
                key = { it.key },
                contentType = { if (it.tools.isEmpty()) "message" else "tool-group" }
            ) { row ->
                val rowMotion = messageMotion.motion(row.key, motionSpecs)
                val itemModifier = Modifier.animateItem(
                    placementSpec = rowMotion.placement,
                    fadeInSpec = rowMotion.fadeIn,
                    fadeOutSpec = rowMotion.fadeOut,
                )
                when (val item = row.item) {
                    is DshTimelineItem.Text -> MessageCard(item.message, itemModifier)
                    is DshTimelineItem.Tool -> DshToolGroupCard(row.tools, itemModifier, pendingApprovalCalls, onOpenApproval)
                }
            }
            if (split != null) {
                // 锚点固定在历史段与新消息段之间；新消息只从它下方增长。
                item(key = ANCHOR_ITEM_KEY, contentType = "anchor") {
                    ReadAnchorDivider(hasLive = live.isNotEmpty())
                }
            }
            items(
                items = liveRows,
                key = { it.key },
                contentType = { if (it.tools.isEmpty()) "message" else "tool-group" }
            ) { row ->
                val rowMotion = messageMotion.motion(row.key, motionSpecs)
                val itemModifier = Modifier.animateItem(
                    placementSpec = rowMotion.placement,
                    fadeInSpec = rowMotion.fadeIn,
                    fadeOutSpec = rowMotion.fadeOut,
                )
                when (val item = row.item) {
                    is DshTimelineItem.Text -> MessageCard(item.message, itemModifier)
                    is DshTimelineItem.Tool -> DshToolGroupCard(row.tools, itemModifier, pendingApprovalCalls, onOpenApproval)
                }
            }
            activeFailure?.let { failed ->
                item(key = "failed-${failed.time}", contentType = "failed") {
                    FailedPromptCard(
                        failed = failed,
                        sending = if (failureFromComposer) composerSending else state.sending,
                        retryEnabled = !failureFromComposer || composerRetryEnabled,
                        retryHint = if (failureFromComposer) composerRetryHint else null,
                        onRetry = if (failureFromComposer) onRetryComposer else onRetryFailed,
                        onFill = {
                            if (failureFromComposer) onFillComposer(failed.text) else onFillFailed(failed.text)
                        }
                    )
                }
            }
            if (composerSending) {
                item(key = "composer-pending", contentType = "pending") {
                    ComposerPendingCard(composerPendingText)
                }
            }
            if (state.streamingText.isNotBlank()) {
                item(key = "streaming", contentType = "streaming") { StreamingCard(state.streamingText) }
            } else if (state.running) {
                item(key = "waiting", contentType = "waiting") {
                    NativeCaption("DSH 正在处理；回复出现后会实时显示。", Modifier.padding(vertical = 4.dp))
                }
            }
        }
        if (!following) {
            FilledTonalButton(
                onClick = {
                    follow.reattach()
                    scope.launch { listState.scrollToBottom() }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Text(if (unseen > 0) "$unseen 条新消息" else "↓ 最新")
            }
        }
    }
}

/** 视口探针：一条可定位的行及其相对视口顶部的偏移。 */
private data class DshViewportProbe(val key: String?, val offset: Int, val seq: Int?)

/** 头部变化后要恢复到的行；只在用户脱离跟随且历史被插页/释放时使用。 */
private data class DshViewportRestore(val key: String, val offset: Int)

private const val ANCHOR_ITEM_KEY = "chat-anchor"
private const val HISTORY_TOP_KEY = "history-top"

/** 文本与工具按官方 seq 交错；本地发送占位始终排在最后。 */
private fun buildTimeline(messages: List<DshMessage>, tools: List<DshToolItem>): List<DshTimelineItem> =
    (messages.map { DshTimelineItem.Text(it) } + tools.map { DshTimelineItem.Tool(it) })
        .sortedWith(
            compareBy<DshTimelineItem> {
                if (it is DshTimelineItem.Text && it.message.pending) 1 else 0
            }.thenBy { it.seq }
        )

/** 区分历史段与新消息段的分界；没有新内容时只占位、不显示多余装饰。 */
@Composable
private fun ReadAnchorDivider(hasLive: Boolean) {
    if (!hasLive) {
        Spacer(Modifier.fillMaxWidth())
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text(
            "以下为新消息",
            Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color(TanukiDesign.tokens.colors.textSecondary)
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

/** 列表顶部：更早历史的加载入口与边界提示；没有更多时明确说明。 */
@Composable
private fun HistoryTopRow(state: DshNativeState, onLoadOlder: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.loadingOlder) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                NativeCaption("正在加载更早历史…")
            } else if (state.hasMore) {
                TextButton(onClick = onLoadOlder) { Text("加载更早历史") }
            } else {
                NativeCaption("已到对话最早一条")
            }
        }
        state.olderError?.let { NativeCaption("更早历史加载失败：$it") }
        if (state.releasedOlder > 0 && state.hasMore) {
            NativeCaption("已释放 ${state.releasedOlder} 条更早消息，向上滚动会重新读取")
        }
    }
}

@Composable
private fun ComposerPendingCard(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(color = Color(TanukiDesign.tokens.colors.primaryContainer),
            contentColor = Color(TanukiDesign.tokens.colors.textPrimary), shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(0.88f)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text.ifBlank { "正在发送附件…" }, style = MaterialTheme.typography.bodyMedium)
                Text("发送中…", fontSize = 11.sp, color = Color(TanukiDesign.tokens.colors.textSecondary))
            }
        }
    }
}

@Composable
private fun MessageCard(message: DshMessage, modifier: Modifier = Modifier) {
    val colors = TanukiDesign.tokens.colors
    val clipboard = LocalClipboardManager.current
    val assistant = message.role == DshMessageRole.ASSISTANT
    var details by remember(message.seq) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (assistant) {
            DshMarkdown(message.text)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TanukiCopyButton("复制", identity = message.seq, onCopy = { clipboard.setText(AnnotatedString(message.text)) })
                DshShareButton(message.text)
                IconButton(onClick = { details = true }, modifier = Modifier.semantics { contentDescription = "更多消息操作" }) { Text("⋯") }
                // 原生消息没有回合统计；小字可换行，避免窄屏把操作挤出屏幕。
                TextButton(onClick = { details = true }, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp)) {
                    Text("耗时与用量暂无数据 ›", fontSize = 11.sp, color = Color(colors.textSecondary))
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(modifier = Modifier.fillMaxWidth(0.88f), shape = RoundedCornerShape(16.dp),
                    color = Color(colors.primaryContainer), contentColor = Color(colors.textPrimary)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DshMarkdown(message.text)
                        if (message.pending) Text("发送中…", fontSize = 11.sp, color = Color(colors.textSecondary))
                    }
                }
            }
        }
    }
    if (details) AlertDialog(
        onDismissRequest = { details = false }, title = { Text("消息详情") },
        text = { Text("消息时间：${timeLabel(message.time)}\n本次回复的耗时、步骤数与用量暂无数据。") },
        confirmButton = { TextButton(onClick = { details = false }) { Text("关闭") } }
    )
}

/** 分享使用系统选择器；只有用户点击后才将本条内容交给目标应用。 */
@Composable
internal fun DshShareButton(text: String) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    TextButton(onClick = {
        failed = runCatching {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, "分享内容"))
        }.isFailure
    }) { Text("分享") }
    if (failed) AlertDialog(onDismissRequest = { failed = false }, title = { Text("分享未完成") },
        text = { Text("系统未能打开分享面板，可复制内容后重试。") },
        confirmButton = { TextButton(onClick = { failed = false }) { Text("关闭") } })
}

@Composable
private fun StreamingCard(text: String) {
    // 流式正文直接显示；状态和停止入口分别由状态条与输入栏承担。
    DshMarkdown(text, Modifier.fillMaxWidth(), streaming = true)
}

@Composable
private fun FailedPromptCard(
    failed: DshFailedPrompt,
    sending: Boolean,
    retryEnabled: Boolean = true,
    retryHint: String? = null,
    onRetry: () -> Unit,
    onFill: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val error = remember(failed.reason) { DshErrorPresenter.describe(failed.reason) }
    Surface(
        color = Color(TanukiDesign.tokens.colors.dangerContainer),
        contentColor = Color(TanukiDesign.tokens.colors.onDangerContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("发送失败", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(timeLabel(failed.time), style = MaterialTheme.typography.labelSmall)
            }
            Text(
                failed.text.ifBlank { "（附件消息）" },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Text(error.hint, style = MaterialTheme.typography.labelSmall)
            retryHint?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Color(TanukiDesign.tokens.colors.onDangerContainer))
            }
            SelectionContainer {
                Text(error.detail, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRetry, enabled = !sending && retryEnabled) { Text("重试") }
                TextButton(onClick = onFill, enabled = !sending && failed.text.isNotBlank()) { Text("填入输入框") }
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(failed.text)) },
                    enabled = failed.text.isNotBlank()
                ) { Text("复制内容") }
            }
        }
    }
}

@Composable
private fun ErrorBanner(
    title: String,
    detail: String,
    retryLabel: String = "重试",
    onRetry: (() -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    val info = remember(detail) { DshErrorPresenter.describe(detail) }
    Surface(
        color = Color(TanukiDesign.tokens.colors.dangerContainer),
        contentColor = Color(TanukiDesign.tokens.colors.onDangerContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(info.detail)) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) { Text("复制错误", style = MaterialTheme.typography.labelSmall) }
            }
            Text(info.hint, style = MaterialTheme.typography.labelSmall)
            SelectionContainer {
                Text(info.detail, style = MaterialTheme.typography.bodySmall)
            }
            onRetry?.let { retry ->
                TextButton(onClick = retry) { Text(retryLabel) }
            }
        }
    }
}

@Composable
private fun NoticeBanner(text: String) {
    Surface(
        color = Color(TanukiDesign.tokens.colors.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyNativeState(
    title: String,
    detail: String,
    onRefresh: (() -> Unit)? = null,
    onCreate: (() -> Unit)? = null,
) {
    Surface(
        color = Color(TanukiDesign.tokens.colors.surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            NativeCaption(detail)
            onCreate?.let { create ->
                TanukiPillButton(
                    text = "＋ 新建对话",
                    onClick = create,
                    tone = TanukiPillButtonTone.Filled,
                )
            }
            onRefresh?.let { refresh -> TextButton(onClick = refresh) { Text("刷新") } }
        }
    }
}

@Composable
private fun NativeCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = Color(TanukiDesign.tokens.colors.textSecondary))
}

/**
 * 跟随到底部：最后一页内容可能是一个持续变高的流式气泡，先确保末项已布局，
 * 再向下滚动到真正的内容底部，避免只看到长回复的开头。
 */
private suspend fun LazyListState.scrollToBottom() {
    val target = layoutInfo.totalItemsCount - 1
    if (target < 0) return
    if (layoutInfo.visibleItemsInfo.lastOrNull()?.index != target) scrollToItem(target)
    var remaining = 1_000_000f
    var attempts = 0
    while (remaining > 0f && attempts < 8) {
        val consumed = scrollBy(remaining)
        if (consumed <= 0f) break
        remaining -= consumed
        attempts++
    }
}

/** 底部判定读取 LazyColumn 的可见项与偏移，只有最后一项完整进入视口才算“在底部”。 */
private fun LazyListState.isAtBottom(): Boolean {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return false
    return DshChatRenderPolicy.isAtBottom(
        lastVisibleIndex = last.index,
        totalItems = info.totalItemsCount,
        lastItemEndOffset = last.offset + last.size,
        viewportEndOffset = info.viewportEndOffset
    )
}

private fun timeLabel(millis: Long): String {
    if (millis <= 0L) return "时间未知"
    return runCatching {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }.getOrDefault("时间未知")
}
