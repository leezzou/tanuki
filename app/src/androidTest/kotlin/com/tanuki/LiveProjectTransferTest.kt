package com.tanuki

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.runtime.Paths
import com.tanuki.runtime.RuntimeManager
import com.tanuki.storage.ProjectArchive
import com.tanuki.storage.WorkspaceStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.util.zip.ZipInputStream

/** 仅用独立合成目录验证运行中传输策略，不访问用户项目或启动真实 DSH。 */
class LiveProjectTransferTest {
    private class FixtureContext(base: Context, private val directory: File) : ContextWrapper(base) {
        override fun getFilesDir() = directory
        override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
    }

    private suspend fun fixture(block: suspend (Paths, WorkspaceStorage) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".workspacecheck")) { "回归只能运行于隔离验证包" }
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "live-transfer-").toFile()
        val paths = Paths(FixtureContext(context, directory))
        try { block(paths, WorkspaceStorage(paths)) }
        finally { RuntimeManager.deleteTree(directory) }
    }

    private suspend fun rejected(block: suspend () -> Unit): Throwable {
        try { block() } catch (error: Throwable) { return error }
        throw AssertionError("操作应明确失败，不能把不完整传输报告为成功")
    }

    private fun assertNoImportStage(paths: Paths) {
        assertFalse(paths.workspaces.list().orEmpty().any { it.startsWith(".import-") })
    }

    @Test fun importPublishesANewProjectAndNeverOverwritesTheRunningProject(): Unit = runBlocking {
        fixture { paths, storage ->
            val existing = File(paths.workspaces, "shared.txt").apply { writeText("agent-original") }
            val payload = "导入内容\n".repeat(20000).toByteArray(Charsets.UTF_8)
            val progress = mutableListOf<Long>()
            var concurrentWrite = false
            val imported = storage.importFiles(listOf("shared.txt"), onProgress = { bytes ->
                progress += bytes
                if (bytes > 0 && !concurrentWrite) {
                    concurrentWrite = true
                    existing.appendText("-updated")
                }
            }) { payload.inputStream() }

            assertTrue(concurrentWrite)
            assertEquals("agent-original-updated", existing.readText())
            assertEquals("shared", imported.name)
            assertEquals(paths.workspaces.canonicalFile, imported.canonicalFile.parentFile)
            assertArrayEquals(payload, File(imported, "shared.txt").readBytes())
            assertEquals(payload.size.toLong(), progress.last())
            assertTrue(progress.zipWithNext().all { (before, after) -> after >= before })
            assertNoImportStage(paths)
        }
    }

    @Test fun failedSourceRollsBackOnlyTheNewImport(): Unit = runBlocking {
        fixture { paths, storage ->
            val existing = File(paths.workspaces, "keep.txt").apply { writeText("keep") }
            var calls = 0
            val source = object : InputStream() {
                override fun read(): Int = throw IOException("合成源读取失败")
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (calls++ > 0) throw IOException("合成源读取失败")
                    val count = minOf(length, 4096)
                    buffer.fill(0x61, offset, offset + count)
                    return count
                }
            }
            val error = rejected { storage.importFiles(listOf("partial.bin")) { source } }
            assertTrue(error is IOException)
            assertEquals("keep", existing.readText())
            assertEquals(listOf("keep.txt"), paths.workspaces.list().orEmpty().toList())
            assertNoImportStage(paths)
        }
    }

    @Test fun cancelledImportCleansItsStageAndKeepsExistingFiles(): Unit = runBlocking {
        fixture { paths, storage ->
            val existing = File(paths.workspaces, "keep.txt").apply { writeText("keep") }
            val error = rejected {
                storage.importFiles(listOf("cancelled.bin"), onProgress = { bytes ->
                    if (bytes > 0) throw CancellationException("合成导入取消")
                }) { ByteArray(200000) { 0x61 }.inputStream() }
            }
            assertTrue(error is CancellationException)
            assertEquals("keep", existing.readText())
            assertEquals(listOf("keep.txt"), paths.workspaces.list().orEmpty().toList())
            assertNoImportStage(paths)
        }
    }

    @Test fun exportIncludesMoreThanFiveThousandFilesAndExcludesPrivateNames(): Unit = runBlocking {
        fixture { paths, _ ->
            repeat(5001) { index -> File(paths.workspaces, "file-$index.txt").writeText("item-$index") }
            File(paths.workspaces, ".env").writeText("synthetic-private")
            File(paths.workspaces, ".import-incomplete").mkdir()
            File(paths.workspaces, ".import-incomplete/partial.txt").writeText("incomplete")
            val output = ByteArrayOutputStream()
            val progress = mutableListOf<Long>()
            ProjectArchive.write(paths.workspaces, paths.base, output, onProgress = { progress += it })

            val names = mutableSetOf<String>()
            var lastContent: String? = null
            ZipInputStream(output.toByteArray().inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    names += entry.name
                    val content = zip.readBytes().toString(Charsets.UTF_8)
                    if (entry.name == "file-5000.txt") lastContent = content
                }
            }
            assertEquals(5001, names.size)
            assertEquals("item-5000", lastContent)
            assertFalse(names.any { it == ".env" || it.startsWith(".import-") })
            assertEquals((0..5000).sumOf { "item-$it".toByteArray().size.toLong() }, progress.last())
            assertTrue(progress.zipWithNext().all { (before, after) -> after >= before })
        }
    }

    @Test fun exportRejectsAFileModifiedDuringItsRead(): Unit = runBlocking {
        fixture { paths, _ ->
            val source = File(paths.workspaces, "changing.bin").apply { writeBytes(ByteArray(300000) { 0x61 }) }
            var changed = false
            val error = rejected {
                ProjectArchive.write(paths.workspaces, paths.base, ByteArrayOutputStream(), onProgress = { bytes ->
                    if (bytes > 0 && !changed) {
                        changed = true
                        source.writeBytes(byteArrayOf(0x62))
                    }
                })
            }
            assertTrue("必须在读文件时触发合成改写", changed)
            assertFalse(error is CancellationException)
            assertArrayEquals(byteArrayOf(0x62), source.readBytes())
        }
    }

    @Test fun exportRejectsAFileDeletedDuringItsRead(): Unit = runBlocking {
        fixture { paths, _ ->
            val source = File(paths.workspaces, "deleted.bin").apply { writeBytes(ByteArray(300000) { 0x61 }) }
            var deleted = false
            val error = rejected {
                ProjectArchive.write(paths.workspaces, paths.base, ByteArrayOutputStream(), onProgress = { bytes ->
                    if (bytes > 0 && !deleted) {
                        Files.delete(source.toPath())
                        deleted = true
                    }
                })
            }
            assertTrue("必须在文件已打开时触发合成删除", deleted)
            assertFalse(error is CancellationException)
            assertFalse(source.exists())
        }
    }

    @Test fun exportRejectsSameLengthRewriteWithANewModificationTime(): Unit = runBlocking {
        fixture { paths, _ ->
            val source = File(paths.workspaces, "rewrite.bin").apply { writeBytes(ByteArray(300000) { 0x61 }) }
            val originalTime = Files.getLastModifiedTime(source.toPath())
            var changed = false
            rejected {
                ProjectArchive.write(paths.workspaces, paths.base, ByteArrayOutputStream(), onProgress = { bytes ->
                    if (bytes > 0 && !changed) {
                        changed = true
                        source.writeBytes(ByteArray(300000) { 0x62 })
                        Files.setLastModifiedTime(source.toPath(), FileTime.fromMillis(originalTime.toMillis() + 2000))
                    }
                })
            }
            assertTrue(changed)
            assertEquals(300000L, source.length())
            assertEquals(0x62.toByte(), source.inputStream().use { it.read().toByte() })
        }
    }

    @Test fun exportRejectsReplacementEvenWhenLengthAndModificationTimeMatch(): Unit = runBlocking {
        fixture { paths, _ ->
            val source = File(paths.workspaces, "replaced.bin").apply { writeBytes(ByteArray(300000) { 0x61 }) }
            val originalTime = Files.getLastModifiedTime(source.toPath())
            val replacement = File(paths.base, "replacement.bin").apply { writeBytes(ByteArray(300000) { 0x62 }) }
            Files.setLastModifiedTime(replacement.toPath(), originalTime)
            var changed = false
            rejected {
                ProjectArchive.write(paths.workspaces, paths.base, ByteArrayOutputStream(), onProgress = { bytes ->
                    if (bytes > 0 && !changed) {
                        changed = true
                        Files.move(replacement.toPath(), source.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                })
            }
            assertTrue(changed)
            assertEquals(300000L, source.length())
            assertEquals(originalTime, Files.getLastModifiedTime(source.toPath()))
            assertEquals(0x62.toByte(), source.inputStream().use { it.read().toByte() })
        }
    }

    @Test fun cancelledExportDoesNotReturnSuccess(): Unit = runBlocking {
        fixture { paths, _ ->
            val source = File(paths.workspaces, "cancelled.bin").apply { writeBytes(ByteArray(200000) { 0x61 }) }
            val error = rejected {
                ProjectArchive.write(paths.workspaces, paths.base, ByteArrayOutputStream(), onProgress = { bytes ->
                    if (bytes > 0) throw CancellationException("合成导出取消")
                })
            }
            assertTrue(error is CancellationException)
            assertEquals(200000L, source.length())
        }
    }

    @Test fun exportNeverFollowsAnOrdinaryLinkToPrivateData(): Unit = runBlocking {
        fixture { paths, _ ->
            val privateFile = File(paths.home, "synthetic-private.txt").apply { writeText("private-fixture") }
            val link = File(paths.workspaces, "outside.txt").toPath()
            Files.createSymbolicLink(link, privateFile.toPath())
            val output = ByteArrayOutputStream()
            val error = rejected { ProjectArchive.write(paths.workspaces, paths.base, output) }
            assertTrue(error is IOException || error is IllegalStateException)
            assertEquals("private-fixture", privateFile.readText())
            assertTrue(Files.isSymbolicLink(link))
        }
    }
}
