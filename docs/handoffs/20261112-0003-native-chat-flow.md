# 交接：原生聊天、会话与交互闭环

- Agent/会话标识：android_agent-104
- 时间：2026-11-12 00:03 CST 开始
- 状态：整体进行中；PR #107审查修复及系统栏接线Host复验完成，准备一次更新原PR，新SHA需独立重审/QA
- 前一记录：[当前发布基线](20261111-2313-rebuild-0.1.14.md)

## 用户目标与范围

全面打磨原生聊天共有功能，包括新建/切换、空态、输入附件、发送/停止/插话、流式滚动/键盘、工具审批、提问选择与提交、模型切换。允许调整旧布局，保留用户数据及既有能力。基于官方生产协议证据接通提问，不猜方法、不假提交成功。

本会话负责聊天、输入、会话、审批与提问及直接相关协议/测试。全局配色、安装后端与引导由其他 owner 维护。不操作设备、不读取凭据，不涉及 API Key 保存暂停项，不自行合并、发布或触发独立审查。

## 当前状态

远端 `git ls-remote origin refs/heads/main` 与起始 HEAD 均为 `3c56167b94efeff47a20bd609b389708ba080ce5`。独立 worktree 的功能分支为 `ao/tanuki-chat/chat-flow`，Git author/committer 为 leezzou 指定身份。目标验证变体为 `com.tanuki.modern.workspacecheck`，未操作设备。

已读规范、接口与核心源码，已请求独立行为研究提供实测与官方协议证据。首轮确认：运行中主按钮始终为停止、触屏排队发送入口仅静默降级后出现；QuestionCard 没有生产入口，审批流将提问交给后续回答者；模型入口位于会话详情；切会话重建阅读跟随且清空附件。现有发送快照保留新编辑、官方 idle 确认停止的保护应继续保留。

## 审查跟进与第二阶段隔离

第一阶段已交 [PR #107](https://github.com/leezzou/tanuki/pull/107)，完整 head `dfd0ea85fed47f50fa85c6aa4925c36db62543b6`；该 SHA 四条 provider CI 成功。独立 [review 5862474255](https://github.com/leezzou/tanuki/pull/107#pullrequestreview-5862474255) 的提交类型为 COMMENTED，正文结论为 changes requested，唯一 P2 是实时流已结束后 accepted 未恢复 follow。旧 CI 成功不能替代此必改或新 SHA 重审。

已 claim 既有 PR、fetch 上述完整 SHA，在本注册 worktree 建立 `ao/tanuki-chat/chat-flow-review`。问答阶段11个修改/新增文件已完整保存于本地唯一标识 stash `858d12e4506e5ce7be37d9eeaae93f561e68f71e`，未混入本修复 diff；后续在 `ao/tanuki-chat/questions` 恢复继续，不丢弃。该阶段首批纯协议/本地网络夹具40项曾通过，但控制器/UI未完成，不能宣称问答闭环。最新官方timed契约要求：claim流需持续开放；零值帧与零帧不同；HTTP ok可能为空操作，cancel不证明本答案获胜；只有持久结果/settled才能确认答案生效。后续需按这些事实重整状态及12组时序fixture，不沿用旧简写。

已消费独立研究的官方模块执行报告：Node v26.13.0，直接运行官方0.2.1-rc.5导出prototype方法，8组实验报告全过；本会话读取REPORT/results并实核实验脚本、结果JSON、依赖锁三份SHA一致，未重执行。该实证覆盖claim跨deadline/最后释放、零值帧与零帧、主动timeout、late false/true/REPLY_QUEUED、BAD_ANSWER及入队失败重试、stale dispatcher ok但不结算。ctx/agent/projection/peer为合成替身，未运行构造器、投影reducer、持久日志、HTTP/socket、完整Host、Android或模型；不计本分支测试，不等于12组接线时序全部完成。后续仍须补父取消、重连身份、真实流释放及持久胜出结果确认。

本次审查修复：仅失活live Job触发重建，健康/在途连接复用，保留分页/工具/流式前缀；连接尝试序号隔离旧事件和收尾，并先登记Job再启动。新增正常/异常断流两组回归，覆盖连续accepted只建一条新流、后续消息/工具/流式/running/idle仍到达、历史晚页不覆盖新内容、切B后A迟到受理不读不重连。`onSentReceivesSessionId` 改等回调自身CompletableDeferred；notice先发布不代表回调已执行。按编排明确接续授权取得唯一时段后，固定源码以1 worker复验成功：产品/AndroidTest/JVM测试源码compile完成、两类JVM71项全过、lint0错/117警告/2提示，4m48s退出0；已收集最终任务并立即向106释放时段，未重跑全套。

共享入口：105 owner TanukiUi宿主header/网页等待/设置导航，我owner原生Header及默认空LocalDshHostActions slot的后续定义/消费。独立SessionManager系统栏已按106稳定提交 `b2b30c4060f825ee6bc0795b6c03f68fc93103ec` 单文件引入 `ui/TanukiSystemBars.kt`，并在Activity的TanukiTheme首行调用，按协调将这条小修复纳入107；helper消费有效主题tokens与背景，不改全屏显隐/刘海。来源文件的106 compile/lint结果未冒充本分支验证；现本分支已实际完成产品/AndroidTest源码compile及lint，最终手动主题设备效果仍未验。header slot等其余后续UI不混入本次审查修复。不重复造已有manager后端；优先直达模型/会话能力，保留文件/附件既有能力。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `ui/DshComposerController.kt` | 官方 queue/steer 模式、草稿驱动主键和独立停止判断、准确受理提示及持久发送尝试序号 |
| `ui/DshComposerBar.kt` | 运行中直接排队发送，提供带时机说明的插话菜单；保留停止、附件、引用、命令、用量说明和系统语音能力 |
| `ui/DshNativePage.kt`、`ui/DshChatRender.kt` | 主动发送恢复跟随，占位文案与真实排队语义一致 |
| `ui/DshNativeController.kt`、`MainActivity.kt` | accepted 后原位对账，健康/在途 follow 复用、失活时恢复；不清空历史/工具/流式；MainActivity 仍仅第一阶段 onSent 一行 |
| `ui/TanukiSystemBars.kt`、`ui/DshSessionManagerActivity.kt` | 按106稳定SHA单文件引入helper，独立管理页系统栏使用当前有效主题背景与图标明暗，来源及待验证范围见上文 |
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
| 审查修复与系统栏接线的compile + 两类JVM + lint（1 worker） | 退出0，BUILD SUCCESSFUL，4m48s；产品/AndroidTest/JVM源码compile完成 | XML实核71 tests / 2 XML / 0 failures / 0 errors / 0 skipped（NativeController38、ComposerController33）；lint0 errors / 117 warnings / 2 hints；不含设备或真实模型，时段已释放 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261112-0003-native-chat-flow.md` | 通过 | 共享状态允许落后 |
| `python3 scripts/check-publication.py --git` | 853 public files / 852 index entries / 0 findings | 最终暂存后重核 |

审查修复实际串行命令：复用隔离JDK17/SDK及worktree缓存，执行 `./gradlew --console=plain --max-workers=1 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest --tests 'com.tanuki.DshNativeControllerTest' --tests 'com.tanuki.DshComposerControllerTest' :app:lintModernDebug`。最终任务已收集退出0，JUnit/lint XML独立汇总如上，未把Android测试源码编译当作仪器执行。

第一阶段首次通过命令：初次通过 `source scripts/ao-worktree-env.sh` 准备环境；最后复验直接复用该 JDK17/SDK36 和 worktree 内的 `GRADLE_USER_HOME`、`ANDROID_USER_HOME`，避免重复扫描缓存。运行 `./gradlew --console=plain --max-workers=2 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest`，过滤 `com.tanuki.DshComposerControllerTest`、`DshChatRenderTest`、`DshChatFollowStateTest`、`DshNativeComposerBindingTest`、`DshNativeControllerTest`、`DshNativePagingTest`、`DshChatWindowTest`，最后执行 `:app:lintModernDebug`。最终任务退出 0，XML 已独立汇总为 140 项全过，lint 0 错误/117 警告/2 提示；阶段输出未冒充最终结果。

源码候选在系统临时目录建立 853 公开文件副本，运行未修改的 `scripts/package-source.py`，校验 `artifacts/SOURCE-SHA256SUMS` 并以原工作树 `check-publication.py --archive` 扫描；阶段结果退出 0、0 findings。仅为候选检查，未准备原生源码附件，不能作为二进制分发包。最终文档后刷新候选。

本记录不继承历史测试数字作为本轮结果。原生 UI、宿主协议夹具与 Android/真实模型端到端分开记录。

## 未完成与暂停项

PR #107独立审查修复及系统栏接线已完成本分支compile/71项JVM/lint复验；收尾交接/公开/源码候选检查后一次提交推送原PR，完整新SHA以provider回执交编排重审，旧review/CI不沿用。问答生产控制器与UI仍未完成，WIP已隔离保留；模型/会话快捷入口、原生停机重试空态、附件/阅读状态继续按100/103研究推进。设备研究已确认manager现有模型/权限/搜索/时间能力，缺口在聊天入口与能力割裂，不重复造后端。独立 Android QA 与真实模型端到端未在本修复验证；API Key保存保持暂停，不读凭据，不占设备。

## 下一步

1. 本次产品/AndroidTest源码compile、两类JVM及lint已完成并释放时段；完成最终公开/交接/源码候选检查后一次commit/push更新原PR107，核对provider完整新SHA/CI，交编排重审。每阶段/重构建前读取自身AO transcript最新指导，后续重任务仍向106申请时段。
2. 恢复已隔离的questions WIP，合入审查修复，先修未完成的控制器源码及timed零值/零帧/持久确认语义，再按12组时序fixture接完整生产UI；legacy确认路径单独核实。
3. 系统栏helper已按106稳定SHA集成并完成本分支产品/AndroidTest源码compile及lint；之后定义并交付默认空LocalDshHostActions slot给105消费，复用现有manager完成模型/会话直达、原生停机重试空态和附件阅读体验。每阶段取得串行时段验证，完整SHA交编排安排审查/QA，不自行触发。

## 约束与风险

不复制外部应用源码、资产或字符串，不在公开资料写入其身份。不改共享状态/印章。未知协议必须以官方公开声明和生产实现核对；accepted 与真实完成分离。附件状态扩展需限制总内存和端点身份，不让旧会话/主机结果污染新状态。

## 产物与校验

第一阶段已推送原PR107，本次审查修复准备一次更新同一PR；未生成APK、安装、签名、合并或发布。源码ZIP仅在系统临时公开镜像作为候选检查，按最终文档刷新并核对SOURCE-SHA256SUMS及archive公开扫描，不作发布或二进制交付；最终摘要随AO/provider回执提供。当前保留交接均有历史验证或未完成事项，未确认本范围内过期记录，不做跨owner清理，不改共享状态/印章。普通PR仅维护本次唯一记录，提交前检查实际索引及最终源码候选。
