package com.tanuki.runtime

import java.security.MessageDigest

/**
 * 配对链接的 6 位人工核对码。
 *
 * 电脑端在显示/打印配对链接时同时显示这 6 位码；手机端解析链接后会自动算出同一个值，
 * 用户只需和电脑屏幕对照，不需要手抄或手输。它用来确认“链接 + 码”来自同一次配对，
 * 避免复制了旧链接或相邻房间的链接。
 *
 * 它不是新的传输秘密，也不能替代链接中的一次性 token；单独输入 6 位码
 * 无法确定中继地址、房间和 token，因此不提供“只输码”的降级。
 * 算法固定为 SHA-256(规范配对链接) 前 8 字节的大端无符号整数对 1,000,000 取模，
 * 与主机端显示逻辑保持同一实现。
 */
object RemotePairingCode {
    const val LENGTH = 6
    private const val MODULUS = 1_000_000L

    fun of(pairing: RemoteRelayPairing): String = ofLink(pairing.toUri())

    fun ofLink(uri: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.trim().toByteArray(Charsets.UTF_8))
        // 按字节逐步取模，避免 8 字节无符号值在 Long 中溢出成负数。
        var value = 0L
        for (index in 0 until 8) {
            value = ((value % MODULUS) * 256L + (digest[index].toLong() and 0xffL)) % MODULUS
        }
        return value.toString().padStart(LENGTH, '0')
    }

    fun validate(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return "请输入电脑上显示的 6 位配对码"
        if (text.length != LENGTH || !text.all { it in '0'..'9' }) return "配对码必须是 6 位数字"
        return null
    }

    fun matches(pairing: RemoteRelayPairing, input: String): Boolean = of(pairing) == input.trim()
}
