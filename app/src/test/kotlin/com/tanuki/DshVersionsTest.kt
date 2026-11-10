package com.tanuki

import com.tanuki.runtime.DshVersions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class DshVersionsTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun scanReportsSizeTimeStateAndVerification() {
        val engines = temporary.newFolder("engines")
        val old = seed(engines, "1.2.3")
        seed(engines, "1.3.0")
        File(engines, "1.3.0/extra.bin").writeBytes(ByteArray(4096))
        val oldMarker = File(old, ".installed")
        assertTrue(oldMarker.setLastModified(1_600_000_000_000L))

        val inventory = DshVersions.scan(engines, "1.3.0")

        assertEquals(listOf("1.3.0", "1.2.3"), inventory.versions.map { it.version })
        assertEquals("1.3.0", inventory.activeVersion)
        assertTrue(inventory.versions.first().active)
        assertFalse(inventory.versions.last().active)
        assertTrue(inventory.versions.all { it.verified })
        assertTrue(inventory.versions.all { it.lockVerified })
        assertEquals(oldMarker.lastModified(), inventory.versions.last().installedAtMillis)
        assertTrue("大小应包含目录内普通文件", inventory.versions.first().bytes >= 4096L)
        assertEquals(inventory.versions.sumOf { it.bytes }, inventory.totalBytes)
        assertEquals(setOf("1.2.3", "1.3.0"), inventory.installedVersions)
    }

    @Test
    fun scanSkipsIncompleteAndRedirectedInstalls() {
        val engines = temporary.newFolder("engines")
        seed(engines, "1.0.0")
        seed(engines, "1.0.1", marker = null)
        seed(engines, "1.0.2", bin = false)

        // .installed 是符号链接，即使目标普通文件也不能算已安装。
        val markerLink = seed(engines, "1.0.3", marker = null)
        val externalMarker = temporary.newFile("external-marker").apply { writeText(sha256("{}\n")) }
        Files.createSymbolicLink(File(markerLink, ".installed").toPath(), externalMarker.toPath())

        // 版本目录本身是符号链接；真实目标放在 engines 外，避免被当成另一个合法安装。
        val real = seed(File(temporary.root, "real-engine"), "1.0.4")
        Files.createSymbolicLink(File(engines, "1.0.5").toPath(), real.toPath())

        // node_modules 中间目录被重定向到程序目录外。
        val redirected = seed(engines, "1.0.6", bin = false)
        val outsideBin = File(temporary.root, "outside/node_modules/@deepseek-ai/dsh/lib")
        outsideBin.mkdirs()
        File(outsideBin, "bin.js").writeText("outside")
        Files.createSymbolicLink(File(redirected, "node_modules").toPath(), File(temporary.root, "outside/node_modules").toPath())

        val versions = DshVersions.scan(engines, "1.0.0").versions.map { it.version }

        assertEquals(listOf("1.0.0"), versions)
        assertTrue(DshVersions.isInstalled(engines, "1.0.0"))
        assertFalse(DshVersions.isInstalled(engines, "1.0.1"))
        assertFalse(DshVersions.isInstalled(engines, "1.0.2"))
        assertFalse(DshVersions.isInstalled(engines, "1.0.3"))
        assertFalse(DshVersions.isInstalled(engines, "1.0.5"))
        assertFalse(DshVersions.isInstalled(engines, "1.0.6"))
    }

    @Test
    fun verificationRequiresLockDigestAndStartMarker() {
        val engines = temporary.newFolder("engines")
        seed(engines, "1.0.0")
        seed(engines, "1.0.1", marker = "0".repeat(64))
        seed(engines, "1.0.2", webVerified = false)

        val byVersion = DshVersions.scan(engines, "1.0.0").versions.associateBy { it.version }

        assertTrue(byVersion.getValue("1.0.0").verified)
        assertTrue(byVersion.getValue("1.0.0").lockVerified)
        assertFalse("依赖锁哈希不一致必须标记未校验", byVersion.getValue("1.0.1").verified)
        assertFalse(byVersion.getValue("1.0.1").lockVerified)
        assertFalse("缺少本机启动检查标记必须标记未校验", byVersion.getValue("1.0.2").verified)
        assertTrue("缺少 .web-verified 不能等同于锁损坏", byVersion.getValue("1.0.2").lockVerified)
    }

    @Test
    fun lockVerificationRejectsMissingOrMismatchedLock() {
        val engines = temporary.newFolder("engines")
        seed(engines, "1.0.0")
        seed(engines, "1.0.1", marker = "0".repeat(64))
        seed(engines, "1.0.2")
        File(engines, "1.0.2/package-lock.json").delete()

        assertTrue(DshVersions.isLockVerified(engines, "1.0.0"))
        assertFalse("锁哈希不匹配必须拒绝切换", DshVersions.isLockVerified(engines, "1.0.1"))
        assertFalse("锁文件缺失必须拒绝切换", DshVersions.isLockVerified(engines, "1.0.2"))
        assertFalse(DshVersions.isLockVerified(engines, "9.9.9"))
    }

    @Test
    fun sizeScanDoesNotFollowSymlinkedFiles() {
        val engines = temporary.newFolder("engines")
        val engine = seed(engines, "1.0.0")
        val outside = temporary.newFile("outside.bin").apply { writeBytes(ByteArray(2 * 1024 * 1024)) }
        Files.createSymbolicLink(File(engine, "outside-link.bin").toPath(), outside.toPath())

        val bytes = DshVersions.scan(engines, "1.0.0").versions.single().bytes

        assertTrue("不能把链接目标的体积计入已安装版本：$bytes", bytes < outside.length())
    }

    @Test
    fun uninstallPolicyOnlyAllowsNonCurrentVersions() {
        assertFalse("当前版本必须在版本管理中禁用卸载", DshVersions.canUninstallFromManager("1.2.0", "1.2.0"))
        assertTrue(DshVersions.canUninstallFromManager("1.3.0", "1.2.0"))
        assertFalse("当前版本未知时必须禁用卸载，避免误删正在使用的程序", DshVersions.canUninstallFromManager("1.2.0", ""))
        assertFalse(DshVersions.canUninstallFromManager("not-a-version", "1.2.0"))
    }

    @Test
    fun missingOrEmptyEnginesDirectoryHasEmptyInventory() {
        val missing = File(temporary.root, "missing-engines")
        assertTrue(DshVersions.scan(missing, "1.0.0").versions.isEmpty())
        assertTrue(DshVersions.installedVersions(missing).isEmpty())
        val empty = temporary.newFolder("empty-engines")
        assertTrue(DshVersions.scan(empty, "1.0.0").versions.isEmpty())
        assertTrue(DshVersions.scan(empty, "").versions.isEmpty())
    }

    private fun seed(
        engines: File,
        version: String,
        lock: String = "{\"lock\":\"$version\"}\n",
        marker: String? = sha256(lock),
        webVerified: Boolean = true,
        bin: Boolean = true
    ): File {
        val directory = File(engines, version).apply { mkdirs() }
        if (bin) {
            File(directory, "node_modules/@deepseek-ai/dsh/lib/bin.js").apply {
                parentFile?.mkdirs()
                writeText("bin-$version")
            }
        }
        marker?.let { File(directory, ".installed").writeText(it) }
        File(directory, "package-lock.json").apply { parentFile?.mkdirs(); writeText(lock) }
        if (webVerified) File(directory, ".web-verified").writeText("fixture")
        return directory
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
