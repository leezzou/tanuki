package com.tanuki.web

import kotlinx.coroutines.flow.Flow

/** 原生页实时通道事件；来自官方 `$events` 与 `session/follow`，不另造聊天协议。 */
sealed interface DshLiveEvent {
    /** WebSocket 已连接并已打开两个官方逻辑流。 */
    data object Connected : DshLiveEvent
    data class Snapshot(
        val messages: List<DshMessage>,
        val hasMore: Boolean,
        val liveText: String,
        val tools: List<DshToolItem> = emptyList(),
        val coordination: DshCoordination = DshCoordination()
    ) : DshLiveEvent
    data class Timeline(val tools: List<DshToolItem>) : DshLiveEvent
    data class Message(val message: DshMessage) : DshLiveEvent
    data class Streaming(val text: String) : DshLiveEvent
    data class Finished(val committed: Boolean) : DshLiveEvent
    data class Running(val running: Boolean) : DshLiveEvent
    /** terminal只用于连接/逻辑流不可继续，普通Agent业务错误不使follow失效。 */
    data class Failure(val message: String, val terminal: Boolean = false) : DshLiveEvent
    /** 真实目标/待办事件增量；只含本次事件解析出的字段，空值不会清空已有状态。 */
    data class Coordination(val update: DshCoordination) : DshLiveEvent
}

/**
 * 原生页需要的官方会话能力。读与发送都走官方 Remote；实时流只走
 * `/api/remote.mux`，测试可替换为合成实现。
 */
interface DshChatSource : DshSessionSource {
    suspend fun send(sessionId: String, text: String): Result<Unit>
    suspend fun cancel(sessionId: String): Result<Unit>
    fun live(sessionId: String): Flow<DshLiveEvent>

    /**
     * 读取早于 [beforeSeq] 的一页历史，用于向上翻页。
     * `beforeSeq` 是当前窗口最早行的 seq 减一，实现方把它作为官方 `throughSeq` 上界。
     */
    suspend fun messagesBefore(
        sessionId: String,
        beforeSeq: Int,
        maxMessages: Int
    ): Result<DshSessionPage>
}
