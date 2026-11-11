package com.tanuki.ui.design

/**
 * 间距以 4dp 为基准；所有可点击控件还必须满足 48dp 最小触控区。
 *
 * 这里保存纯 Int，Compose 层再转成 Dp，因此 JVM 测试可以断言阶梯和下限。
 */
internal object TanukiSpace {
    const val BASE_DP: Int = 4
    const val TOUCH_TARGET_MIN_DP: Int = 48
    const val LIST_ROW_MIN_DP: Int = 56

    const val NONE_DP: Int = 0
    const val XS_DP: Int = 4
    const val SM_DP: Int = 8
    const val MD_DP: Int = 12
    const val LG_DP: Int = 16
    const val XL_DP: Int = 20
    const val XXL_DP: Int = 24
    const val XXXL_DP: Int = 32
    const val HUGE_DP: Int = 40
    const val GIANT_DP: Int = 48

    /** 命名到 dp 的稳定顺序，供文档和测试逐项核对。 */
    val values: Map<String, Int> = linkedMapOf(
        "none" to NONE_DP,
        "xs" to XS_DP,
        "sm" to SM_DP,
        "md" to MD_DP,
        "lg" to LG_DP,
        "xl" to XL_DP,
        "xxl" to XXL_DP,
        "xxxl" to XXXL_DP,
        "huge" to HUGE_DP,
        "giant" to GIANT_DP,
    )

    fun dp(name: String): Int = values.getValue(name)
}

/**
 * 圆角阶梯：胶囊、卡片、弹层分别有明确上限。
 *
 * 胶囊用 50% 形成真正的两端半圆；卡片 20dp、弹层 28dp，数值随层级递增。
 */
internal object TanukiRadius {
    const val SMALL_DP: Int = 8
    const val CONTROL_DP: Int = 12
    const val CARD_DP: Int = 20
    const val SHEET_DP: Int = 28
    const val CHROME_DP: Int = 28
    const val PILL_PERCENT: Int = 50

    val values: Map<String, Int> = linkedMapOf(
        "small" to SMALL_DP,
        "control" to CONTROL_DP,
        "card" to CARD_DP,
        "sheet" to SHEET_DP,
        "chrome" to CHROME_DP,
    )
}

/** 字体族只允许系统字体和等宽字体；等宽字体只能用于代码与路径。 */
internal enum class TanukiFontToken {
    System,
    Monospace,
}

/** 四档文字角色：标题、正文、说明、代码。 */
internal enum class TanukiTypeRole {
    Title,
    Body,
    Caption,
    Code,
}

internal enum class TanukiTypeSize {
    Large,
    Medium,
    Small,
}

/** 一档排版令牌；weight 使用 400/500/600/700 的纯 Int。 */
internal data class TanukiTypeToken(
    val role: TanukiTypeRole,
    val size: TanukiTypeSize,
    val fontSizeSp: Int,
    val lineHeightSp: Int,
    val weight: Int,
    val trackingEm: Double,
    val family: TanukiFontToken,
)

/**
 * 字体阶梯：标题 3 档、正文 3 档、说明 3 档、代码 3 档。
 *
 * 中文正文跟随系统字体，不打包任何字体文件；Monospace 只出现在代码/路径档，
 * 避免等宽字体被误用到普通说明或标题。
 */
internal object TanukiTypeScale {
    val tokens: List<TanukiTypeToken> = listOf(
        TanukiTypeToken(TanukiTypeRole.Title, TanukiTypeSize.Large, 28, 36, 600, -0.020, TanukiFontToken.System),
        TanukiTypeToken(TanukiTypeRole.Title, TanukiTypeSize.Medium, 22, 30, 600, -0.015, TanukiFontToken.System),
        TanukiTypeToken(TanukiTypeRole.Title, TanukiTypeSize.Small, 17, 24, 600, -0.010, TanukiFontToken.System),
        TanukiTypeToken(TanukiTypeRole.Body, TanukiTypeSize.Large, 17, 26, 400, 0.0, TanukiFontToken.System),
        TanukiTypeToken(TanukiTypeRole.Body, TanukiTypeSize.Medium, 15, 23, 400, 0.0, TanukiFontToken.System),
        TanukiTypeToken(TanukiTypeRole.Body, TanukiTypeSize.Small, 13, 20, 400, 0.0, TanukiFontToken.System),
        TanukiTypeToken(TanukiTypeRole.Caption, TanukiTypeSize.Large, 13, 18, 500, 0.0, TanukiFontToken.System),
        TanukiTypeToken(TanukiTypeRole.Caption, TanukiTypeSize.Medium, 12, 17, 500, 0.0, TanukiFontToken.System),
        TanukiTypeToken(TanukiTypeRole.Caption, TanukiTypeSize.Small, 11, 16, 500, 0.0, TanukiFontToken.System),
        TanukiTypeToken(TanukiTypeRole.Code, TanukiTypeSize.Large, 14, 22, 400, 0.0, TanukiFontToken.Monospace),
        TanukiTypeToken(TanukiTypeRole.Code, TanukiTypeSize.Medium, 12, 20, 400, 0.0, TanukiFontToken.Monospace),
        TanukiTypeToken(TanukiTypeRole.Code, TanukiTypeSize.Small, 11, 18, 400, 0.0, TanukiFontToken.Monospace),
    )

    fun token(role: TanukiTypeRole, size: TanukiTypeSize): TanukiTypeToken =
        tokens.first { it.role == role && it.size == size }

    fun byRole(role: TanukiTypeRole): List<TanukiTypeToken> = tokens.filter { it.role == role }
}

/** 动效类型；时长范围由交付约束固定。 */
internal enum class TanukiMotionKind {
    Feedback,
    Structural,
    Ambient,
}

internal data class TanukiMotionToken(
    val name: String,
    val durationMs: Int,
    val kind: TanukiMotionKind,
)

/**
 * 动效令牌：反馈不超过 160ms，结构动画 240–340ms，环境动画约 2.4s。
 *
 * 系统开启“移除动画”时，`durationMs()` 统一返回 0，所有令牌都立即完成。
 */
internal object TanukiMotion {
    const val FEEDBACK_MAX_MS: Int = 160
    const val STRUCTURAL_MIN_MS: Int = 240
    const val STRUCTURAL_MAX_MS: Int = 340
    const val AMBIENT_MS: Int = 2400

    // 即时档与语义别名不重复加入历史枚举列表，避免调用方重复统计同一时长档。
    val instant = TanukiMotionToken("instant", 0, TanukiMotionKind.Feedback)
    val layout = TanukiMotionToken("layout", 240, TanukiMotionKind.Structural)
    val pressIn = TanukiMotionToken("pressIn", 100, TanukiMotionKind.Feedback)
    val pressOut = TanukiMotionToken("pressOut", 140, TanukiMotionKind.Feedback)
    val toggle = TanukiMotionToken("toggle", 160, TanukiMotionKind.Feedback)
    val sheetDismiss = TanukiMotionToken("sheetDismiss", 240, TanukiMotionKind.Structural)
    val sheetOpen = TanukiMotionToken("sheetOpen", 280, TanukiMotionKind.Structural)
    val expand = TanukiMotionToken("expand", 300, TanukiMotionKind.Structural)
    val page = TanukiMotionToken("page", 320, TanukiMotionKind.Structural)
    val pageMax = TanukiMotionToken("pageMax", 340, TanukiMotionKind.Structural)
    val ambientPulse = TanukiMotionToken("ambientPulse", AMBIENT_MS, TanukiMotionKind.Ambient)
    val ambientShimmer = TanukiMotionToken("ambientShimmer", AMBIENT_MS, TanukiMotionKind.Ambient)

    val tokens: List<TanukiMotionToken> = listOf(
        pressIn, pressOut, toggle,
        sheetDismiss, sheetOpen, expand, page, pageMax,
        ambientPulse, ambientShimmer,
    )

    fun durationMs(token: TanukiMotionToken, reducedMotion: Boolean): Int =
        if (reducedMotion) 0 else token.durationMs
}

/** 四个海拔/玻璃层：chrome 浮在内容上，sheet/card/pill 各有独立用途。 */
internal enum class TanukiSurfaceLayer {
    Card,
    Pill,
    Sheet,
    Chrome,
}

/**
 * 单层规格。`fillAlpha` 是表面压在不透明底色上的不透明度；`shadowDp` 是
 * 视觉抬升，`zIndex` 只用于同一容器内的绘制顺序。
 */
internal data class TanukiLayerSpec(
    val layer: TanukiSurfaceLayer,
    val zIndex: Float,
    val shadowDp: Int,
    val shadowAlpha: Double,
    val fillAlpha: Double,
    val blurEnabled: Boolean,
    val blurRadiusDp: Int,
)

/**
 * 海拔/玻璃四层。
 *
 * - chrome：最高层，默认开启模糊；用于顶栏、底栏和悬浮控制。
 * - sheet：模态弹层，不模糊；用遮罩和抬升保证密集内容可读。
 * - card：内容卡，不模糊；用纯色和细边框分层。
 * - pill：玻璃胶囊，开启轻模糊；只承载短标签或单个操作。
 *
 * 实际模糊需要调用方提供已录制的背板（Backdrop）；没有背板时组件退化为
 * 半透明玻璃，不假装已经模糊。
 */
internal object TanukiElevation {
    val card = TanukiLayerSpec(
        layer = TanukiSurfaceLayer.Card,
        zIndex = 1f,
        shadowDp = 0,
        shadowAlpha = 0.0,
        fillAlpha = 1.0,
        blurEnabled = false,
        blurRadiusDp = 0,
    )
    val pill = TanukiLayerSpec(
        layer = TanukiSurfaceLayer.Pill,
        zIndex = 2f,
        shadowDp = 6,
        shadowAlpha = 0.10,
        fillAlpha = 0.78,
        blurEnabled = true,
        blurRadiusDp = 8,
    )
    val sheet = TanukiLayerSpec(
        layer = TanukiSurfaceLayer.Sheet,
        zIndex = 3f,
        shadowDp = 18,
        shadowAlpha = 0.18,
        fillAlpha = 1.0,
        blurEnabled = false,
        blurRadiusDp = 0,
    )
    val chrome = TanukiLayerSpec(
        layer = TanukiSurfaceLayer.Chrome,
        zIndex = 4f,
        shadowDp = 14,
        shadowAlpha = 0.14,
        fillAlpha = 0.86,
        blurEnabled = true,
        blurRadiusDp = 16,
    )

    val all: List<TanukiLayerSpec> = listOf(card, pill, sheet, chrome)

    fun of(layer: TanukiSurfaceLayer): TanukiLayerSpec = all.first { it.layer == layer }
}
