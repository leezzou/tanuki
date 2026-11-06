# 交接：20261106-1357-android-qa-readiness-audit

- Agent/会话标识：android_agent-8（DSH QA 能力核查 Worker，DeepSeek V4.1 Flash）
- 时间：2026-11-06 13:57 CST（UTC 05:57）；实施与验证完成于同日
- 状态：已完成（只读核查 + 设备验收门禁实现；未做 Android 设备验收）
- 前一记录：[20261106-1207-ci-rpc-contract-tests.md](20261106-1207-ci-rpc-contract-tests.md)

## 用户目标与范围

- 用户本次实际要求：先只读核查现有 Android 验收能力；随后批准把核查记录保存为本记录、同步 `AGENT-STATE`/`CURRENT.json`，并为设备验收补上 APK 哈希/签名/版本/ABI/WebView 门禁与设备忙碌预检。
- 本次包含：最新 main 的规范/状态/上手指南/TEST-REPORT/SECURITY/AO-WORKTREE-BUILD/设备脚本与仪器测试静态核对；公开 GitHub CI 只读核对；AVD/ADB 普通 metadata 只读查询；新增 `scripts/device-preflight.py`；`scripts/verify-workspace.sh` 接入 pre/post 门禁；宿主 mock 回归；文档与交接同步。
- 本次不包含：产品 Kotlin/原生代码改动；构建 APK；安装/卸载/清数据；运行仪器；启动/停止 App 或模拟器；模型/账号验收；改 AO 配置；合并或发布。
- 用户暂停/保留：API Key 保存；真实模型/账号验收；真机验收。真实模型只可在已有安全测试配置下执行，不索取/读取 key。

## 当前状态

- 已实现：
  - `scripts/device-preflight.py`：只读门禁，校验 APK SHA-256、包名、versionCode/versionName、应用/测试签名一致、arm64-v8a、`libproot.so`/`libproot-loader.so`/`libsupervisor.so`、设备 API/ABI、WebView 最低主版本（默认 123）、apt/dpkg/Node/PRoot/supervisor 进程、活动安装会话；`--phase post` 确认 `install -r` 后实际生效版本；证据写私有 `.build/logs/device-preflight-*.json`。
  - `scripts/verify-workspace.sh`：设备路径在 install 前执行 pre 门禁、install 后执行 post 版本确认；新增 `--expect-apk-sha256`、`--expect-cert-sha256`、`--min-webview-major`；失败即中止，不安装。
  - `scripts/test-agent-workflow.py`：mock ADB/apkanalyzer/apksigner 覆盖新门禁，含失败场景与证据 pin。
- 已验证（本轮实际执行）：
  - `python3 scripts/test-agent-workflow.py` → **14 tests OK（54.390s）**。
  - `bash -n scripts/verify-workspace.sh`、`python3 scripts/device-preflight.py --help` → 通过。
- 未验证：真实 APK/设备的 pre/post 门禁、构建、安装、仪器、0.1.8 设备证据。
- 当前目标包名/分支：`com.tanuki.modern.workspacecheck`；分支 `audit/android-qa-readiness`，基线 `main=a9aa507c06883c62048a89924384ef0a10e01470`；无运行中构建/测试任务。
- 关键事实与定位：
  - 设备验收：`scripts/verify-workspace.sh`；新门禁：`scripts/device-preflight.py`。
  - 发布 APK 身份校验：`scripts/prepare-app-release.py`（apkanalyzer + apksigner）；签名：`scripts/sign-release.sh`。
  - 默认 debug 版本：`app/build.gradle.kts:16-17`（默认 `0.1.7/code9`）。
  - CI：`.github/workflows/verify.yml`、`docs/CI.md`。
  - 环境测试：`RuntimeFunctionalTest`、`UbuntuMinimalTest`、`DshInstallationRegressionTest`、`InstalledToolsSmokeTest`、`PhoneFileGuestClientTest`。
  - WebSocket 仅注释：`app/src/main/kotlin/com/tanuki/web/DshWebView.kt:101`。

### 设备与 CI 只读证据（2026-11-06）

- main：本地 HEAD、`origin/main`、GitHub `main` 引用均为 `a9aa507c06883c62048a89924384ef0a10e01470`；PR #12 已合并。
- 公开 CI：[run 42163963031](https://github.com/leezzou/tanuki/actions/runs/42163963031)，`headSha=a9aa507…`，Host 10s、Android 7m56s，双绿。Android job 使用 `-x buildNativeRuntime`，仓库跟踪 `app/src/main/jniLibs` 为 0 个文件、目录不存在，CI APK 无原生 `.so`，不能用于设备验收。
- AVD：`Pixel_7_API_34_arm64`（运行中）、`Tanuki_012_Test_API34`（未启动）。
- 设备：`emulator-5572` = `Pixel_7_API_34_arm64`；API34/Android14/arm64-v8a；WebView `com.android.webview 159.0.8133.0`；已装 `com.tanuki.modern.workspacecheck 0.1.7/code9`（min26/target34，lastUpdate 2026-11-05 17:35:33），test 包存在。
- 忙碌检查：`ps -A -o NAME` 过滤未见 node/proot/apt/dpkg/supervisor；`pm list staged-sessions` 在 API34 可用且为空；`dumpsys package` 有 `Active install sessions:` 段可作回退。
- 数据边界：未读取 App 私有文件、userdata、Key、Cookie、token；因此“数据可测”未独立复核。安装/仪器前仍须由设备所有者确认无真实账号/项目数据，禁止 wipe/清数据。

### 能力矩阵（静态核对，不等于验收通过）

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

### 模拟器独占评估

- 现状：**流程约定**，不是机器锁。`AGENTS.md`/`AGENT-QUICKSTART.md` 要求复用同一 AVD、显式 serial、确认无安装/apt/真实任务；`verify-workspace.sh` 要求 `--confirm-test-data` + `--device`。未发现 `flock`/lockfile/lease；`emulator` 单实例只阻止同一 AVD 二次启动，不阻止两个 ADB 客户端并发 install/instrument。
- 适用范围：同一主机/同一登录用户、所有 AO worktree/会话；不覆盖跨主机、手工 adb 或其他工具。
- 最小方案（未实施）：以 serial 为键在共享临时目录用 `flock` 建租约（记录 `AO_SESSION_ID`、超时、退出释放），包裹 install/instrument；QA 与实现由 Orchestrator 串行调度。

### 历史执行 vs 本轮新核查 vs 未打通

- 历史已执行（`docs/TEST-REPORT.md`，未复跑）：0.1.7 release 52 项设备用例（含 `ApplicationUpdateNativeTest` 3 + `ApkInstallAccessTest` 5）；0.1.6 release 57 项；PR #8 22 项；多轮 debug 44/48/54 项与 JVM 104 项。
- 0.1.8：**没有设备测试报告**。
- 本轮新核查：main/CI 公开证据、AVD/ADB metadata、脚本/测试入口真实覆盖、独占机制、文档滞后。
- 尚未打通：APK 身份门禁的真实设备执行（本轮仅 host mock）、机器级设备租约、Phone Guest 自动化、WebSocket、进程死亡/后台重启、真实模型任务、0.1.8 设备证据。
- “Wave0”：仓库内无记录（`git log --all --grep=Wave` 与文档检索均无），不能把任何流程归为 Wave0。

### 文档滞后

- `docs/AGENT-STATE.md` 原写 main=`9e3e316`、PR #12 待复审；实际 PR #12 已合并为 `a9aa507`。本记录已同步。
- 仓库不存在任务提到的 `ORCHESTRATOR-RULES`、`QA-GATES`、`LESSONS`、`code-standards`；`INTERFACES.md` 存在（Browser 17 / Phone 11 方法）。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `scripts/device-preflight.py` | 新增设备验收只读门禁：APK 哈希/包名/版本/签名/ABI/原生库、设备 API/ABI/WebView、忙碌进程/活动安装会话、post 版本确认、私有证据 JSON |
| `scripts/verify-workspace.sh` | 设备路径 install 前 pre 门禁、install 后 post 版本确认；新增 `--expect-apk-sha256`、`--expect-cert-sha256`、`--min-webview-major`；失败不安装 |
| `scripts/test-agent-workflow.py` | mock ADB/apkanalyzer/apksigner 覆盖新门禁；新增签名/证书/降级/WebView/忙碌进程/安装会话/证据 pin/post 失败测试 |
| `docs/AGENT-QUICKSTART.md` | 设备验收章节说明 pre/post 门禁、pin 参数、证据路径；标注默认 14 类含会改环境的 `RuntimeFoundationToolsTest` |
| `docs/CI.md` | 说明 host 回归已用 mock 覆盖设备门禁，CI 不连接设备 |
| `docs/TEST-REPORT.md` | 新增本轮 host-only 门禁验证记录与未测范围 |
| `docs/handoffs/20261106-1357-android-qa-readiness-audit.md` | 本记录 |
| `docs/AGENT-STATE.md` | 当前交接指向本记录，同步 main/PR #12 状态、本轮实现与未测范围 |
| `docs/handoffs/CURRENT.json` | 由 `check-handoff.py --record` 生成，不手写 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `python3 scripts/test-agent-workflow.py` | **14 tests OK（54.390s）** | 宿主 mock；新增 2 个门禁测试方法，覆盖成功/失败/证据 |
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

## 未完成与暂停项

- 未完成：真实设备上的 pre/post 门禁执行；机器级设备租约；Phone Guest/WS/进程死亡/后台重启/模型任务自动化；0.1.8 设备证据。
- 外部阻塞：无。
- 用户暂停：API Key 保存；真实模型/账号验收；真机验收。

## 下一步

1. 在已确认的专用 AVD 上，用 `bash scripts/verify-workspace.sh --run-device-tests --confirm-test-data --device <serial>` 实际跑一次 pre/post 门禁与 14 类回归；先确认无真实数据、无 apt/dpkg/安装/真实任务。
2. 视需要增加机器级设备租约（serial + flock + `AO_SESSION_ID`），避免 QA/实现并发。
3. 为 WebSocket、Phone Guest、进程死亡/后台重启补 opt-in 用例；skip 必须记为未运行，不能计通过。
4. 0.1.8 设备证据另开验收任务，不能用历史数字或 CI APK 代替；真实模型/账号仍待用户恢复。

## 约束与风险

- 数据/设备/权限边界：未读私有文件、Key、Cookie、token；未安装/卸载/清数据/wipe/改设置；未启动或停止 App/模拟器。
- 关键决策及理由：门禁放在宿主脚本的 install 前后，只读 metadata；不把 CI 编译包、宿主机 mock 或历史设备数字当作 Android 验收。
- 失败尝试与避免重复踩坑：`pm list sessions` 在当前 Android 不可用，正确命令是 `pm list staged-sessions`；部分镜像仍可能返回非零，故保留 `dumpsys package` 的 Active install sessions 回退。`installd` 是系统常驻守护进程，不能据此判断在途安装。
- 风险：默认 debug 固定 `0.1.7/code9`；设备升级到 0.1.8/code10 后，默认 debug code9 覆盖安装会触发 `INSTALL_FAILED_VERSION_DOWNGRADE`，需传 `-PtanukiVersionCode`。默认 14 类含真实工具安装，不得与其他安装/apt 并行。
- 验证边界：本轮只做 host mock 回归；不能宣称设备门禁已在真实 APK/AVD 上通过。

## 产物与校验

- 新/旧 APK、源码 ZIP：**未更新**；未构建、未签名、未安装。
- 覆盖安装/签名范围/未发布事项：未安装、未签名、未发布；无 tag/Release。
- 交接清理：未删除旧记录；保留本记录、W1/W2/W3 记录、`TEMPLATE.md`、`CURRENT.json`。
- 本轮新增公开脚本会改变源码指纹；已用 `check-handoff.py --record` 刷新 `CURRENT.json`。源码 ZIP 待下一次发布/交付流程重打，本分支不进入 `artifacts/`。
