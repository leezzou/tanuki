package com.tanuki

import com.tanuki.update.ApkUpdateCheckState
import com.tanuki.update.BackgroundUpdateChecks
import com.tanuki.update.DshRegistry
import com.tanuki.update.DshUpdateCheckState
import com.tanuki.update.UpdateCheckStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundUpdateChecksTest {
    private data class Entry(val packageName: String, val version: String, val code: Long)

    private fun apkManifest(vararg entries: Entry, schema: Int = 1): String = JSONObject()
        .put("schemaVersion", schema)
        .put("packages", org.json.JSONArray().apply {
            entries.forEach { entry ->
                put(JSONObject()
                    .put("packageName", entry.packageName)
                    .put("versionName", entry.version)
                    .put("versionCode", entry.code))
            }
        })
        .toString()

    private fun metadata(latest: String, vararg versions: String): String = JSONObject()
        .put("dist-tags", JSONObject().put("latest", latest))
        .put("versions", JSONObject().apply { versions.forEach { put(it, JSONObject().put("version", it)) } })
        .toString()

    private val mirrorPayload = metadata("0.2.2-rc.1", "0.2.1-rc.5", "0.2.2-rc.1")
    private val officialPayload = metadata("0.2.2", "0.2.1-rc.5", "0.2.2")

    @Test fun scheduleUsesThirtyMinutesAndTwentyFourHours() {
        assertEquals(30L, BackgroundUpdateChecks.APK_INTERVAL_MINUTES)
        assertEquals(24L * 60L, BackgroundUpdateChecks.DSH_INTERVAL_MINUTES)
        assertEquals(1_440L, BackgroundUpdateChecks.DSH_INTERVAL_MINUTES)
        assertEquals("tanuki-apk-update-check", BackgroundUpdateChecks.APK_UNIQUE_WORK_NAME)
        assertEquals("dsh-engine-update-check", BackgroundUpdateChecks.DSH_UNIQUE_WORK_NAME)
        assertNotEquals(BackgroundUpdateChecks.APK_UNIQUE_WORK_NAME, BackgroundUpdateChecks.DSH_UNIQUE_WORK_NAME)
        val spec = BackgroundUpdateChecks.schedule
        assertEquals(30L, spec.apkIntervalMinutes)
        assertEquals(1_440L, spec.dshIntervalMinutes)
        assertTrue(spec.requiresNetwork)
        assertTrue(BackgroundUpdateChecks.CONNECT_TIMEOUT_MS in 10_000..15_000)
        assertTrue(BackgroundUpdateChecks.READ_TIMEOUT_MS in 10_000..15_000)
        // WorkManager 周期任务下限为 15 分钟，两个间隔都必须高于它。
        assertTrue(spec.apkIntervalMinutes >= 15L)
        assertTrue(spec.dshIntervalMinutes >= 15L)
    }

    @Test fun apkManifestChoosesHighestVersionForInstalledPackage() {
        val manifest = apkManifest(
            Entry("com.tanuki.modern", "9.9.9", 99L),
            Entry("com.tanuki.modern.debug", "0.2.0", 11L),
            Entry("com.tanuki.modern.debug", "0.2.1", 12L)
        )
        val result = BackgroundUpdateChecks.evaluateApk(
            manifest = manifest,
            installedPackage = "com.tanuki.modern.debug",
            installedVersion = "0.2.0",
            installedCode = 10L,
            now = 1_000L
        )
        assertEquals(UpdateCheckStatus.SUCCEEDED, result.status)
        assertEquals("0.2.1", result.latestVersion)
        assertEquals(12L, result.latestCode)
        assertTrue(result.available)
        assertEquals("有更新：0.2.1", result.badgeText())
        assertEquals(1_000L, result.lastCheckedAt)
    }

    @Test fun apkSameOrLowerVersionIsNotAnUpdate() {
        val same = BackgroundUpdateChecks.evaluateApk(
            apkManifest(Entry("com.tanuki.modern", "0.2.1", 10L)),
            "com.tanuki.modern", "0.2.1", 10L, now = 2_000L
        )
        assertFalse(same.available)
        assertEquals("0.2.1", same.latestVersion)
        assertNull(same.badgeText())

        val lower = BackgroundUpdateChecks.evaluateApk(
            apkManifest(Entry("com.tanuki.modern", "0.2.0", 9L)),
            "com.tanuki.modern", "0.2.1", 10L, now = 2_000L
        )
        assertFalse(lower.available)
        assertEquals("0.2.1", lower.latestVersion)
        assertEquals(10L, lower.latestCode)
    }

    @Test fun apkMalformedManifestRecordsFailureButKeepsKnownUpdate() {
        val previous = ApkUpdateCheckState(
            status = UpdateCheckStatus.SUCCEEDED,
            installedVersion = "0.2.0",
            installedCode = 10L,
            latestVersion = "0.2.1",
            latestCode = 12L,
            available = true,
            lastCheckedAt = 1_000L
        )
        val result = BackgroundUpdateChecks.evaluateApk(
            manifest = "{not-json",
            installedPackage = "com.tanuki.modern",
            installedVersion = "0.2.0",
            installedCode = 10L,
            now = 9_000L,
            previous = previous
        )
        assertEquals(UpdateCheckStatus.FAILED, result.status)
        assertEquals("0.2.1", result.latestVersion)
        assertTrue(result.available)
        assertEquals(9_000L, result.lastCheckedAt)
        assertEquals(9_000L, result.lastErrorAt)
        assertTrue(result.lastError.isNotBlank())
        assertEquals("上次后台检查未成功", result.statusText())
    }

    @Test fun staleManualApkReplayCannotClearNewerBackgroundSnapshot() {
        val background = ApkUpdateCheckState(
            status = UpdateCheckStatus.SUCCEEDED,
            installedVersion = "0.1.7",
            installedCode = 9L,
            latestVersion = "0.1.9",
            latestCode = 11L,
            available = true,
            lastCheckedAt = 2_000L
        )
        // 模拟 Activity 重建：回放一个开始于后台结果之前的“手动无更新”结果。
        val replayed = BackgroundUpdateChecks.applyManualApk(
            candidateVersion = null,
            candidateCode = null,
            startedAt = 1_000L,
            previous = background,
            installedVersion = "0.1.7",
            installedCode = 9L
        )
        assertEquals(background, replayed)
        assertEquals("0.1.9", replayed.latestVersion)
        assertEquals(2_000L, replayed.lastCheckedAt)
        assertTrue(replayed.available)

        // 真正开始于后台结果之后的手动结果仍可更新快照。
        val newer = BackgroundUpdateChecks.applyManualApk(
            candidateVersion = "0.1.10",
            candidateCode = 12L,
            startedAt = 3_000L,
            previous = background,
            installedVersion = "0.1.7",
            installedCode = 9L
        )
        assertTrue(newer.available)
        assertEquals("0.1.10", newer.latestVersion)
        assertEquals(3_000L, newer.lastCheckedAt)
    }

    @Test fun staleManualDshReplayCannotClearNewerBackgroundSnapshot() {
        val background = DshUpdateCheckState(
            status = UpdateCheckStatus.SUCCEEDED,
            installedVersion = "0.2.1",
            latestVersion = "0.2.3",
            available = true,
            lastCheckedAt = 2_000L,
            source = DshRegistry.MIRROR.label
        )
        // 模拟 Activity 重建：旧手动候选不能把较新的后台结果降级。
        val replayed = BackgroundUpdateChecks.applyManualDsh(
            latest = "0.2.2",
            startedAt = 1_000L,
            previous = background,
            installedVersion = "0.2.1"
        )
        assertEquals(background, replayed)
        assertEquals("0.2.3", replayed.latestVersion)
        assertEquals(2_000L, replayed.lastCheckedAt)
        assertTrue(replayed.available)

        val newer = BackgroundUpdateChecks.applyManualDsh(
            latest = "0.2.4",
            startedAt = 3_000L,
            previous = background,
            installedVersion = "0.2.1"
        )
        assertTrue(newer.available)
        assertEquals("0.2.4", newer.latestVersion)
        assertEquals(3_000L, newer.lastCheckedAt)
    }

    @Test fun apkAndDshPersistedStatesAreNotInterchangeable() {
        val apk = ApkUpdateCheckState(
            status = UpdateCheckStatus.SUCCEEDED,
            installedVersion = "0.1.7",
            installedCode = 9L,
            latestVersion = "0.1.8",
            latestCode = 10L,
            available = true,
            lastCheckedAt = 100L
        )
        val dsh = DshUpdateCheckState(
            status = UpdateCheckStatus.SUCCEEDED,
            installedVersion = "0.2.1-rc.5",
            latestVersion = "0.2.2",
            available = true,
            lastCheckedAt = 200L,
            source = DshRegistry.OFFICIAL.label
        )
        assertEquals(apk, BackgroundUpdateChecks.decodeApk(BackgroundUpdateChecks.encodeApk(apk)))
        assertEquals(dsh, BackgroundUpdateChecks.decodeDsh(BackgroundUpdateChecks.encodeDsh(dsh)))
        // 两个状态文件即使被错误串读，也会因 kind 不同回落到空状态，不混用字段。
        assertEquals(ApkUpdateCheckState(), BackgroundUpdateChecks.decodeApk(BackgroundUpdateChecks.encodeDsh(dsh)))
        assertEquals(DshUpdateCheckState(), BackgroundUpdateChecks.decodeDsh(BackgroundUpdateChecks.encodeApk(apk)))
    }

    @Test fun dshMirrorSuccessDoesNotTouchOfficialEndpoint() {
        val calls = mutableListOf<DshRegistry>()
        val result = BackgroundUpdateChecks.evaluateDsh("0.2.1-rc.5", now = 1_000L) { registry ->
            calls += registry
            mirrorPayload
        }
        assertEquals(listOf(DshRegistry.MIRROR), calls)
        assertEquals(UpdateCheckStatus.SUCCEEDED, result.status)
        assertEquals("0.2.2-rc.1", result.latestVersion)
        assertTrue(result.available)
        assertEquals(DshRegistry.MIRROR.label, result.source)
        assertEquals(1_000L, result.lastCheckedAt)
    }

    @Test fun dshMalformedMirrorFallsBackToOfficial() {
        val calls = mutableListOf<DshRegistry>()
        val result = BackgroundUpdateChecks.evaluateDsh("0.2.1-rc.5", now = 2_000L) { registry ->
            calls += registry
            if (registry == DshRegistry.MIRROR) "{\"dist-tags\":{\"latest\":\"0.2.2-rc.1\"}}" else officialPayload
        }
        assertEquals(listOf(DshRegistry.MIRROR, DshRegistry.OFFICIAL), calls)
        assertEquals(UpdateCheckStatus.SUCCEEDED, result.status)
        assertEquals("0.2.2", result.latestVersion)
        assertEquals(DshRegistry.OFFICIAL.label, result.source)
        assertEquals(2_000L, result.lastCheckedAt)
    }

    @Test fun dshNetworkFailureFallsBackToOfficial() {
        val result = BackgroundUpdateChecks.evaluateDsh("0.2.1-rc.5", now = 3_000L) { registry ->
            if (registry == DshRegistry.MIRROR) throw java.io.IOException("timeout") else officialPayload
        }
        assertEquals(UpdateCheckStatus.SUCCEEDED, result.status)
        assertEquals(DshRegistry.OFFICIAL.label, result.source)
        assertEquals("0.2.2", result.latestVersion)
    }

    @Test fun dshBothSourcesFailingKeepsKnownUpdateAndRecordsError() {
        val previous = DshUpdateCheckState(
            status = UpdateCheckStatus.SUCCEEDED,
            installedVersion = "0.2.1-rc.5",
            latestVersion = "0.2.2-rc.1",
            available = true,
            lastCheckedAt = 1_000L,
            source = DshRegistry.MIRROR.label
        )
        val result = BackgroundUpdateChecks.evaluateDsh("0.2.1-rc.5", now = 9_000L, previous = previous) {
            throw java.io.IOException("offline")
        }
        assertEquals(UpdateCheckStatus.FAILED, result.status)
        assertEquals("0.2.2-rc.1", result.latestVersion)
        assertTrue(result.available)
        assertEquals(9_000L, result.lastCheckedAt)
        assertEquals(9_000L, result.lastErrorAt)
        assertTrue(result.lastError.isNotBlank())
        assertEquals("上次后台检查未成功", result.statusText())
    }

    @Test fun dshSuccessAfterFailureClearsLastError() {
        val failed = DshUpdateCheckState(
            status = UpdateCheckStatus.FAILED,
            installedVersion = "0.2.1-rc.5",
            latestVersion = "0.2.2-rc.1",
            available = true,
            lastCheckedAt = 1_000L,
            lastErrorAt = 1_000L,
            lastError = "镜像与官方源均不可用"
        )
        val result = BackgroundUpdateChecks.evaluateDsh("0.2.1-rc.5", now = 5_000L, previous = failed) {
            mirrorPayload
        }
        assertEquals(UpdateCheckStatus.SUCCEEDED, result.status)
        assertEquals("", result.lastError)
        assertEquals(0L, result.lastErrorAt)
        assertEquals(5_000L, result.lastCheckedAt)
    }

    @Test fun dshVersionComparisonHandlesPrereleaseAndSameVersion() {
        assertTrue(BackgroundUpdateChecks.isUpdateAvailable("0.2.1-rc.5", "0.2.2"))
        assertTrue(BackgroundUpdateChecks.isUpdateAvailable("0.2.1-rc.5", "0.2.1"))
        assertFalse(BackgroundUpdateChecks.isUpdateAvailable("0.2.1-rc.5", "0.2.1-rc.5"))
        assertFalse(BackgroundUpdateChecks.isUpdateAvailable("0.2.2", "0.2.1"))
        assertFalse(BackgroundUpdateChecks.isUpdateAvailable("", "0.2.2"))
        assertFalse(BackgroundUpdateChecks.isUpdateAvailable("0.2.1-rc.5", "latest"))
        assertEquals("0.2.2", BackgroundUpdateChecks.latestFromMetadata(officialPayload))
        assertNull(BackgroundUpdateChecks.latestFromMetadata(metadata("9.9.9", "0.2.1-rc.5")))
        assertNull(BackgroundUpdateChecks.latestFromMetadata("not-json"))
    }

    @Test fun sameVersionIsNotifiedOnlyOnceUntilNewerVersionAppears() {
        val available = ApkUpdateCheckState(
            status = UpdateCheckStatus.SUCCEEDED,
            installedVersion = "0.1.7",
            installedCode = 9L,
            latestVersion = "0.1.8",
            latestCode = 10L,
            available = true
        )
        assertTrue(BackgroundUpdateChecks.shouldNotifyApk(available))
        val notified = BackgroundUpdateChecks.markApkNotified(available, "0.1.8")
        assertEquals("0.1.8", notified.notifiedVersion)
        assertFalse(BackgroundUpdateChecks.shouldNotifyApk(notified))
        assertFalse(BackgroundUpdateChecks.shouldNotifyApk(notified.copy(available = false)))
        val newer = notified.copy(latestVersion = "0.1.9", latestCode = 11L)
        assertTrue(BackgroundUpdateChecks.shouldNotifyApk(newer))

        val dsh = DshUpdateCheckState(
            status = UpdateCheckStatus.SUCCEEDED,
            installedVersion = "0.2.1",
            latestVersion = "0.2.2",
            available = true
        )
        assertTrue(BackgroundUpdateChecks.shouldNotifyDsh(dsh))
        assertFalse(BackgroundUpdateChecks.shouldNotifyDsh(BackgroundUpdateChecks.markDshNotified(dsh, "0.2.2")))
    }

    @Test fun duplicateQueriesInsideWindowAreSkipped() {
        assertTrue(BackgroundUpdateChecks.shouldQuery(now = 100_000L, lastCheckedAt = 0L))
        assertFalse(BackgroundUpdateChecks.shouldQuery(
            now = 100_000L,
            lastCheckedAt = 100_000L - BackgroundUpdateChecks.MIN_QUERY_INTERVAL_MS + 1L
        ))
        assertTrue(BackgroundUpdateChecks.shouldQuery(
            now = 100_000L,
            lastCheckedAt = 100_000L - BackgroundUpdateChecks.MIN_QUERY_INTERVAL_MS
        ))
        assertTrue(BackgroundUpdateChecks.shouldQuery(now = 100_000L, lastCheckedAt = 99_999L, force = true))
    }

    @Test fun checkingIsNeverPersistedAsPermanentChecking() {
        val checking = DshUpdateCheckState(status = UpdateCheckStatus.CHECKING, installedVersion = "0.2.1")
        val decoded = BackgroundUpdateChecks.decodeDsh(BackgroundUpdateChecks.encodeDsh(checking))
        assertEquals(UpdateCheckStatus.FAILED, decoded.status)
        assertFalse(decoded.checking)
    }

    @Test fun corruptOrOldSchemaFallsBackToEmptyState() {
        assertEquals(ApkUpdateCheckState(), BackgroundUpdateChecks.decodeApk(null))
        assertEquals(ApkUpdateCheckState(), BackgroundUpdateChecks.decodeApk("{"))
        assertEquals(ApkUpdateCheckState(), BackgroundUpdateChecks.decodeApk("{\"schema\":2,\"kind\":\"apk\"}"))
        assertEquals(DshUpdateCheckState(), BackgroundUpdateChecks.decodeDsh(null))
        assertEquals(DshUpdateCheckState(), BackgroundUpdateChecks.decodeDsh("{"))
        assertEquals(DshUpdateCheckState(), BackgroundUpdateChecks.decodeDsh("{\"schema\":1,\"kind\":\"other\"}"))
    }

    @Test fun persistedAvailableFlagIsRecomputedFromStoredVersions() {
        val raw = JSONObject()
            .put("schema", 1)
            .put("kind", "apk")
            .put("status", "SUCCEEDED")
            .put("installedVersion", "0.1.8")
            .put("installedCode", 10L)
            .put("latestVersion", "0.1.8")
            .put("latestCode", 10L)
            .put("available", true)
            .put("lastCheckedAt", 1L)
            .toString()
        val decoded = BackgroundUpdateChecks.decodeApk(raw)
        assertFalse(decoded.available)
        assertNull(decoded.badgeText())
    }
}
