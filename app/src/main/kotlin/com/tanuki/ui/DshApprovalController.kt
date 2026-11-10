package com.tanuki.ui

import com.tanuki.web.DshApprovalMemory
import com.tanuki.web.DshApprovalOutcome
import com.tanuki.web.DshApprovalFailure
import com.tanuki.web.DshApprovalFailures
import com.tanuki.web.DshApprovalRequest
import com.tanuki.web.DshApprovalRisk
import com.tanuki.web.DshApprovalSession
import com.tanuki.web.DshApprovalTransport
import com.tanuki.web.DshEndpoint
import com.tanuki.web.DshRememberedChoice
import com.tanuki.web.DshRiskAssessment
import com.tanuki.web.DshRiskLevel
import com.tanuki.web.DshToolItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** 原生审批卡片的一项；参数与风险由该审批所属会话的工具调用补齐。 */
data class DshPendingApproval(
    val request: DshApprovalRequest,
    val arguments: String? = null,
    val risk: DshRiskAssessment = DshRiskAssessment(DshRiskLevel.UNKNOWN, "无法判断影响范围"),
    val answering: Boolean = false,
    val error: String? = null,
    /** 正在按 request.sessionId + callId 读取工具参数；此时禁止盲目允许。 */
    val lookupPending: Boolean = false,
    val lookupError: String? = null,
    /** 由“记住本次运行”自动回答，而不是用户刚点击。 */
    val automatic: Boolean = false
)

data class DshApprovalState(
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val ready: Boolean = false,
    val pending: List<DshPendingApproval> = emptyList(),
    val error: String? = null,
    /** 审批通道失败是否可重试；待审批项始终保留，不因断线静默删除。 */
    val errorRetryable: Boolean = false,
    /** 服务地址变化导致旧审批失效等需要明确告知用户的情况，不静默丢弃。 */
    val notice: String? = null,
    val remembered: List<DshRememberedChoice> = emptyList()
) {
    fun pendingFor(sessionId: String?): List<DshPendingApproval> =
        if (sessionId == null) emptyList() else pending.filter { it.request.sessionId == sessionId }

    fun otherPendingCount(sessionId: String?): Int =
        pending.count { it.request.sessionId != sessionId }

    /** 记住的选择存在时即使没有待审批，也要保留面板与“清除记住的选择”入口。 */
    fun shouldShowPanel(): Boolean =
        pending.isNotEmpty() || error != null || notice != null || connecting || remembered.isNotEmpty()
}

/**
 * 原生审批控制器。
 *
 * - 连接与回答都在 IO 线程；主线程只读 StateFlow。
 * - 每个请求都对应官方 eventId；允许/拒绝只通过官方 `$events/result` 回传。
 * - 收到审批后按 `request.sessionId + callId` 异步读取工具参数；历史后到时可通过
 *   [updateTool] 推送，参数/风险会更新，必要时再按记忆自动回答。
 * - “记住本次运行”只在内存中按会话 + 工具 + 风险等级复用，关闭原生页/服务停止即清空；
 *   不写 DSH 配置，不冒充官方持久授权。未知审批类型永远不允许。
 * - 任何连接/回答失败都保留待审批项并显示中文错误，不静默吞掉。
 */
class DshApprovalController(
    private val endpointProvider: () -> DshEndpoint?,
    private val scope: CoroutineScope,
    private val toolLookup: suspend (sessionId: String, callId: String) -> DshToolItem? = { _, _ -> null },
    private val sessionFactory: (DshApprovalSession.Listener) -> DshApprovalTransport =
        { listener -> DshApprovalSession(endpointProvider, listener) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _state = MutableStateFlow(DshApprovalState())
    val state: StateFlow<DshApprovalState> = _state.asStateFlow()
    private val memory = DshApprovalMemory()
    private var session: DshApprovalTransport? = null
    private var generation = 0
    private var connectJob: Job? = null
    private var connectedOrigin: String? = null
    /** eventId -> 发起回答时的连接代次；断线/重连后旧回答不能清理新状态。 */
    private val answering = HashMap<String, Int>()
    /** eventId -> 发起参数查询时的连接代次；重连后需要重新启动被旧代次丢弃的查询。 */
    private val lookupTokens = HashMap<String, Int>()

    /** 打开审批通道；重复调用不会建立第二条连接。 */
    fun start() {
        val endpointOrigin = endpointProvider()?.origin
        // 本地 DSH 重启、远程隧道换端口或安全模式切换都会改变 origin；旧连接不能继续回答。
        if (session != null && endpointOrigin != null && connectedOrigin != null && endpointOrigin != connectedOrigin) {
            session?.close()
            session = null
            connectedOrigin = null
            synchronized(answering) { answering.clear() }
            synchronized(lookupTokens) { lookupTokens.clear() }
            val dropped = _state.value.pending.size
            _state.value = DshApprovalState(
                remembered = memory.snapshot(),
                notice = if (dropped > 0) {
                    "DSH 服务地址已变化，$dropped 个旧待审批已失效；请到官方界面重新处理。"
                } else {
                    null
                }
            )
        }
        val current = _state.value
        if (session != null && (current.connecting || current.connected)) return
        session?.close()
        session = null
        val token = ++generation
        connectJob?.cancel()
        _state.update { it.copy(connecting = true, error = null, errorRetryable = false) }
        val created = sessionFactory(object : DshApprovalSession.Listener {
            override fun onReady() {
                if (token != generation) return
                _state.update {
                    it.copy(connecting = false, connected = true, ready = true, error = null, errorRetryable = false)
                }
                // 断线时未完成的参数查询会被旧代次丢弃；新流就绪后对保留项重新发起。
                resumePendingLookups()
            }

            override fun onApproval(request: DshApprovalRequest) {
                if (token != generation) return
                onApprovalRequest(request)
            }

            override fun onCancel(eventId: String) {
                if (token != generation) return
                _state.update { state -> state.copy(pending = state.pending.filterNot { it.request.eventId == eventId }) }
                synchronized(answering) { answering.remove(eventId) }
                synchronized(lookupTokens) { lookupTokens.remove(eventId) }
            }

            override fun onUnsupportedWaterfall(eventId: String) {
                if (token != generation) return
                val current = session ?: return
                scope.launch(ioDispatcher) {
                    current.answerNext(eventId).onFailure { error ->
                        if (session === current && token == generation) {
                            val failure = messageOf(error)
                            _state.update {
                                it.copy(
                                    error = "无法跳过非审批请求：${failure.message}",
                                    errorRetryable = failure.retryable
                                )
                            }
                        }
                    }
                }
            }

            override fun onDisconnected(message: String) {
                if (token != generation) return
                val current = session
                session = null
                connectedOrigin = null
                // 连接代次已失效：释放所有 in-flight 标记，避免重连后同一 eventId 永久卡住。
                synchronized(answering) { answering.clear() }
                synchronized(lookupTokens) { lookupTokens.clear() }
                current?.close()
                val failure = DshApprovalFailures.describe(IOException(message))
                _state.update {
                    it.copy(
                        connecting = false,
                        connected = false,
                        ready = false,
                        error = failure.message,
                        errorRetryable = failure.retryable,
                        pending = it.pending.map { item ->
                            if (item.answering) item.copy(answering = false, error = "连接已断开，未能回答；可重试") else item
                        }
                    )
                }
            }
        })
        session = created
        connectedOrigin = endpointOrigin
        connectJob = scope.launch(ioDispatcher) {
            val result = runCatching { created.connect() }
            if (token != generation) {
                created.close()
                return@launch
            }
            result.onSuccess {
                if (session !== created) {
                    created.close()
                    return@onSuccess
                }
                _state.update {
                    it.copy(connecting = false, connected = true, error = null, errorRetryable = false)
                }
            }.onFailure { error ->
                created.close()
                if (session === created) {
                    session = null
                    connectedOrigin = null
                }
                val failure = messageOf(error)
                _state.update {
                    it.copy(
                        connecting = false,
                        connected = false,
                        ready = false,
                        error = failure.message,
                        errorRetryable = failure.retryable
                    )
                }
            }
        }
    }

    /** 断开后手动重试；旧的待审批项保留，避免用户以为请求消失了。 */
    fun retry() {
        connectJob?.cancel()
        session?.close()
        session = null
        generation++
        // 旧代次在途回答/参数查询的标记必须清掉，否则同一 eventId 重连后会被当成仍在提交。
        val interrupted = synchronized(answering) { answering.keys.toSet() }
        synchronized(answering) { answering.clear() }
        synchronized(lookupTokens) { lookupTokens.clear() }
        _state.update {
            it.copy(
                connecting = false,
                connected = false,
                ready = false,
                error = null,
                errorRetryable = false,
                notice = null,
                pending = it.pending.map { item ->
                    if (item.request.eventId in interrupted) {
                        item.copy(answering = false, error = "回答已中断，可重试")
                    } else {
                        item
                    }
                }
            )
        }
        start()
    }

    /** 回答一项审批；`remember` 只影响本次原生运行内的后续相同工具/风险请求。 */
    fun answer(eventId: String, outcome: DshApprovalOutcome, remember: Boolean = false) {
        val item = _state.value.pending.firstOrNull { it.request.eventId == eventId } ?: return
        if (!_state.value.ready) {
            _state.update { state ->
                state.copy(pending = state.pending.map {
                    if (it.request.eventId == eventId) it.copy(error = "审批通道尚未就绪，请重试") else it
                })
            }
            return
        }
        if (item.request.unknownTool && outcome == DshApprovalOutcome.ALLOWED_ONCE) {
            _state.update { state ->
                state.copy(pending = state.pending.map {
                    if (it.request.eventId == eventId) it.copy(error = "未知审批类型只能拒绝") else it
                })
            }
            return
        }
        val token = generation
        synchronized(answering) {
            if (answering.containsKey(eventId)) return
            answering[eventId] = token
        }
        _state.update { state ->
            state.copy(pending = state.pending.map {
                if (it.request.eventId == eventId) it.copy(answering = true, error = null) else it
            })
        }
        val current = session
        if (current == null) {
            finishAnswer(eventId, token, null, IOException("审批通道已关闭"))
            return
        }
        scope.launch(ioDispatcher) {
            val result = current.answer(eventId, outcome)
            if (remember && result.isSuccess && session === current) {
                memory.remember(item.request.sessionId, item.request.toolName, item.risk.level, outcome)
            }
            // 即使连接已变化也要释放自己的 in-flight 标记；finishAnswer 会按代次校验归属。
            finishAnswer(eventId, token, outcome.takeIf { result.isSuccess }, result.exceptionOrNull())
        }
    }

    /** 历史后到时由 42 推送工具调用；按审批所属会话与 callId 更新参数/风险。 */
    fun updateTool(sessionId: String, tool: DshToolItem) {
        val item = _state.value.pending.firstOrNull {
            it.request.sessionId == sessionId && it.request.callId == tool.callId
        } ?: return
        applyTool(item.request.eventId, tool)
    }

    /**
     * 单独撤销一条“记住本次运行”的选择。
     *
     * 撤销立即写入内存表：之后到达的相同会话/工具/风险审批不会再自动回答；
     * 已经发出的官方审批结果无法撤回，也不应伪造撤回。
     */
    fun forgetRemembered(choice: DshRememberedChoice) {
        if (!memory.forget(choice)) return
        _state.update { it.copy(remembered = memory.snapshot()) }
    }

    /** 清除服务地址变化等一次性提示；不影响待审批项或记忆。 */
    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    /** 清空“记住本次运行”的选择；已发出的官方审批结果不受影响。 */
    fun clearRemembered() {
        memory.clear()
        _state.update { it.copy(remembered = emptyList()) }
    }

    /** 页面销毁或服务停止；默认同时清空本次运行的记忆。 */
    fun close(clearMemory: Boolean = true) {
        generation++
        connectJob?.cancel()
        connectJob = null
        session?.close()
        session = null
        connectedOrigin = null
        synchronized(answering) { answering.clear() }
        synchronized(lookupTokens) { lookupTokens.clear() }
        if (clearMemory) memory.clear()
        _state.value = DshApprovalState(remembered = if (clearMemory) emptyList() else memory.snapshot())
    }

    private fun onApprovalRequest(request: DshApprovalRequest) {
        val existing = _state.value.pending.firstOrNull { it.request.eventId == request.eventId }
        if (existing != null) {
            // Host 在新流重放同一 eventId 时不能只去重：旧代次查询可能已退出，必须重新启动。
            if (existing.lookupPending) {
                existing.request.callId?.let { lookupTool(existing.request, it) }
            }
            return
        }
        val baseRisk = if (request.unknownTool) {
            DshRiskAssessment(DshRiskLevel.HIGH, "未知审批类型；只能拒绝")
        } else {
            DshApprovalRisk.assess(request.toolName, null)
        }
        val item = DshPendingApproval(
            request = request,
            risk = baseRisk,
            lookupPending = !request.unknownTool && request.callId != null
        )
        _state.update { it.copy(pending = it.pending + item, notice = null) }
        if (request.unknownTool) return
        val callId = request.callId
        if (callId == null) {
            maybeAutoAnswer(request.eventId)
        } else {
            lookupTool(request, callId)
        }
    }

    private fun lookupTool(request: DshApprovalRequest, callId: String) {
        val token = generation
        synchronized(lookupTokens) {
            if (lookupTokens[request.eventId] == token) return
            lookupTokens[request.eventId] = token
        }
        scope.launch(ioDispatcher) {
            val tool = runCatching {
                withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { toolLookup(request.sessionId, callId) }
            }.getOrNull()
            if (token != generation) return@launch
            synchronized(lookupTokens) {
                if (lookupTokens[request.eventId] == token) lookupTokens.remove(request.eventId)
            }
            if (tool != null) applyTool(request.eventId, tool) else markLookupDone(request.eventId)
        }
    }

    /** 新连接就绪后重新启动仍标记为 lookupPending 的查询。 */
    private fun resumePendingLookups() {
        val pending = _state.value.pending
        for (item in pending) {
            if (item.lookupPending && !item.request.unknownTool) {
                item.request.callId?.let { lookupTool(item.request, it) }
            }
        }
    }

    private fun applyTool(eventId: String, tool: DshToolItem) {
        val item = _state.value.pending.firstOrNull { it.request.eventId == eventId } ?: return
        val risk = if (item.request.unknownTool) {
            DshRiskAssessment(DshRiskLevel.HIGH, "未知审批类型；只能拒绝")
        } else {
            DshApprovalRisk.assess(item.request.toolName, tool.arguments)
        }
        _state.update { state ->
            state.copy(pending = state.pending.map {
                if (it.request.eventId == eventId) {
                    it.copy(
                        arguments = tool.arguments,
                        risk = risk,
                        lookupPending = false,
                        lookupError = null
                    )
                } else it
            })
        }
        maybeAutoAnswer(eventId)
    }

    private fun markLookupDone(eventId: String) {
        _state.update { state ->
            state.copy(pending = state.pending.map {
                if (it.request.eventId == eventId) {
                    it.copy(lookupPending = false, lookupError = "未读取到该调用的参数；风险未知")
                } else it
            })
        }
    }

    /** 参数/风险确定后再按记忆自动回答；未知审批类型永远不自动允许。 */
    private fun maybeAutoAnswer(eventId: String) {
        if (!_state.value.ready) return
        val item = _state.value.pending.firstOrNull { it.request.eventId == eventId } ?: return
        if (item.request.unknownTool || item.answering || item.automatic || item.lookupPending) return
        val remembered = memory.lookup(item.request.sessionId, item.request.toolName, item.risk.level) ?: return
        _state.update { state ->
            state.copy(pending = state.pending.map {
                if (it.request.eventId == eventId) it.copy(automatic = true) else it
            })
        }
        answer(eventId, remembered, remember = false)
    }

    private fun finishAnswer(
        eventId: String,
        token: Int,
        outcome: DshApprovalOutcome?,
        error: Throwable?
    ) {
        // 只有仍属于本次连接代次的回答才能修改状态；旧回答退出时不再误伤重连后的卡片。
        val owned = synchronized(answering) {
            if (answering[eventId] == token) {
                answering.remove(eventId)
                true
            } else {
                false
            }
        }
        if (!owned) return
        _state.update { state ->
            if (error == null && outcome != null) {
                state.copy(
                    pending = state.pending.filterNot { it.request.eventId == eventId },
                    remembered = memory.snapshot()
                )
            } else {
                state.copy(
                    pending = state.pending.map {
                        if (it.request.eventId == eventId) {
                            it.copy(
                                answering = false,
                                error = messageOf(error ?: IOException("审批回答失败")).message
                            )
                        } else it
                    }
                )
            }
        }
    }

    private fun messageOf(error: Throwable): DshApprovalFailure = DshApprovalFailures.describe(error)

    private companion object {
        const val LOOKUP_TIMEOUT_MS = 2_000L
    }
}
