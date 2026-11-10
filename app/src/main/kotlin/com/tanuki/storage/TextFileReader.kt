package com.tanuki.storage

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
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

/**
 * 按字节游标分段解码；游标只停在完整字符后，不将整个大文件装入内存。
 *
 * 编码识别严格按以下顺序进行，任何一步都不会把“无法解码”抛给界面：
 * BOM（UTF-8/UTF-16LE/BE/UTF-32）→ UTF-8 严格校验 → 无 BOM UTF-16 启发式
 * → 二进制判定 → GB18030（覆盖 GBK/GB2312）→ Big5 → Shift_JIS → Latin-1 兜底。
 * 传统编码候选都可以严格解码时，用常用字命中率降低误判；仍无法确定时按上述
 * 顺序取第一个候选，并允许用户在查看器里手动切换编码。
 */
object TextFileReader {
    const val PAGE_BYTES = 64 * 1024

    /** 编码探测只读取文件头部的有界样本，不为识别编码扫描整个文件。 */
    const val DETECT_BYTES = 8 * 1024

    /** 单次查看最多显示 4 MiB，与界面截断提示保持一致。 */
    const val MAX_VIEW_BYTES = 4L * 1024 * 1024

    /** 二进制文件使用此占位编码，界面据此切换到二进制提示，不进入文本视图。 */
    const val BINARY_ENCODING = "二进制"

    /** 查看器手动切换支持的可选编码；GB18030 覆盖 GBK/GB2312。 */
    val ENCODINGS = listOf("自动", "UTF-8", "GB18030", "UTF-16LE", "UTF-16BE", "UTF-16", "Big5", "ISO-8859-1")

    /**
     * 读取接口允许的编码 = 菜单项 + 自动探测可能返回的内部编码。
     * Shift_JIS/UTF-32 不放入手动菜单，但查看器会把第一页的探测结果原样传给后续页，
     * 因此读取校验必须接受它们，否则超过一页就会拒绝翻页。
     */
    private val READABLE_ENCODINGS = (ENCODINGS + listOf("Shift_JIS", "UTF-32", "UTF-32LE", "UTF-32BE")).toSet()

    data class Page(
        val text: String,
        val offset: Long,
        val nextOffset: Long,
        val totalBytes: Long,
        val encoding: String,
        val hasMore: Boolean,
        val binary: Boolean = false,
    )

    fun read(path: Path, offset: Long = 0, encoding: String = "自动"): Page {
        FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
            return read(ChannelSeekableInput(channel), offset, encoding)
        }
    }

    /** 调用方负责关闭 [source]；本函数只读取有界样本和当前页。 */
    internal fun read(source: SeekableInput, offset: Long = 0, encoding: String = "自动"): Page {
        require(offset >= 0) { "文本位置无效" }
        require(encoding in READABLE_ENCODINGS) { "不支持此文本编码" }
        val size = source.size
        check(size == null || offset <= size) { "文件已缩小，请重新打开" }

        // 只读文件头样本；它同时充当第一页的前缀，避免重复读取同一段字节。
        val sampleLength = when {
            size == null -> DETECT_BYTES
            size <= 0L -> 0
            else -> minOf(DETECT_BYTES.toLong(), size).toInt()
        }
        val sample = ByteArray(sampleLength)
        source.seek(0)
        val sampleCount = readAtMost(source, sample, 0, sample.size)
        val sampleAtEof = (size != null && sampleCount.toLong() >= size) || sampleCount < sample.size

        val detected = when (encoding) {
            "自动" -> detect(sample, sampleCount, sampleAtEof)
            // 手动选 UTF-16 时仍用 BOM/启发式确定端序，否则跨页会退回大端默认值。
            "UTF-16" -> resolveManualUtf16(sample, sampleCount, sampleAtEof)
            else -> Detected(encoding, if (offset == 0L) bomLengthFor(encoding, sample, sampleCount) else 0, binary = false)
        }

        if (detected.binary) {
            // 二进制文件不进入文本解码，也不消耗后续页。
            return Page("", offset, offset, size ?: ExternalDocumentPolicy.UNKNOWN_SIZE, BINARY_ENCODING, false, true)
        }

        val limit = size?.let { minOf(it, MAX_VIEW_BYTES) } ?: MAX_VIEW_BYTES
        val start = if (offset == 0L) detected.bomBytes.toLong() else offset
        if (start >= limit) {
            // 已到 4 MiB 显示上限；保留实际总大小供界面提示“已截断”。
            val total = size ?: ExternalDocumentPolicy.UNKNOWN_SIZE
            return Page("", offset, offset, total, detected.encoding, false, false)
        }

        val capacity = minOf(PAGE_BYTES.toLong(), limit - start).toInt()
        val buffer = ByteArray(capacity)
        var filled = 0
        if (start < sampleCount) {
            // 顺序流不能在读完表头样本后回退；复用样本里仍在当前页范围内的前缀。
            val from = start.toInt()
            val copied = minOf(sampleCount - from, capacity)
            System.arraycopy(sample, from, buffer, 0, copied)
            filled = copied
        }
        var eof = size == null && sampleAtEof
        if (filled < capacity && !eof) {
            source.seek(start + filled)
            while (filled < capacity) {
                val read = source.read(buffer, filled, capacity - filled)
                if (read < 0) { eof = true; break }
                if (read == 0) { eof = true; break }
                filled += read
            }
        } else if (size != null && start + filled >= size) {
            eof = true
        }

        val atEof = if (size != null) start + filled >= size else eof
        // 4 MiB 显示上限可能落在多字节字符中间；此时只显示完整字符，不能把残字节当 Latin-1。
        val reachesViewCap = limit == MAX_VIEW_BYTES && start + filled >= MAX_VIEW_BYTES &&
            (size == null || size > MAX_VIEW_BYTES)
        val decoded = decodePage(buffer, filled, detected.encoding, atEof, atViewCap = reachesViewCap)
        val next = start + decoded.consumed
        val splitAtViewCap = reachesViewCap && !atEof && decoded.consumed < filled
        // 未知大小的顺序流读到 EOF 后必须停止分页，并把 EOF 处游标作为实际总大小；
        // 未到 EOF 且未到 4 MiB 上限时才继续返回“大小未知”。
        val hasMore = when {
            splitAtViewCap -> false
            size == null && eof -> false
            else -> next < limit
        }
        val total = size ?: if (eof) next else ExternalDocumentPolicy.UNKNOWN_SIZE
        return Page(decoded.text, offset, next, total, detected.encoding, hasMore, false)
    }

    private data class Detected(val encoding: String, val bomBytes: Int, val binary: Boolean)

    private data class Decoded(val text: String, val consumed: Int)

    private fun readAtMost(source: SeekableInput, buffer: ByteArray, offset: Int, count: Int): Int {
        var read = 0
        while (read < count) {
            val current = source.read(buffer, offset + read, count - read)
            if (current < 0) break
            if (current == 0) break
            read += current
        }
        return read
    }

    /** 按 BOM 识别；UTF-32LE 的四字节 BOM 必须先于 UTF-16LE 判断。 */
    private fun detectBom(sample: ByteArray, count: Int): Detected? = when {
        count >= 4 && sample[0] == 0xff.toByte() && sample[1] == 0xfe.toByte() &&
            sample[2] == 0x00.toByte() && sample[3] == 0x00.toByte() -> Detected("UTF-32LE", 4, false)
        count >= 4 && sample[0] == 0x00.toByte() && sample[1] == 0x00.toByte() &&
            sample[2] == 0xfe.toByte() && sample[3] == 0xff.toByte() -> Detected("UTF-32BE", 4, false)
        count >= 3 && sample[0] == 0xef.toByte() && sample[1] == 0xbb.toByte() &&
            sample[2] == 0xbf.toByte() -> Detected("UTF-8", 3, false)
        count >= 2 && sample[0] == 0xff.toByte() && sample[1] == 0xfe.toByte() -> Detected("UTF-16LE", 2, false)
        count >= 2 && sample[0] == 0xfe.toByte() && sample[1] == 0xff.toByte() -> Detected("UTF-16BE", 2, false)
        else -> null
    }

    /** 手动选中的编码与文件 BOM 一致时才跳过 BOM；不一致时由用户明确选择负责。 */
    private fun bomLengthFor(encoding: String, sample: ByteArray, count: Int): Int {
        val bom = detectBom(sample, count) ?: return 0
        return if (bom.encoding == encoding) bom.bomBytes else 0
    }

    private fun detect(sample: ByteArray, count: Int, atEof: Boolean): Detected {
        detectBom(sample, count)?.let { return it }
        if (count == 0) return Detected("UTF-8", 0, false)

        // UTF-8 严格校验优先；含 NUL 或明显异常控制字符的样本留给 UTF-16 启发式/二进制判定。
        if (looksLikeUtf8(sample, count, atEof)) return Detected("UTF-8", 0, false)

        detectUtf16WithoutBom(sample, count, atEof)?.let { return Detected(it, 0, false) }

        if (looksBinary(sample, count)) return Detected(BINARY_ENCODING, 0, true)

        // 传统编码按要求的顺序尝试；只有当前候选可严格解码时才继续比较下一个。
        val candidates = listOf("GB18030", "Big5", "Shift_JIS")
        val decoded = candidates.mapNotNull { name ->
            decodeStrict(sample, count, name, atEof)?.let { name to it }
        }
        var best: Pair<String, Double>? = null
        for ((name, text) in decoded) {
            // 候选编码各自的可读性权重不同：中日韩文本通常不含假名，
            // 用 Shift_JIS 的假名权重去评 GB18030/Big5 会把乱码误判成可信结果。
            val score = scoreText(text, name)
            if (best == null || score > best.second + 0.05) best = name to score
            // 顺序靠前的候选已经足够自然时直接采用，符合 GB18030→Big5→Shift_JIS 的优先级。
            if (score >= STRONG_SCORE) break
        }
        best?.let { (name, score) ->
            if (score >= WEAK_SCORE) return Detected(name, 0, false)
        }
        decoded.firstOrNull()?.let { return Detected(it.first, 0, false) }
        return Detected("ISO-8859-1", 0, false)
    }

    /** 手动 UTF-16 只确定端序，不改变用户“按 UTF-16 解码”的选择。 */
    private fun resolveManualUtf16(sample: ByteArray, count: Int, atEof: Boolean): Detected {
        val bom = detectBom(sample, count)
        if (bom != null && bom.encoding.startsWith("UTF-16")) {
            return Detected(bom.encoding, bom.bomBytes, false)
        }
        if (bom != null && bom.encoding.startsWith("UTF-32")) {
            // 用户明确选择 UTF-16，不因 UTF-32 BOM 改变编码，也不跳过 BOM。
            return Detected("UTF-16", 0, false)
        }
        detectUtf16WithoutBom(sample, count, atEof)?.let { return Detected(it, 0, false) }
        return Detected("UTF-16", 0, false)
    }

    private fun looksLikeUtf8(sample: ByteArray, count: Int, atEof: Boolean): Boolean {
        var control = 0
        for (index in 0 until count) {
            val value = sample[index].toInt() and 0xff
            if (value == 0) return false
            if ((value < 0x20 && value !in 0x09..0x0d) || value == 0x7f) control++
        }
        if (control * 20 > count) return false
        val text = decodeStrict(sample, count, "UTF-8", atEof) ?: return false
        return text.none { it == '\u0000' || (it < ' ' && it !in "\n\r\t\u000c") }
    }

    /**
     * 无 BOM UTF-16 启发式：先看大量 0x00 是否按奇偶交错（ASCII 为主的 UTF-16），
     * 再比较两种端序解码后的可读性（纯中日韩文本可能没有 NUL）。
     */
    private fun detectUtf16WithoutBom(sample: ByteArray, count: Int, atEof: Boolean): String? {
        if (count < 8) return null
        val pairs = count / 2
        if (pairs < 4) return null
        var nullAtEven = 0
        var nullAtOdd = 0
        for (index in 0 until pairs * 2) {
            if (sample[index] == 0.toByte()) {
                if (index and 1 == 0) nullAtEven++ else nullAtOdd++
            }
        }
        val evenRatio = nullAtEven.toDouble() / pairs
        val oddRatio = nullAtOdd.toDouble() / pairs
        val littleEndian = decodeStrict(sample, pairs * 2, "UTF-16LE", atEof)
        val bigEndian = decodeStrict(sample, pairs * 2, "UTF-16BE", atEof)

        if (oddRatio >= 0.20 && evenRatio <= 0.05 && littleEndian != null &&
            scoreText(littleEndian) >= 0.70
        ) {
            return "UTF-16LE"
        }
        if (evenRatio >= 0.20 && oddRatio <= 0.05 && bigEndian != null &&
            scoreText(bigEndian) >= 0.70
        ) {
            return "UTF-16BE"
        }

        val littleScore = littleEndian?.let(::scoreText) ?: -1.0
        val bigScore = bigEndian?.let(::scoreText) ?: -1.0
        if (maxOf(littleScore, bigScore) >= 0.82) {
            if (littleScore >= bigScore + 0.12) return "UTF-16LE"
            if (bigScore >= littleScore + 0.12) return "UTF-16BE"
        }
        return null
    }

    /** 原始字节层面的二进制判定；UTF-16/32 已在 BOM 或启发式阶段排除。 */
    private fun looksBinary(sample: ByteArray, count: Int): Boolean {
        if (count == 0) return false
        var nulls = 0
        var controls = 0
        for (index in 0 until count) {
            val value = sample[index].toInt() and 0xff
            when {
                value == 0 -> nulls++
                (value < 0x20 && value !in 0x09..0x0d) || value == 0x7f -> controls++
            }
        }
        return nulls.toDouble() / count > 0.01 || controls.toDouble() / count > 0.10
    }

    private fun decodeStrict(bytes: ByteArray, count: Int, encoding: String, atEof: Boolean): String? {
        if (count == 0) return ""
        val charset = runCatching { Charset.forName(encoding) }.getOrNull() ?: return null
        val input = ByteBuffer.wrap(bytes, 0, count)
        val chars = CharBuffer.allocate(count + 8)
        return try {
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val result = decoder.decode(input, chars, atEof)
            if (result.isError) {
                null
            } else {
                chars.flip()
                chars.toString()
            }
        } catch (_: CharacterCodingException) {
            null
        }
    }

    /**
     * 解码当前页；界面解码一律使用替代字符，任何非法字节都不会变成用户可见的异常。
     * 只消费到完整字符边界，下一页从剩余字节继续，跨页的多字节字符不会被拆坏。
     */
    private fun decodePage(
        bytes: ByteArray,
        count: Int,
        encoding: String,
        atEof: Boolean,
        atViewCap: Boolean = false,
    ): Decoded {
        if (count == 0) return Decoded("", 0)
        val charset = runCatching { Charset.forName(encoding) }.getOrDefault(StandardCharsets.ISO_8859_1)
        val input = ByteBuffer.wrap(bytes, 0, count)
        val chars = CharBuffer.allocate(count + 8)
        try {
            val decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
            decoder.decode(input, chars, atEof)
        } catch (_: CharacterCodingException) {
            // 替代模式理论上不会走到这里；仍按 Latin-1 兜底，绝不把解码异常抛给界面。
            input.position(0)
            chars.clear()
            chars.put(String(bytes, 0, count, StandardCharsets.ISO_8859_1))
            input.position(count)
        }
        var consumed = input.position()
        if (consumed == 0) {
            if (atViewCap) {
                // 上限切在多字节字符内部：不显示伪造字符，由调用方在完整字符处结束本页。
                return Decoded("", 0)
            }
            // 页首落在不完整字符或异常序列上：按 Latin-1 消费本页，保证翻页始终前进。
            chars.clear()
            chars.put(String(bytes, 0, count, StandardCharsets.ISO_8859_1))
            consumed = count
        } else if (input.hasRemaining() && atEof) {
            // 输入已结束但仍有残留字节：按 Latin-1 补入，避免静默丢内容或卡在读不满的一页。
            val remaining = String(bytes, consumed, count - consumed, StandardCharsets.ISO_8859_1)
            if (chars.position() + remaining.length <= chars.capacity()) chars.put(remaining)
            consumed = count
        }
        chars.flip()
        return Decoded(chars.toString(), consumed)
    }

    private const val STRONG_SCORE = 0.90
    private const val WEAK_SCORE = 0.45

    /**
     * 候选编码的可读性评分：常用字、中日韩文字、假名和 ASCII 得分高，
     * 控制字符、未分配码位、私用区和替换符扣分。用于在 GB18030 能“解码”
     * 但结果明显像乱码时继续尝试 Big5/Shift_JIS，而不是仅凭可解码就下结论。
     */
    private fun scoreText(text: String, encoding: String? = null): Double {
        if (text.isEmpty()) return 0.0
        var score = 0.0
        var total = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            index += Character.charCount(codePoint)
            total++
            score += when {
                codePoint == 0 -> -4.0
                codePoint == 0x09 || codePoint == 0x0a || codePoint == 0x0c || codePoint == 0x0d -> 1.0
                codePoint in 0x20..0x7e -> 1.0
                isCommonCjk(codePoint) -> 1.0
                isCjk(codePoint) -> 0.55
                // Shift_JIS 文本里的假名是正常内容；GB18030/Big5 文本里成片的假名更可能是错判。
                isKana(codePoint) -> if (encoding == null || encoding == "Shift_JIS") 1.0 else 0.55
                isHangul(codePoint) -> 1.0
                codePoint in 0x3000..0x303f || codePoint in 0xff01..0xff60 -> 0.8
                Character.isISOControl(codePoint) -> -3.0
                Character.isWhitespace(codePoint) -> 0.8
                codePoint == 0xfffd -> -3.0
                Character.getType(codePoint) == Character.UNASSIGNED.toInt() ||
                    Character.getType(codePoint) == Character.PRIVATE_USE.toInt() ||
                    Character.getType(codePoint) == Character.SURROGATE.toInt() -> -3.0
                else -> 0.25
            }
        }
        return score / total
    }

    private fun isCjk(codePoint: Int): Boolean =
        codePoint in 0x3400..0x4dbf || codePoint in 0x4e00..0x9fff ||
            codePoint in 0x20000..0x2fa1f

    private fun isKana(codePoint: Int): Boolean =
        codePoint in 0x3040..0x30ff || codePoint in 0x31f0..0x31ff || codePoint in 0xff66..0xff9d

    private fun isHangul(codePoint: Int): Boolean =
        codePoint in 0xac00..0xd7af || codePoint in 0x1100..0x11ff

    private fun isCommonCjk(codePoint: Int): Boolean =
        codePoint <= 0xffff && COMMON_CJK.indexOf(codePoint.toChar()) >= 0

    /** 高频简繁汉字与常见标点；只作为候选编码之间的可读性参考，不参与最终兜底。 */
    private const val COMMON_CJK =
        "的一是不了在人有我他这个们中来上大为和国地到以说时要就出会可也你对生能而子那得于着下自之年过发后作里用道行所然家种事成方多经么去法学如都同现当没动面起看定天分还进好小部其些主样理心她本前开但因只从想实日军者意无力它与长把" +
            "机十民第公此已工使情明性知全三又关点正业外将两高间由问很最重并物手应战向头文体政美相见被利什二等产或新己制身果加西斯月话合回特代内信表化老给世位次度门任常先海通教儿原东声提立及比员解水名" +
            "真论处走义各入几口认条平系气题活尔更别打女变四神总何电数安少报才结反受目太量再感建务做接必场件计管期市直德资命山金指克许统区保至队形社便空决治展马科司五基眼书非则听白却界达光放强即像" +
            "难且权思王象完设式色路记南品住告类求据程北边死张该交规万取拉格望觉术领共确传师观清今切院让识候带导争运笑飞风步改收根干造言联持组每济车亲极林服快办议往元英士证近失转夫令准布始怎呢存未" +
            "远叫台单影具罗字爱击流备兵连调深商算质团集百需价花党华城石级整府离况亚请技际约示复病息究线似官火断精满支视消越器容照须九增研写称企八功吗包片史委乎查轻易早曾除农找装广显吧阿李标谈吃图" +
            "念六引历首医局突专费号尽另周较注语仅考落青随选列武红响虽推势参希古众构房半节土投某案黑维革划敌致陈律足态护七兴派孩验责营星够章音跟志底站严巴例防族供效续施留讲型料终答紧黄绝奇察母京段依" +
            "批群项故按河米围江织害斗双境客纪采举杀攻父苏密低朝友诉止细愿千值仍男钱破网热助倒育属坐帝限船脸职速刻乐否刚威毛状率甚独球般普怕弹校苦创假久错承印晚兰试股拿脑预谁益阳若哪微尼继送急血惊" +
            "伤素药适波夜省初喜卫源食险待述陆习置居劳财环排福纳欢雷警获模充负云停木游龙树疑层冷洲冲射略范竟句室异激汉村哈策演简卡罪判担州静退既衣您宗积余痛检差富灵协角占配征修皮挥胜降阶审沉坚善妈" +
            "刘读啊超免压银买皇养伊怀执副乱抗犯追帮宣佛岁航优怪香著田铁控税左右份穿艺背阵草脚概恶块顿敢守酒岛托央户烈洋哥索胡款靠评版宝座释景顾弟登货互付伯慢欧换闻危忙核暗姐介坏讨丽良序升监临亮露" +
            "永呼味野架域沙掉括舰鱼杂误湾吉减编楚肯测败屋跑梦散温困剑渐封救贵枪缺楼县尚毫移娘朋画班智亦耳恩短掌恐遗固席松秘谢鲁遇康虑幸均销钟诗藏赶剧票损忽巨炮旧端探湖录叶春乡附吸予礼港雨呀板庭妇" +
            "归睛饭额含顺输摇招婚脱补谓督毒油疗旅泽材灭逐莫笔亡鲜词圣择寻厂睡博勒烟授诺伦岸奥唐卖俄炸载洛健堂旁宫喝借君禁阴园谋宋避抓荣姑孙逃牙束跳顶玉镇雪午练迫爷篇肉嘴馆遍凡础洞卷坦牛宁纸诸训私" +
            "庄祖丝翻暴森塔默握戏隐熟骨访弱蒙歌店鬼软典欲萨伙遭盘爸扩盖弄雄稳忘亿刺拥徒姆杨齐赛趣曲刀床迎冰虚玩析窗醒妻透购替塞努休虎扬途侵刑绿兄迅套贸毕唯谷轮库迹尤竞街促延震弃甲伟麻川申缓潜闪售" +
            "灯针哲络抵朱埃抱鼓植纯夏忍页杰筑折郑贝尊吴秀混臣雅振染盛怒舞圆搞狂措姓残秋培迷诚宽宇猛摆梅毁伸摩盟末乃悲拍丁赵硬麦蒋操耶阻订彩抽赞魔纷沿喊违妹浪汇币丰蓝殊献桌啦瓦莱援译夺汽烧距裁偏符" +
            "勇触课敬哭懂墙袭召罚侠厅拜巧侧韩冒债曼融惯享戴童犹乘挂奖绍厚纵障讯涉彻刊丈爆乌役描洗玛患妙镜唱烦签仙彼弗症仿倾牌陷鸟轰咱菜闭奋庆撤泪茶疾缘播朗杜奶季丹狗尾仪偷奔珠虫驻孔宜艾桥淡翼恨" +
            "繁寒伴叹旦愈潮粮缩罢聚径恰挑袋灰捕徐珍幕映裂泰隔启尖忠累炎暂估泛荒偿横拒瑞忆孤鼻闹羊呆厉衡胞零穷舍码赫婆魂灾洪腿胆津俗辩胸晓劲贫仁偶辑邦恢赖圈摸仰润堆碰艇稍迟辆废净凶署壁御奉旋冬矿" +
            "抬蛋晨伏吹鸡倍糊秦盾杯租骑乏隆诊奴摄丧污渡旗甘耐凭扎抢绪粗肩梁幻菲皆碎宙叔岩荡综爬荷悉蒂返井壮薄悄扫敏碍殖详迪矛霍允幅撒剩凯颗骂赏液番箱贴漫酸郎腰舒眉忧浮辛恋餐吓挺励辞艘键伍峰尺昨" +
            "黎辈贯侦滑券崇扰宪绕趋慈乔阅汗枝拖墨胁插箭腊粉泥氏彭拔骗凤慧媒佩愤扑龄驱惜豪掩兼跃尸肃帕驶堡届欣惠册储飘桑闲惨洁踪勃宾频仇磨递邪撞拟滚奏巡颜剂绩贡疯坡瞧截燃焦殿伪柳锁逼颇昏劝呈搜勤" +
            "戒驾漂饮曹朵仔柔俩孟腐幼践籍牧凉牲佳娜浓芳稿竹腹跌逻垂遵脉貌柏狱猜怜惑陶兽帐饰贷昌叙躺钢沟寄扶铺邓寿惧询汤盗肥尝匆辉奈扣廷澳嘛董迁凝慰厌脏腾幽尊缴谎遭姚闹愤" +
            // 常用繁体字与常见异体，避免 Big5 文本因为只命中简体高频字而被 GB18030 抢走。
            "臺灣繁體中文測試國學語言說時間會為與這個們來對開關發現實際應該還進過讓從麼樣點電腦網路檔案編輯碼顯示確認錯誤讀寫檢查轉換數據資料庫連線圖片聲音視頻預覽設定支援環境變更條件結果記錄項目類型問題幫助訊息軟體硬體系統執行應用程式開啟關閉選擇複製貼上刪除儲存載入匯出匯入傳送接收等候暫停繼續完成失敗成功警告提示取消無長短舊壞熱晚昨週億錢幣價買賣付銀帳戶卡號稱姓地址話機郵箱密碼登註冊權限員廠商師課書筆記紙張門窗牆房間廚浴廳臥園樹雲陽亮國際鄉車輛飛船鐵捷運計腳踏貨救護消防設備零件材能源電力風核石油氣煤炭污染垃圾資源回收再生永續發展"
}
