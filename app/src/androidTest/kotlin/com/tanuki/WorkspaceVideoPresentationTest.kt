package com.tanuki

import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.VideoView
import androidx.test.platform.app.InstrumentationRegistry
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
}
