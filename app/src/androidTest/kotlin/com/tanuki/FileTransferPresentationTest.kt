package com.tanuki

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.runtime.RuntimeManager
import com.tanuki.runtime.ServiceState
import com.tanuki.storage.PhoneStorageAccess
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** 真实文件页与系统选择器往返自建文件；权限由宿主在仪器测试进程之外授权并恢复。 */
@SdkSuppress(minSdkVersion = 30)
class FileTransferPresentationTest {
    @get:org.junit.Rule val visibleAccessibility = VisibleAccessibilityRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as TanukiApp
    private val runtime get() = app.runtime
    private fun ui(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun nodes(): List<AccessibilityNodeInfo> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptyList()
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        val result = mutableListOf<AccessibilityNodeInfo>()
        while (queue.isNotEmpty() && result.size < 1500) {
            val node = queue.removeFirst()
            if (!node.refresh()) continue
            result += node
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return result
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue(message, condition())
    }

    private fun label(node: AccessibilityNodeInfo, value: String): Boolean =
        node.text?.toString() == value || node.contentDescription?.toString() == value

    private fun clickNode(node: AccessibilityNodeInfo) {
        var current: AccessibilityNodeInfo? = node
        while (current != null && !current.isClickable) current = current.parent
        assertTrue("真实控件必须可点击", current?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
    }

    private fun appClick(value: String) {
        await("应用应显示所需文件控件") {
            nodes().any { it.packageName?.toString() == context.packageName && it.isVisibleToUser && label(it, value) }
        }
        clickNode(nodes().last { it.packageName?.toString() == context.packageName && it.isVisibleToUser && label(it, value) })
    }

    private fun pickerShown(): Boolean = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
        ?.endsWith(".documentsui") == true

    private fun pickerDownloads() {
        await("应打开真实系统文档选择器", ::pickerShown)
        val home = nodes().firstOrNull {
            it.isVisibleToUser && (it.viewIdResourceName == "android:id/home" ||
                it.contentDescription?.toString() in setOf("Show roots", "显示根目录", "打开导航抽屉", "显示导航菜单"))
        }
        home?.let(::clickNode)
        await("系统文档选择器应提供下载目录") {
            nodes().any { it.isVisibleToUser && (label(it, "Downloads") || label(it, "下载")) }
        }
        clickNode(nodes().first { it.isVisibleToUser && (label(it, "Downloads") || label(it, "下载")) })
    }

    private fun pickerSave(filename: String) {
        pickerDownloads()
        await("系统保存页应显示文件名输入框") { nodes().any { it.isEditable && it.isVisibleToUser } }
        val field = nodes().first { it.isEditable && it.isVisibleToUser }
        assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, filename)
        }))
        await("系统保存页应提供保存按钮") {
            nodes().any { node -> node.isVisibleToUser && node.isEnabled &&
                (node.viewIdResourceName?.endsWith(":id/action_menu_save") == true ||
                    node.text?.toString()?.uppercase() in setOf("SAVE", "保存")) }
        }
        clickNode(nodes().last { node -> node.isVisibleToUser && node.isEnabled &&
            (node.viewIdResourceName?.endsWith(":id/action_menu_save") == true ||
                node.text?.toString()?.uppercase() in setOf("SAVE", "保存")) })
    }

    @Test fun runningFilePageExportsAndImportsThroughTheRealSystemPicker() {
        check(context.packageName.endsWith(".workspacecheck") && context.packageName.matches(Regex("[A-Za-z0-9_.]+")))
        check(!runtime.hasLiveChild && runtime.fileTransfers.state.value.isEmpty()) { "不干扰已有运行或文件任务" }
        check(app.browserController?.state?.value?.overlayFrame == null) { "不干扰已有浏览器小窗" }
        assertTrue("宿主须在启动仪器测试前授权所有文件访问，并在整组测试退出后恢复原模式", PhoneStorageAccess.isGranted(context))
        val original = runtime.state.value
        val filename = "tanuki-saf-${UUID.randomUUID()}.txt"
        val content = "真实文件页SAF往返\nSynthetic file transfer while service state is RUNNING.\n".toByteArray(Charsets.UTF_8)
        val source = File(runtime.paths.workspaces, filename)
        val destination = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename)
        var imported: File? = null
        var activity: MainActivity? = null
        try {
            assertFalse(source.exists())
            assertFalse(destination.exists())
            source.writeBytes(content)
            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            ui { runtime.state.value = runtime.state.value.copy(service = ServiceState.RUNNING) }
            appClick("文件")
            appClick("文件操作")
            appClick("搜索当前文件夹")
            await("文件搜索框应显示") { nodes().any { it.packageName?.toString() == context.packageName && it.isVisibleToUser && it.isEditable } }
            assertTrue(nodes().first { it.packageName?.toString() == context.packageName && it.isVisibleToUser && it.isEditable }
                .performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, filename)
                }))
            appClick("${filename}的操作")
            appClick("导出此文件")
            pickerSave(filename)
            await("MainActivity回调应完成单文件SAF导出") {
                !pickerShown() && runtime.fileTransfers.state.value.isEmpty() &&
                    destination.isFile && destination.readBytes().contentEquals(content)
            }
            assertArrayEquals(content, destination.readBytes())
            assertEquals(ServiceState.RUNNING, runtime.state.value.service)
            assertFalse(runtime.hasLiveChild)

            appClick("文件操作")
            appClick("导入为新项目")
            pickerDownloads()
            await("系统选择器应显示本用例导出的唯一文件") {
                nodes().any { it.isVisibleToUser && label(it, filename) }
            }
            clickNode(nodes().first { it.isVisibleToUser && label(it, filename) })
            await("MainActivity导入回调应原子发布新的项目目录") {
                if (pickerShown() || runtime.fileTransfers.state.value.isNotEmpty()) false
                else {
                    val expectedProject = filename.substringBeforeLast('.')
                    imported = runtime.paths.workspaces.listFiles().orEmpty().firstOrNull { directory ->
                        directory.isDirectory && (directory.name == expectedProject || directory.name.startsWith("$expectedProject (")) &&
                            File(directory, filename).isFile && File(directory, filename).readBytes().contentEquals(content)
                    }
                    imported != null
                }
            }
            assertArrayEquals(content, File(requireNotNull(imported), filename).readBytes())
            assertArrayEquals(content, source.readBytes())
            assertEquals(ServiceState.RUNNING, runtime.state.value.service)
            assertFalse(runtime.hasLiveChild)
        } finally {
            try {
                if (pickerShown()) instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                await("清理前应等待本用例文件回调退出") { runtime.fileTransfers.state.value.isEmpty() }
                imported?.let { directory ->
                    check(directory.parentFile == runtime.paths.workspaces && File(directory, filename).isFile)
                    RuntimeManager.deleteTree(directory)
                }
                if (source.exists()) check(source.delete())
                if (destination.exists()) check(destination.delete())
            } finally {
                ui { runtime.state.value = original; activity?.finish() }
            }
        }
    }
}
