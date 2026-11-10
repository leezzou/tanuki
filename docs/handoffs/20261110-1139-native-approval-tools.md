# 交接：20261110-1139-native-approval-tools

- Agent/会话标识：android_agent-45（AO worker）
- 时间：2026-11-10 11:39–14:00 CST
- 状态：实现与 JVM/lint 验证完成；已合入 `main=533eff4`（含 PR #34 文件查看器格式扩展）；设备未验证；PR [#41](https://github.com/leezzou/tanuki/pull/41) 待审；未合并、未发布
- 前一记录：[原生界面与官方界面切换](20261109-1829-native-ui-switch.md)

## 用户目标与范围

- 用户本次实际要求：在 Tanuki 原生 DSH 界面补齐「审批/权限/工具调用」切片：
  1. 审批/权限请求完整展示工具名、参数、风险、允许/拒绝/记住选择，并按官方协议回传；
  2. 工具调用卡片展示运行中/成功/失败、输出折叠、diff/命令/文件变更；
  3. 与安全模式、远程 origin、通知点击兼容；失败给中文错误，不静默吞；
  4. 补 JVM/协议回归；设备未验证如实标注；
  5. 中文注释、author/committer=`leezzou`、PR、不合并、不发布；文档/注释/提交不出现参考实现名称。
- 本次包含：原生页审批 waterfall、工具事件解析与卡片、最小 WebSocket `/api/remote.mux` 客户端、官方 `$events/result` 回答、远程 loopback origin 复用、安全模式下的明确降级提示。
- 本次不包含：聊天发送/取消/附件/模型选择（android_agent-42）、会话列表/设置/技能（android_agent-44）、官方 npm 包修改、DSH 数据目录改写、真实设备验收、合并/发布。
- 用户改变或暂停的要求（需保留）：API Key 保存、真实模型/账号验收、真机验收仍暂停；不自行合并/发布。

## 当前状态

- 已实现：审批协议/回答、WebSocket 审批通道、工具时间线/卡片、审批面板、控制器生命周期、MainActivity 接线、文档与回归。
- 已验证：`compileModernDebugKotlin`、完整 `testModernDebugUnitTest`、`lintModernDebug`、`assembleModernDebug` 均通过；完整 JVM 结果见下。最终复核：569 tests / 0 failures / 0 errors / 0 skipped，lint 0 errors / 94 warnings / 1 hint，assemble BUILD SUCCESSFUL（未安装）。
- 待验证：真实设备/模拟器 UI、真实 PRoot DSH `/connect`/WebSocket/审批回答、远程 SSH 隧道、安全模式 rescue、通知点击过滤、assemble/仪器/真机。
- 当前目标包名：`com.tanuki.modern`（debug）；分支 `ao/tool-approval/root`，基线 `origin/main=0555c3c`，已合入 `main=533eff4`；无运行中安装/设备任务。
- 关键事实：官方 DSH `0.2.1-rc.5` 审批是 Agent-scoped waterfall，经 `/api/remote.mux` `$events` 下发；回答走 `POST /api/$events/result`，outcome 仅 `allowed-once`/`rejected`/`cancelled`/`unavailable`。工具卡片来自 `session/page` 的 `tool/call`、`tool/result` 与 `meta.diffs`。

## 合并说明

- 2026-11-10 合入 `origin/main=533eff4`（PR #34 文件查看器格式扩展）。代码无重叠；仅 `docs/handoffs/CURRENT.json` 冲突，已保留本切片为当前交接、PR #34 为上一记录，并刷新交接印章。合并后需按最终 head 重跑 CI。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshApprovalProtocol.kt` | 新增：审批帧/回答信封/风险分级/本次运行记忆；只读白名单字段，不复制请求正文。 |
| `app/src/main/kotlin/com/tanuki/web/DshWebSocketClient.kt` | 新增：最小 RFC6455 客户端；只连 loopback，Cookie 握手，校验 Accept，客户端掩码、拒绝服务端掩码、1 MiB 上限。 |
| `app/src/main/kotlin/com/tanuki/web/DshApprovalSession.kt` | 新增：`/connect` 换 Cookie、`$events` 流、`$events/result` 回答、401 重认证一次、ready 10 秒超时、中文错误；定义可测试 `DshApprovalTransport`。 |
| `app/src/main/kotlin/com/tanuki/web/DshToolTimeline.kt` | 新增：`tool/call`/`tool/result` 时间线、命令/cwd/路径、输出截断、`meta.diffs`、replace 去重、diff LCS 展示。 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalController.kt` | 新增：审批状态机、连接/重试/关闭、允许/拒绝/记住本次运行、断线与 origin 变化处理。 |
| `app/src/main/kotlin/com/tanuki/ui/DshApprovalPanel.kt` | 新增：审批卡片；工具名、参数（长参数可展开）、风险、允许/拒绝/记住、其他会话计数、错误重试。 |
| `app/src/main/kotlin/com/tanuki/ui/DshToolCard.kt` | 新增：工具卡片；运行中/成功/失败、命令、文件变更、diff、参数、折叠输出、风险徽标。 |
| `app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt` | 增量：`DshSessionPage.timeline`，复用 `messageOf` 提取文本；保留既有 `messages` 契约。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt` | 增量：状态增加 `timeline`，历史成功时填充；清理时同步清空。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 增量：审批面板放入可滚动时间线（空会话时也可达），渲染文本/工具时间线；新参数带默认值，兼容既有调用。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 增量：创建审批控制器、复用 endpoint、按服务/模式/远程 origin 启停、传入当前会话与工具查找。 |
| `app/src/test/kotlin/com/tanuki/DshApprovalProtocolTest.kt` | 新增 9 项协议/风险/记忆回归。 |
| `app/src/test/kotlin/com/tanuki/DshToolTimelineTest.kt` | 新增 8 项工具/命令/diff/分页回归。 |
| `app/src/test/kotlin/com/tanuki/DshWebSocketTest.kt` | 新增 5 项 RFC6455/本地握手回归。 |
| `app/src/test/kotlin/com/tanuki/DshApprovalSessionTest.kt` | 新增 5 项 `/connect`→`$events`→`$events/result`/401 回归。 |
| `app/src/test/kotlin/com/tanuki/DshApprovalControllerTest.kt` | 新增 7 项控制器/记忆/断线/origin 回归。 |
| `docs/INTERFACES.md` | 更新 1.7：`$events`、`$events/result`、工具事件、记住选择与安全边界。 |
| `docs/NATIVE-UI.md` | 更新结论、端点表、文件表、安全边界与验证/未验证。 |
| `docs/TEST-REPORT.md` | 新增本切片验证记录。 |
| `docs/handoffs/20261110-1139-native-approval-tools.md` | 本记录。 |

文件边界：未修改 `DshUiMode.kt`、`TanukiUi.kt`、`DshChatProtocol.kt`、`DshWebView.kt`、`RuntimeManager.kt`、`RemoteHost*`、工作区文件/设置/技能相关文件，避免与 android_agent-42/44 抢占。

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh` + `./gradlew :app:compileModernDebugKotlin -x buildNativeRuntime --console=plain` | BUILD SUCCESSFUL | JDK17 worktree 环境；未跑 assemble |
| `./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime --console=plain` | BUILD SUCCESSFUL；70 个结果文件，569 tests / 0 failures / 0 errors / 0 skipped | 基线 535 + 新增 34 |
| `./gradlew :app:lintModernDebug -x buildNativeRuntime --console=plain` | BUILD SUCCESSFUL；0 errors / 94 warnings / 1 hint | 存量弃用/可读性告警；非零告警 |
| `./gradlew :app:assembleModernDebug -x buildNativeRuntime --console=plain` | BUILD SUCCESSFUL | 仅打包验证，未安装/未设备执行 |
| 目标回归 `DshApprovalProtocolTest` 9、`DshToolTimelineTest` 8、`DshWebSocketTest` 5、`DshApprovalSessionTest` 5、`DshApprovalControllerTest` 7 | 34/34 通过 | 本地合成 HTTP+WebSocket 服务器；不代表真实 DSH |
| 既有 `DshNativeApiTest` 12、`DshNativeControllerTest` 6、`DshUiSwitchTest` 10 | 通过 | 时间线增量未破坏旧契约 |
| 官方 npm 包类型/客户端只读核对 | 已确认 `$events`、`$events/result`、`tool/call`、`tool/result`、`FileDiff` | 包在 `/tmp` 临时目录，不作为交付物 |

- 哪些是前一轮结果，哪些是本次新验证：以上产品编译/JVM/lint 均为本次新跑；未复用历史数字。
- 未测试、超时、失败和暂未复现的情况：真实设备/模拟器、真实 PRoot DSH、远程 SSH 隧道、安全模式 rescue、通知点击过滤、Android 仪器、真机均未执行；assemble 已执行但未安装。

## 未完成与暂停项

- 未完成：PR 已创建（[#41](https://github.com/leezzou/tanuki/pull/41)），等待审查；不合并、不发布。真实设备 QA 另行授权。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收。

## 下一步

1. 提交前清理过期交接（按 `docs/PUBLICATION.md` 评估），运行 `python3 scripts/check-handoff.py --record docs/handoffs/20261110-1139-native-approval-tools.md`、`python3 scripts/check-handoff.py`，必要时刷新 `CURRENT.json`/源码包。
2. PR [#41](https://github.com/leezzou/tanuki/pull/41) 已创建；等待独立审查与 CI，不合并、不发布。
3. 真实设备/PRoot 审批与工具卡片验收由独立 QA 另行授权；远程 SSH 隧道与安全模式 rescue 同样待验。

## 约束与风险

- 数据/设备/权限边界：不读真实 Key/Cookie/token/会话；不改 DSH 数据；设备 QA 由独立 QA 独占。
- 关键决策及理由：审批回答只走官方 `$events/result`，不自造 HTTP 端点；“记住选择”是原生本次运行内的自动回答策略，不冒充官方持久授权；无结果工具卡片标“进行中/未读取到结果”，不伪造成功。
- 失败尝试与避免重复踩坑的结论：官方无持久“记住”outcome；append 的 `surfaceOp` 是字符串 `"append"`，replace 才是对象；同一 callId 的 replace 结果必须更新已有卡片；WebSocket 服务端帧不能掩码；ready 未到不能假装可回答。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未生成 APK/源码 ZIP；未安装、未发布。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装；未发布。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：待收尾评估；当前保留既有记录。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP。
