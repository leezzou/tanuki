package com.tanuki.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.platform.LocalDensity
import com.tanuki.ui.design.TanukiDesign
import com.tanuki.ui.design.TanukiMotionEasing
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanuki.runtime.FileTransferProgress
import com.tanuki.storage.ConflictBatch
import com.tanuki.storage.FileLocation
import com.tanuki.storage.OpenDocument
import com.tanuki.storage.TransferConflictPolicy
import com.tanuki.storage.WorkspaceConflict
import com.tanuki.storage.WorkspaceDocument
import com.tanuki.storage.WorkspaceFileManager
import com.tanuki.storage.WorkspaceTransferMode
import com.tanuki.storage.WorkspaceTransferReport
import com.tanuki.ui.design.TanukiGlassPill
import com.tanuki.ui.design.TanukiMotion as TanukiMotionTokens
import com.tanuki.ui.design.TanukiPathText
import com.tanuki.ui.design.TanukiPillButton
import com.tanuki.ui.design.TanukiPillButtonTone
import com.tanuki.ui.design.TanukiShapeTokens
import com.tanuki.ui.design.TanukiSpace
import com.tanuki.ui.design.TanukiStatusChip
import com.tanuki.ui.design.TanukiStatusDot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** 页面单独预览时可用的空传输流；真实页面由宿主注入。 */
private val EMPTY_FILE_TRANSFERS: StateFlow<List<FileTransferProgress>> = MutableStateFlow(emptyList())

/** 导入完成后由宿主投递的一次性事件；用 id 去重，避免重组或旋转重复提示。 */
data class WorkspaceImportEvent(val id: String, val projectName: String, val fileCount: Int)

/** 手机存储到工作区的一次批量传输状态；决定冲突期间不会启动复制。 */
private data class PhoneTransfer(
    val mode: WorkspaceTransferMode,
    val entries: List<WorkspaceFileManager.Entry>,
    val target: String = "",
    val conflicts: List<WorkspaceConflict> = emptyList(),
    val decisions: Map<String, TransferConflictPolicy> = emptyMap(),
    val picking: Boolean = true,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WorkspaceFilesPage(
    manager: WorkspaceFileManager, onImport: () -> Unit, onExport: () -> Unit,
    modifier: Modifier = Modifier, runtimeRunning: Boolean = false, environmentBusy: Boolean = false,
    onStop: () -> Unit = {}, bottomInset: Dp = 0.dp,
    onRequestStoragePermission: () -> Unit = {}, phoneStorageGranted: Boolean = false,
    onExportFile: (WorkspaceFileManager, String, String) -> Unit = { _, _, _ -> },
    onOpenExternal: () -> Unit = {},
    onOpenFreeSpace: () -> Unit = {},
    externalDocument: OpenDocument? = null,
    onCloseExternal: () -> Unit = {},
    onCopyExternalToWorkspace: (OpenDocument) -> Unit = {},
    onExportExternal: (OpenDocument) -> Unit = {},
    onReselectExternal: (() -> Unit)? = null,
    fullscreen: Boolean = false, onFullscreen: (Boolean) -> Unit = {},
    phoneWriteAllowed: Boolean = false, phoneWritingAvailable: Boolean = false,
    onRequestPhoneWrite: () -> Unit = {}, onRevokePhoneWrite: () -> Unit = {},
    transfers: StateFlow<List<FileTransferProgress>> = EMPTY_FILE_TRANSFERS,
    onCancelTransfer: (Long) -> Unit = {},
    importEvent: WorkspaceImportEvent? = null,
    phoneMoveGranted: Boolean = false,
    onRequestPhoneMovePermission: () -> Unit = {},
    onTransferToWorkspace: suspend (
        List<String>,
        String,
        WorkspaceTransferMode,
        Map<String, TransferConflictPolicy>,
        (String) -> Unit,
    ) -> WorkspaceTransferReport = { _, _, _, _, _ -> error("文件传输尚未连接，请返回后重试") },
    previewMaxDimension: Int = PdfRenderPolicy.MAX_DIMENSION,
    previewMaxPixels: Int = PdfRenderPolicy.MAX_PIXELS,
) {
    var writePermissionDialog by remember { mutableStateOf(false) }
    var location by rememberSaveable { mutableStateOf(FileLocation.WORKSPACE) }
    val files = remember(manager, location) { manager.inLocation(location) }
    val canBrowse = location != FileLocation.PHONE || phoneStorageGranted
    val context = LocalContext.current
    // 偏好存储只保存当前文件的路径和显示位置，不保存文件内容。
    val readerStore = remember(context) { PreferencesReaderStateStore(context) }
    var directory by rememberSaveable(location) { mutableStateOf("") }
    var entries by remember(files) { mutableStateOf(emptyList<WorkspaceFileManager.Entry>()) }
    var selected by remember(files) { mutableStateOf(emptySet<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    // 阅读页不再依赖 files 对象：配置变更会重建 files，旧的 remember(files) 会把页面退回列表。
    var openedEntry by rememberSaveable(stateSaver = WorkspaceEntrySaver) {
        mutableStateOf<WorkspaceFileManager.Entry?>(null)
    }
    var viewerActionBarHeight by remember { mutableStateOf(FileViewerLayout.ActionBarHeight) }
    var readerView by rememberSaveable(stateSaver = ReaderViewStateSaver) {
        mutableStateOf(ReaderViewState())
    }
    var readerRestored by rememberSaveable { mutableStateOf(false) }
    // 异步恢复上次阅读不算用户主动导航；这里只记动效身份，不参与阅读状态保存。
    var restoredDetailKey by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<String?>(null) }
    var actionEntries by remember { mutableStateOf(emptyList<WorkspaceFileManager.Entry>()) }
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var draftOriginal by remember { mutableStateOf("") }
    var draftRevision by remember { mutableStateOf("") }
    var discard by remember { mutableStateOf(false) }
    var destinationPicker by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var locations by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var permissionSheet by remember { mutableStateOf(false) }
    var handledImportId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingOpenProject by rememberSaveable { mutableStateOf<String?>(null) }
    var recentImportName by rememberSaveable { mutableStateOf<String?>(null) }
    var feedbackTarget by remember { mutableStateOf<String?>(null) }
    var transferPhase by remember { mutableStateOf<String?>(null) }
    var transferStartedAt by remember { mutableLongStateOf(0L) }
    var phoneTransferActive by remember { mutableStateOf(false) }
    // 复制/移动结果按原因分类展示；同一结果重复出现时用版本号重新弹出提示。
    var operationOutcome by remember { mutableStateOf<FileOperationOutcome?>(null) }
    var outcomeVersion by remember { mutableIntStateOf(0) }
    var showOperationDetails by remember { mutableStateOf(false) }
    // 手机存储发起复制/移动时默认落到用户最近停留的工作区目录。
    var lastWorkspaceDirectory by rememberSaveable { mutableStateOf("") }
    var phoneTransfer by remember { mutableStateOf<PhoneTransfer?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val workspaceFiles = remember(manager) { manager.inLocation(FileLocation.WORKSPACE) }
    // chip 的只读/可写是“当前是否可操作”，DSH 运行或手机写入撤销都会立即反映。
    val access = remember(location, runtimeRunning, environmentBusy, phoneStorageGranted, phoneWriteAllowed, phoneWritingAvailable) {
        FileAccessPresentation.state(location, runtimeRunning, phoneStorageGranted, phoneWriteAllowed, phoneWritingAvailable, environmentBusy)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val searchFocus = remember { FocusRequester() }
    val notice = if (action == null) error ?: feedback else null
    fun handleOutcomeRecovery(recovery: FileOperationRecovery?) {
        when (recovery) {
            FileOperationRecovery.VIEW -> showOperationDetails = true
            FileOperationRecovery.FREE_SPACE -> onOpenFreeSpace()
            FileOperationRecovery.GRANT_PERMISSION -> permissionSheet = true
            null -> Unit
        }
    }
    fun publishOutcome(outcome: FileOperationOutcome) {
        feedback = null; error = null; feedbackTarget = null
        operationOutcome = outcome
        outcomeVersion++
    }
    LaunchedEffect(notice) {
        notice?.let {
            val target = if (error == null) feedbackTarget else null
            val result = snackbarHostState.showSnackbar(
                message = it,
                actionLabel = if (target != null) "打开" else null,
                withDismissAction = true,
                duration = if (error != null || target != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && target != null) {
                openedEntry = null; onCloseExternal(); onFullscreen(false)
                searching = false; query = ""; location = FileLocation.WORKSPACE
                pendingOpenProject = target
            }
            if (feedback == it) feedback = null
        }
    }
    LaunchedEffect(outcomeVersion) {
        if (outcomeVersion == 0) return@LaunchedEffect
        val outcome = operationOutcome ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = outcome.message,
            actionLabel = outcome.actionLabel,
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) handleOutcomeRecovery(outcome.recovery)
    }
    LaunchedEffect(searching) { if (searching) runCatching { searchFocus.requestFocus() } }
    fun report(e: Exception) {
        error = e.message?.takeIf { text -> text.any { it in '\u4e00'..'\u9fff' } }
            ?: "操作失败，请检查文件是否仍存在、空间是否充足，然后重试。"
    }
    fun runTask(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; feedback = null; feedbackTarget = null
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e) }
            finally { busy = false }
        }
    }
    fun navigate(path: String) = runTask {
        val loaded = files.list(path)
        directory = path; entries = loaded; selected = emptySet(); query = ""
    }
    fun requestImport() {
        if (environmentBusy) { feedbackTarget = null; error = null; feedback = FILE_INSTALL_WRITE_NOTICE } else onImport()
    }
    fun begin(kind: String, targets: List<WorkspaceFileManager.Entry> = emptyList()) {
        if (environmentBusy) { feedbackTarget = null; error = null; feedback = FILE_INSTALL_WRITE_NOTICE; menu = false; return }
        if (runtimeRunning) { error = "请先停止 DSH，再修改文件。浏览、导入和导出不受影响。"; menu = false; return }
        actionEntries = targets; name = targets.singleOrNull()?.name.orEmpty(); content = ""; draftOriginal = ""
        error = null; action = kind; menu = false
        destinationPicker = kind == "复制" || kind == "移动"
    }
    fun dismissEditor() {
        if (!busy) { if (content != draftOriginal && (action == "编辑文本" || action == "新建文本文件")) discard = true else action = null }
    }
    fun copyPath(path: String) { clipboard.setText(AnnotatedString(files.guestPath(path))); feedback = if (location == FileLocation.PHONE) "已复制手机工具路径" else "已复制 Agent 可用的路径" }
    fun toggle(path: String) { selected = if (path in selected) selected - path else selected + path }
    fun openReader(entry: WorkspaceFileManager.Entry) {
        restoredDetailKey = null
        // 重新打开同一路径才恢复旧偏移；打开新文件从默认位置开始。
        val saved = readerStore.load()?.takeIf { it.location == location && it.path == entry.path }
        readerView = saved?.view ?: ReaderViewState()
        openedEntry = entry
    }
    fun closeReader() {
        restoredDetailKey = null
        onFullscreen(false)
        openedEntry = null
        readerStore.clear()
    }
    // Bundle 恢复优先；进程被回收后从偏好存储恢复上次文件与位置。
    LaunchedEffect(Unit) {
        if (!readerRestored) {
            readerRestored = true
            if (openedEntry == null) {
                readerStore.load()?.takeIf { saved ->
                    saved.location == location && (saved.location != FileLocation.PHONE || phoneStorageGranted)
                }?.let { saved ->
                    restoredDetailKey = "workspace:${saved.location.name}:${saved.path}"
                    readerView = saved.view
                    openedEntry = WorkspaceFileManager.Entry(
                        path = saved.path,
                        name = saved.name,
                        directory = false,
                        bytes = 0L,
                        linked = saved.linked,
                    )
                }
            }
        }
    }
    // 刚打开文件时先落一次路径，之后滚动/编码/行号变化再更新偏移。
    LaunchedEffect(openedEntry?.path, readerView) {
        openedEntry?.let { entry ->
            readerStore.save(ReaderSessionState(entry.path, entry.name, entry.linked, location, readerView))
        }
    }
    // 先预检目标目录的同名冲突，用户逐项选择后再启动传输；取消或失败都不会产生半成品。
    fun startPhoneTransfer(draft: PhoneTransfer, scanConflicts: Boolean) {
        if (environmentBusy) {
            phoneTransfer = null; feedbackTarget = null; error = null; feedback = FILE_INSTALL_WRITE_NOTICE
            return
        }
        if (busy) return
        busy = true; error = null; feedback = null; feedbackTarget = null
        scope.launch {
            try {
                var current = draft
                if (scanConflicts) {
                    val conflicts = files.workspaceConflicts(draft.entries.map { it.path }, draft.target)
                    current = draft.copy(picking = false, conflicts = conflicts)
                    if (conflicts.isNotEmpty()) { phoneTransfer = current; return@launch }
                }
                phoneTransferActive = true
                transferStartedAt = SystemClock.elapsedRealtime()
                transferPhase = "正在准备传输"
                val outcome = onTransferToWorkspace(
                    current.entries.map { it.path }, current.target, current.mode, current.decisions,
                ) { phase -> scope.launch { if (phoneTransferActive) transferPhase = phase } }
                feedbackTarget = current.target.takeIf { outcome.results.any { !it.skipped && it.error == null } }
                selected = emptySet()
                entries = runCatching { files.list(directory) }.getOrDefault(entries)
                val result = FileOperationOutcome.fromTransfer(current.mode, outcome)
                if (result.failures.isEmpty()) feedback = "已${result.action} ${result.completed} 项到${result.target}"
                else publishOutcome(result)
                phoneTransfer = null
            } catch (e: CancellationException) {
                // 取消会清理暂存并保留源文件；列表回到稳定状态，不把取消当失败提示。
                withContext(NonCancellable) { runCatching { entries = files.list(directory) } }
                phoneTransfer = null
                feedback = "传输已取消；已完成项保留，未完成项的源文件保留。"
                throw e
            } catch (e: Exception) {
                phoneTransfer = null
                // 批次尚未开始就失败（如总量空间检查、权限）也要落到可行动的结果入口。
                val reason = FileOperationOutcome.classify(e)
                val action = if (draft.mode == WorkspaceTransferMode.MOVE) "移动" else "复制"
                val target = if (draft.target.isEmpty()) "工作区根目录" else "工作区「${draft.target}」"
                publishOutcome(FileOperationOutcome(
                    action, target, completed = 0, total = draft.entries.size,
                    failures = draft.entries.map { FileOperationFailure(it.name, reason, e.message) },
                ))
            } finally { busy = false; phoneTransferActive = false; transferPhase = null }
        }
    }
    fun decidePhoneConflict(
        draft: PhoneTransfer,
        conflict: WorkspaceConflict,
        policy: TransferConflictPolicy,
        applyAll: Boolean,
    ) {
        val names = draft.conflicts.map { it.name }
        val replaceable = draft.conflicts.filter { it.replaceable }.map { it.name }.toSet()
        val next = draft.copy(decisions = ConflictBatch.apply(
            draft.decisions, names, conflict.name, policy, applyAll, replaceable,
        ))
        if (ConflictBatch.next(names, next.decisions) == null) {
            phoneTransfer = null
            startPhoneTransfer(next, scanConflicts = false)
        } else phoneTransfer = next
    }
    LaunchedEffect(files, runtimeRunning, phoneStorageGranted, phoneMoveGranted, phoneTransferActive) {
        if (phoneTransferActive) return@LaunchedEffect
        selected = emptySet()
        if (!canBrowse) { entries = emptyList(); busy = false; return@LaunchedEffect }
        busy = true
        try { entries = files.list(directory) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { report(e) }
        finally { busy = false }
    }
    // 记住工作区当前位置，手机存储发起复制/移动时默认落到这里。
    LaunchedEffect(location, directory, entries) {
        if (location == FileLocation.WORKSPACE) lastWorkspaceDirectory = directory
    }
    // 导入完成：刷新列表并给出可操作的 Snackbar；点“打开”进入新项目目录。
    LaunchedEffect(importEvent?.id) {
        val event = importEvent ?: return@LaunchedEffect
        if (event.id == handledImportId) return@LaunchedEffect
        handledImportId = event.id
        recentImportName = event.projectName
        if (location != FileLocation.WORKSPACE) {
            location = FileLocation.WORKSPACE
            directory = ""
        } else {
            entries = runCatching { files.list(directory) }.getOrDefault(entries)
        }
        val result = snackbarHostState.showSnackbar(
            message = "已导入 ${event.fileCount} 个文件到「${event.projectName}」",
            actionLabel = "打开",
            withDismissAction = true,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            openedEntry = null; onCloseExternal(); onFullscreen(false)
            searching = false; query = ""; location = FileLocation.WORKSPACE
            pendingOpenProject = event.projectName
        }
    }
    LaunchedEffect(files, pendingOpenProject, busy) {
        val name = pendingOpenProject ?: return@LaunchedEffect
        // 列表刷新期间 runTask 会拒绝新任务；等 busy 结束后再执行“打开”，避免动作丢失。
        if (location != FileLocation.WORKSPACE || busy) return@LaunchedEffect
        pendingOpenProject = null
        recentImportName = null
        navigate(name)
    }
    BackHandler(enabled = action == null && openedEntry == null && (selected.isNotEmpty() || searching || directory.isNotEmpty())) {
        when { selected.isNotEmpty() -> selected = emptySet(); searching -> { searching = false; query = "" }
            !busy -> navigate(directory.substringBeforeLast('/', "")) }
    }
    // 每次路径或筛选变化生成独立请求，旧请求的结果不能作为新目录的显示内容。
    val displayRequest = remember(files, directory, entries, query, hidden, sort) {
        FileDisplayRequest(FileListPresentation.source(location, directory), entries, query, hidden, sort)
    }
    var display by remember(files) { mutableStateOf<FileDisplayResult?>(null) }
    val sorting = FileListPresentation.isStale(display, displayRequest)
    // 同目录刷新先保留旧列表，换目录才清空；避免排序期间闪空并重置滚动位置。
    val shown = FileListPresentation.visible(display, displayRequest)
    // 滚动状态随目录保存，排序、筛选和进度更新不会把列表拉回顶部；旋转后仍可恢复。
    val listState = rememberSaveable(files, directory, saver = LazyListState.Saver) { LazyListState() }
    val gridState = rememberSaveable(files, directory, saver = LazyGridState.Saver) { LazyGridState() }
    LaunchedEffect(displayRequest) {
        val prepared = withContext(Dispatchers.Default) { FileListPresentation.prepare(displayRequest) }
        currentCoroutineContext().ensureActive()
        display = FileListPresentation.result(display, FileDisplayResult(displayRequest, prepared))
    }
    if (writePermissionDialog) AlertDialog(onDismissRequest = { writePermissionDialog = false },
        title = { Text("允许助手写入手机文件？") },
        text = { Text("允许助手在本次运行中新建、修改、重命名和删除已授权的手机共享文件。\n拒绝后保持只读；可在文件页的权限入口修改，停止助手后自动恢复只读。") },
        dismissButton = { TextButton(onClick = { writePermissionDialog = false }) { Text("保持只读") } },
        confirmButton = { TextButton(onClick = { writePermissionDialog = false; onRequestPhoneWrite() }, enabled = phoneWritingAvailable) { Text("允许本次写入") } })
    // 页面状态与副作用仍在宿主之外；稳定详情 key 仅驱动单次页面过渡。
    val detailKey = externalDocument?.key ?: openedEntry?.let { "workspace:${location.name}:${it.path}" }
    TanukiPageTransitionHost(
        target = detailKey,
        navigation = { from, to -> tanukiDetailNavigation(from, to, restoring = to != null && to == restoredDetailKey) },
        modifier = modifier,
    ) { pageModifier ->
    externalDocument?.let { document ->
        Box(pageModifier.fillMaxSize()) {
            WorkspaceFileViewer(document = document, modifier = Modifier.fillMaxSize(), bottomInset = bottomInset,
                actionError = error, fullscreen = fullscreen, onFullscreen = onFullscreen,
                onActionBarHeightChanged = { viewerActionBarHeight = it },
                canEdit = false,
                previewMaxDimension = previewMaxDimension, previewMaxPixels = previewMaxPixels,
                onClose = onCloseExternal,
                onExport = { onExportExternal(document) },
                onEdit = {},
                onCopyToWorkspace = { if (environmentBusy) error = FILE_INSTALL_WRITE_NOTICE else onCopyExternalToWorkspace(document) },
                onReselect = onReselectExternal)
            // 查看器和列表共用同一个传输浮层；查看器操作栏可见时胶囊需额外上移避让。
            FileTransferOverlay(transfers = transfers, bottomInset = bottomInset, onCancelTransfer = onCancelTransfer,
                viewerActionBarVisible = !fullscreen, viewerActionBarHeight = viewerActionBarHeight, snackbarHostState = snackbarHostState)
        }
        return@TanukiPageTransitionHost
    }
    openedEntry?.let { entry ->
        val document = remember(files, entry.path) { WorkspaceDocument(files, entry.path, entry.name) }
        Box(pageModifier.fillMaxSize()) {
            WorkspaceFileViewer(document = document, modifier = Modifier.fillMaxSize(), bottomInset = bottomInset,
                actionError = error, fullscreen = fullscreen, onFullscreen = onFullscreen,
                onActionBarHeightChanged = { viewerActionBarHeight = it },
                canEdit = location.writable && !runtimeRunning && !entry.linked,
                previewMaxDimension = previewMaxDimension, previewMaxPixels = previewMaxPixels,
                initialReaderState = readerView,
                onReaderStateChange = { next ->
                    // 关闭阅读页后旧组合仍可能回调一次；不能让它把已清理的状态写回去。
                    if (openedEntry?.path == entry.path) {
                        readerView = next
                        // 直接落盘，避免 Activity 在防抖窗口内重建时只更新内存状态。
                        readerStore.save(ReaderSessionState(entry.path, entry.name, entry.linked, location, next))
                    }
                },
                onClose = { closeReader() },
                onExport = { onExportFile(files, entry.path, entry.name) },
                onEdit = { if (environmentBusy) error = FILE_INSTALL_WRITE_NOTICE else runTask {
                    val editable = files.preview(entry.path)
                    check(editable.text != null) { "此文件不能直接编辑，请使用其他应用打开。" }
                    begin("编辑文本", listOf(entry)); content = editable.text
                    draftOriginal = content; draftRevision = editable.revision.orEmpty(); closeReader()
                } })
            // 编辑动作会离开查看器；传输浮层保持在当前页面，并按操作栏高度避让。
            FileTransferOverlay(transfers = transfers, bottomInset = bottomInset, onCancelTransfer = onCancelTransfer,
                viewerActionBarVisible = !fullscreen, viewerActionBarHeight = viewerActionBarHeight, snackbarHostState = snackbarHostState)
        }
        return@TanukiPageTransitionHost
    }
    Surface(pageModifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().imePadding()) {
            Column(Modifier.fillMaxSize()) {
                // 顶部只保留位置、一级导入、更多和权限；低频浏览操作放进更多菜单。
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(start = 2.dp)) {
                        Surface(
                            onClick = { locations = true },
                            enabled = !busy,
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Row(
                                Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(location.title, Modifier.weight(1f), maxLines = 1,
                                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                            }
                        }
                        DropdownMenu(locations, { locations = false }) {
                            FileLocation.entries.forEach { area ->
                                DropdownMenuItem(
                                    text = { Text(area.title + if (!area.writable) " · 只读" else "") },
                                    onClick = { locations = false; directory = ""; query = ""; error = null; feedback = null; location = area },
                                )
                            }
                        }
                    }
                    TanukiPillButton(
                        text = "＋ 导入", onClick = ::requestImport, enabled = !busy,
                        tone = TanukiPillButtonTone.Filled,
                        modifier = Modifier.padding(horizontal = 4.dp).heightIn(min = 48.dp),
                    )
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.MoreVert, "文件操作")
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(if (searching) "关闭搜索" else "搜索当前文件夹") },
                                enabled = canBrowse, onClick = { menu = false; searching = !searching; query = "" })
                            DropdownMenuItem(text = { Text("刷新文件") }, enabled = canBrowse,
                                onClick = { menu = false; navigate(directory) })
                            DropdownMenuItem(text = { Text("排序与显示") },
                                onClick = { menu = false; sortMenu = true })
                            HorizontalDivider()
                            if (location.writable) {
                                listOf("新建文件夹", "新建文本文件").forEach { label ->
                                    DropdownMenuItem(text = { Text(label) }, enabled = !runtimeRunning,
                                        onClick = { begin(label) })
                                }
                                DropdownMenuItem(text = { Text("导出整个工作区") },
                                    onClick = { menu = false; onExport() })
                            }
                            DropdownMenuItem(text = { Text("打开手机/其他应用文件") },
                                onClick = { menu = false; onOpenExternal() })
                            DropdownMenuItem(text = {
                                Column {
                                    Text("导入为新项目")
                                    Text("会创建一个新文件夹，不覆盖现有项目", style = MaterialTheme.typography.bodySmall)
                                }
                            }, onClick = { menu = false; requestImport() })
                            DropdownMenuItem(text = { Text("复制当前目录路径") },
                                onClick = { menu = false; copyPath(directory) })
                        }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            listOf("名称", "最近修改", "文件大小").forEachIndexed { index, label ->
                                DropdownMenuItem(text = { Text((if (sort == index) "✓ " else "") + label) },
                                    onClick = { sort = index; sortMenu = false })
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(if (grid) "切换为列表" else "切换为网格") },
                                onClick = { grid = !grid; sortMenu = false })
                            DropdownMenuItem(text = { Text(if (hidden) "隐藏点号文件" else "显示隐藏文件") },
                                onClick = { hidden = !hidden; sortMenu = false })
                        }
                    }
                    PermissionChip(access, onClick = { permissionSheet = true })
                }
                recentImportName?.let { project ->
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("刚刚导入：$project", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = {
                            searching = false; query = ""; location = FileLocation.WORKSPACE
                            pendingOpenProject = project
                        }, enabled = !busy) { Text("打开") }
                        TextButton(onClick = { recentImportName = null }) { Text("忽略") }
                    }
                }
                transferPhase?.let { phase -> TransferStatusLine(phase, transferStartedAt) }
                // 第二行只承载一种上下文：批量选择优先于搜索，保证选中后操作可达且不丢过滤条件。
                when {
                    selected.isNotEmpty() -> Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, "取消选择") }
                        Text("已选 ${selected.size}", style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 4.dp))
                        if (query.isNotEmpty()) Text("筛选：$query", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                        TextButton(onClick = { selected = shown.map { it.path }.toSet() },
                            modifier = Modifier.heightIn(min = 48.dp)) { Text("全选") }
                        if (location.writable) listOf("复制", "移动", "删除").forEach { label ->
                            TextButton(
                                onClick = { begin(label, entries.filter { it.path in selected }) },
                                enabled = !busy && !runtimeRunning,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(label) }
                        }
                        if (location == FileLocation.PHONE) listOf(
                            "复制到工作区" to WorkspaceTransferMode.COPY,
                            "移动到工作区" to WorkspaceTransferMode.MOVE,
                        ).forEach { (label, mode) ->
                            TextButton(
                                onClick = {
                                    phoneTransfer = PhoneTransfer(
                                        mode,
                                        entries.filter { it.path in selected },
                                        target = lastWorkspaceDirectory,
                                    )
                                },
                                enabled = !busy && !runtimeRunning && selected.isNotEmpty() &&
                                    entries.filter { it.path in selected }.all {
                                        it.path.contains('/') && !it.linked &&
                                            (mode == WorkspaceTransferMode.COPY || !phoneMoveGranted || it.deletable)
                                    },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(label) }
                        }
                    }
                    searching -> Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f).focusRequester(searchFocus),
                            singleLine = true,
                            shape = MaterialTheme.shapes.extraLarge,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            placeholder = { Text("搜索当前文件夹") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                IconButton(onClick = { query = ""; searching = false }) {
                                    Icon(Icons.Default.Close, "关闭搜索")
                                }
                            },
                        )
                    }
                    else -> Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (directory.isNotEmpty()) IconButton(
                            onClick = { navigate(directory.substringBeforeLast('/', "")) }, enabled = !busy,
                        ) { TanukiIcon(TanukiGlyph.Back, "返回上一级") }
                        TextButton(onClick = { navigate("") }, enabled = !busy,
                            modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(location.guestPath, maxLines = 1)
                        }
                        val parts = directory.split('/').filter { it.isNotEmpty() }
                        parts.forEachIndexed { index, part ->
                            Text("/", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(
                                onClick = { navigate(parts.take(index + 1).joinToString("/")) },
                                enabled = !busy,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(part, maxLines = 1) }
                        }
                        TanukiAnimatedCount("${shown.size} 项", Modifier.padding(horizontal = 12.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val row: @Composable (WorkspaceFileManager.Entry, Boolean) -> Unit = { entry, tile ->
                    var expanded by remember(entry.path) { mutableStateOf(false) }
                    // 选中态切换属于反馈类动效：走 toggle 令牌，系统移除动画时立即变色。
                    val rowColor by animateColorAsState(
                        targetValue = if (entry.path in selected) MaterialTheme.colorScheme.primaryContainer
                            else if (tile) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.background,
                        animationSpec = tween(durationMillis = tanukiMotionMs(TanukiMotionTokens.toggle)),
                        label = "fileSelection",
                    )
                    Surface(
                        color = rowColor,
                        shape = if (tile) TanukiShapeTokens.card else TanukiShapeTokens.control,
                    ) {
                        Column(Modifier.fillMaxWidth().heightIn(min = TanukiFileVisuals.ROW_MIN_HEIGHT_DP.dp)
                            .combinedClickable(enabled = !busy,
                                onClick = { if (selected.isNotEmpty()) toggle(entry.path) else if (entry.directory) navigate(entry.path)
                                    else openReader(entry) },
                                onLongClick = { toggle(entry.path) }).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selected.isNotEmpty()) Checkbox(checked = entry.path in selected, onCheckedChange = { toggle(entry.path) })
                                else TanukiIcon(if (entry.directory) TanukiGlyph.Folder else TanukiGlyph.File, modifier = Modifier.size(if (tile) 32.dp else 24.dp),
                                    tint = if (entry.directory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!tile) {
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) { FileEntryLabel(entry, false) }
                                } else Spacer(Modifier.weight(1f))
                                Box {
                                    IconButton(onClick = { expanded = true }, enabled = !busy) { Icon(Icons.Default.MoreVert, "${entry.name}的操作") }
                                    DropdownMenu(expanded, { expanded = false }) {
                                        DropdownMenuItem(text = { Text(if (location == FileLocation.PHONE) "复制手机工具路径" else "复制 Agent 路径") }, onClick = { expanded = false; copyPath(entry.path) })
                                        if (!entry.directory) DropdownMenuItem(text = { Text("导出此文件") }, onClick = { expanded = false; onExportFile(files, entry.path, entry.name) })
                                        DropdownMenuItem(text = { Text("选择") }, onClick = { expanded = false; toggle(entry.path) })
                                        if (location.writable) listOf("重命名", "复制", "移动", "删除").forEach { label -> DropdownMenuItem(text = { Text(label) }, enabled = !runtimeRunning, onClick = { expanded = false; begin(label, listOf(entry)) }) }
                                        if (location == FileLocation.PHONE) listOf(
                                            "复制到工作区" to WorkspaceTransferMode.COPY,
                                            "移动到工作区" to WorkspaceTransferMode.MOVE,
                                        ).forEach { (label, mode) ->
                                            DropdownMenuItem(text = { Text(label) }, enabled = !runtimeRunning && entry.path.contains('/') && !entry.linked &&
                                                (mode == WorkspaceTransferMode.COPY || !phoneMoveGranted || entry.deletable), onClick = {
                                                expanded = false
                                                phoneTransfer = PhoneTransfer(mode, listOf(entry), target = lastWorkspaceDirectory)
                                            })
                                        }
                                    }
                                }
                            }
                            if (tile) FileEntryLabel(entry, true)
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (!canBrowse) {
                        // 未授权页保留原有说明与动作，只换成令牌字号和胶囊主操作。
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp + bottomInset),
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        ) {
                            TanukiIcon(TanukiGlyph.Folder, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("浏览手机文件", style = MaterialTheme.typography.titleLarge)
                            Text("用于浏览手机共享文件和存储卡，不会开放其他应用的私有目录。", style = MaterialTheme.typography.bodyMedium)
                            TanukiPillButton(
                                text = "允许访问文件",
                                onClick = onRequestStoragePermission,
                                tone = TanukiPillButtonTone.Filled,
                            )
                            Text("拒绝后仍可从系统文件选择器导入。\n可在系统设置中修改 Tanuki 的文件访问权限（较新系统为“所有文件访问权限”）。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        // 三态占位只在没有可显示内容时接管页面；列表上的操作失败仍走 Snackbar。
                        val stateKind = when {
                            busy && shown.isEmpty() && error == null -> TanukiStateKind.LOADING
                            error != null && entries.isEmpty() && !busy -> TanukiStateKind.ERROR
                            shown.isEmpty() && !busy && !sorting -> TanukiStateKind.EMPTY
                            else -> null
                        }
                        if (stateKind != null) {
                            // 状态切换属于反馈类动效；系统移除动画时立即切换。
                            Crossfade(
                                targetState = stateKind,
                                animationSpec = tween(durationMillis = tanukiMotionMs(TanukiMotionTokens.toggle)),
                                label = "fileState",
                            ) { kind ->
                                Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                                    FileListState(
                                        kind = kind,
                                        query = query,
                                        hasEntries = entries.isNotEmpty(),
                                        writable = location.writable,
                                        detail = error,
                                        bottomInset = bottomInset,
                                        onReload = { navigate(directory) },
                                        onShowHidden = { hidden = true },
                                        onClearQuery = { query = "" },
                                        onImport = ::requestImport,
                                        onCreateFolder = { begin("新建文件夹") },
                                        canCreateFolder = !runtimeRunning,
                                        transfers = transfers,
                                        onCancelTransfer = onCancelTransfer,
                                    )
                                }
                            }
                        } else if (grid) LazyVerticalGrid(
                            GridCells.Adaptive(160.dp),
                            Modifier.fillMaxSize(),
                            state = gridState,
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 72.dp + bottomInset),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            gridItems(shown, key = { it.path }) { entry -> row(entry, true) }
                        } else LazyColumn(
                            Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 72.dp + bottomInset),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(shown, key = { it.path }) { entry -> row(entry, false) }
                        }
                    }
                    // 列表加载/排序只叠加 2dp 指示线，不占布局高度，也不会推动列表。
                    if (busy || sorting) LinearProgressIndicator(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(2.dp),
                    )
                }
            }
            // 传输流只在 overlay 内订阅；进度帧不会重组列表或改变列表测量。
            FileTransferOverlay(
                transfers = transfers,
                bottomInset = bottomInset,
                onCancelTransfer = onCancelTransfer,
                snackbarHostState = snackbarHostState,
            )
        }
    }
    }
    // 保持查看器原来的提前返回语义，不让列表弹窗在阅读页背后出现。
    if (externalDocument != null || openedEntry != null) return
    operationOutcome?.takeIf { showOperationDetails }?.let { outcome ->
        FileOperationResultDialog(
            outcome = outcome,
            onDismiss = { showOperationDetails = false },
            onRecovery = { recovery ->
                showOperationDetails = false
                handleOutcomeRecovery(recovery)
            },
        )
    }
    if (permissionSheet) PermissionSheet(
        state = access,
        location = location,
        runtimeRunning = runtimeRunning,
        phoneStorageGranted = phoneStorageGranted,
        phoneWritingAvailable = phoneWritingAvailable,
        onDismiss = { permissionSheet = false },
        onRequestStorage = { permissionSheet = false; onRequestStoragePermission() },
        onRequestWrite = { permissionSheet = false; writePermissionDialog = true },
        onRevokeWrite = { permissionSheet = false; onRevokePhoneWrite() },
        onStopDsh = { permissionSheet = false; onStop() },
    )
    if (destinationPicker) DirectoryPicker(
        target = files,
        operation = action.orEmpty(),
        initial = directory,
        sources = actionEntries.map { it.path },
        busy = busy,
        destinationLabel = "文件夹",
        onDismiss = { if (!busy) { destinationPicker = false; action = null } },
        onChoose = { target ->
            runTask {
                val operation = action.orEmpty()
                val total = actionEntries.size
                val failures = mutableListOf<FileOperationFailure>()
                var completed = 0
                for (entry in actionEntries) {
                    try {
                        if (operation == "复制") files.copy(entry.path, target) else files.move(entry.path, target)
                        completed++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 保留真实异常用于分类；未知原因仍能在详情里看到具体信息。
                        failures += FileOperationFailure(entry.name, FileOperationOutcome.classify(e), e.message)
                    }
                }
                val targetLabel = if (target.isEmpty()) "当前位置" else files.guestPath(target)
                destinationPicker = false; action = null; selected = emptySet(); entries = files.list(directory)
                if (failures.isEmpty()) feedback = "已完成 $completed 项"
                else publishOutcome(FileOperationOutcome(operation, targetLabel, completed, total, failures))
            }
        })
    else action?.let { kind ->
        AlertDialog(onDismissRequest = ::dismissEditor, title = { Text(if (kind == "编辑文本") "编辑 ${actionEntries.single().name}" else kind) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (kind == "删除") Text("删除 ${actionEntries.size} 项${if (actionEntries.any { it.directory }) "及其中的内容" else ""}？无法撤销；链接只删除条目本身。\n\n${actionEntries.take(6).joinToString("\n") { it.name }}")
                else {
                    if (kind != "编辑文本") OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
                    if (kind == "新建文本文件" || kind == "编辑文本") OutlinedTextField(content, { if (it.length <= WorkspaceFileManager.TEXT_LIMIT) content = it },
                        label = { Text("UTF-8 文本 · 最多 256 KiB") }, textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        enabled = !busy, minLines = 5, maxLines = 14, modifier = Modifier.fillMaxWidth())
                    Text(if (kind == "编辑文本") "保存前会检查原文件是否被修改，避免覆盖他人的更改。" else "同名文件不会被覆盖。", style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, dismissButton = { TextButton(onClick = ::dismissEditor, enabled = !busy) { Text("取消") } }, confirmButton = {
            TextButton(enabled = !busy && !runtimeRunning && (kind == "删除" || kind == "编辑文本" || name.isNotBlank()), onClick = { runTask {
                when (kind) {
                    "新建文件夹" -> files.createFolder(directory, name)
                    "新建文本文件" -> files.createText(directory, name, content)
                    "编辑文本" -> files.saveText(actionEntries.single().path, content, draftRevision)
                    "重命名" -> files.rename(actionEntries.single().path, name)
                    "删除" -> {
                        var completed = 0
                        try { for (entry in actionEntries) { files.delete(entry.path); completed++ } }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) {
                            action = null; entries = files.list(directory); selected = emptySet()
                            throw IllegalStateException("已删除 $completed 项；后续删除未完成，请刷新检查。${e.message.orEmpty()}")
                        }
                    }
                }
                action = null; selected = emptySet(); entries = files.list(directory); feedback = if (kind == "编辑文本") "已保存" else "操作完成"
            } }) { Text(if (busy) "处理中…" else if (kind == "删除") "确认删除" else "保存") }
        })
    }
    // 手机存储到工作区：先选目标目录，再逐项处理同名冲突，最后才启动复制/移动。
    phoneTransfer?.takeIf { it.picking }?.let { draft ->
        if (draft.mode == WorkspaceTransferMode.MOVE && !phoneMoveGranted) AlertDialog(
            onDismissRequest = { phoneTransfer = null },
            title = { Text("移动需要手机存储写权限") },
            text = { Text("移动会在工作区副本复制并校验成功后删除手机源文件。请先授予系统写权限；这不会授权 DSH 修改手机文件。") },
            dismissButton = { TextButton(onClick = { phoneTransfer = null }) { Text("取消") } },
            confirmButton = { TextButton(onClick = onRequestPhoneMovePermission) { Text("授权手机存储写入") } },
        ) else DirectoryPicker(
            target = workspaceFiles,
            operation = if (draft.mode == WorkspaceTransferMode.MOVE) "移动" else "复制",
            initial = draft.target,
            sources = emptyList(),
            busy = busy,
            destinationLabel = "工作区",
            onDismiss = { if (!busy) phoneTransfer = null },
            onChoose = { target ->
                val next = draft.copy(target = target, picking = false)
                phoneTransfer = null
                startPhoneTransfer(next, scanConflicts = true)
            },
        )
    }
    phoneTransfer?.takeIf { !it.picking }?.let { draft ->
        val conflict = draft.conflicts.firstOrNull { it.name !in draft.decisions }
        if (conflict != null) ConflictDialog(
            conflict = conflict,
            remaining = draft.conflicts.count { it.name !in draft.decisions },
            busy = busy,
            onChoose = { policy, applyAll -> decidePhoneConflict(draft, conflict, policy, applyAll) },
            onDismiss = { if (!busy) phoneTransfer = null },
        )
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存的内容？") },
        text = { Text("原文件没有改变，当前草稿将丢失。") }, dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } },
        confirmButton = { TextButton(onClick = { discard = false; action = null }) { Text("放弃草稿") } })
}

/**
 * 文件页的三态占位。
 *
 * 空态按“搜索无结果 / 只有隐藏文件 / 目录为空”给出不同标题和真实动作；
 * 错误态只转述真实原因并提供重试；加载态只说明等待，不伪造进度。
 */
@Composable
private fun FileListState(
    kind: TanukiStateKind,
    query: String,
    hasEntries: Boolean,
    writable: Boolean,
    detail: String?,
    bottomInset: Dp,
    onReload: () -> Unit,
    onShowHidden: () -> Unit,
    onClearQuery: () -> Unit,
    onImport: () -> Unit,
    onCreateFolder: () -> Unit,
    canCreateFolder: Boolean,
    transfers: StateFlow<List<FileTransferProgress>>,
    onCancelTransfer: (Long) -> Unit,
) {
    val modifier = Modifier.fillMaxWidth()
        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp + bottomInset)
    val copy = TanukiFileVisuals.emptyCopy(query = query, hasEntries = hasEntries, writable = writable)
    when (kind) {
        TanukiStateKind.LOADING -> TanukiStateView(
            kind = kind,
            modifier = modifier,
            subject = "文件",
            detail = "正在读取当前文件夹，请稍候。",
        )
        TanukiStateKind.ERROR -> TanukiStateView(
            kind = kind,
            modifier = modifier,
            subject = "文件",
            title = "文件列表加载失败",
            detail = detail ?: "请检查文件是否仍存在、空间是否充足，然后重试。",
            actionLabel = "重试",
            onAction = onReload,
        )
        TanukiStateKind.EMPTY -> {
            // 只在空态内订阅导入进度；main 的完成事件继续负责刷新列表与“打开”反馈。
            val transferItems by transfers.collectAsState()
            val importItems = FileTransferPresentation.imports(transferItems)
            var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
            LaunchedEffect(importItems.isNotEmpty()) {
                while (importItems.isNotEmpty()) {
                    delay(1000)
                    now = SystemClock.elapsedRealtime()
                }
            }
            if (importItems.isNotEmpty()) {
                TanukiStateView(
                    kind = TanukiStateKind.LOADING,
                    modifier = modifier,
                    title = "正在导入文件",
                    detail = "文件正在复制到 /workspaces，完成后会刷新文件列表。",
                ) {
                    importItems.forEach { item ->
                        key(item.id) {
                            TransferRow(item = item, now = now, onCancel = { onCancelTransfer(item.id) })
                        }
                    }
                }
            } else {
                val actions = TanukiFileVisuals.emptyActions(query, hasEntries, writable)
                TanukiStateView(
                    kind = kind,
                    modifier = modifier,
                    subject = "文件",
                    title = copy.title,
                    detail = if (actions.primary == FileEmptyCommand.IMPORT)
                        "导入文件会按源文件名创建新项目，不覆盖已有项目。" else copy.detail,
                    icon = Icons.Default.FolderOpen,
                    actionLabel = actions.primaryLabel,
                    actionFilled = actions.primary == FileEmptyCommand.IMPORT,
                    onAction = {
                        dispatchFileEmptyCommand(actions.primary, onImport, onCreateFolder,
                            onClearQuery, onShowHidden, onReload)
                    },
                    secondaryActionLabel = actions.secondaryLabel,
                    secondaryAsButton = actions.secondary != null,
                    secondaryActionEnabled = canCreateFolder,
                    onSecondaryAction = actions.secondary?.let { secondary ->
                        {
                            dispatchFileEmptyCommand(secondary, onImport, onCreateFolder,
                                onClearQuery, onShowHidden, onReload)
                        }
                    },
                )
            }
        }
    }
}

/** 阶段与耗时独立重组；未知大小不显示百分比。 */
@Composable
private fun TransferStatusLine(phase: String, startedAt: Long) {
    var elapsed by remember(startedAt) { mutableLongStateOf(0L) }
    LaunchedEffect(startedAt) {
        while (true) {
            elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0) / 1000
            delay(1000)
        }
    }
    Text("$phase · 已用 $elapsed 秒", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 传输浮层独立订阅进度；列表页只接收稳定布局，不参与每个进度帧的重组。 */
@Composable
internal fun FileTransferOverlay(
    transfers: StateFlow<List<FileTransferProgress>>,
    bottomInset: Dp,
    onCancelTransfer: (Long) -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    viewerActionBarVisible: Boolean = false,
    viewerActionBarHeight: Dp = FileViewerLayout.ActionBarHeight,
) {
    val items by transfers.collectAsState()
    val summary = FileTransferPresentation.summary(items)
    val runtimeOverlay = LocalFileOperationOverlay.current
    val hasProgress = runtimeOverlay != null || summary != null
    val progressBottom = FileViewerLayout.transferPillBottomPadding(bottomInset, viewerActionBarVisible, viewerActionBarHeight)
    var sheet by remember { mutableStateOf(false) }
    LaunchedEffect(runtimeOverlay != null) {
        if (runtimeOverlay != null) sheet = false
    }
    val motion = TanukiDesign.motion
    val shift = with(LocalDensity.current) { 8.dp.roundToPx() }
    var lastSummary by remember { mutableStateOf(summary) }
    var lastIdentity by remember { mutableStateOf(FileTransferPresentation.motionIdentity(items)) }
    SideEffect {
        if (summary != null) {
            lastSummary = summary
            lastIdentity = FileTransferPresentation.motionIdentity(items)
        }
    }
    val pillVisible = runtimeOverlay == null && summary != null
    Box(Modifier.fillMaxSize().imePadding()) {
        // 一次只放一个入口；运行时抽屉也包含文件任务，保留同一个导入/复制结果提示宿主。
        if (runtimeOverlay != null) {
            runtimeOverlay(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).padding(bottom = progressBottom))
        }
        val pillModifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = progressBottom)
        val pillContent: @Composable () -> Unit = {
            (summary ?: lastSummary)?.takeIf { runtimeOverlay == null }?.let {
                TransferPill(summary = it, identity = if (summary != null) FileTransferPresentation.motionIdentity(items) else lastIdentity,
                    enabled = pillVisible, onOpen = { if (pillVisible) sheet = true },
                    onCancel = { if (pillVisible) items.forEach { item -> onCancelTransfer(item.id) } })
            }
        }
        if (motion.reducedMotion) {
            if (pillVisible) Box(pillModifier) { pillContent() }
        } else AnimatedVisibility(
            visible = pillVisible,
            modifier = pillModifier,
            enter = fadeIn(motion.state()) + slideInVertically(
                tween(motion.duration(TanukiMotionTokens.layout), easing = TanukiMotionEasing.standard)) { shift },
            exit = fadeOut(motion.state()) + slideOutVertically(
                tween(motion.duration(TanukiMotionTokens.layout), easing = TanukiMotionEasing.standard)) { shift },
        ) { pillContent() }
        snackbarHostState?.let { host ->
            SnackbarHost(host, Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp,
                bottom = if (hasProgress) progressBottom + FileViewerLayout.TransferPillHeight + FileViewerLayout.FloatingGap
                    else bottomInset + 16.dp))
        }
    }
    if (sheet && runtimeOverlay == null) {
        TransferSheet(transfers = items, onDismiss = { sheet = false }, onCancel = onCancelTransfer)
    }
}

/**
 * 权限 chip。
 *
 * 复用状态 chip 的容器色/状态点和 48dp 触控区；宽度上限保持原来的 76dp，
 * 避免在窄屏上挤压左侧位置选择器。语气由 [TanukiFileVisuals.permissionTone]
 * 统一映射，页面不直接选颜色。
 */
@Composable
private fun PermissionChip(
    state: FileAccessState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TanukiStatusChip(
        tone = TanukiFileVisuals.permissionTone(state),
        label = state.label,
        onClick = onClick,
        contentDescription = TanukiFileVisuals.permissionDescription(state.label),
        modifier = modifier
            .height(TanukiSpace.TOUCH_TARGET_MIN_DP.dp)
            .widthIn(min = 56.dp, max = 76.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionSheet(
    state: FileAccessState,
    location: FileLocation,
    runtimeRunning: Boolean,
    phoneStorageGranted: Boolean,
    phoneWritingAvailable: Boolean,
    onDismiss: () -> Unit,
    onRequestStorage: () -> Unit,
    onRequestWrite: () -> Unit,
    onRevokeWrite: () -> Unit,
    onStopDsh: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("权限与写入", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭权限说明") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 与权限 chip 共用同一语气，避免弹层和入口显示不同状态色。
                TanukiStatusDot(
                    tone = TanukiFileVisuals.permissionTone(state),
                    diameter = 10.dp,
                    decorative = true,
                )
                Spacer(Modifier.width(8.dp))
                Text("当前：${state.label}", style = MaterialTheme.typography.titleMedium)
            }
            Text(state.summary, style = MaterialTheme.typography.titleSmall)
            Text(state.detail, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                state.needsStorageGrant -> TanukiPillButton(
                    text = "允许访问文件",
                    onClick = onRequestStorage,
                    tone = TanukiPillButtonTone.Filled,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.canGrantPhoneWrite -> TanukiPillButton(
                    text = "允许本次写入",
                    onClick = onRequestWrite,
                    enabled = phoneWritingAvailable,
                    tone = TanukiPillButtonTone.Filled,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.canRevokePhoneWrite -> TanukiPillButton(
                    text = "撤销助手写入",
                    onClick = onRevokeWrite,
                    tone = TanukiPillButtonTone.Tonal,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (location == FileLocation.PHONE && phoneStorageGranted &&
                !state.canGrantPhoneWrite && !state.canRevokePhoneWrite && !state.needsStorageGrant) {
                Text("写入仅本次运行有效；停止助手后恢复只读。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (location == FileLocation.PHONE) {
                Text("文件页对手机存储只读；如需放入工作区，请使用条目的「复制到工作区」或「移动到工作区」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.canStopRuntime) {
                HorizontalDivider()
                Text("停止助手后可修改工作区文件；助手当前任务会中断。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TanukiPillButton(
                    text = "停止助手",
                    onClick = onStopDsh,
                    tone = TanukiPillButtonTone.Outline,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (location == FileLocation.WORKSPACE && !runtimeRunning) {
                Text("手机存储的写入授权在该位置的权限 chip 中单独管理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * 传输胶囊。
 *
 * 用玻璃胶囊层承载，保留原来的进度环、百分比文案和取消动作；没有背板时
 * 组件只画半透明表面，不会假装已经模糊。无障碍描述保持“文件传输…”。
 */
@Composable
private fun TransferPill(
    summary: FileTransferSummary,
    identity: Any?,
    enabled: Boolean,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val percent = summary.percent
    val fraction = if (summary.allTotalsKnown) FileTransferPresentation.fraction(summary.received, summary.total) else null
    val animated by rememberFileTransferFraction(identity, fraction)
    val reduced = TanukiDesign.motion.reducedMotion
    val label = TanukiFileVisuals.transferLabel(percent)
    TanukiGlassPill(
        modifier = modifier
            .widthIn(max = 190.dp)
            .clickable(enabled = enabled, onClick = onOpen)
            .semantics { contentDescription = "文件传输$label，共 ${summary.count} 项，点击查看详情" },
        contentPadding = PaddingValues(start = TanukiSpace.MD_DP.dp, end = 0.dp),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (percent != null) CircularProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 2.5.dp,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            ) else if (reduced) Icon(Icons.Default.Info, "总大小未知，正在传输", Modifier.fillMaxSize())
            else CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 2.5.dp,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            )
        }
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
        if (summary.count > 1) {
            Text("· ${summary.count}", maxLines = 1, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onCancel, enabled = enabled, modifier = Modifier.size(TanukiSpace.TOUCH_TARGET_MIN_DP.dp)) {
            Icon(Icons.Default.Close,
                if (summary.count > 1) "取消全部文件传输" else "取消文件传输",
                Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferSheet(
    transfers: List<FileTransferProgress>,
    onDismiss: () -> Unit,
    onCancel: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var lastTransfers by remember { mutableStateOf(transfers) }
    SideEffect { if (transfers.isNotEmpty()) lastTransfers = transfers }
    val displayed = transfers.ifEmpty { lastTransfers }
    fun dismiss() { scope.launch { sheetState.hide(); onDismiss() } }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    // sheet 打开期间每秒刷新剩余时间；浮层本身只随真实字节进度重组。
    LaunchedEffect(transfers.size) {
        while (transfers.isNotEmpty()) {
            delay(1000)
            now = SystemClock.elapsedRealtime()
        }
    }
    LaunchedEffect(transfers.isEmpty()) {
        if (transfers.isEmpty()) { sheetState.hide(); onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("传输进度", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = ::dismiss) { Icon(Icons.Default.Close, "关闭传输详情") }
            }
            FileTransferPresentation.summary(displayed)?.let { summary ->
                Text(
                    if (summary.percent != null) "总进度 ${summary.percent}% · ${summary.count} 个文件任务"
                    else "${summary.count} 个文件任务 · 总大小未知",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            displayed.forEach { item ->
                key(item.id) {
                    TransferRow(item = item, now = now, onCancel = { if (transfers.any { it.id == item.id }) onCancel(item.id) },
                        enabled = transfers.any { it.id == item.id })
                }
            }
            Text("进度按实际复制或下载的字节计算；总大小未知时显示不定进度，不估造百分比。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun TransferRow(
    item: FileTransferProgress,
    now: Long,
    onCancel: () -> Unit,
    enabled: Boolean = true,
) {
    val percent = FileTransferPresentation.percent(item.received, item.total)
    val fraction = FileTransferPresentation.fraction(item.received, item.total)
    val animated by rememberFileTransferFraction(item.id to item.label, fraction)
    val reduced = TanukiDesign.motion.reducedMotion
    val progressModifier = Modifier.fillMaxWidth().semantics {
        contentDescription = "${item.label}的传输进度"
        stateDescription = percent?.let { "已完成 $it%" } ?: "总大小未知，进度无法估算"
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.label, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall)
            Text(percent?.let { "$it%" } ?: "进行中", maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // 只平滑图形显示；百分比与可访问状态仍来自真实传输字节。
        if (fraction != null) LinearProgressIndicator(progress = { animated }, modifier = progressModifier)
        else if (reduced) Text("总大小未知 · 正在传输", progressModifier, style = MaterialTheme.typography.labelSmall)
        else LinearProgressIndicator(modifier = progressModifier)
        Text(FileTransferPresentation.sizeText(item.received, item.total),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(FileTransferPresentation.remainingText(item, now),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TanukiPillButton(
            text = "取消此文件",
            onClick = onCancel,
            enabled = enabled,
            tone = TanukiPillButtonTone.Outline,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    HorizontalDivider()
}

@Composable
private fun FileEntryLabel(entry: WorkspaceFileManager.Entry, tile: Boolean) {
    Text(entry.name, maxLines = if (tile) 2 else 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
    Text((if (entry.directory) "文件夹" else (if (entry.linked) "链接 · " else "") + fileSize(entry.bytes)) +
        if (!tile && entry.modified > 0) " · " + DateFormat.getDateInstance(DateFormat.SHORT).format(Date(entry.modified)) else "",
        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun DirectoryPicker(
    target: WorkspaceFileManager,
    operation: String,
    initial: String,
    sources: List<String>,
    busy: Boolean,
    destinationLabel: String,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
) {
    var current by remember { mutableStateOf(initial) }
    var folders by remember { mutableStateOf(emptyList<WorkspaceFileManager.Entry>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(current) {
        loading = true; error = null
        try { folders = target.list(current).filter { it.directory && sources.none { path -> it.path == path || it.path.startsWith("$path/") } } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { folders = emptyList(); error = "无法打开此目录，请返回上一级" }
        finally { loading = false }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("${operation}到$destinationLabel") }, text = {
        Column(Modifier.heightIn(max = 400.dp)) {
            TanukiPathText(
                if (target.location == FileLocation.WORKSPACE) {
                    if (current.isEmpty()) "工作区 / 根目录" else "工作区 / $current"
                } else target.guestPath(current),
            )
            if (current.isNotEmpty()) TextButton(onClick = { current = current.substringBeforeLast('/', "") }, enabled = !loading && !busy) { Text("返回上一级") }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(folders, key = { it.path }) { folder -> TextButton(onClick = { current = folder.path }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), contentPadding = PaddingValues(12.dp)) {
                    TanukiIcon(TanukiGlyph.Folder); Spacer(Modifier.width(12.dp)); Text(folder.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TanukiIcon(TanukiGlyph.Chevron)
                } }
                if (!loading && folders.isEmpty() && error == null) item { Text("没有可进入的子文件夹", Modifier.padding(vertical = 16.dp)) }
            }
            Text(if (target.location == FileLocation.WORKSPACE && sources.isEmpty()) {
                if (operation == "移动") "复制并校验成功后删除手机源文件；同名文件会先询问如何处理。"
                else "同名文件会先询问保留两者、替换或跳过。"
            } else "选择当前位置；同名条目不会覆盖。", style = MaterialTheme.typography.bodySmall)
        }
    }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } }, confirmButton = {
        TextButton(onClick = { onChoose(current) }, enabled = !loading && !busy && error == null && sources.none { current == it || current.startsWith("$it/") }) { Text(if (busy) "处理中…" else "${operation}到这里") }
    })
}

/** 同名冲突三选；目录不能整体替换，避免误删已有内容。 */
@Composable
private fun ConflictDialog(
    conflict: WorkspaceConflict,
    remaining: Int,
    busy: Boolean,
    onChoose: (TransferConflictPolicy, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var policy by remember(conflict.name) { mutableStateOf(TransferConflictPolicy.KEEP_BOTH) }
    var applyAll by remember(conflict.name) { mutableStateOf(false) }
    val options = if (!conflict.replaceable) listOf(
        TransferConflictPolicy.KEEP_BOTH to "保留两者（新文件夹自动改名）",
        TransferConflictPolicy.SKIP to "跳过",
    ) else listOf(
        TransferConflictPolicy.KEEP_BOTH to "保留两者（新文件自动改名）",
        TransferConflictPolicy.REPLACE to "替换同名文件",
        TransferConflictPolicy.SKIP to "跳过",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("已有同名${if (conflict.directory) "文件夹" else "文件"}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "工作区中已存在「${conflict.name}」。" +
                        if (remaining > 1) "还有 ${remaining - 1} 个冲突。" else "",
                )
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                            selected = policy == value, enabled = !busy, role = Role.RadioButton,
                        ) { policy = value },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = policy == value, onClick = null, enabled = !busy)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
                if (!conflict.replaceable) {
                    Text("只有同类型的普通文件可替换；文件夹或链接请选择保留两者或跳过。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                    value = applyAll, enabled = !busy, role = Role.Checkbox,
                ) { applyAll = it }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = applyAll, onCheckedChange = null, enabled = !busy)
                    Spacer(Modifier.width(8.dp))
                    Text("全部应用到这个批次")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } },
        confirmButton = {
            TextButton(onClick = { onChoose(policy, applyAll) }, enabled = !busy) { Text("继续") }
        },
    )
}

/**
 * 复制/移动结果弹层：按原因分组列出文件名。
 *
 * 同名组只说明未覆盖；空间不足和权限组就地给出恢复入口，混合原因也能逐组处理，
 * 不再让用户面对一句“检查是否同名、链接或空间不足”。
 */
@Composable
private fun FileOperationResultDialog(
    outcome: FileOperationOutcome,
    onDismiss: () -> Unit,
    onRecovery: (FileOperationRecovery) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${outcome.action}结果") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(outcome.summary, style = MaterialTheme.typography.titleSmall)
                Text("目标：${outcome.target}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                outcome.groups.forEach { group ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("${group.reason.title}（${group.failures.size} 个）", style = MaterialTheme.typography.titleSmall)
                    when (group.reason) {
                        FileOperationReason.DUPLICATE -> Text("同名文件已跳过，原文件没有被覆盖。",
                            style = MaterialTheme.typography.bodySmall)
                        FileOperationReason.NO_SPACE -> Text("清理空间后可以重新选择这些文件。",
                            style = MaterialTheme.typography.bodySmall)
                        FileOperationReason.PERMISSION -> Text("授权后再试；原文件保持未处理。",
                            style = MaterialTheme.typography.bodySmall)
                        FileOperationReason.OTHER -> Text("下面是系统返回的具体原因。",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    group.failures.take(12).forEach { failure ->
                        val detail = failure.shortDetail.takeIf { group.reason == FileOperationReason.OTHER }
                        Text(
                            "· ${failure.name}" + detail?.let { "：$it" }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (group.failures.size > 12) Text("…另有 ${group.failures.size - 12} 个",
                        style = MaterialTheme.typography.bodySmall)
                    if (group.reason == FileOperationReason.NO_SPACE || group.reason == FileOperationReason.PERMISSION) {
                        TextButton(onClick = { onRecovery(group.recovery) }) { Text(group.actionLabel) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** 文件大小格式化委托给可 JVM 回归的纯函数。 */
private fun fileSize(bytes: Long): String = TanukiFileVisuals.sizeText(bytes)
