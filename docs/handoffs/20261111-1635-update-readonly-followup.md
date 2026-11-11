# 交接：更新页与安装期只读 follow-up

- Agent/会话标识：android_agent-73
- 时间：2026-11-11 CST
- 状态：实现和 Host 定向验证完成；以 `ao/update-page/v2` 交付，未合并、未发布
- 前一记录：[组件进度与重试专题](../COMPONENTS.md)；安装进度重开的历史验证已归入 [测试现状](../TEST-REPORT.md)。

## 用户目标与范围

仅迁移五块增量：唯一任务宿主与查看器高度避让、Scaffold 统一底部避让、安装期只读绕过环境事务锁并保护写入、应用更新页简短正文/折叠详情/主按钮/可信状态点、版本变化后旧重试失效并保留失败详情。保留 main 的新文件页、错误恢复、横屏阅读及 #82 基础浮层/空态/导入行，不重复移植或覆盖整文件。

编排明确要求仅 compile + 定向测试，通过后统一推送新分支 `ao/update-page/v2` 并开 PR，标题 `fix(update): 更新页改版与安装期只读解锁`。不跑本地 assemble、全量 JVM 或 lint；不向 #77/#82 推送。

## 当前状态

- fetch 实核基线为 `origin/main=9f5a2fe3c1b16e019144d1ff26f94c5babf12bcf`，已含 #82。新分支从该 SHA 创建，开始时工作区干净。
- main 已有保留原 `FailureRetry` 版本与重装语义的生产路由；本轮在该实现上添加版本选择变化后的编号失效，不回退原路由。
- 目标仍为独立 workspacecheck；设备由独立 QA 使用，本会话不操作设备或 adb。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt`、`FileOperationOverlay.kt` | 外壳统一导航/系统栏避让；文件页唯一任务宿主共用运行时与文件抽屉，保留 main 的存储/远程导航和错误恢复。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt`、`WorkspaceFileViewer.kt`、`FileViewerLayout.kt`、`FileAccessPresentation.kt` | 读取实测操作栏高度，保留横屏紧凑条/阅读恢复；安装期写入提示覆盖顶部导入、空态/菜单、编辑和手机传输，不重做空态或导入行。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceIoGate.kt`、`WorkspaceFileManager.kt`、`app/src/main/kotlin/com/tanuki/MainActivity.kt` | 只读与冲突查询不等环境事务；写入前后复核，保留手机授权/删源/容量保护；门禁使用实际操作快照而非包含自有文件锁/常驻服务的宽泛 busy 标志。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeVersionSelection.kt`、`RuntimeComponents.kt`、`OperationRetry.kt`、`RuntimeManager.kt`、`ui/OperationProgressPresentation.kt` | 保存新目标前失效旧编号与回调，保留失败阶段/原因/耗时并返回组件入口；main 原版本/重装路由保留。 |
| `app/src/main/kotlin/com/tanuki/ui/ApplicationUpdateCard.kt`、`ApplicationUpdatePresentation.kt` | 简短正文、折叠工程详情、主按钮、可信版本状态点；原错误恢复、清理与设置入口保留。 |
| `app/src/test/kotlin/com/tanuki/` 相关7文件 | 版本选择+生产重试路由、锁边界与持锁不自拒绝、稳态服务导入、布局/入口门禁及更新状态回归。 |
| `app/src/androidTest/kotlin/com/tanuki/SetupPresentationTest.kt` | 迁入唯一宿主真实坐标点击/取消及深色大字体设置末项边界断言；仅编译，不运行设备。 |
| `docs/APP-UPDATES.md`、`COMPONENTS.md`、`WORKSPACE-STORAGE.md`、本记录 | 最小更新专题行为与当前验证，不修改共享状态或整份旧测试报告。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git fetch origin main`、新建 `ao/update-page/v2` | 成功，基线 `9f5a2fe3c1b16e019144d1ff26f94c5babf12bcf` | Git 元数据工作区外，首次被沙箱拒绝，原命令一次性权限重试成功。 |
| 首轮同组命令，使用 `--offline` | 失败，49秒，尚未进入 Kotlin 编译/测试 | 最新 main 新增 QR 依赖未在本 worktree 离线缓存；改为在线正常解析，未修改全局缓存/配置。 |
| 去掉 `--offline` 的同组 compile + 13类定向 JVM | 成功，5分57秒 | 在线取得新依赖；后续补齐顶部导入/手机传输门禁接线后，再执行下一条最终确认，不把此中间状态当最终证明。 |
| 下述最终命令（`--offline`） | **BUILD SUCCESSFUL，3分1秒；13类106项，0 failures/errors/skipped** | 产品、仪器、JVM源码均编译；只执行选定JVM类，无 assemble、全量JVM、lint 或设备执行。 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1635-update-readonly-followup.md` | 通过 | 共享状态允许落后，未写印章。 |
| `python3 scripts/check-publication.py --git` | 880个公开文件，0 findings | 索引和工作树独立扫描；提交前对实际暂存再检查。 |
| `python3 scripts/package-source.py`、`python3 scripts/check-publication.py --archive artifacts/sources/Tanuki-0.1.7-source.zip` | 880文件源码候选生成、归档扫描0 findings | 收尾文档后再生成并核对外部checksum；native_sources=absent，本轮不分发原生二进制。 |

最终实际命令（项目 JDK17、SDK36，worktree 独立可写缓存）：

```bash
source scripts/ao-worktree-env.sh
./gradlew --offline --no-daemon --max-workers=2 -PtestBuildType=debug \
  -I scripts/workspace-verification.init.gradle -x buildNativeRuntime \
  :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin \
  :app:testModernDebugUnitTest \
  --tests com.tanuki.ApplicationUpdatePresentationTest \
  --tests com.tanuki.RuntimeVersionSelectionTest \
  --tests com.tanuki.WorkspaceIoGateTest \
  --tests com.tanuki.FileViewerLayoutTest \
  --tests com.tanuki.FileAccessPresentationTest \
  --tests com.tanuki.OperationProgressPresentationTest \
  --tests com.tanuki.OperationProgressLayoutTest \
  --tests com.tanuki.OperationRetryTest \
  --tests com.tanuki.FailureRecoveryFollowupTest \
  --tests com.tanuki.ImportNamingTest \
  --tests com.tanuki.TransferEngineTest \
  --tests com.tanuki.FileOperationOutcomeTest \
  --tests com.tanuki.FileTransferProgressTest --console=plain
```

实际 XML 分项：更新状态4、版本选择2、IO门禁4、查看器几何/宿主11、文件权限6、进度展示13、布局/入口契约5、原路由重试8、main错误恢复11、导入命名8、传输执行20、操作结果9、文件进度5。

main 的 `hasActiveOperation` 还包含 `mutation.isLocked` 与常驻 `serverJob`，不能直接作为持锁写入的 idle 检查；本轮生产策略 `environmentBlocksFileWrites` 只消费实际快照/更新任务，测试涵盖锁内不自拒绝、常驻服务不等于安装、安装/更新立即拒绝。保留原停止 DSH 的写入约束。

以上是本轮新验证。旧 `9aafbbd7` 的1197项/旧APK/lint结果不作为新 main 验证。未执行 Android 仪器测试，不把坐标点击断言源码的编译当作设备通过。SDK 工具版本/analytics 写入提示和既有 Kotlin 弃用告警未使编译失败，未为消除告警扩大修改范围。

## 未完成与暂停项

- 五块增量与本轮 compile/106项定向回归完成；按本记录范围完成上传检查后，统一一次 commit/push 并创建指定标题 PR，动态 PR/head 以 provider 交付为准。
- 当前 SHA 独立复审与设备 QA 待编排安排；API Key 保存、真实模型/账号及真机验收继续暂停。

## 下一步

1. 编排在新 PR 当前完整 head 上触发独立 Review，并检查 GitHub 全量 CI；本 worker 不自发触发复审，不合并、不发布。
2. 独立设备 QA 在确认没有安装/真实任务后，执行更新后的 `SetupPresentationTest`，检查运行时+文件同时进行、失败保留、物理点击取消、查看器操作栏不被遮挡、深色200%字体设置末项完整可见。
3. 另验安装期间首次进文件页能列目录/预览；顶部/空态/菜单导入、编辑和手机传输给等待提示；空闲写入仍可执行、DSH稳态仍能导入新项目；失败A→改选B后旧编号不能安装A/B，需显式发起新任务。

## 约束与风险

- 保留 #73/#75/#80/#82 的接口与行为，不恢复旧分支整份文件页、主页面或测试报告。
- 写入仍受路径、授权、运行状态和事务互斥保护；只读浏览不因环境安装等待事务锁。
- 不修改共享状态/印章；第一方新增注释中文；不合并、不发布。

## 产物与校验

- 本轮不生成新 app/test APK，不安装、不分发旧输出；只生成 `artifacts/sources/Tanuki-0.1.7-source.zip` 源码检查候选及 `artifacts/SOURCE-SHA256SUMS`，名称沿用日常检查默认值，不代表确认了发布版本。文档收尾后重打归档并复核外部 checksum，不把源码 ZIP 自身哈希写回归档。
- author/committer 使用 `leezzou <115924933+leezzou@users.noreply.github.com>`。
- 已复核本轮相关交接：main 中仅 `20261111-1520-pr77-reopen.md`，没有旧分支的1254记录可清理；1520仍保留生产重试路由RED→GREEN等独有验证依据及未完成设备边界，不判定过期。保留当前指针使用的发布记录与跨 PR 材料，不按日期删文档。本轮无确认过期且属本轮的记录；跨 PR 清理由集成维护方负责。

- 后续文档维护：本功能已由 PR #88 合入 `b324d54dce2727e8e304fa939fb84c143add6b20`；过期记录迁移/引用修复及长期清理规则见 [文档收尾记录](20261111-1802-handoff-cleanup.md)。本记录保留原产品验证事实，不将后补文档检查写成产品重测。
