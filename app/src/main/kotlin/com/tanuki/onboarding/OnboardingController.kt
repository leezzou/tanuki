package com.tanuki.onboarding

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 首次启动引导的界面快照。
 *
 * [interfaceNative] 由宿主当前的界面偏好传入，避免引导层再维护一份选择状态。
 */
data class OnboardingPresentation(
    val open: Boolean,
    val step: OnboardingStep,
    val record: OnboardingRecord,
    val device: DeviceProfile,
    val interfaceNative: Boolean,
    val banner: OnboardingBanner?,
)

/**
 * 引导控制器：连接纯状态机、SharedPreferences 与系统设备信息。
 *
 * 只做薄适配，不在 Compose 里写业务判断；Activity 重建后从持久化记录恢复到
 * 第一个未完成步骤。所有展示文案与控件状态仍由纯逻辑层给出。
 */
class OnboardingController(
    context: Context,
    private val currentVersion: String,
    upgradedFromPreviousInstall: Boolean = false,
) {
    private val appContext = context.applicationContext
    private val store = OnboardingStore(appContext)
    private val loaded = store.load()
    private val seededForUpgrade = upgradedFromPreviousInstall && !loaded.started
    private val stored = if (seededForUpgrade) {
        OnboardingMachine.completedForExistingInstall(currentVersion)
    } else {
        loaded
    }

    /** 本次冷启动的处置；只在构造时计算一次，之后由用户操作改变页面。 */
    val launch: OnboardingLaunch = OnboardingGate.decide(stored, currentVersion)

    /**
     * 首次启动或上次未完成时直接以展开状态创建，避免先闪一帧首页。
     * 跳过与升级提示保持关闭，由首页提示条承接。
     */
    var state by mutableStateOf(
        when (launch) {
            OnboardingLaunch.FIRST_RUN,
            OnboardingLaunch.RESUME,
            -> OnboardingMachine.start(stored, currentVersion)
            OnboardingLaunch.BANNER,
            OnboardingLaunch.UPGRADE_NOTICE,
            OnboardingLaunch.NONE,
            -> OnboardingMachine.initial(stored)
        }
    )
        private set

    var device by mutableStateOf(OnboardingDevice.capture(appContext))
        private set

    init {
        // 首次打开即记录“已看过”，进程被杀后下次从第一个未完成步骤继续；
        // 升级安装只写入完成记录，不展开整套引导。
        if (state.open || seededForUpgrade) runCatching { store.save(state.record) }
    }

    /** 从首页提示条或设置进入；已完成记录从欢迎页开始回顾。 */
    fun open(step: OnboardingStep? = null) {
        refreshDevice()
        update(OnboardingMachine.start(state.record, currentVersion, step))
    }

    fun advance() {
        update(OnboardingMachine.advance(state, currentVersion))
    }

    fun back() {
        update(OnboardingMachine.back(state, currentVersion))
    }

    fun skip() {
        update(OnboardingMachine.skip(state, currentVersion))
    }

    fun complete() {
        update(OnboardingMachine.complete(state, currentVersion))
    }

    fun dismissBanner() {
        update(state.copy(record = OnboardingMachine.dismissBanner(state.record)))
    }

    fun dismissUpgradeNotice() {
        update(state.copy(record = OnboardingMachine.dismissUpgradeNotice(state.record, currentVersion)))
    }

    /** 重新读取可用空间；清理存储后不必重启应用即可看到新结果。 */
    fun refreshDevice() {
        device = OnboardingDevice.capture(appContext)
    }

    fun presentation(interfaceNative: Boolean): OnboardingPresentation = OnboardingPresentation(
        open = state.open,
        step = state.step,
        record = state.record,
        device = device,
        interfaceNative = interfaceNative,
        banner = OnboardingMachine.banner(state.record, currentVersion),
    )

    private fun update(next: OnboardingUiState) {
        state = next
        runCatching { store.save(next.record) }
    }
}
