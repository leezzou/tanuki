package com.tanuki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tanuki.runtime.DshVersions
import com.tanuki.runtime.InstalledDshInventory
import com.tanuki.runtime.InstalledDshVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 独立的已安装 DSH 版本管理：清单读取、切换与卸载都留在本文件，主界面只提供入口和回调。
 * 当前版本禁止在此卸载；旧版切换不静默失败，而是禁用按钮并说明需要恢复匹配快照。
 */
@Composable
internal fun DshVersionManagerDialog(
    activeVersion: String,
    idle: Boolean,
    inventoryKey: Set<String>,
    load: suspend () -> InstalledDshInventory,
    onSwitch: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inventory by remember { mutableStateOf<InstalledDshInventory?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var pendingSwitch by remember { mutableStateOf<String?>(null) }
    var pendingUninstall by remember { mutableStateOf<InstalledDshVersion?>(null) }

    LaunchedEffect(activeVersion, inventoryKey, reload) {
        loadError = null
        runCatching { withContext(Dispatchers.IO) { load() } }
            .onSuccess { inventory = it }
            .onFailure { loadError = it.message ?: "读取已安装版本失败" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("已安装 DSH 版本") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val current = inventory
                when {
                    loadError != null -> Text(loadError!!, color = MaterialTheme.colorScheme.error)
                    current == null -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        ManagerCaption("正在读取版本、校验状态与占用…")
                    }
                    current.versions.isEmpty() -> {
                        Text("尚未安装 DSH 版本。")
                        ManagerCaption("先通过“选择版本安装”安装程序；安装后这里会显示体积、安装时间与校验状态。")
                    }
                    else -> {
                        Text("共 ${current.versions.size} 个版本，合计占用 ${formatDshBytes(current.totalBytes)}",
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        ManagerCaption("“当前使用”是应用正在使用的版本，禁止卸载；“备用”可切换或卸载。卸载只删除程序目录，不删除配置、会话、项目、快照与其他版本。")
                        LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(current.versions, key = { it.version }) { item ->
                                InstalledDshRow(
                                    item = item,
                                    activeVersion = activeVersion,
                                    idle = idle,
                                    onSwitch = { pendingSwitch = it },
                                    onUninstall = { pendingUninstall = it }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        if (!activeVersion.matches(DshVersions.VERSION)) {
                            Text("当前安装元数据缺少有效 DSH 版本，无法确认哪个版本正在使用；列表仅供查看，已禁止切换与卸载。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        } else if (current.versions.none { it.active }) {
                            Text("当前安装元数据指向的 DSH $activeVersion 不在清单中；请重装该版本或恢复匹配的数据快照。",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (!idle) ManagerCaption("服务运行中或正在执行操作：切换与卸载已禁用，请先停止 DSH 并等待操作完成。")
            }
        },
        confirmButton = {
            TextButton(onClick = { reload++ }, enabled = inventory != null) { Text("刷新") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )

    pendingSwitch?.let { version ->
        AlertDialog(
            onDismissRequest = { pendingSwitch = null },
            title = { Text("切换到 DSH $version") },
            text = { Text("切换前会保存当前版本的数据快照；不重新下载，也不删除任何已安装版本。旧版需先恢复匹配的数据快照。请确认 DSH 服务已停止。") },
            confirmButton = {
                TextButton(onClick = { pendingSwitch = null; onSwitch(version) }) { Text("切换") }
            },
            dismissButton = { TextButton(onClick = { pendingSwitch = null }) { Text("取消") } }
        )
    }

    pendingUninstall?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            title = { Text("确认卸载 DSH ${item.version}？") },
            text = {
                Text("将删除备用版本的程序目录：占用 ${formatDshBytes(item.bytes)}，安装于 ${formatDshTime(item.installedAtMillis)}。\n\n" +
                    "不会删除配置、会话、扩展、项目、用户数据、数据快照、共享下载缓存与其他已安装版本；" +
                    "如需再回滚到该版本，需要重新安装匹配程序。当前使用版本不能在此卸载。")
            },
            confirmButton = {
                TextButton(
                    onClick = { pendingUninstall = null; item.version.let(onUninstall) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("确认卸载") }
            },
            dismissButton = { TextButton(onClick = { pendingUninstall = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun InstalledDshRow(
    item: InstalledDshVersion,
    activeVersion: String,
    idle: Boolean,
    onSwitch: (String) -> Unit,
    onUninstall: (InstalledDshVersion) -> Unit
) {
    val currentKnown = activeVersion.matches(DshVersions.VERSION)
    val older = !item.active && currentKnown && DshVersions.isOlder(item.version, activeVersion)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.version, style = MaterialTheme.typography.titleMedium)
            StatusPill(if (item.active) "当前使用" else "备用", item.active)
            StatusPill(if (item.verified) "已校验" else "未校验", item.verified)
        }
        ManagerCaption("占用 ${formatDshBytes(item.bytes)} · 安装于 ${formatDshTime(item.installedAtMillis)}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.active) {
                ManagerCaption("当前使用中，禁止卸载；如需清理请先切换到其他版本。")
            } else if (!currentKnown || !DshVersions.canUninstallFromManager(item.version, activeVersion)) {
                ManagerCaption("当前使用的版本未知，已禁止切换与卸载。")
            } else {
                OutlinedButton(onClick = { onSwitch(item.version) }, enabled = idle && !older && item.lockVerified,
                    modifier = Modifier.heightIn(min = 44.dp)) { Text("切换") }
                TextButton(onClick = { onUninstall(item) }, enabled = idle,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("卸载") }
            }
        }
        if (older) {
            Text(DshVersions.DOWNGRADE_MESSAGE + "；当前数据可能与旧版不兼容。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!item.lockVerified) {
            Text("依赖锁缺失或与 .installed 不一致，无法切换；仍可卸载后重装该版本。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ManagerCaption(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

internal fun formatDshBytes(bytes: Long): String {
    val units = listOf("B", "KiB", "MiB", "GiB")
    var value = bytes.coerceAtLeast(0L).toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) "${bytes.coerceAtLeast(0L)} B"
        else String.format(Locale.getDefault(), "%.1f %s", value, units[index])
}

internal fun formatDshTime(millis: Long): String =
    if (millis <= 0L) "未知"
    else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
