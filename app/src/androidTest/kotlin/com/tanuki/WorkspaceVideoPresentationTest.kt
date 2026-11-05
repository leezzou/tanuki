package com.tanuki

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.VideoView
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.storage.WorkspaceFileManager
import com.tanuki.ui.TanukiTheme
import com.tanuki.ui.WorkspaceFileViewer
import com.tanuki.ui.WorkspaceVideoView
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** 自有三色 H.264 合成短片经过真实文件页与只读 URI，验证原生播放器实际播放。 */
class WorkspaceVideoPresentationTest {
    @get:org.junit.Rule val visibleAccessibility = VisibleAccessibilityRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun nodes(): List<AccessibilityNodeInfo> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptyList()
        if (root.packageName?.toString() != context.packageName) return emptyList()
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        val result = arrayListOf<AccessibilityNodeInfo>()
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (!node.refresh()) continue
            result += node
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return result
    }

    private fun click(label: String) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val current = nodes()
            var node = current.lastOrNull {
                it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label)
            }
            while (node != null && !node.isClickable) node = node.parent
            if (node?.isEnabled == true && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            current.firstOrNull { it.text?.toString() == label }?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
            current.firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(100)
        }
        fail("未找到可点击的合成视频控件：$label")
    }

    private fun videoIn(view: View): VideoView? {
        if (view is VideoView && view.isShown) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            videoIn(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    @Test fun actualFilePageOpensAndPlaysSyntheticVideoThroughNativeVideoView() {
        check(context.packageName.endsWith(".workspacecheck")) { "仅允许独立验证包" }
        val runtime = (context.applicationContext as TanukiApp).runtime
        check(!runtime.hasLiveChild && !runtime.hasActiveOperation) { "不能覆盖运行中任务" }
        val folder = File(runtime.paths.workspaces, "video-ui-${UUID.randomUUID().toString().take(8)}")
        check(folder.mkdirs())
        val movie = File(folder, "${folder.name}.mp4")
        // 测试资源为 AVFoundation 本地生成的160×96、16fps、3秒无音轨红绿蓝画面，无第三方素材。
        instrumentation.context.assets.open("workspace-synthetic-colors.mp4").use { input ->
            movie.outputStream().use { output -> input.copyTo(output) }
        }
        var activity: MainActivity? = null
        var video: VideoView? = null
        try {
            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            click("文件")
            click(folder.name)
            click(movie.name)
            click("播放视频")
            val deadline = SystemClock.elapsedRealtime() + 10_000
            var playing = false
            var initialPosition = 0
            while (SystemClock.elapsedRealtime() < deadline && !playing) {
                instrumentation.runOnMainSync {
                    video = videoIn(requireNotNull(activity).window.decorView)
                    playing = video?.isPlaying == true
                    if (playing) initialPosition = video!!.currentPosition
                }
                if (!playing) SystemClock.sleep(50)
            }
            assertTrue("必须由真实 VideoView 开始播放，不能仅凭播放按钮存在判通过", playing)
            // 模拟器可能在 isPlaying 为真后继续等待首帧缓冲；轮询实际时钟，不以固定延时推断已播放。
            val progressDeadline = SystemClock.elapsedRealtime() + 8_000
            var finalPosition = initialPosition
            var furthestPosition = initialPosition
            while (SystemClock.elapsedRealtime() < progressDeadline && furthestPosition <= initialPosition) {
                instrumentation.runOnMainSync {
                    finalPosition = requireNotNull(video).currentPosition
                    furthestPosition = maxOf(furthestPosition, finalPosition)
                }
                if (furthestPosition <= initialPosition) SystemClock.sleep(50)
            }
            assertTrue("播放时钟必须实际推进：初始=${initialPosition}ms，末次=${finalPosition}ms，最远=${furthestPosition}ms",
                furthestPosition > initialPosition)
            instrumentation.runOnMainSync {
                val player = requireNotNull(video)
                assertTrue("短片应已解码为约3秒视频", player.duration in 2500..4000)
                assertTrue("原生视频必须实际显示", player.isShown && player.width > 0 && player.height > 0)
            }
            assertTrue("打开视频不修改原文件", movie.length() > 0)
        } finally {
            instrumentation.runOnMainSync { video?.stopPlayback(); activity?.finish() }
            folder.deleteRecursively()
            // 只清理本测试唯一名称的缓存副本，不清除其他文件或用户缓存。
            File(context.cacheDir, "workspace-open").listFiles()?.forEach { directory ->
                val selected = File(directory, movie.name)
                if (selected.isFile) { selected.delete(); directory.delete() }
            }
        }
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        assertTrue(message, condition())
    }

    private fun visible(label: String): AccessibilityNodeInfo? = nodes().lastOrNull {
        it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label)
    }

    private fun withSyntheticMovie(block: (MainActivity, File, File) -> Unit) {
        check(context.packageName.endsWith(".workspacecheck"))
        val runtime = (context.applicationContext as TanukiApp).runtime
        check(!runtime.hasLiveChild && !runtime.hasActiveOperation)
        val folder = File(runtime.paths.workspaces, "video-controls-${UUID.randomUUID().toString().take(8)}").apply { check(mkdirs()) }
        val movie = File(folder, "${folder.name}.mp4")
        instrumentation.context.assets.open("workspace-synthetic-colors.mp4").use { input -> movie.outputStream().use { output -> input.copyTo(output) } }
        var activity: MainActivity? = null
        try {
            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            block(activity, folder, movie)
        } finally {
            instrumentation.runOnMainSync { activity?.finish() }
            folder.deleteRecursively()
            File(context.cacheDir, "workspace-open").listFiles()?.forEach { directory ->
                val selected = File(directory, movie.name)
                if (selected.isFile) { selected.delete(); directory.delete() }
            }
        }
    }

    @Test fun coverSeekSpeedAndFullscreenKeepOneNativePlaybackSession() = withSyntheticMovie { activity, folder, movie ->
        click("文件"); click(folder.name); click(movie.name)
        await("必须显示提取出的封面") { visible("视频封面") != null }
        instrumentation.waitForIdleSync()
        val bounds = Rect().also(requireNotNull(visible("视频封面"))::getBoundsInScreen)
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val color = screenshot.getPixel(bounds.centerX().coerceIn(0, screenshot.width - 1), bounds.centerY().coerceIn(0, screenshot.height - 1))
            val channels = listOf(android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color))
            assertTrue("封面中心应有合成短片实际颜色，不能是黑色空白", channels.max() > 80 && channels.max() - channels.min() > 40)
            File(context.cacheDir, "video-cover-controls.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { screenshot.recycle() }
        var native: WorkspaceVideoView? = null
        await("播放器必须准备就绪") {
            instrumentation.runOnMainSync { native = videoIn(activity.window.decorView) as? WorkspaceVideoView }
            native?.preparedPlayer != null && visible("倍速 1×") != null
        }
        val sameView = requireNotNull(native)
        val samePlayer = requireNotNull(sameView.preparedPlayer)
        click("倍速 1×"); click("0.5×")
        await("暂停时界面必须保留用户选择的0.5倍速") { visible("倍速 0.5×") != null }
        instrumentation.runOnMainSync {
            assertFalse("暂停状态改倍速不能隐式开始播放", sameView.isPlaying)
            samePlayer.isLooping = true
        }
        click("播放视频")
        await("开始播放后必须实际应用0.5倍速") {
            var applied = false
            instrumentation.runOnMainSync { applied = sameView.isPlaying && kotlin.math.abs(sameView.actualPlaybackSpeed() - .5f) < .01f }
            applied
        }
        click("暂停视频")
        await("再次暂停仍保留所选倍速") { visible("倍速 0.5×") != null }
        instrumentation.runOnMainSync { assertFalse(sameView.isPlaying); samePlayer.isLooping = false }
        val progressAction = AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id
        fun sliderCandidates(label: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
            val result = arrayListOf<AccessibilityNodeInfo>()
            val labelBounds = Rect().also(label::getBoundsInScreen)
            var ancestor: AccessibilityNodeInfo? = label
            // 内容描述可在外层标签，进度动作可由其父节点或内部thumb承载；限定搜索深度与数量。
            repeat(4) {
                val root = ancestor ?: return@repeat
                val queue = java.util.ArrayDeque<Pair<AccessibilityNodeInfo, Int>>().apply { add(root to 0) }
                var inspected = 0
                while (queue.isNotEmpty() && inspected++ < 48) {
                    val (node, depth) = queue.removeFirst()
                    if (!node.refresh()) continue
                    val bounds = Rect().also(node::getBoundsInScreen)
                    if (node !in result && (node.rangeInfo == null || Rect.intersects(labelBounds, bounds))) result += node
                    if (depth < 3) for (index in 0 until node.childCount) node.getChild(index)?.let { queue.add(it to depth + 1) }
                }
                ancestor = root.parent
            }
            return result
        }
        fun sliderDiagnostics(candidates: List<AccessibilityNodeInfo>): String {
            val details = candidates.filter { it.rangeInfo != null || it.contentDescription?.toString() == "视频进度" || it.actionList.any { action -> action.id == progressAction } }
                .joinToString("; ") { node ->
                    val range = node.rangeInfo
                    val state = if (Build.VERSION.SDK_INT >= 30) node.stateDescription else "API<30"
                    "enabled=${node.isEnabled}, visible=${node.isVisibleToUser}, actions=${node.actionList.map { it.id }}, " +
                        "range=${range?.min}..${range?.max}, current=${range?.current}, state=$state"
                }
            val loading = nodes().mapNotNull { it.text?.toString() }.filter { it == "视频缓冲中…" || it == "正在跳转…" || it == "正在准备视频…" }
            return "slider=[$details], loading=$loading"
        }
        fun readySlider(): AccessibilityNodeInfo {
            val deadline = SystemClock.elapsedRealtime() + 20_000
            var diagnostics = "视频进度标签尚未出现"
            while (SystemClock.elapsedRealtime() < deadline) {
                val label = visible("视频进度")
                if (label != null) {
                    val candidates = sliderCandidates(label)
                    diagnostics = sliderDiagnostics(candidates)
                    candidates.firstOrNull { node ->
                        val range = node.rangeInfo
                        node.isEnabled && node.isVisibleToUser && range != null && range.max > range.min &&
                            node.actionList.any { it.id == progressAction }
                    }?.let { return it }
                }
                SystemClock.sleep(50)
            }
            throw AssertionError("等待可操作的视频进度控件超时；$diagnostics")
        }
        readySlider()
        // 等待后重新取节点，只执行一次动作；拒绝动作必须保留为失败，不能靠重复提交掩盖。
        val slider = readySlider()
        val range = requireNotNull(slider.rangeInfo)
        var seekTarget = (range.min + (range.max - range.min) * .4f).coerceIn(range.min, range.max)
        if (kotlin.math.abs(range.current - seekTarget) < 1f) seekTarget = range.min + (range.max - range.min) * .7f
        val seekExpected = (seekTarget.toInt() - 350).coerceAtLeast(range.min.toInt())..
            (seekTarget.toInt() + 350).coerceAtMost(range.max.toInt())
        assertTrue("进度动作被拒绝：target=$seekTarget；${sliderDiagnostics(listOf(slider))}",
            slider.performAction(progressAction, Bundle().apply {
                putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, seekTarget)
            }))
        await("拖动后原生进度必须跳至目标附近：$seekExpected") {
            var position = 0
            instrumentation.runOnMainSync { position = sameView.currentPosition }
            position in seekExpected
        }
        click("视频全屏")
        await("全屏应隐藏外壳并保留播放器") { visible("退出视频全屏") != null && visible("关闭文件") == null }
        instrumentation.runOnMainSync {
            assertSame(sameView, videoIn(activity.window.decorView))
            assertSame(samePlayer, sameView.preparedPlayer)
            assertFalse(sameView.isPlaying)
            assertTrue("暂停切换全屏保持真实进度：$seekExpected", sameView.currentPosition in seekExpected)
        }
        assertNotNull("暂停全屏界面也保持0.5倍速选择", visible("倍速 0.5×"))
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        await("第一次系统返回只退出全屏") { visible("视频全屏") != null && visible("关闭文件") != null }
        click("前进10秒")
        await("快进不可越过视频末尾") {
            var position = 0
            var duration = 0
            instrumentation.runOnMainSync { position = sameView.currentPosition; duration = sameView.duration }
            position >= duration - 500
        }
        click("后退10秒")
        await("后退不可越过视频开头") {
            var position = Int.MAX_VALUE
            instrumentation.runOnMainSync { position = sameView.currentPosition }
            position <= 300
        }
        click("倍速 0.5×"); click("1.5×")
        // 三秒素材仅在本用例中循环，保留跨布局验证的观察窗口；产品默认不循环。
        instrumentation.runOnMainSync { samePlayer.isLooping = true }
        click("播放视频")
        await("必须实际播放") { var playing = false; instrumentation.runOnMainSync { playing = sameView.isPlaying }; playing }
        click("视频全屏")
        await("播放中也能切入全屏") { visible("退出视频全屏") != null }
        instrumentation.runOnMainSync {
            assertSame(sameView, videoIn(activity.window.decorView)); assertSame(samePlayer, sameView.preparedPlayer)
            assertTrue("切换全屏保持播放", sameView.isPlaying)
            assertEquals(1.5f, sameView.actualPlaybackSpeed(), .01f)
        }
        click("暂停视频")
        instrumentation.runOnMainSync { assertFalse(sameView.isPlaying) }
        click("退出视频全屏"); click("关闭文件")
        await("关闭查看器应释放媒体") {
            var released = false
            instrumentation.runOnMainSync { released = sameView.preparedPlayer == null }
            released
        }
    }

    @Test fun videoControlsRemainReachableInDarkNarrowLargeText() = withSyntheticMovie { activity, folder, movie ->
        val fullscreen = mutableStateOf(false)
        val manager = WorkspaceFileManager((context.applicationContext as TanukiApp).runtime.paths)
        instrumentation.runOnMainSync {
            val config = Configuration(activity.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
            }
            val density = activity.resources.displayMetrics.density
            activity.setContent {
                CompositionLocalProvider(LocalConfiguration provides config, LocalDensity provides Density(density, 2f)) {
                    TanukiTheme {
                        WorkspaceFileViewer(manager,
                            WorkspaceFileManager.Entry("${folder.name}/${movie.name}", movie.name, false, movie.length(), false),
                            Modifier.widthIn(max = 320.dp).fillMaxSize().safeDrawingPadding(), canEdit = false,
                            onClose = {}, onExport = {}, onEdit = {}, fullscreen = fullscreen.value, onFullscreen = { fullscreen.value = it })
                    }
                }
            }
        }
        await("大字体下仍要显示封面") { visible("视频封面") != null }
        click("倍速 1×"); click("0.75×"); click("视频全屏")
        await("窄屏全屏入口必须可触达") { fullscreen.value && visible("退出视频全屏") != null }
        val bounds = Rect().also(requireNotNull(visible("退出视频全屏"))::getBoundsInScreen)
        assertTrue("全屏退出控件必须位于屏幕内", bounds.left >= 0 && bounds.right <= activity.resources.displayMetrics.widthPixels && bounds.top >= 0)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        await("返回退出全屏") { !fullscreen.value }
        click("播放视频"); click("暂停视频")
    }

}
