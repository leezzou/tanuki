package com.tanuki.web

import org.json.JSONArray
import org.json.JSONObject

/** 用户提问的输入类型；单选/多选/自由文本。 */
enum class DshQuestionKind(val label: String) {
    SINGLE("单选"),
    MULTI("多选"),
    TEXT("文本")
}

data class DshQuestionOption(
    val id: String,
    val label: String,
    val description: String? = null
)

data class DshQuestion(
    val id: String,
    val text: String,
    val kind: DshQuestionKind,
    val options: List<DshQuestionOption>,
    val allowCustom: Boolean = false,
    val required: Boolean = true
)

/** 官方 `user-questions/request` 中可展示的提问请求；只保留问题、选项和必填标记。 */
data class DshQuestionRequest(
    val eventId: String,
    val sessionId: String,
    val questions: List<DshQuestion>
)

data class DshQuestionAnswers(
    val selections: Map<String, List<String>>,
    val custom: Map<String, String>
)

/**
 * 用户提问的本地草稿。
 *
 * 不可变对象：每次选择返回新草稿，Compose 可可靠重组；单选覆盖、多选切换、文本单独保存。
 * 这里只负责 UI 选择，不发送官方回答；回答入口由页面按真实协议接线。
 */
data class DshQuestionDraft(
    val questions: List<DshQuestion>,
    val selections: Map<String, List<String>> = emptyMap(),
    val custom: Map<String, String> = emptyMap()
) {
    fun selected(questionId: String, optionId: String): Boolean =
        selections[questionId].orEmpty().contains(optionId)

    fun toggle(questionId: String, optionId: String): DshQuestionDraft {
        val question = questions.firstOrNull { it.id == questionId } ?: return this
        val current = selections[questionId].orEmpty()
        val next = when (question.kind) {
            DshQuestionKind.SINGLE, DshQuestionKind.TEXT -> listOf(optionId)
            DshQuestionKind.MULTI -> if (current.contains(optionId)) {
                current - optionId
            } else {
                current + optionId
            }
        }
        return copy(selections = selections + (questionId to next))
    }

    fun setCustom(questionId: String, text: String): DshQuestionDraft {
        if (questions.none { it.id == questionId }) return this
        val clean = text.trim().take(MAX_CUSTOM_CHARS)
        return copy(
            custom = if (clean.isBlank()) custom - questionId else custom + (questionId to clean)
        )
    }

    fun customFor(questionId: String): String = custom[questionId].orEmpty()

    /** 必填问题必须已有选项或非空文本；可选问题不阻塞提交。 */
    fun canSubmit(): Boolean = questions.all { question ->
        !question.required ||
            selections[question.id].orEmpty().isNotEmpty() ||
            custom[question.id].orEmpty().isNotBlank()
    }

    fun answers(): DshQuestionAnswers = DshQuestionAnswers(
        selections = selections.filterValues { it.isNotEmpty() },
        custom = custom.filterValues { it.isNotBlank() }
    )

    companion object {
        const val MAX_CUSTOM_CHARS = 2_000
    }
}

/**
 * `user-questions/request` 的保守解析。
 *
 * 兼容常见的 `questions[]`/`options[]` 形状；无法识别的字段丢弃，不把原始请求对象带进 UI。
 * 回答 RPC 未在本切片确认，因此本类只解析、不回答。
 */
object DshQuestionParser {
    const val MAX_QUESTIONS = 10
    const val MAX_OPTIONS = 20
    const val MAX_TEXT_CHARS = 600
    const val MAX_LABEL_CHARS = 160

    fun parse(eventId: String, sessionId: String, request: JSONObject?): DshQuestionRequest? {
        if (request == null) return null
        if (!DshApprovalProtocol.safeId(eventId, 128) || !DshApprovalProtocol.safeId(sessionId, 128)) {
            return null
        }
        val questions = request.optJSONArray("questions")
            ?: request.optJSONObject("questions")?.let { JSONArray().put(it) }
            ?: JSONArray().put(request)
        val parsed = ArrayList<DshQuestion>(minOf(questions.length(), MAX_QUESTIONS))
        for (index in 0 until minOf(questions.length(), MAX_QUESTIONS)) {
            val item = questions.optJSONObject(index) ?: continue
            questionOf(index, item)?.let(parsed::add)
        }
        if (parsed.isEmpty()) return null
        return DshQuestionRequest(eventId, sessionId, parsed)
    }

    private fun questionOf(index: Int, json: JSONObject): DshQuestion? {
        val text = firstText(json, "question", "text", "prompt", "title", "label")
            ?: return null
        val id = firstText(json, "id", "questionId", "key", max = 128)
            ?.takeIf { DshApprovalProtocol.safeId(it, 128) }
            ?: "q${index + 1}"
        val options = optionsOf(json)
        val kind = kindOf(json, options.isNotEmpty())
        val allowCustom = json.optBoolean("allowCustom", false) ||
            json.optBoolean("custom", false) ||
            json.optBoolean("allowFreeform", false) ||
            kind == DshQuestionKind.TEXT
        val required = if (json.has("required")) json.optBoolean("required", true) else true
        if (kind != DshQuestionKind.TEXT && options.isEmpty() && !allowCustom) return null
        return DshQuestion(
            id = id,
            text = text,
            kind = kind,
            options = options,
            allowCustom = allowCustom,
            required = required
        )
    }

    private fun optionsOf(json: JSONObject): List<DshQuestionOption> {
        val array = json.optJSONArray("options") ?: json.optJSONArray("choices") ?: return emptyList()
        val result = ArrayList<DshQuestionOption>(minOf(array.length(), MAX_OPTIONS))
        for (index in 0 until minOf(array.length(), MAX_OPTIONS)) {
            val raw = array.opt(index)
            val option = when (raw) {
                is String -> DshQuestionOption("o${index + 1}", normalize(raw), null)
                is JSONObject -> {
                    val label = firstText(raw, "label", "text", "title", "value")
                        ?: firstText(raw, "id")
                        ?: continue
                    val id = firstText(raw, "id", "value", "key", max = 128)
                        ?.takeIf { DshApprovalProtocol.safeId(it, 128) }
                        ?: "o${index + 1}"
                    DshQuestionOption(id, label, firstText(raw, "description", "detail", max = 300))
                }
                else -> null
            } ?: continue
            if (result.none { it.id == option.id }) result += option
        }
        return result
    }

    private fun kindOf(json: JSONObject, hasOptions: Boolean): DshQuestionKind {
        val type = json.optString("type").trim().lowercase()
        if (json.optBoolean("multiSelect", false) || json.optBoolean("multiple", false) ||
            json.optBoolean("multi", false) || type in MULTI_TYPES
        ) {
            return DshQuestionKind.MULTI
        }
        if (type in TEXT_TYPES) return DshQuestionKind.TEXT
        return if (hasOptions) DshQuestionKind.SINGLE else DshQuestionKind.TEXT
    }

    private fun firstText(json: JSONObject, vararg keys: String, max: Int = MAX_TEXT_CHARS): String? {
        for (key in keys) {
            val value = json.optString(key).trim()
            if (value.isNotEmpty()) return normalize(value).take(max)
        }
        return null
    }

    private fun normalize(value: String): String =
        value.filterNot { it == '\u0000' }.replace('\n', ' ').trim()

    private val MULTI_TYPES = setOf("multi", "multiple", "multi-select", "multiselect", "checkbox")
    private val TEXT_TYPES = setOf("text", "input", "freeform", "free-form")
}
