# 无公网 IP 远程连接（反向注册中继切片）

本文记录“手机连接远程主机/DSH，但双方都没有公网 IP”的方案选型、端到端流程、协议契约、资源需求和降级边界。实现是第一个可运行切片：**主机端主动向 TLS 中继注册房间，手机用一次性配对链接加入，中继只转发 SSH 密文字节；SSH 仍由手机与远程 sshd 端到端协商**。WebRTC/TURN 留后续，不在本切片内。

## 1. 选型结论

| 方案 | NAT/CGNAT 适用性 | 依赖与成本 | 安全边界 | 本切片结论 |
|---|---|---|---|---|
| WebRTC DataChannel + STUN/TURN | STUN 只能覆盖部分锥形 NAT；对称 NAT、运营商 CGNAT 必须 TURN；TURN 仍是公网中继并消耗带宽 | Android 无原生 DataChannel API；需引入按 ABI 分发的 WebRTC 库或依赖隐藏 WebView，另需信令服务、STUN/TURN、ICE/DTLS/SCTP；APK 与运维复杂度高 | DTLS 指纹必须在信令中钉扎；信令、TURN 凭据和房间授权都需额外设计；组件多，审计面大 | 不作为第一切片；作为后续 P2P 优化，与现有配对/身份契约复用 |
| 反向隧道/中继 | 双方都主动出站，能穿过常见 NAT/CGNAT；不需要手机或远程主机有公网 IP | 需要一个手机和主机都能访问的中继端口；参考实现只依赖 Python 标准库与 TLS 证书；Android 侧复用 JSch，无新增运行时依赖 | 中继只看到房间元数据和 SSH 密文；SSH 主机密钥仍由手机校验；中继可拒绝服务或看流量元数据，但不能解密/冒充 sshd | **采用**；先做反向注册 + 字节透传 |
| 复用 SSH + 打洞 | UDP/TCP 打洞需要会合服务、NAT 类型判断和失败回退；TCP 打洞在运营商网络不可靠；SSH 跑在 UDP 上还需要用户态可靠传输 | 仍需信令/会合服务器；实现和测试成本高于中继；打洞失败时还是要中继 | 会合服务与打洞包都要认证；实现错误会引入明文/重放风险 | 不作为第一切片；保留为后续优化 |

**决策**：第一阶段采用“反向注册中继 + 既有 SSH 端到端 + 局域网/IPv6 直连降级”。理由：

1. 真正解决“双方都没有公网 IP”的问题，且不要求远程主机开放入站端口；
2. 复用已经实现并验证的 JSch 主机密钥确认、认证、`direct-tcpip` 本地转发和 loopback origin 契约；
3. 中继只做字节转发，不引入新的密码协议；安全边界集中在 TLS、房间 token 和既有 SSH 校验；
4. 资源需求小，可自建，也可在用户已有的一台可访问主机上运行；
5. WebRTC/TURN 的最坏情况仍然需要一个公网中继，先把可用的最小路径做对，再评估 P2P 优化。

## 2. 端到端流程

```text
远程主机                                    中继（TLS，公网/可达地址）                         手机
   |  生成 room/token/hostId，写 0600 配对文件  |                                              |
   |---- TLS 出站 HELLO host ------------------>|                                              |
   |<--- CHALLENGE -----------------------------|                                              |
   |---- AUTH(HMAC(token,...)) ---------------->|                                              |
   |<--- REGISTERED ----------------------------|                                              |
   |                                            |<---- TLS 出站 HELLO client + AUTH -----------|
   |<--- OPEN(stream) --------------------------|<---- QUEUED ---------------------------------|
   |---- 连接 127.0.0.1:sshd -------------------|                                              |
   |---- OPEN_OK(stream) ---------------------->|---- OPEN_OK(stream) ------------------------>|
   |<============ DATA：SSH 密文双向透传 =======>|<============ DATA ===========================>|
   |  本机 sshd <-> agent <-> 中继 <-> 手机桥 <-> JSch -> 远程 DSH loopback 转发 -> 管理 WebView |
```

### 2.1 主机端发现/配对

1. 远程主机运行参考 agent（`scripts/remote-relay/host_agent.py`）。agent 生成或读取稳定的 16 字节 `hostId`，生成 16 字节 `room` 和 32 字节 `token`，写入 `0600` 配对文件；token 是房间级一次性秘密，不是 SSH 密码/私钥。
2. agent 向配置的中继发起**出站** TLS 1.2+ 连接，提交 `room`、`hostId` 和 token 的 HMAC 证明。中继只在内存保存房间和 token，不落盘、不写日志。
3. 配对链接（二维码/剪贴板）只包含中继地址、房间、token、hostId 和可选证书钉扎；不包含 SSH 用户名、密码、私钥或 DSH token。手机扫码/粘贴即可，不需要知道远程主机的公网地址。
4. 中继只允许每个房间一个活动主机、一个活动客户端；主机掉线后房间按 TTL 保留，允许同一 token 重新注册；中继重启后房间消失，主机重连会重建房间，手机可继续用原配对链接（在 token 未更换的前提下）。

### 2.2 手机加入与受控通道

1. 手机设置 → 远程主机 → 新增远程连接 → 选择“配对链接”，粘贴 `tanuki-relay://v1?...` 并输入电脑启动日志里的 6 位配对码；解析器严格拒绝未知/重复字段、非规范 base64url、非法端口和非法证书钉扎。6 位码是链接的 SHA-256 前 8 字节 mod 1e6，用于人工核对，不是新的传输秘密。
2. 手机与中继建立 TLS：有 `pin` 时只信任该证书 DER 的 SHA-256（仍检查有效期）；没有 `pin` 时必须走系统 CA + 主机名校验。两者都没有合法结果就失败，不存在“忽略证书”开关。
3. 手机收到 `CHALLENGE` 后用 token 计算 `AUTH`，中继校验通过后返回 `QUEUED`；主机端连接本机 `127.0.0.1:sshd`，中继回 `OPEN_OK`。
4. 手机把中继字节流桥接到 **只监听 127.0.0.1** 的临时 TCP 端口；JSch 连接该端口，执行既有 SSH 主机密钥确认/比对、密码或私钥认证、`direct-tcpip` 本地转发。中继无法替代或关闭这些检查。
5. 远程 DSH 仍只监听远程 `127.0.0.1:<port>`；手机管理 WebView 的 origin 仍是 `http://127.0.0.1:<本地转发端口>`。Cookie 按 `relay:<hostId>:<user>:<remoteDshPort>` 逻辑服务隔离：room 不参与，同一服务换房间复用 Cookie；同一主机上的不同用户/DSH 端口分别保存，切换时不会恢复上一服务的认证 Cookie。

### 2.3 身份与指纹

| 层 | 身份/校验 | 失败行为 |
|---|---|---|
| 中继 TLS | 系统 CA + 主机名，或配对链接中的 `sha256-<base64url(证书 DER SHA-256)>` 钉扎 | 握手失败/钉扎不匹配直接拒绝，不回退明文 |
| 房间 | 32 字节随机 token；中继用 HMAC-SHA256 证明校验；token 只在 TLS 内传输/存储 | `bad_token`、`room_not_found`、`room_busy`、`host_offline` 中文错误 |
| 主机稳定身份 | 16 字节随机 `hostId`（配对链接携带）；用于 known_hosts 信任键；Cookie 作用域在其后追加 SSH 用户与远程 DSH 端口 | 缺失/非法则拒绝配对 |
| SSH 主机密钥 | 既有 `SHA256:` 首次确认 + 后续比对，按 `relay:<hostId>` 保存 | 指纹变化直接拒绝 |
| SSH 用户认证 | 既有密码/私钥；凭据只经 Android Keystore 加密保存 | 认证失败关闭隧道并回退本地 |
| 远程 DSH token | 可选，只拼到本机 loopback 启动 URL，经 SSH 隧道发送 | 不写日志、不导出 |

### 2.4 断线、重连与回退

- **控制握手重试**：`relayRetry` 只对网络、房间忙、主机离线等可恢复错误做指数退避（1s/2s/4s/8s，带抖动，最多 4 次尝试，即首次 + 至多 3 次退避；协程取消可中断）；认证失败、证书钉扎不匹配、协议错误立即失败，不重试。该策略已接入 `RelaySshTransport.join` 的实际 probe/open 路径。
- **主机端重连**：agent 断开后按 1s→30s 退避重新连接并注册；同一 `room`/`token` 在 TTL 内可重新注册。中继重启后 agent 会重建房间。
- **已建立 SSH 会话**：本切片不把断线后的 SSH 会话透明迁移到新连接；隧道断开时 `RemoteHostController` 关闭隧道、切回本地端点并显示中文错误，用户可用已保存配置重连（token 仍有效时）。透明会话恢复留后续。
- **关闭与背压**：桥接/会话关闭直接关闭底层 TLS socket，不等待可能被阻塞的 `writerLock`，也不在关闭路径同步写 CLOSE 帧；`RelaySshTunnel.close` 先拆桥再关闭 JSch，避免向停止读取的中继无限写 SSH disconnect。参考中继用每 peer 写锁内的阻塞写向下游传播背压，不使用有损队列；慢消费者不会因 32 帧队列满被断开。
- **失败回退**：中继不可达、TLS/钉扎失败、token 错误、房间过期、主机端离线、sshd 不可达或 SSH 失败都会回退本地运行时；不自动降级明文，不静默信任新主机密钥。
- **无中继时的降级**：
  1. 同一局域网/热点：直接使用既有 SSH 直连切片，零额外基础设施；
  2. 远程主机有全局 IPv6：直接 SSH（仍受防火墙和 sshd 配置约束）；
  3. 用户已有一台可访问的 SSH 跳板/VPS/家庭服务器：把它作为中继运行参考实现；
  4. 用户已有可用的组网/隧道设施：本切片不绑定任何第三方，可继续用既有 SSH 直连；
  5. 双方都在 CGNAT 且没有任何可访问的会合/中继：跨网络直连在物理上无法保证，必须提供中继或已有组网，不能承诺“零基础设施”。

## 3. 主机端 5 分钟上手（quickstart）

`scripts/remote-relay/quickstart.sh` 把主机端启动收敛为一条命令：检查 python3/openssl；按需在 `~/.tanuki-relay` 生成自签名证书（复用 `gen-cert.sh`）；自动计算中继证书的 `sha256-...` 钉扎，不需要手工抄写；后台启动 TLS 中继并做端口健康检查；启动 `host_agent.py` 时自动带上 `--cert-pin`；把配对链接尽力复制到剪贴板，并在终端显示配对链接、6 位核对码和手机端步骤。Ctrl+C 会同时停止中继与主机端 agent，不留孤儿进程。

### 3.1 三条命令

```text
1) 主机：bash scripts/remote-relay/quickstart.sh --public-host <手机可访问的中继地址或域名>
2) 手机：设置 → 远程主机 → 新增远程连接 → 粘贴配对链接，并输入终端里的 6 位核对码
3) 手机：保存后点连接；首次确认 SSH 主机密钥，认证通过后进入远程 DSH
```

只有第 1 条是主机命令，第 2、3 条是手机端操作。中继默认监听 `0.0.0.0:8443`；`--public-host` 必须是手机能访问的地址（同一 Wi-Fi 可用局域网 IP，跨网络用公网 IP/域名，并在防火墙或安全组放行端口）。本机 sshd 不在 22 时加 `--ssh-port`；端口 8443 被占用时加 `--port`。若主机尚未配置 SSH，先按 [REMOTE-HOST](REMOTE-HOST.md) 完成 sshd 和登录验证。

```bash
# 同一 Wi-Fi：自动探测局域网 IPv4，只需一条命令
bash scripts/remote-relay/quickstart.sh

# 公网域名 + 9443 端口
bash scripts/remote-relay/quickstart.sh --port 9443 --public-host relay.example.com

# 只打印解析后的参数和将执行的命令，不启动任何进程（排查用）
bash scripts/remote-relay/quickstart.sh --print-only
```

### 3.2 预期输出

首次运行会先看到证书生成与启动日志，随后是横幅。配对链接和 6 位核对码每次启动都会变化：

```text
首次运行：在 /home/you/.tanuki-relay 生成中继自签名证书（私钥仅本机可读）
启动中继：0.0.0.0:8443（日志 /home/you/.tanuki-relay/relay.log）
启动主机端 agent：连接 127.0.0.1:8443，配对地址 relay.example.com:8443

============================================================
  Tanuki 远程连接已就绪
============================================================

  配对链接（复制到手机；含一次性密钥，勿外传）：
  tanuki-relay://v1?relay=relay.example.com%3A8443&room=...&token=...&host=...&pin=sha256-...

  6 位核对码：  >>>  123456  <<<

  手机端下一步：
    1. 打开 Tanuki → 设置 → 远程主机 → 新增远程连接
    2. 选“配对链接”，粘贴上面的链接，并输入核对码 123456
    3. 保存后点连接；首次确认 SSH 主机密钥后进入远程 DSH

  中继监听：0.0.0.0:8443（主机端连接 127.0.0.1:8443）
  配对地址：relay.example.com:8443
  配对文件：/home/you/.tanuki-relay/pairing.txt
  日志：/home/you/.tanuki-relay/agent.log
  Ctrl+C 会同时停止中继与主机端 agent
============================================================
```

看到 `远程连接已就绪` 且没有 `注意：主机端尚未确认注册`，表示中继已监听、agent 已注册房间。日志在 `~/.tanuki-relay/agent.log` 与 `relay.log`；配对链接同时写入 `~/.tanuki-relay/pairing.txt`（0600）。链接含一次性房间 token，不要截图、贴到公开聊天或提交到仓库。

### 3.3 常见失败对照

| 手机/终端现象 | 含义 | 主机端处理 |
|---|---|---|
| `bad_token` | 配对链接里的 token 与中继当前房间不一致，常见于复制了旧链接，或中继重启后换了新链接 | 回到主机终端复制本次横幅里的新链接；必要时重新运行 quickstart |
| `room_not_found` | 中继内存里没有该房间：中继重启、房间 TTL 过期，或手机连错中继地址/端口 | 确认 `--public-host`、`--port` 与手机一致；重新运行 quickstart 生成新房间 |
| `host_offline` | 房间存在但主机端 agent 未注册或已掉线 | 看 `~/.tanuki-relay/agent.log` 是否在重连；确认中继端口可达；重启 quickstart |
| 钉扎不匹配（`relay certificate pin mismatch`） | 手机或 agent 记录的证书指纹与中继当前证书不一致，常见于删除/重建了证书，或手机存的是旧链接 | 用本次 quickstart 输出的新链接，不要复用旧 `pairing.txt`；重新生成证书后重新配对 |
| 端口占用（`端口 8443 已被占用`） | 8443 已被其他进程监听 | 换端口：`--port 9443`；或停止占用进程后重试 |
| `中继未在 N 秒内监听` | relay.py 启动失败，如证书/私钥不可读、监听地址不属于本机 | 看 `~/.tanuki-relay/relay.log`；确认 `--listen` 地址可用、证书与私钥匹配 |
| `未能自动复制到剪贴板` | 主机没有 pbcopy/wl-copy/xclip/xsel，或没有图形会话 | 手动打开 `~/.tanuki-relay/pairing.txt` 复制，或加 `--no-clipboard` 跳过提示 |

失败时先看终端提示和 `agent.log`。`--print-only` 会打印解析后的证书路径、钉扎、监听地址和将执行的 relay/agent 命令，便于确认参数；它不建目录、不生成证书、不启动进程。中继日志只记录房间短前缀和错误码，不记录 token、配对链接或 SSH 数据。

## 4. 协议契约

完整契约见 [INTERFACES](INTERFACES.md) 1.6。摘要：

- 传输：TLS 1.2/1.3；服务端证书必须是系统 CA 链或配对链接中的 SHA-256 钉扎。
- 帧：`4 字节大端总长 | 1 字节类型 | 4 字节流号 | 负载`；总长上限 64 KiB，控制帧负载上限 1 KiB。
- 类型：`CONTROL=1`、`OPEN=2`、`OPEN_OK=3`、`DATA=4`、`CLOSE=5`、`PING=6`、`PONG=7`、`ERROR=8`。
- 主机握手：`HELLO 1 host <room> <hostId> <token> <hostNonce>` → `CHALLENGE <serverNonce>` → `AUTH <hostProof>` → `REGISTERED <expiresAt>`。
- 客户端握手：`HELLO 1 client <room> - <clientNonce>` → `CHALLENGE` → `AUTH <clientProof>` → `QUEUED` → `OPEN(stream)`（中继到主机）→ `OPEN_OK(stream)`（中继到客户端）。
- 证明：
  - `hostProof = HMAC-SHA256(token, "tanuki-relay-v1|host|<room>|<hostId>|<hostNonce>|<serverNonce>")`
  - `clientProof = HMAC-SHA256(token, "tanuki-relay-v1|client|<room>|<clientNonce>|<serverNonce>")`
  - 输出为无 padding base64url；比较使用常量时间比较。
- 数据阶段：同一 TLS 流只承载 `DATA/CLOSE/PING/PONG/ERROR`；`DATA` 负载就是 SSH 密文字节，中继不解析。
- 错误码：`room_not_found`、`bad_token`、`room_busy`、`host_offline`、`host_timeout`、`expired`、`too_large`、`protocol`、`internal`；客户端映射为中文提示，不回显原始载荷。
- 配对链接：`tanuki-relay://v1?relay=<host:port>&room=<16B b64url>&token=<32B b64url>&host=<16B hex>&pin=sha256-<32B b64url>`；`pin` 在系统 CA 可用时可省略，自签名中继必须提供。
- 共享测试向量：`app/src/test/resources/relay-protocol-vectors.json`，Kotlin 与 Python 回归都校验同一组 HMAC 值。

## 5. 资源需求与部署

### 5.1 中继

- 任意手机和远程主机都能访问的地址/端口；IPv4/IPv6 均可，建议 TLS 443 以穿过常见出站限制。
- 最低参考：1 vCPU、128–256 MiB 内存；空闲房间约几 MiB；带宽约等于被转发的 SSH/DSH Web UI 流量（中继只转发密文，不压缩、不缓存）。
- 需要 TLS 证书：公网域名可用系统 CA 证书；自签名证书必须在配对链接里带 `pin`。私钥只留在中继主机，权限 0600。
- 参考实现没有持久化、水平扩展、限流、审计存储和高可用；生产化需要补这些，不能把参考脚本当公网 SLA 服务。
- 中继必须能承受恶意连接的基本边界：房间上限、帧/控制帧上限、认证失败即关闭；参考实现的 TLS 握手在 accept 线程内完成，公开部署前应改为每连接独立握手线程并加限流。

### 5.2 远程主机端

- 推荐用 [3. 主机端 5 分钟上手](#3-主机端-5-分钟上手quickstart) 的 `quickstart.sh` 一条命令启动；它复用下面的 relay/agent，不改变安全边界。
- 只需要**出站**访问中继端口；不新增监听端口，不需要路由器端口映射/公网 IP。
- 本机 sshd 已运行，agent 只连接 `127.0.0.1:<sshPort>`；不读取/修改 sshd 配置，不执行命令。
- 配对文件包含 token，权限 0600；不要提交到仓库、贴到公开聊天或写入日志。

### 5.3 手机端

- 复用现有 JSch 与 Android JCA，不新增 WebRTC/中继 SDK；APK 体积不变。
- 中继 token 经 Android Keystore AES-256-GCM 加密后保存；配对链接输入框视为秘密，不应截图/转发。
- 只新增 loopback 桥监听 `127.0.0.1`，不开放局域网端口。

### 5.4 需要 TURN/WebRTC 时的额外资源

- 信令服务（房间/身份/ICE 候选交换）与 STUN；对称 NAT/CGNAT 还需要 TURN。
- TURN 需要公网地址、UDP/TCP 端口范围、带宽和凭据轮换；流量成本高于纯字节中继，且仍需 TLS/DTLS 指纹与授权设计。
- 因此本切片不假设用户有公网服务器，也不把 TURN 作为默认路径；没有中继时按 2.4 的局域网/IPv6/既有跳板降级。

## 6. 安全边界与威胁模型

- **传输**：中继连接必须 TLS 1.2+；有 pin 时只信该证书，没有 pin 时系统 CA + 主机名；不存在 `CERT_NONE` 无 pin、明文 scheme 或“忽略证书”的用户开关。
- **房间 token**：256 位随机；中继只存内存；手机加密保存；不写日志、不出现在 `toString()`/错误消息；配对文件 0600。token 泄露可让攻击者占用房间（DoS），但仍需通过 SSH 用户认证，不能直接获得 DSH。
- **端到端 SSH**：SSH 密钥交换/认证/主机密钥校验在手机与远程 sshd 之间完成；中继只看到 TLS 内的 SSH 密文和流量大小。中继被攻破可以拒绝服务、观察元数据或尝试替换房间对端，但不能解密 SSH，也不能让手机接受未确认的新主机密钥。
- **主机端 agent**：不监听、不执行命令、不读项目文件；只连中继和本机回环 sshd。中继不能让它连接任意地址/端口（目标由本地参数固定）。
- **管理页**：origin 仍是精确 `http://127.0.0.1:<本地端口>`；TLS/明文策略、Cookie 校验、Blob 导出边界不因中继而放宽；Cookie 按 `relay:<hostId>:<user>:<remoteDshPort>` 隔离，同一主机不同用户/DSH 端口不能互相恢复认证 Cookie。
- **参考实现限制**：不防同 UID 恶意代码、不提供发布者签名验证、不做 Relay 高可用/限流/审计；不是恶意远程主机沙箱。

## 7. 实现位置

| 责任 | 文件 |
|---|---|
| 配对链接、帧、HMAC、TLS 钉扎、重连策略 | `app/src/main/kotlin/com/tanuki/runtime/RemoteRelay.kt` |
| TLS 中继客户端、loopback 桥、直连/中继路由 | `app/src/main/kotlin/com/tanuki/runtime/RelayTransport.kt` |
| 远程配置/known_hosts/Cookie 作用域 | `app/src/main/kotlin/com/tanuki/runtime/RemoteHost.kt`、`RemoteHostStorage.kt`、`RemoteHostController.kt` |
| 关闭/背压与握手退避 | `app/src/main/kotlin/com/tanuki/runtime/RelayTransport.kt`、`scripts/remote-relay/relay.py` |
| 设置页配对链接入口 | `app/src/main/kotlin/com/tanuki/ui/RemoteHostSettings.kt` |
| JVM 回归 | `app/src/test/kotlin/com/tanuki/RemoteRelayTest.kt` |
| Python 参考中继/主机端/自测 | `scripts/remote-relay/{protocol,relay,host_agent,test_remote_relay,gen-cert}.py/sh` |
| 一键启动/钉扎与参数辅助/自测 | `scripts/remote-relay/{quickstart.sh,quickstart_lib.py,test_quickstart.py}` |
| 共享协议向量 | `app/src/test/resources/relay-protocol-vectors.json` |

## 8. 验证与未验证

已执行（JVM/宿主）：

- `RemoteRelayTest` 22 项：配对链接往返/拒绝未知字段/非规范 base64url；帧编解码/上限/截断/未知类型；共享 HMAC 向量；证书钉扎；重连策略；loopback 假中继的配对 + DATA 回声 + 早期字节不丢失；错误 token；中继配置身份/Cookie 作用域；加密存储往返/篡改拒绝/切回直连；控制器使用稳定 `relay:<hostId>` 信任键。
- `DshRemoteIsolationTest` 新增中继 Cookie 分区：同一 hostId/user/DSH 端口换 room 复用同一分区；不同用户、不同 DSH 端口分别保存，互不恢复认证 Cookie。
- `RemoteRelayTest` 新增 P2 回归：对端不读/写阻塞时 `RelayBridge.close` 与 `RelayClientSession.close` 及时返回；`relayRetry` 先失败后成功、重试耗尽、认证/钉扎/协议不重试、退避取消。
- Python 参考实现 9 项回环端到端：自签名 TLS + pin、主机注册、客户端 JOIN/OPEN_OK/DATA 双向、2 MiB 慢消费者完整传输、错误 token、未知房间、明文连接不获得协议响应、共享向量一致、日志不泄露 token。
- `test_quickstart.py` 19 项离线自测（2026-11-11，本机 Python 3.9.6/OpenSSL 4.1.0）：证书 DER SHA-256 钉扎与 `protocol.cert_pin` 一致、不同证书钉扎不同、非法 PEM 拒绝；relay/agent 命令拼装与 `--cert-pin`/`--pairing-relay` 传递、非法端口/地址拒绝；配对文件解析与 6 位码、空/损坏文件拒绝；端口探测与等待、局域网 IPv4 探测；`--print-only` 不建目录/不启动进程；端口占用快速失败；真实回环启动后 SIGINT 退出码 130，relay 与 agent 均退出、端口释放、`quickstart.pids` 清理。
- `host_agent.py --pairing-relay` 回归：连接地址仍为 `127.0.0.1:1` 时，配对链接展示 `203.0.113.9:9443`，钉扎保持不变。

未验证：

- 真实 Android 设备与真实远程 sshd/DSH 的端到端；Android Keystore 在真机上的中继 token 存储；真实弱网/断线重连；中继公网部署、TLS 证书链/域名、运营商网络/CGNAT；多客户端并发/高可用/限流；真实二维码扫描；Ed25519；WebRTC/TURN。
- quickstart 的真实手机配对、真实剪贴板（pbcopy/xclip/wl-copy）与真实公网地址未验证；自测使用 `--no-clipboard` 和回环端口，不能替代设备/公网验收。
- 需要独立 Android QA 提供设备、APK、SHA 和实际步骤；需要用户提供一台可访问的中继主机或明确的中继地址后，才能做真实公网验收。
