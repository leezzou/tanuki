package com.tanuki.power

/**
 * 设备温控与省电的纯策略层。
 *
 * 本文件不引用 Android、WorkManager 或协程实现，只接收已经采样好的设备信号，
 * 输出状态、降级额度、延后工作与恢复结果，便于在 JVM 中覆盖状态转换、迟滞和恢复分支。
 */
enum class ThermalLevel {
    NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN;

    companion object {
        /** 与 PowerManager.THERMAL_STATUS_* 的整数值一一对应；未知值按 NONE 处理。 */
        fun fromPlatform(status: Int): ThermalLevel = when (status) {
            1 -> LIGHT
            2 -> MODERATE
            3 -> SEVERE
            4 -> CRITICAL
            5 -> EMERGENCY
            6 -> SHUTDOWN
            else -> NONE
        }
    }
}

/** 对外展示的主状态。低电与过热可同时存在，原因集合会保留两者。 */
enum class DeviceCondition { NORMAL, WARM, HOT, CRITICAL, LOW_BATTERY }

/** 触发降级的具体原因，界面只展示中文说明，不暴露平台原始值。 */
enum class DeviceConditionReason { THERMAL, LOW_BATTERY, LOW_MEMORY }

/**
 * 可被策略影响的工作类别。
 *
 * FOREGROUND_TASK 与 DSH_TASK 是受保护类别：任何状态下都只允许继续运行，
 * 不得暂停、取消或缩短已领取的任务；其余类别才允许降并发或延后。
 */
enum class DeviceWork {
    FOREGROUND_TASK,
    DSH_TASK,
    USER_DOWNLOAD,
    NPM_DEPENDENCY_DOWNLOAD,
    APK_UPDATE_CHECK,
    DSH_UPDATE_CHECK,
    LOG_RENDER,
    PREVIEW_RENDER;

    companion object {
        /** 只有这两个具体检查会登记“实际被跳过、待恢复补跑”。 */
        val UPDATE_CHECKS: Set<DeviceWork> = setOf(APK_UPDATE_CHECK, DSH_UPDATE_CHECK)
    }
}

enum class WorkDisposition { RUN, THROTTLE, DEFER }

data class WorkDecision(val disposition: WorkDisposition, val reason: String)

/** 一次采样的设备信号；batteryPercent 小于 0 表示系统尚未给出可用电量。 */
data class DeviceSignals(
    val thermalLevel: ThermalLevel = ThermalLevel.NONE,
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val lowMemory: Boolean = false,
    val nowMillis: Long = 0L,
)

/**
 * 阈值与额度。默认值来自保守的移动设备体验：
 * 轻热只降并发，中热以上延后后台更新，严重热只保留最少下载窗口，低电与过热取更严格者。
 */
data class DeviceConditionConfig(
    val enabled: Boolean = true,
    val warmThermalLevel: ThermalLevel = ThermalLevel.LIGHT,
    val hotThermalLevel: ThermalLevel = ThermalLevel.MODERATE,
    val criticalThermalLevel: ThermalLevel = ThermalLevel.SEVERE,
    val lowBatteryEnterPercent: Int = 20,
    val lowBatteryExitPercent: Int = 25,
    /** 温度降级需连续低于退出边界的时间，避免在阈值附近反复跳变。 */
    val thermalDowngradeDwellMillis: Long = 30_000L,
    /** 低内存解除后保持降级的时间，避免内存压力抖动导致后台任务反复启停。 */
    val memoryDowngradeDwellMillis: Long = 60_000L,
    val normalWorkerCap: Int = 16,
    val warmWorkerCap: Int = 8,
    val hotWorkerCap: Int = 4,
    val criticalWorkerCap: Int = 2,
    val lowBatteryWorkerCap: Int = 4,
    val normalLogLines: Int = 80,
    val warmLogLines: Int = 60,
    val hotLogLines: Int = 40,
    val criticalLogLines: Int = 20,
    val lowBatteryLogLines: Int = 40,
    val normalPreviewDimension: Int = 2048,
    val warmPreviewDimension: Int = 1600,
    val hotPreviewDimension: Int = 1280,
    val criticalPreviewDimension: Int = 1024,
    val lowBatteryPreviewDimension: Int = 1280,
    val normalPreviewPixels: Int = 4_000_000,
    val warmPreviewPixels: Int = 2_500_000,
    val hotPreviewPixels: Int = 1_600_000,
    val criticalPreviewPixels: Int = 1_000_000,
    val lowBatteryPreviewPixels: Int = 1_600_000,
) {
    init {
        require(warmThermalLevel != ThermalLevel.NONE) { "轻热阈值不能为 NONE" }
        require(warmThermalLevel.ordinal < hotThermalLevel.ordinal) { "轻热阈值必须低于中热阈值" }
        require(hotThermalLevel.ordinal < criticalThermalLevel.ordinal) { "中热阈值必须低于严重热阈值" }
        require(lowBatteryEnterPercent in 0..100) { "低电进入阈值必须在 0..100" }
        require(lowBatteryExitPercent in lowBatteryEnterPercent..100 && lowBatteryExitPercent > lowBatteryEnterPercent) {
            "低电退出阈值必须大于进入阈值且不超过 100"
        }
        require(thermalDowngradeDwellMillis >= 0L) { "温度迟滞时间不能为负" }
        require(memoryDowngradeDwellMillis >= 0L) { "内存迟滞时间不能为负" }
        require(criticalWorkerCap in 1..hotWorkerCap && hotWorkerCap in 1..warmWorkerCap && warmWorkerCap in 1..normalWorkerCap) {
            "下载并发额度必须逐级递减且至少为 1"
        }
        require(criticalLogLines in 1..hotLogLines && hotLogLines in 1..warmLogLines && warmLogLines in 1..normalLogLines) {
            "日志行数上限必须逐级递减且至少为 1"
        }
        require(criticalPreviewDimension in 256..hotPreviewDimension && hotPreviewDimension <= warmPreviewDimension && warmPreviewDimension <= normalPreviewDimension) {
            "预览尺寸上限必须逐级递减且不小于 256"
        }
        require(criticalPreviewPixels in 1..hotPreviewPixels && hotPreviewPixels <= warmPreviewPixels && warmPreviewPixels <= normalPreviewPixels) {
            "预览像素上限必须逐级递减且至少为 1"
        }
        require(lowBatteryPreviewDimension in 256..normalPreviewDimension) { "低电预览尺寸上限无效" }
        require(lowBatteryPreviewPixels in 1..normalPreviewPixels) { "低电预览像素上限无效" }
    }
}

/** 策略输出的完整快照；用于界面展示，也用于进程重启后的恢复。 */
data class DeviceConditionState(
    val condition: DeviceCondition = DeviceCondition.NORMAL,
    val reasons: Set<DeviceConditionReason> = emptySet(),
    val enabled: Boolean = true,
    val thermalLevel: ThermalLevel = ThermalLevel.NONE,
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val lowMemory: Boolean = false,
    /** 只看热信号得到的等级，低电与低内存不会写入这里。 */
    val thermalCondition: DeviceCondition = DeviceCondition.NORMAL,
    val thermalHoldSinceMillis: Long = 0L,
    val memoryWarm: Boolean = false,
    val memoryHoldSinceMillis: Long = 0L,
    val lowBatteryHeld: Boolean = false,
    val workerCap: Int = 16,
    val logLineLimit: Int = 80,
    val previewMaxDimension: Int = 2048,
    val previewMaxPixels: Int = 4_000_000,
    val backgroundChecksAllowed: Boolean = true,
    /** 已实际被跳过的具体后台检查；仅由 Worker 在确认无法运行时登记。 */
    val deferredChecks: Set<DeviceWork> = emptySet(),
    val lowBatteryEnterPercent: Int = 20,
    val sinceMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
) {
    /** 低电阈值用于界面说明，退出阈值固定为进入阈值 + 5。 */
    val lowBatteryExitPercent: Int get() = (lowBatteryEnterPercent + 5).coerceAtMost(100)

    fun reasonText(): String = when {
        reasons.isEmpty() -> "设备状态正常"
        else -> reasons.joinToString("、") {
            when (it) {
                DeviceConditionReason.THERMAL -> when (condition) {
                    DeviceCondition.CRITICAL -> "设备严重过热"
                    DeviceCondition.HOT -> "设备过热"
                    else -> "设备温度偏高"
                }
                DeviceConditionReason.LOW_BATTERY -> "电量偏低且未充电"
                DeviceConditionReason.LOW_MEMORY -> "系统内存紧张"
            }
        }
    }

    fun statusText(): String = when (condition) {
        DeviceCondition.NORMAL -> "正常"
        DeviceCondition.WARM -> "轻度降级，已降低非关键并发"
        DeviceCondition.HOT -> "过热，后台更新与下载已降速"
        DeviceCondition.CRITICAL -> "严重过热，仅保留必要前台任务与最低下载窗口"
        DeviceCondition.LOW_BATTERY -> "低电，后台更新延后、下载降速"
    }
}

data class DeviceEvaluation(
    val state: DeviceConditionState,
    /** 本次因状态恢复而需要补跑的具体检查；空集合表示没有实际待办。 */
    val resumedChecks: Set<DeviceWork> = emptySet(),
)

/**
 * 状态机入口。
 *
 * 温度按进入阈值升级、按退出阈值降级并要求持续 [DeviceConditionConfig.thermalDowngradeDwellMillis]，
 * 因此 CRITICAL 不能因为一次瞬时降温直接回到 NORMAL；电量用进入/退出两个阈值做迟滞。
 */
object DeviceConditionPolicy {
    /** 任何状态下都不允许被暂停或取消的工作。 */
    val PROTECTED_WORK: Set<DeviceWork> = setOf(DeviceWork.FOREGROUND_TASK, DeviceWork.DSH_TASK)

    /** 只有具体后台检查会登记“实际被跳过、待恢复补跑”；下载只降并发，不丢弃已领取的归档。 */
    val UPDATE_CHECK_WORK: Set<DeviceWork> = DeviceWork.UPDATE_CHECKS

    /**
     * 由确认被状态阻塞的 Worker 调用，登记自己这一项待办。
     * 状态进入 HOT/低电本身不会登记任何检查，避免没有到期任务时恢复仍发起网络请求。
     */
    fun markCheckSkipped(state: DeviceConditionState, work: DeviceWork): DeviceConditionState {
        require(work in UPDATE_CHECK_WORK) { "只有后台更新检查可以登记延后" }
        return state.copy(deferredChecks = state.deferredChecks + work)
    }

    /**
     * 补跑任务已经可靠入队后才确认清除待办；入队失败时保留，下一次采样可重试。
     * 只清除 [checks] 中确认过的项，未确认的检查继续保留。
     */
    fun acknowledgeChecks(state: DeviceConditionState, checks: Set<DeviceWork>): DeviceConditionState =
        state.copy(deferredChecks = state.deferredChecks - checks)

    fun evaluate(
        previous: DeviceConditionState?,
        signals: DeviceSignals,
        config: DeviceConditionConfig = DeviceConditionConfig(),
    ): DeviceEvaluation {
        val prev = previous ?: DeviceConditionState(
            enabled = config.enabled,
            lowBatteryEnterPercent = config.lowBatteryEnterPercent,
            sinceMillis = signals.nowMillis,
            updatedAtMillis = signals.nowMillis,
        )

        if (!config.enabled) {
            val resumed = prev.deferredChecks
            val normal = DeviceConditionState(
                condition = DeviceCondition.NORMAL,
                reasons = emptySet(),
                enabled = false,
                thermalLevel = signals.thermalLevel,
                batteryPercent = signals.batteryPercent,
                charging = signals.charging,
                lowMemory = signals.lowMemory,
                workerCap = config.normalWorkerCap,
                logLineLimit = config.normalLogLines,
                previewMaxDimension = config.normalPreviewDimension,
                previewMaxPixels = config.normalPreviewPixels,
                backgroundChecksAllowed = true,
                // 待办要等 WorkManager 确认入队后才清除，关闭策略也不例外。
                deferredChecks = prev.deferredChecks,
                lowBatteryEnterPercent = config.lowBatteryEnterPercent,
                sinceMillis = if (prev.condition == DeviceCondition.NORMAL) prev.sinceMillis else signals.nowMillis,
                updatedAtMillis = signals.nowMillis,
            )
            return DeviceEvaluation(normal, resumedChecks = resumed)
        }

        val thermal = thermalTarget(prev, signals, config)
        val memory = memoryTarget(prev, signals, config)
        val lowBattery = lowBatteryTarget(prev, signals, config)
        val thermalCondition = maxThermal(thermal.condition, if (memory.warm) DeviceCondition.WARM else DeviceCondition.NORMAL)

        val reasons = buildSet {
            if (thermal.condition != DeviceCondition.NORMAL) add(DeviceConditionReason.THERMAL)
            if (memory.warm) add(DeviceConditionReason.LOW_MEMORY)
            if (lowBattery) add(DeviceConditionReason.LOW_BATTERY)
        }
        val condition = when (thermalCondition) {
            DeviceCondition.CRITICAL -> DeviceCondition.CRITICAL
            DeviceCondition.HOT -> DeviceCondition.HOT
            DeviceCondition.WARM -> DeviceCondition.WARM
            else -> if (lowBattery) DeviceCondition.LOW_BATTERY else DeviceCondition.NORMAL
        }

        val workerCap = workerCap(thermalCondition, lowBattery, config)
        val logLines = logLines(thermalCondition, lowBattery, config)
        val previewDimension = previewDimension(thermalCondition, lowBattery, config)
        val previewPixels = previewPixels(thermalCondition, lowBattery, config)
        val backgroundAllowed = thermalCondition != DeviceCondition.HOT &&
            thermalCondition != DeviceCondition.CRITICAL && !lowBattery
        // 恢复时只报告“确实被跳过的具体检查”；待办保留到调度器确认补跑已入队。
        val resumed = if (backgroundAllowed) prev.deferredChecks else emptySet()
        val deferred = prev.deferredChecks

        val state = DeviceConditionState(
            condition = condition,
            reasons = reasons,
            enabled = true,
            thermalLevel = signals.thermalLevel,
            batteryPercent = signals.batteryPercent,
            charging = signals.charging,
            lowMemory = signals.lowMemory,
            // 这里只保存热信号等级；低内存只参与组合主状态，避免内存恢复时又被热迟滞拖住。
            thermalCondition = thermal.condition,
            thermalHoldSinceMillis = thermal.holdSinceMillis,
            memoryWarm = memory.warm,
            memoryHoldSinceMillis = memory.holdSinceMillis,
            lowBatteryHeld = lowBattery,
            workerCap = workerCap,
            logLineLimit = logLines,
            previewMaxDimension = previewDimension,
            previewMaxPixels = previewPixels,
            backgroundChecksAllowed = backgroundAllowed,
            deferredChecks = deferred,
            lowBatteryEnterPercent = config.lowBatteryEnterPercent,
            sinceMillis = if (prev.condition == condition) prev.sinceMillis else signals.nowMillis,
            updatedAtMillis = signals.nowMillis,
        )
        return DeviceEvaluation(state, resumedChecks = resumed)
    }

    /**
     * 工作类别决策。受保护的前台与 DSH 任务永远 RUN；只有后台更新检查会 DEFER，
     * 其余降级类别用 THROTTLE 表示缩并发/缩渲染，而不是中断。
     */
    fun decide(state: DeviceConditionState, work: DeviceWork): WorkDecision {
        if (work in PROTECTED_WORK) {
            return WorkDecision(WorkDisposition.RUN, "前台任务与 DSH 任务不暂停、不取消")
        }
        if (!state.enabled) return WorkDecision(WorkDisposition.RUN, "温控与省电策略已关闭")
        if (work in state.deferredChecks) {
            return WorkDecision(WorkDisposition.DEFER, "${state.reasonText()}，${workLabel(work)}已跳过一次，恢复后补跑")
        }
        return when (work) {
            DeviceWork.APK_UPDATE_CHECK, DeviceWork.DSH_UPDATE_CHECK ->
                if (state.backgroundChecksAllowed) WorkDecision(WorkDisposition.RUN, "当前状态允许后台更新检查")
                else WorkDecision(WorkDisposition.DEFER, "${state.reasonText()}，${workLabel(work)}延后到状态恢复")
            DeviceWork.NPM_DEPENDENCY_DOWNLOAD ->
                if (state.condition == DeviceCondition.NORMAL) WorkDecision(WorkDisposition.RUN, "依赖下载按正常额度运行")
                else WorkDecision(WorkDisposition.THROTTLE, "${state.reasonText()}，依赖下载并发降至 ${state.workerCap}")
            // 应用更新是单路用户主动下载，策略不中断；真正需要降并发的是上面的依赖归档池。
            DeviceWork.USER_DOWNLOAD -> WorkDecision(WorkDisposition.RUN, "用户主动下载保持单路运行，不取消已开始的文件")
            DeviceWork.LOG_RENDER ->
                if (state.condition == DeviceCondition.NORMAL) WorkDecision(WorkDisposition.RUN, "日志按正常行数展示")
                else WorkDecision(WorkDisposition.THROTTLE, "${state.reasonText()}，日志展示降为 ${state.logLineLimit} 行")
            DeviceWork.PREVIEW_RENDER ->
                if (state.condition == DeviceCondition.NORMAL) WorkDecision(WorkDisposition.RUN, "预览按正常分辨率渲染")
                else WorkDecision(WorkDisposition.THROTTLE, "${state.reasonText()}，预览上限降至 ${state.previewMaxDimension} 像素")
            DeviceWork.FOREGROUND_TASK, DeviceWork.DSH_TASK ->
                WorkDecision(WorkDisposition.RUN, "前台任务与 DSH 任务不暂停、不取消")
        }
    }

    /** 是否允许取消或暂停该类别；仅用于调用方自检，策略本身不会发起取消。 */
    fun mayPauseOrCancel(work: DeviceWork): Boolean = work !in PROTECTED_WORK

    private data class Hold(val condition: DeviceCondition, val holdSinceMillis: Long = 0L)
    private data class MemoryHold(val warm: Boolean, val holdSinceMillis: Long = 0L)

    private fun thermalTarget(prev: DeviceConditionState, signals: DeviceSignals, config: DeviceConditionConfig): Hold {
        val level = signals.thermalLevel
        val entered = when {
            level.ordinal >= config.criticalThermalLevel.ordinal -> DeviceCondition.CRITICAL
            level.ordinal >= config.hotThermalLevel.ordinal -> DeviceCondition.HOT
            level.ordinal >= config.warmThermalLevel.ordinal -> DeviceCondition.WARM
            else -> DeviceCondition.NORMAL
        }
        val prior = prev.thermalCondition
        if (entered.ordinal >= prior.ordinal) return Hold(entered)

        // 降级时使用更低的退出边界：CRITICAL 至少要回到中热，HOT 至少要回到轻热。
        val exited = when {
            level.ordinal >= config.hotThermalLevel.ordinal -> DeviceCondition.HOT
            level.ordinal >= config.warmThermalLevel.ordinal -> DeviceCondition.WARM
            else -> DeviceCondition.NORMAL
        }
        if (exited.ordinal >= prior.ordinal) return Hold(prior)
        val since = if (prev.thermalHoldSinceMillis > 0L) prev.thermalHoldSinceMillis else signals.nowMillis
        return if (signals.nowMillis - since >= config.thermalDowngradeDwellMillis) {
            Hold(exited)
        } else {
            Hold(prior, since)
        }
    }

    private fun memoryTarget(prev: DeviceConditionState, signals: DeviceSignals, config: DeviceConditionConfig): MemoryHold {
        if (signals.lowMemory) return MemoryHold(warm = true)
        if (!prev.memoryWarm) return MemoryHold(warm = false)
        val since = if (prev.memoryHoldSinceMillis > 0L) prev.memoryHoldSinceMillis else signals.nowMillis
        return if (signals.nowMillis - since >= config.memoryDowngradeDwellMillis) {
            MemoryHold(warm = false)
        } else {
            MemoryHold(warm = true, holdSinceMillis = since)
        }
    }

    private fun lowBatteryTarget(prev: DeviceConditionState, signals: DeviceSignals, config: DeviceConditionConfig): Boolean {
        val percent = signals.batteryPercent
        if (percent !in 0..100 || signals.charging) return false
        return if (prev.lowBatteryHeld) {
            percent < config.lowBatteryExitPercent
        } else {
            percent <= config.lowBatteryEnterPercent
        }
    }

    private fun maxThermal(left: DeviceCondition, right: DeviceCondition): DeviceCondition =
        if (left.ordinal >= right.ordinal) left else right

    private fun workerCap(thermal: DeviceCondition, lowBattery: Boolean, config: DeviceConditionConfig): Int {
        var cap = when (thermal) {
            DeviceCondition.CRITICAL -> config.criticalWorkerCap
            DeviceCondition.HOT -> config.hotWorkerCap
            DeviceCondition.WARM -> config.warmWorkerCap
            else -> config.normalWorkerCap
        }
        if (lowBattery) cap = minOf(cap, config.lowBatteryWorkerCap)
        return cap
    }

    private fun logLines(thermal: DeviceCondition, lowBattery: Boolean, config: DeviceConditionConfig): Int {
        var lines = when (thermal) {
            DeviceCondition.CRITICAL -> config.criticalLogLines
            DeviceCondition.HOT -> config.hotLogLines
            DeviceCondition.WARM -> config.warmLogLines
            else -> config.normalLogLines
        }
        if (lowBattery) lines = minOf(lines, config.lowBatteryLogLines)
        return lines
    }

    private fun previewDimension(thermal: DeviceCondition, lowBattery: Boolean, config: DeviceConditionConfig): Int {
        var dimension = when (thermal) {
            DeviceCondition.CRITICAL -> config.criticalPreviewDimension
            DeviceCondition.HOT -> config.hotPreviewDimension
            DeviceCondition.WARM -> config.warmPreviewDimension
            else -> config.normalPreviewDimension
        }
        if (lowBattery) dimension = minOf(dimension, config.lowBatteryPreviewDimension)
        return dimension
    }

    private fun previewPixels(thermal: DeviceCondition, lowBattery: Boolean, config: DeviceConditionConfig): Int {
        var pixels = when (thermal) {
            DeviceCondition.CRITICAL -> config.criticalPreviewPixels
            DeviceCondition.HOT -> config.hotPreviewPixels
            DeviceCondition.WARM -> config.warmPreviewPixels
            else -> config.normalPreviewPixels
        }
        if (lowBattery) pixels = minOf(pixels, config.lowBatteryPreviewPixels)
        return pixels
    }

    private fun workLabel(work: DeviceWork): String = when (work) {
        DeviceWork.APK_UPDATE_CHECK -> "APK 更新检查"
        DeviceWork.DSH_UPDATE_CHECK -> "DSH 更新检查"
        DeviceWork.NPM_DEPENDENCY_DOWNLOAD -> "依赖下载"
        DeviceWork.USER_DOWNLOAD -> "用户下载"
        DeviceWork.LOG_RENDER -> "日志展示"
        DeviceWork.PREVIEW_RENDER -> "预览渲染"
        DeviceWork.FOREGROUND_TASK -> "前台任务"
        DeviceWork.DSH_TASK -> "DSH 任务"
    }
}
