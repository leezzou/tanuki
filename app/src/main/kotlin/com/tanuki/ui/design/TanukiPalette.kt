package com.tanuki.ui.design

/**
 * 共享明度阶梯：中性色和四类语义色都从同一组 L 值推导。
 *
 * 色阶索引越大越深。浅色主题从浅端取画布、从深端取正文；深色主题反向取用。
 * 这样中性色、成功、警告、危险、信息在任一档上的感知明度一致，替换色相不会
 * 改变版面的轻重关系。
 */
internal object TanukiToneLadder {
    val values: List<Double> = listOf(
        0.995, 0.975, 0.945, 0.905, 0.855, 0.775, 0.665,
        0.555, 0.455, 0.365, 0.285, 0.215, 0.165, 0.115,
    )

    val indices: IntRange = values.indices

    fun l(stop: Int): Double {
        require(stop in indices) { "色阶档位越界：$stop" }
        return values[stop]
    }
}

/** 五组色相；中性色只保留极低彩度，语义色使用固定色相。 */
internal enum class TanukiColorFamily(
    val hueDegrees: Double,
    val chroma: Double,
) {
    Neutral(110.0, 0.008),
    Success(150.0, 0.11),
    Warning(75.0, 0.13),
    Danger(27.0, 0.15),
    Info(245.0, 0.12),
}

/** 某一色相在完整明度阶梯上的 sRGB 结果。 */
internal data class TanukiColorScale(
    val family: TanukiColorFamily,
    val tones: List<TanukiRgb>,
) {
    init {
        require(tones.size == TanukiToneLadder.values.size) {
            "色阶必须覆盖全部明度档位：${family.name}"
        }
    }

    operator fun get(stop: Int): TanukiRgb = tones[stop]
}

/** 由五组色相组成的完整色板；浅色和深色主题共用同一份色板。 */
internal data class TanukiTonePalette(
    val scales: Map<TanukiColorFamily, TanukiColorScale>,
) {
    operator fun get(family: TanukiColorFamily): TanukiColorScale =
        scales.getValue(family)

    companion object {
        /**
         * 只生成一次。生成过程是纯计算，不读取系统主题、资源或设备状态，
         * 因此 JVM 测试与运行时得到完全相同的色值。
         */
        fun standard(): TanukiTonePalette {
            val scales = TanukiColorFamily.entries.associateWith { family ->
                val tones = TanukiToneLadder.values.map { lightness ->
                    TanukiColorMath.oklchToRgb(lightness, family.chroma, family.hueDegrees)
                }
                TanukiColorScale(family, tones)
            }
            return TanukiTonePalette(scales)
        }
    }
}

/** 状态语义；界面组件只认这五个语义，不直接依赖具体色相。 */
internal enum class TanukiStatusTone {
    Neutral,
    Success,
    Warning,
    Danger,
    Info,
}

/** 状态点、状态 chip 和状态文字共用的三枚颜色。 */
internal data class TanukiStatusRoleColors(
    val dot: Long,
    val container: Long,
    val content: Long,
)

/**
 * 一套主题下的语义颜色令牌。
 *
 * 字段全部是 0xAARRGGBB，方便纯 JVM 审计；Compose 层通过 `Color(value)` 转换。
 * `chromeBase` 是不透明底色，玻璃层再叠加 `TanukiElevation` 中的透明度。
 */
internal data class TanukiColorTokens(
    val isDark: Boolean,
    val canvas: Long,
    val surface: Long,
    val surfaceRaised: Long,
    val surfaceSunken: Long,
    val surfaceMuted: Long,
    val sheet: Long,
    val chromeBase: Long,
    val scrim: Long,
    val textPrimary: Long,
    val textSecondary: Long,
    val textTertiary: Long,
    val textDisabled: Long,
    val outline: Long,
    val outlineStrong: Long,
    val hairline: Long,
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val success: Long,
    val onSuccess: Long,
    val successContainer: Long,
    val onSuccessContainer: Long,
    val warning: Long,
    val onWarning: Long,
    val warningContainer: Long,
    val onWarningContainer: Long,
    val danger: Long,
    val onDanger: Long,
    val dangerContainer: Long,
    val onDangerContainer: Long,
    val info: Long,
    val onInfo: Long,
    val infoContainer: Long,
    val onInfoContainer: Long,
    val neutralStatus: Long,
    val onNeutralStatus: Long,
    val neutralStatusContainer: Long,
    val onNeutralStatusContainer: Long,
) {
    fun statusColors(tone: TanukiStatusTone): TanukiStatusRoleColors = when (tone) {
        TanukiStatusTone.Neutral -> TanukiStatusRoleColors(
            dot = neutralStatus,
            container = neutralStatusContainer,
            content = onNeutralStatusContainer,
        )
        TanukiStatusTone.Success -> TanukiStatusRoleColors(success, successContainer, onSuccessContainer)
        TanukiStatusTone.Warning -> TanukiStatusRoleColors(warning, warningContainer, onWarningContainer)
        TanukiStatusTone.Danger -> TanukiStatusRoleColors(danger, dangerContainer, onDangerContainer)
        TanukiStatusTone.Info -> TanukiStatusRoleColors(info, infoContainer, onInfoContainer)
    }

    companion object {
        private val palette: TanukiTonePalette by lazy { TanukiTonePalette.standard() }

        private fun neutral(stop: Int): Long = palette[TanukiColorFamily.Neutral][stop].toArgb()
        private fun success(stop: Int): Long = palette[TanukiColorFamily.Success][stop].toArgb()
        private fun warning(stop: Int): Long = palette[TanukiColorFamily.Warning][stop].toArgb()
        private fun danger(stop: Int): Long = palette[TanukiColorFamily.Danger][stop].toArgb()
        private fun info(stop: Int): Long = palette[TanukiColorFamily.Info][stop].toArgb()

        /**
         * 浅色主题：近白画布、深石墨正文。
         *
         * 正文主色取 12 档，次级 9 档、三级 8 档，均高于 4.5:1；7 档留给
         * 停用态或大号图标。语义实心色统一取 8 档并配近白前景，四类均高于 4.5:1。
         */
        val Light: TanukiColorTokens = TanukiColorTokens(
            isDark = false,
            canvas = neutral(1),
            surface = neutral(0),
            surfaceRaised = neutral(0),
            surfaceSunken = neutral(2),
            surfaceMuted = neutral(2),
            sheet = neutral(0),
            chromeBase = neutral(0),
            scrim = TanukiRgb(0, 0, 0).toArgb(alpha = 0x66),
            textPrimary = neutral(12),
            textSecondary = neutral(9),
            textTertiary = neutral(8),
            textDisabled = neutral(7),
            outline = neutral(5),
            outlineStrong = neutral(7),
            hairline = neutral(3),
            primary = neutral(11),
            onPrimary = neutral(0),
            primaryContainer = neutral(2),
            onPrimaryContainer = neutral(12),
            success = success(8),
            onSuccess = neutral(0),
            successContainer = success(2),
            onSuccessContainer = neutral(12),
            warning = warning(8),
            onWarning = neutral(0),
            warningContainer = warning(2),
            onWarningContainer = neutral(12),
            danger = danger(8),
            onDanger = neutral(0),
            dangerContainer = danger(2),
            onDangerContainer = neutral(12),
            info = info(8),
            onInfo = neutral(0),
            infoContainer = info(2),
            onInfoContainer = neutral(12),
            neutralStatus = neutral(8),
            onNeutralStatus = neutral(0),
            neutralStatusContainer = neutral(2),
            onNeutralStatusContainer = neutral(12),
        )

        /**
         * 深色主题：近黑画布、近白正文。
         *
         * 正文主色取 0 档，次级 5 档、三级 6 档；语义实心色取 6 档并配近黑
         * 前景，四类均高于 4.5:1。语义容器取 10 档，容器正文取 1 档。
         */
        val Dark: TanukiColorTokens = TanukiColorTokens(
            isDark = true,
            canvas = neutral(13),
            surface = neutral(12),
            surfaceRaised = neutral(11),
            surfaceSunken = neutral(13),
            surfaceMuted = neutral(11),
            sheet = neutral(11),
            chromeBase = neutral(12),
            scrim = TanukiRgb(0, 0, 0).toArgb(alpha = 0x99),
            textPrimary = neutral(0),
            textSecondary = neutral(5),
            textTertiary = neutral(6),
            textDisabled = neutral(7),
            outline = neutral(9),
            outlineStrong = neutral(7),
            hairline = neutral(10),
            primary = neutral(2),
            onPrimary = neutral(12),
            primaryContainer = neutral(9),
            onPrimaryContainer = neutral(1),
            success = success(6),
            onSuccess = neutral(13),
            successContainer = success(10),
            onSuccessContainer = success(1),
            warning = warning(6),
            onWarning = neutral(13),
            warningContainer = warning(10),
            onWarningContainer = warning(1),
            danger = danger(6),
            onDanger = neutral(13),
            dangerContainer = danger(10),
            onDangerContainer = danger(1),
            info = info(6),
            onInfo = neutral(13),
            infoContainer = info(10),
            onInfoContainer = info(1),
            neutralStatus = neutral(6),
            onNeutralStatus = neutral(13),
            neutralStatusContainer = neutral(10),
            onNeutralStatusContainer = neutral(1),
        )
    }
}

/** 对比度审计项；`minimum` 由文字层级决定。 */
internal data class TanukiContrastCheck(
    val name: String,
    val foreground: Long,
    val background: Long,
    val minimum: Double,
) {
    val actualRatio: Double
        get() = TanukiColorMath.contrastRatio(foreground, background)

    val passes: Boolean
        get() = actualRatio + 1e-9 >= minimum
}

/**
 * 设计令牌的对比度审计。
 *
 * 正文（含次级、三级文字和容器正文）要求 ≥4.5:1；停用文字、图标和状态点
 * 只要求 ≥3.0:1。玻璃 chrome 按 0.86 不透明度压在画布上后再检查正文。
 */
internal object TanukiContrastAudit {
    const val BODY_MINIMUM: Double = 4.5
    const val LARGE_MINIMUM: Double = 3.0

    fun checks(tokens: TanukiColorTokens): List<TanukiContrastCheck> {
        val result = mutableListOf<TanukiContrastCheck>()
        fun body(name: String, foreground: Long, background: Long) {
            result += TanukiContrastCheck(name, foreground, background, BODY_MINIMUM)
        }
        fun large(name: String, foreground: Long, background: Long) {
            result += TanukiContrastCheck(name, foreground, background, LARGE_MINIMUM)
        }

        body("正文/画布", tokens.textPrimary, tokens.canvas)
        body("正文/表面", tokens.textPrimary, tokens.surface)
        body("正文/抬升表面", tokens.textPrimary, tokens.surfaceRaised)
        body("正文/弹层", tokens.textPrimary, tokens.sheet)
        body("次级正文/画布", tokens.textSecondary, tokens.canvas)
        body("次级正文/表面", tokens.textSecondary, tokens.surface)
        body("次级正文/凹陷表面", tokens.textSecondary, tokens.surfaceMuted)
        body("三级正文/画布", tokens.textTertiary, tokens.canvas)
        body("三级正文/表面", tokens.textTertiary, tokens.surface)
        body("三级正文/凹陷表面", tokens.textTertiary, tokens.surfaceMuted)
        large("停用正文/画布", tokens.textDisabled, tokens.canvas)
        large("停用正文/表面", tokens.textDisabled, tokens.surface)
        large("强边框图标/表面", tokens.outlineStrong, tokens.surface)
        large("状态点/画布", tokens.neutralStatus, tokens.canvas)

        val chromeOverCanvas = TanukiColorMath.composite(
            foreground = tokens.chromeBase,
            alpha = TanukiElevation.of(TanukiSurfaceLayer.Chrome).fillAlpha,
            background = tokens.canvas,
        )
        body("玻璃 chrome 正文/画布", tokens.textPrimary, chromeOverCanvas)
        body("玻璃 chrome 次级正文/画布", tokens.textSecondary, chromeOverCanvas)

        body("主操作正文/主色", tokens.onPrimary, tokens.primary)
        body("主容器正文/主容器", tokens.onPrimaryContainer, tokens.primaryContainer)
        body("成功正文/成功色", tokens.onSuccess, tokens.success)
        body("成功容器正文/成功容器", tokens.onSuccessContainer, tokens.successContainer)
        body("警告正文/警告色", tokens.onWarning, tokens.warning)
        body("警告容器正文/警告容器", tokens.onWarningContainer, tokens.warningContainer)
        body("危险正文/危险色", tokens.onDanger, tokens.danger)
        body("危险容器正文/危险容器", tokens.onDangerContainer, tokens.dangerContainer)
        body("信息正文/信息色", tokens.onInfo, tokens.info)
        body("信息容器正文/信息容器", tokens.onInfoContainer, tokens.infoContainer)
        body("中性状态正文/中性状态色", tokens.onNeutralStatus, tokens.neutralStatus)
        body("中性容器正文/中性容器", tokens.onNeutralStatusContainer, tokens.neutralStatusContainer)
        return result
    }

    fun failures(tokens: TanukiColorTokens): List<TanukiContrastCheck> =
        checks(tokens).filterNot { it.passes }
}
