package com.tanuki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tanuki.ui.design.TanukiSpace

@Composable
internal fun AppearanceSettingsPage(
    onOpenSection: (String) -> Unit,
    requestedTarget: SettingsTarget? = null,
) {
    val store = rememberAppearancePreferenceStore()
    val state by store.state.collectAsState()
    AppearanceSettingsContent(state, store::select, store::reload, onOpenSection, requestedTarget)
}

/** 状态与持久层分离，设备交互回归可使用合成状态而不改用户偏好。 */
@Composable
internal fun AppearanceSettingsContent(
    state: AppearancePreferenceState,
    onModeSelected: (AppearanceMode) -> Unit,
    onRetryRead: () -> Unit,
    onOpenSection: (String) -> Unit,
    requestedTarget: SettingsTarget? = null,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TanukiSpace.XL_DP.dp)) {
        SettingsSearchAnchor(SettingsTarget.THEME, requestedTarget) {
            Text("主题模式", style = MaterialTheme.typography.titleMedium)
            Text("选择应用内界面的明暗，保存后立即生效。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup()) {
                AppearanceMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .selectable(
                                selected = state.mode == mode,
                                enabled = !state.loading && !state.saving && state.error != AppearanceSaveError.LOAD,
                                role = Role.RadioButton,
                                onClick = { onModeSelected(mode) },
                            ).padding(vertical = TanukiSpace.SM_DP.dp),
                        horizontalArrangement = Arrangement.spacedBy(TanukiSpace.MD_DP.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.mode == mode, onClick = null,
                            enabled = !state.loading && !state.saving && state.error != AppearanceSaveError.LOAD)
                        Column(Modifier.weight(1f)) {
                            Text(mode.title, style = MaterialTheme.typography.bodyLarge)
                            if (mode == AppearanceMode.SYSTEM) Text("随系统的深色模式自动调整", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (state.loading || state.saving) {
                Row(horizontalArrangement = Arrangement.spacedBy(TanukiSpace.MD_DP.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(if (state.loading) "正在读取外观设置…" else "正在保存外观设置…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            when (state.error) {
                AppearanceSaveError.LOAD -> {
                    Text("未能读取已保存设置，保留当前主题。请重试后再更改。", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onRetryRead, modifier = Modifier.heightIn(min = 48.dp)) { Text("重新读取") }
                }
                AppearanceSaveError.SAVE -> Text("未能保存，仍使用原来的主题。请重新选择以重试。",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                null -> Unit
            }
            Text("网页内容与系统浮窗使用各自的主题设置。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsSearchAnchor(SettingsTarget.LANGUAGE, requestedTarget) {
            Text("界面语言", style = MaterialTheme.typography.titleMedium)
            Text("简体中文", style = MaterialTheme.typography.bodyLarge)
            Text("应用内界面使用简体中文。网页语言可在网页界面调整。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsSearchAnchor(SettingsTarget.FONT_SIZE, requestedTarget) {
            Text("字体大小", style = MaterialTheme.typography.titleMedium)
            Text("跟随系统", style = MaterialTheme.typography.bodyLarge)
            Text("在手机的系统显示设置中调整字号，应用内文字会随之变化。", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column {
            TextButton(onClick = { onOpenSection("通知与后台") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("通知与后台") }
            TextButton(onClick = { onOpenSection("存储与空间") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("存储与空间") }
        }
    }
}
