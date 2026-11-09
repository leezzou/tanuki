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
            if (forceText || info?.kind == WorkspaceFileManager.FileKind.TEXT) {
                TextFileReader(files, entry.path, Modifier.weight(1f), clipboardCopy = { clipboard.setText(AnnotatedString(it)) })
            } else {
                val kind = info?.kind
                val contentModifier = Modifier.weight(1f).fillMaxWidth()
                    .padding(if (fullscreen || kind == WorkspaceFileManager.FileKind.PDF || kind == WorkspaceFileManager.FileKind.DOCX) 0.dp else 16.dp)
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
                        WorkspaceFileManager.FileKind.DOCX -> DocxTextPreview(files, entry.path, Modifier.fillMaxSize())
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
                        WorkspaceFileManager.FileKind.OTHER -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            TanukiIcon(TanukiGlyph.File, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(info?.mimeType.orEmpty(), style = MaterialTheme.typography.titleMedium)
                            Text("使用设备上的应用打开此文件。原文件不会被修改。", style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = { forceText = true }) { Text("按文本查看") }
                        }
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
    var pageIndex by remember(files, path, requestedEncoding) { mutableIntStateOf(0) }
    var page by remember(files, path, requestedEncoding) { mutableStateOf<WorkspaceFileManager.TextPage?>(null) }
    var error by remember(files, path) { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var query by rememberSaveable(files, path) { mutableStateOf("") }
    var searchVisible by rememberSaveable(files, path) { mutableStateOf(false) }
    var matchIndex by remember { mutableIntStateOf(0) }
    var fontSize by rememberSaveable { mutableIntStateOf(16) }
    var wrap by rememberSaveable { mutableStateOf(true) }
    var encodingMenu by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val text = page?.text.orEmpty()
    val matches = remember(text, query) {
        buildList {
            if (query.isNotEmpty()) {
                var next = text.indexOf(query, ignoreCase = true)
                while (next >= 0) { add(next); next = text.indexOf(query, next + query.length, ignoreCase = true) }
            }
        }
    }
    LaunchedEffect(files, path, requestedEncoding, pageIndex) {
        loading = true; error = null; copied = false
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
    LaunchedEffect(matchIndex, matches, layout) {
        val target = matches.getOrNull(matchIndex) ?: return@LaunchedEffect
        val result = layout ?: return@LaunchedEffect
        if (target < result.layoutInput.text.length) {
            val bounds = result.getBoundingBox(target)
            vertical.animateScrollTo(bounds.top.toInt().coerceAtLeast(0))
            if (!wrap) horizontal.animateScrollTo(bounds.left.toInt().coerceAtLeast(0))
        }
    }
    val highlight = MaterialTheme.colorScheme.secondaryContainer
    val selected = MaterialTheme.colorScheme.tertiaryContainer
    val annotated = remember(text, matches, matchIndex, highlight, selected) {
        buildAnnotatedString {
            append(text)
            for ((index, start) in matches.withIndex()) addStyle(SpanStyle(background = if (index == matchIndex) selected else highlight), start, start + query.length)
        }
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { searchVisible = !searchVisible }) { Icon(Icons.Default.Search, "查找文本") }
            IconButton(onClick = { fontSize = (fontSize - 2).coerceAtLeast(12) }, enabled = fontSize > 12) { Text("A−", Modifier.semantics { contentDescription = "减小字号" }) }
            Text("${fontSize}sp", style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { fontSize = (fontSize + 2).coerceAtMost(32) }, enabled = fontSize < 32) { Text("A+", Modifier.semantics { contentDescription = "增大字号" }) }
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
        Text("${page?.encoding ?: requestedEncoding} · 第 ${pageIndex + 1} 段" + page?.let { " · ${viewerBytes(it.nextOffset)} / ${viewerBytes(it.totalBytes)}" }.orEmpty(),
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { pageIndex-- }, enabled = pageIndex > 0 && !loading) { Text("上一段") }
            TextButton(onClick = {
                page?.let { current -> offsets = offsets.take(pageIndex + 1) + current.nextOffset; pageIndex++ }
            }, enabled = page?.hasMore == true && !loading) { Text("下一段") }
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
