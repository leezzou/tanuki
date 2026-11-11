package com.tanuki.ui

import com.tanuki.runtime.Redact
import com.tanuki.web.DshApiException
import com.tanuki.web.DshChatSource
import com.tanuki.web.DshCoordination
import com.tanuki.web.DshLiveEvent
import com.tanuki.web.DshMessage
import com.tanuki.web.DshMessageRole
import com.tanuki.web.DshSession
import com.tanuki.web.DshSessionPage
import com.tanuki.web.DshTimelineItem
import com.tanuki.web.DshToolItem
import com.tanuki.web.DshToolMerge
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
import java.io.IOException

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
    /** 更早历史正在加载；页面顶部据此显示进度而不是按钮。 */
    val loadingOlder: Boolean = false,
    /** 向前分页失败原因；已加载页保持不动。 */
    val olderError: String? = null,
    /** 头部行发生变化（插入更早页或释放窗口）时递增；界面据此按行 key 恢复视口。 */
    val historyRevision: Int = 0,
    /** 已从内存释放的更早消息条数；再向上翻页时会重新读取。 */
    val releasedOlder: Int = 0,
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
    val tools: List<DshToolItem> = emptyList(),
    /** 最近一次发送失败的原文与原因；保留在页面上供复制、重试或回填输入框。 */
    val failedPrompt: DshFailedPrompt? = null,
    /** 真实目标事件投影出的目标条状态；没有真实数据时为 null（整条隐藏）。 */
    val goal: GoalDockState? = null,
    /** 真实待办事件/待办工具调用投影出的待办条状态；没有真实数据时为 null（整条隐藏）。 */
    val todos: TodoDockState? = null,
    /** 两类投影独立比较序号；清除后仍保留序号，防止旧页重新显示已移除的条。 */
    val lastGoalSeq: Int? = null,
    val lastTodoSeq: Int? = null
) {
    val selectedSession: DshSession? get() = sessions.firstOrNull { it.id == selectedId }
}

/** 发送失败后保留的本地内容；不写入官方会话，也不伪造已送达。 */
data class DshFailedPrompt(
    val text: String,
    val reason: String,
    val time: Long,
    /** 本次失败提交携带的附件数量；非 0 时不能直接按当前草稿重试。 */
    val attachmentCount: Int = 0
)

/**
 * 原生页控制器。列表/历史失败仍按原策略回退官方；实时流或单次操作失败只提示，
 * 不把整个原生页判死，也不伪造消息、审批或运行状态。
 */
class DshNativeController(
    private val client: DshChatSource,
    private val scope: CoroutineScope,
    private val renameRemote: (suspend (String, String) -> Result<String>)? = null
) {
    private val _state = MutableStateFlow(DshNativeState())
    val state: StateFlow<DshNativeState> = _state.asStateFlow()
    private var pageJob: Job? = null
    private var liveJob: Job? = null
    private var generation = 0
    private var pendingSeq = 0
    /** 通知点击目标；会话列表尚未加载时先保存，加载完成后选中。 */
    private var pendingSelection: String? = null
    /** 流式文本按帧合并：事件只更新待发布值，真正的状态变更每个帧窗口最多一次。 */
    private var pendingStreamText: String? = null
    private var pendingStreamReset = false
    private var streamFlushJob: Job? = null
    /** 视口顶部行的 seq；只用于判断窗口可以释放哪些“视口上方”的行。 */
    private var viewportTopSeq: Int? = null
    /** 本次实时连接是否已经收到过 Connected；用于区分首连与断线重连。 */
    private var liveConnected = false

    fun refresh() {
        val token = ++generation
        val target = pendingSelection
        pageJob?.cancel()
        liveJob?.cancel()
        resetStreaming()
        viewportTopSeq = null
        liveConnected = false
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

    val canRenameSession: Boolean get() = renameRemote != null

    /** 重命名复用官方接口；失败保留原标题，切换会话后不再发布旧请求结果。 */
    suspend fun renameSession(sessionId: String, title: String): Result<Unit> {
        val rename = renameRemote ?: return Result.failure(IOException("当前无法重命名会话"))
        val clean = title.trim()
        if (clean.isEmpty() || clean.length > 200 || clean.any { it.code < 0x20 || it.code == 0x7f }) {
            return Result.failure(IllegalArgumentException("标题不能为空、不能超过 200 字或包含控制字符"))
        }
        if (_state.value.selectedId != sessionId) return Result.failure(IOException("会话已切换，请重新打开标题"))
        val token = generation
        return rename(sessionId, clean).fold(
            onSuccess = { officialTitle ->
                if (token != generation) {
                    Result.failure(IOException("会话已切换，请刷新查看标题"))
                } else {
                    _state.update { state ->
                        state.copy(sessions = state.sessions.map { session ->
                            if (session.id == sessionId) session.copy(title = officialTitle) else session
                        })
                    }
                    Result.success(Unit)
                }
            },
            onFailure = { Result.failure(IOException(messageOf(it))) }
        )
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
        resetStreaming()
        viewportTopSeq = null
        liveConnected = false
        val session = current.sessions.firstOrNull { it.id == sessionId }
        _state.update {
            it.copy(
                selectedId = sessionId,
                messages = emptyList(),
                tools = emptyList(),
                error = null,
                hasMore = false,
                loadingOlder = false,
                olderError = null,
                historyRevision = 0,
                releasedOlder = 0,
                goal = null,
                todos = null,
                lastGoalSeq = null,
                lastTodoSeq = null,
                loading = true,
                running = session?.running ?: it.running,
                stopping = false,
                sendError = null,
                actionError = null,
                notice = null,
                streamingText = "",
                failedPrompt = null
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
                failedPrompt = null,
                messages = it.messages + pending
            )
        }
        return client.send(sessionId, prompt).fold(
            onSuccess = {
                // 只允许仍属于发起会话的结果修改状态或触发历史刷新。
                if (token != generation || _state.value.selectedId != sessionId) {
                    return@fold false
                }
                _state.update { it.copy(sending = false, notice = "已交给官方 DSH", failedPrompt = null) }
                // follow 正常时由实时消息替换占位；follow 已结束/滞后时也补一次历史读取，避免停在“发送中”。
                refreshMessages(sessionId, token)
                true
            },
            onFailure = { error ->
                if (token != generation || _state.value.selectedId != sessionId) {
                    return@fold false
                }
                val reason = messageOf(error)
                _state.update { state ->
                    state.copy(
                        sending = false,
                        sendError = reason,
                        failedPrompt = DshFailedPrompt(prompt, reason, System.currentTimeMillis()),
                        running = wasRunning,
                        messages = state.messages.filterNot { it.pending && it.text == prompt }
                    )
                }
                false
            }
        )
    }

    /** 重试最近一次发送失败的原文；不清空用户已编辑的输入框，也不伪造官方重发。 */
    suspend fun retryFailedPrompt(): Boolean {
        val failed = _state.value.failedPrompt ?: return false
        if (_state.value.sending) return false
        return send(failed.text)
    }

    /** 官方没有独立“重新生成”一元方法；这里明确重发上一条用户消息，不伪造隐藏重试。 */
    suspend fun retryLastPrompt(): Boolean {
        val state = _state.value
        if (state.running) return false
        state.failedPrompt?.let { failed -> return send(failed.text) }
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
        resetStreaming()
        pageJob = null
        liveJob = null
        pendingSelection = null
        viewportTopSeq = null
        liveConnected = false
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
        liveConnected = false
        liveJob = scope.launch {
            try {
                client.live(sessionId).collect { event ->
                    if (token != generation) return@collect
                    applyLive(event, sessionId, token)
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

    private fun applyLive(event: DshLiveEvent, sessionId: String, token: Int) {
        when (event) {
            DshLiveEvent.Connected -> {
                val reconnected = liveConnected
                liveConnected = true
                _state.update { it.copy(live = true, liveError = null) }
                // 断线期间的历史事件可能已丢；重连后主动对账一次，首连仍交给历史页/快照。
                if (reconnected && _state.value.messages.isNotEmpty()) {
                    refreshMessages(sessionId, token)
                }
            }
            is DshLiveEvent.Snapshot -> {
                cancelStreamFlush()
                _state.update { state ->
                    state.copy(
                        loading = false,
                        live = true,
                        liveError = null,
                        // 快照只覆盖自己的窗口：已加载的更早分页和更新的实时行都要保留。
                        messages = DshMessageLedger.reconcile(state.messages, event.messages),
                        // 合并阶段不截断工具，窗口与视口释放统一由 trimWindow 负责。
                        tools = DshToolMerge.merge(state.tools, event.tools, maxTools = null),
                        hasMore = state.hasMore || event.hasMore,
                        streamingText = event.liveText
                    ).withCoordination(event.coordination)
                }
            }
            is DshLiveEvent.Message -> {
                if (event.message.role == DshMessageRole.ASSISTANT) cancelStreamFlush()
                _state.update { state ->
                    state.copy(
                        // 同 seq 重复投递只保留一份；重放/重连不会插入第二条气泡。
                        messages = DshMessageLedger.merge(state.messages, listOf(event.message)),
                        streamingText = if (event.message.role == DshMessageRole.ASSISTANT) "" else state.streamingText
                    )
                }
            }
            is DshLiveEvent.Timeline -> _state.update { state ->
                state.copy(tools = DshToolMerge.merge(state.tools, event.tools, maxTools = null))
            }
            is DshLiveEvent.Streaming -> scheduleStreaming(event.text)
            is DshLiveEvent.Finished -> {
                // 结束事件到达时先同步发布最后一帧，避免 committed 文本停在上一帧。
                if (event.committed) commitStreamingNow() else resetStreaming()
            }
            is DshLiveEvent.Running -> {
                if (!event.running) resetStreaming()
                _state.update { state ->
                    state.copy(
                        running = event.running,
                        stopping = if (event.running) state.stopping else false,
                        streamingText = if (event.running) state.streamingText else "",
                        sessions = state.sessions.map { session ->
                            if (session.id == state.selectedId) session.copy(running = event.running) else session
                        }
                    )
                }
            }
            is DshLiveEvent.Failure -> _state.update { it.copy(liveError = event.message) }
            is DshLiveEvent.Coordination -> _state.update { it.withCoordination(event.update) }
        }
    }

    /**
     * 同一帧窗口内只保留最新全文；旧增量不会逐条触发整段 Markdown 重排。
     * 空串是官方新 assistant-stream 的“重置”信号，必须单独保留，不能被子帧覆盖。
     */
    private fun scheduleStreaming(text: String) {
        if (text.isEmpty()) pendingStreamReset = true
        pendingStreamText = text
        if (streamFlushJob?.isActive == true) return
        streamFlushJob = scope.launch {
            delay(DshStreamPolicy.FRAME_MILLIS)
            publishPendingStream()
        }
    }

    private fun publishPendingStream() {
        streamFlushJob = null
        val pending = pendingStreamText ?: return
        val reset = pendingStreamReset
        pendingStreamText = null
        pendingStreamReset = false
        applyStreamFrame(DshStreamFrame(pending, reset))
    }

    private fun commitStreamingNow() {
        streamFlushJob?.cancel()
        streamFlushJob = null
        val pending = pendingStreamText
        val reset = pendingStreamReset
        pendingStreamText = null
        pendingStreamReset = false
        if (pending != null) applyStreamFrame(DshStreamFrame(pending, reset))
    }

    private fun applyStreamFrame(frame: DshStreamFrame) {
        _state.update { state ->
            val merged = DshStreamPolicy.mergeFrame(state.streamingText, frame)
            if (merged == state.streamingText) state else state.copy(streamingText = merged)
        }
    }

    private fun cancelStreamFlush() {
        streamFlushJob?.cancel()
        streamFlushJob = null
        pendingStreamText = null
        pendingStreamReset = false
    }

    private fun resetStreaming() {
        cancelStreamFlush()
        _state.update { if (it.streamingText.isEmpty()) it else it.copy(streamingText = "") }
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
                // 文本与停靠条各用自己的序号；文本旧页也可能携带较新的目标/待办投影。
                val reconciled = if (state.messages.isEmpty() || pageMax >= currentMax) {
                    val pageMin = page.messages.minOfOrNull { it.seq }
                    val loadedOldest = DshChatWindowPolicy.oldestSeq(state.messages, state.tools)
                    // 只有这一页真正接到已加载窗口的起点时，它的 hasMore 才代表“还有更早历史”；
                    // 快照先到且更窄时不能被最近一页的 hasMore 覆盖。
                    val pageReachesLoadedStart =
                        pageMin != null && (loadedOldest == null || pageMin <= loadedOldest)
                    state.copy(
                        loading = false,
                        // 首次用页面铺底；刷新/重连只做对账，不删除已加载的更早分页。
                        messages = if (state.messages.isEmpty()) {
                            DshMessageLedger.merge(emptyList(), page.messages)
                        } else {
                            DshMessageLedger.reconcile(state.messages, page.messages)
                        },
                        tools = DshToolMerge.merge(
                            state.tools,
                            page.timeline.filterIsInstance<DshTimelineItem.Tool>().map { it.tool },
                            maxTools = null
                        ),
                        hasMore = if (pageReachesLoadedStart) page.hasMore else state.hasMore
                    )
                } else {
                    state.copy(loading = false)
                }
                reconciled.withCoordination(page.coordination)
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
     * 向上翻页：按当前窗口最早行之前的 seq 读取一页；结果按 seq 合并排序，
     * 重复页不会插入第二份，失败时保留已加载内容。
     */
    fun loadOlder() {
        val snapshot = _state.value
        val sessionId = snapshot.selectedId ?: return
        if (snapshot.loading || snapshot.loadingOlder || !snapshot.hasMore) return
        val before = DshChatWindowPolicy.beforeSeq(
            DshChatWindowPolicy.oldestSeq(snapshot.messages, snapshot.tools)
        )
        if (before == null) {
            _state.update { it.copy(hasMore = false, olderError = null) }
            return
        }
        val token = generation
        _state.update { it.copy(loadingOlder = true, olderError = null) }
        scope.launch {
            val result = client.messagesBefore(sessionId, before, DshChatWindowPolicy.PAGE_SIZE)
            if (token != generation || _state.value.selectedId != sessionId) return@launch
            result.onSuccess { page -> applyOlderPage(page, before) }.onFailure { error ->
                _state.update { state ->
                    // 失败只提示，不清空已经读到的历史，也不改 hasMore。
                    state.copy(loadingOlder = false, olderError = messageOf(error))
                }
            }
        }
    }

    private fun applyOlderPage(page: DshSessionPage, beforeSeq: Int) {
        // 双重防御：只接受早于游标的行；服务端忽略 throughSeq 时不会把当前页重复插到头部。
        val older = page.messages.filter { !it.pending && it.seq <= beforeSeq }
        val tools = page.timeline.filterIsInstance<DshTimelineItem.Tool>().map { it.tool }
        _state.update { state ->
            val merged = if (older.isEmpty()) state.messages else DshMessageLedger.merge(state.messages, older)
            val grew = merged.size > state.messages.size
            state.copy(
                messages = merged,
                tools = if (grew) DshToolMerge.merge(state.tools, tools, maxTools = null) else state.tools,
                loadingOlder = false,
                // 没有新增行时不再续页，避免服务端重复返回同一页导致死循环。
                hasMore = if (grew) page.hasMore else false,
                olderError = null,
                historyRevision = if (grew) state.historyRevision + 1 else state.historyRevision
            )
        }
    }

    /**
     * 界面报告视口顶部行的 seq；只在窗口超出预算时释放该行上方的消息。
     * 传入 null 表示没有可参考视口（列表尚未布局），此时只按预算保留最新行。
     */
    fun onViewportTop(seq: Int?) {
        val normalized = seq?.takeIf { it > 0 }
        val previous = viewportTopSeq
        if (normalized == previous) return
        viewportTopSeq = normalized
        // 上翻（视口首行 seq 变小）不释放，避免刚加载的更早页立刻被裁掉；
        // 首次布局或向下滚动时才按预算释放视口上方的行。
        if (previous == null || (normalized != null && normalized > previous)) trimWindow()
    }

    /** 内存窗口：消息与工具按同一视口边界释放；界面按 historyRevision 恢复滚动位置。 */
    private fun trimWindow() {
        _state.update { state ->
            if (state.loading) return@update state
            val window = DshChatWindowPolicy.trim(
                messages = state.messages,
                tools = state.tools,
                keepFromSeq = viewportTopSeq
            )
            if (!window.trimmed) return@update state
            state.copy(
                messages = window.messages,
                tools = window.tools,
                // 释放的行仍可从官方历史页重新读取，因此重新标记为“还有更早历史”。
                hasMore = true,
                releasedOlder = state.releasedOlder + window.releasedMessages,
                historyRevision = state.historyRevision + 1
            )
        }
    }

    /**
     * 历史页、快照和增量统一按各类投影序号合并，包括清除投影；重放与旧页不能回退状态。
     * 字段缺失、未知目标状态或解析失败不改变已有状态，也不推进该类序号。
     */
    private fun DshNativeState.withCoordination(update: DshCoordination): DshNativeState {
        var merged = this
        update.goal?.let { projection ->
            if (lastGoalSeq == null || projection.seq > lastGoalSeq) {
                val mapped = DshDockStateMapper.goal(projection)
                if (projection.clearsDock || mapped != null) {
                    merged = merged.copy(goal = mapped, lastGoalSeq = projection.seq)
                }
            }
        }
        update.todos?.let { projection ->
            if (lastTodoSeq == null || projection.seq > lastTodoSeq) {
                val mapped = DshDockStateMapper.todo(projection)
                if (projection.items.isEmpty() || mapped != null) {
                    merged = merged.copy(todos = mapped, lastTodoSeq = projection.seq)
                }
            }
        }
        return merged
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

    private fun messageOf(error: Throwable): String = when (error) {
        is DshApiException -> "官方接口返回 ${error.code}"
        is JSONException, is IllegalArgumentException -> "官方接口响应格式不正确"
        else -> Redact.text(error.message ?: "未知错误").take(200)
    }

    private companion object {
        const val MAX_PROMPT_CHARS = 12_000
    }
}
