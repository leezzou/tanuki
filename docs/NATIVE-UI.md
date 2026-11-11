# 原生界面与官方 DSH 界面切换

本文件记录“官方界面 / 原生界面”开关的接口发现、已实现最小切片、回退与安全边界。默认始终是官方界面；原生界面是可切换的交互层，复用官方 Typert Remote 读写会话；不修改官方 npm 包、DSH 数据目录、会话内容或 WebView 安全策略。

导航、适配、三态、无障碍与冷启动的通用组件和接线清单见 [原生界面导航、适配与可用性打磨](NATIVE-UI-POLISH.md)。

## 1. 结论

官方 DSH `0.2.1-rc.5` 没有 REST 会话列表端点，也没有 SSE。会话、消息、发送与审批都通过 **Typert Remote** 暴露：

- 一元调用：`POST /api/<method>`，JSON 信封，需签名 Cookie。
- 实时流：`/api/remote.mux` WebSocket。
- 审批/提问：Agent-scoped waterfall 事件，经 WebSocket 的 `$events` 通道回答。

原生页现已覆盖：官方会话列表/切换、历史文本与工具事件、`session/follow` 实时流、`session/prompt` 发送、`session/cancel` 停止、输入/附件、Markdown、审批面板与工具卡片。渲染层（46）补齐流式按帧合并、长列表窗口、代码行号、表格/链接/图片附件交互与发送失败卡片。官方网页版始终保留为可切换、可回退入口；用户提问仍只读展示并跳官方处理。

## 2. 认证与传输（可复用）

| 项目 | 官方行为 | Tanuki 使用方式 |
|---|---|---|
| 启动地址 | `dsh web: http://127.0.0.1:<port>/?token=...` | `RuntimeManager.captureUrl()` 原样保留在内存，不写日志/状态 |
| 浏览器登录 | `GET /` 带 token，写签名 Cookie 后重定向清理 token | 管理 WebView 继续使用，未改 |
| API 登录 | `GET /connect?token=...&interactions=none`，返回 302、API Cookie 与 mux 地址 | 原生客户端用同一启动 token 换取独立 API Cookie |
| 一元 RPC | `POST /api/<method>`，`Content-Type: application/json` | 仅向当前精确 loopback origin 发送 |
| 请求信封 | `{"type":"client-request","rpcId":"...","method":"<method>","payload":{"args":{...}}}` | `DshApiProtocol.requestBody()` |
| 响应信封 | `{"type":"server-response","rpcId":"...","result":{"ok":true,"value":...}}` 或 `{"ok":false,"error":{"code":...}}` | 校验 `rpcId`；业务错误只保留短错误码 |
| 实时流 | WebSocket `/api/remote.mux` | 本切片未使用 |
| Cookie 绑定 | 签名 Cookie 绑定 host + port；进程/Host 重启后需重新 `/connect` | 客户端按 `origin + launchUrl` 绑定，地址变化自动重取 |

`interactions=none` 的 API Peer 不需要 WebSocket 即可先做 HTTP 一元调用；未建 WebSocket 时该轻量 Peer 会保留到 Host 退出，这是官方已记录的边界。原生客户端在服务运行期间复用同一 Cookie，切换界面不重复 `/connect`；服务停止或启动地址变化后丢弃旧 Cookie。

## 3. 可复用端点与本切片

| 官方 method | 用途 | 本切片 |
|---|---|---|
| `session/list` | 读取可见会话摘要；标题在 `projections.values.title`，事件切点在 `projections.asOfSeq` | 使用 |
| `session/projections` | 读取单会话 `kind=sequenced` 的 `asOfSeq` | 使用；作为 `session/page` 的 `throughSeq` |
| `session/page` | 读取一段历史事件；`records[].event` 含 `user/message`、`assistant/message` 等；`throughSeq` 是事件上界 | 使用；首屏 `maxMessages=120`；向前分页 `maxMessages=50`、`throughSeq=最早 seq-1` |
| `session/prompt` | 发送提示词 | 未使用；原生切片没有输入框 |
| `session/cancel` | 取消运行 | 未使用 |
| `session/create` / `rename` / `selectModel` / `updateQueue` | 会话管理 | 未使用 |
| `session/attachment` | 附件读取 | 未使用 |
| `skills/list` | 技能目录 | 未使用 |
| `session/follow` / `session/control` | 实时历史跟随 / 会话状态流 | 缺口：需要 WebSocket，本切片未实现；工具卡片只来自历史页 |
| `$events` / `$events/result` | 转发事件流与审批回答 | 已实现：打开 `$events`，读取 `approval/request` waterfall，经 `$events/result` 回传 `allowed-once`/`rejected` |
| `approval/request`、`userQuestions.answer` | 审批、用户提问 | 审批已实现；用户提问仍是缺口，需要 answer Peer 与事件通道，不能靠一元 HTTP 伪造 |

事件文本提取规则（`DshApiProtocol.parsePage`）：

- `user/message`：读 `data.content` 中 `type=text` 的块。
- `assistant/message`：读 `data.message.content` 中 `type=text` 的块。
- 只保留官方 append-origin 事件（`surfaceOp === "append"`）；`surfaceOp=replace` 的模型上下文替换副本、compact-checkpoint 不显示。
- `user/message` 仅保留 `source.kind=user` 的真实用户消息；注入上下文不标成“你”。
- `tool/call` 与 `tool/result` 进入工具时间线：显示工具名、参数、命令、输出、错误、`meta.diffs` 文件变更与 diff；没有结果时明确标为“进行中/未读取到结果”。
- 其他事件（`turn/end`、未知事件）跳过；图片、附件显示占位卡片，远程图片不自动联网，本地/内嵌图片点击后才能查看，读取失败给中文原因。
- 空文本、损坏记录跳过；不生成占位消息。
- 单条消息上限 12,000 字符，响应上限 2 MiB。

## 4. 应用内实现

| 文件 | 作用 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshUiMode.kt` | 偏好枚举与纯策略：默认官方；只有“用户选原生 + 服务运行 + 无回退原因”才显示原生 |
| `app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt` | 42 已接入 `DshSessionPage.timeline`、follow 工具事件与 `DshTransport`；聊天/流式/审批状态共用 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt` | 会话/历史/发送/停止状态；2026-11-11 增加 `loadOlder()`、`onViewportTop()`、内存窗口释放、重连对账与 `DshMessageLedger` 合并 |
| `app/src/main/kotlin/com/tanuki/web/DshApprovalProtocol.kt` | 审批帧/回答信封/风险/本次运行记忆的纯协议 |
| `app/src/main/kotlin/com/tanuki/web/DshWebSocketClient.kt` | **临时实现，待收敛到 42 transport**：最小 RFC6455 客户端，loopback、Cookie、Accept 校验、掩码、握手超时与大小上限 |
| `app/src/main/kotlin/com/tanuki/web/DshApprovalSession.kt` | `/connect` 认证、`$events` 流、`$events/result` 回答、401 断开重连/独立 Peer |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalController.kt` | 审批状态机、连接生命周期、允许/拒绝/记住本次运行 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalPanel.kt` | 审批卡片：来源、工具名、风险等级、参数摘要/长参数折叠、逐条编号、高风险二次确认、允许/拒绝/记住；记忆列表可逐条撤销/全部清除 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalSheet.kt` | 审批入口窄条 `DshApprovalEntryBar` 与底部抽屉 `DshApprovalSheet`；窄条只显示数量和风险，不把待审批卡片散落在消息流 |
| `app/src/main/kotlin/com/tanuki/ui/DshToolCard.kt` | 工具卡片：运行/成功/失败、耗时、命令与 cwd、文件变更、增删行配色 diff、折叠输出与复制 |
| `app/src/main/kotlin/com/tanuki/web/DshPlan.kt` | 计划步骤/审阅状态模型；保守解析计划事件与计划工具调用，返回计划与需从普通工具卡剔除的 callId |
| `app/src/main/kotlin/com/tanuki/ui/DshPlanCard.kt` | 计划卡片：多步骤状态、进度条、当前步骤、待审阅时“同意执行/要求修改”；不复用普通消息气泡 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 挂载 `DshToolCard`、输入栏、Markdown、审批入口条/抽屉；46 增加发送失败卡片；51 接入 `DshApprovalEntryBar`/`DshApprovalSheet`；2026-11-11 改为历史段 + 锚点 + 新消息段的两段列表、新消息胶囊与顶部向前分页 |
| `app/src/main/kotlin/com/tanuki/ui/DshMarkdown.kt` | 46 渲染层：标题/嵌套列表/任务列表/引用/表格/分隔线/代码块/行内样式；代码横向滚动与可选行号、链接确认、图片/附件占位与查看器 |
| `app/src/main/kotlin/com/tanuki/ui/DshChatRender.kt` | 46 纯策略：底部判定、流式增量合并、图片采样、composer 发送记录/失败跟踪、链接/图片/附件分类、错误可读化、代码行号与表格上限 |
| `app/src/main/kotlin/com/tanuki/ui/DshChatWindow.kt` | 2026-11-11 纯策略：锚点分段与跟随判定、向前分页游标、内存窗口裁剪、消息账本去重/快照对账 |
| `app/src/main/kotlin/com/tanuki/web/DshToolTimeline.kt` | `tool/call`/`tool/result` 时间线、命令/diff/文件变更解析；51 已改用 `DshToolMerge.merge` 保留真实起止时间 |
| `app/src/main/kotlin/com/tanuki/web/DshQuestion.kt` | 用户提问模型与保守解析；单选/多选/文本、选项、草稿选择与必填校验 |
| `app/src/main/kotlin/com/tanuki/ui/DshQuestionCard.kt` | 用户提问独立卡片：单选/多选/文本、选项与本地提交草稿；`onSubmit=null` 时明确标注“回答入口待协议确认” |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerController.kt` | 会话列表状态机、搜索/置顶/未读、单个与批量归档、写操作与中文错误 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerPage.kt` | 按 cwd 分组的会话列表、多选批量归档、主机切换、详情分页、模型/权限面板与确认交互 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerActivity.kt` | 独立会话管理页入口、本机未读/置顶偏好与本机/远程主机切换 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionPresentation.kt` | 权限人话说明、删除能力、模型强度、cwd 分组与主机摘要的纯函数 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 设置入口、官方/原生 DSH 切换、回退提示与按域分组的设置页 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 42 已创建/释放审批控制器，按服务状态启停并传入页面；54 不改此文件；51 仅补 composer send recorder 与审批回调 |

偏好文件 `ui`、键 `dsh-ui`，值 `official` / `native`；未知值回到 `official`。设置入口在“设置 → 网页与兼容性 → 界面：官方界面 / 原生界面”。

### 42 接线状态（已完成）

审批/工具切片合入后，42 已在四个集成文件中完成以下接线；保留本节作为契约说明：

1. `DshNativeApi.kt`
   - `DshSessionPage` 增加 `timeline: List<DshTimelineItem> = messages.map { DshTimelineItem.Text(it) }`。
   - `parsePage` 读取 `records` 后调用 `DshToolTimeline.parse(records)`，写入 `timeline`；既有 `messages` 契约不变。
2. `DshNativeController.kt`
   - `DshNativeState` 增加 `timeline: List<DshTimelineItem>`；历史成功时 `timeline = page.timeline`，选择/加载/清空时同步清空。
   - 已提供 `suspend fun tool(sessionId: String, callId: String): DshToolItem?`，按审批所属会话查询，不依赖当前选中会话旧快照。
3. `DshNativePage.kt`
   - 增加可选参数：
     ```kotlin
     approvalState: DshApprovalState = DshApprovalState(),
     onApprovalAnswer: (String, DshApprovalOutcome, Boolean) -> Unit = { _, _, _ -> },
     onApprovalRetry: () -> Unit = {},
     onClearRemembered: () -> Unit = {}
     ```
   - 已在 `LazyColumn` 首个 `item` 挂 `DshApprovalPanel(state, selectedId, onAnswer, onRetry, onClearRemembered)`。
   - 时间线 `DshTimelineItem.Text` 渲染 `MessageCard`，`DshTimelineItem.Tool` 渲染 `DshToolCard(tool)`；工具与文本按 seq 交错，pending 消息置后。
4. `MainActivity.kt`
   - 复用同一 `endpointProvider` 创建 `DshApprovalController(endpointProvider, lifecycleScope, toolLookup = { sessionId, callId -> ... })`；`toolLookup` 必须按 `sessionId+callId` 查询，不能用当前选中会话的旧快照。
   - 在原生模式 + 服务 `RUNNING` 时 `start()`；停止/切官方/页面销毁时 `close()`；origin 变化时重建。
   - 收集 `approvalController.state`，把状态与回调传给 `DshNativePage`。
5. 通知点击：42 切换 `selectedId` 后，审批入口窄条/抽屉按当前会话过滤；无需新增通知协议。

临时 WebSocket：`DshWebSocketClient.kt` 只作为 `DshApprovalTransport` 的默认实现保留，待 42 的 `DshWebSocket.kt`/transport 可用后替换；不要再扩展它。

### 46 渲染质量切片（2026-11-10）

- 流式：`assistant-stream` 全文在控制器内按约 32ms 合并，同一帧只发布最后一版；committed/assistant 消息到达时取消待发布增量，避免临时气泡与落盘消息重复。
- 长列表：46 切片曾用末尾渲染窗口（80 项起、每次加 40、最多 400）；该截断已由 2026-11-11 的两段锚定 + 向前分页 + 控制器内存窗口取代，见下节。时间线仍按 `messages/tools` 记忆化，流式文本不重建整段历史。
- 跟随：46 切片在拖动开始时停止跟随、回到底部恢复并显示“↓ 最新”；2026-11-11 改为用户一滚动就冻结锚点、胶囊显示「N 条新消息」，见下节。
- Markdown：标题、段落、引用、分隔线、有序/无序/嵌套/任务列表、带对齐表格、围栏代码块（等宽、横向滚动、长行不换行、可选行号）、行内粗体/斜体/粗斜体/删除线/行内代码/链接。单条消息最多 300 个块、代码 400 行、表格 60 行/12 列，超出明确提示且复制仍用全文。
- 链接：`http/https/mailto/tel` 点击先弹确认框，确认后才调起系统应用；`javascript/data/intent/file/content` 等直接拒绝并给中文原因，附件/相对路径只提示需要工作区授权。
- 图片/附件：Markdown 图片显示占位卡片与中文原因；本地/内嵌图片点击后在对话框按尺寸采样解码，失败显示“图片加载失败：文件不可读、已损坏或格式不支持”；远程图片保持不自动联网。附件链接显示占位卡片，可查看地址/复制，不静默读取工作区文件。
- 发送状态：发送中显示占位气泡与进度圈；失败保留原文、可读原因、“重试/填入输入框/复制内容”；错误 banner 提供中文提示与“复制错误”，不隐藏原始短错误码。
- 审批入口：使用 53 的 `DshApprovalEntryBar` 固定在消息区上方，点击打开 `DshApprovalSheet` 底部抽屉逐条处理；自动滚到底部不影响入口条可见。
- 发送状态：观察输入栏真实 `DshComposerController` 的 sending/error 变化，发送中显示占位，失败保留本次提交原文并提供重试/回填/复制；不再只依赖控制器内部的发送路径。
- 评审修复：图片采样只按原始宽高除以 2 的幂采样（8192→4、32768→16）；新流空串重置在帧合并中保留；发送记录改到 transport 边界；重试绑定失败提交，草稿变化/含附件时禁用。补图片采样、快慢失败、重试边界与跨 start 流式回归。
- 未验证：真实设备上的链接点击、文本选择与链接手势冲突、图片解码内存、长代码/表格滚动、深色/大字体、低端机帧率；审批停靠条与 composer 失败卡片的设备界面未验。

### 聊天列表两段锚定与向前分页（2026-11-11，android_agent-62）

- 结构：消息列表逻辑上分两段——历史段（含锚点行）只向上增长，新消息段只从锚点下方向下增长。跟随状态下锚点与列表尾部重合；用户离开底部后冻结“当时的最后一行”为锚点，流式增量与新消息都落在锚点下方，因此不会改变正在阅读的视口。
- 脱离与恢复：`NestedScrollConnection` 只在 `NestedScrollSource.UserInput` 时冻结锚点（拖动与随后的惯性都不会再被自动贴底拉回）；`isAtBottom` 为真时恢复跟随；未跟随时右下角显示「N 条新消息」（没有新消息时「↓ 最新」），点击回到底部。程序滚动不会误触发脱离。
- 索引恢复：向前分页只在历史段头部插入，窗口化只释放头部行；两者都会改变 LazyColumn 索引。界面记录视口内第一条可定位行（时间线行或锚点）的稳定 key 与相对偏移，在 `historyRevision` 变化后按 **实际渲染 key 顺序**（顶部提示、历史段、锚点分隔行、新消息段）调用 `scrollToItem` 回到同一内容；锚点之后的消息也计入分隔行，不会跳到前一条。用户正在阅读的一屏不会因释放而消失。
- 向前分页：滚到顶部（或点“加载更早历史”）时，用当前窗口最早行 `seq - 1` 作为官方 `session/page` 的 `throughSeq`、`maxMessages=50` 读取一页；`hasMore` 由官方返回。按 `seq` 去重排序，重复页/空页停止续页，失败保留已加载内容并显示中文原因；没有更多时明确显示“已到会话最早一条”。
- 内存窗口：控制器保留最近 500 条消息与 200 张工具卡，超出后只释放视口上方的行；工具与消息共用同一视口边界（消息释放边界之前的工具一并释放），不再在合并阶段按“最新 200 张”截断，否则向前分页返回的更早工具会在合并瞬间消失。释放后重新标记为“还有更早历史”，再次向上翻页会重新读取，页首提示已释放条数。上翻时（视口首行 seq 变小）不释放，避免刚加载的更早页立刻被裁掉；首次布局或向下滚动时才按预算释放。用户在顶部阅读时宁可暂时超出预算，也不释放正在看的内容。
- 幂等与对账：`DshMessageLedger` 以官方 `seq` 为唯一键去重、排序；同一事件重复投递或重连重放不会插入第二条。follow 快照只覆盖自己的 `seq` 区间，保留已加载的更早分页与更新的实时行；重连后第二次 `Connected` 会主动触发一次历史对账（首连仍交给历史页/快照）。
- 纯策略与回归：`DshChatWindow.kt` 提供 `DshChatAnchorPolicy`、`DshChatWindowPolicy`、`DshMessageLedger`；`DshChatFollowState.kt` 提供会话级跟随状态。JVM 覆盖锚点分段与渲染 key 顺序、上翻不跟随、A→B 会话隔离、消息/工具预算边界、只释放视口上方、工具合并不截断、seq 去重、快照对账与重连对账。设备手势、帧率、深色/大字体与真实 DSH 历史未验证。

### 输入栏上方三条停靠条（2026-11-11，android_agent-62 接线）

- 位置：页面纵向顺序为“消息区 `weight(1f)` → `DshDockColumn` → `DshComposerBar`”。原 `SessionStrip` 下方的审批条已移入停靠容器插槽，不再单独渲染，避免出现两条审批条。
- 顺序与折叠：审批 > 目标 > 待办；同屏最多两条（三条齐全时显示审批+目标，待办折叠为“+1”），点“+N”临时铺开、按钮变“收起”；组件按唯一渲染计划遍历，展开时每条只出现一次。
- 数据：目标/待办只来自真实事件投影（`goal/update`/`goal`/`goals/update`/`session/goal`、`todo/update`/`todos/update` 与待办工具调用），经 `DshDockStateMapper` 映射；缺失、空文本或未知状态时保持 `null`，对应停靠条不占高度。目标操作能力在 `goals/*` RPC 接线前保持默认全 false，只展示禁用原因，不伪造成功。
- 输入栏稳定：停靠条高度动画只压缩消息区，输入栏始终是纵向栈最后一项；页面不要在外层再包 `animateContentSize`/`AnimatedVisibility`（布局契约见 `DshDockPlacement`）。纯策略回归覆盖三档折叠/展开不重复、缺失隐藏，以及停靠条高度变化时输入栏顶部不变。
- 兼容：锚定滚动、审批抽屉、发送/停止逻辑未改；审批入口点击仍打开 `DshApprovalSheet`。设备上的高度动画、展开滚动、深色/大字体与 TalkBack 未验证。

### 53 本轮组件与接线 hook（DshNativePage/MainActivity 由集成方接线）

本轮只改审批/工具/计划组件，不修改 `DshNativePage.kt` 与 `MainActivity.kt`。以下 hook 保持源兼容：既有命名参数调用无需改动即可编译；集成方补传新增可选参数即可启用增强。

1. 审批入口：窄条 + 底部抽屉（推荐接线）
   - 输入框上方只放 `DshApprovalEntryBar`，显示“⚑ 待审批 N 项”/高风险数量/记忆数量；点击打开 `DshApprovalSheet`。不要把 `DshApprovalPanel` 直接放进消息流。
   - 页面状态示例：
     ```kotlin
     var showApprovalSheet by rememberSaveable { mutableStateOf(false) }
     DshApprovalEntryBar(
         state = approvalState,
         sessionId = state.selectedId,
         onClick = { showApprovalSheet = true },
         modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
     )
     DshApprovalSheet(
         visible = showApprovalSheet,
         onDismiss = { showApprovalSheet = false },
         state = approvalState,
         sessionId = state.selectedId,
         sessionTitle = state.selectedSession?.displayTitle,
         onAnswer = { eventId, outcome, remember -> approvalController.answer(eventId, outcome, remember) },
         onRetry = approvalController::retry,
         onClearRemembered = approvalController::clearRemembered,
         onForgetRemembered = approvalController::forgetRemembered,
         onDismissNotice = approvalController::dismissNotice
     )
     ```
   - 抽屉逐条显示审批卡；没有“一键全允许”。高风险“允许一次”需卡片内二次确认；拒绝立即生效。批量按“第 i/N 条”编号，勾选“记住”只作用于本卡片本次回答。
   - `DshApprovalPanel` 保留为抽屉内容和旧调用兼容；其新可选参数：`sessionTitle`、`onForgetRemembered`、`onDismissNotice`。
   - `DshApprovalController.forgetRemembered(choice)` 立即撤销单条记忆；之后相同会话/工具/风险的请求不会再自动回答。已经回传官方的结果不能撤回，也不伪造撤回。
   - `DshApprovalState.errorRetryable` 表示当前错误可重试；断线/回答失败始终保留 `pending`，界面显示中文原因和“重试连接”。服务地址变化导致旧审批无法回答时，`notice` 明确告知失效数量，不静默丢弃；`onDismissNotice` 可关闭提示。
   - 记忆的独立入口：`DshApprovalEntryBar` 在无待审批但有记忆时显示“审批记忆 N 项”，点击进入同一抽屉逐条撤销/全部清除。
   - 抽屉内容有受视口约束的滚动容器；打开期间有新审批到达时只更新计数并显示 3 秒轻提示，不自动滚动或跳走当前处理项。

2. 工具卡片 `DshToolCard`
   - 签名不变。`DshToolItem` 新增只读字段：`startedAt`、`finishedAt`、`durationMs`。
   - `DshToolTimeline.parse` 已用 `tool/call.time` 与 `tool/result.time` 填充；只显示真实耗时，进行中/孤儿结果显示“耗时未知”，不伪造。
   - 输出/错误复制、diff 增删行背景色和耗时均由组件内部处理；实时 follow 分批到达时，本 PR 已把 `DshNativeController.mergeTools` 委托给 `DshToolMerge.merge(current, incoming)`，终态保留 `finishedAt` 和已知调用起点；51 复核该跨文件改动。该 helper 同时保留“迟到 RUNNING 不回退终态”的规则。

3. 计划审阅 `DshPlanCard` + `DshPlanParser`
   - `DshPlanParser.extract(records)` 返回 `DshPlanExtraction(plans, planCallIds)`，`latest` 为最新计划。
   - 集成方在历史页/follow 快照中：
     - 把 `planCallIds` 从 `DshToolTimeline.parse(records)` 的结果中剔除，避免计划同时显示为普通工具卡；
     - 不要从普通消息文本中提取/渲染计划；
     - 把 `latest` 放进页面状态（例如 `DshNativeState.plans`），在 `LazyColumn` 中渲染 `DshPlanCard`。
   - 建议调用：
     ```kotlin
     DshPlanCard(
         plan = plan,
         submitting = planReviewSubmitting,
         onApprove = { requestPlanReview(plan.id, approve = true) },
         onRequestChanges = { requestPlanReview(plan.id, approve = false) }
     )
     ```
   - `DshPlanCard` 只在 `plan.review == AWAITING` 时显示“同意执行/要求修改”；不自动批准。审阅回答的官方 RPC/事件字段仍由集成方按真实协议接线，本轮没有新增 RPC。
   - 解析支持计划事件（`plan/update`、`plan/review`、`plan`、`todo/update`、`todos/update`）与计划工具调用（`update_plan`、`plan`、`set_plan`、`plan_update`、`todo_write`、`write_todo`、`todowrite`、`update_todos`）。
   - 若官方计划审阅是 `user-questions/request` 或独立 waterfall，集成方可调用 `DshPlanParser.fromReviewPayload(seq, id, request)`，默认得到 `review=AWAITING`；解析失败时回退官方界面，不猜计划内容。
   - 真实字段以合成回归为准，未在真实 PRoot DSH 验证。

4. 用户提问卡 `DshQuestionCard` + `DshQuestionParser`
   - `DshQuestionParser.parse(eventId, sessionId, request)` 从 `user-questions/request` 的 `questions[]` 解析单选/多选/文本、选项、`allowCustom`、`required`；只保留短文本，不复制原始请求。
   - `DshQuestionDraft` 负责本地选择：单选覆盖、多选切换、文本保存、必填校验；编辑态保留原始空格/换行（只限长度），必填用 `isBlank`，提交边界才 trim；可独立 JVM 回归。
   - 组件调用：
     ```kotlin
     DshQuestionCard(
         request = question,
         onSubmit = null, // 协议未确认时必须为 null，界面显示“回答入口待协议确认”
         submitting = false,
         error = null
     )
     ```
   - 回答 RPC 未在本切片确认；`onSubmit` 为可空接口，未接线时禁止伪造提交。51 接线时若官方协议确认，再把 `onSubmit` 接到真实回答；否则保持只读/待确认。
   - 官方回答字段确认前，不要把 `user-questions/request` 当作普通消息，也不要把它静默回 `next` 后当作已处理。

### 51 接线状态（2026-11-10）

- 已接入 `DshApprovalEntryBar` + `DshApprovalSheet`：入口条固定在消息区上方，点击打开底部抽屉；`onForgetRemembered`/`onDismissNotice` 已从 `MainActivity` 传入。消息流不再直接挂 `DshApprovalPanel`。
- 已用 `DshToolMerge.merge` 替换 `DshNativeController` 私有 `mergeTools`，并补 startedAt/finishedAt 保留回归。
- `DshPlanCard`/`DshPlanParser` 组件随 #47 合入，但计划状态解析与审阅 RPC 本次未接线；仍按组件 hook 等待后续集成。
- 设备上的入口条/抽屉、长历史初始审批、A→B 会话切换与手势未验证。

### 切换语义

- 默认官方界面；不改变现有 WebView 加载、Cookie、同源策略或 DSH 启动参数。
- 切到原生：不停止服务、不重建 DSH、不创建第二个服务；复用同一 `origin` 与同一启动 token。
- 切回官方：仍使用同一个 `DshWebView` 实例；需要时由现有 `connectDsh` 重新挂载，不重启服务。
- 原生列表读取失败（认证、404、格式错误）：立即回退官方界面，并在官方页顶部显示原因和“重试”；用户偏好保持 `native`，不会静默改回官方。
- 通知点击：原生偏好下目标交给原生控制器；冷启动先加载列表再选中，已有原生页直接替换；原生列表失败不消费目标，回退官方后由 WebView 连接完成同一目标；通知点击当前会话会强制重读历史，不复用旧页缓存；只有本次通知已发起并完成刷新才消费目标。
- 服务未运行时原生页不显示；停止服务会清空原生页内存 Cookie；再次启动由新 `origin` 自动重新认证。
- 原生页通过官方 `session/prompt`/`session/cancel` 发送与停止，附件走官方上传/引用；不直接写 `userdata/dsh`。审批只回答官方已下发的 `approval/request`，不主动创建审批。
- “记住本次运行”不是官方持久授权：只在内存中按 `sessionId + toolName + 风险等级` 复用，关闭原生页/服务停止即清空；高风险与不同工具不会被低风险记忆自动放行。

## 4.1 原生会话管理与设置（android_agent-54）

原生会话管理页把官方 `session/*`、`workspace/archiveSession`、`permissionPresets/catalog` 与 `commands/execute "/permission <preset>"` 组合成一个独立界面；所有会话数据仍来自当前 DSH 实例，不建立第二套会话库。

- 列表：首次读取显示骨架加载态；读取失败且无旧数据时显示可重试错误态；有旧数据时保留列表并提示刷新失败。搜索对标题/会话 ID 即时过滤，消息内容搜索仍需显式点击搜索图标。未读与置顶都是本机偏好：未读按本机最后查看的 `updatedAt` 计算，置顶只改变本页顺序并保存在应用私有偏好中，不写回官方会话。运行中标识来自官方 `running` 字段。
- 分组与多选：按官方 `cwd` 把会话归入文件夹（空目录为“未分组”），组内保持置顶/更新时间顺序；顶部“选择”进入多选，可批量归档并显示逐个进度。官方没有会话硬删除端点，批量删除只显示不可用说明，不伪造删除。
- 主机切换：列表头部 chip 显示当前主机与状态（本机运行中/未运行、远程已连接/连接中/未保存配置），点开底部 sheet 可在“本机 + 当前已保存远程主机”之间切换；切换只改变会话管理页的数据源，不删除本地/远程会话或项目文件。当前运行时只保存一个远程配置，多主机列表需共享 runtime 类型扩展。
- 刷新：已有列表时使用下拉刷新，不再清空列表；首次读取才显示整页加载态。
- 操作：新建、重命名、归档都有确认或明确的输入/后果说明；归档运行中会话会请求官方先停止当前回合、子代理、后台任务与提醒。官方当前没有会话硬删除端点，详情页只展示“删除会话（官方接口不支持）”，不提供假按钮；归档也没有本页撤销按钮。
- 详情分页：概览、进度、产物、文件四个 tab。概览保留真实会话信息与设置入口；进度/产物/文件中的待办、目标、产物预览和文件树需要 `goals/*`、`artifacts`、`workspaceFiles/*` 契约，当前未接入，因此明确显示“暂不支持”并提供真实工作目录复制，不生成假进度、假产物或假文件树。
- 模型与推理强度：展示官方模型目录、当前选择、模型默认推理强度和每个模型的强度选项；切换失败保留原选择并在面板内显示中文原因（如“所选模型不可用”）。
- 权限模式：标准预设使用人话说明，并同时展示官方说明；`custom` 只展示、不能切换。`danger-full-access` 需要二次确认对话框、逐条风险说明和勾选确认后才提交，Tanuki 不会自动切换。
- 设置：按「外观与语言 / 通知与后台 / 存储与空间 / 引擎与更新 / 远程主机 / 关于与诊断」六域分组；未接入的手动主题切换、语言切换、独立字号等明确写“暂不支持”，通知与存储沿用真实能力。

## 5. 安全边界

- 启动 token 只从 `RuntimeManager.authenticatedUrl` 读取，只用于当前精确 origin 的 `/connect`；不写偏好、日志、状态或错误文本。审批流与列表各自换取 API Cookie，不共享内存对象。
- 认证网络 I/O 不持对象 monitor；`reset()/close()` 只递增代次并清状态，主线程不会等待 `/connect` 超时；旧请求不能重新发布 Cookie。
- API Cookie 只保存在 `DshNativeClient` 内存中；请求前校验 origin 为 `http://127.0.0.1:<port>`，且只拼 `/api/<method>`；不跟随重定向。
- `/connect` 不跟随 302；只从 `Set-Cookie` 取一个 `name=value`，拒绝控制字符。
- 业务错误只显示短错误码；其他异常经 `Redact.text` 截断；`DshEndpoint.toString()` 显式脱敏 launch URL，避免误日志带出 token。
- 不新增 JavaScript Bridge、不扩大 WebView origin、不把 API Cookie 交给网页脚本；原生页不持有 WebView。
- 审批 WebSocket 只连 `http://127.0.0.1:<port>/api/remote.mux`，不跟随重定向、不带 `Origin`；握手校验 `Sec-WebSocket-Accept`，客户端帧全部掩码，拒绝服务端掩码帧，单帧/分片消息上限 1 MiB；握手阶段有总 deadline（默认 10 秒）且先发布可关闭 socket，完成后才切长期流；`error` 帧只显示短错误码。
- 本地 DSH 重启、远程隧道换端口或安全模式切换会改变 origin：审批控制器检测到 origin 变化会关闭旧连接、清空旧待审批并重建，避免把回答发到错误端点。
- 请求不带 `Origin` 头；与官方“工具客户端”语义一致。

## 6. 验证

46 渲染质量切片（2026-11-10，基线 `origin/main=4f2a3717`，非设备）新跑：

- `:app:compileModernDebugKotlin -x buildNativeRuntime`：BUILD SUCCESSFUL。
- 定向 `DshMarkdownTest` 15 项、`DshChatRenderTest` 19 项、`DshNativeControllerTest` 22 项：56 tests / 0 failures / 0 errors / 0 skipped。
- 完整 `:app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime`：BUILD SUCCESSFUL，**981 tests / 104 个结果文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 103 warnings / 1 hint**。
- 覆盖：窗口/底部判定、流式累计/增量/去重/重置、链接协议拒绝、图片/附件分类、错误分类、代码/表格上限、Markdown 块与行内语法、控制器按帧合并/committed 取消/失败重试。
- 未执行：真实 Android 设备、PRoot DSH、仪器测试、assemble；JVM 与 lint 不能替代设备验收。

本轮（android_agent-42，合并 `main=b075725`，含 #34/#35/#36/#37/#38/#41/#42/#43）新跑：

- `:app:compileModernDebugKotlin -x buildNativeRuntime`：BUILD SUCCESSFUL。
- 本轮定向 `DshNativeLiveTest`/`DshApprovalRoutingTest`/`DshNativeControllerTest`：29 tests / 0 failures / 0 errors / 0 skipped。
- 完整 `:app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime`：BUILD SUCCESSFUL（1m54s），**794 tests / 0 failures / 0 errors / 0 skipped**（90 个结果文件）。
- `:app:lintModernDebug -x buildNativeRuntime`：BUILD SUCCESSFUL，0 errors / 101 warnings / 1 hint（既有弃用/可读性告警）。
- 42 新增/覆盖：mux/`$events`/follow 协议、发送/停止/错误、发送 A→B 代次、WebSocket 空闲取消、Markdown、composer 客户端/协议/控制器、审批/工具事件与 `tools` 合并。
- 审批路由：聊天 Peer 的 `$events` 保留状态通知，收到 waterfall 用该连接的固定 Cookie/clientId 回 `next`；唯一 UI 与回答入口是 `DshApprovalPanel`/独立审批 Peer。转交 HTTP/业务失败或未收到 ready 时先关闭聊天 mux 释放回答者，再显示中文错误；401 不换 Cookie 重试旧事件。取消会中止转交 HTTP 并传播，不伪装成失败。
- 工具时间线：迟到历史页与实时 `tool/result` 并发时按 callId 非回退合并，已收到成功/失败终态不会被旧页 RUNNING 覆盖。
- 路由回归包含真实 loopback HTTP/WS 的两个 answer Peer：聊天先就绪、审批先就绪、聊天断开后重新认证；检查 waterfall 的 Cookie/clientId/所属 Peer 一致，审批面板可见并能回答，聊天状态通知继续送达。
- 45 审批/工具：`DshApprovalProtocolTest`、`DshToolTimelineTest`、`DshWebSocketTest`、`DshApprovalSessionTest`、`DshApprovalControllerTest` 随合并通过。
- #43 composer：附件会话 token、发送快照保留后续编辑、批量读取预算、拖入元数据、图片/文件名额等回归随 main 合并通过；#40 不再保留重复副本。

未验证：

- 真实 Android 设备/模拟器上的原生页布局、暗色模式、大字体、长参数/长 diff、输出折叠与滚动、输入法/附件/图片、Markdown 长表格。
- 真实 PRoot 内 DSH `0.2.1-rc.5` 的 `/connect`（answer Peer）、`/api/remote.mux` WebSocket、`session/follow`、审批 waterfall、`$events/result`、401 Cookie 失效、服务重启与安全模式 rescue。
- 远程 SSH 隧道后的审批/实时流；通知点击后待审批过滤；用户提问的完整回答。
- Android 仪器、真机、assemble 与合并/发布均未执行。
