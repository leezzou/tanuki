# 原生界面对齐：非聊天主链路

本文件记录 Tanuki 原生界面对齐官方网页版的“非聊天主链路”切片：会话管理、工作区文件、设置、技能/插件/斜杠命令，以及通知点击、远程 origin 与安全模式兼容。聊天流式、输入发送、停止和消息渲染由并行切片负责，不在本文件范围内。

## 1. 范围与边界

- 本切片只做原生层展示与官方 DSH 一元 Remote 调用，不修改官方 npm 包、不另造聊天协议、不改 WebView 安全策略。
- 官方 DSH 仍保留为默认界面；原生界面是可切换视图，接口不可用时回退官方，不重启服务。
- 聊天主链路文件（`DshNativeApi.kt`、`DshNativeController.kt`、`DshNativePage.kt` 及流式/发送/停止新增文件）由并行切片维护。本切片不修改这些文件，避免同一核心文件并发写。
- 共享集成只做加法：设置页入口、独立原生会话管理 Activity、清单声明。后续由编排器决定是否把管理组件嵌入原生 DSH 页。

## 2. 官方协议基线

目标运行时为官方 DSH `0.2.1-rc.5`。本切片使用的 method 均来自该版本已声明的 Typert Remote：

| 能力 | method | 参数要点 |
|---|---|---|
| 会话列表 | `session/list` | `_request:{}`；读取 `items[].projections.values` 中的标题、权限、模型、切点 |
| 新建会话 | `session/create` | `request:{}`；可选 `workspaceId`/`cwd`/`agentPreset` |
| 重命名 | `session/rename` | `request:{sessionId,title}` |
| 内容搜索 | `session/search` | `request:{query}`；返回 `items[{sessionId,snippet}]` 与 `hasMore` |
| 归档 | `workspace/archiveSession` | `request:{sessionId,stopActivity?}`；运行中不停止会被拒绝 |
| 模型目录 | `session/modelCatalog` | 无参；返回默认模型、分组、失败 provider |
| 选择模型 | `session/selectModel` | `request:{sessionId,provider,model,reasoningEffort?}` |
| 权限目录 | `permissionPresets/catalog` | 无参；返回可选 preset、默认 preset |
| 切换当前会话权限 | `commands/execute` | `agentId` + `/permission <preset>` + 空附件 |
| 技能目录 | `skills/list` | `request:{sessionId}` |
| 斜杠命令 | `commands/list` | `agentId:sessionId` |

协议无会话硬删除端点。`workspace/delete` 删除的是工作区注册，不是会话；本切片不把工作区删除伪装成会话删除。归档会话的恢复列表来自 `workspace/follow` 流，本切片未建立 WebSocket，因此只提供归档，不在原生侧伪造“已归档列表”。

## 3. 功能清单与状态

图例：**已实现**（本切片代码已落地）、**已有复用**（main 已有能力，本切片不改或只接线）、**后续**（明确未做）、**协议不可用**（官方 0.2.1-rc.5 无可靠端点，不用假实现）。

### 3.1 会话管理

| 功能 | 状态 | 说明 |
|---|---|---|
| 会话列表 | 已实现 | 读取官方 `session/list`，按更新时间倒序；显示标题、时间、运行状态、空白会话、子代理来源 |
| 标题过滤 | 已实现 | 本地即时过滤，不联网 |
| 内容搜索 | 已实现 | 官方 `session/search`，显示命中片段与更多结果标记；空查询不请求 |
| 新建会话 | 已实现 | 官方 `session/create`；可选立即重命名；失败不写本地伪会话 |
| 重命名 | 已实现 | 官方 `session/rename`；长度/空值校验，失败保留原列表 |
| 归档 | 已实现 | 官方 `workspace/archiveSession`；运行中需用户确认“停止并归档”，归档后从本地列表移除 |
| 硬删除 | 协议不可用 | 官方无 `session/delete`；不通过直接改 DSH 数据目录伪造删除 |
| 恢复归档 | 后续 | 需要 `workspace/follow` WebSocket 基线；本切片未建立 |
| 未读状态 | 已实现（本地） | 以本机最后一次查看的 `updatedAt` 比较；不是官方未读字段，不跨设备同步 |
| 运行状态 | 已实现 | 官方 `running`/`agentAvailable`；运行中行显示标记 |
| 通知点击定位 | 已有复用 | main 的通知点击路由保持不变；本切片不接管聊天页选择 |

### 3.2 工作区

| 功能 | 状态 | 说明 |
|---|---|---|
| 文件树/目录浏览 | 已有复用 | `WorkspaceFilesPage` + `WorkspaceFileManager`，支持工作区/手机卷、列表/网格、排序、隐藏文件 |
| 文件预览入口 | 已有复用 | `WorkspaceFileViewer` 已接文本、图片、PDF、DOCX、XLSX、音视频与外部打开 |
| 当前目录搜索 | 已有复用 | 文件页现有搜索框，按名称过滤当前目录 |
| 全局文件搜索 | 后续 | 需要递归索引/预算与官方文件接口；不在第一批扩大范围 |
| diff 展示 | 后续 | 官方 0.2.1-rc.5 无一元 diff 端点；需基于会话 `workspace/changes` 或本地编辑基线单独切片，不能把普通文本预览冒充 diff |

### 3.3 设置

| 功能 | 状态 | 说明 |
|---|---|---|
| 模型选择 | 已实现 | 会话级读取 `session/modelCatalog` 与列表 projection，调用 `session/selectModel`；失败 provider 单独展示 |
| 权限模式 | 已实现 | 读取 `permissionPresets/catalog` 与 `permissions` projection，通过官方 `/permission` 命令切换；`danger-full-access` 需二次确认，不自动切换 |
| 通知 | 已有复用 | 设置 → 运行与后台已有聊天通知总开关、权限/渠道入口、更新通知 |
| 外观/语言 | 后续 | 当前主题跟随系统；语言未做应用内切换。新增偏好需要同时改主题/资源与 UI 测试，单独切片 |
| 原生界面开关 | 已有复用 | 设置 → 网页与兼容性保留官方/原生切换；本切片新增会话管理入口 |

### 3.4 技能、插件与斜杠命令

| 功能 | 状态 | 说明 |
|---|---|---|
| Skills 列表 | 已实现 | 官方 `skills/list`，按选中会话加载；显示名称、描述、`modelInvocable`（模型可用/仅人工） |
| 启用状态 | 已实现（只读） | 官方目录返回的条目即当前可用技能；协议无独立“禁用”字段，不伪造开关 |
| 斜杠命令列表 | 已实现 | 官方 `commands/list`，显示 `/name`、描述、输入提示、是否支持附件 |
| 插件列表/启用 | 后续 | 需要官方插件管理器端点与安装状态读取；不在第一批伪造插件开关 |
| 执行斜杠命令 | 后续 | 第一批只展示目录；执行涉及输入/附件/审批，由聊天主链路统一处理 |

### 3.5 通知、远程与安全模式

| 能力 | 状态 | 说明 |
|---|---|---|
| 通知点击定位 | 已有复用 | main 的 `ChatNotificationRouting` 与原生控制器目标选择不改；本切片不抢占通知 PendingIntent |
| 远程 origin | 已实现 | 管理页使用 `RuntimeManager.origin/authenticatedUrl`，因此本地与 SSH 隧道远程端点都走同一精确 loopback origin；远程界面仍保留官方页 |
| 安全模式 | 已实现 | 检测 `Snapshot.recovery.mode`；非普通模式下管理页只读，禁用新建/重命名/归档/模型/权限写入，避免改救援 profile |
| 服务停止/重启 | 已实现 | 页面观察运行状态；停止时清空列表与内存 Cookie，重新运行后重新认证 |

## 4. 文件边界

### 4.1 本切片新增

| 文件 | 作用 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshSessionAdminApi.kt` | 官方会话管理一元协议：method/参数构造、响应解析、纯数据模型；`DshAdminTransport` 薄接口与临时 `DshHttpAdminTransport` |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerController.kt` | 会话管理状态机：列表/过滤/搜索/新建/重命名/归档/未读/模型/权限/技能/命令 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerPage.kt` | 原生会话管理 Compose 页与详情面板 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerActivity.kt` | 独立入口 Activity；复用 RuntimeManager 的 origin 与状态，安全模式只读 |
| `app/src/test/kotlin/com/tanuki/DshSessionAdminApiTest.kt` | 协议解析、认证、错误码、归档/模型/权限/技能/命令请求回归 |
| `app/src/test/kotlin/com/tanuki/DshSessionManagerControllerTest.kt` | 控制器列表/过滤/搜索/新建/重命名/归档/未读/只读/失败路径回归 |

### 4.2 共享文件的最小加法

| 文件 | 改动 | 与并行切片的关系 |
|---|---|---|
| `app/src/main/AndroidManifest.xml` | 新增 `DshSessionManagerActivity` 声明，`exported=false` | 与其他新增 Activity/Provider 的声明并列，不改现有组件 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | `TanukiUiCallbacks` 增加 `openSessionManager` 默认回调；设置 → 网页与兼容性增加一行入口 | 只加字段/一行 UI，不改导航与聊天页 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 回调启动独立管理 Activity | 本次仅 import + 一个回调赋值，已由编排器放行；后续不再修改，入口 hook 由 42 集成 |

### 4.3 明确不修改

- `app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt`
- `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt`
- `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt`
- `app/src/main/kotlin/com/tanuki/web/DshChatBridge.kt`、`DshChatProtocol.kt`、`DshWebSocket.kt`、`DshMuxProtocol.kt`、`DshLive.kt`
- `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt`、`WorkspaceFileViewer.kt`（由文件查看并行切片维护）

## 5. 契约冲突点与合并顺序

1. **一元 transport 复用**：`DshSessionAdminClient` 已收敛到 `DshAdminTransport`（`suspend call(method,args): Any?` + `reset()`），只保留 method/参数、解析和中文错误。当前 `DshHttpAdminTransport` 是临时 HTTP 栈；PR #40 的 `DshTransport`（`connection`、`call`、`live`、`reset`）合并后，改为 `DshAdminTransportAdapter(transport::call, transport::reset)`。会话管理不需要 `live`，也不复制认证/Cookie/URL；切换前不得出现两套不同的 origin/Cookie 规则。
2. **`/connect` 参数**：本切片固定使用 `interactions=none` 的只读一元 Peer，不建立 WebSocket；聊天切片可使用自己的 `answer` Peer。两者 Cookie 绑定同一启动地址但彼此独立。
3. **权限切换**：通过官方 `commands/execute` 执行 `/permission <preset>`，不直接写 `sandbox/mode`/`approval/policy` 事件，不绕过官方权限服务。
4. **归档**：只调用 `workspace/archiveSession`，不直接删 `userdata/dsh` 文件；归档恢复列表需要 WebSocket，留给后续切片。
5. **嵌入原生 DSH 页**：当前通过独立 Activity 交付，避免与聊天页重写冲突。聊天切片稳定后，可把 `DshSessionManagerPage` 作为独立 composable 嵌入，控制器生命周期由页面宿主管理。

## 6. 第一批交付范围

本 PR 交付：

- 会话列表、标题过滤、官方内容搜索、新建、重命名、归档、运行/未读状态。
- 会话级模型选择、权限模式切换（含完全权限二次确认）。
- 会话级 Skills 与斜杠命令目录（只读）。
- 设置页原生会话管理入口；安全模式只读；远程 origin 复用；通知点击路由不变。
- 协议与控制器 JVM 回归。

本 PR 不交付：

- 会话硬删除、归档列表恢复。
- 全局文件搜索、diff 查看器。
- 插件列表/启用、斜杠命令执行。
- 应用内语言切换与主题偏好。
- 真实设备/PRoot 验收；本轮只做 JVM 与构建验证。
