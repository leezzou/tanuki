# 交接：文本文件编码识别与兜底显示

- Agent/会话标识：android_agent-57（DSH Worker）
- 时间：2026-11-10 18:24 CST
- 状态：已完成（PR [#53](https://github.com/leezzou/tanuki/pull/53) 待独立审查与设备验证；不合并、不发布）
- 前一记录：[0.1.11 发布准备与交接集成](20261110-1636-release-0.1.11.md)

## 用户目标与范围

- 用户本次实际要求：修复打开 `.txt`/`.cpp` 等文本文件时提示“无法使用 utf8 解码”、直接打不开的严重问题；用户在国内环境，文件很可能是 GBK/GB18030 或 UTF-16。基于 `origin/main=4f2a3717` 建分支（先 fetch），只改文本读取/解码与查看器显示层，不改文件直开（content://）与共享状态文件。编码识别顺序为 BOM（UTF-8/UTF-16LE/BE/UTF-32）→ UTF-8 严格校验 → 无 BOM UTF-16 启发式 → GB18030（覆盖 GBK/GB2312）→ Big5 → Shift_JIS → Latin-1 兜底；任何情况下不得把“无法解码”抛给用户，识别不到按兜底编码显示并在顶部提供可关闭提示条与手动切换（UTF-8/GB18030/UTF-16/Big5/Latin-1）；二进制内容显示“这是二进制文件”+“用其他应用打开”+“复制路径”，不进入文本查看器；4 MiB 上限、流式解码、截断提示；补 JVM 测试；中文注释、author/committer=leezzou、PR 交付、不合并、不发布。
- 本次包含：重写 `TextFileReader` 的编码探测、二进制判定、兜底解码与 4 MiB 上限；`WorkspaceFileManager` 文本分类探测与 `TextPage.binary` 传递；查看器编码提示条、手动编码菜单、二进制提示面板与跨页编码沿用；`TextPageLayout` 截断判定同步；`TextFileReaderTest`/`TextPageLayoutTest` 回归；更新项目空间文档、测试报告与本交接。
- 本次不包含：合并、发布、tag/Release、签名打包、设备/模拟器安装与真机验收；不重写 PDF/DOCX/XLSX/音视频预览；不改 content:// 文件直开、SAF 授权、文件类型扩展名目录、编辑器写入格式（仍只写 UTF-8）或 `AGENT-STATE.md`/`CURRENT.json`。
- 用户改变或暂停的要求（需保留）：不合并、不发布；API Key 保存问题继续暂停；不执行 `pm clear`、卸载、wipe、恢复旧快照等破坏性设备操作；真机验收仍需另外确认。

## 当前状态

- 已实现：BOM/严格 UTF-8/无 BOM UTF-16/GB18030/Big5/Shift_JIS 顺序识别与 Latin-1 兜底；显示解码使用替代字符，不再向界面抛解码异常；二进制专用面板；4 MiB 截断；顶部可关闭编码提示与手动切换；自动识别后跨页沿用具体编码。
- 已验证：`compileModernDebugKotlin` BUILD SUCCESSFUL；定向 JVM 28 tests / 0 failures；完整 `testModernDebugUnitTest` 802 tests / 90 个结果文件 / 0 failures / 0 errors / 0 skipped，`lintModernDebug` 0 errors / 102 warnings / 1 hint。
- 待验证：Android 设备/模拟器打开真实 GBK `.txt`/`.cpp`、二进制伪装文本、4 MiB 大文件、手动切换与深色/大字体下的提示条布局；Android 仪器测试；独立 Codex High Review。
- 当前目标包名：未构建/安装新 APK；源码分支 `ao/text-decoding/text-encoding`（从 `ao/text-decoding/root` 建立），基线 `4f2a3717`，PR [#53](https://github.com/leezzou/tanuki/pull/53)；fetch 发现远端 `origin/main` 已前进到 `326726c`（仅文档），按用户指定 SHA 保留基线，未合并该文档提交。
- 关键事实与可定位源码：探测/解码在 `app/src/main/kotlin/com/tanuki/storage/TextFileReader.kt`；文本分类与二进制标记传递在 `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt`；提示条/手动切换/二进制面板在 `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt`；4 MiB 截断在 `app/src/main/kotlin/com/tanuki/ui/TextPageLayout.kt`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/TextFileReader.kt` | 重写编码识别：BOM（UTF-8/UTF-16LE/BE/UTF-32）→ UTF-8 严格校验（拒绝 NUL/异常控制字符）→ 无 BOM UTF-16 启发式（0x00 奇偶交错 + 两端序可读性）→ 二进制判定 → GB18030→Big5→Shift_JIS（严格解码 + 候选相关可读性评分）→ Latin-1 兜底；显示解码使用 `CodingErrorAction.REPLACE`，页首不完整字符按 Latin-1 消费保证翻页前进；有界探测样本 8 KiB、单页 64 KiB、显示上限 4 MiB；二进制返回占位编码“二进制”，不进入文本解码；手动 “UTF-16” 先用 BOM/启发式解析具体端序，避免跨页退回大端默认值。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt` | `TextPage` 增加默认 `binary` 字段并透传；未知类型内容探测改为“可文本解码且非二进制”，避免二进制被误判为文本；编辑入口的 UTF-8 限制改为“内置编辑只支持 UTF-8 文本”，不再用“解码失败”阻断阅读。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | 文本查看器改名为 `TextViewerContent`；自动识别后记住并沿用具体编码翻页；顶部新增可关闭提示条“已按 <编码> 显示，如有乱码可切换编码”和“切换编码”；编码菜单改用统一列表并标注 GB18030/UTF-16/Latin-1；二进制内容提前返回专用面板“这是二进制文件”+“用其他应用打开”+“复制路径”。 |
| `app/src/main/kotlin/com/tanuki/ui/TextPageLayout.kt` | 4 MiB 上限引用 `TextFileReader.MAX_VIEW_BYTES` 保持单一来源；截断判定在到达上限且总大小更大或未知时提示。 |
| `app/src/test/kotlin/com/tanuki/TextFileReaderTest.kt` | 更新并扩展回归：GBK 中文跨页、GB18030 生僻字、UTF-16LE/BE 无 BOM（含纯中日韩与手动 UTF-16）、UTF-8/UTF-32 BOM、非法字节 Latin-1 兜底、二进制 PNG/控制字节、手动切换与错选编码不抛异常、Big5/Shift_JIS、4 MiB 上限、原有页边界/UTF-8 BOM/流式窗口用例。 |
| `app/src/test/kotlin/com/tanuki/TextPageLayoutTest.kt` | 截断判定改为按显示字节与总大小判断，覆盖已知超限与未知大小流。 |
| `docs/WORKSPACE-STORAGE.md` | 更新文本查看器编码识别顺序、兜底提示、手动切换、二进制面板和 4 MiB 截断说明。 |
| `docs/TEST-REPORT.md` | 新增本次非设备验证记录与未测试范围。 |
| `docs/handoffs/20261110-1824-text-encoding-fallback.md` | 新增本次唯一交接记录；未改 `AGENT-STATE.md`/`CURRENT.json`。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin -x buildNativeRuntime` | BUILD SUCCESSFUL（JDK17、Android SDK 36、macOS ARM64） | 主源码编译通过；未安装设备。 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.TextFileReaderTest' --tests 'com.tanuki.TextPageLayoutTest' -x buildNativeRuntime` | BUILD SUCCESSFUL，28 tests / 0 failures / 0 errors / 0 skipped | 首次 Big5 用例失败（GB18030 误判），已把候选评分改为按编码区分假名权重并补常用繁体字，复跑通过。 |
| `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest :app:lintModernDebug` | BUILD SUCCESSFUL（5m09s），**802 tests / 90 个结果文件 / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 102 warnings / 1 hint** | 本轮新跑；新增 8 项（794 → 802）。lint warning 数与 0.1.11 基线一致，无新增 error。 |
| `git rev-parse origin/main` / `git log --oneline 4f2a371..origin/main` | fetch 后 `origin/main=326726c`，仅 `docs: correct 0.1.11 post-release status (#45)` 的文档改动 | 按用户指定基线 `4f2a3717` 建分支；未合并该文档提交，PR 合并前由集成方决定是否同步。 |

- 哪些是前一轮结果，哪些是本次新验证：上述编译、定向 JVM、完整 JVM/lint 均为本轮新跑；0.1.11 的 794 tests、APK 与设备事实为前轮记录，本轮未复跑。
- 未测试、超时、失败和暂未复现的情况：未在 Android 设备上打开真实用户的 GBK `.txt`/`.cpp` 或二进制伪装文件；提示条/手动菜单在深色、大字体、窄屏下的布局未验证；未知大小 SAF 流和 Android UTF-32 charset 未做设备验证；Big5/Shift_JIS 与 GB18030 字节范围重叠时仍可能误判，需手动切换；`preview()` 编辑入口仍只支持 UTF-8（GBK 等文件可查看但不能内置编辑）。

## 未完成与暂停项

- 未完成：PR 创建后的独立 Codex High Review、Android 设备/模拟器验收、Android 仪器测试。
- 外部阻塞：无。
- 用户暂停：API Key 保存问题继续暂停；合并/发布需另行授权。

## 下一步

1. PR [#53](https://github.com/leezzou/tanuki/pull/53) 的正文已写明识别策略、改动文件、测试命令与未覆盖编码；后续修复按该 PR 的 review/CI 跟进。
2. 独立 Codex High Review 绑定 PR 当前完整 SHA；推送新提交后需重新审查。
3. 设备验证建议：在 ARM64 测试 AVD 打开合成 GBK 中文 `.txt`、GB18030 生僻字、UTF-16LE/BE 无 BOM、UTF-8 BOM、非法字节和二进制 `.bin`；检查提示条关闭/切换编码、二进制按钮、4 MiB 截断和深色/大字体布局；再补 Android 仪器测试。

## 约束与风险

- 数据/设备/权限边界：未连接设备、未安装 APK、未读取真实用户数据；测试文件均为临时合成文本/字节。未改 content:// 直开、工作区读写权限、DSH 运行检查、编辑器 256 KiB 上限或共享状态文件。
- 关键决策及理由：探测只读文件头 8 KiB，避免为识别编码扫描整个文件；分页仍为 64 KiB 单页解码，满足流式读取；显示解码一律使用替代字符，任何非法字节都不会变成异常；GB18030 能接受部分 Big5/Shift_JIS 字节，因此候选评分按编码区分假名权重并结合常用简繁字命中率，仍无法确定时按用户要求顺序取第一个候选并保留手动切换；二进制判定放在 UTF-16 启发式之后，避免把正常 UTF-16 误判为二进制。
- 失败尝试与避免重复踩坑的结论：仅用严格 UTF-8 会把 UTF-16LE ASCII 当成含 NUL 的 UTF-8，必须先做 BOM/UTF-16 启发式；只按“能严格解码”会让 GB18030 抢走 Big5/Shift_JIS，必须比较候选可读性；显示解码不能用 `REPORT`，否则仍会把解码异常抛给界面；未改 content:// 代码，`ContentDocument` 通过 `encoding == "二进制"` 自然进入二进制面板。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未构建 APK、未生成源码 ZIP；PR [#53](https://github.com/leezzou/tanuki/pull/53) 以分支 `ao/text-decoding/text-encoding` 交付，代码提交 `ea60853`。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未覆盖、未签名、未发布、未建 tag/Release。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮未删除既有 `docs/handoffs/` 记录；它们仍承载未合并 PR、待验证设备项或历史发布证据，且不属于本次范围。本次只新增记录并更新 `docs/TEST-REPORT.md` 与 `docs/WORKSPACE-STORAGE.md`。
- 仍需保留的验证/未完成记录：`docs/TEST-REPORT.md` 的 0.1.11 与设备历史记录、各 PR 交接记录。
