package com.tanuki.runtime

import com.tanuki.update.VersionOrder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/** 一个可运行的已安装 DSH 程序目录；bytes 为目录占用，不跟随链接。 */
data class InstalledDshVersion(
    val version: String,
    val bytes: Long,
    val installedAtMillis: Long,
    val active: Boolean,
    val lockVerified: Boolean,
    val verified: Boolean
)

data class InstalledDshInventory(val versions: List<InstalledDshVersion> = emptyList()) {
    val totalBytes: Long get() = versions.sumOf { it.bytes }
    val installedVersions: Set<String> get() = versions.mapTo(LinkedHashSet()) { it.version }
    val activeVersion: String? get() = versions.firstOrNull { it.active }?.version
}

/**
 * 已安装 DSH 清单与切换策略的纯逻辑部分，不依赖 Android API，可在 JVM 回归中直接验证。
 *
 * “可运行”要求目录名是合法版本、程序目录不是符号链接、`.installed` 与
 * `node_modules/@deepseek-ai/dsh/lib/bin.js` 都是真实普通文件，且中间目录均未被重定向。
 * `verified` 进一步要求依赖锁哈希与 `.installed` 一致，并存在本机启动检查通过的
 * `.web-verified` 标记；缺少标记时仍列出目录，但明确显示“未校验”。
 */
object DshVersions {
    val VERSION = Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?")
    const val DOWNGRADE_MESSAGE = "旧版切换需先恢复匹配的数据快照"

    private val BIN_PARTS = listOf("node_modules", "@deepseek-ai", "dsh", "lib")

    /** 只做结构与链接校验，不读取依赖锁、不遍历体积；可安全用于频繁刷新的清单。 */
    fun installedVersions(engines: File): Set<String> {
        if (!engines.isDirectory || Files.isSymbolicLink(engines.toPath())) return emptySet()
        return engines.listFiles().orEmpty()
            .filter { it.name.matches(VERSION) && isInstalled(engines, it.name) }
            .mapTo(LinkedHashSet<String>()) { it.name }
    }

    fun isInstalled(engines: File, version: String): Boolean {
        if (!version.matches(VERSION) || Files.isSymbolicLink(engines.toPath())) return false
        val directory = File(engines, version)
        if (Files.isSymbolicLink(directory.toPath()) || !Files.isDirectory(directory.toPath(), LinkOption.NOFOLLOW_LINKS)) return false
        val marker = File(directory, ".installed")
        if (!Files.isRegularFile(marker.toPath(), LinkOption.NOFOLLOW_LINKS) || marker.length() == 0L) return false
        var cursor = directory.toPath()
        for (part in BIN_PARTS) {
            cursor = cursor.resolve(part)
            if (Files.isSymbolicLink(cursor) || !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)) return false
        }
        return Files.isRegularFile(cursor.resolve("bin.js"), LinkOption.NOFOLLOW_LINKS)
    }

    /** 完整清单：版本、逻辑字节数、安装时间、当前/备用与校验状态。 */
    fun scan(engines: File, activeVersion: String?): InstalledDshInventory {
        if (!engines.isDirectory || Files.isSymbolicLink(engines.toPath())) return InstalledDshInventory()
        val versions = engines.listFiles().orEmpty()
            .filter { it.name.matches(VERSION) && isInstalled(engines, it.name) }
            .sortedWith { left, right -> VersionOrder.compare(right.name, left.name) }
            .map { directory ->
                val marker = File(directory, ".installed")
                val lockVerified = lockMatches(directory, marker)
                InstalledDshVersion(
                    version = directory.name,
                    bytes = directoryBytes(directory),
                    installedAtMillis = marker.lastModified().takeIf { it > 0L } ?: directory.lastModified(),
                    active = directory.name == activeVersion,
                    lockVerified = lockVerified,
                    verified = lockVerified && hasStartMarker(directory)
                )
            }
        return InstalledDshInventory(versions)
    }

    fun isOlder(candidate: String, current: String): Boolean =
        candidate.matches(VERSION) && current.matches(VERSION) && VersionOrder.compare(candidate, current) < 0

    /**
     * 版本管理只允许卸载非当前的有效版本。
     * 当前版本未知（元数据缺失/损坏）时一律禁用卸载，避免把正在使用的程序删掉。
     */
    fun canUninstallFromManager(version: String, activeVersion: String): Boolean =
        version.matches(VERSION) && activeVersion.matches(VERSION) && version != activeVersion

    /**
     * 切换前只复核依赖锁与 .installed；缺少 .web-verified 只代表没有本机启动记录，
     * 不能等同于锁损坏，因此不在这里拒绝。
     */
    fun isLockVerified(engines: File, version: String): Boolean {
        if (!isInstalled(engines, version)) return false
        val directory = File(engines, version)
        return lockMatches(directory, File(directory, ".installed"))
    }

    private fun hasStartMarker(directory: File): Boolean =
        Files.isRegularFile(File(directory, ".web-verified").toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun lockMatches(directory: File, marker: File): Boolean {
        val lock = File(directory, "package-lock.json")
        if (!Files.isRegularFile(lock.toPath(), LinkOption.NOFOLLOW_LINKS) || marker.length() != 64L) return false
        val expected = runCatching { marker.readText().trim() }.getOrNull() ?: return false
        val actual = runCatching { sha256(lock) }.getOrNull() ?: return false
        return expected.equals(actual, ignoreCase = true)
    }

    /**
     * 优先按实际分配的磁盘块统计（与 DirectoryUsage 口径一致），失败时回退逻辑长度；
     * 不跟随任何链接，并按 fileKey 去掉硬链接重复计数。
     */
    private fun directoryBytes(root: File): Long {
        var total = 0L
        val seen = HashSet<Any>()
        fun add(path: Path, attrs: BasicFileAttributes) {
            if (attrs.isSymbolicLink) return
            val key = attrs.fileKey()
            if (key != null && !seen.add(key)) return
            val size = runCatching {
                val blocks = Files.getAttribute(path, "unix:blocks", LinkOption.NOFOLLOW_LINKS) as? Number
                if (blocks != null) Math.multiplyExact(blocks.toLong(), 512L) else attrs.size()
            }.getOrDefault(attrs.size())
            total = try {
                Math.addExact(total, size)
            } catch (_: ArithmeticException) {
                Long.MAX_VALUE
            }
        }
        try {
            Files.walkFileTree(root.toPath(), emptySet(), 64, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(directory: Path, attrs: BasicFileAttributes): FileVisitResult {
                    add(directory, attrs)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    add(file, attrs)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    if (exc !is NoSuchFileException) throw exc
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (_: IOException) {
            return total
        }
        return total
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

/**
 * 版本元数据窄锁：切换、卸载与更新检查共用同一实例。
 *
 * 调用方仍须先持有环境 mutation（保证与安装/启动的锁顺序固定为 mutation → 本锁），
 * 再用 [withLock] 串行化版本元数据操作；[tryEnter] 供需要快速拒绝的路径使用。
 * 纯 Kotlin 实现，JVM 回归可直接验证并发拒绝与串行等待。
 */
class DshVersionGate {
    companion object {
        const val BUSY_MESSAGE = "更新检查或安装正在进行，请稍后再操作 DSH 版本"
    }

    private val mutex = Mutex()

    fun tryEnter(): Boolean = mutex.tryLock()

    fun exit() {
        mutex.unlock()
    }

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}

/**
 * 卸载前的本机状态校验与两阶段目录事务；与 Android API 解耦，便于 JVM 覆盖拒绝与回滚分支。
 *
 * 只接收 `engines/<版本>` 程序目录与 `runtime/dsh-<版本>.uninstall` 隔离目录，
 * 不读取或删除 userdata、workspaces、backups、共享下载缓存与数据快照。
 */
object DshUninstall {
    const val CURRENT_MESSAGE = "正在使用的 DSH 版本不能卸载；请先切换到其他版本"
    const val RUNNING_MESSAGE = "DSH 正在运行，请先停止服务再卸载版本"
    const val UPDATE_MESSAGE = "更新检查或安装正在进行，请稍后再卸载版本"
    private const val BUSY_MESSAGE = "请等待当前操作完成并停止 DSH 后再卸载版本"
    private const val MISSING_CURRENT_MESSAGE = "当前安装元数据缺少有效 DSH 版本，无法确认正在使用的版本；已拒绝卸载"

    fun requireUninstallable(
        version: String,
        current: String,
        installed: Set<String>,
        serviceStopped: Boolean,
        updateBusy: Boolean,
        busy: Boolean,
        liveChild: Boolean
    ) {
        check(!busy && !liveChild) { BUSY_MESSAGE }
        check(!updateBusy) { UPDATE_MESSAGE }
        check(serviceStopped) { RUNNING_MESSAGE }
        require(version.matches(DshVersions.VERSION)) { "DSH 版本格式无效" }
        require(current.matches(DshVersions.VERSION)) { MISSING_CURRENT_MESSAGE }
        require(version != current) { CURRENT_MESSAGE }
        require(version in installed) { "DSH $version 未安装或程序目录不完整" }
    }

    /**
     * 先把目标版本原子改名到隔离目录，改写提交日志后再删除。
     *
     * 提交日志阶段：`prepared` 表示尚未确认提交，进程中断时可由 recover 把完整隔离目录移回；
     * `committed` 表示用户已确认卸载，中断后由 recover 继续清理。
     * 删除失败时仅在隔离目录仍完整（`.installed` 尚在）时回滚；已部分删除的目录不回滚，
     * 避免把半删程序重新当成可安装版本。任何失败都抛出带中文原因和当前保留位置的异常。
     */
    suspend fun apply(
        version: String,
        target: File,
        quarantine: File,
        journal: (String) -> Unit,
        clearJournal: () -> Unit,
        delete: (File) -> Unit,
        move: (File, File) -> Boolean = { from, to -> from.renameTo(to) }
    ) {
        require(version.matches(DshVersions.VERSION)) { "DSH 版本格式无效" }
        check(target.isDirectory) { "DSH $version 未安装或程序目录不完整" }
        check(!quarantine.exists()) { "上次卸载的残留目录尚未清理，请重新打开应用完成恢复" }
        quarantine.parentFile?.let { parent ->
            check(parent.isDirectory || parent.mkdirs()) { "无法创建卸载隔离目录；原版本已保留" }
        }
        try {
            journal("prepared")
        } catch (error: Throwable) {
            throw IllegalStateException("无法开始卸载 DSH $version；原版本未改动：${reason(error)}", error)
        }
        if (!move(target, quarantine)) {
            runCatching { clearJournal() }
            throw IllegalStateException("无法移出 DSH $version 程序目录；原版本已保留")
        }
        try {
            journal("committed")
        } catch (error: Throwable) {
            // 无法记录提交就不能继续删除；完整目录必须移回原位。
            val restored = move(quarantine, target)
            runCatching { clearJournal() }
            throw IllegalStateException(
                if (restored) "卸载 DSH $version 未能写入事务记录，已回滚并保留原版本：${reason(error)}"
                else "卸载 DSH $version 未能写入事务记录，且回滚未完成；程序已移到 ${quarantine.name}，请重新打开应用完成恢复：${reason(error)}",
                error
            )
        }
        try {
            delete(quarantine)
        } catch (error: Throwable) {
            // 只有结构仍完整的目录才值得回滚；删除已触及安装标记/入口时保留隔离目录交给 recover 清理。
            val intact = quarantine.isDirectory &&
                File(quarantine, ".installed").isFile &&
                File(quarantine, "node_modules/@deepseek-ai/dsh/lib/bin.js").isFile
            if (intact && move(quarantine, target)) {
                runCatching { clearJournal() }
                throw IllegalStateException("卸载 DSH $version 失败，已回滚并保留原版本：${reason(error)}", error)
            }
            throw IllegalStateException(
                "卸载 DSH $version 失败，程序目录已移出但残留清理未完成；请重新打开应用完成清理，用户数据未删除：${reason(error)}",
                error
            )
        }
        runCatching { clearJournal() }
    }

    private fun reason(error: Throwable): String = error.message ?: error.javaClass.simpleName
}

/** 切换前的本机状态与数据兼容规则；与文件系统解耦，便于 JVM 覆盖各拒绝分支。 */
object DshSwitch {
    fun requireSwitchable(
        target: String,
        current: String,
        installed: Set<String>,
        targetLockVerified: Boolean,
        serviceStopped: Boolean,
        busy: Boolean,
        liveChild: Boolean,
        updateBusy: Boolean = false
    ) {
        check(!busy && !liveChild) { "请等待当前操作完成并停止 DSH 后再切换版本" }
        check(!updateBusy) { DshUninstall.UPDATE_MESSAGE }
        check(serviceStopped) { "请先停止 DSH 服务再切换版本" }
        require(target.matches(DshVersions.VERSION)) { "DSH 版本格式无效" }
        require(current.matches(DshVersions.VERSION)) { "当前安装元数据缺少有效 DSH 版本；请先恢复匹配的数据快照" }
        require(current in installed) { "当前 DSH $current 程序缺失或不完整；请先重装当前版本或恢复匹配的数据快照，再切换" }
        require(target != current) { "DSH $target 已是当前版本" }
        require(target in installed) { "DSH $target 未安装或程序目录不完整" }
        check(targetLockVerified) { "DSH $target 的依赖锁缺失或与 .installed 不一致；请先卸载并重装该版本，当前版本保留" }
        check(!DshVersions.isOlder(target, current)) { DshVersions.DOWNGRADE_MESSAGE }
    }

    /**
     * 先执行目标与当前环境的兼容性检查，再创建当前数据快照，最后只改写 installed.json 的 dsh 字段。
     * 兼容性或快照失败时不动元数据；写入失败时把 previous 写回，旧程序目录始终保留。
     */
    suspend fun apply(
        previous: JSONObject,
        target: String,
        compatible: suspend () -> Unit = {},
        snapshot: suspend () -> String,
        write: (JSONObject) -> Unit
    ): String {
        compatible()
        val snapshotName = snapshot()
        val updated = JSONObject(previous.toString()).put("dsh", target)
        try {
            write(updated)
        } catch (error: Throwable) {
            try {
                write(JSONObject(previous.toString()))
            } catch (rollback: Throwable) {
                error.addSuppressed(rollback)
            }
            throw error
        }
        return snapshotName
    }
}
