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
