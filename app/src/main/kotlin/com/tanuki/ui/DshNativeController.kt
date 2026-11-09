package com.tanuki.ui

import com.tanuki.runtime.Redact
import com.tanuki.web.DshApiException
import com.tanuki.web.DshMessage
import com.tanuki.web.DshSessionSource
import com.tanuki.web.DshSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException

/** 原生只读页的展示状态；会话和消息都直接来自官方 DSH 接口。 */
data class DshNativeState(
    val loading: Boolean = false,
    val sessions: List<DshSession> = emptyList(),
    val selectedId: String? = null,
    val messages: List<DshMessage> = emptyList(),
    val hasMore: Boolean = false,
    val error: String? = null,
    /** 会话列表都读不到时置位；界面据此回退官方页面。 */
    val unavailableReason: String? = null
)

/**
 * 原生只读页控制器。一次只保留一个活动请求；服务停止、切换界面或失败回退时
 * 会清空内存中的 API Cookie，但不会停止 DSH 服务或改写任何会话。
 */
class DshNativeController(
    private val client: DshSessionSource,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(DshNativeState())
    val state: StateFlow<DshNativeState> = _state.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    /** 通知点击目标；会话列表尚未加载时先保存，加载完成后选中。 */
    private var pendingSelection: String? = null

    fun refresh() {
        val token = ++generation
        val target = pendingSelection
        job?.cancel()
        _state.value = DshNativeState(loading = true)
        job = scope.launch {
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
                    _state.value = DshNativeState(sessions = visible, selectedId = selected.id, loading = true)
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
            // 通知点击必须重新读取历史；原生页没有实时跟随，不能复用旧页缓存。
            select(sessionId, force = true)
        } else {
            refresh()
        }
    }

    fun select(sessionId: String, force: Boolean = false) {
        if (sessionId.isBlank()) return
        val current = _state.value
        if (!force && current.selectedId == sessionId && current.error == null && current.messages.isNotEmpty()) return
        pendingSelection = null
        val token = generation
        job?.cancel()
        _state.update { it.copy(selectedId = sessionId, messages = emptyList(), error = null, hasMore = false) }
        job = scope.launch { loadMessages(sessionId, token) }
    }

    fun clear() {
        generation++
        job?.cancel()
        job = null
        pendingSelection = null
        _state.value = DshNativeState()
    }

    /** 服务停止或页面销毁时再丢弃内存 Cookie；仅切换界面不重复换取 API Peer。 */
    fun close() {
        clear()
        client.reset()
    }

    private suspend fun loadMessages(sessionId: String, token: Int) {
        _state.update { it.copy(loading = true, error = null, messages = emptyList(), hasMore = false) }
        val result = client.messages(sessionId)
        if (token != generation) return
        result.onSuccess { page ->
            _state.update { it.copy(loading = false, messages = page.messages, hasMore = page.hasMore) }
        }.onFailure { error ->
            // 历史页读取失败只影响当前会话，不把整个原生列表判死，也不伪造消息。
            _state.update { it.copy(loading = false, error = messageOf(error)) }
        }
    }

    private fun messageOf(error: Throwable): String = when (error) {
        is DshApiException -> "官方接口返回 ${error.code}"
        is JSONException, is IllegalArgumentException -> "官方接口响应格式不正确"
        else -> Redact.text(error.message ?: "未知错误").take(200)
    }
}
