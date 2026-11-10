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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanuki.web.DshMessage
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 原生只读页：左侧交互是官方会话列表，右侧是官方历史事件中的文本消息。
 * 没有可靠数据时明确显示空态或错误，不生成占位聊天内容。
 */
@Composable
fun DshNativePage(
    controller: DshNativeController,
    modifier: Modifier = Modifier,
    onFallback: (String) -> Unit,
    onSwitchOfficial: () -> Unit
) {
    val state by controller.state.collectAsState()
    LaunchedEffect(state.unavailableReason) {
        state.unavailableReason?.let(onFallback)
    }
    Column(modifier.fillMaxSize().imePadding().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("原生界面", style = MaterialTheme.typography.titleMedium)
                    StatusPill("只读", true)
                }
                Text(
                    "会话与消息来自官方 DSH 接口；不修改官方数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = controller::refresh, enabled = !state.loading) {
                TanukiIcon(TanukiGlyph.Refresh, "刷新官方会话")
            }
            TextButton(onClick = onSwitchOfficial) { Text("官方界面") }
        }
        state.error?.let { message ->
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("原生界面读取失败", fontWeight = FontWeight.SemiBold)
                    SelectionContainer { Text(message, style = MaterialTheme.typography.bodySmall) }
                    if (state.sessions.isNotEmpty()) {
                        Text("会话列表仍可用；可切换其他会话重试。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (state.loading && state.sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        if (state.sessions.isEmpty()) {
            EmptyNativeState(
                title = "官方 DSH 尚无会话",
                detail = "在官方界面创建任务后，这里会显示官方会话。原生页不另建会话库。",
                onRefresh = controller::refresh
            )
            return@Column
        }
        SessionStrip(state.sessions, state.selectedId, controller::select)
        if (state.loading) {
            Text(
                "正在读取官方消息…",
                Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.messages.isEmpty() && !state.loading && state.error == null) {
                item {
                    EmptyNativeState(
                        title = "该会话没有可读文本消息",
                        detail = "原生页只读取官方 session/page 的 user/message 与 assistant/message 文本；工具调用、附件和审批不在本切片展示。",
                        onRefresh = { state.selectedId?.let(controller::select) }
                    )
                }
            }
            items(state.messages, key = { it.seq }) { message -> MessageCard(message) }
            if (state.hasMore) {
                item {
                    NativeCaption("仅显示最近一页消息；更早历史请在官方界面查看。")
                }
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
        color = if (assistant) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (assistant) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(message.role.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
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
private fun EmptyNativeState(title: String, detail: String, onRefresh: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            NativeCaption(detail)
            TextButton(onClick = onRefresh) { Text("刷新") }
        }
    }
}

@Composable
private fun NativeCaption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun timeLabel(millis: Long): String {
    if (millis <= 0L) return "时间未知"
    return runCatching {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }.getOrDefault("时间未知")
}
