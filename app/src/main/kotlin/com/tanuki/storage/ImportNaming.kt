package com.tanuki.storage

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 导入与跨位置复制的命名规则。
 *
 * 目标是不让用户看到 UUID：单文件用源文件名（去掉扩展名）作为项目名，多文件用时间戳；
 * 重名只追加 (2)、(3)，绝不覆盖已有条目。这里不使用 Android API，便于 JVM 回归直接覆盖。
 */
object ImportNaming {
    /** ext4/Android 单个文件名上限为 255 字节；给 " (9999)" 后缀预留空间。 */
    private const val MAX_BASE_BYTES = 220
    private const val MAX_NAME_BYTES = 240
    private const val MAX_ATTEMPTS = 10_000

    private val invalid = Regex("[\\\\/:*?\"<>|\\u0000-\\u001f\\u007f]")
    // 与 WorkspaceFileManager 的内部条目保持一致，避免生成列表里看不到的名字。
    private val internalName = Regex("^\\.(?:proot\\.)?l2s\\..*|^\\.workspace-tmp-")

    /** 去掉路径分隔符、控制字符和尾随点/空白；保留 `.env` 这类前导点文件。 */
    fun sanitize(raw: String): String {
        var name = invalid.replace(raw.trim(), "_").trimEnd(' ', '.')
        if (name.isEmpty() || name == "." || name == "..") return "导入"
        if (internalName.containsMatchIn(name)) name = "_" + name.trimStart('.')
        name = fitFileName(name, MAX_NAME_BYTES).trimEnd(' ', '.')
        return name.ifEmpty { "导入" }
    }

    /** 项目目录名不保留前导点，避免导入完成后在默认列表里看不到新项目。 */
    fun sanitizeProject(raw: String): String {
        val name = sanitize(raw).trimStart(' ', '.')
        return limitUtf8(name, MAX_BASE_BYTES).ifEmpty { "导入" }
    }

    /** 单文件取去掉最后一个扩展名后的文件名，例如「合同.pdf」→「合同」。 */
    fun baseName(displayName: String): String {
        val name = sanitize(displayName)
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        return sanitizeProject(base)
    }

    /**
     * 计算默认项目名；调用方可以传入用户编辑后的名字。
     * [nowMillis] 与 [timeZone] 只为测试可控，生产使用当前时间与本机时区。
     */
    fun projectName(
        displayNames: List<String>,
        requested: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        if (!requested.isNullOrBlank()) return sanitizeProject(requested)
        if (displayNames.size == 1) return baseName(displayNames.single())
        val format = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).apply { this.timeZone = timeZone }
        return sanitizeProject("导入-${format.format(Date(nowMillis))}")
    }

    /** 第 1 次保持原名，之后依次追加 " (2)"、" (3)"。 */
    fun numbered(base: String, index: Int): String {
        require(index >= 1) { "序号必须从 1 开始" }
        return if (index == 1) base else "$base ($index)"
    }

    /** 在 [exists] 判定下选择第一个未被占用的名字；不改变已有条目。 */
    fun uniqueName(desired: String, exists: (String) -> Boolean): String {
        val base = limitUtf8(sanitize(desired), MAX_BASE_BYTES)
        if (!exists(base)) return base
        for (index in 2..MAX_ATTEMPTS) {
            val candidate = numbered(base, index)
            if (!exists(candidate)) return candidate
        }
        throw IllegalStateException("无法为同名条目分配新名称，请先整理目标目录")
    }

    /** 同目录发布完整项目；目标含悬空链接时也视为已占用，绝不替换。 */
    fun publishProject(stage: Path, root: Path, desired: String): Path {
        require(stage.parent == root && Files.isDirectory(stage, LinkOption.NOFOLLOW_LINKS)) { "导入暂存目录无效" }
        val base = sanitizeProject(desired)
        for (index in 1..MAX_ATTEMPTS) {
            val target = root.resolve(numbered(base, index))
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) continue
            try {
                return Files.move(stage, target)
            } catch (_: java.nio.file.FileAlreadyExistsException) {
                // 目标名刚被其他操作占用时继续分配，不重试其他 I/O 错误。
            }
        }
        throw IllegalStateException("无法为导入项目分配唯一名称，原有项目已保留")
    }

    /** 文件重名把序号放在扩展名前，避免复制后的 PDF/图片失去类型识别。 */
    fun uniqueFileName(desired: String, exists: (String) -> Boolean): String {
        val safe = fitFileName(sanitize(desired), MAX_BASE_BYTES)
        if (!exists(safe)) return safe
        val (stem, extension) = splitExtension(safe)
        for (index in 2..MAX_ATTEMPTS) {
            val candidate = "$stem ($index)$extension"
            if (!exists(candidate)) return candidate
        }
        throw IllegalStateException("无法为同名文件分配新名称，请先整理目标目录")
    }

    private fun splitExtension(name: String): Pair<String, String> {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && name.substring(dot).toByteArray(Charsets.UTF_8).size <= 40) {
            name.substring(0, dot) to name.substring(dot)
        } else name to ""
    }

    private fun fitFileName(name: String, maxBytes: Int): String {
        val (stem, extension) = splitExtension(name)
        return limitUtf8(stem, maxBytes - extension.toByteArray(Charsets.UTF_8).size) + extension
    }

    private fun limitUtf8(value: String, maxBytes: Int): String {
        val result = StringBuilder()
        var bytes = 0
        var index = 0
        while (index < value.length) {
            val point = value.codePointAt(index)
            val text = String(Character.toChars(point))
            val count = text.toByteArray(Charsets.UTF_8).size
            if (bytes + count > maxBytes) break
            result.append(text)
            bytes += count
            index += Character.charCount(point)
        }
        return result.toString()
    }
}
