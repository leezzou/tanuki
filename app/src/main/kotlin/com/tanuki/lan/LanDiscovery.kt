package com.tanuki.lan

import com.tanuki.runtime.RemoteHostAddress
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * 手机在同一局域网里发现的一台电脑。
 *
 * 这些字段全部来自 mDNS 广播，只用于“看到并点选”，不是认证凭据：
 * - [hostId] 是电脑端稳定标识，与配对链接中的 host 相同；
 * - [fingerprint] 只是 hostId 的短摘要，用于界面人工对照；
 * - 真正的信任仍由现有 SSH 主机密钥确认和登录认证建立。
 */
data class LanDiscoveredHost(
    val serviceName: String,
    val name: String,
    val sshPort: Int,
    val dshPort: Int,
    val hostId: String,
    val protocolVersion: Int,
    val fingerprint: String,
    val address: String,
    val online: Boolean = true,
    val lastSeenMillis: Long = 0L
) {
    /** 同一台电脑可能被重复广播，hostId 是稳定去重键。 */
    val identity: String get() = hostId.lowercase(Locale.ROOT)

    /** 没有解析到地址时不能直接发起 SSH；界面据此禁用点选。 */
    val connectable: Boolean get() = address.isNotBlank()
}

/** 发现列表的纯状态；Android 控制器只负责把 NsdManager 回调转成这些变更。 */
data class LanDiscoveryState(
    val hosts: List<LanDiscoveredHost> = emptyList(),
    val scanning: Boolean = false,
    val error: String = "",
    val permissionDenied: Boolean = false,
    val lastScanAtMillis: Long = 0L
)

/**
 * TXT 契约解析。
 *
 * 主机端广播的键固定为 name/ssh/dsh/host/ver/fp；本解析器只读取这六个键，
 * 即使同网段有人塞入 token、room 或凭据字段，也不会被带进界面或连接配置。
 */
object LanDiscoveryTxt {
    /** NsdManager 的服务类型不带末尾点；与主机端 `_tanuki-remote._tcp.local` 对应。 */
    const val SERVICE_TYPE = "_tanuki-remote._tcp"

    /** NsdManager 在部分机型上把实例名拼上完整服务后缀，解析前去掉。 */
    private const val SERVICE_SUFFIX = "._tanuki-remote._tcp.local"

    private val HOST_ID = Regex("^[0-9a-f]{32}$")
    private val FINGERPRINT = Regex("^[0-9a-f]{16}$")

    /**
     * 解析一次服务解析结果；字段缺失或非法时返回 null。
     *
     * [serviceName] 只在 TXT 没有可用 name 时作为显示名兜底；
     * [address] 来自 SRV/A 解析，回环地址不采用，避免把手机自己当成电脑。
     */
    fun parse(
        serviceName: String,
        attributes: Map<String, ByteArray>,
        address: String?,
        now: Long = 0L
    ): LanDiscoveredHost? {
        val keys = listOf("name", "ssh", "dsh", "host", "ver", "fp")
        val txt = mutableMapOf<String, String>()
        for (key in keys) {
            val raw = attributes[key] ?: continue
            if (raw.size > 255) return null
            txt[key] = decode(raw) ?: return null
        }
        val sshPort = txt["ssh"].toPort() ?: return null
        val dshPort = txt["dsh"].toPort() ?: return null
        val hostId = txt["host"]?.trim()?.lowercase(Locale.ROOT) ?: return null
        if (!HOST_ID.matches(hostId)) return null
        val version = txt["ver"]?.trim()?.toIntOrNull() ?: return null
        if (version != 1) return null
        val fingerprint = txt["fp"]?.trim()?.lowercase(Locale.ROOT) ?: return null
        if (!FINGERPRINT.matches(fingerprint)) return null
        val name = sanitizeName(txt["name"].orEmpty().ifBlank { instanceName(serviceName) })
        if (name.isBlank()) return null
        return LanDiscoveredHost(
            serviceName = serviceName,
            name = name,
            sshPort = sshPort,
            dshPort = dshPort,
            hostId = hostId,
            protocolVersion = version,
            fingerprint = fingerprint,
            address = usableAddress(address),
            online = true,
            lastSeenMillis = now
        )
    }

    /** 非法 UTF-8 不用替换字符悄悄接受，避免显示名与电脑上的名称不一致。 */
    private fun decode(value: ByteArray): String? = runCatching {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value)).toString().trim()
    }.getOrNull()

    private fun String?.toPort(): Int? {
        val value = this?.trim()?.toIntOrNull() ?: return null
        return value.takeIf { it in 1..65535 }
    }

    /** 实例名兜底：去掉完整服务后缀，避免列表出现 `电脑._tanuki-remote._tcp.local`。 */
    private fun instanceName(serviceName: String): String {
        val trimmed = serviceName.trim()
        val cut = trimmed.indexOf(SERVICE_SUFFIX, ignoreCase = true)
        return if (cut > 0) trimmed.substring(0, cut) else trimmed
    }

    /** 只采用可直接用于既有 SSH 配置的数字地址；不做 DNS 查询，不接受回环/链路本地/组播。 */
    private fun usableAddress(raw: String?): String {
        val value = raw?.trim().orEmpty()
        val normalized = RemoteHostAddress.normalize(value) ?: return ""
        if (':' in normalized) {
            // 规范化后只含数字、十六进制和冒号；这里只解析 IPv6 字面量，不会发起 DNS。
            val address = runCatching { InetAddress.getByName(normalized) }.getOrNull() ?: return ""
            if (address.isLoopbackAddress || address.isAnyLocalAddress || address.isLinkLocalAddress || address.isMulticastAddress) return ""
        } else {
            val octets = normalized.split('.').map { it.toIntOrNull() ?: return "" }
            if (octets.size != 4 || octets.any { it !in 0..255 }) return ""
            if (octets[0] == 0 || octets[0] == 127 || octets[0] >= 224 || (octets[0] == 169 && octets[1] == 254)) return ""
        }
        return normalized
    }

    /** 清洗显示名：控制字符变空格，点/斜杠变连字符，合并空白并限制 63 字节。 */
    fun sanitizeName(raw: String): String {
        val chars = raw.map { char ->
            when {
                char == '.' || char == '。' || char == '/' || char == '\\' -> '-'
                char.isISOControl() -> ' '
                else -> char
            }
        }.joinToString("")
        val collapsed = chars.trim().replace(Regex("\\s+"), " ")
        if (collapsed.toByteArray(Charsets.UTF_8).size <= 63) return collapsed
        val builder = StringBuilder()
        var bytes = 0
        for (codePoint in collapsed.codePoints().toArray()) {
            val next = String(Character.toChars(codePoint))
            bytes += next.toByteArray(Charsets.UTF_8).size
            if (bytes > 63) break
            builder.append(next)
        }
        return builder.toString().trim()
    }
}

/**
 * 发现列表的去重、在线状态和排序。
 *
 * 重新扫描不立即改变在线状态；服务丢失或超过在线有效期才标离线，
 * 离线结果短暂保留后清理，避免每五秒闪烁或把过期结果一直显示为在线。
 */
object LanDiscoveryList {
    const val ONLINE_TTL_MILLIS = 15_000L
    const val OFFLINE_TTL_MILLIS = 30_000L

    /** 同一 hostId 只保留最新一条；新的结果重新标为在线。 */
    fun merge(
        current: List<LanDiscoveredHost>,
        discovered: LanDiscoveredHost,
        now: Long
    ): List<LanDiscoveredHost> {
        val incoming = discovered.copy(online = true, lastSeenMillis = now)
        val merged = current.filterNot { it.identity == incoming.identity } + incoming
        return sort(merged).take(64)
    }

    /** 服务消失时只标离线，等 TTL 到期再移除，避免短暂丢包导致列表跳变。 */
    fun lost(
        current: List<LanDiscoveredHost>,
        serviceName: String,
        now: Long
    ): List<LanDiscoveredHost> = sort(
        current.map {
            if (it.serviceName == serviceName) it.copy(online = false, lastSeenMillis = now) else it
        }
    )

    /** 扫描结束：清理长时间离线条目；在线条目保持原顺序按名称排序。 */
    fun finishScan(current: List<LanDiscoveredHost>, now: Long): List<LanDiscoveredHost> = sort(
        current.filter { now - it.lastSeenMillis <= OFFLINE_TTL_MILLIS }
            .map { it.copy(online = it.online && now - it.lastSeenMillis <= ONLINE_TTL_MILLIS) }
    )

    /** 在线优先；同状态按名称、再按 hostId 排序，保证每次刷新顺序稳定。 */
    fun sort(current: List<LanDiscoveredHost>): List<LanDiscoveredHost> = current.sortedWith(
        compareByDescending<LanDiscoveredHost> { it.online }
            .thenBy { it.name.lowercase(Locale.ROOT) }
            .thenBy { it.hostId }
    )
}

/**
 * Android 13+ 的局域网发现权限策略。
 *
 * 只有首次进入且从未询问过时才发起系统权限请求；用户拒绝后不再自动弹窗，
 * 页面降级为扫码/粘贴，用户仍可主动使用其他方式连接。
 */
object LanDiscoveryPermission {
    /** 与 Manifest 声明保持一致；用字符串常量让 JVM 回归不依赖 Android 框架。 */
    const val PERMISSION = "android.permission.NEARBY_WIFI_DEVICES"

    enum class Decision { ALLOWED, REQUEST, DEGRADED }

    fun required(sdkInt: Int): Boolean = sdkInt >= 33

    fun decide(sdkInt: Int, granted: Boolean, askedBefore: Boolean): Decision = when {
        !required(sdkInt) || granted -> Decision.ALLOWED
        askedBefore -> Decision.DEGRADED
        else -> Decision.REQUEST
    }
}

/** 远程页局域网区块的固定文案；JVM 回归锁定空态和降级提示，防止改版时丢失入口。 */
object LanDiscoveryCopy {
    const val SECTION_TITLE = "局域网发现的电脑"
    const val SCANNING = "正在查找同一 Wi-Fi 下的电脑…"
    const val EMPTY_TITLE = "没有发现同一 Wi-Fi 下的电脑"
    const val EMPTY_DETAIL = "在同一 Wi-Fi 下运行电脑端命令（仓库根目录），并保持终端开着："
    const val HOST_COMMAND = "bash scripts/remote-relay/quickstart.sh"
    const val COPY_COMMAND = "复制命令"
    const val COPIED = "已复制"
    const val RETRY = "重新扫描"
    const val USE_QR = "改用扫码"
    const val USE_MANUAL = "手动填写地址"
    const val PERMISSION_DENIED = "未开启局域网发现权限，已改为扫码或粘贴连接。"
    const val OTHER_TITLE = "其他方式"
    const val OTHER_DETAIL = "不在同一 Wi-Fi，或列表里没有电脑时，扫码或粘贴配对链接。"
    const val SAFETY = "连接时会核对电脑指纹，密码和私钥只加密存在本机"
    const val ONLINE = "在线"
    const val OFFLINE = "离线"
    const val PLATFORM = "平台未提供"
    const val ADDRESS_PENDING = "地址无法直连，可改用扫码"

    fun itemTitle(host: LanDiscoveredHost): String =
        "${host.name} · ${if (host.online) ONLINE else OFFLINE}"

    fun itemSubtitle(host: LanDiscoveredHost): String =
        "$PLATFORM · 协议 v${host.protocolVersion}"
}

/** 串行解析队列；停止后旧请求只回收占位，不能借新一轮页面的可见状态写入结果。 */
internal class LanResolveQueue<T> {
    data class Request<T>(val name: String, val value: T, val epoch: Long, var valid: Boolean = true)
    private var epoch = 0L
    private var active = false
    private val pending = linkedMapOf<String, T>()
    private var inFlight: Request<T>? = null

    fun start() { active = true; epoch++ }
    fun stop() { active = false; epoch++; pending.clear() }
    fun offer(name: String, value: T) {
        if (active && pending.size < 64 && (inFlight?.name != name || inFlight?.valid == false)) pending[name] = value
    }
    fun remove(name: String) {
        pending.remove(name)
        if (inFlight?.name == name) inFlight?.valid = false
    }
    fun next(): Request<T>? {
        if (!active || inFlight != null) return null
        val entry = pending.entries.firstOrNull() ?: return null
        pending.remove(entry.key)
        return Request(entry.key, entry.value, epoch).also { inFlight = it }
    }
    fun complete(request: Request<T>): Boolean {
        if (inFlight !== request) return false
        inFlight = null
        return active && request.epoch == epoch && request.valid
    }
}
