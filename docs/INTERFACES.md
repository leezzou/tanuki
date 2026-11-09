# Tanuki 接口契约

本文件固定当前源码中的控制面、RPC 工具面与进程/挂载面契约，作为原生层、guest 客户端、Skill 文档和测试的共同依据。源码是最终事实；本文件与源码冲突时先查证并同时修正两者，不能只改客户端或只改 native。

**变更顺序：新增或修改方法、参数、错误码、大小/超时限制时，先改本文件，再改 native 服务与 guest 客户端，最后补测试并在 [TEST-REPORT](TEST-REPORT.md) 记录。不要发明协议、错误码或兼容别名。**

方法数量以源码为准（2026-11-06 核对）：

- Browser RPC：**17 个方法**，见 `app/src/main/kotlin/com/tanuki/browser/AgentBrowserController.kt`、`app/src/main/assets/browser/tanuki-browser.mjs`。
- Phone RPC：**11 个方法**，见 `app/src/main/kotlin/com/tanuki/storage/PhoneFileService.kt`、`PhoneFileAccess.kt`、`app/src/main/assets/phone/tanuki-phone.mjs`。

## 1. 控制面：DSH 启动、就绪与 WebView

### 1.1 启动命令

DSH 只在已初始化的私有 Ubuntu 中启动。命令由 `RuntimeManager.start()` 组装，`Launcher.start()` 通过 `libsupervisor.so` 进入 PRoot，再执行官方 DSH CLI：

```text
libsupervisor.so
  libproot.so --link2symlink -0 -r <files/runtime/ubuntu>
  -b <browser-tools>:/opt/tanuki            # 实际 DSH 运行时的浏览器客户端
  -b <phone-tools>:/opt/tanuki-phone        # phoneFiles=true 时
  -b <dsh-startup-tools>:/opt/tanuki-startup # 仅启动期路径缓存，准备成功时
  ... 其他固定挂载，见第 3 节 ...
  -w /workspaces
  /usr/bin/env -i
  HOME=/home/tanuki DSH_HOME=/dsh-data ...
  /opt/node/bin/node
  --require /opt/tanuki-startup/path-cache.cjs # 仅启动期；就绪行/超时后卸载
  /opt/engines/<dsh-version>/node_modules/@deepseek-ai/dsh/lib/bin.js
  web --no-open --host 127.0.0.1 --port 0
```

关键语义：

- `--host 127.0.0.1 --port 0`：只绑定 loopback，由内核分配端口；不监听 `0.0.0.0`，不使用固定端口。
- 启动前要求环境 `EnvironmentState.READY`、无 `UpdateState.RECOVERY_REQUIRED`、工作区容量可启动；否则不启动。
- 每次运行都会启动新的 phone 文件连接代次（第 4 节），并在启动 DSH 前发布 `connection.json`。
- 失败、DSH 退出或用户停止时，先撤销 phone 写权限并关闭连接，再清理子进程和 `authenticatedUrl`/`origin`；不自动重放任务。

#### 1.1.1 启动期只读路径缓存

`RuntimeManager.start()` 在 `web` 启动前把 APK 资源 `dsh-startup/path-cache.cjs` 复制到应用私有目录，并以只读绑定挂到 `/opt/tanuki-startup`；只有准备和绑定都成功时才给 node 注入 `--require`。准备失败、脚本缺失或绑定异常时不注入，按原生方式启动，不阻塞 DSH。

- 缓存范围只包括 rootfs 内只读目录的绝对路径：`/opt/node`、`/opt/engines`、`/opt/pnpm`、`/opt/tanuki`、`/opt/tanuki-phone`、`/opt/tanuki-startup`。
- 接管 `realpathSync`、`realpathSync.native`、`existsSync` 和这些目录内 `package.json` 的 `readFileSync`；不缓存缺失路径、非只读目录、非 `r` 标志读取或超过 256 KiB 的 package.json。
- 官方就绪行 `dsh web: http://127.0.0.1:<port>/...` 出现或默认 30 秒超时后卸载，之后全部直通 `node:fs`。`TANUKI_DSH_PATH_CACHE=0|false|off|no` 关闭；`TANUKI_DSH_PATH_CACHE_TIMEOUT_MS` 可调整超时。
- 任何缓存逻辑异常都退回原生实现；不修改官方 npm 包，不改变 DSH UI/协议。该缓存只是启动加速，不是文件系统安全边界。

#### 1.1.2 首帧前提前启动

`MainActivity.onCreate()` 在 `setContent` 前调用 `RuntimeManager.requestEarlyStart()`，与首帧并行；只复用既有前台服务 `START` 链路和 `RuntimeManager.start()`，不另建进程或第二套密钥/用户数据流程。

- 仅当环境 `READY`、服务为干净的 `STOPPED`、无安装/下载等互斥操作、无存活子进程、未在停止且无 `RECOVERY_REQUIRED` 时才发起；`STARTING/RUNNING/STOPPING/FAILED/INTERRUPTED` 都不自动启动。
- 用户设置“启动应用时自动运行 DSH”默认开启，可在设置中关闭。每个应用进程最多自动发起一次；Activity 重建不会重复拉起。
- 前台服务收到独立的 `START_EARLY` 动作后会再次校验条件；条件已变化则静默返回，不把状态误置为 `FAILED`。自动发起失败时仍可手动启动。

### 1.2 官方就绪日志与精确 origin

原生只从 DSH 输出流中采信官方就绪行：

```text
dsh web: http://127.0.0.1:<port>/...
```

`RuntimeManager.captureUrl()` 的接受条件：

- 行必须包含字面量 `dsh web:`。
- 从行中提取 `http://127\.0\.0\.1:\d+/[^\s\u001B]*`。
- URI 的 host 必须是 `127.0.0.1`，端口在 `1..65535`，不能有 userInfo，query 不能为空。
- 只有满足条件才设置 `origin = "http://127.0.0.1:<port>"` 和 `authenticatedUrl = <原始带 token URL>`。

就绪等待上限为 **180 秒**，期间要求 DSH 进程存活；超时或提前退出都判失败。启动 token 只保留在进程内存，不写入日志、state 或元数据；日志过滤器会脱敏 `token`/`auth`/`key`/`code`、`Bearer`、`Set-Cookie` 等模式。

### 1.3 启动 token → 签名 Cookie

1. `authenticatedUrl` 含官方一次性启动 token。
2. 管理 WebView 加载该 URL；官方服务完成校验后设置签名 Cookie，并由官方重定向清理地址中的 token。
3. 页面主框架完成或收到 401/403 时，原生用 WebView 当前 Cookie 对 `origin/` 做校验请求：
   - `GET <origin>/`，带 `Cookie`，禁止跟随重定向，连接/读取超时各 5 秒。
   - **HTTP 200 → `WebState.CONNECTED`**。
   - 401/403 → `WebState.AUTH_EXPIRED`，每个启动 URL 最多自动重试一次。
   - 其他/异常 → `WebState.DISCONNECTED`。
4. Cookie 只由 WebView 与官方服务持有；原生不复制 Cookie 给独立浏览器，不把启动 token 暴露给 AI 工具。

管理 WebView 只允许当前精确 origin：`OriginPolicy.sameOrigin()` 要求 `http`、host `127.0.0.1`、端口相同、无 userInfo、`rawAuthority` 完全一致。管理页之外的资源请求返回 403；第三方 Cookie 关闭；TLS 错误直接取消；不启用 `file://` 或 JavaScript Bridge。

### 1.4 HTTP 健康检查

启动完成前的存活检查 `RuntimeManager.httpReady()`：

```text
GET <origin>/
连接超时 2s，读取超时 2s，不跟随重定向
HTTP 200..399 或 401 → 存活
```

注意区分两个阈值：

- **存活检查**允许 2xx/3xx/401（401 说明服务在运行但未认证）。
- **Cookie 认证检查**要求精确 **200**，401/403 视为认证失效。

启动成功后写 `.web-verified` 标记，状态置 `ServiceState.RUNNING`、`webHealthy=true`，并每 10 秒复查一次；复查失败只把 `webHealthy=false`、`web=DISCONNECTED`，不等同于 DSH 进程退出。DSH 真正退出时状态进入 `STOPPED` 或 `INTERRUPTED`，并关闭 phone 连接。

### 1.5 `WebState` 语义

`WebState` 描述管理网页连接，不表示 DSH 进程或环境状态。定义见 `runtime/State.kt`，中文标签见 `ui/Labels.kt`。

| 值 | 标签 | 语义与进入条件 |
|---|---|---|
| `DISCONNECTED` | 未连接 | 尚未启动、地址未就绪、主框架加载失败、健康检查失败或服务停止 |
| `CONNECTING` | 连接中 | 当前 origin 的主框架开始加载 |
| `AUTHENTICATING` | 认证中 | 页面完成或收到 401/403，正在用 Cookie 校验；自动重试期间也保持此状态 |
| `CONNECTED` | 认证已连接 | 带 Cookie 的 `GET /` 精确返回 200 |
| `AUTH_EXPIRED` | 认证失效 | 带 Cookie 的 `GET /` 返回 401/403；只对同一启动 URL 重试一次 |
| `INCOMPATIBLE` | WebView 需要更新 | WebView 能力探测失败或超时，缺少所需 CSS/JS 能力 |
| `RENDER_ERROR` | 渲染进程异常 | WebView 渲染进程退出或加载抛错；DSH 进程状态另行显示 |

`ServiceState`、`EnvironmentState`、`HealthState` 与 `WebState` 相互独立；不能把网页未连接写成 DSH 未运行，也不能把 401 写成进程失败。

## 2. 工具面：Browser RPC

### 2.1 传输与认证

实现：`AgentBrowserController` 内的 loopback `ServerSocket`；客户端：`assets/browser/tanuki-browser.mjs`。

| 项目 | 契约 |
|---|---|
| 监听 | `ServerSocket(0, 8, 127.0.0.1)`，只接受本机连接 |
| 端点 | `POST /rpc HTTP/1.1` 或 `POST /rpc HTTP/1.0`；其他路径/方法 404 `endpoint_not_found` |
| Bearer | `Authorization: Bearer <token>`；token 为 32 字节随机数的 base64url 无填充编码，恰好 43 字符；常量时间比较 |
| `Origin` | 请求中**存在** `Origin` 头即拒绝：HTTP 403 `origin_forbidden`；工具客户端不发送该头 |
| 请求头 | 逐字节累计上限 8192；重复头、非法头名、`Transfer-Encoding`、`Expect` 均拒绝 |
| 请求体 | 必须有 `Content-Length`，范围 `1..65536`；超出返回 413 `body_too_large` |
| JSON | 单个 JSON object；嵌套深度上限 32；尾部多余内容拒绝；否则 `invalid_request` |
| 响应 | HTTP JSON，`Content-Type: application/json; charset=utf-8`、`Content-Length`、`Cache-Control: no-store`、`Connection: close`；按 8192 字节分块写出 |
| 会话期限 | 每个 socket 12 秒硬截止；socket `soTimeout=5000`；主线程调度结果最多等待 5 秒，超时 `operation_timeout` |
| 并发 | 同时只允许一个文档操作；冲突返回 `operation_busy` |

客户端额外边界：

- 读取私有 `connection.json`：`{port, token, clientId}`；token 必须匹配 `^[A-Za-z0-9_-]{43}$`，端口 `1..65535`。
- 请求体超过 65536 字节：客户端直接 `request_too_large`。
- 响应累计超过 **3 MiB**：`response_too_large`。
- 客户端超时 **12000 ms**：`browser_timeout`。
- 响应体不是合法 JSON：客户端抛 `invalid_response`。客户端**不校验** `ok`、`result` 或 `error` 的类型/结构，合法 JSON 会原样返回；不要依赖客户端做错误结构校验。
- 客户端不打印 token、连接文件路径或原始 HTTP 异常。

### 2.2 请求与响应信封

请求：

```json
{"clientId":"<uuid>","method":"<name>","params":{}}
```

- `clientId` 必填，必须是 UUID（8-4-4-4-12 十六进制）；非法为 `invalid_client`。
- `method` 必填且必须在下表 17 个方法中；缺失 `invalid_method`，未知 `unknown_method`。
- `params` 可省略；存在时必须是 object，否则 `invalid_params`。
- `status` 只返回认证/连接元数据，不返回 URL、标题、文档或其他客户端 ID。

响应：

```json
{"ok":true,"result":{...}}
{"ok":false,"error":{"code":"<short_code>","message":"<short_code>"}}
```

错误信息只返回短错误码，不返回路径、凭据或底层异常文本。

### 2.3 授权与票据

- 合法连接默认可以查看普通页面；操作权由原生票据约束，**RPC 不能自行授权**。
- `status` 只读认证元数据。
- `tabs`、`snapshot`、`screenshot` 是查看方法；当前连接可直接查看普通页面，不需要操作票据。
- `request_control` 向用户请求当前连接的操作权。已授权时返回 `{pending:false, authorized:true}`；未授权时**立即**返回 `{pending:true}`，不阻塞等待用户。调用方应通过 `status` 的 `authorized`/`pending` 观察结果；120 秒只是待处理请求的有效期，到期仍未处理会撤销该请求。用户未授权前，操作方法返回 `permission_required`。
- 操作方法在执行前取得票据；用户收回、临时接管到期、页面重建或连接关闭都会使旧票据失效，后续返回 `permission_revoked`。
- 触碰网页会先暂停 AI 操作并使旧票据失效；临时接管连续 30 秒无交互后只恢复同一当前连接的票据。
- 登录保护页：`tabs` 仍可用，但活动标签 ID 返回空；其他方法返回 `private_page`。
- `tabId` 读取参数只能指向当前选中标签，否则 `tab_not_selected`，不会因查看请求切换页面。

### 2.4 方法表（17 个）

| # | 方法 | 参数与限制 | 行为 / 结果 |
|---|---|---|---|
| 1 | `status` | 无 | 返回 `visible`、`closed`、`authorized`、`pending`、`background`；不含文档数据 |
| 2 | `request_control` | 无 | 已授权返回 `{pending:false, authorized:true}`；未授权立即返回 `{pending:true}` 并由原生询问用户；120 秒为待处理请求有效期，不是 RPC 等待时间；结果通过 `status` 观察 |
| 3 | `tabs` | 可选 `tabId` 必须等于当前选中标签 | 返回普通标签 `id/url/title` 与 `activeTab`；敏感登录页 `activeTab=""` 且不列出该标签 |
| 4 | `snapshot` | `offset` 0..1000000（默认 0）、`elementOffset` 0..50000（默认 0）、可选 `tabId` | 读取整篇主文档；每页最多 20000 字符、100 个控件；扫描上限 50000 节点；序列化结果上限 160000 字符 |
| 5 | `screenshot` | 可选 `tabId` | 完整文档 PNG；最大边 2048 px；Base64 后超过 2 MiB 返回 `screenshot_too_large` |
| 6 | `navigate` | `url` UTF-8 不超过 8192 字节 | 导航当前页；不允许的地址 `navigation_blocked` |
| 7 | `click` | `documentId` ≤64 字节、`elementId` ≤80 字节、可选 `tabId` | 点击已快照元素；文档/元素失效分别 `stale_document`/`stale_element` |
| 8 | `type` | `documentId`、`elementId`、`text` ≤16384 字节、可选 `tabId` | 写入可编辑输入框并派发 `input`/`change`；不可编辑 `not_editable` |
| 9 | `scroll` | `dx`、`dy` 为有限数字，绝对值 ≤10000 | 滚动当前页 |
| 10 | `back` | 可选 `tabId` | 后退；无历史时仍返回 `accepted=true` |
| 11 | `forward` | 可选 `tabId` | 前进；无历史时仍返回 `accepted=true` |
| 12 | `reload` | 可选 `tabId` | 重载；当前 URL 不允许时 `navigation_blocked` |
| 13 | `new_tab` | 可选 `url` ≤8192 字节 | 新建标签；不允许的 URL `navigation_blocked`；失败 `tab_creation_failed` |
| 14 | `select_tab` | `tabId` ≤64 字节 | 切换标签；不存在 `tab_not_found`，敏感页 `private_page` |
| 15 | `close_tab` | `tabId` ≤64 字节 | 关闭标签；不存在 `tab_not_found`，敏感页 `private_page` |
| 16 | `find` | `query` ≤200 字节 | 页内查找 |
| 17 | `zoom` | `fit=true`，或 `percent` 在 50..300 | 适应宽度或设置缩放；返回 `zoomPercent`、`fitWidth` |

除 `status` 与 `request_control` 外，方法都受页面可用性约束：渲染进程已退出返回 `browser_unavailable`；敏感登录页返回 `private_page`（`tabs` 除外）。

### 2.5 错误码

协议/传输层：

| 错误码 | 触发 |
|---|---|
| `invalid_http` | 请求行/头格式错误、头过大、缺少 `Content-Length` |
| `body_too_large` | 请求体不在 1..65536 |
| `origin_forbidden` | 请求带 `Origin` 头 |
| `unauthorized` | Bearer 缺失或不匹配 |
| `endpoint_not_found` | 非 `POST /rpc` |
| `invalid_request` | JSON 非法、嵌套过深、尾部多余内容 |
| `request_failed` | 未归类的服务端异常（不回传原文） |
| `invalid_client` | `clientId` 缺失或不是 UUID |
| `invalid_method` | `method` 缺失 |
| `unknown_method` | 方法不在 17 个之内 |
| `invalid_params` | `params` 非 object 或参数类型/范围不合法 |
| `operation_busy` | 已有文档操作在执行 |
| `operation_timeout` | 5 秒内未完成 |
| `operation_failed` | 方法执行中的未归类异常 |

授权/页面层：

| 错误码 | 触发 |
|---|---|
| `permission_required` | 操作未获授权，或接管请求进行中 |
| `control_busy` | 已有授权或另一个客户端正在请求控制 |
| `permission_revoked` | 票据/代次在操作或响应期间失效 |
| `browser_unavailable` | 渲染进程退出或连接已关闭 |
| `private_page` | 当前为敏感登录页且方法不是 `tabs` |
| `tab_not_selected` | 读取方法指定的 `tabId` 不是当前选中标签 |
| `tab_not_found` | 目标标签不存在 |
| `tab_creation_failed` | 新建标签失败 |
| `navigation_blocked` | URL 未通过 `OriginPolicy`/浏览器地址策略 |
| `stale_document` | `documentId` 与当前文档不符 |
| `stale_element` | 快照元素已脱离文档 |
| `element_unavailable` | 元素禁用、隐藏、`file` 类型或不可见 |
| `not_editable` | `type` 目标不是受支持的输入框/文本域 |
| `result_too_large` | `snapshot` 序列化结果超过 160000 字符 |
| `screenshot_too_large` | 截图 Base64 超过 2 MiB |
| `page_unavailable` | 页面不可绘制或快照结果不可解析 |
| `capture_failed` | PNG 编码失败 |

客户端本地错误：`request_too_large`、`response_too_large`、`browser_timeout`，以及响应体 JSON 解析失败时的 `invalid_response`。`invalid_response` 仅表示 JSON 解析失败，不代表客户端校验了 `ok`/`result`/`error` 结构。

## 3. 工具面：Phone RPC

### 3.1 传输与认证

实现：`PhoneFileService` + `PhoneFileAccess`；客户端：`assets/phone/tanuki-phone.mjs`。

| 项目 | 契约 |
|---|---|
| 监听 | `ServerSocket(0, 8, 127.0.0.1)`，每次 DSH 运行期新建 |
| 端点 | 仅 `POST /rpc HTTP/1.1`；其他请求行/方法 `invalid_request` |
| Bearer | `Authorization: Bearer <token>`；32 字节随机数 base64url 无填充，43 字符；常量时间比较；失败 `permission_required` |
| 请求头 | 逐行上限：请求行 1024、单头 4096、累计 8192；重复头拒绝；禁止 `Origin` 与 `Transfer-Encoding` |
| 请求体 | 必须 `Content-Length`，范围 `1..300000`；否则 `request_too_large`/`invalid_request` |
| JSON | 单个 object；`method` 必填且属于 11 个方法；`params` 必须是 object |
| 响应 | HTTP **200**，JSON `Content-Length`，`Connection: close`，`Cache-Control: no-store`；响应体上限 1,048,576 字节 |
| 会话期限 | socket `soTimeout=10_000`；每个请求在代次不匹配时返回 `connection_expired` |
| 并发 | 2 个 worker，队列 8；每个连接代次独立 |

注意：Phone RPC **没有 `clientId` 字段**。它用运行代次（generation）区分连接，用文件 `expectedRevision` 做写前条件校验；Browser 的 `clientId` 语义不能套到 phone。

客户端额外边界：

- 读取 `connection.json`：`{port, token}`；token 匹配 `^[A-Za-z0-9_-]{43}$`，端口 `1..65535`；文件缺失/非法 `phone_unavailable`。
- 请求体超过 300000 字节：`request_too_large`。
- 响应累计超过 1 MiB：`response_too_large`。
- 客户端超时：短控制方法 **20 秒**；`stat`、`list`、`read`、`write_begin`、`write_commit`、`rename`、`delete` 等长 I/O 方法 **120 秒**，超时 `phone_timeout`。
- 响应中出现当前 token、非对象结果、非法错误结构：`invalid_response`。

### 3.2 请求与响应信封

请求：

```json
{"method":"<name>","params":{}}
```

响应：

```json
{"ok":true,"result":{...}}
{"ok":false,"error":"<short_code>"}
```

与 Browser 不同，Phone 的错误 `error` 是字符串，不是对象；服务端始终返回 HTTP 200。错误文本只允许 `[a-z_]{1,64}`，否则归为 `file_operation_failed`。

### 3.3 逻辑路径与授权

- 逻辑根固定为 `/phone`；主共享卷为 `/phone/primary`，其他卷为 `/phone/volume-<16位十六进制>`（由宿主路径 SHA-256 前 16 位派生）。
- `/phone/...` 是 RPC 逻辑路径，**不是** guest 内的真实挂载；真实手机卷不 bind 进 Ubuntu。
- 默认只读。`writeAllowed=false` 在每个 DSH 运行期开始时重置；用户在原生文件页明确授权后才可写，停止、重启、撤回或观察到系统写权限丢失都会恢复只读。
- 写入还要求系统共享存储写权限；缺少时分别返回 `write_permission_required` / `storage_write_permission_required`。
- 拒绝应用专属外部目录：`Android/data`、`Android/obb`、`Android/sandbox` → `private_directory_denied`。
- 拒绝符号链接和特殊文件：`symlink_not_allowed`、`unsupported_file_type`。
- 每个分块、提交、重命名、删除都复核代次、系统权限、路径和原版本；旧请求不能借用新运行期授权。

### 3.4 方法表（11 个）

| # | 方法 | 参数 | 结果 / 语义 |
|---|---|---|---|
| 1 | `status` | 无 | `readAllowed`、`writeAllowed`、`roots[{id,label,path}]` |
| 2 | `list` | `path`（默认 `/phone`）、`offset` ≥0、`limit` 1..200（默认 200） | `entries[]`、`totalEntries`、`nextOffset`；目录项含 `name/path/directory/size/modified` |
| 3 | `stat` | `path` 必填 | `path/directory/size/modified/revision` |
| 4 | `read` | `path`、`offset` ≥0、`length` 1..196608（默认 196608） | `dataBase64`、`offset`、`nextOffset`、`totalBytes`、`revision` |
| 5 | `write_begin` | `path`、`size` ≥0；目标已存在时 `expectedRevision` 必填 | `id`、`received=0`、`size`；不存在的目标若带 `expectedRevision` 则 `file_changed` |
| 6 | `write_chunk` | `id`、`offset` 必须等于已接收字节数、`dataBase64` 解码后 ≤196608 | `received`、`size` |
| 7 | `write_commit` | `id` | `path`、`size`、`written=true`；原子替换并复核原版本 |
| 8 | `write_cancel` | `id` | `cancelled=true`；删除暂存文件 |
| 9 | `mkdir` | `path`；不能是卷根，目标必须不存在 | `path`、`created=true` |
| 10 | `rename` | `path`、`to`；同卷；源目录必须为空；目标必须不存在；`expectedRevision` 必填 | `path`（新路径）、`renamed=true` |
| 11 | `delete` | `path`；目录必须为空；`expectedRevision` 必填 | `deleted=true` |

补充语义：

- `revision` 形如 `sha256:<hex>`；目录使用目录指纹，普通文件使用内容 SHA-256。实现内有 128 项哈希缓存，不限制可访问文件/目录数量。
- `expectedRevision` 是写前条件；原文件在 `write_begin` 后变化、提交时变化或源目录非空都返回 `file_changed`。
- 上传暂存位于应用 `cacheDir/phone-file-uploads`，分块 192 KiB，超过 1 小时未触碰会过期；`write_cancel` 或代次失效会清理。
- 手机卷可用空间低于工作区保留量时 `insufficient_space`。

### 3.5 错误码

服务端/文件语义：

| 错误码 | 触发 |
|---|---|
| `invalid_request` | 请求行/头/JSON/`Origin`/`Transfer-Encoding` 不合法 |
| `request_too_large` | 请求体不在 1..300000 |
| `permission_required` | Bearer 缺失或不匹配 |
| `invalid_method` | 服务端校验到方法不在 11 个之内；`PhoneFileAccess` 另有不可达的 `unknown_method` 兜底 |
| `connection_closed` | 服务已关闭 |
| `connection_expired` | 运行代次已变化 |
| `file_operation_failed` | 未归类异常（不回传原文） |
| `invalid_path` | 路径缺失、不以 `/phone/` 开头、含空段/`.`/`..`/反斜杠/NUL |
| `storage_permission_required` | 缺少共享存储读权限 |
| `storage_write_permission_required` | 缺少系统共享存储写权限 |
| `write_permission_required` | 当前运行期未获原生写授权 |
| `permission_revoked` | 写授权代次已变化 |
| `storage_volume_unavailable` | 卷不存在、不是目录或是符号链接 |
| `private_directory_denied` | 访问 `Android/data`、`Android/obb`、`Android/sandbox` |
| `symlink_not_allowed` | 路径中遇到符号链接 |
| `unsupported_file_type` | 不是普通文件/目录 |
| `file_not_found` | 目标不存在 |
| `not_a_file` / `not_a_directory` | 类型不符合方法要求 |
| `file_exists` | 创建/重命名目标已存在 |
| `file_changed` | `expectedRevision` 不匹配或文件在操作中变化 |
| `expected_revision_required` | 覆盖/重命名/删除已有目标时缺少 `expectedRevision` |
| `invalid_pagination` | `offset`/`limit`/`length` 越界 |
| `invalid_offset` | 读取偏移超过文件大小 |
| `invalid_size` | `write_begin` 的 `size` < 0 |
| `invalid_chunk` | Base64 非法、offset 不连续、块过大或超过声明大小 |
| `upload_not_found` | 上传 ID 不存在 |
| `upload_incomplete` | 提交时接收字节不等于声明大小 |
| `insufficient_space` | 目标/缓存可用空间不足 |
| `volume_root_protected` | 试图修改卷根 |
| `directory_not_empty` | 重命名/删除非空目录 |
| `cross_volume_move_not_supported` | 重命名跨卷 |
| `unsafe_staging_path` | 上传暂存目录被符号链接替换 |

客户端本地错误：`phone_unavailable`、`phone_timeout`、`response_too_large`、`invalid_response`、`local_destination_unavailable`、`local_write_failed`、`local_read_failed`、`local_source_unavailable`、`local_source_changed`。

## 4. Phone 连接生命周期

`PhoneFileService` 与 DSH 运行期绑定，不是常驻服务：

1. `RuntimeManager.start()` 在启动 DSH 前调用 `phoneFiles.start()`。
2. `start()` 生成新的 32 字节随机 token、绑定 loopback 端口、`generation++`，把客户端和 `connection.json` 发布到 `files/phone-tools`，并把受管 Skill 写入 `~/.agents/skills/tanuki-phone/SKILL.md`（带 `managed-by: Tanuki` 标记，不覆盖用户同名自定义 Skill）。
3. `Launcher` 只在真实 DSH 运行且路径未被重定向时把 `phone-tools` 挂到 `/opt/tanuki-phone`，并设置 `TANUKI_PHONE_FILES=/opt/tanuki-phone`；真实手机卷不挂载。
4. 写入默认关闭；用户授权通过原生界面进入，`setWriteAllowed(true)` 还要求系统写权限；`setWriteAllowed(false)` 先撤权再清理上传并递增 epoch。
5. DSH 退出、用户停止、启动失败或服务失联时调用 `phoneFiles.stop()`：先置只读、`generation++`、关闭 socket、停止 worker、删除 `connection.json`。
6. 旧代次的排队/进行中请求在读取前后和操作中检查代次，返回 `connection_expired`；旧连接不能把写事务提交到新运行期。重启后端口、token、代次全部更新，默认只读。

## 5. 进程与挂载面

### 5.1 supervisor 身份行

`app/src/main/cpp/supervisor.c` 在 `setsid()`、设置 `PR_SET_PDEATHSIG=SIGKILL` 后，向 stderr 输出唯一的身份行，然后 `execv(argv[1], &argv[1])`：

```text
TANUKI_IDENTITY pid=<pid> ppid=<parent> uid=<uid> euid=<euid> gid=<gid> pgid=<pgid>
```

`Launcher.Child.observe()` 只在该行满足以下全部条件时记录进程身份：

- `pid > 1`；
- `ppid` 等于本 App 进程 PID；
- `uid` 等于本 App UID；
- `pgid` 等于 `pid`。

随后读取 `/proc/<pid>/stat` 的 starttime 作为出生时间。停止时只有 Java `Process` 存活、PID/出生时间匹配才按记录的身份杀进程组；避免 PID 复用误杀。先 TERM，必要时 KILL，并清理本 App 记录的后代。子进程通过常驻 `tanuki-process-owner` 线程创建，避免创建线程退出触发 PDEATHSIG 误杀。

### 5.2 PRoot 与 loader

- 运行时库来自 `applicationInfo.nativeLibraryDir`：`libsupervisor.so`、`libproot.so`、`libproot-loader.so`。
- PRoot 参数：`--link2symlink -0 -r <root>`；`-0` 只模拟 root，不是 Android Root。
- 环境变量：`PROOT_LOADER=<lib>/libproot-loader.so`、`PROOT_TMP_DIR=<files/proot-tmp>`、`PROOT_L2S_DIR=<files/link-store>`。
- `--link2symlink` 写入的宿主绝对目标必须能解析到持久 `link-store`，因此同时 bind `link-store` 到同一路径；不能把 link-store 内容藏进会被删除的 staging。
- `/tmp` 使用私有 `guest-tmp`，`/dev/shm` 是磁盘目录映射，不具真正 tmpfs 语义。

### 5.3 关键挂载

由 `Launcher.start()` 固定，顺序和路径如下（宿主路径均在 App 私有 `filesDir` 下）：

| 宿主路径 | guest 路径 | 说明 |
|---|---|---|
| `<browser-tools>` | `/opt/tanuki` | 仅真实 DSH 运行且目录未重定向时挂载；浏览器客户端与 `connection.json` |
| `<phone-tools>` | `/opt/tanuki-phone` | 仅 `phoneFiles=true` 且真实 DSH 运行；受控手机文件客户端 |
| `/dev` | `/dev` | 设备节点 |
| `/proc` | `/proc` | 进程信息 |
| `/sys` | `/sys` | 系统信息 |
| `files/guest-tmp` | `/tmp` | 私有临时目录 |
| `files/link-store` | 同一宿主绝对路径 | link2symlink 内容持久化 |
| `files/shared-memory` | `/dev/shm` | 磁盘目录映射 |
| `files/userdata/home` | `/home/tanuki` | guest HOME |
| `files/userdata/dsh` | `/dsh-data` | `DSH_HOME` |
| `files/workspaces` | `/workspaces` | 工作目录 `-w /workspaces` |
| `files/downloads/npm` | `/opt/download-cache` | npm 缓存 |
| `files/engines` | `/opt/engines` | 官方 DSH/npm 引擎 |
| `files/runtime/node` | `/opt/node` | Node |
| `files/runtime/pnpm` | `/opt/pnpm` | pnpm |
| `files/runtime/node-cache` | `/opt/node-cache` | `NODE_COMPILE_CACHE` |

固定环境还包括 `HOME=/home/tanuki`、`DSH_HOME=/dsh-data`、`PATH`（pnpm/node + 系统路径）、`TMPDIR=/tmp`、`LANG=C.UTF-8`、`TERM=xterm-256color`、`DEBIAN_FRONTEND=noninteractive`、单线程下载/编译限制等；完整列表以 `Launcher.start()` 为准。

不挂载：真实手机共享卷、用户真实 HOME、其他 App 数据、`/data/data` 别名。手机文件只能通过第 3 节的受控 loopback RPC 访问。

### 5.4 进程退出与清理

- DSH 退出：关闭 phone 连接，`service` 置 `STOPPED`（用户停止）或 `INTERRUPTED`（非预期），`webHealthy=false`、`web=DISCONNECTED`；不自动重放任务。
- 用户停止：先标记 `stopping-user`，取消监控/服务任务，停止子进程组，关闭 phone 连接，清空 `origin`/`authenticatedUrl`。
- 进程无法确认停止时保留停止入口并报错，不把未确认的终止当作成功。
- 运行期诊断日志只保留脱敏后的最近输出；原始日志不进入公开源码或发布附件。

## 6. 契约变更检查清单

新增/修改方法或错误码时按顺序执行：

1. 更新本文件：方法数量、参数、结果、错误码、大小/超时、授权/Origin 规则。
2. 更新 native：Browser 改 `AgentBrowserController.kt`；Phone 改 `PhoneFileService.kt` + `PhoneFileAccess.kt`；控制面改 `RuntimeManager.kt`/`DshWebView.kt`/`Launcher.kt`。
3. 更新 guest 客户端与 Skill：`assets/browser/tanuki-browser.mjs`、`assets/phone/tanuki-phone.mjs` 及对应 `SKILL.md`。
4. 补回归：客户端宿主测试、JVM 测试、适用的 Android 仪器测试；明确哪些未运行。
5. 在 [TEST-REPORT](TEST-REPORT.md) 记录实际命令、结果和未测范围；不要把源码存在或历史结果当成已通过。

禁止事项：

- 不新增未在本文件声明的 RPC 方法、参数或错误码。
- 不让 RPC 自行授予浏览器操作权或手机写权限。
- 不把 `/phone/...` 宣传成 guest 真实挂载，不把 PRoot 宣传成恶意代码沙箱。
- 不通过关闭 Bearer、Origin 检查、超时、大小限制或签名校验来“兼容”。
