package com.tanuki.ui

import com.tanuki.ui.design.TanukiStatusTone
import com.tanuki.update.ApkUpdateCheckState
import com.tanuki.update.AppUpdatePhase
import com.tanuki.update.AppUpdateState
import com.tanuki.update.UpdateCheckStatus

internal data class AppVersionStatus(val label: String, val tone: TanukiStatusTone)

/** 主体只说用户需要知道的结论，安装约束和原始检查信息留在可展开的详情里。 */
internal object ApplicationUpdatePresentation {
    const val summary = "只会安装同一来源的更高版本，下载后会校验完整性"
    const val details = "仅接收本项目同包名、同签名且版本更高的 APK；下载后校验大小和 SHA-256，安装前再次核对。Android 安装器会继续校验签名并等待你的确认。"
    const val dataNotice = "覆盖更新保留已安装的 Ubuntu、Node.js、DSH 和项目。不要卸载旧应用，也不要清除数据；正式签名与开发签名不能相互覆盖。"

    fun status(state: AppUpdateState, background: ApkUpdateCheckState): AppVersionStatus = when {
        state.phase == AppUpdatePhase.CHECKING -> AppVersionStatus("正在检查", TanukiStatusTone.Info)
        state.phase == AppUpdatePhase.CURRENT -> AppVersionStatus("已是最新", TanukiStatusTone.Success)
        state.candidate != null || background.available -> AppVersionStatus("有新版本", TanukiStatusTone.Info)
        state.phase == AppUpdatePhase.NO_RELEASE -> AppVersionStatus("暂无可用更新", TanukiStatusTone.Neutral)
        state.phase == AppUpdatePhase.FAILED -> AppVersionStatus("检查未完成", TanukiStatusTone.Warning)
        background.status == UpdateCheckStatus.SUCCEEDED -> AppVersionStatus("已是最新", TanukiStatusTone.Success)
        background.status == UpdateCheckStatus.CHECKING -> AppVersionStatus("正在检查", TanukiStatusTone.Info)
        background.status == UpdateCheckStatus.FAILED -> AppVersionStatus("检查未完成", TanukiStatusTone.Warning)
        else -> AppVersionStatus("尚未检查", TanukiStatusTone.Neutral)
    }

    fun message(state: AppUpdateState): String = when (state.phase) {
        AppUpdatePhase.IDLE -> "检查是否有新版本。"
        AppUpdatePhase.CHECKING -> "正在检查新版本…"
        AppUpdatePhase.CURRENT -> "你正在使用最新版本。"
        AppUpdatePhase.NO_RELEASE -> "暂时没有可用更新，稍后再来看看。"
        AppUpdatePhase.AVAILABLE -> "发现新版本，可以下载更新。"
        AppUpdatePhase.DOWNLOADING -> "正在下载更新…"
        AppUpdatePhase.VERIFYING -> "正在校验下载文件…"
        AppUpdatePhase.READY -> "更新已准备好，点击安装继续。"
        AppUpdatePhase.FAILED -> "更新没有完成，请稍后重试，或展开详情查看原因。"
        AppUpdatePhase.CANCELLED -> "更新任务已取消，可以重新检查。"
    }
}
