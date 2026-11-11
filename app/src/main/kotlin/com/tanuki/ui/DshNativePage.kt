package com.tanuki.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
    val sendTracker = remember(composer) { DshComposerSendTracker() }
    var fallbackFailure by remember { mutableStateOf<DshFailedPrompt?>(null) }
    var composerPendingText by remember { mutableStateOf("") }
    val emptyFailures = remember { MutableStateFlow<Map<String, DshComposerSendResult>>(emptyMap()) }
    val recordedFailures by (composerSendFailures ?: emptyFailures).collectAsState()
    val composerFailure = fallbackFailure ?: recordedFailures[state.selectedId]?.let {
        DshFailedPrompt(it.text, it.reason, it.time, it.attachmentCount)
    }
    var showApprovalSheet by rememberSaveable { mutableStateOf(false) }
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
    Column(modifier.fillMaxSize().imePadding().background(MaterialTheme.colorScheme.background)) {
        Header(
            state = state,
            onRefresh = controller::refresh,
            onSwitchOfficial = onSwitchOfficial
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
                    title = "官方 DSH 尚无会话",
                    detail = "在官方界面创建任务后，这里会显示官方会话。原生页不另建会话库。",
                    onRefresh = controller::refresh
                )
            }
            return@Column
        }
        SessionStrip(state.sessions, state.selectedId, controller::select)
        DshApprovalEntryBar(
            state = approvalState,
            sessionId = state.selectedId,
            onClick = { showApprovalSheet = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Box(Modifier.weight(1f)) {
            MessageList(
                state = state,
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
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { scope.launch { controller.retryLastPrompt() } },
                    enabled = !state.running && !state.sending
                ) { Text("重发上一条") }
            }
        }
        if (state.running) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { scope.launch { controller.stop() } }, enabled = !state.stopping) {
                    Text(if (state.stopping) "停止中…" else "停止生成")
                }
            }
        }
        // 失败原文已用卡片展示；这里的 sendError 只处理没有失败原文的校验/前置错误。
        state.sendError?.takeIf { state.failedPrompt == null }?.let { message ->
            ErrorBanner("发送未完成", message)
        }
        DshComposerBar(composer, Modifier.fillMaxWidth())
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
private fun Header(state: DshNativeState, onRefresh: () -> Unit, onSwitchOfficial: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("原生界面", style = MaterialTheme.typography.titleMedium)
                StatusPill(if (state.live) "实时" else "未实时", state.live)
                if (state.running) StatusPill("运行中", true)
            }
            Text(
                "消息、发送、停止与审批都走官方 DSH；不会另建聊天协议",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRefresh, enabled = !state.loading) {
            TanukiIcon(TanukiGlyph.Refresh, "刷新官方会话")
        }
        TextButton(onClick = onSwitchOfficial) { Text("官方界面") }
    }
}

@Composable
private fun MessageList(
    state: DshNativeState,
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
    val unseen = remember(split) { split?.let { DshChatAnchorPolicy.unseenMessages(it.live) } ?: 0 }
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
                if (bottom && !following && !restoring) follow.reattach()
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
        if (following) return@LaunchedEffect
        val target = when (request.key) {
            ANCHOR_ITEM_KEY -> 1 + history.size
            else -> DshChatAnchorPolicy.restoreIndex(timeline.map { it.stableKey() }, request.key)
        } ?: return@LaunchedEffect
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
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        title = "该会话没有可读文本消息",
                        detail = "原生页读取官方 user/message、assistant/message 与工具事件；点击下方输入框即可发送。"
                    )
                }
            }
            items(
                items = history,
                key = { item -> item.stableKey() },
                contentType = { item ->
                    when (item) {
                        is DshTimelineItem.Text -> "message"
                        is DshTimelineItem.Tool -> "tool"
                    }
                }
            ) { item ->
                when (item) {
                    is DshTimelineItem.Text -> MessageCard(item.message)
                    is DshTimelineItem.Tool -> DshToolCard(item.tool)
                }
            }
            if (split != null) {
                // 锚点固定在历史段与新消息段之间；新消息只从它下方增长。
                item(key = ANCHOR_ITEM_KEY, contentType = "anchor") {
                    ReadAnchorDivider(hasLive = live.isNotEmpty())
                }
            }
            items(
                items = live,
                key = { item -> item.stableKey() },
                contentType = { item ->
                    when (item) {
                        is DshTimelineItem.Text -> "message"
                        is DshTimelineItem.Tool -> "tool"
                    }
                }
            ) { item ->
                when (item) {
                    is DshTimelineItem.Text -> MessageCard(item.message)
                    is DshTimelineItem.Tool -> DshToolCard(item.tool)
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
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                NativeCaption("已到会话最早一条")
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
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("你 · 发送中", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(6.dp))
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
            }
            Text(
                text.ifBlank { "正在发送附件…" },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SessionStrip(sessions: List<DshSession>, selectedId: String?, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sessions.forEach { session ->
            val selected = session.id == selectedId
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(max = 240.dp).clickable { onSelect(session.id) }
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        session.displayTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        buildString {
                            append(if (session.running) "运行中" else "已结束")
                            append(" · ")
                            append(timeLabel(session.updatedAt))
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: DshMessage) {
    val clipboard = LocalClipboardManager.current
    val assistant = message.role == DshMessageRole.ASSISTANT
    Surface(
        color = when {
            message.pending -> MaterialTheme.colorScheme.secondaryContainer
            assistant -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = if (assistant && !message.pending) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (message.pending) "你 · 发送中" else message.role.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (message.pending) {
                    Spacer(Modifier.size(6.dp))
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                }
                Spacer(Modifier.weight(1f))
                Text(timeLabel(message.time), style = MaterialTheme.typography.labelSmall)
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(message.text)) },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) { Text("复制", style = MaterialTheme.typography.labelSmall) }
            }
            DshMarkdown(message.text)
        }
    }
}

@Composable
private fun StreamingCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DSH 正在回复", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.size(6.dp))
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
            }
            DshMarkdown(text)
        }
    }
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
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
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
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
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
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp)
    ) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EmptyNativeState(title: String, detail: String, onRefresh: (() -> Unit)? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            NativeCaption(detail)
            onRefresh?.let { refresh -> TextButton(onClick = refresh) { Text("刷新") } }
        }
    }
}

@Composable
private fun NativeCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
