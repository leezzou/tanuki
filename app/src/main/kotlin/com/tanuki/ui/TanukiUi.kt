package com.tanuki.ui

import com.tanuki.ui.design.TanukiOutlinedButton as OutlinedButton

import com.tanuki.ui.design.TanukiButton as Button

import android.os.Build
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tanuki.R
import com.tanuki.power.DeviceConditionState
import com.tanuki.runtime.*
import com.tanuki.storage.WorkspaceStorageState
import com.tanuki.update.ApkUpdateCheckState
import com.tanuki.update.DshUpdateCheckState
import com.tanuki.update.Release
import com.tanuki.ui.design.TanukiMotion as TanukiMotionTokens
import com.tanuki.ui.design.TanukiShapeTokens
import com.tanuki.ui.design.TanukiSpace
import com.tanuki.web.DshUiMode
import com.tanuki.web.DshUiSwitchPolicy
import com.tanuki.web.WebViewSupport
import com.tanuki.web.WebViewSupportState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** 未持有传输时共用的空流；模型在每次重组时构造，不能为默认值新建实例。 */
private val EMPTY_TRANSFERS: StateFlow<List<FileTransferProgress>> = MutableStateFlow(emptyList())

/** Presentation only. The host owns permissions, confirmations, jobs and WebView lifetime. */
data class TanukiUiModel(
    val snapshot: Snapshot,
    val support: WebViewSupport,
    val versions: List<Release>,
    val recentLogs: List<String>,
    val storageUsage: String,
    val deviceInfo: String,
    val backgroundResult: String?,
    val legacyShim: Boolean,
    val blobExport: Boolean,
    val versionName: String,
    val freeBytes: Long,
    val components: ComponentInventory = ComponentInventory(),
    val ubuntuSources: List<RuntimeSource> = emptyList(),
    val nodeSources: List<RuntimeSource> = emptyList(),
    val ubuntuSpeed:SourceSpeedSnapshot=SourceSpeedSnapshot(),
    val nodeSpeed:SourceSpeedSnapshot=SourceSpeedSnapshot(),
    val nodeRequirement:String="",
    val workspaceStorage: WorkspaceStorageState = WorkspaceStorageState(),
    val tools: RequiredToolsStatus = RequiredToolsStatus(),
    val npmSelection: NpmSourceSelection = NpmSourceSelection(),
    val npmSources: List<RuntimeSource> = emptyList(),
    val npmSpeed: SourceSpeedSnapshot = SourceSpeedSnapshot(),
    // 只传递流，不在根组合里 collect：进度更新只重组进度条，不重排整页列表。
    val transfers: StateFlow<List<FileTransferProgress>> = EMPTY_TRANSFERS,
    val liveLogs: kotlinx.coroutines.flow.StateFlow<List<String>>? = null,
    val appUpdate: com.tanuki.update.AppUpdateState = com.tanuki.update.AppUpdateState(),
    val appInstallAllowed: Boolean = false,
    val apkUpdateCheck: ApkUpdateCheckState = ApkUpdateCheckState(),
    val dshUpdateCheck: DshUpdateCheckState = DshUpdateCheckState(),
    val chatNotifications: com.tanuki.notify.ChatNotificationStatus = com.tanuki.notify.ChatNotificationStatus(),
    val fileFullscreen: Boolean = false,
    val remoteHost: RemoteHostConfig? = null,
    val remote: RemoteHostStatus = RemoteHostStatus(),
    val remoteBusy: Boolean = false,
    val remoteMessage: String = "",
    val remoteError: String = "",
    val remotePendingFingerprint: String? = null,
    val remotePendingHost: String = "",
    val remoteProfiles: List<RemoteHostProfile> = emptyList(),
    val remoteActiveProfileId: String? = null,
    val remoteStage: RemoteConnectionStage? = null,
    val remoteCompletedStages: Set<RemoteConnectionStage> = emptySet(),
    val remoteFailure: RemoteFailureInfo? = null,
    val remoteAttemptSucceeded: Boolean = false,
    val remoteLocalPort: Int? = null,
    val remoteAttemptConfig: RemoteHostConfig? = null,
    val remoteAttemptProfileId: String? = null,
    val cookieReady: Boolean = true,
    val deviceCondition: DeviceConditionState = DeviceConditionState(),
    val earlyStart: Boolean = true,
    /** 用户偏好；默认官方界面。 */
    val nativeDshPreferred: Boolean = false,
    /** 原生接口不可用时的回退原因；为空表示未回退。 */
    val nativeDshFallback: String? = null,
    /** 首次启动引导；为空表示从未打开过，不显示提示条。 */
    val onboarding: com.tanuki.onboarding.OnboardingPresentation? = null)

data class TanukiUiCallbacks(
    val command: (String) -> Unit,
    val install: (Release) -> Unit,
    val checkUpdates: () -> Unit,
    val checkDshUpdate: () -> Unit = {},
    val importFiles: () -> Unit,
    val measureStorage: () -> Unit,
    val toggleLegacy: (Boolean) -> Unit,
    val toggleBlob: (Boolean) -> Unit,
    val toggleEarlyStart: (Boolean) -> Unit = {},
    val showNotices: () -> Unit,
    val updateWebView: () -> Unit,
    val webViewSettings: () -> Unit,
    val appSettings: () -> Unit,
    val notifications: () -> Unit,
    val connectDsh: () -> Unit,
    /** 生产入口继续走确认框与前台服务，并携带失败时的目标版本。 */
    val retryFailure: (FailureRetry) -> Unit = { command(it.command) },
    val selectSource: (RuntimeComponent, String) -> Unit = { _, _ -> },
    val reinstallDsh: (Release) -> Unit = { },
    val uninstallDsh: (Release?) -> Unit = { },
    val cacheDsh: (Release) -> Unit = { },
    val loadInstalledDsh: suspend () -> InstalledDshInventory = { InstalledDshInventory() },
    val switchInstalledDsh: (String) -> Unit = { },
    val uninstallInstalledDsh: (String) -> Unit = { },
    val setFullscreen: (Boolean) -> Unit = { },
    val selectVersion:(RuntimeComponent,String)->Unit={_,_->},
    val testSources:(RuntimeComponent)->Unit={},
    val prepareSources:(RuntimeComponent)->Unit={},
    val cancelSourceTests:(RuntimeComponent)->Unit={},
    val refreshWorkspaceStorage: () -> Unit = {},
    val saveWorkspaceCapacity: suspend (Long?) -> Unit = {},
    val selectNpmSource: (String) -> Unit = {},
    val prepareNpmSources: (String) -> Unit = {},
    val testNpmSources: (String) -> Unit = {},
    val cancelNpmSourceTests: () -> Unit = {},
    val openBrowser: () -> Unit = {},
    val toggleBrowserControl: () -> Unit = {},
    val openBrowserOverlay: (() -> Unit)? = null,
    val checkAppUpdates: () -> Unit = {},
    val downloadAppUpdate: () -> Unit = {},
    val installAppUpdate: () -> Unit = {},
    val cancelAppUpdate: () -> Unit = {},
    val testRemoteHost: (RemoteHostConfig) -> Boolean = { false },
    val connectRemoteHost: (RemoteHostConfig) -> Unit = {},
    val confirmRemoteHostKey: () -> Unit = {},
    val cancelRemoteHostKey: () -> Unit = {},
    val disconnectRemoteHost: () -> Unit = {},
    val forgetRemoteHost: () -> Unit = {},
    val saveRemoteProfile: (String, RemoteHostConfig, String?, Boolean) -> Unit = { _, _, _, _ -> },
    val deleteRemoteProfile: (String) -> Unit = {},
    val connectRemoteProfile: (RemoteHostProfile) -> Unit = {},
    val setDeviceConditionEnabled: (Boolean) -> Unit = {},
    val setLowBatteryThreshold: (Int) -> Unit = {},
    val dshVisible: (Boolean) -> Unit = {},
    val chatNotifications: () -> Unit = {},
    val toggleChatNotifications: (Boolean) -> Unit = {},
    val toggleNativeDsh: (Boolean) -> Unit = {},
    val retryNativeDsh: () -> Unit = {},
    val cancelTransfer: (Long) -> Unit = {},
    /** 打开发送独立原生会话管理页；不改变官方/原生 DSH 页选择。 */
    val openSessionManager: () -> Unit = {},
    // 首次启动引导：界面只转发用户动作，状态与持久化由 OnboardingController 负责。
    val onboardingContinue: () -> Unit = {},
    val onboardingBack: () -> Unit = {},
    val onboardingSkip: () -> Unit = {},
    val onboardingPrepare: () -> Unit = {},
    val onboardingCancelPrepare: () -> Unit = { command("CANCEL") },
    val onboardingRefreshDevice: () -> Unit = {},
    val onboardingSelectInterface: (Boolean) -> Unit = {},
    val onboardingComplete: (com.tanuki.onboarding.OnboardingFinish) -> Unit = {},
    val onboardingOpen: () -> Unit = {},
    val onboardingDismissBanner: () -> Unit = {},
    val onboardingDismissUpgrade: () -> Unit = {}
)

internal val Snapshot.tanukiBusy: Boolean get() = operationActive
internal val Snapshot.tanukiIdle: Boolean get() = !tanukiBusy &&
    service in setOf(ServiceState.STOPPED, ServiceState.FAILED, ServiceState.INTERRUPTED)
private enum class TanukiDestination(val title: String, val glyph: TanukiGlyph) {
    WORKSPACE("工作台", TanukiGlyph.Home), DSH("DSH", TanukiGlyph.Terminal),
    FILES("文件", TanukiGlyph.Folder), BROWSER("浏览器", TanukiGlyph.Browser), SETTINGS("设置", TanukiGlyph.Settings)
}
internal enum class TanukiGlyph { Home, Terminal, Layers, Settings, Folder, File, Browser, Back, Refresh, Stop, Chevron, Download, Expand, Contract }

@Composable
fun TanukiUi(model: TanukiUiModel, callbacks: TanukiUiCallbacks, browserView: WebView,
             filesPage: @Composable (Modifier, Dp) -> Unit = { _, _ -> },
             agentBrowserPage: @Composable (Modifier, Boolean, (Boolean) -> Unit, (() -> Unit)?, Dp) -> Unit = { _, _, _, _, _ -> },
             nativeDshPage: @Composable (Modifier) -> Unit = {},
             browserOpenRequest: Int = 0, dshOpenRequest: Int = 0, filesOpenRequest: Int = 0,
             browserControl: BrowserControlUiState = BrowserControlUiState(),
             remoteOpenRequest: Int = 0) {
    // 首次启动引导占据整屏；此时不创建导航与 WebView 相关的页面状态。
    val onboarding = model.onboarding
    if (onboarding?.open == true) {
        OnboardingScreen(
            presentation = onboarding,
            snapshot = model.snapshot,
            conversationReady = model.cookieReady,
            onContinue = callbacks.onboardingContinue,
            onBack = callbacks.onboardingBack,
            onSkip = callbacks.onboardingSkip,
            onPrepare = callbacks.onboardingPrepare,
            onCancelPrepare = callbacks.onboardingCancelPrepare,
            onRefreshDevice = callbacks.onboardingRefreshDevice,
            onSelectInterface = callbacks.onboardingSelectInterface,
            onComplete = callbacks.onboardingComplete,
        )
        return
    }
    var tab by rememberSaveable(key = "tanuki-navigation-5") { mutableIntStateOf(0) }
    val pageStates = rememberSaveableStateHolder()
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var browserFullscreen by rememberSaveable { mutableStateOf(false) }
    val activeFullscreen = (fullscreen && tab == 1) || (browserFullscreen && tab == 3) || (model.fileFullscreen && tab == 2)
    var floatingBrowser by rememberSaveable { mutableStateOf(false) }
    fun navigate(index: Int) {
        if (index == 3) { callbacks.openBrowser(); floatingBrowser = false }
        tab = index
    }
    LaunchedEffect(browserOpenRequest) { if (browserOpenRequest > 0) navigate(3) }
    // 通知点击可能要求直接回到 DSH 页；保留当前会话选择，只切换导航。
    LaunchedEffect(dshOpenRequest) { if (dshOpenRequest > 0) tab = 1 }
    // 外部 content:// 文件或应用内选择器打开文件时直接进入文件页，避免用户再手动切页。
    LaunchedEffect(filesOpenRequest) { if (filesOpenRequest > 0) tab = 2 }
    LaunchedEffect(tab) { callbacks.dshVisible(tab == 1) }
    DisposableEffect(Unit) { onDispose { callbacks.dshVisible(false) } }
    val s = model.snapshot
    // 远程主机只有官方 Web UI，远程连接期间强制走 WebView，不能进入本地原生 DSH。
    val nativeActive = DshUiSwitchPolicy.useNative(
        if (model.nativeDshPreferred) DshUiMode.NATIVE else DshUiMode.OFFICIAL,
        s.service == ServiceState.RUNNING && !s.remote.connected,
        model.nativeDshFallback
    )
    LaunchedEffect(tab, s.service, s.remote.phase, s.remote.localPort, model.cookieReady, nativeActive) {
        if (tab == 1 && (s.service == ServiceState.RUNNING || s.remote.connected) && model.cookieReady && !nativeActive) callbacks.connectDsh()
    }
    LaunchedEffect(activeFullscreen) { callbacks.setFullscreen(activeFullscreen) }
    DisposableEffect(Unit) { onDispose { callbacks.setFullscreen(false) } }
    BackHandler(tab == 1) {
        if (activeFullscreen) {
            fullscreen = false
        } else if (nativeActive) {
            tab = 0
        } else if ((s.service == ServiceState.RUNNING || s.remote.connected) && model.support.state == WebViewSupportState.SUPPORTED &&
            s.web != WebState.RENDER_ERROR && browserView.canGoBack()) browserView.goBack() else tab = 0
    }
    var settingsReturn by rememberSaveable { mutableIntStateOf(0) }
    var settingsSection by rememberSaveable { mutableStateOf<String?>(null) }
    // 首次引导完成时可直接进入远程主机设置；与普通设置返回栈保持同一入口。
    LaunchedEffect(remoteOpenRequest) {
        if (remoteOpenRequest > 0) {
            settingsReturn = tab.takeUnless { it == 4 } ?: 0
            settingsSection = "远程主机"
            tab = 4
        }
    }
    fun openSettings() { settingsReturn = tab.takeUnless { it == 4 } ?: 0; settingsSection = null; tab = 4 }
    fun settingsBack() { if (settingsSection != null) settingsSection = null else tab = settingsReturn }
    fun openStorageSettings() {
        settingsReturn = tab.takeUnless { it == 4 } ?: 0
        settingsSection = "存储与空间"
        tab = 4
    }

    // 全局错误条只认一份统一模型；运行失败优先，应用更新失败在其后。
    val runtimeFailure = s.displayFailure()
    val appUpdateFailure = com.tanuki.update.AppUpdateFailure.from(model.appUpdate)
    val activeFailure = runtimeFailure ?: appUpdateFailure
    var dismissedFailureKey by rememberSaveable { mutableStateOf<String?>(null) }
    var failureDetail by remember { mutableStateOf<AppFailure?>(null) }
    LaunchedEffect(activeFailure?.stableKey) { if (activeFailure == null) dismissedFailureKey = null }
    val visibleFailure = activeFailure.afterDismiss(dismissedFailureKey)

    fun runFailureAction(failure: AppFailure, kind: FailureActionKind) {
        if (failure.componentId == "app" && kind == FailureActionKind.RETRY) {
            if (model.appUpdate.candidate != null) callbacks.downloadAppUpdate() else callbacks.checkAppUpdates()
            return
        }
        performFailureAction(failure, kind, callbacks, ::openStorageSettings,
            onViewVersions = {
                settingsReturn = tab.takeUnless { it == 4 } ?: 0
                settingsSection = "组件维护与诊断"
                tab = 4
            },
            versionsAvailable = model.versions.isNotEmpty(),
            onSwitchSource = {
                val component = when (failure.componentId) {
                    "ubuntu" -> RuntimeComponent.UBUNTU
                    "node" -> RuntimeComponent.NODE
                    else -> null
                }
                if (component != null) {
                    val sources = if (component == RuntimeComponent.UBUNTU) model.ubuntuSources else model.nodeSources
                    val current = if (component == RuntimeComponent.UBUNTU) model.components.ubuntuSource else model.components.nodeSource
                    val next = sources.firstOrNull { it.id != current } ?: sources.firstOrNull()
                    if (next != null) callbacks.selectSource(component, next.id)
                }
            })
    }
    BackHandler(tab != 0 && tab != 1 && tab != 4) {
        if (tab == 3 && activeFullscreen) browserFullscreen = false else tab = 0
    }
    BackHandler(tab == 4) { settingsBack() }
    val destinations = TanukiDestination.entries.take(4)
    val navigationBackdrop = rememberLayerBackdrop()
    val layoutDirection = LocalLayoutDirection.current
    val hasRuntimeProgress = fileProgressHost(s, false) == FileProgressHost.RUNTIME
    val progressEntry: @Composable (Modifier) -> Unit = { modifier ->
        OperationProgressEntry(snapshot = s, transfers = model.transfers,
            onCancel = { callbacks.command("CANCEL") }, onCancelTransfer = callbacks.cancelTransfer,
            onRetry = { command ->
                if (command == "OPEN_COMPONENTS") {
                    openSettings(); settingsSection = "组件维护与诊断"
                } else callbacks.command(command)
            }, modifier = modifier.widthIn(max = 360.dp))
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val floatingNavigation = !wide && !activeFullscreen && WindowInsets.ime.getBottom(LocalDensity.current) == 0
        // 各页共用浮动布局；只录制原生内容，网页维持原生绘制。
        val nativeGlass = floatingNavigation && tab in setOf(0, 2, 4) && Build.VERSION.SDK_INT >= 33
        Row(Modifier.fillMaxSize()) {
            if (wide && !activeFullscreen) NavigationRail(containerColor = Color.Transparent,
                modifier = Modifier.fillMaxHeight().width(96.dp)) {
                Spacer(Modifier.height(20.dp))
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(vertical = 12.dp)) {
                        destinations.forEachIndexed { index, destination ->
                            if (index == 2) Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                BrowserControlButton(browserControl, callbacks.toggleBrowserControl)
                            }
                            NavigationRailItem(selected = tab == index, onClick = { navigate(index) },
                                icon = { TanukiIcon(destination.glyph, destination.title) }, label = { Text(destination.title) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                ))
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
            Scaffold(modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = if (activeFullscreen) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
                topBar = {
                    Column {
                        if (tab != 1 && tab != 3 && !activeFullscreen) TanukiHeader(tab, if (tab == 4) settingsSection ?: "设置" else TanukiDestination.entries[tab].title, callbacks,
                            s.service in setOf(ServiceState.STARTING, ServiceState.RUNNING),
                            settings = ::openSettings, back = ::settingsBack)
                        if (browserControl.requestPending && tab != 3) Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Row(Modifier.fillMaxWidth().then(if (tab == 1) Modifier.statusBarsPadding() else Modifier).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("浏览器有待处理请求", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                                TextButton(onClick = { navigate(3) }) { Text("查看请求") }
                            }
                        }
                        visibleFailure?.let { failure ->
                            GlobalFailureBar(
                                failure = failure,
                                onOpen = { failureDetail = failure },
                                onDismiss = { dismissedFailureKey = failure.stableKey },
                                modifier = when {
                                    tab == 3 && activeFullscreen -> Modifier.displayCutoutPadding().statusBarsPadding()
                                    tab == 3 || (tab == 1 && !browserControl.requestPending) -> Modifier.statusBarsPadding()
                                    else -> Modifier
                                }
                            )
                        }
                    }
                }, bottomBar = {
                    if (floatingNavigation) Column(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)
                            .semantics { contentDescription = "主导航栏" }
                    ) {
                        GlassNavigationBar(
                            selectedIndex = tab,
                            labels = destinations.map { it.title },
                            onSelect = ::navigate,
                            backdrop = if (nativeGlass) navigationBackdrop else null,
                            translucentBackground = floatingNavigation && !nativeGlass,
                            centerAction = { BrowserControlButton(browserControl, callbacks.toggleBrowserControl) },
                        ) { index, tint ->
                            TanukiIcon(destinations[index].glyph, destinations[index].title, tint = tint)
                        }
                    }
                }) { padding ->
                // 外壳统一预留导航与系统栏，子页不重复叠加同一 inset。
                val bottomInset = 0.dp
                // 录制仍与导航共用坐标原点，任务浮层不改变内容视口。
                val pageModifier = Modifier.fillMaxSize()
                    .then(if (nativeGlass) Modifier.layerBackdrop(navigationBackdrop) else Modifier)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(start = padding.calculateStartPadding(layoutDirection), top = padding.calculateTopPadding(),
                        end = padding.calculateEndPadding(layoutDirection), bottom = padding.calculateBottomPadding())
                    .consumeWindowInsets(padding)
                Box(Modifier.fillMaxSize()) {
                TanukiPageTransitionHost(
                    target = TanukiPageRoute(tab, settingsSection.takeIf { tab == 4 }),
                    navigation = ::tanukiPageNavigation,
                    modifier = pageModifier,
                    webView = tab == 3 || (tab == 1 && !nativeActive),
                ) { pageContentModifier ->
                when (tab) {
                    1 -> DshPage(model, callbacks, browserView, pageContentModifier, home = { tab = 0 },
                        fullscreen = activeFullscreen, onFullscreen = { fullscreen = it }, settings = ::openSettings, bottomInset = bottomInset,
                        onOpenStorage = ::openStorageSettings,
                        native = nativeActive, nativePage = nativeDshPage, fallback = model.nativeDshFallback,
                        openBrowserWindow = {
                            if (callbacks.openBrowserOverlay != null) callbacks.openBrowserOverlay.invoke()
                            else { if (!floatingBrowser) callbacks.openBrowser(); floatingBrowser = !floatingBrowser }
                        })
                    2 -> pageStates.SaveableStateProvider("files") {
                        CompositionLocalProvider(LocalFileOperationOverlay provides if (hasRuntimeProgress) progressEntry else null) {
                            filesPage(pageContentModifier, bottomInset)
                        }
                    }
                    3 -> pageStates.SaveableStateProvider("browser") {
                        agentBrowserPage(pageContentModifier, browserFullscreen, { browserFullscreen = it }, ::openSettings, bottomInset)
                    }
                    else -> key(tab, settingsSection.takeIf { tab == 4 }) {
                        val bottomSpace = 32.dp + bottomInset
                        Box(pageContentModifier.background(tanukiPageBrush())) {
                            // 远程页自带滚动与下拉刷新；放进外层 verticalScroll 会嵌套滚动并让手势失效。
                            if (tab == 4 && settingsSection == "远程主机") {
                                Box(Modifier.align(Alignment.TopCenter).widthIn(max = 760.dp).fillMaxSize()) {
                                    RemoteHostSettingsPage(model, callbacks, bottomInset)
                                }
                            } else {
                                Column(Modifier.align(Alignment.TopCenter).widthIn(max = 760.dp).fillMaxSize().imePadding()
                                    .verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = bottomSpace),
                                    verticalArrangement = Arrangement.spacedBy(28.dp)) {
                                    if (tab == 0) {
                                        model.onboarding?.banner?.let { banner ->
                                            OnboardingBannerCard(
                                                banner = banner,
                                                onOpen = callbacks.onboardingOpen,
                                                onDismiss = if (banner.kind == com.tanuki.onboarding.OnboardingBannerKind.UPGRADE)
                                                    callbacks.onboardingDismissUpgrade else callbacks.onboardingDismissBanner,
                                            )
                                        }
                                        Overview(model, callbacks, openDsh = { tab = 1 }, onOpenStorage = ::openStorageSettings)
                                    }
                                    else SettingsPage(model, callbacks, settingsSection, ::openStorageSettings) { settingsSection = it }
                                }
                            }
                        }
                    }
                }
                }
                    // 文件页的唯一宿主按查看器高度定位；其他页面由外壳统一定位。
                    if (tab != 2) progressEntry(Modifier.align(Alignment.BottomCenter).imePadding()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = padding.calculateBottomPadding() + FileViewerLayout.FloatingGap))
                }
            }
        }
        if (floatingBrowser && tab != 3) BrowserFloatingWindow(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            onClose = { floatingBrowser = false },
            onExpand = { floatingBrowser = false; tab = 3 },
            content = { floatingModifier -> agentBrowserPage(floatingModifier, false, { enabled ->
                if (enabled) { floatingBrowser = false; browserFullscreen = true; tab = 3 }
            }, null, 0.dp) })
    }
    val shownFailure = failureDetail
    FailureDetailSheet(
        failure = shownFailure,
        versionName = model.versionName,
        onAction = { kind -> shownFailure?.let { runFailureAction(it, kind) } },
        onDismiss = { failureDetail = null }
    )
}

@Composable
private fun TanukiHeader(tab: Int, title: String, c: TanukiUiCallbacks, stoppable: Boolean,
                         settings: () -> Unit, back: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(Modifier.statusBarsPadding().fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (tab == 4) IconButton(onClick = back) { TanukiIcon(TanukiGlyph.Back, "返回上一页") }
            if (tab == 0) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        TanukiIcon(TanukiGlyph.Terminal, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
            }
            Text(if (tab == 0) stringResource(R.string.app_name) else title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (stoppable && tab == 0) StopButton(c, true)
            if (tab != 4) IconButton(onClick = settings) { TanukiIcon(TanukiGlyph.Settings, "设置") }
        }
    }
}

@Composable
private fun Overview(model: TanukiUiModel, c: TanukiUiCallbacks, openDsh: () -> Unit, onOpenStorage: () -> Unit) =
    SetupOverview(model,c,openDsh,onOpenStorage)

@Composable
internal fun OperationCard(s: Snapshot, c: TanukiUiCallbacks, onOpenStorage: () -> Unit = {}) {
    // 组件卡、更新卡和运行恢复卡各自负责对应错误；这里只收留没有专属位置的失败。
    val failure = s.displayFailure()?.takeIf {
        s.failure == null || it.componentId !in setOf("ubuntu", "node", "dsh", "app") || it.domain == FailureDomain.FILE || it.domain == FailureDomain.UNKNOWN
    } ?: return
    InlineFailureBlock(
        failure = failure,
        onAction = { kind ->
            performFailureAction(failure, kind, c, onOpenStorage, c.checkUpdates, versionsAvailable = false)
        }
    )
}

/** 组件卡错误动作：重试、换源、清理与查看存储都落到现有入口。 */
private fun componentFailureAction(
    failure: AppFailure,
    component: RuntimeComponent,
    sources: List<RuntimeSource>,
    currentSource: String,
    c: TanukiUiCallbacks,
    onOpenStorage: () -> Unit,
    onViewVersions: () -> Unit
): (FailureActionKind) -> Unit = { kind ->
    performFailureAction(failure, kind, c, onOpenStorage, onViewVersions,
        onSwitchSource = {
            // 选一个不是当前源的镜像；切源后仍由用户点安装，避免静默重装。
            val next = sources.firstOrNull { it.id != currentSource } ?: sources.firstOrNull()
            if (next != null) c.selectSource(component, next.id)
        })
}

@Composable
internal fun WebViewCard(support: WebViewSupport, idle: Boolean, c: TanukiUiCallbacks) {
    val needsUpdate = support.state == WebViewSupportState.UPDATE_REQUIRED
    TanukiCard {
        Text(when (support.state) {
            WebViewSupportState.UPDATE_REQUIRED -> "升级 WebView，打开 DSH"
            WebViewSupportState.FAILED -> "WebView 检测未完成"
            WebViewSupportState.CHECKING -> "正在检查网页支持"
            else -> "先检测 WebView 能力"
        }, style = MaterialTheme.typography.titleMedium)
        if (needsUpdate) {
            Text("Chromium ${support.chromium ?: support.version} → 123+", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Caption(if (support.missing.isNotEmpty()) "实测缺失：${support.missing.joinToString("、")}" else "${cssResult("subgrid", support.subgrid)} · ${cssResult("light-dark()", support.lightDark)}")
            Caption("先保存任务并停止 DSH；更新内核可能关闭其他使用它的应用。")
            Button(onClick = c.updateWebView, enabled = idle, shape = MaterialTheme.shapes.medium) { Text("官方更新入口") }
            if (!idle) Caption("停止服务并等待当前操作完成后可更新。")
        } else {
            Caption(if (support.state == WebViewSupportState.FAILED) support.error.ifBlank { "请重新检测渲染能力。" } else "需要 Chromium 123+，并实测支持 subgrid 与 light-dark()。")
            TextButton(onClick = { c.command("WEBVIEW_CHECK") }, enabled = support.state != WebViewSupportState.CHECKING) { Text("检测 WebView") }
        }
    }
}

private val DSH_VERSION = Regex("[0-9]+\\.[0-9]+\\.[0-9]+.*")

@Composable
internal fun EnvironmentSection(model: TanukiUiModel, c: TanukiUiCallbacks, guideStep:Int?=null, onOpenStorage: () -> Unit = {}) {
    val s = model.snapshot
    val comps = model.components
    val guided=guideStep!=null
    val automatic=ComponentSourceOption("auto","自动择优","先测速再使用最快可用源，下载失败后自动换源")
    var showVersions by rememberSaveable { mutableStateOf(false) }
    var ubuntuVersionRequest by remember { mutableIntStateOf(0) }
    var nodeVersionRequest by remember { mutableIntStateOf(0) }
    var showInstalledDsh by rememberSaveable { mutableStateOf(false) }
    var selectedVersion by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = model.versions.firstOrNull { it.version == selectedVersion }
        ?: model.versions.firstOrNull { it.version == s.dsh }
        ?: model.versions.firstOrNull { it.version == RuntimeManager.DSH }
        ?: model.versions.firstOrNull { it.version == s.candidate } ?: model.versions.firstOrNull()
    val actionsEnabled = s.tanukiIdle && s.update != UpdateState.RECOVERY_REQUIRED
    val activeFailure = s.displayFailure()
    val ubuntuFailure = activeFailure?.takeIf { it.componentId == "ubuntu" }
    val nodeFailure = activeFailure?.takeIf { it.componentId == "node" }
    val dshFailure = activeFailure?.takeIf { it.componentId == "dsh" }
    val download = s.download
    val ubuntuProgress = download?.takeIf { it.component.equals("ubuntu", ignoreCase = true) }
        ?.let { ComponentProgressModel(s.stage, it.source, it.received, it.total, it.bytesPerSecond,it.reused) }
    val nodeProgress = download?.takeIf { it.component.equals("node", ignoreCase = true) }
        ?.let { ComponentProgressModel(s.stage, it.source, it.received, it.total, it.bytesPerSecond,it.reused) }

    val currentVersion = s.dsh.takeIf { it.matches(DSH_VERSION) }
    val currentRelease = model.versions.firstOrNull { it.version == currentVersion }
    val dshSwitching = s.update in setOf(UpdateState.STAGING, UpdateState.TESTING, UpdateState.BACKUP, UpdateState.SWITCHING)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
    if(!guided)Text("运行组件",Modifier.padding(start=16.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(
        shape=MaterialTheme.shapes.extraLarge,
        color=if(guided)Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
    Column {
    if(guideStep==null || guideStep==0)RuntimeComponentCard(
        model = RuntimeComponentCardModel(
            id = "ubuntu", title = "Ubuntu Base", version = if(comps.ubuntuInstalled)comps.ubuntuVersion else "未安装",
            installed = comps.ubuntuInstalled, sources = listOf(automatic)+model.ubuntuSources.map { ComponentSourceOption(it.id, it.title, it.detail) },
            selectedSource = if(comps.ubuntuAutomatic)"auto" else comps.ubuntuSource, actionsEnabled = actionsEnabled, canInstall = true,
            dependencyHint = "官方 ARM64 精简基础镜像，不含桌面；Node、DSH、项目和配置独立保留",
            progress = ubuntuProgress,
            versions=RuntimeCatalog.ubuntu.map { ComponentVersionOption(it.version,"Ubuntu Base ${it.version} LTS","ARM64 · ${it.size/(1024*1024)} MB · 精简基础镜像") },
            selectedVersion=comps.selectedUbuntuVersion,guided=guided,failure=ubuntuFailure),
        onSource = { id -> c.selectSource(RuntimeComponent.UBUNTU, id) },
        onInstall = { c.command("UBUNTU_INSTALL") },
        onReinstall = { c.command("UBUNTU_REINSTALL") },
        onUninstall = { c.command("UBUNTU_UNINSTALL") },
        onCancel = { c.command("CANCEL") },
        versionPickerRequest = ubuntuVersionRequest,
        onVersion={ c.selectVersion(RuntimeComponent.UBUNTU,it) },
        onFailureAction = ubuntuFailure?.let { failure ->
            componentFailureAction(failure, RuntimeComponent.UBUNTU, model.ubuntuSources, comps.ubuntuSource, c, onOpenStorage) { ubuntuVersionRequest++ }
        } ?: {},
        sourceControls={ SourceSpeedControls("Ubuntu",model.ubuntuSources.map { ComponentSourceOption(it.id,it.title,it.detail) },comps.ubuntuSource,
            model.ubuntuSpeed,actionsEnabled,!s.tanukiBusy && !model.nodeSpeed.running,
            {c.testSources(RuntimeComponent.UBUNTU)},{c.cancelSourceTests(RuntimeComponent.UBUNTU)},{c.selectSource(RuntimeComponent.UBUNTU,it)},automatic=comps.ubuntuAutomatic,
            probeKey=comps.selectedUbuntuVersion,onPrepare={c.prepareSources(RuntimeComponent.UBUNTU)}) })
    if(!guided)HorizontalDivider(Modifier.padding(horizontal=16.dp),color=MaterialTheme.colorScheme.outlineVariant)
    if(guideStep==null || guideStep==1)RuntimeComponentCard(
        model = RuntimeComponentCardModel(
            id = "node", title = "Node.js", version = if(comps.nodeInstalled)comps.nodeVersion else "未安装",
            installed = comps.nodeInstalled, sources = listOf(automatic)+model.nodeSources.map { ComponentSourceOption(it.id, it.title, it.detail) },
            selectedSource = if(comps.nodeAutomatic)"auto" else comps.nodeSource, actionsEnabled = actionsEnabled, canInstall = comps.ubuntuInstalled,
            dependencyHint = if (!comps.ubuntuInstalled) "请先安装 Ubuntu 才能安装 Node；已安装 Node 可独立卸载" else "",
            progress = nodeProgress,
            versions=RuntimeCatalog.node.map { ComponentVersionOption(it.version,"v${it.version}${if(it.lts) " · LTS" else " · 非 LTS"}","ARM64 · ${it.size/(1024*1024)} MB") },
            selectedVersion=comps.selectedNodeVersion,guided=guided,failure=nodeFailure,
            selectionNotice=if(model.nodeRequirement.isBlank())"安装前验证 Node.js、pnpm 和已安装 DSH 的兼容性。" else "当前 DSH 要求 Node.js ${model.nodeRequirement}；不兼容的版本将保留当前安装。"),
        onSource = { id -> c.selectSource(RuntimeComponent.NODE, id) },
        onInstall = { c.command("NODE_INSTALL") },
        onReinstall = { c.command("NODE_REINSTALL") },
        onUninstall = { c.command("NODE_UNINSTALL") },
        onCancel = { c.command("CANCEL") },
        versionPickerRequest = nodeVersionRequest,
        onVersion={ c.selectVersion(RuntimeComponent.NODE,it) },
        onFailureAction = nodeFailure?.let { failure ->
            componentFailureAction(failure, RuntimeComponent.NODE, model.nodeSources, comps.nodeSource, c, onOpenStorage) { nodeVersionRequest++ }
        } ?: {},
        sourceControls={ SourceSpeedControls("Node.js",model.nodeSources.map { ComponentSourceOption(it.id,it.title,it.detail) },comps.nodeSource,
            model.nodeSpeed,actionsEnabled,!s.tanukiBusy && !model.ubuntuSpeed.running,
            {c.testSources(RuntimeComponent.NODE)},{c.cancelSourceTests(RuntimeComponent.NODE)},{c.selectSource(RuntimeComponent.NODE,it)},automatic=comps.nodeAutomatic,
            probeKey=comps.selectedNodeVersion,onPrepare={c.prepareSources(RuntimeComponent.NODE)}) })

    if(!guided)HorizontalDivider(Modifier.padding(horizontal=16.dp),color=MaterialTheme.colorScheme.outlineVariant)
    val dshSummary = buildString {
        append(if (comps.dshInstalled) s.dsh else "官方开发引擎")
        model.dshUpdateCheck.badgeText()?.let { append(" · $it") }
    }
    if(guideStep==null || guideStep==3)ComponentDisclosure("DSH",dshSummary,comps.dshInstalled,dshSwitching,guided=guided,
        failure=dshFailure,
        onFailureAction={ kind ->
            val failure=dshFailure
            if (failure != null) performFailureAction(failure, kind, c, onOpenStorage,
                onViewVersions = { showVersions = true }, versionsAvailable = model.versions.isNotEmpty())
        }) {
        val dshProgress=download?.takeIf { it.component.equals("dsh",ignoreCase=true) }
        if(dshProgress!=null) {
            ComponentDownloadProgress(ComponentProgressModel(s.stage,dshProgress.source,dshProgress.received,dshProgress.total,dshProgress.bytesPerSecond,dshProgress.reused))
        } else if (dshSwitching) {
            Text(s.stage, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Caption("解析、校验与安装无法准确估算总量；上方显示耗时和当前进展。")
        }
        DshUpdateStatus(model.dshUpdateCheck, c.checkDshUpdate)
        SourceSpeedControls("DSH 依赖",model.npmSources.map { ComponentSourceOption(it.id,it.title,it.detail) },model.npmSelection.id,
            model.npmSpeed,actionsEnabled,!s.tanukiBusy && !model.ubuntuSpeed.running && !model.nodeSpeed.running,
            {c.testNpmSources(selected?.version ?: RuntimeManager.DSH)},c.cancelNpmSourceTests,c.selectNpmSource,
            automatic=model.npmSelection.automatic,probeKey=selected?.version ?: RuntimeManager.DSH,
            onPrepare={c.prepareNpmSources(selected?.version ?: RuntimeManager.DSH)})
        Caption("依赖归档自动选源，失败时换源；版本与校验信息仍来自 npm 官方。")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            Button(onClick={if(model.versions.isEmpty())c.checkUpdates() else showVersions=true},enabled=!s.tanukiBusy,
                modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=MaterialTheme.shapes.medium) {
                Text(if(model.versions.isEmpty())"获取官方版本" else "选择版本安装")
            }
            TextButton(onClick=c.checkUpdates,enabled=!s.tanukiBusy,modifier=Modifier.heightIn(min=48.dp)) { Text("刷新版本列表") }
        }
        // 已安装清单与“官方发布列表”分开：前者管理本机程序，后者负责下载/安装。
        OutlinedButton(onClick={showInstalledDsh=true},enabled=!s.tanukiBusy,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=MaterialTheme.shapes.medium) {
            Text("已安装版本（${comps.installedDshVersions.size}）")
        }
        if(comps.dshInstalled && !guided) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                OutlinedButton(onClick={currentRelease?.let(c.reinstallDsh)},
                    enabled=currentRelease!=null && s.tanukiIdle && comps.ubuntuInstalled && comps.nodeInstalled,
                    modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=MaterialTheme.shapes.medium) { Text("重新安装") }
                // 当前使用版本禁止卸载；多余版本统一在“已安装版本”中管理。
                TextButton(onClick={showInstalledDsh=true},enabled=s.tanukiIdle,modifier=Modifier.heightIn(min=48.dp)) { Text("管理版本") }
            }
            if(currentRelease==null)Caption("重新安装前请先获取官方版本列表。")
        }
        if(!guided)TanukiDetails("更新与恢复") {
            Fact("更新状态",s.update.label)
            if(s.candidate.isNotBlank())Fact("官方候选",s.candidate)
            Caption("选择官方发布后确认安装；切换数据版本前会执行检查并保留匹配快照。")
            if(s.update==UpdateState.RECOVERY_REQUIRED) {
                ActionRow("检查并保留当前版本","不自动回滚项目",enabled=s.tanukiIdle) { c.command("RESUME_UPDATE") }
            }
            ActionRow("恢复匹配快照","恢复程序、配置和会话；项目不回滚",enabled=s.tanukiIdle) { c.command("RESTORE") }
        }
    }
    }
    }
    }

    if (showVersions) AlertDialog(onDismissRequest = { showVersions = false },
        title = { Text("选择 DSH 版本") },
        text = {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            LazyColumn(Modifier.weight(1f,fill=false).heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(model.versions, key = { it.version }) { release ->
                    Row(Modifier.fillMaxWidth().selectable(selected = selected?.version == release.version, role = Role.RadioButton,
                        onClick = { selectedVersion = release.version }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected?.version == release.version, onClick = null)
                        Column(Modifier.padding(start = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(release.version, style = MaterialTheme.typography.titleMedium)
                                if (release.version == currentVersion) StatusPill("当前", true)
                                if (release.version in comps.installedDshVersions) StatusPill("已安装", false)
                            }
                            Caption(if (release.verified) "已通过本机启动检查（历史记录）" else "尚未通过本机启动检查")
                        }
                    }
                }
                item {
                    selected?.let { release ->
                        TanukiDetails("所选版本详情 · ${release.version}") {
                            Caption("Node.js 要求：${release.node}")
                            Caption(release.notes)
                            SelectableCopy("来源：${release.source}\n完整性：${release.integrity}")
                            Caption("安装备用只保存程序，不切换当前数据。较旧版本需先恢复匹配的数据快照才能使用。")
                            Caption("验证记录不代表所有模型与数据迁移均兼容。")
                        }
                    }
                }
            }
            OutlinedButton(enabled=selected!=null && s.tanukiIdle && comps.ubuntuInstalled && comps.nodeInstalled,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),onClick={selected?.let(c.cacheDsh);showVersions=false}) { Text("安装为备用程序") }
            if(selected!=null && selected.version in comps.installedDshVersions && selected.version != s.dsh) {
                TextButton(enabled=s.tanukiIdle,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                    onClick={c.uninstallDsh(selected);showVersions=false}) { Text("卸载该备用版本") }
            }
            if(selected!=null && selected.version == s.dsh && selected.version in comps.installedDshVersions) {
                Caption("当前使用版本不能卸载；如需清理请先在“已安装版本”中切换到其他版本。")
            }
            }
        },
        confirmButton = {
            Row {
                TextButton(enabled = selected != null && s.tanukiIdle && comps.ubuntuInstalled && comps.nodeInstalled &&
                    s.update != UpdateState.RECOVERY_REQUIRED,
                    onClick = { selected?.let(c.install); showVersions = false }) { Text("安装并使用") }
            }
        },
        dismissButton = { TextButton(onClick = { showVersions = false }) { Text("取消") } })
    if(showInstalledDsh) DshVersionManagerDialog(
        activeVersion = s.dsh,
        idle = s.tanukiIdle,
        inventoryKey = comps.installedDshVersions,
        load = c.loadInstalledDsh,
        onSwitch = c.switchInstalledDsh,
        onUninstall = c.uninstallInstalledDsh,
        onDismiss = { showInstalledDsh = false })
    if(!guided)TanukiCard {
        TanukiDetails("来源与完整性") {
            val ubuntu=RuntimeCatalog.find(RuntimeComponent.UBUNTU,comps.selectedUbuntuVersion)!!
            val node=RuntimeCatalog.find(RuntimeComponent.NODE,comps.selectedNodeVersion)!!
            Caption("Ubuntu、Node.js 与 DSH 分层安装；Ubuntu 镜像和 Node.js 版本按官方 SHA-256 校验，所有下载源必须提供相同文件。")
            SelectableCopy("Ubuntu ${ubuntu.version} SHA-256：${ubuntu.sha256}\nNode.js ${node.version} SHA-256：${node.sha256}")
            Caption("Ubuntu 附加工具由签名 apt 软件源补齐；PRoot 不提供恶意代码隔离。")
            OutlinedButton(onClick={c.command("BASE")},enabled=actionsEnabled,modifier=Modifier.heightIn(min=48.dp)) { Text("验证 Ubuntu 基础启动") }
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        TanukiDetails("运行诊断") {
        Fact("助手", s.service.label)
        Fact("本机 Web 健康", if (s.webHealthy) "就绪" else "未就绪")
        Fact("网页认证", s.web.label)
        Fact("健康检查", s.health.label)
        Fact("DSH 沙箱探测", s.confinement.label)
        Fact("启动模式", s.recovery.mode.label)
        s.recovery.lastFailure?.let {
            Fact("最近失败", FailureCatalog.fromRuntimeFailure(it, s.recovery, s.error, s.recovery.lastFailureAtMillis).reason)
        }
        Caption("模型上下文会发送至你选择的 API 服务商。工具执行在 DSH 中逐次确认；停止后的任务不会自动重放。")
        ActionRow("重启 DSH", "可能中断当前任务", enabled = s.environment == EnvironmentState.READY && !s.tanukiBusy && s.update != UpdateState.RECOVERY_REQUIRED) { c.command("RESTART") }
        ActionRow("固定健康检查", enabled = s.tanukiIdle && s.environment == EnvironmentState.READY) { c.command("HEALTH") }
        ActionRow("一分钟后台自检", "专用目录计数 60 次，不调用模型 API", enabled = !s.tanukiBusy && s.environment == EnvironmentState.READY) { c.command("BACKGROUND") }
        Text(s.stage, style = MaterialTheme.typography.bodySmall)
        SelectableCopy(model.backgroundResult ?: "暂无完整的后台自检结果")
        TanukiDetails("设备信息") { SelectableCopy(model.deviceInfo) }
        TanukiDetails("最近事件") { SelectableCopy(s.events.takeLast(12).joinToString("\n").ifBlank { "暂无事件" }) }
        TanukiDetails("脱敏日志") {
            val logs = model.liveLogs?.collectAsState()?.value ?: model.recentLogs
            // 热/低电时只减少界面展示行数，不影响落盘日志或正在执行的任务。
            SelectableCopy(logs.takeLast(model.deviceCondition.logLineLimit.coerceAtLeast(1)).joinToString("\n").ifBlank { "暂无日志" })
        }
        s.displayFailure()?.let { failure ->
            Text(failure.reason, style = MaterialTheme.typography.bodyMedium)
            FailureTechnicalDetails(failure.detail, failure.stableKey)
        }
        }
    }
}

@Composable
private fun SettingsPage(model: TanukiUiModel, c: TanukiUiCallbacks, section: String?, onOpenStorage: () -> Unit, select: (String) -> Unit) {
    val s = model.snapshot
    if (section == null) {
        val categories = listOf(
            "外观与语言" to "主题、语言和字体大小",
            "通知与后台" to "对话提醒、更新通知与后台运行",
            "存储与空间" to "工作区限额、空间用量与项目备份",
            "引擎与更新" to "更新应用、维护运行环境与网页组件",
            "远程主机" to "连接自己的电脑，使用电脑上的助手",
            "关于与诊断" to "检查运行状态、清理缓存与查看许可")
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("使用偏好", style = MaterialTheme.typography.headlineMedium)
            Caption("选择要调整的设置。")
        }
        Surface(
            Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = TanukiShapeTokens.card,
        ) {
            Column { categories.forEachIndexed { index, (title, detail) ->
                ActionRow(
                    title,
                    detail,
                    contentPadding = PaddingValues(horizontal = TanukiSpace.XL_DP.dp, vertical = TanukiSpace.MD_DP.dp),
                ) { select(title) }
                if (index != categories.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(horizontal = TanukiSpace.XL_DP.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            } }
        }
        return
    }
    if (section == "外观与语言") {
        AppearanceSettings(select)
        return
    }
    if (section == "通知与后台") {
        NotificationAndBackgroundSettings(model, c)
        return
    }
    if (section == "存储与空间") {
        TanukiCard {
            WorkspaceStorageControls(model.workspaceStorage, s.tanukiIdle,
                onRefresh = c.refreshWorkspaceStorage, onSave = c.saveWorkspaceCapacity)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TanukiDetails("其他目录占用") {
                Text(model.storageUsage, style = MaterialTheme.typography.bodyMedium)
                ActionRow("统计空间用量", "查看各目录实际占用的存储空间", onClick = c.measureStorage)
                ActionRow("整理安装缓存", "保留工具、项目与版权文件", enabled = s.tanukiIdle && s.environment == EnvironmentState.READY) { c.command("SLIM") }
            }
        }
        SettingsGroup("项目与备份") {
            ActionRow("导入导出与备份", "导入项目、导出文件或备份应用数据", onClick = { select("项目与备份") })
            Caption("导出项目便于分享；应用数据备份用于恢复配置和对话。")
        }
        return
    }
    if (section == "引擎与更新") {
        SettingsGroup("更新与组件") {
            ActionRow("应用更新", "当前 ${model.versionName} · 更新应用并保留数据", onClick = { select("应用更新") })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActionRow("组件维护与诊断", "管理运行环境和助手版本，检查运行状态", onClick = { select("组件维护与诊断") })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActionRow("网页与兼容性", "选择对话界面，更新或检查网页组件", onClick = { select("网页与兼容性") })
        }
        Caption("更新应用或运行环境前，请先保存正在编辑的内容。")
        return
    }
    if (section == "关于与诊断") {
        SettingsGroup("诊断与高级") {
            ActionRow("运行诊断与组件状态", "查看助手状态，排查启动或连接问题", onClick = { select("组件维护与诊断") })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActionRow("高级选项与许可", "调整旧网页组件兼容性，查看许可和来源", onClick = { select("高级与关于") })
        }
        SettingsGroup("首次启动引导") {
            ActionRow("重新查看引导", "重新选择运行方式和对话界面", onClick = c.onboardingOpen)
            Caption("已完成的步骤会保留；跳过的步骤可在工作台继续。")
        }
        SettingsGroup("关于") {
            ReadOnlyRow("应用版本", model.versionName)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ReadOnlyRow("架构与许可", "ARM64 · GPL-3.0-or-later")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ActionRow("清除下载缓存", "保留运行环境和项目", enabled = s.tanukiIdle) { c.command("CACHE") }
        }
        Caption("备份入口在“存储与空间”，连接电脑请返回设置选择“远程主机”。")
        return
    }
    if (section == "应用更新") {
        ApplicationUpdateCard(model.appUpdate, model.versionName, model.appInstallAllowed,
            c.checkAppUpdates, c.downloadAppUpdate, c.installAppUpdate, c.cancelAppUpdate,
            background = model.apkUpdateCheck,
            onCleanCache = { c.command("CACHE") },
            onOpenStorage = onOpenStorage,
            onOpenSettings = c.appSettings)
        return
    }
    if (section == "组件维护与诊断") { EnvironmentSection(model, c, onOpenStorage = onOpenStorage); return }
    if (section == "项目与备份") SettingsGroup("项目与备份") {
        ActionRow("导入文件", "保存为新项目", enabled = s.tanukiIdle, onClick = c.importFiles)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ActionRow("导出项目", "不含助手的对话与密钥；分享前请检查项目中的敏感文件", enabled = s.tanukiIdle) { c.command("EXPORT") }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        TanukiDetails("应用数据备份") {
            ActionRow("备份应用数据", "包含配置、账号信息、扩展和对话", enabled = s.tanukiIdle) { c.command("BACKUP") }
            ActionRow("恢复应用数据", "覆盖备份后的配置和对话，项目文件保持当前内容", enabled = s.tanukiIdle) { c.command("RESTORE") }
            Caption("应用数据备份只保存在应用内，卸载即丢失；不能代替项目导出。")
        }
    }
    if (section == "网页与兼容性") SettingsGroup("网页与兼容性") {
        ToggleRow("使用手机界面", model.nativeDshPreferred, c.toggleNativeDsh)
        Caption("开启后用手机界面对话；不可用时回到网页界面。两者共用对话，切换不中断助手。")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ActionRow("管理对话", "查找和整理对话，选择模型与权限", onClick = c.openSessionManager)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ActionRow("更新网页组件", "当前版本 ${model.support.version}", enabled = s.tanukiIdle, onClick = c.updateWebView)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ActionRow("网页组件设置", enabled = s.tanukiIdle, onClick = c.webViewSettings)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ActionRow("重新检查", "会重新加载网页，请先保存草稿", enabled = model.support.state != WebViewSupportState.CHECKING) { c.command("WEBVIEW_CHECK") }
        TanukiDetails("检测详情") {
            Text(model.support.provider, style = MaterialTheme.typography.bodySmall)
            Caption("要求 Chromium 123+；${cssResult("subgrid", model.support.subgrid)} · ${cssResult("light-dark()", model.support.lightDark)}")
            SelectableCopy(model.support.description)
            Caption("JavaScript 兼容层不能升级系统内核或补齐 CSS 渲染能力。")
        }
    }
    if (section == "高级与关于") SettingsGroup("高级与关于") {
        TanukiDetails("高级选项") {
            ToggleRow("兼容旧网页组件", model.legacyShim, c.toggleLegacy)
            Caption("尝试改善旧网页组件的使用体验；无法解决显示问题。修改后需重新连接。")
            ToggleRow("网页文件导出", model.blobExport, c.toggleBlob)
            Caption("允许保存网页界面生成的文件，每次需选择保存位置，上限 8 MiB；重新连接后生效。")
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ActionRow("许可与组件来源", "${model.versionName} · ARM64 · GPL-3.0-or-later", onClick = c.showNotices)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ActionRow("清除下载缓存", "保留运行环境和项目", enabled = s.tanukiIdle) { c.command("CACHE") }
    }
}

@Composable
private fun AppearanceSettings(select: (String) -> Unit) {
    SettingsGroup("主题") {
        ReadOnlyRow("当前主题", "跟随系统")
        Caption("跟随系统切换浅色或深色；手动切换暂不支持。网页主题请在网页界面设置。")
    }
    SettingsGroup("语言") {
        ReadOnlyRow("界面语言", "简体中文")
        Caption("手机界面仅提供简体中文，切换语言暂不支持。网页语言请在网页界面设置。")
    }
    SettingsGroup("显示与常用入口") {
        ReadOnlyRow("字体大小", "跟随系统")
        Caption("跟随系统字体大小；单独调整暂不支持。")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ActionRow("通知与后台", "对话提醒、更新通知与后台运行") { select("通知与后台") }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ActionRow("存储与空间", "工作区限额、空间用量与项目备份") { select("存储与空间") }
    }
}

@Composable
private fun NotificationAndBackgroundSettings(model: TanukiUiModel, c: TanukiUiCallbacks) {
    SettingsGroup("通知") {
        ToggleRow("对话通知", model.chatNotifications.permission.userEnabled, c.toggleChatNotifications)
        Caption("对话不可见时，提醒本次回复完成、待确认、出错或待授权；不含对话内容。")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ActionRow("对话通知设置", model.chatNotifications.summaryText(), onClick = c.chatNotifications)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ActionRow("更新通知权限", "允许后提醒新版本；拒绝仍可手动检查，可在系统通知设置修改", onClick = c.notifications)
    }
    SettingsGroup("后台与启动") {
        ToggleRow("打开应用时启动助手", model.earlyStart, c.toggleEarlyStart)
        Caption("上次正常停止后，打开应用会自动启动助手；忙碌或上次任务中断时需手动启动。")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        ActionRow("电池与应用设置", "管理后台与锁屏行为", onClick = c.appSettings)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DeviceConditionSettings(model.deviceCondition, c)
        TanukiDetails("后台运行说明") {
            Caption("关闭自动启动后，需手动启动助手；后台和息屏运行仍受系统限制。")
            Caption("若后台提醒延迟，请在系统通知、电池或自启动设置中允许 Tanuki。")
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    // 分区标题使用说明字号 + 次级文字色；卡片统一为 20dp 圆角和抬升表面。
    Column(verticalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp)) {
        Text(title, Modifier.padding(horizontal = TanukiSpace.XS_DP.dp), style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            shape = TanukiShapeTokens.card,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(horizontal = TanukiSpace.XL_DP.dp, vertical = TanukiSpace.XS_DP.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun DshPage(model: TanukiUiModel, c: TanukiUiCallbacks, browser: WebView, modifier: Modifier,
                    home: () -> Unit, fullscreen: Boolean, onFullscreen: (Boolean) -> Unit, settings: () -> Unit, bottomInset: Dp,
                    onOpenStorage: () -> Unit,
                    native: Boolean, nativePage: @Composable (Modifier) -> Unit, fallback: String?,
                    openBrowserWindow: () -> Unit) {
    val s = model.snapshot
    val density = LocalDensity.current
    var headerPixels by remember { mutableIntStateOf(with(density) { 60.dp.roundToPx() }) }
    val topInset = if (fullscreen) 0.dp else with(density) { headerPixels.toDp() }
    val remoteActive = s.remote.connected
    val showBrowser = (s.service == ServiceState.RUNNING || remoteActive) && model.support.state == WebViewSupportState.SUPPORTED && s.web != WebState.RENDER_ERROR && model.cookieReady
    val originalLayer = remember(browser) { browser.layerType }
    DisposableEffect(browser, fullscreen) {
        // On some WebView/GPU combinations the direct draw path goes blank when system
        // bars are hidden. A dedicated HARDWARE layer keeps immersive pixels composited;
        // release it on exit rather than retaining a full-screen texture in normal mode.
        browser.setLayerType(if (fullscreen) android.view.View.LAYER_TYPE_HARDWARE else originalLayer, null)
        onDispose { browser.setLayerType(originalLayer, null) }
    }
    Box(modifier.fillMaxSize().imePadding()) {
        // 原生页不持有 WebView；官方页仍是同一个 WebView 实例，切换界面不会重建或重启 DSH。
        if (native) {
            // 崩溃或启动失败时，恢复卡直接留在当前页，不必先返回工作台。
            Column(Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset)) {
                if (s.displayFailure()?.domain == FailureDomain.RUNTIME || s.recovery.mode != RecoveryMode.NORMAL || s.recovery.lastFailure != null) {
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        RuntimeRecoveryCard(s, c, model.components, onOpenStorage)
                    }
                }
                Box(Modifier.weight(1f)) {
                    nativePage(
                        Modifier.fillMaxSize()
                            .then(if (fullscreen) Modifier.statusBarsPadding() else Modifier)
                    )
                }
            }
        } else if (s.web != WebState.RENDER_ERROR) key(browser) {
            // 正常模式预留导航及其间隙；键盘或全屏隐藏导航时 bottomInset 为零。
            AndroidView(factory = { context ->
                android.widget.FrameLayout(context).apply {
                    clipChildren = true; clipToPadding = true
                    (browser.parent as? ViewGroup)?.removeView(browser)
                    addView(browser, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            }, modifier = Modifier.fillMaxSize().padding(top = topInset, bottom = bottomInset).clipToBounds(),
                update = {
                    browser.visibility = if (showBrowser) android.view.View.VISIBLE else android.view.View.INVISIBLE
                })
        }
        if (!native && !showBrowser) Column(Modifier.fillMaxSize().padding(top = topInset).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp + bottomInset), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TanukiCard {
                TanukiIcon(TanukiGlyph.Terminal, tint = MaterialTheme.colorScheme.primary)
                Text(when {
                    remoteActive -> "远程 DSH 已连接"
                    s.service != ServiceState.RUNNING -> "助手尚未启动"
                    s.web == WebState.RENDER_ERROR -> "网页渲染已中断"
                    else -> "先准备兼容的网页内核"
                }, style = MaterialTheme.typography.titleLarge)
                if (remoteActive) {
                    Fact("远程主机", s.remote.host)
                    Fact("SSH 隧道", "127.0.0.1:${s.remote.localPort ?: "分配中"}")
                    Fact("主机密钥", s.remote.fingerprint)
                    Caption("远程 token 只通过本机 SSH 隧道传输；本地运行时保持原状。")
                } else {
                    Fact("助手", s.service.label)
                    Fact("对话连接", if (s.webHealthy) "就绪" else "未就绪")
                    Fact("启动模式", s.recovery.mode.label)
                    s.recovery.lastFailure?.let {
            Fact("最近失败", FailureCatalog.fromRuntimeFailure(it, s.recovery, s.error, s.recovery.lastFailureAtMillis).reason)
        }
                }
                Caption("网页状态：${s.web.label}")
                if (!remoteActive && s.service != ServiceState.RUNNING) Button(onClick = { c.command(if (s.environment == EnvironmentState.READY) "START" else "INIT") },
                    enabled = !s.tanukiBusy && s.update != UpdateState.RECOVERY_REQUIRED) { Text(if (s.environment == EnvironmentState.READY) "启动助手" else "准备运行环境") }
                if (remoteActive) OutlinedButton(onClick = { c.disconnectRemoteHost() }) { Text("断开远程并切回本地") }
                if (s.web == WebState.RENDER_ERROR) OutlinedButton(onClick = { c.command("RECONNECT") }, enabled = s.service == ServiceState.RUNNING || remoteActive) { Text("恢复网页连接") }
                if (s.remote.error.isNotBlank()) Caption(s.remote.error)
            }
            RuntimeRecoveryCard(s, c, model.components, onOpenStorage)
            OperationCard(s, c, onOpenStorage)
            if (model.support.state != WebViewSupportState.SUPPORTED) WebViewCard(model.support, s.tanukiIdle, c)
        }
        if (!native && fallback != null && s.service == ServiceState.RUNNING && model.nativeDshPreferred) Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.align(Alignment.TopCenter).zIndex(0.6f).padding(top = topInset + 8.dp, start = 20.dp, end = 20.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("原生界面不可用，已显示官方界面", style = MaterialTheme.typography.labelLarge)
                    Text(fallback, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = c.retryNativeDsh) { Text("重试") }
            }
        }
        if (!fullscreen) Surface(color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.align(Alignment.TopCenter).zIndex(1f).onSizeChanged { headerPixels = it.height }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = home) { TanukiIcon(TanukiGlyph.Back, "返回概览") }
                Column(Modifier.weight(1f)) {
                    Text("DSH", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            native -> "原生界面"
                            remoteActive -> "远程 · ${s.remote.host}"
                            else -> s.web.label
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = openBrowserWindow) { TanukiIcon(TanukiGlyph.Browser, "浏览器小窗") }
                IconButton(onClick = { onFullscreen(true) }) { TanukiIcon(TanukiGlyph.Expand, "进入全屏") }
                if (native) {
                    IconButton(onClick = c.retryNativeDsh, enabled = s.service == ServiceState.RUNNING) {
                        TanukiIcon(TanukiGlyph.Refresh, "刷新原生会话")
                    }
                } else {
                    IconButton(
                        onClick = { c.command("RECONNECT") },
                        enabled = (s.service == ServiceState.RUNNING || remoteActive) &&
                            model.support.state == WebViewSupportState.SUPPORTED
                    ) {
                        TanukiIcon(TanukiGlyph.Refresh, "重新连接 DSH 网页")
                    }
                }
                IconButton(onClick = settings) { TanukiIcon(TanukiGlyph.Settings, "设置") }
            }
        }
        if (fullscreen) Surface(
            shape = RoundedCornerShape(25.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.align(Alignment.TopEnd).zIndex(1f).statusBarsPadding().padding(end = 12.dp, top = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = openBrowserWindow, modifier = Modifier.size(48.dp)) { TanukiIcon(TanukiGlyph.Browser, "浏览器小窗") }
                IconButton(onClick = { onFullscreen(false) }, modifier = Modifier.size(48.dp)) {
                    TanukiIcon(TanukiGlyph.Contract, "退出全屏")
                }
            }
        }
    }
}

@Composable
private fun DshUpdateStatus(state: DshUpdateCheckState, onCheck: () -> Unit) {
    val lastChecked = remember(state.lastCheckedAt) {
        if (state.lastCheckedAt <= 0L) "尚未检查"
        else runCatching { java.text.SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(java.util.Date(state.lastCheckedAt)) }
            .getOrDefault("未知")
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("版本更新", style = MaterialTheme.typography.titleSmall)
                state.badgeText()?.let { StatusPill(it, true) }
            }
            Text(when {
                state.installedVersion.isBlank() -> "尚未安装 DSH；安装后可比较官方最新版本。"
                state.available && state.latestVersion.isNotBlank() -> "已安装 ${state.installedVersion} · 最新 ${state.latestVersion}"
                else -> "已安装 ${state.installedVersion} · 已是最新版本"
            }, style = MaterialTheme.typography.bodySmall)
            Caption(buildString {
                append("后台每 24 小时检查一次（需联网）")
                append(" · 上次 $lastChecked")
                if (state.source.isNotBlank()) append(" · ${state.source}")
                if (state.status == com.tanuki.update.UpdateCheckStatus.FAILED) append(" · 上次未成功，将按周期重试")
            })
        }
        TextButton(onClick = onCheck, enabled = !state.checking,
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "检查 DSH 更新" }) {
            Text(if (state.checking) "检查中…" else "检查更新")
        }
    }
}

@Composable
private fun ComponentDisclosure(title:String,version:String,installed:Boolean,forceExpanded:Boolean=false,guided:Boolean=false,
                                failure:AppFailure?=null,onFailureAction:(FailureActionKind)->Unit={},content:@Composable ColumnScope.()->Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    LaunchedEffect(forceExpanded, failure?.stableKey) { if (forceExpanded || failure != null) expanded = true }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = tanukiMotionMs(TanukiMotionTokens.toggle)),
        label = "componentChevron",
    )
    Column(Modifier.fillMaxWidth()) {
        if(!guided)Row(Modifier.fillMaxWidth().clickable(role=Role.Button,onClickLabel=if(expanded)"收起$title" else "展开$title") { expanded=forceExpanded || !expanded }
            .semantics { stateDescription=if(expanded)"已展开" else "已收起" }.padding(horizontal=20.dp,vertical=16.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title,style=MaterialTheme.typography.titleMedium)
                Text(version,style=MaterialTheme.typography.labelMedium,fontFamily=FontFamily.Monospace,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(when { failure!=null -> "安装没有完成"; installed -> "已安装"; else -> "未安装" },failure==null && installed)
            Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {
                TanukiIcon(TanukiGlyph.Chevron,modifier=Modifier.size(20.dp).rotate(rotation),tint=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // 折叠状态下也保留错误原因与主行动，避免用户为了重试还要先展开卡片。
        failure?.let {
            InlineFailureBlock(
                failure = it,
                onAction = onFailureAction,
                modifier = Modifier.padding(
                    start = if (guided) 0.dp else TanukiSpace.XL_DP.dp,
                    end = if (guided) 0.dp else TanukiSpace.XL_DP.dp,
                    bottom = TanukiSpace.MD_DP.dp,
                ),
            )
        }
        TanukiExpandVisibility(visible = expanded || guided) {
            Column(
                Modifier.padding(
                    start = if (guided) 0.dp else TanukiSpace.XL_DP.dp,
                    end = if (guided) 0.dp else TanukiSpace.XL_DP.dp,
                    bottom = if (guided) 0.dp else TanukiSpace.XL_DP.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(TanukiSpace.MD_DP.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun TanukiCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = TanukiShapeTokens.card,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(with(LocalDensity.current) { 1.toDp() }, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(TanukiSpace.XL_DP.dp),
            verticalArrangement = Arrangement.spacedBy(TanukiSpace.MD_DP.dp),
            content = content,
        )
    }
}

@Composable
private fun Caption(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Fact(name: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun StatusPill(text: String, active: Boolean) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(7.dp).background(if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,RoundedCornerShape(50)))
        Text(text,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StopButton(c: TanukiUiCallbacks, enabled: Boolean) {
    TextButton(onClick = { c.command("STOP") }, enabled=enabled, modifier=Modifier.heightIn(min=48.dp),contentPadding = PaddingValues(horizontal = 12.dp)) {
        TanukiIcon(TanukiGlyph.Stop, "停止助手", modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(4.dp)); Text("停止")
    }
}

@Composable
private fun ReadOnlyRow(title: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = TanukiSpace.MD_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionRow(title: String, detail: String? = null, enabled: Boolean = true,
                      contentPadding: PaddingValues = PaddingValues(vertical = TanukiSpace.MD_DP.dp), onClick: () -> Unit) {
    val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else tanukiDisabledContentColor()
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(contentPadding), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = TanukiSpace.MD_DP.dp), verticalArrangement = Arrangement.spacedBy(TanukiSpace.XS_DP.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = contentColor)
            if (detail != null) Caption(detail)
        }
        TanukiIcon(TanukiGlyph.Chevron, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else tanukiDisabledContentColor(), modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun TanukiDetails(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // 折叠箭头走反馈令牌，展开内容走结构令牌；系统移除动画时都立即完成。
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(durationMillis = tanukiMotionMs(TanukiMotionTokens.toggle)),
        label = "detailsChevron",
    )
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(role=Role.Button,onClickLabel=if(expanded)"收起" else "展开") { expanded=!expanded }
            .semantics { stateDescription=if(expanded)"已展开" else "已收起" },verticalAlignment=Alignment.CenterVertically) {
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            TanukiIcon(TanukiGlyph.Chevron,modifier=Modifier.size(20.dp).rotate(rotation),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TanukiExpandVisibility(visible = expanded) {
            Column(
                Modifier.padding(top = TanukiSpace.MD_DP.dp),
                verticalArrangement = Arrangement.spacedBy(TanukiSpace.MD_DP.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f).padding(end = TanukiSpace.MD_DP.dp), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            // 开关的轨道/滑块取主操作与边框令牌，避免默认 Material 紫色。
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}

@Composable
internal fun SelectableCopy(text: String, error: Boolean = false) {
    SelectionContainer {
        Text(text, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun cssResult(name: String, supported: Boolean?): String = "$name ${when (supported) { true -> "支持"; false -> "缺失"; null -> "待检测" }}"
private fun byteSize(bytes: Long): String = when {
    bytes < 0 -> "未知"
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f GiB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024))
    bytes >= 1024 -> String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
    else -> "$bytes B"
}

/** Small original line symbols; no icon font or additional dependency. */
@Composable
internal fun TanukiIcon(glyph: TanukiGlyph, description: String? = null, modifier: Modifier = Modifier.size(24.dp), tint: Color = LocalContentColor.current) {
    Canvas(if (description == null) modifier else modifier.semantics { contentDescription = description }) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round)
            val path = Path()
            when (glyph) {
                TanukiGlyph.Home -> {
                    path.moveTo(3f, 10f); path.lineTo(12f, 3f); path.lineTo(21f, 10f)
                    path.moveTo(5f, 9f); path.lineTo(5f, 21f); path.lineTo(10f, 21f); path.lineTo(10f, 15f)
                    path.lineTo(14f, 15f); path.lineTo(14f, 21f); path.lineTo(19f, 21f); path.lineTo(19f, 9f)
                }
                TanukiGlyph.Terminal -> {
                    drawRoundRect(tint, Offset(2f, 4f), Size(20f, 16f), CornerRadius(3f), style = stroke)
                    path.moveTo(6f, 9f); path.lineTo(9f, 12f); path.lineTo(6f, 15f)
                    path.moveTo(12f, 15f); path.lineTo(17f, 15f)
                }
                TanukiGlyph.Folder -> {
                    path.moveTo(3f, 7f); path.lineTo(3f, 5f); path.lineTo(10f, 5f); path.lineTo(12f, 8f)
                    path.lineTo(21f, 8f); path.lineTo(21f, 20f); path.lineTo(3f, 20f); path.close()
                }
                TanukiGlyph.File -> {
                    path.moveTo(5f, 3f); path.lineTo(14f, 3f); path.lineTo(20f, 9f); path.lineTo(20f, 21f)
                    path.lineTo(5f, 21f); path.close(); path.moveTo(14f, 3f); path.lineTo(14f, 9f); path.lineTo(20f, 9f)
                    path.moveTo(9f, 13f); path.lineTo(16f, 13f); path.moveTo(9f, 17f); path.lineTo(16f, 17f)
                }
                TanukiGlyph.Browser -> {
                    drawCircle(tint, 9f, Offset(12f, 12f), style = stroke)
                    drawOval(tint, Offset(8f, 3f), Size(8f, 18f), style = stroke)
                    path.moveTo(3f, 12f); path.lineTo(21f, 12f)
                }
                TanukiGlyph.Layers -> {
                    path.moveTo(3f, 7f); path.lineTo(12f, 3f); path.lineTo(21f, 7f); path.lineTo(12f, 11f); path.close()
                    path.moveTo(3f, 12f); path.lineTo(12f, 16f); path.lineTo(21f, 12f)
                    path.moveTo(3f, 17f); path.lineTo(12f, 21f); path.lineTo(21f, 17f)
                }
                TanukiGlyph.Settings -> {
                    for ((y, x) in listOf(6f to 8f, 12f to 16f, 18f to 10f)) {
                        drawLine(tint, Offset(3f, y), Offset(x - 3f, y), 1.8f, StrokeCap.Round)
                        drawLine(tint, Offset(x + 3f, y), Offset(21f, y), 1.8f, StrokeCap.Round)
                        drawCircle(tint, 2.5f, Offset(x, y), style = stroke)
                    }
                }
                TanukiGlyph.Back -> { path.moveTo(13f, 5f); path.lineTo(6f, 12f); path.lineTo(13f, 19f); path.moveTo(6f, 12f); path.lineTo(21f, 12f) }
                TanukiGlyph.Refresh -> {
                    drawArc(tint, 35f, 285f, false, Offset(4f, 4f), Size(16f, 16f), style = stroke)
                    path.moveTo(20f, 3f); path.lineTo(20f, 9f); path.lineTo(14f, 9f)
                }
                TanukiGlyph.Stop -> drawRoundRect(tint, Offset(5f, 5f), Size(14f, 14f), CornerRadius(3f), style = stroke)
                TanukiGlyph.Chevron -> { path.moveTo(9f, 6f); path.lineTo(15f, 12f); path.lineTo(9f, 18f) }
                TanukiGlyph.Download -> {
                    path.moveTo(12f, 3f); path.lineTo(12f, 15f); path.moveTo(7f, 10f); path.lineTo(12f, 15f); path.lineTo(17f, 10f)
                    path.moveTo(4f, 16f); path.lineTo(4f, 21f); path.lineTo(20f, 21f); path.lineTo(20f, 16f)
                }
                TanukiGlyph.Expand -> {
                    path.moveTo(9f, 3f); path.lineTo(3f, 3f); path.lineTo(3f, 9f)
                    path.moveTo(15f, 3f); path.lineTo(21f, 3f); path.lineTo(21f, 9f)
                    path.moveTo(3f, 15f); path.lineTo(3f, 21f); path.lineTo(9f, 21f)
                    path.moveTo(21f, 15f); path.lineTo(21f, 21f); path.lineTo(15f, 21f)
                }
                TanukiGlyph.Contract -> {
                    path.moveTo(10f, 10f); path.lineTo(4f, 4f); path.moveTo(4f, 9f); path.lineTo(4f, 4f); path.lineTo(9f, 4f)
                    path.moveTo(14f, 10f); path.lineTo(20f, 4f); path.moveTo(20f, 9f); path.lineTo(20f, 4f); path.lineTo(15f, 4f)
                    path.moveTo(10f, 14f); path.lineTo(4f, 20f); path.moveTo(9f, 20f); path.lineTo(4f, 20f); path.lineTo(4f, 15f)
                    path.moveTo(14f, 14f); path.lineTo(20f, 20f); path.moveTo(15f, 20f); path.lineTo(20f, 20f); path.lineTo(20f, 15f)
                }
            }
            drawPath(path, tint, style = stroke)
        }
    }
}

/**
 * 温控/省电设置。开关只影响非关键后台工作；阈值改变后立即重新采样，
 * 正在运行的 DSH 任务和用户主动下载不会被取消。
 */
@Composable
private fun DeviceConditionSettings(state: DeviceConditionState, c: TanukiUiCallbacks) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleRow("温控与省电策略", state.enabled, c.setDeviceConditionEnabled)
        Caption("温度、电量或内存紧张时减少后台活动；助手当前任务继续运行。")
        Text("低电阈值", style = MaterialTheme.typography.bodyMedium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(10, 15, 20, 25, 30).forEach { percent ->
                FilterChip(
                    selected = state.lowBatteryEnterPercent == percent,
                    onClick = { c.setLowBatteryThreshold(percent) },
                    label = { Text("${percent}%") },
                )
            }
        }
        Caption("未充电且电量低于此值时，延后后台更新并降低下载速度；电量回升后恢复。")
        Text("当前状态：${state.condition.label} · ${state.reasonText()}", style = MaterialTheme.typography.bodyMedium)
        Caption("当前后台活动和预览质量会随设备状态调整。")
        if (state.deferredChecks.isNotEmpty()) {
            Caption("后台更新检查已延后，设备状态恢复后会自动继续。")
        }
    }
}
