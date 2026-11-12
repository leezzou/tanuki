package com.tanuki.runtime

import org.json.JSONObject

/** 引导点击时重新决定动作，不以页面曾经显示 READY 作为安装或导航依据。 */
enum class LocalPreparationAction { BUSY, INITIALIZE, TOOLS, START, HEALTH, OPEN_CHAT, RECOVERY }

fun localPreparationAction(snapshot: Snapshot, components: ComponentInventory, tools: RequiredToolsStatus): LocalPreparationAction {
    if (snapshot.operationActive) return LocalPreparationAction.BUSY
    if (snapshot.update == UpdateState.RECOVERY_REQUIRED) return LocalPreparationAction.RECOVERY
    val installed = components.ubuntuInstalled && components.nodeInstalled && components.dshInstalled &&
        snapshot.environment == EnvironmentState.READY
    if (!installed) return LocalPreparationAction.INITIALIZE
    if (!tools.passed || !tools.ubuntuMarker.startsWith("${components.ubuntuVersion} ")) return LocalPreparationAction.TOOLS
    if (snapshot.service == ServiceState.RUNNING) {
        return if (snapshot.webHealthy) LocalPreparationAction.OPEN_CHAT else LocalPreparationAction.HEALTH
    }
    return LocalPreparationAction.START
}

/** 沿用完整元数据选定的程序；其他版本缺少程序时不能用默认版接管现有会话。 */
internal fun initializationDshVersion(metadata: JSONObject?, installedVersions: Set<String>, defaultVersion: String): String {
    if (metadata == null) return defaultVersion
    check(metadata.optBoolean("ready") && listOf("dsh", "node", "ubuntu").all(metadata::has)) {
        "安装元数据损坏，已保留所有数据；请恢复匹配快照，不能自动选择旧程序读取现有会话"
    }
    val selected = metadata.getString("dsh")
    check(selected.matches(DshVersions.VERSION)) { "安装元数据中的 DSH 版本无效，已保留所有数据" }
    check(selected == defaultVersion || selected in installedVersions) {
        "选定的 DSH $selected 程序缺失；请在组件设置中刷新官方版本列表并修复该版本，现有会话已保留，不会改装默认版本"
    }
    return selected
}
