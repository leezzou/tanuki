package com.tanuki.update

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.tanuki.MainActivity

/**
 * 后台更新提示。
 *
 * 只有“发现新版本且该版本尚未通知过”才会调用；Android 13+ 未授予通知权限时静默跳过，
 * 不弹出错误、不阻塞检查，也不触发下载或安装。
 */
object UpdateNotifier {
    const val CHANNEL_ID = "updates"
    const val APK_NOTIFICATION_ID = 3
    const val DSH_NOTIFICATION_ID = 4

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    fun notifyApk(context: Context, version: String) {
        post(
            context = context,
            id = APK_NOTIFICATION_ID,
            title = "Tanuki 有可用更新",
            text = "新版本 $version 已发布；可在“设置 → 应用更新”查看。不会自动下载或安装。"
        )
    }

    fun notifyDsh(context: Context, version: String) {
        post(
            context = context,
            id = DSH_NOTIFICATION_ID,
            title = "DSH 有可用更新",
            text = "新版本 $version 已发布；可在“组件维护与诊断”查看。不会自动下载或安装。"
        )
    }

    private fun post(context: Context, id: Int, title: String, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "应用与引擎更新", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tanuki APK 与 DSH 引擎的新版本提示"
            }
        )
        val content = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(id, notification)
    }
}
