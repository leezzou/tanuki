package com.tanuki

import com.tanuki.runtime.DshSwitch
import com.tanuki.runtime.DshVersions
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DshSwitchTest {
    private fun requireSwitchable(
        target: String,
        current: String = "1.2.0",
        installed: Set<String> = setOf("1.2.0", "1.3.0"),
        targetLockVerified: Boolean = true,
        serviceStopped: Boolean = true,
        busy: Boolean = false,
        liveChild: Boolean = false
    ) = DshSwitch.requireSwitchable(target, current, installed, targetLockVerified, serviceStopped, busy, liveChild)

    @Test
    fun rejectsCurrentVersion() {
        val error = assertThrows(IllegalArgumentException::class.java) { requireSwitchable("1.2.0") }
        assertTrue(error.message!!.contains("已是当前版本"))
    }

    @Test
    fun rejectsUninstalledOrIncompleteVersion() {
        val error = assertThrows(IllegalArgumentException::class.java) { requireSwitchable("9.9.9") }
        assertTrue(error.message!!.contains("未安装或程序目录不完整"))
    }

    @Test
    fun rejectsRunningBusyOrLiveChild() {
        assertTrue(assertThrows(IllegalStateException::class.java) {
            requireSwitchable("1.3.0", busy = true)
        }.message!!.contains("等待当前操作完成"))
        assertTrue(assertThrows(IllegalStateException::class.java) {
            requireSwitchable("1.3.0", liveChild = true)
        }.message!!.contains("等待当前操作完成"))
        assertTrue(assertThrows(IllegalStateException::class.java) {
            requireSwitchable("1.3.0", serviceStopped = false)
        }.message!!.contains("先停止 DSH 服务"))
    }

    @Test
    fun rejectsDowngradeWithSnapshotGuidance() {
        val error = assertThrows(IllegalStateException::class.java) {
            requireSwitchable("1.1.0", installed = setOf("1.1.0", "1.2.0"))
        }
        assertEquals(DshVersions.DOWNGRADE_MESSAGE, error.message)
        assertTrue(error.message!!.contains("恢复匹配的数据快照"))
    }

    @Test
    fun rejectsTargetWithMissingOrMismatchedLock() {
        val error = assertThrows(IllegalStateException::class.java) {
            requireSwitchable("1.3.0", targetLockVerified = false)
        }
        assertTrue(error.message!!.contains("依赖锁缺失或与 .installed 不一致"))
    }

    @Test
    fun rejectsSwitchWhenCurrentProgramIsMissing() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            requireSwitchable("1.3.0", current = "1.2.0", installed = setOf("1.3.0"))
        }
        assertTrue(error.message!!.contains("当前 DSH 1.2.0 程序缺失或不完整"))
    }

    @Test
    fun rejectsInvalidCurrentMetadata() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            requireSwitchable("1.3.0", current = "")
        }
        assertTrue(error.message!!.contains("缺少有效 DSH 版本"))
    }

    @Test
    fun allowsNewerInstalledVersionWhenIdle() {
        requireSwitchable("1.3.0")
        requireSwitchable("1.3.0", current = "1.2.0-alpha.2", installed = setOf("1.2.0-alpha.2", "1.3.0"))
    }

    @Test
    fun applySnapshotsFirstAndOnlyChangesDshMetadata() = runBlocking {
        val previous = JSONObject()
            .put("ready", true)
            .put("ubuntu", "24.04")
            .put("node", "22.0.0")
            .put("dsh", "1.2.0")
        val order = mutableListOf<String>()
        var written: JSONObject? = null

        val snapshot = DshSwitch.apply(
            previous = previous,
            target = "1.3.0",
            compatible = { order += "compatible" },
            snapshot = { order += "snapshot"; "snapshot-123" },
            write = { order += "write"; written = JSONObject(it.toString()) }
        )

        assertEquals(listOf("compatible", "snapshot", "write"), order)
        assertEquals("snapshot-123", snapshot)
        val output = requireNotNull(written)
        assertEquals("1.3.0", output.getString("dsh"))
        assertEquals("24.04", output.getString("ubuntu"))
        assertEquals("22.0.0", output.getString("node"))
        assertTrue(output.getBoolean("ready"))
        assertEquals("1.2.0", previous.getString("dsh"))
    }

    @Test
    fun compatibilityFailureLeavesMetadataAndSnapshotUntouched() = runBlocking {
        val previous = JSONObject().put("ready", true).put("node", "20.0.0").put("dsh", "1.2.0")
        var snapshots = 0
        var writes = 0
        var failure: Throwable? = null

        try {
            DshSwitch.apply(
                previous = previous,
                target = "1.3.0",
                compatible = { throw IllegalStateException("DSH 1.3.0 要求 Node.js >=22") },
                snapshot = { snapshots++; "snapshot-123" },
                write = { writes++ }
            )
        } catch (error: Throwable) {
            failure = error
        }

        assertEquals("DSH 1.3.0 要求 Node.js >=22", failure?.message)
        assertEquals("兼容性失败时不能创建快照", 0, snapshots)
        assertEquals("兼容性失败时不能改元数据", 0, writes)
        assertEquals("1.2.0", previous.getString("dsh"))
    }

    @Test
    fun writeFailureRollsBackMetadata() = runBlocking {
        val previous = JSONObject().put("ready", true).put("node", "22.0.0").put("dsh", "1.2.0")
        var stored = JSONObject(previous.toString())
        var writes = 0
        var failure: Throwable? = null

        try {
            DshSwitch.apply(
                previous = previous,
                target = "1.3.0",
                snapshot = { "snapshot-123" },
                write = { candidate ->
                    writes++
                    stored = JSONObject(candidate.toString())
                    if (writes == 1) throw IllegalStateException("模拟写入失败")
                }
            )
        } catch (error: Throwable) {
            failure = error
        }

        assertEquals("模拟写入失败", failure?.message)
        assertEquals("写入失败后应重写 previous", 2, writes)
        assertEquals("1.2.0", stored.getString("dsh"))
        assertEquals("22.0.0", stored.getString("node"))
        assertTrue(stored.getBoolean("ready"))
    }

    @Test
    fun snapshotFailureLeavesMetadataUntouched() = runBlocking {
        val previous = JSONObject().put("ready", true).put("dsh", "1.2.0")
        var writes = 0
        var failure: Throwable? = null

        try {
            DshSwitch.apply(
                previous = previous,
                target = "1.3.0",
                snapshot = { throw IllegalStateException("快照空间不足") },
                write = { writes++ }
            )
        } catch (error: Throwable) {
            failure = error
        }

        assertEquals("快照空间不足", failure?.message)
        assertEquals(0, writes)
        assertEquals("1.2.0", previous.getString("dsh"))
        assertFalse(previous.has("ubuntu"))
    }
}
