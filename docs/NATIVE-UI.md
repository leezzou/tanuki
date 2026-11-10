# 原生界面与官方 DSH 界面切换

本文件记录“官方界面 / 原生界面”开关的官方协议依据、可交互切片、回退与安全边界。默认始终是官方界面；原生界面只复用官方 DSH 的 Typert Remote 与会话数据，不修改官方 npm 包、DSH 数据目录、会话内容或 WebView 安全策略。

## 1. 结论

官方 DSH `0.2.1-rc.5` 没有 REST 会话端点，也没有 SSE。会话、发送、停止、实时事件与审批都通过 **Typert Remote** 暴露：

- 一元调用：`POST /api/<method>`，JSON 信封，需签名 Cookie。
- 实时流：`/api/remote.mux` WebSocket，逻辑流用 `open/item/end/cancel` 文本帧。
- 审批/提问：Agent-scoped waterfall，经 `$events` 转发，用 `$events/result` 回答。

本切片已实现：会话列表、历史文本、发送、停止、实时增量文本、运行状态、错误提示、审批显示与“允许一次/拒绝”，以及提问的只读展示 + 跳官方处理。附件、模型选择、提问多选回答、自动重连仍未实现。

## 2. 认证与传输

| 项目 | 官方行为 | Tanuki 使用方式 |
|---|---|---|
| 启动地址 | `dsh web: http://127.0.0.1:<port>/?token=...` | `RuntimeManager.captureUrl()` 原样保留在内存，不写日志/状态 |
| 浏览器登录 | `GET /` 带 token，写签名 Cookie 后重定向清理 token | 管理 WebView 继续使用，未改 |
| API 登录 | `GET /connect?token=...&interactions=answer`，返回 302、API Cookie | 原生客户端用同一启动 token 换取独立 API Peer；`answer` 用于审批 |
| 一元 RPC | `POST /api/<method>` | 只向当前精确 loopback origin 发送 |
| 请求信封 | `{"type":"client-request","rpcId":"...","method":"<method>","payload":{"args":{...}}}` | `DshApiProtocol.requestBody()` |
| 响应信封 | `{"type":"server-response","rpcId":"...","result":{"ok":true,"value":...}}` 或 `{"ok":false,"error":{"code":...}}` | 校验 `rpcId`；业务错误只保留短错误码 |
| 实时流 | `GET /api/remote.mux` Upgrade 到 WebSocket | `DshWebSocketMuxSocket` 最小 RFC6455 客户端 |
| Cookie 绑定 | 签名 Cookie 绑定 host + port；关闭唯一 mux socket 会销毁该 Peer | 客户端按 `origin + launchUrl` 绑定，socket 关闭即丢 Cookie |

`interactions=answer` 的 Peer 可以同时做一元调用与 `$events` waterfall；`$events`/`$events/result` 在 `interactions=none` 下会被官方拒绝。原生页不另开 `none` 连接，避免同一服务出现两套凭据。

## 3. 官方端点与本切片

| 官方 method | 用途 | 本切片 |
|---|---|---|
| `session/list` | 读取可见会话摘要；标题在 `projections.values.title`，事件切点在 `projections.asOfSeq` | 使用 |
| `session/projections` | 读取单会话 `kind=sequenced` 的 `asOfSeq` | 使用；作为 `session/page` 的 `throughSeq` |
| `session/page` | 读取一段历史事件 | 使用；文本、`maxMessages=120` |
| `session/prompt` | 发送提示词；要求 `requestId/sessionId/mode/content` | 使用；`mode=queue`，只发文本 |
| `session/cancel` | 取消当前活动回合，不清空待处理队列 | 使用 |
| `$events` | 转发 Agent 事件与审批 waterfall | 使用；ready/waterfall/cancel/emit |
| `$events/result` | 回答指定 `eventId` 的 waterfall | 使用；审批回 `allowed-once`/`rejected` |
| `session/follow` | 从开局或恢复游标跟随会话日志 | 使用；`assistantStream=true` |
| `session/control` | Host 全局实时状态 | 未使用；会话状态已由 `session/list` 与 `$events` 覆盖 |
| `session/create` / `rename` / `selectModel` / `updateQueue` | 会话管理 | 未使用 |
| `session/attachment` | 附件读取 | 未使用 |
| `skills/list` | 技能目录 | 未使用 |

### 3.1 文本与流式提取

- `user/message`：读 `data.content` 中 `type=text` 的块；只保留 `source.kind=user`。
- `assistant/message`：读 `data.message.content` 中 `type=text` 的块。
- 只保留官方 append-origin 事件（`surfaceOp === "append"`）；`surfaceOp=replace` 的模型上下文替换副本、compact-checkpoint 不显示。
- `assistant-stream` 只聚合 `text-delta`；`reasoning-delta`、`tool-call-delta` 不进入聊天正文。快照里的 compact `text-chunks`/`chunk` 也会展开成临时文本。
- 单条消息上限 12,000 字符，响应上限 2 MiB；空文本、损坏记录跳过，不生成占位消息。

### 3.2 审批与提问

- `approval/request`：只取 `eventId`、`agentId`（会话 id）、`toolName`、`displayReason`/`reason`；不读工具名之外的参数。界面提供“允许一次 / 拒绝 / 去官方处理”。
- 回答信封：`payload.args = {clientId, eventId, outcome:{kind:"result", value:"allowed-once"|"rejected"}}`；`clientId` 来自 `$events` 的 `ready`。
- `user-questions/request`：只读展示首条问题与选项，明确“去官方处理”；不伪造多选答案或 `userQuestions.answer`。
- 只有用户点击才发送裁决；连接断开、事件被 `cancel` 或页面切回官方时，原生不会自动允许/拒绝。
- 同一审批若官方 WebView 也在线，两边都会收到；原生回答后官方应收到 `cancel`，官方回答后原生卡片也会移除。页面切回官方会关闭原生 mux，未决事项交给官方页面。

## 4. 应用内实现

| 文件 | 作用 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshUiMode.kt` | 偏好枚举与纯策略：默认官方；只有“用户选原生 + 服务运行 + 无回退原因”才显示原生 |
| `app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt` | 官方 unary 协议、Cookie 获取、会话/历史解析、`session/prompt|cancel`、审批应答、实时流编排 |
| `app/src/main/kotlin/com/tanuki/web/DshMuxProtocol.kt` | mux 帧、`$events`、`session/follow`、文本/审批解析与流式聚合 |
| `app/src/main/kotlin/com/tanuki/web/DshWebSocket.kt` | 最小 RFC6455 客户端（握手、掩码、分片、ping/pong、close） |
| `app/src/main/kotlin/com/tanuki/web/DshLive.kt` | 实时事件模型与 `DshChatSource` 接口 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt` | 原生页状态机：选择、发送占位、停止、实时快照、审批、错误与回退 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 原生 Compose 页：会话条、消息/流式气泡、审批卡、输入栏、停止按钮 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 设置开关、原生/官方切换、回退提示与“重试” |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 偏好持久化、控制器接线、通知目标、同一 token/同一 DSH 进程 |

偏好文件 `ui`、键 `dsh-ui`，值 `official` / `native`；未知值回到 `official`。设置入口在“设置 → 网页与兼容性 → 界面：官方界面 / 原生界面”。

### 切换语义

- 默认官方界面；不改变现有 WebView 加载、Cookie、同源策略或 DSH 启动参数。
- 切到原生：不停止服务、不重建 DSH、不创建第二个服务；复用同一 `origin` 与同一启动 token。
- 切回官方：仍使用同一个 `DshWebView` 实例；需要时由现有 `connectDsh` 重新挂载，不重启服务。
- 原生列表读取失败（认证、404、格式错误）：立即回退官方界面，并在官方页顶部显示原因和“重试”；用户偏好保持 `native`，不会静默改回官方。
- 实时流失败或单次发送/停止/审批失败：只显示错误提示，保留已加载历史；发送与停止仍可走一元接口；审批卡片提供“去官方处理”。
- 通知点击：原生偏好下目标交给原生控制器；冷启动先加载列表再选中，已有原生页直接替换并重连实时流；原生列表失败不消费目标，回退官方后由 WebView 连接完成同一目标；通知点击当前会话会强制重读历史并重连。
- 服务未运行时原生页不显示；停止服务会取消实时任务并清空原生状态，再次启动由新 `origin` 自动重新认证。
- 原生页不写 `userdata/dsh`，不调用 `session/create`/`rename`/`selectModel`/`updateQueue`，不修改官方数据。

## 5. 安全边界

- 启动 token 只从 `RuntimeManager.authenticatedUrl` 读取，只用于当前精确 origin 的 `/connect`；不写偏好、日志、状态或错误文本。
- 认证网络 I/O 不持对象 monitor；`reset()/close()` 只短暂同步并清状态，主线程不会等待 `/connect` 超时；旧请求不能重新发布 Cookie。
- API Cookie 只保存在 `DshNativeClient` 内存中；请求前校验 origin 为 `http://127.0.0.1:<port>`，且只拼 `/api/<method>`；不跟随重定向。
- `/connect` 不跟随 302；只从 `Set-Cookie` 取一个 `name=value`，拒绝控制字符。
- WebSocket 只连当前精确 loopback origin，不发送 `Origin`；客户端帧强制掩码，服务端帧必须未掩码；拒绝二进制帧、扩展位与超 2 MiB 消息。
- 业务错误只显示短错误码；其他异常经 `Redact.text` 截断；`DshEndpoint.toString()` 显式脱敏 launch URL，避免误日志带出 token。
- 审批只展示工具名与原因，不读取/记录工具参数；只有用户显式点击才回答。提问不回传答案，跳官方处理。
- 不新增 JavaScript Bridge、不扩大 WebView origin、不把 API Cookie 交给网页脚本；原生页不持有 WebView。
- 不自动切换 DSH `danger-full-access`，不关闭审批/TLS/哈希校验。

## 6. 验证

本轮新跑：

- `:app:compileModernDebugKotlin`：BUILD SUCCESSFUL。
- 目标回归：`DshMuxProtocolTest`、`DshNativeLiveTest`、`DshNativeControllerTest`、`DshNativeApiTest`、`DshUiSwitchTest` 全部通过（见 [测试报告](TEST-REPORT.md)）。
- 完整 `:app:testModernDebugUnitTest` 与 `:app:lintModernDebug`：BUILD SUCCESSFUL；合并最新 `main=533eff4` 后 581 tests / 0 failures / 0 errors / 0 skipped，lint 0 errors / 94 warnings / 1 hint。
- 新增覆盖：mux 帧合法性/请求体、`$events` ready/waterfall/cancel/status、审批字段与不泄露参数、`session/follow` 快照过滤与流式 text-delta、WebSocket 握手/掩码、发送/取消/审批 HTTP 路径、控制器发送占位/失败/停止/实时/审批/回退。
- 未在真实 Android 设备/PRoot DSH 上验证：`/connect` Cookie、WebSocket Upgrade、真实 `$events`/`session/follow` 帧、审批 waterfall 的实际到达与回答、发送/停止的设备交互、通知点击、远程主机隧道、安全模式 rescue、暗色/长文本/大字体/输入法。
- 未运行 assemble、Android 仪器测试、真机与发布流程；编译、JVM 与 lint 不能替代设备验收。
- 未实现：附件、图片、模型选择、会话创建/重命名、提问多选回答、自动重连/心跳超时策略、后台保活、`session/control` 全局状态。

## 7. 后续

1. 设备/模拟器验收：启动既有测试 AVD，确认 DSH 运行、发送/流式/停止、审批允许/拒绝与切官方回退；核对 logcat 无 token/Cookie/消息正文。
2. 若官方升级调整 `$events`、`session/follow` 或审批字段：先更新 [INTERFACES](INTERFACES.md) 与本文件，再改解析与回归。
3. 自动重连、多会话全局审批、提问多选回答、附件与模型选择另立切片，不在本 PR 扩大范围。
