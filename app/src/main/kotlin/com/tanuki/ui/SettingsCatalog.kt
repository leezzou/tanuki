package com.tanuki.ui

import java.text.Normalizer
import java.util.Locale

/** 搜索只产出这些导航目标；目标不携带命令或执行回调。 */
internal enum class SettingsTarget(
    val title: String,
    val section: String,
    val category: String,
    vararg val aliases: String,
) {
    THEME("主题模式", "外观与语言", "外观与语言", "主题", "浅色", "深色", "夜间", "跟随系统", "dark", "light", "theme"),
    LANGUAGE("界面语言", "外观与语言", "外观与语言", "中文", "language"),
    FONT_SIZE("字体大小", "外观与语言", "外观与语言", "字号", "文字", "font"),
    CHAT_NOTIFICATIONS("对话通知", "通知与后台", "通知与后台", "消息提醒", "完成提醒", "通知开关"),
    CHAT_NOTIFICATION_PERMISSION("对话通知设置", "通知与后台", "通知与后台", "通知权限", "消息权限"),
    UPDATE_NOTIFICATIONS("更新通知权限", "通知与后台", "通知与后台", "版本提醒", "更新提醒"),
    AUTO_START("打开应用时启动助手", "通知与后台", "通知与后台", "自动启动", "自启动", "开机"),
    BATTERY("电池与应用设置", "通知与后台", "通知与后台", "省电", "后台限制", "锁屏"),
    DEVICE_CONDITIONS("温控与省电策略", "通知与后台", "通知与后台", "设备状态", "温度", "发热", "热状态", "性能", "低电阈值"),
    WORKSPACE_CAPACITY("工作区空间限额", "存储与空间", "存储与空间", "空间限额", "容量", "存储限制", "磁盘"),
    STORAGE_USAGE("统计空间用量", "存储与空间", "存储与空间", "目录占用", "用量", "剩余空间"),
    INSTALL_CACHE("整理安装缓存", "存储与空间", "存储与空间", "精简", "安装缓存", "清理安装"),
    IMPORT_PROJECT("导入文件", "项目与备份", "存储与空间", "导入项目", "import"),
    EXPORT_PROJECT("导出项目", "项目与备份", "存储与空间", "分享项目", "export"),
    DATA_BACKUP("备份应用数据", "项目与备份", "存储与空间", "数据备份", "快照", "backup"),
    DATA_RESTORE("恢复应用数据", "项目与备份", "存储与空间", "还原", "恢复备份", "restore"),
    APP_UPDATE("应用更新", "应用更新", "引擎与更新", "检查更新", "升级应用", "apk", "版本"),
    RUNTIME_COMPONENTS("运行环境与助手版本", "组件维护与诊断", "引擎与更新", "组件", "安装", "ubuntu", "node", "dsh", "运行诊断"),
    NATIVE_CHAT("使用手机界面", "网页与兼容性", "引擎与更新", "原生对话", "聊天界面", "网页界面"),
    SESSION_MANAGER("管理对话", "网页与兼容性", "引擎与更新", "会话", "模型", "权限"),
    WEBVIEW_UPDATE("更新网页组件", "网页与兼容性", "引擎与更新", "webview", "浏览内核", "chromium"),
    WEBVIEW_SETTINGS("网页组件设置", "网页与兼容性", "引擎与更新", "webview", "系统内核"),
    WEBVIEW_CHECK("重新检查网页兼容性", "网页与兼容性", "引擎与更新", "检测详情", "webview", "css"),
    LEGACY_WEBVIEW("兼容旧网页组件", "高级与关于", "关于与诊断", "兼容层", "旧内核"),
    BLOB_EXPORT("网页文件导出", "高级与关于", "关于与诊断", "blob", "网页保存", "导出文件"),
    LICENSES("许可与组件来源", "高级与关于", "关于与诊断", "开源", "许可证", "gpl", "license"),
    DOWNLOAD_CACHE("清除下载缓存", "关于与诊断", "关于与诊断", "清理缓存", "下载空间"),
    ONBOARDING("重新查看引导", "关于与诊断", "关于与诊断", "首次启动", "欢迎", "入门"),
    REMOTE_HOSTS("远程主机连接", "远程主机", "远程主机", "连接电脑", "ssh", "扫码", "中继", "局域网"),
    ;

    val path: String get() = (listOf("设置", category, section).distinct() + title).joinToString(" › ")

    companion object {
        fun fromSavedName(value: String?): SettingsTarget? = entries.firstOrNull { it.name == value }
    }
}

internal object SettingsCatalog {
    const val MAX_QUERY_LENGTH = 160
    private val separators = Regex("[\\s\\p{Z}/›>：:]+")
    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
    private data class IndexedTarget(val target: SettingsTarget) {
        val title = normalize(target.title)
        val aliases = target.aliases.map(::normalize)
        val path = normalize(target.path)
        val fields = listOf(title, path) + aliases
    }
    private val index = SettingsTarget.entries.map(::IndexedTarget)

    fun search(query: String): List<SettingsTarget> {
        if (query.length > MAX_QUERY_LENGTH) return emptyList()
        val normalized = normalize(query).trim()
        val terms = normalized.split(separators).filter(String::isNotBlank)
        if (terms.isEmpty()) return emptyList()
        return index.filter { item -> terms.all { term -> item.fields.any { term in it } } }
            .sortedWith(compareBy<IndexedTarget> { item ->
                when {
                    item.title == normalized -> 0
                    item.title.startsWith(normalized) -> 1
                    normalized in item.title -> 2
                    normalized in item.aliases -> 3
                    else -> 4
                }
            }.thenBy { it.target.ordinal })
            .map { it.target }
    }
}
