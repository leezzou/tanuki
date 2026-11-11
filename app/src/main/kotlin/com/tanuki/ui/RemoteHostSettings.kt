package com.tanuki.ui

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tanuki.lan.LanDiscoveredHost
import com.tanuki.lan.LanDiscoveryController
import com.tanuki.lan.LanDiscoveryCopy
import com.tanuki.lan.LanDiscoveryPermission
import com.tanuki.lan.LanDiscoveryPreferences
import com.tanuki.lan.LanDiscoveryState
import com.tanuki.runtime.PairingClipboardStatus
import com.tanuki.runtime.RemoteAuthType
import com.tanuki.runtime.RemoteConnectionMethod
import com.tanuki.runtime.RemoteConnectionStage
import com.tanuki.runtime.RemoteFailureInfo
import com.tanuki.runtime.RemoteHostConfig
import com.tanuki.runtime.RemoteHostProfile
import com.tanuki.runtime.RemoteHostStatus
import com.tanuki.runtime.RemoteHostWizard
import com.tanuki.runtime.RemoteWizardAttemptGuard
import com.tanuki.runtime.RemotePhase
import com.tanuki.runtime.RemoteStageStatus
import com.tanuki.runtime.RemoteWizardField
import com.tanuki.runtime.RemoteWizardStep
import com.tanuki.runtime.RemoteWizardValidation
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 远程页：独立滚动容器，支持下拉刷新。
 *
 * 页面可见时启动系统 NsdManager 发现，之后每 5 秒重扫；离开页面立即停止并释放多播锁。
 * Android 13+ 首次进入只询问一次附近设备权限；拒绝后降级为扫码/粘贴，不再自动弹窗。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoteHostSettingsPage(model: TanukiUiModel, c: TanukiUiCallbacks, bottomInset: Dp = 0.dp) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) { LanDiscoveryController(context) }
    val discovery by controller.state.collectAsState()
    val preferences = remember(context) { LanDiscoveryPreferences(context) }
    var permissionGranted by remember { mutableStateOf(LanDiscoveryController.hasPermission(context)) }
    var permissionDenied by remember { mutableStateOf(false) }
    var manualRefresh by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
        preferences.markPermissionAsked()
        if (granted && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.start()
    }

    LaunchedEffect(Unit) {
        when (LanDiscoveryPermission.decide(
            Build.VERSION.SDK_INT,
            LanDiscoveryController.hasPermission(context),
            preferences.permissionAsked()
        )) {
            LanDiscoveryPermission.Decision.REQUEST -> {
                preferences.markPermissionAsked()
                permissionLauncher.launch(LanDiscoveryPermission.PERMISSION)
            }
            LanDiscoveryPermission.Decision.ALLOWED -> {
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.start()
            }
            LanDiscoveryPermission.Decision.DEGRADED -> permissionDenied = true
        }
    }

    // 生命周期驱动发现：只在页面可见时扫描，避免后台耗电和持续组播。
    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val granted = LanDiscoveryController.hasPermission(context)
                    permissionGranted = granted
                    permissionDenied = !granted && preferences.permissionAsked()
                    if (granted) controller.start() else controller.stop()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> controller.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && permissionGranted) {
            controller.start()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.stop()
        }
    }

    // 自动重扫每 5 秒一次，不能每次都转下拉指示器；只有用户主动下拉才显示。
    LaunchedEffect(manualRefresh, discovery.scanning) {
        if (manualRefresh && !discovery.scanning) {
            delay(500)
            manualRefresh = false
        }
    }

    PullToRefreshBox(
        isRefreshing = manualRefresh,
        onRefresh = {
            if (permissionGranted) {
                manualRefresh = true
                controller.refresh()
            } else {
                // 已拒绝过权限时不再自动弹系统框，只保留扫码/粘贴降级路径。
                permissionDenied = true
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RemoteHostSettingsContent(
                model = model,
                c = c,
                discovery = discovery,
                permissionDenied = permissionDenied || discovery.permissionDenied,
                onRefresh = { if (permissionGranted) controller.refresh() }
            )
        }
    }
}

/** 远程页主体；顺序固定为「当前主机状态 → 局域网发现的电脑 → 我的连接 → 其他方式」。 */
@Composable
private fun RemoteHostSettingsContent(
    model: TanukiUiModel,
    c: TanukiUiCallbacks,
    discovery: LanDiscoveryState,
    permissionDenied: Boolean,
    onRefresh: () -> Unit
) {
    var wizard by remember { mutableStateOf<WizardSession?>(null) }
    var confirmForget by remember { mutableStateOf(false) }
    var pendingConnect by remember { mutableStateOf<RemoteHostProfile?>(null) }
    var pendingDelete by remember { mutableStateOf<RemoteHostProfile?>(null) }
    var pendingLanHost by remember { mutableStateOf<LanDiscoveredHost?>(null) }
    var scanNotice by remember { mutableStateOf("") }
    val currentModel by rememberUpdatedState(model)
    val connectionsEnabled = RemoteWizardAttemptGuard.canOpen(model.remoteBusy, model.remotePendingFingerprint)
    val saved = model.remoteHost
    val savedProfile = saved?.let { config ->
        model.remoteProfiles.firstOrNull {
            it.config.hostIdentity() == config.hostIdentity() &&
                it.config.user == config.user &&
                it.config.remoteDshPort == config.remoteDshPort
        }
    }
    val failedConfig = model.remoteAttemptConfig
    val failedProfile = model.remoteAttemptProfileId?.let { id ->
        model.remoteProfiles.firstOrNull { it.id == id }
    } ?: failedConfig?.let { config ->
        model.remoteProfiles.firstOrNull {
            it.config.hostIdentity() == config.hostIdentity() &&
                it.config.user == config.user &&
                it.config.remoteDshPort == config.remoteDshPort
        }
    }

    fun canOpenWizard(): Boolean {
        if (RemoteWizardAttemptGuard.canOpen(currentModel.remoteBusy, currentModel.remotePendingFingerprint)) return true
        scanNotice = "连接正在进行，请完成当前连接或指纹确认后再添加。"
        return false
    }

    fun openExisting(config: RemoteHostConfig, name: String, id: String?) {
        if (canOpenWizard()) wizard = WizardSession(config, name, id)
    }

    fun openManualWizard() {
        if (canOpenWizard()) wizard = WizardSession()
    }

    fun openPairingWizard(link: String? = null) {
        if (!canOpenWizard()) return
        wizard = WizardSession().also { session ->
            session.machine.selectMethod(RemoteConnectionMethod.PAIRING_LINK)
            session.machine.next()
            link?.let(session.machine::updateRelayLink)
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        // 相机返回时连接状态可能已变化，必须再次检查，不能只依赖打开相机前的按钮状态。
        if (!canOpenWizard()) return@rememberLauncherForActivityResult
        val link = result.contents
        if (link == null) {
            scanNotice = "扫码已取消，可继续粘贴配对链接。"
        } else if (RemoteWizardValidation.relayLink(link) != null) {
            scanNotice = "这个二维码不是有效的电脑配对链接，请扫描电脑端终端显示的二维码。"
        } else {
            scanNotice = ""
            openPairingWizard(link)
        }
    }
    fun scanQr() {
        if (!canOpenWizard()) return
        runCatching {
            scanLauncher.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("扫描电脑端的配对二维码").setBeepEnabled(false)
                .setBarcodeImageEnabled(false).setOrientationLocked(false)
                .addExtra("MISSING_CAMERA_PERMISSION_DIALOG_MESSAGE", "未开启相机权限，可以返回后粘贴配对链接。"))
        }.onFailure { scanNotice = "暂时无法打开相机，可以粘贴配对链接。" }
    }

    fun openDiscoveredHost(host: LanDiscoveredHost) {
        if (!canOpenWizard()) return
        val session = WizardSession()
        // 发现地址无法直接用于 SSH（例如带 zone 的 IPv6）时退回手动填写，避免死路。
        wizard = if (session.machine.startFromLanDiscovery(host.address, host.sshPort, host.dshPort, host.name)) {
            session
        } else {
            WizardSession()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StatusCard(
            model = model,
            onEdit = { saved?.let { openExisting(it, savedProfile?.name.orEmpty(), savedProfile?.id) } },
            onForget = { confirmForget = true },
            onConnectSaved = { saved?.let { c.connectRemoteHost(it) } },
            onEditFailed = {
                failedConfig?.let {
                    openExisting(it, failedProfile?.name.orEmpty(), failedProfile?.id)
                }
            },
            onRetry = { (failedConfig ?: saved)?.let { c.connectRemoteHost(it) } }
        )

        if (model.remotePendingFingerprint != null && wizard == null) {
            HostKeyConfirmationCard(model.remotePendingFingerprint, model.remotePendingHost, c)
        }

        LanDiscoveryCard(
            state = discovery,
            permissionDenied = permissionDenied,
            connectionEnabled = connectionsEnabled,
            onRefresh = onRefresh,
            onSelect = { if (canOpenWizard()) pendingLanHost = it },
            onUseQr = ::scanQr,
            onPaste = { openPairingWizard() }
        )

        SavedProfilesCard(
            model = model,
            onConnect = { pendingConnect = it },
            onEdit = { profile -> openExisting(profile.config, profile.name, profile.id) },
            onDelete = { pendingDelete = it }
        )

        OtherWaysCard(
            enabled = connectionsEnabled,
            onUseQr = ::scanQr,
            onPaste = { openPairingWizard() },
            onUseManual = ::openManualWizard
        )

        if (scanNotice.isNotBlank()) Text(scanNotice, style = MaterialTheme.typography.bodySmall)
        SafetyNote()
    }

    pendingLanHost?.let { host ->
        AlertDialog(
            onDismissRequest = { pendingLanHost = null },
            title = { Text("连接「${host.name}」？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${host.address}:${host.sshPort}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        LanDiscoveryCopy.itemSubtitle(host),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "电脑标识：${host.hostId.take(8)}…（${host.fingerprint}）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "发现信息只用来找到电脑。继续后会进入现有向导；首次连接会显示 SSH 主机密钥指纹，需要你手动核对后再信任。",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val selected = host
                    pendingLanHost = null
                    openDiscoveredHost(selected)
                }, enabled = connectionsEnabled) { Text("继续配对") }
            },
            dismissButton = { TextButton(onClick = { pendingLanHost = null }) { Text("取消") } }
        )
    }

    wizard?.let { session ->
        RemoteWizardDialog(
            model = model,
            c = c,
            session = session,
            onDismiss = { wizard = null }
        )
    }

    pendingConnect?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingConnect = null },
            title = { Text("切换到远程连接？") },
            text = {
                Text(
                    "连接“${profile.name}”后会切换到远程 DSH 页面，当前本地 DSH 页面会断开；本地运行时继续在后台运行。" +
                        "远程连接失败会自动回退本地。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingConnect = null
                    if (canOpenWizard()) c.connectRemoteProfile(profile)
                }, enabled = connectionsEnabled) { Text("连接") }
            },
            dismissButton = { TextButton(onClick = { pendingConnect = null }) { Text("取消") } }
        )
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除连接“${profile.name}”？") },
            text = { Text("会删除本机加密保存的密码/私钥/配对密钥和该连接的配置；不会修改远程主机。当前已建立的远程会话不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    c.deleteRemoteProfile(profile.id)
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }
        )
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("清除当前活动配置？") },
            text = { Text("将删除当前活动远程配置和本机保存的主机密钥信任记录，并断开当前远程连接。命名连接库中的其他连接不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    c.forgetRemoteHost()
                }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("取消") } }
        )
    }
}

/** 局域网发现区块：列表、权限降级、空态命令与扫码入口。 */
@Composable
private fun LanDiscoveryCard(
    state: LanDiscoveryState,
    permissionDenied: Boolean,
    connectionEnabled: Boolean,
    onRefresh: () -> Unit,
    onSelect: (LanDiscoveredHost) -> Unit,
    onUseQr: () -> Unit,
    onPaste: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(LanDiscoveryCopy.SECTION_TITLE, style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            permissionDenied -> "未开启权限"
                            state.scanning -> "正在查找同一 Wi-Fi 下的电脑…"
                            state.hosts.isEmpty() -> "未发现电脑"
                            else -> "已发现 ${state.hosts.size} 台 · 每 5 秒自动刷新"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onRefresh, enabled = !permissionDenied) { Text("刷新") }
            }

            when {
                permissionDenied -> PermissionDegraded(connectionEnabled, onUseQr, onPaste)
                state.hosts.isEmpty() -> LanEmptyState(enabled = connectionEnabled, onUseQr = onUseQr)
                else -> state.hosts.forEach { host ->
                    LanHostRow(host = host, enabled = connectionEnabled, onClick = { onSelect(host) })
                }
            }

            if (state.error.isNotBlank()) {
                Text(state.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PermissionDegraded(enabled: Boolean, onUseQr: () -> Unit, onPaste: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(LanDiscoveryCopy.PERMISSION_DENIED, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onUseQr, enabled = enabled) { Text("扫码连接") }
            TextButton(onClick = onPaste, enabled = enabled) { Text("粘贴配对链接") }
        }
    }
}

@Composable
private fun LanEmptyState(enabled: Boolean, onUseQr: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(LanDiscoveryCopy.EMPTY_TITLE, style = MaterialTheme.typography.titleSmall)
        Text(
            LanDiscoveryCopy.EMPTY_DETAIL,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(Modifier.weight(1f)) {
                    Text(
                        LanDiscoveryCopy.HOST_COMMAND,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(LanDiscoveryCopy.HOST_COMMAND))
                    copied = true
                }) { Text(if (copied) LanDiscoveryCopy.COPIED else LanDiscoveryCopy.COPY_COMMAND) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onUseQr, enabled = enabled) { Text(LanDiscoveryCopy.USE_QR) }
        }
    }
}

@Composable
private fun LanHostRow(host: LanDiscoveredHost, enabled: Boolean, onClick: () -> Unit) {
    val clickable = enabled && host.online && host.connectable
    Surface(
        onClick = onClick,
        enabled = clickable,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(LanDiscoveryCopy.itemTitle(host), style = MaterialTheme.typography.titleSmall)
                Text(
                    LanDiscoveryCopy.itemSubtitle(host),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (host.connectable) "${host.address}:${host.sshPort}" else LanDiscoveryCopy.ADDRESS_PENDING,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TanukiIcon(TanukiGlyph.Chevron, modifier = Modifier.size(18.dp))
        }
    }
}

/** 扫码/粘贴与手动填写；与局域网列表并列，作为网络不支持发现时的稳定入口。 */
@Composable
private fun OtherWaysCard(enabled: Boolean, onUseQr: () -> Unit, onPaste: () -> Unit, onUseManual: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(LanDiscoveryCopy.OTHER_TITLE, style = MaterialTheme.typography.titleSmall)
            Text(
                LanDiscoveryCopy.OTHER_DETAIL,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onUseQr, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text("扫码连接")
            }
            OutlinedButton(onClick = onPaste, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("粘贴配对链接") }
            TextButton(onClick = onUseManual, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(LanDiscoveryCopy.USE_MANUAL)
            }
            Text(
                "向导会自动读取剪贴板里的配对链接；读不到时仍可手动粘贴。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 一句话安全说明，技术细节默认收起。 */
@Composable
private fun SafetyNote() {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(LanDiscoveryCopy.SAFETY, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "收起详情" else "了解详情")
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "首次连接会显示 SSH 主机密钥的 SHA256 指纹，需要你手动核对；以后主机密钥变化时会直接拒绝连接。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "局域网发现只用 mDNS 广播电脑名、端口、电脑标识和协议版本，不包含配对 token；同一网络里的其他人也能看到这些信息。发现只用于找到电脑，不能替代配对和指纹确认。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "密码、私钥和口令使用 Android Keystore 加密保存，不写日志、不导出。中继只转发 SSH 密文，中继证书按配对链接中的指纹或系统信任证书校验，没有关闭校验的开关。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    model: TanukiUiModel,
    onEdit: () -> Unit,
    onForget: () -> Unit,
    onConnectSaved: () -> Unit,
    onEditFailed: () -> Unit,
    onRetry: () -> Unit
) {
    val status = model.remote
    val saved = model.remoteHost
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("当前主机状态", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    phaseLabel(status.phase),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (status.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                modeImpact(status.phase),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (status.connected) {
                RemoteFact("远程主机", status.host)
                RemoteFact("SSH 隧道", "127.0.0.1:${status.localPort ?: "-"}")
                RemoteFact("主机密钥", status.fingerprint)
            }
            if (saved != null) {
                if (saved.relay != null) {
                    RemoteFact("当前配置", "TLS 中继 · ${saved.relay.endpointLabel()}")
                    RemoteFact("远程 DSH", "127.0.0.1:${saved.remoteDshPort}")
                } else {
                    RemoteFact("当前配置", "${saved.user}@${saved.host}:${saved.sshPort}")
                    RemoteFact("远程 DSH", "127.0.0.1:${saved.remoteDshPort}")
                }
            }

            if (model.remoteBusy && model.remoteStage != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("正在${model.remoteStage.label}…", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (model.remoteMessage.isNotBlank()) {
                Text(model.remoteMessage, style = MaterialTheme.typography.bodySmall)
            }
            if (model.remoteFailure != null) {
                FailurePanel(
                    info = model.remoteFailure,
                    onRetry = if (model.remoteFailure.retryable) onRetry else null,
                    onEdit = onEditFailed
                )
            } else if (model.remoteError.isNotBlank()) {
                SelectionContainer {
                    Text(
                        model.remoteError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (saved != null) {
                OutlinedButton(
                    onClick = onConnectSaved,
                    enabled = !model.remoteBusy && !status.connected
                ) { Text("连接当前配置") }
            }
            if (status.connected || status.phase == RemotePhase.CONNECTING) {
                TextButton(onClick = { onEdit() }) { Text("查看/编辑当前配置") }
            }
            if (saved != null) {
                TextButton(onClick = onForget, enabled = !model.remoteBusy) { Text("清除当前活动配置与主机密钥") }
            }
        }
    }
}

@Composable
private fun SavedProfilesCard(
    model: TanukiUiModel,
    onConnect: (RemoteHostProfile) -> Unit,
    onEdit: (RemoteHostProfile) -> Unit,
    onDelete: (RemoteHostProfile) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("我的连接", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${model.remoteProfiles.size} 个", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (model.remoteProfiles.isEmpty()) {
                Text(
                    "还没有保存的连接。完成向导的“测试连接”后，可以命名保存，下次一键连接。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                model.remoteProfiles.forEach { profile ->
                    val endpoint = profile.config.relay?.let {
                        "TLS 中继 · ${it.endpointLabel()} · ${profile.config.user}"
                    } ?: "${profile.config.user}@${profile.config.host}:${profile.config.sshPort}"
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(profile.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                if (profile.id == model.remoteActiveProfileId) {
                                    AssistChip(onClick = {}, label = { Text("上次使用") })
                                }
                            }
                            Text(endpoint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "远程 DSH 127.0.0.1:${profile.config.remoteDshPort}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { onConnect(profile) }, enabled = !model.remoteBusy) { Text("连接") }
                                TextButton(onClick = { onEdit(profile) }, enabled = !model.remoteBusy) { Text("编辑") }
                                TextButton(onClick = { onDelete(profile) }, enabled = !model.remoteBusy) { Text("删除") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostKeyConfirmationCard(fingerprint: String, host: String, c: TanukiUiCallbacks) {
    var acknowledged by remember(fingerprint) { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("首次连接：核对远程主机密钥", style = MaterialTheme.typography.titleSmall)
            if (host.isNotBlank()) Text("远程主机：$host", style = MaterialTheme.typography.bodySmall)
            SelectionContainer {
                Text(fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "请在远程主机执行 ssh-keygen -lf /etc/ssh/ssh_host_rsa_key.pub（ECDSA 用对应文件）并逐字核对。指纹不一致时不要信任。",
                style = MaterialTheme.typography.bodySmall
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                Text("我已通过独立渠道核对上述 SHA256 指纹", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = c.confirmRemoteHostKey, enabled = acknowledged) { Text("核对无误，信任并继续") }
                TextButton(onClick = c.cancelRemoteHostKey) { Text("取消") }
            }
        }
    }
}

/** 一次向导会话；remember 持有状态机，关闭后丢弃。 */
private class WizardSession(
    initialConfig: RemoteHostConfig? = null,
    initialName: String = "",
    profileId: String? = null
) {
    val machine = RemoteHostWizard(initialConfig, initialName, profileId)
    val attempt = RemoteWizardAttemptGuard()
}

@Composable
private fun RemoteWizardDialog(
    model: TanukiUiModel,
    c: TanukiUiCallbacks,
    session: WizardSession,
    onDismiss: () -> Unit
) {
    val state by session.machine.state.collectAsState()
    val currentModel by rememberUpdatedState(model)
    val canStartAttempt = RemoteWizardAttemptGuard.canOpen(model.remoteBusy, model.remotePendingFingerprint)
    val ownsAttempt = session.attempt.accepts(model.remoteAttemptConfig)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keyLoading by remember { mutableStateOf(false) }
    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val request = session.machine.beginPrivateKeyRead()
        keyLoading = true
        scope.launch {
            // 云盘/慢速 DocumentsProvider 的 query/open/read 都不能在主线程做；
            // 64 KiB 只限制读取量，不限制 provider 的等待时间。
            val result = withContext(Dispatchers.IO) { runCatching { readPrivateKey(context, uri) } }
            keyLoading = false
            result.onSuccess { picked ->
                // 版本/步骤/认证方式任一变化都丢弃迟到结果，避免覆盖已开始测试的草稿。
                if (session.machine.applyPrivateKeyRead(request, picked.name, picked.text)) {
                    session.machine.setNotice("已选择私钥文件：${picked.name}（内容不会显示）")
                } else {
                    session.machine.setNotice("私钥读取完成，但草稿或步骤已变化，未应用；请重新选择")
                }
            }.onFailure { error ->
                session.machine.setNotice(error.message ?: "无法读取私钥文件，请重新选择")
            }
        }
    }

    // 只有本向导实际提交过的配置才可接收控制器事件；其他已保存连接的迟到结果全部忽略。
    LaunchedEffect(session, model.remoteAttemptConfig, model.remotePendingFingerprint) {
        if (session.attempt.accepts(model.remoteAttemptConfig)) model.remotePendingFingerprint?.let(session.machine::onHostKeyRequired)
    }
    LaunchedEffect(session, model.remoteAttemptConfig, model.remoteStage) {
        if (session.attempt.accepts(model.remoteAttemptConfig)) model.remoteStage?.let(session.machine::onStage)
    }
    LaunchedEffect(session, model.remoteAttemptConfig, model.remoteAttemptSucceeded) {
        if (session.attempt.accepts(model.remoteAttemptConfig) && model.remoteAttemptSucceeded) session.machine.onTestSucceeded(model.remoteLocalPort)
    }
    LaunchedEffect(session, model.remoteAttemptConfig, model.remoteFailure) {
        if (session.attempt.accepts(model.remoteAttemptConfig)) model.remoteFailure?.let(session.machine::onFailure)
    }

    // 每次进入配对链接步骤读一次剪贴板；读不到就保留粘贴框，不阻塞、不报错。
    // 结果带回 visit，状态机会丢弃离开/返回后迟到的读取。
    LaunchedEffect(state.step, state.method, state.clipboardVisit) {
        if (state.step == RemoteWizardStep.HOST_ACCOUNT &&
            state.method == RemoteConnectionMethod.PAIRING_LINK
        ) {
            session.machine.applyClipboardPairing(readPairingClipboard(context), state.clipboardVisit)
        }
    }

    fun dismiss() {
        if (state.busy) return
        if (session.attempt.accepts(currentModel.remoteAttemptConfig) && currentModel.remotePendingFingerprint != null) c.cancelRemoteHostKey()
        onDismiss()
    }

    fun canBeginTest(): Boolean {
        if (RemoteWizardAttemptGuard.canOpen(currentModel.remoteBusy, currentModel.remotePendingFingerprint)) return true
        session.machine.setNotice("请先完成当前连接或指纹确认，再测试这条连接。")
        return false
    }

    fun testNow() {
        if (keyLoading) {
            session.machine.setNotice("正在读取私钥文件，请稍候再开始测试")
            return
        }
        val config = session.machine.buildConfig()
        if (config == null) {
            session.machine.onFailure(
                com.tanuki.runtime.RemoteConnectionErrors.validation("连接配置不完整，请返回上一步检查主机、账号和认证信息")
            )
        } else if (!session.attempt.submit(config, currentModel.remoteBusy, currentModel.remotePendingFingerprint, c.testRemoteHost)) {
            // 防御迟到点击：控制器拒绝并发尝试时，不能让向导停在等待别人的结果的状态。
            session.machine.onFailure(com.tanuki.runtime.RemoteConnectionErrors.validation("当前另有连接正在进行，请完成后重新测试"))
        }
    }

    fun retest() {
        if (!canBeginTest()) return
        session.machine.beginTest()
        testNow()
    }

    fun startNext() {
        if (keyLoading) {
            session.machine.setNotice("正在读取私钥文件，请稍候再继续")
            return
        }
        val before = session.machine.state.value.step
        if (before == RemoteWizardStep.AUTHENTICATION && !canBeginTest()) return
        if (!session.machine.next()) return
        val after = session.machine.state.value
        if (before != RemoteWizardStep.TEST && after.step == RemoteWizardStep.TEST && after.busy) {
            testNow()
        }
    }

    fun goBack() {
        session.machine.cancelPrivateKeyRead()
        keyLoading = false
        if (session.machine.state.value.step == RemoteWizardStep.FINGERPRINT && session.attempt.accepts(currentModel.remoteAttemptConfig)) c.cancelRemoteHostKey()
        session.machine.back()
    }

    fun save(connect: Boolean) {
        if (!canBeginTest()) return
        if (!session.attempt.accepts(currentModel.remoteAttemptConfig) || !session.machine.state.value.tested) {
            session.machine.setNotice("请先重新测试当前连接，再保存。")
            return
        }
        if (!session.machine.requireValidName()) return
        val config = session.machine.buildConfig() ?: return
        c.saveRemoteProfile(state.name.trim(), config, state.editingProfileId, connect)
        onDismiss()
    }

    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.isEditing) "编辑远程连接" else "远程连接向导",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "第 ${state.stepNumber}/${state.stepTotal} 步 · ${state.step.titleFor(state.method)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { dismiss() }, enabled = !state.busy) { Text("关闭") }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.steps.forEachIndexed { index, step ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .background(
                                    if (index < state.steps.indexOf(state.step)) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (state.step) {
                        RemoteWizardStep.METHOD -> StepMethod(session, state.method)
                        RemoteWizardStep.HOST_ACCOUNT -> StepHostAccount(session, state)
                        RemoteWizardStep.PAIRING_CONFIRM -> StepPairingConfirm(state)
                        RemoteWizardStep.AUTHENTICATION -> StepAuthentication(
                            session = session,
                            state = state,
                            keyLoading = keyLoading,
                            onPickKey = { keyPicker.launch(arrayOf("*/*")) },
                            onSelectAuth = { auth ->
                                session.machine.cancelPrivateKeyRead()
                                keyLoading = false
                                session.machine.setAuth(auth)
                            }
                        )
                        RemoteWizardStep.FINGERPRINT -> StepFingerprint(session, state)
                        RemoteWizardStep.TEST -> StepTest(session, state)
                        RemoteWizardStep.DONE -> StepDone(
                            model = model,
                            session = session,
                            state = state,
                            onRetryConnect = { if (canBeginTest()) session.machine.buildConfig()?.let(c.connectRemoteHost) },
                            onRetest = ::retest
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (state.step) {
                        RemoteWizardStep.METHOD -> Button(onClick = ::startNext, modifier = Modifier.fillMaxWidth()) {
                            Text("下一步：填写主机与账号")
                        }
                        RemoteWizardStep.HOST_ACCOUNT -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = ::goBack) { Text("上一步") }
                            Button(onClick = ::startNext, modifier = Modifier.weight(1f)) {
                                Text(
                                    if (state.method == RemoteConnectionMethod.PAIRING_LINK) {
                                        "下一步：确认这台电脑"
                                    } else {
                                        "下一步：选择认证"
                                    }
                                )
                            }
                        }
                        RemoteWizardStep.PAIRING_CONFIRM -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = ::goBack) { Text("返回修改") }
                            Button(
                                onClick = { session.machine.confirmPairing() },
                                enabled = state.canConfirmPairing,
                                modifier = Modifier.weight(1f)
                            ) { Text("这是你的电脑") }
                        }
                        RemoteWizardStep.AUTHENTICATION -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = ::goBack) { Text("上一步") }
                            Button(
                                onClick = ::startNext,
                                enabled = !keyLoading && canStartAttempt,
                                modifier = Modifier.weight(1f)
                            ) { Text(if (keyLoading) "正在读取私钥…" else "开始测试连接") }
                        }
                        RemoteWizardStep.FINGERPRINT -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = ::goBack) { Text("返回修改") }
                            Button(
                                onClick = {
                                    if (ownsAttempt && session.machine.confirmFingerprint()) c.confirmRemoteHostKey()
                                },
                                enabled = ownsAttempt && state.canConfirmFingerprint,
                                modifier = Modifier.weight(1f)
                            ) { Text("核对无误，继续测试") }
                        }
                        RemoteWizardStep.TEST -> {
                            val failure = state.failure
                            if (failure != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = ::goBack) { Text("返回修改") }
                                    if (failure.retryable) {
                                        Button(
                                            onClick = { if (canBeginTest() && session.machine.retry()) testNow() },
                                            enabled = canStartAttempt,
                                            modifier = Modifier.weight(1f)
                                        ) { Text("重试") }
                                    }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = ::goBack, enabled = !state.busy) { Text("返回修改") }
                                    Button(
                                        onClick = ::retest,
                                        enabled = !state.busy && canStartAttempt,
                                        modifier = Modifier.weight(1f)
                                    ) { Text(if (state.busy) "正在测试…" else "重新测试") }
                                }
                            }
                        }
                        RemoteWizardStep.DONE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = ::retest, enabled = !state.busy && canStartAttempt) { Text("重新测试") }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { save(connect = false) }, enabled = canStartAttempt && ownsAttempt, modifier = Modifier.weight(1f)) {
                                    Text(if (state.isEditing) "仅保存修改" else "仅保存")
                                }
                                Button(onClick = { save(connect = true) }, enabled = canStartAttempt && ownsAttempt, modifier = Modifier.weight(1f)) {
                                    Text("保存并连接")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepMethod(session: WizardSession, selected: RemoteConnectionMethod?) {
    Text(
        "先选择你现在的网络情况。三种方式最终都走同一套 SSH 主机密钥校验和加密存储。",
        style = MaterialTheme.typography.bodyMedium
    )
    RemoteConnectionMethod.entries.forEach { method ->
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { session.machine.selectMethod(method) },
            shape = MaterialTheme.shapes.large,
            color = if (selected == method) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                RadioButton(selected = selected == method, onClick = { session.machine.selectMethod(method) })
                Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(method.title, style = MaterialTheme.typography.titleSmall)
                    Text(method.summary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        method.example,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            "连接远程会切换到远程 DSH 页面，当前本地 DSH 页面会断开；本地运行时继续在后台运行。远程连接失败会自动回退本地。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun StepHostAccount(session: WizardSession, state: com.tanuki.runtime.RemoteWizardState) {
    val pairing = state.method == RemoteConnectionMethod.PAIRING_LINK
    val preview = state.pairingPreview
    Text(
        if (pairing) {
            "手机已尝试从剪贴板读取配对链接；识别成功会自动填入并算好 6 位码，你只需确认。链接包含一次性密钥，不要截图或转发。"
        } else {
            "填写远程主机的地址和登录账号。端口不确定时先保留默认值。"
        },
        style = MaterialTheme.typography.bodyMedium
    )
    if (pairing) {
        OutlinedTextField(
            value = state.draft.relayUri,
            onValueChange = session.machine::updateRelayLink,
            label = { Text("配对链接") },
            placeholder = { Text("tanuki-relay://v1?relay=…&room=…&token=…&host=…") },
            minLines = 2,
            maxLines = 3,
            isError = state.fieldErrors[RemoteWizardField.RELAY_LINK] != null,
            supportingText = state.fieldErrors[RemoteWizardField.RELAY_LINK]?.let { message -> { Text(message) } },
            modifier = Modifier.fillMaxWidth()
        )
        if (state.clipboardMessage.isNotBlank()) {
            Text(
                state.clipboardMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.clipboardStatus == PairingClipboardStatus.INVALID_LINK) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
        if (state.pendingClipboardLink.isNotBlank()) {
            TextButton(onClick = { session.machine.usePendingClipboardLink() }) {
                Text("改用剪贴板里的配对链接")
            }
        }

        if (preview != null) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("电脑上应显示", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer {
                        Text(
                            preview.code,
                            style = MaterialTheme.typography.headlineMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        "手机已按配对链接自动算出这个 6 位码，不需要手输。请和电脑屏幕上显示的值对照。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "如果电脑显示的不是这个数，说明链接不是同一次配对：不要继续，返回电脑重新复制完整链接。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            OutlinedTextField(
                value = state.draft.pairingCode,
                onValueChange = { value ->
                    session.machine.updateDraft { it.copy(pairingCode = value.filter(Char::isDigit).take(6)) }
                },
                label = { Text("6 位配对码（可选校对）") },
                placeholder = { Text(preview.code) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = state.fieldErrors[RemoteWizardField.PAIRING_CODE] != null,
                supportingText = {
                    Text(
                        state.fieldErrors[RemoteWizardField.PAIRING_CODE]
                            ?: "已自动填入上面的码；只有想和电脑核对时才需要改这里。"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (state.draft.pairingCode.isBlank() || state.draft.pairingCode != preview.code) {
                TextButton(onClick = { session.machine.fillAutoPairingCode() }) {
                    Text("填入自动计算的 ${preview.code}")
                }
            }
        } else {
            // 链接无效或还没拿到时保留手动回退；不会要求用户先手抄电脑上的码才能继续。
            OutlinedTextField(
                value = state.draft.pairingCode,
                onValueChange = { value ->
                    session.machine.updateDraft { it.copy(pairingCode = value.filter(Char::isDigit).take(6)) }
                },
                label = { Text("6 位配对码（手动回退）") },
                placeholder = { Text("链接解析成功后会自动填入") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = state.fieldErrors[RemoteWizardField.PAIRING_CODE] != null,
                supportingText = state.fieldErrors[RemoteWizardField.PAIRING_CODE]?.let { message -> { Text(message) } },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            "配对码只是链接的人工核对值，不能单独恢复中继地址/房间/token，因此仍需完整的配对链接。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "配对链接由远程电脑生成；手机只经 TLS 中继转发 SSH 密文，仍会独立校验 SSH 主机密钥。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    OutlinedTextField(
        value = state.draft.host,
        onValueChange = { value -> session.machine.updateDraft { it.copy(host = value) } },
        label = { Text(if (pairing) "展示名（可选）" else "主机地址") },
        placeholder = { Text(RemoteWizardValidation.hostExample(state.method)) },
        singleLine = true,
        isError = state.fieldErrors[RemoteWizardField.HOST] != null,
        supportingText = state.fieldErrors[RemoteWizardField.HOST]?.let { message -> { Text(message) } },
        modifier = Modifier.fillMaxWidth()
    )
    if (!pairing) {
        Text(
            "示例：192.168.1.20（局域网）、dev.example.com（域名）、[2001:db8::10]（IPv6）；不要带 http:// 或端口。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.draft.sshPort,
            onValueChange = { value -> session.machine.updateDraft { it.copy(sshPort = value) } },
            label = { Text("SSH 端口") },
            placeholder = { Text("22") },
            singleLine = true,
            isError = state.fieldErrors[RemoteWizardField.SSH_PORT] != null,
            supportingText = state.fieldErrors[RemoteWizardField.SSH_PORT]?.let { message -> { Text(message) } },
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = state.draft.user,
            onValueChange = { value -> session.machine.updateDraft { it.copy(user = value) } },
            label = { Text("用户名") },
            placeholder = { Text("tanuki") },
            singleLine = true,
            isError = state.fieldErrors[RemoteWizardField.USER] != null,
            supportingText = state.fieldErrors[RemoteWizardField.USER]?.let { message -> { Text(message) } },
            modifier = Modifier.weight(1.4f)
        )
    }
    Text(
        "用户名是远程电脑上的登录账号，可用 whoami 查看；不是 DSH 的显示名。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.draft.remoteDshPort,
            onValueChange = { value -> session.machine.updateDraft { it.copy(remoteDshPort = value) } },
            label = { Text("远程 DSH 端口") },
            placeholder = { Text("3080") },
            singleLine = true,
            isError = state.fieldErrors[RemoteWizardField.REMOTE_DSH_PORT] != null,
            supportingText = state.fieldErrors[RemoteWizardField.REMOTE_DSH_PORT]?.let { message -> { Text(message) } },
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = state.draft.token,
            onValueChange = { value -> session.machine.updateDraft { it.copy(token = value) } },
            label = { Text("DSH token（可选）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = state.fieldErrors[RemoteWizardField.TOKEN] != null,
            supportingText = state.fieldErrors[RemoteWizardField.TOKEN]?.let { message -> { Text(message) } },
            modifier = Modifier.weight(1.4f)
        )
    }
    Text(
        "远程 DSH 端口是 DSH 在远程电脑 127.0.0.1 上监听的端口，默认 3080；SSH 隧道只转发到这个回环端口。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun StepPairingConfirm(state: com.tanuki.runtime.RemoteWizardState) {
    val preview = state.pairingPreview
    if (preview == null) {
        Text(
            "配对链接已失效或无法解析，请返回上一步重新复制。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        return
    }
    var showFullPin by remember(preview.pin) { mutableStateOf(false) }
    Text(
        "链接解析成功。请确认下面这台电脑确实是你自己的，再继续登录。",
        style = MaterialTheme.typography.bodyMedium
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("电脑名", style = MaterialTheme.typography.labelMedium)
            Text(preview.computerName, style = MaterialTheme.typography.bodyMedium)
            Text(
                preview.computerNameSource,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("中继地址", style = MaterialTheme.typography.labelMedium)
            SelectionContainer {
                Text(preview.relayLabel, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            }

            Text("中继证书指纹", style = MaterialTheme.typography.labelMedium)
            if (preview.pinned) {
                SelectionContainer {
                    Text(
                        if (showFullPin) preview.pin else preview.pinSummary,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                TextButton(onClick = { showFullPin = !showFullPin }) {
                    Text(if (showFullPin) "收起完整指纹" else "展开完整指纹")
                }
            } else {
                Text("系统 CA（链接未固定证书）", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "手机会用系统 CA 和主机名校验中继；如果电脑使用的是自签名证书，请让电脑重新生成带证书指纹的链接。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("SSH 目标", style = MaterialTheme.typography.labelMedium)
            Text(preview.sshHint, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
            Text(
                "中继只转发 SSH 密文；手机仍会单独核对远程主机的 SSH 密钥指纹。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("远程 DSH", style = MaterialTheme.typography.labelMedium)
            Text(preview.dshHint, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)

            Text("6 位配对码", style = MaterialTheme.typography.labelMedium)
            SelectionContainer {
                Text(
                    preview.code,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                "电脑上应显示同一个 ${preview.code}；手机按链接自动算出，不需要手输。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
    Text(
        "如果电脑名、中继地址或证书指纹与电脑上显示的不一致，请返回修改，不要点“这是你的电脑”。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun StepAuthentication(
    session: WizardSession,
    state: com.tanuki.runtime.RemoteWizardState,
    keyLoading: Boolean,
    onPickKey: () -> Unit,
    onSelectAuth: (RemoteAuthType) -> Unit
) {
    Text(
        "选择 SSH 认证方式。密码和私钥只会传给 SSH，并加密保存在本机。",
        style = MaterialTheme.typography.bodyMedium
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.draft.auth == RemoteAuthType.PRIVATE_KEY,
            onClick = { onSelectAuth(RemoteAuthType.PRIVATE_KEY) },
            enabled = !keyLoading,
            label = { Text("私钥") }
        )
        FilterChip(
            selected = state.draft.auth == RemoteAuthType.PASSWORD,
            onClick = { onSelectAuth(RemoteAuthType.PASSWORD) },
            enabled = !keyLoading,
            label = { Text("密码") }
        )
    }

    if (state.draft.auth == RemoteAuthType.PASSWORD) {
        OutlinedTextField(
            value = state.draft.password,
            onValueChange = { value -> session.machine.updateDraft { it.copy(password = value) } },
            label = { Text("SSH 密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = state.fieldErrors[RemoteWizardField.PASSWORD] != null,
            supportingText = state.fieldErrors[RemoteWizardField.PASSWORD]?.let { message -> { Text(message) } },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "密码认证需要远程 sshd 允许 PasswordAuthentication；更推荐使用私钥。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Text("私钥文件", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPickKey, enabled = !keyLoading) { Text("选择私钥文件") }
            Text(
                state.draft.privateKeyFileName.ifBlank { "未选择文件" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        if (keyLoading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("正在读取私钥文件…", style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedTextField(
            value = state.draft.privateKey,
            onValueChange = { value -> session.machine.updateDraft { it.copy(privateKey = value) } },
            enabled = !keyLoading,
            label = { Text("或粘贴 OpenSSH/PEM 私钥全文") },
            minLines = 3,
            maxLines = 6,
            visualTransformation = PasswordVisualTransformation(),
            isError = state.fieldErrors[RemoteWizardField.PRIVATE_KEY] != null,
            supportingText = state.fieldErrors[RemoteWizardField.PRIVATE_KEY]?.let { message -> { Text(message) } },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = state.draft.passphrase,
            onValueChange = { value -> session.machine.updateDraft { it.copy(passphrase = value) } },
            enabled = !keyLoading,
            label = { Text("私钥口令（可选）") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = state.fieldErrors[RemoteWizardField.PASSPHRASE] != null,
            supportingText = state.fieldErrors[RemoteWizardField.PASSPHRASE]?.let { message -> { Text(message) } },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "私钥留在手机本地，不会上传；当前支持 RSA/ECDSA 私钥，Ed25519 暂不支持。带口令的私钥必须填写口令。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (state.notice.isNotBlank()) {
        Text(state.notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun StepFingerprint(session: WizardSession, state: com.tanuki.runtime.RemoteWizardState) {
    Text(
        "这是首次连接该主机（或本机还没有信任记录）。请先核对指纹，再决定是否信任。",
        style = MaterialTheme.typography.bodyMedium
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("远程主机", style = MaterialTheme.typography.labelMedium)
            Text(state.draft.host.ifBlank { "由配对链接指定" }, style = MaterialTheme.typography.bodyMedium)
            Text("主机密钥 SHA256", style = MaterialTheme.typography.labelMedium)
            SelectionContainer {
                Text(
                    state.hostKeyFingerprint.orEmpty(),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
    Text(
        "在远程主机执行：ssh-keygen -lf /etc/ssh/ssh_host_rsa_key.pub（ECDSA 用 ssh_host_ecdsa_key.pub），逐字核对上面的 SHA256。",
        style = MaterialTheme.typography.bodySmall
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = state.hostKeyAcknowledged,
            onCheckedChange = { session.machine.acknowledgeHostKey(it) }
        )
        Text(
            "我已通过独立渠道核对上述 SHA256 指纹，确认信任此主机",
            style = MaterialTheme.typography.bodySmall
        )
    }
    Text(
        "不要仅因为页面提示就点信任。指纹不一致可能意味着主机被重装，也可能意味着中间人攻击；无法核对时应停止连接。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
}

@Composable
private fun StepTest(session: WizardSession, state: com.tanuki.runtime.RemoteWizardState) {
    Text(
        "正在分阶段验证连接。每一阶段失败都会指出原因和下一步，不会只显示“连接失败”。",
        style = MaterialTheme.typography.bodyMedium
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteConnectionStage.testStages.forEach { stage ->
                StageRow(stage, session.machine.stageStatus(stage))
            }
        }
    }
    if (state.busy) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("正在测试，请稍候…", style = MaterialTheme.typography.bodySmall)
        }
    }
    state.failure?.let { failure ->
        FailurePanel(info = failure, onRetry = null, onEdit = null)
    }
}

@Composable
private fun StageRow(stage: RemoteConnectionStage, status: RemoteStageStatus) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        when (status) {
            RemoteStageStatus.ACTIVE -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            RemoteStageStatus.DONE -> Text(
                "✓",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(20.dp)
            )
            RemoteStageStatus.FAILED -> Text(
                "✕",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.width(20.dp)
            )
            RemoteStageStatus.PENDING -> Text(
                "○",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp)
            )
        }
        Text(stage.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        when (status) {
            RemoteStageStatus.DONE -> Text("已完成", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            RemoteStageStatus.ACTIVE -> Text("进行中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            RemoteStageStatus.FAILED -> Text("失败", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            RemoteStageStatus.PENDING -> Unit
        }
    }
}

@Composable
private fun StepDone(
    model: TanukiUiModel,
    session: WizardSession,
    state: com.tanuki.runtime.RemoteWizardState,
    onRetryConnect: () -> Unit,
    onRetest: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("测试通过", style = MaterialTheme.typography.titleMedium)
            Text("SSH 认证与远程 DSH 探测均成功。", style = MaterialTheme.typography.bodySmall)
            state.localPort?.let { RemoteFact("本机隧道", "127.0.0.1:$it") }
            state.hostKeyFingerprint?.let { RemoteFact("主机密钥", it) }
        }
    }

    OutlinedTextField(
        value = state.name,
        onValueChange = session.machine::setName,
        label = { Text("连接名称") },
        placeholder = { Text("例如：家里电脑、公司开发机") },
        singleLine = true,
        isError = state.fieldErrors[RemoteWizardField.NAME] != null,
        supportingText = state.fieldErrors[RemoteWizardField.NAME]?.let { message -> { Text(message) } },
        modifier = Modifier.fillMaxWidth()
    )
    Text(
        "保存并连接会切换到远程 DSH 页面，当前本地 DSH 页面会断开；本地运行时继续在后台运行。之后可在“我的连接”里随时编辑或删除。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (model.remoteFailure != null) {
        FailurePanel(
            info = model.remoteFailure,
            onRetry = if (model.remoteFailure.retryable) onRetryConnect else null,
            onEdit = onRetest
        )
    }
    if (state.notice.isNotBlank()) {
        Text(state.notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun FailurePanel(
    info: RemoteFailureInfo,
    onRetry: (() -> Unit)?,
    onEdit: (() -> Unit)?
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${info.stageLabel}失败", style = MaterialTheme.typography.titleSmall)
            Text(info.reason, style = MaterialTheme.typography.bodyMedium)
            Text("下一步：${info.suggestion}", style = MaterialTheme.typography.bodySmall)
            if ((info.retryable && onRetry != null) || onEdit != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (info.retryable && onRetry != null) {
                        Button(onClick = onRetry) { Text("重试") }
                    }
                    if (onEdit != null) {
                        TextButton(onClick = onEdit) { Text("返回修改") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteFact(name: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            name,
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
}

/** chip 的纯展示状态，便于 JVM 回归；不依赖 Compose。 */
enum class RemoteHostChipTone { LOCAL, REMOTE, BUSY, ERROR, OFFLINE }

data class RemoteHostChipState(val label: String, val tone: RemoteHostChipTone)

object RemoteHostSwitchPresentation {
    fun of(status: RemoteHostStatus, localRunning: Boolean): RemoteHostChipState = when (status.phase) {
        RemotePhase.CONNECTING -> RemoteHostChipState("切换中…", RemoteHostChipTone.BUSY)
        RemotePhase.REMOTE -> RemoteHostChipState("电脑·已连接", RemoteHostChipTone.REMOTE)
        RemotePhase.FALLBACK -> RemoteHostChipState("本机·已回退", RemoteHostChipTone.ERROR)
        RemotePhase.LOCAL -> if (localRunning) {
            RemoteHostChipState("本机·运行中", RemoteHostChipTone.LOCAL)
        } else {
            RemoteHostChipState("本机·未运行", RemoteHostChipTone.OFFLINE)
        }
    }

    /** 切换中禁止再次点击，避免并发切换；失败后可回到原主机或列表。 */
    fun canSwitch(status: RemoteHostStatus): Boolean = status.phase != RemotePhase.CONNECTING
}

/**
 * 供 DshNativePage 头部复用的主机切换 chip。
 *
 * 接线方只需要传入真实的 [status] 与本地服务是否运行；不要用“上次成功”的本地状态冒充。
 */
@Composable
fun RemoteHostSwitchChip(
    status: RemoteHostStatus,
    localRunning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visual = RemoteHostSwitchPresentation.of(status, localRunning)
    val dotColor = when (visual.tone) {
        RemoteHostChipTone.LOCAL, RemoteHostChipTone.REMOTE -> MaterialTheme.colorScheme.primary
        RemoteHostChipTone.BUSY -> MaterialTheme.colorScheme.tertiary
        RemoteHostChipTone.ERROR -> MaterialTheme.colorScheme.error
        RemoteHostChipTone.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = onClick,
        enabled = RemoteHostSwitchPresentation.canSwitch(status),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(8.dp).background(dotColor, CircleShape))
            Text(visual.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * 主机切换 sheet；由页面根部持有是否显示。
 *
 * 切换失败时保留 [RemoteHostStatus.error] 与“返回原主机”动作，不静默回到另一台主机。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteHostSwitchSheet(
    status: RemoteHostStatus,
    localRunning: Boolean,
    profiles: List<RemoteHostProfile>,
    onDismiss: () -> Unit,
    onSelectLocal: () -> Unit,
    onSelectProfile: (RemoteHostProfile) -> Unit,
    onRestorePrevious: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("切换主机", style = MaterialTheme.typography.titleLarge)
            Text(
                RemoteHostSwitchPresentation.of(status, localRunning).label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (status.phase == RemotePhase.CONNECTING) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("正在切换，请稍候…", style = MaterialTheme.typography.bodySmall)
                }
            }
            SwitchRow(
                title = "本机",
                subtitle = if (localRunning) "本地 DSH · 运行中" else "本地 DSH · 未运行",
                selected = !status.connected,
                enabled = RemoteHostSwitchPresentation.canSwitch(status),
                onClick = onSelectLocal
            )
            profiles.forEach { profile ->
                SwitchRow(
                    title = profile.name,
                    subtitle = profile.config.relay?.let { "TLS 中继 · ${it.endpointLabel()}" }
                        ?: "${profile.config.user}@${profile.config.host}:${profile.config.sshPort}",
                    selected = status.connected && status.host == profile.config.displayName(),
                    enabled = RemoteHostSwitchPresentation.canSwitch(status),
                    onClick = { onSelectProfile(profile) }
                )
            }
            if (status.phase == RemotePhase.FALLBACK && status.error.isNotBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("切换失败，已回退本机", style = MaterialTheme.typography.titleSmall)
                        Text(status.error, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onRestorePrevious) { Text("返回原主机") }
                            TextButton(onClick = onDismiss) { Text("留在本机") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Text("当前", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun phaseLabel(phase: RemotePhase): String = when (phase) {
    RemotePhase.LOCAL -> "本地运行时"
    RemotePhase.CONNECTING -> "正在连接"
    RemotePhase.REMOTE -> "远程已连接"
    RemotePhase.FALLBACK -> "已回退本地"
}

private fun modeImpact(phase: RemotePhase): String = when (phase) {
    RemotePhase.LOCAL ->
        "当前使用手机上的 DSH。选择下面的电脑即可开始连接。"
    RemotePhase.CONNECTING ->
        "正在连接电脑，请稍候。"
    RemotePhase.REMOTE ->
        "当前使用电脑上的 DSH。断开后会回到手机。"
    RemotePhase.FALLBACK ->
        "未能连接电脑，已回到手机。可以按下方提示重试。"
}

/**
 * 读取系统剪贴板的第一条文本。
 *
 * Android 10+ 只在应用可见/聚焦时允许读取；无内容、剪贴板服务不可用或被系统拒绝时
 * 一律返回 null，由向导保留手动粘贴框，不把“读不到”当成错误弹窗。
 */
private fun readPairingClipboard(context: Context): String? = runCatching {
    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
    if (clip == null || clip.itemCount == 0) {
        null
    } else {
        clip.getItemAt(0).coerceToText(context)?.toString()
    }
}.getOrNull()

private data class PickedPrivateKey(val name: String, val text: String)

/** 私钥文件读取上限与配置校验一致，避免把超大文件读进内存。 */
private fun readPrivateKey(context: Context, uri: Uri): PickedPrivateKey {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "所选私钥文件"
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > RemoteHostConfig.MAX_PRIVATE_KEY_BYTES) {
                throw IllegalArgumentException("私钥文件过大（上限 64 KiB），请选择正确的私钥")
            }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } ?: throw IllegalArgumentException("无法读取所选文件，请重新选择")
    val text = bytes.toString(Charsets.UTF_8)
    if (text.isBlank()) throw IllegalArgumentException("私钥文件为空，请重新选择")
    return PickedPrivateKey(name, text)
}
