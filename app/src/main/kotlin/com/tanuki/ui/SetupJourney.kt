package com.tanuki.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tanuki.runtime.*
import com.tanuki.ui.design.TanukiButton
import kotlinx.coroutines.delay

/** 首次引导和设置共用一份真实进度；计时只刷新展示，不代表后端取得新进展。 */
@Composable
internal fun rememberSetupJourney(snapshot: Snapshot, components: ComponentInventory, tools: RequiredToolsStatus): SetupJourneyPresentation {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val active = snapshot.operationActive
    val appearedAt = remember(active) { SystemClock.elapsedRealtime() }
    LaunchedEffect(active) {
        now = SystemClock.elapsedRealtime()
        while (active) {
            delay(1000)
            now = SystemClock.elapsedRealtime()
        }
    }
    return setupJourney(snapshot, components, tools, now, appearedAt)
}

@Composable
internal fun SetupJourneyContent(
    journey: SetupJourneyPresentation,
    snapshot: Snapshot,
    canPrepare: Boolean,
    onPrepare: () -> Unit,
    onCommand: (String) -> Unit,
    onCancel: () -> Unit,
    onOpenChat: () -> Unit,
    webViewSupported: Boolean = true,
    conversationReady: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val progress = journey.progress
    var enterAfterStart by remember { mutableStateOf(false) }
    // 只有本页用户主动启动才自动进入；离页后该意图丢弃，不劫持之后的导航。
    LaunchedEffect(journey.phase, conversationReady, webViewSupported, enterAfterStart) {
        if (enterAfterStart && journey.phase == SetupJourneyPhase.RUNNING && conversationReady && webViewSupported) {
            enterAfterStart = false
            onOpenChat()
        } else if (journey.phase in setOf(SetupJourneyPhase.FAILED, SetupJourneyPhase.CANCELLED, SetupJourneyPhase.RECOVERY)) {
            enterAfterStart = false
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(journey.title, style = MaterialTheme.typography.headlineMedium,
            color = if (journey.phase == SetupJourneyPhase.FAILED) colors.error else colors.onSurface,
            modifier = Modifier.semantics { heading() })
        Text(journey.detail, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        if (journey.phase == SetupJourneyPhase.PREPARE) {
            Text("需要联网下载组件，建议使用 Wi-Fi，并预留 4 GB 空间。首次准备耗时取决于网络和手机性能。",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            Text("开始时会请求通知权限，用于显示后台进度；拒绝后仍可继续。浏览手机文件时才会请求文件权限。",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        if (progress?.active == true) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(progress.statusText, style = MaterialTheme.typography.titleSmall)
                    progress.percent?.let { Text("当前阶段 $it%", style = MaterialTheme.typography.labelLarge) }
                }
                OperationProgressBar(progress.fraction, modifier = Modifier.fillMaxWidth(), height = 6.dp)
                Text(progress.elapsedText, style = MaterialTheme.typography.bodyMedium)
                if (progress.stage == OperationStage.DOWNLOAD) {
                    Text(progress.detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    // 只有下载有有效总量与速度样本时才展示预计时间，避免无依据地承诺几分钟。
                    snapshot.download?.takeIf { it.total > it.received && it.received > 0 && it.bytesPerSecond > 0 }
                        ?.let { remainingText(snapshot, progress.fraction) }?.let {
                            Text("本次下载：$it", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                }
                progress.quietWarning?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error) }
            }
            Text("可以离开此页，准备会继续；回来后会显示当前状态。", style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant)
            if (progress.canCancel) TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("停止准备")
            }
        }
        journey.action?.let { label ->
            TanukiButton(
                onClick = {
                    when {
                        journey.phase == SetupJourneyPhase.RUNNING -> onOpenChat()
                        journey.command != null -> {
                            enterAfterStart = journey.command == "START"
                            onCommand(journey.command)
                        }
                        else -> onPrepare()
                    }
                },
                enabled = (journey.phase !in setOf(SetupJourneyPhase.PREPARE, SetupJourneyPhase.CANCELLED, SetupJourneyPhase.FAILED) ||
                    journey.command != null || canPrepare) &&
                    (webViewSupported || (journey.command != "START" && journey.phase != SetupJourneyPhase.RUNNING)) &&
                    (journey.phase != SetupJourneyPhase.RUNNING || conversationReady),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) { Text(when {
                journey.phase == SetupJourneyPhase.RUNNING && !conversationReady -> "等待对话连接"
                journey.command == "START" -> "启动并进入对话"
                else -> label
            }) }
        }
        journey.notice?.let { notice ->
            Text(notice, style = MaterialTheme.typography.bodyMedium, color = colors.error)
            if (snapshot.error.isNotBlank()) FailureTechnicalDetails(snapshot.error)
        }
        if (journey.phase in setOf(SetupJourneyPhase.FAILED, SetupJourneyPhase.CANCELLED, SetupJourneyPhase.RECOVERY)) {
            progress?.elapsedText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            val details = snapshot.error.ifBlank { progress?.detail.orEmpty() }
            if (details.isNotBlank() && details != journey.detail) FailureTechnicalDetails(details)
        }
    }
}

/** 组件清单说明实际依赖关系，不把下载/解压等重复子阶段画成整次安装的完成刻度。 */
@Composable
internal fun SetupDependencySummary(components: ComponentInventory, tools: RequiredToolsStatus) {
    val toolsPassed = tools.passed && tools.ubuntuMarker.startsWith("${components.ubuntuVersion} ") && components.nodeInstalled
    val items = listOf("系统组件" to components.ubuntuInstalled, "运行组件" to components.nodeInstalled,
        "基础工具" to toolsPassed, "助手" to components.dshInstalled)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("准备内容", style = MaterialTheme.typography.titleSmall)
        items.forEach { (title, complete) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(if (complete) "已安装" else "待准备", style = MaterialTheme.typography.bodyMedium,
                    color = if (complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
