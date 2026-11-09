package com.tanuki

import com.tanuki.storage.docx.DocxBlock
import com.tanuki.storage.docx.DocxInline
import com.tanuki.storage.docx.DocxLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** DOCX 损坏包、路径穿越、解压上限与 zip bomb 回归。 */
class DocxSecurityTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun write(files: Map<String, ByteArray>, name: String = "doc.docx"): File =
        temporary.newFile(name).also { DocxFixtures.write(it, files) }

    private fun read(files: Map<String, ByteArray>, limits: DocxLimits = DocxLimits()) =
        DocxFixtures.read(write(files), limits)

    private fun failure(files: Map<String, ByteArray>, limits: DocxLimits = DocxLimits()): IOException {
        val thrown = runCatching { read(files, limits) }.exceptionOrNull()
        assertTrue("期望 IOException，实际: $thrown", thrown is IOException)
        return thrown as IOException
    }

    private fun documentFiles(body: String = DocxFixtures.paragraph("正文")): LinkedHashMap<String, ByteArray> =
        linkedMapOf("word/document.xml" to DocxFixtures.document(body).toByteArray())

    @Test
    fun nonZipFileGetsActionableChineseError() {
        val file = temporary.newFile("plain.docx")
        file.writeText("not a zip")
        val error = runCatching { DocxFixtures.read(file) }.exceptionOrNull() as IOException
        assertTrue(error.message!!.contains("无法读取此 DOCX"))
        assertTrue(error.message!!.contains("损坏"))
    }

    @Test
    fun truncatedZipGetsActionableChineseError() {
        val valid = write(documentFiles()).readBytes()
        val broken = temporary.newFile("broken.docx").apply { writeBytes(valid.copyOf(valid.size / 2)) }
        val error = runCatching { DocxFixtures.read(broken) }.exceptionOrNull() as IOException
        assertTrue(error.message!!.contains("无法读取此 DOCX"))
    }

    @Test
    fun missingDocumentEntryIsRejected() {
        val error = failure(linkedMapOf("word/styles.xml" to DocxFixtures.styles("").toByteArray()))
        assertTrue(error.message!!.contains("缺少正文"))
    }

    @Test
    fun malformedDocumentXmlIsRejected() {
        val error = failure(linkedMapOf("word/document.xml" to "<w:document><w:body><w:p>".toByteArray()))
        assertTrue(error.message!!.contains("格式不正确") || error.message!!.contains("无法解析"))
    }

    @Test
    fun doctypeDeclarationIsRejected() {
        val xml = """<?xml version="1.0"?><!DOCTYPE w:document><w:document xmlns:w="${DocxFixtures.W}"><w:body/></w:document>"""
        val error = failure(linkedMapOf("word/document.xml" to xml.toByteArray()))
        assertTrue(error.message!!.contains("文档类型声明"))
    }

    @Test
    fun undefinedEntityIsRejected() {
        val body = DocxFixtures.rawParagraph("<w:r><w:t>坏实体&unknown;</w:t></w:r>")
        val error = failure(documentFiles(body))
        assertTrue(error.message!!.contains("实体"))
    }

    @Test
    fun standardAndNumericEntitiesArePreserved() {
        val body = DocxFixtures.rawParagraph("<w:r><w:t>A&amp;B&#65;&#x42;</w:t></w:r>")
        val doc = read(documentFiles(body))
        val text = (doc.blocks.single() as DocxBlock.Paragraph).inlines
            .filterIsInstance<DocxInline.Text>().joinToString("") { it.text }
        assertEquals("A&BAB", text)
    }

    @Test
    fun traversalEntryNameRejectsWholeArchive() {
        val files = documentFiles()
        files["../escape.xml"] = "<x/>".toByteArray()
        val error = failure(files)
        assertTrue(error.message!!.contains("不安全的路径"))
    }

    @Test
    fun tooManyEntriesIsRejected() {
        val files = documentFiles()
        files["word/media/a.png"] = byteArrayOf(1)
        files["word/media/b.png"] = byteArrayOf(2)
        val error = failure(files, DocxLimits(maxEntries = 2))
        assertTrue(error.message!!.contains("条目过多"))
    }

    @Test
    fun declaredTotalUncompressedSizeIsCapped() {
        val error = failure(documentFiles(), DocxLimits(maxTotalUncompressedBytes = 10))
        assertTrue(error.message!!.contains("总大小"))
    }

    @Test
    fun singleEntryDeclaredSizeIsCapped() {
        val error = failure(documentFiles(), DocxLimits(maxEntryBytes = 10))
        assertTrue(error.message!!.contains("单个条目"))
    }

    @Test
    fun archiveFileSizeIsCappedBeforeOpening() {
        val error = failure(documentFiles(), DocxLimits(maxArchiveBytes = 10))
        assertTrue(error.message!!.contains("文件过大"))
    }

    @Test
    fun actualDecompressionBeyondDocumentLimitFailsEvenIfDeclaredSizeLies() {
        // 中央目录声明的大小通过检查，但实际读取受 LimitedInputStream 限制。
        val body = DocxFixtures.paragraph("x".repeat(2000))
        val error = failure(documentFiles(body), DocxLimits(maxDocumentBytes = 100, maxEntryBytes = 64 * 1024))
        assertTrue(error.message!!.contains("解压后超过上限") || error.message!!.contains("无法读取"))
    }

    @Test
    fun excessiveXmlDepthIsRejected() {
        val body = DocxFixtures.paragraph("深", rPr = "<w:rPr><w:b/></w:rPr>")
        val error = failure(documentFiles(body), DocxLimits(maxXmlDepth = 4))
        assertTrue(error.message!!.contains("嵌套过深"))
    }

    @Test
    fun excessiveElementCountIsRejected() {
        val error = failure(documentFiles(), DocxLimits(maxElements = 5))
        assertTrue(error.message!!.contains("结构过于复杂"))
    }

    @Test
    fun mediaEntryOverSingleImageLimitIsSkippedNotFatal() {
        val rels = DocxFixtures.relationships(DocxFixtures.imageRelationship("rId5", "media/big.png"))
        val files = documentFiles(DocxFixtures.drawing("rId5"))
        files["word/_rels/document.xml.rels"] = rels.toByteArray()
        files["word/media/big.png"] = ByteArray(2048) { 1 }
        val doc = read(files, DocxLimits(maxMediaEntryBytes = 1024))
        assertTrue(doc.media.isEmpty())
        assertTrue(doc.warnings.any { it.contains("图片") })
        assertEquals(1, (doc.blocks.single() as DocxBlock.Paragraph).inlines.filterIsInstance<DocxInline.Image>().size)
    }

    @Test
    fun traversalRelationshipTargetIsRejectedWithoutNetworkOrFileAccess() {
        val rels = DocxFixtures.relationships(DocxFixtures.imageRelationship("rId5", "../../outside.png"))
        val files = documentFiles(DocxFixtures.drawing("rId5"))
        files["word/_rels/document.xml.rels"] = rels.toByteArray()
        val doc = read(files)
        val image = (doc.blocks.single() as DocxBlock.Paragraph).inlines
            .filterIsInstance<DocxInline.Image>().single().image
        assertNull(image.entryName)
        assertTrue(doc.media.isEmpty())
        assertTrue(doc.warnings.any { it.contains("不安全") })
    }

    @Test
    fun imageOutsideWordMediaIsRejected() {
        val rels = DocxFixtures.relationships(DocxFixtures.imageRelationship("rId5", "../embeddings/payload.bin"))
        val files = documentFiles(DocxFixtures.drawing("rId5"))
        files["word/_rels/document.xml.rels"] = rels.toByteArray()
        files["word/embeddings/payload.bin"] = byteArrayOf(1, 2, 3)
        val doc = read(files)
        val image = (doc.blocks.single() as DocxBlock.Paragraph).inlines
            .filterIsInstance<DocxInline.Image>().single().image
        assertNull(image.entryName)
        assertTrue(doc.media.isEmpty())
        assertTrue(doc.warnings.any { it.contains("不支持的位置") })
    }

    @Test
    fun externalHyperlinkRelationshipIsNotFetched() {
        val rels = DocxFixtures.relationships(
            "<Relationship Id=\"rId9\" Type=\"${DocxFixtures.HYPERLINK_TYPE}\" Target=\"https://example.com\" TargetMode=\"External\"/>",
        )
        val files = documentFiles(DocxFixtures.paragraph("链接"))
        files["word/_rels/document.xml.rels"] = rels.toByteArray()
        val doc = read(files)
        assertTrue(doc.media.isEmpty())
        assertEquals(1, doc.blocks.size)
    }

    @Test
    fun hugeMediaTotalIsCappedAndReportsWarning() {
        val rels = DocxFixtures.relationships(
            DocxFixtures.imageRelationship("rId1", "media/a.png") + DocxFixtures.imageRelationship("rId2", "media/b.png"),
        )
        val body = DocxFixtures.drawing("rId1") + DocxFixtures.drawing("rId2")
        val files = documentFiles(body)
        files["word/_rels/document.xml.rels"] = rels.toByteArray()
        files["word/media/a.png"] = ByteArray(800) { 1 }
        files["word/media/b.png"] = ByteArray(800) { 2 }
        val doc = read(files, DocxLimits(maxMediaTotalBytes = 1000))
        assertTrue(doc.media.size <= 1)
        assertTrue(doc.warnings.any { it.contains("图片总量") })
    }

    @Test
    fun onlyReferencedImagesAreReadFromArchive() {
        val rels = DocxFixtures.relationships(DocxFixtures.imageRelationship("rId5", "media/used.png"))
        val files = documentFiles(DocxFixtures.drawing("rId5"))
        files["word/_rels/document.xml.rels"] = rels.toByteArray()
        files["word/media/used.png"] = DocxFixtures.PNG
        files["word/media/unused.png"] = ByteArray(4096) { 7 }
        val doc = read(files)
        assertEquals(setOf("word/media/used.png"), doc.media.keys)
    }

    @Test
    fun emptyDocumentProducesNoBlocksWithoutError() {
        val doc = read(documentFiles(""))
        assertTrue(doc.blocks.isEmpty())
        assertFalse(doc.truncated)
        assertEquals("", doc.plainText())
    }
}
