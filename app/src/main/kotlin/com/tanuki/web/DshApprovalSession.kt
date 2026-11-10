package com.tanuki.web

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 原生审批通道：复用官方 `/connect` API Cookie，打开 `/api/remote.mux` 的 `$events`
 * 逻辑流，并把用户选择通过官方 `$events/result` 一元 RPC 回传。
 *
 * 认证、连接、回答失败都返回中文错误；错误文本不含 token、Cookie 或底层异常原文。
 * 连接只允许当前精确 loopback origin，不跟随重定向，不写日志。
 */
class DshApprovalSession(
    private val endpointProvider: () -> DshEndpoint?,
    private val listener: Listener,
    private val socketFactory: (DshWebSocketClient.Listener) -> DshWebSocketClient = { DshWebSocketClient(it) }
) : DshApprovalTransport {
    interface Listener {
        /** 官方 `$events` 流已就绪，可以回答审批。 */
        fun onReady()

        fun onApproval(request: DshApprovalRequest)

        /** Host 撤回了审批请求；界面应移除对应卡片。 */
        fun onCancel(eventId: String)

        /** 连接断开或读取失败；界面保留但禁用回答，并显示中文错误。 */
        fun onDisconnected(message: String)
    }

    private val lock = Any()
    private var endpoint: DshEndpoint? = null
    private var cookie: String? = null
    private var clientId: String? = null
    private var socket: DshWebSocketClient? = null
    private var closed = false
    private var ready = false
    private val readyLatch = CountDownLatch(1)

    /**
     * 建立认证与 WebSocket。调用方应在 IO 线程执行；本方法阻塞到握手完成，
     * 官方 `ready` 帧由读线程异步送达。
     */
    @Throws(IOException::class)
    override fun connect() {
        val target = endpointProvider() ?: throw IOException("DSH 服务地址尚未就绪")
        if (!DshApiProtocol.validOrigin(target.origin) ||
            !OriginPolicy.sameOrigin(target.launchUrl, target.origin)
        ) {
            throw IOException("DSH 服务地址无效")
        }
        val apiCookie = exchangeCookie(target)
        val client = socketFactory(object : DshWebSocketClient.Listener {
            override fun onOpen() = Unit

            override fun onText(text: String) {
                when (val frame = DshApprovalProtocol.parseStreamItem(text)) {
                    is DshEventFrame.Ready -> {
                        synchronized(lock) {
                            if (closed) return
                            clientId = frame.clientId
                            ready = true
                        }
                        readyLatch.countDown()
                        listener.onReady()
                    }
                    is DshEventFrame.Approval -> listener.onApproval(frame.request)
                    is DshEventFrame.Cancelled -> listener.onCancel(frame.eventId)
                    is DshEventFrame.StreamError -> disconnect("审批通道返回错误（${frame.code}）")
                    DshEventFrame.Ignored -> Unit
                }
            }

            override fun onClosed(reason: String?) {
                val message = reason ?: "审批通道已断开"
                disconnect(message)
            }

            override fun onFailure(message: String) {
                disconnect(message)
            }
        })
        synchronized(lock) {
            if (closed) throw IOException("审批通道已关闭")
            endpoint = target
            cookie = apiCookie
            socket = client
            clientId = null
            ready = false
        }
        try {
            client.connect(target.origin, apiCookie, DshApprovalProtocol.MUX_PATH)
            if (!client.sendText(DshApprovalProtocol.openStreamBody(DshApprovalProtocol.newStreamId()))) {
                throw IOException("审批通道初始化失败")
            }
            // 官方 ready 帧证明事件流可用；只握手成功但未就绪时不能假装可回答。
            if (!readyLatch.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw IOException("审批通道未就绪（等待官方 ready 超时）")
            }
            if (closed) throw IOException("审批通道已关闭")
            // ready 到达后又立刻断线时，latch 已释放但 ready 被清零；不能把失败当成功。
            synchronized(lock) {
                if (!ready || clientId == null) throw IOException("审批通道已断开")
            }
        } catch (error: Throwable) {
            synchronized(lock) {
                if (socket === client) socket = null
                cookie = null
                clientId = null
                ready = false
            }
            client.close()
            if (error is IOException) throw error
            throw IOException("审批通道连接失败", error)
        }
    }

    /** 把一次用户选择回传官方；连接未就绪或回答失败时返回中文错误。 */
    override fun answer(eventId: String, outcome: DshApprovalOutcome): Result<Unit> {
        val snapshot = synchronized(lock) {
            if (closed) return Result.failure(IOException("审批通道已关闭"))
            val endpoint = endpoint ?: return Result.failure(IOException("DSH 服务地址尚未就绪"))
            val cookie = cookie ?: return Result.failure(IOException("DSH 审批通道尚未认证"))
            val clientId = clientId ?: return Result.failure(IOException("DSH 审批通道尚未就绪"))
            if (!ready) return Result.failure(IOException("DSH 审批通道尚未就绪"))
            Snapshot(endpoint, cookie, clientId)
        }
        return runCatching {
            try {
                postAnswer(snapshot.endpoint, snapshot.cookie, snapshot.clientId, eventId, outcome)
            } catch (_: AuthExpiredException) {
                // Cookie 失效只重认证一次；不改变已建立的审批流，也不把 token 带进错误。
                val refreshed = exchangeCookie(snapshot.endpoint)
                synchronized(lock) { if (!closed) cookie = refreshed }
                postAnswer(snapshot.endpoint, refreshed, snapshot.clientId, eventId, outcome)
            }
        }.recoverCatching { error ->
            throw IOException(
                when (error) {
                    is DshApiException -> "官方接口返回 ${error.code}"
                    else -> error.message?.takeIf { it.isNotBlank() } ?: "审批回答失败"
                }
            )
        }
    }

    /** 关闭连接；幂等。清空内存 Cookie 与 clientId，不触碰 DSH 进程或会话数据。 */
    override fun close() {
        val client = synchronized(lock) {
            if (closed) return
            closed = true
            ready = false
            clientId = null
            cookie = null
            endpoint = null
            readyLatch.countDown()
            socket.also { socket = null }
        }
        client?.close()
    }

    private fun disconnect(message: String) {
        val client = synchronized(lock) {
            if (closed) return
            ready = false
            clientId = null
            readyLatch.countDown()
            socket.also { socket = null }
        }
        client?.close()
        listener.onDisconnected(message)
    }

    private fun postAnswer(
        endpoint: DshEndpoint,
        cookie: String,
        clientId: String,
        eventId: String,
        outcome: DshApprovalOutcome
    ) {
        if (!DshApprovalProtocol.safeId(eventId, 128)) throw IOException("审批请求标识无效")
        val url = DshApiProtocol.apiUrl(endpoint.origin, DshApprovalProtocol.RESULT_ENDPOINT)
            ?: throw IOException("DSH 接口地址无效")
        val connection = open(url)
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cookie", cookie)
            val rpcId = DshApprovalProtocol.newRpcId()
            val body = DshApprovalProtocol.answerBody(rpcId, clientId, eventId, outcome)
                .toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val text = readBounded(if (code in 200..299) connection.inputStream else connection.errorStream)
            if (code == 401 || code == 403) throw AuthExpiredException()
            if (code !in 200..299) throw IOException("审批回答失败（HTTP $code）")
            DshApiProtocol.checkEnvelope(text, rpcId)
            val result = JSONObject(text).optJSONObject("result")
            if (result == null || !result.optBoolean("ok", false)) {
                val error = result?.optJSONObject("error")?.optString("code").orEmpty()
                throw DshApiException(error.takeIf { it.matches(Regex("[A-Za-z0-9/_-]{1,80}")) } ?: "unknown")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun exchangeCookie(endpoint: DshEndpoint): String {
        val url = DshApiProtocol.connectUrl(endpoint) ?: throw IOException("DSH 启动地址无效")
        val connection = try { open(url) } catch (_: IOException) {
            throw IOException("DSH 审批通道认证失败")
        }
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "text/plain")
            val code = connection.responseCode
            if (code == 401 || code == 403) {
                throw IOException("DSH 审批通道认证失败（HTTP $code）")
            }
            if (code != HttpURLConnection.HTTP_MOVED_TEMP && code != HttpURLConnection.HTTP_SEE_OTHER) {
                throw IOException("DSH 审批通道认证失败（HTTP $code）")
            }
            val headers = connection.headerFields.entries
                .filter { (key, _) -> key.equals("Set-Cookie", ignoreCase = true) }
                .flatMap { it.value.orEmpty() }
            return DshApiProtocol.cookieFrom(headers)
                ?: throw IOException("DSH 审批通道未返回认证 Cookie")
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

    private data class Snapshot(val endpoint: DshEndpoint, val cookie: String, val clientId: String)

    private companion object {
        const val READY_TIMEOUT_SECONDS = 10L
    }

    private class AuthExpiredException : IOException()
}

/** 审批传输的最小接口；生产实现是官方 WebSocket/HTTP，测试可替换。 */
interface DshApprovalTransport {
    @Throws(IOException::class)
    fun connect()

    fun answer(eventId: String, outcome: DshApprovalOutcome): Result<Unit>

    fun close()
}
