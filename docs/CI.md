# CI 门禁

`.github/workflows/verify.yml` 在每次 `push` 以及面向 `main` 的 Pull Request 上运行。CI 只做可重复的源码检查、Android 编译、JVM 单测与 lint，不跑设备、不使用 secrets、不发布。

## 任务

### Host scripts and handoff checks

运行以下脚本，任一步失败即门禁失败：

```bash
python3 scripts/test-agent-workflow.py
python3 scripts/test-app-release.py
python3 scripts/test-publication.py
python3 scripts/check-rpc-contract.py
node scripts/test-browser-client.mjs
node scripts/test-phone-client.mjs
python3 scripts/check-handoff.py
```

Host job 使用 Python 3.12 与 Node 22。`check-handoff.py` 放在最后：W1 的交接恢复记录尚未合并时它会失败，但前面的独立协议与客户端回归仍会完整执行；该步骤不会被跳过或标记为 `continue-on-error`。

其中 `check-rpc-contract.py` 同时比对 Browser/Phone 的客户端与原生方法集、`/rpc`、Bearer、token 正则、请求/响应大小上限和错误码格式；任何一侧单独扩展协议都会失败。`test-browser-client.mjs` 与 `test-phone-client.mjs` 使用本机回环 mock server，不依赖真实浏览器、设备或用户能力配置。`test-agent-workflow.py` 还会用 mock ADB/apkanalyzer/apksigner 覆盖设备验收门禁：APK 哈希/签名/版本/ABI/原生库、WebView 最低版本、忙碌进程与活动安装会话，以及 install 后版本确认；CI 不连接任何设备。

### Android build, JVM tests and lint

安装 JDK 17、Android SDK platform 36 与 build-tools 35，然后执行：

```bash
./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle \
  -x buildNativeRuntime \
  :app:assembleModernDebug \
  :app:assembleModernDebugAndroidTest \
  :app:testModernDebugUnitTest \
  :app:lintModernDebug
```

## 原生运行时边界

CI 不重建原生 `.so`，原生构建由 W2 的本地引导和人工验证覆盖。

`-x buildNativeRuntime` 用于避免在 Ubuntu runner 上引入 NDK/macOS 依赖。仓库当前不跟踪 `app/src/main/jniLibs`，因此 CI 检出的工作区没有预编译原生库；CI 构建出的 APK 只用于编译、JVM 单测和 lint 门禁，不是可运行交付物，也不能替代设备验证或正式发布包。

## 已知依赖

- Host job 的交接检查依赖 `docs/handoffs/CURRENT.json` 与当前公开源码指纹一致。新增或修改 `scripts/`、`app/src/` 等公共文件后，需要由交接维护方刷新记录与印章；CI 不会跳过该检查。
- Android job 不生成原生运行时，涉及 `libproot`/`libsupervisor` 的行为必须由本地构建与设备验证覆盖。

## 本地准备

CI 会在干净 runner 上安装 SDK；本地不需要为 CI 额外安装工具。需要复现 Android job 时，按仓库上手指南准备 JDK 17、SDK 36、build-tools 35，并避免与设备安装或其它 flavor 构建并行执行 Gradle。
