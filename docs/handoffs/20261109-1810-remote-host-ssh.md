# 交接：20261109-1810-remote-host-ssh

- Agent/会话标识：android_agent-34（Tanuki DSH Worker）
- 时间：2026-11-09 19:10 Asia/Shanghai
- 状态：已完成（PR [#29](https://github.com/leezzou/tanuki/pull/29) 待审）
- 前一记录：[DSH 已安装版本管理](20261109-1539-dsh-installed-version-manager.md)（android_agent-25）；并行记录 [后台更新检查](20261109-1535-background-update-check.md)、[DSH 安装提速与国内镜像](20261109-1539-dsh-install-resilience.md)

## 用户目标与范围

- 用户本次实际要求：实现远程主机最小可用切片——直连 SSH + 本地/远程切换；读现有 RuntimeManager origin/端口/token、WebView 加载与设置存储；远程配置（host/port/user/密钥或密码）、SSH 隧道到远程 DSH 端口、切换 local/remote origin，不破坏本地运行时；凭据放 Android Keystore/加密存储、不落日志/不导出；主机密钥 known_hosts/首次确认，拒绝明文降级，远程 token 只走隧道；连接测试、失败回退本地并给中文错误；不做中继/WebRTC 并标注后续；Android 兼容依赖、无 GPL 不兼容；JVM 回归覆盖配置校验、切换状态机、失败回退、密钥不泄露，SSH 接口可 mock。
- 本次包含：`RemoteHostConfig` 校验、`RemoteHostMachine` origin 状态机、Android Keystore AES-GCM 存储、known_hosts 指纹、JSch 直连 SSH 本地转发、连接测试、设置页与 DSH 页远程状态、失败回退、JVM 回归、文档与依赖许可。
- 本次不包含：中继/WebRTC、远程 DSH 启停、多主机、Ed25519（需额外 BouncyCastle）、真实主机/设备验收、自动重连、远程会话迁移。
- 用户改变或暂停的要求：未改变；既有“API Key 保存暂停”“真机验收另行确认”“不合并/不发布”继续保留。

## 当前状态

- 已实现：直连 SSH + 端口转发 + local/remote origin 切换 + 设置页 + 失败回退 + 加密凭据/主机密钥校验 + JVM 回归。
- 已验证（本轮实际执行）：`compileModernDebugKotlin`、`assembleModernDebug -x buildNativeRuntime`（D8）、远程分支完整 `testModernDebugUnitTest` 229/0/0/0、`lintModernDebug` 0 errors/80 warnings/1 hint、依赖清单 91 构件并刷新许可文件；合入 `main=5fd1ab4`（安全模式 + XLSX）后完整 JVM 289/0/0/0（41 结果文件）、lint 0 errors/81 warnings/1 hint；review 修复并合入冷启动/PDF/原生界面后完整 JVM 424/0/0/0（57 结果文件）、lint 0 errors/95 warnings，androidTest 编译通过。
- 待验证：真实 Android 设备到真实 sshd、真实 Android Keystore、真实远程 DSH token 登录、弱网/断线、UI 手势、Ed25519、Android 仪器测试、真机。
- 状态顺序：按串行合并候选要求，`AGENT-STATE` 将已合并的冷启动作为当前交接，远程主机作为并行记录；`CURRENT.json` 仍绑定本记录以便交接指纹校验。
- 主线同步：最终合入 `main=d9b4aa2`（PR #27 DSH 冷启动，含 PR #22 聊天通知、PR #25 安全模式与 PR #23 XLSX）；同步包含 `.github/workflows/verify.yml`、`scripts/test-chat-session-open.mjs` 与冷启动的 `scripts/test-dsh-path-cache.mjs`，交接指纹已按合并树刷新。`RuntimeManager`/`State.kt`/`TanukiUi.kt` 与安全模式有代码冲突，已保留远程 origin 与安全模式恢复策略：本地端点单独清理、远程连接时本地失败不强制断开网页；文档保留远程、XLSX、安全模式三节。
- 当前目标包名/分支：仅 JVM/APK 构建，未安装设备；分支 `ao/remote-host/remote-ssh` 已从基线 `2f3fac9301c7c3484df85e99091e8f63dddc0a48` 创建，PR [#29](https://github.com/leezzou/tanuki/pull/29) 已创建，base `main`，未触发 Review；head 以 PR 页面为准。无运行中任务。
- 关键事实：远程 origin 固定 `http://127.0.0.1:<隧道本地端口>`；远程 token 只拼到该 loopback URL；凭据只在 `remote-host-credentials` SharedPreferences 的 AES-GCM 密文，密钥来自 AndroidKeyStore；主机指纹保存在 `remote-host-known-hosts`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/runtime/RemoteHost.kt` | 新增配置校验、IPv4/IPv6/域名规范化、token 隧道 URL、SHA-256 主机指纹、local/remote origin 状态机 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteHostStorage.kt` | 新增 Keystore AES-256-GCM 凭据存储、known_hosts 指纹、清除逻辑；Keystore 失败不降级明文 |
| `app/src/main/kotlin/com/tanuki/runtime/SshTransport.kt` | 新增 JSch 直连 SSH、首次主机公钥探测、指纹比对、本地端口转发、远程 DSH HTTP 探测 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteHostController.kt` | 新增连接测试/保存连接/首次确认/断开/清除流程与中文错误映射、隧道存活监控 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeManager.kt` | origin/authenticatedUrl 改由状态机提供；本地端点单独保存；本地健康检查绑定本地端点；远程失败回退 |
| `app/src/main/kotlin/com/tanuki/runtime/State.kt` | `Snapshot` 增加远程状态 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 设置入口、DSH 页远程状态/切换/回退提示、WebView 连接条件 |
| `app/src/main/kotlin/com/tanuki/ui/RemoteHostSettings.kt` | 新增远程主机设置页、连接测试/保存/断开/清除、首次主机密钥确认对话框 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 接线远程控制器状态与回调 |
| `app/src/main/kotlin/com/tanuki/runtime/DshCookieIsolation.kt` | Review P1：管理页 Cookie 按逻辑主机分区，切换前清空/恢复，失败时页面不可用 |
| `app/src/main/kotlin/com/tanuki/web/DshConnectionPolicy.kt` | Review P2：认证校验/token 重试绑定活动端点，不再要求本地 ServiceState.RUNNING |
| `app/src/test/kotlin/com/tanuki/RemoteHostTest.kt` | 新增 24 项 JVM 回归 |
| `app/src/test/kotlin/com/tanuki/DshRemoteIsolationTest.kt` | 新增 Cookie 分区与活动端点认证 4 项 |
| `app/src/androidTest/kotlin/com/tanuki/DshCookieIsolationTest.kt` | 真实 CookieManager 两合成主机隔离回归（仅编译） |
| `app/src/androidTest/kotlin/com/tanuki/RemoteHostKeystoreTest.kt` | Android Keystore provider 生成 IV 的回归（仅编译） |
| `app/build.gradle.kts` | 新增 `com.github.mwiede:jsch:2.28.8` |
| `app/src/androidTest/kotlin/com/tanuki/{BrowserStateTest,DshFileUploadTest,WebAuthenticationTest}.kt` | Review P2：夹具改用 `attachLocalEndpointForTesting`，移除 origin/authenticatedUrl 反射 |
| `docs/REMOTE-HOST.md`、`docs/INTERFACES.md`、`docs/SECURITY.md` | 补充 Cookie 隔离、provider 生成 IV 与活动端点认证说明 |
| `THIRD_PARTY_NOTICES`、`licenses/JSch-*`、`licenses/ANDROID-DEPENDENCIES.md`、`licenses/android-runtime-dependencies.json`、`licenses/android-runtime-notices/*` | JSch 与当前 91 个运行时依赖的许可记录 |
| `scripts/collect-component-licenses.py` | 允许从 AO worktree 的 `GRADLE_USER_HOME` 读取 POM，避免误报“POM 未声明” |
| `docs/INTERFACES.md`、`docs/SECURITY.md`、`docs/REMOTE-HOST.md`、`docs/TEST-REPORT.md` | 远程主机契约、安全边界、使用说明与验证记录 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin` | BUILD SUCCESSFUL | 首次构建含缓存准备约 7m34s；仅编译 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.RemoteHostTest'` | BUILD SUCCESSFUL，24 tests / 0 failures / 0 errors / 0 skipped | 新增类；JVM mock SSH |
| `./gradlew :app:assembleModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL，D8 打包 JSch | 未重新构建/验证原生 `.so`；APK 不用于设备验收 |
| `./gradlew :app:testModernDebugUnitTest` | 远程分支 BUILD SUCCESSFUL，37 结果文件，229/0/0/0；合并 `main=5fd1ab4` 后 41 结果文件，289/0/0/0；review 修复后 42 结果文件，293/0/0/0；合入冷启动后 47 结果文件，334/0/0/0；第二轮 P1 修复后 337/0/0/0；合入 PDF 后 396/0/0/0；合入原生界面后 424/0/0/0 | 合并后包含安全模式 32 项 + XLSX 28 项 + 远程 24 项 + Cookie 隔离 4 项及既有 205 |
| `./gradlew -PtestBuildType=debug :app:compileModernDebugAndroidTestKotlin` | BUILD SUCCESSFUL | 新增 Cookie 隔离/Keystore 设备回归仅编译，未运行 |
| `./gradlew :app:lintModernDebug` | BUILD SUCCESSFUL，0 errors / 80 warnings / 1 hint | 新增文件无告警；既有告警未处理 |
| `./gradlew -I scripts/dependency-inventory.init.gradle tanukiDependencyInventory` + `python3 scripts/collect-component-licenses.py` | 91 runtime artifacts，5 个仍无 POM 许可声明 | JSch 为 Revised BSD / JZlib Revised BSD / jBCrypt ISC；清单位于 `licenses/` |
| PR [#29](https://github.com/leezzou/tanuki/pull/29) CI（head `9dbaacc`） | Host scripts 与 Android build/JVM/lint 均 success | 公开 Actions；不替代独立 Review 或设备验收 |

- 哪些是前一轮结果：229 中的既有 205 项来自此前 #19/#20 组合；本轮新增 24 项并复跑完整套件。
- 未测试/失败/暂未复现：真实 sshd/设备/Keystore/token、Ed25519、断线重连、UI 操作、仪器、真机；未做设备安装。

## 未完成与暂停项

- 未完成：review 修复后的独立复审、合并与发布授权；真实设备/主机验收；Ed25519；中继/WebRTC；远程 DSH 启停/多主机；自动重连。
- 外部阻塞：无（缺少真实 SSH 主机/设备，属于未验证而非代码阻塞）。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收继续按既有要求等待授权/恢复。
- 不代合、不触发 AO Review、不创建 tag/Release。

## 下一步

1. PR [#29](https://github.com/leezzou/tanuki/pull/29)：base `main`，已推送；head 以 PR 页面为准，不触发 Review。
2. PR 正文已写明本次改动、验证命令、未验证项（真实 SSH/设备/Ed25519）与后续中继/WebRTC。
3. 由 Orchestrator 触发当前完整 SHA 的独立 Codex High Review；设备/真实主机 QA 另行授权。
4. Review 意见逐条修复并重审；用户明确授权后才合并。

## 约束与风险

- 数据/设备/权限边界：未安装/启动 App，未操作设备，未读取真实凭据/日志；所有测试使用合成主机、合成 key/token 和内存存储。
- 关键决策及理由：选 JSch 2.28.8（纯 Java、无传递依赖、约 700KB、Revised BSD）；选 AES-GCM + AndroidKeyStore 而不是明文/普通 SharedPreferences；远程 origin 仍走 loopback，避免放宽 `OriginPolicy`；首次 known_hosts 确认前不发送认证信息；本切片明确 RSA/ECDSA，Ed25519 留后续。
- 第二轮 P1 复审修复：管理页 Cookie 删除固定从主 Looper 发起、IO 等待回调后再 flush；Cookie 写入使用独立串行锁、UI `currentScope()` 只读 volatile，不再持主线程读取所需 monitor；新增主线程持续读取与切换并发的设备回归；连接远程 B 前先切到本地 Cookie 作用域，成功后才撤销 A 端点，测试连接取消保留 A；新增 `cookieReady` 门禁，WebView 加载/认证重试/HTTP 下载/本地端点发布在 Cookie 作用域不一致时拒绝，本地启动失败快速中止。
- 失败尝试与避免重复踩坑：最初 JVM 测试使用 `com.sun.net.httpserver`，Android 单元测试 bootclasspath 不提供，改为 `ServerSocket` 合成 HTTP；`RemoteHostController` 在配置校验失败时曾会误关活跃远程隧道，已改为不触发回退并加回归；`SharedPreferences` 显式 `commit()` 需要检查返回值，保留同步写入并豁免 UseKtx 风格告警；清除配置失败时保留已保存状态并报错，不谎称已清除；SSH 算法协商失败（Ed25519 等）给出明确中文提示；私钥解析失败与连接失败共用会话清理路径。

## 产物与校验

- PR [#29](https://github.com/leezzou/tanuki/pull/29)，base `main`，head 以 PR 页面为准。
- 未生成 APK/ZIP 交付附件；本轮只有源码与文档，不更新 0.1.8 六附件、不创建 tag/Release。
- 依赖许可：`licenses/JSch-BSD.txt`、`JSch-JZlib.txt`、`JSch-jBCrypt.txt`，以及刷新后的 `ANDROID-DEPENDENCIES.md`/`android-runtime-dependencies.json`/`android-runtime-notices`。
- 提交/推送前清理的过期交接：本轮未删除旧记录；旧记录包含历史验证/未完成事项，暂不判定过期。新记录保留；`CURRENT.json` 由检查脚本刷新。
- 仍需保留的验证/未完成记录：`20261109-1539-dsh-installed-version-manager.md`、`20261109-1539-dsh-install-resilience.md`、`20261109-1535-background-update-check.md`。
- 文档/脚本变化后仍需刷新交接印章、重新生成源码 ZIP；不把源码 ZIP 自身哈希写回本记录。
