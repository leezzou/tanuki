# 原生界面设计令牌与基础组件

本文件记录设计令牌、基础组件以及页面接入结果。令牌与组件由 android_agent-59 建立（PR #59）；android_agent-64 在 `origin/main=62e4c30b` 上把令牌接入根主题、文件页、会话管理页和设置页，并补齐可 JVM 回归的视觉映射。聊天页（`DshNativePage`/`DshNativeController`）与目标/待办停靠条不在本次接入范围，只通过根主题继承新调色板，未改其源码。

- 基线：任务起始 `origin/main=62e4c30b`（2026-11-11）。
- 代码位置：`app/src/main/kotlin/com/tanuki/ui/design/`；页面视觉映射在 `app/src/main/kotlin/com/tanuki/ui/TanukiPageVisuals.kt`。
- JVM 回归：`app/src/test/kotlin/com/tanuki/ui/design/TanukiDesignTokensTest.kt`、`app/src/test/kotlin/com/tanuki/TanukiPageVisualsTest.kt`。
- 相关文档：[原生界面实现边界](NATIVE-UI.md)、[原生界面行为规格](handoffs/20261110-1759-native-ui-parity-spec.md)、[代码规范](code-standards.md)。

当前配色由 android_agent-87 在 `main=7cfe1446effa76d1e7901f839d6a22996778d013` 上实现，android_agent-73 从含#89动效的 `main=4710c5e08ffa8c86d8cdc1bffbec1a1052be0935` 落库；第2节配色取值表与落地说明逐行一致，保留主线动效工厂/时长及reduced-motion结构。当前集成验证见 [主题落库交接](handoffs/20261111-1819-sky-theme-landing.md)，下文历史验证均不代表本次设备验收。

## 1. 设计原则

1. **用途与可读性优先**：角色色使用明确 sRGB 值；保留 OKLCH 转换、WCAG 对比度及 alpha 合成能力。装饰色不作为可读文字或白字实底。
2. **系统字体优先**：中文标题、正文、说明全部跟随系统字体，不打包字体资源；等宽字体只用于代码和路径。三态组件复用 main 已有实现，主题包裹后自动采用本令牌。
3. **触控优先**：所有可点击控件至少 48dp 触控区；视觉胶囊可以更矮，但热区不变。
4. **层级克制**：只有 chrome 和 pill 允许模糊；sheet 和 card 使用不透明表面和细边框，保证密集内容可读。
5. **动效可关闭**：所有时长都走令牌；系统移除动画时时长统一为 0，循环动画改为静态。
6. **纯函数可测**：颜色生成、对比度和数值范围不依赖 Android/Compose，JVM 可直接锁定；对比度公式复用 main 的 `TanukiContrast`，避免两份阈值漂移。

## 2. 配色

### 2.1 用途约束

角色令牌直接指定 sRGB 值（下表省略不透明 alpha FF），不再由旧暖色中性阶梯取样；`TanukiColorMath` 继续支持 OKLCH、色域映射、WCAG 和透明合成。备用 OKLCH 中性色阶设为零彩度，避免暖色残留。

- 装饰天蓝：浅色 #0EA5E9、深色 #38BDF8；仅用于选中、进度、状态点、图标着色、细描边与小面积浅底。白字主按钮两种主题均为 #0369A1，按下色 #075985。
- 文字：浅色链接/可点击文字 #0369A1，深色 #7DD3FC。信息图标需 ≥3:1，浅色用深天蓝，深色用亮天蓝；纯装饰色不参加文字阈值。
- 弱灰：#8C929A / #6F767F 仅禁用、占位、装饰。说明色 #6B7280 在白底通过，但在 #F5F6F7 上为 4.47:1、在 #E6F4FE 上为 4.31:1，因此 `descriptionOn(background)` 在这些底色上改用 #5A6068；路径采用分组底也可读的说明色。
- 次按钮：浅色纯白底、#ECEDEF 描边、#111315 文字；深色使用 #1A1D21、#24282D、#F2F4F6。普通 tonal 次按钮也遵守此规则；Ghost 保留透明底。
- 成功/警告/危险只用于语义；深色单独取亮度。浅色危险实底 #DC2626 配白字；白底危险文字 #B91C1C。成功/警告实色配深色文字，浅底标签使用各自容器文字色。
- Material `primary` 供文字和选中控件，`onPrimary` 与之配对；白字按钮通过 `TanukiButton` / `TanukiPillButton` 读取独立 `primaryFill/onPrimary`，不把深色亮天蓝当白字底。Material 表面不叠加天蓝 tonal tint。

### 2.2 当前完整取值表

| 令牌 | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `canvas` | `#FFFFFF` | `#0E1012` | 页面与系统栏背景 |
| `surface` | `#FFFFFF` | `#1A1D21` | 卡片表面 |
| `surfaceRaised` | `#FFFFFF` | `#1A1D21` | 卡片表面 |
| `surfaceSunken` | `#F5F6F7` | `#16181B` | 次级背景 |
| `surfaceMuted` | `#F5F6F7` | `#16181B` | 分组背景 |
| `sheet` | `#FFFFFF` | `#1A1D21` | 弹层 |
| `chromeBase` | `#FFFFFF` | `#16181B` | 浮层底色 |
| `scrim` | `#66111315` | `#990E1012` | 带透明度的遮罩 |
| `textPrimary` | `#111315` | `#F2F4F6` | 主文字 |
| `textSecondary` | `#5A6068` | `#A8AFB8` | 次级正文 |
| `textTertiary` | `#6B7280` | `#9AA1AA` | 白底说明；其他背景用 descriptionOn |
| `textDisabled` | `#8C929A` | `#6F767F` | 仅禁用、占位及装饰 |
| `outline` | `#ECEDEF` | `#24282D` | 灰描边 |
| `outlineStrong` | `#ECEDEF` | `#24282D` | 灰描边；不作为信息图标 |
| `hairline` | `#ECEDEF` | `#24282D` | 分隔线 |
| `primary` | `#0369A1` | `#7DD3FC` | 链接与可点击文字 |
| `accent` | `#0EA5E9` | `#38BDF8` | 仅装饰、选中、进度、状态点、细描边 |
| `accentIcon` | `#0369A1` | `#38BDF8` | 信息图标，至少 3:1 |
| `primaryFill` | `#0369A1` | `#0369A1` | 白字主按钮实底 |
| `primaryPressed` | `#075985` | `#075985` | 主按钮按下色 |
| `onPrimary` | `#FFFFFF` | `#FFFFFF` | 白字主按钮前景 |
| `primaryContainer` | `#E6F4FE` | `#0B2B3A` | 选中态浅底 |
| `onPrimaryContainer` | `#0369A1` | `#7DD3FC` | 选中态文字 |
| `success` | `#16A34A` | `#4ADE80` | 语义状态实色/容器与配对前景 |
| `onSuccess` | `#052E16` | `#052E16` | 语义状态实色/容器与配对前景 |
| `successContainer` | `#F0FDF4` | `#102A1B` | 语义状态实色/容器与配对前景 |
| `onSuccessContainer` | `#166534` | `#86EFAC` | 语义状态实色/容器与配对前景 |
| `warning` | `#D97706` | `#FBBF24` | 语义状态实色/容器与配对前景 |
| `onWarning` | `#111315` | `#111315` | 语义状态实色/容器与配对前景 |
| `warningContainer` | `#FFFBEB` | `#30230E` | 语义状态实色/容器与配对前景 |
| `onWarningContainer` | `#92400E` | `#FCD34D` | 语义状态实色/容器与配对前景 |
| `danger` | `#DC2626` | `#F87171` | 语义状态实色/容器与配对前景 |
| `onDanger` | `#FFFFFF` | `#450A0A` | 语义状态实色/容器与配对前景 |
| `dangerContainer` | `#FEF2F2` | `#32181B` | 语义状态实色/容器与配对前景 |
| `onDangerContainer` | `#B91C1C` | `#FCA5A5` | 语义状态实色/容器与配对前景 |
| `info` | `#0369A1` | `#7DD3FC` | 语义状态实色/容器与配对前景 |
| `onInfo` | `#FFFFFF` | `#0E1012` | 语义状态实色/容器与配对前景 |
| `infoContainer` | `#E6F4FE` | `#0B2B3A` | 语义状态实色/容器与配对前景 |
| `onInfoContainer` | `#0369A1` | `#7DD3FC` | 语义状态实色/容器与配对前景 |
| `neutralStatus` | `#5A6068` | `#A8AFB8` | 语义状态实色/容器与配对前景 |
| `onNeutralStatus` | `#FFFFFF` | `#0E1012` | 语义状态实色/容器与配对前景 |
| `neutralStatusContainer` | `#F5F6F7` | `#16181B` | 语义状态实色/容器与配对前景 |
| `onNeutralStatusContainer` | `#5A6068` | `#A8AFB8` | 语义状态实色/容器与配对前景 |

`TanukiStatusTone` 的五类语义不变；Info 的点用装饰强调色，标签用可读的容器文字色。状态含义同时由文字表达。卡片以设备密度换算的 1px 灰描边分层，内容卡阴影为 0。状态栏/系统导航栏与 canvas 同色，浅色深字、深色浅字；导航栏深色图标按平台能力从 API 27 起通过专用资源启用，API 26 不支持此能力且未设备验证。底部导航选中标签用可读天蓝、图标用图标用途色。

### 2.3 全仓硬编码收敛清单

扫描涵盖全仓已跟踪 Kotlin/XML/HTML/CSS/JS/SVG 色值以及 `Color.White/Black` 等命名常量；构建缓存不属于产品源码。

| 文件/范围（相对 app/src） | 本次处理 |
|---|---|
| `main/kotlin/com/tanuki/ui/design/TanukiPalette.kt` | 统一浅深调色板、装饰/文字/白字底分工、用途注释和审计 |
| `main/kotlin/com/tanuki/ui/design/TanukiDesignTheme.kt` | Material 与原生 View 同源，消除默认 tint，危险文字取可读语义色 |
| `main/kotlin/com/tanuki/ui/design/TanukiComponents.kt`、`TanukiMaterialButtons.kt` | 胶囊与 Material 主次按钮统一取令牌 |
| `main/kotlin/com/tanuki/ui/design/TanukiGlass.kt`、`TanukiTokenSpec.kt` | 去固定白高光；灰描边与无阴影内容卡 |
| `main/kotlin/com/tanuki/ui/GlassNavigationBar.kt`、`OperationProgress.kt`、`DshComposerBar.kt` | 导航、进度和发送键按用途读取强调色 |
| `main/kotlin/com/tanuki/ui/DshMarkdown.kt` | 删除固定 #4C8DFF，所有生产链接传递当前主题文字色 |
| `main/kotlin/com/tanuki/ui/WorkspacePdfPreview.kt`、`WorkspaceDocxPreview.kt`、`WorkspaceVideoPlayer.kt` | 加载/错误/边框/留白改令牌；文档纸张见保留边界 |
| `main/kotlin/com/tanuki/MainActivity.kt`、`main/res/values{,-v27,-v29,-night,-night-v27,-night-v29}/themes.xml`、`main/res/values{,-night}/colors.xml` | 系统栏、首帧窗口同背景；图标色集中资源 |
| `main/res/drawable/ic_tanuki.xml` | 原路径不变，原绿/棕/米色改白灰与天蓝叶片 |
| `main/kotlin/com/tanuki/ui/{WorkspaceStorageControls,SetupGuide,WorkspaceFileViewer,OnboardingScreen,DshPlanCard,AgentBrowserPage,TanukiUi,DshNativePage,ApplicationUpdateCard,RemoteHostSettings,SetupScreens,RuntimeComponents,DshApprovalPanel,DshVersionManagerDialog,SourceSpeedControls,DshGoalDock,DshQuestionCard,TanukiAccessibility}.kt` | Material 按钮导入统一颜色入口；普通提示/装饰底回归灰白 |
| `test/kotlin/com/tanuki/{DshMarkdownTest,TanukiPageVisualsTest}.kt`、`test/kotlin/com/tanuki/ui/design/{TanukiDesignTokensTest,TanukiPlatformThemeTest}.kt` | 用途回归、明暗链接、方案取值与平台资源断言 |

保留项及原因：

- `storage/docx/{DocxXml,DocxModel,DocxStyles,DocxDocumentReader}.kt` 和 `storage/XlsxWorkbook.kt`：文档格式命名色、索引色及默认原文色，不是应用调色板；改变会破坏文件忠实显示。
- PDF 渲染位图的白纸底与 DOCX 纸张：属于文档内容，保持原文前景/填充；纸张占位文案收敛到 Light 令牌，应用工具栏、卡片、加载/失败外壳跟随主题。深色没有白色应用卡片或纯黑视频留白，不宣称将原文纸张自动改色。
- `ui/BrowserIcons.kt` 的黑色向量笔画：绘制遮罩，实际 Icon tint 由主题决定；透明常量仅代表透明。
- JVM `TanukiContrastTest`、`DocxDocumentReaderTest`、`XlsxWorkbookParserTest`、颜色数学测试的色值：算法/格式输入与预期。
- Android `FixedBottomControlsFixture`、`SetupPresentationTest`、`BrowserLibraryTest`、`BrowserWorkspacePresentationTest`、`AgentBrowserControlTest` 的彩色页与像素断言：合成渲染/截图探针，不是用户界面配色，不修改测试辨识色。

## 3. 字体阶梯

字体族只有两种：`System` 对应系统无衬线字体，`Monospace` 对应系统等宽字体。`TanukiTypeScale` 中只有 `Code` 角色使用 `Monospace`；`Title / Body / Caption` 全部使用系统字体。没有新增 `res/font` 资源，也没有打包中文字体。

| 角色 | 档位 | 字号/行高 | 字重 | 字距 | 字体 |
|---|---|---:|---:|---:|---|
| Title | Large | 28/36sp | 600 | -0.020em | 系统 |
| Title | Medium | 22/30sp | 600 | -0.015em | 系统 |
| Title | Small | 17/24sp | 600 | -0.010em | 系统 |
| Body | Large | 17/26sp | 400 | 0 | 系统 |
| Body | Medium | 15/23sp | 400 | 0 | 系统 |
| Body | Small | 13/20sp | 400 | 0 | 系统 |
| Caption | Large | 13/18sp | 500 | 0 | 系统 |
| Caption | Medium | 12/17sp | 500 | 0 | 系统 |
| Caption | Small | 11/16sp | 500 | 0 | 系统 |
| Code | Large | 14/22sp | 400 | 0 | 等宽 |
| Code | Medium | 12/20sp | 400 | 0 | 等宽 |
| Code | Small | 11/18sp | 400 | 0 | 等宽 |

`TanukiCodeText` 用于命令/代码，`TanukiPathText` 用于文件路径。其他组件不得使用 `FontFamily.Monospace`。

## 4. 形状与间距

间距以 **4dp** 为基准，命名令牌为 `none=0, xs=4, sm=8, md=12, lg=16, xl=20, xxl=24, xxxl=32, huge=40, giant=48`。所有可点击控件至少 **48dp** 触控区；列表行建议至少 56dp。

| 形状令牌 | 值 | 用途 |
|---|---:|---|
| `small` | 8dp | 小标签、缩略图 |
| `control` | 12dp | 输入框、普通控件 |
| `card` | 20dp | 内容卡 |
| `sheet` | 28dp（仅顶部） | 底部弹层 |
| `chrome` | 28dp | 顶栏/底栏/浮层 |
| `pill` | 50% | 胶囊按钮、玻璃胶囊、状态 chip |

Material3 通用 `Shapes` 映射：`extraSmall=8dp`、`small=12dp`、`medium=20dp`、`large/extraLarge=28dp`（全圆角）。`sheet` 是只有上圆角的专用形状，只通过 `TanukiSheetShape` 给底部弹层使用，不放进通用阶梯，避免普通卡片出现方下角。

## 5. 海拔与玻璃四层

| 层 | z | 阴影 | 填充不透明度 | 模糊 | 典型用途 |
|---|---:|---:|---:|---|---|
| chrome | 4 | 14dp / 0.14 | 0.86 | 是，16dp | 浮在内容上的顶栏、底栏、悬浮控制 |
| sheet | 3 | 18dp / 0.18 | 1.00 | 否 | 模态底部弹层，密集内容 |
| pill | 2 | 6dp / 0.10 | 0.78 | 是，8dp | 玻璃胶囊、短标签、单个高频操作 |
| card | 1 | 0dp / 0.00 | 1.00 | 否 | 内容卡、分组块 |

只有 **chrome 与 pill** 开启模糊。sheet 和 card 不模糊，优先保证正文与表单在复杂背景上的可读性。

模糊必须由调用方提供已录制的背板。没有背板时，`TanukiGlassSurface` 只绘制半透明底色并保留细边框，不调用模糊；组件不会把“半透明”冒充成“已模糊”。

## 6. 动效令牌

动效 A 批（基线 `3c63c9b`）已扩展统一工厂和可观察 reduced-motion。`TanukiDesign.motion` 返回 `TanukiMotionSpecs`；`instant` 与 `layout` 是新增入口，兼容原有 `sheetDismiss` 及历史枚举列表，不重复统计语义别名。

| 类型 | 令牌 | 时长 | 用途 |
|---|---|---:|---|
| 即时 | `instant` | 0ms | 恢复与移除动画 |
| 反馈 | `pressIn` | 100ms | 按下 |
| 反馈 | `pressOut` | 140ms | 抬起/取消 |
| 反馈 | `toggle` | 160ms | 开关、选中态 |
| 结构 | `layout` / `sheetDismiss` | 240ms | 局部布局 / 弹层收起 |
| 结构 | `sheetOpen` | 280ms | 弹层展开 |
| 结构 | `expand` | 300ms | 折叠展开 |
| 结构 | `page` | 320ms | 页面/大块结构切换 |
| 结构 | `pageMax` | 340ms | 结构动画上限 |
| 环境 | `ambientPulse` | 2400ms | 状态点呼吸 |
| 环境 | `ambientShimmer` | 2400ms | 骨架/环境微动 |

约束：反馈 ≤160ms；结构 240–340ms；环境约 2.4s。`TanukiMotion.durationMs(token, reducedMotion=true)` 恒为 **0**；状态点和流式光标在 reduced-motion 下保持静态常亮。

### 6.1 A 批语义工厂与共享设置

曲线为 `enter(0.2,0,0,1)`、`standard(0.4,0,0.2,1)`、`exit(0.4,0,1,1)`、linear；手势释放 settle 为 `spring(dampingRatio=1, stiffness=500)`，移除动画时 snap。`TanukiMotionSpecs` 提供 toggle、press、icon、state、expand、list、count、copy、progress，对应审计的 S-*；reduced-motion 下有限工厂均为 0ms、列表 spec 为 null。expand/list/progress 工厂已提供，不据此宣称 B/C 批的展开、列表与进度迁移已完成。

`TanukiMotionSettings` 进程内单例监听 `Settings.Global.ANIMATOR_DURATION_SCALE`，Activity 恢复及原生浮窗显示时补读。主题和 `BrowserOverlayWindow` 共用同一 StateFlow，不修改用户系统设置。开启移除动画时按压保持 scale=1，状态点/光标关闭循环，复制反馈文字仍保留；普通非零系统缩放交给平台，不重复乘倍率。

五类反馈与五页接入：

| 页面 | A 批接入 | 行为边界 |
|---|---|---|
| 原生聊天 | `DshComposerBar` 发送/停止图标 alpha + 0.92→1，无位移/尺寸补间；`DshToolCard` 固定宽度复制确认 | 发送/停止、工具展开和剪贴板回调即时；确认默认1500ms，重复点击重置并服从无障碍建议超时 |
| 会话管理 | 固定内容区域的 S-state；公共按钮/卡片按下0.98、松手1，保留ripple | 去掉列表二次入场；设置开关只改spec，不更换状态宿主；搜索/筛选/权限回调不变 |
| 文件页 | 项数固定最小宽度、新数字只淡入；公共胶囊按压 | 首次计数直接显示，不改变目录/排序/多选/传输 |
| 浏览器 | 控制图标S-icon；原生浮窗按钮共用S-press与系统设置 | AI权限与点击/取消即时，保留原生ripple；网页、拖动和窗口几何不参与动画 |
| 远程设置 | 我的连接数量固定最小宽度淡入 | 连接、发现、指纹确认及错误状态不等待动效 |

新封装位于 `ui/TanukiMotionFeedback.kt`：`TanukiIconSwap`、`TanukiStateCrossfade`、`TanukiAnimatedCount`、`TanukiCopyButton`。状态区不做高度动画，图标关闭 SizeTransform；系统 sheet 不再包动画。Host 回归见 `TanukiMotionSpecsTest`；Android实际设置通知、五页视觉效果、浮窗触摸、TalkBack与帧率仍待独立QA。

### 6.2 B 批页面转场所有权

`TanukiPageTransitionHost` 位于 `ui/TanukiPageTransitions.kt`，统一使用 A 批时长和曲线。单槽宿主立即提交目标页面，只组合当前内容；旧页不保留返回处理器、不继续发请求、不复制 WebView。初始挂载、恢复和宽度断点变化不当作导航入场；快速切页取消上一段协程，重定向到最新目标。

| 跳转 | 语义与参数 | 所有者 |
|---|---|---|
| 工作台 / DSH / 文件 / 浏览器互切（含 rail、通知与程序导航） | alpha 160ms standard；无位移、无缩放 | TanukiUi 页面宿主 |
| 主目的地 → 设置、设置 → 分区（含远程、存储与组件入口） | alpha + 16dp→0，上移淡入 320ms enter | 同一页面宿主 |
| 设置分区 → 设置、设置 → 原目的地 | alpha + -16dp→0，反向 240ms exit | 同一页面宿主；沿用现有返回状态 |
| 文件列表 → 内部/外部查看器 / 返回 | 稳定文档 key，前进 320ms / 返回 240ms，同一文档的加载/滚动不重播 | 文件渲染段的统一宿主；业务状态仍在宿主外 |
| 浏览器网页 → 原生二级页、设置 → 子项 | 前进 320ms；返回 240ms；网页目标只揭示、不位移 | 浏览器内容段的统一宿主，沿用既有 back() 关系 |
| 窄屏列表 → 详情 / 返回 | 前进 320ms / 返回 240ms，同上；宽屏双栏即时布局 | TanukiTwoPane（当前为组件储备，无业务调用） |
| 打开 / 关闭独立会话管理 Activity | 保留系统过渡，不加 Compose 页面入场 | Android 系统；列表内部 S-state 沿用 A 批 |

层级位移按审计约定在 RTL 中镜像；所有同级位移恒为零。返回页采用与进入相反的起点，单槽不保留退出页。`reduced-motion` 直接显示目标态（alpha=1、位移=0、时长=0），动态切换设置也不重建页面内容。

官方 DSH 与浏览器可能持有活 WebView：页面宿主只让原生背景遮罩从不透明到透明，不给网页祖先套 `graphicsLayer` 或移动/缩放，不截图或复制网页。返回网页也只揭示同一实例。嵌套宿主在父宿主正在转场时立即显示目标，保证一次导航只播一层页面动效。文件页旧 `tanukiEnter` 已移除；兼容入口在统一宿主内直接返回原 Modifier，不创建第二段入场。文件查看器保持原来的提前返回和列表弹窗条件，浏览器菜单与授权弹窗不进入页面动效。详情见 [B 批交接](handoffs/20261111-1900-motion-batch-b.md)。

回归 `TanukiPageTransitionsTest` 使用 Compose 实际动画 spec 的时间采样验证主目的地矩阵、层级曲线/方向、RTL、进入/返回方向互逆、0ns 即终态及 WebView 零位移；不把 JVM 策略验证称为 Android 视觉、WebView/Surface 或帧率验收。

### 6.3 C 批弹层与抽屉所有权

`TanukiMotionSpecs.sheet(opening)` 与 `scrim()` 复用 A 批令牌。自绘 `TanukiBottomSheet` 的遮罩与面板共享一个父 Transition，退出结束后才通知 `onHidden`；面板不再叠加 alpha 或高度补间。拖动仍直接跟手，释放回位用 settle，关闭时停止未完成的回位。

| 表面 | 打开 | 关闭 / 回位 | 遮罩 / reduced-motion |
|---|---|---|---|
| 自绘 TanukiBottomSheet | 全高位移 280ms enter | 全高位移 240ms exit；拖拽回位 settle | 遮罩唯一 alpha：160ms standard；有限动画 0ms，settle snap |
| TanukiAdaptiveShell 窄屏抽屉 | 按钮 280ms enter | 导航/返回/遮罩点击 240ms exit；拖拽释放按目标打开 280ms / 关闭 240ms | 遮罩唯一 alpha：160ms standard，与位移同时启停，拖拽期间直接跟手；reduced 立即 snap，打开途中返回也先关抽屉 |
| 系统 ModalBottomSheet | Material 3 窗口默认 | 系统 hide 完成后卸载展示内容 | 平台独占位移/遮罩；不伪造固定毫秒，移除动画关闭立即卸载，打开遵循平台缩放 |
| AlertDialog / Dialog / DropdownMenu | 系统默认 | 系统默认 | 不包自绘 scale/fade，保留窗口焦点、返回与系统无障碍设置 |

`TanukiModalSheet` 位于 `ui/TanukiMotionFeedback.kt`，为审批、权限、传输/全局进度、会话详情/主机选择和浏览器菜单提供常驻宿主。调用方传当前模型，关闭时传 null；`TanukiOverlayState` 保留最后 UI 快照到 hide 结束，快速重开取消旧 hide 并重定向。关闭代次仅在打开/关闭边界递增；`finally` 即使遇到系统 hide 取消也会释放同一关闭代次的窗口，不吞掉取消，不清理快速重开或再次关闭的新代次。系统关闭回调也核对渲染代次，避免旧任务清空新模型。

退出时关闭系统 sheet 手势、返回和遮罩点击，触摸/键盘/无障碍屏障覆盖整个 sheet Surface（包括 Material 自带的 clickable 拖拽柄），不能只覆盖内容 Column。打开期间正常交互，业务许可与状态更新不等待动画。错误详情仍使用快照策略和自绘 `onHidden`。未接线的 `RemoteHostSwitchSheet` 保持平台默认；TanukiDrawer 为本批新增的单一位移宿主，复用 Material 表面；当前源码没有 TanukiDialog 定义，不新增同义系统窗口。

Host 回归 `TanukiOverlayMotionTest` 采样实际 spec 时长/曲线、0ns 终态、退出快照保留/清理与快速重开，另检查自绘遮罩单次 alpha 和系统窗口不叠外层动画；`TanukiOverlayExitTest` 实际取消关闭协程，验证同代次释放与新代次隔离。Android 回归 `TanukiModalSheetInteractionTest` 使用独立合成 Activity、正常时长的可控帧时钟和系统触摸注入，检查退出中按住系统拖拽柄仍卸载窗口、下层物理点击恢复，以及快速重开后能再次关闭。Android 源码编译不等于设备执行，实际执行及未覆盖的系统设置、返回焦点、RTL、TalkBack、IME、帧率与深浅色边界以 [C 批交接](handoffs/20261111-1900-motion-batch-c.md) 为准。

## 7. 基础组件

除三态外，组件位于 `com.tanuki.ui.design`，当前为模块内 `internal`，由同一 App 模块的页面直接使用。

空/错/载三态不重复实现：复用 `com.tanuki.ui.TanukiStateView(kind, subject, detail, actionLabel, onAction)`。它已在 main 提供中文文案、礼貌播报和 48dp 操作按钮；页面用 `TanukiDesignTheme` 包裹后，其 `MaterialTheme` 颜色、字体和形状会自动采用本令牌。

| 组件 | 关键参数 | 行为与边界 |
|---|---|---|
| `TanukiPillButton` | `text, onClick, tone, enabled, loading, leading` | 五种语气 `Filled/Tonal/Outline/Ghost/Danger`；至少 48dp；加载时禁用点击并保留宽度；leading 图标继承当前 tone/enabled 的内容色 |
| `TanukiStatusDot` | `tone, pulse, diameter, decorative` | 8dp 状态点；默认静态；可选的 2.4s 呼吸；装饰模式不产生重复无障碍节点 |
| `TanukiStatusChip` | `tone, label, onClick, enabled, pulse, contentDescription` | 容器色 + 容器正文；默认 28dp；可点击时扩展到 48dp；可选 `contentDescription` 覆盖朗读文案（例如“权限：可写，点击管理”） |
| `TanukiGlassSurface` | `layer, backdrop, shape` | 四层通用玻璃/海拔表面；无背板时退化为半透明底色 |
| `TanukiGlassPill` | `backdrop, contentPadding` | 玻璃胶囊，至少 48dp，适合短标签/单个操作 |
| `TanukiGlassChrome` | `backdrop, contentPadding` | 顶栏/底栏 chrome，至少 56dp |
| `TanukiCardSurface` | `onClick, content` | 不模糊内容卡；可点击时走 `TanukiPressable` |
| `TanukiPressable` | `onClick, enabled, shape` | 按下0.98/100ms、松手1/140ms并保留ripple；reduced保持1；无回调时不拦截触摸 |
| `TanukiBottomSheet` | `visible, onDismissRequest, title, subtitle, heightFraction, content` | 遮罩/返回键/下拉关闭；96dp 阈值；结构动画走令牌；`heightFraction` 为空时按内容包裹，传 0.9 时固定占屏并在内部滚动；内容根部提供 `textPrimary` 的 `LocalContentColor`，深色下正文不会继承页面默认黑色 |
| `TanukiStreamingCursor` | `visible` | 2×18dp 竖条；2.4s 明暗周期；reduced-motion 常亮 |
| `TanukiCodeText` | `text, maxLines` | 唯一允许等宽的命令/代码文本 |
| `TanukiPathText` | `path, maxLines` | 等宽路径文本，默认单行省略 |

主题入口：`TanukiDesignTheme(dark, reducedMotion) { ... }`。组件通过 `TanukiDesign.tokens` 读取颜色、排版和 reduced-motion 状态；未包裹主题时会明确报错，不会静默使用错误主题。

## 8. 页面接入结果

本轮已接入（android_agent-64，基线 `62e4c30b`）：

| 页面/文件 | 已接入内容 | 使用的令牌/组件 | 保留边界 |
|---|---|---|---|
| `TanukiDesign.kt` 根主题 | `TanukiTheme` 直接委托 `TanukiDesignTheme`，只保留一套颜色来源；`tanukiColorScheme(dark)` 也读同一调色板，供浏览器悬浮层等原生 View 使用 | `TanukiDesignTheme`、`TanukiColorTokens`、`TanukiShapeTokens` | 深浅色跟随系统；不嵌套两套主题；`TanukiSpacing` 仅保留为兼容入口 |
| `WorkspaceFilesPage.kt` | 位置选择器/行/权限 chip/传输胶囊/三态/弹层按钮 | `TanukiPillButton`、`TanukiStatusChip`、`TanukiStatusDot`、`TanukiGlassPill`、`TanukiPathText`、`TanukiStateView`、`TanukiShapeTokens` | 文件行长按多选、菜单项文案、传输取消/无障碍描述不变；未授权页主操作仍调用原回调 |
| `DshSessionManagerPage.kt` | 列表行、分组头、筛选条、选择条、批量进度、消息搜索、横幅、三态、新建/批量弹层、详情内容 | `TanukiPressable`、`TanukiStatusChip`、`TanukiPillButton`、`TanukiBottomSheet`、`TanukiStateView`、`TanukiShapeTokens` | 筛选/搜索/分页/归档/权限协议不变；详情仍用系统 `ModalBottomSheet`（保留拖拽/返回键/输入法）；从详情发起的重命名/归档/完全权限必须用窗口级 `AlertDialog`，见下 |
| `TanukiUi.kt` 设置段 | 分区标题与卡片、动作行、只读行、开关、折叠面板、分类入口 | `TanukiShapeTokens`、`TanukiSpace`、`TanukiExpandVisibility`、`tanukiDisabledContentColor`、`TanukiMotion` | 回调、禁用条件和文案不变；概览/组件页共用的 `Caption`/`StatusPill` 未改 |
| `TanukiMotion.kt` | 入场、折叠面板、状态切换、按压反馈统一读令牌 | `tanukiMotionMs`、`tanukiEnter`、`TanukiExpandVisibility`、`TanukiPressable` | 系统“移除动画”时时长为 0；循环动画保持静态 |

### 8.1 令牌映射表

| 界面元素 | 旧实现 | 新令牌/组件 | 取值 |
|---|---|---|---|
| 根画布/表面 | `TanukiDesign.kt` 内的独立十六进制色表 | `TanukiColorTokens.Light/Dark` | `canvas/surface/surfaceRaised/surfaceMuted` |
| 主操作按钮 | `Button`、`ExtendedFloatingActionButton`、`OutlinedButton` | `TanukiPillButton` | `Filled/Tonal/Outline/Ghost/Danger`；≥48dp |
| 次操作/关闭 | `TextButton` | `TanukiPillButton` | `Ghost`（表单弹层底部） |
| 筛选/选中 chip | `FilterChip`、自定义 `Pill` | `TanukiPillButton`（选中 `Filled`，未选中 `Outline`）、`TanukiStatusChip` | 胶囊 50%；可点击 ≥48dp |
| 状态/权限标签 | 自定义 `Pill`、圆点 + `labelSmall` | `TanukiStatusChip` + `TanukiStatusDot` | 五种 `TanukiStatusTone`；28dp / 48dp |
| 文件行 | `RoundedCornerShape(18/24)`，无最小行高 | `TanukiShapeTokens.control/card` + `TanukiFileVisuals.ROW_MIN_HEIGHT_DP` | 12/20dp；≥56dp |
| 会话行 | `MaterialTheme.shapes.large` + `clickable` | `TanukiPressable` + `TanukiShapeTokens.card` | 20dp；≥56dp；按压 0.98 |
| 分区/内容卡 | `shapes.extraLarge` + `surfaceContainerLow` | `TanukiShapeTokens.card` + `surfaceContainer` | 20dp；抬升表面 |
| 卡片圆角（通用 large） | `large = sheet`（只有上圆角） | `large = chrome`（28dp 全圆角） | 修正普通卡片方下角 |
| 三态（空/错/载） | 页面各自拼 `Column`/`Button` | `TanukiStateView` | 复用现有实现；新增可选 `title` 与次操作 |
| 传输胶囊 | `Surface` + `RoundedCornerShape(50)` | `TanukiGlassPill` | 玻璃 pill 层；无背板时只画半透明表面 |
| 路径文本 | `bodySmall` 普通字体 | `TanukiPathText` | 等宽 11/18；单行省略 |
| 字号 | 散落 `MaterialTheme.typography.*` | `TanukiTypeScale` 经 `TanukiDesignTheme` 映射 | Title/Body/Caption/Code 各三档 |
| 动效·入场 | 420ms 硬编码 | `tanukiEnter` + `TanukiMotion.page` | 320ms；移除动画时 0 |
| 动效·面板 | `AnimatedVisibility` 默认弹簧 | `TanukiExpandVisibility` + `TanukiMotion.expand` | 300ms；移除动画时 0 |
| 动效·状态切换 | 无/默认 | `Crossfade` + `TanukiMotion.toggle` | 160ms；移除动画时 0 |
| 动效·按压 | Material 默认 | `TanukiPressable` + `TanukiMotion.pressIn` | 100ms |
| 详情内二级确认 | 本轮初版用页面内 `TanukiBottomSheet` | 窗口级 `AlertDialog`（重命名/归档/完全权限） | 详情是 Material3 `ModalBottomSheet` 的独立窗口，页面内 `Box` 会被遮住（PR #70 评审 P1） |
| 弹层正文色 | 继承页面默认黑色 | `TanukiBottomSheet` 提供 `LocalContentColor = textPrimary` | 深色 sheet 上正文可读；对比度按 textPrimary/textSecondary × sheet 回归（PR #70 评审 P2） |

### 8.2 截图自查清单（静态核对，设备未验证）

下表只记录源码/令牌层面的核对结论；“设备未验证”表示没有在模拟器或真机上渲染确认像素。

| 页面 | 深色 | 浅色 | 1.3x/2x 大字体 | 360dp 窄屏 | 说明 |
|---|---|---|---|---|---|
| 文件页 | 静态通过，设备未验证 | 静态通过，设备未验证 | 静态通过，设备未验证 | 静态通过，设备未验证 | 权限 chip 宽度上限 76dp，360dp 下头部按 48×4 图标 + 76dp chip 计算仍可容纳；320dp 可能只剩图标 |
| 会话管理页 | 静态通过，设备未验证 | 静态通过，设备未验证 | 静态通过，设备未验证 | 静态通过，设备未验证 | 筛选条与行内标签横向滚动；列表层弹层动作 1.3x 起改整行按钮；详情内二级确认使用窗口级 Dialog |
| 设置页 | 静态通过，设备未验证 | 静态通过，设备未验证 | 静态通过，设备未验证 | 静态通过，设备未验证 | 行高 ≥56dp，说明文字换行；只读行的长值在 2x 字体下会换行而非截断；开关触控区由 Material 组件保证 |
| 根主题/原生悬浮层 | 静态通过，设备未验证 | 静态通过，设备未验证 | 不适用 | 不适用 | `tanukiColorScheme(dark)` 与 Compose 主题同源；对比度由 JVM 审计 |

核对依据：`TanukiPageVisualsTest` 的对比度/取值断言、`TanukiContrastTest`、以及 `docs/DESIGN-TOKENS.md` 第 2、3、6 节的令牌表。未验证项不能写成设备通过。

### 8.3 尚未接入

| 页面/文件 | 原因 | 下一步 |
|---|---|---|
| `DshNativePage.kt` / `DshNativeController.kt` | 归 android_agent-62；本轮不改聊天页 | 由该任务按本文件第 7 节组件表接入 |
| 目标/待办停靠条 | 归 android_agent-63 | 同上 |
| `DshComposerBar.kt`、`DshApprovalPanel.kt`、`DshToolCard.kt` | 不在本轮三个页面范围 | 后续按组件表分批接入 |
| `BrowserChrome.kt` / `AgentBrowserPage.kt` | 不在本轮范围；仅通过根主题与 `tanukiColorScheme` 继承新调色板 | 后续单独接入玻璃层与菜单弹层 |
| `SetupScreens.kt` / `RuntimeComponents.kt` | 不在本轮范围 | 进度语义保持不变，只替换状态组件 |
| `DshVersionManagerDialog.kt` | 不在本轮范围 | 版本列表弹层迁移到 `TanukiBottomSheet` |

接入约束（继续有效）：

- 保留现有业务状态、回调、协议和安全边界；视觉状态不能替代发送/审批/写入状态。
- 文件行的长按多选不能被按压容器吞掉；本轮仍用 `combinedClickable`。
- 不把 `com.tanuki.ui.TanukiStateView` 用作整页错误横幅；消息流内的失败行仍按行为规格显示。
- 玻璃层放在内容之上时必须提供真实背板；没有背板就用 card/sheet 的不透明表面。
- WebView 保持原层级和生命周期，不包进玻璃、缩放或动画容器。

## 9. 当前用途对比度校验

`TanukiContrastAudit` 每主题 45 项，共 90 项；正文、说明、链接、按钮及状态标签 ≥4.5:1，大字与信息图标 ≥3:1。装饰天蓝、禁用灰和细分隔线不参与可读文字断言；测试明确证明装饰天蓝配白字不合格，不会通过降低阈值掩盖问题。

白字主按钮 #0369A1 为 5.93:1。#6B7280 在灰底的不足通过 `descriptionOn` 回退解决。玻璃 chrome 以实际底色和 fillAlpha 压在 canvas 上审计；任意网页或原文图片的像素对比度不在该模型的保证范围。

原补丁在7cfe1446基线的主源码、Android 测试源码及 JVM 测试源码编译通过；该会话定向 JVM 共 58 项（设计令牌 23、平台主题 3、Material 主题 4、页面映射 12、Markdown 16），0 failures / errors / skipped；90 项用途审计全部通过。正文配对最低约 4.52:1，图标/大字最低约 5.29:1；主按钮 5.93:1、按下态 7.56:1、浅/深链接在卡片上分别 5.93/10.14:1。最终同命令重跑 BUILD SUCCESSFUL（5m52s），lint 为 0 errors / 103 warnings / 1 hint；此前 API27 资源属性位置错误已修复，失败原因保留于交接和测试报告。设备仍未验证。下节为旧接入工作的历史验证，不作为本轮通过依据。

## 10. 验证与未验证

本轮（android_agent-64，`62e4c30b` + 本 PR 改动）已执行：

```bash
./gradlew :app:compileModernDebugKotlin :app:compileModernDebugUnitTestKotlin -x buildNativeRuntime
./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime
./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.TanukiPageVisualsTest' \
  --tests 'com.tanuki.TanukiContrastTest' -x buildNativeRuntime
./gradlew :app:lintModernDebug -x buildNativeRuntime
```

结果：

- 编译通过（app 与 unit test）。
- 完整 JVM：**1059 tests / 109 个结果文件 / 0 failures / 0 errors / 0 skipped**。
- 定向 JVM：`TanukiPageVisualsTest` 12 tests、`TanukiContrastTest` 4 tests，全部通过。
- lint：`BUILD SUCCESSFUL`，0 errors / 103 warnings / 1 hint；改动文件没有新增告警（`ModifierParameter` 等告警来自既有的 `TanukiIcon` 等位置）。
- 期间 `NpmAdaptiveWorkerPoolTest.downshiftRetiresExcessWorkersAfterCurrentTaskAndKeepsQueueDraining` 出现过一次 `expected:<8> but was:<9>` 的时序失败；单测类重跑通过，未改该模块，记录为既有 flaky 用例。
- APK 使用 `-x buildNativeRuntime`，不含原生运行时，不能作为设备验收或发布包。

未执行 / 未验证：

- Android 仪器测试与模拟器/真机视觉验收（设备由独立 QA 独占）。
- 深色/浅色/大字体/窄屏的逐页截图对照只在静态清单层面核对，未在设备上确认像素。
- 真机手势、TalkBack、输入法遮挡和滚动性能未验证。
- 完整原生构建、发布包与合并。

## 11. 文件清单

| 文件 | 作用 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiColorMath.kt` | OKLCH→sRGB、色域映射、WCAG 亮度/对比度、alpha 合成 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiPalette.kt` | 共享明度阶梯、五组色相、浅/深角色令牌、对比度审计 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiTokenSpec.kt` | 间距、圆角、四档字体、动效、海拔四层纯值 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiDesignTheme.kt` | Compose 令牌入口、字体/形状映射、reduced-motion、原生 View 调色板 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiComponents.kt` | 胶囊按钮、状态点/chip、按压容器、流式光标、代码/路径文本 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiGlass.kt` | chrome/sheet/card/pill 表面、玻璃胶囊、内容卡 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiBottomSheet.kt` | 可拖拽关闭的底部弹层骨架（支持固定占屏比例） |
| `app/src/main/kotlin/com/tanuki/ui/TanukiDesign.kt` | 根主题接线、`TanukiSpacing` 兼容入口、停用色 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiMotion.kt` | 动效令牌读取、入场、折叠面板 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiPageVisuals.kt` | 文件页/会话页的纯视觉映射与格式化 |
| `app/src/test/kotlin/com/tanuki/ui/design/TanukiDesignTokensTest.kt` | JVM 对比度、转换、令牌取值回归 |
| `app/src/test/kotlin/com/tanuki/TanukiPageVisualsTest.kt` | 页面映射、令牌取值、对比度与格式化回归 |
