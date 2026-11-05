# 交接：浏览器输入法线程崩溃修复

- Agent/会话标识：browser-input-thread
- 时间：2026-11-05 CST
- 状态：修复、构建与设备验证完成；准备推送贡献分支
- 前一记录：[v0.1.5交付依据](20261104-1632-pr-release-015.md)

## 用户目标与范围

修复用户在浏览器登录页键入时的闪退，补回归、模拟器验证并准备贡献 PR。不改登录协议、凭据、模型、下载机制或版本号。用户已允许复用当前模拟器测试及贡献流程。

## 当前状态

分支 `fix-browser-input-thread-crash`，基于 `4509972`。原崩溃为 InputConnectionHandlerThread 调用 directInput 的主线程断言。早期 inputGate 草稿及两项直接调用测试已撤换：草稿跨线程访问计时和非原子状态更新，原测试不足以覆盖真实输入连接。

最终实现把输入连接的权限判断同步转投主线程（500ms上限）；保留 delegate 调用线程，超时/中断拒绝输入并取消排队判断。控制器保持原样。当前6项新增及16项关联设备用例均通过。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/browser/InteractionWebView.kt` | 输入法回调与控制器主线程之间的有界调度 |
| `app/src/androidTest/kotlin/com/tanuki/BrowserInputThreadTest.kt` | 六种回调与主线程/拒绝/超时/中断/真实WebView输入回归 |
| 本记录、AGENT-STATE、TEST-REPORT、BROWSER-WORKSPACE | 保存验证证据、线程边界与限制 |

## 验证记录

- 复用 `tanuki-test / emulator-5554`，API34/ARM64/WebView159.0.8133.0。覆盖前检查无node/proot/apt/dpkg运行进程；使用隔离缓存目录和本地HTTP合成页面，不读取账号数据。
- JDK17/Gradle8.13，`-PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:assembleModernDebug :app:assembleModernDebugAndroidTest :app:testModernDebugUnitTest :app:lintModernDebug`：1分2秒成功，97 JVM通过（0失败/错误/跳过），lint0错误/71警告/1Hint。
- apkanalyzer/aapt核验 app/test目标 `com.tanuki.modern.workspacecheck`，双包同签名覆盖成功，无卸载/清数据。
- 新6项首跑5通过/1失败：真实输入、接管、退格已通过，后续测试夹具误用 `revoke()` 而非持久收回入口 `takeControl()`，纠正后仅重编测试APK，6项全部通过。
- 最终产品APK上 `AgentBrowserControlTest` 14项 + `BrowserTabLifecycleTest` 2项通过（70.441秒）；最终设备共22项。完整类与命令见TEST-REPORT新增节。
- 早期草稿的2项测试与此前版本回归不计入最终结果；真实账号、真机、其他WebView/输入法及全部浏览器回归未执行。

## 未完成与暂停项

代码验证完成。无GitHub API登录凭据，若无法直接创建PR，可推送分支并提供预填比较链接交用户创建；不能声称PR已经创建。API Key保存继续暂停。

## 下一步

1. 刷新交接/源码包、核对外部SHA与公开扫描，按明确文件提交并推送本分支。
2. 提供PR说明与比较链接；用户可在现有模拟器重新验证原页面输入，真实账号登录不由测试代办。

## 约束与风险

不卸载、清数据或恢复快照，不覆盖运行中的安装。main等待上限到达会拒绝当次输入，需要用户重试；不能把等待超时记成输入成功。所有控制器状态仍仅由主线程访问。

## 产物与校验

开发debug构建沿用0.1.5/7，本轮不发版。APK在 `app/build/outputs/apk/modern/debug/app-modern-debug.apk`，SHA-256 `ccdaff15b60b8dfecc50a5718aef9e0c43aad083d2f7d564d65c9e6eb2bc6509`，已覆盖本机测试安装。源码准备区通过项目脚本随当前工作树生成，不改历史发布附件。

交接清理审阅：保留前轮 `20261104-1632-pr-release-015.md` 作为历史发布证据和未完成设备/账号范围依据，本轮未替代其发布流程；目录无其他已失效过程记录可删。原始日志仅放忽略目录 `.build`，不提交截图/账号/原始日志。提交、推送和PR创建状态以最终回执为准，不为回填提交自身重复提交。
