# 交接：20261109-1802-device-condition-policy

- Agent/会话标识：android_agent-30（DSH Worker）
- 时间：2026-11-09 18:02 CST
- 状态：已完成（PR #21 待独立 Review 与真机 QA）
- 前一记录：[DSH 已安装版本管理](20261109-1539-dsh-installed-version-manager.md)

> 本记录只描述本轮设备温控/省电策略；不覆盖并行 PR 的结论。真机温控、低电/低内存注入与 WorkManager 实际调度尚未验证。

## 用户目标与范围

- 用户本次实际要求：定义 `NORMAL/WARM/HOT/CRITICAL/LOW_BATTERY` 设备状态；在热/低电时降低非关键后台工作，不突然杀死正在执行的 DSH 任务；与现有下载并发/WorkManager/前台服务兼容；提供开关/阈值；进程重启恢复；补纯策略 JVM 测试；在 `/tmp` 独立 clone 提交并开 PR，不合并、不触发 Review。
- 本次包含：纯策略状态机、Android 采样与持久化、WorkManager 后台更新检查延后/恢复、DSH 依赖下载并发上限、日志展示行数、PDF 预览渲染上限、设置页开关与低电阈值、首页状态卡片、专题文档与 JVM 回归。
- 本次不包含：真实设备温控/电池/低内存验收、WorkManager 实际调度与重启恢复、通知展示、图片/DOCX/视频预览降级、lint/assemble/仪器测试、合并/发布/独立 Review 触发。
- 用户改变或暂停的要求：未改变；API Key 保存、真实模型/账号与真机验收继续按既有暂停项处理。

## 当前状态

- 已实现：上述策略与接入点；中文注释；开关/阈值；状态持久化。
- 已验证：合并 main 后完整 JVM+lint 通过；`DeviceConditionPolicyTest` 24 项、`DeviceConditionRefreshQueueTest` 2 项、`DeviceConditionInitializationTest` 2 项、`DeviceConditionConfigWriterTest` 1 项、`NpmDownloadPolicyTest` 7 项、`NpmAdaptiveWorkerPoolTest` 2 项通过；完整 JVM 套件 395 tests / 0 failures / 0 errors / 0 skipped。
- 待验证：真机/模拟器温控回调、电池广播、系统低内存、WorkManager 真实调度与重启恢复、设置页 UI、PDF 降级实际像素、独立 Review。
- 当前分支：`ao/thermal-policy/device-condition-policy`，已合并 `origin/main=5fd1ab4` 解决冲突；在 `/tmp/tanuki-thermal-30` 独立 clone 验证/提交，不使用共享 worktree 的 `.git` 写操作。PR [#21](https://github.com/leezzou/tanuki/pull/21) 已创建，未合并、未触发 Review。
- 没有运行中的设备安装或真实 DSH 任务；本轮未启动/停止模拟器，未安装 APK。

## 本次改动

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
| `app/src/test/kotlin/com/tanuki/DeviceConditionPolicyTest.kt` | 新增 24 项纯策略回归，含待办确认/重试/代次复用/准入绑定 |
| `app/src/test/kotlin/com/tanuki/DeviceConditionRefreshQueueTest.kt` | 新增 2 项后台刷新队列回归 |
| `app/src/test/kotlin/com/tanuki/DeviceConditionInitializationTest.kt` | 新增 2 项初始化并发回归：暂停 restore 时提前启动 Worker、初始化只跑一次且失败释放等待者 |
| `app/src/test/kotlin/com/tanuki/DeviceConditionConfigWriterTest.kt` | 新增配置保存并发回归：首次保存暂停后第二次仍落盘最后选择 |
| `app/src/test/kotlin/com/tanuki/NpmDownloadPolicyTest.kt` | 新增外部并发上限与恢复回归 |
| `docs/DEVICE-CONDITION-POLICY.md` | 新增状态表、工作矩阵、接入点、恢复和未验证边界 |
| `docs/AGENT-STATE.md`、`docs/TEST-REPORT.md`、本记录 | 记录本轮状态和证据 |

## Review 修复（P2，三条）

1. 只为实际跳过的到期检查登记补跑：策略不再自动登记；`DeviceWork` 拆为 `APK_UPDATE_CHECK`/`DSH_UPDATE_CHECK`，被阻塞的 Worker 调用 `beginBackgroundWork(check)` 只登记自己；`resumeDeferred(checks)` 只入队实际登记项，空集合直接返回。新增无 Worker 跳过、只恢复实际跳过项、登记幂等回归。
2. 监控采样和持久化移出主线程：`refresh()` 只向 `DeviceConditionRefreshQueue` 投递合并请求，后台 collector 串行采样/评估/写盘；设置也走后台写盘；持久化比较排除 `updatedAtMillis`，常规采样不重复写盘。新增 `DeviceConditionRefreshQueueTest` 2 项，覆盖 `request()` 不内联执行写盘 worker 和突发请求合并。
3. 确认补跑持久入队后再清除待办：`evaluate()` 保留 `deferredChecks`，`resumeDeferred()` 返回确认入队的检查集合，监控调用 `acknowledgeChecks()` 后才清除；WorkManager `Operation` 结果确认失败/超时则保留待办，后续采样重试。新增入队失败保留重试、部分确认只清除已确认项、进程重启窗口保留待办回归。
4. 恢复完成前阻止 Worker 评估/写盘：新增 `DeviceConditionInitialization` 一次性屏障，`beginBackgroundWork()` 与设置入口等待 `restore()` 完成；新增并发回归模拟暂停 restore 后提前启动 Worker，确认 Worker 不会先用默认状态覆盖磁盘待办；初始化失败也释放等待者，避免 Worker 永久挂起。
5. 避免旧入队确认清除新一轮待办：每项待办增加登记代次，`acknowledgeChecks()` 只清除与入队时记录代次一致的项；确认回调阻塞期间同一检查被重新登记时保留新待办。新增“阻塞确认期间重新登记仍保留新待办”回归。
6. 代次不能重用：单独保留每项检查已发放的最大代次，确认清除待办后不删除计数；下一轮登记继续递增，避免“旧确认悬停、另一确认先清除、重新登记又从 1 开始”时被旧确认误删。新增完整三段窗口回归。
7. 设置保存串行化：新增 `DeviceConditionConfigWriter`，用单一 Mutex 覆盖“读取最新 config -> 原子写入”全过程；连续选择不同阈值不会让旧快照后写覆盖。新增“首次保存暂停、第二次保存完成、释放首次后最终落盘仍是最后一次选择”的回归。

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin --stacktrace` | JDK17/SDK36，`/tmp/tanuki-thermal-30`，BUILD SUCCESSFUL（5m29s，19 tasks） | 编译通过；只有既有弃用警告 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.DeviceConditionPolicyTest' --tests 'com.tanuki.DeviceConditionRefreshQueueTest' --tests 'com.tanuki.DeviceConditionInitializationTest' --tests 'com.tanuki.NpmDownloadPolicyTest' --tests 'com.tanuki.NpmAdaptiveWorkerPoolTest'` | BUILD SUCCESSFUL；24/1/2/2/7/2 tests，0 failures/errors/skipped | 目标回归通过；不是完整套件 |
| `./gradlew :app:testModernDebugUnitTest :app:lintModernDebug --rerun-tasks` | BUILD SUCCESSFUL（2m26s，35 tasks）；395 tests / 0 failures / 0 errors / 0 skipped；lint 0 Error | 合并 main 后完整 JVM+lint；不代表设备测试 |
| `./gradlew :app:lintModernDebug` | 与上一行组合执行；0 个 lint Error | 本地 lint 通过；不替代 assemble/仪器/真机 |

- 哪些是前一轮结果，哪些是本次新验证：本记录中的编译与目标回归均为本轮在 `/tmp` clone 新跑；前轮 205 tests 等历史数字未复用。
- 未测试、超时、失败和暂未复现的情况：真实温控/电池/低内存、WorkManager 调度/重启恢复、通知、设置页交互、PDF 降级像素、assemble/仪器、独立 Review 均未执行；lint 已在本轮通过。

### 最终结果

- 完整 JVM 与 lint：`./gradlew :app:testModernDebugUnitTest :app:lintModernDebug --rerun-tasks` → BUILD SUCCESSFUL（2m26s，35 tasks executed）；56 个结果文件，395 tests / 0 failures / 0 errors / 0 skipped。

## 未完成与暂停项

- 未完成：PR #21 等待独立 Codex High Review；真机/模拟器温控、低电/低内存、WorkManager 真实调度与重启恢复、通知、设置页 UI、PDF 降级像素、assemble/仪器测试。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号、真机验收继续暂停；合并/发布/触发 Review 未获授权。

## 下一步

1. PR [#21](https://github.com/leezzou/tanuki/pull/21) 已创建并推送；等待独立 Review，不合并、不触发 Review。
2. 由 Orchestrator/独立 QA 在无真实任务的设备上验证：加热触发 thermal status、充电/低电注入、低内存、WorkManager 周期与恢复、设置页开关/阈值、PDF 预览降级。
3. Review 意见逐条修复并重跑适用的 compile/JVM；通过且用户明确授权后才合并。

## 约束与风险

- 数据/设备/权限边界：未读取真实 Key、Cookie、token 或会话；未安装/启动设备；未修改既有安全/暂停项。
- 关键决策：策略只降并发/延后，不取消；前台与 DSH 任务列为受保护类别；后台更新检查由被阻塞的具体 Worker 登记，恢复时只补跑实际待办；低电退出阈值固定比进入阈值高 5 个百分点；低内存只参与组合状态，不写入热状态字段。
- 风险：`PowerManager` thermal status 仅 API 29+；低内存使用系统 `MemoryInfo.lowMemory`，可能与单应用内存压力不完全一致；只有实际被跳过的 APK/DSH 检查才会补跑，无 Worker 跳过时不发请求；PDF 降级只覆盖内置 PDF，图片/DOCX 未接入。
- 失败尝试与避免重复踩坑：没有把内存降级写入 `thermalCondition`（否则内存解除后还会被热迟滞多拖 30 秒）；并发测试需累计 32 次成功才能到 16 路。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未构建/发布 APK；提交前运行 `package-source.py` 仅核对 487 个公开源文件，工作材料不提交。
- 是否覆盖安装/签名范围/未发布事项：未安装、未签名、未发布。
- 提交/推送前清理的过期记录：未删除任何旧交接；它们仍被 `AGENT-STATE` 或并行任务引用。新增本记录并更新当前状态。
- 文档或脚本变化后仍需：运行 `python3 scripts/check-handoff.py --record docs/handoffs/20261109-1802-device-condition-policy.md`、`python3 scripts/check-handoff.py`；如需源码包再运行 `python3 scripts/package-source.py`。
