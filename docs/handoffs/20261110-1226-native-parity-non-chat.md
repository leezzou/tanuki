# 交接：20261110-1226-native-parity-non-chat

- Agent/会话标识：android_agent-44
- 时间：2026-11-10 12:26 CST（2026-11-10 16:36 发布集成时按合并后事实修正）
- 状态：PR #38 已合并（merge bc802cf，head cf0fb76）；发布集成应用本记录
- 前一记录：[0.1.10 Pre-release 发布准备](20261109-2310-release-0.1.10.md)

## 用户目标与范围

- 用户本次实际要求：Tanuki 原生界面与官方网页版功能对齐；本会话负责非聊天主链路，与 android_agent-42 分工，避免抢同一批文件。范围包括会话管理、工作区、设置、技能/插件/命令、通知点击/远程/安全模式兼容。
- 本次包含：会话列表/搜索/新建/重命名/归档/未读/运行状态；会话级模型与权限；Skills 与斜杠命令目录；独立原生会话管理页；设置入口；安全模式只读；远程 origin 复用；功能清单与文件边界；Codex 三项 P2 修复；transport 薄接口。
- 本次不包含：聊天流式/输入发送/停止/消息渲染；工作区全局搜索与 diff；插件列表/启用；斜杠命令执行；应用内语言/主题偏好；真实设备验收；合并/发布。
- 用户改变或暂停的要求（需保留）：不合并、不发布；保留官方 DSH UI 可切换；不另造聊天协议；author/committer 必须 `leezzou`；不要出现参考实现名称；普通 PR 不修改 `docs/AGENT-STATE.md`、`docs/TEST-REPORT.md`、`docs/handoffs/CURRENT.json`。

## 功能清单与文件边界

### 已实现

| 域 | 交付 |
|---|---|
| 会话管理 | 官方 `session/list` 列表、标题/ID 过滤、官方 `session/search` 内容搜索、`session/create` 新建、`session/rename` 重命名、`workspace/archiveSession` 归档、运行状态、本机未读 |
| 模型/权限 | `session/modelCatalog`/`selectModel` 会话级模型与推理强度；`permissionPresets/catalog` + 官方 `/permission` 命令切换；完全权限必须二次确认 |
| Skills/命令 | `skills/list` 与 `commands/list` 只读目录；显示模型可用/仅人工、输入提示、附件能力 |
| 兼容 | 安全模式/试用模式只读；远程 SSH 隧道 origin 复用；端点切换清理旧目录并使在途响应失效；停止时丢弃内存 Cookie；通知点击路由不变 |

### 未实现/协议不可用

- 会话硬删除：官方 `0.2.1-rc.5` 无端点，未伪造。
- 归档恢复列表：需要 `workspace/follow` WebSocket，后续。
- 未读：本机按最后查看 `updatedAt` 计算，不是官方字段。
- 工作区：文件树/预览/当前目录搜索复用现有查看器；全局搜索与 diff 后续。
- 插件列表/启用、斜杠命令执行、应用内语言/主题：后续。
- 真机/PRoot、Android 仪器未验证。

### 文件边界

- 新增：`app/src/main/kotlin/com/tanuki/web/DshSessionAdminApi.kt`、`ui/DshSessionManagerController.kt`、`ui/DshSessionManagerPage.kt`、`ui/DshSessionManagerActivity.kt`、两个 JVM 测试类。
- 共享加法（已由编排器放行，本次不再改）：`AndroidManifest.xml` 新增 Activity 声明；`TanukiUi.kt` 新增 `openSessionManager` 回调与设置入口；`MainActivity.kt` 仅 import + 一个启动回调。
- 未修改：`DshNativeApi.kt`、`DshNativeController.kt`、`DshNativePage.kt`、聊天 WebSocket 文件、文件查看器文件。
- 本次按新机制不修改 `docs/AGENT-STATE.md`、`docs/TEST-REPORT.md`、`docs/handoffs/CURRENT.json`、`docs/INTERFACES.md`、`docs/NATIVE-UI.md`，这些文件取 main 版本；原专题文档已移除，功能清单/边界/契约保留在本记录。

### transport 契约

- 会话管理只依赖薄接口：
  ```kotlin
  interface DshAdminTransport {
      suspend fun call(method: String, args: JSONObject): Any? // result.value；业务错误抛 DshApiException
      fun reset()
  }
  ```
- `DshSessionAdminClient` 只做 method/参数、解析和中文错误；当前 `DshHttpAdminTransport` 是临时 HTTP 栈。
- 已提供 `DshAdminTransportAdapter(callImpl, resetImpl)`。42 的 PR #40 `DshTransport`（`connection`、`call`、`live`、`reset`）合并后改为 `DshAdminTransportAdapter(transport::call, transport::reset)`；本切片不使用 `live`。

## 当前状态

- 已实现：会话管理完整切片、三项 Codex P2 修复、transport 薄接口与适配器。
- 已验证：完整 JVM **74 个结果文件、626 tests / 0 failures / 0 errors / 0 skipped**；`lintModernDebug` **0 errors / 97 warnings / 1 hint**；`assembleModernDebug` BUILD SUCCESSFUL；merge head `cf0fb76` 的 Host + Android CI 全绿。
- 待验证：真实 Android/PRoot DSH 的 `/connect`、`session/create|rename|search`、`workspace/archiveSession`、模型/权限命令；设备 UI/深色/大字体；远程 SSH 端点；Android 仪器测试。
- 当前分支：PR #38 已合并进 main（merge `bc802cf`，head `cf0fb76`）；本记录随发布集成落地；#40 合并后替换临时 transport。
- 关键事实：官方 `0.2.1-rc.5` 无会话硬删除端点；归档用 `workspace/archiveSession`；归档恢复需要 `workspace/follow` 流；未读是本机 `updatedAt` 比较。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshSessionAdminApi.kt` | 官方 method/参数、响应解析、`DshSessionAdminSource`；`DshAdminTransport` 薄接口、`DshAdminTransportAdapter`、临时 `DshHttpAdminTransport` |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerController.kt` | 列表/过滤/搜索/新建/重命名/归档/未读/模型/权限/Skills/命令状态机；P2：取消请求复位 loading、写操作清列表 loading、端点切换清旧目录并失效在途请求 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerPage.kt` | 原生会话管理页、详情面板、模型/权限/目录面板、新建/重命名/归档/完全权限确认 |
| `app/src/main/kotlin/com/tanuki/ui/DshSessionManagerActivity.kt` | 独立入口 Activity；origin 纳入 key；本地/远程/安全模式判断；退出丢弃内存 Cookie |
| `app/src/main/AndroidManifest.xml` | 声明 `DshSessionManagerActivity`，`exported=false` |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 回调增加 `openSessionManager`；设置 → 网页与兼容性增加入口 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 仅 import + 一个启动回调，已放行；本次未再修改 |
| `app/src/test/kotlin/com/tanuki/DshSessionAdminApiTest.kt` | 8 项协议/端点/错误/Cookie/transport 适配回归 |
| `app/src/test/kotlin/com/tanuki/DshSessionManagerControllerTest.kt` | 18 项状态机/过滤/未读/写操作/安全模式/权限确认/loading 恢复/端点切换回归 |
| `docs/handoffs/20261110-1226-native-parity-non-chat.md` | 本记录；普通 PR 唯一文档改动 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin -x buildNativeRuntime` | BUILD SUCCESSFUL | JDK17 项目缓存、macOS ARM64；不代表设备 |
| `./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime` | BUILD SUCCESSFUL，74 个结果文件、626 tests / 0 failures / 0 errors / 0 skipped | 合入 41/40/44/48 代码后；无设备/仪器 |
| `./gradlew :app:lintModernDebug :app:assembleModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL，0 errors / 97 warnings / 1 hint；打包成功 | 未安装设备、未签名发布 |
| PR #38 merge head `cf0fb76` Host / Android CI | 均 pass | 最终合并 head；设备/仪器未跑 |
| `python3 scripts/check-native-parity.py` | PASS：166 行、14 域 | 审计矩阵由属主维护，44 不写矩阵 |
| 一次 `--rerun-tasks` 全量组合 | `NpmAdaptiveWorkerPoolTest` 并发 flake 1 次；单独重跑与完整重跑均通过 | 未改该测试 |

- 哪些是前一轮结果，哪些是本次新验证：626 JVM、lint、assemble、P2 回归均为合并前本轮新跑；535/556 等为历史结果，未冒充 0.1.11 候选复跑。
- 未测试、超时、失败和暂未复现的情况：真实 Android/PRoot DSH、真实 `/connect` Cookie 生命周期、`commands/execute` 的 `/permission` 实际结果、`workspace/archiveSession` 运行中停止语义、设备 UI、远程 SSH、Android 仪器。

## 未完成与暂停项

- 未完成：真实设备/PRoot 验收；工作区全局搜索/diff；插件列表；斜杠命令执行；应用内语言/主题；#40 合并后 transport 适配器切换。
- 外部依赖：PR #40 `DshTransport` 已合并（`1f7222d`）；临时 `DshHttpAdminTransport` 仍在，替换为 `DshAdminTransportAdapter` 属后续清理；归档列表恢复依赖 workspace/follow WebSocket。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收按原状态保留。

## 下一步

1. 发布集成应用本记录。
2. #40 合并后把临时 `DshHttpAdminTransport` 替换为 `DshAdminTransportAdapter(transport::call, transport::reset)`，不重写会话管理逻辑。
3. 设备 QA 授权后验证真实 PRoot DSH 的 `/connect`、会话管理、归档/权限与页面布局。
4. 后续切片：工作区全局搜索/diff、插件列表与启用、斜杠命令执行、应用内主题/语言、归档列表恢复（需 workspace/follow WebSocket）。

## 约束与风险

- 数据/设备/权限边界：不读真实凭据；API Cookie 只在内存；不写 `userdata/dsh`；不删会话/项目；不合并/发布；不关闭审批/TLS/校验；不自动切 `danger-full-access`。
- 关键决策及理由：42 独占 `DshNativeApi.kt`/`DshNativeController.kt`/`DshNativePage.kt`/`MainActivity.kt`，本切片新增独立 API/Controller/Page/Activity，避免同文件并发写。完全权限需界面二次确认与控制器显式参数。
- MainActivity 归 42 独占：本 PR 只保留已放行的两行；若 42 重写该处，需要保留的 hook 是 `TanukiUiCallbacks.openSessionManager: () -> Unit`（设置页入口）与 Manifest 中 `DshSessionManagerActivity` 声明。
- transport：`DshHttpAdminTransport` 是临时实现；切换前不得出现两套不同的 origin/Cookie 规则，也不复制认证/Cookie/URL。`DshAdminTransport` 不使用 `live`。
- 已知风险：独立 Activity 与原生聊天页暂时是两个入口；官方 `session/search` 可能返回已归档会话，打开时会提示目标不在当前列表；远程 DSH 是否为安全模式无法从本地状态得知，写入失败由官方错误提示。
- 失败尝试与避免重复踩坑的结论：Android JVM 单测不能使用 JDK `com.sun.net.httpserver`，使用原始 `ServerSocket`；含中文请求体必须按 ISO-8859-1 逐字节读取，否则 `Content-Length` 与字符数错位导致超时。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本切片不生成发布 APK、不更新 `artifacts/releases/`；`assembleModernDebug` 仅用于验证，APK 在 `app/build/outputs/`，不作为交付包。源码 ZIP 由 `package-source.py` 生成，属工作材料，不提交。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装设备、未覆盖安装、未签名发布、未创建 tag/Release。
- 提交/推送前清理的过期记录：本轮未删除历史交接；共享状态与印章按新机制由集成维护方统一刷新，本 PR 不修改。
- 仍需保留的验证/未完成记录：本记录；共享状态、测试汇总与印章由集成维护方统一刷新。
