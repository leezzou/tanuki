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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.TimeZone
import java.util.UUID

enum class DshAttachmentStatus { READY, UPLOADING, UPLOADED, FAILED }

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
    val uploadBusy: Boolean = false,
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

    fun removeAttachment(id: String) {
        _state.update { state -> state.copy(attachments = state.attachments.filterNot { it.id == id }) }
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
        val submittedText = current.text
        val submittedAttachmentIds = attachments.map { it.id }.toSet()
        val requestGeneration = generation
        _state.update { it.copy(sending = true, error = null, notice = null) }
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
                        mentionQuery = null,
                        mentionCandidates = emptyList(),
                        commandCandidates = emptyList(),
                        notice = if (keptEdits) "已发送；发送期间的新编辑已保留" else "已发送"
                    )
                }
                onSent(sessionId)
            }.onFailure { error ->
                _state.update { it.copy(sending = false, error = messageOf(error)) }
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
        _state.update { it.copy(mentionLoading = true, mentionError = null) }
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

    private fun filterCommands(commands: List<DshCommand>, query: String): List<DshCommand> {
        if (query.isEmpty()) return commands
        return commands.filter { it.name.startsWith(query, ignoreCase = true) }
    }

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
                val next = _state.value.attachments.firstOrNull {
                    it.kind == DshAttachmentKind.FILE && it.status == DshAttachmentStatus.READY
                } ?: break
                _state.update { state ->
                    state.copy(attachments = state.attachments.map {
                        if (it.id == next.id) it.copy(status = DshAttachmentStatus.UPLOADING, error = null) else it
                    }, uploadBusy = true)
                }
                val result = client.uploadFile(sessionId, next.name, next.bytes)
                if (requestGeneration != generation) return@launch
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
            }
            _state.update { it.copy(uploadBusy = false) }
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
