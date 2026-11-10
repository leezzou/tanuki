package com.tanuki

import com.tanuki.storage.ChannelSeekableInput
import com.tanuki.storage.SeekableInput
import com.tanuki.storage.StreamSeekableInput
import com.tanuki.storage.TextFileReader
import com.tanuki.ui.TextPageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
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

    @Test fun bomAndExplicitLegacyEncodingRoundTripAcrossPages() {
        val file = Files.createTempFile("text-encoding", ".txt")
        try {
            val text = "简体中文和換行\n".repeat(12_000)
            for (encoding in listOf("UTF-16LE", "UTF-16BE", "GB18030", "Big5")) {
                val charset = Charset.forName(encoding)
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
            val second = TextFileReader.read(file, first.nextOffset, first.encoding)
            assertEquals("中", second.text)
            assertEquals(original, first.text + second.text)
        } finally { Files.delete(file) }
    }

    @Test fun detectsGbkChineseWithoutUserVisibleDecodeError() {
        val file = Files.createTempFile("text-gbk", ".txt")
        try {
            val original = "简体中文测试：编码自动识别，GBK 文件不应再提示解码失败。\n".repeat(4_000)
            Files.write(file, original.toByteArray(Charset.forName("GBK")))

            val first = TextFileReader.read(file)
            assertEquals("GB18030", first.encoding)
            assertFalse(first.binary)
            assertTrue("测试样本必须跨页", first.hasMore)
            val result = StringBuilder(first.text)
            var offset = first.nextOffset
            var hasMore = first.hasMore
            // 查看器在自动识别后沿用第一页的编码翻页，避免从文件中部重新猜测。
            while (hasMore) {
                val next = TextFileReader.read(file, offset, first.encoding)
                result.append(next.text)
                offset = next.nextOffset
                hasMore = next.hasMore
            }
            assertEquals(original, result.toString())
        } finally { Files.delete(file) }
    }

    @Test fun detectsGb18030RareCharacters() {
        val file = Files.createTempFile("text-gb18030-rare", ".txt")
        try {
            val original = "生僻字：𠀀龘，扩展区字符也应正常显示。"
            Files.write(file, original.toByteArray(Charset.forName("GB18030")))
            val page = TextFileReader.read(file)
            assertEquals("GB18030", page.encoding)
            assertEquals(original, page.text)
        } finally { Files.delete(file) }
    }

    @Test fun detectsUtf16LittleAndBigEndianWithoutBom() {
        val file = Files.createTempFile("text-utf16-no-bom", ".txt")
        try {
            val original = "Hello 中文测试\n编码识别".repeat(20)
            for (encoding in listOf("UTF-16LE", "UTF-16BE")) {
                Files.write(file, original.toByteArray(Charset.forName(encoding)))
                val page = TextFileReader.read(file)
                assertEquals(encoding, page.encoding)
                assertEquals(original, page.text)
            }

            // 纯中日韩文本没有 0x00 交错，仍需用两种端序的可读性挑出正确端序。
            val cjk = "中文测试编码识别"
            for (encoding in listOf("UTF-16LE", "UTF-16BE")) {
                Files.write(file, cjk.toByteArray(Charset.forName(encoding)))
                val page = TextFileReader.read(file)
                assertEquals(encoding, page.encoding)
                assertEquals(cjk, page.text)
            }

            // 手动选择笼统的 UTF-16 时也要解析出具体端序，避免后续分页退回大端默认值。
            Files.write(file, cjk.toByteArray(Charset.forName("UTF-16LE")))
            val manual = TextFileReader.read(file, encoding = "UTF-16")
            assertEquals("UTF-16LE", manual.encoding)
            assertEquals(cjk, manual.text)
        } finally { Files.delete(file) }
    }

    @Test fun detectsUtf8AndUtf32BomAndStripsThem() {
        val file = Files.createTempFile("text-bom", ".txt")
        try {
            val utf8 = "UTF-8 中文 BOM"
            Files.write(file, byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + utf8.toByteArray())
            val utf8Page = TextFileReader.read(file)
            assertEquals("UTF-8", utf8Page.encoding)
            assertEquals(utf8, utf8Page.text)

            val utf32 = "UTF-32 中文"
            val le = byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0x00, 0x00) +
                utf32.toByteArray(Charset.forName("UTF-32LE"))
            Files.write(file, le)
            val lePage = TextFileReader.read(file)
            assertEquals("UTF-32LE", lePage.encoding)
            assertEquals(utf32, lePage.text)

            val be = byteArrayOf(0x00, 0x00, 0xfe.toByte(), 0xff.toByte()) +
                utf32.toByteArray(Charset.forName("UTF-32BE"))
            Files.write(file, be)
            val bePage = TextFileReader.read(file)
            assertEquals("UTF-32BE", bePage.encoding)
            assertEquals(utf32, bePage.text)
        } finally { Files.delete(file) }
    }

    @Test fun invalidByteSequencesFallBackToLatin1WithoutThrowing() {
        val file = Files.createTempFile("text-invalid", ".txt")
        try {
            // 0x81/0x82 后跟空格不是 GB18030、Big5 或 Shift_JIS 的合法组合，也不构成 UTF-8。
            val bytes = byteArrayOf(0x81.toByte(), 0x20, 0x82.toByte(), 0x20)
            Files.write(file, bytes)
            val page = TextFileReader.read(file)
            assertEquals("ISO-8859-1", page.encoding)
            assertFalse(page.binary)
            assertEquals(bytes.size, page.text.length)
        } finally { Files.delete(file) }
    }

    @Test fun detectsBinaryFilesInsteadOfRenderingControlCharacters() {
        val file = Files.createTempFile("text-binary", ".bin")
        try {
            // PNG 文件头包含非 UTF-8 高位字节和控制字符，必须判为二进制而不是文本。
            Files.write(file, byteArrayOf(
                0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
            ))
            val png = TextFileReader.read(file)
            assertTrue(png.binary)
            assertEquals(TextFileReader.BINARY_ENCODING, png.encoding)
            assertEquals("", png.text)
            assertFalse(png.hasMore)

            Files.write(file, byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15))
            val controls = TextFileReader.read(file)
            assertTrue(controls.binary)
            assertEquals(TextFileReader.BINARY_ENCODING, controls.encoding)
        } finally { Files.delete(file) }
    }

    @Test fun manualEncodingSwitchDecodesTheSameBytesAndNeverThrows() {
        val file = Files.createTempFile("text-manual", ".txt")
        try {
            val original = "中文 GBK 手动切换"
            val bytes = original.toByteArray(Charset.forName("GBK"))
            Files.write(file, bytes)

            assertEquals(original, TextFileReader.read(file, encoding = "GB18030").text)

            // 手动选错编码时用替代字符显示，不把解码异常抛给界面。
            val wrongUtf8 = TextFileReader.read(file, encoding = "UTF-8")
            assertEquals("UTF-8", wrongUtf8.encoding)
            assertTrue(wrongUtf8.text.contains('\ufffd'))

            val manualUtf16 = TextFileReader.read(file, encoding = "UTF-16LE")
            assertEquals("UTF-16LE", manualUtf16.encoding)
            assertTrue(manualUtf16.text.isNotEmpty())

            val latin1 = TextFileReader.read(file, encoding = "ISO-8859-1")
            assertEquals("ISO-8859-1", latin1.encoding)
            assertEquals(bytes.size, latin1.text.length)

            try {
                TextFileReader.read(file, encoding = "NOT-A-CHARSET")
                fail("未知编码名应拒绝")
            } catch (_: IllegalArgumentException) { }
        } finally { Files.delete(file) }
    }

    @Test fun detectsBig5AndShiftJisWhenGb18030WouldProduceGarbledCjk() {
        val file = Files.createTempFile("text-east-asian", ".txt")
        try {
            val big5Text = "繁體中文測試"
            Files.write(file, big5Text.toByteArray(Charset.forName("Big5")))
            val big5 = TextFileReader.read(file)
            assertEquals("Big5", big5.encoding)
            assertEquals(big5Text, big5.text)

            val sjisText = "日本語のテスト"
            Files.write(file, sjisText.toByteArray(Charset.forName("Shift_JIS")))
            val sjis = TextFileReader.read(file)
            assertEquals("Shift_JIS", sjis.encoding)
            assertEquals(sjisText, sjis.text)
        } finally { Files.delete(file) }
    }

    @Test fun autoDetectedShiftJisAndUtf32CanContinuePaging() {
        val file = Files.createTempFile("text-internal-encoding-pages", ".txt")
        try {
            // 查看器把第一页自动识别出的 encoding 原样传给后续页，内部编码必须在读取白名单中。
            val sjis = "日本語のテスト。\n".repeat(20_000)
            Files.write(file, sjis.toByteArray(Charset.forName("Shift_JIS")))
            assertPagedWithDetectedEncoding(file, sjis, "Shift_JIS")

            val utf32 = "UTF-32 中文跨页测试\n".repeat(5_000)
            val le = byteArrayOf(0xff.toByte(), 0xfe.toByte(), 0x00, 0x00) +
                utf32.toByteArray(Charset.forName("UTF-32LE"))
            Files.write(file, le)
            assertPagedWithDetectedEncoding(file, utf32, "UTF-32LE")

            val be = byteArrayOf(0x00, 0x00, 0xfe.toByte(), 0xff.toByte()) +
                utf32.toByteArray(Charset.forName("UTF-32BE"))
            Files.write(file, be)
            assertPagedWithDetectedEncoding(file, utf32, "UTF-32BE")
        } finally { Files.delete(file) }
    }

    @Test fun unknownSizeStreamStopsAtEofAndReportsActualTotal() {
        val empty = TextFileReader.read(StreamSeekableInput(ByteArrayInputStream(byteArrayOf()), null), 0)
        assertEquals("", empty.text)
        assertEquals(0L, empty.nextOffset)
        assertEquals(0L, empty.totalBytes)
        assertFalse("空顺序流不能继续翻页", empty.hasMore)

        val shortBytes = "abc".toByteArray()
        val short = TextFileReader.read(StreamSeekableInput(ByteArrayInputStream(shortBytes), null), 0)
        assertEquals("abc", short.text)
        assertEquals(shortBytes.size.toLong(), short.nextOffset)
        assertEquals(shortBytes.size.toLong(), short.totalBytes)
        assertFalse("短顺序流读到 EOF 后不能继续翻页", short.hasMore)

        val longBytes = ByteArray(TextFileReader.PAGE_BYTES * 2 + 100) { 'a'.code.toByte() }
        var offset = 0L
        var pages = 0
        var finalTotal = -2L
        while (true) {
            val page = TextFileReader.read(
                StreamSeekableInput(ByteArrayInputStream(longBytes), null),
                offset,
            )
            assertTrue(page.nextOffset >= offset)
            offset = page.nextOffset
            pages++
            finalTotal = page.totalBytes
            if (!page.hasMore) break
            assertTrue("未到 EOF 前应继续返回大小未知", page.totalBytes < 0)
        }
        assertEquals(longBytes.size.toLong(), offset)
        assertEquals(3, pages)
        assertEquals(longBytes.size.toLong(), finalTotal)
    }

    @Test fun stopsAtFourMebibyteDisplayCap() {
        val file = Files.createTempFile("text-cap", ".txt")
        try {
            val total = TextFileReader.MAX_VIEW_BYTES + 123
            Files.write(file, ByteArray(total.toInt()) { 'a'.code.toByte() })
            var offset = 0L
            var pages = 0
            while (true) {
                val page = TextFileReader.read(file, offset)
                assertTrue(page.nextOffset >= offset)
                offset = page.nextOffset
                pages++
                if (!page.hasMore) break
                assertTrue("未到 4 MiB 上限前应继续分页", offset < TextFileReader.MAX_VIEW_BYTES)
            }
            assertEquals(TextFileReader.MAX_VIEW_BYTES, offset)
            assertEquals(TextFileReader.MAX_VIEW_BYTES / TextFileReader.PAGE_BYTES, pages.toLong())

            val capped = TextFileReader.read(file, TextFileReader.MAX_VIEW_BYTES)
            assertEquals("", capped.text)
            assertFalse(capped.hasMore)
            assertEquals(total, capped.totalBytes)
        } finally { Files.delete(file) }
    }

    @Test fun fourMebibyteCapDoesNotTurnPartialCharacterIntoLatin1() {
        val file = Files.createTempFile("text-cap-multibyte", ".txt")
        try {
            // 3 字节汉字正好横跨 4 MiB 上限：最后一页只能读到首字节，不能显示成 Latin-1 的 ä。
            val prefix = "a".repeat((TextFileReader.MAX_VIEW_BYTES - 1).toInt())
            val original = prefix + "中"
            Files.write(file, original.toByteArray())

            var offset = 0L
            val shown = StringBuilder()
            var last: TextFileReader.Page? = null
            while (true) {
                val page = TextFileReader.read(file, offset)
                shown.append(page.text)
                last = page
                if (!page.hasMore) break
                offset = page.nextOffset
                assertTrue("未到 4 MiB 上限前应继续分页", offset < TextFileReader.MAX_VIEW_BYTES)
            }
            assertEquals(prefix, shown.toString())
            assertFalse("被上限切断的字符不能单独成段", shown.contains('ä'))

            val end = requireNotNull(last)
            assertFalse(end.hasMore)
            assertEquals(original.toByteArray().size.toLong(), end.totalBytes)
            assertTrue(
                "上限切在多字节字符内部也要保留截断提示",
                TextPageLayout.isTruncated(end.nextOffset, end.totalBytes, end.hasMore),
            )
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
            assertTrue("当前页读取量不能超过一页加探测样本: ${counting.totalRead}",
                counting.totalRead <= TextFileReader.PAGE_BYTES + TextFileReader.DETECT_BYTES)
            val firstOffset = first.nextOffset

            counting.reset()
            val second = TextFileReader.read(counting, firstOffset)
            assertTrue(second.nextOffset >= firstOffset)
            assertTrue(counting.maxRead <= TextFileReader.PAGE_BYTES)
            assertTrue(counting.totalRead <= TextFileReader.PAGE_BYTES + TextFileReader.DETECT_BYTES)
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
            first.encoding,
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

    @Test fun explicitIncompleteCharacterAndLatin1DoNotFailTheViewer() {
        val file = Files.createTempFile("text-encoding-edge", ".txt")
        try {
            // UTF-16LE 末尾只剩半个字符：显式选择时用替代字符显示，不抛异常。
            Files.write(file, "a".toByteArray(Charsets.UTF_16LE) + byteArrayOf(0x41))
            val utf16 = TextFileReader.read(file, encoding = "UTF-16LE")
            assertEquals("UTF-16LE", utf16.encoding)
            assertTrue(utf16.text.startsWith("a"))
            assertTrue(utf16.text.contains('\ufffd'))

            // ISO-8859-1 是显式单选，单个高位字节应正常解码。
            Files.write(file, byteArrayOf(0x63, 0x61, 0x66, 0xe9.toByte()))
            val latin1 = TextFileReader.read(file, encoding = "ISO-8859-1")
            assertEquals("café", latin1.text)
            assertEquals("ISO-8859-1", latin1.encoding)
        } finally { Files.delete(file) }
    }

    /** 模拟查看器：第一页自动识别，后续页沿用第一页返回的具体编码。 */
    private fun assertPagedWithDetectedEncoding(file: Path, original: String, expectedEncoding: String) {
        val first = TextFileReader.read(file)
        assertEquals(expectedEncoding, first.encoding)
        assertTrue("测试样本必须跨页", first.hasMore)
        val result = StringBuilder(first.text)
        var offset = first.nextOffset
        var hasMore = first.hasMore
        while (hasMore) {
            val next = TextFileReader.read(file, offset, first.encoding)
            assertTrue(next.nextOffset > offset)
            result.append(next.text)
            offset = next.nextOffset
            hasMore = next.hasMore
        }
        assertEquals(original, result.toString())
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
