package com.tanuki.runtime

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 管理 WebView 的一份 Cookie 快照。
 *
 * CookieManager 没有公开的 Cookie 枚举 API，只能读取当前 loopback origin 的 Cookie header；
 * 还原时统一使用 Path=/ 和 HttpOnly。认证 Cookie 不在页面脚本中读取，按 HttpOnly 还原更保守。
 */
data class DshCookie(val name: String, val value: String) {
    fun headerValue(): String = "$name=$value"

    fun setCookieValue(): String = "$name=$value; Path=/; HttpOnly; SameSite=Strict"
}

/** 纯 Kotlin 的逻辑主机 Cookie 分区，JVM 可回归，不接触 Android CookieManager。 */
class DshCookieJar {
    private val jars = LinkedHashMap<String, List<DshCookie>>()

    @Synchronized
    fun save(scope: String, cookies: List<DshCookie>) {
        jars[scope] = cookies.toList()
    }

    @Synchronized
    fun load(scope: String): List<DshCookie> = jars[scope].orEmpty()

    @Synchronized
    fun clear() {
        jars.clear()
    }
}

/**
 * 在切换本地/远程 DSH 前交换管理 WebView 的 Cookie 集合。
 *
 * CookieManager 是进程级单例；端口不是 Cookie 作用域，仅靠 origin 精确匹配不能阻止
 * 127.0.0.1:portA 的 Cookie 发给 127.0.0.1:portB。这里按逻辑主机保存 Cookie header，
 * 切换时抓取当前作用域、清空 CookieManager，再恢复目标作用域。
 * 必须在 IO 线程调用（removeAllCookies 回调需要主线程）。
 */
class DshCookieIsolation(
    private val cookieManager: CookieManager = CookieManager.getInstance(),
    private val scopeStore: KeyValueStore? = null
) {
    private val jar = DshCookieJar()
    private val mainHandler = Handler(Looper.getMainLooper())
    // 写入用独立串行锁；UI 读取只读 volatile，不能等待主线程删除回调。
    private val switchGate = Any()
    @Volatile private var activeScope: String = scopeStore?.getString(KEY_SCOPE) ?: LOCAL_SCOPE

    fun switchTo(scope: String) {
        synchronized(switchGate) {
            if (scope == activeScope) return
            if (Looper.myLooper() == Looper.getMainLooper()) {
                throw IllegalStateException("Cookie 隔离不能在主线程执行")
            }
            capture(activeScope)
            if (!clearAll()) throw IllegalStateException("无法清理管理 WebView Cookie，已取消切换")
            restore(scope)
            activeScope = scope
            scopeStore?.putString(KEY_SCOPE, scope)
        }
    }

    /** 主线程每次重组都会读取；只读 volatile，不能拿写锁。 */
    fun currentScope(): String = activeScope

    private fun capture(scope: String) {
        // Cookie 的端口不参与作用域匹配，读取 loopback 根路径可取得当前管理主机的认证 Cookie。
        val header = cookieManager.getCookie("http://127.0.0.1/").orEmpty()
        val cookies = header.split(';').mapNotNull { item ->
            val text = item.trim()
            val separator = text.indexOf('=')
            if (separator <= 0) null else DshCookie(text.substring(0, separator).trim(), text.substring(separator + 1).trim())
        }
        jar.save(scope, cookies)
    }

    private fun restore(scope: String) {
        val cookies = jar.load(scope)
        for (cookie in cookies) {
            cookieManager.setCookie("http://127.0.0.1/", cookie.setCookieValue())
        }
        cookieManager.flush()
    }

    private fun clearAll(): Boolean {
        // removeAllCookies 的回调绑定调用线程的 Looper；IO 线程没有 Looper 会直接抛异常。
        // 固定从主线程发起删除，IO 调用方等待回调后再 flush，主线程本身不阻塞。
        val latch = CountDownLatch(1)
        var completed = false
        mainHandler.post {
            cookieManager.removeAllCookies {
                completed = true
                latch.countDown()
            }
        }
        if (!latch.await(3, TimeUnit.SECONDS)) return false
        cookieManager.flush()
        return completed
    }

    companion object {
        const val LOCAL_SCOPE = "local"
        private const val KEY_SCOPE = "active-scope"
    }
}
