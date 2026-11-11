# 原生界面设计令牌与基础组件

本文件记录设计令牌、基础组件以及页面接入结果。令牌与组件由 android_agent-59 建立（PR #59）；android_agent-64 在 `origin/main=62e4c30b` 上把令牌接入根主题、文件页、会话管理页和设置页，并补齐可 JVM 回归的视觉映射。聊天页（`DshNativePage`/`DshNativeController`）与目标/待办停靠条不在本次接入范围，只通过根主题继承新调色板，未改其源码。

- 基线：任务起始 `origin/main=62e4c30b`（2026-11-11）。
- 代码位置：`app/src/main/kotlin/com/tanuki/ui/design/`；页面视觉映射在 `app/src/main/kotlin/com/tanuki/ui/TanukiPageVisuals.kt`。
- JVM 回归：`app/src/test/kotlin/com/tanuki/ui/design/TanukiDesignTokensTest.kt`、`app/src/test/kotlin/com/tanuki/TanukiPageVisualsTest.kt`。
- 相关文档：[原生界面实现边界](NATIVE-UI.md)、[原生界面行为规格](handoffs/20261110-1759-native-ui-parity-spec.md)、[代码规范](code-standards.md)。

## 1. 设计原则

1. **感知明度优先**：中性色和语义色共用一条 OKLCH 明度阶梯，换色相不改变版面的轻重关系。
2. **系统字体优先**：中文标题、正文、说明全部跟随系统字体，不打包字体资源；等宽字体只用于代码和路径。三态组件复用 main 已有实现，主题包裹后自动采用本令牌。
3. **触控优先**：所有可点击控件至少 48dp 触控区；视觉胶囊可以更矮，但热区不变。
4. **层级克制**：只有 chrome 和 pill 允许模糊；sheet 和 card 使用不透明表面和细边框，保证密集内容可读。
5. **动效可关闭**：所有时长都走令牌；系统移除动画时时长统一为 0，循环动画改为静态。
6. **纯函数可测**：颜色生成、对比度和数值范围不依赖 Android/Compose，JVM 可直接锁定；对比度公式复用 main 的 `TanukiContrast`，避免两份阈值漂移。

## 2. 颜色令牌

### 2.1 生成方式

颜色由 OKLCH 生成：`L` 是感知明度，`C` 是彩度，`H` 是色相角。请求彩度超出 sRGB 色域时，沿同一色相二分降低彩度，避免直接裁剪通道导致色相漂移。

五组色相：

| 色相组 | H | C | 用途 |
|---|---:|---:|---|
| Neutral | 110° | 0.008 | 画布、表面、正文、边框 |
| Success | 150° | 0.11 | 成功、完成、已连接 |
| Warning | 75° | 0.13 | 警告、降级、需注意 |
| Danger | 27° | 0.15 | 危险、失败、拒绝 |
| Info | 245° | 0.12 | 信息、运行中、提示 |

共享明度阶梯共 14 档（索引 0–13）：

```text
0.995, 0.975, 0.945, 0.905, 0.855, 0.775, 0.665,
0.555, 0.455, 0.365, 0.285, 0.215, 0.165, 0.115
```

浅色主题从浅端取画布、从深端取正文；深色主题反向取用。语义色与中性色在每个索引上使用同一个 L 值。

### 2.2 角色令牌

浅色/深色是两套角色映射，共用同一份色板。下表为 JVM 测试输出的实际 ARGB。

| 令牌 | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `canvas` | `#FFF7F7F1` | `#FF050503` | 页面画布 |
| `surface` | `#FFFEFEF8` | `#FF0E0F0B` | 基础表面 |
| `surfaceRaised` | `#FFFEFEF8` | `#FF1A1A16` | 抬升表面 |
| `surfaceSunken` | `#FFEDEDE7` | `#FF050503` | 凹陷表面 |
| `surfaceMuted` | `#FFEDEDE7` | `#FF1A1A16` | 次级块、分组底 |
| `sheet` | `#FFFEFEF8` | `#FF1A1A16` | 底部弹层 |
| `chromeBase` | `#FFFEFEF8` | `#FF0E0F0B` | 玻璃 chrome 不透明底 |
| `scrim` | `#66000000` | `#99000000` | 弹层遮罩（带 alpha） |
| `textPrimary` | `#FF0E0F0B` | `#FFFEFEF8` | 正文主色 |
| `textSecondary` | `#FF3E3F3A` | `#FFB6B6B1` | 次级正文 |
| `textTertiary` | `#FF575752` | `#FF94948F` | 三级正文、路径 |
| `textDisabled` | `#FF73736E` | `#FF73736E` | 停用文字（大号/图标） |
| `outline` | `#FFB6B6B1` | `#FF3E3F3A` | 弱边框、拖动把手 |
| `outlineStrong` | `#FF73736E` | `#FF73736E` | 强边框、图标 |
| `hairline` | `#FFE0E0DA` | `#FF2A2A26` | 1px 分隔线 |
| `primary` | `#FF1A1A16` | `#FFEDEDE7` | 主操作 |
| `onPrimary` | `#FFFEFEF8` | `#FF0E0F0B` | 主操作正文 |
| `primaryContainer` | `#FFEDEDE7` | `#FF3E3F3A` | 主操作容器 |
| `onPrimaryContainer` | `#FF0E0F0B` | `#FFF7F7F1` | 主容器正文 |
| `success` | `#FF1B6734` | `#FF5EA76F` | 成功实心色 |
| `onSuccess` | `#FFFEFEF8` | `#FF050503` | 成功实心色正文 |
| `successContainer` | `#FFC1FFCC` | `#FF003413` | 成功容器 |
| `onSuccessContainer` | `#FF0E0F0B` | `#FFE5FFE9` | 成功容器正文 |
| `warning` | `#FF754D00` | `#FFC28621` | 警告实心色 |
| `onWarning` | `#FFFEFEF8` | `#FF050503` | 警告实心色正文 |
| `warningContainer` | `#FFFFE9CC` | `#FF3B2500` | 警告容器 |
| `onWarningContainer` | `#FF0E0F0B` | `#FFFFF5E8` | 警告容器正文 |
| `danger` | `#FF992724` | `#FFE16A60` | 危险实心色 |
| `onDanger` | `#FFFEFEF8` | `#FF050503` | 危险实心色正文 |
| `dangerContainer` | `#FFFFE6E3` | `#FF560004` | 危险容器 |
| `onDangerContainer` | `#FF0E0F0B` | `#FFFFF4F2` | 危险容器正文 |
| `info` | `#FF005B91` | `#FF4D9AD8` | 信息实心色 |
| `onInfo` | `#FFFEFEF8` | `#FF050503` | 信息实心色正文 |
| `infoContainer` | `#FFDEEFFF` | `#FF002C4B` | 信息容器 |
| `onInfoContainer` | `#FF0E0F0B` | `#FFF0F8FF` | 信息容器正文 |
| `neutralStatus` | `#FF575752` | `#FF94948F` | 中性状态点 |
| `onNeutralStatus` | `#FFFEFEF8` | `#FF050503` | 中性实心色正文 |
| `neutralStatusContainer` | `#FFEDEDE7` | `#FF2A2A26` | 中性状态容器 |
| `onNeutralStatusContainer` | `#FF0E0F0B` | `#FFF7F7F1` | 中性容器正文 |

`TanukiStatusTone` 固定为 `Neutral / Success / Warning / Danger / Info`。组件只接收语义，不接收具体色值；`statusColors(tone)` 返回 `dot / container / content` 三枚颜色。

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
| card | 1 | 2dp / 0.06 | 1.00 | 否 | 内容卡、分组块 |

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

## 9. 对比度校验

审计代码在 `TanukiContrastAudit`，每个主题 28 项，共 56 项；正文/容器正文最低 4.5:1，停用文字/图标/状态点最低 3.0:1。测试结果：**19 tests / 0 failures / 0 errors / 0 skipped**。

关键实测值：

| 检查项 | 浅色 | 深色 | 最低要求 |
|---|---:|---:|---:|
| 正文/画布 | 17.89 | 20.15 | 4.5 |
| 正文/表面 | 19.00 | 19.00 | 4.5 |
| 次级正文/画布 | 9.88 | 10.02 | 4.5 |
| 三级正文/画布 | 6.76 | 6.69 | 4.5 |
| 三级正文/凹陷表面 | 6.18 | 5.73 | 4.5 |
| 停用正文/画布 | 4.43 | 4.28 | 3.0（大号） |
| 玻璃 chrome 正文/画布 | 18.84 | 19.13 | 4.5 |
| 主操作正文/主色 | 17.24 | 16.37 | 4.5 |
| 主容器正文/主容器 | 16.37 | 9.88 | 4.5 |
| 成功正文/成功色 | 6.83 | 7.03 | 4.5 |
| 警告正文/警告色 | 7.36 | 6.53 | 4.5 |
| 危险正文/危险色 | 7.77 | 6.25 | 4.5 |
| 信息正文/信息色 | 7.14 | 6.73 | 4.5 |
| 成功容器正文/容器 | 16.89 | 13.20 | 4.5 |
| 危险容器正文/容器 | 16.19 | 13.94 | 4.5 |

玻璃 chrome 的审计方式是：`chromeBase` 以 0.86 不透明度压在画布上后再计算正文。实际页面上的玻璃会随底层内容变化；关键正文应使用 sheet/card，或确保背板有足够不透明度，不能仅凭该审计值宣称任意背景都达标。

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
