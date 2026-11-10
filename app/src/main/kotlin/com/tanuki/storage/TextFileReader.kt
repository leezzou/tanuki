package com.tanuki.storage

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * 文本分页读取的最小可随机访问来源。
 *
 * 文件路径和 content:// 描述符都实现同一接口；内置查看器每页只读取
 * [TextFileReader.PAGE_BYTES] 附近的数据，不把整个大文件装入内存。
 */
internal interface SeekableInput : Closeable {
    /** 已知总字节数；提供方不返回大小时为 null。 */
    val size: Long?
    fun seek(offset: Long)
    fun read(buffer: ByteArray, offset: Int, count: Int): Int
}

/** 基于 FileChannel 的随机访问来源，用于工作区文件和 SAF 返回的可随机读描述符。 */
internal class ChannelSeekableInput(private val channel: FileChannel) : SeekableInput {
    override val size: Long get() = channel.size()
    override fun seek(offset: Long) { channel.position(offset) }
    override fun read(buffer: ByteArray, offset: Int, count: Int): Int =
        channel.read(ByteBuffer.wrap(buffer, offset, count))
    override fun close() = channel.close()
}

/**
 * 只有顺序流的提供方回退：每次分页重新打开流，向前跳过 offset。
 * 内存仍只保留一页；代价是跳页时可能需要重新读取前面的字节。
 */
internal class StreamSeekableInput(
    private val input: InputStream,
    override val size: Long?,
) : SeekableInput {
    private var position = 0L

    override fun seek(offset: Long) {
        require(offset >= 0) { "文本位置无效" }
        if (offset < position) throw IOException("此文件来源不支持向后跳转，请重新打开文件")
        var remaining = offset - position
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                position += skipped
                remaining -= skipped
            } else {
                if (input.read() < 0) throw IOException("文件已缩短，请重新打开")
                position += 1
                remaining -= 1
            }
        }
    }

    override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
        val read = input.read(buffer, offset, count)
        if (read > 0) position += read
        return read
    }

    override fun close() = input.close()
}

/** 按字节游标分段解码；游标只停在完整字符后，不将整个大文件装入内存。 */
object TextFileReader {
    const val PAGE_BYTES = 64 * 1024
    val ENCODINGS = listOf("自动", "UTF-8", "UTF-16LE", "UTF-16BE", "GB18030", "Big5", "ISO-8859-1")
    data class Page(val text: String, val offset: Long, val nextOffset: Long, val totalBytes: Long,
                    val encoding: String, val hasMore: Boolean)

    fun read(path: Path, offset: Long = 0, encoding: String = "自动"): Page {
        FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
            return read(ChannelSeekableInput(channel), offset, encoding)
        }
    }

    /** 调用方负责关闭 [source]；本函数只读取表头和当前页。 */
    internal fun read(source: SeekableInput, offset: Long = 0, encoding: String = "自动"): Page {
        require(offset >= 0) { "文本位置无效" }
        require(encoding in ENCODINGS) { "不支持此文本编码" }
        val size = source.size
        check(size == null || offset <= size) { "文件已缩小，请重新打开" }
        source.seek(0)
        val header = ByteArray(4)
        var headerCount = 0
        while (headerCount < header.size) {
            val read = source.read(header, headerCount, header.size - headerCount)
            if (read < 0) break
            if (read == 0) continue
            headerCount += read
        }
        val signature = header
        val bom = when {
            headerCount >= 3 && signature[0] == 0xef.toByte() && signature[1] == 0xbb.toByte() && signature[2] == 0xbf.toByte() -> "UTF-8" to 3
            headerCount >= 2 && signature[0] == 0xff.toByte() && signature[1] == 0xfe.toByte() -> "UTF-16LE" to 2
            headerCount >= 2 && signature[0] == 0xfe.toByte() && signature[1] == 0xff.toByte() -> "UTF-16BE" to 2
            else -> "UTF-8" to 0
        }
        val selected = if (encoding == "自动") bom.first else encoding
        val start = if (offset == 0L && selected == bom.first) bom.second.toLong() else offset
        val capacity = if (size != null) minOf(PAGE_BYTES.toLong(), (size - start).coerceAtLeast(0)).toInt() else PAGE_BYTES
        val buffer = ByteBuffer.allocate(capacity)
        if (start <= headerCount) {
            // 顺序流不能在读完表头后回退；把 start 之后仍在表头缓冲中的字节作为本页前缀，
            // 再从流当前位置继续读取剩余部分。UTF-8 BOM 的 start=3、表头已读 4 字节也适用。
            val from = start.toInt()
            buffer.put(signature, from, minOf(headerCount - from, capacity))
        } else {
            source.seek(start)
        }
        var eof = false
        while (buffer.hasRemaining()) {
            val read = source.read(buffer.array(), buffer.position(), buffer.remaining())
            if (read < 0) { eof = true; break }
            if (read == 0) continue
            buffer.position(buffer.position() + read)
        }
        val readBytes = buffer.position()
        buffer.flip()
        val chars = CharBuffer.allocate(PAGE_BYTES)
        val decoder = Charset.forName(selected).newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        // 已知大小按末尾判断；未知大小依赖本次读取是否遇到 EOF。
        val end = if (size != null) start + readBytes >= size else eof
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
        val hasMore = if (size != null) next < size else !eof
        val total = size ?: if (!hasMore) next else ExternalDocumentPolicy.UNKNOWN_SIZE
        return Page(text, offset, next, total, selected, hasMore)
    }
}
