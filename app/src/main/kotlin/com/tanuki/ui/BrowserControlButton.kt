package com.tanuki.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/** 只订阅控制状态，网页进度/滚动和逐秒计时不重组整个工作区。 */
data class BrowserControlUiState(
    val aiControlled: Boolean = false,
    val enabled: Boolean = false,
    val description: String = "浏览器不可用",
    val requestPending: Boolean = false,
)

@Composable
internal fun BrowserControlButton(state: BrowserControlUiState, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    FilledTonalIconButton(onClick = onToggle, enabled = state.enabled,
        modifier = modifier.size(56.dp).shadow(4.dp, CircleShape).pointerInput(Unit) {
            // 底栏和侧栏共用：移动超过阈值只取消点击，不在抬手时误切控制权。
            detectDragGestures { change, _ -> change.consume() }
        }.semantics {
            contentDescription = "浏览器控制"
            stateDescription = state.description
            onClick(label = if (state.aiControlled) "撤销 AI 操作权限" else "交回 AI", action = null)
        },
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (state.aiControlled) colors.primary else colors.surfaceContainerHighest,
            contentColor = if (state.aiControlled) colors.onPrimary else colors.onSurface,
        )) {
        TanukiIconSwap(state.aiControlled, Modifier.size(26.dp)) { controlled ->
            Icon(if (controlled) Icons.Default.SmartToy else Icons.Default.Person, null, Modifier.size(26.dp))
        }
    }
}
