package com.tanuki.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tanuki.onboarding.EnvironmentReport
import com.tanuki.onboarding.EnvironmentRequirement
import com.tanuki.onboarding.OnboardingBanner
import com.tanuki.onboarding.OnboardingBannerKind
import com.tanuki.onboarding.OnboardingEnvironment
import com.tanuki.onboarding.OnboardingFinish
import com.tanuki.onboarding.OnboardingPrepare
import com.tanuki.onboarding.OnboardingPreparePhase
import com.tanuki.onboarding.OnboardingPrepareState
import com.tanuki.onboarding.OnboardingPresentation
import com.tanuki.onboarding.OnboardingStep
import com.tanuki.onboarding.RequirementStatus
import com.tanuki.runtime.EnvironmentState
import com.tanuki.runtime.Snapshot
import com.tanuki.runtime.operationActive

/**
 * 首次启动引导。
 *
 * 只负责展示：设备结论、准备进度和失败原因都来自可测层；宿主提供按钮回调。
 * 顶部始终显示四步进度点，底部固定“继续/跳过”，系统返回键回到上一步。
 */
@Composable
internal fun OnboardingScreen(
    presentation: OnboardingPresentation,
    snapshot: Snapshot,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onPrepare: () -> Unit,
    onCancelPrepare: () -> Unit,
    onRefreshDevice: () -> Unit,
    onSelectInterface: (Boolean) -> Unit,
    onComplete: (OnboardingFinish) -> Unit,
) {
    val report = remember(presentation.device) { OnboardingEnvironment.evaluate(presentation.device) }
    val prepare = remember(snapshot.environment, snapshot.stage, snapshot.bytes, snapshot.total, snapshot.phasePercent, snapshot.error) {
        OnboardingPrepare.from(
            environment = snapshot.environment,
            stage = snapshot.stage,
            receivedBytes = snapshot.bytes,
            totalBytes = snapshot.total,
            phasePercent = snapshot.phasePercent,
            error = snapshot.error,
        )
    }
    val canContinue = presentation.step != OnboardingStep.ENVIRONMENT || report.canContinue
    androidx.activity.compose.BackHandler { onBack() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().background(tanukiPageBrush()).statusBarsPadding().navigationBarsPadding(),
        ) {
            OnboardingHeader(presentation.step)
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (presentation.step) {
                    OnboardingStep.WELCOME -> WelcomeStep()
                    OnboardingStep.ENVIRONMENT -> EnvironmentStep(
                        report = report,
                        prepare = prepare,
                        snapshot = snapshot,
                        onPrepare = onPrepare,
                        onCancelPrepare = onCancelPrepare,
                        onRefreshDevice = onRefreshDevice,
                    )
                    OnboardingStep.INTERFACE -> InterfaceStep(
                        nativeSelected = presentation.interfaceNative,
                        onSelect = onSelectInterface,
                    )
                    OnboardingStep.DONE -> DoneStep(onComplete = onComplete)
                }
                Spacer(Modifier.height(8.dp))
            }
            OnboardingFooter(
                step = presentation.step,
                canContinue = canContinue,
                blocking = presentation.step == OnboardingStep.ENVIRONMENT && report.blocking,
                onContinue = onContinue,
                onSkip = onSkip,
            )
        }
    }
}

@Composable
private fun OnboardingHeader(step: OnboardingStep) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(34.dp).clip(MaterialTheme.shapes.medium).background(tanukiHeroBrush()),
                contentAlignment = Alignment.Center,
            ) {
                TanukiIcon(TanukiGlyph.Terminal, tint = colors.primary, modifier = Modifier.size(19.dp))
            }
            Text("TANUKI · 首次设置", style = MaterialTheme.typography.labelMedium, color = colors.primary, modifier = Modifier.weight(1f))
            Text("${step.ordinal + 1} / ${OnboardingStep.entries.size}", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OnboardingStep.entries.forEachIndexed { index, item ->
                val current = item == step
                val done = item.ordinal < step.ordinal
                if (index > 0) {
                    Box(
                        Modifier.weight(1f).height(2.dp).background(
                            if (done || current) colors.primary.copy(alpha = 0.45f)
                            else colors.surfaceContainerHighest
                        )
                    )
                }
                Box(
                    Modifier.size(if (current) 12.dp else 8.dp).clip(CircleShape).background(
                        when {
                            current -> colors.primary
                            done -> colors.primary.copy(alpha = 0.55f)
                            else -> colors.surfaceContainerHighest
                        }
                    ).clearAndSetSemantics {
                        contentDescription = "第 ${item.ordinal + 1} 步，共 ${OnboardingStep.entries.size} 步" +
                            if (done) "，已完成" else if (current) "，当前步骤" else ""
                    }
                )
            }
        }
    }
}

@Composable
private fun OnboardingFooter(
    step: OnboardingStep,
    canContinue: Boolean,
    blocking: Boolean,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 2.dp) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (blocking) {
                Text(
                    "当前设备不满足最低要求；可以先跳过，稍后改用连接远程电脑。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (step == OnboardingStep.DONE) "稍后再说" else "跳过")
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onContinue,
                    enabled = canContinue,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        when (step) {
                            OnboardingStep.DONE -> "完成"
                            OnboardingStep.ENVIRONMENT -> "继续"
                            else -> "继续"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.WelcomeStep() {
    Text("先花一分钟，把这里准备好。", style = MaterialTheme.typography.headlineMedium)
    Text(
        "你可以在手机本机运行开发助手，也可以连接你电脑上的环境；对话、文件和项目都在一个应用里完成。",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OnboardingCard {
        WelcomePoint("在本机运行", "不需要电脑，准备完成后直接在手机上开始。")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        WelcomePoint("连接远程电脑", "已经在电脑上配置好环境时，可以直接连过去使用。")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        WelcomePoint("随时可以回来", "每一步都能跳过；之后可以从设置重新打开这份引导。")
    }
}

@Composable
private fun WelcomePoint(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EnvironmentStep(
    report: EnvironmentReport,
    prepare: OnboardingPrepareState,
    snapshot: Snapshot,
    onPrepare: () -> Unit,
    onCancelPrepare: () -> Unit,
    onRefreshDevice: () -> Unit,
) {
    Text("准备运行环境", style = MaterialTheme.typography.headlineMedium)
    Text(
        "先检查这台手机是否满足最低要求。满足后点“一键准备”，应用会下载、解压并校验必要组件；期间可以离开此页。",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OnboardingCard {
        report.requirements.forEachIndexed { index, requirement ->
            RequirementRow(requirement)
            if (index != report.requirements.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
    TextButton(onClick = onRefreshDevice, modifier = Modifier.heightIn(min = 48.dp)) { Text("重新检测") }
    when {
        snapshot.environment == EnvironmentState.READY -> ReadyCard(prepare)
        prepare.phase == OnboardingPreparePhase.FAILED -> FailureCard(prepare, report.canContinue, onPrepare)
        prepare.active || snapshot.operationActive -> PrepareProgressCard(prepare, onCancelPrepare)
        else -> PrepareStartCard(prepare, report.canContinue, onPrepare)
    }
}

@Composable
private fun RequirementRow(requirement: EnvironmentRequirement) {
    val colors = MaterialTheme.colorScheme
    val accent = when (requirement.status) {
        RequirementStatus.OK -> colors.primary
        RequirementStatus.WARNING -> colors.tertiary
        RequirementStatus.BLOCKING -> colors.error
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when (requirement.status) {
                    RequirementStatus.OK -> "✓"
                    RequirementStatus.WARNING -> "!"
                    RequirementStatus.BLOCKING -> "×"
                },
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(requirement.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    when (requirement.status) {
                        RequirementStatus.OK -> "满足"
                        RequirementStatus.WARNING -> "偏低"
                        RequirementStatus.BLOCKING -> "不满足"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
            }
            Text(
                "实测 ${requirement.actual} · 要求 ${requirement.requirement}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            requirement.advice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = accent)
            }
        }
    }
}

@Composable
private fun PrepareStartCard(prepare: OnboardingPrepareState, canPrepare: Boolean, onPrepare: () -> Unit) {
    OnboardingCard {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("一键准备", style = MaterialTheme.typography.titleMedium)
            Text(
                prepare.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "会下载并解压必要组件，建议连接 Wi-Fi，并预留 4 GB 空间。准备期间会申请通知权限用于显示进度；拒绝不影响继续安装。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!canPrepare) {
                Text(
                    "请先按上面的建议满足最低要求；也可以跳过，改用连接远程电脑。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onPrepare,
                enabled = canPrepare,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            ) {
                Text(if (prepare.phase == OnboardingPreparePhase.CANCELLED) "重新准备" else "一键准备")
            }
        }
    }
}

@Composable
private fun PrepareProgressCard(prepare: OnboardingPrepareState, onCancel: () -> Unit) {
    val stageIndex = when (prepare.phase) {
        OnboardingPreparePhase.DOWNLOAD -> 0
        OnboardingPreparePhase.EXTRACT -> 1
        OnboardingPreparePhase.VERIFY -> 2
        OnboardingPreparePhase.INSTALL -> 3
        else -> -1
    }
    OnboardingCard {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(prepare.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                prepare.fraction?.let {
                    Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("下载", "解压", "校验", "补齐组件").forEachIndexed { index, label ->
                    val reached = stageIndex >= index
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).clip(MaterialTheme.shapes.small)
                            .background(
                                if (reached) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (prepare.fraction != null) {
                LinearProgressIndicator(
                    progress = { prepare.fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Text(
                prepare.detail.ifBlank { "正在准备，请稍候。" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (prepare.fraction != null) "进度来自实际下载量；解压与校验无法预估总量时显示不定进度。"
                else "当前阶段无法预估总量，因此不显示百分比；上方会给出真实写入量或阶段说明。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("取消准备") }
        }
    }
}

@Composable
private fun FailureCard(prepare: OnboardingPrepareState, canRetry: Boolean, onRetry: () -> Unit) {
    OnboardingCard {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("准备未完成", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Text(
                prepare.failure ?: "准备没有完成。请重试。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (prepare.detail.isNotBlank() && prepare.detail != prepare.failure) {
                Text(prepare.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!canRetry) {
                Text(
                    "当前设备仍不满足最低要求，请按上方建议处理后再重试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = onRetry,
                enabled = canRetry,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            ) {
                Text("重试")
            }
        }
    }
}

@Composable
private fun ReadyCard(prepare: OnboardingPrepareState) {
    OnboardingCard {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("环境已就绪", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                prepare.detail.ifBlank { "安装与自检已完成，可以继续下一步。" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InterfaceStep(nativeSelected: Boolean, onSelect: (Boolean) -> Unit) {
    Text("选择界面", style = MaterialTheme.typography.headlineMedium)
    Text(
        "先选一个开始，之后可以在设置里随时切换。",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InterfaceOption(
            title = "官方网页界面",
            detail = "功能最全，也是官方默认界面；适合大多数使用。",
            selected = !nativeSelected,
            onClick = { onSelect(false) },
        )
        InterfaceOption(
            title = "原生界面",
            detail = "更贴近手机操作的会话列表与消息视图；高级功能仍会回到网页。",
            selected = nativeSelected,
            onClick = { onSelect(true) },
        )
    }
    Text(
        "稍后可在 设置 → 网页与兼容性 中修改。接口不可用时会自动回到官方界面，不会中断正在运行的任务。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InterfaceOption(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.primaryContainer.copy(alpha = 0.55f) else colors.surfaceContainerLow,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) colors.primary else colors.outlineVariant),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(22.dp).clip(CircleShape)
                    .background(if (selected) colors.primary else colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Text("✓", color = colors.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DoneStep(onComplete: (OnboardingFinish) -> Unit) {
    Text("设置完成", style = MaterialTheme.typography.headlineMedium)
    Text(
        "可以马上开始；环境还没准备好时，进入后仍会看到准备入口。",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = { onComplete(OnboardingFinish.FIRST_MESSAGE) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Text("发第一条消息")
    }
    Text(
        "进入对话，直接描述你想让助手完成的事情。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = { onComplete(OnboardingFinish.REMOTE) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Text("连接远程电脑")
    }
    Text(
        "用 SSH 连接你已经在使用的电脑，不需要在本机安装运行环境。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OnboardingCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

/**
 * 首页可关闭的完成提示条。
 *
 * 跳过引导后出现；点“继续”回到第一个未完成步骤。大版本升级只给一次轻提示，
 * 不自动展开整套引导。
 */
@Composable
internal fun OnboardingBannerCard(
    banner: OnboardingBanner,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (banner.kind == OnboardingBannerKind.UPGRADE) "已更新到新版本" else "完成设置",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (banner.kind == OnboardingBannerKind.UPGRADE) "可以重新查看引导，了解当前设置。"
                    else "已完成 ${banner.completedSteps} / ${banner.totalSteps} 步" +
                        (banner.step?.let { " · 下一步：${stepTitle(it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (banner.kind == OnboardingBannerKind.UPGRADE) "查看" else "继续")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("关闭")
            }
        }
    }
}

private fun stepTitle(step: OnboardingStep): String = when (step) {
    OnboardingStep.WELCOME -> "欢迎"
    OnboardingStep.ENVIRONMENT -> "准备环境"
    OnboardingStep.INTERFACE -> "选择界面"
    OnboardingStep.DONE -> "完成"
}
