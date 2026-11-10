# 原生界面与官方 DSH 界面切换

本文件记录“官方界面 / 原生界面”开关的接口发现、已实现最小切片、回退与安全边界。默认始终是官方界面；原生界面只是可切换的只读展示层，不修改官方 npm 包、DSH 数据目录、会话内容或 WebView 安全策略。

## 1. 结论

官方 DSH `0.2.1-rc.5` 没有 REST 会话列表端点，也没有 SSE。会话、消息、发送与审批都通过 **Typert Remote** 暴露：

- 一元调用：`POST /api/<method>`，JSON 信封，需签名 Cookie。
- 实时流：`/api/remote.mux` WebSocket。
- 审批/提问：Agent-scoped waterfall 事件，经 WebSocket 的 `$events` 通道回答。

因此原生切片在只读历史之外，新增官方 WebSocket 审批通道与工具卡片：**官方会话列表 + 历史文本/工具事件 + 审批 waterfall**。聊天发送、实时跟随和用户提问仍没有可靠的一元端点，未做假输入框或假审批按钮。

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
| `app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt` | 官方 unary 协议、Cookie 获取、会话/切点/历史解析、`DshNativeClient` |
| `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt` | 原生页状态机；列表失败回退，历史页失败只影响当前会话 |
| `app/src/main/kotlin/com/tanuki/web/DshApprovalProtocol.kt` | 审批帧/回答信封/风险/本次运行记忆的纯协议 |
| `app/src/main/kotlin/com/tanuki/web/DshWebSocketClient.kt` | 最小 RFC6455 客户端：loopback、Cookie、Accept 校验、掩码与大小上限 |
| `app/src/main/kotlin/com/tanuki/web/DshApprovalSession.kt` | `/connect` 认证、`$events` 流、`$events/result` 回答、401 重认证 |
| `app/src/main/kotlin/com/tanuki/web/DshToolTimeline.kt` | `tool/call`/`tool/result` 时间线、命令/diff/文件变更解析 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalController.kt` | 审批状态机、连接生命周期、允许/拒绝/记住本次运行 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalPanel.kt` | 审批卡片：工具名、参数、风险、允许/拒绝/记住 |
| `app/src/main/kotlin/com/tanuki/ui/DshToolCard.kt` | 工具卡片：状态、命令、diff、文件变更、折叠输出 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 原生页：会话条 + 文本/工具时间线 + 审批面板 + 空态/错误态 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 设置开关、DSH 页原生/官方切换、回退提示与“重试” |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 偏好持久化、控制器接线、同一 token/同一 DSH 进程 |

偏好文件 `ui`、键 `dsh-ui`，值 `official` / `native`；未知值回到 `official`。设置入口在“设置 → 网页与兼容性 → 界面：官方界面 / 原生界面”。

### 切换语义

- 默认官方界面；不改变现有 WebView 加载、Cookie、同源策略或 DSH 启动参数。
- 切到原生：不停止服务、不重建 DSH、不创建第二个服务；复用同一 `origin` 与同一启动 token。
- 切回官方：仍使用同一个 `DshWebView` 实例；需要时由现有 `connectDsh` 重新挂载，不重启服务。
- 原生列表读取失败（认证、404、格式错误）：立即回退官方界面，并在官方页顶部显示原因和“重试”；用户偏好保持 `native`，不会静默改回官方。
- 通知点击：原生偏好下目标交给原生控制器；冷启动先加载列表再选中，已有原生页直接替换；原生列表失败不消费目标，回退官方后由 WebView 连接完成同一目标；通知点击当前会话会强制重读历史，不复用旧页缓存；只有本次通知已发起并完成刷新才消费目标。
- 服务未运行时原生页不显示；停止服务会清空原生页内存 Cookie；再次启动由新 `origin` 自动重新认证。
- 原生页不调用 `session/prompt`、`session/cancel`、附件或文件写入，不修改 `userdata/dsh`；审批只回答官方已下发的 `approval/request`，不主动创建审批。
- “记住本次运行”不是官方持久授权：只在内存中按 `sessionId + toolName + 风险等级` 复用，关闭原生页/服务停止即清空；高风险与不同工具不会被低风险记忆自动放行。

## 5. 安全边界

- 启动 token 只从 `RuntimeManager.authenticatedUrl` 读取，只用于当前精确 origin 的 `/connect`；不写偏好、日志、状态或错误文本。审批流与列表各自换取 API Cookie，不共享内存对象。
- 认证网络 I/O 不持对象 monitor；`reset()/close()` 只递增代次并清状态，主线程不会等待 `/connect` 超时；旧请求不能重新发布 Cookie。
- API Cookie 只保存在 `DshNativeClient` 内存中；请求前校验 origin 为 `http://127.0.0.1:<port>`，且只拼 `/api/<method>`；不跟随重定向。
- `/connect` 不跟随 302；只从 `Set-Cookie` 取一个 `name=value`，拒绝控制字符。
- 业务错误只显示短错误码；其他异常经 `Redact.text` 截断；`DshEndpoint.toString()` 显式脱敏 launch URL，避免误日志带出 token。
- 不新增 JavaScript Bridge、不扩大 WebView origin、不把 API Cookie 交给网页脚本；原生页不持有 WebView。
- 审批 WebSocket 只连 `http://127.0.0.1:<port>/api/remote.mux`，不跟随重定向、不带 `Origin`；握手校验 `Sec-WebSocket-Accept`，客户端帧全部掩码，拒绝服务端掩码帧，单帧/分片消息上限 1 MiB；`error` 帧只显示短错误码。
- 本地 DSH 重启、远程隧道换端口或安全模式切换会改变 origin：审批控制器检测到 origin 变化会关闭旧连接、清空旧待审批并重建，避免把回答发到错误端点。
- 请求不带 `Origin` 头；与官方“工具客户端”语义一致。

## 6. 验证

本轮（android_agent-45，基线 `origin/main=0555c3c`）新跑：

- `:app:compileModernDebugKotlin -x buildNativeRuntime`：BUILD SUCCESSFUL。
- `:app:testModernDebugUnitTest -x buildNativeRuntime`：BUILD SUCCESSFUL，**592 tests / 0 failures / 0 errors / 0 skipped**（72 个结果文件；基线 535 + 本切片 34 + 合入 PR #34 的 23）。
- `:app:lintModernDebug -x buildNativeRuntime`：BUILD SUCCESSFUL，0 errors / 94 warnings / 1 hint（既有弃用/可读性告警）。
- `:app:assembleModernDebug -x buildNativeRuntime`：BUILD SUCCESSFUL（仅打包验证，未安装）。
- 新增 `DshApprovalProtocolTest` 9 项：ready/waterfall/cancel/error 帧、字段白名单、官方 `$events/result` 信封、`$events` open 信封、风险分级、本次运行记忆。
- 新增 `DshToolTimelineTest` 8 项：工具调用与结果配对、失败原因、replace 去重、孤儿结果、长输出截断、diff 行/计数、新建文件、命令/路径参数、`session/page` 文本与工具时间线并存。
- 新增 `DshWebSocketTest` 5 项：RFC6455 Accept、客户端掩码、拒绝服务端掩码、服务端文本帧、真实 loopback 握手/Cookie/ready/approval/cancel、错误 Accept 拒绝。
- 新增 `DshApprovalSessionTest` 5 项：`/connect`→`$events`→`$events/result` 全链路、401 重认证一次、HTTP 500 中文错误、缺少端点、非 loopback 拒绝。
- 新增 `DshApprovalControllerTest` 7 项：允许并记住、同工具/风险自动回答、不同工具/风险不自动、回答失败保留卡片、断线禁用并关闭、连接期间断线不误报已连接、origin 变化重建、关闭清空记忆。
- 既有 `DshUiSwitchTest` 10 项、`DshNativeControllerTest` 6 项、`DshNativeApiTest` 12 项继续通过。

未验证：

- 真实 Android 设备/模拟器上的原生页布局、暗色模式、大字体、长参数/长 diff、输出折叠与滚动、全屏与返回。
- 真实 PRoot 内 DSH `0.2.1-rc.5` 的 `/connect`、`/api/remote.mux` WebSocket、审批 waterfall、`$events/result` 回答、401 Cookie 失效、服务重启与安全模式 rescue。
- 远程 SSH 隧道后的审批流；通知点击后待审批列表过滤。
- WebSocket `session/follow`、`session/control`、用户提问；聊天发送、取消、附件、模型选择。
- 低版本 DSH 缺少 `session/list` 或 `$events` 时的回退只在代码层验证。
- Android 仪器、真机与合并/发布均未执行。
