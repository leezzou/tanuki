package com.tanuki.ui.design

import com.tanuki.ui.TanukiContrast
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 与 Android、Compose 无关的 sRGB 颜色值。
 *
 * 设计令牌的生成、对比度审计和 JVM 回归都只依赖本类型，避免把 Compose 的
 * `Color` 带进纯计算层；界面层再统一转换成 Compose 颜色。
 */
internal data class TanukiRgb(
    val red: Int,
    val green: Int,
    val blue: Int,
) {
    init {
        require(red in 0..255 && green in 0..255 && blue in 0..255) {
            "sRGB 通道必须在 0..255：($red, $green, $blue)"
        }
    }

    /** 转成 0xAARRGGBB；默认完全不透明。 */
    fun toArgb(alpha: Int = 255): Long {
        require(alpha in 0..255) { "alpha 必须在 0..255：$alpha" }
        return (alpha.toLong() shl 24) or
            (red.toLong() shl 16) or
            (green.toLong() shl 8) or
            blue.toLong()
    }

    /** WCAG 相对亮度；复用原生界面已有的对比度实现，避免两份公式漂移。 */
    fun relativeLuminance(): Double = TanukiContrast.relativeLuminance(toArgb())

    /** 与另一个颜色的 WCAG 对比度，范围 1..21。 */
    fun contrastWith(other: TanukiRgb): Double =
        TanukiContrast.ratio(toArgb(), other.toArgb())

    fun toHex(): String = "#%02X%02X%02X".format(red, green, blue)
}

/**
 * OKLCH 与 sRGB 的转换、对比度和透明合成。
 *
 * OKLCH 的 L 是感知明度，C 是彩度，H 是色相角。生成色阶时如果请求的彩度超出
 * sRGB 色域，会沿同一色相二分降低彩度，而不是直接裁剪通道，避免色相明显漂移。
 */
internal object TanukiColorMath {

    /** 把 OKLCH 转为 sRGB；超出色域时先做色域映射。 */
    fun oklchToRgb(lightness: Double, chroma: Double, hueDegrees: Double): TanukiRgb {
        require(lightness in 0.0..1.0) { "OKLCH 明度必须在 0..1：$lightness" }
        require(chroma >= 0.0 && chroma.isFinite()) { "OKLCH 彩度必须是非负有限值：$chroma" }
        require(hueDegrees.isFinite()) { "OKLCH 色相必须是有限值：$hueDegrees" }

        val hue = Math.toRadians(hueDegrees)
        val mappedChroma = mapIntoGamut(lightness, chroma, hue)
        val linear = linearRgb(lightness, mappedChroma, hue)
        fun encode(value: Double): Int {
            val clamped = value.coerceIn(0.0, 1.0)
            val encoded = if (clamped <= 0.0031308) {
                12.92 * clamped
            } else {
                1.055 * clamped.pow(1.0 / 2.4) - 0.055
            }
            return (encoded * 255.0).roundToInt().coerceIn(0, 255)
        }
        return TanukiRgb(encode(linear[0]), encode(linear[1]), encode(linear[2]))
    }

    /** 从 0xAARRGGBB 还原 sRGB；忽略 alpha。 */
    fun fromArgb(argb: Long): TanukiRgb = TanukiRgb(
        red = ((argb shr 16) and 0xFF).toInt(),
        green = ((argb shr 8) and 0xFF).toInt(),
        blue = (argb and 0xFF).toInt(),
    )

    /** WCAG 对比度；前景和背景都按不透明 sRGB 计算。 */
    fun contrastRatio(foreground: Long, background: Long): Double =
        fromArgb(foreground).contrastWith(fromArgb(background))

    /**
     * 按 sRGB 通道做 alpha 合成，用于检查玻璃层压在画布上的实际对比度。
     * 只用于设计审计；界面运行时由渲染管线完成同样的合成。
     */
    fun composite(foreground: Long, alpha: Double, background: Long): Long {
        require(alpha in 0.0..1.0) { "alpha 必须在 0..1：$alpha" }
        val fg = fromArgb(foreground)
        val bg = fromArgb(background)
        fun blend(f: Int, b: Int): Int = (f * alpha + b * (1.0 - alpha)).roundToInt().coerceIn(0, 255)
        return TanukiRgb(
            red = blend(fg.red, bg.red),
            green = blend(fg.green, bg.green),
            blue = blend(fg.blue, bg.blue),
        ).toArgb()
    }

    private fun mapIntoGamut(lightness: Double, chroma: Double, hue: Double): Double {
        if (chroma == 0.0 || inGamut(linearRgb(lightness, chroma, hue))) return chroma
        var low = 0.0
        var high = chroma
        repeat(32) {
            val middle = (low + high) / 2.0
            if (inGamut(linearRgb(lightness, middle, hue))) low = middle else high = middle
        }
        return low
    }

    private fun inGamut(linear: DoubleArray): Boolean {
        val epsilon = 1e-6
        return linear.all { it >= -epsilon && it <= 1.0 + epsilon }
    }

    /** OKLab → 线性 sRGB，使用公开的矩阵常量。 */
    private fun linearRgb(lightness: Double, chroma: Double, hue: Double): DoubleArray {
        val a = chroma * cos(hue)
        val b = chroma * sin(hue)
        val long = lightness + 0.3963377774 * a + 0.2158037573 * b
        val medium = lightness - 0.1055613458 * a - 0.0638541728 * b
        val short = lightness - 0.0894841775 * a - 1.2914855480 * b
        val l = long * long * long
        val m = medium * medium * medium
        val s = short * short * short
        return doubleArrayOf(
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
    }
}
