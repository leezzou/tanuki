# Tanuki 当前状态

当前交接：[AO Worktree 构建环境](handoffs/20261106-1211-phase2-worktree-build.md)。当前 PR #11（`chore/phase2-worktree-build`）在 main `5b01ddf` 之上新增 AO worktree 构建脚本、回归测试与本专题文档，并补齐本文件与交接印章；不改产品代码、不重打 APK。0.1.8/code10 已由用户发布，六附件保持原样。

## 0.1.8 交付与仓库基线

- `main` 为 `5b01ddfc4d95a45cb65198d6f2efef4ef167baae`（PR #10 文档门禁合并）；`v0.1.8` tag 仍为 `c196bbf4d211604bc2076de450a8ed2d52ddde99`（PR #8 浏览器输入线程修复合并提交）。用户已发布 0.1.8；Agent 不代合 main、不创建 tag/Release。
- `artifacts/releases/0.1.8/` 六附件齐全。W1 只读复核 APK：`com.tanuki.modern.workspacecheck`、`versionName=0.1.8`、`versionCode=10`、minSdk26、targetSdk34、arm64-v8a；开发证书 SHA-256 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`；APK 10,479,850 字节，SHA-256 `eb695a12778652075e0324c65486534dab98848f073daa14ea631291b69b146e`。公开 Release 的 `app-update.json` 与 APK 均返回 HTTP 200。
- **0.1.8 没有对应的设备测试报告。** 0.1.7 及更早的 104 JVM、54 设备方法、PR #8 的 22 项等均为历史记录，未在本轮复跑；不能写成 0.1.8 已通过。
- 当前模拟器 `emulator-5572`：API34/ARM64、WebView `159.0.8133.0`，已安装 0.1.7/code9。任务基线记录 `files/runtime` 与 `files/engines` 为空；本轮未提权读取 release 私有目录。只读查看，未安装、未清数据、未运行仪器。

## Phase 2 W2：AO worktree 构建环境

- 分支 `chore/phase2-worktree-build`，PR #11；已用 GitHub API 在 main `5b01ddf` 上同步（merge commit，非 force，未推 main、未重建 PR）。
- 新增 `scripts/ao-worktree-env.sh`：JDK17/SDK 组件校验、per-worktree `GRADLE_USER_HOME`/`ANDROID_USER_HOME`、只读复用 Gradle 8.13 发行版与依赖缓存、复用主仓库原生源码、严格跳过符号链接、生成并忽略 `local.properties`。
- 新增 `scripts/test-ao-worktree-env.sh` 与 `docs/AO-WORKTREE-BUILD.md`：覆盖复制失败回退、顶层/目录内/链式/循环链接跳过、目标树无链接、写目标不改源，并记录用法、成本、排查与 W3 CI 建议。
- 已验证（W2 本轮）：`source` rc=0；`./gradlew --version` 为 Gradle 8.13 / JDK 17.0.21；`:app:compileModernDebugKotlin` BUILD SUCCESSFUL（约 15 s，up-to-date）；`:app:testModernDebugUnitTest` BUILD SUCCESSFUL（约 11 s，up-to-date）；`scripts/test-ao-worktree-env.sh` PASS；本地 433 个 blob 与同步 head tree 一致。
- 未验证：完整 `verify-workspace.sh`、lint、设备/仪器测试、发布签名；本分支较早完整执行过 21 套件/104 测试/0 失败，但最终同步后测试任务为 up-to-date，未重新执行。
- 交接/印章：本记录已链接；`check-handoff.py --record` 刷新后，无参数检查通过（不再有 `No handoff stamp`）。

## 已实现、已验证与待验证

- 已验证（W2 本轮）：worktree 构建引导脚本、严格链接跳过策略、回归测试与专题文档；命令与结果见本轮交接。
- 已验证（W1 前轮，仅文档/门禁，历史）：`check-handoff.py` 刷新后退出 0；`check-publication.py --git` 0 findings；PR #10 已合并。
- 待验证：PR #11 尚未合并；`verify-workspace.sh` 完整构建/lint 未在本 worktree 执行；0.1.8 的构建、设备、JVM、lint 没有随版本记录；真实模型/账号、真机/MIUI、低 API、长期后台等仍未验收；CI 由 W3 建立。
- 用户暂停：API Key 保存；真实模型/账号验收。

## 环境与阻塞

- 当前分支 `chore/phase2-worktree-build`，基线 main `5b01ddf`。本地 worktree 的 Git 元数据在主仓库 `.git/worktrees/` 下，沙箱禁止 `git fetch/add/commit`；同步与发布通过 GitHub API 和 main tarball 完成，未绕过沙箱。
- 文件边界：本轮只改三个交付文件、本文件、交接记录与 `CURRENT.json`；不改 `app/`、`runtime/`、`artifacts/releases/0.1.8/`，不重打或替换已发布附件。
- 无当前产品修复阻塞；PR #11 等待 Codex 复审与合并。

## 下一步

1. Codex 复审 PR #11；通过后由用户/编排器决定合并，不自行合并。
2. 合并后在 main 运行 `python3 scripts/check-handoff.py` 与 `bash scripts/verify-workspace.sh`（需完整构建/lint），确认 W1 修复的门禁在 main 生效。
3. W3 按 `docs/AO-WORKTREE-BUILD.md` §8 集成 CI：per-job `GRADLE_USER_HOME`、发行版/依赖/原生源码缓存、`ANDROID_USER_HOME`、version→compile→test→按需 assemble。
4. 0.1.8 的测试证据如需要，另开验证任务实际构建/运行并写入 [TEST-REPORT](TEST-REPORT.md)；真实模型/账号验收仍等待用户恢复。
