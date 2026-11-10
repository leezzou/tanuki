# 交接：20261110-1226-native-parity-non-chat

- Agent/会话标识：android_agent-44
- 时间：2026-11-10 12:26 CST
- 状态：评审修复完成（3 项 P2 + transport 薄接口），head 以 PR 页面为准；设备 QA 待另行授权
- 前一记录：[0.1.10 Pre-release 发布准备](20261109-2310-release-0.1.10.md)

## 用户目标与范围

- 用户本次实际要求：Tanuki 原生界面与官方网页版功能对齐；本会话负责非聊天主链路，与 android_agent-42 分工，避免抢同一批文件。范围包括会话管理、工作区、设置、技能/插件/命令、通知点击/远程/安全模式兼容。
- 本次包含：会话列表/搜索/新建/重命名/归档/未读/运行状态；会话级模型与权限；Skills 与斜杠命令目录；独立原生会话管理页；设置入口；安全模式只读；远程 origin 复用；功能清单与文件边界文档。
- 本次不包含：聊天流式/输入发送/停止/消息渲染；工作区全局搜索与 diff；插件列表/启用；斜杠命令执行；应用内语言/主题偏好；真实设备验收；合并/发布。
- 用户改变或暂停的要求（需保留）：不合并、不发布；保留官方 DSH UI 可切换；不另造聊天协议；author/committer 必须 `leezzou`；不要出现参考实现名称。

## 当前状态

- 已实现：`DshSessionAdminApi` 官方一元协议与纯解析；`DshSessionManagerController` 状态机；`DshSessionManagerPage` Compose 页；`DshSessionManagerActivity` 独立入口；设置 → 网页与兼容性 → 原生会话管理；安全模式只读；远程 DSH 连接可用。
- 已验证：`compileModernDebugKotlin`；评审修复后完整 `testModernDebugUnitTest` **74 个结果文件、626 tests / 0 failures / 0 errors / 0 skipped**；`lintModernDebug` 0 errors / 97 warnings / 1 hint；`assembleModernDebug` BUILD SUCCESSFUL（未安装设备）。
- 待验证：真实 Android/PRoot DSH 的 `/connect`、`session/create|rename|search`、`workspace/archiveSession`、模型/权限命令；设备 UI/深色/大字体；远程 SSH 端点；Android 仪器测试。
- 当前分支：`ao/native-parity/session-admin`，基线 `origin/main=0555c3c0`，已合并 `origin/main=533eff4`（文件查看器）、`1cb7ad9`（原生对齐审计矩阵；44 只读核对、不写矩阵）、`2a4a050`（文件复制稳定）与 `0068c87`（远程中继）；PR [#38](https://github.com/leezzou/tanuki/pull/38)。按编排器要求，P2 修复推送前不再合 main；评审修复前的 CI 全绿 head 为 `1e40b33`，最终 head 以 PR 页面为准。未合并、未发布、未触发 AO Review。
- 关键事实：官方 `0.2.1-rc.5` 无会话硬删除端点；归档用 `workspace/archiveSession`；归档恢复需要 `workspace/follow` 流，未实现；未读是本机 `updatedAt` 比较。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshSessionAdminApi.kt` | 新增会话管理 method/参数、响应解析、`DshSessionAdminSource`；网络收敛到 `DshAdminTransport`，`DshHttpAdminTransport` 为临时实现，提供 `DshAdminTransportAdapter` 供 42 `DshTransport` 接线 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerController.kt` | 新增列表/过滤/搜索/新建/重命名/归档/未读/模型/权限/Skills/命令状态机；安全模式只读、完全权限显式确认、归档响应排除；P2 修复 close/beginAction loading 与端点切换缓存失效 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerPage.kt` | 新增原生会话管理页、详情面板、模型/权限/目录面板、新建/重命名/归档/完全权限确认对话框 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerActivity.kt` | 新增独立入口 Activity；复用 `RuntimeManager` 的同一个 origin 与状态；本地/远程/安全模式判断；origin 变化清理旧端点目录缓存；退出丢弃内存 Cookie |
| `app/src/main/AndroidManifest.xml` | 声明 `DshSessionManagerActivity`，`exported=false` |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 回调增加 `openSessionManager`；设置 → 网页与兼容性增加“原生会话管理”入口 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 接线启动独立管理 Activity；不改聊天页/原生 DSH 页 |
| `app/src/test/kotlin/com/tanuki/DshSessionAdminApiTest.kt` | 新增 8 项协议/端点/错误/Cookie/transport 适配回归 |
| `app/src/test/kotlin/com/tanuki/DshSessionManagerControllerTest.kt` | 新增 18 项状态机/过滤/未读/写操作/安全模式/权限确认/loading 恢复/端点切换回归 |
| `docs/NATIVE-PARITY-NON-CHAT.md` | 新增功能清单、官方方法表、文件边界、契约冲突与后续项 |
| `docs/INTERFACES.md` | 增加 1.7.1 原生会话管理官方 method 与边界 |
| `docs/NATIVE-UI.md` | 增加管理页、文件、切换语义、安全与验证 |
| `docs/TEST-REPORT.md` | 记录本轮实际命令、结果与未测范围 |
| `docs/AGENT-STATE.md` / `docs/handoffs/CURRENT.json` | 更新本次记录链接与交接印章 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin -x buildNativeRuntime` | BUILD SUCCESSFUL（2m04s；仅既有告警） | JDK17 项目缓存、macOS ARM64；不代表设备 |
| 新增测试 `DshSessionAdminApiTest` + `DshSessionManagerControllerTest` | BUILD SUCCESSFUL，21 tests | 本地合成 HTTP 服务器，无真实 DSH |
| `./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime` | BUILD SUCCESSFUL，67 个结果文件、556 tests / 0 failures / 0 errors / 0 skipped | 与本轮代码同次编译；不含设备/仪器 |
| `./gradlew :app:lintModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL，0 errors / 95 warnings / 1 hint | 无新增 Error；告警为既有弃用/可读性 |
| `./gradlew :app:lintModernDebug -x buildNativeRuntime --rerun-tasks` | BUILD SUCCESSFUL（3m33s，28 tasks executed），0 errors / 95 warnings / 1 hint | 最终源码强制重跑，不是缓存结果 |
| `./gradlew :app:assembleModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL（3m29s，40 tasks） | 只验证 APK 打包与清单，未安装设备、未签名发布 |
| 合并 `533eff4`/`1cb7ad9`/`2a4a050`/`0068c87` 后完整 JVM | BUILD SUCCESSFUL，74 个结果文件、621 tests / 0 failures / 0 errors / 0 skipped | 含文件查看器 23 项、文件复制 19 项、远程中继 22 项；无设备 |
| 合并后 `lintModernDebug` / `assembleModernDebug` | BUILD SUCCESSFUL，0 errors / 97 warnings / 1 hint；打包成功 | 新增 BlobDownloads/远程中继既有告警各 1 条；未安装设备 |
| 再合并 `origin/main=1cb7ad9` 审计矩阵 | 只读文档/CI 脚本，不改产品代码；`scripts/check-native-parity.py` 保持通过 | 44 不写矩阵，状态由矩阵属主更新 |
| 评审修复后完整 `testModernDebugUnitTest` | BUILD SUCCESSFUL，74 个结果文件、626 tests / 0 failures / 0 errors / 0 skipped | 新增 4 项 loading/端点回归 + 1 项 transport 适配回归；无设备 |
| 评审修复后 `lintModernDebug` / `assembleModernDebug` | BUILD SUCCESSFUL，0 errors / 97 warnings / 1 hint；打包成功 | 未安装设备 |
| PR #38 Host / Android CI（代码 head `1e40b33`） | 全部 pass | 评审修复前 head；P2 修复后 head 以 PR 页面为准 |

- 哪些是前一轮结果，哪些是本次新验证：556 JVM、lint、新增 21 项均为本轮新跑；535 tests/393 tests 等为历史，未冒充本轮。
- 一次 `--rerun-tasks` 全量组合中 `NpmAdaptiveWorkerPoolTest` 的 downshift 并发时序失败；该测试单独重跑与完整 JVM 重跑均通过，未改该测试，属既有 flake。
- 未测试、超时、失败和暂未复现的情况：真实 Android/PRoot DSH、真实 `/connect` Cookie 生命周期、`commands/execute` 的 `/permission` 实际结果、`workspace/archiveSession` 运行中停止语义、设备 UI、远程 SSH、Android 仪器。

## 未完成与暂停项

- 未完成：真实设备/PRoot 验收；工作区全局搜索/diff；插件列表；斜杠命令执行；应用内语言/主题；归档列表恢复。
- 外部阻塞：无（网络与构建可用）。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收按原状态保留。

## 下一步

1. 推送 P2 修复 head，回复并 resolve 三条 review thread（PRRT_kwDOVgRm4s61kEtf、PRRT_kwDOVgRm4s61kEtk、PRRT_kwDOVgRm4s61kEtl），回报 head。
2. 按编排器暂停要求，不在 P2 推送前合 main；待编排器确认后再统一处理共享状态/印章冲突。
3. 42 的 PR #40 `DshTransport` 合并后，用 `DshAdminTransportAdapter(transport::call, transport::reset)` 替换 `DshHttpAdminTransport`，删除临时 HTTP 栈；不重写会话管理逻辑。
4. 设备 QA 授权后验证真实 PRoot DSH 的一元接口、归档/权限与页面布局；后续把管理 composable 嵌入原生 DSH 页。
5. 后续切片：工作区全局搜索/diff、插件列表与启用、斜杠命令执行、应用内主题/语言、归档列表恢复（需 WebSocket）。

## 约束与风险

- 数据/设备/权限边界：不读真实凭据；API Cookie 只在内存；不写 `userdata/dsh`；不删会话/项目；不合并/发布；不关闭审批/TLS/校验；不自动切 `danger-full-access`。
- 关键决策及理由：因 42 正在重写 `DshNativeApi.kt` 并新增 WebSocket 流，本切片新增独立一元客户端与独立 Activity，避免同文件并发写；共享文件只做最小加法。完全权限需界面二次确认与控制器显式参数，避免自动提权。
- MainActivity 归 42 独占：本 PR 只保留已放行的 import + `openSessionManager = { startActivity(DshSessionManagerActivity.intent(this@MainActivity)) }` 两行，后续不再改；若 42 重写该处，需要保留的 hook 是 `TanukiUiCallbacks.openSessionManager: () -> Unit`（设置页“原生会话管理”入口）与 Manifest 中的 `DshSessionManagerActivity` 声明。
- transport：会话管理只依赖 `DshAdminTransport.call(method,args): Any?` 与 `reset()`；42 的 `DshTransport` 还需提供 `connection: StateFlow<DshConnectionState>` 与 `live(sessionId): Flow<DshLiveEvent>`，但本切片不使用 live。
- 失败尝试与避免重复踩坑的结论：Android JVM 单测不能使用 JDK `com.sun.net.httpserver`，已改用原始 `ServerSocket`；合成服务器读取含中文请求体必须按 ISO-8859-1 逐字节读取，否则 `Content-Length` 与字符数错位导致 5 秒超时。
- 提交前最后硬化：会话/模型/权限字段截断；`session/create` 返回标识要求可打印 ASCII；归档响应已确认 ID 在刷新后仍从列表排除；写操作期间禁用刷新；查询变化丢弃在途全文搜索；完全权限需显式 `allowFullAccess=true`；模型目录失败信息展示前经 `Redact` 截断。
- 已知风险：独立 Activity 与原生聊天页暂时是两个入口；后续需把 `DshSessionManagerPage` 嵌入聊天页或统一导航。官方 `session/search` 可能返回已归档会话，打开时会提示目标不在当前列表。远程 DSH 是否为安全模式无法从本地状态得知，写入失败由官方错误提示。
- 参考实现名称：文档/注释/提交未写入参考实现名称。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本切片不生成发布 APK、不更新 `artifacts/releases/`；`assembleModernDebug` 仅用于验证，APK 在 `app/build/outputs/`，不作为交付包。源码 ZIP 由 `package-source.py` 在收尾时生成并核对，属工作材料，不提交。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装设备、未覆盖安装、未签名发布、未创建 tag/Release。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮未删除历史交接；不新增过期记录。当前记录保留，`CURRENT.json` 在收尾时刷新。
- 仍需保留的验证/未完成记录：`docs/TEST-REPORT.md` 本轮章节、`docs/NATIVE-PARITY-NON-CHAT.md`、本记录。
