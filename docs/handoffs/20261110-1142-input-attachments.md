# 交接：20261110-1142-input-attachments

- Agent/会话标识：android_agent-46
- 时间：2026-11-10 11:42 CST
- 状态：组件 PR [#43](https://github.com/leezzou/tanuki/pull/43) 已创建（head `0b747ab`）；已合入 `origin/main=7593805a`，PR 交接检查通过，合并提交待推送
- 前一记录：[0.1.10 Pre-release 发布准备](20261109-2310-release-0.1.10.md)

> 每任Agent创建唯一记录，不覆盖前任。开始填写目标/范围；结束或中断前补齐证据与可执行下一步，并更新 `docs/AGENT-STATE.md`。只读Agent交回同格式文本，由主Agent保存。不要包含真实Key、Cookie、token、会话/备份或私有原始日志。

## 用户目标与范围

- 用户本次实际要求：把原生输入/附件组件与 5 项评审修复以独立组件 PR 交付；从 `ao/input-attachments/root`（`59bbc77`）取内容，但新分支只含 6 个组件、3 个测试、`docs/INTERFACES.md` 临时抽象说明和本 handoff；不含 `DshNativePage.kt`/`MainActivity.kt`，不合 main。
- 本次包含：`DshComposerController`/`Bar`/`AttachmentReader`/`Protocol`/`Client`/`DraftStore`；3 个 JVM/协议测试；`INTERFACES.md` 1.9.1 组件契约；本 handoff。
- 本次不包含：页面接线（42 负责，见 #40）、共享状态 `AGENT-STATE`/`NATIVE-UI`/`TEST-REPORT`/`CURRENT.json`、聊天流式/消息渲染、审批/提问、停止/取消、发布/合并；不自建 HTTP/WS。
- 用户改变或暂停的要求（需保留）：不合并、不发布；文档/注释/提交不出现参考实现名称；Git author/committer 必须为 `leezzou <115924933+leezzou@users.noreply.github.com>`；PR 开好后不再 push 新提交。

## 当前状态

- 已实现：原生输入栏组件（多行、`Ctrl/⌘+Enter` 发送、按会话草稿）、@ 文件/会话补全、斜杠命令目录/补全/执行、图片/文件选择与预览、普通文件上传换 receipt、图片 base64 随 prompt、粘贴图片、拖入文件、大小/类型限制中文错误；5 项评审修复。
- 已验证：`compileModernDebugKotlin` BUILD SUCCESSFUL；目标回归 38 项全过；完整 JVM 638 tests / 0 failures / 0 errors / 0 skipped（75 个结果文件，含 main 既有回归）；`lintModernDebug` 0 errors / 97 warnings / 1 hint，新增文件无 lint 告警。
- 待验证：42 接线后的真实 Android 设备/模拟器输入栏布局、软/硬件键盘、图片/文件选择、缩略图/大图预览、粘贴/拖入、跨应用临时权限、上传失败重试；真实 PRoot DSH；assemble、Android 仪器、真机。
- 当前目标包名、分支/无Git状态、是否有运行中任务：`com.tanuki`（modern flavor 为 `com.tanuki.modern`）；分支 `ao/input-attachments/components`，已合入 `origin/main=7593805a8a2f685e056c9fde7487b504d0a08552`；PR #43 已创建；无设备/模拟器任务。
- 关键事实与可定位源码/公共文档：官方 `@deepseek-ai/dsh@0.2.1-rc.5` 的 `session/prompt`、`fileUploads/upload`、`commands/list|execute`、`fileReferences/list`、`sessionReferenceResolver/candidates` 参数结构；实现见 6 个组件文件；契约见 `docs/INTERFACES.md` 1.9.1。

### Codex Review 修复（5 项）

- P1 发送成功误清新草稿：发送前快照 `submittedText` 与附件 id；成功时仅移除本次提交的文本/附件，发送期间的新文本、新附件保留并回写草稿；延迟响应回归 `sendKeepsEditsMadeWhileWaitingForResponse`。
- P1 异步附件串会话：新增 `DshComposerSessionToken(sessionId, generation)`；选择器打开、粘贴、拖入读取前捕获，返回时 `isCurrentSession` 校验；`addImages/addFiles` 也接受 token 做二次拒绝；回归 `staleAttachmentTokenIsRejectedAfterSessionSwitch`。
- P2 批量读取预算：新增 `DshAttachmentReadBudget`，`readUris`/剪贴板/拖入在读取每个 URI 前按剩余数量、剩余总量和单文件上限分配；不再先把整批字节读进内存；回归 `batchBudgetStopsBeforeOverAllocating`。
- P2 拖入开始判定：`shouldStartDragAndDrop` 改用 `event.mimeTypes()`，不再依赖 ACTION_DROP 才提供的 `clipData`；纯策略 `DshComposerDragPolicy.accepts` 有回归。
- P2 跨应用临时权限：`onDrop` 调 `Activity.requestDragAndDropPermissions`，权限保留到异步读取结束并在 `finally` release；真实外部 provider 读取仍需设备验证。

### transport 收敛

- 当前分支已删除早期 head 的临时 `HttpDshComposerRemote`；它曾使用 `POST /api/<method>`、connect 5s/read 30s、401 单次重试、2 MiB 响应上限和 `DshApiException(code)` 错误映射。该实现不再存在，仅保留历史说明。
- 现在只依赖 `DshComposerRemote`：`suspend fun call(method: String, args: JSONObject): Any?`（返回官方 `result.value`，业务错误抛 `DshApiException`，认证/网络 IOException）与 `fun reset()`。
- `DshComposerRemote` 是**临时抽象**：只覆盖输入/附件需要的 unary `call`/`reset`，不代表第二套传输。42 的 `DshTransport` / `DshNativeComposerRemote(transport)` 定稿后，消费方应改为直接依赖该适配器并删除/收敛这个临时接口。
- 输入栏不使用 `live`/`connection`，不读 token/Cookie、不拼 URL、不自建 HTTP/WS。

### 真实可用性

- 发送、附件选择/上传、@ 引用、斜杠命令、草稿保留：控制器/UI/协议已实现并有 JVM 覆盖；**本组件 PR 不挂载 `DshNativePage`**，不能宣称端到端在原生页可用。
- 停止/取消：**未实现**；输入栏没有“停止生成立即中断”或“附件上传取消”，`sendJob`/`uploadJob` 只在 `setSession`/`close` 取消。
- 图片/文件选择、缩略图/大图预览、粘贴、拖入、临时权限：Android 代码已实现，未在设备验证。
- #40 负责页面接线；端到端可用性以 #40 接线与设备 QA 为准。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshComposerProtocol.kt` | 官方 prompt/upload/commands/文件引用/会话引用参数构造与返回解析；图片嗅探/尺寸、@/斜杠语法、附件限制、批量预算、拖入 MIME 策略 |
| `app/src/main/kotlin/com/tanuki/web/DshComposerClient.kt` | 输入/附件 Remote 客户端；只依赖 `DshComposerRemote`（`call` + `reset`），不自建 HTTP/WS；base64 在后台线程 |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerController.kt` | 按会话草稿、补全候选、附件校验/串行上传、命令/提示词发送、失败保留草稿；发送快照与会话代次 token |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerBar.kt` | 多行输入、@/斜杠菜单、附件卡片/缩略图/大图预览、图片/文件选择、粘贴、Compose 拖入、发送快捷键；临时权限与 token 绑定 |
| `app/src/main/kotlin/com/tanuki/ui/DshAttachmentReader.kt` | 文档 URI、剪贴板图片、拖入 ClipData 读取；读取前预算、大小上限与中文错误 |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerDraftStore.kt` | 应用私有 `dsh-composer` 按会话文本草稿存储与内存测试实现 |
| `app/src/test/kotlin/com/tanuki/DshComposerProtocolTest.kt` | 协议/策略回归 17 项 |
| `app/src/test/kotlin/com/tanuki/DshComposerClientTest.kt` | 客户端协议回归 8 项 |
| `app/src/test/kotlin/com/tanuki/DshComposerControllerTest.kt` | 控制器状态机回归 13 项 |
| `docs/INTERFACES.md` | 新增 1.9.1 组件契约、方法参数、限制、临时 transport 抽象与停止/取消边界 |
| `docs/handoffs/20261110-1142-input-attachments.md` | 本记录 |

明确未改：`DshNativeApi.kt`、`DshNativeController.kt`、`DshNativePage.kt`、`MainActivity.kt`（42 独占）；`TanukiUi.kt`/`DshUiMode.kt`（44）；`DshChatProtocol.kt`（42）；共享状态 `AGENT-STATE`/`NATIVE-UI`/`TEST-REPORT`/`CURRENT.json` 不在本组件 PR。

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin -x buildNativeRuntime` | BUILD SUCCESSFUL；仅既有弃用/平台告警 | 本 worktree；未跑原生 `buildNativeRuntime` |
| `./gradlew ... :app:testModernDebugUnitTest -x buildNativeRuntime --tests 'com.tanuki.DshComposerProtocolTest' --tests 'com.tanuki.DshComposerClientTest' --tests 'com.tanuki.DshComposerControllerTest'` | BUILD SUCCESSFUL；**38 tests / 0 failures / 0 errors / 0 skipped** | 17 + 8 + 13；不含 transport 认证/HTTP |
| `./gradlew ... :app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL；75 个结果文件、**638 tests / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 97 warnings / 1 hint** | 含 main 既有回归；新增文件无 lint 告警 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1142-input-attachments.md` | Handoff PR check passed | 普通 PR 模式；`AGENT-STATE`/`CURRENT.json` 与 main 完全一致，未修改 |
| `git diff --check` | 无输出 | 无空白错误 |

- 哪些是前一轮结果，哪些是本次新验证：638/0/0/0、lint 0 errors/97 warnings/1 hint、38 项目标回归均为本组件分支新跑；main 既有回归不是本切片新增。
- 未测试、超时、失败和暂未复现的情况：真实设备 UI/键盘/选择器/粘贴/拖入/跨应用临时权限/上传失败重试、真实 PRoot DSH、assemble、Android 仪器、真机均未执行；`DshComposerBar` 的 Compose 交互没有 JVM 自动化覆盖；`DshAttachmentReader` 的 Android URI/剪贴板路径没有仪器测试；transport 认证/HTTP 由 42 统一实现，本分支未测。

## 未完成与暂停项

- 未完成：创建组件 PR；#40 接线；独立 Codex High Review；适用 Android QA。
- 外部阻塞：无；本组件 PR 作为 #40 的依赖，不合并 main、不重开 #39。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收按项目既有暂停项保留；本次未触碰。

## 下一步

1. 组件 PR #43 已创建；本次只推送合入 `main=7593805a` 与交接更新，之后不再 push 新提交。
2. 由编排器先合 #42（交接机制），再合本组件 PR，最后 #40 rebase 只保留接线。
3. 若 #40 评审指出 composer 问题，由编排器转交后通过 42 分支或另开小 PR 修复。

## 约束与风险

- 数据/设备/权限边界：只读用户选择的 `content://` 或受限 `data:image`；不读取 `file://`、App 私有文件或外部明文路径；不写真实 token/Cookie 到日志/状态/偏好；未启动设备、未安装、未跑仪器。
- 关键决策及理由：按编排器要求做组件-only PR，避免共享状态冲突；不修改 42 独占文件；不自建 HTTP/WS，保留 `DshComposerRemote` 临时抽象；附件不跨会话保留，只持久化文本草稿。
- 失败尝试与避免重复踩坑的结论：官方图片上限可能小于客户端 64 KiB 下限，测试按 effective 值断言；跨行 `@` 在第二行行首仍识别；`session/prompt` 的 connect URL 必须保留 token，安全要求是 token 不进请求体/错误/日志；斜杠命令目录未加载时不能把 `/name` 当普通提示词发送。
- 风险：真实 DSH 缺少 `commands/list`/`fileReferences/list`/`sessionReferenceResolver` 时补全为空但普通发送可用；真实服务端附件上限/错误码可能与默认不同；图片 base64 与 50 MiB 总量在低内存设备仍有 OOM 风险，已用 20/30/50 MiB 与串行上传收敛。

## 产物与校验

- 新/旧APK、源码ZIP及校验文件：本轮未生成 APK、源码 ZIP 或发布附件；只改源码/测试/文档。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布、未合并。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本组件 PR 只新增本 handoff；不包含 `AGENT-STATE`/`NATIVE-UI`/`TEST-REPORT`/`CURRENT.json`，共享状态由集成阶段统一刷新。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码ZIP；普通 PR 运行 `python3 scripts/check-handoff.py --pr --record docs/handoffs/<本次记录>.md`，不修改 `AGENT-STATE.md`/`CURRENT.json`；提交前运行 `git diff --check` 与 `check-publication.py --git`，如需源码包再运行 `package-source.py`。
