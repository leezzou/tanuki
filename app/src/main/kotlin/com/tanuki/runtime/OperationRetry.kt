package com.tanuki.runtime

/** 失败详情是一次操作的快照，计时在结束时冻结；不持久化可能引用安装参数的回调。 */
data class FailedOperation(
    val retryId: Long,
    val stage: String,
    val reason: String,
    val elapsedMillis: Long,
    val cancelled: Boolean = false,
    val retryAvailable: Boolean = true,
)

/** 组件安装必须重新走入口恢复失败任务的版本；其他操作继续重放原任务。 */
internal fun operationRetryAction(
    request: FailureRetry?,
    installComponent: (RuntimeComponent, Boolean, String?) -> Unit,
    replay: () -> Unit,
): () -> Unit {
    if (request == null) return replay
    val component = when (request.command) {
        "UBUNTU_INSTALL", "UBUNTU_REINSTALL" -> RuntimeComponent.UBUNTU
        "NODE_INSTALL", "NODE_REINSTALL" -> RuntimeComponent.NODE
        else -> return replay
    }
    val force = request.command == "${component.name}_REINSTALL"
    val version = request.version
    // 安装入口先恢复选择并校验清单、空闲状态，不能只重放会读取新选择的安装主体。
    return { installComponent(component, force, version) }
}

/**
 * 重试仅重放用户刚刚选择的同一操作。编号绑定失败结果，开始新操作或点击一次后，
 * 旧按钮立即失效；回调在锁外运行，避免重试再次登记时交叉加锁。
 */
internal class OperationRetry(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var startedAt = 0L
    private var sequence = 0L
    private var current: Long = 0
    private var failed = false
    private var action: (() -> Unit)? = null

    @Synchronized fun begin(retry: () -> Unit): Long {
        current = ++sequence
        startedAt = clock()
        failed = false
        action = retry
        return current
    }

    @Synchronized fun elapsedMillis(id: Long): Long =
        if (id == current) (clock() - startedAt).coerceAtLeast(0) else 0

    @Synchronized fun fail(id: Long): Boolean {
        if (id != current || action == null) return false
        failed = true
        return true
    }

    @Synchronized fun complete(id: Long) {
        if (id == current) {
            failed = false
            action = null
        }
    }

    @Synchronized fun invalidate() {
        current = ++sequence
        failed = false
        action = null
    }

    fun retry(id: Long): Boolean {
        val retry = synchronized(this) {
            if (id != current || !failed) return false
            val pending = action ?: return false
            action = null
            failed = false
            pending
        }
        retry()
        return true
    }
}
