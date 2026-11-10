package com.tanuki.web

import org.json.JSONObject
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
    val displayReason: String? = null
)

/** `/api/remote.mux` `$events` 流中本切片关心的事件。 */
sealed interface DshEventFrame {
    /** 官方事件流已就绪，返回后续回答所需的 clientId。 */
    data class Ready(val clientId: String) : DshEventFrame

    /** 一个待回答的审批请求。 */
    data class Approval(val request: DshApprovalRequest) : DshEventFrame

    /** Host 撤回了同一 eventId 的审批请求。 */
    data class Cancelled(val eventId: String) : DshEventFrame

    /** 官方逻辑流返回错误；只保留短错误码，不显示可能含细节的 message。 */
    data class StreamError(val code: String) : DshEventFrame

    /** 本切片不处理的通知/未知帧；不解析其正文，避免把工具参数或会话内容带进原生层。 */
    data object Ignored : DshEventFrame
}

/** 审批通道的纯协议层：只做白名单字段校验、请求信封与回答信封，不做网络 I/O。 */
object DshApprovalProtocol {
    const val EVENTS_ENDPOINT = "\$events"
    const val RESULT_ENDPOINT = "\$events/result"
    const val MUX_PATH = "/api/remote.mux"

    /** 单条文本帧上限；官方审批帧很小，超过即视为异常连接。 */
    const val MAX_FRAME_BYTES = 1_048_576
    const val MAX_TOOL_NAME_CHARS = 128
    const val MAX_REASON_CHARS = 2_000

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
        JSONObject()
            .put("type", "client-request")
            .put("rpcId", rpcId)
            .put("method", RESULT_ENDPOINT)
            .put("payload", JSONObject().put("args", JSONObject()
                .put("clientId", clientId)
                .put("eventId", eventId)
                .put("outcome", JSONObject()
                    .put("kind", "result")
                    .put("value", outcome.wire))))
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
        "waterfall" -> parseApproval(value)
        "cancel" -> {
            val eventId = value.optString("eventId")
            if (!safeId(eventId, 128)) DshEventFrame.Ignored else DshEventFrame.Cancelled(eventId)
        }
        else -> DshEventFrame.Ignored
    }

    private fun parseApproval(value: JSONObject): DshEventFrame {
        if (value.optString("event") != "approval/request") return DshEventFrame.Ignored
        val eventId = value.optString("eventId")
        val sessionId = value.optString("agentId")
        if (!safeId(eventId, 128) || !safeId(sessionId, 128)) return DshEventFrame.Ignored
        val request = value.optJSONObject("request") ?: return DshEventFrame.Ignored
        val toolName = request.optString("toolName").trim()
        if (toolName.isBlank() || toolName.length > MAX_TOOL_NAME_CHARS) return DshEventFrame.Ignored
        // 只读取白名单字段；request 里的其他内容不复制到原生层。
        val callId = request.optString("callId").takeIf { safeId(it, 128) }
        val reason = text(request, "reason")
        val displayReason = displayReason(request)
        return DshEventFrame.Approval(
            DshApprovalRequest(
                eventId = eventId,
                sessionId = sessionId,
                toolName = toolName,
                callId = callId,
                reason = reason,
                displayReason = displayReason
            )
        )
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

/** 一条“本次原生界面运行内记住”的审批选择。 */
data class DshRememberedChoice(
    val sessionId: String,
    val toolName: String,
    val risk: DshRiskLevel,
    val outcome: DshApprovalOutcome
)

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

    fun clear() = synchronized(lock) { entries.clear() }

    private fun key(sessionId: String, toolName: String, risk: DshRiskLevel): String =
        "$sessionId\u0000${toolName.lowercase()}\u0000${risk.name}"
}
