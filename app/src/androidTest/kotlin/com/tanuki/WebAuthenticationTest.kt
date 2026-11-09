package com.tanuki

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.webkit.CookieManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.tanuki.runtime.*
import com.tanuki.web.DshWebView
import com.tanuki.web.WebViewSupportState
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Local HTTP fixture only: no DSH models, user profiles, or external network. */
class WebAuthenticationTest {
    private class Fixture(base: Context, private val directory: File) : ContextWrapper(base) {
        override fun getFilesDir() = directory
        override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
    }

    private class Server : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val origin = "http://127.0.0.1:${socket.localPort}"
        val cookieName = "hp_test_${socket.localPort}"
        val token = AtomicReference("first-fixture-token")
        val acceptToken = AtomicBoolean(true)
        val exchanges = AtomicInteger()
        val requests = java.util.concurrent.CopyOnWriteArrayList<String>()
        @Volatile var page = "<!doctype html><title>Fixture authenticated</title><p>Ready</p>"
        private val worker = thread(isDaemon = true, name = "hp-auth-fixture") {
            while (!socket.isClosed) {
                try {
                    socket.accept().use { client ->
                        client.soTimeout = 2000
                        val input = client.getInputStream().bufferedReader()
                        val target = input.readLine()?.split(' ')?.getOrNull(1) ?: return@use
                        val headers = HashMap<String, String>()
                        for (i in 0..100) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                            if (':' in line) headers[line.substringBefore(':').lowercase()] = line.substringAfter(':').trim()
                        }
                        var extra = ""
                        val status = if (target.startsWith("/?token=")) {
                            exchanges.incrementAndGet()
                            if (acceptToken.get() && target.substringAfter("token=").substringBefore('&') == token.get()) {
                                extra = "Location: /\r\nSet-Cookie: $cookieName=${token.get()}; Path=/; HttpOnly; SameSite=Strict\r\n"
                                302
                            } else 401
                        } else if (headers["cookie"].orEmpty().split(';').any { it.trim() == "$cookieName=${token.get()}" }) 200 else 401
                        requests += "${if(target.startsWith("/?token=")) "bootstrap" else "page"}:$status"
                        val body = if (status == 200) page else "Authentication required"
                        val bytes = body.toByteArray(Charsets.UTF_8)
                        val head = "HTTP/1.1 $status Fixture\r\nContent-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n$extra\r\n"
                        client.getOutputStream().apply { write(head.toByteArray(Charsets.US_ASCII)); write(bytes); flush() }
                    }
                } catch (_: java.io.IOException) { /* Closing the fixture or a browser socket is expected. */ }
            }
        }
        override fun close() { socket.close(); worker.join(2500) }
    }

    @Test fun externalLinksAndWindowOpenUseNewBrowserPagesWithoutLeavingDsh(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".workspacecheck"))
        val directory = File(context.cacheDir, "dsh-links-${System.nanoTime()}")
        val manager = RuntimeManager(Fixture(context, directory))
        val pages = java.util.concurrent.CopyOnWriteArrayList<String>()
        val server = Server()
        val public = BrowserFixtureServer()
        server.page = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head><body><script>window.epoch=performance.timeOrigin;</script><a id=link href="${public.base}/ordinary?code=public-oauth" style="display:block;padding:24px">Open login</a><button id=popup onclick="window.open('${public.base}/popup?code=public-oauth','_blank')">Login popup</button></body></html>"""
        manager.attachLocalEndpointForTesting(server.origin, "${server.origin}/?token=${server.token.get()}")
        manager.state.value = manager.state.value.copy(environment = EnvironmentState.READY, service = ServiceState.RUNNING)
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        lateinit var browser: DshWebView
        var created = false
        fun ui(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun js(source: String): String {
            val latch = CountDownLatch(1); var result = "null"
            ui { browser.view.evaluateJavascript(source) { result = it; latch.countDown() } }
            assertTrue(latch.await(5, TimeUnit.SECONDS)); return result
        }
        suspend fun await(message: String = "管理页连接", condition: () -> Boolean) {
            try { withTimeout(20000) { while (!condition()) delay(25) } }
            catch (_: TimeoutCancellationException) { fail("$message 超时；forwarded=${pages.size}; web=${manager.state.value.web}") }
        }
        fun diagnostic(stage: String, tapX: Float? = null, tapY: Float? = null): String {
            val result = org.json.JSONObject().put("stage", stage).put("forwarded", pages.size)
                .put("web", manager.state.value.web.name)
            ui {
                val location = IntArray(2); browser.view.getLocationOnScreen(location)
                val rootLocation = IntArray(2); activity.window.decorView.getLocationOnScreen(rootLocation)
                val visible = android.graphics.Rect(); browser.view.getGlobalVisibleRect(visible)
                val bars = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.getInsets(WindowInsetsCompat.Type.systemBars())
                val safe = android.graphics.Rect(rootLocation[0] + (bars?.left ?: 0), rootLocation[1] + (bars?.top ?: 0),
                    rootLocation[0] + activity.window.decorView.width - (bars?.right ?: 0),
                    rootLocation[1] + activity.window.decorView.height - (bars?.bottom ?: 0))
                result.put("shown", browser.view.isShown).put("windowFocus", browser.view.hasWindowFocus())
                    .put("viewLocation", org.json.JSONArray(location.toList()))
                    .put("viewSize", org.json.JSONArray(listOf(browser.view.width, browser.view.height)))
                    .put("globalVisibleBounds", visible.toShortString())
                    .put("systemBarsSafeBounds", safe.toShortString())
                    .put("systemBarsInsets", org.json.JSONArray(listOf(bars?.left ?: 0, bars?.top ?: 0, bars?.right ?: 0, bars?.bottom ?: 0)))
                @Suppress("DEPRECATION") val scale = browser.view.scale
                result.put("deprecatedWebViewScale", scale)
                if (tapX != null && tapY != null) result.put("screenTap", org.json.JSONArray(listOf(tapX, tapY)))
                    .put("tapInsideSystemBarsSafeBounds", safe.contains(tapX.toInt(), tapY.toInt()))
            }
            // 仅记录合成页面的几何与事件，不读取URL、Cookie、启动token或网页存储。
            val dom = js("""JSON.stringify((()=>{
                const r=document.querySelector('#link').getBoundingClientRect(),v=window.visualViewport;
                const cx=r.x+r.width/2,cy=r.y+r.height/2,e=document.elementFromPoint(cx,cy);
                return {readyState:document.readyState,linkRect:[r.x,r.y,r.width,r.height],
                    linkCenter:[cx,cy],elementAtLinkCenter:e?.id||e?.tagName,
                    innerWidth,innerHeight,visualViewport:v?{width:v.width,height:v.height,scale:v.scale,offsetLeft:v.offsetLeft,offsetTop:v.offsetTop}:null,
                    viewportMeta:document.querySelector('meta[name="viewport"]')?.content,
                    events:window.__authLinkEvents||[],activation:{isActive:navigator.userActivation?.isActive,hasBeenActive:navigator.userActivation?.hasBeenActive}};
            })())""")
            result.put("dom", org.json.JSONObject(org.json.JSONTokener(dom).nextValue() as String))
            val text = result.toString()
            File(context.cacheDir, "auth-external-$stage.json").writeText(text)
            runCatching {
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                try { File(context.cacheDir, "auth-external-$stage.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                } } finally { bitmap.recycle() }
            }
            android.util.Log.i("TANUKI_PROBE", "AUTH_LINK_DIAG $text")
            return text
        }
        suspend fun awaitVisibleFrame() {
            await("合成外链页可见并取得窗口焦点") {
                var ready = false
                ui { ready = browser.view.isShown && browser.view.hasWindowFocus() && browser.view.width > 0 && browser.view.height > 0 }
                ready
            }
            val frame = CountDownLatch(1)
            ui { browser.view.postVisualStateCallback(1, object : android.webkit.WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) { frame.countDown() }
            }) }
            assertTrue("合成外链页完成首帧绘制", frame.await(5, TimeUnit.SECONDS))
        }
        try {
            ui {
                browser = DshWebView(activity, manager, { _, callback -> callback.onReceiveValue(null) }, { _, _ -> }, { file, _ -> file.delete() }, { pages.add(it) })
                created = true
                activity.setContentView(browser.view); browser.checkSupport(connectAfter = true)
            }
            await { manager.state.value.web == WebState.CONNECTED }
            awaitVisibleFrame()
            js("""window.__authLinkEvents=[];['pointerdown','pointerup','click'].forEach(type=>document.addEventListener(type,event=>{
                const events=window.__authLinkEvents;events.push({type,target:event.target?.id||event.target?.tagName,
                    trusted:event.isTrusted,x:event.clientX,y:event.clientY,active:navigator.userActivation?.isActive});
                if(events.length>20)events.shift();
            },true));true""")
            // 先留存裸WebView的原几何：edge-to-edge下链接中心可能落在系统状态栏里。
            val originalPosition = org.json.JSONArray(org.json.JSONTokener(js("JSON.stringify((()=>{const r=document.querySelector('#link').getBoundingClientRect();return [r.x+r.width/2,r.y+r.height/2]})())")).nextValue() as String)
            var originalX = 0f; var originalY = 0f
            ui {
                val location = IntArray(2); browser.view.getLocationOnScreen(location)
                @Suppress("DEPRECATION") val scale = browser.view.scale
                originalX = location[0] + originalPosition.getDouble(0).toFloat() * scale
                originalY = location[1] + originalPosition.getDouble(1).toFloat() * scale
            }
            diagnostic("bare-host", originalX, originalY)
            val epoch = js("performance.timeOrigin")
            // 测试用裸宿主没有Compose消费系统栏，这里恢复夹具自身安全布局；不改MainActivity窗口策略。
            lateinit var host: FrameLayout
            var hostInsetsApplied = false
            ui {
                (browser.view.parent as? ViewGroup)?.removeView(browser.view)
                host = FrameLayout(activity).apply { clipChildren = true; clipToPadding = true }
                host.addView(browser.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                ViewCompat.setOnApplyWindowInsetsListener(host) { _, insets ->
                    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    host.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                    hostInsetsApplied = true
                    insets
                }
                activity.setContentView(host)
                ViewCompat.requestApplyInsets(host)
            }
            await("外链测试宿主系统栏避让") {
                var applied = false
                ui {
                    val bars = ViewCompat.getRootWindowInsets(host)?.getInsets(WindowInsetsCompat.Type.systemBars())
                    applied = hostInsetsApplied && bars != null && host.paddingLeft == bars.left && host.paddingTop == bars.top &&
                        host.paddingRight == bars.right && host.paddingBottom == bars.bottom
                }
                applied
            }
            awaitVisibleFrame()
            val position = org.json.JSONArray(js("JSON.stringify((function(){const r=document.querySelector('#link').getBoundingClientRect();return [r.x+r.width/2,r.y+r.height/2];})())").let { org.json.JSONTokener(it).nextValue() as String })
            var x = 0f; var y = 0f
            val viewportWidth = js("window.innerWidth").toFloat()
            ui {
                val location = IntArray(2); browser.view.getLocationOnScreen(location)
                val scale = browser.view.width / viewportWidth
                x = location[0] + position.getDouble(0).toFloat() * scale
                y = location[1] + position.getDouble(1).toFloat() * scale
            }
            val beforeTap = org.json.JSONObject(diagnostic("before-tap", x, y))
            assertTrue("合成外链中心必须位于系统栏之外的可点击区域", beforeTap.getBoolean("tapInsideSystemBarsSafeBounds"))
            val now = android.os.SystemClock.uptimeMillis()
            for ((action, time) in listOf(android.view.MotionEvent.ACTION_DOWN to now, android.view.MotionEvent.ACTION_UP to now + 80)) {
                val event = android.view.MotionEvent.obtain(now,time,action,x,y,0)
                event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event,true)) } finally { event.recycle() }
            }
            try { await("普通外链") { pages.size == 1 } }
            catch (error: AssertionError) {
                val details = runCatching { diagnostic("failure", x, y) }.getOrElse { "diagnostic=${it.javaClass.simpleName}" }
                throw AssertionError("$details; ${error.message}", error)
            }
            diagnostic("after-tap", x, y)
            assertEquals(public.base + "/ordinary?code=public-oauth", pages[0])
            js("document.querySelector('#popup').click();true")
            await("直接弹窗") { pages.size == 2 }
            assertEquals(public.base + "/popup?code=public-oauth", pages[1])
            js("setTimeout(()=>window.open('${public.base}/delayed?code=public-oauth','_blank'),20);true")
            await("异步外链弹窗") { pages.size == 3 }
            assertEquals(public.base + "/delayed?code=public-oauth", pages[2])
            js("window.open('${server.origin}/?token=not-for-browser','_blank');true")
            delay(300); assertEquals("Never export DSH bootstrap links", 3, pages.size)
            // 官方先预开空白弹窗、异步初始化后再赋值；13 秒超过旧弹窗存活上限。
            val official = "https://platform.deepseek.com/dsh/authorize?client_id=synthetic-native-test"
            js("window.oauthFixture=window.open('','_blank');setTimeout(()=>window.oauthFixture.location.assign('$official'),13000);true")
            await("预开空白官方授权弹窗") { pages.size == 4 }
            assertEquals(official, pages[3])
            // 官方主框架也可能在异步完成后跳转，不依赖已经失效的用户手势标志。
            js("setTimeout(()=>window.location.assign('$official'),20);true")
            await("主框架异步官方授权") { pages.size == 5 }
            assertEquals(official, pages[4])
            assertEquals(epoch, js("performance.timeOrigin"))
            assertEquals("No external content may load in the credential profile", 0, public.requests.get())
        } finally {
            ui { if (created) browser.destroy(); activity.finish() }
            manager.scope.cancel(); manager.scope.coroutineContext[Job]?.join()
            public.close(); server.close(); RuntimeManager.deleteTree(directory)
        }
    }

    @Test fun cookieRecoveryAndSameOriginTokenChangeAreBounded(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".workspacecheck"))
        val directory = File(context.cacheDir, "web-auth-${System.nanoTime()}")
        val manager = RuntimeManager(Fixture(context, directory))
        val server = Server()
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var browser: DshWebView? = null
        fun setTarget() {
            manager.attachLocalEndpointForTesting(server.origin, "${server.origin}/?token=${server.token.get()}")
        }
        suspend fun await(message: String, predicate: () -> Boolean) {
            try { withTimeout(15000) { while (!predicate()) delay(25) } }
            catch (_: TimeoutCancellationException) { fail("$message; state=${manager.state.value.web}; exchanges=${server.exchanges.get()}; requests=${server.requests}") }
        }
        fun clearFixtureCookie() {
            val done = CountDownLatch(1)
            instrumentation.runOnMainSync {
                CookieManager.getInstance().setCookie(server.origin, "${server.cookieName}=; Path=/; Max-Age=0") { done.countDown() }
            }
            assertTrue("fixture cookie removal", done.await(5, TimeUnit.SECONDS))
        }
        try {
            setTarget()
            manager.state.value = manager.state.value.copy(environment = EnvironmentState.READY, service = ServiceState.RUNNING)
            instrumentation.runOnMainSync {
                browser = DshWebView(activity, manager, { _, callback -> callback.onReceiveValue(null) }, { _, _ -> }, { file, _ -> file.delete() })
                activity.setContentView(browser!!.view)
                browser!!.checkSupport(connectAfter = true)
            }
            await("initial authenticated page") { browser!!.support.value.state == WebViewSupportState.SUPPORTED && manager.state.value.web == WebState.CONNECTED }
            assertEquals(1, server.exchanges.get())

            clearFixtureCookie()
            instrumentation.runOnMainSync { browser!!.view.loadUrl(server.origin + "/") }
            await("401 must exchange the current launch URL once") { server.exchanges.get() == 2 && manager.state.value.web == WebState.CONNECTED }

            server.token.set("second-fixture-token")
            setTarget()
            instrumentation.runOnMainSync { browser!!.connect() }
            await("changed token at the same origin must reload") { server.exchanges.get() == 3 && manager.state.value.web == WebState.CONNECTED }

            server.acceptToken.set(false)
            server.token.set("rejected-fixture-token")
            setTarget()
            instrumentation.runOnMainSync { browser!!.connect() }
            await("invalid token must settle as expired") { server.exchanges.get() >= 5 && manager.state.value.web == WebState.AUTH_EXPIRED }
            delay(500)
            assertEquals("initial request plus exactly one retry", 5, server.exchanges.get())
            android.util.Log.i("TANUKI_PROBE", "WEB_AUTH cookie_recovery=true same_origin_token_change=true invalid_token_bounded=true")
        } finally {
            clearFixtureCookie()
            instrumentation.runOnMainSync { browser?.destroy(); activity.finish() }
            manager.scope.cancel(); manager.scope.coroutineContext[Job]?.join()
            server.close()
            RuntimeManager.deleteTree(directory)
        }
    }
}
