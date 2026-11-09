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

    private var config: DeviceConditionConfig = DeviceConditionConfig()
    private var configLoaded = false
    private val mutable = MutableStateFlow(
        DeviceConditionState(
            enabled = config.enabled,
            workerCap = config.normalWorkerCap,
            logLineLimit = config.normalLogLines,
            previewMaxDimension = config.normalPreviewDimension,
            previewMaxPixels = config.normalPreviewPixels,
        )
    )
    val state = mutable.asStateFlow()

    /**
     * 状态恢复到允许后台工作时回调；参数是本次需要补跑的检查，
     * 返回值必须是调度器确认已持久入队的检查集合；未确认项会保留待办并重试。
     */
    var onBackgroundWorkResumed: ((Set<DeviceWork>) -> Set<DeviceWork>)? = null

    /** 主线程/热回调只投递请求，采样与写盘统一在后台 collector 中串行完成。 */
    private val refreshQueue = DeviceConditionRefreshQueue(scope) { refreshNow(null) }

    /** restore/config 完成前，Worker 与设置入口都必须等待同一屏障。 */
    private val initialization = DeviceConditionInitialization { initializeState() }

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
        initialization.start(scope)
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
    suspend fun beginBackgroundWork(check: DeviceWork): Boolean {
        require(check in DeviceConditionPolicy.UPDATE_CHECK_WORK) { "只有后台更新检查可以使用此入口" }
        // 冷启动时 Worker 可能先于 restore 被唤醒；等初始化完成后再评估和写入。
        startIfNeeded()
        initialization.awaitReady()
        // Worker 本身运行在 WorkManager 后台线程，这里可以同步完成评估与写盘。
        refreshNow(check)
        return state.value.backgroundChecksAllowed
    }

    fun setEnabled(enabled: Boolean) {
        synchronized(lock) {
            configLoaded = true
            config = config.copy(enabled = enabled)
        }
        startIfNeeded()
        scope.launch {
            initialization.awaitReady()
            persistConfigNow()
            refreshNow(null)
        }
    }

    /** 界面只调低电阈值；退出阈值固定比进入阈值高 5 个百分点，避免频繁抖动。 */
    fun setLowBatteryThreshold(enterPercent: Int) {
        val enter = enterPercent.coerceIn(5, 45)
        synchronized(lock) {
            configLoaded = true
            config = config.copy(
                lowBatteryEnterPercent = enter,
                lowBatteryExitPercent = (enter + 5).coerceAtMost(100),
            )
        }
        startIfNeeded()
        scope.launch {
            initialization.awaitReady()
            persistConfigNow()
            refreshNow(null)
        }
    }

    /** 主线程/广播回调只投递合并请求；实际采样、评估和写盘在后台串行执行。 */
    fun refresh() {
        if (!started) return
        refreshQueue.request()
    }

    private fun startIfNeeded() {
        if (!started) start()
    }

    /** 后台一次性初始化：加载设置、恢复待办，再启动合并刷新 collector。 */
    private fun initializeState() {
        try {
            // 设置和已有状态也在后台读取，避免 Application.onCreate 主线程做磁盘 I/O。
            // 若用户在加载完成前已修改设置，以内存中的新值为准，避免被旧文件覆盖。
            synchronized(lock) {
                if (!configLoaded) {
                    config = loadConfig()
                    configLoaded = true
                }
            }
            val restored = restore()
            synchronized(lock) { mutable.value = restored }
            refreshNow(null)
        } finally {
            refreshQueue.start()
        }
    }

    /**
     * 采样、评估、持久化并处理恢复补跑。
     *
     * 调用方必须是后台线程。待办只有在 [onBackgroundWorkResumed] 返回确认集合后才清除；
     * 调度失败或进程在中途结束时保留待办，后续采样会重试。
     */
    private fun refreshNow(workerCheck: DeviceWork?) {
        val snapshot: DeviceConditionState
        val resumed: Set<DeviceWork>
        val expectedGenerations: Map<DeviceWork, Long>
        val sequence: Long
        synchronized(lock) {
            val evaluation = DeviceConditionPolicy.evaluate(mutable.value, readSignals(), config)
            var state = evaluation.state
            var candidates = evaluation.resumedChecks
            if (workerCheck != null) {
                if (state.backgroundChecksAllowed) {
                    // 当前 Worker 会立刻执行这一项，不再为它排补跑任务。
                    candidates = candidates - workerCheck
                    state = DeviceConditionPolicy.acknowledgeChecks(state, setOf(workerCheck))
                } else {
                    state = DeviceConditionPolicy.markCheckSkipped(state, workerCheck)
                }
            }
            mutable.value = state
            snapshot = state
            resumed = candidates
            // 记录本次入队对应的待办代次；确认期间重新登记的同类检查不能被旧确认删除。
            expectedGenerations = candidates.associateWith { state.deferredGenerations[it] ?: 0L }
            sequence = refreshSequence.incrementAndGet()
        }
        persistState(snapshot, sequence)
        if (resumed.isEmpty()) return
        val confirmed = runCatching {
            onBackgroundWorkResumed?.invoke(resumed) ?: emptySet()
        }.getOrDefault(emptySet())
        if (confirmed.isEmpty()) return
        val acknowledged: DeviceConditionState
        val ackSequence: Long
        synchronized(lock) {
            acknowledged = DeviceConditionPolicy.acknowledgeChecks(
                mutable.value,
                confirmed,
                expectedGenerations,
            )
            mutable.value = acknowledged
            ackSequence = refreshSequence.incrementAndGet()
        }
        persistState(acknowledged, ackSequence)
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

    private fun persistConfigNow() {
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
            // updatedAtMillis 每次采样都变化，不写入磁盘；否则会退化为每 30 秒写一次。
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
        val deferred = workOr(saved.optJSONArray("deferredChecks"))
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
            deferredChecks = deferred,
            // 代次不跨进程持久化；恢复后从 1 开始，新的跳过会继续递增。
            deferredGenerations = deferred.associateWith { 1L },
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
