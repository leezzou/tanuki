# 交接：文件查看器格式支持扩展

- Agent/会话标识：android_agent-41（DSH Worker）
- 时间：2026-11-10 11:42 CST
- 状态：已完成（PR [#34](https://github.com/leezzou/tanuki/pull/34) 待独立审查与设备验证；不合并、不发布）
- 前一记录：[0.1.10 Pre-release 发布准备](20261109-2310-release-0.1.10.md)（已发布的版本事实保留在 [当前状态](../AGENT-STATE.md) 与 [测试报告](../TEST-REPORT.md)）

## 用户目标与范围

- 用户本次实际要求：Tanuki 文件查看器大量常见文件无法打开（至少 `.cpp`、`.c` 不行）。先读 AGENTS.md、AGENT-STATE、AGENT-QUICKSTART 与现有 `WorkspaceFileViewer`/文件类型识别/预览实现，基于 `origin/main=0555c3c0` 建独立分支，系统梳理并支持常见文本/代码类型；代码/文本查看器需要等宽字体、行号、只读选择复制、大文件上限与截断提示；未知类型要有明确中文提示与“用文本打开/复制”回退；补扩展名/MIME/大小/编码边界单测；中文注释、author/committer=leezzou、PR 交付、不合并、不发布；回报支持清单、PR、测试与未支持项。
- 本次包含：新增纯 Kotlin `FileTypeCatalog` 统一扩展名/MIME 分类；重写 `WorkspaceFileManager` 的分类入口与规范 MIME；文本查看器增加行号、4 MiB 显示上限与截断提示；未知/不支持类型面板与“用文本打开/复制路径”回退；扩展 `TextFileReaderTest`，新增 `FileTypeCatalogTest`、`TextPageLayoutTest`；更新上手指南、项目空间文档、测试报告与本交接。
- 本次不包含：合并、发布、tag/Release、签名打包、设备/模拟器安装与真机验收；不重写 PDF/DOCX/XLSX/音视频渲染；不新增语言解析器或高亮引擎；不改变工作区读写权限、DSH 运行检查或 256 KiB 编辑器上限。
- 用户改变或暂停的要求（需保留）：不合并、不发布；API Key 保存问题继续暂停；不执行 `pm clear`、卸载、wipe、恢复旧快照等破坏性设备操作；真机验收仍需另外确认。

## 当前状态

- 已实现：集中式类型目录、文本查看器行号/截断、未知类型回退、MIME 规范化与内容探测边界。
- 已验证：`compileModernDebugKotlin` BUILD SUCCESSFUL；定向 JVM 回归 29 项通过（`FileTypeCatalogTest` 11、`TextPageLayoutTest` 9、`TextFileReaderTest` 6、`WorkspaceFileKindTest` 3）；完整 `testModernDebugUnitTest` 558 tests / 0 failures / 0 errors / 0 skipped（67 个结果文件），`lintModernDebug` 0 errors / 95 warnings / 1 hint，最终代码上组合 BUILD SUCCESSFUL（2m49s）。
- 待验证：真机/模拟器上的行号对齐、自动换行、深色主题、大文件截断提示、未知类型面板与复制路径；Android 仪器测试。
- 当前目标包名：未构建/安装新 APK；源码分支 `ao/file-viewer/formats`，基线 `0555c3c0eaeeed181269b5e68733ccfdd48d9e75`，PR [#34](https://github.com/leezzou/tanuki/pull/34)；无运行中安装/下载/设备任务。
- 关键事实与可定位源码：分类规则在 `app/src/main/kotlin/com/tanuki/storage/FileTypeCatalog.kt`；工作区接入在 `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt`；行号/截断策略在 `app/src/main/kotlin/com/tanuki/ui/TextPageLayout.kt`；界面在 `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/FileTypeCatalog.kt` | 新增：扩展名 → 文件类别/规范 MIME 的集中映射；文本扩展名优先于系统 MIME（修复 `.ts`/`.mts` 与 MPEG-TS 冲突，TypeScript/TSX 一律按文本）；点号配置只匹配已知文件名与 `.env`/`.env.*`，`.DS_Store` 等隐藏二进制归 OTHER，媒体扩展名优先于文件名规则；覆盖 C/C++/头文件、JVM/Python/JS/TS/Go/Rust/Shell、配置/标记/数据、HTML/CSS/SQL、图片/音频/视频、PDF/Office、归档/二进制；明确二进制集合避免内容探测误判。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt` | `classifyFileKind` 委托 `FileTypeCatalog`；`describe` 使用项目内规范 MIME，未知类型（非明确二进制）做有界 UTF-8 内容探测；`fileMime` 统一使用目录 MIME 并给无扩展名文本回退 `text/plain`。 |
| `app/src/main/kotlin/com/tanuki/ui/TextPageLayout.kt` | 新增：行号排版（行号与正文同一段文本，自动换行不错位）、原始/带行号下标换算（批量线性推进，避免多匹配 O(n×m)）、跨段行号延续、64 KiB 分页之上的 4 MiB 显示上限与截断判定。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | 文本查看器增加可开关行号、当前行范围与已显示字节、截断提示；未知/不支持类型改为中文说明面板，提供“用文本打开/复制路径”和返回类型说明；信息缺失时给兜底提示；切换分段时清空旧文本。 |
| `app/src/test/kotlin/com/tanuki/FileTypeCatalogTest.kt` | 新增 11 项：C/C++ 家族、`.ts`/`.mts` 冲突、常见语言、配置/标记/数据、图片/音频/视频、PDF/Office、归档/二进制、无扩展名/已知点号配置、隐藏二进制与 `.env` 前缀媒体、大小写/复合扩展名、MIME 回退。 |
| `app/src/test/kotlin/com/tanuki/TextPageLayoutTest.kt` | 新增 9 项：行号宽度/空文本/末尾换行、关闭行号、搜索下标换算、跨段行号、4 MiB 分页上限、截断判定、行数统计。 |
| `app/src/test/kotlin/com/tanuki/TextFileReaderTest.kt` | 新增 3 项：64 KiB 页边界与空文件、跨页多字节字符不拆分、UTF-16LE 不完整字符报错/ISO-8859-1/未知编码拒绝。 |
| `docs/WORKSPACE-STORAGE.md` | 更新文本查看器能力、4 MiB 截断与支持类型/回退说明。 |
| `docs/AGENT-QUICKSTART.md` | 模块地图补充 `FileTypeCatalog.kt`、`TextPageLayout.kt`。 |
| `docs/TEST-REPORT.md` | 新增本次非设备验证记录。 |
| `docs/AGENT-STATE.md` | 新增加本次交付状态与交接链接。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin` | BUILD SUCCESSFUL（1m05s，增量） | 仅主源码编译，未安装设备。 |
| 同上 `:app:testModernDebugUnitTest --tests 'com.tanuki.FileTypeCatalogTest' --tests 'com.tanuki.TextPageLayoutTest' --tests 'com.tanuki.TextFileReaderTest' --tests 'com.tanuki.WorkspaceFileKindTest'` | BUILD SUCCESSFUL（2m05s），28 tests / 0 failures / 0 errors / 0 skipped | 首次运行 2 项断言写错（`displayOffset(0)` 应含行号前缀、无末尾换行的下一段行号），已修正测试并复跑通过。 |
| `source scripts/ao-worktree-env.sh && ./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:testModernDebugUnitTest :app:lintModernDebug` | BUILD SUCCESSFUL（最终代码 2m49s），558 tests / 0 failures / 0 errors / 0 skipped（67 个结果文件）；lint 0 errors / 95 warnings / 1 hint | 新增 23 项（535 → 558），与 0.1.10 基线一致；优化批量下标换算、收紧点号配置后均重跑，结果不变；未跑 assemble/Android 仪器/真机。 |

- Review P2 修复（thread `PRRT_kwDOVgRm4s61jwII`）：点号配置仅匹配已知文件名与 `.env`/`.env.*`；`.DS_Store` 归隐藏二进制并跳过内容探测，`.env-image.png` 按媒体扩展名优先识别；新增 1 项回归，完整 JVM 558 tests。
- 哪些是前一轮结果，哪些是本次新验证：上述编译与定向测试均为本轮新跑；0.1.10 的 535 JVM、发布 APK 与设备事实均为前轮记录，本轮未复跑，不作为本次结论。
- 未测试、超时、失败和暂未复现的情况：未在 Android 设备上打开真实 `.c`/`.cpp` 文件复现用户报告；行号在自动换行、超大字号、深色主题下的像素对齐需要设备/截图验证；4 MiB 截断在真实大日志上的滚动与提示需要设备验证；Android 仪器测试未运行。

## 未完成与暂停项

- 未完成：独立 Codex High Review（合并前要求）、真机/模拟器验收、Android 仪器测试。
- 外部阻塞：无。
- 用户暂停：API Key 保存问题继续暂停；发布/合并需另行授权。

## 下一步

1. 独立 Codex High Review 绑定 PR #34 当前完整 SHA；推送新提交后需重新审查。
2. 设备验证建议：在 ARM64 测试 AVD 打开合成 `.c`/`.cpp`/`.ts`/`.json`/大日志/未知扩展名文件，检查行号、自动换行、复制、截断提示、深色主题与回退按钮；再补 Android 仪器测试。
3. 若用户设备上的 `.c`/`.cpp` 仍无法打开，收集文件路径、大小、前几 KB 的字节/编码与 `describe` 显示的类型，再定位是否为特定编码或文件变化检查。

## 约束与风险

- 数据/设备/权限边界：未连接设备、未安装 APK、未读取真实用户数据；测试文件为临时合成文本。工作区读写权限、DSH 运行检查、256 KiB 编辑器上限和既有安全策略均未改变。
- 关键决策及理由：分类目录放在 `storage` 纯 Kotlin 层，避免依赖 Android `MimeTypeMap` 的不稳定映射，并让扩展名优先于 MIME；行号与正文放在同一段文本，自动换行时行号不会错位，搜索仍以原始文本下标计算；“复制本段”复制原始文本，不含行号；4 MiB 上限沿用 64 KiB 分页，避免一次性载入大日志。
- 失败尝试与避免重复踩坑的结论：不能只依赖 `MimeTypeMap`（`.ts` 在 Android 上可能映射为 `video/mp2t`），也不能只按 MIME 判断媒体，否则缺 MIME 时 PNG/MP4 会落入未知类型；明确二进制/归档/旧版 Office 不参与内容探测；测试断言必须按“带行号后的显示下标”计算。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未构建 APK；`scripts/package-source.py` 生成的源码包与 `artifacts/SOURCE-SHA256SUMS` 为工作材料，不提交；PR [#34](https://github.com/leezzou/tanuki/pull/34) 以分支 `ao/file-viewer/formats` 交付，最终提交 `60889eb`（文档回填后会有新的提交，head 以 PR 页面为准）。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未覆盖、未签名、未发布、未建 tag/Release。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮保留全部既有 `docs/handoffs/` 记录。它们仍分别承载未合并 PR、待验证设备项与历史发布证据，删除会丢失唯一定位信息，且不属于本次任务范围；本次只新增记录并更新 `AGENT-STATE` 最近链接。后续若确认某记录已被 `AGENT-STATE`/专题文档完整替代，再按 AGENTS.md 迁移后删除。
- 仍需保留的验证/未完成记录：`docs/TEST-REPORT.md` 的 0.1.10 与设备相关历史记录、各 PR 交接记录。
