package com.tanuki

import android.os.Environment
import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.runtime.CommandResult
import com.tanuki.runtime.RuntimeManager
import com.tanuki.runtime.ServiceState
import com.tanuki.storage.PhoneStorageAccess
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** 真实启动链和客户端端到端探针；仅用现有环境及自建文件，不安装组件或启动官方 DSH/模型。 */
class PhoneFileGuestClientTest {
    @Test fun existingUbuntuNodeClientReadsAndRequiresNativeWriteGrant(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".workspacecheck")) { "必须使用隔离验证包" }
        val runtime = (context.applicationContext as TanukiApp).runtime
        assertFalse("不得干扰真实 DSH 子进程", runtime.hasLiveChild)
        assertFalse("不得干扰安装、更新或其他运行操作", runtime.hasActiveOperation)
        assertTrue("不得干扰其他文件任务", runtime.fileTransfers.state.value.isEmpty())
        assertTrue(runtime.state.value.service in setOf(ServiceState.STOPPED, ServiceState.FAILED, ServiceState.INTERRUPTED))
        assertTrue("只使用已存在的 Ubuntu，不在测试中下载环境", File(runtime.paths.root, "usr/bin/env").isFile)
        assertTrue("只使用已存在的 Node，不在测试中下载环境", File(runtime.paths.node, "bin/node").isFile ||
            File(runtime.paths.root, "opt/node/bin/node").isFile)
        assertTrue("宿主须在仪器测试前授权共享存储，组后由宿主恢复", PhoneStorageAccess.isGranted(context))
        assertTrue("写入探针需要宿主提供 Android 层写权限，原生 AI 写许可仍默认关闭", PhoneStorageAccess.isWriteGranted(context))
        assertFalse("不得接管一个已经允许写入的连接", runtime.phoneFiles.writeAllowed.value)

        val token = UUID.randomUUID().toString()
        val sharedParent = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val shared = File(sharedParent, "Tanuki-phone-guest-$token")
        val workspace = File(runtime.paths.workspaces, "phone-guest-$token")
        assertFalse(shared.exists()); assertFalse(workspace.exists())
        val phoneInput = "真实 Ubuntu/Node 手机文件读取\n".repeat(4000).toByteArray(Charsets.UTF_8)
        val upload = ByteArray(150000) { (it % 251).toByte() }
        val logical = PhoneStorageAccess.guestPath(Environment.getExternalStorageDirectory(), "Download/${shared.name}")
        val local = "/workspaces/${workspace.name}"
        var startedService = false

        runtime.mutation.withLock {
            try {
                assertTrue(shared.mkdirs()); assertTrue(workspace.mkdirs())
                File(shared, "input.txt").writeBytes(phoneInput)
                File(workspace, "upload.bin").writeBytes(upload)
                val marker = File(workspace, "keep.txt").apply { writeText("unchanged:$token") }
                val service = runtime.state.value.service
                runtime.phoneFiles.start(); startedService = true
                assertFalse(runtime.phoneFiles.writeAllowed.value)

                suspend fun client(vararg arguments: String): CommandResult = runtime.launcher.run(
                    runtime.paths.root,
                    listOf("/opt/node/bin/node", "/opt/tanuki-phone/tanuki-phone.mjs") + arguments,
                    timeoutMs = 60000,
                    phoneFiles = true,
                )

                val read = client("read-file", "$logical/input.txt", "$local/download.txt")
                assertEquals("真实 Launcher/Node 客户端读取必须成功", 0, read.exitCode)
                val readResult = JSONObject(read.stdout.trim())
                assertTrue(readResult.getBoolean("ok"))
                assertEquals(phoneInput.size.toLong(), readResult.getJSONObject("result").getLong("bytes"))
                assertArrayEquals(phoneInput, File(workspace, "download.txt").readBytes())

                val denied = client("write-file", "$local/upload.bin", "$logical/default-denied.bin")
                assertEquals("默认读取不包含原生写授权", 1, denied.exitCode)
                assertTrue(denied.stderr.contains("write_permission_required"))
                assertFalse(File(shared, "default-denied.bin").exists())

                runtime.phoneFiles.setWriteAllowed(true)
                assertTrue(runtime.phoneFiles.writeAllowed.value)
                val written = client("write-file", "$local/upload.bin", "$logical/explicit-grant.bin")
                assertEquals("原生显式授权后真实分块写入必须成功", 0, written.exitCode)
                val writtenResult = JSONObject(written.stdout.trim())
                assertTrue(writtenResult.getBoolean("ok"))
                assertEquals(upload.size.toLong(), writtenResult.getJSONObject("result").getLong("bytes"))
                assertArrayEquals(upload, File(shared, "explicit-grant.bin").readBytes())

                runtime.phoneFiles.setWriteAllowed(false)
                assertFalse(runtime.phoneFiles.writeAllowed.value)
                val revoked = client("write-file", "$local/upload.bin", "$logical/revoked.bin")
                assertEquals("撤回许可后客户端不能写入另一个目标", 1, revoked.exitCode)
                assertTrue(revoked.stderr.contains("write_permission_required"))
                assertFalse(File(shared, "revoked.bin").exists())
                assertEquals("unchanged:$token", marker.readText())
                assertArrayEquals(phoneInput, File(shared, "input.txt").readBytes())
                assertArrayEquals(upload, File(workspace, "upload.bin").readBytes())
                assertEquals("CLI探针不能启动官方DSH或改变运行状态", service, runtime.state.value.service)
                assertFalse(runtime.hasLiveChild)
                assertTrue(runtime.fileTransfers.state.value.isEmpty())
            } finally {
                try {
                    if (startedService) runtime.phoneFiles.stop()
                } finally {
                    try {
                        if (workspace.exists()) {
                            check(workspace.parentFile == runtime.paths.workspaces && workspace.name == "phone-guest-$token")
                            RuntimeManager.deleteTree(workspace)
                        }
                    } finally {
                        if (shared.exists()) {
                            check(shared.parentFile == sharedParent && shared.name == "Tanuki-phone-guest-$token")
                            check(shared.deleteRecursively()) { "自建共享探针目录清理失败" }
                        }
                    }
                }
            }
        }
    }
}
