package com.tanuki.ui

import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tanuki.runtime.RemoteAuthType
import com.tanuki.runtime.RemoteConnectionMethod
import com.tanuki.runtime.RemoteConnectionStage
import com.tanuki.runtime.RemoteFailureInfo
import com.tanuki.runtime.RemoteHostConfig
import com.tanuki.runtime.RemoteHostProfile
import com.tanuki.runtime.RemoteHostWizard
import com.tanuki.runtime.RemotePhase
import com.tanuki.runtime.RemoteStageStatus
import com.tanuki.runtime.RemoteWizardField
import com.tanuki.runtime.RemoteWizardStep
import com.tanuki.runtime.RemoteWizardValidation
import java.io.ByteArrayOutputStream

/** 设置页的远程主机入口：状态、命名连接库和分步向导。 */
@Composable
fun RemoteHostSettings(model: TanukiUiModel, c: TanukiUiCallbacks) {
    var wizard by remember { mutableStateOf<WizardSession?>(null) }
    var confirmForget by remember { mutableStateOf(false) }
    var pendingConnect by remember { mutableStateOf<RemoteHostProfile?>(null) }
    var pendingDelete by remember { mutableStateOf<RemoteHostProfile?>(null) }
    val status = model.remote
    val saved = model.remoteHost
    val savedProfile = saved?.let { config ->
        model.remoteProfiles.firstOrNull {
            it.config.hostIdentity() == config.hostIdentity() &&
                it.config.user == config.user &&
                it.config.remoteDshPort == config.remoteDshPort
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("远程主机", style = MaterialTheme.typography.headlineSmall)
            Text(
                "用向导连接另一台电脑上的 DSH。每一步都会说明示例和校验原因；连接失败时会指出卡住的阶段和下一步。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        StatusCard(
            model = model,
            onNew = { wizard = WizardSession() },
            onEdit = { saved?.let { wizard = WizardSession(it, savedProfile?.name.orEmpty(), savedProfile?.id) } },
            onForget = { confirmForget = true },
            onConnectSaved = { saved?.let { c.connectRemoteHost(it) } },
            onRetry = { saved?.let { c.connectRemoteHost(it) } }
        )

        if (model.remotePendingFingerprint != null && wizard == null) {
            HostKeyConfirmationCard(model.remotePendingFingerprint, model.remotePendingHost, c)
        }

        SavedProfilesCard(
            model = model,
            onConnect = { pendingConnect = it },
            onEdit = { profile -> wizard = WizardSession(profile.config, profile.name, profile.id) },
            onDelete = { pendingDelete = it }
        )

        Text(
            "安全边界：始终校验 SSH 主机密钥与中继 TLS，不提供关闭校验的开关；密码、私钥、口令和 token 只加密保存在本机，不写日志、不导出。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    c.connectRemoteProfile(profile)
                }) { Text("连接") }
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

@Composable
private fun StatusCard(
    model: TanukiUiModel,
    onNew: () -> Unit,
    onEdit: () -> Unit,
    onForget: () -> Unit,
    onConnectSaved: () -> Unit,
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
                Text("当前状态", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
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
                    onEdit = onEdit
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNew, enabled = !model.remoteBusy) { Text("新增远程连接") }
                if (saved != null) {
                    OutlinedButton(
                        onClick = onConnectSaved,
                        enabled = !model.remoteBusy && !status.connected
                    ) { Text("连接当前配置") }
                }
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
}

@Composable
private fun RemoteWizardDialog(
    model: TanukiUiModel,
    c: TanukiUiCallbacks,
    session: WizardSession,
    onDismiss: () -> Unit
) {
    val state by session.machine.state.collectAsState()
    val context = LocalContext.current
    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { readPrivateKey(context, uri) }
            .onSuccess { picked ->
                session.machine.setAuth(RemoteAuthType.PRIVATE_KEY)
                session.machine.updateDraft {
                    it.copy(privateKey = picked.text, privateKeyFileName = picked.name)
                }
                session.machine.setNotice("已选择私钥文件：${picked.name}（内容不会显示）")
            }
            .onFailure { error ->
                session.machine.setNotice(error.message ?: "无法读取私钥文件，请重新选择")
            }
    }

    // 控制器是唯一连接执行者；向导只把阶段/指纹/结果转成步骤状态。
    LaunchedEffect(model.remotePendingFingerprint) {
        model.remotePendingFingerprint?.let(session.machine::onHostKeyRequired)
    }
    LaunchedEffect(model.remoteStage) {
        model.remoteStage?.let(session.machine::onStage)
    }
    LaunchedEffect(model.remoteAttemptSucceeded) {
        if (model.remoteAttemptSucceeded) session.machine.onTestSucceeded(model.remoteLocalPort)
    }
    LaunchedEffect(model.remoteFailure) {
        model.remoteFailure?.let(session.machine::onFailure)
    }

    fun dismiss() {
        if (state.busy) return
        if (model.remotePendingFingerprint != null) c.cancelRemoteHostKey()
        onDismiss()
    }

    fun testNow() {
        val config = session.machine.buildConfig()
        if (config == null) {
            session.machine.onFailure(
                com.tanuki.runtime.RemoteConnectionErrors.validation("连接配置不完整，请返回上一步检查主机、账号和认证信息")
            )
        } else {
            c.testRemoteHost(config)
        }
    }

    fun startNext() {
        val before = session.machine.state.value.step
        if (!session.machine.next()) return
        val after = session.machine.state.value
        if (before != RemoteWizardStep.TEST && after.step == RemoteWizardStep.TEST && after.busy) {
            testNow()
        }
    }

    fun goBack() {
        if (session.machine.state.value.step == RemoteWizardStep.FINGERPRINT) c.cancelRemoteHostKey()
        session.machine.back()
    }

    fun save(connect: Boolean) {
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
                            "第 ${state.step.position}/${RemoteWizardStep.total} 步 · ${state.step.title}",
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
                    repeat(RemoteWizardStep.total) { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .background(
                                    if (index < state.step.position) {
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
                        RemoteWizardStep.AUTHENTICATION -> StepAuthentication(
                            session = session,
                            state = state,
                            onPickKey = { keyPicker.launch(arrayOf("*/*")) }
                        )
                        RemoteWizardStep.FINGERPRINT -> StepFingerprint(session, state)
                        RemoteWizardStep.TEST -> StepTest(session, state)
                        RemoteWizardStep.DONE -> StepDone(
                            model = model,
                            session = session,
                            state = state,
                            onRetryConnect = { session.machine.buildConfig()?.let(c.connectRemoteHost) },
                            onRetest = {
                                session.machine.beginTest()
                                testNow()
                            }
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
                            Button(onClick = ::startNext, modifier = Modifier.weight(1f)) { Text("下一步：选择认证") }
                        }
                        RemoteWizardStep.AUTHENTICATION -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = ::goBack) { Text("上一步") }
                            Button(onClick = ::startNext, modifier = Modifier.weight(1f)) { Text("开始测试连接") }
                        }
                        RemoteWizardStep.FINGERPRINT -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = ::goBack) { Text("返回修改") }
                            Button(
                                onClick = {
                                    if (session.machine.confirmFingerprint()) c.confirmRemoteHostKey()
                                },
                                enabled = state.canConfirmFingerprint,
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
                                            onClick = { if (session.machine.retry()) testNow() },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("重试") }
                                    }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = ::goBack, enabled = !state.busy) { Text("返回修改") }
                                    Button(
                                        onClick = {
                                            session.machine.beginTest()
                                            testNow()
                                        },
                                        enabled = !state.busy,
                                        modifier = Modifier.weight(1f)
                                    ) { Text(if (state.busy) "正在测试…" else "重新测试") }
                                }
                            }
                        }
                        RemoteWizardStep.DONE -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                session.machine.beginTest()
                                testNow()
                            }, enabled = !state.busy) { Text("重新测试") }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { save(connect = false) }, modifier = Modifier.weight(1f)) {
                                    Text(if (state.isEditing) "仅保存修改" else "仅保存")
                                }
                                Button(onClick = { save(connect = true) }, modifier = Modifier.weight(1f)) {
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
    Text(
        if (pairing) {
            "粘贴主机端生成的配对链接。链接包含一次性密钥，不要截图或转发。"
        } else {
            "填写远程主机的地址和登录账号。端口不确定时先保留默认值。"
        },
        style = MaterialTheme.typography.bodyMedium
    )
    if (pairing) {
        OutlinedTextField(
            value = state.draft.relayUri,
            onValueChange = { value -> session.machine.updateDraft { it.copy(relayUri = value) } },
            label = { Text("配对链接") },
            placeholder = { Text("tanuki-relay://v1?relay=…&room=…&token=…&host=…") },
            minLines = 2,
            maxLines = 3,
            isError = state.fieldErrors[RemoteWizardField.RELAY_LINK] != null,
            supportingText = state.fieldErrors[RemoteWizardField.RELAY_LINK]?.let { message -> { Text(message) } },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "配对链接由远程主机端生成；手机只经 TLS 中继转发 SSH 密文，仍会独立校验 SSH 主机密钥。",
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
private fun StepAuthentication(
    session: WizardSession,
    state: com.tanuki.runtime.RemoteWizardState,
    onPickKey: () -> Unit
) {
    Text(
        "选择 SSH 认证方式。密码和私钥只会传给 SSH，并加密保存在本机。",
        style = MaterialTheme.typography.bodyMedium
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.draft.auth == RemoteAuthType.PRIVATE_KEY,
            onClick = { session.machine.setAuth(RemoteAuthType.PRIVATE_KEY) },
            label = { Text("私钥") }
        )
        FilterChip(
            selected = state.draft.auth == RemoteAuthType.PASSWORD,
            onClick = { session.machine.setAuth(RemoteAuthType.PASSWORD) },
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
            OutlinedButton(onClick = onPickKey) { Text("选择私钥文件") }
            Text(
                state.draft.privateKeyFileName.ifBlank { "未选择文件" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = state.draft.privateKey,
            onValueChange = { value -> session.machine.updateDraft { it.copy(privateKey = value) } },
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

private fun phaseLabel(phase: RemotePhase): String = when (phase) {
    RemotePhase.LOCAL -> "本地运行时"
    RemotePhase.CONNECTING -> "正在连接"
    RemotePhase.REMOTE -> "远程已连接"
    RemotePhase.FALLBACK -> "已回退本地"
}

private fun modeImpact(phase: RemotePhase): String = when (phase) {
    RemotePhase.LOCAL ->
        "当前使用本地 DSH。连接远程会断开本地 DSH 页面并切换到远程页面；本地运行时继续在后台运行。"
    RemotePhase.CONNECTING ->
        "正在切换到远程 DSH。切换期间页面可能短暂不可用，本地 DSH 页面会断开。"
    RemotePhase.REMOTE ->
        "当前使用远程 DSH。断开或切换会关闭 SSH 隧道并刷新管理页，本地 DSH 页面会重新接管。"
    RemotePhase.FALLBACK ->
        "远程连接未建立，已回退本地 DSH。检查下方原因后可以直接重试，或重新运行向导。"
}

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
