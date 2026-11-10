# 交接：20261110-1737-composer-input-polish

- Agent/会话标识：android_agent-52
- 时间：2026-11-10 17:37 CST
- 状态：组件实现与 JVM 回归完成；已修 PR #48 两条 P2（停止与提交回执分离、等待官方 idle）并按 P0 改成胶囊输入条；待新 head CI、独立审查；页面接线、设备验证待后续
- 前一记录：[0.1.11 发布准备与交接集成](20261110-1636-release-0.1.11.md)

> 每任Agent创建唯一记录，不覆盖前任。开始填写目标/范围；结束或中断前补齐证据与可执行下一步。不要包含真实Key、Cookie、token、会话/备份或私有原始日志。

## 用户目标与范围

- 用户本次实际要求：把原生输入栏与附件体验做顺，主改 `DshComposer*`（Bar/Controller/DraftStore/AttachmentReader），不改 `DshNativePage`/`MainActivity` 接线（归集成方）。
- 本次包含：胶囊输入条（单行 48dp/圆角 28、1–6 行自适应、左右 36dp 圆键贴底、多行圆角 18）、发送/停止同键形变动画、多行自适应与键盘避让、Ctrl/⌘+Enter 发送、发送后焦点、失败重发；按会话草稿与发送快照清理；图片/文件/粘贴/拖入、中文限制提示、上传进度与取消；@/斜杠候选键盘导航与插入位置；停止生成入口与“请求中/已停止/失败”三段状态；JVM 回归。
- 本次不包含：`DshNativePage.kt`/`MainActivity.kt` 接线、页面级停止按钮合并、真实设备/模拟器验证、合并与发布。
- 用户改变或暂停的要求（需保留）：不合并、不发布；文档/注释/提交不出现参考实现名称；Git author/committer 必须为 `leezzou <115924933+leezzou@users.noreply.github.com>`；PR 开好后按授权处理。

## 当前状态

- 开始基线：`origin/main=4f2a3717d6d795fd6b94cdb695878d8119f818c1`；分支 `ao/composer-input/root`；无本地改动。
- 已实现：
  - 输入：`BasicTextField` 胶囊布局，单行 48dp/圆角 28、1–6 行随文本布局增高、多行圆角 18、左右 36dp 圆键贴底；只有图标做短促形变动画；组件根节点 `imePadding()`；`Ctrl/⌘+Enter` 发送；发送/重发后 `FocusRequester` 把焦点还给输入框；失败保留内容并显示“重发”。
  - 草稿：沿用按会话 `SharedPreferencesDshDraftStore`；发送时快照文本与附件 id，成功后只清快照，等待期间新编辑保留；跨会话不携带附件。
  - 附件：图片/文件选择、粘贴图片、拖入文件；中文类型/大小/数量提示；上传卡片显示不定进度和“取消”，取消立即移除并阻止晚到响应回写；读取/拖入期间显示“正在读取附件…”。
  - @/斜杠：候选上下键循环、Enter/Tab 插入、Esc 关闭；斜杠候选按名称/描述/参数提示搜索排序；插入只替换当前查询并保留后续参数/换行；查询变化清空过期候选。
  - 停止：`session/cancel` 客户端与参数；`generationRunning`/`stopping`/`stopOutcome`。accepted 只进入 `REQUESTED`，保留停止入口/运行状态；官方 `running=false` 经 `setGenerationRunning(false)` 才进入 `STOPPED`，失败进入 `FAILED`。停止不取消在途 prompt 回执、不强制清 `sending`。
- 已验证：定向 53 tests；完整 JVM 806 tests / 90 文件 / 0 failures / 0 errors / 0 skipped；`lintModernDebug` 0 errors / 102 warnings / 1 hint；交接检查与发布过滤通过。
- 待验证：真实设备/模拟器的软键盘避让、输入法组合、焦点、图片/文件选择器、粘贴/拖入、上传取消、候选键盘导航；页面接线后的停止入口与运行状态同步；真实 PRoot DSH；assemble、Android 仪器、真机。
- 当前目标包名、分支/无Git状态、是否有运行中任务：`com.tanuki.modern`；分支 `ao/composer-input/root`；无设备任务。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/web/DshComposerProtocol.kt` | 新增 `session/cancel` 参数、命令名校验、@/斜杠插入纯函数、命令搜索排序、候选循环移动；供控制器/UI/测试复用 |
| `app/src/main/kotlin/com/tanuki/web/DshComposerClient.kt` | 新增 `cancel(sessionId)`，只调用官方 `session/cancel` 并校验 `accepted` |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerController.kt` | 停止三段状态 `REQUESTED/STOPPED/FAILED`；停止不取消 prompt 回执、不清 `sending`；发送失败标记可重发；上传取消与晚到结果丢弃；上传串行锁；查询变化清空过期候选；命令搜索 |
| `app/src/main/kotlin/com/tanuki/ui/DshComposerBar.kt` | 胶囊输入条 48dp/1–6 行/圆角 28→18、左右 36dp 圆键、发送/停止同键形变；键盘避让、焦点回归、候选键盘导航与高亮、停止状态、不定进度、读取中提示、取消上传按钮；@ 插入避免双空格 |
| `app/src/test/kotlin/com/tanuki/DshComposerProtocolTest.kt` | 新增取消参数、插入位置、命令搜索/候选循环 6 项 |
| `app/src/test/kotlin/com/tanuki/DshComposerClientTest.kt` | 新增取消请求成功/拒绝 1 项 |
| `app/src/test/kotlin/com/tanuki/DshComposerControllerTest.kt` | 新增失败重发、跨会话草稿/附件、取消上传、停止请求/官方确认/失败、延迟 prompt 回执、命令搜索 7 项 |
| `docs/INTERFACES.md` | 1.9.1 增补 `session/cancel`、停止状态、键盘导航、上传取消与页面接线边界 |
| `docs/handoffs/20261110-1737-composer-input-polish.md` | 本记录 |

明确未改：`DshNativePage.kt`、`MainActivity.kt`、`DshNativeController.kt`、`DshNativeApi.kt`、`DshTransport.kt`、`DshComposerDraftStore.kt`、`DshAttachmentReader.kt`、共享状态 `AGENT-STATE`/`NATIVE-UI`/`TEST-REPORT`/`CURRENT.json`。

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:testModernDebugUnitTest -x buildNativeRuntime --tests 'com.tanuki.DshComposerProtocolTest' --tests 'com.tanuki.DshComposerClientTest' --tests 'com.tanuki.DshComposerControllerTest'` | BUILD SUCCESSFUL；**53 tests / 0 failures / 0 errors / 0 skipped**（23 + 9 + 21） | 新增“accepted 仍运行”和“延迟 prompt 回执时停止”回归 |
| `... :app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL；**806 tests / 90 结果文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 102 warnings / 1 hint**；`DshComposer*` 无新增 lint 项 | 一次运行出现既有无关注册池测试偶发失败，单测重跑与完整重跑均通过；非本切片改动 |
| `git diff --check` | 无输出 | 无空白错误 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1737-composer-input-polish.md` | Handoff PR check passed | 普通 PR 模式；`AGENT-STATE`/`CURRENT.json` 未修改 |
| `python3 scripts/check-publication.py --git` | Publication check passed；673 public files / 672 index entries / 0 findings | 未输出匹配值 |
| `gh pr create --base main --head ao/composer-input/input-polish` | PR [#48](https://github.com/leezzou/tanuki/pull/48) 已创建 | 未合并、未发布 |
| `gh run watch 42798827155 --exit-status` | success；Android build/JVM/lint 7m7s，Host scripts/handoff 39s | head `e4daf58`；仅 CI，不等于设备验证或独立审查 |
| 同 SHA push 运行 42798815793 | 暴露 `fileUploadsBeforePromptAndUsesReceipt` 测试竞态：先读到 `UPLOADED`、worker 尚未置 `uploadBusy=false` | 产品逻辑无失败；已改为同时等待两个状态 |
| `... --tests 'com.tanuki.DshComposerControllerTest'` | BUILD SUCCESSFUL；21 tests / 0 failures | 停止两段语义与延迟回执回归 |
| `... :app:compileModernDebugKotlin -x buildNativeRuntime` | BUILD SUCCESSFUL | 胶囊 `BasicTextField`/`onTextLayout`/形变动画 API 编译通过 |

- 哪些是前一轮结果，哪些是本次新验证：53 项目标回归、806 项完整 JVM、lint 均为本分支当前 head 新跑；806 中既有回归不是本切片新增。
- 未测试、超时、失败和暂未复现的情况：真实设备 UI/输入法/选择器/粘贴/拖入/跨应用临时权限/上传取消/停止、真实 PRoot DSH、assemble、Android 仪器、真机均未执行；`DshComposerBar` 的 Compose 焦点/键盘事件没有 JVM 自动化覆盖；`DshAttachmentReader` 的 Android URI/剪贴板路径没有仪器测试。

## 未完成与暂停项

- 页面接线：`DshNativePage`/`MainActivity` 归集成方；需要把 `DshNativeState.running` 同步到 `nativeComposer.setGenerationRunning(running)`，并在需要时把页面停止按钮与 `nativeComposer.requestStop()` 统一，避免两处状态不一致。
- 未验证：设备/模拟器、真实 DSH、上传取消的服务端残留（官方无上传取消端点，本地不保留残缺文件）、真实键盘导航。
- 未完成：本轮 P2/P0 修复的新 head CI 与独立 Codex High 审查；按项目规则最新 SHA 必须独立审查。上一条 review 的两条 thread 在推送后回复并 resolve。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收按项目既有暂停项保留；本次未触碰。

## 下一步

1. 推送本轮修复，跑完整 JVM/lint/交接/发布检查并等新 head CI；回复并 resolve 两条 review thread。
2. 由独立 Codex High 审查 PR #48 最新 SHA；按审查意见补修。
3. 集成方接线 `setGenerationRunning(running)`，在设备/模拟器验证胶囊尺寸、发送/停止形变、输入法避让、焦点、候选键盘、图片/文件/粘贴/拖入、上传取消与停止。
4. 通过后由有合并授权的人合并；本 worker 不合并、不发布。

## 约束与风险

- 数据/设备/权限边界：只读用户选择的 `content://` 或受限 `data:image`；不读取 `file://`、App 私有文件或外部明文路径；不写真实 token/Cookie 到日志/状态/偏好；未启动设备、未安装、未跑仪器。
- 关键决策及理由：停止入口放在组件内，避免改页面接线；`session/cancel` accepted 只表示受理，必须等官方 `running=false` 才显示已停止，避免隐藏仍在收尾的停止入口；停止不取消在途 prompt 回执，避免已提交消息被误判失败后重复发送；胶囊高度由文本布局驱动，只有图标做形变动画；上传取消只保证本地卡片和状态不回流，官方无上传取消方法，不宣称服务端已删除；进度使用不定进度，不伪造百分比。
- 失败尝试与避免重复踩坑的结论：Material3 当前 `LinearProgressIndicator` 无 `strokeWidth` 参数；命令名校验在 `DshComposerProtocol` 内，策略层需走公开校验；取消上传后重启 worker 必须有串行锁，否则旧请求晚到可能重复上传。
- 风险：`generationRunning` 未接线时发送成功后停止入口不会自动消失，且无法进入“已停止”；真实输入法/焦点/胶囊尺寸与形变动画需设备验证；取消上传后服务端可能仍完成已发出的 unary 请求；`DshComposerBar` 依赖页面提供的会话与生命周期。

## 产物与校验

- PR：https://github.com/leezzou/tanuki/pull/48（本轮修复提交后以 PR 当前 head 为准）。
- 新/旧APK、源码ZIP及校验文件：本轮未生成 APK、源码 ZIP 或发布附件；只改源码/测试/文档。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布、未合并。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：普通 PR 只新增本记录，不改共享状态。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码ZIP；普通 PR 运行 `python3 scripts/check-handoff.py --pr --record docs/handoffs/<本次记录>.md`，不修改 `AGENT-STATE.md`/`CURRENT.json`；提交前运行 `git diff --check` 与 `check-publication.py --git`。
