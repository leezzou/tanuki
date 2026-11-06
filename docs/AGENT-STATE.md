# Tanuki 当前状态

当前交接：[Android QA 验收能力核查与门禁](handoffs/20261106-1357-android-qa-readiness-audit.md)。本轮先只读核查 AVD/设备、脚本与仪器测试入口、模拟器独占和公开 CI，再按用户批准保存记录并新增设备验收门禁：`scripts/device-preflight.py` 在 install 前后核验 APK 哈希/包名/版本/签名/ABI/原生库、设备 API/ABI/WebView 与忙碌状态；`scripts/verify-workspace.sh` 已接入并支持 pin 参数。宿主 mock 回归 14 项通过；未构建、未安装、未跑仪器。

## 0.1.8 交付与仓库基线

- `main` 为 `a9aa507c06883c62048a89924384ef0a10e01470`（PR #12 CI/RPC 契约合并）；`v0.1.8` tag 仍为 `c196bbf4d211604bc2076de450a8ed2d52ddde99`（PR #8 浏览器输入线程修复合并提交）。用户已发布 0.1.8；Agent 不代合 main、不创建 tag/Release。
- `artifacts/releases/0.1.8/` 六附件齐全。W1 只读复核 APK：`com.tanuki.modern.workspacecheck`、`versionName=0.1.8`、`versionCode=10`、minSdk26、targetSdk34、arm64-v8a；开发证书 SHA-256 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`；APK 10,479,850 字节，SHA-256 `eb695a12778652075e0324c65486534dab98848f073daa14ea631291b69b146e`。公开 Release 的 `app-update.json` 与 APK 均返回 HTTP 200。
- **0.1.8 没有对应的设备测试报告。** 0.1.7 及更早的 104 JVM、54 设备方法、PR #8 的 22 项等均为历史记录，未在本轮复跑；不能写成 0.1.8 已通过。
- 当前模拟器 `emulator-5572` = `Pixel_7_API_34_arm64`：API34/ARM64、WebView `159.0.8133.0`，已安装 0.1.7/code9。本轮只读查看 serial/API/ABI/WebView/已安装版本，未安装、未清数据、未运行仪器；数据可测性未独立复核。
- main CI [run 42163963031](https://github.com/leezzou/tanuki/actions/runs/42163963031) 双绿（Host 10s、Android 7m56s），但 Android job 使用 `-x buildNativeRuntime` 且仓库不跟踪 `jniLibs`，CI APK 无原生 `.so`，不能用于设备验收。

## Phase 2 交付状态

### W1 文档门禁（已合并）

- PR #10 已合并为 `5b01ddf`：恢复 `AGENT-STATE`/`TEST-REPORT`/交接印章，修复 20 个断链，新增 `docs/INTERFACES.md`；历史测试逐节标注“历史”，不改产品代码。

### W2 AO worktree 构建环境（已合并）

- PR #11 已合并为 `9e3e316`。新增 `scripts/ao-worktree-env.sh`、`scripts/test-ao-worktree-env.sh`、`docs/AO-WORKTREE-BUILD.md`；per-worktree `GRADLE_USER_HOME`/`ANDROID_USER_HOME`、只读复用缓存。
- 已验证（W2 当时）：`compileModernDebugKotlin` 与 `testModernDebugUnitTest` BUILD SUCCESSFUL；`test-ao-worktree-env.sh` PASS。未跑完整 `verify-workspace.sh`、lint、设备/仪器、发布签名。

### W3 CI 与 RPC 契约（已合并）

- PR #12 已合并为 `a9aa507`。新增 `.github/workflows/verify.yml`、`scripts/check-rpc-contract.py`、Browser/Phone 客户端回归、`docs/CI.md`；`.github` 进入公开源码包。CI 无 secrets/设备/发布。
- 已验证（W3 当时）：Host/Android 双绿；本轮再次以公开 run 42163963031 核对为 success。

### W4 设备验收门禁（本轮，待复审）

- 新增 `scripts/device-preflight.py`：APK SHA-256/包名/versionCode/versionName、应用与测试签名一致、arm64-v8a、`libproot.so`/`libproot-loader.so`/`libsupervisor.so`、设备 API/ABI、WebView ≥123（可配）、apt/dpkg/Node/PRoot/supervisor 进程、活动安装会话；`--phase post` 校验 install 后版本；证据写私有 `.build/logs/device-preflight-*.json`。
- `scripts/verify-workspace.sh` 设备路径已接入 pre/post 门禁，并新增 `--expect-apk-sha256`、`--expect-cert-sha256`、`--min-webview-major` 参数；失败不安装。
- `scripts/test-agent-workflow.py` 增加 mock 回归；`docs/AGENT-QUICKSTART.md`、`docs/CI.md`、`docs/TEST-REPORT.md` 已同步。

## 已实现、已验证与待验证

- 已验证（本轮）：宿主 mock 回归 14 tests OK（54.390s）；`bash -n verify-workspace.sh` 与 `device-preflight.py --help` 通过；main/CI/设备 metadata 只读核对；交接门禁刷新。
- 已验证（W3 本轮，历史）：CI/协议测试实现、Host 门禁、RPC 方法集/常量一致性。
- 已验证（W2/W1 前轮，历史）：worktree 构建引导、链接跳过策略、文档/门禁恢复、接口契约。
- 待验证：真实 APK/AVD 上的 pre/post 门禁与 14 类回归；PR 复审与合并；0.1.8 构建/设备/JVM/lint 证据；真实模型/账号、真机/MIUI、低 API、长期后台。
- 用户暂停：API Key 保存；真实模型/账号验收；真机验收。

## 环境与阻塞

- 当前分支 `audit/android-qa-readiness`，基线 `main/a9aa507`；工作区只含本轮脚本/文档改动，未改产品 Kotlin/原生代码、`runtime/` 或 0.1.8 附件。
- 设备：`emulator-5572` 原已运行；本轮未启动/停止、未安装、未跑仪器。
- 无当前产品修复阻塞；PR 的最终 CI/复审与合并由用户与编排器推进，Agent 不自行合并。

## 下一步

1. 在已确认无真实数据的专用 AVD 上实跑一次 `verify-workspace.sh --run-device-tests --confirm-test-data --device emulator-5572`，记录 pre/post 证据与 14 类结果；先确认无 apt/dpkg/安装/真实任务。
2. 视需要补机器级设备租约（serial + flock + `AO_SESSION_ID`），避免 QA/实现并发。
3. 为 WebSocket、Phone Guest、进程死亡/后台重启补 opt-in 用例；skip 记为未运行，不能计通过。
4. 0.1.8 设备证据另开验收任务；真实模型/账号仍等待用户恢复。
