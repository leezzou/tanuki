# 交接：20261109-1742-dsh-chat-notifications

- Agent/会话标识：android_agent-29（DSH Worker）
- 时间：2026-11-09 17:42 CST
- 状态：实现、JVM/lint/CI 完成；4 条 Review 意见（含重复 thread）已修复并补回归，已 rebase 到 `main=5fd1ab4`（含安全模式 PR #25 与 XLSX PR #23）后复跑 283 JVM；PR [#22](https://github.com/leezzou/tanuki/pull/22) 待独立 Review 与设备/ROM 验收
- 前一记录：[DSH 安装提速与国内镜像](20261109-1539-dsh-install-resilience.md)（PR #20 已合并为 `2f3fac9`）

> 本记录只描述 DSH 聊天通知；不改 DSH 下载/安装、模型、账号、API Key 保存、浏览器 RPC、手机文件 RPC 或既有暂停项。

## 用户目标与范围

- 用户本次实际要求：为 DSH 增加聊天通知，覆盖回合完成、等待用户确认/审批、错误、权限请求；只在应用不在前台或对应会话不可见时提醒；按 session/version/event 去重；点击通知打开对应会话；Android 13 `POST_NOTIFICATIONS` 请求、通知渠道、group/summary、国产 ROM 设置入口；不读取/输出 token 或会话内容；通知正文最小化/可配置；补纯策略 JVM 测试；在 `/tmp` 独立 clone 提交并开 PR，不合并、不触发 Review；文档/PR 不提参考项目；注释中文。
- 本次包含：DSH 官方 `$events` 转发事件的只读页面旁路、事件分类与去重策略、前台/会话可见抑制、系统通知投递与分渠道/分组、设置页入口与 Android 13 权限请求、通知点击回会话、JVM 回归、接口/测试/状态/交接文档。
- 本次不包含：新建 RPC、读取 Cookie/token、复制原生 WebSocket 凭据、修改 DSH 官方包、自动应答审批、设备/真机/ROM 实机验证、独立 Review、合并或发布。

## 当前状态

- 已实现：页面脚本在 `/api/remote.mux` 的 `$events` 流中只识别 `api-session/status`、`api-session/error`、`approval/request`、`user-questions/request`；原生按 session + DSH 版本 + event 去重；前台且 `[data-conversation-session]` 与事件会话一致时抑制；通知点击带 session extra，回到 DSH 页后点击侧栏 `[data-row-key="session:<id>"]`。
- 已实现：`dsh-chat` 渠道、每个会话独立通知 id、`com.tanuki.DSH_CHAT` group + summary、Android 13 权限请求、渠道/应用通知设置回退、应用内总开关、设置页状态与“上次未提醒”原因。
- 已实现：通知正文固定为“回合完成/等待确认/出错/权限审批”四类最小文案，不使用会话标题、消息、工具名、错误原文或路径。
- 已验证：`compileModernDebugKotlin`、完整 `testModernDebugUnitTest`（223 tests / 0 failures / 0 errors / 0 skipped）、`:app:lintModernDebug` 均 BUILD SUCCESSFUL；新增策略/身份/协议 18 项 JVM 回归；`node scripts/test-chat-session-open.mjs` 通过收起侧栏→展开分组→点击→确认选中的状态机回归；注入脚本通过 `node --check` 和 5 类合成帧的本地模拟，未把 `secret` 正文带出。
- 待验证：真实设备上的 Android 13 权限弹窗、通知渠道/group/summary 展示、点击通知切会话、后台/息屏 WebSocket 事件、MIUI/其他 ROM 的通知/电池/自启动限制、真实 DSH 页面（当前无设备执行）。
- Rebase：已 rebase 到 `main=5fd1ab4bb269888289445ed42d8a98424e9fc680`（含 PR #25 安全模式）；仅 `docs/AGENT-STATE.md`/`docs/handoffs/CURRENT.json` 文档冲突，保留 main 当前交接与本 PR 并行记录，代码无冲突。
- 当前分支：`ao/dsh-chat-notify/root`（本 worktree）；提交分支 `ao/dsh-chat-notify/chat-notifications`（`/tmp/tanuki-chat-notify` 独立 clone），PR [#22](https://github.com/leezzou/tanuki/pull/22)，实现提交 `09396bc`；后续文档/CI 记录提交会推进 head，head 以 PR 页面为准；基线 `main=2f3fac9301c7c3484df85e99091e8f63dddc0a48`，已 rebase 到 `main=5fd1ab4bb269888289445ed42d8a98424e9fc680`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/notify/ChatNotificationPolicy.kt` | 纯策略：事件类型/权限状态/去重/前台抑制、`ChatTurnState`、抑制原因文案。 |
| `app/src/main/kotlin/com/tanuki/notify/ChatNotificationStatus.kt` | 设置页可读状态；不含会话内容。 |
| `app/src/main/kotlin/com/tanuki/notify/ChatNotificationCopy.kt` | 四类最小通知文案。 |
| `app/src/main/kotlin/com/tanuki/notify/ChatNotifier.kt` | 渠道、Android 13 权限、group/summary、稳定会话 tag、PendingIntent data 身份、设置 Intent、应用内总开关；新事件不再被 `setOnlyAlertOnce` 压掉。 |
| `app/src/main/kotlin/com/tanuki/notify/ChatNotificationRouting.kt` | 跨进程稳定的通知 tag/固定 id、会话级 PendingIntent action/data/requestCode；子通知可再次提醒，摘要静默。 |
| `app/src/main/kotlin/com/tanuki/notify/ChatNotificationCenter.kt` | 运行时编排：版本、回合状态、去重、权限/前台、状态流。 |
| `app/src/main/kotlin/com/tanuki/web/DshChatProtocol.kt` | 官方事件名/流路径、最小 JSON 校验、DocumentStartScript 旁路脚本；打开会话先展开收起侧栏、清除非空搜索过滤、展开工作区分组，确认实际选中后才返回成功。 |
| `app/src/main/kotlin/com/tanuki/web/DshChatBridge.kt` | WebView 精确 origin 的 DocumentStartScript + WebMessageListener 安装/替换/移除。 |
| `app/src/main/kotlin/com/tanuki/web/DshWebView.kt` | 连接时安装旁路、销毁时移除、`openSession` 回调式打开会话。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 通知点击 extra、DSH 页导航、前台/标签可见状态、权限请求与设置入口、通知状态对话框。 |
| `app/src/main/kotlin/com/tanuki/TanukiApp.kt` | 创建通知中心并启动时建立渠道。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 设置页“DSH 聊天通知”总开关/状态/入口；DSH 可见回调；通知点击导航。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` 模型/回调 | 新增 `chatNotifications`、`dshVisible`、`chatNotifications`、`toggleChatNotifications`、`dshOpenRequest`。 |
| `app/src/test/kotlin/com/tanuki/ChatNotificationPolicyTest.kt` | 10 项纯 JVM：基线 false、true→false、重复 false、前台同/异会话、后台、session/version/event 去重、旧通知未清除时新事件仍提醒、权限/渠道/用户开关、Android<33、非法 id、状态文案。 |
| `app/src/test/kotlin/com/tanuki/ChatNotificationRoutingTest.kt` | 4 项纯 JVM：同会话跨进程稳定 tag/id、不同会话 Identity 不同、新事件可再次提醒/摘要静默、非法 id 拒绝。 |
| `app/src/test/kotlin/com/tanuki/DshChatProtocolTest.kt` | 4 项纯 JVM：四类事件解析、运行中补种、非法载荷、脚本只读边界/打开会话函数。 |
| `scripts/test-chat-session-open.mjs` | Node 假 DOM 回归：侧栏初始收起、保留非空搜索且当前为其他会话时，展开侧栏/清除搜索/展开分组、只点一次、确认选中后才成功。 |
| `.github/workflows/verify.yml` | Host job 增加 `node scripts/test-chat-session-open.mjs`。 |
| `docs/INTERFACES.md` | 新增 1.6 节，固定页面旁路来源、字段、抑制、去重、点击与限制。 |
| `docs/TEST-REPORT.md` | 新增本轮实际命令、结果和未验证范围。 |
| `docs/AGENT-STATE.md` | 更新当前交接、基线、实现/验证/待验证、环境与下一步。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin` | BUILD SUCCESSFUL（首次约 4m29s） | 本 worktree，JDK17/SDK36；只有既有弃用警告。 |
| 同上 `:app:testModernDebugUnitTest --tests 'com.tanuki.ChatNotificationPolicyTest' --tests 'com.tanuki.ChatNotificationRoutingTest' --tests 'com.tanuki.DshChatProtocolTest'` | BUILD SUCCESSFUL，18 tests / 0 failures | 新增 10 + 4 + 4 项。 |
| `node scripts/test-chat-session-open.mjs` | `CHAT_SESSION_OPEN_TEST_OK` | 收起侧栏 + 非空搜索 + 其他会话、搜索异步清除、分组展开、已选中目标、非法 id。 |
| 同上完整 `:app:testModernDebugUnitTest` | BUILD SUCCESSFUL，223 tests / 0 failures / 0 errors / 0 skipped（39 个 XML 结果文件） | 既有 205 + 新增 18；不是设备测试。 |
| 同上 `:app:lintModernDebug` | BUILD SUCCESSFUL（约 4m44s） | lint 报告 `app/build/reports/lint-results-modernDebug.html`；未构建 APK。 |
| `python3` 抽取注入脚本 + `node --check /tmp/tanuki-chat-bridge.js` | JS_SYNTAX_OK | 仅语法。 |
| `/tmp` 合成 Fake WebSocket 脚本 | `JS_BRIDGE_TEST_OK 5` | 状态 true/false、error、approval、question 五帧均被识别；`secret` 工具名/错误文本/消息未出现在原生消息中；脚本未读取 `innerText`/`textContent`/localStorage。 |
| `git diff --check` | 通过 | 见提交前检查。 |
| PR #22 GitHub Actions `verify` | Host checks / Android build+JVM+lint 全部 pass | PR 页面；首轮 Android 曾因既有 `NpmAdaptiveWorkerPoolTest` 并发时序失败，重跑通过；Review 修复提交 `21cc811` 与搜索过滤修复提交 `462d266` 的 Host/Android 两 job 均全绿，未改该并发测试。 |
| rebase 到 `main=5fd1ab4`（安全模式 PR #25 + XLSX PR #23）后 `:app:compileModernDebugKotlin` + 完整 `:app:testModernDebugUnitTest` + `:app:lintModernDebug` | BUILD SUCCESSFUL，283 tests / 0 failures / 0 errors / 0 skipped | 在 `/tmp/tanuki-chat-notify` rebase clone 复跑；聊天通知 18 项与安全模式 32 项、XLSX 28 项共存。 |

- 本轮新跑的是上述编译、完整 JVM、lint 和本地脚本模拟；223 项中既有 205 项为同一任务内完整套件结果，不是历史摘要。
- 未执行：assemble、Android 仪器测试、真实设备/模拟器安装、Android 13 权限弹窗、真实通知展示/点击、真实 DSH WebSocket、后台/息屏、MIUI/其他 ROM、WorkManager/网络。

## 未完成与暂停项

- 未完成：独立 Codex High Review；设备/ROM 通知与点击会话验收。
- 未完成：真机上确认 `WebViewFeature.DOCUMENT_START_SCRIPT` / `WEB_MESSAGE_LISTENER` 可用；若 WebView 不支持则旁路不安装，应用不会伪造通知。
- 未完成：DSH 升级后复核官方转发事件表和页面 `data-conversation-session` / `data-row-key` 属性是否变化。
- 外部阻塞：本机无授权设备执行；不把宿主/JVM 结果冒充 Android 执行。
- 用户暂停保持：API Key 保存、真实模型/账号验收、真机验收。

## 下一步

1. 4 条 Review 修复已推送 PR [#22](https://github.com/leezzou/tanuki/pull/22)（侧栏搜索过滤修复提交 `462d266`），CI 通过且全部 thread 已 resolve；等待独立 Codex High Review 与设备 QA，不合并、不触发 `ao review trigger`。
2. 由 Orchestrator 安排独立 Codex High Review；设备 QA 需独立会话、确认无真实任务后执行。
3. 设备验收至少覆盖：Android 13 首次权限请求/拒绝后设置入口、四类通知与 group/summary、同会话新事件再次提醒、前台同会话抑制、后台/息屏送达、侧栏收起且保留搜索条件时点击其他会话通知、MIUI 通知/电池/自启动设置、DSH 运行中加载页面后的完成提醒。
4. 若 DSH 官方事件或页面属性变化，先更新 `docs/INTERFACES.md` 和回归，再改实现；不得用猜测事件或读取正文替代。

## 约束与风险

- 数据/设备/权限边界：不读取/输出 token、Cookie、会话内容、工具参数、错误原文；通知只含类型；不新增原生 JS Bridge 方法；WebMessageListener 只接受当前精确 origin 和主框架。
- 关键决策：优先使用官方 `/api/remote.mux` `$events` 转发事件；不另建原生 WebSocket 复制 Cookie；页面只旁路现有消息监听，不替换 WebSocket；回合完成只在观察到 true→false 时提醒；页面已在运行时通过会话行 `data-state="ongoing"` 补种一次 running。通知用会话 tag + 固定 id 跨进程稳定；PendingIntent 用会话级 data/action 区分，不靠进程内计数。
- 风险：DSH 是官方 npm 包，事件表和 HTML 属性属于页面内部契约；升级后需复核。错误来源为官方 `api-session/error`；若某类失败只体现在 `turn/end` 而不触发该事件，只会按回合完成提示，需在 DSH 升级后复核。WebView 后台可能被系统节流，通知可能延迟；ROM 可能拦截后台网络/通知。点击会话会先点侧栏展开入口（按 aria-label/内部 toggle 兜底）、清除非空搜索（官方清除按钮优先，输入框 setter+input 兜底）、展开折叠工作区分组，最多重试 15 次；归档或清搜索后仍被其他过滤隐藏时显示 Toast，不谎报成功。
- 失败尝试：未尝试通过原生 Cookie 直接开 WebSocket 或 `addJavascriptInterface`，避免扩大凭据/攻击面。
- 未删除旧交接记录：旧记录的设备验收等独有未完成项尚未完整迁移，不属于“可清理的过期记录”；保留为验证依据。

## 产物与校验

- 本轮不生成 APK；PR 只含源码、测试和文档。
- 提交前需运行 `python3 scripts/check-handoff.py --record docs/handoffs/20261109-1742-dsh-chat-notifications.md`、`python3 scripts/check-handoff.py`、`python3 scripts/package-source.py`，并核对 `artifacts/SOURCE-SHA256SUMS`；源码包为工作材料，不提交。
- 未发布、未覆盖安装、未切换签名。PR：https://github.com/leezzou/tanuki/pull/22（实现提交 `09396bc`，head 以 PR 页面为准）。
