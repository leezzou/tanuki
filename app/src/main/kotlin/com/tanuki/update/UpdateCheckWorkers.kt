package com.tanuki.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tanuki.TanukiApp
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
        if (!app.deviceConditions.beginBackgroundWork()) return Result.success()
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
        if (!app.deviceConditions.beginBackgroundWork()) return Result.success()
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
     * 设备恢复后补跑被延后的检查。
     *
     * 使用唯一的 OneTimeWorkRequest 和 REPLACE，避免多次状态抖动堆积任务；
     * 网络仍由约束控制，仓库的最小查询间隔也会阻止紧邻的重复请求。
     */
    fun resumeDeferred(context: Context) {
        val app = context.applicationContext
        val spec = BackgroundUpdateChecks.schedule
        runCatching {
            val manager = WorkManager.getInstance(app)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (spec.requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED
                )
                .build()
            manager.enqueueUniqueWork(
                APK_RESUME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ApkUpdateCheckWorker>()
                    .setConstraints(constraints)
                    .addTag(spec.apkUniqueName)
                    .build()
            )
            manager.enqueueUniqueWork(
                DSH_RESUME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<DshUpdateCheckWorker>()
                    .setConstraints(constraints)
                    .addTag(spec.dshUniqueName)
                    .build()
            )
        }
    }
}
