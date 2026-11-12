package com.tanuki.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicBoolean

/** 先占环境锁再调度恢复；完成信号只在释放锁后发布，所有入口等待同一结果。 */
internal class RuntimeInitialization(
    scope: CoroutineScope,
    mutation: Mutex,
    background: Boolean,
    initialize: () -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    private val completion = CompletableDeferred<Unit>()
    val isComplete: Boolean get() = completion.isCompleted
    val succeeded: Boolean get() = completion.isCompleted && !completion.isCancelled

    init {
        if (!background) {
            initialize()
            completion.complete(Unit)
        } else {
            check(mutation.tryLock()) { "运行环境恢复无法获得独占锁" }
            val released = AtomicBoolean(false)
            fun finish(error: Throwable?) {
                if (!released.compareAndSet(false, true)) return
                mutation.unlock()
                if (error == null) completion.complete(Unit) else completion.completeExceptionally(error)
            }
            scope.launch {
                try {
                    initialize()
                    finish(null)
                } catch (error: Throwable) {
                    try { onFailure(error) } finally { finish(error) }
                }
            }.invokeOnCompletion { error -> if (error != null) finish(error) }
        }
    }

    suspend fun await() = completion.await()
}
