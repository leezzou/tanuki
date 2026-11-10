package com.tanuki.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tanuki.web.DshPlan
import com.tanuki.web.DshPlanReviewState
import com.tanuki.web.DshPlanStep
import com.tanuki.web.DshPlanStepStatus

/**
 * 计划审阅/执行进度卡片。
 *
 * 计划是一段有顺序的步骤状态，不使用普通消息气泡；审阅按钮只在真实“待审阅”状态下出现。
 * 组件不自己批准或修改计划，调用方负责把回调接到官方事件。
 */
@Composable
fun DshPlanCard(
    plan: DshPlan,
    modifier: Modifier = Modifier,
    submitting: Boolean = false,
    onApprove: (() -> Unit)? = null,
    onRequestChanges: (() -> Unit)? = null
) {
    val awaiting = plan.awaitingReview && onApprove != null
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (awaiting) "计划审阅" else "执行计划",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        plan.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusPill(
                    plan.statusLabel,
                    awaiting || plan.currentStep != null || plan.completed == plan.total
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "进度 ${plan.completed}/${plan.total}",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.weight(1f))
                    plan.currentStep?.let { step ->
                        Text(
                            "当前：${step.title}",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { plan.progress },
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "计划进度 ${plan.completed}/${plan.total}"
                    }
                )
            }
            plan.steps.forEach { step -> PlanStepRow(step) }
            if (plan.truncated) {
                Text(
                    "仅显示前 ${plan.steps.size} 个步骤；完整计划请到官方界面查看。",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (awaiting) {
                Text(
                    "同意执行会按当前计划继续；要求修改不会提交批准。",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !submitting && onRequestChanges != null,
                        onClick = { onRequestChanges?.invoke() }
                    ) { Text("要求修改") }
                    Button(
                        enabled = !submitting,
                        onClick = { onApprove.invoke() }
                    ) { Text(if (submitting) "提交中…" else "同意执行") }
                }
            } else if (plan.review == DshPlanReviewState.AWAITING) {
                Text(
                    "计划待审阅；当前版本尚未接入审阅操作，请到官方界面处理。",
                    style = MaterialTheme.typography.labelSmall
                )
            } else if (plan.review == DshPlanReviewState.APPROVED) {
                Text("已同意执行；后续步骤状态会随官方事件更新。",
                    style = MaterialTheme.typography.labelSmall)
            } else if (plan.review == DshPlanReviewState.CHANGES_REQUESTED) {
                Text("已要求修改；计划不会按当前版本执行。",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PlanStepRow(step: DshPlanStep) {
    val current = step.status == DshPlanStepStatus.IN_PROGRESS
    Surface(
        color = if (current) {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlanStepMark(step.status)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${step.index + 1}. ${step.title}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    textDecoration = if (step.status == DshPlanStepStatus.COMPLETED) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    }
                )
                step.detail?.let { detail ->
                    Text(
                        detail,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(step.status.label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PlanStepMark(status: DshPlanStepStatus) {
    when (status) {
        DshPlanStepStatus.IN_PROGRESS -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        else -> {
            val color = when (status) {
                DshPlanStepStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                DshPlanStepStatus.BLOCKED -> MaterialTheme.colorScheme.error
                DshPlanStepStatus.SKIPPED -> MaterialTheme.colorScheme.onTertiaryContainer
                else -> MaterialTheme.colorScheme.onTertiaryContainer
            }
            Text(
                when (status) {
                    DshPlanStepStatus.COMPLETED -> "✓"
                    DshPlanStepStatus.BLOCKED -> "!"
                    DshPlanStepStatus.SKIPPED -> "–"
                    else -> "○"
                },
                Modifier.width(16.dp),
                color = color,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
