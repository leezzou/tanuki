package com.tanuki.ui

import com.tanuki.runtime.RemoteHostConfig
import com.tanuki.runtime.RemoteHostStatus
import com.tanuki.runtime.RemotePhase
import com.tanuki.web.DshAdminSession
import com.tanuki.web.DshModelCatalog
import com.tanuki.web.DshModelSelection

/** 主机连接过程/失败的可见反馈；error 为 true 时使用错误样式。 */
data class DshHostFeedback(val message: String, val error: Boolean)

object DshSessionHostFeedback {
    fun forState(busy: Boolean, message: String, error: String): DshHostFeedback? = when {
        error.isNotBlank() -> DshHostFeedback("远程主机连接失败：$error", true)
        message.isNotBlank() -> DshHostFeedback("远程主机：$message", false)
        busy -> DshHostFeedback("正在连接远程主机…", false)
        else -> null
    }
}

/** 会话管理页可切换的数据源主机；只描述展示状态，不持有连接或凭据。 */
data class DshSessionHost(
    val id: String,
    val label: String,
    val detail: String,
    val active: Boolean,
    val available: Boolean,
    val busy: Boolean = false
)

/**
 * 把本机运行状态与已保存远程主机状态整理成主机选择列表。
 *
 * 当前运行时只保存一个远程配置，所以列表固定为“本机 + 已保存远程主机”；
 * 没有配置时远程项明确不可选，不生成第二个假主机。
 */
object DshSessionHosts {
    const val LOCAL_ID = "local"
    const val REMOTE_ID = "remote"

    fun list(
        localRunning: Boolean,
        saved: RemoteHostConfig?,
        status: RemoteHostStatus,
        busy: Boolean,
        pendingHostKey: Boolean = false
    ): List<DshSessionHost> = listOf(
        local(localRunning, status.connected),
        remote(saved, status, busy, pendingHostKey)
    )

    fun local(running: Boolean, remoteConnected: Boolean): DshSessionHost = DshSessionHost(
        id = LOCAL_ID,
        label = "本机",
        detail = if (running) "运行中" else "未运行",
        active = !remoteConnected,
        available = running
    )

    fun remote(
        saved: RemoteHostConfig?,
        status: RemoteHostStatus,
        busy: Boolean,
        pendingHostKey: Boolean = false
    ): DshSessionHost {
        val label = saved?.displayName()?.takeIf { it.isNotBlank() } ?: "远程主机"
        val detail = when {
            saved == null -> "未保存配置"
            pendingHostKey -> "等待主机密钥确认"
            status.phase == RemotePhase.CONNECTING || busy -> "连接中"
            status.phase == RemotePhase.REMOTE -> "已连接"
            status.phase == RemotePhase.FALLBACK -> "连接失败，已回退本地"
            else -> "未连接"
        }
        return DshSessionHost(
            id = REMOTE_ID,
            label = label,
            detail = detail,
            active = status.connected,
            available = saved != null && !busy && status.phase != RemotePhase.CONNECTING && !pendingHostKey,
            busy = busy || status.phase == RemotePhase.CONNECTING || pendingHostKey
        )
    }
}

/** 一个会话文件夹分组；key 是官方 cwd，空表示官方没有返回工作目录。 */
data class DshSessionGroup(
    val key: String,
    val title: String,
    val detail: String,
    val sessions: List<DshAdminSession>
)

/**
 * 按官方 `cwd` 把会话分组。
 *
 * 只做展示分组，不把工作目录写回官方；同一目录内的会话保持调用方传入的顺序
 * （通常已按置顶与更新时间排序）。空目录统一归入“未分组”。
 */
object DshSessionGrouping {
    fun groups(sessions: List<DshAdminSession>): List<DshSessionGroup> {
        if (sessions.isEmpty()) return emptyList()
        val grouped = LinkedHashMap<String, MutableList<DshAdminSession>>()
        sessions.forEach { session ->
            grouped.getOrPut(folderKey(session.cwd)) { mutableListOf() } += session
        }
        return grouped.map { (key, items) ->
            DshSessionGroup(
                key = key,
                title = folderTitle(key),
                detail = key.ifBlank { "官方未返回工作目录" },
                sessions = items
            )
        }.sortedWith(
            compareBy<DshSessionGroup> { it.key.isBlank() }
                .thenBy { it.title.lowercase() }
        )
    }

    fun folderKey(cwd: String?): String {
        val clean = cwd?.trim().orEmpty()
        if (clean.isEmpty()) return ""
        return clean.trimEnd('/').ifBlank { "/" }
    }

    fun folderTitle(cwd: String?): String {
        val key = folderKey(cwd)
        if (key.isBlank()) return "未分组"
        return key.substringAfterLast('/').ifBlank { key }
    }
}


/**
 * 会话管理页的纯展示文案与能力判断。
 *
 * 这里只放可被 JVM 单测覆盖的纯函数：权限预设的人话说明、官方硬删除能力、
 * 模型推理强度名称。网络结果与官方字段仍以 `DshSessionAdminApi` 为准。
 */
object DshPermissionPresentation {
    /** 把官方预设翻译成用户能直接判断后果的中文说明。 */
    fun guidance(value: String, officialName: String = ""): String = when (value) {
        "read-only" -> "只读：只能查看会话、文件与目录；写入和执行请求会被拒绝。"
        "workspace-write" -> "工作区写入：可修改当前工作区内的文件并执行命令；工作区外默认不放开。"
        "danger-full-access" -> "完全权限：取消工作区边界限制，工具可访问应用有权限读写的更多位置；风险最高。"
        "auto" -> "自动：由官方预设决定审批与确认策略；本页不额外放大权限，具体以 DSH 说明为准。"
        "custom" -> "自定义：由官方配置组合而成，本页只展示，不能直接切换。"
        else -> officialName.takeIf { it.isNotBlank() }
            ?.let { "官方预设「$it」；具体行为以 DSH 返回的说明为准。" }
            ?: "官方预设；具体行为以 DSH 返回的说明为准。"
    }

    /** 完全权限的二次确认风险点；不在其他预设下展示。 */
    fun fullAccessRisks(): List<String> = listOf(
        "工具可以读写工作区之外、应用有权访问的文件。",
        "误操作、提示注入或恶意脚本造成的影响不再被工作区边界限制。",
        "Tanuki 不会自动切换该模式；切回其他预设仍通过官方命令提交。"
    )

    /** `custom` 由官方组合配置，本页只读展示，不能伪造切换动作。 */
    fun canSelect(value: String): Boolean = value != "custom"
}

/** 当前官方接口能力；删除没有端点时只说明，不生成可点击的假按钮。 */
object DshSessionCapabilities {
    const val DELETE_SUPPORTED: Boolean = false
    const val DELETE_UNAVAILABLE_REASON: String =
        "官方 DSH 当前没有会话硬删除接口。归档只会把会话从原生列表移除，不会删除项目文件，也不能在本页找回。"

    /** 归档是可见的官方写操作；硬删除不是。 */
    fun archiveSupported(): Boolean = true
}

/** 模型选择的展示辅助；推理强度只在模型目录声明时展示。 */
object DshModelPresentation {
    fun effortName(catalog: DshModelCatalog?, selection: DshModelSelection?): String? {
        val effort = selection?.reasoningEffort?.takeIf { it.isNotBlank() } ?: return null
        return catalog?.groups
            ?.firstOrNull { it.id == selection.provider }
            ?.models
            ?.firstOrNull { it.id == selection.model }
            ?.efforts
            ?.firstOrNull { it.id == effort }
            ?.name
            ?: effort
    }

    /** 形如“DeepSeek / chat · 高”，没有推理强度时只显示模型。 */
    fun selectionLabel(catalog: DshModelCatalog?, selection: DshModelSelection?): String {
        if (selection == null) return "未选择"
        val effort = effortName(catalog, selection)
        return if (effort == null) selection.label else "${selection.label} · $effort"
    }
}
