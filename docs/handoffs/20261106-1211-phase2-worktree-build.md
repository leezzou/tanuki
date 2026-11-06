# 交接：20261106-1211-phase2-worktree-build

- Agent/会话标识：android_agent-5（DSH Worker 2，AO Worktree 构建环境）
- 时间：2026-11-06 12:11 CST
- 状态：部分完成（实现与验证完成；PR #11 等待 Codex 复审与合并）
- 前一记录：[文档门禁恢复与接口契约](20261106-1059-phase2-docs-contracts.md)

## 用户目标与范围

- 用户本次实际要求：为 AO 独立 worktree 提供可被 `source` 的构建环境脚本，解决 Gradle Wrapper 写 `~/.gradle` 被沙箱拒绝、JDK17/SDK 在主仓库或用户目录、worktree 缺少 `local.properties` 等问题；只写当前 worktree，不关闭沙箱，不修改全局 Java/SDK/Shell，不提交构建产物。
- 追加要求：同步最新 main `5b01ddf`（PR #10 文档合并）；按最保守策略处理缓存/源码树中的符号链接——一律跳过，目标树不得出现链接；补齐本轮独立交接、更新 `AGENT-STATE.md` 并刷新 `check-handoff.py` 印章。
- 本次包含：`scripts/ao-worktree-env.sh`、`scripts/test-ao-worktree-env.sh`、`docs/AO-WORKTREE-BUILD.md`、本记录、`docs/AGENT-STATE.md`、`docs/handoffs/CURRENT.json`。
- 本次不包含：产品代码、协议、资源、`app/src/main/**`；不改 main；不合并 PR；不创建 tag/Release；不重打 APK/源码 ZIP；不运行设备/仪器测试或 lint。

## 当前状态

- 已实现：per-worktree `GRADLE_USER_HOME` / `ANDROID_USER_HOME`；JDK17 与 SDK 组件校验；只读复用 Gradle 8.13 发行版、依赖缓存与主仓库原生源码；严格跳过符号链接；缺失 `local.properties` 时生成并确认被忽略。
- 已验证：`source`、`./gradlew --version`、`:app:compileModernDebugKotlin`、`:app:testModernDebugUnitTest`、`scripts/test-ao-worktree-env.sh` 均通过；本地工作树 433 个 blob 与同步 head tree 一致。
- 待验证：`scripts/verify-workspace.sh` 的完整 assemble/androidTest/lint 流程未在本 worktree 执行；lint、设备/仪器测试、发布签名未跑。
- 当前分支/PR：`chore/phase2-worktree-build`，PR #11；同步后 head 为 `290cb019c1b572bb31ed8cc3d1e542cd4af5eb40`（本交接/印章提交后由最终 PR head 取代）。main 为 `5b01ddfc4d95a45cb65198d6f2efef4ef167baae`。
- 关键事实：`docs/AO-WORKTREE-BUILD.md` 记录脚本用法、缓存成本、per-worktree 原因、失败排查与 W3 CI 建议；`scripts/test-ao-worktree-env.sh` 覆盖复制回退与链接跳过回归。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `scripts/ao-worktree-env.sh` | 新增 worktree 构建引导：JDK/SDK 校验、per-worktree Gradle/Android home、缓存复用、严格跳过符号链接、生成并忽略 `local.properties` |
| `scripts/test-ao-worktree-env.sh` | 新增回归：clone 失败回退不嵌套、顶层/目录内/链式/循环链接全部跳过、目标树无链接、写目标不改源、已有目标清理 |
| `docs/AO-WORKTREE-BUILD.md` | 新增专题文档：用法、成本、隔离原因、排查、W3 CI 建议；更新交接门禁状态为真实结果 |
| `docs/AGENT-STATE.md` | 更新当前交接、main 基线、W2 状态与下一步；保留 W1/0.1.8 历史事实 |
| `docs/handoffs/20261106-1211-phase2-worktree-build.md` | 本记录 |
| `docs/handoffs/CURRENT.json` | 由 `check-handoff.py --record` 生成，不手写 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh` | rc=0，约 2 s；跳过 `readelf` 与 `CLAUDE.md` 链接；复制树链接数 0 | 复用本机 Gradle 8.13/依赖缓存与主仓库原生源码；未写 `~/.gradle` |
| `./gradlew --version` | rc=0；Gradle 8.13；Launcher JVM 17.0.21 | Temurin JDK 17.0.21+9 |
| `:app:compileModernDebugKotlin` | BUILD SUCCESSFUL，15 s（19 个任务 up-to-date） | 同步 main 后运行；未改产品代码，任务未重新执行 |
| `:app:testModernDebugUnitTest` | BUILD SUCCESSFUL，11 s（28 个任务 up-to-date） | 同上；未重新执行测试；本分支较早完整执行过 21 套件/104 测试/0 失败 |
| `bash scripts/test-ao-worktree-env.sh` | PASS | 覆盖复制失败回退、链接跳过、目标无链接、写目标不改源 |
| GitHub API tree 对比 | 本地 433 个 blob 与 `290cb01` tree 全部一致 | 通过 main tarball 同步 docs；本地 Git 元数据仍受沙箱限制 |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261106-1211-phase2-worktree-build.md` | 通过：`Handoff refreshed`，433 个公开文件 | 已刷新 `CURRENT.json` |
| `python3 scripts/check-handoff.py` | 通过：`Handoff current: docs/handoffs/20261106-1211-phase2-worktree-build.md (433 public files)` | 不再出现 `No handoff stamp` |

- 前一轮 W1 的 `check-handoff.py`/`check-publication.py` 结果不在本轮重复声称；0.1.8 的历史测试未在本轮复跑。
- 未测试：完整 `verify-workspace.sh`、lint、设备/仪器、发布签名、真实模型/账号。

## 未完成与暂停项

- 未完成：PR #11 等待 Codex 复审与合并；合并后需在 main 复跑交接检查与完整 `verify-workspace.sh`。
- 未完成：lint、设备/仪器测试、发布签名未执行；0.1.8 仍无对应设备测试报告。
- 外部阻塞：本地 worktree 的 Git 元数据在主仓库 `.git/worktrees/` 下，沙箱禁止 `git fetch/add/commit`；本轮用 GitHub API 与 tarball 完成同步，未绕过沙箱。
- 用户暂停：API Key 保存；真实模型/账号验收。

## 下一步

1. Codex 复审 PR #11；通过后由用户/编排器决定合并，不自行合并。
2. 合并后在 main 运行 `python3 scripts/check-handoff.py`、`bash scripts/verify-workspace.sh`（需完整构建/lint），确认此前 W1 修复的门禁在 main 生效。
3. W3 按 `docs/AO-WORKTREE-BUILD.md` §8 集成 CI：per-job `GRADLE_USER_HOME`、发行版/依赖/原生源码缓存、`ANDROID_USER_HOME`、version→compile→test→按需 assemble。
4. 0.1.8 的测试证据如需要，另开验证任务实际执行；不要用历史数字代替。

## 约束与风险

- 数据/设备/权限边界：不读真实 Key/Cookie/token；不操作设备；不关闭/放宽沙箱；不使用 `danger-full-access`；不写全局 `~/.gradle`/`~/.android`。
- 关键决策：per-worktree 可写目录 + 只读缓存源；符号链接严格跳过，含链接缓存项可能未复用并由 Gradle 重下/重建，这是为隔离与可证明性接受的成本。
- 失败尝试：曾尝试保留安全相对链接、词法深度校验与 `cp -RL` dereference，均因链式目录链接可绕过或保留链接文本而放弃；最终采用一律跳过链接。
- 构建脚本自身可能在 `.build/native/readelf` 重建工具链接；该链接由项目构建产生，不属于复制缓存保留的链接。

## 产物与校验

- PR #11：<https://github.com/leezzou/tanuki/pull/11>；仅三个交付文件 + 本交接/状态/印章，未改产品代码。
- 未更新 APK、源码 ZIP 或发布附件；不覆盖安装、不签名、不发布。
- 本轮未删除历史交接：`20261105-1509-browser-input-thread.md`、`20261105-1625-video-shared-storage.md` 仍被 `TEST-REPORT.md` 引用并保留历史验证/未完成边界；W1 记录作为前一记录保留。当前记录、`TEMPLATE.md`、`CURRENT.json` 保留。
- 交接收尾需刷新印章；源码 ZIP 未重新生成，因为本轮不发布源码包。
