# 交接：20261109-1812 DSH 运行失败分类与安全模式

- Agent/会话标识：android_agent-28
- 时间：2026-11-09 18:12 CST
- 状态：部分完成（实现、JVM 与 PR CI 已验证；PR [#25](https://github.com/leezzou/tanuki/pull/25) 已创建，独立 Review、设备/真机未完成）
- 前一记录：[DSH 已安装版本管理](20261109-1539-dsh-installed-version-manager.md)

## 用户目标与范围

- 用户本次实际要求：在既有 RuntimeManager/RuntimeService/健康检查/恢复代码上增加 DSH 运行失败分类（launchFailed、startupFailed、crashedEarly、crashedLate、killed、noSpace）；连续两次 startupFailed/crashedEarly 自动进入安全模式；安全模式使用最小/救援启动参数，不加载可疑插件/配置；恢复正常后进入 2 分钟 probation；状态持久化并支持进程重启恢复；UI 显示失败类型/安全模式/退出试用入口；自动重启有上限/退避，killed 与 noSpace 不盲目重启；纯策略抽离 JVM 测试；不删除用户数据、不改项目、不做破坏性恢复。
- 本次包含：纯 Kotlin 失败策略、RuntimeManager/RuntimeService/UI/MainActivity 接线、JVM 回归、INTERFACES/RUNTIME-COMPATIBILITY/TEST-REPORT/AGENT-STATE/本记录。
- 本次不包含：真实设备/PRoot 内 rescue 验证、真实崩溃/低内存/磁盘满注入、真实插件配置隔离验收、assemble/lint/仪器、合并/发布。
- 用户改变或暂停的要求：无新增暂停；既有 API Key 保存、真实模型/账号、真机验收暂停项保持不变。

## 当前状态

- 已实现：六类失败分类；normal 下连续两次 `STARTUP_FAILED`/`CRASHED_EARLY` 自动进入安全模式；`rescue` 最小启动参数与独立 `safe-home`/`safe-dsh`；安全模式不删除用户数据；显式“尝试恢复正常模式”进入 `PROBATION`，2 分钟内失败回安全模式；自动重启最多 3 次、2s/4s/8s 退避、killed/noSpace 不自动重启；`state/failure-state.json` 持久化与进程重启恢复；UI 失败类型/安全模式/退出试用入口；前台服务在自动重启等待期间保持。
- 已验证：`/tmp` 独立 clone 中 `compileModernDebugKotlin` 与完整 `testModernDebugUnitTest` 通过；完整 JVM 230 tests / 0 failures / 0 errors / 0 skipped，新增 `RuntimeFailurePolicyTest` 25/25。PR #25 的功能提交 `b4a6ef5` 与文档提交 `e2583b2` 的 push/PR CI 均通过（Host scripts + handoff、Android build/JVM/lint）；Android CI 不重建原生 `.so`，不能当设备验收。
- 待验证：真机崩溃/低内存、真实磁盘满、PRoot 内 rescue、真实插件配置隔离、UI 视觉/大字体/深色、独立 Review、合并；本机 lint/assemble/仪器未跑（CI 已跑 lint，不能替代设备验收）。
- 当前目标包名：未构建 APK；PR [#25](https://github.com/leezzou/tanuki/pull/25)，分支 `ao/runtime-safe-mode/root`，功能提交 `b4a6ef5ffe0c573bb44de2b7604dc43d91f79bf9`，基线 `2f3fac9301c7c3484df85e99091e8f63dddc0a48`；独立 `/tmp/tanuki-safe-mode` clone 验证，未使用共享 worktree 的 `.git` 写操作。
- 关键事实与可定位源码：`app/src/main/kotlin/com/tanuki/runtime/RuntimeFailurePolicy.kt`、`RuntimeManager.kt`、`RuntimeService.kt`、`State.kt`、`ui/SetupScreens.kt`、`ui/TanukiUi.kt`、`ui/Labels.kt`、`MainActivity.kt`；契约见 [INTERFACES](../INTERFACES.md) 1.1.1。

## 本次改动

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
| `app/src/test/kotlin/com/tanuki/RuntimeFailurePolicyTest.kt` | 新增 25 项纯 JVM 策略回归。 |
| `docs/INTERFACES.md` | 记录正常/安全模式启动参数、失败类型、安全/试用/重启边界。 |
| `docs/RUNTIME-COMPATIBILITY.md` | DSH 启动行补充安全模式 rescue 形式。 |
| `docs/TEST-REPORT.md` | 记录本轮命令、230 JVM、25 项新增与未验证范围。 |
| `docs/AGENT-STATE.md` | 更新当前交接与本轮状态。 |
| 本记录 | 本轮唯一交接、验证和未完成项。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `./gradlew -I /tmp/mirror.init.gradle :app:compileModernDebugKotlin --stacktrace` | `/tmp` clone、JDK17、SDK36；BUILD SUCCESSFUL（7m 9s，19 tasks） | 临时镜像 init script 仅用于本次构建，未提交、未改仓库配置。 |
| `./gradlew -I /tmp/mirror.init.gradle :app:testModernDebugUnitTest --stacktrace` | BUILD SUCCESSFUL（1m 53s）；37 个结果文件合计 230 tests / 0 failures / 0 errors / 0 skipped | 本轮新跑；205 项既有 + 25 项新增。 |
| `./gradlew -I /tmp/mirror.init.gradle :app:testModernDebugUnitTest --tests 'com.tanuki.RuntimeFailurePolicyTest'` | BUILD SUCCESSFUL；25/25 | 覆盖分类、连续计数、安全/试用、未就绪 probation 进程重启、重启上限、killed/noSpace、持久化与启动参数。 |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261109-1812-runtime-safe-mode.md` | 结果见提交前最终输出 | 刷新 `CURRENT.json` 印章。 |
| `python3 scripts/check-handoff.py` | 结果见提交前最终输出 | 当前交接一致性。 |
| `python3 scripts/check-publication.py --git` | 结果见提交前最终输出 | 本地链接/身份路径/凭据边界。 |
| `python3 scripts/package-source.py` | PASS；`source_files=485`，源码 ZIP/`SOURCE-SHA256SUMS` 已生成 | 工作材料，不提交。 |
| GitHub Actions run 42602010730（push）/ 42602805776（PR） | 均 success：Host scripts + handoff、Android build/JVM/lint | Android CI 使用 `-x buildNativeRuntime`，其 APK 无原生 `.so`，不能用于设备验收。 |

- 哪些是前一轮结果，哪些是本次新验证：230 JVM 与 compile 为本轮新跑；205 项中的既有测试源码来自基线，不能记成前轮设备结果。未运行前轮 PR 的 CI/设备结果，也未复跑 lint/assemble。
- 未测试、超时、失败和暂未复现的情况：真实 DSH 崩溃/低内存/磁盘满、PRoot rescue、插件配置隔离、UI 实机、lint/assemble/仪器均未执行。Maven Central DNS 不稳定，本轮仅用临时镜像完成依赖解析；不是产品网络行为验证。

## 未完成与暂停项

- 未完成：PR #25 的独立 Codex High Review、设备/真机验证、本机 assemble/仪器。
- 外部阻塞：当前环境 Maven Central DNS 不稳定，已用临时镜像绕过，未改仓库；若 CI 网络正常则不受影响。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收继续暂停；未在本轮恢复。

## 下一步

1. PR [#25](https://github.com/leezzou/tanuki/pull/25) 已创建并绑定 `ao/runtime-safe-mode/root`；不合并、不触发 AO Review，等待用户/编排器决定。
2. 由独立 Android QA 在明确无真实任务的设备上验证：安全模式 rescue 启动、连续失败进入安全模式、2 分钟 probation 回退、killed/noSpace 不自动重启、进程重启后状态恢复、UI 入口与大字体/深色。
3. 修复审查意见后重新跑 compile + 完整 JVM；新增设备证据必须绑定当前 head SHA、AVD/serial 与 APK 哈希。

## 约束与风险

- 数据/设备/权限边界：安全模式只读独立 `safe-home`/`safe-dsh`，项目工作区仍为原 `/workspaces`，未执行删除、清数据、卸载、wipe 或快照恢复；未改 API Key 暂停项、AO 配置、安全校验或 TLS/审批。
- 关键决策及理由：使用 DSH 官方 `rescue` profile 作为最小救援启动；`--from-default-profile web` 仅在 rescue 不存在时初始化，避免每次安全启动替换安全模式目录。安全模式不挂载浏览器/phone 工具，减少可疑配置与权限面。自动重启只拉起服务，不重放任务。用户停止入口递增 `runtimeGeneration` 并取消待执行自动重启；`interrupted` 服务状态下的 probation 进程重启回到安全模式，避免把崩溃后的试用误判为通过。重启计数达到上限后保持 3，不把未执行的第 4 次展示成已重启；自动重启等待时界面不声称服务已正常运行。
- 失败尝试与避免重复踩坑的结论：`/tmp` clone 首次离线构建缺少 `kotlin-stdlib-jdk8:1.8.0`，Maven Central DNS 超时；改用 `/tmp/mirror.init.gradle` 临时镜像后通过，未把环境问题写成代码失败，也未提交镜像配置。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未构建 APK；`package-source.py` 生成的源码 ZIP 与 `SOURCE-SHA256SUMS` 仅作工作材料，不提交、不作为发布附件。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名发布、未创建 tag/Release、未合并；PR [#25](https://github.com/leezzou/tanuki/pull/25) 仅创建，未触发 Review。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮未删除既有交接；`20261109-1539-dsh-installed-version-manager.md`、`20261109-1535-background-update-check.md`、`20261109-1539-dsh-install-resilience.md` 仍对应待审 PR/未完成验证，其余历史记录仍被 AGENT-STATE 引用，保留以避免丢失验证依据。本轮新增记录为唯一当前交接。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。
