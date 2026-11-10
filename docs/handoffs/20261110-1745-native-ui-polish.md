# 交接：20261110-1745-native-ui-polish

- Agent/会话标识：android_agent-56（AO worker）
- 时间：2026-11-10 17:45 CST
- 状态：已完成评审修复，待复审（PR [#49](https://github.com/leezzou/tanuki/pull/49)；未合并、未发布）
- 前一记录：[0.1.11 发布准备与交接集成](20261110-1636-release-0.1.11.md)

## 用户目标与范围

- 用户本次实际要求：基于 `origin/main=4f2a3717` 做原生界面的导航、适配与可用性打磨；原生/官方切换不丢状态，返回键先关面板再退页，通知点击返回栈合理，会话管理/审批二级页返回不重置列表；覆盖深色、1.3x/2x 字体、横屏、360dp、系统栏 inset；关键按钮有 `contentDescription`、触控区不小于 48dp、对比度可读；空态/加载/错误三态统一且中文可操作；避免整页重组、稳定列表 key、冷启动不做重活；补 JVM 可测部分，设备未验要标注；中文注释、作者/committer 为 leezzou、建 PR、不合并、不发布；不得出现参考实现名称。
- 本次包含：新增通用导航/布局/三态/无障碍/对比度/冷启动策略与组件；新增 JVM 回归；新增专题文档与接线清单；在 `docs/NATIVE-UI.md` 增加入口。
- 本次不包含：不改 `DshNativePage.kt`、`MainActivity.kt`；不改 `DshComposer*`、`DshApproval*`、`DshSessionManager*` 内部；不做页面最终接线；不改 WebView、协议、官方包、DSH 数据或安全策略；不运行设备/模拟器测试。
- 用户改变或暂停的要求（需保留）：API Key 保存、真实模型/账号验收、真机验收仍暂停；未获合并/发布授权不合并、不发布。

## 当前状态

- 基线：`origin/main=4f2a3717d6d795fd6b94cdb695878d8119f818c1`（`release: Tanuki 0.1.11 workspacecheck candidate (#44)`）。
- 分支：`ao/native-ui-polish/ui-adaptivity`，从会话分支 `ao/native-ui-polish/root` 创建；提交 `6ef5076` 已推送，PR [#49](https://github.com/leezzou/tanuki/pull/49)。
- 已实现：导航策略、面板栈、二级页状态、导航历史、原生/官方切换状态；适配断点与 inset 骨架；48dp 触控区与无障碍按钮；三态中文组件；WCAG 对比度工具；冷启动预算；对应 JVM 测试与接线文档。
- 已验证：见“验证记录”；设备/模拟器未验证。
- 待验证：页面最终接线后的真实布局、像素、系统栏、输入法和返回栈；设备深色/字体/横屏/360dp。
- 关键事实与可定位源码：[NATIVE-UI-POLISH](../NATIVE-UI-POLISH.md)、`app/src/main/kotlin/com/tanuki/ui/TanukiNavigation.kt`、`TanukiLayoutPolicy.kt`、`TanukiAdaptiveComponents.kt`、`TanukiAccessibility.kt`、`TanukiStateViews.kt`、`TanukiContrast.kt`、`TanukiStartupPolicy.kt`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/TanukiNavigation.kt` | 新增返回优先级、面板栈、二级页保留状态、通知/切页历史、原生/官方切换状态；纯 Kotlin 便于 JVM 回归。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiLayoutPolicy.kt` | 新增 360dp/横屏/宽屏/字体缩放断点、内容最大宽度、48dp 触控区与按钮行数策略。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiAdaptiveComponents.kt` | 新增自动换行操作区、统一 safeDrawing inset 骨架、当前视口度量、可保存列表状态。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiAccessibility.kt` | 新增 48dp 触控区扩展、无障碍描述回退、关键按钮/文本按钮/图标按钮组件。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiStateViews.kt` | 新增空态/加载/错误三态组件与中文文案，错误可重试、空态可刷新。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiAdaptiveShell.kt` | 新增宽屏左侧导航+内容、窄屏抽屉外壳，以及宽屏并排/窄屏整页的列表+详情容器；窄屏抽屉宽度钳制在 240–360dp。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiContrast.kt` | 新增 WCAG 相对亮度与 4.5:1/3:1 判定。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiStartupPolicy.kt` | 新增首帧前/首帧后/可交互三档冷启动工作预算。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiListKeys.kt` | 新增会话/消息/工具卡稳定 key 与唯一性断言。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiSaveableState.kt` | 新增原生/官方切换与会话二级页状态的 Bundle Saver。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiAdaptiveComponents.kt` | 新增按会话的 `TanukiListStateStore` 与 `TanukiPageStateHost`，替代会随 key 变化的 `rememberSaveable`。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiNavigation.kt` | 返回上下文增加抽屉态与 `CloseDrawer` 优先级。 |
| `app/src/test/kotlin/com/tanuki/TanukiNavigationPolicyTest.kt` | 返回优先级、面板 LIFO、二级页保留列表/滚动、通知返回栈、切换保留状态回归。 |
| `app/src/test/kotlin/com/tanuki/TanukiLayoutPolicyTest.kt` | 320/360/横屏/1280dp、1.3x/2x、触控区与字体钳制回归。 |
| `app/src/test/kotlin/com/tanuki/TanukiStateAndAccessibilityTest.kt` | 三态中文文案、默认动作、描述回退、48dp 下限回归。 |
| `app/src/test/kotlin/com/tanuki/TanukiContrastTest.kt` | 黑白 21:1、阈值边界、明暗主题 8 组关键组合回归。 |
| `app/src/test/kotlin/com/tanuki/TanukiStartupPolicyTest.kt` | 冷启动三档工作预算与单调性回归。 |
| `app/src/test/kotlin/com/tanuki/TanukiListKeysTest.kt` | 稳定 key、冲突、非法 id 与重复 key 回归。 |
| `app/src/test/kotlin/com/tanuki/TanukiSaveableStateTest.kt` | 两个 Saver 的保存/恢复往返回归。 |
| `app/src/test/kotlin/com/tanuki/TanukiListStateStoreTest.kt` | 按会话 A→B→A、页面重进、负值钳制、清理与空 key 回归。 |
| `docs/NATIVE-UI-POLISH.md` | 新增组件说明、按页接线清单、适配矩阵、无障碍/三态/性能约束与未验证范围。 |
| `docs/NATIVE-UI.md` | 增加指向本次专题文档的入口链接。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:compileModernDebugKotlin` | 通过（1m42s；仅既有弃用/可读性告警） | 编译验证，不替代设备 |
| `./gradlew ... :app:testModernDebugUnitTest --tests 'com.tanuki.Tanuki*'` | 通过：8 个新测试类、54 tests / 0 failures / 0 errors / 0 skipped | 导航 16、布局 9、三态/无障碍 8、对比度 4、冷启动 4、稳定 key 5、Saver 2、滚动 store 6 |
| `./gradlew ... :app:testModernDebugUnitTest :app:lintModernDebug` | 完整套件通过：98 个结果文件、848 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 102 warnings / 1 hint | 848 = 基线 794 + 本轮 54；新增文件无 lint 告警 |
| 最终代码 `:app:compileModernDebugKotlin :app:lintModernDebug` | 通过（5m32s）；lint 0 errors / 102 warnings / 1 hint | 改用 `LocalWindowInfo` 后，新文件 4 条 `Configuration.screenWidthDp` 告警已消除 |
| 最终代码 `./gradlew ... :app:testModernDebugUnitTest :app:lintModernDebug` | 通过（3m24s，含 lint）：98 个结果文件、848 tests / 0 failures / 0 errors / 0 skipped；lint 0 errors / 102 warnings / 1 hint | 最终 head 复跑；未执行设备/仪器 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261110-1745-native-ui-polish.md` | 通过 | 共享状态允许落后，集成方统一刷新 |
| `python3 scripts/check-publication.py --git` | 通过：688 public files / 672 index entries / 0 findings | 未打印匹配到的敏感值 |

- 哪些是前一轮结果，哪些是本次新验证：0.1.11 的 794 tests/lint 是上一轮结果，不能算作本轮；本轮只记录本次新跑命令。
- 未测试、超时、失败和暂未复现的情况：设备/模拟器未执行；页面尚未接线，因此真实返回栈、深色/字体/横屏/360dp 像素、系统栏与输入法行为均未验证。

## 未完成与暂停项

- 已修复独立审查两条 P2：滚动状态改为 `TanukiPageStateHost` + `TanukiListStateStore` 显式保存；pending key 接受控制器负序号 `-1/-2`，官方 seq 仍拒绝负值。
- 未完成：页面接线（`TanukiUi.kt`、`DshNativePage.kt`、`DshSessionManager*`、`DshApproval*`、`MainActivity.kt`）由对应负责人按 [接线清单](../NATIVE-UI-POLISH.md#3-接线清单) 完成；本轮不越界修改。
- 未完成：Compose UI 仪器测试与设备适配验证。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；合并/发布未授权。

## 下一步

1. 页面负责人按 [接线清单](../NATIVE-UI-POLISH.md#3-接线清单) 接入导航、三态与按钮组件；优先 `DshNativePage.kt` 与 `MainActivity.kt`，再会话管理与审批面板。
2. 独立 QA 在明确 AVD/serial 上验证深色、1.3x/2x 字体、横屏、360dp、状态栏/导航栏/输入法、返回栈与通知进入目标会话；记录 AVD 名、serial、API、ABI、APK 哈希。
3. 页面接线完成后重跑完整 JVM/lint 与设备回归；不要用本轮的策略测试替代设备结论。

## 约束与风险

- 数据/设备/权限边界：未读写真实凭据、Cookie、token、用户会话或备份；未安装 APK、未启动设备、未执行破坏性命令；未改 WebView/协议/官方 npm 包/DSH 数据。
- 关键决策及理由：在“不改页面文件”的边界下，将可验证逻辑抽成纯 Kotlin 策略与通用 Compose 组件，页面只做接线；这样并行 PR 不冲突，且 JVM 可覆盖返回优先级、断点、文案和对比度。
- 失败尝试与避免重复踩坑的结论：`git switch -c` 首次被文件沙箱拒绝（主仓库 refs 在工作区外），已用一次更高权限完成；后续 Git 写操作仍需同样的权限。首次编译前的 `ao-worktree-env.sh` 会复制缓存，耗时约 36 秒，不是构建失败。
- 已知风险：`TanukiStateView` 父级 `contentDescription` 与子按钮语义可能重复朗读，接线时若出现重复播报，改为仅对标题/状态区设置 `liveRegion`；大字体下按钮高度会明显增加，长列表需保留尾部滚动空间。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件：本轮未生成 APK、未打包源码 ZIP；无发布附件。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布、未合并；PR [#49](https://github.com/leezzou/tanuki/pull/49) 等待独立审查与适用 CI/QA。
- 过期记录清理：本轮未删除任何旧交接；未发现仅属于本次且已过期的记录。`AGENT-STATE.md` 与 `CURRENT.json` 未修改，由集成维护方统一刷新。
- 仍需保留的验证/未完成记录：`docs/handoffs/20261110-1636-release-0.1.11.md`（0.1.11 候选与设备未验）、`docs/handoffs/20261110-1233-native-ui-chat.md`（原生聊天切片）等仍有未完成/设备验证事项，保留。
