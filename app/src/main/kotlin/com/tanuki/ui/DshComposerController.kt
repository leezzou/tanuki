package com.tanuki.ui

import com.tanuki.runtime.Redact
import com.tanuki.web.DshApiException
import com.tanuki.web.DshAttachmentKind
import com.tanuki.web.DshCommand
import com.tanuki.web.DshCommandException
import com.tanuki.web.DshComposerClient
import com.tanuki.web.DshComposerPolicy
import com.tanuki.web.DshComposerProtocol
import com.tanuki.web.DshFileReference
import com.tanuki.web.DshImageLimits
import com.tanuki.web.DshImageMetadata
import com.tanuki.web.DshMediaTypes
import com.tanuki.web.DshMentionQuery
import com.tanuki.web.DshOutgoingPart
import com.tanuki.web.DshSessionReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.TimeZone
import java.util.UUID

enum class DshAttachmentStatus { READY, UPLOADING, UPLOADED, FAILED }

/**
 * 停止生成的结果。
 *
 * [REQUESTED] 只表示官方已接受停止请求，回合可能仍在收尾；必须等官方
 * `running=false` 确认后才能进入 [STOPPED]，不能把 accepted 回执当成已停止。
 */
enum class DshStopOutcome { REQUESTED, STOPPED, FAILED }

/** 已读取到内存的待发送附件；previewUri 只用于本地缩略图，不进入发送协议。 */
data class DshDraftAttachment(
    val id: String,
    val kind: DshAttachmentKind,
    val name: String,
    val mediaType: String,
    val bytes: ByteArray,
    val width: Int? = null,
    val height: Int? = null,
    val previewUri: String? = null,
    val status: DshAttachmentStatus = DshAttachmentStatus.READY,
    val receiptId: String? = null,
    val error: String? = null
) {
    val sizeLabel: String get() = DshComposerPolicy.formatBytes(bytes.size)
}

/** Android 层读取完成后的附件输入；控制器只做校验和协议状态，不直接访问 URI。 */
data class DshPickedAttachment(
    val name: String,
    val mediaType: String,
    val bytes: ByteArray,
    val kind: DshAttachmentKind,
    val width: Int? = null,
    val height: Int? = null,
    val previewUri: String? = null
)

/** 一次输入/附件操作的会话代次快照；异步读取返回时必须仍属于同一会话。 */
data class DshComposerSessionToken(val sessionId: String?, val generation: Long)

sealed interface DshMentionCandidate {
    val title: String
    val subtitle: String?
    val insertion: String

    data class File(val reference: DshFileReference) : DshMentionCandidate {
        override val title: String get() = reference.path
        override val subtitle: String? get() = if (reference.kind == "directory") "目录" else "文件"
        override val insertion: String get() = reference.mention
    }

    data class Session(val reference: DshSessionReference) : DshMentionCandidate {
        override val title: String get() = reference.displayTitle
        override val subtitle: String? get() = reference.sessionId.take(12)
        override val insertion: String get() = reference.mention
    }
}

data class DshComposerState(
    val sessionId: String? = null,
    val text: String = "",
    val attachments: List<DshDraftAttachment> = emptyList(),
    val limits: DshImageLimits = DshImageLimits(),
    val commands: List<DshCommand> = emptyList(),
    val commandsLoaded: Boolean = false,
    val commandCandidates: List<DshCommand> = emptyList(),
    val mentionQuery: DshMentionQuery? = null,
    val mentionCandidates: List<DshMentionCandidate> = emptyList(),
    val mentionLoading: Boolean = false,
    val mentionError: String? = null,
    val sending: Boolean = false,
    val sendFailed: Boolean = false,
    val uploadBusy: Boolean = false,
    /** 当前回合是否在运行；由发送成功置位，页面接入官方 running 状态后同步复位。 */
    val generationRunning: Boolean = false,
    val stopping: Boolean = false,
    val stopOutcome: DshStopOutcome? = null,
    val error: String? = null,
    val notice: String? = null
) {
    val canSend: Boolean
        get() = sessionId != null && !sending && (text.isNotBlank() || attachments.isNotEmpty()) &&
            attachments.none { it.kind == DshAttachmentKind.FILE && it.status != DshAttachmentStatus.UPLOADED }
}

/**
 * 原生输入栏状态机。
 *
 * 发送只调用官方 session/prompt 或 commands/execute；图片随 prompt 以 base64
 * 发送，普通文件先上传换 receipt。失败时保留草稿与附件，成功后才清空。
 */
class DshComposerController(
    private val client: DshComposerClient,
    private val drafts: DshDraftStore,
    private val scope: CoroutineScope,
    private val onSent: (String) -> Unit = {},
    private val timeZoneProvider: () -> String? = { TimeZone.getDefault().id }
) {
    private val _state = MutableStateFlow(DshComposerState())
    val state: StateFlow<DshComposerState> = _state.asStateFlow()

    private var generation = 0L
    private var draftJob: Job? = null
    private var mentionJob: Job? = null
    private var commandJob: Job? = null
    private var uploadJob: Job? = null
    private var sendJob: Job? = null
    private var stopJob: Job? = null
    /** 上传串行锁：取消后立即重启 worker 时，旧请求必须结束后新请求才能开始。 */
    private val uploadLock = Mutex()

    /** 切换会话先落盘旧草稿，再读取新草稿；附件不跨会话保留。 */
    fun setSession(sessionId: String?) {
        val normalized = sessionId?.takeIf { DshComposerProtocol.validSessionId(it) }
        if (normalized == _state.value.sessionId) return
        flushDraft()
        generation++
        draftJob?.cancel()
        mentionJob?.cancel()
        commandJob?.cancel()
        uploadJob?.cancel()
        sendJob?.cancel()
        stopJob?.cancel()
        _state.value = DshComposerState(
            sessionId = normalized,
            text = normalized?.let(drafts::read).orEmpty().take(DshComposerProtocol.MAX_TEXT_CHARS)
        )
        if (normalized == null) return
        loadLimits(normalized, generation)
        loadCommands(normalized, generation)
    }

    /** 在启动选择器/剪贴板/拖入读取前捕获；结果返回时用它拒绝已切换会话的旧结果。 */
    fun captureSessionToken(): DshComposerSessionToken =
        DshComposerSessionToken(_state.value.sessionId, generation)

    fun isCurrentSession(token: DshComposerSessionToken): Boolean =
        token.generation == generation && token.sessionId == _state.value.sessionId

    fun updateText(text: String, cursor: Int = text.length) {
        val bounded = text.take(DshComposerProtocol.MAX_TEXT_CHARS)
        val safeCursor = cursor.coerceIn(0, bounded.length)
        _state.update { it.copy(text = bounded, error = null) }
        scheduleDraftSave()
        val mention = DshComposerPolicy.mentionQuery(bounded, safeCursor)
        val previousQuery = _state.value.mentionQuery?.query
        _state.update { it.copy(mentionQuery = mention) }
        if (mention == null) {
            mentionJob?.cancel()
            _state.update { it.copy(mentionCandidates = emptyList(), mentionLoading = false, mentionError = null) }
        } else if (mention.query != previousQuery) {
            loadMentions(mention.query)
        }
        val slash = DshComposerPolicy.slashQuery(bounded, safeCursor)
        _state.update {
            it.copy(commandCandidates = slash?.let { query -> filterCommands(it.commands, query) }.orEmpty())
        }
    }

    /** 用户从候选菜单选择后由界面插入文本，再回到 updateText 刷新候选。 */
    fun clearMention() {
        mentionJob?.cancel()
        _state.update { it.copy(mentionQuery = null, mentionCandidates = emptyList(), mentionLoading = false, mentionError = null) }
    }

    fun clearCommandCandidates() {
        _state.update { it.copy(commandCandidates = emptyList()) }
    }

    /** 只有携带当前会话 token 的异步结果才能进入附件列表；null 一律视为无效发起凭证。 */
    fun canApplyAttachmentResult(token: DshComposerSessionToken?): Boolean =
        token != null && isCurrentSession(token)

    fun addImages(items: List<DshPickedAttachment>, token: DshComposerSessionToken) {
        if (!isCurrentSession(token)) return
        addAttachments(items, DshAttachmentKind.IMAGE)
    }

    fun addFiles(items: List<DshPickedAttachment>, token: DshComposerSessionToken) {
        if (!isCurrentSession(token)) return
        addAttachments(items, DshAttachmentKind.FILE)
    }

    private fun addAttachments(items: List<DshPickedAttachment>, expectedKind: DshAttachmentKind) {
        if (items.isEmpty()) return
        val sessionId = _state.value.sessionId
        if (sessionId == null) {
            _state.update { it.copy(error = "请先选择会话") }
            return
        }
        var accepted = 0
        var firstRejection: String? = null
        for (item in items) {
            val current = _state.value
            val rejection = when (expectedKind) {
                DshAttachmentKind.IMAGE -> {
                    val mediaType = DshMediaTypes.sniff(item.bytes)
                    if (mediaType == null) {
                        "不支持该图片格式，请选择 PNG、JPEG、WebP 或 GIF"
                    } else {
                        val size = DshImageMetadata.read(item.bytes)
                        val imageCount = current.attachments.count { it.kind == DshAttachmentKind.IMAGE }
                        val imageBytes = current.attachments.filter { it.kind == DshAttachmentKind.IMAGE }.sumOf { it.bytes.size.toLong() }
                        val totalBytes = current.attachments.sumOf { it.bytes.size.toLong() }
                        if (current.attachments.size >= DshComposerProtocol.MAX_ATTACHMENTS_PER_MESSAGE) {
                            "每条消息最多添加 ${DshComposerProtocol.MAX_ATTACHMENTS_PER_MESSAGE} 个附件"
                        } else {
                            DshComposerPolicy.validateImage(
                                mediaType = mediaType,
                                bytes = item.bytes.size,
                                width = size?.width ?: item.width,
                                height = size?.height ?: item.height,
                                limits = current.limits,
                                existingImages = imageCount,
                                existingImageBytes = imageBytes,
                                existingTotalBytes = totalBytes
                            )
                        }
                    }
                }
                DshAttachmentKind.FILE -> {
                    val totalBytes = current.attachments.sumOf { it.bytes.size.toLong() }
                    if (current.attachments.size >= DshComposerProtocol.MAX_ATTACHMENTS_PER_MESSAGE) {
                        "每条消息最多添加 ${DshComposerProtocol.MAX_ATTACHMENTS_PER_MESSAGE} 个附件"
                    } else {
                        DshComposerPolicy.validateFile(
                            bytes = item.bytes.size,
                            existingFiles = current.attachments.count { it.kind == DshAttachmentKind.FILE },
                            existingTotalBytes = totalBytes
                        )
                    }
                }
            }
            if (rejection != null) {
                if (firstRejection == null) firstRejection = rejection
                continue
            }
            val mediaType = when (expectedKind) {
                DshAttachmentKind.IMAGE -> DshMediaTypes.sniff(item.bytes) ?: item.mediaType
                DshAttachmentKind.FILE -> item.mediaType
            }
            val size = if (expectedKind == DshAttachmentKind.IMAGE) DshImageMetadata.read(item.bytes) else null
            val attachment = DshDraftAttachment(
                id = UUID.randomUUID().toString(),
                kind = expectedKind,
                name = DshComposerProtocol.sanitizeFileName(item.name)
                    ?: if (expectedKind == DshAttachmentKind.IMAGE) "图片" else "附件",
                mediaType = mediaType,
                bytes = item.bytes,
                width = size?.width ?: item.width,
                height = size?.height ?: item.height,
                previewUri = item.previewUri,
                status = DshAttachmentStatus.READY
            )
            _state.update { it.copy(attachments = it.attachments + attachment) }
            accepted++
        }
        val message = buildString {
            if (accepted > 0) append("已添加 $accepted 个附件")
            if (firstRejection != null) {
                if (isNotEmpty()) append("；")
                append(firstRejection)
            }
        }
        _state.update {
            it.copy(
                error = firstRejection,
                notice = message.takeIf { accepted > 0 && firstRejection == null }
            )
        }
        if (accepted > 0 && _state.value.attachments.any { it.kind == DshAttachmentKind.FILE && it.status == DshAttachmentStatus.READY }) {
            ensureUploadWorker()
        }
    }

    /**
     * 移除附件；若正在上传则同时取消上传任务并立刻移除卡片。
     * 官方上传是 unary 调用，没有字节级取消回执；这里保证晚到的成功/失败结果
     * 不会把已取消的附件重新写回草稿，也不在本地留下半成品。
     */
    fun removeAttachment(id: String) {
        val target = _state.value.attachments.firstOrNull { it.id == id } ?: return
        val wasUploading = target.status == DshAttachmentStatus.UPLOADING
        if (wasUploading) {
            uploadJob?.cancel()
            uploadJob = null
        }
        _state.update { state ->
            val remaining = state.attachments.filterNot { it.id == id }
            state.copy(
                attachments = remaining,
                uploadBusy = remaining.any { it.status == DshAttachmentStatus.UPLOADING },
                notice = if (wasUploading) "已取消上传，未保留残缺文件" else state.notice
            )
        }
        // 当前项被取消后，队列里其他待上传文件继续处理。
        if (wasUploading) ensureUploadWorker()
    }

    fun retryAttachment(id: String) {
        val target = _state.value.attachments.firstOrNull { it.id == id } ?: return
        if (target.kind != DshAttachmentKind.FILE || target.status != DshAttachmentStatus.FAILED) return
        _state.update { state ->
            state.copy(attachments = state.attachments.map {
                if (it.id == id) it.copy(status = DshAttachmentStatus.READY, error = null) else it
            })
        }
        ensureUploadWorker()
    }

    fun send() {
        val current = _state.value
        val sessionId = current.sessionId ?: run {
            _state.update { it.copy(error = "请先选择会话") }
            return
        }
        if (current.sending) return
        if (current.text.isBlank() && current.attachments.isEmpty()) return
        if (current.attachments.any { it.kind == DshAttachmentKind.FILE && it.status == DshAttachmentStatus.FAILED }) {
            _state.update { it.copy(error = "有附件上传失败，请重试或移除后再发送") }
            return
        }
        if (current.attachments.any { it.kind == DshAttachmentKind.FILE && it.status != DshAttachmentStatus.UPLOADED }) {
            _state.update { it.copy(error = "文件仍在上传，请稍候再发送") }
            return
        }
        val text = current.text.trim()
        if (text.startsWith("/") && !current.commandsLoaded) {
            _state.update { it.copy(error = "正在读取斜杠命令，请稍候再发送") }
            return
        }
        val command = DshComposerPolicy.commandFor(text, current.commands)
        if (command != null && current.attachments.isNotEmpty() && !command.acceptsAttachments) {
            _state.update { it.copy(error = "该斜杠命令不接受附件，请移除附件或改用普通消息") }
            return
        }
        val attachments = current.attachments
        // 发送时快照：成功返回后只清理这份快照，等待期间的编辑/新附件必须保留。
        val submittedText = current.text
        val submittedAttachmentIds = attachments.map { it.id }.toSet()
        val requestGeneration = generation
        _state.update {
            it.copy(
                sending = true,
                sendFailed = false,
                error = null,
                notice = null,
                // 已在收尾的停止请求不能被新一次提交清掉；它是独立于提交回执的状态。
                stopOutcome = if (it.stopOutcome == DshStopOutcome.REQUESTED) {
                    DshStopOutcome.REQUESTED
                } else null
            )
        }
        sendJob?.cancel()
        sendJob = scope.launch {
            // 图片 base64 与文件编码可能达到数十 MB，放到 Default 线程，避免卡住输入与滚动。
            val partsResult = runCatching {
                withContext(Dispatchers.Default) {
                    buildList {
                        for (attachment in attachments) {
                            when (attachment.kind) {
                                DshAttachmentKind.IMAGE -> add(
                                    DshOutgoingPart.Image(
                                        mediaType = attachment.mediaType,
                                        data = DshComposerProtocol.base64(attachment.bytes),
                                        name = attachment.name
                                    )
                                )
                                DshAttachmentKind.FILE -> add(
                                    DshOutgoingPart.File(
                                        receiptId = attachment.receiptId
                                            ?: throw IllegalArgumentException("文件凭证缺失，请重新上传")
                                    )
                                )
                            }
                        }
                        if (text.isNotBlank()) add(DshOutgoingPart.Text(text))
                    }
                }
            }
            if (requestGeneration != generation) return@launch
            val parts = partsResult.getOrElse { error ->
                _state.update { it.copy(sending = false, error = messageOf(error)) }
                return@launch
            }
            val result = if (command != null) {
                client.executeCommand(sessionId, DshComposerPolicy.commandLine(text), parts.filterNot { it is DshOutgoingPart.Text })
            } else {
                client.sendPrompt(
                    sessionId = sessionId,
                    requestId = UUID.randomUUID().toString(),
                    mode = "queue",
                    parts = parts,
                    clientTimeZone = timeZoneProvider()
                )
            }
            if (requestGeneration != generation) return@launch
            result.onSuccess {
                // 发送期间仍可继续编辑；只移除本次实际提交的文本/附件，保留之后的新输入。
                val latest = _state.value
                val keepText = if (latest.text == submittedText) "" else latest.text
                val keepAttachments = latest.attachments.filterNot { it.id in submittedAttachmentIds }
                if (keepText.isBlank() && keepAttachments.isEmpty()) drafts.clear(sessionId)
                else drafts.write(sessionId, keepText)
                val keptEdits = keepText.isNotBlank() || keepAttachments.isNotEmpty()
                _state.update {
                    it.copy(
                        text = keepText,
                        attachments = keepAttachments,
                        sending = false,
                        sendFailed = false,
                        // 普通 prompt 被接受后本轮进入运行；斜杠命令不一定产生回合，
                        // 不默认置运行中，页面仍可用官方 running 状态纠正。
                        generationRunning = command == null,
                        // 停止回执可能与 prompt 回执交错到达；保留仍在收尾的请求状态。
                        stopOutcome = if (it.stopOutcome == DshStopOutcome.REQUESTED) {
                            DshStopOutcome.REQUESTED
                        } else null,
                        mentionQuery = null,
                        mentionCandidates = emptyList(),
                        commandCandidates = emptyList(),
                        notice = if (keptEdits) "已发送；发送期间的新编辑已保留" else "已发送"
                    )
                }
                onSent(sessionId)
            }.onFailure { error ->
                // 失败保留文本与附件，界面显示“重发”；用户可直接再次发送。
                _state.update {
                    it.copy(sending = false, sendFailed = true, error = messageOf(error))
                }
            }
        }
    }

    /**
     * 页面把官方 `running` 状态同步进来。
     *
     * `running=false` 才是停止完成的唯一确认：若此前已接受停止请求，这里才切到
     * [DshStopOutcome.STOPPED]；单纯收到 `session/cancel` 的 accepted 回执不改变
     * 运行状态，避免隐藏仍在收尾的停止入口。
     */
    fun setGenerationRunning(running: Boolean) {
        _state.update { state ->
            if (running) {
                if (state.generationRunning) state else state.copy(generationRunning = true)
            } else {
                val awaitingStop = state.stopping || state.stopOutcome == DshStopOutcome.REQUESTED
                if (!awaitingStop && !state.generationRunning) return@update state
                state.copy(
                    generationRunning = false,
                    stopping = false,
                    stopOutcome = if (awaitingStop) DshStopOutcome.STOPPED else state.stopOutcome,
                    notice = if (awaitingStop) "已停止当前回合" else state.notice
                )
            }
        }
    }

    /**
     * 请求停止当前活动回合；只影响生成，不取消在途 prompt 的提交回执。
     *
     * accepted 回执表示官方已受理停止请求，回合仍可能在收尾；此期间保留
     * `generationRunning` 与停止入口，等 [setGenerationRunning] 收到官方 idle 再显示“已停止”。
     */
    fun requestStop() {
        val current = _state.value
        val sessionId = current.sessionId ?: run {
            _state.update { it.copy(error = "请先选择会话") }
            return
        }
        if (current.stopping) return
        if (!current.generationRunning && !current.sending) {
            _state.update { it.copy(notice = "当前没有运行中的回合") }
            return
        }
        _state.update {
            it.copy(
                stopping = true,
                // 已受理的停止请求在重试期间保持“收尾中”，不要退回空闲。
                stopOutcome = if (it.stopOutcome == DshStopOutcome.REQUESTED) {
                    DshStopOutcome.REQUESTED
                } else null,
                error = null,
                notice = null
            )
        }
        stopJob?.cancel()
        stopJob = scope.launch {
            val result = client.cancel(sessionId)
            if (sessionId != _state.value.sessionId) return@launch
            result.onSuccess {
                // 停止只影响生成；在途 prompt 的回执必须继续走完，不能在这里 cancel sendJob。
                // 只要回合仍在运行或还有提交回执在途，就不能把 accepted 当成“已停止”。
                val awaitingOfficial = _state.value.generationRunning || _state.value.sending
                _state.update {
                    it.copy(
                        stopping = false,
                        stopOutcome = if (awaitingOfficial) {
                            DshStopOutcome.REQUESTED
                        } else DshStopOutcome.STOPPED,
                        notice = if (awaitingOfficial) {
                            "停止请求已发送，仍在收尾，等待官方确认"
                        } else "已停止当前回合"
                    )
                }
            }.onFailure { error ->
                val stillActive = _state.value.generationRunning || _state.value.sending
                _state.update {
                    it.copy(
                        stopping = false,
                        stopOutcome = if (stillActive) DshStopOutcome.FAILED else DshStopOutcome.STOPPED,
                        error = if (stillActive) "停止失败：${messageOf(error)}" else it.error,
                        notice = if (stillActive) it.notice else "回合已结束"
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun showError(message: String) {
        _state.update { it.copy(error = Redact.text(message).take(240), notice = null) }
    }

    fun showNotice(message: String) {
        _state.update { it.copy(notice = Redact.text(message).take(240)) }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun close() {
        flushDraft()
        generation++
        draftJob?.cancel()
        mentionJob?.cancel()
        commandJob?.cancel()
        uploadJob?.cancel()
        sendJob?.cancel()
        stopJob?.cancel()
        client.reset()
        _state.value = DshComposerState()
    }

    private fun loadLimits(sessionId: String, requestGeneration: Long) {
        scope.launch {
            client.imageLimits(sessionId).onSuccess { limits ->
                if (requestGeneration != generation) return@onSuccess
                _state.update { it.copy(limits = limits) }
            }
        }
    }

    private fun loadCommands(sessionId: String, requestGeneration: Long) {
        commandJob?.cancel()
        commandJob = scope.launch {
            client.commands(sessionId).onSuccess { commands ->
                if (requestGeneration != generation) return@onSuccess
                _state.update {
                    it.copy(
                        commands = commands,
                        commandsLoaded = true,
                        commandCandidates = DshComposerPolicy.slashQuery(it.text, it.text.length)
                            ?.let { query -> filterCommands(commands, query) }
                            .orEmpty()
                    )
                }
            }.onFailure {
                if (requestGeneration != generation) return@onFailure
                // 目录不可用时不阻塞普通发送；斜杠文本会按普通提示词发送。
                _state.update { it.copy(commandsLoaded = true, commands = emptyList(), commandCandidates = emptyList()) }
            }
        }
    }

    private fun loadMentions(query: String) {
        val sessionId = _state.value.sessionId ?: return
        mentionJob?.cancel()
        val requestGeneration = generation
        // 查询变化后旧候选不再对应当前文本，先清空避免键盘误选到过期结果。
        _state.update {
            it.copy(mentionLoading = true, mentionError = null, mentionCandidates = emptyList())
        }
        mentionJob = scope.launch {
            delay(160)
            val fileResult = client.fileReferences(sessionId, query)
            val sessionResult = client.sessionReferences(sessionId, query)
            if (requestGeneration != generation) return@launch
            val files = fileResult.getOrDefault(emptyList())
            val sessions = sessionResult.getOrDefault(emptyList())
            val candidates = buildList {
                sessions.forEach { add(DshMentionCandidate.Session(it)) }
                files.forEach { add(DshMentionCandidate.File(it)) }
            }
            _state.update {
                it.copy(
                    mentionCandidates = candidates,
                    mentionLoading = false,
                    // 两类候选都失败才提示；单个目录能力缺失仍允许另一类补全。
                    mentionError = if (fileResult.isFailure && sessionResult.isFailure) {
                        messageOf(fileResult.exceptionOrNull() ?: sessionResult.exceptionOrNull()!!)
                    } else null
                )
            }
        }
    }

    private fun filterCommands(commands: List<DshCommand>, query: String): List<DshCommand> =
        DshComposerPolicy.searchCommands(commands, query)

    private fun scheduleDraftSave() {
        draftJob?.cancel()
        draftJob = scope.launch {
            delay(350)
            flushDraft()
        }
    }

    private fun flushDraft() {
        val current = _state.value
        val sessionId = current.sessionId ?: return
        drafts.write(sessionId, current.text)
    }

    private fun ensureUploadWorker() {
        if (uploadJob?.isActive == true) return
        val sessionId = _state.value.sessionId ?: return
        val requestGeneration = generation
        uploadJob = scope.launch {
            while (true) {
                // 取消后重启 worker 时，旧调用可能还没返回；锁保证同一时刻只有一个上传，
                // 新 worker 不会重复上传旧任务或并发占内存。
                val hasMore = uploadLock.withLock {
                    val next = _state.value.attachments.firstOrNull {
                        it.kind == DshAttachmentKind.FILE && it.status == DshAttachmentStatus.READY
                    } ?: return@withLock false
                    _state.update { state ->
                        state.copy(attachments = state.attachments.map {
                            if (it.id == next.id) it.copy(status = DshAttachmentStatus.UPLOADING, error = null) else it
                        }, uploadBusy = true)
                    }
                    val result = client.uploadFile(sessionId, next.name, next.bytes)
                    // 取消上传会 cancel 当前 worker；晚到的响应不得继续写回状态。
                    coroutineContext.ensureActive()
                    if (requestGeneration != generation) return@launch
                    if (_state.value.attachments.none { it.id == next.id }) return@withLock true
                    result.onSuccess { uploaded ->
                        _state.update { state ->
                            state.copy(attachments = state.attachments.map {
                                if (it.id == next.id) it.copy(
                                    status = DshAttachmentStatus.UPLOADED,
                                    receiptId = uploaded.receiptId,
                                    name = uploaded.name,
                                    error = null
                                ) else it
                            })
                        }
                    }.onFailure { error ->
                        _state.update { state ->
                            state.copy(attachments = state.attachments.map {
                                if (it.id == next.id) it.copy(
                                    status = DshAttachmentStatus.FAILED,
                                    error = messageOf(error)
                                ) else it
                            })
                        }
                    }
                    true
                }
                if (!hasMore) break
            }
            _state.update { state ->
                state.copy(uploadBusy = state.attachments.any { it.status == DshAttachmentStatus.UPLOADING })
            }
        }
    }

    private fun messageOf(error: Throwable): String = when (error) {
        is DshApiException -> when {
            error.code.contains("too-large", ignoreCase = true) ||
                error.code.contains("too_large", ignoreCase = true) -> "附件超过官方大小限制"
            error.code.contains("media", ignoreCase = true) ||
                error.code.contains("type", ignoreCase = true) -> "官方接口不支持该附件类型"
            error.code.contains("not-found", ignoreCase = true) -> "会话或附件不存在"
            else -> "官方接口返回 ${error.code}"
        }
        is DshCommandException -> Redact.text(error.message ?: "斜杠命令执行失败").take(240)
        is IllegalArgumentException -> Redact.text(error.message ?: "输入内容无效").take(240)
        else -> Redact.text(error.message ?: "网络请求失败").take(240)
    }
}
