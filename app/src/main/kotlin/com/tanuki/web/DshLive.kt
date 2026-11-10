package com.tanuki.web

import kotlinx.coroutines.flow.Flow

/** 原生页实时通道事件；来自官方 `$events` 与 `session/follow`，不另造聊天协议。 */
sealed interface DshLiveEvent {
    /** WebSocket 已连接并已打开两个官方逻辑流。 */
    data object Connected : DshLiveEvent
    data class Snapshot(
        val messages: List<DshMessage>,
        val hasMore: Boolean,
        val liveText: String
    ) : DshLiveEvent
    data class Message(val message: DshMessage) : DshLiveEvent
    data class Streaming(val text: String) : DshLiveEvent
    data class Finished(val committed: Boolean) : DshLiveEvent
    data class Running(val running: Boolean) : DshLiveEvent
    data class Approvals(val approvals: List<DshApproval>) : DshLiveEvent
    data class Failure(val message: String) : DshLiveEvent
}

/**
 * 原生页需要的官方会话能力。读与发送都走官方 Remote；实时流只走
 * `/api/remote.mux`，测试可替换为合成实现。
 */
interface DshChatSource : DshSessionSource {
    suspend fun send(sessionId: String, text: String): Result<Unit>
    suspend fun cancel(sessionId: String): Result<Unit>
    suspend fun answer(eventId: String, allow: Boolean): Result<Unit>
    fun live(sessionId: String): Flow<DshLiveEvent>
}
