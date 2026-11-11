package com.tanuki.ui

import com.tanuki.ui.design.TanukiOutlinedButton as OutlinedButton

import com.tanuki.ui.design.TanukiButton as Button

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tanuki.storage.WorkspaceCapacity
import com.tanuki.storage.WorkspaceStorageState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WorkspaceStorageControls(
    storage: WorkspaceStorageState,
    canEdit: Boolean,
    onRefresh: () -> Unit,
    onSave: suspend (Long?) -> Unit
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(storage.limitBytes != null) }
    var draft by rememberSaveable { mutableStateOf(storage.limitBytes?.let(WorkspaceCapacity::giBText).orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { onRefresh() }
    LaunchedEffect(storage.limitBytes, editing) {
        if (!editing) {
            custom = storage.limitBytes != null
            draft = storage.limitBytes?.let(WorkspaceCapacity::giBText).orEmpty()
        }
    }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("工作区空间", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onRefresh, enabled = !storage.measuring && !saving) {
                TanukiIcon(TanukiGlyph.Refresh, "刷新工作区用量", tint = secondary)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(when {
                storage.error != null -> "统计未完成"
                storage.usedBytes != null -> storageSize(storage.usedBytes)
                else -> "尚未统计"
            }, style = MaterialTheme.typography.headlineMedium)
            Text("工作区已用 · 限额 ${storage.limitBytes?.let(::storageSize) ?: "使用可用空间"}",
                style = MaterialTheme.typography.bodyMedium, color = secondary)
        }
        WorkspaceFact("可继续使用", storage.availableBytes?.let(::storageSize) ?: "待确认")
        WorkspaceFact("手机可用空间", storageSize(storage.freeBytes))
        if (storage.complete && storage.limitBytes != null) {
            val fraction = (storage.usedBytes!!.toDouble() / storage.limitBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "工作区空间使用情况" },
                color = if (fraction >= .9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            if (storage.overBudget) Text("已超出限额，请调高限额或选择“使用可用空间”后再启动。", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
        }
        Text(when {
            storage.measuring -> "正在统计工作区用量"
            storage.measuredAtMillis > 0 -> "最近统计 ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(storage.measuredAtMillis))}"
            else -> "刷新后显示实际占用，统计不会读取文件内容。"
        }, style = MaterialTheme.typography.bodySmall, color = secondary)
        storage.configurationError?.let { Text("空间设置读取失败，请重新打开应用。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        storage.error?.let { Text("用量统计未完成，请刷新后重试。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Text("只统计工作区文件，不含运行环境和对话；设置限额不会预占空间或删除文件。",
            style = MaterialTheme.typography.bodySmall, color = secondary)
        if (!editing) {
            OutlinedButton(onClick = { saveError = null; editing = true }, enabled = canEdit,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("调整空间限额") }
            if (!canEdit) Text("请先停止助手并完成当前操作，再调整限额。", style = MaterialTheme.typography.bodySmall, color = secondary)
        } else {
            WorkspaceChoice("使用可用空间", !custom, canEdit && !saving) { custom = false; saveError = null }
            WorkspaceChoice("自定空间限额", custom, canEdit && !saving) {
                custom = true
                if (draft.isBlank()) {
                    val suggested = WorkspaceCapacity.availableForWorkspace(storage.usedBytes ?: 0, storage.freeBytes)
                        .coerceAtMost(5 * WorkspaceCapacity.GIB).coerceAtLeast(WorkspaceCapacity.MIN_LIMIT)
                    draft = WorkspaceCapacity.giBText(suggested)
                }
                saveError = null
            }
            if (custom) {
                OutlinedTextField(value = draft, onValueChange = { if (it.length <= 32) draft = it; saveError = null },
                    label = { Text("空间限额 GiB") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, enabled = canEdit && !saving, isError = saveError != null,
                    supportingText = { Text("至少0.0625 GiB，不能小于工作区已用空间。") },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                if (storage.complete) Text("当前最多可设 ${storageSize(WorkspaceCapacity.availableForWorkspace(storage.usedBytes!!, storage.freeBytes))}，包含现有工作区文件。",
                    style = MaterialTheme.typography.bodySmall, color = secondary)
            }
            if (custom && !storage.complete) Text("用量尚未确认，请先刷新，或选择“使用可用空间”。",
                style = MaterialTheme.typography.bodySmall, color = secondary)
            Text("限额定期检查，写入可能暂时超额；超限会停止助手，可能中断任务。",
                style = MaterialTheme.typography.bodySmall, color = secondary)
            saveError?.let { Text("限额未保存，请检查输入后重试。", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { editing = false; saveError = null }, enabled = !saving,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("取消调整") }
                Button(onClick = {
                    scope.launch {
                        saving = true
                        saveError = null
                        try {
                            val bytes = if (custom) WorkspaceCapacity.parseGiB(draft) else null
                            onSave(bytes)
                            editing = false
                        } catch (error: CancellationException) { throw error }
                        catch (error: Exception) { saveError = error.message ?: "限额未保存，请检查输入后重试。" }
                        finally { saving = false }
                    }
                }, enabled = canEdit && !saving && (!custom || (!storage.measuring && storage.complete)),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (saving) "正在保存…" else "保存限额") }
            }
        }
    }
}

@Composable
private fun WorkspaceChoice(title: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun WorkspaceFact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun storageSize(bytes: Long): String = when {
    bytes >= WorkspaceCapacity.GIB -> String.format(Locale.getDefault(), "%.2f GiB", bytes.toDouble() / WorkspaceCapacity.GIB)
    bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MiB", bytes.toDouble() / (1024 * 1024))
    bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KiB", bytes.toDouble() / 1024)
    else -> "$bytes B"
}
