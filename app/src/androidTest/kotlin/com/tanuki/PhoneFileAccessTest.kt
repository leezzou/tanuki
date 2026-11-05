package com.tanuki

import android.os.Environment
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.storage.PhoneFileAccess
import com.tanuki.storage.PhoneStorageAccess
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** 只操作 Download 下本用例创建的 UUID 文件，不读取或修改任何用户现有文件。 */
class PhoneFileAccessTest {
    private suspend fun fixture(block: suspend (PhoneFileAccess, File, String) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".workspacecheck"))
        assertTrue("宿主须在仪器测试前授予共享存储权限，并在组后恢复", PhoneStorageAccess.isWriteGranted(context))
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Tanuki-phone-rpc-${UUID.randomUUID()}")
        assertFalse(folder.exists()); assertTrue(folder.mkdirs())
        val access = PhoneFileAccess(context, (context.applicationContext as TanukiApp).runtime.paths)
        val path = PhoneStorageAccess.guestPath(Environment.getExternalStorageDirectory(), "Download/${folder.name}")
        try { block(access, folder, path) }
        finally { access.close(); check(folder.deleteRecursively()) }
    }

    private suspend fun denied(code: String, block: suspend () -> Unit) {
        try { block(); fail("应拒绝操作：$code") }
        catch (error: IllegalStateException) { assertEquals(code, error.message) }
    }

    private fun chunk(id: String, offset: Long, text: String) = JSONObject().put("id", id).put("offset", offset)
        .put("dataBase64", Base64.encodeToString(text.toByteArray(), Base64.NO_WRAP))

    @Test fun defaultPermissionReadsPagesButRejectsEveryMutationAndUnsafePath(): Unit = runBlocking {
        fixture { access, folder, path ->
            File(folder, "read.txt").writeText("只读合成数据")
            repeat(205) { File(folder, "entry-$it").writeText("") }
            assertFalse(access.writeAllowed.value)
            assertFalse(access.execute("status", JSONObject()).getBoolean("writeAllowed"))
            val first = access.execute("list", JSONObject().put("path", path).put("limit", 200))
            assertEquals(200, first.getJSONArray("entries").length())
            assertEquals(206, first.getInt("totalEntries"))
            val second = access.execute("list", JSONObject().put("path", path).put("offset", first.getLong("nextOffset")))
            assertEquals(6, second.getJSONArray("entries").length())
            assertTrue(second.isNull("nextOffset"))
            val read = access.execute("read", JSONObject().put("path", "$path/read.txt"))
            assertEquals("只读合成数据", String(Base64.decode(read.getString("dataBase64"), Base64.DEFAULT)))
            for (method in listOf("mkdir", "write_begin", "write_chunk", "write_commit", "rename", "delete")) {
                denied("write_permission_required") { access.execute(method, JSONObject().put("path", "$path/new").put("size", 1)) }
            }
            denied("unknown_method") { access.execute("set_write_allowed", JSONObject().put("enabled", true)) }
            denied("invalid_path") { access.execute("read", JSONObject().put("path", "$path/../private")) }
            denied("invalid_path") { access.execute("read", JSONObject().put("path", "/data/user/0/private")) }
            denied("private_directory_denied") { access.execute("list", JSONObject().put("path", "/phone/primary/Android/data")) }
            denied("private_directory_denied") { access.execute("list", JSONObject().put("path", "/phone/primary/ANDROID/DaTa")) }
            denied("private_directory_denied") { access.execute("list", JSONObject().put("path", "/phone/primary/android/ObB")) }
        }
    }

    @Test fun explicitWriteRequiresOriginalRevisionAndCannotOverwriteAChangedFile(): Unit = runBlocking {
        fixture { access, folder, path ->
            val file = File(folder, "existing.txt").apply { writeText("before") }
            val original = access.execute("stat", JSONObject().put("path", "$path/existing.txt")).getString("revision")
            access.setWriteAllowed(true)
            denied("expected_revision_required") { access.execute("write_begin", JSONObject().put("path", "$path/existing.txt").put("size", 5)) }
            val id = access.execute("write_begin", JSONObject().put("path", "$path/existing.txt").put("size", 5)
                .put("expectedRevision", original)).getString("id")
            access.execute("write_chunk", chunk(id, 0, "after"))
            file.writeText("external change")
            denied("file_changed") { access.execute("write_commit", JSONObject().put("id", id)) }
            assertEquals("external change", file.readText())
            access.execute("write_cancel", JSONObject().put("id", id))
            val fresh = access.execute("write_begin", JSONObject().put("path", "$path/new.txt").put("size", 5)).getString("id")
            access.execute("write_chunk", chunk(fresh, 0, "hello"))
            access.execute("write_commit", JSONObject().put("id", fresh))
            assertEquals("hello", File(folder, "new.txt").readText())
        }
    }

    @Test fun revocationPreventsPreparedUploadAndInProgressCommit(): Unit = runBlocking {
        fixture { access, folder, path ->
            access.setWriteAllowed(true)
            val id = access.execute("write_begin", JSONObject().put("path", "$path/blocked.txt").put("size", 5)).getString("id")
            access.execute("write_chunk", chunk(id, 0, "hello"))
            access.revoke()
            assertFalse(access.writeAllowed.value)
            try { access.execute("write_commit", JSONObject().put("id", id)); fail("撤权后不得提交") }
            catch (error: IllegalStateException) { assertTrue(error.message in setOf("upload_not_found", "write_permission_required", "permission_revoked")) }
            assertFalse(File(folder, "blocked.txt").exists())
            access.setWriteAllowed(true)
            val largeText = "x".repeat(PhoneFileAccess.CHUNK_BYTES)
            val pending = access.execute("write_begin", JSONObject().put("path", "$path/mid-copy.txt").put("size", largeText.length)).getString("id")
            access.execute("write_chunk", chunk(pending, 0, largeText))
            denied("write_permission_required") {
                access.execute("write_commit", JSONObject().put("id", pending)) { copied, _ -> if (copied > 0) access.revoke() }
            }
            assertFalse(File(folder, "mid-copy.txt").exists())
            assertFalse(folder.list()!!.any { it.startsWith(".tanuki-phone-") })
        }
    }

    @Test fun expiredConnectionCannotFinishAnAlreadyStartedCommit(): Unit = runBlocking {
        fixture { access, folder, path ->
            access.setWriteAllowed(true)
            val text = "x".repeat(PhoneFileAccess.CHUNK_BYTES)
            val id = access.execute("write_begin", JSONObject().put("path", "$path/stale-connection.txt").put("size", text.length)).getString("id")
            access.execute("write_chunk", chunk(id, 0, text))
            var current = true
            denied("connection_expired") {
                access.execute("write_commit", JSONObject().put("id", id), isCurrent = { current }) { copied, _ ->
                    if (copied > 0) current = false
                }
            }
            assertFalse(File(folder, "stale-connection.txt").exists())
            assertFalse(folder.list()!!.any { it.startsWith(".tanuki-phone-") })
            assertTrue("失效请求不能修改新运行期的原生授权", access.writeAllowed.value)
        }
    }

    @Test fun nativeGrantCanCreateRenameAndDeleteFilesButNeverRecursesOrChangesRoot(): Unit = runBlocking {
        fixture { access, folder, path ->
            access.setWriteAllowed(true)
            access.execute("mkdir", JSONObject().put("path", "$path/empty"))
            val revision = access.execute("stat", JSONObject().put("path", "$path/empty")).getString("revision")
            access.execute("rename", JSONObject().put("path", "$path/empty").put("to", "$path/renamed").put("expectedRevision", revision))
            assertTrue(File(folder, "renamed").isDirectory)
            val updated = access.execute("stat", JSONObject().put("path", "$path/renamed")).getString("revision")
            access.execute("delete", JSONObject().put("path", "$path/renamed").put("expectedRevision", updated))
            assertFalse(File(folder, "renamed").exists())
            File(folder, "nonempty").mkdir(); File(folder, "nonempty/keep").writeText("keep")
            denied("directory_not_empty") { access.execute("delete", JSONObject().put("path", "$path/nonempty").put("expectedRevision", "unused")) }
            denied("volume_root_protected") { access.execute("mkdir", JSONObject().put("path", "/phone/primary")) }
            assertEquals("keep", File(folder, "nonempty/keep").readText())
        }
    }
}
