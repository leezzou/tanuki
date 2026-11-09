package com.tanuki.storage.docx

/**
 * 内置 DOCX 只读预览的文档模型。
 *
 * 只覆盖常用文字排版子集（段落、标题、列表、字符样式、表格、内嵌图片、纸张与页边距）；
 * 复杂域、宏、嵌入对象、文本框、页眉页脚和精确分页不在模型内，界面必须如实提示边界。
 * [media] 只保存正文实际引用且有上限的图片字节，不暴露压缩包路径。
 */
data class DocxDocument(
    val page: DocxPageSetup,
    val blocks: List<DocxBlock>,
    val media: Map<String, ByteArray>,
    val truncated: Boolean,
    val warnings: List<String>,
) {
    val paragraphCount: Int by lazy { countParagraphs(blocks) }
    val tableCount: Int by lazy { blocks.count { it is DocxBlock.Table } }
    val imageCount: Int get() = media.size

    /** 供“复制全文”使用；不含编号符号，表格单元格以制表符分隔。 */
    fun plainText(limit: Int = 1_000_000): String {
        val output = StringBuilder()
        appendBlocks(blocks, output, limit)
        return output.toString()
    }

    private fun appendBlocks(blocks: List<DocxBlock>, output: StringBuilder, limit: Int) {
        for (block in blocks) {
            if (output.length >= limit) return
            when (block) {
                is DocxBlock.Paragraph -> {
                    for (inline in block.inlines) {
                        if (inline is DocxInline.Text) {
                            val room = limit - output.length
                            if (room <= 0) return
                            output.append(if (inline.text.length <= room) inline.text else inline.text.take(room))
                        }
                    }
                    output.append('\n')
                }
                is DocxBlock.Table -> for (row in block.rows) {
                    output.append(row.cells.joinToString("\t") { cell -> cell.blocks.filterIsInstance<DocxBlock.Paragraph>().joinToString("") { it.text() } })
                    output.append('\n')
                }
                DocxBlock.PageBreak -> Unit
            }
        }
    }

    private fun countParagraphs(blocks: List<DocxBlock>): Int = blocks.sumOf { block ->
        when (block) {
            is DocxBlock.Paragraph -> 1
            is DocxBlock.Table -> block.rows.sumOf { row -> row.cells.sumOf { cell -> countParagraphs(cell.blocks) } }
            DocxBlock.PageBreak -> 0
        }
    }
}

/** 纸张与页边距，单位为缇（twip，1/1440 英寸）。默认 A4 纵向、1 英寸页边距。 */
data class DocxPageSetup(
    val widthTwips: Int = 11906,
    val heightTwips: Int = 16838,
    val marginLeftTwips: Int = 1440,
    val marginRightTwips: Int = 1440,
    val marginTopTwips: Int = 1440,
    val marginBottomTwips: Int = 1440,
) {
    val contentWidthTwips: Int get() = (widthTwips - marginLeftTwips - marginRightTwips).coerceAtLeast(1)
    val contentHeightTwips: Int get() = (heightTwips - marginTopTwips - marginBottomTwips).coerceAtLeast(1)
}

sealed interface DocxBlock {
    data class Paragraph(
        val style: DocxParagraphStyle,
        val inlines: List<DocxInline>,
        val numbering: DocxNumberingMarker? = null,
    ) : DocxBlock

    data class Table(
        val rows: List<DocxTableRow>,
        val columnWidthsTwips: List<Int>,
        val headerRow: Boolean = false,
    ) : DocxBlock

    /** 显式分页符（`w:br w:type="page"` 或段落 `pageBreakBefore`），渲染时强制换页。 */
    data object PageBreak : DocxBlock
}

data class DocxTableRow(val cells: List<DocxTableCell>, val header: Boolean = false)

data class DocxTableCell(
    val blocks: List<DocxBlock>,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
    val shadeArgb: Int? = null,
    val widthTwips: Int? = null,
)

sealed interface DocxInline {
    data class Text(val text: String, val style: DocxRunStyle) : DocxInline

    data class Image(val image: DocxImage) : DocxInline

    data object PageBreak : DocxInline
}

data class DocxImage(
    val relationshipId: String,
    /** 压缩包内已校验的条目名；为 null 表示关系缺失、外部图片或被拒绝的路径。 */
    val entryName: String?,
    val widthEmu: Long? = null,
    val heightEmu: Long? = null,
    val altText: String? = null,
)

data class DocxRunStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    /** 半磅值，22 表示 11 磅。 */
    val fontSizeHalfPoints: Int = 22,
    val colorArgb: Int = 0xFF000000.toInt(),
    val highlightArgb: Int? = null,
)

data class DocxParagraphStyle(
    val headingLevel: Int = 0,
    val alignment: DocxAlignment = DocxAlignment.START,
    val spaceBeforeTwips: Int = 0,
    val spaceAfterTwips: Int = 0,
    val lineSpacing: DocxLineSpacing = DocxLineSpacing.Auto(240),
    val indentLeftTwips: Int = 0,
    val indentFirstLineTwips: Int = 0,
    val hangingTwips: Int = 0,
    val pageBreakBefore: Boolean = false,
    val defaultRun: DocxRunStyle = DocxRunStyle(),
)

enum class DocxAlignment { START, CENTER, END, JUSTIFY }

sealed interface DocxLineSpacing {
    /** `lineRule=auto`，240 表示单倍行距。 */
    data class Auto(val percent: Int) : DocxLineSpacing

    data class Exact(val twips: Int) : DocxLineSpacing

    data class AtLeast(val twips: Int) : DocxLineSpacing
}

/** 列表项目符号/编号；[indentLeftTwips] 来自列表级别定义。 */
data class DocxNumberingMarker(
    val text: String,
    val level: Int,
    val bullet: Boolean,
    val indentLeftTwips: Int = 0,
    val hangingTwips: Int = 0,
)

/** 解析上限；所有值都必须在读取时按实际字节再次校验，不能只信压缩包中央目录。 */
data class DocxLimits(
    val maxArchiveBytes: Long = 64L * 1024 * 1024,
    val maxEntries: Int = 4096,
    val maxTotalUncompressedBytes: Long = 256L * 1024 * 1024,
    val maxEntryBytes: Long = 32L * 1024 * 1024,
    val maxDocumentBytes: Long = 16L * 1024 * 1024,
    val maxAuxXmlBytes: Long = 8L * 1024 * 1024,
    val maxMediaEntryBytes: Long = 16L * 1024 * 1024,
    val maxMediaTotalBytes: Long = 48L * 1024 * 1024,
    val maxMediaCount: Int = 256,
    val maxElements: Int = 300_000,
    val maxXmlDepth: Int = 200,
    val maxTextChars: Int = 2_000_000,
    val maxBlocks: Int = 6_000,
    val maxTableCells: Int = 20_000,
)

/** 段落里所有可复制文本的拼接，供表格单元格纯文本复用。 */
internal fun DocxBlock.Paragraph.text(): String = buildString {
    for (inline in inlines) if (inline is DocxInline.Text) append(inline.text)
}
