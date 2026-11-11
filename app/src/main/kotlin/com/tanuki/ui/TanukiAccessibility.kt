package com.tanuki.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import com.tanuki.ui.design.TanukiButton as Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import com.tanuki.ui.design.TanukiOutlinedButton as OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 关键触控区下限；所有通用按钮和图标操作都必须满足。 */
val TanukiMinTouchTarget = 48.dp

/** 不小于 48dp 的触控区；大字体时按策略增大，可叠加在自定义控件上。 */
fun Modifier.tanukiMinimumTouchTarget(fontScale: Float = 1f): Modifier {
    val minimum = TanukiLayoutPolicy.minimumTouchTargetDp(fontScale).dp
    return sizeIn(minWidth = minimum, minHeight = minimum)
}

/**
 * 无障碍描述清洗：空白描述回退到可见文案，避免出现无名称按钮。
 */
fun tanukiActionDescription(description: String, fallback: String): String =
    description.trim().ifEmpty { fallback.trim().ifEmpty { "操作" } }

/**
 * 关键操作按钮。
 *
 * - 触控区不小于 48dp；
 * - 文字自动换行，1.3x/2x 字体下不限行，避免关键动作被省略号截断；
 * - 始终提供 contentDescription。
 */
@Composable
fun TanukiActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = label,
    outlined: Boolean = false,
) {
    val fontScale = LocalDensity.current.fontScale
    val description = tanukiActionDescription(contentDescription, label)
    val buttonModifier = modifier
        .tanukiMinimumTouchTarget(fontScale)
        .semantics { this.contentDescription = description }
    val padding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    val content: @Composable RowScope.() -> Unit = {
        Text(
            text = label,
            textAlign = TextAlign.Center,
            softWrap = true,
            maxLines = TanukiLayoutPolicy.keyButtonMaxLines(fontScale),
            overflow = TextOverflow.Visible,
            style = MaterialTheme.typography.labelLarge,
        )
    }
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            contentPadding = padding,
            content = content,
        )
    } else {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled,
            contentPadding = padding,
            content = content,
        )
    }
}

/** 文本型关键操作；同样保证 48dp 触控区与无障碍描述。 */
@Composable
fun TanukiTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = label,
) {
    val description = tanukiActionDescription(contentDescription, label)
    val fontScale = LocalDensity.current.fontScale
    TextButton(
        onClick = onClick,
        modifier = modifier.tanukiMinimumTouchTarget(fontScale).semantics { this.contentDescription = description },
        enabled = enabled,
    ) {
        Text(label, softWrap = true, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * 图标操作按钮。
 *
 * 图标自身不再重复声明描述，由按钮统一提供可点击语义；视觉尺寸随字体
 * 小幅增大，但触控区继续由 48dp 下限保证。
 */
@Composable
internal fun TanukiIconAction(
    glyph: TanukiGlyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
) {
    val description = tanukiActionDescription(contentDescription, "操作")
    val fontScale = LocalDensity.current.fontScale
    val visualSize = TanukiLayoutPolicy.iconVisualSizeDp(fontScale)
    IconButton(
        onClick = onClick,
        modifier = modifier.tanukiMinimumTouchTarget(fontScale).semantics { this.contentDescription = description },
        enabled = enabled,
    ) {
        TanukiIcon(glyph, description = null, modifier = Modifier.size(visualSize.dp), tint = tint)
    }
}
