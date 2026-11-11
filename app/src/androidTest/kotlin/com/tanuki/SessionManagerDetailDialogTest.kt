package com.tanuki

import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.ui.DshReadStore
import com.tanuki.ui.DshSessionManagerController
import com.tanuki.ui.DshSessionManagerPage
import com.tanuki.ui.TanukiTheme
import com.tanuki.web.DshAdminSession
import com.tanuki.web.DshCommand
import com.tanuki.web.DshModelCatalog
import com.tanuki.web.DshModelSelection
import com.tanuki.web.DshPermissionCatalog
import com.tanuki.web.DshPermissionOption
import com.tanuki.web.DshSessionAdminSource
import com.tanuki.web.DshSessionSearchResult
import com.tanuki.web.DshSkill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 会话详情里的三条二级确认（重命名/归档/完全权限）。
 *
 * 详情是 Material3 `ModalBottomSheet` 的独立窗口；二级确认必须使用窗口级
 * Dialog，才能画在详情之上并被操作。这里只遍历 `rootInActiveWindow`：如果确认层
 * 仍是 Activity 内的 Box，活动窗口会停在详情，找不到“新标题”等控件。
 *
 * 只使用合成会话与内存假数据；不连接 DSH、不读写用户数据。
 */
@RunWith(AndroidJUnit4::class)
class SessionManagerDetailDialogTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private val session = DshAdminSession(
        id = "synthetic-session",
        title = "评审会话",
        updatedAt = 1_760_000_000_000L,
        running = false,
        blank = false,
        agentAvailable = true,
        asOfSeq = null,
        currentPermission = "read-only",
        model = null,
        cwd = null,
        parentSessionId = null,
        subagent = false,
        formatStatus = null,
    )

    private class FakeReadStore : DshReadStore {
        override fun lastRead(sessionId: String): Long = 0L
        override fun markRead(sessionId: String, updatedAt: Long) = Unit
    }

    private class FakeSource(private val sessions: List<DshAdminSession>) : DshSessionAdminSource {
        override suspend fun sessions(): Result<List<DshAdminSession>> = Result.success(sessions)
        override suspend fun createSession(): Result<String> = Result.success("synthetic-new")
        override suspend fun renameSession(sessionId: String, title: String): Result<String> = Result.success(title)
        override suspend fun archiveSession(sessionId: String, stopActivity: Boolean): Result<List<String>> =
            Result.success(emptyList())
        override suspend fun search(query: String): Result<DshSessionSearchResult> =
            Result.success(DshSessionSearchResult(emptyList(), false))
        override suspend fun models(): Result<DshModelCatalog> =
            Result.success(DshModelCatalog(default = null, groups = emptyList(), failures = emptyList()))
        override suspend fun selectModel(sessionId: String, selection: DshModelSelection): Result<DshModelSelection> =
            Result.success(selection)
        override suspend fun permissions(): Result<DshPermissionCatalog> = Result.success(
            DshPermissionCatalog(
                options = listOf(
                    DshPermissionOption("read-only", "只读", null),
                    DshPermissionOption("workspace-write", "工作区写入", null),
                    DshPermissionOption("danger-full-access", "完全权限", null),
                ),
                defaultOptions = emptyList(),
                defaultPreset = "read-only",
            )
        )
        override suspend fun selectPermission(sessionId: String, preset: String): Result<Unit> = Result.success(Unit)
        override suspend fun skills(sessionId: String): Result<List<DshSkill>> = Result.success(emptyList())
        override suspend fun commands(sessionId: String): Result<List<DshCommand>> = Result.success(emptyList())
    }

    private fun ui(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun launch(): MainActivity {
        check(context.packageName.endsWith(".workspacecheck")) { "Use the isolated verification applicationId" }
        return instrumentation.startActivitySync(
            android.content.Intent(context, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ) as MainActivity
    }

    /** 只取当前活动窗口：被其它窗口盖住的控件不应算“可见”。 */
    private fun nodes(): List<AccessibilityNodeInfo> {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return emptyList()
        if (root.packageName?.toString() != context.packageName) return emptyList()
        val result = arrayListOf<AccessibilityNodeInfo>()
        val queue = java.util.ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (queue.isNotEmpty() && result.size < 1000) {
            val node = queue.removeFirst()
            if (!node.refresh()) continue
            result += node
            for (index in 0 until node.childCount) node.getChild(index)?.let(queue::addLast)
        }
        return result
    }

    private fun matches(node: AccessibilityNodeInfo, label: String) =
        node.text?.toString() == label || node.contentDescription?.toString() == label

    private fun find(label: String): AccessibilityNodeInfo {
        repeat(60) {
            nodes().lastOrNull { matches(it, label) && it.isVisibleToUser }?.let { return it }
            SystemClock.sleep(100)
        }
        error("当前窗口找不到控件：$label；节点=${nodes().joinToString { "${it.text}/${it.contentDescription}" }}")
    }

    private fun awaitGone(label: String) {
        repeat(60) {
            if (nodes().none { matches(it, label) && it.isVisibleToUser }) return
            SystemClock.sleep(100)
        }
        error("控件仍然可见：$label")
    }

    private fun click(label: String) {
        var node: AccessibilityNodeInfo? = find(label)
        while (node != null && !node.isClickable) node = node.parent
        check(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "无法点击：$label" }
        SystemClock.sleep(220)
    }

    private fun tapAt(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, x, y, 0)
            try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
        }
        SystemClock.sleep(220)
    }

    /**
     * 权限选项行只有单选按钮可点，文字本身没有点击语义；按同一行的纵坐标
     * 找到左侧可点击节点再点击，避免依赖控件类名。
     */
    private fun tapRowRadio(label: String) {
        val labelBounds = Rect().also(find(label)::getBoundsInScreen)
        val target = nodes().asSequence()
            .filter { it.isClickable && it.isVisibleToUser }
            .map { it to Rect().also { rect -> it.getBoundsInScreen(rect) } }
            .filter { (_, rect) -> labelBounds.centerY() in rect.top..rect.bottom && rect.centerX() < labelBounds.centerX() }
            .minByOrNull { (_, rect) -> labelBounds.centerX() - rect.centerX() }
        val (_, bounds) = requireNotNull(target) { "找不到 $label 所在行的单选按钮" }
        tapAt(bounds.exactCenterX(), bounds.exactCenterY())
    }

    @Test
    fun detailSecondaryDialogsStayAboveDetailAndCancelKeepsDetail() {
        val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        val controller = DshSessionManagerController(
            source = FakeSource(listOf(session)),
            readStore = FakeReadStore(),
            scope = scope,
        )
        val activity = launch()
        try {
            ui {
                activity.setContent {
                    TanukiTheme {
                        DshSessionManagerPage(
                            controller = controller,
                            running = true,
                            readOnly = false,
                            onBack = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            ui { controller.refresh() }
            click("评审会话")
            find("会话详情")

            // 重命名：返回键只关闭确认层，详情仍在。
            click("重命名会话")
            find("新标题")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            awaitGone("新标题")
            find("会话详情")

            // 重命名：取消后详情仍在。
            click("重命名会话")
            find("新标题")
            click("取消")
            awaitGone("新标题")
            find("会话详情")

            // 归档：取消后详情仍在。
            click("归档会话")
            find("归档")
            click("取消")
            awaitGone("归档")
            find("会话详情")

            // 完全权限：从权限页进入二次确认，取消后回到权限页。
            click("权限模式")
            find("完全权限")
            tapRowRadio("完全权限")
            find("完全权限二次确认")
            click("保持当前权限")
            awaitGone("完全权限二次确认")
            find("当前权限")

            // 详情返回后回到列表：新建入口只在列表窗口可见。
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            find("新建会话")
            assertTrue(controller.state.value.selectedId == null)
        } finally {
            ui { activity.finish() }
            scope.cancel()
        }
    }
}
