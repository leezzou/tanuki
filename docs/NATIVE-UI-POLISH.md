# 原生界面导航、适配与可用性打磨

本文件记录 android_agent-56 在基线 `origin/main=4f2a3717` 上新增的通用导航、适配、三态、无障碍与冷启动组件，以及交给页面集成方的接线清单。它不替代 [原生界面与官方界面切换](NATIVE-UI.md) 的协议与安全边界。

## 1. 范围与边界

- 本分支只新增通用 UI/策略文件与 JVM 回归，不改 `DshNativePage.kt`、`MainActivity.kt`，不改 `DshComposer*`、`DshApproval*`、`DshSessionManager*` 内部。
- 原生页、通知入口、会话管理页的最终接线由对应页面负责人完成；本文第 3 节给出可逐项勾选的接线点。
- 未改 WebView、官方 npm 包、DSH 数据目录、协议、审批策略或任何安全校验。
- 设备/模拟器未在本轮执行，适配结论见第 4 节的“已验证 / 未验证”分列。

## 2. 新增组件

| 文件 | 作用 | JVM 可测部分 |
|---|---|---|
| `app/src/main/kotlin/com/tanuki/ui/TanukiNavigation.kt` | 返回优先级、面板栈、二级页状态、导航历史、原生/官方切换状态 | `TanukiBackPolicy`、`TanukiOverlayStack`、`TanukiSecondaryPagePolicy`、`TanukiTabHistory`、`TanukiDshSwitchPolicy` |
| `app/src/main/kotlin/com/tanuki/ui/TanukiLayoutPolicy.kt` | 360dp/横屏/大字体/宽屏断点、触控区、内容最大宽度 | 全部 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiAdaptiveComponents.kt` | 自动换行操作区、统一 inset 骨架、布局度量、按会话的列表滚动 store 与页面宿主 | `TanukiListStateStore` |
| `app/src/main/kotlin/com/tanuki/ui/TanukiAdaptiveShell.kt` | 宽屏左侧导航 + 内容、窄屏抽屉；列表/详情宽屏并排、窄屏整页详情 | 依赖 Compose，随编译验证 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiAccessibility.kt` | 48dp 触控区、必填 `contentDescription`、不限行关键按钮、图标操作 | `tanukiActionDescription`、触控下限 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiStateViews.kt` | 空态/加载/错误三态统一组件与中文文案 | `TanukiStateText` |
| `app/src/main/kotlin/com/tanuki/ui/TanukiContrast.kt` | WCAG 相对亮度与对比度阈值 | 全部 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiStartupPolicy.kt` | 冷启动到可交互的工作预算 | 全部 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiListKeys.kt` | 会话/消息/工具卡的稳定 key 与唯一性断言 | 全部 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiSaveableState.kt` | 原生/官方切换状态、会话二级页状态的 Bundle Saver | `TanukiDshSwitchStateSaver`、`TanukiSecondaryPageStateSaver` |

## 3. 接线清单

### 3.0 自适应外壳（P0-1）

- 宽屏 `>=840dp`：`TanukiAdaptiveShell` 用左侧导航栏 + 右侧内容；同一 `navigation`/`content` slot 不写第二套。
- 窄屏：同一外壳改为 `ModalNavigationDrawer`，内容全宽，`content` 的 `openNavigation` 打开抽屉；返回键先关抽屉。
- 列表+详情：`TanukiTwoPane` 在宽屏并排、窄屏只显示其中一页；窄屏详情用整页打开，返回键先 `onDetailBack` 回列表。
- 返回仲裁：抽屉打开时 `TanukiTwoPane` 的详情返回处理器自动禁用，shell 的抽屉处理器最后注册并优先；不会出现“先关详情、抽屉还在”的情况。
- 不再在业务页里写 `if (wide) ... else ...` 两套页面；只把布局差异收敛到这两个容器。

### 3.1 主壳导航（`TanukiUi.kt`）

1. 用 `TanukiTabHistory` 替代裸 `var tab`，或至少让通知入口走 `openNotificationTarget()`；`BackHandler` 使用 `TanukiBackPolicy.decide(TanukiBackContext(...))`。
2. `overlays` 放入当前可见的浮动面板、全屏浮层和二级弹层 id；顺序从底到顶。执行 `CloseOverlay` 时只关闭该层，不改 `tab`，也不重置列表。窄屏抽屉打开时把 `drawerOpen = true`，返回键先 `CloseDrawer`。
3. `webCanGoBack` 只在官方 DSH WebView 实际可后退时传 `true`；`atRootDestination = (tab == HOME_TAB)`。
4. DSH 页切换原生/官方时，用 `TanukiDshSwitchState` 配合 `TanukiDshSwitchStateSaver` 放进 `rememberSaveable`，保存 `selectedSessionId`、列表位置和草稿；页面容器用稳定位置的 `TanukiPageStateHost(if (native) "dsh-native" else "dsh-official")` 包裹，内部 `rememberTanukiListState("dsh-native:<sessionId>")` 自动按会话保存滚动。
5. 官方页与原生页共用同一 DSH tab 时，返回键顺序是：关闭面板 → 退出全屏 → 官方 Web 历史 → 离开 DSH 页 → 根页退出。

### 3.2 原生聊天页（`DshNativePage.kt`，由集成方接线）

1. 页面根用 `TanukiPageStateHost("dsh-native")`（或原生/官方二选一）包裹；内部 `LazyColumn` 使用 `rememberTanukiListState("dsh-native:${state.selectedId ?: "none"}")`。页面宿主必须位于切换点之外，否则页面移出时 store 也会销毁。
2. `timeline` 的 key 保持 `message-<seq>`、`tool-<callId>`、`pending-<seq>` 等稳定值；不要用列表下标。会话条改为 `LazyRow` 时同样使用会话 id 作为 key。
3. 页面没有会话的 `EmptyNativeState` 替换为 `TanukiStateView(EMPTY, subject = "官方会话", ...)`；加载态用 `TanukiStateView(LOADING, ...)`；读取失败用 `TanukiStateView(ERROR, ...)` 并与“重试”接通。
4. 顶部“刷新/官方界面”等关键图标按钮改用 `TanukiIconAction`；审批/错误面板的“允许一次/拒绝/重试”改用 `TanukiActionButton`，并用 `TanukiResponsiveActionRow` 包裹。这样 360dp、横屏和 1.3x/2x 字体下不会截断或横向溢出。
5. 审批面板、工具详情等作为 `overlays` 上报给主壳或页内 `TanukiOverlayStack`；返回键先关闭它们，再离开 DSH 页。
6. 首帧前只做 `RESTORE_VIEW_STATE`、`APPLY_THEME`、`READ_LOCAL_PREFERENCES`；会话列表/实时连接放首帧后；工作区扫描、更新检查、WebView 预热放可交互后（`TanukiStartupPolicy`）。列表数据变化只更新对应 `collectAsState`，不要在主壳根部收集高频进度。

### 3.3 会话管理页（由 `DshSessionManager*` 负责人接线）

1. 详情/模型/权限/目录作为 `TanukiSecondaryPageState.panels`；`BackHandler` 调 `TanukiSecondaryPagePolicy.back(state)`，只弹面板并保留 `listItems`、滚动位置和搜索条件。状态本身用 `TanukiSecondaryPageStateSaver` 保存。
2. 关闭详情后不要重新 `refresh()`；列表数据由控制器缓存。搜索框、筛选和滚动状态用 `rememberSaveable` 保留。
3. 未运行/加载/无会话/读取失败分别接入 `TanukiStateView` 的 `EMPTY`/`LOADING`/`ERROR`，错误态动作是“重试”，空态动作是“刷新”或“新建会话”。
4. 顶部返回、刷新、关闭详情等按钮使用 `TanukiIconAction`；确认对话框的主操作使用 `TanukiActionButton`，触控区不小于 48dp。

### 3.4 通知入口（`MainActivity.kt`，由集成方接线）

1. `consumeChatIntent` 只记录目标会话并请求 DSH 目的地，不清空当前 tab 历史；通知打开 DSH 时用 `TanukiTabHistory.openNotificationTarget()`。
2. 从设置/文件页收到通知，返回键应先回原页（`TanukiTabHistory.back()`），再按根页规则退出。
3. 原生目标未确认前保留 pending；列表失败仍按现有策略回退官方，不消费目标。该语义沿用 [原生界面与官方界面切换](NATIVE-UI.md)，本组件不改变通知协议。

### 3.5 审批/会话设置等二级弹层

1. `DshApprovalPanel` / `DshToolCard` / 模型与权限对话框的按钮改用 `TanukiActionButton` 或 `TanukiTextAction`，保留原有回调与风险文案。
2. 面板自身不要抢占返回键；把面板 id 交给页面级 `TanukiOverlayStack`，由页面统一按“先面板后页面”处理。
3. 窄屏/大字体下用 `TanukiResponsiveActionRow` 排列动作，避免固定宽度 `Row` 把“允许一次”挤出屏幕。

## 4. 适配矩阵

| 场景 | 期望 | 组件/策略 | 本轮状态 |
|---|---|---|---|
| 深色模式 | 正文、按钮、错误容器对比度 ≥ 4.5:1 | `TanukiTheme` + `TanukiContrast` | JVM 已验证明暗两套主题的 8 组关键组合；设备未验证 |
| 系统字体 1.0x | 关键按钮最多两行，布局不横向溢出 | `TanukiLayoutPolicy.keyButtonMaxLines` | JVM 已验证 |
| 系统字体 1.3x | 关键按钮不限行，操作区最多两列 | `TanukiLayoutPolicy`、`TanukiResponsiveActionRow` | JVM 已验证策略；设备未验证 |
| 系统字体 2.0x | 关键按钮单列、文字不限行、触控区放大 | 同上 | JVM 已验证策略；设备未验证 |
| 竖屏 360dp | 单列，内容宽度不超过视口 | `TanukiLayoutMetrics.isNarrow` | JVM 已验证；设备未验证 |
| 窄屏 320dp | 单列，不出现负宽度/横向溢出 | `TanukiLayoutMetrics` | JVM 已验证策略；设备未验证 |
| 手机横屏 800×360 | 两列，不启用两栏详情 | `TanukiLayoutMetrics.isLandscape` | JVM 已验证；设备未验证 |
| 平板/宽屏 ≥840dp | 左侧导航 + 右侧内容；列表/详情可并排 | `TanukiAdaptiveShell`、`TanukiTwoPane` | 编译通过；设备未验证 |
| 窄屏抽屉 | 导航以抽屉打开，返回先关抽屉 | `TanukiAdaptiveShell`、`TanukiBackPolicy.CloseDrawer` | JVM 策略已验证；设备未验证 |
| 状态栏/刘海 | 顶部内容不被遮挡 | `TanukiInsetScaffold` + `WindowInsets.safeDrawing` | 编译预期；设备未验证 |
| 导航栏/手势条 | 底部按钮与列表尾部不被遮挡 | `TanukiInsetScaffold`、`rememberTanukiListState` | 编译预期；设备未验证 |
| 输入法 | 输入区随 IME 上移，列表尾部可滚到 | `TanukiInsetScaffold` 的 `safeDrawing` + 页面 `imePadding` | 编译预期；设备未验证 |

未验证原因：本轮未安装 APK、未启动模拟器/真机、未切系统深色与字体缩放、未旋转屏幕；设备由独立 QA 独占。上述“JVM 已验证”只证明策略输入/输出，不证明真实像素、厂商输入法或系统栏行为。

## 5. 无障碍

- 关键按钮统一走 `TanukiActionButton` / `TanukiTextAction` / `TanukiIconAction`，三者都会写 `contentDescription`；空描述回退到可见文案或“操作”。
- 触控区下限 48dp；1.3x 约 62dp，2x 为 96dp。不要用小于 48dp 的自定义 `clickable` 包裹图标。
- 三态容器使用 `liveRegion = Polite`，加载/错误状态可被屏幕阅读器播报；错误态使用 `errorContainer/onErrorContainer`。
- 关键按钮文字在 1.3x/2x 不限行，使用换行而不是省略号；操作区由 `TanukiResponsiveActionRow` 自动调整列数。
- 装饰图标不重复声明描述；由外层可点击控件统一提供名称，避免 TalkBack 重复朗读。

## 6. 空态/加载/错误

- 统一入口：`TanukiStateView(kind, subject, detail, actionLabel, onAction)`。
- 文案：`TanukiStateText.of(...)` 默认中文；错误默认动作“重试”，空态默认动作“刷新”，加载态不显示假按钮。
- 页面只传业务主语（如“官方会话”“审批”“会话详情”）和可选补充说明；不要另写一套“加载中…”/“出错了”文案。
- `onAction` 为空时不渲染按钮，避免“看起来可点但没有行为”的假操作。

## 7. 性能

- `TanukiStartupPolicy` 把工作分成首帧前、首帧后、可交互后三档；冷启动到可交互只做状态恢复、主题和轻量偏好。
- 列表 key 必须稳定：`message-<seq>`、`pending-<负序号>`、`tool-<callId>`、会话 id；禁止下标 key。`TanukiListKeys.requireUnique` 可在开发期做唯一性断言。
- 滚动位置用 `TanukiPageStateHost` + `rememberTanukiListState(key)` 显式保存；store 通过 `TanukiListStateStoreSaver` 写入 Bundle，列表滚动时持续保存，Activity 重建/进程恢复可读回。key 必须包含页面与会话身份。不要只用 `rememberSaveable(key)`，key 变化会重置状态。
- 高频进度流只让进度组件订阅，不在主壳根部 `collectAsState`；避免整页重组。
- 切换原生/官方或进入二级页时复用已有控制器与列表快照，不重新认证、不重建服务、不重新 `refresh()`。
- `TanukiStateView` 与按钮均为纯展示组件，不持有业务状态；数据变化只更新传入参数。

## 8. 验证

本轮新增 JVM 回归：

- `TanukiNavigationPolicyTest`：面板优先、抽屉优先、全屏、Web 历史、根页退出；面板栈 LIFO；关闭二级面板保留列表与滚动；通知从设置进入 DSH 后返回设置；重复通知不增长历史；原生/官方双向切换保留两侧会话、滚动与草稿。
- `TanukiLayoutPolicyTest`：360dp、320dp、800×360 横屏、1280dp 宽屏、1.3x/2x 字体、触控区、字体缩放上下限。
- `TanukiStateAndAccessibilityTest`：三态中文文案、默认动作、空主语回退、无障碍描述回退、48dp 下限。
- `TanukiContrastTest`：黑白 21:1、阈值边界、明暗主题 8 组关键前景/背景组合。
- `TanukiStartupPolicyTest`：首帧前/首帧后/可交互三档工作预算与单调性。
- `TanukiListKeysTest`：同一业务 id 稳定、不同 id 不冲突、已落库/待发送消息区分、非法 id 与重复 key 拒绝。
- `TanukiSaveableStateTest`：原生/官方切换状态与会话二级页状态经 Saver 保存/恢复后完全一致。
- `TanukiListStateStoreTest`：按会话保存/恢复、页面重进、Saver 跨 Activity 重建、重建后 `LazyListState` 位置、未设置 key、负值钳制、单 key/全部清理与空 key 拒绝。

本轮实际：`compileModernDebugKotlin` 通过；新增 8 个测试类共 56 tests / 0 failures / 0 errors / 0 skipped；最终完整 JVM 套件 98 个结果文件、**850 tests / 0 failures / 0 errors / 0 skipped**；lint **0 errors / 102 warnings / 1 hint**。详细命令与限制见本轮交接记录 [20261110-1745-native-ui-polish](handoffs/20261110-1745-native-ui-polish.md)；设备结果不在此列。

## 9. 未完成与后续

- 上述接线清单尚未落到 `DshNativePage.kt` / `MainActivity.kt` / `DshSessionManager*`；这些文件由对应负责人修改，避免与并行 PR 冲突。
- 设备适配（深色、字体 1.3x/2x、横屏、360dp、状态栏/导航栏/输入法）未验证，需独立 QA 在明确 AVD/serial 上执行。
- 未新增 Compose UI 仪器测试；本轮只补 JVM 可测策略与组件编译。
