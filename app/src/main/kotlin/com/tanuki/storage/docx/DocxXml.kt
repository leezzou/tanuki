package com.tanuki.storage.docx

import org.xmlpull.v1.XmlPullParser
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale

/** 已被识别、可直接展示给用户的中文格式错误。 */
internal class DocxFormatException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** 文档超过预览上限时提前结束，保留已解析内容并标记截断，不当作损坏。 */
internal class DocxTruncatedException : RuntimeException()

/** XML 元素数、嵌套深度和文字总量预算；解压字节另由 [LimitedInputStream] 按实际读取计数。 */
internal class DocxBudget(private val limits: DocxLimits) {
    var elements: Int = 0
        private set
    private var depth: Int = 0
    private var textChars: Int = 0

    fun enter() {
        elements++
        if (elements > limits.maxElements) {
            throw DocxFormatException("此 DOCX 结构过于复杂（元素过多），已停止预览。")
        }
        depth++
        if (depth > limits.maxXmlDepth) {
            throw DocxFormatException("此 DOCX 的 XML 嵌套过深，已停止预览。")
        }
    }

    fun exit() {
        if (depth > 0) depth--
    }

    fun text(count: Int) {
        if (count <= 0) return
        textChars = (textChars + count).coerceAtMost(Int.MAX_VALUE)
        if (textChars > limits.maxTextChars) throw DocxTruncatedException()
    }
}

/** 不信任中央目录声明的解压后大小；实际读取超过上限立即失败。 */
internal class LimitedInputStream(
    input: InputStream,
    private val limit: Long,
    private val message: String,
) : FilterInputStream(input) {
    private var count = 0L

    private fun guard(read: Long) {
        if (read <= 0) return
        count += read
        if (count > limit) throw IOException(message)
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

/** OOXML 的轻量 XML 读取工具；属性按本地名匹配，兼容带/不带命名空间前缀的实现。 */
internal object DocxXml {
    private const val MAX_PATH_LENGTH = 1024
    private const val MAX_SEGMENT_LENGTH = 255

    /** 保留 OOXML 的驼峰大小写（pPr/sectPr/gridCol），只去掉命名空间前缀。 */
    fun localName(parser: XmlPullParser): String =
        (parser.name ?: "").substringAfter(':')

    fun attribute(parser: XmlPullParser, name: String): String? {
        for (index in 0 until parser.attributeCount) {
            if (parser.getAttributeName(index).substringAfter(':').equals(name, ignoreCase = true)) {
                return parser.getAttributeValue(index)
            }
        }
        return null
    }

    fun intAttribute(parser: XmlPullParser, name: String): Int? =
        attribute(parser, name)?.trim()?.toIntOrNull()

    fun longAttribute(parser: XmlPullParser, name: String): Long? =
        attribute(parser, name)?.trim()?.toLongOrNull()

    /** OOXML 布尔属性缺省为 true；显式的 0/false/off/none 表示关闭。 */
    fun onOff(parser: XmlPullParser, name: String = "val"): Boolean {
        val value = attribute(parser, name)?.trim()?.lowercase(Locale.ROOT) ?: return true
        return value !in setOf("0", "false", "off", "none")
    }

    fun color(parser: XmlPullParser, name: String = "val"): Int? {
        val value = attribute(parser, name)?.trim()?.removePrefix("#") ?: return null
        if (value.length != 6 || !value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
        return (0xFF000000L or value.toLong(16)).toInt()
    }

    fun highlight(parser: XmlPullParser): Int? {
        val value = attribute(parser, "val")?.trim()?.lowercase(Locale.ROOT) ?: return null
        return HIGHLIGHTS[value]
    }

    /** 压缩包条目名必须是由安全字符组成的相对路径；拒绝绝对路径、反斜杠、`.`/`..` 与控制字符。 */
    fun isSafePath(path: String): Boolean {
        if (path.isEmpty() || path.length > MAX_PATH_LENGTH || path.startsWith("/")) return false
        if (path.any { it == '\\' || it == '\u0000' || it == ':' || it.isISOControl() }) return false
        val parts = path.split('/')
        return parts.isNotEmpty() && parts.all(::isSafeSegment)
    }

    private fun isSafeSegment(part: String): Boolean =
        part.isNotEmpty() && part != "." && part != ".." && part.length <= MAX_SEGMENT_LENGTH &&
            part.none { it == '\\' || it == ':' || it == '\u0000' || it.isISOControl() }


    /**
     * 把关系目标解析成压缩包内路径；`..` 只允许回退到包根以上之前，任何逃逸都返回 null。
     * 外部链接（含 scheme）由调用方按 External 处理，这里不解析。
     */
    fun resolveTarget(baseDirectory: String, target: String): String? {
        val clean = target.substringBefore('#').substringBefore('?').trim()
        if (clean.isEmpty() || clean.length > MAX_PATH_LENGTH) return null
        if (clean.startsWith("/")) return clean.removePrefix("/").takeIf(::isSafePath)
        if (clean.contains('\\') || clean.contains(':') || clean.any { it.isISOControl() }) return null
        val stack = ArrayDeque<String>()
        if (baseDirectory.isNotEmpty()) {
            for (part in baseDirectory.split('/')) if (part.isNotEmpty()) stack.addLast(part)
        }
        for (part in clean.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isEmpty()) return null else stack.removeLast()
                else -> {
                    if (!isSafeSegment(part)) return null
                    stack.addLast(part)
                }
            }
        }
        val resolved = stack.joinToString("/")
        return resolved.takeIf(::isSafePath)
    }

    /** 跳过当前 START_TAG 开始的整个子树，并同步深度预算。 */
    fun skipSubtree(parser: XmlPullParser, budget: DocxBudget) {
        var depth = 1
        budget.enter()
        while (depth > 0) {
            when (parser.nextToken()) {
                XmlPullParser.START_TAG -> {
                    budget.enter()
                    depth++
                }
                XmlPullParser.END_TAG -> {
                    budget.exit()
                    depth--
                }
                XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                else -> Unit
            }
        }
    }

    private val HIGHLIGHTS = mapOf(
        "black" to 0xFF000000.toInt(), "blue" to 0xFF0000FF.toInt(), "cyan" to 0xFF00FFFF.toInt(),
        "green" to 0xFF00FF00.toInt(), "magenta" to 0xFFFF00FF.toInt(), "red" to 0xFFFF0000.toInt(),
        "yellow" to 0xFFFFFF00.toInt(), "white" to 0xFFFFFFFF.toInt(), "darkblue" to 0xFF000080.toInt(),
        "darkcyan" to 0xFF008080.toInt(), "darkgreen" to 0xFF008000.toInt(), "darkmagenta" to 0xFF800080.toInt(),
        "darkred" to 0xFF800000.toInt(), "darkyellow" to 0xFF808000.toInt(), "darkgray" to 0xFF808080.toInt(),
        "lightgray" to 0xFFC0C0C0.toInt(),
    )
}
