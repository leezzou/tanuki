package com.tanuki

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.tanuki.onboarding.OnboardingRecord
import com.tanuki.onboarding.OnboardingStep
import com.tanuki.onboarding.OnboardingStore
import com.tanuki.storage.FileLocation
import com.tanuki.ui.PreferencesReaderStateStore
import com.tanuki.ui.ReaderSessionState
import com.tanuki.ui.ReaderViewState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 阅读页配置变更回归。
 *
 * 打开一段长文本、关闭行号并滚动后重建 Activity；页面必须回到同一文件、
 * 同一滚动位置，行号开关也保持关闭。使用测试包内的合成文件，不读取用户数据。
 */
@RunWith(AndroidJUnit4::class)
class ReaderConfigChangeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val runtime get() = (context.applicationContext as TanukiApp).runtime
    private val store by lazy { PreferencesReaderStateStore(context) }

    @Before fun prepare() {
        check(context.packageName.endsWith(".workspacecheck")) {
            "Use scripts/workspace-verification.init.gradle for UI test isolation"
        }
        // 让首页直接进入主界面，避免首次启动引导遮住文件入口。
        OnboardingStore(context).save(
            OnboardingRecord(
                lastSeenVersion = BuildConfig.VERSION_NAME,
                completedVersion = BuildConfig.VERSION_NAME,
                completedSteps = OnboardingStep.entries.toSet(),
                bannerDismissed = true,
                upgradeNoticeSeenVersion = BuildConfig.VERSION_NAME,
            )
        )
        store.clear()
        File(runtime.paths.workspaces, FILE_NAME).writeText(
            buildString {
                repeat(6_000) { index ->
                    append("LINE-")
                    append(index.toString().padStart(4, '0'))
                    append(" 阅读位置与配置重建测试内容\n")
                }
            }
        )
    }

    @After fun cleanup() {
        store.clear()
        File(runtime.paths.workspaces, FILE_NAME).delete()
    }

    @Test fun preferenceStoreRoundTripsEncodingOffsetAndLineNumbers() {
        val expected = ReaderSessionState(
            path = "/workspaces/sample.txt",
            name = "sample.txt",
            linked = true,
            location = FileLocation.WORKSPACE,
            view = ReaderViewState(encoding = "GB18030", scrollOffset = 321, showLineNumbers = false,
                pageIndex = 2, offsets = listOf(0L, 65_535L, 131_070L),
                lineStarts = listOf(1, 801, 1_601), resolvedEncoding = "GB18030"),
        )
        store.save(expected)
        assertEquals(expected.normalized(), store.load())
        store.clear()
        assertNull(store.load())
    }

    @Test fun configurationChangeKeepsSameFileScrollOffsetAndLineNumberChoice() {
        val activity = launch()
        click("文件")
        click(FILE_NAME)

        // 默认行号开启；先关闭行号，验证显示偏好也会随配置重建恢复。
        findTextStartingWith("   1  LINE-0000")
        click("行号")
        val text = findTextStartingWith("LINE-0000")
        val scrollable = requireNotNull(scrollableAncestor(text)) { "文本阅读区必须可滚动" }

        repeat(4) {
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(200)
        }
        SystemClock.sleep(700)
        val before = Rect().also(text::getBoundsInScreen)

        // Activity 重建等价于深色切换/字体缩放引发的配置重建；保存状态必须恢复文件与位置。
        instrumentation.runOnMainSync { activity.recreate() }
        waitForRecreated(activity)
        // 若重建后落在首页，重新进入文件页；从 Bundle/偏好恢复的阅读页会继续显示该文件。
        click("文件")

        val restored = findTextStartingWith("LINE-0000")
        assertTrue("配置重建后不应显示行号", restored.text.toString().startsWith("LINE-0000"))
        assertNotNull(find(FILE_NAME))
        val after = Rect().also(restored::getBoundsInScreen)
        assertTrue(
            "配置重建后正文滚动位置应保持：重建前 ${before.top}，重建后 ${after.top}",
            kotlin.math.abs(before.top - after.top) <= 3,
        )

        val saved = requireNotNull(store.load()) { "阅读位置未持久化" }
        assertEquals(FILE_NAME, saved.name)
        assertEquals(FileLocation.WORKSPACE, saved.location)
        assertFalse(saved.view.showLineNumbers)
        assertTrue("滚动偏移应大于 0", saved.view.scrollOffset > 0)
    }

    @Test fun configurationChangeKeepsThirdSegmentAndAllowsGoingBack() {
        val activity = launch()
        click("文件")
        click(FILE_NAME)
        findTextStartingWith("   1  LINE-0000")
        click("行号")
        repeat(2) { index ->
            click("下一段")
            waitForSegment(index + 1)
        }
        val before = requireNotNull(store.load()).view
        val body = nodes().first { it.isVisibleToUser && it.text.toString().length > 1_000 }
            .text.toString()
        assertEquals(2, before.pageIndex)
        assertTrue(before.offsets[2] > 0L)

        // 模拟系统深色或字体配置引起的重建；必须恢复当前分段而不是首段。
        instrumentation.runOnMainSync { activity.recreate() }
        waitForRecreated(activity)
        click("文件")
        findTextStartingWith(body.take(100))
        val after = requireNotNull(store.load()).view
        assertEquals(before.pageIndex, after.pageIndex)
        assertEquals(before.offsets, after.offsets)
        assertEquals(before.lineStarts, after.lineStarts)
        assertEquals(before.resolvedEncoding, after.resolvedEncoding)
        click("上一段")
        waitForSegment(1)
        assertEquals(before.offsets[1], requireNotNull(store.load()).view.offsets[1])
    }

    private fun waitForSegment(index: Int) {
        repeat(100) {
            instrumentation.waitForIdleSync()
            if (store.load()?.view?.pageIndex == index &&
                nodes().any { it.isVisibleToUser && it.text.toString().length > 1_000 }) return
            SystemClock.sleep(100)
        }
        throw AssertionError("未恢复到第 ${index + 1} 段")
    }

    private fun launch(): MainActivity {
        return instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ) as MainActivity
    }

    private fun waitForRecreated(previous: MainActivity) {
        var resumed: MainActivity? = null
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync {
                resumed = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>()
                    .firstOrNull()
            }
            val current = resumed
            if (current != null && current !== previous) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Activity 未完成配置重建")
    }

    private fun nodes(): List<AccessibilityNodeInfo> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptyList()
        if (root.packageName?.toString() != context.packageName) return emptyList()
        val result = ArrayList<AccessibilityNodeInfo>()
        val pending = java.util.ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        while (pending.isNotEmpty() && result.size < 1_500) {
            val node = pending.removeFirst()
            if (!node.refresh()) continue
            result += node
            for (index in 0 until node.childCount) node.getChild(index)?.let(pending::addLast)
        }
        return result
    }

    private fun matches(node: AccessibilityNodeInfo, text: String): Boolean =
        node.text?.toString() == text || node.contentDescription?.toString() == text

    private fun find(text: String): AccessibilityNodeInfo {
        repeat(30) {
            nodes().firstOrNull { matches(it, text) && it.isVisibleToUser }?.let { return it }
            val scrollable = nodes().firstOrNull {
                it.isScrollable && it.actionList.any { action -> action.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD }
            }
            scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            SystemClock.sleep(120)
        }
        throw AssertionError("找不到可见控件：$text")
    }

    private fun findTextStartingWith(prefix: String): AccessibilityNodeInfo {
        repeat(40) {
            nodes().firstOrNull { node ->
                node.isVisibleToUser && node.text?.toString()?.startsWith(prefix) == true
            }?.let { return it }
            SystemClock.sleep(120)
        }
        throw AssertionError("找不到文本：$prefix")
    }

    private fun click(text: String) {
        var node: AccessibilityNodeInfo? = find(text)
        while (node != null && !node.isClickable) node = node.parent
        check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "控件无法点击：$text" }
        SystemClock.sleep(180)
    }

    private fun scrollableAncestor(start: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var node: AccessibilityNodeInfo? = start
        while (node != null) {
            if (node.isScrollable) return node
            node = node.parent
        }
        return null
    }

    private companion object {
        const val FILE_NAME = "qa-reader-config.txt"
    }
}
