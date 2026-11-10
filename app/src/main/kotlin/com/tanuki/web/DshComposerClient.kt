package com.tanuki.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** 原生输入栏使用的最小官方 Remote 调用面；测试可替换为合成实现。 */
interface DshComposerRemote {
    suspend fun call(method: String, args: JSONObject): Any?
    fun reset() {}
}

/** 官方命令处理器返回的业务失败；保留可展示文本，但不把请求细节带入日志。 */
class DshCommandException(message: String) : IOException(message)

data class DshUploadedFile(
    val receiptId: String,
    val attachmentId: String,
    val name: String,
    val bytes: Long
)

/**
 * 输入/附件协议客户端。
 *
 * 只调用官方已声明的方法；Cookie 由 [HttpDshComposerRemote] 内存持有，
 * 本类不把 token/Cookie 写进界面状态、异常或请求内容。
 */
class DshComposerClient(private val remote: DshComposerRemote) {
    suspend fun imageLimits(sessionId: String): Result<DshImageLimits> = runCatching {
        DshComposerProtocol.parseImageLimits(
            remote.call("session/projections", DshComposerProtocol.projectionArgs(sessionId))
        )
    }

    suspend fun commands(sessionId: String): Result<List<DshCommand>> = runCatching {
        DshComposerProtocol.parseCommands(
            remote.call("commands/list", DshComposerProtocol.commandsArgs(sessionId))
        )
    }

    suspend fun fileReferences(sessionId: String, query: String): Result<List<DshFileReference>> = runCatching {
        DshComposerProtocol.parseFileReferences(
            remote.call("fileReferences/list", DshComposerProtocol.fileReferencesArgs(sessionId, query)),
            query
        )
    }

    suspend fun sessionReferences(sessionId: String, query: String): Result<List<DshSessionReference>> = runCatching {
        DshComposerProtocol.parseSessionReferences(
            remote.call(
                "sessionReferenceResolver/candidates",
                DshComposerProtocol.sessionReferencesArgs(sessionId, query)
            )
        )
    }

    suspend fun uploadFile(sessionId: String, name: String?, bytes: ByteArray): Result<DshUploadedFile> = runCatching {
        require(bytes.isNotEmpty()) { "文件内容为空" }
        // base64 可能放大到约 27 MB；不要占用调用方主线程。
        val data = withContext(Dispatchers.Default) { DshComposerProtocol.base64(bytes) }
        val value = remote.call("fileUploads/upload", DshComposerProtocol.uploadArgs(sessionId, name, data))
        val root = value as? JSONObject ?: throw IllegalArgumentException("上传响应格式不正确")
        val receiptId = root.optString("receiptId")
        val file = root.optJSONObject("file") ?: throw IllegalArgumentException("上传响应缺少文件信息")
        val attachmentId = file.optString("attachmentId")
        if (receiptId.isBlank() || attachmentId.isBlank()) throw IllegalArgumentException("上传响应缺少文件凭证")
        DshUploadedFile(
            receiptId = receiptId,
            attachmentId = attachmentId,
            name = DshComposerProtocol.sanitizeFileName(file.optString("name")) ?: name ?: "附件",
            bytes = file.optLong("bytes", bytes.size.toLong()).coerceAtLeast(0L)
        )
    }

    suspend fun sendPrompt(
        sessionId: String,
        requestId: String,
        mode: String,
        parts: List<DshOutgoingPart>,
        clientTimeZone: String?
    ): Result<Unit> = runCatching {
        val value = remote.call(
            "session/prompt",
            DshComposerProtocol.promptArgs(requestId, sessionId, mode, parts, clientTimeZone)
        )
        val root = value as? JSONObject ?: throw IllegalArgumentException("发送响应格式不正确")
        if (!root.optBoolean("accepted", false)) throw IllegalArgumentException("官方接口未接受该消息")
    }

    suspend fun executeCommand(
        sessionId: String,
        line: String,
        attachments: List<DshOutgoingPart>
    ): Result<Unit> = runCatching {
        val value = remote.call(
            "commands/execute",
            DshComposerProtocol.commandExecuteArgs(sessionId, line, attachments)
        )
        // 未解析到命令时官方返回 undefined/null；不要伪装成已执行。
        val root = value as? JSONObject ?: throw DshCommandException("未找到该斜杠命令")
        val result = root.optJSONObject("result") ?: throw DshCommandException("斜杠命令返回格式不正确")
        when (result.optString("kind")) {
            "success" -> Unit
            "error" -> throw DshCommandException(
                result.optString("text").take(240).ifBlank { "斜杠命令执行失败" }
            )
            else -> throw DshCommandException("斜杠命令返回格式不正确")
        }
    }

    fun reset() = remote.reset()
}

/**
 * 官方 loopback Remote 的 HTTP 传输。
 *
 * 认证流程与只读页一致：同一启动 token 换 API Cookie，之后只向当前精确
 * loopback origin 的 `/api/<method>` 发送 JSON 信封；401 只在当前代次内重试一次。
 */
class HttpDshComposerRemote(
    private val endpointProvider: () -> DshEndpoint?
) : DshComposerRemote {
    private val stateLock = Any()
    private var cookie: String? = null
    private var boundOrigin: String? = null
    private var boundLaunchUrl: String? = null
    private var authGeneration = 0L

    override fun reset() {
        synchronized(stateLock) {
            authGeneration++
            cookie = null
            boundOrigin = null
            boundLaunchUrl = null
        }
    }

    override suspend fun call(method: String, args: JSONObject): Any? = withContext(Dispatchers.IO) {
        val endpoint = endpointProvider() ?: throw IOException("DSH 服务地址尚未就绪")
        var generation = synchronized(stateLock) { authGeneration }
        var currentCookie = ensureCookie(endpoint, generation)
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
                    generation = beginCookieRefresh(generation)
                    currentCookie = ensureCookie(endpoint, generation)
                    continue
                }
                if (code !in 200..299) throw IOException("DSH 接口返回 HTTP $code")
                synchronized(stateLock) {
                    if (generation != authGeneration) throw IOException("DSH 接口认证已取消")
                }
                return@withContext parseValue(text, rpcId)
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("DSH 接口认证失败")
    }

    /** 成功时返回 result.value；业务失败只抛出短错误码。 */
    private fun parseValue(body: String, rpcId: String): Any? {
        DshApiProtocol.checkEnvelope(body, rpcId)
        val root = JSONObject(body)
        val result = root.optJSONObject("result") ?: throw IllegalArgumentException("DSH 接口响应缺少 result")
        if (!result.optBoolean("ok", false)) {
            val code = result.optJSONObject("error")?.optString("code").orEmpty()
            throw DshApiException(code.takeIf { it.matches(Regex("[A-Za-z0-9/_-]{1,80}")) } ?: "unknown")
        }
        return result.opt("value")?.takeUnless { it == JSONObject.NULL }
    }

    private fun beginCookieRefresh(expectedGeneration: Long): Long = synchronized(stateLock) {
        if (expectedGeneration != authGeneration) throw IOException("DSH 接口认证已取消")
        authGeneration++
        cookie = null
        boundOrigin = null
        boundLaunchUrl = null
        authGeneration
    }

    private fun ensureCookie(endpoint: DshEndpoint, generation: Long): String {
        synchronized(stateLock) {
            if (generation != authGeneration) throw IOException("DSH 接口认证已取消")
            val existing = cookie
            if (existing != null && boundOrigin == endpoint.origin && boundLaunchUrl == endpoint.launchUrl) return existing
        }
        val url = DshApiProtocol.connectUrl(endpoint) ?: throw IOException("DSH 启动地址无效")
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
                if (generation != authGeneration) throw IOException("DSH 接口认证已取消")
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
        // 上传 20 MiB 文件时服务端可能先落盘再响应；读取超时留足但不无限等待。
        connection.readTimeout = 30_000
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
                if (total > DshComposerProtocol.MAX_RESPONSE_BYTES) throw IOException("DSH 接口响应过大")
                output.write(buffer, 0, read)
            }
            return output.toString("UTF-8")
        }
    }
}
