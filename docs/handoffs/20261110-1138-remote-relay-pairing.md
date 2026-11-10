# 交接：20261110-1138-remote-relay-pairing

- Agent/会话标识：android_agent-48（Tanuki DSH Worker）
- 时间：2026-11-10 11:38 Asia/Shanghai（开始）；结束时间见下方验证
- 状态：已完成（PR [#36](https://github.com/leezzou/tanuki/pull/36) 待审；未合并、未发布）
- 前一记录：[0.1.10 Pre-release 发布准备](20261109-2310-release-0.1.10.md)（android_agent-32）；远程主机直连切片见 [远程主机 SSH](20261109-1810-remote-host-ssh.md)（android_agent-34）

## 用户目标与范围

- 用户本次实际要求：实现不需要公网 IP 的远程连接，让手机连接到远程主机/DSH；先评估 WebRTC DataChannel + STUN/TURN、反向隧道/中继、复用 SSH + 打洞，给出端到端流程、身份/指纹、加密、断线重连、失败回退；实现第一个可运行切片，先定契约，不破坏现有 remote origin；说明中继/TURN 资源需求与不依赖公网的降级方案；补协议/JVM 回归；中文注释、author/committer=leezzou、PR、不合并、不发布。
- 本次包含：选型与端到端文档、INTERFACES 契约、Kotlin 配对/帧/HMAC/TLS 钉扎/重连、TLS 中继客户端与 loopback 桥、中继配置/存储/控制器/设置页接线、Python 参考中继与主机端 agent、JVM 与 Python 回环回归、文档与交接。
- 本次不包含：WebRTC/TURN/P2P 打洞实现、已建立 SSH 会话的透明迁移、远程 DSH 启停、多主机、真实 Android/公网中继验收、Ed25519、合并/发布。
- 用户改变或暂停的要求：既有“API Key 保存暂停”“真机验收另行确认”“不合并/不发布”继续保留；仓库文档/注释/提交不出现参考实现的名称或来源。

## 当前状态

- 已实现：中继配对契约与 Android 客户端、loopback 桥、直连/中继路由、加密存储/稳定身份/Cookie 作用域、设置页配对入口、Python 参考中继/主机端 agent、共享协议向量与回归。
- 已验证（本轮实际执行）：
  - `compileModernDebugKotlin` BUILD SUCCESSFUL。
  - `RemoteRelayTest` 22 tests / 0 failures / 0 errors / 0 skipped。
  - Python 参考实现 7 项回环端到端自测 OK。
  - P1 修复前完整 JVM/lint：BUILD SUCCESSFUL（2m32s）；552 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 95 warnings / 1 hint。
  - 合并 `origin/main=533eff4` 后、P2 修复前完整 JVM/lint：BUILD SUCCESSFUL（2m20s）；575 tests / 0 failures / 0 errors / 0 skipped（68 个结果文件）。
  - P2 修复后完整 JVM/lint：BUILD SUCCESSFUL（2m17s）；**581 tests / 0 failures / 0 errors / 0 skipped**（68 个结果文件）；lint **0 errors / 95 warnings / 1 hint**。
- 待验证：真实 Android 设备到真实中继/sshd/DSH、Android Keystore token、真实公网 TLS/域名/CGNAT、弱网/断线、多客户端/限流、二维码扫描、Ed25519、WebRTC/TURN；独立 Codex High Review、合并/发布未授权。
- 主线同步：已合并 `origin/main=533eff4`、`1cb7ad9`，解决 `AGENT-STATE`/`CURRENT.json`/`verify.yml` 冲突；合并后的 app 代码与 `049edcc` 相同。
- 当前目标包名、分支/无 Git 状态、是否有运行中任务：分支 `ao/tanuki-remote/relay-pairing`，基线 `origin/main=0555c3c0eaeeed181269b5e68733ccfdd48d9e75`；PR [#36](https://github.com/leezzou/tanuki/pull/36) 已创建，base `main`；无运行中任务。
- 关键事实与可定位源码/公共文档：方案/资源/流程见 `docs/REMOTE-CONNECT.md`；契约见 `docs/INTERFACES.md` 1.6；实现见 `app/src/main/kotlin/com/tanuki/runtime/{RemoteRelay,RelayTransport,RemoteHost,RemoteHostStorage,RemoteHostController}.kt`；参考中继/agent 见 `scripts/remote-relay/`；JVM 回归见 `app/src/test/kotlin/com/tanuki/RemoteRelayTest.kt`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `docs/REMOTE-CONNECT.md` | 新增选型对比、端到端流程、协议摘要、资源需求、无公网降级、安全边界、验证/未验证 |
| `docs/INTERFACES.md` | 新增 1.6 中继配对契约；WebState/DSH 事件/原生界面顺延为 1.7/1.8/1.9 |
| `docs/SECURITY.md` | 新增中继 TLS/钉扎、token、SSH 端到端、agent 只出站、Cookie/origin 边界 |
| `docs/REMOTE-HOST.md` | 补充中继模式使用流程、实现位置、验证与后续切片 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteRelay.kt` | 新增配对链接、帧、HMAC、TLS 钉扎、重连策略、错误映射、随机材料 |
| `app/src/main/kotlin/com/tanuki/runtime/RelayTransport.kt` | 新增生产 TLS 客户端、JOIN 握手、loopback 桥、路由选择、失败映射；P2 修复 close 不等待 writerLock、接入握手退避 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteHost.kt` | 配置增加可选 `relay`，稳定 `hostIdentity`/Cookie 作用域/展示名/校验/脱敏 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteHostStorage.kt` | 中继非敏感字段 + token 加密保存、mode 提交标记、篡改拒绝、切回直连清理 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteHostController.kt` | 用连接身份读写 known_hosts、中继提示/错误映射、展示名 |
| `app/src/main/kotlin/com/tanuki/runtime/SshTransport.kt` | `JschSshTransport` 支持只替换 TCP 拨号目标，供中继桥复用既有认证/转发 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeManager.kt` | 生产接线改为 `RoutingSshTransport` |
| `app/src/main/kotlin/com/tanuki/ui/RemoteHostSettings.kt` | 新增配对链接输入、解析失败提示、中继状态展示、说明文案 |
| `app/src/test/kotlin/com/tanuki/RemoteRelayTest.kt` | 新增 22 项 JVM 回归（含 P2 close/重试/背压握手路径） |
| `app/src/test/kotlin/com/tanuki/DshRemoteIsolationTest.kt` | Review P1：新增中继 Cookie 按 hostId + 用户 + 远程 DSH 端口分区、同服务换 room 复用的回归 |
| `app/src/test/resources/relay-protocol-vectors.json` | Kotlin/Python 共用 HMAC 向量 |
| `scripts/remote-relay/protocol.py` | 线协议、HMAC、配对链接、证书钉扎的 Python 参考实现 |
| `scripts/remote-relay/relay.py` | TLS 中继：房间注册/挑战、客户端加入、字节转发、TTL/上限；P2 改为阻塞写背压，去掉 32 帧有损队列 |
| `scripts/remote-relay/host_agent.py` | 只出站主机端 agent：注册、连接本机回环 sshd、双向转发、重连 |
| `scripts/remote-relay/test_remote_relay.py` | 7 项回环端到端自测（合成证书/字节、2 MiB 慢消费者，不访问外网） |
| `scripts/remote-relay/gen-cert.sh` | 自签名中继证书生成辅助 |
| `.github/workflows/verify.yml` | Host job 增加 Python 中继回环自测步骤 |
| `docs/AGENT-STATE.md` | 新增本轮状态小节、更新当前交接链接与下一步 |

### Review 修复

- PR #36 [P1 discussion r4521208579](https://github.com/leezzou/tanuki/pull/36#discussion_r4521208579)：中继 Cookie 作用域原来只有 `relay:<hostId>`，同一主机不同 SSH 用户/远程 DSH 端口会共用 Cookie jar，切换时可能恢复上一服务的认证 Cookie。已改为 `relay:<hostId>:<user>:<remoteDshPort>`；room 不参与，因此同一服务换房间仍复用 Cookie，不同用户/DSH 端口隔离。
- 回归：`DshRemoteIsolationTest` 覆盖同 hostId 下不同用户/端口分区、同服务换 room 复用；`RemoteRelayTest` 断言更新为新作用域。同步修正 `INTERFACES` 1.6、`REMOTE-CONNECT`、`REMOTE-HOST`、`SECURITY` 与状态文档。
- PR #36 [P2 discussion r4521208583](https://github.com/leezzou/tanuki/pull/36#discussion_r4521208583)：`RelayBridge.close` 原来在关闭前等待 `writerLock`，对端停读时会被阻塞的 TLS 写拖住；`RelayClientSession.close` 也在关闭路径同步写 CLOSE。已改为先关闭底层 socket 解除阻塞、不再同步写 CLOSE，并让 `RelaySshTunnel.close` 先拆桥再关 JSch。回归：`bridgeCloseDoesNotWaitForBlockedTlsWrite`、`sessionCloseDoesNotAttemptBlockingCloseFrameWrite`。
- PR #36 [P2 discussion r4521208585](https://github.com/leezzou/tanuki/pull/36#discussion_r4521208585)：参考中继 `Peer` 的 32 帧有损队列在慢消费者时会断开合法 SSH。已去掉队列与 writer 线程，改为每 peer 写锁内的阻塞写，把背压沿 TCP/TLS 传回上游；所有可能阻塞的发送移到 `room.lock` 之外，避免死锁。回归：Python 7 项中的 2 MiB 慢消费者完整传输。
- PR #36 [P2 discussion r4521208592](https://github.com/leezzou/tanuki/pull/36#discussion_r4521208592)：`RelayReconnectPolicy` 原来没有生产调用方。已抽出 `relayRetry` 并接入 `RelaySshTransport.join`，最多 4 次尝试、只重试网络/房间忙/主机离线等瞬时错误、协程取消可中断；认证/钉扎/协议错误只尝试一次。回归：先失败后成功、重试耗尽、认证/钉扎/协议不重试、退避期间取消。

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin -x buildNativeRuntime` | BUILD SUCCESSFUL（约 3m53s；含既有告警） | 仅 Kotlin 编译；未跑设备 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.RemoteRelayTest' -x buildNativeRuntime` | BUILD SUCCESSFUL；22 tests / 0 failures / 0 errors / 0 skipped | 覆盖配对链接、帧、HMAC 向量、钉扎、重连、假中继 DATA 回声、错误 token、存储、身份/Cookie、控制器、P2 close/重试 |
| `cd scripts/remote-relay && python3 test_remote_relay.py` | OK；7 tests / 0 failures | 自签名 TLS + pin、注册、JOIN/OPEN_OK/DATA、2 MiB 慢消费者、错误 token、未知房间、明文拒绝、日志不含 token；仅回环；已加入 CI Host job |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.DshRemoteIsolationTest' --tests 'com.tanuki.RemoteRelayTest' -x buildNativeRuntime` | BUILD SUCCESSFUL；27 tests / 0 failures / 0 errors / 0 skipped | P1/P2 修复后定向回归；含 Cookie 用户/端口分区、close、重试 |
| `./gradlew :app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime`（P1 修复前） | BUILD SUCCESSFUL（2m32s）；66 个结果文件，552 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 95 warnings / 1 hint | 完整 JVM 含既有 535 + 新增 17 |
| 合并 `origin/main=533eff4` 后、P2 修复前 `./gradlew ...` | BUILD SUCCESSFUL（2m20s）；68 个结果文件，575 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 95 warnings / 1 hint | main 558 + 中继 17；解决 AGENT-STATE/CURRENT 冲突后重跑 |
| P2 修复后 `./gradlew :app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL（2m17s）；68 个结果文件，581 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 95 warnings / 1 hint | 新增 6 项 close/重试回归；Python 7 项通过 |
| `python3 scripts/check-handoff.py --record ...` / 无参检查 | 通过；602 public files | 本轮结束刷新印章 |
| `python3 scripts/check-publication.py --git` | 0 findings | 实际暂存内容检查 |
| PR #36 P1 修复提交 `ff8b706` 的 push/PR CI | Host scripts、Android build/JVM/lint 均 pass | 公开 Actions；P2 修复后的新 head CI 以 PR 页面为准 |
| `python3 scripts/package-source.py` | 待执行 | 仅检查源码包，不提交 |

- 哪些是前一轮结果，哪些是本次新验证：本次只记录上表本轮实际执行；0.1.10 的 535 tests 与旧 PR 结果未复跑，不作为本轮结论。
- 未测试、超时、失败和暂未复现的情况：真实设备/公网中继/真实 sshd 与 DSH、Keystore 真机、弱网/断线、多客户端/限流、二维码扫描、Ed25519、WebRTC/TURN；未安装设备、未生成发布包。

## 未完成与暂停项

- 未完成：独立 Codex High Review、真实设备/公网中继 QA。
- 外部阻塞：真实公网中继与 Android 设备需用户/独立 QA 提供资源与授权。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收继续按既有要求等待授权/恢复；不合并、不发布、不触发 AO Review。

## 下一步

1. PR [#36](https://github.com/leezzou/tanuki/pull/36) 已创建，base `main`；以 PR 页面最终 head/CI 为准，不合并、不发布。
2. 由 Orchestrator 安排当前 head 的独立 Codex High Review；真实设备/公网中继 QA 需独立 QA session、明确中继地址/证书和 APK/SHA。

## 约束与风险

- 数据/设备/权限边界：未安装/启动 App，未操作设备，未读取真实凭据；所有回归使用合成 token/证书/字节；参考中继仅回环测试。
- 关键决策及理由：优先反向注册中继 + SSH 端到端，复用既有 JSch 主机密钥/认证/转发与 loopback origin；中继只转发密文，TLS 必须系统 CA 或钉扎；WebRTC/TURN 留后续。
- 失败尝试与避免重复踩坑的结论：
  - 握手不能用 `BufferedInputStream`：会把 `OPEN_OK` 之后紧邻的首批 SSH 字节预读进旧缓冲区；改为无缓冲 `DataInputStream`，并加“早期 banner 不丢失”回归。
  - Python `settimeout(0.0)` 是 non-blocking 而非无超时；握手后改为 `settimeout(None)`，否则 agent/中继空闲会误断。
  - IPv6 在配对链接中需要方括号编码，解析后存储/拨号用不带方括号形式；中继主机规范化单独接受无方括号 IPv6。
  - 中继 token 只在内存保存，不写日志；手机端 token 经 Keystore 加密，配对文件 0600；参考中继未做生产化限流/HA，已在文档标注。

## 产物与校验

- PR [#36](https://github.com/leezzou/tanuki/pull/36)，base `main`，head 以 PR 页面为准。
- 新/旧 APK、源码 ZIP 及校验文件：本轮不生成 APK/ZIP 发布附件；只创建 PR。
- 是否覆盖安装/签名范围/未发布事项：不安装、不覆盖、不发布。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮未删除旧交接；现有记录仍含未完成 Review/QA 或历史验证依据，未判定为可安全删除。若后续确认过期，应先迁移结论到 `AGENT-STATE`/`TEST-REPORT`/专题文档，再删除并修复引用。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。
