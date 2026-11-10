# 交接：20261110-1737-composer-input-polish

- Agent/会话标识：android_agent-52
- 时间：2026-11-10 17:37 CST
- 状态：组件实现与 JVM 回归完成；页面接线、设备验证、独立审查待后续
- 前一记录：[0.1.11 发布准备与交接集成](20261110-1636-release-0.1.11.md)

> 每任Agent创建唯一记录，不覆盖前任。开始填写目标/范围；结束或中断前补齐证据与可执行下一步。不要包含真实Key、Cookie、token、会话/备份或私有原始日志。

## 用户目标与范围

- 用户本次实际要求：把原生输入栏与附件体验做顺，主改 `DshComposer*`（Bar/Controller/DraftStore/AttachmentReader），不改 `DshNativePage`/`MainActivity` 接线（归集成方）。
- 本次包含：多行自适应与键盘避让、Ctrl/⌘+Enter 发送、发送后焦点、失败重发；按会话草稿与发送快照清理；图片/文件/粘贴/拖入、中文限制提示、上传进度与取消；@/斜杠候选键盘导航与插入位置；停止生成入口与停止/失败状态；JVM 回归。
- 本次不包含：`DshNativePage.kt`/`MainActivity.kt` 接线、页面级停止按钮合并、真实设备/模拟器验证、合并与发布。
- 用户改变或暂停的要求（需保留）：不合并、不发布；文档/注释/提交不出现参考实现名称；Git author/committer 必须为 `leezzou <115924933+leezzou@users.noreply.github.com>`；PR 开好后按授权处理。

## 当前状态

- 开始基线：`origin/main=4f2a3717d6d795fd6b94cdb695878d8119f818c1`；分支 `ao/composer-input/root`；无本地改动。
- 已实现：
  - 输入：`TextField` 1–6 行自适应；组件根节点 `imePadding()`；`Ctrl/⌘+Enter` 发送；发送/重发后 `FocusRequester` 把焦点还给输入框；失败保留内容并显示“重发”。
  - 草稿：沿用按会话 `SharedPreferencesDshDraftStore`；发送时快照文本与附件 id，成功后只清快照，等待期间新编辑保留；跨会话不携带附件。
  - 附件：图片/文件选择、粘贴图片、拖入文件；中文类型/大小/数量提示；上传卡片显示不定进度和“取消”，取消立即移除并阻止晚到响应回写；读取/拖入期间显示“正在读取附件…”。
  - @/斜杠：候选上下键循环、Enter/Tab 插入、Esc 关闭；斜杠候选按名称/描述/参数提示搜索排序；插入只替换当前查询并保留后续参数/换行；查询变化清空过期候选。
  - 停止：`session/cancel` 客户端与参数；`generationRunning`/`stopping`/`stopOutcome`；成功显示“已停止”，失败显示“停止失败”并保留运行状态；发送成功后自动置运行中。
- 已验证：定向 52 tests；完整 JVM 805 tests / 90 文件 / 0 failures / 0 errors / 0 skipped；`lintModernDebug` 0 errors（最终以复跑为准）。
- 待验证：真实设备/模拟器的软键盘避让、输入法组合、焦点、图片/文件选择器、粘贴/拖入、上传取消、候选键盘导航；页面接线后的停止入口与运行状态同步；真实 PRoot DSH；assemble、Android 仪器、真机。
- 当前目标包名、分支/无Git状态、是否有运行中任务：`com.tanuki.modern`；分支 `ao/composer-input/root`；无设备任务。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshComposerProtocol.kt` | 新增 `session/cancel` 参数、命令名校验、@/斜杠插入纯函数、命令搜索排序、候选循环移动；供控制器/UI/测试复用 |
| `app/src/main/kotlin/com/tanuki/web/DshComposerClient.kt` | 新增 `cancel(sessionId)`，只调用官方 `session/cancel` 并校验 `accepted` |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerController.kt` | 停止状态机与失败/成功结果；发送失败标记可重发；上传取消与晚到结果丢弃；上传串行锁；查询变化清空过期候选；命令搜索 |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerBar.kt` | 键盘避让、焦点回归、候选键盘导航与高亮、停止入口/状态、不定进度、读取中提示、取消上传按钮；@ 插入避免双空格 |
| `app/src/test/kotlin/com/tanuki/DshComposerProtocolTest.kt` | 新增取消参数、插入位置、命令搜索/候选循环 6 项 |
| `app/src/test/kotlin/com/tanuki/DshComposerClientTest.kt` | 新增取消请求成功/拒绝 1 项 |
| `app/src/test/kotlin/com/tanuki/DshComposerControllerTest.kt` | 新增失败重发、跨会话草稿/附件、取消上传、停止成功/失败、命令搜索 6 项 |
| `docs/INTERFACES.md` | 1.9.1 增补 `session/cancel`、停止状态、键盘导航、上传取消与页面接线边界 |
| `docs/handoffs/20261110-1737-composer-input-polish.md` | 本记录 |

明确未改：`DshNativePage.kt`、`MainActivity.kt`、`DshNativeController.kt`、`DshNativeApi.kt`、`DshTransport.kt`、`DshComposerDraftStore.kt`、`DshAttachmentReader.kt`、共享状态 `AGENT-STATE`/`NATIVE-UI`/`TEST-REPORT`/`CURRENT.json`。

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:testModernDebugUnitTest -x buildNativeRuntime --tests 'com.tanuki.DshComposerProtocolTest' --tests 'com.tanuki.DshComposerClientTest' --tests 'com.tanuki.DshComposerControllerTest'` | BUILD SUCCESSFUL；**52 tests / 0 failures / 0 errors / 0 skipped**（23 + 9 + 20） | 本 worktree；未跑 `buildNativeRuntime` |
| `... :app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL；**805 tests / 90 结果文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 102 warnings / 1 hint**；`DshComposer*` 无新增 lint 项 | 含 main 既有回归；lint 其余告警为既有文件 |
| `git diff --check` | 无输出 | 无空白错误 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1737-composer-input-polish.md` | Handoff PR check passed | 普通 PR 模式；`AGENT-STATE`/`CURRENT.json` 未修改 |
| `python3 scripts/check-publication.py --git` | 待运行/运行中 | 提交前按结果处理 |

- 哪些是前一轮结果，哪些是本次新验证：52 项目标回归、805 项完整 JVM、lint 均为本分支新跑；805 中既有回归不是本切片新增。
- 未测试、超时、失败和暂未复现的情况：真实设备 UI/输入法/选择器/粘贴/拖入/跨应用临时权限/上传取消/停止、真实 PRoot DSH、assemble、Android 仪器、真机均未执行；`DshComposerBar` 的 Compose 焦点/键盘事件没有 JVM 自动化覆盖；`DshAttachmentReader` 的 Android URI/剪贴板路径没有仪器测试。

## 未完成与暂停项

- 页面接线：`DshNativePage`/`MainActivity` 归集成方；需要把 `DshNativeState.running` 同步到 `nativeComposer.setGenerationRunning(running)`，并在需要时把页面停止按钮与 `nativeComposer.requestStop()` 统一，避免两处状态不一致。
- 未验证：设备/模拟器、真实 DSH、上传取消的服务端残留（官方无上传取消端点，本地不保留残缺文件）、真实键盘导航。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收按项目既有暂停项保留；本次未触碰。

## 下一步

1. 集成方接线 `setGenerationRunning(running)`，在设备/模拟器验证输入法避让、焦点、候选键盘、图片/文件/粘贴/拖入、上传取消与停止。
2. 由独立 Codex High 审查本 PR 当前 SHA；按审查意见补修。
3. 通过后由有合并授权的人合并；本 worker 不合并、不发布。

## 约束与风险

- 数据/设备/权限边界：只读用户选择的 `content://` 或受限 `data:image`；不读取 `file://`、App 私有文件或外部明文路径；不写真实 token/Cookie 到日志/状态/偏好；未启动设备、未安装、未跑仪器。
- 关键决策及理由：停止入口放在组件内，避免改页面接线；发送成功自动置 `generationRunning`，页面 running 状态负责复位；上传取消只保证本地卡片和状态不回流，官方无上传取消方法，不宣称服务端已删除；进度使用不定进度，不伪造百分比。
- 失败尝试与避免重复踩坑的结论：Material3 当前 `LinearProgressIndicator` 无 `strokeWidth` 参数；命令名校验在 `DshComposerProtocol` 内，策略层需走公开校验；取消上传后重启 worker 必须有串行锁，否则旧请求晚到可能重复上传。
- 风险：`generationRunning` 未接线时发送成功后停止入口不会自动消失；真实输入法/焦点行为需设备验证；取消上传后服务端可能仍完成已发出的 unary 请求；`DshComposerBar` 依赖页面提供的会话与生命周期。

## 产物与校验

- 新/旧APK、源码ZIP及校验文件：本轮未生成 APK、源码 ZIP 或发布附件；只改源码/测试/文档。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布、未合并。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：普通 PR 只新增本记录，不改共享状态。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码ZIP；普通 PR 运行 `python3 scripts/check-handoff.py --pr --record docs/handoffs/<本次记录>.md`，不修改 `AGENT-STATE.md`/`CURRENT.json`；提交前运行 `git diff --check` 与 `check-publication.py --git`。
