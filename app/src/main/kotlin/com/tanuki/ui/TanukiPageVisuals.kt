package com.tanuki.ui

import com.tanuki.ui.design.TanukiSpace
import com.tanuki.ui.design.TanukiStatusTone
import java.util.Locale

/**
 * 文件页空态的纯文案。
 *
 * 标题、说明和动作都由“是否在搜索、是否只有隐藏文件、当前是否可写”推导，
 * 因此可以在宿主机 JVM 里锁定文案，不依赖 Compose 或设备。
 */
internal data class TanukiFileEmptyCopy(
    val title: String,
    val detail: String,
    val actionLabel: String,
)

/** 文件空态能触发的真实动作；界面只负责把命令接到对应回调。 */
internal enum class FileEmptyCommand { IMPORT, CREATE_FOLDER, CLEAR_QUERY, SHOW_HIDDEN, RELOAD }

/** 空态主/次按钮的文案与命令；没有次级按钮时为 null。 */
internal data class FileEmptyActions(
    val primaryLabel: String,
    val primary: FileEmptyCommand,
    val secondaryLabel: String? = null,
    val secondary: FileEmptyCommand? = null,
)

/** 把空态命令分派给页面回调；单独抽出来让 JVM 回归可以验证按钮接线。 */
internal fun dispatchFileEmptyCommand(
    command: FileEmptyCommand,
    onImport: () -> Unit,
    onCreateFolder: () -> Unit,
    onClearQuery: () -> Unit,
    onShowHidden: () -> Unit,
    onReload: () -> Unit,
) {
    when (command) {
        FileEmptyCommand.IMPORT -> onImport()
        FileEmptyCommand.CREATE_FOLDER -> onCreateFolder()
        FileEmptyCommand.CLEAR_QUERY -> onClearQuery()
        FileEmptyCommand.SHOW_HIDDEN -> onShowHidden()
        FileEmptyCommand.RELOAD -> onReload()
    }
}

/**
 * 文件页的视觉映射。
 *
 * 只做“状态 → 语义/文案/尺寸”的纯计算；真正的颜色和字号仍由令牌提供，
 * 页面不在这里保存具体色值。
 */
internal object TanukiFileVisuals {
    /** 列表行最小高度；与令牌里的推荐行高一致，避免散落常量。 */
    const val ROW_MIN_HEIGHT_DP: Int = TanukiSpace.LIST_ROW_MIN_DP

    /** 传输胶囊最多同时显示一个百分比标签；总大小未知时只说明“传输中”。 */
    fun transferLabel(percent: Int?): String = percent?.let { "$it%" } ?: "传输中"

    /**
     * 空态文案。
     *
     * 三种空态共用同一套三态组件，因此标题必须能独立成立：搜索无结果时
     * 不说“暂无文件”，只有隐藏文件时直接给出显示隐藏文件的动作。
     */
    fun emptyCopy(query: String, hasEntries: Boolean, writable: Boolean): TanukiFileEmptyCopy = when {
        query.isNotEmpty() -> TanukiFileEmptyCopy(
            title = "暂无匹配文件",
            detail = "试试更短的文件名，或清除搜索。",
            actionLabel = "清除搜索",
        )
        hasEntries -> TanukiFileEmptyCopy(
            title = "暂无可见文件",
            detail = "当前只有隐藏文件；在“排序与显示”中显示隐藏文件。",
            actionLabel = "显示隐藏文件",
        )
        writable -> TanukiFileEmptyCopy(
            title = "暂无文件",
            detail = "从手机导入文件，或新建文件夹开始整理。",
            actionLabel = "从手机导入",
        )
        else -> TanukiFileEmptyCopy(
            title = "暂无文件",
            detail = "此位置暂无内容，可以刷新后重试。",
            actionLabel = "刷新",
        )
    }

    /**
     * 空态按钮。
     *
     * 只有“可写目录且确实为空”同时给出导入与新建文件夹；搜索、隐藏文件、
     * 只读位置仍只保留一个真实可执行的动作，不显示无法完成的假按钮。
     */
    fun emptyActions(query: String, hasEntries: Boolean, writable: Boolean): FileEmptyActions = when {
        query.isNotEmpty() -> FileEmptyActions("清除搜索", FileEmptyCommand.CLEAR_QUERY)
        hasEntries -> FileEmptyActions("显示隐藏文件", FileEmptyCommand.SHOW_HIDDEN)
        writable -> FileEmptyActions(
            primaryLabel = "从手机导入",
            primary = FileEmptyCommand.IMPORT,
            secondaryLabel = "新建文件夹",
            secondary = FileEmptyCommand.CREATE_FOLDER,
        )
        else -> FileEmptyActions("刷新", FileEmptyCommand.RELOAD)
    }

    /**
     * 权限 chip 的语气。
     *
     * 可写用成功色；尚未授权手机存储用警告色提示需要操作；可以授权/撤销
     * 本次写入用信息色；其余只读位置保持中性，避免整页被彩色强调占满。
     */
    fun permissionTone(state: FileAccessState): TanukiStatusTone = when {
        state.mode == FileAccessMode.WRITABLE -> TanukiStatusTone.Success
        state.needsStorageGrant -> TanukiStatusTone.Warning
        state.canGrantPhoneWrite || state.canRevokePhoneWrite -> TanukiStatusTone.Info
        else -> TanukiStatusTone.Neutral
    }

    /** 权限 chip 的无障碍描述；保持既有“权限：X，点击管理”的说法。 */
    fun permissionDescription(label: String): String = "权限：$label，点击管理"

    /**
     * 文件大小文案。
     *
     * 传入 locale 便于 JVM 固定小数点和分组规则；未知大小不伪造 0 B。
     */
    fun sizeText(bytes: Long, locale: Locale = Locale.getDefault()): String = when {
        bytes < 0 -> "无法读取大小"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(locale, "%.1f KiB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format(locale, "%.1f MiB", bytes / (1024.0 * 1024))
        else -> String.format(locale, "%.1f GiB", bytes / (1024.0 * 1024 * 1024))
    }
}

/** 会话列表里的状态标签类别；页面只选择语义，色调由这里统一决定。 */
internal enum class TanukiSessionBadgeKind {
    RUNNING,
    UNREAD,
    PINNED,
    BLANK,
    SUBAGENT,
    AGENT_ONLINE,
    MIGRATION_REQUIRED,
    CURRENT,
}

/**
 * 会话管理页的视觉映射。
 *
 * 列表行、分组和状态标签的语气在此集中，避免每个调用点各自挑选颜色。
 */
internal object TanukiSessionVisuals {
    const val ROW_MIN_HEIGHT_DP: Int = TanukiSpace.LIST_ROW_MIN_DP

    fun badgeTone(kind: TanukiSessionBadgeKind): TanukiStatusTone = when (kind) {
        TanukiSessionBadgeKind.RUNNING -> TanukiStatusTone.Success
        TanukiSessionBadgeKind.UNREAD -> TanukiStatusTone.Info
        TanukiSessionBadgeKind.PINNED -> TanukiStatusTone.Neutral
        TanukiSessionBadgeKind.BLANK -> TanukiStatusTone.Neutral
        TanukiSessionBadgeKind.SUBAGENT -> TanukiStatusTone.Neutral
        TanukiSessionBadgeKind.AGENT_ONLINE -> TanukiStatusTone.Success
        TanukiSessionBadgeKind.MIGRATION_REQUIRED -> TanukiStatusTone.Danger
        TanukiSessionBadgeKind.CURRENT -> TanukiStatusTone.Success
    }

    /** 只有“运行中”需要呼吸；系统移除动画时组件会自行保持静态。 */
    fun badgePulse(kind: TanukiSessionBadgeKind): Boolean = kind == TanukiSessionBadgeKind.RUNNING

    /**
     * 权限标签语气。
     *
     * 完全权限是最高风险档，单独用危险色；工作区写入用警告色提示越界；
     * 只读保持中性；官方返回的未知/自定义预设按信息色展示，不伪装成已知档位。
     */
    fun permissionTone(permission: String?): TanukiStatusTone = when (permission) {
        "read-only" -> TanukiStatusTone.Neutral
        "workspace-write" -> TanukiStatusTone.Warning
        "danger-full-access" -> TanukiStatusTone.Danger
        null -> TanukiStatusTone.Neutral
        else -> TanukiStatusTone.Info
    }
}
