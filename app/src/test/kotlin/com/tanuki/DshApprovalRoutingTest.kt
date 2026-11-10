package com.tanuki

import com.tanuki.ui.DshApprovalController
import com.tanuki.web.DshApprovalOutcome
import com.tanuki.web.DshEndpoint
import com.tanuki.web.DshLiveEvent
import com.tanuki.web.DshNativeClient
import com.tanuki.web.DshWebSocketFrames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class DshApprovalRoutingTest {
    @Test fun chatReadyFirstForwardsNextToApprovalPeer() = runBlocking {
        Harness(ReadyOrder.FIRST).use { harness ->
            val chat = harness.startChat()
            val approval = harness.startApproval()
            assertTrue(harness.approvals.state.value.pending.isEmpty())

            // 按最早就绪顺序先交给聊天；只有该 Peer 回 next 才交给审批界面。
            harness.host.requestApproval("chat-first")
            harness.awaitPending("chat-first")
            assertNext(harness.host.results.single(), chat.peer, "chat-first")
            assertEquals(listOf(chat.peer.clientId, approval.clientId), harness.host.deliveriesFor("chat-first"))
            harness.assertPendingRequest("chat-first")
            harness.assertRunning(chat, true)
            harness.decide("chat-first", DshApprovalOutcome.ALLOWED_ONCE)
            assertDecision(harness.host.results.last(), approval, "chat-first", "allowed-once")
            harness.assertRunning(chat, false)
            harness.assertChatResultsAreNext(chat)
            assertEquals(2, harness.host.peers.size)
        }
    }

    @Test fun approvalReadyFirstReceivesRequestAndAnswersWithItsOwnPeer() = runBlocking {
        Harness(ReadyOrder.FIRST).use { harness ->
            val approval = harness.startApproval()
            val chat = harness.startChat()
            harness.host.requestApproval("approval-first")
            harness.awaitPending("approval-first")
            harness.assertPendingRequest("approval-first")
            assertEquals(listOf(approval.clientId), harness.host.deliveriesFor("approval-first"))
            assertTrue(harness.host.results.isEmpty())

            harness.assertRunning(chat, true)
            harness.decide("approval-first", DshApprovalOutcome.REJECTED)
            assertDecision(harness.host.results.single(), approval, "approval-first", "rejected")
            assertTrue(harness.host.results.none { it.cookie == chat.peer.cookie })
            harness.assertRunning(chat, false)
            harness.assertChatResultsAreNext(chat)
            assertEquals(2, harness.host.peers.size)
        }
    }

    @Test fun reconnectedChatPeerUsesNewCookieAndClientIdToForwardNext() = runBlocking {
        Harness(ReadyOrder.NEWEST).use { harness ->
            val approval = harness.startApproval()
            val firstChat = harness.startChat()
            harness.host.requestApproval("before-reconnect")
            harness.awaitPending("before-reconnect")
            harness.assertPendingRequest("before-reconnect")
            assertEquals(listOf(firstChat.peer.clientId, approval.clientId), harness.host.deliveriesFor("before-reconnect"))
            assertNext(harness.host.results.first(), firstChat.peer, "before-reconnect")
            harness.assertRunning(firstChat, true)
            harness.decide("before-reconnect", DshApprovalOutcome.ALLOWED_ONCE)
            assertDecision(harness.host.results.last(), approval, "before-reconnect", "allowed-once")

            // 由服务端关闭真实 socket；同一个客户端再次订阅时不能复用已销毁 Peer 的 Cookie。
            harness.host.disconnect(firstChat.peer)
            withTimeout(5_000) { firstChat.job.join() }
            assertTrue(harness.approvals.state.value.ready)
            val secondChat = harness.startChat()
            assertNotEquals(firstChat.peer.cookie, secondChat.peer.cookie)
            assertNotEquals(firstChat.peer.clientId, secondChat.peer.clientId)
            assertEquals(3, harness.host.peers.size)

            harness.host.requestApproval("after-reconnect")
            harness.awaitPending("after-reconnect")
            harness.assertPendingRequest("after-reconnect")
            assertEquals(listOf(secondChat.peer.clientId, approval.clientId), harness.host.deliveriesFor("after-reconnect"))
            assertNext(harness.host.results[2], secondChat.peer, "after-reconnect")
            harness.assertRunning(secondChat, true)
            harness.decide("after-reconnect", DshApprovalOutcome.REJECTED)
            assertDecision(harness.host.results.last(), approval, "after-reconnect", "rejected")
            harness.assertRunning(secondChat, false)
            harness.assertChatResultsAreNext(firstChat)
            harness.assertChatResultsAreNext(secondChat)
            assertEquals(4, harness.host.results.size)
        }
    }

    private companion object {
        private fun assertNext(result: PostedResult, peer: Peer, eventId: String) {
            assertEquals(peer.cookie, result.cookie)
            assertEquals(peer.clientId, result.clientId)
            assertEquals(eventId, result.eventId)
            assertEquals("next", result.kind)
            assertFalse(result.hasValue)
        }

        private fun assertDecision(result: PostedResult, peer: Peer, eventId: String, value: String) {
            assertEquals(peer.cookie, result.cookie)
            assertEquals(peer.clientId, result.clientId)
            assertEquals(eventId, result.eventId)
            assertEquals("result", result.kind)
            assertEquals(value, result.value)
        }
    }

    private class Harness(order: ReadyOrder) : AutoCloseable {
        val host = FakeHost(order)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val endpoint = { DshEndpoint(host.origin, "${host.origin}/?token=synthetic-routing-token") }
        private val client = DshNativeClient(endpointProvider = endpoint)
        val approvals = DshApprovalController(endpointProvider = endpoint, scope = scope)
        private val chats = CopyOnWriteArrayList<Chat>()

        suspend fun startChat(): Chat {
            val previousPeers = host.peers.size
            val events = CopyOnWriteArrayList<DshLiveEvent>()
            val job = scope.launch { client.live("session-1").collect { events += it } }
            val chat = Chat(events, job)
            chats += chat
            await { host.peers.size > previousPeers && host.peers.last().followStream != null &&
                events.any { it is DshLiveEvent.Snapshot } }
            chat.peer = host.peers.last()
            assertEquals(listOf("\$events", "session/follow"), chat.peer.opens.toList())
            assertTrue(events.any { it is DshLiveEvent.Connected })
            return chat
        }

        suspend fun startApproval(): Peer {
            approvals.start()
            await { approvals.state.value.ready }
            val peer = host.peers.last()
            assertEquals(listOf("\$events"), peer.opens.toList())
            assertNotEquals(peer.cookie, chats.firstOrNull()?.peer?.cookie)
            assertNotEquals(peer.clientId, chats.firstOrNull()?.peer?.clientId)
            return peer
        }

        suspend fun awaitPending(eventId: String) {
            await { approvals.state.value.pending.any { it.request.eventId == eventId } }
        }

        fun assertPendingRequest(eventId: String) {
            val item = approvals.state.value.pending.single()
            assertEquals(eventId, item.request.eventId)
            assertEquals("session-1", item.request.sessionId)
            assertEquals("bash", item.request.toolName)
            assertEquals("合成审批请求", item.request.reason)
            assertNull(item.request.callId)
            assertFalse(item.lookupPending)
            assertFalse(item.answering)
            assertNull(item.error)
            assertNull(approvals.state.value.error)
        }

        suspend fun decide(eventId: String, outcome: DshApprovalOutcome) {
            approvals.answer(eventId, outcome)
            await { approvals.state.value.pending.isEmpty() }
            assertNull(approvals.state.value.error)
            host.assertHealthy()
        }

        suspend fun assertRunning(chat: Chat, running: Boolean) {
            val previous = chat.events.filterIsInstance<DshLiveEvent.Running>().size
            host.emitStatus(running)
            await { chat.events.filterIsInstance<DshLiveEvent.Running>().size > previous }
            assertEquals(running, chat.events.filterIsInstance<DshLiveEvent.Running>().last().running)
            assertTrue(chat.events.filterIsInstance<DshLiveEvent.Failure>().toString(),
                chat.events.none { it is DshLiveEvent.Failure })
        }

        fun assertChatResultsAreNext(chat: Chat) {
            host.results.filter { it.cookie == chat.peer.cookie }.forEach {
                assertNext(it, chat.peer, it.eventId)
            }
            assertTrue(chat.events.filterIsInstance<DshLiveEvent.Failure>().toString(),
                chat.events.none { it is DshLiveEvent.Failure })
        }

        suspend fun await(condition: () -> Boolean) {
            withTimeout(5_000) {
                while (!condition()) {
                    host.assertHealthy()
                    delay(10)
                }
            }
            host.assertHealthy()
        }

        override fun close() {
            try {
                approvals.close()
                runBlocking { chats.forEach { it.job.cancelAndJoin() } }
                client.reset()
                scope.cancel()
            } finally {
                host.close()
            }
        }
    }

    private class Chat(val events: CopyOnWriteArrayList<DshLiveEvent>, val job: Job) {
        lateinit var peer: Peer
    }

    private enum class ReadyOrder { FIRST, NEWEST }
    private data class PostedResult(
        val cookie: String,
        val clientId: String,
        val eventId: String,
        val kind: String,
        val hasValue: Boolean,
        val value: String?
    )
    private data class Delivery(val eventId: String, val clientId: String)
    private class Waterfall(val eventId: String) {
        val visited = HashSet<String>()
        var owner: Peer? = null
    }

    private class Peer(val cookie: String, val clientId: String) {
        val opens = CopyOnWriteArrayList<String>()
        @Volatile var active = true
        @Volatile var readyOrder = 0
        @Volatile var eventsStream: String? = null
        @Volatile var followStream: String? = null
        lateinit var socket: Socket
        private val outputLock = Any()

        fun item(streamId: String, value: JSONObject) {
            frame(1, JSONObject().put("type", "item").put("streamId", streamId).put("value", value)
                .toString().toByteArray(Charsets.UTF_8))
        }

        fun frame(opcode: Int, bytes: ByteArray) = synchronized(outputLock) {
            check(bytes.size <= 0xffff) { "测试帧过大" }
            val header = ByteArrayOutputStream().apply {
                write(0x80 or opcode)
                if (bytes.size <= 125) write(bytes.size) else {
                    write(126)
                    write(bytes.size ushr 8)
                    write(bytes.size and 0xff)
                }
            }
            socket.getOutputStream().apply {
                write(header.toByteArray())
                write(bytes)
                flush()
            }
        }
    }

    /** 真实回环 HTTP/WS Host；审批只交给当前回答者，收到 next 后才推进 waterfall。 */
    private class FakeHost(private val order: ReadyOrder) : AutoCloseable {
        private val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val origin = "http://127.0.0.1:${server.localPort}"
        val peers = CopyOnWriteArrayList<Peer>()
        val results = CopyOnWriteArrayList<PostedResult>()
        private val deliveries = CopyOnWriteArrayList<Delivery>()
        private val pending = LinkedHashMap<String, Waterfall>()
        private val routingLock = Any()
        private val sequence = AtomicInteger()
        private val readiness = AtomicInteger()
        private val failure = AtomicReference<Throwable?>()
        private val closing = AtomicBoolean()
        private val sockets = CopyOnWriteArrayList<Socket>()
        private val workers = Executors.newCachedThreadPool { task ->
            Thread(task, "approval-routing-host-worker").apply { isDaemon = true }
        }
        private val acceptThread = thread(name = "approval-routing-host", isDaemon = true) {
            try {
                while (!closing.get()) {
                    val socket = server.accept()
                    sockets += socket
                    workers.execute {
                        try {
                            socket.use { serve(it) }
                        } catch (_: SocketException) {
                            // 客户端取消或 Host 关闭会结束正在读取的连接。
                        } catch (_: EOFException) {
                            // 客户端关闭时允许帧读取提前结束。
                        } catch (error: Throwable) {
                            failure.compareAndSet(null, error)
                        } finally {
                            sockets -= socket
                        }
                    }
                }
            } catch (error: Throwable) {
                if (!closing.get()) failure.compareAndSet(null, error)
            }
        }

        fun requestApproval(eventId: String) = synchronized(routingLock) {
            check(!pending.containsKey(eventId))
            val waterfall = Waterfall(eventId)
            pending[eventId] = waterfall
            advance(waterfall)
        }

        fun deliveriesFor(eventId: String): List<String> =
            deliveries.filter { it.eventId == eventId }.map { it.clientId }

        fun emitStatus(running: Boolean) = synchronized(routingLock) {
            peers.filter { it.active && it.eventsStream != null }.forEach { peer ->
                peer.item(peer.eventsStream!!, JSONObject().put("type", "emit")
                    .put("event", "api-session/status").put("args", JSONArray().put("session-1").put(running)))
            }
        }

        fun disconnect(peer: Peer) = synchronized(routingLock) {
            check(peer.followStream != null) { "只能断开聊天 Peer" }
            peer.active = false
            peer.frame(8, byteArrayOf(0x03, 0xe8.toByte()))
        }

        fun assertHealthy() {
            failure.get()?.let { throw AssertionError("回环 Host 协议校验失败", it) }
        }

        private fun advance(waterfall: Waterfall) {
            val candidates = peers.filter { it.active && it.readyOrder > 0 && it.clientId !in waterfall.visited }
            val next = when (order) {
                ReadyOrder.FIRST -> candidates.minByOrNull { it.readyOrder }
                ReadyOrder.NEWEST -> candidates.maxByOrNull { it.readyOrder }
            }
            check(next != null) { "waterfall 未能到达审批 Peer" }
            waterfall.owner = next
            waterfall.visited += next.clientId
            deliveries += Delivery(waterfall.eventId, next.clientId)
            // 不带 callId，直接验证审批路由，不触发工具历史查询。
            next.item(next.eventsStream!!, JSONObject().put("type", "waterfall")
                .put("event", "approval/request").put("eventId", waterfall.eventId).put("agentId", "session-1")
                .put("request", JSONObject().put("toolName", "bash").put("reason", "合成审批请求")))
        }

        private fun serve(socket: Socket) {
            socket.soTimeout = 5_000
            val input = BufferedInputStream(socket.getInputStream())
            val request = readHead(input)
            when {
                request.target.startsWith("/connect?") -> {
                    check(request.method == "GET")
                    val query = request.target.substringAfter('?').split('&').associate {
                        it.substringBefore('=') to it.substringAfter('=')
                    }
                    check(query["token"] == "synthetic-routing-token") { "只接受合成 token" }
                    check(query["interactions"] == "answer") { "聊天和审批都必须创建 answer Peer" }
                    val id = sequence.incrementAndGet()
                    val peer = Peer("dsh=routing-cookie-$id", "routing-client-$id")
                    peers += peer
                    respond(socket, 302, "", listOf("Set-Cookie: ${peer.cookie}; Path=/; HttpOnly", "Location: /"))
                }
                request.target == "/api/remote.mux" -> serveMux(socket, input, request)
                request.target == "/api/\$events/result" -> serveResult(socket, input, request)
                else -> error("出现预期外的接口：${request.target.substringBefore('?')}")
            }
        }

        private fun serveMux(socket: Socket, input: InputStream, request: Head) {
            val peer = peers.singleOrNull { it.cookie == request.headers["cookie"] && it.active }
            if (peer == null) {
                respond(socket, 403, "")
                error("WebSocket 不能复用已销毁 Peer 的 Cookie")
            }
            check(request.method == "GET")
            val key = request.headers.getValue("sec-websocket-key")
            peer.socket = socket
            socket.getOutputStream().apply {
                write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: ${DshWebSocketFrames.accept(key)}\r\n\r\n").toByteArray(Charsets.US_ASCII))
                flush()
            }
            socket.soTimeout = 0
            try {
                while (true) {
                    val frame = readClientFrame(input) ?: break
                    if (frame.opcode == 8) break
                    check(frame.opcode == 1) { "只接受客户端文本帧或关闭帧" }
                    val open = JSONObject(frame.payload.toString(Charsets.UTF_8))
                    if (open.optString("type") == "cancel") continue
                    check(open.getString("type") == "open") { "回答必须通过 HTTP POST" }
                    val endpoint = open.getString("endpoint")
                    val streamId = open.getString("streamId")
                    peer.opens += endpoint
                    synchronized(routingLock) {
                        when (endpoint) {
                            "\$events" -> {
                                check(peer.eventsStream == null)
                                check(open.getJSONObject("payload").getJSONObject("args").length() == 0)
                                peer.eventsStream = streamId
                                peer.item(streamId, JSONObject().put("type", "ready").put("clientId", peer.clientId))
                                peer.readyOrder = readiness.incrementAndGet()
                            }
                            "session/follow" -> {
                                val address = open.getJSONObject("payload").getJSONObject("args")
                                    .getJSONObject("request").getJSONObject("address")
                                check(address.getString("kind") == "session")
                                check(address.getString("sessionId") == "session-1")
                                peer.followStream = streamId
                                peer.item(streamId, JSONObject().put("type", "snapshot")
                                    .put("hasMore", false).put("records", JSONArray()))
                            }
                            else -> error("出现预期外的逻辑流")
                        }
                    }
                }
            } finally {
                peer.active = false
            }
        }

        private fun serveResult(socket: Socket, input: InputStream, request: Head) {
            check(request.method == "POST")
            val length = request.headers.getValue("content-length").toInt()
            check(length in 1..16_384) { "请求体大小无效" }
            val body = readBytes(input, length).toString(Charsets.UTF_8)
            check(!body.contains("synthetic-routing-token")) { "回答不应包含启动 token" }
            val envelope = JSONObject(body)
            check(envelope.getString("type") == "client-request")
            check(envelope.getString("method") == "\$events/result")
            val args = envelope.getJSONObject("payload").getJSONObject("args")
            val outcome = args.getJSONObject("outcome")
            val result = PostedResult(request.headers["cookie"].orEmpty(), args.getString("clientId"),
                args.getString("eventId"), outcome.getString("kind"), outcome.has("value"),
                if (outcome.has("value")) outcome.getString("value") else null)
            results += result
            synchronized(routingLock) {
                val peer = peers.singleOrNull { it.cookie == result.cookie && it.active }
                val waterfall = pending[result.eventId]
                // Cookie、ready clientId、当前 waterfall 所属 Peer 三者必须一致。
                if (peer == null || peer.clientId != result.clientId || waterfall == null || waterfall.owner !== peer) {
                    respond(socket, 403, "")
                    error("回答的 Cookie、clientId 或 waterfall 归属不匹配")
                }
                when (result.kind) {
                    "next" -> {
                        check(!result.hasValue) { "next 不能夹带审批决定" }
                        advance(waterfall)
                    }
                    "result" -> {
                        check(result.value in listOf("allowed-once", "rejected"))
                        pending.remove(result.eventId)
                    }
                    else -> error("出现预期外的 waterfall 结果")
                }
            }
            respond(socket, 200, JSONObject().put("type", "server-response")
                .put("rpcId", envelope.getString("rpcId"))
                .put("result", JSONObject().put("ok", true).put("value", JSONObject.NULL)).toString())
        }

        override fun close() {
            closing.set(true)
            server.close()
            sockets.forEach { runCatching { it.close() } }
            workers.shutdownNow()
            acceptThread.join(1_000)
            assertTrue("回环 Host 工作线程未退出", workers.awaitTermination(2, TimeUnit.SECONDS))
            assertHealthy()
        }

        private data class Head(val method: String, val target: String, val headers: Map<String, String>)
        private data class ClientFrame(val opcode: Int, val payload: ByteArray)

        private fun readHead(input: InputStream): Head {
            val bytes = ByteArrayOutputStream()
            while (bytes.size() < 8192) {
                bytes.write(readByte(input))
                val data = bytes.toByteArray()
                if (data.size >= 4 && data.takeLast(4) == listOf<Byte>(13, 10, 13, 10)) {
                    val lines = bytes.toString("US-ASCII").split("\r\n")
                    val request = lines.first().split(' ')
                    val headers = lines.drop(1).filter { ':' in it }.associate {
                        it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim()
                    }
                    return Head(request[0], request[1], headers)
                }
            }
            error("HTTP 请求头过大")
        }

        private fun readClientFrame(input: InputStream): ClientFrame? {
            val first = input.read()
            if (first < 0) return null
            val second = readByte(input)
            check(first and 0x80 != 0 && second and 0x80 != 0) { "客户端帧必须完整且带掩码" }
            var length = second and 0x7f
            check(length != 127) { "客户端帧过大" }
            if (length == 126) length = (readByte(input) shl 8) or readByte(input)
            check(length in 0..16_384) { "客户端帧过大" }
            val mask = readBytes(input, 4)
            val payload = readBytes(input, length)
            return ClientFrame(first and 0x0f, ByteArray(length) {
                (payload[it].toInt() xor mask[it % 4].toInt()).toByte()
            })
        }

        private fun readBytes(input: InputStream, length: Int): ByteArray {
            val bytes = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val count = input.read(bytes, offset, length - offset)
                if (count < 0) throw EOFException("测试请求提前结束")
                offset += count
            }
            return bytes
        }

        private fun readByte(input: InputStream): Int = input.read().also {
            if (it < 0) throw EOFException("测试请求提前结束")
        }

        private fun respond(socket: Socket, status: Int, body: String, headers: List<String> = emptyList()) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            socket.getOutputStream().apply {
                write(buildString {
                    append("HTTP/1.1 $status Test\r\n")
                    headers.forEach { append(it).append("\r\n") }
                    append("Content-Type: application/json\r\nContent-Length: ${bytes.size}\r\n")
                    append("Connection: close\r\n\r\n")
                }.toByteArray(Charsets.US_ASCII))
                write(bytes)
                flush()
            }
        }
    }
}
