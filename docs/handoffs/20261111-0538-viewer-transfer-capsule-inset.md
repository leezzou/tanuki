# 交接：20261111-0538-viewer-transfer-capsule-inset

- Agent/会话标识：android_agent-61（AO worker）
- 时间：2026-11-11 05:38 CST
- 状态：已完成本地实现与 JVM/lint 验证；设备验证、独立审查、合并与发布未执行
- 前一记录：[文件页布局瘦身](20261110-1936-files-layout.md)（已合并，PR #58）

> 普通 PR 只新增本记录，不修改 `docs/AGENT-STATE.md` 或 `docs/handoffs/CURRENT.json`；共享状态由集成维护方统一刷新。

## 用户目标与范围

- 用户本次实际要求：修复已合并进 main 的查看器布局 bug——底部 48dp 操作栏与 48dp 文件传输胶囊只上移 12dp，重叠约 36dp，导致导出/复制/关闭等按钮点不到；基于 `origin/main=f338bdb4` 建分支。
- 本次包含：查看器底部操作栏高度与传输浮层避让的纯计算常量/函数；阅读页在操作栏可见时叠加操作栏高度 + 12dp；列表页保持既有 12dp 间距、不新增固定块；JVM 重叠回归；相关文档与本记录。
- 本次不包含：文件传输业务逻辑、列表页布局重构、设备/仪器测试、独立 Review、合并、发布、APK/源码包。
- 用户保留要求：只改查看器与传输浮层相关文件；不碰 `AGENT-STATE.md`/`CURRENT.json`/`TEST-REPORT.md`；中文注释；提交 author/committer 为 leezzou；PR 标题 `fix(files): 查看器传输胶囊避让底部操作栏`；不合并、不发布；文档/注释不出现外部参考实现名称。

## 当前状态

- 已实现：新增 `FileViewerLayout` 纯布局常量与避让函数；查看器底部操作栏复用 48dp 常量；阅读页两个早返回分支把“操作栏可见”传给传输浮层，胶囊底距改为 `导航 inset + 48dp 操作栏 + 12dp`；列表页与全屏阅读保持 `导航 inset + 12dp`。补 3 项 JVM 几何回归。
- 当前分支：`ao/viewer-capsule-inset/root`，基线 `origin/main=f338bdb4194ab697647122de164b0a502b219c6c`（已 fetch 核对）；PR [#60](https://github.com/leezzou/tanuki/pull/60)，实现提交 `a8e9667`；无运行中设备/安装任务。
- 关键源码：`app/src/main/kotlin/com/tanuki/ui/FileViewerLayout.kt`（新增）、`WorkspaceFilesPage.kt`、`WorkspaceFileViewer.kt`、`app/src/test/kotlin/com/tanuki/FileViewerLayoutTest.kt`（新增）。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/FileViewerLayout.kt` | 新增阅读页操作栏 48dp、胶囊 48dp、间距 12dp 常量，以及 `transferPillBottomPadding(bottomInset, actionBarVisible)` 纯计算；把“操作栏可见时叠加 48dp 再留 12dp”变成可单测逻辑。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFileViewer.kt` | 底部三个操作按钮的高度改用 `FileViewerLayout.ActionBarHeight`，让布局事实与避让常量同源；补充中文说明注释。 |
| `app/src/main/kotlin/com/tanuki/ui/WorkspaceFilesPage.kt` | `FileTransferOverlay` 增加 `viewerActionBarVisible` 参数；查看器两个早返回分支传入 `!fullscreen`，胶囊按操作栏高度避让；列表页默认 false，维持原有 12dp，不新增固定块。 |
| `app/src/test/kotlin/com/tanuki/FileViewerLayoutTest.kt` | 新增 3 项 JVM 回归：列表页仍为 12dp；阅读页胶囊在操作栏上方且保留 12dp；历史 12dp 定位会重叠 36dp。 |
| `docs/WORKSPACE-STORAGE.md` | 文件页布局说明补充阅读页胶囊避让规则，明确列表页/全屏阅读不变。 |
| `docs/handoffs/20261111-0538-viewer-transfer-capsule-inset.md` | 本记录。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git fetch origin main && git rev-parse origin/main` | `f338bdb4194ab697647122de164b0a502b219c6c` | 基线核对 |
| `./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest --tests 'com.tanuki.FileViewerLayoutTest'` | 通过（BUILD SUCCESSFUL，3 tests / 0 failed） | 首次失败为测试断言把 24+48+12 写成 72dp，修正为 84dp 后通过；实现未改 |
| `./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest :app:lintModernDebug` | 通过（BUILD SUCCESSFUL in 30m 01s） | 完整 JVM 1047 tests / 108 个结果文件 / 0 failures / 0 errors / 0 skipped；lint 0 errors / 104 warnings / 1 hint；未跑设备 |
| 手工验收（未执行，需设备） | 未执行 | 设备由独立 QA 独占；步骤见下 |

### 手工验收步骤（设备）

1. 安装当前 PR head 构建的 debug 包，打开文件页，分别打开 PDF、文本、图片查看器。
2. 点“导出此文件”（或“复制到工作区”）触发传输，确认右下角胶囊出现。
3. 确认胶囊底边完全在底部操作栏分隔线以上、间距 ≥ 12dp；“导出此文件”“使用其他应用打开”“复制到工作区”和胶囊取消按钮都可点击。
4. 若打开视频并进入全屏，确认操作栏隐藏时胶囊回落至导航栏上方 12dp；退出全屏后胶囊重新上移。
5. 返回文件列表，确认胶囊仍只在导航栏上方 12dp，列表没有新增固定块或出现跳动。
6. 在 360dp 窄屏、大字体和深色模式下重复步骤 2–5。

## 未完成与暂停项

- 未完成：Android 仪器测试、设备/模拟器视觉与触控验收、独立高能力模型审查、GitHub CI 结果核对、PR 审查意见处理（若有）。
- 外部阻塞：设备由独立 QA 独占，本会话未申请也未运行模拟器/真机。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收、合并与发布；本记录不触碰这些暂停项。

## 下一步

1. PR [#60](https://github.com/leezzou/tanuki/pull/60) 已创建；等待 GitHub CI 与独立审查，不合并、不发布。
2. 等待 GitHub CI 与独立审查；由独立 QA 按上方手工步骤在模拟器/真机验证 PDF/文本/图片、全屏视频、360dp、大字体和深色模式。
3. 处理 review comment 后推送新提交；最新 SHA 重新验证，旧审查结论不沿用。

## 约束与风险

- 数据/设备/权限边界：只改原生 Compose 布局计算，不触碰文件写入授权、传输任务、网络或真实数据。
- 关键决策：先定位为“同一 Box 内浮层只按导航 inset 定位”的几何问题，再用显式、可单测的间距函数修复，避免依赖设备截图。
- 失败尝试与避免重复踩坑的结论：首次定向测试失败是测试自身把 `24 + 48 + 12` 算成 72dp；实现未变，修正为 84dp 后通过。后续同类几何断言应直接写清三项加数，避免口算错误。

## 产物与校验

- 本轮未生成 APK、源码 ZIP 或发布附件；不覆盖安装、不发布、不合并。
- PR [#60](https://github.com/leezzou/tanuki/pull/60)，目标 `main`；实现提交 `a8e9667`，后续仅补本记录的 PR 号。
- 已运行 `python3 scripts/check-publication.py --git`：734 public files / 731 index entries / 0 findings。
- `git diff --check` 通过；提交前再跑 `check-handoff.py --pr --record docs/handoffs/20261111-0538-viewer-transfer-capsule-inset.md`。
- 普通 PR 不刷新 `AGENT-STATE.md`/`CURRENT.json`/`TEST-REPORT.md`；只新增本记录。
