# 交接：20261109-1829-native-ui-switch

- Agent/会话标识：android_agent-33（DSH Worker）
- 时间：2026-11-09 18:29 CST
- 状态：部分完成（P1/P2 修复完成，已 rebase 到 `main=5fd1ab4`；rebase 后本地 JVM/lint 通过，单一提交待推送；最终 CI、独立 Pi Review、设备与真实 DSH 未验证）
- 前一记录：[DSH 已安装版本管理](20261109-1539-dsh-installed-version-manager.md)（android_agent-25，历史记录保留）

## 用户目标与范围

- 用户本次实际要求：原生 UI 与官方 DSH UI 可切换，先交付可验证最小切片；先 discovery 官方会话/消息/发送/审批 API；默认官方；新增“官方界面/原生界面”开关；原生侧用官方 API 做真实可用的只读面；不修改官方 npm 包/DSH 数据；切换不丢会话、不重复启动服务、WebView 与原生共享 token/session；token 不落日志；JVM 回归；`/tmp` 独立 clone 提交并开 PR，不合并、不触发 Review。
- 基线：`main=2f3fac9301c7c3484df85e99091e8f63dddc0a48`；rebase 目标 `main=0ba11da5af89fc3554f678b9b3bf6908f8622eb8`（含 PR #24 PDF、PR #27 cold-start、PR #22 chat-notify、PR #23 XLSX 与 PR #25 runtime-safe-mode）。
- 本次包含：官方 API 发现、界面偏好与回退策略、官方 unary 客户端、会话列表 + 只读历史页、设置开关、JVM 回归、文档。
- 本次不包含：发送/取消/审批/提问、WebSocket 实时跟随、附件/模型选择、官方包修改、设备/真机、assemble、合并/发布/tag、AO Review 触发。

## 当前状态

- 已实现：
  - `DshUiMode` / `DshUiSwitchPolicy`：默认 `official`；只有用户选 `native`、服务运行且无回退原因时才显示原生；接口失败回退官方但保留偏好。
  - `DshApiProtocol` / `DshNativeClient`：同一启动 token 调 `/connect?token=...&interactions=none`；`POST /api/session/list|projections|page`；校验 `rpcId`；只读文本消息；内存 Cookie，不写日志。P1：认证网络 I/O 不再持 monitor，`reset()` 立即递增代次并清 Cookie，旧请求不能重新发布；P2：仅接受 `surfaceOp=append`，并跳过非 `source.kind=user` 的注入上下文；401 重试也必须属于当前代次，已被 reset 的旧请求不能复活。
  - `DshNativeController` / `DshNativePage`：会话条 + 文本消息 + 空态/错误态；列表失败触发官方回退；历史页失败只影响当前会话。
  - `TanukiUi` / `MainActivity`：设置开关、原生/官方切换、回退提示与重试；保留同一 `DshWebView` 与 DSH 进程；服务停止时丢弃内存 Cookie。
- 已验证（rebase 后新跑）：
  - `:app:testModernDebugUnitTest -x buildNativeRuntime` BUILD SUCCESSFUL，393 tests / 0 failures / 0 errors / 0 skipped；新增 `DshUiSwitchTest` 10 项、`DshNativeApiTest` 12 项、`DshNativeControllerTest` 6 项。
  - `:app:lintModernDebug -x buildNativeRuntime` BUILD SUCCESSFUL（2m4s；仅既有弃用/可读性告警）。
- 待验证：真实 PRoot 内 DSH 的 `/connect` 与 HTTP 一元调用；设备上的布局/暗色/长文本/全屏/返回；WebSocket 实时流与审批/发送；服务重启后 Cookie；低版本 DSH 缺少端点时的回退；assemble/仪器。
- 当前分支/PR：`ao/ui-switch/native-ui`，PR [#26](https://github.com/leezzou/tanuki/pull/26)，rebase 到 `main=5fd1ab4`；rebase 后单一提交待推送，最终 SHA/CI 以 PR 页面为准（独立 worktree，未启动 DSH、未操作设备）。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshUiMode.kt` | 新增偏好枚举与纯策略；默认官方，失败回退不改偏好。 |
| `app/src/main/kotlin/com/tanuki/web/DshNativeApi.kt` | 官方 unary 协议封装、Cookie 获取、会话/切点/历史解析与安全校验；P1 代次化认证状态；P2 append-origin/用户来源过滤。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt` | 新增原生只读页状态机与错误映射；列表失败回退，历史失败局部提示。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 新增原生只读 Compose 页，不伪造聊天/审批。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 新增设置开关、原生/官方切换、回退横幅与重试；原生模式不再要求 WebView 能力。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 接线偏好持久化、控制器、回退与服务生命周期；不重启服务。 |
| `app/src/test/kotlin/com/tanuki/DshUiSwitchTest.kt` | 开关/回退/路由/信封/通知路由回归 9 项。 |
| `app/src/test/kotlin/com/tanuki/DshNativeControllerTest.kt` | 原生通知目标冷启动/已加载/目标缺失/失败回退/当前会话强制重读/决策+分发集成 6 项。 |
| `app/src/test/kotlin/com/tanuki/DshNativeApiTest.kt` | 解析/Cookie/rpcId/端点/token 不泄露、append/replace fixture、延迟认证 reset、401-after-reset 回归 11 项。 |
| `docs/NATIVE-UI.md` | 新增官方 API 发现、端点/缺口、实现、安全与未验证项。 |
| `docs/INTERFACES.md` | 增加 1.6 原生界面官方 DSH 接口摘要。 |
| `docs/TEST-REPORT.md` | 增加本轮 JVM 验证记录。 |
| `docs/AGENT-STATE.md` | 更新当前交接与本轮状态。 |

- rebase 处理：`origin/main` 仅修改 `docs/AGENT-STATE.md`、`docs/TEST-REPORT.md`、`docs/handoffs/CURRENT.json`，代码无冲突；以 main 文档为底稿合并原生界面章节，保留 XLSX 记录，将原 5 个提交压缩为 rebase 后单一提交。最终文档已同时保留 XLSX 与原生界面两条线，rebase 后完整 JVM 为 251 项。

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin` | 通过 | 本 worktree，JDK17；仅既有弃用告警。 |
| `./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime --tests 'com.tanuki.DshUiSwitchTest' --tests 'com.tanuki.DshNativeApiTest'` | 通过 | 18 tests / 0 failures（rebase 后）。 |
| `./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime` | 通过 | rebase 到 `0ba11da` 后 393 tests / 0 failures / 0 errors / 0 skipped（1m50s）。 |
| `./gradlew :app:lintModernDebug -x buildNativeRuntime` | 通过 | rebase 后 BUILD SUCCESSFUL（2m4s）；仅既有弃用/可读性告警，报告为工作材料。 |
| 官方包只读核对 | 完成 | 在官方 npm 包 `@deepseek-ai/dsh 0.2.1-rc.5` 的 README/类型声明/`lib` 中核对 `/connect`、`/api`、`remote.mux`、`session/*`、审批 waterfall；未在设备运行真实 DSH。 |

- 前一轮 205 JVM 不是本轮结果；rebase 到 `0ba11da` 后完整套件为 393（含 main 的 PDF、cold-start、XLSX、runtime-safe-mode、chat-notify 与通知目标/401/并发回归）。
- 未测试：真实 HTTP 往返、WebSocket、设备 UI、进程重启、assemble/仪器。

## 未完成与暂停项

- 未完成：rebase 后单一提交推送与最终 CI；独立 Pi Review（不自行触发，等待 Orchestrator）；设备/真实 DSH 验收。
- 外部阻塞：无（等待 PR 流水线与审查/设备安排）。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；本轮未触碰。

## 下一步

1. PR #26：一次推送 rebase 后的单一提交，随后解析/回复 review thread；最终 CI 以 PR 页面为准。不合并、不自行触发 Review。
2. 最终 CI 失败时按日志修复；若需评审，使用 Orchestrator 指定的 Pi（openai/gpt-6-astra/high），不自行回退 Codex。
3. 设备验收（另行授权）：启动既有 AVD，确认服务运行后开关默认官方；切原生看到官方会话与消息；停止服务再启动不丢会话；断网/404/401 回退官方并可重试；切换前后 DSH 进程未重启、WebView 未重建；logcat 无 token/Cookie。
4. 若后续实现实时流/审批：先补 `INTERFACES`/本专题契约，再实现 `/api/remote.mux` 与 answer Peer；不要复用本切片的 unary 通道伪造审批。

## 约束与风险

- 数据/设备/权限边界：未启动设备、未安装、未修改 DSH 数据/官方 npm 包；原生只读。
- 关键决策：官方没有 REST 会话端点，采用官方 Typert unary `/api`；发送/审批无可靠一元端点，宁可不做；原生最小面只读文本，避免假聊天框；P2 采用官方 `isAppendSurfaceEvent()` 语义，只显示 append-origin。
- 安全：token 只在内存与精确 loopback origin 使用；Cookie 只发 `/api`；不跟随重定向；错误只保留短码；WebView 同源策略未改；`reset()` 只短暂持锁，旧认证请求由代次拒绝。
- 风险：`/connect` 创建的 API Peer 未开 WebSocket 时会保留到 Host 退出（官方已知边界）；本切片每服务周期只换取一次 Cookie，切换界面不重复换取。
- 失败尝试：首个 Cookie 测试把 `name=bad;value` 误当非法；实际按官方 Cookie 语法应取 `name=bad`，已修正测试预期。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未生成 APK/ZIP。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未发布、未合并。
- 提交/推送前清理的过期记录：未删除历史记录；本轮只有新增专题/交接和状态更新，避免覆盖并发 Worker 结论。
- 文档变化后仍需运行 `check-handoff.py`、`check-publication.py --git`、`package-source.py`；源码 ZIP 为工作材料，不提交。

结束前：

```bash
python3 scripts/check-handoff.py --record docs/handoffs/20261109-1829-native-ui-switch.md
python3 scripts/check-handoff.py
python3 scripts/package-source.py
```
