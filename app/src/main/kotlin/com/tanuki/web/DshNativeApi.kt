package com.tanuki.web

import com.tanuki.runtime.Redact
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 当前 DSH 进程的 loopback 地址与带启动 token 的官方入口地址。 */
data class DshEndpoint(val origin: String, val launchUrl: String) {
    /** 默认 data class toString 会带出启动 token；显式脱敏，避免误日志。 */
    override fun toString(): String = "DshEndpoint(origin=$origin, launchUrl=<redacted>)"
}

/** 原生只读页展示的官方会话摘要。字段来自 session/list，不另造本地会话库。 */
data class DshSession(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val running: Boolean,
    val blank: Boolean,
    val agentAvailable: Boolean,
    val asOfSeq: Int?
) {
    val displayTitle: String get() = title.ifBlank { "会话 ${id.take(8)}" }
}

enum class DshMessageRole(val label: String) { USER("你"), ASSISTANT("DSH") }

/** 从官方 session/page 或 session/follow 事件中提取的文本消息。 */
data class DshMessage(
    val seq: Int,
    val role: DshMessageRole,
    val text: String,
    val time: Long,
    /** 仅本地发送占位使用；官方落盘后同一文本的用户消息会替换它。 */
    val pending: Boolean = false
)

data class DshSessionPage(
    val messages: List<DshMessage>,
    val hasMore: Boolean,
    val timeline: List<DshTimelineItem> = messages.map { DshTimelineItem.Text(it) }
)

/** 官方 Remote 返回的业务错误；只保留短错误码，避免把响应细节带入界面或日志。 */
class DshApiException(val code: String) : IOException("DSH 接口返回 $code")

/**
 * 官方 DSH 的 unary Remote 协议封装。
 *
 * DSH 没有 REST 会话端点：浏览器先通过 `GET /connect?token=...&interactions=none`
 * 换取独立的 API Cookie，再用 `POST /api/<method>` 发送
 * `{"type":"client-request","rpcId":...,"method":...,"payload":{"args":{...}}}`
 * 信封。这里只调用官方已声明的 method，不复制 Cookie 给 WebView，也不伪造数据。
 */
object DshApiProtocol {
    const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    const val MAX_MESSAGE_CHARS = 12_000
    private val ENDPOINT_SEGMENT = Regex("[A-Za-z0-9_$.-]+")

    fun validOrigin(origin: String?): Boolean = runCatching {
        if (origin == null) return false
        val uri = URI(origin)
        uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1..65535 &&
            uri.userInfo == null && uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null
    }.getOrDefault(false)

    /**
     * 使用同一次启动的 token 换取 API Cookie；不跟随重定向，避免把 token 带到别处。
     *
     * 原生页需要 `$events` 审批 waterfall 与 `session/follow` 实时流，因此默认声明
     * `interactions=answer`；只读调用也复用同一 Peer，不再另开 `none` 连接。
     */
    fun connectUrl(endpoint: DshEndpoint, interactions: String = "none"): String? = runCatching {
        if (interactions != "answer" && interactions != "none") return null
        if (!validOrigin(endpoint.origin) || !OriginPolicy.sameOrigin(endpoint.launchUrl, endpoint.origin)) return null
        val uri = URI(endpoint.launchUrl)
        val query = uri.rawQuery ?: return null
        val parts = query.split('&').filter { it.isNotBlank() }
        val hasToken = parts.any { part ->
            part.substringBefore('=').equals("token", ignoreCase = true) &&
                part.substringAfter('=', "").isNotBlank()
        }
        if (!hasToken) return null
        val withoutInteractions = parts.filterNot { it.substringBefore('=').equals("interactions", ignoreCase = true) }
        "${endpoint.origin}/connect?${withoutInteractions.joinToString("&")}&interactions=$interactions"
    }.getOrNull()

    /** 只接受官方 endpoint 形式的 method，并固定拼到当前 loopback origin。 */
    fun apiUrl(origin: String?, method: String): String? {
        if (!validOrigin(origin) || method.isBlank()) return null
        if (method.split('/').any { it.isEmpty() || it == "." || it == ".." || !ENDPOINT_SEGMENT.matches(it) }) return null
        return "$origin/api/$method"
    }

    /** 从 Set-Cookie 中只保留 name=value；属性不进入后续请求。 */
    fun cookieFrom(setCookieHeaders: List<String>): String? {
        for (header in setCookieHeaders) {
            val pair = header.substringBefore(';').trim()
            val separator = pair.indexOf('=')
            if (separator <= 0) continue
            val name = pair.substring(0, separator).trim()
            val value = pair.substring(separator + 1).trim()
            if (!name.matches(Regex("[A-Za-z0-9._-]+"))) continue
            if (value.isEmpty() || value.any { it <= '\u0020' || it == '\u007f' || it == ';' }) continue
            return "$name=$value"
        }
        return null
    }

    fun requestBody(rpcId: String, method: String, args: JSONObject): String =
        JSONObject()
            .put("type", "client-request")
            .put("rpcId", rpcId)
            .put("method", method)
            .put("payload", JSONObject().put("args", args))
            .toString()

    fun newRpcId(): String = UUID.randomUUID().toString()

    /** 官方客户端按 rpcId 关联响应；错配响应不能进入会话解析。 */
    fun checkEnvelope(body: String, expectedRpcId: String) {
        val root = JSONObject(body)
        if (root.optString("type") != "server-response" || root.optString("rpcId") != expectedRpcId) {
            throw IllegalArgumentException("DSH 接口响应关联不正确")
        }
    }

    fun parseSessionList(body: String): List<DshSession> {
        val value = successValue(body)?.optJSONObject("value") ?: throw IllegalArgumentException("会话列表响应格式不正确")
        val items = value.optJSONArray("items") ?: JSONArray()
        val result = ArrayList<DshSession>(items.length())
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("sessionId").trim()
            if (id.isBlank()) continue
            val projections = item.optJSONObject("projections")
            val projectionValues = projections?.optJSONObject("values")
            result += DshSession(
                id = id,
                title = projectionValues?.optString("title", "").orEmpty(),
                updatedAt = item.optLong("updatedAt", 0L),
                running = item.optBoolean("running", false),
                blank = item.optBoolean("blank", false),
                agentAvailable = item.optBoolean("agentAvailable", false),
                asOfSeq = projections?.takeIf { it.has("asOfSeq") }?.optInt("asOfSeq", -1)?.takeIf { it >= 0 }
            )
        }
        return result.sortedByDescending { it.updatedAt }
    }

    /** projections 的 asOfSeq 是官方事件切点，可直接作为 session/page 的 throughSeq。 */
    fun parseProjectionSeq(body: String): Int? {
        val result = successValue(body) ?: return null
        val value = result.optJSONObject("value") ?: return null
        if (value.optString("kind") != "sequenced") return null
        return value.takeIf { it.has("asOfSeq") }?.optInt("asOfSeq", -1)?.takeIf { it >= 0 }
    }

    fun parsePage(body: String): DshSessionPage {
        val value = successValue(body)?.optJSONObject("value") ?: throw IllegalArgumentException("消息响应格式不正确")
        val records = value.optJSONArray("records") ?: JSONArray()
        return DshSessionPage(
            messages = parseRecords(records),
            hasMore = value.optBoolean("hasMore", false),
            timeline = DshToolTimeline.parse(records)
        )
    }

    /** 历史页与实时跟随共用同一套事件过滤，避免两个入口显示不一致。 */
    fun parseRecords(records: JSONArray): List<DshMessage> {
        val messages = ArrayList<DshMessage>(records.length())
        for (index in 0 until records.length()) {
            val event = records.optJSONObject(index)?.optJSONObject("event") ?: continue
            parseMessage(event)?.let(messages::add)
        }
        messages.sortBy { it.seq }
        return messages
    }

    fun parseMessage(event: JSONObject): DshMessage? {
        val type = event.optString("type")
        val role = when (type) {
            "user/message" -> DshMessageRole.USER
            "assistant/message" -> DshMessageRole.ASSISTANT
            else -> return null
        }
        // 官方聊天只取 append-origin surface；replace 副本仅供模型上下文，不能当对话显示。
        if (event.opt("surfaceOp") != "append") return null
        val data = event.optJSONObject("data") ?: return null
        // user/message 也可能是注入上下文；原生只把真实用户来源标成“你”。
        if (role == DshMessageRole.USER) {
            val sourceKind = data.optJSONObject("source")?.optString("kind")
            if (sourceKind != null && sourceKind != "user") return null
        }
        val content = when (role) {
            DshMessageRole.USER -> data.optJSONArray("content")
                ?: data.optJSONObject("message")?.optJSONArray("content")
            DshMessageRole.ASSISTANT -> data.optJSONObject("message")?.optJSONArray("content")
                ?: data.optJSONArray("content")
        } ?: return null
        val text = textOf(content)
        if (text.isBlank()) return null
        return DshMessage(
            seq = event.optInt("seq", 0),
            role = role,
            text = text,
            time = event.optLong("time", 0L)
        )
    }

    private fun textOf(content: JSONArray): String {
        val builder = StringBuilder()
        for (index in 0 until content.length()) {
            val part = content.optJSONObject(index) ?: continue
            if (part.optString("type") != "text") continue
            val text = part.optString("text")
            if (text.isBlank()) continue
            if (builder.isNotEmpty()) builder.append('\n')
            builder.append(text)
            if (builder.length >= MAX_MESSAGE_CHARS) break
        }
        val text = builder.toString().trim()
        return if (text.length <= MAX_MESSAGE_CHARS) text else text.take(MAX_MESSAGE_CHARS) + "…"
    }

    /** 成功时返回 result 对象；业务失败只抛出短错误码。 */
    private fun successValue(body: String): JSONObject? {
        val root = JSONObject(body)
        if (root.optString("type") != "server-response") throw IllegalArgumentException("DSH 接口响应类型不正确")
        val result = root.optJSONObject("result") ?: throw IllegalArgumentException("DSH 接口响应缺少 result")
        if (!result.optBoolean("ok", false)) {
            val code = result.optJSONObject("error")?.optString("code").orEmpty()
            throw DshApiException(code.takeIf { it.matches(Regex("[A-Za-z0-9/_-]{1,80}")) } ?: "unknown")
        }
        return result
    }

    /** 输入/审批/会话管理组件需要官方 `result.value`；undefined 统一转成 null。 */
    fun parseValue(body: String): Any? {
        val value = successValue(body)?.opt("value")
        return if (value == null || value === JSONObject.NULL) null else value
    }

    /** 发送/取消的官方结果都要求 `accepted=true`，不能只凭 HTTP 200 判断成功。 */
    fun parseAccepted(body: String): Boolean =
        successValue(body)?.optJSONObject("value")?.optBoolean("accepted", false) == true

    /** 审批应答只关心业务成功；失败仍抛出短错误码，不回显响应细节。 */
    fun checkSuccess(body: String) {
        successValue(body)
    }
}

/** 原生页依赖的最小会话数据源；生产实现是官方 API，测试可替换。 */
interface DshSessionSource {
    suspend fun sessions(): Result<List<DshSession>>
    suspend fun messages(sessionId: String): Result<DshSessionPage>
    fun reset() {}
}

/**
 * 用当前 DSH 启动 token 换取一个独立 API Peer，再读取官方会话、历史、实时流并发送消息。
 * Cookie 只保存在本对象内存中；本类不写日志、不把 token/Cookie 放进异常文本。
 */
class DshNativeClient(
    private val socketFactory: () -> DshMuxSocket = { DshWebSocketMuxSocket() },
    private val endpointProvider: () -> DshEndpoint?
) : DshChatSource, DshTransport {
    private val stateLock = Any()
    /** 同一时刻只允许一条 remote.mux 物理连接；切换会话时先关闭旧连接。 */
    private val muxLock = Mutex()
    private var cookie: String? = null
    private var boundOrigin: String? = null
    private var boundLaunchUrl: String? = null
    /** 每次 reset 递增；旧认证请求即使晚到也不能重新发布 Cookie。 */
    private var authGeneration = 0L
    private var muxGeneration = 0L
    private var activeSocket: DshMuxSocket? = null
    /** 事件回答固定属于建立该 mux 的 Peer，不得在 401 后换 Cookie 重试旧 clientId。 */
    private class EventsPeer(val endpoint: DshEndpoint, val cookie: String) {
        var clientId: String? = null
    }
    private val _connection = MutableStateFlow(DshConnectionState.DISCONNECTED)
    override val connection: StateFlow<DshConnectionState> = _connection.asStateFlow()

    /** 只短暂同步状态，绝不等待网络；主线程可安全调用。 */
    override fun reset() {
        val socket: DshMuxSocket?
        synchronized(stateLock) {
            authGeneration++
            muxGeneration++
            cookie = null
            boundOrigin = null
            boundLaunchUrl = null
            socket = activeSocket
            activeSocket = null
            _connection.value = DshConnectionState.DISCONNECTED
        }
        runCatching { socket?.close() }
    }

    override suspend fun sessions(): Result<List<DshSession>> = withContext(Dispatchers.IO) {
        runCatching { parse(DshApiProtocol::parseSessionList, "session/list", JSONObject().put("_request", JSONObject())) }
    }

    override suspend fun messages(sessionId: String): Result<DshSessionPage> = withContext(Dispatchers.IO) {
        runCatching {
            require(sessionId.isNotBlank()) { "会话标识为空" }
            val projection = callEnvelope(
                "session/projections",
                JSONObject().put("request", JSONObject().put("sessionId", sessionId))
            )
            val throughSeq = DshApiProtocol.parseProjectionSeq(projection)
                ?: return@runCatching DshSessionPage(emptyList(), false)
            val page = callEnvelope(
                "session/page",
                JSONObject().put(
                    "request",
                    JSONObject()
                        .put("address", JSONObject().put("kind", "session").put("sessionId", sessionId))
                        .put("throughSeq", throughSeq)
                        .put("maxMessages", 120)
                )
            )
            DshApiProtocol.parsePage(page)
        }
    }

    /** 官方 `session/prompt`；`mode=queue` 与官方网页默认一致，不伪造本地队列。 */
    override suspend fun send(sessionId: String, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(sessionId.isNotBlank()) { "会话标识为空" }
            val prompt = text.trim()
            require(prompt.isNotEmpty()) { "消息不能为空" }
            require(prompt.length <= MAX_PROMPT_CHARS) { "消息过长（最多 $MAX_PROMPT_CHARS 字）" }
            val body = callEnvelope(
                "session/prompt",
                JSONObject().put(
                    "request",
                    JSONObject()
                        .put("requestId", UUID.randomUUID().toString())
                        .put("sessionId", sessionId)
                        .put("mode", "queue")
                        .put(
                            "content",
                            JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
                        )
                )
            )
            if (!DshApiProtocol.parseAccepted(body)) throw IOException("官方接口未接受消息")
        }
    }

    /** 官方 `session/cancel`；只取消当前活动回合，不清空官方待处理队列。 */
    override suspend fun cancel(sessionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(sessionId.isNotBlank()) { "会话标识为空" }
            val body = callEnvelope(
                "session/cancel",
                JSONObject().put("request", JSONObject().put("sessionId", sessionId))
            )
            if (!DshApiProtocol.parseAccepted(body)) throw IOException("官方接口未接受停止请求")
        }
    }

    /** 聊天流接收状态通知；waterfall 只转交给负责审批 UI 的 Peer。 */
    override fun live(sessionId: String): Flow<DshLiveEvent> = flow {
        require(sessionId.isNotBlank()) { "会话标识为空" }
        muxLock.withLock {
            // 认证、握手与 socket 读取都是阻塞 I/O；整个上游必须在 IO 线程，不能占主线程。
            _connection.value = DshConnectionState.CONNECTING
            var generation = 0L
            var socket: DshMuxSocket? = null
            try {
                val endpoint = endpointProvider() ?: throw IOException("DSH 服务地址尚未就绪")
                generation = synchronized(stateLock) { ++muxGeneration }
                val requestGeneration = synchronized(stateLock) { authGeneration }
                val currentCookie = ensureCookie(endpoint, requestGeneration)
                val opened = socketFactory()
                socket = opened
                opened.connect(endpoint.origin, currentCookie)
                synchronized(stateLock) {
                    if (generation != muxGeneration) throw IOException("实时连接已取消")
                    activeSocket = opened
                    _connection.value = DshConnectionState.CONNECTED
                }
                val eventsId = "events-${UUID.randomUUID()}"
                val followId = "follow-${UUID.randomUUID()}"
                opened.send(DshMuxProtocol.openFrame(eventsId, DshMuxProtocol.EVENTS_ENDPOINT, DshMuxProtocol.eventsOpenArgs()))
                opened.send(DshMuxProtocol.openFrame(followId, DshMuxProtocol.FOLLOW_ENDPOINT, DshMuxProtocol.followArgs(sessionId)))
                emit(DshLiveEvent.Connected)
                val followParser = DshFollowParser()
                val eventsPeer = EventsPeer(endpoint, currentCookie)
                opened.incoming().collect { text ->
                    when (val downlink = DshMuxProtocol.parseDownlink(text)) {
                        is DshMuxDownlink.Item -> when (downlink.streamId) {
                            eventsId -> handleEvent(downlink.value, sessionId, eventsPeer)?.let { emit(it) }
                            followId -> {
                                // 工具事件从同一 follow 事件流增量合成；文本仍交给 follow 解析器。
                                if (downlink.value.optString("type") == "event") {
                                    val tools = DshToolTimeline.parse(JSONArray().put(downlink.value))
                                        .filterIsInstance<DshTimelineItem.Tool>()
                                        .map { it.tool }
                                    if (tools.isNotEmpty()) emit(DshLiveEvent.Timeline(tools))
                                }
                                followParser.accept(downlink.value).forEach { frame ->
                                when (frame) {
                                    is DshFollowFrame.Snapshot ->
                                        emit(DshLiveEvent.Snapshot(frame.messages, frame.hasMore, frame.liveText, frame.tools))
                                    is DshFollowFrame.Message -> emit(DshLiveEvent.Message(frame.message))
                                    is DshFollowFrame.Streaming -> emit(DshLiveEvent.Streaming(frame.text))
                                    is DshFollowFrame.Finished -> emit(DshLiveEvent.Finished(frame.committed))
                                }
                                }
                            }
                        }
                        is DshMuxDownlink.Failure -> emit(DshLiveEvent.Failure(liveFailure(DshApiException(downlink.code))))
                        is DshMuxDownlink.Ended -> {
                            if (downlink.streamId == followId) {
                                emit(DshLiveEvent.Failure("实时消息流已结束，可点刷新重连"))
                                emit(DshLiveEvent.Finished(false))
                            } else {
                                emit(DshLiveEvent.Failure("实时事件流已结束，审批请到官方界面处理"))
                            }
                        }
                        null -> Unit
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // 先释放回答者再提示错误，Host 才能继续 waterfall，不能留一个无 UI 的等待者。
                runCatching { socket?.close() }
                if (generation == 0L || generation == muxGeneration) {
                    _connection.value = DshConnectionState.FAILED
                }
                emit(DshLiveEvent.Failure(liveFailure(error)))
            } finally {
                runCatching { socket?.close() }
                synchronized(stateLock) {
                    if (generation != 0L && generation == muxGeneration) {
                        activeSocket = null
                        // 关闭物理 socket 会销毁该 API Peer，旧 Cookie 不可再用于 HTTP。
                        cookie = null
                        boundOrigin = null
                        boundLaunchUrl = null
                        if (_connection.value != DshConnectionState.FAILED) {
                            _connection.value = DshConnectionState.DISCONNECTED
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun handleEvent(value: JSONObject, sessionId: String, peer: EventsPeer): DshLiveEvent? {
        val frame = DshMuxProtocol.parseEventFrame(value) ?: return null
        return when (frame) {
            is DshEventFrame.Ready -> {
                peer.clientId = frame.clientId
                null
            }
            is DshEventFrame.Waterfall -> {
                // 聊天 Peer 不展示/回答审批；明确转交 next，让 Host 交给独立审批 Peer。
                forwardWaterfallNext(peer, frame.eventId)
                null
            }
            is DshEventFrame.Cancelled -> null
            is DshEventFrame.Emit -> when (frame.event) {
                "api-session/status" -> {
                    if (frame.args.length() < 2 || frame.args.optString(0) != sessionId) return null
                    if (frame.args.opt(1) !is Boolean) return null
                    DshLiveEvent.Running(frame.args.optBoolean(1))
                }
                "api-session/error" -> {
                    if (frame.args.length() < 1 || frame.args.optString(0) != sessionId) return null
                    DshLiveEvent.Failure("官方 Agent 报告错误，可切换官方界面查看详情")
                }
                else -> null
            }
            else -> null
        }
    }

    /**
     * next 必须使用收到事件的 Cookie/clientId；401 不换 Peer 重试。
     * 未送达时抛错，由 live 关闭 mux 释放回答者；取消同时断开正在等待的 HTTP。
     */
    private suspend fun forwardWaterfallNext(peer: EventsPeer, eventId: String) {
        try {
            val clientId = peer.clientId ?: throw IOException("事件流尚未就绪")
            val method = DshApprovalProtocol.RESULT_ENDPOINT
            val url = DshApiProtocol.apiUrl(peer.endpoint.origin, method)
                ?: throw IOException("DSH 接口地址无效")
            val connection = open(url)
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.invokeOnCancellation { connection.disconnect() }
                try {
                    if (!continuation.isActive) return@suspendCancellableCoroutine
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("Cookie", peer.cookie)
                    val rpcId = DshApiProtocol.newRpcId()
                    val args = JSONObject()
                        .put("clientId", clientId)
                        .put("eventId", eventId)
                        .put("outcome", JSONObject().put("kind", "next"))
                    val body = DshApiProtocol.requestBody(rpcId, method, args).toByteArray(Charsets.UTF_8)
                    connection.setFixedLengthStreamingMode(body.size)
                    connection.outputStream.use { it.write(body) }
                    val code = connection.responseCode
                    if (code !in 200..299) throw IOException("HTTP $code")
                    val text = readBounded(connection.inputStream)
                    DshApiProtocol.checkEnvelope(text, rpcId)
                    DshApiProtocol.parseValue(text)
                    continuation.resume(Unit)
                } catch (error: Exception) {
                    continuation.resumeWithException(error)
                } finally {
                    connection.disconnect()
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 不回显响应正文/凭据；失败后断开聊天 Peer，让 Host 继续交给可见审批入口。
            throw IOException("审批转交失败，已断开聊天实时连接；请刷新重连或切换官方界面处理")
        }
    }

    private fun liveFailure(error: Throwable): String = when (error) {
        is DshApiException -> "官方接口返回 ${error.code}"
        is org.json.JSONException -> "官方实时流格式不正确"
        else -> Redact.text(error.message ?: "实时连接失败").take(200)
    }

    /** 供输入/审批/会话管理组件使用；返回官方 result.value，不暴露 Cookie/token。 */
    override suspend fun call(method: String, args: JSONObject): Any? = withContext(Dispatchers.IO) {
        try {
            val value = DshApiProtocol.parseValue(callEnvelope(method, args))
            if (_connection.value == DshConnectionState.DISCONNECTED ||
                _connection.value == DshConnectionState.FAILED
            ) {
                _connection.value = DshConnectionState.CONNECTED
            }
            value
        } catch (error: Throwable) {
            if (_connection.value != DshConnectionState.CONNECTED) {
                _connection.value = DshConnectionState.FAILED
            }
            throw error
        }
    }

    private fun <T> parse(reader: (String) -> T, method: String, args: JSONObject): T =
        reader(callEnvelope(method, args))

    private fun callEnvelope(method: String, args: JSONObject): String {
        val endpoint = endpointProvider() ?: throw IOException("DSH 服务地址尚未就绪")
        var requestGeneration = synchronized(stateLock) { authGeneration }
        var currentCookie = ensureCookie(endpoint, requestGeneration)
        for (attempt in 0..1) {
            val url = DshApiProtocol.apiUrl(endpoint.origin, method) ?: throw IOException("DSH 接口地址无效")
            val connection = open(url)
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Cookie", currentCookie)
                val rpcId = DshApiProtocol.newRpcId()
                val body = DshApiProtocol.requestBody(rpcId, method, args).toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
                val code = connection.responseCode
                val text = readBounded(if (code in 200..299) connection.inputStream else connection.errorStream)
                if (code == 401 && attempt == 0) {
                    // 401 重试也必须是当前代次；已被 reset 的旧请求不能复活并清掉新 Cookie。
                    requestGeneration = beginCookieRefresh(requestGeneration)
                    currentCookie = ensureCookie(endpoint, requestGeneration)
                    continue
                }
                if (code !in 200..299) throw IOException("DSH 接口返回 HTTP $code")
                synchronized(stateLock) {
                    if (requestGeneration != authGeneration) throw IOException("DSH 接口认证已取消")
                }
                DshApiProtocol.checkEnvelope(text, rpcId)
                return text
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("DSH 接口认证失败")
    }

    /** 仅当请求仍属于当前代次时，原子失效旧 Cookie 并返回新代次。 */
    private fun beginCookieRefresh(expectedGeneration: Long): Long = synchronized(stateLock) {
        if (expectedGeneration != authGeneration) throw IOException("DSH 接口认证已取消")
        authGeneration++
        cookie = null
        boundOrigin = null
        boundLaunchUrl = null
        authGeneration
    }

    private fun ensureCookie(endpoint: DshEndpoint, requestGeneration: Long): String {
        synchronized(stateLock) {
            if (requestGeneration != authGeneration) throw IOException("DSH 接口认证已取消")
            val existing = cookie
            if (existing != null && boundOrigin == endpoint.origin && boundLaunchUrl == endpoint.launchUrl) return existing
        }
        val url = DshApiProtocol.connectUrl(endpoint, interactions = "answer") ?: throw IOException("DSH 启动地址无效")
        // 连接失败时不把带 token 的 URL 放进异常文本。
        val connection = try { open(url) } catch (_: IOException) { throw IOException("DSH 接口认证失败") }
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "text/plain")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_MOVED_TEMP && code != HttpURLConnection.HTTP_SEE_OTHER) {
                throw IOException("DSH 接口认证失败（HTTP $code）")
            }
            val headers = connection.headerFields.entries
                .filter { (key, _) -> key.equals("Set-Cookie", ignoreCase = true) }
                .flatMap { it.value.orEmpty() }
            val parsed = DshApiProtocol.cookieFrom(headers) ?: throw IOException("DSH 接口未返回认证 Cookie")
            synchronized(stateLock) {
                if (requestGeneration != authGeneration) throw IOException("DSH 接口认证已取消")
                cookie = parsed
                boundOrigin = endpoint.origin
                boundLaunchUrl = endpoint.launchUrl
            }
            return parsed
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        return connection
    }

    private fun readBounded(stream: InputStream?): String {
        if (stream == null) return ""
        stream.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                total += read
                if (total > DshApiProtocol.MAX_RESPONSE_BYTES) throw IOException("DSH 接口响应过大")
                output.write(buffer, 0, read)
            }
            return output.toString("UTF-8")
        }
    }

    private companion object {
        const val MAX_PROMPT_CHARS = 12_000
    }
}
