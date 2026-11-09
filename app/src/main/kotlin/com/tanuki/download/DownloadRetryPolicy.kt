package com.tanuki.download

/** 单个归档请求的重试与状态分类；下载源切换由调用方按分类决定。 */
internal object DownloadRetryPolicy {
    const val MAX_ATTEMPTS = 3

    /** 第 [retry] 次重试前的指数退避：500ms、1000ms。 */
    fun delayMillis(retry: Int): Long {
        require(retry in 1 until MAX_ATTEMPTS) { "重试序号超出范围" }
        return 500L shl (retry - 1)
    }

    fun retryable(status: Int): Boolean =
        status == 408 || status == 425 || status == 429 || status in 500..599

    fun missing(status: Int): Boolean = status == 404 || status == 410

    fun permanent(status: Int): Boolean = status == 400 || status == 401 || status == 403
}
