package com.tanuki.ui.design

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 系统设置的可观察快照；读取函数可注入，设置通知与回前台走同一刷新路径。 */
internal class TanukiReducedMotionState(private val read: () -> Boolean) {
    private val mutable = MutableStateFlow(read())
    val reducedMotion: StateFlow<Boolean> = mutable.asStateFlow()
    fun refresh() { mutable.value = read() }
}

/** 进程级来源，不持有 Activity；Compose 与原生浮窗共用订阅，生命周期等同应用进程。 */
internal class TanukiMotionSettings private constructor(application: Application) {
    private val resolver = application.contentResolver
    val state = TanukiReducedMotionState {
        runCatching { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0f }
            .getOrElse { !ValueAnimator.areAnimatorsEnabled() }
    }
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { state.refresh() }
    }

    init {
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { state.refresh() }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        // 注册与首次读取之间也可能发生设置变更，订阅完成后再补读一次。
        state.refresh()
    }

    companion object {
        @Volatile private var instance: TanukiMotionSettings? = null
        fun get(context: Context): TanukiReducedMotionState =
            (instance ?: synchronized(this) {
                instance ?: TanukiMotionSettings(context.applicationContext as Application).also { instance = it }
            }).state
    }
}
