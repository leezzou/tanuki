package com.tanuki

import com.tanuki.runtime.DshCookie
import com.tanuki.runtime.DshCookieIsolation
import com.tanuki.runtime.DshCookieJar
import com.tanuki.runtime.RemoteHostConfig
import com.tanuki.runtime.RemoteRelayPairing
import com.tanuki.web.DshConnectionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DshRemoteIsolationTest {
    @Test
    fun cookieJarPartitionsLocalAndTwoRemoteHosts() {
        val jar = DshCookieJar()
        jar.save("local", listOf(cookie("local-auth", "local-secret")))
        jar.save("remote:a", listOf(cookie("remote-a-auth", "a-secret")))
        jar.save("remote:b", listOf(cookie("remote-b-auth", "b-secret")))

        assertEquals(listOf("local-auth"), jar.load("local").map { it.name })
        assertEquals(listOf("remote-a-auth"), jar.load("remote:a").map { it.name })
        assertEquals(listOf("remote-b-auth"), jar.load("remote:b").map { it.name })
        assertTrue(jar.load("remote:a").none { it.value.contains("local-secret") })
        assertTrue(jar.load("remote:b").none { it.value.contains("a-secret") })
        assertTrue(jar.load("local").none { it.value.contains("b-secret") })
    }

    @Test
    fun cookieSnapshotRestoresOnlyOnLoopbackRootAsHttpOnly() {
        val text = cookie("auth", "secret").setCookieValue()
        assertTrue(text.startsWith("auth=secret; Path=/"))
        assertTrue(text.contains("; HttpOnly"))
        assertTrue(text.contains("; SameSite=Strict"))
        assertFalse(text.contains("Domain="))
    }

    @Test
    fun logicalHostScopesAreDistinctForHostsPortsAndUsers() {
        val a = RemoteHostConfig(host = "a.example.test", sshPort = 22, user = "one", remoteDshPort = 3080)
        val b = RemoteHostConfig(host = "b.example.test", sshPort = 22, user = "one", remoteDshPort = 3080)
        val sameHostOtherUser = a.copy(user = "two")
        assertEquals("local", DshCookieIsolation.LOCAL_SCOPE)
        assertFalse(a.cookieScope() == b.cookieScope())
        assertFalse(a.cookieScope() == sameHostOtherUser.cookieScope())
    }

    @Test
    fun relayCookieScopeKeepsUserAndDshPortSeparatedWhileAllowingRoomChanges() {
        val pairing = RemoteRelayPairing(
            relayHost = "relay.example.test",
            relayPort = 8443,
            roomId = "ICEiIyQlJicoKSorLC0uLw",
            token = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8",
            hostId = "303132333435363738393a3b3c3d3e3f"
        )
        val base = RemoteHostConfig(
            host = "relay-display",
            sshPort = 22,
            user = "alice",
            remoteDshPort = 3080,
            relay = pairing
        )
        val otherUser = base.copy(user = "bob")
        val otherDshPort = base.copy(remoteDshPort = 3081)
        val sameServiceOtherRoom = base.copy(relay = pairing.copy(roomId = "MDEyMzQ1Njc4OWFiY2RlZg"))

        // room 不是服务身份：同一用户/端口换房间必须复用 Cookie。
        assertEquals(base.cookieScope(), sameServiceOtherRoom.cookieScope())
        // 用户或远程 DSH 端口变化必须是不同 Cookie 分区。
        assertFalse(base.cookieScope() == otherUser.cookieScope())
        assertFalse(base.cookieScope() == otherDshPort.cookieScope())

        val jar = DshCookieJar()
        jar.save(base.cookieScope(), listOf(cookie("alice-auth", "alice-secret")))
        jar.save(otherUser.cookieScope(), listOf(cookie("bob-auth", "bob-secret")))
        jar.save(otherDshPort.cookieScope(), listOf(cookie("port-auth", "port-secret")))

        assertEquals(listOf("alice-auth"), jar.load(base.cookieScope()).map { it.name })
        assertEquals(listOf("alice-auth"), jar.load(sameServiceOtherRoom.cookieScope()).map { it.name })
        assertEquals(listOf("bob-auth"), jar.load(otherUser.cookieScope()).map { it.name })
        assertEquals(listOf("port-auth"), jar.load(otherDshPort.cookieScope()).map { it.name })
        assertTrue(jar.load(otherUser.cookieScope()).none { it.value.contains("alice-secret") })
        assertTrue(jar.load(otherDshPort.cookieScope()).none { it.value.contains("alice-secret") })
    }

    @Test
    fun authStateUsesActiveEndpointNotLocalProcessState() {
        val remote = "http://127.0.0.1:42000"
        val url = "$remote/?token=synthetic"
        // 本地 STOPPED 时端点已被清理，不能把旧页面认证结果当成当前连接。
        assertFalse(DshConnectionPolicy.canVerifyCookie(null, remote))
        // 远程独立连接没有本地 ServiceState.RUNNING，但活动端点仍允许 200/401 收敛。
        assertTrue(DshConnectionPolicy.canVerifyCookie(remote, remote))
        assertTrue(DshConnectionPolicy.canRetryAuthentication(remote, url, remote, url))
        // Cookie 失效后若端点或 token URL 已变化，不允许把旧票据重放给新连接。
        assertFalse(DshConnectionPolicy.canRetryAuthentication(remote, url, "http://127.0.0.1:42001", url))
        assertFalse(DshConnectionPolicy.canRetryAuthentication(remote, "$remote/?token=other", remote, url))
        assertFalse(DshConnectionPolicy.canVerifyCookie("http://127.0.0.1:42001", remote))
    }

    private fun cookie(name: String, value: String) = DshCookie(name, value)
}
