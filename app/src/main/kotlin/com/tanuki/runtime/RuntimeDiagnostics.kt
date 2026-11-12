package com.tanuki.runtime

import kotlinx.coroutines.CancellationException

/** 公网检查与本地环境健康独立；未执行不等于通过。 */
enum class NetworkDiagnosticState { UNCHECKED, CHECKING, REACHABLE, FAILED }

data class NetworkDiagnostic(
    val state: NetworkDiagnosticState = NetworkDiagnosticState.UNCHECKED,
    val detail: String = "",
)

/** 本地失败立即返回；公网异常只记入独立结果，用户取消仍向上传播。 */
internal suspend fun runRuntimeDiagnostics(
    local: suspend () -> CommandResult,
    network: (suspend () -> CommandResult)? = null,
    onNetwork: (NetworkDiagnostic) -> Unit = {},
): CommandResult {
    onNetwork(NetworkDiagnostic())
    val result = local()
    check(result.exitCode == 0) { "健康检查失败 exit=${result.exitCode}: ${Redact.text(result.stderr)}" }
    if (network == null) return result
    onNetwork(NetworkDiagnostic(NetworkDiagnosticState.CHECKING))
    val diagnostic = try {
        val response = network()
        if (response.exitCode == 0) NetworkDiagnostic(NetworkDiagnosticState.REACHABLE, "官方 npm HTTPS 连接通过")
        else NetworkDiagnostic(NetworkDiagnosticState.FAILED,
            "公网检查失败 exit=${response.exitCode}：${Redact.text(response.stderr).takeLast(1200)}")
    } catch (cancelled: CancellationException) {
        onNetwork(NetworkDiagnostic())
        throw cancelled
    } catch (error: Exception) {
        NetworkDiagnostic(NetworkDiagnosticState.FAILED,
            "公网检查未完成：${Redact.text(error.message ?: error.javaClass.simpleName).takeLast(1200)}")
    }
    onNetwork(diagnostic)
    return result
}
