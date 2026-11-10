package com.tanuki.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanuki.web.DshApprovalOutcome
import com.tanuki.web.DshTimelineItem
import com.tanuki.web.DshMessage
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshSession
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 原生页：官方会话、历史、实时回复、发送/停止与审批都直接使用官方 DSH 协议。
 * 无法连接实时流时仍可读历史、发送与停止，并明确提示到官方界面处理审批。
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
        state.liveError?.let { ErrorBanner("实时连接提示", it) }
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
            MessageList(state, approvalState, onApprovalAnswer, onApprovalRetry, onClearRemembered)
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
        state.sendError?.let { message ->
            Text(
                message,
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
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
    onClearRemembered: () -> Unit
) {
    val timeline = (state.messages.map { DshTimelineItem.Text(it) } +
        state.tools.map { DshTimelineItem.Tool(it) })
        .sortedWith(
            compareBy<DshTimelineItem> {
                if (it is DshTimelineItem.Text && it.message.pending) 1 else 0
            }.thenBy { it.seq }
        )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "approval") {
            DshApprovalPanel(
                state = approvalState,
                sessionId = state.selectedId,
                onAnswer = onApprovalAnswer,
                onRetry = onApprovalRetry,
                onClearRemembered = onClearRemembered
            )
        }
        if (timeline.isEmpty() && !state.loading && state.error == null && state.streamingText.isBlank()) {
            item {
                EmptyNativeState(
                    title = "该会话没有可读文本消息",
                    detail = "原生页读取官方 user/message、assistant/message 与工具事件；点击下方输入框即可发送。"
                )
            }
        }
        items(
            timeline,
            key = { item ->
                when (item) {
                    is DshTimelineItem.Text ->
                        if (item.message.pending) "pending-${item.message.seq}" else "message-${item.message.seq}"
                    is DshTimelineItem.Tool -> item.tool.key
                }
            }
        ) { item ->
            when (item) {
                is DshTimelineItem.Text -> MessageCard(item.message)
                is DshTimelineItem.Tool -> DshToolCard(item.tool)
            }
        }
        if (state.streamingText.isNotBlank()) {
            item(key = "streaming") { StreamingCard(state.streamingText) }
        } else if (state.running) {
            item(key = "waiting") {
                NativeCaption("DSH 正在处理；回复出现后会实时显示。", Modifier.padding(vertical = 4.dp))
            }
        }
        if (state.hasMore) {
            item { NativeCaption("仅显示最近一页消息；更早历史请在官方界面查看。") }
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
                Spacer(Modifier.weight(1f))
                Text(timeLabel(message.time), style = MaterialTheme.typography.labelSmall)
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
            Text("DSH 正在回复", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            DshMarkdown(text)
        }
    }
}

@Composable
private fun ErrorBanner(title: String, detail: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            SelectionContainer { Text(detail, style = MaterialTheme.typography.bodySmall) }
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

private fun timeLabel(millis: Long): String {
    if (millis <= 0L) return "时间未知"
    return runCatching {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }.getOrDefault("时间未知")
}
