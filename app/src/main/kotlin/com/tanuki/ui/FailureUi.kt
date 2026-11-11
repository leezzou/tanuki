package com.tanuki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanuki.runtime.AppFailure
import com.tanuki.runtime.FailureActionKind
import com.tanuki.ui.design.TanukiBottomSheet
import com.tanuki.ui.design.TanukiDesign
import com.tanuki.ui.design.TanukiPillButton
import com.tanuki.ui.design.TanukiPillButtonTone
import com.tanuki.ui.design.TanukiShapeTokens
import com.tanuki.ui.design.TanukiSpace

/**
 * 组件卡内嵌错误块：失败后就地显示原因与主行动，不依赖 toast 或顶部进度条。
 * 颜色、圆角、字体和按钮都读取 main 的设计令牌，避免错误卡与迁移后的页面风格分叉。
 */
@Composable
internal fun InlineFailureBlock(
    failure: AppFailure,
    onAction: (FailureActionKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = TanukiDesign.tokens
    Surface(
        shape = TanukiShapeTokens.card,
        color = Color(tokens.colors.dangerContainer),
        contentColor = Color(tokens.colors.onDangerContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(TanukiSpace.MD_DP.dp),
            verticalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp)
        ) {
            Text(failure.title, style = tokens.type.titleSmall)
            Text(failure.reason, style = tokens.type.bodyMedium)
            TanukiPillButton(
                text = failure.primaryAction.label,
                onClick = { onAction(failure.primaryAction.kind) },
                tone = TanukiPillButtonTone.Filled,
                modifier = Modifier.fillMaxWidth()
            )
            failure.secondaryAction?.let { secondary ->
                TanukiPillButton(
                    text = secondary.label,
                    onClick = { onAction(secondary.kind) },
                    tone = TanukiPillButtonTone.Outline,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // 技术原文默认折叠；用户需要反馈问题时再展开复制。
            FailureTechnicalDetails(failure.detail, failure.stableKey, failure.attempt)
        }
    }
}

/**
 * 全局错误条：任何 Tab 失败时停在顶部，点整条或「处理」打开详情；
 * 关闭只隐藏本次提示，组件卡和工作台里的错误仍保留。
 */
@Composable
internal fun GlobalFailureBar(
    failure: AppFailure,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = TanukiDesign.tokens
    Surface(
        onClick = onOpen,
        shape = TanukiShapeTokens.chrome,
        color = Color(tokens.colors.dangerContainer),
        contentColor = Color(tokens.colors.onDangerContainer),
        modifier = modifier.fillMaxWidth().padding(horizontal = TanukiSpace.MD_DP.dp, vertical = TanukiSpace.XS_DP.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = TanukiSpace.MD_DP.dp,
                end = TanukiSpace.XS_DP.dp,
                top = TanukiSpace.SM_DP.dp,
                bottom = TanukiSpace.SM_DP.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(TanukiSpace.XS_DP.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${failure.title}：${failure.reason}",
                    style = tokens.type.captionLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TanukiPillButton(
                text = "处理",
                onClick = onOpen,
                tone = TanukiPillButtonTone.Tonal,
            )
            TanukiPillButton(
                text = "关闭",
                onClick = onDismiss,
                tone = TanukiPillButtonTone.Ghost,
            )
        }
    }
}

/**
 * 错误详情：用 main 的底部弹层展示原因、技术原文与主/次行动；复制内容带时间。
 * 行动执行前先关闭弹层，避免和确认弹窗叠在一起。
 */
/** 新错误拥有独立折叠状态，展开旧错误不会让下一条异常自动暴露。 */
@Composable
internal fun FailureTechnicalDetails(detail: String, identity: Any = detail, attempt: Int = 0) {
    key(identity) {
        TanukiDetails("技术细节") {
            SelectableCopy(detail, error = true)
            if (attempt > 0) Text("已尝试 $attempt 次", style = TanukiDesign.tokens.type.captionSmall)
        }
    }
}

@Composable
internal fun FailureDetailSheet(
    failure: AppFailure?,
    versionName: String,
    onAction: (FailureActionKind) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var presentation by remember { mutableStateOf(TanukiOverlayState<AppFailure>()) }
    presentation = presentation.update(failure)
    val displayed = presentation.shown
    var copied by remember(displayed?.stableKey) { mutableStateOf(false) }
    TanukiBottomSheet(
        visible = failure != null,
        onDismissRequest = onDismiss,
        title = displayed?.title.orEmpty(),
        subtitle = displayed?.reason,
        onHidden = { presentation = presentation.hidden() },
    ) {
        displayed?.let { shown ->
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(TanukiSpace.SM_DP.dp)
            ) {
                FailureTechnicalDetails(shown.detail, shown.stableKey)
                TanukiPillButton(
                    text = if (copied) "已复制" else "复制错误信息",
                    onClick = {
                        if (failure != null) {
                            clipboard.setText(AnnotatedString(shown.copyText(versionName)))
                            copied = true
                        }
                    },
                    tone = TanukiPillButtonTone.Tonal,
                    modifier = Modifier.fillMaxWidth()
                )
                shown.secondaryAction?.let { secondary ->
                    TanukiPillButton(
                        text = secondary.label,
                        onClick = { if (failure != null) { onAction(secondary.kind); onDismiss() } },
                        tone = TanukiPillButtonTone.Outline,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TanukiPillButton(
                    text = shown.primaryAction.label,
                    onClick = { if (failure != null) { onAction(shown.primaryAction.kind); onDismiss() } },
                    tone = TanukiPillButtonTone.Filled,
                    modifier = Modifier.fillMaxWidth()
                )
                TanukiPillButton(
                    text = "关闭",
                    onClick = onDismiss,
                    tone = TanukiPillButtonTone.Ghost,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
