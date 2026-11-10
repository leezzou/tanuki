package com.tanuki.web

import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.UUID

/**
 * 原生界面可回传的官方审批结果。
 *
 * 官方 waterfall 只接受 `allowed-once`、`rejected`、`cancelled`、`unavailable`；
 * 原生界面只产生前两个，`cancelled` 由 Host 取消，`unavailable` 由 Host 在无回答者时
 * 自动产生。这里不新增协议值，也不把“记住选择”伪装成官方持久授权。
 */
enum class DshApprovalOutcome(val wire: String) {
    ALLOWED_ONCE("allowed-once"),
    REJECTED("rejected");

    companion object {
        fun fromWire(value: String?): DshApprovalOutcome? =
            entries.firstOrNull { it.wire == value }
    }
}

/** 官方 `approval/request` waterfall 中与界面有关的最小字段。 */
data class DshApprovalRequest(
    val eventId: String,
    val sessionId: String,
    val toolName: String,
    val callId: String? = null,
    val reason: String? = null,
    val displayReason: String? = null,
    /** 官方 request 中可选的来源说明（插件、服务或调用端）；只保留短文本。 */
    val source: String? = null,
    /** 官方 toolName 缺失/非法；界面只能拒绝，不能按记忆自动允许。 */
    val unknownTool: Boolean = false
)

/** `/api/remote.mux` `$events` 流中本切片关心的事件。 */
/** 审批通道的纯协议层：只做白名单字段校验、请求信封与回答信封，不做网络 I/O。 */
object DshApprovalProtocol {
    const val EVENTS_ENDPOINT = "\$events"
    const val RESULT_ENDPOINT = "\$events/result"
    const val MUX_PATH = "/api/remote.mux"

    /** 单条文本帧上限；官方审批帧很小，超过即视为异常连接。 */
    const val MAX_FRAME_BYTES = 1_048_576
    const val MAX_TOOL_NAME_CHARS = 128
    const val MAX_REASON_CHARS = 2_000
    const val MAX_SOURCE_CHARS = 120

    /**
     * 审批必须使用 `interactions=answer` 的 API Peer。
     *
     * 官方 gateway 的 `$events` 打开与 `$events/result` 回答都会调用
     * `requireInteractions()`，只接受 `answer`；只读列表继续使用 `none`，
     * 两者不能共用同一个 `/connect` 结果。
     */
    fun connectUrl(endpoint: DshEndpoint): String? = runCatching {
        if (!DshApiProtocol.validOrigin(endpoint.origin) ||
            !OriginPolicy.sameOrigin(endpoint.launchUrl, endpoint.origin)
        ) return null
        val uri = URI(endpoint.launchUrl)
        val query = uri.rawQuery ?: return null
        val parts = query.split('&').filter { it.isNotBlank() }
        val hasToken = parts.any { part ->
            part.substringBefore('=').equals("token", ignoreCase = true) &&
                part.substringAfter('=', "").isNotBlank()
        }
        if (!hasToken) return null
        val withoutInteractions = parts.filterNot {
            it.substringBefore('=').equals("interactions", ignoreCase = true)
        }
        "${endpoint.origin}/connect?${withoutInteractions.joinToString("&")}&interactions=answer"
    }.getOrNull()

    fun newStreamId(): String = UUID.randomUUID().toString()

    fun newRpcId(): String = UUID.randomUUID().toString()

    /** 打开 Gateway 内部 `$events` 逻辑流；payload 与官方客户端一致为空 args。 */
    fun openStreamBody(streamId: String): String = JSONObject()
        .put("type", "open")
        .put("streamId", streamId)
        .put("endpoint", EVENTS_ENDPOINT)
        .put("payload", JSONObject().put("args", JSONObject()))
        .toString()

    /**
     * 官方 `$events/result` 回答信封。
     *
     * 回答不是 WebSocket 上行帧，而是同一 Cookie 下的一元 RPC：
     * `POST /api/$events/result`，`payload.args.outcome.kind=result`。
     */
    fun answerBody(rpcId: String, clientId: String, eventId: String, outcome: DshApprovalOutcome): String =
        resultBody(
            rpcId, clientId, eventId,
            JSONObject().put("kind", "result").put("value", outcome.wire)
        )

    /** 不支持的合法 waterfall 回 next，让 Host 继续后续回答者/兜底；不产生审批卡片。 */
    fun answerNextBody(rpcId: String, clientId: String, eventId: String): String =
        resultBody(rpcId, clientId, eventId, JSONObject().put("kind", "next"))

    private fun resultBody(rpcId: String, clientId: String, eventId: String, outcome: JSONObject): String =
        JSONObject()
            .put("type", "client-request")
            .put("rpcId", rpcId)
            .put("method", RESULT_ENDPOINT)
            .put("payload", JSONObject().put("args", JSONObject()
                .put("clientId", clientId)
                .put("eventId", eventId)
                .put("outcome", outcome)))
            .toString()

    /**
     * 解析一条 WebSocket 文本帧。只接受外层 `item` 信封，并只读取
     * `ready`、`approval/request` waterfall 和 `cancel` 三类值。
     */
    fun parseStreamItem(raw: String?): DshEventFrame {
        if (raw.isNullOrBlank() || raw.length > MAX_FRAME_BYTES) return DshEventFrame.Ignored
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return DshEventFrame.Ignored
        if (root.optString("type") == "error") {
            val code = root.optJSONObject("error")?.optString("code").orEmpty()
            return DshEventFrame.StreamError(
                code.takeIf { it.matches(Regex("[A-Za-z0-9/_-]{1,80}")) } ?: "unknown"
            )
        }
        if (root.optString("type") != "item") return DshEventFrame.Ignored
        val value = root.optJSONObject("value") ?: return DshEventFrame.Ignored
        return parseValue(value)
    }

    fun parseValue(value: JSONObject): DshEventFrame = when (value.optString("type")) {
        "ready" -> {
            val clientId = value.optString("clientId")
            if (!safeId(clientId, 128)) DshEventFrame.Ignored else DshEventFrame.Ready(clientId)
        }
        "waterfall" -> parseWaterfall(value)
        "cancel" -> {
            val eventId = value.optString("eventId")
            if (!safeId(eventId, 128)) DshEventFrame.Ignored else DshEventFrame.Cancelled(eventId)
        }
        else -> DshEventFrame.Ignored
    }

    private fun parseWaterfall(value: JSONObject): DshEventFrame {
        if (value.optString("event") == "approval/request") return parseApproval(value)
        val eventId = value.optString("eventId")
        if (!safeId(eventId, 128)) return DshEventFrame.Ignored
        return DshEventFrame.UnsupportedWaterfall(eventId)
    }

    private fun parseApproval(value: JSONObject): DshEventFrame {
        if (value.optString("event") != "approval/request") return DshEventFrame.Ignored
        val eventId = value.optString("eventId")
        val sessionId = value.optString("agentId")
        if (!safeId(eventId, 128) || !safeId(sessionId, 128)) return DshEventFrame.Ignored
        val request = value.optJSONObject("request") ?: return DshEventFrame.Ignored
        val rawToolName = request.optString("toolName").trim()
        val knownTool = rawToolName.isNotBlank() && rawToolName.length <= MAX_TOOL_NAME_CHARS
        // 只读取白名单字段；request 里的其他内容不复制到原生层。
        val callId = request.optString("callId").takeIf { safeId(it, 128) }
        val reason = text(request, "reason")
        val displayReason = displayReason(request)
        return DshEventFrame.Approval(
            DshApprovalRequest(
                eventId = eventId,
                sessionId = sessionId,
                toolName = if (knownTool) rawToolName else "未知审批",
                callId = callId,
                reason = reason,
                displayReason = displayReason,
                source = sourceOf(request),
                unknownTool = !knownTool
            )
        )
    }

    /**
     * 官方审批请求可能带来源对象（插件/服务/调用端）或来源字符串。
     * 这里只取一个短标签，不复制对象里的其他字段，避免把凭据或内部路径带进界面。
     */
    private fun sourceOf(request: JSONObject): String? {
        if (!request.has("source") || request.isNull("source")) return null
        val raw = request.opt("source")
        val text = when (raw) {
            is JSONObject -> listOf("name", "label", "title", "kind", "id")
                .firstNotNullOfOrNull { key ->
                    raw.optString(key).trim().takeIf { it.isNotEmpty() }
                }
            is String -> raw.trim()
            else -> null
        } ?: return null
        val bounded = text.filterNot { it == '\u0000' }.trim()
        return bounded.takeIf { it.isNotEmpty() }?.let { it.take(MAX_SOURCE_CHARS) }
    }

    /** displayReason 是本地化对象（至少 en）；旧版本可能是字符串，两种都只取短文本。 */
    private fun displayReason(request: JSONObject): String? {
        if (!request.has("displayReason") || request.isNull("displayReason")) return null
        val raw = request.opt("displayReason")
        val text = when (raw) {
            is JSONObject -> raw.optString("zh").ifBlank { raw.optString("en") }
            is String -> raw
            else -> ""
        }.trim()
        return text.takeIf { it.isNotEmpty() }?.let { bounded(it, MAX_REASON_CHARS) }
    }

    private fun text(request: JSONObject, key: String): String? {
        if (!request.has(key) || request.isNull(key)) return null
        val value = request.optString(key).trim()
        return value.takeIf { it.isNotEmpty() }?.let { bounded(it, MAX_REASON_CHARS) }
    }

    private fun bounded(value: String, max: Int): String =
        if (value.length <= max) value else value.take(max) + "…"

    /** 官方 id 只允许可打印 ASCII；避免把控制字符带进日志或界面。 */
    fun safeId(value: String, maxLength: Int): Boolean =
        value.isNotBlank() && value.length <= maxLength && value.all { it.code in 0x21..0x7e }
}

/** 审批风险等级；只用于原生提示，不改变官方审批结果。 */
enum class DshRiskLevel(val label: String) {
    LOW("低"),
    MEDIUM("中"),
    HIGH("高"),
    UNKNOWN("未知")
}

data class DshRiskAssessment(val level: DshRiskLevel, val summary: String)

/**
 * 从官方工具名与原始参数推断风险提示。
 *
 * 这不是官方权限判定，也不替代官方审批；只是让用户在原生界面看到
 * “这条请求大概在做什么”。无法判断时明确显示未知，不假装安全。
 */
object DshApprovalRisk {
    fun assess(toolName: String, arguments: String?): DshRiskAssessment {
        val name = toolName.lowercase()
        val args = arguments.orEmpty()
        val normalized = args.replace(" ", "").lowercase()
        val shell = name in SHELL_TOOLS || name.contains("bash") || name.contains("terminal")
        val escalation = normalized.contains("danger-full-access") ||
            normalized.contains("sandbox_permissions") ||
            normalized.contains("sandboxpermissions") ||
            normalized.contains("--dangerously")
        val write = name in WRITE_TOOLS || name.contains("write") || name.contains("edit") ||
            name.contains("delete") || name.contains("rename") || name.contains("move")
        val network = name.contains("web") || name.contains("fetch") || name.contains("browser") ||
            name.contains("http")
        return when {
            escalation -> DshRiskAssessment(DshRiskLevel.HIGH, "请求提升沙箱或执行权限")
            shell -> DshRiskAssessment(DshRiskLevel.HIGH, "执行命令或进程操作")
            write -> DshRiskAssessment(DshRiskLevel.MEDIUM, "修改工作区文件")
            network -> DshRiskAssessment(DshRiskLevel.MEDIUM, "访问网络或浏览器")
            name in READ_TOOLS -> DshRiskAssessment(DshRiskLevel.LOW, "读取工作区内容")
            else -> DshRiskAssessment(DshRiskLevel.UNKNOWN, "无法从工具名判断影响范围")
        }
    }

    private val SHELL_TOOLS = setOf("bash", "shell", "terminal", "pwsh", "process", "exec", "run", "run_command")
    private val WRITE_TOOLS = setOf("write", "edit", "str_replace", "delete", "rename", "move", "mkdir", "copy")
    private val READ_TOOLS = setOf("read", "list", "glob", "grep", "search", "stat", "status")
}

/**
 * 审批卡片的一行参数摘要。
 *
 * 审批请求本身只带工具名与原因，参数由关联工具调用补齐；这里从原始参数中提取
 * 命令、路径、地址或查询等最影响判断的字段，让用户在展开完整 JSON 前就能核对。
 */
data class DshApprovalSummary(val headline: String, val field: String)

object DshApprovalPresentation {
    const val MAX_HEADLINE_CHARS = 180

    fun summarize(arguments: String?): DshApprovalSummary? {
        val raw = arguments?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val json = runCatching { JSONObject(raw) }.getOrNull()
        if (json == null) return DshApprovalSummary(bounded(raw), "参数")
        for ((field, keys) in FIELDS) {
            for (key in keys) {
                val value = json.optString(key).trim()
                if (value.isNotEmpty()) return DshApprovalSummary(bounded(value), field)
            }
        }
        // 没有常见字段时只列出前三个标量，避免把整段 JSON 当成摘要。
        val summary = buildList {
            val names = json.keys()
            while (names.hasNext() && size < 3) {
                val key = names.next()
                val value = json.opt(key)
                if (value is String || value is Number || value is Boolean) {
                    add("$key=${bounded(value.toString(), 60)}")
                }
            }
        }.joinToString(" · ")
        return summary.takeIf { it.isNotBlank() }?.let { DshApprovalSummary(bounded(it), "参数") }
    }

    private fun bounded(value: String, max: Int = MAX_HEADLINE_CHARS): String {
        val clean = value.filterNot { it == '\u0000' }.replace('\n', ' ').trim()
        return if (clean.length <= max) clean else clean.take(max) + "…"
    }

    private val FIELDS = listOf(
        "命令" to listOf("command", "cmd", "script", "commandLine"),
        "路径" to listOf("file_path", "filePath", "path", "file", "target"),
        "地址" to listOf("url", "uri", "endpoint"),
        "查询" to listOf("query", "pattern", "search"),
        "工作目录" to listOf("cwd", "workdir", "working_directory", "workingDirectory")
    )
}

/** 审批通道失败的统一中文文案；`retryable` 表示保留待审批项并允许用户重试。 */
data class DshApprovalFailure(val message: String, val retryable: Boolean = true)

object DshApprovalFailures {
    fun describe(error: Throwable): DshApprovalFailure {
        val fallback = when (error) {
            is DshApiException -> "官方审批接口返回 ${error.code}，请重试"
            is SocketTimeoutException -> "审批通道连接超时，请确认 DSH 仍在运行后重试"
            is ConnectException -> "无法连接 DSH 审批通道，请确认服务仍在运行后重试"
            is UnknownHostException -> "无法解析 DSH 服务地址，请重试"
            is IOException -> englishOrChinese(error.message)
            else -> englishOrChinese(error.message)
        }
        return DshApprovalFailure(fallback, retryable = true)
    }

    private fun englishOrChinese(message: String?): String {
        val text = message?.trim().orEmpty()
        if (text.isEmpty()) return "审批通道异常，请重试"
        // 已有中文原因（连接层主动生成）原样保留；纯英文底层异常不直接展示给用户。
        val chinese = text.any { it.code in 0x4e00..0x9fff }
        return if (chinese) text.take(200) else "审批通道连接失败，请重试"
    }
}

/** 一条“本次原生界面运行内记住”的审批选择。 */
data class DshRememberedChoice(
    val sessionId: String,
    val toolName: String,
    val risk: DshRiskLevel,
    val outcome: DshApprovalOutcome
) {
    /** 仅用于界面列表定位，不参与官方协议。 */
    val id: String get() = "$sessionId\u0000${toolName.lowercase()}\u0000${risk.name}"

    val outcomeLabel: String
        get() = if (outcome == DshApprovalOutcome.ALLOWED_ONCE) "允许一次" else "拒绝"
}

/**
 * 原生界面内的审批记忆。
 *
 * 官方审批是一次性授权，没有“记住”outcome；这里仅在同一原生界面进程/同一次运行内，
 * 对相同会话、工具和风险等级自动复用用户刚才的选择。它不写 DSH 配置、不跨重启、
 * 不改变官方审批策略；关闭原生页或服务停止时清空。
 */
class DshApprovalMemory {
    private val entries = LinkedHashMap<String, DshRememberedChoice>()
    private val lock = Any()

    fun remember(sessionId: String, toolName: String, risk: DshRiskLevel, outcome: DshApprovalOutcome): Boolean {
        if (sessionId.isBlank() || toolName.isBlank()) return false
        if (outcome != DshApprovalOutcome.ALLOWED_ONCE && outcome != DshApprovalOutcome.REJECTED) return false
        synchronized(lock) {
            entries[key(sessionId, toolName, risk)] = DshRememberedChoice(sessionId, toolName, risk, outcome)
        }
        return true
    }

    fun lookup(sessionId: String, toolName: String, risk: DshRiskLevel): DshApprovalOutcome? =
        synchronized(lock) { entries[key(sessionId, toolName, risk)]?.outcome }

    fun snapshot(): List<DshRememberedChoice> = synchronized(lock) { entries.values.toList() }

    /** 单独撤销一条记忆；撤销后相同会话/工具/风险不再自动回答。 */
    fun forget(choice: DshRememberedChoice): Boolean = synchronized(lock) {
        entries.remove(choice.id) != null
    }

    fun forget(sessionId: String, toolName: String, risk: DshRiskLevel): Boolean = synchronized(lock) {
        entries.remove(key(sessionId, toolName, risk)) != null
    }

    fun clear() = synchronized(lock) { entries.clear() }

    private fun key(sessionId: String, toolName: String, risk: DshRiskLevel): String =
        "$sessionId\u0000${toolName.lowercase()}\u0000${risk.name}"
}
