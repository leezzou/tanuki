# 交接：20261109-1807-xlsx-native-preview

- Agent/会话标识：android_agent-32（Tanuki DSH Worker）
- 时间：2026-11-09 18:07 CST
- 状态：已完成（PR [#23](https://github.com/leezzou/tanuki/pull/23) 待独立 Review；未合并、未发布）
- 前一记录：[DSH 已安装版本管理](20261109-1539-dsh-installed-version-manager.md)

> 本记录只描述本轮 XLSX 应用内原生预览。未读取或写入真实凭据、日志或设备数据；未改官方 DSH UI、协议、AO 配置和既有暂停项。

## 用户目标与范围

- 用户本次实际要求：为工作区文件查看器增加 XLSX 应用内原生只读预览；解析 OOXML（workbook、sharedStrings、styles、sheet、rels），不依赖 LibreOffice 或外部 App；实现多工作表、行列表头、冻结窗格、列宽/行高、合并单元格、基础字体/填充/边框、数字/日期/布尔/公式缓存值、双向滚动与缩放；具备 zip/XML/数量/路径安全上限与中文错误；明确保真边界；补合成 XLSX 的 JVM 回归；只读，不修改官方 DSH UI。
- 本次包含：新增纯 Kotlin OOXML 解析器与格式化器、`FileKind.XLSX` 分类/MIME、Compose 原生表格预览、Viewer 接线、JVM 回归、文档与交接。
- 本次不包含：`.xls`/`.xlsm` 支持、公式重算、图表/宏/透视表/外部链接/图片/条件格式还原、设备/仪器测试、lint/assemble、发布或合并。
- 用户改变或暂停的要求：无新增暂停；API Key 保存、真实模型/账号与真机验收继续按既有记录暂停。

## 当前状态

- 已实现：XLSX 只读解析与原生表格预览已接入 `WorkspaceFileViewer`；支持多 sheet、行号/列标表头、冻结行/列、列宽/行高、合并单元格、基础字体/填充/四边边框、水平/垂直对齐、数字/百分比/日期/时间/布尔/错误值、公式缓存值与 `date1904`。
- 已验证（本轮新跑）：`compileModernDebugKotlin` BUILD SUCCESSFUL；完整 `testModernDebugUnitTest` BUILD SUCCESSFUL，230 tests / 0 failures / 0 errors / 0 skipped；新增 3 个测试类共 24 项（解析 17、格式化 4、缩放策略 3），并在 `WorkspaceFileKindTest` 增补 1 项，合计 25 项。
- 待验证：真机/模拟器上的双向滚动、冻结区手势、缩放可读性、大表滚动性能、深色主题与 CJK 字体；真实 Excel/WPS 生成文件（含主题色、隐藏行列、复杂格式）；lint、assemble、仪器测试。
- 当前分支：`ao/xlsx-preview/xlsx-native-preview`（从会话分支 `ao/xlsx-preview/root` 创建），基于 `main=2f3fac9301c7c3484df85e99091e8f63dddc0a48`；独立 `/tmp` clone 提交/推送，PR [#23](https://github.com/leezzou/tanuki/pull/23) 已创建，未触发 AO Review。
- 关键事实与源码：解析与上限在 `app/src/main/kotlin/com/tanuki/storage/XlsxWorkbook.kt`；格式化在 `XlsxValueFormatter.kt`；表格 UI 在 `app/src/main/kotlin/com/tanuki/ui/WorkspaceXlsxPreview.kt`；缩放/尺寸策略在 `XlsxPreviewPolicy.kt`；入口接线在 `WorkspaceFileManager.readXlsx` 与 `WorkspaceFileViewer`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/storage/XlsxWorkbook.kt` | 新增 OOXML XLSX 只读解析器与模型：workbook/rels/sharedStrings/styles/sheet、合并单元格、冻结窗格、列宽行高、公式缓存值；zip 条目/总量/单条目、XML 深度/DTD/实体、sheet/行/列/单元格/共享字符串/合并区域/文本预算上限；合并生成的起点与 covered 占位同样计入共享单元格预算；路径穿越与外部链接拒绝；中文错误。 |
| `app/src/main/kotlin/com/tanuki/storage/XlsxValueFormatter.kt` | 新增数字/百分比/日期/时间/布尔格式的纯函数；处理 1900 闰年偏差、1904 日期系统与最小小数位。 |
| `app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt` | `FileKind` 增加 `XLSX`；`.xlsx` 分类与 MIME；新增 `readXlsx`（IO 锁内有界解析，不暴露路径）。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceXlsxPreview.kt` | 新增 Compose 原生表格：sheet 选择、行/列表头、冻结行/列、合并单元格按跨度占位、样式映射、LazyColumn 行虚拟化、共享水平滚动、按钮缩放、保真边界提示与截断提示。 |
| `app/src/main/kotlin/com/tanuki/ui/XlsxPreviewPolicy.kt` | 新增列字母、0.5–2.0x 缩放步进/clamp、列宽/行高 dp 换算等纯策略。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | 增加 `XLSX` 分支与全宽布局；未改 PDF/DOCX/图片/视频逻辑。 |
| `app/src/test/kotlin/com/tanuki/XlsxWorkbookParserTest.kt` | 合成 XLSX fixture 回归：多 sheet、类型/公式缓存、样式、合并/冻结、列宽行高、超限、损坏包/缺 workbook/坏 XML、zip 条目与总量/单条目、谎报解压大小的 bomb、共享字符串上限、路径穿越/外部链接、绝对包内路径、DOCTYPE/深层 XML/未定义实体、1904 日期、空表。 |
| `app/src/test/kotlin/com/tanuki/XlsxValueFormatterTest.kt` | 数字/千分位/百分比/日期时间/1900 闰年/1904/布尔回归。 |
| `app/src/test/kotlin/com/tanuki/XlsxPreviewPolicyTest.kt` | 列字母 A/Z/AA、缩放 clamp/步进、列宽行高边界回归。 |
| `app/src/test/kotlin/com/tanuki/WorkspaceFileKindTest.kt` | 增加 `.xlsx` 映射，并确认 `.xls`/`.xlsm` 不被当作已支持。 |
| `docs/WORKSPACE-STORAGE.md` | 更新查看器支持的格式与 XLSX 保真边界，避免旧“Office 仅外部打开”描述过期。 |
| `docs/AGENT-STATE.md`、`docs/TEST-REPORT.md`、本记录、`docs/handoffs/CURRENT.json` | 状态、验证证据与交接印章。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin --console=plain` | BUILD SUCCESSFUL（本轮执行） | 仅编译 modernDebug，未 assemble/安装。 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.Xlsx*' --tests 'com.tanuki.WorkspaceFileKindTest'` | BUILD SUCCESSFUL，27 tests / 0 failures（本轮执行） | 目标回归，含 17 解析 + 4 格式化 + 3 策略 + 3 文件分类（含原有 2 项）。 |
| `./gradlew :app:testModernDebugUnitTest --console=plain` | BUILD SUCCESSFUL，230 tests / 0 failures / 0 errors / 0 skipped（本轮最终复跑） | 全量 JVM；结果目录 `app/build/test-results/testModernDebugUnitTest/`。时间格式回归补 `mm:ss` 的分钟/秒语义。 |
| 本记录与状态文档检查 | `python3 scripts/check-handoff.py`、`check-publication.py --git`、`package-source.py` 结果见“产物与校验” | 文档/脚本变化不影响产品行为。 |

- 前一轮结果：不适用；本轮未复用旧 XLSX 测试（此前没有 XLSX 实现）。
- 未测试、超时、失败：无 JVM 失败；真机/模拟器、lint、assemble、仪器、真实 Excel 文件、真实大表性能均未执行。

## 未完成与暂停项

- 未完成：PR [#23](https://github.com/leezzou/tanuki/pull/23) 的 P1 复审意见（合并占位未计入预算）已修复并补多表预算回归；仍需独立 Codex High Review 与用户明确合并授权。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收继续暂停；本轮未触碰。
- 真机大表（上万行、宽表、合并跨冻结区）滚动与缩放性能未验证，不能据此宣称完整 Excel 兼容。

## 下一步

1. PR [#23](https://github.com/leezzou/tanuki/pull/23) 已创建（base `main`），正文包含支持/不支持清单、改动文件、测试结果与未验证项；不合并、不触发 AO Review。等待编排器/用户决定是否触发独立 Review。
2. 由 Orchestrator/用户决定是否触发当前 head 的独立 Codex High Review；Review 意见逐条修复并重审。
3. 获得设备授权后，在无真实任务的 ARM64 AVD/真机上验证滚动、冻结、缩放、大表与深色主题，并记录 AVD/serial 与 APK 哈希。
4. 未获用户明确授权前不合并、不发布、不创建 tag/Release。

## 约束与风险

- 数据/设备/权限边界：只读工作区文件；不解压到磁盘、不执行宏/公式/外部链接；不读取真实 Key/Cookie/日志；未操作设备。
- 关键决策：不引入第三方 XLSX 库，直接用 `ZipFile` + `XmlPullParser` 读 OOXML；缩放通过重算列宽/行高/字号而非整表 `graphicsLayer`，保证滚动范围与缩放一致；合并单元格只保留起点跨度和文本，跨冻结列时按冻结边界裁剪，跨行边框为近似；界面最多显示 8 个冻结行、3 个冻结列，其余仍随表体滚动，避免冻结区占满手机视口；超限采用截断 + 中文提示，损坏/越界/zip bomb 直接拒绝。
- 保真边界：明确不支持图表、宏、透视表、外部链接、图片、复杂条件格式、隐藏行列、主题色、公式重算和富文本多字体；长文本单行省略号显示。内置预览不宣称完整 Excel。
- 失败尝试/避免重复：Kotlin 字符串模板中 `$label解压后...` 会把中文当作标识符，已改为 `${label}`；共享 `ScrollState` 的多行水平同步需在设备上确认手势体验。

## 产物与校验

- 本轮不产出 APK/源码 ZIP 发布件；交付为源码 PR 与文档。
- 未安装、未签名、未发布；不涉及版本号、证书或发布目录。
- 过期交接清理：本轮未删除既有记录。原因：F1 与视频记录仍承载待设备验收项，DSH #20 与已安装版本管理仍待 Review/PR，均非“已完成且结论已完全迁移”的过期记录；本轮记录、TEMPLATE 与 CURRENT.json 保留。
- 文档/脚本变化后需重新运行交接印章与源码包检查；不把源码 ZIP 自身哈希写回本记录。
