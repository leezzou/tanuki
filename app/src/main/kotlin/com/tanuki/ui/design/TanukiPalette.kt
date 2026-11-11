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
    Neutral(0.0, 0.0),
    Success(150.0, 0.11),
    Warning(75.0, 0.13),
    Danger(27.0, 0.15),
    Info(271.0, 0.20),
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
    /** 弱灰仅用于停用、占位和装饰；可读说明使用 textTertiary。 */
    val textDisabled: Long,
    val outline: Long,
    val outlineStrong: Long,
    val hairline: Long,
    /** 可读强调文字；浅色深靛蓝、深色浅靛蓝，普通文字必须达到 4.5:1。 */
    val primary: Long,
    /** 强调靛蓝用于选中、进度、状态点和细描边；正文与白字背景使用独立角色。 */
    val accent: Long,
    /** 图标需达到 3:1；按明暗主题选择靛蓝，保证信息图标可辨认。 */
    val accentIcon: Long,
    /** 白字主按钮独立于主题文字色；两种主题均用深靛蓝。 */
    val primaryFill: Long,
    val primaryPressed: Long,
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
    /** 白底说明色在分组浅底不足 4.5:1 时，使用可读的次级文字色。 */
    fun descriptionOn(background: Long): Long =
        if (TanukiColorMath.contrastRatio(textTertiary, background) >= TanukiContrastAudit.BODY_MINIMUM) {
            textTertiary
        } else {
            textSecondary
        }

    fun statusColors(tone: TanukiStatusTone): TanukiStatusRoleColors = when (tone) {
        TanukiStatusTone.Neutral -> TanukiStatusRoleColors(
            dot = neutralStatus,
            container = neutralStatusContainer,
            content = onNeutralStatusContainer,
        )
        TanukiStatusTone.Success -> TanukiStatusRoleColors(success, successContainer, onSuccessContainer)
        TanukiStatusTone.Warning -> TanukiStatusRoleColors(warning, warningContainer, onWarningContainer)
        TanukiStatusTone.Danger -> TanukiStatusRoleColors(danger, dangerContainer, onDangerContainer)
        TanukiStatusTone.Info -> TanukiStatusRoleColors(accent, infoContainer, onInfoContainer)
    }

    companion object {
        /** 浅色主题使用纯白/中性灰，靛蓝只强调操作与状态。 */
        val Light: TanukiColorTokens = TanukiColorTokens(
            isDark = false,
            canvas = 0xFFFFFFFF,
            surface = 0xFFFFFFFF,
            surfaceRaised = 0xFFFFFFFF,
            surfaceSunken = 0xFFF5F6F7,
            surfaceMuted = 0xFFF5F6F7,
            sheet = 0xFFFFFFFF,
            chromeBase = 0xFFFFFFFF,
            scrim = 0x66111315,
            textPrimary = 0xFF111315,
            textSecondary = 0xFF5A6068,
            textTertiary = 0xFF6B7280,
            textDisabled = 0xFF8C929A,
            outline = 0xFFECEDEF,
            outlineStrong = 0xFFECEDEF,
            hairline = 0xFFECEDEF,
            primary = 0xFF3D56D6,
            accent = 0xFF4D6BFE,
            accentIcon = 0xFF3D56D6,
            primaryFill = 0xFF3D56D6,
            primaryPressed = 0xFF3348B8,
            onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFEEF0FF,
            onPrimaryContainer = 0xFF3D56D6,
            // 成功/警告实色仅用于语义图形；深色前景保证实色标签的文字对比度。
            success = 0xFF16A34A,
            onSuccess = 0xFF052E16,
            successContainer = 0xFFF0FDF4,
            onSuccessContainer = 0xFF166534,
            warning = 0xFFD97706,
            onWarning = 0xFF111315,
            warningContainer = 0xFFFFFBEB,
            onWarningContainer = 0xFF92400E,
            danger = 0xFFDC2626,
            onDanger = 0xFFFFFFFF,
            dangerContainer = 0xFFFEF2F2,
            onDangerContainer = 0xFFB91C1C,
            info = 0xFF3D56D6,
            onInfo = 0xFFFFFFFF,
            infoContainer = 0xFFEEF0FF,
            onInfoContainer = 0xFF3D56D6,
            neutralStatus = 0xFF5A6068,
            onNeutralStatus = 0xFFFFFFFF,
            neutralStatusContainer = 0xFFF5F6F7,
            onNeutralStatusContainer = 0xFF5A6068,
        )

        /** 深色主题单独取明度，不复用浅色语义色；卡片不出现白底、画布不取纯黑。 */
        val Dark: TanukiColorTokens = TanukiColorTokens(
            isDark = true,
            canvas = 0xFF0E1012,
            surface = 0xFF1A1D21,
            surfaceRaised = 0xFF1A1D21,
            surfaceSunken = 0xFF16181B,
            surfaceMuted = 0xFF16181B,
            sheet = 0xFF1A1D21,
            chromeBase = 0xFF16181B,
            scrim = 0x990E1012,
            textPrimary = 0xFFF2F4F6,
            textSecondary = 0xFFA8AFB8,
            textTertiary = 0xFF9AA1AA,
            textDisabled = 0xFF6F767F,
            outline = 0xFF24282D,
            outlineStrong = 0xFF24282D,
            hairline = 0xFF24282D,
            primary = 0xFFA6B3FF,
            accent = 0xFF899BFF,
            accentIcon = 0xFF899BFF,
            primaryFill = 0xFF3D56D6,
            primaryPressed = 0xFF3348B8,
            onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFF232B50,
            onPrimaryContainer = 0xFFA6B3FF,
            success = 0xFF4ADE80,
            onSuccess = 0xFF052E16,
            successContainer = 0xFF102A1B,
            onSuccessContainer = 0xFF86EFAC,
            warning = 0xFFFBBF24,
            onWarning = 0xFF111315,
            warningContainer = 0xFF30230E,
            onWarningContainer = 0xFFFCD34D,
            danger = 0xFFF87171,
            onDanger = 0xFF450A0A,
            dangerContainer = 0xFF32181B,
            onDangerContainer = 0xFFFCA5A5,
            info = 0xFFA6B3FF,
            onInfo = 0xFF0E1012,
            infoContainer = 0xFF232B50,
            onInfoContainer = 0xFFA6B3FF,
            neutralStatus = 0xFFA8AFB8,
            onNeutralStatus = 0xFF0E1012,
            neutralStatusContainer = 0xFF16181B,
            onNeutralStatusContainer = 0xFFA8AFB8,
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
 * 用途审计：正文、链接、按钮与标签 ≥4.5:1，大字及有信息含义的图标 ≥3:1。
 * 弱灰停用内容及细分隔线不作为可读文字或图标；强调色需通过非文字阈值。
 */
internal object TanukiContrastAudit {
    const val BODY_MINIMUM: Double = 4.5
    const val LARGE_MINIMUM: Double = 3.0

    fun checks(tokens: TanukiColorTokens): List<TanukiContrastCheck> = buildList {
        fun body(name: String, fg: Long, bg: Long) {
            add(TanukiContrastCheck(name, fg, bg, BODY_MINIMUM))
        }
        val surfaces = linkedMapOf(
            "画布" to tokens.canvas, "卡片" to tokens.surface,
            "分组" to tokens.surfaceMuted, "弹层" to tokens.sheet,
            "选中容器" to tokens.primaryContainer,
        )
        for ((name, bg) in surfaces) {
            body("正文/$name", tokens.textPrimary, bg)
            body("次级正文/$name", tokens.textSecondary, bg)
            body("说明/$name", tokens.descriptionOn(bg), bg)
            body("链接/$name", tokens.primary, bg)
            add(TanukiContrastCheck("图标/$name", tokens.accentIcon, bg, LARGE_MINIMUM))
            add(TanukiContrastCheck("强调与进度/$name", tokens.accent, bg, LARGE_MINIMUM))
            add(TanukiContrastCheck("焦点/$name", tokens.primary, bg, LARGE_MINIMUM))
            add(TanukiContrastCheck("大字/$name", tokens.textPrimary, bg, LARGE_MINIMUM))
        }
        val chrome = TanukiColorMath.composite(tokens.chromeBase, TanukiElevation.chrome.fillAlpha, tokens.canvas)
        body("浮层正文", tokens.textPrimary, chrome)
        body("浮层说明", tokens.textTertiary, chrome)
        body("主按钮", tokens.onPrimary, tokens.primaryFill)
        body("主按钮按下", tokens.onPrimary, tokens.primaryPressed)
        body("强调容器", tokens.onPrimaryContainer, tokens.primaryContainer)
        for ((tone, colors) in TanukiStatusTone.entries.map { it to tokens.statusColors(it) }) {
            body("$tone 状态标签", colors.content, colors.container)
        }
        body("成功实色标签", tokens.onSuccess, tokens.success)
        body("警告实色标签", tokens.onWarning, tokens.warning)
        body("危险实色标签", tokens.onDanger, tokens.danger)
        body("信息实色标签", tokens.onInfo, tokens.info)
        body("中性实色标签", tokens.onNeutralStatus, tokens.neutralStatus)
    }

    fun failures(tokens: TanukiColorTokens): List<TanukiContrastCheck> = checks(tokens).filterNot { it.passes }
}
