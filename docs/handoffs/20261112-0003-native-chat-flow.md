# 交接：原生聊天、会话与交互闭环

- Agent/会话标识：android_agent-104
- 时间：2026-11-12 00:03 CST 开始
- 状态：整体进行中；第一阶段实现与 Host 验证完成，准备独立 PR
- 前一记录：[当前发布基线](20261111-2313-rebuild-0.1.14.md)

## 用户目标与范围

全面打磨原生聊天共有功能，包括新建/切换、空态、输入附件、发送/停止/插话、流式滚动/键盘、工具审批、提问选择与提交、模型切换。允许调整旧布局，保留用户数据及既有能力。基于官方生产协议证据接通提问，不猜方法、不假提交成功。

本会话负责聊天、输入、会话、审批与提问及直接相关协议/测试。全局配色、安装后端与引导由其他 owner 维护。不操作设备、不读取凭据，不涉及 API Key 保存暂停项，不自行合并、发布或触发独立审查。

## 当前状态

远端 `git ls-remote origin refs/heads/main` 与起始 HEAD 均为 `3c56167b94efeff47a20bd609b389708ba080ce5`。独立 worktree 的功能分支为 `ao/tanuki-chat/chat-flow`，Git author/committer 为 leezzou 指定身份。目标验证变体为 `com.tanuki.modern.workspacecheck`，未操作设备。

已读规范、接口与核心源码，已请求独立行为研究提供实测与官方协议证据。首轮确认：运行中主按钮始终为停止、触屏排队发送入口仅静默降级后出现；QuestionCard 没有生产入口，审批流将提问交给后续回答者；模型入口位于会话详情；切会话重建阅读跟随且清空附件。现有发送快照保留新编辑、官方 idle 确认停止的保护应继续保留。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `ui/DshComposerController.kt` | 官方 queue/steer 模式、草稿驱动主键和独立停止判断、准确受理提示及持久发送尝试序号 |
| `ui/DshComposerBar.kt` | 运行中直接排队发送，提供带时机说明的插话菜单；保留停止、附件、引用、命令、用量说明和系统语音能力 |
| `ui/DshNativePage.kt`、`ui/DshChatRender.kt` | 主动发送恢复跟随，占位文案与真实排队语义一致 |
| `ui/DshNativeController.kt`、`MainActivity.kt` | accepted 后原位对账，不清空历史/工具/流式，不重启 follow；MainActivity 仅替换 onSent 一行，已向编排说明协调范围 |
| `DshComposerControllerTest.kt`、`DshNativeControllerTest.kt`、`DshChatRenderTest.kt` | 补忙碌发送、模式编码、失败保留、快速回执、原位对账和跨会话迟到回执回归 |
| `docs/INTERFACES.md`、`docs/NATIVE-UI.md`、本记录 | 同步第一阶段行为，固定已核实但尚未接线的官方提问协议证据 |

实施顺序：忙碌发送与独立停止、发送后恢复跟随；官方提问契约与完整闭环；结合行为证据优化模型入口、审批与会话切换。附件保留必须有总内存预算，不能无限按会话累积。

后续审计项：`DshNativeController.refresh()` 目标仅取 pendingSelection，手动刷新未保留当前会话；备用 `stop()` 回执/4 秒提示缺少 generation 保护。模型页面 Activity 虽接收目标会话 id，详情面板展开仍由页面本地 detailId 控制，后续快捷入口需一并核验。上述尚未在第一阶段修改。

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git status --short --branch`、`git rev-parse HEAD`、`git ls-remote origin refs/heads/main` | 起始干净且本地/远端完整 SHA 一致 | 不代表产品行为已验证 |
| `git switch -c ao/tanuki-chat/chat-flow` | 初次沙箱拒绝共享 refs 写入；同一命令获授权后成功 | checkout 后 status/branch/HEAD 正常 |
| 源码静态检查 | 已完成第一轮，范围与问题已回报 | 不代表 Android 或真实协议链路已验证 |
| 首轮 compile + 四类 JVM + lint 命令 | 退出 1，11m04s；产品/Android 测试源码阶段通过，JVM 测试源码编译失败 | 构建期间追加 onPromptAccepted，测试读到新接口但产品编译产物仍旧；未执行 JVM/lint，不计通过。停止改源码后完整串行重跑 |
| 固定源码离线 compile + 七类 JVM + lint 命令 | 退出 1，8m17s；产品/Android 测试/JVM 测试源码均编译通过 | lint 模型缺少离线缓存 hamcrest-integration/library 1.3、javawriter 2.1.1、jsr305 2.0.2；尚未执行 JVM/lint，不计通过。保持源码不变，仅移除 offline 联网重跑 |
| 联网 compile + 七类 JVM + lint 命令 | Gradle 退出 1，1m03s（环境准备另计）；140 项执行，1 项失败 | watchdog 回归第二次 busy 发送仍等待旧“已发送”，实际正确返回“已提交到队列”；只修正测试回执断言，保留静默态复位验证。lint 尚未完成，不计通过 |
| 修正 watchdog 回执断言后的最终 compile + 七类 JVM + lint 命令 | 退出 0，BUILD SUCCESSFUL，5m38s；产品/Android 测试/JVM 测试源码 compile 通过 | XML 实核 140 tests / 7 XML / 0 failures / 0 errors / 0 skipped；lint 0 errors / 117 warnings / 2 hints，与基线数量一致；不含设备或真实模型 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261112-0003-native-chat-flow.md` | 通过 | 共享状态允许落后 |
| `python3 scripts/check-publication.py --git` | 853 public files / 852 index entries / 0 findings | 最终暂存后重核 |

最终串行验证命令：初次通过 `source scripts/ao-worktree-env.sh` 准备环境；最后复验直接复用该 JDK17/SDK36 和 worktree 内的 `GRADLE_USER_HOME`、`ANDROID_USER_HOME`，避免重复扫描缓存。运行 `./gradlew --console=plain --max-workers=2 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest`，过滤 `com.tanuki.DshComposerControllerTest`、`DshChatRenderTest`、`DshChatFollowStateTest`、`DshNativeComposerBindingTest`、`DshNativeControllerTest`、`DshNativePagingTest`、`DshChatWindowTest`，最后执行 `:app:lintModernDebug`。最终任务退出 0，XML 已独立汇总为 140 项全过，lint 0 错误/117 警告/2 提示；阶段输出未冒充最终结果。

源码候选在系统临时目录建立 853 公开文件副本，运行未修改的 `scripts/package-source.py`，校验 `artifacts/SOURCE-SHA256SUMS` 并以原工作树 `check-publication.py --archive` 扫描；阶段结果退出 0、0 findings。仅为候选检查，未准备原生源码附件，不能作为二进制分发包。最终文档后刷新候选。

本记录不继承历史测试数字作为本轮结果。原生 UI、宿主协议夹具与 Android/真实模型端到端分开记录。

## 未完成与暂停项

第一阶段产品实现与 compile/JVM/lint 已完成，准备提交并创建独立 PR，完整 head 以 provider 回执为准。官方提问生产证据已取得，接线仍未完成；已读取独立源码研究的定稿行为规格，设备研究尚未形成聊天实测结论。模型高频入口、会话阅读与附件切换体验继续按证据推进。独立 Android QA 与真实模型端到端尚未验证；API Key 保存保持暂停，不读凭据。设备由其他会话独占。

## 下一步

1. 为运行中排队/插话与发送恢复跟随补有意义回归，保留独立停止及官方确认状态。
2. 收集官方生产提问事件/回答结构，先修接口契约，再接协议、控制器和 UI；保留失败/重连/撤回语义。
3. 结合行为研究优化高频入口，compile/JVM/lint 后收尾本记录与专题，交付 PR 完整 SHA，由编排安排审查与 QA。

## 约束与风险

不复制外部应用源码、资产或字符串，不在公开资料写入其身份。不改共享状态/印章。未知协议必须以官方公开声明和生产实现核对；accepted 与真实完成分离。附件状态扩展需限制总内存和端点身份，不让旧会话/主机结果污染新状态。

## 产物与校验

目前未生成 APK 或源码 ZIP，未安装、签名、发布或推送。当前保留交接均有历史验证或未完成事项，未确认本范围内过期记录，不做跨 owner 清理。普通 PR 仅维护本次唯一记录；提交前运行交接与公开检查并核验源码候选包。
