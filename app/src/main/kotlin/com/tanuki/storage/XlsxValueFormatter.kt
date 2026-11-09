package com.tanuki.storage

import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * XLSX 数字、日期、百分比与布尔值的只读显示格式。
 * 只覆盖常见内建/自定义格式：不解析 Excel 的全部格式语法，货币字面量、分数和科学计数按近似规则显示。
 */
internal object XlsxValueFormatter {
    /** 1900 日期系统含虚构的 1900-02-29（序列号 60）；序列号 1 表示 1900-01-01。 */
    private val EXCEL_EPOCH = LocalDate.of(1899, 12, 30)
    private val EXCEL_1904_EPOCH = LocalDate.of(1904, 1, 1)

    fun booleanText(value: String): String = when (value.trim()) {
        "1", "true", "TRUE" -> "TRUE"
        "0", "false", "FALSE" -> "FALSE"
        else -> value.trim()
    }

    /** 按单元格格式把数值转成显示文本；无法解析时保留原值。 */
    fun number(raw: String, formatCode: String, date1904: Boolean): String {
        val value = raw.trim().toDoubleOrNull() ?: return raw
        return number(value, formatCode, date1904)
    }

    fun number(value: Double, formatCode: String?, date1904: Boolean): String {
        if (!value.isFinite()) return value.toString()
        val code = formatCode?.takeIf { it.isNotBlank() } ?: "General"
        if (code.equals("General", ignoreCase = true) || code == "@") return general(value)
        val tokens = significantTokens(code)
        val isTime = tokens.any { it == 'h' || it == 's' }
        // "mm:ss" 这类时间格式里的 m 是分钟；只有存在 y/d，或没有时间标记时 m 才按月份处理。
        val isDate = tokens.any { it == 'y' || it == 'd' } || (!isTime && tokens.contains('m'))
        if (isDate || isTime) return dateTime(value, code, isDate, isTime, date1904)
        if (tokens.contains('%')) return percent(value, code)
        return decimal(value, code)
    }

    /** 去掉引号字面量、转义符和方括号区段，只保留决定格式含义的字符。 */
    private fun significantTokens(code: String): String {
        val output = StringBuilder()
        var index = 0
        var quoted = false
        while (index < code.length) {
            val char = code[index]
            when {
                char == '"' -> quoted = !quoted
                quoted -> Unit
                char == '\\' -> index++
                char == '[' -> while (index < code.length && code[index] != ']') index++
                char == '_' || char == '*' -> index++
                else -> output.append(char.lowercaseChar())
            }
            index++
        }
        return output.toString()
    }

    private fun section(code: String): String = code.substringBefore(';').ifEmpty { code }

    private fun decimals(code: String): Int {
        val part = section(significantTokens(code)).substringBefore('%')
        val dot = part.indexOf('.')
        if (dot < 0) return 0
        var count = 0
        for (index in dot + 1 until part.length) {
            val char = part[index]
            if (char == '0' || char == '#') count++ else break
        }
        return count.coerceIn(0, 10)
    }

    private fun grouped(code: String): Boolean = Regex("[#0],[#0]").containsMatchIn(significantTokens(code))

    private fun general(value: Double): String {
        if (value == floor(value) && abs(value) < 1e15) return value.toLong().toString()
        val rounded = BigDecimal.valueOf(value).round(MathContext(12)).stripTrailingZeros()
        return rounded.toPlainString()
    }

    private fun decimal(value: Double, code: String): String {
        val places = decimals(code)
        val pattern = if (grouped(code)) "%,.${places}f" else "%.${places}f"
        return String.format(Locale.ROOT, pattern, value)
    }

    private fun percent(value: Double, code: String): String =
        String.format(Locale.ROOT, "%.${decimals(code)}f%%", value * 100.0)

    private fun dateTime(value: Double, code: String, hasDate: Boolean, hasTime: Boolean, date1904: Boolean): String {
        val days = floor(value).toLong()
        val seconds = ((value - days) * 86_400.0).roundToLong().coerceIn(0L, 86_399L)
        val date = if (date1904) EXCEL_1904_EPOCH.plusDays(days)
        // 1900 日期系统在序列号 60 之前偏移一天，才能与 Excel 的闰年错误保持一致。
        else EXCEL_EPOCH.plusDays(if (days in 1..59) days + 1 else days)
        val moment = LocalDateTime.of(date, LocalTime.MIDNIGHT).plusSeconds(seconds)
        val lower = code.lowercase(Locale.ROOT)
        return when {
            hasDate && hasTime -> moment.format(
                DateTimeFormatter.ofPattern(if (lower.contains('s')) "yyyy-MM-dd HH:mm:ss" else "yyyy-MM-dd HH:mm", Locale.ROOT),
            )
            hasDate -> moment.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT))
            // 不含小时的 mm:ss 是“时内分钟+秒”，不能按 12 小时制显示成 12:00:00。
            !lower.contains('h') -> {
                val withinHour = seconds % 3600
                String.format(Locale.ROOT, "%02d:%02d", withinHour / 60, withinHour % 60)
            }
            else -> moment.format(
                DateTimeFormatter.ofPattern(if (lower.contains('s')) "HH:mm:ss" else "HH:mm", Locale.ROOT),
            )
        }
    }
}
