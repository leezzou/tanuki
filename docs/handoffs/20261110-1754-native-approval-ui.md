# 交接：20261110-1754-native-approval-ui

- Agent/会话标识：android_agent-53（AO worker）
- 时间：2026-11-10 17:54 CST
- 状态：已完成（组件/JVM/lint/文档）；PR [#47](https://github.com/leezzou/tanuki/pull/47) 待审；未合并、未发布
- 前一记录：[原生界面可交互切片](20261110-1233-native-ui-chat.md)（PR #40 已合并 `main=1f7222d`）；当前基线 `origin/main=4f2a3717`

## 用户目标与范围

- 用户本次实际要求：把审批、工具调用、计划审阅的原生呈现做到清晰可用；基于 `origin/main=4f2a3717` 建分支；主改 `DshApproval*`/`DshTool*` 组件，`DshNativePage`/`MainActivity` 接线交给集成方并提供 hook 清单。
- 具体验收：审批卡显示来源/工具/风险/参数摘要（长参数折叠）/允许/拒绝/记住；批量逐条清晰不误点；记住的选择可查看、单条撤销、全部清除且撤销立即生效；工具卡显示状态/耗时/命令与 cwd/diff 增删配色/文件变更/输出折叠与复制；计划多步骤进度可见且不当普通消息；失败/断线给中文原因并可重试，不静默丢弃待审批；补 JVM 回归。
- 本次包含：`DshApprovalProtocol`/`DshApprovalController`/`DshApprovalPanel`/`DshToolCard`/`DshToolTimeline`/`DshPlan`（新）/`DshPlanCard`（新）、对应 JVM 测试与专题文档。
- 本次不包含：`DshNativePage.kt`、`MainActivity.kt` 接线；官方 npm 包/DSH 数据目录修改；真实设备/PRoot DSH/仪器/真机；合并/发布；用户暂停的 API Key 保存与真实模型/账号验收。

## 当前状态

- 已实现：
  - 审批卡：请求来源、工具名、中文风险等级与说明、参数摘要、完整参数折叠、逐条编号、批量说明、高风险允许二次确认、允许/拒绝/记住；未知工具只能拒绝。
  - 本次运行记忆：`DshApprovalMemory.forget` + `DshApprovalController.forgetRemembered`；面板列出记忆并可逐条“撤销”或“全部清除”，撤销立即阻止后续自动回答。
  - 失败/断线：`DshApprovalFailures` 统一中文文案；`DshApprovalState.errorRetryable`；断线/回答失败保留 `pending`，卡片显示“可重试”，通道错误提供“重试连接”。
  - 工具卡：运行/成功/失败、真实耗时、命令与 cwd、文件变更列表、增删行背景色 diff、折叠输出/错误与复制。
  - 计划：`DshPlan`/`DshPlanParser` 保守解析计划事件与计划工具调用；`DshPlanCard` 显示进度条、逐步骤状态、当前步骤和“同意执行/要求修改”按钮；计划工具 callId 可从普通工具时间线剔除。
- 已验证：定向 JVM 4 个测试类共 51 tests / 0 failures / 0 errors / 0 skipped（`DshPlanTest` 7、`DshToolTimelineTest` 12、`DshApprovalProtocolTest` 16、`DshApprovalControllerTest` 16）；完整 JVM 810 tests / 91 个结果文件 / 0 failures / 0 errors / 0 skipped；lint 0 errors / 102 warnings / 1 hint。
- 待验证：真实设备/PRoot DSH、计划审阅真实事件字段、深色/大字体/长参数/长 diff。
- 分支：`ao/native-approval-ui/root`，基线 `origin/main=4f2a3717d6d795fd6b94cdb695878d8119f818c1`；无运行中安装/设备任务。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshApprovalProtocol.kt` | 解析审批来源短标签；参数摘要；中文失败文案；记忆单条撤销与显示标签。 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalController.kt` | `errorRetryable`、统一失败文案、`forgetRemembered`；断线保留待审批。 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalPanel.kt` | 来源/风险/参数摘要/长参数折叠、批量编号、高风险二次确认、记忆列表与单条撤销、断线中文提示。 |
| `app/src/main/kotlin/com/tanuki/ui/DshToolCard.kt` | 状态耗时、文件变更、diff 增删行背景色、输出/错误复制、cwd/命令保留。 |
| `app/src/main/kotlin/com/tanuki/web/DshToolTimeline.kt` | `startedAt`/`finishedAt`/`durationMs`；`DshToolDuration` 文案；孤儿结果不伪造耗时。 |
| `app/src/main/kotlin/com/tanuki/web/DshPlan.kt` | 新增计划模型、步骤状态、审阅状态、计划事件/计划工具调用解析、`planCallIds`。 |
| `app/src/main/kotlin/com/tanuki/ui/DshPlanCard.kt` | 新增计划卡片：进度、步骤状态、当前步骤、审阅按钮；不复用普通消息气泡。 |
| `app/src/test/kotlin/com/tanuki/DshPlanTest.kt` | 新增 7 项计划解析/进度/审阅/截断/非法输入回归。 |
| `app/src/test/kotlin/com/tanuki/DshToolTimelineTest.kt` | 新增 3 项耗时/运行中/孤儿/格式化回归。 |
| `app/src/test/kotlin/com/tanuki/DshApprovalProtocolTest.kt` | 新增 4 项来源、参数摘要、中文失败、单条撤销回归。 |
| `app/src/test/kotlin/com/tanuki/DshApprovalControllerTest.kt` | 新增 2 项撤销立即生效、断线保留待审批并重试回答回归；origin 变化补失效 notice。 |
| `docs/NATIVE-UI.md` | 53 组件与集成 hook 清单、文件表、计划边界。 |
| `docs/INTERFACES.md` | 1.9 审批/工具/计划界面契约与缺口。 |
| `docs/NATIVE-PARITY.md` | APPROVAL/TOOL/CHAT 计划相关状态更新为组件就绪/待接线。 |
| `docs/TEST-REPORT.md` | 本切片验证记录。 |
| `docs/handoffs/20261110-1754-native-approval-ui.md` | 本记录。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime --tests 'com.tanuki.DshPlanTest' --tests 'com.tanuki.DshToolTimelineTest' --tests 'com.tanuki.DshApprovalProtocolTest' --tests 'com.tanuki.DshApprovalControllerTest'` | BUILD SUCCESSFUL；51 tests / 0 failures / 0 errors / 0 skipped | JDK17/worktree；合成 JSON，不访问真实 DSH |
| `./gradlew :app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL；810 tests / 91 个结果文件 / 0 failures / 0 errors / 0 skipped；lint 0 errors / 102 warnings / 1 hint | 完整 JVM/lint；合成事件，不访问真实 DSH |
| `git diff --check`、`python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1754-native-approval-ui.md`、`python3 scripts/check-publication.py --git` | 通过；676 个公开文件 / 672 个索引项 / 0 findings | 提交前收尾；共享状态允许落后 |

- 哪些是前一轮结果，哪些是本次新验证：51 项定向与完整 810 tests、lint 均本轮新跑；未复用历史数字。
- 未测试、超时、失败和暂未复现的情况：真实 Android/PRoot DSH、计划审阅真实事件、设备布局/暗色/大字体/长参数/长 diff、仪器/真机、Android assemble 均未执行。

## 未完成与暂停项

- 未完成：提交/推送/PR；`DshNativePage`/`MainActivity` 接线（集成方按 NATIVE-UI 的 hook 清单完成）。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收。

## 下一步

1. 已完成完整 JVM/lint、`git diff --check`、`check-handoff.py --pr`、`check-publication.py --git`。
2. 已以 `leezzou <115924933+leezzou@users.noreply.github.com>` 提交并推送 `ao/native-approval-ui/root`；PR [#47](https://github.com/leezzou/tanuki/pull/47) 已创建，待 CI/独立审查，不合并。
3. 集成方按 `docs/NATIVE-UI.md` 第 4 节 hook 接入：审批面板 `sessionTitle`/`onForgetRemembered`，计划卡 `latest`/`planCallIds`，并补真实计划审阅事件映射；设备验收另行授权。

## 约束与风险

- 数据/设备/权限边界：不读真实 Key/Cookie/token/会话；不改 DSH 数据/官方 npm；设备 QA 由独立 QA 独占。
- 关键决策及理由：记忆仅本次原生运行内存，不冒充官方持久授权；撤销只影响后续自动回答，不伪造撤回已发出的官方结果；高风险允许二次确认；计划解析只认白名单工具/事件，无法识别时不猜；计划真实审阅 RPC 未新增，留给集成方按官方协议接线。
- 失败尝试与避免重复踩坑的结论：首次编译 `DshPlan` 的 `for` 循环条件不合法，已改为 `0 until minOf(...)`；计划事件 `eventId` 在内层 `event` 对象，测试按此构造。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未生成 APK/源码 ZIP；未安装、未发布。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装；未发布；不合并。
- 提交/推送前清理的过期记录：本轮未删除历史记录；仅新增本记录，结论在 `NATIVE-UI`/`INTERFACES`/`NATIVE-PARITY`/`TEST-REPORT` 可定位。
- PR：[https://github.com/leezzou/tanuki/pull/47](https://github.com/leezzou/tanuki/pull/47)，head 以最终推送 SHA 为准。
- 普通 PR 不刷新共享状态/印章；集成维护方在批量合并后统一处理。
