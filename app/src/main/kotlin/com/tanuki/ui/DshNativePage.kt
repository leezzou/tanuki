package com.tanuki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanuki.web.DshApprovalOutcome
import com.tanuki.web.DshMessage
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshSession
import com.tanuki.web.DshTimelineItem
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
    onClearRemembered: () -> Unit = {}
) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
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
        Box(Modifier.weight(1f)) {
            MessageList(
                state = state,
                approvalState = approvalState,
                onApprovalAnswer = onApprovalAnswer,
                onApprovalRetry = onApprovalRetry,
                onClearRemembered = onClearRemembered,
                onRetryFailed = { scope.launch { controller.retryFailedPrompt() } },
                onFillFailed = { text -> composer.updateText(text) }
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
    approvalState: DshApprovalState,
    onApprovalAnswer: (String, DshApprovalOutcome, Boolean) -> Unit,
    onApprovalRetry: () -> Unit,
    onClearRemembered: () -> Unit,
    onRetryFailed: () -> Unit,
    onFillFailed: (String) -> Unit
) {
    // 只有消息或工具列表变化时才重排时间线；流式文本更新不会重建整段历史。
    val timeline = remember(state.messages, state.tools) {
        (state.messages.map { DshTimelineItem.Text(it) } + state.tools.map { DshTimelineItem.Tool(it) })
            .sortedWith(
                compareBy<DshTimelineItem> {
                    if (it is DshTimelineItem.Text && it.message.pending) 1 else 0
                }.thenBy { it.seq }
            )
    }
    var extraWindow by remember(state.selectedId) { mutableIntStateOf(0) }
    val window = remember(timeline, extraWindow) {
        DshChatRenderPolicy.window(timeline, DshChatRenderPolicy.WINDOW_SIZE + extraWindow)
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var following by remember(state.selectedId) { mutableStateOf(true) }
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    val totalItems by remember { derivedStateOf { listState.layoutInfo.totalItemsCount } }

    LaunchedEffect(dragging, state.selectedId) {
        following = DshChatRenderPolicy.followAfterDrag(dragging, listState.isAtBottom(), following)
    }
    // 用户松手/惯性结束后若已回到底部，恢复跟随；上滑过程中不会自动拉回。
    LaunchedEffect(listState, state.selectedId) {
        snapshotFlow { listState.isScrollInProgress to listState.isAtBottom() }
            .collect { (scrolling, bottom) ->
                if (!scrolling && bottom) following = true
            }
    }
    LaunchedEffect(following, totalItems, state.streamingText.length, timeline.size) {
        if (following && totalItems > 0) listState.scrollToBottom()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "approval", contentType = "approval") {
                DshApprovalPanel(
                    state = approvalState,
                    sessionId = state.selectedId,
                    onAnswer = onApprovalAnswer,
                    onRetry = onApprovalRetry,
                    onClearRemembered = onClearRemembered
                )
            }
            if (window.hasEarlier) {
                item(key = "earlier", contentType = "earlier") {
                    EarlierMessagesButton(
                        hidden = window.hiddenBefore,
                        onClick = { extraWindow = DshChatRenderPolicy.nextWindow(extraWindow) }
                    )
                }
            }
            if (timeline.isEmpty() && state.failedPrompt == null && !state.loading &&
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
                items = window.items,
                key = { item ->
                    when (item) {
                        is DshTimelineItem.Text ->
                            if (item.message.pending) "pending-${item.message.seq}" else "message-${item.message.seq}"
                        is DshTimelineItem.Tool -> item.tool.key
                    }
                },
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
            state.failedPrompt?.let { failed ->
                item(key = "failed-${failed.time}", contentType = "failed") {
                    FailedPromptCard(
                        failed = failed,
                        sending = state.sending,
                        onRetry = onRetryFailed,
                        onFill = { onFillFailed(failed.text) }
                    )
                }
            }
            if (state.streamingText.isNotBlank()) {
                item(key = "streaming", contentType = "streaming") { StreamingCard(state.streamingText) }
            } else if (state.running) {
                item(key = "waiting", contentType = "waiting") {
                    NativeCaption("DSH 正在处理；回复出现后会实时显示。", Modifier.padding(vertical = 4.dp))
                }
            }
            if (state.hasMore) {
                item(key = "has-more", contentType = "has-more") {
                    NativeCaption("官方仅返回最近一页消息；更早历史请在官方界面查看。")
                }
            }
        }
        if (!following && totalItems > 0) {
            FilledTonalButton(
                onClick = {
                    following = true
                    scope.launch { listState.scrollToBottom() }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) { Text("↓ 最新") }
        }
    }
}

@Composable
private fun EarlierMessagesButton(hidden: Int, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            NativeCaption("更早的 $hidden 条未渲染")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClick) { Text("显示更早") }
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
                failed.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Text(error.hint, style = MaterialTheme.typography.labelSmall)
            SelectionContainer {
                Text(error.detail, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRetry, enabled = !sending) { Text("重试") }
                TextButton(onClick = onFill, enabled = !sending) { Text("填入输入框") }
                TextButton(onClick = { clipboard.setText(AnnotatedString(failed.text)) }) { Text("复制内容") }
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
