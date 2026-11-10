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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanuki.web.DshApproval
import com.tanuki.web.DshApprovalKind
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
    modifier: Modifier = Modifier,
    onFallback: (String) -> Unit,
    onSwitchOfficial: () -> Unit
) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf("") }
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
        if (state.currentApprovals.isNotEmpty()) {
            ApprovalPanel(
                approvals = state.currentApprovals,
                onAnswer = { approval, allow -> scope.launch { controller.answerApproval(approval, allow) } },
                onOfficial = onSwitchOfficial
            )
        }
        if (state.otherApprovalCount > 0) {
            NativeCaption(
                "其他会话有 ${state.otherApprovalCount} 个待审批；切换会话后可查看。",
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        Box(Modifier.weight(1f)) {
            MessageList(state)
            if (state.loading && state.messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
        Composer(
            state = state,
            draft = draft,
            onDraft = { draft = it.take(MAX_PROMPT_CHARS) },
            onSend = {
                val text = draft.trim()
                if (text.isNotEmpty() && !state.sending) {
                    scope.launch {
                        if (controller.send(text)) draft = ""
                    }
                }
            },
            onStop = { scope.launch { controller.stop() } },
            onOfficial = onSwitchOfficial
        )
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
private fun ApprovalPanel(
    approvals: List<DshApproval>,
    onAnswer: (DshApproval, Boolean) -> Unit,
    onOfficial: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(
            Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            approvals.forEach { approval ->
                Text(
                    if (approval.kind == DshApprovalKind.APPROVAL) "权限请求：${approval.title}"
                    else "需要确认：${approval.title}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                approval.detail?.let {
                    SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (approval.kind == DshApprovalKind.APPROVAL) {
                        TextButton(onClick = { onAnswer(approval, true) }) { Text("允许一次") }
                        TextButton(onClick = { onAnswer(approval, false) }) { Text("拒绝") }
                    }
                    TextButton(onClick = onOfficial) { Text("去官方处理") }
                }
            }
            if (approvals.size > 1) {
                NativeCaption("以上请求来自官方审批通道；原生只回传允许一次或拒绝。")
            }
        }
    }
}

@Composable
private fun MessageList(state: DshNativeState) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.messages.isEmpty() && !state.loading && state.error == null && state.streamingText.isBlank()) {
            item {
                EmptyNativeState(
                    title = "该会话没有可读文本消息",
                    detail = "原生页读取官方 user/message 与 assistant/message 文本；点击下方输入框即可发送。"
                )
            }
        }
        items(
            state.messages,
            key = { message -> if (message.pending) "pending-${message.seq}" else "message-${message.seq}" }
        ) { message -> MessageCard(message) }
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
private fun Composer(
    state: DshNativeState,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOfficial: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraft,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("发送消息给官方 DSH") },
                    maxLines = 5,
                    enabled = state.selectedId != null && !state.sending,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() })
                )
                if (state.running) {
                    OutlinedButton(onClick = onStop, enabled = !state.stopping) {
                        Text(if (state.stopping) "停止中…" else "停止")
                    }
                }
                Button(onClick = onSend, enabled = draft.isNotBlank() && !state.sending && state.selectedId != null) {
                    Text(if (state.sending) "发送中…" else "发送")
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                NativeCaption(if (state.live) "实时连接已建立" else "实时未连接；发送与停止仍可用")
                Spacer(Modifier.weight(1f))
                if (state.liveError != null || !state.live) {
                    TextButton(onClick = onOfficial) { Text("官方处理审批") }
                }
                NativeCaption("${draft.length}/$MAX_PROMPT_CHARS")
            }
            state.sendError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
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
            SelectionContainer {
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
            }
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
            SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium) }
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

private const val MAX_PROMPT_CHARS = 12_000
