package com.tanuki

import com.tanuki.storage.XlsxHorizontalAlign
import com.tanuki.storage.XlsxVerticalAlign
import com.tanuki.storage.XlsxWorkbookParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 用合成 OOXML 压缩包验证 XLSX 只读解析、样式/合并/冻结和全部安全上限。 */
class XlsxWorkbookParserTest {
    private val parserFor: (InputStream) -> XmlPullParser = { input ->
        KXmlParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(input, null)
        }
    }

    private val workbookHeader = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>"""

    private fun escape(text: String): String = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun worksheet(content: String): String =
        """$workbookHeader<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">$content</worksheet>"""

    private fun workbookXml(sheetTags: String, date1904: Boolean): String =
        """$workbookHeader<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
            (if (date1904) """<workbookPr date1904="1"/>""" else "") +
            "<sheets>$sheetTags</sheets></workbook>"

    private fun sharedStringsXml(strings: List<String>): String =
        """$workbookHeader<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
            """count="${strings.size}" uniqueCount="${strings.size}">""" +
            strings.joinToString("") { "<si><t>${escape(it)}</t></si>" } + "</sst>"

    private fun zipOf(entries: List<Pair<String, ByteArray>>): File {
        val file = Files.createTempFile("tanuki-xlsx", ".xlsx").toFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun build(
        sheets: List<Pair<String, String>>,
        sharedStrings: List<String>? = null,
        sharedStringsXml: String? = null,
        styles: String? = null,
        date1904: Boolean = false,
        targets: List<String>? = null,
        relationshipsXml: String? = null,
        overrideFirstSheet: String? = null,
        extra: List<Pair<String, ByteArray>> = emptyList(),
    ): File {
        val sheetTags = sheets.mapIndexed { index, pair ->
            """<sheet name="${escape(pair.first)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>"""
        }.joinToString("")
        val relationships = relationshipsXml ?: buildString {
            append(workbookHeader)
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            sheets.forEachIndexed { index, _ ->
                val target = targets?.getOrNull(index) ?: "worksheets/sheet${index + 1}.xml"
                append(
                    """<Relationship Id="rId${index + 1}" """ +
                        """Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" """ +
                        """Target="${escape(target)}"/>""",
                )
            }
            append("</Relationships>")
        }
        val entries = ArrayList<Pair<String, ByteArray>>()
        entries += "[Content_Types].xml" to
            """$workbookHeader<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"/>""".toByteArray()
        entries += "xl/workbook.xml" to workbookXml(sheetTags, date1904).toByteArray()
        entries += "xl/_rels/workbook.xml.rels" to relationships.toByteArray()
        when {
            sharedStringsXml != null -> entries += "xl/sharedStrings.xml" to sharedStringsXml.toByteArray()
            sharedStrings != null -> entries += "xl/sharedStrings.xml" to sharedStringsXml(sharedStrings).toByteArray()
        }
        if (styles != null) entries += "xl/styles.xml" to styles.toByteArray()
        sheets.forEachIndexed { index, pair ->
            val content = if (index == 0 && overrideFirstSheet != null) overrideFirstSheet else worksheet(pair.second)
            entries += "xl/worksheets/sheet${index + 1}.xml" to content.toByteArray()
        }
        entries += extra
        return zipOf(entries)
    }

    private fun parse(file: File, limits: XlsxWorkbookParser.Limits = XlsxWorkbookParser.Limits()) =
        XlsxWorkbookParser.parse(file, limits, parserFor)

    private fun assertChineseFailure(file: File, limits: XlsxWorkbookParser.Limits = XlsxWorkbookParser.Limits(), contains: String) {
        try {
            parse(file, limits)
            fail("应当拒绝此 XLSX")
        } catch (failure: IOException) {
            val message = failure.message.orEmpty()
            assertTrue("错误信息应为中文并可操作：$message", message.any { it in '\u4e00'..'\u9fff' })
            assertTrue("错误信息应包含“$contains”，实际：$message", message.contains(contains))
        } finally {
            file.delete()
        }
    }

    @Test
    fun parsesMultipleSheetsInWorkbookOrder() {
        val file = build(
            listOf(
                "第一张" to """<sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>甲</t></is></c></row></sheetData>""",
                "Second" to """<sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>乙</t></is></c></row></sheetData>""",
            ),
        )
        try {
            val workbook = parse(file)
            assertEquals(listOf("第一张", "Second"), workbook.sheets.map { it.name })
            assertEquals("甲", workbook.sheets[0].rows[0].cells[0].text)
            assertEquals("乙", workbook.sheets[1].rows[0].cells[0].text)
        } finally {
            file.delete()
        }
    }

    @Test
    fun parsesSharedInlineBooleanErrorNumberFormulaAndCachedValues() {
        val styles = """$workbookHeader<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<numFmts count="1"><numFmt numFmtId="164" formatCode="0.00%"/></numFmts>""" +
            """<fonts count="1"><font><sz val="11"/></font></fonts>""" +
            """<fills count="1"><fill><patternFill patternType="none"/></fill></fills>""" +
            """<borders count="1"><border/></borders>""" +
            """<cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>""" +
            """<xf numFmtId="14" fontId="0" fillId="0" borderId="0"/>""" +
            """<xf numFmtId="164" fontId="0" fillId="0" borderId="0"/></cellXfs></styleSheet>"""
        val sheet = """<sheetData>""" +
            """<row r="1">""" +
            """<c r="A1" t="s"><v>0</v></c>""" +
            """<c r="B1" t="inlineStr"><is><t>内联</t></is></c>""" +
            """<c r="C1" t="b"><v>1</v></c>""" +
            """<c r="D1" t="e"><v>#DIV/0!</v></c>""" +
            """<c r="E1"><v>42</v></c>""" +
            """<c r="F1"><f>SUM(E1:E2)</f><v>3.5</v></c>""" +
            """<c r="G1" t="str"><f>CONCAT(A1,B1)</f><v>缓存</v></c>""" +
            """<c r="H1" s="1"><v>44927</v></c>""" +
            """<c r="I1" s="2"><v>0.125</v></c>""" +
            """</row></sheetData>"""
        val file = build(listOf("类型" to sheet), sharedStrings = listOf("共享"), styles = styles)
        try {
            val row = parse(file).sheets.single().rows.single()
            assertEquals(9, row.cells.size)
            assertEquals("共享", row.cells[0].text)
            assertEquals("内联", row.cells[1].text)
            assertEquals("TRUE", row.cells[2].text)
            assertEquals("#DIV/0!", row.cells[3].text)
            assertEquals("42", row.cells[4].text)
            assertEquals("3.5", row.cells[5].text)
            assertTrue("公式单元格应保留缓存值并标记公式", row.cells[5].formula)
            assertEquals("缓存", row.cells[6].text)
            assertEquals("2023-01-01", row.cells[7].text)
            assertEquals("12.50%", row.cells[8].text)
        } finally {
            file.delete()
        }
    }

    @Test
    fun preservesResolvedEntitiesInSharedInlineAndCachedStrings() {
        val shared = """$workbookHeader<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<si><t>共&amp;享&#65;</t></si></sst>"""
        val sheet = """<sheetData><row r="1">""" +
            """<c r="A1" t="s"><v>0</v></c>""" +
            """<c r="B1" t="inlineStr"><is><t>内&amp;联&#66;</t></is></c>""" +
            """<c r="C1" t="str"><f>CONCAT(A1,B1)</f><v>缓&amp;存&#67;</v></c>""" +
            """</row></sheetData>"""
        val file = build(listOf("实体" to sheet), sharedStringsXml = shared)
        try {
            val cells = parse(file).sheets.single().rows.single().cells
            assertEquals("共&享A", cells[0].text)
            assertEquals("内&联B", cells[1].text)
            assertEquals("缓&存C", cells[2].text)
        } finally {
            file.delete()
        }
    }

    @Test
    fun appliesBaseFontFillBorderAndAlignmentStyles() {
        val styles = """$workbookHeader<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<fonts count="2"><font><sz val="11"/></font>""" +
            """<font><b/><i/><u/><sz val="16"/><color rgb="FFFF0000"/></font></fonts>""" +
            """<fills count="2"><fill><patternFill patternType="none"/></fill>""" +
            """<fill><patternFill patternType="solid"><fgColor rgb="FF00FF00"/></patternFill></fill></fills>""" +
            """<borders count="2"><border/>""" +
            """<border><left style="thin"><color rgb="FF0000FF"/></left><right style="thin"/>""" +
            """<top style="thin"/><bottom style="thin"/></border></borders>""" +
            """<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>""" +
            """<xf numFmtId="0" fontId="1" fillId="1" borderId="1"><alignment horizontal="center" vertical="top"/></xf>""" +
            """</cellXfs></styleSheet>"""
        val sheet = """<sheetData><row r="2"><c r="B2" s="1" t="inlineStr"><is><t>样式</t></is></c></row></sheetData>"""
        val file = build(listOf("样式" to sheet), styles = styles)
        try {
            val cell = parse(file).sheets.single().rows.first { it.cells.isNotEmpty() }.cells.single()
            assertEquals("样式", cell.text)
            assertTrue(cell.style.bold)
            assertTrue(cell.style.italic)
            assertTrue(cell.style.underline)
            assertEquals(16f, cell.style.fontSize)
            assertEquals(0xFFFF0000.toInt(), cell.style.fontColor)
            assertEquals(0xFF00FF00.toInt(), cell.style.fillColor)
            val border = cell.style.border
            assertNotNull(border)
            assertTrue(border!!.left && border.right && border.top && border.bottom)
            assertEquals(0xFF0000FF.toInt(), border.color)
            assertEquals(XlsxHorizontalAlign.CENTER, cell.style.horizontal)
            assertEquals(XlsxVerticalAlign.TOP, cell.style.vertical)
        } finally {
            file.delete()
        }
    }

    @Test
    fun readsColumnWidthsRowHeightsMergedCellsAndFrozenPane() {
        val content =
            """<sheetViews><sheetView workbookViewId="0">""" +
                """<pane xSplit="1" ySplit="2" topLeftCell="B3" activePane="bottomRight" state="frozen"/>""" +
                """</sheetView></sheetViews>""" +
                """<cols><col min="1" max="2" width="20" customWidth="1"/></cols>""" +
                """<sheetData><row r="1" ht="30" customHeight="1"><c r="A1" t="inlineStr"><is><t>合并起点</t></is></c></row>""" +
                """<row r="2"><c r="B2" t="inlineStr"><is><t>被覆盖</t></is></c></row></sheetData>""" +
                """<mergeCells count="1"><mergeCell ref="A1:C2"/></mergeCells>"""
        val file = build(listOf("合并" to content))
        try {
            val parsed = parse(file).sheets.single()
            assertEquals(3, parsed.columnCount)
            assertEquals(20f, parsed.columnWidths[0]!!)
            assertEquals(20f, parsed.columnWidths[1]!!)
            assertNull(parsed.columnWidths[2])
            assertEquals(30f, parsed.rows[0].heightPoints!!)
            assertEquals(2, parsed.frozenRows)
            assertEquals(1, parsed.frozenColumns)
            val origin = parsed.rows[0].cells.first { it.column == 0 }
            assertEquals(3, origin.columnSpan)
            assertEquals(2, origin.rowSpan)
            assertEquals("合并起点", origin.text)
            val covered = parsed.rows[1].cells.first { it.column == 1 }
            assertTrue("合并区域内的单元格应标记 covered", covered.covered)
            assertEquals("被覆盖", covered.text)
            // 合并区域扩展了显示范围，B1/C1/B2/C2 都应有占位单元格。
            assertTrue(parsed.rows[0].cells.count { it.covered } >= 2)
        } finally {
            file.delete()
        }
    }

    @Test
    fun truncatesOversizedSheetAtRowAndColumnLimits() {
        val rows = (1..10).joinToString("") { row ->
            """<row r="$row">""" + (1..6).joinToString("") { column ->
                """<c r="${'A' + (column - 1)}$row"><v>$row</v></c>"""
            } + "</row>"
        }
        val file = build(listOf("超大" to """<sheetData>$rows</sheetData>"""))
        try {
            val sheet = parse(file, XlsxWorkbookParser.Limits(maxRows = 3, maxColumns = 4)).sheets.single()
            assertTrue(sheet.truncated)
            assertEquals(3, sheet.rows.size)
            assertEquals(4, sheet.columnCount)
            assertTrue(sheet.rows.all { row -> row.cells.all { it.column < 4 } })
        } finally {
            file.delete()
        }
    }

    @Test
    fun truncatesWhenCellBudgetIsExhausted() {
        val sheet = """<sheetData><row r="1">""" +
            (1..10).joinToString("") { column -> """<c r="${'A' + (column - 1)}1"><v>$column</v></c>""" } +
            """</row></sheetData>"""
        val file = build(listOf("预算" to sheet))
        try {
            val parsed = parse(file, XlsxWorkbookParser.Limits(maxCellsPerSheet = 2)).sheets.single()
            assertTrue(parsed.truncated)
            assertTrue(parsed.rows.sumOf { it.cells.size } <= 2)
        } finally {
            file.delete()
        }
    }

    @Test
    fun mergePlaceholdersShareCellBudgetAcrossSheets() {
        val merges = """<mergeCells count="2"><mergeCell ref="A1:J10"/><mergeCell ref="A12:J21"/></mergeCells>"""
        val file = build(
            listOf("一" to """<sheetData/>$merges""", "二" to """<sheetData/>$merges"""),
        )
        try {
            val parsed = parse(file, XlsxWorkbookParser.Limits(maxCellsPerSheet = 10, maxTotalCells = 15))
            assertTrue("合并占位超出预算时工作簿应标记截断", parsed.truncated)
            assertTrue(parsed.sheets.all { it.truncated })
            // 单表最多 10 格；全簿最多 15 格，第二张表只能再拿 5 格。
            assertTrue(parsed.sheets[0].rows.sumOf { it.cells.size } <= 10)
            assertTrue(parsed.sheets[1].rows.sumOf { it.cells.size } <= 5)
            assertTrue(parsed.sheets.sumOf { sheet -> sheet.rows.sumOf { it.cells.size } } <= 15)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsNonZipMissingWorkbookAndMalformedSheetWithChineseMessages() {
        val notZip = Files.createTempFile("tanuki-xlsx-corrupt", ".xlsx").toFile()
        notZip.writeText("这不是 ZIP")
        assertChineseFailure(notZip, contains = "XLSX")

        val missing = zipOf(listOf("xl/worksheets/sheet1.xml" to "<worksheet/>".toByteArray()))
        assertChineseFailure(missing, contains = "工作簿")

        val malformed = build(
            listOf("坏表" to ""),
            overrideFirstSheet = """<worksheet><sheetData><row r="1"><c r="A1"><v>1</v></row></sheetData></worksheet>""",
        )
        assertChineseFailure(malformed, contains = "格式不正确")
    }

    @Test
    fun rejectsZipEntryCountTotalSizeAndPartSizeLimits() {
        val content = """<sheetData><row r="1"><c r="A1"><v>1</v></c></row></sheetData>"""
        val tooMany = build(
            listOf("限制" to content),
            extra = (1..5).map { """xl/custom/part$it.xml""" to "<x/>".toByteArray() },
        )
        assertChineseFailure(tooMany, XlsxWorkbookParser.Limits(maxEntries = 2), contains = "条目过多")

        val tooLarge = build(listOf("限制" to content))
        assertChineseFailure(tooLarge, XlsxWorkbookParser.Limits(maxTotalUncompressedBytes = 10), contains = "解压后过大")

        val partTooLarge = build(listOf("限制" to content))
        assertChineseFailure(partTooLarge, XlsxWorkbookParser.Limits(maxPartBytes = 100), contains = "过大")
    }

    /** 只改中央目录的“解压后大小”，让声明值小于真实值，验证读取时的实际字节上限。 */
    private fun forgeUncompressedSize(file: File, target: String, claimed: Int) {
        val bytes = file.readBytes()
        var offset = 0
        while (offset + 46 <= bytes.size) {
            if (bytes[offset] == 0x50.toByte() && bytes[offset + 1] == 0x4b.toByte() &&
                bytes[offset + 2] == 0x01.toByte() && bytes[offset + 3] == 0x02.toByte()
            ) {
                val nameLength = (bytes[offset + 28].toInt() and 0xff) or ((bytes[offset + 29].toInt() and 0xff) shl 8)
                val name = String(bytes, offset + 46, nameLength, Charsets.UTF_8)
                if (name == target) {
                    for (index in 0 until 4) bytes[offset + 24 + index] = ((claimed ushr (8 * index)) and 0xff).toByte()
                    file.writeBytes(bytes)
                    return
                }
            }
            offset++
        }
        fail("未找到中央目录条目：$target")
    }

    @Test
    fun stopsReadingWhenZipEntryLiesAboutUncompressedSize() {
        val huge = (1..80).joinToString("") { "<si><t>${"膨".repeat(100)}</t></si>" }
        val file = build(
            listOf("bomb" to """<sheetData/>"""),
            sharedStringsXml = """$workbookHeader<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">$huge</sst>""",
        )
        forgeUncompressedSize(file, "xl/sharedStrings.xml", 32)
        assertChineseFailure(file, XlsxWorkbookParser.Limits(maxPartBytes = 2048), contains = "超过上限")
    }

    @Test
    fun rejectsDeclaredOversizedPartBeforeDecompressing() {
        val huge = (1..200).joinToString("") { "<si><t>${"膨".repeat(200)}</t></si>" }
        val file = build(
            listOf("bomb" to """<sheetData/>"""),
            sharedStringsXml = """$workbookHeader<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">$huge</sst>""",
        )
        assertChineseFailure(file, XlsxWorkbookParser.Limits(maxPartBytes = 4096), contains = "过大")
    }

    @Test
    fun rejectsTooManySharedStringsAndTruncatesCellText() {
        val two = build(
            listOf("共享" to """<sheetData><row r="1"><c r="A1" t="s"><v>0</v></c></row></sheetData>"""),
            sharedStrings = listOf("一", "二"),
        )
        assertChineseFailure(two, XlsxWorkbookParser.Limits(maxSharedStrings = 1), contains = "共享字符串数量")

        val longText = "长".repeat(50)
        val file = build(
            listOf("长文本" to """<sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>$longText</t></is></c></row></sheetData>"""),
        )
        try {
            val parsed = parse(file, XlsxWorkbookParser.Limits(maxCellChars = 10))
            assertTrue(parsed.textTruncated)
            assertEquals(10, parsed.sheets.single().rows.single().cells.single().text.length)
            // 单格上限之外还有整个工作簿的文本预算，避免大量单元格共同占满内存。
            val budgeted = parse(file, XlsxWorkbookParser.Limits(maxCellChars = 10, maxTotalCellChars = 5))
            assertTrue(budgeted.textTruncated)
            assertEquals(5, budgeted.sheets.single().rows.single().cells.single().text.length)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsPathTraversalAndExternalWorkbookRelationships() {
        val traversal = build(
            listOf("越界" to """<sheetData/>"""),
            relationshipsXml = workbookHeader +
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" """ +
                """Target="../../../etc/passwd"/></Relationships>""",
        )
        assertChineseFailure(traversal, contains = "路径")

        val external = build(
            listOf("外部" to """<sheetData/>"""),
            relationshipsXml = workbookHeader +
                """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" """ +
                """Target="https://example.com/sheet.xml" TargetMode="External"/></Relationships>""",
        )
        assertChineseFailure(external, contains = "外部")
    }

    @Test
    fun acceptsAbsoluteInPackageTargets() {
        val file = build(
            listOf("绝对" to """<sheetData><row r="1"><c r="A1"><v>7</v></c></row></sheetData>"""),
            targets = listOf("/xl/worksheets/sheet1.xml"),
        )
        try {
            assertEquals("7", parse(file).sheets.single().rows.single().cells.single().text)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsDoctypeDeepXmlAndUndefinedEntities() {
        val doctype = build(
            listOf("DTD" to """<sheetData/>"""),
            sharedStringsXml = """<?xml version="1.0" encoding="UTF-8"?><!DOCTYPE sst>""" +
                """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>x</t></si></sst>""",
        )
        assertChineseFailure(doctype, contains = "文档类型声明")

        val deep = build(
            listOf(
                "深层" to """<sheetData><row r="1">${"<x>".repeat(20)}${"</x>".repeat(20)}</row></sheetData>""",
            ),
        )
        assertChineseFailure(deep, XlsxWorkbookParser.Limits(maxXmlDepth = 8), contains = "层级过深")

        val entity = build(
            listOf("实体" to """<sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>A&missing;B</t></is></c></row></sheetData>"""),
        )
        assertChineseFailure(entity, contains = "格式不正确")
    }

    @Test
    fun honours1904DateSystemAndDefaultStyleIndex() {
        val styles = """$workbookHeader<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<fonts count="1"><font><sz val="11"/></font></fonts>""" +
            """<fills count="1"><fill><patternFill patternType="none"/></fill></fills>""" +
            """<borders count="1"><border/></borders>""" +
            """<cellXfs count="1"><xf numFmtId="14" fontId="0" fillId="0" borderId="0"/></cellXfs></styleSheet>"""
        val file = build(
            listOf("1904" to """<sheetData><row r="1"><c r="A1"><v>0</v></c></row></sheetData>"""),
            styles = styles,
            date1904 = true,
        )
        try {
            val workbook = parse(file)
            assertTrue(workbook.date1904)
            // 省略 s 属性时使用 cellXfs[0] 的日期格式，而不是无格式数字。
            assertEquals("1904-01-01", workbook.sheets.single().rows.single().cells.single().text)
        } finally {
            file.delete()
        }
    }

    @Test
    fun emptySheetStillRendersOneColumnAndNoRows() {
        val file = build(listOf("空表" to """<sheetData/>"""))
        try {
            val sheet = parse(file).sheets.single()
            assertEquals(0, sheet.rows.size)
            assertEquals(1, sheet.columnCount)
            assertFalse(sheet.truncated)
        } finally {
            file.delete()
        }
    }
}
