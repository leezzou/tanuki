# 交接：横屏紧凑阅读与复制结果分类

- Agent/会话标识：android_agent-75（DSH Worker）
- 时间：2026-11-11 14:25 CST（UTC+8）
- 状态：已完成（PR 待创建）
- 前一记录：[用户旅程静态审计](20261111-1050-ux-audit.md)；本任务依据独立 QA 分支上的设备复测记录（该记录尚未进入 main，不在此处链接）

## 用户目标与范围

- 用户本次实际要求：修复设备 QA 报的 QA-04（横屏打开 GBK `.cpp` 时正文仅约 48px 高）和 QA-09（复制同名文件被跳过时结果只给通用排查文案）。
- 本次包含：
  - 横屏查看器紧凑布局：正文至少占 60%，工具折叠为「Aa」面板，编码提示折叠为一行，分页与导出收进 48dp 底部工具条。
  - 竖屏查看器保持现状。
  - 复制/移动结果按同名、空间不足、权限、其他原因分类，提供「查看 / 清理空间 / 去授权」动作。
  - JVM 回归：横屏高度预算可计算断言、结果文案与原因分类映射。
- 本次不包含：设备安装/真机验证、正式发布、合并、其他 QA 项、真实模型/凭据。
- 用户暂停/未授权：合并、发布；设备由独立 QA 独占，本会话不安装、不占设备。

## 当前状态

- 已实现：
  - 横屏以实际可用宽高判定；顶部栏 48dp，编码提示单行 24dp，底部工具条 48dp，正文用剩余高度。
  - 411dp 高横屏（2400×1080、约 411dp）正文约 291dp / 70.8%；360dp 高约 240dp / 66.7%，均在 60% 以上。
  - 极短横屏若显示编码提示会跌破 60%，自动收起提示；当前编码仍在底栏显示，可从 Aa 面板重新打开编码菜单。
  - 「Aa」面板包含搜索、字号、行号、自动换行、文本编码、复制本段；底栏包含上一段/下一段、位置状态、导出和更多操作。
  - 竖屏继续走原顶部栏、原工具行、原分页行和原底部操作栏，未换布局。
  - 复制/移动结果按原因生成文案：同名「N 个文件同名已存在，已跳过（未覆盖）」+「查看」；空间不足「…请清理空间后重试」+「清理空间」；权限「…请授权后重试」+「去授权」；混合原因「查看」后按组列出文件名。
  - 工作区内复制/移动和手机存储→工作区批量传输共用同一结果模型；批次开始前的空间/权限失败也进入分类结果。
- 已验证：最终源码全量 JVM 1233 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 110 warnings / 1 hint；定向 18 项通过。
- 待验证：模拟器/真机横屏实际像素分配、真实同名冲突/空间不足/权限拒绝流程、TalkBack 与 1.3x 字体下的横屏点击区域。
- 当前分支：`ao/landscape-reader/viewer-copy-result`，基于 `origin/main=6e1e833`（含 PR #75）。提交后以 PR head 为准。
- 关键源码：`app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt`、`WorkspaceFilesPage.kt`、`FileViewerLayout.kt`、`FileOperationOutcome.kt`、`app/src/main/kotlin/com/tanuki/storage/WorkspaceFileManager.kt`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/FileViewerLayout.kt` | 新增横屏 48/24/48dp 尺寸、60% 正文下限、高度预算和极短屏提示自动收起判定 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | 按实际宽高进入横屏紧凑布局；Aa 面板、单行编码提示、48dp 底栏；竖屏原路径保持 |
| `app/src/main/kotlin/com/tanuki/ui/FileOperationOutcome.kt` | 新增复制/移动结果模型：异常分类、分组文案、恢复动作、手机传输报告映射 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt` | 旧通用失败文案替换为分类结果 Snackbar + 详情弹层；接入工作区复制和手机传输 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 空间不足动作打开系统存储管理，失败时回退应用信息 |
| `app/src/test/kotlin/com/tanuki/FileViewerLayoutTest.kt` | 补横屏判定、411/360dp 高度预算、60% 下限、提示开合、极短屏收起回归 |
| `app/src/test/kotlin/com/tanuki/FileOperationOutcomeTest.kt` | 补同名/空间/权限/其他分类、精确文案、动作、混合原因、传输报告映射 |
| `docs/handoffs/20261111-1425-landscape-copy-result.md` | 本记录 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git fetch --no-write-fetch-head origin main` | 成功；origin/main `6e1e833` | 基线核对 |
| `git merge --ff-only origin/main` | 快进 `d80d660..6e1e833` | 含 PR #75 文件页传输重构 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.FileViewerLayoutTest' --tests 'com.tanuki.FileOperationOutcomeTest' -x buildNativeRuntime` | 通过，18 tests / 0 failures | JDK17；横屏预算与结果分类 |
| 同命令加 `--tests 'com.tanuki.NpmAdaptiveWorkerPoolTest'` | 通过 | 该时序用例在全量首跑时曾失败 1 次，单跑通过 |
| `./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest :app:lintModernDebug` | BUILD SUCCESSFUL（4m18s）；1233 tests / 123 文件 / 0 failures / 0 errors / 0 skipped；lint 0 errors / 110 warnings / 1 hint | 最终源码；未运行 Android 仪器测试 |
| `git diff --check` | 通过 | 提交前检查 |

- 前一轮结果与本次新验证分开：以上均为本次执行，未引用历史测试数字。
- 首轮全量 JVM 曾因 `NpmAdaptiveWorkerPoolTest` 时序断言失败 1 项，单跑通过后最终全量通过；不把该次失败当作产品回归。
- 未测试：设备横屏真实布局、真实复制冲突/空间不足/权限失败、TalkBack、1.3x 字体横屏、旋转后状态保持。

## 未完成与暂停项

- 未完成：PR 创建后需要独立审查和适用设备 QA；本次不合并、不发布。
- 外部阻塞：设备由独立 QA 独占，本会话未安装、未操作设备。
- 用户暂停：真实账号/模型验收、合并、发布均未授权。

## 下一步

1. 推送 `ao/landscape-reader/viewer-copy-result` 并创建 PR；PR 正文写明横屏结构、文案映射、测试和未验证项。
2. 由编排者安排当前 head 的独立 Codex High 审查；若审查要求改动，按 PR 流程继续修复。
3. 独立 QA 在横屏 2400×1080、1.3x 字体和深色下复测 QA-04；用真实同名/空间/权限场景复测 QA-09。

## 约束与风险

- 未读取真实 Key、Cookie、token、会话或备份；未安装设备、未操作独立 QA 设备。
- 中文注释；author/committer 使用 `leezzou <115924933+leezzou@users.noreply.github.com>`。
- 横屏判定基于实际宽高，不绑定具体机型；极短横屏会收起编码提示以守住 60%。
- 已知风险：结果提示仍是 Snackbar，自动消失后无法再次打开详情；详情弹层最多列 12 个文件名并提示剩余数量。
- 竖屏未改布局，但新增的 `BoxWithConstraints` 包裹层仍需设备回归确认无额外测量开销。

## 产物与校验

- 本次无 APK、无源码 ZIP 发布；PR 只含源码、测试和本交接。
- 未覆盖安装、未签名、未合并、未发布。
- 提交前未发现本次范围内可删除的过期交接记录；未改 `AGENTS`、`AGENT-STATE.md`、`CURRENT.json` 或其他共享状态。
- 待执行：`python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1425-landscape-copy-result.md`、`python3 scripts/check-publication.py --git`。
