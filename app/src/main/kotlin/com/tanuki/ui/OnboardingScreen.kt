package com.tanuki.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tanuki.onboarding.*
import com.tanuki.runtime.*
import com.tanuki.ui.design.TanukiButton as Button
import com.tanuki.ui.design.TanukiOutlinedButton as OutlinedButton

/** 首启只保留选择运行位置与准备两屏；旧步骤值仍可恢复到准备页，不重置记录。 */
@Composable
internal fun OnboardingScreen(
    presentation: OnboardingPresentation,
    snapshot: Snapshot,
    conversationReady: Boolean = false,
    components: ComponentInventory,
    tools: RequiredToolsStatus,
    webViewSupported: Boolean,
    onUpdateWebView: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onPrepare: () -> Unit,
    onCommand: (String) -> Unit,
    onCancelPrepare: () -> Unit,
    onRefreshDevice: () -> Unit,
    onSelectInterface: (Boolean) -> Unit,
    onComplete: (OnboardingFinish) -> Unit,
) {
    val welcome = presentation.step == OnboardingStep.WELCOME
    val report = remember(presentation.device) { OnboardingEnvironment.evaluate(presentation.device) }
    val journey = rememberSetupJourney(snapshot, components, tools)
    var showDevice by rememberSaveable { mutableStateOf(false) }
    var showInterface by rememberSaveable { mutableStateOf(presentation.step == OnboardingStep.INTERFACE) }
    val scroll = rememberScrollState()
    LaunchedEffect(welcome) { scroll.scrollTo(0) }
    BackHandler { onBack() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (!welcome) TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("返回") }
                Text("Tanuki", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (snapshot.operationActive) "后台继续" else "稍后设置")
                }
            }
            Column(Modifier.weight(1f).widthIn(max = 640.dp).fillMaxWidth().align(Alignment.CenterHorizontally)
                .verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)) {
                if (welcome) {
                    Spacer(Modifier.height(16.dp))
                    TanukiIcon(TanukiGlyph.Terminal, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Text("把想法变成\n手机上的项目", style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() })
                    Text("在手机上运行开发助手，处理代码、文件和项目。模型通过网络提供服务。",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            Text(if (journey.componentsComplete) "使用本机环境" else "在这台手机上准备")
                        }
                        Text("无需电脑。下一步会先检查设备，再由你确认下载。", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("已有电脑环境？", style = MaterialTheme.typography.titleMedium)
                        Text("通过 SSH 或配对连接电脑上的助手，手机无需安装本机运行组件。",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { onComplete(OnboardingFinish.REMOTE) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("连接远程电脑") }
                    }
                } else {
                    SetupJourneyContent(journey, snapshot, report.canContinue, onPrepare,
                        onCommand = { command ->
                            if (command == "OPEN_COMPONENTS") onComplete(OnboardingFinish.HOME) else onCommand(command)
                        }, onCancel = onCancelPrepare,
                        onOpenChat = { onComplete(OnboardingFinish.FIRST_MESSAGE) }, webViewSupported = webViewSupported,
                        conversationReady = conversationReady)
                    if (!webViewSupported) {
                        Text("当前网页引擎需要更新，更新后才能打开对话。已准备的组件会保留。",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = onUpdateWebView, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("更新网页引擎")
                        }
                    }
                    report.requirements.filter { it.status != RequirementStatus.OK }.forEach { RequirementRow(it) }
                    if (report.blocking) {
                        OutlinedButton(onClick = { onComplete(OnboardingFinish.REMOTE) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("改用远程电脑") }
                    }
                    HorizontalDivider()
                    SetupDependencySummary(components, tools)
                    TextButton(onClick = { showDevice = !showDevice }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (showDevice) "收起设备检查" else "查看设备检查")
                    }
                    if (showDevice) {
                        report.requirements.filter { it.status == RequirementStatus.OK }.forEach { RequirementRow(it) }
                        TextButton(onClick = onRefreshDevice, modifier = Modifier.heightIn(min = 48.dp)) { Text("重新检测") }
                    }
                    TextButton(onClick = { showInterface = !showInterface }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (showInterface) "收起界面偏好" else "界面偏好 · ${if (presentation.interfaceNative) "手机界面" else "网页界面"}")
                    }
                    if (showInterface) InterfaceOptions(presentation.interfaceNative, onSelectInterface)
                    Text("之后可从设置重新打开引导，或查看与维护运行组件。", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun RequirementRow(requirement: EnvironmentRequirement) {
    val color = if (requirement.status == RequirementStatus.BLOCKING) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(requirement.title + if (requirement.status == RequirementStatus.OK) " · 满足" else " · 需要注意",
            style = MaterialTheme.typography.titleSmall, color = color)
        Text("当前 ${requirement.actual} · 要求 ${requirement.requirement}", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        requirement.advice?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = color) }
    }
}

@Composable
private fun InterfaceOptions(nativeSelected: Boolean, onSelect: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("选择对话界面", style = MaterialTheme.typography.titleMedium)
        InterfaceOption("网页界面", "官方完整功能。", !nativeSelected) { onSelect(false) }
        InterfaceOption("手机界面", "适合手机的对话视图，高级功能可切回网页。", nativeSelected) { onSelect(true) }
        Text("可在设置 → 引擎与更新 → 网页与兼容性中随时切换。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InterfaceOption(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
        .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 跳过后保留恢复入口，不把查看过页面当作已经准备好运行环境。 */
@Composable
internal fun OnboardingBannerCard(banner: OnboardingBanner, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (banner.kind == OnboardingBannerKind.UPGRADE) "已更新到新版本" else "继续设置 Tanuki",
                style = MaterialTheme.typography.titleSmall)
            Text(if (banner.kind == OnboardingBannerKind.UPGRADE) "可查看当前运行方式与界面偏好。"
                else "回到准备页面，查看当前进度或选择连接电脑。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (banner.kind == OnboardingBannerKind.UPGRADE) "查看" else "继续设置")
                }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("关闭") }
            }
        }
    }
}
