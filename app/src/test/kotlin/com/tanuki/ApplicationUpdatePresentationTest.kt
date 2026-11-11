package com.tanuki

import com.tanuki.ui.ApplicationUpdatePresentation
import com.tanuki.update.*
import org.junit.Assert.*
import org.junit.Test

class ApplicationUpdatePresentationTest {
    @Test fun `主体说明是短句而工程约束保留在详情`() {
        assertEquals("只会安装同一来源的更高版本，下载后会校验完整性", ApplicationUpdatePresentation.summary)
        assertFalse(ApplicationUpdatePresentation.summary.contains("SHA-256"))
        assertTrue(ApplicationUpdatePresentation.details.contains("SHA-256"))
        assertFalse(ApplicationUpdatePresentation.message(AppUpdateState()).contains("GitHub"))
    }
    @Test fun `未检查时不声称已是最新`() {
        assertEquals("尚未检查", ApplicationUpdatePresentation.status(AppUpdateState(), ApkUpdateCheckState()).label)
    }
    @Test fun `手动检查结论优先于历史后台结果`() {
        assertEquals("已是最新", ApplicationUpdatePresentation.status(AppUpdateState(AppUpdatePhase.CURRENT),
            ApkUpdateCheckState(available = true)).label)
        assertEquals("正在检查", ApplicationUpdatePresentation.status(AppUpdateState(AppUpdatePhase.CHECKING),
            ApkUpdateCheckState(status = UpdateCheckStatus.SUCCEEDED)).label)
    }
    @Test fun `后台发现新版及成功检查都有明确版本状态`() {
        assertEquals("有新版本", ApplicationUpdatePresentation.status(AppUpdateState(), ApkUpdateCheckState(available = true)).label)
        assertEquals("已是最新", ApplicationUpdatePresentation.status(AppUpdateState(),
            ApkUpdateCheckState(status = UpdateCheckStatus.SUCCEEDED)).label)
        assertEquals("检查未完成", ApplicationUpdatePresentation.status(AppUpdateState(AppUpdatePhase.FAILED),
            ApkUpdateCheckState(status = UpdateCheckStatus.SUCCEEDED)).label)
    }
}
