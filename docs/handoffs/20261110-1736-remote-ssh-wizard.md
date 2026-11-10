# 交接：20261110-1736-remote-ssh-wizard

- Agent/会话标识：android_agent-55
- 时间：2026-11-10 17:36（UTC+8）
- 状态：后续增量已完成（PR [#57](https://github.com/leezzou/tanuki/pull/57)；MainActivity 保留 #52 接线，相对当前 main 零 diff，待 51 接线落地后单独迁移）
- 前一记录：[0.1.11 发布准备与交接集成](20261110-1636-release-0.1.11.md)
- 前置 PR：[#52](https://github.com/leezzou/tanuki/pull/52) 已合并（head `7a13988`）。
- 后续 PR：[#57](https://github.com/leezzou/tanuki/pull/57)（MainActivity 回退 + 6 位码 + 主机 chip hook）。

> 每任 Agent 创建唯一记录，不覆盖前任。普通 PR 只新增本记录，**不要改 `docs/AGENT-STATE.md` 或 `docs/handoffs/CURRENT.json`**；共享状态由集成维护方统一刷新。开始填写目标/范围；结束或中断前补齐证据、未测试范围与可执行下一步。只读 Agent 交回同格式文本，由主 Agent 保存。不要包含真实 Key、Cookie、token、会话/备份或私有原始日志。

## 用户目标与范围

- 用户本次实际要求：把「远程主机/SSH 连接」的用户引导重做为普通用户能自己连上的分步向导；包含连接方式选择、主机/账号、认证（密码/私钥文件与口令）、首次 SHA256 指纹确认、分阶段测试连接、完成保存；错误按阶段给中文原因与下一步建议并支持重试；保存连接可命名/编辑/删除；切换远程/本地显示当前状态与影响。
- 本次包含：六步向导状态机与字段校验、阶段化错误映射、命名连接库、控制器阶段进度、Compose 设置页与系统私钥文件选择器、配对链接 + 6 位核对码、主机切换 chip/sheet 组件与 hook 清单、JVM/Python 回归、专题文档。
- 本次不包含：合并、发布、tag/Release、真实设备/真实远程主机的端到端验收、Ed25519、WebRTC/TURN、P1 相机扫码（CameraX + ZXing 留后续）、关闭或放宽 TLS/主机密钥校验。
- 用户改变或暂停的要求（需保留）：API Key 保存、真实模型/账号验收、真机验收仍暂停；未获授权不得合并或发布。
- 基线：`origin/main=4f2a3717d6d795fd6b94cdb695878d8119f818c1`。分支：`ao/remote-ssh-wizard/guided-connect`（从 `ao/remote-ssh-wizard/root`/`origin/main` 切出）。

## 当前状态

- 已实现：六步向导（方式 → 主机账号 → 认证 → 指纹 → 测试 → 完成）；字段级中文校验与示例；阶段化错误（TCP/SSH 握手/认证/端口转发/DSH 探测）+ 下一步建议 + 重试；首次 SHA256 指纹必须显式勾选；命名连接新增/编辑/删除与“上次使用”；密码/私钥/口令/token 继续走 Android Keystore AES-256-GCM；远程/本地状态与切换影响提示；私钥系统文件选择器（64 KiB 上限）。
- 已验证：Kotlin 编译、完整 JVM 927 tests / 101 文件 / 0 失败、lint 0 errors / 103 warnings / 1 hint；交接/上传检查待提交前最后运行。
- 待验证：真实 Android 设备/模拟器 UI、系统文件选择器、真实 sshd/中继/DSH、Android Keystore 真机、弱网/断线、深色/大字体。
- 当前目标包名：`com.tanuki`（本轮未构建 APK，不涉及安装包）。
- 关键事实与可定位源码：向导与校验 `runtime/RemoteHostWizard.kt`；阶段模型/错误映射 `runtime/RemoteConnectionError.kt`；命名连接 `runtime/RemoteHostProfiles.kt`；控制器 `runtime/RemoteHostController.kt`；阶段回调 `runtime/SshTransport.kt`、`runtime/RelayTransport.kt`；UI `ui/RemoteHostSettings.kt`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/runtime/RemoteHostWizard.kt` | 新增连接方式/六步状态机、草稿脱敏、字段校验、示例、指纹确认、阶段状态与重试规则 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteConnectionError.kt` | 新增阶段枚举、失败分类和中文原因/建议/可重试映射；不使用底层异常原文 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteHostProfiles.kt` | 新增命名连接库：最多 50 条，新增/编辑/删除/活动标记，凭据仅加密保存 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteHostController.kt` | 新增阶段进度、结构化失败、命名连接读写/删除/连接；保留严格主机密钥校验、Cookie 隔离和失败回退 |
| `app/src/main/kotlin/com/tanuki/runtime/SshTransport.kt` | 新增可选 `StagedSshTransport`；JSch 在 TCP/主机密钥/认证/转发节点回报阶段 |
| `app/src/main/kotlin/com/tanuki/runtime/RelayTransport.kt` | 中继与路由传输实现阶段回调，复用同一套 JSch 校验 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeManager.kt` | 注入命名连接库 |
| `app/src/main/kotlin/com/tanuki/ui/RemoteHostSettings.kt` | 重做设置页：状态/影响、命名连接列表、全屏六步向导、阶段进度、失败面板、私钥文件选择与口令 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 保留命名连接、阶段、失败、attempt 配置等状态字段与回调；MainActivity 保留 #52 接线（相对 main 零 diff），51 迁移见 hook 清单 |
| `app/src/main/kotlin/com/tanuki/runtime/RemotePairingCode.kt` | 新增配对链接的 6 位人工核对码（SHA-256 前 8 字节 mod 1e6），与 Python 参考主机端共享向量 |
| `app/src/main/kotlin/com/tanuki/ui/RemoteHostSettings.kt` 中的 `RemoteHostSwitchChip`/`RemoteHostSwitchSheet` | 提供给 DshNativePage 头部的主机切换组件与 sheet；本 PR 不接线，见 hook 清单 |
| `scripts/remote-relay/protocol.py`、`host_agent.py`、`test_remote_relay.py` | 主机端输出 6 位配对码并新增共享向量回归 |
| `app/src/test/kotlin/com/tanuki/RemoteWizardTest.kt` | 新增 40 项 JVM 回归：状态机、校验、错误映射、命名连接、控制器阶段、来源 profile ID 清理、迟到私钥读取、重试归属、6 位码、chip 展示 |
| `docs/REMOTE-HOST.md`、`docs/REMOTE-CONNECT.md`、`docs/INTERFACES.md` | 同步向导流程、阶段错误语义与命名连接契约；按普通 PR 规则不修改 `docs/TEST-REPORT.md` |
| `docs/handoffs/20261110-1736-remote-ssh-wizard.md` | 本轮唯一交接记录；未改 `AGENT-STATE.md`/`CURRENT.json` |

## 给 51 的接线 hook（当前保留 #52 接线）

当前 `MainActivity.kt` 保留 #52 已合并的 12 行向导入口接线，本 PR 相对 main 零 diff，因此设置页向导仍可用。以下字段/组件供 51 迁移接线；等 51 的接线合并后，再单独小 PR 删除这 12 行重复入口。

### 1. TanukiUiModel（MainActivity 构造 `TanukiUiModel(...)` 处）

```kotlin
remoteProfiles = remoteHost.profiles,
remoteActiveProfileId = remoteHost.activeProfileId,
remoteStage = remoteHost.stage,
remoteCompletedStages = remoteHost.completedStages,
remoteFailure = remoteHost.failure,
remoteAttemptSucceeded = remoteHost.attemptSucceeded,
remoteLocalPort = remoteHost.localPort,
remoteAttemptConfig = remoteHost.attemptConfig,
remoteAttemptProfileId = remoteHost.attemptProfileId,
```

用途：向导连接列表、阶段进度、失败面板、测试完成判断与“重试/返回修改”归属。不接时这些字段取默认空值，向导仍能编译但看不到进度/列表。

### 2. TanukiUiCallbacks（MainActivity 构造 `TanukiUiCallbacks(...)` 处）

```kotlin
saveRemoteProfile = manager.remoteHost::saveProfile,
deleteRemoteProfile = manager.remoteHost::deleteProfile,
connectRemoteProfile = manager.remoteHost::connectProfile,
```

签名：
- `saveRemoteProfile: (String, RemoteHostConfig, String?, Boolean) -> Unit`（名称、配置、编辑 id、保存后是否连接）
- `deleteRemoteProfile: (String) -> Unit`
- `connectRemoteProfile: (RemoteHostProfile) -> Unit`

### 3. DshNativePage 头部 chip

```kotlin
RemoteHostSwitchChip(
    status = model.remote,
    localRunning = model.snapshot.service == ServiceState.RUNNING,
    onClick = { showRemoteHostSheet = true }
)
```

`RemoteHostSwitchChip` 位于 `com.tanuki.ui`（`RemoteHostSettings.kt`），签名：
`(status: RemoteHostStatus, localRunning: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)`。
展示映射由 `RemoteHostSwitchPresentation.of(...)` 提供：`本机·运行中` / `本机·未运行` / `电脑·已连接` / `切换中…` / `本机·已回退`；状态必须来自 `model.remote`，不能用上次成功缓存冒充。

### 4. 页面根部 sheet

```kotlin
var showRemoteHostSheet by rememberSaveable { mutableStateOf(false) }
if (showRemoteHostSheet) {
    RemoteHostSwitchSheet(
        status = model.remote,
        localRunning = model.snapshot.service == ServiceState.RUNNING,
        profiles = model.remoteProfiles,
        onDismiss = { showRemoteHostSheet = false },
        onSelectLocal = { c.disconnectRemoteHost() },
        onSelectProfile = { profile -> c.connectRemoteProfile(profile) },
        onRestorePrevious = {
            model.remoteHost?.let(c::connectRemoteHost) ?: c.disconnectRemoteHost()
        }
    )
}
```

生命周期：
- `showRemoteHostSheet` 是页面级 `rememberSaveable`，旋转/重组保留；sheet 关闭时置 false。
- `status`/`profiles` 每次重组从 `model` 读取，不缓存；切换中 `canSwitch=false`，避免并发切换。
- 切换失败时控制器进入 `FALLBACK`，`model.remote.error` 非空；sheet 显示失败原因与“返回原主机”。`onRestorePrevious` 使用 `model.remoteHost`（上一次成功配置）重连，因为控制器在切换开始时已关闭旧隧道；若没有可回退配置则断开回本地。
- 不在 sheet 内直接调用 SSH/存储；只调 `TanukiUiCallbacks`，保持 Controller 唯一执行者。

### 5. P1 相机扫码计划（本 PR 未实现）

- 依赖：CameraX（`androidx.camera:camera-camera2`、`camera-lifecycle`、`camera-view`）+ `com.google.zxing:core`（Apache-2.0，纯 Java 离线解码）；不引入 Google Play 服务。
- 接入时必须记录：库名/版本/许可证、APK 体积增量、CameraX 生命周期绑定、相机权限被拒时的降级路径（直接回到“粘贴链接 + 6 位码”手输，不卡扫码页）。
- P0 当前只做“配对链接 + 6 位码”，无相机、无新依赖。

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin -x buildNativeRuntime` | 通过（BUILD SUCCESSFUL） | JDK17、Android SDK、macOS ARM64；仅编译 |
| `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:testModernDebugUnitTest -x buildNativeRuntime` | 通过：**927 tests / 101 个结果文件 / 0 failures / 0 errors / 0 skipped**（1m4s） | 含新增 `RemoteWizardTest` 40 项；既有 `RemoteHostTest`/`RemoteRelayTest`/`DshRemoteIsolationTest` 通过 |
| 同上，单独 `--tests com.tanuki.RemoteWizardTest` | 通过：26 tests / 0 failures | 新增回归定向结果 |
| `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:lintModernDebug -x buildNativeRuntime` | 通过：**0 errors / 102 warnings / 1 hint**（3m58s） | 仅 JVM/lint，不含设备 |
| 最终自查修复（完成页空配置、隧道断开/存储错误不再误报阶段）后重跑 JVM + lint | 通过：820 tests / 0 failures；lint 0 errors / 102 warnings / 1 hint | 最终 head `c017488`；本轮修复提交也附带本交接记录更新 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1736-remote-ssh-wizard.md` | 通过 | 普通 PR 模式 |
| `python3 scripts/check-publication.py --git` | 通过：677 public files / 672 index entries / 0 findings | 上传检查 |
| 评审修复与后续增量：4 个 P2、6 位码、chip 映射、MainActivity 回退 | 通过：927 tests / 0 failures；lint 0 errors / 103 warnings / 1 hint | 合并最新 main 后复验；MainActivity 保留接线、相对 main 零 diff |
| 第三轮 P2：活动副本按 sourceProfileId 归属；迟到私钥读取按请求版本丢弃 | 通过：924 tests / 0 failures | 编辑目标后删除、同服务双 profile、切换认证/进入测试后迟到回调均有回归 |
| 最终 P1/P2：保留 #52 向导接线；host_agent 默认系统 CA 路径先配 INFO 日志 | 通过：MainActivity 相对 main 零 diff；Python 9 tests | 设置页向导入口不消失；配对码在默认路径可见 |
| MainActivity 接线恢复提交 | `822ad6f`：保留 12 行，相对 main 零 diff | 设置页向导入口在 51 迁移前保持可用 |
| 控制器→向导状态回归 | 通过：`RemoteWizardTest` 40 项；成功进入 DONE、失败退出 busy 并停 AUTHENTICATION | 覆盖 MainActivity 真实模型接线后的成功/失败路径 |
| 公开重连来源保留 P2 | 通过：`RemoteWizardTest` 40 项 | `connect(config)` 重连/重试恢复 sourceProfileId；新手工配置不继承；编辑目标后删除仍清理 |
| `python3 scripts/remote-relay/test_remote_relay.py` | 通过：9 tests（含 6 位码共享向量 308002、默认系统 CA 路径配对码输出） | Python 3.9 标准库回环 |
| `git diff origin/main -- app/src/main/kotlin/com/tanuki/MainActivity.kt` | 空（MainActivity 相对当前 main 零 diff） | 保留 #52 接线；51 迁移后单独删除重复入口 |

- 哪些是前一轮结果，哪些是本次新验证：以上编译/JVM/lint 均为本次新验证；前一轮 0.1.11 的 794 tests 不应与新数字混算。
- 未测试、超时、失败和暂未复现的情况：`NpmAdaptiveWorkerPoolTest` 在首次完整套件和单独复跑时曾出现一次 8/9 并发时序失败；随后完整套件通过，未修改该测试，不能据此宣称该测试稳定。真实设备/真实 SSH/中继/DSH、系统文件选择器、Keystore 真机、弱网/断线、深色/大字体均未执行。

## 未完成与暂停项

- 未完成：独立 Codex High 审查、CI、适用设备 QA。分支已推送，PR [#52](https://github.com/leezzou/tanuki/pull/52) 已创建。
- 外部阻塞：无（设备与真实主机验收需要独立 QA 和明确目标）。
- 用户暂停：合并/发布、真机/真实账号验收、API Key 保存。

## 下一步

1. 提交并推送 `ao/remote-ssh-wizard/finalize`，PR [#57](https://github.com/leezzou/tanuki/pull/57)（目标 `main`）已包含改动、924 tests、lint、未验证项与不合并/不发布。
2. 由独立 Codex High 对 PR 当前完整 SHA 审查；处理意见后对新 SHA 重跑 `:app:testModernDebugUnitTest` 与 `:app:lintModernDebug`。
3. 设备 QA 需要独立会话：验证六步向导、私钥系统文件选择器、指纹勾选、阶段进度、失败重试、命名连接增删改、远程/本地切换提示；需要真实 sshd/中继/DSH 环境。
4. 未获明确授权不合并、不打 tag、不创建 Release、不发布 APK/源码 ZIP。

## 约束与风险

- 数据/设备/权限边界：未使用真实凭据；未关闭主机密钥/TLS 校验；未引入“信任未知主机”默认；私钥/口令/token 未落明文；未操作设备。
- 关键决策及理由：
  - 向导状态机保持纯 Kotlin，Compose 只做展示与副作用，便于 JVM 回归。
  - 阶段回调做成可选接口 `StagedSshTransport`，不破坏既有测试假传输；控制器对未实现回调的传输退化为粗粒度。
  - 命名连接库独立于当前活动配置存储，避免改动既有 `RemoteHostStorage` 兼容键；同一时间仍只有一个活动远程连接。
  - 测试连接继续不切换 origin、不保存配置；只有“保存并连接”才持久化并切换管理页。
- 失败尝试与避免重复踩坑的结论：
  - 共享 `.git` 在工作区外，Git 写引用/Gradle 编译需要一次完整访问权限；未修改全局 Java，复用主检出 `.build/jdk` 的 JDK17。
  - `NpmAdaptiveWorkerPoolTest` 存在既有并发时序抖动，本轮未修改；完整套件通过后才记录 927/0，不隐瞒首次失败。
  - Compose 中函数类型回调不能用 `c::connectRemoteHost` 直接引用，已改为显式 lambda。

## 产物与校验

- 新/旧APK、源码ZIP及校验文件，哪些确实更新：本轮不生成 APK/源码 ZIP。
- 是否覆盖安装 / 签名范围 / 未发布事项：不安装、不发布、不创建 tag/Release。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮未删除任何既有交接；0.1.11 记录仍是当前集成记录，由集成维护方统一刷新共享状态。
- 普通 PR 不刷新共享状态/印章；集成维护方在批量合并后统一运行 `--integrate`。文档或脚本变化后重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。

结束前：

```bash
# 普通 PR：只校验本记录，共享状态允许落后
python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1736-remote-ssh-wizard.md
# 集成/发布由集成维护方执行，普通 PR 不运行写印章的命令
python3 scripts/package-source.py
```
