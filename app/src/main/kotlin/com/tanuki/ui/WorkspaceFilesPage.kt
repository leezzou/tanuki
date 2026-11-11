package com.tanuki.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tanuki.runtime.FileTransferProgress
import com.tanuki.storage.FileLocation
import com.tanuki.storage.OpenDocument
import com.tanuki.storage.WorkspaceDocument
import com.tanuki.storage.WorkspaceFileManager
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WorkspaceFilesPage(
    manager: WorkspaceFileManager, onImport: () -> Unit, onExport: () -> Unit,
    modifier: Modifier = Modifier, runtimeRunning: Boolean = false, onStop: () -> Unit = {}, bottomInset: Dp = 0.dp,
    onRequestStoragePermission: () -> Unit = {}, phoneStorageGranted: Boolean = false,
    onExportFile: (WorkspaceFileManager, String, String) -> Unit = { _, _, _ -> },
    onOpenExternal: () -> Unit = {},
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
    previewMaxDimension: Int = PdfRenderPolicy.MAX_DIMENSION,
    previewMaxPixels: Int = PdfRenderPolicy.MAX_PIXELS,
) {
    var writePermissionDialog by remember { mutableStateOf(false) }
    var location by rememberSaveable { mutableStateOf(FileLocation.WORKSPACE) }
    val files = remember(manager, location) { manager.inLocation(location) }
    val canBrowse = location != FileLocation.PHONE || phoneStorageGranted
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
    var openedEntry by remember(files) { mutableStateOf<WorkspaceFileManager.Entry?>(null) }
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
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    // chip 的只读/可写是“当前是否可操作”，DSH 运行或手机写入撤销都会立即反映。
    val access = remember(location, runtimeRunning, phoneStorageGranted, phoneWriteAllowed, phoneWritingAvailable) {
        FileAccessPresentation.state(location, runtimeRunning, phoneStorageGranted, phoneWriteAllowed, phoneWritingAvailable)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val searchFocus = remember { FocusRequester() }
    val notice = if (action == null) error ?: feedback else null
    LaunchedEffect(notice) {
        notice?.let {
            snackbarHostState.showSnackbar(
                message = it,
                withDismissAction = true,
                duration = if (error != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
        }
    }
    LaunchedEffect(searching) { if (searching) runCatching { searchFocus.requestFocus() } }
    fun report(e: Exception) {
        error = e.message?.takeIf { text -> text.any { it in '\u4e00'..'\u9fff' } }
            ?: "操作失败，请检查文件是否仍存在、空间是否充足，然后重试。"
    }
    fun runTask(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; feedback = null
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
    fun begin(kind: String, targets: List<WorkspaceFileManager.Entry> = emptyList()) {
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
    LaunchedEffect(files, runtimeRunning, phoneStorageGranted) {
        error = null; selected = emptySet()
        if (!canBrowse) { entries = emptyList(); busy = false; return@LaunchedEffect }
        busy = true
        try { entries = files.list(directory) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { report(e) }
        finally { busy = false }
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
        title = { Text("允许 DSH 写入手机文件？") },
        text = { Text("允许本次 DSH 运行期间新建、修改、重命名和删除系统已授权的手机共享文件。可以随时撤销，停止 DSH 后自动恢复只读。") },
        dismissButton = { TextButton(onClick = { writePermissionDialog = false }) { Text("保持只读") } },
        confirmButton = { TextButton(onClick = { writePermissionDialog = false; onRequestPhoneWrite() }, enabled = phoneWritingAvailable) { Text("允许本次写入") } })
    externalDocument?.let { document ->
        Box(modifier.fillMaxSize()) {
            WorkspaceFileViewer(document = document, modifier = Modifier.fillMaxSize(), bottomInset = bottomInset,
                actionError = error, fullscreen = fullscreen, onFullscreen = onFullscreen,
                canEdit = false,
                previewMaxDimension = previewMaxDimension, previewMaxPixels = previewMaxPixels,
                onClose = onCloseExternal,
                onExport = { onExportExternal(document) },
                onEdit = {},
                onCopyToWorkspace = { onCopyExternalToWorkspace(document) },
                onReselect = onReselectExternal)
            // 查看器和列表共用同一个传输浮层；查看器操作栏可见时胶囊需额外上移避让。
            FileTransferOverlay(transfers = transfers, bottomInset = bottomInset, onCancelTransfer = onCancelTransfer,
                viewerActionBarVisible = !fullscreen)
        }
        return
    }
    openedEntry?.let { entry ->
        val document = remember(files, entry.path) { WorkspaceDocument(files, entry.path, entry.name) }
        Box(modifier.fillMaxSize()) {
            WorkspaceFileViewer(document = document, modifier = Modifier.fillMaxSize(), bottomInset = bottomInset,
                actionError = error, fullscreen = fullscreen, onFullscreen = onFullscreen,
                canEdit = location.writable && !runtimeRunning && !entry.linked,
                previewMaxDimension = previewMaxDimension, previewMaxPixels = previewMaxPixels,
                onClose = { onFullscreen(false); openedEntry = null },
                onExport = { onExportFile(files, entry.path, entry.name) },
                onEdit = { runTask {
                    val editable = files.preview(entry.path)
                    check(editable.text != null) { "此文件不能直接编辑，请使用其他应用打开。" }
                    begin("编辑文本", listOf(entry)); content = editable.text
                    draftOriginal = content; draftRevision = editable.revision.orEmpty(); openedEntry = null
                } })
            // 编辑动作会离开查看器；传输浮层保持在当前页面，并按操作栏高度避让。
            FileTransferOverlay(transfers = transfers, bottomInset = bottomInset, onCancelTransfer = onCancelTransfer,
                viewerActionBarVisible = !fullscreen)
        }
        return
    }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().imePadding()) {
            Column(Modifier.fillMaxSize()) {
                // 顶部主操作固定 56dp：位置、权限 chip 与四个图标入口都不参与列表测量。
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 2.dp),
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
                                    onClick = { locations = false; directory = ""; query = ""; location = area },
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { searching = !searching; query = "" },
                        enabled = !busy && canBrowse,
                        modifier = Modifier.size(48.dp),
                    ) { Icon(if (searching) Icons.Default.Close else Icons.Default.Search,
                        if (searching) "关闭搜索" else "搜索当前文件夹") }
                    IconButton(onClick = { navigate(directory) }, enabled = !busy && canBrowse,
                        modifier = Modifier.size(48.dp)) { TanukiIcon(TanukiGlyph.Refresh, "刷新文件") }
                    Box {
                        IconButton(onClick = { sortMenu = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Menu, "排序与显示")
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
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.MoreVert, "文件操作")
                        }
                        DropdownMenu(menu, { menu = false }) {
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
                            DropdownMenuItem(text = { Text("导入文件到工作区") },
                                onClick = { menu = false; onImport() })
                            DropdownMenuItem(text = { Text("复制当前目录路径") },
                                onClick = { menu = false; copyPath(directory) })
                        }
                    }
                    PermissionChip(access, onClick = { permissionSheet = true })
                }
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
                        Text("${shown.size} 项", Modifier.padding(horizontal = 12.dp),
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
                                    else openedEntry = entry },
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
                                    }
                                }
                            }
                            if (tile) FileEntryLabel(entry, true)
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().tanukiEnter()) {
                    if (!canBrowse) {
                        // 未授权页保留原有说明与动作，只换成令牌字号和胶囊主操作。
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp + bottomInset),
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        ) {
                            TanukiIcon(TanukiGlyph.Folder, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("浏览手机文件", style = MaterialTheme.typography.titleLarge)
                            Text("授权后可查看手机共享存储和可用存储卡中的文件，并打开音频、视频和文档。其他应用的私有目录与系统限制目录仍由 Android 保护。", style = MaterialTheme.typography.bodyMedium)
                            TanukiPillButton(
                                text = "授权访问手机文件",
                                onClick = onRequestStoragePermission,
                                tone = TanukiPillButtonTone.Filled,
                            )
                            Text("不授权也可通过文件操作菜单，从系统文件选择器导入文件。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
    if (destinationPicker) DirectoryPicker(files, action.orEmpty(), directory, actionEntries.map { it.path }, busy,
        onDismiss = { if (!busy) { destinationPicker = false; action = null } }, onChoose = { target ->
            runTask {
                val failed = mutableListOf<String>(); var completed = 0
                for (entry in actionEntries) {
                    try { if (action == "复制") files.copy(entry.path, target) else files.move(entry.path, target); completed++ }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { failed += entry.name }
                }
                destinationPicker = false; action = null; selected = emptySet(); entries = files.list(directory)
                if (failed.isEmpty()) feedback = "已完成 $completed 项"
                else error = "已完成 $completed 项；未处理：${failed.take(5).joinToString("、")}。检查是否同名、链接或空间不足；已有文件未覆盖。"
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
        TanukiStateKind.EMPTY -> TanukiStateView(
            kind = kind,
            modifier = modifier,
            subject = "文件",
            title = copy.title,
            detail = copy.detail,
            actionLabel = copy.actionLabel,
            onAction = when {
                query.isNotEmpty() -> onClearQuery
                hasEntries -> onShowHidden
                else -> onReload
            },
        )
    }
}

/** 传输浮层独立订阅进度；列表页只接收稳定布局，不参与每个进度帧的重组。 */
@Composable
private fun FileTransferOverlay(
    transfers: StateFlow<List<FileTransferProgress>>,
    bottomInset: Dp,
    onCancelTransfer: (Long) -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    viewerActionBarVisible: Boolean = false,
) {
    val items by transfers.collectAsState()
    val summary = FileTransferPresentation.summary(items)
    var sheet by remember { mutableStateOf(false) }
    LaunchedEffect(items.isEmpty()) { if (items.isEmpty()) sheet = false }
    Box(Modifier.fillMaxSize()) {
        // 查看器内没有 SnackbarHost，避免和查看器自己的错误行重复；列表页仍由此统一呈现提示。
        snackbarHostState?.let { host ->
            SnackbarHost(
                host,
                Modifier.align(Alignment.BottomCenter).padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = bottomInset + if (summary != null) 76.dp else 16.dp,
                ),
            )
        }
        summary?.let {
            TransferPill(
                summary = it,
                onOpen = { sheet = true },
                onCancel = { items.forEach { item -> onCancelTransfer(item.id) } },
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = FileViewerLayout.transferPillBottomPadding(bottomInset, viewerActionBarVisible),
                    ),
            )
        }
    }
    if (sheet && items.isNotEmpty()) {
        TransferSheet(
            transfers = items,
            onDismiss = { sheet = false },
            onCancel = onCancelTransfer,
        )
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
                    text = "授权访问手机文件",
                    onClick = onRequestStorage,
                    tone = TanukiPillButtonTone.Filled,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.canGrantPhoneWrite -> TanukiPillButton(
                    text = "授权本次 DSH 写入",
                    onClick = onRequestWrite,
                    enabled = phoneWritingAvailable,
                    tone = TanukiPillButtonTone.Filled,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.canRevokePhoneWrite -> TanukiPillButton(
                    text = "撤销 DSH 写入权限",
                    onClick = onRevokeWrite,
                    tone = TanukiPillButtonTone.Tonal,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (location == FileLocation.PHONE && phoneStorageGranted &&
                !state.canGrantPhoneWrite && !state.canRevokePhoneWrite && !state.needsStorageGrant) {
                Text("授权只在本次 DSH 运行期间有效；停止 DSH 后自动恢复只读。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.canStopRuntime) {
                HorizontalDivider()
                Text("停止 DSH 后，工作区写入会自动恢复；正在运行的会话也会结束。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TanukiPillButton(
                    text = "停止 DSH",
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
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val percent = summary.percent
    val label = TanukiFileVisuals.transferLabel(percent)
    TanukiGlassPill(
        modifier = modifier
            .widthIn(max = 190.dp)
            .clickable(onClick = onOpen)
            .semantics { contentDescription = "文件传输$label，共 ${summary.count} 项，点击查看详情" },
        contentPadding = PaddingValues(start = TanukiSpace.MD_DP.dp, end = 0.dp),
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            if (percent != null) CircularProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 2.5.dp,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            ) else CircularProgressIndicator(
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
        IconButton(onClick = onCancel, modifier = Modifier.size(TanukiSpace.TOUCH_TARGET_MIN_DP.dp)) {
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
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    // sheet 打开期间每秒刷新剩余时间；浮层本身只随真实字节进度重组。
    LaunchedEffect(transfers.size) {
        while (transfers.isNotEmpty()) {
            delay(1000)
            now = SystemClock.elapsedRealtime()
        }
    }
    LaunchedEffect(transfers.isEmpty()) { if (transfers.isEmpty()) onDismiss() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())
                .navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("传输进度", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭传输详情") }
            }
            FileTransferPresentation.summary(transfers)?.let { summary ->
                Text(
                    if (summary.percent != null) "总进度 ${summary.percent}% · ${summary.count} 个文件任务"
                    else "${summary.count} 个文件任务 · 总大小未知",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            transfers.forEach { item ->
                key(item.id) {
                    TransferRow(item = item, now = now, onCancel = { onCancel(item.id) })
                }
            }
            Text("进度按实际复制或下载的字节计算；总大小未知时显示不定进度，不估造百分比。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TransferRow(
    item: FileTransferProgress,
    now: Long,
    onCancel: () -> Unit,
) {
    val percent = FileTransferPresentation.percent(item.received, item.total)
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
        if (percent != null) LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = progressModifier,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
        ) else LinearProgressIndicator(
            modifier = progressModifier,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
        )
        Text(FileTransferPresentation.sizeText(item.received, item.total),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(FileTransferPresentation.remainingText(item, now),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TanukiPillButton(
            text = "取消此文件",
            onClick = onCancel,
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
private fun DirectoryPicker(files: WorkspaceFileManager, operation: String, initial: String, sources: List<String>, busy: Boolean,
                            onDismiss: () -> Unit, onChoose: (String) -> Unit) {
    var current by remember { mutableStateOf(initial) }
    var folders by remember { mutableStateOf(emptyList<WorkspaceFileManager.Entry>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(current) {
        loading = true; error = null
        try { folders = files.list(current).filter { it.directory && sources.none { path -> it.path == path || it.path.startsWith("$path/") } } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { folders = emptyList(); error = "无法打开此目录，请返回上一级" }
        finally { loading = false }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("${operation}到文件夹") }, text = {
        Column(Modifier.heightIn(max = 400.dp)) {
            TanukiPathText(files.guestPath(current))
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
            Text("选择当前位置；同名条目会跳过，不会覆盖。", style = MaterialTheme.typography.bodySmall)
        }
    }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } }, confirmButton = {
        TextButton(onClick = { onChoose(current) }, enabled = !loading && !busy && error == null && sources.none { current == it || current.startsWith("$it/") }) { Text(if (busy) "处理中…" else "${operation}到这里") }
    })
}

/** 文件大小格式化委托给可 JVM 回归的纯函数。 */
private fun fileSize(bytes: Long): String = TanukiFileVisuals.sizeText(bytes)
