# 交接：原生聊天、会话与交互闭环

- Agent/会话标识：android_agent-104
- 时间：2026-11-12 00:03 CST 开始
- 状态：按用户立即合并及最新冲突修复要求，仅将公开ad0916三提交rebase到最新main/af416b1；冲突只在TEST-REPORT，保留主题/引导与聊天双方记录，未带入任何测试WIP，未重跑产品验证
- 前一记录：[当前发布基线](20261111-2313-rebuild-0.1.14.md)

## 用户目标与范围

全面打磨原生聊天共有功能，包括新建/切换、空态、输入附件、发送/停止/插话、流式滚动/键盘、工具审批、提问选择与提交、模型切换。允许调整旧布局，保留用户数据及既有能力。基于官方生产协议证据接通提问，不猜方法、不假提交成功。

本会话负责聊天、输入、会话、审批与提问及直接相关协议/测试。全局配色、安装后端与引导由其他 owner 维护。不操作设备、不读取凭据，不涉及 API Key 保存暂停项，不自行合并、发布或触发独立审查。

## 当前状态

远端 `git ls-remote origin refs/heads/main` 与起始 HEAD 均为 `3c56167b94efeff47a20bd609b389708ba080ce5`。独立 worktree 的功能分支为 `ao/tanuki-chat/chat-flow`，Git author/committer 为 leezzou 指定身份。目标验证变体为 `com.tanuki.modern.workspacecheck`，未操作设备。

已读规范、接口与核心源码，已请求独立行为研究提供实测与官方协议证据。首轮确认：运行中主按钮始终为停止、触屏排队发送入口仅静默降级后出现；QuestionCard 没有生产入口，审批流将提问交给后续回答者；模型入口位于会话详情；切会话重建阅读跟随且清空附件。现有发送快照保留新编辑、官方 idle 确认停止的保护应继续保留。

## 用户要求立即解决最新main冲突

本次远端实核PR head ad0916ec0039d1fb51387c0577d1c7b4696b4088、main af416b16f6247dd709b2744095314f334ec5fd94（106/108已合）；只在干净pr107-rebase树重放公开三提交，不等测试诊断。三提交冲突均仅TEST-REPORT的插入/标题上下文，完整保留主题/引导及107历史段落；代码补丁range-diff无变化。原motion/cursor/question与HostActions独立WIP不混入。仅做差异、字节、交接及公开检查；不启动Gradle、设备或宣称新QA通过。旧7e和G8冻结预检失败仍为各自输入的真实结果，cursor诊断单独后续。提交前确认本唯一记录仍有未完成项，其他owner记录不做跨范围清理；精确lease绑定远端旧ad0916，冲突修复后由parent按用户授权直接合并，本worker不代合/发布。

## 用户请求的PR107前次rebase

用户明确要求解决PR107（ao/tanuki-chat/chat-flow→main）冲突并rebase。开始实核远端PR完整head`7e3d00b9a6dc3fe586782cc5aeb04da954edaf46`、main完整`a35c6ba9dbe570dc61fee0d61509bf394d26a2c1`；新建独立pr107-rebase分支/工作树，仅重放原三提交。questions22文件及独立motion三文件候选保持原冻结，HostActions两文件补丁不混入。

三次冲突均仅docs/TEST-REPORT.md文首插入，完整保留main安装启动/线程池验证和107各阶段验证，不用ours/theirs整文件覆盖。range-diff显示代码补丁无变化；MainActivity相对新main只改onSent为nativeController::onPromptAccepted，保留109准备/重试/停止状态接线。其他107源代码/测试文件与原7e3d核对，不把原历史84项变成rebase后验证。

设备边界：100在原7e3d/API34 ARM64/浅色font1.0执行DshChatMotionPresentationTest，五例4过1失败；唯一单例复跑同第120行，ADB0不等于JUnit通过。独立受控帧候选仍未验证，本次rebase未带入，不宣称设备问题解决。新head不沿用旧批准；不运行未获调度授权的Gradle/设备，不合并或发布。后续应将motion候选集成到新基线并刷新冻结输入，再按编排新代次正常native+产品/仪器双assemble、独立review及100复验。

提交/推送前仅清理本范围确认过期的记录：本唯一记录仍有未完成事项；其他保留记录属main的发布/设备证据或其他owner，未确认过期，不做跨PR清理，不改AGENT-STATE/CURRENT。公开检查与源码候选按本次最终文档刷新，不重用7e3d旧ZIP。推送采用明确绑定旧7e3d的force-with-lease，若远端已变化就拒绝覆盖。

## follow逻辑流终止追修

前次修复已推送并实核远端/provider head `641d518978b947a79a61b502963cb994f050f272`。新[行内P2](https://github.com/leezzou/tanuki/pull/107#discussion_r4545647944)，线程 `PRRT_kwDOVgRm4s62ceLp`，指出外层Job存活不证明follow可用：生产NativeClient在逻辑end/error后继续收集socket。前次仅让整个Flow结束/抛错的回归未覆盖此形态，71项不能替代本次新增验证。

当前静态修复：必需follow/events流end/error退出mux收集并释放Peer；终止Failure显式标terminal，控制器独立标记失效并使live=false，accepted在旧Job仍active时也恢复。普通Agent业务错误不使健康follow失效；正常/异常收尾同样先标失效，Connected清标记；尝试序号继续隔离旧事件与收尾。新NativeLive两组使用生产DshNativeClient、本地合成HTTP connect/prompt及受控mux入站，保持socket不主动finish，只注入follow end/error，再受理并验证新follow正文/工具/流式/running/idle及旧历史保留。列表/历史为合成替身，不宣称真实Host或网络全链路；另补控制器普通/terminal错误与仍开放Flow区别。

按37明确接续授权，以1 worker完成本轮产品/AndroidTest/JVM源码compile、三类JVM和lint，4m09s退出0。XML独立实核84项/3份报告/0失败错误跳过（12+39+33），lint0错误/117警告/2提示；最终job已收集并立即向106释放时段，未重跑全套。用户明确授权push后用该thread ID直接resolve；必须在本次验证、公开检查、推送及新head核对之后执行，不提前解决。问答11文件仍完整隔离，本追修不混入其他阶段。

## 审查跟进与第二阶段隔离

第一阶段已交 [PR #107](https://github.com/leezzou/tanuki/pull/107)，完整 head `dfd0ea85fed47f50fa85c6aa4925c36db62543b6`；该 SHA 四条 provider CI 成功。独立 [review 5862474255](https://github.com/leezzou/tanuki/pull/107#pullrequestreview-5862474255) 的提交类型为 COMMENTED，正文结论为 changes requested，唯一 P2 是实时流已结束后 accepted 未恢复 follow。旧 CI 成功不能替代此必改或新 SHA 重审。

已 claim 既有 PR、fetch 上述完整 SHA，在本注册 worktree 建立 `ao/tanuki-chat/chat-flow-review`。问答阶段11个修改/新增文件已完整保存于本地唯一标识 stash `858d12e4506e5ce7be37d9eeaae93f561e68f71e`，未混入本修复 diff；后续在 `ao/tanuki-chat/questions` 恢复继续，不丢弃。该阶段首批纯协议/本地网络夹具40项曾通过，但控制器/UI未完成，不能宣称问答闭环。最新官方timed契约要求：claim流需持续开放；零值帧与零帧不同；HTTP ok可能为空操作，cancel不证明本答案获胜；只有持久结果/settled才能确认答案生效。后续需按这些事实重整状态及12组时序fixture，不沿用旧简写。

已消费独立研究的官方模块执行报告：Node v26.13.0，直接运行官方0.2.1-rc.5导出prototype方法，8组实验报告全过；本会话读取REPORT/results并实核实验脚本、结果JSON、依赖锁三份SHA一致，未重执行。该实证覆盖claim跨deadline/最后释放、零值帧与零帧、主动timeout、late false/true/REPLY_QUEUED、BAD_ANSWER及入队失败重试、stale dispatcher ok但不结算。ctx/agent/projection/peer为合成替身，未运行构造器、投影reducer、持久日志、HTTP/socket、完整Host、Android或模型；不计本分支测试，不等于12组接线时序全部完成。后续仍须补父取消、重连身份、真实流释放及持久胜出结果确认。

本次审查修复：仅失活live Job触发重建，健康/在途连接复用，保留分页/工具/流式前缀；连接尝试序号隔离旧事件和收尾，并先登记Job再启动。新增正常/异常断流两组回归，覆盖连续accepted只建一条新流、后续消息/工具/流式/running/idle仍到达、历史晚页不覆盖新内容、切B后A迟到受理不读不重连。`onSentReceivesSessionId` 改等回调自身CompletableDeferred；notice先发布不代表回调已执行。按编排明确接续授权取得唯一时段后，固定源码以1 worker复验成功：产品/AndroidTest/JVM测试源码compile完成、两类JVM71项全过、lint0错/117警告/2提示，4m48s退出0；已收集最终任务并立即向106释放时段，未重跑全套。

原生空态可达性已与105核对：TanukiUi当前useNative将RUNNING作为渲染条件，因此单改内层STOPPED/FAILED空态不可达。后续须等稳定空态与slot交付后由105一起翻本地native可用门控，连接/发送仍由真实runtime/ready guards控制，远程保留Web；同时核对通知路由的共用策略。拟由MainActivity传runtimeSnapshot: Snapshot?及可空onStartRuntime/onRetryRuntime/onOpenSetup回调，复用现有启动/重试/引导动作；此契约尚未实现或交付稳定SHA，不提前引用。

共享入口：105 owner TanukiUi宿主header/网页等待/设置导航，我owner原生Header及默认空LocalDshHostActions slot的后续定义/消费。独立SessionManager系统栏已按106稳定提交 `b2b30c4060f825ee6bc0795b6c03f68fc93103ec` 单文件引入 `ui/TanukiSystemBars.kt`，并在Activity的TanukiTheme首行调用，按协调将这条小修复纳入107；helper消费有效主题tokens与背景，不改全屏显隐/刘海。来源文件的106 compile/lint结果未冒充本分支验证；现本分支已实际完成产品/AndroidTest源码compile及lint，最终手动主题设备效果仍未验。header slot等其余后续UI不混入本次审查修复。不重复造已有manager后端；优先直达模型/会话能力，保留文件/附件既有能力。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `ui/DshComposerController.kt` | 官方 queue/steer 模式、草稿驱动主键和独立停止判断、准确受理提示及持久发送尝试序号 |
| `ui/DshComposerBar.kt` | 运行中直接排队发送，提供带时机说明的插话菜单；保留停止、附件、引用、命令、用量说明和系统语音能力 |
| `ui/DshNativePage.kt`、`ui/DshChatRender.kt` | 主动发送恢复跟随，占位文案与真实排队语义一致 |
| `ui/DshNativeController.kt`、`MainActivity.kt` | accepted 后原位对账，健康/在途 follow 复用、失活时恢复；不清空历史/工具/流式；MainActivity 仍仅第一阶段 onSent 一行 |
| `web/DshLive.kt`、`web/DshNativeApi.kt`、`DshNativeLiveTest.kt` | 终止性连接/逻辑流错误与业务错误分开，owned mux end/error释放Peer，生产Client保持socket开放形态回归；本追修验证状态见上文 |
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
| 本次rebase range-diff/字节校验及历史完整性 | 13个app源码/测试文件与旧7e3d字节一致；MainActivity为新main加唯一onSent变更；main及旧PR测试专题非空行全文按序保留 | 仅冲突解决静态验证，不是新compile/JVM/设备通过 |
| 本次`git diff --check`与唯一记录`check-handoff.py --pr` | 均退出0 | 不改共享状态，不沿用旧head批准 |
| 本次`check-publication.py --git`、`package-source.py`、SOURCE-SHA256SUMS及归档扫描 | 均退出0；871公开文件/871索引，0 findings，源码候选校验和一致 | 仅日常源码检查候选，不是版本决定、发布或native二进制对应源码附件；文档收尾后刷新最终候选 |
| 首轮 compile + 四类 JVM + lint 命令 | 退出 1，11m04s；产品/Android 测试源码阶段通过，JVM 测试源码编译失败 | 构建期间追加 onPromptAccepted，测试读到新接口但产品编译产物仍旧；未执行 JVM/lint，不计通过。停止改源码后完整串行重跑 |
| 固定源码离线 compile + 七类 JVM + lint 命令 | 退出 1，8m17s；产品/Android 测试/JVM 测试源码均编译通过 | lint 模型缺少离线缓存 hamcrest-integration/library 1.3、javawriter 2.1.1、jsr305 2.0.2；尚未执行 JVM/lint，不计通过。保持源码不变，仅移除 offline 联网重跑 |
| 联网 compile + 七类 JVM + lint 命令 | Gradle 退出 1，1m03s（环境准备另计）；140 项执行，1 项失败 | watchdog 回归第二次 busy 发送仍等待旧“已发送”，实际正确返回“已提交到队列”；只修正测试回执断言，保留静默态复位验证。lint 尚未完成，不计通过 |
| 修正 watchdog 回执断言后的最终 compile + 七类 JVM + lint 命令 | 退出 0，BUILD SUCCESSFUL，5m38s；产品/Android 测试/JVM 测试源码 compile 通过 | XML 实核 140 tests / 7 XML / 0 failures / 0 errors / 0 skipped；lint 0 errors / 117 warnings / 2 hints，与基线数量一致；不含设备或真实模型 |
| 审查修复与系统栏接线的compile + 两类JVM + lint（1 worker） | 退出0，BUILD SUCCESSFUL，4m48s；产品/AndroidTest/JVM源码compile完成 | XML实核71 tests / 2 XML / 0 failures / 0 errors / 0 skipped（NativeController38、ComposerController33）；lint0 errors / 117 warnings / 2 hints；不含设备或真实模型，时段已释放 |
| follow逻辑流终止追修compile + 三类JVM + lint（1 worker） | 退出0，BUILD SUCCESSFUL，4m09s；产品/AndroidTest/JVM源码compile完成 | XML实核84 tests / 3 XML / 0 failures / 0 errors / 0 skipped（NativeLive12、NativeController39、Composer33）；lint0 errors / 117 warnings / 2 hints；仅Host合成夹具，时段已释放 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261112-0003-native-chat-flow.md` | 通过 | 共享状态允许落后 |
| `python3 scripts/check-publication.py --git` | 853 public files / 852 index entries / 0 findings | 最终暂存后重核 |

follow逻辑流终止追修实际命令：复用隔离JDK17/SDK及worktree缓存，运行 `./gradlew --console=plain --max-workers=1 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest --tests 'com.tanuki.DshNativeLiveTest' --tests 'com.tanuki.DshNativeControllerTest' --tests 'com.tanuki.DshComposerControllerTest' :app:lintModernDebug`，最终收取退出0，XML独立汇总如上。

前次审查修复实际串行命令：复用隔离JDK17/SDK及worktree缓存，执行 `./gradlew --console=plain --max-workers=1 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest --tests 'com.tanuki.DshNativeControllerTest' --tests 'com.tanuki.DshComposerControllerTest' :app:lintModernDebug`。最终任务已收集退出0，JUnit/lint XML独立汇总如上，未把Android测试源码编译当作仪器执行。

第一阶段首次通过命令：初次通过 `source scripts/ao-worktree-env.sh` 准备环境；最后复验直接复用该 JDK17/SDK36 和 worktree 内的 `GRADLE_USER_HOME`、`ANDROID_USER_HOME`，避免重复扫描缓存。运行 `./gradlew --console=plain --max-workers=2 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest`，过滤 `com.tanuki.DshComposerControllerTest`、`DshChatRenderTest`、`DshChatFollowStateTest`、`DshNativeComposerBindingTest`、`DshNativeControllerTest`、`DshNativePagingTest`、`DshChatWindowTest`，最后执行 `:app:lintModernDebug`。最终任务退出 0，XML 已独立汇总为 140 项全过，lint 0 错误/117 警告/2 提示；阶段输出未冒充最终结果。

源码候选在系统临时目录建立 853 公开文件副本，运行未修改的 `scripts/package-source.py`，校验 `artifacts/SOURCE-SHA256SUMS` 并以原工作树 `check-publication.py --archive` 扫描；阶段结果退出 0、0 findings。仅为候选检查，未准备原生源码附件，不能作为二进制分发包。最终文档后刷新候选。

本记录不继承历史测试数字作为本轮结果。原生 UI、宿主协议夹具与 Android/真实模型端到端分开记录。

## 未完成与暂停项

原7e3d已完成历史compile/84项JVM/lint及独立review，指定follow线程已解决；设备QA仍4过1失败。本次rebase未重验产品，也未带入独立motion候选；新head需重新绑定审查及适用QA。问答生产接线22文件已在另一分支冻结，未验证且当前暂停；模型/会话快捷入口、原生停机重试空态、附件/阅读状态继续按100/103研究推进。设备研究已确认manager现有模型/权限/搜索/时间能力，缺口在聊天入口与能力割裂，不重复造后端。独立 Android QA 与真实模型端到端未在本修复验证；API Key保存保持暂停，不读凭据，不占设备。

## 下一步

1. 完成本次rebase差异/交接/公开源码候选检查，按用户指令以明确旧head lease一次更新107并核对GitHub不再CONFLICTING；不合并、不宣称新测试通过。指定follow线程此前已解决，不重复处理。
2. 原7e3d设备失败继续阻断；独立motion最小候选需集成新main基线、刷新三文件冻结，再按新账本正常native+双assemble一次验证并交独立review/100复验，不重跑旧84。questions22文件继续冻结暂停，不混入本rebase。
3. HostActions作者补丁已独立交105，其集成待新的验证；runtime门控未翻，仍需完整inventory/tools准备与真实回调。后续模型/会话入口、附件内存/阅读和实际忙态Composer合成Compose覆盖保留，不扩展本批。

## 约束与风险

不复制外部应用源码、资产或字符串，不在公开资料写入其身份。不改共享状态/印章。未知协议必须以官方公开声明和生产实现核对；accepted 与真实完成分离。附件状态扩展需限制总内存和端点身份，不让旧会话/主机结果污染新状态。

## 产物与校验

第一阶段及follow追修原head为7e3d，本次仅rebase冲突解决并更新107，不带入独立未验证修补；未生成APK、安装、签名、合并或发布。源码ZIP仅在系统临时公开镜像作为候选检查，按最终文档刷新并核对SOURCE-SHA256SUMS及archive公开扫描，不作发布或二进制交付；最终摘要随AO/provider回执提供。当前保留交接均有历史验证或未完成事项，未确认本范围内过期记录，不做跨owner清理，不改共享状态/印章。普通PR仅维护本次唯一记录，提交前检查实际索引及最终源码候选。
