package com.tanuki.storage.docx

import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/** 字符格式覆盖项；null 表示“未指定”，用于按样式链逐层合并。 */
internal data class DocxRunProps(
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val strike: Boolean? = null,
    val sizeHalfPoints: Int? = null,
    val colorArgb: Int? = null,
    val highlightArgb: Int? = null,
) {
    fun merge(over: DocxRunProps): DocxRunProps = DocxRunProps(
        bold = over.bold ?: bold,
        italic = over.italic ?: italic,
        underline = over.underline ?: underline,
        strike = over.strike ?: strike,
        sizeHalfPoints = over.sizeHalfPoints ?: sizeHalfPoints,
        colorArgb = over.colorArgb ?: colorArgb,
        highlightArgb = over.highlightArgb ?: highlightArgb,
    )

    fun applyTo(style: DocxRunStyle): DocxRunStyle = style.copy(
        bold = bold ?: style.bold,
        italic = italic ?: style.italic,
        underline = underline ?: style.underline,
        strike = strike ?: style.strike,
        fontSizeHalfPoints = sizeHalfPoints ?: style.fontSizeHalfPoints,
        colorArgb = colorArgb ?: style.colorArgb,
        highlightArgb = highlightArgb ?: style.highlightArgb,
    )
}

/** 段落格式覆盖项；null 表示“未指定”。 */
internal data class DocxParagraphProps(
    val styleId: String? = null,
    val alignment: DocxAlignment? = null,
    val spaceBeforeTwips: Int? = null,
    val spaceAfterTwips: Int? = null,
    val lineSpacing: DocxLineSpacing? = null,
    val indentLeftTwips: Int? = null,
    val indentFirstLineTwips: Int? = null,
    val hangingTwips: Int? = null,
    val pageBreakBefore: Boolean? = null,
    val numId: Int? = null,
    val numLevel: Int? = null,
    val outlineLevel: Int? = null,
) {
    fun merge(over: DocxParagraphProps): DocxParagraphProps = DocxParagraphProps(
        styleId = over.styleId ?: styleId,
        alignment = over.alignment ?: alignment,
        spaceBeforeTwips = over.spaceBeforeTwips ?: spaceBeforeTwips,
        spaceAfterTwips = over.spaceAfterTwips ?: spaceAfterTwips,
        lineSpacing = over.lineSpacing ?: lineSpacing,
        indentLeftTwips = over.indentLeftTwips ?: indentLeftTwips,
        indentFirstLineTwips = over.indentFirstLineTwips ?: indentFirstLineTwips,
        hangingTwips = over.hangingTwips ?: hangingTwips,
        pageBreakBefore = over.pageBreakBefore ?: pageBreakBefore,
        numId = over.numId ?: numId,
        numLevel = over.numLevel ?: numLevel,
        outlineLevel = over.outlineLevel ?: outlineLevel,
    )
}

internal data class ParsedRunProps(val props: DocxRunProps = DocxRunProps(), val styleId: String? = null)

internal data class DocxStyleDef(
    val id: String,
    val type: String,
    val name: String?,
    val basedOn: String?,
    val paragraph: DocxParagraphProps,
    val run: DocxRunProps,
    val isDefault: Boolean,
)

internal data class ResolvedParagraphStyle(
    val paragraph: DocxParagraphStyle,
    val run: DocxRunStyle,
    val headingLevel: Int,
    val styleName: String?,
    val numId: Int? = null,
    val numLevel: Int = 0,
)

/** docDefaults 与普通/字符样式表；解析时已按上限限制元素数量和样式链长度。 */
internal data class DocxStyleSheet(
    val docRunDefaults: DocxRunProps = DocxRunProps(),
    val docParagraphDefaults: DocxParagraphProps = DocxParagraphProps(),
    val paragraphStyles: Map<String, DocxStyleDef> = emptyMap(),
    val characterStyles: Map<String, DocxStyleDef> = emptyMap(),
    val defaultParagraphStyleId: String? = null,
    val defaultCharacterStyleId: String? = null,
) {
    private val defaultRunStyle = DocxRunStyle(
        fontSizeHalfPoints = docRunDefaults.sizeHalfPoints ?: 22,
        bold = docRunDefaults.bold ?: false,
        italic = docRunDefaults.italic ?: false,
        underline = docRunDefaults.underline ?: false,
        strike = docRunDefaults.strike ?: false,
        colorArgb = docRunDefaults.colorArgb ?: 0xFF000000.toInt(),
        highlightArgb = docRunDefaults.highlightArgb,
    )

    fun resolveParagraph(styleId: String? = null, direct: DocxParagraphProps = DocxParagraphProps()): ResolvedParagraphStyle {
        val appliedId = direct.styleId ?: styleId ?: defaultParagraphStyleId
        val chain = chain(appliedId, paragraphStyles)
        var paragraph = docParagraphDefaults
        var run = docRunDefaults
        for (style in chain) {
            paragraph = paragraph.merge(style.paragraph)
            run = run.merge(style.run)
        }
        // 直接段落格式最后合并，保证标题级别、编号和缩进覆盖样式定义。
        paragraph = paragraph.merge(direct)
        val name = chain.lastOrNull()?.name
        val heading = headingLevel(paragraph, name)
        val runStyle = run.applyTo(defaultRunStyle).withHeading(heading, run)
        return ResolvedParagraphStyle(
            paragraph = DocxParagraphStyle(
                headingLevel = heading,
                alignment = paragraph.alignment ?: DocxAlignment.START,
                spaceBeforeTwips = paragraph.spaceBeforeTwips ?: headingSpaceBefore(heading),
                spaceAfterTwips = paragraph.spaceAfterTwips ?: headingSpaceAfter(heading),
                lineSpacing = paragraph.lineSpacing ?: DocxLineSpacing.Auto(240),
                indentLeftTwips = paragraph.indentLeftTwips ?: 0,
                indentFirstLineTwips = paragraph.indentFirstLineTwips ?: 0,
                hangingTwips = paragraph.hangingTwips ?: 0,
                pageBreakBefore = paragraph.pageBreakBefore ?: false,
                defaultRun = runStyle,
            ),
            run = runStyle,
            headingLevel = heading,
            styleName = name,
            numId = paragraph.numId,
            numLevel = paragraph.numLevel ?: 0,
        )
    }

    /** 合并字符样式链（不含段落的字符格式，调用方需先取段落样式再叠加）。 */
    fun resolveCharacter(styleId: String?): DocxRunProps {
        val applied = styleId ?: defaultCharacterStyleId
        var run = DocxRunProps()
        for (style in chain(applied, characterStyles)) run = run.merge(style.run)
        return run
    }

    fun styleName(styleId: String?): String? = styleId?.let { paragraphStyles[it]?.name ?: characterStyles[it]?.name }

    private fun chain(id: String?, styles: Map<String, DocxStyleDef>): List<DocxStyleDef> {
        if (id == null) return emptyList()
        val output = ArrayDeque<DocxStyleDef>()
        val seen = HashSet<String>()
        var current: String? = id
        while (current != null && seen.size < 32 && seen.add(current)) {
            val style = styles[current] ?: break
            output.addFirst(style)
            current = style.basedOn
        }
        return output.toList()
    }

    private fun headingLevel(paragraph: DocxParagraphProps, name: String?): Int {
        val outline = paragraph.outlineLevel
        if (outline != null) return if (outline in 0..8) outline + 1 else 0
        if (name == null) return 0
        val match = HEADING_NAME.find(name.lowercase(Locale.ROOT)) ?: return 0
        return match.groupValues.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 9) ?: 0
    }

    private fun headingSize(level: Int): Int? = when (level) {
        1 -> 32; 2 -> 26; 3 -> 24; 4 -> 22; 5 -> 22; 6 -> 22
        else -> null
    }

    private fun DocxRunStyle.withHeading(level: Int, props: DocxRunProps): DocxRunStyle {
        if (level <= 0) return this
        return copy(
            bold = props.bold ?: true,
            // 样式显式字号优先；否则标题使用内置字号，不受 docDefaults 的 11 磅影响。
            fontSizeHalfPoints = props.sizeHalfPoints ?: headingSize(level) ?: fontSizeHalfPoints,
        )
    }

    private fun headingSpaceBefore(level: Int): Int = when (level) {
        0 -> 0; 1 -> 240; 2 -> 200; 3 -> 160; else -> 120
    }

    private fun headingSpaceAfter(level: Int): Int = when (level) {
        0 -> 0; 1 -> 160; 2 -> 120; 3 -> 100; else -> 80
    }

    private companion object {
        val HEADING_NAME = Regex("(?:heading|标题)\\s*([1-9])")
    }
}

/** 解析 styles.xml；遇到损坏/不支持结构由调用方转换为中文错误。 */
internal object DocxStyleParser {
    fun parse(parser: XmlPullParser, budget: DocxBudget): DocxStyleSheet {
        val paragraphStyles = linkedMapOf<String, DocxStyleDef>()
        val characterStyles = linkedMapOf<String, DocxStyleDef>()
        var docRun = DocxRunProps()
        var docParagraph = DocxParagraphProps()
        var defaultParagraph: String? = null
        var defaultCharacter: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                    "docDefaults" -> {
                        val defaults = parseDocDefaults(parser, budget)
                        docRun = docRun.merge(defaults.first)
                        docParagraph = docParagraph.merge(defaults.second)
                    }
                    "style" -> {
                        val style = parseStyle(parser, budget)
                        when (style.type) {
                            "paragraph" -> {
                                paragraphStyles[style.id] = style
                                if (style.isDefault) defaultParagraph = style.id
                            }
                            "character" -> {
                                characterStyles[style.id] = style
                                if (style.isDefault) defaultCharacter = style.id
                            }
                        }
                    }
                    else -> budget.enter()
                }
                XmlPullParser.END_TAG -> budget.exit()
                XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                else -> Unit
            }
            event = parser.nextToken()
        }
        return DocxStyleSheet(
            docRunDefaults = docRun,
            docParagraphDefaults = docParagraph,
            paragraphStyles = paragraphStyles,
            characterStyles = characterStyles,
            defaultParagraphStyleId = defaultParagraph,
            defaultCharacterStyleId = defaultCharacter,
        )
    }

    private fun parseDocDefaults(parser: XmlPullParser, budget: DocxBudget): Pair<DocxRunProps, DocxParagraphProps> {
        budget.enter()
        var run = DocxRunProps()
        var paragraph = DocxParagraphProps()
        while (true) {
            when (val event = parser.nextToken()) {
                XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                    "rPr" -> run = run.merge(parseRunProps(parser, budget).props)
                    "pPr" -> paragraph = paragraph.merge(parseParagraphProps(parser, budget))
                    else -> budget.enter()
                }
                XmlPullParser.END_TAG -> {
                    budget.exit()
                    if (DocxXml.localName(parser) == "docDefaults") return run to paragraph
                }
                XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                else -> Unit
            }
        }
    }

    private fun parseStyle(parser: XmlPullParser, budget: DocxBudget): DocxStyleDef {
        budget.enter()
        val id = DocxXml.attribute(parser, "styleId").orEmpty()
        val type = DocxXml.attribute(parser, "type")?.lowercase(Locale.ROOT).orEmpty()
        val isDefault = DocxXml.attribute(parser, "default")?.let { it == "1" || it.equals("true", true) } ?: false
        var name: String? = null
        var basedOn: String? = null
        var paragraph = DocxParagraphProps()
        var run = DocxRunProps()
        while (true) {
            when (val event = parser.nextToken()) {
                XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                    "name" -> {
                        name = DocxXml.attribute(parser, "val")
                        DocxXml.skipSubtree(parser, budget)
                    }
                    "basedOn" -> {
                        basedOn = DocxXml.attribute(parser, "val")
                        DocxXml.skipSubtree(parser, budget)
                    }
                    "pPr" -> paragraph = paragraph.merge(parseParagraphProps(parser, budget))
                    "rPr" -> run = run.merge(parseRunProps(parser, budget).props)
                    "tblStylePr", "pPrChange", "rPrChange" -> DocxXml.skipSubtree(parser, budget)
                    else -> budget.enter()
                }
                XmlPullParser.END_TAG -> {
                    budget.exit()
                    if (DocxXml.localName(parser) == "style") return DocxStyleDef(id, type, name, basedOn, paragraph, run, isDefault)
                }
                XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                else -> Unit
            }
        }
    }

    internal fun parseRunProps(parser: XmlPullParser, budget: DocxBudget): ParsedRunProps {
        budget.enter()
        var props = DocxRunProps()
        var styleId: String? = null
        while (true) {
            when (val event = parser.nextToken()) {
                XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                    "rStyle" -> {
                        styleId = DocxXml.attribute(parser, "val")
                        budget.enter()
                    }
                    "b" -> {
                        props = props.copy(bold = DocxXml.onOff(parser))
                        budget.enter()
                    }
                    "i" -> {
                        props = props.copy(italic = DocxXml.onOff(parser))
                        budget.enter()
                    }
                    "u" -> {
                        val value = DocxXml.attribute(parser, "val")?.lowercase(Locale.ROOT)
                        props = props.copy(underline = value == null || value != "none")
                        budget.enter()
                    }
                    "strike", "dstrike" -> {
                        props = props.copy(strike = DocxXml.onOff(parser))
                        budget.enter()
                    }
                    "sz" -> {
                        DocxXml.intAttribute(parser, "val")?.takeIf { it in 2..400 }?.let { props = props.copy(sizeHalfPoints = it) }
                        budget.enter()
                    }
                    "color" -> {
                        DocxXml.color(parser)?.let { props = props.copy(colorArgb = it) }
                        budget.enter()
                    }
                    "highlight" -> {
                        props = props.copy(highlightArgb = DocxXml.highlight(parser))
                        budget.enter()
                    }
                    "rPrChange" -> DocxXml.skipSubtree(parser, budget)
                    else -> budget.enter()
                }
                XmlPullParser.END_TAG -> {
                    budget.exit()
                    if (DocxXml.localName(parser) == "rPr") return ParsedRunProps(props, styleId)
                }
                XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                else -> Unit
            }
        }
    }

    internal fun parseParagraphProps(parser: XmlPullParser, budget: DocxBudget): DocxParagraphProps {
        budget.enter()
        var props = DocxParagraphProps()
        var inNumbering = 0
        while (true) {
            when (val event = parser.nextToken()) {
                XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                    "pStyle" -> {
                        props = props.copy(styleId = DocxXml.attribute(parser, "val"))
                        budget.enter()
                    }
                    "jc" -> {
                        props = props.copy(alignment = alignment(DocxXml.attribute(parser, "val")))
                        budget.enter()
                    }
                    "spacing" -> {
                        props = props.copy(
                            spaceBeforeTwips = DocxXml.intAttribute(parser, "before")?.coerceIn(0, 100_000),
                            spaceAfterTwips = DocxXml.intAttribute(parser, "after")?.coerceIn(0, 100_000),
                            lineSpacing = lineSpacing(parser),
                        )
                        budget.enter()
                    }
                    "ind" -> {
                        props = props.copy(
                            indentLeftTwips = (DocxXml.intAttribute(parser, "left") ?: DocxXml.intAttribute(parser, "start"))?.coerceIn(-100_000, 100_000),
                            indentFirstLineTwips = DocxXml.intAttribute(parser, "firstLine")?.coerceIn(-100_000, 100_000),
                            hangingTwips = DocxXml.intAttribute(parser, "hanging")?.coerceIn(0, 100_000),
                        )
                        budget.enter()
                    }
                    "numPr" -> {
                        inNumbering++
                        budget.enter()
                    }
                    "numId" -> {
                        if (inNumbering > 0) props = props.copy(numId = DocxXml.intAttribute(parser, "val")?.takeIf { it > 0 })
                        budget.enter()
                    }
                    "ilvl" -> {
                        if (inNumbering > 0) props = props.copy(numLevel = DocxXml.intAttribute(parser, "val")?.coerceIn(0, 8))
                        budget.enter()
                    }
                    "outlineLvl" -> {
                        props = props.copy(outlineLevel = DocxXml.intAttribute(parser, "val")?.coerceIn(0, 9))
                        budget.enter()
                    }
                    "pageBreakBefore" -> {
                        props = props.copy(pageBreakBefore = DocxXml.onOff(parser))
                        budget.enter()
                    }
                    "rPr", "sectPr", "pPrChange" -> DocxXml.skipSubtree(parser, budget)
                    else -> budget.enter()
                }
                XmlPullParser.END_TAG -> {
                    if (DocxXml.localName(parser) == "numPr" && inNumbering > 0) inNumbering--
                    budget.exit()
                    if (DocxXml.localName(parser) == "pPr") return props
                }
                XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                else -> Unit
            }
        }
    }

    private fun alignment(value: String?): DocxAlignment = when (value?.lowercase(Locale.ROOT)) {
        "center" -> DocxAlignment.CENTER
        "right", "end" -> DocxAlignment.END
        "both", "justify", "distribute" -> DocxAlignment.JUSTIFY
        else -> DocxAlignment.START
    }

    private fun lineSpacing(parser: XmlPullParser): DocxLineSpacing? {
        val line = DocxXml.intAttribute(parser, "line")?.coerceIn(0, 100_000) ?: return null
        return when (DocxXml.attribute(parser, "lineRule")?.lowercase(Locale.ROOT)) {
            "exact" -> DocxLineSpacing.Exact(line)
            "atleast" -> DocxLineSpacing.AtLeast(line)
            else -> DocxLineSpacing.Auto(line.coerceAtLeast(1))
        }
    }
}
