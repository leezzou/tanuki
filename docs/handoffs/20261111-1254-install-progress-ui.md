# 交接：20261111-1254-install-progress-ui

- Agent/会话标识：android_agent-73
- 时间：2026-11-11（Asia/Shanghai）
- 状态：PR #77 追加更新页/底部导航/安装期浏览和两条审查修复已完成实现与 Host 验证，交付同一 PR；当前 head 独立复审/设备 QA 待完成，未合并、未发布
- 前一记录：[远程连接 30 秒上手](20261111-1213-quickstart-30s.md)

## 用户目标与范围

- 用户本次实际要求：收敛全局安装进度呈现，移除文件页和其他页面顶部的常驻安装进度卡；有可信总量时显示真实百分比，未知总量使用不定进度与“正在准备”，阶段使用人话并提供大致剩余时间；文件页空态补图标、说明、“从手机导入 / 新建文件夹”与导入进度行；复用 `TanukiStateView`，补 JVM 回归。
- 本次包含：全局细进度浮层和底部抽屉，进度/阶段/剩余时间纯展示模型，绑定原操作参数的一次性失败重试，文件页三态及导入结束刷新，适用测试与文档。
- 本次不包含：远程页重做（由其他任务处理，没有修改 `RemoteHostSettings.kt`），API Key、真实模型/账号；合并、发布、tag/Release。
- 用户保留要求：从 fetch 后核实的 `8ba7b4b3` 建分支；不合并、不发布；新增或修改的第一方代码注释为中文；提交 author/committer 为 leezzou；文档/注释不出现外部参考实现名称。

## 追加范围与审查修复

- 继续同一 [PR #77](https://github.com/leezzou/tanuki/pull/77)，已 claim；fetch 后按 provider 核实的 `4474f6a25d76990363fd653ba13a9b616505ec49` 对齐 `ao/install-progress/global-progress`，无用户未提交改动。
- 编排追加：应用更新页主体用简短说明，安装约束收进“了解详情”；检查更新改主按钮，当前版本旁显示可信检查状态点；外层 Scaffold 统一预留导航和系统栏，保证设置页末项能完整滚出；安装期间允许文件列表和预览，写入立即说明原因。
- 审查 `PRRT_kwDOVgRm4s62DIk8`：文件页使用唯一进度宿主，运行时和文件任务共用抽屉；不再把全局入口盖在文件传输入口上，按实际查看器操作栏高度避让。补运行/失败与文件任务并存的几何及真实坐标点击源码回归。
- 审查 `PRRT_kwDOVgRm4s62DIlE`：版本选择先使旧重试失效，再保存新目标；失败原因保留，抽屉返回组件入口重新选择。新增真实版本目录 + 延迟读取选择 + 实际重试注册器的 A 失败 → 选 B → 点旧重装回归，覆盖 Ubuntu/Node。
- 文件页阻塞根因：只读 `WorkspaceFileManager.io` 也等安装 `mutation` 锁。读操作改走文件锁，写操作在等待事务前和执行前各复核，目录/预览不等安装结束；UI 与权限说明同步提示暂停写入。
- 追加修复 Host 验证已通过：同一组合命令 BUILD SUCCESSFUL（11m39s），app/test debug APK 和仪器源码编译通过，JVM 1197 项 / 125 suites / 0 failures / 0 errors / 0 skipped，相关 57 项通过；lint 0 errors / 109 warnings / 1 hint。
- 新 APK 实核为 `com.tanuki.modern.workspacecheck`、0.1.12/code14、minSdk26/targetSdk34；19,387,409 字节，SHA-256 `c35ba8aaa239bc76edf6535cd7abe2d7692bfb13935ed4d86404800d1b3cd089`。test 包 `com.tanuki.modern.workspacecheck.test`，instrumentation target 为该 app；未安装、未分发。
- 此前 1182 项 JVM 和 `0c4125b9…` APK 哈希保留为首提交 `4474f6a` 的历史结果；追加修复以本节为准，不能混用。
- 设备由独立 QA 使用，本实现会话不启动或操作模拟器/真机、不运行 adb；当前 head 的独立 review 由编排者触发，本会话不自行启动。

## 待设备复验清单

1. 320/360dp、浅色/深色、100%/200% 字体：设置首页滚到底，“关于与诊断”整行在导航上方完整可见且能点击；文件、远程、应用更新和浏览器最后一项同样可达。
2. 任务开始/结束前后文件内容区域边界相同；有/无可信总量分别显示真实百分比/不定进度，不出现分段误导。
3. 运行时进行中或保留失败时，同时导入/导出文件：只显示一个入口；查看器导出/打开栏保持完整可点；入口打开的抽屉内能找到并取消文件任务，不误触运行时取消/重试。
4. 抽屉取消实际下载/解压/安装任务并等待安全清理；失败重试原操作。A 失败后选择 B，旧重试不得静默安装或重装 B，必须从组件入口重新发起。
5. 空目录“从手机导入 / 新建文件夹”均有回调；导入中显示真实字节和取消；小文件瞬间完成后自动刷新 `/workspaces`，失败/取消后显示磁盘真实内容。
6. 安装持有环境事务期间，工作区可切目录、搜索、打开预览；新建/编辑/导入显示等待写入提示，不能挂住整页或排队后静默写入。反复点击仍有明确提示。
7. 应用更新页：检查更新为主按钮；主体说明一句人话，工程约束默认折叠；“了解详情”可展开/收起；已是最新/有新版本由真实检查结果决定，未检查不冒充最新，自动检查时间为次要说明。
8. TalkBack 可聚焦唯一进度入口，读出阶段与百分比/未知状态，抽屉关闭/取消/返回组件按钮可用；全屏与平板避让正确。

仪器源码 `runtimeAndFailureShareFileProgressHostAndKeepViewerActionsClickable`、`lastSettingsRowIsFullyVisibleAboveNavigationInDarkLargeFonts` 与既有进度/空态用例用于上述验证；本轮只编译，未在 Android 执行。

## 当前状态

- 开始基线：`origin/main=8ba7b4b3ecdc82c17a200dca77ef9420924d76b1`，工作区干净；起始分支 `ao/install-progress/root`，交付分支 `ao/install-progress/global-progress`。
- 已实现：移除 `TanukiUi` topBar 的 `OperationBanner`，统一细进度入口作为内容区浮层放在导航上方，不参与 topBar/bottomBar 测量；全屏和平板保留入口。点击打开 Material3 底部抽屉，显示阶段、已用时间、剩余时间、原始补充说明、取消、失败原因和重试。
- 进度语义：明确总量或可信阶段计数才显示真实百分比；未知总量显示连续不定进度与“正在准备”；无分段间隙/终点。下载速度可用时按剩余字节估时，否则“预计还需要几分钟”。失败耗时冻结，失败后残留的安装阶段不会继续冒充运行中。移除动画设置下保留静态状态，进度条提供标准无障碍进度语义。
- 重试：互斥运行时操作、DSH 安装/备用程序和版本检查保存本次回调；重试绑定任务编号，开始新操作或点击一次后旧入口失效。DSH 安装保留 release/reinstall 参数；切换失败继续走原恢复检查。缺失原参数的历史失败返回组件入口，避免误触全量初始化。
- 文件空态清单：可写空目录（文件夹图标、说明、导入/新建双按钮）；搜索无结果（清除搜索）；只有隐藏文件（显示隐藏文件）；只读空位置（刷新）；加载中；列表错误（重试）；手机存储未授权（授权说明/按钮）。全部复用 `TanukiStateView`。DSH 运行中继续禁用新建，导入仍可用。
- 导入中：明确 `FileTransferKind.IMPORT`，空态区域显示真实传输行、字节、剩余时间与取消；普通网页下载不会被称为导入。导入结束（含取消/失败）通过保留最新值的完成序号刷新当前工作区目录，即使小文件开始/结束被任务列表合并也不漏刷新，不按每个字节重组列表。加载/排序暂无结果时显示加载态，大字体状态区域可滚动。
- 当前目标包名：`com.tanuki.modern.workspacecheck`，仅 debug 验证；本会话不操作设备，无安装任务。
- 已验证（追加修复）：BUILD SUCCESSFUL，app/test debug APK 构建与仪器源码编译通过，JVM 1197 项 / 125 个结果文件 / 0 failures / 0 errors / 0 skipped，相关 57 项通过；lint 0 errors / 109 warnings / 1 hint。设备未验证；首提交结果另列为历史。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/OperationProgress.kt` | 细进度浮层、连续进度条、底部详情抽屉；耗时、阶段、失败、取消和重试。 |
| `app/src/main/kotlin/com/tanuki/ui/OperationProgressPresentation.kt` | JVM 可验证的阶段、确定/不定进度、失败和时间文案映射。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 移除顶部常驻卡，单一全局浮层不参与页面/导航测量；旧失败回组件入口。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiStateViews.kt` | 通用图标、双按钮、按钮启用状态与进度内容插槽，保持空/错/载风格一致。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiPageVisuals.kt` | 空态文案、双按钮命令及真实回调分派。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt` | 空态双按钮、导入进度行、导入结束刷新、排序等待态、权限空态复用和大字体滚动。 |
| `app/src/main/kotlin/com/tanuki/ui/FileTransferPresentation.kt` | 只选择明确标记的导入任务。 |
| `app/src/main/kotlin/com/tanuki/runtime/FileTransfers.kt` | 增加导入类型，保留普通传输默认行为和节流。 |
| `app/src/main/kotlin/com/tanuki/runtime/OperationRetry.kt` | 一次性、编号绑定的原操作重试与冻结失败详情。 |
| `app/src/main/kotlin/com/tanuki/runtime/OperationProgress.kt`、`State.kt`、`RuntimeManager.kt` | 失败记录、互斥操作重试与失败后活动状态修正。 |
| `app/src/main/kotlin/com/tanuki/update/UpdateManager.kt` | 保存失败 DSH 安装、备用程序及版本检查的原参数供重试。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 导入任务类型标记及受编号约束的重试接线。 |
| `app/src/test/kotlin/com/tanuki/OperationProgressPresentationTest.kt`、`OperationProgressLayoutTest.kt`、`OperationRetryTest.kt`、`FileEmptyStateTest.kt`、`TanukiPageVisualsTest.kt` | 进度语义/阶段/估时/失败冻结、常驻卡移除、重试失效与空态回调回归。 |
| `app/src/androidTest/kotlin/com/tanuki/SetupPresentationTest.kt` | 更新抽屉交互断言，补任务前后文件页内容区域不变的布局回归；本轮只编译。 |
| `docs/COMPONENTS.md`、`docs/WORKSPACE-STORAGE.md`、`docs/APP-UPDATES.md`、`docs/TEST-REPORT.md`、本记录 | 行为说明、实际验证与设备未测边界。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeVersionSelection.kt` | 实际版本选择入口在保存前失效旧安装重试，保留失败详情并返回组件入口。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceIoGate.kt`、`WorkspaceFileManager.kt` | 浏览不等待安装事务；写入在排队前与执行前复核。 |
| `app/src/main/kotlin/com/tanuki/ui/FileOperationOverlay.kt`、`FileViewerLayout.kt`、`WorkspaceFileViewer.kt`、`FileAccessPresentation.kt` | 唯一浮层宿主、查看器实测高度避让、安装时只暂停写入与明确提示。 |
| `app/src/main/kotlin/com/tanuki/ui/ApplicationUpdateCard.kt`、`ApplicationUpdatePresentation.kt` | 更新页短文案/折叠详情/主按钮/可信版本状态点。 |
| `app/src/test/kotlin/com/tanuki/RuntimeVersionSelectionTest.kt`、`WorkspaceIoGateTest.kt`、`ApplicationUpdatePresentationTest.kt`、`FileViewerLayoutTest.kt`、`FileAccessAndTransferPresentationTest.kt` | 追加版本真实调用链、事务锁读写边界、更新页状态、导航/浮层几何及安装写入说明回归。 |

## 验证记录

下表保留首提交及中途检查；追加修复的 1197 项 JVM、125 个结果文件与新 APK 见“追加范围与审查修复”。每轮收尾均重跑交接、实际暂存/归档检查并核对源码 checksum；最终 provider CI 与线程结果见会话交付报告。

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git fetch origin --prune`、`git rev-parse HEAD origin/main` | 通过，开始时二者均为 `8ba7b4b3ecdc82c17a200dca77ef9420924d76b1` | Git 元数据在 worktree 外，fetch 和建分支使用一次性获准的 Git 写权限 |
| `source scripts/ao-worktree-env.sh`、`./gradlew --version` | 通过，JDK17 / Gradle8.13 / macOS ARM64 / SDK36 | 缓存和 Android 构建可写状态在 worktree 内，不修改全局 Gradle/SDK 配置 |
| `... :app:compileModernDebugKotlin` | 初版通过 | 首次缺少 Close 图标导入，补齐后通过 |
| `... :app:testModernDebugUnitTest --tests com.tanuki.OperationProgressPresentationTest --tests com.tanuki.FileEmptyStateTest --tests com.tanuki.OperationProgressLayoutTest --tests com.tanuki.TanukiPageVisualsTest` | 初版 26 项通过 | 初次 1 项空态文案断言失败，统一文案后复跑通过；不冒充最终全量结果 |
| `... :app:compileModernDebugAndroidTestKotlin` | 初版通过 | 只有源码编译，未在 Android 执行 |
| `... :app:testModernDebugUnitTest :app:compileModernDebugAndroidTestKotlin` | 中途编译失败 | 细入口改浮层时混用了 padding 重载参数，已拆成两次 padding；失败未计通过 |
| 两次中途 app/test APK + JVM/lint 候选调用 | 主动中止，未计通过 | 分别为补取消/恢复状态边界、小文件导入完成通知竞态而停止；最终验证重新运行 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1254-install-progress-ui.md` | 通过 | 中途结构检查；收尾后复跑 |
| `python3 scripts/check-publication.py --git` | 通过，0 findings | 中途及收尾候选均检查，实际暂存内容在提交前再次检查 |
| `python3 scripts/package-source.py`、`python3 scripts/check-publication.py --archive artifacts/sources/Tanuki-0.1.7-source.zip` | 通过，780 个公开源文件，归档 0 findings | 脚本日常默认归档名，不代表发布版本；没有分发原生二进制 |
| 从 `artifacts` 目录执行 `shasum -a 256 -c SOURCE-SHA256SUMS` | 通过，OK | 首次从仓库根目录执行因找不到清单失败，改正确目录后通过；收尾重新生成后再核对 |

- 首提交 `4474f6a` 的组合命令：`./gradlew --no-daemon --max-workers=2 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:assembleModernDebug :app:assembleModernDebugAndroidTest :app:testModernDebugUnitTest :app:lintModernDebug`。最终调用 BUILD SUCCESSFUL；app/test debug APK 构建和 JVM 1182 项 / 122 个结果文件 / 0 failures / 0 errors / 0 skipped，定向 34 项通过；lint 0 errors / 109 warnings / 1 hint。
- 首提交 app APK 实核：`com.tanuki.modern.workspacecheck`、minSdk26/targetSdk34，SHA-256 `0c4125b9e5eaae687f218466e31a6c663808c1ac47025c29cb7800ce5b756a34`；test 包 `com.tanuki.modern.workspacecheck.test`，instrumentation target 为该 app。未安装，非正式签名。
- 验证边界：初版结果仅属中途检查；1182 是首提交 `4474f6a` 的结果，追加修复 1197 项见“追加范围与审查修复”。不使用其他 PR 的测试数字；新增仪器用例只编译，不计设备通过。
- 未测试范围：独立 Android 设备/模拟器 UI、真实安装/更新/取消/重试、SAF 文件选择和导入、深色/大字体、TalkBack、全屏与平板位置/手势；真实模型/账号继续暂停。

## 未完成与暂停项

- Host 验证已完成，交付分支为 `ao/install-progress/global-progress`；PR 链接和 provider CI 结果由本会话交付报告提供。
- 未完成：当前完整 head SHA 的独立 Codex High 审查和独立 Android QA；没有这些证据不能宣称满足合并门禁。
- 外部阻塞：无实现阻塞；独立设备 QA 需由编排者安排，不能用实现会话的 Host 结果替代。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；不合并、不发布。

## 下一步

1. Host 全量验证已完成；收尾后按明确路径暂存，以 leezzou 身份统一提交推送并创建 PR（结果见会话交付报告），不合并、不发布。
2. 追加修复推送后回复并解决两条已修复线程，把最新完整 head 回报编排者，由编排者触发独立复审。本会话不自行触发；QA 释放设备后按“待设备复验清单”统一复测。
3. 若审查产生修复，绑定新 SHA 重跑受影响验证；不沿用旧批准，不代合或发布。

## 约束与风险

- 未读真实凭据、未操作设备、未改网络校验或安装/文件写入保护；未知总量不伪造百分比。
- 当前仍为非设备验证。顶部常驻卡已移除，但浮层的实际遮挡、窄屏/大字体触控、底部系统栏和全屏避让仍需独立 QA；宿主构建不等于 Android 运行验收。
- 重试回调仅保存在本进程内存，应用重建后通过组件入口重新选择，不自动重放安装；原有恢复事务路径保留。
- 失败尝试：标准 Gradle 环境会写全局缓存而被沙箱拦截，改用项目现有 worktree 构建脚本；缺少图标导入、文案断言不一致和 padding 参数错误均据真实输出修正。

## 产物与校验

- 本轮仅构建开发 debug 验证包，不覆盖安装，不修改版本/签名，不创建 tag/Release，不上传 APK。
- 源码候选归档仅用于上传前检查，不作为发布附件分发；完成文档后重新生成并核对校验清单。
- 过期记录核对：保留文件页布局、传输胶囊避让、设计令牌和发布交接，它们仍有独有验证事实或未完成 QA；本范围内无满足删除条件的过期记录，不跨 PR 清理。
- 普通 PR 不修改 `docs/AGENT-STATE.md` 或 `docs/handoffs/CURRENT.json`，共享状态/印章由集成维护方刷新。
