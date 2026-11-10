package com.tanuki.ui

/**
 * 列表稳定 key 规则。
 *
 * 原生聊天时间线、会话条和工具卡统一使用“类型 + 业务 id”的 key：
 * - 消息按官方 seq，不使用列表下标；
 * - 待发送气泡与已落库消息使用不同前缀，避免同 seq 冲突；
 * - 工具按 callId，迟到历史页与实时事件合并时保持同一项身份。
 */
object TanukiListKeys {
    private const val MAX_ID_LENGTH = 512

    fun session(sessionId: String): String = "session:${requireId(sessionId)}"

    fun message(seq: Long): String = "message:${requireSeq(seq)}"

    fun pendingMessage(seq: Long): String = "pending:${requireSeq(seq)}"

    fun tool(callId: String): String = "tool:${requireId(callId)}"

    /** 页面组装列表后做一次唯一性断言；重复 key 会让 Compose 丢项或错位。 */
    fun requireUnique(keys: List<String>): List<String> {
        require(keys.all { it.isNotBlank() }) { "列表 key 不能为空白" }
        require(keys.size == keys.toSet().size) { "列表 key 必须唯一" }
        return keys
    }

    private fun requireId(id: String): String {
        val clean = id.trim()
        require(clean.isNotEmpty()) { "列表 id 不能为空" }
        require(clean.length <= MAX_ID_LENGTH) { "列表 id 超出上限" }
        return clean
    }

    private fun requireSeq(seq: Long): Long {
        require(seq >= 0L) { "事件序号不能为负" }
        return seq
    }
}
