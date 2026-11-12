package com.tanuki.runtime

/** 停止未确认的异常跨服务/恢复入口传播时仍保留来源，不能被外层重分类成其他操作。 */
internal class RuntimeStopException(message: String, cause: Throwable, val expectedGeneration: Long? = null) : IllegalStateException(message, cause)

internal fun failureOperation(error: Throwable, requested: String?, active: String?): String? =
    if (error is RuntimeStopException) "runtime-stop" else requested ?: active

/** 当前失败的真实快照转换；过期停止异常只作诊断，不再改写后继运行。 */
internal fun runtimeFailureSnapshot(current: Snapshot, error: Throwable, operation: String?, generation: Long,
                                    message: String, occurredAtMillis: Long): Snapshot {
    if (error is RuntimeStopException && error.expectedGeneration != null && error.expectedGeneration != generation) return current
    val source = failureOperation(error, operation, current.operation)
    val failure = FailureCatalog.describe(FailureInput(message = message, operation = source,
        retry = current.operationRetry.takeIf { source == current.operation }, stage = current.stage,
        environment = current.environment, update = current.update, service = current.service,
        recovery = current.recovery, occurredAtMillis = occurredAtMillis))
    return current.copy(error = message, failure = failure, failureRevision = current.failureRevision + 1)
}

internal data class UserStopResult(val snapshot: Snapshot, val persistedRuntimeError: String? = null)

/** 捕获停止前的错误实例；停止期间产生的新失败即使文案相同也不属于这次收尾。 */
internal class UserStopState(private val before: Snapshot) {
    val serviceBefore: ServiceState get() = before.service
    private val runtimeFailure = before.failure?.takeIf {
        it.domain == FailureDomain.RUNTIME && it.operation !in DIAGNOSTICS &&
            (before.error.isBlank() || before.error == it.detail)
    }
    private val runtimeOperation = before.failedOperation?.takeIf {
        it.domain == FailureDomain.RUNTIME && it.operation !in DIAGNOSTICS
    }

    fun finish(
        current: Snapshot,
        confirmedStopped: Boolean,
        userRequested: Boolean,
        sameGeneration: Boolean,
        remoteActive: Boolean = current.remote.connected,
    ): UserStopResult {
        if (!confirmedStopped || !userRequested || !sameGeneration) return UserStopResult(current)
        val sameErrorEvent = current.failureRevision == before.failureRevision && current.failure === before.failure && current.error == before.error &&
            current.failedOperation === before.failedOperation && current.operation == before.operation
        val clearFailure = runtimeFailure != null && sameErrorEvent
        val clearError = sameErrorEvent && (runtimeFailure != null || (runtimeOperation != null && before.failure == null &&
            !runtimeOperation.cancelled && before.error.isNotBlank() && before.error == runtimeOperation.reason))
        val clearOperation = runtimeOperation != null && sameErrorEvent
        val next = current.copy(
            service = ServiceState.STOPPED,
            webHealthy = false,
            web = if (remoteActive) current.web else WebState.DISCONNECTED,
            error = if (clearError) "" else current.error,
            failure = if (clearFailure) null else current.failure,
            failedOperation = if (clearOperation) null else current.failedOperation,
            operationRetry = if (clearOperation && current.operation == null && current.operationRetry === before.operationRetry) null else current.operationRetry,
        )
        // 其他域的操作/失败以及新阶段继续拥有自己的文案，不被“已停止”覆盖。
        val canOwnStage = next.operation == null && !next.operationActive && next.error.isBlank() && next.failure == null &&
            next.failedOperation == null && next.environment !in setOf(EnvironmentState.FAILED, EnvironmentState.REPAIR, EnvironmentState.CANCELLED) &&
            next.update !in setOf(UpdateState.FAILED, UpdateState.CHECK_FAILED, UpdateState.RECOVERY_REQUIRED) &&
            next.health != HealthState.FAILED && current.stage == before.stage && current.operation == before.operation
        return UserStopResult(
            if (canOwnStage) next.copy(stage = "助手已停止") else next,
            if (clearError) before.error.ifBlank { runtimeFailure!!.detail } else null,
        )
    }

    private companion object {
        val DIAGNOSTICS = setOf("health", "background-probe", "service-http-check")
    }
}
