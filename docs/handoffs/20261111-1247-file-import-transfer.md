# 交接：文件导入命名与手机存储复制/移动

- Agent/会话标识：android_agent-70（AO Worker）
- 时间：2026-11-11 12:47 CST
- 状态：已完成实现与本地非设备验证；PR 待创建/独立审查，未合并、未发布
- 前一记录：[用户旅程审计](20261111-1050-ux-audit.md)

## 用户目标与范围

- 用户本次实际要求：修 P0 #4/#5（文件导入生成随机名、列表不刷新跳转；手机存储不能复制/移动到工作区），依据已合并审计 `docs/handoffs/20261111-1050-ux-audit.md` 第 4/5 条与 R4.1/R4.2/R4.3；基于 `origin/main` 最新 main 建分支（先 fetch）。
- 本次包含：导入目标命名（源文件名/时间戳、重名 (2)/(3)、不覆盖）、导入完成 Snackbar + 打开 + 列表刷新；手机存储条目菜单与批量条新增复制/移动到工作区；目标目录默认最近工作区目录且可改；移动先复制并逐文件 SHA-256 校验成功再删源；进度可见可取消；同名冲突保留两者/替换/跳过与批量全部应用；仅改文件页与存储层；补 JVM 回归；中文注释；PR 交付。
- 本次不包含：聊天页、协议/RPC、权限/安全边界、发布版本号、正式签名、APK/源码包发布、合并。
- 用户改变或暂停的要求（需保留）：不合并、不发布；API Key 保存问题仍暂停，本轮未触碰；设备由独立 QA 独占，本轮未申请设备或模拟器。

## 当前状态

- 已实现：导入命名、导入完成事件与 Snackbar、手机→工作区复制/移动、冲突三选与批量应用、流式校验与取消清理、容量/剩余空间检查。
- 已验证：完整 modernDebug JVM 单测、定向新增回归、lint、modernDebug assemble、仪器测试源码两套配置编译；均为本地非设备结果。
- 待验证：真实设备/模拟器上的 SAF 选择器、手机存储授权与复制/移动、进度/取消、深色/大字体/360dp/TalkBack；独立 Review、GitHub CI、合并与发布。
- 当前目标包名、分支、任务：`com.tanuki`；分支 `ao/file-import-move/p0-file-import-move`，基于 `origin/main=732cb79`（`feat(onboarding)` #67），会话根分支 `ao/file-import-move/root`；无运行中的设备任务、未安装 APK。
- 关键事实与可定位源码/公共文档：命名与冲突纯逻辑 `app/src/main/kotlin/com/tanuki/storage/ImportNaming.kt`、`TransferEngine.kt`；导入落点 `WorkspaceStorage.kt`；跨位置传输 `WorkspaceFileManager.kt`；页面 `WorkspaceFilesPage.kt`；宿主事件 `MainActivity.kt`；JVM 回归 `ImportNamingTest.kt`、`TransferEngineTest.kt`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/ImportNaming.kt` | 新增纯 JVM 命名规则：单文件取源文件名去最后扩展名，多文件用 `导入-yyyyMMdd-HHmm`；过滤路径分隔符/控制字符/尾随点、限制 UTF-8 字节；重名追加 ` (2)`、` (3)`；项目名去掉前导点，避免导入后不可见。 |
| `app/src/main/kotlin/com/tanuki/storage/TransferEngine.kt` | 新增流式复制校验引擎：64 KiB 分块、写后回读 SHA-256、大小一致才发布；暂存目录内完成后移动，取消/失败清理；移动仅在发布后删源，删源失败保留目标并报错；批量冲突策略与“全部应用”纯逻辑，替换策略不作用于目录。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceStorage.kt` | `importFiles` 增加可选项目名；最终目录不再使用 `import-<uuid>`，改为可读名并原子选择未占用后缀；同批同名源文件在项目内自动改名，不再整批失败；保留 `.import-<uuid>` 仅作隐藏暂存与崩溃恢复。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt` | 新增 `workspaceConflicts` 预检与 `transferToWorkspace`：手机存储→工作区复制/移动，逐项冲突策略、目录递归、真实字节进度、容量/剩余空间检查、取消清理；移动先校验后删源。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt` | 导入完成事件去重、列表刷新、Snackbar「已导入 N 个文件到「目录」」+「打开」；手机条目菜单/批量条新增复制/移动到工作区；目标目录默认最近工作区目录且可改；冲突三选对话框与全部应用；空态导入入口；菜单文案改为「导入为新项目」；手机权限说明补充只读边界。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 导入完成投递一次性 `WorkspaceImportEvent`；外部文件显式复制到工作区同样投递；文件页接入手机→工作区传输，复用 `FileTransfers` 进度与取消。 |
| `app/src/test/kotlin/com/tanuki/ImportNamingTest.kt` | 新增 5 项 JVM 回归：单文件命名、多文件时间戳、重名 (2)/(3) 且不改已有、用户可编辑名清洗、长中文名 UTF-8 字节上限。 |
| `app/src/test/kotlin/com/tanuki/TransferEngineTest.kt` | 新增 9 项 JVM 回归：复制保留源、移动复制校验期间源仍在且成功后删除、失败/取消不留暂存与目标、目录递归移动、替换文件、拒绝替换目录、批量全部应用、批量替换不作用于目录。 |
| `app/src/androidTest/kotlin/com/tanuki/LiveProjectTransferTest.kt` | 导入目录期望从 `import-<uuid>` 改为源文件名。 |
| `app/src/androidTest/kotlin/com/tanuki/FileTransferPresentationTest.kt` | SAF 往返按新命名匹配项目目录；菜单文案改为「导入为新项目」。 |
| `app/src/androidTest/kotlin/com/tanuki/FilesBrowserPresentationTest.kt` | 同步导入菜单文案。 |
| `docs/TEST-REPORT.md` | 记录本轮验证范围、命令、结果与未验证项。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git fetch origin --prune` | 成功；`origin/main=732cb79` | 共享 `.git` 需放宽沙箱；fetch 后 main 未前进 |
| `./gradlew testModernDebugUnitTest --tests com.tanuki.ImportNamingTest --tests com.tanuki.TransferEngineTest` | BUILD SUCCESSFUL，14 tests / 0 failures | JDK17（项目内置下载器，`.build/jdk`）；定向回归 |
| `./gradlew testModernDebugUnitTest` | 第一次完整运行有一个既有 `NpmAdaptiveWorkerPoolTest` 时序失败（8 vs 9）；单独重跑该用例与完整套件均 BUILD SUCCESSFUL；**1098 tests / 113 个结果文件 / 0 failures / 0 errors / 0 skipped** | 失败未抹掉；与本改动文件无调用关系；完整 modernDebug JVM，未使用设备 |
| `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:compileModernDebugAndroidTestKotlin` | BUILD SUCCESSFUL（1m30s） | 仅编译仪器测试源码，未安装、未运行；不含设备证据 |
| `./gradlew assembleModernProbeAndroidTest` | BUILD SUCCESSFUL | 仪器测试源码在默认 probe 配置下也可编译；未运行 |
| `./gradlew lintModernDebug` | BUILD SUCCESSFUL；0 errors / 109 warnings / 1 hint | lint 不替代设备或人工验收 |
| `./gradlew assembleModernDebug` | BUILD SUCCESSFUL；`app-modern-debug.apk` 17,559,104 字节，SHA-256 `a7bda140...` | 开发 debug 包，非发布候选；未安装 |
| `git diff --check` | 通过 | 无空白/冲突标记问题 |

- 哪些是前一轮结果，哪些是本次新验证：本次新验证为上述 JVM、lint、assemble、仪器测试源码编译；未复用 0.1.12 发布轮的 1044 tests 等历史数字。
- 未测试、超时、失败和暂未复现的情况：真实设备/模拟器上的 SAF 多选与保留权限、手机存储授权/撤销、复制/移动进度与取消、移动删源失败场景、同名冲突三选与全部应用、深色/大字体/360dp/TalkBack、隐藏文件与符号链接边界；Android 仪器测试未运行；GitHub CI 与独立 Review 未执行。

## 未完成与暂停项

- 未完成：创建 PR、独立 Codex High/人工审查、GitHub CI 结果核对；PR 合并需另行授权。
- 外部阻塞：设备 QA 由独立 QA 独占，本会话未申请；真实 SAF 提供方与厂商 ROM 行为未提供。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收、合并与发布；本轮均未触碰。

## 下一步

1. 在当前分支提交并创建 PR（目标 `main`），正文写明命名规则、交互流程、测试结果与未验证项；不合并、不发布。
2. 独立 Reviewer 对当前 head 做审查；CI 在 PR 页面复跑 JVM/lint/assemble。
3. 独立 QA 在授权设备上验证：SAF 导入命名/重名、导入 Snackbar 打开与刷新、手机存储复制/移动/取消/冲突三选/全部应用、移动删源失败保留目标、深色/大字体/360dp/TalkBack。

## 约束与风险

- 数据/设备/权限边界：只操作工作区与合成临时目录；未读取真实 Key/Cookie/token/会话；未安装 APK、未操作设备、未改共享状态或权限策略。
- 关键决策及理由：导入最终目录按源文件名/时间戳命名并用 ` (2)` 后缀，避免 UUID 与覆盖；移动采用“暂存→流式复制→回读 SHA-256→发布→删源”，把丢文件风险降到最低；手机存储文件页仍只读，复制/移动显式确认，避免把 DSH 写授权误解为文件页可写。
- 失败尝试与避免重复踩坑的结论：`File.walkFileTree` 回调不是挂起上下文，改为递归 suspend 遍历并在每项检查取消；批处理“全部替换”曾会把同名目录也标记为替换，现只作用于普通文件，目录回退保留两者。
- 风险：长文件名按 220 字节截断可能丢失末尾扩展名；目录复制会二次遍历统计总量，超大目录开始前有额外延迟；冲突预检与实际写入之间若外部进程新建同名文件，KEEP_BOTH 会再改名、REPLACE 仍受“目录不替换”保护；移动删源失败会同时保留源与目标，需要用户手动清理源。
- 未覆盖：R4.3 中把导入提升为顶部一级图标并重排搜索/排序的布局改造本轮未做；仅在空态增加“导入文件”按钮并将菜单改为“导入为新项目”。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：仅生成未安装的 `app-modern-debug.apk` 用于编译核对；未生成源码 ZIP、未更新发布目录、未创建 tag/Release。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未覆盖、未发布；debug 包不是正式签名候选。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：未删除旧交接；本轮只新增本记录并补 `docs/TEST-REPORT.md`，旧记录事实仍在原文件。
- 普通 PR 不刷新共享状态/印章；`docs/AGENT-STATE.md`、`docs/handoffs/CURRENT.json` 未修改，集成维护方在合并后统一运行 `--integrate`。

结束前实际执行：

```bash
python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1247-file-import-transfer.md
python3 scripts/check-publication.py --git
git diff --check
```
