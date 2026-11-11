# 研发与发布历史事实

本专题首批70份记录由 0.1.14 发布维护迁移，基线 `3ba8ef1fc2ee4aa45347b50677b0c395aa6a88b6`。下文按原记录编号保留实现、验证命令、失败、关键决策、产物身份及待验事项；各节中的“当前”、待推送/待合并和授权描述只属于记录当时。这些实现/文档 PR 已进入上述基线；产品现状以源码、专题及 [当前状态](AGENT-STATE.md) 为准。历史 JVM 数字与 QA 不等于 0.1.14 已测；原命令中的旧 handoff 路径仅用于保存当时的验证证据，不作为现在的执行入口。

0.1.10/code12 是未发布历史候选；旧记录中的发布计划不代表已发布。0.1.11/code13 的已发布事实保留；0.1.12/code14、0.1.13/code18 的包与测试分别保留原基线，不能用来替代本次 rebuild。

独立设备证据继续保留在 [八旅程 QA](handoffs/20261111-1216-device-qa.md) 和 [0.1.14 视觉 QA](handoffs/20261111-2108-visual-qa-0.1.14.md)。未复验的缺陷与真实运行链路不因归档关闭。

## 索引

- [20261106-1357-android-qa-readiness-audit](#20261106-1357-android-qa-readiness-audit)
- [20261106-1415-governance-gates](#20261106-1415-governance-gates)
- [20261106-1647-workspace-file-preview](#20261106-1647-workspace-file-preview)
- [20261106-1735-video-fullscreen-full-sensor](#20261106-1735-video-fullscreen-full-sensor)
- [20261109-1535-background-update-check](#20261109-1535-background-update-check)
- [20261109-1539-dsh-install-resilience](#20261109-1539-dsh-install-resilience)
- [20261109-1539-dsh-installed-version-manager](#20261109-1539-dsh-installed-version-manager)
- [20261109-1700-dsh-cold-start](#20261109-1700-dsh-cold-start)
- [20261109-1742-dsh-chat-notifications](#20261109-1742-dsh-chat-notifications)
- [20261109-1802-device-condition-policy](#20261109-1802-device-condition-policy)
- [20261109-1807-xlsx-native-preview](#20261109-1807-xlsx-native-preview)
- [20261109-1810-remote-host-ssh](#20261109-1810-remote-host-ssh)
- [20261109-1812-runtime-safe-mode](#20261109-1812-runtime-safe-mode)
- [20261109-1819-pdf-native-reading](#20261109-1819-pdf-native-reading)
- [20261109-1829-native-ui-switch](#20261109-1829-native-ui-switch)
- [20261109-1836-docx-native-preview](#20261109-1836-docx-native-preview)
- [20261109-2310-release-0.1.10](#20261109-2310-release-0.1.10)
- [20261110-0330-copy-flicker](#20261110-0330-copy-flicker)
- [20261110-0418-saf-direct-open](#20261110-0418-saf-direct-open)
- [20261110-1133-design-tokens](#20261110-1133-design-tokens)
- [20261110-1138-remote-relay-pairing](#20261110-1138-remote-relay-pairing)
- [20261110-1139-native-approval-tools](#20261110-1139-native-approval-tools)
- [20261110-1140-native-parity-audit](#20261110-1140-native-parity-audit)
- [20261110-1142-dsh-version-uninstall](#20261110-1142-dsh-version-uninstall)
- [20261110-1142-file-viewer-formats](#20261110-1142-file-viewer-formats)
- [20261110-1142-input-attachments](#20261110-1142-input-attachments)
- [20261110-1226-native-parity-non-chat](#20261110-1226-native-parity-non-chat)
- [20261110-1233-native-ui-chat](#20261110-1233-native-ui-chat)
- [20261110-1333-handoff-pr-mode](#20261110-1333-handoff-pr-mode)
- [20261110-1636-release-0.1.11](#20261110-1636-release-0.1.11)
- [20261110-1736-remote-ssh-wizard](#20261110-1736-remote-ssh-wizard)
- [20261110-1737-composer-input-polish](#20261110-1737-composer-input-polish)
- [20261110-1737-session-settings](#20261110-1737-session-settings)
- [20261110-1745-native-ui-polish](#20261110-1745-native-ui-polish)
- [20261110-1754-native-approval-ui](#20261110-1754-native-approval-ui)
- [20261110-1802-native-chat-render](#20261110-1802-native-chat-render)
- [20261110-1824-text-encoding-fallback](#20261110-1824-text-encoding-fallback)
- [20261110-1935-pr47-followup](#20261110-1935-pr47-followup)
- [20261110-1936-files-layout](#20261110-1936-files-layout)
- [20261111-0314-goal-todo-dock](#20261111-0314-goal-todo-dock)
- [20261111-0325-release-0.1.12](#20261111-0325-release-0.1.12)
- [20261111-1007-chat-anchored-history](#20261111-1007-chat-anchored-history)
- [20261111-1008-token-page-integration](#20261111-1008-token-page-integration)
- [20261111-1017-remote-relay-quickstart](#20261111-1017-remote-relay-quickstart)
- [20261111-1024-mobile-pairing-auto](#20261111-1024-mobile-pairing-auto)
- [20261111-1049-first-run-onboarding](#20261111-1049-first-run-onboarding)
- [20261111-1147-mdns-discovery](#20261111-1147-mdns-discovery)
- [20261111-1213-quickstart-30s](#20261111-1213-quickstart-30s)
- [20261111-1228-failure-exit](#20261111-1228-failure-exit)
- [20261111-1246-dock-integration](#20261111-1246-dock-integration)
- [20261111-1247-file-import-transfer](#20261111-1247-file-import-transfer)
- [20261111-1300-lan-discovery-ui](#20261111-1300-lan-discovery-ui)
- [20261111-1425-landscape-copy-result](#20261111-1425-landscape-copy-result)
- [20261111-1442-qa10-qa07](#20261111-1442-qa10-qa07)
- [20261111-1456-text-page-boundary](#20261111-1456-text-page-boundary)
- [20261111-1635-update-readonly-followup](#20261111-1635-update-readonly-followup)
- [20261111-1643-release-0.1.13](#20261111-1643-release-0.1.13)
- [20261111-1703-release-order](#20261111-1703-release-order)
- [20261111-1730-motion-batch-a](#20261111-1730-motion-batch-a)
- [20261111-1750-qa06-qa07-pagination](#20261111-1750-qa06-qa07-pagination)
- [20261111-1802-handoff-cleanup](#20261111-1802-handoff-cleanup)
- [20261111-1819-sky-theme-landing](#20261111-1819-sky-theme-landing)
- [20261111-1848-copy-p0-core](#20261111-1848-copy-p0-core)
- [20261111-1854-settings-copy](#20261111-1854-settings-copy)
- [20261111-1900-motion-batch-b](#20261111-1900-motion-batch-b)
- [20261111-1900-motion-batch-f](#20261111-1900-motion-batch-f)
- [20261111-1915-chat-redesign-v2](#20261111-1915-chat-redesign-v2)
- [20261111-2030-motion-batch-e](#20261111-2030-motion-batch-e)
- [20261111-2043-motion-batch-d](#20261111-2043-motion-batch-d)
- [20261111-2200-progress-layout-test](#20261111-2200-progress-layout-test)

<a id="20261106-1357-android-qa-readiness-audit"></a>

## 20261106-1357-android-qa-readiness-audit

- Agent/会话标识：android_agent-8（DSH QA 能力核查 Worker，DeepSeek V4.1 Flash）
- 时间：2026-11-06 13:57 CST（UTC 05:57）；实施与验证完成于同日

### 当前状态

- 已实现：
  - `scripts/device-preflight.py`：只读门禁，校验 APK SHA-256、包名、versionCode/versionName、应用/测试签名一致、arm64-v8a、`libproot.so`/`libproot-loader.so`/`libsupervisor.so`、设备 API/ABI、WebView 最低主版本（默认 123）、apt/dpkg/Node/PRoot/supervisor 进程、活动安装会话；`--phase post` 确认 `install -r` 后实际生效版本；证据写私有 `.build/logs/device-preflight-*.json`。
  - `scripts/verify-workspace.sh`：设备路径在 install 前执行 pre 门禁、install 后执行 post 版本确认；新增 `--expect-apk-sha256`、`--expect-cert-sha256`、`--min-webview-major`；失败即中止，不安装。
  - `scripts/device-lock.py`：按 serial 的 `flock` 进程锁，覆盖构建→预检→install→instrument 全程；同 serial 第二任务退出 3 并报告持有者，不同 serial 独立；锁 fd 传给子任务，父进程意外退出时仍由存活子任务持有。
  - `scripts/test-agent-workflow.py`：mock ADB/apkanalyzer/apksigner 覆盖新门禁与失败场景；新增同 serial 拒绝、不同 serial 独立、失败/父进程死亡释放、`verify-workspace.sh` 串行化回归。
  - 复审修复（P1）：androidTest manifest 不继承应用 versionCode/versionName，测试 APK 改为允许缺失/独立版本并分别记录；API34 活动安装段兼容 `Active Session`/`Active Child Session` 前缀，mock 使用真实样例。
- 已验证（本轮实际执行）：
  - `python3 scripts/test-agent-workflow.py` → **19 tests OK（76.760s）**。
  - `bash -n scripts/verify-workspace.sh`、`python3 scripts/device-preflight.py --help`、`python3 scripts/device-lock.py --help` → 通过。
- 未验证：真实 APK/设备上的锁、pre/post 门禁、构建、安装、仪器、0.1.8 设备证据。
- 当前目标包名/分支：`com.tanuki.modern.workspacecheck`；分支 `audit/android-qa-readiness`，基线 `main=a9aa507c06883c62048a89924384ef0a10e01470`；无运行中构建/测试任务。
- 关键事实与定位：
  - 设备验收：`scripts/verify-workspace.sh`；新门禁：`scripts/device-preflight.py`；设备锁：`scripts/device-lock.py`。
  - 发布 APK 身份校验：`scripts/prepare-app-release.py`（apkanalyzer + apksigner）；签名：`scripts/sign-release.sh`。
  - 默认 debug 版本：`app/build.gradle.kts:16-17`（默认 `0.1.7/code9`）。
  - CI：`.github/workflows/verify.yml`、`docs/CI.md`。
  - 环境测试：`RuntimeFunctionalTest`、`UbuntuMinimalTest`、`DshInstallationRegressionTest`、`InstalledToolsSmokeTest`、`PhoneFileGuestClientTest`。
  - WebSocket 仅注释：`app/src/main/kotlin/com/tanuki/web/DshWebView.kt:101`。

#### 设备与 CI 只读证据（2026-11-06）

- main：本地 HEAD、`origin/main`、GitHub `main` 引用均为 `a9aa507c06883c62048a89924384ef0a10e01470`；PR #12 已合并。
- 公开 CI：[run 42163963031](https://github.com/leezzou/tanuki/actions/runs/42163963031)，`headSha=a9aa507…`，Host 10s、Android 7m56s，双绿。Android job 使用 `-x buildNativeRuntime`，仓库跟踪 `app/src/main/jniLibs` 为 0 个文件、目录不存在，CI APK 无原生 `.so`，不能用于设备验收。
- AVD：`Pixel_7_API_34_arm64`（运行中）、`Tanuki_012_Test_API34`（未启动）。
- 设备：`emulator-5572` = `Pixel_7_API_34_arm64`；API34/Android14/arm64-v8a；WebView `com.android.webview 159.0.8133.0`；已装 `com.tanuki.modern.workspacecheck 0.1.7/code9`（min26/target34，lastUpdate 2026-11-05 17:35:33），test 包存在。
- 忙碌检查：`ps -A -o NAME` 过滤未见 node/proot/apt/dpkg/supervisor；`pm list staged-sessions` 在 API34 可用且为空；`dumpsys package` 有 `Active install sessions:` 段可作回退。
- 数据边界：未读取 App 私有文件、userdata、Key、Cookie、token；因此“数据可测”未独立复核。安装/仪器前仍须由设备所有者确认无真实账号/项目数据，禁止 wipe/清数据。

#### 能力矩阵（静态核对，不等于验收通过）

| 能力 | 现有入口 | 结论 | 缺口 |
|---|---|---|---|
| APK 身份校验 | 新 `device-preflight.py`；`prepare-app-release.py`；`ApplicationUpdateNativeTest`（设备，非默认） | 门禁已实现（host 验证） | 真实 APK/设备未跑 |
| 保留数据 `install -r` | `verify-workspace.sh`；`ApplicationUpdateNativeTest#privateSyntheticFileSurvivesCoverInstall` | 未卸载/未清数据；post 校验版本 | 默认脚本不校验覆盖前后业务数据 |
| 生命周期/权限 | `ComponentLifecycleTest(4)`、`ComponentReinstallTest(2)`、`ProcessOwnershipTest(3)`、`RestoreJournalTest(1)`、`UpdateRecoveryTest(1)`、`PhoneStorageAccessTest(4)` | 存在，默认 14 类均不含 | 无自动入口/证据汇总 |
| PRoot+Ubuntu | `RuntimeProbeTest(1)`、`UbuntuMinimalTest(2)`、`RuntimeFunctionalTest(1)` | 手动可用 | 不在默认回归；`UbuntuMinimalTest` 会真实 dpkg/apt 并改配置 |
| Android 内 Node | `RuntimeFunctionalTest`、`InstalledToolsSmokeTest(2)`、`RuntimeFoundationToolsTest(默认1)`、`DshInstallationRegressionTest` | 部分 | 默认仅工具安装；Node/PTY/官方模块为手动 |
| 官方 DSH 启动 | `RuntimeFunctionalTest`（start/stop×2、HTTP 303+Cookie）、`DshInstallationRegressionTest`（冷装+web auth） | 手动 | 默认 `SetupPresentationTest` 是合成 HTML，不是真实 DSH |
| WebView HTTP/WS | HTTP：`AgentBrowserControlTest`/`BrowserIntegrationTest` loopback fixture；DSH auth 手动测试 | 部分 | **WebSocket 无测试**，仅代码注释 |
| Browser/Phone | Browser 在默认 14 类；Phone：`PhoneFileServiceTest(5)`、`PhoneFileAccessTest(5)`、`PhoneFileGuestClientTest(1)` | Browser 部分；Phone 手动 | Phone 全不在默认回归；Guest 需已装环境+宿主存储写授权 |
| 后台重启 | Activity recreate/home/resize；`ProcessOwnershipTest` 子进程跨线程存活 | 部分 | 无进程死亡/`am kill`/force-stop/重启端到端；DSH 启停仅手动 |
| 模型任务 | 无 | 未打通 | 0 自动化；`InstalledToolsSmokeTest` 仅合成凭据持久化（API Key 暂停中） |

#### 模拟器独占评估

- 现状：**已加进程级机器锁**（本轮）。`verify-workspace.sh` 在设备入口先经 `scripts/device-lock.py` 以 `flock` 持有 per-serial 锁，再重新执行自身；锁覆盖构建、pre/post 门禁、`install -r` 与仪器全程。同 serial 第二任务退出 3 并报告 `session/pid/childPid/since`；不同 serial 使用不同锁文件，可并行。
- 释放：正常结束、失败、Ctrl-C/SIGTERM 都会释放；锁 fd 通过 `pass_fds` 传给子任务，父进程被 SIGKILL 时只要子任务还存活，锁仍有效，直到子任务退出。不会为恢复锁终止他人进程或删除数据。
- 绕过边界：人工直接 `adb`、其他未接入脚本、手动设置 `TANUKI_DEVICE_LOCK_HELD=1` 均可绕过；锁只保障遵循 `verify-workspace.sh` 的任务，不是安全边界。`install-and-launch.sh`/`test-runtime.sh` 等历史入口尚未接入。
- 适用范围：同一主机/同一登录用户、所有 AO worktree/会话；不覆盖跨主机。

#### 历史执行 vs 本轮新核查 vs 未打通

- 历史已执行（`docs/TEST-REPORT.md`，未复跑）：0.1.7 release 52 项设备用例（含 `ApplicationUpdateNativeTest` 3 + `ApkInstallAccessTest` 5）；0.1.6 release 57 项；PR #8 22 项；多轮 debug 44/48/54 项与 JVM 104 项。
- 0.1.8：**没有设备测试报告**。
- 本轮新核查：main/CI 公开证据、AVD/ADB metadata、脚本/测试入口真实覆盖、独占机制、文档滞后。
- 尚未打通：APK 身份门禁的真实设备执行（本轮仅 host mock）、机器级设备租约、Phone Guest 自动化、WebSocket、进程死亡/后台重启、真实模型任务、0.1.8 设备证据。
- “Wave0”：仓库内无记录（`git log --all --grep=Wave` 与文档检索均无），不能把任何流程归为 Wave0。

#### 文档滞后

- `docs/AGENT-STATE.md` 原写 main=`9e3e316`、PR #12 待复审；实际 PR #12 已合并为 `a9aa507`。本记录已同步。
- 仓库不存在任务提到的 `ORCHESTRATOR-RULES`、`QA-GATES`、`LESSONS`、`code-standards`；`INTERFACES.md` 存在（Browser 17 / Phone 11 方法）。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `scripts/device-preflight.py` | 新增设备验收只读门禁：APK 哈希/包名/版本/签名/ABI/原生库、设备 API/ABI/WebView、忙碌进程/活动安装会话、post 版本确认、私有证据 JSON |
| `scripts/device-lock.py` | 新增 per-serial 进程级独占锁；锁 fd 传给子任务，覆盖完整设备任务区间 |
| `scripts/verify-workspace.sh` | 设备入口经锁重新执行；install 前 pre 门禁、install 后 post 版本确认；新增 `--expect-apk-sha256`、`--expect-cert-sha256`、`--min-webview-major`；失败不安装 |
| `scripts/test-agent-workflow.py` | mock ADB/apkanalyzer/apksigner 覆盖新门禁；新增签名/证书/降级/WebView/忙碌进程/安装会话/证据 pin/post 失败测试，以及并发锁/释放/串行化回归 |
| `docs/handoffs/20261106-1357-android-qa-readiness-audit.md` | 本记录 |
| `docs/AGENT-STATE.md` | 当前交接指向本记录，同步 main/PR #12 状态、本轮实现与未测范围 |
| `docs/handoffs/CURRENT.json` | 由 `check-handoff.py --record` 生成，不手写 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `python3 scripts/test-agent-workflow.py` | **19 tests OK（76.760s）** | 宿主 mock；覆盖门禁成功/失败/证据、测试 APK 无版本字段/独立版本、空活动段不误报、API34 Active/Child Session、同 serial 拒绝、不同 serial 独立、失败/父进程死亡释放、verify 串行化 |
| `bash -n scripts/verify-workspace.sh` | 通过 | 语法检查 |
| `python3 scripts/device-preflight.py --help` | 通过 | 模块可导入/入口正常 |
| `git rev-parse HEAD`、`gh api …/commits/a9aa507…`、`gh api …/git/ref/heads/main` | 均 `a9aa507c…` | 本地/远端一致 |
| `gh run view 42163963031` | success；Host 10s、Android 7m56s | 公开 CI；不含设备 |
| `git ls-files app/src/main/jniLibs`、`ls` | 0 个、目录不存在 | 证明 CI APK 无原生 `.so` |
| `emulator -list-avds` | `Pixel_7_API_34_arm64`、`Tanuki_012_Test_API34` | 未启动 AVD |
| `adb devices -l`、`adb -s emulator-5572 emu avd name` | `emulator-5572` = `Pixel_7_API_34_arm64` | 设备原已运行，未启动/停止 |
| `getprop ro.build.version.sdk/release/ro.product.cpu.abi` | 34 / 14 / arm64-v8a | 普通 metadata |
| `dumpsys webviewupdate` | `com.android.webview 159.0.8133.0` | 当时当前 WebView |
| `dumpsys package com.tanuki.modern.workspacecheck` | `0.1.7/code9`、min26/target34、lastUpdate 2026-11-05 17:35:33 | test 包存在 |
| `ps -A -o NAME` 过滤 | 无 node/proot/apt/dpkg/supervisor | 仅进程名，未输出 cmdline |
| `pm list staged-sessions`、`dumpsys package` 的 Active 段 | 空 | API34 可用；已实现回退解析 |
| `check-handoff.py --record` / 无参数检查 | 通过 | 记录 AGENT-STATE 与本记录后执行 |

- 哪些是前一轮结果：0.1.6/0.1.7/PR #8 的设备数字均为 `TEST-REPORT` 历史，未复跑。
- 未测试/失败/未复现：所有 Android 内执行未测试；真实 APK 未跑 pre/post；设备数据可测性未独立复核；0.1.8 设备证据缺失。

### 未完成与暂停项

- 未完成：真实设备上的 pre/post 门禁与串行化执行（本轮仅离线 mock）；`install-and-launch.sh`/`test-runtime.sh` 等历史入口未接入锁；Phone Guest/WS/进程死亡/后台重启/模型任务自动化；0.1.8 设备证据。
- 外部阻塞：无。
- 用户暂停：API Key 保存；真实模型/账号验收；真机验收。

### 下一步

1. 在已确认的专用 AVD 上，用 `bash scripts/verify-workspace.sh --run-device-tests --confirm-test-data --device <serial>` 实际跑一次锁、pre/post 门禁与 14 类回归；先确认无真实数据、无 apt/dpkg/安装/真实任务。设备当前由独立 QA 使用，需等其完成后再安排。
2. 如需覆盖历史入口，把 `device-lock.py` 接入 `test-runtime.sh`/`install-and-launch.sh`；人工直接 adb 始终绕过，文档不得宣称完全独占。
3. 为 WebSocket、Phone Guest、进程死亡/后台重启补 opt-in 用例；skip 必须记为未运行，不能计通过。
4. 0.1.8 设备证据另开验收任务，不能用历史数字或 CI APK 代替；真实模型/账号仍待用户恢复。

### 约束与风险

- 数据/设备/权限边界：未读私有文件、Key、Cookie、token；未安装/卸载/清数据/wipe/改设置；未启动或停止 App/模拟器。
- 关键决策及理由：门禁放在宿主脚本的 install 前后，只读 metadata；不把 CI 编译包、宿主机 mock 或历史设备数字当作 Android 验收。
- 失败尝试与避免重复踩坑：`pm list sessions` 在当前 Android 不可用，正确命令是 `pm list staged-sessions`；部分镜像仍可能返回非零，故保留 `dumpsys package` 的 Active install sessions 回退。`installd` 是系统常驻守护进程，不能据此判断在途安装。
- 风险：默认 debug 固定 `0.1.7/code9`；设备升级到 0.1.8/code10 后，默认 debug code9 覆盖安装会触发 `INSTALL_FAILED_VERSION_DOWNGRADE`，需传 `-PtanukiVersionCode`。默认 14 类含真实工具安装（`RuntimeFoundationToolsTest`），不得与其他安装/apt 并行；该用例与门禁的说明留待 W7 统一专题文档。
- 锁的边界：`flock` 只在遵循入口的任务间生效；若 helper 与子任务都被 SIGKILL，任务本身已终止、锁随 fd 关闭释放；若仅 helper 被 SIGKILL，子任务仍持锁。不会为恢复锁杀进程。
- 治理分支：本分支 `CURRENT.json` 已自洽；若 W7 治理分支先合并，最终合并方需在合并后重新 `check-handoff.py --record`，避免两个分支印章互相过期。
- 验证边界：本轮只做 host mock 回归；不能宣称设备门禁已在真实 APK/AVD 上通过。

### 产物与校验

- 新/旧 APK、源码 ZIP：**未更新**；未构建、未签名、未安装。
- 覆盖安装/签名范围/未发布事项：未安装、未签名、未发布；无 tag/Release。
- 交接清理：未删除旧记录；保留本记录、W1/W2/W3 记录、`TEMPLATE.md`、`CURRENT.json`。
- 本轮新增公开脚本会改变源码指纹；已用 `check-handoff.py --record` 刷新 `CURRENT.json`。源码 ZIP 待下一次发布/交付流程重打，本分支不进入 `artifacts/`。

<a id="20261106-1415-governance-gates"></a>

## 20261106-1415-governance-gates

- Agent/会话标识：android_agent-7（DSH 规范维护 Worker，DeepSeek V4.1 Flash）；PR #14 rebase 与复验：android_agent-13；仅测试确定性修复：android_agent-14
- 时间：2026-11-06 14:15 CST；rebase 复验于 2026-11-06 完成（最终 head 以 [PR #14](https://github.com/leezzou/tanuki/pull/14) 为准）

### 当前状态

- 已实现：上述治理文档、门禁脚本、离线回归、Host CI 接入和状态/交接同步；PR #14 已在独立 `/tmp` clone 中 rebase 到 `14d2884`。
- 已验证：门禁离线回归 24/24；真实 `gh`/`ao` 只读 CLI 格式核对（已合并 PR #12 被保守拒绝）；PR #14 rebase 后交接检查、公开候选检查、源码包刷新结果见“验证记录”。
- 待验证：PR #14 的独立 Codex High Review（由 Orchestrator 触发）；最终 head 的 Host/Android CI；真实设备上的设备门禁；0.1.8 的 Ubuntu/Node/DSH 安装与真实模型任务。Android 设备/产品功能不在本 PR 范围。
- 当前分支/基线：`chore/autonomous-development-governance`，基线 `origin/main=14d2884d690f0264b14c5ada8391ad14f42f12f2`。PR #13 已合并（`b2a018d`）；PR #14 打开待审；PR #15（设备门禁测试补充）已合并（`14d2884`）。Wave0 的 PR #10/#11/#12 均已合并。
- 发布边界：0.1.8/code10 已由用户发布，`v0.1.8` tag 仍为 `c196bbf`；本轮不改附件、不新建正式版本。0.1.8 已有 android_agent-10 的首轮模拟器冒烟（0.1.8/code10、冷启动 1616ms、无 FATAL/ANR、WebView 正常），但 Ubuntu/Node/DSH 安装与真实模型未验证，不能写成已验收。
- 关键事实与定位：门禁 `scripts/check-development-gates.py`；离线回归 `scripts/test-development-gates.py`；CI 步骤 `.github/workflows/verify.yml` 的 Host job；编排规则 `docs/ORCHESTRATOR-RULES.md`；QA 证据格式 `docs/QA-GATES.md`；经验证据 `docs/LESSONS.md`；W8 报告 `docs/handoffs/20261106-1357-android-qa-readiness-audit.md`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `AGENTS.md` | 只加入口、五角色/并发、单一状态维护、共享接口先行、集成后复验、两轮根因、独立 Review 绑 SHA、合并授权与门禁入口；细节留在专题 |
| `docs/ORCHESTRATOR-RULES.md` | 新增编排职责、并发上限、共享接口、独立审查、CI/QA 分工、状态印章串行、合并/发布授权、门禁用法与安全边界 |
| `docs/QA-GATES.md` | 新增 QA 适用路径、独立 QA 最少证据、不能算通过的情况、证据分层、门禁用法与记录位置 |
| `docs/LESSONS.md` | 新增带证据的经验：交接/印章并发、AO 配置角色字段、CI APK 非设备物、三类证据不可混算、历史状态≠远端 SHA、AO review 顶层 status、重复 check、旧 SHA 审查、Python 版本差异 |
| `AGENTS.md`、`docs/ORCHESTRATOR-RULES.md`、`docs/LESSONS.md`（复验修正） | 按 `ao project get` 实测配置删除“保存的 `orchestrator.agent=codex`”过期断言，改为 `deepseek-harness`/DeepSeek V4.1 Flash；Reviewer 仍为 Codex/GPT High |
| `docs/code-standards.md` | 新增 Kotlin/Android、Python 3.9、Shell、文档、提交/PR 和审查清单规范 |
| `scripts/check-development-gates.py` | 新增只读合并前门禁：PR 身份/最新 head/全部 check/独立 review/QA 适用与证据结构；失败、空 checks、旧 SHA、自评、未知范围均拒绝 |
| `scripts/test-development-gates.py` | 新增 28 项离线回归，用假 `gh`/`ao` 覆盖旧 SHA、缺 review/CI、QA 漏项、失败/跳过、身份不符、豁免和有效 QA；补 app 内 `SKILL.md` 必须 QA、根目录 Markdown 豁免、AO 查询阶段 head/状态变化拒绝 |
| `.github/workflows/verify.yml` | 在既有 Host job 增加 `test-development-gates.py`；Android job 和 CI 边界不变 |
| `docs/CI.md` | 记录新回归、合并前门禁、独立 review 与 provider 审批边界 |
| `docs/AGENT-STATE.md` | 以 PR #13 合并后的状态为基础，更新 main/CI/Wave0/W4/W5/0.1.8 冒烟边界；删除过期“待复审/未合并”描述 |
| `docs/AGENT-QUICKSTART.md`、`docs/PUBLICATION.md`、`docs/ARTIFACTS.md`、`docs/RELEASE.md`、`docs/APP-UPDATES.md`、`docs/RUNTIME-COMPATIBILITY.md` | 修正 main/tag/分支/当前任务表述；保留 0.1.8 附件与历史测试边界 |
| `docs/TEST-REPORT.md` | 追加本轮治理门禁、W8 只读核查摘要与 0.1.8 首轮设备冒烟边界；历史数字继续标为历史 |
| `docs/AO-WORKTREE-BUILD.md` | 修正对已清理 W2 交接的引用，事实指向 AGENT-STATE/PR #11 |
| `docs/handoffs/20261106-1357-android-qa-readiness-audit.md` | 保留 PR #13 合并的 W8 报告；仅修正其“前一记录”链接，避免指向已清理的 W3 记录 |
| `docs/handoffs/20261106-1059-phase2-docs-contracts.md`、`20261106-1207-ci-rpc-contract-tests.md`、`20261106-1211-phase2-worktree-build.md` | 迁移必要事实后删除（见“产物与校验”） |
| `docs/handoffs/CURRENT.json` | 由 `check-handoff.py --record` 生成，不手写 |

### 仅测试确定性修复（android_agent-14）

- 原因：`scripts/test-agent-workflow.py` 的 `test_verify_workspace_device_runs_are_serialized_per_serial` 直接断言 `first.stdout.readline()` 首行包含 `DEVICE_LOCK_ACQUIRED`；`device-lock.py` 先启动子进程再 flush 锁行，子进程输出（fixture 的 `Synthetic handoff guard`）可能先进入同一管道，导致偶发失败。
- 改动：仅该用例改为循环读取 stdout，最多等待 30 秒直到出现锁标记；未出现仍断言失败。保留第二任务退出码 3 与“设备任务被拒绝”断言，不放宽设备门禁，不改产品、协议、`device-preflight.py`/`device-lock.py` 逻辑或 0.1.8 附件。
- 验证：目标用例连续 10 次通过；完整 `test-agent-workflow.py` 19 项通过，见“验证记录”。本修复不启动设备、不跑 Gradle/Android 构建。

### Review 修复：Markdown QA 与 PASS 前复核（android_agent-14）

- Review 意见（P2）：`classify_paths` 在所有路径判断前用 `path.endswith(".md")` 直接豁免，导致 `app/src/main/assets/browser/SKILL.md`、`app/src/main/assets/phone/SKILL.md` 这类随 App 发布到 Android 内 DSH 的产品资源只改 Markdown 时得到 `qa=not_required`，绕过 `app/**` 必须独立 QA 的规则。
- 修复：必需 QA 路径（`QA_REQUIRED_FILES`/`QA_REQUIRED_PREFIXES`）优先判断；Markdown 后缀豁免仅限根目录（路径不含 `/`），与 [QA-GATES](QA-GATES.md) 的 `docs/**`、根目录 `*.md` 表述一致。`app/**/*.md` 现进入 QA 必需范围。
- 第二处 P2：最后一次 provider head 查询发生在 AO session/project/review 查询与 QA 校验之前，窗口内 A→B 仍可能输出 PASS。现于 `qa_status` 分支后、`print("GATE RESULT: PASS")` 前再查询 `state,isDraft,headRefOid`；head 不等于被校验 SHA、非 `OPEN` 或 `isDraft` 非 `False` 时拒绝。
- 回归：新增 `test_product_skill_markdown_requires_qa`、`test_root_markdown_remains_exempt`、`test_head_change_during_ao_queries_is_rejected`（第三次查询才把 A 改成 B）与 `test_non_candidate_state_during_ao_queries_is_rejected`（draft/closed）；门禁套件 28 项通过。未改 QA 证据结构、CI 配置或产品代码。

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `python3 scripts/test-development-gates.py` | 24 tests OK；覆盖旧 SHA、缺 review、空/排队/失败/跳过 checks、QA 漏项/错 SHA/同 session/未知 session/失败步骤/无 Android 步骤、跨仓库 PR、head 变化、纯文档豁免 | 宿主 macOS Python 3.9.6；假 CLI，无网络/设备/真实 PR |
| `python3 scripts/check-development-gates.py --pr 12 --session android_agent-6` | exit 1，`PR 不是打开状态` | 真实 `gh`/`ao` 只读；证明已合并 PR 被拒绝，不证明 open PR 通过 |
| `gh pr view 12 --json …`、`gh pr checks 12 --json …`、`gh pr diff 12 --name-only` | 字段/退出码与脚本解析一致；同一 head 返回 push+PR 两组 check | 只读公开数据；未修改 PR |
| `gh api repos/leezzou/tanuki/commits/main`、`gh run view 42163963031` | main=`a9aa507…`；CI success（Host+Android） | 公开证据；Android job 不重建原生 `.so` |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261106-1357-android-qa-readiness-audit.md`（W7 原记录） | `Handoff refreshed: …` | 历史记录，已被本记录取代 |
| `git clone https://github.com/leezzou/tanuki.git /tmp/tanuki-pr14-*`；`git checkout -B pr14 9a49dfa`；`git rebase origin/main` | 独立 clone `.git` 可写；先 rebase 到 `b2a018d`（冲突含 1357 记录），#15 合并后再次 rebase 到 `14d2884`（冲突仅 `AGENT-STATE`/`CURRENT.json`，1357 按 main 保留） | 未写共享 worktree 的 `.git`；未把 main merge 进分支 |
| `ao project get android_agent --json` | `config.orchestrator.agent`/`config.worker.agent` 均为 `deepseek-harness`，模型 `opencode-go/deepseek-v4.1-flash`；`config.reviewers=[codex/gpt-6.1-sol/high]` | 只读 AO 配置；据此删除“orchestrator.agent=codex”过期断言 |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261106-1415-governance-gates.md` | `Handoff refreshed: docs/handoffs/20261106-1415-governance-gates.md (445 public files)` | 印章由脚本刷新；N 以实际输出为准 |
| `python3 scripts/check-handoff.py` | `Handoff current: docs/handoffs/20261106-1415-governance-gates.md (445 public files)`，exit 0 | 不证明自然语言真实 |
| `python3 scripts/test-development-gates.py` | 24 tests OK | 宿主 Python 3.9；离线假 CLI |
| `python3 scripts/check-publication.py --git` | `446 public files, 446 index entries, 0 findings`，exit 0 | 文档链接/公开边界检查，不是产品测试 |
| `python3 scripts/package-source.py` | `source_files=446`、`native_sources=absent`；生成 `artifacts/sources/Tanuki-0.1.7-source.zip` 与 `artifacts/SOURCE-SHA256SUMS` | 工作材料，不提交/不发布；外部 SHA 只在本轮报告给出 |
| `git push --force-with-lease origin HEAD:chore/autonomous-development-governance`；`gh pr view 14` | PR #14 head 更新，触发 Host/Android CI | 仅更新 PR 分支，不合并、不触发 Review/发布 |
| `python3 scripts/test-agent-workflow.py WorkflowTests.test_verify_workspace_device_runs_are_serialized_per_serial` ×10（android_agent-14） | 连续 10/10 通过，单次 6.512–11.453s，均为 `OK` | 宿主 mock；不启动设备/Gradle |
| `python3 scripts/test-agent-workflow.py`（android_agent-14） | `Ran 19 tests in 74.688s`，`OK`，exit 0 | 宿主 mock；设备/Android 未执行 |
| `python3 scripts/test-development-gates.py`（android_agent-14 review 修复） | `Ran 28 tests in 15.297s`，`OK`；新增 app 内两个 `SKILL.md` 拒绝、根目录 Markdown 豁免、AO 查询阶段 head/状态变化拒绝 | 宿主 Python 3.9；离线假 CLI，无网络/设备 |

- 哪些是前一轮结果：Wave0 的 24 项门禁回归、W2 构建、W3 CI/契约测试、0.1.7/0.1.8 历史设备数字均为前轮记录；本表区分 W7 原轮次、PR #14 rebase 后复验与 android_agent-14 的仅测试修复。W8 的 AVD/ADB 核查发生在 W8/#13/#15 分支，本 PR 未重跑；`test-agent-workflow.py` 19 项已由 android_agent-14 在本 PR 重跑（74.688s）。
- 未测试、失败和暂未复现：未运行 Gradle/Android 构建、lint、设备/仪器；无真实 open PR 的完整门禁通过样例；W8 的 `device-preflight.py` 未在真实设备执行；0.1.8 的 Ubuntu/Node/DSH 安装与真实模型未验证。

### 未完成与暂停项

- 未完成：本 PR 的独立 Codex High Review（两处 P2 已修复：app 内 Markdown 必须 QA、PASS 前复核 head/状态；待复验）；推送后当前 head 的 Host/Android CI 确认；合并与发布未授权。
- 未完成（其他会话）：PR #15 补充的设备门禁测试已随 `14d2884` 合并；W8 的设备门禁虽随 PR #13 合并，但尚未在真实设备执行。
- 设备边界：0.1.8 只有 android_agent-10 的安装/冷启动/基础 UI/WebView 冒烟；Ubuntu/Node/基础工具/DSH 安装、真实模型任务、底部导航点击、真机/MIUI/低 API 未验证。
- 用户暂停：API Key 保存；真实模型/账号验收；真机验收。

### 下一步

1. 等 PR #14 当前 head 的 Host/Android CI；由 Orchestrator 触发独立 Codex High Review，本会话不自行触发、不自评。
2. Review 意见逐条修复并重审；通过且用户明确授权后才合并，不代合、不直推 main。
3. 合并后在 main 复跑 `check-handoff.py`、`check-publication.py --git`、`test-development-gates.py` 与适用的 `verify-workspace.sh`。
4. PR #15 已合并；真实设备执行前确认无真实数据、无 apt/dpkg/安装/真实任务，不把 host mock 当设备验收。
5. 0.1.8 的 Ubuntu/Node/DSH/真实模型验证另开授权任务；API Key 保存与真实模型/账号验收继续等待用户恢复。

### 约束与风险

- 数据/设备/权限边界：不读真实 Key/Cookie/token/会话；不安装、不卸载、不 `pm clear`、不 wipe、不恢复快照、不启动/停止 App 或模拟器；不使用 `danger-full-access`。
- 关键决策及理由：门禁只读、保守拒绝，空/排队/失败/跳过 checks、旧 SHA、自评、未知范围都不放行；QA 证据只校验结构，不执行命令、不打印命令/日志；纯文档与本门禁自身豁免设备 QA，但仍等 Host CI。
- Git 隔离：共享 worktree 的 `.git` 写操作曾被沙箱拒绝；本轮改用独立 `/tmp` clone 完成 rebase/提交，未绕过沙箱、未改写 main 历史；PR 分支按 `--force-with-lease` 更新。
- 编排配置：当前 `ao project get android_agent` 的 orchestrator/worker 均为 `deepseek-harness`，模型 `opencode-go/deepseek-v4.1-flash`；早期记录中的 `orchestrator.agent=codex` 差异已不存在，不得再写成当前事实。Reviewer 仍是 Codex/GPT High，只做独立 Review。
- W8 归档：W8 报告随 PR #13 合并保留；本 PR 只修正其过期链接，不把 W8 的 host mock 或 AVD 只读结果当成设备验收。
- 门禁局限：脚本能防旧 SHA、空/跳过 check、自评和证据漏项，但不能证明自然语言、截图、设备结果真实；调度者仍需抽查 serial、APK hash、provider 记录和实际输出。
- 失败尝试与避免重复踩坑：本地 `main` 引用曾为 `c196bbf`（旧）；判断基线使用 `origin/main`/provider 实际 SHA。W8 记录原链接指向已清理的 W3，已改为 AGENT-STATE/PR #12。

### 产物与校验

- 新/旧 APK、源码 ZIP：**未更新 0.1.8 附件**；`python3 scripts/package-source.py` 只刷新 `artifacts/sources/` 的默认工作包与 `SOURCE-SHA256SUMS`，该目录被忽略，不提交、不发布。不创建 tag/Release，不改版本号。
- 覆盖安装/签名范围/未发布事项：本 PR 未安装、未签名、未发布；无 tag/Release。0.1.8 的安装/冒烟证据属于 android_agent-10 的独立 QA 会话，不是本 PR 的产物。
- 交接清理：删除已完成的 W1 `20261106-1059-phase2-docs-contracts.md`、W2 `20261106-1211-phase2-worktree-build.md`、W3 `20261106-1207-ci-rpc-contract-tests.md`。迁移位置：W1 事实见 AGENT-STATE/`INTERFACES`/PR #10；W2 见 `AO-WORKTREE-BUILD`/AGENT-STATE/PR #11；W3 见 AGENT-STATE、`CI`、`INTERFACES`/PR #12。保留 `20261105-1509-browser-input-thread.md`、`20261105-1625-video-shared-storage.md`（仍被 TEST-REPORT 引用）、W8 报告、本记录、`TEMPLATE.md`、`CURRENT.json`。
- 文档/脚本变化后已刷新交接印章；源码 ZIP 仅用于检查，不进入公开 PR。

<a id="20261106-1647-workspace-file-preview"></a>

## 20261106-1647-workspace-file-preview

- Agent/会话标识：android_agent-16（DSH Worker）
- 时间：2026-11-06 16:47 CST

### 当前状态

- 已实现：上述功能全部落到源码；PDF/DOCX 在查看器内打开，不再落入 `OTHER`；`.doc` 仍走外部打开并显示明确提示。已按 PR #16 两轮 P2 review 把 DOCX 读取改为 `nextToken()`，拒绝 `DOCDECL`，未定义实体不再静默丢成空串而是返回中文格式错误，保留标准实体/数字字符引用/CDATA；并补 DOCTYPE 与未定义实体回归。
- 已验证：`./gradlew :app:compileModernDebugKotlin :app:testModernDebugUnitTest` BUILD SUCCESSFUL；126 个 JVM 测试，0 失败、0 错误、0 跳过（含新增 22 项）。
- 待验证：真实设备上的手势、翻页、加密/损坏 PDF、真实复杂 DOCX 与深色主题；未运行 lint/assemble/仪器测试。
- 当前基线：`main=cff09a377baf5d047990c0e81eb3984520e4a026`（PR #14 合并提交）；分支 `ao/tanuki-android-dsh/workspace-file-preview`，PR [#16](https://github.com/leezzou/tanuki/pull/16)。初始代码提交 `dd3807bb5529c18c0e320ebdbc5de818b0f2788c`；review 修复后的最终 head 以 PR 页面为准。
- 关键源码：[`WorkspaceFileManager.kt`](../app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt)、[DOCX 原生解析层](../app/src/main/kotlin/com/tanuki/storage/docx/DocxDocumentReader.kt)（F1 的纯文本提取器已由 android_agent-31 替换）、[`WorkspaceFileViewer.kt`](../app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt)、[`WorkspacePdfPreview.kt`](../app/src/main/kotlin/com/tanuki/ui/WorkspacePdfPreview.kt)、[`WorkspaceDocxPreview.kt`](../app/src/main/kotlin/com/tanuki/ui/WorkspaceDocxPreview.kt)、[`ZoomableContent.kt`](../app/src/main/kotlin/com/tanuki/ui/ZoomableContent.kt)。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt` | `FileKind` 增加 PDF/DOCX/DOC；抽 `classifyFileKind` 纯映射；`describe`/`fileMime` 映射 pdf/docx/doc；新增 `openPreviewDescriptor`（只读 ParcelFileDescriptor）和 `extractDocxText`（IO 锁内有界解压）。 |
| `app/src/main/kotlin/com/tanuki/storage/DocxTextExtractor.kt` | 新增：只读 `word/document.xml`，处理 `w:p/w:t/w:br/w:cr/w:tab`；限制归档大小、条目数、总解压大小、正文大小与文本长度；用 `nextToken()` 观察并拒绝 `DOCDECL`（`next()` 会跳过），`ENTITY_REF` 无 replacement text 时返回中文格式错误而不是静默丢弃；中文错误。 |
| `app/src/main/kotlin/com/tanuki/ui/ZoomableContent.kt` | 新增：`ZoomPolicy` 纯策略（1.0–5.0x、双击 1x/2.5x、围绕手指缩放、平移边界）与共享 `Zoomable` 手势组件，切换文件/页面或视口变化时重置。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfRenderPolicy.kt` | 新增：PDF 位图长边 ≤2048、总像素 ≤4,000,000、小页最多 2x 放大，避免 OOM。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspacePdfPreview.kt` | 新增：PFD + `PdfRenderer` 打开/逐页渲染/关闭；上一页/下一页/页码/缩放；加密、损坏、无页中文提示；IO 渲染与 Bitmap 及时回收。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceDocxPreview.kt` | 新增：DOCX 只读文本预览，提示复杂排版/页眉页脚/图片丢失及截断，支持选择复制。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | 图片改用共享 `Zoomable`；接入 PDF/DOCX；`.doc` 显示“暂不支持内置预览，可用其他应用打开”；视频分支未改。 |
| `app/build.gradle.kts` | 仅测试增加 `net.sf.kxml:kxml2:2.3.0`；产品运行时仍用 Android 内置 XmlPullParser，不新增运行时库。 |
| `app/src/test/kotlin/com/tanuki/WorkspaceFileKindTest.kt` | 新增：PDF/DOCX/DOC 与既有类型映射。 |
| `app/src/test/kotlin/com/tanuki/DocxTextExtractorTest.kt` | 新增：合成 zip 多段/空段/换行/制表/实体/CDATA、DOCTYPE 拒绝、文本截断、单条目与总解压超限、缺条目、损坏 zip、坏 XML。 |
| `app/src/test/kotlin/com/tanuki/ZoomPolicyTest.kt` | 新增：clamp、双击切换、Fit 尺寸、围绕中心缩放、平移边界、5x 上限。 |
| `app/src/test/kotlin/com/tanuki/PdfRenderPolicyTest.kt` | 新增：A4、超大页、超小页、窄页的分辨率上限。 |
| `docs/AGENT-STATE.md`、`docs/TEST-REPORT.md`、`docs/AGENT-QUICKSTART.md` | 更新当前状态、本轮验证记录和模块地图；未改接口契约。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin :app:testModernDebugUnitTest` | BUILD SUCCESSFUL；126 tests / 0 failures / 0 errors / 0 skipped | 两轮 review 修复后本轮新跑；测试 XML 在 `app/build/test-results/testModernDebugUnitTest/`，不提交。 |
| 测试类 `WorkspaceFileKindTest`、`ZoomPolicyTest`、`DocxTextExtractorTest`、`PdfRenderPolicyTest` | 2 + 6 + 10 + 4 = 22 项全部通过 | 其中 22 项为本次新增；DOCX 测试使用 kxml2 作为 JVM 侧 XmlPullParser 实现，产品代码不依赖该库。 |
| PR #16 review P2 修复 1（`DocxTextExtractor.kt:97`） | 改为 `nextToken()` 并补 `rejectsDoctypeDeclarationBeforeRootElement`；DOCTYPE 返回中文“文档类型声明”错误，实体与 CDATA 断言同时通过 | 针对合成 XML/zip，未用真实 Word 生成样本。 |
| PR #16 review P2 修复 2（`DocxTextExtractor.kt:95`） | `ENTITY_REF` 单独处理：replacement text 为 null 时抛中文格式错误；新增未定义实体拒绝与标准/数字字符引用正常提取回归 | 合成 XML/zip；KXml 对未定义实体返回 null、对 `&amp;`/`&#65;`/`&#x42;` 返回可解析文本，均已断言。 |
| 首次测试运行 | 1 项失败：新增缩放测试断言写反（期望 -maxX 却传入正向平移） | 仅测试代码错误；修正断言后复跑通过，不涉及产品行为。 |
| 静态边界检查 | `.doc` 不进入 DOCX 分支；`WorkspaceVideoPlayer.kt` 未修改；产品依赖未新增 | 编译覆盖 UI 接线；未做设备手势/真实文件验证。 |

- 哪些是前一轮结果，哪些是本次新验证：126 项 JVM 与编译结果均为两轮 review 修复后新跑；历史 104 JVM、设备测试和 0.1.8 冒烟不混入本轮。
- 未测试、超时、失败和暂未复现的情况：未跑 lint、assemble、Android 仪器、真实设备；未用真实加密 PDF/损坏 PDF/复杂 DOCX 验证错误文案与渲染效果；手势在 Compose 中的双击/双指共存仅编译通过，未设备实测。

### 未完成与暂停项

- 未完成：提交/推送/创建 PR；真实设备 UI 与文件样本验收。
- 外部阻塞：无。
- 用户暂停（沿用）：API Key 保存、真实模型/账号验收、真机验收；本轮不触碰。

### 下一步

1. PR #16 已从独立 `/tmp` clone 创建，目标 `main`、未合并、未触发 AO Review、未发布；等待当前 head 的独立 Codex High Review、CI 与设备 QA。
2. 由 Orchestrator 安排当前 head 的独立 Codex High Review 与适用设备 QA；设备验证至少覆盖图片双指/双击/边界、PDF 翻页/缩放/加密与损坏样本、DOCX 多段/超限/复杂格式提示，真机与低 API 另行确认。

### 约束与风险

- 数据/设备/权限边界：未启动模拟器/真机、未安装、未读真实文件或凭据；只使用合成 zip 和 JVM。
- 关键决策及理由：PDF 每次渲染在单个 IO 调用内打开并关闭 `PdfRenderer`，避免跨线程/跨协程持有原生 renderer；PFD 只读且调用方负责关闭。DOCX 不引第三方运行时库，限制声明大小并实际计数解压字节；文本超限截断而非无限占用内存。图片与 PDF 共用 `Zoomable`，视频文件完全未动。
- 失败尝试与避免重复踩坑的结论：新增缩放测试首次把平移方向写反，说明边界测试要同时覆盖正负方向；已修正。`net.sf.kxml` 仅放在 `testImplementation`，若后续误移到 `implementation` 会新增运行时依赖。

### 产物与校验

- 新/旧 APK、源码 ZIP：本轮不生成 APK、不发布；源码 ZIP 仅在提交前按 `scripts/package-source.py` 刷新并核对 `artifacts/SOURCE-SHA256SUMS`，属于工作材料。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布；PR [#16](https://github.com/leezzou/tanuki/pull/16) 只交付可审查候选。
- 提交/推送前清理的过期记录：未删除历史交接；W5 治理记录仍与当前 `AGENT-STATE` 和 PR #14 相关，保留。结论已迁入 `AGENT-STATE`/`TEST-REPORT`，无因本任务过期而必须删除的记录。
- 仍需保留的验证/未完成记录：本记录与 `docs/handoffs/CURRENT.json` 印章；设备验证缺口待独立 QA。

<a id="20261106-1735-video-fullscreen-full-sensor"></a>

## 20261106-1735-video-fullscreen-full-sensor

- Agent/会话标识：android_agent-17（接替 android_agent-15 的 DSH Worker 会话）
- 时间：2026-11-06 17:35 CST

### 当前状态

- 已实现：视频全屏期间请求 `ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR`（10），允许竖屏/横屏随设备旋转，不强制横屏；退出时按进入前记录的 `requestedOrientation` 恢复；非视频全屏不触碰方向。
- 已验证：`cff09a3` 基线上 `:app:compileModernDebugKotlin` 与 `:app:testModernDebugUnitTest` BUILD SUCCESSFUL（3m 4s），110 tests / 0 failures；该提交 `d5b83fe` 的 GitHub Actions 4 个 check 全部 pass。rebase 到 `origin/main` `41609cb`（PR #16 已合并）后 `:app:testModernDebugUnitTest --rerun-tasks` 真实重跑 BUILD SUCCESSFUL（53m 56s，28 tasks executed），132 tests / 0 failures / 0 errors / 0 skipped，`VideoFullscreenPolicyTest` 6/6。
- 待验证：设备上的真实旋转与退出恢复（本轮不启动设备）；独立 Codex High Review 与合并授权。
- 当前目标包名、分支/无Git状态、是否有运行中任务：工作副本为独立 `/tmp` clone；PR #17 分支 `ao/video-fullscreen-rotatio/full-sensor` 已 rebase 到 `origin/main` `41609cb206416c1f16d1133eb57ce3a20a2e8a4b`（PR #16 合并提交），提交为 `8ce9882`（视频实现）与一个文档提交，head 以 PR 页面为准；无设备任务、无安装、无后台构建残留。
- 关键事实与可定位源码/公共文档：`app/src/main/kotlin/com/tanuki/ui/VideoFullscreenPolicy.kt`、`app/src/main/kotlin/com/tanuki/ui/WorkspaceVideoPlayer.kt`、`app/src/test/kotlin/com/tanuki/VideoFullscreenPolicyTest.kt`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/VideoFullscreenPolicy.kt` | 新增纯 Kotlin 方向会话：`FULL_SENSOR = 10`（等价 `ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR`），`enter()` 返回 `FULL_SENSOR` 并只在首次进入记录原方向，`exit()` 返回原方向后清空，未进入返回 `null`。注释明确“全屏期间允许竖屏/横屏随设备旋转，不强制横屏”。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceVideoPlayer.kt` | 保留 W15 的方向恢复逻辑：`DisposableEffect(fullscreen, hostActivity, orientation)` 在进入全屏时 `orientation.enter(hostActivity.requestedOrientation)` 并写入 `requestedOrientation`，`onDispose` 时 `orientation.exit()?.let { requestedOrientation = it }`；仅 `fullscreen && hostActivity != null` 时生效，找不到 Activity 不改变方向；播放器仍复用同一 `VideoView`/`MediaPlayer`，播放状态、位置、倍速逻辑未改；仅更新注释为“允许随设备旋转（不强制横屏）”。 |
| `app/src/test/kotlin/com/tanuki/VideoFullscreenPolicyTest.kt` | 新增 JVM 回归：断言 `FULL_SENSOR == 10`；进入返回 10、退出恢复进入前方向、重复 `enter` 不覆盖首个恢复值、未进入 `exit()` 返回 `null`、退出后可复用。 |
| `docs/AGENT-STATE.md` | 更新当前交接链接与本节结论、验证/待验证项。 |
| `docs/handoffs/20261106-1735-video-fullscreen-full-sensor.md` | 本记录。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git clone https://github.com/leezzou/tanuki.git /tmp/tanuki-video-<ts>` + `git checkout cff09a3` | 通过 | 基线 `cff09a377baf5d047990c0e81eb3984520e4a026`（`Merge pull request #14 ... governance`）。 |
| 从 android_agent-15 worktree 复制 3 个文件 | 通过 | 3 个文件为 W15 未提交产物，本轮直接复用，未重新设计。 |
| `source scripts/ao-worktree-env.sh`（首次，未设 `TANUKI_JDK`） | 失败 | 脚本未找到 JDK 17（本机 `java` 为 Homebrew JDK 27，`/usr/libexec/java_home` 仅 JDK 11）；Gradle 回落到只读 `~/.gradle` 报 `Operation not permitted`。 |
| `TANUKI_JDK=<主仓库 .build/jdk/jdk-17.0.21+9/Contents/Home>` 后 `source scripts/ao-worktree-env.sh`，再 `./gradlew :app:compileModernDebugKotlin :app:testModernDebugUnitTest` | 通过 | JDK17: Temurin 17.0.21+9；SDK 取 `ANDROID_HOME`（android-36 / build-tools 35.0.0 / ndk 27.2.12479018 齐全）；`GRADLE_USER_HOME` 落在 clone 内 `.build/gradle-user-home`；`BUILD SUCCESSFUL in 3m 4s`，28 tasks executed，仅有既有 deprecation/警告。 |
| `app/build/test-results/testModernDebugUnitTest/*.xml` 汇总 | 通过 | 22 个测试类、110 tests / 0 failures / 0 errors / 0 skipped；`com.tanuki.VideoFullscreenPolicyTest` 6 tests 全过。 |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261106-1735-video-fullscreen-full-sensor.md` | 通过 | 刷新 `docs/handoffs/CURRENT.json` 印章。 |
| `python3 scripts/check-handoff.py` | 通过 | 指纹与当前记录一致。 |
| `python3 scripts/check-publication.py --git` | 通过 | 0 findings（仅只读扫描暂存内容）。 |
| `gh pr checks 17 --watch`（head `d5b83fe`） | 通过 | 4/4 pass：`Android build, JVM tests and lint` 4m55s 与 3m19s；`Host scripts and handoff checks` 27s 与 39s。CI 与本地结论一致。 |
| rebase 到 `origin/main` `41609cb`（PR #16 已合并，原基线 `cff09a3`） | 通过（文档冲突已解决） | 冲突仅 `docs/AGENT-STATE.md`、`docs/handoffs/CURRENT.json`：AGENT-STATE 以 #16 合并后的版本为基础，保留 F1/PDF/图片段落，再叠加视频 FULL_SENSOR 段；`CURRENT.json` 由 `check-handoff.py --record` 重新生成。源码零冲突，`WorkspaceVideoPlayer.kt` 未被 PR #16 改动。未 force-push 改历史前的最后一次 `--force-with-lease` 推送见 PR 页面。 |
| rebase 后 `./gradlew :app:testModernDebugUnitTest --rerun-tasks` | 通过 | `BUILD SUCCESSFUL in 53m 56s`，28 tasks executed（含全量重建）；132 tests / 0 failures / 0 errors / 0 skipped（26 个测试类，含 PR #16 的 22 项），`VideoFullscreenPolicyTest` 6/6。 |
| `python3 scripts/test-development-gates.py` | 通过 | `Ran 28 tests in 22.740s`，`OK`。 |
| `python3 scripts/package-source.py` | 通过 | 生成 `artifacts/sources/Tanuki-0.1.7-source.zip` 与校验清单（工作材料，不提交）。 |

- 哪些是前一轮结果，哪些是本次新验证：3 个源文件来自 W15 的未提交实现（W15 未跑通 Gradle，无测试结论）；本轮在 `cff09a3` 跑过编译/JVM，随后 rebase 到 `41609cb` 又真实重跑一次 JVM（132 项）与门禁/交接/公开边界检查。`d5b83fe` 的 CI 4/4 属 rebase 前结果，不能当作新 head 的结论。
- 未测试、超时、失败和暂未复现的情况（不能抹掉）：设备/仪器、真实旋转与恢复未执行；独立 Codex High Review 未触发（未获授权）；合并与发布未授权。

### 未完成与暂停项

- 未完成：真实设备旋转/退出恢复验收；独立 Codex High Review；合并授权。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；合并/发布/Review 触发需明确授权。

### 下一步

1. PR #17 在 `d5b83fe` 上 CI 4/4 pass；后续如再推送，需重新等 Host/Android CI 并复跑 `:app:compileModernDebugKotlin :app:testModernDebugUnitTest`。
2. 由 Orchestrator 决定是否触发独立 Codex High Review；本会话不自行触发、不自评。
3. 设备验收需另开授权：确认无真实数据与安装任务后再验证全屏旋转与退出恢复。
4. 合并前确认用户明确授权；不代合、不直推 `main`。

### 约束与风险

- 数据/设备/权限边界：未启动模拟器/设备，未安装、未 `pm clear`、未改系统设置；仅本地编译与 JVM 单测。
- 关键决策及理由：用 `FULL_SENSOR` 而不是 `SENSOR_LANDSCAPE`，让全屏尊重设备当前持握方向（竖屏看竖屏视频不被迫横屏）；方向状态机保持纯 Kotlin，便于 JVM 回归；恢复值只记录首次进入，避免重组/重复进入覆盖。
- 失败尝试与避免重复踩坑的结论：`scripts/ao-worktree-env.sh` 的 `TANUKI_JDK` 必须指向含 `bin/java` 与 `release` 的 JDK Home（macOS 为 `.../Contents/Home`），否则脚本继续报“未找到 JDK 17”；未设置成功时不要直接跑 `./gradlew`，否则 wrapper 会尝试写只读的 `~/.gradle`。

### 产物与校验

- 新/旧APK、源码ZIP及校验文件，哪些确实更新：本轮不产出 APK/发布件；源码包仅按交接要求本地生成用于校验（`source_files=449`），不提交、不发布。
- PR：[#17](https://github.com/leezzou/tanuki/pull/17)（base `main`，来源分支 `ao/video-fullscreen-rotatio/full-sensor`）；rebase 后功能提交 `8ce9882`，基于 `41609cb`，具体 head 以 PR 页面为准。未合并、未发布、未触发 AO Review。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮未删除历史记录；`20261106-1415-governance-gates.md` 仍为有效交接（PR #14 待 Review），继续保留。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码ZIP；不要把源码ZIP自身哈希写回归档内形成循环。

结束前：

```bash
python3 scripts/check-handoff.py --record docs/handoffs/<本次记录>.md
python3 scripts/check-handoff.py
python3 scripts/package-source.py
```

<a id="20261109-1535-background-update-check"></a>

## 20261109-1535-background-update-check

- Agent/会话标识：android_agent-24（DSH Worker）
- 时间：2026-11-09 15:35 CST

### 当前状态

- 已实现：后台检查纯策略、两个仓库、两个 Worker、调度器、通知、UI 徽标、手动结果同步。
- 已验证：`:app:compileModernDebugKotlin` BUILD SUCCESSFUL；`:app:testModernDebugUnitTest` BUILD SUCCESSFUL，174 tests / 0 failures / 0 errors / 0 skipped。新增 `BackgroundUpdateChecksTest` 23 项全过。
- 待验证：真实 WorkManager 调度与进程重启、Android 13 通知权限与通知展示、真实 GitHub/npmmirror/npm 网络、设备 UI、lint/assemble/仪器。
- 当前分支：`ao/workmanager-updates/background-update-check`（从 `origin/main=5c680b9` 创建，2026-11-09 rebase 到 `origin/main=9b32051`；在本会话 worktree 实现，验证与提交在独立 `/tmp` clone 完成）。
- 未运行设备、未启动模拟器、未安装 APK、未发布。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/build.gradle.kts` | 增加 WorkManager 依赖。 |
| `app/src/main/kotlin/com/tanuki/update/BackgroundUpdateChecks.kt` | 纯策略：30 分钟/24 小时间隔、唯一任务名、12 秒超时、版本比较、APK/DSH 状态编解码、DSH 安装身份重算、镜像回退、通知去重。 |
| `app/src/main/kotlin/com/tanuki/update/BackgroundUpdateRepositories.kt` | APK/DSH 两个独立仓库与受限 HTTPS 读取；恢复快照按当前安装身份重算；写 `apk-update-check.json`、`dsh-update-check.json`。 |
| `app/src/main/kotlin/com/tanuki/update/UpdateCheckWorkers.kt` | 两个 `CoroutineWorker` 与 `UpdateCheckScheduler`（网络约束、UPDATE 唯一任务）。 |
| `app/src/main/kotlin/com/tanuki/update/UpdateNotifier.kt` | `updates` 通知渠道、APK/DSH 通知、Android 13 权限检查。 |
| `app/src/main/kotlin/com/tanuki/TanukiApp.kt` | 暴露两个仓库、注册周期任务，并观察 `RuntimeManager.state.dsh` 同步 DSH 快照。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 收集两份状态、手动检查申请通知权限；不再在 Activity 重建时回放旧同步。 |
| `app/src/main/kotlin/com/tanuki/update/ApplicationUpdateManager.kt` | 手动 APK 检查成功时回调后台仓库，携带检查开始时间。 |
| `app/src/main/kotlin/com/tanuki/update/UpdateManager.kt` | 手动 DSH 检查成功时回调后台仓库，携带检查开始时间；下载/安装逻辑未改。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 模型/回调接入；DSH 摘要与版本更新行显示“有更新：<version>”；“刷新”改为“刷新版本列表”。 |
| `app/src/main/kotlin/com/tanuki/ui/ApplicationUpdateCard.kt` | APK 卡片显示后台徽标、上次检查时间与静默失败说明。 |
| `app/src/test/kotlin/com/tanuki/BackgroundUpdateChecksTest.kt` | 23 项离网 JVM 回归。 |
| `docs/APP-UPDATES.md` | 增加后台周期检查说明。 |
| `docs/TEST-REPORT.md` | 记录本轮命令、174 项结果与未验证范围；更新清理后的历史链接。 |
| `docs/AGENT-STATE.md` | 更新当前交接、实现/验证/待验证与下一步。 |
| `docs/handoffs/20261105-1509-browser-input-thread.md`、`docs/handoffs/20261105-1625-video-shared-storage.md` | 删除过期交接；结论已保留在 `docs/TEST-REPORT.md`、`BROWSER-WORKSPACE.md`、`WORKSPACE-STORAGE.md`。 |

### Review 修复（P2）

- 评论：Activity 重建会重新执行同步 `LaunchedEffect`，把 Application 中保留的旧手动结果写回，可能清除较新的后台 latest/available。
- 修复：删除两个 `LaunchedEffect`；`ApplicationUpdateManager`/`UpdateManager` 在手动检查成功事件回调后台仓库，并携带检查开始时间。`applyManualApk`/`applyManualDsh` 在 `startedAt < previous.lastCheckedAt` 时保留后台快照，覆盖“手动旧结果 → 后台新结果 → Activity 重建”的 APK/DSH 两条链路。
- 回归：`staleManualApkReplayCannotClearNewerBackgroundSnapshot`、`staleManualDshReplayCannotClearNewerBackgroundSnapshot` 均通过；策略测试 23 项，完整 JVM 174 项。

### Rebase

- 2026-11-09 rebase 到 `origin/main=9b32051`（PR #18 DSH 已安装版本管理），解决 `docs/AGENT-STATE.md` 与 `docs/handoffs/CURRENT.json` 冲突。
- 保留 main 的当前交接（android_agent-25）与本 PR 的并行记录；rebase 后重新运行 `compileModernDebugKotlin` 与完整 JVM：174 tests / 0 failures / 0 errors / 0 skipped（既有 151 + 新增 23）。
- 修复冲突合并时 `AGENT-STATE` 当前交接中 W8 链接的全角括号。

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin --stacktrace` | BUILD SUCCESSFUL（3m36s，19 tasks） | 本轮新跑；仅有既有弃用警告。 |
| `source scripts/ao-worktree-env.sh && ./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.BackgroundUpdateChecksTest'` | BUILD SUCCESSFUL；21 tests / 0 failures | 目标类快速回归。 |
| `source scripts/ao-worktree-env.sh && ./gradlew :app:testModernDebugUnitTest --stacktrace` | BUILD SUCCESSFUL；27 个结果文件，174 tests / 0 failures / 0 errors / 0 skipped | 本轮完整 JVM；174 = 既有 151 + 新增 23。 |
| `grep` 合并清单 | 含 `WorkManagerInitializer`、`SystemJobService`、`RescheduleReceiver`、`RECEIVE_BOOT_COMPLETED`、`POST_NOTIFICATIONS` | 证明 WorkManager 清单合并生效；未在设备运行。 |

- 前一轮结果：0.1.8 设备冒烟、F1 126 JVM、PR #17 132 JVM 均为历史记录，未在本轮复跑。
- 未测试：lint、assemble、Android 仪器、真实网络请求、WorkManager 实际触发/重启恢复、通知展示与权限拒绝路径、深色/大字体 UI。

### 未完成与暂停项

- 未完成：PR 创建与独立 Codex High Review；设备级后台调度、通知、徽标验收。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收继续等待用户恢复。

### 下一步

1. PR [#19](https://github.com/leezzou/tanuki/pull/19)：等待 Orchestrator/用户决定是否触发当前 head 的独立 Codex High Review；PR 正文已记录本轮的 174 项结果。
2. 由 Orchestrator/用户决定是否触发当前 head 的独立 Codex High Review；不要用 Worker 自评替代。
3. 设备 QA（另行授权）检查：APK/DSH 徽标、30 分钟/24 小时调度、同版本只通知一次、Android 13 权限、重启后任务保留、失败静默。
4. 合并与发布需用户明确授权；本轮不代合、不创建 tag/Release。

### 约束与风险

- 未改 DSH 下载、安装、快照逻辑；仅给 `UpdateManager` 增加手动检查完成回调，后台检查仍只读元数据。
- WorkManager 周期任务是系统尽力调度，省电模式和 OEM 后台限制可能推迟；不承诺精确到分钟。
- APK 后台检查使用稳定 Release 的 `app-update.json`；debug/预发布渠道仍以手动检查的 Atom 流程为准，包名不匹配时不会误报。
- 通知只在发现新版本且权限允许时发送；同一版本由持久化 `notifiedVersion` 去重。拒绝通知权限不弹错误、不影响检查。
- 真实设备、真实 Release、真实 npm 元数据和 Android 13 通知行为未验证。

### 产物与校验

- 本轮无新 APK、源码 ZIP 或发布附件；`artifacts/` 未修改。
- 文档/源码变化后需按规则刷新 `CURRENT.json` 印章并重新生成源码包；本轮提交前执行交接/公开检查。
- 清理的过期记录：`20261105-1509-browser-input-thread.md`、`20261105-1625-video-shared-storage.md`。理由：任务已完成且结论已进入 TEST-REPORT/专题文档；保留 `20261106-1735`（PR #17 未决）、`20261106-1647`、`20261106-1415`、`20261106-1357`（仍被状态/验证引用）。

<a id="20261109-1539-dsh-install-resilience"></a>

## 20261109-1539-dsh-install-resilience

- Agent/会话标识：android_agent-23（DSH Worker）；android_agent-25 接续收尾
- 时间：2026-11-09 15:39 CST（UTC+8）

### 当前状态

- 已实现：上述 DSH 依赖解析、镜像映射、下载重试/续传、并发与进度改动；P1 复审后补官方元数据逐项核验、v3 锁缓存与 `.lock-trusted` 安装标记。
- 已验证：`compileModernDebugKotlin` 通过；完整 JVM 套件 161 tests / 0 failures / 0 errors / 0 skipped（含 `NpmLockTrustTest` 6 项）；目标 Npm/Download 回归 60 项通过；`test-dsh-lock.py` 通过。
- 合并后验证（本轮新跑）：同步 main `9b32051` 后 `compileModernDebugKotlin` + 完整 `testModernDebugUnitTest` BUILD SUCCESSFUL，180 tests / 0 failures / 0 errors / 0 skipped。
- 接续验证（android_agent-25 新跑）：新增实际 worker 调度回归后 `compileModernDebugKotlin` + 完整 `testModernDebugUnitTest` 通过，182 tests / 0 failures / 0 errors / 0 skipped（含 `NpmAdaptiveWorkerPoolTest` 2 项）。
- Rebase 收尾（android_agent-25 新跑）：rebase 到 `origin/main=6136451`，保留 #19 更新检查与 #20 下载/镜像/并发改动；`compileModernDebugKotlin` + 完整 JVM 通过，205 tests / 0 failures / 0 errors / 0 skipped。
- 待验证：新 head 的 CI；真实 Android 设备上的 DSH 首次安装/断网重试/镜像切换与降并发；真实 4000 包规模吞吐对比；独立 Review。
- 当前目标包名、分支/无Git状态、是否有运行中任务：`ao/dsh-install-2/install-resilience`，已 rebase 到 `origin/main=6136451`（含 #19）；PR [#20](https://github.com/leezzou/tanuki/pull/20)，head 以 PR 页面为准。
- 关键事实与可定位源码/公共文档：`app/src/main/kotlin/com/tanuki/runtime/NpmPackageDownloads.kt`、`NpmAdaptiveWorkerPool.kt`、`NpmDependencyLocks.kt`、`NpmSources.kt`、`NpmArchiveMapping.kt`、`NpmDownloadPolicy.kt`、`NpmLockNormalizer.kt`、`NpmLockTrust.kt`、`app/src/main/kotlin/com/tanuki/download/Downloader.kt`、`DownloadRetryPolicy.kt`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/runtime/NpmArchiveMapping.kt`（新增） | 唯一的官方锁路径→镜像白名单映射；拒绝任意 host/端口/参数/编码穿越；反向把华为/腾讯 registry 前缀还原为官方路径。 |
| `app/src/main/kotlin/com/tanuki/runtime/NpmDownloadPolicy.kt`（新增） | 8→16 自适应并发策略；每 host 8 路信号量；无锁元数据源顺序与 15s 单请求/180s 命令超时。 |
| `app/src/main/kotlin/com/tanuki/runtime/NpmLockNormalizer.kt`（新增） | 镜像解析出的 `resolved` 恢复为官方地址，SRI/版本不变；未知 host 直接拒绝。 |
| `app/src/main/kotlin/com/tanuki/runtime/NpmLockTrust.kt`（新增） | P1：镜像锁只作候选；逐包对比官方锁的版本、官方归档路径、SHA-512；缺包/多包/篡改均拒绝。 |
| `app/src/main/kotlin/com/tanuki/download/DownloadRetryPolicy.kt`（新增） | 3 次尝试（2 次重试）、500/1000ms 指数退避、404/410 缺失、429/5xx 可重试分类。 |
| `app/src/main/kotlin/com/tanuki/download/Downloader.kt` | 429/5xx 分类退避；404/410 立即上抛；临时文件哈希通过后 `ATOMIC_MOVE` 提交；Range 解析改为可测试函数；保留 30s connect/read 与部分文件续传。 |
| `app/src/main/kotlin/com/tanuki/runtime/NpmDependencyLocks.kt` | 锁身份改为 version+integrity+platform（Node/npm 升级不再强制重新解析）；v3 只接纳官方核验后的锁；saved→installed→bundled 候选逐个校验，全部不可用才解析；重定向缓存返回 null。 |
| `app/src/main/kotlin/com/tanuki/runtime/NpmSources.kt` | 自动模式国内镜像优先、官方最后；手动选择仍优先用户来源；暴露 host 供单 host 限流。 |
| `app/src/main/kotlin/com/tanuki/runtime/NpmPackageDownloads.kt` | 镜像元数据只生成候选，官方锁逐项核验后才使用；官方核验不可用时 fail-closed；安装成功写 `.lock-trusted`；下载调度改用 `NpmAdaptiveWorkerPool`，单 host≤8、404/410 快速换源、429/5xx 退避降级。 |
| `app/src/main/kotlin/com/tanuki/runtime/NpmAdaptiveWorkerPool.kt`（新增） | 接续 P2：领取下一个归档前检查目标；目标降低时用 CAS 退休名额让超出者完成当前归档后退出，调度协程只补到目标，不重新拉起已退休 worker。 |
| `app/src/test/kotlin/com/tanuki/NpmArchiveMappingTest.kt`（新增） | 映射/非法 host/端口/参数/穿越/白名单回归。 |
| `app/src/test/kotlin/com/tanuki/NpmLockNormalizerTest.kt`（新增） | 镜像锁归一化、SRI/版本保持、未知 host/非 HTTPS 拒绝。 |
| `app/src/test/kotlin/com/tanuki/NpmLockTrustTest.kt`（新增） | P1 回归：镜像 URL 等价接受；传递依赖 SRI/版本/路径篡改、缺包/多包、入口 SRI 篡改、非 SHA-512 均拒绝。 |
| `app/src/test/kotlin/com/tanuki/NpmDownloadPolicyTest.kt`（新增） | 退避值、状态分类、元数据源顺序/超时、并发 8–16、单 host 8。 |
| `app/src/test/kotlin/com/tanuki/NpmAdaptiveWorkerPoolTest.kt`（新增） | 实际调度回归：16→8 时超出目标的 worker 完成当前任务后退出、活跃回落到 8、队列继续完成；8→16 时补足 worker。 |
| `app/src/test/kotlin/com/tanuki/DownloaderRetryTest.kt`（新增） | 429/503 重试与退避、404/410 不重试、重试耗尽、断点续传 Range。 |
| `app/src/test/kotlin/com/tanuki/DownloadRangeTest.kt`（新增） | Content-Range 合法/非法解析、坏 206 整包重下。 |
| `app/src/test/kotlin/com/tanuki/NpmDependencyLocksTest.kt` | 适配锁身份/候选列表；新增 saved 锁不兼容时回退 installed、不触发元数据；重定向缓存安全返回 null。 |
| `app/src/test/kotlin/com/tanuki/NpmUnknownLengthDownloadTest.kt` | 截断响应现在在 2 次退避后以 IOException 结束，更新断言。 |
| `docs/AGENT-STATE.md` | 增加本轮交付/验证/待验证状态。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin --console=plain` | 通过（BUILD SUCCESSFUL） | 首次编译暴露注释嵌套/构造器重载问题，修复后通过；未跑 lint/assemble。 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.Npm*' --tests 'com.tanuki.Download*' --tests 'com.tanuki.OperationProgressTest'` | 通过（目标回归 61 tests，0 failed） | 覆盖映射、归一化、锁快速路径、退避、Range、并发、缓存命中；不联网、不装设备。 |
| `./gradlew :app:testModernDebugUnitTest --rerun-tasks` | 通过（BUILD SUCCESSFUL） | 32 个测试类，161 tests / 0 failures / 0 errors / 0 skipped；含 P1 `NpmLockTrustTest` 6 项；不联网、不装设备。 |
| `python3 scripts/test-dsh-lock.py` | 通过（PASS） | 模拟 linux/arm64/glibc 三个 Node 版本，560 archives；官方 HTTPS/SHA-512 条目 686，3 个负例；不下载、不装设备。 |
| `python3 scripts/check-handoff.py --record ...` / `check-handoff.py` | 通过 | 记录印章刷新；当前交接检查 468 public files。 |
| `./gradlew :app:compileModernDebugKotlin` | 通过（BUILD SUCCESSFUL） | 接续轮在 `/tmp/tanuki-pr20` 独立 clone，head `906c500` + 本地改动；仅既有弃用告警。 |
| `./gradlew :app:testModernDebugUnitTest` | 通过（BUILD SUCCESSFUL） | 182 tests / 0 failures / 0 errors / 0 skipped；新增 `NpmAdaptiveWorkerPoolTest` 2/2。 |
| rebase 到 `origin/main=6136451` 后 `:app:compileModernDebugKotlin` + `:app:testModernDebugUnitTest` | 通过（BUILD SUCCESSFUL） | 205 tests / 0 failures / 0 errors / 0 skipped；#19 与 #20 组合代码同时保留。 |
| `./gradlew :app:testModernDebugUnitTest --tests com.tanuki.NpmAdaptiveWorkerPoolTest --rerun-tasks` | 通过（BUILD SUCCESSFUL） | 重复运行实际调度用例，0 failed。 |
| `python3 scripts/package-source.py` | 通过 | 提交前生成源码包与校验清单（469 public files）；本记录不写包自身哈希，工作材料不提交。 |

- 哪些是前一轮结果，哪些是本次新验证：本轮首次编译/目标 JVM 测试是新验证；完整 JVM、锁脚本、交接/源码包检查待补。前轮 132 tests 不作为本轮结果。
- 未测试、超时、失败和暂未复现的情况（不能抹掉）：未在 Android 设备/真实 PRoot 环境执行 DSH 安装；未做真实弱网/镜像 404/429 注入；未做 4000 包端到端吞吐基准；未跑 lint、assemble、仪器测试、真机/MIUI/低 API。

### 未完成与暂停项

- 未完成：PR #20 的 CI/独立 Review；真实设备/弱网 QA 与真实降并发验收。完整 JVM、`test-dsh-lock.py`、交接/公开检查已完成。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收继续等待用户恢复；本轮未触碰。

### 下一步

1. 推送新 head 后确认 CI；需要时按 review 继续修复，不合并、不触发 Review。
2. PR [#20](https://github.com/leezzou/tanuki/pull/20) 等待 CI 与独立 Codex High Review；不合并、不触发 AO Review。
3. Review 意见逐条修复后重审当前完整 SHA；真实设备/弱网 QA 另行授权后执行。

### 约束与风险

- 数据/设备/权限边界：未读取密钥/Cookie/用户数据；未启动/安装/清空设备；未改 Ubuntu/Node/协议；未合并/发布。
- 关键决策及理由：锁身份去掉 Node/npm 以避免升级触发元数据重解析，改由每次依赖图校验兜底；自动模式官方最后，手动选择保留用户意图；镜像只映射路径；P1 后镜像生成的传递 SRI 不再可信，必须由官方元数据逐项确认，官方不可用时拒绝而非回退信任镜像。
- 失败尝试与避免重复踩坑的结论：`Downloader` 第三个函数参数会改变既有尾随 lambda 绑定，必须保留 2 参重载；Kotlin 块注释可嵌套，注释中不要写未转义的 `/*`；华为/腾讯 registry 带路径前缀，反向归一化必须剥离。

### 产物与校验

- 新/旧APK、源码ZIP及校验文件，哪些确实更新：本轮不产出 APK；源码包在提交前按脚本刷新，工作材料不提交。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：保留既有历史交接与验证记录；本轮新增记录，不改写前任记录。若后续确认旧记录已无独有未完成事项，再由当轮维护方迁移后清理。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码ZIP；不要把源码ZIP自身哈希写回归档内形成循环。

<a id="20261109-1539-dsh-installed-version-manager"></a>

## 20261109-1539-dsh-installed-version-manager

- Agent/会话标识：android_agent-25（DSH Worker）
- 时间：2026-11-09 15:39 CST

### 当前状态

- 已实现：`DshVersions` 纯逻辑清单与切换策略；`RuntimeComponents.installedDshDetails()` 与 `switchDsh()`；`RuntimeService` 的 `DSH_SWITCH`；独立 `DshVersionManagerDialog`；TanukiUi 入口与 MainActivity 回调。
- 已验证：`:app:compileModernDebugKotlin` 与 `:app:testModernDebugUnitTest` 均 BUILD SUCCESSFUL；151 JVM 测试 0 失败/0 错误/0 跳过，其中 `DshVersionsTest` 7 项、`DshSwitchTest` 12 项。
- 待验证：PR 的 Host/Android CI、独立 Codex High Review、真实设备上的对话框布局/切换/卸载、`DataSnapshots.create` 的真实数据量耗时与失败路径。
- 当前分支：`ao/dsh-version-manager/root`（独立 worktree，基线 `5c680b9`）；未运行真实 DSH 任务。
- 关键事实：`DshVersions` 只把程序目录、`.installed`、依赖入口与中间目录都非符号链接的版本视为已安装；`lockVerified` 要求依赖锁哈希与 `.installed` 一致；`verified` 还要求存在 `.web-verified`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/runtime/DshVersions.kt` | 新增纯 Kotlin 清单/校验/切换策略与元数据事务；输出 `lockVerified`；切换前复核目标依赖锁与当前程序完整性；优先按 `unix:blocks` 统计实际占用并对硬链接去重；不依赖 Android API。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeComponents.kt` | 清单统一走 `DshVersions`；新增 `installedDshDetails()` 与 `switchDsh()`；切换前检查基础环境、目标 Node 兼容性与目标依赖锁；拒绝当前程序缺失；复用同一版本正则。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeService.kt` | 新增 `DSH_SWITCH` 前台服务动作。 |
| `app/src/main/kotlin/com/tanuki/ui/DshVersionManagerDialog.kt` | 新增独立“已安装 DSH 版本”对话框：总占用、当前/备用、校验、切换/卸载确认与降级说明。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 仅新增入口按钮、对话框调用与 3 个回调字段。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 仅接线清单读取、切换与卸载回调，并把 `DSH_SWITCH` 纳入前台通知条件。 |
| `app/src/test/kotlin/com/tanuki/DshVersionsTest.kt` | 清单校验、占用/时间、符号链接、锁校验、校验状态与版本管理卸载保护回归。 |
| `app/src/test/kotlin/com/tanuki/DshSwitchTest.kt` | 当前/未安装/运行中/降级/锁损坏/当前程序缺失拒绝、成功元数据更新、写入失败回滚、快照/兼容性失败不动元数据。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin` | 通过（BUILD SUCCESSFUL，2m45s） | 本 worktree，JDK17；仅告警既有弃用项。 |
| `source scripts/ao-worktree-env.sh && ./gradlew :app:testModernDebugUnitTest` | 通过（BUILD SUCCESSFUL） | 151 tests / 0 failures / 0 errors / 0 skipped；`DshVersionsTest` 7/7、`DshSwitchTest` 12/12。 |
| `source scripts/ao-worktree-env.sh && ./gradlew :app:lintModernDebug` | 通过（BUILD SUCCESSFUL，3m07s） | 仅有既有弃用/可读性告警；lint 报告写入工作材料，不提交。 |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261109-1539-dsh-installed-version-manager.md` | 通过 | 刷新 `docs/handoffs/CURRENT.json` 指纹。 |
| `python3 scripts/check-handoff.py` | 通过 | 当前记录与源码指纹一致。 |

- 哪些是前一轮结果，哪些是本次新验证：151 JVM 与编译均为本次新跑；历史 132 项、126 项不再冒充本轮结果。
- 未测试、超时、失败和暂未复现的情况：未跑 assemble/仪器/真机；未在真实 userdata 上执行切换快照；未验证 PR CI。

### 未完成与暂停项

- 未完成：PR 创建后的 Host/Android CI、独立 Codex High Review、设备 UI 与真实切换验收。
- 外部阻塞：无（等待 PR 流水线与审查安排）。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；本轮未触碰。

### 下一步

1. 在 `/tmp` 独立 clone 提交并开 PR；PR 描述写明基线、改动、测试与未验证项。
2. PR CI 若失败，按实际日志修复并重新推送；不自行合并、不触发 Review。
3. 设备验收覆盖：对话框列出多版本与总占用、当前版本卸载禁用、非当前卸载后刷新、较新版本切换成功且元数据更新、较旧版本按钮禁用并显示“需恢复匹配的数据快照”、运行中禁用操作；另验证 Node 降级后目标版本不兼容、目标依赖锁损坏、当前程序已卸载时切换均被拒绝且指针/快照未变。

### 约束与风险

- 数据/设备/权限边界：切换只创建私有数据快照并改写 `installed.json`；不删除旧版本目录、不恢复旧数据、不操作设备。
- 关键决策及理由：清单与切换策略抽成纯 Kotlin 以便 JVM 回归；占用优先按 `unix:blocks` 分配块统计并回退逻辑长度；`verified` 采用依赖锁哈希 + `.web-verified` 双重依据，缺少标记时仍列出但显示“未校验”；降级继续沿用现有数据兼容规则，不绕过。
- 失败尝试与避免重复踩坑的结论：首个符号链接测试把真实目标放在 `engines` 内，导致目标本身成为合法安装；已改为放在 `engines` 外。占用优先统计实际分配块，`unix:blocks` 不可用时回退逻辑长度，界面文案使用“占用”。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未生成 APK/ZIP。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未发布、未合并。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：未删除历史记录；本次仅新增记录与状态条目，避免覆盖其他并发 Worker 的结论。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。

<a id="20261109-1700-dsh-cold-start"></a>

## 20261109-1700-dsh-cold-start

- Agent/会话标识：android_agent-27（DSH Worker，AO 独立 worktree）
- 时间：2026-11-09（本地时区）

### 当前状态

- 已实现：启动期路径缓存（宿主复制 `assets/dsh-startup/path-cache.cjs` 到 `files/dsh-startup-tools`，绑定 `/opt/tanuki-startup`，仅绑定成功才注入 `--require`）；缓存只覆盖 `/opt/node`、`/opt/engines`、`/opt/pnpm`、`/opt/tanuki`、`/opt/tanuki-phone`、`/opt/tanuki-startup` 的绝对路径；含 `.`/`..`/空段的路径直接交原生，避免符号链接后的 `..` 被词法折叠造成串项。缺失路径/非只读目录/非 `r` 读取不缓存；官方 `dsh web:` 地址行或默认 30 秒超时后卸载；`TANUKI_DSH_PATH_CACHE=0|false|off|no` 关闭；缓存异常逐调用回退原生。`TANUKI_DSH_PATH_CACHE`/`TANUKI_DSH_PATH_CACHE_TIMEOUT_MS` 经 `DshStartupConfig` 校验后显式写入 guest 环境，保留 `env -i` 清理边界。
- 已实现：`MainActivity.onCreate()` 在 `setContent` 前调用 `RuntimeManager.requestEarlyStart()`；只复用既有前台服务与 `RuntimeManager.start()`；条件为 `READY + STOPPED + 无 job/child + 非 stopping + 无 RECOVERY_REQUIRED`，并通过 `Snapshot.operationActive` 与独立更新忙碌信号排除 STAGING/TESTING/RESTORING 等状态；仅 Launcher `MAIN + LAUNCHER` 冷启动触发，仪器测试/通知/内部显式启动跳过；每进程一次；服务端 `START_EARLY` 再次校验，条件变化静默返回，不误置 `FAILED`。
- 已实现：设置 → 运行与后台新增“启动应用时自动运行 DSH”，默认开启，可关闭；只自动运行上次正常停止的实例。
- 已验证：`compileModernDebugKotlin` 与 `compileModernDebugAndroidTestKotlin` BUILD SUCCESSFUL；合并后完整 JVM 306 tests / 0 failures / 0 errors / 0 skipped（含 `EarlyRuntimeStartTest` 17 项、`DshStartupConfigTest` 6 项，以及 main 的安全模式 32 项、聊天通知 18 项与 XLSX 回归）；`lintModernDebug` BUILD SUCCESSFUL；`node scripts/test-dsh-path-cache.mjs` 10 场景 PASS。
- 待验证：真实 Android/PRoot 内 DSH 启动、shim 实际命中率与冷启动耗时、首帧提前启动的设备时序、设置开关 UI、assemble、真机。
- 当前分支 `ao/cold-start/root`，基线 `2f3fac9`；已合并 `origin/main=c2f1b44`（含安全模式 PR #25、XLSX PR #23、聊天通知 PR #22），解决 `RuntimeManager.start()` 与文档/印章冲突；无运行中设备任务。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/assets/dsh-startup/path-cache.cjs` | 新增 Node 启动期只读路径缓存 shim；接管 realpathSync/native、existsSync、只读 package.json readFileSync；地址行/超时/关闭开关/异常回退。 |
| `app/src/main/kotlin/com/tanuki/runtime/DshStartup.kt` | 新增宿主准备与 `DshStartupConfig`：安全复制 shim、拒绝符号链接重定向、原子写入；校验并生成 guest 环境变量。 |
| `app/src/main/kotlin/com/tanuki/runtime/Launcher.kt` | 新增 `startupTools`/`startupConfig`；安全绑定 `/opt/tanuki-startup`，仅在绑定成功时注入 `--require` 与受控环境变量。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeManager.kt` | `web` 启动前准备 shim 并传入 Launcher；新增 `requestEarlyStart`、`earlyConditions`、`start(early)`；通过 `registerBusySignal` 纳入独立更新任务。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeService.kt` | 新增 `START_EARLY` 动作，复用既有前台服务。 |
| `app/src/main/kotlin/com/tanuki/runtime/EarlyRuntimeStart.kt` | 新增纯 Kotlin 提前启动条件、真实 Snapshot 映射与每进程一次的门。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 首帧前读取用户开关并请求提前启动；设置页开关持久化。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | UI model/callback 增加 `earlyStart` 与开关入口；更新运行与后台说明。 |
| `app/src/test/kotlin/com/tanuki/EarlyRuntimeStartTest.kt` | 新增 17 项 JVM 回归：真实 Snapshot 的 STAGING/TESTING/RESTORING 等状态、独立更新 job、就绪/停止、安装/子进程/停止/恢复阻塞、幂等、失败后可重试。 |
| `scripts/test-dsh-path-cache.mjs` | 新增可执行 Node 回归，10 个场景独立子进程运行；新增符号链接+`..`交叉命中。 |
| `.github/workflows/verify.yml` | Host CI 增加 Node 路径缓存回归步骤。 |
| `docs/INTERFACES.md` | 启动命令与新增 1.1.1/1.1.2 契约：shim 挂载/卸载/关闭开关与提前启动条件。 |
| `docs/DESIGN.md` | 更新前台服务启动语义与启动期缓存边界。 |
| `docs/TEST-REPORT.md` | 记录本轮实际命令与结果、未验证范围。 |
| `docs/AGENT-STATE.md` | 增加本轮状态与交接链接。 |
| `app/src/test/kotlin/com/tanuki/DshStartupConfigTest.kt` | 新增 6 项 JVM 回归：默认值、关闭值、超时校验/边界、绑定前不输出环境、读取异常回退。 |
| `docs/handoffs/20261109-1700-dsh-cold-start.md` | 本记录。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew --no-daemon --console=plain -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin` | macOS ARM64、JDK17.0.21+9、Gradle8.13；BUILD SUCCESSFUL（7m13s，19 tasks，含原生重建） | 编译通过；既有弃用/平台告警保留，不代表设备运行。 |
| 合并后 `./gradlew -x buildNativeRuntime :app:compileModernDebugKotlin :app:testModernDebugUnitTest :app:lintModernDebug` | BUILD SUCCESSFUL（2m54s）；45 个结果文件、306 tests / 0 failures / 0 errors / 0 skipped | 含 `EarlyRuntimeStartTest` 17/17、`DshStartupConfigTest` 6/6、`RuntimeFailurePolicyTest` 32/32、聊天通知 18 项与 XLSX 回归；未跑设备仪器。 |
| `./gradlew ... :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin` | BUILD SUCCESSFUL（2m38s） | 验证 UI model/callback 改动与 AndroidTest 编译。 |
| `./gradlew ... :app:lintModernDebug` | 合并后与 JVM 同次 BUILD SUCCESSFUL | 最终代码通过 lint；未跑 assemble/设备。 |
| `node scripts/test-dsh-path-cache.mjs` | PASS，10/10 场景 | 覆盖缓存命中、符号链接/缺失链接、符号链接+`..`交叉命中、缺失路径不缓存、非只读直通、异常编码回退、卸载、地址行、超时、关闭开关；宿主 Node 26.13.0，CI 使用 Node 22。 |
| 只读核对 | 基线 `git rev-parse HEAD=2f3fac9…`；工作区仅本次改动 | 未触发额外 Review、未合并、未发布。 |
| PR #27 CI | 合并后本地完整 JVM/lint 通过；推送后以新 head 的 PR CI 为准 | 仅证明宿主/编译/JVM/lint；不代表设备/真机/冷启动计时。 |

- 代码审查后收紧：路径缓存绑定失败时回退原生启动；提前启动条件改为真实 `Snapshot.operationActive` + 独立更新忙碌信号，并在服务端复检；路径键拒绝 `.`/`..`/空段，符号链接后 `..` 交原生；配置经 `DshStartupConfig` 显式传入 guest。合并 `origin/main=c2f1b44` 后完整 JVM 为 306 tests / 0 failures / 0 errors / 0 skipped，Node 10/10 场景通过；`lintModernDebug` 也通过。另加 Launcher `MAIN + LAUNCHER` 判定，避免仪器测试/内部显式启动在已安装环境上意外拉起服务。
- 前一轮结果：`AGENT-STATE` 中 205 JVM 等为 android_agent-25 历史结果；合并后本轮新跑为 306 JVM。
- 未测试、超时、失败和暂未复现：真实 PRoot 内 `--require` 挂载、DSH 依赖对 `fs` 的实际调用路径与命中率、真机冷启动 `TotalTime`、提前启动与首帧的实际并行效果、设置开关 UI、assemble、Android 仪器、真机/MIUI/低 API。

### 未完成与暂停项

- 未完成：PR #27 已创建；3 条 P2 已修复并推送；已合并 `origin/main=c2f1b44` 解决冲突，待推送 merge head 与 CI/复审。未合并、未触发额外 Review。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收继续暂停；本轮未触碰。
- 设备 QA：本任务不自行运行设备测试，冷启动计时留给独立 Android QA。

### 下一步

1. 在独立 `/tmp` clone 以 `leezzou <115924933+leezzou@users.noreply.github.com>` 作者/committer 提交，推送 `ao/cold-start/root` 并创建 PR；PR 正文写清改动、验证命令、未验证项与风险，不合并、不触发 Review。
2. 独立 Android QA 在明确 AVD/serial 上验证：DSH 已安装且上次正常停止时冷启动自动拉起；`dsh web:` 地址行后 shim 卸载；真实启动耗时与首帧时序；关闭设置后不自动启动；安装中/运行中不重复启动。
3. 如需合并，先按项目门禁完成当前 head 的独立 Review、CI 与适用 QA；未获明确授权不合并/不发布。

### 约束与风险

- 数据/设备/权限边界：未读取/输出真实 Key、Cookie、token、会话或备份；未安装/卸载/清数据/wipe 设备；未改 PRoot 边界、官方 npm 包、DSH UI/协议；未关闭审批/TLS/哈希校验。
- 关键决策：路径缓存只覆盖显式只读目录，缺失结果不做负缓存；异常逐调用回退；shim 准备/绑定失败时不注入 `--require`，保持原生启动。提前启动只允许干净的 `STOPPED`，`FAILED`/`INTERRUPTED` 不自动重试，避免掩盖真实问题或重放任务。
- 风险：Node 侧只覆盖 `require('fs')`/属性访问；ESM 命名导入可能绕过 monkey patch，实际收益需设备测量。默认开启自动运行是行为变化，已提供设置开关并在文档中说明。`--require` 仅在绑定成功时注入，但若客体 rootfs 被恶意重定向，绑定检查会失败并停止启动（安全优先）。
- 失败尝试与避免重复踩坑：首次 Node 回归因场景间共用 fixture、前一场景删除文件导致 `nonReadonly` 失败；改为每个场景独立 fixture 后 9/9 通过。

### 产物与校验

- 新/旧 APK、源码 ZIP：本轮未生成/未更新 APK 或源码 ZIP；仅源码与文档改动。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布。
- 提交/推送前清理的过期记录：本任务未删除历史交接；`docs/AGENT-STATE.md` 仅新增本轮链接与状态。并发 Worker 的状态/印章由编排方串行汇总时需复核本记录。
- 文档或脚本变化后需运行 `python3 scripts/check-handoff.py --record docs/handoffs/20261109-1700-dsh-cold-start.md`、`python3 scripts/check-handoff.py`、`python3 scripts/package-source.py` 并核对 `artifacts/SOURCE-SHA256SUMS`；源码 ZIP 为工作材料，不提交。

<a id="20261109-1742-dsh-chat-notifications"></a>

## 20261109-1742-dsh-chat-notifications

- Agent/会话标识：android_agent-29（DSH Worker）
- 时间：2026-11-09 17:42 CST

### 当前状态

- 已实现：页面脚本在 `/api/remote.mux` 的 `$events` 流中只识别 `api-session/status`、`api-session/error`、`approval/request`、`user-questions/request`；原生按 session + DSH 版本 + event 去重；前台且 `[data-conversation-session]` 与事件会话一致时抑制；通知点击带 session extra，回到 DSH 页后点击侧栏 `[data-row-key="session:<id>"]`。
- 已实现：`dsh-chat` 渠道、每个会话独立通知 id、`com.tanuki.DSH_CHAT` group + summary、Android 13 权限请求、渠道/应用通知设置回退、应用内总开关、设置页状态与“上次未提醒”原因。
- 已实现：通知正文固定为“回合完成/等待确认/出错/权限审批”四类最小文案，不使用会话标题、消息、工具名、错误原文或路径。
- 已验证：`compileModernDebugKotlin`、完整 `testModernDebugUnitTest`（223 tests / 0 failures / 0 errors / 0 skipped）、`:app:lintModernDebug` 均 BUILD SUCCESSFUL；新增策略/身份/协议 18 项 JVM 回归；`node scripts/test-chat-session-open.mjs` 通过收起侧栏→展开分组→点击→确认选中的状态机回归；注入脚本通过 `node --check` 和 5 类合成帧的本地模拟，未把 `secret` 正文带出。
- 待验证：真实设备上的 Android 13 权限弹窗、通知渠道/group/summary 展示、点击通知切会话、后台/息屏 WebSocket 事件、MIUI/其他 ROM 的通知/电池/自启动限制、真实 DSH 页面（当前无设备执行）。
- Rebase：已 rebase 到 `main=5fd1ab4bb269888289445ed42d8a98424e9fc680`（含 PR #25 安全模式）；仅 `docs/AGENT-STATE.md`/`docs/handoffs/CURRENT.json` 文档冲突，保留 main 当前交接与本 PR 并行记录，代码无冲突。
- 当前分支：`ao/dsh-chat-notify/root`（本 worktree）；提交分支 `ao/dsh-chat-notify/chat-notifications`（`/tmp/tanuki-chat-notify` 独立 clone），PR [#22](https://github.com/leezzou/tanuki/pull/22)，实现提交 `09396bc`；后续文档/CI 记录提交会推进 head，head 以 PR 页面为准；基线 `main=2f3fac9301c7c3484df85e99091e8f63dddc0a48`，已 rebase 到 `main=5fd1ab4bb269888289445ed42d8a98424e9fc680`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/notify/ChatNotificationPolicy.kt` | 纯策略：事件类型/权限状态/去重/前台抑制、`ChatTurnState`、抑制原因文案。 |
| `app/src/main/kotlin/com/tanuki/notify/ChatNotificationStatus.kt` | 设置页可读状态；不含会话内容。 |
| `app/src/main/kotlin/com/tanuki/notify/ChatNotificationCopy.kt` | 四类最小通知文案。 |
| `app/src/main/kotlin/com/tanuki/notify/ChatNotifier.kt` | 渠道、Android 13 权限、group/summary、稳定会话 tag、PendingIntent data 身份、设置 Intent、应用内总开关；新事件不再被 `setOnlyAlertOnce` 压掉。 |
| `app/src/main/kotlin/com/tanuki/notify/ChatNotificationRouting.kt` | 跨进程稳定的通知 tag/固定 id、会话级 PendingIntent action/data/requestCode；子通知可再次提醒，摘要静默。 |
| `app/src/main/kotlin/com/tanuki/notify/ChatNotificationCenter.kt` | 运行时编排：版本、回合状态、去重、权限/前台、状态流。 |
| `app/src/main/kotlin/com/tanuki/web/DshChatProtocol.kt` | 官方事件名/流路径、最小 JSON 校验、DocumentStartScript 旁路脚本；打开会话先展开收起侧栏、清除非空搜索过滤、展开工作区分组，确认实际选中后才返回成功。 |
| `app/src/main/kotlin/com/tanuki/web/DshChatBridge.kt` | WebView 精确 origin 的 DocumentStartScript + WebMessageListener 安装/替换/移除。 |
| `app/src/main/kotlin/com/tanuki/web/DshWebView.kt` | 连接时安装旁路、销毁时移除、`openSession` 回调式打开会话。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 通知点击 extra、DSH 页导航、前台/标签可见状态、权限请求与设置入口、通知状态对话框。 |
| `app/src/main/kotlin/com/tanuki/TanukiApp.kt` | 创建通知中心并启动时建立渠道。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 设置页“DSH 聊天通知”总开关/状态/入口；DSH 可见回调；通知点击导航。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` 模型/回调 | 新增 `chatNotifications`、`dshVisible`、`chatNotifications`、`toggleChatNotifications`、`dshOpenRequest`。 |
| `app/src/test/kotlin/com/tanuki/ChatNotificationPolicyTest.kt` | 10 项纯 JVM：基线 false、true→false、重复 false、前台同/异会话、后台、session/version/event 去重、旧通知未清除时新事件仍提醒、权限/渠道/用户开关、Android<33、非法 id、状态文案。 |
| `app/src/test/kotlin/com/tanuki/ChatNotificationRoutingTest.kt` | 4 项纯 JVM：同会话跨进程稳定 tag/id、不同会话 Identity 不同、新事件可再次提醒/摘要静默、非法 id 拒绝。 |
| `app/src/test/kotlin/com/tanuki/DshChatProtocolTest.kt` | 4 项纯 JVM：四类事件解析、运行中补种、非法载荷、脚本只读边界/打开会话函数。 |
| `scripts/test-chat-session-open.mjs` | Node 假 DOM 回归：侧栏初始收起、保留非空搜索且当前为其他会话时，展开侧栏/清除搜索/展开分组、只点一次、确认选中后才成功。 |
| `.github/workflows/verify.yml` | Host job 增加 `node scripts/test-chat-session-open.mjs`。 |
| `docs/INTERFACES.md` | 新增 1.6 节，固定页面旁路来源、字段、抑制、去重、点击与限制。 |
| `docs/TEST-REPORT.md` | 新增本轮实际命令、结果和未验证范围。 |
| `docs/AGENT-STATE.md` | 更新当前交接、基线、实现/验证/待验证、环境与下一步。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin` | BUILD SUCCESSFUL（首次约 4m29s） | 本 worktree，JDK17/SDK36；只有既有弃用警告。 |
| 同上 `:app:testModernDebugUnitTest --tests 'com.tanuki.ChatNotificationPolicyTest' --tests 'com.tanuki.ChatNotificationRoutingTest' --tests 'com.tanuki.DshChatProtocolTest'` | BUILD SUCCESSFUL，18 tests / 0 failures | 新增 10 + 4 + 4 项。 |
| `node scripts/test-chat-session-open.mjs` | `CHAT_SESSION_OPEN_TEST_OK` | 收起侧栏 + 非空搜索 + 其他会话、搜索异步清除、分组展开、已选中目标、非法 id。 |
| 同上完整 `:app:testModernDebugUnitTest` | BUILD SUCCESSFUL，223 tests / 0 failures / 0 errors / 0 skipped（39 个 XML 结果文件） | 既有 205 + 新增 18；不是设备测试。 |
| 同上 `:app:lintModernDebug` | BUILD SUCCESSFUL（约 4m44s） | lint 报告 `app/build/reports/lint-results-modernDebug.html`；未构建 APK。 |
| `python3` 抽取注入脚本 + `node --check /tmp/tanuki-chat-bridge.js` | JS_SYNTAX_OK | 仅语法。 |
| `/tmp` 合成 Fake WebSocket 脚本 | `JS_BRIDGE_TEST_OK 5` | 状态 true/false、error、approval、question 五帧均被识别；`secret` 工具名/错误文本/消息未出现在原生消息中；脚本未读取 `innerText`/`textContent`/localStorage。 |
| `git diff --check` | 通过 | 见提交前检查。 |
| PR #22 GitHub Actions `verify` | Host checks / Android build+JVM+lint 全部 pass | PR 页面；首轮 Android 曾因既有 `NpmAdaptiveWorkerPoolTest` 并发时序失败，重跑通过；Review 修复提交 `21cc811` 与搜索过滤修复提交 `462d266` 的 Host/Android 两 job 均全绿，未改该并发测试。 |
| rebase 到 `main=5fd1ab4`（安全模式 PR #25 + XLSX PR #23）后 `:app:compileModernDebugKotlin` + 完整 `:app:testModernDebugUnitTest` + `:app:lintModernDebug` | BUILD SUCCESSFUL，283 tests / 0 failures / 0 errors / 0 skipped | 在 `/tmp/tanuki-chat-notify` rebase clone 复跑；聊天通知 18 项与安全模式 32 项、XLSX 28 项共存。 |

- 本轮新跑的是上述编译、完整 JVM、lint 和本地脚本模拟；223 项中既有 205 项为同一任务内完整套件结果，不是历史摘要。
- 未执行：assemble、Android 仪器测试、真实设备/模拟器安装、Android 13 权限弹窗、真实通知展示/点击、真实 DSH WebSocket、后台/息屏、MIUI/其他 ROM、WorkManager/网络。

### 未完成与暂停项

- 未完成：独立 Codex High Review；设备/ROM 通知与点击会话验收。
- 未完成：真机上确认 `WebViewFeature.DOCUMENT_START_SCRIPT` / `WEB_MESSAGE_LISTENER` 可用；若 WebView 不支持则旁路不安装，应用不会伪造通知。
- 未完成：DSH 升级后复核官方转发事件表和页面 `data-conversation-session` / `data-row-key` 属性是否变化。
- 外部阻塞：本机无授权设备执行；不把宿主/JVM 结果冒充 Android 执行。
- 用户暂停保持：API Key 保存、真实模型/账号验收、真机验收。

### 下一步

1. 4 条 Review 修复已推送 PR [#22](https://github.com/leezzou/tanuki/pull/22)（侧栏搜索过滤修复提交 `462d266`），CI 通过且全部 thread 已 resolve；等待独立 Codex High Review 与设备 QA，不合并、不触发 `ao review trigger`。
2. 由 Orchestrator 安排独立 Codex High Review；设备 QA 需独立会话、确认无真实任务后执行。
3. 设备验收至少覆盖：Android 13 首次权限请求/拒绝后设置入口、四类通知与 group/summary、同会话新事件再次提醒、前台同会话抑制、后台/息屏送达、侧栏收起且保留搜索条件时点击其他会话通知、MIUI 通知/电池/自启动设置、DSH 运行中加载页面后的完成提醒。
4. 若 DSH 官方事件或页面属性变化，先更新 `docs/INTERFACES.md` 和回归，再改实现；不得用猜测事件或读取正文替代。

### 约束与风险

- 数据/设备/权限边界：不读取/输出 token、Cookie、会话内容、工具参数、错误原文；通知只含类型；不新增原生 JS Bridge 方法；WebMessageListener 只接受当前精确 origin 和主框架。
- 关键决策：优先使用官方 `/api/remote.mux` `$events` 转发事件；不另建原生 WebSocket 复制 Cookie；页面只旁路现有消息监听，不替换 WebSocket；回合完成只在观察到 true→false 时提醒；页面已在运行时通过会话行 `data-state="ongoing"` 补种一次 running。通知用会话 tag + 固定 id 跨进程稳定；PendingIntent 用会话级 data/action 区分，不靠进程内计数。
- 风险：DSH 是官方 npm 包，事件表和 HTML 属性属于页面内部契约；升级后需复核。错误来源为官方 `api-session/error`；若某类失败只体现在 `turn/end` 而不触发该事件，只会按回合完成提示，需在 DSH 升级后复核。WebView 后台可能被系统节流，通知可能延迟；ROM 可能拦截后台网络/通知。点击会话会先点侧栏展开入口（按 aria-label/内部 toggle 兜底）、清除非空搜索（官方清除按钮优先，输入框 setter+input 兜底）、展开折叠工作区分组，最多重试 15 次；归档或清搜索后仍被其他过滤隐藏时显示 Toast，不谎报成功。
- 失败尝试：未尝试通过原生 Cookie 直接开 WebSocket 或 `addJavascriptInterface`，避免扩大凭据/攻击面。
- 未删除旧交接记录：旧记录的设备验收等独有未完成项尚未完整迁移，不属于“可清理的过期记录”；保留为验证依据。

### 产物与校验

- 本轮不生成 APK；PR 只含源码、测试和文档。
- 提交前需运行 `python3 scripts/check-handoff.py --record docs/handoffs/20261109-1742-dsh-chat-notifications.md`、`python3 scripts/check-handoff.py`、`python3 scripts/package-source.py`，并核对 `artifacts/SOURCE-SHA256SUMS`；源码包为工作材料，不提交。
- 未发布、未覆盖安装、未切换签名。PR：https://github.com/leezzou/tanuki/pull/22（实现提交 `09396bc`，head 以 PR 页面为准）。

<a id="20261109-1802-device-condition-policy"></a>

## 20261109-1802-device-condition-policy

- Agent/会话标识：android_agent-30（DSH Worker）
- 时间：2026-11-09 18:02 CST

### 当前状态

- 已实现：上述策略与接入点；中文注释；开关/阈值；状态持久化。
- 已验证：合并 main 后完整 JVM+lint 通过；`DeviceConditionPolicyTest` 25 项、`DeviceConditionRefreshQueueTest` 2 项、`DeviceConditionInitializationTest` 2 项、`DeviceConditionConfigWriterTest` 1 项、`NpmDownloadPolicyTest` 7 项、`NpmAdaptiveWorkerPoolTest` 2 项通过；完整 JVM 套件 424 tests / 0 failures / 0 errors / 0 skipped。
- 待验证：真机/模拟器温控回调、电池广播、系统低内存、WorkManager 真实调度与重启恢复、设置页 UI、PDF 降级实际像素、独立 Review。
- 当前分支：`ao/thermal-policy/device-condition-policy`，已合并 `origin/main=5fd1ab4` 解决冲突；在 `/tmp/tanuki-thermal-30` 独立 clone 验证/提交，不使用共享 worktree 的 `.git` 写操作。PR [#21](https://github.com/leezzou/tanuki/pull/21) 已创建，未合并、未触发 Review。
- 没有运行中的设备安装或真实 DSH 任务；本轮未启动/停止模拟器，未安装 APK。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/power/DeviceConditionPolicy.kt` | 新增纯策略：状态枚举、热/电/内存信号、迟滞、工作矩阵、具体检查登记/恢复结果；不依赖 Android |
| `app/src/main/kotlin/com/tanuki/power/DeviceConditionMonitor.kt` | 新增 Android 采样：PowerManager thermal status、电池/充电广播、ActivityManager 低内存；后台串行持久化；入队确认后清除待办；进程重启恢复 |
| `app/src/main/kotlin/com/tanuki/power/DeviceConditionRefreshQueue.kt` | 合并主线程/广播刷新请求，`request()` 不内联采样或写盘 |
| `app/src/main/kotlin/com/tanuki/power/DeviceConditionInitialization.kt` | 一次性初始化屏障，Worker/设置等待 restore 完成后再评估和写盘 |
| `app/src/main/kotlin/com/tanuki/power/DeviceConditionConfigWriter.kt` | 串行设置写入：锁内读取最新 config 后原子写，避免旧值覆盖 |
| `app/src/main/kotlin/com/tanuki/TanukiApp.kt` | 创建并启动监控；绑定恢复回调到 WorkManager 补跑 |
| `app/src/main/kotlin/com/tanuki/update/UpdateCheckWorkers.kt` | 周期 Worker 在热/低电时只登记自己并成功返回；恢复时只按实际登记项补跑 |
| `app/src/main/kotlin/com/tanuki/runtime/NpmDownloadPolicy.kt` | `NpmConcurrencyPolicy` 增加外部 `capProvider`，`current()` 取自适应目标与设备额度较小值 |
| `app/src/main/kotlin/com/tanuki/runtime/NpmPackageDownloads.kt` | 注入当前设备 `workerCap`；降档由现有池子自然退休多余 worker |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 模型加入设备状态与两个回调；日志展示行数按策略裁剪；设置页新增开关/阈值/当前额度 |
| `app/src/main/kotlin/com/tanuki/ui/SetupScreens.kt` | 首页非正常状态卡片，展示原因、额度和恢复说明 |
| `app/src/main/kotlin/com/tanuki/ui/Labels.kt` | 设备状态中文标签 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspacePdfPreview.kt` | 渲染入口接受长边/像素上限；默认行为不变 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt`、`WorkspaceFilesPage.kt` | 透传预览上限 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 订阅设备状态，接入模型/设置回调/PDF 上限 |
| `app/src/test/kotlin/com/tanuki/DeviceConditionPolicyTest.kt` | 新增 25 项纯策略回归，含待办确认/重试/代次复用/准入绑定/PDF 双上限 |
| `app/src/test/kotlin/com/tanuki/DeviceConditionRefreshQueueTest.kt` | 新增 2 项后台刷新队列回归 |
| `app/src/test/kotlin/com/tanuki/DeviceConditionInitializationTest.kt` | 新增 2 项初始化并发回归：暂停 restore 时提前启动 Worker、初始化只跑一次且失败释放等待者 |
| `app/src/test/kotlin/com/tanuki/DeviceConditionConfigWriterTest.kt` | 新增配置保存并发回归：首次保存暂停后第二次仍落盘最后选择 |
| `app/src/test/kotlin/com/tanuki/NpmDownloadPolicyTest.kt` | 新增外部并发上限与恢复回归 |
| `docs/DEVICE-CONDITION-POLICY.md` | 新增状态表、工作矩阵、接入点、恢复和未验证边界 |
| `docs/AGENT-STATE.md`、`docs/TEST-REPORT.md`、本记录 | 记录本轮状态和证据 |

### Review 修复（P2，三条）

1. 只为实际跳过的到期检查登记补跑：策略不再自动登记；`DeviceWork` 拆为 `APK_UPDATE_CHECK`/`DSH_UPDATE_CHECK`，被阻塞的 Worker 调用 `beginBackgroundWork(check)` 只登记自己；`resumeDeferred(checks)` 只入队实际登记项，空集合直接返回。新增无 Worker 跳过、只恢复实际跳过项、登记幂等回归。
2. 监控采样和持久化移出主线程：`refresh()` 只向 `DeviceConditionRefreshQueue` 投递合并请求，后台 collector 串行采样/评估/写盘；设置也走后台写盘；持久化比较排除 `updatedAtMillis`，常规采样不重复写盘。新增 `DeviceConditionRefreshQueueTest` 2 项，覆盖 `request()` 不内联执行写盘 worker 和突发请求合并。
3. 确认补跑持久入队后再清除待办：`evaluate()` 保留 `deferredChecks`，`resumeDeferred()` 返回确认入队的检查集合，监控调用 `acknowledgeChecks()` 后才清除；WorkManager `Operation` 结果确认失败/超时则保留待办，后续采样重试。新增入队失败保留重试、部分确认只清除已确认项、进程重启窗口保留待办回归。
4. 恢复完成前阻止 Worker 评估/写盘：新增 `DeviceConditionInitialization` 一次性屏障，`beginBackgroundWork()` 与设置入口等待 `restore()` 完成；新增并发回归模拟暂停 restore 后提前启动 Worker，确认 Worker 不会先用默认状态覆盖磁盘待办；初始化失败也释放等待者，避免 Worker 永久挂起。
5. 避免旧入队确认清除新一轮待办：每项待办增加登记代次，`acknowledgeChecks()` 只清除与入队时记录代次一致的项；确认回调阻塞期间同一检查被重新登记时保留新待办。新增“阻塞确认期间重新登记仍保留新待办”回归。
6. 代次不能重用：单独保留每项检查已发放的最大代次，确认清除待办后不删除计数；下一轮登记继续递增，避免“旧确认悬停、另一确认先清除、重新登记又从 1 开始”时被旧确认误删。新增完整三段窗口回归。
7. 设置保存串行化：新增 `DeviceConditionConfigWriter`，用单一 Mutex 覆盖“读取最新 config -> 原子写入”全过程；连续选择不同阈值不会让旧快照后写覆盖。新增“首次保存暂停、第二次保存完成、释放首次后最终落盘仍是最后一次选择”的回归。

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin --stacktrace` | JDK17/SDK36，`/tmp/tanuki-thermal-30`，BUILD SUCCESSFUL（5m29s，19 tasks） | 编译通过；只有既有弃用警告 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.DeviceConditionPolicyTest' --tests 'com.tanuki.DeviceConditionRefreshQueueTest' --tests 'com.tanuki.DeviceConditionInitializationTest' --tests 'com.tanuki.NpmDownloadPolicyTest' --tests 'com.tanuki.NpmAdaptiveWorkerPoolTest'` | BUILD SUCCESSFUL；25/1/2/2/7/2 tests，0 failures/errors/skipped | 目标回归通过；不是完整套件 |
| `./gradlew :app:testModernDebugUnitTest :app:lintModernDebug --rerun-tasks` | BUILD SUCCESSFUL（2m17s，35 tasks）；424 tests / 0 failures / 0 errors / 0 skipped；lint 0 Error | 合并 main 后完整 JVM+lint；不代表设备测试 |
| `./gradlew :app:lintModernDebug` | 与上一行组合执行；0 个 lint Error | 本地 lint 通过；不替代 assemble/仪器/真机 |

- 哪些是前一轮结果，哪些是本次新验证：本记录中的编译与目标回归均为本轮在 `/tmp` clone 新跑；前轮 205 tests 等历史数字未复用。
- 未测试、超时、失败和暂未复现的情况：真实温控/电池/低内存、WorkManager 调度/重启恢复、通知、设置页交互、PDF 降级像素、assemble/仪器、独立 Review 均未执行；lint 已在本轮通过。

#### 最终结果

- 完整 JVM 与 lint：`./gradlew :app:testModernDebugUnitTest :app:lintModernDebug --rerun-tasks` → BUILD SUCCESSFUL（2m17s，35 tasks executed）；59 个结果文件，424 tests / 0 failures / 0 errors / 0 skipped。

### 未完成与暂停项

- 未完成：PR #21 等待独立 Codex High Review；真机/模拟器温控、低电/低内存、WorkManager 真实调度与重启恢复、通知、设置页 UI、PDF 降级像素、assemble/仪器测试。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号、真机验收继续暂停；合并/发布/触发 Review 未获授权。

### 下一步

1. PR [#21](https://github.com/leezzou/tanuki/pull/21) 已创建并推送；等待独立 Review，不合并、不触发 Review。
2. 由 Orchestrator/独立 QA 在无真实任务的设备上验证：加热触发 thermal status、充电/低电注入、低内存、WorkManager 周期与恢复、设置页开关/阈值、PDF 预览降级。
3. Review 意见逐条修复并重跑适用的 compile/JVM；通过且用户明确授权后才合并。

### 约束与风险

- 数据/设备/权限边界：未读取真实 Key、Cookie、token 或会话；未安装/启动设备；未修改既有安全/暂停项。
- 关键决策：策略只降并发/延后，不取消；前台与 DSH 任务列为受保护类别；后台更新检查由被阻塞的具体 Worker 登记，恢复时只补跑实际待办；低电退出阈值固定比进入阈值高 5 个百分点；低内存只参与组合状态，不写入热状态字段。
- 风险：`PowerManager` thermal status 仅 API 29+；低内存使用系统 `MemoryInfo.lowMemory`，可能与单应用内存压力不完全一致；只有实际被跳过的 APK/DSH 检查才会补跑，无 Worker 跳过时不发请求；PDF 降级只覆盖内置 PDF，图片/DOCX 未接入。
- 失败尝试与避免重复踩坑：没有把内存降级写入 `thermalCondition`（否则内存解除后还会被热迟滞多拖 30 秒）；并发测试需累计 32 次成功才能到 16 路。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未构建/发布 APK；提交前运行 `package-source.py` 仅核对 487 个公开源文件，工作材料不提交。
- 是否覆盖安装/签名范围/未发布事项：未安装、未签名、未发布。
- 提交/推送前清理的过期记录：未删除任何旧交接；它们仍被 `AGENT-STATE` 或并行任务引用。新增本记录并更新当前状态。
- 文档或脚本变化后仍需：运行 `python3 scripts/check-handoff.py --record docs/handoffs/20261109-1802-device-condition-policy.md`、`python3 scripts/check-handoff.py`；如需源码包再运行 `python3 scripts/package-source.py`。

<a id="20261109-1807-xlsx-native-preview"></a>

## 20261109-1807-xlsx-native-preview

- Agent/会话标识：android_agent-32（Tanuki DSH Worker）
- 时间：2026-11-09 18:07 CST

### 当前状态

- 已实现：XLSX 只读解析与原生表格预览已接入 `WorkspaceFileViewer`；支持多 sheet、行号/列标表头、冻结行/列、列宽/行高、合并单元格、基础字体/填充/四边边框、水平/垂直对齐、数字/百分比/日期/时间/布尔/错误值、公式缓存值与 `date1904`。
- 已验证（本轮新跑）：`compileModernDebugKotlin` BUILD SUCCESSFUL；完整 `testModernDebugUnitTest` BUILD SUCCESSFUL，233 tests / 0 failures / 0 errors / 0 skipped；新增 3 个测试类共 27 项（解析 18、格式化 4、缩放策略 5），并在 `WorkspaceFileKindTest` 增补 1 项，合计 28 项。
- 待验证：真机/模拟器上的双向滚动、冻结区手势、缩放可读性、大表滚动性能、深色主题与 CJK 字体；真实 Excel/WPS 生成文件（含主题色、隐藏行列、复杂格式）；lint、assemble、仪器测试。
- 当前分支：`ao/xlsx-preview/xlsx-native-preview`（从会话分支 `ao/xlsx-preview/root` 创建），基于 `main=2f3fac9301c7c3484df85e99091e8f63dddc0a48`；独立 `/tmp` clone 提交/推送，PR [#23](https://github.com/leezzou/tanuki/pull/23) 已创建，未触发 AO Review。
- 关键事实与源码：解析与上限在 `app/src/main/kotlin/com/tanuki/storage/XlsxWorkbook.kt`；格式化在 `XlsxValueFormatter.kt`；表格 UI 在 `app/src/main/kotlin/com/tanuki/ui/WorkspaceXlsxPreview.kt`；缩放/尺寸策略在 `XlsxPreviewPolicy.kt`；入口接线在 `WorkspaceFileManager.readXlsx` 与 `WorkspaceFileViewer`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/XlsxWorkbook.kt` | 新增 OOXML XLSX 只读解析器与模型：workbook/rels/sharedStrings/styles/sheet、合并单元格、冻结窗格、列宽行高、公式缓存值；zip 条目/总量/单条目、XML 深度/DTD/实体、sheet/行/列/单元格/共享字符串/合并区域/文本预算上限；合并生成的起点与 covered 占位同样计入共享单元格预算；标准实体/数字字符引用按已解析文本保留；路径穿越与外部链接拒绝；中文错误。 |
| `app/src/main/kotlin/com/tanuki/storage/XlsxValueFormatter.kt` | 新增数字/百分比/日期/时间/布尔格式的纯函数；处理 1900 闰年偏差、1904 日期系统与最小小数位。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt` | `FileKind` 增加 `XLSX`；`.xlsx` 分类与 MIME；新增 `readXlsx`（IO 锁内有界解析，不暴露路径）。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceXlsxPreview.kt` | 新增 Compose 原生表格：sheet 选择、行/列表头、按视口收缩的冻结行/列、合并单元格按跨度占位、样式映射（含单元格字号与垂直对齐）、LazyColumn 行虚拟化、共享水平滚动、按钮缩放、保真边界提示与截断提示。 |
| `app/src/main/kotlin/com/tanuki/ui/XlsxPreviewPolicy.kt` | 新增列字母、0.5–2.0x 缩放步进/clamp、列宽/行高 dp 换算、按视口收缩冻结区与单元格字号换算等纯策略。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | 增加 `XLSX` 分支与全宽布局；未改 PDF/DOCX/图片/视频逻辑。 |
| `app/src/test/kotlin/com/tanuki/XlsxWorkbookParserTest.kt` | 合成 XLSX fixture 回归：多 sheet、类型/公式缓存、样式、合并/冻结、列宽行高、超限、损坏包/缺 workbook/坏 XML、zip 条目与总量/单条目、谎报解压大小的 bomb、共享字符串上限、路径穿越/外部链接、绝对包内路径、DOCTYPE/深层 XML/未定义实体、1904 日期、空表。 |
| `app/src/test/kotlin/com/tanuki/XlsxValueFormatterTest.kt` | 数字/千分位/百分比/日期时间/1900 闰年/1904/布尔回归。 |
| `app/src/test/kotlin/com/tanuki/XlsxPreviewPolicyTest.kt` | 列字母 A/Z/AA、缩放 clamp/步进、列宽行高边界回归。 |
| `app/src/test/kotlin/com/tanuki/WorkspaceFileKindTest.kt` | 增加 `.xlsx` 映射，并确认 `.xls`/`.xlsm` 不被当作已支持。 |
| `docs/WORKSPACE-STORAGE.md` | 更新查看器支持的格式与 XLSX 保真边界，避免旧“Office 仅外部打开”描述过期。 |
| `docs/AGENT-STATE.md`、`docs/TEST-REPORT.md`、本记录、`docs/handoffs/CURRENT.json` | 状态、验证证据与交接印章。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin --console=plain` | BUILD SUCCESSFUL（本轮执行） | 仅编译 modernDebug，未 assemble/安装。 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.Xlsx*' --tests 'com.tanuki.WorkspaceFileKindTest'` | BUILD SUCCESSFUL，30 tests / 0 failures（本轮执行） | 目标回归，含 18 解析 + 4 格式化 + 5 策略 + 3 文件分类（含原有 2 项）。 |
| `./gradlew :app:testModernDebugUnitTest --console=plain` | BUILD SUCCESSFUL，233 tests / 0 failures / 0 errors / 0 skipped（本轮最终复跑） | 全量 JVM；结果目录 `app/build/test-results/testModernDebugUnitTest/`。时间格式回归补 `mm:ss` 的分钟/秒语义。 |
| `./gradlew :app:lintModernDebug` | BUILD SUCCESSFUL（本轮执行） | 未跑 assemble/仪器/真机。 |
| 本记录与状态文档检查 | `python3 scripts/check-handoff.py`、`check-publication.py --git`、`package-source.py` 结果见“产物与校验” | 文档/脚本变化不影响产品行为。 |

- 前一轮结果：不适用；本轮未复用旧 XLSX 测试（此前没有 XLSX 实现）。
- 未测试、超时、失败：无 JVM 失败；真机/模拟器、lint、assemble、仪器、真实 Excel 文件、真实大表性能均未执行。

### 未完成与暂停项

- 未完成：PR [#23](https://github.com/leezzou/tanuki/pull/23) 的 P1（合并占位预算）与 P2（实体字符、冻结区视口、单元格字号）复审意见均已修复并补回归；仍需独立 Codex High Review 与用户明确合并授权。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收继续暂停；本轮未触碰。
- 真机大表（上万行、宽表、合并跨冻结区）滚动与缩放性能未验证，不能据此宣称完整 Excel 兼容。

### 下一步

1. PR [#23](https://github.com/leezzou/tanuki/pull/23) 已创建（base `main`），正文包含支持/不支持清单、改动文件、测试结果与未验证项；不合并、不触发 AO Review。等待编排器/用户决定是否触发独立 Review。
2. 由 Orchestrator/用户决定是否触发当前 head 的独立 Codex High Review；Review 意见逐条修复并重审。
3. 获得设备授权后，在无真实任务的 ARM64 AVD/真机上验证滚动、冻结、缩放、大表与深色主题，并记录 AVD/serial 与 APK 哈希。
4. 未获用户明确授权前不合并、不发布、不创建 tag/Release。

### 约束与风险

- 数据/设备/权限边界：只读工作区文件；不解压到磁盘、不执行宏/公式/外部链接；不读取真实 Key/Cookie/日志；未操作设备。
- 关键决策：不引入第三方 XLSX 库，直接用 `ZipFile` + `XmlPullParser` 读 OOXML；缩放通过重算列宽/行高/字号而非整表 `graphicsLayer`，保证滚动范围与缩放一致；合并单元格只保留起点跨度和文本，跨冻结列时按冻结边界裁剪，跨行边框为近似；界面最多显示 8 个冻结行、3 个冻结列，其余仍随表体滚动，避免冻结区占满手机视口；超限采用截断 + 中文提示，损坏/越界/zip bomb 直接拒绝。
- 保真边界：明确不支持图表、宏、透视表、外部链接、图片、复杂条件格式、隐藏行列、主题色、公式重算和富文本多字体；长文本单行省略号显示。内置预览不宣称完整 Excel。
- 失败尝试/避免重复：Kotlin 字符串模板中 `$label解压后...` 会把中文当作标识符，已改为 `${label}`；共享 `ScrollState` 的多行水平同步需在设备上确认手势体验。

### 产物与校验

- 本轮不产出 APK/源码 ZIP 发布件；交付为源码 PR 与文档。
- 未安装、未签名、未发布；不涉及版本号、证书或发布目录。
- 过期交接清理：本轮未删除既有记录。原因：F1 与视频记录仍承载待设备验收项，DSH #20 与已安装版本管理仍待 Review/PR，均非“已完成且结论已完全迁移”的过期记录；本轮记录、TEMPLATE 与 CURRENT.json 保留。
- 文档/脚本变化后需重新运行交接印章与源码包检查；不把源码 ZIP 自身哈希写回本记录。

<a id="20261109-1810-remote-host-ssh"></a>

## 20261109-1810-remote-host-ssh

- Agent/会话标识：android_agent-34（Tanuki DSH Worker）
- 时间：2026-11-09 19:10 Asia/Shanghai

### 当前状态

- 已实现：直连 SSH + 端口转发 + local/remote origin 切换 + 设置页 + 失败回退 + 加密凭据/主机密钥校验 + JVM 回归。
- 已验证（本轮实际执行）：`compileModernDebugKotlin`、`assembleModernDebug -x buildNativeRuntime`（D8）、远程分支完整 `testModernDebugUnitTest` 229/0/0/0、`lintModernDebug` 0 errors/80 warnings/1 hint、依赖清单 91 构件并刷新许可文件；合入 `main=5fd1ab4`（安全模式 + XLSX）后完整 JVM 289/0/0/0（41 结果文件）、lint 0 errors/81 warnings/1 hint；review 修复并合入冷启动/PDF/原生界面/温控/DOCX后完整 JVM 535/0/0/0（65 结果文件）、lint 0 errors/95 warnings，androidTest 编译通过。
- 待验证：真实 Android 设备到真实 sshd、真实 Android Keystore、真实远程 DSH token 登录、弱网/断线、UI 手势、Ed25519、Android 仪器测试、真机。
- 状态顺序：按串行合并候选要求，`AGENT-STATE` 将已合并的冷启动作为当前交接，远程主机作为并行记录；`CURRENT.json` 仍绑定本记录以便交接指纹校验。
- 主线同步：最终合入 `main=192a6f4`（PR #28 DOCX 原生预览，含 PR #21 温控、PR #26 原生界面、PR #24 PDF、PR #27 冷启动、PR #22 聊天通知、PR #25 安全模式与 PR #23 XLSX）；同步包含 `.github/workflows/verify.yml`、`scripts/test-chat-session-open.mjs` 与冷启动的 `scripts/test-dsh-path-cache.mjs`，交接指纹已按合并树刷新。`RuntimeManager`/`State.kt`/`TanukiUi.kt` 与安全模式有代码冲突，已保留远程 origin 与安全模式恢复策略：本地端点单独清理、远程连接时本地失败不强制断开网页；文档保留远程、XLSX、安全模式三节。
- 当前目标包名/分支：仅 JVM/APK 构建，未安装设备；分支 `ao/remote-host/remote-ssh` 已从基线 `2f3fac9301c7c3484df85e99091e8f63dddc0a48` 创建，PR [#29](https://github.com/leezzou/tanuki/pull/29) 已创建，base `main`，未触发 Review；head 以 PR 页面为准。无运行中任务。
- 关键事实：远程 origin 固定 `http://127.0.0.1:<隧道本地端口>`；远程 token 只拼到该 loopback URL；凭据只在 `remote-host-credentials` SharedPreferences 的 AES-GCM 密文，密钥来自 AndroidKeyStore；主机指纹保存在 `remote-host-known-hosts`。

### 本次改动

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

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin` | BUILD SUCCESSFUL | 首次构建含缓存准备约 7m34s；仅编译 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.RemoteHostTest'` | BUILD SUCCESSFUL，24 tests / 0 failures / 0 errors / 0 skipped | 新增类；JVM mock SSH |
| `./gradlew :app:assembleModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL，D8 打包 JSch | 未重新构建/验证原生 `.so`；APK 不用于设备验收 |
| `./gradlew :app:testModernDebugUnitTest` | 远程分支 BUILD SUCCESSFUL，37 结果文件，229/0/0/0；合并 `main=5fd1ab4` 后 41 结果文件，289/0/0/0；review 修复后 42 结果文件，293/0/0/0；合入冷启动后 47 结果文件，334/0/0/0；第二轮 P1 修复后 337/0/0/0；合入 PDF 后 396/0/0/0；合入原生界面后 424/0/0/0；合入温控后 455/0/0/0；合入 DOCX 后 535/0/0/0 | 合并后包含安全模式 32 项 + XLSX 28 项 + 远程 24 项 + Cookie 隔离 4 项及既有 205 |
| `./gradlew -PtestBuildType=debug :app:compileModernDebugAndroidTestKotlin` | BUILD SUCCESSFUL | 新增 Cookie 隔离/Keystore 设备回归仅编译，未运行 |
| `./gradlew :app:lintModernDebug` | BUILD SUCCESSFUL，0 errors / 80 warnings / 1 hint | 新增文件无告警；既有告警未处理 |
| `./gradlew -I scripts/dependency-inventory.init.gradle tanukiDependencyInventory` + `python3 scripts/collect-component-licenses.py` | 91 runtime artifacts，5 个仍无 POM 许可声明 | JSch 为 Revised BSD / JZlib Revised BSD / jBCrypt ISC；清单位于 `licenses/` |
| PR [#29](https://github.com/leezzou/tanuki/pull/29) CI（head `9dbaacc`） | Host scripts 与 Android build/JVM/lint 均 success | 公开 Actions；不替代独立 Review 或设备验收 |

- 哪些是前一轮结果：229 中的既有 205 项来自此前 #19/#20 组合；本轮新增 24 项并复跑完整套件。
- 未测试/失败/暂未复现：真实 sshd/设备/Keystore/token、Ed25519、断线重连、UI 操作、仪器、真机；未做设备安装。

### 未完成与暂停项

- 未完成：review 修复后的独立复审、合并与发布授权；真实设备/主机验收；Ed25519；中继/WebRTC；远程 DSH 启停/多主机；自动重连。
- 外部阻塞：无（缺少真实 SSH 主机/设备，属于未验证而非代码阻塞）。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收继续按既有要求等待授权/恢复。
- 不代合、不触发 AO Review、不创建 tag/Release。

### 下一步

1. PR [#29](https://github.com/leezzou/tanuki/pull/29)：base `main`，已推送；head 以 PR 页面为准，不触发 Review。
2. PR 正文已写明本次改动、验证命令、未验证项（真实 SSH/设备/Ed25519）与后续中继/WebRTC。
3. 由 Orchestrator 触发当前完整 SHA 的独立 Codex High Review；设备/真实主机 QA 另行授权。
4. Review 意见逐条修复并重审；用户明确授权后才合并。

### 约束与风险

- 数据/设备/权限边界：未安装/启动 App，未操作设备，未读取真实凭据/日志；所有测试使用合成主机、合成 key/token 和内存存储。
- 关键决策及理由：选 JSch 2.28.8（纯 Java、无传递依赖、约 700KB、Revised BSD）；选 AES-GCM + AndroidKeyStore 而不是明文/普通 SharedPreferences；远程 origin 仍走 loopback，避免放宽 `OriginPolicy`；首次 known_hosts 确认前不发送认证信息；本切片明确 RSA/ECDSA，Ed25519 留后续。
- 第二轮 P1 复审修复：管理页 Cookie 删除固定从主 Looper 发起、IO 等待回调后再 flush；Cookie 写入使用独立串行锁、UI `currentScope()` 只读 volatile，不再持主线程读取所需 monitor；切换前由管理 WebView guard 执行 stopLoading + `about:blank`，切换期间所有请求返回 403、导航被拒绝，guard 失败则取消切换；新增主线程持续读取、guard 失败设备回归；连接远程 B 前先切到本地 Cookie 作用域，成功后才撤销 A 端点，测试连接取消保留 A；新增 `cookieReady` 门禁，WebView 加载/认证重试/HTTP 下载/本地端点发布在 Cookie 作用域不一致时拒绝，本地启动失败快速中止。
- 失败尝试与避免重复踩坑：最初 JVM 测试使用 `com.sun.net.httpserver`，Android 单元测试 bootclasspath 不提供，改为 `ServerSocket` 合成 HTTP；`RemoteHostController` 在配置校验失败时曾会误关活跃远程隧道，已改为不触发回退并加回归；`SharedPreferences` 显式 `commit()` 需要检查返回值，保留同步写入并豁免 UseKtx 风格告警；清除配置失败时保留已保存状态并报错，不谎称已清除；SSH 算法协商失败（Ed25519 等）给出明确中文提示；私钥解析失败与连接失败共用会话清理路径。

### 产物与校验

- PR [#29](https://github.com/leezzou/tanuki/pull/29)，base `main`，head 以 PR 页面为准。
- 未生成 APK/ZIP 交付附件；本轮只有源码与文档，不更新 0.1.8 六附件、不创建 tag/Release。
- 依赖许可：`licenses/JSch-BSD.txt`、`JSch-JZlib.txt`、`JSch-jBCrypt.txt`，以及刷新后的 `ANDROID-DEPENDENCIES.md`/`android-runtime-dependencies.json`/`android-runtime-notices`。
- 提交/推送前清理的过期交接：本轮未删除旧记录；旧记录包含历史验证/未完成事项，暂不判定过期。新记录保留；`CURRENT.json` 由检查脚本刷新。
- 仍需保留的验证/未完成记录：`20261109-1539-dsh-installed-version-manager.md`、`20261109-1539-dsh-install-resilience.md`、`20261109-1535-background-update-check.md`。
- 文档/脚本变化后仍需刷新交接印章、重新生成源码 ZIP；不把源码 ZIP 自身哈希写回本记录。

<a id="20261109-1812-runtime-safe-mode"></a>

## 20261109-1812-runtime-safe-mode

- Agent/会话标识：android_agent-28
- 时间：2026-11-09 18:12 CST

### 当前状态

- 已实现：六类失败分类；normal 下连续两次 `STARTUP_FAILED`/`CRASHED_EARLY` 自动进入安全模式；`rescue` 最小启动参数与独立 `safe-home`/`safe-dsh`；安全模式不删除用户数据；显式“尝试恢复正常模式”进入 `PROBATION`，2 分钟内失败回安全模式；自动重启最多 3 次、2s/4s/8s 退避、killed/noSpace 不自动重启；`state/failure-state.json` 持久化与进程重启恢复；UI 失败类型/安全模式/退出试用入口；前台服务在自动重启等待期间保持。
- 已验证：`/tmp` 独立 clone 中 `compileModernDebugKotlin` 与完整 `testModernDebugUnitTest` 通过；rebase 到 `origin/main=f5debf5` 后完整 JVM 265 tests / 0 failures / 0 errors / 0 skipped（rebase 前 DSH-only 237），新增 `RuntimeFailurePolicyTest` 32/32。PR #25 的历史 push/PR CI 已通过；最新 head 的 CI 以 PR 页面为准。Android CI 不重建原生 `.so`，不能当设备验收。
- 待验证：真机崩溃/低内存、真实磁盘满、PRoot 内 rescue、真实插件配置隔离、UI 视觉/大字体/深色、独立 Review、合并；rebase 后 lint BUILD SUCCESSFUL，本机 assemble/仪器未跑（lint 不能替代设备验收）。
- 当前目标包名：未构建 APK；PR [#25](https://github.com/leezzou/tanuki/pull/25)，分支 `ao/runtime-safe-mode/root`，功能提交 `b4a6ef5`（rebase 后 `dc0494b`），基线 `2f3fac9301c7c3484df85e99091e8f63dddc0a48`；已 rebase 到 `origin/main=f5debf5` 并解决文档冲突。独立 `/tmp/tanuki-safe-mode` clone 验证，未使用共享 worktree 的 `.git` 写操作。
- 关键事实与可定位源码：`app/src/main/kotlin/com/tanuki/runtime/RuntimeFailurePolicy.kt`、`RuntimeManager.kt`、`RuntimeService.kt`、`State.kt`、`ui/SetupScreens.kt`、`ui/TanukiUi.kt`、`ui/Labels.kt`、`MainActivity.kt`；契约见 [INTERFACES](INTERFACES.md) 1.1.1。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeFailurePolicy.kt` | 新增纯 Kotlin 失败分类、连续计数、安全/试用状态机、退避上限、救援启动参数、JSON 状态持久化。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeManager.kt` | 记录每次启动模式/就绪时间；按证据分类失败；安全模式使用独立 home/DSH_HOME 与 rescue；有限自动重启；持久化恢复；提供试用 normal/回安全模式入口；停止时取消重启。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeService.kt` | 新增 `RECOVERY_TRIAL`/`RECOVERY_SAFE` 动作；通知显示安全模式/试用；自动重启等待期间不退出前台服务。 |
| `app/src/main/kotlin/com/tanuki/runtime/State.kt` | Snapshot 增加 recovery；Paths 增加安全模式独立 home/DSH_HOME。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 两个恢复动作的确认与前台服务派发；通知权限入口。 |
| `app/src/main/kotlin/com/tanuki/ui/SetupScreens.kt` | 新增安全模式/试用/最近失败卡片与“尝试恢复正常模式/退出试用”入口。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 运行诊断显示启动模式与最近失败类型。 |
| `app/src/main/kotlin/com/tanuki/ui/Labels.kt` | 六类失败与启动模式中文标签。 |
| `app/src/test/kotlin/com/tanuki/RuntimeFailurePolicyTest.kt` | 新增 32 项纯 JVM 策略回归。 |
| `docs/INTERFACES.md` | 记录正常/安全模式启动参数、失败类型、安全/试用/重启边界。 |
| `docs/RUNTIME-COMPATIBILITY.md` | DSH 启动行补充安全模式 rescue 形式。 |
| `docs/TEST-REPORT.md` | 记录本轮命令、rebase 后 265 JVM、32 项新增与未验证范围。 |
| `docs/AGENT-STATE.md` | 更新当前交接与本轮状态。 |
| 本记录 | 本轮唯一交接、验证和未完成项。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `./gradlew -I /tmp/mirror.init.gradle :app:compileModernDebugKotlin --stacktrace` | `/tmp` clone、JDK17、SDK36；BUILD SUCCESSFUL（7m 9s，19 tasks） | 临时镜像 init script 仅用于本次构建，未提交、未改仓库配置。 |
| `./gradlew -I /tmp/mirror.init.gradle :app:testModernDebugUnitTest --stacktrace` | rebase 前 BUILD SUCCESSFUL（1m）；37 个结果文件合计 237 tests / 0 failures / 0 errors / 0 skipped | 本轮新跑；205 项既有 + 32 项新增。 |
| rebase 到 `origin/main=f5debf5` 后 `:app:compileModernDebugKotlin :app:testModernDebugUnitTest` | BUILD SUCCESSFUL（6m 11s）；40 个结果文件合计 265 tests / 0 failures / 0 errors / 0 skipped | 包含 XLSX PR #23 的 28 项既有新增；`RuntimeFailurePolicyTest` 32/32。 |
| `./gradlew -I /tmp/mirror.init.gradle :app:testModernDebugUnitTest --tests 'com.tanuki.RuntimeFailurePolicyTest'` | BUILD SUCCESSFUL；32/32 | 覆盖分类、连续计数、安全/试用、长间隔 probation 重开、非活动服务、过期手动启动、延迟 HTTP 就绪的晚崩溃、SAFE 恢复提交边界、重启上限、killed/noSpace、超时清理信号、持久化与启动参数。 |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261109-1812-runtime-safe-mode.md` | 结果见提交前最终输出 | 刷新 `CURRENT.json` 印章。 |
| `python3 scripts/check-handoff.py` | 结果见提交前最终输出 | 当前交接一致性。 |
| `python3 scripts/check-publication.py --git` | 结果见提交前最终输出 | 本地链接/身份路径/凭据边界。 |
| `python3 scripts/package-source.py` | PASS；`source_files=485`，源码 ZIP/`SOURCE-SHA256SUMS` 已生成 | 工作材料，不提交。 |
| GitHub Actions run 42602010730（push）/ 42602805776（PR） | 均 success：Host scripts + handoff、Android build/JVM/lint | Android CI 使用 `-x buildNativeRuntime`，其 APK 无原生 `.so`，不能用于设备验收。 |

- 哪些是前一轮结果，哪些是本次新验证：rebase 后 265 JVM 与 compile 为本轮新跑；rebase 前 DSH-only 237；205 项中的既有测试源码来自基线，不能记成前轮设备结果。未运行前轮 PR 的 CI/设备结果，也未复跑 lint/assemble。
- 未测试、超时、失败和暂未复现的情况：真实 DSH 崩溃/低内存/磁盘满、PRoot rescue、插件配置隔离、UI 实机、lint/assemble/仪器均未执行。Maven Central DNS 不稳定，本轮仅用临时镜像完成依赖解析；不是产品网络行为验证。

### 未完成与暂停项

- 未完成：PR #25 的独立 Codex High Review、设备/真机验证、本机 assemble/仪器。
- 外部阻塞：当前环境 Maven Central DNS 不稳定，已用临时镜像绕过，未改仓库；若 CI 网络正常则不受影响。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收继续暂停；未在本轮恢复。

### 下一步

1. PR [#25](https://github.com/leezzou/tanuki/pull/25) 已创建并绑定 `ao/runtime-safe-mode/root`；不合并、不触发 AO Review，等待用户/编排器决定。
2. 由独立 Android QA 在明确无真实任务的设备上验证：安全模式 rescue 启动、连续失败进入安全模式、2 分钟 probation 回退、killed/noSpace 不自动重启、进程重启后状态恢复、UI 入口与大字体/深色。
3. 修复审查意见后重新跑 compile + 完整 JVM；新增设备证据必须绑定当前 head SHA、AVD/serial 与 APK 哈希。

### 约束与风险

- 数据/设备/权限边界：安全模式只读独立 `safe-home`/`safe-dsh`，项目工作区仍为原 `/workspaces`，未执行删除、清数据、卸载、wipe 或快照恢复；未改 API Key 暂停项、AO 配置、安全校验或 TLS/审批。
- 关键决策及理由：使用 DSH 官方 `rescue` profile 作为最小救援启动；`--from-default-profile web` 仅在 rescue 不存在时初始化，避免每次安全启动替换安全模式目录。安全模式不挂载浏览器/phone 工具，减少可疑配置与权限面。自动重启只拉起服务，不重放任务。P1 复审修复：`timedOut` 在 noSpace/launch 之后优先判 `STARTUP_FAILED`，清理产生的 143/137 与 “Killed” 不再覆盖分类；`RuntimeManager` 超时清理时不把退出码作为原始证据。P2 复审修复：`PROBATION` 且上次服务活动时，进程重启一律回 `SAFE`，不再用重开 App 的墙钟时间补足 2 分钟；服务未活动时保留试用并在下次手动启动重新计时，只有存活稳定计时器提交 `NORMAL`。安全模式成功不提交 `RESTORED_PENDING_CHECK`，只有实际加载恢复 userdata 的 normal/probation 启动成功后才写入 verified/SUCCESS。P2 时间基准修复：日志就绪早于 HTTP 就绪时，`CRASHED_LATE` 仍可能落在实际 probation 窗口内；晚崩溃分支先判窗口，窗口内回 SAFE，不再直接转 NORMAL。已 rebase 到 `origin/main=f5debf5`（含 XLSX PR #23），解决 `AGENT-STATE`/`TEST-REPORT`/`CURRENT.json` 文档冲突并复跑全量 JVM。用户停止入口递增 `runtimeGeneration` 并取消待执行自动重启；`interrupted` 服务状态下的 probation 进程重启回到安全模式，避免把崩溃后的试用误判为通过。重启计数达到上限后保持 3，不把未执行的第 4 次展示成已重启；自动重启等待时界面不声称服务已正常运行。
- 失败尝试与避免重复踩坑的结论：`/tmp` clone 首次离线构建缺少 `kotlin-stdlib-jdk8:1.8.0`，Maven Central DNS 超时；改用 `/tmp/mirror.init.gradle` 临时镜像后通过，未把环境问题写成代码失败，也未提交镜像配置。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未构建 APK；`package-source.py` 生成的源码 ZIP 与 `SOURCE-SHA256SUMS` 仅作工作材料，不提交、不作为发布附件。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名发布、未创建 tag/Release、未合并；PR [#25](https://github.com/leezzou/tanuki/pull/25) 仅创建，未触发 Review。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮未删除既有交接；`20261109-1539-dsh-installed-version-manager.md`、`20261109-1535-background-update-check.md`、`20261109-1539-dsh-install-resilience.md` 仍对应待审 PR/未完成验证，其余历史记录仍被 AGENT-STATE 引用，保留以避免丢失验证依据。本轮新增记录为唯一当前交接。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。

<a id="20261109-1819-pdf-native-reading"></a>

## 20261109-1819-pdf-native-reading

- Agent/会话标识：android_agent-35（Tanuki DSH Worker）
- 时间：2026-11-09 18:19 CST

### 当前状态

- 已实现：上述阅读能力与策略层；文件查看入口、官方 DSH UI/模型/插件/Skills 未改，未新增依赖。
- 已验证：本 worktree `compileModernDebugKotlin` 成功；完整 `testModernDebugUnitTest` 365 tests / 0 failures / 0 errors / 0 skipped；其中本轮新增 59 项 PDF 策略测试，既有 `ZoomPolicyTest` 6 项与 `PdfRenderPolicyTest` 4 项不回归。
- 待验证：真实设备上的连续滚动帧率、捏合/双击/横向平移手感、深色主题与字体缩放、真实大文档/加密/损坏 PDF、低内存设备预算表现；lint/assemble/Android 仪器未跑。
- 当前分支：`ao/pdf-reading/root`（会话 worktree）；实际交付分支为 `ao/pdf-reading/native-reading`，已 rebase 到 `origin/main=b9eeb75`。
- 关键事实与可定位源码：`app/src/main/kotlin/com/tanuki/ui/WorkspacePdfPreview.kt`（会话/界面/手势）、`PdfRenderPolicy.kt`（视口渲染尺寸）、`PdfPageCache.kt`、`PdfPageMemory.kt`、`PdfPreloadPolicy.kt`、`PdfMemoryPolicy.kt`、`PdfViewportPolicy.kt`、`PdfJumpPolicy.kt`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/WorkspacePdfPreview.kt` | 改为单 `PdfRenderer` 会话；连续纵向 `LazyColumn`；±1 页窗口加载与取消回收；适应宽度/整页；双指缩放、双击、横向平移；页码/跳页；加密/损坏/超大/内存不足中文错误；页面加载失败可重试。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfRenderPolicy.kt` | 保留既有 `targetSize`（测试兼容）；新增 `viewportSize`，按视口宽度、长边 2048、总像素 4,000,000 与缓存预算计算渲染尺寸。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfPageCache.kt` | 新增按字节记账的 LRU 缓存（纯 Kotlin）。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfPageMemory.kt` | 新增位图生命周期管理：预算淘汰、显示中延迟回收、离开组合/关闭时回收。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfPreloadPolicy.kt` | 新增当前页 + 前后各一页的预加载窗口与保护集合；复审后新增 `renderPlan`：同屏可见页全部优先加载，再在可见范围外补首尾预加载。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfMemoryPolicy.kt` | 新增堆比例缓存预算（24–64 MiB）、单页像素上限与 512 MiB 文件打开上限。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfViewportPolicy.kt` | 新增适应宽度/整页尺寸、横向平移边界、围绕手指缩放的平移/滚动补偿、视口中心当前页判定；复审后修正纵向补偿为 `(centroidY−H/2)×(1/oldScale−1/newScale)`，并新增放大首尾边缘留白 `zoomEdgePadding` 与屏幕坐标推导 `scaledScreenY`。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfJumpPolicy.kt` | 新增页码输入解析（`12`、`12/50`、`第12页` 等）、页码与倍率文案。 |
| `app/src/test/kotlin/com/tanuki/PdfPageCacheTest.kt` 等 7 个新增测试文件 | 新增 42 项纯 JVM 策略测试：缓存淘汰/保护、显示中延迟回收/关闭回收、预加载窗口、内存上限与文件限制、跳页解析、视口/平移/缩放补偿/当前页、视口渲染尺寸。 |
| `docs/handoffs/20261109-1819-pdf-native-reading.md` | 本记录。 |
| `docs/AGENT-STATE.md`、`docs/TEST-REPORT.md` | 记录本轮状态与验证事实。 |

#### 复审修复（PR #24 Codex P2，3 条）

- 缩放首尾可达：`LazyColumn` 仍按 1× 测量，但按 `PdfViewportPolicy.zoomEdgePadding(H, scale)=H×(scale−1)/(2×scale)` 增加上下滚动留白；1× 留白为 0。第一页顶部在滚动 0 处贴视口上沿，最后一页底部在最大滚动处贴视口下沿；捏合/双击补偿同时计入留白变化。补偿先累积，等下一帧新留白完成测量后再执行，避免文档末尾放大时被旧滚动范围截断。
- 纵向补偿公式：由 `(centroidY−H/2)×(1−old/new)` 修正为 `(centroidY−H/2)×(1/oldScale−1/newScale)`，非 1× 起点连续放大/缩小不再累积偏移；新增 2×→4×（62.5）、4×→2×（−62.5）回归。
- 可见页渲染：新增 `PdfPreloadPolicy.renderPlan`，`visiblePages` 通过 `snapshotFlow` 单独跟踪；同屏超过 3 页时全部可见页优先加载，再补首尾各 1 页预加载；已淘汰但仍显示的位图保留到离开组合，避免绘制已回收 Bitmap。
- 固定视口裁剪（第五轮 P2）：在未参与缩放的阅读视口 `Box` 上加 `clipToBounds()`；`graphicsLayer.clip` 只裁剪层内坐标，层放大后仍会绘制到标题栏/控制栏，外层裁剪才能把 PDF 像素限制在阅读区域内。
- 预留失败原子性（第七轮 P2）：`tryReserve` 先计算全部可淘汰页能释放的字节，确认足够后才执行淘汰；失败保持缓存/界面不变，避免部分淘汰后返回 null 让 `pages` 持有已回收位图。新增“可淘汰但总量仍不足→失败不回收→释放显示页后重试成功”的回归。
- 分配前预留驻留空间（第六轮 P2）：`PdfPageMemory.tryReserve` 按「缓存值 + 延迟回收/替换旧值 + 在途预留」的实际字节淘汰未显示页，返回被淘汰键供界面同步移除；`store` 消耗预留并按实际驻留再收紧一次，渲染失败/取消归还预留。新增 LRU 已满后新计划页先腾空间再分配的峰值回归。
- 渲染集合与内存预算联动（第四轮 P2）：单页像素上限按 `renderPlan.size + 1`（含一页在途位图）分配，受保护页总字节不超过 `cacheBudget`；计划变大导致上限收紧时，先 `discard` 超限旧页并等待组合回收，再按新上限渲染。新增 `PdfPageMemory.discard`、驻留页数像素上限、渲染计划 + 预算联合回归。

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew compileModernDebugKotlin testModernDebugUnitTest --tests com.tanuki.PdfRenderPolicyTest --tests com.tanuki.ZoomPolicyTest` | 基线改造前：BUILD SUCCESSFUL（7m26s） | 确认 worktree/JDK17/SDK 可用，既有 PDF/缩放测试通过 |
| `./gradlew compileModernDebugKotlin` | 改造后：BUILD SUCCESSFUL（3m08s，无新增编译警告） | 仅编译 modernDebug，不代表其他 flavor |
| `./gradlew testModernDebugUnitTest --tests 'com.tanuki.Pdf*' --tests 'com.tanuki.ZoomPolicyTest'` | BUILD SUCCESSFUL，52 tests / 0 failures（PDF 相关 46 + Zoom 6） | 纯 JVM，使用 Android 桩类；未触真机 |
| `./gradlew testModernDebugUnitTest` | 提交前最终复跑：BUILD SUCCESSFUL，247 tests / 0 failures / 0 errors / 0 skipped | 完整 JVM 套件；不能替代设备/仪器 |
| `./gradlew compileModernDebugKotlin`（open 回调调用点清理后） | BUILD SUCCESSFUL（1m34s） | 仅调用点整理，未改策略行为 |
| 追加自审修复后 `./gradlew testModernDebugUnitTest --tests 'com.tanuki.Pdf*' --tests 'com.tanuki.ZoomPolicyTest'` | BUILD SUCCESSFUL，52 tests / 0 failures | 修复适应页面水平居中与文件缺失中文原因透传；未改缓存/缩放策略 |
| 复审修复后同目标测试 | BUILD SUCCESSFUL，69 tests / 0 failures | 新增首尾可达、非 1× 补偿、同屏 >3 页渲染集合、计划 + 内存预算联合回归 |
| rebase 到 `origin/main=b9eeb75` 后完整 `./gradlew testModernDebugUnitTest lintModernDebug` | BUILD SUCCESSFUL，365 tests / 0 failures / 0 errors / 0 skipped；lint 通过 | 合并 XLSX/安全模式后复跑；未跑设备/仪器 |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261109-1819-pdf-native-reading.md`、`python3 scripts/check-handoff.py` | 见本轮收尾输出 | 以实际命令输出为准 |
| `python3 scripts/package-source.py` | 见本轮收尾输出 | 只写 `.gitignore` 的 `artifacts/`，不作为发布附件 |

- 哪些是前一轮结果：F1 的 126 JVM、PR #16 合并结果均为历史，本轮未复用其结论。
- 未测试、超时、失败和暂未复现：真机/模拟器大 PDF 连续滚动、加密/损坏 PDF、5x 放大清晰度、手势冲突、深色主题、低内存设备；lint、assemble、Android 仪器未执行。

### 未完成与暂停项

- 未完成：真实设备交互与内存验收；lint/assemble/仪器；独立 Review、合并、发布均未授权/未执行。
- 外部阻塞：无（本轮可离线完成）。
- 用户暂停：API Key 保存问题仍按既定要求暂停，未触碰。

### 下一步

1. 由独立 QA 在测试 AVD/真机安装候选 APK，使用合成大文档（≥500 页）、加密 PDF、损坏 PDF 验证连续滚动、预加载、跳页、1–5x 缩放与内存曲线；记录设备型号、API、堆大小与 logcat。
2. 独立 Codex High Review 以 PR 最新完整 SHA 为准；通过后再讨论合并/发布，Worker 不代合、不触发 Review。
3. 若设备上 5x 文字清晰度不足，可在不突破单页像素/缓存预算的前提下评估按缩放级别重渲染当前页。

### 约束与风险

- 数据/设备/权限边界：未读写真实 PDF、未安装应用、未使用设备；未新增依赖、未改 DSH/协议/AO 配置、未改安全与暂停项；未发布。
- 关键决策及理由：连续翻转用 `LazyColumn` + 单文档会话，避免每页重开 PDF；缩放采用视口内容层相机缩放 + 横向平移，纵向仍由列表滚动，从而放大后仍能滚动全文档；页缓存按字节而非页数，避免不同页尺寸导致内存超限；显示中的位图延迟回收，避免 Compose 绘制已回收 Bitmap。
- 风险/限制：单页渲染受 2048 长边、4,000,000 像素与缓存预算约束，5x 时文字会放大但清晰度有限；缓存预算取堆的 1/8（24–64 MiB），低内存设备可能更频繁重渲染；512 MiB 文件上限是产品限制，非 PdfRenderer 硬限制；手势为自绘，真机手感与无障碍未验证。
- 失败尝试与避免重复踩坑：旧实现每次翻页重新打开 `PdfRenderer`，大文档会反复解析；新实现必须保持所有 `PdfRenderer` 调用在同一 IO 调度器并加互斥锁，避免关闭与渲染竞争。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未构建 APK、未发布；`package-source.py` 只刷新忽略目录中的源码包用于检查。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未覆盖任何设备数据、未签名发布。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮未删除历史记录；F1 PDF 预览记录仍是该功能的来源依据，保留。新增记录通过 `AGENT-STATE` 链接。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。

<a id="20261109-1829-native-ui-switch"></a>

## 20261109-1829-native-ui-switch

- Agent/会话标识：android_agent-33（DSH Worker）
- 时间：2026-11-09 18:29 CST

### 当前状态

- 已实现：
  - `DshUiMode` / `DshUiSwitchPolicy`：默认 `official`；只有用户选 `native`、服务运行且无回退原因时才显示原生；接口失败回退官方但保留偏好。
  - `DshApiProtocol` / `DshNativeClient`：同一启动 token 调 `/connect?token=...&interactions=none`；`POST /api/session/list|projections|page`；校验 `rpcId`；只读文本消息；内存 Cookie，不写日志。P1：认证网络 I/O 不再持 monitor，`reset()` 立即递增代次并清 Cookie，旧请求不能重新发布；P2：仅接受 `surfaceOp=append`，并跳过非 `source.kind=user` 的注入上下文；401 重试也必须属于当前代次，已被 reset 的旧请求不能复活。
  - `DshNativeController` / `DshNativePage`：会话条 + 文本消息 + 空态/错误态；列表失败触发官方回退；历史页失败只影响当前会话。
  - `TanukiUi` / `MainActivity`：设置开关、原生/官方切换、回退提示与重试；保留同一 `DshWebView` 与 DSH 进程；服务停止时丢弃内存 Cookie。
- 已验证（rebase 后新跑）：
  - `:app:testModernDebugUnitTest -x buildNativeRuntime` BUILD SUCCESSFUL，393 tests / 0 failures / 0 errors / 0 skipped；新增 `DshUiSwitchTest` 10 项、`DshNativeApiTest` 12 项、`DshNativeControllerTest` 6 项。
  - `:app:lintModernDebug -x buildNativeRuntime` BUILD SUCCESSFUL（2m4s；仅既有弃用/可读性告警）。
- 待验证：真实 PRoot 内 DSH 的 `/connect` 与 HTTP 一元调用；设备上的布局/暗色/长文本/全屏/返回；WebSocket 实时流与审批/发送；服务重启后 Cookie；低版本 DSH 缺少端点时的回退；assemble/仪器。
- 当前分支/PR：`ao/ui-switch/native-ui`，PR [#26](https://github.com/leezzou/tanuki/pull/26)，rebase 到 `main=5fd1ab4`；rebase 后单一提交待推送，最终 SHA/CI 以 PR 页面为准（独立 worktree，未启动 DSH、未操作设备）。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshUiMode.kt` | 新增偏好枚举与纯策略；默认官方，失败回退不改偏好。 |
| `app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt` | 官方 unary 协议封装、Cookie 获取、会话/切点/历史解析与安全校验；P1 代次化认证状态；P2 append-origin/用户来源过滤。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt` | 新增原生只读页状态机与错误映射；列表失败回退，历史失败局部提示。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 新增原生只读 Compose 页，不伪造聊天/审批。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 新增设置开关、原生/官方切换、回退横幅与重试；原生模式不再要求 WebView 能力。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 接线偏好持久化、控制器、回退与服务生命周期；不重启服务。 |
| `app/src/test/kotlin/com/tanuki/DshUiSwitchTest.kt` | 开关/回退/路由/信封/通知路由回归 9 项。 |
| `app/src/test/kotlin/com/tanuki/DshNativeControllerTest.kt` | 原生通知目标冷启动/已加载/目标缺失/失败回退/当前会话强制重读/决策+分发集成 6 项。 |
| `app/src/test/kotlin/com/tanuki/DshNativeApiTest.kt` | 解析/Cookie/rpcId/端点/token 不泄露、append/replace fixture、延迟认证 reset、401-after-reset 回归 11 项。 |
| `docs/NATIVE-UI.md` | 新增官方 API 发现、端点/缺口、实现、安全与未验证项。 |
| `docs/INTERFACES.md` | 增加 1.6 原生界面官方 DSH 接口摘要。 |
| `docs/TEST-REPORT.md` | 增加本轮 JVM 验证记录。 |
| `docs/AGENT-STATE.md` | 更新当前交接与本轮状态。 |

- rebase 处理：`origin/main` 仅修改 `docs/AGENT-STATE.md`、`docs/TEST-REPORT.md`、`docs/handoffs/CURRENT.json`，代码无冲突；以 main 文档为底稿合并原生界面章节，保留 XLSX 记录，将原 5 个提交压缩为 rebase 后单一提交。最终文档已同时保留 XLSX 与原生界面两条线，rebase 后完整 JVM 为 251 项。

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin` | 通过 | 本 worktree，JDK17；仅既有弃用告警。 |
| `./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime --tests 'com.tanuki.DshUiSwitchTest' --tests 'com.tanuki.DshNativeApiTest'` | 通过 | 18 tests / 0 failures（rebase 后）。 |
| `./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime` | 通过 | rebase 到 `0ba11da` 后 393 tests / 0 failures / 0 errors / 0 skipped（1m50s）。 |
| `./gradlew :app:lintModernDebug -x buildNativeRuntime` | 通过 | rebase 后 BUILD SUCCESSFUL（2m4s）；仅既有弃用/可读性告警，报告为工作材料。 |
| 官方包只读核对 | 完成 | 在官方 npm 包 `@deepseek-ai/dsh 0.2.1-rc.5` 的 README/类型声明/`lib` 中核对 `/connect`、`/api`、`remote.mux`、`session/*`、审批 waterfall；未在设备运行真实 DSH。 |

- 前一轮 205 JVM 不是本轮结果；rebase 到 `0ba11da` 后完整套件为 393（含 main 的 PDF、cold-start、XLSX、runtime-safe-mode、chat-notify 与通知目标/401/并发回归）。
- 未测试：真实 HTTP 往返、WebSocket、设备 UI、进程重启、assemble/仪器。

### 未完成与暂停项

- 未完成：rebase 后单一提交推送与最终 CI；独立 Pi Review（不自行触发，等待 Orchestrator）；设备/真实 DSH 验收。
- 外部阻塞：无（等待 PR 流水线与审查/设备安排）。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；本轮未触碰。

### 下一步

1. PR #26：一次推送 rebase 后的单一提交，随后解析/回复 review thread；最终 CI 以 PR 页面为准。不合并、不自行触发 Review。
2. 最终 CI 失败时按日志修复；若需评审，使用 Orchestrator 指定的 Pi（openai/gpt-6-astra/high），不自行回退 Codex。
3. 设备验收（另行授权）：启动既有 AVD，确认服务运行后开关默认官方；切原生看到官方会话与消息；停止服务再启动不丢会话；断网/404/401 回退官方并可重试；切换前后 DSH 进程未重启、WebView 未重建；logcat 无 token/Cookie。
4. 若后续实现实时流/审批：先补 `INTERFACES`/本专题契约，再实现 `/api/remote.mux` 与 answer Peer；不要复用本切片的 unary 通道伪造审批。

### 约束与风险

- 数据/设备/权限边界：未启动设备、未安装、未修改 DSH 数据/官方 npm 包；原生只读。
- 关键决策：官方没有 REST 会话端点，采用官方 Typert unary `/api`；发送/审批无可靠一元端点，宁可不做；原生最小面只读文本，避免假聊天框；P2 采用官方 `isAppendSurfaceEvent()` 语义，只显示 append-origin。
- 安全：token 只在内存与精确 loopback origin 使用；Cookie 只发 `/api`；不跟随重定向；错误只保留短码；WebView 同源策略未改；`reset()` 只短暂持锁，旧认证请求由代次拒绝。
- 风险：`/connect` 创建的 API Peer 未开 WebSocket 时会保留到 Host 退出（官方已知边界）；本切片每服务周期只换取一次 Cookie，切换界面不重复换取。
- 失败尝试：首个 Cookie 测试把 `name=bad;value` 误当非法；实际按官方 Cookie 语法应取 `name=bad`，已修正测试预期。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未生成 APK/ZIP。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未发布、未合并。
- 提交/推送前清理的过期记录：未删除历史记录；本轮只有新增专题/交接和状态更新，避免覆盖并发 Worker 结论。
- 文档变化后仍需运行 `check-handoff.py`、`check-publication.py --git`、`package-source.py`；源码 ZIP 为工作材料，不提交。

结束前：

```bash
python3 scripts/check-handoff.py --record docs/handoffs/20261109-1829-native-ui-switch.md
python3 scripts/check-handoff.py
python3 scripts/package-source.py
```

<a id="20261109-1836-docx-native-preview"></a>

## 20261109-1836-docx-native-preview

- Agent/会话标识：android_agent-31（DSH Worker）
- 时间：2026-11-09 18:36 CST

### 当前状态

- 已实现：`.docx` 打开后进入原生分页预览；支持常用字符/段落/标题/列表/表格/图片/页面设置；连续滚动、1–4 倍按钮/双指/双击缩放；复制纯文本；支持范围对话框如实列出不支持项。
- 已实现安全边界：归档/条目/总解压/单条目/正文/辅助 XML/图片大小与数量、XML 元素与深度、文字量、块数/单元格数上限；拒绝路径穿越、重复条目、DOCTYPE、未定义实体与不安全关系目标；外部关系不联网；图片解码采样与总像素预算；损坏/加密/超限返回中文错误。
- 已验证：merge `main=f5f3bf3` 后 `compileModernDebugKotlin` BUILD SUCCESSFUL；完整 `testModernDebugUnitTest` BUILD SUCCESSFUL，494 tests / 0 failures / 0 errors / 0 skipped（main 424 - 被替换的 DOCX 纯文本测试 10 + 新增 80），其中新增 DOCX 回归 80 项。
- 待验证：真实设备上的双指缩放/横向滚动/深色主题/超大文档性能、真实 Word 复杂样本、低 API/真机；assemble、Android 仪器未跑，lint 本轮通过。
- 当前基线：`main=f5f3bf3`（已包含温控 PR #21、XLSX 预览 PR #23、DSH 安全模式 PR #25、聊天通知 PR #22、冷启动 PR #27、PDF 阅读 PR #24 与原生界面 PR #26）；分支 `ao/docx-preview/root` 已 merge 该基线（独立 `/tmp` clone 提交/验证），PR [#28](https://github.com/leezzou/tanuki/pull/28)；merge 前功能提交 `36bd87c`（Review 修复 `f2a6a3d`、`6a81792`、`c970e93`、`2517cd9`）；PR 后续仅有交接/状态回填提交，最终 head 以 PR 页面为准；不合并、不触发 AO Review。
- 关键源码：[`DocxDocumentReader.kt`](../app/src/main/kotlin/com/tanuki/storage/docx/DocxDocumentReader.kt)、[`DocxModel.kt`](../app/src/main/kotlin/com/tanuki/storage/docx/DocxModel.kt)、[`DocxStyles.kt`](../app/src/main/kotlin/com/tanuki/storage/docx/DocxStyles.kt)、[`DocxNumbering.kt`](../app/src/main/kotlin/com/tanuki/storage/docx/DocxNumbering.kt)、[`DocxXml.kt`](../app/src/main/kotlin/com/tanuki/storage/docx/DocxXml.kt)、[`DocxLayout.kt`](../app/src/main/kotlin/com/tanuki/ui/DocxLayout.kt)、[`WorkspaceDocxPreview.kt`](../app/src/main/kotlin/com/tanuki/ui/WorkspaceDocxPreview.kt)。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/docx/DocxModel.kt` | 新增只读文档/页面/块/表格/图片/样式/编号模型与解析上限。 |
| `app/src/main/kotlin/com/tanuki/storage/docx/DocxXml.kt` | 新增 XML 元素/深度/文字预算、实际解压限流、安全路径与关系目标归一化。 |
| `app/src/main/kotlin/com/tanuki/storage/docx/DocxStyles.kt` | 新增 `styles.xml` 解析、basedOn 链合并、docDefaults、标题级别与标题字号回退。 |
| `app/src/main/kotlin/com/tanuki/storage/docx/DocxNumbering.kt` | 新增 `numbering.xml` 解析、多级计数器、decimal/letter/roman/bullet 格式化。 |
| `app/src/main/kotlin/com/tanuki/storage/docx/DocxDocumentReader.kt` | 新增 ZIP 安全扫描、正文/关系/媒体解析、分页符与分节属性、中文错误与不支持下提示。 |
| `app/src/main/kotlin/com/tanuki/ui/DocxLayout.kt` | 新增纯 Kotlin 页面几何、贪心分页、图片显示尺寸/采样与像素预算。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceDocxPreview.kt` | 重写为 Compose 原生分页渲染：文本行切片、表格行、图片、页边距、连续滚动、缩放、复制、支持范围对话框。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | DOCX 分支改接 `DocxPreview`。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt` | 用 `readDocx()`（IO 锁内调用新解析器）替换 `extractDocxText()`。 |
| `app/src/main/kotlin/com/tanuki/storage/DocxTextExtractor.kt` | 删除纯文本提取器，避免与新解析器重复且不一致的安全逻辑。 |
| `app/src/test/kotlin/com/tanuki/DocxTextExtractorTest.kt` | 删除被替代的测试。 |
| `app/src/test/kotlin/com/tanuki/DocxFixtures.kt` | 新增合成 DOCX/ZIP/XML fixture 工具。 |
| `app/src/test/kotlin/com/tanuki/DocxDocumentReaderTest.kt` | 新增 25 项样式/列表/表格/图片/页面/分页/文本回归。 |
| `app/src/test/kotlin/com/tanuki/DocxSecurityTest.kt` | 新增 22 项损坏包/路径穿越/条目与解压上限/XML/媒体安全回归。 |
| `app/src/test/kotlin/com/tanuki/DocxPaginatorTest.kt` | 新增 8 项纯分页策略回归。 |
| `app/src/test/kotlin/com/tanuki/DocxLayoutPolicyTest.kt` | 新增 10 项页面几何/字号比例/图片采样与显示回归。 |
| `docs/DOCX-PREVIEW.md` | 新增支持/不支持、近似分页与安全边界说明。 |
| `docs/AGENT-QUICKSTART.md`、`docs/AGENT-STATE.md`、`docs/TEST-REPORT.md` | 更新模块入口、当前状态与本轮验证记录。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin --console=plain` | BUILD SUCCESSFUL（JDK17、SDK36、共享 worktree 缓存） | 编译覆盖新增存储层与 Compose 接线；不代表设备渲染正确。 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.Docx*'` | BUILD SUCCESSFUL；80 tests / 0 failures / 0 errors / 0 skipped（Reader 25、Security 22、Paginator 8、LayoutPolicy 25） | 合成 ZIP/XML；kxml2 仅测试实现。 |
| `./gradlew :app:testModernDebugUnitTest`（rebase 前） | BUILD SUCCESSFUL；260 tests / 0 failures / 0 errors / 0 skipped | 完整 JVM 套件；测试 XML 在 `app/build/test-results/testModernDebugUnitTest/`，不提交。 |
| `git merge origin/main`（`f5f3bf3`）后 `./gradlew :app:compileModernDebugKotlin :app:testModernDebugUnitTest :app:lintModernDebug` | BUILD SUCCESSFUL；494 tests / 0 failures / 0 errors / 0 skipped；lint 通过 | 解决 `WorkspaceFileViewer` 冲突（PDF 保留渲染上限参数、DOCX 使用原生预览、保留 XLSX 分支）并合并温控、原生界面、PDF、冷启动、聊天通知与安全模式后的 `AGENT-STATE`/印章；merge 后重新运行。 |
| 独立 `/tmp` clone（merge 后 `main=f5f3bf3`）`./gradlew :app:compileModernDebugKotlin :app:testModernDebugUnitTest` | BUILD SUCCESSFUL；494 tests / 0 failures / 0 errors / 0 skipped | 干净 clone 复跑并解决冲突，不使用共享 worktree 的 `.git` 写操作；构建缓存来自项目既有环境。 |
| 第五轮 Review P2 修复 | `DocxPageFramePolicy` 框架宽度取 `max(available, page)`，小于 240dp 视口仍容纳最小纸张、可横向滚动；恢复聊天通知历史验证的原始 `main=5fd1ab4` 基线，不改其他任务的历史记录。 | 新增 100dp 视口框架回归；历史测试报告基线已校正。 |
| 第四轮 Review P2 修复 | 单元格分段整体高于剩余高度时，按已测量布局的行边界截取可见前缀并继续渲染，仅将后续行标记截断；新增 `DocxCellPrefixPolicy` 行前缀回归。 | 前缀策略有 JVM 回归；真实设备超长单元格仍待验证。 |
| 第三轮 Review P2 修复 | 宽屏居中改由 `DocxPageFramePolicy` 把留白放在纸张宽度约束之外，页面仍按测量得到的完整宽度绘制；新增大视口（1200dp）与窄视口框架回归，验证右侧正文不会被裁掉。 | 纯布局策略回归；真实平板/折叠屏仍待设备验证。 |
| 第二轮 Review P1 修复 | 单元格段落纳入同一原子预算并按 8,000 字分段；单元格内容累计高度限制在页面内容高度内，表格行高在传给 `Modifier.height` 前 clamp；新增 `DocxCellPlanner`/`DocxTableRowPolicy` 回归（40,000 段单元格、Long/Infinity/NaN 行高）。 | 单元格路径与行高限制均有 JVM 回归；真实超大表格滚动仍未验证。 |
| 首轮 Review P1/P2 修复回归 | Bitmap 释放改为捕获 effect 快照并新增 `DocxBitmapLifetime` 测试；文本切片复用已测量布局（`drawText`）并按页合并、长段落 8,000 字有界分段、行循环内执行预算；列宽用 Long 归一化并限制到正文宽度；页首 gap 清零；测量结果携带截断状态并在界面提示。 | 5 条 inline thread 均有对应代码/测试；真实设备手势与大文档性能仍未验证。 |
| 关键失败与修复 | 1）XML 本地名被整体小写，导致 `pPr/sectPr/gridCol` 等全部失配，样式/表格/页面/图片测试失败；改为保留 OOXML 大小写后 73 项全过。2）分页 gap 测试期望错误；修正为分页起点丢弃 gap。3）`DocxParagraph`/`DocxTable` 误当顶层类型；实际是 `DocxBlock` 嵌套类型，已修正。 | 均为本轮新跑记录，不掩盖失败。 |
| 未执行 | assemble、Android 仪器、真实设备、真实 Word 样本、大文档性能 | lint 与 JVM 已跑；不能写成设备验收或完整 Word 兼容。 |

- 哪些是前一轮结果，哪些是本次新验证：merge 后的 494 JVM、compile、lint 与独立 `/tmp` clone 复跑均为本轮新跑；rebase 前 260 JVM 保留为历史步骤；XLSX 233 项、runtime 安全模式 32 项、聊天通知 18 项、冷启动 23 项、PDF 59 项与原生界面 28 项、温控 31 项属 main 既有记录，不混入 DOCX 新增 80 项。
- 未测试、超时、失败和暂未复现的情况：真实 Word 复杂排版、双指与横向滚动交互、深色主题、图片解码 OOM 边界、超大表格/文档滚动性能未验证；assemble/仪器/真机未跑，lint 本轮通过。

### 未完成与暂停项

- 未完成：PR #28 的独立 Codex High Review 与真实设备 QA（由 Orchestrator/用户按项目规则推进）。
- 外部阻塞：无。
- 用户暂停（沿用）：API Key 保存、真实模型/账号验收、真机验收；本轮未触碰。

### 下一步

1. PR [#28](https://github.com/leezzou/tanuki/pull/28) 已 merge `main=f5f3bf3` 并解决与温控/XLSX/安全模式/聊天通知/冷启动/PDF/原生界面文档的冲突；目标 `main`，正文含摘要、支持/不支持清单、测试与未验证项，不合并、不触发 AO Review。
2. 由 Orchestrator/用户安排当前 head 的独立 Codex High Review 与设备 QA；设备至少覆盖图片/表格/列表/真实复杂 DOCX、双指缩放与横向滚动、超大文档。
3. 若 Review 要求修改，先在本分支修复、重跑 compile/JVM 并更新交接，再推送；不得以 JVM 结果替代设备验收。

### 约束与风险

- 数据/设备/权限边界：未启动模拟器/真机、未安装、未读取真实文件或凭据；只使用合成 ZIP/XML 与 JVM。未修改官方 DSH UI、协议、AO 配置或既有安全/暂停项。
- 关键决策及理由：解析与渲染分层，纯 Kotlin 策略可 JVM 测试；所有解压按实际字节计数，不信任 ZIP 声明；图片只读取正文引用且限制位置/总量；为保证手机可读性统一放大正文 1.9x，因此分页为近似而非 Word 精确排版；`.doc` 继续走外部打开。
- 失败尝试与避免重复踩坑的结论：XML 标签名不能统一小写；Compose 文本分页切片必须允许超出切片高度排版后再裁剪，否则后续行可能被测量阶段截断；表格单元格渲染宽度必须与测量宽度（列宽减左右 padding）一致，否则会重新换行。
- 已知风险：真实 Word 复杂样式/域/浮动图片、超大文档性能、不同字体度量与 Compose 行高差异可能造成分页偏差；图片字节在内存中保留到预览离开，受 48 MiB 上限约束；照片解码仍可能受设备内存影响。

### 产物与校验

- 新/旧 APK、源码 ZIP：本轮不生成 APK、不发布；源码 ZIP 仅在提交前按 `scripts/package-source.py` 刷新并核对 `artifacts/SOURCE-SHA256SUMS`，属于工作材料。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布；PR [#28](https://github.com/leezzou/tanuki/pull/28) 只交付可审查候选，不合并。
- 提交/推送前清理的过期记录：未删除历史交接；DSH 已安装版本管理、DSH 安装提速、后台更新检查等记录仍与当前 `AGENT-STATE` 和未完成 PR 相关，保留。本记录为当前唯一交接。
- 仍需保留的验证/未完成记录：本记录、`docs/handoffs/CURRENT.json` 印章与 `docs/DOCX-PREVIEW.md`；真实设备与独立 Review 缺口待 QA/Orchestrator。

<a id="20261109-2310-release-0.1.10"></a>

## 20261109-2310-release-0.1.10

- Agent/会话标识：android_agent-32（Tanuki DSH Worker）
- 时间：2026-11-09 23:10 CST（最终发布更新）

### 当前状态

- 已实现：发布分支已 rebase 到 `origin/main=9e7edbb9`；`app/build.gradle.kts` 默认 `versionName=0.1.10`、`versionCode=12`；发布文档已按最终 main 与 9 个 PR 更新。
- 已验证：最终 main 上 `compileModernDebugKotlin`、完整 `testModernDebugUnitTest`、`lintModernDebug` BUILD SUCCESSFUL，**535 tests / 0 failures / 0 errors / 0 skipped**；正式候选 APK 重新生成后已实核包名/版本/code/minSdk/证书/大小/SHA-256。
- 待验证：设备/模拟器安装与升级、真机 QA；不阻塞本次 Pre-release 上传，但不得宣称设备已验收。
- 当前分支：`ao/xlsx-preview/release-0.1.10`，在 `/tmp/tanuki-release-0.1.10` 独立 clone 操作；最终提交 author/committer 为 `leezzou <115924933+leezzou@users.noreply.github.com>`。
- 关键事实与源码：版本默认值 `app/build.gradle.kts`；最终 APK/六附件 `artifacts/releases/0.1.10/`；发布流程 `docs/RELEASE.md`、`docs/ARTIFACTS.md`、`docs/APP-UPDATES.md`、`docs/PUBLICATION.md`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/build.gradle.kts` | 默认 `versionName=0.1.10`、`versionCode=12`。 |
| `docs/AGENT-STATE.md` | 最终 main、9 个 PR、0.1.10 候选与测试基线。 |
| `docs/TEST-REPORT.md` | 最终 main 上的 535 JVM 结果、0.1.9 已发布事实与候选核对。 |
| `docs/RELEASE.md` | 0.1.9 为上一发布，示例/产物改为 0.1.10/code12。 |
| `docs/ARTIFACTS.md` | 0.1.9 已发布、0.1.10 正式附件目录与校验。 |
| `docs/APP-UPDATES.md` | 更新清单/命令到 0.1.10/code12。 |
| `docs/PUBLICATION.md` | 发布检查与版本基线更新。 |
| `docs/AGENT-QUICKSTART.md` | 最终 main、发布分支与测试基线。 |
| `docs/RUNTIME-COMPATIBILITY.md` | 0.1.9/0.1.10 版本状态。 |
| `docs/handoffs/20261109-2310-release-0.1.10.md` | 本次发布交接。 |
| `docs/handoffs/CURRENT.json` | 交接印章。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `./gradlew --no-daemon -x buildNativeRuntime :app:compileModernDebugKotlin :app:testModernDebugUnitTest :app:lintModernDebug` | BUILD SUCCESSFUL（3m07s），535 tests / 0 failures / 0 errors / 0 skipped | 最终 `main=9e7edbb9`，JDK 17.0.21、SDK 36；`-x buildNativeRuntime` 只用于 JVM/lint，正式 release 另行重建原生。 |
| `python3 scripts/prepare-app-release.py --version 0.1.10 --version-code 12 --channel workspacecheck` | BUILD SUCCESSFUL（2m05s）；正式 APK/清单/源码/原生对应源码写入 `artifacts/releases/0.1.10/` | 旧 `d9b4aa21` 候选作废；APK 实核：`com.tanuki.modern.workspacecheck`、0.1.10/code12、minSdk26、证书 `371dc3...`、11,485,978 字节、SHA-256 `66cacc393fe5e7633df2079dd278ef21dd2f7731eb6f977f30a97f2638e1563d`。 |
| 正式 APK 实核 | `com.tanuki.modern.workspacecheck`、0.1.10/code12、minSdk26、证书 `371dc301...`、11,485,978 字节、SHA-256 `66cacc393fe5e7633df2079dd278ef21dd2f7731eb6f977f30a97f2638e1563d` | 由 apkanalyzer/apksigner 与 `app-update.json` 双重核对。 |
| `check-handoff.py`、`check-publication.py --git`、`package-source.py` | 通过 | 文档、公开边界、源码包与印章。 |

- 9 个 PR：#21 设备温控、#22 DSH 通知、#23 XLSX、#24 PDF、#25 失败分类、#26 原生界面切换、#27 冷启动、#28 DOCX、#29 远程主机。
- 0.1.9 发布事实（公开 Release 只读核对）：`v0.1.9` tag `5c680b9`、code11、APK SHA-256 `210b6ec2119e8dd6ee08ec3d2be07889f1fa36720c6e15dd24d74a1d25a49dc8`、10,506,550 字节、证书 `371dc301...`。
- 未测试、超时、失败：设备/仪器未执行；最终 build 与附件打包待记录。JVM/lint 不能替代设备验收。

### 未完成与暂停项

- 未完成：push main、创建 tag `v0.1.10`、创建 Pre-release 并上传六附件；设备/真机 QA。
- 外部阻塞：无。
- 用户暂停：真实模型/账号与真机验收继续暂停；本任务不操作设备。

### 下一步

1. 提交最终改动并 push `main`，确认远端 main SHA 与提交一致。
2. 在 main 上创建并推送 `v0.1.10` tag；创建 Pre-release，上传 APK、`app-update.json`、源码 ZIP、原生对应源码 TAR、`native-source-SHA256SUMS`、`RELEASE-SHA256SUMS`。
3. 公开 Release 创建后核对附件大小/哈希与 `app-update.json`；不覆盖 0.1.9。
4. 设备 QA 另行授权，覆盖安装/升级/启动与 0.1.10 主要功能。

### 约束与风险

- 数据/设备/权限边界：只用公开源码与合成/本机构建产物；未读取凭据、用户数据或设备；不覆盖 0.1.9 及更早附件。
- 关键决策：最终 main 9 个 PR 全部计入；正式 APK 必须从最终源码重新构建，旧 `d9b4aa21` 候选作废；沿用 `~/.android/debug.keystore` 的 workspacecheck 开发证书（证书 `371dc3...`），保证同包覆盖升级。
- 风险：尚无设备验收；最终 main 若在发布期间再次前进，需重新核对并重跑/重打包；Pre-release 由开发证书签名，不能宣传为正式签名。
- 交接清理：既有记录均关联未合并 PR（发布时已全部合并）或未完成设备/QA；本轮已在最终 main 上保留历史记录并在本记录汇总 9 个 PR，未删除可能仍被引用的验证依据。

### 产物与校验

- 正式 APK（本地，待上传）：`artifacts/releases/0.1.10/Tanuki-0.1.10-workspacecheck-arm64-release.apk`，SHA-256 `66cacc393fe5e7633df2079dd278ef21dd2f7731eb6f977f30a97f2638e1563d`、`11,485,978` 字节、证书 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。
- 六附件：APK、`app-update.json`、`Tanuki-0.1.10-source.zip`、`Tanuki-native-corresponding-sources.tar.gz`、`native-source-SHA256SUMS`、`RELEASE-SHA256SUMS`，生成后逐项校验。
- 未覆盖 0.1.9 及更早附件；本地候选在 `/tmp/tanuki-release-0.1.10/artifacts/releases/0.1.10/`。
- 文档/脚本变化后需重新运行 `check-handoff.py --record`、`check-publication.py --git`、`package-source.py`，并核对正式 APK SHA。

<a id="20261110-0330-copy-flicker"></a>

## 20261110-0330-copy-flicker

- Agent/会话标识：android_agent-40（worker）
- 时间：2026-11-10 UTC

### 当前状态

- PR：https://github.com/leezzou/tanuki/pull/35（base `main`，head `ao/copy-flicker/root`，未合并、未发布、未触发 AO Review）。
- 已合并 `main=533eff4`（PR #34 文件查看器格式）与 `main=1cb7ad9`（PR #32 原生对等审计）解决 `AGENT-STATE`/`TEST-REPORT`/`CURRENT.json` 冲突；main 自带 558 项 JVM，本分支合入前 554 项；合并后的完整套件由 CI 在新 head 复跑，本记录不把两者相加当作本地已跑。
- 已实现：`FileTransfers` 集中限流/去重/单调/最终帧/协作取消，并支持非协程任务登记取消动作；Blob/URL/浏览器导出取消或失败时清理本次 SAF 目标；导入真实总量（`DISPLAY_NAME`+`SIZE`，仅全部已知时确定）；进度流只由 `OperationBanner` 订阅；文件列表同目录刷新保留旧条目、换目录清空、内容相等复用条目列表；Lazy 列表状态按目录保存并支持旋转恢复；列表进度槽固定 4dp。
- 已验证（本轮新跑，合入 main 前）：`compileModernDebugKotlin` BUILD SUCCESSFUL；完整 JVM 554 tests / 0 failures / 0 errors / 0 skipped；`lintModernDebug` BUILD SUCCESSFUL；`compileModernDebugAndroidTestKotlin` BUILD SUCCESSFUL；上述为合并 main 前的本地结果，合并后的完整套件由 CI 在新 head 复跑。
- 待验证：真实 SAF/系统文档选择器导入、大文件/多文件/取消 UI、旋转恢复、真机；新增仪器测试只编译未运行。
- 当前目标包名：`com.tanuki.modern.workspacecheck`（源码/回归用），分支 `ao/copy-flicker/root`，基线 `origin/main=0555c3c0`；本会话未启动/安装/操作任何设备，无运行中的产品任务。
- 关键事实与可定位源码：`app/src/main/kotlin/com/tanuki/runtime/FileTransfers.kt`、`storage/ImportProgress.kt`、`MainActivity.kt`（导入回调/`importProjectFiles`）、`ui/OperationProgress.kt`、`ui/TanukiUi.kt`、`ui/FileListPresentation.kt`、`ui/WorkspaceFilesPage.kt`；测试 `app/src/test/kotlin/com/tanuki/{FileTransferProgressTest,ImportProgressTest,FileListPresentationTest}.kt`，仪器 `app/src/androidTest/kotlin/com/tanuki/FilesBrowserPresentationTest.kt`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/runtime/FileTransfers.kt` | 进度发布集中在 `update/complete`：160ms 合并、总量变化与最终帧立即发布、同总量丢弃回退分块；`track` 用子作用域登记 Job，`cancel(id)` 协作取消，原调用方 `finally` 清理暂存；新增 `onCancel(id, action)` 供等待网页回包等非协程任务登记取消动作。 |
| `app/src/main/kotlin/com/tanuki/web/BlobDownloads.kt` | 等待网页 Blob 回包时登记取消动作：取消清空 pending、移除进度并忽略晚到回包；解码阶段取消协程并立即 finish，不再打开系统导出选择器。 |
| `app/src/main/kotlin/com/tanuki/storage/ExportCleanup.kt` | 新增 SAF 目标清理策略：删除异常/false 视为未清理；取消与失败提示不假装目标已删除。 |
| `app/src/main/kotlin/com/tanuki/browser/BrowserLibrary.kt` | 浏览器文件导出取消/失败时删除本次 CreateDocument 目标；取消与清理失败通过 notice 明确提示。 |
| `app/src/main/kotlin/com/tanuki/storage/ImportProgress.kt` | 新增纯策略：只有全部大小有效且不溢出时才给真实总量，否则 -1；百分比只由正总量产生。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 根组合不再 `collect` 传输流（避免每个分块重组整页）；导入先查询名称/大小再复制，`onProgress` 带真实总量；新增 `cancelTransfer` 回调；取消选择器空结果直接返回；Blob/URL 下载取消或失败后删除未完成 SAF 目标并提示。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | `TanukiUiModel.transfers` 改为 `StateFlow`（稳定实例），新增空流默认值与 `cancelTransfer` 回调。 |
| `app/src/main/kotlin/com/tanuki/ui/OperationProgress.kt` | 进度条内部 `collectAsState`，只重组进度区；百分比统一使用 `ImportProgress.fraction`；进度项携带 transferId；详情对话框为文件传输提供“取消此文件任务”。 |
| `app/src/main/kotlin/com/tanuki/ui/FileListPresentation.kt` | 新增纯显示策略：同目录刷新保留旧条目、换目录清空、内容相等复用条目列表；筛选/排序抽成可回归的 `prepare()`。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt` | 使用显示策略避免排序期间闪空/滚动归零；`LazyListState`/`LazyGridState` 按目录 `rememberSaveable`；固定 4dp 进度槽，加载/排序不再推动列表。 |
| `app/src/test/kotlin/com/tanuki/FileTransferProgressTest.kt` | 新增 5 项：分块合并、总量/最终帧、回退丢弃、取消清理、未知 id no-op，以及登记式取消动作只执行一次/结束后不可再登记。 |
| `app/src/test/kotlin/com/tanuki/ImportProgressTest.kt` | 新增 3 项：未知/负数/溢出总量、百分比边界。 |
| `app/src/test/kotlin/com/tanuki/FileListPresentationTest.kt` | 新增 8 项：同目录保留、换目录清空、身份复用、筛选/排序、来源区分。 |
| `app/src/test/kotlin/com/tanuki/ExportCleanupTest.kt` | 新增 3 项：删除成功/失败/抛异常、提示文案，以及写入部分数据后取消会删除截断目标。 |
| `app/src/androidTest/kotlin/com/tanuki/FilesBrowserPresentationTest.kt` | 新增仪器用例：同目录新增文件后刷新保持滚动位置（只编译，未执行）。 |
| `app/src/androidTest/kotlin/com/tanuki/BlobDownloadTest.kt` | 新增仪器用例：取消等待中的 Blob 后进度立即移除、晚到回包不触发 ready/系统选择器且不报错（只编译，未执行）。 |
| `app/src/androidTest/kotlin/com/tanuki/SetupPresentationTest.kt` | 模型进度字段改为 `MutableStateFlow` 以匹配新契约。 |
| `docs/WORKSPACE-STORAGE.md` | 说明导入后台执行、真实/不定进度、限流、列表稳定与取消语义。 |
| `docs/TEST-REPORT.md` | 记录本次根因、修复和新增验证/未测范围。 |
| `docs/AGENT-STATE.md` | 增加本任务状态与交接链接。 |
| `docs/handoffs/20261110-0330-copy-flicker.md` | 本记录。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:compileModernDebugKotlin` | 通过（BUILD SUCCESSFUL） | 本 worktree、JDK17/SDK36；只证明 Kotlin 编译。 |
| `... :app:testModernDebugUnitTest --tests 'com.tanuki.FileTransferProgressTest' --tests 'com.tanuki.ImportProgressTest' --tests 'com.tanuki.FileListPresentationTest' --tests 'com.tanuki.OperationProgressTest'` | 通过 | 目标回归先行验证；随后跑完整套件。 |
| `... :app:testModernDebugUnitTest :app:lintModernDebug :app:compileModernDebugAndroidTestKotlin` | 通过（BUILD SUCCESSFUL，2m57s）；69 个结果文件，554 tests / 0 failures / 0 errors / 0 skipped | 本 worktree；main 既有 535 + 新增 19（两条 Review P2 共 4 项）。 |
| Review P2 修复后：`compileModernDebugKotlin`、`compileModernDebugAndroidTestKotlin`、`FileTransferProgressTest`、`ExportCleanupTest` | 通过（BUILD SUCCESSFUL） | 取消动作、Blob 取消、部分写入后取消清理的编译与目标测试；完整 554 项与 lint 见上一行。 |
| `... :app:compileModernDebugAndroidTestKotlin` | 通过（BUILD SUCCESSFUL） | 新增仪器测试只编译，未在设备执行。 |
| `python3 scripts/check-handoff.py --record ...` / `check-handoff.py` / `check-publication.py --git` / `package-source.py` | 见「产物与校验」 | 提交前执行。 |

- 哪些是前一轮结果，哪些是本次新验证：上述编译、554 项 JVM、lint、androidTest 编译均为本轮新跑；前一轮 535 项只作为对照，不冒充本轮结果。
- 未测试、超时、失败和暂未复现的情况（不能抹掉）：未在设备/模拟器执行真实 SAF 导入、大文件/多文件/取消 UI、旋转恢复、真实系统文档选择器；未跑 `assembleModernDebug`/仪器；没有设备截图或帧率数据，根因由源码路径与宿主回归证明，不声称已做设备复现。

### 未完成与暂停项

- 未完成：第二条 Review 线程 resolve、合并 main 后的新 head CI 与独立审查；设备侧验收未做。
- 外部阻塞：无（GitHub 推送/PR 在授权范围内）。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；设备由独立 QA 独占。

### 下一步

1. PR [#35](https://github.com/leezzou/tanuki/pull/35) 已创建（base `main`，head `ao/copy-flicker/root`，head 以 PR 页面为准）；两条 Review P2 已修复，合并 `main=533eff4` 解冲突并推送后解析线程，等待新 head CI 与独立 Codex High Review；不合并、不发布、不触发 AO Review（等用户/编排器同意）。
2. 独立 Codex High Review 与独立 QA 在真实 SAF/多文件/大文件/取消/旋转场景复验；本会话不操作设备。

### 约束与风险

- 数据/设备/权限边界：只用合成文件与宿主测试；不启动模拟器/真机，不读取真实用户数据，不安装/覆盖 APK，不发布。
- 关键决策及理由：限流放在 `FileTransfers` 而不是每个调用方，避免遗漏（导入、导出、浏览器下载、手机文件工具共用）；进度流改为由进度条订阅，保留 `StateFlow` 的线程安全与去重；列表显示策略抽成纯函数，便于 JVM 回归并保留“换目录不串项”的既有仪器断言。
- 失败尝试与避免重复踩坑的结论：`FileTransfers` 构造参数把 `clock` 放在最后以兼容既有 `FileTransfers { ... }` 尾随 lambda；`rememberSaveable` 的 saver 必须显式使用 `LazyListState.Saver`/`LazyGridState.Saver`，否则旋转会丢滚动位置；不要用 `shown = emptyList()` 表示“正在排序”，否则 Lazy 列表会归零。

### 产物与校验

- 新/旧APK、源码ZIP及校验文件：本轮不生成 APK、不更新 `artifacts/`；源码 ZIP 仅在提交前按 `scripts/package-source.py` 重新生成并核对 `artifacts/SOURCE-SHA256SUMS`（工作材料，不提交）。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布；PR [#35](https://github.com/leezzou/tanuki/pull/35) 为可合并候选。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮未删除任何历史交接；其余记录仍含未完成 QA/Review 事项且被 `AGENT-STATE` 引用，删除会丢失证据。清理结论记录于此，保留 `TEMPLATE.md`、`CURRENT.json` 和本记录。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码ZIP；不要把源码ZIP自身哈希写回归档内形成循环。

<a id="20261110-0418-saf-direct-open"></a>

## 20261110-0418-saf-direct-open

- Agent/会话标识：android_agent-39（DSH Worker）
- 时间：2026-11-10 04:18 UTC

### 当前状态

- 已实现：外部 content URI 直开与回退逻辑、查看器文档源抽象、文本分页流式读取、PDF/图片/DOCX/XLSX/音视频直开接线、Activity 进程重建描述恢复、系统 ACTION_VIEW/ACTION_SEND 过滤器与 `singleTop`。
- 已验证：`compileModernDebugKotlin`；rebase 到 `origin/main=7593805` 后完整 `testModernDebugUnitTest` 619 tests / 0 failures / 0 errors / 0 skipped；`lintModernDebug` 0 errors / 101 warnings / 1 hint；新增/扩展回归见下。
- 待验证：真实设备/模拟器 SAF 提供方、系统「打开方式/分享」、真实 `takePersistableUriPermission` 与撤销、旋转/进程被杀、权限失效后重新选择、真实大文件/低内存/弱网、DOCX/XLSX 私有缓存清理、音视频直开。
- 当前分支：`ao/saf-file-open/root`；原基线 `0555c3c0eaeeed181269b5e68733ccfdd48d9e75`；已 rebase 到 `origin/main=7593805`（PR #42 交接 PR 模式，含 #34/#32/#35/#36）；独立 worktree；PR [#37](https://github.com/leezzou/tanuki/pull/37) 待审。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/ExternalDocumentPolicy.kt` | 新增纯 Kotlin 外部 URI 校验、显示名清洗、类型大小上限、失败分类、描述往返、读取代次 `openToken`/`contentKey`、已消费 Intent 身份与 `BoundedCopy` 有界流复制。 |
| `app/src/main/kotlin/com/tanuki/storage/ExternalDocumentAccess.kt` | 新增 Android 元数据查询、`takePersistableUriPermission` 申请与异常中文映射。 |
| `app/src/main/kotlin/com/tanuki/storage/ContentDocument.kt` | 新增 `content://` 文档源：文本随机读/顺序流、图片降采样、PDF 描述符、DOCX/XLSX 私有缓存暂存、导出与直接转发 URI；元数据读取/外部打开不再被内置预览大小上限阻断。 |
| `app/src/main/kotlin/com/tanuki/storage/DocumentAccess.kt` | 新增 `OpenDocument` 接口与工作区适配器，查看器不再强依赖 `WorkspaceFileManager + path`。 |
| `app/src/main/kotlin/com/tanuki/storage/TextFileReader.kt` | 支持 `SeekableInput`；文件/描述符随机读与纯顺序流共用分页逻辑；表头字节作为首页前缀，避免顺序流回退；未知大小支持翻页。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt` | 文本 revision 判断兼容未知大小；分类委托合并后的 `FileTypeCatalog`。 |
| `app/src/main/kotlin/com/tanuki/storage/FileTypeCatalog.kt` | 在 main 的扩展名/MIME 分类上补充 PDF/DOCX/DOC/XLSX 的纯 MIME 识别，供无扩展名的 content URI 直开。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 接入外部 URI/`ACTION_VIEW`/`ACTION_SEND`/SAF 选择器、持久权限、Activity 保存恢复、文件页跳转、外部导出与显式复制回退；记录已消费启动 Intent，重建时不重放旧打开请求。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | 改为接收 `OpenDocument`；显示非持久授权提示、URI 失效/重新选择/复制回退；外部文件底部保留复制入口。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt` | 新增「打开手机/其他应用文件」菜单、外部文档查看分支与工作区文档适配。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspacePdfPreview.kt`、`WorkspaceDocxPreview.kt`、`WorkspaceXlsxPreview.kt` | 改为接收 `OpenDocument`，直开外部描述符/流，不要求工作区路径。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 新增 `filesOpenRequest`，外部文件到达时切换到文件页。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfMemoryPolicy.kt` | PDF 大小上限复用外部策略常量，避免两条入口不一致。 |
| `app/src/main/AndroidManifest.xml` | 注册 `content://` 的 VIEW/SEND 过滤器（PDF/图片/文本/DOCX/XLSX/DOC/XLS），`singleTop`。 |
| `app/src/test/kotlin/com/tanuki/ExternalDocumentPolicyTest.kt` | 新增 URI 校验、显示名、直接打开/复制回退、大小上限、持久/非持久授权、URI 失效、描述往返、`BoundedCopy` 回归。 |
| `app/src/test/kotlin/com/tanuki/TextFileReaderTest.kt` | 新增可随机读页窗口、纯顺序流翻页、未知大小回归。 |
| `app/src/test/kotlin/com/tanuki/WorkspaceFileKindTest.kt` | 新增无扩展名 MIME 分类回归。 |
| `app/src/main/kotlin/com/tanuki/ui/TextPageLayout.kt` | 新增未知总大小时按 `hasMore` 翻页的重载，保留 4 MiB 显示上限。 |
| `docs/handoffs/20261110-0418-saf-direct-open.md` | 本记录；按 [交接机制](HANDOFFS.md) 普通 PR 只新增自己的记录，不修改 `AGENT-STATE.md`/`CURRENT.json`，共享状态由集成方统一刷新。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin` | BUILD SUCCESSFUL | 宿主 macOS，JDK17，SDK36，跳过 `buildNativeRuntime`；只有既有弃用/平台告警。 |
| `./gradlew ... :app:testModernDebugUnitTest :app:lintModernDebug` | BUILD SUCCESSFUL；74 个结果文件，619 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 101 warnings / 1 hint | 完整 JVM 与 lint；不是设备/仪器验证。 |
| `./gradlew ... :app:compileModernDebugAndroidTestKotlin -x buildNativeRuntime` | BUILD SUCCESSFUL | 修正 `WorkspaceVideoPresentationTest` 对旧查看器签名的调用；仅编译，未运行仪器测试。 |
| `./gradlew ... :app:testModernDebugUnitTest --tests 'com.tanuki.TextFileReaderTest' --tests 'com.tanuki.ExternalDocumentPolicyTest' --tests 'com.tanuki.BoundedCopyTest' --tests 'com.tanuki.WorkspaceFileKindTest'` | BUILD SUCCESSFUL | 新增/扩展目标回归全部通过。 |
| rebase 到 `origin/main=7593805` 后 `:app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin` | BUILD SUCCESSFUL | 解决 `WorkspaceFileManager`/`WorkspaceFileViewer` 与 main 的 `FileTypeCatalog`、行号/截断 UI 冲突；Android 测试源码同时编译。 |
| 代码审查（本地 diff） | 进行中 | 已检查主流程、权限/回退、流读取、缓存清理和取消传播；未触发 AO Review。 |
| GitHub Actions `verify`（PR #37，首个 head `21677ab`） | Android job 失败 | `WorkspaceVideoPresentationTest` 仍按旧 `WorkspaceFileViewer(files, entry, ...)` 调用；已改为 `WorkspaceDocument` 并本地编译通过。 |
| GitHub Actions `verify`（PR #37，head `7e6ff13`） | 全部 pass | Host scripts and handoff checks 与 Android build/JVM/lint 均 success；这是合并 main 前的结果。 |
| GitHub Actions `verify`（PR #37，合并 main 后 head `01ee0cd`） | 全部 pass | Host scripts and handoff checks 与 Android build/JVM/lint 均 success；PR `mergeStateStatus=CLEAN`、`mergeable=MERGEABLE`。 |

- 哪些是前一轮结果，哪些是本次新验证：上述编译、618 项 JVM、Android 测试源码编译与 lint 均为本轮新跑；0.1.10 的 535 项与设备 QA 是历史记录，不是本轮结果。
- 未测试、超时、失败和暂未复现：真实 Android 设备/模拟器、系统 SAF/DocumentsUI、`takePersistableUriPermission` 真实结果与撤销、旋转/进程死亡恢复、真实大文件与低内存/弱网、音视频直开、DOCX/XLSX 私有缓存清理、assemble/仪器测试均未执行；没有把源码存在当成通过。

### Review 修复（3 项 P2，2026-11-10）

- **重新选择同一 URI 不刷新**：`ExternalDocumentDescriptor.openToken` 每次成功打开/重新选择递增，`ContentDocument.key` 使用 `contentKey(uri, token)`；查看器和 PDF/DOCX/XLSX/文本子预览都以该 key 重置 `remember`/`LaunchedEffect`，同一 URI 重新授权后也会重新读取。
- **超限文件无法转交外部打开**：`ContentDocument.describe()` 只读取元数据，不再因内置预览上限抛错；PDF/图片/DOCX/XLSX 的大小限制只保留在各自解码/解析路径。`prepareOpen()` 与 `export()` 只看 URI 是否合法，因此 65 MiB DOCX 或 >512 MiB PDF 仍能「使用其他应用打开」或导出。
- **重建重放已消费 Intent**：`ExternalDocumentPolicy.requestId`/`shouldConsume` 记录 ACTION_VIEW/SEND 的已消费身份；`MainActivity` 在 `onCreate` 重建时跳过已消费的原始 Intent，`onNewIntent` 始终消费新请求，关闭后重建也不会重开旧文件。已消费身份写入 saved state 与私有 SharedPreferences，任务真正结束时清除。
- 回归：`ExternalDocumentPolicyTest` 新增同一 URI 新 token、已消费 Intent 不重放、超限仍可转交原 URI 3 项；完整 JVM 619 tests / 0 failures，lint 0 errors / 101 warnings / 1 hint。
- 按冲突处理要求 squash-rebase 到最新 `main=7593805`（PR #42 之后的 PR 模式），保留三项 P2 修复；本 PR 不修改 `AGENT-STATE.md`/`CURRENT.json`，共享状态由集成阶段统一刷新。

### 第二轮 Review 修复（3 项 P2，2026-11-10）

- **remember 键遗漏 openToken**：`MainActivity` 创建 `ContentDocument` 的 `remember` 已加入 `current.openToken`；`ContentDocument.key` 改由 `ExternalDocumentPolicy.viewerKey(descriptor)` 派生，同一 URI/元数据但新 token 会得到不同 key，查看器及子预览重新读取。
- **恢复后 token 计数器归零**：新增 `restoredTokenSequence(current, restoredToken)`，Activity 恢复时把 `externalOpenSequence` 推进到至少已恢复 token；恢复 token=1 后第一次重选得到 2，不会复用旧 key。
- **持久标记误判新启动为恢复**：移除 `externalConsumedRequest` 的 SharedPreferences 持久标记与 `onDestroy` 清理，只使用 `savedInstanceState` 中的已消费请求。`savedInstanceState == null` 的新 ACTION_VIEW/SEND 即使同一 URI 也会正常打开；有 saved state 的重建仍跳过已消费请求。
- 回归：`ExternalDocumentPolicyTest` 增加 `viewerKey` 同 URI 不同 token、`restoredTokenSequence(0,1)+1==2`、无恢复状态同一请求仍消费；完整 JVM 619 tests / 0 failures，lint 0 errors / 101 warnings / 1 hint。

- **关闭后丢失已消费请求**：新增纯 Kotlin `ExternalDocumentSaveState`，把 descriptor 与 consumedRequest 分开序列化；`MainActivity.onSaveInstanceState` 不再把 consumedRequest 放在 descriptor 非空分支内。补充「关闭查看器→保存/恢复→不重放原 Intent」回归。完整 JVM 619 tests / 0 failures，lint 0 errors / 101 warnings / 1 hint。

### 未完成与暂停项

- 未完成：独立 Codex High 审查与设备 QA；合并 main 后 head `01ee0cd` 的 CI 已通过；PR [#37](https://github.com/leezzou/tanuki/pull/37) 已创建、未合并。
- 外部阻塞：无。
- 用户暂停：无。
- 未验证项：见本记录“验证记录”与“未完成与暂停项”。

### 下一步

1. 等待独立 Codex High 审查与设备 QA；根据审查意见修复并推送同一 PR [#37](https://github.com/leezzou/tanuki/pull/37)，不要另开替代 PR。
2. 在真实 ARM64 设备/模拟器上用系统 DocumentsUI 验证：PDF/图片/文本/DOCX/XLSX 直开、旋转、进程被杀后恢复、权限撤销后的中文错误与重新选择、显式复制到工作区、超大文件拒绝、音视频直开；设备验证前不要宣称已通过。
3. 需要时遵循项目规则触发独立 Codex High 审查；不得自行合并或发布。

### 约束与风险

- 数据/设备/权限边界：外部 URI 只接受 `content://` 且有 authority；拒绝 `file://`、`http(s)://`、userInfo、控制字符和超长 URI；不开放写权限、不把 URI 交给 DSH/浏览器 AI；复制回退沿用 512 MiB/容量预算/临时目录回滚；DOCX/XLSX 暂存只在私有 cacheDir、64 MiB 上限、解析后删除。
- 关键决策及理由：查看器改为 `OpenDocument` 抽象，工作区与 content URI 共用预览/导出/打开方式；文本用 `SeekableInput` 区分随机读与顺序流，避免整文件入内存；DOCX/XLSX 的 ZIP 随机访问通过有界私有缓存暂存解决，不写入工作区；非持久授权不阻断本次读取，但明确提示进程重建风险。
- 失败尝试与避免重复踩坑的结论：首个 CI head 暴露了 Android 仪器测试仍调用旧查看器签名，已改为 `WorkspaceDocument` 并补跑 `compileModernDebugAndroidTestKotlin`；本地 JVM/lint 不会编译 androidTest，后续改动查看器签名必须显式编译 Android 测试源码。顺序流读完表头后不能回退到 0；已将表头字节作为首页前缀，避免 `StreamSeekableInput` 抛“向后跳转”错误。`BoundedCopy` 超限改为 `IOException` 并携带具体上限文案，避免被误报为“文件损坏”。`ContentDocument` 的 `ParcelFileDescriptor` 所有权交给 `PdfDocumentSession`，用 `@SuppressLint("Recycle")` 说明而非在返回前关闭。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本次未生成发布 APK/源码 ZIP；仅源码改动与文档。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装设备、未覆盖安装、未发布；PR [#37](https://github.com/leezzou/tanuki/pull/37) 已创建、待审。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮未删除历史交接；普通 PR 只清理本次范围内确认过期的记录，跨 PR 清理由集成维护方统一处理。本次暂存只包含本记录、代码与测试改动；`AGENT-STATE.md`/`CURRENT.json` 未修改。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。

<a id="20261110-1133-design-tokens"></a>

## 20261110-1133-design-tokens

- Agent/会话标识：android_agent-59
- 时间：2026-11-10T11:33Z

### 当前状态

- 已实现：`app/src/main/kotlin/com/tanuki/ui/design/` 下 7 个新增源码文件和 JVM 回归；见第 3 节。
- 已验证：`compileModernDebugKotlin` 通过；定向 JVM `TanukiDesignTokensTest` 19 tests / 0 failures / 0 errors / 0 skipped；完整 JVM 952 tests / 102 个结果文件全部通过；lint 0 errors / 103 warnings / 1 hint；`assembleModernDebug -x buildNativeRuntime` 通过（APK 不含原生运行时，不是设备/发布产物）；两套主题各 28 项对比度审计全部通过。
- 待验证：完整原生构建、Android 仪器测试、页面接入后的深色/大字体/低 API/模拟器视觉验收。
- 当前目标包名、分支/无 Git 状态：无安装/无设备任务；分支 `ao/design-tokens/root`，基线 `1122d3f`，工作树只新增文件。
- 关键事实与可定位源码/文档：颜色与审计在 `TanukiPalette.kt`/`TanukiColorMath.kt`；组件在 `TanukiComponents.kt`/`TanukiGlass.kt`/`TanukiBottomSheet.kt`；令牌说明在 [设计令牌文档](DESIGN-TOKENS.md)。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiColorMath.kt` | 新增纯 Kotlin OKLCH→sRGB、色域映射、alpha 合成；WCAG 亮度/对比度委托 main 的 `TanukiContrast` |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiPalette.kt` | 新增共享明度阶梯、五组色相、浅/深角色令牌、状态色和对比度审计 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiTokenSpec.kt` | 新增间距、圆角、四档字体、动效、海拔/玻璃四层纯值 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiDesignTheme.kt` | 新增令牌 CompositionLocal、Compose 字体/形状映射、reduced-motion |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiComponents.kt` | 新增胶囊按钮、状态点/chip、按压容器、流式光标、代码/路径文本；三态复用 main 组件；leading 插槽提供与 tone/enabled 一致的 `LocalContentColor` |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiGlass.kt` | 新增 chrome/sheet/card/pill 表面、玻璃胶囊、内容卡 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiBottomSheet.kt` | 新增可拖拽关闭的底部弹层骨架 |
| `app/src/test/kotlin/com/tanuki/ui/design/TanukiDesignTokensTest.kt` | 新增 18 项 JVM 回归：转换、对比度、字体/间距/圆角/动效/海拔取值 |
| `docs/DESIGN-TOKENS.md` | 新增令牌清单、组件清单、对比度结果和页面接入方式 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `./gradlew :app:compileModernDebugKotlin -x buildNativeRuntime` | 通过；JDK17，`BUILD SUCCESSFUL` | 只证明 Kotlin/Compose 编译通过，不证明页面视觉或设备行为 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.ui.design.TanukiDesignTokensTest' -x buildNativeRuntime` | 通过；19 tests / 0 failures / 0 errors / 0 skipped | 结果文件 `app/build/test-results/testModernDebugUnitTest/TEST-com.tanuki.ui.design.TanukiDesignTokensTest.xml` |
| `./gradlew :app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | 通过；完整 JVM 952 tests / 102 个结果文件，0 failures / 0 errors / 0 skipped；lint 0 errors / 103 warnings / 1 hint | 新增 `ui/design` 文件没有 lint 告警；仅证明宿主 JVM/lint，不是设备证据 |
| `./gradlew :app:assembleModernDebug :app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | 通过；`BUILD SUCCESSFUL` | APK 使用 `-x buildNativeRuntime`，不含原生运行时；不是可安装设备交付物，也不是发布包 |
| 对比度审计输出 | 浅色 28/28、深色 28/28 通过；正文最低 6.18/5.73（浅/深），停用文字 4.43/4.28（大字 3.0 门槛） | 审计只覆盖令牌配对；玻璃压在任意页面内容上的实际对比度仍需设备/视觉验证 |

- 哪些是前一轮结果，哪些是本次新验证：以上编译、定向/完整 JVM、lint 与 assemble 均为本轮新执行；未复用历史测试数字。
- 未测试、超时、失败和暂未复现的情况：完整原生构建、Android 仪器、模拟器/真机、页面接入后的大字体/深色/低 API 均未执行；无失败或超时。

### 未完成与暂停项

- 未完成：页面接入（按 [设计令牌文档](DESIGN-TOKENS.md) 分批进行）；CI；完整原生构建；设备视觉验收；PR 推送与独立审查。
- 外部阻塞：无。
- 用户暂停：真实模型/账号验收、API Key 保存、真机验收沿用既有暂停项，本任务未触碰。

### 下一步

1. 完成 commit/push 并创建 PR，正文附摘要、测试结果和未验证范围；等待独立审查与适用 CI。
2. 页面接入先从 `DshNativePage` 的三态和流式光标、`DshSessionManagerPage` 的弹层开始，每次只改一个关注点并补对应回归。
3. 接入后重跑 lint、完整 JVM，补完整原生构建；设备视觉由独立 QA 在明确 AVD/serial 上执行，不把宿主结果当 Android 验收。

### 约束与风险

- 数据/设备/权限边界：本任务未安装、未启动设备、未读写用户数据；没有 APK/源码包产物。
- 关键决策及理由：用 OKLCH 共享明度阶梯保证中性色与语义色同明度；只有 chrome/pill 模糊，sheet/card 保持不透明以保证密集内容可读；无背板时玻璃退化为半透明而不是伪模糊；等宽字体只允许 `TanukiCodeText`/`TanukiPathText`。
- 失败尝试与避免重复踩坑的结论：首次编译因 `Shapes` 需要 `CornerBasedShape` 而失败，已把形状令牌类型收窄；玻璃无背板路径最初实际上不透明，已改为独立半透明分支；review 指出 leading 图标仍读取外层 `LocalContentColor`，已用当前 tone/enabled 的内容色包裹插槽并补浅/深 Filled、Danger、停用态回归。
- 风险：`TanukiDesignTheme` 与现有 `TanukiTheme` 并存，页面迁移期必须二选一；玻璃 chrome 的对比度审计基于固定 0.86 不透明度压在画布上，不能外推到任意背景。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本次未生成，未覆盖安装，未发布。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本 PR 只新增本次记录，没有删除或迁移历史交接；共享状态 `AGENT-STATE.md` 与 `CURRENT.json` 未修改，由集成维护方后续刷新。
- 普通 PR 只运行 `check-handoff.py --pr`；不运行写印章的集成/发布模式。

结束前命令：

```bash
python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1133-design-tokens.md
python3 scripts/check-publication.py --git
```

<a id="20261110-1138-remote-relay-pairing"></a>

## 20261110-1138-remote-relay-pairing

- Agent/会话标识：android_agent-48（Tanuki DSH Worker）
- 时间：2026-11-10 11:38 Asia/Shanghai（开始）；结束时间见下方验证

### 当前状态

- 已实现：中继配对契约与 Android 客户端、loopback 桥、直连/中继路由、加密存储/稳定身份/Cookie 作用域、设置页配对入口、Python 参考中继/主机端 agent、共享协议向量与回归。
- 已验证（本轮实际执行）：
  - `compileModernDebugKotlin` BUILD SUCCESSFUL。
  - `RemoteRelayTest` 22 tests / 0 failures / 0 errors / 0 skipped。
  - Python 参考实现 7 项回环端到端自测 OK。
  - P1 修复前完整 JVM/lint：BUILD SUCCESSFUL（2m32s）；552 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 95 warnings / 1 hint。
  - 合并 `origin/main=533eff4` 后、P2 修复前完整 JVM/lint：BUILD SUCCESSFUL（2m20s）；575 tests / 0 failures / 0 errors / 0 skipped（68 个结果文件）。
  - P2 修复后、合并 `2a4a050` 前完整 JVM/lint：BUILD SUCCESSFUL（2m17s）；581 tests / 0 failures / 0 errors / 0 skipped（68 个结果文件）。
  - 合并 `origin/main=2a4a050`（PR #35 复制界面）后完整 JVM/lint：BUILD SUCCESSFUL（4m06s）；**600 tests / 0 failures / 0 errors / 0 skipped**（72 个结果文件）；lint **0 errors / 96 warnings / 1 hint**。
- 待验证：真实 Android 设备到真实中继/sshd/DSH、Android Keystore token、真实公网 TLS/域名/CGNAT、弱网/断线、多客户端/限流、二维码扫描、Ed25519、WebRTC/TURN；独立 Codex High Review、合并/发布未授权。
- 主线同步：已合并 `origin/main=533eff4`、`1cb7ad9`、`2a4a050`，解决 `AGENT-STATE`/`TEST-REPORT`/`CURRENT.json`/`verify.yml` 冲突；合并后的 app 代码与 `049edcc` 相同。
- 当前目标包名、分支/无 Git 状态、是否有运行中任务：分支 `ao/tanuki-remote/relay-pairing`，基线 `origin/main=0555c3c0eaeeed181269b5e68733ccfdd48d9e75`；PR [#36](https://github.com/leezzou/tanuki/pull/36) 已创建，base `main`；无运行中任务。
- 关键事实与可定位源码/公共文档：方案/资源/流程见 `docs/REMOTE-CONNECT.md`；契约见 `docs/INTERFACES.md` 1.6；实现见 `app/src/main/kotlin/com/tanuki/runtime/{RemoteRelay,RelayTransport,RemoteHost,RemoteHostStorage,RemoteHostController}.kt`；参考中继/agent 见 `scripts/remote-relay/`；JVM 回归见 `app/src/test/kotlin/com/tanuki/RemoteRelayTest.kt`。

### 本次改动

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

#### Review 修复

- PR #36 [P1 discussion r4521208579](https://github.com/leezzou/tanuki/pull/36#discussion_r4521208579)：中继 Cookie 作用域原来只有 `relay:<hostId>`，同一主机不同 SSH 用户/远程 DSH 端口会共用 Cookie jar，切换时可能恢复上一服务的认证 Cookie。已改为 `relay:<hostId>:<user>:<remoteDshPort>`；room 不参与，因此同一服务换房间仍复用 Cookie，不同用户/DSH 端口隔离。
- 回归：`DshRemoteIsolationTest` 覆盖同 hostId 下不同用户/端口分区、同服务换 room 复用；`RemoteRelayTest` 断言更新为新作用域。同步修正 `INTERFACES` 1.6、`REMOTE-CONNECT`、`REMOTE-HOST`、`SECURITY` 与状态文档。
- PR #36 [P2 discussion r4521208583](https://github.com/leezzou/tanuki/pull/36#discussion_r4521208583)：`RelayBridge.close` 原来在关闭前等待 `writerLock`，对端停读时会被阻塞的 TLS 写拖住；`RelayClientSession.close` 也在关闭路径同步写 CLOSE。已改为先关闭底层 socket 解除阻塞、不再同步写 CLOSE，并让 `RelaySshTunnel.close` 先拆桥再关 JSch。回归：`bridgeCloseDoesNotWaitForBlockedTlsWrite`、`sessionCloseDoesNotAttemptBlockingCloseFrameWrite`。
- PR #36 [P2 discussion r4521208585](https://github.com/leezzou/tanuki/pull/36#discussion_r4521208585)：参考中继 `Peer` 的 32 帧有损队列在慢消费者时会断开合法 SSH。已去掉队列与 writer 线程，改为每 peer 写锁内的阻塞写，把背压沿 TCP/TLS 传回上游；所有可能阻塞的发送移到 `room.lock` 之外，避免死锁。回归：Python 7 项中的 2 MiB 慢消费者完整传输。
- PR #36 [P2 discussion r4521208592](https://github.com/leezzou/tanuki/pull/36#discussion_r4521208592)：`RelayReconnectPolicy` 原来没有生产调用方。已抽出 `relayRetry` 并接入 `RelaySshTransport.join`，最多 4 次尝试、只重试网络/房间忙/主机离线等瞬时错误、协程取消可中断；认证/钉扎/协议错误只尝试一次。回归：先失败后成功、重试耗尽、认证/钉扎/协议不重试、退避期间取消。

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin -x buildNativeRuntime` | BUILD SUCCESSFUL（约 3m53s；含既有告警） | 仅 Kotlin 编译；未跑设备 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.RemoteRelayTest' -x buildNativeRuntime` | BUILD SUCCESSFUL；22 tests / 0 failures / 0 errors / 0 skipped | 覆盖配对链接、帧、HMAC 向量、钉扎、重连、假中继 DATA 回声、错误 token、存储、身份/Cookie、控制器、P2 close/重试 |
| `cd scripts/remote-relay && python3 test_remote_relay.py` | OK；7 tests / 0 failures | 自签名 TLS + pin、注册、JOIN/OPEN_OK/DATA、2 MiB 慢消费者、错误 token、未知房间、明文拒绝、日志不含 token；仅回环；已加入 CI Host job |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.DshRemoteIsolationTest' --tests 'com.tanuki.RemoteRelayTest' -x buildNativeRuntime` | BUILD SUCCESSFUL；27 tests / 0 failures / 0 errors / 0 skipped | P1/P2 修复后定向回归；含 Cookie 用户/端口分区、close、重试 |
| `./gradlew :app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime`（P1 修复前） | BUILD SUCCESSFUL（2m32s）；66 个结果文件，552 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 95 warnings / 1 hint | 完整 JVM 含既有 535 + 新增 17 |
| 合并 `origin/main=533eff4` 后、P2 修复前 `./gradlew ...` | BUILD SUCCESSFUL（2m20s）；68 个结果文件，575 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 95 warnings / 1 hint | main 558 + 中继 17；解决 AGENT-STATE/CURRENT 冲突后重跑 |
| P2 修复后 `./gradlew ...`（合并 `2a4a050` 前） | BUILD SUCCESSFUL（2m17s）；68 个结果文件，581 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 95 warnings / 1 hint | 新增 6 项 close/重试回归；Python 7 项通过 |
| 合并 `origin/main=2a4a050` 后 `./gradlew :app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL（4m06s）；72 个结果文件，600 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 96 warnings / 1 hint | main 558 + 中继 23 + 复制界面 19；解决 AGENT-STATE/TEST-REPORT/CURRENT 冲突后重跑 |
| `python3 scripts/check-handoff.py --record ...` / 无参检查 | 通过；602 public files | 本轮结束刷新印章 |
| `python3 scripts/check-publication.py --git` | 0 findings | 实际暂存内容检查 |
| PR #36 P2 修复提交 `049edcc` 的 push/PR CI | Host scripts、Android build/JVM/lint 均 pass | 公开 Actions；最终 merge head CI 以 PR 页面为准 |
| `python3 scripts/package-source.py` | 待执行 | 仅检查源码包，不提交 |

- 哪些是前一轮结果，哪些是本次新验证：本次只记录上表本轮实际执行；0.1.10 的 535 tests 与旧 PR 结果未复跑，不作为本轮结论。
- 未测试、超时、失败和暂未复现的情况：真实设备/公网中继/真实 sshd 与 DSH、Keystore 真机、弱网/断线、多客户端/限流、二维码扫描、Ed25519、WebRTC/TURN；未安装设备、未生成发布包。

### 未完成与暂停项

- 未完成：独立 Codex High Review、真实设备/公网中继 QA。
- 外部阻塞：真实公网中继与 Android 设备需用户/独立 QA 提供资源与授权。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收继续按既有要求等待授权/恢复；不合并、不发布、不触发 AO Review。

### 下一步

1. PR [#36](https://github.com/leezzou/tanuki/pull/36) 已创建，base `main`；以 PR 页面最终 head/CI 为准，不合并、不发布。
2. 由 Orchestrator 安排当前 head 的独立 Codex High Review；真实设备/公网中继 QA 需独立 QA session、明确中继地址/证书和 APK/SHA。

### 约束与风险

- 数据/设备/权限边界：未安装/启动 App，未操作设备，未读取真实凭据；所有回归使用合成 token/证书/字节；参考中继仅回环测试。
- 关键决策及理由：优先反向注册中继 + SSH 端到端，复用既有 JSch 主机密钥/认证/转发与 loopback origin；中继只转发密文，TLS 必须系统 CA 或钉扎；WebRTC/TURN 留后续。
- 失败尝试与避免重复踩坑的结论：
  - 握手不能用 `BufferedInputStream`：会把 `OPEN_OK` 之后紧邻的首批 SSH 字节预读进旧缓冲区；改为无缓冲 `DataInputStream`，并加“早期 banner 不丢失”回归。
  - Python `settimeout(0.0)` 是 non-blocking 而非无超时；握手后改为 `settimeout(None)`，否则 agent/中继空闲会误断。
  - IPv6 在配对链接中需要方括号编码，解析后存储/拨号用不带方括号形式；中继主机规范化单独接受无方括号 IPv6。
  - 中继 token 只在内存保存，不写日志；手机端 token 经 Keystore 加密，配对文件 0600；参考中继未做生产化限流/HA，已在文档标注。

### 产物与校验

- PR [#36](https://github.com/leezzou/tanuki/pull/36)，base `main`，head 以 PR 页面为准。
- 新/旧 APK、源码 ZIP 及校验文件：本轮不生成 APK/ZIP 发布附件；只创建 PR。
- 是否覆盖安装/签名范围/未发布事项：不安装、不覆盖、不发布。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮未删除旧交接；现有记录仍含未完成 Review/QA 或历史验证依据，未判定为可安全删除。若后续确认过期，应先迁移结论到 `AGENT-STATE`/`TEST-REPORT`/专题文档，再删除并修复引用。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。

<a id="20261110-1139-native-approval-tools"></a>

## 20261110-1139-native-approval-tools

- Agent/会话标识：android_agent-45（AO worker）
- 时间：2026-11-10 11:39–16:30 CST

### 合并说明

- 已合入 `origin/main=bc802cf`（PR #38 原生会话管理；此前已合入 PR #42 交接机制、PR #36 中继配对、PR #35 copy-flicker、PR #32 parity audit、PR #34 文件查看器格式扩展）。代码无重叠。按新交接机制，`docs/AGENT-STATE.md` 与 `docs/handoffs/CURRENT.json` 直接取 main 版本，本 PR 不包含对这两个共享状态文件的任何改动。

### 当前状态

- 已实现：审批协议/回答、WebSocket 审批通道、工具时间线/卡片、审批面板、控制器生命周期、5 项 Review 修复、文档与回归。
- 已验证：`compileModernDebugKotlin`、完整 `testModernDebugUnitTest` 691/0/0/0、`lintModernDebug` 0 errors / 97 warnings、`assembleModernDebug` 通过（未安装）。
- 待验证：真实设备/模拟器 UI、真实 PRoot DSH `/connect`（answer Peer）/WebSocket/审批回答、远程 SSH 隧道、安全模式 rescue、通知点击过滤、Android 仪器/真机。
- 分支 `ao/tool-approval/root`，基线 `origin/main=0555c3c`，已合入 `main=bc802cf`；无运行中安装/设备任务。
- 四文件归属：`DshNativeApi.kt`、`DshNativeController.kt`、`DshNativePage.kt`、`MainActivity.kt` 已恢复为 `origin/main` 内容，本切片不再修改；42 接线清单见 [NATIVE-UI](NATIVE-UI.md)。

### 42 接线清单

1. `DshNativeApi.kt`
   - `DshSessionPage` 增加 `timeline: List<DshTimelineItem> = messages.map { DshTimelineItem.Text(it) }`。
   - `parsePage` 读取 `records` 后调用 `DshToolTimeline.parse(records)`，写入 `timeline`；既有 `messages` 契约不变。
2. `DshNativeController.kt`
   - `DshNativeState` 增加 `timeline: List<DshTimelineItem>`；历史成功时 `timeline = page.timeline`，选择/加载/清空时同步清空。
   - 提供按审批会话读取工具的能力，例如 `suspend fun tool(sessionId: String, callId: String): DshToolItem?`；历史后到时调用 `approvalController.updateTool(sessionId, tool)`。
3. `DshNativePage.kt`
   - 增加可选参数：
     ```kotlin
     approvalState: DshApprovalState = DshApprovalState(),
     onApprovalAnswer: (String, DshApprovalOutcome, Boolean) -> Unit = { _, _, _ -> },
     onApprovalRetry: () -> Unit = {},
     onClearRemembered: () -> Unit = {}
     ```
   - 在 `LazyColumn` 首个 `item` 挂 `DshApprovalPanel(state, selectedId, onAnswer, onRetry, onClearRemembered)`。
   - 时间线 `DshTimelineItem.Text` 渲染 `MessageCard`，`DshTimelineItem.Tool` 渲染 `DshToolCard(tool)`。
4. `MainActivity.kt`
   - 复用同一 `endpointProvider` 创建 `DshApprovalController(endpointProvider, lifecycleScope, toolLookup = { sessionId, callId -> ... })`；`toolLookup` 必须按 `sessionId+callId` 查询，不能用当前选中会话的旧快照。
   - 在原生模式 + 服务 `RUNNING` 时 `start()`；停止/切官方/页面销毁时 `close()`；origin 变化时重建。
   - 收集 `approvalController.state`，把状态与回调传给 `DshNativePage`。
5. 通知点击：42 切换 `selectedId` 后，审批面板自然按当前会话过滤；无需新增通知协议。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshApprovalProtocol.kt` | 新增：审批帧/`interactions=answer` 连接/回答信封/风险分级/本次运行记忆；未知 toolName 只允许拒绝。 |
| `app/src/main/kotlin/com/tanuki/web/DshWebSocketClient.kt` | 新增（临时）：最小 RFC6455 客户端；loopback、Cookie、Accept 校验、客户端掩码、拒绝服务端掩码、1 MiB 上限、握手有界超时、先发布可关闭 socket。待收敛到 42 transport。 |
| `app/src/main/kotlin/com/tanuki/web/DshApprovalSession.kt` | 新增：`/connect`（answer）→`$events`→`$events/result`、401 断开重连/独立 Peer、非审批 next、ready 10 秒超时、中文错误；定义 `DshApprovalTransport`。 |
| `app/src/main/kotlin/com/tanuki/web/DshToolTimeline.kt` | 新增：自包含解析 `tool/call`/`tool/result`/`meta.diffs` 与文本消息；命令/cwd/路径、输出截断、replace 去重、diff LCS。 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalController.kt` | 新增：审批状态机；按 `sessionId+callId` 异步取参数；`updateTool` 后到更新；未知类型拒绝；断线代次清理；记忆存在时面板可见。 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalPanel.kt` | 新增：审批卡片；工具名、参数（长参数可展开）、风险、允许/拒绝/记住、其他会话计数、错误重试、记忆撤回入口。 |
| `app/src/main/kotlin/com/tanuki/ui/DshToolCard.kt` | 新增：工具卡片；运行中/成功/失败、命令、文件变更、diff、参数、折叠输出、风险徽标。 |
| `app/src/test/kotlin/com/tanuki/DshApprovalProtocolTest.kt` | 11 项：帧/白名单/answer 连接/回答信封/风险/记忆/未知类型。 |
| `app/src/test/kotlin/com/tanuki/DshToolTimelineTest.kt` | 9 项：工具配对/失败/replace/孤儿/截断/diff/命令路径/文本过滤。 |
| `app/src/test/kotlin/com/tanuki/DshWebSocketTest.kt` | 7 项：RFC6455 Accept/掩码/握手/超时/close 解除阻塞。 |
| `app/src/test/kotlin/com/tanuki/DshApprovalSessionTest.kt` | 5 项：全链路/401/HTTP 500/缺端点/非 loopback；假服务器断言 `interactions=answer`。 |
| `app/src/test/kotlin/com/tanuki/DshApprovalControllerTest.kt` | 12 项：允许/拒绝/记忆/按审批会话取参数/历史后到/未知类型/失败/断线重连/origin/面板可见。 |
| `docs/INTERFACES.md` | 更新 1.7：answer Peer、`$events`/`$events/result`、参数后到、未知类型、临时 WebSocket 收敛说明。 |
| `docs/NATIVE-UI.md` | 更新归属边界、42 接线清单、文件表、安全边界与验证/未验证。 |
| `docs/TEST-REPORT.md` | 新增本切片验证与 5 项 Review 修复记录。 |

### Review 修复

1. **P1 answer Peer**：`DshApprovalProtocol.connectUrl()` 生成 `interactions=answer`，只读列表继续 `interactions=none`；`DshApprovalSession` 使用新函数；假服务器断言改为 `answer`。
2. **P2 按审批会话取参数**：`toolLookup` 改为 `suspend (sessionId, callId) -> DshToolItem?`；收到审批后异步读取并更新参数/风险；新增 `updateTool(sessionId, tool)` 处理历史后到；非当前会话也按 `request.sessionId` 查询；参数未到时禁止盲目允许，2 秒超时后显示“未读取到参数”。
3. **P2 记忆撤回入口**：`DshApprovalState.shouldShowPanel()` 把 `remembered` 纳入可见条件；成功回答后 pending 为空仍显示面板与“清除记住的选择”。
4. **P2 断线释放 answering**：`answering` 改为 `eventId -> 连接代次`；断线清空；旧回答退出按代次校验，不误伤重连后的卡片；新增“回答中断线后重连仍可回答”回归。
5. **P2 握手期限**：握手期 `soTimeout=handshakeTimeoutMs`（默认 10 秒），先发布 socket/output，握手完成后再设 0；`close()` 可解除握手中阻塞；新增“接受 TCP 但不返回 Upgrade 头”和“握手中 close()”回归。
6. 额外安全：未知/非法 `toolName` 显示“未知审批”，只能拒绝，不能按记忆自动允许。
7. **认证失效重建 Peer**：401/403 不再用新 Cookie 回答旧 clientId；明确断开并提示“审批认证已失效，需要重新连接”，由重连创建新 answer Peer；假服务器按每次 `/connect` 分配独立 Peer 并校验回答归属。
8. **重连重启参数查询**：`lookupTokens` 按连接代次去重；新流就绪或 Host 重放同一 eventId 时重新启动仍为 `lookupPending` 的查询；旧代次结果被丢弃，避免卡片永久卡在“正在读取参数”。
9. **非审批 waterfall 回 next**：`user-questions/request` 等合法 waterfall 通过 `$events/result` 回 `outcome.kind=next`，不产生审批卡片、不静默吞；补协议/传输/控制器回归。

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh` + `./gradlew :app:compileModernDebugKotlin -x buildNativeRuntime --console=plain` | BUILD SUCCESSFUL | JDK17 worktree 环境 |
| `./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime --console=plain` | BUILD SUCCESSFUL；80 个结果文件，691 tests / 0 failures / 0 errors / 0 skipped | 基线 535 + 本切片 48 + PR #34 的 23 + PR #35 的 19 + PR #36 的 23 + PR #38 的 43 |
| `./gradlew :app:lintModernDebug -x buildNativeRuntime --console=plain` | BUILD SUCCESSFUL；0 errors / 97 warnings / 1 hint | 存量弃用/可读性告警 |
| `./gradlew :app:assembleModernDebug -x buildNativeRuntime --console=plain` | BUILD SUCCESSFUL | 仅打包验证，未安装 |
| 目标回归 48 项：Protocol 12、Timeline 9、WebSocket 7、Session 6、Controller 14 | 48/48 通过 | 本地合成 HTTP+WebSocket 服务器；不代表真实 DSH |
| 既有 `DshNativeApiTest` 12、`DshNativeControllerTest` 6、`DshUiSwitchTest` 10 | 通过 | 四文件已恢复 main，旧契约不受影响 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1139-native-approval-tools.md`、`python3 scripts/check-publication.py --git`、`package-source.py` | 通过 | 新交接机制；不修改共享状态文件 |

- 哪些是前一轮结果，哪些是本次新验证：以上产品编译/JVM/lint/assemble 均为本次新跑；未复用历史数字。
- 未测试、超时、失败和暂未复现的情况：真实设备/模拟器、真实 PRoot DSH、远程 SSH 隧道、安全模式 rescue、通知点击过滤、Android 仪器、真机均未执行。

### 未完成与暂停项

- 未完成：提交/推送新 head、等待新 head CI；10 条 review thread 已全部 resolve；PR 不合并、不发布。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收。

### 下一步

1. 以 `leezzou <115924933+leezzou@users.noreply.github.com>` 提交并推送 `ao/tool-approval/root`（一次 push），确认 `AGENT-STATE.md`/`CURRENT.json` 与 main 完全一致。
2. 确认新 head Host/Android CI；10 条 review thread 已全部 resolve。
3. 真实设备/PRoot 审批与工具卡片验收由独立 QA 另行授权；远程 SSH 隧道与安全模式 rescue 同样待验。

### 约束与风险

- 数据/设备/权限边界：不读真实 Key/Cookie/token/会话；不改 DSH 数据；设备 QA 由独立 QA 独占。
- 关键决策及理由：审批回答只走官方 `$events/result`；审批用独立 answer Peer；参数按审批会话读取，避免用当前选中会话旧快照；“记住选择”仅本次原生运行内存，不冒充官方持久授权；未知审批只能拒绝；临时 WebSocket 不再扩展，等待 42 transport。
- 失败尝试与避免重复踩坑的结论：官方 `$events`/`$events/result` 需要 `interactions=answer`；append 的 `surfaceOp` 是字符串，replace 才是对象；同一 callId 的 replace 结果必须更新已有卡片；WebSocket 服务端帧不能掩码；握手必须有界且可 close；断线必须清 answering 代次。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未生成发布 APK/源码 ZIP；未安装、未发布。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装；未发布。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：保留 PR #34 记录与本切片记录；未删除历史记录。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP。

<a id="20261110-1140-native-parity-audit"></a>

## 20261110-1140-native-parity-audit

- Agent/会话标识：android_agent-47（AO worker，项目 android_agent）
- 时间：2026-11-10 11:40 CST

### 当前状态

- 已实现：`docs/NATIVE-PARITY.md`（166 行矩阵，14 个功能域，每行含官方行为、原生现状、缺口/归属/验收）；`scripts/check-native-parity.py`（矩阵结构、ID、状态、优先级、验收引用、路径检查，带 `--self-test`）；本交接记录。
- 已验证：`python3 scripts/check-native-parity.py --self-test` PASS；`python3 scripts/check-native-parity.py` PASS（166 行、14 域）；`python3 -c "compile(...)"` 语法检查 PASS；`python3 scripts/check-handoff.py --record ...` 与无参数 `check-handoff.py` PASS（594 public files）。
- 待验证：所有 JVM/仪器/手工验收用例均未执行；矩阵中的“原生现状”来自源码/既有测试与只读分支核对，不代表当前设备行为。
- 当前目标包名、分支/无 Git 状态：分支 `ao/parity-audit/root`，基线 `0555c3c0`；无运行中产品任务；未安装 APK、未启动设备。
- Rebase（2026-11-10）：PR 从 `0555c3c0` rebase 到 `origin/main=533eff4`；冲突仅 `docs/AGENT-STATE.md` 与 `docs/handoffs/CURRENT.json`。AGENT-STATE 保留 main 的 PR #34 内容并追加本审计链接；CURRENT.json 按新 main 指纹重新生成。
- Review P2 补强（2026-11-10）：检查器新增“跨功能域复用”校验；29 个合理跨域用例均补 `- 跨域：` 理由，未说明理由的跨域复用直接失败。这样既保留共享用例，又能发现“同一用例被不同能力蹭用”的可疑情况。
- Review P2 已处理（2026-11-10）：SESS-14/15、SET-05/07/15 等缺口不再引用不覆盖它们的共享用例；新增 22 个缺口用例（goals、schedule、快捷键、语言、遥测、工作区管理、消息操作/链接/查找/滚动/中断、权限预设、Office/PDF、自动审查/CoT、账户配额、语音、计划模式、全局搜索、应用内徽标、文件管理器打开、应用更新、存储/备份、复制会话 ID/日志导出、@ 提及 UI），每个用例用 `- 覆盖：` 声明真正验证的矩阵行；检查器升级为拒绝“行引用了不覆盖该行的用例”。
- Review P2 第二轮（2026-11-10）：A-INPUT-01 增加 `fileReferences/list`、`@` 提及解析/候选/引用提交断言；新增 A-INPUT-06（@ 提及 UI/引用卡片）；SESS-16 改接 A-SESS-07（复制完整 ID/日志导出）；MODEL-10 改接 A-MODEL-05（account 余额/配额）；A-FILE-06 区分“文件管理器显示”（不复制）与“系统关联打开”（允许 `cacheDir/workspace-open` 单文件只读副本，验证只共享选中文件、只读 URI、原文件不变、临时目录清理）；同步移除不诚实覆盖（MODEL-10 从 A-MODEL-01，SESS-16 从 A-SESS-02/04，FILE-14/16 从 A-FILE-02，SET-10/13/14 从 A-SET-03，CHAT-19 从 A-SESS-05）。
- 编排器范围确认（2026-11-10）：`android_agent-44` 是实现会话管理（DshSessionManagerActivity/Controller/Page、DshSessionAdminApi），不与本审计重复；`docs/NATIVE-PARITY.md` 与 `scripts/check-native-parity.py` 由 android_agent-47 唯一维护，其他 worker 只提交证据、不双写矩阵。P0 已拆到 INPUT-01/02/03、CHAT-15/11、SESS-02/04/12、APPROVAL-01/02/05/06 等行，并标注 42 聊天链路 / 45 审批 / 46 输入 / 44 会话管理。
- 关键事实与可定位源码/公共文档：
  - 原生只读页：`app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt`、`DshNativeController.kt`、`app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt`；PR #26（android_agent-33）已合并 `d9b4aa2`。
  - 官方/原生切换：`app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt`；`docs/NATIVE-UI.md`、`docs/INTERFACES.md` 第 1.7 节。
  - 并行进行中：android_agent-42 未提交 `DshMuxProtocol.kt`/`DshWebSocket.kt`（session/follow、`$events`、审批/提问解析）；android_agent-45 tool-approval、android_agent-46 input-attachments、android_agent-48 remote relay 已建分支无提交；android_agent-44 分支名与本审计同题但无提交。
  - 既有能力：Android 聊天通知 PR #22（`c2f1b44`）、远程 SSH PR #29（`9e7edbb`）、安全模式 PR #25（`5fd1ab4`）、DOCX/XLSX/PDF PR #28/#23/#24、更新检查 PR #19、版本管理 PR #18、视频全屏 PR #17。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `docs/NATIVE-PARITY.md` | 新增功能对齐审计：状态/优先级图例、P0 拆解与 42/45/46/44 归属、14 类矩阵、64 个验收用例（每个声明覆盖行）、自动化与设备前置、并行归属与阻塞、矩阵唯一所有权、不做/不宣称、维护规则。 |
| `scripts/check-native-parity.py` | 新增只读文档门禁：校验 14 域、行 ID 唯一/归属正确、状态/优先级、验收用例定义、每行引用的用例必须覆盖该行、覆盖声明只能引用存在的行、跨域复用必须有 `- 跨域：` 理由、反引号仓库路径存在；`--self-test` 用内置样例验证能发现坏行、覆盖错配与可疑跨域复用。 |
| `.github/workflows/verify.yml` | 在 Host job 增加 `Check native parity matrix` 步骤，运行同一脚本，避免矩阵随代码漂移。 |
| `docs/handoffs/20261110-1140-native-parity-audit.md` | 本记录。 |
| `docs/AGENT-STATE.md` | 仅增加本审计记录链接；不改写当前状态、其他记录或印章。并发汇总仍由编排器指定的维护方完成。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `python3 scripts/check-native-parity.py --self-test` | PASS（有效样例通过；坏样例发现 5 个问题） | 本机 Python 3；只验证检查器逻辑，不验证矩阵事实。 |
| `python3 scripts/check-native-parity.py` | PASS（166 行、14 个功能域；ID/状态/优先级/验收引用/路径检查通过） | 只验证文档结构；矩阵中的产品结论来自源码/只读包，未跑产品测试。 |
| `python3 -m py_compile scripts/check-native-parity.py` | 失败（沙箱禁止写宿主 Python 缓存 `~/Library/Caches/com.apple.python/...`） | 不是脚本语法错误；改用下一条等价检查。 |
| `python3 -c "compile(open('scripts/check-native-parity.py',encoding='utf-8').read(),'scripts/check-native-parity.py','exec')"` | PASS（syntax PASS） | 不写字节码缓存；只验证 Python 语法，不执行产品代码。 |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261110-1140-native-parity-audit.md` | PASS（Handoff refreshed，594 public files） | 刷新印章；新文档/脚本已进入源码指纹。 |
| `python3 scripts/check-handoff.py` | PASS（Handoff current，594 public files） | 无参数检查印章当前。 |
| `python3 scripts/check-publication.py --git` | 0 findings（595 public files） | 提交前运行；未暂存前与提交后各核对一次。 |
| GitHub Actions Host job `Check native parity matrix` | 未在本机执行；PR CI 为准 | 已接入 `.github/workflows/verify.yml`，本轮未等待 CI 完成。 |
| 产品 JVM/仪器/设备 | 未执行 | 本任务为只读审计；不把历史测试当本轮结果。 |

- 哪些是前一轮结果，哪些是本次新验证：本次只新跑矩阵检查器与自测；PR #22/#25/#29/#28/#23/#24 等是既有记录，未在本轮复跑。
- Review P2 修复：新增 22 个缺口用例并补 `- 覆盖：` 声明；检查器从“编号存在”升级为“编号存在且覆盖该行”；再新增跨域复用校验，合理跨域用例补 `- 跨域：` 理由；`--self-test` 覆盖错配与可疑跨域用例均通过。矩阵仍 166 行、14 域、66 个用例。
- 内容复核：修正 10 处措辞，区分 Tanuki 专有运行时/存储能力与官方网页能力，并把 android_agent-42 的 mux/follow 明确标为“未提交、进行中”；矩阵门禁复跑 PASS。
- 范围确认后更新：新增 1.1 P0 拆解表，44 行矩阵补充 42/45/46/44 归属；矩阵仍为 166 行、14 域，门禁复跑 PASS。
- 未测试、超时、失败和暂未复现的情况：所有产品验收用例未执行；真实模型/账号、真机、ROM、远程 sshd、Keystore 均未测试；矩阵中的 `进行中` 分支未提交，不能写成已实现。

### 未完成与暂停项

- 未完成：
  - P0 缺口：原生页无 composer/发送/停止/审批回答；android_agent-42 的 mux 解析尚未接 UI，45/46 尚未提交。
  - P1 缺口：会话生命周期、模型、附件、会话搜索、工作区管理未排期。
  - P2 缺口：工具卡片、终端/浏览器/任务/子代理、插件/技能、交付物、完整设置、远程中继/多主机/Ed25519。
- 外部阻塞：44 已确认实现会话管理，边界已明确；仍需 42/45/46 的接口稳定并提交后再更新矩阵状态。矩阵/检查器由 47 唯一维护，其他 worker 只提交证据。
- 用户暂停：API Key 保存；真实模型/账号验收；真机验收。
- 设备：本审计未启动/安装/跑仪器；仪器用例需独立 Android QA 会话执行。

### 下一步

1. 运行 `python3 -m py_compile scripts/check-native-parity.py`、`python3 scripts/check-handoff.py --record docs/handoffs/20261110-1140-native-parity-audit.md`、`python3 scripts/check-handoff.py`，回填本记录。
2. 运行 `python3 scripts/check-publication.py --git`，按实际路径暂存 `docs/NATIVE-PARITY.md`、`scripts/check-native-parity.py`、`.github/workflows/verify.yml`、本记录与 `docs/AGENT-STATE.md` 链接；文档提交用 `docs: add native UI parity audit and acceptance matrix`，CI 接线用 `ci: check native parity matrix`。
3. 推送分支并创建 PR（base `main`），正文写摘要、实际验证命令、未验证范围与风险；不合并、不发布、不触发 AO Review（除非用户/编排器明确要求）。
4. 44 实现会话管理、42 接 mux/UI、45 接审批、46 接输入后，由矩阵属主核对 PR/commit/测试证据并更新 SESS/CHAT/INPUT/APPROVAL 行；42 的 mux 接 UI 后优先更新 CHAT-15、SESS-12、APPROVAL-01/05 状态。
5. 独立 Android QA 按第 4 节仪器/手工用例执行；真实模型/账号用例等用户恢复授权。

### 约束与风险

- 数据/设备/权限边界：不读取/输出真实 API Key、Cookie、token、用户会话或内部备份；不使用真实用户数据；不启动设备、不安装 APK、不跑仪器；设备由独立 QA 独占。
- 关键决策及理由：把“官方 WebView 可用”和“原生已对齐”分开记录，避免把回退能力写成原生实现；矩阵按 P0/P1/P2 与 L1/L2/L3 分层，便于排期；验收用例优先用合成 HTTP/WebSocket 服务做 JVM 自动化，设备相关标记为仪器/手工。
- 失败尝试与避免重复踩坑的结论：官方客户端前端 bundle 是动态插件加载，不能只靠 bundle 字符串枚举功能；改用官方客户端只读包 README、协议方法名与前端 devDependencies 组合取证。远程行曾把官方能力写得过宽，已改为只陈述可核实的“可信主机/远程访问配置”，Tanuki SSH 明确为原生能力。
- 协议风险：官方 0.2.1-rc.5 的 `session/follow`、`$events`、审批 waterfall 若升级调整，必须先更新 `docs/INTERFACES.md` 与回归，再改实现。
- 并发风险：`docs/AGENT-STATE.md` 是共享文件；本次只加链接，未改写当前状态。若其他 worker 同时写该文件，合并时以编排器指定维护方为准。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮不构建 APK、不生成源码 ZIP、不更新 `artifacts/`。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未覆盖、未签名、未发布；无 tag/Release。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：已核对 `docs/handoffs/` 现有 18 份记录；它们仍被 `docs/AGENT-STATE.md` 引用或含未完成的设备/Review/QA 事项，本轮未判定为可安全删除，未删除任何旧交接。保留本记录、TEMPLATE、CURRENT.json 与仍使用的验证/未完成记录；若后续集中清理，按 `docs/PUBLICATION.md` 先迁移独有结论再删除。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。
- 交付物：
  - `docs/NATIVE-PARITY.md`（主交付）
  - `scripts/check-native-parity.py`（可执行门禁）
  - 本交接记录

结束前执行：

```bash
python3 -m py_compile scripts/check-native-parity.py
python3 scripts/check-native-parity.py --self-test
python3 scripts/check-native-parity.py
python3 scripts/check-handoff.py --record docs/handoffs/20261110-1140-native-parity-audit.md
python3 scripts/check-handoff.py
```

<a id="20261110-1142-dsh-version-uninstall"></a>

## 20261110-1142-dsh-version-uninstall

- Agent/会话标识：android_agent-43（DSH Worker）
- 时间：2026-11-10 11:42 CST

### 当前状态

- 已实现：
  - 清单继续显示版本、占用、安装时间、当前/备用、依赖锁与启动校验；新增“当前版本未知时禁止切换/卸载”的明确说明。
  - 卸载后端新增当前版本、运行中服务、活动子进程、更新检查/安装占用拒绝；所有拒绝均为中文。
  - 卸载改为两阶段事务：`prepared` 日志 → 改名到 `runtime/dsh-<version>.uninstall` → `committed` 日志 → 删除。删除一旦开始就不再回滚（无法证明未删内容），保留隔离目录与 `committed` 日志交给 `recover()` 清理；提交日志失败时只有移回成功才清 `prepared` 日志，回滚失败保留记录供下次启动恢复。
  - 卸载只处理 `engines/<version>` 与隔离目录；共享 `userdata`、`workspaces`、`backups`、下载缓存和用户会话不在删除范围；硬链接载荷只回收不再被引用的项。
  - 切换/卸载与安装、启动共用环境 mutation；切换/卸载与更新检查共用 `DshVersionGate`，锁顺序固定为 mutation → versionGate。
  - 主界面当前版本“卸载”入口改为“管理版本”；官方版本选择弹窗只对非当前已安装版本显示“卸载该备用版本”。
- 已验证：`compileModernDebugKotlin` BUILD SUCCESSFUL；目标 JVM `DshUninstallTest` 17 项、`DshVersionsTest` 7 项、`DshSwitchTest` 12 项全过；完整 JVM **552 tests / 0 failures / 0 errors / 0 skipped**、lint **0 errors / 94 warnings / 1 hint**；合并 `origin/main=533eff4` 后完整 JVM **575 tests / 0 failures / 0 errors / 0 skipped**、lint **0 errors / 94 warnings / 1 hint**；再合并 #35/#36 后本地最近一次为 **594 tests / 0 failures / 0 errors / 0 skipped**、lint **0 errors / 95 warnings / 1 hint**；合入 `origin/main=7593805`（#42 新交接机制）后的完整套件由 CI 在新 head 复跑。
- PR [#33](https://github.com/leezzou/tanuki/pull/33) 已创建。Codex Review 的 3 条意见（删除后完整性、回滚日志保留、committed 误删 target）已在 `6c75914` 修复：删除开始后不再回滚，`prepared` 回滚失败保留日志，`DshUninstallRecovery` 让 committed 只清理隔离目录并保留可能重装的 target。第四轮发现 DSH 重装会覆盖共用 `component-transaction`：`replace()` 现在先 `recover()` 再写新日志，并补“卸载失败后重装另一版本，重启只清隔离目录、保留当前版本”的回归。review 修复新 head 以 PR 页面为准。
- 待验证：review 修复后新 head 的 Host/Android CI、独立 Codex High Review 复核、真实设备上的版本列表/确认/卸载/回滚/清单刷新、真实 Node 降级/依赖锁损坏/进程中断恢复。
- 当前分支：`ao/version-uninstall/root`，基线 `origin/main=0555c3c0`；已按新交接机制合入 `origin/main=7593805`（#42）；`AGENT-STATE.md`、`TEST-REPORT.md`、`CURRENT.json` 均取 main 版本，PR 不包含对它们的改动；仅保留本 handoff 与代码/测试改动。本地最近完整 JVM **594 tests / 0 failures / 0 errors / 0 skipped**、lint 0 errors/95 warnings/1 hint；合入后完整套件由 CI 复跑；未启动设备，未切换/卸载真实 DSH。
- 关键事实：卸载事务只接收程序目录和隔离目录；当前版本不提供任何后端卸载路径；`DshVersions.canUninstallFromManager` 在当前版本未知时返回 false。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/runtime/DshVersions.kt` | 新增 `DshVersionGate`（切换/卸载/更新检查互斥）、`DshUninstall`（拒绝矩阵与两阶段事务）、`DshUninstallRecovery`（prepared 恢复/committed 只清隔离目录/旧协议兼容）；删除开始后不回滚，回滚失败保留 prepared 日志；`DshSwitch.requireSwitchable` 增加更新忙碌拒绝。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeComponents.kt` | `uninstallDsh` 接入当前/运行/更新校验、版本窄锁与两阶段事务；`recover()` 改用 `DshUninstallRecovery`，committed 不再删除 engines 中可能重装的 target；`replace()` 通过 `DshComponentJournal.recoverThenWrite` 先恢复再写新日志，避免 DSH 安装/重装覆盖待恢复的卸载日志；`switchDsh` 接入版本窄锁与更新忙碌校验。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeManager.kt` | 新增 `versionGate`；环境锁抢占失败时同时写入中文错误，避免按钮点击无反馈。 |
| `app/src/main/kotlin/com/tanuki/runtime/State.kt` | 新增 `deleteState()`，用 `AtomicFile.delete()` 同时删除事务日志备份，避免下次启动恢复已完成的组件事务。 |
| `app/src/main/kotlin/com/tanuki/update/UpdateManager.kt` | 手动更新检查在 `versionGate` 内执行，避免与切换/卸载并发读写版本状态。 |
| `app/src/main/kotlin/com/tanuki/ui/DshVersionManagerDialog.kt` | 清单文案改为“当前使用/备用”，卸载确认显示占用、安装时间、保留范围；当前版本/未知当前版本禁用操作并说明原因。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 当前版本“卸载”改为“管理版本”；官方版本列表只允许卸载非当前已安装版本。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 卸载确认文案改为备用程序、保留数据、失败回滚、当前版本不可卸载。 |
| `app/src/test/kotlin/com/tanuki/DshUninstallTest.kt` | 新增 17 项：拒绝矩阵、备用卸载保留共享数据、删除依赖后标记仍在仍不回滚、部分删除保留隔离、提交日志失败回滚、回滚 move 失败保留 prepared、committed 恢复保留已重装 target、prepared 恢复、旧协议兼容、并发锁与并发卸载；卸载失败后重装另一版本，重启恢复保留当前版本；恢复失败不覆盖待处理日志；先恢复后写新日志。 |
| `app/src/test/kotlin/com/tanuki/DshVersionsTest.kt` | 当前版本未知时禁用卸载的策略回归。 |
| `app/src/test/kotlin/com/tanuki/DshSwitchTest.kt` | 更新检查/安装进行中拒绝切换。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew --no-daemon -x buildNativeRuntime :app:compileModernDebugKotlin :app:testModernDebugUnitTest --tests 'com.tanuki.DshUninstallTest' --tests 'com.tanuki.DshVersionsTest' --tests 'com.tanuki.DshSwitchTest'` | 通过（BUILD SUCCESSFUL） | 本 worktree，JDK17；36 tests / 0 failures / 0 errors / 0 skipped（卸载 17、版本 7、切换 12）。 |
| `./gradlew --no-daemon -x buildNativeRuntime :app:testModernDebugUnitTest :app:lintModernDebug` | 通过（BUILD SUCCESSFUL，3m30s） | 66 个结果文件、**552 tests / 0 failures / 0 errors / 0 skipped**（合并前）；合并 `origin/main=533eff4` 后 **575 tests / 0 failures / 0 errors / 0 skipped**（68 个结果文件）；`lintModernDebug` **0 errors / 95 warnings / 1 hint**；不替代设备验收。 |
| `./gradlew --no-daemon -x buildNativeRuntime :app:testModernDebugUnitTest --tests 'com.tanuki.NpmAdaptiveWorkerPoolTest' --rerun-tasks` | 通过 | 首次完整运行命中既有 `NpmAdaptiveWorkerPoolTest` 并发时序失败（退休 worker 数与目标不符），该测试单独重跑与最终完整 JVM 重跑均通过；本轮未改该测试。 |
| GitHub Actions 42756830044 / 42756838942 | 通过 | review 修复前 head `1fa66bd` 的 Host/Android 4/4 全绿；review 修复后的新 head CI 以 PR 页面为准。 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1142-dsh-version-uninstall.md` | 待本次收尾执行 | 普通 PR 模式只校验本记录，不写 `AGENT-STATE.md`/`CURRENT.json`。 |
| `python3 scripts/check-handoff.py --pr` | 待本次收尾执行 | 普通 PR 交接结构检查。 |

- 哪些是前一轮结果，哪些是本次新验证：review 修复后的 36 项目标 JVM、完整 552 项与合并后 594 项、lint 为本次新跑；review 修复前的 28/544 项是历史结果；合并前的 552 项也不再代表合并后 head，以 594 项为准。
- 未测试、超时、失败和暂未复现的情况：未跑 assemble、Android 仪器、真实设备；未在真实 userdata 上执行卸载；未验证进程在 `prepared`/`committed` 之间被杀后的 OEM 行为；review 修复后新 head 的 CI 待跑。

### 未完成与暂停项

- 未完成：第四轮修复后新 head 的 CI、独立 Codex High Review 复核、设备 UI 与真实卸载/回滚验收。
- 外部阻塞：无（等待 PR 流水线与审查安排）。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；本轮未触碰。

### 下一步

1. 推送第四轮修复后，逐条回复并 resolve 四条 thread；新 head CI 失败时按日志修复。
2. 等待独立 Codex High Review 复核新 head；不合并、不发布、不触发 AO Review。
3. 设备验收覆盖：清单显示当前/备用/占用/时间；当前版本无卸载入口；卸载备用后清单刷新且总占用下降；服务运行中/更新检查中按钮禁用或中文拒绝；删除中断后隔离目录与 committed 清理；确认会话、项目、快照与其他版本仍在。
4. 需要复核的恢复路径：prepared 中断 → 重启移回隔离目录；committed 中断 → 只清理隔离目录并保留已重装 target；安装/重装前先恢复待处理事务，不能覆盖日志；`recover()` 失败时保留中文错误与事务日志并进入 REPAIR。

### 约束与风险

- 数据/设备/权限边界：只删除 `engines/<version>` 与隔离目录；不执行 `pm clear`、不卸载 App、不操作设备；L2S 回收只在 mutation 与版本窄锁内、所有 guest 进程停止后运行。
- 关键决策及理由：组件事务日志改用 `AtomicFile.delete()`，否则只删主文件会留下 `.bak`，下次启动可能把已完成的卸载/替换事务重新恢复；卸载采用“先改名隔离、再提交、最后删除”，删除开始后一律不回滚（无法证明目录完整），隔离目录交给 `recover()` 清理；提交日志失败时只有回滚 move 成功才清日志，失败保留 prepared 记录；当前版本禁止所有后端卸载路径，主界面改用“重新安装/管理版本”。
- 失败尝试与避免重复踩坑的结论：测试临时目录没有 `runtime` 父目录，`renameTo` 失败；已在 `DshUninstall.apply` 创建隔离父目录并补回归。`UpdateManager.check` 直接持 `versionGate`，不持 mutation，避免与切换的 mutation → versionGate 顺序形成死锁。review 修复明确：标记文件不能证明目录完整，删除开始后不回滚；committed 阶段只允许清理隔离目录。
- 已知风险：`versionGate` 被手动检查持有时，切换/卸载会快速拒绝并提示稍后重试；UI 的 `tanukiIdle` 已把 `CHECKING` 计入忙碌，正常入口会先禁用。真实设备上 OEM 的 `renameTo`/删除权限行为仍需验证。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未生成 APK/ZIP；收尾按 AGENTS 要求运行 `package-source.py` 核对清单，源码包为工作材料，不提交。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未发布、未合并。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：未删除历史记录；`20261109-1539-dsh-installed-version-manager.md` 仍被多份并行交接引用并保留切换/清单实现依据，本轮结论保留在本记录；共享状态由集成维护方刷新。旧记录中的 151 项测试为历史结果，不代表当前 head。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。

<a id="20261110-1142-file-viewer-formats"></a>

## 20261110-1142-file-viewer-formats

- Agent/会话标识：android_agent-41（DSH Worker）
- 时间：2026-11-10 11:42 CST

### 当前状态

- 已实现：集中式类型目录、文本查看器行号/截断、未知类型回退、MIME 规范化与内容探测边界。
- 已验证：`compileModernDebugKotlin` BUILD SUCCESSFUL；定向 JVM 回归 29 项通过（`FileTypeCatalogTest` 11、`TextPageLayoutTest` 9、`TextFileReaderTest` 6、`WorkspaceFileKindTest` 3）；完整 `testModernDebugUnitTest` 558 tests / 0 failures / 0 errors / 0 skipped（67 个结果文件），`lintModernDebug` 0 errors / 95 warnings / 1 hint，最终代码上组合 BUILD SUCCESSFUL（2m49s）。
- 待验证：真机/模拟器上的行号对齐、自动换行、深色主题、大文件截断提示、未知类型面板与复制路径；Android 仪器测试。
- 当前目标包名：未构建/安装新 APK；源码分支 `ao/file-viewer/formats`，基线 `0555c3c0eaeeed181269b5e68733ccfdd48d9e75`，PR [#34](https://github.com/leezzou/tanuki/pull/34)；无运行中安装/下载/设备任务。
- 关键事实与可定位源码：分类规则在 `app/src/main/kotlin/com/tanuki/storage/FileTypeCatalog.kt`；工作区接入在 `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt`；行号/截断策略在 `app/src/main/kotlin/com/tanuki/ui/TextPageLayout.kt`；界面在 `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/FileTypeCatalog.kt` | 新增：扩展名 → 文件类别/规范 MIME 的集中映射；文本扩展名优先于系统 MIME（修复 `.ts`/`.mts` 与 MPEG-TS 冲突，TypeScript/TSX 一律按文本）；点号配置只匹配已知文件名与 `.env`/`.env.*`，`.DS_Store` 等隐藏二进制归 OTHER，媒体扩展名优先于文件名规则；覆盖 C/C++/头文件、JVM/Python/JS/TS/Go/Rust/Shell、配置/标记/数据、HTML/CSS/SQL、图片/音频/视频、PDF/Office、归档/二进制；明确二进制集合避免内容探测误判。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt` | `classifyFileKind` 委托 `FileTypeCatalog`；`describe` 使用项目内规范 MIME，未知类型（非明确二进制）做有界 UTF-8 内容探测；`fileMime` 统一使用目录 MIME 并给无扩展名文本回退 `text/plain`。 |
| `app/src/main/kotlin/com/tanuki/ui/TextPageLayout.kt` | 新增：行号排版（行号与正文同一段文本，自动换行不错位）、原始/带行号下标换算（批量线性推进，避免多匹配 O(n×m)）、跨段行号延续、64 KiB 分页之上的 4 MiB 显示上限与截断判定。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | 文本查看器增加可开关行号、当前行范围与已显示字节、截断提示；未知/不支持类型改为中文说明面板，提供“用文本打开/复制路径”和返回类型说明；信息缺失时给兜底提示；切换分段时清空旧文本。 |
| `app/src/test/kotlin/com/tanuki/FileTypeCatalogTest.kt` | 新增 11 项：C/C++ 家族、`.ts`/`.mts` 冲突、常见语言、配置/标记/数据、图片/音频/视频、PDF/Office、归档/二进制、无扩展名/已知点号配置、隐藏二进制与 `.env` 前缀媒体、大小写/复合扩展名、MIME 回退。 |
| `app/src/test/kotlin/com/tanuki/TextPageLayoutTest.kt` | 新增 9 项：行号宽度/空文本/末尾换行、关闭行号、搜索下标换算、跨段行号、4 MiB 分页上限、截断判定、行数统计。 |
| `app/src/test/kotlin/com/tanuki/TextFileReaderTest.kt` | 新增 3 项：64 KiB 页边界与空文件、跨页多字节字符不拆分、UTF-16LE 不完整字符报错/ISO-8859-1/未知编码拒绝。 |
| `docs/WORKSPACE-STORAGE.md` | 更新文本查看器能力、4 MiB 截断与支持类型/回退说明。 |
| `docs/AGENT-QUICKSTART.md` | 模块地图补充 `FileTypeCatalog.kt`、`TextPageLayout.kt`。 |
| `docs/TEST-REPORT.md` | 新增本次非设备验证记录。 |
| `docs/AGENT-STATE.md` | 新增加本次交付状态与交接链接。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin` | BUILD SUCCESSFUL（1m05s，增量） | 仅主源码编译，未安装设备。 |
| 同上 `:app:testModernDebugUnitTest --tests 'com.tanuki.FileTypeCatalogTest' --tests 'com.tanuki.TextPageLayoutTest' --tests 'com.tanuki.TextFileReaderTest' --tests 'com.tanuki.WorkspaceFileKindTest'` | BUILD SUCCESSFUL（2m05s），28 tests / 0 failures / 0 errors / 0 skipped | 首次运行 2 项断言写错（`displayOffset(0)` 应含行号前缀、无末尾换行的下一段行号），已修正测试并复跑通过。 |
| `source scripts/ao-worktree-env.sh && ./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:testModernDebugUnitTest :app:lintModernDebug` | BUILD SUCCESSFUL（最终代码 2m49s），558 tests / 0 failures / 0 errors / 0 skipped（67 个结果文件）；lint 0 errors / 95 warnings / 1 hint | 新增 23 项（535 → 558），与 0.1.10 基线一致；优化批量下标换算、收紧点号配置后均重跑，结果不变；未跑 assemble/Android 仪器/真机。 |

- Review P2 修复（thread `PRRT_kwDOVgRm4s61jwII`）：点号配置仅匹配已知文件名与 `.env`/`.env.*`；`.DS_Store` 归隐藏二进制并跳过内容探测，`.env-image.png` 按媒体扩展名优先识别；新增 1 项回归，完整 JVM 558 tests。
- 哪些是前一轮结果，哪些是本次新验证：上述编译与定向测试均为本轮新跑；0.1.10 的 535 JVM、发布 APK 与设备事实均为前轮记录，本轮未复跑，不作为本次结论。
- 未测试、超时、失败和暂未复现的情况：未在 Android 设备上打开真实 `.c`/`.cpp` 文件复现用户报告；行号在自动换行、超大字号、深色主题下的像素对齐需要设备/截图验证；4 MiB 截断在真实大日志上的滚动与提示需要设备验证；Android 仪器测试未运行。

### 未完成与暂停项

- 未完成：独立 Codex High Review（合并前要求）、真机/模拟器验收、Android 仪器测试。
- 外部阻塞：无。
- 用户暂停：API Key 保存问题继续暂停；发布/合并需另行授权。

### 下一步

1. 独立 Codex High Review 绑定 PR #34 当前完整 SHA；推送新提交后需重新审查。
2. 设备验证建议：在 ARM64 测试 AVD 打开合成 `.c`/`.cpp`/`.ts`/`.json`/大日志/未知扩展名文件，检查行号、自动换行、复制、截断提示、深色主题与回退按钮；再补 Android 仪器测试。
3. 若用户设备上的 `.c`/`.cpp` 仍无法打开，收集文件路径、大小、前几 KB 的字节/编码与 `describe` 显示的类型，再定位是否为特定编码或文件变化检查。

### 约束与风险

- 数据/设备/权限边界：未连接设备、未安装 APK、未读取真实用户数据；测试文件为临时合成文本。工作区读写权限、DSH 运行检查、256 KiB 编辑器上限和既有安全策略均未改变。
- 关键决策及理由：分类目录放在 `storage` 纯 Kotlin 层，避免依赖 Android `MimeTypeMap` 的不稳定映射，并让扩展名优先于 MIME；行号与正文放在同一段文本，自动换行时行号不会错位，搜索仍以原始文本下标计算；“复制本段”复制原始文本，不含行号；4 MiB 上限沿用 64 KiB 分页，避免一次性载入大日志。
- 失败尝试与避免重复踩坑的结论：不能只依赖 `MimeTypeMap`（`.ts` 在 Android 上可能映射为 `video/mp2t`），也不能只按 MIME 判断媒体，否则缺 MIME 时 PNG/MP4 会落入未知类型；明确二进制/归档/旧版 Office 不参与内容探测；测试断言必须按“带行号后的显示下标”计算。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未构建 APK；`scripts/package-source.py` 生成的源码包与 `artifacts/SOURCE-SHA256SUMS` 为工作材料，不提交；PR [#34](https://github.com/leezzou/tanuki/pull/34) 以分支 `ao/file-viewer/formats` 交付，最终提交 `60889eb`（文档回填后会有新的提交，head 以 PR 页面为准）。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未覆盖、未签名、未发布、未建 tag/Release。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮保留全部既有 `docs/handoffs/` 记录。它们仍分别承载未合并 PR、待验证设备项与历史发布证据，删除会丢失唯一定位信息，且不属于本次任务范围；本次只新增记录并更新 `AGENT-STATE` 最近链接。后续若确认某记录已被 `AGENT-STATE`/专题文档完整替代，再按 AGENTS.md 迁移后删除。
- 仍需保留的验证/未完成记录：`docs/TEST-REPORT.md` 的 0.1.10 与设备相关历史记录、各 PR 交接记录。

<a id="20261110-1142-input-attachments"></a>

## 20261110-1142-input-attachments

- Agent/会话标识：android_agent-46
- 时间：2026-11-10 11:42 CST

### 当前状态

- 已实现：原生输入栏组件（多行、`Ctrl/⌘+Enter` 发送、按会话草稿）、@ 文件/会话补全、斜杠命令目录/补全/执行、图片/文件选择与预览、普通文件上传换 receipt、图片 base64 随 prompt、粘贴图片、拖入文件、大小/类型限制中文错误；5 项评审修复。
- 已验证：`compileModernDebugKotlin` BUILD SUCCESSFUL；目标回归 41 项全过；完整 JVM 684 tests / 0 failures / 0 errors / 0 skipped（78 个结果文件，含 main 既有回归）；`lintModernDebug` 0 errors / 98 warnings / 1 hint，新增文件无 lint 告警。
- 待验证：42 接线后的真实 Android 设备/模拟器输入栏布局、软/硬件键盘、图片/文件选择、缩略图/大图预览、粘贴/拖入、跨应用临时权限、上传失败重试；真实 PRoot DSH；assemble、Android 仪器、真机。
- 当前目标包名、分支/无Git状态、是否有运行中任务：`com.tanuki`（modern flavor 为 `com.tanuki.modern`）；分支 `ao/input-attachments/components`，已合入 `origin/main=bc802cfddbc7cc37a49a79cf9e31a104cb8cfc70`；PR #43 已创建；无设备/模拟器任务。
- 关键事实与可定位源码/公共文档：官方 `@deepseek-ai/dsh@0.2.1-rc.5` 的 `session/prompt`、`fileUploads/upload`、`commands/list|execute`、`fileReferences/list`、`sessionReferenceResolver/candidates` 参数结构；实现见 6 个组件文件；契约见 `docs/INTERFACES.md` 1.9.1。

#### Codex Review 修复（5 项）

- P1 发送成功误清新草稿：发送前快照 `submittedText` 与附件 id；成功时仅移除本次提交的文本/附件，发送期间的新文本、新附件保留并回写草稿；延迟响应回归 `sendKeepsEditsMadeWhileWaitingForResponse`。
- P1 异步附件串会话：新增 `DshComposerSessionToken(sessionId, generation)`；选择器打开、粘贴、拖入读取前捕获，返回时 `isCurrentSession` 校验；`addImages/addFiles` 也接受 token 做二次拒绝；回归 `staleAttachmentTokenIsRejectedAfterSessionSwitch`。
- P2 批量读取预算：新增 `DshAttachmentReadBudget`，`readUris`/剪贴板/拖入在读取每个 URI 前按剩余数量、剩余总量和单文件上限分配；不再先把整批字节读进内存；回归 `batchBudgetStopsBeforeOverAllocating`。
- P2 混合拖入名额：总附件名额 `maxItems` 与类型名额 `typeMaxItems` 分开；`afterImages()` 只扣总名额/共享字节，不扣文件名额；混合拖入回归 `mixedDropKeepsFileTypeQuotaAfterImages`。
- main 兼容：删除组件内重复的 `DshCommand`，复用 main `DshSessionAdminApi.kt` 的 `DshCommand`，字段改为 `acceptsAttachments`；修复合并最新 main 后的重复声明/字段编译冲突。
- P2 选择器恢复：`applyRead` 与选择器回调在 token 为 null 时直接丢弃并提示；`addImages/addFiles` 改为强制非空 token，新增 `canApplyAttachmentResult(null)==false` 回归 `restoredPickerResultWithoutTokenIsRejected`。
- P2 静默丢弃：`DshAttachmentReadBudget.blockedMessage()` 给出中文名额/总量提示，`readDrop` 文件批次被预算阻断时返回错误而不是空结果；回归 `blockedFileBudgetReturnsChineseMessage`。
- P2 拖入开始判定：`shouldStartDragAndDrop` 改用 `event.mimeTypes()`，不再依赖 ACTION_DROP 才提供的 `clipData`；纯策略 `DshComposerDragPolicy.accepts` 有回归。
- P2 跨应用临时权限：`onDrop` 调 `Activity.requestDragAndDropPermissions`，权限保留到异步读取结束并在 `finally` release；真实外部 provider 读取仍需设备验证。

#### transport 收敛

- 当前分支已删除早期 head 的临时 `HttpDshComposerRemote`；它曾使用 `POST /api/<method>`、connect 5s/read 30s、401 单次重试、2 MiB 响应上限和 `DshApiException(code)` 错误映射。该实现不再存在，仅保留历史说明。
- 现在只依赖 `DshComposerRemote`：`suspend fun call(method: String, args: JSONObject): Any?`（返回官方 `result.value`，业务错误抛 `DshApiException`，认证/网络 IOException）与 `fun reset()`。
- `DshComposerRemote` 是**临时抽象**：只覆盖输入/附件需要的 unary `call`/`reset`，不代表第二套传输。42 的 `DshTransport` / `DshNativeComposerRemote(transport)` 定稿后，消费方应改为直接依赖该适配器并删除/收敛这个临时接口。
- 输入栏不使用 `live`/`connection`，不读 token/Cookie、不拼 URL、不自建 HTTP/WS。

#### 真实可用性

- 发送、附件选择/上传、@ 引用、斜杠命令、草稿保留：控制器/UI/协议已实现并有 JVM 覆盖；**本组件 PR 不挂载 `DshNativePage`**，不能宣称端到端在原生页可用。
- 停止/取消：**未实现**；输入栏没有“停止生成立即中断”或“附件上传取消”，`sendJob`/`uploadJob` 只在 `setSession`/`close` 取消。
- 图片/文件选择、缩略图/大图预览、粘贴、拖入、临时权限：Android 代码已实现，未在设备验证。
- #40 负责页面接线；端到端可用性以 #40 接线与设备 QA 为准。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshComposerProtocol.kt` | 官方 prompt/upload/commands/文件引用/会话引用参数构造与返回解析；图片嗅探/尺寸、@/斜杠语法、附件限制、批量预算、拖入 MIME 策略 |
| `app/src/main/kotlin/com/tanuki/web/DshComposerClient.kt` | 输入/附件 Remote 客户端；只依赖 `DshComposerRemote`（`call` + `reset`），不自建 HTTP/WS；base64 在后台线程 |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerController.kt` | 按会话草稿、补全候选、附件校验/串行上传、命令/提示词发送、失败保留草稿；发送快照与会话代次 token |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerBar.kt` | 多行输入、@/斜杠菜单、附件卡片/缩略图/大图预览、图片/文件选择、粘贴、Compose 拖入、发送快捷键；临时权限与 token 绑定 |
| `app/src/main/kotlin/com/tanuki/ui/DshAttachmentReader.kt` | 文档 URI、剪贴板图片、拖入 ClipData 读取；读取前预算、大小上限与中文错误 |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerDraftStore.kt` | 应用私有 `dsh-composer` 按会话文本草稿存储与内存测试实现 |
| `app/src/test/kotlin/com/tanuki/DshComposerProtocolTest.kt` | 协议/策略回归 17 项 |
| `app/src/test/kotlin/com/tanuki/DshComposerClientTest.kt` | 客户端协议回归 8 项 |
| `app/src/test/kotlin/com/tanuki/DshComposerControllerTest.kt` | 控制器状态机回归 13 项 |
| `docs/INTERFACES.md` | 新增 1.9.1 组件契约、方法参数、限制、临时 transport 抽象与停止/取消边界 |
| `docs/handoffs/20261110-1142-input-attachments.md` | 本记录 |

明确未改：`DshNativeApi.kt`、`DshNativeController.kt`、`DshNativePage.kt`、`MainActivity.kt`（42 独占）；`TanukiUi.kt`/`DshUiMode.kt`（44）；`DshChatProtocol.kt`（42）；共享状态 `AGENT-STATE`/`NATIVE-UI`/`TEST-REPORT`/`CURRENT.json` 不在本组件 PR。

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin -x buildNativeRuntime` | BUILD SUCCESSFUL；仅既有弃用/平台告警 | 本 worktree；未跑原生 `buildNativeRuntime` |
| `./gradlew ... :app:testModernDebugUnitTest -x buildNativeRuntime --tests 'com.tanuki.DshComposerProtocolTest' --tests 'com.tanuki.DshComposerClientTest' --tests 'com.tanuki.DshComposerControllerTest'` | BUILD SUCCESSFUL；**41 tests / 0 failures / 0 errors / 0 skipped** | 19 + 8 + 14；不含 transport 认证/HTTP |
| `./gradlew ... :app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL；78 个结果文件、**684 tests / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 98 warnings / 1 hint** | 含 main 既有回归；新增文件无 lint 告警 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1142-input-attachments.md` | Handoff PR check passed | 普通 PR 模式；`AGENT-STATE`/`CURRENT.json` 与 main 完全一致，未修改 |
| `git diff --check` | 无输出 | 无空白错误 |

- 哪些是前一轮结果，哪些是本次新验证：684/0/0/0、lint 0 errors/98 warnings/1 hint、41 项目标回归均为本组件分支新跑；main 既有回归不是本切片新增。
- 未测试、超时、失败和暂未复现的情况：真实设备 UI/键盘/选择器/粘贴/拖入/跨应用临时权限/上传失败重试、真实 PRoot DSH、assemble、Android 仪器、真机均未执行；`DshComposerBar` 的 Compose 交互没有 JVM 自动化覆盖；`DshAttachmentReader` 的 Android URI/剪贴板路径没有仪器测试；transport 认证/HTTP 由 42 统一实现，本分支未测。

### 未完成与暂停项

- 未完成：创建组件 PR；#40 接线；独立 Codex High Review；适用 Android QA。
- 外部阻塞：无；本组件 PR 作为 #40 的依赖，不合并 main、不重开 #39。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收按项目既有暂停项保留；本次未触碰。

### 下一步

1. 组件 PR #43 已创建；本次推送混合名额修复与交接更新，之后等待评审。
2. 由编排器先合 #42（交接机制），再合本组件 PR，最后 #40 rebase 只保留接线。
3. 若 #40 评审指出 composer 问题，由编排器转交后通过 42 分支或另开小 PR 修复。

### 约束与风险

- 数据/设备/权限边界：只读用户选择的 `content://` 或受限 `data:image`；不读取 `file://`、App 私有文件或外部明文路径；不写真实 token/Cookie 到日志/状态/偏好；未启动设备、未安装、未跑仪器。
- 关键决策及理由：按编排器要求做组件-only PR，避免共享状态冲突；不修改 42 独占文件；不自建 HTTP/WS，保留 `DshComposerRemote` 临时抽象；附件不跨会话保留，只持久化文本草稿。
- 失败尝试与避免重复踩坑的结论：官方图片上限可能小于客户端 64 KiB 下限，测试按 effective 值断言；跨行 `@` 在第二行行首仍识别；`session/prompt` 的 connect URL 必须保留 token，安全要求是 token 不进请求体/错误/日志；斜杠命令目录未加载时不能把 `/name` 当普通提示词发送。
- 风险：真实 DSH 缺少 `commands/list`/`fileReferences/list`/`sessionReferenceResolver` 时补全为空但普通发送可用；真实服务端附件上限/错误码可能与默认不同；图片 base64 与 50 MiB 总量在低内存设备仍有 OOM 风险，已用 20/30/50 MiB 与串行上传收敛。

### 产物与校验

- 新/旧APK、源码ZIP及校验文件：本轮未生成 APK、源码 ZIP 或发布附件；只改源码/测试/文档。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布、未合并。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本组件 PR 只新增本 handoff；不包含 `AGENT-STATE`/`NATIVE-UI`/`TEST-REPORT`/`CURRENT.json`，共享状态由集成阶段统一刷新。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码ZIP；普通 PR 运行 `python3 scripts/check-handoff.py --pr --record docs/handoffs/<本次记录>.md`，不修改 `AGENT-STATE.md`/`CURRENT.json`；提交前运行 `git diff --check` 与 `check-publication.py --git`，如需源码包再运行 `package-source.py`。

<a id="20261110-1226-native-parity-non-chat"></a>

## 20261110-1226-native-parity-non-chat

- Agent/会话标识：android_agent-44
- 时间：2026-11-10 12:26 CST（2026-11-10 16:36 发布集成时按合并后事实修正）

### 功能清单与文件边界

#### 已实现

| 域 | 交付 |
|---|---|
| 会话管理 | 官方 `session/list` 列表、标题/ID 过滤、官方 `session/search` 内容搜索、`session/create` 新建、`session/rename` 重命名、`workspace/archiveSession` 归档、运行状态、本机未读 |
| 模型/权限 | `session/modelCatalog`/`selectModel` 会话级模型与推理强度；`permissionPresets/catalog` + 官方 `/permission` 命令切换；完全权限必须二次确认 |
| Skills/命令 | `skills/list` 与 `commands/list` 只读目录；显示模型可用/仅人工、输入提示、附件能力 |
| 兼容 | 安全模式/试用模式只读；远程 SSH 隧道 origin 复用；端点切换清理旧目录并使在途响应失效；停止时丢弃内存 Cookie；通知点击路由不变 |

#### 未实现/协议不可用

- 会话硬删除：官方 `0.2.1-rc.5` 无端点，未伪造。
- 归档恢复列表：需要 `workspace/follow` WebSocket，后续。
- 未读：本机按最后查看 `updatedAt` 计算，不是官方字段。
- 工作区：文件树/预览/当前目录搜索复用现有查看器；全局搜索与 diff 后续。
- 插件列表/启用、斜杠命令执行、应用内语言/主题：后续。
- 真机/PRoot、Android 仪器未验证。

#### 文件边界

- 新增：`app/src/main/kotlin/com/tanuki/web/DshSessionAdminApi.kt`、`ui/DshSessionManagerController.kt`、`ui/DshSessionManagerPage.kt`、`ui/DshSessionManagerActivity.kt`、两个 JVM 测试类。
- 共享加法（已由编排器放行，本次不再改）：`AndroidManifest.xml` 新增 Activity 声明；`TanukiUi.kt` 新增 `openSessionManager` 回调与设置入口；`MainActivity.kt` 仅 import + 一个启动回调。
- 未修改：`DshNativeApi.kt`、`DshNativeController.kt`、`DshNativePage.kt`、聊天 WebSocket 文件、文件查看器文件。
- 本次按新机制不修改 `docs/AGENT-STATE.md`、`docs/TEST-REPORT.md`、`docs/handoffs/CURRENT.json`、`docs/INTERFACES.md`、`docs/NATIVE-UI.md`，这些文件取 main 版本；原专题文档已移除，功能清单/边界/契约保留在本记录。

#### transport 契约

- 会话管理只依赖薄接口：
  ```kotlin
  interface DshAdminTransport {
      suspend fun call(method: String, args: JSONObject): Any? // result.value；业务错误抛 DshApiException
      fun reset()
  }
  ```
- `DshSessionAdminClient` 只做 method/参数、解析和中文错误；当前 `DshHttpAdminTransport` 是临时 HTTP 栈。
- 已提供 `DshAdminTransportAdapter(callImpl, resetImpl)`。42 的 PR #40 `DshTransport`（`connection`、`call`、`live`、`reset`）合并后改为 `DshAdminTransportAdapter(transport::call, transport::reset)`；本切片不使用 `live`。

### 当前状态

- 已实现：会话管理完整切片、三项 Codex P2 修复、transport 薄接口与适配器。
- 已验证：完整 JVM **74 个结果文件、626 tests / 0 failures / 0 errors / 0 skipped**；`lintModernDebug` **0 errors / 97 warnings / 1 hint**；`assembleModernDebug` BUILD SUCCESSFUL；merge head `cf0fb76` 的 Host + Android CI 全绿。
- 待验证：真实 Android/PRoot DSH 的 `/connect`、`session/create|rename|search`、`workspace/archiveSession`、模型/权限命令；设备 UI/深色/大字体；远程 SSH 端点；Android 仪器测试。
- 当前分支：PR #38 已合并进 main（merge `bc802cf`，head `cf0fb76`）；本记录随发布集成落地；#40 合并后替换临时 transport。
- 关键事实：官方 `0.2.1-rc.5` 无会话硬删除端点；归档用 `workspace/archiveSession`；归档恢复需要 `workspace/follow` 流；未读是本机 `updatedAt` 比较。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshSessionAdminApi.kt` | 官方 method/参数、响应解析、`DshSessionAdminSource`；`DshAdminTransport` 薄接口、`DshAdminTransportAdapter`、临时 `DshHttpAdminTransport` |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerController.kt` | 列表/过滤/搜索/新建/重命名/归档/未读/模型/权限/Skills/命令状态机；P2：取消请求复位 loading、写操作清列表 loading、端点切换清旧目录并失效在途请求 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerPage.kt` | 原生会话管理页、详情面板、模型/权限/目录面板、新建/重命名/归档/完全权限确认 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerActivity.kt` | 独立入口 Activity；origin 纳入 key；本地/远程/安全模式判断；退出丢弃内存 Cookie |
| `app/src/main/AndroidManifest.xml` | 声明 `DshSessionManagerActivity`，`exported=false` |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 回调增加 `openSessionManager`；设置 → 网页与兼容性增加入口 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 仅 import + 一个启动回调，已放行；本次未再修改 |
| `app/src/test/kotlin/com/tanuki/DshSessionAdminApiTest.kt` | 8 项协议/端点/错误/Cookie/transport 适配回归 |
| `app/src/test/kotlin/com/tanuki/DshSessionManagerControllerTest.kt` | 18 项状态机/过滤/未读/写操作/安全模式/权限确认/loading 恢复/端点切换回归 |
| `docs/handoffs/20261110-1226-native-parity-non-chat.md` | 本记录；普通 PR 唯一文档改动 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin -x buildNativeRuntime` | BUILD SUCCESSFUL | JDK17 项目缓存、macOS ARM64；不代表设备 |
| `./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime` | BUILD SUCCESSFUL，74 个结果文件、626 tests / 0 failures / 0 errors / 0 skipped | 合入 41/40/44/48 代码后；无设备/仪器 |
| `./gradlew :app:lintModernDebug :app:assembleModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL，0 errors / 97 warnings / 1 hint；打包成功 | 未安装设备、未签名发布 |
| PR #38 merge head `cf0fb76` Host / Android CI | 均 pass | 最终合并 head；设备/仪器未跑 |
| `python3 scripts/check-native-parity.py` | PASS：166 行、14 域 | 审计矩阵由属主维护，44 不写矩阵 |
| 一次 `--rerun-tasks` 全量组合 | `NpmAdaptiveWorkerPoolTest` 并发 flake 1 次；单独重跑与完整重跑均通过 | 未改该测试 |

- 哪些是前一轮结果，哪些是本次新验证：626 JVM、lint、assemble、P2 回归均为合并前本轮新跑；535/556 等为历史结果，未冒充 0.1.11 候选复跑。
- 未测试、超时、失败和暂未复现的情况：真实 Android/PRoot DSH、真实 `/connect` Cookie 生命周期、`commands/execute` 的 `/permission` 实际结果、`workspace/archiveSession` 运行中停止语义、设备 UI、远程 SSH、Android 仪器。

### 未完成与暂停项

- 未完成：真实设备/PRoot 验收；工作区全局搜索/diff；插件列表；斜杠命令执行；应用内语言/主题；#40 合并后 transport 适配器切换。
- 外部依赖：PR #40 `DshTransport` 已合并（`1f7222d`）；临时 `DshHttpAdminTransport` 仍在，替换为 `DshAdminTransportAdapter` 属后续清理；归档列表恢复依赖 workspace/follow WebSocket。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收按原状态保留。

### 下一步

1. 发布集成应用本记录。
2. #40 合并后把临时 `DshHttpAdminTransport` 替换为 `DshAdminTransportAdapter(transport::call, transport::reset)`，不重写会话管理逻辑。
3. 设备 QA 授权后验证真实 PRoot DSH 的 `/connect`、会话管理、归档/权限与页面布局。
4. 后续切片：工作区全局搜索/diff、插件列表与启用、斜杠命令执行、应用内主题/语言、归档列表恢复（需 workspace/follow WebSocket）。

### 约束与风险

- 数据/设备/权限边界：不读真实凭据；API Cookie 只在内存；不写 `userdata/dsh`；不删会话/项目；不合并/发布；不关闭审批/TLS/校验；不自动切 `danger-full-access`。
- 关键决策及理由：42 独占 `DshNativeApi.kt`/`DshNativeController.kt`/`DshNativePage.kt`/`MainActivity.kt`，本切片新增独立 API/Controller/Page/Activity，避免同文件并发写。完全权限需界面二次确认与控制器显式参数。
- MainActivity 归 42 独占：本 PR 只保留已放行的两行；若 42 重写该处，需要保留的 hook 是 `TanukiUiCallbacks.openSessionManager: () -> Unit`（设置页入口）与 Manifest 中 `DshSessionManagerActivity` 声明。
- transport：`DshHttpAdminTransport` 是临时实现；切换前不得出现两套不同的 origin/Cookie 规则，也不复制认证/Cookie/URL。`DshAdminTransport` 不使用 `live`。
- 已知风险：独立 Activity 与原生聊天页暂时是两个入口；官方 `session/search` 可能返回已归档会话，打开时会提示目标不在当前列表；远程 DSH 是否为安全模式无法从本地状态得知，写入失败由官方错误提示。
- 失败尝试与避免重复踩坑的结论：Android JVM 单测不能使用 JDK `com.sun.net.httpserver`，使用原始 `ServerSocket`；含中文请求体必须按 ISO-8859-1 逐字节读取，否则 `Content-Length` 与字符数错位导致超时。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本切片不生成发布 APK、不更新 `artifacts/releases/`；`assembleModernDebug` 仅用于验证，APK 在 `app/build/outputs/`，不作为交付包。源码 ZIP 由 `package-source.py` 生成，属工作材料，不提交。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装设备、未覆盖安装、未签名发布、未创建 tag/Release。
- 提交/推送前清理的过期记录：本轮未删除历史交接；共享状态与印章按新机制由集成维护方统一刷新，本 PR 不修改。
- 仍需保留的验证/未完成记录：本记录；共享状态、测试汇总与印章由集成维护方统一刷新。

<a id="20261110-1233-native-ui-chat"></a>

## 20261110-1233-native-ui-chat

- Agent/会话标识：android_agent-42（原生聊天集成 Worker）
- 时间：2026-11-10；同一 PR 的范围升级与 Review 修复持续记录于本文件。

### 当前状态

- 已实现：官方会话/历史、follow 快照与增量、assistant text-delta、发送/停止/错误/重发、Markdown/代码复制/基础表格/图片占位、composer 页面接线、审批面板与工具卡接线。
- 已实现：`DshTransport` 的 connection/call/live/reset 与 `DshNativeComposerRemote`；输入共用聊天 transport，审批暂用 #41 独立 answer Peer。
- 已修：取消收集立即关闭底层 socket；发送结果绑定发起会话代次；#43 的附件 token/发送快照/批量预算等修复直接沿用 main。
- 已修：聊天 Peer 不展示或裁决审批，waterfall 回 next；审批 Peer 是唯一 UI/裁决入口。历史页工具按 callId 合并，旧 RUNNING 不覆盖 SUCCESS/FAILED，也不删除实时卡片。
- 本轮实现：next 请求绑定事件连接的 Cookie/clientId，禁止 401 换 Peer 重试；HTTP/业务错误或缺少 ready 时关闭聊天 mux 后提示，清理旧 Cookie；取消中止 HTTP 并传播。
- 前轮验证：完整 JVM 786 tests / 0 failures / 0 errors / 0 skipped，lint 0 errors / 101 warnings / 1 hint，前轮 PR/push CI 全绿。该数字不作为本轮新代码的验证结果。
- 本轮验证：定向 29 tests / 0 failures / 0 errors / 0 skipped（`DshNativeLiveTest` 10、`DshApprovalRoutingTest` 3、`DshNativeControllerTest` 16）；完整 JVM 794 tests / 0 failures / 0 errors / 0 skipped（90 结果文件），lint 0 errors / 101 warnings / 1 hint；BUILD SUCCESSFUL（1m54s）。
- 待验证：真实 Android/PRoot DSH、审批 waterfall、输入法/附件、通知、远程、安全模式；assemble/仪器/真机。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `web/DshNativeApi.kt` | 官方会话/历史/发送/取消/live/transport；本轮 next 固定 Peer 身份、可取消 HTTP、失败释放 mux 并提示。 |
| `web/DshLive.kt`、`web/DshMuxProtocol.kt`、`web/DshTransport.kt`、`web/DshWebSocket.kt` | 聊天/工具流事件、mux 编解码、transport 适配与可取消 WebSocket。 |
| `ui/DshNativeController.kt` | 会话代次、消息/工具合并、停止/重发；`9df3153` 修迟到历史回退工具终态。 |
| `ui/DshNativePage.kt`、`MainActivity.kt` | 挂载 main 的 composer/审批面板/工具卡；生命周期与会话同步，保留会话管理和外部文档入口。 |
| `ui/DshMarkdown.kt` | Markdown/围栏代码/基础表格/图片占位。 |
| `DshNativeLiveTest.kt` | next 成功、HTTP/认证/业务失败、缺少 ready、取消阻塞 HTTP 的协议回归。 |
| `DshApprovalRoutingTest.kt` | 真实 loopback 双 Peer、不同就绪顺序与重连的审批路由回归。 |
| `DshNativeControllerTest.kt` | 发送 A→B、延迟历史页返回时保留工具终态；其他聊天回归保留。 |
| `DshMuxWebSocketTest.kt`、`DshMarkdownTest.kt`、`DshMuxProtocolTest.kt`、`DshNativeApiTest.kt` | 聊天 WebSocket 空闲取消/协议/渲染/transport 回归；main 的 `DshWebSocketTest` 审批用例保留。 |
| `docs/NATIVE-UI.md`、`docs/INTERFACES.md`、`docs/TEST-REPORT.md`、`docs/NATIVE-PARITY.md` | 当前协议、失败恢复、测试事实与功能差距；本记录是普通 PR 唯一交接记录。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| 前轮完整 `:app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | 786/0/0/0；lint 0 errors/101 warnings/1 hint | head `9df3153`，仅前轮结果。 |
| 本轮定向 `DshNativeLiveTest`、`DshApprovalRoutingTest`、`DshNativeControllerTest` | 通过，29/0/0/0 | 真实 loopback 双 Peer、next 失败/取消、迟到工具历史；合成 Host 不读真实会话/凭据。 |
| 本轮 `:app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | 通过，794/0/0/0；lint 0 errors/101 warnings/1 hint | 90 结果文件、BUILD SUCCESSFUL（1m54s），无设备/真实 DSH。 |
| composer 9 文件与共享状态对比 main | 无 diff | 本轮不修改组件内部与共享状态。 |
| `check-handoff.py --pr --record ... --changed-from ...`、`check-publication.py --git` | 通过；671 public files / 0 findings | 公开候选与明确暂存检查，未修改共享印章。 |
| `package-source.py` 与外部 SHA-256 清单核对 | 通过 | 只生成源码候选包；未生成/分发原生二进制，最终 SHA 取外部清单。 |
| 设备/真机/仪器/assemble | 未执行 | JVM/lint 不能替代设备验收。 |

- 前轮一次 `NpmAdaptiveWorkerPoolTest` 8/9 时序抖动，定向及全量重跑通过。
- 本轮定向首轮编译发现新增路由测试的嵌套夹具访问外层辅助方法，已将辅助方法移入 companion；随后三类定向测试通过。

### 未完成与暂停项

- 未完成：当前完整 SHA 的独立 Review、设备验收仍由编排器安排；代码/测试已准备完成，交付检查、单次提交推送及线程回复按下列步骤收尾，结果以 PR 当前 SHA 为准。
- 后续功能以 [完整矩阵](NATIVE-PARITY.md) 为准；历史向前分页、模型/推理、完整用户提问、链接/授权图片、自动重连仍待后续。
- 外部阻塞：无；当前 SHA 的独立 Review 与设备验收仍待安排。
- 用户暂停：API Key 保存、真实模型/账号与真机验收。

### 下一步

1. 定向与完整 JVM/lint 已通过；提交前完成 `check-handoff.py --pr --record`、`check-publication.py --git`、`git diff --check` 与源码候选包核验，确认 composer/共享状态零 diff。
2. 用 leezzou 一次提交推送；回复/Resolve `nBWq` 与重复 `nBWj` 线程，收集当前 SHA 的 PR/push CI 并回报，不代合、不发布。
3. 后续当前 SHA 的独立 Review、设备/PRoot 验收由编排器与独立 QA 安排。

### 约束与风险

- 不读取真实 Key/Cookie/token/会话，不修改 DSH 数据/官方 npm 包；测试只用合成 token、Cookie、事件与内容。
- next 在原 Peer 上一次请求；任何未确认接受的结果都不视为成功。不自动重试未知提交，不自动批准，失败释放连接并显示手动重连/官方入口。
- 审批与聊天暂为两个 answer Peer；必须由 Host 的 next/断开语义交接，真实 PRoot DSH 的端到端行为仍未设备验证。
- 取消与 next 并发时可能 Host 已接受转交；客户端正常取消并释放旧连接，不用新 Cookie 重发旧 clientId。
- Markdown 不执行 HTML、不自动联网，图片仍占位；重发是真实新 prompt，不冒充官方 regenerate。
- 交接清理：本次沿用本 PR 唯一记录，清除其中过期“等待 #43/#38 合并”结论；未删除跨 PR 记录，因为它们仍是并行功能/未验证范围的依据。

### 产物与校验

- 本轮不构建/安装 APK，不创建 tag/Release，不合并默认分支。
- 源码候选包由最终文档完成后 `scripts/package-source.py` 生成，SHA 以 `artifacts/SOURCE-SHA256SUMS` 为准，不回填自身形成循环。
- PR：[https://github.com/leezzou/tanuki/pull/40](https://github.com/leezzou/tanuki/pull/40)，head 以最终推送 SHA 为准。

<a id="20261110-1333-handoff-pr-mode"></a>

## 20261110-1333-handoff-pr-mode

- Agent/会话标识：android_agent-49（AO Worker）
- 时间：2026-11-10 13:33 CST

### 当前状态

- 已实现：`scripts/check-handoff.py` 支持 `--pr`（默认）、`--integrate`、`--release` 三种模式；PR 模式可接收变更清单，强制“公开文件有改动必须附带交接记录”；集成模式要求记录已链接且为最新并刷新 schema 2 印章；发布模式只读严格校验。新增 `scripts/test-check-handoff.py` 8 组离线回归。CI Host job 改为 PR 模式并支持 `workflow_dispatch` 选择 release 严格模式。`prepare-app-release.py` 改为 `--release`，`verify-workspace.sh` 明确 `--pr`。新增 `docs/HANDOFFS.md` 并同步 `AGENTS.md`、模板、QUICKSTART、PUBLICATION、CI、ORCHESTRATOR-RULES、LESSONS、code-standards、QA-GATES、RELEASE。
- 已验证：`test-check-handoff.py` 8/8、`test-agent-workflow.py` 19/19、`test-publication.py` 19/19、`test-app-release.py` 18/18、PR/集成/发布三种模式、`check-publication.py --git` 0 findings、`package-source.py` 成功、脚本语法与 CI YAML 解析通过；具体命令与结果见“验证记录”。
- 待验证：PR 的独立 Codex High 审查、GitHub PR CI、合并后的首次集成迁移。
- 当前分支：`ao/handoff-conflicts/pr-mode`，创建时基线 `origin/main=1cb7ad95229c539231a64c103b581a477709e344`；PR [#42](https://github.com/leezzou/tanuki/pull/42) 创建后 main 两次前进：`2a4a0503`（PR #35）与 `0068c872`（PR #36 中继配对）；均已合并并解决 `AGENT-STATE`/`TEST-REPORT`/`CURRENT.json` 冲突，按新树重建印章；未合并、未发布、未创建 tag/Release。
- 关键事实与定位：模式说明与迁移步骤见 [交接机制](HANDOFFS.md)；脚本入口 `scripts/check-handoff.py`；自测 `scripts/test-check-handoff.py`；CI `.github/workflows/verify.yml`；发布入口 `scripts/prepare-app-release.py`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `scripts/check-handoff.py` | 重写为 PR/集成/发布三模式；schema 2 印章增加证据指纹；PR 模式支持 `--changed-from`/`--changed-file` 守卫（含公开文件删除；旧交接删除按路径豁免）；拒绝模板占位符与未声明未测范围。 |
| `scripts/test-check-handoff.py` | 新增离线自测：容忍落后共享状态、记录完整性、路径/符号链接、变更集守卫（含删除源码/豁免旧交接）、无变更清单时校验最新记录、CI 配置回归、集成最新记录、发布篡改检测、目录移动可迁移。 |
| `scripts/test-agent-workflow.py` | 原有交接用例迁移到新语义（PR 校验、`--integrate` 刷新、`--release` 严格）。 |
| `.github/workflows/verify.yml` | Host job 先跑交接自测；交接步骤改为 PR 模式 + 变更清单（`--diff-filter=AMRD` 包含删除），无基线时不传空清单而执行 `--pr` 结构校验；步骤名统一为 `Check handoff records`；新增 `workflow_dispatch` 的 `handoff_mode=release` 严格路径。 |
| `scripts/verify-workspace.sh` | 构建/设备门禁明确使用 `--pr`，普通 PR 不被共享状态阻塞。 |
| `scripts/prepare-app-release.py` | 发布打包前使用 `--release` 严格核对。 |
| `docs/HANDOFFS.md` | 新增：角色/时间点、三模式、普通 PR 与集成流程、冲突避免、指针 + 最近 N 条目标格式、迁移步骤、质量检索与失败处理。 |
| `AGENTS.md` | 交接总规则拆分为普通 PR/集成/发布三层；更新命令、清理与接手入口。 |
| `docs/handoffs/TEMPLATE.md` | 明确普通 PR 不改共享状态；结束命令改为 `--pr`。 |
| `docs/AGENT-QUICKSTART.md` | 接手入口、宿主自测与交付步骤改用 PR 模式。 |
| `docs/PUBLICATION.md` | 提交前清理/检查区分普通 PR 与集成方，新增 `--release`。 |
| `docs/CI.md` | 说明 CI PR 模式、变更清单守卫和手动严格模式。 |
| `docs/ORCHESTRATOR-RULES.md` | 明确普通 Worker 不写共享状态、集成阶段单一维护方流程。 |
| `docs/LESSONS.md` | L-001 更新为“普通 PR 不写共享状态 + 集成统一刷新”的结论与动作。 |
| `docs/code-standards.md` | 文档/脚本提交检查改为 PR 模式与集成模式分工。 |
| `docs/QA-GATES.md` | QA 记录只写自己的交接，`AGENT-STATE` 由集成方汇总。 |
| `docs/RELEASE.md` | 说明发布前使用严格模式。 |
| `docs/TEST-REPORT.md` | 增加本轮非设备验证摘要。 |
| `docs/handoffs/20261110-1333-handoff-pr-mode.md` | 本记录。 |
| `docs/NATIVE-PARITY.md` | 维护规则与验收步骤改用 `--pr --record`，共享状态由集成方刷新。 |
| `docs/AO-WORKTREE-BUILD.md` | 更新 worktree 门禁说明：`verify-workspace.sh` 先跑 `--pr`，不再要求每个 worktree 自带最新印章。 |
| `docs/AGENT-STATE.md` | 仅更新当前交接指针、机制说明与合并后集成命令；未做最近 N 条精简（迁移步骤见 HANDOFFS）。 |
| `docs/handoffs/CURRENT.json` | 由 `--integrate` 生成 schema 2 印章，不手写。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `python3 scripts/test-check-handoff.py` | 通过，11 tests / 0 failures | 临时目录合成 fixture；覆盖三模式、篡改、变更集（含删除）、无变更清单、CI 配置、目录移动 |
| `python3 scripts/test-agent-workflow.py` | 通过，19 tests / 0 failures（54.318s） | 含设备锁/模拟 ADB 离线回归，不访问真实设备 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1333-handoff-pr-mode.md` | 通过（共享状态可落后） | 本记录结构、占位符、未测声明 |
| `python3 scripts/check-handoff.py --integrate --record docs/handoffs/20261110-1333-handoff-pr-mode.md` | 通过，刷新 schema 2 印章（合并 main 后按新公开文件数重建） | 要求 AGENT-STATE 已链接且本记录最新；合并 main 后按新公开文件数重建 |
| `python3 scripts/check-handoff.py --release` | 通过（记录哈希、公开指纹、证据指纹、最新记录一致） | 只读严格模式 |
| `python3 scripts/check-publication.py --git` | 通过，0 findings | 检查公开候选、链接与身份/凭据模式；提交前最终复跑 |
| `python3 scripts/package-source.py` | 通过，生成源码包与 `artifacts/SOURCE-SHA256SUMS` | 工作材料；最终文档改动后会再生成，SHA 以最后输出为准 |
| `ruby -ryaml -e 'YAML.load_file(".github/workflows/verify.yml")'` | `YAML_OK` | 只证明 YAML 可解析；GitHub Actions 表达式以 PR CI 为准 |
| GitHub Actions（head `c417daa`） | Host scripts 41s pass；Android build/JVM/lint 5m38s pass；PR run 也 pass | 合并 main 后的新 head 以 PR 页面为准 |
| `bash -n scripts/verify-workspace.sh`；Python 内存语法检查 | 通过 | 未执行真实 Gradle/设备 |
| `python3 scripts/test-publication.py`、`test-app-release.py` | 19/19、18/18 通过 | Host CI 同套离线回归；发布入口断言 `--release` |

- Review P2 修复（PR #42）：① `--diff-filter=AMR` 改为 `AMRD`，删除公开源码/测试同样触发交接要求，旧交接删除由 `is_handoff_path()` 豁免并补回归；② 无基线时不再传空变更清单，改为执行不带清单的 `--pr` 校验最新记录结构，并补 checker/CI 配置回归。
- 哪些是前一轮结果，哪些是本次新验证：以上均为本次新跑；0.1.10 的 535 JVM、设备 QA、发布包等是历史结果，本轮未复跑。
- 未测试、超时、失败和暂未复现的情况：未跑 Android 构建/JVM/lint/仪器/设备；未在 GitHub CI 上验证新 workflow；未执行 AGENT-STATE 实际精简；PR 尚未创建、未合并、未发布。

### 未完成与暂停项

- 未完成：创建 PR 并等待当前 head 的独立 Codex High 审查与 GitHub CI；合并后由集成维护方按 `docs/HANDOFFS.md` 第 8 节执行 `AGENT-STATE` 指针 + 最近 N 条迁移，并重跑 `--integrate` / `--release`。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；未获合并/发布授权。
- 不在本任务范围：产品代码与 Android 测试、APK/发布附件。

### 下一步

1. 推送分支 `ao/handoff-conflicts/pr-mode`，创建面向 `main` 的 PR；PR 描述包含三模式命令、验证结果、迁移步骤与风险，不合并、不发布。
2. 独立审查关注：PR 模式是否仍能发现“源码改动未写交接”、变更清单基线是否可靠、`evidenceFingerprint` 是否允许不更新记录而刷新共享状态、发布严格模式是否覆盖旧 schema 迁移。
3. 合并后首个集成：按 `docs/HANDOFFS.md` 第 8 节精简 `AGENT-STATE`，再运行 `--integrate --record <最新记录>`、`--release`、`check-publication.py --git`、`package-source.py`。
4. 若 GitHub CI 因 `fetch-depth: 0` 或基线 SHA 不可用失败，回退为“仅校验最新记录结构”并在 PR 中记录原因，不放宽变更集守卫。

### 约束与风险

- 数据/设备/权限边界：只改脚本、CI 与文档，未读写真实 Key/Cookie/token，未启动设备、未构建 APK、未发布。
- 关键决策及理由：
  - 默认模式设为 PR 模式，避免普通 PR 被共享状态阻塞；严格性放在显式 `--integrate` / `--release`。
  - 用 CI 传入的变更清单补回“源码变化必须有交接”的质量守卫，弥补 PR 模式不再比较全局指纹的缺口。
  - 印章 schema 升到 2，新增 `evidenceFingerprint`（排除 `AGENT-STATE.md`），允许集成方只刷新共享状态而不被迫改写记录正文；发布仍校验包含状态的完整指纹。
  - 集成模式要求目标记录是文件名最新记录，确保 `AGENT-STATE`/`CURRENT.json` 不会指向旧交接。
- 失败尝试与避免重复踩坑的结论：`py_compile` 在沙箱外写缓存失败，改用内存 `compile()`；自测 stub 最初未复刻 `jniLibs` 排除规则，补齐后通过。不要手工合并 `CURRENT.json`；冲突时取 main 后重新 `--integrate`。PR #42 创建后 main 连续前进到 `2a4a0503` 与 `0068c872`，证实旧机制会在每个合并后制造冲突；本分支用 merge 更新基线并解决共享文档冲突，未改写已推送历史。

### 产物与校验

- 新/旧APK、源码ZIP及校验文件，哪些确实更新：不生成 APK；已重新生成 `artifacts/sources/Tanuki-0.1.7-source.zip` 与 `artifacts/SOURCE-SHA256SUMS`（工作材料，不提交）。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名发布、未创建 tag/Release。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮未删除历史交接；F1、PDF、远程主机等记录仍有未完成/待审事项，保留。本记录与 `TEMPLATE.md`、`CURRENT.json` 保留。
- 普通 PR 不刷新共享状态/印章；本轮因是机制改造并需验证 `--release`，由本会话执行一次 `--integrate` 生成 schema 2 印章。后续普通 PR 只跑 `--pr`。
- 文档或脚本变化后重新生成源码 ZIP；不把源码 ZIP 自身哈希写回归档内形成循环。

<a id="20261110-1636-release-0.1.11"></a>

## 20261110-1636-release-0.1.11

- Agent/会话标识：android_agent-49（AO Worker）
- 时间：2026-11-10 16:36 CST

### 当前状态

- 已实现：`app/build.gradle.kts` 默认版本改为 0.1.11/code13；`AGENT-STATE` 精简为当前指针 + 最近 5 条 + 全局未验证/暂停 + 长期入口；#38 记录按合并后事实修正并移除已删专题文档引用；发布/取包/更新/兼容性/上手指南同步 0.1.11；新增本轮记录。
- 已验证：在最终 main（#40 合并）上完整 JVM **794 tests / 90 个结果文件 / 0 failures / 0 errors / 0 skipped**、`lintModernDebug` **0 errors / 102 warnings / 1 hint**（本地 JDK17）；候选 APK 实核 `com.tanuki.modern.workspacecheck`、0.1.11/code13、minSdk26、ARM64 三个原生库、证书 `371dc301...`、11,890,210 字节、SHA-256 `192f73f2...31a976d`；六附件已生成且 `RELEASE-SHA256SUMS` 校验通过。
- 待验证/待完成：设备/真机；临时 `DshHttpAdminTransport` 适配器后续清理。
- 当前分支/发布：PR #44 已 squash 合并为 `main=4f2a3717d6d7`，tag `v0.1.11` 与 Pre-release 已发布；发布后文档一致性修正基于该 main。
- 发布后文档一致性修正：将 #40 状态与 0.1.11 发布状态统一到 main=`4f2a3717d6d7`；未改代码、版本或附件。
- 关键事实：0.1.11/code13 已发布为 Pre-release（tag `v0.1.11`，main=`4f2a3717d6d7`）；0.1.9/code11 为上一 Pre-release；0.1.10/code12 为历史本地候选、未发布；#40 已合并（`1f7222d0`），临时 `DshHttpAdminTransport` 可在后续切片替换为 `DshTransport`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/build.gradle.kts` | 默认 `versionName=0.1.11`、`versionCode=13`，与发布参数一致。 |
| `docs/AGENT-STATE.md` | 精简为指针 + 最近 5 条 + 全局未验证/暂停 + 长期入口，保留交接/测试/发布检索入口。 |
| `docs/handoffs/20261110-1226-native-parity-non-chat.md` | 用 `433d7b1` 版本覆盖并按 8 条清单修正：状态改为 #38 已合并，删除临时表述/虚假文档改动，历史数字标注，外部依赖改 #40 与 workspace/follow，更新下一步，移除已删除专题文档引用。 |
| `docs/TEST-REPORT.md` | 新增 0.1.11 发布准备验证章节与测试基线。 |
| `docs/RELEASE.md` | 0.1.11/code13 发布命令、附件名、tag、输出目录；保留 0.1.9 已发布事实与 0.1.10 历史候选说明。 |
| `docs/ARTIFACTS.md` | 新增 0.1.11 候选目录，0.1.10 标为历史本地候选；更新六附件/tag/取包说明。 |
| `docs/APP-UPDATES.md` | 0.1.11/code13 发布候选、命令、附件名与版本历史。 |
| `docs/PUBLICATION.md` | 0.1.11/code13 发布状态、功能范围与收尾分支。 |
| `docs/RUNTIME-COMPATIBILITY.md` | 0.1.11/code13 发布候选，保留 0.1.10 未发布事实。 |
| `docs/AGENT-QUICKSTART.md` | 交付章节改为 0.1.11 候选、基线 b075725e 与 #40 后 rebase。 |
| `docs/handoffs/20261110-1636-release-0.1.11.md` | 本记录。 |
| `docs/handoffs/CURRENT.json` | 由 `--integrate` 生成的 schema 2 印章，不手写。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest` | BUILD SUCCESSFUL（1m35s）；794 tests / 90 结果文件 / 0 failures / 0 errors / 0 skipped | 最终 main（#40）；JDK17、Android SDK、macOS ARM64；不含设备/仪器 |
| `./gradlew ... :app:lintModernDebug` | BUILD SUCCESSFUL（2m5s）；0 errors / 102 warnings / 1 hint | 最终 main（#40）；存量告警；不代表设备 |
| `check-handoff.py --integrate --record docs/handoffs/20261110-1636-release-0.1.11.md` | 通过，刷新 schema 2 印章（671 公开文件） | AGENT-STATE 已链接本记录 |
| `check-handoff.py --release` | 通过 | 记录哈希、公开源码指纹、证据指纹与最新记录一致 |
| `check-handoff.py --pr` | 通过 | 最新记录 8 段结构、占位符与未测声明 |
| `check-publication.py --git` | 通过，0 findings（672 public files / 671 index entries） | 断链、公开边界与凭据模式 |
| `package-source.py` | 通过，生成 `Tanuki-0.1.11-source.zip` 与 `SOURCE-SHA256SUMS` | 最终源码 ZIP 的字节/SHA 以 `RELEASE-SHA256SUMS` 为准，不写回文档形成循环 |
| `prepare-app-release.py --version 0.1.11 --version-code 13 --channel workspacecheck` | BUILD SUCCESSFUL（2m7s）；APK 实核包名/版本/code/minSdk/证书/ARM64 原生库；六附件已生成 | 最终 APK SHA-256 `192f73f2e07782c63a9b8defc5294153987fd03285181a5c12960531231a976d`、11,890,210 字节 |

- 失败与修正：首次 Gradle 测试只设 `TANUKI_JDK`、未设 `JAVA_HOME`，Gradle 使用 JDK27 报 `27.0.1.0.3`；设置 `JAVA_HOME` 后重跑成功。
- 前一轮/历史：0.1.10 的 535 tests、0.1.9 的发布附件与设备结论均为历史，未冒充本轮。
- 未测试：设备/模拟器安装、升级覆盖、真机/MIUI/低 API；APK 已实核，但未安装/未在真实 PRoot DSH 运行。

### 未完成与暂停项

- 未完成：设备 QA；临时 `DshHttpAdminTransport` 后续清理。发布与 tag/六附件上传已完成。
- 后续清理：PR #40 `DshTransport` 已合并（`1f7222d`）；临时 `DshHttpAdminTransport` 替换为 `DshAdminTransportAdapter` 属后续代码清理；归档列表恢复依赖 workspace/follow WebSocket。
- 用户暂停：API Key 保存、真实模型/账号、真机验收；未获最终授权不合并、不发布。

### 下一步

1. 发布已完成：main=`4f2a3717d6d7`，tag `v0.1.11`，Pre-release 六附件；本 PR 只做发布后文档一致性修正。
2. 设备 QA 授权后验证真实 PRoot DSH、聊天发送/停止/实时流、审批/工具、输入附件、会话管理、远程连接与文件预览。
3. 后续清理临时 `DshHttpAdminTransport`；后续切片：工作区全局搜索/diff、插件、命令执行、语言主题、归档恢复。

### 约束与风险

- 数据/设备/权限边界：不改产品代码/协议；不读真实凭据；不启动设备；不合并/发布/tag；不关闭审批/TLS/校验。
- 关键决策：0.1.10 未发布，文档明确标为历史本地候选；0.1.11 初始准备基线 `b075725e`，#40 合并后最终基线 `1f7222d0e4ac`；AGENT-STATE 只保留指针 + 最近 5 条，细节留在 handoff。
- 风险：候选已按 #40 最终树重建并实核，不能沿用 0.1.10/旧 0.1.11 候选；设备 QA 未跑，0.1.11 不能宣称已通过真机验收。
- 失败尝试：Gradle 测试首次因 `JAVA_HOME` 未指向 JDK17 失败，已修正并重跑；未修改产品代码规避。

### 产物与校验

- 新 APK/源码：`artifacts/releases/0.1.11/` 六个附件已按最终 main（#40）生成：APK（11,890,210 字节，SHA-256 `192f73f2e07782c63a9b8defc5294153987fd03285181a5c12960531231a976d`）、`app-update.json`、`Tanuki-0.1.11-source.zip`、原生对应源码 TAR、`native-source-SHA256SUMS`、`RELEASE-SHA256SUMS`；源码 ZIP 最终字节/SHA 以 `RELEASE-SHA256SUMS` 为准。
- 是否覆盖安装/签名：未安装、未覆盖；workspacecheck 使用开发证书、non-debuggable、ARM64、minSdk26/targetSdk34；不是正式签名。
- 发布事项：main=`4f2a3717d6d7`，annotated tag `v0.1.11`，Pre-release 六附件已上传；0.1.10 及更早附件保持原样。
- 过期记录清理：本轮未删除历史交接；0.1.10 为历史候选，保留其记录/附件事实。#38 记录按合并后事实修正，不删除其功能清单与未完成项。
- 共享状态/印章：发布时已按 `--integrate` 刷新；本 PR 为发布后文档一致性修正，只改上述文档，不刷新共享印章。

<a id="20261110-1736-remote-ssh-wizard"></a>

## 20261110-1736-remote-ssh-wizard

- Agent/会话标识：android_agent-55
- 时间：2026-11-10 17:36（UTC+8）

### 当前状态

- 已实现：六步向导（方式 → 主机账号 → 认证 → 指纹 → 测试 → 完成）；字段级中文校验与示例；阶段化错误（TCP/SSH 握手/认证/端口转发/DSH 探测）+ 下一步建议 + 重试；首次 SHA256 指纹必须显式勾选；命名连接新增/编辑/删除与“上次使用”；密码/私钥/口令/token 继续走 Android Keystore AES-256-GCM；远程/本地状态与切换影响提示；私钥系统文件选择器（64 KiB 上限）。
- 已验证：Kotlin 编译、完整 JVM 928 tests / 101 文件 / 0 失败、lint 0 errors / 103 warnings / 1 hint；交接/上传检查待提交前最后运行。
- 待验证：真实 Android 设备/模拟器 UI、系统文件选择器、真实 sshd/中继/DSH、Android Keystore 真机、弱网/断线、深色/大字体。
- 当前目标包名：`com.tanuki`（本轮未构建 APK，不涉及安装包）。
- 关键事实与可定位源码：向导与校验 `runtime/RemoteHostWizard.kt`；阶段模型/错误映射 `runtime/RemoteConnectionError.kt`；命名连接 `runtime/RemoteHostProfiles.kt`；控制器 `runtime/RemoteHostController.kt`；阶段回调 `runtime/SshTransport.kt`、`runtime/RelayTransport.kt`；UI `ui/RemoteHostSettings.kt`。

### 本次改动

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
| `app/src/test/kotlin/com/tanuki/RemoteWizardTest.kt` | 新增 41 项 JVM 回归：状态机、校验、错误映射、命名连接、控制器阶段、来源 profile ID 清理、迟到私钥读取、重试归属、6 位码、chip 展示 |
| `docs/REMOTE-HOST.md`、`docs/REMOTE-CONNECT.md`、`docs/INTERFACES.md` | 同步向导流程、阶段错误语义与命名连接契约；按普通 PR 规则不修改 `docs/TEST-REPORT.md` |
| `docs/handoffs/20261110-1736-remote-ssh-wizard.md` | 本轮唯一交接记录；未改 `AGENT-STATE.md`/`CURRENT.json` |

### 给 51 的接线 hook（当前保留 #52 接线）

当前 `MainActivity.kt` 保留 #52 已合并的 12 行向导入口接线，本 PR 相对 main 零 diff，因此设置页向导仍可用。以下字段/组件供 51 迁移接线；等 51 的接线合并后，再单独小 PR 删除这 12 行重复入口。

#### 1. TanukiUiModel（MainActivity 构造 `TanukiUiModel(...)` 处）

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

#### 2. TanukiUiCallbacks（MainActivity 构造 `TanukiUiCallbacks(...)` 处）

```kotlin
saveRemoteProfile = manager.remoteHost::saveProfile,
deleteRemoteProfile = manager.remoteHost::deleteProfile,
connectRemoteProfile = manager.remoteHost::connectProfile,
```

签名：
- `saveRemoteProfile: (String, RemoteHostConfig, String?, Boolean) -> Unit`（名称、配置、编辑 id、保存后是否连接）
- `deleteRemoteProfile: (String) -> Unit`
- `connectRemoteProfile: (RemoteHostProfile) -> Unit`

#### 3. DshNativePage 头部 chip

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

#### 4. 页面根部 sheet

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

#### 5. P1 相机扫码计划（本 PR 未实现）

- 依赖：CameraX（`androidx.camera:camera-camera2`、`camera-lifecycle`、`camera-view`）+ `com.google.zxing:core`（Apache-2.0，纯 Java 离线解码）；不引入 Google Play 服务。
- 接入时必须记录：库名/版本/许可证、APK 体积增量、CameraX 生命周期绑定、相机权限被拒时的降级路径（直接回到“粘贴链接 + 6 位码”手输，不卡扫码页）。
- P0 当前只做“配对链接 + 6 位码”，无相机、无新依赖。

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin -x buildNativeRuntime` | 通过（BUILD SUCCESSFUL） | JDK17、Android SDK、macOS ARM64；仅编译 |
| `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:testModernDebugUnitTest -x buildNativeRuntime` | 通过：**928 tests / 101 个结果文件 / 0 failures / 0 errors / 0 skipped**（33m50s） | 含新增 `RemoteWizardTest` 41 项；既有 `RemoteHostTest`/`RemoteRelayTest`/`DshRemoteIsolationTest` 通过 |
| 同上，单独 `--tests com.tanuki.RemoteWizardTest` | 通过：26 tests / 0 failures | 新增回归定向结果 |
| `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:lintModernDebug -x buildNativeRuntime` | 通过：**0 errors / 102 warnings / 1 hint**（3m58s） | 仅 JVM/lint，不含设备 |
| 最终自查修复（完成页空配置、隧道断开/存储错误不再误报阶段）后重跑 JVM + lint | 通过：820 tests / 0 failures；lint 0 errors / 102 warnings / 1 hint | 最终 head `c017488`；本轮修复提交也附带本交接记录更新 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1736-remote-ssh-wizard.md` | 通过 | 普通 PR 模式 |
| `python3 scripts/check-publication.py --git` | 通过：677 public files / 672 index entries / 0 findings | 上传检查 |
| 评审修复与后续增量：4 个 P2、6 位码、chip 映射、MainActivity 回退 | 通过：928 tests / 0 failures；lint 0 errors / 103 warnings / 1 hint | 合并最新 main 后复验；MainActivity 保留接线、相对 main 零 diff |
| 第三轮 P2：活动副本按 sourceProfileId 归属；迟到私钥读取按请求版本丢弃 | 通过：924 tests / 0 failures | 编辑目标后删除、同服务双 profile、切换认证/进入测试后迟到回调均有回归 |
| 最终 P1/P2：保留 #52 向导接线；host_agent 默认系统 CA 路径先配 INFO 日志 | 通过：MainActivity 相对 main 零 diff；Python 9 tests | 设置页向导入口不消失；配对码在默认路径可见 |
| MainActivity 接线恢复提交 | `822ad6f`：保留 12 行，相对 main 零 diff | 设置页向导入口在 51 迁移前保持可用 |
| 控制器→向导状态回归 | 通过：`RemoteWizardTest` 40 项；成功进入 DONE、失败退出 busy 并停 AUTHENTICATION | 覆盖 MainActivity 真实模型接线后的成功/失败路径 |
| 公开重连来源保留 P2 | 通过：`RemoteWizardTest` 41 项 | `connect(config)` 重连/重试恢复 sourceProfileId；新手工配置不继承；编辑目标后删除仍清理 |
| legacy 同主机不同账号清理 P2 | 通过：`RemoteWizardTest` 41 项 | 活动凭据归属与 known_hosts 主机身份分离；alice/bob 同主机删除 alice 仍清理，bob 的主机密钥信任保留 |
| `python3 scripts/remote-relay/test_remote_relay.py` | 通过：9 tests（含 6 位码共享向量 308002、默认系统 CA 路径配对码输出） | Python 3.9 标准库回环 |
| `git diff origin/main -- app/src/main/kotlin/com/tanuki/MainActivity.kt` | 空（MainActivity 相对当前 main 零 diff） | 保留 #52 接线；51 迁移后单独删除重复入口 |

- 哪些是前一轮结果，哪些是本次新验证：以上编译/JVM/lint 均为本次新验证；前一轮 0.1.11 的 794 tests 不应与新数字混算。
- 未测试、超时、失败和暂未复现的情况：`NpmAdaptiveWorkerPoolTest` 在首次完整套件和单独复跑时曾出现一次 8/9 并发时序失败；随后完整套件通过，未修改该测试，不能据此宣称该测试稳定。真实设备/真实 SSH/中继/DSH、系统文件选择器、Keystore 真机、弱网/断线、深色/大字体均未执行。

### 未完成与暂停项

- 未完成：独立 Codex High 审查、CI、适用设备 QA。分支已推送，PR [#52](https://github.com/leezzou/tanuki/pull/52) 已创建。
- 外部阻塞：无（设备与真实主机验收需要独立 QA 和明确目标）。
- 用户暂停：合并/发布、真机/真实账号验收、API Key 保存。

### 下一步

1. 提交并推送 `ao/remote-ssh-wizard/finalize`，PR [#57](https://github.com/leezzou/tanuki/pull/57)（目标 `main`）已包含改动、924 tests、lint、未验证项与不合并/不发布。
2. 由独立 Codex High 对 PR 当前完整 SHA 审查；处理意见后对新 SHA 重跑 `:app:testModernDebugUnitTest` 与 `:app:lintModernDebug`。
3. 设备 QA 需要独立会话：验证六步向导、私钥系统文件选择器、指纹勾选、阶段进度、失败重试、命名连接增删改、远程/本地切换提示；需要真实 sshd/中继/DSH 环境。
4. 未获明确授权不合并、不打 tag、不创建 Release、不发布 APK/源码 ZIP。

### 约束与风险

- 数据/设备/权限边界：未使用真实凭据；未关闭主机密钥/TLS 校验；未引入“信任未知主机”默认；私钥/口令/token 未落明文；未操作设备。
- 关键决策及理由：
  - 向导状态机保持纯 Kotlin，Compose 只做展示与副作用，便于 JVM 回归。
  - 阶段回调做成可选接口 `StagedSshTransport`，不破坏既有测试假传输；控制器对未实现回调的传输退化为粗粒度。
  - 命名连接库独立于当前活动配置存储，避免改动既有 `RemoteHostStorage` 兼容键；同一时间仍只有一个活动远程连接。
  - 测试连接继续不切换 origin、不保存配置；只有“保存并连接”才持久化并切换管理页。
- 失败尝试与避免重复踩坑的结论：
  - 共享 `.git` 在工作区外，Git 写引用/Gradle 编译需要一次完整访问权限；未修改全局 Java，复用主检出 `.build/jdk` 的 JDK17。
  - `NpmAdaptiveWorkerPoolTest` 存在既有并发时序抖动，本轮未修改；完整套件通过后才记录 928/0，不隐瞒首次失败。
  - Compose 中函数类型回调不能用 `c::connectRemoteHost` 直接引用，已改为显式 lambda。

### 产物与校验

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

<a id="20261110-1737-composer-input-polish"></a>

## 20261110-1737-composer-input-polish

- Agent/会话标识：android_agent-52
- 时间：2026-11-10 17:37 CST

### 当前状态

- 开始基线：`origin/main=4f2a3717d6d795fd6b94cdb695878d8119f818c1`；分支 `ao/composer-input/root`；无本地改动。
- 已实现：
  - 输入：`BasicTextField` 胶囊布局，单行 48dp/圆角 28、1–6 行随文本布局增高、多行圆角 18、左右 36dp 圆键贴底；只有图标做短促形变动画；组件根节点 `imePadding()`；`Ctrl/⌘+Enter` 发送；发送/重发后 `FocusRequester` 把焦点还给输入框；失败保留内容并显示“重发”。
  - 草稿：沿用按会话 `SharedPreferencesDshDraftStore`；发送时快照文本与附件 id，成功后只清快照，等待期间新编辑保留；跨会话不携带附件。
  - 附件：图片/文件选择、粘贴图片、拖入文件；中文类型/大小/数量提示；上传卡片显示不定进度和“取消”，取消立即移除并阻止晚到响应回写；读取/拖入期间显示“正在读取附件…”。
  - @/斜杠：候选上下键循环、Enter/Tab 插入、Esc 关闭；斜杠候选按名称/描述/参数提示搜索排序；插入只替换当前查询并保留后续参数/换行；查询变化清空过期候选。
  - 停止：`session/cancel` 客户端与参数；`generationRunning`/`stopping`/`stopOutcome`。accepted 只进入 `REQUESTED` 并保持“正在停止”，保留停止入口/运行状态；超时降级为“仍在收尾”并重新开放停止入口；官方 `running=false` 经 `setGenerationRunning(false)` 才进入 `STOPPED`，失败进入 `FAILED`。停止不取消在途 prompt 回执、不强制清 `sending`。
  - 接线兜底：只有页面调用过 `setGenerationRunning(...)` 后，提交回执才可能置 `generationRunning=true`；未接 running 时发送键保持可用。官方状态带版本号，等待期间状态已变化时迟到的 prompt accepted 不再覆盖；已确认 `STOPPED` 也不会被迟到的 cancel/prompt 回执重新打开；停止回执还校验 `generation` 代次，A→B→A 的旧回执不会污染新状态/定时器；接入 running 后 30 秒无新事件只进入 `runStateStale` 降级，额外开放继续发送，保留官方运行态与停止能力。
- 已验证：定向 61 tests；完整 JVM 814 tests / 90 文件 / 0 failures / 0 errors / 0 skipped；`lintModernDebug` 0 errors / 102 warnings / 1 hint；交接检查与发布过滤通过。
- 待验证：真实设备/模拟器的软键盘避让、输入法组合、焦点、图片/文件选择器、粘贴/拖入、上传取消、候选键盘导航；页面接线后的停止入口与运行状态同步；真实 PRoot DSH；assemble、Android 仪器、真机。
- 当前目标包名、分支/无Git状态、是否有运行中任务：`com.tanuki.modern`；分支 `ao/composer-input/root`；无设备任务。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshComposerProtocol.kt` | 新增 `session/cancel` 参数、命令名校验、@/斜杠插入纯函数、命令搜索排序、候选循环移动；供控制器/UI/测试复用 |
| `app/src/main/kotlin/com/tanuki/web/DshComposerClient.kt` | 新增 `cancel(sessionId)`，只调用官方 `session/cancel` 并校验 `accepted` |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerController.kt` | 停止三段状态 `REQUESTED/STOPPED/FAILED`；停止不取消 prompt 回执、不清 `sending`；发送失败标记可重发；上传取消与晚到结果丢弃；上传串行锁；查询变化清空过期候选；命令搜索 |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerBar.kt` | 胶囊输入条 48dp/1–6 行/圆角 28→18、左右 36dp 圆键、发送/停止同键形变；键盘避让、焦点回归、候选键盘导航与高亮、停止状态、不定进度、读取中提示、取消上传按钮；@ 插入避免双空格 |
| `app/src/test/kotlin/com/tanuki/DshComposerProtocolTest.kt` | 新增取消参数、插入位置、命令搜索/候选循环 6 项 |
| `app/src/test/kotlin/com/tanuki/DshComposerClientTest.kt` | 新增取消请求成功/拒绝 1 项 |
| `app/src/test/kotlin/com/tanuki/DshComposerControllerTest.kt` | 新增失败重发、跨会话草稿/附件、取消上传、停止请求/确认/失败/超时、延迟/迟到回执、未接线发送、30 秒静默降级、旧代次停止回执、命令搜索等回归 |
| `docs/INTERFACES.md` | 1.9.1 增补 `session/cancel`、停止状态、键盘导航、上传取消与页面接线边界 |
| `docs/handoffs/20261110-1737-composer-input-polish.md` | 本记录 |

明确未改：`DshNativePage.kt`、`MainActivity.kt`、`DshNativeController.kt`、`DshNativeApi.kt`、`DshTransport.kt`、`DshComposerDraftStore.kt`、`DshAttachmentReader.kt`、共享状态 `AGENT-STATE`/`NATIVE-UI`/`TEST-REPORT`/`CURRENT.json`。

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:testModernDebugUnitTest -x buildNativeRuntime --tests 'com.tanuki.DshComposerProtocolTest' --tests 'com.tanuki.DshComposerClientTest' --tests 'com.tanuki.DshComposerControllerTest'` | BUILD SUCCESSFUL；**61 tests / 0 failures / 0 errors / 0 skipped**（23 + 9 + 29） | 覆盖 accepted 仍运行、延迟/迟到回执、超时、未接线发送、静默降级、旧代次回执 |
| `... :app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL；**814 tests / 90 结果文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 102 warnings / 1 hint**；`DshComposer*` 无新增 lint 项 | 一次运行出现既有无关注册池测试偶发失败，单测重跑与完整重跑均通过；非本切片改动 |
| `git diff --check` | 无输出 | 无空白错误 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1737-composer-input-polish.md` | Handoff PR check passed | 普通 PR 模式；`AGENT-STATE`/`CURRENT.json` 未修改 |
| `python3 scripts/check-publication.py --git` | Publication check passed；673 public files / 672 index entries / 0 findings | 未输出匹配值 |
| `gh pr create --base main --head ao/composer-input/input-polish` | PR [#48](https://github.com/leezzou/tanuki/pull/48) 已创建 | 未合并、未发布 |
| `gh run watch 42798827155 --exit-status` | success；Android build/JVM/lint 7m7s，Host scripts/handoff 39s | head `e4daf58`；仅 CI，不等于设备验证或独立审查 |
| 同 SHA push 运行 42798815793 | 暴露 `fileUploadsBeforePromptAndUsesReceipt` 测试竞态：先读到 `UPLOADED`、worker 尚未置 `uploadBusy=false` | 产品逻辑无失败；已改为同时等待两个状态 |
| `... --tests 'com.tanuki.DshComposerControllerTest'` | BUILD SUCCESSFUL；29 tests / 0 failures | 停止两段语义、延迟/迟到回执、超时、未接线发送、静默降级、旧代次回执 |
| `... :app:compileModernDebugKotlin -x buildNativeRuntime` | BUILD SUCCESSFUL | 胶囊 `BasicTextField`/`onTextLayout`/形变动画 API 编译通过 |

- 哪些是前一轮结果，哪些是本次新验证：61 项目标回归、814 项完整 JVM、lint 均为本分支当前 head 新跑；809 中既有回归不是本切片新增。
- 未测试、超时、失败和暂未复现的情况：真实设备 UI/输入法/选择器/粘贴/拖入/跨应用临时权限/上传取消/停止、真实 PRoot DSH、assemble、Android 仪器、真机均未执行；`DshComposerBar` 的 Compose 焦点/键盘事件没有 JVM 自动化覆盖；`DshAttachmentReader` 的 Android URI/剪贴板路径没有仪器测试。

### 未完成与暂停项

- 页面接线：`DshNativePage`/`MainActivity` 归集成方；需要把 `DshNativeState.running` 同步到 `nativeComposer.setGenerationRunning(running)`，并在需要时把页面停止按钮与 `nativeComposer.requestStop()` 统一，避免两处状态不一致。
- 未验证：设备/模拟器、真实 DSH、上传取消的服务端残留（官方无上传取消端点，本地不保留残缺文件）、真实键盘导航。
- 未完成：本轮 P1/P2/P0 修复的新 head CI 与独立 Codex High 审查；按项目规则最新 SHA 必须独立审查。相关 review thread 在推送后回复并 resolve。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收按项目既有暂停项保留；本次未触碰。

### 下一步

1. 推送本轮修复，跑完整 JVM/lint/交接/发布检查并等新 head CI；回复并 resolve 两条 review thread。
2. 由独立 Codex High 审查 PR #48 最新 SHA；按审查意见补修。
3. 集成方接线 `setGenerationRunning(running)`，在设备/模拟器验证胶囊尺寸、发送/停止形变、输入法避让、焦点、候选键盘、图片/文件/粘贴/拖入、上传取消与停止。
4. 通过后由有合并授权的人合并；本 worker 不合并、不发布。

### 约束与风险

- 数据/设备/权限边界：只读用户选择的 `content://` 或受限 `data:image`；不读取 `file://`、App 私有文件或外部明文路径；不写真实 token/Cookie 到日志/状态/偏好；未启动设备、未安装、未跑仪器。
- 关键决策及理由：停止入口放在组件内，避免改页面接线；`session/cancel` accepted 只表示受理，必须等官方 `running=false` 才显示已停止，避免隐藏仍在收尾的停止入口；停止不取消在途 prompt 回执，避免已提交消息被误判失败后重复发送；未接 running 时用 `generationStateBound` 保留发送键，避免互斥按钮把发送永久锁死；官方状态版本号阻止迟到回执覆盖已确认 idle；停止回执校验会话代次；30 秒无事件只做 UI 降级并保留停止能力；胶囊高度由文本布局驱动，只有图标做形变动画；上传取消只保证本地卡片和状态不回流，官方无上传取消方法，不宣称服务端已删除；进度使用不定进度，不伪造百分比。
- 失败尝试与避免重复踩坑的结论：Material3 当前 `LinearProgressIndicator` 无 `strokeWidth` 参数；命令名校验在 `DshComposerProtocol` 内，策略层需走公开校验；取消上传后重启 worker 必须有串行锁，否则旧请求晚到可能重复上传。
- 风险：未接 running 时发送键可用但停止入口不会出现；接入后 30 秒无新事件会开放“继续发送”但保留停止入口；真实输入法/焦点/胶囊尺寸与形变动画需设备验证；取消上传后服务端可能仍完成已发出的 unary 请求；`DshComposerBar` 依赖页面提供的会话与生命周期。

### 产物与校验

- PR：https://github.com/leezzou/tanuki/pull/48（本轮修复提交后以 PR 当前 head 为准）。
- 新/旧APK、源码ZIP及校验文件：本轮未生成 APK、源码 ZIP 或发布附件；只改源码/测试/文档。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布、未合并。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：普通 PR 只新增本记录，不改共享状态。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码ZIP；普通 PR 运行 `python3 scripts/check-handoff.py --pr --record docs/handoffs/<本次记录>.md`，不修改 `AGENT-STATE.md`/`CURRENT.json`；提交前运行 `git diff --check` 与 `check-publication.py --git`。

<a id="20261110-1737-session-settings"></a>

## 20261110-1737-session-settings

- Agent/会话标识：android_agent-54（DSH Worker）
- 时间：2026-11-10 17:37 CST（开始），2026-11-10 19:20 CST（追加 P0/P1 本地实现）

### 当前状态

- 已实现（本地工作区，**尚未推送**）：
  - 回退上一版越界改动：`MainActivity.kt`、`TanukiDesign.kt` 恢复为基线；删除 `TanukiThemeMode.kt`/`TanukiThemeModeTest.kt`；设置页主题改为“跟随系统，手动切换暂不支持”。
  - P0-1：按官方 `cwd` 分组（空目录“未分组”）；筛选条保留全部/运行中/未读；顶部“选择”进入多选，可批量归档并显示 `batchDone/batchTotal` 进度；批量删除明确“官方无硬删除端点，暂不支持”；会话项保留标题、更新时间、运行中/未读/置顶标识。
  - P0-3：会话页头部 host chip 显示本机/远程状态；底部 sheet 列出本机与当前已保存远程主机，选中本机断开远程、选中远程连接已保存配置；说明切换影响。当前 runtime 只保存一个远程配置，多主机列表未做。
  - P1：设置根页改为六域：外观与语言、通知与后台、存储与空间、引擎与更新、远程主机、关于与诊断；各域复用真实设置项；手动主题/语言/独立字号明确“暂不支持”。
  - P0-2：详情 sheet 增加概览/进度/产物/文件四 tab；进度/产物/文件在协议未接入处明确“暂不支持”，文件 tab 展示真实 `cwd` 并支持复制，不生成假进度/假产物/假文件树。
- 已验证（本地）：
  - `:app:compileModernDebugKotlin` 成功（新增 host/tab/分组代码后）。
  - 定向 `DshSessionManagerControllerTest`：37 tests / 0 failures / 0 errors；含延迟失败、200/201 上限、批量挂起进度回归。
  - 完整 `:app:testModernDebugUnitTest`：936 tests / 101 文件 / 0 failures / 0 errors / 0 skipped。
  - `:app:lintModernDebug`：BUILD SUCCESSFUL，0 errors / 94 warnings / 1 hint。
  - 旧 head `30da86d` 的 GitHub CI 两个 run 均 success（不含本轮新改动）。
- 待验证：Android 仪器/真机、真实 DSH 的 cwd 分组/批量归档/主机切换/模型权限。
- 当前分支/提交/PR：跟进分支 `ao/session-settings/fix-batch-host`（从最新 `origin/main=7757b38` 创建），PR #56 最新 head（产品修复 `9a020c0`）；跟进 PR https://github.com/leezzou/tanuki/pull/56 ，CI 4 项通过。PR #50 已合并。
- 基线：`4f2a3717d6d795fd6b94cdb695878d8119f818c1`；远端 `origin/main` 已到 `326726c`（仅文档 #45），未 rebase。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerController.kt` | 增加批量归档 `archiveSessions`、进度字段、失败聚合；保留单个归档/模型/权限/刷新状态。 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerPage.kt` | 按 cwd 分组、多选批量归档 bar/dialog、host chip + 主机 sheet、详情四 tab、删除不可用说明。 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerActivity.kt` | 读取 `runtime.remoteHost.ui`，构造本机/远程 host 列表并接线切换；不改 MainActivity。 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionPresentation.kt` | 新增 cwd 分组 `DshSessionGrouping`、主机摘要 `DshSessionHosts`；保留权限/模型/删除纯函数。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 设置页改为六域分组；外观页只展示真实能力（跟随系统/简体中文/系统字号），未实现项写“暂不支持”。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 恢复为任务基线，未保留主题接线（属主 51）。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiDesign.kt` | 恢复为任务基线，未保留主题偏好（通用主题属主 56）。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiThemeMode.kt` | 删除；主题手动切换不在本 PR 实现。 |
| `app/src/test/kotlin/com/tanuki/DshSessionManagerControllerTest.kt` | 新增 cwd 分组、批量归档部分失败、安全模式拒绝、主机摘要状态回归。 |
| `app/src/test/kotlin/com/tanuki/TanukiThemeModeTest.kt` | 删除；主题实现撤回。 |
| `docs/NATIVE-UI.md` | 更新会话管理/设置切片：分组、多选、host、四 tab、六域设置与协议缺口。 |
| `docs/COMPONENTS.md` | 主题说明恢复为跟随系统，手动切换暂不支持。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh` | 通过 | worktree 本地 Gradle/JDK/SDK |
| `:app:compileModernDebugKotlin` | BUILD SUCCESSFUL | 新增分组/host/tab 后编译通过 |
| `:app:testModernDebugUnitTest --tests com.tanuki.DshSessionManagerControllerTest` | BUILD SUCCESSFUL；37 tests / 0 failures / 0 errors | 含延迟失败、200/201、挂起进度回归 |
| `:app:testModernDebugUnitTest` | BUILD SUCCESSFUL；936 tests / 101 文件 / 0 failures / 0 errors / 0 skipped | 本轮新改动 |
| `:app:lintModernDebug` | BUILD SUCCESSFUL；0 errors / 94 warnings / 1 hint | 警告为既有/工具链提示 |
| 旧 head `30da86d` PR CI | pull_request + push 两个 run 均 success | 旧 head 结果 |
| 新 head `9b6ddf9` PR CI | pull_request + push 两个 run 均 success（4 项检查） | 历史 head 结果 |
| 跟进 PR #56 CI | pull_request + push 两个 run 均 success（4 项检查） | 3 项 P2 修复后的 head |
| `python3 scripts/check-handoff.py --pr`、`check-publication.py --git` | 本轮最终改动后重跑 | 见收尾输出 |

- 哪些是前一轮结果，哪些是本次新验证：旧 head CI 为前一轮；本轮 37 定向 tests、936 全量 JVM、lint 为第二轮 P2 修复的新验证。
- 未测试、超时、失败和暂未复现：Android 仪器/真机、真实 DSH 的 goals/workspaceFiles/artifacts、主机切换失败回退、批量归档部分失败的真实官方行为。

### 未完成与暂停项

- 阻塞/待协调：
  - 第二轮 review 已对 `9b6ddf9` 返回 3 项 P2（批量进度不可见、主机连接反馈丢失、200 上限静默截断）；修复已 cherry-pick 到跟进分支，待新 PR 复审。
  - P0-2 真实数据需要公共协议/目录扩展：未在本地核对到官方 0.2.1-rc.5 的 `goals/*`、`artifacts`、`workspaceFiles/*` 字段契约（docs 只有方法名）；`RuntimePaths` 也没有向会话管理暴露本地 artifacts 目录。按“不得编造字段”的要求保留“暂不支持”tab，待拿到契约或只读 artifacts 目录后再接。
  - 多远程主机列表需要共享 runtime 类型扩展；当前只支持“本机 + 一个已保存远程”。
- 未完成：批量硬删除（官方无端点）、归档撤销、语言切换、手动主题切换（归通用主题属主）、设备/仪器/assemble 验证、第二轮修复的新 head 复审。
- 外部阻塞：无。
- 用户暂停：API Key、真实模型/账号、真机验收；不合并、不发布。

### 下一步

1. 请编排对跟进 PR #56 的最新 head 独立评审。
2. 等待编排对公共协议扩展的决定：若获批，扩展 `DshSessionAdminSource`/`DshSessionAdminApi`（goals/artifacts/workspaceFiles）并替换 P0-2 的“暂不支持”内容；若未获批，保留如实说明并在 PR 标注边界。
3. 由独立 QA 补设备/真实 DSH 验证；未获合并/发布授权不合并、不发布。
4. 向 android_agent-56 请求通用主题 hook（由 51 接线）；向编排请求 `goals/*`、`artifacts`、`workspaceFiles/*` 字段契约或只读 artifacts 目录。

### 约束与风险

- 数据/设备/权限边界：不读取/输出真实 Key、Cookie、token、会话或备份；不跑设备任务；不合并、不发布；不自动切换 `danger-full-access`。
- 关键决策：
  - 主题撤回后由通用主题属主 56 提供 hook、由 51 接线；本 PR 设置页只显示“跟随系统（手动切换暂不支持）”，不改 MainActivity。
  - 置顶/未读为本机偏好；文件夹分组只读官方 `cwd`，不写回。
  - 批量归档逐个调用官方接口，部分失败不回滚已成功项；硬删除没有端点，只说明不伪造。
  - 主机切换复用共享 `RemoteHostController`；只支持当前一个已保存远程配置，多主机明确列为后续。
  - 详情进度/产物/文件 tab 在协议缺失时只显示“暂不支持”，不造数据。
  - 回退 MainActivity/主题改动，避免违反属主；设置页对未实现能力写“暂不支持”。
- 失败尝试/注意事项：直接 `./gradlew` 被沙箱拒绝写全局缓存，使用 `scripts/ao-worktree-env.sh`；Git 元数据在主仓库，提交/推送需要一次性 wider access。

### 产物与校验

- 新/旧 APK、源码 ZIP：本轮不构建、不更新。
- PR #50：https://github.com/leezzou/tanuki/pull/50 （已由编排合并）；跟进 PR #56：https://github.com/leezzou/tanuki/pull/56 （PR #56 最新 head，CI 通过；待复审；本会话未合并、未发布）。
- 过期记录：未删除历史交接；本记录保留本轮证据。
- 普通 PR 不刷新共享状态/印章；集成维护方批量合并后统一 `--integrate`。

<a id="20261110-1745-native-ui-polish"></a>

## 20261110-1745-native-ui-polish

- Agent/会话标识：android_agent-56（AO worker）
- 时间：2026-11-10 17:45 CST

### 当前状态

- 基线：`origin/main=4f2a3717d6d795fd6b94cdb695878d8119f818c1`（`release: Tanuki 0.1.11 workspacecheck candidate (#44)`）。
- 分支：`ao/native-ui-polish/ui-adaptivity`，从会话分支 `ao/native-ui-polish/root` 创建；提交 `6ef5076` 已推送，PR [#49](https://github.com/leezzou/tanuki/pull/49)。
- 已实现：导航策略、面板栈、二级页状态、导航历史、原生/官方切换状态；适配断点与 inset 骨架；48dp 触控区与无障碍按钮；三态中文组件；WCAG 对比度工具；冷启动预算；对应 JVM 测试与接线文档。
- 已验证：见“验证记录”；设备/模拟器未验证。
- 待验证：页面最终接线后的真实布局、像素、系统栏、输入法和返回栈；设备深色/字体/横屏/360dp。
- 关键事实与可定位源码：[NATIVE-UI-POLISH](NATIVE-UI-POLISH.md)、`app/src/main/kotlin/com/tanuki/ui/TanukiNavigation.kt`、`TanukiLayoutPolicy.kt`、`TanukiAdaptiveComponents.kt`、`TanukiAccessibility.kt`、`TanukiStateViews.kt`、`TanukiContrast.kt`、`TanukiStartupPolicy.kt`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/TanukiNavigation.kt` | 新增返回优先级、面板栈、二级页保留状态、通知/切页历史、原生/官方切换状态；返回上下文含抽屉态与 `CloseDrawer`。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiLayoutPolicy.kt` | 新增 360dp/横屏/宽屏/字体缩放断点、内容最大宽度、48dp 触控区与按钮行数策略。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiAdaptiveComponents.kt` | 新增自动换行操作区、safeDrawing inset 骨架、视口度量，以及按会话的 `TanukiListStateStore` / `TanukiPageStateHost` 与 Bundle Saver。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiAccessibility.kt` | 新增 48dp 触控区扩展、无障碍描述回退、关键按钮/文本按钮/图标按钮组件。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiStateViews.kt` | 新增空态/加载/错误三态组件与中文文案，错误可重试、空态可刷新。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiAdaptiveShell.kt` | 新增宽屏左侧导航+内容、窄屏抽屉外壳，以及宽屏并排/窄屏整页的列表+详情容器；窄屏抽屉宽度钳制在 240–360dp。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiContrast.kt` | 新增 WCAG 相对亮度与 4.5:1/3:1 判定。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiStartupPolicy.kt` | 新增首帧前/首帧后/可交互三档冷启动工作预算。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiListKeys.kt` | 新增会话/消息/工具卡稳定 key 与唯一性断言。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiSaveableState.kt` | 新增原生/官方切换与会话二级页状态的 Bundle Saver。 |
| `app/src/test/kotlin/com/tanuki/TanukiNavigationPolicyTest.kt` | 返回优先级、面板 LIFO、二级页保留列表/滚动、通知返回栈、切换保留状态回归。 |
| `app/src/test/kotlin/com/tanuki/TanukiLayoutPolicyTest.kt` | 320/360/横屏/1280dp、1.3x/2x、触控区与字体钳制回归。 |
| `app/src/test/kotlin/com/tanuki/TanukiStateAndAccessibilityTest.kt` | 三态中文文案、默认动作、描述回退、48dp 下限回归。 |
| `app/src/test/kotlin/com/tanuki/TanukiContrastTest.kt` | 黑白 21:1、阈值边界、明暗主题 8 组关键组合回归。 |
| `app/src/test/kotlin/com/tanuki/TanukiStartupPolicyTest.kt` | 冷启动三档工作预算与单调性回归。 |
| `app/src/test/kotlin/com/tanuki/TanukiListKeysTest.kt` | 稳定 key、冲突、非法 id 与重复 key 回归。 |
| `app/src/test/kotlin/com/tanuki/TanukiSaveableStateTest.kt` | 两个 Saver 的保存/恢复往返回归。 |
| `app/src/test/kotlin/com/tanuki/TanukiListStateStoreTest.kt` | 按会话 A→B→A、Saver 跨 Activity 重建、重建后 LazyListState 位置、负值钳制、清理与空 key 回归。 |
| `docs/NATIVE-UI-POLISH.md` | 新增组件说明、按页接线清单、适配矩阵、无障碍/三态/性能约束与未验证范围。 |
| `docs/NATIVE-UI.md` | 增加指向本次专题文档的入口链接。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:compileModernDebugKotlin` | 通过（1m42s；仅既有弃用/可读性告警） | 编译验证，不替代设备 |
| `./gradlew ... :app:testModernDebugUnitTest --tests 'com.tanuki.Tanuki*'` | 通过：8 个新测试类、56 tests / 0 failures / 0 errors / 0 skipped | 导航 16、布局 9、三态/无障碍 8、对比度 4、冷启动 4、稳定 key 5、Saver 2、滚动 store 8 |
| `./gradlew ... :app:testModernDebugUnitTest :app:lintModernDebug` | 完整套件通过：98 个结果文件、850 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 102 warnings / 1 hint | 850 = 基线 794 + 本轮 56；新增文件无 lint 告警 |
| 最终代码 `./gradlew ... :app:testModernDebugUnitTest :app:lintModernDebug` | 通过（含 lint）：98 个结果文件、850 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 102 warnings / 1 hint | 最终 head 复跑；未执行设备/仪器 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1745-native-ui-polish.md` | 通过 | 共享状态允许落后，集成方统一刷新 |
| `python3 scripts/check-publication.py --git` | 通过：692 public files / 692 index entries / 0 findings | 未打印匹配到的敏感值 |

- 哪些是前一轮结果，哪些是本次新验证：0.1.11 的 794 tests/lint 是上一轮结果，不能算作本轮；本轮只记录本次新跑命令。
- 未测试、超时、失败和暂未复现的情况：设备/模拟器未执行；页面尚未接线，因此真实返回栈、深色/字体/横屏/360dp 像素、系统栏与输入法行为均未验证。

### 未完成与暂停项

- 已修复首轮独立审查两条 P2：滚动状态改为 `TanukiPageStateHost` + `TanukiListStateStore` 显式保存；pending key 接受控制器负序号 `-1/-2`，官方 seq 仍拒绝负值。
- 已修复第二轮复审三条 P2：store 通过 `TanukiListStateStoreSaver` 跨 Activity 重建恢复并持续保存；抽屉打开时详情返回处理器让位且抽屉 handler 最后注册；`TanukiTwoPane` 窄屏应用调用方 `modifier`。
- 未完成：页面接线（`TanukiUi.kt`、`DshNativePage.kt`、`DshSessionManager*`、`DshApproval*`、`MainActivity.kt`）由对应负责人按 [接线清单](NATIVE-UI-POLISH.md#3-接线清单) 完成；本轮不越界修改。
- 未完成：Compose UI 仪器测试与设备适配验证。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；合并/发布未授权。

### 下一步

1. 页面负责人按 [接线清单](NATIVE-UI-POLISH.md#3-接线清单) 接入导航、三态与按钮组件；优先 `DshNativePage.kt` 与 `MainActivity.kt`，再会话管理与审批面板。
2. 独立 QA 在明确 AVD/serial 上验证深色、1.3x/2x 字体、横屏、360dp、状态栏/导航栏/输入法、返回栈与通知进入目标会话；记录 AVD 名、serial、API、ABI、APK 哈希。
3. 页面接线完成后重跑完整 JVM/lint 与设备回归；不要用本轮的策略测试替代设备结论。

### 约束与风险

- 数据/设备/权限边界：未读写真实凭据、Cookie、token、用户会话或备份；未安装 APK、未启动设备、未执行破坏性命令；未改 WebView/协议/官方 npm 包/DSH 数据。
- 关键决策及理由：在“不改页面文件”的边界下，将可验证逻辑抽成纯 Kotlin 策略与通用 Compose 组件，页面只做接线；这样并行 PR 不冲突，且 JVM 可覆盖返回优先级、断点、文案和对比度。
- 失败尝试与避免重复踩坑的结论：`git switch -c` 首次被文件沙箱拒绝（主仓库 refs 在工作区外），已用一次更高权限完成；后续 Git 写操作仍需同样的权限。首次编译前的 `ao-worktree-env.sh` 会复制缓存，耗时约 36 秒，不是构建失败。
- 已知风险：`TanukiStateView` 父级 `contentDescription` 与子按钮语义可能重复朗读，接线时若出现重复播报，改为仅对标题/状态区设置 `liveRegion`；大字体下按钮高度会明显增加，长列表需保留尾部滚动空间。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未生成 APK、未打包源码 ZIP；无发布附件。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布、未合并；PR [#49](https://github.com/leezzou/tanuki/pull/49) 等待独立审查与适用 CI/QA。
- 过期记录清理：本轮未删除任何旧交接；未发现仅属于本次且已过期的记录。`AGENT-STATE.md` 与 `CURRENT.json` 未修改，由集成维护方统一刷新。
- 仍需保留的验证/未完成记录：`docs/handoffs/20261110-1636-release-0.1.11.md`（0.1.11 候选与设备未验）、`docs/handoffs/20261110-1233-native-ui-chat.md`（原生聊天切片）等仍有未完成/设备验证事项，保留。

<a id="20261110-1754-native-approval-ui"></a>

## 20261110-1754-native-approval-ui

- Agent/会话标识：android_agent-53（AO worker）
- 时间：2026-11-10 17:54 CST

### 当前状态

- 已实现：
  - 审批入口：输入框上方 `DshApprovalEntryBar` 窄条“⚑ 待审批 N 项”，点击打开 `DshApprovalSheet` 底部抽屉逐条处理；不再把审批卡散落在消息流。
  - 审批卡：请求来源、工具名、中文风险等级与说明、参数摘要、完整参数折叠、逐条编号、批量说明、高风险允许二次确认、允许/拒绝/记住；未知工具只能拒绝。
  - 本次运行记忆：`DshApprovalMemory.forget` + `DshApprovalController.forgetRemembered`；面板列出记忆并可逐条“撤销”或“全部清除”，撤销立即阻止后续自动回答；未接线时默认不显示单条撤销，避免“点了没用”。
  - 失败/断线：`DshApprovalFailures` 统一中文文案；`DshApprovalState.errorRetryable`；断线/回答失败保留 `pending`，卡片显示“可重试”，通道错误提供“重试连接”。
  - 工具卡：运行/成功/失败、真实耗时、命令与 cwd、文件变更列表、增删行背景色 diff、折叠输出/错误与复制。
  - 计划：`DshPlan`/`DshPlanParser` 保守解析计划事件与计划工具调用；`DshPlanCard` 显示进度条、逐步骤状态、当前步骤和“同意执行/要求修改”按钮；计划工具 callId 可从普通工具时间线剔除。
- 已验证：定向 JVM 5 个测试类共 62 tests / 0 failures / 0 errors / 0 skipped（`DshApprovalEntryTest` 5、`DshPlanTest` 10、`DshToolTimelineTest` 14、`DshApprovalProtocolTest` 16、`DshApprovalControllerTest` 17）；完整 JVM 821 tests / 92 个结果文件 / 0 failures / 0 errors / 0 skipped；lint 0 errors / 102 warnings / 1 hint。
- 待验证：真实设备/PRoot DSH、计划审阅真实事件字段、深色/大字体/长参数/长 diff。
- 分支：`ao/native-approval-ui/root`，基线 `origin/main=4f2a3717d6d795fd6b94cdb695878d8119f818c1`；无运行中安装/设备任务。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshApprovalProtocol.kt` | 解析审批来源短标签；参数摘要；中文失败文案；记忆单条撤销与显示标签。 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalController.kt` | `errorRetryable`、统一失败文案、`forgetRemembered`；断线保留待审批。 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalPanel.kt` | 来源/风险/参数摘要/长参数折叠、批量编号、高风险二次确认、记忆列表与单条撤销、断线中文提示；未接线时隐藏单条撤销。 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalSheet.kt` | 新增审批入口窄条与底部抽屉；窄条只显示数量/高风险/记忆，抽屉复用 `DshApprovalPanel` 逐条处理。 |
| `app/src/main/kotlin/com/tanuki/ui/DshToolCard.kt` | 状态耗时、文件变更、diff 增删行背景色、输出/错误复制、cwd/命令保留。 |
| `app/src/main/kotlin/com/tanuki/web/DshToolTimeline.kt` | `startedAt`/`finishedAt`/`durationMs`；`DshToolDuration` 文案；孤儿结果不伪造耗时；`DshToolMerge` 合并时间戳并提供给 51 替换私有合并。 |
| `app/src/main/kotlin/com/tanuki/web/DshPlan.kt` | 新增计划模型、步骤状态、审阅状态、计划事件/计划工具调用解析、`planCallIds`。 |
| `app/src/main/kotlin/com/tanuki/ui/DshPlanCard.kt` | 新增计划卡片：进度、步骤状态、当前步骤、审阅按钮；不复用普通消息气泡。 |
| `app/src/test/kotlin/com/tanuki/DshApprovalEntryTest.kt` | 新增 5 项窄条计数/高风险/记忆/错误/默认撤销安全策略回归。 |
| `app/src/test/kotlin/com/tanuki/DshPlanTest.kt` | 新增 10 项计划解析/进度/审阅/截断/隐藏步骤/嵌套审阅/非法输入回归。 |
| `app/src/test/kotlin/com/tanuki/DshToolTimelineTest.kt` | 新增 5 项耗时/运行中/孤儿/格式化/分批合并回归。 |
| `app/src/test/kotlin/com/tanuki/DshApprovalProtocolTest.kt` | 新增 4 项来源、参数摘要、中文失败、单条撤销回归。 |
| `app/src/test/kotlin/com/tanuki/DshApprovalControllerTest.kt` | 新增 3 项撤销立即生效、断线保留待审批并重试回答、回答在途 retry 回归；origin 变化补失效 notice。 |
| `docs/NATIVE-UI.md` | 53 组件与集成 hook 清单、文件表、计划边界。 |
| `docs/INTERFACES.md` | 1.9 审批/工具/计划界面契约与缺口。 |
| `docs/NATIVE-PARITY.md` | APPROVAL/TOOL/CHAT 计划相关状态更新为组件就绪/待接线。 |
| `docs/handoffs/20261110-1754-native-approval-ui.md` | 本记录。 |

### 独立 Review 修复

- P1 未接线撤销：`DshApprovalPanel`/`DshApprovalSheet` 的 `onForgetRemembered` 改为可空，默认 null；未提供回调时隐藏单条“撤销”，保留“全部清除”；`DshApprovalActions.canForget` + `DshApprovalEntryTest` 覆盖默认调用安全策略。
- P2 重试遗留 answering：`retry()` 捕获在途 eventId，复位 `answering=false` 并显示“回答已中断，可重试”；旧完成按代次丢弃；新增回答在途直接 retry 的控制器回归。
- P2 实时耗时丢失：新增 `DshToolMerge.merge`，合并时保留 `startedAt`、补 `finishedAt`，迟到 RUNNING 不回退终态；回归覆盖分批 call/result 与迟到 RUNNING。生产 `DshNativeController.mergeTools` 属 51，需 51 替换为 helper（已写入 hook）。
- P2 截断计划误报完成：`DshPlan` 分开保存展示步骤与完整统计（`totalSteps`/`completedSteps`/`currentStepTitle`/`progressUnknown`）；超过扫描上限或损坏项显示“进度未知”，隐藏步骤进行中仍显示当前步骤；新增 21 步回归。
- P2 嵌套审阅状态：先查外层、再查内层显式状态，两层均缺失才应用“待审阅”默认值；新增 `plan/review` 与 `fromReviewPayload` 的嵌套 approved/changes-requested 回归。
- 验收更新：审批入口改为输入框上方窄条 + 底部抽屉；未提供回调时单条撤销隐藏。未放宽为“本会话所有工具自动允许”，仍按会话+工具+风险记忆，等待编排方确认是否扩大授权范围。

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime --tests 'com.tanuki.DshApprovalEntryTest' --tests 'com.tanuki.DshPlanTest' --tests 'com.tanuki.DshToolTimelineTest' --tests 'com.tanuki.DshApprovalProtocolTest' --tests 'com.tanuki.DshApprovalControllerTest'` | BUILD SUCCESSFUL；62 tests / 0 failures / 0 errors / 0 skipped | JDK17/worktree；合成 JSON，不访问真实 DSH |
| `./gradlew :app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL；821 tests / 92 个结果文件 / 0 failures / 0 errors / 0 skipped；lint 0 errors / 102 warnings / 1 hint | 完整 JVM/lint；合成事件，不访问真实 DSH |
| `git diff --check`、`python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1754-native-approval-ui.md`、`python3 scripts/check-publication.py --git` | 通过；678 个公开文件 / 676 个索引项 / 0 findings | 提交前收尾；共享状态允许落后 |
| GitHub Actions PR #47（runs `42801842844`/`42801851968`，head `d41b408`） | Host scripts and handoff checks pass；Android build, JVM tests and lint pass | review 修复后的 CI；最终文档 head 以 PR 为准 |

- 哪些是前一轮结果，哪些是本次新验证：62 项定向与完整 821 tests、lint 均本轮新跑；未复用历史数字。
- 未测试、超时、失败和暂未复现的情况：真实 Android/PRoot DSH、计划审阅真实事件、设备布局/暗色/大字体/长参数/长 diff、仪器/真机、Android assemble 均未执行。

### 未完成与暂停项

- 未完成：等待最终 head 独立复审；`DshNativePage`/`MainActivity` 接线（51 按 NATIVE-UI 的 hook 清单完成）；51 用 `DshToolMerge.merge` 替换私有 `mergeTools`。
- 外部阻塞：无。
- 未纳入：用户提问卡、本会话所有工具自动放行；前者需确认归属与官方回答协议，后者安全影响大，等编排方明确。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收。

### 下一步

1. 已完成完整 JVM/lint、`git diff --check`、`check-handoff.py --pr`、`check-publication.py --git`，并以 leezzou 身份推送 review 修复（`d41b408`）；CI 已通过。
2. 请求/等待最终 head 独立复审；不合并、不发布。
3. 51 按 `docs/NATIVE-UI.md` 第 4 节接入：审批窄条+抽屉、`DshToolMerge.merge`、计划卡 `latest`/`planCallIds`；设备验收另行授权。

### 约束与风险

- 数据/设备/权限边界：不读真实 Key/Cookie/token/会话；不改 DSH 数据/官方 npm；设备 QA 由独立 QA 独占。
- 关键决策及理由：记忆仅本次原生运行内存，不冒充官方持久授权；撤销只影响后续自动回答，不伪造撤回已发出的官方结果；高风险允许二次确认；计划解析只认白名单工具/事件，无法识别时不猜；计划真实审阅 RPC 未新增，留给集成方按官方协议接线。
- 失败尝试与避免重复踩坑的结论：首次编译 `DshPlan` 的 `for` 循环条件不合法，已改为 `0 until minOf(...)`；计划事件 `eventId` 在内层 `event` 对象，测试按此构造。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未生成 APK/源码 ZIP；未安装、未发布。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装；未发布；不合并。
- 提交/推送前清理的过期记录：本轮未删除历史记录；仅新增本记录，结论在 `NATIVE-UI`/`INTERFACES`/`NATIVE-PARITY` 与本记录可定位。
- PR：[https://github.com/leezzou/tanuki/pull/47](https://github.com/leezzou/tanuki/pull/47)，head 以最终推送 SHA 为准。
- 普通 PR 不刷新共享状态/印章；集成维护方在批量合并后统一处理。

<a id="20261110-1802-native-chat-render"></a>

## 20261110-1802-native-chat-render

- Agent/会话标识：android_agent-51（native chat render worker）
- 时间：2026-11-10 18:02 CST

### 当前状态

- PR #47 接线（2026-11-10）：
  - 合并 `ao/native-approval-ui/root`（head `2b919ea`）以取得 53 的组件，未复制/重写其文件；#47 合并后已同步最新 `main`，PR #51 基线回到 `main`，diff 只保留本线文件。
  - `DshNativeController` 改用 `DshToolMerge.merge`，删除私有 `mergeTools`；新增 startedAt/finishedAt 保留回归。
  - `DshNativePage` 改用 `DshApprovalEntryBar` + `DshApprovalSheet`，入口条固定在消息区上方，点击打开底部抽屉；消息流不再直接挂 `DshApprovalPanel`。`MainActivity` 传入 `onForgetRemembered`/`onDismissNotice`。
  - 未接线 `DshPlanCard`/`DshPlanParser`；计划审阅仍待后续集成。
- 复审修复（PR #51 新 thread，2026-11-10）：
  - 重试不再调用无参 `composer.send()` 直接发当前草稿：`DshComposerDraftPolicy` 仅在失败原文未改且失败/当前附件数都为 0 时允许直接重试；已编辑或含附件时禁用并提示回填/重新添加。
  - 失败记录改到 `DshComposerSendRecorder`（包裹 composer remote），在 `session/prompt`/`commands/execute` 提交/返回边界产生 Started/Succeeded/Failed 事件；`MainActivity` 注入，页面直接收集，不依赖重组是否看到 sending 中间帧。
  - 新增“快速失败无 UI 帧”“失败 A→编辑 B 不可重试”“含附件不可直接重试”回归。
  - 复审补充（2026-11-10）：recorder 在 `session/prompt accepted=false`、`commands/execute result.kind=error`、无效响应时记录失败而不是先报成功；失败结果按 sessionId 存 StateFlow，页面切走/重入仍能恢复；取消不记为失败；新增业务拒绝、命令失败、跨会话与取消回归。
- 评审修复（PR #51，2026-11-10）：
  - [P1] 图片采样改为只对原始宽高除以 2 的幂采样，8192×8192→sample 4、32768×32768→sample 16，并补 `DshImageSampling` 边界回归，避免突破 2048 上限。
  - [P2] 待审批时在消息区上方固定审批停靠条（条数/最近工具/查看审批）；点击回到顶部审批面板并暂停自动跟随，长历史初始或新到审批不再被滚出屏幕。
  - [P2] 帧合并显式保留 `Streaming("")` 的新流重置语义；`mergeFrame(reset=true)` 直接替换旧前缀，补跨 start 的控制器回归。
  - [P2] 失败卡片改为观察输入栏真实 `DshComposerController` 的 sending/error，失败保留本次提交原文，重试走 composer；发送中显示 composer 占位。未修改 `DshComposer*` 内部。
- 已实现：
  - 流式：控制器把 `assistant-stream` 累计全文按约 32ms 合并，同一帧只发布最后一版；`Finished(committed)` 同步发布最后一帧；assistant 消息/非 committed 结束取消待发布增量；`DshStreamPolicy.merge` 兼容累计全文、纯增量、重复尾段与空串重置。
  - 长消息/历史：`LazyColumn` 只渲染末尾窗口（80，步进 40，上限 400），更早本地消息可“显示更早”；时间线按 messages/tools 记忆化；单条 Markdown 300 块、代码 400 行、表格 60 行/12 列上限并明确提示；代码/表格超限时复制仍用完整原文。
  - 滚动跟随：用户拖动立即停止跟随，松手/惯性结束后在底部才恢复；自动滚动对齐最后一项底部，长流式回复可看到最新行；离开底部显示“↓ 最新”。
  - Markdown：标题、段落、引用、分隔线、有序/无序/嵌套/任务列表、带对齐表格、围栏代码块、行内粗体/斜体/粗斜体/删除线/行内代码/链接；代码块等宽、横向滚动、`softWrap=false`、可选行号、复制；行内代码与代码块视觉区分。
  - 链接：`http/https/mailto/tel` 点击先确认再调起系统应用；`javascript/data/intent/file/content` 拒绝并显示中文原因；附件/相对路径提示需要工作区授权。
  - 图片/附件：图片占位卡片、中文原因、点击查看；本地/内嵌图片按尺寸采样解码，失败显示“图片加载失败：文件不可读、已损坏或格式不支持”；远程与会话授权图片仍不自动联网。独立附件链接显示占位卡片，可查看/复制地址。
  - 发送状态：发送中占位气泡 + 进度圈；失败保留原文与可读原因，提供“重试/填入输入框/复制内容”；错误 banner 显示中文分类、原始短错误并支持“复制错误”；整条消息可复制。
- 已验证：评审修复 + #47 接线后定向 56 tests（Markdown 15 + 渲染 19 + 控制器 22）；完整 JVM **981 tests / 104 结果文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 103 warnings / 1 hint**；`check-native-parity.py` 通过。详见下方验证记录。
- 待验证：真实 Android 设备/模拟器上的流式帧率、链接与文本选择手势冲突、图片解码内存、长代码/表格横向滚动、深色/大字体、输入法、审批停靠条与 composer 失败卡片；PRoot DSH 实时流；仪器、assemble。
- 关键事实与定位：`DshChatRender.kt` 纯策略；`DshMarkdown.kt` 解析与渲染；`DshNativeController.kt` 流式/失败状态；`DshNativePage.kt` 窗口/跟随/卡片；见 [NATIVE-UI](NATIVE-UI.md) 第 46 节。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/DshChatRender.kt` | 新增纯策略：消息窗口、底部判定/跟随、流式合并/重置帧、图片采样、审批停靠条、composer 失败跟踪、链接/图片/附件分类、错误可读化、代码行号、表格上限。 |
| `app/src/main/kotlin/com/tanuki/ui/DshMarkdown.kt` | 解析器支持嵌套/任务列表、表格对齐与转义、附件块、块上限；渲染器支持代码行号/不换行、链接确认、图片/附件占位与查看器、本地图片有界解码。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 窗口化消息与“显示更早”、底部跟随/“↓ 最新”、`DshApprovalEntryBar`/`DshApprovalSheet` 接线、composer 发送中/失败卡片、失败提交绑定重试、错误复制、消息复制、附件/图片入口。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 传入审批抽屉 `onForgetRemembered`/`onDismissNotice`；不改变服务与聊天协议。 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalSheet.kt` 等 #47 组件 | 随 PR #47 合并引入，未在本次提交重写；51 只做页面接线。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt` | 新增 `DshFailedPrompt` 与 `failedPrompt` 状态、流式按帧合并/取消、失败重试与切会话清理；改用 `DshToolMerge.merge`。 |
| `app/src/test/kotlin/com/tanuki/DshMarkdownTest.kt` | 15 项 Markdown/代码/表格/链接/截断回归。 |
| `app/src/test/kotlin/com/tanuki/DshChatRenderTest.kt` | 新增 16 项纯策略回归：窗口、跟随、流式合并/重置、图片采样、审批停靠条、composer 失败跟踪、链接协议、图片/附件、错误分类。 |
| `app/src/test/kotlin/com/tanuki/DshNativeControllerTest.kt` | 新增流式合并、committed 取消、失败保留/重试、切会话清理；更新原有流式断言为最终帧。 |
| `docs/NATIVE-UI.md` | 新增第 46 节渲染切片、文件表与验证结果；更新图片/附件事件描述。 |
| `docs/NATIVE-PARITY.md` | 更新 CHAT-02/03/04/05/11/12/13/15 的本轮落地状态与边界。 |
| `docs/TEST-REPORT.md` | 新增本轮 JVM/lint 记录。 |
| `docs/handoffs/20261110-1802-native-chat-render.md` | 本记录。 |

- 与 main 对比确认：`DshComposer*` 组件/测试、`web/DshNativeApi.kt`、`MainActivity.kt`、`docs/AGENT-STATE.md`、`docs/handoffs/CURRENT.json` 无改动。

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin -x buildNativeRuntime` | BUILD SUCCESSFUL | 本 worktree、JDK17、Android SDK；仅既有/剪贴板弃用告警。 |
| `./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime --tests 'com.tanuki.DshMarkdownTest' --tests 'com.tanuki.DshChatRenderTest' --tests 'com.tanuki.DshNativeControllerTest'` | BUILD SUCCESSFUL；15 + 19 + 22 = 56 tests / 0 failures / 0 errors / 0 skipped | 纯 JVM；`DshNativeControllerTest` 使用 `Dispatchers.Unconfined` 与合成 source，不连真实 DSH。 |
| `./gradlew :app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL（33m29s）；**981 tests / 104 结果文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 103 warnings / 1 hint** | 同步 #47/#49/#50/#52/#54/#55 合并后的 main 后新跑（`NpmAdaptiveWorkerPoolTest` 首轮 8/9 抖动，定向与完整重跑通过）；lint 仅存量告警，不改产品安全策略。 |
| `python3 scripts/check-native-parity.py` | PASS：166 行、14 功能域、ID/状态/验收/路径检查通过 | 文档结构检查，不证明设备行为。 |
| 设备/仪器/assemble | 未执行 | JVM/lint 不能替代 Android 设备验收；本轮未安装/启动 App。 |

- 首次定向运行 4 项失败：窗口上限测试期望值写错、列表正则捕获组取错、重发成功后仍保留本地“发送中”占位的断言写反；已按实现语义修正并重跑通过。未将失败计为通过。
- 未测试：真实设备/模拟器、PRoot DSH、真实 follow/streaming、图片 content:// 解码、链接调起系统应用、审批停靠条与 composer 失败卡片的设备界面、深色/大字体/输入法、assemble、仪器。

### 未完成与暂停项

- 未完成：PR 创建后的独立 Review（Codex High）与设备 QA 由编排器/独立 QA 安排；当前 head 的 CI 以 PR 页面为准。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号、真机验收；未获合并/发布授权。

### 下一步

1. 已同步 #47 合并后的 `main` 并推送；PR #51 基线为 `main`，diff 只保留 51 的渲染/接线文件。
2. 汇报新 head、完整 JVM/lint、check-handoff/check-publication 结果；不重复回复已 resolve 的 4 条 thread。
3. 复审通过前不再推新提交；未获授权不合并、不发布；设备 QA 由编排器安排。
4. 后续可做：居中锚定滚动/双段列表、输入框上方目标/待办/审批三条停靠条、历史向前分页、远程/授权图片加载、文件链接工作区预览、语法高亮/代码折叠、逐轮失败行与反馈接口。

### 约束与风险

- 不读取/输出真实 Key/Cookie/token/会话；测试只用合成文本、URL 与事件。
- 不修改官方 npm 包/DSH 数据/WebView 安全策略；远程图片不自动联网，危险协议不打开。
- 代码块/表格上限是展示上限，不改变原文与复制内容；更早于已加载页的历史仍依赖官方界面。
- 流式合并只改变发布频率，不改变官方事件语义；committed 消息到达后以落盘消息为准。
- 设备未验证是主要风险：滚动跟随/链接手势/图片解码/长表格横向滚动可能需要在真机调参。

### 产物与校验

- 本轮不构建/安装 APK，不创建 tag/Release，不合并默认分支；不生成源码 ZIP。
- PR：[#51](https://github.com/leezzou/tanuki/pull/51)，head 以最终推送 SHA 为准；当前含 PR #47 合并提交，未合并、未发布。
- 普通 PR 不刷新共享状态/印章；集成维护方在批量合并后统一运行 `--integrate`。

<a id="20261110-1824-text-encoding-fallback"></a>

## 20261110-1824-text-encoding-fallback

- Agent/会话标识：android_agent-57（DSH Worker）
- 时间：2026-11-10 18:24 CST

### 当前状态

- 已实现：BOM/严格 UTF-8/无 BOM UTF-16/GB18030/Big5/Shift_JIS 顺序识别与 Latin-1 兜底；显示解码使用替代字符，不再向界面抛解码异常；二进制专用面板；4 MiB 截断；顶部可关闭编码提示与手动切换；自动识别后跨页沿用具体编码。
- 已验证：`compileModernDebugKotlin` BUILD SUCCESSFUL；定向 JVM 31 tests / 0 failures；完整 `testModernDebugUnitTest` 832 tests / 92 个结果文件 / 0 failures / 0 errors / 0 skipped，`lintModernDebug` 0 errors / 103 warnings / 1 hint。
- 待验证：Android 设备/模拟器打开真实 GBK `.txt`/`.cpp`、二进制伪装文本、4 MiB 大文件、手动切换与深色/大字体下的提示条布局；Android 仪器测试；独立 Codex High Review。
- 当前目标包名：未构建/安装新 APK；源码分支 `ao/text-decoding/text-encoding`（#53，从 `ao/text-decoding/root` 建立）与 `ao/text-decoding/text-encoding-cap`（#55），基线 `4f2a3717`，PR [#53](https://github.com/leezzou/tanuki/pull/53)；#53 已 squash 合并为 `main=00fa462`。4 MiB 多字节边界修复因 #53 已合并，改在最新 `main=26694f4` 上以 follow-up PR [#55](https://github.com/leezzou/tanuki/pull/55) 交付，head `03a8f10`。
- 关键事实与可定位源码：探测/解码在 `app/src/main/kotlin/com/tanuki/storage/TextFileReader.kt`；文本分类与二进制标记传递在 `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt`；提示条/手动切换/二进制面板在 `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt`；4 MiB 截断在 `app/src/main/kotlin/com/tanuki/ui/TextPageLayout.kt`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/TextFileReader.kt` | 重写编码识别：BOM（UTF-8/UTF-16LE/BE/UTF-32）→ UTF-8 严格校验（拒绝 NUL/异常控制字符）→ 无 BOM UTF-16 启发式（0x00 奇偶交错 + 两端序可读性）→ 二进制判定 → GB18030→Big5→Shift_JIS（严格解码 + 候选相关可读性评分）→ Latin-1 兜底；显示解码使用 `CodingErrorAction.REPLACE`，页首不完整字符按 Latin-1 消费保证翻页前进；有界探测样本 8 KiB、单页 64 KiB、显示上限 4 MiB；二进制返回占位编码“二进制”，不进入文本解码；手动 “UTF-16” 先用 BOM/启发式解析具体端序，避免跨页退回大端默认值。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt` | `TextPage` 增加默认 `binary` 字段并透传；未知类型内容探测改为“可文本解码且非二进制”，避免二进制被误判为文本；编辑入口的 UTF-8 限制改为“内置编辑只支持 UTF-8 文本”，不再用“解码失败”阻断阅读。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | 文本查看器改名为 `TextViewerContent`；自动识别后记住并沿用具体编码翻页；顶部新增可关闭提示条“已按 <编码> 显示，如有乱码可切换编码”和“切换编码”；编码菜单改用统一列表并标注 GB18030/UTF-16/Latin-1；二进制内容提前返回专用面板“这是二进制文件”+“用其他应用打开”+“复制路径”。 |
| `app/src/main/kotlin/com/tanuki/ui/TextPageLayout.kt` | 4 MiB 上限引用 `TextFileReader.MAX_VIEW_BYTES` 保持单一来源；截断判定在到达上限且总大小更大或未知时提示。 |
| `app/src/test/kotlin/com/tanuki/TextFileReaderTest.kt` | 更新并扩展回归：GBK 中文跨页、GB18030 生僻字、UTF-16LE/BE 无 BOM（含纯中日韩与手动 UTF-16）、UTF-8/UTF-32 BOM、非法字节 Latin-1 兜底、二进制 PNG/控制字节、手动切换与错选编码不抛异常、Big5/Shift_JIS、4 MiB 上限、原有页边界/UTF-8 BOM/流式窗口用例。 |
| `app/src/test/kotlin/com/tanuki/TextPageLayoutTest.kt` | 截断判定改为按显示字节与总大小判断，覆盖已知超限与未知大小流。 |
| `docs/WORKSPACE-STORAGE.md` | 更新文本查看器编码识别顺序、兜底提示、手动切换、二进制面板和 4 MiB 截断说明。 |
| `docs/TEST-REPORT.md` | 新增本次非设备验证记录与未测试范围。 |
| `docs/handoffs/20261110-1824-text-encoding-fallback.md` | 新增本次唯一交接记录；未改 `AGENT-STATE.md`/`CURRENT.json`。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin -x buildNativeRuntime` | BUILD SUCCESSFUL（JDK17、Android SDK 36、macOS ARM64） | 主源码编译通过；未安装设备。 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.TextFileReaderTest' --tests 'com.tanuki.TextPageLayoutTest' -x buildNativeRuntime` | BUILD SUCCESSFUL，28 tests / 0 failures / 0 errors / 0 skipped | 首次 Big5 用例失败（GB18030 误判），已把候选评分改为按编码区分假名权重并补常用繁体字，复跑通过。 |
| `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest :app:lintModernDebug` | BUILD SUCCESSFUL（5m09s），**802 tests / 90 个结果文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 102 warnings / 1 hint** | 首次交付验证；新增 8 项（794 → 802）。lint warning 数与 0.1.11 基线一致，无新增 error。 |
| Review P2 修复：`READABLE_ENCODINGS` 接受自动探测的 Shift_JIS/UTF-32LE/BE；未知大小流 EOF 后 `hasMore=false` 并返回实际总大小 | 定向 30 tests / 0 failures；完整 JVM 804 tests / 90 文件 / 0 failures / 0 errors / 0 skipped；lint 0 errors / 102 warnings / 1 hint | 新增 Shift_JIS、UTF-32LE/BE 跨页与未知大小空文件/短文件/多页末页回归；未改 content://、TEST-REPORT 或共享状态。 |
| Review P2 修复（4 MiB 多字节边界）：上限切在字符内部时在完整字符处停止，不再把残字节按 Latin-1 显示；截断判定结合 `hasMore` | 定向 31 tests / 0 failures；完整 JVM 832 tests / 92 文件 / 0 failures / 0 errors / 0 skipped；lint 0 errors / 103 warnings / 1 hint；PR #55 四项 CI check 全 pass | 基于最新 `main=26694f4`；新增 UTF-8 三字节字符横跨 4 MiB 边界回归；完整套件首次出现 1 次已知 `NpmAdaptiveWorkerPoolTest` 时序抖动（8/9），单测复跑通过后全套复跑通过。 |
| `git rev-parse origin/main` / `git log --oneline 4f2a371..origin/main` | fetch 后 `origin/main=326726c`，仅 `docs: correct 0.1.11 post-release status (#45)` 的文档改动 | 按用户指定基线 `4f2a3717` 建分支；未合并该文档提交，PR 合并前由集成方决定是否同步。 |

- 哪些是前一轮结果，哪些是本次新验证：上述编译、定向 JVM、完整 JVM/lint 均为本轮新跑；0.1.11 的 794 tests、APK 与设备事实为前轮记录，本轮未复跑。
- 未测试、超时、失败和暂未复现的情况：未在 Android 设备上打开真实用户的 GBK `.txt`/`.cpp` 或二进制伪装文件；提示条/手动菜单在深色、大字体、窄屏下的布局未验证；未知大小 SAF 流和 Android UTF-32 charset 未做设备验证；Big5/Shift_JIS 与 GB18030 字节范围重叠时仍可能误判，需手动切换；`preview()` 编辑入口仍只支持 UTF-8（GBK 等文件可查看但不能内置编辑）。

### 未完成与暂停项

- 未完成：PR 创建后的独立 Codex High Review、Android 设备/模拟器验收、Android 仪器测试。
- 外部阻塞：无。
- 用户暂停：API Key 保存问题继续暂停；合并/发布需另行授权。

### 下一步

1. PR [#53](https://github.com/leezzou/tanuki/pull/53) 的正文已写明识别策略、改动文件、测试命令与未覆盖编码；后续修复按该 PR 的 review/CI 跟进。
2. 独立 Codex High Review 绑定 PR 当前完整 SHA；推送新提交后需重新审查。
3. 设备验证建议：在 ARM64 测试 AVD 打开合成 GBK 中文 `.txt`、GB18030 生僻字、UTF-16LE/BE 无 BOM、UTF-8 BOM、非法字节和二进制 `.bin`；检查提示条关闭/切换编码、二进制按钮、4 MiB 截断和深色/大字体布局；再补 Android 仪器测试。

### 约束与风险

- 数据/设备/权限边界：未连接设备、未安装 APK、未读取真实用户数据；测试文件均为临时合成文本/字节。未改 content:// 直开、工作区读写权限、DSH 运行检查、编辑器 256 KiB 上限或共享状态文件。
- 关键决策及理由：探测只读文件头 8 KiB，避免为识别编码扫描整个文件；分页仍为 64 KiB 单页解码，满足流式读取；显示解码一律使用替代字符，任何非法字节都不会变成异常；GB18030 能接受部分 Big5/Shift_JIS 字节，因此候选评分按编码区分假名权重并结合常用简繁字命中率，仍无法确定时按用户要求顺序取第一个候选并保留手动切换；二进制判定放在 UTF-16 启发式之后，避免把正常 UTF-16 误判为二进制。
- 失败尝试与避免重复踩坑的结论：仅用严格 UTF-8 会把 UTF-16LE ASCII 当成含 NUL 的 UTF-8，必须先做 BOM/UTF-16 启发式；只按“能严格解码”会让 GB18030 抢走 Big5/Shift_JIS，必须比较候选可读性；显示解码不能用 `REPORT`，否则仍会把解码异常抛给界面；未改 content:// 代码，`ContentDocument` 通过 `encoding == "二进制"` 自然进入二进制面板。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未构建 APK、未生成源码 ZIP；PR [#53](https://github.com/leezzou/tanuki/pull/53) 以分支 `ao/text-decoding/text-encoding` 交付，代码提交 `ea60853`。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未覆盖、未签名、未发布、未建 tag/Release。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮未删除既有 `docs/handoffs/` 记录；它们仍承载未合并 PR、待验证设备项或历史发布证据，且不属于本次范围。本次只新增记录并更新 `docs/TEST-REPORT.md` 与 `docs/WORKSPACE-STORAGE.md`。
- 仍需保留的验证/未完成记录：`docs/TEST-REPORT.md` 的 0.1.11 与设备历史记录、各 PR 交接记录。

<a id="20261110-1935-pr47-followup"></a>

## 20261110-1935-pr47-followup

- Agent/会话标识：android_agent-53（AO worker）
- 时间：2026-11-10 19:35 CST

### 当前状态

- 已实现：
  - 审批抽屉滚动容器；到达提示放在滚动内容之外的固定预留槽，出现/消失不改变审批卡位置；数量基线与 3 秒隐藏计时分离，2→3→2 不会永久卡住提示。
  - 计划状态先判断 `currentStepTitle`/`currentStep`，零已完成但首步进行中显示“执行中”；截断统计、嵌套审阅沿用上一轮修复。
  - `DshNativeController.mergeTools` 委托 `DshToolMerge.merge`，实时 follow 分批 call/result 保留 `startedAt`、补 `finishedAt`；新增 controller 级回归。
  - 用户提问：`DshQuestionParser` 支持单选/多选/文本、选项、自定义、必填；`DshQuestionDraft` 为不可变选择，编辑态保留原始空格/换行，必填用 `isBlank`，提交边界才 trim；`DshQuestionCard` 独立呈现，`onSubmit=null` 明示“回答入口待协议确认”。
- 已验证：定向提问/到达提示 12 tests / 0 failures / 0 errors / 0 skipped；完整 JVM 845 tests / 94 个结果文件 / 0 failures / 0 errors / 0 skipped；lint 0 errors / 102 warnings / 1 hint。
- 分支：`ao/native-approval-ui/followup`，基线 `main=26694f487267a774384a5d4e7ff07506f92dd47c`（PR #47 squash 合并）；无运行中安装/设备任务。
- 待验证：真实设备/PRoot DSH、提问真实协议、深色/大字体/长参数/多抽屉、Android assemble/仪器/真机。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalSheet.kt` | 抽屉加滚动容器、新审批轻提示；不自动跳转当前项。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt` | `mergeTools` 委托 `DshToolMerge.merge`，关闭实时耗时丢失；51 复核。 |
| `app/src/main/kotlin/com/tanuki/web/DshPlan.kt` | 状态判断优先识别进行中步骤。 |
| `app/src/main/kotlin/com/tanuki/web/DshQuestion.kt` | 新增提问模型、解析、不可变草稿、必填校验。 |
| `app/src/main/kotlin/com/tanuki/ui/DshQuestionCard.kt` | 新增独立提问卡；协议未确认时 `onSubmit=null` 只展示本地选择。 |
| `app/src/test/kotlin/com/tanuki/DshNativeControllerTest.kt` | 新增分批 call/result 耗时合并回归；51 复核。 |
| `app/src/test/kotlin/com/tanuki/DshPlanTest.kt` | 新增首步进行中（零完成）状态回归。 |
| `app/src/test/kotlin/com/tanuki/DshQuestionTest.kt` | 新增提问单选/多选/文本/可选/非法/单对象/逐字符空格换行回归。 |
| `app/src/test/kotlin/com/tanuki/DshApprovalArrivalTest.kt` | 新增到达提示增量、2→3→2、重新计时、无新增不显示回归。 |
| `docs/NATIVE-UI.md` | 抽屉滚动/提示、提问卡 hook、`DshToolMerge` 接线说明。 |
| `docs/INTERFACES.md` | 提问解析/回答入口待确认、计划进度无关。 |
| `docs/NATIVE-PARITY.md` | APPROVAL-05 提问组件状态更新。 |
| `docs/handoffs/20261110-1935-pr47-followup.md` | 本记录。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| 定向 `DshQuestionTest` + `DshApprovalArrivalTest` / 完整 7 类 JVM | BUILD SUCCESSFUL；提问 8 + 到达提示 4 tests、7 类合计 92 tests / 0 failures / 0 errors / 0 skipped | JDK17/worktree；合成 JSON，不访问真实 DSH |
| 完整 `:app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL；845 tests / 94 结果文件 / 0 失败；lint 0 errors / 102 warnings / 1 hint | 完整 JVM/lint；未跑设备 |
| `git diff --check`、`check-native-parity.py`、`check-handoff.py --pr`、`check-publication.py --git` | 通过；681 公开文件 / 678 索引项 / 0 findings | 提交前检查 |
| GitHub Actions | 待推送后运行 | 本 follow-up 尚无 CI |

- 哪些是前一轮结果，哪些是本次新验证：以上 92/845 与 lint 为本轮新跑；PR #47 的 810/821 结果已并入 main，不重复计算。
- 未测试、超时、失败和暂未复现：真实 Android/PRoot DSH、提问回答协议、设备布局/暗色/大字体/长参数/长抽屉、assemble、仪器/真机均未执行。

### 未完成与暂停项

- 未完成：推送草稿编辑/到达提示修复到 PR #54、等待新 head CI/复审。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收。
- 未纳入：官方提问回答 RPC；「本对话都允许」保持会话+同工具+同级风险，不放宽到所有工具。

### 下一步

1. 以 leezzou 身份推送 follow-up 分支并创建 PR；回报 head。
2. 等待 CI 与独立复审；不合并、不发布。
3. 51 按 `docs/NATIVE-UI.md` 接入审批窄条/抽屉、计划卡、提问卡；复核 `DshNativeController.mergeTools` 的委托改动。

### 约束与风险

- 数据/设备/权限边界：不读真实 Key/Cookie/token/会话；不改 DSH 数据/官方 npm；设备 QA 由独立 QA 独占。
- 关键决策：不重写已合并历史；从 main 新建 follow-up，只 cherry-pick 未合并的修复。提问卡回答协议不确定时不伪造提交；审批记忆保持窄口径。
- 失败/踩坑：PR #47 在最后一轮修复推送前已被 squash 合并，原分支被删除；重新推送会重建分支但不属于已合并 PR，因此另开 follow-up。

### 产物与校验

- 未生成 APK/源码 ZIP；未安装、未发布、未合并。
- 普通 PR 不刷新 `AGENT-STATE.md`/`CURRENT.json`/`TEST-REPORT.md`；只新增本记录。
- follow-up PR 待创建，链接以最终回报为准。

<a id="20261110-1936-files-layout"></a>

## 20261110-1936-files-layout

- Agent/会话标识：android_agent-58
- 时间：2026-11-10 19:36 CST

### 当前状态

- 已实现：`WorkspaceFilesPage` 新布局与权限/传输 sheet；`OperationBanner` 在文件页隐藏文件传输项；`MainActivity` 注入传输流与取消回调；新增 `FileAccessPresentation`、`FileTransferPresentation` 与 10 项 JVM 回归。
- 已验证：Modern Debug Kotlin 编译；定向 JVM；完整 JVM 944 tests / 103 个结果文件 / 0 failures / 0 errors / 0 skipped；lint 0 errors / 104 warnings。
- 待验证：模拟器/真机上的深色模式、大字体、360dp 窄屏、触控区实际尺寸、列表滚动锚定、底部 sheet 手势、TalkBack；Android 仪器测试；独立 Review。
- 当前分支：`ao/files-layout/info-hierarchy`，起点 `1122d3fc79a44e9ac8440794ccc71140054af6aa`，已 rebase 到最新 `origin/main=811b5d292643d504f0073cc3832cb4a90b870b64`；目标默认分支 `main`。PR [#58](https://github.com/leezzou/tanuki/pull/58)，不合并、不发布。本地未安装 APK，无运行中的设备任务。
- 关键源码：`app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt`、`FileAccessPresentation.kt`、`FileTransferPresentation.kt`、`OperationProgress.kt`、`TanukiUi.kt`、`MainActivity.kt`；规格背景见 [原生界面行为规格](NATIVE-UI-BEHAVIOR-SPEC.md)，文件页行为说明见 [项目空间与容量预算](WORKSPACE-STORAGE.md)。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt` | 顶部收成 56dp 单行：位置、搜索/刷新/排序/更多图标、最右权限 chip；第二行改为搜索/选择/可横滚面包屑三态；移除大块权限卡、运行提示卡、4dp 加载槽、计数行、行内错误/反馈块；列表满高并固定底部避让；新增权限 sheet、传输胶囊、传输 sheet、Snackbar host；传输订阅收进独立 overlay；查看器两个早返回分支也挂载浮层，导出/复制后仍可看进度和取消；选择态优先于搜索行并保留 query，全选只作用于匹配项。 |
| `app/src/main/kotlin/com/tanuki/ui/FileAccessPresentation.kt` | 新增权限只读/可写状态、chip 文案、sheet 说明与可执行操作（手机存储授权、写入授权/撤销、停止 DSH）的纯逻辑。 |
| `app/src/main/kotlin/com/tanuki/ui/FileTransferPresentation.kt` | 新增传输百分比、字节加权汇总、二进制大小文本、剩余时间估算与中文时长的纯逻辑；未知总量返回 null，不估造百分比。 |
| `app/src/main/kotlin/com/tanuki/ui/OperationProgress.kt` | `OperationBanner` 增加 `showFileTransfers` 参数；文件页只保留运行时操作，传输进度交给页面内浮层，避免重复占高。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 文件 Tab 调用 `OperationBanner` 时关闭文件传输项。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 向 `WorkspaceFilesPage` 注入 `manager.fileTransfers.state` 与取消回调。 |
| `app/src/test/kotlin/com/tanuki/FileAccessAndTransferPresentationTest.kt` | 新增 10 项 JVM 回归：权限状态、手机存储/写入授权、只读位置、百分比边界、未知总量、加权汇总、大小文本、剩余时间与时长。 |
| `app/src/androidTest/kotlin/com/tanuki/FilesBrowserPresentationTest.kt` | 新增两条 UI 回归：查看器长传输浮层可见/可取消；搜索态选择保留筛选且全选只作用于匹配项。本轮仅编译，无设备未运行。 |
| `docs/WORKSPACE-STORAGE.md` | 新增“文件页信息层级”说明，明确新布局、收进 sheet 的块、列表避让和未验证设备项。 |
| `docs/NATIVE-PARITY.md` | 更新 FILE-04 行为描述为可横滚面包屑与 56dp 主操作行。 |
| `docs/TEST-REPORT.md` | 新增本轮非设备验证记录与未测试范围。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:compileModernDebugKotlin` | 通过（rebase 后包含在完整任务中） | JDK17、Android SDK、macOS ARM64；只证明编译 |
| `... :app:testModernDebugUnitTest --tests 'com.tanuki.FileAccessPresentationTest' --tests 'com.tanuki.FileTransferPresentationTest' --tests 'com.tanuki.FileListPresentationTest' --tests 'com.tanuki.OperationProgressTest'` | 通过 | 定向状态/格式化/列表稳定性回归 |
| `... :app:testModernDebugUnitTest :app:lintModernDebug` | 通过（rebase 后 26m44s） | 944 tests / 103 个结果文件 / 0 failures / 0 errors / 0 skipped；lint 0 errors / 104 warnings |
| `... :app:compileModernDebugAndroidTestKotlin` | 通过（rebase 后 28m16s） | 追加参数未破坏既有仪器测试源码；仅编译，未在设备运行 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1936-files-layout.md` | 通过 | 普通 PR 只校验本记录，共享状态允许落后 |
| `python3 scripts/check-publication.py --git` | 通过，0 findings | 检查公开文件、链接与敏感内容 |

- 本轮新验证：上述编译、定向 JVM、完整 JVM、lint；rebase 到最新 main 后重新执行完整 JVM/lint 与 Android 仪器测试源码编译。复审 P2 修复后重新编译主代码与仪器测试源码并跑 lint（6m07s，0 errors / 104 warnings）；查看器传输与搜索态选择两条 UI 用例因无可用设备未运行。
- 失败与修正：首次定向测试 2 项失败，分别为剩余时间把毫秒当秒返回、测试把已接收总量/任务数写错；已修正 `remainingSeconds` 的单位与断言后复跑通过。rebase 到最新 main 时仅 `docs/TEST-REPORT.md` 与 `docs/WORKSPACE-STORAGE.md` 冲突，保留本 PR 新章节并采用 main 更新后的文本编码段落。
- 未测试：所有设备/模拟器视觉、手势、滚动、深色、大字体、360dp、TalkBack；新增 `viewerKeepsTransferPillAndCancelAvailable`、`searchSelectionKeepsBatchActionsAndSelectsOnlyMatches` 仅编译，无可用设备未运行；未跑 Android 仪器测试、assemble、独立 Review、合并或发布。

### 未完成与暂停项

- 未完成：设备 QA、独立高能力模型审查、PR 审查意见处理（若有）、GitHub CI 结果核对。
- 外部阻塞：设备由独立 QA 独占，本会话未申请也未运行模拟器/真机。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收、合并与发布；本记录不触碰这些暂停项。

### 下一步

1. PR [#58](https://github.com/leezzou/tanuki/pull/58) 已创建；等待 GitHub CI 与独立审查，PR 正文已写明 944 tests/lint 结果、未验证设备和“不合并、不发布”。
2. 由独立 Reviewer 对当前 head 做独立审查；由独立 QA 在模拟器/真机按 360dp、大字体、深色模式检查顶部不重叠、权限 sheet、传输浮层、滚动锚定和 48dp 触控区。
3. 处理 review comment 后推送新 commit；最新 SHA 重新验证，旧审查结论不沿用。

### 约束与风险

- 数据/设备/权限边界：只改原生 UI 与展示模型；未改变文件写入授权、PhoneFileService 权限代次、DSH 运行期只读策略或网络行为。
- 关键决策：权限块和传输块都改为“状态入口 + 底部 sheet”，列表页不再常驻大块说明；传输浮层独立订阅进度，页面主体不随每个进度帧重组；列表底部固定预留 72dp 避让，避免传输出现/消失时改变列表测量。
- 已知限制：查看器内浮层固定在右下角，可能与查看器底部工具栏接近；窄屏/大字体下的实际间距未在设备上确认。
- 失败尝试：无设备失败记录；未用宿主结果冒充 Android 执行。

### 产物与校验

- 本轮未生成 APK、源码 ZIP 或发布附件；不涉及覆盖安装、签名或发布。
- 未删除过期交接记录；本轮只新增 `20261110-1936-files-layout.md`，未修改共享状态或印章。
- 提交前运行 `check-handoff.py --pr` 与 `check-publication.py --git`；集成维护方在批量合并后统一运行 `--integrate`。文档变化会改变公开源码指纹，但本轮不生成/分发源码 ZIP。

<a id="20261111-0314-goal-todo-dock"></a>

## 20261111-0314-goal-todo-dock

- Agent/会话标识：android_agent-63（AO worker）
- 时间：2026-11-11 03:14 UTC / 11:14 CST

### 当前状态

- 已实现并验证：`DshDock.kt`、`DshGoalDock.kt`、`DshTodoDock.kt` 三个新文件，以及 `DshDockLayoutTest`、`DshGoalDockTest`、`DshTodoDockTest` 三个 JVM 测试类；修复前定向 23 tests 与完整 1070 tests 均通过，lint 0 errors。
- 复审修复（PR #68 discussion r4533863153）：原实现展开“+1”时同时遍历 `visible` 与 `folded`，同一待办条会被渲染两份。现改为唯一渲染来源 `DshDockRenderPlan.bars`，组件按 `DshDockKind.entries` 每种只遍历一次；“+N”展开只把折叠项并入该计划，不再二次渲染。隐藏槽位保持 0 高度，条间距改放在可见条内部，避免不可见槽位额外占用版面。补 3 项渲染计划回归：展开后待办恰好一个、收起后待办槽隐藏、所有组合均无重复。
- 未接线：`DshNativePage` 仍直接挂旧审批条；新组件尚未出现在真实聊天页。需要 android_agent-62 按第 6 节 hook 清单接线后才能看到界面。
- 当前分支：`ao/goal-todo-dock/dock-bars`，基线 `origin/main=62e4c30b4a61636867cfc94736e44fa159f50b9f`；初始实现 `25abc96`，复审修复提交以 PR #68 当前 head 为准；PR [#68](https://github.com/leezzou/tanuki/pull/68)，未合并。
- 无运行中设备/安装任务；未执行任何模拟器或真机操作。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/DshDock.kt` | 新增 `DshDockKind` 优先级枚举、`DshDockLayout` 纯折叠策略、`DshDockMotion` 200–300ms 高度动画常量、`DshDockColumn` 停靠容器与“+N”折叠入口。审批条通过插槽传入，不复制、不改写审批条实现。 |
| `app/src/main/kotlin/com/tanuki/ui/DshGoalDock.kt` | 新增 `GoalDockState`/`GoalDockStatus` 纯状态、`DshGoalCapabilities`/`DshGoalAction`/`DshGoalActionAvailability` 能力策略，以及 `DshGoalEntryBar`：单行摘要、状态胶囊、受阻警示色、原地展开操作面板、禁用原因说明。 |
| `app/src/main/kotlin/com/tanuki/ui/DshTodoDock.kt` | 新增 `TodoDockState`/`TodoDockItem`/`TodoDockItemStatus` 纯状态、`TodoDockLayout` 30% 高度策略、`TodoDockScrollPolicy` 定位策略，以及 `DshTodoEntryBar`：完成数/当前项、细进度条、原地清单、完成项淡化、进行中加粗、空清单整条隐藏。 |
| `app/src/test/kotlin/com/tanuki/DshDockLayoutTest.kt` | 11 项折叠/优先级/展开/动画时长回归；新增 3 项渲染计划唯一性、展开后待办单实例、收起隐藏回归。 |
| `app/src/test/kotlin/com/tanuki/DshGoalDockTest.kt` | 7 项目标状态、单行摘要、能力禁用与状态互斥回归。 |
| `app/src/test/kotlin/com/tanuki/DshTodoDockTest.kt` | 8 项进度、空态、当前项、展开滚动定位、30% 高度回归。 |
| `docs/handoffs/20261111-0314-goal-todo-dock.md` | 本记录与 hook 清单。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git rev-parse origin/main` | `62e4c30b4a61636867cfc94736e44fa159f50b9f` | 基线核对；分支从该 SHA 建立 |
| `source scripts/ao-worktree-env.sh && ./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest --tests 'com.tanuki.DshDockLayoutTest' --tests 'com.tanuki.DshGoalDockTest' --tests 'com.tanuki.DshTodoDockTest'` | 修复前通过（6m 41s，23 tests）；复审修复后通过（1m 25s，26 tests） | 修复后 **26 tests / 0 failures / 0 errors / 0 skipped**：`DshDockLayoutTest` 11、`DshGoalDockTest` 7、`DshTodoDockTest` 8；JDK17/worktree；不访问真实 DSH |
| `source scripts/ao-worktree-env.sh && ./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest :app:lintModernDebug` | 修复前通过（4m 45s，1070 tests）；复审修复后首次全量出现 1 个既有 `NpmAdaptiveWorkerPoolTest` 时序失败；该用例单独复跑通过后，全量重跑通过（1m 58s；间距收尾后再次全量通过 3m 16s） | 修复后完整 JVM **1073 tests / 111 个结果文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 104 warnings**；新文件无 lint 告警。`NpmAdaptiveWorkerPoolTest` 的失败为历史已记录的并发时序抖动，与本次停靠条改动无关（JDK17/worktree，未跑设备） |
| `source scripts/ao-worktree-env.sh && ./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest --tests 'com.tanuki.NpmAdaptiveWorkerPoolTest'` | 通过（BUILD SUCCESSFUL in 11s） | 单用例复跑通过，确认首次全量为时序抖动而非稳定失败 |

- 未测试：Compose 组件在设备上的实际高度动画、30% 高度在横屏/分屏/大字体下的表现、展开自动滚动的触控体验、TalkBack、深色主题；未安装 APK，未跑 Android 仪器测试。
- 编译/JVM/lint 不能替代设备验收；本轮没有真实目标/待办协议数据。

### 未完成与暂停项

- 未完成：android_agent-62 在 `DshNativePage` 接线；真实 `goals/*` 与待办投影/事件映射；设备 UI 验收。
- 外部阻塞：无。
- 用户暂停：合并、发布、真实模型/账号验收仍按当前状态文档保留暂停，本 PR 不合并、不发布。

### 6. 给 android_agent-62 的 hook 清单

> 本节只描述接线契约；本 PR 不修改下列文件。

#### 6.1 挂载位置

1. `DshNativePage.kt` 的 `Column` 内，保持“消息区 `Box(Modifier.weight(1f)) { MessageList(...) }` → 停靠条 → `DshComposerBar(...)`”的顺序。
2. 删除当前直接调用 `DshApprovalEntryBar(...)` 的那一处（约在 `SessionStrip` 之后、消息 `Box` 之前），把同一个审批条移入 `DshDockColumn` 的 `approvalBar` 插槽；不要同时保留两处，否则会出现两条审批条。
3. 推荐新增参数（全部带默认值，旧调用可编译）：
   ```kotlin
   goalState: GoalDockState? = null,
   todoState: TodoDockState? = null,
   goalCapabilities: DshGoalCapabilities = DshGoalCapabilities(),
   onGoalAction: (DshGoalAction) -> Unit = {},
   onTodoExpandedChange: (Boolean) -> Unit = {}
   ```
4. 调用示例：
   ```kotlin
   DshDockColumn(
       approvalVisible = DshApprovalEntry.of(approvalState, state.selectedId).visible,
       goal = goalState,
       todo = todoState,
       approvalBar = {
           DshApprovalEntryBar(
               state = approvalState,
               sessionId = state.selectedId,
               onClick = { showApprovalSheet = true },
               modifier = Modifier.fillMaxWidth()
           )
       },
       modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
       goalCapabilities = goalCapabilities,
       onGoalAction = onGoalAction,
       onTodoExpandedChange = onTodoExpandedChange
   )
   ```
   注意：横向间距放在 `DshDockColumn` 的 `modifier` 上；`approvalBar` 内部不要再加横向 padding，否则三条条宽会不一致。
5. 不要把 `DshDockColumn` 再包进 `animateContentSize()` 或 `AnimatedVisibility`；高度动画由组件内每条停靠条负责，外层再动画会叠加时长并推动消息区跳动。
6. `DshApprovalSheet` 保持现有位置和调用不变，只由审批条点击打开。

#### 6.2 数据来源

- `GoalDockState(summary, status, detail)`：
  - `summary`：目标一句话摘要，建议截断前由数据层压成单行；不要把完整目标正文、原始事件或凭据塞入。
  - `status`：映射官方目标状态到 `RUNNING / PAUSED / BLOCKED`；无法判断时不要猜，保持上次已知状态或整条隐藏。
  - `detail`：可选短说明（例如受阻原因），为空时组件显示内置状态提示。
  - 没有目标时传 `null` 或 `GoalDockState()`；`summary` 为空白时整条隐藏。
- `TodoDockState(items)`：
  - 每个 `TodoDockItem(id, text, status)`；`id` 稳定且唯一优先，重复时列表会退回下标键。
  - 进度只用 `COMPLETED / total`；不要把 `IN_PROGRESS` 折算成小数进度。
  - 清单为空传 `TodoDockState()` 或 `null`，整条不占高度。
- `DshGoalCapabilities`：
  - 官方协议具备目标读取/暂停/恢复/编辑/清除通道，但当前原生控制器尚未接入 `goals/*`；**在真正接线前保持默认全 false**，界面会显示禁用按钮和“本会话尚未接入官方目标操作通道”。
  - 接入后按实际可用的 RPC 传 `canPause/canResume/canEdit/canClear`；不要在 RPC 未实现时传 true，避免死按钮。
- `DshGoalAction` 回调：
  - `PAUSE/RESUME/CLEAR` 派发到对应官方方法，成功后由状态回读更新 `GoalDockState`，组件不本地乐观改状态。
  - `EDIT` 由页面打开官方目标编辑入口或编辑对话框；组件只发出意图，不直接改目标文本。

#### 6.3 审批条兼容

- `DshApprovalEntryBar` 的签名与行为未改；`DshDockColumn` 只把它当作普通插槽内容。
- `approvalVisible` 必须与审批条自身是否可见一致，推荐直接用 `DshApprovalEntry.of(approvalState, selectedId).visible`；若传 true 但审批条内部返回空，会白占一个停靠槽。
- 审批 > 目标 > 待办；三者同时存在时默认显示审批+目标，待办折叠为“+1”。用户点“+1”后临时铺开折叠项，按钮变“收起”。

### 下一步

1. android_agent-62 按第 6 节接线，并在接线后跑一次完整 JVM + lint。
2. 设备验收：三条条同时存在/两条/一条/零条；目标受阻警示色；目标操作禁用说明；待办空态隐藏、3/7 文案、展开 30% 上限、内部滚动与自动定位当前项；深色、大字体、横屏/分屏、TalkBack。
3. 本 PR 只等待 review，不合并、不发布。

### 约束与风险

- 数据/设备/权限边界：组件只接收纯状态，不解析协议、不读凭据、不访问网络；本轮未操作设备。
- 关键决策：
  - 审批条不重写，作为插槽复用，避免与现有审批抽屉/状态机分叉。
  - “+N”默认遵守同屏最多两条；点开后是用户主动临时展开，折叠计数仍保留，便于收起。
  - 待办进度不把进行中折算完成度；空清单不占位。
  - 目标能力默认全 false；官方通道存在但原生未接线时明确禁用，不伪造可操作。
  - 动画固定 240ms，落在要求的 200–300ms；系统移除动画时为 0。
  - 渲染只使用 `DshDockRenderPlan.bars` 一个列表，折叠项不再单独遍历，从结构上杜绝展开时同一停靠条出现两份。
  - 间距不依赖 `Column.spacedBy`，改放在可见条内部；隐藏槽位不产生空隙。
- 失败尝试：无；定向与完整构建均一次通过，没有需要规避的失败路径。
- 已知风险：Compose 高度动画和 30% 高度需要在真实窗口/分屏下验收；当前测试只锁定纯策略，不能证明视觉效果。

### 产物与校验

- 无 APK、源码 ZIP 或发布附件；PR [#68](https://github.com/leezzou/tanuki/pull/68) 已创建，未合并、未发布。
- CI push 事件按本次 push 的 base 计算变更集，因此仅改源码/注释的后续提交也必须同时更新本交接记录；本轮已按此规则补记，避免 Host 的 handoff 门禁误判。
- 普通 PR 不刷新共享状态/印章；集成维护方在批量合并后统一运行 `--integrate`。
- 结束时已按本 PR 范围检查交接：只新增本记录，未改 `AGENT-STATE.md`/`CURRENT.json`/`TEST-REPORT.md`；未发现本 PR 范围内可清理的过期交接记录。

<a id="20261111-0325-release-0.1.12"></a>

## 20261111-0325-release-0.1.12

- Agent/会话标识：android_agent-60（AO Worker）
- 时间：2026-11-11 03:25 CST（开始），2026-11-11 最终候选

### 当前状态

- 已实现：版本默认值 0.1.12/code14；六份版本文档与发布交接同步到最终事实；rebase 到 `origin/main=f338bdb`（含 #48/#51/#57/#58/#59）；解决 `TEST-REPORT` 冲突时保留 main 的新章节和本版 0.1.12 章节；`CURRENT.json` 按发布维护流程刷新印章。
- 已验证：完整 JVM **1044 tests / 107 个结果文件 / 0 failures / 0 errors / 0 skipped**；`lintModernDebug` **0 errors / 101 warnings / 1 hint**；`check-handoff --pr`、`check-publication --git`、`package-source`、`prepare-app-release` 与六附件校验见下表；APK 实核 `com.tanuki.modern.workspacecheck`、0.1.12/code14、minSdk26、证书 `371dc301...`、**12,295,334 字节**、SHA-256 `27580e6d4c2234466b026cd46c65a3c01794a327c717acc1218cb19ca6949a02`。
- 待验证/待完成：设备/模拟器安装与升级覆盖、真实 PRoot DSH、真机 PRoot、真实 sshd/中继、弱网；PR 独立审查与用户合并/发布。
- 当前分支/发布：`ao/release/0.1.12`，基于最终 `main=f338bdb`；PR [#61](https://github.com/leezzou/tanuki/pull/61) 已创建，待合并。未创建 tag/Release、未上传附件。
- 关键事实：0.1.11/code13 仍是当前已发布 Pre-release；0.1.12/code14 只是本地/待审查的 workspacecheck 候选，不能宣称已发布或已通过设备验收。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/build.gradle.kts` | 默认 `versionName=0.1.12`、`versionCode=14`，与最终发布参数一致。 |
| `README.md` | 增加当前开发候选 0.1.12（workspacecheck，code14）说明。 |
| `docs/AGENT-STATE.md` | 当前指针与最近 5 条更新为最终 `main=f338bdb`、1044 tests、0.1.12 候选与 PR 待合并状态。 |
| `docs/TEST-REPORT.md` | 保留 main 新增的文件页章节，新增并更新 0.1.12 发布准备验证：1044 tests、lint 与最终 APK 事实。 |
| `docs/RELEASE.md` | 当前候选、发布命令、输出目录与附件名改为最终 0.1.12/code14；保留 0.1.11/0.1.9/0.1.10 历史。 |
| `docs/ARTIFACTS.md` | `artifacts/releases/0.1.12/` 候选目录与最终 APK SHA/大小；保留 0.1.11 已发布目录。 |
| `docs/APP-UPDATES.md` | 应用内更新命令、目录、附件名与最终事实改为 0.1.12；保留 0.1.11 发布事实。 |
| `docs/handoffs/20261111-0325-release-0.1.12.md` | 本记录。 |
| `docs/handoffs/CURRENT.json` | 由 `check-handoff.py --integrate` 刷新的 schema 2 印章；供发布脚本严格校验，不手写。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `./gradlew -PtestBuildType=debug -PtanukiVersionName=0.1.12 -PtanukiVersionCode=14 -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest :app:lintModernDebug` | BUILD SUCCESSFUL（45m38s）；**1044 tests / 107 结果文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 101 warnings / 1 hint** | JDK17、Android SDK、macOS ARM64；`-x buildNativeRuntime`；不含设备/仪器 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-0325-release-0.1.12.md` | 通过 | 普通 PR 模式校验本记录结构、占位符与未测声明 |
| `python3 scripts/check-publication.py --git` | 通过，0 findings | 公开边界、凭据模式、断链与实际索引；不打开被忽略凭据 |
| `python3 scripts/package-source.py` | 通过，生成最终 `artifacts/sources/Tanuki-0.1.12-source.zip` 与 `SOURCE-SHA256SUMS` | 最终源码 ZIP 以发布目录副本及 `RELEASE-SHA256SUMS` 为准 |
| `python3 scripts/prepare-app-release.py --version 0.1.12 --version-code 14 --channel workspacecheck` | 通过，生成 `artifacts/releases/0.1.12/` 六附件 | APK 包名/版本/code/minSdk/证书/ARM64 原生库实核；12,295,334 字节、SHA-256 `27580e6d...949a02` |
| `(cd artifacts/releases/0.1.12 && shasum -a 256 -c RELEASE-SHA256SUMS)` | 通过 | 六附件逐项校验 |

- 哪些是前一轮结果，哪些是本次新验证：上表 JVM/lint、检查、APK 与附件均为 rebase 到最终 main 后新验证；最初 557f8a06 的 951 tests / 12,120,954 字节候选已作废，不冒充最终结果；0.1.11 的 794 tests 与更早附件均为历史。
- 未测试、超时、失败和暂未复现的情况：设备/模拟器安装与升级覆盖、真实 PRoot DSH、真机 PRoot、真实 sshd/中继、弱网/断线未执行；未运行 Android 仪器测试、`assembleModernDebug`。rebase 时 `docs/TEST-REPORT.md` 有内容冲突，已保留 main 新章节与本版章节后继续，未改产品代码。

### 未完成与暂停项

- 未完成：发布 PR 待创建/合并；独立 Codex High 审查最终完整 SHA；用户合并与发布授权。
- 未验证：设备/模拟器安装与升级覆盖、真实 PRoot DSH 聊天/审批/会话、真机 PRoot、真实 sshd/中继、弱网/断线、深色/大字体/低 API。
- 外部阻塞/用户暂停：API Key 保存、真实模型/账号、真机验收继续暂停；未获合并/发布授权不得合并、打 tag 或上传 Release。
- 发布动作：未合并、未创建 tag/Release、未上传附件。

### 下一步

1. push `ao/release/0.1.12`，创建标题 `release: 0.1.12` 的 PR（不合并），正文写明版本、测试、未验证项与不自行合并。
2. 由独立 Codex High 对 PR 当前完整 SHA 审查；如补丁改变产品字节，必须重跑 JVM/lint 并重建 APK/六附件。
3. 用户合并并明确授权后，才创建 `v0.1.12` tag/Pre-release 并上传 `artifacts/releases/0.1.12/` 六附件；当前不执行。

### 约束与风险

- 数据/设备/权限边界：不自行合并、不打 tag、不上传 Release；不启动设备；不读取/输出真实凭据；不关闭审批/TLS/校验；文档与注释不出现外部参考实现名称。
- 关键决策及理由：以最终 `origin/main=f338bdb` 重建，旧 557f8a06 候选作废；`AGENT-STATE`/`CURRENT.json` 按发布维护流程更新并由 `--integrate` 刷新，保证 `prepare-app-release.py` 的 `--release` 严格校验通过。
- 签名/升级风险：沿用 workspacecheck 开发证书（证书 SHA-256 前缀 `371dc301`），与 0.1.11 同证书才能覆盖安装；APK 未安装、未做升级覆盖验证。
- 失败尝试与避免重复踩坑：初次原生源码打包因复用的 PRoot 源码树缺少 `CLAUDE.md` 符号链接失败，已恢复链接后重新打包；未修改产品代码规避。

### 产物与校验

- 新 APK/源码：`artifacts/releases/0.1.12/` 六个附件——`Tanuki-0.1.12-workspacecheck-arm64-release.apk`（12,295,334 字节，SHA-256 `27580e6d4c2234466b026cd46c65a3c01794a327c717acc1218cb19ca6949a02`）、`app-update.json`、`Tanuki-0.1.12-source.zip`、`Tanuki-native-corresponding-sources.tar.gz`、`native-source-SHA256SUMS`、`RELEASE-SHA256SUMS`；六附件 SHA 以 `RELEASE-SHA256SUMS` 为准。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未覆盖；workspacecheck 开发证书、non-debuggable、ARM64、minSdk26/targetSdk34；不是正式签名；未创建 tag/Release、未上传附件。
- 过期记录清理：本轮未删除历史交接；保留 0.1.11 发布记录、0.1.11/0.1.9 附件事实与仍有效的验证依据。跨 PR 清理和共享状态刷新由集成维护方在最终 main 上执行。
- 共享状态/印章：按发布维护流程刷新 `CURRENT.json`；发布 PR 合并后由集成维护方在最终 main 上重新 `--integrate`。

<a id="20261111-1007-chat-anchored-history"></a>

## 20261111-1007-chat-anchored-history

- Agent/会话标识：android_agent-62（AO worker）
- 时间：2026-11-11 10:07–11:00 CST

### 当前状态

- 基线：`origin/main=62e4c30b4a61636867cfc94736e44fa159f50b9f`（已 fetch 核对）；分支 `ao/chat-anchoring/anchored-list`，从会话根分支 `ao/chat-anchoring/root` 创建的兄弟分支；PR [#62](https://github.com/leezzou/tanuki/pull/62)；实现提交 `adffdb2`，补充提交 `42e1f3d`（交接链接）与 `72c7b67`（hasMore 优先级修复）；最新 head 以 GitHub PR 为准，本记录记录到 `72c7b67` 的完整 CI。
- 已实现：
  - `DshChatWindow.kt` 纯策略：锚点分段（历史段含锚点、新消息段在锚点下方）、用户手势脱离判定、头部插页后的索引恢复、每页 50 条的游标、只释放视口上方的内存窗口、按 seq 去重的消息账本与快照区间对账。
  - 控制器：`loadOlder()`、`onViewportTop()`、`historyRevision`/`releasedOlder` 状态；快照不再清空已加载的更早分页；同 seq 实时事件只保留一份；重连后第二次 `Connected` 主动对账历史；最近历史页的 `hasMore` 只在真正接到已加载窗口起点时才覆盖当前值。
  - 页面：`LazyColumn` 显式历史段 + 锚点行 + 新消息段；顶部「加载更早历史 / 正在加载 / 已到会话最早一条 / 失败原因 / 已释放 N 条」；未跟随时「N 条新消息」或「↓ 最新」胶囊；`nestedScroll` 只在用户输入时脱离跟随，程序贴底/恢复不误判。
  - 会话级跟随状态：`DshChatFollowState` 按 `selectedId` 重建，nested scroll 监听通过 `rememberUpdatedState` 读取当前实例；修复评审指出的 A→B 切换后手势仍读写 A 状态、B 永远贴底的问题。
  - 协议：`DshChatSource.messagesBefore(sessionId, beforeSeq, maxMessages)`；`DshNativeClient` 复用官方 `session/page`，`throughSeq=beforeSeq`、单页上限 200。
  - 文档：INTERFACES 增加历史分页契约；NATIVE-UI 增加本轮结构说明；NATIVE-PARITY 更新 SESS-02/SESS-03 现状与 A-SESS-01 落地证据。
- 已验证：定向 7 个相关类全部通过（follow 5 / window 18 / paging 16 / tool 15 / render 16 / controller 22 / api 14）与完整 JVM 1085 tests/111 文件全部通过；lint 0 errors/104 warnings/1 hint；`check-publication.py --git` 0 findings；`git diff --check` 通过。
- 待验证：设备/模拟器上的手势、流式滚动、顶部加载、深色/大字体、真实 DSH 历史分页；独立 Codex High 审查；合并与发布。
- 关键源码：`app/src/main/kotlin/com/tanuki/ui/DshChatWindow.kt`（新增）、`DshChatFollowState.kt`（新增）、`DshNativeController.kt`、`DshNativePage.kt`、`app/src/main/kotlin/com/tanuki/web/DshLive.kt`、`DshNativeApi.kt`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/DshChatWindow.kt` | 新增纯策略：`DshChatAnchorPolicy`（两段切分、未读计数、渲染 key 顺序与恢复索引、用户手势脱离）、`DshChatWindowPolicy`（500 条消息 + 200 张工具的统一视口窗口、只释放视口上方、分页游标）、`DshMessageLedger`（seq 去重排序、占位保留、快照区间对账）。 |
| `app/src/main/kotlin/com/tanuki/web/DshToolTimeline.kt` | `DshToolMerge.merge` 增加 `maxTools` 参数；原生控制器传 `null`，合并阶段不再按最新 200 张截断，改由统一视口窗口释放。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt` | 增加 `loadingOlder`/`olderError`/`historyRevision`/`releasedOlder`；实现 `loadOlder()`、`applyOlderPage()`、`onViewportTop()`、`trimWindow()`；历史页与快照改用消息账本；重连后对账；分页失败/重复页/空页处理。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 消息列表改为历史段 + 锚点 + 新消息段；顶部加载入口与边界提示；未跟随胶囊；按行 key + 偏移恢复视口；`nestedScroll` 用户手势脱离；跟随状态改为按会话重建并经 `rememberUpdatedState` 读取；移除 80/40/400 渲染窗口与旧「官方仅返回最近一页」文案。 |
| `app/src/main/kotlin/com/tanuki/ui/DshChatFollowState.kt` | 新增会话级跟随状态（`StateFlow` 快照）：上翻冻结锚点、回底恢复、切换会话互不影响；供页面与回归共用。 |
| `app/src/main/kotlin/com/tanuki/web/DshLive.kt` | `DshChatSource` 增加 `messagesBefore`，作为向前分页的唯一数据入口。 |
| `app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt` | `messages()` 与 `messagesBefore()` 共用 `pageThrough()`；首屏 120 条、分页单页上限 200；未新增官方方法或端点。 |
| `app/src/main/kotlin/com/tanuki/ui/DshChatRender.kt` | 移除旧的渲染窗口与拖动跟随策略，保留底部判定与其余渲染策略；窗口/锚定策略迁入 `DshChatWindow.kt`。 |
| `app/src/test/kotlin/com/tanuki/DshChatWindowTest.kt` | 新增 18 项纯策略回归：锚点切分、分页前插不改变下方段、渲染 key 顺序（锚点前/锚点/锚点后）、索引恢复、未读计数、手势脱离、消息与工具预算边界、只释放视口上方、seq 去重、占位、快照对账与幂等。 |
| `app/src/test/kotlin/com/tanuki/DshChatFollowStateTest.kt` | 新增 4 项会话级回归：A→B 状态隔离、上翻后流式/新消息不恢复跟随、空列表不变、本地占位可作锚点。 |
| `app/src/test/kotlin/com/tanuki/DshToolTimelineTest.kt` | 新增 1 项合并回归：`maxTools=null` 保留更早工具，默认上限仍生效。 |
| `app/src/test/kotlin/com/tanuki/DshNativePagingTest.kt` | 新增 16 项控制器回归：分页插入排序/去重、`beforeSeq` 与每页 50、失败保留、空页停止、重复页防循环、窗口释放边界、同 seq 只插一次、重连对账、快照不删更早分页、快照先到时 `hasMore` 不被窄页覆盖、更早工具不被合并上限丢弃、向下滚动按预算释放工具、上翻不立即释放刚加载行。 |
| `app/src/test/kotlin/com/tanuki/DshNativeApiTest.kt` | 新增 1 项协议回归：`messagesBefore` 以 `beforeSeq` 作 `throughSeq`、`maxMessages=50`。 |
| `app/src/test/kotlin/com/tanuki/DshChatRenderTest.kt` | 删除已迁出的渲染窗口/拖动跟随即测试；保留并继续覆盖底部判定。 |
| `app/src/test/kotlin/com/tanuki/DshNativeControllerTest.kt` | 为测试数据源补 `messagesBefore`；流式落盘用例改为断言同一条消息只出现一次（空快照不再清空 HTTP 历史页）。 |
| `docs/INTERFACES.md` | 增加 `session/page` 向前分页契约、内存窗口与快照对账边界；从缺口列表移除“历史向前分页”。 |
| `docs/NATIVE-UI.md` | 增加“聊天列表两段锚定与向前分页（2026-11-11）”专题段；更新 `session/page`、控制器/页面/策略文件说明。 |
| `docs/NATIVE-PARITY.md` | 更新 SESS-02/SESS-03 现状与归属，补充 A-SESS-01 分页回归落地证据。 |
| `docs/handoffs/20261111-1007-chat-anchored-history.md` | 本记录。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git rev-parse origin/main` | `62e4c30b4a61636867cfc94736e44fa159f50b9f` | 基线核对 |
| `./gradlew ... :app:testModernDebugUnitTest --tests 'com.tanuki.DshChatFollowStateTest' --tests 'com.tanuki.DshChatWindowTest' --tests 'com.tanuki.DshNativePagingTest' --tests 'com.tanuki.DshChatRenderTest' --tests 'com.tanuki.DshNativeControllerTest' --tests 'com.tanuki.DshNativeApiTest' --tests 'com.tanuki.DshToolTimelineTest'` | 通过（7 类 / 0 failed） | `-PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime`；JVM，未涉及设备 |
| `./gradlew ... :app:testModernDebugUnitTest`（完整） | 通过：1085 tests / 111 个结果文件 / 0 failures / 0 errors / 0 skipped | 同一 Gradle 配置；首次完整运行 1071 tests 也通过 |
| `./gradlew ... :app:lintModernDebug` | 通过：0 errors / 104 warnings / 1 hint | 无新增 lint 错误 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1007-chat-anchored-history.md` | 通过 | 普通 PR 模式，不改共享状态 |
| `python3 scripts/check-publication.py --git` | 739 public files / 735 index entries / 0 findings | 上传前检查 |
| `git diff --check` | 通过 | 无空白错误 |
| 设备 / 模拟器 / 真实 DSH | 未执行 | 设备由独立 QA 独占；下方手工步骤待 QA 执行 |

#### 评审修复（PR #62）

1. thread `PRRT_kwDOVgRm4s62AmPB`（`DshNativePage.kt:306`，P1）：无 key `remember` 保留首次会话的 `following`/`anchor` 状态委托，A→B 切换后手势仍读写 A，B 永远贴底、没有胶囊也不触发顶部自动分页。
   - 修复：新增会话级 `DshChatFollowState`（`StateFlow<DshChatFollowSnapshot>`），页面按 `selectedId` 重建；nested scroll 监听按会话重建并通过 `rememberUpdatedState` 读取当前实例。
   - 回归：`DshChatFollowStateTest` 覆盖 A→B 隔离、上翻后流式/新消息不恢复跟随、回底清锚点、空列表与占位锚点。
2. thread `PRRT_kwDOVgRm4s62AmPD`（`DshNativeController.kt:550`，P2）：`DshToolMerge.merge` 无条件 `takeLast(200)`，当前已有 200 张新工具时，向前分页返回的更早工具会被合并瞬间丢弃，文本却保留。
   - 修复：`DshToolMerge.merge` 增加 `maxTools` 参数；控制器所有合并传 `null`，由 `DshChatWindowPolicy` 把消息与工具放在同一视口窗口内释放（消息 500、工具 200，只释放视口上方）。
   - 回归：`DshNativePagingTest` 覆盖“已有 200 张工具时加载更早混合页保留 203 张”、向下滚动按预算释放工具、上翻不立即释放刚加载行；`DshToolTimelineTest` 覆盖无上限合并与默认上限。
3. thread `PRRT_kwDOVgRm4s62AmPG`（`DshNativePage.kt:372`，P2）：`split != null` 时实际列表多一个锚点分隔行，但恢复索引只加顶部提示行；锚点后的消息会少算一行，`scrollToItem` 跳到前一条。
   - 修复：新增 `DshChatAnchorPolicy.renderKeys`，按实际渲染顺序（顶部提示、历史段、锚点、新消息段）生成 key 列表，恢复时 `restoreIndex(renderKeys, key, leadingRows = 0)`。
   - 回归：`DshChatWindowTest` 覆盖锚点前、锚点本身、锚点后消息的恢复索引。
4. thread `PRRT_kwDOVgRm4s62BJm4`（`DshNativePage.kt:337`，P2）：`following` 改为普通 Boolean 后，未随 `following` 重建的底部监听持续捕获首次组合的 `true`，上翻再滚回底部不会 `reattach`。
   - 修复：新增 `DshChatFollowState.reattachIfDetached()`，底部监听直接按当前状态恢复；restore effect 也改为读取 `follow.snapshot.following`，不再使用组合期局部值。
   - 回归：`DshChatFollowStateTest` 覆盖同一会话“上翻 → 回底恢复 → 新消息继续跟随”；Compose 监听本身仍由设备验收。
5. thread `PRRT_kwDOVgRm4s62BJm6` / `PRRT_kwDOVgRm4s62BJm8`（针对 `bb15389` 的重复提交）：分别对应第 2、3 条的工具窗口与锚点索引问题，已在 `5ec8b2a` 一并修复；工作区提交后复审以新 head 为准。

Compose 真实手势与渲染仍需设备验证。


- 已知既有不稳定项：`NpmAdaptiveWorkerPoolTest.downshiftRetiresExcessWorkersAfterCurrentTaskAndKeepsQueueDraining` 在本轮完整套件中失败 2 次（断言 `retiredWorkers expected 8 but was 9`），单独复跑和最终完整套件均通过。该测试属于 NPM 并发池，本轮未改动；失败源于“活跃数瞬时低于目标时调度器补员后又退休”的时序竞争，不是聊天列表改动。
- 未测试、超时、失败和暂未复现的情况：设备手势与帧率、真实 DSH 的 `session/page` 分页返回、深色/大字体、无障碍、低端机长历史内存峰值均未验证。

#### 手工验收步骤（设备，待独立 QA）

1. 打开一个长会话，滚动到顶部，确认自动/按钮加载更早历史时当前内容不跳动；重复上滑直到出现“已到会话最早一条”。
2. 触发一条长回复流式输出，在回复过程中上滑阅读旧消息，确认视口不被拉回底部；右下角出现「N 条新消息」胶囊，点击回到底部并继续跟随。
3. 在底部等待新消息/流式增量，确认自动跟随且不出现重复气泡；断网重连或点刷新后核对消息齐全、顺序正确、无重复。
4. 长会话连续上翻十余页后再回到底部，确认旧消息未消失、滚动位置正确，顶部提示“已释放 N 条更早消息，向上滚动会重新读取”。
5. 在深色、大字体、360dp 窄屏重复 1–3。

### 未完成与暂停项

- 未完成：设备/模拟器验收、真实 DSH 历史分页验证、独立 Codex High 审查、PR 审查意见处理（若有）。
- 外部阻塞：设备由独立 QA 独占；本会话未运行设备测试。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收、合并与发布；本记录不触碰这些暂停项。

### 下一步

1. PR 已创建（见“产物与校验”）；3 条评审 thread 已修复并逐条回复/解决，等待新 head 的 GitHub CI、复审与独立 QA。
2. QA 按上方手工步骤验证；若真实 DSH 的 `session/page` 不支持 `throughSeq` 向前读取，控制器会因“无新增行”停止续页并显示“已到会话最早一条”，需要在设备上确认官方实际返回后调整游标。
3. 处理 review comment 后推送新提交；最新 SHA 重新验证，旧审查结论不沿用。

### 约束与风险

- 数据/设备/权限边界：只改原生聊天展示/状态与官方 `session/page` 读取；不写 DSH 数据、不读凭据、不改官方 npm 包、不触碰设备。
- 关键决策：
  - 用单一 `LazyColumn` 的“历史段 + 锚点行 + 新消息段”实现两段结构，避免两个独立滚动容器造成跨段手势断裂。
  - 用视口首行 key + `firstVisibleItemScrollOffset` 恢复位置；头部释放只发生在视口上方，正在阅读的行不会被删。
  - 内存窗口只按消息条数计预算（500），工具卡按同一 seq 边界同步释放，避免文本/工具错位。
  - 快照按 seq 区间对账而不是整表替换，避免重连或分页后被 120 条窗口覆盖。
- 失败尝试与避免重复踩坑的结论：
  - 曾用 `LazyListItemInfo.offset - viewportStartOffset` 计算恢复偏移，会与内容内边距叠加；改为“目标行是首行时直接用 `firstVisibleItemScrollOffset`，否则对齐到 0”。
  - 曾用 `isScrollInProgress && !atBottom` 判断脱离，程序贴底也会误判；改为只在 `NestedScrollSource.UserInput` 且已离开底部时冻结锚点。
  - 空快照整表替换会清空已加载历史；改为区间对账，并由现有流式用例守住“落盘消息只出现一次”。
- 已知边界：用户在顶部持续上翻时，正在阅读的分页不会被释放，内存可短暂超过 500 条预算；回到下方后按预算释放。真实 DSH 分页语义未在设备验证。

### 产物与校验

- 本轮不生成 APK、源码 ZIP 或发布附件；不覆盖安装、不发布、不合并。
- PR [#62](https://github.com/leezzou/tanuki/pull/62)，目标 `main`；实现提交 `adffdb2`，补充 `42e1f3d`/`72c7b67` 与两轮评审修复提交；`72c7b67` 的 4 个 GitHub check 全部 pass，最新 head 的检查以 GitHub PR 为准。
- 提交前已运行 `check-handoff.py --pr`、`check-publication.py --git`、`git diff --check`。
- 普通 PR 不刷新 `AGENT-STATE.md`/`CURRENT.json`/`TEST-REPORT.md`；只新增本记录。

<a id="20261111-1008-token-page-integration"></a>

## 20261111-1008-token-page-integration

- Agent/会话标识：android_agent-64（AO worker）
- 时间：2026-11-11 10:08 CST（开始）/ 2026-11-11 11:40 CST（收尾）

### 当前状态

- 已实现：根主题统一到新调色板；文件页、会话管理页、设置段接入令牌与基础组件；动效令牌接入入场/折叠/状态切换/按压；新增页面视觉映射的 JVM 回归。
- 已验证：编译（app + unit test）、完整 JVM 1059 tests / 0 failures、定向页面视觉测试 12 tests、主题对比度 4 tests、lint 0 errors/103 warnings/1 hint（改动文件无新增告警）。
- 待验证：设备/模拟器上的深色、浅色、大字体、窄屏截图对照；TalkBack、输入法遮挡、滚动手感；Android 仪器测试。
- 当前目标包名、分支：`ao/token-ui-integration/root`，基线 `origin/main=62e4c30b`；无运行中设备任务。
- 关键事实与可定位源码/公共文档：令牌与组件在 `app/src/main/kotlin/com/tanuki/ui/design/`；页面视觉映射在 `app/src/main/kotlin/com/tanuki/ui/TanukiPageVisuals.kt`；说明见 [设计令牌](DESIGN-TOKENS.md) 第 8 节。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/TanukiDesign.kt` | `TanukiTheme` 直接委托 `TanukiDesignTheme`，只保留一套颜色来源；`tanukiColorScheme(dark)` 改读同一调色板；`TanukiSpacing` 改为从 `TanukiSpace` 取值；新增 `tanukiDisabledContentColor()` |
| `app/src/main/kotlin/com/tanuki/ui/TanukiMotion.kt` | `tanukiEnter()` 由 420ms 改为 `page` 令牌（320ms）并尊重移除动画；新增 `tanukiMotionMs`、`tanukiToggleSpec`、`TanukiExpandVisibility` |
| `app/src/main/kotlin/com/tanuki/ui/TanukiStateViews.kt` | `TanukiStateView` 增加可选 `title`、`secondaryActionLabel`/`onSecondaryAction`，供页面复用三态并保留“刷新列表”等次操作 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiPageVisuals.kt` | 新增纯映射：文件空态文案、权限语气、大小/传输文案、会话状态标签语气与呼吸 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt` | 位置选择器/行高/权限 chip/传输胶囊/三态/弹层主按钮改用令牌与基础组件；空态按搜索/隐藏文件/空目录给不同动作 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerPage.kt` | 列表行改 `TanukiPressable` + 56dp；分组头、筛选条、选择条、批量进度、消息搜索、横幅、三态改用令牌；列表层的新建/批量归档/批量上限改用 `TanukiBottomSheet`，详情层发起的重命名/归档/完全权限保留窗口级 `AlertDialog`；状态切换用 `Crossfade` 令牌；大字体时弹层按钮改为整行 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 设置分区标题/卡片、动作行、只读行、开关、折叠面板、分类入口改用令牌；折叠箭头与内容动效走令牌 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiDesignTheme.kt` | 通用 `Shapes.large` 从“只有上圆角”改为 28dp 全圆角（弹层继续用 `TanukiSheetShape`）；新增 `tanukiDesignColorScheme(dark)` 供原生 View 使用 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiComponents.kt` | `TanukiStatusChip` 增加可选 `contentDescription`，保留“权限：…，点击管理”朗读 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiBottomSheet.kt` | 增加可选 `heightFraction`，短确认框仍按内容包裹；内容根部提供 `textPrimary` 的 `LocalContentColor`，修复深色下正文继承黑色 |
| `app/src/test/kotlin/com/tanuki/TanukiPageVisualsTest.kt` | 新增 12 项 JVM 回归：行高/间距/动效/字体令牌、状态 chip 与卡片文字对比度、大小与空态文案、权限/会话语气映射 |
| `app/src/androidTest/kotlin/com/tanuki/SessionManagerDetailDialogTest.kt` | 新增设备回归（仅编译，未运行）：从详情进入重命名/归档/完全权限，验证确认层出现在详情之上、取消与返回只关闭确认层 |
| `docs/DESIGN-TOKENS.md` | 更新为“令牌 + 接入结果”：接入表、令牌映射表、未接入清单、本轮验证与未验证 |
| `docs/handoffs/20261111-1008-token-page-integration.md` | 本次唯一交接记录 |

未改：`DshNativePage.kt`、`DshNativeController.kt`、`DshComposerBar.kt`、停靠条相关文件、WebView/浏览器实现、任何协议或业务状态机。

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `./gradlew :app:compileModernDebugKotlin :app:compileModernDebugUnitTestKotlin -x buildNativeRuntime` | 通过（JDK17，复用主检出 `.build/jdk`） | 编译通过；`-x buildNativeRuntime` 的产物不含原生运行时 |
| `./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime` | 通过：1059 tests / 109 个结果文件 / 0 failures / 0 errors / 0 skipped | 结果 XML 在 `app/build/test-results/testModernDebugUnitTest/` |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.TanukiPageVisualsTest' --tests 'com.tanuki.TanukiContrastTest'` | 通过：12 + 4 tests | 新增页面映射与主题对比度均通过 |
| `./gradlew :app:lintModernDebug -x buildNativeRuntime` | 通过：0 errors / 103 warnings / 1 hint | 改动文件没有新增告警；`ModifierParameter` 等来自既有 `TanukiIcon` 等位置 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.NpmAdaptiveWorkerPoolTest'` | 通过（重跑） | 首次全量运行时该用例出现一次 `expected:<8> but was:<9>` 时序失败；未改该模块，单类重跑通过，记录为既有 flaky |
| 静态自查：深色/浅色/大字体/窄屏 | 仅源码与令牌层面核对，逐页矩阵见 `docs/DESIGN-TOKENS.md` 8.2 | 未在设备/模拟器渲染；不能计为设备通过 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.TanukiPageVisualsTest'` | 通过（P2 修复后） | 新增弹层正文/副标题对比度断言 |
| `./gradlew -PtestBuildType=debug :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin` | 通过 | 新增仪器用例仅编译；无设备未运行 |
| GitHub Actions PR #70 | `Host scripts and handoff checks`、`Android build, JVM tests and lint` 在推送 head 上通过 | 以 PR 页面当前 head 的检查为准；新提交后需复跑 |

- 哪些是前一轮结果，哪些是本次新验证：PR #59 的 19 项设计令牌测试与 952 tests 为历史；本轮新增 12 项页面映射测试，并重跑完整 JVM 与 lint。
- 未测试、超时、失败和暂未复现的情况：见下节；设备未授权。

### 未完成与暂停项

- 未完成：设备/模拟器逐页截图对照（深色、浅色、1.3x/2x 大字体、360dp 窄屏）与 TalkBack/输入法实测；新增的 `SessionManagerDetailDialogTest` 只编译未运行。
- 外部阻塞：设备由独立 QA 独占，本 worker 不安装、不操作设备。
- 用户暂停：合并、发布、tag/Release；API Key 保存；真实模型/账号验收。
- 已知风险：
  - 聊天页与浏览器页通过根主题继承新调色板（未改其源码），其视觉会随主题变化，需要各自的 owner 在设备上确认。
  - 会话详情仍使用系统 `ModalBottomSheet`：拖拽/返回键/输入法行为保留，动画时长由平台决定（300ms，落在令牌 240–340ms 区间），不能由令牌置零。
  - 因此从详情发起的三条二级确认必须用窗口级 `AlertDialog`；不能用页面内 `TanukiBottomSheet`，否则会被详情窗口遮住（PR #70 评审 P1 已修复）。
  - `TanukiBottomSheet` 自己绘制 sheet 背景，必须同时提供 `LocalContentColor`；否则深色下正文继承页面默认黑色（PR #70 评审 P2 已修复，并补 textPrimary/textSecondary × sheet 对比度断言）。
  - 文件页头部在 320dp 极窄屏下位置选择器可能只剩图标宽度；360dp 下按静态计算可容纳。
  - 大字体下会话行会变高；弹层动作按钮在 1.3x 起改为整行，避免省略号截断。

### 下一步

1. 独立审查本 PR（绑定当前 head），重点看 `DshSessionManagerPage.kt` 的弹层改造与 `TanukiDesignTheme.kt` 的 Shapes 修正。
2. 独立 Android QA 在明确设备上安装 workspacecheck 包，按 `docs/DESIGN-TOKENS.md` 第 8 节页面清单逐页截图：浅色/深色、1.3x/2x 字体、360dp 窄屏；重点确认文件页权限 chip、传输胶囊、会话筛选条、列表层弹层，以及详情内三条窗口级确认。
3. 若设备结果与静态自查不一致，按页面回改令牌映射，不要回退到硬编码色值。
4. 获得明确合并授权后再合并；本 PR 不发布。

### 约束与风险

- 数据/设备/权限边界：未读取真实凭据；未安装/启动设备；未合并/发布。
- 关键决策及理由：
  - 主题只保留一套颜色来源（`TanukiDesignTheme`），避免 `TanukiTheme` 与新令牌并存导致同页两种色值。
  - 文件行继续用 `combinedClickable`，不用 `TanukiPressable`，避免吞掉长按多选。
  - 会话详情保留系统 `ModalBottomSheet`，因为它是窗口级交互（拖拽、返回键、输入法），换成自绘骨架会改变行为；列表层的新建/批量确认改用页面内 `TanukiBottomSheet` 以获得令牌动效，详情内三条确认保留窗口级 `AlertDialog` 以免被详情窗口遮住。
  - 通用 `Shapes.large` 改为全圆角，避免普通卡片继承“只有上圆角”的弹层形状。
- 失败尝试与避免重复踩坑的结论：
  - 首次全量 JVM 出现 `NpmAdaptiveWorkerPoolTest` 时序失败，重跑通过；不要据此改产品代码。
  - Gradle 需要读取工作区外的 `~/.gradle` 与 Android SDK；本机工作区外没有 JDK17，复用主检出的 `.build/jdk/jdk-17.0.21+9`。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本 PR 不产出 APK/ZIP；验证构建使用 `-x buildNativeRuntime`，不可作为设备验收物。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未发布、未创建 tag/Release。
- 提交/推送前清理的过期记录：本轮没有判定过期的交接记录，未删除历史记录。
- 普通 PR 不刷新共享状态/印章；集成维护方在批量合并后统一运行 `--integrate`。

<a id="20261111-1017-remote-relay-quickstart"></a>

## 20261111-1017-remote-relay-quickstart

- Agent/会话标识：android_agent-65（AO worker，DSH）
- 时间：2026-11-11 10:17 CST

### 当前状态

- 已实现：`quickstart.sh` 一条命令流程、`quickstart_lib.py` 辅助逻辑、`host_agent.py --pairing-relay`、CI 自测步骤、文档 3.1–3.3。
- 已验证：19 项 quickstart 离线自测通过；既有 9 项 relay 回归通过；`--print-only` 无副作用；真实回环启动后 SIGINT 退出码 130，relay/agent 均退出、端口释放、pid 文件清理；发布检查 0 findings。
- 待验证：PR 推送后的 GitHub CI；独立 Codex High 对当前 head 的审查；真实手机 + 真实 sshd/公网中继、真实剪贴板。
- 当前分支：`ao/relay-quickstart/root`，基于 `origin/main=62e4c30`，已合并 `origin/main=7d58e90`（#63 手机端自动配对）；PR：<https://github.com/leezzou/tanuki/pull/64>（上一 head `2cb3341` CI 通过，merge 后 CI 待重跑）；无运行中的 relay/agent 测试进程。
- 关键事实与可定位源码：`scripts/remote-relay/quickstart.sh`、`quickstart_lib.py`、`test_quickstart.py`、`host_agent.py`（`--pairing-relay`）、`docs/REMOTE-CONNECT.md` 第 3 节、`.github/workflows/verify.yml` 的 “Test remote relay quickstart”。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `scripts/remote-relay/quickstart.sh` | 新增一键启动：检查 python3/openssl、复用 `gen-cert.sh` 生成/复用证书、自动计算钉扎、端口预检、后台 relay + 健康检查、agent 自动带 `--cert-pin`、剪贴板、大字横幅、`--print-only`、`--no-clipboard`、INT/TERM/HUP trap 清理两个子进程。 |
| `scripts/remote-relay/quickstart_lib.py` | 新增可离线单测的辅助逻辑：证书 DER SHA-256 钉扎、relay/agent 命令拼装、配对文件解析与 6 位码、端口探测/等待、局域网 IPv4 探测、`--print-only` 计划输出。 |
| `scripts/remote-relay/host_agent.py` | 新增向后兼容的 `--pairing-relay`：agent 仍连接 `--relay`，但配对链接可展示手机可达的另一地址；未提供时行为不变。 |
| `scripts/remote-relay/test_quickstart.py` | 新增 19 项自测：钉扎、参数拼装、配对解析、端口、局域网探测、`--print-only`、端口占用、真实回环启动 + SIGINT trap 清理、`--pairing-relay` 回归。 |
| `.github/workflows/verify.yml` | 在既有 relay 回归后新增 quickstart 自测步骤。 |
| `docs/REMOTE-CONNECT.md` | 新增“主机端 5 分钟上手（quickstart）”3.1 三条命令、3.2 预期输出、3.3 常见失败对照；顺延后续章节编号；实现位置与验证记录补 quickstart。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `cd scripts/remote-relay && python3 test_quickstart.py` | 通过：19 tests / 0 failures，约 3 秒 | 本机 macOS、Python 3.9.6、OpenSSL 4.1.0；仅回环与临时目录，不访问外网 |
| `cd scripts/remote-relay && python3 -m unittest test_quickstart -v` | 通过：19 tests / 0 failures | 同一组用例，确认 `python -m unittest` 入口可用 |
| `python3 scripts/remote-relay/test_remote_relay.py` | 通过：9 tests / 0 failures | 既有 relay 端到端回归；确认 `host_agent.py` 改动未破坏原行为 |
| `bash -n scripts/remote-relay/quickstart.sh` | 通过 | 仅语法检查，不替代运行验证 |
| `bash scripts/remote-relay/quickstart.sh --print-only --state-dir <临时目录> --port 18443` | 退出 0；输出证书路径、钉扎状态、relay/agent 命令、配对文件状态；临时目录无新增文件 | 确认 `--print-only` 不建目录、不启动进程 |
| 回环启动后发送 SIGINT（`Popen` + `send_signal`） | 退出码 130；relay/agent PID 均消失；端口释放；`quickstart.pids` 删除；stderr 含“已停止” | 使用 `--listen 127.0.0.1 --public-host 127.0.0.1 --no-clipboard`，不依赖真实网络 |
| 回环启动后读取横幅 | stdout 含完整配对链接、6 位核对码、手机端三步、Ctrl+C 提示；链接中的 `pin` 与证书钉扎一致 | 临时状态目录；未验证真实手机粘贴/扫码 |
| GitHub Actions 首轮 Host job（run 42948018982） | 失败：`test_port_detection_and_wait` 在 Linux 上对 backlog=1 的监听端口做第三次连接探测时超时 | 已改为直接断言 `probe_host` 的通配地址映射并增大 backlog；本地复跑 19 项通过 |
| GitHub Actions（head `2cb3341`） | 通过：push run 42948762327（Android 7m47s、Host 40s）与 PR run 42948753628（Android 5m59s、Host 38s），4 个 check 全 pass | 本记录为 CI 后的 docs-only 提交，最终 head 的 CI 以 PR 为准；未跑设备 |
| `git merge origin/main`（main=`7d58e90`，#63）后复跑 | 通过：quickstart 19 tests、既有 relay 9 tests；仅 `docs/REMOTE-CONNECT.md` 冲突 | 冲突保留 #63 的手机自动读剪贴板/自动算码内容，并同步 quickstart 横幅文案 |
| `python3 scripts/check-publication.py --git` | 通过：0 findings（检查时新增文件尚未 `git add`，提交前需重跑） | 发布检查不打印匹配到的敏感值 |

- 哪些是前一轮结果，哪些是本次新验证：本表全部为本次新验证；未引用前一轮产品测试数字。
- 未测试、超时、失败和暂未复现的情况：真实手机、真实剪贴板工具、真实公网中继/NAT、真实 sshd、弱网、GitHub CI 与独立审查尚未执行；本机为 macOS，Linux 的 `wl-copy/xclip` 分支仅做代码审阅。

### 未完成与暂停项

- 未完成：merge 后最终 head 的 GitHub CI；独立 Codex High 审查绑定最终 head；真实公网/设备验收。
- 外部阻塞：无（本地可完成部分已完成）。
- 用户暂停：API Key、真实模型/账号、真机验收、合并/发布；本次未触碰，也未合并/发布。
- 未删除任何旧交接记录：没有因本任务而过期的记录；历史验证依据保留。

### 下一步

1. PR #64 CI 已通过；请编排方安排独立 Codex High 审查最终 head；不要合并、不要发布。
2. 请编排方安排独立 Codex High 审查当前 PR head；CI 与审查通过后再按用户授权决定合并。
3. 有真实中继主机/手机时，按 `docs/REMOTE-CONNECT.md` 3.1 做一次真实配对，记录中继地址类型、手机网络、证书钉扎与失败码。

### 约束与风险

- 数据/设备/权限边界：未改 Android 代码；未读写真实凭据/用户目录（自测只用临时目录与回环端口）；未执行破坏性设备操作。
- 关键决策及理由：用 Python 辅助模块承载钉扎/参数/解析，shell 只做编排，便于离线单测；新增 `--pairing-relay` 让 agent 连 `127.0.0.1`、配对链接展示手机可达地址，避免跨网络链接写成回环地址；端口检查放在证书生成前，失败不留半套文件；trap 先停 agent 再停 relay，避免重连孤儿。
- 失败尝试与避免重复踩坑的结论：用 bash `&` 手动测试时 SIGINT 会被后台任务忽略，不能用它验证 INT trap；改用 Python `Popen`（默认 SIGINT）验证，退出码固定为 130。`py_compile` 在受限沙箱会尝试写 home 缓存，需设置 `PYTHONPYCACHEPREFIX` 到工作区。
- 合并冲突：`origin/main` 的 #63 改了 `docs/REMOTE-CONNECT.md` 的手机配对流程，与本分支新增章节冲突；已保留双方内容，并把 quickstart 的“粘贴并输入核对码”文案改为“手机自动读取/算码，终端码用于对照”，避免误导。
- 安全风险：配对链接含一次性房间 token，横幅与 `pairing.txt` 均视为秘密；`--print-only` 在配对文件存在时会打印旧链接，仅用于本机排查；默认 `--public-host` 是局域网 IPv4 的最好猜测，跨网络必须显式传公网地址。

### 产物与校验

- PR：<https://github.com/leezzou/tanuki/pull/64>（不合并、不发布）。
- 新/旧 APK、源码 ZIP 及校验文件：本次未构建 APK、未生成源码 ZIP（文档/脚本变化后由发布维护方在发布流程中重打并核对外部 SHA）。
- 是否覆盖安装 / 签名范围 / 未发布事项：无安装、无签名、无 tag/Release、未合并。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：无删除；本记录为本次唯一记录，未改共享状态/印章。
- 普通 PR 不刷新共享状态/印章；集成维护方在批量合并后统一运行 `--integrate`。

<a id="20261111-1024-mobile-pairing-auto"></a>

## 20261111-1024-mobile-pairing-auto

- Agent/会话标识：android_agent-66（AO Worker）
- 时间：2026-11-11 10:24 CST

### 当前状态

- 已实现：剪贴板自动读取与安全覆盖策略、链接自动算码与可选校对、配对确认步骤、按中继错误分流的中文建议、手动粘贴/输入回退、配对步骤仅链接方式出现。
- 已验证：见下方“验证记录”。
- 待验证：真实 Android 剪贴板行为（Android 10+ 聚焦限制）、Compose 确认页在设备上的布局/大字/深色、真实中继端到端。
- 当前目标包名、分支/无Git状态、是否有运行中任务：`com.tanuki`（modern/debug 变体）；分支 `ao/mobile-pairing/root`，基线 `origin/main=62e4c30b`；编译、JVM、lint 均已结束，无遗留构建任务。
- 关键事实与可定位源码/公共文档：6 位码算法在 `RemotePairingCode`，自动填充/确认在 `RemoteHostWizard`，剪贴板入口在 `RemoteHostSettings.readPairingClipboard`，失败建议在 `RemotePairingGuidance`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/runtime/RemotePairingCode.kt` | 更新注释：手机自动算码、不要求手输；删除外部参考实现措辞。 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteRelay.kt` | 新增 `RelayFailureKind.EXPIRED` 区分过期与房间不存在；`expired` 映射；`RelayTlsPin.abbreviate` 供确认页截断展示。 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteConnectionError.kt` | 新增 `RemotePairingGuidance`，按中继错误类型给出中文原因与下一步；中继异常统一走该映射。 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteHostWizard.kt` | 新增配对确认步骤、剪贴板状态/访问代次、自动填链接与算码、手动链接保护与显式替换、自动码回填、配对码可选校验；直连/局域网跳过确认步骤。 |
| `app/src/main/kotlin/com/tanuki/ui/RemoteHostSettings.kt` | 进入链接步骤自动读剪贴板；显示“电脑上应显示”大号码与校对框；新增确认页（电脑名/中继/指纹展开/SSH 目标）；失败面板继续复用阶段化建议；手动粘贴回退保留。 |
| `app/src/test/kotlin/com/tanuki/RemoteWizardTest.kt` | 新增/更新 JVM 回归：剪贴板自动填充与算码一致、非链接/无效链接回退、手动链接不被覆盖、可选码校对、确认步骤、五类失败文案。 |
| `docs/REMOTE-CONNECT.md` | 更新手机加入流程：自动剪贴板、自动算码、确认页、按原因建议。 |
| `docs/REMOTE-HOST.md` | 更新向导步骤和配对操作说明，去掉“必须输入 6 位码”的旧描述与“Python 主机端”措辞。 |
| `docs/INTERFACES.md` | 更新配对链接/6 位码的行内契约：自动计算展示、可选校对、剪贴板失败保留手动粘贴。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `./gradlew :app:compileModernDebugKotlin -x buildNativeRuntime` | 通过（BUILD SUCCESSFUL，2m27s；仅既有 deprecation 警告） | JDK17（主仓库 `.build/jdk/jdk-17.0.21+9`）、Android SDK 36、macOS ARM64 |
| `./gradlew --no-daemon -PtestBuildType=debug -x buildNativeRuntime :app:testModernDebugUnitTest --tests 'com.tanuki.RemoteWizardTest' --tests 'com.tanuki.RemoteRelayTest' --tests 'com.tanuki.RemoteHostTest'` | 通过：RemoteWizardTest 46、RemoteRelayTest 22、RemoteHostTest 27，合计 95 项，0 failures/0 errors/0 skipped | 定向 JVM；结果 XML 位于 `app/build/test-results/testModernDebugUnitTest/` |
| `./gradlew --no-daemon -PtestBuildType=debug -x buildNativeRuntime :app:testModernDebugUnitTest` | 通过（BUILD SUCCESSFUL）：1052 tests / 108 结果文件 / 0 failures / 0 errors / 0 skipped | 完整 JVM；不含设备/仪器测试 |
| `./gradlew --no-daemon -PtestBuildType=debug -x buildNativeRuntime :app:lintModernDebug` | 通过（BUILD SUCCESSFUL，2m57s）：0 errors / 104 warnings / 1 hint；改动文件无 lint 发现 | 完整 lint；既有警告未在本任务修复 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1024-mobile-pairing-auto.md` | 通过 | 普通 PR 记录结构检查 |
| `python3 scripts/check-publication.py --git` | 通过：736 个公开文件 / 735 索引项 / 0 findings | 文档链接/敏感路径检查；不输出匹配值 |

- 哪些是前一轮结果，哪些是本次新验证：本次只记录本轮实际执行；前轮完整套件结果不冒充本轮复跑。
- 未测试、超时、失败和暂未复现的情况（不能抹掉）：真实设备剪贴板读取、Compose 确认页视觉/无障碍、真实中继与 SSH 端到端未执行。

### 未完成与暂停项

- 未完成：无；PR [#63](https://github.com/leezzou/tanuki/pull/63) 已创建，等待独立审查与适用 QA。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；合并/发布未授权。

### 下一步

1. 在真实 Android 设备上验证：首次进入链接步骤读剪贴板、剪贴板为空/非链接时粘贴框可用、确认页截断/展开指纹、深色与大字体布局。
2. 用真实中继与 sshd 验证五类失败文案与实际错误码一致，特别是自签名证书钉扎不匹配时不可重试。

### 约束与风险

- 数据/设备/权限边界：剪贴板只在进入链接步骤时读取第一条文本，不写入、不上传、不记录日志；非配对链接内容不显示、不覆盖粘贴框。
- 关键决策及理由：确认页作为独立步骤而非弹窗，保证用户明确确认电脑身份后才进入认证；剪贴板只覆盖空白框或本机上次自动填入的内容，避免悄悄覆盖手动编辑；配对码改为可空白，只在用户主动校对时校验不一致。
- 失败尝试与避免重复踩坑的结论：不扩展配对链接字段（链接本身不含电脑名），确认页显示“手机填写名/主机标识前缀”并明确来源；未做假扫码。手机端已支持 `expired` 专用文案，但当前中继脚本清理 TTL 房间后对查询返回 `room_not_found`，因此真实链路多数会落到“房间不存在/链接失效”文案；要在协议层区分“曾经存在但已过期”，需中继保留短期过期标记，留作后续改动。

### 产物与校验

- 新/旧APK、源码ZIP及校验文件，哪些确实更新：本次不构建/发布 APK，不生成源码 ZIP。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名发布、未合并。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本次未发现需要清理的过期交接记录；保留当前记录与基线记录。
- 普通 PR 不刷新共享状态/印章；集成维护方在批量合并后统一运行 `--integrate`。文档或脚本变化后重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。

<a id="20261111-1049-first-run-onboarding"></a>

## 20261111-1049-first-run-onboarding

- Agent/会话标识：android_agent-68（AO worker，分支 `ao/first-run-onboarding/root`）
- 时间：2026-11-11 10:49 +0800

### 当前状态

- 已实现：上述引导流程、状态机、环境门槛、准备阶段映射、权限计划、首页提示条、设置回看、解压写入量上报。
- 已验证：JVM 全量测试与 lint 通过（见下）；定向引导测试 32 项通过。
- 待验证：设备 UI、真实安装链路、真实权限弹窗、升级安装现场、深色/大字体/TalkBack。
- 当前分支：`ao/first-run-onboarding/root`，基于 `origin/main=62e4c30b4a61636867cfc94736e44fa159f50b9f`；实现提交 `933d4d7`，PR [#67](https://github.com/leezzou/tanuki/pull/67) 已创建，工作区干净，无运行中的安装/测试任务。
- 关键事实与可定位源码：`docs/ONBOARDING.md`；纯状态机 `app/src/main/kotlin/com/tanuki/onboarding/OnboardingModel.kt`；环境门槛 `OnboardingEnvironment.kt`；准备/失败/权限 `OnboardingPrepare.kt`；SharedPreferences 与设备读取 `OnboardingStore.kt`；控制器 `OnboardingController.kt`；界面 `app/src/main/kotlin/com/tanuki/ui/OnboardingScreen.kt`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingModel.kt` | 新增步骤、记录、版本门控、状态机、升级安装默认完成记录、首页提示条；纯 JVM。 |
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingEnvironment.kt` | 新增设备画像与内存/存储/架构/系统门槛、建议文案和字节格式化；纯 JVM。 |
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingPrepare.kt` | 把运行时环境状态映射为下载/解压/校验/补齐阶段；未知总量不给百分比；失败原因中文化；按需权限计划。 |
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingStore.kt` | SharedPreferences 持久化（版本、完成步骤、跳过、提示条、升级提示）与真实设备读取。 |
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingController.kt` | 状态机、存储、设备信息的薄接线；避免组合期写状态；升级安装不自动展开。 |
| `app/src/main/kotlin/com/tanuki/ui/OnboardingScreen.kt` | 四步整屏 UI、顶部进度点、底部继续/跳过、环境结果、真实阶段进度、失败重试、界面选择、完成入口、首页提示条。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 引导整屏门控；首页提示条；完成页跳转请求；设置“重新查看引导”入口；远程设置直达。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 创建引导控制器；按需申请通知权限；准备/取消/重试/完成跳转；升级安装判定。 |
| `app/src/main/kotlin/com/tanuki/storage/SafeTar.kt` | `extract` 增加可选写入量回调；默认关闭，既有调用不变。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeManager.kt` | 解压 Ubuntu 时按 32 MB 节流上报真实写入量，不伪造百分比。 |
| `app/src/test/kotlin/com/tanuki/OnboardingFlowTest.kt` | 首次/非首次/升级安装/大版本提示/跳过/部分完成/提示条/返回键/完成态回归。 |
| `app/src/test/kotlin/com/tanuki/OnboardingEnvironmentTest.kt` | 满足、存储不足、存储偏低、内存不足/偏低、非 ARM64、旧系统与格式化回归。 |
| `app/src/test/kotlin/com/tanuki/OnboardingPrepareTest.kt` | 下载百分比、未知总量、解压/校验不定进度、apt 百分比、失败中文原因、取消复用、权限计划回归。 |
| `docs/ONBOARDING.md` | 新增专题文档：四步、版本规则、环境门槛、真实进度、权限、代码位置、未验证项。 |
| `README.md` | 增加首次启动引导文档入口。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest --tests 'com.tanuki.Onboarding*'` | 通过 | 32 个引导测试，0 失败；首轮曾因 0.x 版本线判断和取消文案断言失败，修正后重跑通过。 |
| `source scripts/ao-worktree-env.sh && ./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest :app:lintModernDebug` | 通过（最终重跑 BUILD SUCCESSFUL，7m51s） | 111 个结果文件、1079 tests、0 failures / 0 errors / 0 skipped；lint 0 errors / 108 warnings / 1 hint。 |
| 测试结果统计脚本（解析 `app/build/test-results/testModernDebugUnitTest/TEST-*.xml`） | 通过 | `files=111 tests=1079 failures=0 errors=0 skipped=0`。 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1049-first-run-onboarding.md` | 通过 | 仅校验本记录；共享状态与印章允许落后。 |
| GitHub Actions PR #67（`933d4d7`） | 通过 | Host scripts and handoff checks 43s；Android build, JVM tests and lint 8m19s；两次运行均为 pass。 |

- 哪些是前一轮结果，哪些是本次新验证：前一轮基线为 `main=62e4c30`；本次新增 JVM 回归与完整测试/lint 均为本次新执行。
- 未测试、超时、失败和暂未复现的情况（不能抹掉）：未在设备/模拟器点击四步；未实际下载/解压/apt；未验证通知权限弹窗、升级安装现场、深色/大字体/TalkBack；未执行 instrumentation 测试；未打 APK。

### 未完成与暂停项

- 未完成：设备/模拟器 UI 与真实安装链路验收；独立 Codex High 对 PR 当前完整 SHA 的审查；设备 QA。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；合并与发布需用户明确授权。

### 下一步

1. 由独立 Codex High 审查 PR #67 的当前完整 SHA；由独立 QA 在测试设备上验证首启、跳过、提示条、环境不满足、真实准备与权限弹窗。
2. 通过审查与适用 QA 后，等待用户明确合并授权；不要自行合并、打 tag 或发布。
3. 若后续修改源码，重新运行完整 JVM 测试与 lint，并更新本记录中的 SHA/验证证据。

### 约束与风险

- 数据/设备/权限边界：不读取/输出真实 Key、Cookie、token、会话或私有日志；未执行 `pm clear`、卸载、wipe AVD 等破坏性操作；设备 QA 由独立 QA 负责，本轮未占用设备。
- 关键决策及理由：状态机与门槛放纯 JVM；解压总量未知时只用不定进度和真实写入量，不造百分比；升级安装通过 `lastUpdateTime`/`firstInstallTime` 判定，避免老用户被整套引导拦住；仅准备动作申请通知权限，存储仍在文件页按需申请，当前没有附近设备能力故不声明权限。
- 失败尝试与避免重复踩坑的结论：`0.1 → 0.2` 在原“主版本号”规则下不会触发提示，已改为 0.x 比较次版本号、1.x 起比较主版本号；取消态文案必须显式包含“复用”，避免测试与界面承诺不一致。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件，哪些确实更新：本轮未生成 APK、源码 ZIP 或发布附件；仅源码、测试和文档改动。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未覆盖、未签名、未发布。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：未删除历史交接；本次没有确认过期且属于本 PR 范围的记录。`docs/AGENT-STATE.md` 与 `CURRENT.json` 未修改。
- 普通 PR 不刷新共享状态/印章；集成维护方在批量合并后统一运行 `--integrate`。文档或脚本变化后重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。

<a id="20261111-1147-mdns-discovery"></a>

## 20261111-1147-mdns-discovery

- Agent/会话标识：android_agent-65（AO worker，DSH）
- 时间：2026-11-11 11:47 CST

### 当前状态

- 已实现：主机端 mDNS 广播（avahi/dns-sd 优先，Python 最小应答兜底）、TXT 契约、`--name/--dsh-port/--no-mdns`、quickstart 局域网提示与 `qrencode` 二维码/退化提示、文档与自测。
- 已验证：`test_mdns.py` 20 项、`test_quickstart.py` 20 项、`test_remote_relay.py` 9 项通过；host_agent 默认 `dns-sd` 启动且 SIGTERM 退出 0、无 `dns-sd` 残留；quickstart 默认 mDNS 横幅正确、Ctrl+C 后无 `dns-sd` 残留；假 `qrencode` 成功路径与缺失退化路径均验证。
- 待验证：PR 推送后的 GitHub CI；独立 Codex High 审查；真实手机发现列表、真实 avahi/路由器 AP 隔离/组播屏蔽、真实 `qrencode` 扫码。
- 当前分支：`ao/relay-quickstart/mdns-qr`，基于 `origin/main=8d620ee`（#64 合并后），已 fast-forward 到 `origin/main=95704ff`（#66 docs-only）；PR：<https://github.com/leezzou/tanuki/pull/71>（CI 通过 head `69280e5`）。
- 关键事实与可定位源码：`scripts/remote-relay/discovery.py`、`host_agent.py`、`quickstart.sh`、`quickstart_lib.py`、`test_mdns.py`、`docs/INTERFACES.md` 1.7、`docs/REMOTE-CONNECT.md` 3.4。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `scripts/remote-relay/discovery.py` | 新增：显示名清洗、hostId 指纹摘要、TXT 拼装/解析、mDNS 查询/响应编解码（含压缩指针）、PTR/SRV/TXT/A 最小应答、avahi/dns-sd 命令与失败回退。 |
| `scripts/remote-relay/host_agent.py` | 新增 `--name/--dsh-port/--no-mdns`；启动时广播 mDNS，失败只告警；SIGTERM 走 KeyboardInterrupt 路径，finally 停掉系统广播子进程/应答线程。 |
| `scripts/remote-relay/quickstart.sh` | 新增 `--name/--dsh-port/--no-mdns`；横幅打印“列表里应出现 <名称>”；qrencode 二维码，缺失时打印链接大字与安装提示；mDNS 未确认成功时提示用二维码。 |
| `scripts/remote-relay/quickstart_lib.py` | agent 参数增加 `--dsh-port/--name/--no-mdns`；`--print-only` 输出 display_name/dsh_port/mdns 状态；`detect_lan_ip` 统一委托 discovery。 |
| `scripts/remote-relay/test_mdns.py` | 新增 20 项离线自测：名称清洗、TXT、固定查询/响应向量、压缩指针、系统命令、工具失败回退、host_agent `--no-mdns`。 |
| `scripts/remote-relay/test_quickstart.py` | 增加 `--name/--dsh-port/--no-mdns` 参数断言、横幅 mDNS/二维码断言；测试启动默认关闭 mDNS。 |
| `scripts/remote-relay/test_remote_relay.py` | 测试 agent 启动加 `--no-mdns`，避免真实广播。 |
| `.github/workflows/verify.yml` | 新增 `test_mdns.py` CI 步骤。 |
| `docs/INTERFACES.md` | 新增 1.7 mDNS 契约，原 1.7–1.9 顺延为 1.8–1.10。 |
| `docs/REMOTE-CONNECT.md` | quickstart 说明补 mDNS/二维码；新增 3.4 局域网自动发现（前置条件、TXT、失败对照）；实现/验证补 discovery/test_mdns。 |
| `docs/REMOTE-HOST.md` | 补局域网发现前置条件、2.4 主机端说明、实现位置与未验证项。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `cd scripts/remote-relay && python3 test_mdns.py` | 通过：20 tests / 0 failures | 本机 macOS、Python 3.9.6；固定报文/假进程，不绑定 5353 |
| `cd scripts/remote-relay && python3 test_quickstart.py` | 通过：20 tests / 0 failures | 回环端口 + `--no-mdns`，不依赖真实网络 |
| `cd scripts/remote-relay && python3 test_remote_relay.py` | 通过：9 tests / 0 failures | 既有 relay 回归；agent 测试加 `--no-mdns` |
| `bash -n scripts/remote-relay/quickstart.sh` | 通过 | 仅语法检查 |
| host_agent 默认 mDNS（macOS） | 通过：日志 `mDNS 广播：测试主机（dns-sd...）`；SIGTERM 退出 0；`pgrep dns-sd` 前后均为空 | 回环/无中继；不证明真实局域网可见 |
| quickstart 默认 mDNS | 通过：横幅含“列表里应出现「测试主机」”；Ctrl+C 退出 130；`dns-sd` 无残留 | 回环端口；真实手机未验证 |
| 假 `qrencode` 成功路径 | 通过：输出假二维码内容并打印“扫码配对” | 用临时 PATH 假脚本；真实二维码未扫 |
| 无 `qrencode` 退化 | 通过：输出“未安装 qrencode”、安装提示和链接大字 | 本机未安装 qrencode |
| `python3 scripts/check-publication.py --git` / `check-handoff.py --pr` | 通过：0 findings / handoff PR check passed | 提交前已重跑 |
| GitHub Actions（head `69280e5`） | 通过：push run 42955599383（Android 5m41s、Host 34s）与 PR run 42955593842（Android 5m5s、Host 45s），4 个 check 全 pass | 本记录为 CI 后的 docs-only 提交，最终 head 的 CI 以 PR 为准；未跑设备 |

- 哪些是前一轮结果，哪些是本次新验证：本表全部为本次新验证；未引用 #64 的 19 项结果。
- 未测试、超时、失败和暂未复现的情况：真实手机发现列表、真实 avahi（Linux）与 dns-sd（macOS）跨设备、路由器 AP 隔离/访客网络/组播屏蔽、真实 qrencode 扫码、CI 与独立审查未执行。

### 未完成与暂停项

- 未完成：独立 Codex High 审查绑定最终 head；真实同网段/跨网设备验收。
- 外部阻塞：无。
- 用户暂停：合并/发布；API Key、真实模型/账号、真机验收保持暂停，本次未触碰。
- P2 审查意见（`--listen` 连接地址、状态目录锁）按编排方要求记为 #64 待办，本 PR 未顺手修改。

### 下一步

1. PR #71 CI 已通过；请编排方安排独立 Codex High 审查最终 head（不合并、不发布）。
2. 观察 CI；请编排方安排独立 Codex High 审查当前 head。
3. 有真实同网段手机/路由器时，验证发现列表、AP 隔离、UDP 5353 和二维码扫码；记录设备、网络和失败现象。

### 约束与风险

- 数据/设备/权限边界：未改 Android 代码；未读写真实凭据；测试只用临时目录/回环端口/固定报文，不绑定 5353、不启动真实 avahi/dns-sd。
- 关键决策及理由：mDNS 只广播显示名/端口/hostId/版本/指纹摘要，不广播 token；`fp` 明确定义为显示对照而非认证；系统工具优先、Python 最小应答兜底，避免引入依赖；SRV/A 目标对非 ASCII 主机名回退 `tanuki-<hostId 前 8>`，提高 Android 解析兼容性；广播失败只告警，不阻塞中继。
- 失败尝试与避免重复踩坑的结论：`${NO_MDNS:+...}` 在值为 0 时也会展开，已改为显式 `MDNS_ARG`；测试默认 `--no-mdns` 防止 CI 绑定 5353/启动系统广播；`Advertiser.stop()` 会清空 responder 引用，测试需在 stop 前保存对象。
- 安全风险：同网段可伪造 mDNS 记录；发现信息不能替代配对 token/SSH 主机密钥确认；二维码与配对链接含 token，只能在自己的终端显示；Python 兜底绑定 UDP 5353，可能与已有 mDNS 服务冲突，冲突时用 `--no-mdns`。

### 产物与校验

- PR：<https://github.com/leezzou/tanuki/pull/71>（不合并、不发布）。
- 新/旧 APK、源码 ZIP：本次未构建 APK、未生成源码 ZIP。
- 是否覆盖安装 / 签名范围 / 未发布事项：无安装、无签名、无 tag/Release、未合并。
- 清理过期记录：未删除；本记录为本次唯一记录，未改共享状态/印章。

<a id="20261111-1213-quickstart-30s"></a>

## 20261111-1213-quickstart-30s

- Agent/会话标识：android_agent-65（AO worker，DSH）
- 时间：2026-11-11 12:13 CST

### 当前状态

- 已实现：在文档标题与「1. 选型结论」之间新增「30 秒上手」，覆盖主机一条命令、手机同 Wi-Fi 发现、跨网二维码、四项排查。
- 已验证：文档结构/链接由 `check-publication.py --git` 与 `check-handoff.py --pr` 检查；本次无代码改动，不重跑产品测试。
- 待验证：PR 推送后的 CI；独立审查。
- 当前分支：`ao/relay-quickstart/30s-guide`，基于 `origin/main=ac8c20a`（#71 合并后）；PR：<https://github.com/leezzou/tanuki/pull/72>。
- 关键事实与可定位文档：`docs/REMOTE-CONNECT.md` 开头的「30 秒上手」。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `docs/REMOTE-CONNECT.md` | 新增「30 秒上手」：一条命令启动；同 Wi-Fi 远程页应出现「<主机名>」；跨网扫二维码/用 pairing.txt；组播隔离、防火墙 UDP 5353、`--no-mdns`/同名、qrencode 缺失四项排查。 |
| `docs/handoffs/20261111-1213-quickstart-30s.md` | 本次唯一交接记录。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `python3 scripts/check-publication.py --git` | 待提交前执行 | 文档链接/敏感信息检查 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1213-quickstart-30s.md` | 待提交前执行 | 交接结构检查 |
| 人工核对章节 | 已读：30 秒上手位于 H1 与「1. 选型结论」之间；命令、发现、二维码、排查四项齐全 | 未执行真实手机/网络验证（文档说明性质） |

- 哪些是前一轮结果，哪些是本次新验证：本次仅文档；未引用 #71 的测试数字作为本次代码验证。
- 未测试、超时、失败和暂未复现的情况：无代码测试；真实手机发现/二维码扫码仍以 #71 记录的未验证项为准。

### 未完成与暂停项

- 未完成：PR 创建/CI；独立审查。
- 外部阻塞：无。
- 用户暂停：合并/发布；本次未触碰。
- 未删除旧交接记录：没有因本任务而过期的记录。

### 下一步

1. PR #72 已创建；观察 CI，等待编排方/独立审查（不合并、不发布）。
2. 观察 CI；请编排方按需安排独立审查。

### 约束与风险

- 数据/设备/权限边界：仅文档，无代码、设备、凭据或真实网络操作。
- 关键决策及理由：把操作说明放在文档最前，减少新用户翻长文；明确“发现列表由手机端提供，当前版本没有列表时用二维码/链接”，避免把主机端广播说成手机端已可用。
- 失败尝试与避免重复踩坑的结论：无；文档中保留 `--public-host`、`--port`、`--no-mdns` 等既有参数，避免新增未实现行为。

### 产物与校验

- PR：<https://github.com/leezzou/tanuki/pull/72>（不合并、不发布）。
- 新/旧 APK、源码 ZIP：本次未构建、未生成。
- 是否覆盖安装 / 签名范围 / 未发布事项：无安装、无签名、无 tag/Release、未合并。
- 普通 PR 不刷新共享状态/印章。

<a id="20261111-1228-failure-exit"></a>

## 20261111-1228-failure-exit

- Agent/会话标识：android_agent-69（AO Worker）
- 时间：2026-11-11 12:28 CST

### 当前状态

- 当前普通 merge 起点：PR head `b2416b6977359e7dcb441eeb1d91cf10c5dc0c11`，工作区干净，provider 为 OPEN/CONFLICTING；fetch 得到 `main=6e1e83347b264b588c97fe380214c955bd7ba9c0`，包含 #74/#75。
- 对齐方式：`git merge --no-commit origin/main`；仅 `docs/TEST-REPORT.md` 顶部新增段落冲突，已合并保留双方记录。`MainActivity.kt`、`TanukiUi.kt` 和其余产品源码自动合并，未做额外逻辑变更。已用 `git diff --quiet` 比较确认：存储目录、`WorkspaceFilesPage.kt`、命名/传输 JVM 测试与 main 完全相同；runtime/update/onboarding、错误 UI、恢复卡、中文文案及错误 JVM 测试与合并前 HEAD 完全相同。主壳入口与 main 文件传输回调、聊天更新均保留。
- 当前合并后验证：JDK17 下 `./gradlew -PtestBuildType=debug :app:testModernDebugUnitTest :app:lintModernDebug --offline` 串行通过（4m21s）；1237 tests / 124 个结果文件 / 0 skipped / 0 failures / 0 errors，lint 0 errors / 99 warnings。错误分类/恢复、文件命名/传输和聊天协调/停靠条回归均已执行。handoff/publication 在最终暂存内容上检查，不沿用旧 head 结果。设备继续由独立 QA 独占，本轮未运行仪器测试、未操作模拟器/真机或 adb 安装。
- 已完成评审跟进：修复提交 `b2416b6977359e7dcb441eeb1d91cf10c5dc0c11` 已推送，两批共十二条对应评审线程均已回复并 resolve；本轮只做基线对齐。

- 评审/QA 跟进起点：provider 确认 PR #73 为 OPEN/MERGEABLE，完整 head `cde1644c98d4e6b10d6fc8295fd506fd87376c05`；本地与已 fetch 的远端一致、开始时工作区干净。编排方要求修复断网检查更新暴露英文异常及四项 P2；实际 GraphQL 显示六条线程仍未解决，前两条在当前 head 也未实现，故一并修复旧 error 展示兜底和原操作重试。
- 本轮已实现：`Snapshot.displayFailure()` 兼容持久化/直接写入 error，主文案中文化；所有技术原文放默认折叠且随新错误重置的“技术细节”，首次引导未知错误也不拼接原文。明确 operation 优先于残留 update，componentId 也优先由 operation 判定。
- 本轮已实现：`FailureRetry` 携带原命令及目标版本，从操作启动、失败模型、UI 确认直到 RuntimeService 保留；重装仍 force=true，卸载/切换保持原语义。缺少可重放上下文的错误不提供空重试；组件兼容性失败打开本组件选择器；安装引导透传存储导航；获取版本列表失败或列表为空时先重新获取。
- 本轮已验证：JDK17 下显式 `-PtestBuildType=debug`，完整 Modern JVM、lint、仪器回归源码编译在同一 Gradle 调用中串行通过（5m02s）：1179 tests / 120 个结果文件 / 0 skipped / 0 failures / 0 errors；新增回归类 11 项全部通过；lint 0 errors / 98 warnings。仪器仅编译，不计设备执行。以下 1168 项记录属于上一轮组件对齐。
- 设备约束：`emulator-5554 / Pixel_7_API_34_arm64` 由 android_agent-71 独占 QA，本轮没有启动/操作模拟器、真机或 adb 安装；新增仪器测试不计已执行。

- 已实现：统一错误模型 `AppFailure` 与分类器 `FailureCatalog`；运行状态新增 `Snapshot.failure`；运行失败、安装失败、更新失败和资源原因停止都会生成统一结构；恢复成功后清空。
- 已实现：组件卡折叠区外内嵌错误块并自动展开；DSH 组件卡同样支持；应用更新卡内嵌错误块；DSH 页原生与官方空态都显示恢复卡，含重新启动、清理下载缓存、切换已安装版本；主壳顶栏全局错误条点开详情、执行主动作、复制或关闭，恢复后自动消失。
- 组件对齐跟进：按编排方要求重新 fetch 并执行 `git merge origin/main`，结果 `Already up to date`，基线仍为 `8ba7b4b3ecdc82c17a200dca77ef9420924d76b1`（含 #68/#70/#71/#72）。该组件对齐轮只调整展示：沿用根部 `TanukiDesignTheme`，错误块和顶栏读取 `TanukiDesign.tokens`；错误动作与恢复卡使用 `TanukiPillButton`；错误详情改用 `TanukiBottomSheet`。错误模型、状态机、分类和动作回调保持不变；全局条、安装/更新卡内嵌块与 DSH 页恢复卡调用点均保留。
- 已验证：rebase 到 `origin/main=8ba7b4b` 并完成本轮设计组件对齐后，`:app:compileModernDebugKotlin` 通过；最终串行运行 `:app:testModernDebugUnitTest :app:lintModernDebug --offline` 通过，1168 项 JVM 全部通过（119 个结果文件、0 skipped/0 failures/0 errors），其中 `FailureCatalogTest` 8 项。
- 待验证：Android 设备/模拟器上的实际点击、滚动、深色/大字体/360dp/TalkBack、系统权限弹窗、真实弱网/磁盘满/DSH 崩溃路径；独立 Codex High 审查与 GitHub CI。
- 当前目标包名、分支：`com.tanuki`；PR 源分支 `ao/error-recovery/failure-exit`，PR #73；无运行中的设备任务，未安装 APK。
- 关键事实与可定位源码：`app/src/main/kotlin/com/tanuki/runtime/FailureCatalog.kt`（模型/分类/动作）、`ui/FailureUi.kt`（错误块/顶栏/详情）、`ui/SetupScreens.kt`（恢复卡）、`ui/TanukiUi.kt`（组件卡/顶栏/DSH 页）、`ui/RuntimeComponents.kt`（安装卡）、`ui/ApplicationUpdateCard.kt`（应用更新卡）、`update/AppUpdateFailure.kt`（应用更新映射）。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/runtime/FailureCatalog.kt` | 新增统一错误模型、失败域/原因/动作枚举、文本分类、主次行动选择、重试命令、关闭去重与恢复后隐藏策略。 |
| `app/src/main/kotlin/com/tanuki/runtime/State.kt` | `Snapshot` 新增可空 `failure`，原始 `error` 仍保留脱敏技术文本。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeManager.kt` | 失败时生成统一结构；操作开始/安装或运行成功/用户主动停止时清除；资源原因停止也生成可操作错误。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeComponents.kt` | 组件安装、卸载、切换成功时清除旧失败。 |
| `app/src/main/kotlin/com/tanuki/update/UpdateManager.kt` | DSH 更新检查/暂存/成功/恢复成功时清除旧失败，避免恢复后顶栏残留。 |
| `app/src/main/kotlin/com/tanuki/update/AppUpdateFailure.kt` | 把 APK 应用更新失败映射到同一错误模型，状态离开 FAILED 即消失。 |
| `app/src/main/kotlin/com/tanuki/ui/FailureUi.kt` | 组件内嵌错误块、全局错误条读取设计令牌；动作使用 `TanukiPillButton`，详情使用 `TanukiBottomSheet`；保留技术原文、复制、关闭和主次行动。 |
| `app/src/main/kotlin/com/tanuki/ui/RuntimeComponents.kt` | 安装卡接收匹配错误，折叠状态也显示原因和主行动，失败时自动展开。 |
| `app/src/main/kotlin/com/tanuki/ui/SetupScreens.kt` | 重写运行恢复卡：人话原因、重试/试用/回安全模式、清理下载缓存、切换已安装版本；工作台步骤视图也保留运行恢复入口。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 顶栏全局错误条与详情；Ubuntu/Node/DSH 组件卡接入统一错误与换源/重试/清理/查看存储；DSH 原生页与官方空态复用恢复卡；设置/存储导航入口。 |
| `app/src/main/kotlin/com/tanuki/ui/ApplicationUpdateCard.kt` | 应用更新失败内嵌错误块，动作按上下文触发检查、下载、清理缓存或系统设置。 |
| `app/src/test/kotlin/com/tanuki/FailureCatalogTest.kt` | 原有 8 项 JVM 回归：分类映射、主行动选择、内部术语禁入、恢复后隐藏、关闭去重、复制文本。 |
| `app/src/main/kotlin/com/tanuki/runtime/FailureRetry.kt`、`RuntimeService.kt`、`MainActivity.kt` | 保留原重试命令及目标版本，从失败模型经过确认框传入前台服务；重装保留 force，卸载/切换保持原动作。 |
| `app/src/main/kotlin/com/tanuki/ui/FailureActions.kt` | 顶栏、组件及无专属位置错误共用可测试动作分发；版本为空先获取，存储回调落到实际导航。 |
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingPrepare.kt`、`ui/OnboardingScreen.kt` | 首次引导未知失败不拼接原文；原文保留在默认折叠的技术细节。 |
| `app/src/test/kotlin/com/tanuki/FailureRecoveryFollowupTest.kt`、`OnboardingPrepareTest.kt` | 新增 11 项回归并加强未知错误断言；覆盖旧 error、中文文案、连续操作、原命令/版本、版本与存储动作。 |
| `app/src/androidTest/kotlin/com/tanuki/SetupPresentationTest.kt` | 新增引导存储、兼容性选择和技术细节三条仪器回归源码；本轮不运行设备测试。 |
| `docs/INTERFACES.md`、`docs/TEST-REPORT.md` | 记录重试契约和评审跟进的实际验证边界。 |
| `docs/UPDATE-RECOVERY.md` | 新增“失败出口与恢复卡”说明，记录统一模型、全局条、就地卡、恢复清除和当前限制。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git rebase origin/main` | 早期按当时要求完成，不属于本轮普通 merge；唯一冲突在 `ui/TanukiUi.kt` | 保留 main 的 `TanukiExpandVisibility`、设计令牌与 toggle 动画，同时保留错误块与失败自动展开；无残留冲突标记 |
| `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:compileModernDebugKotlin --offline` | 通过 | rebase 后主源码编译；仅既有 deprecation/告警，无新增错误 |
| `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testModernDebugUnitTest --offline` | 通过，1168 tests / 0 skipped / 0 failures / 0 errors | rebase 后测试报告：`app/build/test-results/testModernDebugUnitTest/`；含 `FailureCatalogTest` 8 项 |
| `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:lintModernDebug --offline` | 通过 | rebase 后 lint 报告：`app/build/reports/lint-results-modernDebug.html` |
| 代码走查 | 完成 | 覆盖运行失败、组件安装/卸载/切换、DSH 更新、APK 更新、DSH 原生/官方页、工作台恢复卡、顶栏错误条 |
| `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew -PtestBuildType=debug :app:testModernDebugUnitTest :app:lintModernDebug :app:compileModernDebugAndroidTestKotlin --offline` | 本轮评审修复后通过，5m02s | 1179 tests / 120 个结果文件 / 0 skipped / 0 failures / 0 errors；`FailureRecoveryFollowupTest` 11 项执行；lint 0 errors / 98 warnings；三条仪器回归源码编译通过，未运行 |
| `git merge --no-commit origin/main` | 本轮普通 merge，main=`6e1e83347b264b588c97fe380214c955bd7ba9c0` | 仅 TEST-REPORT 内容冲突，双方记录均保留；产品源码自动合并，没有额外逻辑修改 |
| `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew -PtestBuildType=debug :app:testModernDebugUnitTest :app:lintModernDebug --offline` | 本轮普通 merge 后通过，4m21s | 1237 tests / 124 个结果文件 / 0 skipped / 0 failures / 0 errors；lint 0 errors / 99 warnings |
| 设备/模拟器 | 未执行 | 设备由独立 QA 独占；本次未申请，不以宿主/编译结果冒充 Android 执行 |

- 哪些是前一轮结果，哪些是本次新验证：1168 项属于早期 rebase/组件对齐，1179 项及仪器源码编译属于前一轮评审修复。本轮普通 merge 后重新完整执行 JVM/lint，通过 1237 tests / 124 个结果文件、0 skipped / failures / errors，lint 0 errors / 99 warnings；本轮未编译或运行仪器测试，未沿用旧 head 的测试或设备结论。
- 未测试、超时、失败和暂未复现的情况：真实断网、真实磁盘耗尽、真实权限拒绝、真实 DSH 崩溃与安全模式、真实应用更新失败、深色/大字体/360dp/TalkBack、长文案换行与顶栏遮挡。编译与 JVM 分类测试不能证明这些界面在设备上的最终表现。

### 未完成与暂停项

- 本轮后续：完成普通 merge 的完整 JVM/lint 与公开检查后统一提交并普通推送，回报新完整 head；已修复线程处理已完成。新 merge head 的独立审查、GitHub CI 与适用设备 QA 由编排方核对，旧 head 的结论不沿用。
- 外部阻塞：Android 设备 QA 由独立 QA 独占，本会话未申请；真实网络/磁盘/崩溃注入不属于本会话授权范围。
- 用户暂停：API Key 保存；真实模型/账号及真机测试未授权本会话。本 worker 不合并、不发布，修复后的合并由编排方负责。

### 下一步

1. 本轮用普通 merge 纳入 `main=6e1e83347b264b588c97fe380214c955bd7ba9c0`，完成检查后以一个 merge 提交正常推送原 PR #73；不 rebase、不强推。
2. 推送后的新完整 SHA 交编排方核对独立审查、GitHub CI 与适用 QA；是否合并由编排方处理，本 worker 不合并、不发布。

### 约束与风险

- 数据/设备/权限边界：未读取或输出真实 API Key、Cookie、token、会话内容或私有日志；未安装 APK、未操作设备、未改共享状态；未改远程/权限/安全问题。
- 关键决策及理由：把分类与动作选择做成纯 Kotlin，避免各页面拼文案并让 JVM 可回归；用 `Snapshot.failure` 而不是新增全局单例，保持状态仍由运行管理器单一来源；全局条关闭只隐藏提示，组件卡与恢复卡继续保留可操作错误；恢复成功或用户主动停止时清除旧失败，避免“一直报错”的错觉。
- 失败尝试与避免重复踩坑的结论：本机默认 JDK 27 无法被当前 Gradle/Kotlin 解析，使用 Homebrew `openjdk@17` 执行验证；未把 JDK 27 的失败当成代码失败。DSH 页恢复卡需要区分运行失败与其他文件失败，已按 `FailureDomain.RUNTIME` 过滤。组件对齐收尾时，受限命令因无法写入共享 Gradle 缓存锁而退出，按同一命令申请更宽权限后执行；补齐关闭按钮前的一次中间 lint 被主动取消，不计通过，以最终串行 JVM/lint 为准。评审修复轮首次未指定 `testBuildType`，默认 probe 未注册 Debug 仪器编译任务，任务图阶段退出；加 `-PtestBuildType=debug` 后最终全部通过，该失败尝试未计入测试通过。
- 回归风险：`FailureCatalog` 依赖中文关键词分类，后续新增错误原文若不含现有标记会落到“操作没有完成”；需要在新错误路径补充测试。组件卡换源动作只切换下载源，不自动重装，仍需用户点安装。

### 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本次未生成、未更新；不涉及覆盖安装、签名或发布。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未覆盖、未发布。
- 提交/推送前清理的过期记录：本任务只有当前这一份失败出口交接，仍用于 PR #73；没有本次范围内可确认过期的其他记录，未删除旧交接。本轮只在同一记录补充普通 merge 对齐结果并合并 `docs/TEST-REPORT.md`；main 带入的 #74/#75 交接仍是有效实现/验证依据，按原文保留，不做跨 PR 清理。此前评审修复的专题结论继续保留；共享状态/印章未改。
- 普通 PR 不刷新共享状态/印章；集成维护方在批量合并后统一运行 `--integrate`。本次未生成源码 ZIP；文档变化会改变公开源码指纹，后续发布需重新打包。

结束前实际执行：

```bash
python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1228-failure-exit.md
python3 scripts/check-publication.py --git
git diff --check
```

<a id="20261111-1246-dock-integration"></a>

## 20261111-1246-dock-integration

- Agent/会话标识：android_agent-62（AO worker）
- 时间：2026-11-11 12:46 CST

### 当前状态

- 基线：`origin/main=7780fbf3ec23d207263e210c6911c338a062d7cd`（#68 合并后、#72 文档提交）；分支 `ao/chat-anchoring/dock-integration`，从会话根分支命名空间 `ao/chat-anchoring` 创建的兄弟分支；PR [#74](https://github.com/leezzou/tanuki/pull/74)，实现提交 `cc9c530`。
- 已实现：
  - 页面纵向顺序改为“消息区 `weight(1f)` → `DshDockColumn` → `DshComposerBar`”；删除 `SessionStrip` 后的独立审批条，把同一个 `DshApprovalEntryBar` 作为 `approvalBar` 插槽传入，避免两条审批条。
  - `approvalVisible = DshApprovalEntry.of(approvalState, selectedId).visible`；目标/待办来自控制器状态，缺失时 `null`，对应条不占高度。
  - `DshCoordinationParser`（web）：只识别 `goal/update`/`goal`/`goals/update`/`session/goal`、`todo/update`/`todos/update` 与 `todo_write`/`write_todo`/`todowrite`/`update_todos` 工具调用；缺失/损坏字段不生成更新，明确目标终态和显式空待办数组保留清除投影，不保留原始 JSON。
  - `DshSessionPage`、`session/follow` 快照与 `event` 帧接入 `DshCoordination`；`DshLiveEvent.Coordination` 增量驱动目标/待办条。
  - `DshDockStateMapper`（ui）：目标状态映射 `RUNNING/PAUSED/BLOCKED`，已完成/已清除隐藏，未知状态由控制器保持已有值；待办状态映射 `PENDING/IN_PROGRESS/COMPLETED`，空清单隐藏。
  - 控制器 `DshNativeState.goal/todos`；选择会话清空，页面/快照/实时事件按字段合并，解析失败不覆盖已知值。
  - `DshDockPlacement` 记录“停靠条高度只压缩消息区、输入栏贴底”的布局契约，配套 JVM 回归。
- 已验证：停靠条接线阶段完整 JVM 1165 tests/119 文件通过；截图反馈修复后完整 JVM **1169 tests/119 文件，0 failures/errors/skipped**（标题解析 15 项、控制器 29 项均通过）。lint **0 errors / 108 warnings / 1 hint**。
- 待验证：真实 DSH 的 goal/todo 事件名与字段、设备高度动画/展开滚动/深色/大字体/TalkBack、独立审查、合并与发布。
- 关键源码：`app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt`、`DshNativeController.kt`、`DshDockStateMapper.kt`（新增）、`app/src/main/kotlin/com/tanuki/web/DshCoordination.kt`（新增）、`DshNativeApi.kt`、`DshMuxProtocol.kt`、`DshLive.kt`、`DshDock.kt`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 审批条移入 `DshDockColumn` 插槽，停靠容器放在 `DshComposerBar` 之前；三条顺序/折叠由组件负责，数据取控制器 `goal/todos`。 |
| `app/src/main/kotlin/com/tanuki/ui/DshDock.kt` | 新增 `DshDockPlacement` 纯布局契约：停靠条高度变化只压缩消息区，输入栏顶部位置不变。 |
| `app/src/main/kotlin/com/tanuki/ui/DshDockStateMapper.kt` | 新增目标/待办投影到组件纯状态的唯一映射；未知状态/空文本/空清单隐藏。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt` | 状态增加 `goal/todos`；选择会话清空；历史页/快照/实时 `Coordination` 按字段合并；解析失败不覆盖。 |
| `app/src/main/kotlin/com/tanuki/web/DshCoordination.kt` | 新增保守的事件投影与解析：白名单事件/工具、字段/条数上限、单行摘要、空值不生成。 |
| `app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt` | `DshSessionPage.coordination`、`parsePage` 解析、follow 帧映射与 `DshLiveEvent.Coordination`。 |
| `app/src/main/kotlin/com/tanuki/web/DshMuxProtocol.kt` | follow `Snapshot` 携带 coordination；`event` 帧解析目标/待办并返回 `DshFollowFrame.Coordination`。 |
| `app/src/main/kotlin/com/tanuki/web/DshLive.kt` | 实时事件新增 `Coordination`；快照带 coordination（默认空，兼容旧调用）。 |
| `app/src/test/kotlin/com/tanuki/DshCoordinationTest.kt` | 新增 7 项事件投影回归：目标摘要/状态、空文本忽略、取最新、待办事件、工具参数（对象/裸数组）、未知事件忽略、空清单隐藏。 |
| `app/src/test/kotlin/com/tanuki/DshDockIntegrationTest.kt` | 新增 5 项接线回归：状态映射、空数据隐藏、三条折叠/展开不重复、缺失不占槽、停靠条高度变化时输入栏顶部不变。 |
| `app/src/test/kotlin/com/tanuki/DshNativeControllerTest.kt` | 新增 4 项控制器回归：历史页驱动目标/待办、实时事件更新、会话切换隐藏、未知状态不覆盖已知目标。 |
| `app/src/test/kotlin/com/tanuki/DshMuxProtocolTest.kt` | 新增 1 项 follow 解析回归：snapshot 与 event 帧携带 goal/todo 投影。 |
| `docs/INTERFACES.md` | 增加目标/待办投影契约与缺失隐藏边界；更新缺口列表。 |
| `docs/NATIVE-UI.md` | 增加输入栏上方三条停靠条接线说明与布局契约。 |
| `docs/NATIVE-PARITY.md` | SESS-14/CHAT-19 更新为原生部分（只读目标/待办停靠条）。 |
| `docs/handoffs/20261111-1246-dock-integration.md` | 本记录。 |

### 截图反馈修复

- `DshNativeApi.kt` 严格按字符串读取标题；JSON null、缺失、空白与字面 `null` 显示“新对话”。新增标题解析回归。
- `DshNativePage.kt` 显示当前会话名及“重命名”，复用 Material 输入对话框，保存中禁用重复操作，失败保留输入/原标题；空态改为“这个会话还没有消息，在下面输入就能开始”，顶部说明改为“和官方界面是同一份会话，随时可切换”。
- `MainActivity.kt` 以 `DshAdminTransportAdapter(nativeClient::call)` 复用现有认证与官方 `session/rename`，本地安全/试用模式拒绝写入。`DshNativeController.kt` 校验标题、采用官方返回值，generation 变化后忽略旧请求结果；补成功、失败/非法输入、会话切换晚到三项回归。
- `TanukiUi.kt` 外层顶部去掉“只读”；`DshComposerBar.kt` 原有插入动作改为“@ 引用”“/ 命令”胶囊。
- 追加修改的首次定向构建因缺少 `java.io.IOException` 导入编译失败，已补齐导入；随后执行完整 JVM + lint，结果以本节下表为准。
- 收到的 CI 失败通知对应旧尝试：`42962321501/job/130332450202`，既有 `NpmAdaptiveWorkerPoolTest.downshiftRetiresExcessWorkersAfterCurrentTaskAndKeepsQueueDraining` 时序失败；同 head 重跑通过，追加修改前的四项 CI 均为 SUCCESS。
- 本次范围内交接清理：沿用唯一记录，没有可确认过期且属于本次范围的其他交接可删除；共享状态不变。
- 真实目标/待办事件名与字段仍未验证；输入框位置回归仅为纯计算，不能替代 Compose 设备验收。空待办/已完成目标残留与跨批次 seq 回退已按下节复审意见修复。

### PR #74 状态投影复审修复

- 用户要求：处理 `PRRT_kwDOVgRm4s62Cfln`、`PRRT_kwDOVgRm4s62Cflp`、`PRRT_kwDOVgRm4s62Cflr` 三条 review，验证并推送后逐条解决；设备 `emulator-5554`（Pixel_7_API_34_arm64）由 android_agent-71 独占 QA，本轮不运行 adb 或操作设备。
- 目标终态：`DshGoalProjection.clearsDock` 区分 `completed/cleared` 与缺失/未知状态；明确终态即使无摘要也保留带 seq 的投影，控制器将旧条置 null。原测试改为真正的未知状态，并新增 running→completed/cleared→旧快照回放回归。
- 空待办：显式空数组产生 `DshTodoProjection(seq, emptyList())`；缺失/损坏及非空但无法解析的数组仍不产生更新。历史批次中的更大 seq 空清单覆盖旧非空清单，实时空清单清除旧条。
- 防回退：`DshNativeState.lastGoalSeq/lastTodoSeq` 独立保存最后应用的序号，含终态/空数组；所有 HTTP 页、follow 快照及增量统一比较，只接受更大 seq。清除后保留序号，切换会话时重置。文本页新旧判断与停靠条投影序号分开处理。
- 新增回归：解析器 3 项（无序历史批次非空→空、损坏/缺失保持旧投影、无摘要终态）；控制器 6 项（终态清除及旧快照、空待办清除及旧快照、延迟 HTTP 页不得回退实时状态/清除、新投影不受旧文本页阻挡、两类序号独立、切换会话重置）。复审修复后完整 JVM **1178 tests/119 文件，0 failures/errors/skipped**；其中解析器 10 项、控制器 35 项全部通过。lint 结果见验证记录。
- 本次范围继续沿用唯一交接，未删除其他 PR 的历史依据，未改共享状态或印章。推送后针对三个已提供 thread ID 直接 resolve，不沿用旧 SHA 的独立批准。

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git rev-parse origin/main` | `7780fbf3ec23d207263e210c6911c338a062d7cd` | #68 已合入的基线 |
| `./gradlew ... :app:testModernDebugUnitTest --tests 'com.tanuki.DshCoordinationTest' --tests 'com.tanuki.DshDockIntegrationTest' --tests 'com.tanuki.DshDockLayoutTest' --tests 'com.tanuki.DshGoalDockTest' --tests 'com.tanuki.DshTodoDockTest' --tests 'com.tanuki.DshNativeControllerTest'` | 通过：定向 6 类 / 64 tests / 0 failed（coordination 7、integration 5、layout 11、goal 7、todo 8、controller 26） | JDK17；不访问真实 DSH |
| `./gradlew ... :app:testModernDebugUnitTest --tests 'com.tanuki.DshMuxProtocolTest'` | 通过（follow 解析 1 项） | 覆盖 snapshot/event 的 goal/todo 投影 |
| `./gradlew ... :app:testModernDebugUnitTest :app:lintModernDebug` | 通过：完整 JVM **1165 tests / 119 个结果文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 108 warnings / 1 hint** | 同一 Gradle 配置；新增/修改文件无 lint 告警 |
| 截图反馈修复后：`export TANUKI_JDK=<JDK17>; source scripts/env.sh; ./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest :app:lintModernDebug` | JVM **1169 tests / 119 文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 108 warnings / 1 hint**；BUILD SUCCESSFUL（7m 45s） | 新增 1 项标题解析、3 项重命名回归；未运行设备 |
| 三条 review 修复后：`source scripts/env.sh; ./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest :app:lintModernDebug` | JVM **1178 tests / 119 文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 108 warnings / 1 hint**；BUILD SUCCESSFUL（15m 47s） | 新增解析器 3 项、控制器 6 项；终态与清空、晚到历史页/旧快照、独立 seq、会话切换 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1246-dock-integration.md`、`python3 scripts/check-publication.py --git`、`git diff --check` | 通过；774 public files / 774 index entries / 0 findings | 普通 PR 不改共享状态 |
| 设备/模拟器/真实 DSH | 未执行 | 设备由独立 QA 独占 |

- 未测试：真实 DSH 目标/待办事件名与字段、目标操作 RPC、设备高度动画与展开滚动、深色/大字体/横屏/分屏/TalkBack。
- 数据边界：没有事件时 `goal/todos` 为 `null`，停靠条不占高度；不使用示例目标、占位待办或默认进度。

### 未完成与暂停项

- 未完成：真实 `goals/*` 暂停/恢复/编辑/清除 RPC 与 `/goal` 气泡；队列卡片；设备验收；独立审查。
- 外部阻塞：真实目标/待办事件字段未在设备确认；若官方事件名/字段与保守白名单不同，停靠条会保持隐藏（不会显示错数据），需要拿到真实样本后扩展解析。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收、合并与发布；本记录不触碰。

### 下一步

1. PR #74 已创建；三条状态投影复审修复验证通过，统一提交并推送后逐条解决指定 thread，回报最终 head；独立 Review 绑定新 SHA。
2. QA 在设备上验证：三条/两条/一条/零条；+N 展开与收起；目标/待办真实数据出现时显示、缺失时隐藏；输入栏在折叠/展开动画中不位移；锚定滚动与审批抽屉行为不变。
3. 拿到真实 goals/todo 事件样本后，按实际字段补充解析；未确认前继续保持隐藏而非猜测。

### 约束与风险

- 数据/设备/权限边界：目标/待办只读解析事件；仅用户明确保存会话名时调用已接入的官方 `session/rename`。不调用未确认的 goals RPC、不读凭据、不操作设备。
- 关键决策：
  - 审批条只保留一份，作为 `DshDockColumn` 插槽；页面不再单独渲染，避免重复。
  - 目标/待办初始缺失即隐藏；后续缺失/损坏保持旧值，明确目标终态或空待办数组清除旧条；未知状态不猜成进行中。
  - 目标操作能力保持默认全 false；暂停/恢复/编辑/清除按钮只显示禁用原因，等 RPC 契约确认后再接。
  - 停靠条不外包动画；高度动画由组件内部完成，输入栏保持在纵向栈最后。
- 已知风险：真实事件名/字段未验证，可能全部隐藏；当前只将明确 `completed/cleared` 状态视为目标清除，其他未确认协议形态继续保持旧值；设备动画与布局未验收。

### 产物与校验

- 本轮不生成 APK、源码 ZIP 或发布附件；不覆盖安装、不发布、不合并。
- PR [#74](https://github.com/leezzou/tanuki/pull/74)，目标 `main`；分支 `ao/chat-anchoring/dock-integration`；实现提交 `cc9c530`。
- 提交前运行 `check-handoff.py --pr`、`check-publication.py --git`、`git diff --check`。
- 普通 PR 不刷新 `AGENT-STATE.md`/`CURRENT.json`/`TEST-REPORT.md`；只新增本记录。

<a id="20261111-1247-file-import-transfer"></a>

## 20261111-1247-file-import-transfer

- Agent/会话标识：android_agent-70（AO Worker）
- 时间：2026-11-11，CST

### 当前状态

- 开始时先 fetch，基线为 `origin/main=732cb7965c8c7db378c70edb70f05f5a50ed30d6`；分支 `ao/file-import-move/p0-file-import-move`，原提交 `2200c0901a3aa0b02f310a4e800111d8e5901f84` 已推送并创建 PR #75，两项 CI 成功。
- 开发期间 main 前进到 `8ba7b4b3ecdc82c17a200dca77ef9420924d76b1`。保留本地集成备份，从 PR 完整 head 恢复源分支后普通 merge main，复用已编译的文件页设计令牌冲突解决；保留已推送历史，后续普通推送，不 force-push。
- 已实现：单文件以去最后扩展名的源文件名建立独立项目，多文件 `导入-yyyyMMdd-HHmm`；项目重名用 ` (2)`、` (3)`；项目内重名文件用 `合同 (2).pdf`。长名称按 UTF-8 完整字符截断并保留常见扩展名，悬空链接也视为名称已占用。
- 已实现：顶部填充色「＋ 导入」，搜索/刷新/排序收进更多菜单；空态导入与新建文件夹；菜单「导入为新项目」及不覆盖说明。导入事件使用独立 ID 并随 Activity 保存，成功后刷新列表、显示「已导入 N 个文件到「目录」」+「打开」，一次性「刚刚导入：目录」提供「打开 / 忽略」。查看器中的显式复制也可显示并打开目标。
- 已实现：手机条目与批量「复制到工作区 / 移动到工作区」，默认最近工作区目录；卷根/链接不能传输，不可删除条目禁用移动，旧系统缺写权限时单独申请。目标确认说明移动删源行为。
- 已实现：同名普通文件保留两者/替换/跳过；目录、链接和不同类型条目不替换。全部应用仅作用于尚未处理的冲突。失败保留已完成项，并列出未完成项。
- 已实现：64 KiB 分块复制，回读 SHA-256，核对源清单、文件版本与摘要后才发布；每项发布前检查实际容量预算，移动前/删源前复核系统授权。只清理本次已校验的源条目，新出现的文件不递归删除。取消清理未提交暂存；发布后删源失败保留目标并提示检查。
- `content://` 查看继续直读提供方，不强制复制。现有 SAF 导入 512 MiB 单次限制及 64 MiB 磁盘余量保留。
- 包名：开发验证使用 `com.tanuki.modern.workspacecheck`；未安装 APK。共享 `AGENT-STATE.md` / `CURRENT.json` 不修改。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/ImportNaming.kt` | 文件/项目安全命名、UTF-8 截断、扩展名与序号、唯一项目发布；不覆盖文件/目录/悬空链接。 |
| `app/src/main/kotlin/com/tanuki/storage/TransferEngine.kt` | 流式复制、长度/摘要/源清单校验、同目录暂存发布、移动安全删源与取消清理；纯 JVM 冲突批处理逻辑。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceTransfer.kt` | 真实批量复制/移动执行器，串联冲突三选、进度、部分失败及容量/权限注入检查。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceStorage.kt` | SAF 导入复用流式校验及可读项目命名，保留 journal、容量检查和恢复策略。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt` | 手机源/工作区目标边界、卷根限制、可删除信息、写权限复核和实际容量检查。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt` | R4.1/R4.2/R4.3 交互：一级导入、刷新/打开/忽略、目标选择、冲突及全部应用、移动权限、阶段/耗时/取消。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 导入事件保存/恢复、SAF 大小元数据、文件页独立写权限申请；串联传输进度与取消。 |
| `app/src/test/kotlin/com/tanuki/ImportNamingTest.kt` | 8 项命名与真实发布回归，含后缀、扩展名、中文/emoji 字节边界和悬空链接。 |
| `app/src/test/kotlin/com/tanuki/TransferEngineTest.kt` | 20 项流式复制/移动/取消/容量拒绝/源变化/摘要损坏/真实批量冲突回归。 |
| `app/src/androidTest/kotlin/com/tanuki/LiveProjectTransferTest.kt` | 同步可读项目名期望。 |
| `app/src/androidTest/kotlin/com/tanuki/FileTransferPresentationTest.kt` | 同步菜单路径与可读项目名。 |
| `app/src/androidTest/kotlin/com/tanuki/FilesBrowserPresentationTest.kt` | 同步搜索/排序/刷新菜单路径、验证运行中一级导入入口，新增导入完成反馈/打开用例；仅编译。 |
| `docs/WORKSPACE-STORAGE.md` | 更新命名、刷新、导入入口、手机传输与权限边界。 |
| `docs/TEST-REPORT.md` | 本轮真实验证摘要；与历史/设备证据分开。 |

### 验证记录

环境为 macOS ARM64、项目 JDK17、现有 Android SDK；Gradle 串行运行，无设备任务。

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `./gradlew :app:testModernDebugUnitTest --tests com.tanuki.ImportNamingTest --tests com.tanuki.TransferEngineTest --console=plain` | BUILD SUCCESSFUL；28 tests，0 failures / errors / skipped | 实际执行命名与流式/批量文件操作，不是仅检查策略枚举。 |
| `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:testModernDebugUnitTest :app:lintModernDebug :app:assembleModernDebug :app:assembleModernDebugAndroidTest --console=plain` | BUILD SUCCESSFUL（18m36s）；1188 tests / 120 个报告文件，0 failures / errors / skipped；lint 0 errors / 109 warnings / 1 hint；开发验证包与测试 APK 构建成功 | `com.tanuki.modern.workspacecheck`；只编译，不执行 Android 仪器测试。 |
| 原提交 `2200c09` GitHub Actions | Host scripts 与 Android build/JVM/lint 均通过 | 仅适用于旧 head；最终 head 另行核对，不沿用旧结论。 |
| 早期完整 JVM | 1098/113 文件；main 集成后 1174/120 文件均通过 | 过程结果，不作为最终代码验证；曾出现既有 NPM 线程池时序失败（期望 8，实际 9），单独和完整重跑通过。 |

- 定向覆盖：命名/重名与不覆盖、长 Unicode/扩展名、复制保留源、移动复制期间源仍在、目录递归、目标校验损坏、同大小同时间戳源改写、目录新增、容量拒绝、发布前取消无半成品、发布后删源失败保留副本、流式未知大小、三种冲突实际执行、全部应用与部分失败进度。
- 失败记录保留：最初编译尝试在非挂起文件遍历回调调用挂起 API，已修正；JDK27 不满足工程要求，使用项目内 JDK17；沙箱拒绝共享 Git 元数据和用户 Gradle 缓存写入后，使用同命令一次权限重试。
- 未执行：任何设备/模拟器安装和 Android 仪器测试，真实 SAF 提供方、系统权限弹窗/撤销、深色/大字体/360dp/TalkBack、实际手机 SD/USB 只读卷、进程被杀/断电。宿主合成文件和 Android 源码编译不代替这些验收。

### 未完成与暂停项

- 本地完整验证已完成；交接与 Git 公开文件检查通过（779 个公开候选、0 findings）。最终 head 的 GitHub CI 由 PR #75 provider 检查核对；独立 Review 和独立设备 QA 待安排，旧 head 的批准或 CI 不沿用。
- 设备 QA 为独立会话职责，不将实现 worker 的宿主结果当设备证据；无真实模型配置要求。
- 用户暂停与授权边界不变：API Key 保存、真实模型/账号、真机验收、合并与发布均不执行。

### 下一步

1. 本地候选与普通 PR 交接已完成，以 `leezzou <115924933+leezzou@users.noreply.github.com>` 身份提交，普通推送更新原 PR #75，核对当前 head 的 GitHub CI。
2. 已向编排方请求是否启动 AO 原生独立审查及独立 QA；获得同意后再触发，审查绑定最终完整 SHA，完成前不记为已通过。
3. 独立 QA 检查 SAF 命名与重名、列表刷新与打开、手机权限与只读卷、复制/移动/冲突/取消/删源失败，以及浅深色/大字体/360dp/TalkBack；合并/发布需另行授权。

### 约束与风险

- 目标替换只支持同类型普通文件；目录冲突的批量替换安全回退为保留两者，既有目录内容不会整体删除。
- 目录复制存路径/大小/版本清单，不存文件正文，拒绝链接、特殊文件和超过 128 层目录；超大目录统计和多次摘要核验会增加耗时，阶段/耗时保持可见。
- 移动没有跨应用文件锁；复制期间的源变更会拒绝或停止删源，发布后删源失败会保留目标。应停止其他应用对源的写入；不承诺恶意同 UID 并发或断电条件下的事务隔离。
- 本轮新增/修改的第一方注释与文档使用中文，不引入外部参考实现名称。系统权限与 DSH 原生授权分开，不更改 RPC 或自动开放 AI 写入。

### 产物与校验

- 只产生用于验证的开发 APK 与测试 APK，不覆盖安装、不签发正式包、不创建 tag/Release、不上传附件。开发包沿用 0.1.12/code14，`app-modern-debug.apk` 为 17,247,521 字节，SHA-256 `4fde01ef31fc2f2d71f05c37a106ef9ebb2b43c42389312e342dc065ffe5c413`；包名由输出元数据确认，不把它当作正式签名候选。
- 源码候选包检查只在系统临时目录的公开源码副本运行 `scripts/package-source.py`，核对 `SOURCE-SHA256SUMS` 并执行 `scripts/check-publication.py --archive`；不把生成物、日志或本机配置纳入 PR，不向用户交付临时 ZIP。
- 过期交接核对：审计是本任务验收依据，文件页布局记录仍含未完成设备 QA，故保留；未确认本范围内有可删除的过期独有记录，不跨 PR 清理。当前唯一交接修订在本文件，不新增重复记录。
- 普通 PR 不刷新共享状态或印章。提交前执行：`python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1247-file-import-transfer.md`、`python3 scripts/check-publication.py --git`、`git diff --cached --check`；暂存区已解决冲突，普通 merge 的目标为 `8ba7b4b3ecdc82c17a200dca77ef9420924d76b1`。源码包与 CI 结果另在 PR 验证记录核对，未获独立审查/设备 QA 不能合并。

<a id="20261111-1300-lan-discovery-ui"></a>

## 20261111-1300-lan-discovery-ui

- Agent/会话标识：android_agent-72（AO worker）
- 时间：2026-11-11 13:00 CST（开始）

### 当前状态

- 已实现：单一顶栏标题，内容按「当前主机状态 → 局域网发现的电脑 → 我的连接 → 其他方式」排列；空态命令复制和实际扫码入口；安全一句话与折叠技术细节；仅本页隐藏运行时/安装 banner。
- 已实现：`_tanuki-remote._tcp` 发现、串行解析、主机标识去重、在线优先稳定排序、有效期清理、退出后旧解析不更新新页面。广播可见不等于 SSH/DSH 已验证。
- 关键限制：现有 TXT 没有平台/操作系统/DSH 版本，`ver` 为协议版本，列表明确显示“平台未提供 · 协议 v1”；没有从名称猜测平台或改主机端协议。摘要 `fp` 不写入 known_hosts，不冒充 SSH 指纹。
- 已验证：首版完整 JVM 1180 项、119 结果文件，0 失败/错误/跳过；LAN 17、向导 49、进度 7 项通过；debug 组装/AndroidTest 源码编译/lint/依赖清单通过（16m47s）；lint 0 errors / 110 warnings / 1 hint。主机 mDNS 20 项与交接/公开扫描通过。
- 待验证：独立 Android QA、真实同 Wi-Fi mDNS/路由器隔离、相机扫码、系统权限拒绝与设置恢复、页面手势/深色/大字号、真实 sshd/DSH；独立审查与 PR CI 以最终 head 为准。
- 包名：验证构建使用 `com.tanuki.modern.workspacecheck`；未安装、未操作设备、未发布附件。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/lan/LanDiscovery.kt` | TXT 严格解析、发现模型、去重/排序/有效期、单次权限策略、空态文案、带代次的串行解析队列。 |
| `app/src/main/kotlin/com/tanuki/lan/LanDiscoveryController.kt` | NsdManager 发现与解析适配、主线程回调、5 秒重扫、停止回调后重启、多播锁与权限撤销处理。 |
| `app/src/main/AndroidManifest.xml` | 附近设备、多播锁权限；扫码相机为可选硬件。 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteHostWizard.kt` | 局域网点选预填入口、无效剪贴板保留已有输入、统一忙碌/接纳门禁；认证与真实 SSH 指纹确认继续复用。 |
| `app/src/main/kotlin/com/tanuki/runtime/RemoteHostController.kt` | 测试提交返回是否接纳；忙碌或等待指纹时拒绝新尝试，不改本次配置。 |
| `app/src/main/kotlin/com/tanuki/ui/RemoteHostSettings.kt` | 页面生命周期/下拉刷新、发现列表、确认、扫码/粘贴、空态与安全折叠。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 远程页独立滚动容器，保留单一顶栏标题，本页隐藏无关运行时 banner；测试回调返回接纳结果。 |
| `app/src/main/kotlin/com/tanuki/ui/OperationProgress.kt` | 按调用页隐藏运行时进度与安装取消；工作台/DSH 保留原入口。 |
| `app/build.gradle.kts`、`THIRD_PARTY_NOTICES`、`licenses/` | 实际扫码依赖与必要许可清单。 |
| `app/src/test/kotlin/com/tanuki/LanDiscoveryTest.kt`、`RemoteWizardTest.kt` | TXT、重复/在线/过期、队列迟到/重入/服务消失、权限降级、空态与向导预填回归。 |
| `docs/INTERFACES.md`、`REMOTE-HOST.md`、`REMOTE-CONNECT.md`、`TEST-REPORT.md` | 手机端消费契约、入口、限制、验证与未验说明。 |
| `docs/handoffs/20261111-1300-lan-discovery-ui.md` | 本轮唯一交接。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git fetch origin` / `git ls-remote origin refs/heads/main` | 通过；基线 `8ba7b4b3ecdc82c17a200dca77ef9420924d76b1` | 首次 fetch 因 worktree 外 Git 元数据写权限受阻，获准后原命令成功；没有以本地 main 代替远端。 |
| `./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:compileModernDebugKotlin` | 首版通过（4m37s） | JDK17、Android SDK36、macOS ARM64；此后实现继续修正，最终验证见下一行。 |
| `./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -I scripts/dependency-inventory.init.gradle -x buildNativeRuntime :app:assembleModernDebug :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest :app:lintModernDebug :app:tanukiDependencyInventory` | BUILD SUCCESSFUL（16m47s）；1180 tests / 119 结果文件 / 0 failures/errors/skipped；lint 0 errors / 110 warnings / 1 hint | 同一份冻结产品/测试代码；LAN 17、向导 49、进度 7 项全通过。发现控制器有 1 项 UseKtx 风格建议；没有设备执行。 |
| `python3 scripts/collect-component-licenses.py` | 通过；93 个运行归档 | 本轮新增 2 个扫码依赖；根第三方声明保留必要 Apache-2.0 声明，Core 的许可继承父 POM，收集器仍如实标记其直接 POM 未声明。 |
| `python3 scripts/remote-relay/test_mdns.py` | 20 项通过 | 离线固定报文/假进程，无真实手机网络验收。 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1300-lan-discovery-ui.md`、`python3 scripts/check-publication.py --git` | 通过；0 findings | 提交前复查暂存与链接；不读被忽略凭据。 |

- 本表只有本轮新执行结果；没有引用历史测试数量冒充重跑。
- 失败保留：首轮 Gradle 命中工作区外缓存锁权限，获准后继续；首轮编译抓到 TXT map 可空字段，已修为缺失拒绝后编译通过。首次分支切换受 Git 元数据权限阻挡，确认 HEAD/改动不变后同命令成功。
- 首次定向 JVM：70 项、1 失败，失败为 `lostInFlightServiceCannotBecomeOnlineAgainFromStaleResult`。构建过程中继续修改了队列和用例，产品类先编译、测试类后编译，导致新用例跑到旧产品类；此轮不计通过。随后冻结产品/测试代码，重新编译并执行完整验证，不把不同快照的局部结果拼成通过。
- 未测试：真实组播网络/真实相机/系统权限交互、页面截图与无障碍、Android 仪器执行、真实 sshd/中继/DSH、真机/低 API/弱网。宿主结果不代表设备结果。

### 未完成与暂停项

- 未完成门禁：独立当前 SHA 审查、Android QA 和 provider CI；审查先请求用户或编排方同意，由编排方安排独立 QA。本次实现与宿主验证已完成。
- 外部阻塞：无。
- 用户暂停：合并、发布、API Key 保存、真实模型/账号和真机验收保持暂停。

### 下一步

1. 在本分支 PR 的当前 head 上核对 provider CI；最终 PR URL 与 SHA 由 provider 和 AO 完成报告记录。
2. 请求编排方确认是否启动 AO 原生独立审查，并安排绑定最终 SHA 的独立 Android QA；本会话不自行占用设备、不合并、不发布。

### 约束与风险

- 数据/设备/权限边界：不读真实凭据、Cookie、用户会话/备份；测试数据为合成；扫码结果只在内存走现有严格配对解析器，不保存图片或写日志。
- 关键决策：发现仅用于展示和预填；真实 SSH 信任仍来自既有公钥探测/显式确认。请求前持久记录已询问，拒绝/进程重建后不会反复弹窗；系统设置授权返回时恢复发现。所有 NSD 回调串行回到主线程，解析队列带可见代次并处理服务消失。
- 网络/平台风险：AP 隔离、组播过滤会导致空态；NSD 的“在线”是近期可见，不能证明 SSH 入站可达。旧 Android 系统没有取消单次解析 API，退出后只丢弃结果，仍需等待系统回调回收占位；API34+ 尝试停止解析。
- 失败尝试的结论：不能用固定延时假定 NSD 已停止；不能每轮重扫先把全部电脑标为离线；不能给“扫码”标签绑定纯粘贴页面。

### 产物与校验

- PR：[#76](https://github.com/leezzou/tanuki/pull/76)，源分支 `ao/lan-discovery/mobile`，目标 `main`；最终完整 head 见本会话 AO 完成报告/provider PR，不合并、不发布。
- APK/源码 ZIP：只做验证构建，未交付 APK/正式源码 ZIP、tag 或 Release；许可清单在验证后按实际解析归档补齐。源码候选归档与校验只在系统临时目录检查，不作为用户取用附件。生成日志/中间件不作为用户产物。
- 覆盖安装：无；没有设备写入或签名迁移。
- 过期交接清理：已核对本任务直接依赖的主机广播与配对交接；仍含独有协议/验证与设备未完成项，本次保留。没有属于本任务且已完全过期可删的记录；不做跨 PR 清理。
- 普通 PR 不刷新共享状态/印章；由集成维护方在合并后处理。

### PR #76 复审修复

- 继续同一分支/PR：已重新登记归属，provider 与本地均为 `45f61218db6e124afb199d00c60e8de88d0d5c25`，干净工作树下 fetch 后按完整 SHA 刷新分支并核对；没有替换 PR。
- `PRRT_kwDOVgRm4s62DIwC`：无效旧剪贴板现在也先检查是否可覆盖；扫码或手动输入保留链接、核对码与字段校验状态，空输入仍展示无效剪贴板错误。新增“有效扫码结果/手动内容 + 残缺旧链接”回归。
- `PRRT_kwDOVgRm4s62DIwF`：LAN、扫码/粘贴/手动和编辑入口禁用并在回调重查；相机返回与确认同样重查。测试接纳返回 Boolean，控制器拒绝时不替换尝试配置，向导不再等待其他连接结果；阶段/指纹/成功/失败按本向导已接纳配置过滤，确认/取消也核对归属。新增延迟 A 的指纹/成功/失败、UI 陈旧空闲但控制器忙碌、只接收自己配置三项回归。
- 新增共 4 项 JVM 回归；冻结代码后完整流程通过（4m02s），1184 tests / 119 结果文件 / 0 failures/errors/skipped，RemoteWizardTest 53 项全通过，lint 0 errors / 110 warnings / 1 hint。上一版 1180 项/CI 通过只属于旧 head，最新 CI/独立审查仍须绑定推送后的新 SHA。
- 线程在修复推送后使用提供的线程 ID 直接解决；新 head 仍需独立审查与 CI，不沿用旧 head 的任何审查结论。
- 设备保持由独立 QA 会话独占，本次没有启动/操作模拟器或真机、没有 adb 安装。未测范围仍为真实 NSD/权限/相机/页面交互与真实 SSH/DSH。
- 清理复核：沿用本任务唯一记录；相关旧广播/配对记录仍有独有协议与未验事项，继续保留。未修改共享状态/印章。

- 复审验证首轮失败保留：产品/仪器源码编译与 debug 组装通过；1184 项中 1 项新回归失败。原因是已信任主机跳过 `probeHostKey`，测试假传输只在该入口延迟，导致“仍忙碌”断言提前完成；把同一延迟信号应用到认证入口，并显式核对三种终态后重新冻结测试重跑。不是沿用失败结果计通过。

- 复审修复最终命令：`./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:assembleModernDebug :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest :app:lintModernDebug`，BUILD SUCCESSFUL（4m02s）；没有更改依赖/许可清单，没有设备执行。提交前重跑 `check-handoff.py --pr`、`check-publication.py --git` 与源码候选校验。修复与文档收尾统一一次提交/推送，再解决两条原线程。

### 按要求同步 main（仅合并，不运行验证）

- 本次起点为 PR #76 head `cd6f1db7d14df3bc5e2e16ffc1ff0672b2111eb3`，工作树干净；实际 fetch 后 main 为 `6b0b0b3fe4e327fb62fd30ddcbd979f2737ab446`，普通 merge，无 rebase/强推。
- 仅两个文本冲突：`TanukiUi.kt` 保留 main 的存储导航/错误恢复接线，同时保留本 PR 的远程页独立滚动与下拉刷新入口；移除旧远程页分支。`TEST-REPORT.md` 整份取 main，`AGENT-STATE.md`/`CURRENT.json` 没有添加本分支改写。文件页/主题/存储实现沿用 main；NSD 与两项已修复 P2 保留。
- 按编排方最新提速要求，本轮没有构建、没有运行测试或 lint，也未操作设备；上文 1184 项属于合并前结果，不能作为本次集成后验证。推送后 CI 由编排方观察，独立审查须绑定新 head。
- 沿用本任务唯一交接；旧广播/配对记录仍有独有协议与未验事项，未做跨 PR 清理。不合并 PR、不发布。

<a id="20261111-1425-landscape-copy-result"></a>

## 20261111-1425-landscape-copy-result

- Agent/会话标识：android_agent-75（DSH Worker）
- 时间：2026-11-11 14:25 CST（UTC+8）

### 当前状态

- 已实现：
  - 横屏以实际可用宽高判定；顶部栏 48dp，编码提示单行 24dp，底部工具条 48dp，正文用剩余高度。
  - 411dp 高横屏（2400×1080、约 411dp）正文约 291dp / 70.8%；360dp 高约 240dp / 66.7%，均在 60% 以上。
  - 极短横屏若显示编码提示会跌破 60%，自动收起提示；当前编码仍在底栏显示，可从 Aa 面板重新打开编码菜单。
  - 「Aa」面板包含搜索、字号、行号、自动换行、文本编码、复制本段；底栏包含上一段/下一段、位置状态、导出和更多操作。
  - 竖屏继续走原顶部栏、原工具行、原分页行和原底部操作栏，未换布局。
  - 复制/移动结果按原因生成文案：同名「N 个文件同名已存在，已跳过（未覆盖）」+「查看」；空间不足「…请清理空间后重试」+「清理空间」；权限「…请授权后重试」+「去授权」；混合原因「查看」后按组列出文件名。
  - 工作区内复制/移动和手机存储→工作区批量传输共用同一结果模型；批次开始前的空间/权限失败也进入分类结果。
- 已验证：最终源码全量 JVM 1233 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 110 warnings / 1 hint；定向 18 项通过。
- 待验证：模拟器/真机横屏实际像素分配、真实同名冲突/空间不足/权限拒绝流程、TalkBack 与 1.3x 字体下的横屏点击区域。
- 当前分支：`ao/landscape-reader/viewer-copy-result`，基于 `origin/main=6e1e833`（含 PR #75）。提交后以 PR head 为准。
- 关键源码：`app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt`、`WorkspaceFilesPage.kt`、`FileViewerLayout.kt`、`FileOperationOutcome.kt`、`app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/FileViewerLayout.kt` | 新增横屏 48/24/48dp 尺寸、60% 正文下限、高度预算和极短屏提示自动收起判定 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | 按实际宽高进入横屏紧凑布局；Aa 面板、单行编码提示、48dp 底栏；竖屏原路径保持 |
| `app/src/main/kotlin/com/tanuki/ui/FileOperationOutcome.kt` | 新增复制/移动结果模型：异常分类、分组文案、恢复动作、手机传输报告映射 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt` | 旧通用失败文案替换为分类结果 Snackbar + 详情弹层；接入工作区复制和手机传输 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 空间不足动作打开系统存储管理，失败时回退应用信息 |
| `app/src/test/kotlin/com/tanuki/FileViewerLayoutTest.kt` | 补横屏判定、411/360dp 高度预算、60% 下限、提示开合、极短屏收起回归 |
| `app/src/test/kotlin/com/tanuki/FileOperationOutcomeTest.kt` | 补同名/空间/权限/其他分类、精确文案、动作、混合原因、传输报告映射 |
| `docs/handoffs/20261111-1425-landscape-copy-result.md` | 本记录 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git fetch --no-write-fetch-head origin main` | 成功；origin/main `6e1e833` | 基线核对 |
| `git merge --ff-only origin/main` | 快进 `d80d660..6e1e833` | 含 PR #75 文件页传输重构 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.FileViewerLayoutTest' --tests 'com.tanuki.FileOperationOutcomeTest' -x buildNativeRuntime` | 通过，18 tests / 0 failures | JDK17；横屏预算与结果分类 |
| 同命令加 `--tests 'com.tanuki.NpmAdaptiveWorkerPoolTest'` | 通过 | 该时序用例在全量首跑时曾失败 1 次，单跑通过 |
| `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest :app:lintModernDebug` | BUILD SUCCESSFUL（4m18s）；1233 tests / 123 文件 / 0 failures / 0 errors / 0 skipped；lint 0 errors / 110 warnings / 1 hint | 最终源码；未运行 Android 仪器测试 |
| `git diff --check` | 通过 | 提交前检查 |

- 前一轮结果与本次新验证分开：以上均为本次执行，未引用历史测试数字。
- 首轮全量 JVM 曾因 `NpmAdaptiveWorkerPoolTest` 时序断言失败 1 项，单跑通过后最终全量通过；不把该次失败当作产品回归。
- 未测试：设备横屏真实布局、真实复制冲突/空间不足/权限失败、TalkBack、1.3x 字体横屏、旋转后状态保持。

### 未完成与暂停项

- 未完成：PR 创建后需要独立审查和适用设备 QA；本次不合并、不发布。
- 外部阻塞：设备由独立 QA 独占，本会话未安装、未操作设备。
- 用户暂停：真实账号/模型验收、合并、发布均未授权。

### 下一步

1. 推送 `ao/landscape-reader/viewer-copy-result` 并创建 PR；PR 正文写明横屏结构、文案映射、测试和未验证项。
2. 由编排者安排当前 head 的独立 Codex High 审查；若审查要求改动，按 PR 流程继续修复。
3. 独立 QA 在横屏 2400×1080、1.3x 字体和深色下复测 QA-04；用真实同名/空间/权限场景复测 QA-09。

### 约束与风险

- 未读取真实 Key、Cookie、token、会话或备份；未安装设备、未操作独立 QA 设备。
- 中文注释；author/committer 使用 `leezzou <115924933+leezzou@users.noreply.github.com>`。
- 横屏判定基于实际宽高，不绑定具体机型；极短横屏会收起编码提示以守住 60%。
- 已知风险：结果提示仍是 Snackbar，自动消失后无法再次打开详情；详情弹层最多列 12 个文件名并提示剩余数量。
- 竖屏未改布局，但新增的 `BoxWithConstraints` 包裹层仍需设备回归确认无额外测量开销。

### 产物与校验

- 本次无 APK、无源码 ZIP 发布；PR 只含源码、测试和本交接。
- 未覆盖安装、未签名、未合并、未发布。
- 提交前未发现本次范围内可删除的过期交接记录；未改 `AGENTS`、`AGENT-STATE.md`、`CURRENT.json` 或其他共享状态。
- 待执行：`python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1425-landscape-copy-result.md`、`python3 scripts/check-publication.py --git`。

<a id="20261111-1442-qa10-qa07"></a>

## 20261111-1442-qa10-qa07

- Agent/会话标识：android_agent-74（AO worker）
- 时间：2026-11-11 14:42 CST

### 当前状态

- 基线：`origin/main=d80d6609b1042351f2dcb3c6fc015ead9ea8cc9e`（PR #74 合并后）；分支 `ao/device-qa-fixes/qa10-qa07`，从会话根分支 `ao/device-qa-fixes/root` 的命名空间创建。
- 已实现：
  - `DshNativePage` 顶部增加“＋ 新建会话”，空态增加“＋ 新建会话”；`DshNativeController.createSession()` 一次点击调用官方 `session/create`，重读列表并进入新会话。
  - 新增 `DshSessionSync` 进程内失效广播；聊天页与会话管理页在创建/重命名/归档成功后广播，另一侧自动回到官方 `session/list` 重读；外部变更只更新会话条，不取消当前实时流/消息。
  - 阅读页 `openedEntry` 改为 `rememberSaveable` + 偏好存储；`WorkspaceFileViewer` 读取/回写编码、滚动偏移和行号开关；用户主动换编码/翻页仍回顶部，配置重建与进程恢复不再重置位置。
- 已验证：完整 JVM **1195 tests / 121 个结果文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 108 warnings / 1 hint**（与基线一致，无新增告警）；modern debug Kotlin 与 AndroidTest Kotlin 均编译通过；定向 `DshSessionSyncTest` 5 项及既有控制器回归通过。
- 待验证：仪器测试仅编译未在设备执行；深色/字体缩放/旋转、360dp、真实 DSH 创建/重命名/归档链路待独立 QA。
- 关键源码：`app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt`、`DshNativePage.kt`、`DshSessionSync.kt`（新增）、`DshSessionManagerController.kt`、`WorkspaceFilesPage.kt`、`WorkspaceFileViewer.kt`、`ReaderState.kt`（新增）、`TanukiSaveableState.kt`。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/DshSessionSync.kt` | 新增会话列表失效广播；只传变更信号，避免两边各存一份会话副本。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt` | 新增 `createSession()`/`canCreateSession`；新增外部列表同步；创建/重命名后广播；停止后不响应外部变更。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 顶部与空态增加一键新建入口；创建中禁用并显示进度。 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerController.kt` | 订阅共享广播并自动刷新；创建/重命名/归档后广播；隐藏时忽略外部变更。 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerActivity.kt`、`MainActivity.kt`、`TanukiApp.kt` | 接入进程内 `DshSessionSync` 与官方 `session/create`；安全模式拒绝写入。 |
| `app/src/main/kotlin/com/tanuki/ui/ReaderState.kt` | 新增阅读会话状态、归一化与 `SharedPreferences` 存储；只保存路径/编码/偏移/行号，不保存文件内容。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiSaveableState.kt` | 新增 `ReaderViewStateSaver` 与 `WorkspaceEntrySaver`，保存 `linked` 防止符号链接误判可编辑。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt` | `openedEntry` 改 `rememberSaveable`；配置重建/进程恢复同一文件；关闭时清理记录。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | 阅读状态初始值/回调；移除非用户操作的滚动归零；滚动停止后持久化。 |
| `app/src/test/kotlin/com/tanuki/DshSessionSyncTest.kt` | 新增 5 项 JVM 回归：聊天新建双侧同步、管理器新建/归档同步、阅读状态归一化。 |
| `app/src/androidTest/kotlin/com/tanuki/ReaderConfigChangeTest.kt` | 新增仪器回归：偏好存储往返；长文本滚动/关行号后重建 Activity，回同一文件同位置。 |
| `docs/NATIVE-UI.md`、`docs/NATIVE-PARITY.md`、`docs/WORKSPACE-STORAGE.md` | 更新一键新建/自动同步/阅读状态恢复的实现事实与未验证项。 |
| `docs/handoffs/20261111-1442-qa10-qa07.md` | 本记录。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git fetch origin main` / `git rev-parse origin/main` | `d80d6609b1042351f2dcb3c6fc015ead9ea8cc9e` | 最新 main 基线；从该 SHA 建分支 |
| `./gradlew ... :app:compileModernDebugKotlin` | 通过 | JDK17；未运行设备 |
| `./gradlew ... :app:testModernDebugUnitTest --tests 'com.tanuki.DshSessionSyncTest'` | 通过：5 tests | 内存合成会话，不连接 DSH |
| `./gradlew ... :app:testModernDebugUnitTest --tests 'com.tanuki.DshSessionSyncTest' --tests 'com.tanuki.DshNativeControllerTest' --tests 'com.tanuki.DshSessionManagerControllerTest'` | 通过 | 既有控制器回归与新增同步回归 |
| `./gradlew ... :app:compileModernDebugAndroidTestKotlin` | 通过 | 仪器测试仅编译，未在设备执行 |
| `./gradlew ... :app:testModernDebugUnitTest :app:lintModernDebug` | 通过：JVM **1195 tests / 121 文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 108 warnings / 1 hint** | JDK17；未运行设备。首次全量运行唯一失败为既有 `NpmAdaptiveWorkerPoolTest` 时序断言，单独复跑及后续全量均通过 |

- 未测试：完整 JVM 全量、lint、设备/模拟器仪器执行；深色/字体缩放/旋转实机；真实 DSH 的 `session/create`/`rename`/归档与列表可见性；360dp、1.3x 字体、TalkBack。
- 数据边界：所有测试使用合成会话与合成文本文件；不读取真实会话、凭据或用户文件。

### 未完成与暂停项

- 未完成：PR 创建与推送、当前 head 的独立审查。
- 外部阻塞：设备/模拟器由独立 QA 独占，本 worker 不运行仪器测试；真实 DSH 链路需独立 QA 绑定 head 复测。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收、合并与发布；本记录不触碰。

### 下一步

1. 推送分支并创建 PR，正文写明改动摘要、测试、未验证项；不合并、不发布。
2. 由编排者安排当前 head 的独立审查；QA 在独立设备上验证一键新建、双边同步、深色/字体缩放后的阅读位置。
3. 根据 review/CI 结果在当前分支继续修复；每次推送后重新绑定新 SHA 的审查与 QA。

### 约束与风险

- 数据/设备/权限边界：新建/重命名/归档只走已接入的官方 `session/*`；安全/试用模式拒绝写入；不读凭据、不操作设备。
- 关键决策：
  - 共享广播只传失效信号，两侧仍以官方 `session/list` 为准，不建立会漂移的本地会话副本。
  - 外部变更只刷新会话条，保留当前消息和实时流；当前会话被归档才切换。
  - 阅读状态持久化只存路径与显示位置，不存正文；`linked` 一并保存，避免恢复后放宽可编辑判定。
- 已知风险：官方列表在创建后短暂滞后时，聊天页用“新会话”占位；下一次官方刷新会替换为真实标题。仪器测试用 Activity 重建模拟配置变更，不能替代真实深色/字体缩放/旋转 QA。

### 产物与校验

- 本轮不生成 APK、源码 ZIP 或发布附件；不覆盖安装、不发布、不合并。
- 分支 `ao/device-qa-fixes/qa10-qa07`；PR [#79](https://github.com/leezzou/tanuki/pull/79)，目标 `main`，不合并、不发布。
- 提交前运行 `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1442-qa10-qa07.md`、`python3 scripts/check-publication.py --git`、`git diff --check`。
- 本次范围未发现可确认过期且属于本范围的交接记录，未删除其他 PR 的历史依据；不改共享状态或印章。

<a id="20261111-1456-text-page-boundary"></a>

## 20261111-1456-text-page-boundary

- Agent/会话标识：android_agent-76
- 时间：2026-11-11 14:56 CST 开始

### 当前状态

- 基线：先核对远端并 fetch，`origin/main=6b0b0b3fe4e327fb62fd30ddcbd979f2737ab446`。从本会话 root 建同级分支 `ao/paging-boundary/page-char-boundary`，未集成其他并发任务。
- 已实现：非 EOF 页从页尾向前最多退 3 字节，逐个严格解码并完成 flush，取最长合法前缀；文本解码与 `nextOffset` 使用同一前缀。未消费字节由下一页重新读取，继续沿用第一页编码。
- 已实现：切点相当于本页完整输入时完成解码收尾；已知非空来源读不到字节时抛中文读取失败，不能伪造游标跳过数据；空文件停止，下一页单字符可推进，上限内放不下完整字符时停止空尾页。
- 已验证：最终源码完整 JVM 1243 tests / 124 文件 / 0 failures / 0 errors / 0 skipped，其中 `TextFileReaderTest` 27/27；lint 0 errors / 109 warnings / 1 hint；工作区 debug app 与测试 APK 构建通过（14m31s）。
- 待验证：PR 当前 head CI、独立审查、Android 平台解码器与真实查看器翻页；PR URL、完整 head 与后续结果以 provider/AO report 为准。验证包 `com.tanuki.modern.workspacecheck`，本会话未安装。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/TextFileReader.kt` | 显式严格解码试探页尾，补完整输入的 flush，不依赖解码器消费位置代表字符边界；非空来源读取失败时防止游标循环或跳字节。 |
| `app/src/test/kotlin/com/tanuki/TextFileReaderTest.kt` | 四组字符的全部内部切点，下一页单字符与中文后缀，文件及已知/未知大小顺序流，逐字节还原与无替代字；读不到字节的来源及上限空尾页保护。 |
| `app/src/androidTest/kotlin/com/tanuki/TextFilePagingBoundaryTest.kt` | 四项 Android 回归，覆盖 9 个内部切点及两类后缀，共 18 个合成文件场景；只写测试缓存。 |
| `docs/WORKSPACE-STORAGE.md` | 说明读取窗口、完整字符游标、flush、空页与非法输入边界。 |
| `docs/TEST-REPORT.md` | 记录最终实际验证结果与设备未验证项。 |
| `docs/handoffs/20261111-1456-text-page-boundary.md` | 本记录；未修改共享状态或印章。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git ls-remote origin refs/heads/main`、`git fetch --no-write-fetch-head origin main` | 通过，远端为任务给定完整 SHA | 普通 fetch 写工作区外 FETCH_HEAD 被拒；无 FETCH_HEAD 写入的 fetch 完成，未切到其他工作区。 |
| `source scripts/ao-worktree-env.sh; ./gradlew -PtestBuildType=debug :app:testModernDebugUnitTest --tests 'com.tanuki.TextFileReaderTest'` | 第一轮 25 tests / 0 failures / 0 errors / 0 skipped | 四组新增用例执行；此时尚未补 flush 和额外边界保护。首次离线运行缺 `jsch:2.28.8`，联网解析后通过。 |
| `./gradlew -PtestBuildType=debug :app:testModernDebugUnitTest :app:lintModernDebug` | 第一轮 BUILD SUCCESSFUL，9m42s；1241 tests / 124 文件 / 0 failures / 0 errors / 0 skipped；lint 0 errors / 109 warnings / 1 hint | 宿主 JDK17/macOS ARM64；不替代最终源码结果。 |
| `./gradlew --console=plain -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:testModernDebugUnitTest :app:lintModernDebug :app:assembleModernDebug :app:assembleModernDebugAndroidTest` | 最终 BUILD SUCCESSFUL，14m31s；1243 tests / 124 文件 / 0 failures / 0 errors / 0 skipped；lint 0 errors / 109 warnings / 1 hint | app/test APK 构建成功，四项 Android 专项回归编译通过、未执行；未跳过原生构建任务。此前一次候选验证主动中断以补齐 flush，不算通过。 |
| `aapt2 dump badging`、`aapt2 dump xmltree --file AndroidManifest.xml`、SHA-256 | 包名、runner 与 target 核对通过 | app 为 `com.tanuki.modern.workspacecheck` / 0.1.12 / code14 / target34，17,330,545 字节，SHA `0d2149b82e8c4bda595544ca4c7b716f644537383c5a9574b147bcf2de86b8dd`，含 4 个 ARM64 原生库；测试包为 `.workspacecheck.test`，runner `AndroidJUnitRunner`，target `.workspacecheck`，SHA `b4ec84fbf96448e61390c7c8209bd4af9b231236dd72e329bd68c01b515377cc`。 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1456-text-page-boundary.md`、`python3 scripts/check-publication.py --git` | 交接检查通过；公开边界 794 public files / 792 index entries / 0 findings | 提交暂存后再次检查实际索引；普通 PR 不刷新共享印章。 |

- 全部执行结果均来自本会话；其他 PR 和历史设备验证没有折算为本次通过。
- 四组断言：GBK 2 字节（1 切点）、GB18030 4 字节（3 切点）、UTF-8 3 字节（2 切点）、UTF-8 4 字节（3 切点）；每个切点分别覆盖空后缀和中文后缀。JVM 还比较可随机访问文件及已知/未知大小顺序流结果，共 54 个跨页场景。
- 未测试：Android 仪器测试未执行；真实查看器下一段/上一段、行号、SAF 提供方、设备 UI、真机、低 API 均未验证。新增 Android 源码存在或编译不等于设备执行。

### 未完成与暂停项

- 未完成：PR 当前 head 的独立 Codex High 审查、独立 Android QA；CI 需按本次实际推送的 SHA 在 provider 核对。本会话交付修复 PR，不将这些待验项算作已完成。
- 外部阻塞：无实现阻塞；设备与审查需要编排方按独立职责安排。
- 用户暂停：API Key 保存、真实账号与真机验收；本次明确不合并、不发布。

### 下一步

1. 在 provider 核对本会话分支 PR 的最终完整 head 与 CI；有本次引入的失败时继续修复并重新验证。
2. 独立 QA 在 PR 最终完整 SHA 上构建并运行 `com.tanuki.TextFilePagingBoundaryTest`，随后按 QA-12 的合成 GBK `.cpp` 复测真实查看器下一段/上一段、第二页中文与行号，并核对两页逐字节还原。
3. 编排方安排当前 head 的独立 Codex High 审查；CI 或 review 发现本次问题时继续修复，不自行合并。

### 约束与风险

- 只读写合成临时文件，不读取真实用户文件，不操作设备，不安装、不清数据、不恢复快照。
- 根因证据：设备报告的 `input.position()` 相关游标已越过不完整字符，而宿主 JVM 留住残字节；不能推断所有平台解码器的内部缓存/替代路径相同。因此严格试探使用完整 decode + flush 入口，不能只给 `endOfInput=true` 而忽略收尾。
- 不完整尾字节最多 3 字节，最多 4 次有界严格解码，内存仍受 64 KiB 页窗口约束。页内本就非法的文本仍走原有替代显示；无损拼接保证以选定编码下合法文本为前提。
- 旧 JVM 用例在旧代码上也能通过，不能宣称 Host 复现了 Android 缺陷；本次增加 Android 专项测试供独立 QA 验证平台实际实现。

### 产物与校验

- 构建产物只作为本地 debug 编译验证，不作为正式签名、发布或已安装证据；不生成正式发布附件。
- 源码候选检查已通过：将公开清单复制到系统临时目录后执行 `python3 scripts/package-source.py`，核对 `artifacts/SOURCE-SHA256SUMS` 与实际归档 SHA-256，再运行 `python3 scripts/check-publication.py --archive <临时源码归档>`，794 文件、0 findings。最终文档写定后重新生成并检查，不沿用旧 ZIP；候选只作检查，不作为用户发布包。
- 提交前交接清理：复核了前一编码记录，仍含 Big5/Shift_JIS/UTF-16、SAF 与设备未完成项，且是共享状态引用的历史依据，因此保留；本次无已确认过期且属于本范围的记录可删除，不做跨 PR 清理。
- 普通 PR 不修改 `AGENT-STATE.md` / `CURRENT.json`；共享状态与印章由集成维护方维护。

<a id="20261111-1635-update-readonly-followup"></a>

## 20261111-1635-update-readonly-followup

- Agent/会话标识：android_agent-73
- 时间：2026-11-11 CST

### 当前状态

- fetch 实核基线为 `origin/main=9f5a2fe3c1b16e019144d1ff26f94c5babf12bcf`，已含 #82。新分支从该 SHA 创建，开始时工作区干净。
- main 已有保留原 `FailureRetry` 版本与重装语义的生产路由；本轮在该实现上添加版本选择变化后的编号失效，不回退原路由。
- 目标仍为独立 workspacecheck；设备由独立 QA 使用，本会话不操作设备或 adb。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt`、`FileOperationOverlay.kt` | 外壳统一导航/系统栏避让；文件页唯一任务宿主共用运行时与文件抽屉，保留 main 的存储/远程导航和错误恢复。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt`、`WorkspaceFileViewer.kt`、`FileViewerLayout.kt`、`FileAccessPresentation.kt` | 读取实测操作栏高度，保留横屏紧凑条/阅读恢复；安装期写入提示覆盖顶部导入、空态/菜单、编辑和手机传输，不重做空态或导入行。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceIoGate.kt`、`WorkspaceFileManager.kt`、`app/src/main/kotlin/com/tanuki/MainActivity.kt` | 只读与冲突查询不等环境事务；写入前后复核，保留手机授权/删源/容量保护；门禁使用实际操作快照而非包含自有文件锁/常驻服务的宽泛 busy 标志。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeVersionSelection.kt`、`RuntimeComponents.kt`、`OperationRetry.kt`、`RuntimeManager.kt`、`ui/OperationProgressPresentation.kt` | 保存新目标前失效旧编号与回调，保留失败阶段/原因/耗时并返回组件入口；main 原版本/重装路由保留。 |
| `app/src/main/kotlin/com/tanuki/ui/ApplicationUpdateCard.kt`、`ApplicationUpdatePresentation.kt` | 简短正文、折叠工程详情、主按钮、可信版本状态点；原错误恢复、清理与设置入口保留。 |
| `app/src/test/kotlin/com/tanuki/` 相关7文件 | 版本选择+生产重试路由、锁边界与持锁不自拒绝、稳态服务导入、布局/入口门禁及更新状态回归。 |
| `app/src/androidTest/kotlin/com/tanuki/SetupPresentationTest.kt` | 迁入唯一宿主真实坐标点击/取消及深色大字体设置末项边界断言；仅编译，不运行设备。 |
| `docs/APP-UPDATES.md`、`COMPONENTS.md`、`WORKSPACE-STORAGE.md`、本记录 | 最小更新专题行为与当前验证，不修改共享状态或整份旧测试报告。 |

### 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git fetch origin main`、新建 `ao/update-page/v2` | 成功，基线 `9f5a2fe3c1b16e019144d1ff26f94c5babf12bcf` | Git 元数据工作区外，首次被沙箱拒绝，原命令一次性权限重试成功。 |
| 首轮同组命令，使用 `--offline` | 失败，49秒，尚未进入 Kotlin 编译/测试 | 最新 main 新增 QR 依赖未在本 worktree 离线缓存；改为在线正常解析，未修改全局缓存/配置。 |
| 去掉 `--offline` 的同组 compile + 13类定向 JVM | 成功，5分57秒 | 在线取得新依赖；后续补齐顶部导入/手机传输门禁接线后，再执行下一条最终确认，不把此中间状态当最终证明。 |
| 下述最终命令（`--offline`） | **BUILD SUCCESSFUL，3分1秒；13类106项，0 failures/errors/skipped** | 产品、仪器、JVM源码均编译；只执行选定JVM类，无 assemble、全量JVM、lint 或设备执行。 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1635-update-readonly-followup.md` | 通过 | 共享状态允许落后，未写印章。 |
| `python3 scripts/check-publication.py --git` | 880个公开文件，0 findings | 索引和工作树独立扫描；提交前对实际暂存再检查。 |
| `python3 scripts/package-source.py`、`python3 scripts/check-publication.py --archive artifacts/sources/Tanuki-0.1.7-source.zip` | 880文件源码候选生成、归档扫描0 findings | 收尾文档后再生成并核对外部checksum；native_sources=absent，本轮不分发原生二进制。 |

最终实际命令（项目 JDK17、SDK36，worktree 独立可写缓存）：

```bash
source scripts/ao-worktree-env.sh
./gradlew --offline --no-daemon --max-workers=2 -PtestBuildType=debug \
  -I scripts/workspace-verification.init.gradle -x buildNativeRuntime \
  :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin \
  :app:testModernDebugUnitTest \
  --tests com.tanuki.ApplicationUpdatePresentationTest \
  --tests com.tanuki.RuntimeVersionSelectionTest \
  --tests com.tanuki.WorkspaceIoGateTest \
  --tests com.tanuki.FileViewerLayoutTest \
  --tests com.tanuki.FileAccessPresentationTest \
  --tests com.tanuki.OperationProgressPresentationTest \
  --tests com.tanuki.OperationProgressLayoutTest \
  --tests com.tanuki.OperationRetryTest \
  --tests com.tanuki.FailureRecoveryFollowupTest \
  --tests com.tanuki.ImportNamingTest \
  --tests com.tanuki.TransferEngineTest \
  --tests com.tanuki.FileOperationOutcomeTest \
  --tests com.tanuki.FileTransferProgressTest --console=plain
```

实际 XML 分项：更新状态4、版本选择2、IO门禁4、查看器几何/宿主11、文件权限6、进度展示13、布局/入口契约5、原路由重试8、main错误恢复11、导入命名8、传输执行20、操作结果9、文件进度5。

main 的 `hasActiveOperation` 还包含 `mutation.isLocked` 与常驻 `serverJob`，不能直接作为持锁写入的 idle 检查；本轮生产策略 `environmentBlocksFileWrites` 只消费实际快照/更新任务，测试涵盖锁内不自拒绝、常驻服务不等于安装、安装/更新立即拒绝。保留原停止 DSH 的写入约束。

以上是本轮新验证。旧 `9aafbbd7` 的1197项/旧APK/lint结果不作为新 main 验证。未执行 Android 仪器测试，不把坐标点击断言源码的编译当作设备通过。SDK 工具版本/analytics 写入提示和既有 Kotlin 弃用告警未使编译失败，未为消除告警扩大修改范围。

### 未完成与暂停项

- 五块增量与本轮 compile/106项定向回归完成；按本记录范围完成上传检查后，统一一次 commit/push 并创建指定标题 PR，动态 PR/head 以 provider 交付为准。
- 当前 SHA 独立复审与设备 QA 待编排安排；API Key 保存、真实模型/账号及真机验收继续暂停。

### 下一步

1. 编排在新 PR 当前完整 head 上触发独立 Review，并检查 GitHub 全量 CI；本 worker 不自发触发复审，不合并、不发布。
2. 独立设备 QA 在确认没有安装/真实任务后，执行更新后的 `SetupPresentationTest`，检查运行时+文件同时进行、失败保留、物理点击取消、查看器操作栏不被遮挡、深色200%字体设置末项完整可见。
3. 另验安装期间首次进文件页能列目录/预览；顶部/空态/菜单导入、编辑和手机传输给等待提示；空闲写入仍可执行、DSH稳态仍能导入新项目；失败A→改选B后旧编号不能安装A/B，需显式发起新任务。

### 约束与风险

- 保留 #73/#75/#80/#82 的接口与行为，不恢复旧分支整份文件页、主页面或测试报告。
- 写入仍受路径、授权、运行状态和事务互斥保护；只读浏览不因环境安装等待事务锁。
- 不修改共享状态/印章；第一方新增注释中文；不合并、不发布。

### 产物与校验

- 本轮不生成新 app/test APK，不安装、不分发旧输出；只生成 `artifacts/sources/Tanuki-0.1.7-source.zip` 源码检查候选及 `artifacts/SOURCE-SHA256SUMS`，名称沿用日常检查默认值，不代表确认了发布版本。文档收尾后重打归档并复核外部 checksum，不把源码 ZIP 自身哈希写回归档。
- author/committer 使用 `leezzou <115924933+leezzou@users.noreply.github.com>`。
- 已复核本轮相关交接：main 中仅 `20261111-1520-pr77-reopen.md`，没有旧分支的1254记录可清理；1520仍保留生产重试路由RED→GREEN等独有验证依据及未完成设备边界，不判定过期。保留当前指针使用的发布记录与跨 PR 材料，不按日期删文档。本轮无确认过期且属本轮的记录；跨 PR 清理由集成维护方负责。

- 后续文档维护：本功能已由 PR #88 合入 `b324d54dce2727e8e304fa939fb84c143add6b20`；过期记录迁移/引用修复及长期清理规则见 [文档收尾记录](DEVELOPMENT-HISTORY.md#20261111-1802-handoff-cleanup)。本记录保留原产品验证事实，不将后补文档检查写成产品重测。

<a id="20261111-1643-release-0.1.13"></a>

## 20261111-1643-release-0.1.13

- Agent/会话标识：android_agent-81（AO Worker）
- 时间：2026-11-11 16:43 CST 开始

### 当前状态

基线 fetch 后 origin/main=9f5a2fe3c1b16e019144d1ff26f94c5babf12bcf，工作区初始干净；分支 ao/release/0.1.13。目标 com.tanuki.modern.workspacecheck、0.1.13/code18、ARM64、minSdk26，开发证书前缀 371dc301。已更新版本与文档；release 构建、APK 实核、六附件校验通过，不宣称设备通过。

### 本次改动

- app/build.gradle.kts：默认版本 0.1.13/code18，不改产品逻辑。
- README.md、docs/AGENT-STATE.md、docs/RELEASE.md、docs/ARTIFACTS.md、docs/APP-UPDATES.md、docs/TEST-REPORT.md：当前版本、基线包含内容与未验证项。
- 本记录及 docs/handoffs/CURRENT.json：发布交接与严格模式印章，由 check-handoff --integrate 生成。
- 包含基线已合并的聊天分页/停靠条、首次引导、设计令牌接入、文件导入/复制移动、统一失败出口、局域网发现/远程页、横屏阅读/复制反馈、会话同步/阅读状态、完整字符分页、全局安装进度与空态（#62/#67/#68/#70/#71/#73/#74/#75/#76/#79/#80/#82/#83）；#84 仅动效方案文档。

### 验证记录

- 已执行 git fetch origin main，确认完整基线 SHA；git switch -c ao/release/0.1.13 origin/main 后核对分支、HEAD、状态。
- `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1643-release-0.1.13.md`、`--integrate --record`、`--release`：通过。`python3 scripts/check-publication.py --git`：0 findings。
- `source scripts/ao-worktree-env.sh` 后执行 `python3 scripts/prepare-app-release.py --version 0.1.13 --version-code 18 --channel workspacecheck`：退出 0；BUILD SUCCESSFUL（8m50s），54 tasks executed，包含原生运行时构建与 release 自带 lintVital；不等同完整 debug lint。
- apkanalyzer/apksigner 实核包名 `com.tanuki.modern.workspacecheck`、0.1.13/code18、minSdk26、non-debuggable；4 个 ARM64 原生库含 PRoot/loader/supervisor；证书 SHA-256 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。
- APK 大小 **12,950,514 字节**；SHA-256 **`888859f1c2afd7f440b6b4f08a2e169d59df188ce29a48466ad1bfa981b3c7a3`**，副本哈希复核一致。
- 发布目录实际六附件齐全；`shasum -a 256 -c RELEASE-SHA256SUMS` 与 `shasum -a 256 -c native-source-SHA256SUMS` 通过。文档收尾后刷新印章与源码 ZIP，再核对外部清单。
- 本轮未执行 JVM/lint、Android 仪器、设备安装/升级、模拟器/真机 UI、真实 PRoot/DSH/sshd/中继、弱网与真实模型/账号；历史记录不作本轮通过结论。

### 未完成与暂停项

- 本轮交付步骤：完成文档收尾、源码附件刷新后统一一次提交推送并创建 PR；实际 PR 号与提交 SHA 通过最终回执及 PR 页面记录，不为回填再提交。
- 后续独立审查、CI、QA 与用户合并/发布待处理；本轮按用户要求不等待 QA 出包。
- API Key 保存、真实模型/账号、真机验收继续暂停。

### 下一步

1. 候选已核验，保持 APK 字节；如产品源码或 APK 改变，重新构建并验证。
2. 完成源码与校验，统一一次提交推送并开 PR；独立 Review/QA 待安排。
3. 等用户后续明确授权；不合并、不创建 tag/Release、不上传。

### 约束与风险

不连接或修改设备，不覆盖安装，不读取凭据或用户数据；不改产品逻辑。workspacecheck 为开发证书 release，不能宣传正式签名或已通过设备验收。Git fetch 与建分支首次因 linked worktree 元数据在工作区外被沙箱拦截，获准相同命令后成功。缓存复制去掉的上游 CLAUDE.md -> AGENTS.md 链接在核对固定提交后仅恢复到被忽略缓存，原生源码打包通过；未改产品脚本。上游架构能力探测有编译诊断但最终原生任务成功；metrics 因全局目录不可写产生非致命提示，未为此扩大权限。

### 产物与校验

本版六附件目标 artifacts/releases/0.1.13/：Tanuki-0.1.13-workspacecheck-arm64-release.apk、app-update.json、Tanuki-0.1.13-source.zip、Tanuki-native-corresponding-sources.tar.gz、native-source-SHA256SUMS、RELEASE-SHA256SUMS。APK 实际大小 12,950,514 字节、SHA-256 `888859f1c2afd7f440b6b4f08a2e169d59df188ce29a48466ad1bfa981b3c7a3`；六附件与校验通过，不回写旧版附件。源码变化后重包 ZIP 并刷新校验，避免使用旧源码。

过期记录检查：本轮未确认可删的独有验证记录；保留 0.1.12 历史交接、QA 证据与未完成事项，不扩大到跨任务清理。本任务明确授权刷新 AGENT-STATE，按发布维护流程生成 CURRENT.json；合并后须在最终 main 再核对印章。

<a id="20261111-1703-release-order"></a>

## 20261111-1703-release-order

- Agent/会话标识：android_agent-81
- 时间：2026-11-11 17:03 CST

### 当前状态

基线 fetch 后 origin/main=9d10912b8df7f0b509dc7ce1d6d1df261161d5ca，初始工作区干净；独立分支 ao/release-0-1/release-order。docs/RELEASE.md 开头新增规则，并修正本文件直接冲突的旧顺序。无产品代码、脚本或设备改动。

### 本次改动

- docs/RELEASE.md：新增「发布顺序」四步、明确禁止项；调整原固定收尾顺序、最终封装前置及网页发布第一步，保持本文件一致；实际发布范围遵守当次明确授权。
- docs/handoffs/20261111-1703-release-order.md：本次独立交接。不改 AGENT-STATE.md 或 CURRENT.json。

### 验证记录

本轮验证命令：`python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1703-release-order.md`、`python3 scripts/check-publication.py --git`、`git diff --check`。以命令实际结果与 PR 回执为准。文档人工核对四步顺序和禁止项；未运行构建、JVM/lint、Android 仪器或设备 QA，历史构建不作本次验证结果。

### 未完成与暂停项

提交推送并创建独立文档 PR，之后等待独立审查/适用 CI 与用户合并。API Key 保存、真实模型/账号、真机验收暂停项不变。本任务未授权实际合并、tag、Release 或附件上传。

### 下一步

1. 文档检查通过后提交推送本分支并创建 PR，回报 PR 号。
2. 合并由明确授权的维护方处理；本文件规则在后续发布任务中按授权范围应用。

### 约束与风险

仅更新发布说明及必要交接，保持改动范围；不执行产品构建或操作设备。此次文档更改不自动授权发布任何版本。其他专题或脚本的全面一致性维护不在本次范围。

### 产物与校验

交付物为 Git 文档 PR，无新增 APK 或发布附件；不改写 0.1.13 已生成的六附件。提交前检查过期记录：旧发布交接仍有独有校验与未验证事实，保留；不在本次小 PR 清理其他任务记录。源码包仅按现有脚本生成检查副本，不将其作为刷新后的发布附件或上传。

<a id="20261111-1730-motion-batch-a"></a>

## 20261111-1730-motion-batch-a

- Agent/会话标识：android_agent-82
- 时间：2026-11-11 CST

### 当前状态

fetch 的最新 main 为 `3c63c9b277b6affc82b25a62ccb7cc483b948d38`，分支 `ao/motion-foundation/batch-a`。该分支先前存在，核对会话83已终止、0提交/PR且无独有提交后，在本worktree快进至最新main；未改83工作区。QA-06/07 为独立 PR #87，不混入本PR。共享Git默认不可写，经命令审批后fetch/switch/commit可写。

#### PR #89 基线同步

编排的等待 #87 合并消息在 #89 创建后才送达。核对 #87 已合并（main=`7cfe1446effa76d1e7901f839d6a22996778d013`），在工作区干净且本地HEAD与provider的`15ccf72e40ba64fb43f44b0534c6135fe0ba0492`一致后，先claim #89，再fetch最新main并无冲突合入（未提交）。不改写已推送历史、不代合PR、不触碰83/84工作区或废弃root。

集成后的产品/Android测试源码编译与动效+阅读状态九类定向回归通过：148项，0失败/错误/跳过，BUILD SUCCESSFUL（5m21s）。统一收尾后只新增一次同步提交/推送；最终head由provider与AO完成回执记录。

### 本次改动

- `ui/design/TanukiTokenSpec.kt`、`TanukiMotionSpecs.kt`：instant/layout及S-toggle/press/icon/state/expand/list/count/copy/progress工厂；明确enter/standard/exit/linear与settle(1,500)，reduced下即时/列表null/按压1。
- `ui/design/TanukiMotionSettings.kt`、`TanukiDesignTheme.kt`：进程共享StateFlow、系统设置ContentObserver、Activity恢复补读；Compose订阅并向主题提供。
- `ui/design/TanukiComponents.kt`、`ui/TanukiMotion.kt`：公共按钮/卡片100ms按下、140ms释放保留ripple；状态chip用toggle，呼吸linear；reduced禁用缩放并保持循环静态。
- `ui/TanukiMotionFeedback.kt`：固定槽位图标/状态、最小宽度计数、复制1500ms可重置及无障碍建议超时。计数首次直接显示；状态宿主在reduced切换时保持身份，避免丢失列表滚动。
- 五页接入：原生聊天（`DshComposerBar.kt`图标、`DshToolCard.kt`复制）、会话管理（`DshSessionManagerPage.kt`固定状态区/移除列表二次入场）、文件（`WorkspaceFilesPage.kt`项数）、浏览器（`BrowserControlButton.kt`控制图标）、远程设置（`RemoteHostSettings.kt`连接数量）。业务回调与权限即时生效。
- `browser/BrowserOverlayWindow.kt`：与Compose共用设置来源，原生按钮按压保留ripple/点击取消，显示时补读；reduced立即取消缩放并归位，关闭时取消动画。
- `TanukiMotionSpecsTest.kt`：5项工厂/曲线/降级/共享观察回归；`docs/DESIGN-TOKENS.md`：A批接口与五页表。

不修改导航、系统sheet、聊天历史滚动和展开高度的既有所有权；expand/list/progress工厂可供后续批次迁移，不宣称整批审计已完成。

### 验证记录

- fetch与分支切换/快进：通过，工作区起始干净。
- `source scripts/ao-worktree-env.sh` 后执行 `./gradlew --console=plain -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest --tests com.tanuki.ui.design.TanukiMotionSpecsTest --tests com.tanuki.ui.design.TanukiDesignTokensTest --tests com.tanuki.TanukiPageVisualsTest --tests com.tanuki.DshComposerControllerTest --tests com.tanuki.DshSessionManagerControllerTest`：最终退出0，BUILD SUCCESSFUL（4m22s），39 tasks（6 executed/33 up-to-date）。产品与Android测试源码编译通过；五类JVM共102项（5/19/12/29/37），0 failures/errors/skipped。
- 首轮同组验证3m10s通过；随后修正状态宿主身份、首次计数及浮窗补读，再跑上述最终验证，未沿用旧结果。
- `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1730-motion-batch-a.md`、`python3 scripts/check-publication.py --git`、`git diff --check`：通过，公开检查879 public files、0 findings。
- 同步至main=`7cfe1446`后，运行上述Gradle命令并追加 `--tests com.tanuki.TanukiSaveableStateTest --tests com.tanuki.DshSessionSyncTest --tests com.tanuki.TextPageLayoutTest --tests com.tanuki.TextFileReaderTest`：两项Kotlin编译通过；九类148项（102+4+5+10+27），0 failures/errors/skipped；退出0，5m21s。相对新main仍仅A批17文件，未重复包含#87功能差异。
- Android设备、仪器、性能帧率、完整JVM、lint未执行。

### 未完成与暂停项

- PR [#89](https://github.com/leezzou/tanuki/pull/89) 已交付；对齐main后的148项复验已完成，待统一同步提交/推送。最终head由provider及AO完成回执记录。
- 独立审查、CI与设备QA待安排，未触发AO Review；系统设置实时通知/回前台、复制重复点击与无障碍超时、浮窗触摸和五页视觉/帧率未在Android执行。
- API Key保存、真实模型/账号、真机验收继续暂停。

### 下一步

1. 已补统一令牌/工厂/共享设置及五页接入；同步main后最新148项Host通过，原102项保留为同步前历史，不替代设备验收。
2. 统一提交推送PR，不合并；后续审查和QA绑定provider当前head。
3. 独立QA验证系统设置实时变化/回前台、五类反馈及TalkBack。

### 约束与风险

不连接设备、不改系统设置、权限和业务动作即时生效，不包装活WebView、不扩大到B/C批高度和导航改造。无动画仍保留复制成功文字及真实阶段反馈。

### 产物与校验

仅源码PR，无发布附件、不安装、不合并、不发布。普通PR不更新共享状态/印章。既有审计与QA记录仍有独有验证/未完成项，保留，不做跨任务清理。

<a id="20261111-1750-qa06-qa07-pagination"></a>

## 20261111-1750-qa06-qa07-pagination

- Agent/会话标识：android_agent-82
- 时间：2026-11-11（CST）

### 当前状态

fetch 后实际 origin/main 为 `9d10912b8df7f0b509dc7ce1d6d1df261161d5ca`，比任务指定 `9f5a2fe3` 多发布 PR #85。当前分支 `ao/settings-pagination/fix`，初始工作区干净。任务引用的 `20261111-1634-qa-reverify.md` 不在远端 main，依据用户明确复现步骤及 [既有设备 QA](handoffs/20261111-1216-device-qa.md) QA-06/07 修复。

### 本次改动

- `TanukiUi.kt`：设置说明改为“原生界面：可查看会话、发送消息、处理审批”。
- `ReaderState.kt`、`TanukiSaveableState.kt`：偏好和 Bundle 保存分页索引、字节边界、行号起点、自动识别编码；兼容旧状态，损坏游标回退首段。
- `WorkspaceFileViewer.kt`：从游标恢复正文和行号，分页状态立即回传，切换编码重置游标。
- `TanukiSaveableStateTest.kt`、`ReaderConfigChangeTest.kt`：第 N 段游标往返、旧状态兼容与坏值回退；设备用例翻到第 3 段重建后比较正文和游标，并验证可回上一段。

### 验证记录

- `git fetch origin main`：通过，确认上述基线。
- 全仓搜索“只读”：当前设置误导文案已修复；其余匹配主要是历史切片交接、权限、安全模式、文件预览和协议读取语义，未扩大修改范围。
- `source scripts/ao-worktree-env.sh` 后执行 `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest --tests com.tanuki.TanukiSaveableStateTest --tests com.tanuki.DshSessionSyncTest --tests com.tanuki.TextPageLayoutTest --tests com.tanuki.TextFileReaderTest`：退出 0，BUILD SUCCESSFUL（5m17s），39 tasks executed。四类共 46 tests，0 failures / errors / skipped（4 + 5 + 10 + 27）。原生构建也通过；编译输出既有告警，未把架构探测诊断视为构建失败。
- `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1750-qa06-qa07-pagination.md`、`python3 scripts/check-publication.py --git`、`git diff --check`：通过；公开检查 0 findings。初次交接文件名缺时间被校验拒绝，已按格式重命名后通过。
- 工作期间 main 新增 `3c63c9b`（#86），仅发布文档，与本次文件无交集；本 PR 起点保留开始 fetch 的 SHA。
- Android 仪器、独立设备 QA、完整 JVM、lint 未执行，不以 Host 结果替代设备复验。

### 未完成与暂停项

- 编译和定向测试已完成，待提交推送及 PR 交付；最终 PR 号和 head 由 provider 页面及 AO 完成回执记录。
- 独立审查、CI 和设备复验待后续安排；未触发 AO Review。
- API Key 保存、真实模型/账号和真机继续暂停。

### 下一步

1. 本地编译和定向 JVM 已通过，保留本次验证范围。
2. 提交推送及创建 PR，不合并；当前 head 的审查和 CI 由后续流程核对。
3. 独立 QA 执行 `ReaderConfigChangeTest` 并切换系统深色/字体复验第 N 段恢复。

### 约束与风险

不连接或修改设备、不安装、不读取用户内容或凭据。状态只保存定位信息，不保存正文。Git fetch 和分支创建首次因共享元数据权限被拦截，获准相同命令后成功；一次只读辅助查询不存在的 ref 返回 128，已确认不影响 main 和工作区。

### 产物与校验

仅源码 PR，不生成发布 APK、源码 ZIP 或正式附件，不修改共享状态与印章。过期记录检查：本次依赖的旧 QA 与修复交接仍含独有验证和未完成设备项，保留；未确认本范围有可删记录，不做跨任务清理。

<a id="20261111-1802-handoff-cleanup"></a>

## 20261111-1802-handoff-cleanup

- Agent/会话标识：android_agent-73
- 时间：2026-11-11 CST

### 当前状态

- 原功能PR #88在文档追加处理期间已合并，merge=`b324d54dce2727e8e304fa939fb84c143add6b20`，未向已合并分支追加提交。
- 原准备内容已保存，fetch实核最新 `origin/main=b324d54dce2727e8e304fa939fb84c143add6b20`，从它创建 `ao/handoff-cleanup/v1`，单独交付文档PR。
- 迁移补丁只在 `TEST-REPORT.md` 顶部有冲突：保留main的0.1.13候选报告和本轮迁入的历史验证两个章节，未覆盖任一方。
- #87和QA-06/07记录不属本会话交付；据实际内容判断保留，不冒领验证或按消息中的归属误删。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `docs/HANDOFFS.md` | 明确每次提交前清理流程、发布≤10、保留边界和历史事实的专题检索位置。 |
| `docs/TEST-REPORT.md` | 承接PR #60/#82历史基线、1047/101/24/31项结果、版本路由RED→GREEN和失败原因；保留各自范围，不作为当前已测。 |
| `docs/WORKSPACE-STORAGE.md`、`COMPONENTS.md` | 承接PDF/文本/图片/全屏视频按钮点击、任务取消/失败重试、空态导入/完成打开、复制移动、窄屏/深色/大字体/TalkBack等未完成设备QA。 |
| `docs/handoffs/20261111-0538-viewer-transfer-capsule-inset.md`（删除） | PR #60固定48dp方案已由实测高度/统一避让替代；有用事实迁入专题与测试报告后删除。 |
| `docs/handoffs/20261111-1520-pr77-reopen.md`（删除） | PR #82已合并且阶段任务完成；历史验证与待验事项已迁移，删除旧阶段交接。 |
| 0314、1007、1017、1050四条旧交接的前一记录链接；1635功能交接 | 仅修链接/补后续维护指引，不重写历史实现和测试事实。 |
| 本记录 | 独立文档PR的实际基线、清理范围、验证与边界。 |

### 验证记录

| 实际命令/操作 | 结果 | 证据与限制 |
|---|---|---|
| 查询PR #88 provider状态 | MERGED，merge=`b324d54dce2727e8e304fa939fb84c143add6b20` | 先前“必须OPEN”的预检退出1，未打包/提交；查明后改走独立文档分支。 |
| 保存文档改动、`git fetch origin main`、创建 `ao/handoff-cleanup/v1`、`git apply --3way` | fetch/分支成功；应用补丁因测试报告顶部并发新增章节返回1 | 保留两方内容后消除冲突；Git元数据沙箱拒绝按原命令一次性权限重试。 |
| `git diff HEAD --name-only` 与未跟踪候选核对 | 12个路径全部为docs Markdown | 含两条删除和本次新记录；无产品/测试源码、共享状态或印章修改。 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1802-handoff-cleanup.md` | 通过 | 首次因“未运行”不匹配检查器固定未验词退出1，改为准确的“未执行”后通过；不是产品失败。 |
| `python3 scripts/check-publication.py` | 882个公开文件，0 findings | 最新main工作树的新验证；提交前另对已解决冲突的实际索引运行`--git`。 |
| `python3 scripts/package-source.py`、`python3 scripts/check-publication.py --archive artifacts/sources/Tanuki-0.1.7-source.zip`、`git diff --check` | 882文件归档生成/扫描与差异检查通过，0 findings | 文档收尾后再次生成并核对外部checksum；native_sources=absent，本轮无原生二进制分发。 |

本轮未执行产品构建、JVM/lint或设备测试。原PR #88的106项结果属于原产品提交，不将其作为本轮文档验证或独立审查。前一旧分支曾有11个文档路径、878文件扫描0发现，不沿用为最新main检查；本表882文件结果是本次新执行。

### 未完成与暂停项

- 本轮文档/工作树与归档检查完成；按范围暂存复核后统一提交并交付独立文档PR，当前完整head的review/CI由编排安排。
- 原始设备QA仍待独立设备资源；API Key保存、真实模型/账号及真机验收继续暂停。
- 整个目录的跨主题精简与发版≤10收口由集成/发布维护方执行，本次不为凑数删除未闭环事项。

### 下一步

1. 对最新main上的文档候选运行普通PR检查、公开扫描、源码打包和外部校验，按指定身份统一提交/推送独立文档PR。
2. 回报新PR与完整head，编排重新绑定审查/CI；不沿用原354f98e的批准，不自行触发review，不合并/发布。
3. 后续集成/发布前，将其他仍有用事实和未完成项迁专题/当前有效交接，再清理过期记录并执行数量收口。

### 约束与风险

- 保留本轮最新记录、`TEMPLATE.md`、`CURRENT.json`、共享状态引用的发布记录，以及仍被使用的证据；不修改共享状态/印章。
- 保留 `20261111-1216-device-qa.md`：包含12项问题和独有截图/复现。保留 `20261111-1442-qa10-qa07.md`：除QA-07外还含QA-10实现/1195项Host依据和未完成设备边界，不能仅因#87替代了部分阅读逻辑就删除。
- 删除前已验证两个目标为本worktree内预期绝对路径、普通文件非符号链接；先迁事实与待验步骤再删，修复全部Markdown引用，不依赖Git历史或私有缓存接手。
- `git apply --3way` 已将两条删除放入索引，后续再次把已不存在于索引的旧路径传给 `git add` 会报pathspec；保留已暂存删除，仅显式暂存其余10个现存文档，并断言最终12路径/两条删除，避免误判丢失或重复恢复旧记录。

### 产物与校验

- 仅生成日常源码检查候选 `artifacts/sources/Tanuki-0.1.7-source.zip` 与外部 `SOURCE-SHA256SUMS`；名称不代表发布版本，不生成或分发APK/发布附件。
- 文档收尾后重打源码包、扫描归档并复核外部哈希，不把源码ZIP自身哈希写回归档。
- author/committer均使用 `leezzou <115924933+leezzou@users.noreply.github.com>`。
- 实际清理2条旧记录，事实保留位置和保留理由见本次改动/约束；四条历史记录只修前一链接，产品交接保留原验证，新增本次独立文档交接以避免混写已合并PR。

<a id="20261111-1819-sky-theme-landing"></a>

## 20261111-1819-sky-theme-landing

- Agent/会话标识：android_agent-73
- 时间：2026-11-11 CST

### 当前状态

- fetch实核最新 `origin/main=4710c5e08ffa8c86d8cdc1bffbec1a1052be0935`（含#87/#88/#89）；新分支从该SHA创建，开始时工作区干净。
- 原补丁基线 `7cfe1446effa76d1e7901f839d6a22996778d013`；48文件、7新增，SHA256已核对为 `e91e1787b06113dc597748e5a541ed6bef47c5948f62abe4124bee3450e8b821`。最新main上 `git apply --check` 通过，未忽略上下文冲突强套。
- PR #90仍为独立文档交付，不复制其清理提交；只按本配色主题判断过期记录。

### 本次改动

| 文件/范围 | 改动与目的 |
|---|---|
| `MainActivity.kt` 与25个UI页面/控件 | 系统栏同canvas；按钮别名接统一颜色入口；导航/进度/链接/阅读器外壳读取用途令牌，保持回调、布局和main动效接线。 |
| `ui/design/` 6个文件 | 角色色、文字/装饰/实底分工、灰边框无阴影卡片、统一Material按钮映射；既有motion.press继续保留。 |
| 9个XML资源 | 图标仅改fill/stroke颜色；浅深首帧/系统栏背景同主题，API27属性放v27专用资源，API26基础资源不越界。 |
| 4个JVM测试文件 | 设计令牌、页面映射、明暗Markdown链接和平台资源回归；现有Material对比度类一并执行。 |
| `docs/DESIGN-TOKENS.md`、`TEST-REPORT.md` | 配色表与landing逐行一致；保留#89动效章节，明确原87旧基线验证与本轮落库新验证的归属。 |
| 本记录 | 记录最新主线、补丁身份、重新验证和设备/评审边界。 |

### 验证记录

| 实际命令/操作 | 结果 | 证据与限制 |
|---|---|---|
| `git fetch origin main`、新建主题分支 | 成功，基线4710c5e完整SHA如上 | 首次因工作区外Git元数据被拒，原命令一次性权限重试成功。 |
| 原patch SHA256与 `git apply --check` | SHA匹配；最新main兼容检查通过 | 单独此检查不等于编译/测试通过；实际执行结果另列于下文。 |
| 精确文件集合、配色表逐行比对、motion保留断言 | 48文件/7新增，表格与landing相同，main动效保留 | 无冲突、无手工产品代码适配；未带入原1731阶段记录。 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1819-sky-theme-landing.md`、`git diff --check` | 通过 | 普通PR不改共享状态或印章。 |
| `python3 scripts/check-publication.py --git`、`python3 scripts/package-source.py`、`python3 scripts/check-publication.py --archive artifacts/sources/Tanuki-0.1.7-source.zip` | 895个公开文件，工作树和归档均0 findings | 初查索引888条，提交前明确暂存7新增并重查实际索引/重打归档，核对外部SOURCE-SHA256SUMS；不回填ZIP自身哈希。 |

实际执行：`source scripts/ao-worktree-env.sh` 后 `./gradlew --console=plain --max-workers=2 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest --tests com.tanuki.ui.design.TanukiDesignTokensTest --tests com.tanuki.ui.design.TanukiPlatformThemeTest --tests com.tanuki.TanukiContrastTest --tests com.tanuki.TanukiPageVisualsTest --tests com.tanuki.DshMarkdownTest -x buildNativeRuntime --offline`，BUILD SUCCESSFUL（1m43s）。主源码/Android测试源码/JVM测试源码编译通过；XML实核58项/5结果文件/0 failures/errors/skipped（23/3/4/12/16）。其中生产用途审计在两套主题各执行45项，共90项通过；装饰天蓝配白字不合格的负向断言和说明色按底色回退断言仍执行。

原生运行时任务跳过，不产出设备或发布APK。本轮未执行lint、完整JVM、设备、实际逐页渲染或Android仪器测试，不把源码编译当设备通过；旧87会话的lint 0错误/103警告/1提示只保留为7cfe基线历史。编译只有既有弃用/SDK XML版本/工作区外analytics提示，没有产品编译或测试失败。

### 未完成与暂停项

- 补丁落库、配色文档合并、本分支compile/58项、90项用途对比度及工作树/归档检查已完成；提交前按48条明确路径暂存复核，再统一提交推送。独立review/CI及设备QA不属于本轮已通过范围。
- 独立review、CI、明暗逐页/系统栏/大字体/TalkBack/低API设备QA由编排安排；设备5554由android_agent-85独占研究。

### 下一步

1. 精确应用补丁，确认main动效结构保留，补齐配色取值与应用/调整清单。
2. 只执行适用compile和选定JVM/对比度回归，补本记录与公开/源码归档检查后统一提交推送。
3. 回报PR、完整head与冲突/调整结果；当前SHA独立复审/QA交编排，不合并、不发布。

### 约束与风险

- 装饰天蓝与可读文字/白字按钮用途分离，保持语义色配对；不降低WCAG阈值。原文纸张/文档颜色与合成探针色按说明保留。
- 不覆盖main的TanukiMotion factories、时长、reduced-motion结构或业务/布局；不改共享状态/印章，不读取真实凭据/用户数据。

### 产物与校验

- 只生成日常源码检查候选与外部校验，不生成或安装新APK、tag或Release；源码默认文件名不代表发布版本。
- author/committer均为 `leezzou <115924933+leezzou@users.noreply.github.com>`，不修改共享Git身份配置。
- 清理范围/理由/保留位置：原1731补丁交付记录的任务已被本轮接管，其基线、历史58/90项、NewApi失败与资源修复及设备边界已完整保留在TEST-REPORT历史节/设计专题/本记录，因此不重复引入该已完成阶段记录，以本次唯一落库记录替代。实际精确应用47条补丁路径（6新增），加本记录后仍48文件/7新增；原patch SHA与48/7身份不变。
- 两条相关旧主题记录1133设计令牌、1008页面接入已逐条读过：前者有CornerBasedShape/玻璃退化/leading内容色失败依据，后者有NpmAdaptiveWorkerPool时序失败、详情确认窗口层级及尚未执行的仪器/输入法/窄屏QA，本轮不整删。保留模板/印章、最近记录与仍使用证据。原清理计划的0538/1520已在独立PR #90交付，不重复混入主题PR，不做跨主题全目录裁剪。
- 应用结果：最新main上48文件全部通过git apply --check；实际47路径直接应用，无冲突、无手工产品代码调整。仅3个文档路径做落库归属/配色节标题与记录更新，landing完整取值表逐行比对一致。TanukiMotionSpecs/Settings逐字节等于主线，TokenSpec海拔段之前（含动效令牌）逐字一致，现有motion.press调用保留。

<a id="20261111-1848-copy-p0-core"></a>

## 20261111-1848-copy-p0-core

- Agent/会话标识：android_agent-73；runtime文案由同范围子任务协作，主会话统一验证/提交
- 时间：2026-11-11 CST

### 当前状态

- fetch实核最新main与用户给定一致：`c4bf0f63fb319b52074cc75b47f44f6844aec537`，已包含#90/#91/#92；从它建立指定分支，开始时工作区干净。
- 已读审计前220行、授权UI五文件和错误回归的相关断言。runtime三文件由子任务独立编辑，不同时写同一文件；本会话负责UI与最终验证。
- `app/src/main/res/values/strings.xml`只有app_name=Tanuki，没有首启/安装/错误资源，按产品名保留，不新增无用资源或改ID。
- 首轮71164ca在原测试不变时通过117项。后收到明确补充授权，产品安装标题改“安装失败”、复制诊断标题改“问题详情”，同步FailureCatalogTest中3处文字期望；分类、动作与行为断言不变。复用本记录，不修改共享状态或TEST-REPORT。其余两个仪器测试的对应页面/定位词未被本分支改变，不代改其他worker场景。

### 本次改动

| 文件/范围 | 目的 |
|---|---|
| `ui/SetupScreens.kt` | 56处中文展示位置：系统/运行组件与助手步骤、工作台、恢复/权限/基础工具、设备说明；阶段标题复用现有operationStage展示，raw状态不变。 |
| `ui/SetupGuide.kt` | 5处：运行环境引导、短主标题/说明、打开对话与下一步。 |
| `ui/RuntimeComponents.kt` | 15处：组件术语、安装动作、真实下载进度/ETA；纯展示名称映射，不动model.id、源/版本选择与回调。 |
| `ui/SourceSpeedControls.kt` | 19处：下载源动作、短测速解释、未知源友好缺省；失败主文不拼result.error，原错误状态保留。 |
| `ui/WorkspaceStorageControls.kt` | 23处：工作区限额/用量、输入单位与底线、统计/读取/保存分开的友好提示；不直接显示原异常，输入/保存/取消控制流不变。 |
| `runtime/FailureCatalog.kt` | 25处：原因+下一步和短动作，以及安装失败/问题详情标题；运行内存不足不再暗示清缓存能解决，分类/动作kind/重试参数/raw detail完整保留。 |
| `test/FailureCatalogTest.kt` | 补充授权仅同步3处标题期望，全部分类、动作与行为断言原样。 |
| `runtime/RuntimeFailurePolicy.kt` | 15处：助手错误与恢复短句，仍保留原因、等待秒数、次数/上限及中断任务不自动继续；状态机/参数/JSON完全不变。 |
| `runtime/OperationProgress.kt` | 2处：基础工具/后台检查；6条承载组件识别词的raw阶段不盲改。 |
| 本记录 | 本次唯一交接；不改共享状态、审计原文或其他worker负责的产品文件。 |

统计口径：产品文案合计160处（首轮158+授权标题2处），是相对c4bf0f63按改动后的中文展示字面量出现位置计，重复按位置计，包含新增术语映射及动态摘要归一；不包含3处测试期望，不是160个独立语义句，也不代表4787处全量完成。词法对齐另有150处原有字面量发生变化（含嵌套/组合字符串），不混用两种计数。

### 验证记录

| 实际命令/操作 | 结果 | 证据与限制 |
|---|---|---|
| fetch与建立ao/copy/p0-core | 成功，基线c4bf0f63完整SHA如上 | 默认工作区策略阻止共享Git元数据写入；原命令一次性授权重试成功。 |
| 审计/范围核对 | 完成 | XML资源只有产品名；五个UI文件的原始异常/阶段出口只在展示处归一，不改底层错误分类。 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1848-copy-p0-core.md`、`git diff --check` | 通过 | 本记录完整，不改共享状态/印章。 |
| `python3 scripts/check-publication.py --git` | 工作树896公开文件，0 findings | 初查索引895，提交前明确暂存8个产品文件+本记录，再核对实际索引。 |
| `python3 scripts/package-source.py`、`python3 scripts/check-publication.py --archive artifacts/sources/Tanuki-0.1.7-source.zip` | 896文件源码候选生成/扫描通过，0 findings | 文档收尾后重打包，核对外部SOURCE-SHA256SUMS，不把ZIP自身哈希写回归档文档。 |

首轮71164ca实际执行：`source scripts/ao-worktree-env.sh` 后 `./gradlew --console=plain --max-workers=2 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest --tests com.tanuki.FailureCatalogTest --tests com.tanuki.RuntimeFailurePolicyTest --tests com.tanuki.FailureRecoveryFollowupTest --tests com.tanuki.OperationProgressTest --tests com.tanuki.OperationProgressPresentationTest --tests com.tanuki.OperationProgressLayoutTest --tests com.tanuki.OperationRetryTest --tests com.tanuki.WorkspaceCapacityTest --tests com.tanuki.OnboardingPrepareTest --tests com.tanuki.EarlyRuntimeStartTest -x buildNativeRuntime --offline`。BUILD SUCCESSFUL（1m28s）。主源码、Android测试源码、JVM测试源码编译通过；XML实核117项/10结果文件/0 failures/errors/skipped，按命令类序分别8/32/11/7/13/5/8/5/11/17，原测试文件未改。

补充授权后实际重跑：同样环境、compile任务和参数，仅将测试过滤收窄到 `com.tanuki.FailureCatalogTest`、`com.tanuki.FailureRecoveryFollowupTest`、`com.tanuki.OperationRetryTest`；BUILD SUCCESSFUL（1m21s）。主/AndroidTest/JVM源码compile通过，XML实核27项/3结果文件/0失败/错误/跳过（8/11/8）。静态断言FailureCatalogTest与首轮相比严格只替换指定3个字符串期望，测试行为及分类/动作断言完全未动。

静态核对：产品仍仅8个授权文件，另有获准的1个测试文件和本次唯一记录。runtime三文件屏蔽字符串后与基线逐字一致；UI的命令键、onClick与enabled契约不变，新增when与阶段调用仅用于展示名称/摘要。初次直接用行正则取enabled时包含同行Text文案，发生检查脚本误报；改用词法屏蔽文案后通过，不是产品逻辑失败。全部160处新中文固定文案≤40且黑名单残留0；按钮≤6、标题≤12；动态名称/版本/容量仍保留实际值，不把它们塞进按钮。恢复摘要以真实原因与默认次数/秒数展开，最长36字。

本轮未执行本地lint、全量JVM、assemble、设备/adb、Android仪器或真实账号/模型测试；跳过原生任务的编译不构成可安装/发布产物，不沿用前一配色任务结果。现有弃用/API、SDK XML版本及analytics写权限提示保留，不计成产品失败。

### 未完成与暂停项

- UI/runtime文案、长度/黑名单/计数核对、compile/117项定向回归完成；交接上传检查后统一提交推送PR，当前完整SHA的独立review/CI由编排安排。
- 源文件范围外的首启欢迎OnboardingScreen/OnboardingPrepare、全局进度的ui/OperationProgress及OperationProgressPresentation（含ETA说明）、通知、应用更新、文件/对话/浏览器/远程确认与动态控制器出口本批不修改；需其他owner分批落实。本批runtime/OperationProgress不是同名UI进度文件，不为清全仓用词越界修改。
- 补充授权后安装标题已用“安装失败”，复制诊断标题已用“问题详情”，对应测试3处定位/期望同步。仅底层raw阶段/分类识别词继续保留；其他owner的默认折叠详情入口名称、本批未授权的动态出口尚未统一，不宣称全应用术语清零。
- API Key保存、真实模型/账号及真机验收保持暂停；设备由其他会话占用，本会话不操作。

### 下一步

1. 首轮已交付PR #94；补充授权仅暂存FailureCatalog产品/测试与本记录3路径，重查实际索引/归档外部哈希后更新PR head。整PR为8产品+1授权测试+1唯一记录，不改共享状态或TEST-REPORT，旧SHA批准不沿用。
2. 编排按当前完整SHA安排独立review、适用CI及独立设备QA，检查首启/安装失败、窄屏/大字体/无障碍；本会话不自行触发review或合并发布。
3. 其他owner按审计继续未覆盖的首启欢迎、通知、更新、文件/对话/远程与详情入口；不要将底层匹配词当作界面残留盲替换。

### 约束与风险

- 审计是建议与范围定位，不盲改内部英文键。OperationProgress的“准备安装/卸载Ubuntu/Node.js/DSH”“整理Ubuntu缓存”等6条raw字符串被FailureCatalog.resolveComponentId按stage补组件身份，完整保留；只在授权UI用现有operationStage映射短中文。版本异常仍保留“版本格式无效”匹配子串，命令/版本路由和错误分类原样。
- 术语统一：引擎/DSH的普通入口改“助手”，会话改“对话”，项目空间/容量预算改“工作区/工作区限额”，首页改“工作台”，组件集合改“运行环境”，系统/运行组件分开。本批没有模型单次回复展示，不把安装任务误称“本次回复”。
- 已有AppFailure.detail/复制诊断仍保留原始脱敏信息；未带详情宿主的测速/用量错误仅换友好摘要，原error字段未删，不在常驻UI输出原始异常，也不新建业务状态或确认流程。
- 不伪造安装ETA，不删除权限扩大、任务中断、工作区限额定期检查等实际后果。
- 原始异常只可保留于已有默认折叠的问题详情或诊断状态；没有详情宿主的摘要不继续拼原文，不新增业务状态/协议。

### 产物与校验

- 只生成日常源码检查候选及外部校验，不生成/安装APK，不创建tag/Release。
- author/committer按指定leezzou身份；工作区外Git元数据写只在对应命令被拒后一次性授权重试。
- 提交前已按主题检索copy/first-run/failure相关交接：1730审计仍覆盖尚未落库的其余清单；首启/错误出口记录属于仍使用的功能/验证依据，本轮文字改写没有替代它们；copy-flicker/landscape-copy属于文件复制反馈，不是本批文案。没有确认可删除的同范围旧P0实施记录；保留审计、模板/印章、最近记录及仍引用证据，不按日期删文档、不混入跨主题清理。

<a id="20261111-1854-settings-copy"></a>

## 20261111-1854-settings-copy

- Agent/会话标识：android_agent-88
- 时间：2026-11-11 18:54 CST

### 当前状态

9 个产品文件共 186 处展示字符串替换。非字符串内容逐行不变，设置分支、select 跳转值和 command 键不变。更新主按钮和详情折叠沿用 main 已有结构。生产和 Android 测试源码编译、最终整组 117 项定向 JVM、lint 均通过。首轮文件权限测试暴露“暂停”语义遗漏及两条旧术语断言；已补回暂停说明，并按授权将断言改为“启动助手”和“复制到工作区”。未操作设备。

### 本次改动

以下生产路径以 `app/src/main/kotlin/com/tanuki/` 为前缀：

| 文件 | 替换数 | 目的 |
|---|---:|---|
| `ui/TanukiUi.kt` | 63 | 设置说明解释选择后的影响；统一助手、对话、运行环境；未支持项直说暂不支持 |
| `ui/ApplicationUpdateCard.kt` | 5 | 更新标题、自动检查说明、安装前等待和详情动作 |
| `update/ApplicationUpdateManager.kt` | 11 | 更新检查、校验进度及安装提示；保留校验流程和异常分类依据 |
| `update/UpdateNotifier.kt` | 5 | 更新通知与渠道名称，给出查看入口和手动下载/安装边界 |
| `ui/DshSessionManagerPage.kt` | 77 | 空态、创建/重命名/归档、完全权限确认及关联标签；保留停止任务和数据后果 |
| `MainActivity.kt` | 4 | 通知权限对话框用途、拒绝后果及系统设置入口 |
| `lan/LanDiscovery.kt` | 1 | 附近设备权限用途、扫码/粘贴替代方式与设置位置 |
| `ui/WorkspaceFilesPage.kt` | 11 | 手机存储及助手写入授权说明、拒绝后果和撤销入口 |
| `ui/FileAccessPresentation.kt` | 9 | 文件权限弹层各状态说明；不改任何权限布尔值或分支 |

另仅修改 `app/src/test/kotlin/com/tanuki/FileAccessAndTransferPresentationTest.kt` 两条旧术语断言，不新增镜像测试，其余行为断言保持原样。资源 `values/strings.xml` 只有 app_name，无本范围条目，未修改。所有计数按实际变化的展示字符串位置计算，不包含测试断言和本记录。

### 验证记录

- `git status --short`、`git branch --show-current`、`git rev-parse HEAD`、`git ls-remote origin refs/heads/main`：远端/起始 HEAD 完整 SHA 一致、工作区干净；`git switch -c ao/copy/p0-settings` 初次沙箱拒绝，同命令授权重试成功，随后核对 branch/HEAD/status。
- 临时静态检查：所有产品文件行数和非字符串内容不变；设置路由、跳转值、command 键不变，共 186 个展示字符串位置变化。
- `python3 scripts/check-publication.py --git`：895 个公开文件，0 findings；`git diff --check` 通过。最终纳入交接后另行刷新检查。
- 首轮在 `source scripts/ao-worktree-env.sh` 后执行下面完整命令：2m25s 失败；主/Android 测试源码编译通过，117 tests、3 failures、0 errors/skip。失败为文件权限“暂停/启动 DSH/挂载路径”断言；测试失败未执行后续 lint。补回“暂停”后仅重跑 `FileAccessPresentationTest`：6 项、2 失败（11s），暂停语义已通过，仅余两条旧词断言。随后获得最小范围授权并同步两条断言。
- 单独重跑主源码 compile + lint：BUILD SUCCESSFUL（2m55s），退出 0；已有 SDK XML、API 弃用和 analytics 不可写提示，不影响最终成功。
- 两条断言同步后再跑整组：117 项、1 失败（47s），“启动后”未明确动作对象，不能通过“启动助手”语义断言。已将提示改成“请先启动助手，再允许本次写入；也可保持只读”，保留断言不变后重跑整组。
- 最终完整命令 BUILD SUCCESSFUL（2m43s），生产/Android 测试源码编译通过；XML 核对 10 类、117 项、0 failures/errors/skipped；lint 0 errors、105 warnings、1 hint：

```bash
source scripts/ao-worktree-env.sh
./gradlew --console=plain --max-workers=2 -PtestBuildType=debug \
  -I scripts/workspace-verification.init.gradle -x buildNativeRuntime --offline \
  :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin \
  :app:testModernDebugUnitTest \
  --tests 'com.tanuki.ApplicationUpdatePresentationTest' \
  --tests 'com.tanuki.AppUpdatePolicyTest' \
  --tests 'com.tanuki.AppUpdateFilesTest' \
  --tests 'com.tanuki.AppUpdateDownloadTest' \
  --tests 'com.tanuki.BackgroundUpdateChecksTest' \
  --tests 'com.tanuki.DshSessionManagerControllerTest' \
  --tests 'com.tanuki.ChatNotificationPolicyTest' \
  --tests 'com.tanuki.FileAccessPresentationTest' \
  --tests 'com.tanuki.LanDiscoveryTest' \
  --tests 'com.tanuki.FailureCatalogTest' \
  :app:lintModernDebug
```

Android 仪器、模拟器/真机 UI、深色/大字体/TalkBack、真实更新安装、真实运行环境和模型均未执行。Host 不代替设备 QA，`-x buildNativeRuntime` 的产物不交付或安装。

### 未完成与暂停项

本地最终验证完成，提交推送、创建 PR 和当前 head CI 将由本会话继续收尾，实际编号及结果以 provider 回执和 AO 报告为准。独立审查和 Android QA 未执行；不合并、不发布。两份设备测试仍有旧定位词：`SessionManagerDetailDialogTest.kt` 的对话详情/重命名/归档/完全权限标题，`FilesBrowserPresentationTest.kt` 的存储授权按钮；未获得修改这些文件的范围授权，本轮保留原样，设备 QA 前需同步。

### 下一步

本地验证已完成，按用户指定标题 `feat(copy): 设置更新权限文案重写` 开 PR，检查当前 head 的 CI。独立审查由人或编排方决定启动，设备由独立 QA 执行；设备测试定位词需先获得最小授权同步。

### 约束与风险

保留设置分类“引擎与更新”作为现有路由值，以及来源/许可详情中的官方组件名与协议值。范围外页面、官方动态内容、全局错误分类器和原始异常结构性分层未改。助手停止/只读空态和技能/产物空态缺少对应动作回调，只写明可执行路径，不新增按钮或逻辑。同源更新策略和折叠结构已在 main 存在，不冒充本轮新增能力。不读取账号或用户数据，不改变安全边界。

### 产物与校验

只有源码 PR；未生成发布附件、未覆盖安装、未打 tag 或 Release。工作脚本和草稿留临时目录，不提交。审计方案与历史验证交接仍有独有事项，本轮不删除；共享状态、CURRENT.json 和 TEST-REPORT 均不改。提交显式指定 `leezzou <115924933+leezzou@users.noreply.github.com>` 为 author/committer。提交前使用临时公开源码镜像运行 package-source.py、归档公开检查并核对外部 SHA-256 和本次修改文件字节；只用于候选检查，不分发旧源码 ZIP 冒充本次交付。最终提交/PR 身份以 provider 和 AO 报告为准，不为回填编号追加提交。

<a id="20261111-1900-motion-batch-b"></a>

## 20261111-1900-motion-batch-b

- Agent/会话标识：android_agent-90（AO Worker）
- 时间：2026-11-11，UTC+8

### 当前状态

起始 fetch 实核 `origin/main=226c3f95e0b71b8e4a34b13f8302ec68abce5314`，工作区干净，从该 SHA 建立 `ao/motion/batch-b`。编排期间 main 推进至 `824c6c9be855389708e2b3d5886f5670cbc6695e`（#94），核对新增路径与本地改动不重叠后，保留修改执行 `git merge --ff-only <完整SHA>`，成功且随后核对分支、HEAD 与改动清单。最终验证绑定 824c6c9b + 本 PR 改动。

编排方已于 2026-11-11 11:50 UTC 批准并重复确认最小接线，明确文件页和浏览器页无并发占用。已移除文件页旧 tanukiEnter，接文件详情和浏览器原生二级状态，保留兼容函数并在宿主内抑制旧入场。A 批会话管理列表已无旧入场，继续只保留状态 Crossfade。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `ui/TanukiPageTransitions.kt` | 单槽页面宿主、同级/层级策略、RTL 镜像、0ms 降级；WebView 只用原生遮罩；嵌套父层活跃时子层即时 |
| `ui/TanukiUi.kt` | 四目的地和设置/分区统一宿主，移除宿主内旧 tanukiEnter；沿用原返回回调与保存状态 |
| `ui/TanukiAdaptiveShell.kt` | 仅窄屏 TwoPane 列表/详情接统一宿主，宽屏布局及 drawer 仍由原逻辑/系统拥有 |
| `MainActivity.kt` | 注释明确独立会话管理 Activity 保留系统所有者，不加自定义过渡 |
| `ui/WorkspaceFilesPage.kt` | 仅列表/查看器渲染段接统一宿主，稳定文档 key，偏好恢复标记使异步恢复立即显示；去旧入场并保留查看器提前返回，不改导入/复制/空态/传输浮层逻辑 |
| `ui/AgentBrowserPage.kt` | 仅页面内容切换段接统一宿主，按已有 back() 判方向；网页返回只遮罩，菜单/授权确认/网页挂载逻辑不变 |
| `ui/TanukiMotion.kt` | 保留 tanukiEnter 签名兼容；宿主内不创建第二个入场，reduced 直接返回原 Modifier |
| `TanukiPageTransitionsTest.kt` | 8 项真实 Compose spec/页面关系回归，新增文件稳定身份及浏览器已有返回目标 |
| `docs/DESIGN-TOKENS.md`、`docs/TEST-REPORT.md`、本记录 | 转场矩阵、实际验证、独立 QA 与设备未覆盖项 |

以上代码路径省略公共前缀 `app/src/main/kotlin/com/tanuki/`，测试位于 `app/src/test/kotlin/com/tanuki/ui/`。不改 TanukiAdaptiveComponents：其中没有导航切页。不改 DshNativePage。

### 验证记录

- `git fetch origin main`：首次和 workspace-write 重试因共享 Git 元数据在工作区外被拒绝；danger-full-access 批准后成功。
- `git switch -c ao/motion/batch-b 226c3f95e0b71b8e4a34b13f8302ec68abce5314`：成功，随后核对分支、HEAD 和干净状态。
- 首轮 `source scripts/ao-worktree-env.sh` 后执行 `./gradlew --console=plain --max-workers=2 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest --tests 'com.tanuki.ui.TanukiPageTransitionsTest' --tests 'com.tanuki.ui.design.TanukiMotionSpecsTest' --tests 'com.tanuki.TanukiPageVisualsTest' -x buildNativeRuntime --offline`：退出 0，BUILD SUCCESSFUL（2m16s）。产品/Android 测试源码编译通过；XML 实核 23 项 / 3 类（6/5/12），0 failures/errors/skipped。跳过原生运行时，仅 Host 证据，不生成可安装交付包。
- `:app:lintModernDebug`（相同环境、参数）：退出 0，BUILD SUCCESSFUL（2m02s）。XML 实核 0 errors / 105 warnings / 1 hint，新宿主文件没有 lint 告警。
- 在 824c6c9b 上补文件/浏览器接线后执行五类定向，XML 实核 40 项（新转场8、A批motion5、页面visuals12、保存状态4、查看器布局11）全部通过。该阶段 BUILD SUCCESSFUL（3m06s）；完成测试后发现异步阅读恢复会被当作主动导航，已加仅动效标记及回归断言，最终证据为下条完整重跑。
- **最终验证**：在上述 824c6c9b 基线上、阅读恢复修正后，执行 `source scripts/ao-worktree-env.sh`，再运行 `./gradlew --console=plain --max-workers=2 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest --tests 'com.tanuki.ui.TanukiPageTransitionsTest' --tests 'com.tanuki.ui.design.TanukiMotionSpecsTest' --tests 'com.tanuki.TanukiPageVisualsTest' --tests 'com.tanuki.TanukiSaveableStateTest' --tests 'com.tanuki.FileViewerLayoutTest' :app:lintModernDebug -x buildNativeRuntime --offline`：退出 0，BUILD SUCCESSFUL（2m57s）。产品/Android 测试源码 compile 通过；XML 实核 **40 项 / 5 类（8/5/12/4/11）/ 0 failures / 0 errors / 0 skipped**；lint **0 errors / 105 warnings / 1 hint**，新宿主没有 lint 告警。业务页面 tanukiEnter 调用清零，仅保留兼容定义。
- 提交前执行 `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1900-motion-batch-b.md`、`python3 scripts/check-publication.py --git`、`git diff --cached --check`；在系统临时目录复制公开白名单快照运行 `python3 scripts/package-source.py`，核对 `artifacts/SOURCE-SHA256SUMS` 并执行 `check-publication.py --archive`。这些只校验公开边界/归档，不作为产品或设备验证。
- 环境准备 15s 完成；脚本跳过缓存中的符号链接，不改 SDK/全局 Gradle。构建有既有 SDK XML、analytics 目录不可写与弃用/API 提示，无首轮编译/测试失败。
- Android 仪器、设备 UI、真机、真实模型：未执行；设备留给独立 QA。

### 未完成与暂停项

- 实现目标无剩余，范围决策已解决、文件/浏览器接线与最终复验均完成。统一 commit/push/创建 PR；最终 PR、head 与 CI 状态由 provider/AO 完成回执记录，不为回填再提交。
- 独立审查、设备视觉/快速切页/WebView/Surface/RTL/系统设置/帧时序仍需独立 QA。
- API Key、真实模型和真机暂停项保留。

### 下一步

1. 最终 compile、40 项定向 JVM 和 lint 已通过，保持已测源码；收尾公开候选/临时源码包检查后统一提交推送并开指定 PR。
2. 核对 author/committer、PR head 与 CI；若当前 head 检查失败，按任务范围修复，不沿用旧 SHA 审查。
3. 由编排方安排当前 SHA 的独立审查及 Android QA；未授权合并。

### 约束与风险

只处理宿主和转场层，不改聊天内部、业务返回栈或 WebView 生命周期；不操作设备、不读真实凭据。注释中文，Git author/committer 使用 leezzou。最初范围协调消息正常排队，steer 因 CHAT_STEER_UNSUPPORTED 退出 1；后通过编排正常消息取得批准，未改 AO 配置。

### 产物与校验

未生成发布 APK 或交付附件。提交前检查本范围过期交接；动效审计仍是后续各批实施依据；A 批仍有独有系统设置/浮窗/设备验证未完成项，均保留，无属于本批可确认过期的旧记录可删。普通 PR 不刷新 AGENT-STATE/CURRENT.json。

<a id="20261111-1900-motion-batch-f"></a>

## 20261111-1900-motion-batch-f

- Agent/会话标识：android_agent-94
- 时间：2026-11-11（开始）

### 当前状态

远端 main 与开始 HEAD 均为 `37358849742b2ad3e2b24bc539395ec20801f413`；工作区开始干净，分支 `ao/motion/batch-f`。系统栏继续由宿主/系统控制，不接触设备。正文实例保持稳定；全屏由最终状态决定布局，退出 chrome 覆盖绘制，不逐帧追赶正文高度。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | 稳定正文槽位；全屏 chrome 240ms alpha，不补间正文尺寸；搜索条单高度所有者；文本分段 160ms alpha、位置立即；加载 reduced 静态标识 |
| `app/src/main/kotlin/com/tanuki/ui/FileTransferPresentation.kt` | 字节精度 fraction、稳定任务/阶段身份、100ms linear 重定向与 unknown/reduced 即时复位 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt` | 仅传输浮层/详情/行：胶囊 8dp + alpha、退出摘要保留且立即禁操作；系统 sheet hide 后卸载；真实字节进度与未知总量静态降级 |
| `app/src/main/kotlin/com/tanuki/ui/ZoomableContent.kt` | 查看器图片可选保持视口阅读状态，双击 240ms，捏合取消补间；其他调用方保留原有即时缩放/视口重置策略 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceDocxPreview.kt` | 只读正文加载后 160ms alpha；未知加载 reduced 静态标识 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspacePdfPreview.kt` | 页面首次位图加载 160ms alpha，标识按页而非位图，避免换分辨率重播；reduced 不创建 spinner |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceXlsxPreview.kt` | 读取/切工作表 160ms alpha；缩放引发的大表重排保持立即 |
| `app/src/test/kotlin/com/tanuki/FileViewerMotionTest.kt` | 全屏最终视口几何、单高度所有者、播放器单实例/无整体 alpha、分页无滑动、进度线性重定向、reduced 目标态；静态约束不等于设备执行 |
| `app/src/test/kotlin/com/tanuki/FileAccessAndTransferPresentationTest.kt` | 总量未知、字节精度及任务/阶段身份边界 |
| 本记录 | 目标、决策、实际验证与未测边界 |

#### 时长矩阵

| 交互 | 正常模式 | reduced-motion |
|---|---|---|
| 查看器全屏 chrome | alpha 240ms standard；正文位置/尺寸按最终状态一次切换 | 当帧切换 |
| 搜索工具条 | 单容器高度 + alpha 240ms standard | 当帧切换 |
| 文本分段 | 新段 alpha 160ms standard；位置立即，无整页滑动 | alpha=1，位置立即 |
| 图片/DOCX/PDF 页/XLSX 表就绪 | alpha 160ms standard；不添加加载缩放或业务最短等待 | 立即显示 |
| 查看器图片双击 | scale/pan 同步 240ms standard，手势即时接管 | 立即到目标倍率 |
| 传输胶囊显隐 | alpha 160ms standard + 8dp 位移 240ms standard | 当帧显隐 |
| 确定传输进度 | 100ms linear，从当前值重定向到真实新值；换任务/阶段立即复位 | 真实目标值立即 |
| 未知总量 | 系统不定进度，不显示百分比 | 静态标识 + 真实文案/耗时 |
| 传输详情窗口、系统栏 | 保留系统所有者，未给平台动画伪标固定时长 | 遵循平台设置；待设备验证 |

DOCX/XLSX 的字号、表格缩放涉及正文重排，本次保留立即更新，避免每帧重算整份文档；PDF 手势/适应模式/还原仍沿用现有策略。图片保留倍率与边界内平移；全屏最终约束由应用提交，系统 inset 的平台过程不在应用内另做追赶动画。

### 验证记录

- `git ls-remote origin refs/heads/main`：确认上述基线。
- 分支创建首次因 Git 元数据位于工作区外被沙箱拒绝；同命令获准后成功，分支与 HEAD 已复核。
- 首轮 compile 因新增 `Icons.Default.Sync` 不在当前依赖图标集中失败，改用已有 `Info`。
- 第二轮产品 compile 成功；33 项定向 JVM 中 32 通过、1 项源码约束失败：测试误截取视频准备分支，收窄到实际播放器分支后待复跑。
- 合并 lint 命令首次因 `--tests` 跟在 lint 任务后被错误解析，检查未执行；调整任务顺序后重新运行。
- 最终命令：在独立 worktree 的 JDK17、SDK36、独立 Gradle/Android user home 下，执行 `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin :app:testModernDebugUnitTest --tests 'com.tanuki.FileViewerMotionTest' --tests 'com.tanuki.FileViewerLayoutTest' --tests 'com.tanuki.FileTransferPresentationTest' --tests 'com.tanuki.ZoomPolicyTest' --tests 'com.tanuki.ui.design.TanukiMotionSpecsTest' :app:lintModernDebug -x buildNativeRuntime --console=plain`。最终退出 0，`BUILD SUCCESSFUL in 5m 31s`。产品 compile 与定向 JVM 33 项通过（5 + 11 + 6 + 6 + 5，0 failures/errors/skipped）；lint 通过，0 errors / 116 warnings / 2 hints。独立解析 JUnit XML 和 lint XML 确认计数。
- 本轮 Android 仪器测试、设备安装/视频播放/全屏帧序列、系统栏 inset、深色/大字体、TalkBack 与真实传输均未验证；不把历史结果计为本轮通过。`-x buildNativeRuntime` 仅用于宿主检查，不产生可安装交付物。
- 交接格式检查首次要求明确未验证标记，补全后 `check-handoff.py --pr --record docs/handoffs/20261111-1900-motion-batch-f.md` 通过；`check-publication.py --git`：904 public files / 902 index entries / 0 findings；`git diff --check` 通过。
- 最终 lint 首轮发现本次排版整理引入的 `SuspiciousIndentation`（阅读工具按钮条件语句），补明确大括号后重新运行；该轮在 lint 失败处停止，未把未执行的 JVM 计为通过。
- 沙箱拒绝一次只读宿主进程查询，未提升权限、未影响构建流程；以本任务日志与退出码判断验证状态。

### 未完成与暂停项

- 实现、最新 compile/JVM/lint 与上传候选检查完成；统一提交推送并开 PR，具体 PR/提交 SHA 由 provider、最终回执和 AO report 记录，不为回填编号再修改源码。
- 未完成：当前 SHA 的独立 review、设备 QA 与 provider CI 由后续流程核对，不将本轮宿主结果当成全部门禁通过。
- 独立设备 QA、当前 SHA 独立审查待协调；API Key 保存、真实模型/账号和真机验收继续暂停。

### 下一步

1. 统一 commit/push 并创建 `feat(motion): 文件查看器与传动效` PR；不合并。
2. 经用户/编排者同意启动 AO 原生独立审查；独立 QA 在当前 SHA 验证快速反复全屏、播放/缩放/阅读位置、系统 inset、手势接管、传输取消、未知总量与 reduced 切换。
3. 合并前核对同一 SHA 的 CI、独立审查与设备证据；合并/发布仍须另行授权。

### 约束与风险

不安装、不读取设备/凭据，不改系统设置。全屏系统 inset 的平台过渡由系统拥有；视频 Surface 不套 alpha/scale。只在解码完成后揭示图片，不增加业务最短等待。

### 产物与校验

不交付 APK，不发布、不合并。使用 `scripts/package-source.py` 的公开清单将 904 个文件复制至系统临时目录，再在该快照运行原脚本（未传发布版本），生成检查用源码候选；`check-publication.py --archive <临时候选>` 为 0 findings，`shasum -a 256 -c SOURCE-SHA256SUMS` 通过。交接收尾后刷新同一快照与候选再核对；未包含原生对应源码，不分发原生二进制。临时日志/源码候选不作为用户交付附件，不写入 Git 工作区。普通 PR 不刷新共享状态/印章。

提交/推送前检查过期记录：本范围内动效审计仍是其他批次与设备验证依据，未确认可删除的过期记录，保留其独有未完成事项；不开展跨任务清理。

<a id="20261111-1915-chat-redesign-v2"></a>

## 20261111-1915-chat-redesign-v2

- Agent/会话标识：android_agent-89（实现 Worker）
- 时间：2026-11-11 CST

### 当前状态

- 分支 ao/chat-redesign/v2，基线 c4bf0f63fb319b52074cc75b47f44f6844aec537；起始工作区干净。
- 已实现：会话抽屉/两行顶栏、助手无气泡/用户右侧气泡、代码复制/分享、相邻工具组及精确待审批提示、提问卡结构和本地选择、单胶囊输入/系统语音输入/生成占位/未知用量环、本次观察计时。
- 已验证：主源码、AndroidTest/JVM 测试源码编译；定向 JVM 19 类248项全通过；lint 0 errors/105 warnings/2 hints；公开候选检查 0 findings；git diff --check 通过。
- 待验证：设备 UI、真实 DSH、独立审查、当前 PR CI。没有安装、启动、操作设备或生成发布包。
- DshComposerController.kt 和 web 协议文件无 diff。工作目录、回合统计、上下文用量均缺现有字段，界面显示未提供，不填造数字。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt | 顶栏与会话抽屉、无气泡助手正文、用户/发送占位右对齐、复制/分享/详情、相邻工具组接线、分页恢复 key 映射、运行状态与观察计时。 |
| app/src/main/kotlin/com/tanuki/ui/DshChatRender.kt | 展示状态、生成占位、真实用量百分比规则、相邻工具展示分组及成员 key 恢复；不改变协议时间线。 |
| app/src/main/kotlin/com/tanuki/ui/DshMarkdown.kt | 正文16sp/24sp、段间距14dp、代码12dp圆角/内边距、复制反馈与系统分享，保留完整复制和可选行号；颜色从设计令牌读取。 |
| app/src/main/kotlin/com/tanuki/ui/DshToolCard.kt | 相邻调用组的真实状态/结果数、展开内容、精确待审批锁提示及现有审批抽屉入口；保留风险、目录、命令、diff与输出。 |
| app/src/main/kotlin/com/tanuki/ui/DshQuestionCard.kt | 问题标题、上下文标签、可访问选项、其他回答、双按钮；缺少RPC回调时禁用提交/跳过并说明本地选择。 |
| app/src/main/kotlin/com/tanuki/ui/DshComposerBar.kt | 56dp单行胶囊、加号菜单保留引用/命令/附件、系统语音输入校验会话代次、发送/停止主键、生成中占位、未知用量环；去掉高度补间，圆角走S-toggle。 |
| app/src/main/kotlin/com/tanuki/ui/DshDock.kt | 去掉容器和槽位的叠加高度动画，保留审批/目标/待办优先级及+N规则；目标/待办内部展开不叠加父层高度动画。 |
| app/src/test/kotlin/com/tanuki/DshChatRenderTest.kt | 新增缺失用量/断线状态/生成占位与工具分组边界、锚点隔离、成员key恢复回归。 |

### 验证记录

实际执行 `source scripts/ao-worktree-env.sh` 后：

```bash
./gradlew --console=plain --max-workers=2 -PtestBuildType=debug \
  -I scripts/workspace-verification.init.gradle \
  :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin \
  :app:testModernDebugUnitTest \
  --tests 'com.tanuki.DshChatRenderTest' --tests 'com.tanuki.DshMarkdownTest' \
  --tests 'com.tanuki.DshChatFollowStateTest' --tests 'com.tanuki.DshChatWindowTest' \
  --tests 'com.tanuki.DshNativePagingTest' --tests 'com.tanuki.DshComposerControllerTest' \
  --tests 'com.tanuki.DshQuestionTest' --tests 'com.tanuki.DshToolTimelineTest' \
  --tests 'com.tanuki.DshDock*Test' --tests 'com.tanuki.DshApproval*Test' \
  --tests 'com.tanuki.TextFileReaderTest' \
  --tests 'com.tanuki.ui.design.TanukiDesignTokensTest' \
  --tests 'com.tanuki.ui.design.TanukiMotionSpecsTest' \
  :app:lintModernDebug -x buildNativeRuntime --offline
```

- 本轮最终 BUILD SUCCESSFUL（3m18s），JDK17/SDK36，隔离 Gradle 缓存；跳过原生运行时构建，不作为设备或发布包。
- XML 实核19个结果文件：248 tests/0 failures/0 errors/0 skipped。含渲染19、Markdown16、follow5、window18、paging16、composer29、question8、tool15、dock16、approval51、GBK读取27、设计令牌23、动效令牌5。
- lint：0 errors/105 warnings/2 hints；聊天观察计时存在1条非阻断 AutoboxingStateCreation 提示。保留实际数字，不宣称所有警告为历史。
- `python3 scripts/check-publication.py --git`：895 public files/895 index entries/0 findings（写入本记录前）。`git diff --check` 通过。
- 首轮构建在参数同步期间读到工具组调用与声明不一致；第二轮发现项目图标集不含Mic；已同步参数并以Canvas绘制麦克风图标，最终同套命令重新通过。SDK XML版本、analytics目录不可写及既有弃用提示未阻断构建。
- 本轮未执行完整 JVM、assemble、Android 仪器/设备、真实模型，不把旧记录或源码存在算成本轮通过。

#### PR #95 停止入口接线修复

- 审查线程：`PRRT_kwDOVgRm4s62JzOV`。原页面移除独立停止入口，但生产代码没有调用 `setGenerationRunning`，输入栏无法从官方运行状态切换为停止键。
- 修复：`DshNativePage` 用 `LaunchedEffect(controller, composer)` 持续调用 `bindDshComposerRunState`；绑定先 `setSession` 再 `setGenerationRunning`，只在会话/运行态变化时同步，避免无关页面更新用旧 idle 干扰发送回执。沿用既有官方 idle 确认、停止超时与会话代次保护，不改控制器或协议。
- 新增 `DshNativeComposerBindingTest`，连接实际 `DshNativeController`、`DshComposerController` 和页面所用绑定函数，合成官方事件与 Remote 边界；覆盖进入运行会话可停止、发送受理后可停止、官方 idle 复位、会话切换保留独立草稿/停止状态、旧会话事件不污染当前会话，以及无关连接更新不覆盖发送状态。
- 原 head `47a1f36956b9c456d84f65e4d659beaba7d5f048` 的 push/PR 共4项CI已核实通过；不冒充修复后head的结果。
- 修复后使用相同 Gradle 配置运行 `:app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest --tests 'com.tanuki.DshNativeComposerBindingTest' --tests 'com.tanuki.DshComposerControllerTest' --tests 'com.tanuki.DshNativeControllerTest' --tests 'com.tanuki.DshNativeLiveTest' --tests 'com.tanuki.DshChatRenderTest' :app:lintModernDebug`。主/AndroidTest/JVM源码编译通过，XML实核5类97项（4/29/35/10/19）、0失败/错误/跳过；lint 0 errors/105 warnings/2 hints，BUILD SUCCESSFUL（1m51s）。
- 首轮夹具误把 `session/cancel` 参数的 `request.sessionId` 读作顶层字段，3项超时；该轮已停止，不计通过。修正夹具后同套97项回归通过。
- 以上为 Host 真实控制器组合回归，不是 Android 设备按钮操作；设备验收仍交独立QA。

#### PR #95 分页工具组稳定性与停止兜底补充

- P2线程：`PRRT_kwDOVgRm4s62JzOZ`。原分组按新时间线重建，前插相邻工具A会把已展开的[B,C]改成[A,B,C]，首key/callId变化导致折叠，旧像素偏移也无法对应同一成员。
- 修复：`DshChatGroupState`按会话保留已展示组的起点，`DshNativePage`通过`rememberSaveable`保存/恢复边界；前插后A单独成组，原[B,C]的key、首callId、成员顺序与组内布局不变。历史/新消息两段仍先拆后聚合，窗口释放时清理不再存在的边界。
- 新增回归验证展开组前插相邻工具后完整展示行相等、Saveable key及首callId不变、成员顺序不变，并使用页面相同renderKeys/restoreIndex对合成展开高度恢复组内C输出像素位置；另测边界保存恢复/会话隔离/窗口释放清理及阅读锚点边界保留。这是Host恢复模型回归，不是设备像素验收。
- P1补充：页面在官方运行中、输入栏尚未绑定或会话错配时保留调用`controller.stop()`的兜底入口；正常绑定或停止中不重复显示。绑定额外收集运行中的流式正文变化以刷新既有watchdog，静默超时仍只开放继续发送，不伪造官方idle；不改控制器或协议。
- 新增停止兜底策略回归及真实绑定的stream/watchdog回归：静默降级→流式片段刷新→再次静默后仍可发送与停止。
- 本轮沿用上文JDK/SDK/Gradle配置，执行主/AndroidTest源码编译、`:app:testModernDebugUnitTest`与`:app:lintModernDebug`；`--tests`逐类筛选DshChatRenderTest、DshChatWindowTest、DshChatFollowStateTest、DshNativePagingTest、DshNativeComposerBindingTest、DshComposerControllerTest、DshNativeControllerTest、DshNativeLiveTest、DshToolTimelineTest。本轮XML实核9类156项（23/18/5/16/5/29/35/10/15）、0失败/错误/跳过；lint 0 errors/105 warnings/2 hints，BUILD SUCCESSFUL（3m6s）。上一轮97项结果不充作本轮结果。

#### PR #95 同值 idle 确认与持续流式活动回归

- 新线程：`PRRT_kwDOVgRm4s62KOGA`（旧SHA分页意见）、`PRRT_kwDOVgRm4s62KOFu`（同值idle）、`PRRT_kwDOVgRm4s62KOF1`（持续流式）。分页稳定边界与流式正文刷新已在远端`08d1b9e1d862b46380f3bd3c7b379dad372969e6`，不是未提交工作区改动；本轮继续补确认身份及更强回归。
- `DshNativeController.kt`仅增加官方运行确认的本地投影版本：每条既有`DshLiveEvent.Running`递增`runningConfirmationVersion`，即使布尔值未改变也形成新StateFlow状态。没有新增协议、改变发送/停止逻辑或修改`DshComposerController.kt`。
- 绑定投影纳入会话、运行态、确认版本、运行中的流式正文。新idle可覆盖发送受理后的乐观生成态并完成REQUESTED停止确认，无关连接/页面更新仍被排除；重复running确认及真实stream均刷新既有watchdog。
- 新增组合回归：accepted后不注入true直接false能复位；停止REQUESTED后直接false确认STOPPED；连续Streaming跨越300ms超时窗口不出现任何stale，真正停止活动后才进入静默降级。既有无关连接更新不覆盖accepted状态、切会话隔离等回归一起执行。
- 首轮159项中持续流式回归1项超时：夹具用非累计编号文本，却等待累计正文全文相等；按既有增量合并语义，第二帧已追加到第一帧，导致条件不成立。已改为严格累计前缀快照后重跑，不修改产品合并策略，不把失败计为通过。
- 使用上一节相同Gradle任务与九类筛选，修正夹具后BUILD SUCCESSFUL（54s）；主/AndroidTest/JVM源码编译通过，XML实核9类159项（绑定测试8项）、0失败/错误/跳过；lint 0 errors/105 warnings/2 hints。设备QA与真实服务未执行。

### 未完成与暂停项

1. 提问卡当前无聊天页调用点，DshNativeState无提问正文，回答/跳过RPC未确认。组件结构与本地选择已完成，实际提问、回答和等待回答状态未接通，提交/不回答禁用。
2. 会话摘要无工作目录，消息模型无回合耗时/步骤/token，输入模型无上下文用量/上限。使用明确未知说明，不能按时间戳或工具历史猜测。
3. 生成期间可编辑与占位文案已实现，流式插话协议未确认；主键保留停止，现有发送和静默降级规则不变。
4. 系统语音输入/分享、窄屏/大字体/深色/TalkBack、流式滚动、分页前插、真实审批和设备帧率待独立QA。
5. 独立审查和CI待PR创建后安排。API Key保存、真实模型/账号、真机验收继续暂停；未获授权不合并/发布。

### 下一步

1. 本记录用于满足现有 CI 的任务交接要求；落盘后运行 `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1915-chat-redesign-v2.md` 及带本次变更清单的检查，再跑公开检查。
2. 按明确文件清单暂存，作者和committer均为 leezzou <115924933+leezzou@users.noreply.github.com>，统一commit/push，创建指定标题PR，正文保留逐项对照与未完成项。
3. 独立QA在最终SHA检查长历史前插/锚定、跨会话草稿与语音结果、输入法、多行输入、代码横向滚动/复制/分享、工具组展开/待审批抽屉以及深浅色/大字体。
4. 独立审查由编排者决定是否启动AO原生review；本实现会话不自行审查，不合并、不发布。

### 约束与风险

- 仅使用合成测试数据；不读凭据、用户会话或内部备份，不碰设备和暂停项。
- 工具组先按阅读锚点拆段再聚合，原始seq/key保留；分页插入后将组内成员key映射到展示组，避免跨锚点吞并新调用。真实像素滚动仍需设备QA。
- 授权行只按当前会话+callId匹配真实pending审批，不按命令或风险猜测授权状态。
- 语音仅由用户点击系统入口触发，结果先校验原会话代次，不自动发送；系统不可用时提示键盘输入。
- 创建分支与fetch曾受linked worktree共享Git元数据权限限制，相同命令获得授权后完成；未更改共享Git配置或重写历史。

### 产物与校验

- 本轮交付为源码PR；不生成APK、发布附件、tag或Release，不覆盖安装。
- 旧聊天交接仍包含分页/协议/审批及未验证事实，本轮未确认可删记录，保留20261111-1007、20261110-1802及20261110-1233记录；不扩大到跨任务清理。
- 不改AGENT-STATE.md或CURRENT.json。源码ZIP不作为本任务交付物；不以旧ZIP对应本次源码。
- 最终提交SHA、PR URL及CI以PR页面与AO最终回报为准，不为回填元数据另作提交。

<a id="20261111-2030-motion-batch-e"></a>

## 20261111-2030-motion-batch-e

- Agent/会话标识：android_agent-93
- 时间：2026-11-11（项目批次日期）

### 当前状态

远端 main 与起始 HEAD 均实核为上述完整 SHA，起始工作区干净；当前分支已建立为 `ao/motion/batch-e`。初次与 workspace-write 重试因 shared Git 元数据 index.lock 不可写失败；随后仅对相同命令申请一次更广沙箱权限后成功，HEAD 仍为基线，改动完整保留，没有改变会话文件策略。目标仅 Host 编译验证，不操作设备。代码块/工具复制已使用 TanukiCopyButton，复用其 160ms/1500ms/重按计时实现。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/DshToolCard.kt` | 标题点击才改变详情目标；恢复与失败首帧立即展示；单一顶端裁剪展开；组过渡期间通过组合局部状态暂停子卡/diff 的高度动画；长 diff 240/480dp 虚拟列表视口，禁止整卡尺寸动画 |
| `app/src/main/kotlin/com/tanuki/ui/DshChatRender.kt` | 长详情分类、diff 上限与即时提交状态纯策略 |
| `app/src/main/kotlin/com/tanuki/ui/DshMarkdown.kt` | 新增块仅 alpha，初始/历史与流式 delta 不入场；显式 streaming 参数使用既有 TanukiStreamingCursor；代码块继续复用 TanukiCopyButton |
| `app/src/main/kotlin/com/tanuki/ui/DshQuestionCard.kt` | 选中背景/边框 S-toggle；enabled 直接依赖当前草稿和 submitting，不等待视觉状态 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalSheet.kt` | 入口条颜色/计数与固定 40dp 提示槽 alpha；不改抽屉容器与业务回答 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 编排批准的单行最小接线：仅 StreamingCard 调用增加 streaming=true；静态消息继续使用默认 false |
| `app/src/test/kotlin/com/tanuki/ui/DshChatMotionTest.kt` | Compose spec 时间采样、长 diff 视口策略、详情分类、按钮即时状态五项 JVM 回归 |
| `app/src/androidTest/kotlin/com/tanuki/DshChatMotionPresentationTest.kt` | 复制重按计时/固定宽度、按钮即时 enabled、4000 行 diff 实际测量/realtime 更新、reduced 光标像素、静态消息无光标五项仪器回归源码；未执行 |

#### 时长矩阵

| 交互 | 普通模式 | reduced |
|---|---|---|
| 短详情打开 / 收起 | 240ms standard / 240ms exit；alpha 160ms | 0ms |
| 长详情打开 / 收起 | 300ms enter / 240ms exit；alpha 160ms | 0ms |
| 长 diff 视口 | 240dp → 480dp 300ms enter / 240ms exit；内容 alpha 160ms | 0ms |
| 工具组显隐 | 打开 300ms enter / 收起 240ms exit，alpha 160ms；组过渡期间子卡/diff 高度直接到位，组稳定后子卡独立补间 | 0ms |
| 光标 | 既有 ambientPulse 完整 2400ms，1200ms linear 反向 | 不创建循环、常亮；仅真实流式调用启用，静态消息隐藏 |
| 代码/工具复制 | 既有公共 S-copy 固定宽度 160ms；1500ms 后复位，重按重置，遵循无障碍推荐超时 | 0ms 切换，保留文字和计时 |
| 提问选中 / 审批入口条色彩 | S-toggle 160ms standard | 0ms |
| 消息内后续新增块 / 审批提示 | alpha 160ms，无位移；提示原 3 秒计时保留 | 0ms |
| 提交 enabled / 权限业务值 | 即时，不依赖动效完成 | 即时 |

### 验证记录

- 已执行 git status、git rev-parse HEAD、git ls-remote origin refs/heads/main；基线一致。
- git switch -c 指定分支前两次被 shared Git 元数据权限拦截，失败后核对分支与 HEAD 未变；仅对相同命令申请一次更广沙箱权限后成功，核对分支 ao/motion/batch-e、HEAD 仍为基线。
- 首轮 `:app:compileModernDebugKotlin` 失败（1m18s）：审批固定槽中的 AnimatedVisibility 被隐式 ColumnScope 接收者误解析；显式调用顶层函数后修复。
- 已执行 `source scripts/ao-worktree-env.sh` 后 `./gradlew --console=plain --max-workers=2 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime --offline :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest`，重复传入六个 `--tests`：`com.tanuki.ui.DshChatMotionTest`、`com.tanuki.ui.design.TanukiMotionSpecsTest`、`com.tanuki.DshChatRenderTest`、`com.tanuki.DshMarkdownTest`、`com.tanuki.DshQuestionTest`、`com.tanuki.DshToolTimelineTest`。BUILD SUCCESSFUL（2m01s），退出 0。
- XML 实核 72 项 / 6 类，0 failures / 0 errors / 0 skipped，分别为 5/5/23/16/8/15；其中新增 JVM 5 项。Android 仪器测试源码编译通过，不表示设备执行通过。
- 同组 compile/测试并加 `:app:lintModernDebug` 阶段验证 BUILD SUCCESSFUL（4m31s），72 项通过，lint 0 errors/105 warnings/3 hints；随后补齐父子高度协调并把 Markdown 块数状态改为 mutableIntStateOf。
- 接线前最终重跑相同 compile/六类定向测试/lint 命令：BUILD SUCCESSFUL（6m49s），退出 0；XML 再核对 72 项 / 6 类、0 failures/errors/skipped；lint **0 errors / 105 warnings / 2 hints**，两个 hint 位于未改动的 DshNativePage.kt 和 SourceSpeedControls.kt，指定五文件没有新增 lint finding。仪器测试 class 时间实核晚于最后一次源码编辑，包含四项最终用例；仍未在设备执行。
- `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-2030-motion-batch-e.md` 通过；`python3 scripts/check-publication.py --git`：905 public files、902 index entries、0 findings；`git diff --check` 通过。交接检查首次因草稿没有明确“未验证”字段失败，补充边界后通过。
- 获批单行接线后，使用同一完整命令重跑产品/Android 测试源码 compile、六类定向 JVM 和 lint：BUILD SUCCESSFUL（5m19s），退出 0；XML 实核 **72 项 / 6 类 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 105 warnings / 2 个既有 hints**。最新五项仪器用例的 class 时间晚于源码编辑，确认包含新增静态消息无光标回归；五项仍未执行设备测试。`git diff` 确认 DshNativePage.kt 仅 StreamingCard 一行参数变化。
- 未验证：Android 仪器执行、设备安装、真实 DSH/模型/账号、真机、深浅色/大字体/TalkBack、滚动帧性能与完整 JVM。所有已测数字均为本轮 Host 结果，不沿用历史数字。

### 未完成与暂停项

原候选 e49f9c0 已推送并创建 PR #97，author/committer 均为 leezzou；原 SHA 的两项 Host CI 已通过，两项 Android CI 在接线开始时仍运行，不能当作接线后 head 证据。流式唯一入口的最小范围扩展已获编排明确批准，已完成单行接线和静态消息无光标回归源码；集成后 compile/72项定向 JVM/lint 已通过，追加提交更新同一 PR 并解除草稿。无未完成的产品实现项；设备验证和独立审查仍为未验证。独立审查/Android QA 待安排；API Key/真实模型/真机继续暂停。

### 下一步

1. 收集集成后 Host 结果，提交推送单行接线/静态消息回归/本记录更新，更新同一 PR #97 描述并解除草稿，不合并；最新 SHA 和 CI 以 provider/AO 交付回报为准。
2. 在最终 SHA 上安排独立 Review 和 Android QA；QA 执行 `DshChatMotionPresentationTest` 五项并复核深浅色、大字体、滚动与快速打断。未启动独立审查，不沿用旧 SHA 的验证或批准。

### 约束与风险

不做逐字动画、不对整卡 animateContentSize、不动画实时批量结果高度、不延迟 enabled/权限生效。设备留给独立 QA，Host 结果不冒充 Android 执行。长 diff 须限高与虚拟化。当前无产品代码失败验证结论。

### 产物与校验

未生成发布 APK/源码交付附件，未覆盖安装、未合并发布。保留动效审计作为后续批次依据；本范围尚无确认过期可删记录，不做跨任务清理。普通 PR 不刷新共享 AGENT-STATE/CURRENT 印章。

<a id="20261111-2043-motion-batch-d"></a>

## 20261111-2043-motion-batch-d

- Agent/会话标识：android_agent-92（AO Worker）
- 时间：2026-11-11 20:43 CST 开始

### 当前状态

- `git ls-remote origin refs/heads/main` 实核为 `37358849742b2ad3e2b24bc539395ec20801f413`；初始 HEAD 相同、工作区干净，现分支 `ao/motion/batch-d`。
- 建分支最初因共享 Git index.lock 写权限受阻；workspace-write 原命令重试无效。编排者明确授权对 Git 写操作申请一次 danger-full-access 后，原建分支命令成功。没有更换 worktree、更改共享 Git 配置/远端、改写共享历史或强推。
- 已实现：复用 `TanukiDesign.motion.list()`；会话/文件稳定 key 行动效、搜索/目录数据集隔离、聊天文本和新增工具组仅 alpha 入场。工具组只透传 Modifier，所有历史行 placement/fadeOut 为 null。
- 聊天 timeline/分组段以及锚点/分页/滚动副作用与 HEAD 逐段精确比对，完全相同；没有改分组边界保存、renderKeys、restore 探针、滚动或协议。
- 最终验证：工具组 Modifier 参数顺序修正后，完整命令 BUILD SUCCESSFUL（3m34s）/退出0；产品/AndroidTest/JVM源码 compile通过，XML8类88项、0失败/错误/跳过；lint 0 errors/105 warnings/2 hints，新增 ModifierParameter 告警已消失。早期71项和接线前88项只保留为阶段证据。
- 无外部决策阻塞。目标 `com.tanuki.modern.workspacecheck`；仅 Host 编译，不连接或操作设备，不生成设备/发布 APK。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| app/src/main/kotlin/com/tanuki/ui/TanukiListMotion.kt | 稳定数据集身份、文件异步搜索结果边界；聊天新增行资格快照、序号水位和占位/流式替换抑制；不保存正文 |
| app/src/main/kotlin/com/tanuki/ui/DshSessionManagerPage.kt | session id 行 animateItem；搜索/筛选/主机切换重建行动画宿主，滚动状态保存在宿主外；分组标题静态 |
| app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt | 文件列表和网格共用 S-list；path key 和按目录保存的滚动状态不变；来源/目录/筛选结果切换隔离旧动画 |
| app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt | 独立入场资格快照；向原文本根 Column 和工具组根 Surface 传 Modifier；历史与实时段均禁 placement/退出补间 |
| app/src/main/kotlin/com/tanuki/ui/DshToolCard.kt | 获准的最小扩展：DshToolGroupCard 新增默认 modifier 参数并传至原 Surface；不改结构/展开/渲染内容 |
| app/src/test/kotlin/com/tanuki/ui/TanukiListMotionTest.kt | 5项：实际文件排序/会话置顶归档、真实spec时间采样、异步查询/清空、来源/目录/隐藏筛选、reduced |
| app/src/test/kotlin/com/tanuki/ui/DshMessageMotionTest.kt | 8项：首屏/加载、头插/释放/并发追加、空会话、占位/流式替换、占位信号晚清除、分组/锚点变化、reduced |
| docs/DESIGN-TOKENS.md、docs/TEST-REPORT.md、本记录 | 行为契约、场景矩阵、实际验证与未覆盖范围；不更新共享状态/印章 |

### 场景矩阵与关键决策

| 场景 | 行为 | 边界 |
|---|---|---|
| 会话置顶/更新重排/归档/增删 | 240ms standard placement；160ms enter / 140ms exit | 默认同来源数据集，稳定 session id；分组标题不入场 |
| 文件同目录排序/增删（列表与网格） | 同上，复用 S-list | path key 不变；不做整列表尺寸补间 |
| 搜索/筛选/换目录/换来源/换主机 | 立即替换，无逐项位移或级联 | 重建 Lazy 动效宿主，沿用外部滚动状态；查询/会话过滤活动期间行 spec 关闭 |
| 文件搜索异步提交/清空查询 | 请求与已显示筛选结果均关闭行动画；结果提交才换宿主身份 | 排序请求不换身份；保留旧筛选结果时不提前恢复 placement |
| 聊天首屏/恢复/分页头插/窗口释放 | placement/fadeIn/fadeOut 全 null | revision 变化连同同帧追加均立即显示；序号水位防旧历史重读入场 |
| 聊天新追加已提交文本/新工具组 | 仅一次160ms alpha，placement/fadeOut null | 新行可能在历史段或实时段，不能只按段名判断；工具组追加成员不重播 |
| 流式 delta/发送占位及流式落盘替换 | 不重播入场 | 占位先撤下、落盘稍后到也保留一次抑制；占位信号后清除不会反复建立抑制 |
| reduced-motion | 所有列表 spec 全 null/立即 | 使用既有共享设置，不修改用户系统设置 |

- 锁定依赖 [Compose foundation-android 1.10.3 源码](https://dl.google.com/dl/android/maven2/androidx/compose/foundation/foundation-android/1.10.3/foundation-android-1.10.3-sources.jar)核对：LazyLayoutItemAnimator 的 DisplayingDisappearingItemsNode.onDetach 调用 animator.reset。重建宿主可清空上个数据集的 animator，同时继续使用原 LazyListState/LazyGridState；不调用内部 API、不反射。
- 展示快照只保留当前数据窗口的入场资格 key、最高序号和布尔标记；普通重组保留 spec，不提前截断淡入。控制器、消息分组、渲染树、锚点与滚动计算不变。

### 验证记录

环境：macOS ARM64、JDK17、SDK36、Compose1.10.3，隔离 worktree Gradle/Android 缓存；不使用设备。

最终命令：

```bash
source scripts/ao-worktree-env.sh
./gradlew --console=plain --max-workers=2 -PtestBuildType=debug \
  -I scripts/workspace-verification.init.gradle -x buildNativeRuntime --offline \
  :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin \
  :app:testModernDebugUnitTest \
  --tests 'com.tanuki.ui.TanukiListMotionTest' \
  --tests 'com.tanuki.ui.DshMessageMotionTest' \
  --tests 'com.tanuki.ui.design.TanukiMotionSpecsTest' \
  --tests 'com.tanuki.FileListPresentationTest' \
  --tests 'com.tanuki.DshChatWindowTest' \
  --tests 'com.tanuki.DshChatFollowStateTest' \
  --tests 'com.tanuki.DshChatRenderTest' \
  --tests 'com.tanuki.DshNativePagingTest' :app:lintModernDebug
```

- 第一轮未含分页类/lint，BUILD SUCCESSFUL（4m1s），产品/AndroidTest/JVM源码 compile 通过；XML7类71项（5/7/5/8/18/5/23），0失败/错误/跳过。
- 占位晚清除边界补强后按上文8类完整重跑：BUILD SUCCESSFUL（7m35s），compile/JVM/lint通过；XML8类88项（5/8/5/8/18/5/23/16），0失败/错误/跳过；lint 0 errors/105 warnings/2 hints。此前独立 lint 亦成功（5m2s）。这两轮均为工具组接线前阶段证据。
- 工具组初版接线后同套命令 BUILD SUCCESSFUL（5m55s），compile与88项定向回归通过；lint为0 errors/106 warnings/2 hints。新增1条 ModifierParameter 警告：Modifier不是第一个可选参数。已将其移到第一个可选参数位置并同步两个调用点，不改行为。修正后按上文完整命令最终重跑 BUILD SUCCESSFUL（3m34s）/退出0；产品/AndroidTest/JVM源码 compile通过，XML8类88项、0失败/错误/跳过，lint回到0 errors/105 warnings/2 hints，工具组新增告警消失。
- 阶段 `check-handoff.py --pr --record docs/handoffs/20261111-2043-motion-batch-d.md`、`check-publication.py --git`、`git diff --check` 通过，906 public files/902 index entries/0 findings；最终候选仍须重跑。
- 精确比对：DshNativePage 从 timeline 构建到分组结果段，以及 unseen/renderKeys 到 LazyColumn 之前的锚点/分页/滚动副作用段与 HEAD 完全相同。
- 本地编译仅 SDK XML版本、analytics目录不可写和既有弃用/API提示；未因这些提示扩大权限。本地编译或定向测试没有失败，CI合并候选失败见下一节。
- 本地未执行完整 JVM、assemble、Android 仪器、实际 Compose 列表挂载/快速打断、设备手势/帧率、真机或真实模型。历史测试不计本次通过；跳过原生运行时构建的 Host 产物不能安装作设备验收。

### PR #100 CI 合并候选跟进

- 首轮提交 `e2b6417944af478d54b3dc9ecb1f8d27f9b6456f` 已推送并创建PR，author/committer均为 leezzou；本地工作树与远端完整SHA核对一致。push远端成功，但本地remote-tracking ref更新被共享Git元数据权限拦截；未重复推送或改共享配置。
- 两项Host checks及push事件的Android build/JVM/lint job成功；pull_request事件的 Android job [130553927134](https://github.com/leezzou/tanuki/actions/runs/43029892008/job/130553927134)在 compileModernDebugKotlin 失败：合并候选 WorkspaceFilesPage 第11/64行重复导入 TanukiDesign。最新main已前进到 `fde9e396c6e2d43bf8f63d03d982c850e9369562`，该文件顶部新增同一import；本批在另一个位置新增的import被文本合并保留，导致语义重复。该失败不能计为CI通过，也不推翻分支独立编译的已测事实。
- 最小修复：移除本批的TanukiDesign import，仅本批列表工厂的一次访问使用限定名；不改行为，不把最新main其他产品代码带入本分支，保持用户指定373基线。
- 用373原始文件、修正后的本分支文件和fde9完整文件执行 `git merge-file -p` 三方预检：干净合并，合并结果TanukiDesign import恰好1条，列表工厂限定名调用保留。此为文本合并证据，不能冒充合并候选Android编译；真正结果以新head CI为准。
- 修复后按上文完整命令刷新：BUILD SUCCESSFUL（2m47s）/退出0，产品/测试源码compile通过；XML8类88项、0失败/错误/跳过，lint 0 errors/105 warnings/2 hints。正常追加修复提交并推送原PR，不改写历史，不创建替代PR；新head合并候选CI结果以PR Checks为准。已通知编排者暂缓旧head独立审查，后续审查绑定新head。

### 未完成与暂停项

- 本轮按授权统一提交推送并创建源码PR，最终SHA/PR和CI以provider事实与回执为准；当前head CI须跟进，失败时修复并刷新验证。
- 独立 Codex High 审查与独立 Android QA 尚未安排/执行，不以 Host 结果替代；由编排者协调。
- API Key 保存、真实模型/账号与真机验收继续暂停；不合并、不发布。

### 下一步

1. 本分支以明确文件清单、作者与committer均为 leezzou 统一commit/push，创建指定标题PR；不合并、不发布。
2. 跟进当前head适用CI；有失败则定位修复并重新验证。独立审查绑定最新SHA，请编排者决定是否启动AO原生Codex High审查。
3. 独立QA在独占设备上验证场景矩阵、快速搜索/目录切换、分页视口与reduced动态切换；不将Host结果替代设备证据。

### 约束与风险

- 仅合成测试数据，不读凭据、真实会话或备份；不连接或操作设备。
- Git 写操作仅按编排者授权对具体命令申请权限；不更改 DSH 策略、共享Git配置/远端、默认分支或共享状态印章。
- 工具组透传范围已明确获准；不改消息结构、展开逻辑、渲染内容、分组边界或滚动锚定。
- Host策略/时间采样不等于 Android 帧行为；设备QA仍需覆盖快速搜索/清空、目录切换、排序/归档、分页阅读、reduced动态切换和深浅色/大字体。

### 产物与校验

- 本轮交付为源码PR，不生成/分发APK、源码ZIP或发布附件；无覆盖安装、签名、合并、tag或Release。
- 提交/推送前过期交接核对：无确认可删的本范围旧记录。动效审计仍为其他批次依据，聊天改版/分页和A/B批仍有独有验证与未验事项，保留；不扩大跨任务清理。
- 临时源码候选通过 package-source.py、公开归档检查与外部 SOURCE-SHA256SUMS 复核（906文件，无原生附件，非设备/发布包）；文档收尾后在系统临时目录重建最终候选，不分发候选ZIP、不以旧ZIP对应本次改动。共享状态与印章由集成维护方刷新。
- 最终提交SHA、PR URL和CI以provider事实和最终回执为准，不为回填元数据再拆提交。

<a id="20261111-2200-progress-layout-test"></a>

## 20261111-2200-progress-layout-test

- Agent/会话标识：android_agent-97
- 时间：2026-11-11，UTC+08:00

### 当前状态

- 基线远端 main 与 provider API 实核为 `13f1b235207abed24e6a662bc67e71fff369c930`。
- 分支 `ao/progress-test/layout-contract`；原测试修复提交 `2f0d8667eee74879d1140d426ed244f91c232718`；[PR #102](https://github.com/leezzou/tanuki/pull/102)。CI 跟进前已通过 AO 认领并核对抓取的 PR SHA 与本地 HEAD 一致，工作树干净。
- 已实现：以当前 WorkspaceFilesPage/TanukiUi 的真实结构替代过期语法要求。已验证：定向与完整 ModernDebug JVM；待验证：补交接后的新 head CI。
- 未生成、安装或分发 APK；目标验证为 Host JVM，不表示 Android 设备验收。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/test/kotlin/com/tanuki/OperationProgressLayoutTest.kt` | 断言外层统一避让导航；文件列表、内部查看器、外部文档路径各一个宿主；runtime overlay 优先；查看器传入实测操作栏高度；退出摘要缓存、入口交互和详情抽屉互斥。 |
| `docs/handoffs/20261111-2200-progress-layout-test.md` | 按 CI 反馈补本次唯一交接记录，保留实际测试证据与范围。 |

### 验证记录

首次修复在 JDK17、SDK36、独立 Gradle/Android 缓存环境下，先执行 `source scripts/ao-worktree-env.sh`，再依次执行：

```bash
./gradlew --console=plain --max-workers=2 -PtestBuildType=debug \
  -I scripts/workspace-verification.init.gradle -x buildNativeRuntime --offline \
  :app:testModernDebugUnitTest --tests com.tanuki.OperationProgressLayoutTest
./gradlew --console=plain --max-workers=2 -PtestBuildType=debug \
  -I scripts/workspace-verification.init.gradle -x buildNativeRuntime --offline \
  :app:testModernDebugUnitTest
```

- 定向 BUILD SUCCESSFUL，退出 0，2m58s；JUnit XML 为 6 tests / 0 failures / 0 errors / 0 skipped。
- 完整 JVM BUILD SUCCESSFUL，退出 0，35s；测试任务实际执行，XML 汇总 1380 tests / 140 结果文件 / 0 failures / 0 errors / 0 skipped。
- 首次 `git diff --check` 与 `python3 scripts/check-publication.py --git` 通过，公开检查 0 findings。
- 首次单文件范围执行 `python3 scripts/check-handoff.py --pr --changed-file app/src/test/kotlin/com/tanuki/OperationProgressLayoutTest.kt` 退出 1，原因是缺本次交接记录。随后 [PR Host CI](https://github.com/leezzou/tanuki/actions/runs/43033831917/job/130567387492) 在同一条件失败；本次按用户 CI 修复要求补齐记录。
- 本次文档跟进：`python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-2200-progress-layout-test.md` 在显式传入本 PR 测试与交接两个 `--changed-file` 后通过；不指定 `--record`、仅传同一两文件清单也通过。`python3 scripts/check-publication.py --git` 为 0 findings，`git diff --check` 通过。上述 JVM 是原测试提交的实际结果，本次不改可执行代码，不冒充重跑。
- 未执行 lint、Android 仪器、设备操作、真实 PRoot/DSH/模型；跳过原生运行时构建的 Host 检查不作为设备包证据。

### 未完成与暂停项

- 补交接后的当前 head provider CI、独立审查待核对；未启动 AO review，需用户或编排者同意。
- API Key 保存、真实模型/账号、真机验收继续暂停。
- 无产品修复或其他任务包含在本次 CI 跟进中。

### 下一步

1. 验证本次记录与实际 PR 两文件变更集，提交并推送至现有 PR #102，不创建替代 PR。
2. 核对新 head 的 Host 与 Android CI；若失败继续定位。未经明确授权不合并或发布。

### 约束与风险

源码文本契约只能保护宿主接线与条件，不能替代 Compose 动画帧序列、设备布局或触控验收。首次单文件限制与强制交接门禁冲突已如实报告；此次用户明确要求修复缺记录的 CI，追加最小必要文档，不放宽门禁。Git 写操作因 linked worktree 元数据位于工作区外而被拒后，使用已获授权的单次升级重试；未改共享 Git 配置或强推历史。author/committer 均使用 leezzou。

### 产物与校验

交付为 PR 源码与本记录，无 APK、源码 ZIP、Release 或设备数据变动，不覆盖安装。临时测试日志与 PR 描述不提交。普通 PR 不刷新 AGENT-STATE/CURRENT 印章。

提交前检查相关旧记录：动效 F 批记录仍有独有验证、设备未验证事项和实现决策，动效审计仍供其他批次使用，均保留；本次没有确认可删除且属于本任务范围的过期记录，不开展跨任务清理。

## 最终16c07591b rebuild的补充迁移

以下两份为前轮未合并发布候选（PR #105的0910cc1）及已合入16c07591b的#104测试修复。前轮候选已由本次rebuild替代，不将历史包/测试/待提交描述当当前事实；完整原步骤和未验边界保留。原prepare普通构建、续封装及签名修正的失败分别保留于TEST-REPORT。

<a id="20261111-2213-release-0.1.14"></a>

## 20261111-2213-release-0.1.14

## 交接：0.1.14 最终发布候选

- Agent/会话标识：android_agent-98
- 时间：2026-11-11，准备阶段22:13 CST开始，收到REBUILD后完成最终候选
- 状态：最终main候选已构建实核，统一一次commit/push与PR收尾；实际PR/head以provider回执为准
- 前一记录：[测试现状](TEST-REPORT.md)

### 用户目标与范围

0.1.14/code19/workspacecheck。先准备、同步六份版本文档和迁移清理交接；收到编排REBUILD后fetch并merge最终main、核对版本、清理经确认的旧本版目录、执行指定准备脚本、实核APK/六附件，更新最终事实，一次commit/push并创建标题release: 0.1.14的PR。不要合并、打tag或上传，最后三步由编排者处理。

### 当前状态

最终基线`efc1d9ae4e5d62e6492807d965697a1b863a7511`，包含视觉P2 #103和动效C批 #98。原准备基线为3ba8ef1f；通过git merge --autostash快进合入final main，无冲突恢复准备改动。默认版本保持0.1.14/code19。完整 JVM **1407 tests / 144 XML / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 117 warnings / 2 hints**。

APK路径`artifacts/releases/0.1.14/Tanuki-0.1.14-workspacecheck-arm64-release.apk`；实核com.tanuki.modern.workspacecheck、0.1.14/code19、minSdk26/target34、ARM64、non-debuggable。APK **13,093,605 字节**，SHA-256 `87bc0f8b9701eda85afdc384fdafd4953bce8ce334ee1b1fd1d40e42939c1f8f`；证书 SHA-256 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。 六附件已生成并通过清单校验；最终文档后刷新源码ZIP和外部校验，APK字节保持不变。未安装、未合并/tag/上传。

### 本次改动

- app/build.gradle.kts：默认版本0.1.14/code19，保留final main的测试依赖。
- OperationProgressLayoutTest.kt：仅同步一条因C批重构过时的源码契约至TransferSheet常驻宿主visible互斥；其余断言不变，产品逻辑未改。
- README、AGENT-STATE、TEST-REPORT、RELEASE、ARTIFACTS、APP-UPDATES：同步最终main、APK实际身份/字节/SHA、1407项与lint及未验范围。
- DEVELOPMENT-HISTORY和5份专题迁入75份中间交接，修复相关Markdown引用。70份历史正文规范化比对保留；两份设备QA、C批/P2独有记录、本次/模板/印章共7文件。CURRENT.json由--integrate生成，不能手写合并。

### 验证记录

1. 实际git fetch origin，远端为最终完整SHA；git merge --autostash origin/main快进并自动恢复，diff无冲突。版本行实核19/0.1.14。
2. 最终源码运行：source scripts/ao-worktree-env.sh后`./gradlew --console=plain --max-workers=2 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest :app:lintModernDebug`，无过滤。
3. 首轮退出1（2m08s），1407项仅布局类第84行失败，旧if语法已由C批常驻TransferSheet visible替代。人工核对入口/退出缓存/交互禁用保留，只同步该断言；定向6项通过（30s），最终同命令全套退出0（2m17s）：完整 JVM **1407 tests / 144 XML / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 117 warnings / 2 hints**。 XML独立汇总，未把首轮失败算通过。
4. 原准备阶段1393项通过只属3ba8ef1f；曾有官方idle等待5000ms超时和NPM退休计数8→9失败，各自单类及最终全套复跑通过，细节保留TEST-REPORT；不能推断时序问题不存在。
5. 核对本版目录真实绝对路径属于当前worktree后，确认旧目录不存在。普通命令`python3 scripts/prepare-app-release.py --version 0.1.14 --version-code 19 --channel workspacecheck`的release构建成功（2m56s，含native）；原生封装因环境加载清理CLAUDE.md链接退出64。在最后加载环境后核对固定commit及链接目标并恢复，再以verified-apk模式续封装。独立核验随后发现worktree新debug证书1141466e不符，未交付该包；核对既有开发证书371dc301并用apksigner重新签名同一构建载荷（v2/v3通过），以修正APK完整SHA再次封装六附件，最终全部通过。失败与原因详见TEST-REPORT；不重编产品。
6. 独立aapt2 dump badging和apksigner verify核验APK；ZIP检查仅ARM64并含libproot/libproot-loader/libsupervisor；app-update.json全部字段与APK实值一致。APK **13,093,605 字节**，SHA-256 `87bc0f8b9701eda85afdc384fdafd4953bce8ce334ee1b1fd1d40e42939c1f8f`；证书 SHA-256 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。
7. RELEASE-SHA256SUMS、native-source-SHA256SUMS、SOURCE-SHA256SUMS通过shasum -a 256 -c；六附件（含RELEASE清单自身）的实际SHA在最终回执列出，不写源码ZIP自身SHA进入包内文档形成循环。文档收尾后重新生成并扫描源码归档、刷新严格印章及外部清单。
8. 准备阶段check-native-parity.py为166行/14域；70份历史正文与原Git内容规范化比对通过；--pr/--integrate/--release、公开工作树/索引与源码归档检查执行并按最终内容复核。没有把历史检查当Android通过。

### 未完成与暂停项

- 当前完整PR SHA的独立审查/CI由编排者安排；不自行触发review。本地候选已完成，统一一次commit/push和指定标题PR交付，实际编号/head由provider回执记录，不为回填再拆提交。
- 未执行：Android仪器、设备安装/升级覆盖、真实DSH/模型/审批/附件/提问/sshd/中继/弱网、真机/低API、视觉与帧率，本版未验证。
- 两份设备QA继续保留原SHA/APK；QA-04/07/09/10/12的修复需新包复验。视觉P2代码已合并但未新包复验；VQ-01真实提问入口/RPC不在范围。C批记录中的键盘焦点/回调校验及文件页外层进度宿主P2仍保留。
- API Key保存、真实模型/账号、真机验收继续暂停。不合并/tag/创建或上传Release。

### 下一步

1. 完成最终文档、严格印章和源码ZIP/外部散列刷新；核对暂存明确路径含75份删除、6份迁移专题、最终记录和两项app文件，不提交生成物/本机日志。
2. author/committer均用leezzou <115924933+leezzou@users.noreply.github.com>，统一一次commit/push，创建release: 0.1.14 PR，不force push、不直推main。
3. 回报PR号、完整head、APK路径/字节/SHA、六附件SHA及JVM/lint；编排者自行完成最后合并/tag/上传。上传全部六附件，workspacecheck为Pre-release。
4. 如需后续产品修复，重新验证并重建，不能沿用旧SHA的批准或设备结果；APK已核字节无变化时只刷新源码副本与校验。

### 约束与风险

不访问设备/真实用户数据或凭据；不关闭审批/TLS/哈希校验。Git共享元数据位于worktree外，fetch/merge的原命令因权限被拒后一次性更广权限重试成功；没有修改全局配置或重写共享历史。原生缓存遗漏CLAUDE.md链接，按固定commit b27d81a68d765f3462e807f4a14e01a03f8e89ea核对120000模式和AGENTS.md目标后恢复，仅修复构建缓存，不改第三方源码。

删除旧交接前逐个核对真实目标与迁移正文；迁移脚本首轮路径键断言失败时没有删除文件，修正后完成迁移。打包前后文档变化必须重打源码并刷新外部清单；不把ZIP自身散列写回归档，不重签已核验APK。

### 产物与校验

`artifacts/releases/0.1.14/`共六附件：APK、app-update.json、Tanuki-0.1.14-source.zip、Tanuki-native-corresponding-sources.tar.gz、native-source-SHA256SUMS、RELEASE-SHA256SUMS。APK路径/大小/SHA与证书见上文。生成物被Git忽略，交付完整目录，不从sources/builds临时区挑旧包。

旧版附件保持原样；没有覆盖安装、清数据、tag或Release。清理75份记录的理由为任务已合并/专题接管；独有实现/验证/失败/决策/设备待验步骤先归入6份专题，保留两份原始QA和C批/P2证据，不依赖Git历史或.build才能接手。最终handoffs为7文件，满足不超过10。


<a id="20261111-2250-sheet-host-assert"></a>

## 20261111-2250-sheet-host-assert

## 交接：常驻 sheet 宿主断言同步

- Agent/会话标识：android_agent-99（AO Worker）
- 时间：2026-11-11 22:50 CST
- 状态：实现与本地验证完成，提交推送及 PR 交付中
- 前一记录：[C 批弹层动效](handoffs/20261111-1900-motion-batch-c.md)

### 用户目标与范围

修复 main 上 OperationProgressLayoutTest 对旧外层 if 挂载写法的断言。仅修改该测试类及本次唯一交接；不修改产品代码，不合并、不发布。基于 origin/main=efc1d9ae 建分支，编译和相关测试通过后 push 开 PR。

### 当前状态

远端 git ls-remote 与起始 HEAD 均为 efc1d9ae4e5d62e6492807d965697a1b863a7511，起始工作区干净。分支 ao/sheet-assertions/sheet-host。目标为 Host 源码契约回归，不操作 Android 设备或安装包。

### 本次改动

| 文件 | 改动与目的 |
|---|---|
| app/src/test/kotlin/com/tanuki/OperationProgressLayoutTest.kt | 传输抽屉单一常驻宿主、visible 互斥、函数直属层级禁止提前卸载；全局失败详情单一宿主及可空模型接线 |
| docs/handoffs/20261111-2250-sheet-host-assert.md | 唯一交接与验证边界 |

产品事实见 WorkspaceFilesPage.kt 的 FileTransferOverlay：TransferSheet 常驻，visible = sheet && runtimeOverlay == null；运行时浮层出现后关闭 sheet。TanukiUi.kt 的 FailureDetailSheet 常驻并接收 shownFailure 可空模型。旧外层条件不再是正确契约。

### 验证记录

- 已执行 git status、git rev-parse HEAD、git ls-remote origin refs/heads/main：基线一致，初始无改动。
- git switch 首次因共享 Git 元数据写入被沙箱拒绝；核对分支、HEAD 与状态未变化后，按用户明确授权原命令 danger-full-access 一次性重试成功。
- git diff --check：通过。
- `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-2250-sheet-host-assert.md`：通过；`python3 scripts/check-publication.py --git`：921 public files / 920 index entries / 0 findings。
- 首轮带 `--offline` 的编译/定向 JVM 命令在 checkModernDebugAarMetadata 失败（34s，退出1）：缓存缺少 androidx.compose.ui:ui-test-manifest:1.10.3；尚未运行测试，不记为测试通过。去掉 --offline 补齐相同版本依赖并重跑，未修改构建配置。
- 联网重跑 **BUILD SUCCESSFUL（3m19s），退出0**；产品、Android 测试与 JVM 测试源码编译通过。XML 实核 **8类61项，0 failures / 0 errors / 0 skipped**：OperationProgressLayout 7、OperationProgressPresentation 15、OperationProgress 7、FileTransferProgress 5、FileViewerLayout 11、FileViewerMotion 5、TanukiOverlayMotion 6、TanukiMotionSpecs 5。
- 实际定向命令：`source scripts/ao-worktree-env.sh` 后，`./gradlew --console=plain --max-workers=2 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest --tests 'com.tanuki.OperationProgressLayoutTest' --tests 'com.tanuki.OperationProgressPresentationTest' --tests 'com.tanuki.OperationProgressTest' --tests 'com.tanuki.FileTransferProgressTest' --tests 'com.tanuki.FileViewerLayoutTest' --tests 'com.tanuki.FileViewerMotionTest' --tests 'com.tanuki.ui.design.TanukiOverlayMotionTest' --tests 'com.tanuki.ui.design.TanukiMotionSpecsTest'`。
- 为确认 main 单点失败消除，完整 JVM 命令：同环境下 `./gradlew --console=plain --max-workers=2 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime --offline :app:testModernDebugUnitTest`，**BUILD SUCCESSFUL（30s），退出0**。XML 实核 **144类1408项，0 failures / 0 errors / 0 skipped**；OperationProgressLayoutTest 7项全通过（新增1项全局失败详情宿主回归）。本段及61项均为本轮实测，不引用历史数字。
- `git diff --exit-code -- app/src/main docs/AGENT-STATE.md docs/handoffs/CURRENT.json`：通过，无产品源码或共享状态改动。
- 未执行 lint、Android 仪器、设备/UI、真实运行时及模型。本轮仅宿主编译和 JVM，不生成可安装验收包；SDK XML、analytics 不可写及既有弃用警告没有变更或抑制。

### 未完成与暂停项

- Host 验证已完成；待提交推送和创建 PR，PR 号与最终 head 在提供方及 AO 完成回报记录，不为回填再推送。CI 与独立审查以最终 head 为准，独立审查待编排批准启动。
- 不执行设备、仪器、真实模型/账号验收；API Key 保存和真机暂停项保留。

### 下一步

1. 本地编译与 JVM 已完成；完成普通 PR 交接与公开候选检查后统一提交推送并开 PR。
2. 核对最终 head CI，交编排者安排独立审查与适用门禁；不合并、不发布。

### 约束与风险

仅更新测试契约，不修改产品代码；大括号层级检查针对现有源码布局，不替代 Compose 实际挂载和退出动画设备验收。不读取用户数据、凭据，不启动或安装设备。宿主结果不作为 Android 执行证据。

### 产物与校验

无 APK、ZIP、发布附件交付；产物为测试修复 PR。普通 PR 不刷新 AGENT-STATE.md/CURRENT.json。过期记录检查限本次范围，没有确认可删除的独有旧记录，保留既有验证和未完成项，不作跨任务清理。
