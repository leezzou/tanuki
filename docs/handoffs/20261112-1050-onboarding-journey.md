# 交接：首次引导与准备启动旅程

- Agent/会话标识：android_agent-105
- 时间：2026-11-12（本轮开始）
- 状态：已发布草稿按main a35c6ba变基维护；本轮验证边界如下，后续完整UI另存不混入
- 前一记录：[当前发布候选](20261111-2313-rebuild-0.1.14.md)

## 用户目标与范围

重做首次引导、安装准备与启动体验；以用户理解下一步、真实阶段/耗时、失败重试取消恢复、成功进入聊天为目标。只改引导与安装界面及纯展示策略，不改运行安装后端、全局主题或聊天实现。协调后端阶段契约及独立设备验证。API Key保存继续暂停；不操作模拟器、不清数据、不读凭据、不合并或发布。

## PR108变基维护

本次按用户直接要求将已发布2665ba927bec95c9effffe2475123c21ad14290d变基到远端实际main a35c6ba9dbe570dc61fee0d61509bf394d26a2c1（PR109已合并）。未提交的后续UI实现先完整保存为固定stash 0333bb1489455fb24737b4f51dfe91e9ee1d0748，本次历史维护不把该大批WIP混入公开提交，随后恢复，stash保留作为恢复点。

冲突为OnboardingScreen、TanukiUi及TEST-REPORT：保留两屏UI与main的conversationReady；共用准备页同时按Cookie/连接及网页支持门控自动/手动进入；main的两条旧引导fixture按新两屏迁移，保留真实localPreparationAction/withStartAdmission和HTTP后Cookie等待语义。两方测试报告均保留，未覆盖运行后端或MainActivity。旧81项/CI只属于原基线，不覆盖变基字节。

本轮不操作设备。权威generation7为106紧急发布专用，105不启动Gradle；实际本机只做格式、交接、公开源码与源码归档检查，GitHub CI以变基后新SHA查询为准。已检查现有7份实际交接（除模板），本范围只有当前一份，无可确认过期的本范围旧记录；其余跨PR依据/未验事项保留，未修改共享状态或印章。

变基轻量实核：git diff --cached --check及普通handoff通过；check-publication.py --git实核873公开文件/873索引条目、0 findings。源码检查快照首次因导出器把app/src按单个顶层名匹配而漏目录、package-source预检退出1；修正为目录前缀匹配后按同一原入口重打，未修改产品源码、未把导出失败算产品测试。源码检查归档不作为发版附件，本次没有APK、设备或新的本机JVM/lint结果。

## 当前状态

开始工作树干净，HEAD与远端main均为3c56167b94efeff47a20bd609b389708ba080ce5，分支ao/tanuki-onboarding/root。验证目标com.tanuki.modern.workspacecheck，无本会话设备任务。

已读现状规范和源码；OnboardingScreen环境页仅按设备门槛允许继续，未安装也会进入完成页；准备展示只映射Ubuntu环境状态，忽略后续组件；固定四页与设置安装四步重复，耗时只在全局抽屉。后端INIT已串起完整准备链，界面不另造安装器。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/OnboardingScreen.kt` | 两屏首启，本机/远程入口前置，设备检查与界面偏好渐进展开 |
| `app/src/main/kotlin/com/tanuki/ui/SetupJourney.kt`、`SetupJourneyPresentation.kt` | 共用准备状态与真实进度耗时、整链就绪门控、原操作重试；主动启动且健康后进入聊天 |
| `app/src/main/kotlin/com/tanuki/ui/SetupScreens.kt`、`SetupGuide.kt` | 默认一键准备，保留逐项组件维护、换源与版本功能；移除被替代的旧私有启动卡 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 仅引导调用块传入组件、工具检查、网页兼容性与命令回调 |
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingModel.kt` | 保留持久化值，旧界面选择/完成记录返回时跳过不可见步骤 |
| `app/src/test/kotlin/com/tanuki/SetupJourneyPresentationTest.kt`、`OnboardingFlowTest.kt` | 新增15项真实状态边界与旧记录返回回归 |
| `app/src/androidTest/kotlin/com/tanuki/SetupPresentationTest.kt` | 新增5项首启/准备/启动与历史失败互斥点击用例，组件测试从新维护入口进入；未上设备执行 |
| `docs/ONBOARDING.md`、本记录 | 更新真实主路径、恢复与验证边界 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| git status --short --branch、git rev-parse HEAD、git ls-remote origin refs/heads/main | 通过，工作树干净，基线一致 | 不代表产品行为已验证 |
| 源码与规范检查 | 已定位引导/准备/完成回调；已取得首启与安装行为规格 | 研究结论不代表设备验收 |
| source scripts/ao-worktree-env.sh；Gradle产品/Android测试源码compile与SetupJourneyPresentation、Onboarding*、OperationProgress*定向JVM | 首轮BUILD SUCCESSFUL（19m1s），XML实核7类77项、0失败/错误/跳过 | 独立缓存准备449s；原生任务跳过，无可安装APK交付；最终代码追加启动策略回归及lint正在运行 |
| 最终Gradle命令（见下） | BUILD SUCCESSFUL（6m3s），产品与AndroidTest源码compile通过；XML实核8类81项，0失败/错误/跳过；lint 0错误/117警告/2提示 | 新准备/引导文件无lint告警；未运行完整JVM、Android仪器、设备或真实模型 |
| git diff --check；python3 scripts/check-handoff.py --pr --record 本记录；python3 scripts/check-publication.py --git | 阶段通过，公开扫描856文件/852索引条目、0 findings | 最终提交前须按收尾字节重跑；Android仪器、设备与真实模型未执行 |

最终实际命令：先 `export TANUKI_SKIP_GRADLE_CACHE_COPY=1`、`source scripts/ao-worktree-env.sh`，再执行：

```bash
./gradlew --console=plain --max-workers=2 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin :app:testModernDebugUnitTest --tests 'com.tanuki.SetupJourneyPresentationTest' --tests 'com.tanuki.Onboarding*' --tests 'com.tanuki.OperationProgress*' --tests 'com.tanuki.TanukiStartupPolicyTest' :app:lintModernDebug
```

XML分别为OnboardingEnvironment 8、OnboardingFlow 14、OnboardingPrepare 11、OperationProgressLayout 7、OperationProgressPresentation 15、OperationProgress 7、SetupJourneyPresentation 15、TanukiStartupPolicy 4。宿主有既存SDK元数据与metrics目录权限警告，最终退出0；未为此扩权写全局Android配置。

## 未完成与暂停项

已只读取得行为研究的安装/首启规格，支持最短路径、可选设置安装并行、真实状态与原操作恢复的组织方式；不将源码观察标为Android实测。当前快照下UI与回归已实现，分支ao/tanuki-onboarding/journey。最终Host验证8类81项通过，compile/lint通过。编排确定先交独立可编译draft PR，待后端阶段契约PR完成验证/审查/适用QA并合main后再适配；不复制未提交后端，不把后端改动纳入本PR。待完成：typed阶段接入、后端集成后的回归与独立设备验收。API Key保存、真实模型账号与真机暂停项保留。

## 下一步

1. 以本分支当前Snapshot适配创建draft PR，保留未完成标记；后端owner负责runtime、Snapshot.startup/StartupProgress及MainActivity后端接线，本PR负责引导/准备展示和TanukiUi引导调用块。
2. 后端依赖合main后更新本分支，接入attemptId、StartupStage、单调阶段/总耗时与终态；安装现有下载快照继续复用，不从阶段中文反推typed启动阶段。
3. 在最终集成字节上重跑compile/定向JVM/lint；由独立Android执行者验收首启、本机/远程、权限、离页恢复、取消重试、启动成功直达和历史失败互斥，review/CI/QA均绑定最终SHA。

## 约束与风险

不操作设备、不读取真实凭据，不把Host通过当Android验收。未知总量不显示百分比，不隐藏失败。独有远程能力及双界面选择保留，采用渐进呈现。共享状态和印章由集成方维护。

## 产物与校验

未生成或安装APK，未合并或发布。源码候选只在系统临时目录的公开源码快照中运行原package-source入口，用于SHA/CRC/公开检查，不作发布附件。已核对现有5份历史交接：两份设备QA、动效C批、视觉P2及发布rebuild仍保留独有待验项或发布依据，无本范围内可确认过期记录，不做跨主题删除。普通PR只新增本记录，不刷新共享状态/印章。提交前按最终字节运行普通PR交接、工作树/索引公开检查与补丁格式检查。
