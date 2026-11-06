# Tanuki 当前状态

当前交接：[研发治理与合并前门禁](handoffs/20261106-1415-governance-gates.md)（W5 治理，执行会话 android_agent-7，记录内标识 W7；PR #14 待独立 Review）。W8 的 [Android QA 能力核查报告](handoffs/20261106-1357-android-qa-readiness-audit.md) 保留；其设备验收门禁实现已随 PR #13 合并。本轮由 android_agent-13 在独立 clone 中把 PR #14 rebase 到最新 main 并复验，不改产品代码、协议、AO 配置或 0.1.8 附件。

## 0.1.8 交付与仓库基线

- `main` 为 `14d2884d690f0264b14c5ada8391ad14f42f12f2`（PR #15 设备门禁测试补充合并提交）；`v0.1.8` tag 仍为 `c196bbf4d211604bc2076de450a8ed2d52ddde99`（PR #8 浏览器输入线程修复合并提交）。用户已发布 0.1.8；Agent 不代合 main、不创建 tag/Release。
- `artifacts/releases/0.1.8/` 六附件齐全。W1 只读复核 APK：`com.tanuki.modern.workspacecheck`、`versionName=0.1.8`、`versionCode=10`、minSdk26、targetSdk34、arm64-v8a；开发证书 SHA-256 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`；APK 10,479,850 字节，SHA-256 `eb695a12778652075e0324c65486534dab98848f073daa14ea631291b69b146e`。公开 Release 的 `app-update.json` 与 APK 均返回 HTTP 200。
- 0.1.8 已有独立 QA 会话 android_agent-10 的首轮设备冒烟：`emulator-5572` = `Pixel_7_API_34_arm64`（API34/ARM64，WebView `159.0.8133.0`）；`install -r` 从 0.1.7/code9 升级到 0.1.8/code10（约 1.57s，保留数据），设备 base.apk 与发布件 SHA-256 一致；冷启动 `LaunchState COLD`、`TotalTime 1616ms`；首启停在 Ubuntu 安装页 1/4，未执行安装；应用内浏览器加载 GitHub Release 页，WebView 正常；logcat 无 FATAL/ANR。
- 该冒烟只覆盖安装/启动/基础 UI/WebView；Ubuntu/Node/基础工具/DSH 安装、真实模型任务、底部导航点击、真机/MIUI/低 API 均未验证，不能写成 0.1.8 已验收。0.1.7 及更早的 104 JVM、54 设备方法等仍是历史记录，未在本轮复跑。
- main CI 以 PR #13 合并后的 Actions 结果为准；Android job 使用 `-x buildNativeRuntime` 且仓库不跟踪 `jniLibs`，CI APK 无原生 `.so`，不能用于设备验收。

## Phase 2 交付状态

### W1 文档门禁（已合并）

- PR #10 已合并为 `5b01ddf`：恢复 `AGENT-STATE`/`TEST-REPORT`/交接印章，修复 20 个断链，新增 `docs/INTERFACES.md`；历史测试逐节标注“历史”，不改产品代码。

### W2 AO worktree 构建环境（已合并）

- PR #11 已合并为 `9e3e316`。新增 `scripts/ao-worktree-env.sh`、`scripts/test-ao-worktree-env.sh`、`docs/AO-WORKTREE-BUILD.md`；per-worktree `GRADLE_USER_HOME`/`ANDROID_USER_HOME`、只读复用缓存。
- 已验证（W2 当时）：`compileModernDebugKotlin` 与 `testModernDebugUnitTest` BUILD SUCCESSFUL；`test-ao-worktree-env.sh` PASS。未跑完整 `verify-workspace.sh`、lint、设备/仪器、发布签名。

### W3 CI 与 RPC 契约（已合并）

- PR #12 已合并为 `a9aa507`。新增 `.github/workflows/verify.yml`、`scripts/check-rpc-contract.py`、Browser/Phone 客户端回归、`docs/CI.md`；`.github` 进入公开源码包。CI 无 secrets/设备/发布。
- 已验证（W3 当时）：Host/Android 双绿；本轮再次以公开 run 42163963031 核对为 success。

### W4 设备验收门禁（已合并，PR #13/#15）

- PR #13 已合并为 `b2a018d`。新增 `scripts/device-preflight.py`：APK SHA-256/包名/versionCode/versionName、应用与测试签名一致、arm64-v8a、`libproot.so`/`libproot-loader.so`/`libsupervisor.so`、设备 API/ABI、WebView ≥123（可配）、apt/dpkg/Node/PRoot/supervisor 进程、活动安装会话；`--phase post` 校验 install 后版本；证据写私有 `.build/logs/device-preflight-*.json`。
- `scripts/device-lock.py`：基于 `flock` 的 per-serial 进程锁；`verify-workspace.sh` 设备入口经锁重新执行自身，覆盖构建→pre/post 门禁→`install -r`→仪器全程；同 serial 第二任务退出 3，不同 serial 独立；锁 fd 传给子任务，父进程 SIGKILL 后仍由存活子任务持有。
- `scripts/verify-workspace.sh` 设备路径已接入 pre/post 门禁，并新增 `--expect-apk-sha256`、`--expect-cert-sha256`、`--min-webview-major` 参数；失败不安装。
- 已验证（PR #13/#15）：宿主 mock/并发回归 19 tests OK（76.760s，含复审 P1 修复：测试 APK 无版本字段/独立版本、空活动段不误报、API34 Active/Child Session）；`bash -n verify-workspace.sh`、`device-preflight.py --help`、`device-lock.py --help` 通过；未在真实 APK/设备上执行门禁、锁、构建、安装或仪器。PR #15 已合并为 `14d2884`，补充测试 APK 无版本字段、空活动段与 API34 会话前缀回归。

### W5 研发治理与合并前门禁（PR #14，待独立 Review）

- 规范：`AGENTS.md` 只保留通用入口/角色/合并授权，细节归入 [ORCHESTRATOR-RULES](ORCHESTRATOR-RULES.md)、[QA-GATES](QA-GATES.md)、[LESSONS](LESSONS.md)、[code-standards](code-standards.md)；[INTERFACES](INTERFACES.md) 现有契约未改。
- 门禁：只读 `scripts/check-development-gates.py`，接口 `--pr <ref> --session <AO worker id> [--qa-evidence <json>]`；通过 `gh` 读取 PR 身份/最新 head/全部 check，通过 `ao review ls` 读取绑定当前 head 的 review，通过 `ao project get` 核对独立 reviewer；空/排队/失败/跳过 checks、旧 SHA review、自评、跨仓库 PR、未知范围和缺失 QA 证据均拒绝。
- 回归：`scripts/test-development-gates.py` 24 项离线通过（假 `gh`/`ao`，无网络/设备）；已接入 `.github/workflows/verify.yml` 的 Host job，Android CI 边界未改。
- 角色：Orchestrator、Worker、Android QA、规范维护由 DSH/DeepSeek V4.1 Flash 承担；Reviewer 为 Codex/GPT High，只做独立 Review。当前 `ao project get android_agent` 的 orchestrator/worker 均为 `deepseek-harness`，模型 `opencode-go/deepseek-v4.1-flash`，与本轮实际运行一致。
- PR #14 已由 android_agent-13 rebase 到 `origin/main=14d2884` 后重新运行交接/门禁/公开检查/源码包；独立 Review、合并和发布未授权/未完成。最终 head 的 Host/Android CI 以 PR 页面为准。

## 已实现、已验证与待验证

- 已验证（PR #14 rebase 后本轮复跑）：`check-handoff.py --record ...` 刷新印章；`check-handoff.py` 当前；`test-development-gates.py` 24 项离线通过；`check-publication.py --git` 0 findings；`package-source.py` 生成源码包与校验清单（工作材料，不提交）。真实输出见 1415 交接记录。
- 已验证（#13/#15、W8 当时，历史）：宿主 mock/并发回归 19 tests OK（76.760s）；设备门禁脚本 `--help` 与 `bash -n` 通过；main/CI/设备 metadata 只读核对。
- 已验证（W5 前轮，历史）：24 项门禁离线回归；真实 `gh`/`ao` 只读 CLI 格式核对。
- 已验证（W2/W3/W1 前轮，历史）：worktree 构建引导、CI/RPC 契约、文档门禁恢复。
- 待验证：PR #14 独立 Codex High Review 与最终 head CI；真实设备上的锁、pre/post 门禁与 14 类回归；0.1.8 的 Ubuntu/Node/DSH 安装、真实模型任务与真机/MIUI/低 API；底部导航点击。
- 用户暂停：API Key 保存；真实模型/账号验收；真机验收。

## 环境与阻塞

- 当前分支 `chore/autonomous-development-governance`，基线 `origin/main=14d2884`；本轮在独立 `/tmp` clone 完成 rebase/验证，未使用共享 worktree 的 `.git` 写操作。
- 设备：本轮未启动/停止、未安装、未跑仪器；0.1.8 冒烟证据来自 android_agent-10 的独立 QA 会话。
- 无当前产品修复阻塞；PR #14 的独立 Review、最终 CI 与合并由用户和编排器推进，Agent 不自行合并/发布。

## 下一步

1. 等 PR #14 当前 head 的 Host/Android CI；由 Orchestrator 触发独立 Codex High Review，本会话不自行触发、不自评。
2. Review 意见逐条修复并重审；通过且用户明确授权后才合并，不代合、不直推 main。
3. 合并后在 main 复跑 `check-handoff.py`、`check-publication.py --git`、`test-development-gates.py` 与适用的 `verify-workspace.sh`。
4. PR #15（设备门禁测试补充）已合并；真实设备执行前确认无真实数据、无 apt/dpkg/安装/真实任务，不把 host mock 当设备验收。
5. 0.1.8 的 Ubuntu/Node/DSH/真实模型验证另开授权任务；API Key 保存与真实模型/账号验收继续等待用户恢复。
