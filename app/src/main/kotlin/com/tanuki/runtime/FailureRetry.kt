package com.tanuki.runtime

/** 用户确认过的原操作与目标版本；命令保留安装、强制重装、卸载或切换的语义。 */
data class FailureRetry(val command: String, val version: String? = null) {
    companion object {
        fun component(component: RuntimeComponent, force: Boolean, version: String): FailureRetry =
            FailureRetry("${component.name}_${if (force) "REINSTALL" else "INSTALL"}", version)
    }
}
