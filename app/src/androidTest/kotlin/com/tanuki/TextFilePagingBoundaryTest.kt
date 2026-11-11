package com.tanuki

import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.storage.TextFileReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.nio.file.Files

/** 在 Android 实际字符集实现上验证分页边界；只读写本用例创建的合成文件。 */
class TextFilePagingBoundaryTest {
    @Test fun gbkDoubleByteBoundary() = assertBoundary("GBK", "边", 2, "GB18030")

    @Test fun gb18030FourByteBoundary() =
        assertBoundary("GB18030", String(Character.toChars(0x20000)), 4, "GB18030")

    @Test fun utf8ThreeByteBoundary() = assertBoundary("UTF-8", "中", 3, "UTF-8")

    @Test fun utf8FourByteBoundary() = assertBoundary("UTF-8", "🙂", 4, "UTF-8")

    private fun assertBoundary(encoding: String, target: String, width: Int, detectedEncoding: String) {
        val charset = Charset.forName(encoding)
        assertEquals(width, target.toByteArray(charset).size)
        val prefix = "// 中文源码分页边界测试\n".repeat(64)
        val prefixBytes = prefix.toByteArray(charset).size
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = Files.createTempFile(context.cacheDir.toPath(), "text-page-boundary-", ".cpp")
        try {
            // 覆盖每个字符内部切点，也覆盖下一页仅剩一个字符的情况。
            for (bytesOnFirstPage in 1 until width) {
                val boundary = TextFileReader.PAGE_BYTES - bytesOnFirstPage
                val completePrefix = prefix + "a".repeat(boundary - prefixBytes)
                for (tail in listOf("", "边界标记：第二页\n")) {
                    val original = completePrefix + target + tail
                    val bytes = original.toByteArray(charset)
                    Files.write(file, bytes)
                    val first = TextFileReader.read(file)
                    assertEquals(detectedEncoding, first.encoding)
                    assertEquals(boundary.toLong(), first.nextOffset)
                    assertTrue(first.nextOffset > first.offset)
                    assertTrue(first.hasMore)
                    assertEquals(completePrefix, first.text)

                    val second = TextFileReader.read(file, first.nextOffset, first.encoding)
                    assertEquals(first.nextOffset, second.offset)
                    assertTrue(second.nextOffset > second.offset)
                    assertEquals(bytes.size.toLong(), second.nextOffset)
                    assertFalse(second.hasMore)
                    assertEquals(target + tail, second.text)
                    assertFalse((first.text + second.text).contains('\ufffd'))
                    assertEquals(original, first.text + second.text)
                    assertArrayEquals(bytes, (first.text + second.text).toByteArray(charset))
                }
            }
        } finally {
            Files.delete(file)
        }
    }
}
