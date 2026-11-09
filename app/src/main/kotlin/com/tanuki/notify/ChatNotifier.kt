package com.tanuki.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.tanuki.MainActivity

/** 应用内聊天通知总开关；默认开启，关闭后策略层返回 USER_DISABLED。 */
object ChatNotificationPreferences {
    private const val FILE = "chat-notifications"
    private const val KEY_ENABLED = "enabled"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}

/**
 * DSH 会话通知的系统投递层。
 *
 * 只负责 Android 13 权限、渠道、group/summary、PendingIntent 和设置入口；事件是否应该
 * 提醒由 [ChatNotificationPolicy] 决定。通知正文固定使用 [ChatNotificationCopy]，
 * 不读取或输出 DSH 会话内容。
 */
class ChatNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "DSH 会话通知", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "回合完成、等待确认、错误或权限请求；不包含会话内容"
                setShowBadge(true)
            }
        )
    }

    fun permissionState(): ChatPermissionState {
        val sdk = Build.VERSION.SDK_INT
        val runtimeGranted = sdk < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val channel = if (sdk >= 26) manager.getNotificationChannel(CHANNEL_ID) else null
        return ChatPermissionState(
            sdkInt = sdk,
            runtimeGranted = runtimeGranted,
            appNotificationsEnabled = manager.areNotificationsEnabled(),
            channelEnabled = channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE,
            userEnabled = ChatNotificationPreferences.enabled(context),
        )
    }

    fun status(): ChatNotificationStatus = ChatNotificationStatus(permissionState())

    fun post(event: ChatEvent) {
        ensureChannel()
        val title = ChatNotificationCopy.title(event.type)
        val text = ChatNotificationCopy.text(event.type)
        val target = ChatNotificationRouting.child(event.sessionId)
        manager.notify(target.tag, target.id, childNotification(target, event.sessionId, title, text))
        // 摘要静默跟随；子通知独立发声，重复事件由策略层拦截。
        val summary = ChatNotificationRouting.summary()
        manager.notify(summary.tag, summary.id, summaryNotification(summary))
    }

    private fun childNotification(
        target: ChatNotificationTarget,
        sessionId: String,
        title: String,
        text: String,
    ): Notification {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(openSessionIntent(target, sessionId))
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setCategory(Notification.CATEGORY_STATUS)
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
        // 新事件（策略层已去重）必须再次提醒；不要用 setOnlyAlertOnce 压掉后续审批/错误。
        if (target.onlyAlertOnce) builder.setOnlyAlertOnce(true)
        return builder.build()
    }

    private fun summaryNotification(target: ChatNotificationTarget): Notification {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("DSH 会话动态")
            .setContentText("点击返回 Tanuki 查看会话。通知不包含会话内容。")
            .setContentIntent(summaryIntent(target))
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setCategory(Notification.CATEGORY_STATUS)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN)
        if (target.onlyAlertOnce) builder.setOnlyAlertOnce(true)
        // 摘要只承载分组，不重复响铃/振动；平台 Builder 没有 setSilent，显式清空声音与振动。
        builder.setSound(null).setVibrate(null)
        return builder.build()
    }

    private fun openSessionIntent(target: ChatNotificationTarget, sessionId: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            target.requestCode,
            Intent(context, MainActivity::class.java)
                .setAction(target.action)
                .setData(Uri.parse(target.dataUri))
                .putExtra(EXTRA_SESSION_ID, sessionId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun summaryIntent(target: ChatNotificationTarget): PendingIntent =
        PendingIntent.getActivity(
            context,
            target.requestCode,
            Intent(context, MainActivity::class.java)
                .setAction(target.action)
                .setData(Uri.parse(target.dataUri))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** 渠道设置优先；厂商 ROM 没有该页面时回退到应用通知设置，再回退到应用详情。 */
    fun notificationSettingsIntent(): Intent = runCatching {
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
    }.getOrElse {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }

    fun appNotificationSettingsIntent(): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    companion object {
        const val CHANNEL_ID = "dsh-chat"
        const val GROUP_KEY = "com.tanuki.DSH_CHAT"
        const val EXTRA_SESSION_ID = "com.tanuki.extra.DSH_CHAT_SESSION"
    }
}
