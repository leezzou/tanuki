package com.tanuki.storage

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile

/**
 * 仅从 DOCX 的 word/document.xml 提取正文文字，供只读内置预览使用。
 * 不解析样式、表格边框、页眉页脚和图片；压缩包条目、解压字节和文字长度都有上限。
 * parserFor 由调用方提供 Android 的 XmlPullParser 实现，JVM 测试可使用同类实现。
 */
object DocxTextExtractor {
    data class Limits(
        val maxArchiveBytes: Long = 64L * 1024 * 1024,
        val maxEntries: Int = 4096,
        val maxTotalUncompressedBytes: Long = 256L * 1024 * 1024,
        val maxDocumentBytes: Long = 16L * 1024 * 1024,
        val maxTextChars: Int = 200_000,
    )

    data class Result(val text: String, val truncated: Boolean)

    fun extract(file: java.io.File, limits: Limits = Limits(), parserFor: (InputStream) -> XmlPullParser): Result {
        if (!file.isFile) throw IOException("文件不存在或不是普通文件")
        if (file.length() > limits.maxArchiveBytes) throw IOException("此 DOCX 文件过大，已停止预览")
        val zip = try {
            ZipFile(file)
        } catch (failure: IOException) {
            throw IOException("无法读取此 DOCX：文件已加密、损坏或不是有效的 Word 文档", failure)
        }
        zip.use {
            var total = 0L
            var count = 0
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (++count > limits.maxEntries) throw IOException("此 DOCX 压缩包条目过多，已停止预览")
                total += entry.size.coerceAtLeast(0L)
                if (total > limits.maxTotalUncompressedBytes) throw IOException("此 DOCX 解压后过大，已停止预览")
            }
            val document = zip.getEntry("word/document.xml")
                ?: throw IOException("此 DOCX 缺少正文（word/document.xml），无法预览")
            if (document.size > limits.maxDocumentBytes) throw IOException("此 DOCX 正文解压后过大，已停止预览")
            val text = StringBuilder()
            val truncated = zip.getInputStream(document).use { raw ->
                val input = LimitedInputStream(raw, limits.maxDocumentBytes)
                val parser = try {
                    parserFor(input)
                } catch (failure: Exception) {
                    throw IOException("此 DOCX 正文格式不正确，无法预览", failure)
                }
                try {
                    readDocument(parser, limits.maxTextChars, text)
                } catch (failure: XmlPullParserException) {
                    throw IOException("此 DOCX 正文格式不正确，无法预览", failure)
                }
            }
            return Result(text.toString(), truncated)
        }
    }

    private fun readDocument(parser: XmlPullParser, maxChars: Int, output: StringBuilder): Boolean {
        var event = parser.eventType
        var paragraphs = 0
        var inText = false
        var truncated = false
        fun append(value: String) {
            if (value.isEmpty() || truncated) return
            val room = maxChars - output.length
            if (room <= 0) {
                truncated = true
                return
            }
            if (value.length <= room) output.append(value) else {
                output.append(value, 0, room)
                truncated = true
            }
        }
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.DOCDECL -> throw IOException("此 DOCX 包含不受支持的文档类型声明，已停止预览")
                XmlPullParser.START_TAG -> when (localName(parser)) {
                    // 每个 w:p 结束都换行，空段落因此保留为独立空行。
                    "p" -> if (paragraphs++ > 0) append("\n")
                    "t" -> inText = true
                    "br", "cr" -> append("\n")
                    "tab" -> append("\t")
                }
                XmlPullParser.END_TAG -> if (localName(parser) == "t") inText = false
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> if (inText) append(parser.text.orEmpty())
            }
            if (truncated) break
            event = parser.next()
        }
        return truncated
    }

    private fun localName(parser: XmlPullParser): String =
        (parser.name ?: "").substringAfter(':').lowercase(Locale.ROOT)

    /** 不信任中央目录声明的解压后大小，实际解压超过上限立即停止。 */
    private class LimitedInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L

        private fun guard(read: Long) {
            if (read <= 0) return
            count += read
            if (count > limit) throw IOException("此 DOCX 正文解压后超过上限，已停止预览")
        }

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) guard(1)
            return value
        }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            val read = super.read(bytes, offset, length)
            guard(read.toLong())
            return read
        }

        override fun skip(amount: Long): Long {
            val skipped = super.skip(amount)
            guard(skipped)
            return skipped
        }
    }
}
