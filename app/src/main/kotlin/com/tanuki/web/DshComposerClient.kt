package com.tanuki.web

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

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
 * 只调用官方已声明的方法；认证、Cookie 与传输由调用方提供的
 * [DshComposerRemote] 负责，本类不把 token/Cookie 写进界面状态、异常或请求内容。
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

    /** 官方 `session/cancel`；只取消当前活动回合，不停止 DSH 服务。 */
    suspend fun cancel(sessionId: String): Result<Unit> = runCatching {
        val value = remote.call("session/cancel", DshComposerProtocol.cancelArgs(sessionId))
        val root = value as? JSONObject ?: throw IllegalArgumentException("停止响应格式不正确")
        if (!root.optBoolean("accepted", false)) throw IOException("官方接口未接受停止请求")
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
