package com.tanuki.ui

import com.tanuki.runtime.Redact
import com.tanuki.web.DshComposerRemote
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.util.Locale

/**
 * 原生聊天渲染层的确定性策略集合。
 *
 * 这里只放不依赖 Android/Compose 运行时的纯函数，便于在 JVM 回归里覆盖：
 * 底部判定、流式增量合并、链接/图片/附件分类、错误可读化与代码行号。
 * 列表锚定、向前分页与内存窗口见 [DshChatAnchorPolicy] 与 [DshChatWindowPolicy]。
 */
object DshChatRenderPolicy {
    /** 仅表示页面可观察状态；断线时不把旧运行标记解释为实时思考。 */
    fun activity(running: Boolean, live: Boolean, waiting: Boolean = false): String = when {
        waiting -> "等你回答"
        !live -> "连接未实时"
        running -> "思考中"
        else -> "就绪"
    }

    fun composerPlaceholder(running: Boolean): String =
        if (running) "生成中 · 可直接插话" else "发消息，@ 引用，/ 命令"

    /** 用量只能由已确认的分子/分母产生；缺值不能当成 0%。 */
    fun contextPercent(used: Long?, limit: Long?): String {
        if (used == null || limit == null || used < 0 || limit <= 0) return "—"
        val percent = (used.toDouble() / limit * 100).coerceIn(0.0, 100.0)
        return if (percent > 0 && percent < 1) "<1%" else "${percent.toInt()}%"
    }

    /** 相邻工具才聚合；调用方先按阅读锚点拆段，不跨锚点吞并新调用。 */
    fun rows(timeline: List<com.tanuki.web.DshTimelineItem>): List<DshChatRow> {
        val rows = mutableListOf<DshChatRow>()
        timeline.forEach { item ->
            val previous = rows.lastOrNull()
            if (item is com.tanuki.web.DshTimelineItem.Tool && previous?.tools?.isNotEmpty() == true) {
                rows[rows.lastIndex] = previous.copy(tools = previous.tools + item.tool)
            } else {
                rows += DshChatRow(item, if (item is com.tanuki.web.DshTimelineItem.Tool) listOf(item.tool) else emptyList())
            }
        }
        return rows
    }

    /** 分页插入后，原工具 key 可能已进入同组；仍能恢复到包含它的那一行。 */
    fun rowKey(rows: List<DshChatRow>, memberKey: String): String? = rows.firstOrNull {
        it.key == memberKey || it.tools.any { tool -> tool.key == memberKey }
    }?.key

    /** 单个代码块最多显示行数；完整内容仍可复制。 */
    const val MAX_CODE_LINES = 400

    /** 单张表格最多显示行数；完整表格仍可从官方界面查看。 */
    const val MAX_TABLE_ROWS = 60

    /** 单张表格最多渲染列数，防止超宽表把一次布局拖死。 */
    const val MAX_TABLE_COLUMNS = 12

    /** 判定“已在底部”的像素容差。 */
    const val BOTTOM_TOLERANCE_PX = 24

    /** 单个 Markdown 消息最多解析的块数；超出时明确提示而不是静默丢弃。 */
    const val MAX_MARKDOWN_BLOCKS = 300

    /** 是否已经看到列表最后一项且没有明显向上偏移。 */
    fun isAtBottom(
        lastVisibleIndex: Int,
        totalItems: Int,
        lastItemEndOffset: Int,
        viewportEndOffset: Int,
        tolerance: Int = BOTTOM_TOLERANCE_PX
    ): Boolean {
        if (totalItems <= 0) return false
        if (lastVisibleIndex < totalItems - 1) return false
        return lastItemEndOffset <= viewportEndOffset + tolerance
    }
}

/**
 * 页面唯一的运行态接线：先切会话再写官方状态，沿用输入栏已有停止确认与代次保护。
 * 只收集会话/运行态变化，避免无关页面更新用旧 idle 干扰尚在途的发送回执。
 */
internal suspend fun bindDshComposerRunState(
    nativeState: StateFlow<DshNativeState>,
    composer: DshComposerController
) {
    nativeState.map { it.selectedId to it.running }.distinctUntilChanged().collect { (sessionId, running) ->
        composer.setSession(sessionId)
        composer.setGenerationRunning(running)
    }
}

/** 展示行保留首事件的稳定身份，工具聚合不改变协议时间线。 */
data class DshChatRow(
    val item: com.tanuki.web.DshTimelineItem,
    val tools: List<com.tanuki.web.DshToolItem> = emptyList()
) {
    val key: String get() = item.stableKey()
}

/**
 * 流式增量合并：
 * - 官方 follow 的 `assistant-stream` 是累计全文，正常情况下新文本以旧文本为前缀；
 * - 若服务端改成只发增量，则追加到末尾；
 * - 重复的尾段不再次插入，避免同一段文字出现两次；
 * - 空字符串表示新一轮开始，必须清空旧内容。
 */
object DshStreamPolicy {
    /** 约 30fps 合并一次，兼顾顺滑与低端机帧预算。 */
    const val FRAME_MILLIS = 32L

    const val MAX_TEXT_CHARS = 12_000

    fun merge(current: String, incoming: String): String {
        if (incoming.isEmpty()) return ""
        if (incoming == current) return current
        if (current.isEmpty()) return incoming.take(MAX_TEXT_CHARS)
        if (incoming.startsWith(current)) return incoming.take(MAX_TEXT_CHARS)
        if (current.endsWith(incoming)) return current
        return (current + incoming).take(MAX_TEXT_CHARS)
    }

    fun shouldPublish(pending: String?, current: String): Boolean =
        pending != null && pending != current

    /** reset 帧直接替换旧内容；普通帧按累计/增量规则合并。 */
    fun mergeFrame(current: String, frame: DshStreamFrame): String =
        if (frame.reset) frame.text.take(MAX_TEXT_CHARS) else merge(current, frame.text)
}

/** 一个流式帧：reset=true 表示官方开启了新一轮 assistant-stream，必须替换旧前缀而不是追加。 */
data class DshStreamFrame(
    val text: String,
    val reset: Boolean = false
)

/**
 * 图片采样：BitmapFactory 只会按 2 的幂缩小，因此必须用**原始宽高**除以 sampling，
 * 不能每轮同时把工作宽高减半，否则会低估采样倍数并突破解码上限。
 */
object DshImageSampling {
    const val MAX_DIMENSION = 2_048

    fun sampleSize(width: Int, height: Int, maxDimension: Int = MAX_DIMENSION): Int {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
        var sample = 1
        while ((width / sample > maxDimension || height / sample > maxDimension) &&
            sample <= (1 shl 29)
        ) {
            sample = sample shl 1
        }
        return sample
    }

    fun fits(width: Int, height: Int, sample: Int, maxDimension: Int = MAX_DIMENSION): Boolean {
        if (width <= 0 || height <= 0 || sample <= 0) return false
        return width / sample <= maxDimension && height / sample <= maxDimension
    }
}

/**
 * 输入栏真实发送链路的观察器。
 *
 * 输入栏走 `DshComposerController.send()`；这里只观察其公开 state 的 sending/error 变化，
 * 捕获本次提交文本。失败时返回可重试/可复制的原文，避免呈现层另造一份发送状态。
 */
class DshComposerSendTracker {
    private var sending = false
    private var submittedText = ""
    private var submittedAttachments = 0
    private var submittedAt = 0L

    fun observe(
        sendingNow: Boolean,
        text: String,
        error: String?,
        attachmentCount: Int = 0
    ): DshFailedPrompt? {
        if (!sending && sendingNow) {
            sending = true
            submittedText = text
            submittedAttachments = attachmentCount
            submittedAt = System.currentTimeMillis()
            return null
        }
        if (sending && !sendingNow) {
            sending = false
            val payload = submittedText
            val attachments = submittedAttachments
            val time = submittedAt
            submittedText = ""
            submittedAttachments = 0
            submittedAt = 0L
            if (!error.isNullOrBlank()) {
                return DshFailedPrompt(payload, error, time, attachments)
            }
        }
        return null
    }

    fun reset() {
        sending = false
        submittedText = ""
        submittedAttachments = 0
        submittedAt = 0L
    }
}

enum class DshLinkKind { WEB, MAIL, PHONE, ATTACHMENT, BLOCKED }

data class DshLinkDecision(
    val kind: DshLinkKind,
    val url: String,
    val problem: String? = null
) {
    val canOpen: Boolean get() = kind == DshLinkKind.WEB || kind == DshLinkKind.MAIL || kind == DshLinkKind.PHONE

    val confirmText: String
        get() = when (kind) {
            DshLinkKind.WEB -> "将在系统浏览器中打开此链接。"
            DshLinkKind.MAIL -> "将调用系统邮件应用。"
            DshLinkKind.PHONE -> "将调用系统拨号界面。"
            DshLinkKind.ATTACHMENT -> "附件/工作区链接需要额外授权，原生消息暂不直接打开。"
            DshLinkKind.BLOCKED -> "出于安全原因，原生消息不打开该链接。"
        }
}

/** 链接打开前的确定性分类与安全边界；只有可确认的普通链接才会调起系统应用。 */
object DshLinkPolicy {
    const val MAX_URL_CHARS = 2_048

    private val controlChars = Regex("[\\u0000-\\u001F\\u007F]")
    private val dangerousSchemes = setOf("javascript", "data", "vbscript", "intent", "file", "content")

    fun classify(raw: String): DshLinkDecision {
        val cleaned = raw.trim().removeSurrounding("<", ">").trim()
        if (cleaned.isEmpty()) return blocked("", "链接地址为空")
        if (cleaned.length > MAX_URL_CHARS) return blocked("", "链接地址过长，已拒绝打开")
        if (controlChars.containsMatchIn(cleaned)) return blocked("", "链接包含不可见控制字符，已拒绝打开")

        val scheme = cleaned.substringBefore(':', missingDelimiterValue = "").lowercase(Locale.ROOT)
        if (scheme in dangerousSchemes) return blocked(cleaned, "出于安全原因不打开该链接")
        return when (scheme) {
            "http", "https" -> DshLinkDecision(DshLinkKind.WEB, cleaned)
            "mailto" -> DshLinkDecision(DshLinkKind.MAIL, cleaned)
            "tel" -> DshLinkDecision(DshLinkKind.PHONE, cleaned)
            "sandbox", "attachment", "workspace" ->
                DshLinkDecision(DshLinkKind.ATTACHMENT, cleaned, "附件链接需要 DSH 工作区授权")
            "" -> DshLinkDecision(DshLinkKind.ATTACHMENT, cleaned, "相对路径需要工作区授权")
            else -> blocked(cleaned, "暂不支持打开该类型的链接")
        }
    }

    private fun blocked(url: String, reason: String) =
        DshLinkDecision(DshLinkKind.BLOCKED, url, reason)
}

enum class DshImageKind { REMOTE, LOCAL, EMBEDDED, AUTHORIZED, INVALID }

data class DshImageInfo(
    val kind: DshImageKind,
    val url: String,
    val message: String
) {
    val canPreview: Boolean get() = kind == DshImageKind.LOCAL || kind == DshImageKind.EMBEDDED
}

/** 图片占位与失败提示策略；远程图片保持“不自动联网”，需要会话授权的图片不直连。 */
object DshImagePolicy {
    const val MAX_EMBEDDED_CHARS = 2_000_000

    fun describe(alt: String, url: String): DshImageInfo {
        val cleaned = url.trim()
        if (cleaned.isEmpty()) return DshImageInfo(DshImageKind.INVALID, cleaned, "图片地址为空，无法加载")
        if (cleaned.length > MAX_EMBEDDED_CHARS) {
            return DshImageInfo(DshImageKind.INVALID, cleaned, "图片数据过大，无法在消息内预览")
        }
        val lower = cleaned.lowercase(Locale.ROOT)
        return when {
            lower.startsWith("data:image/") -> DshImageInfo(
                DshImageKind.EMBEDDED,
                cleaned,
                if (alt.isBlank()) "内嵌图片" else alt
            )
            lower.startsWith("content:") || lower.startsWith("file:") -> DshImageInfo(
                DshImageKind.LOCAL,
                cleaned,
                "本地图片，点击查看；读取失败时会显示中文原因"
            )
            lower.startsWith("http://") || lower.startsWith("https://") -> DshImageInfo(
                DshImageKind.REMOTE,
                cleaned,
                "远程图片未自动加载；点击查看地址，避免绕过 DSH 会话授权"
            )
            lower.startsWith("sandbox:") || lower.startsWith("attachment:") || lower.startsWith("workspace:") ->
                DshImageInfo(
                    DshImageKind.AUTHORIZED,
                    cleaned,
                    "该图片需要 DSH 会话授权，原生消息暂不直接加载"
                )
            else -> DshImageInfo(DshImageKind.INVALID, cleaned, "图片地址格式不支持，无法加载")
        }
    }
}

data class DshAttachmentInfo(
    val name: String,
    val url: String,
    val message: String,
    val canOpen: Boolean
)

/** 附件占位文案；不在消息内直接读取工作区文件，避免绕过权限。 */
object DshAttachmentPolicy {
    fun describe(name: String, url: String): DshAttachmentInfo {
        val label = name.trim().ifBlank { url.substringAfterLast('/').ifBlank { "未命名附件" } }
        val decision = DshLinkPolicy.classify(url)
        return when (decision.kind) {
            DshLinkKind.WEB -> DshAttachmentInfo(label, url, "网络附件，点击确认后查看地址", true)
            DshLinkKind.ATTACHMENT -> DshAttachmentInfo(label, url, "附件需要工作区或会话授权，原生消息暂不直接打开", false)
            else -> DshAttachmentInfo(label, url, decision.problem ?: "附件地址无效", false)
        }
    }
}

enum class DshErrorKind { AUTH, BUSY, TIMEOUT, NETWORK, PROTOCOL, CANCELLED, UNKNOWN }

data class DshErrorInfo(
    val kind: DshErrorKind,
    val title: String,
    val hint: String,
    val detail: String
)

/** 把官方短错误码/异常文本转成可读中文提示；详情保持可复制、不展开敏感内容。 */
object DshErrorPresenter {
    private const val MAX_DETAIL_CHARS = 240

    fun describe(raw: String?): DshErrorInfo {
        val detail = raw?.trim().orEmpty().ifBlank { "未知错误" }.take(MAX_DETAIL_CHARS)
        val lower = detail.lowercase(Locale.ROOT)
        return when {
            lower.contains("401") || lower.contains("unauthorized") || detail.contains("认证") ->
                DshErrorInfo(DshErrorKind.AUTH, "连接认证已失效", "切换到官方界面重新连接后再试。", detail)
            lower.contains("agent-busy") || lower.contains("busy") || detail.contains("正在处理") ->
                DshErrorInfo(DshErrorKind.BUSY, "DSH 正在处理其他消息", "等待当前回合结束后再重试。", detail)
            lower.contains("timeout") || lower.contains("timed out") || detail.contains("超时") ->
                DshErrorInfo(DshErrorKind.TIMEOUT, "连接超时", "检查 DSH 服务后点刷新重试。", detail)
            lower.contains("network") || lower.contains("connect") || lower.contains("socket") ||
                lower.contains("eof") || lower.contains("websocket") || detail.contains("网络") ->
                DshErrorInfo(DshErrorKind.NETWORK, "网络连接中断", "检查 DSH 服务状态后重试。", detail)
            lower.contains("json") || lower.contains("格式") || lower.contains("响应") ->
                DshErrorInfo(DshErrorKind.PROTOCOL, "官方响应格式异常", "可复制错误信息，或切换官方界面查看。", detail)
            lower.contains("cancel") || detail.contains("取消") ->
                DshErrorInfo(DshErrorKind.CANCELLED, "操作已取消", "如仍需要结果，请重新发送。", detail)
            else -> DshErrorInfo(DshErrorKind.UNKNOWN, "操作未完成", "可复制错误信息，或切换官方界面处理。", detail)
        }
    }
}

data class DshCodeView(
    val lines: List<String>,
    val totalLines: Int,
    val truncated: Boolean
) {
    val shownText: String get() = lines.joinToString("\n")
}

/** 代码块展示上限与行号前缀；复制与解析始终使用完整原文。 */
object DshCodeBlockPolicy {
    fun view(code: String, maxLines: Int = DshChatRenderPolicy.MAX_CODE_LINES): DshCodeView {
        val all = code.split('\n')
        val safeMax = maxLines.coerceAtLeast(1)
        if (all.size <= safeMax) return DshCodeView(all, all.size, false)
        return DshCodeView(all.take(safeMax), all.size, true)
    }

    /** 行号右对齐并留出固定分隔，避免代码长行把行号挤乱。 */
    fun lineNumberPrefix(line: Int, width: Int): String {
        val safeWidth = width.coerceAtLeast(1)
        return line.toString().padStart(safeWidth) + " │ "
    }

    fun numberWidth(totalLines: Int): Int = totalLines.toString().length.coerceAtLeast(1)
}

data class DshTableView(
    val headers: List<String>,
    val rows: List<List<String>>,
    val alignments: List<DshTableAlign>,
    val totalRows: Int,
    val truncatedRows: Boolean,
    val truncatedColumns: Boolean
)

/** 表格渲染上限；超宽/超长时保留表头并明确提示，不静默丢弃。 */
object DshTablePolicy {
    fun view(
        table: DshMarkdownBlock.Table,
        maxRows: Int = DshChatRenderPolicy.MAX_TABLE_ROWS,
        maxColumns: Int = DshChatRenderPolicy.MAX_TABLE_COLUMNS
    ): DshTableView {
        val safeColumns = maxColumns.coerceAtLeast(1)
        val safeRows = maxRows.coerceAtLeast(1)
        val columns = table.headers.size.coerceAtLeast(1)
        val shownColumns = columns.coerceAtMost(safeColumns)
        val headers = List(shownColumns) { table.headers.getOrElse(it) { "" } }
        val rows = table.rows.take(safeRows).map { row ->
            List(shownColumns) { row.getOrElse(it) { "" } }
        }
        val alignments = List(shownColumns) { table.alignments.getOrElse(it) { DshTableAlign.LEFT } }
        return DshTableView(
            headers = headers,
            rows = rows,
            alignments = alignments,
            totalRows = table.rows.size,
            truncatedRows = table.rows.size > safeRows,
            truncatedColumns = columns > shownColumns
        )
    }
}

/** 输入栏一次发送失败的最终结果；按会话保存，页面离场/重入后仍可恢复。 */
data class DshComposerSendResult(
    val sessionId: String,
    val text: String,
    val reason: String,
    val attachmentCount: Int,
    val time: Long = System.currentTimeMillis()
)

/**
 * 包装官方 composer remote，在**最终业务结果**确定后记录失败；成功/取消会清除该会话旧失败。
 *
 * - `session/prompt` 只有在 `accepted=true` 时才算成功；`commands/execute` 只有在
 *   `result.kind=success` 时才算成功，和 [DshComposerClient] 的校验保持一致。
 * - 携带 sessionId 持久化到 StateFlow，页面切走/重入不会丢失败；取消不作为失败展示。
 * - 只旁路记录，不改变请求/响应、错误传播或附件语义。
 */
class DshComposerSendRecorder(
    private val delegate: DshComposerRemote
) : DshComposerRemote {
    private val mutableFailures = MutableStateFlow<Map<String, DshComposerSendResult>>(emptyMap())
    val failures: StateFlow<Map<String, DshComposerSendResult>> = mutableFailures.asStateFlow()

    override suspend fun call(method: String, args: JSONObject): Any? {
        if (method != PROMPT_METHOD && method != COMMAND_METHOD) return delegate.call(method, args)
        val sessionId = submittedSessionId(method, args)
        val text = submittedText(method, args)
        val attachments = submittedAttachmentCount(method, args)
        if (sessionId.isNotBlank()) {
            // 新一次提交先清掉该会话的旧失败卡，避免旧内容与新尝试混淆。
            mutableFailures.update { it - sessionId }
        }
        return try {
            val value = delegate.call(method, args)
            val businessError = businessFailure(method, value)
            if (sessionId.isNotBlank()) {
                if (businessError == null) {
                    mutableFailures.update { it - sessionId }
                } else {
                    mutableFailures.update {
                        it + (sessionId to DshComposerSendResult(
                            sessionId = sessionId,
                            text = text,
                            reason = businessError,
                            attachmentCount = attachments
                        ))
                    }
                }
            }
            value
        } catch (cancelled: CancellationException) {
            // 切换会话/关闭 composer 会取消发送；这不是失败，不能把 A 的取消写到 B。
            throw cancelled
        } catch (error: Throwable) {
            if (sessionId.isNotBlank()) {
                mutableFailures.update {
                    it + (sessionId to DshComposerSendResult(
                        sessionId = sessionId,
                        text = text,
                        reason = Redact.text(error.message ?: "发送失败").take(240),
                        attachmentCount = attachments
                    ))
                }
            }
            throw error
        }
    }

    override fun reset() {
        delegate.reset()
    }

    private fun submittedSessionId(method: String, args: JSONObject): String =
        if (method == COMMAND_METHOD) {
            args.optString("agentId")
        } else {
            args.optJSONObject("request")?.optString("sessionId").orEmpty()
        }

    private fun submittedText(method: String, args: JSONObject): String {
        if (method == COMMAND_METHOD) return args.optString("line")
        val content = args.optJSONObject("request")?.optJSONArray("content") ?: return ""
        val parts = ArrayList<String>()
        for (index in 0 until content.length()) {
            val part = content.optJSONObject(index) ?: continue
            if (part.optString("type") != "text") continue
            val value = part.optString("text")
            if (value.isNotBlank()) parts += value
        }
        return parts.joinToString("\n")
    }

    private fun submittedAttachmentCount(method: String, args: JSONObject): Int {
        if (method == COMMAND_METHOD) return args.optJSONArray("submittedAttachments")?.length() ?: 0
        val content = args.optJSONObject("request")?.optJSONArray("content") ?: return 0
        var count = 0
        for (index in 0 until content.length()) {
            val part = content.optJSONObject(index) ?: continue
            if (part.optString("type") != "text") count++
        }
        return count
    }

    /** 与 DshComposerClient 的最终业务校验保持一致；null 表示成功。 */
    private fun businessFailure(method: String, value: Any?): String? {
        if (method == PROMPT_METHOD) {
            val root = value as? JSONObject ?: return "发送响应格式不正确"
            return if (root.optBoolean("accepted", false)) null else "官方接口未接受该消息"
        }
        val root = value as? JSONObject ?: return "未找到该斜杠命令"
        val result = root.optJSONObject("result") ?: return "斜杠命令返回格式不正确"
        return when (result.optString("kind")) {
            "success" -> null
            "error" -> result.optString("text").take(240).ifBlank { "斜杠命令执行失败" }
            else -> "斜杠命令返回格式不正确"
        }
    }

    private companion object {
        const val PROMPT_METHOD = "session/prompt"
        const val COMMAND_METHOD = "commands/execute"
    }
}

/**
 * 失败卡片的重试边界：只有当前草稿与失败提交完全一致且没有附件快照时才允许直接重试。
 * 原生输入栏没有“按快照重发附件”的公开接口，因此含附件或已编辑的草稿必须显式回填/重新添加。
 */
object DshComposerDraftPolicy {
    fun matches(failure: DshFailedPrompt, text: String, attachmentCount: Int): Boolean =
        failure.attachmentCount == 0 && attachmentCount == 0 &&
            failure.text.isNotBlank() && failure.text == text

    fun retryHint(failure: DshFailedPrompt, text: String, attachmentCount: Int): String? = when {
        failure.attachmentCount > 0 || attachmentCount > 0 ->
            "失败消息包含附件；原生输入栏暂不支持按快照重发附件，请复制内容并重新添加附件。"
        text != failure.text ->
            "当前草稿已修改；请先回填失败内容，或复制后重新发送。"
        failure.text.isBlank() -> "失败消息没有可重发的文本，请重新编辑后发送。"
        else -> null
    }
}
