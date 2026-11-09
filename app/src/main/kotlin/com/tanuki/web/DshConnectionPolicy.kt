package com.tanuki.web

/**
 * 管理页认证状态只绑定当前活动端点，不绑定本地 DSH 进程状态。
 * 远程模式没有本地 ServiceState.RUNNING，仍必须能把 HTTP 200/401 转成连接状态并做一次 token 重试。
 */
object DshConnectionPolicy {
    fun canVerifyCookie(activeOrigin: String?, expectedOrigin: String): Boolean =
        activeOrigin != null && activeOrigin == expectedOrigin

    fun canRetryAuthentication(
        activeOrigin: String?,
        authenticatedUrl: String?,
        origin: String,
        url: String
    ): Boolean = activeOrigin == origin && authenticatedUrl == url
}
