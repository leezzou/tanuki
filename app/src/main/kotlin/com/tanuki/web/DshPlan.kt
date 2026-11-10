package com.tanuki.web

import org.json.JSONArray
import org.json.JSONObject

/** 计划步骤状态；只用于原生展示，不改变官方计划或执行结果。 */
enum class DshPlanStepStatus(val label: String) {
    PENDING("待开始"),
    IN_PROGRESS("进行中"),
    COMPLETED("已完成"),
    BLOCKED("受阻"),
    SKIPPED("已跳过")
}

/** 计划审阅状态；未知/未提供时保持 [NONE]，不伪造“已批准”。 */
enum class DshPlanReviewState(val label: String) {
    NONE("执行计划"),
    AWAITING("待审阅"),
    APPROVED("已同意执行"),
    CHANGES_REQUESTED("已要求修改")
}

data class DshPlanStep(
    val index: Int,
    val title: String,
    val detail: String?,
    val status: DshPlanStepStatus
)

/**
 * 原生计划卡片的数据投影。
 *
 * 计划来自官方计划事件或计划工具调用的参数，是一段有顺序的步骤列表；
 * 原生层不重新规划、不自动批准，也不把计划正文当普通聊天消息渲染。
 */
data class DshPlan(
    val id: String,
    val seq: Int,
    val title: String,
    val steps: List<DshPlanStep>,
    val review: DshPlanReviewState = DshPlanReviewState.NONE,
    val sourceTool: String? = null,
    val truncated: Boolean = false,
    /** 原始步骤总数；展示层只保留前 MAX_STEPS 个步骤。 */
    val totalSteps: Int = steps.size,
    /** 原始步骤中已完成的数量；不能只用展示列表推导。 */
    val completedSteps: Int = steps.count { it.status == DshPlanStepStatus.COMPLETED },
    /** 进行中步骤标题；可能来自被截断的步骤。 */
    val currentStepTitle: String? = null,
    /** 无法取得完整进度统计（超长/损坏步骤）时为 true，界面显示未知而不是完成。 */
    val progressUnknown: Boolean = false
) {
    val total: Int get() = totalSteps
    val completed: Int get() = completedSteps
    val progress: Float get() =
        if (progressUnknown || total <= 0) 0f else completed.toFloat() / total
    val currentStep: DshPlanStep?
        get() = steps.firstOrNull { it.status == DshPlanStepStatus.IN_PROGRESS }
    val awaitingReview: Boolean get() = review == DshPlanReviewState.AWAITING

    /** 卡片状态：审阅状态优先，其次按完整统计推导执行进度，不虚构“已完成”。 */
    val statusLabel: String
        get() = when {
            review != DshPlanReviewState.NONE -> review.label
            progressUnknown -> "进度未知"
            total == 0 -> "无步骤"
            completed == total -> "已完成"
            completed == 0 -> "未开始"
            else -> "执行中"
        }
}

/**
 * 一次事件解析的结果。
 *
 * [planCallIds] 是计划工具调用的 callId；接线方应把这些 callId 从普通工具时间线中剔除，
 * 只渲染 [latest] 计划卡片，避免“计划既当工具卡又当计划卡”重复出现。
 */
data class DshPlanExtraction(
    val plans: List<DshPlan>,
    val planCallIds: Set<String>
) {
    val latest: DshPlan? get() = plans.maxByOrNull { it.seq }
}

/**
 * 官方计划/待办事件与计划工具调用的保守解析。
 *
 * 真实字段以合成回归为准；无法识别时返回 null，不猜状态、不把任意工具参数当计划。
 * 最多读取 20 个步骤和有限文本，忽略其他字段，不保留原始参数对象。
 */
object DshPlanParser {
    const val MAX_STEPS = 20
    const val MAX_SCAN_STEPS = 500
    const val MAX_STEP_CHARS = 240
    const val MAX_DETAIL_CHARS = 600

    private val PLAN_EVENT_TYPES = setOf(
        "plan/update", "plan/review", "plan", "todo/update", "todos/update"
    )
    private val PLAN_TOOLS = setOf(
        "update_plan", "plan", "set_plan", "plan_update",
        "todo_write", "write_todo", "todowrite", "update_todos"
    )

    fun isPlanTool(toolName: String?): Boolean {
        val name = toolName?.trim()?.lowercase().orEmpty()
        return name.isNotEmpty() && name in PLAN_TOOLS
    }

    /** 从一条 `tool/call` 合成计划；非计划工具或无法识别返回 null。 */
    fun fromToolCall(seq: Int, callId: String, toolName: String, arguments: String?): DshPlan? {
        if (!isPlanTool(toolName)) return null
        val raw = arguments?.trim().orEmpty()
        if (raw.isEmpty() || raw.length > 65_536) return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        return fromJson(
            seq = seq,
            id = callId.take(128).ifBlank { "plan-$seq" },
            json = json,
            sourceTool = toolName.take(128)
        )
    }

    /** 从一条计划事件合成；事件类型不在白名单时返回 null。 */
    fun fromEvent(event: JSONObject): DshPlan? {
        val type = event.optString("type")
        if (type !in PLAN_EVENT_TYPES) return null
        val data = event.optJSONObject("data") ?: return null
        val id = event.optString("eventId").trim().take(128)
            .ifBlank { "plan-${event.optInt("seq", 0)}" }
        val fromReviewEvent = type == "plan/review"
        val plan = fromJson(
            seq = event.optInt("seq", 0),
            id = id,
            json = data,
            sourceTool = null,
            reviewByDefault = fromReviewEvent
        ) ?: return null
        return plan
    }

    /**
     * 从计划审阅 waterfall 的 request 负载合成计划；默认标记为“待审阅”。
     *
     * 真实字段未在 PRoot DSH 验证；接线方拿到 `user-questions/request` 或计划审阅事件后
     * 可先调用本方法，解析失败时回退官方界面，不猜计划内容。
     */
    fun fromReviewPayload(seq: Int, id: String, payload: JSONObject): DshPlan? =
        fromJson(
            seq = seq,
            id = id.take(128).ifBlank { "plan-review-$seq" },
            json = payload,
            sourceTool = null,
            reviewByDefault = true
        )

    /**
     * 读取历史页/实时页记录中的计划与计划工具 callId。
     *
     * 同一 eventId/id 的后到更新替换前一条；计划工具调用每次生成一条，接线方使用
     * [DshPlanExtraction.latest] 渲染最新计划，并用 [DshPlanExtraction.planCallIds]
     * 排除普通工具卡。
     */
    fun extract(records: JSONArray): DshPlanExtraction {
        val byId = LinkedHashMap<String, DshPlan>()
        val callIds = LinkedHashSet<String>()
        for (index in 0 until records.length()) {
            val event = records.optJSONObject(index)?.optJSONObject("event") ?: continue
            when (event.optString("type")) {
                "tool/call" -> {
                    val data = event.optJSONObject("data") ?: continue
                    val callId = data.optString("callId").trim()
                    if (!DshApprovalProtocol.safeId(callId, 128)) continue
                    val name = data.optString("name").trim()
                    val plan = fromToolCall(
                        seq = event.optInt("seq", 0),
                        callId = callId,
                        toolName = name,
                        arguments = data.optString("arguments").takeIf { it.isNotBlank() }
                    ) ?: continue
                    callIds += callId
                    byId[plan.id] = plan
                }
                in PLAN_EVENT_TYPES -> {
                    fromEvent(event)?.let { byId[it.id] = it }
                }
            }
        }
        return DshPlanExtraction(byId.values.toList(), callIds)
    }

    private fun fromJson(
        seq: Int,
        id: String,
        json: JSONObject,
        sourceTool: String?,
        reviewByDefault: Boolean = false
    ): DshPlan? {
        val planJson = json.optJSONObject("plan") ?: json
        val rawSteps = when {
            planJson.optJSONArray("steps") != null -> planJson.optJSONArray("steps")
            planJson.optJSONArray("todos") != null -> planJson.optJSONArray("todos")
            planJson.optJSONArray("items") != null -> planJson.optJSONArray("items")
            planJson.optJSONArray("tasks") != null -> planJson.optJSONArray("tasks")
            planJson.optJSONArray("plan") != null -> planJson.optJSONArray("plan")
            else -> return null
        }
        val stats = stepsOf(rawSteps)
        if (stats.display.isEmpty()) return null
        val title = firstText(
            planJson, "title", "name", "summary", "goal", "explanation"
        ) ?: "执行计划"
        // 先在外层、再在内层查找显式审阅状态；两层都没有时才应用“待审阅”默认值。
        val outerReview = reviewOf(json, reviewByDefault = false)
        val innerReview = reviewOf(planJson, reviewByDefault = false)
        val review = when {
            outerReview != DshPlanReviewState.NONE -> outerReview
            innerReview != DshPlanReviewState.NONE -> innerReview
            reviewByDefault -> DshPlanReviewState.AWAITING
            else -> DshPlanReviewState.NONE
        }
        return DshPlan(
            id = id,
            seq = seq,
            title = title,
            steps = stats.display,
            review = review,
            sourceTool = sourceTool,
            truncated = rawSteps.length() > MAX_STEPS,
            totalSteps = stats.total,
            completedSteps = stats.completed,
            currentStepTitle = stats.currentTitle,
            progressUnknown = stats.unknown
        )
    }

    /**
     * 展示只保留前 [MAX_STEPS] 项，但统计遍历整份计划；超过扫描上限或遇到损坏项时
     * 标记进度未知，避免把“只看到前 20 步”误报成整份计划完成。
     */
    private fun stepsOf(array: JSONArray): PlanSteps {
        val display = ArrayList<DshPlanStep>(minOf(array.length(), MAX_STEPS))
        val total = array.length()
        val scan = minOf(total, MAX_SCAN_STEPS)
        var completed = 0
        var currentTitle: String? = null
        var unknown = total > MAX_SCAN_STEPS
        for (index in 0 until scan) {
            val raw = array.opt(index)
            val step = when (raw) {
                is JSONObject -> stepOf(index, raw)
                is String -> raw.takeIf { it.isNotBlank() }?.let {
                    DshPlanStep(index, bounded(it, MAX_STEP_CHARS), null, DshPlanStepStatus.PENDING)
                }
                else -> null
            }
            if (step == null) {
                unknown = true
                continue
            }
            if (step.status == DshPlanStepStatus.COMPLETED) completed++
            if (step.status == DshPlanStepStatus.IN_PROGRESS && currentTitle == null) {
                currentTitle = step.title
            }
            if (display.size < MAX_STEPS) display += step
        }
        return PlanSteps(display, total, completed, currentTitle, unknown)
    }

    private data class PlanSteps(
        val display: List<DshPlanStep>,
        val total: Int,
        val completed: Int,
        val currentTitle: String?,
        val unknown: Boolean
    )

    private fun stepOf(index: Int, json: JSONObject): DshPlanStep? {
        val title = firstText(json, "step", "title", "text", "content", "task")
            ?: firstText(json, "description")
            ?: return null
        val detail = firstText(json, "detail", "note", "description", max = MAX_DETAIL_CHARS)
            ?.takeIf { it != title }
        return DshPlanStep(
            index = index,
            title = title,
            detail = detail,
            status = statusOf(firstText(json, "status", "state"))
        )
    }

    private fun statusOf(value: String?): DshPlanStepStatus = when (value?.trim()?.lowercase()) {
        "in_progress", "in-progress", "running", "active", "doing", "current" -> DshPlanStepStatus.IN_PROGRESS
        "completed", "complete", "done", "success", "finished" -> DshPlanStepStatus.COMPLETED
        "blocked", "failed", "error" -> DshPlanStepStatus.BLOCKED
        "skipped", "cancelled", "canceled", "abandoned" -> DshPlanStepStatus.SKIPPED
        else -> DshPlanStepStatus.PENDING
    }

    private fun reviewOf(json: JSONObject, reviewByDefault: Boolean): DshPlanReviewState {
        if (json.optBoolean("requiresApproval", false) || json.optBoolean("awaitingReview", false)) {
            return DshPlanReviewState.AWAITING
        }
        val review = json.optJSONObject("review")
        val raw = firstText(json, "reviewStatus", "review_state")
            ?: review?.let { firstText(it, "status", "state", "decision") }
        return when (raw?.trim()?.lowercase()) {
            "approved", "accepted", "agreed" -> DshPlanReviewState.APPROVED
            "changes-requested", "changes_requested", "rejected", "revise",
            "needs-revision", "revision-requested" -> DshPlanReviewState.CHANGES_REQUESTED
            "awaiting-review", "pending-review", "review", "awaiting" -> DshPlanReviewState.AWAITING
            else -> if (reviewByDefault) DshPlanReviewState.AWAITING else DshPlanReviewState.NONE
        }
    }

    private fun firstText(json: JSONObject, vararg keys: String, max: Int = MAX_STEP_CHARS): String? {
        for (key in keys) {
            val value = json.optString(key).trim()
            if (value.isNotEmpty()) return bounded(value, max)
        }
        return null
    }

    private fun bounded(value: String, max: Int): String {
        val clean = value.filterNot { it == '\u0000' }.trim()
        return if (clean.length <= max) clean else clean.take(max) + "…"
    }
}
