package com.tanuki.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tanuki.ui.design.TanukiSpace

/** 查询由外层保存，返回结果页时保留原词；这里只发导航目标，不调用设置动作。 */
@Composable
internal fun SettingsSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (SettingsTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val results = remember(query) { SettingsCatalog.search(query) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { onQueryChange(it.take(SettingsCatalog.MAX_QUERY_LENGTH)) },
            label = { Text("搜索设置") },
            placeholder = { Text("主题、通知、备份…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) TextButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "清除设置搜索" },
                ) { Text("清除") }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            query.isBlank() -> Text(
                "输入设置名称或用途，例如“夜间”“自动启动”。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            results.isEmpty() -> Column(
                Modifier.padding(vertical = TanukiSpace.MD_DP.dp).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp),
            ) {
                Text("没有找到对应设置", style = MaterialTheme.typography.titleMedium)
                Text("试试更短的词，或清除搜索后按分类查找。", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                Text("${results.size} 项结果", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                results.forEach { target -> key(target) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .clickable(role = Role.Button) {
                                keyboard?.hide()
                                focus.clearFocus()
                                onSelect(target)
                            }.padding(horizontal = TanukiSpace.SM_DP.dp, vertical = TanukiSpace.MD_DP.dp),
                        verticalArrangement = Arrangement.spacedBy(TanukiSpace.XS_DP.dp),
                    ) {
                        Text(target.title, style = MaterialTheme.typography.titleMedium)
                        Text(target.path, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                } }
            }
        }
    }
}

/** 定位已展开的目标；折叠区由外层按白名单目标展开，内部动作仍需用户点击。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingsSearchAnchor(
    target: SettingsTarget,
    requestedTarget: SettingsTarget?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val requester = remember { BringIntoViewRequester() }
    var positioned by remember { mutableStateOf(false) }
    val highlighted = target == requestedTarget
    LaunchedEffect(highlighted, positioned) {
        if (highlighted && positioned) {
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
    Column(
        modifier.fillMaxWidth().bringIntoViewRequester(requester)
            .onGloballyPositioned { positioned = true }
            .then(if (highlighted) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                .padding(TanukiSpace.SM_DP.dp) else Modifier),
        verticalArrangement = Arrangement.spacedBy(TanukiSpace.XS_DP.dp),
    ) {
        if (highlighted) Text("已定位：${target.title}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        content()
    }
}
