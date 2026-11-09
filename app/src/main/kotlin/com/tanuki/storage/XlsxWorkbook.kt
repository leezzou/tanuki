package com.tanuki.storage

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile

/** 单元格水平对齐；GENERAL 按文本/数字自动选择。 */
internal enum class XlsxHorizontalAlign { GENERAL, LEFT, CENTER, RIGHT }

/** 单元格垂直对齐。 */
internal enum class XlsxVerticalAlign { TOP, CENTER, BOTTOM }

/** 四边边框；只区分是否存在和颜色，不还原线型。 */
internal data class XlsxBorderSides(
    val left: Boolean = false,
    val right: Boolean = false,
    val top: Boolean = false,
    val bottom: Boolean = false,
    val color: Int? = null,
) {
    val visible: Boolean get() = left || right || top || bottom
}

/** 解析后的常用单元格样式；主题色和条件格式不在支持范围。 */
internal data class XlsxCellStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val fontSize: Float = 11f,
    val fontColor: Int? = null,
    val fillColor: Int? = null,
    val horizontal: XlsxHorizontalAlign = XlsxHorizontalAlign.GENERAL,
    val vertical: XlsxVerticalAlign = XlsxVerticalAlign.BOTTOM,
    val border: XlsxBorderSides? = null,
    val formatCode: String = "General",
)

/**
 * 只读单元格。text 已是显示文本：数字/日期按单元格格式转换，公式只使用文件内缓存值。
 * row/column 从 0 开始；covered 表示被合并区域覆盖，rowSpan/columnSpan 只写在合并起点。
 */
internal data class XlsxCell(
    val row: Int,
    val column: Int,
    val text: String,
    val style: XlsxCellStyle,
    val formula: Boolean = false,
    val covered: Boolean = false,
    val rowSpan: Int = 1,
    val columnSpan: Int = 1,
    val numeric: Boolean = false,
)

internal data class XlsxRow(val index: Int, val heightPoints: Float?, val cells: List<XlsxCell>)

/** 一个工作表；columnWidths 以字符宽度表示，null 表示使用默认列宽。 */
internal data class XlsxSheet(
    val name: String,
    val rows: List<XlsxRow>,
    val columnWidths: List<Float?>,
    val frozenRows: Int,
    val frozenColumns: Int,
    val truncated: Boolean,
) {
    val columnCount: Int get() = columnWidths.size
}

/** 工作簿只读模型；truncated 表示结构被上限截断，textTruncated 表示有单元格文本被截断。 */
internal data class XlsxWorkbook(
    val sheets: List<XlsxSheet>,
    val truncated: Boolean = false,
    val textTruncated: Boolean = false,
    val date1904: Boolean = false,
)

/**
 * 最小 OOXML XLSX 解析器：读取 workbook.xml、rels、sharedStrings.xml、styles.xml 和 sheet XML。
 * 不解压到磁盘、不执行宏、不计算公式、不访问外部链接；所有大小、数量、XML 深度和路径都有上限。
 */
internal object XlsxWorkbookParser {
    data class Limits(
        val maxArchiveBytes: Long = 64L * 1024 * 1024,
        val maxEntries: Int = 4096,
        val maxTotalUncompressedBytes: Long = 256L * 1024 * 1024,
        val maxPartBytes: Long = 32L * 1024 * 1024,
        val maxSheets: Int = 32,
        val maxRows: Int = 2000,
        val maxColumns: Int = 128,
        val maxCellsPerSheet: Int = 40_000,
        val maxTotalCells: Int = 120_000,
        val maxSharedStrings: Int = 50_000,
        val maxSharedStringChars: Int = 32_767,
        val maxSharedStringTotalChars: Int = 4_000_000,
        val maxCellChars: Int = 2000,
        val maxTotalCellChars: Int = 8_000_000,
        val maxXmlDepth: Int = 64,
        val maxMergeArea: Int = 4096,
        val maxMerges: Int = 4096,
    )

    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val WORKBOOK_PART = "xl/workbook.xml"
    private const val WORKBOOK_RELS = "xl/_rels/workbook.xml.rels"
    private const val SHARED_STRINGS = "xl/sharedStrings.xml"
    private const val STYLES = "xl/styles.xml"
    private val DEFAULT_STYLE = XlsxCellStyle()

    fun parse(
        file: java.io.File,
        limits: Limits = Limits(),
        parserFor: (InputStream) -> XmlPullParser,
    ): XlsxWorkbook {
        if (!file.isFile) throw IOException("文件不存在或不是普通文件")
        if (file.length() > limits.maxArchiveBytes) throw IOException("此 XLSX 文件过大，已停止预览")
        val zip = openZip(file, limits)
        zip.use { archive ->
            val shared = parseSharedStrings(archive, limits, parserFor)
            val styles = parseStyles(archive, limits, parserFor)
            val definition = parseWorkbook(archive, limits, parserFor)
            if (definition.sheets.isEmpty()) throw IOException("此 XLSX 没有可显示的工作表")
            val budget = CellBudget(limits.maxCellsPerSheet, limits.maxTotalCells, limits.maxTotalCellChars)
            var truncated = shared.truncated
            var textTruncated = shared.textTruncated
            val sheets = definition.sheets.map { reference ->
                budget.startSheet()
                val sheet = parseSheet(
                    archive, reference, shared.strings, styles, definition.date1904, limits, parserFor, budget,
                )
                truncated = truncated || sheet.truncated
                textTruncated = textTruncated || sheet.textTruncated
                sheet.sheet
            }
            return XlsxWorkbook(sheets, truncated, textTruncated, definition.date1904)
        }
    }

    private fun openZip(file: java.io.File, limits: Limits): ZipFile {
        val zip = try {
            ZipFile(file)
        } catch (failure: IOException) {
            throw IOException("无法读取此 XLSX：文件已加密、损坏或不是有效的 Excel 工作簿", failure)
        }
        try {
            var count = 0
            var total = 0L
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (++count > limits.maxEntries) throw IOException("此 XLSX 压缩包条目过多，已停止预览")
                total += entry.size.coerceAtLeast(0L)
                if (total > limits.maxTotalUncompressedBytes) throw IOException("此 XLSX 解压后过大，已停止预览")
            }
        } catch (failure: Throwable) {
            zip.close()
            throw failure
        }
        return zip
    }

    /** 只允许访问压缩包内的普通相对路径；拒绝绝对路径、反斜杠和 .. 段。 */
    private fun safePart(name: String): String {
        if (name.isEmpty() || name.startsWith('/') || name.contains('\\') || name.contains('\u0000') ||
            name.split('/').any { it.isEmpty() || it == "." || it == ".." }
        ) throw IOException("此 XLSX 包含不安全的路径引用，已停止预览")
        return name
    }

    /** 把 rels 的 Target 解析为相对 xl/ 的包内路径，越界或外部链接一律拒绝。 */
    private fun resolveTarget(target: String): String {
        val trimmed = target.trim()
        if (trimmed.isEmpty()) throw IOException("此 XLSX 包含空路径的工作表引用，无法预览")
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.startsWith("http:") || lower.startsWith("https:") || lower.startsWith("file:") ||
            trimmed.contains('\\') || trimmed.contains('\u0000')
        ) throw IOException("此 XLSX 引用外部工作表，内置预览不支持")
        val absolute = trimmed.startsWith('/')
        val segments = ArrayDeque<String>()
        if (!absolute) segments.addAll("xl".split('/').filter { it.isNotEmpty() })
        for (segment in trimmed.removePrefix("/").split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> throw IOException("此 XLSX 包含不安全的路径引用，已停止预览")
                else -> {
                    if (segment.contains(':')) throw IOException("此 XLSX 包含不安全的路径引用，已停止预览")
                    segments.addLast(segment)
                }
            }
        }
        if (segments.isEmpty()) throw IOException("此 XLSX 包含空路径的工作表引用，无法预览")
        return safePart(segments.joinToString("/"))
    }

    private fun <T> readPart(
        zip: ZipFile,
        name: String,
        label: String,
        limits: Limits,
        parserFor: (InputStream) -> XmlPullParser,
        block: (Cursor) -> T,
    ): T {
        val path = safePart(name)
        val entry = zip.getEntry(path) ?: throw IOException("此 XLSX 缺少${label}，无法预览")
        if (entry.size > limits.maxPartBytes) throw IOException("此 XLSX 的${label}解压后过大，已停止预览")
        return zip.getInputStream(entry).use { raw ->
            val input = LimitedInputStream(raw, limits.maxPartBytes, label)
            val parser = try {
                parserFor(input)
            } catch (failure: Exception) {
                throw IOException("此 XLSX 的${label}格式不正确，无法预览", failure)
            }
            try {
                block(Cursor(parser, limits.maxXmlDepth, label))
            } catch (failure: XmlPullParserException) {
                throw IOException("此 XLSX 的${label}格式不正确，无法预览", failure)
            }
        }
    }

    /** 带深度、DTD 和实体检查的 XML 游标；所有解析循环都必须经 next() 前进。 */
    private class Cursor(
        val parser: XmlPullParser,
        private val maxDepth: Int,
        private val label: String,
    ) {
        private var depth = 0
        var event: Int = parser.eventType
            private set
        val name: String get() = parser.name.orEmpty().substringAfter(':')

        fun next(): Int {
            event = parser.nextToken()
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    if (depth > maxDepth) throw IOException("此 XLSX 的 XML 层级过深，已停止预览")
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.DOCDECL -> throw IOException("此 XLSX 包含不受支持的文档类型声明，已停止预览")
                XmlPullParser.ENTITY_REF -> if (parser.text == null) {
                    throw IOException("此 XLSX 包含无法解析的实体引用，格式不正确")
                }
            }
            return event
        }

        fun attr(name: String): String? = parser.getAttributeValue(null, name)
        fun relId(): String? = parser.getAttributeValue(REL_NS, "id")
            ?: parser.getAttributeValue(null, "r:id")
            ?: attr("id")
        fun text(): String = parser.text.orEmpty()
        fun intAttr(name: String): Int? = attr(name)?.trim()?.toIntOrNull()
        fun floatAttr(name: String): Float? = attr(name)?.trim()?.toFloatOrNull()
    }

    private data class SharedStrings(val strings: List<String>, val truncated: Boolean, val textTruncated: Boolean)

    private fun parseSharedStrings(zip: ZipFile, limits: Limits, parserFor: (InputStream) -> XmlPullParser): SharedStrings {
        if (zip.getEntry(SHARED_STRINGS) == null) return SharedStrings(emptyList(), false, false)
        var truncated = false
        var textTruncated = false
        var totalChars = 0
        val output = ArrayList<String>()
        readPart(zip, SHARED_STRINGS, "共享字符串表", limits, parserFor) { cursor ->
            var current: StringBuilder? = null
            var inText = false
            var phoneticDepth = 0
            var event = cursor.event
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (cursor.name) {
                        "si" -> {
                            if (output.size >= limits.maxSharedStrings) {
                                throw IOException("此 XLSX 共享字符串数量超过内置预览上限，已停止预览")
                            }
                            current = StringBuilder()
                        }
                        "rPh" -> phoneticDepth++
                        "t" -> inText = true
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.IGNORABLE_WHITESPACE -> {
                        val text = cursor.text()
                        if (inText && phoneticDepth == 0 && text.isNotEmpty()) {
                            val builder = current ?: StringBuilder()
                            val room = limits.maxSharedStringChars - builder.length
                            if (room <= 0) textTruncated = true
                            else {
                                val accepted = if (text.length <= room) text else text.substring(0, room)
                                if (accepted.length < text.length) textTruncated = true
                                val totalRoom = limits.maxSharedStringTotalChars - totalChars
                                if (totalRoom <= 0) throw IOException("此 XLSX 共享字符串总量超过内置预览上限，已停止预览")
                                val finalText = if (accepted.length <= totalRoom) accepted else accepted.substring(0, totalRoom)
                                if (finalText.length < accepted.length) textTruncated = true
                                builder.append(finalText)
                                totalChars += finalText.length
                            }
                            current = builder
                        }
                    }
                    XmlPullParser.END_TAG -> when (cursor.name) {
                        "t" -> inText = false
                        "rPh" -> if (phoneticDepth > 0) phoneticDepth--
                        "si" -> {
                            output += current?.toString().orEmpty()
                            current = null
                        }
                    }
                }
                event = cursor.next()
            }
        }
        return SharedStrings(output, truncated, textTruncated)
    }

    private data class XfRecord(
        val numFmtId: Int,
        val fontId: Int,
        val fillId: Int,
        val borderId: Int,
        val horizontal: XlsxHorizontalAlign,
        val vertical: XlsxVerticalAlign,
    )

    private data class FontRecord(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val size: Float = 11f,
        val color: Int? = null,
    )

    private data class BorderRecord(
        val left: Boolean = false,
        val right: Boolean = false,
        val top: Boolean = false,
        val bottom: Boolean = false,
        val color: Int? = null,
    )

    private data class Styles(val cellStyles: List<XlsxCellStyle>) {
        /** 缺少 s 属性表示样式索引 0，而不是无样式。 */
        fun styleAt(index: Int?): XlsxCellStyle = cellStyles.getOrNull(index ?: 0) ?: DEFAULT_STYLE
    }

    private val builtinFormats: Map<Int, String> = mapOf(
        0 to "General", 1 to "0", 2 to "0.00", 3 to "#,##0", 4 to "#,##0.00",
        9 to "0%", 10 to "0.00%", 11 to "0.00E+00", 12 to "# ?/?", 13 to "# ??/??",
        14 to "yyyy-mm-dd", 15 to "d-mmm-yy", 16 to "d-mmm", 17 to "mmm-yy",
        18 to "h:mm AM/PM", 19 to "h:mm:ss AM/PM", 20 to "h:mm", 21 to "h:mm:ss",
        22 to "yyyy-mm-dd h:mm", 37 to "#,##0", 38 to "#,##0", 39 to "#,##0.00", 40 to "#,##0.00",
        45 to "mm:ss", 46 to "[h]:mm:ss", 47 to "mm:ss.0", 48 to "##0.0E+0", 49 to "@",
    )

    private fun parseStyles(zip: ZipFile, limits: Limits, parserFor: (InputStream) -> XmlPullParser): Styles {
        if (zip.getEntry(STYLES) == null) return Styles(listOf(DEFAULT_STYLE))
        val customFormats = HashMap<Int, String>()
        val fonts = ArrayList<FontRecord>()
        val fills = ArrayList<Int?>()
        val borders = ArrayList<BorderRecord>()
        val xfs = ArrayList<XfRecord>()
        readPart(zip, STYLES, "样式表", limits, parserFor) { cursor ->
            var font: FontRecord? = null
            var border: BorderRecord? = null
            var xf: XfRecord? = null
            var fillColor: Int? = null
            var fillStarted = false
            var patternSolid = false
            var inFonts = false
            var inFills = false
            var inBorders = false
            var inCellXfs = false
            var horizontal = XlsxHorizontalAlign.GENERAL
            var vertical = XlsxVerticalAlign.BOTTOM
            var event = cursor.event
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (cursor.name) {
                        "fonts" -> inFonts = true
                        "fills" -> inFills = true
                        "borders" -> inBorders = true
                        "cellXfs" -> inCellXfs = true
                        "font" -> if (inFonts) font = FontRecord()
                        "fill" -> if (inFills) {
                            fillStarted = true
                            fillColor = null
                            patternSolid = false
                        }
                        "patternFill" -> if (fillStarted) patternSolid = cursor.attr("patternType") == "solid"
                        "fgColor" -> if (fillStarted) {
                            fillColor = parseColor(cursor) ?: fillColor
                        }
                        "border" -> if (inBorders) border = BorderRecord()
                        "left", "right", "top", "bottom" -> if (border != null) {
                            border = borderSide(border, cursor.name, cursor)
                        }
                        "b" -> if (font != null && cursor.attr("val") != "0") font = font.copy(bold = true)
                        "i" -> if (font != null && cursor.attr("val") != "0") font = font.copy(italic = true)
                        "u" -> if (font != null && cursor.attr("val") != "none") font = font.copy(underline = true)
                        "sz" -> if (font != null) {
                            font = font.copy(size = cursor.floatAttr("val")?.coerceIn(6f, 96f) ?: font.size)
                        }
                        "color" -> when {
                            font != null -> font = font.copy(color = parseColor(cursor) ?: font.color)
                            border != null -> border = border.copy(color = parseColor(cursor) ?: border.color)
                        }
                        "numFmt" -> {
                            val id = cursor.intAttr("numFmtId")
                            val code = cursor.attr("formatCode")?.take(256)
                            if (id != null && code != null) customFormats[id] = code
                        }
                        "xf" -> if (inCellXfs) {
                            horizontal = XlsxHorizontalAlign.GENERAL
                            vertical = XlsxVerticalAlign.BOTTOM
                            xf = XfRecord(
                                numFmtId = cursor.intAttr("numFmtId") ?: 0,
                                fontId = cursor.intAttr("fontId") ?: 0,
                                fillId = cursor.intAttr("fillId") ?: 0,
                                borderId = cursor.intAttr("borderId") ?: 0,
                                horizontal = XlsxHorizontalAlign.GENERAL,
                                vertical = XlsxVerticalAlign.BOTTOM,
                            )
                        }
                        "alignment" -> if (xf != null) {
                            horizontal = when (cursor.attr("horizontal")) {
                                "left" -> XlsxHorizontalAlign.LEFT
                                "center", "centerContinuous" -> XlsxHorizontalAlign.CENTER
                                "right" -> XlsxHorizontalAlign.RIGHT
                                else -> XlsxHorizontalAlign.GENERAL
                            }
                            vertical = when (cursor.attr("vertical")) {
                                "top" -> XlsxVerticalAlign.TOP
                                "center" -> XlsxVerticalAlign.CENTER
                                else -> XlsxVerticalAlign.BOTTOM
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> when (cursor.name) {
                        "fonts" -> inFonts = false
                        "fills" -> inFills = false
                        "borders" -> inBorders = false
                        "cellXfs" -> inCellXfs = false
                        "font" -> font?.let { fonts += it; font = null }
                        "fill" -> {
                            if (fillStarted) fills += if (patternSolid) fillColor else null
                            fillStarted = false
                        }
                        "border" -> border?.let { borders += it; border = null }
                        "xf" -> if (inCellXfs) {
                            xf?.let {
                                xfs += it.copy(horizontal = horizontal, vertical = vertical)
                            }
                            xf = null
                        }
                    }
                }
                event = cursor.next()
            }
            // 没有 cellXfs 时保留默认样式，避免单元格索引失效。
            if (xfs.isEmpty()) xfs += XfRecord(0, 0, 0, 0, XlsxHorizontalAlign.GENERAL, XlsxVerticalAlign.BOTTOM)
            if (fonts.isEmpty()) fonts += FontRecord()
            if (fills.isEmpty()) fills += null
            if (borders.isEmpty()) borders += BorderRecord()
        }
        val styles = xfs.map { xf ->
            val font = fonts.getOrNull(xf.fontId) ?: FontRecord()
            val fill = fills.getOrNull(xf.fillId)
            val border = borders.getOrNull(xf.borderId)?.takeIf { it.left || it.right || it.top || it.bottom }
            val format = customFormats[xf.numFmtId] ?: builtinFormats[xf.numFmtId] ?: "General"
            XlsxCellStyle(
                bold = font.bold,
                italic = font.italic,
                underline = font.underline,
                fontSize = font.size,
                fontColor = font.color,
                fillColor = fill,
                horizontal = xf.horizontal,
                vertical = xf.vertical,
                border = border?.let { XlsxBorderSides(it.left, it.right, it.top, it.bottom, it.color) },
                formatCode = format,
            )
        }
        return Styles(styles.ifEmpty { listOf(DEFAULT_STYLE) })
    }

    private fun borderSide(current: BorderRecord, side: String, cursor: Cursor): BorderRecord {
        val style = cursor.attr("style")
        if (style == null || style == "none") return current
        return when (side) {
            "left" -> current.copy(left = true)
            "right" -> current.copy(right = true)
            "top" -> current.copy(top = true)
            else -> current.copy(bottom = true)
        }
    }

    /** 只解析 rgb 和常用 indexed 色；主题色需要主题表，近似为无色。 */
    private fun parseColor(cursor: Cursor): Int? {
        cursor.attr("rgb")?.let { return parseRgb(it) }
        cursor.attr("indexed")?.trim()?.toIntOrNull()?.let { return indexedColors.getOrNull(it) }
        return null
    }

    private fun parseRgb(value: String): Int? {
        val hex = value.trim().removePrefix("#")
        val normalized = when (hex.length) {
            6 -> "FF$hex"
            8 -> hex
            else -> return null
        }
        return normalized.toLongOrNull(16)?.toInt()
    }

    private val indexedColors: List<Int> = listOf(
        0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(),
        0xFF0000FF.toInt(), 0xFFFFFF00.toInt(), 0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(),
        0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(),
        0xFF0000FF.toInt(), 0xFFFFFF00.toInt(), 0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(),
        0xFF800000.toInt(), 0xFF008000.toInt(), 0xFF000080.toInt(), 0xFF808000.toInt(),
        0xFF800080.toInt(), 0xFF008080.toInt(), 0xFFC0C0C0.toInt(), 0xFF808080.toInt(),
        0xFF9999FF.toInt(), 0xFF993366.toInt(), 0xFFFFFFCC.toInt(), 0xFFCCFFFF.toInt(),
        0xFF660066.toInt(), 0xFFFF8080.toInt(), 0xFF0066CC.toInt(), 0xFFCCCCFF.toInt(),
    )

    private data class SheetReference(val name: String, val part: String)

    private data class WorkbookDefinition(val sheets: List<SheetReference>, val date1904: Boolean)

    private data class Relationship(val type: String, val target: String, val external: Boolean)

    private fun parseWorkbook(zip: ZipFile, limits: Limits, parserFor: (InputStream) -> XmlPullParser): WorkbookDefinition {
        if (zip.getEntry(WORKBOOK_PART) == null) {
            throw IOException("此 XLSX 缺少工作簿定义（xl/workbook.xml），无法预览")
        }
        var date1904 = false
        val rawSheets = ArrayList<Triple<String, String, Boolean>>()
        readPart(zip, WORKBOOK_PART, "工作簿定义", limits, parserFor) { cursor ->
            var event = cursor.event
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) when (cursor.name) {
                    "workbookPr" -> date1904 = cursor.attr("date1904").let { it == "1" || it.equals("true", true) }
                    "sheet" -> {
                        if (rawSheets.size >= limits.maxSheets) {
                            throw IOException("此 XLSX 工作表数量超过内置预览上限，已停止预览")
                        }
                        val name = cursor.attr("name")?.take(64)?.ifBlank { "工作表${rawSheets.size + 1}" }
                            ?: "工作表${rawSheets.size + 1}"
                        val relId = cursor.relId()
                            ?: throw IOException("此 XLSX 缺少工作表“$name”的引用，无法预览")
                        val hidden = cursor.attr("state") == "hidden" || cursor.attr("state") == "veryHidden"
                        rawSheets += Triple(name, relId, hidden)
                    }
                }
                event = cursor.next()
            }
        }
        if (rawSheets.isEmpty()) throw IOException("此 XLSX 没有可显示的工作表")
        val relationships = parseRelationships(zip, limits, parserFor)
        val sheets = rawSheets.map { (name, relId, _) ->
            val relationship = relationships[relId]
                ?: throw IOException("此 XLSX 缺少工作表“$name”的定义，无法预览")
            if (relationship.external || !relationship.type.endsWith("/worksheet")) {
                throw IOException("此 XLSX 引用外部或不支持的工作表，内置预览不支持")
            }
            SheetReference(name, resolveTarget(relationship.target))
        }
        return WorkbookDefinition(sheets, date1904)
    }

    private fun parseRelationships(zip: ZipFile, limits: Limits, parserFor: (InputStream) -> XmlPullParser): Map<String, Relationship> {
        if (zip.getEntry(WORKBOOK_RELS) == null) {
            throw IOException("此 XLSX 缺少工作表关系定义（xl/_rels/workbook.xml.rels），无法预览")
        }
        val output = HashMap<String, Relationship>()
        readPart(zip, WORKBOOK_RELS, "工作簿关系定义", limits, parserFor) { cursor ->
            var event = cursor.event
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && cursor.name == "Relationship") {
                    val id = cursor.attr("Id")
                    val target = cursor.attr("Target")
                    if (id != null && target != null) {
                        output[id] = Relationship(
                            type = cursor.attr("Type").orEmpty(),
                            target = target,
                            external = cursor.attr("TargetMode").equals("External", true),
                        )
                    }
                }
                event = cursor.next()
            }
        }
        return output
    }

    private class CellBudget(val perSheet: Int, val total: Int, private val totalChars: Int) {
        private var usedTotal = 0
        private var usedSheet = 0
        private var usedChars = 0
        fun startSheet() { usedSheet = 0 }
        fun take(): Boolean {
            if (usedSheet >= perSheet || usedTotal >= total) return false
            usedSheet++
            usedTotal++
            return true
        }
        fun takeChars(length: Int): Int {
            val allowed = minOf(length, totalChars - usedChars).coerceAtLeast(0)
            usedChars += allowed
            return allowed
        }
    }

    private class SheetResult(val sheet: XlsxSheet, val truncated: Boolean, val textTruncated: Boolean)

    private class MutableRow(val index: Int, val heightPoints: Float?) {
        val cells = ArrayList<MutableCell>()
    }

    private class MutableCell(
        val row: Int,
        val column: Int,
        var text: String,
        val style: XlsxCellStyle,
        val formula: Boolean,
        val numeric: Boolean,
    ) {
        var covered = false
        var rowSpan = 1
        var columnSpan = 1
    }

    private data class MergeRef(val rowStart: Int, val columnStart: Int, val rowEnd: Int, val columnEnd: Int)

    private fun parseSheet(
        zip: ZipFile,
        reference: SheetReference,
        sharedStrings: List<String>,
        styles: Styles,
        date1904: Boolean,
        limits: Limits,
        parserFor: (InputStream) -> XmlPullParser,
        budget: CellBudget,
    ): SheetResult {
        val rows = LinkedHashMap<Int, MutableRow>()
        val widths = HashMap<Int, Float>()
        val merges = ArrayList<MergeRef>()
        var frozenRows = 0
        var frozenColumns = 0
        var truncated = false
        var textTruncated = false
        var lastRow = -1
        var lastColumn = -1
        var maxUsedRow = -1
        var maxUsedColumn = -1
        var exhausted = false
        var currentRow: MutableRow? = null
        var cellRow = 0
        var cellColumn = 0
        var cellType: String? = null
        var cellStyle: Int? = null
        var cellHasFormula = false
        val value = StringBuilder()
        val inline = StringBuilder()
        var inValue = false
        var inInlineText = false
        var inPhonetic = 0

        fun appendLimited(builder: StringBuilder, text: String) {
            if (text.isEmpty()) return
            val room = limits.maxCellChars - builder.length
            if (room <= 0) {
                textTruncated = true
            } else if (text.length <= room) {
                builder.append(text)
            } else {
                builder.append(text, 0, room)
                textTruncated = true
            }
        }
        readPart(zip, reference.part, "工作表“${reference.name}”", limits, parserFor) { cursor ->
            var event = cursor.event
            scan@ while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (cursor.name) {
                        "row" -> {
                            val index = cursor.intAttr("r")?.minus(1)?.coerceAtLeast(0) ?: (lastRow + 1)
                            lastRow = index
                            lastColumn = -1
                            val row = MutableRow(index, cursor.floatAttr("ht"))
                            currentRow = row
                            if (index < limits.maxRows) rows.getOrPut(index) { row }
                        }
                        "c" -> {
                            val ref = cursor.attr("r")
                            if (ref != null) {
                                val parsed = parseReference(ref)
                                if (parsed != null) {
                                    cellRow = parsed.first
                                    cellColumn = parsed.second
                                } else {
                                    cellRow = (currentRow?.index ?: lastRow).coerceAtLeast(0)
                                    cellColumn = lastColumn + 1
                                }
                            } else {
                                cellRow = (currentRow?.index ?: lastRow).coerceAtLeast(0)
                                cellColumn = lastColumn + 1
                            }
                            lastRow = cellRow
                            lastColumn = cellColumn
                            cellType = cursor.attr("t")
                            cellStyle = cursor.intAttr("s")
                            cellHasFormula = false
                            value.setLength(0)
                            inline.setLength(0)
                        }
                        "v" -> inValue = true
                        "f" -> cellHasFormula = true
                        "is" -> Unit
                        "t" -> inInlineText = true
                        "rPh" -> inPhonetic++
                        "col" -> {
                            val min = cursor.intAttr("min") ?: 1
                            val max = cursor.intAttr("max") ?: min
                            val width = cursor.floatAttr("width")
                            if (width != null && width > 0f) {
                                val start = (min - 1).coerceAtLeast(0)
                                val end = (max - 1).coerceAtMost(limits.maxColumns - 1)
                                for (column in start..end) {
                                    widths[column] = width.coerceIn(0f, 255f)
                                }
                            }
                        }
                        "mergeCell" -> cursor.attr("ref")?.let { ref ->
                            if (merges.size < limits.maxMerges) parseMerge(ref)?.let { merges += it }
                            else truncated = true
                        }
                        "pane" -> {
                            val state = cursor.attr("state")
                            if (state == "frozen" || state == "frozenSplit") {
                                frozenColumns = cursor.intAttr("xSplit")?.coerceAtLeast(0) ?: 0
                                frozenRows = cursor.intAttr("ySplit")?.coerceAtLeast(0) ?: 0
                            }
                        }
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.IGNORABLE_WHITESPACE -> {
                        if (inValue) appendLimited(value, cursor.text())
                        if (inInlineText && inPhonetic == 0) appendLimited(inline, cursor.text())
                    }
                    XmlPullParser.END_TAG -> when (cursor.name) {
                        "v" -> inValue = false
                        "t" -> inInlineText = false
                        "rPh" -> if (inPhonetic > 0) inPhonetic--
                        "c" -> {
                            maxUsedRow = maxOf(maxUsedRow, cellRow)
                            maxUsedColumn = maxOf(maxUsedColumn, cellColumn)
                            if (cellRow >= limits.maxRows || cellColumn >= limits.maxColumns) {
                                truncated = true
                            } else if (!budget.take()) {
                                truncated = true
                                exhausted = true
                            } else {
                                val style = styles.styleAt(cellStyle)
                                val raw = value.toString()
                                val content = when (cellType) {
                                    "s" -> {
                                        val index = raw.trim().toIntOrNull()
                                            ?: throw IOException("此 XLSX 的共享字符串索引无效，文件可能已损坏")
                                        sharedStrings.getOrNull(index)
                                            ?: throw IOException("此 XLSX 的共享字符串索引超出范围，文件可能已损坏")
                                    }
                                    "inlineStr" -> inline.toString()
                                    "b" -> XlsxValueFormatter.booleanText(raw)
                                    "str", "e", "d" -> raw
                                    else -> if (raw.isBlank()) "" else XlsxValueFormatter.number(raw, style.formatCode, date1904)
                                }
                                val perCell = if (content.length > limits.maxCellChars) {
                                    textTruncated = true
                                    content.substring(0, limits.maxCellChars)
                                } else content
                                val allowedChars = budget.takeChars(perCell.length)
                                val clipped = if (allowedChars < perCell.length) {
                                    textTruncated = true
                                    perCell.substring(0, allowedChars)
                                } else perCell
                                val target = rows.getOrPut(cellRow) { MutableRow(cellRow, currentRow?.heightPoints) }
                                val numeric = (cellType == null || cellType == "n") && raw.trim().toDoubleOrNull() != null
                                target.cells += MutableCell(cellRow, cellColumn, clipped, style, cellHasFormula, numeric)
                            }
                        }
                        "sheetData" -> if (exhausted) break@scan
                    }
                }
                if (exhausted) break@scan
                event = cursor.next()
            }
        }

        // 合并区域即使没有单元格数据也要占据行列；超出上限的部分裁剪并标记截断。
        for (merge in merges) {
            if (merge.rowEnd >= limits.maxRows || merge.columnEnd >= limits.maxColumns) truncated = true
            maxUsedRow = maxOf(maxUsedRow, merge.rowEnd.coerceAtMost(limits.maxRows - 1))
            maxUsedColumn = maxOf(maxUsedColumn, merge.columnEnd.coerceAtMost(limits.maxColumns - 1))
        }
        if (maxUsedRow + 1 > limits.maxRows) truncated = true
        if (maxUsedColumn + 1 > limits.maxColumns) truncated = true
        val columnCount = (maxUsedColumn + 1).coerceAtMost(limits.maxColumns).coerceAtLeast(1)
        val rowCount = (maxUsedRow + 1).coerceAtMost(limits.maxRows)
        if (rowCount <= 0) {
            return SheetResult(
                XlsxSheet(reference.name, emptyList(), List(columnCount) { widths[it] }, 0, 0, truncated),
                truncated, textTruncated,
            )
        }
        applyMerges(rows, merges, rowCount, columnCount, limits, styles)
        val sheetRows = (0 until rowCount).map { index ->
            val source = rows[index]
            val cells = source?.cells.orEmpty()
                .filter { it.column < columnCount }
                .sortedBy { it.column }
                .map {
                    XlsxCell(
                        row = it.row, column = it.column, text = it.text, style = it.style,
                        formula = it.formula, covered = it.covered,
                        rowSpan = it.rowSpan, columnSpan = it.columnSpan, numeric = it.numeric,
                    )
                }
            XlsxRow(index, source?.heightPoints, cells)
        }
        val frozenRowCount = frozenRows.coerceIn(0, rowCount)
        val frozenColumnCount = frozenColumns.coerceIn(0, columnCount)
        return SheetResult(
            XlsxSheet(
                reference.name, sheetRows, List(columnCount) { widths[it] },
                frozenRowCount, frozenColumnCount, truncated,
            ),
            truncated, textTruncated,
        )
    }

    /** 合并区域只有起点保存跨度和文本，其余单元格标记 covered；区域按行列上限裁剪。 */
    private fun applyMerges(
        rows: LinkedHashMap<Int, MutableRow>,
        merges: List<MergeRef>,
        rowCount: Int,
        columnCount: Int,
        limits: Limits,
        styles: Styles,
    ) {
        for (merge in merges) {
            val rowStart = merge.rowStart.coerceAtLeast(0)
            val columnStart = merge.columnStart.coerceAtLeast(0)
            val rowEnd = merge.rowEnd.coerceAtMost(rowCount - 1)
            val columnEnd = merge.columnEnd.coerceAtMost(columnCount - 1)
            if (rowStart > rowEnd || columnStart > columnEnd) continue
            if (rowStart >= rowCount || columnStart >= columnCount) continue
            val area = (rowEnd - rowStart + 1).toLong() * (columnEnd - columnStart + 1)
            if (area > limits.maxMergeArea) continue
            val origin = cellsAt(rows, rowStart, columnStart)
                ?: MutableCell(rowStart, columnStart, "", styles.styleAt(null), false, false)
                    .also { rows.getOrPut(rowStart) { MutableRow(rowStart, null) }.cells += it }
            origin.columnSpan = columnEnd - columnStart + 1
            origin.rowSpan = rowEnd - rowStart + 1
            for (row in rowStart..rowEnd) {
                for (column in columnStart..columnEnd) {
                    if (row == rowStart && column == columnStart) continue
                    val covered = cellsAt(rows, row, column)
                        ?: MutableCell(row, column, "", styles.styleAt(null), false, false)
                            .also { rows.getOrPut(row) { MutableRow(row, null) }.cells += it }
                    covered.covered = true
                }
            }
        }
        // 高度按行保留；合并区域不会修改行高，只隐藏被覆盖的单元格。
    }

    private fun cellsAt(rows: Map<Int, MutableRow>, row: Int, column: Int): MutableCell? =
        rows[row]?.cells?.firstOrNull { it.column == column }

    private fun parseMerge(ref: String): MergeRef? {
        val parts = ref.split(':')
        if (parts.size != 2) return null
        val start = parseReference(parts[0]) ?: return null
        val end = parseReference(parts[1]) ?: return null
        if (end.first < start.first || end.second < start.second) return null
        return MergeRef(start.first, start.second, end.first, end.second)
    }

    /** 解析 A1 形式的单元格引用，返回 0 基的 (行, 列)；非法引用返回 null。 */
    private fun parseReference(ref: String): Pair<Int, Int>? {
        val cleaned = ref.trim().uppercase(Locale.ROOT)
        val split = cleaned.indexOfFirst { it.isDigit() }
        // Excel 最大列 XFD 只有三个字母；限制长度同时避免恶意超长引用造成整数溢出。
        if (split <= 0 || split > 3 || split >= cleaned.length) return null
        var column = 0
        for (index in 0 until split) {
            val char = cleaned[index]
            if (char !in 'A'..'Z') return null
            column = column * 26 + (char - 'A' + 1)
        }
        val row = cleaned.substring(split).toIntOrNull() ?: return null
        if (row <= 0 || column <= 0) return null
        return (row - 1) to (column - 1)
    }

    /** 不信任中央目录声明的解压后大小，实际读取超过单条目上限立即停止。 */
    private class LimitedInputStream(
        input: InputStream,
        private val limit: Long,
        private val label: String,
    ) : FilterInputStream(input) {
        private var count = 0L

        private fun guard(read: Long) {
            if (read <= 0) return
            count += read
            if (count > limit) throw IOException("此 XLSX 的${label}解压后超过上限，已停止预览")
        }

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) guard(1)
            return value
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(bytes, offset, length)
            guard(read.toLong())
            return read
        }

        override fun skip(amount: Long): Long {
            val skipped = super.skip(amount)
            guard(skipped)
            return skipped
        }
    }
}
