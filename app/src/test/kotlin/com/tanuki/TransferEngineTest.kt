package com.tanuki

import com.tanuki.storage.ConflictBatch
import com.tanuki.storage.StreamingTransfer
import com.tanuki.storage.TransferConflictPolicy
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
        assertTrue(all.values.all { it == TransferConflictPolicy.REPLACE })
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
}
