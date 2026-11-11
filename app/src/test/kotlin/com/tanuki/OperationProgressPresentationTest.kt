package com.tanuki

import com.tanuki.runtime.EnvironmentState
import com.tanuki.runtime.HealthState
import com.tanuki.runtime.OperationTiming
import com.tanuki.runtime.RuntimeDownloadProgress
import com.tanuki.runtime.ServiceState
import com.tanuki.runtime.Snapshot
import com.tanuki.runtime.UpdateState
import com.tanuki.ui.OperationProgressMode
import com.tanuki.ui.OperationStage
import com.tanuki.ui.operationFailure
import com.tanuki.ui.operationProgress
import com.tanuki.ui.operationStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationProgressPresentationTest {
    private val now = 1_000_000L

    @Test fun `阶段文案使用下载解压校验安装等短标签`() {
        assertEquals(OperationStage.DOWNLOAD, operationStage("下载 Ubuntu 24.04", EnvironmentState.DOWNLOADING, UpdateState.UNCHECKED))
        // “校验与解压”同时出现时，用耗时最长的解压作为阶段名。
        assertEquals(OperationStage.EXTRACT, operationStage("校验与解压 Node.js", EnvironmentState.INSTALLING, UpdateState.UNCHECKED))
        assertEquals(OperationStage.VERIFY, operationStage("校验 DSH 依赖归档", EnvironmentState.VERIFYING, UpdateState.UNCHECKED))
        assertEquals(OperationStage.INSTALL, operationStage("离线安装 DSH 依赖", EnvironmentState.INSTALLING, UpdateState.UNCHECKED))
        // 括号里的“已校验”不能把下载阶段误标成校验。
        assertEquals(OperationStage.DOWNLOAD, operationStage("下载 DSH 依赖（已校验 1/2）", EnvironmentState.DOWNLOADING, UpdateState.UNCHECKED))
        assertEquals(OperationStage.CHECK, operationStage("解析 DSH 依赖", EnvironmentState.INSTALLING, UpdateState.UNCHECKED))
        assertEquals(OperationStage.CHECK, operationStage("运行检查", EnvironmentState.VERIFYING, UpdateState.UNCHECKED))
        assertEquals(OperationStage.START, operationStage("启动 DSH", EnvironmentState.READY, UpdateState.UNCHECKED))
        assertEquals(OperationStage.CHECK, operationStage("获取官方 DSH 版本列表", EnvironmentState.READY, UpdateState.CHECKING))
    }

    @Test fun `有可信总量时显示当前阶段真实百分比和剩余时间`() {
        val snapshot = Snapshot(
            environment = EnvironmentState.DOWNLOADING,
            stage = "下载 Node.js",
            download = RuntimeDownloadProgress("node", "测试源", 256, 1024, 64),
            timing = OperationTiming(now - 10_000, now),
        )
        val text = operationProgress(snapshot, now, now)!!
        assertTrue(text.active)
        assertEquals(OperationProgressMode.DETERMINATE, text.mode)
        assertEquals(OperationStage.DOWNLOAD, text.stage)
        assertEquals(25, text.percent)
        assertEquals("下载 25%", text.statusText)
        assertEquals("已用 10秒", text.elapsedText)
        assertEquals("预计还需要12秒", text.remainingText)
        assertNull(text.failureTitle)
    }

    @Test fun `阶段计数也使用真实百分比而不是分段条`() {
        val snapshot = Snapshot(
            environment = EnvironmentState.INSTALLING,
            stage = "离线安装 DSH 依赖",
            phasePercent = 42,
            timing = OperationTiming(now - 30_000, now),
        )
        val text = operationProgress(snapshot, now, now)!!
        assertEquals(OperationProgressMode.DETERMINATE, text.mode)
        assertEquals(42, text.percent)
        assertEquals("安装 42%", text.statusText)
        assertEquals(OperationStage.INSTALL, text.stage)
    }

    @Test fun `未知总量只用不定进度和正在准备`() {
        val snapshot = Snapshot(
            environment = EnvironmentState.DOWNLOADING,
            stage = "下载 DSH 依赖",
            download = RuntimeDownloadProgress("dsh", "测试源", 100, -1, 0),
            timing = OperationTiming(now - 20_000, now),
        )
        val text = operationProgress(snapshot, now, now)!!
        assertEquals(OperationProgressMode.INDETERMINATE, text.mode)
        assertNull(text.fraction)
        assertNull(text.percent)
        assertEquals("正在准备", text.statusText)
        assertEquals("预计还需要几分钟", text.remainingText)
    }

    @Test fun `失败态保留原因并提供重试命令`() {
        val text = operationProgress(
            Snapshot(environment = EnvironmentState.FAILED, stage = "离线安装 DSH 依赖", error = "磁盘空间不足",
                failedOperation = com.tanuki.runtime.FailedOperation(7, "安装 DSH 依赖", "磁盘空间不足", 12_000)),
            now,
            now,
        )!!
        assertFalse(text.active)
        assertEquals(OperationProgressMode.FAILED, text.mode)
        assertEquals("操作失败", text.statusText)
        assertEquals("已用 12秒", text.elapsedText)
        assertEquals("磁盘空间不足", text.detail)
        assertFalse(text.canCancel)
        assertTrue(text.canRetry)
        assertEquals("RETRY_OPERATION/7", text.retryCommand)
    }

    @Test fun `更新与服务失败分别重试更新和启动`() {
        assertEquals("RESUME_UPDATE", operationFailure(Snapshot(update = UpdateState.RECOVERY_REQUIRED, error = "校验失败"))!!.retryCommand)
        assertEquals("OPEN_COMPONENTS", operationFailure(Snapshot(update = UpdateState.FAILED))!!.retryCommand)
        assertEquals("START", operationFailure(Snapshot(service = ServiceState.FAILED))!!.retryCommand)
        assertEquals("HEALTH", operationFailure(Snapshot(health = HealthState.FAILED))!!.retryCommand)
        assertNull(operationFailure(Snapshot(environment = EnvironmentState.READY, service = ServiceState.RUNNING)))
    }

    @Test fun `切换与恢复期间不提供会造成半途状态的取消`() {
        val switching = operationProgress(
            Snapshot(environment = EnvironmentState.READY, update = UpdateState.SWITCHING, stage = "切换到 DSH 候选"),
            now,
            now,
        )!!
        assertFalse(switching.canCancel)
        val installing = operationProgress(
            Snapshot(environment = EnvironmentState.INSTALLING, stage = "安装 DSH 依赖"),
            now,
            now,
        )!!
        assertTrue(installing.canCancel)
    }

    @Test fun `长时间没有新进展时给出静默提醒`() {
        val text = operationProgress(
            Snapshot(
                environment = EnvironmentState.INSTALLING,
                stage = "解析 DSH 依赖",
                timing = OperationTiming(now - 180_000, now - 90_000),
            ),
            now,
            now,
        )!!
        val warning = text.quietWarning!!
        assertTrue(warning.contains("没有新进展"))
        assertTrue(warning.contains("1分30秒"))
    }

    @Test fun `失败阶段不再伪装为活动安装且耗时不会继续增长`() {
        val failed = Snapshot(environment = EnvironmentState.INSTALLING,
            failedOperation = com.tanuki.runtime.FailedOperation(8, "解压 Ubuntu", "空间不足", 8_000))
        val first = operationProgress(failed, now, now)!!
        val later = operationProgress(failed, now + 60_000, now)!!
        assertFalse(first.active)
        assertEquals(first.elapsedText, later.elapsedText)
        assertEquals("已用 8秒", later.elapsedText)
    }

    @Test fun `主动取消使用已取消文案并允许重试原任务`() {
        val cancelled = Snapshot(environment = EnvironmentState.INSTALLING,
            failedOperation = com.tanuki.runtime.FailedOperation(9, "安装 DSH", "", 2_000, cancelled = true))
        val text = operationProgress(cancelled, now, now)!!
        assertEquals("操作已取消", text.statusText)
        assertEquals("RETRY_OPERATION/9", text.retryCommand)
        assertFalse(text.active)
        assertTrue(text.canRetry)
    }

    @Test fun `缺少阶段速度时不使用整次操作耗时外推剩余时间`() {
        val preparing = Snapshot(environment = EnvironmentState.INSTALLING, stage = "安装依赖",
            phasePercent = 25, timing = OperationTiming(now - 900_000, now))
        assertEquals("预计还需要几分钟", operationProgress(preparing, now, now)!!.remainingText)
    }

    @Test fun `空闲页面不生成常驻进度入口`() {
        assertNull(operationProgress(Snapshot(environment = EnvironmentState.READY, service = ServiceState.RUNNING), now, now))
    }
}
