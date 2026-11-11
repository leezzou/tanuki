package com.tanuki.ui

import com.tanuki.ui.design.TanukiDesign
import androidx.compose.ui.graphics.Color

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.TextButton
import com.tanuki.ui.design.TanukiOutlinedButton as OutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.tanuki.ui.design.TanukiButton as Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanuki.web.DshQuestion
import com.tanuki.web.DshQuestionAnswers
import com.tanuki.web.DshQuestionDraft
import com.tanuki.web.DshQuestionKind
import com.tanuki.web.DshQuestionRequest

/**
 * 用户提问卡：单选/多选/文本与本地草稿独立呈现，不复用普通消息气泡。
 *
 * 官方回答 RPC 尚未在本切片确认，因此 [onSubmit] 为 null 时只展示与本地选择，
 * 明确标注“回答入口待协议确认”，不伪造提交成功。
 */
@Composable
fun DshQuestionCard(
    request: DshQuestionRequest,
    modifier: Modifier = Modifier,
    submitting: Boolean = false,
    error: String? = null,
    onSubmit: ((DshQuestionAnswers) -> Unit)? = null,
    onSkip: (() -> Unit)? = null
) {
    var draft by remember(request.sessionId, request.eventId, request.questions) { mutableStateOf(DshQuestionDraft(request.questions)) }
    Surface(
        color = Color(TanukiDesign.tokens.colors.surface),
        contentColor = Color(TanukiDesign.tokens.colors.textPrimary),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(TanukiDesign.tokens.colors.outline)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "? 有个问题想问你",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (onSubmit == null) "本地选择 · 回答接口待确认" else "请选择后提交",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                StatusPill(if (onSubmit == null) "待协议确认" else "待回答", onSubmit == null)
            }
            request.questions.forEach { question ->
                QuestionBlock(
                    question = question,
                    draft = draft,
                    enabled = !submitting,
                    onToggle = { optionId -> draft = draft.toggle(question.id, optionId) },
                    onCustom = { text -> draft = draft.setCustom(question.id, text) }
                )
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = Color(TanukiDesign.tokens.colors.danger))
            }
            if (onSubmit == null) {
                Text(
                    "回答入口待协议确认：当前只展示提问与本地选择，不会发送官方回答。",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onSkip?.invoke() }, enabled = onSkip != null && !submitting,
                    modifier = Modifier.weight(1f)) { Text("不回答") }
                Button(enabled = onSubmit != null && draft.canSubmit() && !submitting,
                    onClick = { onSubmit?.invoke(draft.answers()) }, modifier = Modifier.weight(1f)) {
                    Text(if (submitting) "提交中…" else "提交")
                }
            }
        }
    }
}

@Composable
private fun QuestionBlock(
    question: DshQuestion,
    draft: DshQuestionDraft,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    onCustom: (String) -> Unit
) {
    var customExpanded by remember(question.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                question.text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (question.required) {
                Text(" *", style = MaterialTheme.typography.labelMedium,
                    color = Color(TanukiDesign.tokens.colors.danger))
            }
        }
        Surface(shape = RoundedCornerShape(8.dp), color = Color(TanukiDesign.tokens.colors.canvas)) {
            Text(question.kind.label + if (question.kind == DshQuestionKind.MULTI) " · 可多选" else " · 本次提问",
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
        }
        question.options.forEach { option ->
            val optionModifier = if (question.kind == DshQuestionKind.MULTI) {
                Modifier.toggleable(value = draft.selected(question.id, option.id), enabled = enabled,
                    role = Role.Checkbox, onValueChange = { onToggle(option.id) })
            } else {
                Modifier.selectable(selected = draft.selected(question.id, option.id), enabled = enabled,
                    role = Role.RadioButton, onClick = { onToggle(option.id) })
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).then(optionModifier), verticalAlignment = Alignment.CenterVertically) {
                when (question.kind) {
                    DshQuestionKind.MULTI -> Checkbox(
                        checked = draft.selected(question.id, option.id),
                        onCheckedChange = null,
                        enabled = enabled
                    )
                    else -> RadioButton(
                        selected = draft.selected(question.id, option.id),
                        onClick = null,
                        enabled = enabled
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(option.label, style = MaterialTheme.typography.bodySmall)
                    option.description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (question.allowCustom && question.kind != DshQuestionKind.TEXT) {
            TextButton(enabled = enabled, onClick = { customExpanded = !customExpanded }) { Text("+ 其他回答") }
        }
        if (question.kind == DshQuestionKind.TEXT || (question.allowCustom && customExpanded)) {
            OutlinedTextField(
                value = draft.customFor(question.id),
                onValueChange = onCustom,
                enabled = enabled,
                singleLine = false,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (question.required) "其他说明（必填）" else "其他说明（可选）") }
            )
        }
    }
}
