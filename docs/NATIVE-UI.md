# 原生界面与官方 DSH 界面切换

本文件记录“官方界面 / 原生界面”开关的接口发现、已实现切片、回退与安全边界。默认始终是官方界面；原生界面读取官方会话与历史文本，并提供输入/附件发送；不修改官方 npm 包、DSH 数据目录或 WebView 安全策略。

## 1. 结论

官方 DSH `0.2.1-rc.5` 没有 REST 会话列表端点，也没有 SSE。会话、消息、发送与审批都通过 **Typert Remote** 暴露：

- 一元调用：`POST /api/<method>`，JSON 信封，需签名 Cookie。
- 实时流：`/api/remote.mux` WebSocket。
- 审批/提问：Agent-scoped waterfall 事件，经 WebSocket 的 `$events` 通道回答。

因此原生切片实现：**官方会话列表 + 官方历史页文本消息 + 输入/附件发送**。实时跟随、审批/提问仍只走 WebSocket，本切片未实现；不伪造审批按钮或流式消息。

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
| `session/projections` | 读取单会话 `kind=sequenced` 的 `asOfSeq` 与 `values.imageLimits` | 使用；作为 `session/page` 的 `throughSeq`，并约束图片附件 |
| `session/page` | 读取一段历史事件；`records[].event` 含 `user/message`、`assistant/message` 等 | 使用；只提取文本，`maxMessages=120` |
| `session/prompt` | 发送提示词 | 使用；文本、图片 base64 与文件 receipt |
| `session/cancel` | 取消运行 | 未使用 |
| `session/create` / `rename` / `selectModel` / `updateQueue` | 会话管理 | 未使用 |
| `session/attachment` | 附件读取 | 未使用；历史附件仍不在原生页展示 |
| `fileUploads/upload` | 普通文件上传换取 receipt | 使用 |
| `commands/list` / `commands/execute` | 斜杠命令目录与执行 | 使用 |
| `fileReferences/list` | @ 文件/目录补全 | 使用 |
| `sessionReferenceResolver/candidates` | @ 会话补全 | 使用 |
| `skills/list` | 技能目录 | 未使用 |
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
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 原生页：会话条 + 文本消息 + 空态/错误态 + 底部输入栏插槽 |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerBar.kt` | 多行输入、@/斜杠菜单、附件预览、粘贴与拖入 |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerController.kt` | 按会话草稿、补全、附件校验/上传、发送状态机 |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerDraftStore.kt` | 私有偏好按会话保存文本草稿 |
| `app/src/main/kotlin/com/tanuki/ui/DshAttachmentReader.kt` | 文档 URI、剪贴板图片与拖入内容读取 |
| `app/src/main/kotlin/com/tanuki/web/DshComposerProtocol.kt` | 官方 prompt/upload/commands/引用协议与附件策略 |
| `app/src/main/kotlin/com/tanuki/web/DshComposerClient.kt` | 输入/附件 Remote 客户端；依赖 42 提供的 `DshComposerRemote` transport 适配，不自建 HTTP/WS |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 设置开关、DSH 页原生/官方切换、回退提示与“重试” |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 偏好持久化、控制器接线、同一 token/同一 DSH 进程与输入栏 endpoint |

偏好文件 `ui`、键 `dsh-ui`，值 `official` / `native`；未知值回到 `official`。设置入口在“设置 → 网页与兼容性 → 界面：官方界面 / 原生界面”。

### 切换语义

- 默认官方界面；不改变现有 WebView 加载、Cookie、同源策略或 DSH 启动参数。
- 切到原生：不停止服务、不重建 DSH、不创建第二个服务；复用同一 `origin` 与同一启动 token。
- 切回官方：仍使用同一个 `DshWebView` 实例；需要时由现有 `connectDsh` 重新挂载，不重启服务。
- 原生列表读取失败（认证、404、格式错误）：立即回退官方界面，并在官方页顶部显示原因和“重试”；用户偏好保持 `native`，不会静默改回官方。
- 通知点击：原生偏好下目标交给原生控制器；冷启动先加载列表再选中，已有原生页直接替换；原生列表失败不消费目标，回退官方后由 WebView 连接完成同一目标；通知点击当前会话会强制重读历史，不复用旧页缓存；只有本次通知已发起并完成刷新才消费目标。
- 服务未运行时原生页不显示；停止服务会清空原生页内存 Cookie；再次启动由新 `origin` 自动重新认证。
- 原生页的消息区仍只读；底部输入栏会调用 `session/prompt`/`commands/execute`，因此会按官方语义向当前会话追加用户消息。发送不直接改写 `userdata/dsh`，也不调用 `session/cancel`、审批或模型选择。

## 4.1 输入与附件

输入栏只在原生页、已选中官方会话时出现；默认官方界面完全不受影响。

- 多行输入与草稿：`TextField` 最多 6 行；按会话把文本草稿写入应用私有 `dsh-composer` 偏好，切换会话/通知点击前先落盘旧草稿。附件不跨会话保留，也不持久化临时 URI。
- 发送：`Ctrl/⌘ + Enter` 或“发送”按钮；普通 Enter 换行。普通文本走 `session/prompt(mode=queue)`；已识别的斜杠命令走 `commands/execute`，命令是否接受附件以 `commands/list` 的 `input.attachments` 为准。
- @ 补全：首行 `@` 触发；同时请求 `fileReferences/list` 与 `sessionReferenceResolver/candidates`。文件/目录使用官方 `@path`/`@"path"` 语法，会话插入官方 `@[label](dsh-session:...)` mention。
- 斜杠补全：首行 `/` 触发，读取 `commands/list` 并按前缀过滤；选择后插入 `/name `，命令参数保留。
- 附件：图片与普通文件分开选择；图片按实际字节嗅探 PNG/JPEG/WebP/GIF，读取宽高/像素并在客户端与服务端 `imageLimits` 交集内校验；普通文件先 `fileUploads/upload` 换取 receipt，再随 prompt 发送。图片在发送时以 base64 随 prompt 发送。
- 限制：单张图片 20 MiB、图片总量 30 MiB、单文件 20 MiB、每条消息最多 5 个文件/10 个附件、附件总量 50 MiB；服务端更小限制优先。错误为中文，失败保留草稿与附件。
- 粘贴/拖入：剪贴板读取 `content://` 图片或受限 `data:image/...;base64`；Compose `dragAndDropTarget` 接收其他应用拖入的 `content://` 文件。只接受可读 `content://`，不接受 `file://`/App 私有文件。
- 兼容：输入栏复用同一 `DshEndpoint`（本地或远程 SSH 转发的 loopback origin）与启动 token；通知点击仍由 `DshNativeController.openSession()` 选择会话，输入栏随 `selectedId` 切换草稿。发送成功后只调用现有 `select(force=true)` 重读历史，不接管流式/消息渲染。

## 5. 安全边界

- 启动 token 只从 `RuntimeManager.authenticatedUrl` 读取，只用于当前精确 origin 的 `/connect`；不写偏好、日志、状态或错误文本。
- 认证网络 I/O 不持对象 monitor；`reset()/close()` 只递增代次并清状态，主线程不会等待 `/connect` 超时；旧请求不能重新发布 Cookie。
- API Cookie 只保存在 `DshNativeClient` 与 42 提供的 transport 内存中；输入/附件客户端只依赖 `DshComposerRemote` 小接口，不自行读取 token/Cookie 或拼 URL。
- `/connect` 不跟随 302；只从 `Set-Cookie` 取一个 `name=value`，拒绝控制字符。
- 业务错误只显示短错误码；其他异常经 `Redact.text` 截断；`DshEndpoint.toString()` 显式脱敏 launch URL，避免误日志带出 token。
- 不新增 JavaScript Bridge、不扩大 WebView origin、不把 API Cookie 交给网页脚本；原生页不持有 WebView。
- 请求不带 `Origin` 头；与官方“工具客户端”语义一致。

## 6. 验证

本轮（android_agent-46，基线 `0555c3c0`）新跑：

- `:app:compileModernDebugKotlin`：BUILD SUCCESSFUL（`-x buildNativeRuntime`）。
- 输入/附件目标回归 34 项（不含 transport 认证/HTTP；该层等待 42 统一接线）：`DshComposerProtocolTest` 15、`DshComposerClientTest` 8、`DshComposerControllerTest` 11；0 failures / 0 errors / 0 skipped。
- 完整 `:app:testModernDebugUnitTest`：BUILD SUCCESSFUL，68 个结果文件、**569 tests / 0 failures / 0 errors / 0 skipped**（基线 535 + 新增 34）。
- `:app:lintModernDebug`：BUILD SUCCESSFUL，**0 errors / 95 warnings / 1 hint**；新增文件无 lint 告警。
- 覆盖：官方 `session/prompt`/`fileUploads/upload`/`commands/list|execute`/`fileReferences/list`/`sessionReferenceResolver/candidates` 参数与返回解析；图片类型嗅探与 PNG/JPEG/GIF/WebP 尺寸；图片字节/数量/总量/宽高/像素限制中文错误；文件 20 MiB/5 个/50 MiB 限制；@ 引号/目录/邮箱式文本；斜杠命令识别；按会话草稿；图片+文本发送；文件先上传后 receipt；命令附件策略；上传失败/发送失败保留草稿；官方附件错误码中文化。

未验证：

- 真实 Android 设备/模拟器上的原生页布局、暗色模式、长文本、全屏与返回。
- 真实 PRoot 内 DSH `0.2.1-rc.5` 的 `/connect`、HTTP 一元调用与 Cookie 生命周期。
- 真实设备上的图片/文件选择、缩略图/大图预览、粘贴图片、拖入文件、软键盘/硬件键盘快捷键、附件上传进度与失败重试。
- WebSocket `session/follow`、`session/control`、审批与提问；真实设备上的发送、取消、模型选择。
- 服务重启/应用进程重建后的 Cookie 失效与重新认证；低版本 DSH 缺少 `session/list`/`commands/list` 时的回退只在代码层验证。
- assemble、Android 仪器测试、真机与合并/发布均未执行。
