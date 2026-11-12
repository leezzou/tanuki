package com.tanuki.runtime

internal const val START_REQUEST_GENERATION = "runtime-start-generation"

/** 请求身份必须在真正接纳启动的同一锁内校验，不能在恢复等待结束后先检查再调用。 */
internal inline fun withStartAdmission(
    owner: Any,
    expectedGeneration: Long,
    generation: () -> Long,
    stopping: () -> Boolean,
    accepted: () -> Unit,
) {
    synchronized(owner) {
        if (generation() != expectedGeneration || stopping()) return
        accepted()
    }
}
