# 交接：20261110-1139-native-approval-tools

- Agent/会话标识：android_agent-45（AO worker）
- 时间：2026-11-10 11:39–16:30 CST
- 状态：5 项 Codex Review 已修复并本地验证；四文件已归还 42；PR [#41](https://github.com/leezzou/tanuki/pull/41) 待推送/复审；未合并、未发布
- 前一记录：[文件查看器格式支持扩展](20261110-1142-file-viewer-formats.md)（android_agent-41，PR #34 已合并 `main=533eff4`）

## 用户目标与范围

- 用户本次实际要求：在 Tanuki 原生 DSH 界面补齐「审批/权限/工具调用」切片：
  1. 审批/权限请求完整展示工具名、参数、风险、允许/拒绝/记住选择，并按官方协议回传；
  2. 工具调用卡片展示运行中/成功/失败、输出折叠、diff/命令/文件变更；
  3. 与安全模式、远程 origin、通知点击兼容；失败给中文错误，不静默吞；
  4. 补 JVM/协议回归；设备未验证如实标注；
  5. 中文注释、author/committer=`leezzou`、PR、不合并、不发布；文档/注释/提交不出现参考实现名称。
- 编排器最新边界：`DshNativeApi.kt`、`DshNativeController.kt`、`DshNativePage.kt`、`MainActivity.kt` 由 android_agent-42 独占；本切片只保留自己的新文件，并把 42 接线清单写清。`DshWebSocketClient.kt` 暂时保留完成功能，但标注“临时实现，待收敛到 42 transport”，不再扩展。
- 本次不包含：聊天发送/取消/附件/模型选择（42）、会话列表/设置/技能（44）、官方 npm 包修改、DSH 数据目录改写、真实设备验收、合并/发布。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收仍暂停。

## 合并说明

- 已合入 `origin/main=2a4a050`（PR #35 文件复制界面稳定；此前已合入 PR #32 parity audit、PR #34 文件查看器格式扩展）。代码无重叠；仅 `docs/AGENT-STATE.md`/`docs/handoffs/CURRENT.json` 冲突，已保留本切片为当前交接、copy-flicker 为上一记录，并刷新交接印章。

## 当前状态

- 已实现：审批协议/回答、WebSocket 审批通道、工具时间线/卡片、审批面板、控制器生命周期、5 项 Review 修复、文档与回归。
- 已验证：`compileModernDebugKotlin`、完整 `testModernDebugUnitTest` 602/0/0/0、`lintModernDebug` 0 errors / 94 warnings、`assembleModernDebug` 通过（未安装）。
- 待验证：真实设备/模拟器 UI、真实 PRoot DSH `/connect`（answer Peer）/WebSocket/审批回答、远程 SSH 隧道、安全模式 rescue、通知点击过滤、Android 仪器/真机。
- 分支 `ao/tool-approval/root`，基线 `origin/main=0555c3c`，已合入 `main=2a4a050`；无运行中安装/设备任务。
- 四文件归属：`DshNativeApi.kt`、`DshNativeController.kt`、`DshNativePage.kt`、`MainActivity.kt` 已恢复为 `origin/main` 内容，本切片不再修改；42 接线清单见 [NATIVE-UI](../NATIVE-UI.md)。

## 42 接线清单

1. `DshNativeApi.kt`
   - `DshSessionPage` 增加 `timeline: List<DshTimelineItem> = messages.map { DshTimelineItem.Text(it) }`。
   - `parsePage` 读取 `records` 后调用 `DshToolTimeline.parse(records)`，写入 `timeline`；既有 `messages` 契约不变。
2. `DshNativeController.kt`
   - `DshNativeState` 增加 `timeline: List<DshTimelineItem>`；历史成功时 `timeline = page.timeline`，选择/加载/清空时同步清空。
   - 提供按审批会话读取工具的能力，例如 `suspend fun tool(sessionId: String, callId: String): DshToolItem?`；历史后到时调用 `approvalController.updateTool(sessionId, tool)`。
3. `DshNativePage.kt`
   - 增加可选参数：
     ```kotlin
     approvalState: DshApprovalState = DshApprovalState(),
     onApprovalAnswer: (String, DshApprovalOutcome, Boolean) -> Unit = { _, _, _ -> },
     onApprovalRetry: () -> Unit = {},
     onClearRemembered: () -> Unit = {}
     ```
   - 在 `LazyColumn` 首个 `item` 挂 `DshApprovalPanel(state, selectedId, onAnswer, onRetry, onClearRemembered)`。
   - 时间线 `DshTimelineItem.Text` 渲染 `MessageCard`，`DshTimelineItem.Tool` 渲染 `DshToolCard(tool)`。
4. `MainActivity.kt`
   - 复用同一 `endpointProvider` 创建 `DshApprovalController(endpointProvider, lifecycleScope, toolLookup = { sessionId, callId -> ... })`；`toolLookup` 必须按 `sessionId+callId` 查询，不能用当前选中会话的旧快照。
   - 在原生模式 + 服务 `RUNNING` 时 `start()`；停止/切官方/页面销毁时 `close()`；origin 变化时重建。
   - 收集 `approvalController.state`，把状态与回调传给 `DshNativePage`。
5. 通知点击：42 切换 `selectedId` 后，审批面板自然按当前会话过滤；无需新增通知协议。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshApprovalProtocol.kt` | 新增：审批帧/`interactions=answer` 连接/回答信封/风险分级/本次运行记忆；未知 toolName 只允许拒绝。 |
| `app/src/main/kotlin/com/tanuki/web/DshWebSocketClient.kt` | 新增（临时）：最小 RFC6455 客户端；loopback、Cookie、Accept 校验、客户端掩码、拒绝服务端掩码、1 MiB 上限、握手有界超时、先发布可关闭 socket。待收敛到 42 transport。 |
| `app/src/main/kotlin/com/tanuki/web/DshApprovalSession.kt` | 新增：`/connect`（answer）→`$events`→`$events/result`、401 重认证一次、ready 10 秒超时、中文错误；定义 `DshApprovalTransport`。 |
| `app/src/main/kotlin/com/tanuki/web/DshToolTimeline.kt` | 新增：自包含解析 `tool/call`/`tool/result`/`meta.diffs` 与文本消息；命令/cwd/路径、输出截断、replace 去重、diff LCS。 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalController.kt` | 新增：审批状态机；按 `sessionId+callId` 异步取参数；`updateTool` 后到更新；未知类型拒绝；断线代次清理；记忆存在时面板可见。 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalPanel.kt` | 新增：审批卡片；工具名、参数（长参数可展开）、风险、允许/拒绝/记住、其他会话计数、错误重试、记忆撤回入口。 |
| `app/src/main/kotlin/com/tanuki/ui/DshToolCard.kt` | 新增：工具卡片；运行中/成功/失败、命令、文件变更、diff、参数、折叠输出、风险徽标。 |
| `app/src/test/kotlin/com/tanuki/DshApprovalProtocolTest.kt` | 11 项：帧/白名单/answer 连接/回答信封/风险/记忆/未知类型。 |
| `app/src/test/kotlin/com/tanuki/DshToolTimelineTest.kt` | 9 项：工具配对/失败/replace/孤儿/截断/diff/命令路径/文本过滤。 |
| `app/src/test/kotlin/com/tanuki/DshWebSocketTest.kt` | 7 项：RFC6455 Accept/掩码/握手/超时/close 解除阻塞。 |
| `app/src/test/kotlin/com/tanuki/DshApprovalSessionTest.kt` | 5 项：全链路/401/HTTP 500/缺端点/非 loopback；假服务器断言 `interactions=answer`。 |
| `app/src/test/kotlin/com/tanuki/DshApprovalControllerTest.kt` | 12 项：允许/拒绝/记忆/按审批会话取参数/历史后到/未知类型/失败/断线重连/origin/面板可见。 |
| `docs/INTERFACES.md` | 更新 1.7：answer Peer、`$events`/`$events/result`、参数后到、未知类型、临时 WebSocket 收敛说明。 |
| `docs/NATIVE-UI.md` | 更新归属边界、42 接线清单、文件表、安全边界与验证/未验证。 |
| `docs/TEST-REPORT.md` | 新增本切片验证与 5 项 Review 修复记录。 |
| `docs/AGENT-STATE.md` | 更新当前切片状态、602 项验证与 42 归属。 |
| `docs/handoffs/CURRENT.json` | 刷新交接印章。 |

## 5 项 Review 修复

1. **P1 answer Peer**：`DshApprovalProtocol.connectUrl()` 生成 `interactions=answer`，只读列表继续 `interactions=none`；`DshApprovalSession` 使用新函数；假服务器断言改为 `answer`。
2. **P2 按审批会话取参数**：`toolLookup` 改为 `suspend (sessionId, callId) -> DshToolItem?`；收到审批后异步读取并更新参数/风险；新增 `updateTool(sessionId, tool)` 处理历史后到；非当前会话也按 `request.sessionId` 查询；参数未到时禁止盲目允许，2 秒超时后显示“未读取到参数”。
3. **P2 记忆撤回入口**：`DshApprovalState.shouldShowPanel()` 把 `remembered` 纳入可见条件；成功回答后 pending 为空仍显示面板与“清除记住的选择”。
4. **P2 断线释放 answering**：`answering` 改为 `eventId -> 连接代次`；断线清空；旧回答退出按代次校验，不误伤重连后的卡片；新增“回答中断线后重连仍可回答”回归。
5. **P2 握手期限**：握手期 `soTimeout=handshakeTimeoutMs`（默认 10 秒），先发布 socket/output，握手完成后再设 0；`close()` 可解除握手中阻塞；新增“接受 TCP 但不返回 Upgrade 头”和“握手中 close()”回归。
6. 额外安全：未知/非法 `toolName` 显示“未知审批”，只能拒绝，不能按记忆自动允许。

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh` + `./gradlew :app:compileModernDebugKotlin -x buildNativeRuntime --console=plain` | BUILD SUCCESSFUL | JDK17 worktree 环境 |
| `./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime --console=plain` | BUILD SUCCESSFUL；72 个结果文件，602 tests / 0 failures / 0 errors / 0 skipped | 基线 535 + 本切片 44 + 合入 PR #34 的 23 |
| `./gradlew :app:lintModernDebug -x buildNativeRuntime --console=plain` | BUILD SUCCESSFUL；0 errors / 94 warnings / 1 hint | 存量弃用/可读性告警 |
| `./gradlew :app:assembleModernDebug -x buildNativeRuntime --console=plain` | BUILD SUCCESSFUL | 仅打包验证，未安装 |
| 目标回归 44 项：Protocol 11、Timeline 9、WebSocket 7、Session 5、Controller 12 | 44/44 通过 | 本地合成 HTTP+WebSocket 服务器；不代表真实 DSH |
| 既有 `DshNativeApiTest` 12、`DshNativeControllerTest` 6、`DshUiSwitchTest` 10 | 通过 | 四文件已恢复 main，旧契约不受影响 |
| `python3 scripts/check-handoff.py`、`check-publication.py --git`、`package-source.py` | 通过 | 收尾时重跑并刷新印章 |

- 哪些是前一轮结果，哪些是本次新验证：以上产品编译/JVM/lint/assemble 均为本次新跑；未复用历史数字。
- 未测试、超时、失败和暂未复现的情况：真实设备/模拟器、真实 PRoot DSH、远程 SSH 隧道、安全模式 rescue、通知点击过滤、Android 仪器、真机均未执行。

## 未完成与暂停项

- 未完成：提交/推送新 head、回复并 resolve 5 条 GitHub review thread、等待新 head CI；PR 不合并、不发布。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收。

## 下一步

1. 以 `leezzou <115924933+leezzou@users.noreply.github.com>` 提交并推送 `ao/tool-approval/root`（一次 push）。
2. 在新 head 上回复并 resolve 5 条 thread（`PRRT_kwDOVgRm4s61kLrI`、`...LrP`、`...LrT`、`...LrY`、`...Lrc`），确认 Host/Android CI。
3. 真实设备/PRoot 审批与工具卡片验收由独立 QA 另行授权；远程 SSH 隧道与安全模式 rescue 同样待验。

## 约束与风险

- 数据/设备/权限边界：不读真实 Key/Cookie/token/会话；不改 DSH 数据；设备 QA 由独立 QA 独占。
- 关键决策及理由：审批回答只走官方 `$events/result`；审批用独立 answer Peer；参数按审批会话读取，避免用当前选中会话旧快照；“记住选择”仅本次原生运行内存，不冒充官方持久授权；未知审批只能拒绝；临时 WebSocket 不再扩展，等待 42 transport。
- 失败尝试与避免重复踩坑的结论：官方 `$events`/`$events/result` 需要 `interactions=answer`；append 的 `surfaceOp` 是字符串，replace 才是对象；同一 callId 的 replace 结果必须更新已有卡片；WebSocket 服务端帧不能掩码；握手必须有界且可 close；断线必须清 answering 代次。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未生成发布 APK/源码 ZIP；未安装、未发布。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装；未发布。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：保留 PR #34 记录与本切片记录；未删除历史记录。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP。
