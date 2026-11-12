package com.tanuki

import com.tanuki.ui.SettingsCatalog
import com.tanuki.ui.SettingsTarget
import org.junit.Assert.*
import org.junit.Test

class SettingsCatalogTest {
    @Test fun titlesAndSynonymsReachSpecificControls() {
        for (query in listOf("主题", "夜间", "dark", "浅色")) {
            assertEquals(SettingsTarget.THEME, SettingsCatalog.search(query).first())
        }
        assertEquals(SettingsTarget.AUTO_START, SettingsCatalog.search("自动启动").first())
        assertEquals(SettingsTarget.WORKSPACE_CAPACITY, SettingsCatalog.search("空间限额").first())
    }

    @Test fun pathsAndMultipleTermsNarrowResults() {
        assertEquals(listOf(SettingsTarget.DATA_RESTORE), SettingsCatalog.search("存储与空间 恢复"))
        assertEquals(listOf(SettingsTarget.WEBVIEW_UPDATE), SettingsCatalog.search("引擎与更新 > 网页与兼容性 > 更新网页组件"))
        assertTrue(SettingsCatalog.search("不存在的设置").isEmpty())
    }

    @Test fun normalizationSupportsFullWidthAndCaseWithoutInventingRoutes() {
        assertEquals(SettingsCatalog.search("webview"), SettingsCatalog.search("  ＷＥＢＶＩＥＷ  "))
        assertTrue(SettingsCatalog.search("\u3000 \t\n").isEmpty())
        assertTrue(SettingsCatalog.search("x".repeat(SettingsCatalog.MAX_QUERY_LENGTH + 1)).isEmpty())
        assertNull(SettingsTarget.fromSavedName("../../CACHE"))
        assertNull(SettingsTarget.fromSavedName(null))
    }

    @Test fun destructiveResultsAreTypedNavigationDestinations() {
        assertEquals(SettingsTarget.DATA_RESTORE, SettingsCatalog.search("恢复应用数据").first())
        assertEquals("项目与备份", SettingsTarget.DATA_RESTORE.section)
        assertEquals(SettingsTarget.DOWNLOAD_CACHE, SettingsCatalog.search("清除下载缓存").first())
        assertEquals("关于与诊断", SettingsTarget.DOWNLOAD_CACHE.section)
        for (target in SettingsTarget.entries) {
            assertEquals(target, SettingsTarget.fromSavedName(target.name))
            assertTrue(target.path.contains(target.title))
            assertTrue(target.path.contains(target.section))
            assertTrue(SettingsCatalog.search(target.title).contains(target))
        }
    }

    @Test fun resultsAreUniqueDeterministicAndPreferExactTitle() {
        val results = SettingsCatalog.search("通知")
        assertEquals(results.distinct(), results)
        assertEquals(results, SettingsCatalog.search("通知"))
        assertEquals(SettingsTarget.CHAT_NOTIFICATIONS, SettingsCatalog.search("对话通知").first())
    }
}
