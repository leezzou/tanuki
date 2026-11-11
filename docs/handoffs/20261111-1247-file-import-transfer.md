# 交接：文件导入命名与手机存储复制/移动

- Agent/会话标识：android_agent-70（AO Worker）
- 时间：2026-11-11，CST
- 状态：实现与本地验证完成；通过原 PR [#75](https://github.com/leezzou/tanuki/pull/75) 交付；独立审查/设备 QA 待安排；未合并、未发布
- 前一记录：[用户旅程审计](20261111-1050-ux-audit.md)

## 用户目标与范围

- 修审计 P0 #4/#5 与 R4.1/R4.2/R4.3：导入可读命名、重名不覆盖、完成提示/刷新/打开；手机条目与批量复制/移动到工作区；目标目录可选；移动先复制校验再删源；冲突三选和全部应用；流式读写与可取消；JVM 回归；中文注释；PR 交付。
- 只改文件页、导入宿主接线与存储层；不改聊天页、RPC、权限清单、共享状态或发布版本。系统手机写权限与 DSH 本次写授权分开，后者不会因文件页移动而开启。
- 不合并、不发布；API Key 保存、真实模型/账号与真机验收仍暂停。设备由独立 QA 独占，本会话未安装应用、未操作设备。

## 当前状态

- 开始时先 fetch，基线为 `origin/main=732cb7965c8c7db378c70edb70f05f5a50ed30d6`；分支 `ao/file-import-move/p0-file-import-move`，原提交 `2200c0901a3aa0b02f310a4e800111d8e5901f84` 已推送并创建 PR #75，两项 CI 成功。
- 开发期间 main 前进到 `8ba7b4b3ecdc82c17a200dca77ef9420924d76b1`。保留本地集成备份，从 PR 完整 head 恢复源分支后普通 merge main，复用已编译的文件页设计令牌冲突解决；保留已推送历史，后续普通推送，不 force-push。
- 已实现：单文件以去最后扩展名的源文件名建立独立项目，多文件 `导入-yyyyMMdd-HHmm`；项目重名用 ` (2)`、` (3)`；项目内重名文件用 `合同 (2).pdf`。长名称按 UTF-8 完整字符截断并保留常见扩展名，悬空链接也视为名称已占用。
- 已实现：顶部填充色「＋ 导入」，搜索/刷新/排序收进更多菜单；空态导入与新建文件夹；菜单「导入为新项目」及不覆盖说明。导入事件使用独立 ID 并随 Activity 保存，成功后刷新列表、显示「已导入 N 个文件到「目录」」+「打开」，一次性「刚刚导入：目录」提供「打开 / 忽略」。查看器中的显式复制也可显示并打开目标。
- 已实现：手机条目与批量「复制到工作区 / 移动到工作区」，默认最近工作区目录；卷根/链接不能传输，不可删除条目禁用移动，旧系统缺写权限时单独申请。目标确认说明移动删源行为。
- 已实现：同名普通文件保留两者/替换/跳过；目录、链接和不同类型条目不替换。全部应用仅作用于尚未处理的冲突。失败保留已完成项，并列出未完成项。
- 已实现：64 KiB 分块复制，回读 SHA-256，核对源清单、文件版本与摘要后才发布；每项发布前检查实际容量预算，移动前/删源前复核系统授权。只清理本次已校验的源条目，新出现的文件不递归删除。取消清理未提交暂存；发布后删源失败保留目标并提示检查。
- `content://` 查看继续直读提供方，不强制复制。现有 SAF 导入 512 MiB 单次限制及 64 MiB 磁盘余量保留。
- 包名：开发验证使用 `com.tanuki.modern.workspacecheck`；未安装 APK。共享 `AGENT-STATE.md` / `CURRENT.json` 不修改。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/ImportNaming.kt` | 文件/项目安全命名、UTF-8 截断、扩展名与序号、唯一项目发布；不覆盖文件/目录/悬空链接。 |
| `app/src/main/kotlin/com/tanuki/storage/TransferEngine.kt` | 流式复制、长度/摘要/源清单校验、同目录暂存发布、移动安全删源与取消清理；纯 JVM 冲突批处理逻辑。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceTransfer.kt` | 真实批量复制/移动执行器，串联冲突三选、进度、部分失败及容量/权限注入检查。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceStorage.kt` | SAF 导入复用流式校验及可读项目命名，保留 journal、容量检查和恢复策略。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt` | 手机源/工作区目标边界、卷根限制、可删除信息、写权限复核和实际容量检查。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt` | R4.1/R4.2/R4.3 交互：一级导入、刷新/打开/忽略、目标选择、冲突及全部应用、移动权限、阶段/耗时/取消。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 导入事件保存/恢复、SAF 大小元数据、文件页独立写权限申请；串联传输进度与取消。 |
| `app/src/test/kotlin/com/tanuki/ImportNamingTest.kt` | 8 项命名与真实发布回归，含后缀、扩展名、中文/emoji 字节边界和悬空链接。 |
| `app/src/test/kotlin/com/tanuki/TransferEngineTest.kt` | 20 项流式复制/移动/取消/容量拒绝/源变化/摘要损坏/真实批量冲突回归。 |
| `app/src/androidTest/kotlin/com/tanuki/LiveProjectTransferTest.kt` | 同步可读项目名期望。 |
| `app/src/androidTest/kotlin/com/tanuki/FileTransferPresentationTest.kt` | 同步菜单路径与可读项目名。 |
| `app/src/androidTest/kotlin/com/tanuki/FilesBrowserPresentationTest.kt` | 同步搜索/排序/刷新菜单路径、验证运行中一级导入入口，新增导入完成反馈/打开用例；仅编译。 |
| `docs/WORKSPACE-STORAGE.md` | 更新命名、刷新、导入入口、手机传输与权限边界。 |
| `docs/TEST-REPORT.md` | 本轮真实验证摘要；与历史/设备证据分开。 |

## 验证记录

环境为 macOS ARM64、项目 JDK17、现有 Android SDK；Gradle 串行运行，无设备任务。

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `./gradlew :app:testModernDebugUnitTest --tests com.tanuki.ImportNamingTest --tests com.tanuki.TransferEngineTest --console=plain` | BUILD SUCCESSFUL；28 tests，0 failures / errors / skipped | 实际执行命名与流式/批量文件操作，不是仅检查策略枚举。 |
| `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:testModernDebugUnitTest :app:lintModernDebug :app:assembleModernDebug :app:assembleModernDebugAndroidTest --console=plain` | BUILD SUCCESSFUL（18m36s）；1188 tests / 120 个报告文件，0 failures / errors / skipped；lint 0 errors / 109 warnings / 1 hint；开发验证包与测试 APK 构建成功 | `com.tanuki.modern.workspacecheck`；只编译，不执行 Android 仪器测试。 |
| 原提交 `2200c09` GitHub Actions | Host scripts 与 Android build/JVM/lint 均通过 | 仅适用于旧 head；最终 head 另行核对，不沿用旧结论。 |
| 早期完整 JVM | 1098/113 文件；main 集成后 1174/120 文件均通过 | 过程结果，不作为最终代码验证；曾出现既有 NPM 线程池时序失败（期望 8，实际 9），单独和完整重跑通过。 |

- 定向覆盖：命名/重名与不覆盖、长 Unicode/扩展名、复制保留源、移动复制期间源仍在、目录递归、目标校验损坏、同大小同时间戳源改写、目录新增、容量拒绝、发布前取消无半成品、发布后删源失败保留副本、流式未知大小、三种冲突实际执行、全部应用与部分失败进度。
- 失败记录保留：最初编译尝试在非挂起文件遍历回调调用挂起 API，已修正；JDK27 不满足工程要求，使用项目内 JDK17；沙箱拒绝共享 Git 元数据和用户 Gradle 缓存写入后，使用同命令一次权限重试。
- 未执行：任何设备/模拟器安装和 Android 仪器测试，真实 SAF 提供方、系统权限弹窗/撤销、深色/大字体/360dp/TalkBack、实际手机 SD/USB 只读卷、进程被杀/断电。宿主合成文件和 Android 源码编译不代替这些验收。

## 未完成与暂停项

- 本地完整验证已完成；交接与 Git 公开文件检查通过（779 个公开候选、0 findings）。最终 head 的 GitHub CI 由 PR #75 provider 检查核对；独立 Review 和独立设备 QA 待安排，旧 head 的批准或 CI 不沿用。
- 设备 QA 为独立会话职责，不将实现 worker 的宿主结果当设备证据；无真实模型配置要求。
- 用户暂停与授权边界不变：API Key 保存、真实模型/账号、真机验收、合并与发布均不执行。

## 下一步

1. 本地候选与普通 PR 交接已完成，以 `leezzou <115924933+leezzou@users.noreply.github.com>` 身份提交，普通推送更新原 PR #75，核对当前 head 的 GitHub CI。
2. 已向编排方请求是否启动 AO 原生独立审查及独立 QA；获得同意后再触发，审查绑定最终完整 SHA，完成前不记为已通过。
3. 独立 QA 检查 SAF 命名与重名、列表刷新与打开、手机权限与只读卷、复制/移动/冲突/取消/删源失败，以及浅深色/大字体/360dp/TalkBack；合并/发布需另行授权。

## 约束与风险

- 目标替换只支持同类型普通文件；目录冲突的批量替换安全回退为保留两者，既有目录内容不会整体删除。
- 目录复制存路径/大小/版本清单，不存文件正文，拒绝链接、特殊文件和超过 128 层目录；超大目录统计和多次摘要核验会增加耗时，阶段/耗时保持可见。
- 移动没有跨应用文件锁；复制期间的源变更会拒绝或停止删源，发布后删源失败会保留目标。应停止其他应用对源的写入；不承诺恶意同 UID 并发或断电条件下的事务隔离。
- 本轮新增/修改的第一方注释与文档使用中文，不引入外部参考实现名称。系统权限与 DSH 原生授权分开，不更改 RPC 或自动开放 AI 写入。

## 产物与校验

- 只产生用于验证的开发 APK 与测试 APK，不覆盖安装、不签发正式包、不创建 tag/Release、不上传附件。开发包沿用 0.1.12/code14，`app-modern-debug.apk` 为 17,247,521 字节，SHA-256 `4fde01ef31fc2f2d71f05c37a106ef9ebb2b43c42389312e342dc065ffe5c413`；包名由输出元数据确认，不把它当作正式签名候选。
- 源码候选包检查只在系统临时目录的公开源码副本运行 `scripts/package-source.py`，核对 `SOURCE-SHA256SUMS` 并执行 `scripts/check-publication.py --archive`；不把生成物、日志或本机配置纳入 PR，不向用户交付临时 ZIP。
- 过期交接核对：审计是本任务验收依据，文件页布局记录仍含未完成设备 QA，故保留；未确认本范围内有可删除的过期独有记录，不跨 PR 清理。当前唯一交接修订在本文件，不新增重复记录。
- 普通 PR 不刷新共享状态或印章。提交前执行：`python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1247-file-import-transfer.md`、`python3 scripts/check-publication.py --git`、`git diff --cached --check`；暂存区已解决冲突，普通 merge 的目标为 `8ba7b4b3ecdc82c17a200dca77ef9420924d76b1`。源码包与 CI 结果另在 PR 验证记录核对，未获独立审查/设备 QA 不能合并。
