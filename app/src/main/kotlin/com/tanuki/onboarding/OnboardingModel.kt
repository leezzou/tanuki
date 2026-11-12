package com.tanuki.onboarding

/**
 * 首次引导沿用的四个持久化步骤值。
 *
 * 当前界面将 ENVIRONMENT、INTERFACE、DONE 统一映射到准备页，保留旧值以恢复已有记录。
 * 状态机只描述引导记录，不代表实际运行环境完成；运行就绪由组件与服务快照判断。
 */
enum class OnboardingStep(val storageValue: String) {
    WELCOME("welcome"),
    ENVIRONMENT("environment"),
    INTERFACE("interface"),
    DONE("done");

    val next: OnboardingStep? get() = entries.getOrNull(ordinal + 1)

    val isLast: Boolean get() = ordinal == entries.lastIndex

    companion object {
        val ordered: List<OnboardingStep> = entries.toList()

        /** 未知或损坏的持久化值返回 null，由调用方回到第一步，避免卡在空白页。 */
        fun parse(raw: String?): OnboardingStep? = entries.firstOrNull { it.storageValue == raw }
    }
}

/** 引导完成后首页的动作入口；只用于界面跳转，不影响引导记录。 */
enum class OnboardingFinish { FIRST_MESSAGE, REMOTE, HOME }

/**
 * 本地持久化的引导记录。
 *
 * [completedVersion] 非空且所有步骤完成才算真正结束；[lastSeenVersion] 在第一次打开
 * 引导时写入，用来区分“从未启动”和“启动过但没做完”。大版本升级只更新
 * [upgradeNoticeSeenVersion]，不会把已完成记录重新变成未完成。
 */
data class OnboardingRecord(
    val lastSeenVersion: String = "",
    val completedVersion: String = "",
    val completedSteps: Set<OnboardingStep> = emptySet(),
    val skipped: Boolean = false,
    val skippedAt: OnboardingStep? = null,
    val bannerDismissed: Boolean = false,
    val upgradeNoticeSeenVersion: String = "",
) {
    val finished: Boolean
        get() = completedVersion.isNotBlank() &&
            OnboardingStep.entries.all { it in completedSteps }

    /** 曾经打开或操作过引导；空记录代表真正的首次启动。 */
    val started: Boolean
        get() = lastSeenVersion.isNotBlank() || completedSteps.isNotEmpty() || skipped

    /** 继续时应该回到的步骤；全部完成时返回 null。 */
    val firstIncomplete: OnboardingStep?
        get() = OnboardingStep.entries.firstOrNull { it !in completedSteps }
}

/** 冷启动时对引导的处置；只有首次启动和未完成的首次流程会自动展开。 */
enum class OnboardingLaunch {
    /** 从未打开过引导，直接进入第一步。 */
    FIRST_RUN,

    /** 首次流程被系统中断，回到第一个未完成步骤。 */
    RESUME,

    /** 用户主动跳过，只在首页保留可关闭提示条。 */
    BANNER,

    /** 大版本升级后的轻提示，不自动展开整套引导。 */
    UPGRADE_NOTICE,

    /** 已完成或无需提示。 */
    NONE,
}

/** 首页提示条的类型；升级提示只引导查看，不代表重新完成设置。 */
enum class OnboardingBannerKind { RESUME, UPGRADE }

/**
 * 首页提示条所需的最小信息。
 *
 * 文案由界面层按 [kind] 渲染，这里只保留可测试的进度与目标步骤。
 */
data class OnboardingBanner(
    val kind: OnboardingBannerKind,
    val step: OnboardingStep?,
    val completedSteps: Int,
    val totalSteps: Int,
)

/** 可持久化状态 + 当前是否展开；界面只读取这个对象。 */
data class OnboardingUiState(
    val record: OnboardingRecord = OnboardingRecord(),
    val open: Boolean = false,
    val step: OnboardingStep = OnboardingStep.WELCOME,
)

/** 版本门控与提示条判断；纯函数，方便覆盖升级/回退/损坏版本号。 */
object OnboardingGate {
    fun decide(record: OnboardingRecord, currentVersion: String): OnboardingLaunch = when {
        !record.started -> OnboardingLaunch.FIRST_RUN
        !record.finished && record.skipped -> OnboardingLaunch.BANNER
        !record.finished -> OnboardingLaunch.RESUME
        majorUpgrade(record, currentVersion) -> OnboardingLaunch.UPGRADE_NOTICE
        else -> OnboardingLaunch.NONE
    }

    /**
     * 大版本提示只在产品主线变化时出现一次。
     *
     * 当前版本号是 0.x：把“0.1 → 0.2”视为一个新的大版本，0.1.12 → 0.1.13 不提示；
     * 进入 1.x 后只比较主版本号。版本号无法解析时按“不升级”处理，避免开发版本
     * 或异常字符串反复提示。
     */
    fun majorUpgrade(record: OnboardingRecord, currentVersion: String): Boolean {
        if (!record.finished) return false
        val current = releaseLine(currentVersion) ?: return false
        val seen = releaseLine(record.lastSeenVersion) ?: return false
        return (current.first > seen.first ||
            (current.first == 0 && seen.first == 0 && current.second > seen.second)) &&
            record.upgradeNoticeSeenVersion != currentVersion
    }

    /** 0.x 返回 (0, 次版本号)，1.x 起返回 (主版本号, 0)。 */
    internal fun releaseLine(version: String): Pair<Int, Int>? {
        val parts = version.trim().split('.')
        val major = parts.firstOrNull()?.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        if (major > 0) return major to 0
        val minor = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return major to minor
    }
}

/** 引导状态迁移；不持有存储和线程，调用方负责落盘。 */
object OnboardingMachine {
    /**
     * 升级安装的默认记录。
     *
     * 已经用过旧版本的用户不是新用户：把当前版本直接标记为完成，避免更新后
     * 被整套引导拦住；需要时仍可从设置重新打开。真正的首次安装不会走这里。
     */
    fun completedForExistingInstall(currentVersion: String): OnboardingRecord = OnboardingRecord(
        lastSeenVersion = currentVersion,
        completedVersion = currentVersion,
        completedSteps = OnboardingStep.entries.toSet(),
    )

    fun initial(record: OnboardingRecord): OnboardingUiState = OnboardingUiState(record = record)

    /**
     * 打开引导。
     *
     * [requested] 用于从提示条精确回到未完成步骤；已完成记录再次打开时从欢迎页
     * 开始回顾，不撤销完成状态。
     */
    fun start(
        record: OnboardingRecord,
        currentVersion: String,
        requested: OnboardingStep? = null,
    ): OnboardingUiState {
        val step = requested
            ?: if (record.finished) OnboardingStep.WELCOME else record.firstIncomplete
            ?: OnboardingStep.WELCOME
        val updated = record.copy(lastSeenVersion = currentVersion, bannerDismissed = false)
        return OnboardingUiState(record = updated, open = true, step = step)
    }

    /** 标记当前步骤完成并前进；最后一步继续即视为完成整套设置。 */
    fun advance(state: OnboardingUiState, currentVersion: String): OnboardingUiState {
        val completed = state.record.completedSteps + state.step
        val next = state.step.next
        if (next == null) return complete(state, currentVersion)
        return state.copy(
            record = state.record.copy(
                completedSteps = completed,
                lastSeenVersion = currentVersion,
                skipped = false,
                skippedAt = null,
            ),
            step = next,
        )
    }

    /** 返回上一步；欢迎页返回等同于跳过，避免把用户困在引导里。 */
    fun back(state: OnboardingUiState, currentVersion: String): OnboardingUiState {
        // 旧版本的界面选择/完成记录现在都映射到准备页，返回时不经过不可见的旧步骤。
        val previous = if (state.step in setOf(OnboardingStep.INTERFACE, OnboardingStep.DONE)) OnboardingStep.WELCOME
            else OnboardingStep.entries.getOrNull(state.step.ordinal - 1)
                ?: return skip(state, currentVersion)
        return state.copy(step = previous)
    }

    /**
     * 跳过整套引导。
     *
     * 已完成的步骤保留，下次从第一个未完成步骤继续；[bannerDismissed] 重置，
     * 让首页立即出现“完成设置”提示条。已完成的记录只关闭页面，不回退完成状态。
     */
    fun skip(state: OnboardingUiState, currentVersion: String): OnboardingUiState {
        val record = state.record.copy(
            lastSeenVersion = currentVersion,
            skipped = true,
            skippedAt = state.step,
            bannerDismissed = false,
        )
        return state.copy(record = record, open = false)
    }

    /** 完成整套设置；完成版本用于区分后续大版本提示。 */
    fun complete(state: OnboardingUiState, currentVersion: String): OnboardingUiState {
        val record = state.record.copy(
            lastSeenVersion = currentVersion,
            completedVersion = currentVersion,
            completedSteps = OnboardingStep.entries.toSet(),
            skipped = false,
            skippedAt = null,
            bannerDismissed = false,
        )
        return OnboardingUiState(record = record, open = false, step = OnboardingStep.DONE)
    }

    /** 关闭页面但不写入完成；界面返回键与“稍后再说”统一走这里。 */
    fun close(state: OnboardingUiState, currentVersion: String): OnboardingUiState =
        skip(state, currentVersion)

    fun dismissBanner(record: OnboardingRecord): OnboardingRecord =
        record.copy(bannerDismissed = true)

    /** 关闭升级提示并记录版本，后续冷启动不再重复。 */
    fun dismissUpgradeNotice(record: OnboardingRecord, currentVersion: String): OnboardingRecord =
        record.copy(
            upgradeNoticeSeenVersion = currentVersion,
            lastSeenVersion = currentVersion,
            bannerDismissed = true,
        )

    /**
     * 首页提示条。
     *
     * 跳过后的完成提示优先；已完成记录只在大版本升级时出现一次轻提示。
     */
    fun banner(record: OnboardingRecord, currentVersion: String): OnboardingBanner? {
        if (record.bannerDismissed) return null
        if (!record.finished && record.skipped) return OnboardingBanner(
            kind = OnboardingBannerKind.RESUME,
            step = record.firstIncomplete ?: OnboardingStep.WELCOME,
            completedSteps = record.completedSteps.size,
            totalSteps = OnboardingStep.entries.size,
        )
        if (OnboardingGate.majorUpgrade(record, currentVersion)) return OnboardingBanner(
            kind = OnboardingBannerKind.UPGRADE,
            step = null,
            completedSteps = record.completedSteps.size,
            totalSteps = OnboardingStep.entries.size,
        )
        return null
    }
}
