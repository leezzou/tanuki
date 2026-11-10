# 交接：20261110-0418-saf-direct-open

- Agent/会话标识：android_agent-39（DSH Worker）
- 时间：2026-11-10 04:18 UTC
- 状态：部分完成（3 项 Review P2 已修复并本地验证；PR #37 等待新 head CI/复查，设备验证未执行）
- 前一记录：[0.1.10 Pre-release 发布准备](20261109-2310-release-0.1.10.md)

## 用户目标与范围

- 用户本次实际要求：改进 Tanuki 文件管理，从手机/SAF 打开文件时不再强制先复制到工作区；已有内置查看器支持的类型直接使用 `content://`，申请并持久化读权限，处理旋转/进程重建/URI 失效；大文件流式/按需读取并保留安全边界；不支持的类型保留显式复制回退；补单元/回归测试；PR 交付，不合并、不发布。
- 本次包含：外部 `content://` 文档源、内置查看器统一抽象、系统「打开方式/分享」与文件页「打开手机/其他应用文件」入口、持久读权限、Activity 状态恢复、显式复制回退、文本顺序流/随机读分页、PDF 直读描述符、DOCX/XLSX 私有缓存暂存、路径/URI 校验和大小上限、JVM 回归。
- 本次不包含：真机/模拟器验证、真实 SAF 提供方与权限撤销验证、assemble/仪器测试、PR 创建/合并/发布；不修改 DSH 官方包、不改变手机存储写授权或浏览器/DSH 挂载边界。

## 当前状态

- 已实现：外部 content URI 直开与回退逻辑、查看器文档源抽象、文本分页流式读取、PDF/图片/DOCX/XLSX/音视频直开接线、Activity 进程重建描述恢复、系统 ACTION_VIEW/ACTION_SEND 过滤器与 `singleTop`。
- 已验证：`compileModernDebugKotlin`；rebase 到 `origin/main=7593805` 后完整 `testModernDebugUnitTest` 618 tests / 0 failures / 0 errors / 0 skipped；`lintModernDebug` 0 errors / 103 warnings / 1 hint；新增/扩展回归见下。
- 待验证：真实设备/模拟器 SAF 提供方、系统「打开方式/分享」、真实 `takePersistableUriPermission` 与撤销、旋转/进程被杀、权限失效后重新选择、真实大文件/低内存/弱网、DOCX/XLSX 私有缓存清理、音视频直开。
- 当前分支：`ao/saf-file-open/root`；原基线 `0555c3c0eaeeed181269b5e68733ccfdd48d9e75`；已 rebase 到 `origin/main=7593805`（PR #42 交接 PR 模式，含 #34/#32/#35/#36）；独立 worktree；PR [#37](https://github.com/leezzou/tanuki/pull/37) 待审。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/ExternalDocumentPolicy.kt` | 新增纯 Kotlin 外部 URI 校验、显示名清洗、类型大小上限、失败分类、描述往返、读取代次 `openToken`/`contentKey`、已消费 Intent 身份与 `BoundedCopy` 有界流复制。 |
| `app/src/main/kotlin/com/tanuki/storage/ExternalDocumentAccess.kt` | 新增 Android 元数据查询、`takePersistableUriPermission` 申请与异常中文映射。 |
| `app/src/main/kotlin/com/tanuki/storage/ContentDocument.kt` | 新增 `content://` 文档源：文本随机读/顺序流、图片降采样、PDF 描述符、DOCX/XLSX 私有缓存暂存、导出与直接转发 URI；元数据读取/外部打开不再被内置预览大小上限阻断。 |
| `app/src/main/kotlin/com/tanuki/storage/DocumentAccess.kt` | 新增 `OpenDocument` 接口与工作区适配器，查看器不再强依赖 `WorkspaceFileManager + path`。 |
| `app/src/main/kotlin/com/tanuki/storage/TextFileReader.kt` | 支持 `SeekableInput`；文件/描述符随机读与纯顺序流共用分页逻辑；表头字节作为首页前缀，避免顺序流回退；未知大小支持翻页。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt` | 文本 revision 判断兼容未知大小；分类委托合并后的 `FileTypeCatalog`。 |
| `app/src/main/kotlin/com/tanuki/storage/FileTypeCatalog.kt` | 在 main 的扩展名/MIME 分类上补充 PDF/DOCX/DOC/XLSX 的纯 MIME 识别，供无扩展名的 content URI 直开。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 接入外部 URI/`ACTION_VIEW`/`ACTION_SEND`/SAF 选择器、持久权限、Activity 保存恢复、文件页跳转、外部导出与显式复制回退；记录已消费启动 Intent，重建时不重放旧打开请求。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | 改为接收 `OpenDocument`；显示非持久授权提示、URI 失效/重新选择/复制回退；外部文件底部保留复制入口。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt` | 新增「打开手机/其他应用文件」菜单、外部文档查看分支与工作区文档适配。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspacePdfPreview.kt`、`WorkspaceDocxPreview.kt`、`WorkspaceXlsxPreview.kt` | 改为接收 `OpenDocument`，直开外部描述符/流，不要求工作区路径。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 新增 `filesOpenRequest`，外部文件到达时切换到文件页。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfMemoryPolicy.kt` | PDF 大小上限复用外部策略常量，避免两条入口不一致。 |
| `app/src/main/AndroidManifest.xml` | 注册 `content://` 的 VIEW/SEND 过滤器（PDF/图片/文本/DOCX/XLSX/DOC/XLS），`singleTop`。 |
| `app/src/test/kotlin/com/tanuki/ExternalDocumentPolicyTest.kt` | 新增 URI 校验、显示名、直接打开/复制回退、大小上限、持久/非持久授权、URI 失效、描述往返、`BoundedCopy` 回归。 |
| `app/src/test/kotlin/com/tanuki/TextFileReaderTest.kt` | 新增可随机读页窗口、纯顺序流翻页、未知大小回归。 |
| `app/src/test/kotlin/com/tanuki/WorkspaceFileKindTest.kt` | 新增无扩展名 MIME 分类回归。 |
| `app/src/main/kotlin/com/tanuki/ui/TextPageLayout.kt` | 新增未知总大小时按 `hasMore` 翻页的重载，保留 4 MiB 显示上限。 |
| `docs/handoffs/20261110-0418-saf-direct-open.md` | 本记录；按 [交接机制](../HANDOFFS.md) 普通 PR 只新增自己的记录，不修改 `AGENT-STATE.md`/`CURRENT.json`，共享状态由集成方统一刷新。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin` | BUILD SUCCESSFUL | 宿主 macOS，JDK17，SDK36，跳过 `buildNativeRuntime`；只有既有弃用/平台告警。 |
| `./gradlew ... :app:testModernDebugUnitTest :app:lintModernDebug` | BUILD SUCCESSFUL；74 个结果文件，618 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 103 warnings / 1 hint | 完整 JVM 与 lint；不是设备/仪器验证。 |
| `./gradlew ... :app:compileModernDebugAndroidTestKotlin -x buildNativeRuntime` | BUILD SUCCESSFUL | 修正 `WorkspaceVideoPresentationTest` 对旧查看器签名的调用；仅编译，未运行仪器测试。 |
| `./gradlew ... :app:testModernDebugUnitTest --tests 'com.tanuki.TextFileReaderTest' --tests 'com.tanuki.ExternalDocumentPolicyTest' --tests 'com.tanuki.BoundedCopyTest' --tests 'com.tanuki.WorkspaceFileKindTest'` | BUILD SUCCESSFUL | 新增/扩展目标回归全部通过。 |
| rebase 到 `origin/main=7593805` 后 `:app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin` | BUILD SUCCESSFUL | 解决 `WorkspaceFileManager`/`WorkspaceFileViewer` 与 main 的 `FileTypeCatalog`、行号/截断 UI 冲突；Android 测试源码同时编译。 |
| 代码审查（本地 diff） | 进行中 | 已检查主流程、权限/回退、流读取、缓存清理和取消传播；未触发 AO Review。 |
| GitHub Actions `verify`（PR #37，首个 head `21677ab`） | Android job 失败 | `WorkspaceVideoPresentationTest` 仍按旧 `WorkspaceFileViewer(files, entry, ...)` 调用；已改为 `WorkspaceDocument` 并本地编译通过。 |
| GitHub Actions `verify`（PR #37，head `7e6ff13`） | 全部 pass | Host scripts and handoff checks 与 Android build/JVM/lint 均 success；这是合并 main 前的结果。 |
| GitHub Actions `verify`（PR #37，合并 main 后 head `01ee0cd`） | 全部 pass | Host scripts and handoff checks 与 Android build/JVM/lint 均 success；PR `mergeStateStatus=CLEAN`、`mergeable=MERGEABLE`。 |

- 哪些是前一轮结果，哪些是本次新验证：上述编译、618 项 JVM、Android 测试源码编译与 lint 均为本轮新跑；0.1.10 的 535 项与设备 QA 是历史记录，不是本轮结果。
- 未测试、超时、失败和暂未复现：真实 Android 设备/模拟器、系统 SAF/DocumentsUI、`takePersistableUriPermission` 真实结果与撤销、旋转/进程死亡恢复、真实大文件与低内存/弱网、音视频直开、DOCX/XLSX 私有缓存清理、assemble/仪器测试均未执行；没有把源码存在当成通过。

## Review 修复（3 项 P2，2026-11-10）

- **重新选择同一 URI 不刷新**：`ExternalDocumentDescriptor.openToken` 每次成功打开/重新选择递增，`ContentDocument.key` 使用 `contentKey(uri, token)`；查看器和 PDF/DOCX/XLSX/文本子预览都以该 key 重置 `remember`/`LaunchedEffect`，同一 URI 重新授权后也会重新读取。
- **超限文件无法转交外部打开**：`ContentDocument.describe()` 只读取元数据，不再因内置预览上限抛错；PDF/图片/DOCX/XLSX 的大小限制只保留在各自解码/解析路径。`prepareOpen()` 与 `export()` 只看 URI 是否合法，因此 65 MiB DOCX 或 >512 MiB PDF 仍能「使用其他应用打开」或导出。
- **重建重放已消费 Intent**：`ExternalDocumentPolicy.requestId`/`shouldConsume` 记录 ACTION_VIEW/SEND 的已消费身份；`MainActivity` 在 `onCreate` 重建时跳过已消费的原始 Intent，`onNewIntent` 始终消费新请求，关闭后重建也不会重开旧文件。已消费身份写入 saved state 与私有 SharedPreferences，任务真正结束时清除。
- 回归：`ExternalDocumentPolicyTest` 新增同一 URI 新 token、已消费 Intent 不重放、超限仍可转交原 URI 3 项；完整 JVM 618 tests / 0 failures，lint 0 errors / 103 warnings / 1 hint。
- 按冲突处理要求 squash-rebase 到最新 `main=7593805`（PR #42 之后的 PR 模式），保留三项 P2 修复；本 PR 不修改 `AGENT-STATE.md`/`CURRENT.json`，共享状态由集成阶段统一刷新。

## 未完成与暂停项

- 未完成：独立 Codex High 审查与设备 QA；合并 main 后 head `01ee0cd` 的 CI 已通过；PR [#37](https://github.com/leezzou/tanuki/pull/37) 已创建、未合并。
- 外部阻塞：无。
- 用户暂停：无。
- 未验证项：见本记录“验证记录”与“未完成与暂停项”。

## 下一步

1. 等待独立 Codex High 审查与设备 QA；根据审查意见修复并推送同一 PR [#37](https://github.com/leezzou/tanuki/pull/37)，不要另开替代 PR。
2. 在真实 ARM64 设备/模拟器上用系统 DocumentsUI 验证：PDF/图片/文本/DOCX/XLSX 直开、旋转、进程被杀后恢复、权限撤销后的中文错误与重新选择、显式复制到工作区、超大文件拒绝、音视频直开；设备验证前不要宣称已通过。
3. 需要时遵循项目规则触发独立 Codex High 审查；不得自行合并或发布。

## 约束与风险

- 数据/设备/权限边界：外部 URI 只接受 `content://` 且有 authority；拒绝 `file://`、`http(s)://`、userInfo、控制字符和超长 URI；不开放写权限、不把 URI 交给 DSH/浏览器 AI；复制回退沿用 512 MiB/容量预算/临时目录回滚；DOCX/XLSX 暂存只在私有 cacheDir、64 MiB 上限、解析后删除。
- 关键决策及理由：查看器改为 `OpenDocument` 抽象，工作区与 content URI 共用预览/导出/打开方式；文本用 `SeekableInput` 区分随机读与顺序流，避免整文件入内存；DOCX/XLSX 的 ZIP 随机访问通过有界私有缓存暂存解决，不写入工作区；非持久授权不阻断本次读取，但明确提示进程重建风险。
- 失败尝试与避免重复踩坑的结论：首个 CI head 暴露了 Android 仪器测试仍调用旧查看器签名，已改为 `WorkspaceDocument` 并补跑 `compileModernDebugAndroidTestKotlin`；本地 JVM/lint 不会编译 androidTest，后续改动查看器签名必须显式编译 Android 测试源码。顺序流读完表头后不能回退到 0；已将表头字节作为首页前缀，避免 `StreamSeekableInput` 抛“向后跳转”错误。`BoundedCopy` 超限改为 `IOException` 并携带具体上限文案，避免被误报为“文件损坏”。`ContentDocument` 的 `ParcelFileDescriptor` 所有权交给 `PdfDocumentSession`，用 `@SuppressLint("Recycle")` 说明而非在返回前关闭。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本次未生成发布 APK/源码 ZIP；仅源码改动与文档。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装设备、未覆盖安装、未发布；PR [#37](https://github.com/leezzou/tanuki/pull/37) 已创建、待审。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮未删除历史交接；普通 PR 只清理本次范围内确认过期的记录，跨 PR 清理由集成维护方统一处理。本次暂存只包含本记录、代码与测试改动；`AGENT-STATE.md`/`CURRENT.json` 未修改。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。
