package com.tanuki

import com.tanuki.storage.docx.DocxAlignment
import com.tanuki.storage.docx.DocxBlock
import com.tanuki.storage.docx.DocxDocument
import com.tanuki.storage.docx.DocxInline
import com.tanuki.storage.docx.DocxLineSpacing
import com.tanuki.storage.docx.DocxLimits
import com.tanuki.storage.docx.DocxPageSetup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DocxDocumentReaderTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun read(
        document: String,
        styles: String? = null,
        numbering: String? = null,
        extra: Map<String, ByteArray> = emptyMap(),
        limits: DocxLimits = DocxLimits(),
    ): DocxDocument {
        val files = linkedMapOf<String, ByteArray>()
        files["word/document.xml"] = document.toByteArray()
        if (styles != null) files["word/styles.xml"] = styles.toByteArray()
        if (numbering != null) files["word/numbering.xml"] = numbering.toByteArray()
        files.putAll(extra)
        val file: File = temporary.newFile("doc.docx")
        DocxFixtures.write(file, files)
        return DocxFixtures.read(file, limits)
    }

    private fun paragraph(doc: DocxDocument, index: Int = 0): DocxBlock.Paragraph =
        doc.blocks.filterIsInstance<DocxBlock.Paragraph>()[index]

    @Test
    fun headingStyleResolvesLevelBoldSizeColorAlignmentAndSpacing() {
        val styles = DocxFixtures.styles(
            """<w:style w:type="paragraph" w:styleId="Body" w:default="1"><w:name w:val="Normal"/></w:style>
               <w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Body"/>
                 <w:pPr><w:jc w:val="center"/><w:spacing w:before="240" w:after="120"/></w:pPr>
                 <w:rPr><w:b/><w:sz w:val="36"/><w:color w:val="FF0000"/></w:rPr></w:style>""",
        )
        val doc = read(
            DocxFixtures.document(DocxFixtures.paragraph("标题", pPr = "<w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>")),
            styles = styles,
        )
        val style = paragraph(doc).style
        assertEquals(1, style.headingLevel)
        assertTrue(style.defaultRun.bold)
        assertEquals(36, style.defaultRun.fontSizeHalfPoints)
        assertEquals(0xFFFF0000.toInt(), style.defaultRun.colorArgb)
        assertEquals(DocxAlignment.CENTER, style.alignment)
        assertEquals(240, style.spaceBeforeTwips)
        assertEquals(120, style.spaceAfterTwips)
    }

    @Test
    fun directRunFormattingOverridesHeadingStyleAndCanDisableBold() {
        val styles = DocxFixtures.styles(
            """<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/>
                 <w:rPr><w:b/><w:sz w:val="36"/></w:rPr></w:style>""",
        )
        val runProps = """<w:rPr><w:b w:val="0"/><w:i/><w:u w:val="single"/><w:sz w:val="28"/><w:color w:val="00FF00"/></w:rPr>"""
        val doc = read(
            DocxFixtures.document(
                DocxFixtures.paragraph("正文", pPr = "<w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr>", rPr = runProps),
            ),
            styles = styles,
        )
        val run = paragraph(doc).inlines.single() as DocxInline.Text
        assertFalse(run.style.bold)
        assertTrue(run.style.italic)
        assertTrue(run.style.underline)
        assertEquals(28, run.style.fontSizeHalfPoints)
        assertEquals(0xFF00FF00.toInt(), run.style.colorArgb)
    }

    @Test
    fun characterStyleIsMergedUnderDirectRunFormatting() {
        val styles = DocxFixtures.styles(
            """<w:style w:type="character" w:styleId="Emph"><w:name w:val="Emphasis"/>
                 <w:rPr><w:i/><w:color w:val="0000FF"/></w:rPr></w:style>""",
        )
        val doc = read(
            DocxFixtures.document(
                DocxFixtures.paragraph("强调", rPr = "<w:rPr><w:rStyle w:val=\"Emph\"/><w:u w:val=\"single\"/></w:rPr>"),
            ),
            styles = styles,
        )
        val run = paragraph(doc).inlines.single() as DocxInline.Text
        assertTrue(run.style.italic)
        assertTrue(run.style.underline)
        assertEquals(0xFF0000FF.toInt(), run.style.colorArgb)
    }

    @Test
    fun docDefaultsApplyWhenParagraphHasNoStyle() {
        val styles = DocxFixtures.styles(
            """<w:docDefaults><w:rPrDefault><w:rPr><w:sz w:val="20"/><w:color w:val="333333"/></w:rPr></w:rPrDefault></w:docDefaults>""",
        )
        val doc = read(DocxFixtures.document(DocxFixtures.paragraph("默认")), styles = styles)
        val run = paragraph(doc).inlines.single() as DocxInline.Text
        assertEquals(20, run.style.fontSizeHalfPoints)
        assertEquals(0xFF333333.toInt(), run.style.colorArgb)
    }

    @Test
    fun outlineLevelCreatesHeadingFallbackWithoutNamedStyle() {
        val doc = read(
            DocxFixtures.document(
                DocxFixtures.paragraph("二级", pPr = "<w:pPr><w:outlineLvl w:val=\"1\"/></w:pPr>"),
            ),
        )
        val style = paragraph(doc).style
        assertEquals(2, style.headingLevel)
        assertTrue(style.defaultRun.bold)
        assertEquals(26, style.defaultRun.fontSizeHalfPoints)
    }

    @Test
    fun paragraphLineSpacingIndentAndJustificationAreParsed() {
        val pPr = """<w:pPr><w:jc w:val="both"/><w:spacing w:line="360" w:lineRule="auto"/>
            <w:ind w:left="720" w:firstLine="480"/></w:pPr>"""
        val doc = read(DocxFixtures.document(DocxFixtures.paragraph("排版", pPr = pPr)))
        val style = paragraph(doc).style
        assertEquals(DocxAlignment.JUSTIFY, style.alignment)
        assertEquals(720, style.indentLeftTwips)
        assertEquals(480, style.indentFirstLineTwips)
        assertEquals(DocxLineSpacing.Auto(360), style.lineSpacing)
    }

    @Test
    fun decimalAndLetterNumberingIncrementPerLevel() {
        val numbering = DocxFixtures.numbering(
            """<w:abstractNum w:abstractNumId="0">
                 <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/>
                   <w:pPr><w:ind w:left="720" w:hanging="360"/></w:pPr></w:lvl>
                 <w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="lowerLetter"/><w:lvlText w:val="%2)"/></w:lvl>
               </w:abstractNum><w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>""",
        )
        fun item(text: String, level: Int) = DocxFixtures.paragraph(
            text,
            pPr = "<w:pPr><w:numPr><w:ilvl w:val=\"$level\"/><w:numId w:val=\"1\"/></w:numPr></w:pPr>",
        )
        val doc = read(DocxFixtures.document(item("一", 0) + item("二", 0) + item("甲", 1)), numbering = numbering)
        val markers = doc.blocks.filterIsInstance<DocxBlock.Paragraph>().map { it.numbering?.text }
        assertEquals(listOf("1.", "2.", "a)"), markers)
        assertEquals(720, doc.blocks.filterIsInstance<DocxBlock.Paragraph>()[0].numbering?.indentLeftTwips)
    }

    @Test
    fun nestedNumberingReplacesAllPlaceholders() {
        val numbering = DocxFixtures.numbering(
            """<w:abstractNum w:abstractNumId="0">
                 <w:lvl w:ilvl="0"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1"/></w:lvl>
                 <w:lvl w:ilvl="1"><w:start w:val="1"/><w:numFmt w:val="decimal"/><w:lvlText w:val="%1.%2"/></w:lvl>
               </w:abstractNum><w:num w:numId="2"><w:abstractNumId w:val="0"/></w:num>""",
        )
        fun item(level: Int) = DocxFixtures.paragraph(
            "项目",
            pPr = "<w:pPr><w:numPr><w:ilvl w:val=\"$level\"/><w:numId w:val=\"2\"/></w:numPr></w:pPr>",
        )
        val doc = read(DocxFixtures.document(item(0) + item(0) + item(1)), numbering = numbering)
        val markers = doc.blocks.filterIsInstance<DocxBlock.Paragraph>().map { it.numbering?.text }
        assertEquals(listOf("1", "2", "2.1"), markers)
    }

    @Test
    fun bulletNumberingUsesGlyphFallback() {
        val numbering = DocxFixtures.numbering(
            """<w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:start w:val="1"/>
                 <w:numFmt w:val="bullet"/><w:lvlText w:val=""/></w:lvl></w:abstractNum>
               <w:num w:numId="3"><w:abstractNumId w:val="0"/></w:num>""",
        )
        val pPr = "<w:pPr><w:numPr><w:ilvl w:val=\"0\"/><w:numId w:val=\"3\"/></w:numPr></w:pPr>"
        val doc = read(DocxFixtures.document(DocxFixtures.paragraph("条目", pPr = pPr)), numbering = numbering)
        val marker = paragraph(doc).numbering
        assertNotNull(marker)
        assertTrue(marker!!.bullet)
        assertEquals("\u2022", marker.text)
    }

    @Test
    fun numberingDefinedOnParagraphStyleAppliesToParagraph() {
        val styles = DocxFixtures.styles(
            """<w:style w:type="paragraph" w:styleId="List"><w:name w:val="List"/>
                 <w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr></w:style>""",
        )
        val numbering = DocxFixtures.numbering(
            """<w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:start w:val="1"/>
                 <w:numFmt w:val="decimal"/><w:lvlText w:val="%1."/></w:lvl></w:abstractNum>
               <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>""",
        )
        val doc = read(
            DocxFixtures.document(DocxFixtures.paragraph("样式列表", pPr = "<w:pPr><w:pStyle w:val=\"List\"/></w:pPr>")),
            styles = styles,
            numbering = numbering,
        )
        assertEquals("1.", paragraph(doc).numbering?.text)
    }

    @Test
    fun tableGridSpansHeaderShadingAndCellParagraphsAreParsed() {
        val table = DocxFixtures.table(
            DocxFixtures.row(
                DocxFixtures.cell(
                    DocxFixtures.paragraph("合并"),
                    props = "<w:gridSpan w:val=\"2\"/><w:tcW w:w=\"6000\"/><w:shd w:fill=\"EEEEEE\"/>",
                ),
                header = true,
            ),
            DocxFixtures.row(
                DocxFixtures.cell(DocxFixtures.paragraph("A") + DocxFixtures.paragraph("B")),
                DocxFixtures.cell(DocxFixtures.paragraph("C")),
            ),
            grid = listOf(2000, 4000),
        )
        val doc = read(DocxFixtures.document(table))
        val parsed = doc.blocks.single() as DocxBlock.Table
        assertEquals(listOf(2000, 4000), parsed.columnWidthsTwips)
        assertEquals(2, parsed.rows.size)
        assertTrue(parsed.headerRow)
        assertTrue(parsed.rows[0].header)
        assertEquals(2, parsed.rows[0].cells[0].colSpan)
        assertEquals(0xFFEEEEEE.toInt(), parsed.rows[0].cells[0].shadeArgb)
        assertEquals(2, parsed.rows[1].cells[0].blocks.size)
        assertEquals(1, doc.tableCount)
        // 表格单元格内的段落也计入段落数。
        assertEquals(4, doc.paragraphCount)
    }

    @Test
    fun drawingImageResolvesRelationshipAndMediaBytes() {
        val rels = DocxFixtures.relationships(DocxFixtures.imageRelationship("rId5", "media/image1.png"))
        val doc = read(
            DocxFixtures.document(DocxFixtures.drawing("rId5", 914400, 457200, "示意图")),
            extra = mapOf(
                "word/_rels/document.xml.rels" to rels.toByteArray(),
                "word/media/image1.png" to DocxFixtures.PNG,
            ),
        )
        val image = paragraph(doc).inlines.filterIsInstance<DocxInline.Image>().single().image
        assertEquals("rId5", image.relationshipId)
        assertEquals("word/media/image1.png", image.entryName)
        assertEquals(914400L, image.widthEmu)
        assertEquals(457200L, image.heightEmu)
        assertEquals("示意图", image.altText)
        assertTrue(doc.media["word/media/image1.png"]!!.contentEquals(DocxFixtures.PNG))
        assertEquals(1, doc.imageCount)
    }

    @Test
    fun vmlImageIsSupportedThroughImagedataRelationship() {
        val rels = DocxFixtures.relationships(DocxFixtures.imageRelationship("rId5", "media/legacy.png"))
        val doc = read(
            DocxFixtures.document(DocxFixtures.vmlDrawing("rId5")),
            extra = mapOf(
                "word/_rels/document.xml.rels" to rels.toByteArray(),
                "word/media/legacy.png" to DocxFixtures.PNG,
            ),
        )
        val image = paragraph(doc).inlines.filterIsInstance<DocxInline.Image>().single().image
        assertEquals("word/media/legacy.png", image.entryName)
    }

    @Test
    fun externalImageIsNotLoaded() {
        val rels = DocxFixtures.relationships(
            DocxFixtures.imageRelationship("rId5", "https://example.com/a.png", external = true),
        )
        val doc = read(
            DocxFixtures.document(DocxFixtures.drawing("rId5")),
            extra = mapOf("word/_rels/document.xml.rels" to rels.toByteArray()),
        )
        val image = paragraph(doc).inlines.filterIsInstance<DocxInline.Image>().single().image
        assertNull(image.entryName)
        assertTrue(doc.media.isEmpty())
        assertTrue(doc.warnings.any { it.contains("外部图片") })
    }

    @Test
    fun missingMediaEntryIsReportedWithoutFailingDocument() {
        val rels = DocxFixtures.relationships(DocxFixtures.imageRelationship("rId5", "media/missing.png"))
        val doc = read(
            DocxFixtures.document(DocxFixtures.drawing("rId5")),
            extra = mapOf("word/_rels/document.xml.rels" to rels.toByteArray()),
        )
        val image = paragraph(doc).inlines.filterIsInstance<DocxInline.Image>().single().image
        assertNull(image.entryName)
        assertTrue(doc.warnings.any { it.contains("缺失") })
    }

    @Test
    fun sectionPropertiesControlPaperSizeAndMargins() {
        val section = DocxFixtures.sectPr(width = 16838, height = 11906, left = 720, right = 720, top = 360, bottom = 360)
        val doc = read(DocxFixtures.document(DocxFixtures.paragraph("横向"), section = section))
        assertEquals(DocxPageSetup(16838, 11906, 720, 720, 360, 360), doc.page)
        assertEquals(15398, doc.page.contentWidthTwips)
        assertEquals(11186, doc.page.contentHeightTwips)
    }

    @Test
    fun missingSectionPropertyFallsBackToA4() {
        val doc = read(DocxFixtures.document(DocxFixtures.paragraph("默认纸张"), section = ""))
        assertEquals(DocxPageSetup(), doc.page)
    }

    @Test
    fun pageBreakRunSplitsParagraphIntoOrderedBlocks() {
        val body = DocxFixtures.rawParagraph(
            "<w:r><w:t>第一页</w:t><w:br w:type=\"page\"/><w:t>第二页</w:t></w:r>",
        )
        val doc = read(DocxFixtures.document(body))
        assertEquals(3, doc.blocks.size)
        assertEquals("第一页", ((doc.blocks[0] as DocxBlock.Paragraph).inlines.single() as DocxInline.Text).text)
        assertTrue(doc.blocks[1] is DocxBlock.PageBreak)
        assertEquals("第二页", ((doc.blocks[2] as DocxBlock.Paragraph).inlines.single() as DocxInline.Text).text)
    }

    @Test
    fun pageBreakBeforePropertyEmitsBreakBeforeParagraph() {
        val doc = read(
            DocxFixtures.document(
                DocxFixtures.paragraph("新页", pPr = "<w:pPr><w:pageBreakBefore/></w:pPr>"),
            ),
        )
        assertEquals(2, doc.blocks.size)
        assertTrue(doc.blocks[0] is DocxBlock.PageBreak)
        assertTrue(doc.blocks[1] is DocxBlock.Paragraph)
    }

    @Test
    fun emptyParagraphAndTabsAndBreaksArePreserved() {
        val body = DocxFixtures.rawParagraph("<w:r><w:t>甲</w:t><w:tab/><w:t>乙</w:t><w:br/><w:t>丙</w:t></w:r>") +
            DocxFixtures.paragraph("")
        val doc = read(DocxFixtures.document(body))
        val paragraphs = doc.blocks.filterIsInstance<DocxBlock.Paragraph>()
        assertEquals(2, paragraphs.size)
        assertEquals("甲\t乙\n丙", (paragraphs[0].inlines.single() as DocxInline.Text).text)
        assertTrue(paragraphs[1].inlines.isEmpty())
    }

    @Test
    fun deletedTextIsSkippedWhileInsertedTextIsKept() {
        val body = DocxFixtures.rawParagraph(
            "<w:del><w:r><w:delText>删除</w:delText></w:r></w:del>" +
                "<w:ins><w:r><w:t>插入</w:t></w:r></w:ins>",
        )
        val doc = read(DocxFixtures.document(body))
        val text = doc.blocks.filterIsInstance<DocxBlock.Paragraph>().single().inlines
            .filterIsInstance<DocxInline.Text>().joinToString("") { it.text }
        assertEquals("插入", text.trim())
    }

    @Test
    fun plainTextIncludesBodyAndTableContent() {
        val body = DocxFixtures.paragraph("正文") +
            DocxFixtures.table(DocxFixtures.row(DocxFixtures.cell(DocxFixtures.paragraph("甲")), DocxFixtures.cell(DocxFixtures.paragraph("乙"))))
        val doc = read(DocxFixtures.document(body))
        val text = doc.plainText()
        assertTrue(text.contains("正文"))
        assertTrue(text.contains("甲"))
        assertTrue(text.contains("乙"))
    }

    @Test
    fun macroHeaderFooterAndEmbeddedWarningsAreExposed() {
        val doc = read(
            DocxFixtures.document(DocxFixtures.paragraph("提示")),
            extra = mapOf(
                "word/vbaProject.bin" to byteArrayOf(1, 2, 3),
                "word/header1.xml" to "<w:hdr xmlns:w=\"${DocxFixtures.W}\"/>".toByteArray(),
                "word/footnotes.xml" to "<w:footnotes xmlns:w=\"${DocxFixtures.W}\"/>".toByteArray(),
                "word/embeddings/oleObject1.bin" to byteArrayOf(4, 5),
            ),
        )
        assertTrue(doc.warnings.any { it.contains("宏") })
        assertTrue(doc.warnings.any { it.contains("页眉") })
        assertTrue(doc.warnings.any { it.contains("脚注") })
        assertTrue(doc.warnings.any { it.contains("嵌入对象") })
    }

    @Test
    fun textBeyondLimitKeepsEarlierBlocksAndMarksTruncated() {
        val body = DocxFixtures.paragraph("abcde") + DocxFixtures.paragraph("fghij")
        val doc = read(DocxFixtures.document(body), limits = DocxLimits(maxTextChars = 5))
        assertTrue(doc.truncated)
        assertEquals(1, doc.blocks.size)
        assertEquals(5, doc.blocks.filterIsInstance<DocxBlock.Paragraph>().single().inlines
            .filterIsInstance<DocxInline.Text>().joinToString("") { it.text }.length)
        assertTrue(doc.warnings.any { it.contains("仅显示前面部分") })
    }

    @Test
    fun unusedMediaIsNotLoaded() {
        val doc = read(
            DocxFixtures.document(DocxFixtures.paragraph("无图")),
            extra = mapOf("word/media/unused.png" to DocxFixtures.PNG),
        )
        assertTrue(doc.media.isEmpty())
        assertEquals(0, doc.imageCount)
    }
}
