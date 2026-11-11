package com.tanuki.lan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.net.Inet4Address
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 页面可见时使用系统 NSD；所有回调回到主线程，避免发现、解析和退出互相覆盖。 */
class LanDiscoveryController(context: Context) {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(NsdManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(LanDiscoveryState())
    val state: StateFlow<LanDiscoveryState> = _state.asStateFlow()
    private var active = false
    private var stoppingDiscovery = false
    private var listener: NsdManager.DiscoveryListener? = null
    private var resolver: NsdManager.ResolveListener? = null
    private val queue = LanResolveQueue<NsdServiceInfo>()
    private var multicastLock: WifiManager.MulticastLock? = null

    private val finishScan = Runnable {
        if (active) _state.update {
            it.copy(scanning = false, hosts = LanDiscoveryList.finishScan(it.hosts, now()))
        }
    }
    private val nextCycle = Runnable { if (active) refresh() }

    /** 仅在远程页前台可见时调用；重复进入不会重复注册。 */
    fun start() {
        if (active) return
        if (!hasPermission(appContext)) {
            _state.update { it.copy(permissionDenied = true, scanning = false) }
            return
        }
        active = true
        queue.start()
        acquireMulticastLock()
        refresh()
    }

    /** 退出立即停止定时器和多播；旧系统无法取消的解析只允许回收，不允许更新页面。 */
    fun stop() {
        active = false
        queue.stop()
        handler.removeCallbacks(nextCycle)
        handler.removeCallbacks(finishScan)
        stopDiscovery()
        if (Build.VERSION.SDK_INT >= 34) resolver?.let { runCatching { nsd?.stopServiceResolution(it) } }
        runCatching { multicastLock?.release() }
        multicastLock = null
        _state.update { it.copy(scanning = false, hosts = it.hosts.map { host -> host.copy(online = false) }) }
    }

    /** 每五秒重扫或用户下拉刷新；保留上一轮状态，避免周期性离线闪动。 */
    fun refresh() {
        if (!active) return
        if (!hasPermission(appContext)) {
            stop()
            _state.update { it.copy(permissionDenied = true) }
            return
        }
        _state.update {
            it.copy(scanning = true, error = "", permissionDenied = false,
                hosts = LanDiscoveryList.finishScan(it.hosts, now()), lastScanAtMillis = now())
        }
        handler.removeCallbacks(finishScan)
        handler.postDelayed(finishScan, 2_000L)
        handler.removeCallbacks(nextCycle)
        handler.postDelayed(nextCycle, 5_000L)
        if (listener == null) startDiscovery() else stopDiscovery()
    }

    private fun startDiscovery() {
        if (!active || listener != null) return
        val manager = nsd ?: return fail()
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onServiceFound(info: NsdServiceInfo) { handler.post {
                if (active && listener === this && !stoppingDiscovery) {
                    queue.offer(info.serviceName, info)
                    resolveNext()
                }
            } }
            override fun onServiceLost(info: NsdServiceInfo) { handler.post {
                if (active && listener === this && !stoppingDiscovery) {
                    queue.remove(info.serviceName)
                    _state.update { it.copy(hosts = LanDiscoveryList.lost(it.hosts, info.serviceName, now())) }
                }
            } }
            override fun onDiscoveryStopped(serviceType: String) { handler.post { stopped(this) } }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { handler.post {
                // 系统可能仍保留监听；不要再创建另一个监听。下一轮可再次尝试停止。
                if (listener === this) {
                    stoppingDiscovery = false
                    fail()
                }
            } }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { handler.post {
                if (listener === this) {
                    listener = null
                    stoppingDiscovery = false
                    fail()
                }
            } }
        }
        listener = discovery
        try {
            manager.discoverServices(LanDiscoveryTxt.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        } catch (_: SecurityException) {
            listener = null
            stop()
            _state.update { it.copy(permissionDenied = true) }
        } catch (_: RuntimeException) {
            listener = null
            fail()
        }
    }

    private fun stopDiscovery() {
        val current = listener ?: return
        if (stoppingDiscovery) return
        stoppingDiscovery = true
        try {
            nsd?.stopServiceDiscovery(current)
        } catch (_: IllegalArgumentException) {
            // 未成功注册的监听无需等待停止回调；重启放在下一轮，避免即时失败循环。
            listener = null
            stoppingDiscovery = false
        }
    }

    private fun stopped(current: NsdManager.DiscoveryListener) {
        if (listener !== current) return
        listener = null
        stoppingDiscovery = false
        if (active) startDiscovery()
    }

    @Suppress("DEPRECATION") // API 26 起支持；串行队列兼容旧系统 resolve 限制。
    private fun resolveNext() {
        if (!active) return
        val request = queue.next() ?: return
        val manager = nsd ?: return
        val callback = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) { handler.post { complete(null) } }
            override fun onResolutionStopped(info: NsdServiceInfo) { handler.post { complete(null) } }
            override fun onStopResolutionFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceResolved(info: NsdServiceInfo) { handler.post { complete(info) } }
            private fun complete(info: NsdServiceInfo?) {
                val eligible = queue.complete(request)
                if (resolver === this) resolver = null
                if (eligible && active && info != null) {
                    val address = if (Build.VERSION.SDK_INT >= 34) {
                        info.hostAddresses.firstOrNull { it is Inet4Address } ?: info.hostAddresses.firstOrNull()
                    } else info.host
                    LanDiscoveryTxt.parse(info.serviceName, info.attributes, address?.hostAddress, now())?.let { host ->
                        _state.update { it.copy(hosts = LanDiscoveryList.merge(it.hosts, host, now())) }
                    }
                }
                resolveNext()
            }
        }
        resolver = callback
        try {
            manager.resolveService(request.value, callback)
        } catch (_: SecurityException) {
            queue.complete(request)
            resolver = null
            stop()
            _state.update { it.copy(permissionDenied = true) }
        } catch (_: RuntimeException) {
            queue.complete(request)
            resolver = null
            handler.post { resolveNext() }
        }
    }

    private fun fail() {
        _state.update { it.copy(scanning = false, error = "暂时找不到局域网电脑，可以重新扫描或扫码、粘贴连接。") }
    }

    private fun acquireMulticastLock() {
        val wifi = appContext.getSystemService(WifiManager::class.java) ?: return
        multicastLock = runCatching {
            wifi.createMulticastLock("tanuki-lan-discovery").apply { setReferenceCounted(false); acquire() }
        }.getOrNull()
    }

    private fun now() = SystemClock.elapsedRealtime()

    companion object {
        fun hasPermission(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED
    }
}

/** 持久保存是否已经询问；在发起请求前记下，拒绝或进程重建后均不反复弹窗。 */
class LanDiscoveryPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("tanuki_lan_discovery", Context.MODE_PRIVATE)
    fun permissionAsked() = preferences.getBoolean("permission_asked", false)
    fun markPermissionAsked() { preferences.edit().putBoolean("permission_asked", true).apply() }
}
