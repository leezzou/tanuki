package com.tanuki.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** 手机存储到工作区的操作；移动先校验副本再删源。 */
enum class WorkspaceTransferMode { COPY, MOVE }

data class WorkspaceConflict(val name: String, val directory: Boolean, val replaceable: Boolean = !directory)
data class WorkspaceTransferResult(val sourceName: String, val targetName: String?, val skipped: Boolean, val error: String? = null)
data class WorkspaceTransferReport(
    val targetDirectory: String,
    val results: List<WorkspaceTransferResult>,
    val copiedBytes: Long,
    val totalBytes: Long,
)

/** 已验证路径的批量执行器；Android 权限、路径边界和容量预算由管理器注入。 */
object WorkspaceTransfer {
    private val noFollow = LinkOption.NOFOLLOW_LINKS

    fun conflicts(sources: List<Path>, folder: Path): List<WorkspaceConflict> = sources.mapNotNull { source ->
        val target = folder.resolve(source.fileName.toString())
        if (!Files.exists(target, noFollow)) null else WorkspaceConflict(
            source.fileName.toString(), Files.isDirectory(target, noFollow),
            Files.isRegularFile(target, noFollow) && Files.isRegularFile(source, noFollow),
        )
    }

    suspend fun execute(
        sources: List<Path>,
        folder: Path,
        targetDirectory: String,
        mode: WorkspaceTransferMode,
        decisions: Map<String, TransferConflictPolicy>,
        beforePublish: suspend (Path) -> Unit = {},
        beforeDelete: (Path) -> Unit = {},
        checkSpace: (Long) -> Unit = {},
        onPhase: (String) -> Unit = {},
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): WorkspaceTransferReport {
        require(sources.isNotEmpty()) { "请先选择文件" }
        onPhase("正在统计所选文件")
        val sizes = sources.map { source ->
            currentCoroutineContext().ensureActive()
            if (decisions[source.fileName.toString()] == TransferConflictPolicy.SKIP) 0L
            else try { StreamingTransfer.measurePath(source) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        }
        val total = ImportProgress.totalBytes(sizes)
        checkSpace(total)
        onProgress(0, total)
        var received = 0L
        var copiedBytes = 0L
        val results = mutableListOf<WorkspaceTransferResult>()
        for (source in sources) {
            currentCoroutineContext().ensureActive()
            val name = source.fileName.toString()
            val policy = decisions[name] ?: TransferConflictPolicy.KEEP_BOTH
            if (policy == TransferConflictPolicy.SKIP) {
                results += WorkspaceTransferResult(name, null, skipped = true)
                continue
            }
            var itemReceived = 0L
            try {
                val desired = folder.resolve(name)
                val targetName = if (policy == TransferConflictPolicy.KEEP_BOTH && Files.exists(desired, noFollow)) {
                    val exists: (String) -> Boolean = { Files.exists(folder.resolve(it), noFollow) }
                    if (Files.isDirectory(source, noFollow)) ImportNaming.uniqueName(name, exists)
                    else ImportNaming.uniqueFileName(name, exists)
                } else name
                val target = folder.resolve(targetName)
                val receipt = StreamingTransfer.transferPath(
                    source, target, deleteSource = mode == WorkspaceTransferMode.MOVE,
                    replace = policy == TransferConflictPolicy.REPLACE,
                    beforePublish = beforePublish,
                    beforeDelete = { beforeDelete(source) },
                    onPhase = { onPhase("$it：$name") },
                ) { done, _ ->
                    checkSpace(-1)
                    itemReceived = done
                    onProgress(received + done, total)
                }
                copiedBytes += receipt.bytes
                results += WorkspaceTransferResult(name, targetName, skipped = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val kept = (error as? SourceRemovalException)?.target?.fileName?.toString()
                results += WorkspaceTransferResult(name, kept, skipped = false, error = error.message ?: "操作未完成，请检查文件")
            }
            received += itemReceived
            // 不把失败文件尚未复制的字节加进进度，也不把被跳过项伪造为复制成功。
            onProgress(received, total)
        }
        return WorkspaceTransferReport(targetDirectory, results, copiedBytes, total)
    }
}
