package com.tanuki

import com.tanuki.web.ChatNotificationDecision
import com.tanuki.web.ChatNotificationRoute
import com.tanuki.web.ChatNotificationRoutePolicy
import com.tanuki.web.ChatNotificationTargetPolicy
import com.tanuki.web.DshApiProtocol
import com.tanuki.web.DshEndpoint
import com.tanuki.web.DshUiMode
import com.tanuki.web.DshUiSwitchPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DshUiSwitchTest {
    @Test fun defaultPreferenceIsOfficial() {
        assertEquals(DshUiMode.OFFICIAL, DshUiMode.parse(null))
        assertEquals(DshUiMode.OFFICIAL, DshUiMode.parse(""))
        assertEquals(DshUiMode.OFFICIAL, DshUiMode.parse("unknown"))
        assertEquals(DshUiMode.NATIVE, DshUiMode.parse("native"))
        assertEquals("official", DshUiMode.OFFICIAL.storageValue)
    }

    @Test fun nativeRequiresExplicitPreferenceAndRunningService() {
        assertFalse(DshUiSwitchPolicy.useNative(DshUiMode.OFFICIAL, true, null))
        assertFalse(DshUiSwitchPolicy.useNative(DshUiMode.NATIVE, false, null))
        assertTrue(DshUiSwitchPolicy.useNative(DshUiMode.NATIVE, true, null))
    }

    @Test fun apiFailureFallsBackToOfficialWithoutChangingPreference() {
        val preference = DshUiMode.NATIVE
        assertFalse(DshUiSwitchPolicy.useNative(preference, true, "官方接口返回 401"))
        assertEquals(DshUiMode.OFFICIAL, DshUiSwitchPolicy.effective(preference, true, "官方接口返回 401"))
        assertEquals(DshUiMode.NATIVE, DshUiSwitchPolicy.effective(preference, true, null))
        assertEquals(preference, DshUiMode.parse("native"))
    }

    @Test fun connectUrlReusesLaunchTokenAndAddsApiInteractionMode() {
        val origin = "http://127.0.0.1:43210"
        val endpoint = DshEndpoint(origin, "$origin/?token=secret-token")
        val connect = DshApiProtocol.connectUrl(endpoint, interactions = "answer")
        assertNotNull(connect)
        assertTrue(connect!!.startsWith("$origin/connect?"))
        assertTrue(connect.contains("token=secret-token"))
        assertTrue(connect.endsWith("&interactions=answer"))
        val replaced = DshApiProtocol.connectUrl(DshEndpoint(origin, "$origin/?token=secret-token&interactions=none"), interactions = "answer")
        assertNotNull(replaced)
        assertEquals(1, Regex("interactions=").findAll(replaced!!).count())
        assertTrue(replaced.endsWith("interactions=answer"))
        // 只读调用仍可显式回到 none，避免把交互能力写死在协议层。
        val readOnly = DshApiProtocol.connectUrl(DshEndpoint(origin, "$origin/?token=secret-token"), interactions = "none")
        assertNotNull(readOnly)
        assertTrue(readOnly!!.endsWith("interactions=none"))
        assertNull(DshApiProtocol.connectUrl(endpoint, interactions = "bad"))
    }

    @Test fun connectUrlRejectsForeignOriginMissingTokenAndNonHttp() {
        assertNull(DshApiProtocol.connectUrl(DshEndpoint("http://127.0.0.1:1", "http://127.0.0.1:2/?token=x")))
        assertNull(DshApiProtocol.connectUrl(DshEndpoint("https://127.0.0.1:1", "https://127.0.0.1:1/?token=x")))
        assertNull(DshApiProtocol.connectUrl(DshEndpoint("http://127.0.0.1:1", "http://127.0.0.1:1/")))
        assertNull(DshApiProtocol.connectUrl(DshEndpoint("http://127.0.0.1:1", "http://localhost:1/?token=x")))
    }

    @Test fun apiRoutesAreBuiltFromTheCurrentLoopbackOriginOnly() {
        val origin = "http://127.0.0.1:43210"
        assertEquals("$origin/api/session/list", DshApiProtocol.apiUrl(origin, "session/list"))
        assertEquals("$origin/api/session/projections", DshApiProtocol.apiUrl(origin, "session/projections"))
        assertEquals("$origin/api/session/page", DshApiProtocol.apiUrl(origin, "session/page"))
        assertNull(DshApiProtocol.apiUrl("http://localhost:43210", "session/list"))
        assertNull(DshApiProtocol.apiUrl("https://127.0.0.1:43210", "session/list"))
        assertNull(DshApiProtocol.apiUrl(origin, "../session/list"))
        assertNull(DshApiProtocol.apiUrl(origin, "session/../list"))
    }

    @Test fun nativePreferenceRoutesNotificationTargetToNativeWithOrWithoutWebView() {
        // 冷启动（WebView 未连接）与已有 WebView 已连接都必须进入原生目标会话。
        assertEquals(
            ChatNotificationRoute.NATIVE,
            ChatNotificationRoutePolicy.route(DshUiMode.NATIVE, true, null, officialWebConnected = false)
        )
        assertEquals(
            ChatNotificationRoute.NATIVE,
            ChatNotificationRoutePolicy.route(DshUiMode.NATIVE, true, null, officialWebConnected = true)
        )
        assertEquals(
            ChatNotificationRoute.OFFICIAL_READY,
            ChatNotificationRoutePolicy.route(DshUiMode.OFFICIAL, true, null, officialWebConnected = true)
        )
        assertEquals(
            ChatNotificationRoute.OFFICIAL_WAIT,
            ChatNotificationRoutePolicy.route(DshUiMode.OFFICIAL, true, null, officialWebConnected = false)
        )
        assertEquals(
            ChatNotificationRoute.OFFICIAL_READY,
            ChatNotificationRoutePolicy.route(DshUiMode.NATIVE, true, "接口失败", officialWebConnected = true)
        )
    }

    @Test fun nativeFailureKeepsNotificationTargetForOfficialRoute() {
        // 冷启动原生请求尚未选中目标时，下一步应是请求原生。
        assertEquals(
            ChatNotificationDecision.REQUEST_NATIVE,
            ChatNotificationTargetPolicy.decide(
                "B", ChatNotificationRoute.NATIVE, nativeSelectedId = null, nativeLoading = true,
                nativeUnavailable = false, nativeRequestIssued = false
            )
        )
        // 原生列表失败：保留 pending，等待回退，而不是消费目标。
        assertEquals(
            ChatNotificationDecision.WAIT_FOR_FALLBACK,
            ChatNotificationTargetPolicy.decide(
                "B", ChatNotificationRoute.NATIVE, nativeSelectedId = null, nativeLoading = false,
                nativeUnavailable = true, nativeRequestIssued = false
            )
        )
        // 回退后 WebView 尚未连接时继续等待；连接成功后转交官方路由。
        assertEquals(
            ChatNotificationDecision.WAIT_FOR_WEB,
            ChatNotificationTargetPolicy.decide(
                "B", ChatNotificationRoute.OFFICIAL_WAIT, nativeSelectedId = null, nativeLoading = false,
                nativeUnavailable = true, nativeRequestIssued = false
            )
        )
        assertEquals(
            ChatNotificationDecision.OPEN_OFFICIAL,
            ChatNotificationTargetPolicy.decide(
                "B", ChatNotificationRoute.OFFICIAL_READY, nativeSelectedId = null, nativeLoading = false,
                nativeUnavailable = true, nativeRequestIssued = false
            )
        )
        // 已选中 B 但本次通知尚未发起刷新时，不能直接消费，必须请求强制重载。
        assertEquals(
            ChatNotificationDecision.REQUEST_NATIVE,
            ChatNotificationTargetPolicy.decide(
                "B", ChatNotificationRoute.NATIVE, nativeSelectedId = "B", nativeLoading = false,
                nativeUnavailable = false, nativeRequestIssued = false
            )
        )
        // 本次通知已发起并完成刷新后才允许消费。
        assertEquals(
            ChatNotificationDecision.CONSUMED,
            ChatNotificationTargetPolicy.decide(
                "B", ChatNotificationRoute.NATIVE, nativeSelectedId = "B", nativeLoading = false,
                nativeUnavailable = false, nativeRequestIssued = true
            )
        )
    }

    @Test fun endpointDescriptionNeverPrintsLaunchToken() {
        val text = DshEndpoint("http://127.0.0.1:43210", "http://127.0.0.1:43210/?token=private-token").toString()
        assertFalse(text.contains("private-token"))
        assertTrue(text.contains("<redacted>"))
    }

    @Test fun requestEnvelopeKeepsOfficialMethodAndArgsShape() {
        val args = JSONObject().put("_request", JSONObject())
        val body = DshApiProtocol.requestBody("rpc-1", "session/list", args)
        val root = JSONObject(body)
        assertEquals("client-request", root.getString("type"))
        assertEquals("rpc-1", root.getString("rpcId"))
        assertEquals("session/list", root.getString("method"))
        assertTrue(root.getJSONObject("payload").getJSONObject("args").has("_request"))
    }
}
