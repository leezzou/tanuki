package com.tanuki

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 页面不再渲染常驻进度卡的源码契约。
 *
 * JVM 单测没有 Compose 运行时，这里直接锁定页面入口：全局只能调用细进度入口，
 * 旧卡片函数不得重新出现在页面或组件文件里。
 */
class OperationProgressLayoutTest {
    private fun ui(name: String): String = File("src/main/kotlin/com/tanuki/ui/$name").readText()

    @Test fun `全局只保留细进度入口`() {
        val page = ui("TanukiUi.kt")
        assertTrue("全局应接入细进度入口", page.contains("OperationProgressEntry("))
        assertFalse("进度入口不能占用 topBar 的测量高度",
            page.substringAfter("topBar = {").substringBefore("}, bottomBar = {").contains("OperationProgressEntry("))
        assertFalse("进度入口不能占用 bottomBar 的测量高度",
            page.substringAfter("}, bottomBar = {").substringBefore("}) { padding ->").contains("OperationProgressEntry("))
        assertFalse("页面不得再调用常驻进度卡", page.contains("OperationBanner("))

        val progress = ui("OperationProgress.kt")
        assertFalse("旧卡片函数必须移除", progress.contains("fun OperationBanner"))
        assertTrue("入口和抽屉共用连续进度条", progress.contains("OperationProgressBar("))
        assertFalse("进度详情不应再用旧 AlertDialog 卡片", progress.contains("AlertDialog"))
    }

    @Test fun `底部导航由外层统一避让且文件页只有一个进度宿主`() {
        val page = ui("TanukiUi.kt")
        assertTrue(page.contains("bottom = padding.calculateBottomPadding()"))
        assertFalse(page.contains("bottom = if (floatingNavigation) 0.dp"))
        assertTrue(page.contains("val bottomInset = 0.dp"))
        assertEquals("全局进度入口只定义一次", 1, Regex("OperationProgressEntry\\s*\\(").findAll(page).count())
        assertTrue("文件页不得同时渲染外壳进度入口", page.contains("if (tab != 2) progressEntry("))
        assertTrue("仅运行时任务通过文件页宿主显示", page.contains(
            "LocalFileOperationOverlay provides if (hasRuntimeProgress) progressEntry else null"))
        assertFalse(page.contains("showFileTransfers = tab != 2"))

        val files = ui("WorkspaceFilesPage.kt")
        val externalViewer = files.substringAfter("externalDocument?.let { document ->")
            .substringBefore("openedEntry?.let { entry ->")
        val workspaceViewer = files.substringAfter("externalDocument?.let { document ->")
            .substringAfter("openedEntry?.let { entry ->")
            .substringBefore("Surface(pageModifier.fillMaxSize()")
        val list = files.substringAfter("Surface(pageModifier.fillMaxSize()")
            .substringBefore("private fun FileListState(")
        listOf(externalViewer, workspaceViewer, list).forEach { branch ->
            assertEquals("每条文件页面路径只能有一个进度宿主", 1,
                Regex("FileTransferOverlay\\s*\\(").findAll(branch).count())
        }
        listOf(externalViewer, workspaceViewer).forEach { viewer ->
            assertTrue("查看器分支必须提前返回，不能叠加列表宿主", viewer.contains("return@TanukiPageTransitionHost"))
            assertTrue("查看器应上报实测操作栏高度", viewer.contains("onActionBarHeightChanged = { viewerActionBarHeight = it }"))
            assertTrue("进度宿主需按全屏状态和实测操作栏高度避让", viewer.contains(
                "viewerActionBarVisible = !fullscreen, viewerActionBarHeight = viewerActionBarHeight"))
        }
    }

    @Test fun `运行时浮层优先且传输退出缓存不能形成第二个入口`() {
        val overlay = ui("WorkspaceFilesPage.kt").substringAfter("internal fun FileTransferOverlay(")
            .substringBefore("private fun PermissionChip(")
        assertTrue("运行时与传输共用操作栏避让计算", overlay.contains(
            "val progressBottom = FileViewerLayout.transferPillBottomPadding(bottomInset, viewerActionBarVisible, viewerActionBarHeight)"))
        assertTrue("运行时入口使用同一避让高度", overlay.contains(
            "runtimeOverlay(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).padding(bottom = progressBottom))"))
        assertTrue("只有没有运行时浮层时才能显示传输入口", overlay.contains(
            "val pillVisible = runtimeOverlay == null && summary != null"))
        assertTrue("退出动画保留最后一次传输摘要", overlay.contains("lastSummary = summary"))
        assertTrue("退出动画保留最后一次任务标识", overlay.contains("lastIdentity = FileTransferPresentation.motionIdentity(items)"))
        assertTrue("运行时浮层出现时，退出缓存也必须立即停止渲染", overlay.contains(
            "(summary ?: lastSummary)?.takeIf { runtimeOverlay == null }?.let"))
        assertEquals("活动摘要与退出缓存共用一个传输入口", 1, Regex("TransferPill\\s*\\(").findAll(overlay).count())
        assertTrue("退出中的传输入口不可交互", overlay.contains("enabled = pillVisible"))
        assertTrue("退出中的入口不能打开抽屉", overlay.contains("onOpen = { if (pillVisible) sheet = true }"))
        assertTrue("退出中的入口不能取消任务", overlay.contains("onCancel = { if (pillVisible) items.forEach"))
        assertTrue("运行时浮层出现后应关闭传输抽屉", Regex(
            "LaunchedEffect\\(runtimeOverlay != null\\)\\s*\\{\\s*if \\(runtimeOverlay != null\\) sheet = false"
        ).containsMatchIn(overlay))
        assertTrue("传输抽屉与运行时浮层互斥", Regex(
            "TransferSheet\\(\\s*visible = sheet && runtimeOverlay == null,"
        ).containsMatchIn(overlay))
    }

    @Test fun `更新页工程说明收起且检查按钮为主操作`() {
        val card = ui("ApplicationUpdateCard.kt")
        assertTrue(card.contains("TanukiPillButton(text = \"检查更新\""))
        assertTrue(card.contains("TanukiPillButtonTone.Filled"))
        assertTrue(card.contains("if (details)"))
        assertTrue(card.contains("ApplicationUpdatePresentation.summary"))
        assertTrue(card.contains("TanukiStatusDot(versionStatus.tone"))
    }

    @Test fun `主线新增导入与手机传输入口也遵守安装期门禁`() {
        val files = ui("WorkspaceFilesPage.kt")
        assertTrue(files.contains("text = \"＋ 导入\", onClick = ::requestImport"))
        assertTrue(files.substringAfter("fun startPhoneTransfer").substringBefore("busy = true").contains("if (environmentBusy)"))
    }

    @Test fun `文件页空态复用通用三态并显示导入进度`() {
        val files = ui("WorkspaceFilesPage.kt")
        assertTrue("空态应复用 TanukiStateView", files.contains("TanukiStateView("))
        assertTrue("可写空目录应提供从手机导入", files.contains("FileEmptyCommand.IMPORT"))
        assertTrue("导入中应显示进度行", files.contains("正在导入文件"))
        assertTrue("导入进度行复用传输行", files.contains("TransferRow("))
    }
}
