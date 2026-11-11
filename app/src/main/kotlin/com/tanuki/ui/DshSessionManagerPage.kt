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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanuki.runtime.Redact
import com.tanuki.ui.design.TanukiBottomSheet
import com.tanuki.ui.design.TanukiPillButton
import com.tanuki.ui.design.TanukiPillButtonTone
import com.tanuki.ui.design.TanukiPressable
import com.tanuki.ui.design.TanukiShapeTokens
import com.tanuki.ui.design.TanukiSpace
import com.tanuki.ui.design.TanukiStatusChip
import com.tanuki.ui.design.TanukiStatusDot
import com.tanuki.ui.design.TanukiStatusTone
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

/** 会话列表的顶层状态；用于在状态切换时使用统一的动效令牌。 */
private enum class ManagerPhase { STOPPED, LOADING, ERROR, EMPTY, LIST }

/** 会话详情内的分页；概览之外只展示已接入或明确不支持的能力。 */
private enum class DshDetailTab(val label: String) {
    OVERVIEW("概览"),
    PROGRESS("进度"),
    ARTIFACTS("产物"),
    FILES("文件")
}

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
    remote: Boolean = false,
    hosts: List<DshSessionHost> = emptyList(),
    hostBusy: Boolean = false,
    hostMessage: String = "",
    hostError: String = "",
    onSelectHost: (String) -> Unit = {}
) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var createDialog by remember { mutableStateOf(false) }
    var createTitle by remember { mutableStateOf("") }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailModeName by rememberSaveable { mutableStateOf(DshDetailMode.DETAIL.name) }
    var detailTabName by rememberSaveable { mutableStateOf(DshDetailTab.OVERVIEW.name) }
    var renameTarget by remember { mutableStateOf<DshAdminSession?>(null) }
    var renameTitle by remember { mutableStateOf("") }
    var archiveTarget by remember { mutableStateOf<DshAdminSession?>(null) }
    var fullAccess by remember { mutableStateOf<Pair<String, String>?>(null) }
    var fullAccessAcknowledged by remember { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var batchArchiveConfirm by remember { mutableStateOf(false) }
    var batchLimitWarning by remember { mutableStateOf(false) }
    var hostSheet by remember { mutableStateOf(false) }
    var hostFeedbackDismissed by remember(hostMessage, hostError, hostBusy) { mutableStateOf(false) }

    val detailMode = runCatching { DshDetailMode.valueOf(detailModeName) }.getOrDefault(DshDetailMode.DETAIL)
    val detailTab = runCatching { DshDetailTab.valueOf(detailTabName) }.getOrDefault(DshDetailTab.OVERVIEW)
    val session = detailId?.let { id -> state.sessions.firstOrNull { it.id == id } }
    val currentHost = hosts.firstOrNull { it.active } ?: hosts.firstOrNull()
    val hostFeedback = DshSessionHostFeedback.forState(hostBusy, hostMessage, hostError)
    // 过滤与排序只在输入/数据变化时计算；输入过程中列表保持旧顺序，不会闪空。
    val visibleSessions = remember(state.sessions, state.query, state.filter, state.unreadIds, state.pinnedIds) {
        state.visibleSessions
    }
    val visibleGroups = remember(visibleSessions) { DshSessionGrouping.groups(visibleSessions) }
    val selectedIdSet = selectedIds.toSet()
    val selectedSessions = state.sessions.filter { it.id in selectedIdSet }

    LaunchedEffect(state.sessions) {
        // 归档/刷新后移除已不存在的选择，避免批量操作命中过期 ID。
        val existing = state.sessions.mapTo(HashSet()) { it.id }
        selectedIds = selectedIds.filter { it in existing }
        if (state.sessions.isEmpty()) {
            selectionMode = false
            selectedIds = emptyList()
        }
    }

    LaunchedEffect(state.selectedId, detailId, state.hasLoaded, state.loading) {
        // 归档或刷新后目标消失时关闭详情；加载中保留，等待 openSession 的刷新结果。
        if (detailId != null && state.hasLoaded && !state.loading && state.selectedId == null) {
            detailId = null
            detailModeName = DshDetailMode.DETAIL.name
            detailTabName = DshDetailTab.OVERVIEW.name
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
    LaunchedEffect(fullAccess) {
        if (fullAccess == null) fullAccessAcknowledged = false
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
                loading = state.loading || state.refreshing,
                busy = state.busy,
                selectionMode = selectionMode,
                hasSessions = state.sessions.isNotEmpty(),
                host = currentHost,
                onHostClick = { if (hosts.isNotEmpty() && !hostBusy) hostSheet = true },
                onToggleSelectionMode = {
                    selectionMode = !selectionMode
                    if (!selectionMode) selectedIds = emptyList()
                },
                onBack = onBack,
                onRefresh = controller::refresh
            )
        },
        floatingActionButton = {
            if (running && !state.readOnly) {
                // 主操作统一为胶囊按钮，不再混用 ExtendedFloatingActionButton。
                TanukiPillButton(
                    text = "新建会话",
                    onClick = {
                        createTitle = ""
                        controller.clearMessages()
                        createDialog = true
                    },
                    tone = TanukiPillButtonTone.Filled,
                    leading = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) },
                )
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
        ) {
            ManagerSearchHeader(
                state = state,
                visibleCount = visibleSessions.size,
                running = running,
                onQueryChange = controller::setQuery,
                onClearQuery = {
                    controller.setQuery("")
                    controller.clearContentSearch()
                },
                onContentSearch = controller::searchContent,
                onFilter = controller::setFilter
            )
            if (hostFeedback != null && !hostFeedbackDismissed) {
                ManagerBanner(hostFeedback.message, error = hostFeedback.error) {
                    hostFeedbackDismissed = true
                }
            }
            if (state.sessions.isNotEmpty()) {
                state.loadError?.let { message ->
                    ManagerBanner("刷新失败，仍显示上次结果：$message", error = true, onClose = controller::clearMessages)
                }
            }
            state.error?.let { message ->
                ManagerBanner(message, error = true, onClose = controller::clearMessages)
            }
            state.notice?.let { message ->
                ManagerBanner(message, error = false, onClose = controller::clearMessages)
            }
            if (state.batchTotal > 0) {
                BatchProgressBar(state.batchDone, state.batchTotal)
            } else if (selectionMode) {
                SelectionBar(
                    selectedCount = selectedIdSet.size,
                    runningCount = selectedSessions.count { it.running },
                    busy = state.busy,
                    onArchive = {
                        when {
                            selectedIdSet.isEmpty() || state.busy -> Unit
                            selectedIdSet.size > DshSessionManagerController.MAX_BATCH_ARCHIVE -> batchLimitWarning = true
                            else -> batchArchiveConfirm = true
                        }
                    },
                    onCancel = {
                        selectionMode = false
                        selectedIds = emptyList()
                    }
                )
            }
            Box(Modifier.weight(1f)) {
                // 状态切换（未运行/加载/错误/空/列表）走反馈令牌；移除动画时立即切换。
                val phase = when {
                    !running -> ManagerPhase.STOPPED
                    state.loading && state.sessions.isEmpty() -> ManagerPhase.LOADING
                    state.loadError != null && state.sessions.isEmpty() -> ManagerPhase.ERROR
                    state.sessions.isEmpty() -> ManagerPhase.EMPTY
                    else -> ManagerPhase.LIST
                }
                TanukiStateCrossfade(target = phase) { currentPhase ->
                    when (currentPhase) {
                        ManagerPhase.STOPPED -> ManagerEmpty(
                            title = "DSH 未运行",
                            detail = "会话管理复用当前 DSH 实例的 loopback 接口。请先在应用内启动 DSH，再回到这里读取官方会话。",
                            action = null
                        )
                        ManagerPhase.LOADING -> ManagerLoading()
                        ManagerPhase.ERROR -> ManagerError(
                            message = state.loadError.orEmpty(),
                            onRetry = controller::refresh
                        )
                        ManagerPhase.EMPTY -> ManagerEmpty(
                            title = if (state.hasLoaded) "官方 DSH 尚无会话" else "尚未读取会话",
                            detail = if (state.hasLoaded) {
                                "原生页不另建会话库；新建会话会直接写入官方 DSH。"
                            } else {
                                "点击读取，从当前 DSH 实例拉取官方会话列表。"
                            },
                            action = when {
                                !state.hasLoaded -> "读取会话"
                                state.readOnly -> null
                                else -> "新建会话"
                            },
                            onAction = {
                                if (state.hasLoaded) createDialog = true else controller.refresh()
                            },
                            secondaryAction = if (state.hasLoaded) "刷新列表" else null,
                            onSecondary = controller::refresh
                        )
                        ManagerPhase.LIST -> PullToRefreshBox(
                            isRefreshing = state.refreshing,
                            onRefresh = controller::refresh,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = TanukiSpace.LG_DP.dp,
                                    end = TanukiSpace.LG_DP.dp,
                                    top = TanukiSpace.XS_DP.dp,
                                    bottom = 96.dp,
                                ),
                                verticalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp)
                            ) {
                                if (state.contentSearching || state.contentSearched || state.contentHits.isNotEmpty()) {
                                    item(key = "content-search", contentType = "content-search") {
                                        ContentSearchCard(
                                            state = state,
                                            onOpen = { hit ->
                                                controller.clearMessages()
                                                detailId = hit.sessionId
                                                detailModeName = DshDetailMode.DETAIL.name
                                                detailTabName = DshDetailTab.OVERVIEW.name
                                                controller.openSession(hit.sessionId)
                                            },
                                            onClear = controller::clearContentSearch
                                        )
                                    }
                                }
                                if (visibleSessions.isEmpty()) {
                                    item(key = "empty-filter", contentType = "empty") {
                                        ManagerInlineEmpty(
                                            title = when {
                                                state.query.isNotBlank() -> "没有匹配的会话"
                                                state.filter == DshSessionFilter.UNREAD -> "没有未读会话"
                                                state.filter == DshSessionFilter.RUNNING -> "没有运行中的会话"
                                                else -> "没有可显示的会话"
                                            },
                                            detail = "标题、会话 ID 会随输入即时过滤；消息内容搜索需要单独点击搜索图标。",
                                            action = "清除搜索与筛选",
                                            onAction = {
                                                controller.setQuery("")
                                                controller.clearContentSearch()
                                                controller.setFilter(DshSessionFilter.ALL)
                                            }
                                        )
                                    }
                                }
                                visibleGroups.forEach { group ->
                                    item(key = "group:${group.key}", contentType = "group") {
                                        SessionGroupHeader(group)
                                    }
                                    items(group.sessions, key = { it.id }, contentType = { "session" }) { item ->
                                        SessionRow(
                                            session = item,
                                            unread = item.id in state.unreadIds,
                                            pinned = item.id in state.pinnedIds,
                                            selectionMode = selectionMode,
                                            selected = item.id in selectedIdSet,
                                            onTogglePin = { controller.togglePinned(item.id) },
                                            onToggleSelect = {
                                                selectedIds = if (item.id in selectedIdSet) {
                                                    selectedIds - item.id
                                                } else {
                                                    selectedIds + item.id
                                                }
                                            },
                                            onClick = {
                                                controller.clearMessages()
                                                detailId = item.id
                                                detailModeName = DshDetailMode.DETAIL.name
                                                detailTabName = DshDetailTab.OVERVIEW.name
                                                controller.openSession(item.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (hostSheet) {
        HostPickerSheet(
            hosts = hosts,
            busy = hostBusy,
            onDismiss = { hostSheet = false },
            onSelect = { id ->
                hostSheet = false
                onSelectHost(id)
            }
        )
    }

    // 列表层发起的弹层（新建、批量归档、批量上限）使用页面内 TanukiBottomSheet，
    // 展开/收起走 240-340ms 结构动效；从详情窗口发起的三条二级确认（重命名、
    // 归档、完全权限）必须用窗口级 AlertDialog：详情是 Material3 ModalBottomSheet
    // 的独立窗口，页面内 Box 会被它盖住（PR #70 评审 P1）。
    TanukiBottomSheet(
        visible = createDialog,
        onDismissRequest = { if (!state.busy) createDialog = false },
        title = "新建会话",
        subtitle = "标题可留空；创建立即调用官方 session/create，非空标题随后调用官方重命名。",
        // 输入法弹出时整体上移弹层，避免按钮被键盘挡住。
        modifier = Modifier.imePadding(),
    ) {
        OutlinedTextField(
            value = createTitle,
            onValueChange = { createTitle = it.take(200) },
            label = { Text("标题（可留空）") },
            singleLine = true,
            shape = TanukiShapeTokens.control,
            modifier = Modifier.fillMaxWidth()
        )
        SheetActions(
            secondary = "取消",
            onSecondary = { createDialog = false },
            secondaryEnabled = !state.busy,
            primary = if (state.busy) "创建中…" else "创建",
            onPrimary = {
                val title = createTitle
                createDialog = false
                controller.createSession(title)
            },
            primaryEnabled = !state.busy,
        )
    }

    renameTarget?.let { target ->
        // 从详情窗口发起，必须使用窗口级 Dialog，才能画在详情之上。
        AlertDialog(
            onDismissRequest = { if (!state.busy) renameTarget = null },
            title = { Text("重命名会话") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp)) {
                    OutlinedTextField(
                        value = renameTitle,
                        onValueChange = { renameTitle = it.take(200) },
                        label = { Text("新标题") },
                        singleLine = true,
                        shape = TanukiShapeTokens.control,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("最多 200 字；保存后以官方返回的标题为准。", style = MaterialTheme.typography.bodySmall)
                }
            },
            dismissButton = {
                TanukiPillButton(
                    text = "取消",
                    onClick = { renameTarget = null },
                    enabled = !state.busy,
                    tone = TanukiPillButtonTone.Ghost,
                )
            },
            confirmButton = {
                TanukiPillButton(
                    text = "保存",
                    onClick = {
                        val title = renameTitle.trim()
                        renameTarget = null
                        controller.renameSession(target.id, title)
                    },
                    enabled = !state.busy && renameTitle.isNotBlank(),
                    tone = TanukiPillButtonTone.Filled,
                )
            },
        )
    }

    archiveTarget?.let { target ->
        // 从详情窗口发起，必须使用窗口级 Dialog，才能画在详情之上。
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            title = { Text("归档会话") },
            text = {
                Text(
                    if (target.running) {
                        "该会话正在运行。归档会请求官方停止当前回合、子代理、后台任务与提醒，然后提交归档；会话会从原生列表移除，项目文件不会删除。本页不提供撤销按钮，请确认后继续。"
                    } else {
                        "归档后该会话会从原生列表移除，项目文件不会删除。官方接口当前没有会话硬删除端点，本页不提供删除，也不提供撤销按钮；请确认后继续。"
                    }
                )
            },
            dismissButton = {
                TanukiPillButton(
                    text = "取消",
                    onClick = { archiveTarget = null },
                    tone = TanukiPillButtonTone.Ghost,
                )
            },
            confirmButton = {
                TanukiPillButton(
                    text = if (target.running) "停止并归档" else "归档",
                    onClick = {
                        val stop = target.running
                        archiveTarget = null
                        controller.archiveSession(target.id, stopActivity = stop)
                    },
                    tone = TanukiPillButtonTone.Danger,
                )
            },
        )
    }

    val batchTargets = rememberLastNonNull(selectedIdSet.toList().ifEmpty { null })
    val batchRunning = rememberLastNonNull(selectedSessions.count { it.running }.takeIf { batchTargets != null })
    TanukiBottomSheet(
        visible = batchArchiveConfirm,
        onDismissRequest = { batchArchiveConfirm = false },
        title = "批量归档 ${batchTargets?.size ?: 0} 个会话",
    ) {
        val ids = batchTargets.orEmpty()
        val running = batchRunning ?: 0
        Text(
            buildString {
                append("已选择 ${ids.size} 个会话")
                if (running > 0) append("，其中 $running 个正在运行")
                append("。归档会逐个调用官方归档接口")
                if (running > 0) append("，并先请求停止运行中的会话")
                append("；会话会从列表移除，项目文件不会删除。")
                append("官方没有会话硬删除端点，本页不提供批量删除，也不提供撤销按钮。")
            },
            style = MaterialTheme.typography.bodySmall,
        )
        SheetActions(
            secondary = "取消",
            onSecondary = { batchArchiveConfirm = false },
            primary = if (running > 0) "停止并归档" else "归档",
            primaryTone = TanukiPillButtonTone.Danger,
            onPrimary = {
                batchArchiveConfirm = false
                // 只有控制器接受请求才清空选择；超限/只读等拒绝时保留选择。
                if (controller.archiveSessions(ids, stopRunning = true)) {
                    selectionMode = false
                    selectedIds = emptyList()
                }
            },
            primaryEnabled = ids.isNotEmpty() &&
                ids.size <= DshSessionManagerController.MAX_BATCH_ARCHIVE && !state.busy,
        )
    }

    TanukiBottomSheet(
        visible = batchLimitWarning,
        onDismissRequest = { batchLimitWarning = false },
        title = "一次最多归档 ${DshSessionManagerController.MAX_BATCH_ARCHIVE} 个会话",
    ) {
        Text(
            "当前已选择 ${selectedIdSet.size} 个会话，超过单次上限。请取消部分选择后再归档；当前选择会保留，不会静默截断。",
            style = MaterialTheme.typography.bodySmall,
        )
        SheetActions(
            primary = "知道了",
            onPrimary = { batchLimitWarning = false },
        )
    }

    fullAccess?.let { (sessionId, preset) ->
        // 从权限详情页发起，必须使用窗口级 Dialog，才能画在详情之上。
        AlertDialog(
            onDismissRequest = { fullAccess = null },
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text("完全权限二次确认") },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(TanukiSpace.MD_DP.dp)
                ) {
                    Text(
                        "你正在把会话切到完全权限。这是高于“工作区写入”的第二档风险，请逐条确认后果：",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    DshPermissionPresentation.fullAccessRisks().forEach { risk ->
                        Row(horizontalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp)) {
                            Text("•", style = MaterialTheme.typography.bodySmall)
                            Text(risk, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = TanukiSpace.TOUCH_TARGET_MIN_DP.dp).clickable {
                            fullAccessAcknowledged = !fullAccessAcknowledged
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = fullAccessAcknowledged,
                            onCheckedChange = null
                        )
                        Text("我已阅读并理解上述风险，仍要切换", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "Tanuki 不会自动切换该模式；切换通过官方 /permission 命令提交。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            dismissButton = {
                TanukiPillButton(
                    text = "保持当前权限",
                    onClick = { fullAccess = null },
                    tone = TanukiPillButtonTone.Ghost,
                )
            },
            confirmButton = {
                TanukiPillButton(
                    text = "仍要切换为完全权限",
                    onClick = {
                        val acknowledged = fullAccessAcknowledged
                        fullAccess = null
                        if (acknowledged) controller.selectPermission(sessionId, preset, allowFullAccess = true)
                    },
                    enabled = fullAccessAcknowledged && !state.busy,
                    tone = TanukiPillButtonTone.Danger,
                )
            },
        )
    }

    if (session != null) {
        // 详情沿用系统 ModalBottomSheet：拖拽、返回键和输入法行为由平台处理，
        // 其 300ms 结构动画本身落在令牌 240-340ms 区间；内容仍使用令牌间距与组件。
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                detailId = null
                detailModeName = DshDetailMode.DETAIL.name
                detailTabName = DshDetailTab.OVERVIEW.name
                controller.closeDetail()
            },
            sheetState = sheetState
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = TanukiSpace.XL_DP.dp)
                    .padding(bottom = TanukiSpace.XXL_DP.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(TanukiSpace.MD_DP.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (detailMode != DshDetailMode.DETAIL) {
                        IconButton(onClick = { detailModeName = DshDetailMode.DETAIL.name }) {
                        detailTabName = DshDetailTab.OVERVIEW.name
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
                        detailTabName = DshDetailTab.OVERVIEW.name
                        controller.closeDetail()
                    }) { Icon(Icons.Default.Close, "关闭详情") }
                }
                state.error?.let { SelectionErrorCard("操作未完成", it) }
                state.notice?.let { SelectionNoticeCard(it) }
                if (detailMode == DshDetailMode.DETAIL) {
                    PrimaryTabRow(selectedTabIndex = detailTab.ordinal) {
                        DshDetailTab.entries.forEach { tab ->
                            Tab(
                                selected = detailTab == tab,
                                onClick = { detailTabName = tab.name },
                                text = { Text(tab.label) }
                            )
                        }
                    }
                }
                when (detailMode) {
                    DshDetailMode.DETAIL -> when (detailTab) {
                        DshDetailTab.OVERVIEW -> DetailHome(
                            session = session,
                            catalog = state.models,
                            pinned = session.id in state.pinnedIds,
                            readOnly = readOnly,
                            onTogglePin = { controller.togglePinned(session.id) },
                            onModels = {
                                controller.clearMessages()
                                detailModeName = DshDetailMode.MODELS.name
                            },
                            onPermissions = {
                                controller.clearMessages()
                                detailModeName = DshDetailMode.PERMISSIONS.name
                            },
                            onCatalog = {
                                controller.clearMessages()
                                detailModeName = DshDetailMode.CATALOG.name
                            },
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
                        DshDetailTab.PROGRESS -> DetailProgressTab(session)
                        DshDetailTab.ARTIFACTS -> DetailArtifactsTab()
                        DshDetailTab.FILES -> DetailFilesTab(
                            session = session,
                            onCopyPath = { path ->
                                clipboard.setText(AnnotatedString(path))
                                Toast.makeText(context, "已复制工作目录", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
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
                            if (preset == "danger-full-access") {
                                fullAccessAcknowledged = false
                                fullAccess = session.id to preset
                            } else {
                                controller.selectPermission(session.id, preset)
                            }
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
    selectionMode: Boolean,
    hasSessions: Boolean,
    host: DshSessionHost?,
    onHostClick: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier.statusBarsPadding().fillMaxWidth().heightIn(min = 60.dp)
                .padding(horizontal = TanukiSpace.SM_DP.dp, vertical = TanukiSpace.XS_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
            Column(Modifier.weight(1f).padding(horizontal = TanukiSpace.XS_DP.dp)) {
                Text("原生会话", style = MaterialTheme.typography.titleLarge)
                if (host != null) {
                    HostChip(host = host, onClick = onHostClick)
                } else {
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
            }
            IconButton(onClick = onRefresh, enabled = running && !loading && !busy) {
                Icon(Icons.Default.Refresh, "刷新官方会话")
            }
            if (running && hasSessions && !readOnly) {
                TextButton(onClick = onToggleSelectionMode, enabled = !busy) {
                    Text(if (selectionMode) "取消选择" else "选择")
                }
            }
        }
    }
}

@Composable
private fun HostChip(host: DshSessionHost, onClick: () -> Unit) {
    // 主机胶囊用状态点表达“当前连接”，整行 32dp 视觉高度但保留胶囊形状。
    Surface(
        shape = TanukiShapeTokens.pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            Modifier.heightIn(min = 32.dp).padding(horizontal = TanukiSpace.SM_DP.dp, vertical = TanukiSpace.XS_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TanukiSpace.XS_DP.dp)
        ) {
            TanukiStatusDot(
                tone = if (host.active) TanukiStatusTone.Success else TanukiStatusTone.Neutral,
                diameter = 7.dp,
                decorative = true,
            )
            Text("${host.label} · ${host.detail}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Icon(Icons.Default.KeyboardArrowDown, "切换主机", Modifier.size(14.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HostPickerSheet(
    hosts: List<DshSessionHost>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("切换主机", style = MaterialTheme.typography.titleLarge)
            Text(
                "会话列表、模型与权限会读取当前选中主机的 DSH；切换不会删除本地或远程的会话与项目文件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            hosts.forEach { host ->
                Surface(
                    shape = TanukiShapeTokens.control,
                    color = if (host.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().clickable(
                        enabled = !busy && host.available && !host.active
                    ) { onSelect(host.id) }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = host.active, onClick = null, enabled = host.available && !busy)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TanukiStatusDot(
                                    tone = if (host.active) TanukiStatusTone.Success else TanukiStatusTone.Neutral,
                                    diameter = 7.dp,
                                    decorative = true,
                                )
                                Text(host.label, style = MaterialTheme.typography.labelLarge)
                                if (host.active) SessionBadge(TanukiSessionBadgeKind.CURRENT)
                            }
                            Text(
                                host.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!host.available && !host.active) {
                                Text(
                                    if (host.id == DshSessionHosts.REMOTE_ID) "请先在设置 → 远程主机保存并测试连接。" else "当前不可用。",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        if (host.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }
            Text(
                "远程连接失败会自动回退本机；切回本机不会自动停止远程正在运行的任务。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("关闭") }
        }
    }
}

@Composable
private fun ManagerSearchHeader(
    state: DshSessionManagerState,
    visibleCount: Int,
    running: Boolean,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onContentSearch: () -> Unit,
    onFilter: (DshSessionFilter) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = TanukiSpace.LG_DP.dp, vertical = TanukiSpace.SM_DP.dp),
        verticalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp)
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = TanukiShapeTokens.control,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            label = { Text("搜索标题或会话 ID") },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.query.isNotBlank()) {
                        IconButton(onClick = onClearQuery) { Icon(Icons.Default.Close, "清除搜索") }
                    }
                    IconButton(
                        onClick = onContentSearch,
                        enabled = running && state.query.isNotBlank() && !state.contentSearching
                    ) { Icon(Icons.Default.Search, "搜索会话内容") }
                }
            }
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 筛选条用胶囊按钮表达选中态：选中 Filled，未选中 Outline，触控区仍为 48dp。
            DshSessionFilter.entries.forEach { filter ->
                val selected = state.filter == filter
                TanukiPillButton(
                    text = filter.label,
                    onClick = { onFilter(filter) },
                    tone = if (selected) TanukiPillButtonTone.Filled else TanukiPillButtonTone.Outline,
                )
            }
            Text(
                when {
                    !state.hasLoaded && state.sessions.isEmpty() -> "等待读取"
                    state.sessions.isEmpty() -> "0 个会话"
                    state.query.isBlank() && state.filter == DshSessionFilter.ALL -> "${state.sessions.size} 个会话"
                    else -> "筛选 $visibleCount / ${state.sessions.size}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TanukiSpace.MD_DP.dp)) {
            Text(
                "输入即过滤标题与 ID，不阻断列表；消息内容需点搜索图标。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.filter == DshSessionFilter.UNREAD) {
                Text(
                    "未读仅本机记录",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: DshAdminSession,
    unread: Boolean,
    pinned: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onTogglePin: () -> Unit,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit
) {
    // 行容器统一走可按压卡片：0.98 缩放走反馈令牌，48dp 触控区由组件保证。
    TanukiPressable(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (selectionMode) onToggleSelect() else onClick() },
        shape = TanukiShapeTokens.card,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(min = TanukiSessionVisuals.ROW_MIN_HEIGHT_DP.dp)
                .background(
                    when {
                        selected -> MaterialTheme.colorScheme.primaryContainer
                        pinned -> MaterialTheme.colorScheme.surfaceContainerHigh
                        else -> MaterialTheme.colorScheme.surfaceContainerLow
                    }
                )
                .padding(
                    start = TanukiSpace.SM_DP.dp,
                    end = TanukiSpace.LG_DP.dp,
                    top = TanukiSpace.MD_DP.dp,
                    bottom = TanukiSpace.MD_DP.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp)) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = null,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Star,
                            if (pinned) "取消置顶" else "置顶",
                            tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    session.displayTitle,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (session.running) SessionBadge(TanukiSessionBadgeKind.RUNNING)
            }
            Row(
                // 时间与多个状态标签在窄屏/大字体下横向滚动，避免被压缩成省略号。
                Modifier.padding(start = TanukiSpace.XS_DP.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(TanukiSpace.XS_DP.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(timeLabel(session.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (unread) SessionBadge(TanukiSessionBadgeKind.UNREAD, label = "未读")
                if (pinned) SessionBadge(TanukiSessionBadgeKind.PINNED, label = "置顶")
                if (session.blank) SessionBadge(TanukiSessionBadgeKind.BLANK, label = "空白会话")
                if (session.subagent) SessionBadge(TanukiSessionBadgeKind.SUBAGENT, label = "子代理")
                if (session.formatStatus == "migration-required") {
                    SessionBadge(TanukiSessionBadgeKind.MIGRATION_REQUIRED, label = "需迁移")
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = TanukiSpace.XS_DP.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(TanukiSpace.XS_DP.dp)
            ) {
                session.currentPermission?.let {
                    TanukiStatusChip(
                        tone = TanukiSessionVisuals.permissionTone(it),
                        label = permissionLabel(it),
                    )
                }
                session.model?.let { TanukiStatusChip(tone = TanukiStatusTone.Neutral, label = it.label) }
                if (session.agentAvailable && !session.running) {
                    SessionBadge(TanukiSessionBadgeKind.AGENT_ONLINE, label = "Agent 在线")
                }
            }
        }
    }
}

/** 会话状态标签；语气和呼吸由 [TanukiSessionVisuals] 统一决定。 */
@Composable
private fun SessionBadge(kind: TanukiSessionBadgeKind, label: String? = null) {
    TanukiStatusChip(
        tone = TanukiSessionVisuals.badgeTone(kind),
        label = label ?: when (kind) {
            TanukiSessionBadgeKind.RUNNING -> "运行中"
            TanukiSessionBadgeKind.UNREAD -> "未读"
            TanukiSessionBadgeKind.PINNED -> "置顶"
            TanukiSessionBadgeKind.BLANK -> "空白会话"
            TanukiSessionBadgeKind.SUBAGENT -> "子代理"
            TanukiSessionBadgeKind.AGENT_ONLINE -> "Agent 在线"
            TanukiSessionBadgeKind.MIGRATION_REQUIRED -> "需迁移"
            TanukiSessionBadgeKind.CURRENT -> "当前"
        },
        pulse = TanukiSessionVisuals.badgePulse(kind),
    )
}

@Composable
private fun SessionGroupHeader(group: DshSessionGroup) {
    // 分组头与列表行使用同一组间距和标题字号，靠上留白而不是重色块区分层级。
    Row(
        Modifier.fillMaxWidth().padding(
            start = TanukiSpace.XS_DP.dp,
            end = TanukiSpace.XS_DP.dp,
            top = TanukiSpace.MD_DP.dp,
            bottom = TanukiSpace.XS_DP.dp,
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(group.title, style = MaterialTheme.typography.titleSmall)
            Text(
                group.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TanukiStatusChip(tone = TanukiStatusTone.Neutral, label = group.sessions.size.toString())
    }
}

@Composable
private fun SelectionBar(
    selectedCount: Int,
    runningCount: Int,
    busy: Boolean,
    onArchive: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = TanukiShapeTokens.card,
        modifier = Modifier.fillMaxWidth().padding(horizontal = TanukiSpace.LG_DP.dp, vertical = TanukiSpace.XS_DP.dp)
    ) {
        Column(
            Modifier.padding(horizontal = TanukiSpace.MD_DP.dp, vertical = TanukiSpace.SM_DP.dp),
            verticalArrangement = Arrangement.spacedBy(TanukiSpace.XS_DP.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append("已选 $selectedCount 个")
                        if (runningCount > 0) append(" · $runningCount 个运行中")
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge
                )
                TanukiPillButton(
                    text = if (runningCount > 0) "停止并归档" else "归档",
                    onClick = onArchive,
                    enabled = selectedCount > 0 && !busy,
                    tone = TanukiPillButtonTone.Tonal,
                )
                Spacer(Modifier.width(TanukiSpace.XS_DP.dp))
                TanukiPillButton(
                    text = "取消",
                    onClick = onCancel,
                    enabled = !busy,
                    tone = TanukiPillButtonTone.Ghost,
                )
            }
            Text(
                "删除：官方无会话硬删除端点，暂不支持；归档只从列表移除，不删除项目文件。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 批量归档进行中的独立进度条；不依赖多选模式，确认后仍持续显示到批次结束。 */
@Composable
private fun BatchProgressBar(done: Int, total: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = TanukiShapeTokens.card,
        modifier = Modifier.fillMaxWidth().padding(horizontal = TanukiSpace.LG_DP.dp, vertical = TanukiSpace.XS_DP.dp)
    ) {
        Column(
            Modifier.padding(horizontal = TanukiSpace.MD_DP.dp, vertical = TanukiSpace.SM_DP.dp),
            verticalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp)
        ) {
            Text("正在批量归档", style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "已完成 $done / $total；请保持页面打开，全部完成后会刷新列表。",
                style = MaterialTheme.typography.labelSmall
            )
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
        shape = TanukiShapeTokens.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(TanukiSpace.MD_DP.dp),
            verticalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("消息内容搜索", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TanukiPillButton(text = "清除", onClick = onClear, tone = TanukiPillButtonTone.Ghost)
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
                // 结果行同样是可按压卡片，点击进入会话详情。
                TanukiPressable(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpen(hit) },
                    shape = TanukiShapeTokens.control,
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(TanukiSpace.MD_DP.dp),
                        verticalArrangement = Arrangement.spacedBy(TanukiSpace.XS_DP.dp)
                    ) {
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
    val role = if (error) MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        color = role.first,
        contentColor = role.second,
        shape = TanukiShapeTokens.card,
        modifier = Modifier.fillMaxWidth().padding(horizontal = TanukiSpace.LG_DP.dp, vertical = TanukiSpace.XS_DP.dp)
    ) {
        Row(
            Modifier.padding(start = TanukiSpace.MD_DP.dp, end = TanukiSpace.XS_DP.dp, top = TanukiSpace.SM_DP.dp, bottom = TanukiSpace.SM_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(if (error) Icons.Default.Warning else Icons.Default.Info, null, Modifier.size(20.dp))
            Spacer(Modifier.width(TanukiSpace.SM_DP.dp))
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TanukiPillButton(text = "关闭", onClick = onClose, tone = TanukiPillButtonTone.Ghost)
        }
    }
}

/** 整页空态/前置状态：统一三态组件 + 页面自己的标题与动作，不伪造会话。 */
@Composable
private fun ManagerEmpty(
    title: String,
    detail: String,
    action: String?,
    onAction: () -> Unit = {},
    secondaryAction: String? = null,
    onSecondary: () -> Unit = {}
) {
    Box(
        Modifier.fillMaxSize().padding(TanukiSpace.XXL_DP.dp),
        contentAlignment = Alignment.Center
    ) {
        TanukiStateView(
            kind = TanukiStateKind.EMPTY,
            title = title,
            detail = detail,
            actionLabel = action,
            onAction = if (action != null) onAction else null,
            secondaryActionLabel = secondaryAction,
            onSecondaryAction = if (secondaryAction != null) onSecondary else null,
        )
    }
}

@Composable
private fun ManagerInlineEmpty(
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit
) {
    TanukiStateView(
        kind = TanukiStateKind.EMPTY,
        modifier = Modifier.padding(horizontal = TanukiSpace.LG_DP.dp, vertical = TanukiSpace.XXXL_DP.dp),
        title = title,
        detail = detail,
        actionLabel = action,
        onAction = onAction,
    )
}

@Composable
private fun ManagerLoading() {
    Column(
        Modifier.fillMaxSize().padding(horizontal = TanukiSpace.LG_DP.dp, vertical = TanukiSpace.XL_DP.dp),
        verticalArrangement = Arrangement.spacedBy(TanukiSpace.MD_DP.dp)
    ) {
        // 只读取当前实例，不新建或改写会话；加载态不伪造进度。
        TanukiStateView(
            kind = TanukiStateKind.LOADING,
            subject = "会话",
            detail = "只读取当前 DSH 实例，不会新建或改写会话。",
        )
        repeat(3) { SkeletonSessionRow() }
    }
}

@Composable
private fun SkeletonSessionRow() {
    Surface(
        shape = TanukiShapeTokens.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(TanukiSpace.LG_DP.dp), verticalArrangement = Arrangement.spacedBy(TanukiSpace.MD_DP.dp)) {
            Box(
                Modifier.fillMaxWidth(0.55f).height(16.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
            )
            Box(
                Modifier.fillMaxWidth(0.3f).height(12.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
            )
        }
    }
}

@Composable
private fun ManagerError(message: String, onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxSize().padding(TanukiSpace.XXL_DP.dp),
        contentAlignment = Alignment.Center
    ) {
        TanukiStateView(
            kind = TanukiStateKind.ERROR,
            title = "读取官方会话失败",
            detail = buildString {
                append(message.ifBlank { "官方接口没有返回可用的会话列表。" })
                append("\n请确认 DSH 正在运行、远程隧道已连接，或稍后重试。")
            },
            actionLabel = "重试",
            onAction = onRetry,
        )
    }
}

@Composable
private fun DetailHome(
    session: DshAdminSession,
    catalog: DshModelCatalog?,
    pinned: Boolean,
    readOnly: Boolean,
    onTogglePin: () -> Unit,
    onModels: () -> Unit,
    onPermissions: () -> Unit,
    onCatalog: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onCopyId: () -> Unit
) {
    Text(session.displayTitle, style = MaterialTheme.typography.titleMedium)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(TanukiSpace.XS_DP.dp)
    ) {
        if (session.running) SessionBadge(TanukiSessionBadgeKind.RUNNING)
        if (session.blank) SessionBadge(TanukiSessionBadgeKind.BLANK)
        if (session.subagent) SessionBadge(TanukiSessionBadgeKind.SUBAGENT)
        if (session.agentAvailable) SessionBadge(TanukiSessionBadgeKind.AGENT_ONLINE)
        if (pinned) SessionBadge(TanukiSessionBadgeKind.PINNED, label = "本机置顶")
    }
    Text("ID：${session.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("更新时间：${timeLabel(session.updatedAt)}", style = MaterialTheme.typography.bodySmall)
    session.cwd?.let { Text("工作目录：$it", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Text("会话设置", style = MaterialTheme.typography.titleSmall)
    DetailAction("模型与推理强度", DshModelPresentation.selectionLabel(catalog, session.model), onModels)
    DetailAction(
        "权限模式",
        session.currentPermission?.let(::permissionLabel) ?: "未知",
        onPermissions
    )
    DetailAction("Skills 与斜杠命令", "查看官方按当前会话返回的目录", onCatalog)
    DetailAction(
        if (pinned) "取消本机置顶" else "置顶会话",
        if (pinned) "恢复按更新时间排序" else "仅调整本机列表顺序，不写回官方",
        onTogglePin
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    if (readOnly) {
        Text("安全模式/试用模式下只读，不能重命名、归档或切换会话设置。可在设置中尝试恢复正常模式后再修改。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    } else {
        DetailAction("重命名会话", "最多 200 字，保存后以官方返回标题为准", onRename)
        DetailAction(
            "归档会话",
            if (session.running) "会先请求停止当前回合、子代理、后台任务与提醒，再从列表移除" else "从原生列表移除；不删除项目文件",
            onArchive
        )
    }
    DetailInfo(
        title = "删除会话",
        detail = DshSessionCapabilities.DELETE_UNAVAILABLE_REASON,
        warning = true,
        icon = Icons.Default.Delete
    )
    TextButton(onClick = onCopyId, modifier = Modifier.fillMaxWidth()) { Text("复制会话 ID") }
}

@Composable
private fun DetailAction(title: String, detail: String, onClick: () -> Unit) {
    Surface(
        shape = TanukiShapeTokens.control,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 只说明能力边界，不提供点击动作；用于协议缺失的删除等操作。 */
@Composable
private fun DetailInfo(
    title: String,
    detail: String,
    warning: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        shape = TanukiShapeTokens.control,
        color = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("$title（官方接口不支持）", style = MaterialTheme.typography.labelLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DetailProgressTab(session: DshAdminSession) {
    Surface(
        shape = TanukiShapeTokens.control,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("运行状态", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (session.running) "运行中" else "空闲", style = MaterialTheme.typography.titleSmall)
            Text(
                if (session.agentAvailable) "Agent 在线" else "Agent 不在线",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    DetailUnavailable(
        title = "待办与目标暂不支持",
        detail = "官方 goals/todos 协议尚未接入原生会话管理；本页不会伪造进度百分比或待办数量。需要查看目标与待办时请使用官方 DSH 页面。"
    )
}

@Composable
private fun DetailArtifactsTab() {
    DetailUnavailable(
        title = "产物列表与预览暂不支持",
        detail = "官方 artifacts/workspaceFiles 字段契约尚未核对接入；当前运行时也没有向会话管理暴露本地 artifacts 目录。本页不会生成假产物或空预览；后续拿到只读目录后只读列出、不复制。"
    )
}

@Composable
private fun DetailFilesTab(session: DshAdminSession, onCopyPath: (String) -> Unit) {
    val cwd = session.cwd?.takeIf { it.isNotBlank() }
    Surface(
        shape = TanukiShapeTokens.control,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("工作目录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(cwd ?: "官方未返回工作目录", style = MaterialTheme.typography.bodyMedium)
            if (cwd != null) {
                TextButton(onClick = { onCopyPath(cwd) }) { Text("复制工作目录") }
            }
        }
    }
    DetailUnavailable(
        title = "文件浏览与预览暂不支持",
        detail = "会话详情内的文件列表、只读预览与产物打开需要 workspaceFiles 接口；当前未接入，因此不提供假文件树。请使用文件页查看本机工作区。"
    )
}

/** 明确展示未接入能力，不生成可点击的假操作。 */
@Composable
private fun DetailUnavailable(title: String, detail: String) {
    Surface(
        shape = TanukiShapeTokens.control,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, null, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
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
    val catalog = state.models
    val currentLabel = DshModelPresentation.selectionLabel(catalog, session.model)
    Surface(
        shape = TanukiShapeTokens.control,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("当前选择", style = MaterialTheme.typography.labelSmall)
            Text(currentLabel, style = MaterialTheme.typography.titleSmall)
            Text(
                "切换会写回当前官方会话；目录来自 session/modelCatalog。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    state.modelSelectionError?.takeIf { it.sessionId == session.id }?.let {
        SelectionErrorCard("模型切换失败", it.message)
    }
    if (state.modelsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.modelsError?.let { message ->
        SelectionErrorCard("模型目录读取失败", message)
        TextButton(onClick = onRetry) { Text("重新读取模型目录") }
    }
    if (catalog == null) {
        if (!state.modelsLoading && state.modelsError == null) {
            Text("尚未读取模型目录。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text("读取模型目录") }
        }
        return
    }
    if (!state.canWrite) {
        Text(
            if (state.readOnly) "安全模式/试用模式下只读，不能切换模型。" else "正在执行其他会话操作，完成后再切换模型。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    catalog.default?.let {
        Text(
            "官方默认：${DshModelPresentation.selectionLabel(catalog, it)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    catalog.groups.forEach { group ->
        Text(group.name.ifBlank { group.id }, style = MaterialTheme.typography.titleSmall)
        group.models.forEach { model ->
            val current = session.model?.provider == group.id && session.model.model == model.id
            val currentEffort = session.model?.reasoningEffort?.takeIf { current }
            var effort by remember(group.id, model.id, session.id, current, currentEffort) {
                mutableStateOf(
                    currentEffort
                        ?: model.defaultEffort
                        ?: model.efforts.firstOrNull()?.id
                )
            }
            Surface(
                shape = TanukiShapeTokens.control,
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
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(model.name, style = MaterialTheme.typography.labelLarge)
                                if (current) SessionBadge(TanukiSessionBadgeKind.CURRENT)
                            }
                            model.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (model.efforts.size > 1) {
                        Text("推理强度", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(TanukiSpace.XS_DP.dp)
                        ) {
                            // 推理强度沿用筛选条的胶囊语义：选中 Filled，未选中 Outline。
                            model.efforts.forEach { option ->
                                TanukiPillButton(
                                    text = option.name,
                                    onClick = { effort = option.id },
                                    enabled = state.canWrite,
                                    tone = if (effort == option.id) TanukiPillButtonTone.Filled
                                    else TanukiPillButtonTone.Outline,
                                )
                            }
                        }
                        model.efforts.firstOrNull { it.id == effort }?.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TanukiPillButton(
                        text = when {
                            !current -> "切换到此模型"
                            effort != currentEffort -> "应用所选推理强度"
                            else -> "重新应用当前选择"
                        },
                        onClick = { onSelect(DshModelSelection(group.id, model.id, effort)) },
                        enabled = state.canWrite,
                        tone = TanukiPillButtonTone.Tonal,
                    )
                }
            }
        }
    }
    if (catalog.failures.isNotEmpty()) {
        Text("部分模型不可用", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
        catalog.failures.forEach { failure ->
            val reason = failure.message.takeIf { it.isNotBlank() }
                ?.let { Redact.text(it).take(200) }
                ?: "模型目录读取失败"
            Text(
                "${failure.name.ifBlank { failure.id }}：$reason",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
    if (catalog.groups.isEmpty()) {
        Text(
            "官方未返回可选模型。请先在官方设置中配置模型服务，再回到这里重试。",
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = onRetry) { Text("重新读取模型目录") }
    }
}

@Composable
private fun SelectionErrorCard(title: String, message: String) {
    Surface(
        shape = TanukiShapeTokens.control,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Warning, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SelectionNoticeCard(message: String) {
    Surface(
        shape = TanukiShapeTokens.control,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PermissionsPane(
    state: DshSessionManagerState,
    session: DshAdminSession,
    onSelect: (String) -> Unit,
    onRetry: () -> Unit
) {
    val catalog = state.permissions
    Surface(
        shape = TanukiShapeTokens.control,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("当前权限", style = MaterialTheme.typography.labelSmall)
            Text(
                session.currentPermission?.let(::permissionLabel) ?: "未知",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "权限通过官方 /permission 命令提交，不改写 sandbox/approval 事件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    state.permissionSelectionError?.takeIf { it.sessionId == session.id }?.let {
        SelectionErrorCard("权限切换失败", it.message)
    }
    if (state.permissionsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.permissionsError?.let { message ->
        SelectionErrorCard("权限目录读取失败", message)
        TextButton(onClick = onRetry) { Text("重新读取权限目录") }
    }
    if (catalog == null) {
        if (!state.permissionsLoading && state.permissionsError == null) {
            Text("尚未读取权限目录。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text("读取权限目录") }
        }
        return
    }
    if (!state.canWrite) {
        Text(
            if (state.readOnly) "安全模式/试用模式下只读，不能切换权限。" else "正在执行其他会话操作，完成后再切换权限。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    catalog.options.forEach { option ->
        val current = session.currentPermission == option.value
        val selectable = DshPermissionPresentation.canSelect(option.value)
        val guidance = DshPermissionPresentation.guidance(option.value, option.name)
        Surface(
            shape = TanukiShapeTokens.control,
            color = if (current) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                RadioButton(
                    selected = current,
                    onClick = { if (state.canWrite && selectable) onSelect(option.value) },
                    enabled = state.canWrite && selectable
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(TanukiSpace.XS_DP.dp)
                    ) {
                        Text(option.name, style = MaterialTheme.typography.labelLarge)
                        if (current) SessionBadge(TanukiSessionBadgeKind.CURRENT)
                        if (!selectable) TanukiStatusChip(tone = TanukiStatusTone.Neutral, label = "仅展示")
                    }
                    Text(guidance, style = MaterialTheme.typography.bodySmall)
                    option.description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "官方说明：${it.take(300)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (option.value == "danger-full-access") {
                        Text(
                            "需要二次确认并勾选风险确认；Tanuki 不会自动切换。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (!selectable) {
                        Text(
                            "自定义预设由官方配置决定，本页不能直接切换。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    if (catalog.options.isEmpty()) {
        Text("官方未返回可选权限预设。", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetry) { Text("重新读取权限目录") }
    }
    Text(
        "完全权限会放宽工作区边界，只在明确需要时选择；切换失败会保留当前权限并显示原因。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
        TanukiStatusChip(tone = TanukiStatusTone.Neutral, label = count.toString())
    }
}

@Composable
private fun SkillRow(skill: DshSkill) {
    Surface(shape = TanukiShapeTokens.control, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("/${skill.name}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                TanukiStatusChip(tone = if (skill.modelInvocable) TanukiStatusTone.Info else TanukiStatusTone.Neutral, label = if (skill.modelInvocable) "模型可用" else "仅人工")
            }
            if (skill.description.isNotBlank()) Text(skill.description, style = MaterialTheme.typography.bodySmall)
            skill.whenToUse?.let { Text("适用：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun CommandRow(command: DshCommand) {
    Surface(shape = TanukiShapeTokens.control, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("/${command.name}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (command.acceptsAttachments) TanukiStatusChip(tone = TanukiStatusTone.Neutral, label = "支持附件")
            }
            if (command.description.isNotBlank()) Text(command.description, style = MaterialTheme.typography.bodySmall)
            command.inputHint?.let {
                Text("输入：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 弹层底部动作排：次操作在左、主操作在右，间距走令牌。
 *
 * 只传主操作时用于单按钮提示；危险动作由调用方指定 Danger 语气。
 */
@Composable
private fun SheetActions(
    primary: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean = true,
    primaryTone: TanukiPillButtonTone = TanukiPillButtonTone.Filled,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
    secondaryEnabled: Boolean = true,
) {
    val largeFont = LocalDensity.current.fontScale >= TanukiLayoutPolicy.LARGE_FONT_SCALE
    if (largeFont) {
        // 1.3x/2x 字体时改为整行按钮，避免“仍要切换为完全权限”被省略号截断。
        Column(
            Modifier.fillMaxWidth().padding(top = TanukiSpace.SM_DP.dp),
            verticalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp),
        ) {
            if (secondary != null) {
                TanukiPillButton(
                    text = secondary,
                    onClick = onSecondary,
                    enabled = secondaryEnabled,
                    tone = TanukiPillButtonTone.Ghost,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TanukiPillButton(
                text = primary,
                onClick = onPrimary,
                enabled = primaryEnabled,
                tone = primaryTone,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(top = TanukiSpace.SM_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (secondary != null) {
            TanukiPillButton(
                text = secondary,
                onClick = onSecondary,
                enabled = secondaryEnabled,
                tone = TanukiPillButtonTone.Ghost,
            )
        }
        TanukiPillButton(text = primary, onClick = onPrimary, enabled = primaryEnabled, tone = primaryTone)
    }
}

/**
 * 保留最后一次非空值。
 *
 * 弹层收起时业务值会先变空；用最后一次快照保证退场动画期间标题和正文
 * 不会闪成空白。
 */
@Composable
private fun <T : Any> rememberLastNonNull(value: T?): T? {
    var last by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (value != null) last = value }
    return if (value != null) value else last
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
