# 交接：20261109-1535-background-update-check

- Agent/会话标识：android_agent-24（DSH Worker）
- 时间：2026-11-09 15:35 CST
- 状态：已完成，[PR #19](https://github.com/leezzou/tanuki/pull/19) 已创建（功能提交 `d19cf48`）；未合并、未触发独立 Review
- 前一记录：[视频全屏允许旋转（FULL_SENSOR）](20261106-1735-video-fullscreen-full-sensor.md)

> 本记录只描述本轮后台更新检查；视频全屏 PR #17、F1 文件预览、0.1.8 发布附件均未改动。

## 用户目标与范围

- 用户本次实际要求：Tanuki APK 每 30 分钟、DSH 引擎每 24 小时做后台更新检查；APK 与 DSH 状态分离；前台徽标、后台通知、同版本去重；失败静默；保留手动检查；补纯策略 JVM 回归；不改 DSH 下载逻辑。
- 本次包含：
  - `androidx.work:work-runtime-ktx:2.9.1` 与两个唯一周期任务。
  - APK 检查 GitHub `releases/latest/download/app-update.json`，以 `BuildConfig.VERSION_CODE` 比较。
  - DSH 检查 `registry.npmmirror.com/@deepseek-ai%2Fdsh`，失败回退 `registry.npmjs.org`，12 秒超时。
  - 两个独立持久化状态与通知版本去重。
  - APK 更新卡片、DSH 版本卡片徽标；手动检查入口保留并同步后台快照。
- 本次不包含：DSH 下载/安装逻辑、APK 自动下载/安装、现有 `ApplicationUpdateManager`/`UpdateManager` 状态复用、设备/真机验证、独立 Review、合并或发布。

## 当前状态

- 已实现：后台检查纯策略、两个仓库、两个 Worker、调度器、通知、UI 徽标、手动结果同步。
- 已验证：`:app:compileModernDebugKotlin` BUILD SUCCESSFUL；`:app:testModernDebugUnitTest` BUILD SUCCESSFUL，150 tests / 0 failures / 0 errors / 0 skipped。新增 `BackgroundUpdateChecksTest` 18 项全过。
- 待验证：真实 WorkManager 调度与进程重启、Android 13 通知权限与通知展示、真实 GitHub/npmmirror/npm 网络、设备 UI、lint/assemble/仪器。
- 当前分支：`ao/workmanager-updates/background-update-check`（从 `origin/main=5c680b9` 创建；在本会话 worktree 实现，验证与提交在独立 `/tmp` clone 完成）。
- 未运行设备、未启动模拟器、未安装 APK、未发布。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/build.gradle.kts` | 增加 WorkManager 依赖。 |
| `app/src/main/kotlin/com/tanuki/update/BackgroundUpdateChecks.kt` | 纯策略：30 分钟/24 小时间隔、唯一任务名、12 秒超时、版本比较、APK/DSH 状态编解码、镜像回退、通知去重。 |
| `app/src/main/kotlin/com/tanuki/update/BackgroundUpdateRepositories.kt` | APK/DSH 两个独立仓库与受限 HTTPS 读取；写 `apk-update-check.json`、`dsh-update-check.json`。 |
| `app/src/main/kotlin/com/tanuki/update/UpdateCheckWorkers.kt` | 两个 `CoroutineWorker` 与 `UpdateCheckScheduler`（网络约束、UPDATE 唯一任务）。 |
| `app/src/main/kotlin/com/tanuki/update/UpdateNotifier.kt` | `updates` 通知渠道、APK/DSH 通知、Android 13 权限检查。 |
| `app/src/main/kotlin/com/tanuki/TanukiApp.kt` | 暴露两个仓库并注册周期任务。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 收集两份状态、手动检查申请通知权限；不再在 Activity 重建时回放旧同步。 |
| `app/src/main/kotlin/com/tanuki/update/ApplicationUpdateManager.kt` | 手动 APK 检查成功时回调后台仓库，携带检查开始时间。 |
| `app/src/main/kotlin/com/tanuki/update/UpdateManager.kt` | 手动 DSH 检查成功时回调后台仓库，携带检查开始时间；下载/安装逻辑未改。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 模型/回调接入；DSH 摘要与版本更新行显示“有更新：<version>”；“刷新”改为“刷新版本列表”。 |
| `app/src/main/kotlin/com/tanuki/ui/ApplicationUpdateCard.kt` | APK 卡片显示后台徽标、上次检查时间与静默失败说明。 |
| `app/src/test/kotlin/com/tanuki/BackgroundUpdateChecksTest.kt` | 18 项离网 JVM 回归。 |
| `docs/APP-UPDATES.md` | 增加后台周期检查说明。 |
| `docs/TEST-REPORT.md` | 记录本轮命令、150 项结果与未验证范围；更新清理后的历史链接。 |
| `docs/AGENT-STATE.md` | 更新当前交接、实现/验证/待验证与下一步。 |
| `docs/handoffs/20261105-1509-browser-input-thread.md`、`docs/handoffs/20261105-1625-video-shared-storage.md` | 删除过期交接；结论已保留在 `docs/TEST-REPORT.md`、`BROWSER-WORKSPACE.md`、`WORKSPACE-STORAGE.md`。 |

## Review 修复（P2）

- 评论：Activity 重建会重新执行同步 `LaunchedEffect`，把 Application 中保留的旧手动结果写回，可能清除较新的后台 latest/available。
- 修复：删除两个 `LaunchedEffect`；`ApplicationUpdateManager`/`UpdateManager` 在手动检查成功事件回调后台仓库，并携带检查开始时间。`applyManualApk`/`applyManualDsh` 在 `startedAt < previous.lastCheckedAt` 时保留后台快照，覆盖“手动旧结果 → 后台新结果 → Activity 重建”的 APK/DSH 两条链路。
- 回归：`staleManualApkReplayCannotClearNewerBackgroundSnapshot`、`staleManualDshReplayCannotClearNewerBackgroundSnapshot` 均通过；策略测试 18 项，完整 JVM 150 项。

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin --stacktrace` | BUILD SUCCESSFUL（3m36s，19 tasks） | 本轮新跑；仅有既有弃用警告。 |
| `source scripts/ao-worktree-env.sh && ./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.BackgroundUpdateChecksTest'` | BUILD SUCCESSFUL；16 tests / 0 failures | 目标类快速回归。 |
| `source scripts/ao-worktree-env.sh && ./gradlew :app:testModernDebugUnitTest --stacktrace` | BUILD SUCCESSFUL；27 个结果文件，150 tests / 0 failures / 0 errors / 0 skipped | 本轮完整 JVM；150 = 既有 132 + 新增 18。 |
| `grep` 合并清单 | 含 `WorkManagerInitializer`、`SystemJobService`、`RescheduleReceiver`、`RECEIVE_BOOT_COMPLETED`、`POST_NOTIFICATIONS` | 证明 WorkManager 清单合并生效；未在设备运行。 |

- 前一轮结果：0.1.8 设备冒烟、F1 126 JVM、PR #17 132 JVM 均为历史记录，未在本轮复跑。
- 未测试：lint、assemble、Android 仪器、真实网络请求、WorkManager 实际触发/重启恢复、通知展示与权限拒绝路径、深色/大字体 UI。

## 未完成与暂停项

- 未完成：PR 创建与独立 Codex High Review；设备级后台调度、通知、徽标验收。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收继续等待用户恢复。

## 下一步

1. PR [#19](https://github.com/leezzou/tanuki/pull/19)：等待 Orchestrator/用户决定是否触发当前 head 的独立 Codex High Review；PR 正文已记录本轮的 150 项结果。
2. 由 Orchestrator/用户决定是否触发当前 head 的独立 Codex High Review；不要用 Worker 自评替代。
3. 设备 QA（另行授权）检查：APK/DSH 徽标、30 分钟/24 小时调度、同版本只通知一次、Android 13 权限、重启后任务保留、失败静默。
4. 合并与发布需用户明确授权；本轮不代合、不创建 tag/Release。

## 约束与风险

- 未改 DSH 下载、安装、快照逻辑；仅给 `UpdateManager` 增加手动检查完成回调，后台检查仍只读元数据。
- WorkManager 周期任务是系统尽力调度，省电模式和 OEM 后台限制可能推迟；不承诺精确到分钟。
- APK 后台检查使用稳定 Release 的 `app-update.json`；debug/预发布渠道仍以手动检查的 Atom 流程为准，包名不匹配时不会误报。
- 通知只在发现新版本且权限允许时发送；同一版本由持久化 `notifiedVersion` 去重。拒绝通知权限不弹错误、不影响检查。
- 真实设备、真实 Release、真实 npm 元数据和 Android 13 通知行为未验证。

## 产物与校验

- 本轮无新 APK、源码 ZIP 或发布附件；`artifacts/` 未修改。
- 文档/源码变化后需按规则刷新 `CURRENT.json` 印章并重新生成源码包；本轮提交前执行交接/公开检查。
- 清理的过期记录：`20261105-1509-browser-input-thread.md`、`20261105-1625-video-shared-storage.md`。理由：任务已完成且结论已进入 TEST-REPORT/专题文档；保留 `20261106-1735`（PR #17 未决）、`20261106-1647`、`20261106-1415`、`20261106-1357`（仍被状态/验证引用）。
