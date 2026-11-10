package com.tanuki.web

import kotlinx.coroutines.Dispatchers
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

/** 原生会话管理展示的一行官方会话摘要；字段直接来自 session/list 与 projection。 */
data class DshAdminSession(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val running: Boolean,
    val blank: Boolean,
    val agentAvailable: Boolean,
    val asOfSeq: Int?,
    val currentPermission: String?,
    val model: DshModelSelection?,
    val cwd: String?,
    val parentSessionId: String?,
    val subagent: Boolean,
    val formatStatus: String?
) {
    val displayTitle: String get() = title.ifBlank { "会话 ${id.take(8)}" }
}

/** 官方模型路由；reasoningEffort 只在该模型声明推理强度时出现。 */
data class DshModelSelection(
    val provider: String,
    val model: String,
    val reasoningEffort: String? = null
) {
    val label: String get() = if (provider.isBlank()) model else "$provider / $model"
}

data class DshReasoningEffort(val id: String, val name: String, val description: String?)

data class DshCatalogModel(
    val id: String,
    val name: String,
    val description: String?,
    val efforts: List<DshReasoningEffort>,
    val defaultEffort: String?
)

data class DshModelGroup(val id: String, val name: String, val models: List<DshCatalogModel>)

data class DshModelFailure(val id: String, val name: String, val message: String)

data class DshModelCatalog(
    val default: DshModelSelection?,
    val groups: List<DshModelGroup>,
    val failures: List<DshModelFailure>
)

data class DshPermissionOption(val value: String, val name: String, val description: String?)

data class DshPermissionCatalog(
    val options: List<DshPermissionOption>,
    val defaultOptions: List<DshPermissionOption>,
    val defaultPreset: String
)

/** 官方 session/search 的一条命中；snippet 由官方生成，原生只展示不二次拼接。 */
data class DshSessionSearchHit(val sessionId: String, val snippet: String)

data class DshSessionSearchResult(val items: List<DshSessionSearchHit>, val hasMore: Boolean)

/** 官方 skills/list 的一条技能；available 由目录返回即表示当前会话可用。 */
data class DshSkill(
    val name: String,
    val description: String,
    val whenToUse: String?,
    val modelInvocable: Boolean,
    val path: String?
)

/** 官方 commands/list 的一条斜杠命令；只做目录展示，不在本切片执行任意命令。 */
data class DshCommand(
    val name: String,
    val description: String,
    val inputHint: String?,
    val acceptsAttachments: Boolean
)

/**
 * 官方 DSH `0.2.1-rc.5` 非聊天一元 Remote 的纯协议解析。
 *
 * 只接受官方信封 `server-response`，业务错误只保留短错误码；会话标题、片段、
 * 模型与技能字段都来自响应本身，不生成占位会话或本地索引。
 */
object DshSessionAdminProtocol {
    private val CODE = Regex("[A-Za-z0-9/_-]{1,80}")

    fun parseSessions(body: String): List<DshAdminSession> {
        val value = successValue(body).optJSONObject("value") ?: throw IllegalArgumentException("会话列表响应格式不正确")
        return parseSessionsValue(value)
    }

    fun parseSessionsValue(value: JSONObject): List<DshAdminSession> {
        val items = value.optJSONArray("items") ?: JSONArray()
        val result = ArrayList<DshAdminSession>(items.length())
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("sessionId").trim()
            if (id.isBlank() || id.length > 128) continue
            val projections = item.optJSONObject("projections")
            val values = projections?.optJSONObject("values")
            result += DshAdminSession(
                id = id,
                title = values?.stringOrNull("title").orEmpty().take(300),
                updatedAt = item.optLong("updatedAt", 0L),
                running = item.optBoolean("running", false),
                blank = item.optBoolean("blank", false),
                agentAvailable = item.optBoolean("agentAvailable", false),
                asOfSeq = projections?.takeIf { it.has("asOfSeq") }?.optInt("asOfSeq", -1)?.takeIf { it >= 0 },
                currentPermission = values?.optJSONObject("permissions")?.stringOrNull("currentValue"),
                model = parseSelection(values?.optJSONObject("modelSelection")?.optJSONObject("next"))
                    ?: parseSelection(values?.optJSONObject("modelSelection")?.optJSONObject("lastUsed")),
                cwd = item.stringOrNull("cwd"),
                parentSessionId = item.stringOrNull("parentSessionId"),
                subagent = item.optString("origin") == "subagent",
                formatStatus = item.stringOrNull("formatStatus")
            )
        }
        return result.sortedByDescending { it.updatedAt }
    }

    fun parseCreatedSessionId(body: String): String {
        val value = successValue(body).optJSONObject("value") ?: throw IllegalArgumentException("新建会话响应格式不正确")
        val id = value.optString("sessionId").trim()
        require(id.isNotBlank() && id.length <= 128 && id.all { it.code in 0x21..0x7e }) { "官方未返回有效会话标识" }
        return id
    }

    fun parseRenamedTitle(body: String): String {
        val value = successValue(body).optJSONObject("value") ?: throw IllegalArgumentException("重命名响应格式不正确")
        val title = value.optString("title").trim()
        require(title.isNotEmpty()) { "官方未返回有效标题" }
        return title
    }

    fun parseArchivedSessionIds(body: String): List<String> {
        val value = successValue(body).optJSONObject("value") ?: throw IllegalArgumentException("归档响应格式不正确")
        val ids = value.optJSONArray("archivedSessionIds") ?: JSONArray()
        return buildList {
            for (index in 0 until ids.length()) {
                ids.optString(index).trim().takeIf { it.isNotBlank() && it.length <= 128 }?.let(::add)
            }
        }
    }

    fun parseSearch(body: String): DshSessionSearchResult {
        val value = successValue(body).optJSONObject("value") ?: throw IllegalArgumentException("搜索响应格式不正确")
        val items = value.optJSONArray("items") ?: JSONArray()
        val hits = ArrayList<DshSessionSearchHit>(items.length())
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("sessionId").trim()
            if (id.isBlank() || id.length > 128) continue
            hits += DshSessionSearchHit(id, item.optString("snippet").take(1000))
        }
        return DshSessionSearchResult(hits, value.optBoolean("hasMore", false))
    }

    fun parseModelCatalog(body: String): DshModelCatalog {
        val value = successValue(body).optJSONObject("value") ?: throw IllegalArgumentException("模型目录响应格式不正确")
        val groups = ArrayList<DshModelGroup>()
        val groupArray = value.optJSONArray("groups") ?: JSONArray()
        for (index in 0 until groupArray.length()) {
            val group = groupArray.optJSONObject(index) ?: continue
            val models = ArrayList<DshCatalogModel>()
            val modelArray = group.optJSONArray("models") ?: JSONArray()
            for (modelIndex in 0 until modelArray.length()) {
                val model = modelArray.optJSONObject(modelIndex) ?: continue
                val id = model.optString("id").trim()
                if (id.isBlank()) continue
                val reasoning = model.optJSONObject("reasoning")
                val efforts = ArrayList<DshReasoningEffort>()
                val effortArray = reasoning?.optJSONArray("efforts") ?: JSONArray()
                for (effortIndex in 0 until effortArray.length()) {
                    val effort = effortArray.optJSONObject(effortIndex) ?: continue
                    val effortId = effort.optString("id").trim()
                    if (effortId.isBlank()) continue
                    efforts += DshReasoningEffort(
                        id = effortId,
                        name = effort.stringOrNull("name") ?: effortId,
                        description = effort.stringOrNull("description")
                    )
                }
                models += DshCatalogModel(
                    id = id,
                    name = model.stringOrNull("name") ?: id,
                    description = model.stringOrNull("description"),
                    efforts = efforts,
                    defaultEffort = reasoning?.stringOrNull("defaultEffort")
                )
            }
            if (models.isNotEmpty()) {
                groups += DshModelGroup(
                    id = group.optString("id").trim(),
                    name = group.stringOrNull("name") ?: group.optString("id").trim(),
                    models = models
                )
            }
        }
        val failures = ArrayList<DshModelFailure>()
        val failureArray = value.optJSONArray("failures") ?: JSONArray()
        for (index in 0 until failureArray.length()) {
            val failure = failureArray.optJSONObject(index) ?: continue
            failures += DshModelFailure(
                id = failure.optString("id").trim(),
                name = failure.stringOrNull("name") ?: failure.optString("id").trim(),
                message = failure.stringOrNull("message").orEmpty().take(300)
            )
        }
        return DshModelCatalog(
            default = parseSelection(value.optJSONObject("default")),
            groups = groups,
            failures = failures
        )
    }

    fun parseSelectedModel(body: String): DshModelSelection {
        val value = successValue(body).optJSONObject("value") ?: throw IllegalArgumentException("模型选择响应格式不正确")
        return parseSelection(value.optJSONObject("selected"))
            ?: throw IllegalArgumentException("官方未返回已选模型")
    }

    fun parsePermissionCatalog(body: String): DshPermissionCatalog {
        val value = successValue(body).optJSONObject("value") ?: throw IllegalArgumentException("权限目录响应格式不正确")
        return DshPermissionCatalog(
            options = parsePermissionOptions(value.optJSONArray("options")),
            defaultOptions = parsePermissionOptions(value.optJSONArray("defaultOptions")),
            defaultPreset = value.optString("defaultPreset").trim()
        )
    }

    fun parseSkills(body: String): List<DshSkill> {
        val value = successValue(body).optJSONObject("value") ?: throw IllegalArgumentException("技能目录响应格式不正确")
        val skills = value.optJSONArray("skills") ?: JSONArray()
        val result = ArrayList<DshSkill>(skills.length())
        for (index in 0 until skills.length()) {
            val skill = skills.optJSONObject(index) ?: continue
            val name = skill.optString("name").trim()
            if (name.isBlank() || name.length > 128) continue
            result += DshSkill(
                name = name,
                description = skill.optString("description").take(1000),
                whenToUse = skill.stringOrNull("whenToUse")?.take(1000),
                modelInvocable = skill.optBoolean("modelInvocable", false),
                path = skill.stringOrNull("path")
            )
        }
        return result
    }

    fun parseCommands(body: String): List<DshCommand> {
        val array = successValue(body).optJSONArray("value") ?: JSONArray()
        val result = ArrayList<DshCommand>(array.length())
        for (index in 0 until array.length()) {
            val command = array.optJSONObject(index) ?: continue
            val name = command.optString("name").trim()
            if (name.isBlank() || name.length > 64 || name.any { it.isWhitespace() || it == '/' }) continue
            val input = command.optJSONObject("input")
            result += DshCommand(
                name = name,
                description = command.optString("description").take(1000),
                inputHint = input?.stringOrNull("hint")?.take(200),
                acceptsAttachments = input?.optBoolean("attachments", false) == true
            )
        }
        return result
    }

    /** transport 边界返回 `result.value`，这里重新包装成现有解析器使用的官方信封。 */
    fun successEnvelope(value: Any?): String = JSONObject()
        .put("type", "server-response")
        .put("rpcId", "transport")
        .put("result", JSONObject().put("ok", true).put("value", value ?: JSONObject.NULL))
        .toString()

    /** 从官方 HTTP 信封中取出 `result.value`；业务错误仍抛短错误码。 */
    fun resultValue(body: String): Any? {
        val result = successValue(body)
        return if (result.has("value")) result.opt("value") else null
    }

    /** `commands/execute` 只有在命令真实返回 success 时才算成功；缺 value 表示未匹配命令。 */
    fun commandAccepted(body: String): Boolean {
        val result = successValue(body)
        val value = result.optJSONObject("value") ?: return false
        return value.optJSONObject("result")?.optString("kind") == "success"
    }

    private fun parsePermissionOptions(array: JSONArray?): List<DshPermissionOption> {
        if (array == null) return emptyList()
        val result = ArrayList<DshPermissionOption>(array.length())
        for (index in 0 until array.length()) {
            val option = array.optJSONObject(index) ?: continue
            val value = option.optString("value").trim()
            if (value.isBlank() || value.length > 64) continue
            result += DshPermissionOption(
                value = value,
                name = (option.stringOrNull("name") ?: value).take(200),
                description = option.stringOrNull("description")?.take(1000)
            )
        }
        return result
    }

    private fun parseSelection(value: JSONObject?): DshModelSelection? {
        if (value == null) return null
        val provider = value.optString("provider").trim().take(200)
        val model = value.optString("model").trim().take(200)
        if (provider.isBlank() || model.isBlank()) return null
        return DshModelSelection(provider, model, value.stringOrNull("reasoningEffort")?.take(64))
    }

    private fun successValue(body: String): JSONObject {
        val root = JSONObject(body)
        if (root.optString("type") != "server-response") throw IllegalArgumentException("DSH 接口响应类型不正确")
        val result = root.optJSONObject("result") ?: throw IllegalArgumentException("DSH 接口响应缺少 result")
        if (!result.optBoolean("ok", false)) {
            val code = result.optJSONObject("error")?.optString("code").orEmpty()
            throw DshApiException(code.takeIf { CODE.matches(it) } ?: "unknown")
        }
        return result
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}

/** 会话管理页依赖的官方数据源；生产实现走 loopback 一元 Remote，测试可替换。 */
interface DshSessionAdminSource {
    suspend fun sessions(): Result<List<DshAdminSession>>
    suspend fun createSession(): Result<String>
    suspend fun renameSession(sessionId: String, title: String): Result<String>
    suspend fun archiveSession(sessionId: String, stopActivity: Boolean): Result<List<String>>
    suspend fun search(query: String): Result<DshSessionSearchResult>
    suspend fun models(): Result<DshModelCatalog>
    suspend fun selectModel(sessionId: String, selection: DshModelSelection): Result<DshModelSelection>
    suspend fun permissions(): Result<DshPermissionCatalog>
    suspend fun selectPermission(sessionId: String, preset: String): Result<Unit>
    suspend fun skills(sessionId: String): Result<List<DshSkill>>
    suspend fun commands(sessionId: String): Result<List<DshCommand>>
    fun reset() {}
}

/** 临时一元 transport 边界；42 的 `DshTransport` 落地后由适配器替换网络实现。 */
interface DshAdminTransport {
    /** 返回官方 `result.value`；业务错误抛 `DshApiException`，认证/网络失败抛 `IOException`。 */
    suspend fun call(method: String, args: JSONObject): Any?

    fun reset()
}

/**
 * 把任意 call/reset 实现适配为 [DshAdminTransport]。
 *
 * 42 的 transport 冻结后可直接接线，不复制认证/Cookie/URL：
 * `DshAdminTransportAdapter(transport::call, transport::reset)`。
 */
class DshAdminTransportAdapter(
    private val callImpl: suspend (String, JSONObject) -> Any?,
    private val resetImpl: () -> Unit = {}
) : DshAdminTransport {
    override suspend fun call(method: String, args: JSONObject): Any? = callImpl(method, args)
    override fun reset() = resetImpl()
}

/**
 * 官方会话管理一元客户端。
 *
 * 只负责官方 method/参数构造、响应解析和中文错误；网络、认证、Cookie 与超时
 * 全部由 [DshAdminTransport] 提供。当前生产实现是临时 HTTP transport，
 * 待 42 的 `DshTransport` 合并后改为适配器注入。
 */
class DshSessionAdminClient(private val transport: DshAdminTransport) : DshSessionAdminSource {
    override fun reset() = transport.reset()

    override suspend fun sessions(): Result<List<DshAdminSession>> = withContext(Dispatchers.IO) {
        runCatching {
            DshSessionAdminProtocol.parseSessions(
                call("session/list", JSONObject().put("_request", JSONObject()))
            )
        }
    }

    override suspend fun createSession(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            DshSessionAdminProtocol.parseCreatedSessionId(
                call("session/create", JSONObject().put("request", JSONObject()))
            )
        }
    }

    override suspend fun renameSession(sessionId: String, title: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(sessionId.isNotBlank()) { "会话标识为空" }
            require(title.isNotBlank()) { "标题不能为空" }
            DshSessionAdminProtocol.parseRenamedTitle(
                call("session/rename", JSONObject().put("request", JSONObject()
                    .put("sessionId", sessionId)
                    .put("title", title)))
            )
        }
    }

    override suspend fun archiveSession(sessionId: String, stopActivity: Boolean): Result<List<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(sessionId.isNotBlank()) { "会话标识为空" }
                DshSessionAdminProtocol.parseArchivedSessionIds(
                    call("workspace/archiveSession", JSONObject().put("request", JSONObject()
                        .put("sessionId", sessionId)
                        .put("stopActivity", stopActivity)))
                )
            }
        }

    override suspend fun search(query: String): Result<DshSessionSearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val text = query.trim()
            require(text.isNotEmpty()) { "搜索内容为空" }
            DshSessionAdminProtocol.parseSearch(
                call("session/search", JSONObject().put("request", JSONObject().put("query", text)))
            )
        }
    }

    override suspend fun models(): Result<DshModelCatalog> = withContext(Dispatchers.IO) {
        runCatching { DshSessionAdminProtocol.parseModelCatalog(call("session/modelCatalog", JSONObject())) }
    }

    override suspend fun selectModel(sessionId: String, selection: DshModelSelection): Result<DshModelSelection> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(sessionId.isNotBlank()) { "会话标识为空" }
                require(selection.provider.isNotBlank() && selection.model.isNotBlank()) { "模型标识为空" }
                val request = JSONObject()
                    .put("sessionId", sessionId)
                    .put("provider", selection.provider)
                    .put("model", selection.model)
                selection.reasoningEffort?.takeIf { it.isNotBlank() }?.let { request.put("reasoningEffort", it) }
                DshSessionAdminProtocol.parseSelectedModel(
                    call("session/selectModel", JSONObject().put("request", request))
                )
            }
        }

    override suspend fun permissions(): Result<DshPermissionCatalog> = withContext(Dispatchers.IO) {
        runCatching { DshSessionAdminProtocol.parsePermissionCatalog(call("permissionPresets/catalog", JSONObject())) }
    }

    override suspend fun selectPermission(sessionId: String, preset: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(sessionId.isNotBlank()) { "会话标识为空" }
            require(preset.matches(Regex("[a-z0-9-]{1,64}"))) { "权限模式标识无效" }
            val accepted = DshSessionAdminProtocol.commandAccepted(
                call("commands/execute", JSONObject()
                    .put("agentId", sessionId)
                    .put("line", "/permission $preset")
                    .put("submittedAttachments", JSONArray()))
            )
            if (!accepted) throw IOException("官方未接受权限切换")
        }
    }

    override suspend fun skills(sessionId: String): Result<List<DshSkill>> = withContext(Dispatchers.IO) {
        runCatching {
            require(sessionId.isNotBlank()) { "会话标识为空" }
            DshSessionAdminProtocol.parseSkills(
                call("skills/list", JSONObject().put("request", JSONObject().put("sessionId", sessionId)))
            )
        }
    }

    override suspend fun commands(sessionId: String): Result<List<DshCommand>> = withContext(Dispatchers.IO) {
        runCatching {
            require(sessionId.isNotBlank()) { "会话标识为空" }
            DshSessionAdminProtocol.parseCommands(
                call("commands/list", JSONObject().put("agentId", sessionId))
            )
        }
    }

    /** 临时把 transport 的 `result.value` 重新包装成现有解析器使用的官方信封。 */
    private suspend fun call(method: String, args: JSONObject): String =
        DshSessionAdminProtocol.successEnvelope(transport.call(method, args))
}

/**
 * 临时 HTTP 一元 transport：认证、Cookie、loopback URL、超时与脱敏都在这里。
 *
 * 待 42 的 `DshTransport`（PR #40）合并后，用
 * `DshAdminTransportAdapter(transport::call, transport::reset)` 替换本类；
 * SessionAdmin 的请求/解析逻辑不变。
 */
class DshHttpAdminTransport(private val endpointProvider: () -> DshEndpoint?) : DshAdminTransport {
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
                val rpcId = UUID.randomUUID().toString()
                val body = DshApiProtocol.requestBody(rpcId, method, args).toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
                val code = connection.responseCode
                val text = readBounded(if (code in 200..299) connection.inputStream else connection.errorStream)
                if (code == 401 && attempt == 0) {
                    requestGeneration = beginCookieRefresh(requestGeneration)
                    currentCookie = ensureCookie(endpoint, requestGeneration)
                    continue
                }
                if (code !in 200..299) throw IOException("DSH 接口返回 HTTP $code")
                synchronized(stateLock) {
                    if (requestGeneration != authGeneration) throw IOException("DSH 接口认证已取消")
                }
                DshApiProtocol.checkEnvelope(text, rpcId)
                return@withContext DshSessionAdminProtocol.resultValue(text)
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("DSH 接口认证失败")
    }

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
        val url = connectUrl(endpoint) ?: throw IOException("DSH 启动地址无效")
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

    /** 管理页只做一元调用，固定声明 `interactions=none`，不建立聊天/审批 WebSocket。 */
    private fun connectUrl(endpoint: DshEndpoint): String? = runCatching {
        if (!DshApiProtocol.validOrigin(endpoint.origin) || !OriginPolicy.sameOrigin(endpoint.launchUrl, endpoint.origin)) return null
        val uri = URI(endpoint.launchUrl)
        val parts = uri.rawQuery?.split('&')?.filter { it.isNotBlank() } ?: return null
        val hasToken = parts.any { part ->
            part.substringBefore('=').equals("token", ignoreCase = true) &&
                part.substringAfter('=', "").isNotBlank()
        }
        if (!hasToken) return null
        val withoutInteractions = parts.filterNot { it.substringBefore('=').equals("interactions", ignoreCase = true) }
        "${endpoint.origin}/connect?${withoutInteractions.joinToString("&")}&interactions=none"
    }.getOrNull()

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
}
