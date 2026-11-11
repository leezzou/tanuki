package com.tanuki

import java.io.File
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

    @Test fun `文件页空态复用通用三态并显示导入进度`() {
        val files = ui("WorkspaceFilesPage.kt")
        assertTrue("空态应复用 TanukiStateView", files.contains("TanukiStateView("))
        assertTrue("可写空目录应提供从手机导入", files.contains("FileEmptyCommand.IMPORT"))
        assertTrue("导入中应显示进度行", files.contains("正在导入文件"))
        assertTrue("导入进度行复用传输行", files.contains("TransferRow("))
    }
}
