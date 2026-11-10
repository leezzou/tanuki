package com.tanuki

import com.tanuki.web.DshApiException
import com.tanuki.web.DshEndpoint
import com.tanuki.web.DshModelSelection
import com.tanuki.web.DshSessionAdminClient
import com.tanuki.web.DshSessionAdminProtocol
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList

class DshSessionAdminApiTest {
    @Test fun parsesSessionsWithPermissionAndModelProjections() {
        val result = DshSessionAdminProtocol.parseSessions(
            responseOk(
                "rpc-list",
                JSONObject().put(
                    "items",
                    JSONArray()
                        .put(session("s1", "修复登录", 2_000L, running = true, permission = "workspace-write", model = "deepseek/chat"))
                        .put(session("s2", "", 1_000L, running = false, permission = null, model = null))
                )
            )
        )
        assertEquals(listOf("s1", "s2"), result.map { it.id })
        assertEquals("修复登录", result[0].title)
        assertEquals("workspace-write", result[0].currentPermission)
        assertEquals("deepseek", result[0].model?.provider)
        assertEquals("chat", result[0].model?.model)
        assertTrue(result[0].running)
        assertEquals("会话 s2", result[1].displayTitle)
        assertNull(result[1].model)
    }

    @Test fun parsesSearchModelPermissionSkillAndCommandCatalogs() {
        val search = DshSessionAdminProtocol.parseSearch(
            responseOk("rpc", JSONObject()
                .put("items", JSONArray().put(JSONObject().put("sessionId", "s1").put("snippet", "命中片段")))
                .put("hasMore", true))
        )
        assertEquals("s1", search.items.single().sessionId)
        assertTrue(search.hasMore)

        val models = DshSessionAdminProtocol.parseModelCatalog(
            responseOk("rpc", JSONObject()
                .put("default", JSONObject().put("provider", "deepseek").put("model", "chat"))
                .put("groups", JSONArray().put(JSONObject()
                    .put("id", "deepseek").put("name", "DeepSeek")
                    .put("models", JSONArray().put(JSONObject()
                        .put("id", "chat").put("name", "Chat")
                        .put("reasoning", JSONObject()
                            .put("efforts", JSONArray().put(JSONObject().put("id", "high").put("name", "High")))
                            .put("defaultEffort", "high"))))))
                .put("failures", JSONArray().put(JSONObject()
                    .put("id", "other").put("name", "Other").put("message", "缺少凭据"))))
        )
        assertEquals("deepseek", models.default?.provider)
        assertEquals("high", models.groups.single().models.single().defaultEffort)
        assertEquals("缺少凭据", models.failures.single().message)

        val permissions = DshSessionAdminProtocol.parsePermissionCatalog(
            responseOk("rpc", JSONObject()
                .put("options", JSONArray()
                    .put(JSONObject().put("value", "read-only").put("name", "只读"))
                    .put(JSONObject().put("value", "danger-full-access").put("name", "完全权限")))
                .put("defaultOptions", JSONArray().put(JSONObject().put("value", "read-only").put("name", "只读")))
                .put("defaultPreset", "read-only"))
        )
        assertEquals(listOf("read-only", "danger-full-access"), permissions.options.map { it.value })
        assertEquals("read-only", permissions.defaultPreset)

        val skills = DshSessionAdminProtocol.parseSkills(
            responseOk("rpc", JSONObject().put("skills", JSONArray().put(JSONObject()
                .put("name", "review").put("description", "代码审查").put("modelInvocable", true))))
        )
        assertEquals("review", skills.single().name)
        assertTrue(skills.single().modelInvocable)

        val commands = DshSessionAdminProtocol.parseCommands(
            responseOk("rpc", JSONArray().put(JSONObject()
                .put("name", "compact").put("description", "压缩上下文")
                .put("input", JSONObject().put("hint", "可选说明").put("attachments", true))))
        )
        assertEquals("compact", commands.single().name)
        assertEquals("可选说明", commands.single().inputHint)
        assertTrue(commands.single().acceptsAttachments)
    }

    @Test fun businessErrorKeepsOnlyShortCode() {
        val body = JSONObject()
            .put("type", "server-response")
            .put("rpcId", "rpc")
            .put("result", JSONObject().put("ok", false).put(
                "error",
                JSONObject().put("code", "session/conflict").put("message", "token=should-not-appear")
            ))
            .toString()
        val error = runCatching { DshSessionAdminProtocol.parseSessions(body) }.exceptionOrNull()
        assertTrue(error is DshApiException)
        assertEquals("session/conflict", (error as DshApiException).code)
        assertFalse(error.message.orEmpty().contains("should-not-appear"))
    }

    @Test fun commandExecutionRequiresExplicitSuccess() {
        val success = responseOk("rpc", JSONObject()
            .put("commandId", "c1")
            .put("result", JSONObject().put("kind", "success")))
        assertTrue(DshSessionAdminProtocol.commandAccepted(success))
        val error = responseOk("rpc", JSONObject()
            .put("commandId", "c1")
            .put("result", JSONObject().put("kind", "error").put("text", "denied")))
        assertFalse(DshSessionAdminProtocol.commandAccepted(error))
        assertFalse(DshSessionAdminProtocol.commandAccepted(responseOk("rpc", JSONObject.NULL)))
    }

    @Test fun clientUsesOnlyOfficialMethodsAndKeepsCookieOnApiPaths() {
        val requests = CopyOnWriteArrayList<TestRequest>()
        TestServer { request ->
            requests += request
            when {
                request.path.startsWith("/connect") -> TestResponse(
                    302, listOf("Set-Cookie" to "dsh-session=abc; Path=/; HttpOnly"), ""
                )
                request.path == "/api/session/list" -> TestResponse(
                    200, emptyList(), okFor(request, JSONObject().put("items", JSONArray().put(session("s1", "标题", 1L, true, "read-only", null))))
                )
                request.path == "/api/session/create" -> TestResponse(200, emptyList(), okFor(request, JSONObject().put("sessionId", "s3")))
                request.path == "/api/session/rename" -> TestResponse(200, emptyList(), okFor(request, JSONObject().put("title", "新标题").put("seq", 4)))
                request.path == "/api/workspace/archiveSession" -> TestResponse(200, emptyList(), okFor(request, JSONObject().put("archivedSessionIds", JSONArray().put("s1"))))
                request.path == "/api/session/search" -> TestResponse(200, emptyList(), okFor(request, JSONObject()
                    .put("items", JSONArray().put(JSONObject().put("sessionId", "s1").put("snippet", "片段")))
                    .put("hasMore", false)))
                request.path == "/api/session/modelCatalog" -> TestResponse(200, emptyList(), okFor(request, JSONObject()
                    .put("default", JSONObject().put("provider", "deepseek").put("model", "chat"))
                    .put("groups", JSONArray())))
                request.path == "/api/session/selectModel" -> TestResponse(200, emptyList(), okFor(request, JSONObject()
                    .put("selected", JSONObject().put("provider", "deepseek").put("model", "chat").put("reasoningEffort", "high"))))
                request.path == "/api/permissionPresets/catalog" -> TestResponse(200, emptyList(), okFor(request, JSONObject()
                    .put("options", JSONArray().put(JSONObject().put("value", "read-only").put("name", "只读")))
                    .put("defaultOptions", JSONArray())
                    .put("defaultPreset", "read-only")))
                request.path == "/api/commands/execute" -> TestResponse(200, emptyList(), okFor(request, JSONObject()
                    .put("commandId", "c1").put("result", JSONObject().put("kind", "success"))))
                request.path == "/api/skills/list" -> TestResponse(200, emptyList(), okFor(request, JSONObject()
                    .put("skills", JSONArray().put(JSONObject().put("name", "review").put("description", "审查").put("modelInvocable", true)))))
                request.path == "/api/commands/list" -> TestResponse(200, emptyList(), okFor(request, JSONArray()
                    .put(JSONObject().put("name", "compact").put("description", "压缩"))))
                else -> TestResponse(404, emptyList(), "not found")
            }
        }.use { server ->
            val client = DshSessionAdminClient { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            val sessions = runBlocking { client.sessions() }
            assertTrue(sessions.exceptionOrNull()?.toString(), sessions.isSuccess)
            assertEquals("s1", sessions.getOrThrow().single().id)
            assertEquals("s3", runBlocking { client.createSession() }.getOrThrow())
            assertEquals("新标题", runBlocking { client.renameSession("s1", "新标题") }.getOrThrow())
            assertEquals(listOf("s1"), runBlocking { client.archiveSession("s1", stopActivity = true) }.getOrThrow())
            assertEquals("片段", runBlocking { client.search("登录") }.getOrThrow().items.single().snippet)
            assertEquals("deepseek", runBlocking { client.models() }.getOrThrow().default?.provider)
            assertEquals("high", runBlocking {
                client.selectModel("s1", DshModelSelection("deepseek", "chat", "high"))
            }.getOrThrow().reasoningEffort)
            assertEquals("read-only", runBlocking { client.permissions() }.getOrThrow().defaultPreset)
            assertTrue(runBlocking { client.selectPermission("s1", "read-only") }.isSuccess)
            assertEquals("review", runBlocking { client.skills("s1") }.getOrThrow().single().name)
            assertEquals("compact", runBlocking { client.commands("s1") }.getOrThrow().single().name)
        }

        val connect = requests.first { it.path.startsWith("/connect") }
        assertTrue(connect.target.contains("interactions=none"))
        assertTrue(connect.target.contains("token=top-secret"))
        assertNull(connect.headers["cookie"])
        assertNull(connect.headers["origin"])
        val api = requests.filter { it.path.startsWith("/api/") }
        assertEquals(11, api.size)
        api.forEach {
            assertEquals("dsh-session=abc", it.headers["cookie"])
            assertNull(it.headers["origin"])
            assertFalse(it.body.contains("top-secret"))
        }
        val create = argsOf(api.first { it.path == "/api/session/create" })
        assertEquals(0, create.getJSONObject("request").length())
        val rename = argsOf(api.first { it.path == "/api/session/rename" }).getJSONObject("request")
        assertEquals("s1", rename.getString("sessionId"))
        assertEquals("新标题", rename.getString("title"))
        val archive = argsOf(api.first { it.path == "/api/workspace/archiveSession" }).getJSONObject("request")
        assertEquals(true, archive.getBoolean("stopActivity"))
        val search = argsOf(api.first { it.path == "/api/session/search" }).getJSONObject("request")
        assertEquals("登录", search.getString("query"))
        val selected = argsOf(api.first { it.path == "/api/session/selectModel" }).getJSONObject("request")
        assertEquals("high", selected.getString("reasoningEffort"))
        val execute = argsOf(api.first { it.path == "/api/commands/execute" })
        assertEquals("s1", execute.getString("agentId"))
        assertEquals("/permission read-only", execute.getString("line"))
        assertEquals(0, execute.getJSONArray("submittedAttachments").length())
        val skills = argsOf(api.first { it.path == "/api/skills/list" }).getJSONObject("request")
        assertEquals("s1", skills.getString("sessionId"))
        val commands = argsOf(api.first { it.path == "/api/commands/list" })
        assertEquals("s1", commands.getString("agentId"))
    }

    @Test fun invalidPermissionPresetFailsBeforeNetwork() {
        val requests = CopyOnWriteArrayList<TestRequest>()
        TestServer { request ->
            requests += request
            TestResponse(302, listOf("Set-Cookie" to "dsh-session=abc; Path=/"), "")
        }.use { server ->
            val client = DshSessionAdminClient { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            val result = runBlocking { client.selectPermission("s1", "bad preset") }
            assertTrue(result.isFailure)
            assertTrue(requests.none { it.path == "/api/commands/execute" })
        }
    }

    @Test fun missingEndpointErrorDoesNotLeakToken() {
        TestServer { request ->
            if (request.path.startsWith("/connect")) {
                TestResponse(302, listOf("Set-Cookie" to "dsh-session=abc; Path=/"), "")
            } else {
                TestResponse(404, emptyList(), "not found")
            }
        }.use { server ->
            val client = DshSessionAdminClient { DshEndpoint(server.origin, "${server.origin}/?token=top-secret") }
            val result = runBlocking { client.sessions() }
            assertTrue(result.isFailure)
            val text = result.exceptionOrNull()?.message.orEmpty()
            assertFalse(text.contains("top-secret"))
            assertTrue(text.contains("HTTP 404"))
        }
    }

    private fun session(
        id: String,
        title: String,
        updatedAt: Long,
        running: Boolean,
        permission: String?,
        model: String?
    ): JSONObject {
        val values = JSONObject().put("title", title)
        permission?.let { values.put("permissions", JSONObject().put("currentValue", it)) }
        model?.let {
            val (provider, name) = it.split('/', limit = 2)
            values.put("modelSelection", JSONObject().put("next", JSONObject().put("provider", provider).put("model", name)))
        }
        return JSONObject()
            .put("sessionId", id)
            .put("updatedAt", updatedAt)
            .put("running", running)
            .put("blank", false)
            .put("agentAvailable", true)
            .put("projections", JSONObject().put("kind", "sequenced").put("asOfSeq", 9).put("values", values))
    }

    private fun okFor(request: TestRequest, value: Any): String {
        val rpcId = JSONObject(request.body).getString("rpcId")
        return responseOk(rpcId, value)
    }

    private fun argsOf(request: TestRequest): JSONObject =
        JSONObject(request.body).getJSONObject("payload").getJSONObject("args")

    private fun responseOk(rpcId: String, value: Any): String = JSONObject()
        .put("type", "server-response")
        .put("rpcId", rpcId)
        .put("result", JSONObject().put("ok", true).put("value", value))
        .toString()

    private data class TestRequest(val method: String, val path: String, val target: String, val headers: Map<String, String>, val body: String)

    private data class TestResponse(val status: Int, val headers: List<Pair<String, String>>, val body: String)

    private class TestServer(private val handler: (TestRequest) -> TestResponse) : AutoCloseable {
        private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val origin: String = "http://127.0.0.1:${server.localPort}"
        private val thread = Thread {
            try {
                while (!server.isClosed) {
                    val socket = server.accept()
                    Thread { socket.use { serve(it) } }.apply { isDaemon = true; start() }
                }
            } catch (_: SocketException) {
                // 关闭监听时 accept 抛出，属正常收尾。
            }
        }.apply { isDaemon = true; start() }

        private fun serve(socket: Socket) {
            socket.soTimeout = 5_000
            // HTTP 头/体按字节读取；ISO-8859-1 保证一个字符对应一个字节，避免中文请求体与 Content-Length 错位。
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val headers = LinkedHashMap<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator <= 0) continue
                headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
            }
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            val body = CharArray(length)
            var read = 0
            while (read < length) {
                val count = reader.read(body, read, length - read)
                if (count < 0) break
                read += count
            }
            val target = parts[1]
            val bodyBytes = String(body, 0, read).toByteArray(Charsets.ISO_8859_1)
            val response = handler(TestRequest(parts[0], target.substringBefore('?'), target, headers, bodyBytes.toString(Charsets.UTF_8)))
            val payload = response.body.toByteArray(Charsets.UTF_8)
            val output = socket.getOutputStream()
            val head = buildString {
                append("HTTP/1.1 ${response.status} ${reason(response.status)}\r\n")
                response.headers.forEach { (name, value) -> append("$name: $value\r\n") }
                append("Content-Type: application/json; charset=utf-8\r\n")
                append("Content-Length: ${payload.size}\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(head.toByteArray(Charsets.US_ASCII))
            output.write(payload)
            output.flush()
        }

        private fun reason(status: Int): String = when (status) {
            200 -> "OK"
            302 -> "Found"
            404 -> "Not Found"
            else -> "Response"
        }

        override fun close() {
            server.close()
        }
    }
}
