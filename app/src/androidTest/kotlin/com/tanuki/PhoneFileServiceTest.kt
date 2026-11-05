package com.tanuki

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Environment
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.runtime.FileTransfers
import com.tanuki.runtime.Paths
import com.tanuki.runtime.RuntimeManager
import com.tanuki.runtime.ServiceState
import com.tanuki.storage.PhoneFileIntegration
import com.tanuki.storage.PhoneFileService
import com.tanuki.storage.PhoneStorageAccess
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.nio.file.Files
import java.util.UUID

/** 仅使用独立手机目录和测试连接，连接能力值不输出；系统授权由宿主在仪器外设置。 */
class PhoneFileServiceTest {
    @get:org.junit.Rule val visibleAccessibility = VisibleAccessibilityRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private class FixtureContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getFilesDir() = root
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
    }
    private fun request(connection: JSONObject, method: String, params: JSONObject = JSONObject(),
                        origin: Boolean = false, wrongToken: Boolean = false): JSONObject {
        val client = URL("http://127.0.0.1:${connection.getInt("port")}/rpc").openConnection() as HttpURLConnection
        try {
            client.requestMethod = "POST"; client.connectTimeout = 5000; client.readTimeout = 15000; client.doOutput = true
            client.setRequestProperty("Authorization", "Bearer " + if (wrongToken) "invalid-test-capability" else connection.getString("token"))
            client.setRequestProperty("Content-Type", "application/json")
            if (origin) client.setRequestProperty("Origin", "https://example.invalid")
            client.outputStream.use { it.write(JSONObject().put("method", method).put("params", params).toString().toByteArray()) }
            return client.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally { client.disconnect() }
    }
    private fun fixture(block: (Paths, PhoneFileService, JSONObject) -> Unit) {
        check(context.packageName.endsWith(".workspacecheck"))
        check(PhoneStorageAccess.isWriteGranted(context)) { "宿主必须在启动测试前授权共享存储" }
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "phone-rpc-").toFile()
        val wrapped = FixtureContext(context, root); val paths = Paths(wrapped)
        val service = PhoneFileService(wrapped, paths, FileTransfers { SystemClock.elapsedRealtime() })
        try {
            service.start()
            val connection = JSONObject(File(PhoneFileIntegration.directory(paths), "connection.json").readText())
            block(paths, service, connection)
        } finally { service.stop(); service.access.close(); RuntimeManager.deleteTree(root) }
    }
    @Test fun defaultReadOnlyAndTransportCannotAuthorizeItsOwnWrites() {
        fixture { _, service, connection ->
            val status = request(connection, "status")
            assertTrue(status.getBoolean("ok")); assertFalse(status.getJSONObject("result").getBoolean("writeAllowed"))
            assertTrue(status.getJSONObject("result").getBoolean("readAllowed"))
            assertFalse(request(connection, "status", wrongToken = true).getBoolean("ok"))
            assertFalse(request(connection, "status", origin = true).getBoolean("ok"))
            assertFalse(request(connection, "setWriteAllowed", JSONObject().put("enabled", true)).getBoolean("ok"))
            val rejected = request(connection, "write_begin", JSONObject().put("path", "/phone/primary/Download/no-write.txt").put("size", 0))
            assertFalse(rejected.getBoolean("ok")); assertEquals("write_permission_required", rejected.getString("error"))
            assertFalse(service.writeAllowed.value)
        }
    }
    @Test fun nativeGrantWritesOnlySyntheticFileAndRestartRevokesTheConnection() {
        fixture { paths, service, connection ->
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Tanuki-rpc-${UUID.randomUUID()}")
            check(directory.mkdirs())
            val logical = "/phone/primary/Download/${directory.name}/result.txt"
            try {
                service.setWriteAllowed(true)
                val begin = request(connection, "write_begin", JSONObject().put("path", logical).put("size", 6))
                assertTrue(begin.toString(), begin.getBoolean("ok"))
                val id = begin.getJSONObject("result").getString("id")
                assertTrue(request(connection, "write_chunk", JSONObject().put("id", id).put("offset", 0).put("dataBase64", "cHVibGlj")).getBoolean("ok"))
                assertTrue(request(connection, "write_commit", JSONObject().put("id", id)).getBoolean("ok"))
                assertEquals("public", File(directory, "result.txt").readText())
                service.setWriteAllowed(false)
                assertFalse(request(connection, "write_begin", JSONObject().put("path", logical).put("size", 0)).getBoolean("ok"))
                val oldRun = service.currentRunId()
                assertNotNull(oldRun)
                service.stop(); service.start()
                assertFalse(service.writeAllowed.value)
                service.setWriteAllowed(true, oldRun)
                assertFalse("旧运行期的异步授权结果不能开启新运行期写权限", service.writeAllowed.value)
                val next = JSONObject(File(PhoneFileIntegration.directory(paths), "connection.json").readText())
                val oldAtNewPort = JSONObject(connection.toString()).put("port", next.getInt("port"))
                assertFalse(request(oldAtNewPort, "status").getBoolean("ok"))
                assertTrue(request(next, "status").getBoolean("ok"))
            } finally { directory.deleteRecursively() }
        }
    }

    @Test fun oldAuthenticatedPartialRequestCannotWriteAfterRestartAndNewNativeGrant() {
        fixture { paths, service, connection ->
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Tanuki-old-rpc-${UUID.randomUUID()}")
            check(directory.mkdirs())
            val blocked = File(directory, "must-not-exist")
            val logical = PhoneStorageAccess.guestPath(Environment.getExternalStorageDirectory(), "Download/${directory.name}/${blocked.name}")
            val body = JSONObject().put("method", "mkdir").put("params", JSONObject().put("path", logical)).toString().toByteArray()
            try {
                Socket("127.0.0.1", connection.getInt("port")).use { socket ->
                    socket.soTimeout = 15_000
                    val output = socket.getOutputStream()
                    // 先让旧连接完成认证并进入请求体读取；仅发送首字节，保证操作尚未执行。
                    val headers = "POST /rpc HTTP/1.1\r\nAuthorization: Bearer ${connection.getString("token")}\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\n\r\n"
                    output.write(headers.toByteArray(Charsets.US_ASCII)); output.write(body, 0, 1); output.flush()
                    val deadline = SystemClock.elapsedRealtime() + 5_000
                    var waitingForBody = false
                    while (!waitingForBody && SystemClock.elapsedRealtime() < deadline) {
                        waitingForBody = Thread.getAllStackTraces().any { (thread, trace) ->
                            thread.name == "phone-files-worker" &&
                                trace.any { it.className == PhoneFileService::class.java.name && it.methodName == "handle" } &&
                                trace.none { it.className == PhoneFileService::class.java.name && it.methodName == "line" } &&
                                trace.any { it.methodName in setOf("read", "read0", "socketRead", "socketRead0") }
                        }
                        if (!waitingForBody) SystemClock.sleep(20)
                    }
                    assertTrue("必须观测旧处理器卡在请求体读取，不能只发送headers后猜测时序", waitingForBody)
                    assertFalse(blocked.exists())
                    service.stop(); service.start()
                    assertFalse("新运行期必须从只读开始", service.writeAllowed.value)
                    service.setWriteAllowed(true)
                    val next = JSONObject(File(PhoneFileIntegration.directory(paths), "connection.json").readText())
                    assertTrue(request(next, "status").getJSONObject("result").getBoolean("writeAllowed"))
                    // 新连接已单独授权后，才补齐旧连接请求体；旧能力不能借用新授权产生副作用。
                    output.write(body, 1, body.size - 1); output.flush()
                    val raw = socket.getInputStream().bufferedReader().readText()
                    assertTrue("旧连接应得到受控HTTP回复", raw.startsWith("HTTP/1.1 200"))
                    val result = JSONObject(raw.substringAfter("\r\n\r\n"))
                    assertFalse(result.getBoolean("ok"))
                    assertEquals("connection_expired", result.getString("error"))
                    assertFalse("旧请求即使最终返回错误，也不能创建目录", blocked.exists())
                }
            } finally { directory.deleteRecursively() }
        }
    }
    @Test fun noSystemStorageGrantMeansNoPhoneReadOrWrite(): Unit = runBlocking {
        check(context.packageName.endsWith(".workspacecheck"))
        assertFalse("此方法必须由宿主在未授权批次单独启动", PhoneStorageAccess.isGranted(context))
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "phone-denied-").toFile()
        val wrapped = FixtureContext(context, root); val paths = Paths(wrapped)
        val service = PhoneFileService(wrapped, paths, FileTransfers { SystemClock.elapsedRealtime() })
        try {
            service.start()
            val connection = JSONObject(File(PhoneFileIntegration.directory(paths), "connection.json").readText())
            assertFalse(request(connection, "status").getJSONObject("result").getBoolean("readAllowed"))
            val result = request(connection, "read", JSONObject().put("path", "/phone/primary/Download/unused.txt"))
            assertFalse(result.getBoolean("ok")); assertEquals("storage_permission_required", result.getString("error"))
        } finally { service.stop(); service.access.close(); RuntimeManager.deleteTree(root) }
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        val first = instrumentation.uiAutomation.rootInActiveWindow ?: return emptyList()
        val result = mutableListOf<AccessibilityNodeInfo>(); val queue = java.util.ArrayDeque<AccessibilityNodeInfo>().apply { add(first) }
        while (queue.isNotEmpty() && result.size < 1200) {
            val node = queue.removeFirst(); if (!node.refresh()) continue
            result += node; for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
        }
        return result
    }
    private fun click(label: String) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        var target: AccessibilityNodeInfo? = null
        while (target == null && SystemClock.elapsedRealtime() < deadline) {
            target = nodes().lastOrNull { it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label) }
            if (target == null) SystemClock.sleep(100)
        }
        while (target != null && !target.isClickable) target = target.parent
        check(target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "未找到可点击测试控件：$label" }
        SystemClock.sleep(150)
    }
    @Test fun actualPhonePageRequiresSeparateNativeConfirmationAndCanRevoke() {
        check(context.packageName.endsWith(".workspacecheck") && PhoneStorageAccess.isGranted(context))
        val runtime = (context.applicationContext as TanukiApp).runtime
        check(!runtime.hasLiveChild && !runtime.hasActiveOperation)
        val original = runtime.state.value
        runtime.phoneFiles.start()
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        try {
            instrumentation.runOnMainSync { runtime.state.value = runtime.state.value.copy(service = ServiceState.RUNNING) }
            click("文件"); click("工作区"); click("手机存储 · 只读")
            assertFalse(runtime.phoneFiles.writeAllowed.value)
            click("授权 DSH 写入"); click("保持只读"); assertFalse(runtime.phoneFiles.writeAllowed.value)
            click("授权 DSH 写入"); click("允许本次写入"); assertTrue(runtime.phoneFiles.writeAllowed.value)
            click("撤销 DSH 写入权限"); assertFalse(runtime.phoneFiles.writeAllowed.value)
        } finally {
            runtime.phoneFiles.stop()
            instrumentation.runOnMainSync { runtime.state.value = original; activity.finish() }
        }
    }
}
