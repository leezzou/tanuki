# Tanuki 当前状态

当前交接：[CI 门禁与 RPC 契约测试](handoffs/20261106-1207-ci-rpc-contract-tests.md)。W3 的 PR #12 在 W2 合并后的 `main/9e3e316` 之上新增 GitHub Actions `verify` workflow、Browser/Phone RPC 契约检查与客户端回归，并把 `.github` 纳入公开源码包；已补 W3 handoff、刷新交接印章并 rebase 解决冲突。不改产品协议。0.1.8/code10 已由用户发布，六附件保持原样。

## 0.1.8 交付与仓库基线

- `main` 为 `9e3e3161a74bf95a5b91089facdeddcadb6d4dda`（PR #11 AO worktree 构建环境合并）；`v0.1.8` tag 仍为 `c196bbf4d211604bc2076de450a8ed2d52ddde99`（PR #8 浏览器输入线程修复合并提交）。用户已发布 0.1.8；Agent 不代合 main、不创建 tag/Release。
- `artifacts/releases/0.1.8/` 六附件齐全。W1 只读复核 APK：`com.tanuki.modern.workspacecheck`、`versionName=0.1.8`、`versionCode=10`、minSdk26、targetSdk34、arm64-v8a；开发证书 SHA-256 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`；APK 10,479,850 字节，SHA-256 `eb695a12778652075e0324c65486534dab98848f073daa14ea631291b69b146e`。公开 Release 的 `app-update.json` 与 APK 均返回 HTTP 200。
- **0.1.8 没有对应的设备测试报告。** 0.1.7 及更早的 104 JVM、54 设备方法、PR #8 的 22 项等均为历史记录，未在本轮复跑；不能写成 0.1.8 已通过。
- 当前模拟器 `emulator-5572`：API34/ARM64、WebView `159.0.8133.0`，已安装 0.1.7/code9。任务基线记录 `files/runtime` 与 `files/engines` 为空；本轮未提权读取 release 私有目录。只读查看，未安装、未清数据、未运行仪器。

## Phase 2 交付状态

### W1 文档门禁（已合并）

- PR #10 已合并为 `5b01ddf`：恢复 `docs/AGENT-STATE.md`、`docs/TEST-REPORT.md`、交接印章，修复 20 个断链，新增 `docs/INTERFACES.md`；历史测试逐节标注“历史”，不改产品代码。
- 已验证（W1 当时）：`check-handoff.py` 刷新后退出 0；`check-publication.py --git` 0 findings。

### W2 AO worktree 构建环境（已合并）

- PR #11 已合并为 `9e3e316`。新增 `scripts/ao-worktree-env.sh`：JDK17/SDK 组件校验、per-worktree `GRADLE_USER_HOME`/`ANDROID_USER_HOME`、只读复用 Gradle 8.13 发行版与依赖缓存、复用主仓库原生源码、严格跳过符号链接、生成并忽略 `local.properties`。
- 新增 `scripts/test-ao-worktree-env.sh`、`docs/AO-WORKTREE-BUILD.md`：覆盖复制失败回退、顶层/目录内/链式/循环链接跳过、目标树无链接、写目标不改源，并记录用法、成本、排查与 W3 CI 建议。
- 已验证（W2 当时）：`source` rc=0；`./gradlew --version` 为 Gradle 8.13 / JDK 17.0.21；`:app:compileModernDebugKotlin` 与 `:app:testModernDebugUnitTest` BUILD SUCCESSFUL（当时多为 up-to-date）；`scripts/test-ao-worktree-env.sh` PASS；本地 433 个 blob 与同步 head tree 一致。
- 未验证：完整 `verify-workspace.sh`、lint、设备/仪器测试、发布签名；本分支较早完整执行过 21 套件/104 测试/0 失败，但最终同步后测试任务为 up-to-date，未重新执行。

### W3 CI 与 RPC 契约（PR #12，待复审）

- 分支 `ci/phase2-ci-protocol-tests`；新增 `.github/workflows/verify.yml`：push 与 PR(main) 触发；host 运行 agent/release/publication/RPC 契约与 Node 客户端回归；Android 使用 JDK17/SDK36/build-tools35 运行 assemble、AndroidTest、JVM 单测与 lint；缓存 Gradle/SDK，无 secrets/设备/发布。
- 新增 `scripts/check-rpc-contract.py`：冻结 Browser 17 个、Phone 11 个方法，校验 `/rpc`、Bearer、43 字符 token、请求/响应上限与错误码格式；新增/扩展 Browser 7 个、Phone 18 个 mock server 回归。
- `docs/CI.md` 记录 CI 边界；`scripts/package-source.py` 仅把 `.github` 加入公开源码 `TREES`。已同步 W2 的 `docs/AO-WORKTREE-BUILD.md` 等 6 个变更并 rebase 到 `main/9e3e316`。
- 已验证（W3 本轮）：`check-handoff.py` current；`check-publication.py --git` 0 findings；干净树 Host 模拟全过；Browser 7/7、Phone 18/18、契约 OK、agent/release/publication 脚本全过；最终 head 的 GitHub Actions 以 PR #12 checks 为准。

## 已实现、已验证与待验证

- 已验证（W3 本轮）：CI/协议测试实现、本地与干净树 Host 门禁、RPC 方法集/常量一致性；未改产品代码。
- 已验证（W2 前轮，历史）：worktree 构建引导脚本、严格链接跳过策略、回归测试与专题文档；命令与结果见 W2 交接。
- 已验证（W1 前轮，历史）：文档/门禁恢复、接口契约、断链修复。
- 待验证：PR #12 等待最终 CI 与 Codex 复审；`verify-workspace.sh` 完整构建/lint 未在当前 worktree 执行；0.1.8 的构建、设备、JVM、lint 没有随版本记录；真实模型/账号、真机/MIUI、低 API、长期后台等仍未验收。
- 用户暂停：API Key 保存；真实模型/账号验收。

## 环境与阻塞

- 当前分支 `ci/phase2-ci-protocol-tests`（PR #12），基线 `main/9e3e316`。本地共享 `.git` 在沙箱内不可写（`index.lock: Operation not permitted`）；按授权使用 GitHub API 发布与 rebase，未改 main、未推 main。
- 文件边界：W3 只新增/修改 CI、协议测试、`docs/CI.md`、`.github` 公开源码白名单、W3 交接记录与当前状态；W1/W2 文档只同步 main 已合并版本，不改写其内容；不改 `app/` 产品代码、`runtime/` 或 0.1.8 附件。
- 无当前产品修复阻塞；PR #12 的最终 CI/复审与合并由用户与编排器推进，W3 不自行合并。

## 下一步

1. 等待 PR #12 最终 head 的 GitHub Actions：Host 的 `check-handoff` 与 Android 构建/JVM/lint 应通过。
2. 通过后请求 Codex Review；按 review 意见继续修复，不跳过检查、不自行合并。
3. 合并后由维护者在 main 复跑 `check-handoff.py`、`check-publication.py --git` 与 `verify-workspace.sh`。
4. 0.1.8 的测试证据若需要，另开验证任务实际构建/运行并写入 [TEST-REPORT](TEST-REPORT.md)；不要用历史数字代替。真实模型/账号验收仍等待用户恢复。
