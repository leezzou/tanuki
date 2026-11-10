package com.tanuki.ui

/**
 * 原生界面的主题偏好。
 *
 * 只影响 Tanuki 自己的 Compose 界面；官方 DSH 网页主题仍由 DSH 页面自身控制，
 * 本偏好不会注入或改写官方页面样式。
 */
enum class TanukiThemeMode(val storageValue: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    /** 系统当前为深色时，按偏好决定最终是否使用深色方案。 */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** 未知或空值回退到“跟随系统”，不把坏偏好当成深色或浅色。 */
        fun parse(raw: String?): TanukiThemeMode =
            entries.firstOrNull { it.storageValue == raw } ?: SYSTEM
    }
}
