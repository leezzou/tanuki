# 原生界面与官方 DSH 界面切换

本文件记录“官方界面 / 原生界面”开关的接口发现、已实现最小切片、回退与安全边界。默认始终是官方界面；原生界面是可切换的交互层，复用官方 Typert Remote 读写会话；不修改官方 npm 包、DSH 数据目录、会话内容或 WebView 安全策略。

导航、适配、三态、无障碍与冷启动的通用组件和接线清单见 [原生界面导航、适配与可用性打磨](NATIVE-UI-POLISH.md)。

## 1. 结论

官方 DSH `0.2.1-rc.5` 没有 REST 会话列表端点，也没有 SSE。会话、消息、发送与审批都通过 **Typert Remote** 暴露：

- 一元调用：`POST /api/<method>`，JSON 信封，需签名 Cookie。
- 实时流：`/api/remote.mux` WebSocket。
- 审批/提问：Agent-scoped waterfall 事件，经 WebSocket 的 `$events` 通道回答。

原生页现已覆盖：官方会话列表/切换、历史文本与工具事件、`session/follow` 实时流、`session/prompt` 发送、`session/cancel` 停止、输入/附件、Markdown、审批面板与工具卡片。官方网页版始终保留为可切换、可回退入口；用户提问仍只读展示并跳官方处理。

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
| `session/page` | 读取一段历史事件；`records[].event` 含 `user/message`、`assistant/message` 等 | 使用；只提取文本，`maxMessages=120` |
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
- 其他事件（`turn/end`、未知事件）跳过；图片、附件、富媒体不显示。
- 空文本、损坏记录跳过；不生成占位消息。
- 单条消息上限 12,000 字符，响应上限 2 MiB。

## 4. 应用内实现

| 文件 | 作用 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshUiMode.kt` | 偏好枚举与纯策略：默认官方；只有“用户选原生 + 服务运行 + 无回退原因”才显示原生 |
| `app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt` | 42 已接入 `DshSessionPage.timeline`、follow 工具事件与 `DshTransport`；聊天/流式/审批状态共用 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt` | 42 已增加 `tools` 状态、`tool(sessionId,callId)` 与工具结果合并 |
| `app/src/main/kotlin/com/tanuki/web/DshApprovalProtocol.kt` | 审批帧/回答信封/风险/本次运行记忆的纯协议 |
| `app/src/main/kotlin/com/tanuki/web/DshWebSocketClient.kt` | **临时实现，待收敛到 42 transport**：最小 RFC6455 客户端，loopback、Cookie、Accept 校验、掩码、握手超时与大小上限 |
| `app/src/main/kotlin/com/tanuki/web/DshApprovalSession.kt` | `/connect` 认证、`$events` 流、`$events/result` 回答、401 断开重连/独立 Peer |
| `app/src/main/kotlin/com/tanuki/web/DshToolTimeline.kt` | `tool/call`/`tool/result` 时间线、命令/diff/文件变更解析 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalController.kt` | 审批状态机、连接生命周期、允许/拒绝/记住本次运行 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalPanel.kt` | 审批卡片：来源、工具名、风险等级、参数摘要/长参数折叠、逐条编号、高风险二次确认、允许/拒绝/记住；记忆列表可逐条撤销/全部清除 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalSheet.kt` | 审批入口窄条 `DshApprovalEntryBar` 与底部抽屉 `DshApprovalSheet`；窄条只显示数量和风险，不把待审批卡片散落在消息流 |
| `app/src/main/kotlin/com/tanuki/ui/DshToolCard.kt` | 工具卡片：运行/成功/失败、耗时、命令与 cwd、文件变更、增删行配色 diff、折叠输出与复制 |
| `app/src/main/kotlin/com/tanuki/web/DshPlan.kt` | 计划步骤/审阅状态模型；保守解析计划事件与计划工具调用，返回计划与需从普通工具卡剔除的 callId |
| `app/src/main/kotlin/com/tanuki/ui/DshPlanCard.kt` | 计划卡片：多步骤状态、进度条、当前步骤、待审阅时“同意执行/要求修改”；不复用普通消息气泡 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 42 已挂载 `DshApprovalPanel`、`DshToolCard`、输入栏与 Markdown 消息 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerController.kt` | 会话列表状态机、搜索/置顶/未读、单个与批量归档、写操作与中文错误 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerPage.kt` | 按 cwd 分组的会话列表、多选批量归档、主机切换、详情分页、模型/权限面板与确认交互 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerActivity.kt` | 独立会话管理页入口、本机未读/置顶偏好与本机/远程主机切换 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionPresentation.kt` | 权限人话说明、删除能力、模型强度、cwd 分组与主机摘要的纯函数 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 设置入口、官方/原生 DSH 切换、回退提示与按域分组的设置页 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 42 已创建/释放审批控制器，按服务状态启停并传入页面；54 不改此文件 |

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

2. 工具卡片 `DshToolCard`
   - 签名不变。`DshToolItem` 新增只读字段：`startedAt`、`finishedAt`、`durationMs`。
   - `DshToolTimeline.parse` 已用 `tool/call.time` 与 `tool/result.time` 填充；只显示真实耗时，进行中/孤儿结果显示“耗时未知”，不伪造。
   - 输出/错误复制、diff 增删行背景色和耗时均由组件内部处理；实时 follow 分批到达时，51 必须用 `DshToolMerge.merge(current, incoming)` 替换 `DshNativeController` 的私有 `mergeTools`，否则终态会丢失 `finishedAt`、完成后仍显示“耗时未知”。该 helper 同时保留“迟到 RUNNING 不回退终态”的规则。

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
