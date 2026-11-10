package com.tanuki.web

import org.json.JSONArray
import org.json.JSONObject
import java.util.TreeMap

/** 官方 remote.mux 的下行帧；只认官方声明的 item/error/end 三种。 */
sealed interface DshMuxDownlink {
    data class Item(val streamId: String, val value: JSONObject) : DshMuxDownlink
    data class Failure(val streamId: String, val code: String) : DshMuxDownlink
    data class Ended(val streamId: String) : DshMuxDownlink
}

/** 官方 `$events` 转发流中与原生页相关的帧；其他事件在解析层丢弃。 */
sealed interface DshEventFrame {
    data class Ready(val clientId: String) : DshEventFrame
    data class Emit(val event: String, val args: JSONArray) : DshEventFrame
    data class Waterfall(
        val event: String,
        val eventId: String,
        val agentId: String,
        val request: JSONObject
    ) : DshEventFrame
    data class Approval(val request: DshApprovalRequest) : DshEventFrame
    data class Cancelled(val eventId: String) : DshEventFrame
    data class StreamError(val code: String) : DshEventFrame
    data class UnsupportedWaterfall(val eventId: String) : DshEventFrame
    data object Ignored : DshEventFrame
}

enum class DshApprovalKind { APPROVAL, QUESTION }

/**
 * 原生页展示的待处理审批/提问。只保留可展示的最小字段，不读取工具参数、
 * 完整请求对象或凭据，避免把敏感内容复制进界面状态与日志。
 */
data class DshApproval(
    val eventId: String,
    val sessionId: String,
    val kind: DshApprovalKind,
    val title: String,
    val detail: String?
)

/** 官方 session/follow 的可见帧；文本增量由 [DshFollowParser] 聚合成一段回复。 */
sealed interface DshFollowFrame {
    data class Snapshot(
        val messages: List<DshMessage>,
        val hasMore: Boolean,
        val liveText: String,
        val tools: List<DshToolItem> = emptyList()
    ) : DshFollowFrame
    data class Message(val message: DshMessage) : DshFollowFrame
    data class Streaming(val text: String) : DshFollowFrame
    data class Finished(val committed: Boolean, val seq: Int?) : DshFollowFrame
}

/**
 * 官方 Typert Remote 流协议封装。
 *
 * `/api/remote.mux` 的每个逻辑流用 `open/item/end/cancel` 文本帧；`$events`
 * 负责转发 Agent 事件与审批 waterfall，`session/follow` 负责历史快照、增量事件
 * 与仍在生成中的 assistant 文本。这里不复制官方 Web 的渲染语义，只解析原生页
 * 需要的文本、状态与审批字段。
 */
object DshMuxProtocol {
    const val MUX_PATH = "/api/remote.mux"
    const val EVENTS_ENDPOINT = "\$events"
    const val EVENTS_RESULT_ENDPOINT = "\$events/result"
    const val FOLLOW_ENDPOINT = "session/follow"

    const val MAX_TEXT_BYTES = 2 * 1024 * 1024
    const val MAX_APPROVAL_TITLE = 160
    const val MAX_APPROVAL_DETAIL = 600

    private val STREAM_ID = Regex("[A-Za-z0-9.-]{1,80}")
    private val ENDPOINT_SEGMENT = Regex("[A-Za-z0-9_$.-]+")
    private val ERROR_CODE = Regex("[A-Za-z0-9/_-]{1,80}")
    private val EVENT_NAME = Regex("[A-Za-z0-9/_.-]{1,80}")

    fun validStreamId(value: String?): Boolean = value != null && STREAM_ID.matches(value)

    fun validEventId(value: String?): Boolean =
        value != null && value.isNotBlank() && value.length <= 128 && value.all { it.code in 0x21..0x7e }

    /** 只接受官方 endpoint 形式，禁止把外部地址塞进流请求。 */
    fun validEndpoint(endpoint: String?): Boolean {
        // `$events` 是网关内部单段端点；其余官方方法固定为 namespace/method。
        if (endpoint == EVENTS_ENDPOINT) return true
        return endpoint != null && endpoint.split('/').let { parts ->
            parts.size == 2 && parts.all { it.isNotEmpty() && ENDPOINT_SEGMENT.matches(it) }
        }
    }

    fun openFrame(streamId: String, endpoint: String, args: JSONObject): String {
        require(validStreamId(streamId)) { "流标识无效" }
        require(validEndpoint(endpoint)) { "流端点无效" }
        return JSONObject()
            .put("type", "open")
            .put("streamId", streamId)
            .put("endpoint", endpoint)
            .put("payload", JSONObject().put("args", args))
            .toString()
    }

    fun itemFrame(streamId: String, value: Any): String {
        require(validStreamId(streamId)) { "流标识无效" }
        return JSONObject()
            .put("type", "item")
            .put("streamId", streamId)
            .put("value", value)
            .toString()
    }

    fun endFrame(streamId: String): String {
        require(validStreamId(streamId)) { "流标识无效" }
        return JSONObject().put("type", "end").put("streamId", streamId).toString()
    }

    fun cancelFrame(streamId: String): String {
        require(validStreamId(streamId)) { "流标识无效" }
        return JSONObject().put("type", "cancel").put("streamId", streamId).toString()
    }

    /** 服务端帧只接受三种官方类型；超长或结构不符直接丢弃，不抛给界面。 */
    fun parseDownlink(text: String?): DshMuxDownlink? {
        if (text.isNullOrBlank() || text.length > MAX_TEXT_BYTES) return null
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val streamId = root.optString("streamId")
        if (!validStreamId(streamId)) return null
        return when (root.optString("type")) {
            "item" -> DshMuxDownlink.Item(streamId, root.optJSONObject("value") ?: return null)
            "error" -> {
                val code = root.optJSONObject("error")?.optString("code").orEmpty()
                DshMuxDownlink.Failure(streamId, if (ERROR_CODE.matches(code)) code else "unknown")
            }
            "end" -> DshMuxDownlink.Ended(streamId)
            else -> null
        }
    }

    fun eventsOpenArgs(): JSONObject = JSONObject()

    fun followArgs(sessionId: String, maxMessages: Int = 120): JSONObject = JSONObject().put(
        "request",
        JSONObject()
            .put("address", JSONObject().put("kind", "session").put("sessionId", sessionId))
            .put("assistantStream", true)
            .put("maxMessages", maxMessages.coerceIn(1, 200))
    )

    fun parseEventFrame(value: JSONObject): DshEventFrame? = when (value.optString("type")) {
        "ready" -> {
            val clientId = value.optString("clientId")
            if (!validEventId(clientId)) null else DshEventFrame.Ready(clientId)
        }
        "waterfall" -> {
            val event = value.optString("event")
            if (!EVENT_NAME.matches(event)) return null
            if (event != "approval/request" && event != "user-questions/request") return null
            val eventId = value.optString("eventId")
            val agentId = value.optString("agentId")
            val request = value.optJSONObject("request")
            if (!validEventId(eventId) || !validEventId(agentId) || request == null) null
            else DshEventFrame.Waterfall(event, eventId, agentId, request)
        }
        "cancel" -> {
            val eventId = value.optString("eventId")
            if (!validEventId(eventId)) null else DshEventFrame.Cancelled(eventId)
        }
        "emit" -> {
            val event = value.optString("event")
            val args = value.optJSONArray("args")
            if (!EVENT_NAME.matches(event) || args == null) null else DshEventFrame.Emit(event, args)
        }
        else -> null
    }

    /** 审批 waterfall 只提取工具名、原因与提问文本；不保存任何执行参数。 */
    fun approvalOf(frame: DshEventFrame.Waterfall): DshApproval? {
        if (!validEventId(frame.eventId) || !validEventId(frame.agentId)) return null
        return if (frame.event == "approval/request") {
            val tool = safeText(frame.request.optString("toolName"), MAX_APPROVAL_TITLE)
                .ifBlank { "未知工具" }
            val reason = displayReason(frame.request)?.let { safeText(it, MAX_APPROVAL_DETAIL) }
                ?.takeIf { it.isNotBlank() }
            DshApproval(frame.eventId, frame.agentId, DshApprovalKind.APPROVAL, tool, reason)
        } else {
            val question = frame.request.optJSONArray("questions")?.optJSONObject(0) ?: return null
            val text = safeText(question.optString("question"), MAX_APPROVAL_DETAIL)
            if (text.isBlank()) return null
            val labels = question.optJSONArray("options")?.let { options ->
                buildList {
                    for (index in 0 until options.length()) {
                        val label = safeText(options.optJSONObject(index)?.optString("label").orEmpty(), 80)
                        if (label.isNotBlank()) add(label)
                    }
                }.joinToString(" / ")
            }?.takeIf { it.isNotBlank() }
            DshApproval(frame.eventId, frame.agentId, DshApprovalKind.QUESTION, text, labels)
        }
    }

    private fun displayReason(request: JSONObject): String? {
        val display = request.optJSONObject("displayReason") ?: return request.optString("reason").takeIf { it.isNotBlank() }
        display.optString("en").takeIf { it.isNotBlank() }?.let { return it }
        val keys = display.keys()
        while (keys.hasNext()) {
            val text = display.optString(keys.next())
            if (text.isNotBlank()) return text
        }
        return request.optString("reason").takeIf { it.isNotBlank() }
    }

    private fun safeText(value: String, limit: Int): String =
        value.filterNot { it == '\u0000' }.trim().take(limit)
}

/**
 * session/follow 的有状态解析器：快照里的 compact assistant 记录和后续
 * 增量 chunk 都只保留 text-delta，不显示 reasoning 或工具参数。
 */
class DshFollowParser {
    private val textByIndex = TreeMap<Int, StringBuilder>()

    fun accept(value: JSONObject): List<DshFollowFrame> = when (value.optString("type")) {
        "snapshot" -> {
            resetStream()
            val records = value.optJSONArray("records") ?: JSONArray()
            expandBaseline(value.optJSONObject("assistantStream"))
            listOf(
                DshFollowFrame.Snapshot(
                    messages = DshApiProtocol.parseRecords(records),
                    hasMore = value.optBoolean("hasMore", false),
                    liveText = liveText(),
                    tools = DshToolTimeline.parse(records)
                        .filterIsInstance<DshTimelineItem.Tool>()
                        .map { it.tool }
                )
            )
        }
        "event" -> {
            val event = value.optJSONObject("event") ?: return emptyList()
            val message = DshApiProtocol.parseMessage(event)
            if (message != null) {
                // 已落盘的 assistant 消息取代同一段临时回复，避免重复气泡。
                if (message.role == DshMessageRole.ASSISTANT) resetStream()
                listOf(DshFollowFrame.Message(message))
            } else {
                emptyList()
            }
        }
        "assistant-stream" -> acceptAssistantStream(value.optJSONObject("frame"))
        else -> emptyList()
    }

    fun liveText(): String {
        val parts = textByIndex.values.map { it.toString() }.filter { it.isNotBlank() }
        val text = parts.joinToString("\n").trim()
        return if (text.length <= DshApiProtocol.MAX_MESSAGE_CHARS) text
        else text.take(DshApiProtocol.MAX_MESSAGE_CHARS) + "…"
    }

    private fun acceptAssistantStream(frame: JSONObject?): List<DshFollowFrame> {
        if (frame == null) return emptyList()
        return when (frame.optString("type")) {
            "start" -> {
                resetStream()
                listOf(DshFollowFrame.Streaming(""))
            }
            "chunk" -> {
                val chunk = frame.optJSONObject("chunk") ?: return emptyList()
                appendChunk(chunk)
                listOf(DshFollowFrame.Streaming(liveText()))
            }
            "end" -> {
                val outcome = frame.optJSONObject("outcome")
                val committed = outcome?.optString("kind") == "committed"
                val seq = if (committed && outcome.optString("eventType") == "assistant/message") {
                    outcome.optInt("seq", -1).takeIf { it >= 0 }
                } else null
                if (!committed) resetStream()
                listOf(DshFollowFrame.Finished(committed, seq))
            }
            else -> emptyList()
        }
    }

    private fun expandBaseline(baseline: JSONObject?) {
        val stream = baseline?.optJSONObject("activeAttempt")?.optJSONArray("stream") ?: return
        for (index in 0 until stream.length()) {
            val record = stream.optJSONObject(index) ?: continue
            when (record.optString("type")) {
                "text-chunks" -> {
                    val target = record.optInt("index", 0)
                    val texts = record.optJSONArray("texts") ?: continue
                    for (position in 0 until texts.length()) {
                        appendText(target, texts.optString(position))
                    }
                }
                "chunk" -> record.optJSONObject("chunk")?.let(::appendChunk)
            }
        }
    }

    private fun appendChunk(chunk: JSONObject) {
        if (chunk.optString("type") != "text-delta") return
        appendText(chunk.optInt("index", 0), chunk.optString("text"))
    }

    private fun appendText(index: Int, text: String) {
        if (text.isEmpty()) return
        val builder = textByIndex.getOrPut(index.coerceAtLeast(0)) { StringBuilder() }
        if (builder.length >= DshApiProtocol.MAX_MESSAGE_CHARS) return
        val remaining = DshApiProtocol.MAX_MESSAGE_CHARS - builder.length
        builder.append(text.take(remaining))
    }

    private fun resetStream() {
        textByIndex.clear()
    }
}
