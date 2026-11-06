# CI 门禁

`.github/workflows/verify.yml` 在每次 `push` 以及面向 `main` 的 Pull Request 上运行。CI 只做可重复的源码检查、Android 编译、JVM 单测与 lint，不跑设备、不使用 secrets、不发布。

## 任务

### Host scripts and handoff checks

运行以下脚本，任一步失败即门禁失败：

```bash
python3 scripts/test-agent-workflow.py
python3 scripts/test-app-release.py
python3 scripts/test-publication.py
python3 scripts/test-development-gates.py
python3 scripts/check-rpc-contract.py
node scripts/test-browser-client.mjs
node scripts/test-phone-client.mjs
python3 scripts/check-handoff.py
```

Host job 使用 Python 3.12 与 Node 22。`check-handoff.py` 放在最后：W1 的交接恢复记录尚未合并时它会失败，但前面的独立协议与客户端回归仍会完整执行；该步骤不会被跳过或标记为 `continue-on-error`。

其中 `check-rpc-contract.py` 同时比对 Browser/Phone 的客户端与原生方法集、`/rpc`、Bearer、token 正则、请求/响应大小上限和错误码格式；任何一侧单独扩展协议都会失败。`test-browser-client.mjs` 与 `test-phone-client.mjs` 使用本机回环 mock server，不依赖真实浏览器、设备或用户能力配置。

`test-development-gates.py` 用假 `gh`/`ao` 和临时 fixture 离线验证合并前门禁：旧 SHA review、无 review、空/排队/失败/跳过 checks、QA 漏项、身份不符、纯文档豁免和有效 QA 证据。它不访问网络、真实 PR 或设备，也不执行证据里的命令。

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

## 合并前门禁与独立审查

CI 的 `verify` 只证明当前提交的 Host/Android 检查结果，不等于可以合并。合并前由编排者运行只读门禁：

```bash
python3 scripts/check-development-gates.py --pr <PR> --session <实现 worker> [--qa-evidence <json>]
```

它要求 PR 属于当前仓库、打开且目标为 `main`、head 在查询期间未变化、当前 head 的每个 check 都是 `pass`、绑定当前 head 的 AO 原生 review 为项目配置的独立 reviewer 且 verdict 为 `approved`，并按 [QA-GATES](QA-GATES.md) 判断是否需要独立 Android QA。空 checks、排队、失败、跳过、旧 SHA review、自评和缺失 QA 证据都拒绝。

该脚本不替代 provider 的 required review 或分支保护：AO approved 不是 GitHub approval。CI 也不运行这个门禁，因为它依赖当时的 PR 与 AO review 状态；离线回归 `test-development-gates.py` 在 Host job 中执行。

## 已知依赖

- Host job 的交接检查依赖 `docs/handoffs/CURRENT.json` 与当前公开源码指纹一致。新增或修改 `scripts/`、`app/src/` 等公共文件后，需要由交接维护方刷新记录与印章；CI 不会跳过该检查。
- Android job 不生成原生运行时，涉及 `libproot`/`libsupervisor` 的行为必须由本地构建与设备验证覆盖。

## 本地准备

CI 会在干净 runner 上安装 SDK；本地不需要为 CI 额外安装工具。需要复现 Android job 时，按仓库上手指南准备 JDK 17、SDK 36、build-tools 35，并避免与设备安装或其它 flavor 构建并行执行 Gradle。
