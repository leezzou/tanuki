# 交接：20261110-1142-input-attachments

- Agent/会话标识：android_agent-46
- 时间：2026-11-10 11:42 CST
- 状态：部分完成（新文件与本地验证完成；42 接线待办；PR #39 已认领，边界修正已推送；已合并最新 `main=533eff4` 解决共享文档冲突，合并提交随本提交推送）
- 前一记录：[0.1.10 Pre-release 发布准备](20261109-2310-release-0.1.10.md)

> 每任Agent创建唯一记录，不覆盖前任。开始填写目标/范围；结束或中断前补齐证据与可执行下一步，并更新 `docs/AGENT-STATE.md`。只读Agent交回同格式文本，由主Agent保存。不要包含真实Key、Cookie、token、会话/备份或私有原始日志。

## 用户目标与范围

- 用户本次实际要求：Tanuki 原生界面功能对齐中的「输入与附件」切片；实现多行输入、草稿保留、发送快捷键、@ 会话/文件引用、斜杠命令列表与补全；图片/文件选择、预览、上传或引用、大小/类型限制与中文错误；粘贴图片、拖入文件（平台支持时）；与通知点击、远程 origin 兼容；不重写聊天流式/消息渲染；补 JVM/协议回归。
- 本次包含：可复用的输入/附件新文件、按会话文本草稿、@/斜杠补全、图片/文件附件、上传换 receipt、图片 base64 随 prompt、粘贴图片、Compose 拖入、附件限制中文错误、JVM/协议回归、INTERFACES/NATIVE-UI/TEST-REPORT/交接更新、42 接线 hook 清单。
- 本次不包含：聊天流式跟随、消息渲染重写、审批/提问交互、会话管理、模型选择、历史附件展示、取消运行、发布/合并；不修改官方 DSH npm 包；不修改 42 独占的 `DshNativeApi.kt`/`DshNativeController.kt`/`DshNativePage.kt`/`MainActivity.kt`；不自建 HTTP/WS transport。
- 用户改变或暂停的要求（需保留）：不合并、不发布；文档/注释/提交不出现参考实现名称；Git author/committer 必须为 `leezzou <115924933+leezzou@users.noreply.github.com>`。

## 当前状态

- 已实现：原生输入栏组件（多行、`Ctrl/⌘+Enter` 发送、按会话草稿）、@ 文件/会话补全、斜杠命令目录/补全/执行、图片/文件选择与预览、普通文件上传换 receipt、图片 base64 随 prompt、粘贴图片、拖入文件、大小/类型限制中文错误。
- 已验证：`compileModernDebugKotlin` BUILD SUCCESSFUL；输入/附件目标回归 34 项全过；完整 JVM 592 tests / 0 failures / 0 errors / 0 skipped；`lintModernDebug` 0 errors / 95 warnings / 1 hint，新增文件无 lint 告警。
- 待验证：42 接线后的真实 Android 设备/模拟器输入栏布局、软/硬件键盘、图片/文件选择、缩略图/大图预览、粘贴/拖入、上传失败重试；真实 PRoot DSH `/connect`/`session/prompt`/`fileUploads/upload`/`commands`；assemble、Android 仪器、真机；独立 Review、CI 与合并。
- 当前目标包名、分支/无Git状态、是否有运行中任务：`com.tanuki`（modern flavor 为 `com.tanuki.modern`）；分支 `ao/input-attachments/root`，基线 `origin/main=0555c3c0eaeeed181269b5e68733ccfdd48d9e75`；PR [#39](https://github.com/leezzou/tanuki/pull/39) 已认领；无设备/模拟器任务。
- 关键事实与可定位源码/公共文档：官方 `@deepseek-ai/dsh@0.2.1-rc.5` 包中的 `session/prompt`、`fileUploads/upload`、`commands/list|execute`、`fileReferences/list`、`sessionReferenceResolver/candidates` 参数结构；实现见 `app/src/main/kotlin/com/tanuki/web/DshComposerProtocol.kt`、`DshComposerClient.kt`、`ui/DshComposerController.kt`、`ui/DshComposerBar.kt`、`ui/DshAttachmentReader.kt`、`ui/DshComposerDraftStore.kt`；契约见 `docs/INTERFACES.md` 1.7.1，行为见 `docs/NATIVE-UI.md` 4.1。最新 `main=533eff4` 已合入本分支，冲突仅在 `AGENT-STATE`/`TEST-REPORT`/`CURRENT.json`，两边记录均已保留。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshComposerProtocol.kt` | 新增官方 prompt/upload/commands/文件引用/会话引用参数构造与返回解析；图片类型嗅探、固有尺寸、@/斜杠语法、附件限制与中文错误纯策略 |
| `app/src/main/kotlin/com/tanuki/web/DshComposerClient.kt` | 新增输入/附件 Remote 客户端；只依赖 `DshComposerRemote` 小接口（`suspend call(method,args)` + `reset()`），不自建 HTTP/WS；base64 在后台线程 |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerController.kt` | 新增按会话草稿、补全候选、附件校验/串行上传、命令/提示词发送、失败保留草稿状态机；命令目录未加载时拒绝把斜杠文本误发为普通提示词 |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerBar.kt` | 新增多行输入、@/斜杠菜单、附件卡片/缩略图/大图预览、图片/文件选择、粘贴、Compose 拖入、发送快捷键 |
| `app/src/main/kotlin/com/tanuki/ui/DshAttachmentReader.kt` | 新增文档 URI、剪贴板图片（content URI/data URI）、拖入 ClipData 读取；大小上限与中文错误；图片以字节嗅探为准 |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerDraftStore.kt` | 新增应用私有 `dsh-composer` 按会话文本草稿存储与内存测试实现 |
| `app/src/test/kotlin/com/tanuki/DshComposerProtocolTest.kt` | 新增协议/策略回归 15 项 |
| `app/src/test/kotlin/com/tanuki/DshComposerClientTest.kt` | 新增客户端协议回归 8 项 |
| `app/src/test/kotlin/com/tanuki/DshComposerControllerTest.kt` | 新增控制器状态机回归 11 项 |
| `docs/INTERFACES.md` | 新增 1.7.1 原生输入与附件契约、方法参数、限制与安全边界 |
| `docs/NATIVE-UI.md` | 更新端点表、文件表、输入/附件行为、安全边界、验证与 42 接线依赖 |
| `docs/TEST-REPORT.md` | 新增本轮验证记录与未验证范围 |
| `docs/AGENT-STATE.md` | 增加本轮交接链接与状态段 |

明确未改：`DshNativeApi.kt`、`DshNativeController.kt`、`DshNativePage.kt`、`MainActivity.kt`（42 独占）；`TanukiUi.kt`/`DshUiMode.kt`（44）；`DshChatProtocol.kt`（42）；RuntimeManager/RemoteHost/设置/技能不在本批。

## 需要 42 的接线 hook

1. **挂载位置**：在 `DshNativePage` 根 `Column` 底部、消息 `LazyColumn` 之后，仅当 `state.selectedId != null` 时挂载：
   ```kotlin
   if (composer != null && state.selectedId != null) {
       DshComposerBar(composer, Modifier.fillMaxWidth())
   }
   ```
2. **构造与签名**：
   ```kotlin
   val composer = remember {
       DshComposerController(
           client = DshComposerClient(transport), // transport 由 42 提供
           drafts = SharedPreferencesDshDraftStore(context),
           scope = rememberCoroutineScope(),
           onSent = { sessionId -> controller.select(sessionId, force = true) }
       )
   }
   ```
   - `DshComposerClient(remote: DshComposerRemote)`
   - `DshComposerRemote`：`suspend fun call(method: String, args: JSONObject): Any?`、`fun reset()`
   - `DshComposerController(client, drafts, scope, onSent, timeZoneProvider)`；`DshComposerBar(controller, modifier)`
3. **生命周期**：`remember` 创建一次；`DisposableEffect(composer) { onDispose { composer.close() } }`；`LaunchedEffect(state.selectedId) { composer.setSession(state.selectedId) }`。通知点击仍由现有 `openSession()` 选中会话，`selectedId` 变化会触发草稿切换。
4. **发送成功刷新**：`onSent` 回调调用 `DshNativeController.select(sessionId, force = true)`；不接管流式/消息渲染。
5. **transport 依赖点**：42 的 transport 适配器实现 `DshComposerRemote.call`，返回官方 `result.value`；业务错误抛 `DshApiException(code)`；`reset()` 在服务停止/地址变化时清 Cookie。输入/附件客户端不读取 token/Cookie、不拼 URL、不自建 HTTP/WS。
6. **Context 要求**：`DshComposerBar` 使用 `rememberLauncherForActivityResult`、剪贴板与 `SharedPreferences`，必须挂在 Activity 的 Compose 组合内；`SharedPreferencesDshDraftStore(context)` 用应用上下文。

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin -x buildNativeRuntime` | BUILD SUCCESSFUL；仅既有弃用/平台告警 | 本 worktree `android_agent-46`；未跑原生 `buildNativeRuntime` |
| `./gradlew ... :app:testModernDebugUnitTest -x buildNativeRuntime --tests 'com.tanuki.DshComposerProtocolTest' --tests 'com.tanuki.DshComposerClientTest' --tests 'com.tanuki.DshComposerControllerTest'` | BUILD SUCCESSFUL；**34 tests / 0 failures / 0 errors / 0 skipped** | 15 + 8 + 11；不含 transport 认证/HTTP |
| `./gradlew ... :app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL；70 个结果文件、**592 tests / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 95 warnings / 1 hint** | 合并 main 后：0.1.10 基线 535 + 文件查看器 23 + 本切片 34；新增文件无 lint 告警 |
| `git diff --check` | 无输出 | 无空白错误 |
| PR #39 新 head `4d03319` CI | Host 29s、Android build+JVM+lint 6m24s，均 pass | 以 PR 页面当前 head 为准 |

- 哪些是前一轮结果，哪些是本次新验证：592/0/0/0、lint 0 errors/95 warnings/1 hint、34 项目标回归均为合并 main 后本次新跑；535 是 0.1.10 基线记录，23 是文件查看器已合并到 main 的回归，34 是本切片新增；PR #39 旧 head CI 不能作为当前 head 依据。
- 未测试、超时、失败和暂未复现的情况：42 接线后的真实设备 UI/键盘/选择器/粘贴/拖入/上传失败重试、真实 PRoot DSH、assemble、Android 仪器、真机均未执行；`DshComposerBar` 的 Compose 交互没有 JVM 自动化覆盖；`DshAttachmentReader` 的 Android URI/剪贴板路径没有仪器测试；transport 认证/HTTP 由 42 统一实现，本分支未测。

## 未完成与暂停项

- 未完成：42 按上节 hook 接线；独立 Codex High Review；适用 Android QA。
- 外部阻塞：transport 最小接口由 42 定稿；在拿到前本分支只保留 `DshComposerRemote` 抽象，不阻塞其余新文件与回归。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收按项目既有暂停项保留；本次未触碰。

## 下一步

1. 边界修正已推送 `4d03319`，PR #39 正文已更新 hook 清单，新 head CI 已 pass；等待 42 接线与独立 Review。
2. 把本交接“需要 42 的接线 hook”发给 android_agent-42/编排器；由 42 在其独占文件中接线，不在本分支自行合并对方改动。
3. 42 接线后复跑 `compileModernDebugKotlin`、目标/完整 JVM、lint；设备 QA 由编排器另派独立 QA 会话，使用既有 ARM64 AVD，不把宿主/JVM 结果当 Android 验收。

## 约束与风险

- 数据/设备/权限边界：只读用户选择的 `content://` 或受限 `data:image`；不读取 `file://`、App 私有文件或外部明文路径；不写真实 token/Cookie 到日志/状态/偏好；未启动设备、未安装、未跑仪器。
- 关键决策及理由：按编排器边界，不修改 42 独占的四个文件；输入/附件协议、状态机、UI 全部放新文件，42 只做小接线；不自建 HTTP/WS，保留 `DshComposerRemote` 两方法抽象，降低 transport 替换成本；文件先上传换 receipt、图片随 prompt 发送，遵循官方 0.2.1-rc.5 能力；附件不跨会话保留，只持久化文本草稿。
- 失败尝试与避免重复踩坑的结论：官方图片上限可能小于客户端 64 KiB 下限，客户端会把过小值抬到 64 KiB，测试需按 effective 值断言；跨行 `@` 在第二行行首仍应识别，只有查询跨行才不识别；`session/prompt` 的 `connect` URL 必须保留 token（实际请求地址），安全要求是 token 不进入请求体/错误/日志，不是从 connect URL 删除；斜杠命令目录未加载时不能把 `/name` 当普通提示词直接发送。
- 风险：真实 DSH 版本若缺少 `commands/list`/`fileReferences/list`/`sessionReferenceResolver`，对应补全为空但普通发送仍可用；真实服务端附件上限/错误码可能与客户端默认不同；图片 base64 与 50 MiB 附件总量在低内存设备上仍有 OOM 风险，已用 20/30/50 MiB 与串行上传收敛。

## 产物与校验

- 新/旧APK、源码ZIP及校验文件：本轮未生成 APK、源码 ZIP 或发布附件；只改源码/测试/文档。此前 `package-source.py` 生成的源码包为工作材料，未提交。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布、未合并。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：未删除历史交接；本轮新增唯一记录 `docs/handoffs/20261110-1142-input-attachments.md`，旧记录保留。删除 `DshComposerHttpTest.kt` 的理由是 transport 改由 42 统一提供，本分支不再保留 HTTP 栈测试。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码ZIP；不要把源码ZIP自身哈希写回归档内形成循环。提交前运行 `python3 scripts/check-handoff.py --record docs/handoffs/20261110-1142-input-attachments.md`、`python3 scripts/check-handoff.py`、`python3 scripts/check-publication.py --git`；如需源码包再运行 `python3 scripts/package-source.py`。
