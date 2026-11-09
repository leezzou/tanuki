# 交接：20261109-1836-docx-native-preview

- Agent/会话标识：android_agent-31（DSH Worker）
- 时间：2026-11-09 18:36 CST
- 状态：进行中（实现、rebase、5 条 Review 修复与 JVM/lint 验证完成；PR [#28](https://github.com/leezzou/tanuki/pull/28) 等待复审与设备 QA）
- 前一记录：[DSH 安装提速与国内镜像](20261109-1539-dsh-install-resilience.md)

## 用户目标与范围

- 用户本次实际要求：把 DOCX 从纯文本提取升级为应用内原生只读预览；解析 OOXML（document/styles/numbering/rels/media），Compose 渲染段落/标题/列表、粗体斜体下划线字号颜色、表格、图片、基本分页和页边距；连续滚动+缩放；安全上限与中文错误；明确保真边界；补 JVM 回归；独立 `/tmp` clone 提交、开 PR，不合并、不触发 Review，文档/PR 不提参考项目，注释中文。
- 本次包含：新增 `com.tanuki.storage.docx` 解析层（模型/样式/编号/关系/媒体/安全）、纯 Kotlin 布局与分页策略、Compose 分页渲染与缩放、查看器接线、`WorkspaceFileManager.readDocx`、专题文档和 JVM 回归。
- 本次不包含：修改官方 DSH UI/依赖、引入 LibreOffice/第三方文档渲染库、`.doc` 旧格式预览、宏/域/嵌入对象/精确分页、设备或仪器测试、合并/发布/触发 AO Review。

## 当前状态

- 已实现：`.docx` 打开后进入原生分页预览；支持常用字符/段落/标题/列表/表格/图片/页面设置；连续滚动、1–4 倍按钮/双指/双击缩放；复制纯文本；支持范围对话框如实列出不支持项。
- 已实现安全边界：归档/条目/总解压/单条目/正文/辅助 XML/图片大小与数量、XML 元素与深度、文字量、块数/单元格数上限；拒绝路径穿越、重复条目、DOCTYPE、未定义实体与不安全关系目标；外部关系不联网；图片解码采样与总像素预算；损坏/加密/超限返回中文错误。
- 已验证：rebase 到 `main=5fd1ab4` 后 `compileModernDebugKotlin` BUILD SUCCESSFUL；完整 `testModernDebugUnitTest` BUILD SUCCESSFUL，328 tests / 0 failures / 0 errors / 0 skipped（main 265 - 被替换的 DOCX 纯文本测试 10 + 新增 73），其中新增 DOCX 回归 73 项。
- 待验证：真实设备上的双指缩放/横向滚动/深色主题/超大文档性能、真实 Word 复杂样本、低 API/真机；assemble、Android 仪器未跑，lint 本轮通过。
- 当前基线：`main=5fd1ab4`（已包含 XLSX 预览 PR #23 与 DSH 安全模式 PR #25）；分支 `ao/docx-preview/root` 已 rebase 到该基线（独立 `/tmp` clone 提交/验证），PR [#28](https://github.com/leezzou/tanuki/pull/28)；rebase 后功能提交 `8eed1f1`（Review 修复 `a4038ad`）；PR 后续仅有交接/状态回填提交，最终 head 以 PR 页面为准；不合并、不触发 AO Review。
- 关键源码：[`DocxDocumentReader.kt`](../../app/src/main/kotlin/com/tanuki/storage/docx/DocxDocumentReader.kt)、[`DocxModel.kt`](../../app/src/main/kotlin/com/tanuki/storage/docx/DocxModel.kt)、[`DocxStyles.kt`](../../app/src/main/kotlin/com/tanuki/storage/docx/DocxStyles.kt)、[`DocxNumbering.kt`](../../app/src/main/kotlin/com/tanuki/storage/docx/DocxNumbering.kt)、[`DocxXml.kt`](../../app/src/main/kotlin/com/tanuki/storage/docx/DocxXml.kt)、[`DocxLayout.kt`](../../app/src/main/kotlin/com/tanuki/ui/DocxLayout.kt)、[`WorkspaceDocxPreview.kt`](../../app/src/main/kotlin/com/tanuki/ui/WorkspaceDocxPreview.kt)。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/docx/DocxModel.kt` | 新增只读文档/页面/块/表格/图片/样式/编号模型与解析上限。 |
| `app/src/main/kotlin/com/tanuki/storage/docx/DocxXml.kt` | 新增 XML 元素/深度/文字预算、实际解压限流、安全路径与关系目标归一化。 |
| `app/src/main/kotlin/com/tanuki/storage/docx/DocxStyles.kt` | 新增 `styles.xml` 解析、basedOn 链合并、docDefaults、标题级别与标题字号回退。 |
| `app/src/main/kotlin/com/tanuki/storage/docx/DocxNumbering.kt` | 新增 `numbering.xml` 解析、多级计数器、decimal/letter/roman/bullet 格式化。 |
| `app/src/main/kotlin/com/tanuki/storage/docx/DocxDocumentReader.kt` | 新增 ZIP 安全扫描、正文/关系/媒体解析、分页符与分节属性、中文错误与不支持下提示。 |
| `app/src/main/kotlin/com/tanuki/ui/DocxLayout.kt` | 新增纯 Kotlin 页面几何、贪心分页、图片显示尺寸/采样与像素预算。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceDocxPreview.kt` | 重写为 Compose 原生分页渲染：文本行切片、表格行、图片、页边距、连续滚动、缩放、复制、支持范围对话框。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | DOCX 分支改接 `DocxPreview`。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt` | 用 `readDocx()`（IO 锁内调用新解析器）替换 `extractDocxText()`。 |
| `app/src/main/kotlin/com/tanuki/storage/DocxTextExtractor.kt` | 删除纯文本提取器，避免与新解析器重复且不一致的安全逻辑。 |
| `app/src/test/kotlin/com/tanuki/DocxTextExtractorTest.kt` | 删除被替代的测试。 |
| `app/src/test/kotlin/com/tanuki/DocxFixtures.kt` | 新增合成 DOCX/ZIP/XML fixture 工具。 |
| `app/src/test/kotlin/com/tanuki/DocxDocumentReaderTest.kt` | 新增 25 项样式/列表/表格/图片/页面/分页/文本回归。 |
| `app/src/test/kotlin/com/tanuki/DocxSecurityTest.kt` | 新增 22 项损坏包/路径穿越/条目与解压上限/XML/媒体安全回归。 |
| `app/src/test/kotlin/com/tanuki/DocxPaginatorTest.kt` | 新增 8 项纯分页策略回归。 |
| `app/src/test/kotlin/com/tanuki/DocxLayoutPolicyTest.kt` | 新增 10 项页面几何/字号比例/图片采样与显示回归。 |
| `docs/DOCX-PREVIEW.md` | 新增支持/不支持、近似分页与安全边界说明。 |
| `docs/AGENT-QUICKSTART.md`、`docs/AGENT-STATE.md`、`docs/TEST-REPORT.md` | 更新模块入口、当前状态与本轮验证记录。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin --console=plain` | BUILD SUCCESSFUL（JDK17、SDK36、共享 worktree 缓存） | 编译覆盖新增存储层与 Compose 接线；不代表设备渲染正确。 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.Docx*'` | BUILD SUCCESSFUL；73 tests / 0 failures / 0 errors / 0 skipped（Reader 25、Security 22、Paginator 8、LayoutPolicy 18） | 合成 ZIP/XML；kxml2 仅测试实现。 |
| `./gradlew :app:testModernDebugUnitTest`（rebase 前） | BUILD SUCCESSFUL；260 tests / 0 failures / 0 errors / 0 skipped | 完整 JVM 套件；测试 XML 在 `app/build/test-results/testModernDebugUnitTest/`，不提交。 |
| `git rebase origin/main`（`5fd1ab4`）后 `./gradlew :app:compileModernDebugKotlin :app:testModernDebugUnitTest :app:lintModernDebug` | BUILD SUCCESSFUL；328 tests / 0 failures / 0 errors / 0 skipped；lint 通过 | 解决 `WorkspaceFileViewer` 冲突（保留 DOCX + XLSX 分支）并合并 runtime 安全模式后的 `AGENT-STATE`/印章；rebase 后重新运行。 |
| 独立 `/tmp` clone（rebase 后 `main=5fd1ab4`）`./gradlew :app:compileModernDebugKotlin :app:testModernDebugUnitTest` | BUILD SUCCESSFUL；328 tests / 0 failures / 0 errors / 0 skipped | 干净 clone 复跑并解决冲突，不使用共享 worktree 的 `.git` 写操作；构建缓存来自项目既有环境。 |
| Review P1/P2 修复回归 | Bitmap 释放改为捕获 effect 快照并新增 `DocxBitmapLifetime` 测试；文本切片复用已测量布局（`drawText`）并按页合并、长段落 8,000 字有界分段、行循环内执行预算；列宽用 Long 归一化并限制到正文宽度；页首 gap 清零；测量结果携带截断状态并在界面提示。 | 5 条 inline thread 均有对应代码/测试；真实设备手势与大文档性能仍未验证。 |
| 关键失败与修复 | 1）XML 本地名被整体小写，导致 `pPr/sectPr/gridCol` 等全部失配，样式/表格/页面/图片测试失败；改为保留 OOXML 大小写后 73 项全过。2）分页 gap 测试期望错误；修正为分页起点丢弃 gap。3）`DocxParagraph`/`DocxTable` 误当顶层类型；实际是 `DocxBlock` 嵌套类型，已修正。 | 均为本轮新跑记录，不掩盖失败。 |
| 未执行 | assemble、Android 仪器、真实设备、真实 Word 样本、大文档性能 | lint 与 JVM 已跑；不能写成设备验收或完整 Word 兼容。 |

- 哪些是前一轮结果，哪些是本次新验证：rebase 后的 328 JVM、compile、lint 与独立 `/tmp` clone 复跑均为本轮新跑；rebase 前 260 JVM 保留为历史步骤；XLSX 233 项与 runtime 安全模式 32 项属 main 既有记录，不混入 DOCX 新增 73 项。
- 未测试、超时、失败和暂未复现的情况：真实 Word 复杂排版、双指与横向滚动交互、深色主题、图片解码 OOM 边界、超大表格/文档滚动性能未验证；assemble/仪器/真机未跑，lint 本轮通过。

## 未完成与暂停项

- 未完成：PR #28 的独立 Codex High Review 与真实设备 QA（由 Orchestrator/用户按项目规则推进）。
- 外部阻塞：无。
- 用户暂停（沿用）：API Key 保存、真实模型/账号验收、真机验收；本轮未触碰。

## 下一步

1. PR [#28](https://github.com/leezzou/tanuki/pull/28) 已 rebase 到 `main=5fd1ab4` 并解决与 XLSX 预览的冲突；目标 `main`，正文含摘要、支持/不支持清单、测试与未验证项，不合并、不触发 AO Review。
2. 由 Orchestrator/用户安排当前 head 的独立 Codex High Review 与设备 QA；设备至少覆盖图片/表格/列表/真实复杂 DOCX、双指缩放与横向滚动、超大文档。
3. 若 Review 要求修改，先在本分支修复、重跑 compile/JVM 并更新交接，再推送；不得以 JVM 结果替代设备验收。

## 约束与风险

- 数据/设备/权限边界：未启动模拟器/真机、未安装、未读取真实文件或凭据；只使用合成 ZIP/XML 与 JVM。未修改官方 DSH UI、协议、AO 配置或既有安全/暂停项。
- 关键决策及理由：解析与渲染分层，纯 Kotlin 策略可 JVM 测试；所有解压按实际字节计数，不信任 ZIP 声明；图片只读取正文引用且限制位置/总量；为保证手机可读性统一放大正文 1.9x，因此分页为近似而非 Word 精确排版；`.doc` 继续走外部打开。
- 失败尝试与避免重复踩坑的结论：XML 标签名不能统一小写；Compose 文本分页切片必须允许超出切片高度排版后再裁剪，否则后续行可能被测量阶段截断；表格单元格渲染宽度必须与测量宽度（列宽减左右 padding）一致，否则会重新换行。
- 已知风险：真实 Word 复杂样式/域/浮动图片、超大文档性能、不同字体度量与 Compose 行高差异可能造成分页偏差；图片字节在内存中保留到预览离开，受 48 MiB 上限约束；照片解码仍可能受设备内存影响。

## 产物与校验

- 新/旧 APK、源码 ZIP：本轮不生成 APK、不发布；源码 ZIP 仅在提交前按 `scripts/package-source.py` 刷新并核对 `artifacts/SOURCE-SHA256SUMS`，属于工作材料。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布；PR [#28](https://github.com/leezzou/tanuki/pull/28) 只交付可审查候选，不合并。
- 提交/推送前清理的过期记录：未删除历史交接；DSH 已安装版本管理、DSH 安装提速、后台更新检查等记录仍与当前 `AGENT-STATE` 和未完成 PR 相关，保留。本记录为当前唯一交接。
- 仍需保留的验证/未完成记录：本记录、`docs/handoffs/CURRENT.json` 印章与 `docs/DOCX-PREVIEW.md`；真实设备与独立 Review 缺口待 QA/Orchestrator。
