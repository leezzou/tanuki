package com.tanuki.storage

import android.content.Context
import com.tanuki.runtime.FileTransfers
import com.tanuki.runtime.Paths
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONTokener
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** 独立的本机手机文件通道；网页不能调用，写授权仅来自原生界面。 */
class PhoneFileService(private val context: Context, private val paths: Paths, private val transfers: FileTransfers) {
    val access = PhoneFileAccess(context, paths)
    val writeAllowed get() = access.writeAllowed
    @Volatile private var server: ServerSocket? = null
    @Volatile private var generation = 0L
    private var workers: ThreadPoolExecutor? = null
    private val methods = setOf("status", "list", "stat", "read", "write_begin", "write_chunk", "write_commit", "write_cancel", "mkdir", "rename", "delete")

    /** 由运行服务在后台启动；每个DSH运行期生成新连接，默认不允许写入。 */
    @Synchronized fun start() {
        if (server != null) return
        access.setWriteAllowed(false)
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret)
        val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val current = ++generation
        val pool = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(8),
            { task -> Thread(task, "phone-files-worker").apply { isDaemon = true } })
        try { PhoneFileIntegration.publish(context, paths, socket.localPort, token) }
        catch (error: Throwable) { socket.close(); pool.shutdownNow(); throw error }
        workers = pool; server = socket
        Thread({
            while (server === socket && generation == current) {
                val incoming = try { socket.accept() } catch (_: Exception) { break }
                try { pool.execute { incoming.use { handle(it, token, current) } } }
                catch (_: Exception) { incoming.close() }
            }
        }, "phone-files-listener").apply { isDaemon = true; start() }
    }

    @Synchronized fun currentRunId(): Long? = if (server != null) generation else null

    @Synchronized fun setWriteAllowed(enabled: Boolean, expectedRun: Long? = null) {
        if (enabled && (server == null || (expectedRun != null && expectedRun != generation))) return
        access.setWriteAllowed(enabled)
    }

    /** 撤权先于关闭连接；旧请求即使已在队列中，也不能在新连接里提交。 */
    @Synchronized fun stop() {
        access.setWriteAllowed(false)
        generation++
        val old = server; server = null
        runCatching { old?.close() }
        workers?.shutdownNow(); workers = null
        PhoneFileIntegration.disconnect(paths)
    }

    private fun handle(socket: Socket, token: String, session: Long) {
        socket.soTimeout = 10_000
        try {
            val input = socket.getInputStream()
            val request = line(input, 1024)
            val headers = linkedMapOf<String, String>()
            var bytes = request.length
            while (true) {
                val value = line(input, 4096)
                bytes += value.length
                require(bytes <= 8192) { "invalid_request" }
                if (value.isEmpty()) break
                val split = value.indexOf(':'); require(split > 0) { "invalid_request" }
                val key = value.substring(0, split).lowercase(java.util.Locale.ROOT)
                require(!headers.containsKey(key)) { "invalid_request" }
                headers[key] = value.substring(split + 1).trim()
            }
            require(request == "POST /rpc HTTP/1.1" && !headers.containsKey("origin") && !headers.containsKey("transfer-encoding")) { "invalid_request" }
            val authorization = headers["authorization"].orEmpty().toByteArray(Charsets.UTF_8)
            require(MessageDigest.isEqual(authorization, "Bearer $token".toByteArray(Charsets.UTF_8))) { "permission_required" }
            require(generation == session && server != null) { "connection_expired" }
            val length = headers["content-length"]?.toIntOrNull() ?: error("invalid_request")
            require(length in 1..300_000) { "request_too_large" }
            val body = ByteArray(length)
            var received = 0
            while (received < length) {
                val count = input.read(body, received, length - received)
                require(count > 0) { "invalid_request" }; received += count
            }
            require(generation == session && server != null) { "connection_expired" }
            val parser = JSONTokener(body.toString(Charsets.UTF_8))
            val value = parser.nextValue() as? JSONObject ?: error("invalid_request")
            require(parser.nextClean() == '\u0000') { "invalid_request" }
            val method = value.getString("method")
            require(method in methods) { "invalid_method" }
            val params = value.optJSONObject("params") ?: JSONObject()
            val isCurrent = { generation == session && server != null }
            val result = runBlocking {
                if (method in setOf("status", "list", "stat")) access.execute(method, params, isCurrent = isCurrent)
                else transfers.track(if (method.startsWith("write") || method in setOf("mkdir", "rename", "delete")) "DSH 手机文件写入" else "DSH 手机文件读取") { id ->
                    access.execute(method, params, isCurrent = isCurrent) { read, total -> transfers.update(id, read, total) }
                }
            }
            require(generation == session && server != null) { "connection_expired" }
            respond(socket, JSONObject().put("ok", true).put("result", result))
        } catch (error: Exception) {
            // 不返回异常原文或认证信息；仅保留约定的短错误码。
            val code = if (generation != session || server == null) "connection_expired"
                else error.message?.takeIf { it.matches(Regex("[a-z_]{1,64}")) } ?: "file_operation_failed"
            runCatching { respond(socket, JSONObject().put("ok", false).put("error", code)) }
        }
    }

    private fun respond(socket: Socket, json: JSONObject) {
        val data = json.toString().toByteArray(Charsets.UTF_8)
        require(data.size <= 1_048_576)
        val output = socket.getOutputStream()
        output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${data.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n".toByteArray(Charsets.US_ASCII))
        output.write(data); output.flush()
    }

    private fun line(input: InputStream, maximum: Int): String {
        val result = StringBuilder()
        while (true) {
            val value = input.read(); require(value >= 0) { "invalid_request" }
            if (value == 10) return result.toString().removeSuffix("\r")
            require(result.length < maximum && value in 9..126) { "invalid_request" }
            result.append(value.toChar())
        }
    }
}
