package com.tanuki.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** 仅覆盖 Material 按钮配色；尺寸、交互、内容和调用方的显式语义色保持原样。 */
@Composable
internal fun TanukiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(
        containerColor = Color(TanukiDesign.tokens.colors.primaryFill),
        contentColor = Color(TanukiDesign.tokens.colors.onPrimary),
        disabledContainerColor = Color(TanukiDesign.tokens.colors.surfaceMuted),
        disabledContentColor = Color(TanukiDesign.tokens.colors.textDisabled),
    ),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tokens = TanukiDesign.tokens.colors
    // 仅主操作使用按下色；调用方显式的危险/其他语义配色保持其含义。
    val displayedColors = if (enabled && pressed && colors.containerColor == Color(tokens.primaryFill) &&
        colors.contentColor == Color(tokens.onPrimary)
    ) colors.copy(containerColor = Color(tokens.primaryPressed)) else colors
    androidx.compose.material3.Button(
        onClick, modifier, enabled, shape, displayedColors, elevation, border, contentPadding, interaction, content,
    )
}

/** 次按钮使用中性表面与灰描边，避免普通次操作铺满强调色。 */
@Composable
internal fun TanukiOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(
        containerColor = Color(TanukiDesign.tokens.colors.surface),
        contentColor = Color(TanukiDesign.tokens.colors.textPrimary),
        disabledContainerColor = Color(TanukiDesign.tokens.colors.surfaceMuted),
        disabledContentColor = Color(TanukiDesign.tokens.colors.textDisabled),
    ),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = BorderStroke(1.dp, Color(TanukiDesign.tokens.colors.outline)),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.OutlinedButton(
    onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content,
)

/** 保留 tonal 调用方的形状与行为，颜色按次按钮规范收敛。 */
@Composable
internal fun TanukiTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.filledTonalShape,
    colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = Color(TanukiDesign.tokens.colors.surface),
        contentColor = Color(TanukiDesign.tokens.colors.textPrimary),
        disabledContainerColor = Color(TanukiDesign.tokens.colors.surfaceMuted),
        disabledContentColor = Color(TanukiDesign.tokens.colors.textDisabled),
    ),
    elevation: ButtonElevation? = ButtonDefaults.filledTonalButtonElevation(),
    border: BorderStroke? = BorderStroke(1.dp, Color(TanukiDesign.tokens.colors.outline)),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.FilledTonalButton(
    onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content,
)
