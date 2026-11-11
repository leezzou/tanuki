package com.tanuki.update

import com.tanuki.runtime.AppFailure
import com.tanuki.runtime.FailureCatalog
import com.tanuki.runtime.FailureDomain
import com.tanuki.runtime.FailureInput

/**
 * 把 Tanuki 应用更新失败收敛成与运行/安装相同的错误模型。
 *
 * 更新管理器已有自己的状态机；这里只做展示映射，不改变下载、校验或安装流程。
 */
object AppUpdateFailure {
    fun from(state: AppUpdateState): AppFailure? {
        if (state.phase != AppUpdatePhase.FAILED) return null
        return FailureCatalog.describe(
            FailureInput(
                message = state.message,
                operation = "app-update",
                stage = "Tanuki 应用更新",
                domainHint = FailureDomain.UPDATE,
                // app 不是运行组件；用它区分应用更新与应用内版本更新。
                componentId = "app",
                occurredAtMillis = state.startedAt
            )
        )
    }
}
