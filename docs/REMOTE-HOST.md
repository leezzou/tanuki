# 远程主机（直连 SSH 与无公网中继）

本切片让 Tanuki 的管理 WebView 指向另一台机器上的 DSH：在 Android 本机建立 SSH，把远程 DSH 的 loopback 端口转发到本机 loopback，再按新 origin 加载官方页面。

支持两种连接方式：

1. **直连 SSH**：手机能直接访问远程 SSH 端口；零额外基础设施。
2. **TLS 中继配对（无公网 IP）**：主机端 agent 主动向中继注册，手机用一次性配对链接加入；中继只转发 SSH 密文，SSH 仍端到端。方案、资源和降级见 [无公网 IP 远程连接](REMOTE-CONNECT.md)。

WebRTC/TURN、远程 DSH 启停和多主机管理尚未实现。

## 1. 适用前提

- 直连模式：远程主机可从 Android 设备直接访问 SSH 端口。
- 中继模式：手机和主机都能访问同一个 TLS 中继端口；主机端运行 `scripts/remote-relay/host_agent.py`，主机只需出站访问中继，不需要公网 IP 或入站端口。
- 远程 DSH 已启动，并只监听远程 `127.0.0.1:<port>`；本切片不会远程启动或停止 DSH。
- 远程 SSH 支持密码、RSA 或 ECDSA 公钥认证。Ed25519 私钥/主机密钥需要额外 BouncyCastle，当前版本会明确失败，留待后续。
- 首次连接需要用户核对主机公钥指纹；远程 DSH token 是可选项，按远程实际启动参数填写。

## 2. 使用流程

1. 设置 → 远程主机 → 配置远程主机。
2. 填写主机（IPv4/IPv6/域名）、SSH 端口（默认 22）、用户名、密码或私钥/口令、远程 DSH 端口（默认 3080）和 token。
3. “测试连接”会先做主机密钥确认，再验证 SSH 认证和远程 DSH 端口可达；成功后不保留隧道，也不保存配置。
4. “保存并连接”在确认与探测成功后，把配置（凭据加密）保存到本机并打开持久隧道；管理 WebView 切换到 `http://127.0.0.1:<本地端口>`。
5. DSH 页显示“远程 DSH 已连接”、远程主机、隧道端口和主机密钥。点“断开远程并切回本地”关闭隧道，恢复本地 origin；本地 DSH 若仍在运行会继续可用。
6. “清除配置与主机密钥”删除已保存的凭据、配置和信任指纹，并断开当前远程连接。

### 2.1 中继配对（无公网 IP）

1. 在可访问的主机上运行中继：`relay.py --cert ... --key ...`；启动时打印 `relay_pin=sha256-...`（证书指纹，不是私钥）。
2. 在远程 DSH 主机运行 agent：`host_agent.py --relay <host:port> --cert-pin <pin> --ssh-port 22`。agent 只向中继发起出站 TLS，并连接本机 `127.0.0.1:22`；不监听端口。
3. agent 把 `tanuki-relay://v1?...` 配对链接写入 `~/.tanuki-relay/pairing.txt`（0600）。把该链接/二维码交给手机；链接含一次性 token，不要截图或公开转发。
4. 手机设置 → 远程主机 → 配置，粘贴配对链接，填写 SSH 用户名/密码或私钥、远程 DSH 端口和可选 token；主机字段可留空（仅作展示名）。
5. 首次连接仍展示远程 SSH 主机密钥 `SHA256:` 指纹，确认后才发送认证信息；之后按 `relay:<hostId>` 比对。中继证书钉扎失败/主机密钥变化都会拒绝，不降级明文。
6. 中继模式与直连模式共用同一个 loopback origin、Cookie 隔离、失败回退本地和“断开远程并切回本地”流程。

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
| 设置页与对话框 | `app/src/main/kotlin/com/tanuki/ui/RemoteHostSettings.kt` |
| JVM 回归 | `app/src/test/kotlin/com/tanuki/RemoteHostTest.kt`、`DshRemoteIsolationTest.kt`、`RemoteRelayTest.kt` |
| Python 参考中继/主机端/自测 | `scripts/remote-relay/` |
| 设备回归（仅编译，未运行） | `app/src/androidTest/kotlin/com/tanuki/DshCookieIsolationTest.kt`、`RemoteHostKeystoreTest.kt` |

## 6. 验证与未验证

已执行（JVM）：配置校验、IPv4/IPv6/域名规范化、token URL 只落 loopback、状态机切换/本地停止保持远程/失败回退、首次指纹确认后才建立信任、主机密钥变化拒绝、加密存储往返/篡改回退/清除、测试连接不切换 origin、HTTP 探测 200/302/401/500 与连接拒绝、错误信息不包含密码、Cookie 分区（本地/两台远程互不串读）、活动端点认证/重试策略。中继切片新增 `RemoteRelayTest` 16 项（配对链接、帧、HMAC 向量、TLS 钉扎、重连、假中继 DATA 回声、错误 token、加密存储、稳定身份/Cookie 作用域）。Python 参考实现 6 项回环端到端自测通过。完整 JVM 结果见 [测试报告](TEST-REPORT.md)。

未验证：真实 Android 设备到真实 sshd/中继的端到端、JSch 在 Android Keystore/后台线程的实际表现、真实远程 DSH token 登录、真实公网中继/证书、弱网/断线重连、真机与模拟器 UI 操作、Ed25519。新增 Cookie 隔离/Keystore 设备回归仅编译，未运行；设备/真实主机验收需要独立 QA 会话和明确目标。

## 7. 后续切片

- WebRTC DataChannel/STUN/TURN 的 P2P 优化（复用现有配对/身份契约；TURN 仍需公网中继）。
- Ed25519（评估 BouncyCastle 体积后引入）。
- 远程 DSH 启停与多主机/多凭据管理。
- 已建立 SSH 会话的断线透明恢复；当前只做控制握手退避与失败回退，不自动重放用户操作。
- 中继生产化：每连接握手线程、限流、审计、TLS 证书自动轮换和高可用；参考实现不提供这些。
