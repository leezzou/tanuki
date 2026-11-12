# 交接：设置搜索与外观选择

- Agent/会话标识：android_agent-106
- 时间：2026-11-12 00:53 +0800开始
- 状态：模块实现与Host编译/定向JVM/lint完成，交付对象为独立功能PR，尚未设备验收
- 前一记录：[0.1.14构建历史](20261111-2313-rebuild-0.1.14.md)

## 用户目标与范围

在独立模块实现按标题、同义词、路径搜索设置，并将结果定位到白名单子页/控件。新增持久的跟随系统、浅色、深色选择，默认跟随系统。保留所有现有设置，不扩展语言/字体或浏览器授权，不碰凭据。TanukiUi.kt接线由105维护；内部主题令牌由101维护；本会话仅消费外层主题。本人实现部分需独立审查与独立Android QA，不能称为独立自审。

## 当前状态

开始时远端main与本地HEAD均为3c56167b94efeff47a20bd609b389708ba080ce5，分支ao/tanuki-3c56167b-0/settings-appearance。105已确认并冻结29个目标/section、搜索与外观页API，正准备TanukiUi入口/保存查询返回/折叠区定位；待本模块稳定commit提供依赖后组合编译，不推不可编译入口。101已回执确认外层主题无owner冲突，支持现有dark参数，保留reducedMotion入口。0.1.14实际已发布，旧“未发布”状态不作为本轮事实；本功能PR不修改共享状态/印章。

## 本次改动

已新增SettingsCatalog.kt固定29项导航目标及标题/别名/路径匹配；SettingsSearch.kt搜索、空态/清除和控件滚动定位；AppearancePreferences.kt纯状态控制器、AppearancePreferenceStore.kt独立原子存储、AppearanceSettingsPage.kt单选及失败/保存状态。新增SettingsCatalogTest.kt、AppearancePreferencesTest.kt共11项JVM回归（含连续选择和提交后调用方离开的时序），以及SettingsSearchInteractionTest.kt五项使用合成状态的Android交互回归（含首屏外具体控件的滚动定位且不执行动作）。11项JVM已实际运行通过；五项Android回归仅源码compile通过，未执行设备测试。合成线程握手采用30秒有界等待，保持持久化时序断言，避免高swap宿主的短调度误判。已只读核对101的PR #106完整head f44ff39aa2728b96849b27959778aaf49d8aa970文件清单，不含ui/TanukiDesign.kt，按明确owner分配接入该外层主题的偏好订阅，未改内部tokens，并已向101同步边界。TanukiUi.kt入口及折叠区展开/返回栈由105接线，尚不能称已在主线可用。102已回执MainActivity系统栏明暗改为TanukiDesign.tokens.isDark并移除旧系统主题import，尚待其最终增量验证回执。另新增TanukiSystemBars.kt统一有效主题的栏背景/图标策略，不改全屏显隐或刘海布局；104已确认唯一接线DshSessionManagerActivity的TanukiTheme内容入口，待本模块稳定commit后再引用并组合编译，不把该Activity栏策略作为永久未覆盖边界。

## 验证记录

102明确收取最终增量退出0并释放后，106独占执行本功能产品/AndroidTest源码compile、11项定向JVM和lint。首轮退出1（3m37s）：产品Kotlin compile完成，AndroidTest因新增测试误导入assertDoesNotExist失败，该API实际为节点对象方法；移除无效import并保留原断言后，第二轮同配置命令已完成产品/AndroidTest/JVM源码compile与定向JVM，XML实核SettingsCatalogTest 5项、AppearancePreferencesTest 6项，共11项/2套件，0 failures/errors/skipped。最终整条命令正式收取退出0、BUILD SUCCESSFUL（6m36s，47任务/16执行/31增量）；lint XML独立实核0 errors/117 warnings/2 hints，106全部持有文件0告警，未降低规则。首轮JVM/lint尚未执行；未执行设备测试。不把此前环境准备或他人局部测试当作本功能通过。Git作者/提交者已实核为指定公开身份。首次普通交接检查因未包含检查器规定的明确未验证词而退出1；补充准确表述后重跑通过。git diff --check通过（新增未跟踪文件不在该命令检查范围内）；另以脚本检查8个新增Kotlin文件的尾部空白及末尾换行通过。提交收尾已执行实际索引/工作树公开边界检查863文件/863索引项/0 findings，唯一交接及git diff --check通过；源码候选863文件及ZIP公开边界检查0 findings。该候选未包含native对应源码附件且不分发二进制，不作为正式发布源码附件；外部校验清单随候选重新生成，不把ZIP自身hash写回归档。与105当前未提交接线草稿做联合静态覆盖检查：29个白名单目标均有真实SettingsSearchAnchor，搜索组件无业务命令回调；这不是compile或设备验证。只读检查发现设置根搜索页Back直接退出而未先清查询，已交105修正；再次只读确认其settingsBack已补根有查询先清再退出分支，子页返回仍保留查询。105报告已新增跨页定位/返回用例，106尚未编译或设备执行，不计通过；未越owner修改。

实际Host命令（先加载JDK17和本会话隔离环境；私有init仅引用仓库外既有debug密钥路径，无口令配置，不随源码分发）：

```bash
./gradlew --no-daemon --console=plain --max-workers=1 -PtestBuildType=debug \
  -I scripts/workspace-verification.init.gradle \
  -I .build/workspacecheck-signing-reference.init.gradle -x buildNativeRuntime \
  :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin \
  :app:testModernDebugUnitTest --tests com.tanuki.SettingsCatalogTest \
  --tests com.tanuki.AppearancePreferencesTest :app:lintModernDebug
```

`-x buildNativeRuntime`仅用于本轮Host门禁，不生成/交付设备包；Android回归只编译，不计设备通过。此前`signingReport`额外显式使用`-PdevelopmentReleaseSigning=true`，验证release确实引用同一debug配置。

补充Host交付核验（不属于本功能验证）：101构建的主题QA debug APK绑定head f44ff39aa2728b96849b27959778aaf49d8aa970/tree 61621285b5ad0976bb895a0916b5abfc39b883c6，tracked clean；106实际核验18011563字节、SHA256 0b8134a7ca0a2f8007e99aa6d0c0d1d0adfebcc008c3c8ab6c6be96c97e4c431、签名371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f、com.tanuki.modern.workspacecheck、0.1.14/code19、minSdk26/target34/debuggable=true、三库ELF64/AArch64且匹配既有native缓存。首轮manifest解析因当前aapt2输出十进制不匹配旧格式而退出1；修正解析后退出0，APK未变。结果已交100/101/37；无安装/Android行为验证，不含106设置，不算本功能测试或0.1.15发布候选。

## 未完成与暂停项

模块实现和本轮适用Host验证已完成；待105集成模块稳定SHA后组合验证真实入口、104接入独立会话Activity系统栏、当前完整SHA的独立审查及100设备验收。API Key保存、真实模型/账号与真机暂停；本会话不占设备。正式release准备与发布操作另按编排明确阶段执行。

## 下一步

本模块稳定提交交105与104集成：105持有TanukiUi真实入口与折叠/返回接线，104持有独立会话Activity系统栏入口；其实际组合SHA须重新compile/适用回归，并由独立reviewer与100验收，不把本轮3c56167b模块结果沿用为组合验证。普通功能PR不刷新共享状态/印章；正式0.1.15打包仅在编排明确开始后执行，merge/tag/Release/上传交编排。

补充[设置模块说明](../SETTINGS.md)，明确搜索只导航、保存失败语义、主题覆盖范围与跨PR接线边界。

## 约束与风险

签名实核：按编排最新明确授权，106私有init仅覆盖仓库外既有debug.storeFile，保留AGP自身其余开发签名配置，校验文件已存在且不重生密钥；不读取/导出口令，不改产品或production配置。实际`:app:signingReport`退出0（1m25s），modernDebug和显式developmentReleaseSigning=true的modernRelease均为debug配置且SHA-256精确371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f，无解锁Error；无需把101的临时三环境变量注入当作本会话前置条件。日志有SDK analytics.settings写权限提示，但该任务成功。此结果仅证明配置可引用该证书，正式APK仍须实际apksigner完整指纹核验。私有init不进入源码/附件。

搜索回调仅导航，不执行清理、恢复、导出等动作；不可任意拼接路由。异步偏好保存失败必须保留原生效模式并报告失败。默认跟随系统，语言与字体不提供假控件。旧平台启动窗口/网页/独立浮窗主题边界须明确，不将应用内主题切换冒称所有网页与系统窗口同步。

## 产物与校验

本阶段无APK、源码ZIP或发布六附件，未安装、合并、tag或上传。准备环境报告目录曾被沙箱拒绝；按编排要求不再重试，完整Host准备交接已通过ao send分四段发送37并明确未落盘，由编排保存notes，不再列为产品阻塞。首次单条发送超过MESSAGE_TOO_LONG上限失败，分段发送已成功。唯一功能交接为本文件，随功能提交交付；实际PR/head以provider和会话回执为准，不在源码内回填自身提交hash。源码ZIP只用于公开候选检查，不是正式发布附件，其外部清单不进入Git。本次没有确认属于设置范围且已完全过期的记录；旧设备QA、C批、视觉P2及rebuild记录仍保留独有证据、未完成边界或共享指针依据，故保留，不按日期删除。普通PR不刷新共享印章。
