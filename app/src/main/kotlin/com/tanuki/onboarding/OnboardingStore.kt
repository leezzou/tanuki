package com.tanuki.onboarding

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * 引导记录的 SharedPreferences 适配层。
 *
 * 只做序列化与版本字段读写；是否展示、如何跳转仍由 [OnboardingMachine] 决定。
 * 文件独立于运行环境偏好，升级或清理 DSH 数据不应丢失“已完成引导”的状态。
 */
class OnboardingStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFERENCE_FILE, Context.MODE_PRIVATE)

    fun load(): OnboardingRecord {
        val schema = prefs.getInt(KEY_SCHEMA, 0)
        if (schema != SCHEMA) return OnboardingRecord()
        val steps = prefs.getStringSet(KEY_COMPLETED_STEPS, emptySet()).orEmpty()
            .mapNotNull(OnboardingStep::parse)
            .toSet()
        return OnboardingRecord(
            lastSeenVersion = prefs.getString(KEY_LAST_SEEN_VERSION, "").orEmpty(),
            completedVersion = prefs.getString(KEY_COMPLETED_VERSION, "").orEmpty(),
            completedSteps = steps,
            skipped = prefs.getBoolean(KEY_SKIPPED, false),
            skippedAt = OnboardingStep.parse(prefs.getString(KEY_SKIPPED_AT, null)),
            bannerDismissed = prefs.getBoolean(KEY_BANNER_DISMISSED, false),
            upgradeNoticeSeenVersion = prefs.getString(KEY_UPGRADE_SEEN, "").orEmpty(),
        )
    }

    fun save(record: OnboardingRecord) {
        prefs.edit()
            .putInt(KEY_SCHEMA, SCHEMA)
            .putString(KEY_LAST_SEEN_VERSION, record.lastSeenVersion)
            .putString(KEY_COMPLETED_VERSION, record.completedVersion)
            .putStringSet(KEY_COMPLETED_STEPS, record.completedSteps.map { it.storageValue }.toSet())
            .putBoolean(KEY_SKIPPED, record.skipped)
            .putString(KEY_SKIPPED_AT, record.skippedAt?.storageValue)
            .putBoolean(KEY_BANNER_DISMISSED, record.bannerDismissed)
            .putString(KEY_UPGRADE_SEEN, record.upgradeNoticeSeenVersion)
            .apply()
    }

    companion object {
        const val PREFERENCE_FILE = "onboarding"

        /** 记录结构变化时递增；旧结构一律按首次启动处理，避免误判为未完成。 */
        const val SCHEMA = 1

        private const val KEY_SCHEMA = "schema"
        private const val KEY_LAST_SEEN_VERSION = "last-seen-version"
        private const val KEY_COMPLETED_VERSION = "completed-version"
        private const val KEY_COMPLETED_STEPS = "completed-steps"
        private const val KEY_SKIPPED = "skipped"
        private const val KEY_SKIPPED_AT = "skipped-at"
        private const val KEY_BANNER_DISMISSED = "banner-dismissed"
        private const val KEY_UPGRADE_SEEN = "upgrade-notice-seen"
    }
}

/** 从系统读取真实设备画像；读取失败时用保守值，界面仍会提示不可用而不是崩溃。 */
object OnboardingDevice {
    fun capture(context: Context): DeviceProfile {
        val memory = runCatching {
            val info = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
            info.totalMem
        }.getOrDefault(-1L)
        val storage = runCatching { context.filesDir.usableSpace }.getOrDefault(-1L)
        return DeviceProfile(
            totalMemoryBytes = memory,
            usableStorageBytes = storage,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            sdkInt = Build.VERSION.SDK_INT,
        )
    }
}
