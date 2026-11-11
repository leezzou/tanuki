# 远程主机（直连 SSH 与无公网中继）

本切片让 Tanuki 的管理 WebView 指向另一台机器上的 DSH：在 Android 本机建立 SSH，把远程 DSH 的 loopback 端口转发到本机 loopback，再按新 origin 加载官方页面。

支持两种连接方式：

1. **直连 SSH**：手机能直接访问远程 SSH 端口；零额外基础设施。
2. **TLS 中继配对（无公网 IP）**：主机端 agent 主动向中继注册，手机用一次性配对链接加入；中继只转发 SSH 密文，SSH 仍端到端。方案、资源和降级见 [无公网 IP 远程连接](REMOTE-CONNECT.md)。

WebRTC/TURN、远程 DSH 启停和多台远程主机同时连接尚未实现；命名连接库支持保存多份配置，但同一时间只有一个活动远程连接。

## 1. 适用前提

- 直连模式：远程主机可从 Android 设备直接访问 SSH 端口。
- 中继模式：手机和主机都能访问同一个 TLS 中继端口；主机端运行 `scripts/remote-relay/host_agent.py`，主机只需出站访问中继，不需要公网 IP 或入站端口。
- 局域网发现：手机和主机在同一 Wi-Fi/热点或同一允许组播的 VLAN；路由器未启用 AP/客户端隔离或 mDNS 屏蔽，主机防火墙放行 UDP 5353 和组播 `224.0.0.251`。发现只用于展示，仍需配对 token 和 SSH 主机密钥确认。
- 远程 DSH 已启动，并只监听远程 `127.0.0.1:<port>`；本切片不会远程启动或停止 DSH。
- 远程 SSH 支持密码、RSA 或 ECDSA 公钥认证。Ed25519 私钥/主机密钥需要额外 BouncyCastle，当前版本会明确失败，留待后续。
- 首次连接需要用户核对主机公钥指纹；远程 DSH token 是可选项，按远程实际启动参数填写。

## 2. 使用流程

设置 → 远程主机，页面按「当前主机状态 → 局域网发现的电脑 → 我的连接 → 其他方式」展示。标题只出现在顶栏；无关的本机环境安装进度留在工作台/DSH 页。安全说明为“连接时会核对电脑指纹，密码和私钥只加密存在本机”，技术细节默认收在“了解详情”。

同一 Wi-Fi 下点选发现的电脑，确认后进入已预填地址/端口的局域网向导；首次连接仍核对真实 SSH 指纹。“其他方式”提供实际相机扫码、粘贴配对链接、手动填写地址。扫码结果只在内存中通过现有配对链接解析器，取消/相机权限拒绝可继续粘贴。向导只负责收集配置，真正的 SSH 连接仍由控制器执行。连接正在进行或等待指纹确认时，新增连接入口暂不可用；扫码返回、确认和测试提交会再次检查。控制器未接收测试时会提示稍后重试，另一条连接的结果不会推进当前向导。有效扫码结果或手动输入不会被随后自动读取的无效旧剪贴板覆盖。

1. **选择连接方式**：直连 SSH（公网/域名/IPv6）、配对链接（双方无公网 IP，经 TLS 中继）或局域网（同一 Wi-Fi/热点）。页面会提示“连接远程会断开本地 DSH 页面，本地运行时继续在后台运行”。
2. **填写主机与账号**：主机（IPv4/IPv6/域名，不允许协议、路径或端口）、SSH 端口（1-65535，默认 22）、用户名、远程 DSH 端口（默认 3080）和可选 token。配对模式进入步骤时自动读取剪贴板并填入 `tanuki-relay://` 链接，解析成功后手机直接算出并显示 6 位核对码，用户只需与电脑屏幕对照；手动粘贴和输入码仍作为回退。主机字段只作展示名。6 位码由链接派生，只用于人工核对，不能单独恢复中继地址/房间/token。
3. **选择认证方式**：密码，或私钥文件（系统文件选择器，上限 64 KiB）与私钥口令；也可以粘贴 OpenSSH/PEM 私钥全文。当前支持 RSA/ECDSA，Ed25519 明确提示暂不支持。
4. **核对主机指纹**：首次连接显示 `SHA256:` 指纹和远程核对命令；必须勾选“我已通过独立渠道核对”才能继续。未勾选或指纹不一致时不会信任、不会发送认证信息。已信任主机按 `host:sshPort`（中继为 `relay:<hostId>`）比对，变化直接拒绝。
5. **测试连接**：按 TCP → SSH 握手 → 用户认证 → 端口转发 → DSH 探测依次显示进度。失败时停在实际阶段，给出中文原因、下一步建议和“重试”；主机密钥变化不提供直接重试。测试成功不保留隧道、不切换 origin。
6. **完成**：填写连接名称，选择“仅保存”或“保存并连接”。保存会写入命名连接库；连接会再次执行完整校验并切换管理页 origin。

### 2.1 命名连接与切换

- “我的连接”支持命名、编辑、删除；删除只清理本机配置和该连接独有的主机密钥信任，不影响远程主机和当前已建立的会话。
- 同一时间仍只允许一个活动远程连接；连接新主机会先关闭旧隧道并切换 Cookie 作用域。切换失败保持原远程端点，不把上一台主机的认证 Cookie 交给新端点。
- 卡片始终显示当前是本地、正在连接、远程已连接还是已回退本地，并说明断开/切换会影响哪个页面；本地 DSH 运行时不会被删除或重建。
- “清除当前活动配置与主机密钥”只清理当前活动配置；命名连接库中的其他连接不受影响。

### 2.2 分阶段失败语义

| 阶段 | 典型原因 | 下一步 |
|---|---|---|
| TCP 连接 | 地址/端口不可达、DNS 失败、超时、局域网不同网段、防火墙 | 核对主机、端口和网络；局域网确认同一 Wi-Fi/热点并放行 sshd |
| SSH 握手 | 端口不是 sshd、算法协商失败、主机密钥交换失败 | 在电脑上先执行 `ssh -p <端口> <用户>@<主机>`；确认 RSA/ECDSA 主机密钥 |
| 用户认证 | 用户名、密码、私钥或口令不匹配 | 核对用户名与凭据；私钥需与远程 `authorized_keys` 匹配 |
| 端口转发 | sshd 禁止 TCP 转发或转发目标无效 | 确认远程 DSH 监听 `127.0.0.1:<端口>`，sshd 未禁用 `AllowTcpForwarding` |
| DSH 探测 | 远程 DSH 未启动或端口不对 | 在远程主机启动 DSH，核对远程 DSH 端口 |
| 主机密钥变化 | 主机重装或中间人攻击 | 先独立核对新指纹；确认安全后删除旧连接重新添加，不提供关闭校验的开关 |

### 2.3 中继配对（无公网 IP）

1. 在可访问的主机上运行中继：`relay.py --cert ... --key ...`；启动时打印 `relay_pin=sha256-...`（证书指纹，不是私钥）。
2. 在远程 DSH 主机运行 agent：`host_agent.py --relay <host:port> --cert-pin <pin> --ssh-port 22`。agent 只向中继发起出站 TLS，并连接本机 `127.0.0.1:22`；不监听端口。
3. agent 把 `tanuki-relay://v1?...` 配对链接写入 `~/.tanuki-relay/pairing.txt`（0600），并在启动日志打印 `pairing code: XXXXXX`。把链接与 6 位码一起交给手机；链接含一次性 token，不要截图或公开转发。
4. 手机设置 → 远程主机 → 其他方式 → 粘贴配对链接：进入步骤时自动读取剪贴板中的链接，解析后直接显示 6 位码供与电脑对照，并在确认页核对电脑名、中继地址、证书指纹和 SSH 目标后点“这是你的电脑”；再填写 SSH 用户名/密码或私钥、远程 DSH 端口和可选 token。主机字段可留空（仅作展示名）。手动粘贴链接和输入码保留为回退。
5. 首次连接仍展示远程 SSH 主机密钥 `SHA256:` 指纹，确认后才发送认证信息；之后按 `relay:<hostId>` 比对。中继证书钉扎失败/主机密钥变化都会拒绝，不降级明文。
6. 中继模式与直连模式共用同一个 loopback origin、Cookie 隔离、失败回退本地和“断开远程并切回本地”流程。

### 2.4 局域网发现（手机与电脑）

主机端 quickstart/host_agent 默认广播 `_tanuki-remote._tcp.local`，TXT 带显示名、SSH/DSH 端口、hostId、协议版本和指纹摘要，**不广播 token**。手机与主机需在同一 Wi-Fi/热点，且路由器未启用 AP/客户端隔离或 mDNS 屏蔽；主机防火墙放行 UDP 5353/组播 `224.0.0.251`。`--name` 改显示名，`--dsh-port` 改 TXT 中的 DSH 端口，`--no-mdns` 关闭广播。广播失败只记日志，不影响中继/配对链接。

手机页面可见时立即开始发现，每 5 秒重新扫描；下拉或点“刷新”可立即重扫。离开页面、打开相机或退到后台停止发现并释放多播锁。列表以电脑标识去重、在线优先；最近 15 秒解析成功视为在线，服务消失立即标离线，30 秒后移除。在线表示广播可见，不表示 SSH 登录或 DSH 已经验证。当前 TXT 不含平台或 DSH 版本，列表显示“平台未提供 · 协议 v1”。

Android 13+ 首次申请“附近设备”权限；拒绝后只保留一条说明和扫码/粘贴入口，下拉刷新或再次进页不重弹。可以在系统设置授权后回到本页恢复发现。未发现电脑时展示同一 Wi-Fi 提示、`bash scripts/remote-relay/quickstart.sh`（在电脑仓库根目录运行）、一键复制和“改用扫码”。如果 sshd 不允许局域网入站，发现后仍需改用扫码/粘贴中继链接。

完整契约和“列表看不到 / 能看到但连不上”的排查见 [无公网 IP 远程连接 3.4](REMOTE-CONNECT.md#34-局域网自动发现mdns)。

本地运行时不会被删除或重建。远程连接期间本地 DSH 可以继续运行；切换只改变管理 WebView 的 origin，不会迁移远程会话、项目或凭据到本地。

## 3. 连接与回退

- 首次连接：读取主机公钥 → 展示 `SHA256:` 指纹 → 用户确认 → 发送 SSH 认证。确认前不发送密码/私钥。
- 已信任主机：按 `host:sshPort` 比对指纹；不一致返回“远程主机密钥已变化”并拒绝，不自动信任新密钥。
- 隧道建立：SSH 本地转发绑定 `127.0.0.1` 的系统分配端口；远程 DSH 端口只从远程 loopback 访问。
- 探测：`GET http://127.0.0.1:<本地端口>/` 接受 2xx/3xx/401；失败关闭隧道。
- 回退：认证失败、超时、主机密钥变化、远程端口不可达或隧道断开都会关闭隧道并回到本地端点，显示中文错误；不自动重试、不静默降级明文。
- 远程 token 只拼到本机 loopback 启动 URL，经 SSH 隧道发送；不直接连接远程 DSH 端口，不写日志。

## 4. 安全边界

- 凭据存储：密码、私钥、口令和 token 只经 Android Keystore 的 AES-256-GCM 密钥加密后写入应用私有 SharedPreferences；加密 IV 由 Keystore provider 生成并随密文保存，Keystore 失败时拒绝保存，不回退明文。
- Cookie 隔离：本地 DSH 与每台远程 DSH 分别保存管理页 Cookie header；切换前清空 CookieManager 并恢复目标主机，Cookie 端口不参与作用域，不能只靠 origin 检查。切换失败时页面置 `RENDER_ERROR`，不把上一台主机的认证 Cookie 交给新端点。Cookie 快照按 loopback 根路径读取，恢复为 `Path=/; HttpOnly; SameSite=Strict`。
- 不导出：远程配置不进入项目 ZIP；应用私有快照只包含 DSH userdata，不包含这些 SharedPreferences。`RemoteHostConfig`/`RemoteEndpoint` 的日志字符串固定脱敏。
- 主机密钥：首次确认 + 后续比对；拒绝变化的主机密钥。
- 传输：SSH 加密；管理 WebView 仍只接受精确 `http://127.0.0.1:<本地端口>`，TLS 错误、文件访问和混合内容策略未放宽。
- 限制：这是用户主动配置的 SSH 通道，不是恶意远程主机沙箱；不提供 WebRTC/P2P 打洞、远程进程管理或多主机隔离。

## 5. 实现位置

| 责任 | 文件 |
|---|---|
| 配置校验、token 隧道 URL、主机指纹、origin 状态机 | `app/src/main/kotlin/com/tanuki/runtime/RemoteHost.kt` |
| 配对链接、帧、HMAC、TLS 钉扎、重连策略 | `app/src/main/kotlin/com/tanuki/runtime/RemoteRelay.kt` |
| TLS 中继客户端、loopback 桥、直连/中继路由 | `app/src/main/kotlin/com/tanuki/runtime/RelayTransport.kt` |
| Keystore 加密存储、known_hosts 指纹 | `app/src/main/kotlin/com/tanuki/runtime/RemoteHostStorage.kt` |
| 管理页 Cookie 逻辑主机隔离 | `app/src/main/kotlin/com/tanuki/runtime/DshCookieIsolation.kt` |
| JSch SSH 隧道与 HTTP 探测 | `app/src/main/kotlin/com/tanuki/runtime/SshTransport.kt` |
| 连接流程、首次确认、失败回退 | `app/src/main/kotlin/com/tanuki/runtime/RemoteHostController.kt` |
| RuntimeManager origin 接线 | `app/src/main/kotlin/com/tanuki/runtime/RuntimeManager.kt` |
| 向导状态机、字段校验、步骤/阶段状态 | `app/src/main/kotlin/com/tanuki/runtime/RemoteHostWizard.kt` |
| 阶段化错误映射（原因 + 下一步 + 可重试） | `app/src/main/kotlin/com/tanuki/runtime/RemoteConnectionError.kt` |
| 命名连接库（加密凭据、增删改） | `app/src/main/kotlin/com/tanuki/runtime/RemoteHostProfiles.kt` |
| 设置页、六步向导、文件选择器、连接列表、主机切换 chip/sheet | `app/src/main/kotlin/com/tanuki/ui/RemoteHostSettings.kt` |
| 6 位配对码（与主机端共享向量） | `app/src/main/kotlin/com/tanuki/runtime/RemotePairingCode.kt`、`scripts/remote-relay/protocol.py`、`host_agent.py` |
| JVM 回归 | `app/src/test/kotlin/com/tanuki/RemoteWizardTest.kt`、`RemoteHostTest.kt`、`DshRemoteIsolationTest.kt`、`RemoteRelayTest.kt` |
| Python 参考中继/主机端/自测 | `scripts/remote-relay/` |
| 局域网发现/mDNS 编解码/自测 | `scripts/remote-relay/discovery.py`、`test_mdns.py` |
| 设备回归（仅编译，未运行） | `app/src/androidTest/kotlin/com/tanuki/DshCookieIsolationTest.kt`、`RemoteHostKeystoreTest.kt` |

## 6. 验证与未验证

已执行（JVM）：本轮新增 `RemoteWizardTest` 41 项，覆盖六步状态机（不可跳步、返回保留草稿、指纹必须显式勾选、失败停在阶段、重试重置、主机密钥变化不可重试、阶段只前进）、host/端口/用户名/密码/私钥/口令/token/配对链接/名称校验、DNS/拒绝/超时/认证/转发/DSH/主机密钥变化的阶段映射与文案不泄露、命名连接新增/编辑/删除/活动标记/密文无明文、公开重连/失败重试恢复来源 profile ID、新手工配置不继承旧来源、活动凭据归属与 known_hosts 主机身份分离（同主机不同账号删除仍清理）、删除连接按来源清理活动副本但保留隧道、编辑目标后删除仍清理旧副本、同服务双 profile 不误删、迟到私钥读取按请求版本丢弃、失败重试绑定本次尝试配置、IPv6 向导配置可被下游再次校验、配对链接 + 6 位码校验与共享向量、主机 chip 状态映射、控制器测试连接全阶段完成且不切 origin、未知主机停在指纹、DSH 失败关闭隧道。既有 `RemoteHostTest`、`RemoteRelayTest`、`DshRemoteIsolationTest` 全部通过；完整 JVM 928 tests / 101 文件 / 0 失败，lint 0 errors。配置校验、IPv4/IPv6/域名规范化、token URL 只落 loopback、状态机切换/本地停止保持远程/失败回退、首次指纹确认后才建立信任、主机密钥变化拒绝、加密存储往返/篡改回退/清除、HTTP 探测 200/302/401/500 与连接拒绝、Cookie 分区、活动端点认证/重试策略仍由既有回归覆盖。本轮完整结果见 PR [#57](https://github.com/leezzou/tanuki/pull/57) 与交接记录；历史验证见 [测试报告](TEST-REPORT.md)。

本轮配对交互回归（android_agent-66，JVM）：新增剪贴板自动填充与链接算码一致、剪贴板为空/非链接/无效链接的粘贴回退、手动编辑链接不被悄悄覆盖并支持显式替换、自动码回填与手动校对不一致提示、配对确认步骤只在链接方式出现、链接过期/房间不存在/电脑离线/证书钉扎不匹配/中继端口不可达五类中文原因与下一步、钉扎不匹配不可重试。定向 `RemoteWizardTest` 46 项、`RemoteRelayTest` 22 项、`RemoteHostTest` 27 项通过；完整 JVM 1052 tests / 108 结果文件 / 0 failures / 0 errors / 0 skipped。本轮未验证项见 [交接](DEVELOPMENT-HISTORY.md#20261111-1024-mobile-pairing-auto)。
本轮主机端发现回归：`test_mdns.py` 20 项、`test_quickstart.py` 20 项、`test_remote_relay.py` 9 项通过；真实同网段手机发现与路由器组播策略未验证。

未验证：真实 Android 设备到真实 sshd/中继的端到端、真实 mDNS 发现列表与 AP 隔离/组播屏蔽、真实剪贴板自动读取与确认页布局（深色/大字体）、JSch 在 Android Keystore/后台线程的实际表现、真实远程 DSH token 登录、真实公网中继/证书、弱网/断线重连、真机与模拟器 UI 操作、Ed25519。新增 Cookie 隔离/Keystore 设备回归仅编译，未运行；设备/真实主机验收需要独立 QA 会话和明确目标。

## 7. 后续切片

- WebRTC DataChannel/STUN/TURN 的 P2P 优化（复用现有配对/身份契约；TURN 仍需公网中继）。
- Ed25519（评估 BouncyCastle 体积后引入）。
- 远程 DSH 启停、多台远程主机同时连接；命名连接库已支持多份配置，但同一时间仍只有一个活动远程连接。
- 相机扫码已接入配对链接向导；真实相机识别、相机权限拒绝/返回和窄屏布局仍待独立 Android QA。
- 已建立 SSH 会话的断线透明恢复；当前只做控制握手退避与失败回退，不自动重放用户操作。
- 中继生产化：每连接握手线程、限流、审计、TLS 证书自动轮换和高可用；参考实现不提供这些。
