package com.tanuki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** 空态、加载、错误三态的统一种类。 */
enum class TanukiStateKind { LOADING, EMPTY, ERROR }

/** 三态文案；actionLabel 为空表示该状态不提供操作。 */
data class TanukiStateCopy(
    val title: String,
    val detail: String,
    val actionLabel: String?,
)

/**
 * 三态中文文案。
 *
 * 错误与空态默认给出可执行动作；加载态只说明等待，不伪造进度。
 * 页面可以覆盖主题（例如“会话”“审批”），但动作文案仍由这里兜底。
 */
object TanukiStateText {
    fun of(
        kind: TanukiStateKind,
        subject: String = "内容",
        detail: String? = null,
        actionLabel: String? = null,
    ): TanukiStateCopy {
        val cleanSubject = subject.trim().ifEmpty { "内容" }
        val cleanDetail = detail?.trim()?.takeIf { it.isNotEmpty() }
        val cleanAction = actionLabel?.trim()?.takeIf { it.isNotEmpty() }
        return when (kind) {
            TanukiStateKind.LOADING -> TanukiStateCopy(
                title = "正在加载$cleanSubject",
                detail = cleanDetail ?: "请稍候，加载完成后会自动显示。",
                actionLabel = null,
            )
            TanukiStateKind.EMPTY -> TanukiStateCopy(
                title = "暂无$cleanSubject",
                detail = cleanDetail ?: "当前没有可显示的$cleanSubject；可以刷新或稍后再试。",
                actionLabel = cleanAction ?: "刷新",
            )
            TanukiStateKind.ERROR -> TanukiStateCopy(
                title = "${cleanSubject}加载失败",
                detail = cleanDetail ?: "请检查网络或 DSH 服务是否运行，然后重试。",
                actionLabel = cleanAction ?: "重试",
            )
        }
    }
}

/**
 * 统一三态组件。
 *
 * [onAction] 为空时不显示假按钮；错误态使用 errorContainer 保证对比度，
 * 加载态带礼貌播报，屏幕阅读器不会静默等待。
 *
 * [title] 用于页面已经有明确标题的场景（例如“DSH 未运行”“读取官方会话失败”），
 * 传空时仍按 [TanukiStateText] 生成“暂无 X / 正在加载 X”。[secondaryActionLabel]
 * 提供第二种离开方式（例如“刷新列表”），不提供时不会出现空按钮。
 *
 * [icon] 用于空态建立视觉锚点；[actionFilled] 让主操作在空态也能成为实心按钮；
 * [secondaryAsButton] 把第二动作渲染成并排的次级按钮；[content] 承载进度行等
 * 需要与三态保持同一容器风格的补充内容。
 */
@Composable
fun TanukiStateView(
    kind: TanukiStateKind,
    modifier: Modifier = Modifier,
    subject: String = "内容",
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    title: String? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    icon: ImageVector? = null,
    actionFilled: Boolean = false,
    secondaryAsButton: Boolean = false,
    secondaryActionEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val copy = TanukiStateText.of(kind, subject, detail, actionLabel)
        .let { if (title.isNullOrBlank()) it else it.copy(title = title) }
    val background = if (kind == TanukiStateKind.ERROR) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.surfaceContainerLow
    val contentColor = if (kind == TanukiStateKind.ERROR) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // 只播报状态变化；标题与说明由 Text 自行朗读，避免重复。
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = background,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (kind == TanukiStateKind.LOADING) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = copy.title },
                    strokeWidth = 3.dp,
                )
            } else if (icon != null) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Text(
                text = copy.title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = copy.detail,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            if (secondaryAsButton && secondaryActionLabel != null && onSecondaryAction != null) {
                // 两个动作可能都是真实主路径（例如导入 / 新建文件夹），窄屏或大字体时自动换行。
                TanukiResponsiveActionRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    if (kind != TanukiStateKind.LOADING && onAction != null) {
                        TanukiActionButton(
                            label = copy.actionLabel ?: "重试",
                            onClick = onAction,
                            outlined = !actionFilled,
                            contentDescription = copy.actionLabel ?: "重试",
                        )
                    }
                    TanukiActionButton(
                        label = secondaryActionLabel,
                        onClick = onSecondaryAction,
                        outlined = true,
                        enabled = secondaryActionEnabled,
                        contentDescription = secondaryActionLabel,
                    )
                }
            } else {
                if (kind != TanukiStateKind.LOADING && onAction != null) {
                    TanukiActionButton(
                        label = copy.actionLabel ?: "重试",
                        onClick = onAction,
                        outlined = kind == TanukiStateKind.EMPTY && !actionFilled,
                        contentDescription = copy.actionLabel ?: "重试",
                    )
                }
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    TanukiTextAction(label = secondaryActionLabel, onClick = onSecondaryAction)
                }
            }
            content()
        }
    }
}
