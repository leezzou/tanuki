package com.tanuki.storage.docx

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * 只读 DOCX（OOXML）解析入口。
 *
 * 安全边界：归档大小、条目数、单条目/总解压字节、XML 元素与嵌套深度、文字量、图片大小全部有上限；
 * 拒绝路径穿越、重复条目、DOCTYPE 与未定义实体；解压按实际读取字节计数，不信中央目录声明。
 * 不执行宏、不解析外部关系、不访问网络、不修改原文件。
 */
object DocxDocumentReader {
    fun read(
        file: File,
        limits: DocxLimits = DocxLimits(),
        parserFor: (InputStream) -> XmlPullParser,
    ): DocxDocument {
        if (!file.isFile) throw IOException("文件不存在或不是普通文件。")
        if (file.length() > limits.maxArchiveBytes) {
            throw IOException("此 DOCX 文件过大（超过 ${limits.maxArchiveBytes / (1024 * 1024)} MiB），已停止预览。")
        }
        val zip = try {
            ZipFile(file)
        } catch (failure: IOException) {
            throw IOException("无法读取此 DOCX：文件已损坏、已加密或不是有效的 Word 文档。请用其他应用打开或重新导出。", failure)
        }
        return zip.use { Reader(it, limits, parserFor).read() }
    }

    private class Reader(
        private val zip: ZipFile,
        private val limits: DocxLimits,
        private val parserFor: (InputStream) -> XmlPullParser,
    ) {
        private val warnings = linkedSetOf<String>()
        private val entries = scanEntries()
        private val relationships = parseRelationships()
        private val styles = parseStyles()
        private val numbering = parseNumbering()

        fun read(): DocxDocument {
            inspectUnsupportedParts()
            val state = DocumentParser(styles, numbering, relationships)
            val entry = entries["word/document.xml"]
                ?: throw DocxFormatException("此 DOCX 缺少正文（word/document.xml），文件可能已损坏。")
            try {
                withXml(entry, limits.maxDocumentBytes, "正文") { parser -> state.parse(parser) }
            } catch (_: DocxTruncatedException) {
                // 保留截断前已解析的块，界面只显示前面部分并明确提示。
                state.truncated = true
            }
            warnings += state.warnings
            val media = loadMedia(state.referencedImages)
            if (state.truncated) warnings += "文档内容超过内置预览上限，仅显示前面部分。"
            return DocxDocument(
                page = state.page,
                blocks = state.blocks.toList(),
                media = media,
                truncated = state.truncated,
                warnings = warnings.toList(),
            )
        }

        private fun scanEntries(): LinkedHashMap<String, ZipEntry> {
            val output = linkedMapOf<String, ZipEntry>()
            var count = 0
            var total = 0L
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                val entry = iterator.nextElement()
                count++
                if (count > limits.maxEntries) {
                    throw DocxFormatException("此 DOCX 压缩包条目过多（超过 ${limits.maxEntries} 个），已停止预览。")
                }
                val name = entry.name
                val checked = name.removeSuffix("/")
                if (!DocxXml.isSafePath(checked)) {
                    throw DocxFormatException("此 DOCX 包含不安全的路径条目（$checked），已拒绝预览。")
                }
                if (output.put(name, entry) != null) {
                    throw DocxFormatException("此 DOCX 包含重复的压缩包条目，无法安全预览。")
                }
                if (!entry.isDirectory) {
                    val size = entry.size
                    if (size > limits.maxEntryBytes) {
                        throw DocxFormatException("此 DOCX 的单个条目解压后超过上限，已停止预览。")
                    }
                    if (size > 0) {
                        total += size
                        if (total > limits.maxTotalUncompressedBytes) {
                            throw DocxFormatException("此 DOCX 解压后总大小超过上限，已停止预览。")
                        }
                    }
                }
            }
            return output
        }

        private fun inspectUnsupportedParts() {
            if (entries.keys.any { it.equals("word/vbaProject.bin", ignoreCase = true) }) {
                warnings += "文档包含宏（vbaProject.bin），内置预览只读且不会执行宏。"
            }
            if (entries.keys.any { HEADER_FOOTER.matches(it) }) {
                warnings += "页眉和页脚不会显示。"
            }
            if (entries.keys.any { it == "word/footnotes.xml" || it == "word/endnotes.xml" }) {
                warnings += "脚注和尾注不会显示。"
            }
            if (entries.keys.any { it == "word/comments.xml" }) {
                warnings += "批注不会显示。"
            }
            if (entries.keys.any { it.startsWith("word/embeddings/") }) {
                warnings += "嵌入对象不会显示。"
            }
        }

        private fun parseRelationships(): Map<String, DocxRelationship> {
            val entry = entries["word/_rels/document.xml.rels"] ?: return emptyMap()
            val output = linkedMapOf<String, DocxRelationship>()
            withXml(entry, limits.maxAuxXmlBytes, "关系表") { parser ->
                val budget = DocxBudget(limits)
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> if (DocxXml.localName(parser) == "Relationship") {
                            budget.enter()
                            val id = DocxXml.attribute(parser, "Id")
                            val target = DocxXml.attribute(parser, "Target")
                            val mode = DocxXml.attribute(parser, "TargetMode")
                            val type = DocxXml.attribute(parser, "Type")
                            if (id != null && output.size < 4096) output[id] = relationship(id, target, mode, type)
                        } else {
                            budget.enter()
                        }
                        XmlPullParser.END_TAG -> budget.exit()
                        XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                        else -> Unit
                    }
                    event = parser.nextToken()
                }
            }
            return output
        }

        private fun relationship(id: String, target: String?, mode: String?, type: String?): DocxRelationship {
            val external = mode.equals("External", ignoreCase = true) || type?.endsWith("/hyperlink") == true
            if (external) return DocxRelationship(id, null, true, type)
            val resolved = target?.let { DocxXml.resolveTarget("word", it) }
            if (resolved == null) {
                warnings += "文档中有关系目标路径不安全或无效，已忽略。"
                return DocxRelationship(id, null, false, type)
            }
            if (type?.endsWith("/image") == true && !resolved.startsWith("word/media/")) {
                warnings += "文档中有图片位于不支持的位置，已跳过。"
                return DocxRelationship(id, null, false, type)
            }
            return DocxRelationship(id, resolved, false, type)
        }

        private fun parseStyles(): DocxStyleSheet {
            val entry = entries["word/styles.xml"] ?: return DocxStyleSheet()
            return try {
                withXml(entry, limits.maxAuxXmlBytes, "样式表") { parser ->
                    DocxStyleParser.parse(parser, DocxBudget(limits))
                }
            } catch (_: DocxTruncatedException) {
                warnings += "样式表超过内置预览上限，文字按默认样式显示。"
                DocxStyleSheet()
            }
        }

        private fun parseNumbering(): DocxNumbering {
            val entry = entries["word/numbering.xml"] ?: return DocxNumbering()
            return try {
                withXml(entry, limits.maxAuxXmlBytes, "编号表") { parser ->
                    DocxNumberingParser.parse(parser, DocxBudget(limits))
                }
            } catch (_: DocxTruncatedException) {
                warnings += "编号表超过内置预览上限，列表按普通段落显示。"
                DocxNumbering()
            }
        }

        private fun loadMedia(referenced: Set<String>): Map<String, ByteArray> {
            if (referenced.isEmpty()) return emptyMap()
            val output = linkedMapOf<String, ByteArray>()
            var total = 0L
            for (name in referenced) {
                if (output.size >= limits.maxMediaCount) {
                    warnings += "文档图片数量超过内置预览上限，部分图片不会显示。"
                    break
                }
                val entry = entries[name]
                if (entry == null || entry.isDirectory) {
                    warnings += "文档引用的图片在压缩包中缺失，已跳过。"
                    continue
                }
                if (entry.size > limits.maxMediaEntryBytes) {
                    warnings += "文档中有图片超过单张大小上限，已跳过。"
                    continue
                }
                val bytes = try {
                    zip.getInputStream(entry).use { raw ->
                        readLimited(raw, limits.maxMediaEntryBytes, "单张图片解压后超过上限")
                    }
                } catch (failure: IOException) {
                    warnings += "文档中有图片读取失败或超过上限，已跳过。"
                    continue
                }
                if (bytes.isEmpty()) continue
                if (total + bytes.size > limits.maxMediaTotalBytes) {
                    warnings += "文档图片总量超过内置预览上限，部分图片不会显示。"
                    break
                }
                total += bytes.size
                output[name] = bytes
            }
            return output
        }

        private fun readLimited(input: InputStream, limit: Long, reason: String): ByteArray {
            val limited = LimitedInputStream(input, limit, reason)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = limited.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }

        private fun <T> withXml(entry: ZipEntry, maxBytes: Long, label: String, block: (XmlPullParser) -> T): T {
            try {
                zip.getInputStream(entry).use { raw ->
                    val limited = LimitedInputStream(raw, maxBytes, "此 DOCX 的${label}解压后超过上限，已停止预览。")
                    val parser = try {
                        parserFor(limited)
                    } catch (failure: Exception) {
                        throw DocxFormatException("此 DOCX 的${label}无法解析，文件可能已损坏。", failure)
                    }
                    runCatching {
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                    }
                    try {
                        parser.setInput(limited, null)
                    } catch (failure: Exception) {
                        throw DocxFormatException("此 DOCX 的${label}无法解析，文件可能已损坏。", failure)
                    }
                    return block(parser)
                }
            } catch (failure: DocxFormatException) {
                throw failure
            } catch (failure: DocxTruncatedException) {
                throw failure
            } catch (failure: XmlPullParserException) {
                throw DocxFormatException("此 DOCX 的${label}格式不正确，文件可能已损坏。", failure)
            } catch (failure: ZipException) {
                throw DocxFormatException("此 DOCX 的${label}压缩数据校验失败，文件可能已损坏。", failure)
            } catch (failure: IOException) {
                if (failure.message?.any { it in '\u4e00'..'\u9fff' } == true) throw failure
                throw DocxFormatException("无法读取此 DOCX 的${label}，文件可能已损坏。", failure)
            }
        }

        /** 正文解析器：按块收集段落/表格，只处理渲染需要的 OOXML 子集。 */
        private inner class DocumentParser(
            private val styles: DocxStyleSheet,
            private val numbering: DocxNumbering,
            private val relationships: Map<String, DocxRelationship>,
        ) {
            val blocks = mutableListOf<DocxBlock>()
            val warnings = linkedSetOf<String>()
            val referencedImages = linkedSetOf<String>()
            var page: DocxPageSetup = DocxPageSetup()
            var truncated = false
            private val budget = DocxBudget(limits)
            private val numberingFormatter = numbering.formatter()
            private var blockCount = 0
            private var cellCount = 0
            private var tableDepth = 0

            fun parse(parser: XmlPullParser) {
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    when (event) {
                        XmlPullParser.START_TAG -> if (DocxXml.localName(parser) == "body") {
                            parseBody(parser)
                            return
                        } else {
                            budget.enter()
                        }
                        XmlPullParser.END_TAG -> budget.exit()
                        XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                        else -> Unit
                    }
                    event = parser.nextToken()
                }
                throw DocxFormatException("此 DOCX 缺少正文内容，文件可能已损坏。")
            }

            private fun parseBody(parser: XmlPullParser) {
                budget.enter()
                while (true) {
                    when (val event = parser.nextToken()) {
                        XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                            "p" -> emitAll(paragraphBlocks(parseParagraph(parser)))
                            "tbl" -> emit(parseTable(parser))
                            "sectPr" -> page = parseSection(parser)
                            "altChunk" -> {
                                warnOnce("文档中的嵌入内容块不会显示。")
                                DocxXml.skipSubtree(parser, budget)
                            }
                            "del", "moveFrom" -> DocxXml.skipSubtree(parser, budget)
                            else -> budget.enter()
                        }
                        XmlPullParser.END_TAG -> {
                            budget.exit()
                            if (DocxXml.localName(parser) == "body") return
                        }
                        XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                        else -> Unit
                    }
                }
            }

            private fun parseParagraph(parser: XmlPullParser): RawParagraph {
                budget.enter()
                var direct = DocxParagraphProps()
                val raw = mutableListOf<RawInline>()
                while (true) {
                    when (val event = parser.nextToken()) {
                        XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                            "pPr" -> direct = direct.merge(DocxStyleParser.parseParagraphProps(parser, budget))
                            "r" -> parseRun(parser, raw)
                            "hyperlink", "ins", "moveTo", "smartTag", "sdt", "sdtContent", "customXml", "fldSimple" -> budget.enter()
                            "del", "moveFrom" -> DocxXml.skipSubtree(parser, budget)
                            "tbl" -> {
                                warnOnce("段落内的表格按独立表格显示。")
                                emit(parseTable(parser))
                            }
                            else -> budget.enter()
                        }
                        XmlPullParser.END_TAG -> {
                            budget.exit()
                            if (DocxXml.localName(parser) == "p") break
                        }
                        XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                        else -> Unit
                    }
                }
                // 段落样式在 pPr 与全部 run 解析完后才确定，避免 rPr 顺序影响继承结果。
                val resolved = styles.resolveParagraph(direct.styleId, direct)
                val inlines = raw.map { inline ->
                    when (inline) {
                        is RawInline.Text -> DocxInline.Text(
                            inline.text,
                            styles.resolveCharacter(inline.styleId).merge(inline.props).applyTo(resolved.run),
                        )
                        is RawInline.Image -> DocxInline.Image(inline.image)
                        RawInline.Break -> DocxInline.PageBreak
                    }
                }
                return RawParagraph(resolved, inlines)
            }

            private fun parseRun(parser: XmlPullParser, out: MutableList<RawInline>) {
                budget.enter()
                var direct = DocxRunProps()
                var styleId: String? = null
                val text = StringBuilder()

                fun flush() {
                    if (text.isEmpty()) return
                    out += RawInline.Text(text.toString(), styleId, direct)
                    text.clear()
                }

                while (true) {
                    when (val event = parser.nextToken()) {
                        XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                            "rPr" -> {
                                val parsed = DocxStyleParser.parseRunProps(parser, budget)
                                direct = direct.merge(parsed.props)
                                styleId = parsed.styleId
                            }
                            "t" -> readText(parser, text)
                            "instrText", "delText" -> {
                                warnOnce("复杂域或修订内容按结果文字近似显示。")
                                DocxXml.skipSubtree(parser, budget)
                            }
                            "fldChar" -> budget.enter()
                            "br" -> {
                                val type = DocxXml.attribute(parser, "type")?.lowercase(Locale.ROOT)
                                if (type == "page") {
                                    flush()
                                    out += RawInline.Break
                                } else {
                                    text.append('\n')
                                }
                                budget.enter()
                            }
                            "cr" -> {
                                text.append('\n')
                                budget.enter()
                            }
                            "tab" -> {
                                text.append('\t')
                                budget.enter()
                            }
                            "noBreakHyphen" -> {
                                text.append('-')
                                budget.enter()
                            }
                            "softHyphen", "lastRenderedPageBreak" -> budget.enter()
                            "drawing", "pict" -> {
                                flush()
                                parseDrawing(parser)?.let { out += RawInline.Image(it) }
                            }
                            "object" -> {
                                warnOnce("嵌入对象不会显示。")
                                DocxXml.skipSubtree(parser, budget)
                            }
                            "sym" -> {
                                val code = DocxXml.attribute(parser, "char")?.toIntOrNull(16)
                                if (code != null && code in 32..0x10FFFF) text.appendCodePoint(code)
                                budget.enter()
                            }
                            else -> budget.enter()
                        }
                        XmlPullParser.END_TAG -> {
                            budget.exit()
                            if (DocxXml.localName(parser) == "r") break
                        }
                        XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                        else -> Unit
                    }
                }
                flush()
            }

            private fun readText(parser: XmlPullParser, sink: StringBuilder) {
                budget.enter()
                var depth = 1
                while (depth > 0) {
                    when (val event = parser.nextToken()) {
                        XmlPullParser.START_TAG -> {
                            budget.enter()
                            depth++
                        }
                        XmlPullParser.END_TAG -> {
                            budget.exit()
                            depth--
                        }
                        XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.IGNORABLE_WHITESPACE -> {
                            val value = parser.text.orEmpty()
                            budget.text(value.length)
                            sink.append(value)
                        }
                        XmlPullParser.ENTITY_REF -> {
                            val value = parser.text
                                ?: throw DocxFormatException("此 DOCX 正文包含无法解析的实体引用，格式不正确。")
                            budget.text(value.length)
                            sink.append(value)
                        }
                        XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                        else -> Unit
                    }
                }
            }

            private fun parseDrawing(parser: XmlPullParser): DocxImage? {
                budget.enter()
                var relationshipId: String? = null
                var widthEmu: Long? = null
                var heightEmu: Long? = null
                var altText: String? = null
                var hasTextBox = false
                while (true) {
                    when (val event = parser.nextToken()) {
                        XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                            "blip" -> {
                                relationshipId = relationshipId ?: DocxXml.attribute(parser, "embed") ?: DocxXml.attribute(parser, "link")
                                budget.enter()
                            }
                            "imagedata" -> {
                                relationshipId = relationshipId ?: DocxXml.attribute(parser, "id")
                                budget.enter()
                            }
                            "extent" -> {
                                widthEmu = DocxXml.longAttribute(parser, "cx")?.coerceIn(1, MAX_EMU)
                                heightEmu = DocxXml.longAttribute(parser, "cy")?.coerceIn(1, MAX_EMU)
                                budget.enter()
                            }
                            "docPr" -> {
                                altText = DocxXml.attribute(parser, "descr") ?: DocxXml.attribute(parser, "title")
                                budget.enter()
                            }
                            "txbxContent" -> {
                                hasTextBox = true
                                DocxXml.skipSubtree(parser, budget)
                            }
                            else -> budget.enter()
                        }
                        XmlPullParser.END_TAG -> {
                            budget.exit()
                            if (DocxXml.localName(parser) == "drawing" || DocxXml.localName(parser) == "pict") break
                        }
                        XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                        else -> Unit
                    }
                }
                if (hasTextBox) warnOnce("文本框内容不会显示。")
                if (relationshipId == null) {
                    warnOnce("文档中有无法识别的图形对象，已跳过。")
                    return null
                }
                val relationship = relationships[relationshipId]
                if (relationship == null || relationship.external) {
                    warnOnce("外部图片或缺失的图片关系不会加载。")
                    return DocxImage(relationshipId, null, widthEmu, heightEmu, altText)
                }
                val target = relationship.target ?: return DocxImage(relationshipId, null, widthEmu, heightEmu, altText)
                if (!entries.containsKey(target)) {
                    warnOnce("文档引用的图片在压缩包中缺失，已跳过。")
                    return DocxImage(relationshipId, null, widthEmu, heightEmu, altText)
                }
                referencedImages += target
                return DocxImage(relationshipId, target, widthEmu, heightEmu, altText)
            }

            private fun parseTable(parser: XmlPullParser): DocxBlock.Table {
                if (tableDepth > 1) warnOnce("嵌套层级较深的表格仅部分显示。")
                tableDepth++
                budget.enter()
                val rows = mutableListOf<DocxTableRow>()
                var grid = mutableListOf<Int>()
                var inGrid = 0
                var header = false
                while (true) {
                    when (val event = parser.nextToken()) {
                        XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                            "tblGrid" -> {
                                inGrid++
                                budget.enter()
                            }
                            "gridCol" -> {
                                if (inGrid > 0) {
                                    DocxXml.intAttribute(parser, "w")?.takeIf { it > 0 }?.let { grid += it }
                                }
                                budget.enter()
                            }
                            "tr" -> {
                                val row = parseRow(parser)
                                rows += row
                                if (row.header) header = true
                            }
                            "tblPr" -> DocxXml.skipSubtree(parser, budget)
                            else -> budget.enter()
                        }
                        XmlPullParser.END_TAG -> {
                            if (DocxXml.localName(parser) == "tblGrid" && inGrid > 0) inGrid--
                            budget.exit()
                            if (DocxXml.localName(parser) == "tbl") {
                                tableDepth--
                                return DocxBlock.Table(rows, grid, header)
                            }
                        }
                        XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                        else -> Unit
                    }
                }
            }

            private fun parseRow(parser: XmlPullParser): DocxTableRow {
                budget.enter()
                val cells = mutableListOf<DocxTableCell>()
                var header = false
                while (true) {
                    when (val event = parser.nextToken()) {
                        XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                            "tc" -> cells += parseCell(parser)
                            "trPr" -> header = parseRowProperties(parser)
                            else -> budget.enter()
                        }
                        XmlPullParser.END_TAG -> {
                            budget.exit()
                            if (DocxXml.localName(parser) == "tr") return DocxTableRow(cells, header)
                        }
                        XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                        else -> Unit
                    }
                }
            }

            private fun parseRowProperties(parser: XmlPullParser): Boolean {
                budget.enter()
                var header = false
                while (true) {
                    when (val event = parser.nextToken()) {
                        XmlPullParser.START_TAG -> if (DocxXml.localName(parser) == "tblHeader") {
                            header = DocxXml.onOff(parser)
                            budget.enter()
                        } else {
                            budget.enter()
                        }
                        XmlPullParser.END_TAG -> {
                            budget.exit()
                            if (DocxXml.localName(parser) == "trPr") return header
                        }
                        XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                        else -> Unit
                    }
                }
            }

            private fun parseCell(parser: XmlPullParser): DocxTableCell {
                cellCount++
                if (cellCount > limits.maxTableCells) throw DocxTruncatedException()
                budget.enter()
                val blocks = mutableListOf<DocxBlock>()
                var colSpan = 1
                var rowSpan = 1
                var shading: Int? = null
                var width: Int? = null
                while (true) {
                    when (val event = parser.nextToken()) {
                        XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                            "tcPr" -> {
                                val props = parseCellProperties(parser)
                                colSpan = props.colSpan
                                rowSpan = props.rowSpan
                                shading = props.shading
                                width = props.width
                            }
                            "p" -> blocks += paragraphBlocks(parseParagraph(parser))
                            "tbl" -> blocks += parseTable(parser)
                            "sdt", "sdtContent", "customXml", "smartTag" -> budget.enter()
                            "del", "moveFrom" -> DocxXml.skipSubtree(parser, budget)
                            else -> budget.enter()
                        }
                        XmlPullParser.END_TAG -> {
                            budget.exit()
                            if (DocxXml.localName(parser) == "tc") {
                                return DocxTableCell(blocks, colSpan, rowSpan, shading, width)
                            }
                        }
                        XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                        else -> Unit
                    }
                }
            }

            private fun parseCellProperties(parser: XmlPullParser): CellProperties {
                budget.enter()
                var colSpan = 1
                var rowSpan = 1
                var shading: Int? = null
                var width: Int? = null
                while (true) {
                    when (val event = parser.nextToken()) {
                        XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                            "gridSpan" -> {
                                colSpan = DocxXml.intAttribute(parser, "val")?.coerceIn(1, 64) ?: 1
                                budget.enter()
                            }
                            "vMerge" -> {
                                rowSpan = if (DocxXml.attribute(parser, "val") == "restart") 2 else 1
                                budget.enter()
                            }
                            "tcW" -> {
                                width = DocxXml.intAttribute(parser, "w")?.takeIf { it > 0 }
                                budget.enter()
                            }
                            "shd" -> {
                                shading = DocxXml.attribute(parser, "fill")?.let { validateColor(it) }
                                budget.enter()
                            }
                            else -> budget.enter()
                        }
                        XmlPullParser.END_TAG -> {
                            budget.exit()
                            if (DocxXml.localName(parser) == "tcPr") return CellProperties(colSpan, rowSpan, shading, width)
                        }
                        XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                        else -> Unit
                    }
                }
            }

            private fun parseSection(parser: XmlPullParser): DocxPageSetup {
                budget.enter()
                var width = 11906
                var height = 16838
                var left = 1440
                var right = 1440
                var top = 1440
                var bottom = 1440
                while (true) {
                    when (val event = parser.nextToken()) {
                        XmlPullParser.START_TAG -> when (DocxXml.localName(parser)) {
                            "pgSz" -> {
                                width = DocxXml.intAttribute(parser, "w") ?: width
                                height = DocxXml.intAttribute(parser, "h") ?: height
                                budget.enter()
                            }
                            "pgMar" -> {
                                left = DocxXml.intAttribute(parser, "left") ?: left
                                right = DocxXml.intAttribute(parser, "right") ?: right
                                top = DocxXml.intAttribute(parser, "top") ?: top
                                bottom = DocxXml.intAttribute(parser, "bottom") ?: bottom
                                budget.enter()
                            }
                            "cols" -> {
                                val columns = DocxXml.intAttribute(parser, "num") ?: 1
                                if (columns > 1) warnOnce("多栏排版按单栏显示。")
                                budget.enter()
                            }
                            "headerReference", "footerReference" -> {
                                warnOnce("页眉和页脚不会显示。")
                                budget.enter()
                            }
                            "footnotePr", "endnotePr" -> {
                                warnOnce("脚注和尾注不会显示。")
                                budget.enter()
                            }
                            else -> budget.enter()
                        }
                        XmlPullParser.END_TAG -> {
                            budget.exit()
                            if (DocxXml.localName(parser) == "sectPr") {
                                return sanitizePage(width, height, left, right, top, bottom)
                            }
                        }
                        XmlPullParser.DOCDECL -> throw DocxFormatException("此 DOCX 包含不受支持的文档类型声明，已停止预览。")
                        else -> Unit
                    }
                }
            }

            private fun sanitizePage(width: Int, height: Int, left: Int, right: Int, top: Int, bottom: Int): DocxPageSetup {
                val pageWidth = width.coerceIn(2_000, 50_000)
                val pageHeight = height.coerceIn(2_000, 50_000)
                val safeLeft = left.coerceIn(0, pageWidth / 3)
                val safeRight = right.coerceIn(0, pageWidth / 3)
                val safeTop = top.coerceIn(0, pageHeight / 3)
                val safeBottom = bottom.coerceIn(0, pageHeight / 3)
                if (pageWidth != width || pageHeight != height) {
                    warnOnce("页面尺寸异常，已按可用范围调整。")
                }
                return DocxPageSetup(pageWidth, pageHeight, safeLeft, safeRight, safeTop, safeBottom)
            }

            private fun paragraphBlocks(raw: RawParagraph): List<DocxBlock> {
                val output = mutableListOf<DocxBlock>()
                val props = raw.style.paragraph
                if (props.pageBreakBefore) output += DocxBlock.PageBreak
                val marker = if ((raw.style.numId ?: 0) > 0) {
                    numberingFormatter.marker(raw.style.numId ?: 0, raw.style.numLevel).also {
                        if (it == null) warnOnce("列表编号定义缺失，编号按普通段落显示。")
                    }
                } else null
                // 页内分页符把同一段落拆成多段；中间空段保留空行，尾部空段只保留分页。
                val segments = mutableListOf<MutableList<DocxInline>>()
                var segment = mutableListOf<DocxInline>()
                for (inline in raw.inlines) {
                    if (inline is DocxInline.PageBreak) {
                        segments += segment
                        segment = mutableListOf()
                    } else {
                        segment += inline
                    }
                }
                segments += segment
                var emitted = false
                for ((index, part) in segments.withIndex()) {
                    if (index > 0) output += DocxBlock.PageBreak
                    if (part.isNotEmpty() || (index == 0 && segments.size == 1)) {
                        output += DocxBlock.Paragraph(raw.style.paragraph, part.toList(), if (index == 0) marker else null)
                        emitted = true
                    }
                }
                if (!emitted) output += DocxBlock.Paragraph(raw.style.paragraph, emptyList(), marker)
                return output
            }

            private fun emit(block: DocxBlock) {
                emitAll(listOf(block))
            }

            private fun emitAll(items: List<DocxBlock>) {
                for (block in items) {
                    blockCount++
                    if (blockCount > limits.maxBlocks) throw DocxTruncatedException()
                    blocks += block
                }
            }

            private fun warnOnce(message: String) {
                if (warnings.size < 32) warnings += message
            }
        }
    }

    private data class RawParagraph(val style: ResolvedParagraphStyle, val inlines: List<DocxInline>)

    /** run 在段落样式确定前先保留原始格式，解析完 pPr 后再统一继承。 */
    private sealed interface RawInline {
        data class Text(val text: String, val styleId: String?, val props: DocxRunProps) : RawInline
        data class Image(val image: DocxImage) : RawInline
        data object Break : RawInline
    }

    private data class DocxRelationship(val id: String, val target: String?, val external: Boolean, val type: String?)

    private data class CellProperties(val colSpan: Int, val rowSpan: Int, val shading: Int?, val width: Int?)

    private const val MAX_EMU = 100_000_000_000L

    private val HEADER_FOOTER = Regex("word/(?:header|footer)\\d*\\.xml")

    private fun validateColor(value: String): Int? {
        val clean = value.trim().removePrefix("#")
        if (clean.length != 6 || !clean.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
        return (0xFF000000L or clean.toLong(16)).toInt()
    }
}
