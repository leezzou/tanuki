package com.tanuki.ui

import com.tanuki.ui.design.TanukiOutlinedButton as OutlinedButton

import com.tanuki.ui.design.TanukiButton as Button

import com.tanuki.power.DeviceCondition
import com.tanuki.power.DeviceConditionState

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tanuki.runtime.*
import com.tanuki.ui.design.TanukiPillButton
import com.tanuki.ui.design.TanukiPillButtonTone

@Composable
internal fun SetupOverview(model:TanukiUiModel,c:TanukiUiCallbacks,openDsh:()->Unit,onOpenStorage:()->Unit={}) {
    val s=model.snapshot
    val comp=model.components
    val toolsPassed=model.tools.passed && model.tools.ubuntuMarker.startsWith("${comp.ubuntuVersion} ") && comp.nodeInstalled
    val complete=listOf(comp.ubuntuInstalled,comp.nodeInstalled,toolsPassed,comp.dshInstalled)
    var step by rememberSaveable { mutableIntStateOf(complete.indexOfFirst { !it }.takeIf { it>=0 } ?: 3) }
    var showSteps by rememberSaveable { mutableStateOf(false) }
    val ready=complete.all { it } && s.environment==EnvironmentState.READY
    if(ready && !showSteps) {
        ReadyWorkspace(model,c,openDsh,onShowSteps={showSteps=true},onOpenStorage=onOpenStorage)
        return
    }
    BackHandler(enabled=step>0 || (ready && showSteps)) {
        if(step>0)step-- else showSteps=false
    }
    if(ready)TextButton(onClick={showSteps=false}) { Text("返回工作台") }
    LaunchedEffect(step,s.setupIdle,comp.selectedUbuntuVersion,comp.selectedNodeVersion,comp.ubuntuAutomatic,comp.nodeAutomatic) {
        if(s.setupIdle) {
            if(step==0 && comp.ubuntuAutomatic && !comp.ubuntuInstalled && model.ubuntuSpeed.measuredAtMillis==0L && !model.ubuntuSpeed.running)c.testSources(RuntimeComponent.UBUNTU)
            if(step==1 && comp.nodeAutomatic && !comp.nodeInstalled && model.nodeSpeed.measuredAtMillis==0L && !model.nodeSpeed.running)c.testSources(RuntimeComponent.NODE)
            if(step==3 && model.versions.isEmpty())c.checkUpdates()
        }
    }
    val steps=listOf(
        SetupGuideStep("ubuntu","系统组件","为助手准备系统组件，请先完成安装。",complete[0]),
        SetupGuideStep("node","运行组件","为助手准备运行组件，请继续安装。",complete[1]),
        SetupGuideStep("tools","基础工具","检查基础工具，只安装缺失的组件。",complete[2]),
        SetupGuideStep("dsh","助手","安装后设置模型，即可开始对话。",complete[3]))
    val prerequisite=when {
        step>0 && !comp.ubuntuInstalled -> 0
        step>1 && !comp.nodeInstalled -> 1
        step==3 && !toolsPassed -> 2
        else -> null
    }
    if(model.support.state==com.tanuki.web.WebViewSupportState.UPDATE_REQUIRED)WebViewCard(model.support,s.setupIdle,c)
    SetupGuide(steps,step,onStep={step=it},busy=s.setupBusy,
        isReady=ready,
        onOpenDsh={if(s.service!=ServiceState.RUNNING)c.command("START");openDsh()}) {
        if(s.service==ServiceState.RUNNING)SetupCaption("请先停止助手再安装或更换组件；停止会中断当前任务。")
        if(prerequisite!=null)SetupPanel {
            Text("先完成 ${steps[prerequisite].title}",style=MaterialTheme.typography.titleMedium)
            SetupCaption("请先完成前一步安装；查看其他步骤不会中断安装。")
            OutlinedButton(onClick={step=prerequisite},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("返回上一步")
            }
        } else if(step==2)FoundationTools(model,c) else EnvironmentSection(model,c,step,onOpenStorage)
        s.download?.takeIf { it.component!=steps[step].id }?.let { progress ->
            SetupPanel {
                Text("安装仍在继续",style=MaterialTheme.typography.titleMedium)
                ComponentDownloadProgress(ComponentProgressModel(s.stage,progress.source,progress.received,
                    progress.total,progress.bytesPerSecond,progress.reused))
                TextButton(onClick={step=steps.indexOfFirst { it.id==progress.component }.takeIf { it>=0 } ?: step}) {
                    Text("查看安装进度")
                }
            }
        }
        if(step==3 && comp.dshInstalled)SandboxGuidance(s)
    }
    DeviceConditionBanner(model.deviceCondition)
    OperationCard(s,c,onOpenStorage)
    RuntimeRecoveryCard(s,c,model.components,onOpenStorage)
    RecoveryGuidance(s,c)
}

@Composable
private fun RecoveryGuidance(s:Snapshot,c:TanukiUiCallbacks) {
    if(s.update==UpdateState.RECOVERY_REQUIRED)SetupPanel {
        Text("上次更新需要确认",style=MaterialTheme.typography.titleMedium)
        SetupCaption("请检查当前版本，或恢复对应的数据备份后继续。")
        TextButton(onClick={c.command("RESUME_UPDATE")},enabled=s.setupIdle) { Text("检查当前版本") }
    }
}


@Composable
internal fun RuntimeRecoveryCard(
    s: Snapshot,
    c: TanukiUiCallbacks,
    components: ComponentInventory = ComponentInventory(),
    onOpenStorage: () -> Unit = {}
) {
    val recovery = s.recovery
    val activeRuntimeFailure = s.displayFailure()?.takeIf { it.domain == FailureDomain.RUNTIME }
    // 正常模式且服务已恢复时，旧失败不再占用页面；重启后仍保留上次失败供排查。
    if (recovery.mode == RecoveryMode.NORMAL && s.service == ServiceState.RUNNING) return
    if (recovery.mode == RecoveryMode.NORMAL && recovery.lastFailure == null && activeRuntimeFailure == null) return
    val fallback = recovery.lastFailure?.let {
        FailureCatalog.fromRuntimeFailure(it, recovery, s.error, recovery.lastFailureAtMillis, recovery.restartAttempts)
    }
    val failure = activeRuntimeFailure ?: fallback
    var showVersions by rememberSaveable { mutableStateOf(false) }

    fun runAction(kind: FailureActionKind) {
        when (kind) {
            FailureActionKind.RETRY, FailureActionKind.RESTART -> c.command("START")
            FailureActionKind.TRY_NORMAL -> c.command("RECOVERY_TRIAL")
            FailureActionKind.SAFE_MODE -> c.command("RECOVERY_SAFE")
            FailureActionKind.CLEAN_CACHE -> c.command("CACHE")
            FailureActionKind.OPEN_STORAGE -> onOpenStorage()
            FailureActionKind.OPEN_SETTINGS -> c.appSettings()
            else -> Unit
        }
    }

    SetupPanel {
        Text(
            when (recovery.mode) {
                RecoveryMode.SAFE -> "正在使用安全模式"
                RecoveryMode.PROBATION -> "正在试用正常模式"
                RecoveryMode.NORMAL -> failure?.title ?: "恢复助手"
            },
            style = MaterialTheme.typography.titleMedium
        )
        if (failure != null) {
            Text(failure.reason, style = MaterialTheme.typography.bodyMedium)
            if (recovery.lastFailureAtMillis > 0L) {
                SetupCaption("上次发生在 ${recoveryTime(recovery.lastFailureAtMillis)}" +
                    if (recovery.restartAttempts > 0) " · 已自动重试 ${recovery.restartAttempts} 次" else "")
            }
            TanukiPillButton(
                text = failure.primaryAction.label,
                onClick = { runAction(failure.primaryAction.kind) },
                enabled = !s.setupBusy,
                tone = TanukiPillButtonTone.Filled,
                modifier = Modifier.fillMaxWidth()
            )
            failure.secondaryAction?.let { secondary ->
                TanukiPillButton(
                    text = secondary.label,
                    onClick = { runAction(secondary.kind) },
                    enabled = !s.setupBusy,
                    tone = TanukiPillButtonTone.Outline,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        when (recovery.mode) {
            RecoveryMode.SAFE -> {
                SetupCaption("安全模式不加载你的插件和设置；对话和工作区保留，请手动试用正常模式。")
                if (s.update == UpdateState.RESTORED_PENDING_CHECK) SetupCaption("恢复检查尚未完成，请在正常模式启动助手。")
                if (failure == null || failure.primaryAction.kind != FailureActionKind.TRY_NORMAL) {
                    TanukiPillButton(
                        text = "试用正常模式",
                        onClick = { c.command("RECOVERY_TRIAL") },
                        enabled = !s.setupBusy,
                        tone = TanukiPillButtonTone.Filled,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                SetupCaption("两分钟内再次失败会回到安全模式；中断任务请确认状态后再继续。")
            }
            RecoveryMode.PROBATION -> {
                SetupCaption(if (s.service == ServiceState.RUNNING) "正在加载插件和设置；两分钟内再失败会回到安全模式，中断任务请手动继续。" else "正常模式试用已中断，请重新启动或返回安全模式；中断任务请手动继续。")
                if (failure == null || failure.primaryAction.kind != FailureActionKind.SAFE_MODE) {
                    TanukiPillButton(
                        text = "返回安全模式",
                        onClick = { c.command("RECOVERY_SAFE") },
                        enabled = !s.setupBusy,
                        tone = TanukiPillButtonTone.Outline,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            RecoveryMode.NORMAL -> {
                SetupCaption(if (s.service == ServiceState.RUNNING) "助手已正常运行，请确认中断任务的状态后再继续。" else "助手尚未运行，请查看当前状态后重新启动；中断任务请手动继续。")
            }
        }

        // 恢复卡始终保留清理与换版本入口，用户不用回工作台找组件维护。
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (failure?.primaryAction?.kind != FailureActionKind.CLEAN_CACHE &&
                failure?.secondaryAction?.kind != FailureActionKind.CLEAN_CACHE
            ) {
                TanukiPillButton(
                    text = "清理下载缓存",
                    onClick = { c.command("CACHE") },
                    enabled = !s.setupBusy,
                    tone = TanukiPillButtonTone.Outline,
                    modifier = Modifier.weight(1f)
                )
            }
            TanukiPillButton(
                text = "切换版本",
                onClick = { showVersions = true },
                enabled = !s.setupBusy,
                tone = TanukiPillButtonTone.Outline,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showVersions) DshVersionManagerDialog(
        activeVersion = s.dsh,
        idle = s.setupIdle,
        inventoryKey = components.installedDshVersions,
        load = c.loadInstalledDsh,
        onSwitch = c.switchInstalledDsh,
        onUninstall = c.uninstallInstalledDsh,
        onDismiss = { showVersions = false })
}

private fun recoveryTime(millis: Long): String = runCatching {
    java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
}.getOrDefault("较早时间")

@Composable
private fun ReadyWorkspace(model:TanukiUiModel,c:TanukiUiCallbacks,openDsh:()->Unit,onShowSteps:()->Unit,onOpenStorage:()->Unit={}) {
    val s=model.snapshot
    var showPermissions by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("你的开发工作台",style=MaterialTheme.typography.headlineLarge)
        SetupCaption("管理本机工作区，告诉助手你想完成的事。")
    }
    RecoveryGuidance(s,c)
    RuntimeRecoveryCard(s,c,model.components,onOpenStorage)
    if(model.support.state==com.tanuki.web.WebViewSupportState.UPDATE_REQUIRED)WebViewCard(model.support,s.setupIdle,c)
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(tanukiHeroBrush()),
    ) {
        Column(Modifier.padding(28.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surface.copy(alpha=0.72f)) {
                    Box(Modifier.size(52.dp),contentAlignment=Alignment.Center) {
                        TanukiIcon(TanukiGlyph.Terminal,tint=MaterialTheme.colorScheme.primary)
                    }
                }
                StatusPill(s.service.label,s.service==ServiceState.RUNNING)
            }
            Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("助手",style=MaterialTheme.typography.headlineMedium)
                Text("版本 ${s.dsh}",style=MaterialTheme.typography.bodyMedium,
                    fontFamily=FontFamily.Monospace,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick={if(s.service!=ServiceState.RUNNING)c.command("START");openDsh()},
                enabled=!s.setupBusy && s.update!=UpdateState.RECOVERY_REQUIRED &&
                    model.support.state!=com.tanuki.web.WebViewSupportState.UPDATE_REQUIRED,
                shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                Text(if(s.service==ServiceState.RUNNING)"打开对话" else if(s.service==ServiceState.STARTING)"正在启动…" else "启动助手")
            }
        }
    }
    Surface(
        onClick=onShowSteps,
        shape=MaterialTheme.shapes.extraLarge,
        color=MaterialTheme.colorScheme.surfaceContainerLow,
        modifier=Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(22.dp).heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            TanukiIcon(TanukiGlyph.Layers,tint=MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("运行环境已就绪",style=MaterialTheme.typography.titleMedium)
                SetupCaption("系统组件 ${model.components.ubuntuVersion} · 运行组件 ${model.components.nodeVersion}")
                Text("查看组件版本",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            }
            TanukiIcon(TanukiGlyph.Chevron,tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Column(Modifier.animateContentSize()) {
        TextButton(onClick={showPermissions=!showPermissions},modifier=Modifier.heightIn(min=48.dp)) { Text("权限说明") }
        AnimatedVisibility(visible=showPermissions) { SandboxGuidance(s) }
    }
    DeviceConditionBanner(model.deviceCondition)
    OperationCard(s,c,onOpenStorage)
}

/**
 * 设备降级状态卡片。只在非正常状态出现，明确说明原因、降级额度和恢复方式，
 * 避免用户把后台更新延后误解为任务被中止。
 */
@Composable
private fun DeviceConditionBanner(state: DeviceConditionState) {
    if (!state.enabled || (state.condition == DeviceCondition.NORMAL && state.reasons.isEmpty())) return
    SetupPanel {
        Text("设备状态 · ${state.condition.label}", style = MaterialTheme.typography.titleMedium)
        SetupCaption(state.reasonText())
        SetupCaption("已调整后台活动，当前任务保持运行。")
        if (state.deferredChecks.isNotEmpty()) {
            SetupCaption("后台更新检查已延后，状态恢复后继续；助手和当前下载保持运行。")
        } else {
            SetupCaption("助手和当前操作保持运行，可在设置中调整后台选项。")
        }
    }
}

@Composable
private fun FoundationTools(model:TanukiUiModel,c:TanukiUiCallbacks) {
    val s=model.snapshot
    val current=model.tools.checked && model.tools.ubuntuMarker.startsWith("${model.components.ubuntuVersion} ")
    val groups=listOf(
        "版本管理与网络工具" to setOf("git","curl","ca-certificates"),
        "脚本与开发工具" to setOf("python3","python3-venv"),
        "远程连接工具" to setOf("openssh-client","openssl"),
        "代码搜索工具" to setOf("ripgrep","jq"),
        "文件压缩工具" to setOf("xz-utils","zip","unzip"),
        "文件查看与同步工具" to setOf("file","less","rsync"),
        "项目构建工具" to setOf("patch","make","pkg-config"),
        "工作区支持工具" to setOf("xdg-user-dirs"),
        "运行支持组件" to setOf("libatomic1","libstdc++6"))
    SetupPanel {
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            groups.forEachIndexed { index, (name,ids) ->
                Row(Modifier.fillMaxWidth().heightIn(min=44.dp),verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(name,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    Text(if(!current)"待检查" else if(model.tools.missing.any { it in ids })"需要补齐" else "已通过",
                        style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(index<groups.lastIndex)HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            }
        }
        SetupCaption(if(current && model.tools.passed)"基础工具已准备好，可继续安装助手。" else "将安装缺少的基础工具，工作区和助手设置保留，请留意安装进度。")
        SetupCaption("基础工具不会自动开放远程登录或扩大助手权限。")
        if(s.setupBusy && s.phasePercent!=null) {
            Text("${s.phasePercent}%",style=MaterialTheme.typography.headlineLarge,fontFamily=FontFamily.Monospace,color=MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress={s.phasePercent/100f},modifier=Modifier.fillMaxWidth())
            SetupCaption("正在${operationStage(s.stage, s.environment, s.update).label}")
        }
        Button(onClick={c.command("TOOLS")},enabled=s.setupIdle && model.components.ubuntuInstalled && model.components.nodeInstalled,
            modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=MaterialTheme.shapes.medium) {
            Text(if(s.setupBusy)"正在检查" else if(current && model.tools.passed)"重新检查" else "检查并补齐")
        }
        if(s.service==ServiceState.RUNNING)SetupCaption("请先停止助手，再检查和修复运行组件。")
    }
}

@Composable
internal fun SandboxGuidance(s:Snapshot) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("文件与命令权限",style=MaterialTheme.typography.titleMedium)
        when(s.confinement) {
            Confinement.UNAVAILABLE -> {
                SetupCaption("当前权限下无法执行命令，文件工具仍可使用。")
                SetupCaption("完全权限可访问工作区外文件和网络，请先核对助手权限设置。")
            }
            Confinement.PARTIAL -> SetupCaption("本机命令能力有限，请查看助手权限设置中的可用选项。")
            Confinement.FULL -> SetupCaption("本机可以执行命令，仍遵循你选择的权限设置。")
            Confinement.UNKNOWN -> SetupCaption("命令能力尚未确认，请重新检查运行环境。")
        }
    }
}

@Composable
private fun SetupPanel(content:@Composable ColumnScope.()->Unit) {
    Surface(
        shape=MaterialTheme.shapes.extraLarge,
        color=MaterialTheme.colorScheme.surfaceContainerLow,
        modifier=Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
    }
}
@Composable
private fun SetupCaption(text:String)=Text(text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)

private val Snapshot.setupBusy:Boolean get()=operationActive
private val Snapshot.setupIdle:Boolean get()=!setupBusy && service in setOf(ServiceState.STOPPED,ServiceState.FAILED,ServiceState.INTERRUPTED)
