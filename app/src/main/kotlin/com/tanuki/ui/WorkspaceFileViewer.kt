package com.tanuki.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.tanuki.storage.WorkspaceFileManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** 文件在独立阅读页展示，分段读取避免将整个大文本放进内存。 */
@Composable
internal fun WorkspaceFileViewer(
    files: WorkspaceFileManager,
    entry: WorkspaceFileManager.Entry,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
    actionError: String? = null,
    canEdit: Boolean,
    onClose: () -> Unit,
    onExport: () -> Unit,
    onEdit: () -> Unit,
    fullscreen: Boolean = false,
    onFullscreen: (Boolean) -> Unit = {},
    previewMaxDimension: Int = PdfRenderPolicy.MAX_DIMENSION,
    previewMaxPixels: Int = PdfRenderPolicy.MAX_PIXELS,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var info by remember(files, entry.path) { mutableStateOf<WorkspaceFileManager.FileInfo?>(null) }
    var image by remember(files, entry.path) { mutableStateOf<WorkspaceFileManager.Preview?>(null) }
    var error by remember(files, entry.path) { mutableStateOf<String?>(null) }
    var loading by remember(files, entry.path) { mutableStateOf(true) }
    var forceText by remember(files, entry.path) { mutableStateOf(false) }
    var copiedPath by remember(files, entry.path) { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    var copied by remember { mutableLongStateOf(0L) }
    var copyTotal by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var openIntent by remember(files, entry.path) { mutableStateOf<Intent?>(null) }
    fun report(e: Exception) {
        error = e.message?.takeIf { it.any { char -> char in '\u4e00'..'\u9fff' } }
            ?: "无法读取此文件。它可能已被修改或移除，请返回后刷新。"
    }
    suspend fun prepare(): Intent {
        copied = 0; copyTotal = 0
        return files.prepareOpen(context, entry.path) { current, total -> copied = current; copyTotal = total }
    }
    fun openWith() {
        if (opening) return
        opening = true; error = null
        scope.launch {
            try {
                val intent = openIntent ?: prepare().also { openIntent = it }
                context.startActivity(Intent.createChooser(intent, "打开方式"))
            } catch (_: ActivityNotFoundException) {
                error = "没有可打开此格式的应用，请安装相应播放器或文档查看器后重试。"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { report(e) }
            finally { opening = false }
        }
    }
    LaunchedEffect(files, entry.path) {
        try {
            val details = files.describe(entry.path)
            info = details
            when (details.kind) {
                WorkspaceFileManager.FileKind.IMAGE -> {
                    image = files.preview(entry.path)
                    if (image?.image == null) error = "此图片格式无法内置预览，请使用其他应用打开。"
                }
                WorkspaceFileManager.FileKind.AUDIO, WorkspaceFileManager.FileKind.VIDEO -> {
                    opening = true
                    try { openIntent = prepare() } finally { opening = false }
                }
                else -> Unit
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { report(e) }
        finally { loading = false }
    }
    LaunchedEffect(opening) {
        if (opening) {
            val started = SystemClock.elapsedRealtime()
            while (true) { elapsed = (SystemClock.elapsedRealtime() - started) / 1000; delay(250) }
        }
    }
    val loadedBitmap = image?.image
    DisposableEffect(loadedBitmap) { onDispose { loadedBitmap?.takeUnless { it.isRecycled }?.recycle() } }
    val currentFullscreenCallback by rememberUpdatedState(onFullscreen)
    DisposableEffect(Unit) { onDispose { currentFullscreenCallback(false) } }
    BackHandler { if (fullscreen) onFullscreen(false) else onClose() }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(bottom = if (fullscreen) 0.dp else bottomInset).imePadding()) {
            if (!fullscreen) Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "关闭文件") }
                Column(Modifier.weight(1f)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(info?.let { "${viewerBytes(it.bytes)} · ${it.mimeType}" } ?: "读取文件信息…",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (canEdit && info?.kind == WorkspaceFileManager.FileKind.TEXT && (info?.bytes ?: Long.MAX_VALUE) <= WorkspaceFileManager.TEXT_LIMIT)
                    TextButton(onClick = onEdit, enabled = !opening && !loading) { Text("编辑") }
            }
            if (opening) {
                if (copyTotal > 0L) LinearProgressIndicator(progress = { (copied.toDouble() / copyTotal).coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("准备打开 · ${elapsed}秒" + if (copyTotal > 0L) " · ${viewerBytes(copied)} / ${viewerBytes(copyTotal)}" else "",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
            } else if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            (error ?: actionError)?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            // 未知/不支持类型被强制按文本打开时，保留返回类型说明的入口，避免用户只能退出查看器。
            if (forceText && info?.kind != null && info?.kind != WorkspaceFileManager.FileKind.TEXT) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("按文本方式查看（只读）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { forceText = false }) { Text("返回类型说明") }
                    }
                }
            }
            if (forceText || info?.kind == WorkspaceFileManager.FileKind.TEXT) {
                TextFileReader(files, entry.path, Modifier.weight(1f), clipboardCopy = { clipboard.setText(AnnotatedString(it)) })
            } else {
                val kind = info?.kind
                val contentModifier = Modifier.weight(1f).fillMaxWidth()
                    .padding(
                        if (fullscreen || kind == WorkspaceFileManager.FileKind.PDF ||
                            kind == WorkspaceFileManager.FileKind.DOCX || kind == WorkspaceFileManager.FileKind.XLSX
                        ) 0.dp else 16.dp,
                    )
                Box(contentModifier, contentAlignment = Alignment.Center) {
                    when (kind) {
                        WorkspaceFileManager.FileKind.IMAGE -> image?.image?.let { bitmap ->
                            Zoomable(
                                contentWidth = bitmap.width,
                                contentHeight = bitmap.height,
                                resetKey = entry.path,
                                modifier = Modifier.fillMaxSize(),
                            ) { content ->
                                Image(bitmap.asImageBitmap(), entry.name, content, contentScale = ContentScale.Fit)
                            }
                        }
                        WorkspaceFileManager.FileKind.PDF -> PdfPreview(
                            files, entry.path, Modifier.fillMaxSize(),
                            maxDimension = previewMaxDimension,
                            maxPixels = previewMaxPixels,
                        )
                        WorkspaceFileManager.FileKind.DOCX -> DocxPreview(files, entry.path, Modifier.fillMaxSize())
                        WorkspaceFileManager.FileKind.XLSX -> XlsxTablePreview(files, entry.path, Modifier.fillMaxSize())
                        WorkspaceFileManager.FileKind.DOC -> Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TanukiIcon(TanukiGlyph.File, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("旧版 Word 文档（.doc）", style = MaterialTheme.typography.titleMedium)
                            Text("暂不支持内置预览，可用其他应用打开。", style = MaterialTheme.typography.bodyMedium)
                        }
                        WorkspaceFileManager.FileKind.AUDIO -> openIntent?.data?.let { NativeAudioPlayer(it) }
                        WorkspaceFileManager.FileKind.VIDEO -> openIntent?.data?.let { WorkspaceVideoPlayer(it, fullscreen, onFullscreen) }
                        WorkspaceFileManager.FileKind.OTHER -> UnsupportedPreview(
                            name = entry.name,
                            mime = info?.mimeType.orEmpty(),
                            copied = copiedPath,
                            onOpenAsText = { forceText = true },
                            onCopyPath = {
                                clipboard.setText(AnnotatedString(files.guestPath(entry.path)))
                                copiedPath = true
                            },
                        )
                        // 信息读取失败时由上方错误文案说明；这里给出兜底提示，不能留下空白区域。
                        null -> if (!loading) Text("无法读取文件类型信息，请返回后重试。", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                        else -> Unit
                    }
                }
            }
            if (!fullscreen) HorizontalDivider()
            if (!fullscreen) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onExport, enabled = !opening && !loading && info != null, modifier = Modifier.heightIn(min = 48.dp)) {
                    TanukiIcon(TanukiGlyph.Download); Spacer(Modifier.width(8.dp)); Text("导出此文件")
                }
                TextButton(onClick = ::openWith, enabled = !opening && !loading && info != null, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Default.ExitToApp, null); Spacer(Modifier.width(8.dp)); Text("使用其他应用打开")
                }
            }
        }
    }
}

@Composable
private fun TextFileReader(files: WorkspaceFileManager, path: String, modifier: Modifier, clipboardCopy: (String) -> Unit) {
    var requestedEncoding by rememberSaveable(files, path) { mutableStateOf("自动") }
    var offsets by remember(files, path, requestedEncoding) { mutableStateOf(listOf(0L)) }
    // 与 offsets 平行保存每段第一行的行号，上一段/下一段不会重新从 1 计数。
    var lineStarts by remember(files, path, requestedEncoding) { mutableStateOf(listOf(1)) }
    var pageIndex by remember(files, path, requestedEncoding) { mutableIntStateOf(0) }
    var page by remember(files, path, requestedEncoding) { mutableStateOf<WorkspaceFileManager.TextPage?>(null) }
    var error by remember(files, path) { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var query by rememberSaveable(files, path) { mutableStateOf("") }
    var searchVisible by rememberSaveable(files, path) { mutableStateOf(false) }
    var matchIndex by remember { mutableIntStateOf(0) }
    var fontSize by rememberSaveable { mutableIntStateOf(16) }
    var wrap by rememberSaveable { mutableStateOf(true) }
    var showLineNumbers by rememberSaveable { mutableStateOf(true) }
    var encodingMenu by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val text = page?.text.orEmpty()
    val startLine = lineStarts.getOrElse(pageIndex) { 1 }
    val numbered = remember(text, startLine, showLineNumbers) { TextPageLayout.number(text, startLine, showLineNumbers) }
    val matches = remember(text, query) {
        buildList {
            if (query.isNotEmpty()) {
                var next = text.indexOf(query, ignoreCase = true)
                while (next >= 0) { add(next); next = text.indexOf(query, next + query.length, ignoreCase = true) }
            }
        }
    }
    // 搜索仍以原始文本计算，再换算为带行号文本，避免把行号里的数字当作匹配。
    val displayMatches = remember(matches, text, numbered.prefixLength) {
        TextPageLayout.displayOffsets(matches, text, numbered.prefixLength)
    }
    LaunchedEffect(files, path, requestedEncoding, pageIndex) {
        loading = true; error = null; copied = false
        // 切换到新分段时先清空旧文本，避免读取失败后继续展示上一段却显示新行号。
        page = null; layout = null
        try {
            page = files.readTextPage(path, offsets[pageIndex], requestedEncoding)
            matchIndex = 0; vertical.scrollTo(0); horizontal.scrollTo(0)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            error = e.message?.takeIf { it.any { char -> char in '\u4e00'..'\u9fff' } }
                ?: "无法按此编码读取，请更换编码或使用其他应用打开。"
        } finally { loading = false }
    }
    LaunchedEffect(query) { matchIndex = 0 }
    LaunchedEffect(matchIndex, displayMatches, layout) {
        val target = displayMatches.getOrNull(matchIndex) ?: return@LaunchedEffect
        val result = layout ?: return@LaunchedEffect
        if (target < result.layoutInput.text.length) {
            val bounds = result.getBoundingBox(target)
            vertical.animateScrollTo(bounds.top.toInt().coerceAtLeast(0))
            if (!wrap) horizontal.animateScrollTo(bounds.left.toInt().coerceAtLeast(0))
        }
    }
    val highlight = MaterialTheme.colorScheme.secondaryContainer
    val selected = MaterialTheme.colorScheme.tertiaryContainer
    val gutterColor = MaterialTheme.colorScheme.onSurfaceVariant
    val annotated = remember(numbered, displayMatches, matchIndex, highlight, selected, gutterColor, query) {
        buildAnnotatedString {
            append(numbered.text)
            val gutter = SpanStyle(color = gutterColor)
            for (start in numbered.numberStarts) addStyle(gutter, start, (start + numbered.prefixLength).coerceAtMost(numbered.text.length))
            for ((index, start) in displayMatches.withIndex()) {
                addStyle(SpanStyle(background = if (index == matchIndex) selected else highlight), start, (start + query.length).coerceAtMost(numbered.text.length))
            }
        }
    }
    val hasNext = page?.let { TextPageLayout.canAdvance(it.nextOffset, it.totalBytes) } == true
    val truncated = page?.let { TextPageLayout.isTruncated(it.nextOffset, it.totalBytes, it.hasMore) } == true
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { searchVisible = !searchVisible }) { Icon(Icons.Default.Search, "查找文本") }
            IconButton(onClick = { fontSize = (fontSize - 2).coerceAtLeast(12) }, enabled = fontSize > 12) { Text("A−", Modifier.semantics { contentDescription = "减小字号" }) }
            Text("${fontSize}sp", style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { fontSize = (fontSize + 2).coerceAtMost(32) }, enabled = fontSize < 32) { Text("A+", Modifier.semantics { contentDescription = "增大字号" }) }
            FilterChip(selected = showLineNumbers, onClick = { showLineNumbers = !showLineNumbers }, label = { Text("行号") })
            FilterChip(selected = wrap, onClick = { wrap = !wrap }, label = { Text("自动换行") })
            Spacer(Modifier.width(8.dp))
            Box {
                TextButton(onClick = { encodingMenu = true }) { Text("文本编码"); Icon(Icons.Default.ArrowDropDown, null) }
                DropdownMenu(expanded = encodingMenu, onDismissRequest = { encodingMenu = false }) {
                    listOf("自动", "UTF-8", "UTF-16LE", "UTF-16BE", "GB18030", "Big5", "ISO-8859-1").forEach { encoding ->
                        DropdownMenuItem(text = { Text(encoding + if (encoding == requestedEncoding) " ✓" else "") },
                            onClick = { requestedEncoding = encoding; encodingMenu = false })
                    }
                }
            }
            TextButton(onClick = { clipboardCopy(text); copied = true }, enabled = page != null && !loading) { Text(if (copied) "已复制本段" else "复制本段") }
        }
        if (searchVisible) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true, label = { Text("搜索当前段") }, supportingText = { Text(if (matches.isEmpty()) "${matches.size} 处匹配" else "${matchIndex + 1} / ${matches.size} 处匹配") },
                trailingIcon = { Row {
                    IconButton(onClick = { matchIndex = (matchIndex - 1 + matches.size) % matches.size }, enabled = matches.isNotEmpty()) { Icon(Icons.Default.KeyboardArrowUp, "上一个匹配") }
                    IconButton(onClick = { matchIndex = (matchIndex + 1) % matches.size }, enabled = matches.isNotEmpty()) { Icon(Icons.Default.KeyboardArrowDown, "下一个匹配") }
                } })
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        SelectionContainer(Modifier.weight(1f).fillMaxWidth().verticalScroll(vertical).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(annotated, Modifier.fillMaxWidth().then(if (wrap) Modifier else Modifier.horizontalScroll(horizontal)),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = fontSize.sp, lineHeight = (fontSize * 1.5).sp),
                softWrap = wrap, onTextLayout = { layout = it })
        }
        Text(
            "${page?.encoding ?: requestedEncoding} · 第 $startLine–${startLine + numbered.lineCount - 1} 行" +
                page?.let { " · 已显示 ${viewerBytes(it.nextOffset)} / ${viewerBytes(it.totalBytes)}" }.orEmpty(),
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (truncated) Text(
            "文件较大（${viewerBytes(page?.totalBytes ?: 0)}），仅显示前 ${viewerBytes(TextPageLayout.MAX_VIEW_BYTES)}；已截断。可导出或使用其他应用打开完整文件。",
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { pageIndex-- }, enabled = pageIndex > 0 && !loading) { Text("上一段") }
            TextButton(onClick = {
                page?.let { current ->
                    offsets = offsets.take(pageIndex + 1) + current.nextOffset
                    lineStarts = lineStarts.take(pageIndex + 1) + TextPageLayout.nextStartLine(startLine, current.text)
                    pageIndex++
                }
            }, enabled = hasNext && !loading) { Text(if (truncated) "已截断" else "下一段") }
        }
    }
}

/**
 * 未知或暂不支持的类型：给出明确中文说明与“用文本打开/复制路径”回退。
 * 不渲染空白区域，也不把文件内容送进任何解析器，保证不会白屏或崩溃。
 */
@Composable
private fun UnsupportedPreview(
    name: String, mime: String, copied: Boolean,
    onOpenAsText: () -> Unit, onCopyPath: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        TanukiIcon(TanukiGlyph.File, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text("暂不支持内置预览", style = MaterialTheme.typography.titleMedium)
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("文件类型：${mime.ifBlank { "未知" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "该格式没有内置查看器。可以按文本方式打开（只读，适合代码、配置和日志），或复制路径后用其他应用打开；原文件不会被修改。",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenAsText) { Text("用文本打开") }
            OutlinedButton(onClick = onCopyPath) { Text(if (copied) "已复制路径" else "复制路径") }
        }
    }
}

@Composable
private fun NativeAudioPlayer(uri: Uri) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var ready by remember(uri) { mutableStateOf(false) }
    var playing by remember(uri) { mutableStateOf(false) }
    var failed by remember(uri) { mutableStateOf(false) }
    var position by remember(uri) { mutableIntStateOf(0) }
    var duration by remember(uri) { mutableIntStateOf(0) }
    var seeking by remember { mutableStateOf(false) }
    DisposableEffect(uri) {
        val current = MediaPlayer()
        player = current
        current.setOnPreparedListener { duration = it.duration.coerceAtLeast(0); ready = true }
        current.setOnCompletionListener { playing = false; position = duration }
        current.setOnErrorListener { _, _, _ -> failed = true; playing = false; true }
        try { current.setDataSource(context, uri); current.prepareAsync() }
        catch (_: Exception) { failed = true }
        onDispose { current.release(); player = null }
    }
    MediaPlaybackLifecycle { if (ready) { runCatching { player?.pause() }; playing = false } }
    LaunchedEffect(ready, playing) {
        while (ready) {
            if (!seeking) position = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            delay(300)
        }
    }
    Column(Modifier.fillMaxWidth().widthIn(max = 600.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.PlayArrow, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        if (failed) Text("设备无法解码此音频，请使用其他应用打开。", color = MaterialTheme.colorScheme.error)
        else if (!ready) LinearProgressIndicator(Modifier.fillMaxWidth())
        else {
            Slider(value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                onValueChange = { seeking = true; position = it.toInt() },
                onValueChangeFinished = { player?.seekTo(position); seeking = false }, valueRange = 0f..duration.coerceAtLeast(1).toFloat())
            Text("${mediaTime(position)} / ${mediaTime(duration)}", style = MaterialTheme.typography.labelLarge)
            FilledTonalButton(onClick = {
                if (playing) player?.pause() else { if (position >= duration) player?.seekTo(0); player?.start() }
                playing = !playing
            }) { if (!playing) Icon(Icons.Default.PlayArrow, null); Text(if (playing) "暂停" else "播放") }
        }
    }
}

/** 查看器离开前台时暂停媒体，避免通过外部应用打开后两路声音重叠。 */
@Composable
internal fun MediaPlaybackLifecycle(onBackground: () -> Unit) {
    val context = LocalContext.current
    val currentAction by rememberUpdatedState(onBackground)
    val owner = remember(context) {
        var current = context
        while (current is android.content.ContextWrapper && current !is LifecycleOwner) current = current.baseContext
        current as? LifecycleOwner
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) currentAction()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
}

private fun mediaTime(milliseconds: Int): String = String.format(Locale.ROOT, "%d:%02d", milliseconds / 60000, milliseconds / 1000 % 60)
private fun viewerBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KiB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MiB", bytes / (1024.0 * 1024))
    else -> String.format(Locale.getDefault(), "%.1f GiB", bytes / (1024.0 * 1024 * 1024))
}
