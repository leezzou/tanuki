package com.tanuki.ui

import com.tanuki.runtime.Redact
import com.tanuki.web.DshApiException
import com.tanuki.web.DshApprovalMemory
import com.tanuki.web.DshApprovalOutcome
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
import java.io.IOException

/** 原生审批卡片的一项；参数与风险由当前会话的工具时间线补齐。 */
data class DshPendingApproval(
    val request: DshApprovalRequest,
    val arguments: String? = null,
    val risk: DshRiskAssessment = DshRiskAssessment(DshRiskLevel.UNKNOWN, "无法判断影响范围"),
    val answering: Boolean = false,
    val error: String? = null,
    /** 由“记住本次运行”自动回答，而不是用户刚点击。 */
    val automatic: Boolean = false
)

data class DshApprovalState(
    val connecting: Boolean = false,
    val connected: Boolean = false,
    val ready: Boolean = false,
    val pending: List<DshPendingApproval> = emptyList(),
    val error: String? = null,
    val remembered: List<DshRememberedChoice> = emptyList()
) {
    fun pendingFor(sessionId: String?): List<DshPendingApproval> =
        if (sessionId == null) emptyList() else pending.filter { it.request.sessionId == sessionId }

    fun otherPendingCount(sessionId: String?): Int =
        pending.count { it.request.sessionId != sessionId }
}

/**
 * 原生审批控制器。
 *
 * - 连接与回答都在 IO 线程；主线程只读 StateFlow。
 * - 每个请求都对应官方 eventId；允许/拒绝只通过官方 `$events/result` 回传。
 * - “记住本次运行”只在内存中按会话 + 工具 + 风险等级复用，关闭原生页/服务停止即清空；
 *   不写 DSH 配置，不冒充官方持久授权。
 * - 任何连接/回答失败都保留待审批项并显示中文错误，不静默吞掉。
 */
class DshApprovalController(
    private val endpointProvider: () -> DshEndpoint?,
    private val scope: CoroutineScope,
    private val toolLookup: (String) -> DshToolItem? = { null },
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
    private val answering = HashSet<String>()

    /** 打开审批通道；重复调用不会建立第二条连接。 */
    fun start() {
        val endpointOrigin = endpointProvider()?.origin
        // 本地 DSH 重启、远程隧道换端口或安全模式切换都会改变 origin；旧连接不能继续回答。
        if (session != null && endpointOrigin != null && connectedOrigin != null && endpointOrigin != connectedOrigin) {
            session?.close()
            session = null
            connectedOrigin = null
            synchronized(answering) { answering.clear() }
            _state.value = DshApprovalState(remembered = memory.snapshot())
        }
        val current = _state.value
        if (session != null && (current.connecting || current.connected)) return
        session?.close()
        session = null
        val token = ++generation
        connectJob?.cancel()
        _state.update { it.copy(connecting = true, error = null) }
        val created = sessionFactory(object : DshApprovalSession.Listener {
            override fun onReady() {
                if (token != generation) return
                _state.update { it.copy(connecting = false, connected = true, ready = true, error = null) }
            }

            override fun onApproval(request: DshApprovalRequest) {
                if (token != generation) return
                onApprovalRequest(request)
            }

            override fun onCancel(eventId: String) {
                if (token != generation) return
                _state.update { state -> state.copy(pending = state.pending.filterNot { it.request.eventId == eventId }) }
                synchronized(answering) { answering.remove(eventId) }
            }

            override fun onDisconnected(message: String) {
                if (token != generation) return
                val current = session
                session = null
                connectedOrigin = null
                current?.close()
                _state.update {
                    it.copy(
                        connecting = false,
                        connected = false,
                        ready = false,
                        error = message,
                        pending = it.pending.map { item ->
                            if (item.answering) item.copy(answering = false, error = "连接已断开，未能回答") else item
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
                _state.update { it.copy(connecting = false, connected = true, error = null) }
            }.onFailure { error ->
                created.close()
                if (session === created) session = null
                _state.update {
                    it.copy(connecting = false, connected = false, ready = false, error = messageOf(error))
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
        _state.update { it.copy(connecting = false, connected = false, ready = false, error = null) }
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
        synchronized(answering) {
            if (!answering.add(eventId)) return
        }
        _state.update { state ->
            state.copy(pending = state.pending.map {
                if (it.request.eventId == eventId) it.copy(answering = true, error = null) else it
            })
        }
        val current = session
        if (current == null) {
            finishAnswer(eventId, null, IOException("审批通道已关闭"))
            return
        }
        scope.launch(ioDispatcher) {
            val result = current.answer(eventId, outcome)
            if (session !== current) return@launch
            if (remember && result.isSuccess) {
                memory.remember(item.request.sessionId, item.request.toolName, item.risk.level, outcome)
            }
            finishAnswer(eventId, outcome.takeIf { result.isSuccess }, result.exceptionOrNull())
        }
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
        if (clearMemory) memory.clear()
        _state.value = DshApprovalState(remembered = if (clearMemory) emptyList() else memory.snapshot())
    }

    private fun onApprovalRequest(request: DshApprovalRequest) {
        if (_state.value.pending.any { it.request.eventId == request.eventId }) return
        val tool = request.callId?.let(toolLookup)
        val arguments = tool?.arguments
        val risk = DshApprovalRisk.assess(request.toolName, arguments)
        val remembered = memory.lookup(request.sessionId, request.toolName, risk.level)
        val item = DshPendingApproval(
            request = request,
            arguments = arguments,
            risk = risk,
            automatic = remembered != null
        )
        _state.update { it.copy(pending = it.pending + item) }
        if (remembered != null) {
            // 自动回答也走同一条官方回答路径；失败会保留卡片并显示错误。
            answer(request.eventId, remembered, remember = false)
        }
    }

    private fun finishAnswer(eventId: String, outcome: DshApprovalOutcome?, error: Throwable?) {
        synchronized(answering) { answering.remove(eventId) }
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
                            it.copy(answering = false, error = messageOf(error ?: IOException("审批回答失败")))
                        } else it
                    }
                )
            }
        }
    }

    private fun messageOf(error: Throwable): String = when (error) {
        is DshApiException -> "官方接口返回 ${error.code}"
        is IOException -> Redact.text(error.message ?: "审批通道失败").take(200)
        else -> Redact.text(error.message ?: "审批通道失败").take(200)
    }
}
