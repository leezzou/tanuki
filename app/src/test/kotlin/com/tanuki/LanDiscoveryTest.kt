package com.tanuki

import com.tanuki.lan.LanDiscoveredHost
import com.tanuki.lan.LanDiscoveryCopy
import com.tanuki.lan.LanDiscoveryList
import com.tanuki.lan.LanDiscoveryPermission
import com.tanuki.lan.LanDiscoveryTxt
import com.tanuki.lan.LanResolveQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 局域网发现的纯 JVM 回归：不依赖 Android、NsdManager 或真实网络。 */
class LanDiscoveryTest {

    private fun txt(vararg pairs: Pair<String, String>): Map<String, ByteArray> =
        pairs.associate { (key, value) -> key to value.toByteArray(Charsets.UTF_8) }

    private fun validTxt(
        name: String = "书房 Mac",
        ssh: String = "22",
        dsh: String = "3080",
        host: String = HOST_ID,
        ver: String = "1",
        fp: String = FINGERPRINT
    ) = txt(
        "name" to name,
        "ssh" to ssh,
        "dsh" to dsh,
        "host" to host,
        "ver" to ver,
        "fp" to fp
    )

    private fun host(
        id: Int = 1,
        name: String = "电脑$id",
        online: Boolean = true,
        lastSeen: Long = 0L
    ) = LanDiscoveredHost(
        serviceName = "$name._tanuki-remote._tcp.local",
        name = name,
        sshPort = 22,
        dshPort = 3080,
        hostId = id.toString(16).padStart(32, '0'),
        protocolVersion = 1,
        fingerprint = id.toString(16).padStart(16, '0'),
        address = "192.168.1.${id.coerceAtMost(254)}",
        online = online,
        lastSeenMillis = lastSeen
    )

    // ---------- TXT 解析 ----------

    @Test
    fun parsesAllContractFields() {
        val parsed = LanDiscoveryTxt.parse(
            serviceName = "书房 Mac",
            attributes = validTxt(),
            address = "192.168.1.20",
            now = 1234L
        )!!
        assertEquals("书房 Mac", parsed.name)
        assertEquals(22, parsed.sshPort)
        assertEquals(3080, parsed.dshPort)
        assertEquals(HOST_ID, parsed.hostId)
        assertEquals(1, parsed.protocolVersion)
        assertEquals(FINGERPRINT, parsed.fingerprint)
        assertEquals("192.168.1.20", parsed.address)
        assertTrue(parsed.online)
        assertEquals(1234L, parsed.lastSeenMillis)
        assertTrue(parsed.connectable)
    }

    @Test
    fun fallsBackToServiceInstanceNameAndStripsSuffix() {
        val parsed = LanDiscoveryTxt.parse(
            serviceName = "客厅电脑._tanuki-remote._tcp.local",
            attributes = validTxt(name = ""),
            address = "192.168.1.30"
        )!!
        assertEquals("客厅电脑", parsed.name)
    }

    @Test
    fun rejectsIncompleteOrInvalidTxt() {
        assertNull(LanDiscoveryTxt.parse("坏主机", validTxt(ssh = "0"), "192.168.1.2"))
        assertNull(LanDiscoveryTxt.parse("坏主机", validTxt(dsh = "70000"), "192.168.1.2"))
        assertNull(LanDiscoveryTxt.parse("坏主机", validTxt(host = "not-a-host"), "192.168.1.2"))
        assertNull(LanDiscoveryTxt.parse("坏主机", validTxt(ver = "0"), "192.168.1.2"))
        assertNull(LanDiscoveryTxt.parse("坏主机", validTxt(fp = "xyz"), "192.168.1.2"))
        assertNull(LanDiscoveryTxt.parse("坏主机", validTxt().filterKeys { it != "host" }, "192.168.1.2"))
    }

    @Test
    fun ignoresUnrelatedTxtKeysAndNeverSurfacesSecrets() {
        val token = "synthetic-token-must-not-appear"
        val attributes = validTxt() + mapOf(
            "token" to token.toByteArray(Charsets.UTF_8),
            "room" to "synthetic-room".toByteArray(Charsets.UTF_8)
        )
        val parsed = LanDiscoveryTxt.parse("书房 Mac", attributes, "192.168.1.20")!!
        assertFalse(parsed.toString().contains(token))
        assertFalse(parsed.toString().contains("synthetic-room"))
    }

    @Test
    fun sanitizesNameAndRejectsLoopbackAddress() {
        val parsed = LanDiscoveryTxt.parse(
            serviceName = "bad",
            attributes = validTxt(name = " 书\u0000房./Mac  "),
            address = "127.0.0.1"
        )!!
        assertEquals("书 房--Mac", parsed.name)
        assertEquals("", parsed.address)
        assertFalse(parsed.connectable)
    }

    // ---------- 去重与排序 ----------

    @Test
    fun duplicateHostIdKeepsNewestAndSortsOnlineFirst() {
        val older = host(id = 1, name = "旧名字", online = false)
        val newest = older.copy(name = "新名字", sshPort = 2222)
        val other = host(id = 2, name = "A 电脑", online = true)
        val offline = host(id = 3, name = "B 电脑", online = false)

        val merged = LanDiscoveryList.merge(listOf(older, other, offline), newest, now = 100L)

        assertEquals(3, merged.size)
        assertEquals("新名字", merged.first { it.hostId == older.hostId }.name)
        assertEquals(2222, merged.first { it.hostId == older.hostId }.sshPort)
        assertEquals("A 电脑", merged[0].name)
        assertEquals("新名字", merged[1].name)
        assertEquals("B 电脑", merged[2].name)
    }

    @Test
    fun rescanningKeepsOnlineUntilExpiryThenPrunes() {
        val online = host(id = 1, name = "A", online = true)
        val stale = host(id = 2, name = "B", online = false, lastSeen = 0L)

        val rescanned = LanDiscoveryList.finishScan(listOf(online), now = 5_000L)
        assertTrue(rescanned.single().online)
        val expired = LanDiscoveryList.finishScan(listOf(online), now = LanDiscoveryList.ONLINE_TTL_MILLIS + 1)
        assertFalse(expired.single().online)

        val kept = LanDiscoveryList.finishScan(
            listOf(stale.copy(lastSeenMillis = 10_000L)),
            now = 10_000L + LanDiscoveryList.OFFLINE_TTL_MILLIS
        )
        assertEquals(1, kept.size)

        val pruned = LanDiscoveryList.finishScan(
            listOf(stale.copy(lastSeenMillis = 10_000L)),
            now = 10_000L + LanDiscoveryList.OFFLINE_TTL_MILLIS + 1
        )
        assertTrue(pruned.isEmpty())
    }

    @Test
    fun lostServiceMarksOfflineWithoutRemovingImmediately() {
        val online = host(id = 1, name = "A", online = true)
        val lost = LanDiscoveryList.lost(listOf(online), online.serviceName, now = 50L)
        assertEquals(1, lost.size)
        assertFalse(lost.single().online)
        assertEquals(50L, lost.single().lastSeenMillis)
    }

    // ---------- 权限拒绝降级 ----------

    @Test
    fun permissionPolicyOnlyRequestsOnceThenDegrades() {
        assertEquals(
            LanDiscoveryPermission.Decision.ALLOWED,
            LanDiscoveryPermission.decide(sdkInt = 32, granted = false, askedBefore = false)
        )
        assertEquals(
            LanDiscoveryPermission.Decision.REQUEST,
            LanDiscoveryPermission.decide(sdkInt = 33, granted = false, askedBefore = false)
        )
        assertEquals(
            LanDiscoveryPermission.Decision.DEGRADED,
            LanDiscoveryPermission.decide(sdkInt = 33, granted = false, askedBefore = true)
        )
        assertEquals(
            LanDiscoveryPermission.Decision.ALLOWED,
            LanDiscoveryPermission.decide(sdkInt = 33, granted = true, askedBefore = true)
        )
        assertTrue(LanDiscoveryPermission.required(33))
        assertFalse(LanDiscoveryPermission.required(32))
        assertTrue(LanDiscoveryCopy.PERMISSION_DENIED.contains("扫码"))
    }

    // ---------- 空态与列表文案 ----------

    @Test
    fun emptyStateKeepsCommandAndFallbackEntrances() {
        assertEquals("没有发现同一 Wi-Fi 下的电脑", LanDiscoveryCopy.EMPTY_TITLE)
        assertEquals("bash scripts/remote-relay/quickstart.sh", LanDiscoveryCopy.HOST_COMMAND)
        assertTrue(LanDiscoveryCopy.EMPTY_DETAIL.contains("终端"))
        assertEquals("复制命令", LanDiscoveryCopy.COPY_COMMAND)
        assertEquals("改用扫码", LanDiscoveryCopy.USE_QR)
        assertTrue(LanDiscoveryCopy.OTHER_DETAIL.contains("扫码"))
        assertTrue(LanDiscoveryCopy.SAFETY.contains("指纹"))
    }

    @Test
    fun listItemShowsNameOnlinePlatformAndVersion() {
        val parsed = LanDiscoveryTxt.parse("书房 Mac", validTxt(), "192.168.1.20")!!
        assertEquals("书房 Mac · 在线", LanDiscoveryCopy.itemTitle(parsed))
        assertEquals("平台未提供 · 协议 v1", LanDiscoveryCopy.itemSubtitle(parsed))
        assertEquals(
            "书房 Mac · 离线",
            LanDiscoveryCopy.itemTitle(parsed.copy(online = false))
        )
    }

    @Test
    fun malformedUtf8AndOversizeTxtAndUnsupportedVersionAreRejected() {
        assertNull(LanDiscoveryTxt.parse("test", validTxt() + ("name" to byteArrayOf(0xc3.toByte(), 0x28)), "192.168.1.20"))
        assertNull(LanDiscoveryTxt.parse("test", validTxt(name = "a".repeat(256)), "192.168.1.20"))
        assertNull(LanDiscoveryTxt.parse("test", validTxt(ver = "2"), "192.168.1.20"))
    }

    @Test
    fun queueIsSerialDeduplicatedAndIgnoresOldCallbacksAfterReentry() {
        val queue = LanResolveQueue<String>()
        queue.start()
        queue.offer("one", "first")
        queue.offer("one", "latest")
        val old = queue.next()!!
        assertEquals("latest", old.value)
        queue.offer("two", "second")
        assertNull(queue.next())
        queue.stop()
        queue.start()
        queue.offer("new", "new page")
        assertNull(queue.next())
        assertFalse(queue.complete(old))
        val next = queue.next()!!
        assertEquals("new", next.name)
        assertFalse(queue.complete(old))
        assertNull(queue.next())
        assertTrue(queue.complete(next))
    }

    @Test
    fun lostQueuedServiceIsNeverResolvedAndStoppedQueueAcceptsNothing() {
        val queue = LanResolveQueue<String>()
        queue.start()
        queue.offer("gone", "value")
        queue.remove("gone")
        assertNull(queue.next())
        queue.stop()
        queue.offer("later", "value")
        queue.start()
        assertNull(queue.next())
    }

    @Test
    fun lostInFlightServiceCannotBecomeOnlineAgainFromStaleResult() {
        val queue = LanResolveQueue<String>()
        queue.start()
        queue.offer("computer", "old address")
        val old = queue.next()!!
        queue.remove("computer")
        queue.offer("computer", "new address")
        assertFalse(queue.complete(old))
        val updated = queue.next()!!
        assertEquals("new address", updated.value)
        assertTrue(queue.complete(updated))
    }

    @Test
    fun unusableAddressesAreNotOfferedForSshAndGlobalIpv6Works() {
        listOf("127.0.0.2", "0.0.0.0", "169.254.1.2", "224.0.0.251", "::1", "0:0:0:0:0:0:0:1", "fe80::1%wlan0", "bad.example.test").forEach { address ->
            assertFalse(address, LanDiscoveryTxt.parse("test", validTxt(), address)!!.connectable)
        }
        assertTrue(LanDiscoveryTxt.parse("test", validTxt(), "2001:db8::10")!!.connectable)
    }

    @Test
    fun unicodeNamesAreTruncatedOnCodePointBoundary() {
        val name = LanDiscoveryTxt.sanitizeName("🙂".repeat(20))
        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 63)
        assertEquals("🙂".repeat(15), name)
    }

    private companion object {
        const val HOST_ID = "303132333435363738393a3b3c3d3e3f"
        const val FINGERPRINT = "a1b2c3d4e5f60718"
    }
}
