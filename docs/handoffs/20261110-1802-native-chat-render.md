# 交接：20261110-1802-native-chat-render

- Agent/会话标识：android_agent-51（native chat render worker）
- 时间：2026-11-10 18:02 CST
- 状态：实现与 JVM/lint 已完成；PR 待创建/待独立审查，设备效果未验证
- 前一记录：[0.1.11 发布准备与交接集成](20261110-1636-release-0.1.11.md)

> 普通 PR 只新增本记录，不修改 `docs/AGENT-STATE.md` 或 `docs/handoffs/CURRENT.json`；共享状态由集成维护方统一刷新。

## 用户目标与范围

- 用户本次实际要求：把原生聊天界面的渲染质量做到“可日常使用”，基于 `origin/main=4f2a3717` 建独立分支，只改渲染相关文件；不要修改 `DshComposer*` 组件内部与共享状态文件。需覆盖流式平滑、Markdown 完整度、图片/附件占位与失败提示、链接确认、长消息/长历史性能、发送失败/重试视觉、错误可复制、JVM 渲染回归，并 PR 交付、不合并、不发布。
- 本次包含：`DshMarkdown`/`DshNativePage`/`DshNativeController` 渲染与呈现层；新增 `DshChatRender.kt` 纯策略；相应 JVM 回归；渲染专题文档、测试记录与本交接。
- 本次不包含：`DshComposer*` 组件内部、`web/DshNativeApi.kt` 等协议/传输、`MainActivity.kt`、官方 npm 包、DSH 数据、共享状态文件；不新增图片授权加载协议、不做历史向前分页协议、不实现语法高亮或反馈接口。
- 用户暂停/保留：API Key 保存、真实模型/账号与真机验收；未获授权不合并、不发布。
- 基线：`origin/main=4f2a3717d6d795fd6b94cdb695878d8119f818c1`；分支 `ao/chat-render/render-quality`（从 `ao/chat-render/root` 建，root=4f2a3717）。PR 目标 `main`，未合并。

## 当前状态

- 已实现：
  - 流式：控制器把 `assistant-stream` 累计全文按约 32ms 合并，同一帧只发布最后一版；`Finished(committed)` 同步发布最后一帧；assistant 消息/非 committed 结束取消待发布增量；`DshStreamPolicy.merge` 兼容累计全文、纯增量、重复尾段与空串重置。
  - 长消息/历史：`LazyColumn` 只渲染末尾窗口（80，步进 40，上限 400），更早本地消息可“显示更早”；时间线按 messages/tools 记忆化；单条 Markdown 300 块、代码 400 行、表格 60 行/12 列上限并明确提示；代码/表格超限时复制仍用完整原文。
  - 滚动跟随：用户拖动立即停止跟随，松手/惯性结束后在底部才恢复；自动滚动对齐最后一项底部，长流式回复可看到最新行；离开底部显示“↓ 最新”。
  - Markdown：标题、段落、引用、分隔线、有序/无序/嵌套/任务列表、带对齐表格、围栏代码块、行内粗体/斜体/粗斜体/删除线/行内代码/链接；代码块等宽、横向滚动、`softWrap=false`、可选行号、复制；行内代码与代码块视觉区分。
  - 链接：`http/https/mailto/tel` 点击先确认再调起系统应用；`javascript/data/intent/file/content` 拒绝并显示中文原因；附件/相对路径提示需要工作区授权。
  - 图片/附件：图片占位卡片、中文原因、点击查看；本地/内嵌图片按尺寸采样解码，失败显示“图片加载失败：文件不可读、已损坏或格式不支持”；远程与会话授权图片仍不自动联网。独立附件链接显示占位卡片，可查看/复制地址。
  - 发送状态：发送中占位气泡 + 进度圈；失败保留原文与可读原因，提供“重试/填入输入框/复制内容”；错误 banner 显示中文分类、原始短错误并支持“复制错误”；整条消息可复制。
- 已验证：定向 46 tests；完整 JVM **818 tests / 91 结果文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 101 warnings / 1 hint**；`check-native-parity.py` 通过。详见下方验证记录。
- 待验证：真实 Android 设备/模拟器上的流式帧率、链接与文本选择手势冲突、图片解码内存、长代码/表格横向滚动、深色/大字体、输入法；PRoot DSH 实时流；仪器、assemble。
- 关键事实与定位：`DshChatRender.kt` 纯策略；`DshMarkdown.kt` 解析与渲染；`DshNativeController.kt` 流式/失败状态；`DshNativePage.kt` 窗口/跟随/卡片；见 [NATIVE-UI](../NATIVE-UI.md) 第 46 节。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/DshChatRender.kt` | 新增纯策略：消息窗口、底部判定/跟随、流式合并、链接/图片/附件分类、错误可读化、代码行号、表格上限。 |
| `app/src/main/kotlin/com/tanuki/ui/DshMarkdown.kt` | 解析器支持嵌套/任务列表、表格对齐与转义、附件块、块上限；渲染器支持代码行号/不换行、链接确认、图片/附件占位与查看器、本地图片有界解码。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativePage.kt` | 窗口化消息与“显示更早”、底部跟随/“↓ 最新”、发送失败卡片、错误复制、消息复制、附件/图片入口接线。 |
| `app/src/main/kotlin/com/tanuki/ui/DshNativeController.kt` | 新增 `DshFailedPrompt` 与 `failedPrompt` 状态、流式按帧合并/取消、失败重试与切会话清理；不改协议。 |
| `app/src/test/kotlin/com/tanuki/DshMarkdownTest.kt` | 15 项 Markdown/代码/表格/链接/截断回归。 |
| `app/src/test/kotlin/com/tanuki/DshChatRenderTest.kt` | 新增 11 项纯策略回归：窗口、跟随、流式合并、链接协议、图片/附件、错误分类。 |
| `app/src/test/kotlin/com/tanuki/DshNativeControllerTest.kt` | 新增流式合并、committed 取消、失败保留/重试、切会话清理；更新原有流式断言为最终帧。 |
| `docs/NATIVE-UI.md` | 新增第 46 节渲染切片、文件表与验证结果；更新图片/附件事件描述。 |
| `docs/NATIVE-PARITY.md` | 更新 CHAT-02/03/04/05/11/12/13/15 的本轮落地状态与边界。 |
| `docs/TEST-REPORT.md` | 新增本轮 JVM/lint 记录。 |
| `docs/handoffs/20261110-1802-native-chat-render.md` | 本记录。 |

- 与 main 对比确认：`DshComposer*` 组件/测试、`web/DshNativeApi.kt`、`MainActivity.kt`、`docs/AGENT-STATE.md`、`docs/handoffs/CURRENT.json` 无改动。

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin -x buildNativeRuntime` | BUILD SUCCESSFUL | 本 worktree、JDK17、Android SDK；仅既有/剪贴板弃用告警。 |
| `./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime --tests 'com.tanuki.DshMarkdownTest' --tests 'com.tanuki.DshChatRenderTest' --tests 'com.tanuki.DshNativeControllerTest'` | BUILD SUCCESSFUL；15 + 11 + 20 = 46 tests / 0 failures / 0 errors / 0 skipped | 纯 JVM；`DshNativeControllerTest` 使用 `Dispatchers.Unconfined` 与合成 source，不连真实 DSH。 |
| `./gradlew :app:testModernDebugUnitTest :app:lintModernDebug -x buildNativeRuntime` | BUILD SUCCESSFUL（5m43s）；**818 tests / 91 结果文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 101 warnings / 1 hint** | 本轮新跑；lint 仅既有存量告警，不改产品安全策略。 |
| `python3 scripts/check-native-parity.py` | PASS：166 行、14 功能域、ID/状态/验收/路径检查通过 | 文档结构检查，不证明设备行为。 |
| 设备/仪器/assemble | 未执行 | JVM/lint 不能替代 Android 设备验收；本轮未安装/启动 App。 |

- 首次定向运行 4 项失败：窗口上限测试期望值写错、列表正则捕获组取错、重发成功后仍保留本地“发送中”占位的断言写反；已按实现语义修正并重跑通过。未将失败计为通过。
- 未测试：真实设备/模拟器、PRoot DSH、真实 follow/streaming、图片 content:// 解码、链接调起系统应用、深色/大字体/输入法、assemble、仪器。

## 未完成与暂停项

- 未完成：PR 创建后的独立 Review（Codex High）与设备 QA 由编排器/独立 QA 安排；当前 head 的 CI 以 PR 页面为准。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号、真机验收；未获合并/发布授权。

## 下一步

1. 推送 `ao/chat-render/render-quality` 并创建目标 `main` 的 PR；PR 描述附改动摘要、818 tests/lint 结果、未验证设备范围与风险。
2. 按需运行 `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1802-native-chat-render.md`、`python3 scripts/check-publication.py --git`、`git diff --check`；确认 composer/共享状态零 diff。
3. 由编排器安排当前 SHA 的独立审查与设备 QA；未获授权不合并、不发布。
4. 后续可做：历史向前分页协议、远程/授权图片加载、文件链接工作区预览、语法高亮/代码折叠、逐轮失败行与反馈接口。

## 约束与风险

- 不读取/输出真实 Key/Cookie/token/会话；测试只用合成文本、URL 与事件。
- 不修改官方 npm 包/DSH 数据/WebView 安全策略；远程图片不自动联网，危险协议不打开。
- 代码块/表格上限是展示上限，不改变原文与复制内容；更早于已加载页的历史仍依赖官方界面。
- 流式合并只改变发布频率，不改变官方事件语义；committed 消息到达后以落盘消息为准。
- 设备未验证是主要风险：滚动跟随/链接手势/图片解码/长表格横向滚动可能需要在真机调参。

## 产物与校验

- 本轮不构建/安装 APK，不创建 tag/Release，不合并默认分支；不生成源码 ZIP。
- PR：待创建；head 以最终推送 SHA 为准。
- 普通 PR 不刷新共享状态/印章；集成维护方在批量合并后统一运行 `--integrate`。
