package com.tanuki

import com.tanuki.storage.ChannelSeekableInput
import com.tanuki.storage.SeekableInput
import com.tanuki.storage.StreamSeekableInput
import com.tanuki.storage.TextFileReader
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption

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

    @Test fun seekableContentSourceReadsOnlyCurrentPageWindow() {
        val file = Files.createTempFile("text-window", ".txt")
        try {
            val text = "行内容中文🙂\n".repeat(120_000)
            Files.write(file, text.toByteArray())
            val counting = CountingSeekable(
                ChannelSeekableInput(FileChannel.open(file, StandardOpenOption.READ))
            )
            val first = TextFileReader.read(counting, 0)
            assertTrue(first.hasMore)
            assertTrue("单次读取不能超过一页: ${counting.maxRead}", counting.maxRead <= TextFileReader.PAGE_BYTES)
            assertTrue("当前页读取量不能超过一页加表头: ${counting.totalRead}",
                counting.totalRead <= TextFileReader.PAGE_BYTES + 4)
            val firstOffset = first.nextOffset

            counting.reset()
            val second = TextFileReader.read(counting, firstOffset)
            assertTrue(second.nextOffset >= firstOffset)
            assertTrue(counting.maxRead <= TextFileReader.PAGE_BYTES)
            assertTrue(counting.totalRead <= TextFileReader.PAGE_BYTES + 4)
            counting.close()
        } finally { Files.delete(file) }
    }

    @Test fun streamOnlyProviderPagesForwardWithoutWholeFileBuffer() {
        val text = "云盘顺序流内容\n".repeat(80_000)
        val bytes = text.toByteArray()
        val first = TextFileReader.read(
            StreamSeekableInput(ByteArrayInputStream(bytes), bytes.size.toLong()),
            0,
        )
        assertTrue(first.hasMore)
        assertEquals(bytes.size.toLong(), first.totalBytes)
        val second = TextFileReader.read(
            StreamSeekableInput(ByteArrayInputStream(bytes), bytes.size.toLong()),
            first.nextOffset,
        )
        assertTrue(second.offset >= first.nextOffset)
        assertEquals(bytes.size.toLong(), second.totalBytes)
    }

    @Test fun streamSourceHandlesUtf8BomWithoutRewinding() {
        val text = "中文BOM内容\n".repeat(20_000)
        val bytes = byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + text.toByteArray()
        val page = TextFileReader.read(
            StreamSeekableInput(ByteArrayInputStream(bytes), bytes.size.toLong()),
            0,
        )
        assertEquals("UTF-8", page.encoding)
        assertTrue(page.text.startsWith("中文BOM内容"))
    }

    @Test fun unknownSizeStreamStillReportsHasMoreAndUnknownTotal() {
        val bytes = "未知大小文本\n".repeat(30_000).toByteArray()
        assertTrue(bytes.size > TextFileReader.PAGE_BYTES)
        val page = TextFileReader.read(StreamSeekableInput(ByteArrayInputStream(bytes), null), 0)
        assertTrue(page.hasMore)
        assertEquals(-1L, page.totalBytes)
    }

    private class CountingSeekable(private val delegate: SeekableInput) : SeekableInput {
        override val size: Long? get() = delegate.size
        var maxRead = 0
        var totalRead = 0L

        override fun seek(offset: Long) = delegate.seek(offset)
        override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
            val read = delegate.read(buffer, offset, count)
            if (read > 0) {
                maxRead = maxOf(maxRead, read)
                totalRead += read
            }
            return read
        }
        fun reset() { maxRead = 0; totalRead = 0 }
        override fun close() = delegate.close()
    }
}
