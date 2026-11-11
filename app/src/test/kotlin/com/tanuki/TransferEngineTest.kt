package com.tanuki

import com.tanuki.storage.ConflictBatch
import com.tanuki.storage.StreamingTransfer
import com.tanuki.storage.TransferConflictPolicy
import com.tanuki.storage.SourceRemovalException
import com.tanuki.storage.WorkspaceTransfer
import com.tanuki.storage.WorkspaceTransferMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** 跨位置复制/移动回归：流式校验、先校验后删源、取消清理暂存、冲突策略。 */
class TransferEngineTest {
    private fun fixture(block: (File) -> Unit) {
        val root = Files.createTempDirectory("tanuki-transfer-").toFile()
        try { block(root) } finally { root.deleteRecursively() }
    }

    private fun stageDirectories(root: File): List<File> =
        root.walkTopDown().filter { it.isDirectory && it.name.startsWith(".workspace-tmp-") }.toList()

    @Test fun copyPublishesExactContentAndKeepsSource() = fixture { root ->
        runBlocking {
            val source = File(root, "source.bin").apply { writeBytes(ByteArray(200_000) { (it % 251).toByte() }) }
            val targetDir = File(root, "target").apply { mkdir() }
            val target = File(targetDir, "copy.bin")
            val receipt = StreamingTransfer.transferPath(source.toPath(), target.toPath(), deleteSource = false)
            assertEquals(source.length(), receipt.bytes)
            assertArrayEquals(source.readBytes(), target.readBytes())
            assertTrue("复制不能删除源文件", source.exists())
            assertTrue(stageDirectories(targetDir).isEmpty())
        }
    }

    @Test fun moveDeletesSourceOnlyAfterVerifiedCopy() = fixture { root ->
        runBlocking {
            val payload = "移动校验\n".repeat(50_000).toByteArray(Charsets.UTF_8)
            val source = File(root, "source.txt").apply { writeBytes(payload) }
            val targetDir = File(root, "target").apply { mkdir() }
            val target = File(targetDir, "moved.txt")
            var sourcePresentWhileCopying = true
            val receipt = StreamingTransfer.transferPath(source.toPath(), target.toPath(), deleteSource = true) { _, _ ->
                // 复制/校验阶段源文件必须仍在；删除只能发生在发布之后。
                if (!source.exists()) sourcePresentWhileCopying = false
            }
            assertTrue(sourcePresentWhileCopying)
            assertArrayEquals(payload, target.readBytes())
            assertEquals(payload.size.toLong(), receipt.bytes)
            assertFalse("校验成功后才允许删除源文件", source.exists())
            assertTrue(stageDirectories(targetDir).isEmpty())
        }
    }

    @Test fun failedTargetKeepsSourceAndLeavesNoStage() = fixture { root ->
        runBlocking {
            val source = File(root, "keep.txt").apply { writeText("source") }
            val missingParent = File(root, "missing")
            try {
                StreamingTransfer.transferPath(source.toPath(), File(missingParent, "x.txt").toPath(), deleteSource = true)
                fail("目标目录不存在时必须失败")
            } catch (_: IOException) {
            }
            assertTrue(source.exists())
            assertEquals("source", source.readText())
            assertTrue(stageDirectories(root).isEmpty())
        }
    }

    @Test fun cancellationRemovesStageAndKeepsSource() = fixture { root ->
        runBlocking {
            val source = File(root, "cancel.bin").apply { writeBytes(ByteArray(2 * 1024 * 1024) { 0x61 }) }
            val targetDir = File(root, "target").apply { mkdir() }
            val target = File(targetDir, "cancel.bin")
            try {
                StreamingTransfer.transferPath(source.toPath(), target.toPath(), deleteSource = true) { received, _ ->
                    if (received > 0) throw CancellationException("合成取消")
                }
                fail("取消必须中断复制")
            } catch (_: CancellationException) {
            }
            assertFalse("取消不能留下目标文件", target.exists())
            assertTrue("取消不能删除源文件", source.exists())
            assertTrue("取消必须清理暂存目录", stageDirectories(targetDir).isEmpty())
        }
    }

    @Test fun directoryMoveCopiesRecursivelyThenDeletesSource() = fixture { root ->
        runBlocking {
            val source = File(root, "folder").apply {
                mkdir()
                File(this, "a.txt").writeText("a")
                File(this, "nested").mkdir()
                File(this, "nested/b.txt").writeText("b")
            }
            val targetDir = File(root, "target").apply { mkdir() }
            val target = File(targetDir, "folder")
            StreamingTransfer.transferPath(source.toPath(), target.toPath(), deleteSource = true)
            assertEquals("a", File(target, "a.txt").readText())
            assertEquals("b", File(target, "nested/b.txt").readText())
            assertFalse(source.exists())
        }
    }

    @Test fun replacePolicyOverwritesExistingFileOnlyAfterVerification() = fixture { root ->
        runBlocking {
            val source = File(root, "new.txt").apply { writeText("new") }
            val targetDir = File(root, "target").apply { mkdir() }
            val target = File(targetDir, "same.txt").apply { writeText("old") }
            StreamingTransfer.transferPath(source.toPath(), target.toPath(), deleteSource = false, replace = true)
            assertEquals("new", target.readText())
            assertTrue(source.exists())
        }
    }

    @Test fun replacePolicyRejectsDirectoryTarget() = fixture { root ->
        runBlocking {
            val source = File(root, "new").apply { mkdir(); File(this, "a.txt").writeText("a") }
            val targetDir = File(root, "target").apply { mkdir() }
            val target = File(targetDir, "new").apply { mkdir(); File(this, "keep.txt").writeText("keep") }
            try {
                StreamingTransfer.transferPath(source.toPath(), target.toPath(), deleteSource = false, replace = true)
                fail("同名目录不能整体替换")
            } catch (_: IllegalStateException) {
            }
            assertEquals("keep", File(target, "keep.txt").readText())
            assertTrue(source.exists())
        }
    }

    @Test fun batchConflictApplyAllCoversEveryConflict() {
        val names = listOf("a.txt", "b.txt", "c.txt")
        val partial = ConflictBatch.apply(emptyMap(), names, "a.txt", TransferConflictPolicy.SKIP, applyToAll = false)
        assertEquals(mapOf("a.txt" to TransferConflictPolicy.SKIP), partial)
        assertEquals("b.txt", ConflictBatch.next(names, partial))
        val all = ConflictBatch.apply(partial, names, "b.txt", TransferConflictPolicy.REPLACE, applyToAll = true)
        assertEquals(names.toSet(), all.keys)
        assertEquals(TransferConflictPolicy.SKIP, all["a.txt"])
        assertEquals(TransferConflictPolicy.REPLACE, all["b.txt"])
        assertEquals(TransferConflictPolicy.REPLACE, all["c.txt"])
        assertEquals(null, ConflictBatch.next(names, all))
    }

    @Test fun applyAllReplaceNeverReplacesDirectories() {
        val names = listOf("a.txt", "dir", "b.txt")
        val all = ConflictBatch.apply(
            emptyMap(), names, "a.txt", TransferConflictPolicy.REPLACE, applyToAll = true,
            replaceable = setOf("a.txt", "b.txt"),
        )
        assertEquals(TransferConflictPolicy.REPLACE, all["a.txt"])
        assertEquals(TransferConflictPolicy.REPLACE, all["b.txt"])
        assertEquals(TransferConflictPolicy.KEEP_BOTH, all["dir"])
    }

    @Test fun sameSizeSourceRewriteNeverGetsDeleted() = fixture { root ->
        runBlocking {
            val source = File(root, "changing.txt").apply { writeText("first") }
            val oldTime = Files.getLastModifiedTime(source.toPath())
            val folder = File(root, "target").apply { mkdir() }
            val target = File(folder, "changing.txt")
            try {
                StreamingTransfer.transferPath(source.toPath(), target.toPath(), true, beforePublish = {
                    source.writeText("other")
                    Files.setLastModifiedTime(source.toPath(), oldTime)
                })
                fail("源摘要变化时不能删源")
            } catch (_: IllegalStateException) { }
            assertEquals("other", source.readText())
            assertFalse(target.exists())
            assertTrue(stageDirectories(folder).isEmpty())
        }
    }

    @Test fun addedDirectoryChildIsPreservedOnMoveFailure() = fixture { root ->
        runBlocking {
            val source = File(root, "dir").apply { mkdir(); File(this, "a.txt").writeText("a") }
            val folder = File(root, "target").apply { mkdir() }
            try {
                StreamingTransfer.transferPath(source.toPath(), File(folder, "dir").toPath(), true, beforePublish = {
                    File(source, "new.txt").writeText("later")
                })
                fail("目录变化必须拒绝提交")
            } catch (_: IllegalStateException) { }
            assertEquals("later", File(source, "new.txt").readText())
            assertEquals("a", File(source, "a.txt").readText())
            assertFalse(File(folder, "dir").exists())
            assertTrue(stageDirectories(folder).isEmpty())
        }
    }

    @Test fun admissionFailureKeepsSourceAndOldReplacementTarget() = fixture { root ->
        runBlocking {
            val source = File(root, "new.txt").apply { writeText("new") }
            val folder = File(root, "target").apply { mkdir() }
            val target = File(folder, "new.txt").apply { writeText("old") }
            try {
                StreamingTransfer.transferPath(source.toPath(), target.toPath(), true, replace = true,
                    beforePublish = { throw IOException("合成容量拒绝") })
                fail("容量拒绝必须阻止发布和删源")
            } catch (_: IOException) { }
            assertEquals("new", source.readText())
            assertEquals("old", target.readText())
            assertTrue(stageDirectories(folder).isEmpty())
        }
    }

    @Test fun cancellationDuringVerificationLeavesNoPartialTarget() = fixture { root ->
        runBlocking {
            val source = File(root, "cancel.txt").apply { writeText("cancel") }
            val folder = File(root, "target").apply { mkdir() }
            val target = File(folder, "cancel.txt")
            try {
                StreamingTransfer.transferPath(source.toPath(), target.toPath(), true, onPhase = { phase ->
                    if (phase.contains("核对源")) throw CancellationException("合成校验取消")
                })
                fail("校验期间取消必须中断")
            } catch (_: CancellationException) { }
            assertEquals("cancel", source.readText())
            assertFalse(target.exists())
            assertTrue(stageDirectories(folder).isEmpty())
        }
    }

    @Test fun sourceDeletionFailureKeepsVerifiedTargetAndChangedSource() = fixture { root ->
        runBlocking {
            val source = File(root, "late.txt").apply { writeText("before") }
            val folder = File(root, "target").apply { mkdir() }
            val target = File(folder, "late.txt")
            try {
                StreamingTransfer.transferPath(source.toPath(), target.toPath(), true, onPhase = { phase ->
                    if (phase.contains("提交")) source.appendText("later")
                })
                fail("提交之后源变化必须停止删源并保留目标")
            } catch (error: SourceRemovalException) {
                assertEquals(target.toPath(), error.target)
            }
            assertEquals("before", target.readText())
            assertEquals("beforelater", source.readText())
            assertTrue(stageDirectories(folder).isEmpty())
        }
    }

    @Test fun failedDigestCleansOwnedFileAndNeverDeletesExistingFile() = fixture { root ->
        runBlocking {
            val target = File(root, "check.txt")
            try {
                StreamingTransfer.copyFileVerified("good".byteInputStream(), 4L, target.toPath()) { done, _ ->
                    if (done == 4L) target.writeText("evil")
                }
                fail("目标摘要损坏不能通过")
            } catch (_: IllegalStateException) { }
            assertFalse(target.exists())
            target.writeText("existing")
            try {
                StreamingTransfer.copyFileVerified("new".byteInputStream(), 3L, target.toPath())
                fail("已有文件不可覆盖")
            } catch (_: java.nio.file.FileAlreadyExistsException) { }
            assertEquals("existing", target.readText())
        }
    }

    @Test fun largeUnknownSourceUsesBoundedReads() = fixture { root ->
        runBlocking {
            val total = 10L * 1024 * 1024
            var remaining = total
            var largestRead = 0
            val source = object : java.io.InputStream() {
                override fun read(): Int = error("只允许分块读取")
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    largestRead = maxOf(largestRead, length)
                    if (remaining == 0L) return -1
                    val count = minOf(length.toLong(), remaining).toInt()
                    buffer.fill(7, offset, offset + count)
                    remaining -= count
                    return count
                }
            }
            val target = File(root, "large.bin")
            val receipt = source.use { StreamingTransfer.copyFileVerified(it, null, target.toPath()) }
            assertEquals(total, receipt.bytes)
            assertEquals(total, target.length())
            assertTrue(largestRead <= 65536)
        }
    }

    @Test fun batchKeepBothPreservesExtensionAndExistingData() = fixture { root ->
        runBlocking {
            val source = File(root, "合同.pdf").apply { writeText("new") }
            val folder = File(root, "target").apply { mkdir() }
            File(folder, "合同.pdf").writeText("old")
            File(folder, "合同 (2).pdf").writeText("older")
            val report = WorkspaceTransfer.execute(listOf(source.toPath()), folder.toPath(), "", WorkspaceTransferMode.COPY, emptyMap())
            assertEquals("合同 (3).pdf", report.results.single().targetName)
            assertEquals("old", File(folder, "合同.pdf").readText())
            assertEquals("older", File(folder, "合同 (2).pdf").readText())
            assertEquals("new", File(folder, "合同 (3).pdf").readText())
        }
    }

    @Test fun batchSkipNeverReadsOrDeletesSkippedSources() = fixture { root ->
        runBlocking {
            val source = File(root, "skip.txt").apply { writeText("source") }
            val folder = File(root, "target").apply { mkdir() }
            File(folder, "skip.txt").writeText("target")
            val report = WorkspaceTransfer.execute(listOf(source.toPath()), folder.toPath(), "", WorkspaceTransferMode.MOVE,
                mapOf("skip.txt" to TransferConflictPolicy.SKIP))
            assertTrue(report.results.single().skipped)
            assertEquals(0L, report.copiedBytes)
            assertEquals("source", source.readText())
            assertEquals("target", File(folder, "skip.txt").readText())
        }
    }

    @Test fun batchApplyAllExecutesReplacementAndMoveForBothFiles() = fixture { root ->
        runBlocking {
            val names = listOf("a.txt", "b.txt")
            val sources = names.map { File(root, it).apply { writeText("new-$it") }.toPath() }
            val folder = File(root, "target").apply { mkdir() }
            names.forEach { File(folder, it).writeText("old-$it") }
            val conflicts = WorkspaceTransfer.conflicts(sources, folder.toPath())
            val decisions = ConflictBatch.apply(emptyMap(), conflicts.map { it.name }, "a.txt", TransferConflictPolicy.REPLACE, true)
            val report = WorkspaceTransfer.execute(sources, folder.toPath(), "", WorkspaceTransferMode.MOVE, decisions)
            assertTrue(report.results.all { it.error == null && !it.skipped })
            names.forEach { assertEquals("new-$it", File(folder, it).readText()) }
            assertTrue(sources.none { Files.exists(it) })
        }
    }

    @Test fun batchContinuesAfterAnItemFailsWithoutForgingFullProgress() = fixture { root ->
        runBlocking {
            val source = File(root, "good.txt").apply { writeText("abc") }
            val missing = File(root, "missing.txt")
            val folder = File(root, "target").apply { mkdir() }
            val progress = mutableListOf<Long>()
            val report = WorkspaceTransfer.execute(listOf(missing.toPath(), source.toPath()), folder.toPath(), "", WorkspaceTransferMode.COPY,
                emptyMap(), onProgress = { bytes, _ -> progress += bytes })
            assertTrue(report.results[0].error != null)
            assertEquals("abc", File(folder, "good.txt").readText())
            assertEquals(3L, report.copiedBytes)
            assertEquals(3L, progress.last())
        }
    }
}
