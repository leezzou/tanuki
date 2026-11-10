package com.tanuki.web

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/** 原生界面 transport 的稳定连接状态；供输入、审批、会话管理组件共用。 */
enum class DshConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

/**
 * 原生界面组件消费的官方 DSH transport 最小契约。
 *
 * - [call] 返回官方 `result.value`；业务失败抛 [DshApiException]，认证/网络失败抛
 *   `IOException`，调用方不需要拼 URL、Cookie 或 token。
 * - [live] 打开官方 `$events` 与 `session/follow`；取消收集会立即关闭底层 socket。
 * - [reset] 丢弃当前 Cookie 与实时连接，下一次调用重新认证。
 *
 * 该签名冻结后只做加法；输入/审批/会话管理组件不得各自再建 HTTP/WS 客户端。
 */
interface DshTransport {
    val connection: StateFlow<DshConnectionState>
    suspend fun call(method: String, args: JSONObject): Any?
    fun live(sessionId: String): Flow<DshLiveEvent>
    fun reset()
}

/**
 * 将官方 transport 适配为输入栏使用的 [DshComposerRemote]。
 *
 * 只转发 method/args、返回 result.value 与 reset；认证、Cookie、超时和错误文本
 * 都由 transport 负责，不在这里复制第四套 HTTP 客户端。
 */
class DshNativeComposerRemote(private val transport: DshTransport) : DshComposerRemote {
    override suspend fun call(method: String, args: JSONObject): Any? = transport.call(method, args)
    override fun reset() = transport.reset()
}
