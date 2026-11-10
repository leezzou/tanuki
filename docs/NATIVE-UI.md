# 原生界面与官方 DSH 界面切换

本文件记录“官方界面 / 原生界面”开关的接口发现、已实现最小切片、回退与安全边界。默认始终是官方界面；原生界面只是可切换的只读展示层，不修改官方 npm 包、DSH 数据目录、会话内容或 WebView 安全策略。

## 1. 结论

官方 DSH `0.2.1-rc.5` 没有 REST 会话列表端点，也没有 SSE。会话、消息、发送与审批都通过 **Typert Remote** 暴露：

- 一元调用：`POST /api/<method>`，JSON 信封，需签名 Cookie。
- 实时流：`/api/remote.mux` WebSocket。
- 审批/提问：Agent-scoped waterfall 事件，经 WebSocket 的 `$events` 通道回答。

因此原生切片只实现可靠的只读面：**官方会话列表 + 官方历史页文本消息**。发送、实时跟随、审批/提问没有可靠的一元端点，未做假输入框或假审批按钮。

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
| `skills/list` | 技能目录 | 原生会话管理页使用；按选中会话读取 |
| `session/create` / `rename` / `search` | 新建、重命名、内容搜索 | 原生会话管理页使用 |
| `session/modelCatalog` / `selectModel` | 模型目录与会话级模型选择 | 原生会话管理页使用 |
| `permissionPresets/catalog` / `commands/execute` | 权限目录与 `/permission` 切换 | 原生会话管理页使用；完全权限二次确认 |
| `commands/list` | 斜杠命令目录 | 原生会话管理页只读展示 |
| `workspace/archiveSession` | 归档会话 | 原生会话管理页使用；运行中需确认停止 |
| `session/follow` / `session/control` | 实时历史跟随 / 会话状态流 | 缺口：需要 WebSocket，本切片未实现 |
| `approval/request`、`userQuestions.answer` | 审批、用户提问 | 缺口：审批是 Agent-scoped waterfall；提问回答需要 answer Peer 与事件通道，不能靠一元 HTTP 伪造 |

事件文本提取规则（`DshApiProtocol.parsePage`）：

- `user/message`：读 `data.content` 中 `type=text` 的块。
- `assistant/message`：读 `data.message.content` 中 `type=text` 的块。
- 只保留官方 append-origin 事件（`surfaceOp === "append"`）；`surfaceOp=replace` 的模型上下文替换副本、compact-checkpoint 不显示。
- `user/message` 仅保留 `source.kind=user` 的真实用户消息；注入上下文不标成“你”。
- 其他事件（`tool/result`、`turn/end`、未知事件）跳过；图片、工具调用、附件不显示。
- 空文本、损坏记录跳过；不生成占位消息。
- 单条消息上限 12,000 字符，响应上限 2 MiB。

## 4. 应用内实现

| 文件 | 作用 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshUiMode.kt` | 偏好枚举与纯策略：默认官方；只有“用户选原生 + 服务运行 + 无回退原因”才显示原生 |
| `app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt` | 官方 unary 协议、Cookie 获取、会话/切点/历史解析、`DshNativeClient` |
| `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt` | 原生页状态机；列表失败回退，历史页失败只影响当前会话 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 原生只读页：会话条 + 文本消息 + 空态/错误态 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 设置开关、DSH 页原生/官方切换、回退提示与“重试” |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 偏好持久化、控制器接线、同一 token/同一 DSH 进程 |
| `app/src/main/kotlin/com/tanuki/web/DshSessionAdminApi.kt` | 会话管理一元协议、独立 `interactions=none` API Peer、纯解析与数据源接口 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerController.kt` | 会话管理状态机：列表/过滤/搜索/新建/重命名/归档/未读/模型/权限/技能/命令 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerPage.kt` | 原生会话管理页与详情面板 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerActivity.kt` | 独立入口 Activity；复用 RuntimeManager 状态，安全模式只读，支持远程 origin |

偏好文件 `ui`、键 `dsh-ui`，值 `official` / `native`；未知值回到 `official`。设置入口在“设置 → 网页与兼容性 → 界面：官方界面 / 原生界面”。

### 切换语义

- 默认官方界面；不改变现有 WebView 加载、Cookie、同源策略或 DSH 启动参数。
- 切到原生：不停止服务、不重建 DSH、不创建第二个服务；复用同一 `origin` 与同一启动 token。
- 切回官方：仍使用同一个 `DshWebView` 实例；需要时由现有 `connectDsh` 重新挂载，不重启服务。
- 原生列表读取失败（认证、404、格式错误）：立即回退官方界面，并在官方页顶部显示原因和“重试”；用户偏好保持 `native`，不会静默改回官方。
- 通知点击：原生偏好下目标交给原生控制器；冷启动先加载列表再选中，已有原生页直接替换；原生列表失败不消费目标，回退官方后由 WebView 连接完成同一目标；通知点击当前会话会强制重读历史，不复用旧页缓存；只有本次通知已发起并完成刷新才消费目标。
- 服务未运行时原生页不显示；停止服务会清空原生页内存 Cookie；再次启动由新 `origin` 自动重新认证。
- 原生页只读，不调用 `session/prompt`、`session/cancel`、审批或文件写入，不修改 `userdata/dsh`。
- 原生会话管理是独立页面（设置 → 网页与兼容性 → 原生会话管理），不替换聊天页；列表/新建/重命名/归档/模型/权限/Skills/命令都调用官方一元 Remote，不直接改 `userdata/dsh`。
- 会话硬删除官方未提供；本页只归档。归档恢复列表需要 `workspace/follow` 流，未实现；未读是本机 `updatedAt` 比较，不是官方未读。
- 安全模式/试用模式下管理页只读；远程 DSH 连接时按 `RuntimeManager.origin` 使用 SSH 隧道端点，本地恢复状态不覆盖远程。

## 5. 安全边界

- 启动 token 只从 `RuntimeManager.authenticatedUrl` 读取，只用于当前精确 origin 的 `/connect`；不写偏好、日志、状态或错误文本。
- 认证网络 I/O 不持对象 monitor；`reset()/close()` 只递增代次并清状态，主线程不会等待 `/connect` 超时；旧请求不能重新发布 Cookie。
- API Cookie 只保存在 `DshNativeClient` 内存中；请求前校验 origin 为 `http://127.0.0.1:<port>`，且只拼 `/api/<method>`；不跟随重定向。
- `/connect` 不跟随 302；只从 `Set-Cookie` 取一个 `name=value`，拒绝控制字符。
- 业务错误只显示短错误码；其他异常经 `Redact.text` 截断；`DshEndpoint.toString()` 显式脱敏 launch URL，避免误日志带出 token。
- 不新增 JavaScript Bridge、不扩大 WebView origin、不把 API Cookie 交给网页脚本；原生页不持有 WebView。
- 请求不带 `Origin` 头；与官方“工具客户端”语义一致。
- 会话管理客户端独立持有 `interactions=none` API Cookie，不复用聊天页的 `answer` Peer；两者都不把 Cookie/token 写入日志、状态或网页脚本。
- 模型目录的 provider 失败信息展示前经 `Redact.text` 截断，避免把 provider 错误中的敏感细节直接铺到界面。

## 6. 验证

本轮（2026-11-10，非聊天主链路）新跑：

- `:app:compileModernDebugKotlin -x buildNativeRuntime`：BUILD SUCCESSFUL。
- 新增 `DshSessionAdminApiTest` 7 项：会话权限/模型 projection、搜索/模型/权限/Skills/命令解析、业务错误短码、命令 success 判定、本地合成服务器验证 11 个官方 method 与 Cookie 只发 `/api`、非法权限 preset 不发网络、404 不含 token。
- 新增 `DshSessionManagerControllerTest` 14 项：列表/本机未读、标题/ID/运行/未读过滤、打开标记已读、新建+重命名、非法标题拒绝、运行中归档 stopActivity、安全模式全写拒绝、内容搜索与查询失效、完全权限二次确认、custom 拒绝、Skills/命令部分失败保留、模型/权限加载与选择、缺失目标不伪造会话、close 丢 Cookie。
- 预合并完整 `:app:testModernDebugUnitTest -x buildNativeRuntime`：BUILD SUCCESSFUL，67 个结果文件、**556 tests / 0 failures / 0 errors / 0 skipped**；合并 `origin/main=533eff4` 后 **69 个结果文件、579 tests / 0 failures / 0 errors / 0 skipped**。
- `:app:lintModernDebug -x buildNativeRuntime`：BUILD SUCCESSFUL，0 errors / 95 warnings / 1 hint。
- `:app:assembleModernDebug -x buildNativeRuntime`：BUILD SUCCESSFUL（3m29s；未安装设备）。

历史（2026-11-09，android_agent-33）：

- `:app:compileModernDebugKotlin`：BUILD SUCCESSFUL。
- `:app:testModernDebugUnitTest -x buildNativeRuntime`：rebase 到 `main=0ba11da` 后 393 tests / 0 failures / 0 errors / 0 skipped。
- `:app:lintModernDebug -x buildNativeRuntime`：BUILD SUCCESSFUL（仅既有弃用/可读性告警）。
- 新增 `DshUiSwitchTest` 10 项、`DshNativeControllerTest` 6 项、`DshNativeApiTest` 12 项。

未验证：

- 真实 Android 设备/模拟器上的原生页/会话管理页布局、暗色模式、长文本、大字体、全屏与返回。
- 真实 PRoot 内 DSH `0.2.1-rc.5` 的 `/connect`、HTTP 一元调用与 Cookie 生命周期。
- WebSocket `session/follow`、`session/control`、审批与提问；发送、取消、附件未实现；模型选择/权限切换已实现但未设备验证。
- 真实 `session/create|rename|search`、`workspace/archiveSession`、`commands/execute` 的 `/permission` 结果与运行中停止语义。
- 服务重启/应用进程重建后的 Cookie 失效与重新认证；低版本 DSH 缺少 `session/list` 时的回退只在代码层验证。
- Android 仪器测试、真机、APK 安装与合并/发布均未执行。
