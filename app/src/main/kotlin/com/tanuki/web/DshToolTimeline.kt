package com.tanuki.web

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** 工具调用的界面状态；没有读到结果时明确标为“进行中/未完成”，不伪造成功。 */
enum class DshToolStatus(val label: String) {
    RUNNING("进行中"),
    SUCCESS("成功"),
    FAILED("失败")
}

/** 官方 `FileDiff` 的只读投影；oldText 为 null 表示新建文件或没有旧内容。 */
data class DshFileDiff(
    val path: String,
    val oldText: String?,
    val newText: String,
    val operation: String? = null
)

enum class DshDiffLineKind { CONTEXT, ADDED, REMOVED }

data class DshDiffLine(val kind: DshDiffLineKind, val text: String)

/** 原生时间线中的一项：文本消息或工具调用卡片。 */
sealed interface DshTimelineItem {
    val seq: Int

    data class Text(val message: DshMessage) : DshTimelineItem {
        override val seq: Int get() = message.seq
    }

    data class Tool(val tool: DshToolItem) : DshTimelineItem {
        override val seq: Int get() = tool.seq
    }
}

/** 从官方 `tool/call` + `tool/result` 事件合成的工具卡片数据。 */
data class DshToolItem(
    val seq: Int,
    val time: Long,
    val callId: String,
    val name: String,
    val arguments: String?,
    val status: DshToolStatus,
    val output: String?,
    val error: String?,
    val command: String?,
    val cwd: String?,
    val diffs: List<DshFileDiff>,
    val files: List<String>,
    val outputTruncated: Boolean,
    val risk: DshRiskAssessment,
    /** 官方 tool/call 事件时间；孤儿结果没有起点时保持 0，不伪造耗时。 */
    val startedAt: Long = time,
    /** 官方 tool/result 事件时间；进行中为 null。 */
    val finishedAt: Long? = null
) {
    val key: String get() = "tool:$callId"
    val hasDetails: Boolean
        get() = command != null || diffs.isNotEmpty() || files.isNotEmpty() ||
            !output.isNullOrBlank() || !error.isNullOrBlank() || !arguments.isNullOrBlank()
    val durationMs: Long?
        get() = finishedAt?.let { finish ->
            if (startedAt > 0L && finish >= startedAt) finish - startedAt else null
        }
}

/**
 * 官方 `session/page` 事件到原生时间线的纯解析。
 *
 * - `user/message`、`assistant/message` 继续复用既有文本规则；
 * - `tool/call` 先建立进行中卡片，`tool/result` 按 callId 回填成功/失败；
 * - `tool/result.meta.diffs` 读取官方 `FileDiff`，用于 diff/文件变更展示；
 * - 不读取图片字节、附件、Cookie 或 token。
 */
object DshToolTimeline {
    const val MAX_ARGUMENT_CHARS = 8_000
    const val MAX_OUTPUT_CHARS = 12_000
    const val MAX_MESSAGE_CHARS = 12_000
    const val MAX_DIFF_FILES = 20
    const val MAX_DIFF_TEXT_CHARS = 8_000
    const val MAX_FILE_PATHS = 40

    fun parse(records: JSONArray): List<DshTimelineItem> {
        val items = ArrayList<DshTimelineItem>(records.length())
        val pending = LinkedHashMap<String, PendingTool>()
        for (index in 0 until records.length()) {
            val event = records.optJSONObject(index)?.optJSONObject("event") ?: continue
            val type = event.optString("type")
            when (type) {
                "user/message", "assistant/message" -> {
                    messageOf(event)?.let { items += DshTimelineItem.Text(it) }
                }
                "tool/call" -> {
                    val call = parseCall(event) ?: continue
                    pending[call.callId] = call
                    items += DshTimelineItem.Tool(call.toItem(null))
                }
                "tool/result" -> {
                    val result = parseResult(event) ?: continue
                    val existing = items.indexOfFirst {
                        it is DshTimelineItem.Tool && it.tool.callId == result.callId
                    }
                    val call = pending.remove(result.callId)
                    when {
                        call != null -> {
                            val completed = call.toItem(result)
                            if (existing >= 0) items[existing] = DshTimelineItem.Tool(completed)
                            else items += DshTimelineItem.Tool(completed)
                        }
                        existing >= 0 -> {
                            // 官方 surfaceOp=replace 会用新的 tool/result 替换同一 callId 的节点；
                            // 保留原调用名/参数，只更新结果，避免出现重复或“未知工具”卡片。
                            val previous = (items[existing] as DshTimelineItem.Tool).tool
                            items[existing] = DshTimelineItem.Tool(previous.withResult(result))
                        }
                        else -> {
                            // 历史页可能从工具结果中间开始；保留可见结果，不把整条卡片丢掉。
                            items += DshTimelineItem.Tool(result.toOrphanItem(event))
                        }
                    }
                }
            }
        }
        return items.sortedBy { it.seq }
    }


    /**
     * 从官方消息事件提取只读文本；这是本切片自带的解析，不依赖 42 独占的
     * DshNativeApi。只接受 append-origin surface，且 user/message 只认真实用户来源。
     */
    private fun messageOf(event: JSONObject): DshMessage? {
        val role = when (event.optString("type")) {
            "user/message" -> DshMessageRole.USER
            "assistant/message" -> DshMessageRole.ASSISTANT
            else -> return null
        }
        if (event.opt("surfaceOp") != "append") return null
        val data = event.optJSONObject("data") ?: return null
        if (role == DshMessageRole.USER) {
            val sourceKind = data.optJSONObject("source")?.optString("kind")
            if (sourceKind != null && sourceKind != "user") return null
        }
        val content = when (role) {
            DshMessageRole.USER -> data.optJSONArray("content")
                ?: data.optJSONObject("message")?.optJSONArray("content")
            DshMessageRole.ASSISTANT -> data.optJSONObject("message")?.optJSONArray("content")
                ?: data.optJSONArray("content")
        } ?: return null
        val text = textOf(content)
        if (text.isBlank()) return null
        val bounded = if (text.length <= MAX_MESSAGE_CHARS) text else text.take(MAX_MESSAGE_CHARS) + "…"
        return DshMessage(
            seq = event.optInt("seq", 0),
            role = role,
            text = bounded,
            time = event.optLong("time", 0L)
        )
    }

    private fun parseCall(event: JSONObject): PendingTool? {
        val data = event.optJSONObject("data") ?: return null
        val callId = data.optString("callId").trim()
        if (!DshApprovalProtocol.safeId(callId, 128)) return null
        val name = data.optString("name").trim().ifBlank { "未知工具" }.take(128)
        val rawArguments = data.optString("arguments").takeIf { it.isNotBlank() }
        return PendingTool(
            seq = event.optInt("seq", 0),
            time = event.optLong("time", 0L),
            callId = callId,
            name = name,
            arguments = rawArguments?.let { bounded(it, MAX_ARGUMENT_CHARS) }
        )
    }

    private fun parseResult(event: JSONObject): ToolResult? {
        val data = event.optJSONObject("data") ?: return null
        val message = data.optJSONObject("message") ?: return null
        val callId = message.optString("toolCallId").ifBlank {
            message.optJSONObject("source")?.optString("callId").orEmpty()
        }.trim()
        if (!DshApprovalProtocol.safeId(callId, 128)) return null
        val content = message.optJSONArray("content") ?: JSONArray()
        val text = textOf(content)
        val nonText = hasNonText(content)
        val isError = message.optBoolean("isError", false)
        val error = if (isError) errorOf(data, text) else null
        val meta = data.optJSONObject("meta")
        val diffs = diffsOf(meta)
        val operation = meta?.optString("operation")?.takeIf { it.isNotBlank() }
        return ToolResult(
            seq = event.optInt("seq", 0),
            time = event.optLong("time", 0L),
            callId = callId,
            isError = isError,
            output = when {
                text.isNotBlank() -> bounded(text, MAX_OUTPUT_CHARS)
                nonText -> "[非文本输出已省略]"
                else -> null
            },
            outputTruncated = text.length > MAX_OUTPUT_CHARS,
            error = error,
            diffs = diffs,
            operation = operation
        )
    }

    private fun PendingTool.toItem(result: ToolResult?): DshToolItem {
        val command = DshToolArguments.command(arguments)
        val cwd = DshToolArguments.cwd(arguments)
        val files = LinkedHashSet<String>()
        result?.diffs?.forEach { files += it.path }
        DshToolArguments.paths(arguments).forEach { files += it }
        return DshToolItem(
            seq = result?.seq ?: seq,
            time = time,
            callId = callId,
            name = name,
            arguments = arguments,
            status = when {
                result == null -> DshToolStatus.RUNNING
                result.isError -> DshToolStatus.FAILED
                else -> DshToolStatus.SUCCESS
            },
            output = result?.output,
            error = result?.error,
            command = command,
            cwd = cwd,
            diffs = result?.diffs.orEmpty().take(MAX_DIFF_FILES),
            files = files.take(MAX_FILE_PATHS),
            outputTruncated = result?.outputTruncated == true,
            risk = DshApprovalRisk.assess(name, arguments),
            startedAt = time,
            finishedAt = result?.time?.takeIf { it > 0L }
        )
    }


    private fun DshToolItem.withResult(result: ToolResult): DshToolItem {
        val mergedFiles = LinkedHashSet(files)
        result.diffs.forEach { mergedFiles += it.path }
        return copy(
            seq = result.seq,
            status = if (result.isError) DshToolStatus.FAILED else DshToolStatus.SUCCESS,
            output = result.output,
            error = result.error,
            diffs = result.diffs.take(MAX_DIFF_FILES),
            files = mergedFiles.take(MAX_FILE_PATHS),
            outputTruncated = result.outputTruncated,
            finishedAt = result.time.takeIf { it > 0L }
        )
    }

    private fun ToolResult.toOrphanItem(event: JSONObject): DshToolItem = DshToolItem(
        seq = event.optInt("seq", 0),
        time = event.optLong("time", 0L),
        callId = callId,
        name = "未知工具",
        arguments = null,
        status = if (isError) DshToolStatus.FAILED else DshToolStatus.SUCCESS,
        output = output,
        error = error,
        command = null,
        cwd = null,
        diffs = diffs,
        files = diffs.map { it.path },
        outputTruncated = outputTruncated,
        risk = DshRiskAssessment(DshRiskLevel.UNKNOWN, "历史页未包含对应的工具调用"),
        startedAt = 0L,
        finishedAt = event.optLong("time", 0L).takeIf { it > 0L }
    )

    private fun textOf(content: JSONArray): String {
        val builder = StringBuilder()
        for (index in 0 until content.length()) {
            val part = content.optJSONObject(index) ?: continue
            if (part.optString("type") != "text") continue
            val text = part.optString("text")
            if (text.isBlank()) continue
            if (builder.isNotEmpty()) builder.append('\n')
            builder.append(text)
            if (builder.length >= MAX_OUTPUT_CHARS) break
        }
        return builder.toString().trim()
    }

    private fun hasNonText(content: JSONArray): Boolean {
        for (index in 0 until content.length()) {
            val part = content.optJSONObject(index) ?: continue
            if (part.optString("type") != "text") return true
        }
        return false
    }

    private fun errorOf(data: JSONObject, output: String): String {
        val error = data.optJSONObject("error")
        val reason = error?.optString("reason").orEmpty().trim()
        if (reason.isNotEmpty()) return bounded(reason, 2_000)
        val code = error?.optString("code").orEmpty().trim()
        if (code.isNotEmpty()) return "工具返回错误（$code）"
        if (output.isNotBlank()) return bounded(output, 2_000)
        return "工具执行失败，官方未返回错误说明"
    }

    private fun diffsOf(meta: JSONObject?): List<DshFileDiff> {
        if (meta == null) return emptyList()
        val array = meta.optJSONArray("diffs") ?: return emptyList()
        val result = ArrayList<DshFileDiff>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val path = item.optString("path").trim()
            if (path.isBlank()) continue
            val newText = item.optString("newText").let { bounded(it, MAX_DIFF_TEXT_CHARS) }
            val oldText = if (item.isNull("oldText")) null else {
                item.optString("oldText").let { bounded(it, MAX_DIFF_TEXT_CHARS) }
            }
            result += DshFileDiff(
                path = path.take(500),
                oldText = oldText,
                newText = newText,
                operation = meta.optString("operation").takeIf { it.isNotBlank() }
            )
            if (result.size >= MAX_DIFF_FILES) break
        }
        return result
    }

    private fun bounded(value: String, max: Int): String =
        if (value.length <= max) value else value.take(max) + "…"

    private data class PendingTool(
        val seq: Int,
        val time: Long,
        val callId: String,
        val name: String,
        val arguments: String?
    )

    private data class ToolResult(
        val seq: Int,
        val time: Long,
        val callId: String,
        val isError: Boolean,
        val output: String?,
        val outputTruncated: Boolean,
        val error: String?,
        val diffs: List<DshFileDiff>,
        val operation: String?
    )
}

/** 从工具原始参数中提取命令、工作目录与文件路径；解析失败只返回空，不影响卡片。 */
object DshToolArguments {
    fun command(arguments: String?): String? = string(arguments, "command", "cmd", "script", "commandLine")

    fun cwd(arguments: String?): String? = string(arguments, "cwd", "workdir", "working_directory", "workingDirectory")

    fun paths(arguments: String?): List<String> {
        val json = parse(arguments) ?: return emptyList()
        val result = LinkedHashSet<String>()
        for (key in listOf("file_path", "filePath", "path", "file", "target")) {
            val value = json.optString(key).trim()
            if (value.isNotEmpty() && value.length <= 500) result += value
        }
        val array = json.optJSONArray("paths")
        if (array != null) {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotEmpty() && value.length <= 500) result += value
            }
        }
        return result.toList()
    }

    /** 参数原文的短展示；保留 JSON 结构，便于用户核对工具实际收到什么。 */
    fun pretty(arguments: String?): String? {
        val raw = arguments?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val pretty = runCatching { JSONObject(raw).toString(2) }.getOrDefault(raw)
        return if (pretty.length <= 4_000) pretty else pretty.take(4_000) + "…"
    }

    private fun string(arguments: String?, vararg keys: String): String? {
        val json = parse(arguments) ?: return null
        for (key in keys) {
            val value = json.optString(key).trim()
            if (value.isNotEmpty()) return value.take(4_000)
        }
        return null
    }

    private fun parse(arguments: String?): JSONObject? {
        val raw = arguments?.trim().orEmpty()
        if (raw.isEmpty() || raw.length > 65_536) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }
}

/**
 * 工具卡合并策略。
 *
 * 实时 follow 按单条事件解析，`tool/call` 与 `tool/result` 会分两批到达；接线方必须使用本
 * 合并器（替换页面控制器里的私有实现），否则新增的 `startedAt`/`finishedAt` 会在 `copy`
 * 合并时丢失，完成后仍显示“耗时未知”。
 */
object DshToolMerge {
    const val MAX_TOOLS = 200

    fun merge(current: List<DshToolItem>, incoming: List<DshToolItem>): List<DshToolItem> {
        if (incoming.isEmpty()) return current
        val merged = LinkedHashMap<String, DshToolItem>()
        current.forEach { merged[it.callId] = it }
        incoming.forEach { item ->
            val previous = merged[item.callId]
            // 迟到的历史页可能仍是 RUNNING；已经收到终态的卡片不能被回退。
            if (previous != null && previous.status != DshToolStatus.RUNNING &&
                item.status == DshToolStatus.RUNNING
            ) return@forEach
            merged[item.callId] = if (previous == null) item else previous.copy(
                name = item.name.takeIf { it.isNotBlank() && it != "未知工具" } ?: previous.name,
                arguments = item.arguments ?: previous.arguments,
                status = item.status,
                output = item.output ?: previous.output,
                error = item.error ?: previous.error,
                command = item.command ?: previous.command,
                cwd = item.cwd ?: previous.cwd,
                diffs = item.diffs.ifEmpty { previous.diffs },
                files = item.files.ifEmpty { previous.files },
                outputTruncated = item.outputTruncated || previous.outputTruncated,
                risk = item.risk,
                // 终态结果保留调用起点；孤儿结果没有起点时沿用已有起点，不伪造耗时。
                startedAt = when {
                    previous.startedAt > 0L -> previous.startedAt
                    item.startedAt > 0L -> item.startedAt
                    else -> 0L
                },
                finishedAt = item.finishedAt ?: previous.finishedAt
            )
        }
        return merged.values.sortedBy { it.seq }.takeLast(MAX_TOOLS)
    }
}

/** 工具卡片耗时文案；只格式化真实起止时间，未知返回 null。 */
object DshToolDuration {
    fun label(durationMs: Long?): String? {
        if (durationMs == null || durationMs < 0L) return null
        return when {
            durationMs < 1_000L -> "${durationMs} 毫秒"
            durationMs < 60_000L -> String.format(Locale.ROOT, "%.1f 秒", durationMs / 1_000.0)
            durationMs < 3_600_000L -> {
                val minutes = durationMs / 60_000L
                val seconds = (durationMs % 60_000L) / 1_000L
                if (seconds == 0L) "$minutes 分钟" else "$minutes 分 $seconds 秒"
            }
            else -> {
                val hours = durationMs / 3_600_000L
                val minutes = (durationMs % 3_600_000L) / 60_000L
                if (minutes == 0L) "$hours 小时" else "$hours 小时 $minutes 分"
            }
        }
    }
}

/** 官方 FileDiff 的逐行展示；LCS 有上限，超大 diff 退化为“整段删除 + 整段新增”。 */
object DshDiffPresentation {
    const val MAX_LINES = 400
    private const val MAX_LCS_CELLS = 90_000

    fun lines(diff: DshFileDiff, maxLines: Int = MAX_LINES): List<DshDiffLine> {
        val oldLines = diff.oldText?.lines().orEmpty()
        val newLines = diff.newText.lines()
        if (diff.oldText == null) return cap(newLines.map { DshDiffLine(DshDiffLineKind.ADDED, it) }, maxLines)
        if (oldLines.size * newLines.size > MAX_LCS_CELLS) {
            return cap(
                oldLines.map { DshDiffLine(DshDiffLineKind.REMOVED, it) } +
                    newLines.map { DshDiffLine(DshDiffLineKind.ADDED, it) },
                maxLines
            )
        }
        val table = Array(oldLines.size + 1) { IntArray(newLines.size + 1) }
        for (i in oldLines.indices.reversed()) {
            for (j in newLines.indices.reversed()) {
                table[i][j] = if (oldLines[i] == newLines[j]) table[i + 1][j + 1] + 1
                else maxOf(table[i + 1][j], table[i][j + 1])
            }
        }
        val result = ArrayList<DshDiffLine>()
        var i = 0
        var j = 0
        while (i < oldLines.size && j < newLines.size) {
            when {
                oldLines[i] == newLines[j] -> {
                    result += DshDiffLine(DshDiffLineKind.CONTEXT, oldLines[i]); i++; j++
                }
                table[i + 1][j] >= table[i][j + 1] -> {
                    result += DshDiffLine(DshDiffLineKind.REMOVED, oldLines[i]); i++
                }
                else -> {
                    result += DshDiffLine(DshDiffLineKind.ADDED, newLines[j]); j++
                }
            }
        }
        while (i < oldLines.size) result += DshDiffLine(DshDiffLineKind.REMOVED, oldLines[i++])
        while (j < newLines.size) result += DshDiffLine(DshDiffLineKind.ADDED, newLines[j++])
        return cap(result, maxLines)
    }

    fun counts(diff: DshFileDiff): Pair<Int, Int> {
        var added = 0
        var removed = 0
        for (line in lines(diff)) {
            when (line.kind) {
                DshDiffLineKind.ADDED -> added++
                DshDiffLineKind.REMOVED -> removed++
                DshDiffLineKind.CONTEXT -> Unit
            }
        }
        return added to removed
    }

    private fun cap(lines: List<DshDiffLine>, maxLines: Int): List<DshDiffLine> {
        if (lines.size <= maxLines) return lines
        return lines.take(maxLines) + DshDiffLine(DshDiffLineKind.CONTEXT, "…（diff 已截断）")
    }
}
