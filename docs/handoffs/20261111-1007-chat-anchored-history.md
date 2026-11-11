# 交接：20261111-1007-chat-anchored-history

- Agent/会话标识：android_agent-62（AO worker）
- 时间：2026-11-11 10:07–11:00 CST
- 状态：已完成本地实现、JVM 与 lint 验证；设备验证、独立审查、合并与发布未执行
- 前一记录：[查看器传输胶囊避让](20261111-0538-viewer-transfer-capsule-inset.md)（已合并，PR #60）

> 普通 PR 只新增本记录，不修改 `docs/AGENT-STATE.md` 或 `docs/handoffs/CURRENT.json`；共享状态由集成维护方统一刷新。

## 用户目标与范围

- 用户本次实际要求：修掉原生聊天列表两个体验硬伤——流式时跟着跳、只能看最近 120 条；这是当前最高优先级。基于 `origin/main=62e4c30b` 建分支，作为聊天页唯一集成方（`DshNativePage`/`DshNativeController`/`MainActivity` 归本会话）。
  1. 居中锚定滚动：历史段向上增长、新消息段向下增长，锚点固定在两者之间；流式增量只影响下方段，用户上翻阅读时视口不动；用户在底部时跟随新消息，否则显示「N 条新消息」胶囊，点击回到底部。
  2. 历史向前分页：滚到顶部按页加载更早历史（每页 50 条或协议分页），加载时保持可见位置，不重复、不乱序；没有更多时明确提示。
  3. 去掉「只保留最近 120 条」硬截断，改为窗口化：内存保留有限窗口，超出后释放头部行并保持滚动位置正确。
  4. 流式增量合并幂等（同 seq 不重复插入），重连后能对账补齐。
  5. 补 JVM 回归：锚定不跳、分页插入位置、去重、窗口化边界；设备未验证要标注。
  6. 中文注释、author/committer=`leezzou`、PR 交付、不合并、不发布；文档/注释不得出现外部参考实现名称。
- 本次包含：聊天列表两段锚定、向前分页（`messagesBefore`）、控制器内存窗口、`DshMessageLedger` 幂等合并与快照/重连对账、顶部加载与「N 条新消息」胶囊、JVM 回归、INTERFACES/NATIVE-UI/NATIVE-PARITY 文档、本记录、PR。
- 本次不包含：审批/提问/工具卡逻辑、输入栏、会话管理、设备/仪器测试、独立审查、合并、发布、APK/源码包。
- 用户保留要求：不合并、不发布；设备由独立 QA 独占，本会话未申请也未运行模拟器/真机。

## 当前状态

- 基线：`origin/main=62e4c30b4a61636867cfc94736e44fa159f50b9f`（已 fetch 核对）；分支 `ao/chat-anchoring/anchored-list`，从会话根分支 `ao/chat-anchoring/root` 创建的兄弟分支。
- 已实现：
  - `DshChatWindow.kt` 纯策略：锚点分段（历史段含锚点、新消息段在锚点下方）、用户手势脱离判定、头部插页后的索引恢复、每页 50 条的游标、只释放视口上方的内存窗口、按 seq 去重的消息账本与快照区间对账。
  - 控制器：`loadOlder()`、`onViewportTop()`、`historyRevision`/`releasedOlder` 状态；快照不再清空已加载的更早分页；同 seq 实时事件只保留一份；重连后第二次 `Connected` 主动对账历史。
  - 页面：`LazyColumn` 显式历史段 + 锚点行 + 新消息段；顶部「加载更早历史 / 正在加载 / 已到会话最早一条 / 失败原因 / 已释放 N 条」；未跟随时「N 条新消息」或「↓ 最新」胶囊；`nestedScroll` 只在用户输入时脱离跟随，程序贴底/恢复不误判。
  - 协议：`DshChatSource.messagesBefore(sessionId, beforeSeq, maxMessages)`；`DshNativeClient` 复用官方 `session/page`，`throughSeq=beforeSeq`、单页上限 200。
  - 文档：INTERFACES 增加历史分页契约；NATIVE-UI 增加本轮结构说明；NATIVE-PARITY 更新 SESS-02/SESS-03 现状与 A-SESS-01 落地证据。
- 已验证：定向 79 tests 与完整 JVM 1072 tests/110 文件全部通过；lint 0 errors/104 warnings/1 hint；`check-publication.py --git` 0 findings；`git diff --check` 通过。
- 待验证：设备/模拟器上的手势、流式滚动、顶部加载、深色/大字体、真实 DSH 历史分页；独立 Codex High 审查；合并与发布。
- 关键源码：`app/src/main/kotlin/com/tanuki/ui/DshChatWindow.kt`（新增）、`DshNativeController.kt`、`DshNativePage.kt`、`app/src/main/kotlin/com/tanuki/web/DshLive.kt`、`DshNativeApi.kt`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/DshChatWindow.kt` | 新增纯策略：`DshChatAnchorPolicy`（两段切分、未读计数、恢复索引、用户手势脱离）、`DshChatWindowPolicy`（500 条内存预算、只释放视口上方、工具同步释放、分页游标）、`DshMessageLedger`（seq 去重排序、占位保留、快照区间对账）。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt` | 增加 `loadingOlder`/`olderError`/`historyRevision`/`releasedOlder`；实现 `loadOlder()`、`applyOlderPage()`、`onViewportTop()`、`trimWindow()`；历史页与快照改用消息账本；重连后对账；分页失败/重复页/空页处理。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 消息列表改为历史段 + 锚点 + 新消息段；顶部加载入口与边界提示；未跟随胶囊；按行 key + 偏移恢复视口；`nestedScroll` 用户手势脱离；移除 80/40/400 渲染窗口与旧「官方仅返回最近一页」文案。 |
| `app/src/main/kotlin/com/tanuki/web/DshLive.kt` | `DshChatSource` 增加 `messagesBefore`，作为向前分页的唯一数据入口。 |
| `app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt` | `messages()` 与 `messagesBefore()` 共用 `pageThrough()`；首屏 120 条、分页单页上限 200；未新增官方方法或端点。 |
| `app/src/main/kotlin/com/tanuki/ui/DshChatRender.kt` | 移除旧的渲染窗口与拖动跟随策略，保留底部判定与其余渲染策略；窗口/锚定策略迁入 `DshChatWindow.kt`。 |
| `app/src/test/kotlin/com/tanuki/DshChatWindowTest.kt` | 新增 16 项纯策略回归：锚点切分、分页前插不改变下方段、索引恢复、未读计数、手势脱离、预算边界、只释放视口上方、工具同步释放、seq 去重、占位、快照对账与幂等。 |
| `app/src/test/kotlin/com/tanuki/DshNativePagingTest.kt` | 新增 11 项控制器回归：分页插入排序/去重、`beforeSeq` 与每页 50、失败保留、空页停止、重复页防循环、窗口释放边界、同 seq 只插一次、重连对账、快照不删更早分页。 |
| `app/src/test/kotlin/com/tanuki/DshNativeApiTest.kt` | 新增 1 项协议回归：`messagesBefore` 以 `beforeSeq` 作 `throughSeq`、`maxMessages=50`。 |
| `app/src/test/kotlin/com/tanuki/DshChatRenderTest.kt` | 删除已迁出的渲染窗口/拖动跟随即测试；保留并继续覆盖底部判定。 |
| `app/src/test/kotlin/com/tanuki/DshNativeControllerTest.kt` | 为测试数据源补 `messagesBefore`；流式落盘用例改为断言同一条消息只出现一次（空快照不再清空 HTTP 历史页）。 |
| `docs/INTERFACES.md` | 增加 `session/page` 向前分页契约、内存窗口与快照对账边界；从缺口列表移除“历史向前分页”。 |
| `docs/NATIVE-UI.md` | 增加“聊天列表两段锚定与向前分页（2026-11-11）”专题段；更新 `session/page`、控制器/页面/策略文件说明。 |
| `docs/NATIVE-PARITY.md` | 更新 SESS-02/SESS-03 现状与归属，补充 A-SESS-01 分页回归落地证据。 |
| `docs/handoffs/20261111-1007-chat-anchored-history.md` | 本记录。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git rev-parse origin/main` | `62e4c30b4a61636867cfc94736e44fa159f50b9f` | 基线核对 |
| `./gradlew ... :app:testModernDebugUnitTest --tests 'com.tanuki.DshChatWindowTest' --tests 'com.tanuki.DshNativePagingTest' --tests 'com.tanuki.DshChatRenderTest' --tests 'com.tanuki.DshNativeControllerTest' --tests 'com.tanuki.DshNativeApiTest'` | 通过（定向 79 tests / 0 failed） | `-PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime`；JVM，未涉及设备 |
| `./gradlew ... :app:testModernDebugUnitTest`（完整） | 通过：1072 tests / 110 个结果文件 / 0 failures / 0 errors / 0 skipped | 同一 Gradle 配置；首次完整运行 1071 tests 也通过 |
| `./gradlew ... :app:lintModernDebug` | 通过：0 errors / 104 warnings / 1 hint | 无新增 lint 错误 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1007-chat-anchored-history.md` | 通过 | 普通 PR 模式，不改共享状态 |
| `python3 scripts/check-publication.py --git` | 739 public files / 735 index entries / 0 findings | 上传前检查 |
| `git diff --check` | 通过 | 无空白错误 |
| 设备 / 模拟器 / 真实 DSH | 未执行 | 设备由独立 QA 独占；下方手工步骤待 QA 执行 |

- 已知既有不稳定项：`NpmAdaptiveWorkerPoolTest.downshiftRetiresExcessWorkersAfterCurrentTaskAndKeepsQueueDraining` 在本轮完整套件中失败 2 次（断言 `retiredWorkers expected 8 but was 9`），单独复跑和最终完整套件均通过。该测试属于 NPM 并发池，本轮未改动；失败源于“活跃数瞬时低于目标时调度器补员后又退休”的时序竞争，不是聊天列表改动。
- 未测试、超时、失败和暂未复现的情况：设备手势与帧率、真实 DSH 的 `session/page` 分页返回、深色/大字体、无障碍、低端机长历史内存峰值均未验证。

### 手工验收步骤（设备，待独立 QA）

1. 打开一个长会话，滚动到顶部，确认自动/按钮加载更早历史时当前内容不跳动；重复上滑直到出现“已到会话最早一条”。
2. 触发一条长回复流式输出，在回复过程中上滑阅读旧消息，确认视口不被拉回底部；右下角出现「N 条新消息」胶囊，点击回到底部并继续跟随。
3. 在底部等待新消息/流式增量，确认自动跟随且不出现重复气泡；断网重连或点刷新后核对消息齐全、顺序正确、无重复。
4. 长会话连续上翻十余页后再回到底部，确认旧消息未消失、滚动位置正确，顶部提示“已释放 N 条更早消息，向上滚动会重新读取”。
5. 在深色、大字体、360dp 窄屏重复 1–3。

## 未完成与暂停项

- 未完成：设备/模拟器验收、真实 DSH 历史分页验证、独立 Codex High 审查、PR 审查意见处理（若有）。
- 外部阻塞：设备由独立 QA 独占；本会话未运行设备测试。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收、合并与发布；本记录不触碰这些暂停项。

## 下一步

1. PR 已创建（见“产物与校验”）；等待 GitHub CI、独立审查与独立 QA。
2. QA 按上方手工步骤验证；若真实 DSH 的 `session/page` 不支持 `throughSeq` 向前读取，控制器会因“无新增行”停止续页并显示“已到会话最早一条”，需要在设备上确认官方实际返回后调整游标。
3. 处理 review comment 后推送新提交；最新 SHA 重新验证，旧审查结论不沿用。

## 约束与风险

- 数据/设备/权限边界：只改原生聊天展示/状态与官方 `session/page` 读取；不写 DSH 数据、不读凭据、不改官方 npm 包、不触碰设备。
- 关键决策：
  - 用单一 `LazyColumn` 的“历史段 + 锚点行 + 新消息段”实现两段结构，避免两个独立滚动容器造成跨段手势断裂。
  - 用视口首行 key + `firstVisibleItemScrollOffset` 恢复位置；头部释放只发生在视口上方，正在阅读的行不会被删。
  - 内存窗口只按消息条数计预算（500），工具卡按同一 seq 边界同步释放，避免文本/工具错位。
  - 快照按 seq 区间对账而不是整表替换，避免重连或分页后被 120 条窗口覆盖。
- 失败尝试与避免重复踩坑的结论：
  - 曾用 `LazyListItemInfo.offset - viewportStartOffset` 计算恢复偏移，会与内容内边距叠加；改为“目标行是首行时直接用 `firstVisibleItemScrollOffset`，否则对齐到 0”。
  - 曾用 `isScrollInProgress && !atBottom` 判断脱离，程序贴底也会误判；改为只在 `NestedScrollSource.UserInput` 且已离开底部时冻结锚点。
  - 空快照整表替换会清空已加载历史；改为区间对账，并由现有流式用例守住“落盘消息只出现一次”。
- 已知边界：用户在顶部持续上翻时，正在阅读的分页不会被释放，内存可短暂超过 500 条预算；回到下方后按预算释放。真实 DSH 分页语义未在设备验证。

## 产物与校验

- 本轮不生成 APK、源码 ZIP 或发布附件；不覆盖安装、不发布、不合并。
- PR：`<待创建后补全>`；目标 `main`；分支 `ao/chat-anchoring/anchored-list`。
- 提交前已运行 `check-handoff.py --pr`、`check-publication.py --git`、`git diff --check`。
- 普通 PR 不刷新 `AGENT-STATE.md`/`CURRENT.json`/`TEST-REPORT.md`；只新增本记录。
