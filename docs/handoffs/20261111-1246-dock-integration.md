# 交接：20261111-1246-dock-integration

- Agent/会话标识：android_agent-62（AO worker）
- 时间：2026-11-11 12:46 CST
- 状态：目标/待办停靠条已接入原生聊天页；JVM 与 lint 验证完成（待本记录最终数字补齐）；设备验证、独立审查、合并未执行
- 前一记录：[聊天列表锚定与向前分页](20261111-1007-chat-anchored-history.md)（已合并，PR #62 / `dfb890ab`）

> 普通 PR 只新增本记录，不修改 `docs/AGENT-STATE.md` 或 `docs/handoffs/CURRENT.json`；共享状态由集成维护方统一刷新。

## 用户目标与范围

- 用户本次实际要求（编排 `android_agent-37` 转达）：#68（`DshGoalDock`/`DshTodoDock`）已合并，基于最新 main 接线。
  1. 在 `DshNativePage` 输入栏上方接入 `DshDockColumn`，与 `DshApprovalEntryBar` 组成三条：审批 > 目标 > 待办；同屏最多两条，其余折叠 `+N`；展开时每条只出现一次。
  2. 数据只接真实事件（goals/todo 来自 live 事件或会话状态）；没有数据就隐藏，不放占位/假数据。
  3. 不破坏已合并的锚定滚动与审批停靠条行为；补回归“三条折叠展开不重复、输入框不被推动跳变”。
  4. 基于最新 main 建分支、push、PR 交付，不合并；回报 PR 号与 head。
- 本次包含：`DshDockColumn` 接线与审批条迁移、目标/待办真实事件投影与解析、页面/控制器状态、JVM 回归、INTERFACES/NATIVE-UI/NATIVE-PARITY 文档、本记录、PR。
- 本次不包含：`goals/*` 暂停/恢复/编辑/清除 RPC、队列卡片、设备/仪器测试、独立审查、合并、发布、APK/源码包。
- 用户保留要求：不合并、不发布；设备由独立 QA 独占。

## 当前状态

- 基线：`origin/main=7780fbf3ec23d207263e210c6911c338a062d7cd`（#68 合并后、#72 文档提交）；分支 `ao/chat-anchoring/dock-integration`，从会话根分支命名空间 `ao/chat-anchoring` 创建的兄弟分支。
- 已实现：
  - 页面纵向顺序改为“消息区 `weight(1f)` → `DshDockColumn` → `DshComposerBar`”；删除 `SessionStrip` 后的独立审批条，把同一个 `DshApprovalEntryBar` 作为 `approvalBar` 插槽传入，避免两条审批条。
  - `approvalVisible = DshApprovalEntry.of(approvalState, selectedId).visible`；目标/待办来自控制器状态，缺失时 `null`，对应条不占高度。
  - `DshCoordinationParser`（web）：只识别 `goal/update`/`goal`/`goals/update`/`session/goal`、`todo/update`/`todos/update` 与 `todo_write`/`write_todo`/`todowrite`/`update_todos` 工具调用；空文本/未知状态不生成投影，不保留原始 JSON。
  - `DshSessionPage`、`session/follow` 快照与 `event` 帧接入 `DshCoordination`；`DshLiveEvent.Coordination` 增量驱动目标/待办条。
  - `DshDockStateMapper`（ui）：目标状态映射 `RUNNING/PAUSED/BLOCKED`，未知/已完成隐藏；待办状态映射 `PENDING/IN_PROGRESS/COMPLETED`，空清单隐藏。
  - 控制器 `DshNativeState.goal/todos`；选择会话清空，页面/快照/实时事件按字段合并，解析失败不覆盖已知值。
  - `DshDockPlacement` 记录“停靠条高度只压缩消息区、输入栏贴底”的布局契约，配套 JVM 回归。
- 已验证：定向 6 类共 64 tests 通过；完整 JVM 1164 tests/119 文件 0 失败；lint 0 errors/108 warnings/1 hint（改动文件无新增告警）。
- 待验证：真实 DSH 的 goal/todo 事件名与字段、设备高度动画/展开滚动/深色/大字体/TalkBack、独立审查、合并与发布。
- 关键源码：`app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt`、`DshNativeController.kt`、`DshDockStateMapper.kt`（新增）、`app/src/main/kotlin/com/tanuki/web/DshCoordination.kt`（新增）、`DshNativeApi.kt`、`DshMuxProtocol.kt`、`DshLive.kt`、`DshDock.kt`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 审批条移入 `DshDockColumn` 插槽，停靠容器放在 `DshComposerBar` 之前；三条顺序/折叠由组件负责，数据取控制器 `goal/todos`。 |
| `app/src/main/kotlin/com/tanuki/ui/DshDock.kt` | 新增 `DshDockPlacement` 纯布局契约：停靠条高度变化只压缩消息区，输入栏顶部位置不变。 |
| `app/src/main/kotlin/com/tanuki/ui/DshDockStateMapper.kt` | 新增目标/待办投影到组件纯状态的唯一映射；未知状态/空文本/空清单隐藏。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt` | 状态增加 `goal/todos`；选择会话清空；历史页/快照/实时 `Coordination` 按字段合并；解析失败不覆盖。 |
| `app/src/main/kotlin/com/tanuki/web/DshCoordination.kt` | 新增保守的事件投影与解析：白名单事件/工具、字段/条数上限、单行摘要、空值不生成。 |
| `app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt` | `DshSessionPage.coordination`、`parsePage` 解析、follow 帧映射与 `DshLiveEvent.Coordination`。 |
| `app/src/main/kotlin/com/tanuki/web/DshMuxProtocol.kt` | follow `Snapshot` 携带 coordination；`event` 帧解析目标/待办并返回 `DshFollowFrame.Coordination`。 |
| `app/src/main/kotlin/com/tanuki/web/DshLive.kt` | 实时事件新增 `Coordination`；快照带 coordination（默认空，兼容旧调用）。 |
| `app/src/test/kotlin/com/tanuki/DshCoordinationTest.kt` | 新增 7 项事件投影回归：目标摘要/状态、空文本忽略、取最新、待办事件、工具参数（对象/裸数组）、未知事件忽略、空清单隐藏。 |
| `app/src/test/kotlin/com/tanuki/DshDockIntegrationTest.kt` | 新增 5 项接线回归：状态映射、空数据隐藏、三条折叠/展开不重复、缺失不占槽、停靠条高度变化时输入栏顶部不变。 |
| `app/src/test/kotlin/com/tanuki/DshNativeControllerTest.kt` | 新增 4 项控制器回归：历史页驱动目标/待办、实时事件更新、会话切换隐藏、未知状态不覆盖已知目标。 |
| `docs/INTERFACES.md` | 增加目标/待办投影契约与缺失隐藏边界；更新缺口列表。 |
| `docs/NATIVE-UI.md` | 增加输入栏上方三条停靠条接线说明与布局契约。 |
| `docs/NATIVE-PARITY.md` | SESS-14/CHAT-19 更新为原生部分（只读目标/待办停靠条）。 |
| `docs/handoffs/20261111-1246-dock-integration.md` | 本记录。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git rev-parse origin/main` | `7780fbf3ec23d207263e210c6911c338a062d7cd` | #68 已合入的基线 |
| `./gradlew ... :app:testModernDebugUnitTest --tests 'com.tanuki.DshCoordinationTest' --tests 'com.tanuki.DshDockIntegrationTest' --tests 'com.tanuki.DshDockLayoutTest' --tests 'com.tanuki.DshGoalDockTest' --tests 'com.tanuki.DshTodoDockTest' --tests 'com.tanuki.DshNativeControllerTest'` | 通过：定向 6 类 / 64 tests / 0 failed（coordination 7、integration 5、layout 11、goal 7、todo 8、controller 26） | JDK17；不访问真实 DSH |
| `./gradlew ... :app:testModernDebugUnitTest :app:lintModernDebug` | 通过：完整 JVM **1164 tests / 119 个结果文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 108 warnings / 1 hint** | 同一 Gradle 配置；新增/修改文件无 lint 告警 |
| 设备/模拟器/真实 DSH | 未执行 | 设备由独立 QA 独占 |

- 未测试：真实 DSH 目标/待办事件名与字段、目标操作 RPC、设备高度动画与展开滚动、深色/大字体/横屏/分屏/TalkBack。
- 数据边界：没有事件时 `goal/todos` 为 `null`，停靠条不占高度；不使用示例目标、占位待办或默认进度。

## 未完成与暂停项

- 未完成：真实 `goals/*` 暂停/恢复/编辑/清除 RPC 与 `/goal` 气泡；队列卡片；设备验收；独立审查。
- 外部阻塞：真实目标/待办事件字段未在设备确认；若官方事件名/字段与保守白名单不同，停靠条会保持隐藏（不会显示错数据），需要拿到真实样本后扩展解析。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收、合并与发布；本记录不触碰。

## 下一步

1. 等完整 JVM + lint 通过后提交并创建 PR（不合并），回报 PR 号与 head。
2. QA 在设备上验证：三条/两条/一条/零条；+N 展开与收起；目标/待办真实数据出现时显示、缺失时隐藏；输入栏在折叠/展开动画中不位移；锚定滚动与审批抽屉行为不变。
3. 拿到真实 goals/todo 事件样本后，按实际字段补充解析；未确认前继续保持隐藏而非猜测。

## 约束与风险

- 数据/设备/权限边界：只读解析官方事件；不写 DSH 数据、不调用未确认的 goals RPC、不读凭据、不操作设备。
- 关键决策：
  - 审批条只保留一份，作为 `DshDockColumn` 插槽；页面不再单独渲染，避免重复。
  - 目标/待办缺失即隐藏，不用默认文案或空清单占位；未知状态不猜成进行中。
  - 目标操作能力保持默认全 false；暂停/恢复/编辑/清除按钮只显示禁用原因，等 RPC 契约确认后再接。
  - 停靠条不外包动画；高度动画由组件内部完成，输入栏保持在纵向栈最后。
- 已知风险：真实事件名/字段未验证，可能全部隐藏；`goal/update` 的“清除”语义未确认，当前不会主动清空已有目标（会话切换/刷新才清）；设备动画与布局未验收。

## 产物与校验

- 本轮不生成 APK、源码 ZIP 或发布附件；不覆盖安装、不发布、不合并。
- PR：待创建后补全；目标 `main`；分支 `ao/chat-anchoring/dock-integration`。
- 提交前运行 `check-handoff.py --pr`、`check-publication.py --git`、`git diff --check`。
- 普通 PR 不刷新 `AGENT-STATE.md`/`CURRENT.json`/`TEST-REPORT.md`；只新增本记录。
