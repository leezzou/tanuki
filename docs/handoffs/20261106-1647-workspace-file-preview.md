# 交接：20261106-1647-workspace-file-preview

- Agent/会话标识：android_agent-16（DSH Worker）
- 时间：2026-11-06 16:47 CST
- 状态：进行中（实现与 JVM 验证通过；PR #16 已创建，等待独立 Review 与设备 QA）
- 前一记录：[研发治理与合并前门禁](20261106-1415-governance-gates.md)

## 用户目标与范围

- 用户本次实际要求：在文件查看器中增加 PDF 内置分页预览、DOCX 只读文本预览、图片双指缩放/平移/双击缩放；扩展 `WorkspaceFileManager.FileKind` 与安全文件访问；抽纯策略并补 JVM 单测；不改视频全屏逻辑；按 AGENTS.md 写交接、刷新印章并开 PR，不合并、不触发 Review、不发布。
- 本次包含：`FileKind` 的 PDF/DOCX/DOC 分类与 `.doc` 明确提示；`PdfRenderer` 逐页渲染、翻页/页码/缩放、中文错误、IO 渲染、Bitmap 回收与分辨率上限；`ZipFile` + `XmlPullParser` 提取 DOCX 正文及 zip bomb/文本上限；共享 `Zoomable` 组件与 `ZoomPolicy`；对应 JVM 回归。
- 本次不包含：`WorkspaceVideoPlayer.kt` 及视频全屏、设备/仪器 UI 测试、真实加密 PDF/DOCX 样本验收、第三方解析库接入、发布/合并。

## 当前状态

- 已实现：上述功能全部落到源码；PDF/DOCX 在查看器内打开，不再落入 `OTHER`；`.doc` 仍走外部打开并显示明确提示。已按 PR #16 review 把 DOCX 读取改为 `nextToken()`，使 `DOCDECL` 可被观察并拒绝，保留实体/CDATA 文本，并补 DOCTYPE 回归。
- 已验证：`./gradlew :app:compileModernDebugKotlin :app:testModernDebugUnitTest` BUILD SUCCESSFUL；124 个 JVM 测试，0 失败、0 错误、0 跳过（含新增 20 项）。
- 待验证：真实设备上的手势、翻页、加密/损坏 PDF、真实复杂 DOCX 与深色主题；未运行 lint/assemble/仪器测试。
- 当前基线：`main=cff09a377baf5d047990c0e81eb3984520e4a026`（PR #14 合并提交）；分支 `ao/tanuki-android-dsh/workspace-file-preview`，PR [#16](https://github.com/leezzou/tanuki/pull/16)。初始代码提交 `dd3807bb5529c18c0e320ebdbc5de818b0f2788c`；review 修复后的最终 head 以 PR 页面为准。
- 关键源码：[`WorkspaceFileManager.kt`](../../app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt)、[`DocxTextExtractor.kt`](../../app/src/main/kotlin/com/tanuki/storage/DocxTextExtractor.kt)、[`WorkspaceFileViewer.kt`](../../app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt)、[`WorkspacePdfPreview.kt`](../../app/src/main/kotlin/com/tanuki/ui/WorkspacePdfPreview.kt)、[`WorkspaceDocxPreview.kt`](../../app/src/main/kotlin/com/tanuki/ui/WorkspaceDocxPreview.kt)、[`ZoomableContent.kt`](../../app/src/main/kotlin/com/tanuki/ui/ZoomableContent.kt)。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt` | `FileKind` 增加 PDF/DOCX/DOC；抽 `classifyFileKind` 纯映射；`describe`/`fileMime` 映射 pdf/docx/doc；新增 `openPreviewDescriptor`（只读 ParcelFileDescriptor）和 `extractDocxText`（IO 锁内有界解压）。 |
| `app/src/main/kotlin/com/tanuki/storage/DocxTextExtractor.kt` | 新增：只读 `word/document.xml`，处理 `w:p/w:t/w:br/w:cr/w:tab`；限制归档大小、条目数、总解压大小、正文大小与文本长度；用 `nextToken()` 观察并拒绝 `DOCDECL`（`next()` 会跳过）；中文错误。 |
| `app/src/main/kotlin/com/tanuki/ui/ZoomableContent.kt` | 新增：`ZoomPolicy` 纯策略（1.0–5.0x、双击 1x/2.5x、围绕手指缩放、平移边界）与共享 `Zoomable` 手势组件，切换文件/页面或视口变化时重置。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfRenderPolicy.kt` | 新增：PDF 位图长边 ≤2048、总像素 ≤4,000,000、小页最多 2x 放大，避免 OOM。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspacePdfPreview.kt` | 新增：PFD + `PdfRenderer` 打开/逐页渲染/关闭；上一页/下一页/页码/缩放；加密、损坏、无页中文提示；IO 渲染与 Bitmap 及时回收。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceDocxPreview.kt` | 新增：DOCX 只读文本预览，提示复杂排版/页眉页脚/图片丢失及截断，支持选择复制。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | 图片改用共享 `Zoomable`；接入 PDF/DOCX；`.doc` 显示“暂不支持内置预览，可用其他应用打开”；视频分支未改。 |
| `app/build.gradle.kts` | 仅测试增加 `net.sf.kxml:kxml2:2.3.0`；产品运行时仍用 Android 内置 XmlPullParser，不新增运行时库。 |
| `app/src/test/kotlin/com/tanuki/WorkspaceFileKindTest.kt` | 新增：PDF/DOCX/DOC 与既有类型映射。 |
| `app/src/test/kotlin/com/tanuki/DocxTextExtractorTest.kt` | 新增：合成 zip 多段/空段/换行/制表/实体/CDATA、DOCTYPE 拒绝、文本截断、单条目与总解压超限、缺条目、损坏 zip、坏 XML。 |
| `app/src/test/kotlin/com/tanuki/ZoomPolicyTest.kt` | 新增：clamp、双击切换、Fit 尺寸、围绕中心缩放、平移边界、5x 上限。 |
| `app/src/test/kotlin/com/tanuki/PdfRenderPolicyTest.kt` | 新增：A4、超大页、超小页、窄页的分辨率上限。 |
| `docs/AGENT-STATE.md`、`docs/TEST-REPORT.md`、`docs/AGENT-QUICKSTART.md` | 更新当前状态、本轮验证记录和模块地图；未改接口契约。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin :app:testModernDebugUnitTest` | BUILD SUCCESSFUL；124 tests / 0 failures / 0 errors / 0 skipped | review 修复后本轮新跑；测试 XML 在 `app/build/test-results/testModernDebugUnitTest/`，不提交。 |
| 测试类 `WorkspaceFileKindTest`、`ZoomPolicyTest`、`DocxTextExtractorTest`、`PdfRenderPolicyTest` | 2 + 6 + 8 + 4 = 20 项全部通过 | 其中 20 项为本次新增；DOCX 测试使用 kxml2 作为 JVM 侧 XmlPullParser 实现，产品代码不依赖该库。 |
| PR #16 review P2 修复（`DocxTextExtractor.kt:97`） | 改为 `nextToken()` 并补 `rejectsDoctypeDeclarationBeforeRootElement`；DOCTYPE 返回中文“文档类型声明”错误，实体与 CDATA 断言同时通过 | 针对合成 XML/zip，未用真实 Word 生成样本；`nextToken()` 对标准实体的解析由现有 `&amp;` 断言覆盖。 |
| 首次测试运行 | 1 项失败：新增缩放测试断言写反（期望 -maxX 却传入正向平移） | 仅测试代码错误；修正断言后复跑通过，不涉及产品行为。 |
| 静态边界检查 | `.doc` 不进入 DOCX 分支；`WorkspaceVideoPlayer.kt` 未修改；产品依赖未新增 | 编译覆盖 UI 接线；未做设备手势/真实文件验证。 |

- 哪些是前一轮结果，哪些是本次新验证：124 项 JVM 与编译结果均为 review 修复后新跑；历史 104 JVM、设备测试和 0.1.8 冒烟不混入本轮。
- 未测试、超时、失败和暂未复现的情况：未跑 lint、assemble、Android 仪器、真实设备；未用真实加密 PDF/损坏 PDF/复杂 DOCX 验证错误文案与渲染效果；手势在 Compose 中的双击/双指共存仅编译通过，未设备实测。

## 未完成与暂停项

- 未完成：提交/推送/创建 PR；真实设备 UI 与文件样本验收。
- 外部阻塞：无。
- 用户暂停（沿用）：API Key 保存、真实模型/账号验收、真机验收；本轮不触碰。

## 下一步

1. PR #16 已从独立 `/tmp` clone 创建，目标 `main`、未合并、未触发 AO Review、未发布；等待当前 head 的独立 Codex High Review、CI 与设备 QA。
2. 由 Orchestrator 安排当前 head 的独立 Codex High Review 与适用设备 QA；设备验证至少覆盖图片双指/双击/边界、PDF 翻页/缩放/加密与损坏样本、DOCX 多段/超限/复杂格式提示，真机与低 API 另行确认。

## 约束与风险

- 数据/设备/权限边界：未启动模拟器/真机、未安装、未读真实文件或凭据；只使用合成 zip 和 JVM。
- 关键决策及理由：PDF 每次渲染在单个 IO 调用内打开并关闭 `PdfRenderer`，避免跨线程/跨协程持有原生 renderer；PFD 只读且调用方负责关闭。DOCX 不引第三方运行时库，限制声明大小并实际计数解压字节；文本超限截断而非无限占用内存。图片与 PDF 共用 `Zoomable`，视频文件完全未动。
- 失败尝试与避免重复踩坑的结论：新增缩放测试首次把平移方向写反，说明边界测试要同时覆盖正负方向；已修正。`net.sf.kxml` 仅放在 `testImplementation`，若后续误移到 `implementation` 会新增运行时依赖。

## 产物与校验

- 新/旧 APK、源码 ZIP：本轮不生成 APK、不发布；源码 ZIP 仅在提交前按 `scripts/package-source.py` 刷新并核对 `artifacts/SOURCE-SHA256SUMS`，属于工作材料。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布；PR [#16](https://github.com/leezzou/tanuki/pull/16) 只交付可审查候选。
- 提交/推送前清理的过期记录：未删除历史交接；W5 治理记录仍与当前 `AGENT-STATE` 和 PR #14 相关，保留。结论已迁入 `AGENT-STATE`/`TEST-REPORT`，无因本任务过期而必须删除的记录。
- 仍需保留的验证/未完成记录：本记录与 `docs/handoffs/CURRENT.json` 印章；设备验证缺口待独立 QA。
