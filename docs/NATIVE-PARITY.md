# 原生界面与官方网页版功能对齐审计

- 审计基线：`origin/main=0555c3c0eaeeed181269b5e68733ccfdd48d9e75`（2026-11-10 核对）。
- PR 基线：本分支已合并 `origin/main=b075725`（2026-11-10）；审计结论仍基于 0555c3c0，main 新增的文件查看器/#35 改动不改变本矩阵行。
- 审计对象：Tanuki 原生界面（Compose 原生页、原生文件页、原生设置、原生浏览器、原生通知/远程/安全模式）与官方网页版（官方客户端 0.2.1-rc.5，只读包与协议）。
- 交付性质：只读审计 + 可执行验收清单；本文件同时保留 PR #40 的落地状态段，不合并、不发布。
- 结论先行：PR #40 已把原生页推进到“可对话”第一批（发送/停止、follow 流式、输入/附件、Markdown/代码块、审批单次允许/拒绝）；官方网页版仍是默认完整交互面。矩阵中的“原生现状”多为审计基线，凡标注本 PR 已落地的行以第 0 节为准；会话生命周期、模型、搜索、工具卡、工作区、设置、插件/终端/交付物仍属后续阶段。

## 0. 本 PR 落地状态（2026-11-10，android_agent-42）

本 PR 把原生页从只读切片推进到“可对话”第一批；只更新本表列出的能力，其余矩阵行仍按审计基线。状态：✅ 本 PR 已实现；◐ 部分；☐ 未实现。

| 矩阵 ID | 状态 | 本 PR 证据 / 边界 |
|---|---|---|
| SESS-01/02 | ◐ | 会话列表、切换、运行状态、通知目标；历史读取最近 120 条 + follow 快照；无搜索/新建/重命名/归档/向前分页 |
| SESS-12 | ◐ | 45 审批面板显示其他会话待处理计数；无完整未读模型 |
| SESS-17 | ✅ | 通知点击目标强制重读/重连；失败保留 pending 给官方回退 |
| CHAT-01 | ✅ | append-origin 用户/助手文本；过滤 replace/注入 |
| CHAT-02 | ◐ | 46：标题/段落/粗体/斜体/粗斜体/删除线/行内代码/嵌套与任务列表/引用/分隔线/带对齐表格；非完整 GFM，HTML 不执行 |
| CHAT-03 | ◐ | 46：围栏代码块、语言标签、复制、横向滚动、长行不换行、可选行号、400 行展示上限；无语法高亮/折叠 |
| CHAT-04 | ◐ | 46：图片占位、中文失败提示、点击查看；本地/内嵌图片采样解码，远程与会话授权图片仍不自动加载 |
| CHAT-05 | ◐ | 46：历史消息中的独立附件链接显示占位卡片并可查看地址；不静默读取工作区文件，文件预览未接 |
| CHAT-06/07 | ✅ | `DshToolCard` 展示工具名/状态/参数摘要/输出/diff/命令；来自 page/follow 工具事件 |
| CHAT-08/09/10 | ☐ | reasoning、token/上下文、轮次过程折叠未接 |
| CHAT-11 | ◐ | 46：发送中占位、失败原文卡片、重试/回填/复制、错误分类与复制；无逐轮失败行/官方 regenerate |
| CHAT-12 | ◐ | 46：代码块与整条消息复制；点赞/点踩反馈仍未接 |
| CHAT-13 | ◐ | 46：链接可点击并二次确认，支持 http/https/mailto/tel；危险协议拒绝，文件链接打开工作区预览未接 |
| CHAT-15 | ✅ | `$events` + `session/follow` + `assistant-stream`；46 按约 32ms 合并累计增量，committed 消息取消待发布帧，避免重复气泡 |
| CHAT-17 | ✅ | 不显示系统提示/普通注入/permission 命令行 |
| INPUT-01/02 | ✅ | composer 多行/键盘发送；`session/prompt` 文本/图片/文件 receipt |
| INPUT-03 | ✅ | 停止按钮 + `session/cancel`，由状态流收敛 |
| INPUT-04/05/06 | ✅ | 斜杠命令、@ 文件/会话引用、附件选择/粘贴/拖入（输入组件） |
| INPUT-12/13 | ◐ | 按会话草稿；默认 queue，steer/队列编辑未做 |
| INPUT-07/08/09 | ☐ | 模型/推理/权限预设选择器未接 |
| ATT-01..04/06 | ◐ | 附件选择、上传、限制、失败重试与文件引用；历史图片读取未接 |
| ATT-05 | ☐ | `session/attachment` 授权图片加载未接 |
| APPROVAL-01/02 | ✅ | 53 审批入口窄条+底部抽屉：来源/工具名/风险/参数摘要/长参数折叠；批量逐条编号、高风险二次确认；`$events/result` 允许一次/拒绝 |
| APPROVAL-05 | ◐ | 提问组件/解析/本地草稿就绪；回答 RPC 待协议确认，页面接线待 51 |
| APPROVAL-07/08 | ✅ | 按审批会话查参数；本次运行记忆可逐条撤销/全部清除，不写配置 |
| APPROVAL-06/10 | ◐ | 计划卡片/解析组件就绪，`DshNativePage` 接线待集成方；审计展示未接；持久策略不做（官方一次性） |
| MODEL-01..10 | ☐ | 模型/推理/用量/配额均未接 |
| TOOL-01/02 | ✅ | 工具卡状态/耗时、输出/错误折叠与复制、diff/文件变更、长输出截断 |
| WORK/文件树/diff/搜索 | ☐ | 另行工作区切片 |
| SET/技能/插件 | ☐/◐ | 通知设置已有；主题/语言/链接打开/技能列表/插件管理未接 |

现有实现测试：本轮审批路由/聊天/工具定向 29 项、完整 JVM 794 tests / 0 failures / 0 errors / 0 skipped（合并 `main=b075725`、含 #41 审批/工具与 #43 composer），lint 0 errors / 101 warnings / 1 hint。**设备、真实 PRoot DSH、输入法、附件上传、审批 waterfall、通知、远程、安全模式均未验证。**

## 1. 状态、优先级与目标层级

| 标记 | 含义 |
|---|---|
| `原生完整` | 原生页或原生应用层可直接完成该功能，不依赖官方 WebView。 |
| `原生部分` | 原生可完成一部分，剩余步骤必须回退官方 WebView。 |
| `仅官方` | 官方 WebView 可用；原生页/原生设置没有对应入口。默认官方模式下产品可用，但不算原生对齐。 |
| `原生替代` | 用不同机制实现同等目的（例如 Android 通知替代网页内提醒）。 |
| `进行中` | 已有并行分支或未提交代码，尚未形成可验收 PR。 |
| `不适用` | 桌面窗口 chrome、浏览器 sandbox 等移动端不适用。 |

优先级：

| 标记 | 含义 |
|---|---|
| `P0` | 原生模式无法完成核心对话闭环：发送、流式、停止、审批/提问回答、错误恢复。 |
| `P1` | 原生模式无法完成常用管理：会话创建/重命名/分叉/归档、模型、附件、搜索、工作区。 |
| `P2` | 高级或可选能力：插件、终端、交付物、快捷键、主题、动态插件等。 |
| `—` | 无缺口，或该能力合理地只由官方 WebView 承载。 |

目标层级（用于排期，不表示已实现）：

- `L1 可对话`：原生页可发送/停止、实时看回复、回答审批与提问、看到失败并可重试。
- `L2 可管理`：L1 + 新建/重命名/分叉/归档会话、模型选择、附件、会话搜索、工作区选择。
- `L3 完整对齐`：L2 + 工具卡片、终端/浏览器/任务/子代理、插件/技能、交付物、完整设置。

### 1.1 P0 拆解与归属

P0 不是一行笼统的“原生页不可用”，按矩阵行拆开，便于按图验收：

| P0 能力 | 矩阵行 | 归属 | 验收 |
|---|---|---|---|
| 文本输入/composer | INPUT-01 | 46 输入 | A-INPUT-02 |
| 发送提示词 | INPUT-02 | 46 输入（发送）+ 42 聊天链路（follow/回显） | A-INPUT-01、A-INPUT-02 |
| 停止/取消当前回合 | INPUT-03 | 42 聊天链路（session/cancel）+ 46 输入（停止按钮） | A-INPUT-01、A-INPUT-02 |
| 实时流式回复 | CHAT-15 | 42 聊天链路 | A-CHAT-01、A-CHAT-03 |
| 历史/follow 快照 | SESS-02 | 42 聊天链路 | A-SESS-01、A-CHAT-01 |
| 失败行/错误恢复 | CHAT-11 | 42 聊天链路 | A-CHAT-01、A-CHAT-03 |
| 审批请求展示/批准拒绝 | APPROVAL-01、APPROVAL-02 | 45 审批（42 提供 waterfall 事件） | A-APPROVAL-01、A-APPROVAL-02 |
| 用户提问/计划审阅 | APPROVAL-05、APPROVAL-06 | 45 审批（42 提供事件解析） | A-APPROVAL-01、A-APPROVAL-02 |
| 待处理交互状态 | SESS-12 | 42 事件 + 45 审批 | A-APPROVAL-01、A-NOTIFY-01 |
| 新建会话/发送入口 | SESS-04 | 44 会话管理（创建）+ 46 输入（composer） | A-SESS-02、A-INPUT-02 |

说明：

- `42 聊天链路`：session/follow、`$events`、assistant-stream、`session/cancel` 等；当前未提交，矩阵状态保持 `进行中`。
- `45 审批`：approval/question/plan-review 的展示与回答；当前未提交，矩阵状态保持 `仅官方/进行中`。
- `46 输入`：composer、发送、附件、草稿/队列；当前未提交，矩阵状态保持 `仅官方`。
- `44 会话管理`：会话管理实现（DshSessionManagerActivity/Controller/Page、DshSessionAdminApi）；矩阵状态在实现落地前保持 `仅官方/进行中`，由矩阵属主核对 PR/测试证据后更新。

## 2. 方法与证据边界

- 官方行为证据来自只读解包的官方客户端 0.2.1-rc.5 包、其 README 与公开协议方法名（`session/*`、`workspace/*`、`workspaceFiles/*`、`skills/list`、`commands/*`、`settings/*`、`credentials/*`、`llm/*`、`permissionPresets/catalog`、`pluginManager/*`、`pluginMarket/*`、`terminal/*`、`schedule/*`、`goals/*`、`job/*`、`subagents/*`、`userQuestions/*`、`approval/request`、`fileUploads/upload` 等）。本文件不记录官方实现名称、内部源码路径或用户数据。
- 原生行为证据来自基线源码与既有测试：`DshNativePage.kt`、`DshNativeController.kt`、`DshNativeApi.kt`、`DshWebView.kt`、`TanukiUi.kt`、`WorkspaceFilesPage.kt`、`WorkspaceFileViewer.kt`、`RemoteHost*`、`RuntimeFailurePolicy.kt` 及 `app/src/test`、`app/src/androidTest`。
- 并行分支只做只读核对（2026-11-10）：`android_agent-42` 已新增未提交的 `DshMuxProtocol.kt`/`DshWebSocket.kt`；`android_agent-45`、`android_agent-46`、`android_agent-48` 已建分支但无提交；`android_agent-44` 实现会话管理（不写矩阵）。未发现与本文件冲突的写入。
- **本轮没有运行产品测试、构建、仪器或设备测试**。下文“验收用例”是待执行方案，不是已通过结果。涉及真实模型/账号的用例仍受用户暂停项约束。
- “官方 WebView 可用”不等于“原生已对齐”。矩阵把两者分开记录，避免把回退能力写成原生实现。

## 3. 功能矩阵

> 读法：`缺口/归属/验收` 一列给出优先级、原生承载文件或并行归属、验收用例编号。验收步骤见第 4 节。

### 3.1 会话（SESS）

| ID | 功能 | 官方网页版 | 原生现状 | 缺口 / 归属 / 验收 |
|---|---|---|---|---|
| SESS-01 | 会话列表 | 侧栏按工作区分组显示标题、更新时间、运行/待处理/未读状态 | `原生部分`：`DshNativePage` 顶部横向会话条，仅标题、运行中/已结束、时间 | P1 · `DshNativePage.kt`/`DshNativeApi.kt` · 现状 android_agent-33/PR #26；会话管理实现 44 · A-SESS-01、A-SESS-03 |
| SESS-02 | 选择会话并读历史 | 打开会话即加载 follow 快照与历史页，支持向前分页 | `原生部分`：选中后读 `session/projections` + `session/page`，最多 120 条，只有“更早历史请在官方界面查看” | P0/P1 · `DshNativeController.kt` · 42 聊天链路（follow 进行中） · A-SESS-01、A-CHAT-01 |
| SESS-03 | 历史向前分页 | `loadOlder()`/轮次跳转，保持实时事件可见 | `仅官方`：原生无 load older/跳转 | P1 · `DshNativeApi.kt` · 42 聊天链路（分页/轮次跳转） · A-SESS-01 |
| SESS-04 | 新建会话/空白会话复用 | New Session 优先当前/最近工作区，复用空白会话与草稿 | `仅官方`：原生页不创建会话 | P0 · 44 会话管理（创建）+ 46 输入（composer） · A-SESS-02、A-INPUT-02 |
| SESS-05 | 重命名会话 | 行菜单/双击标题，预填当前标题，可固定自动标题 | `仅官方` | P1 · `session/rename` · 44 会话管理 · A-SESS-02 |
| SESS-06 | 分叉会话 | 从最近已完成轮次或指定 `atSeq` 创建子会话，不自动导航 | `仅官方` | P1 · `session/fork` · 44 会话管理 · A-SESS-02 |
| SESS-07 | 归档/取消归档 | 静止会话直接归档；运行中会话先列出工作并确认“停止并归档”；支持撤销 | `仅官方` | P1 · `workspace/archiveSession`、`unarchiveSession` · 44 会话管理 · A-SESS-02 |
| SESS-08 | 置顶/手动排序 | 置顶、拖拽、最近更新/手动排序；顺序本地保存 | `仅官方`：原生会话条顺序固定 | P2 · `workspace/pinSession`、`insertSessionBefore` · 44 会话管理（排序/置顶，待确认） · A-SESS-02 |
| SESS-09 | 删除工作区/会话 | 工作区删除确认，会话留在 Ungrouped | `仅官方` | P2 · `workspace/delete` · 44 会话管理 · A-SESS-02 |
| SESS-10 | 会话搜索 | 标题/工作区即时匹配 + 250ms 防抖的会话内容搜索，最多 20 条 | `仅官方`：原生只有文件页“搜索当前文件夹” | P1 · `session/search` · 44 会话管理（搜索，待确认） · A-SEARCH-01、A-SEARCH-02 |
| SESS-11 | 工作区分组/树形层级 | 按工作区或工作区树分组，折叠、层级拖拽、祖先展开 | `仅官方`：原生是扁平会话条 | P2 · `workspace/follow` · 44 会话管理（工作区树，待确认） · A-FILE-05 |
| SESS-12 | 待处理交互/未读状态 | 行内显示等待审批/计划待审/等待回答、warning 点、未读完成 | `仅官方`：原生只区分 running | P1 · `DshMuxProtocol` 事件 · 42 聊天链路（事件）+ 45 审批（状态展示） · A-APPROVAL-01、A-NOTIFY-01 |
| SESS-13 | 子代理会话 | 侧栏隐藏 subagent 来源会话，可按直接父子地址打开 | `仅官方` | P2 · `subagents/*` · 42 聊天链路（子会话路由，待确认） · A-TOOL-02 |
| SESS-14 | 会话目标（goal） | 输入框上方目标条，支持编辑/暂停/恢复/清除与 `/goal` 气泡 | `仅官方` | P2 · `goals/*` · 44 会话管理（目标，待确认） · A-SESS-05 |
| SESS-15 | 定时任务/自动化 | Schedule 列表、历史、更新/删除，活动任务在会话行显示时钟 | `仅官方` | P2 · `schedule/*` · 未排期 · A-SESS-06 |
| SESS-16 | 复制会话 ID/日志导出 | 行菜单复制完整 ID；会话日志下载 | `仅官方` | P2 · `session/*`、日志路由 · 44 会话管理（复制 ID/日志导出，待确认） · A-SESS-07 |
| SESS-17 | 通知点击定位会话 | 通知携带会话 id，点击后展开侧栏、清搜索、展开分组并选中 | `原生完整`：`DshNativeController.openSession()` 冷启动/已加载均选中，失败保留 pending 给官方回退 | — · `DshNativeController.kt` · android_agent-29/PR #22、android_agent-33/PR #26 · A-SESS-03、A-NOTIFY-02 |
| SESS-18 | 会话级模型选择 | 每个会话可选模型，Host 返回模型目录 | `仅官方` | P1 · `session/selectModel`、`session/modelCatalog` · 44 会话管理（模型选择，待确认） · A-MODEL-01 |
| SESS-19 | 队列/中途引导 | 排队消息、编辑/移除、steering 在下一步边界送达 | `仅官方` | P1 · `session/updateQueue`、`session/prompt` · 46 输入（队列/steering） · A-INPUT-01 |
| SESS-20 | 草稿保留 | 切换会话/刷新/重启浏览器后保留未提交输入 | `仅官方` | P2 · 原生 composer · 46 输入（草稿） · A-INPUT-02 |

### 3.2 对话（CHAT）

| ID | 功能 | 官方网页版 | 原生现状 | 缺口 / 归属 / 验收 |
|---|---|---|---|---|
| CHAT-01 | 用户/助手文本 | 按官方事件渲染用户与助手文本，保留来源/时间/操作 | `原生部分`：`DshNativePage` 只显示 append-origin 的 `user/message` 与 `assistant/message` 文本，单条上限 12,000 字符 | — · `DshNativeApi.kt` · android_agent-33/PR #26 · A-CHAT-01 |
| CHAT-02 | Markdown/GFM | 标题、列表、表格、链接、引用、任务列表、代码块 | `原生部分`：46 已支持标题/列表/引用/表格/分隔线/行内样式/代码块；HTML 不执行，非完整 GFM | P2 · `DshMarkdown.kt` · android_agent-51 · A-CHAT-02、A-CHAT-03 |
| CHAT-03 | 代码块/高亮/复制 | 语法高亮、复制、行号/折叠 | `原生部分`：等宽/横向滚动/长行不换行/复制/可选行号；无高亮与折叠 | P2 · `DshMarkdown.kt` · android_agent-51 · A-CHAT-03 |
| CHAT-04 | 消息图片 | 用户/助手图片、内嵌预览、点击浮层、失败状态 | `原生部分`：占位/中文失败提示/点击查看；本地与内嵌图可解码，远程与会话授权图不自动加载 | P1 · `DshMarkdown.kt` · android_agent-51 · A-ATT-01、A-CHAT-03 |
| CHAT-05 | 文件附件/引用卡片 | 附件按输入顺序显示，文件引用可打开右侧预览 | `原生部分`：独立附件链接显示占位卡片并可复制地址；不静默读工作区，预览未接 | P1 · `DshMarkdown.kt` · android_agent-51 · A-ATT-01、A-CHAT-03 |
| CHAT-06 | 工具调用卡片 | 工具名、状态、参数摘要、可展开详情、关联审批 | `原生完整`：`DshToolCard`，来自 page/follow 工具事件；计划工具由 `DshPlanCard` 单独承载 | — · `DshToolTimeline`/`DshNativeController.tools`/`DshPlanParser` · android_agent-53 · A-TOOL-01、A-TOOL-02 |
| CHAT-07 | 工具结果/错误/输出折叠 | 结果、错误、退出码、长输出折叠/展开 | `原生完整`：输出/错误折叠与复制、长输出截断、diff/文件变更、真实耗时 | — · `DshToolCard` · android_agent-53 · A-TOOL-01 |
| CHAT-08 | 推理/思考过程 | 思考预览、过程分组、完全展开模式 | `仅官方`：原生只取 text-delta，不显示 reasoning | P2 · `DshFollowParser` · android_agent-42 · A-CHAT-02 |
| CHAT-09 | Token 用量/上下文 | 每轮 token、输出速度、缓存命中、会话统计 | `仅官方` | P2 · 原生统计条 · 未排期 · A-CHAT-02 |
| CHAT-10 | 轮次折叠/过程分组 | 已完成轮次过程可折叠，最终答案不隐藏 | `仅官方` | P2 · 原生消息分组 · 未排期 · A-CHAT-03 |
| CHAT-11 | 失败行/重试/欠费 | 终止轮次红点、失败摘要、重试、欠费中立文案 | `原生部分`：46 发送失败卡片保留原文，支持重试/回填/复制，错误分类可读；无逐轮失败行/regenerate | P0/P1 · `DshNativePage.kt` · android_agent-51 · A-CHAT-01、A-CHAT-03 |
| CHAT-12 | 复制/反馈 | 消息复制、点赞/点踩反馈 | `原生部分`：46 代码块与整条消息复制；点赞/点踩未接 | P2 · `DshNativePage.kt` · android_agent-51 · A-CHAT-05 |
| CHAT-13 | 文件链接/侧栏预览 | Markdown 文件链接在右侧栏打开，支持 `#L24` 行号 | `原生部分`：46 链接可点击并二次确认；危险协议拒绝；文件/工作区链接只提示授权，侧栏预览未接 | P2 · `DshMarkdown.kt` · android_agent-51 · A-CHAT-05 |
| CHAT-14 | 计划审阅卡片 | 计划待审条、查看全文、同意执行/要求修改 | `进行中`：`DshPlanCard`/`DshPlanParser` 组件就绪，待 42 接线页面状态 | P1 · `userQuestions/*`、`approval/request` · android_agent-53 + 42 · A-APPROVAL-02 |
| CHAT-15 | 实时流式回复 | follow 快照 + assistant-stream 增量 + settlement | `原生部分`：46 已接 UI 并按帧合并累计增量，committed 取消待发布帧；设备流式未验 | P0 · `DshNativeController.kt`/`DshChatRender.kt` · android_agent-51 · A-CHAT-01、A-CHAT-03 |
| CHAT-16 | 历史滚动位置 | 切换/返回恢复滚动位置 | `仅官方` | P2 · 原生列表状态 · 未排期 · A-CHAT-05 |
| CHAT-17 | 系统提示/上下文隐藏 | 不显示系统提示、普通上下文注入、permission 命令行 | `原生部分`：原生只取文本事件，天然不显示这些行 | — · `DshNativeApi.kt` · android_agent-33 · A-CHAT-01 |
| CHAT-18 | 交付物（deliverables） | 轮次页脚交付物卡片、右侧预览 | `仅官方` | P2 · 官方交付物协议 · 未排期 · A-FILE-03 |
| CHAT-19 | Goal/Todo/Queue 卡片 | 输入框上方上下文堆栈 | `仅官方` | P2 · `goals/*`、`session/updateQueue` · 未排期 · A-CHAT-06 |
| CHAT-20 | 中断轮次显示 | 进程中途退出显示本地化停止行，不显示代码标签 | `仅官方` | P2 · 原生消息状态 · 未排期 · A-CHAT-05 |

### 3.3 输入（INPUT）

| ID | 功能 | 官方网页版 | 原生现状 | 缺口 / 归属 / 验收 |
|---|---|---|---|---|
| INPUT-01 | 文本输入/多行/IME | 多行编辑、输入法组合、Shift+Enter 换行 | `仅官方`：原生页无输入框 | P0 · 原生 composer · 46 输入（未提交） · A-INPUT-02 |
| INPUT-02 | 发送提示词 | `session/prompt`，支持文本+附件+requestId 幂等 | `仅官方` | P0 · `session/prompt` · 46 输入（发送）+ 42 聊天链路（follow/回显） · A-INPUT-01、A-INPUT-02 |
| INPUT-03 | 停止/取消运行 | 停止按钮读取运行状态，调用 `session/cancel` | `仅官方`：原生只有停止 DSH 服务，不是停止当前回合 | P0 · `session/cancel` · 42 聊天链路（cancel）+ 46 输入（停止按钮） · A-INPUT-01、A-INPUT-02 |
| INPUT-04 | 斜杠命令 | 命令目录、执行、变更通知 | `仅官方` | P1 · `commands/*` · 未排期 · A-SKILL-01 |
| INPUT-05 | @ 文件引用/文件提及 | 输入框提及工作区文件，生成引用卡片 | `仅官方` | P1 · `fileReferences/list`、`sessionReferenceResolver/*` · 46 输入 · A-INPUT-01、A-INPUT-06 |
| INPUT-06 | 附件/图片选择 | 选择、粘贴、拖放，按顺序进入草稿 | `仅官方` | P1 · `fileUploads/upload` · 46 输入 · A-ATT-01、A-ATT-02 |
| INPUT-07 | 模型选择器 | 输入框旁模型选择，Host 校正实际值 | `仅官方` | P1 · `session/selectModel` · 未排期 · A-MODEL-02 |
| INPUT-08 | 推理强度选择 | 模型支持的 reasoning effort 选择 | `仅官方` | P1 · `llm/*` · 未排期 · A-MODEL-02 |
| INPUT-09 | 权限预设选择 | read-only/workspace-write/danger-full-access 等预设目录 | `仅官方`：原生只显示沙箱能力，不修改 DSH 策略 | P1 · `permissionPresets/catalog` · 未排期 · A-APPROVAL-04 |
| INPUT-10 | 计划模式 | plan mode 与计划审阅联动 | `仅官方` | P2 · `agentPresets/*`、`userQuestions/*` · 未排期 · A-INPUT-05 |
| INPUT-11 | 语音输入 | 语音转文字/语音输入包 | `仅官方` | P2 · 官方语音插件 · 未排期 · A-INPUT-04 |
| INPUT-12 | 草稿/队列/中途引导 | 草稿本地保存、排队、steering | `仅官方` | P1 · `session/updateQueue` · 46 输入（队列/steering） · A-INPUT-01 |
| INPUT-13 | 忙碌时发送行为 | 发送/排队/中途引导策略可配置 | `仅官方` | P1 · `session/prompt` · 46 输入（忙碌时发送） · A-INPUT-01 |
| INPUT-14 | 上下文用量指示 | 输入框统计条：轮次、步骤、速度、token、缓存 | `仅官方` | P2 · 原生统计 · 未排期 · A-CHAT-02 |
| INPUT-15 | 快捷键 | 发送、换行、停止、搜索、新建等可配置快捷键 | `仅官方`：原生无自定义快捷键 | P2 · 官方快捷键服务 · 未排期 · A-SET-05 |

### 3.4 附件（ATT）

| ID | 功能 | 官方网页版 | 原生现状 | 缺口 / 归属 / 验收 |
|---|---|---|---|---|
| ATT-01 | 文件上传 | `fileUploads/upload`，分块/进度/失败重试，附件随 prompt 提交 | `仅官方` | P1 · `fileUploads/*` · 46 输入（未提交） · A-ATT-01 |
| ATT-02 | 图片上传/粘贴/拖放 | 图片预览、总量上限、base64/二进制传输 | `仅官方` | P1 · `fileUploads/upload` · 46 输入 · A-ATT-01、A-ATT-02 |
| ATT-03 | 附件预览/移除 | 草稿附件卡片、缩略图、移除、失败保留 | `仅官方` | P1 · 原生 composer · 46 输入 · A-ATT-02 |
| ATT-04 | 大小/类型/数量限制 | 单请求/图片总量/请求体上限，超限明确报错 | `仅官方` | P1 · `fileUploads/*` · 46 输入 · A-ATT-01 |
| ATT-05 | 会话媒体授权读取 | `session/attachment` 按会话授权，图片 URL 随 binding 释放撤销 | `仅官方` | P1 · `session/attachment` · 未排期 · A-ATT-01 |
| ATT-06 | 远程/工作区文件引用 | 工作区文件引用与 `@` 提及，不复制文件 | `仅官方` | P1 · `fileReferences/list` · 46 输入 · A-INPUT-01、A-INPUT-06 |
| ATT-07 | Office/PDF 转换预览 | `officeToPdf/*` 生成/渲染 | `仅官方`：原生文件页已有 PDF/DOCX/XLSX 只读预览，但不在对话内 | P2 · `officeToPdf/*` · 未排期 · A-ATT-03 |
| ATT-08 | 上传进度/失败重试 | 阶段/字节进度、失败可重试 | `仅官方` | P2 · `fileUploads/*` · 46 输入 · A-ATT-02 |

### 3.5 模型/推理（MODEL）

| ID | 功能 | 官方网页版 | 原生现状 | 缺口 / 归属 / 验收 |
|---|---|---|---|---|
| MODEL-01 | 模型目录 | `session/modelCatalog`、`llm/listProviders`、`llm/discoverModels` | `仅官方` | P1 · `llm/*` · 未排期 · A-MODEL-01 |
| MODEL-02 | 选择模型 | 会话级/默认模型选择，Host 校正 | `仅官方` | P1 · `session/selectModel` · 未排期 · A-MODEL-02 |
| MODEL-03 | 推理强度/思考预算 | 模型能力对应的 reasoning effort | `仅官方` | P1 · `llm/*` · 未排期 · A-MODEL-02 |
| MODEL-04 | 提供方配置/API Key | `credentials/*`、`settings/*` 配置提供方与密钥 | `仅官方`：原生远程凭据用 Keystore，DSH 模型密钥仍由官方设置管理 | P1 · `credentials/*` · 未排期 · A-MODEL-01 |
| MODEL-05 | 默认模型 | 设置默认模型，新会话继承 | `仅官方` | P1 · `session/initializeDefaultModel` · 未排期 · A-MODEL-02 |
| MODEL-06 | 模型发现 | 按提供方发现可用模型 | `仅官方` | P2 · `llm/discoverModels` · 未排期 · A-MODEL-01 |
| MODEL-07 | Token/上下文用量 | 每轮/会话 token、缓存命中、速度 | `仅官方` | P2 · 原生统计 · 未排期 · A-CHAT-02 |
| MODEL-08 | 自动审查 | auto-review 权限/流程 | `仅官方` | P2 · 官方插件 · 未排期 · A-MODEL-04 |
| MODEL-09 | CoT 翻译 | 思考过程翻译 | `仅官方` | P2 · 官方插件 · 未排期 · A-MODEL-04 |
| MODEL-10 | 账户余额/配额 | 账户状态、余额、配额失败提示 | `仅官方` | P2 · `account/*` · 未排期 · A-MODEL-05 |

### 3.6 审批/权限（APPROVAL）

| ID | 功能 | 官方网页版 | 原生现状 | 缺口 / 归属 / 验收 |
|---|---|---|---|---|
| APPROVAL-01 | 审批请求展示 | Agent-scoped waterfall，接管输入框，展示工具/原因/详情 | `原生完整`：输入框上方“⚑ 待审批 N 项”窄条 + 底部抽屉；抽屉内 `DshApprovalPanel` 展示来源/工具名/风险等级/参数摘要/完整参数折叠/其他会话计数；批量逐条编号，高风险允许二次确认 | — · `DshApprovalEntryBar`/`DshApprovalSheet`/`DshApprovalPanel` · android_agent-53 · A-APPROVAL-01、A-APPROVAL-02 |
| APPROVAL-02 | 批准/拒绝 | Enter 批准、Esc 拒绝、按钮拒绝，一次性决定 | `原生完整`：允许一次/拒绝经 `$events/result` 回传 | — · `DshApprovalSession` · android_agent-45 + 42 接线 · A-APPROVAL-01、A-APPROVAL-02 |
| APPROVAL-03 | 权限预设 | 进程级预设目录，会话内切换 | `仅官方`：原生只读显示沙箱能力 | P1 · `permissionPresets/catalog` · 未排期 · A-APPROVAL-04 |
| APPROVAL-04 | 沙箱/命令限制 | workspace-write 无后端时拒绝 bash；danger-full-access 需用户明确选择 | `原生部分`：`SetupScreens` 显示 bwrap/Landlock 探测与说明，不修改策略 | — · `RuntimeManager`/`SetupScreens.kt` · 既有 · A-SAFE-03 |
| APPROVAL-05 | 用户提问 | `ask_user_question` 卡片、选项/自定义答案、跳过、倒计时 | `进行中`：`DshQuestionParser`/`DshQuestionDraft`/`DshQuestionCard` 已支持单选/多选/文本、选项与本地必填校验；回答 RPC 待协议确认，页面接线待 51 | P0 · `userQuestions/*` · android_agent-53 + 51 · A-APPROVAL-01、A-APPROVAL-02 |
| APPROVAL-06 | 计划审阅 | 计划待审条、查看全文、同意执行/要求修改 | `进行中`：`DshPlanCard` 多步骤进度与审阅按钮、`DshPlanParser` 解析计划事件/计划工具调用；`DshNativePage` 状态接线待集成方 | P1 · `userQuestions/*`/计划事件 · android_agent-53 + 42 接线 · A-APPROVAL-02 |
| APPROVAL-07 | 工具详情/参数 | 按需渲染关联工具详情，不泄露敏感参数 | `原生完整`：按审批会话查工具参数，摘要+完整参数折叠 | — · `DshNativeController.tool`/`DshApprovalPanel` · android_agent-53 · A-TOOL-02 |
| APPROVAL-08 | 记住/一次性批准 | 每次批准只对应一个请求，不跨请求复用 | `原生完整（本次运行内存）`：按会话+工具+风险复用，可逐条撤销/全部清除；不写官方持久配置 | — · `DshApprovalMemory`/`DshApprovalController` · android_agent-53 · A-APPROVAL-01 |
| APPROVAL-09 | 审批通知 | 等待审批/提问时提醒，点击定位会话 | `原生替代`：Android 通知已实现（PR #22），点击定位会话；无内联批准动作 | — · `ChatNotification*` · android_agent-29/PR #22 · A-NOTIFY-01、A-NOTIFY-02 |
| APPROVAL-10 | 审计日志 | 每个请求与结果写入会话审计日志 | `仅官方` | P2 · 官方审计 · 未排期 · A-APPROVAL-03 |

### 3.7 工具调用（TOOL）

| ID | 功能 | 官方网页版 | 原生现状 | 缺口 / 归属 / 验收 |
|---|---|---|---|---|
| TOOL-01 | 工具调用卡片/状态 | 运行/完成/失败/取消状态，参数摘要，展开详情 | `原生完整`：运行/成功/失败状态、真实耗时、参数摘要、展开详情 | — · `DshToolCard` · android_agent-53 · A-TOOL-01 |
| TOOL-02 | 工具输出/错误 | 输出、退出码、错误、长输出折叠 | `原生完整`：输出/错误、命令/cwd、diff/文件变更、截断提示、输出与错误复制 | — · `DshToolCard` · android_agent-53 · A-TOOL-01 |
| TOOL-03 | 终端面板 | 右侧栏终端、多 shell、重连、快捷键 | `仅官方`：原生浏览器/文件页不是会话终端 | P2 · `terminal/*` · 未排期 · A-TOOL-03 |
| TOOL-04 | 浏览器面板 | 右侧栏浏览器、tab、sandbox、链接偏好 | `原生替代/仅官方`：原生有独立浏览器（tab/收藏/历史/下载/AI 操作授权），但不作为会话侧栏面板 | P2 · `Browser*`/官方 sidebar browser · 未排期 · A-TOOL-03 |
| TOOL-05 | 文件工具/预览 | 会话内文件树、预览、变更 | `原生部分`：原生文件页可浏览/预览/编辑，但不是会话内工具卡片 | P2 · `WorkspaceFilesPage.kt` · 既有 · A-FILE-02 |
| TOOL-06 | 后台任务（jobs） | 任务列表、实时输出、等待/取消、完成通知 | `仅官方` | P2 · `job/*` · 未排期 · A-TOOL-03 |
| TOOL-07 | 子代理 | 子会话、父子地址、中断/提示 | `仅官方` | P2 · `subagents/*` · 未排期 · A-TOOL-03 |
| TOOL-08 | 技能调用 | `skills/list`、技能卡片、斜杠命令 | `仅官方`：原生运行时装 browser/phone 受管技能，但无 UI | P2 · `skills/list` · 未排期 · A-SKILL-02 |
| TOOL-09 | Office/PDF 工具 | Office 转 PDF/渲染 | `仅官方` | P2 · `officeToPdf/*` · 未排期 · A-TOOL-04 |
| TOOL-10 | 动态插件工具 | 动态 Cordis 插件提供的工具/UI | `仅官方` | P2 · `dynamicCordisRunner/*` · 未排期 · A-SKILL-03 |

### 3.8 工作区/文件（FILE）

| ID | 功能 | 官方网页版 | 原生现状 | 缺口 / 归属 / 验收 |
|---|---|---|---|---|
| FILE-01 | 工作区列表/添加 | 侧栏工作区、目录选择器、添加工作区 | `仅官方`：原生只有固定 `/workspaces` 文件页 | P1 · `workspace/*`、`directoryPicker/*` · 未排期 · A-FILE-05 |
| FILE-02 | 工作区重命名/删除/重排 | 行菜单、确认、层级拖拽 | `仅官方` | P2 · `workspace/rename`、`delete`、`insertBefore` · 未排期 · A-FILE-05 |
| FILE-03 | 工作区树形层级 | 按工作区树分组、祖先展开、子级优先 | `仅官方` | P2 · `workspace/follow` · 未排期 · A-FILE-05 |
| FILE-04 | 文件树/浏览 | 右侧文件树、目录展开、面包屑 | `原生完整`：`WorkspaceFilesPage` 工作区/手机文件、可横滚面包屑、列表/网格；顶部 56dp 主操作行 | — · `WorkspaceFilesPage.kt` · 既有 · A-FILE-02 |
| FILE-05 | 文件预览 | 文本、Markdown、图片、音频、视频、PDF、DOCX、XLSX、HTML | `原生部分`：文本/图片/音视频/PDF/DOCX/XLSX 已支持；`.doc` 明确不支持内置预览；Markdown/HTML 按文本 | P2 · `WorkspaceFileViewer.kt` · android_agent-31/32/35、41、39 · A-FILE-01、A-FILE-02 |
| FILE-06 | 文本编辑 | 会话内编辑/保存，冲突检测 | `原生部分`：原生文件页可编辑 ≤256 KiB 文本，保存前检查 revision；DSH 运行时禁止写 | P2 · `WorkspaceFilesPage.kt`/`WorkspaceFileManager.kt` · 既有 · A-FILE-02 |
| FILE-07 | 新建/重命名/复制/移动/删除 | 文件树操作，冲突/链接/非空目录保护 | `原生完整`：原生文件页支持，DSH 运行时只读 | — · `WorkspaceFilesPage.kt` · 既有 · A-FILE-02 |
| FILE-08 | 导入/导出 | 导入文件到工作区、导出项目 ZIP/单文件 | `原生完整`：原生文件页导入/导出，排除常见凭据 | — · `WorkspaceFilesPage.kt`/`ProjectArchive.kt` · 既有 · A-FILE-02 |
| FILE-09 | 当前文件夹搜索 | 文件树内搜索 | `原生完整`：原生文件页搜索当前文件夹 | — · `WorkspaceFilesPage.kt` · 既有 · A-SEARCH-02 |
| FILE-10 | 排序/网格/隐藏文件 | 名称/时间/大小、列表/网格、点号文件 | `原生完整`：原生文件页支持 | — · `WorkspaceFilesPage.kt` · 既有 · A-FILE-02 |
| FILE-11 | 文件变更/diff | 工作区变更列表、diff 预览 | `仅官方` | P2 · `workspaceFiles/changes`、`workspaceFiles/read` · 未排期 · A-FILE-03 |
| FILE-12 | 交付物 | 轮次交付物卡片/预览 | `仅官方` | P2 · 官方交付物协议 · 未排期 · A-FILE-03 |
| FILE-13 | Git worktree | 实验性 worktree 管理 | `仅官方` | P2 · 官方 worktree 插件 · 未排期 · A-FILE-03 |
| FILE-14 | 在文件管理器中打开 | `session/openWorkspacePath`、系统关联打开 | `原生部分`：原生文件页可用系统 Intent 打开音视频/文档；无“在文件管理器中显示” | P2 · `session/openWorkspacePath` · 未排期 · A-FILE-06 |
| FILE-15 | 手机文件访问/写入授权 | 官方网页无此能力 | `原生完整`：原生文件页手机卷只读浏览、单独授权写入、停止 DSH 恢复只读 | — · `PhoneFileService.kt`/`WorkspaceFilesPage.kt` · 既有 · A-FILE-02、A-FILE-04 |
| FILE-16 | 工作区容量/存储统计 | 官方网页无 Tanuki 容量预算/目录占用 | `原生完整`：原生设置“存储与空间”容量预算、目录占用、清理缓存 | — · `WorkspaceStorageControls.kt` · 既有 · A-SET-08 |

### 3.9 搜索（SEARCH）

| ID | 功能 | 官方网页版 | 原生现状 | 缺口 / 归属 / 验收 |
|---|---|---|---|---|
| SEARCH-01 | 会话标题搜索 | 侧栏搜索标题/工作区，即时结果 | `仅官方` | P1 · `session/search` · 未排期 · A-SEARCH-01 |
| SEARCH-02 | 会话内容搜索 | 250ms 防抖、摘要片段、最多 20 条 | `仅官方` | P1 · `session/search` · 未排期 · A-SEARCH-01 |
| SEARCH-03 | 文件/文件夹搜索 | 文件树搜索 | `原生完整`：原生文件页“搜索当前文件夹” | — · `WorkspaceFilesPage.kt` · 既有 · A-SEARCH-02 |
| SEARCH-04 | 对话内查找 | 页内查找/匹配跳转 | `仅官方` | P2 · 原生消息列表 · 未排期 · A-CHAT-05 |
| SEARCH-05 | 命令面板/快捷键速查 | 快捷键速查、命令目录 | `仅官方` | P2 · 官方快捷键服务 · 未排期 · A-SET-05 |
| SEARCH-06 | 全局搜索 | 跨会话/文件/命令的统一搜索 | `仅官方` | P2 · 官方组合 · 未排期 · A-SEARCH-03 |
| SEARCH-07 | 浏览器页内查找 | 浏览器 tab 内查找/上下匹配 | `原生完整`：原生浏览器页内查找、匹配计数 | — · `AgentBrowserPage.kt` · 既有 · A-SEARCH-02 |

### 3.10 设置（SET）

| ID | 功能 | 官方网页版 | 原生现状 | 缺口 / 归属 / 验收 |
|---|---|---|---|---|
| SET-01 | 官方 DSH 设置入口 | 侧栏 Settings，设置文档/分类面板 | `原生部分`：原生设置“网页与兼容性”可切官方/原生、打开官方页面；不内嵌官方设置 | P1 · `TanukiUi.kt`/`DshWebView.kt` · android_agent-33/PR #26 · A-SET-02 |
| SET-02 | 模型/提供方设置 | 提供方、模型、凭据、默认模型 | `仅官方` | P1 · `settings/*`、`credentials/*`、`llm/*` · 未排期 · A-MODEL-01 |
| SET-03 | 权限/沙箱设置 | 权限预设、工具审批策略 | `仅官方`：原生只显示沙箱能力 | P1 · `permissionPresets/catalog` · 未排期 · A-APPROVAL-04 |
| SET-04 | 插件管理设置 | 安装/启用/禁用/卸载、版本豁免、市场 | `仅官方` | P2 · `pluginManager/*`、`pluginMarket/*` · 未排期 · A-SKILL-02 |
| SET-05 | 快捷键设置 | 每设备自定义、冲突检测、恢复默认 | `仅官方` | P2 · 官方快捷键服务 · 未排期 · A-SET-05 |
| SET-06 | 主题/外观 | 跟随系统/浅色/深色、字号、字体 | `原生部分`：原生 Compose 跟随系统深浅色；无手动主题/字号设置 | P2 · `TanukiDesign.kt` · 既有 · A-SET-02 |
| SET-07 | 语言/本地化 | 官方 zh/en 等 locale，切换即时生效 | `仅官方`：原生界面中文硬编码 | P2 · 原生字符串资源 · 未排期 · A-SET-06 |
| SET-08 | 通知设置 | 应用内通知/系统通知入口 | `原生完整`：原生“运行与后台”聊天通知总开关、权限/系统入口、更新通知 | — · `TanukiUi.kt`/`ChatNotification*` · android_agent-29/PR #22、24/PR #19 · A-NOTIFY-02 |
| SET-09 | 应用更新 | 检查/下载/覆盖安装、后台检查 | `原生完整`：原生“应用更新”卡片、后台每 30 分钟检查、权限申请 | — · `ApplicationUpdateCard.kt`/`BackgroundUpdateChecks.kt` · android_agent-24/PR #19 · A-SET-07 |
| SET-10 | 组件版本/维护 | Ubuntu/Node/DSH 版本、重装、卸载、备用 | `原生完整`：原生“组件维护与诊断”、版本选择、重装/卸载/备用/切换 | — · `TanukiUi.kt`/`DshVersionManagerDialog.kt` · android_agent-25/PR #18、43 · A-SET-02 |
| SET-11 | 存储/备份/快照 | 官方网页无 Tanuki 容量预算/私有快照；项目导出由 Tanuki 提供 | `原生完整`：原生“存储与空间”“项目与备份” | — · `TanukiUi.kt`/`DataSnapshots.kt` · 既有 · A-SET-08 |
| SET-12 | 远程主机 | 官方网页无 SSH 隧道设置 | `原生完整`：原生“远程主机”配置、测试连接、指纹确认、断开/清除 | — · `RemoteHostSettings.kt` · android_agent-34/PR #29 · A-REMOTE-02 |
| SET-13 | 网页/兼容性 | WebView 能力、兼容层、Blob 导出 | `原生完整`：原生“网页与兼容性”检测、更新入口、兼容开关 | — · `TanukiUi.kt`/`WebViewSupport.kt` · 既有 · A-SET-02 |
| SET-14 | 高级/关于/许可 | 关于、许可、缓存清理 | `原生完整`：原生“高级与关于”、许可、清除下载缓存 | — · `TanukiUi.kt` · 既有 · A-SET-02 |
| SET-15 | 遥测/分析开关 | 产品分析策略与开关 | `仅官方` | P2 · `productAnalytics/*` · 未排期 · A-SET-09 |

### 3.11 通知（NOTIFY）

| ID | 功能 | 官方网页版 | 原生现状 | 缺口 / 归属 / 验收 |
|---|---|---|---|---|
| NOTIFY-01 | 回合完成通知 | 页面内完成提醒/未读状态 | `原生替代`：Android `dsh-chat` 通知，回合 true→false 触发，正文最小文案 | — · `ChatNotificationPolicy.kt` · android_agent-29/PR #22 · A-NOTIFY-01 |
| NOTIFY-02 | 错误通知 | 页面错误行/提示 | `原生替代`：`api-session/error` 通知，只取会话 id | — · `ChatNotificationPolicy.kt` · android_agent-29/PR #22 · A-NOTIFY-01 |
| NOTIFY-03 | 审批/提问通知 | 页面 warning 点/等待状态 | `原生替代`：`approval/request`、`user-questions/request` 通知 | — · `ChatNotificationPolicy.kt` · android_agent-29/PR #22 · A-NOTIFY-01 |
| NOTIFY-04 | 通知点击定位会话 | 页面内跳转 | `原生完整`：`openSession` 展开侧栏、清搜索、展开分组、确认选中 | — · `DshNativeController.kt` · android_agent-29/33 · A-NOTIFY-02 |
| NOTIFY-05 | 通知权限/渠道 | 浏览器权限/系统通知 | `原生完整`：Android 13 `POST_NOTIFICATIONS`、`dsh-chat` 渠道、总开关、系统入口 | — · `ChatNotificationCenter.kt` · android_agent-29/PR #22 · A-NOTIFY-02 |
| NOTIFY-06 | 应用内 Toast/待处理徽标 | 全局 Toast、行内 warning 点、未读 | `仅官方`：原生无会话内 toast/徽标；有少量系统 Toast | P2 · 原生 UI · 未排期 · A-NOTIFY-04 |
| NOTIFY-07 | 后台/息屏/ROM 限制 | 浏览器后台节流/桌面通知 | `原生部分`：前台服务/唤醒锁/WorkManager 已有；真实 ROM/息屏送达未验收 | P1 · `RuntimeService`/`ChatNotifier` · android_agent-29 · A-NOTIFY-03 |

### 3.12 技能/插件（SKILL）

| ID | 功能 | 官方网页版 | 原生现状 | 缺口 / 归属 / 验收 |
|---|---|---|---|---|
| SKILL-01 | 技能列表 | `skills/list`，技能卡片/说明/文件预览 | `仅官方` | P2 · `skills/list` · 未排期 · A-SKILL-01 |
| SKILL-02 | 技能调用/斜杠命令 | 命令目录、执行、参数/结果 | `仅官方` | P2 · `commands/*` · 未排期 · A-SKILL-02 |
| SKILL-03 | 插件管理器 | 安装/启用/禁用/卸载/检查 | `仅官方` | P2 · `pluginManager/*` · 未排期 · A-SKILL-02 |
| SKILL-04 | 插件市场 | 索引、搜索、安装源比较 | `仅官方` | P2 · `pluginMarket/*` · 未排期 · A-SKILL-02 |
| SKILL-05 | 版本兼容/豁免 | 插件版本检查、精确版本豁免 | `仅官方` | P2 · `pluginManager/*` · 未排期 · A-SKILL-03 |
| SKILL-06 | 动态 Cordis 插件 | 动态插件 inventory/invoke/UI | `仅官方` | P2 · `dynamicCordisRunner/*` · 未排期 · A-SKILL-03 |
| SKILL-07 | Agent preset | preset 列表/读取/选择 | `仅官方` | P2 · `agentPresets/*` · 未排期 · A-SKILL-02 |
| SKILL-08 | 受管技能（browser/phone） | 官方无 Tanuki 受管技能 | `原生完整`：运行时写入受管 `SKILL.md`，不覆盖用户同名技能 | — · `app/src/main/assets/browser/SKILL.md`、`app/src/main/assets/phone/SKILL.md` · 既有 · A-SKILL-01 |
| SKILL-09 | 插件清单/诊断 | 只读 Host 插件清单、安装源响应比较 | `仅官方` | P2 · `pluginInventory/list` · 未排期 · A-SKILL-03 |

### 3.13 远程（REMOTE）

| ID | 功能 | 官方网页版 | 原生现状 | 缺口 / 归属 / 验收 |
|---|---|---|---|---|
| REMOTE-01 | 远程主机配置 | 官方网页无 SSH 隧道设置 | `原生完整`：host/IPv6/域名、端口、用户、密码/私钥、远程 DSH 端口、可选 token | — · `RemoteHost.kt`/`RemoteHostSettings.kt` · android_agent-34/PR #29 · A-REMOTE-01 |
| REMOTE-02 | SSH 隧道/主机指纹 | 官方网页无 | `原生完整`：JSch `direct-tcpip`，首次展示 SHA256 指纹，确认后才认证；变更拒绝 | — · `SshTransport.kt`/`RemoteHostController.kt` · android_agent-34/PR #29 · A-REMOTE-01 |
| REMOTE-03 | 远程端点优先/回退 | 官方网页无 | `原生完整`：远程优先，断开/失败回退本地；本地停止不清远程端点 | — · `RemoteHostMachine` · android_agent-34/PR #29 · A-REMOTE-01、A-REMOTE-02 |
| REMOTE-04 | Cookie 隔离 | 官方网页按 authority 隔离 | `原生完整`：本地/各远程逻辑主机独立 Cookie，切换前清空并恢复 | — · `DshCookieIsolation.kt` · android_agent-34/PR #29 · A-REMOTE-01 |
| REMOTE-05 | 远程 token/凭据存储 | 官方网页 token 由服务端管理 | `原生完整`：AndroidKeyStore AES-256-GCM，Keystore 失败不降级明文 | — · `RemoteHostStorage.kt` · android_agent-34/PR #29 · A-REMOTE-01、A-REMOTE-03 |
| REMOTE-06 | 中继/配对/二维码 | 官方网页无 Tanuki SSH 隧道；官方支持可信主机/远程访问配置 | `仅官方/进行中`：原生只有直连 SSH，无中继/WebRTC/配对/二维码；android_agent-48 已建分支未提交 | P2 · `RemoteHost*` · android_agent-48 · A-REMOTE-03 |
| REMOTE-07 | 多远程主机 | 官方支持多客户端/可信主机；无 Tanuki 多远程主机管理 | `仅官方`：原生单远程主机 | P2 · `RemoteHostMachine` · 未排期 · A-REMOTE-03 |
| REMOTE-08 | Ed25519 | 官方网页无 SSH 传输；Tanuki 原生 SSH 当前支持 RSA/ECDSA | `仅官方/未实现`：Ed25519 需 BouncyCastle | P2 · `SshTransport.kt` · 未排期 · A-REMOTE-03 |
| REMOTE-09 | 远程文件/终端/浏览器 | 官方页面在远程 DSH 中可用（文件/终端/浏览器侧栏） | `仅官方`：远程 DSH 页面走 WebView，原生页无远程文件/终端面板 | P2 · 官方 sidebar · 未排期 · A-REMOTE-03 |

### 3.14 安全模式（SAFE）

| ID | 功能 | 官方网页版 | 原生现状 | 缺口 / 归属 / 验收 |
|---|---|---|---|---|
| SAFE-01 | 失败分类 | 官方网页无 Tanuki 运行时失败分类；仅进程/页面错误提示 | `原生完整`：`RuntimeFailurePolicy` 分类 LAUNCH/STARTUP/CRASHED_EARLY/CRASHED_LATE/KILLED/NO_SPACE | — · `RuntimeFailurePolicy.kt` · android_agent-28/PR #25 · A-SAFE-01 |
| SAFE-02 | 安全模式/rescue | 官方网页无 Tanuki rescue | `原生完整`：独立 safe-home/safe-dsh，不加载用户插件/配置，不删数据 | — · `RuntimeManager.kt` · android_agent-28/PR #25 · A-SAFE-02 |
| SAFE-03 | 试用/probation | 官方网页无 Tanuki probation | `原生完整`：显式试用 normal，2 分钟窗口，失败回安全模式 | — · `RuntimeFailurePolicy.kt` · android_agent-28/PR #25 · A-SAFE-01、A-SAFE-02 |
| SAFE-04 | 自动重启/退避 | 官方网页无 Tanuki 自动重启/退避 | `原生完整`：最多 3 次，2s/4s/8s 退避；KILLED/NO_SPACE 不自动重启 | — · `RuntimeManager.kt` · android_agent-28/PR #25 · A-SAFE-01 |
| SAFE-05 | 数据/插件隔离 | 官方 profile 隔离；无 Tanuki safe-home/safe-dsh | `原生完整`：安全模式不加载用户插件/配置，项目工作区保留 | — · `RuntimeManager.kt` · android_agent-28/PR #25 · A-SAFE-02 |
| SAFE-06 | 命令沙箱/权限 | 官方 workspace-write/danger-full-access | `原生部分`：原生探测 bwrap/Landlock 并说明；策略修改仍在官方设置 | — · `RuntimeManager`/`SetupScreens.kt` · 既有 · A-SAFE-03 |
| SAFE-07 | WebView 同源/Cookie/TLS | 官方 Host/Origin/Cookie/TLS | `原生完整`：精确 origin、同源策略、第三方 Cookie 关闭、TLS 错误取消、无 JS Bridge | — · `OriginPolicy.kt`/`DshWebView.kt` · 既有 · A-SAFE-03 |
| SAFE-08 | 凭据存储 | 官方 `.credentials`/环境变量 | `原生部分`：远程凭据 Keystore；DSH 模型密钥仍由官方设置管理；API Key 保存问题用户暂停 | — · `RemoteHostStorage.kt` · android_agent-34/PR #29 · A-SAFE-03 |
| SAFE-09 | 私有页面/OAuth | 官方登录保护 | `原生完整`：原生浏览器敏感登录页 AI 不可查看/操作；官方 OAuth 临时页不给 AI 读写 | — · `AgentBrowserController.kt`/`DshOAuth.kt` · 既有 · A-SAFE-03 |
| SAFE-10 | 审批/审计 | 官方一次性审批 + 审计 | `仅官方/进行中`：原生通知有提醒，审批回答与审计未接 | P0/P1 · `DshMuxProtocol`/`approval/request` · 45 审批（42 提供事件） · A-APPROVAL-01、A-APPROVAL-02 |

## 4. 验收用例

> 约定：
> - `JVM`：`./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime`，可用本地合成 HTTP/WebSocket 服务，不需要设备或真实模型。
> - `仪器`：`./gradlew :app:connectedModernDebugAndroidTest` 或指定类；需要 ARM64 模拟器/设备、已安装 App、已准备 DSH 运行环境；使用合成会话/合成数据，不登录真实账号。
> - `手工`：需要真实设备、真实 DSH/PRoot、真实模型或真实 sshd；涉及真实模型/账号的用例仍受用户暂停项约束。
> - 所有用例都不得使用真实用户会话、真实 API Key、真实 Cookie 或未脱敏日志。
> - 每个用例标题下有 `- 覆盖：...`，列出它真正验证的矩阵行；第 3 节的行只能引用覆盖该行的用例。检查器会拒绝“编号存在但未覆盖该行”的引用。
> - 一个用例覆盖多个功能域时，必须在其后写 `- 跨域：<理由>`；检查器会把没有理由的跨域复用判为失败，避免一个共享用例被不同能力“蹭用”。

### 4.1 会话

**A-SESS-01（JVM）会话列表与历史分页协议**
- 覆盖：SESS-01、SESS-02、SESS-03
1. 启动本地合成 DSH HTTP 服务，按官方信封返回 `session/list`、`session/projections`、`session/page`。
2. 断言：列表标题取 `projections.values.title`；`asOfSeq` 作为 `throughSeq`；只提取 append-origin 文本；`maxMessages=120`；空/损坏记录跳过。
3. 增加“向前分页”用例：第二页 `throughSeq` 正确、重复页去重、失败保留已加载页。
4. 现有覆盖：`DshNativeApiTest`、`DshNativeControllerTest`；新增分页用例应放在同目录。

**A-SESS-02（JVM）会话生命周期方法**
- 覆盖：SESS-04、SESS-05、SESS-06、SESS-07、SESS-08、SESS-09
1. 合成服务实现 `session/create`、`session/rename`、`session/fork`、`workspace/archiveSession`、`workspace/unarchiveSession`、`workspace/delete`、`workspace/pinSession`、`workspace/insertSessionBefore`。
2. 断言请求信封 `type=client-request`、`rpcId` 关联、参数与官方方法一致；业务错误只显示短码。
3. 断言失败不改变本地选择；归档/删除确认流程不被静默跳过。
4. 现有覆盖：`DshNativeApiTest`；新增 `DshSessionLifecycleTest`。

**A-SESS-03（仪器）会话列表/状态/通知定位**
- 覆盖：SESS-01、SESS-17
1. 前置：`Pixel_7_API_34_arm64`，WebView ≥123，安装当前 debug APK；DSH 运行环境就绪；预置 2 个合成会话（一个 running、一个 idle）。
2. 打开“设置 → 网页与兼容性 → 界面：原生界面”，确认列表显示标题/时间/运行状态。
3. 点击第二个会话，确认消息区切换且不残留上一个会话内容。
4. 发送一条合成“回合完成”通知并点击，确认展开会话、清除搜索、选中目标；目标不在列表时保留 pending 并回退官方。
5. 现有覆盖：`DshUiSwitchTest`、`DshNativeControllerTest`；仪器新增 `DshNativePresentationTest`。

**A-SESS-04（手工）真实会话生命周期**
- 覆盖：SESS-04、SESS-05、SESS-06、SESS-07、SESS-08、SESS-09
1. 前置：真实/模拟设备 + 真实 DSH + 可用模型账号（用户暂停项恢复后）。
2. 新建、重命名、分叉、归档、取消归档各一次；确认官方页面与原生页看到同一会话。
3. 在运行中会话执行“停止并归档”，确认工作列表、停止、归档、撤销。
4. 记录设备、DSH 版本、模型、时间；不得使用真实用户数据。

**A-SESS-05（JVM/仪器）会话目标（goal）**
- 覆盖：SESS-14
1. JVM：合成 `goals/get`、`goals/create`、`goals/edit`、`goals/pause`、`goals/resume`、`goals/clear`，断言目标文本、状态、错误码与 `/goal` 气泡事件。
2. 仪器：目标条显示、编辑、暂停、恢复、清除；`/goal` 命令气泡；被拒绝的变更内联提示。
3. 预期：状态与官方投影一致；清除后条带消失；不伪造目标，不把目标写进会话正文。

**A-SESS-06（JVM/仪器）定时任务（schedule）**
- 覆盖：SESS-15
1. JVM：合成 `schedule/list`、`schedule/history`、`schedule/update`、`schedule/delete`、`schedule/catalog`，断言任务字段、历史、更新/删除错误码。
2. 仪器：任务列表/历史/编辑/删除；会话行活动时钟标记；收到 `schedule/changed` 后刷新。
3. 预期：变更持久化；失败可读；无任务时空态；活动标记只在 idle 行显示。

### 4.2 对话

**A-CHAT-01（JVM）follow/历史解析**
- 覆盖：CHAT-01、CHAT-11、CHAT-15、CHAT-17
1. 用 `DshFollowParser` 喂入：snapshot（含 assistantStream baseline）、event（user/assistant）、assistant-stream start/chunk/end（committed/aborted）、重复 event。
2. 断言：只显示 text-delta；committed 后临时回复被持久消息替换且不重复；aborted 清空临时回复；超长文本截断；未知帧丢弃。
3. 断言 `surfaceOp=replace` 的上下文副本、注入上下文、工具事件不进入文本消息。
4. 现有覆盖：`DshChatProtocolTest`、`DshNativeApiTest`；新增 `DshMuxProtocolTest`（android_agent-42 已带实现）。

**A-CHAT-02（JVM）消息内容模型**
- 覆盖：CHAT-02、CHAT-08、CHAT-09、INPUT-14、MODEL-07
- 跨域：消息内容模型同时被对话渲染、输入统计与模型 token 展示复用。
1. 为 Markdown、代码块、图片、文件引用、工具卡片、推理、token 统计各构造一条合成事件。
2. 断言解析后的消息模型字段完整、未知字段不崩、超长/非法内容降级为纯文本。
3. 断言不把工具参数、Cookie、token 写入展示模型或日志。
4. 新增 `DshMessageModelTest`。

**A-CHAT-03（仪器）对话渲染**
- 覆盖：CHAT-02、CHAT-03、CHAT-04、CHAT-05、CHAT-10、CHAT-11、CHAT-15
1. 前置：同 A-SESS-03；预置包含文本、Markdown、代码块、图片、工具卡片、推理、失败行、token 统计的合成会话。
2. 逐项检查：Markdown 结构、代码复制、图片预览/失败态、工具展开/折叠、推理显隐、失败重试、token 行。
3. 检查深色主题、长文本、横竖屏、字体放大；截图留存但不得含真实数据。
4. 新增 `DshNativeChatPresentationTest`。

**A-CHAT-04（手工）真实长对话/流式/重连**
- 覆盖：—
1. 真实 DSH + 模型，发送长回复，观察流式增量与最终落盘不重复。
2. 中途断网/杀进程/重启，确认重连后历史不丢、不重复、失败行正确。
3. 记录 DSH 版本、模型、网络条件；用户暂停项恢复后执行。

**A-CHAT-05（仪器）消息操作/链接/查找/滚动/中断**
- 覆盖：CHAT-12、CHAT-13、CHAT-16、CHAT-20、SEARCH-04
- 跨域：消息操作同时覆盖对话内查找与消息链接/滚动/中断。
1. 前置：合成含 Markdown 文件链接、长消息、失败/中断轮次的会话。
2. 操作：复制消息、点赞/点踩、点击文件链接打开右侧预览、页内查找上下匹配、切换会话后返回、查看中断轮次。
3. 预期：复制文本正确；反馈提交；文件链接按当前工作区解析并支持 `#L24`；滚动位置恢复；中断行本地化且无代码标签。

**A-CHAT-06（仪器）Goal/Todo/Queue 上下文卡片**
- 覆盖：CHAT-19
1. 前置：合成同时有 goal、todo、queue 的会话。
2. 操作：查看输入框上方三张卡片，编辑/暂停 goal，展开/收起 todo，编辑/移除 queue。
3. 预期：卡片顺序 Todo→Goal→Queue；状态与官方投影一致；空态不占位。

**A-SESS-07（JVM/仪器）复制会话 ID/日志导出**
- 覆盖：SESS-16
1. JVM：合成 `session/list` 与日志下载路由，断言复制的 ID 为完整 ID（不截断）、日志导出只含当前会话事件且不含 token/Cookie、失败返回短码。
2. 仪器：行菜单“复制 Session ID”写入剪贴板并与官方 ID 一致；日志导出走系统文件选择器，取消不产生文件，失败有提示。
3. 预期：复制不加载历史、不 retain 会话；导出只含当前会话；不泄露凭据。

### 4.3 输入

**A-INPUT-01（JVM）提示词/队列/取消/文件引用协议**
- 覆盖：INPUT-02、INPUT-05、INPUT-12、INPUT-13、SESS-19、ATT-06
- 跨域：提示词协议同时覆盖输入、附件引用与会话队列。
1. 合成服务实现 `session/prompt`、`session/cancel`、`session/updateQueue`、`fileReferences/list`。
2. 断言：空文本且无附件被拒绝；`requestId` 幂等；附件顺序保持；queue edit 只接受非空文本；取消只作用于当前回合。
3. 断言 `fileReferences/list` 的查询/路径解析、候选去重、只返回可读工作区文件；`@` 提及解析为引用而非复制；引用随 prompt 提交且顺序稳定。
4. 断言业务错误映射为短码，不泄露请求体。
5. 新增 `DshPromptProtocolTest`、`DshFileReferenceTest`。

**A-INPUT-02（仪器）原生 composer**
- 覆盖：INPUT-01、INPUT-02、INPUT-03、SESS-04、SESS-20
- 跨域：composer 同时覆盖输入、发送、停止与会话草稿。
1. 前置：同 A-SESS-03；原生页可输入。
2. 输入多行文本、IME 组合、Shift+Enter；发送后出现本地回显；停止按钮可取消；草稿切换会话后保留。
3. 检查发送中禁用/排队提示、空输入禁用、超长文本限制。
4. 新增 `DshNativeComposerTest`。

**A-INPUT-03（手工）真实发送/停止/steering**
- 覆盖：INPUT-02、INPUT-03、SESS-19
- 跨域：手工发送/停止/steering 同时验证输入与会话回合状态。
1. 真实 DSH + 模型，发送、停止、排队、steering 各一次。
2. 确认停止后不回放任务；steering 在下一步边界生效；失败可重试。
3. 用户暂停项恢复后执行。

**A-INPUT-04（仪器）语音输入**
- 覆盖：INPUT-11
1. 前置：设备有麦克风权限；官方语音输入插件可用。
2. 操作：点击语音输入，分别拒绝/允许权限，录制短句，转写后发送或取消。
3. 预期：拒绝时有明确提示；转写文本进入草稿；取消不发送；不把录音写入应用私有目录。

**A-INPUT-05（仪器）计划模式（plan mode）**
- 覆盖：INPUT-10
1. 前置：合成 plan-mode preset 与 plan-review 事件。
2. 操作：进入计划模式，提交计划请求，查看计划审阅卡片，选择同意执行/要求修改。
3. 预期：计划模式状态可见；审阅卡片与官方一致；要求修改回到编辑器且不提交批准。

**A-INPUT-06（仪器）@ 文件提及/引用卡片**
- 覆盖：INPUT-05、ATT-06
- 跨域：文件提及同时属于输入选择与对话附件引用展示。
1. 前置：合成工作区含多个文件；原生 composer 可用。
2. 操作：输入 `@` 触发候选，键盘/触摸选择文件，检查引用卡片；删除引用；发送后检查对话内引用卡片。
3. 预期：候选来自 `fileReferences/list`；只引用不复制文件；引用路径按当前工作区解析；无匹配/失败有提示。

### 4.4 附件

**A-ATT-01（JVM）上传/附件协议**
- 覆盖：ATT-01、ATT-02、ATT-04、ATT-05、CHAT-04、CHAT-05、INPUT-06
- 跨域：附件协议同时验证输入选择、对话内图片/文件卡片与会话授权读取。
1. 合成服务实现 `fileUploads/upload`、`session/attachment`。
2. 断言：分块/偏移/总量、类型/大小限制、失败重试、会话授权、附件不跨会话读取。
3. 断言 token/Cookie 不进入附件 URL 日志。
4. 新增 `DshAttachmentProtocolTest`。

**A-ATT-02（仪器）选择/粘贴/预览/移除**
- 覆盖：ATT-02、ATT-03、ATT-08、INPUT-06
- 跨域：附件 UI 同时覆盖输入选择与对话内预览/移除。
1. 前置：同 A-SESS-03；准备合成图片/文本/PDF。
2. 从系统选择器、粘贴、拖放各添加一次；检查缩略图、移除、失败保留、上传进度。
3. 检查超大文件/不支持类型的明确错误；不读取真实用户文件。
4. 新增 `DshNativeAttachmentTest`。

**A-ATT-03（JVM/仪器）Office/PDF 转换预览**
- 覆盖：ATT-07
1. JVM：合成 `officeToPdf/generation`、`officeToPdf/render`，断言生成状态、渲染 URL/字节、错误码。
2. 仪器：在对话附件中打开 Office/PDF 预览，检查加载、失败与重试。
3. 预期：转换失败有中文错误；不把转换结果写入用户工作区；不覆盖原文件。

### 4.5 模型/推理

**A-MODEL-01（JVM）模型目录/提供方/凭据协议**
- 覆盖：MODEL-01、MODEL-04、MODEL-06、SESS-18、SET-02
- 跨域：模型目录/凭据同时服务设置、会话模型选择与账户信息。
1. 合成服务实现 `session/modelCatalog`、`llm/listProviders`、`llm/listConfigurableProviders`、`llm/discoverModels`、`credentials/describe`。
2. 断言模型列表、默认值、错误码、密钥不回显。
3. 新增 `DshModelProtocolTest`。

**A-MODEL-02（仪器）模型/推理选择 UI**
- 覆盖：MODEL-02、MODEL-03、MODEL-05、INPUT-07、INPUT-08
- 跨域：模型/推理选择同时出现在输入框与会话设置。
1. 前置：同 A-SESS-03；合成模型目录含 2 个模型、2 档 reasoning。
2. 切换模型/推理强度，确认新回合请求使用所选值；Host 拒绝时回退并提示。
3. 检查默认模型设置持久化；重启后仍生效。
4. 新增 `DshNativeModelTest`。

**A-MODEL-03（手工）真实提供方/密钥**
- 覆盖：MODEL-04
1. 真实提供方 + 真实密钥（用户暂停项恢复后），完成一次模型调用。
2. 确认密钥不出现在日志/状态/通知；失败时错误可读。
3. 不在本审计中执行。

**A-MODEL-04（JVM/仪器）自动审查/CoT 翻译**
- 覆盖：MODEL-08、MODEL-09
1. JVM：合成自动审查与 CoT 翻译的配置/事件，断言开关、权限、错误码。
2. 仪器：开启自动审查/翻译，触发一次回合，检查审查结果与翻译显示。
3. 预期：关闭后不显示；失败不阻塞主回复；不把翻译文本写回会话。

**A-MODEL-05（JVM/仪器）账户余额/配额**
- 覆盖：MODEL-10
1. JVM：合成 `account/getState`、`getBalance`、`getProfile`，断言字段与错误码。
2. 仪器：欠费/配额失败时显示中立文案，不显示提供方原文；账户页可打开。
3. 预期：余额不泄露 token；配额失败可重试；账户页不缓存敏感值。

### 4.6 审批/权限

**A-APPROVAL-01（JVM）审批/提问 waterfall**
- 覆盖：APPROVAL-01、APPROVAL-02、APPROVAL-05、APPROVAL-08、SESS-12、SAFE-10
- 跨域：审批 waterfall 同时驱动会话待处理状态、安全审计与通知，跨域是同一事件的消费方。
1. 用 `DshMuxProtocol.parseEventFrame` 喂入 `approval/request`、`user-questions/request`、`cancel`、`ready`、非法帧。
2. 断言：只保留 eventId/agentId/工具名/原因/问题/选项；不保留工具参数；超长截断；非法帧丢弃。
3. 构造 `$events/result` 回答帧，断言 eventId 关联、批准/拒绝/回答语义、一次性请求不可重复回答。
4. 现有/新增：`DshMuxProtocolTest`（android_agent-42）。

**A-APPROVAL-02（仪器）审批/提问/计划审阅 UI**
- 覆盖：APPROVAL-01、APPROVAL-02、APPROVAL-05、APPROVAL-06、APPROVAL-07、CHAT-14、SAFE-10
- 跨域：审批/提问 UI 同时覆盖对话内卡片与安全边界。
1. 前置：同 A-SESS-03；合成 `approval/request`、`user-questions/request`、`plan-review`。
2. 检查卡片显示工具/原因/问题/选项；Enter 批准、Esc 拒绝、按钮拒绝；问题选择/自定义/跳过；计划“同意执行/要求修改”。
3. 检查回答后卡片消失、失败提示、重复点击不重复提交。
4. 新增 `DshNativeApprovalTest`。

**A-APPROVAL-03（手工）真实工具审批/沙箱**
- 覆盖：APPROVAL-10
1. 真实 DSH 触发 shell/文件写入审批；分别以 read-only/workspace-write/danger-full-access 运行。
2. 确认无沙箱后端时 bash 被拒绝；批准只对当前请求生效；审计日志有记录。
3. 用户暂停项恢复后执行。

**A-APPROVAL-04（JVM/仪器）权限预设/沙箱设置**
- 覆盖：APPROVAL-03、INPUT-09、SET-03
- 跨域：权限预设同时影响输入框、设置与审批策略。
1. JVM：合成 `permissionPresets/catalog`，断言预设 id/名称/描述/当前值；切换请求只提交允许字段。
2. 仪器：在设置/输入框切换 read-only、workspace-write、danger-full-access；检查沙箱能力提示与二次确认。
3. 预期：切换后新回合使用新预设；无沙箱后端时 workspace-write 的 bash 被拒绝；不自动切 danger-full-access。

### 4.7 工具调用

**A-TOOL-01（JVM）工具事件/卡片模型**
- 覆盖：TOOL-01、TOOL-02、CHAT-06、CHAT-07
- 跨域：工具事件同时服务对话卡片与工具详情。
1. 合成 `tool/result`、`tool/start`、错误/取消事件，断言卡片状态、参数摘要、输出/错误、折叠边界。
2. 断言敏感参数不进入展示模型；未知工具降级为通用卡片。
3. 新增 `DshToolCardTest`。

**A-TOOL-02（仪器）工具/终端/浏览器/任务/子代理**
- 覆盖：TOOL-01、TOOL-02、TOOL-03、TOOL-04、TOOL-06、TOOL-07、SESS-13、APPROVAL-07
- 跨域：工具 UI 同时覆盖对话、会话子代理与审批详情。
1. 前置：同 A-SESS-03；合成工具调用、终端输出、任务列表、子代理会话。
2. 检查卡片展开/折叠、长输出、错误、取消；终端 tab 创建/关闭/重连；任务等待/取消；子代理跳转。
3. 新增 `DshNativeToolPresentationTest`。

**A-TOOL-03（手工）真实终端/浏览器/任务/子代理**
- 覆盖：TOOL-03、TOOL-04、TOOL-06、TOOL-07
1. 真实 DSH 中运行 shell、打开浏览器、启动后台任务、创建子代理。
2. 确认原生 UI 与官方 UI 状态一致；停止/取消不误杀其他任务。
3. 用户暂停项恢复后执行。

**A-TOOL-04（仪器）Office/PDF 工具**
- 覆盖：TOOL-09
1. 前置：合成 Office/PDF 工具调用。
2. 操作：展开工具卡片，查看转换/渲染结果，失败后重试。
3. 预期：结果可预览；失败有错误；不覆盖原文件。

### 4.8 工作区/文件

**A-FILE-01（JVM）文件类型/预览策略**
- 覆盖：FILE-05
1. 现有 `WorkspaceFileKindTest`、`TextFileReaderTest`、`Pdf*Test`、`Docx*Test`、`Xlsx*Test` 覆盖分类、上限、损坏包、缩放/分页策略。
2. 新增用例：Markdown/HTML 作为文本、`.doc` 明确不支持、超大文件错误、路径穿越拒绝。
3. 命令：`./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime --tests 'com.tanuki.*File*' --tests 'com.tanuki.*Pdf*' --tests 'com.tanuki.*Docx*' --tests 'com.tanuki.*Xlsx*'`。

**A-FILE-02（仪器）原生文件页**
- 覆盖：FILE-04、FILE-05、FILE-06、FILE-07、FILE-08、FILE-09、FILE-10、FILE-15、TOOL-05
- 跨域：原生文件页同时承载工作区文件与会话内文件工具预览。
1. 前置：同 A-SESS-03；预置合成工作区文件（文本/图片/PDF/DOCX/XLSX/视频）与手机卷。
2. 浏览、搜索、排序、网格/列表、隐藏文件、面包屑；预览各格式；编辑 ≤256 KiB 文本并检查 revision 冲突；新建/重命名/复制/移动/删除；导入/导出。
3. 检查 DSH 运行时写操作被阻止；手机卷只读/授权写入/停止后恢复只读；容量统计。
4. 现有覆盖：`WorkspaceFileManagerTest`、`FilesBrowserPresentationTest`、`WorkspaceVideoPresentationTest`、`PhoneFile*Test`；新增 `WorkspaceFileParityTest`。

**A-FILE-03（JVM/仪器）工作区变更/交付物/worktree**
- 覆盖：FILE-11、FILE-12、FILE-13、CHAT-18
- 跨域：工作区变更/交付物同时服务文件页与对话页脚。
1. JVM：合成 `workspaceFiles/changes`、`workspaceFiles/read`，断言 diff 模型、文件路径、截断。
2. 仪器：变更列表、diff 预览、交付物卡片、worktree 入口；无数据时空态。
3. 新增 `WorkspaceChangesTest`。

**A-FILE-04（手工）真实大文件/Office/手机存储**
- 覆盖：FILE-05、FILE-15
1. 真实设备 + 真实 DSH，打开大 PDF/DOCX/XLSX、复杂 Word、加密 PDF、损坏包。
2. 检查滚动/缩放/内存/深色主题；手机卷授权/撤销/停止后只读。
3. 用户暂停项恢复后执行。

**A-FILE-05（JVM/仪器）工作区管理**
- 覆盖：FILE-01、FILE-02、FILE-03、SESS-11
- 跨域：工作区管理同时决定会话归属与文件页根目录。
1. JVM：合成 `workspace/create`、`rename`、`delete`、`insertBefore`、`follow`，断言参数、错误码与顺序。
2. 仪器：添加工作区、重命名、删除确认、拖拽排序、树形层级；删除父工作区后子工作区归入最近祖先。
3. 预期：会话归属不变；删除确认保留边界；层级与官方一致；失败保留当前选择。

**A-FILE-06（仪器）在文件管理器中显示/系统关联打开**
- 覆盖：FILE-14
1. 前置：设备有文件管理器；合成工作区文件。
2. “在文件管理器中显示”：若原生实现，断言只传当前工作区路径/URI，不复制文件；当前原生未实现时记录缺口，不用系统打开冒充。
3. “系统关联打开”：选择单个文件，断言 `prepareOpen` 只把该文件复制到 `cacheDir/workspace-open/<uuid>/`，URI 只读、只授予该文件、原文件未被修改；工作区其他文件/根目录不可访问；取消或失败清理临时目录；超过 24h 的缓存可清理。
4. 预期：两条路径预期不同；系统打开允许受控只读缓存副本，禁止写模式或共享整个工作区。

### 4.9 搜索

**A-SEARCH-01（JVM）会话搜索**
- 覆盖：SESS-10、SEARCH-01、SEARCH-02
- 跨域：会话搜索同时服务会话列表与全局搜索。
1. 合成 `session/search` 返回标题匹配与内容匹配（含摘要片段）。
2. 断言 250ms 防抖、查询中止、最多 20 条、归档结果标记、内容搜索失败仍显示标题匹配。
3. 新增 `DshSessionSearchTest`。

**A-SEARCH-02（仪器）搜索 UI**
- 覆盖：SESS-10、SEARCH-01、SEARCH-02、SEARCH-03、SEARCH-07、FILE-09
- 跨域：搜索 UI 同时覆盖会话、文件与浏览器查找。
1. 前置：同 A-SESS-03；预置多会话/多文件。
2. 会话标题/内容搜索、文件页当前文件夹搜索、浏览器页内查找各一次；检查清空、收起、选中、滚动到结果。
3. 新增 `DshNativeSearchTest`。

**A-SEARCH-03（JVM/仪器）全局搜索**
- 覆盖：SEARCH-06
1. JVM：合成跨会话/文件/命令搜索响应，断言合并、排序、上限与错误降级。
2. 仪器：输入查询，切换结果类型，打开结果；无结果空态。
3. 预期：不跨权限读取；归档结果标记；失败保留可用结果。

### 4.10 设置

**A-SET-01（JVM）设置/凭据/权限预设协议**
- 覆盖：SET-02、SET-03、SET-04
1. 合成 `settings/describe`、`settings/mutate`、`settings/update`、`credentials/describe`、`permissionPresets/catalog`。
2. 断言 schema 校验、变更只提交允许字段、密钥不回显、未知字段拒绝。
3. 新增 `DshSettingsProtocolTest`。

**A-SET-02（仪器）设置 UI**
- 覆盖：SET-01、SET-06、SET-10、SET-13、SET-14
1. 前置：同 A-SESS-03。
2. 原生设置 8 个分类逐项打开；检查官方/原生界面开关、WebView 检测、远程主机、组件维护、应用更新、存储/备份、高级/关于。
3. 检查深浅色、字体放大、横竖屏；官方设置入口可打开官方页面。
4. 现有覆盖：`SetupPresentationTest`、`WorkspaceStorageUiTest`；新增 `SettingsParityTest`。

**A-SET-03（手工）设置持久化/重启**
- 覆盖：SET-01、SET-06、SET-08、SET-12
1. 修改界面偏好、通知开关、早启动、低电阈值、远程主机，重启 App/进程后确认保留。
2. 确认失败写入不覆盖旧值；恢复默认只影响当前项。
3. 用户暂停项恢复后执行。

**A-SET-05（JVM/仪器）快捷键设置**
- 覆盖：SET-05、INPUT-15、SEARCH-05
- 跨域：快捷键同时服务设置、输入与搜索命令。
1. JVM：合成快捷键目录/冲突检测/保存/恢复默认，断言冲突拒绝、覆盖持久化、未知命令拒绝。
2. 仪器：修改发送、新建、搜索快捷键，触发冲突提示，恢复默认；检查快捷键速查。
3. 预期：冲突不保存；重启后保留；恢复默认只影响当前项；不覆盖浏览器/系统保留键。

**A-SET-06（仪器）语言/本地化**
- 覆盖：SET-07
1. 前置：官方支持 zh/en；原生界面当前中文。
2. 操作：切换官方语言，检查会话、设置、审批文案；重启后保留。
3. 预期：官方页面即时切换；原生硬编码文案列出待本地化清单；不把中文写死成唯一语言。

**A-SET-07（仪器）应用更新**
- 覆盖：SET-09
1. 前置：合成 `app-update.json` 与更高 `versionCode`。
2. 操作：手动检查、后台检查、下载、取消、安装权限；低版本/同版本/坏 JSON。
3. 预期：只提示不自动安装；版本比较按 `versionCode`；失败可重试；不覆盖用户数据。

**A-SET-08（仪器）存储/备份/快照**
- 覆盖：SET-11、FILE-16
- 跨域：存储/备份同时服务设置页与文件页容量。
1. 前置：合成工作区容量与目录占用；停止 DSH。
2. 操作：调整容量预算、统计占用、创建/恢复私有快照、导出项目 ZIP。
3. 预期：容量预算生效；快照含配置/凭据/会话但不回滚项目；导出排除常见凭据；失败保留旧数据。

**A-SET-09（JVM/仪器）遥测/分析开关**
- 覆盖：SET-15
1. JVM：合成 `productAnalytics/enabled`、`watchPolicy`，断言开关、策略变更、错误码。
2. 仪器：关闭/开启遥测，检查官方页面不发送分析事件；重启后保留。
3. 预期：默认遵循官方策略；关闭后不采集；不把开关状态写入会话。

### 4.11 通知

**A-NOTIFY-01（JVM）通知策略/路由/文案**
- 覆盖：NOTIFY-01、NOTIFY-02、NOTIFY-03、SESS-12、APPROVAL-09
- 跨域：通知策略消费审批/提问与会话状态事件。
1. 现有 `ChatNotificationPolicyTest`、`ChatNotificationRoutingTest`、`DshChatProtocolTest` 覆盖：true→false 回合完成、error、approval/question、去重、前台/可见会话抑制、正文最小文案。
2. 新增：非法 id/超长/非 ASCII 丢弃；通知点击目标不在列表时保留 pending。
3. 命令：`./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime --tests 'com.tanuki.ChatNotification*' --tests 'com.tanuki.DshChatProtocolTest'`。

**A-NOTIFY-02（仪器）通知权限/渠道/点击**
- 覆盖：NOTIFY-04、NOTIFY-05、NOTIFY-06、SET-08、APPROVAL-09、SESS-17
- 跨域：通知点击同时验证会话定位与通知设置。
1. 前置：Android 13+ 模拟器；安装 debug APK；授予/拒绝 `POST_NOTIFICATIONS` 各一次。
2. 触发回合完成/错误/审批/提问合成事件；检查渠道、group/summary、正文、点击定位会话、总开关关闭后不提醒。
3. 现有覆盖：`ChatNotification*`；新增 `ChatNotificationPresentationTest`。

**A-NOTIFY-03（手工）后台/息屏/ROM**
- 覆盖：NOTIFY-07
1. 真实设备，App 退后台/息屏/省电模式/自启动限制下触发通知。
2. 记录送达延迟、是否被拦截、点击是否定位；覆盖至少一个厂商 ROM。
3. 用户暂停项恢复后执行。

**A-NOTIFY-04（仪器）应用内 Toast/待处理徽标**
- 覆盖：NOTIFY-06
1. 前置：合成待审批/待回答/未读完成会话。
2. 操作：观察侧栏/会话行 warning 点、未读完成标记、全局 Toast；处理后消失。
3. 预期：徽标与官方投影一致；Toast 不遮挡输入；成功/失败提示可关闭。

### 4.12 技能/插件

**A-SKILL-01（JVM）技能/命令协议**
- 覆盖：SKILL-01、SKILL-08、INPUT-04
- 跨域：技能/命令协议同时服务技能列表与输入斜杠命令。
1. 合成 `skills/list`、`commands/list`、`commands/execute`、`commands/change`。
2. 断言技能元数据、路径预览、命令参数、错误码；未知技能/命令降级。
3. 新增 `DshSkillProtocolTest`。

**A-SKILL-02（仪器）技能/命令/插件管理 UI**
- 覆盖：SKILL-02、SKILL-03、SKILL-04、SKILL-07、TOOL-08、SET-04
- 跨域：技能/插件 UI 同时覆盖设置、技能与工具调用。
1. 前置：同 A-SESS-03；合成技能与插件清单。
2. 技能列表/详情/调用；斜杠命令；插件安装/启用/禁用/卸载/市场搜索；Agent preset 选择。
3. 新增 `DshNativeSkillPresentationTest`。

**A-SKILL-03（手工）真实插件安装/版本豁免/动态插件**
- 覆盖：SKILL-05、SKILL-06、SKILL-09、TOOL-10
- 跨域：插件安装同时覆盖技能/插件与工具扩展。
1. 真实 DSH 安装一个测试插件，检查版本兼容、豁免、启用/禁用、卸载。
2. 动态 Cordis 插件 inventory/invoke；失败时错误可读、可回滚。
3. 用户暂停项恢复后执行。

### 4.13 远程

**A-REMOTE-01（JVM）远程配置/隧道/隔离**
- 覆盖：REMOTE-01、REMOTE-02、REMOTE-03、REMOTE-04、REMOTE-05
1. 现有 `RemoteHostTest`、`DshRemoteIsolationTest` 覆盖：host/IPv6/域名、端口、密码/私钥、指纹确认/变更、远程优先/回退、Cookie 隔离、token 不日志。
2. 新增：Keystore 失败不降级明文、远程端点存活时本地停止不清除、弱网/超时错误码。
3. 命令：`./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime --tests 'com.tanuki.RemoteHostTest' --tests 'com.tanuki.DshRemoteIsolationTest'`。

**A-REMOTE-02（仪器）远程设置/连接/回退**
- 覆盖：REMOTE-01、REMOTE-03、SET-12
- 跨域：远程设置同时覆盖远程主机与设置页入口。
1. 前置：同 A-SESS-03；本机/局域网 sshd（合成账号）与远程 DSH。
2. 测试连接、首次指纹确认、保存并连接、断开/清除；远程失败回退本地；Cookie 两主机隔离。
3. 新增 `RemoteHostPresentationTest`。

**A-REMOTE-03（手工）真实 sshd/Keystore/token/Ed25519/中继**
- 覆盖：REMOTE-05、REMOTE-06、REMOTE-07、REMOTE-08、REMOTE-09
1. 真实 sshd（RSA/ECDSA/Ed25519）、真实 Keystore、真实远程 DSH token；弱网/断线/重连。
2. 中继/配对/二维码（若 android_agent-48 落地）单独验收；当前未实现。
3. 用户暂停项恢复后执行。

### 4.14 安全模式

**A-SAFE-01（JVM）失败分类/重启/试用**
- 覆盖：SAFE-01、SAFE-03、SAFE-04
1. 现有 `RuntimeFailurePolicyTest` 覆盖分类、连续失败、probation、退避、KILLED/NO_SPACE、持久化恢复。
2. 新增：143/137、ready 后 HTTP 超时、ENOSPC 优先级、进程重启后试用失败回安全模式。
3. 命令：`./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime --tests 'com.tanuki.RuntimeFailurePolicyTest'`。

**A-SAFE-02（仪器）安全模式/rescue/试用 UI**
- 覆盖：SAFE-02、SAFE-03、SAFE-05
1. 前置：同 A-SESS-03；预置失败状态/救援目录。
2. 检查失败类型、安全模式说明、试用入口、退出试用、恢复入口；确认不加载用户插件、不删项目。
3. 现有覆盖：`SetupPresentationTest`；新增 `RuntimeRecoveryPresentationTest`。

**A-SAFE-03（手工）真实崩溃/磁盘满/OOM/rescue**
- 覆盖：SAFE-06、SAFE-07、SAFE-08、SAFE-09、APPROVAL-04
- 跨域：安全边界同时覆盖沙箱/权限与审批策略。
1. 真实设备/PRoot，注入启动失败、崩溃、ENOSPC、OOM；观察自动重启/安全模式/rescue。
2. 检查 WebView 同源/Cookie/TLS、私有页面、凭据不出现在日志。
3. 用户暂停项恢复后执行。

### 4.15 文档门禁

**A-DOC-01（JVM/宿主）矩阵一致性**
- 覆盖：—
1. 运行 `python3 scripts/check-native-parity.py`（已接入 `.github/workflows/verify.yml` 的 Host job）。
2. 断言：14 个功能域齐全、ID 唯一、每行有状态/优先级/验收用例；每行引用的用例必须在 `- 覆盖：` 中声明覆盖该行；覆盖声明只能引用存在的矩阵行；跨功能域复用必须有 `- 跨域：` 理由；引用的仓库路径存在。
3. 运行 `python3 scripts/check-handoff.py --pr --record docs/handoffs/<本次记录>.md`；共享状态由集成维护方刷新。

## 5. 自动化与设备前置

| 验收类型 | 可自动化范围 | 不能自动化/设备前置 |
|---|---|---|
| JVM | 协议信封、解析、状态机、策略、错误码、限额、去重、回退决策；用本地合成 HTTP/WebSocket 服务 | 真实 DSH 进程、真实 Keystore、真实 sshd、真实模型 |
| 仪器 | Compose 布局、交互、权限弹窗、通知、文件选择器、WebView 同源 | 真实 PRoot/DSH 安装、真实模型账号、真实 ROM 后台限制 |
| 手工 | 真实设备/PRoot/模型/sshd/ROM 的端到端行为 | 需要用户恢复真实模型/账号授权；不得用宿主结果冒充 Android |

设备前置（仪器）：

- 优先复用既有 ARM64 AVD `Pixel_7_API_34_arm64`；记录 AVD 名与 serial。
- WebView ≥123；App 为当前 debug/workspacecheck 包；DSH 运行环境已就绪（Ubuntu/Node/DSH）。
- 使用合成会话/合成文件/合成通知事件；不登录真实账号、不使用真实用户数据。
- 涉及通知的用例需 Android 13+；涉及远程的用例需可用 sshd 与远程 DSH。
- 设备由独立 QA 独占；本审计不启动/安装/跑仪器。

## 6. 并行归属与阻塞（2026-11-10 只读核对）

| 会话/分支 | 当前状态 | 与本审计的关系 |
|---|---|---|
| android_agent-33 / `ao/ui-switch/root` / PR #26 | 已合并 `d9b4aa2` | 原生只读页、官方/原生切换、通知定位；矩阵 SESS/CHAT/SET 的主要现状来源 |
| android_agent-42 / `ao/tanuki-native-ui/root` | 未提交 `DshMuxProtocol.kt`、`DshWebSocket.kt` | session/follow、`$events`、审批/提问解析；对应 CHAT-15、SESS-12、APPROVAL-01/05 |
| android_agent-45 / `ao/tool-approval/root` | 已建分支，无提交 | 工具审批/工具卡片；对应 APPROVAL-02/07、TOOL-01/02 |
| android_agent-46 / `ao/input-attachments/root` | 已建分支，无提交 | 输入/附件；对应 INPUT-01/02/05/06、ATT-* |
| android_agent-44 / `ao/native-parity/root` | 已建分支，无提交 | 实现会话管理（DshSessionManagerActivity/Controller/Page、DshSessionAdminApi）；不写矩阵。矩阵与检查器仍由 android_agent-47 唯一维护。 |
| android_agent-39 / `ao/saf-file-open/root` | 未提交文件访问改动 | 外部文档安全打开；对应 FILE-05/14 |
| android_agent-41 / `ao/file-viewer/formats` | 未提交格式扩展 | 文件格式；对应 FILE-05 |
| android_agent-43 / `ao/version-uninstall/root` | 未提交版本/卸载改动 | 组件维护；对应 SET-10 |
| android_agent-48 / `ao/tanuki-remote/relay-pairing` | 已建分支，无提交 | 远程中继/配对；对应 REMOTE-06 |
| android_agent-29 / PR #22 | 已合并 `c2f1b44` | Android 聊天通知；对应 NOTIFY-01/02/03/05 |
| android_agent-34 / PR #29 | 已合并 `9e7edbb` | 远程 SSH；对应 REMOTE-01..05、SET-12 |
| android_agent-28 / PR #25 | 已合并 `5fd1ab4` | 安全模式；对应 SAFE-01..05 |
| android_agent-31/32/35 | 已合并 PR #28/#23/#24 | DOCX/XLSX/PDF 预览；对应 FILE-05 |
| android_agent-24 / PR #19 | 已合并 `6136451` | 后台更新检查；对应 SET-09 |
| android_agent-25 / PR #18 | 已合并 | DSH 版本管理；对应 SET-10 |
| android_agent-17 / PR #17 | 已合并 | 视频全屏；对应 FILE-05 |

阻塞与风险：

- **P0 阻塞**：原生页没有 composer/发送/停止/审批回答；42 的 mux 解析尚未接 UI，45/46 尚未提交。原生模式当前不能替代官方模式完成一次真实任务。
- **P1 阻塞**：会话生命周期、模型、附件、搜索、工作区均未排期；需要编排器确认 44 与 42/45/46 的边界。
- **用户暂停**：真实模型/账号验收、真机验收、API Key 保存；不得在本审计中恢复或绕过。
- **设备边界**：本审计未启动设备、未安装 APK、未跑仪器；仪器用例需独立 QA 会话执行。
- **协议风险**：官方 0.2.1-rc.5 的 `session/follow`、`$events`、审批 waterfall 若升级调整，必须先更新 `docs/INTERFACES.md` 与回归，再改实现。

## 7. 不做与不宣称

- 不修改产品代码、不新增原生页面、不替 42/45/46 实现功能。
- 不把官方 WebView 可用写成“原生已对齐”。
- 不把源码存在、历史测试或并行分支的未提交代码写成“已实现/已验证”。
- 不合并、不发布、不创建 tag/Release。
- 不使用真实 API Key、Cookie、用户会话或未脱敏日志。
- 不在本文件中记录官方实现名称、内部源码路径或第三方参考实现名称。

## 8. 维护规则

**所有权**：矩阵与 `scripts/check-native-parity.py` 由 android_agent-47 唯一维护。其他 worker 完成项只提交 PR/commit/测试证据，由矩阵属主核对后更新状态；不得直接改矩阵或检查器，避免双写。

1. 新增/修改官方协议方法时，先更新 `docs/INTERFACES.md`，再更新本矩阵的“官方”列与验收用例。
2. 新增矩阵行时必须同时指定至少一个覆盖该行的验收用例；新增/修改用例时必须更新 `- 覆盖：`，不能只写用例编号；跨功能域复用时必须写 `- 跨域：` 理由。
3. 任何并行会话完成一个缺口后，把对应行的状态从 `仅官方/进行中` 改为 `原生完整/原生部分`，并补上 PR/commit 与已跑测试；没有实际测试不得写“已验证”。
4. 每轮提交前运行 `python3 scripts/check-native-parity.py`、`python3 scripts/check-handoff.py --pr --record docs/handoffs/<本次记录>.md`；文档链接变化同时跑 `python3 scripts/check-publication.py --git`。
5. 若官方客户端升级导致行为变化，先记录版本与差异，再改矩阵；不要凭旧记忆更新。
