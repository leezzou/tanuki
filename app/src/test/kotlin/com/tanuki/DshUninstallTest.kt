package com.tanuki

import com.tanuki.runtime.DshComponentJournal
import com.tanuki.runtime.DshUninstall
import com.tanuki.runtime.DshUninstallRecovery
import com.tanuki.runtime.DshVersionGate
import com.tanuki.runtime.DshVersions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.Collections

/** 版本卸载的拒绝、事务回滚、并发互斥与清单刷新；全部使用临时目录，不接触真实用户数据。 */
class DshUninstallTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private fun requireUninstallable(
        version: String,
        current: String = "1.2.0",
        installed: Set<String> = setOf("1.2.0", "1.3.0"),
        serviceStopped: Boolean = true,
        updateBusy: Boolean = false,
        busy: Boolean = false,
        liveChild: Boolean = false
    ) = DshUninstall.requireUninstallable(
        version, current, installed, serviceStopped, updateBusy, busy, liveChild
    )

    @Test
    fun rejectsCurrentVersionUninstall() {
        val error = assertThrows(IllegalArgumentException::class.java) { requireUninstallable("1.2.0") }
        assertEquals(DshUninstall.CURRENT_MESSAGE, error.message)
        assertTrue(error.message!!.contains("不能卸载"))
    }

    @Test
    fun rejectsRunningBusyOrUpdatingOperations() {
        assertTrue(assertThrows(IllegalStateException::class.java) {
            requireUninstallable("1.3.0", serviceStopped = false)
        }.message!!.contains("先停止服务"))
        assertTrue(assertThrows(IllegalStateException::class.java) {
            requireUninstallable("1.3.0", updateBusy = true)
        }.message!!.contains("更新检查或安装正在进行"))
        assertTrue(assertThrows(IllegalStateException::class.java) {
            requireUninstallable("1.3.0", busy = true)
        }.message!!.contains("等待当前操作完成"))
        assertTrue(assertThrows(IllegalStateException::class.java) {
            requireUninstallable("1.3.0", liveChild = true)
        }.message!!.contains("等待当前操作完成"))
    }

    @Test
    fun rejectsMissingCurrentMetadataAndUnknownVersions() {
        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            requireUninstallable("1.3.0", current = "")
        }.message!!.contains("无法确认正在使用的版本"))
        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            requireUninstallable("not-a-version")
        }.message!!.contains("版本格式无效"))
        assertTrue(assertThrows(IllegalArgumentException::class.java) {
            requireUninstallable("9.9.9")
        }.message!!.contains("未安装或程序目录不完整"))
    }

    @Test
    fun uninstallsStandbyVersionAndKeepsSharedUserData() = runBlocking {
        val engines = temporary.newFolder("engines")
        val current = seed(engines, "1.2.0")
        val standby = seed(engines, "1.3.0")
        val shared = temporary.newFolder("shared")
        val sessions = File(shared, "userdata/dsh/session.json").apply { parentFile!!.mkdirs(); writeText("会话") }
        val projects = File(shared, "workspaces/demo/README.md").apply { parentFile!!.mkdirs(); writeText("项目") }
        val snapshot = File(shared, "backups/snapshot.zip").apply { parentFile!!.mkdirs(); writeText("快照") }
        val cache = File(shared, "downloads/npm/archive.tgz").apply { parentFile!!.mkdirs(); writeText("缓存") }
        val quarantine = File(temporary.root, "runtime/dsh-1.3.0.uninstall")
        val phases = Collections.synchronizedList(mutableListOf<String>())
        var cleared = 0

        DshUninstall.apply(
            version = "1.3.0",
            target = standby,
            quarantine = quarantine,
            journal = { phases += it },
            clearJournal = { cleared++ },
            delete = { it.deleteRecursively() }
        )

        assertFalse("备用版本程序目录应被卸载", standby.exists())
        assertTrue("当前使用版本必须保留", current.isDirectory)
        assertEquals(listOf("prepared", "committed"), phases.toList())
        assertEquals(1, cleared)
        assertFalse(quarantine.exists())
        assertTrue("用户会话不能随版本卸载删除", sessions.isFile)
        assertTrue("项目不能随版本卸载删除", projects.isFile)
        assertTrue("数据快照不能随版本卸载删除", snapshot.isFile)
        assertTrue("共享下载缓存不归单个版本所有，不能删除", cache.isFile)
        assertEquals(
            "卸载后重新扫描只应剩当前版本",
            listOf("1.2.0"),
            DshVersions.scan(engines, "1.2.0").versions.map { it.version }
        )
    }

    @Test
    fun deleteFailureAfterRemovingDependencyKeepsQuarantineEvenWithMarkers() = runBlocking {
        val engines = temporary.newFolder("engines")
        seed(engines, "1.2.0")
        val standby = seed(engines, "1.3.0")
        val dependency = File(standby, "node_modules/left-pad/index.js").apply {
            parentFile!!.mkdirs()
            writeText("dependency")
        }
        val quarantine = File(temporary.root, "runtime/dsh-1.3.0.uninstall")
        var cleared = 0

        val error = runCatching {
            DshUninstall.apply(
                version = "1.3.0",
                target = standby,
                quarantine = quarantine,
                journal = {},
                clearJournal = { cleared++ },
                delete = {
                    // 模拟 deleteTree 已删掉普通依赖后抛错：标记文件仍然存在也不能证明目录完整。
                    File(it, "node_modules/left-pad/index.js").delete()
                    throw java.io.IOException("删除被中断")
                }
            )
        }.exceptionOrNull()

        assertTrue("失败必须是可解释的异常", error is IllegalStateException)
        assertTrue("必须说明已隔离且未完成清理", error!!.message!!.contains("残留清理未完成"))
        assertTrue(error.message!!.contains("用户数据未删除"))
        assertFalse("半删目录不能移回 engines", standby.exists())
        assertTrue("隔离目录保留给 recover 清理", quarantine.isDirectory)
        assertFalse("被删依赖不能重新变完整", dependency.exists())
        assertTrue("标记文件仍在，证明不能靠标记判断完整性", File(quarantine, ".installed").isFile)
        assertTrue(File(quarantine, "node_modules/@deepseek-ai/dsh/lib/bin.js").isFile)
        assertFalse(DshVersions.isInstalled(engines, "1.3.0"))
        assertEquals("提交日志必须保留，重启后才能继续清理", 0, cleared)
    }

    @Test
    fun partialDeleteKeepsQuarantineAndDoesNotRestoreHalfProgram() = runBlocking {
        val engines = temporary.newFolder("engines")
        seed(engines, "1.2.0")
        val standby = seed(engines, "1.3.0")
        val quarantine = File(temporary.root, "runtime/dsh-1.3.0.uninstall")
        var cleared = 0

        val error = runCatching {
            DshUninstall.apply(
                version = "1.3.0",
                target = standby,
                quarantine = quarantine,
                journal = {},
                clearJournal = { cleared++ },
                delete = {
                    // 模拟删除已触及安装标记后失败：不能把半删目录移回 engines。
                    File(it, ".installed").delete()
                    throw java.io.IOException("删除被中断")
                }
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("残留清理未完成"))
        assertTrue(error.message!!.contains("用户数据未删除"))
        assertFalse("已部分删除的程序不能回到 engines", standby.exists())
        assertTrue("隔离目录留给 recover 继续清理", quarantine.isDirectory)
        assertFalse(DshVersions.isInstalled(engines, "1.3.0"))
        assertEquals("提交日志不能被清除，否则重启后不会继续清理", 0, cleared)
    }

    @Test
    fun journalCommitFailureRollsBackBeforeDelete() = runBlocking {
        val engines = temporary.newFolder("engines")
        seed(engines, "1.2.0")
        val standby = seed(engines, "1.3.0")
        val quarantine = File(temporary.root, "runtime/dsh-1.3.0.uninstall")
        var deleted = false

        val error = runCatching {
            DshUninstall.apply(
                version = "1.3.0",
                target = standby,
                quarantine = quarantine,
                journal = { if (it == "committed") throw java.io.IOException("状态目录不可写") },
                clearJournal = {},
                delete = { deleted = true }
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("未能写入事务记录"))
        assertTrue(error.message!!.contains("已回滚并保留原版本"))
        assertFalse("提交日志失败时不能删除程序", deleted)
        assertTrue(standby.isDirectory)
        assertFalse(quarantine.exists())
    }

    @Test
    fun journalCommitFailureAndRollbackMoveFailureKeepsPreparedJournal() = runBlocking {
        val engines = temporary.newFolder("engines")
        seed(engines, "1.2.0")
        val standby = seed(engines, "1.3.0")
        val quarantine = File(temporary.root, "runtime/dsh-1.3.0.uninstall")
        var cleared = 0
        var moves = 0

        val error = runCatching {
            DshUninstall.apply(
                version = "1.3.0",
                target = standby,
                quarantine = quarantine,
                journal = { if (it == "committed") throw java.io.IOException("状态目录不可写") },
                clearJournal = { cleared++ },
                delete = { error("提交日志失败时不应开始删除") },
                move = { from, to ->
                    moves++
                    // 第一次是 target→quarantine；第二次回滚故意失败。
                    if (moves == 1) from.renameTo(to) else false
                }
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue("必须说明回滚未完成", error!!.message!!.contains("回滚未完成"))
        assertTrue(error.message!!.contains("已保留恢复记录"))
        assertFalse("回滚失败时原程序仍在隔离目录", standby.exists())
        assertTrue(quarantine.isDirectory)
        assertEquals("prepared 日志必须保留，供下次启动重试", 0, cleared)

        // 模拟下次启动 recover：prepared 阶段把完整隔离目录移回 engines。
        DshUninstallRecovery.apply(
            phase = "prepared",
            target = standby,
            quarantine = quarantine,
            deleteFile = { it.deleteRecursively() }
        )
        assertTrue("恢复后应回到 engines", standby.isDirectory)
        assertTrue(DshVersions.isInstalled(engines, "1.3.0"))
        assertFalse(quarantine.exists())
    }

    @Test
    fun committedRecoveryKeepsReinstalledTargetAndOnlyRemovesQuarantine() {
        val engines = temporary.newFolder("engines")
        val target = seed(engines, "1.3.0")
        val quarantine = File(temporary.root, "runtime/dsh-1.3.0.uninstall").apply { mkdirs() }
        File(quarantine, "partial").writeText("half deleted")

        DshUninstallRecovery.apply(
            phase = "committed",
            target = target,
            quarantine = quarantine,
            deleteFile = { it.deleteRecursively() }
        )

        assertTrue("同版本重装后的有效程序不能被 committed 恢复删除", target.isDirectory)
        assertTrue(DshVersions.isInstalled(engines, "1.3.0"))
        assertFalse("只清理本次卸载的隔离目录", quarantine.exists())
    }

    @Test
    fun committedRecoveryAfterReinstallingDifferentVersionKeepsCurrentProgram() {
        val engines = temporary.newFolder("engines")
        // 当前使用 B；A 卸载部分失败留下 committed 日志与隔离目录，之后又重装了 B。
        val current = seed(engines, "1.2.0")
        val quarantine = seed(File(temporary.root, "runtime"), "dsh-1.3.0.uninstall")
        File(quarantine, "partial").writeText("half deleted")

        DshUninstallRecovery.apply(
            phase = "committed",
            target = File(engines, "1.3.0"),
            quarantine = quarantine,
            deleteFile = { it.deleteRecursively() }
        )

        assertTrue("重装后的当前版本不能被卸载恢复删除", current.isDirectory)
        assertTrue(DshVersions.isInstalled(engines, "1.2.0"))
        assertFalse(quarantine.exists())

        // 隔离目录清理后可以重新安装 A；两个版本都应可用。
        seed(engines, "1.3.0")
        assertTrue(DshVersions.isInstalled(engines, "1.2.0"))
        assertTrue("清理隔离目录后应能重新安装 A", DshVersions.isInstalled(engines, "1.3.0"))
    }

    @Test
    fun componentJournalRecoverFailureDoesNotOverwritePendingJournal() = runBlocking {
        var journal = """{"action":"uninstall","phase":"committed"}"""
        var wrote = false
        val error = runCatching {
            DshComponentJournal.recoverThenWrite({ throw IllegalStateException("存在待恢复的组件") }) {
                journal = """{"phase":"switching"}"""
                wrote = true
            }
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertFalse("恢复失败时不能写新事务日志", wrote)
        assertTrue("原卸载日志必须保留", journal.contains("uninstall"))
    }

    @Test
    fun componentJournalRecoversBeforeWritingNewTransaction() = runBlocking {
        val order = mutableListOf<String>()
        DshComponentJournal.recoverThenWrite({ order += "recover" }) { order += "write" }
        assertEquals("必须先把待处理事务恢复完，再写新日志", listOf("recover", "write"), order)
    }

    @Test
    fun preparedRecoveryRestoresQuarantineAfterRollbackInterrupted() {
        val engines = temporary.newFolder("engines")
        val target = File(engines, "1.3.0")
        // 隔离目录本身就是程序目录，不是它的父目录。
        val quarantine = seed(File(temporary.root, "runtime"), "dsh-1.3.0.uninstall")

        DshUninstallRecovery.apply(
            phase = "prepared",
            target = target,
            quarantine = quarantine,
            deleteFile = { it.deleteRecursively() }
        )

        assertTrue("回滚改名后中断，恢复时应移回 target", target.isDirectory)
        assertTrue(DshVersions.isInstalled(engines, "1.3.0"))
        assertFalse(quarantine.exists())
    }

    @Test
    fun preparedRecoveryKeepsExistingTargetAndDropsStaleQuarantine() {
        val engines = temporary.newFolder("engines")
        val target = seed(engines, "1.3.0")
        val quarantine = seed(File(temporary.root, "runtime"), "dsh-1.3.0.uninstall")

        DshUninstallRecovery.apply(
            phase = "prepared",
            target = target,
            quarantine = quarantine,
            deleteFile = { it.deleteRecursively() }
        )

        assertTrue(target.isDirectory)
        assertFalse("target 已存在时不能覆盖，只清理隔离副本", quarantine.exists())
    }

    @Test
    fun legacyJournalWithoutPhaseStillRemovesTarget() {
        val engines = temporary.newFolder("engines")
        val target = seed(engines, "1.3.0")
        val quarantine = File(temporary.root, "runtime/dsh-1.3.0.uninstall").apply { mkdirs() }

        DshUninstallRecovery.apply(
            phase = null,
            target = target,
            quarantine = quarantine,
            deleteFile = { it.deleteRecursively() }
        )

        assertFalse("旧协议表示卸载已提交，保留原删除语义", target.exists())
        assertFalse(quarantine.exists())
    }

    @Test
    fun gateRejectsSecondEntryAndSerializesWaiter() = runBlocking {
        val gate = DshVersionGate()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val order = Collections.synchronizedList(mutableListOf<String>())

        val first = launch(Dispatchers.Default) {
            gate.withLock {
                order += "切换开始"
                firstEntered.complete(Unit)
                releaseFirst.await()
                order += "切换结束"
            }
        }
        firstEntered.await()
        assertFalse("已有版本操作时第二次进入必须被拒绝", gate.tryEnter())

        val second = launch(Dispatchers.Default) {
            gate.withLock { order += "卸载开始" }
        }
        delay(120)
        assertEquals("检查/卸载必须等待前一个版本操作完成", listOf("切换开始"), order.toList())

        releaseFirst.complete(Unit)
        withTimeout(5_000) { first.join() }
        withTimeout(5_000) { second.join() }
        assertEquals(listOf("切换开始", "切换结束", "卸载开始"), order.toList())
        assertTrue("释放后应可再次进入", gate.tryEnter())
        gate.exit()
    }

    @Test
    fun concurrentUninstallOfSameVersionCannotBothSucceed() = runBlocking {
        val engines = temporary.newFolder("engines")
        seed(engines, "1.2.0")
        val standby = seed(engines, "1.3.0")
        val quarantine = File(temporary.root, "runtime/dsh-1.3.0.uninstall")
        val gate = DshVersionGate()

        val outcomes = (1..2).map {
            async(Dispatchers.Default) {
                runCatching {
                    gate.withLock {
                        DshUninstall.apply(
                            version = "1.3.0",
                            target = standby,
                            quarantine = quarantine,
                            journal = {},
                            clearJournal = {},
                            delete = { it.deleteRecursively() }
                        )
                    }
                }
            }
        }.awaitAll()

        assertEquals("同一版本只能有一个卸载事务成功", 1, outcomes.count { it.isSuccess })
        assertTrue("后进入的操作应给出中文拒绝原因", outcomes.first { it.isFailure }.exceptionOrNull()!!.message!!.contains("未安装或程序目录不完整"))
        assertFalse(standby.exists())
        assertFalse(quarantine.exists())
        assertTrue("当前版本与其他数据不受影响", File(engines, "1.2.0").isDirectory)
    }

    private fun seed(engines: File, version: String): File {
        val directory = File(engines, version).apply { mkdirs() }
        File(directory, "node_modules/@deepseek-ai/dsh/lib/bin.js").apply {
            parentFile!!.mkdirs()
            writeText("bin-$version")
        }
        val lock = "{\"lock\":\"$version\"}\n"
        File(directory, "package-lock.json").writeText(lock)
        File(directory, ".installed").writeText(sha256(lock))
        File(directory, ".web-verified").writeText("fixture")
        return directory
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
