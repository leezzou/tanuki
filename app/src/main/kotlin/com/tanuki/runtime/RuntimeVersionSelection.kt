package com.tanuki.runtime

/** 版本选择和安装重试共用这一入口；保存新目标前先使旧安装重试失效。 */
internal class RuntimeVersionSelection(
    private val selected: (RuntimeComponent) -> RuntimeArtifact,
    private val persist: (RuntimeComponent, String) -> Unit,
    private val invalidateRetry: () -> Unit,
) {
    fun select(component: RuntimeComponent, version: String) {
        require(RuntimeCatalog.find(component, version) != null) { "未知组件版本" }
        val changed = selected(component).version != version
        if (changed) invalidateRetry()
        persist(component, version)
    }
}
