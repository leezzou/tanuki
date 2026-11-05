package com.tanuki.storage

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** 按字节游标分段解码；游标只停在完整字符后，不将整个大文件装入内存。 */
object TextFileReader {
    const val PAGE_BYTES = 64 * 1024
    val ENCODINGS = listOf("自动", "UTF-8", "UTF-16LE", "UTF-16BE", "GB18030", "Big5", "ISO-8859-1")
    data class Page(val text: String, val offset: Long, val nextOffset: Long, val totalBytes: Long,
                    val encoding: String, val hasMore: Boolean)

    fun read(path: Path, offset: Long = 0, encoding: String = "自动"): Page {
        require(offset >= 0) { "文本位置无效" }
        require(encoding in ENCODINGS) { "不支持此文本编码" }
        FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
            val size = channel.size()
            check(offset <= size) { "文件已缩小，请重新打开" }
            val header = ByteBuffer.allocate(4)
            channel.read(header, 0)
            val signature = header.array()
            val bom = when {
                header.position() >= 3 && signature[0] == 0xef.toByte() && signature[1] == 0xbb.toByte() && signature[2] == 0xbf.toByte() -> "UTF-8" to 3
                header.position() >= 2 && signature[0] == 0xff.toByte() && signature[1] == 0xfe.toByte() -> "UTF-16LE" to 2
                header.position() >= 2 && signature[0] == 0xfe.toByte() && signature[1] == 0xff.toByte() -> "UTF-16BE" to 2
                else -> "UTF-8" to 0
            }
            val selected = if (encoding == "自动") bom.first else encoding
            val start = if (offset == 0L && selected == bom.first) bom.second.toLong() else offset
            val buffer = ByteBuffer.allocate(minOf(PAGE_BYTES.toLong(), size - start).toInt())
            channel.position(start)
            while (buffer.hasRemaining() && channel.read(buffer) >= 0) { }
            buffer.flip()
            val chars = CharBuffer.allocate(PAGE_BYTES)
            val decoder = Charset.forName(selected).newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            val end = start + buffer.remaining() >= size
            val result = decoder.decode(buffer, chars, end)
            if (result.isError) throw IOException("无法使用 $selected 解码，请切换文本编码或使用其他应用打开")
            val consumed = buffer.position()
            if (buffer.hasRemaining() && consumed == 0) throw IOException("无法识别此文本内容，请切换编码")
            chars.flip()
            val text = chars.toString()
            if (text.any { it == '\u0000' || (it < ' ' && it !in "\n\r\t\u000c") }) {
                throw IOException("此文件包含二进制内容，请使用其他应用打开")
            }
            val next = start + consumed
            return Page(text, offset, next, size, selected, next < size)
        }
    }
}
