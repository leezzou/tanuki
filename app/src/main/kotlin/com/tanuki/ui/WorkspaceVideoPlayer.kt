package com.tanuki.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale

/** 保留同一原生视图；全屏只改变父布局约束，不切换宿主或重新设置媒体来源。 */
internal class WorkspaceVideoView(context: Context) : VideoView(context) {
    var preparedPlayer: MediaPlayer? = null
    fun actualPlaybackSpeed(): Float = checkNotNull(preparedPlayer).playbackParams.speed
}

@Composable
internal fun WorkspaceVideoPlayer(uri: Uri, fullscreen: Boolean, onFullscreen: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var view by remember(uri) { mutableStateOf<WorkspaceVideoView?>(null) }
    var ready by remember(uri) { mutableStateOf(false) }
    var failed by remember(uri) { mutableStateOf(false) }
    var buffering by remember(uri) { mutableStateOf(false) }
    var seeking by remember(uri) { mutableStateOf(false) }
    var dragging by remember(uri) { mutableStateOf(false) }
    var playing by remember(uri) { mutableStateOf(false) }
    var wantsPlayback by remember(uri) { mutableStateOf(false) }
    var hasStarted by remember(uri) { mutableStateOf(false) }
    var position by remember(uri) { mutableIntStateOf(0) }
    var duration by remember(uri) { mutableIntStateOf(0) }
    var speed by remember(uri) { mutableFloatStateOf(1f) }
    var speedMenu by remember { mutableStateOf(false) }
    var feedback by remember(uri) { mutableStateOf<String?>(null) }
    var poster by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var posterLoading by remember(uri) { mutableStateOf(true) }
    var posterFailed by remember(uri) { mutableStateOf(false) }
    val controlsReady = ready && !failed && !buffering && !seeking
    fun seek(target: Int) {
        if (!controlsReady) return
        val current = view ?: return
        seeking = true; feedback = null
        try { checkNotNull(current.preparedPlayer).seekTo(target.coerceIn(0, duration.coerceAtLeast(0)).toLong(), MediaPlayer.SEEK_CLOSEST) }
        catch (_: Exception) { seeking = false; feedback = "暂时无法跳转，请稍后重试。" }
    }
    fun changeSpeed(requested: Float) {
        if (!controlsReady || !VideoPlaybackPolicy.acceptsSpeed(requested)) return
        val current = view ?: return
        val player = current.preparedPlayer ?: return
        val wasPlaying = runCatching { current.isPlaying }.getOrDefault(false)
        val previousSpeed = speed
        try {
            player.playbackParams = PlaybackParams().setSpeed(requested).setPitch(1f)
            // 暂停后原生速度可能报告0；单独保留用户选择，再恢复原来的播放或暂停状态。
            if (wasPlaying) current.start() else current.pause()
            speed = requested
            playing = wasPlaying; wantsPlayback = wasPlaying; feedback = null
        } catch (_: Exception) {
            // 参数或状态恢复失败时尝试恢复旧参数，不能提交失败的倍速选择或意外开始播放。
            runCatching {
                player.playbackParams = PlaybackParams().setSpeed(previousSpeed).setPitch(1f)
                if (wasPlaying) current.start() else current.pause()
            }
            if (!wasPlaying) runCatching { current.pause() }
            playing = runCatching { current.isPlaying }.getOrDefault(false); wantsPlayback = wasPlaying
            feedback = "此设备或视频不支持该倍速，保留原倍速选择。"
        }
    }
    LaunchedEffect(uri) {
        var candidate: Bitmap? = null
        try {
            withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    candidate = if (Build.VERSION.SDK_INT >= 27) {
                        retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 1280, 720)
                    } else {
                        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { frame ->
                            val ratio = minOf(1f, 1280f / frame.width, 720f / frame.height)
                            if (ratio >= 1f) frame else Bitmap.createScaledBitmap(frame,
                                (frame.width * ratio).toInt().coerceAtLeast(1), (frame.height * ratio).toInt().coerceAtLeast(1), true)
                                .also { if (it !== frame) frame.recycle() }
                        }
                    }
                    currentCoroutineContext().ensureActive()
                } finally { retriever.release() }
            }
            poster = candidate; candidate = null; posterFailed = poster == null
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { posterFailed = true }
        finally { candidate?.recycle(); posterLoading = false }
    }
    val currentPoster = poster
    DisposableEffect(currentPoster) { onDispose { currentPoster?.takeUnless { it.isRecycled }?.recycle() } }
    DisposableEffect(uri) {
        onDispose {
            view?.apply {
                setOnPreparedListener(null); setOnErrorListener(null); setOnCompletionListener(null)
                preparedPlayer?.setOnSeekCompleteListener(null)
                preparedPlayer?.setOnInfoListener(null)
                stopPlayback(); preparedPlayer = null
            }
        }
    }
    MediaPlaybackLifecycle {
        wantsPlayback = false
        if (ready) runCatching { view?.pause() }
        playing = false
    }
    LaunchedEffect(uri, ready) {
        while (ready && !failed) {
            val current = view
            if (!dragging && !seeking) position = runCatching { current?.currentPosition ?: position }.getOrDefault(position)
            playing = runCatching { current?.isPlaying == true }.getOrDefault(false)
            delay(200)
        }
    }
    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { currentContext ->
                WorkspaceVideoView(currentContext).apply {
                    view = this
                    isFocusable = false
                    setOnPreparedListener { player ->
                        preparedPlayer = player
                        duration = player.duration.coerceAtLeast(0); ready = true; failed = false
                        player.setOnSeekCompleteListener {
                            position = currentPosition; seeking = false
                        }
                        player.setOnInfoListener { _, what, _ ->
                            when (what) {
                                MediaPlayer.MEDIA_INFO_BUFFERING_START -> buffering = true
                                MediaPlayer.MEDIA_INFO_BUFFERING_END -> buffering = false
                            }
                            false
                        }
                        // 若系统重建底层视频surface，恢复既有位置、速度与用户播放选择。
                        if (position > 0) { seeking = true; player.seekTo(position.coerceAtMost(duration).toLong(), MediaPlayer.SEEK_CLOSEST) }
                        val resumePlayback = wantsPlayback
                        try {
                            player.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1f)
                            if (resumePlayback) start() else pause()
                        } catch (_: Exception) {
                            runCatching { if (resumePlayback) start() else pause() }
                            feedback = "暂时无法恢复所选倍速，请重新选择后重试。"
                        }
                        playing = runCatching { isPlaying }.getOrDefault(false)
                    }
                    setOnCompletionListener { playing = false; wantsPlayback = false; position = duration; seeking = false }
                    setOnErrorListener { _, _, _ -> failed = true; ready = false; seeking = false; playing = false; wantsPlayback = false; true }
                    setVideoURI(uri)
                }
            })
            if (!hasStarted && currentPoster != null) Image(currentPoster.asImageBitmap(), "视频封面", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            if (!hasStarted && posterLoading) Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .9f)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("正在读取视频封面…")
                }
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp)) {
            if (failed) Text("设备无法解码此视频，请使用其他应用打开。", color = MaterialTheme.colorScheme.error)
            else if (!ready || buffering || seeking) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (seeking) "正在跳转…" else if (buffering) "视频缓冲中…" else "正在准备视频…", style = MaterialTheme.typography.labelSmall)
            }
            if (!hasStarted && posterFailed) Text("无法读取封面，仍可尝试播放视频。", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            feedback?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Slider(value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                onValueChange = { dragging = true; position = it.toInt() },
                onValueChangeFinished = { dragging = false; seek(position) },
                enabled = controlsReady && duration > 0, valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                modifier = Modifier.semantics { contentDescription = "视频进度"; stateDescription = "位置${position}毫秒，共${duration}毫秒" })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(videoTime(position), style = MaterialTheme.typography.labelSmall)
                Text(videoTime(duration), style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { seek(VideoPlaybackPolicy.skip(position, -10_000, duration)) }, enabled = controlsReady && duration > 0) { Text("后退10秒") }
                FilledTonalButton(onClick = {
                    val current = view ?: return@FilledTonalButton
                    val wasPlaying = runCatching { current.isPlaying }.getOrDefault(false)
                    try {
                        if (wasPlaying) { current.pause(); wantsPlayback = false; playing = false }
                        else {
                            if (position >= duration && duration > 0) current.seekTo(0)
                            // 恢复播放时重新应用用户选择，不以暂停状态返回的原生0速度作为倍速。
                            checkNotNull(current.preparedPlayer).playbackParams = PlaybackParams().setSpeed(speed).setPitch(1f)
                            current.start(); hasStarted = true; wantsPlayback = true; playing = true
                        }
                        feedback = null
                    } catch (_: Exception) {
                        if (!wasPlaying) runCatching { current.pause() }
                        playing = runCatching { current.isPlaying }.getOrDefault(false); wantsPlayback = wasPlaying
                        feedback = "暂时无法播放，请稍后重试。"
                    }
                }, enabled = controlsReady) { Text(if (playing) "暂停视频" else "播放视频") }
                TextButton(onClick = { seek(VideoPlaybackPolicy.skip(position, 10_000, duration)) }, enabled = controlsReady && duration > 0) { Text("前进10秒") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Box {
                    TextButton(onClick = { speedMenu = true }, enabled = controlsReady) { Text("倍速 ${videoSpeed(speed)}") }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        VideoPlaybackPolicy.speeds.forEach { rate -> DropdownMenuItem(
                            text = { Text(videoSpeed(rate) + if (rate == speed) " ✓" else "") },
                            onClick = { speedMenu = false; changeSpeed(rate) }, enabled = controlsReady) }
                    }
                }
                IconButton(onClick = { onFullscreen(!fullscreen) }) {
                    TanukiIcon(if (fullscreen) TanukiGlyph.Contract else TanukiGlyph.Expand,
                        if (fullscreen) "退出视频全屏" else "视频全屏")
                }
            }
        }
    }
}

private fun videoTime(milliseconds: Int): String = String.format(Locale.ROOT, "%d:%02d", milliseconds / 60000, milliseconds / 1000 % 60)
private fun videoSpeed(speed: Float): String = String.format(Locale.ROOT, "%.2f", speed).trimEnd('0').trimEnd('.') + "×"
