package com.tanuki

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import com.tanuki.ui.design.TanukiColorTokens
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.ui.DshMarkdown
import com.tanuki.ui.DshToolCard
import com.tanuki.web.DshToolItem
import com.tanuki.web.DshToolStatus
import com.tanuki.web.DshFileDiff
import com.tanuki.web.DshRiskAssessment
import com.tanuki.web.DshRiskLevel
import com.tanuki.ui.DshQuestionCard
import com.tanuki.ui.TanukiCopyButton
import com.tanuki.ui.design.TanukiDesignTheme
import com.tanuki.web.DshQuestionParser
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 只挂载合成组件，不连接 DSH；必须由独立 QA 在专用 workspacecheck 设备执行。 */
@RunWith(AndroidJUnit4::class)
class DshChatMotionPresentationTest {
    @get:org.junit.Rule val visibleAccessibility = VisibleAccessibilityRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun ui(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun mount(reduced: Boolean = false, content: @Composable () -> Unit): MainActivity {
        check(context.packageName.endsWith(".workspacecheck"))
        val app = context.applicationContext as TanukiApp
        check(!app.runtime.hasLiveChild) { "不干扰真实运行任务" }
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        ui { activity.setContent { TanukiDesignTheme(dark = false, reducedMotion = reduced) {
            Column(Modifier.background(MaterialTheme.colorScheme.background).padding(24.dp)) { content() }
        } } }
        instrumentation.waitForIdleSync()
        return activity
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptyList()
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        val result = mutableListOf<AccessibilityNodeInfo>()
        while (pending.isNotEmpty() && result.size < 1500) {
            val node = pending.removeFirst()
            if (!node.refresh()) continue
            if (node.packageName?.toString() == context.packageName && node.isVisibleToUser) result += node
            for (index in 0 until node.childCount) node.getChild(index)?.let(pending::addLast)
        }
        return result
    }

    private fun find(label: String): AccessibilityNodeInfo {
        val deadline = SystemClock.elapsedRealtime() + 5000
        do {
            nodes().lastOrNull { it.text?.toString() == label || it.contentDescription?.toString() == label }
                ?.let { return it }
            SystemClock.sleep(20)
        } while (SystemClock.elapsedRealtime() < deadline)
        error("未找到合成控件：$label")
    }

    private fun button(label: String): AccessibilityNodeInfo {
        var node = find(label)
        while (!node.isClickable && node.parent != null) node = node.parent
        return node
    }

    private fun click(label: String) {
        assertTrue(button(label).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    @Test fun repeatedCopyRestartsConfirmationAndKeepsButtonWidth() {
        var copies = 0
        // reduced 也必须保留确认文字和计时；该用例不依赖视觉淡化是否完成。
        val activity = mount(reduced = true) { TanukiCopyButton("复制代码", onCopy = { copies++ }) }
        try {
            val before = Rect().also(button("复制代码")::getBoundsInScreen)
            click("复制代码")
            find("已复制")
            SystemClock.sleep(950)
            click("已复制")
            SystemClock.sleep(700) // 已超过第一次 1500ms，但未到第二次截止时间。
            val after = Rect().also(button("已复制")::getBoundsInScreen)
            assertEquals(before.width(), after.width())
            assertEquals(2, copies)
            // 系统无障碍推荐超时可能更长，因此最终复位等待上限由测试设备配置决定。
            find("复制代码")
        } finally { ui { activity.finish() } }
    }

    @Test fun selectingImmediatelyEnablesSubmitAndSubmittingImmediatelyDisablesIt() {
        val request = DshQuestionParser.parse("motion-event", "synthetic-session",
            JSONObject().put("questions", JSONArray().put(JSONObject().put("id", "q")
                .put("question", "选择验证项").put("options", JSONArray().put("构建")))))!!
        val submitting = mutableStateOf(false)
        val activity = mount { DshQuestionCard(request, submitting = submitting.value, onSubmit = {}) }
        try {
            assertFalse(button("提交").isEnabled)
            click("构建")
            // 不等待 160ms 选中色动画，组合提交后 enabled 就应为真。
            assertTrue(button("提交").isEnabled)
            ui { submitting.value = true }
            instrumentation.waitForIdleSync()
            assertFalse(button("提交中…").isEnabled)
        } finally { ui { activity.finish() } }
    }

    @Test fun thousandsOfDiffLinesStayInsideOneBoundedViewport() {
        val diff = mutableStateOf(DshFileDiff("synthetic.txt", null,
            (1..4000).joinToString("\n") { "合成行 $it" }))
        val tool = DshToolItem(1, 1L, "synthetic-tool", "edit", null, DshToolStatus.SUCCESS,
            null, null, null, null, emptyList(), emptyList(), false,
            DshRiskAssessment(DshRiskLevel.LOW, "合成测试"))
        val activity = mount { DshToolCard(tool.copy(diffs = listOf(diff.value))) }
        try {
            val density = context.resources.displayMetrics.density
            fun viewportHeight() = Rect().also(find("diff 内容区域")::getBoundsInScreen).height()
            assertTrue(viewportHeight() <= 240 * density + 1)
            val expand = nodes().first { it.text?.toString()?.startsWith("展开完整 diff") == true }
            assertTrue(expand.performAction(AccessibilityNodeInfo.ACTION_CLICK) ||
                expand.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            SystemClock.sleep(350)
            val height = viewportHeight()
            assertTrue(height <= 480 * density + 1)
            assertTrue(height > 240 * density)
            ui { diff.value = diff.value.copy(newText = diff.value.newText + "\n新增合成结果") }
            instrumentation.waitForIdleSync()
            assertEquals(height, viewportHeight())
        } finally { ui { activity.finish() } }
    }

    @Test fun staticMarkdownNeverShowsStreamingCursor() {
        val activity = mount(reduced = true) { DshMarkdown("合成静态消息") }
        try {
            find("合成静态消息")
            assertFalse(nodes().any { it.contentDescription?.toString() == "正在输出" })
        } finally { ui { activity.finish() } }
    }

    @Test fun reducedStreamingCursorRemainsBrightAtDifferentPulsePhases() {
        val activity = mount(reduced = true) { DshMarkdown("合成流式文本", streaming = true) }
        try {
            val bounds = Rect().also(find("正在输出")::getBoundsInScreen)
            fun pixels(): IntArray {
                val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                return try {
                    IntArray(bounds.width() * bounds.height()).also {
                        image.getPixels(it, 0, bounds.width(), bounds.left, bounds.top, bounds.width(), bounds.height())
                    }
                } finally { image.recycle() }
            }
            val first = pixels()
            assertTrue("reduced 光标必须以不透明前景色常亮，不能用不可见冒充静止",
                first.any { it == TanukiColorTokens.Light.textPrimary.toInt() })
            SystemClock.sleep(1200)
            assertArrayEquals(first, pixels())
            SystemClock.sleep(600)
            assertArrayEquals(first, pixels())
        } finally { ui { activity.finish() } }
    }
}
