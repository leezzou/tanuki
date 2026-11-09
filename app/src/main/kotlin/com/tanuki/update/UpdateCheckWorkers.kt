package com.tanuki.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tanuki.TanukiApp
import com.tanuki.power.DeviceWork
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * APK 后台检查：联网时每 30 分钟检查一次 GitHub Release 清单。
 *
 * 失败不抛给 WorkManager、不重试轰炸、不影响应用使用；检查结果只写独立快照，
 * 发现新版本时按版本去重后发一条通知，不自动下载或安装。
 */
class ApkUpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? TanukiApp ?: return Result.success()
        // 热/低电/内存紧张时只登记延后，不启动网络检查；恢复后由调度器补跑一次性任务。
        if (!app.deviceConditions.beginBackgroundWork(DeviceWork.APK_UPDATE_CHECK)) return Result.success()
        return try {
            val state = app.apkUpdateChecks.checkNow(force = false)
            if (state.available && state.latestVersion.isNotBlank() && UpdateNotifier.canNotify(app) &&
                app.apkUpdateChecks.claimNotification(state.latestVersion)
            ) {
                UpdateNotifier.notifyApk(app, state.latestVersion)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // 静默降级：错误已由仓库记录，下一个周期再试。
            Result.success()
        }
    }
}

/**
 * DSH 引擎后台检查：联网时每 24 小时检查一次 npm 元数据。
 * 镜像失败或结构不完整时由纯策略回退官方源。
 */
class DshUpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? TanukiApp ?: return Result.success()
        // 与 APK 检查共用同一份设备状态，但各自保留独立的快照与通知去重。
        if (!app.deviceConditions.beginBackgroundWork(DeviceWork.DSH_UPDATE_CHECK)) return Result.success()
        return try {
            val state = app.dshUpdateChecks.checkNow(force = false)
            if (state.available && state.latestVersion.isNotBlank() && UpdateNotifier.canNotify(app) &&
                app.dshUpdateChecks.claimNotification(state.latestVersion)
            ) {
                UpdateNotifier.notifyDsh(app, state.latestVersion)
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Result.success()
        }
    }
}

/** 注册两个唯一周期任务；UPDATE 语义保证重复启动不会堆积同名任务。 */
object UpdateCheckScheduler {
    const val APK_RESUME_WORK_NAME = "tanuki-apk-update-resume"
    const val DSH_RESUME_WORK_NAME = "dsh-engine-update-resume"
    private const val ENQUEUE_CONFIRM_TIMEOUT_SECONDS = 10L

    fun schedule(context: Context) {
        val app = context.applicationContext
        val spec = BackgroundUpdateChecks.schedule
        runCatching {
            val manager = WorkManager.getInstance(app)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (spec.requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED
                )
                .build()
            manager.enqueueUniquePeriodicWork(
                spec.apkUniqueName,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ApkUpdateCheckWorker>(spec.apkIntervalMinutes, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .addTag(spec.apkUniqueName)
                    .build()
            )
            manager.enqueueUniquePeriodicWork(
                spec.dshUniqueName,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<DshUpdateCheckWorker>(spec.dshIntervalMinutes, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .addTag(spec.dshUniqueName)
                    .build()
            )
        }
    }

    /**
     * 设备恢复后只补跑确实被跳过的检查。
     *
     * [checks] 来自 Worker 自己的登记；空集合不会发起任何网络任务。使用唯一的
     * OneTimeWorkRequest 和 REPLACE，避免多次状态抖动堆积任务；网络仍由约束控制。
     */
    fun resumeDeferred(context: Context, checks: Set<DeviceWork>): Set<DeviceWork> {
        val apk = DeviceWork.APK_UPDATE_CHECK in checks
        val dsh = DeviceWork.DSH_UPDATE_CHECK in checks
        if (!apk && !dsh) return emptySet()
        val app = context.applicationContext
        val spec = BackgroundUpdateChecks.schedule
        val manager = runCatching { WorkManager.getInstance(app) }.getOrNull() ?: return emptySet()
        val constraints = runCatching {
            Constraints.Builder()
                .setRequiredNetworkType(
                    if (spec.requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED
                )
                .build()
        }.getOrNull() ?: return emptySet()

        val confirmed = mutableSetOf<DeviceWork>()
        if (apk) {
            val request = OneTimeWorkRequestBuilder<ApkUpdateCheckWorker>()
                .setConstraints(constraints)
                .addTag(spec.apkUniqueName)
                .build()
            if (enqueueConfirmed(manager, APK_RESUME_WORK_NAME, request)) confirmed += DeviceWork.APK_UPDATE_CHECK
        }
        if (dsh) {
            val request = OneTimeWorkRequestBuilder<DshUpdateCheckWorker>()
                .setConstraints(constraints)
                .addTag(spec.dshUniqueName)
                .build()
            if (enqueueConfirmed(manager, DSH_RESUME_WORK_NAME, request)) confirmed += DeviceWork.DSH_UPDATE_CHECK
        }
        return confirmed
    }

    /**
     * WorkManager 的 enqueue 返回 Operation；只有其持久化结果完成才确认待办已入队。
     * 失败时返回 false，待办由监控保留并在后续采样重试。
     */
    private fun enqueueConfirmed(manager: WorkManager, name: String, request: OneTimeWorkRequest): Boolean =
        runCatching {
            manager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
                .result.get(ENQUEUE_CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            true
        }.getOrDefault(false)
}
