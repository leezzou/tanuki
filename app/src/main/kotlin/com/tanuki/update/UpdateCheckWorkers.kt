package com.tanuki.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
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
}
