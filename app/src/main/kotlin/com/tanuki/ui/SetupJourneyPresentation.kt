package com.tanuki.ui

import com.tanuki.runtime.ComponentInventory
import com.tanuki.runtime.EnvironmentState
import com.tanuki.runtime.RequiredToolsStatus
import com.tanuki.runtime.ServiceState
import com.tanuki.runtime.Snapshot
import com.tanuki.runtime.UpdateState
import com.tanuki.runtime.operationActive
import com.tanuki.runtime.displayFailure

/** 安装完整与服务就绪分别判断，不能用 Ubuntu 的 READY 代替整条准备链完成。 */
internal enum class SetupJourneyPhase { PREPARE, WORKING, FAILED, CANCELLED, READY, RUNNING, RECOVERY }

internal data class SetupJourneyPresentation(
    val phase: SetupJourneyPhase,
    val title: String,
    val detail: String,
    val action: String?,
    val command: String? = null,
    val progress: OperationProgressText? = null,
    val componentsComplete: Boolean = false,
    val notice: String? = null,
)

internal fun setupComponentsComplete(components: ComponentInventory, tools: RequiredToolsStatus): Boolean =
    components.ubuntuInstalled && components.nodeInstalled && components.dshInstalled &&
        tools.passed && tools.ubuntuMarker.startsWith("${components.ubuntuVersion} ")

/** 只消费后端快照；页面重建不会触发安装、启动或自动跳转。 */
internal fun setupJourney(
    snapshot: Snapshot,
    components: ComponentInventory,
    tools: RequiredToolsStatus,
    nowMillis: Long,
    appearedAtMillis: Long,
): SetupJourneyPresentation {
    val complete = setupComponentsComplete(components, tools) && snapshot.environment == EnvironmentState.READY
    val progress = operationProgress(snapshot, nowMillis, appearedAtMillis)
    if (snapshot.operationActive) return SetupJourneyPresentation(
        SetupJourneyPhase.WORKING,
        when (snapshot.service) {
            ServiceState.STARTING -> "正在启动助手"
            ServiceState.STOPPING -> "正在停止助手"
            else -> "正在准备本机环境"
        },
        progress?.stageText ?: snapshot.stage,
        null,
        progress = progress,
        componentsComplete = complete,
    )
    if (snapshot.update == UpdateState.RECOVERY_REQUIRED) return SetupJourneyPresentation(
        SetupJourneyPhase.RECOVERY, "先确认上次更新", "当前版本需要检查后才能启动。已有项目和对话会保留。",
        "检查当前版本", "RESUME_UPDATE", progress, complete,
    )
    val failure = snapshot.displayFailure()
    // 获取新版本失败不影响已经安装的版本；保留错误提示，但不阻断本机启动与对话。
    if (complete && snapshot.update == UpdateState.CHECK_FAILED &&
        snapshot.service !in setOf(ServiceState.FAILED, ServiceState.INTERRUPTED) && snapshot.failedOperation == null) {
        val running = snapshot.service == ServiceState.RUNNING && snapshot.webHealthy
        return SetupJourneyPresentation(
            if (running) SetupJourneyPhase.RUNNING else SetupJourneyPhase.READY,
            if (running) "助手已启动" else "运行环境已准备好",
            "当前已安装版本可以继续使用。",
            if (running) "进入对话" else if (snapshot.service == ServiceState.RUNNING) "检查运行环境" else "启动助手",
            if (running) null else if (snapshot.service == ServiceState.RUNNING) "HEALTH" else "START",
            componentsComplete = true,
            notice = failure?.reason ?: "未能检查新版本，请稍后在组件设置中重试。",
        )
    }
    if (progress?.mode == OperationProgressMode.FAILED || failure != null || snapshot.error.isNotBlank()) {
        val cancelled = snapshot.failedOperation?.cancelled == true || snapshot.environment == EnvironmentState.CANCELLED
        val retry = (progress?.retryCommand ?: "OPEN_COMPONENTS").takeUnless { cancelled && it == "OPEN_COMPONENTS" }
        return SetupJourneyPresentation(
            if (cancelled) SetupJourneyPhase.CANCELLED else SetupJourneyPhase.FAILED,
            if (cancelled) "准备已停止" else failure?.title ?: progress?.failureTitle ?: "准备未完成",
            if (cancelled) "已完成的组件会保留；再次准备时会检查并复用可用下载。"
            else failure?.reason ?: com.tanuki.onboarding.OnboardingFailure.describe(progress?.detail ?: snapshot.error),
            if (retry == "OPEN_COMPONENTS") "组件与下载设置" else if (retry != null) "重试" else "重新检查并准备",
            retry,
            progress,
            complete,
        )
    }
    if (complete && snapshot.service == ServiceState.RUNNING && snapshot.webHealthy) return SetupJourneyPresentation(
        SetupJourneyPhase.RUNNING, "助手已启动", "进入对话后即可描述任务；模型连接状态会在对话中显示。",
        "进入对话", componentsComplete = true,
    )
    if (complete && snapshot.service == ServiceState.RUNNING) return SetupJourneyPresentation(
        SetupJourneyPhase.READY, "助手连接尚未就绪", "服务仍在运行，请检查连接状态后再进入对话。",
        "检查运行环境", "HEALTH", componentsComplete = true,
    )
    if (complete) return SetupJourneyPresentation(
        SetupJourneyPhase.READY, "运行环境已准备好", "启动助手后即可进入对话，无需重新安装组件。",
        "启动助手", "START", componentsComplete = true,
    )
    return SetupJourneyPresentation(
        SetupJourneyPhase.PREPARE, "准备本机环境", "自动检查并补齐系统、运行组件、基础工具和助手。已有组件会保留。",
        "开始准备",
    )
}
