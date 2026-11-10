# 交接：20261110-1142-dsh-version-uninstall

- Agent/会话标识：android_agent-43（DSH Worker）
- 时间：2026-11-10 11:42 CST
- 状态：部分完成（实现与 JVM/lint 验证完成；PR、CI、独立 Review 与设备验收待办）
- 前一记录：[DSH 已安装版本管理](20261109-1539-dsh-installed-version-manager.md)（PR #18，历史记录保留）

## 用户目标与范围

- 用户本次实际要求：修复 DSH 版本管理体验——已安装清单显示当前使用/备用/占用空间/安装时间，支持卸载任意非当前版本；卸载前确认，禁止卸载运行中/当前版本，失败可回滚并给中文原因，清理不能误删共享数据或用户会话；切换/卸载与安装、启动、更新检查互斥；补卸载当前版本被拒、卸载备用版本、并发、失败回滚、清单刷新单测；中文注释、指定 Git 身份、PR 交付、不合并不发布。
- 本次包含：`DshVersions`/`DshUninstall`/`DshVersionGate` 纯逻辑；`RuntimeComponents` 卸载事务与恢复日志；`RuntimeManager` 互斥错误提示；`UpdateManager.check()` 与版本操作互斥；版本管理对话框文案与入口；JVM 回归；交接与专题文档。
- 本次不包含：真实 DSH 安装/卸载设备操作、切换后的真实 DSH 启动、下载逻辑、旧数据快照自动恢复、合并/发布/tag、独立 Review 触发。
- 用户改变或暂停的要求：API Key 保存、真实模型/账号验收、真机验收仍暂停；本次未触碰。

## 当前状态

- 已实现：
  - 清单继续显示版本、占用、安装时间、当前/备用、依赖锁与启动校验；新增“当前版本未知时禁止切换/卸载”的明确说明。
  - 卸载后端新增当前版本、运行中服务、活动子进程、更新检查/安装占用拒绝；所有拒绝均为中文。
  - 卸载改为两阶段事务：`prepared` 日志 → 改名到 `runtime/dsh-<version>.uninstall` → `committed` 日志 → 删除。删除一旦开始就不再回滚（无法证明未删内容），保留隔离目录与 `committed` 日志交给 `recover()` 清理；提交日志失败时只有移回成功才清 `prepared` 日志，回滚失败保留记录供下次启动恢复。
  - 卸载只处理 `engines/<version>` 与隔离目录；共享 `userdata`、`workspaces`、`backups`、下载缓存和用户会话不在删除范围；硬链接载荷只回收不再被引用的项。
  - 切换/卸载与安装、启动共用环境 mutation；切换/卸载与更新检查共用 `DshVersionGate`，锁顺序固定为 mutation → versionGate。
  - 主界面当前版本“卸载”入口改为“管理版本”；官方版本选择弹窗只对非当前已安装版本显示“卸载该备用版本”。
- 已验证：`compileModernDebugKotlin` BUILD SUCCESSFUL；目标 JVM `DshUninstallTest` 17 项、`DshVersionsTest` 7 项、`DshSwitchTest` 12 项全过；完整 JVM **552 tests / 0 failures / 0 errors / 0 skipped**、lint **0 errors / 94 warnings / 1 hint**；合并 `origin/main=533eff4` 后完整 JVM **575 tests / 0 failures / 0 errors / 0 skipped**、lint **0 errors / 94 warnings / 1 hint**。
- PR [#33](https://github.com/leezzou/tanuki/pull/33) 已创建。Codex Review 的 3 条意见（删除后完整性、回滚日志保留、committed 误删 target）已在 `6c75914` 修复：删除开始后不再回滚，`prepared` 回滚失败保留日志，`DshUninstallRecovery` 让 committed 只清理隔离目录并保留可能重装的 target。第四轮发现 DSH 重装会覆盖共用 `component-transaction`：`replace()` 现在先 `recover()` 再写新日志，并补“卸载失败后重装另一版本，重启只清隔离目录、保留当前版本”的回归。review 修复新 head 以 PR 页面为准。
- 待验证：review 修复后新 head 的 Host/Android CI、独立 Codex High Review 复核、真实设备上的版本列表/确认/卸载/回滚/清单刷新、真实 Node 降级/依赖锁损坏/进程中断恢复。
- 当前分支：`ao/version-uninstall/root`，基线 `origin/main=0555c3c0`；已合并 `origin/main=533eff4`（PR #34 文件查看器格式）解决 AGENT-STATE/CURRENT.json 冲突；合并后完整 JVM **573 tests / 0 failures / 0 errors / 0 skipped**、lint 0 errors/94 warnings/1 hint；未启动设备，未切换/卸载真实 DSH。
- 关键事实：卸载事务只接收程序目录和隔离目录；当前版本不提供任何后端卸载路径；`DshVersions.canUninstallFromManager` 在当前版本未知时返回 false。

## 本次改动

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

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew --no-daemon -x buildNativeRuntime :app:compileModernDebugKotlin :app:testModernDebugUnitTest --tests 'com.tanuki.DshUninstallTest' --tests 'com.tanuki.DshVersionsTest' --tests 'com.tanuki.DshSwitchTest'` | 通过（BUILD SUCCESSFUL） | 本 worktree，JDK17；36 tests / 0 failures / 0 errors / 0 skipped（卸载 17、版本 7、切换 12）。 |
| `./gradlew --no-daemon -x buildNativeRuntime :app:testModernDebugUnitTest :app:lintModernDebug` | 通过（BUILD SUCCESSFUL，3m30s） | 66 个结果文件、**552 tests / 0 failures / 0 errors / 0 skipped**（合并前）；合并 `origin/main=533eff4` 后 **575 tests / 0 failures / 0 errors / 0 skipped**（68 个结果文件）；`lintModernDebug` **0 errors / 94 warnings / 1 hint**；不替代设备验收。 |
| `./gradlew --no-daemon -x buildNativeRuntime :app:testModernDebugUnitTest --tests 'com.tanuki.NpmAdaptiveWorkerPoolTest' --rerun-tasks` | 通过 | 首次完整运行命中既有 `NpmAdaptiveWorkerPoolTest` 并发时序失败（退休 worker 数与目标不符），该测试单独重跑与最终完整 JVM 重跑均通过；本轮未改该测试。 |
| GitHub Actions 42756830044 / 42756838942 | 通过 | review 修复前 head `1fa66bd` 的 Host/Android 4/4 全绿；review 修复后的新 head CI 以 PR 页面为准。 |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261110-1142-dsh-version-uninstall.md` | 通过 | 刷新 `CURRENT.json` 指纹。 |
| `python3 scripts/check-handoff.py` | 通过 | 当前记录与源码指纹一致。 |

- 哪些是前一轮结果，哪些是本次新验证：review 修复后的 36 项目标 JVM、完整 552 项与合并后 575 项、lint 为本次新跑；review 修复前的 28/544 项是历史结果；合并前的 552 项也不再代表合并后 head，以 575 项为准。
- 未测试、超时、失败和暂未复现的情况：未跑 assemble、Android 仪器、真实设备；未在真实 userdata 上执行卸载；未验证进程在 `prepared`/`committed` 之间被杀后的 OEM 行为；review 修复后新 head 的 CI 待跑。

## 未完成与暂停项

- 未完成：第四轮修复后新 head 的 CI、独立 Codex High Review 复核、设备 UI 与真实卸载/回滚验收。
- 外部阻塞：无（等待 PR 流水线与审查安排）。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；本轮未触碰。

## 下一步

1. 推送第四轮修复后，逐条回复并 resolve 四条 thread；新 head CI 失败时按日志修复。
2. 等待独立 Codex High Review 复核新 head；不合并、不发布、不触发 AO Review。
3. 设备验收覆盖：清单显示当前/备用/占用/时间；当前版本无卸载入口；卸载备用后清单刷新且总占用下降；服务运行中/更新检查中按钮禁用或中文拒绝；删除中断后隔离目录与 committed 清理；确认会话、项目、快照与其他版本仍在。
4. 需要复核的恢复路径：prepared 中断 → 重启移回隔离目录；committed 中断 → 只清理隔离目录并保留已重装 target；安装/重装前先恢复待处理事务，不能覆盖日志；`recover()` 失败时保留中文错误与事务日志并进入 REPAIR。

## 约束与风险

- 数据/设备/权限边界：只删除 `engines/<version>` 与隔离目录；不执行 `pm clear`、不卸载 App、不操作设备；L2S 回收只在 mutation 与版本窄锁内、所有 guest 进程停止后运行。
- 关键决策及理由：组件事务日志改用 `AtomicFile.delete()`，否则只删主文件会留下 `.bak`，下次启动可能把已完成的卸载/替换事务重新恢复；卸载采用“先改名隔离、再提交、最后删除”，删除开始后一律不回滚（无法证明目录完整），隔离目录交给 `recover()` 清理；提交日志失败时只有回滚 move 成功才清日志，失败保留 prepared 记录；当前版本禁止所有后端卸载路径，主界面改用“重新安装/管理版本”。
- 失败尝试与避免重复踩坑的结论：测试临时目录没有 `runtime` 父目录，`renameTo` 失败；已在 `DshUninstall.apply` 创建隔离父目录并补回归。`UpdateManager.check` 直接持 `versionGate`，不持 mutation，避免与切换的 mutation → versionGate 顺序形成死锁。review 修复明确：标记文件不能证明目录完整，删除开始后不回滚；committed 阶段只允许清理隔离目录。
- 已知风险：`versionGate` 被手动检查持有时，切换/卸载会快速拒绝并提示稍后重试；UI 的 `tanukiIdle` 已把 `CHECKING` 计入忙碌，正常入口会先禁用。真实设备上 OEM 的 `renameTo`/删除权限行为仍需验证。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未生成 APK/ZIP；收尾按 AGENTS 要求运行 `package-source.py` 核对清单，源码包为工作材料，不提交。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未发布、未合并。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：未删除历史记录；`20261109-1539-dsh-installed-version-manager.md` 仍被多份并行交接引用并保留切换/清单实现依据，本轮结论补充到本记录与 `docs/AGENT-STATE.md`。旧记录中的 151 项测试为历史结果，不代表当前 head。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。
