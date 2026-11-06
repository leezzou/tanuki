package com.tanuki

import com.tanuki.storage.DocxTextExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

class DocxTextExtractorTest {
    private val parserFor: (InputStream) -> XmlPullParser = { input ->
        KXmlParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            setInput(input, null)
        }
    }

    private fun xml(body: String): ByteArray = (
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""" +
            "<w:body>$body</w:body></w:document>"
        ).toByteArray()

    private fun docx(vararg entries: Pair<String, ByteArray>): File {
        val file = Files.createTempFile("tanuki-docx", ".docx").toFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun extract(file: File, limits: DocxTextExtractor.Limits = DocxTextExtractor.Limits()): DocxTextExtractor.Result =
        DocxTextExtractor.extract(file, limits, parserFor)

    @Test fun extractsParagraphsEmptyParagraphsBreaksTabsAndEntities() {
        val file = docx(
            "word/document.xml" to xml(
                "<w:p><w:r><w:t>第一段 &amp; 文本<![CDATA[ & CDATA]]></w:t></w:r></w:p>" +
                    "<w:p/>" +
                    "<w:p><w:r><w:t>第二</w:t><w:br/><w:t>段</w:t><w:tab/><w:t>尾</w:t></w:r></w:p>",
            ),
        )
        try {
            val result = extract(file)
            assertEquals("第一段 & 文本 & CDATA\n\n第二\n段\t尾", result.text)
            assertFalse(result.truncated)
        } finally {
            file.delete()
        }
    }

    @Test fun rejectsDoctypeDeclarationBeforeRootElement() {
        val doctype = """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<!DOCTYPE w:document>""" +
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""" +
            """<w:body><w:p><w:r><w:t>不应被提取</w:t></w:r></w:p></w:body></w:document>"""
        val file = docx("word/document.xml" to doctype.toByteArray())
        try {
            extract(file)
            fail("包含 DOCTYPE 的正文应被拒绝")
        } catch (failure: IOException) {
            assertTrue(failure.message.orEmpty().contains("文档类型声明"))
        } finally {
            file.delete()
        }
    }

    @Test fun emptyBodyGivesEmptyText() {
        val file = docx("word/document.xml" to xml(""))
        try {
            val result = extract(file)
            assertEquals("", result.text)
            assertFalse(result.truncated)
        } finally {
            file.delete()
        }
    }

    @Test fun extractsStandardAndNumericCharacterReferences() {
        val file = docx("word/document.xml" to xml("<w:p><w:r><w:t>&amp;&lt;&#65;&#x42;</w:t></w:r></w:p>"))
        try {
            val result = extract(file)
            assertEquals("&<AB", result.text)
            assertFalse(result.truncated)
        } finally {
            file.delete()
        }
    }

    @Test fun rejectsUndefinedEntityInsteadOfSilentlyDroppingIt() {
        val file = docx("word/document.xml" to xml("<w:p><w:r><w:t>A&missing;B</w:t></w:r></w:p>"))
        try {
            extract(file)
            fail("未定义实体应被拒绝，不能静默显示为 AB")
        } catch (failure: IOException) {
            assertTrue(failure.message.orEmpty().contains("实体"))
        } finally {
            file.delete()
        }
    }

    @Test fun truncatesAtTextLimitInsteadOfUnboundedMemory() {
        val file = docx("word/document.xml" to xml("<w:p><w:r><w:t>${"字".repeat(100)}</w:t></w:r></w:p>"))
        try {
            val result = extract(file, DocxTextExtractor.Limits(maxTextChars = 10))
            assertEquals(10, result.text.length)
            assertTrue(result.truncated)
        } finally {
            file.delete()
        }
    }

    @Test fun rejectsOversizedDocumentEntryBeforeParsing() {
        val file = docx("word/document.xml" to xml("<w:p><w:r><w:t>${"a".repeat(200)}</w:t></w:r></w:p>"))
        try {
            extract(file, DocxTextExtractor.Limits(maxDocumentBytes = 100))
            fail("应拒绝超过单条目上限的正文")
        } catch (failure: IOException) {
            assertTrue(failure.message.orEmpty().contains("过大"))
        } finally {
            file.delete()
        }
    }

    @Test fun rejectsArchiveWhoseTotalUncompressedSizeExceedsLimit() {
        val file = docx(
            "word/document.xml" to xml("<w:p><w:r><w:t>正文</w:t></w:r></w:p>"),
            "word/media/image1.bin" to ByteArray(256),
        )
        try {
            extract(file, DocxTextExtractor.Limits(maxTotalUncompressedBytes = 128))
            fail("应拒绝总解压大小超限的压缩包")
        } catch (failure: IOException) {
            assertTrue(failure.message.orEmpty().contains("解压后过大"))
        } finally {
            file.delete()
        }
    }

    @Test fun rejectsMissingBodyEntryAndDamagedArchive() {
        val missing = docx("word/styles.xml" to xml("<w:p/>"))
        try {
            extract(missing)
            fail("缺少 word/document.xml 时应失败")
        } catch (failure: IOException) {
            assertTrue(failure.message.orEmpty().contains("缺少正文"))
        } finally {
            missing.delete()
        }
        val damaged = Files.createTempFile("tanuki-docx-damaged", ".docx").toFile()
        try {
            damaged.writeBytes(byteArrayOf(0x50, 0x4b, 0x00, 0x01, 0x02))
            extract(damaged)
            fail("损坏的 zip 应失败")
        } catch (failure: IOException) {
            assertTrue(failure.message.orEmpty().contains("损坏"))
        } finally {
            damaged.delete()
        }
    }

    @Test fun rejectsMalformedBodyXml() {
        val file = docx("word/document.xml" to "<w:document><w:body><w:p>".toByteArray())
        try {
            extract(file)
            fail("不完整 XML 应失败")
        } catch (failure: IOException) {
            assertTrue(failure.message.orEmpty().contains("格式不正确"))
        } finally {
            file.delete()
        }
    }
}
