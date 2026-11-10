# 原生界面与官方 DSH 界面切换

本文件记录“官方界面 / 原生界面”开关的接口发现、已实现最小切片、回退与安全边界。默认始终是官方界面；原生界面是可切换的交互层，复用官方 Typert Remote 读写会话；不修改官方 npm 包、DSH 数据目录、会话内容或 WebView 安全策略。

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
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalPanel.kt` | 审批卡片：工具名、参数、风险、允许/拒绝/记住 |
| `app/src/main/kotlin/com/tanuki/ui/DshToolCard.kt` | 工具卡片：状态、命令、diff、文件变更、折叠输出 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 42 已挂载 `DshApprovalPanel`、`DshToolCard`、输入栏与 Markdown 消息 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | **android_agent-44 独占**；设置开关、DSH 页原生/官方切换、回退提示与“重试” |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 42 已创建/释放审批控制器，按服务状态启停并传入页面 |

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
5. 通知点击：42 切换 `selectedId` 后，审批面板自然按当前会话过滤；无需新增通知协议。

临时 WebSocket：`DshWebSocketClient.kt` 只作为 `DshApprovalTransport` 的默认实现保留，待 42 的 `DshWebSocket.kt`/transport 可用后替换；不要再扩展它。

### 切换语义

- 默认官方界面；不改变现有 WebView 加载、Cookie、同源策略或 DSH 启动参数。
- 切到原生：不停止服务、不重建 DSH、不创建第二个服务；复用同一 `origin` 与同一启动 token。
- 切回官方：仍使用同一个 `DshWebView` 实例；需要时由现有 `connectDsh` 重新挂载，不重启服务。
- 原生列表读取失败（认证、404、格式错误）：立即回退官方界面，并在官方页顶部显示原因和“重试”；用户偏好保持 `native`，不会静默改回官方。
- 通知点击：原生偏好下目标交给原生控制器；冷启动先加载列表再选中，已有原生页直接替换；原生列表失败不消费目标，回退官方后由 WebView 连接完成同一目标；通知点击当前会话会强制重读历史，不复用旧页缓存；只有本次通知已发起并完成刷新才消费目标。
- 服务未运行时原生页不显示；停止服务会清空原生页内存 Cookie；再次启动由新 `origin` 自动重新认证。
- 原生页通过官方 `session/prompt`/`session/cancel` 发送与停止，附件走官方上传/引用；不直接写 `userdata/dsh`。审批只回答官方已下发的 `approval/request`，不主动创建审批。
- “记住本次运行”不是官方持久授权：只在内存中按 `sessionId + toolName + 风险等级` 复用，关闭原生页/服务停止即清空；高风险与不同工具不会被低风险记忆自动放行。

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
- `:app:testModernDebugUnitTest -x buildNativeRuntime`：BUILD SUCCESSFUL，**785 tests / 0 failures / 0 errors / 0 skipped**（89 个结果文件）。
- `:app:lintModernDebug -x buildNativeRuntime`：BUILD SUCCESSFUL，0 errors / 101 warnings / 1 hint（既有弃用/可读性告警）。
- 42 新增/覆盖：mux/`$events`/follow 协议、发送/停止/错误、发送 A→B 代次、WebSocket 空闲取消、Markdown、composer 客户端/协议/控制器、审批/工具事件与 `tools` 合并。
- 审批路由：聊天 Peer 的 `$events` 只保留状态通知，收到 `approval/request` 明确回 `next`，不展示/回答；唯一 UI 与回答入口是 `DshApprovalPanel`/独立审批 Peer。首次连接与重连后均回 `next` 的回归已加入。
- 45 审批/工具：`DshApprovalProtocolTest`、`DshToolTimelineTest`、`DshWebSocketTest`、`DshApprovalSessionTest`、`DshApprovalControllerTest` 随合并通过。
- #43 composer：附件会话 token、发送快照保留后续编辑、批量读取预算、拖入元数据、图片/文件名额等回归随 main 合并通过；#40 不再保留重复副本。

未验证：

- 真实 Android 设备/模拟器上的原生页布局、暗色模式、大字体、长参数/长 diff、输出折叠与滚动、输入法/附件/图片、Markdown 长表格。
- 真实 PRoot 内 DSH `0.2.1-rc.5` 的 `/connect`（answer Peer）、`/api/remote.mux` WebSocket、`session/follow`、审批 waterfall、`$events/result`、401 Cookie 失效、服务重启与安全模式 rescue。
- 远程 SSH 隧道后的审批/实时流；通知点击后待审批过滤；用户提问的完整回答。
- Android 仪器、真机、assemble 与合并/发布均未执行。
