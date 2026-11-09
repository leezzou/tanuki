package com.tanuki

import com.tanuki.storage.docx.DocxDocument
import com.tanuki.storage.docx.DocxDocumentReader
import com.tanuki.storage.docx.DocxLimits
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 合成 DOCX 压缩包工具；不依赖真实 Word 文件、网络或 Android。 */
internal object DocxFixtures {
    const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    const val WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
    const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
    const val PIC = "http://schemas.openxmlformats.org/drawingml/2006/picture"
    const val V = "urn:schemas-microsoft-com:vml"
    const val RELS = "http://schemas.openxmlformats.org/package/2006/relationships"
    const val IMAGE_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image"
    const val HYPERLINK_TYPE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"

    fun document(body: String, section: String = sectPr()): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="$W" xmlns:r="$R" xmlns:wp="$WP" xmlns:a="$A" xmlns:pic="$PIC" xmlns:v="$V"><w:body>$body$section</w:body></w:document>"""

    fun sectPr(
        width: Int = 11906,
        height: Int = 16838,
        left: Int = 1440,
        right: Int = 1440,
        top: Int = 1440,
        bottom: Int = 1440,
    ): String = "<w:sectPr><w:pgSz w:w=\"$width\" w:h=\"$height\"/>" +
        "<w:pgMar w:left=\"$left\" w:right=\"$right\" w:top=\"$top\" w:bottom=\"$bottom\"/></w:sectPr>"

    fun paragraph(text: String, pPr: String = "", rPr: String = ""): String =
        "<w:p>$pPr<w:r>$rPr<w:t xml:space=\"preserve\">${escape(text)}</w:t></w:r></w:p>"

    fun rawParagraph(inner: String): String = "<w:p>$inner</w:p>"

    fun run(text: String, rPr: String = ""): String =
        "<w:r>$rPr<w:t xml:space=\"preserve\">${escape(text)}</w:t></w:r>"

    fun styles(inner: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:styles xmlns:w=\"$W\">$inner</w:styles>"

    fun numbering(inner: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:numbering xmlns:w=\"$W\">$inner</w:numbering>"

    fun relationships(inner: String): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"$RELS\">$inner</Relationships>"

    fun imageRelationship(id: String = "rId5", target: String = "media/image1.png", external: Boolean = false): String =
        "<Relationship Id=\"$id\" Type=\"$IMAGE_TYPE\" Target=\"${escape(target)}\"" +
            (if (external) " TargetMode=\"External\"" else "") + "/>"

    fun drawing(relId: String = "rId5", cx: Long = 914400, cy: Long = 457200, description: String? = null): String {
        val alt = description?.let { " descr=\"${escape(it)}\"" } ?: ""
        return "<w:p><w:r><w:drawing><wp:inline><wp:extent cx=\"$cx\" cy=\"$cy\"/>" +
            "<wp:docPr id=\"1\" name=\"Picture 1\"$alt/><a:graphic><a:graphicData uri=\"$PIC\">" +
            "<pic:pic><pic:blipFill><a:blip r:embed=\"$relId\"/></pic:blipFill></pic:pic>" +
            "</a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"
    }

    fun vmlDrawing(relId: String = "rId5"): String =
        "<w:p><w:r><w:pict><v:shape style=\"width:100pt;height:50pt\"><v:imagedata r:id=\"$relId\"/></v:shape></w:pict></w:r></w:p>"

    fun table(vararg rows: String, grid: List<Int> = emptyList()): String {
        val gridXml = if (grid.isEmpty()) "" else "<w:tblGrid>${grid.joinToString("") { "<w:gridCol w:w=\"$it\"/>" }}</w:tblGrid>"
        return "<w:tbl><w:tblPr/>$gridXml${rows.joinToString("")}</w:tbl>"
    }

    fun row(vararg cells: String, header: Boolean = false): String =
        "<w:tr>${if (header) "<w:trPr><w:tblHeader/></w:trPr>" else ""}${cells.joinToString("")}</w:tr>"

    fun cell(content: String, props: String = ""): String = "<w:tc>${if (props.isEmpty()) "" else "<w:tcPr>$props</w:tcPr>"}$content</w:tc>"

    fun read(file: File, limits: DocxLimits = DocxLimits()): DocxDocument =
        DocxDocumentReader.read(file, limits, parserFactory())

    fun parserFactory(): (InputStream) -> XmlPullParser {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }
        return { _ -> factory.newPullParser() }
    }

    fun write(file: File, entries: Map<String, ByteArray>) =
        writeEntries(file, entries.map { it.key to it.value })

    /** 允许重复条目，用于拒绝重复名称的回归。 */
    fun writeEntries(file: File, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    fun entries(vararg pairs: Pair<String, ByteArray>): LinkedHashMap<String, ByteArray> =
        linkedMapOf(*pairs)

    fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    val PNG: ByteArray = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
    )
}
