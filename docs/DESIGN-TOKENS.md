# 原生界面设计令牌与基础组件

本文件记录 android_agent-59 新增的设计令牌与基础组件。范围只包括新增文件：不修改业务页面、不替换现有 `TanukiTheme`、不调整构建脚本、不引入新的第三方 UI 依赖。空/错/载三态和 WCAG 对比度复用 main 已有的 `com.tanuki.ui.TanukiStateView`、`com.tanuki.ui.TanukiContrast`，不再新建同义实现。页面接入步骤见第 8 节，由后续改动分批完成。

- 基线：任务起始 `origin/main=1122d3f`；并发合并后已 rebase 到 `origin/main=7757b38`（2026-11-10）。
- 代码位置：`app/src/main/kotlin/com/tanuki/ui/design/`。
- JVM 回归：`app/src/test/kotlin/com/tanuki/ui/design/TanukiDesignTokensTest.kt`。
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

| 类型 | 令牌 | 时长 | 用途 |
|---|---|---:|---|
| 反馈 | `pressIn` | 100ms | 按下 |
| 反馈 | `pressOut` | 140ms | 抬起/取消 |
| 反馈 | `toggle` | 160ms | 开关、选中态 |
| 结构 | `sheetDismiss` | 240ms | 弹层收起 |
| 结构 | `sheetOpen` | 280ms | 弹层展开 |
| 结构 | `expand` | 300ms | 折叠展开 |
| 结构 | `page` | 320ms | 页面/大块结构切换 |
| 结构 | `pageMax` | 340ms | 结构动画上限 |
| 环境 | `ambientPulse` | 2400ms | 状态点呼吸 |
| 环境 | `ambientShimmer` | 2400ms | 骨架/环境微动 |

约束：反馈 ≤160ms；结构 240–340ms；环境约 2.4s。`TanukiMotion.durationMs(token, reducedMotion=true)` 恒为 **0**；状态点和流式光标在 reduced-motion 下保持静态常亮。

## 7. 基础组件

除三态外，组件位于 `com.tanuki.ui.design`，当前为模块内 `internal`，由同一 App 模块的页面直接使用。

空/错/载三态不重复实现：复用 `com.tanuki.ui.TanukiStateView(kind, subject, detail, actionLabel, onAction)`。它已在 main 提供中文文案、礼貌播报和 48dp 操作按钮；页面用 `TanukiDesignTheme` 包裹后，其 `MaterialTheme` 颜色、字体和形状会自动采用本令牌。

| 组件 | 关键参数 | 行为与边界 |
|---|---|---|
| `TanukiPillButton` | `text, onClick, tone, enabled, loading, leading` | 五种语气 `Filled/Tonal/Outline/Ghost/Danger`；至少 48dp；加载时禁用点击并保留宽度；leading 图标继承当前 tone/enabled 的内容色 |
| `TanukiStatusDot` | `tone, pulse, diameter, decorative` | 8dp 状态点；默认静态；可选的 2.4s 呼吸；装饰模式不产生重复无障碍节点 |
| `TanukiStatusChip` | `tone, label, onClick, enabled, pulse` | 容器色 + 容器正文；默认 28dp；可点击时扩展到 48dp |
| `TanukiGlassSurface` | `layer, backdrop, shape` | 四层通用玻璃/海拔表面；无背板时退化为半透明底色 |
| `TanukiGlassPill` | `backdrop, contentPadding` | 玻璃胶囊，至少 48dp，适合短标签/单个操作 |
| `TanukiGlassChrome` | `backdrop, contentPadding` | 顶栏/底栏 chrome，至少 56dp |
| `TanukiCardSurface` | `onClick, content` | 不模糊内容卡；可点击时走 `TanukiPressable` |
| `TanukiPressable` | `onClick, enabled, shape, pressScale` | 0.98 缩放 + 按压反馈；无回调时不拦截触摸 |
| `TanukiBottomSheet` | `visible, onDismissRequest, title, subtitle, content` | 遮罩/返回键/下拉关闭；96dp 阈值；结构动画走令牌 |
| `TanukiStreamingCursor` | `visible` | 2×18dp 竖条；2.4s 明暗周期；reduced-motion 常亮 |
| `TanukiCodeText` | `text, maxLines` | 唯一允许等宽的命令/代码文本 |
| `TanukiPathText` | `path, maxLines` | 等宽路径文本，默认单行省略 |

主题入口：`TanukiDesignTheme(dark, reducedMotion) { ... }`。组件通过 `TanukiDesign.tokens` 读取颜色、排版和 reduced-motion 状态；未包裹主题时会明确报错，不会静默使用错误主题。

## 8. 各页面接入方式

本 PR 只新增组件和令牌，不改业务页面。后续页面接入按以下顺序进行，每次只替换一个关注点并跑对应回归。

| 页面/文件 | 替换点 | 使用组件 | 注意事项 |
|---|---|---|---|
| `MainActivity.kt` / 根组合 | 根主题 | `TanukiDesignTheme` | 与现有 `TanukiTheme` 二选一；迁移期不要嵌套两套主题，避免颜色来源不一致 |
| `TanukiUi.kt` 概览 | 主操作、概览卡、运行状态 | `TanukiPillButton`、`TanukiCardSurface`、`TanukiStatusDot/Chip` | 保留现有回调与状态模型；只替换视觉容器 |
| `TanukiUi.kt` 底部导航 | 导航背板/浮动控制 | `TanukiGlassChrome`、`TanukiGlassPill` | 继续复用现有导航背板录制层；不要把 WebView 放进玻璃层 |
| `DshNativePage.kt` | 加载/空/错、运行状态、流式尾部 | 现有 `com.tanuki.ui.TanukiStateView`、`TanukiStatusChip`、`TanukiStreamingCursor` | 光标只放在流式文本末尾；历史消息不显示光标 |
| `DshSessionManagerPage.kt` | 会话管理弹层 | `TanukiBottomSheet`、现有 `com.tanuki.ui.TanukiStateView` | 弹层放在页面根部；保留现有会话筛选、搜索和生命周期回调 |
| `DshComposerBar.kt` | 发送/停止按钮、附件路径 | `TanukiPillButton`、`TanukiPathText`、`TanukiStatusChip` | 发送/停止的禁用与加载语义沿用现有 controller；不要用视觉状态替代发送状态 |
| `DshApprovalPanel.kt` / `DshToolCard.kt` | 审批/工具状态、命令/路径 | `TanukiStatusDot/Chip`、`TanukiCodeText`、`TanukiPathText` | 危险/成功/警告语义只做展示；不改变审批回答协议 |
| `BrowserChrome.kt` / `AgentBrowserPage.kt` | 菜单弹层、顶栏、圆形/胶囊控制 | `TanukiBottomSheet`、`TanukiGlassChrome`、`TanukiPillButton` | 不改变 WebView 生命周期、整页截图和返回逻辑 |
| `WorkspaceFilesPage.kt` / `WorkspaceStorageControls.kt` | 空/错/载、文件行按压、路径 | 现有 `com.tanuki.ui.TanukiStateView`、`TanukiPressable`、`TanukiPathText` | 文件行原有长按/多选语义不能被按压容器吞掉；先做单一入口回归 |
| `SetupScreens.kt` / `RuntimeComponents.kt` | 安装阶段、成功/失败/进行中 | `TanukiStatusDot/Chip`、现有 `com.tanuki.ui.TanukiStateView` | 进度必须保留真实阶段和百分比；不能用状态 chip 代替真实进度 |
| `DshVersionManagerDialog.kt` | 版本列表弹层 | `TanukiBottomSheet`、`TanukiStatusChip` | 卸载/切换确认仍是独立安全动作，不并入视觉弹层 |

接入约束：

- 每次只改一个页面的一个关注点，保留现有业务状态、回调和安全边界。
- 不把现有 `com.tanuki.ui.TanukiStateView` 用作整页错误横幅；消息流内的失败行仍按行为规格显示。
- 不把等宽字体用于普通标签、按钮或标题；`TanukiCodeText`/`TanukiPathText` 是唯一入口。
- 玻璃层放在内容之上时，必须提供真实背板；没有背板就用 card/sheet 的不透明表面。
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

本轮已执行：

```bash
./gradlew :app:compileModernDebugKotlin -x buildNativeRuntime
./gradlew :app:testModernDebugUnitTest \
  --tests 'com.tanuki.ui.design.TanukiDesignTokensTest' -x buildNativeRuntime
./gradlew :app:assembleModernDebug :app:testModernDebugUnitTest \
  :app:lintModernDebug -x buildNativeRuntime
```

结果：编译通过；定向 JVM 19 tests / 0 failures / 0 errors / 0 skipped；完整 JVM 952 tests / 102 个结果文件 / 0 failures / 0 errors / 0 skipped；lint `BUILD SUCCESSFUL`，0 errors / 103 warnings / 1 hint，新增的 `ui/design` 文件没有 lint 告警；assemble 通过。该 APK 使用 `-x buildNativeRuntime`，不含原生运行时，不能作为设备验收或发布包。未执行：完整原生构建、Android 仪器测试、模拟器/真机视觉验收。页面尚未接入，因此本轮不能宣称任何业务页面的最终视觉已经验收。

## 11. 文件清单

| 文件 | 作用 |
|---|---|
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiColorMath.kt` | OKLCH→sRGB、色域映射、WCAG 亮度/对比度、alpha 合成 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiPalette.kt` | 共享明度阶梯、五组色相、浅/深角色令牌、对比度审计 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiTokenSpec.kt` | 间距、圆角、四档字体、动效、海拔四层纯值 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiDesignTheme.kt` | Compose 令牌入口、字体/形状映射、reduced-motion |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiComponents.kt` | 胶囊按钮、状态点/chip、按压容器、流式光标、代码/路径文本 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiGlass.kt` | chrome/sheet/card/pill 表面、玻璃胶囊、内容卡 |
| `app/src/main/kotlin/com/tanuki/ui/design/TanukiBottomSheet.kt` | 可拖拽关闭的底部弹层骨架 |
| `app/src/test/kotlin/com/tanuki/ui/design/TanukiDesignTokensTest.kt` | JVM 对比度、转换、令牌取值回归 |
