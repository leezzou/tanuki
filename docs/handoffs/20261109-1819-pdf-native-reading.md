# 交接：20261109-1819-pdf-native-reading

- Agent/会话标识：android_agent-35（Tanuki DSH Worker）
- 时间：2026-11-09 18:19 CST
- 状态：已完成（实现 + JVM 验证；设备验证未执行）
- 前一记录：[文件查看器内置预览](20261106-1647-workspace-file-preview.md)（F1，PDF 单页预览基线；PR #16 已合并）

## 用户目标与范围

- 用户本次实际要求：打磨 PDF 原生阅读体验（只读阅读，不编辑 PDF）。实现连续纵向翻页、页面缓存/预加载相邻页、适应宽度/适应页面、捏合 1–5x、双击缩放、页码/总页数、快速跳页；大文档不 OOM；保持现有缩放/平移测试不回归；新增页面缓存/预加载/内存上限/跳页策略 JVM 测试；UI 不遮挡官方 DSH，文件查看入口保持现状；在独立 `/tmp` clone 提交并开 PR，不合并、不触发 Review；文档/PR 不提参考项目；注释中文。
- 本次包含：`PdfRenderer` 单文档会话、连续纵向 `LazyColumn`、±1 页预加载、按字节预算的 LRU 页缓存与位图回收、适应宽度/整页布局、双指 1–5x/双击 2.5x、页码与跳页对话框、加密/损坏/超大/内存不足中文提示、JVM 策略测试、交接与测试文档。
- 本次不包含：PDF 编辑/批注/文本选择/搜索、WebView 或外部 App 渲染、真机/模拟器仪器测试、lint/assemble、版本号/发布、合并或独立 Review。

## 当前状态

- 已实现：上述阅读能力与策略层；文件查看入口、官方 DSH UI/模型/插件/Skills 未改，未新增依赖。
- 已验证：本 worktree `compileModernDebugKotlin` 成功；完整 `testModernDebugUnitTest` 254 tests / 0 failures / 0 errors / 0 skipped；其中本轮新增 49 项 PDF 策略测试，既有 `ZoomPolicyTest` 6 项与 `PdfRenderPolicyTest` 4 项不回归。
- 待验证：真实设备上的连续滚动帧率、捏合/双击/横向平移手感、深色主题与字体缩放、真实大文档/加密/损坏 PDF、低内存设备预算表现；lint/assemble/Android 仪器未跑。
- 当前分支：`ao/pdf-reading/root`（会话 worktree）；实际交付分支为从该 SHA 拉出的 `ao/pdf-reading/native-reading`；基线 `main=2f3fac9301c7c3484df85e99091e8f63dddc0a48`（以实际 Git 为准，`docs/AGENT-STATE.md` 中旧基线说明是其他会话历史）。
- 关键事实与可定位源码：`app/src/main/kotlin/com/tanuki/ui/WorkspacePdfPreview.kt`（会话/界面/手势）、`PdfRenderPolicy.kt`（视口渲染尺寸）、`PdfPageCache.kt`、`PdfPageMemory.kt`、`PdfPreloadPolicy.kt`、`PdfMemoryPolicy.kt`、`PdfViewportPolicy.kt`、`PdfJumpPolicy.kt`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/WorkspacePdfPreview.kt` | 改为单 `PdfRenderer` 会话；连续纵向 `LazyColumn`；±1 页窗口加载与取消回收；适应宽度/整页；双指缩放、双击、横向平移；页码/跳页；加密/损坏/超大/内存不足中文错误；页面加载失败可重试。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfRenderPolicy.kt` | 保留既有 `targetSize`（测试兼容）；新增 `viewportSize`，按视口宽度、长边 2048、总像素 4,000,000 与缓存预算计算渲染尺寸。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfPageCache.kt` | 新增按字节记账的 LRU 缓存（纯 Kotlin）。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfPageMemory.kt` | 新增位图生命周期管理：预算淘汰、显示中延迟回收、离开组合/关闭时回收。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfPreloadPolicy.kt` | 新增当前页 + 前后各一页的预加载窗口与保护集合；复审后新增 `renderPlan`：同屏可见页全部优先加载，再在可见范围外补首尾预加载。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfMemoryPolicy.kt` | 新增堆比例缓存预算（24–64 MiB）、单页像素上限与 512 MiB 文件打开上限。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfViewportPolicy.kt` | 新增适应宽度/整页尺寸、横向平移边界、围绕手指缩放的平移/滚动补偿、视口中心当前页判定；复审后修正纵向补偿为 `(centroidY−H/2)×(1/oldScale−1/newScale)`，并新增放大首尾边缘留白 `zoomEdgePadding` 与屏幕坐标推导 `scaledScreenY`。 |
| `app/src/main/kotlin/com/tanuki/ui/PdfJumpPolicy.kt` | 新增页码输入解析（`12`、`12/50`、`第12页` 等）、页码与倍率文案。 |
| `app/src/test/kotlin/com/tanuki/PdfPageCacheTest.kt` 等 7 个新增测试文件 | 新增 42 项纯 JVM 策略测试：缓存淘汰/保护、显示中延迟回收/关闭回收、预加载窗口、内存上限与文件限制、跳页解析、视口/平移/缩放补偿/当前页、视口渲染尺寸。 |
| `docs/handoffs/20261109-1819-pdf-native-reading.md` | 本记录。 |
| `docs/AGENT-STATE.md`、`docs/TEST-REPORT.md` | 记录本轮状态与验证事实。 |

### 复审修复（PR #24 Codex P2，3 条）

- 缩放首尾可达：`LazyColumn` 仍按 1× 测量，但按 `PdfViewportPolicy.zoomEdgePadding(H, scale)=H×(scale−1)/(2×scale)` 增加上下滚动留白；1× 留白为 0。第一页顶部在滚动 0 处贴视口上沿，最后一页底部在最大滚动处贴视口下沿；捏合/双击补偿同时计入留白变化。
- 纵向补偿公式：由 `(centroidY−H/2)×(1−old/new)` 修正为 `(centroidY−H/2)×(1/oldScale−1/newScale)`，非 1× 起点连续放大/缩小不再累积偏移；新增 2×→4×（62.5）、4×→2×（−62.5）回归。
- 可见页渲染：新增 `PdfPreloadPolicy.renderPlan`，`visiblePages` 通过 `snapshotFlow` 单独跟踪；同屏超过 3 页时全部可见页优先加载，再补首尾各 1 页预加载；已淘汰但仍显示的位图保留到离开组合，避免绘制已回收 Bitmap。

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew compileModernDebugKotlin testModernDebugUnitTest --tests com.tanuki.PdfRenderPolicyTest --tests com.tanuki.ZoomPolicyTest` | 基线改造前：BUILD SUCCESSFUL（7m26s） | 确认 worktree/JDK17/SDK 可用，既有 PDF/缩放测试通过 |
| `./gradlew compileModernDebugKotlin` | 改造后：BUILD SUCCESSFUL（3m08s，无新增编译警告） | 仅编译 modernDebug，不代表其他 flavor |
| `./gradlew testModernDebugUnitTest --tests 'com.tanuki.Pdf*' --tests 'com.tanuki.ZoomPolicyTest'` | BUILD SUCCESSFUL，52 tests / 0 failures（PDF 相关 46 + Zoom 6） | 纯 JVM，使用 Android 桩类；未触真机 |
| `./gradlew testModernDebugUnitTest` | 提交前最终复跑：BUILD SUCCESSFUL，247 tests / 0 failures / 0 errors / 0 skipped | 完整 JVM 套件；不能替代设备/仪器 |
| `./gradlew compileModernDebugKotlin`（open 回调调用点清理后） | BUILD SUCCESSFUL（1m34s） | 仅调用点整理，未改策略行为 |
| 追加自审修复后 `./gradlew testModernDebugUnitTest --tests 'com.tanuki.Pdf*' --tests 'com.tanuki.ZoomPolicyTest'` | BUILD SUCCESSFUL，52 tests / 0 failures | 修复适应页面水平居中与文件缺失中文原因透传；未改缓存/缩放策略 |
| 复审修复后同目标测试 | BUILD SUCCESSFUL，59 tests / 0 failures | 新增首尾可达、非 1× 补偿、同屏 >3 页渲染集合回归 |
| 复审修复后完整 `./gradlew testModernDebugUnitTest lintModernDebug` | BUILD SUCCESSFUL，254 tests / 0 failures / 0 errors / 0 skipped；lint 通过 | 最终提交前复跑；未跑设备/仪器 |
| `python3 scripts/check-handoff.py --record docs/handoffs/20261109-1819-pdf-native-reading.md`、`python3 scripts/check-handoff.py` | 见本轮收尾输出 | 以实际命令输出为准 |
| `python3 scripts/package-source.py` | 见本轮收尾输出 | 只写 `.gitignore` 的 `artifacts/`，不作为发布附件 |

- 哪些是前一轮结果：F1 的 126 JVM、PR #16 合并结果均为历史，本轮未复用其结论。
- 未测试、超时、失败和暂未复现：真机/模拟器大 PDF 连续滚动、加密/损坏 PDF、5x 放大清晰度、手势冲突、深色主题、低内存设备；lint、assemble、Android 仪器未执行。

## 未完成与暂停项

- 未完成：真实设备交互与内存验收；lint/assemble/仪器；独立 Review、合并、发布均未授权/未执行。
- 外部阻塞：无（本轮可离线完成）。
- 用户暂停：API Key 保存问题仍按既定要求暂停，未触碰。

## 下一步

1. 由独立 QA 在测试 AVD/真机安装候选 APK，使用合成大文档（≥500 页）、加密 PDF、损坏 PDF 验证连续滚动、预加载、跳页、1–5x 缩放与内存曲线；记录设备型号、API、堆大小与 logcat。
2. 独立 Codex High Review 以 PR 最新完整 SHA 为准；通过后再讨论合并/发布，Worker 不代合、不触发 Review。
3. 若设备上 5x 文字清晰度不足，可在不突破单页像素/缓存预算的前提下评估按缩放级别重渲染当前页。

## 约束与风险

- 数据/设备/权限边界：未读写真实 PDF、未安装应用、未使用设备；未新增依赖、未改 DSH/协议/AO 配置、未改安全与暂停项；未发布。
- 关键决策及理由：连续翻转用 `LazyColumn` + 单文档会话，避免每页重开 PDF；缩放采用视口内容层相机缩放 + 横向平移，纵向仍由列表滚动，从而放大后仍能滚动全文档；页缓存按字节而非页数，避免不同页尺寸导致内存超限；显示中的位图延迟回收，避免 Compose 绘制已回收 Bitmap。
- 风险/限制：单页渲染受 2048 长边、4,000,000 像素与缓存预算约束，5x 时文字会放大但清晰度有限；缓存预算取堆的 1/8（24–64 MiB），低内存设备可能更频繁重渲染；512 MiB 文件上限是产品限制，非 PdfRenderer 硬限制；手势为自绘，真机手感与无障碍未验证。
- 失败尝试与避免重复踩坑：旧实现每次翻页重新打开 `PdfRenderer`，大文档会反复解析；新实现必须保持所有 `PdfRenderer` 调用在同一 IO 调度器并加互斥锁，避免关闭与渲染竞争。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未构建 APK、未发布；`package-source.py` 只刷新忽略目录中的源码包用于检查。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未覆盖任何设备数据、未签名发布。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：本轮未删除历史记录；F1 PDF 预览记录仍是该功能的来源依据，保留。新增记录通过 `AGENT-STATE` 链接。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。
