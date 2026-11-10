package com.tanuki

import com.tanuki.storage.TextFileReader
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class TextFileReaderTest {
    @Test fun pagesDoNotSplitUtf8AndAreNotBoundedByEditorLimit() {
        val file = Files.createTempFile("text-pages", ".txt")
        try {
            val original = "中🙂文\n".repeat(60_000)
            Files.write(file, original.toByteArray())
            val result = StringBuilder()
            var offset = 0L
            do {
                val page = TextFileReader.read(file, offset)
                result.append(page.text)
                assertTrue(page.nextOffset > offset)
                offset = page.nextOffset
            } while (page.hasMore)
            assertEquals(original, result.toString())
        } finally { Files.delete(file) }
    }

    @Test fun bomAndLegacyEncodingRoundTripAcrossPages() {
        val file = Files.createTempFile("text-encoding", ".txt")
        try {
            val text = "简体中文和換行\n".repeat(12_000)
            for (encoding in listOf("UTF-16LE", "UTF-16BE", "GB18030", "Big5")) {
                val charset = java.nio.charset.Charset.forName(encoding)
                val bytes = text.toByteArray(charset)
                val prefix = when (encoding) {
                    "UTF-16LE" -> byteArrayOf(0xff.toByte(), 0xfe.toByte())
                    "UTF-16BE" -> byteArrayOf(0xfe.toByte(), 0xff.toByte())
                    else -> byteArrayOf()
                }
                Files.write(file, prefix + bytes)
                val result = StringBuilder()
                var offset = 0L
                do {
                    val page = TextFileReader.read(file, offset, if (prefix.isEmpty()) encoding else "自动")
                    result.append(page.text)
                    offset = page.nextOffset
                } while (page.hasMore)
                assertEquals(String(bytes, charset), result.toString())
            }
        } finally { Files.delete(file) }
    }

    @Test fun stopsExactlyAtPageAndFileSizeBoundaries() {
        val file = Files.createTempFile("text-size", ".txt")
        try {
            // 恰好一页：不应提前截断，也不应多读一页。
            Files.write(file, "a".repeat(TextFileReader.PAGE_BYTES).toByteArray())
            val exact = TextFileReader.read(file)
            assertEquals(TextFileReader.PAGE_BYTES.toLong(), exact.nextOffset)
            assertFalse("恰好一页不应再提示有下一页", exact.hasMore)

            // 一页多 1 字节：第一页读完仍有下一页，第二页只有一个字符。
            Files.write(file, "a".repeat(TextFileReader.PAGE_BYTES + 1).toByteArray())
            val first = TextFileReader.read(file)
            assertEquals(TextFileReader.PAGE_BYTES.toLong(), first.nextOffset)
            assertTrue(first.hasMore)
            val second = TextFileReader.read(file, first.nextOffset)
            assertEquals("a", second.text)
            assertEquals(TextFileReader.PAGE_BYTES + 1L, second.totalBytes)
            assertFalse(second.hasMore)

            // 空文件：一页空文本，没有后续。
            Files.write(file, byteArrayOf())
            val empty = TextFileReader.read(file)
            assertEquals("", empty.text)
            assertEquals(0L, empty.nextOffset)
            assertEquals(0L, empty.totalBytes)
            assertFalse(empty.hasMore)
        } finally { Files.delete(file) }
    }

    @Test fun doesNotSplitMultiByteCharacterAtPageBoundary() {
        val file = Files.createTempFile("text-boundary", ".txt")
        try {
            // 3 字节汉字正好跨越 64 KiB 边界，第一页必须停在完整字符之后。
            val original = "a".repeat(TextFileReader.PAGE_BYTES - 1) + "中"
            Files.write(file, original.toByteArray())
            val first = TextFileReader.read(file)
            assertEquals(TextFileReader.PAGE_BYTES - 1L, first.nextOffset)
            assertFalse(first.text.contains('\ufffd'))
            assertTrue(first.hasMore)
            val second = TextFileReader.read(file, first.nextOffset)
            assertEquals("中", second.text)
            assertEquals(original, first.text + second.text)
        } finally { Files.delete(file) }
    }

    @Test fun rejectsIncompleteCharacterAtEndAndSupportsLatin1Explicitly() {
        val file = Files.createTempFile("text-encoding-edge", ".txt")
        try {
            // UTF-16LE 末尾只剩半个字符：必须报错而不是静默替换。
            Files.write(file, "a".toByteArray(Charsets.UTF_16LE) + byteArrayOf(0x41))
            try { TextFileReader.read(file, encoding = "UTF-16LE"); fail("不完整字符应报错") }
            catch (_: java.io.IOException) { }

            // ISO-8859-1 是显式单选，单个高位字节应正常解码。
            Files.write(file, byteArrayOf(0x63, 0x61, 0x66, 0xe9.toByte()))
            val latin1 = TextFileReader.read(file, encoding = "ISO-8859-1")
            assertEquals("café", latin1.text)
            assertEquals("ISO-8859-1", latin1.encoding)

            // 未知编码名直接拒绝，不能落回默认编码。
            try { TextFileReader.read(file, encoding = "NOT-A-CHARSET"); fail("未知编码应拒绝") }
            catch (_: IllegalArgumentException) { }
        } finally { Files.delete(file) }
    }

    @Test fun rejectsBinaryAndReportsMalformedEncodingWithoutSilentlyReplacing() {
        val file = Files.createTempFile("text-invalid", ".txt")
        try {
            for (bytes in listOf(byteArrayOf(0, 1, 2), byteArrayOf(0xc3.toByte(), 0x28))) {
                Files.write(file, bytes)
                try { TextFileReader.read(file); fail("应拒绝二进制或错误编码") }
                catch (_: java.io.IOException) { }
            }
            Files.write(file, byteArrayOf())
            val empty = TextFileReader.read(file)
            assertEquals("", empty.text)
            assertFalse(empty.hasMore)
        } finally { Files.delete(file) }
    }
}
