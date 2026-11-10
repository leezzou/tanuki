package com.tanuki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tanuki.runtime.RemoteAuthType
import com.tanuki.runtime.RemoteHostConfig
import com.tanuki.runtime.RemotePhase
import com.tanuki.runtime.RemoteRelayPairing

/** 设置页的远程主机入口；支持直连 SSH，以及无公网 IP 时经 TLS 中继的反向注册配对。 */
@Composable
fun RemoteHostSettings(model: TanukiUiModel, c: TanukiUiCallbacks) {
    var editing by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    val status = model.remote
    val saved = model.remoteHost

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("远程主机", style = MaterialTheme.typography.headlineSmall)
            Text(
                "直连 SSH 或经 TLS 中继把远程 DSH 的 loopback 端口转发到本机；中继只转发 SSH 密文，WebRTC/TURN 不在本切片内。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("连接状态", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        phaseLabel(status.phase),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (status.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (status.connected) {
                    RemoteFact("远程主机", status.host)
                    RemoteFact("SSH 隧道", "127.0.0.1:${status.localPort ?: "-"}")
                    RemoteFact("主机密钥", status.fingerprint)
                }
                if (model.remoteMessage.isNotBlank()) {
                    Text(model.remoteMessage, style = MaterialTheme.typography.bodySmall)
                }
                if (model.remoteError.isNotBlank()) {
                    SelectionContainer {
                        Text(model.remoteError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (saved != null) {
                    if (saved.relay != null) {
                        RemoteFact("连接方式", "TLS 中继")
                        RemoteFact("中继", saved.relay.endpointLabel())
                        RemoteFact("房间", saved.relay.roomId.take(8) + "…")
                        RemoteFact("主机", saved.displayName())
                    } else {
                        RemoteFact("已保存", "${saved.user}@${saved.host}:${saved.sshPort}")
                    }
                    RemoteFact("远程 DSH", "127.0.0.1:${saved.remoteDshPort}")
                }
            }
        }

        if (model.remotePendingFingerprint != null) {
            HostKeyConfirmationCard(model.remotePendingFingerprint, c)
        }

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("配置与切换", style = MaterialTheme.typography.titleSmall)
                Text(
                    "密码、私钥、口令和 token 只加密保存在本机；私钥和密码不会写入日志或导出。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { editing = true }, enabled = !model.remoteBusy) {
                        Text(if (saved == null) "配置远程主机" else "编辑配置")
                    }
                    if (saved != null) {
                        OutlinedButton(
                            onClick = { c.connectRemoteHost(saved) },
                            enabled = !model.remoteBusy && !status.connected
                        ) { Text("连接已保存") }
                    }
                }
                if (status.connected || status.phase == RemotePhase.CONNECTING) {
                    TextButton(onClick = { c.disconnectRemoteHost() }, enabled = !model.remoteBusy) { Text("断开远程并切回本地") }
                }
                if (saved != null) {
                    TextButton(onClick = { confirmForget = true }, enabled = !model.remoteBusy) { Text("清除配置与主机密钥") }
                }
                Text(
                    "当前切片支持 RSA/ECDSA 私钥与密码认证；Ed25519 私钥/主机密钥需要额外 BouncyCastle，留待后续。SSH 隧道只转发到远程 127.0.0.1，不开放远程端口到局域网。中继模式仍由本机核对远程 SSH 主机密钥。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (editing) {
        RemoteHostDialog(model, c, onDismiss = { editing = false })
    }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("清除远程主机配置？") },
            text = { Text("将删除已保存的密码/私钥/token 和主机密钥信任记录，并断开当前远程连接。本地运行时不受影响。") },
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
private fun HostKeyConfirmationCard(fingerprint: String, c: TanukiUiCallbacks) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("首次连接：确认远程主机密钥", style = MaterialTheme.typography.titleSmall)
            SelectionContainer {
                Text(fingerprint, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "请与远程主机上 ssh-keygen -lf /etc/ssh/ssh_host_*_key.pub 的输出核对。指纹不一致时不要确认。",
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = c.confirmRemoteHostKey) { Text("信任并继续") }
                TextButton(onClick = c.cancelRemoteHostKey) { Text("取消") }
            }
        }
    }
}

@Composable
private fun RemoteHostDialog(model: TanukiUiModel, c: TanukiUiCallbacks, onDismiss: () -> Unit) {
    val initial = model.remoteHost
    var host by remember(initial) { mutableStateOf(initial?.host.orEmpty()) }
    var sshPort by remember(initial) { mutableStateOf(initial?.sshPort?.toString() ?: "22") }
    var user by remember(initial) { mutableStateOf(initial?.user.orEmpty()) }
    var auth by remember(initial) { mutableStateOf(initial?.auth ?: RemoteAuthType.PRIVATE_KEY) }
    var password by remember(initial) { mutableStateOf(initial?.password.orEmpty()) }
    var privateKey by remember(initial) { mutableStateOf(initial?.privateKey.orEmpty()) }
    var passphrase by remember(initial) { mutableStateOf(initial?.passphrase.orEmpty()) }
    var remoteDshPort by remember(initial) { mutableStateOf(initial?.remoteDshPort?.toString() ?: "3080") }
    var token by remember(initial) { mutableStateOf(initial?.token.orEmpty()) }
    var relayUri by remember(initial) { mutableStateOf(initial?.relay?.toUri().orEmpty()) }
    var pairingError by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    val busy = model.remoteBusy

    fun config(): RemoteHostConfig? {
        val trimmed = relayUri.trim()
        val relay = if (trimmed.isEmpty()) {
            null
        } else {
            RemoteRelayPairing.parse(trimmed).getOrElse {
                pairingError = it.message ?: "配对链接格式无效"
                return null
            }
        }
        pairingError = ""
        return RemoteHostConfig(
            host = host.trim().ifBlank { relay?.displayName().orEmpty() },
            sshPort = sshPort.toIntOrNull() ?: -1,
            user = user,
            auth = auth,
            password = password,
            privateKey = privateKey,
            passphrase = passphrase,
            remoteDshPort = remoteDshPort.toIntOrNull() ?: -1,
            token = token,
            relay = relay
        )
    }

    LaunchedEffect(busy, connecting) {
        if (connecting && busy) onDismiss()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (initial == null) "配置远程主机" else "编辑远程主机") },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    relayUri, { relayUri = it; pairingError = "" },
                    label = { Text("配对链接（可选，含一次性密钥）") },
                    minLines = 2, maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "粘贴主机端 agent 生成的 tanuki-relay:// 链接即经 TLS 中继连接；留空使用直连 SSH。链接含一次性配对密钥，不要截图或转发给他人。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(host, { host = it }, label = { Text("主机（直连地址；中继时作展示名，可留空）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(sshPort, { sshPort = it }, label = { Text("SSH 端口") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(user, { user = it }, label = { Text("用户名") }, singleLine = true, modifier = Modifier.weight(1.4f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = auth == RemoteAuthType.PRIVATE_KEY, onClick = { auth = RemoteAuthType.PRIVATE_KEY }, label = { Text("私钥") })
                    FilterChip(selected = auth == RemoteAuthType.PASSWORD, onClick = { auth = RemoteAuthType.PASSWORD }, label = { Text("密码") })
                }
                if (auth == RemoteAuthType.PRIVATE_KEY) {
                    OutlinedTextField(
                        privateKey, { privateKey = it },
                        label = { Text("OpenSSH/PEM 私钥全文") },
                        minLines = 3, maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        passphrase, { passphrase = it },
                        label = { Text("私钥口令（可选）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        password, { password = it },
                        label = { Text("SSH 密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    remoteDshPort, { remoteDshPort = it },
                    label = { Text("远程 DSH 端口") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    token, { token = it },
                    label = { Text("远程 DSH token（可选）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (model.remotePendingFingerprint != null) {
                    Text("等待主机密钥确认；确认后才会发送 SSH 认证信息。", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "远程主机：${model.remotePendingHost.ifBlank { host }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    SelectionContainer {
                        Text(
                            model.remotePendingFingerprint,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        "请与远程主机上 ssh-keygen -lf /etc/ssh/ssh_host_*_key.pub 的输出核对；不一致时不要确认。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = c.confirmRemoteHostKey, enabled = !busy) { Text("信任并继续") }
                        TextButton(onClick = c.cancelRemoteHostKey, enabled = !busy) { Text("取消") }
                    }
                }
                if (pairingError.isNotBlank()) {
                    Text(pairingError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else if (model.remoteError.isNotBlank()) {
                    Text(model.remoteError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else if (model.remoteMessage.isNotBlank()) {
                    Text(model.remoteMessage, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "直连或中继都只建立 SSH 本地端口转发，失败自动回退本地；中继/WebRTC 打洞后续实现。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    config()?.let {
                        c.connectRemoteHost(it)
                        connecting = true
                    }
                },
                enabled = !busy
            ) { Text("保存并连接") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { config()?.let { c.testRemoteHost(it) } }, enabled = !busy) { Text("测试连接") }
                TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
            }
        }
    )
}

@Composable
private fun RemoteFact(name: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            name,
            modifier = Modifier.width(84.dp),
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
