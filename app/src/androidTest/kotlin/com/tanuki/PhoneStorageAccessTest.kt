package com.tanuki

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.runtime.Paths
import com.tanuki.runtime.RuntimeManager
import com.tanuki.storage.FileLocation
import com.tanuki.storage.PhoneStorageAccess
import com.tanuki.storage.WorkspaceFileManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

/** 只验证当前隔离包与自建合成目录；存储权限由宿主在仪器测试进程之外授权并恢复。 */
class PhoneStorageAccessTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private class FixtureContext(base: Context, private val directory: File) : ContextWrapper(base) {
        override fun getFilesDir() = directory
        override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
    }

    private fun requireVerificationPackage() {
        check(context.packageName.endsWith(".workspacecheck") && context.packageName.matches(Regex("[A-Za-z0-9_.]+"))) {
            "存储权限回归只能运行于隔离验证包"
        }
    }

    @Test fun installedTargetPackageUsesThePermissionForItsAndroidVersion() {
        requireVerificationPackage()
        // 指定已安装目标包，避免仪器测试合并资产默认解析到测试 APK 的 manifest。
        val installed = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        assertEquals(context.packageName, installed.packageName)
        val requested = installed.requestedPermissions.orEmpty().toSet()
        assertTrue(requested.contains(Manifest.permission.MANAGE_EXTERNAL_STORAGE))
        // 系统安装解析会过滤已经超过 maxSdkVersion 的旧读取权限；属性由宿主核对实际 APK。
        if (Build.VERSION.SDK_INT <= 29) assertTrue(requested.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
        else assertFalse(requested.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
    }

    @Test fun permissionSettingsIntentTargetsOnlyTheCurrentAppAndResolves() {
        requireVerificationPackage()
        assumeTrue("Android 11以下使用普通读取权限，不支持所有文件访问设置页", Build.VERSION.SDK_INT >= 30)
        val intent = PhoneStorageAccess.permissionIntent(context)
        assertEquals(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, intent.action)
        assertEquals("package", intent.data?.scheme)
        assertEquals(context.packageName, intent.data?.schemeSpecificPart)
        assertNotNull("当前系统应提供本应用所有文件访问权限页", intent.resolveActivity(context.packageManager))
    }

    @Test fun phoneToolRootsExposeOnlyCurrentlyAuthorizedSystemVolumes() {
        requireVerificationPackage()
        val bindings = PhoneStorageAccess.toolRoots(context)
        if (!PhoneStorageAccess.isGranted(context)) {
            assertTrue("未授权时不得提供手机工具卷根", bindings.isEmpty())
            return
        }
        val allowed = PhoneStorageAccess.roots(context).values.map { it.canonicalFile }.toSet()
        assertTrue("本测试设备授权后应提供至少一个共享卷", bindings.isNotEmpty())
        val privateRoot = File(context.applicationInfo.dataDir).canonicalFile.toPath()
        for ((guest, host) in bindings) {
            assertTrue("手机工具卷根只能使用固定 /phone 命名", guest.matches(Regex("/phone/(primary|volume-[a-f0-9]{16})")))
            assertTrue("仅绑定系统报告的共享卷", host.canonicalFile in allowed)
            assertFalse("不能将应用私有数据作为共享卷", host.canonicalFile.toPath().startsWith(privateRoot))
            assertEquals(guest, PhoneStorageAccess.guestPath(host))
        }
    }

    @Test fun grantedPhoneStorageCanBrowseAndOpenOnlyOurSyntheticDownloadFixture(): Unit = runBlocking {
        requireVerificationPackage()
        assumeTrue("所有文件访问AppOp仅适用于Android 11以上", Build.VERSION.SDK_INT >= 30)
        assertTrue("宿主须在启动仪器测试前授权所有文件访问，并在整组测试退出后恢复原模式", PhoneStorageAccess.isGranted(context))
        val privateFixture = Files.createTempDirectory(context.cacheDir.toPath(), "phone-storage-").toFile()
        val name = "Tanuki-storage-test-${UUID.randomUUID()}"
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val sharedFixture = File(downloads, name)
        var openedUri: android.net.Uri? = null
        try {
            val roots = PhoneStorageAccess.roots(context)
            val primary = Environment.getExternalStorageDirectory()
            val primaryKey = roots.entries.firstOrNull { (_, root) -> root.canonicalFile == primary.canonicalFile }?.key
            assertNotNull("授权后应能选择内部共享存储卷", primaryKey)
            assertFalse("合成目录必须是新建唯一目录", sharedFixture.exists())
            assertTrue(sharedFixture.mkdirs())
            val text = "仅包含公开合成数据\n手机存储查看回归\n"
            val selected = File(sharedFixture, "sample.txt").apply { writeText(text, Charsets.UTF_8) }
            val paths = Paths(FixtureContext(context, privateFixture))
            val manager = WorkspaceFileManager(paths, phoneRoots = { PhoneStorageAccess.roots(context) }).inLocation(FileLocation.PHONE)
            val relativeFolder = "$primaryKey/${Environment.DIRECTORY_DOWNLOADS}/$name"
            val relativeFile = "$relativeFolder/sample.txt"
            assertTrue(manager.list().any { it.path == primaryKey })
            assertEquals(listOf("sample.txt"), manager.list(relativeFolder).map { it.name })
            assertEquals(WorkspaceFileManager.FileKind.TEXT, manager.describe(relativeFile).kind)
            assertEquals(text, manager.readTextPage(relativeFile).text)
            assertEquals("/phone/primary/${Environment.DIRECTORY_DOWNLOADS}/$name/sample.txt", manager.guestPath(relativeFile))

            val open = manager.prepareOpen(context, relativeFile)
            openedUri = open.data
            assertEquals(Intent.ACTION_VIEW, open.action)
            assertEquals("content", open.data?.scheme)
            assertTrue(open.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, open.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val copied = context.contentResolver.openInputStream(requireNotNull(open.data))!!.use { it.readBytes() }
            assertArrayEquals(text.toByteArray(Charsets.UTF_8), copied)
            assertEquals(text, selected.readText())
        } finally {
            // 仅删除本用例创建的选中文件缓存、UUID共享目录及独立私有夹具。
            try { openedUri?.let { context.contentResolver.delete(it, null, null) } }
            finally {
                try {
                    if (sharedFixture.exists()) {
                        check(sharedFixture.name == name && sharedFixture.parentFile == downloads)
                        check(sharedFixture.deleteRecursively()) { "合成手机存储目录清理失败" }
                    }
                } finally { RuntimeManager.deleteTree(privateFixture) }
            }
        }
    }
}
