package com.tanuki.ui

import com.tanuki.runtime.Redact
import com.tanuki.web.DshApiException
import com.tanuki.web.DshChatSource
import com.tanuki.web.DshLiveEvent
import com.tanuki.web.DshMessage
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshSession
import com.tanuki.web.DshTimelineItem
import com.tanuki.web.DshToolItem
import com.tanuki.web.DshToolStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException

/**
 * 原生页状态；会话、历史、发送与审批都直接来自官方 DSH，不维护第二套聊天库。
 * `messages` 中的 `pending=true` 项只是本地“发送中”占位，官方落盘后会被替换。
 */
data class DshNativeState(
    val loading: Boolean = false,
    val sessions: List<DshSession> = emptyList(),
    val selectedId: String? = null,
    val messages: List<DshMessage> = emptyList(),
    val hasMore: Boolean = false,
    val error: String? = null,
    /** 会话列表都读不到时置位；界面据此回退官方页面。 */
    val unavailableReason: String? = null,
    val sending: Boolean = false,
    val stopping: Boolean = false,
    val running: Boolean = false,
    val live: Boolean = false,
    val liveError: String? = null,
    val sendError: String? = null,
    val actionError: String? = null,
    val notice: String? = null,
    val streamingText: String = "",
    /** 与文本消息按 seq 交错渲染的工具调用卡片；来自同一官方 session/page 或 follow 事件。 */
    val tools: List<DshToolItem> = emptyList()
) {
    val selectedSession: DshSession? get() = sessions.firstOrNull { it.id == selectedId }
}

/**
 * 原生页控制器。列表/历史失败仍按原策略回退官方；实时流或单次操作失败只提示，
 * 不把整个原生页判死，也不伪造消息、审批或运行状态。
 */
class DshNativeController(
    private val client: DshChatSource,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(DshNativeState())
    val state: StateFlow<DshNativeState> = _state.asStateFlow()
    private var pageJob: Job? = null
    private var liveJob: Job? = null
    private var generation = 0
    private var pendingSeq = 0
    /** 通知点击目标；会话列表尚未加载时先保存，加载完成后选中。 */
    private var pendingSelection: String? = null

    fun refresh() {
        val token = ++generation
        val target = pendingSelection
        pageJob?.cancel()
        liveJob?.cancel()
        _state.value = DshNativeState(loading = true)
        pageJob = scope.launch {
            val result = client.sessions()
            if (token != generation) return@launch
            result.onSuccess { sessions ->
                val targetSession = target?.let { id ->
                    sessions.firstOrNull { it.id == id } ?: DshSession(
                        id = id,
                        title = "通知会话 ${id.take(8)}",
                        updatedAt = 0L,
                        running = false,
                        blank = false,
                        agentAvailable = false,
                        asOfSeq = null
                    )
                }
                val selected = targetSession ?: sessions.firstOrNull()
                pendingSelection = null
                if (selected == null) {
                    _state.value = DshNativeState(sessions = emptyList())
                } else {
                    // 通知目标不在列表时仍加入可见会话条，避免页面继续显示旧的第一项。
                    val visible = if (targetSession != null && sessions.none { it.id == selected.id }) {
                        sessions + targetSession
                    } else sessions
                    _state.value = DshNativeState(
                        sessions = visible,
                        selectedId = selected.id,
                        loading = true,
                        running = selected.running
                    )
                    startLive(selected.id, token)
                    loadMessages(selected.id, token)
                }
            }.onFailure { error ->
                val message = messageOf(error)
                _state.value = DshNativeState(loading = false, error = message, unavailableReason = message)
            }
        }
    }

    /** 通知点击：优先选择已加载列表中的目标；未加载/不在列表时刷新后再选中。 */
    fun openSession(sessionId: String) {
        if (sessionId.isBlank() || sessionId.length > 128 ||
            !sessionId.all { it.code in 0x21..0x7e }) return
        pendingSelection = sessionId
        val current = _state.value
        if (current.sessions.any { it.id == sessionId }) {
            pendingSelection = null
            // 通知点击必须重新读取历史并重连实时流，不能复用旧页缓存。
            select(sessionId, force = true)
        } else {
            refresh()
        }
    }

    fun select(sessionId: String, force: Boolean = false) {
        if (sessionId.isBlank()) return
        val current = _state.value
        if (!force && current.selectedId == sessionId && current.error == null &&
            current.messages.isNotEmpty() && current.live
        ) return
        pendingSelection = null
        val token = ++generation
        pageJob?.cancel()
        liveJob?.cancel()
        val session = current.sessions.firstOrNull { it.id == sessionId }
        _state.update {
            it.copy(
                selectedId = sessionId,
                messages = emptyList(),
                tools = emptyList(),
                error = null,
                hasMore = false,
                loading = true,
                running = session?.running ?: it.running,
                stopping = false,
                sendError = null,
                actionError = null,
                notice = null,
                streamingText = ""
            )
        }
        startLive(sessionId, token)
        pageJob = scope.launch { loadMessages(sessionId, token) }
    }

    /** 发送失败返回 false；成功只表示官方已接受，不代表回合已完成。 */
    suspend fun send(text: String): Boolean {
        val current = _state.value
        val sessionId = current.selectedId ?: return false
        val prompt = text.trim()
        if (prompt.isEmpty() || current.sending) return false
        // 发送是跨网络的长操作；响应回来时用户可能已切到别的会话，必须绑定发起时的代次。
        val token = generation
        val wasRunning = current.running
        if (prompt.length > MAX_PROMPT_CHARS) {
            _state.update { it.copy(sendError = "消息过长（最多 $MAX_PROMPT_CHARS 字）") }
            return false
        }
        val pending = DshMessage(
            seq = --pendingSeq,
            role = DshMessageRole.USER,
            text = prompt,
            time = System.currentTimeMillis(),
            pending = true
        )
        _state.update {
            it.copy(
                sending = true,
                sendError = null,
                actionError = null,
                notice = null,
                running = true,
                messages = it.messages + pending
            )
        }
        return client.send(sessionId, prompt).fold(
            onSuccess = {
                // 只允许仍属于发起会话的结果修改状态或触发历史刷新。
                if (token != generation || _state.value.selectedId != sessionId) {
                    return@fold false
                }
                _state.update { it.copy(sending = false, notice = "已交给官方 DSH") }
                // follow 正常时由实时消息替换占位；follow 已结束/滞后时也补一次历史读取，避免停在“发送中”。
                refreshMessages(sessionId, token)
                true
            },
            onFailure = { error ->
                if (token != generation || _state.value.selectedId != sessionId) {
                    return@fold false
                }
                _state.update { state ->
                    state.copy(
                        sending = false,
                        sendError = messageOf(error),
                        running = wasRunning,
                        messages = state.messages.filterNot { it.pending && it.text == prompt }
                    )
                }
                false
            }
        )
    }

    /** 官方没有独立“重新生成”一元方法；这里明确重发上一条用户消息，不伪造隐藏重试。 */
    suspend fun retryLastPrompt(): Boolean {
        val state = _state.value
        if (state.running) return false
        val last = state.messages.lastOrNull { it.role == DshMessageRole.USER && !it.pending } ?: return false
        return send(last.text)
    }

    /** 停止只取消当前活动回合，不停止 DSH 服务；官方状态流会决定 running 何时结束。 */
    suspend fun stop(): Boolean {
        val current = _state.value
        val sessionId = current.selectedId ?: return false
        if (current.stopping) return false
        _state.update { it.copy(stopping = true, actionError = null, notice = null) }
        val ok = client.cancel(sessionId).fold(
            onSuccess = {
                _state.update { it.copy(stopping = false, notice = "已请求停止当前回合") }
                scope.launch {
                    delay(4_000)
                    _state.update { state ->
                        if (state.running && state.selectedId == sessionId) {
                            state.copy(notice = "停止请求已发送；若仍在运行可再次点击")
                        } else state
                    }
                }
                true
            },
            onFailure = { error ->
                _state.update { it.copy(stopping = false, actionError = messageOf(error)) }
                false
            }
        )
        return ok
    }

    fun clear() {
        generation++
        pageJob?.cancel()
        liveJob?.cancel()
        pageJob = null
        liveJob = null
        pendingSelection = null
        _state.value = DshNativeState()
        // 同时中止仍在进行的 /connect 与 mux；切换界面不停止 DSH 服务。
        client.reset()
    }

    /** 服务停止或页面销毁时丢弃内存 Cookie 与实时 socket；仅切换界面不重启服务。 */
    fun close() {
        clear()
    }

    private fun startLive(sessionId: String, token: Int) {
        liveJob?.cancel()
        liveJob = scope.launch {
            try {
                client.live(sessionId).collect { event ->
                    if (token != generation) return@collect
                    applyLive(event)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // 实时通道的建连/协议异常不能逃逸到 lifecycleScope 并取消整个页面协程域。
                if (token == generation) {
                    _state.update { it.copy(live = false, liveError = messageOf(error)) }
                }
            } finally {
                if (token == generation) {
                    _state.update { it.copy(live = false) }
                }
            }
        }
    }

    private fun applyLive(event: DshLiveEvent) {
        when (event) {
            // 新 mux 连接会重新收到仍待处理的审批；先清掉上一连接的旧卡片，避免幽灵按钮。
            DshLiveEvent.Connected -> _state.update {
                it.copy(live = true, liveError = null)
            }
            is DshLiveEvent.Snapshot -> _state.update { state ->
                state.copy(
                    loading = false,
                    live = true,
                    liveError = null,
                    messages = mergeMessages(state.messages, event.messages, replace = true),
                    tools = mergeTools(state.tools, event.tools),
                    hasMore = event.hasMore,
                    streamingText = event.liveText
                )
            }
            is DshLiveEvent.Message -> _state.update { state ->
                state.copy(
                    messages = mergeMessages(state.messages, listOf(event.message), replace = false),
                    streamingText = if (event.message.role == DshMessageRole.ASSISTANT) "" else state.streamingText
                )
            }
            is DshLiveEvent.Timeline -> _state.update { state ->
                state.copy(tools = mergeTools(state.tools, event.tools))
            }
            is DshLiveEvent.Streaming -> _state.update { it.copy(streamingText = event.text) }
            is DshLiveEvent.Finished -> _state.update {
                if (event.committed) it else it.copy(streamingText = "")
            }
            is DshLiveEvent.Running -> _state.update { state ->
                state.copy(
                    running = event.running,
                    stopping = if (event.running) state.stopping else false,
                    streamingText = if (event.running) state.streamingText else "",
                    sessions = state.sessions.map { session ->
                        if (session.id == state.selectedId) session.copy(running = event.running) else session
                    }
                )
            }
            is DshLiveEvent.Failure -> _state.update { it.copy(liveError = event.message) }
        }
    }

    private fun refreshMessages(sessionId: String, token: Int) {
        if (token != generation || _state.value.selectedId != sessionId) return
        pageJob?.cancel()
        pageJob = scope.launch { loadMessages(sessionId, token) }
    }

    private suspend fun loadMessages(sessionId: String, token: Int) {
        _state.update { it.copy(loading = true, error = null) }
        val result = client.messages(sessionId)
        if (token != generation) return
        result.onSuccess { page ->
            _state.update { state ->
                val currentMax = state.messages.filterNot { it.pending }.maxOfOrNull { it.seq } ?: -1
                val pageMax = page.messages.maxOfOrNull { it.seq } ?: -1
                // 实时快照可能比这次 HTTP 历史更新；旧页不能覆盖它。
                if (state.messages.isEmpty() || pageMax >= currentMax) {
                    state.copy(
                        loading = false,
                        messages = mergeMessages(state.messages, page.messages, replace = true),
                        tools = mergeTools(
                            state.tools,
                            page.timeline.filterIsInstance<DshTimelineItem.Tool>().map { it.tool }
                        ),
                        hasMore = page.hasMore
                    )
                } else {
                    state.copy(loading = false)
                }
            }
        }.onFailure { error ->
            // 实时快照已到达时，历史页失败不覆盖已显示的消息。
            _state.update { state ->
                state.copy(
                    loading = false,
                    error = if (state.messages.isEmpty()) messageOf(error) else state.error
                )
            }
        }
    }

    /**
     * 合并官方消息与本地发送中占位：按官方 seq 去重，快照替换时保留尚未落盘的
     * 本地消息；一旦官方出现相同文本的用户消息，就移除占位。
     */
    private fun mergeMessages(
        current: List<DshMessage>,
        incoming: List<DshMessage>,
        replace: Boolean
    ): List<DshMessage> {
        val durable = LinkedHashMap<Int, DshMessage>()
        if (!replace) current.filterNot { it.pending }.forEach { durable[it.seq] = it }
        incoming.filterNot { it.pending }.forEach { durable[it.seq] = it }
        val pending = current.filter { it.pending }.filter { local ->
            incoming.none { official ->
                !official.pending && official.role == DshMessageRole.USER && official.text == local.text
            }
        }
        return (durable.values + pending).sortedWith(
            compareBy<DshMessage> { it.pending }.thenBy { if (it.pending) it.time else it.seq.toLong() }
        )
    }

    /**
     * 按审批所属会话读取工具调用；优先用当前已加载的工具卡片，否则单独读取该会话历史。
     * 不能用当前选中会话的旧快照回答另一会话的审批。
     */
    suspend fun tool(sessionId: String, callId: String): DshToolItem? {
        if (sessionId.isBlank() || callId.isBlank()) return null
        val current = _state.value
        if (current.selectedId == sessionId) {
            current.tools.firstOrNull { it.callId == callId }?.let { return it }
        }
        val page = client.messages(sessionId).getOrNull() ?: return null
        return page.timeline.filterIsInstance<DshTimelineItem.Tool>()
            .map { it.tool }
            .firstOrNull { it.callId == callId }
    }

    /** 同一 callId 的 tool/result 回填调用卡片；保留先到的名称/参数，只更新结果字段。 */
    private fun mergeTools(current: List<DshToolItem>, incoming: List<DshToolItem>): List<DshToolItem> {
        if (incoming.isEmpty()) return current
        val merged = LinkedHashMap<String, DshToolItem>()
        current.forEach { merged[it.callId] = it }
        incoming.forEach { item ->
            val previous = merged[item.callId]
            // 迟到的历史页可能仍是 RUNNING；已经收到终态的卡片不能被回退。
            if (previous != null && previous.status != DshToolStatus.RUNNING &&
                item.status == DshToolStatus.RUNNING
            ) return@forEach
            merged[item.callId] = if (previous == null) item else previous.copy(
                name = item.name.takeIf { it.isNotBlank() && it != "未知工具" } ?: previous.name,
                arguments = item.arguments ?: previous.arguments,
                status = item.status,
                output = item.output ?: previous.output,
                error = item.error ?: previous.error,
                command = item.command ?: previous.command,
                cwd = item.cwd ?: previous.cwd,
                diffs = item.diffs.ifEmpty { previous.diffs },
                files = item.files.ifEmpty { previous.files },
                outputTruncated = item.outputTruncated || previous.outputTruncated,
                risk = item.risk
            )
        }
        return merged.values.sortedBy { it.seq }.takeLast(200)
    }

    private fun messageOf(error: Throwable): String = when (error) {
        is DshApiException -> "官方接口返回 ${error.code}"
        is JSONException, is IllegalArgumentException -> "官方接口响应格式不正确"
        else -> Redact.text(error.message ?: "未知错误").take(200)
    }

    private companion object {
        const val MAX_PROMPT_CHARS = 12_000
    }
}
