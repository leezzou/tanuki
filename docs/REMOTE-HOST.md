# 远程主机（直连 SSH 切片）

本切片让 Tanuki 的管理 WebView 指向另一台机器上的 DSH：在 Android 本机建立直连 SSH，把远程 DSH 的 loopback 端口转发到本机 loopback，再按新 origin 加载官方页面。中继、WebRTC、远程 DSH 启停和多主机管理尚未实现。

## 1. 适用前提

- 远程主机可从 Android 设备直接访问 SSH 端口。
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
- 限制：这是用户主动配置的 SSH 通道，不是恶意远程主机沙箱；不提供中继/WebRTC、远程进程管理或多主机隔离。

## 5. 实现位置

| 责任 | 文件 |
|---|---|
| 配置校验、token 隧道 URL、主机指纹、origin 状态机 | `app/src/main/kotlin/com/tanuki/runtime/RemoteHost.kt` |
| Keystore 加密存储、known_hosts 指纹 | `app/src/main/kotlin/com/tanuki/runtime/RemoteHostStorage.kt` |
| 管理页 Cookie 逻辑主机隔离 | `app/src/main/kotlin/com/tanuki/runtime/DshCookieIsolation.kt` |
| JSch SSH 隧道与 HTTP 探测 | `app/src/main/kotlin/com/tanuki/runtime/SshTransport.kt` |
| 连接流程、首次确认、失败回退 | `app/src/main/kotlin/com/tanuki/runtime/RemoteHostController.kt` |
| RuntimeManager origin 接线 | `app/src/main/kotlin/com/tanuki/runtime/RuntimeManager.kt` |
| 设置页与对话框 | `app/src/main/kotlin/com/tanuki/ui/RemoteHostSettings.kt` |
| JVM 回归 | `app/src/test/kotlin/com/tanuki/RemoteHostTest.kt`、`DshRemoteIsolationTest.kt` |
| 设备回归（仅编译，未运行） | `app/src/androidTest/kotlin/com/tanuki/DshCookieIsolationTest.kt`、`RemoteHostKeystoreTest.kt` |

## 6. 验证与未验证

已执行（JVM）：配置校验、IPv4/IPv6/域名规范化、token URL 只落 loopback、状态机切换/本地停止保持远程/失败回退、首次指纹确认后才建立信任、主机密钥变化拒绝、加密存储往返/篡改回退/清除、测试连接不切换 origin、HTTP 探测 200/302/401/500 与连接拒绝、错误信息不包含密码、Cookie 分区（本地/两台远程互不串读）、活动端点认证/重试策略。合入安全模式与 XLSX 后完整 JVM 293/0/0/0。

未验证：真实 Android 设备到真实 sshd 的 SSH 认证、JSch 在 Android Keystore/后台线程的实际表现、真实远程 DSH token 登录、弱网/断线重连、真机与模拟器 UI 操作、Ed25519。新增 Cookie 隔离/Keystore 设备回归仅编译，未运行；设备/真实主机验收需要独立 QA 会话和明确目标。

## 7. 后续切片

- 中继/WebRTC（跨 NAT，不要求 SSH 直连）。
- Ed25519（评估 BouncyCastle 体积后引入）。
- 远程 DSH 启停与多主机/多凭据管理。
- 断线自动重连策略和远程状态持久化（当前失败即回退，不自动重放）。
