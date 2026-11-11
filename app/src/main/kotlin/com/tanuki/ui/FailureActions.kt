package com.tanuki.ui

import com.tanuki.runtime.AppFailure
import com.tanuki.runtime.FailureActionKind
import com.tanuki.runtime.FailureDomain

/** 所有错误入口共用动作分发；缺少版本列表时先获取，重试携带原命令与目标。 */
internal fun performFailureAction(
    failure: AppFailure,
    kind: FailureActionKind,
    callbacks: TanukiUiCallbacks,
    onOpenStorage: () -> Unit,
    onViewVersions: () -> Unit,
    versionsAvailable: Boolean = true,
    onSwitchSource: () -> Unit = {}
) {
    when (kind) {
        FailureActionKind.RETRY -> when {
            failure.retry != null -> callbacks.retryFailure(failure.retry)
            failure.retryCommand != null -> callbacks.command(failure.retryCommand)
            failure.domain == FailureDomain.UPDATE -> callbacks.checkUpdates()
            failure.domain == FailureDomain.RUNTIME -> callbacks.command("START")
            else -> Unit
        }
        FailureActionKind.VIEW_VERSIONS -> {
            if (failure.componentId == "dsh" && !versionsAvailable) callbacks.checkUpdates()
            else onViewVersions()
        }
        FailureActionKind.SWITCH_SOURCE -> onSwitchSource()
        FailureActionKind.CLEAN_CACHE -> callbacks.command("CACHE")
        FailureActionKind.OPEN_STORAGE -> onOpenStorage()
        FailureActionKind.OPEN_SETTINGS -> callbacks.appSettings()
        FailureActionKind.RESTART -> callbacks.command("RESTART")
        FailureActionKind.SAFE_MODE -> callbacks.command("RECOVERY_SAFE")
        FailureActionKind.TRY_NORMAL -> callbacks.command("RECOVERY_TRIAL")
    }
}
