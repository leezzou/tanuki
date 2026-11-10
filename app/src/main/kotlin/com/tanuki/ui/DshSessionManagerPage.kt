package com.tanuki.ui

import android.widget.Toast
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanuki.web.DshAdminSession
import com.tanuki.web.DshCommand
import com.tanuki.web.DshModelCatalog
import com.tanuki.web.DshModelSelection
import com.tanuki.web.DshPermissionCatalog
import com.tanuki.web.DshSessionSearchHit
import com.tanuki.web.DshSkill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 详情面板内部页面；同一时间只显示一层，返回键回到详情首页。 */
private enum class DshDetailMode { DETAIL, MODELS, PERMISSIONS, CATALOG }

/**
 * 原生会话管理页。
 *
 * 列表与写操作都调用官方接口；未读只在标题行显示本机标记。安全模式/试用模式
 * 下写操作全部禁用，只保留读取与目录查看。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DshSessionManagerPage(
    controller: DshSessionManagerController,
    running: Boolean,
    readOnly: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    remote: Boolean = false
) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var createDialog by remember { mutableStateOf(false) }
    var createTitle by remember { mutableStateOf("") }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailModeName by rememberSaveable { mutableStateOf(DshDetailMode.DETAIL.name) }
    var renameTarget by remember { mutableStateOf<DshAdminSession?>(null) }
    var renameTitle by remember { mutableStateOf("") }
    var archiveTarget by remember { mutableStateOf<DshAdminSession?>(null) }
    var fullAccess by remember { mutableStateOf<Pair<String, String>?>(null) }

    val detailMode = runCatching { DshDetailMode.valueOf(detailModeName) }.getOrDefault(DshDetailMode.DETAIL)
    val session = detailId?.let { id -> state.sessions.firstOrNull { it.id == id } }

    LaunchedEffect(state.selectedId, detailId, state.hasLoaded, state.loading) {
        // 归档或刷新后目标消失时关闭详情；加载中保留，等待 openSession 的刷新结果。
        if (detailId != null && state.hasLoaded && !state.loading && state.selectedId == null) {
            detailId = null
            detailModeName = DshDetailMode.DETAIL.name
        }
    }
    LaunchedEffect(detailMode, detailId) {
        val current = detailId ?: return@LaunchedEffect
        when (detailMode) {
            DshDetailMode.MODELS -> controller.loadModels()
            DshDetailMode.PERMISSIONS -> controller.loadPermissions()
            DshDetailMode.CATALOG -> controller.loadSessionCatalog(current)
            DshDetailMode.DETAIL -> Unit
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ManagerHeader(
                running = running,
                remote = remote,
                readOnly = readOnly,
                runningCount = state.sessions.count { it.running },
                loading = state.loading,
                busy = state.busy,
                onBack = onBack,
                onRefresh = controller::refresh
            )
        },
        floatingActionButton = {
            if (running && !state.readOnly) {
                ExtendedFloatingActionButton(
                    onClick = {
                        createTitle = ""
                        controller.clearMessages()
                        createDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("新建会话") }
                )
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = controller::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    label = { Text("搜索标题或会话 ID") },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.query.isNotBlank()) {
                                IconButton(onClick = {
                                    controller.setQuery("")
                                    controller.clearContentSearch()
                                }) { Icon(Icons.Default.Close, "清除搜索") }
                            }
                            IconButton(
                                onClick = controller::searchContent,
                                enabled = running && state.query.isNotBlank() && !state.contentSearching
                            ) { Icon(Icons.Default.Search, "搜索会话内容") }
                        }
                    }
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DshSessionFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { controller.setFilter(filter) },
                            label = { Text(filter.label) }
                        )
                    }
                    if (state.filter == DshSessionFilter.UNREAD) {
                        Text(
                            "未读为本机记录",
                            Modifier.align(Alignment.CenterVertically),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            state.error?.let { message ->
                ManagerBanner(message, error = true, onClose = controller::clearMessages)
            }
            state.notice?.let { message ->
                ManagerBanner(message, error = false, onClose = controller::clearMessages)
            }
            when {
                !running -> ManagerEmpty(
                    title = "DSH 未运行",
                    detail = "会话管理复用当前 DSH 实例的 loopback 接口。请先在应用内启动 DSH，再回到这里读取官方会话。",
                    action = null
                )
                state.loading && state.sessions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.sessions.isEmpty() -> ManagerEmpty(
                    title = if (state.hasLoaded) "官方 DSH 尚无会话" else "尚未读取会话",
                    detail = "原生页不另建会话库；新建会话会直接写入官方 DSH。",
                    action = if (state.hasLoaded && !state.readOnly) "新建会话" else null,
                    onAction = { createDialog = true }
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.query.isNotBlank() || state.contentSearching) {
                        item(key = "content-search") {
                            ContentSearchCard(
                                state = state,
                                onOpen = { hit ->
                                    detailId = hit.sessionId
                                    detailModeName = DshDetailMode.DETAIL.name
                                    controller.openSession(hit.sessionId)
                                },
                                onClear = controller::clearContentSearch
                            )
                        }
                    }
                    if (state.visibleSessions.isEmpty()) {
                        item(key = "empty-filter") {
                            ManagerEmpty(
                                title = if (state.filter == DshSessionFilter.UNREAD) "没有未读会话" else "没有匹配的会话",
                                detail = "调整搜索词或筛选条件后再试。",
                                action = null
                            )
                        }
                    }
                    items(state.visibleSessions, key = { it.id }) { item ->
                        SessionRow(
                            session = item,
                            unread = item.id in state.unreadIds,
                            onClick = {
                                detailId = item.id
                                detailModeName = DshDetailMode.DETAIL.name
                                controller.openSession(item.id)
                            }
                        )
                    }
                }
            }
        }
    }

    if (createDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) createDialog = false },
            title = { Text("新建会话") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = createTitle,
                        onValueChange = { createTitle = it.take(200) },
                        label = { Text("标题（可留空）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("标题可留空；创建立即调用官方 session/create，非空标题随后调用官方重命名。", style = MaterialTheme.typography.bodySmall)
                }
            },
            dismissButton = { TextButton(onClick = { createDialog = false }, enabled = !state.busy) { Text("取消") } },
            confirmButton = {
                TextButton(
                    onClick = {
                        val title = createTitle
                        createDialog = false
                        controller.createSession(title)
                    },
                    enabled = !state.busy
                ) { Text(if (state.busy) "创建中…" else "创建") }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!state.busy) renameTarget = null },
            title = { Text("重命名会话") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = renameTitle,
                        onValueChange = { renameTitle = it.take(200) },
                        label = { Text("新标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("最多 200 字；保存后以官方返回的标题为准。", style = MaterialTheme.typography.bodySmall)
                }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }, enabled = !state.busy) { Text("取消") } },
            confirmButton = {
                TextButton(
                    onClick = {
                        val title = renameTitle.trim()
                        renameTarget = null
                        controller.renameSession(target.id, title)
                    },
                    enabled = !state.busy && renameTitle.isNotBlank()
                ) { Text("保存") }
            }
        )
    }

    archiveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            title = { Text("归档会话") },
            text = {
                Text(
                    if (target.running) {
                        "该会话正在运行。归档会请求官方停止当前回合、子代理、后台任务与提醒，然后提交归档；此操作不会删除项目文件。"
                    } else {
                        "归档后该会话会从原生列表移除，项目文件不会删除。官方 0.2.1-rc.5 没有会话硬删除端点，本页不伪造删除。"
                    }
                )
            },
            dismissButton = { TextButton(onClick = { archiveTarget = null }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    val stop = target.running
                    archiveTarget = null
                    controller.archiveSession(target.id, stopActivity = stop)
                }) { Text(if (target.running) "停止并归档" else "归档") }
            }
        )
    }

    fullAccess?.let { (sessionId, preset) ->
        AlertDialog(
            onDismissRequest = { fullAccess = null },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("允许完全权限？") },
            text = {
                Text("完全权限会让 DSH 在后续工具调用中减少工作区边界限制。只在你明确需要、并理解风险时选择；Tanuki 不会自动切换该模式。")
            },
            dismissButton = { TextButton(onClick = { fullAccess = null }) { Text("保持当前权限") } },
            confirmButton = {
                TextButton(onClick = {
                    fullAccess = null
                    controller.selectPermission(sessionId, preset, allowFullAccess = true)
                }) { Text("仍要切换") }
            }
        )
    }

    if (session != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                detailId = null
                detailModeName = DshDetailMode.DETAIL.name
                controller.closeDetail()
            },
            sheetState = sheetState
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (detailMode != DshDetailMode.DETAIL) {
                        IconButton(onClick = { detailModeName = DshDetailMode.DETAIL.name }) {
                            Icon(Icons.Default.ArrowBack, "返回会话详情")
                        }
                    }
                    Text(
                        when (detailMode) {
                            DshDetailMode.DETAIL -> "会话详情"
                            DshDetailMode.MODELS -> "模型与推理强度"
                            DshDetailMode.PERMISSIONS -> "权限模式"
                            DshDetailMode.CATALOG -> "Skills 与斜杠命令"
                        },
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = {
                        detailId = null
                        detailModeName = DshDetailMode.DETAIL.name
                        controller.closeDetail()
                    }) { Icon(Icons.Default.Close, "关闭详情") }
                }
                when (detailMode) {
                    DshDetailMode.DETAIL -> DetailHome(
                        session = session,
                        readOnly = readOnly,
                        onModels = { detailModeName = DshDetailMode.MODELS.name },
                        onPermissions = { detailModeName = DshDetailMode.PERMISSIONS.name },
                        onCatalog = { detailModeName = DshDetailMode.CATALOG.name },
                        onRename = {
                            renameTitle = session.displayTitle
                            renameTarget = session
                        },
                        onArchive = { archiveTarget = session },
                        onCopyId = {
                            clipboard.setText(AnnotatedString(session.id))
                            Toast.makeText(context, "已复制会话 ID", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DshDetailMode.MODELS -> ModelsPane(
                        state = state,
                        session = session,
                        onSelect = { selection -> controller.selectModel(session.id, selection) },
                        onRetry = { controller.loadModels(force = true) }
                    )
                    DshDetailMode.PERMISSIONS -> PermissionsPane(
                        state = state,
                        session = session,
                        onSelect = { preset ->
                            if (preset == "danger-full-access") fullAccess = session.id to preset
                            else controller.selectPermission(session.id, preset)
                        },
                        onRetry = { controller.loadPermissions(force = true) }
                    )
                    DshDetailMode.CATALOG -> CatalogPane(
                        state = state,
                        onRetry = { controller.loadSessionCatalog(session.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ManagerHeader(
    running: Boolean,
    remote: Boolean,
    readOnly: Boolean,
    runningCount: Int,
    loading: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text("原生会话", style = MaterialTheme.typography.titleLarge)
                Text(
                    when {
                        !running -> "DSH 未运行"
                        readOnly -> "安全模式 · 只读"
                        remote -> "远程 DSH · 已连接"
                        runningCount > 0 -> "$runningCount 个会话运行中"
                        else -> "已连接官方 DSH"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefresh, enabled = running && !loading && !busy) {
                Icon(Icons.Default.Refresh, "刷新官方会话")
            }
        }
    }
}

@Composable
private fun SessionRow(session: DshAdminSession, unread: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (unread) {
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
                Text(
                    session.displayTitle,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (session.running) Pill("运行中", strong = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(timeLabel(session.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (session.blank) Text("· 空白会话", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (session.subagent) Text("· 子代理", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (session.formatStatus == "migration-required") {
                    Text("· 需迁移", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                session.currentPermission?.let { Pill(permissionLabel(it)) }
                session.model?.let { Pill(it.label) }
                if (session.agentAvailable && !session.running) Pill("Agent 在线")
            }
        }
    }
}

@Composable
private fun ContentSearchCard(
    state: DshSessionManagerState,
    onOpen: (DshSessionSearchHit) -> Unit,
    onClear: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("消息内容搜索", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = onClear) { Text("清除") }
            }
            if (state.contentSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!state.contentSearching && !state.contentSearched) {
                Text("点击搜索框右侧的搜索图标，调用官方接口搜索消息内容。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.contentSearched && !state.contentSearching && state.contentHits.isEmpty()) {
                Text("没有匹配的消息。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.contentHits.forEach { hit ->
                val title = state.sessions.firstOrNull { it.id == hit.sessionId }?.displayTitle
                    ?: "会话 ${hit.sessionId.take(8)}"
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(hit) }
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(hit.snippet, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (state.contentHasMore) {
                Text("仅显示官方返回的前 20 条，请缩小搜索词。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ManagerBanner(message: String, error: Boolean, onClose: () -> Unit) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (error) Icons.Default.Warning else Icons.Default.Info, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onClose) { Text("关闭") }
        }
    }
}

@Composable
private fun ManagerEmpty(title: String, detail: String, action: String?, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) {
        Icon(Icons.Default.Settings, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun DetailHome(
    session: DshAdminSession,
    readOnly: Boolean,
    onModels: () -> Unit,
    onPermissions: () -> Unit,
    onCatalog: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onCopyId: () -> Unit
) {
    Text(session.displayTitle, style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (session.running) Pill("运行中", strong = true)
        if (session.blank) Pill("空白会话")
        if (session.subagent) Pill("子代理")
        if (session.agentAvailable) Pill("Agent 在线")
    }
    Text("ID：${session.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("更新时间：${timeLabel(session.updatedAt)}", style = MaterialTheme.typography.bodySmall)
    session.cwd?.let { Text("工作目录：$it", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text("会话设置", style = MaterialTheme.typography.titleSmall)
    DetailAction("模型与推理强度", session.model?.label ?: "未选择模型", onModels)
    DetailAction("权限模式", session.currentPermission?.let(::permissionLabel) ?: "未知", onPermissions)
    DetailAction("Skills 与斜杠命令", "查看官方按当前会话返回的目录", onCatalog)
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    if (readOnly) {
        Text("安全模式/试用模式下只读，不能重命名或归档会话。可在设置中尝试恢复正常模式后再修改。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    } else {
        DetailAction("重命名会话", "最多 200 字", onRename)
        DetailAction("归档会话", if (session.running) "会先请求停止当前工作" else "从原生列表移除，不删除项目文件", onArchive)
    }
    TextButton(onClick = onCopyId, modifier = Modifier.fillMaxWidth()) { Text("复制会话 ID") }
}

@Composable
private fun DetailAction(title: String, detail: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.Edit, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ModelsPane(
    state: DshSessionManagerState,
    session: DshAdminSession,
    onSelect: (DshModelSelection) -> Unit,
    onRetry: () -> Unit
) {
    Text("当前：${session.model?.label ?: "未选择"}", style = MaterialTheme.typography.bodyMedium)
    state.modelsError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    if (state.modelsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    val catalog = state.models
    if (catalog == null) {
        if (!state.modelsLoading) {
            Text("尚未读取模型目录。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text("重试") }
        }
        return
    }
    catalog.default?.let { Text("官方默认：${it.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    catalog.groups.forEach { group ->
        Text(group.name.ifBlank { group.id }, style = MaterialTheme.typography.titleSmall)
        group.models.forEach { model ->
            val current = session.model?.provider == group.id && session.model.model == model.id
            var effort by remember(group.id, model.id, session.id) {
                mutableStateOf(model.defaultEffort ?: model.efforts.firstOrNull()?.id)
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = current,
                            onClick = {
                                if (state.canWrite) onSelect(DshModelSelection(group.id, model.id, effort))
                            },
                            enabled = state.canWrite
                        )
                        Column(Modifier.weight(1f)) {
                            Text(model.name, style = MaterialTheme.typography.labelLarge)
                            model.description?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (model.efforts.size > 1) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            model.efforts.forEach { option ->
                                FilterChip(
                                    selected = effort == option.id,
                                    onClick = { effort = option.id },
                                    enabled = state.canWrite,
                                    label = { Text(option.name) }
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { onSelect(DshModelSelection(group.id, model.id, effort)) },
                        enabled = state.canWrite
                    ) { Text(if (current) "重新选择" else "选择此模型") }
                }
            }
        }
    }
    if (catalog.failures.isNotEmpty()) {
        Text("部分模型不可用", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
        catalog.failures.forEach { failure ->
            Text("${failure.name.ifBlank { failure.id }}：${failure.message.ifBlank { "目录读取失败" }}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    if (catalog.groups.isEmpty()) {
        Text("官方未返回可选模型。请先在官方设置中配置模型服务。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PermissionsPane(
    state: DshSessionManagerState,
    session: DshAdminSession,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit
) {
    Text("当前：${session.currentPermission?.let(::permissionLabel) ?: "未知"}", style = MaterialTheme.typography.bodyMedium)
    state.permissionsError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    if (state.permissionsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    val catalog = state.permissions
    if (catalog == null) {
        if (!state.permissionsLoading) {
            Text("尚未读取权限目录。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text("重试") }
        }
        return
    }
    catalog.options.forEach { option ->
        val current = session.currentPermission == option.value
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = current,
                    onClick = { if (state.canWrite) onSelect(option.value) },
                    enabled = state.canWrite && option.value != "custom"
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(option.name, style = MaterialTheme.typography.labelLarge)
                    option.description?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (option.value == "danger-full-access") {
                        Text("需要二次确认；不会自动切换", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    Text("切换通过官方 /permission 命令提交，不改写 sandbox/approval 事件。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CatalogPane(state: DshSessionManagerState, onRetry: () -> Unit) {
    state.catalogError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    if (state.catalogLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    SectionTitle("Skills", state.skills.size)
    if (!state.catalogLoading && state.skills.isEmpty()) {
        Text("当前会话没有可展示的 Skills。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    state.skills.forEach { skill -> SkillRow(skill) }
    Spacer(Modifier.height(4.dp))
    SectionTitle("斜杠命令", state.commands.size)
    if (!state.catalogLoading && state.commands.isEmpty()) {
        Text("当前会话没有可展示的斜杠命令。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    state.commands.forEach { command -> CommandRow(command) }
    if (!state.catalogLoading && state.catalogError != null) TextButton(onClick = onRetry) { Text("重试") }
    Text("目录由官方按当前会话返回；斜杠命令在本页只展示，不在原生侧执行。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(8.dp))
        Pill(count.toString())
    }
}

@Composable
private fun SkillRow(skill: DshSkill) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("/${skill.name}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Pill(if (skill.modelInvocable) "模型可用" else "仅人工")
            }
            if (skill.description.isNotBlank()) Text(skill.description, style = MaterialTheme.typography.bodySmall)
            skill.whenToUse?.let { Text("适用：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun CommandRow(command: DshCommand) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("/${command.name}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (command.acceptsAttachments) Pill("支持附件")
            }
            if (command.description.isNotBlank()) Text(command.description, style = MaterialTheme.typography.bodySmall)
            command.inputHint?.let {
                Text("输入：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Pill(text: String, strong: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (strong) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

private fun permissionLabel(value: String): String = when (value) {
    "read-only" -> "只读"
    "workspace-write" -> "工作区写入"
    "danger-full-access" -> "完全权限"
    "auto" -> "自动"
    "custom" -> "自定义"
    else -> value
}

private fun timeLabel(millis: Long): String {
    if (millis <= 0L) return "时间未知"
    return runCatching {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }.getOrDefault("时间未知")
}
