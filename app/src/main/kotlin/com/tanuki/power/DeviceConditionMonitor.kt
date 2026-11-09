package com.tanuki.power

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.tanuki.runtime.Paths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * Android 设备状态采样与持久化。
 *
 * 热状态来自 PowerManager（API 29+ 的 thermal status 监听），电量与充电来自电池广播，
 * 低内存来自 ActivityManager 的系统内存压力。采样结果交给纯策略 [DeviceConditionPolicy]；
 * 状态、实际被跳过的后台检查与阈值写入应用私有 state 目录，进程重启后先恢复再按新信号校正。
 */
class DeviceConditionMonitor(context: Context, private val paths: Paths) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val persistLock = Any()
    private val refreshSequence = AtomicLong()
    private val power = appContext.getSystemService(PowerManager::class.java)
    private val activityManager = appContext.getSystemService(ActivityManager::class.java)

    private var config: DeviceConditionConfig = loadConfig()
    private val mutable = MutableStateFlow(restore())
    val state = mutable.asStateFlow()

    /** 状态恢复到允许后台工作时回调；参数是本次真正恢复的类别。 */
    var onBackgroundWorkResumed: ((Set<DeviceWork>) -> Unit)? = null

    private var started = false
    private var receiver: BroadcastReceiver? = null
    private var thermalListener: Any? = null
    @Volatile
    private var lastPersisted: String? = null
    private var persistedSequence = 0L

    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        registerBatteryReceiver()
        registerThermalListener()
        refresh()
        scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MILLIS)
                refresh()
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!started) return
            started = false
        }
        receiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        receiver = null
        if (Build.VERSION.SDK_INT >= 29) {
            val listener = thermalListener as? PowerManager.OnThermalStatusChangedListener
            if (listener != null) runCatching { power?.removeThermalStatusListener(listener) }
        }
        thermalListener = null
        scope.cancel()
    }

    /**
     * 供具体后台检查的 Worker 在开始前调用。
     *
     * 允许时返回 true；不允许时只登记当前 Worker 这一项待办，不会代替其他检查登记。
     * 当前 Worker 马上会执行的检查不会再次排入补跑队列，避免同一周期重复请求。
     */
    fun beginBackgroundWork(check: DeviceWork): Boolean {
        require(check in DeviceConditionPolicy.UPDATE_CHECK_WORK) { "只有后台更新检查可以使用此入口" }
        val snapshot: DeviceConditionState
        val resumed: Set<DeviceWork>
        val sequence: Long
        synchronized(lock) {
            val evaluation = evaluateLocked(check)
            snapshot = evaluation.first
            resumed = evaluation.second
            sequence = refreshSequence.incrementAndGet()
        }
        persistState(snapshot, sequence)
        if (resumed.isNotEmpty()) onBackgroundWorkResumed?.invoke(resumed)
        return snapshot.backgroundChecksAllowed
    }

    fun setEnabled(enabled: Boolean) {
        synchronized(lock) { config = config.copy(enabled = enabled) }
        persistConfig()
        refresh()
    }

    /** 界面只调低电阈值；退出阈值固定比进入阈值高 5 个百分点，避免频繁抖动。 */
    fun setLowBatteryThreshold(enterPercent: Int) {
        val enter = enterPercent.coerceIn(5, 45)
        synchronized(lock) {
            config = config.copy(
                lowBatteryEnterPercent = enter,
                lowBatteryExitPercent = (enter + 5).coerceAtMost(100),
            )
        }
        persistConfig()
        refresh()
    }

    fun refresh() {
        val snapshot: DeviceConditionState
        val resumed: Set<DeviceWork>
        val sequence: Long
        synchronized(lock) {
            val evaluation = evaluateLocked()
            snapshot = evaluation.first
            resumed = evaluation.second
            sequence = refreshSequence.incrementAndGet()
        }
        persistState(snapshot, sequence)
        if (resumed.isNotEmpty()) onBackgroundWorkResumed?.invoke(resumed)
    }

    /** 在持有 [lock] 时采样、评估并写入当前状态；可选地由当前 Worker 登记或消费自己的检查。 */
    private fun evaluateLocked(workerCheck: DeviceWork? = null): Pair<DeviceConditionState, Set<DeviceWork>> {
        val evaluation = DeviceConditionPolicy.evaluate(mutable.value, readSignals(), config)
        var snapshot = evaluation.state
        var resumed = evaluation.resumedChecks
        if (workerCheck != null) {
            if (snapshot.backgroundChecksAllowed) {
                // 当前 Worker 会立刻执行这一项，不需要再为它排一次补跑。
                resumed = resumed - workerCheck
            } else {
                snapshot = DeviceConditionPolicy.markCheckSkipped(snapshot, workerCheck)
            }
        }
        mutable.value = snapshot
        return snapshot to resumed
    }

    private fun readSignals(): DeviceSignals {
        val thermal = if (Build.VERSION.SDK_INT >= 29) {
            ThermalLevel.fromPlatform(power?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE)
        } else {
            ThermalLevel.NONE
        }
        val battery = readBattery()
        val info = ActivityManager.MemoryInfo()
        val lowMemory = runCatching {
            activityManager?.getMemoryInfo(info)
            info.lowMemory
        }.getOrDefault(false)
        return DeviceSignals(
            thermalLevel = thermal,
            batteryPercent = battery.first,
            charging = battery.second,
            lowMemory = lowMemory,
            nowMillis = System.currentTimeMillis(),
        )
    }

    private fun readBattery(): Pair<Int, Boolean> {
        val intent = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull() ?: return -1 to false
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else -1
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL || plugged != 0
        return percent to charging
    }

    private fun registerBatteryReceiver() {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        val created = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = refresh()
        }
        runCatching {
            // Android 13 起动态注册非系统专用接收器必须显式声明导出范围。
            ContextCompat.registerReceiver(appContext, created, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.onSuccess { receiver = created }
    }

    private fun registerThermalListener() {
        if (Build.VERSION.SDK_INT < 29 || thermalListener != null) return
        val listener = PowerManager.OnThermalStatusChangedListener { refresh() }
        runCatching {
            power?.addThermalStatusListener(ContextCompat.getMainExecutor(appContext), listener)
        }.onSuccess { thermalListener = listener }
    }

    private fun loadConfig(): DeviceConditionConfig {
        val defaults = DeviceConditionConfig()
        val saved = runCatching { JSONObject(paths.read(CONFIG_FILE) ?: "{}") }.getOrDefault(JSONObject())
        val enter = saved.optInt("lowBatteryEnterPercent", defaults.lowBatteryEnterPercent).coerceIn(5, 45)
        return defaults.copy(
            enabled = saved.optBoolean("enabled", defaults.enabled),
            warmThermalLevel = enumOr(saved.optString("warmThermalLevel"), defaults.warmThermalLevel),
            hotThermalLevel = enumOr(saved.optString("hotThermalLevel"), defaults.hotThermalLevel),
            criticalThermalLevel = enumOr(saved.optString("criticalThermalLevel"), defaults.criticalThermalLevel),
            lowBatteryEnterPercent = enter,
            lowBatteryExitPercent = (enter + 5).coerceAtMost(100),
        )
    }

    private fun persistConfig() {
        val snapshot = synchronized(lock) { config }
        val json = JSONObject()
            .put("schema", SCHEMA)
            .put("enabled", snapshot.enabled)
            .put("warmThermalLevel", snapshot.warmThermalLevel.name)
            .put("hotThermalLevel", snapshot.hotThermalLevel.name)
            .put("criticalThermalLevel", snapshot.criticalThermalLevel.name)
            .put("lowBatteryEnterPercent", snapshot.lowBatteryEnterPercent)
            .put("lowBatteryExitPercent", snapshot.lowBatteryExitPercent)
        runCatching { paths.atomic(CONFIG_FILE, json.toString()) }
    }

    private fun persistState(snapshot: DeviceConditionState, sequence: Long) {
        val json = JSONObject()
            .put("schema", SCHEMA)
            .put("condition", snapshot.condition.name)
            .put("reasons", JSONArray(snapshot.reasons.map { it.name }))
            .put("enabled", snapshot.enabled)
            .put("thermalLevel", snapshot.thermalLevel.name)
            .put("batteryPercent", snapshot.batteryPercent)
            .put("charging", snapshot.charging)
            .put("lowMemory", snapshot.lowMemory)
            .put("thermalCondition", snapshot.thermalCondition.name)
            .put("thermalHoldSinceMillis", snapshot.thermalHoldSinceMillis)
            .put("memoryWarm", snapshot.memoryWarm)
            .put("memoryHoldSinceMillis", snapshot.memoryHoldSinceMillis)
            .put("lowBatteryHeld", snapshot.lowBatteryHeld)
            .put("workerCap", snapshot.workerCap)
            .put("logLineLimit", snapshot.logLineLimit)
            .put("previewMaxDimension", snapshot.previewMaxDimension)
            .put("previewMaxPixels", snapshot.previewMaxPixels)
            .put("backgroundChecksAllowed", snapshot.backgroundChecksAllowed)
            .put("deferredChecks", JSONArray(snapshot.deferredChecks.map { it.name }))
            .put("lowBatteryEnterPercent", snapshot.lowBatteryEnterPercent)
            .put("sinceMillis", snapshot.sinceMillis)
            .put("updatedAtMillis", snapshot.updatedAtMillis)
            .toString()
        // 采样可能来自主线程热回调与后台轮询；序号保证旧快照不会后写覆盖新快照。
        synchronized(persistLock) {
            if (sequence <= persistedSequence) return
            if (json == lastPersisted) {
                persistedSequence = sequence
                return
            }
            if (runCatching { paths.atomic(STATE_FILE, json) }.isSuccess) {
                lastPersisted = json
                persistedSequence = sequence
            }
        }
    }

    private fun restore(): DeviceConditionState {
        val saved = runCatching { JSONObject(paths.read(STATE_FILE) ?: "{}") }.getOrDefault(JSONObject())
        if (saved.length() == 0) {
            return DeviceConditionState(
                enabled = config.enabled,
                workerCap = config.normalWorkerCap,
                logLineLimit = config.normalLogLines,
                previewMaxDimension = config.normalPreviewDimension,
                previewMaxPixels = config.normalPreviewPixels,
                lowBatteryEnterPercent = config.lowBatteryEnterPercent,
            )
        }
        return DeviceConditionState(
            condition = enumOr(saved.optString("condition"), DeviceCondition.NORMAL),
            reasons = reasonsOr(saved.optJSONArray("reasons")),
            enabled = config.enabled,
            thermalLevel = enumOr(saved.optString("thermalLevel"), ThermalLevel.NONE),
            batteryPercent = saved.optInt("batteryPercent", -1),
            charging = saved.optBoolean("charging", false),
            lowMemory = saved.optBoolean("lowMemory", false),
            thermalCondition = enumOr(saved.optString("thermalCondition"), DeviceCondition.NORMAL),
            thermalHoldSinceMillis = saved.optLong("thermalHoldSinceMillis", 0L),
            memoryWarm = saved.optBoolean("memoryWarm", false),
            memoryHoldSinceMillis = saved.optLong("memoryHoldSinceMillis", 0L),
            lowBatteryHeld = saved.optBoolean("lowBatteryHeld", false),
            workerCap = saved.optInt("workerCap", config.normalWorkerCap),
            logLineLimit = saved.optInt("logLineLimit", config.normalLogLines),
            previewMaxDimension = saved.optInt("previewMaxDimension", config.normalPreviewDimension),
            previewMaxPixels = saved.optInt("previewMaxPixels", config.normalPreviewPixels),
            backgroundChecksAllowed = saved.optBoolean("backgroundChecksAllowed", true),
            deferredChecks = workOr(saved.optJSONArray("deferredChecks")),
            lowBatteryEnterPercent = config.lowBatteryEnterPercent,
            sinceMillis = saved.optLong("sinceMillis", 0L),
            updatedAtMillis = saved.optLong("updatedAtMillis", 0L),
        )
    }

    private fun reasonsOr(array: JSONArray?): Set<DeviceConditionReason> =
        (0 until (array?.length() ?: 0)).mapNotNull { index ->
            enumOrOrNull<DeviceConditionReason>(array?.optString(index))
        }.toSet()

    private fun workOr(array: JSONArray?): Set<DeviceWork> =
        (0 until (array?.length() ?: 0)).mapNotNull { index ->
            enumOrOrNull<DeviceWork>(array?.optString(index))
        }.toSet()

    private inline fun <reified T : Enum<T>> enumOr(value: String, fallback: T): T =
        enumOrOrNull<T>(value) ?: fallback

    private inline fun <reified T : Enum<T>> enumOrOrNull(value: String?): T? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    companion object {
        private const val SCHEMA = 1
        private const val STATE_FILE = "device-condition.json"
        private const val CONFIG_FILE = "device-condition-settings.json"
        private const val POLL_INTERVAL_MILLIS = 30_000L
    }
}
