package com.tanuki.storage.docx

import org.xmlpull.v1.XmlPullParser
import java.util.Locale

internal data class DocxListLevel(
    val level: Int,
    val format: String,
    val text: String,
    val start: Int,
    val indentLeftTwips: Int?,
    val hangingTwips: Int?,
)

internal data class DocxAbstractNumbering(val id: String, val levels: Map<Int, DocxListLevel>)

internal class DocxNumbering(
    val abstracts: Map<String, DocxAbstractNumbering> = emptyMap(),
    val nums: Map<Int, String> = emptyMap(),
) {
    fun level(numId: Int, level: Int): DocxListLevel? {
        val abstractId = nums[numId] ?: return null
        val abstract = abstracts[abstractId] ?: return null
        return abstract.levels[level] ?: abstract.levels[0]
    }

    fun formatter(): DocxNumberingFormatter = DocxNumberingFormatter(this)
}

/** 按文档顺序维护每个 numId 的层级计数器，生成 `%1.%2` 之类的编号文本。 */
internal class DocxNumberingFormatter(private val numbering: DocxNumbering) {
    private val counters = HashMap<Int, IntArray>()

    fun marker(numId: Int, level: Int): DocxNumberingMarker? {
        val safeLevel = level.coerceIn(0, 8)
        val definition = numbering.level(numId, safeLevel) ?: return null
        val state = counters.getOrPut(numId) { IntArray(9) { UNSET } }
        state[safeLevel] = if (state[safeLevel] == UNSET) definition.start.coerceAtLeast(0) else state[safeLevel] + 1
        for (deeper in safeLevel + 1 until state.size) state[deeper] = UNSET
        val bullet = definition.format.equals("bullet", ignoreCase = true)
        val text = if (bullet) bulletText(definition.text) else substitute(definition.text, state, numId)
        return DocxNumberingMarker(
            text = text,
            level = safeLevel,
            bullet = bullet,
            indentLeftTwips = definition.indentLeftTwips ?: ((safeLevel + 1) * 720),
            hangingTwips = definition.hangingTwips ?: 360,
        )
    }

    private fun substitute(template: String, state: IntArray, numId: Int): String {
        if (template.isEmpty()) return ""
        val output = StringBuilder()
        var index = 0
        while (index < template.length) {
            val char = template[index]
            if (char == '%' && index + 1 < template.length && template[index + 1] in '1'..'9') {
                val target = template[index + 1] - '1'
                val value = if (state[target] == UNSET) {
                    numbering.level(numId, target)?.start ?: 1
                } else state[target]
                val format = numbering.level(numId, target)?.format ?: "decimal"
                output.append(formatValue(value, format))
                index += 2
            } else {
                output.append(char)
                index++
            }
        }
        return output.toString()
    }

    private fun bulletText(text: String): String {
        if (text.isEmpty()) return "\u2022"
        val mapped = buildString {
            for (char in text) append(if (char.code in 0xE000..0xF8FF) bulletGlyph(char.code) else char)
        }
        return mapped.ifBlank { "\u2022" }
    }

    private fun bulletGlyph(code: Int): String = when (code) {
        0xF0A7, 0xF0D8 -> "\u25AA"
        0xF0FC -> "\u2713"
        0xF0E0 -> "\u2192"
        else -> "\u2022"
    }

    private fun formatValue(value: Int, format: String): String = when (format.lowercase(Locale.ROOT)) {
        "lowerletter" -> letters(value).lowercase(Locale.ROOT)
        "upperletter" -> letters(value)
        "lowerroman" -> roman(value).lowercase(Locale.ROOT)
        "upperroman" -> roman(value)
        "decimalzero" -> value.toString().padStart(2, '0')
        "none" -> ""
        else -> value.toString()
    }

    private fun letters(value: Int): String {
        if (value <= 0) return value.toString()
        var number = value
        val output = StringBuilder()
        while (number > 0) {
            val remainder = (number - 1) % 26
            output.append(('A'.code + remainder).toChar())
            number = (number - 1) / 26
        }
        return output.reverse().toString()
    }

    private fun roman(value: Int): String {
        if (value <= 0 || value > 3999) return value.toString()
        val table = listOf(
            1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
            50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
        )
        var remaining = value
        val output = StringBuilder()
        for ((amount, symbol) in table) {
            while (remaining >= amount) {
                output.append(symbol)
                remaining -= amount
            }
        }
        return output.toString()
    }

    private companion object {
        const val UNSET = Int.MIN_VALUE
    }
}

internal object DocxNumberingParser {
    fun parse(parser: XmlPullParser, budget: DocxBudget): DocxNumbering {
        val abstracts = linkedMapOf<String, DocxAbstractNumbering>()
        val nums = linkedMapOf<Int, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                    "abstractNum" -> {
                        val abstract = parseAbstract(parser, budget)
                        if (abstract != null) abstracts[abstract.id] = abstract
                    }
                    "num" -> {
                        val numId = DocxXml.intAttribute(parser, "numId")
                        val abstractId = parseNum(parser, budget)
                        if (numId != null && numId > 0 && abstractId != null) nums[numId] = abstractId
                    }
                    else -> budget.enter()
                }
                XmlPullParser.END_TAG -> budget.exit()
                XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                else -> Unit
            }
            event = parser.nextToken()
        }
        return DocxNumbering(abstracts, nums)
    }

    private fun parseAbstract(parser: XmlPullParser, budget: DocxBudget): DocxAbstractNumbering? {
        budget.enter()
        val id = DocxXml.attribute(parser, "abstractNumId")
        if (id == null) {
            budget.exit()
            return null
        }
        val levels = linkedMapOf<Int, DocxListLevel>()
        while (true) {
            when (val event = parser.nextToken()) {
                XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                    "lvl" -> {
                        val level = parseLevel(parser, budget)
                        if (level != null) levels[level.level] = level
                    }
                    "numStyleLink", "styleLink" -> DocxXml.skipSubtree(parser, budget)
                    else -> budget.enter()
                }
                XmlPullParser.END_TAG -> {
                    budget.exit()
                    if (DocxXml.localName(parser) == "abstractNum") return DocxAbstractNumbering(id, levels)
                }
                XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                else -> Unit
            }
        }
    }

    private fun parseNum(parser: XmlPullParser, budget: DocxBudget): String? {
        budget.enter()
        var abstractId: String? = null
        while (true) {
            when (val event = parser.nextToken()) {
                XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                    "abstractNumId" -> {
                        abstractId = DocxXml.attribute(parser, "val")
                        budget.enter()
                    }
                    "lvlOverride" -> DocxXml.skipSubtree(parser, budget)
                    else -> budget.enter()
                }
                XmlPullParser.END_TAG -> {
                    budget.exit()
                    if (DocxXml.localName(parser) == "num") return abstractId
                }
                XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                else -> Unit
            }
        }
    }

    private fun parseLevel(parser: XmlPullParser, budget: DocxBudget): DocxListLevel? {
        budget.enter()
        val level = DocxXml.intAttribute(parser, "ilvl")?.coerceIn(0, 8) ?: 0
        var format = "decimal"
        var text = "%${level + 1}."
        var start = 1
        var indentLeft: Int? = null
        var hanging: Int? = null
        while (true) {
            when (val event = parser.nextToken()) {
                XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                    "start" -> {
                        start = DocxXml.intAttribute(parser, "val")?.coerceIn(0, 1_000_000) ?: start
                        budget.enter()
                    }
                    "numFmt" -> {
                        format = DocxXml.attribute(parser, "val")?.lowercase(Locale.ROOT) ?: format
                        budget.enter()
                    }
                    "lvlText" -> {
                        text = DocxXml.attribute(parser, "val").orEmpty().take(64)
                        budget.enter()
                    }
                    "pPr" -> {
                        val props = DocxStyleParser.parseParagraphProps(parser, budget)
                        indentLeft = props.indentLeftTwips
                        hanging = props.hangingTwips
                    }
                    "rPr", "lvlJc", "suff", "isLgl", "pStyle" -> DocxXml.skipSubtree(parser, budget)
                    else -> budget.enter()
                }
                XmlPullParser.END_TAG -> {
                    budget.exit()
                    if (DocxXml.localName(parser) == "lvl") {
                        return DocxListLevel(level, format, text, start, indentLeft, hanging)
                    }
                }
                XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                else -> Unit
            }
        }
    }
}
