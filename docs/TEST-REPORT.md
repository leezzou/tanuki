# 测试现状

Tanuki 验证目标：`com.tanuki.modern.workspacecheck`，命名空间 `com.tanuki`，ARM64、minSdk26/targetSdk34。debug/release 构建与证书、宿主与设备执行必须分别记录；源码中存在测试不等于已运行。

## 0.1.10 Pre-release 最终验证（2026-11-09，android_agent-32，非设备）

- 范围：最终 `main=9e7edbb929cbde4e3560e94a7c157803cac11e95` 上的 0.1.10/code12、workspacecheck 渠道发布；包含 PR #21 设备温控、#22 DSH 通知、#23 XLSX、#24 PDF、#25 失败分类、#26 原生界面切换、#27 冷启动、#28 DOCX、#29 远程主机。
- 实际执行：
  - `source scripts/env.sh && ./gradlew --no-daemon -x buildNativeRuntime :app:compileModernDebugKotlin :app:testModernDebugUnitTest :app:lintModernDebug` → BUILD SUCCESSFUL（3m07s），**535 tests / 0 failures / 0 errors / 0 skipped**。
  - `python3 scripts/prepare-app-release.py --version 0.1.10 --version-code 12 --channel workspacecheck` → BUILD SUCCESSFUL（2m05s）；正式 APK 实核 `com.tanuki.modern.workspacecheck`、0.1.10/code12、minSdk26、证书 `371dc301...`、11,485,978 字节、SHA-256 `66cacc393fe5e7633df2079dd278ef21dd2f7731eb6f977f30a97f2638e1563d`；旧 `d9b4aa21` 候选作废。
  - `python3 scripts/check-handoff.py`、`check-publication.py --git`、`package-source.py` → 通过。
- 0.1.9 已发布事实（公开 Release 只读核对）：`v0.1.9` tag `5c680b9`，Pre-release，`versionCode=11`；APK SHA-256 `210b6ec2119e8dd6ee08ec3d2be07889f1fa36720c6e15dd24d74a1d25a49dc8`、10,506,550 字节、证书 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。
- 未执行：设备/模拟器安装、升级覆盖、真机 QA；本记录不代表 0.1.10 已通过设备验收。

## 原生输入与附件验证（2026-11-10，android_agent-46，非设备）

- 范围：原生页底部输入栏；多行输入、按会话文本草稿、`Ctrl/⌘+Enter` 发送、@ 文件/会话补全、斜杠命令目录/补全/执行；图片/文件选择、图片缩略图与大图预览、普通文件上传换 receipt、粘贴图片、拖入文件；官方 `session/prompt`/`fileUploads/upload`/`commands/list|execute`/`fileReferences/list`/`sessionReferenceResolver/candidates` 协议；图片/文件大小与类型限制中文错误。基线 `origin/main=0555c3c0eaeeed181269b5e68733ccfdd48d9e75`，分支 `ao/input-attachments/root`。
- 实际执行（`source scripts/ao-worktree-env.sh`，`-x buildNativeRuntime`）：
  - `:app:compileModernDebugKotlin` → BUILD SUCCESSFUL。
  - `:app:testModernDebugUnitTest --tests 'com.tanuki.DshComposerProtocolTest' --tests 'com.tanuki.DshComposerClientTest' --tests 'com.tanuki.DshComposerControllerTest' --tests 'com.tanuki.DshComposerHttpTest'` → BUILD SUCCESSFUL，**37 tests / 0 failures / 0 errors / 0 skipped**。
  - `:app:testModernDebugUnitTest` → BUILD SUCCESSFUL，69 个结果文件、**572 tests / 0 failures / 0 errors / 0 skipped**（基线 535 + 新增 37）。
  - `:app:lintModernDebug` → BUILD SUCCESSFUL，**0 errors / 95 warnings / 1 hint**；新增文件无 lint 告警。
- 新增回归：`DshComposerProtocolTest` 15 项（prompt/upload/commands/引用参数、图片上限解析、命令/文件/会话候选解析、PNG/JPEG/GIF/WebP 嗅探与尺寸、图片/文件限制、@ 引号/目录/跨行/邮箱式文本、斜杠识别、文件名清洗、字节格式化）；`DshComposerClientTest` 8 项（projection/commands/引用端点、上传 receipt/base64、prompt accepted、命令成功/业务失败/未解析、reset、非法输入、远程 loopback 协议）；`DshComposerControllerTest` 11 项（按会话草稿、图片+文本发送、文件先上传、命令附件策略、图片超限中文错误、@ 双来源候选、发送失败/官方拒绝保留草稿、官方附件错误码中文化、onSent 会话）；`DshComposerHttpTest` 3 项（Cookie 只发 `/api`、token 不进请求体/错误、401 单次重试与 reset 重连）。
- 未执行：真实 Android 设备/模拟器上的输入栏布局、软/硬件键盘、图片/文件选择、缩略图/大图预览、粘贴、拖入、附件上传失败重试；真实 PRoot DSH `/connect`/`session/prompt`/`fileUploads/upload`/`commands`；assemble、Android 仪器、真机。合成 loopback 与 JVM 测试不能替代设备/真实 DSH 验收。
- 已知边界：附件不跨会话保留，只持久化文本草稿；普通文件串行上传；图片在发送时 base64 编码；历史附件仍不在原生消息区展示；未实现流式跟随、取消、审批/提问与模型选择。

## 设备温控与省电策略验证（2026-11-09，android_agent-30，非设备）

- 范围：新增 `DeviceConditionPolicy`/`DeviceConditionMonitor`，状态 `NORMAL/WARM/HOT/CRITICAL/LOW_BATTERY`；热/低电/低内存降级与迟滞；后台更新检查按具体 Worker 登记/恢复；DSH 依赖下载并发上限；日志展示行数；PDF 预览尺寸/像素上限；设置开关与低电阈值；进程重启状态恢复。
- 分支 `ao/thermal-policy/device-condition-policy`，已合并 `origin/main=0ba11da` 解决冲突，在 `/tmp/tanuki-thermal-30` 独立 clone 验证；未使用共享 worktree 的 `.git` 写操作。
- 实际执行：
  - `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin --stacktrace` → BUILD SUCCESSFUL（5m29s，19 tasks）。
  - `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.DeviceConditionPolicyTest' --tests 'com.tanuki.DeviceConditionRefreshQueueTest' --tests 'com.tanuki.DeviceConditionInitializationTest' --tests 'com.tanuki.DeviceConditionConfigWriterTest' --tests 'com.tanuki.NpmDownloadPolicyTest' --tests 'com.tanuki.NpmAdaptiveWorkerPoolTest'` → BUILD SUCCESSFUL；`DeviceConditionPolicyTest` 25 项、`DeviceConditionRefreshQueueTest` 2 项、`DeviceConditionInitializationTest` 2 项、`DeviceConditionConfigWriterTest` 1 项、`NpmDownloadPolicyTest` 7 项、`NpmAdaptiveWorkerPoolTest` 2 项，0 failures / 0 errors / 0 skipped。
- 新增回归覆盖：正常态全额、热等级即时升级、CRITICAL 降级用更低退出边界并持续 30 秒、低电 20%/25% 进入退出迟滞与充电覆盖、未知电量不误报、低内存 60 秒解除迟滞、过热+低电原因并存且取更严格额度、后台检查按具体 Worker 登记与恢复、无 Worker 跳过时恢复不发请求、只恢复实际跳过的一项、登记幂等、待办确认入队后才清除、入队失败保留重试、部分确认只清除已确认项、主线程刷新不内联写盘、突发刷新合并、DSH/前台任务始终 RUN、关闭策略恢复并补跑、进程重启保留延后直至迟滞结束、各状态额度单调下降、`sinceMillis` 仅在主状态变化时更新、非法阈值拒绝、用户单路下载不降并发、依赖下载外部上限恢复。
- 完整 JVM 与 lint：`./gradlew :app:testModernDebugUnitTest :app:lintModernDebug --rerun-tasks` → BUILD SUCCESSFUL（2m17s，35 tasks executed）；59 个结果文件，424 tests / 0 failures / 0 errors / 0 skipped（合并 main 的原生界面/PDF/冷启动/聊天通知/XLSX/安全模式回归后 + 新增 25 策略 + 1 配置写入 + 2 刷新队列 + 2 初始化 + 1 下载上限）；lint 0 个 Error。
- 未执行：真机/模拟器温控回调、电池广播、系统低内存、WorkManager 真实调度与重启恢复、Android 13+ 通知、设置页 UI、PDF 降级实际像素、assemble/仪器测试。编译、JVM 与 lint 不能替代设备验收。
## PDF 原生阅读体验打磨验证（2026-11-09，android_agent-35，非设备）

- 范围：`WorkspacePdfPreview` 从单页重开改为单 `PdfRenderer` 会话 + 连续纵向 `LazyColumn`；当前页前后各预加载 1 页；按字节 LRU 缓存与位图回收；适应宽度/适应页面；双指 1–5x、双击 1x/2.5x、放大后横向平移；页码/总页数、快速跳页；加密/损坏/超大/内存不足中文提示。基线 `main=2f3fac9301c7c3484df85e99091e8f63dddc0a48`，分支 `ao/pdf-reading/native-reading`。
- 实际执行：
  - `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin` → BUILD SUCCESSFUL（改造后增量 3m08s）。
  - `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.Pdf*' --tests 'com.tanuki.ZoomPolicyTest'` → BUILD SUCCESSFUL，52 tests / 0 failures（PDF 相关 46 + `ZoomPolicyTest` 6）。
  - `./gradlew :app:testModernDebugUnitTest` → BUILD SUCCESSFUL，365 tests / 0 failures / 0 errors / 0 skipped（rebase 到 `main=0ba11da`，含 main 既有 306 项）；`./gradlew :app:lintModernDebug` 通过。
- 新增回归 59 项：`PdfPageCacheTest` 8（LRU、保护键、超大单页、替换记账、remove/clear、非法参数）；`PdfPageMemoryTest` 14（淘汰回收、显示中延迟回收、关闭全回收、同页替换延迟回收、touch、关闭后存入、显示标记、discard 预算移除与延迟回收）；`PdfPreloadPolicyTest` 10（当前页优先、边界、半径、大文档小窗口、非法半径；可见页 >3 时全部进入渲染计划、首尾预加载、去重/边界、计划大小有界）；`PdfMemoryPolicyTest` 6（堆比例预算上下限、单页像素上限、驻留页数联动上限、位图字节记账、512 MiB 文件限制、渲染计划 + 在途页联合预算）；`PdfJumpPolicyTest` 4（纯数字/`12/50`/`第12页` 解析、越界与非法输入、文案）；`PdfViewportPolicyTest` 10（适应宽度/整页、缩放范围、平移边界、缩放锚点、非 1× 连续放大/缩小补偿、1–5× 首尾可达、中心页判定、页间空隙）；`PdfViewportRenderPolicyTest` 7（A4 视口尺寸、未知视口回退、超大/窄/小页面、像素预算、超采样）。既有 `PdfRenderPolicyTest` 4 项与 `ZoomPolicyTest` 6 项不回归。
- 复审修复（Codex 3 条 P2）：
  - 缩放后的首尾内容进入滚动范围：按 `H×(scale−1)/(2×scale)` 增加上下边缘留白，1× 为 0；单页与多页（1/3/20 页）在 1/1.5/2/2.5/4/5× 下首尾文字均可达。补偿延迟到下一帧（新留白完成测量后）执行，避免旧滚动边界截断。
  - 纵向补偿改为 `(centroidY−H/2)×(1/oldScale−1/newScale)`；新增 2×→4× 与 4×→2× 回归，避免非 1× 起点捏合持续偏移。
  - 渲染集合改为「全部可见页 + 可见范围外首尾各 1 页预加载」；同屏 >3 页时不再长期停留在“正在渲染”。
  - 固定视口裁剪：外层阅读容器 `clipToBounds()`，缩放层不能绘制到标题栏/控制栏；该条依赖 Compose 绘制层，JVM 策略测试无法覆盖，需设备/截图验证。
  - 预留失败原子性：先计算可淘汰页总字节，确认足够才淘汰；失败不改变缓存/界面，避免部分淘汰后丢失已回收键；新增失败→释放显示页→重试成功的回归。
  - 分配前按实际驻留预留：`tryReserve` 淘汰未显示缓存页并返回键，界面在创建位图前移除；`store` 消耗预留并按实际驻留收紧，失败/取消归还；新增 LRU 已满后新计划页的峰值回归。
  - 渲染集合与内存预算联动：单页像素上限按 `renderPlan.size + 1`（含在途页）分配，受保护页总字节不超过缓存预算；上限收紧时先 `discard` 超限旧页、等待组合回收后再按新上限渲染；新增计划 + 预算联合回归（4/8/12 个可见页）。
  - 复审修复后目标测试 69 tests / 0 failures；rebase 后完整 JVM 365 / 0 与 lint 通过。
- 未执行：lint、assemble、Android 仪器/真实设备；真实大文档（≥500 页）连续滚动与内存曲线、真实加密/损坏 PDF、捏合/双击/横向平移手感、5x 文字清晰度、深色主题均未验证。编译与 JVM 策略测试不能替代设备验收。
- 已知限制：单页渲染受长边 2048、4,000,000 像素与缓存预算约束，5x 放大清晰度有限；缓存预算为堆的 1/8（24–64 MiB）；512 MiB 是产品打开上限；只读预览，不含编辑/搜索/文本选择。

## 0.1.8 发布状态（2026-11-06，只读复核）

- `v0.1.8` tag 指向 `c196bbf4d211604bc2076de450a8ed2d52ddde99`（0.1.8 发布点）；远端 `main` 已前进到 `a9aa507c06883c62048a89924384ef0a10e01470`（Wave0 PR #10/#11/#12 合并）。`artifacts/releases/0.1.8/` 六附件齐全：APK、`app-update.json`、完整源码 ZIP、原生对应源码 TAR、`RELEASE-SHA256SUMS`、`native-source-SHA256SUMS`。
- 本轮只读复核 APK：包名 `com.tanuki.modern.workspacecheck`、`versionName=0.1.8`、`versionCode=10`、minSdk26、targetSdk34、arm64-v8a；开发证书 SHA-256 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`；APK 10,479,850 字节，SHA-256 `eb695a12778652075e0324c65486534dab98848f073daa14ea631291b69b146e`。公开 Release 的 `app-update.json` 与 APK 均返回 HTTP 200。
- **0.1.8 没有对应的设备测试报告。** 未发现 0.1.8 的 JVM、lint 或设备仪器测试记录；不能把 0.1.7 或更早的数字写成 0.1.8 已通过。
- 当前模拟器 `emulator-5572`：API34/ARM64、WebView `159.0.8133.0`，已安装 0.1.7/code9；任务基线记录 `files/runtime` 与 `files/engines` 为空，本轮未提权读取 release 私有目录。本轮只读查看 serial/API/ABI/WebView/已安装版本，未安装、未清数据、未运行仪器。

### 0.1.8 首轮设备冒烟（2026-11-06，android_agent-10，非完整验收）

- 独立 QA 会话 android_agent-10 在 `emulator-5572`（`Pixel_7_API_34_arm64`，API34/ARM64，WebView `159.0.8133.0`）完成：`install -r` 从 0.1.7/code9 升级到 0.1.8/code10（约 1.57s，保留数据，设备 base.apk 与发布件 SHA-256 一致）；冷启动 `LaunchState COLD`、`TotalTime 1616ms`；首启停在 Ubuntu 安装页 1/4，未执行安装；应用内浏览器加载 GitHub Release 页，WebView 正常；logcat 无 FATAL/ANR。
- 本次只覆盖安装/启动/基础 UI/WebView；Ubuntu/Node/基础工具/DSH 安装、真实模型任务、底部导航点击、真机/MIUI/低 API 未验证，不能写成 0.1.8 已验收或完整设备测试。

## 远程主机直连 SSH 切片验证（2026-11-09，android_agent-34，非设备）

- 范围：远程主机配置校验、Android Keystore AES-GCM 凭据存储、known_hosts 首次确认与变化拒绝、JSch 直连 SSH 本地端口转发、local/remote origin 状态机与失败回退、设置页连接测试。未实现中继/WebRTC、远程 DSH 启停和 Ed25519。
- 实际执行（最终代码）：
  - `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin` → BUILD SUCCESSFUL。
  - `./gradlew :app:assembleModernDebug -x buildNativeRuntime` → BUILD SUCCESSFUL，D8 正常打包 `com.github.mwiede:jsch:2.28.8`。
  - 远程分支 `./gradlew :app:testModernDebugUnitTest` → BUILD SUCCESSFUL，37 个结果文件、**229 tests / 0 failures / 0 errors / 0 skipped**。
  - 合入 `main=5fd1ab4`（安全模式 + XLSX 预览）后 `compileModernDebugKotlin` + `testModernDebugUnitTest` + `lintModernDebug` → BUILD SUCCESSFUL，65 个结果文件、**535 tests / 0 failures / 0 errors / 0 skipped**；lint 0 errors/95 warnings。
  - Review 修复后 `-PtestBuildType=debug :app:compileModernDebugAndroidTestKotlin` → BUILD SUCCESSFUL；新增 Cookie 隔离与 Keystore 设备回归已编译，未运行。
  - `./gradlew :app:lintModernDebug` → BUILD SUCCESSFUL，**0 errors / 80 warnings / 1 hint**；新增文件无 lint 告警。
  - `./gradlew -I scripts/dependency-inventory.init.gradle tanukiDependencyInventory` → 91 runtime artifacts；随后 `python3 scripts/collect-component-licenses.py` 刷新依赖许可清单。
- 第二轮 P1 复审修复（PR #29）：`removeAllCookies` 改在主 Looper 发起、IO 等待后 flush；Cookie 写入用独立串行锁、UI 读取只读 volatile，避免主线程重组与切换等待互锁；交换前由 WebView guard 停止旧文档并等待 `about:blank`，切换期间请求/导航被阻断；新增主线程并发读取、guard 失败设备回归，测试清理也改主 Looper；A→B 切换先切本地 Cookie 再撤销 A 端点，测试连接取消保留 A；`cookieReady` 门禁覆盖 WebView 加载、认证校验/token 重试、HTTP 下载与本地端点发布，失败时快速中止本地启动。新增 3 项控制器时序回归。
- Review 修复（PR #29）：Keystore 加密改为 provider 生成 IV（`cipher.init(ENCRYPT_MODE, key)` 后保存 `cipher.iv`），新增 AndroidKeyStore 设备回归；管理页 Cookie 按逻辑主机分区（本地/每台远程分别保存 header，切换前清空并恢复，失败关闭页面），新增 JVM 分区测试与真实 CookieManager 设备回归；认证校验/自动重试改绑活动端点，不再要求本地 ServiceState.RUNNING；设备测试夹具改用 `attachLocalEndpointForTesting`，移除已删除字段反射；测试连接确认对话框显示实际 SHA256 指纹。
- 新增 `RemoteHostTest` 24 项、`DshRemoteIsolationTest` 4 项：主机/端口/用户名/IPv4/IPv6/token/私钥校验；token URL 只落临时 loopback 端口并编码；配置与端点 `toString` 脱敏；SHA-256 主机指纹固定向量；本地/远程 origin 切换、本地停止保持远程、失败回退、无本地时清空端点；加密存储往返且不落明文、密文篡改不降级、清除配置/凭据/known_hosts；首次指纹确认前不认证、确认后才信任；主机密钥变化拒绝；测试连接不切换 origin 也不保存配置；失败回退中文错误且不泄露密码；隧道探测失败关闭隧道；HTTP 探测 200/302/401/500 与连接拒绝；Cookie 分区、两远程主机不串 Cookie、本地 STOPPED 不误认证、远程活动端点可认证/重试。
- 未执行：真实 Android 设备到真实 sshd、真实 Keystore 加解密、真实远程 DSH token 登录、弱网/断线、设备 UI、Ed25519、Android 仪器测试、真机。JVM 与 mock SSH 不能替代真实主机/设备验收。
## DSH 冷启动优化验证（2026-11-09，android_agent-27，非设备）

- 范围：DSH `web` 启动期只读路径缓存（`/opt/node`、`/opt/engines`、`/opt/pnpm` 与受控支持目录）与首帧前提前启动（仅 Launcher `MAIN + LAUNCHER` 冷启动；仪器测试/内部显式启动跳过；真实 `Snapshot.operationActive` 与独立更新忙碌信号拦截 STAGING/TESTING/RESTORING）；未改 PRoot 边界、官方 npm 包、DSH UI/协议。
- 实际执行：
  - `./gradlew :app:compileModernDebugKotlin :app:compileModernDebugAndroidTestKotlin` → BUILD SUCCESSFUL（含原生运行时重建；既有弃用/平台告警保留）。
  - 合并 `origin/main=0ba11da` 后 `./gradlew -x buildNativeRuntime :app:compileModernDebugKotlin :app:testModernDebugUnitTest :app:lintModernDebug` → BUILD SUCCESSFUL（2m54s）；45 个结果文件、306 tests / 0 failures / 0 errors / 0 skipped，含 `EarlyRuntimeStartTest` 17 项、`DshStartupConfigTest` 6 项及 main 的安全模式/聊天通知/XLSX 回归。
  - `./gradlew :app:lintModernDebug` → 合并后与 JVM 同次 BUILD SUCCESSFUL。
  - `node scripts/test-dsh-path-cache.mjs` → PASS，10 个场景：缓存命中、符号链接/缺失链接、符号链接+`..`交叉命中、缺失路径不缓存、非只读目录直通、异常编码回退、卸载、官方地址行卸载、超时卸载、环境变量关闭。
- 合并冲突已解决；推送后以新 head 的 Host/Android CI 为准。
- 未执行：assemble、Android 仪器/真实设备、真实 PRoot 内 DSH 启动、真机冷启动计时；Node 测试是宿主行为回归，不能替代 Android 设备与 PRoot 验证。
## DSH 运行失败分类与安全模式验证（2026-11-09，android_agent-28，非设备）

- 分支 `ao/runtime-safe-mode/root`，PR [#25](https://github.com/leezzou/tanuki/pull/25)，功能提交 `b4a6ef5`（rebase 后 `dc0494b`）；范围：新增纯 Kotlin `RuntimeFailurePolicy`，覆盖 `LAUNCH_FAILED/STARTUP_FAILED/CRASHED_EARLY/CRASHED_LATE/KILLED/NO_SPACE` 分类、连续两次 startup/early 进入安全模式、安全模式 rescue 启动参数、probation 2 分钟窗口、自动重启上限/退避、noSpace/killed 不自动重启，以及 `state/failure-state.json` 跨进程恢复。RuntimeManager/RuntimeService 接入安全模式与前端入口。
- 验证环境：`/tmp/tanuki-safe-mode` 独立 clone，基线 `2f3fac9301c7c3484df85e99091e8f63dddc0a48`；JDK17、Android SDK36/Build Tools 35；仅构建时通过临时 `/tmp/mirror.init.gradle` 使用可用 Maven 镜像，未提交、未改仓库构建配置。
- 实际执行：
  - `./gradlew -I /tmp/mirror.init.gradle :app:compileModernDebugKotlin --stacktrace` → BUILD SUCCESSFUL（7m 9s，19 tasks）。
  - `./gradlew -I /tmp/mirror.init.gradle :app:testModernDebugUnitTest --stacktrace` → rebase 前 BUILD SUCCESSFUL（1m）；37 个结果文件合计 **237 tests / 0 failures / 0 errors / 0 skipped**。
  - `./gradlew -I /tmp/mirror.init.gradle :app:testModernDebugUnitTest --tests 'com.tanuki.RuntimeFailurePolicyTest'` → BUILD SUCCESSFUL，新增策略回归 **32/32**。
- P1 复审修复：`FailureEvidence.timedOut` 在 noSpace/launch 之后、killed 之前直接返回 `STARTUP_FAILED`；就绪行已打印但 HTTP 未可用而超时也保持 `STARTUP_FAILED`；`RuntimeManager` 在超时清理时不把 143/137 当作原始退出码。新增 143/137、“Killed”、ready 行后 HTTP 超时与 ENOSPC 优先级回归。
- P2 复审修复：`PROBATION` 且上次服务活动时，进程重启一律回 `SAFE`，不再用重开 App 的墙钟时间把 `PROBATION` 提升为 `NORMAL`；服务未活动时保留试用，下次手动启动重新计时。安全模式成功不提交 `RESTORED_PENDING_CHECK`，只有 normal/probation 实际加载恢复 userdata 的启动成功后才写 verified/SUCCESS。新增长间隔重开、非活动服务、过期手动启动与 SAFE 恢复提交边界回归。
- P2 时间基准修复：日志就绪时刻用于区分早/晚崩溃，HTTP 就绪后才启动 probation 计时；`CRASHED_LATE` 现在先检查实际 probation 窗口，窗口内（如日志 t=1s、HTTP t=11s、t=126s 崩溃）回 `SAFE`，不再直接 `onStable` 转 `NORMAL`。新增延迟 HTTP 就绪回归。
- rebase 到 `origin/main=f5debf5`（含 XLSX PR #23）后复跑：`compileModernDebugKotlin`、完整 `testModernDebugUnitTest` 与 `lintModernDebug` BUILD SUCCESSFUL，40 个结果文件合计 **265 tests / 0 failures / 0 errors / 0 skipped**；`RuntimeFailurePolicyTest` 32/32。
- 新增回归覆盖：六类分类与 2 分钟早/晚崩溃边界、ENOSPC/低磁盘优先、signal/OOM 判为 killed、无致命输出退出判为 killed、连续两次 startup/early 进入安全模式、killed/noSpace/launchFailed 打断连续计数、restart 2s/4s/8s 与上限 3 次、probation 内 startup/early/killed/noSpace 回安全模式、probation 晚崩溃转 normal、人工启动重置计数、过期 probation 提升、进程重启恢复安全模式（含未就绪试用超过 2 分钟）、JSON round-trip/非法数据回退、normal/rescue 启动参数与版本注入拒绝。
- CI：PR #25 的 push run 42602010730 与 PR run 42602805776 均 success（Host scripts + handoff；Android assemble/JVM/lint）。Android CI 使用 `-x buildNativeRuntime`，其 APK 无原生 `.so`，不能用于设备验收。
- 未执行：assemble、Android 仪器/真实设备、PRoot 内实际 rescue 启动、真实 DSH 崩溃/低内存/磁盘满注入、UI 视觉与大字体/深色验证；rebase 后 `lintModernDebug` BUILD SUCCESSFUL。JVM 策略测试不能替代真机运行与恢复验收。
- 未验证项：真机崩溃与低内存、真实磁盘满、真实插件配置导致启动失败、安全模式与用户数据的实际隔离效果。PR/独立 Review/合并未授权或未完成。

## 原生界面切换验证（2026-11-09，android_agent-33，非设备）

- 范围：新增“官方界面 / 原生界面”开关；默认官方；原生只读页通过官方 DSH `session/list`、`session/projections`、`session/page` 读取会话与文本消息；仅显示 append-origin（`surfaceOp=append`）且 `source.kind=user` 的文本；列表接口失败自动回退官方界面并保留用户偏好；切换不重启 DSH、不重建 WebView、不修改官方数据。
- 分支/基线：`ao/ui-switch/native-ui`，已 rebase 到 `main=0ba11da`（PR #24 PDF、PR #27 cold-start、PR #23 XLSX、PR #25 runtime-safe-mode、PR #22 chat-notify 合并后）；rebase 后为单一提交，代码无冲突，文档保留 PDF/cold-start/XLSX 记录。
- 实际执行（rebase 后，`/tmp` clone；跳过 `buildNativeRuntime`）：
  - `./gradlew :app:testModernDebugUnitTest -x buildNativeRuntime` → BUILD SUCCESSFUL，393 tests / 0 failures / 0 errors / 0 skipped。
  - `./gradlew :app:lintModernDebug -x buildNativeRuntime` → BUILD SUCCESSFUL。
  - 新增 `DshUiSwitchTest` 10 项：默认官方、原生需运行、回退不改偏好、`/connect` 同 token、拒绝非 loopback/坏 method、官方信封、端点脱敏。
  - 新增 `DshNativeControllerTest` 6 项：原生通知目标冷启动、已加载页面替换选择、目标不在列表仍可见、原生列表失败保留目标给官方回退、当前会话通知强制重读历史、通知决策+分发集成。
  - 新增 `DshNativeApiTest` 12 项：会话标题/切点、projection 仅 sequenced、只取 append-origin 文本/过滤 replace 与注入上下文、业务错误不回显、Cookie 提取、`rpcId` 关联、本地合成服务器验证端点顺序与 Cookie 只发 `/api`、404 错误不含 token、延迟认证期间 reset 不阻塞且旧 Cookie 不发布、reset 后旧 401 不复活、延迟 401 期间新请求不被旧请求清 Cookie。
- 通知点击：原生偏好下目标交给 `DshNativeController.openSession()`，不依赖 WebView 是否连接；官方模式仍走现有 WebView 路由。
- P1/P2 修复：认证网络 I/O 移出 monitor，`reset()` 只短暂持锁并按代次拒绝旧 Cookie 发布；401 重试必须属于当前代次；消息按官方 append-origin 语义过滤。
- 未执行：assemble、仪器测试、真实设备、真实 PRoot 内 DSH 与 WebSocket/审批/发送。最终 head CI 与独立 Pi Review 以 PR 页面为准。

## XLSX 应用内原生预览验证（2026-11-09，android_agent-32，非设备）

- 范围：新增 `XlsxWorkbookParser` 只读 OOXML 解析（workbook/rels/sharedStrings/styles/sheet）、`XlsxValueFormatter` 显示格式、Compose 原生表格 `XlsxTablePreview`、`FileKind.XLSX` 分类/入口与 `WorkspaceFileViewer` 接线。基线 `main=2f3fac9301c7c3484df85e99091e8f63dddc0a48`，分支 `ao/xlsx-preview/xlsx-native-preview`，未引入第三方 XLSX 库，不依赖 LibreOffice/外部 App。
- 实际执行：
  - `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin --console=plain` → BUILD SUCCESSFUL。
  - `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.Xlsx*' --tests 'com.tanuki.WorkspaceFileKindTest'` → BUILD SUCCESSFUL，30 tests / 0 failures。
  - `./gradlew :app:testModernDebugUnitTest --console=plain` → BUILD SUCCESSFUL，233 tests / 0 failures / 0 errors / 0 skipped。
  - `./gradlew :app:lintModernDebug` → BUILD SUCCESSFUL。
- 新增回归（28 项）：`XlsxWorkbookParserTest` 18 项（多 sheet 顺序；共享/内联/布尔/错误/数字/公式缓存值；字体/填充/边框/对齐；列宽/行高/合并/冻结；行列与单元格预算截断；非 zip、缺 workbook、坏 XML 中文错误；条目数/总解压/单条目上限；谎报解压大小的 zip bomb 实际读取上限；共享字符串数量与总文本预算；路径穿越与外部链接拒绝；包内绝对路径；DOCTYPE/深层 XML/未定义实体；1904 日期与默认样式索引；空表；多表合并区域共享单元格预算；共享/内联/公式缓存字符串中的标准实体与数字引用）；`XlsxValueFormatterTest` 4 项（通用数字、千分位/小数/百分比、1900 闰年与 1904 日期时间、布尔）；`XlsxPreviewPolicyTest` 5 项（列字母、缩放 clamp/步进、列宽行高边界、按视口/缩放收缩冻结区、单元格字号换算）；`WorkspaceFileKindTest` 增补 `.xlsx` 与 `.xls`/`.xlsm` 边界。
- 保真边界：支持多工作表、行号/列标表头、冻结行/列、列宽/行高、合并单元格、基础字体/填充/四边边框、水平/垂直对齐、数字/百分比/日期/时间/布尔/错误值与公式缓存值、1904 日期系统。明确不支持图表、宏、透视表、外部链接、图片、复杂条件格式、隐藏行列、主题色、富文本多字体和公式重算；长文本单行省略；跨冻结列的合并按边界裁剪，跨行边框为近似。不宣称完整 Excel 兼容。
- 安全上限：归档 64 MiB、条目 4096、总解压 256 MiB、单条目 32 MiB（并按实际解压字节复核）、sheet 32、行 2000、列 128、单表 40000 单元格、全簿 120000 单元格、共享字符串 50000 条/400 万字符、单元格文本 2000 字/全簿 800 万字、XML 深度 64、合并区域 4096、合并数量 4096，合并生成的起点/covered 占位也计入单表/全簿单元格预算；标准实体/数字引用保留；冻结区按实际视口与缩放收缩，无法容纳的冻结行列回到滚动体；单元格字号按样式磅值与缩放显示；拒绝 DOCDECL、未定义实体、`..`/绝对越界路径与外部工作表链接。
- 未执行：lint、assemble、Android 仪器/真实设备、真实 Excel/WPS 文件、真机大表滚动/冻结/缩放与深色主题、性能与内存实测。编译与 JVM 不能替代设备验收。

## 后台更新检查验证（2026-11-09，android_agent-24，非设备）

- 范围：Tanuki APK 每 30 分钟检查 GitHub `app-update.json`；DSH 每 24 小时检查 npmmirror（失败回退官方 npm）；两份状态独立持久化；同版本通知去重；前台徽标与手动检查保留。未改 DSH 下载/安装逻辑。
- 实际执行：
  - `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin --stacktrace` → BUILD SUCCESSFUL（3m36s，19 tasks）。
  - `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.BackgroundUpdateChecksTest'` → BUILD SUCCESSFUL，21 tests / 0 failures。
  - `./gradlew :app:testModernDebugUnitTest --stacktrace` → BUILD SUCCESSFUL，174 tests / 0 failures / 0 errors / 0 skipped（27 个结果文件）。
- 新增回归：`BackgroundUpdateChecksTest` 23 项，覆盖 30 分钟/24 小时间隔与唯一任务名、12 秒超时范围、APK 版本选择与同/低版本不误报、APK/DSH 状态不可串读、镜像成功不访问官方、镜像损坏/网络失败回退、双源失败保留最近结果并记录错误、成功清除错误、SemVer 预发布比较、同版本只通知一次、最小查询间隔、CHECKING 不持久化、损坏状态回退、手动旧结果不覆盖较新后台快照（APK/DSH 两条链路）。
- P2 复审修复 1：手动检查同步从 Activity `LaunchedEffect` 移到检查完成事件（`ApplicationUpdateManager`/`UpdateManager` 回调），并携带检查开始时间；`applyManualApk`/`applyManualDsh` 拒绝早于后台 `lastCheckedAt` 的旧结果。新增“手动旧结果 → 后台新结果 → Activity 重建”APK/DSH 两条链路回归。
- P2 复审修复 2：APK 的 `evaluateApk`、`decodeApk`、`apkFailure` 全部只按 `versionCode` 判定，允许同 `versionName`、更高 code 的覆盖更新；新增成功 round-trip 与失败保留回归。
- P2 复审修复 3：`ApkUpdateCheckRepository` 初始化时用当前 `BuildConfig.VERSION_NAME/VERSION_CODE` 重算 `available`，保留远端 latest 与 `notifiedVersion`；新增“旧快照 → 同包覆盖升级 → 离线启动不误报”回归。
- P2 复审修复 4：新增 `rebaseDshSnapshot`；`DshUpdateCheckRepository` 初始化及 `RuntimeManager.state.dsh` 变化时只按当前安装身份重算 `installedVersion/available`，保留 latest、lastCheckedAt、source 与 notifiedVersion，不联网、不回放手动候选。新增“发现新版→安装该版→离线重启”和“远端仍有更高版时徽标保留”回归。
- Rebase：2026-11-09 rebase 到 `origin/main=9b32051`（PR #18 DSH 已安装版本管理），解决 `AGENT-STATE`/`CURRENT.json` 冲突；rebase 后 `compileModernDebugKotlin` 与完整 JVM 复跑通过，174 tests / 0 failures（既有 151 + 新增 23）。
- 未执行：lint、assemble、Android 仪器/真实设备、真实 GitHub/npmmirror/npm 请求、WorkManager 实际触发/重启恢复、Android 13 通知权限与展示、深色/大字体 UI。编译与 JVM 不能替代设备与真实网络验收。

## DSH 安装提速与国内镜像验证（2026-11-09，android_agent-25 收尾，非设备）

- 范围：PR #20 的 DSH 锁快速路径、npmmirror 优先生成/官方核验锁、镜像归档映射、404/429/5xx 换源与退避、Range/206 续传、8–16 自适应并发、降目标后 worker 退休；rebase 到 `main=6136451`，同时保留 #19 后台更新检查。
- 实际执行：rebase 后 `:app:compileModernDebugKotlin` 与完整 `:app:testModernDebugUnitTest` → BUILD SUCCESSFUL，205 tests / 0 failures / 0 errors / 0 skipped。`NpmAdaptiveWorkerPoolTest` 升降档 2/2；目标 Npm/Download 回归 61 项通过。
- 未执行：真实 Android/PRoot DSH 首次安装、弱网/镜像 404/429 注入、4000 包端到端吞吐、lint/assemble/仪器/真机；独立 Review 未触发。

## DSH 聊天通知验证（2026-11-09，android_agent-29，非设备）

- 范围：官方 `/api/remote.mux` `$events` 旁路（`api-session/status`、`api-session/error`、`approval/request`、`user-questions/request`）；回合完成/等待确认/错误/权限请求分类；session + DSH 版本 + event 去重；前台且对应会话可见时抑制；Android 13 权限、`dsh-chat` 渠道、group/summary、设置入口、点击打开会话；通知正文固定最小文案。基线 `main=2f3fac9`。
- 实际执行：
  - `source scripts/ao-worktree-env.sh && ./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle :app:compileModernDebugKotlin` → BUILD SUCCESSFUL（首次约 4m29s）。
  - `:app:testModernDebugUnitTest --tests 'com.tanuki.ChatNotificationPolicyTest' --tests 'com.tanuki.ChatNotificationRoutingTest' --tests 'com.tanuki.DshChatProtocolTest'` → BUILD SUCCESSFUL，18 tests / 0 failures。
  - `:app:testModernDebugUnitTest` → BUILD SUCCESSFUL，223 tests / 0 failures / 0 errors / 0 skipped（39 个 XML 结果文件；既有 205 + 新增 18）。
  - `:app:lintModernDebug` → BUILD SUCCESSFUL（约 4m44s）。
  - 抽取注入脚本后 `node --check` → OK；用合成 Fake WebSocket 发送 status true/false、error、approval、question 五帧 → `JS_BRIDGE_TEST_OK 5`，原生消息未包含合成的 `secret` 工具名/错误文本/消息内容，脚本未读取 `innerText`/`textContent`/localStorage。
- 新增回归：`ChatNotificationPolicyTest` 10 项（首次 false 基线、true→false、重复 false、同/异会话前台抑制、后台、版本/会话/事件去重、旧通知未清除时新事件仍提醒、权限/渠道/用户开关、Android<33、非法 id、状态文案）；`ChatNotificationRoutingTest` 4 项（同会话跨进程稳定 tag/id、不同会话 Identity、子通知可再次提醒/摘要静默、非法 id）；`DshChatProtocolTest` 4 项（四类事件、running 补种、非法载荷、脚本只读边界与打开会话函数）。
- Review 修复回归：`node scripts/test-chat-session-open.mjs` → `CHAT_SESSION_OPEN_TEST_OK`，覆盖侧栏初始收起、保留非空搜索且当前为其他会话、通过官方清除入口解除搜索过滤、只展开一次、异步选中前不谎报成功、已选中目标与非法 id；Host CI 已新增该步骤。假 DOM 只有在搜索清除且分组展开后才为目标 id 返回会话行。
- PR #22 GitHub Actions `verify`：Host checks 与 Android build+JVM+lint 均 pass。首轮 Android 唯一失败为既有 `NpmAdaptiveWorkerPoolTest` 并发时序（非本轮代码），重跑通过；Review 修复提交 `21cc811` 与搜索过滤修复提交 `462d266` 的 Host/Android 两 job 均全绿，未修改该并发测试。
- rebase 到 `main=5fd1ab4`（含安全模式 PR #25 与 XLSX PR #23）后，在 `/tmp/tanuki-chat-notify` 复跑 `:app:compileModernDebugKotlin`、完整 `:app:testModernDebugUnitTest` 与 `:app:lintModernDebug` → BUILD SUCCESSFUL，283 tests / 0 failures / 0 errors / 0 skipped；聊天通知 18 项与安全模式 32 项、XLSX 28 项共存。
- 未执行：assemble、Android 仪器/真实设备、Android 13 权限弹窗与通知展示、group/summary/渠道设置、点击通知切会话、同会话新事件再次提醒的设备声音/振动、真实 DSH WebSocket、后台/息屏送达、MIUI/其他 ROM、DSH 升级后事件表/页面属性复核。编译/JVM/lint 不能替代设备与 ROM 验收。

## DOCX 应用内原生预览验证（2026-11-09，android_agent-31，非设备）

- 范围：把 DOCX 纯文本提取替换为 Compose 原生只读分页预览；新增 OOXML 解析（document/styles/numbering/rels/media）、样式/列表/表格/图片/页面设置、安全上限、纯 Kotlin 分页与图片策略、JVM 回归。基线 `main=2f3fac9301c7c3484df85e99091e8f63dddc0a48`，已 merge 到 `main=f5f3bf3`（含温控 PR #21、XLSX 预览 PR #23、运行时安全模式 PR #25、聊天通知 PR #22、冷启动 PR #27、PDF 阅读 PR #24 与原生界面 PR #26），分支 `ao/docx-preview/root`，PR [#28](https://github.com/leezzou/tanuki/pull/28)；不改官方 DSH UI、不引入运行时文档库。
- 实际执行：
  - `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin --console=plain` → BUILD SUCCESSFUL。
  - `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.Docx*'` → BUILD SUCCESSFUL，80 tests / 0 failures / 0 errors / 0 skipped（`DocxDocumentReaderTest` 25、`DocxSecurityTest` 22、`DocxPaginatorTest` 8、`DocxLayoutPolicyTest` 25）。
  - `./gradlew :app:testModernDebugUnitTest`（rebase 前）→ BUILD SUCCESSFUL，260 tests / 0 failures / 0 errors / 0 skipped。
  - merge `main=f5f3bf3` 后 `./gradlew :app:compileModernDebugKotlin :app:testModernDebugUnitTest` → BUILD SUCCESSFUL，494 tests / 0 failures / 0 errors / 0 skipped（main 424 - 旧 DOCX 文本测试 10 + 新增 80；测试 XML 位于 `app/build/test-results/testModernDebugUnitTest/`，不提交）；`lintModernDebug` BUILD SUCCESSFUL。
- 新增回归覆盖：样式 basedOn/docDefaults/标题回退/直接格式覆盖与 `w:b w:val="0"`、字符样式、多级编号与 `%1.%2`/bullet/样式级 numPr、表格 grid/跨列/底纹/表头/单元格多段、DrawingML 与 VML 图片、外部/缺失/穿越关系、纸张尺寸与边距、page break/pageBreakBefore、损坏/截断 zip、缺少正文、坏 XML、DOCTYPE、未定义实体、路径穿越条目、条目数/归档大小/单条目/总解压/实际解压上限、XML 深度/元素上限、文字截断、宏/页眉/脚注/嵌入对象提示，以及分页装箱、页面几何/字号比例、图片采样/显示/像素预算、单元格预算/分段、超高表格行限制。
- 本轮失败与修复：XML 本地名被统一小写导致 `pPr/sectPr/gridCol` 等失配（已改为保留 OOXML 大小写）；分页 gap 测试期望错误（已修正）；`DocxParagraph`/`DocxTable` 作为顶层类型引用错误（实际为 `DocxBlock` 嵌套类型）；早期对文本切片使用 `wrapContentHeight(unbounded = true)`，现已由“复用 `TextLayoutResult` + 按页合并绘制”替代，避免每行重新排版整段；表格单元格渲染 padding 与测量宽度统一；rebase 冲突保留 DOCX 与 XLSX 两个查看器分支，并合并 runtime 安全模式、聊天通知、冷启动与 PDF 文档，重新生成交接印章。Review P1/P2 修复：Bitmap 清理捕获 effect 快照；文本切片改为复用已测量布局并逐页合并、长段落按 8,000 字有界分段；表格列宽用 Long 求和并限制正文宽度；页首 gap 清零；渲染预算在行循环内生效并反馈截断；单元格段落共享原子预算并按 8,000 字分段，单元格内容高度限制在页面内容高度内，表格行高在创建 Compose 尺寸约束前 clamp；宽屏居中留白移到纸张宽度约束之外，页面按完整测量宽度绘制；超高单元格按已测量行边界保留可见文本前缀；极小视口框架至少容纳最小纸张宽度。
- 未执行：assemble、Android 仪器/真实设备、真实 Word 复杂样本、双指/横向滚动与深色主题、超大文档性能、低 API/真机。lint 与 JVM/编译不能替代设备与真实样本验收。

## F1 文件查看器内置预览验证（2026-11-06，非设备）

- 范围：`FileKind` PDF/DOCX/DOC 映射；PDF `PdfRenderer` 分页/缩放；DOCX `ZipFile`+`XmlPullParser` 文本提取与上限；图片/PDF 共享 `Zoomable` 的手势策略。基线 `main=cff09a3`，分支 `ao/tanuki-android-dsh/workspace-file-preview`，PR [#16](https://github.com/leezzou/tanuki/pull/16)。
- 实际执行：`source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin :app:testModernDebugUnitTest` → BUILD SUCCESSFUL，126 tests、0 failures、0 errors、0 skipped。新增 `WorkspaceFileKindTest` 2 项、`ZoomPolicyTest` 6 项、`DocxTextExtractorTest` 10 项、`PdfRenderPolicyTest` 4 项；DOCX 测试用合成 zip 覆盖多段/空段/换行/制表/标准实体/数字字符引用/CDATA、DOCTYPE 拒绝、未定义实体拒绝、文本截断、单条目与总解压超限、缺正文、损坏 zip 和坏 XML。PR #16 两轮 P2 将读取改为 `nextToken()`，使 `DOCDECL` 可被观察并拒绝，并让 replacement text 为 null 的 `ENTITY_REF` 返回中文格式错误；两项回归均通过。
- 未执行：lint、assemble、Android 仪器/真实设备、真实加密或损坏 PDF、真实复杂 DOCX、深色主题与手势实机验证。编译通过和 JVM 策略测试不能替代设备验收。

## 本轮研发治理验证（2026-11-06，W7，非产品测试）

- 用户要求治理研发规范与合并前门禁，不开发产品功能：最小修改 `AGENTS.md`，新增编排/QA/经验/代码规范文档和只读 `check-development-gates.py`，接入 Host CI，并更新状态/交接。
- 实际执行与结果：
  - `python3 scripts/test-development-gates.py` → 24 tests OK；覆盖旧 SHA、缺 review、空/排队/失败/跳过 checks、QA 漏项/错 SHA/同 session/未知 session/失败步骤/无 Android 步骤、跨仓库 PR、head 变化、纯文档豁免和有效 QA 证据。
  - `python3 scripts/check-development-gates.py --pr 12 --session android_agent-6` → exit 1（`PR 不是打开状态`）；用真实 `gh`/`ao` 只读 CLI 验证已合并 PR 被保守拒绝。
  - `python3 scripts/check-handoff.py --record …`、`python3 scripts/check-handoff.py`、`python3 scripts/check-publication.py --git` → 见 W7 交接/PR 的最终输出。
- 本轮没有构建 APK、没有运行 Gradle/JVM/lint/Android 仪器或设备测试；没有真实 open PR 的完整门禁通过样例。历史数字只作范围参考。
- W8 的只读核查与 host mock 结果记录在 [W8 报告](handoffs/20261106-1357-android-qa-readiness-audit.md)；其 `device-preflight.py` 已随 PR #13 合并为 `b2a018d`，但未在真实设备执行，不能算 Android 验收。

## W1 文档门禁验证（2026-11-06，历史记录，未在本轮复跑）

- 用户要求恢复 `docs/AGENT-STATE.md`、`docs/TEST-REPORT.md`、`docs/handoffs/CURRENT.json`，修复 `check-publication.py --git` 的 20 个断链，并新增 `docs/INTERFACES.md`；只改 docs，不改产品代码。
- 实际执行与结果：
  - `python3 scripts/check-handoff.py --record <W1记录>` → 通过，刷新 `CURRENT.json`。
  - `python3 scripts/check-handoff.py` → 退出 0（`Handoff current`）。
  - `python3 scripts/check-publication.py --git` → 0 findings；证据：修复前 20 findings，修复后 0。
  - `git diff --stat` → 只包含 `docs/` 文件。
- W1 当时没有构建 APK、没有运行 JVM/设备/lint，也没有复跑下列任何历史测试；历史数字只作范围参考。

## PR #8 浏览器输入线程修复（2026-11-05，历史记录，未在本轮复跑）

- 修复 InputConnection 在输入法后台线程回调时直接触发 WebView 输入导致的主线程断言闪退；六种被拦截输入先同步转投主线程做授权判断，主线程直接调用不排队，后台最多等待 500ms，超时/中断拒绝本次输入并取消排队判断。
- 历史记录：97 JVM 通过（0 失败/错误/跳过），lint 0 错误/71 警告/1 Hint；新增 6 项及 16 项关联设备用例通过，最终设备共 22 项（含 `AgentBrowserControlTest` 14 项与 `BrowserTabLifecycleTest` 2 项，70.441 秒），环境为 Pixel_7_API_34_arm64/emulator-5554、API34/ARM64/WebView159。
- 上述均为历史记录，未在本轮复跑；0.1.8 合并了该修复，但没有随 0.1.8 的独立设备测试报告。实现边界见[浏览器工作区](BROWSER-WORKSPACE.md)。

以下章节均为历史记录，未在 2026-11-06 本轮复跑。

## 历史：视频控制与DSH手机文件独立写许可（2026-11-05，当时debug）

- 用户最终更正为“默认给读权限，写权限需要专门给”。实现与范围见[项目存储](WORKSPACE-STORAGE.md)：真实共享卷不直接绑定Ubuntu，DSH通过受管手机文件Skill/原生RPC默认读取；原生本次写许可可撤销，停止或重启恢复只读，旧请求及异步权限回调不能借用新运行期许可。官方DSH审批/沙箱设置未变。
- 最终串行modern debug/app-test/JVM/lint构建1分53秒、86任务通过；**104 JVM，0失败/错误/跳过；lint0错误/79警告/1Hint**。另客户端13项宿主HTTP/CLI回归通过（1.041秒，零失败/取消/跳过），包含分块、版本冲突不重试覆盖、20/120秒有界超时及不输出能力凭据。
- 最终验证APK为workspacecheck、debuggable=true、0.1.7/code9、min26/target34、ARM64，14,813,669字节，SHA256 `6d2a91ff4ae1e075446c07836a61790fe982b77d220fcc4ff5cf54417fe5bf3e`，沿用开发证书 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`；配套test APK实际target独立核验后install-r。不把它当新release，已交付0.1.7六附件保持原样。
- 按方法去重，**48个不同目标设备用例最终通过，无跳过**。复用Pixel_7_API_34_arm64/emulator-5572、ARM64/API34；最终47项授权组44过/3败（137.269秒），原样3项复查2过/1败（26.135秒），无权限单项通过（0.143秒）。仅修正视频测试的滑块动作节点等待/定位及不同目标选择，app-test/lint重建1分10秒通过，产品APK SHA保持不变；视频整类3项通过（12.688秒）。两项旧UI原样复查通过，首败原因未定位；不能称首组全绿。
- 实测默认只读、独立原生确认/取消/撤销、重启失效及旧runId拒绝、提交中撤销、真实Socket延迟旧请求体、文件版本冲突保护及无系统权限拒绝；视频核对封面中心真实像素、0.5/1.5原生播放速度、真实进度跳转/边界、暂停与播放全屏的同View/同MediaPlayer及系统栏恢复、暗色窄屏大字。浏览器/DSH WebView全屏关联回归通过；DSH页面和RUNNING状态仍为合成，不是实际模型任务。安装前无node/proot/apt/dpkg/仪器或真实任务；仅用UUID共享目录/工作区与合成媒体，宿主在仪器结束后恢复MANAGE AppOp default，无卸载/清数据/wipe/快照恢复。
- 首轮测试编译因ACTION_SET_PROGRESS引用位置错误失败，修正为AccessibilityAction常量后成功。设备首轮权限11项和无权限单项通过；关联UI34项33通过/1失败：暂停后MediaPlayer返回native speed=0。修复为独立保存所选倍速、恢复播放重设参数；测试暂停时断言未播及UI选择，真实播放再核对原生速度，不改造原生getter伪造结果。随后静态发现延迟请求体跨重启可能借用新写许可，补逐请求/逐操作有效性检查与真实Socket回归。最终进度测试先前只按可见标签提交动作并稳定返回false，改为等待启用且承载SetProgress的局部节点、选择不同目标后，保留真实原生跳转断言并通过；不能据此确定原false是标签层、时序还是目标等值。未将此前测试改写成全绿。
- 本机Ubuntu和Node均不存在，`PhoneFileGuestClientTest#existingUbuntuNodeClientReadsAndRequiresNativeWriteGrant`只新增/编译，**未运行**；不自动下载环境，不计跳过为通过。未验真实Ubuntu/DSH/模型任务、真机/MIUI、低API权限弹窗、SD/USB实物、更多媒体编码及长期压力。API Key保存继续用户暂停。旧系统异步回调仅静态核对与通用runId回归，不能称API29设备验收。

实际构建与宿主命令：

```bash
./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle \
  :app:assembleModernDebug :app:assembleModernDebugAndroidTest \
  :app:testModernDebugUnitTest :app:lintModernDebug
node scripts/test-phone-client.mjs
```

最终目标设备集合（完整类名前缀`com.tanuki.`）为：PhoneFileAccessTest整类5、PhoneStorageAccessTest整类4、PhoneFileServiceTest除noSystemStorageGrantMeansNoPhoneReadOrWrite的4项；WorkspaceVideoPresentationTest整类3、WorkspaceFileManagerTest整类12、WorkspaceStorageTest整类8；FilesBrowserPresentationTest的fileEditingCopyPickerAndSelectionWorkThroughActualActivity、fileControlsRemainReachableInDarkThemeAtTwoHundredPercent、runningFilePageKeepsBrowsingTextAndTransferActionsAvailable、textViewerCanReadPastEditorLimitAndUnknownFilesOfferOpenWith、phoneLocationExplainsPermissionAndLoadsGrantedVolume、audioFileOpensWithNativePlaybackControls、largeDirectoryUsesLatestFilterAndNeverShowsPreviousDirectoryResults；BrowserWorkspacePresentationTest的fullscreenRoundTripsRestoreChromePositionWithoutReload、newBrowserPageDefaultsToWindowsAndFullscreenKeepsTheDocument、emptyBrowserCanExitFullscreenWithoutFocusingAMissingAddressField；SetupPresentationTest#actualDshWebViewSurvivesFullscreenWithoutReload。无系统授权方法在宿主恢复MANAGE AppOp default后单独运行，不在仪器中改AppOp中断自身。

## 历史：0.1.7文件管理发布候选验证（2026-11-05）

- 用户明确确认0.1.7，并要求push新分支后由用户合PR/发布。已fetch main/1024eeb（用户合入PR #6），其完整树等于原016发布分支，保留本地改动创建feature/live-file-manager-017。公开Atom/016更新清单、设备及本地最高code8，本次9；同workspacecheck开发证书，non-debuggable、ARM64、min26/target34。不是生产签名。
- 候选10,424,304字节，SHA256 `aaf92f12bdd407bfa7a9b27be6634d6308a1cc594d43e0accfc63cb0f5ab34ca`，证书 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。实际APK/测试target、版本/SDK/非debug/签名独立核验，固定副本用于测试与最终verified封装；不因推送后的AGP提交号元数据重编另一份APK。
- 独立release/app-test、release JVM/lint单次Gradle3分2秒、95任务成功；**100 JVM，0失败/错误/跳过；release lint0错误/75警告/1Hint**。另49宿主项（发布18、工作流12、公开19）全通过，分别0.576/12.011/2.416秒；不混算下节debug结果或前轮release97项。
- 复用Pixel_7_API_34_arm64/emulator-5572，旧debug code8先种合成私有标记单项通过（0.007秒），核身份后仅install-r覆盖同证书code9/app-test。宿主在仪器外暂时allow共享存储，运行下节44个文件目标，再加ApplicationUpdateNative3与ApkInstallAccess5，带verifyUpgradeMarker=true；**OK (52 tests)，66.258秒，无失败/跳过/中断**。原标记读回后清理，实际系统安装器预读/只读授权回收与provider隔离通过，没有点击系统安装器最终安装确认。结束后宿主恢复原MANAGE AppOp default，无真实任务被停止。
- 本轮release这52项首跑全过；下节debug曾失败的路径/夹具与旧环境下载失败记录均保留，不改写历史。手机卷使用自有UUID Downloads文件，SAF真实往返、视频真实播放时钟推进；RUNNING场景仍为合成状态/并发文件回调，不冒充真实DSH/模型任务。
- 本次无新增依赖，原生对应源码按固定构建来源封装。旧0.1.6六附件及其RELEASE/native清单只读复核匹配、不改写。未验真机/MIUI/低API、SD/USB实物、真实账号/模型/完整环境升级及更多媒体编码；API Key保存继续暂停。由用户自行合并与发布，不直推main/tag/上传Release。

实际release命令：

```bash
./gradlew -PtestBuildType=release -PdevelopmentReleaseSigning=true \
  -PtanukiVersionName=0.1.7 -PtanukiVersionCode=9 \
  -I scripts/workspace-verification.init.gradle \
  :app:assembleModernRelease :app:assembleModernReleaseAndroidTest \
  :app:testModernReleaseUnitTest :app:lintModernRelease
```

## 历史：运行中浏览、手机存储与多格式文件查看（2026-11-05，当时debug）

- 产品改动及实际命令归于本节与[项目存储](WORKSPACE-STORAGE.md)。独立workspacecheck、debug 0.1.6/8、min26/target34、ARM64；本次不是发布。APK 14,916,433字节，SHA256 `c386a91a682c1797f32a47235ff9301d9e51732087b4c3770f088db09a622100`。
- 本轮新跑modern debug/app-test构建、100项JVM（0失败/错误/跳过）、lint0错误/75警告/1Hint。初次provider可空返回编译失败，修正后构建通过；末次产品构建2分45秒，测试夹具收尾编译/lint1分2秒。无新增依赖；专用只读provider、权限maxSdk/legacy在实际APK核验。
- 复用Pixel_7_API_34_arm64/emulator-5572，API34，明确无真实任务。按最终方法集合去重 **44个目标设备方法最终通过**：WorkspaceFileManager12、WorkspaceStorage8、FilesystemSemantics前2个合成方法、LiveProjectTransfer10、PhoneStorageAccess3；FilesBrowserPresentation的2个原文件方法和5个新增方法、WorkspaceVideoPresentation1、FileTransferPresentation1。不含浏览器无关方法和完整guest环境安装用例；不是前轮release57项或首轮全绿。
- 首批36项有24过/9败后权限恢复导致仪器中断。8败与新增导出路径未统一Android `/data/user/0` / `/data/data`别名及派生断言有关，已修正；旧完整环境原子发布方法触发下载中断，未修/未重试、不计本轮通过。权限授予/恢复改宿主在仪器外执行后，目标35项29.905秒为34过/1项manifest夹具失败；UI9项45.710秒为7过/2项断言失败。修正目标/测试manifest混合资产解析、实际控件disabled语义及视频固定250ms假设，保持产品字节不变，仅复查3项 **OK (3 tests)，6.945秒**。视频实际isPlaying、duration、非零尺寸及播放时钟推进均断言；未确定首轮短等待未推进的根因。
- 实测5051目录读取、5001工作区ZIP、6000项UI筛选切目录、UTF8/UTF16/GB18030/Big5分页、256KiB以上文本查看、音频播放暂停、3秒合成H264原生播放、真实DocumentsUI单文件导出和导入新项目，以及授权后自建Downloads目录读/只读URI字节。导出并发追加/改写/删除/同大小同mtime替换拒绝、取消和导入失败回滚均通过。SAF运行场景使用合成service=RUNNING、无真实子进程；并发写由测试合成回调执行，不能称真实DSH模型任务验收。
- 临时MANAGE AppOp由宿主在全部仪器结束后恢复原default；合成文件由夹具清理。无卸载/清数据/快照/wipe，不碰真机或真实账号。未验低API、SD/USB、MIUI真机、更多媒体编码、长期压力/真实DSH/完整环境升级。文本搜索仅当前段，外部文档与媒体能力仍依系统/已装应用；API Key保存暂停。正式0.1.6 release附件未改写。

0.1.7旧交接1503已在后续提交前清理，必要实现/验证与首败过程归本节和上一节，边界归WORKSPACE-STORAGE/SECURITY，发布流程归RELEASE。合成3秒H.264素材随源码分发（3056字节，SHA256 `a57bbcbe20e0a9bf80b6b1912ffdc075978d51742d96a10a53ff328172431735`），不依赖.build生成器；开发并发中曾印章刷新后立即过期，冻结源文件后重新刷新，不作产品测试结果。无独有未完成事项遗留于已删除记录。

## 历史：0.1.6 合并后发布候选验证（2026-11-05）


0.1.6收尾已完成：release/0.1.6已推送b3e5d46876f15fe791f9eb60e7bd94bb07f52f41，六附件按下述已测字节封装；本轮只读复核六文件、RELEASE五项及原生清单全部匹配，旧目录未改写。2026-11-05再次fetch确认用户通过PR #6合入origin/main `1024eeb`，完整树与该发布分支HEAD相同；新功能分支从此main建立，未重复合入产品。此前1632/1009/1134/1118记录的误代合流程、015封装、PR4/5失败/复跑、宽屏和未验边界均已归本报告及发布/产物专题；1204旧发布交接在本轮提交前据此清理。
- 用户自行合入PR #5后，主目录main快进到 `f1fb10296df72cda6fc728d218774ddad6dfcbd4`；其完整tree `74ba29a9c227913097a3af3a9efa718599687b31` 与已测head `862e510` 相同。本轮仅更新0.1.6/code8、应用下节已验证的外链QA宿主夹具并整理文档，没有新增产品逻辑或依赖。用户授权全部适用验证收尾后统一一次push，再封装同一已测APK；无需重建PR5功能分支。
- 本轮新跑：modern release与配套release AndroidTest、compat debug编译、release JVM/lint单次Gradle **4分38秒、134任务成功**；**JVM97，0失败/错误/跳过；release lint0错误、71警告、1Hint**。宿主发布18项、交接工作流12项、公开边界19项均通过（分别1.236/11.894/3.046秒），共49个不同宿主用例。不得混算为设备测试。
- 复用 `Pixel_7_API_34_arm64/emulator-5572`，ARM64/API34/WebView159，已确认专用合成数据且无node/proot/apt/dpkg/真实任务。核实app/test身份、target和证书后仅同包覆盖；不卸载、清数据、wipe或恢复快照。已安装及公开可发现清单最高code7，旧011/010无清单，结合既有实际code3/1与tag核查，选择递增code8。
- 实际候选为 `com.tanuki.modern.workspacecheck`、**0.1.6/code8**、开发证书、**non-debuggable release**、ARM64、minSdk26/targetSdk34；**10,375,252字节**，SHA-256 **`802d1f49db4f56d2cc737cf95242fcd86ab6abebe7f0a5acf1a559d97147138e`**，证书SHA-256 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。后续verified模式只封装这些字节，不因Git提交元数据重编另一份包。
- release核心6项全部通过（124.278秒），选择与下节PR5 focused完全一致，含真实30秒临时交回、再等31秒显式撤销保持、普通当前页撤权可读、私有页阻断、4页中央控制、真实手势/取消及RTL200%。关联51项首轮 **49通过、2失败**：`SetupPresentationTest#filesListExtendsBehindNavigationAndScrollsTailClearWithoutReloadingDsh` 与 `#fixedBottomDshControlsAcceptRealTouchesWithoutChangingNavigation` 均为合成HTML marker载入超时；保持产品APK和测试源码/测试APK原样，仅选这两项独立复跑，**2项通过**（JUnit23.34秒，宿主24.313秒）。未找到可证明的产品或夹具根因，不能宣称修复了超时。首轮JUnit3266.997秒与宿主344.843秒存在时钟差异，仅作观察，不据此确定归因。按方法集合去重，**57个不同release设备用例最终通过，无跳过**，不是首组全部通过，也没有把旧debug通过冒充本次结果。
- 关联51的完整选择为下节PR5 related43项，再加 `ApplicationUpdateNativeTest` 3项及 `ApkInstallAccessTest` 5项（如下）。旧code7先显式种入一个私有合成升级标记（1项通过、1.584秒），随后同证书覆盖code8；关联组以 `-e verifyUpgradeMarker true` 验证原标记仍在并清理，不能把单纯新包内写读当覆盖升级。实际系统安装器预读、URI授权/回收、两provider隔离、文件选择ClipData及系统SAF回传均通过；没有点击系统安装器最终确认来重装或卸载。
- 同一release再以 **2400x1800/420dpi** 跑4页中央控制单项，14.673秒通过；两张实际截图尺寸均为2400x1800，真实宽屏侧栏布局。`finally`已核实还原 **1080x2400/420dpi**。此重复配置不增加57个不同方法计数。
- APK独立核验通过：实际签名、版本/SDK/ABI、4个AArch64 ELF、更新与浏览器两个非导出FileProvider的不同组件/authority/路径及DEX继承；62份许可资产与源码逐字节一致，79依赖记录/78坐标、Kyant2 NOTICE与共享Apache内容齐全。固定APK公开扫描403成员、0发现。最终源码及实际index须在文档冻结后重新扫描、重打包并核外部散列。
- 未验：MIUI和其他真机/低API、真实账号OAuth/模型/DSH任务、完整运行环境升级、长期复杂页压力及private/notice全状态矩阵。原始用户图标滞后未全面重现；旧RTL200%轻微折射现象不能称视觉无瑕。API Key保存继续用户暂停。compat仅编译，不称旧Android设备通过。

本轮实际构建命令（同一目录串行，JDK17/SDK36）：

```bash
./gradlew -PtestBuildType=release -PdevelopmentReleaseSigning=true \
  -PtanukiVersionName=0.1.6 -PtanukiVersionCode=8 \
  -I scripts/workspace-verification.init.gradle \
  :app:assembleModernRelease :app:assembleCompatDebug :app:assembleModernReleaseAndroidTest \
  :app:testModernReleaseUnitTest :app:lintModernRelease
python3 scripts/test-app-release.py
python3 scripts/test-agent-workflow.py
python3 scripts/test-publication.py
adb -s emulator-5572 install -r <核对身份后的release APK>
adb -s emulator-5572 install -r <配套release AndroidTest APK>
adb -s emulator-5572 shell am instrument -w -r -e class <上述方法选择> \
  -e verifyUpgradeMarker true \
  com.tanuki.modern.workspacecheck.test/androidx.test.runner.AndroidJUnitRunner
```

补充的8项完整类名前缀为 `com.tanuki.`：

```text
ApplicationUpdateNativeTest#androidArchiveAndInstalledSignerMatchActualPublishedFields
ApplicationUpdateNativeTest#privateSyntheticFileSurvivesCoverInstall
ApplicationUpdateNativeTest#androidCacheRootIsAcceptedButOwnUpdateLinkIsNotNeeded
ApkInstallAccessTest#installerCanReadBeforeActivityStartsButMatchingUserAppCannot
ApkInstallAccessTest#plainViewFlagAloneDoesNotGrantInstallerPreRead
ApkInstallAccessTest#nextApkRevokesThePreviousFileAndExpiryRevokesTheCurrentFile
ApkInstallAccessTest#foreignAndBrowserProviderUrisAreRejected
ApkInstallAccessTest#actualInstallerReadsApkMetadataBeforeUserConfirmation
```

## 历史：PR #5 中央控制导航独立验证（2026-11-05，验证时尚未合并）

- 用户本次明确要求测试PR5，上一轮“不动PR5”的范围限制由新请求解除；仍只测试并告知，**由用户合并，不提交/推送/发版**。固定[PR #5](https://github.com/leezzou/tanuki/pull/5) head `862e510c87e5334f58b96778a9d8483d5bd3fa7f`；merge-base最新main `8b5776adcd8a4043398ac9271e1b6ce384c7c31a`，ahead6/behind0。用户已squash合PR4为8b5776a，其完整tree与已测4a7bc31相同；独立detached工作树测试，主目录local main4509972及未提交文档保留，不整合产品。
- PR改动：共享中央人/AI按钮，点击明确切换操作权；拖动不切权、镜片按住连续跟手，松手才跳过中央吸附，取消回原页；移除重复常态控制条，待处理/登录/临时接管提示、小窗本地入口保留。原生当前连接可跨页面显式授权，RPC仍不能授权；普通当前页无操作权可读、读取不切页、OAuth/private屏蔽保持。旧30秒已交回提示只在当前权限失效时清除，其他通知保留。
- PR产品/测试原样构建 **5分9秒、125任务成功**，modern/compat debug及modern AndroidTest；**JVM97，0失败/错误/跳过；modern debug lint0错误/71警告/1Hint**。原生固定源码/补丁在测试树由构建脚本准备/编译，未改第三方源码来消除其警告。主构建/核心组/关联首跑均为原样产品和测试；随后仅修正旧外链测试宿主，产品未改，不混算作者API36结果、PR4的34项或旧0.1.5的57项。
- 确认既有Pixel_7_API_34_arm64/emulator-5572、ARM64/API34/WebView159，无node/proot/apt/dpkg/仪器或真实任务；核app/test身份、target与开发证书后仅覆盖安装，不清数据/卸载/快照。候选 `com.tanuki.modern.workspacecheck`、0.1.5/code7、debuggable=true、min26/target34，**14,112,591字节**，SHA-256 `2e6bd70c773540bcb0081b663a280d78d647e0e47ce361846edaad4f56a132c9`，开发证书 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。这是测试debug包，不是新release。
- 核心 **6项全部通过，132.802秒**：真实触摸点击与拖动、松手吸附/取消、RTL/200%、跨4页不切页不重载、真实30秒交回图标和31秒显式撤销不恢复、旧提示失效及其他提示保留、私有登录页禁用。关联43项首跑42通过/1失败365.577秒。唯一外链真实点击超时在同PR5原样单例再现27.225秒，已合PR4等价基线同点失败24.818秒；不是只凭旧记录排除回归。实拍/诊断显示裸WebView旧触点y=96.84位于系统栏top=136之内。仅给测试FrameLayout补systemBars insets、可见/焦点/首帧与正确真触摸，保留全部原跳转/隔离/epoch断言；测试包/lint重编57秒，认证整类2项通过23.082秒，产品APK SHA不变。修正后触点y=232.83位于安全区，DOM pointerdown/up/click均trusted，forwarded=1，后续5跳转及Cookie恢复/有界失败全部通过。按方法集合去重 **49个不同用例最终全部通过**，无跳过/host timeout/断线；原始失败不抹去。另同方法宽屏2400x1800/420复测通过15.847秒，截图确认为真实左侧栏，显示已finally还原1080x2400/420。
- 13张合成图复核：正常布局8图、宽屏2图、全屏/恢复/键盘3图；人/机器人与中央镜片实际绘制，侧栏确实是>=840dp布局，IME/全屏隐藏主导航后退出恢复。RTL/320dp/200%选中DSH文字两侧有轻微窄竖折射重影，未见功能遮挡、无同配置基线对照，未定为新增回归，不宣称视觉完全无瑕。原图不公开。
- QA修补保存在[外链测试夹具](../app/src/androidTest/kotlin/com/tanuki/WebAuthenticationTest.kt)，仅测试宿主，不修改产品来源/手势/隐私校验；验证当时只存在于独立测试树。用户已于12:04确认合并，main f1fb102完整tree与862e510相同；本轮已同步并将该夹具应用到主目录，后续0.1.6另跑release验证后一次push再封装。版本已确认，不再询问。
- 静态未见权限边界阻塞；仍须以真实30+31秒的图标像素/语义/权限/提示回归判断本机表现，不将作者记录的原始图标滞后未复现自动称作全面修复。private前已有AI权的mask、notice在private/关闭/渲染失败分支、中央撤销后重建同链未独立新增用例，不能扩称全部状态矩阵已测。
- 真机/低API/真实模型与DSH任务、长期复杂页面压力、完整运行环境升级未验，API Key保存继续暂停。

实际命令（独立目录、JDK17/SDK36）：

```bash
./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle \
  :app:assembleModernDebug :app:assembleCompatDebug :app:assembleModernDebugAndroidTest \
  :app:testModernDebugUnitTest :app:lintModernDebug
adb -s emulator-5572 install -r <身份核对后的app.apk>
adb -s emulator-5572 install -r <配套AndroidTest.apk>
adb -s emulator-5572 shell am instrument -w -r -e class <以下逗号分隔选择> \
  com.tanuki.modern.workspacecheck.test/androidx.test.runner.AndroidJUnitRunner
```

以下完整类名前缀为 `com.tanuki.`，整类覆盖与单方法选择均按实际运行去重计数：

### focused（6项）

```text
SetupPresentationTest#browserControlWithoutGlassRejectsDragButAcceptsClick
SetupPresentationTest#glassCenterOnlyClicksAndDragsSnapPastTheAction
SetupPresentationTest#glassCenterSkipsRtlGapAtLargeFontsAndCancelsVerticalPress
BrowserWorkspacePresentationTest#navigationControlTogglesFromEveryPageWithoutSwitchingOrReloading
BrowserWorkspacePresentationTest#navigationControlTracksRealIdleReturnAndExplicitRevoke
BrowserWorkspacePresentationTest#navigationControlCannotGrantOnPrivateLoginPage
```

### related（43项）

```text
AgentBrowserControlTest（整类14项）
BrowserWorkspacePresentationTest#fullscreenRoundTripsRestoreChromePositionWithoutReload
BrowserWorkspacePresentationTest#newBrowserPageDefaultsToWindowsAndFullscreenKeepsTheDocument
BrowserWorkspacePresentationTest#emptyBrowserCanExitFullscreenWithoutFocusingAMissingAddressField
BrowserWorkspacePresentationTest#optionalBottomAvoidanceKeepsFixedControlsClickableWithoutReload
BrowserWorkspacePresentationTest#compactChromeSharesTitleRowAndWebPixelsShowBehindNavigation
BrowserWorkspacePresentationTest#revokedAiKeepsDshBrowserEntryAndUserPageVisible
BrowserWorkspacePresentationTest#defaultConnectionAndTouchTakeoverSurviveBackgroundAndRecreation
SetupPresentationTest#actualDshWebViewSurvivesFullscreenWithoutReload
SetupPresentationTest#fixedBottomDshControlsAcceptRealTouchesWithoutChangingNavigation
SetupPresentationTest#filesListExtendsBehindNavigationAndScrollsTailClearWithoutReloadingDsh
SetupPresentationTest#glassNavigationBlursPixelsAndKeepsTabsClickable
SetupPresentationTest#glassNavigationDragPreviewsThenCommitsOnceAndCancels
SetupPresentationTest#glassNavigationNullBackdropDragsInRtlAndKeepsLargeTabsClickable
SetupPresentationTest#setupGuideFirstStepSourceVersionAndLocalThemeScreenshots
FilesBrowserPresentationTest（整类5项）
BrowserOverlayPresentationTest#fullDocumentAndAiZoomAreIndependentOfSmallWindow
BrowserOverlayPresentationTest#revokedAiCanStillOpenAndViewBrowserOverlay
BrowserOverlayPresentationTest#keyboardTouchTakeoverAndSystemRevocationRemainUsable
BrowserOverlayPresentationTest#windowSurvivesHomeMovesAndResizesWithoutReloadAndReturnsToBrowser
DshFileUploadTest#clipDataOnlyResultDeliversAllFilesThroughMainActivity
DshFileUploadTest#systemDocumentPickerReturnsChosenBytesThroughMainActivity
WebAuthenticationTest（整类2项）
DshViewportTest#logicalWidthMakesTheWholeDshPageCompactWithoutReloadOrSystemChanges
ApplicationUpdatePresentationTest#checkActionIsReachableAt320dpWithLargeFontsInBothThemes
```

## 历史：PR #4 fix 独立验证（2026-11-05，验证时尚未合并）

- 用户只要求测fix PR，正常后由用户亲自合并，**不合并、不推送、不动PR #5、不发版**。固定[PR #4](https://github.com/leezzou/tanuki/pull/4) head `4a7bc3133f664272b16f66023a00f1dd126e8cf3`；merge-base为最新main `4509972ff34d1a2c2da9b0469dca8760c71c5233`，ahead2/behind0。列表页面缓存仍返回PR #3，独立PR页面与实时Git refs确认目标；仅fetch main/PR4，独立detached工作树测试，主目录产品代码不整合。
- PR产品只改MainActivity、DshWindowMode、BrowserLibrary、AgentBrowserPage。主窗口统一edge-to-edge，Compose消费insets，全屏只切系统栏和刘海策略；浏览器默认恢复网页透显，用户可在「浏览器设置→通用→避让网页底部控件」开启安全视口，设置持久保存；DSH原有底部保护保持。默认透显不承诺网页fixed底栏可点击，不能把开启后的真实触摸结果用于宣称默认模式也避让。
- 原样PR构建 **5分47秒成功（125任务）**：modern/compat debug、modern AndroidTest、**97 JVM（0失败/错误/跳过）**，modern debug lint **0错误/71警告/1Hint**。本轮未改产品/测试代码；作者API36/15项与旧0.1.5/57项均是历史，不能算本轮。
- 实际检查app/test包名与instrumentation target、证书后，同包同证书覆盖专用 **Pixel_7_API_34_arm64 / emulator-5572**，API34/ARM64/WebView159；安装前无node/proot/apt/dpkg/仪器或真实任务，不清数据/卸载/恢复快照。候选 `com.tanuki.modern.workspacecheck`、0.1.5/code7、**debuggable=true**、min26/target34，**14,104,579字节**，SHA-256 `1fc1b905f18402b2a76b6a1e7dc4cc8734274fe6e3ff5913dfa5fa9731127171`；开发证书 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。这是验证debug包，不是新release，不覆盖历史发布附件。
- 设备重点 **11项全部通过，126.301秒**：三次全屏往返（按钮/系统返回）后地址栏顶/网页顶/高度严格恢复，WebView/timeOrigin/草稿保持；旧全屏方法改为真实status/nav分别隐藏并完整恢复初始栏状态，本机通过，没有继续排除旧方法。caption/combined逻辑纠正与产品顶部留白修复是两个问题，未把修改断言冒充产品修复。默认透显实际像素、开关后底部真实点击/同文档、DSH全屏/底部控件、地址/暗色200%及整页尾部/AI缩放/小窗均通过。
- 关联 **23项全部通过，175.755秒**，与重点组合计 **34个不同设备用例全部通过**；无失败/跳过/host timeout/ADB断线/仪器中断。包括上传5、AI只读/不切页/私有页3、接管/重建与撤权后可见2、原生glass3/文件尾部/工作台5、文件3、悬浮窗3、viewport1、更新页1。
- 8张本轮合成截图独立视觉复核通过：默认网页透显、避让后底部控件完整露出、全屏满幅及退出无重复留白、DSH前后边界相同、键盘时导航收起且无残留空白、暗色200%控件可见；只支持本机这些帧，原图留忽略目录，不公开设备原始资料。
- 环境准备首次误假设主目录有local.properties，复制中断，测试树缺当前STATE链接导致交接拒绝；日志目录也未建立，首次Gradle未启动。显式写测试树SDK配置、复制记录/STATE并刷新守卫后重启成功，不将该准备失败计产品失败/通过。
- 未验：MIUI真机、低API/桌面caption/宽屏旋转、真实账号/模型/DSH任务键盘、长期后台/复杂页面压力与完整Ubuntu/Node/DSH跨APK升级。API Key保存继续暂停。偏好旧JSON缺字段与AI状态保持本轮静态核对，设备只测现有默认false/true重开及UI开关，不能扩称整个迁移矩阵通过。

实际命令（设置JDK17/SDK36后）：

```bash
./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle \
  :app:assembleModernDebug :app:assembleCompatDebug :app:assembleModernDebugAndroidTest \
  :app:testModernDebugUnitTest :app:lintModernDebug
adb -s emulator-5572 install -r <核对身份的app.apk>
adb -s emulator-5572 install -r <配套AndroidTest.apk>
adb -s emulator-5572 shell am instrument -w -r -e class <下列逗号分隔选择> \
  com.tanuki.modern.workspacecheck.test/androidx.test.runner.AndroidJUnitRunner
```

以下方法完整类名前缀为 `com.tanuki.`，分11与23两组，单一设备串行执行：

### focused（11项）

```text
BrowserLibraryTest#nativeLibraryAndPagePreferencesPersistWithoutAnyPermission
BrowserWorkspacePresentationTest#compactChromeSharesTitleRowAndWebPixelsShowBehindNavigation
BrowserWorkspacePresentationTest#optionalBottomAvoidanceKeepsFixedControlsClickableWithoutReload
BrowserWorkspacePresentationTest#fullscreenRoundTripsRestoreChromePositionWithoutReload
BrowserWorkspacePresentationTest#newBrowserPageDefaultsToWindowsAndFullscreenKeepsTheDocument
BrowserWorkspacePresentationTest#emptyBrowserCanExitFullscreenWithoutFocusingAMissingAddressField
SetupPresentationTest#actualDshWebViewSurvivesFullscreenWithoutReload
SetupPresentationTest#fixedBottomDshControlsAcceptRealTouchesWithoutChangingNavigation
FilesBrowserPresentationTest#addressBarIsActuallyPaintedAndRealTouchCanNavigateBeforeAndAfterPageLoad
FilesBrowserPresentationTest#browserAddressRemainsVisibleAndTouchableInDarkThemeAtTwoHundredPercent
BrowserOverlayPresentationTest#fullDocumentAndAiZoomAreIndependentOfSmallWindow
```

### related（23项）

```text
AgentBrowserControlTest#canonicalReadSurvivesHumanControlWhileMutationsStayDenied
AgentBrowserControlTest#readWithTabIdCannotSwitchTheUsersPage
AgentBrowserControlTest#privateLoginIsHiddenFromReadAndControlEvenWhenGranted
BrowserWorkspacePresentationTest#defaultConnectionAndTouchTakeoverSurviveBackgroundAndRecreation
BrowserWorkspacePresentationTest#revokedAiKeepsDshBrowserEntryAndUserPageVisible
SetupPresentationTest#glassNavigationBlursPixelsAndKeepsTabsClickable
SetupPresentationTest#glassNavigationDragPreviewsThenCommitsOnceAndCancels
SetupPresentationTest#glassNavigationNullBackdropDragsInRtlAndKeepsLargeTabsClickable
SetupPresentationTest#filesListExtendsBehindNavigationAndScrollsTailClearWithoutReloadingDsh
SetupPresentationTest#setupGuideFirstStepSourceVersionAndLocalThemeScreenshots
FilesBrowserPresentationTest#fileControlsRemainReachableInDarkThemeAtTwoHundredPercent
FilesBrowserPresentationTest#fileEditingCopyPickerAndSelectionWorkThroughActualActivity
FilesBrowserPresentationTest#canonicalGrantAndTabExitKeepControlUntilExplicitTakeback
BrowserOverlayPresentationTest#windowSurvivesHomeMovesAndResizesWithoutReloadAndReturnsToBrowser
BrowserOverlayPresentationTest#revokedAiCanStillOpenAndViewBrowserOverlay
BrowserOverlayPresentationTest#displayDensityKeepsCollapsedControlsReachableAndNotificationStopIsFinal
DshViewportTest#logicalWidthMakesTheWholeDshPageCompactWithoutReloadOrSystemChanges
DshFileUploadTest#selectedContentFilesReachDomAndWorkerUpload
DshFileUploadTest#clipDataOnlyResultDeliversAllFilesThroughMainActivity
DshFileUploadTest#systemDocumentPickerReturnsChosenBytesThroughMainActivity
DshFileUploadTest#cancelledAndNavigatedRequestsCannotDeliverStaleFiles
DshFileUploadTest#foreignPagesAndPrivateFileUrisAreRejected
ApplicationUpdatePresentationTest#checkActionIsReachableAt320dpWithLargeFontsInBothThemes
```


## 历史：合入PR #4后的main（2026-11-05）

- 将main/8b5776a正常合入feat/navigation-browser-control/452aab1；main完整源码树与本分支祖先4a7bc31相同，但squash后共同祖先仍4509972，造成一份设备测试与AGENT-STATE/TEST-REPORT/CURRENT.json共4处冲突。
- 基于完整树相等证据保留现有功能/回归，核对所有产品、测试、构建脚本、依赖与许可逐字节等于452aab1，未丢松手吸附或提示修正。清理合并自动重引入的1911旧交接及已完成1113记录，必要历史与未验范围仍在本报告和当前状态。
- 本轮宿主工作流12项、公开边界19项通过；交接/源码SHA/候选与索引检查另做。只有合并及文档变化，没有重跑Android构建、JVM或设备测试；下列97/11仍属上一实现轮，不冒充本轮验证。

## 历史：30秒交回与状态提示一致性（2026-11-05，独立debug）

- 先按用户要求独立本地提交松手吸附为4ad6661。随后新回归真实等待临时接管30秒：旧版实际授权、按钮语义和机器人像素均恢复，**未复现自动交回后图标仍为人**；复现的是再次点击收回后仍显示旧“30秒未操作，浏览器权限已交回AI”提示，和当前人工状态矛盾。
- AgentBrowserController集中更新状态时，仅清除失效的自动交回提示；无操作权、私有页、关闭或渲染失败均不展示该结论。保留有效交回提示、其他通知与原授权策略，不新增UI轮询/强刷或提前授权。
- `BrowserWorkspacePresentationTest#navigationControlTracksRealIdleReturnAndExplicitRevoke`先失败于过期提示断言；修改后真实30秒交回及另等31秒确认显式撤权不恢复均通过，同时对照图标像素和语义、验证下载类通知不被误清。补夹具通知恢复后重跑通过；再将等待阶段切至工作台原生玻璃页，授权/语义/图标也正确，返回浏览器继续撤权断言通过，重复不累计。额外测试只安装固定已测APK，未用切回隔离构建后字节不同的重编译包替换附件。
- 本轮双debug/modern AndroidTest、**97 JVM无失败/跳过，modern lint0错误/71警告/1提示**；Tanuki_UI_API36/emulator-5554、ARM64/API36/WebView133、独立uipolish.workspacecheck：**11个不同设备用例最终通过**。实际包/版本/签名/target核对后覆盖，不清数据或安装正常包。新用例约70秒，不缩短安全计时。
- 其余10项：BrowserWorkspacePresentationTest的`navigationControlTogglesFromEveryPageWithoutSwitchingOrReloading`、`navigationControlCannotGrantOnPrivateLoginPage`、`defaultConnectionAndTouchTakeoverSurviveBackgroundAndRecreation`；AgentBrowserControlTest的`idleThirtySecondsReturnsTemporaryControlButNeverExplicitRevoke`、`canonicalReadSurvivesHumanControlWhileMutationsStayDenied`、`privateLoginIsHiddenFromReadAndControlEvenWhenGranted`、`proactiveNativeGrantIsForKnownConnectionAndCannotBeInvokedByRpc`；SetupPresentationTest的`glassCenterOnlyClicksAndDragsSnapPastTheAction`、`glassCenterSkipsRtlGapAtLargeFontsAndCancelsVerticalPress`；BrowserOverlayPresentationTest的`revokedAiCanStillOpenAndViewBrowserOverlay`（以上类前缀com.tanuki.）。
- 小窗回归首跑误找已移除的主页面撤权按钮/DSH状态条，修改为中央按钮且保留撤权断言后通过；测试适配及清理重跑时产品APK字节未变。不把该测试失败算新产品缺陷。
- 副本在artifacts/builds/navigation-control-sync，包含松手吸附；后续提交/推送状态见AGENT-STATE。真机/低API/完整宽屏及用户原始图标滞后场景仍未确认，不能将已复现的提示矛盾说成重现了所有图标问题；API Key保存暂停。

## 历史：中央镜片仅松手吸附（2026-11-05，独立debug）

- 用户指出此前理解错误：按住时应能停在中央，松手才避让。GlassNavigationBar移除拖动中的吸附状态，预览保持原始指针位置；沿用释放时最近侧判定及弹簧，不改按钮、权限或WebView。
- SetupPresentationTest在同一按压中对照侧栏位/中央区域实际像素，验证按住时不提前吸附，同时保留松手位置、取消、精确回调/控制点击次数。旧版LTR先失败`changed=0`；首轮RTL通过，随后统一将像素差阈值强化为中央区域5%，不把此前未触发的RTL计为缺陷复现。
- 本轮双debug/modern AndroidTest、**97 JVM无失败/跳过，lint0错误/71警告/1提示**。复用Tanuki_UI_API36/emulator-5554、ARM64/API36/WebView133及独立uipolish.workspacecheck包，核对版本/签名/target后覆盖；**11项设备专项通过，162.2秒**，无跳过/超时。
- 实际选择（类前缀com.tanuki.）：SetupPresentationTest的`glassCenterOnlyClicksAndDragsSnapPastTheAction`、`glassCenterSkipsRtlGapAtLargeFontsAndCancelsVerticalPress`、`browserControlWithoutGlassRejectsDragButAcceptsClick`、`glassNavigationDragPreviewsThenCommitsOnceAndCancels`、`glassNavigationNullBackdropDragsInRtlAndKeepsLargeTabsClickable`、`glassNavigationBlursPixelsAndKeepsTabsClickable`、`actualDshWebViewSurvivesFullscreenWithoutReload`；BrowserWorkspacePresentationTest的`navigationControlTogglesFromEveryPageWithoutSwitchingOrReloading`、`navigationControlCannotGrantOnPrivateLoginPage`、`fullscreenRoundTripsRestoreChromePositionWithoutReload`、`optionalBottomAvoidanceKeepsFixedControlsClickableWithoutReload`。
- 独立debug副本在artifacts/builds/navigation-release-snap，不覆盖旧d1a6989验证包或正式release。未重跑前轮全部25项，不宣称真机/低API/完整宽屏或真实模型验收；本轮未提交/推送。

## 历史：导航中央控制（2026-11-04，独立debug）

- `feat/navigation-browser-control`基于修复分支4a7bc31，保留悬浮与全屏修复。主导航中央/侧栏中部人、机器人按钮统一操作权状态；普通状态条移除，登录保护、请求/下载、接管询问及独立小窗入口保留。
- 新回归先复现：后台原生授权仍受可见性限制、工作区无中央控制入口。改为显式原生点击使用已有当前连接授权，不开放RPC授权、不改变普通页只读/私有页阻断。拖动采用真实DOWN区分中央起点，镜片吸附最近有效页签，不触发控制动作。
- 审阅发现侧栏直接按钮缺少底栏父级的拖动取消保护；独立按钮真实MotionEvent先失败（应0次、实际1次）。将取消保护移到共用按钮后通过，未复制另一套侧栏逻辑。后续补按键测试曾缺KeyEvent导入，已补齐，不把编译失败计为通过。
- 最终双debug/modern AndroidTest构建、**97 JVM无失败/跳过，modern lint0错误/71警告/1提示**。Tanuki_UI_API36/emulator-5554、ARM64/API36/WebView133，独立`com.tanuki.modern.uipolish.workspacecheck`：最终APK按下列方法去重，**25项通过**；包含像素定位/真实触摸、中央起拖/跨越/取消/左右吸附、RTL/320dp/200%字体、合成Tab/Enter与无障碍点击、四页实际授权切换、私有页禁用、文档/草稿保留及旧全屏/避让回归。
- 最终APK先跑新增/强化5项通过；关联20项首跑19通过，旧独立控制器只读用例的首次截图缺少result。失败定位于截图读取而非授权断言；测试增加WebView可视帧同步及错误码断言，随后连续3次通过，产品APK字节未变。不以缺少result日志推断具体RPC错误码，重复次数不累计为新增用例。
- 核对版本0.1.5/code7、包名、instrumentation target及与前一独立包相同开发签名后覆盖测试包，无清数据/正常安装覆盖。副本在`artifacts/builds/navigation-control`；本轮没有正式签名发布、提交或推送。真机/低API、完整宽屏Activity、真实账号/模型未验；独立按钮手势测试不冒充宽屏整页验收，API Key保存仍暂停。

公开源码中的实际选择如下（前缀`com.tanuki.`），可用`adb -s <已确认的合成设备> shell am instrument -w -r -e class <类#方法> <实际test包>/androidx.test.runner.AndroidJUnitRunner`逐项执行。独立init与公开`workspace-verification.init.gradle`仅debug后缀不同；构建目标为`assembleModernDebug assembleCompatDebug assembleModernDebugAndroidTest testModernDebugUnitTest lintModernDebug`。

```text
SetupPresentationTest#browserControlWithoutGlassRejectsDragButAcceptsClick
SetupPresentationTest#glassCenterOnlyClicksAndDragsSnapPastTheAction
SetupPresentationTest#glassCenterSkipsRtlGapAtLargeFontsAndCancelsVerticalPress
BrowserWorkspacePresentationTest#navigationControlTogglesFromEveryPageWithoutSwitchingOrReloading
BrowserWorkspacePresentationTest#navigationControlCannotGrantOnPrivateLoginPage
AgentBrowserControlTest#proactiveNativeGrantIsForKnownConnectionAndCannotBeInvokedByRpc
AgentBrowserControlTest#visibilityAndTouchNeitherGrantNorRevokeExplicitPermission
AgentBrowserControlTest#canonicalReadSurvivesHumanControlWhileMutationsStayDenied
AgentBrowserControlTest#privateLoginIsHiddenFromReadAndControlEvenWhenGranted
AgentBrowserControlTest#idleThirtySecondsReturnsTemporaryControlButNeverExplicitRevoke
BrowserWorkspacePresentationTest#revokedAiKeepsDshBrowserEntryAndUserPageVisible
BrowserWorkspacePresentationTest#defaultConnectionAndTouchTakeoverSurviveBackgroundAndRecreation
BrowserWorkspacePresentationTest#compactChromeSharesTitleRowAndWebPixelsShowBehindNavigation
BrowserWorkspacePresentationTest#optionalBottomAvoidanceKeepsFixedControlsClickableWithoutReload
BrowserWorkspacePresentationTest#fullscreenRoundTripsRestoreChromePositionWithoutReload
BrowserWorkspacePresentationTest#newBrowserPageDefaultsToWindowsAndFullscreenKeepsTheDocument
FilesBrowserPresentationTest#canonicalGrantAndTabExitKeepControlUntilExplicitTakeback
FilesBrowserPresentationTest#addressBarIsActuallyPaintedAndRealTouchCanNavigateBeforeAndAfterPageLoad
FilesBrowserPresentationTest#browserAddressRemainsVisibleAndTouchableInDarkThemeAtTwoHundredPercent
SetupPresentationTest#actualDshWebViewSurvivesFullscreenWithoutReload
SetupPresentationTest#fixedBottomDshControlsAcceptRealTouchesWithoutChangingNavigation
SetupPresentationTest#filesListExtendsBehindNavigationAndScrollsTailClearWithoutReloadingDsh
SetupPresentationTest#glassNavigationDragPreviewsThenCommitsOnceAndCancels
SetupPresentationTest#glassNavigationNullBackdropDragsInRtlAndKeepsLargeTabsClickable
BrowserOverlayPresentationTest#fullDocumentAndAiZoomAreIndependentOfSmallWindow
```

## 历史：全屏往返与悬浮修复（2026-11-04，独立debug）

- 悬浮修复cbe9e9c：浏览器默认恢复透显，可在通用设置主动开启/保存网页底部控件避让，DSH保护不变。此前双debug/AndroidTest、97 JVM、12项API36专项通过（97.5秒），lint0/71/1；避让项补网页可视帧同步另过，不重复计数。原生宿主不裁切实验仍无法透显，已撤回；不存在的图标编译错误改为复用已有图标。旧过程交接归档删除。
- 新增全屏往返坐标回归先失败：地址/网页顶部由139/528变为267/656，重复128px状态栏占位。MainActivity在创建时统一edge-to-edge，DshWindowMode不再来回切换decorFits，占位只由Compose处理；不使用负padding、不重建WebView。
- 旧systemBars断言在API36另有误判：初始和退出均为 `[status=true, navigation=true, caption=false, combined=false]`。手机没有窗口caption，不能要求组合systemBars全部可见；现明确要求状态/导航栏进入全屏都隐藏，退出恢复完整初始可见性。新坐标断言独立验证产品缺陷，没有用测试修改掩盖重复占位。
- 本轮新跑双debug/AndroidTest、**97 JVM无失败/跳过，modern lint0错误/71警告/1提示**；Tanuki_UI_API36/emulator-5554、WebView133、独立uipolish.workspacecheck包：**13项UI专项全过137.4秒，2项ClipData/系统SAF上传全过8.3秒，共15个不同用例**。往返用例增加原生栏可见等待，避免通用查找器在过渡帧滚动网页，随后2项复跑通过，不累计。
- 覆盖3次按钮/系统返回往返、地址/网页坐标及高度、状态栏/导航栏、同WebView/草稿/timeOrigin，DSH边界与退出绘制、空页、透明像素、可选避让、文件列表/玻璃、地址栏/暗色200%、后台重建、完整截图/AI缩放/小窗和上传。正常开发安装未覆盖；真机/低API与真实DSH任务未验，不将API36结果扩大为所有平台。实现决策见[浏览器工作区](BROWSER-WORKSPACE.md)。

## 历史：v0.1.5/7 PR #3 release 验证（2026-11-04）

- 流程回顾：Agent当时误把push理解为直推main4509972，越过用户手动合并PR3步骤，已告知/致歉，没有擅自回退/强推。PR4/5随后均由用户自行合并；本轮0.1.6的同步/一次push是在用户确认合并后按新授权执行。015当时最终工作树/索引各400、源码402项公开检查0发现，SOURCE-SHA和旧014清单通过，push后verified封装六附件（原生19.6秒、源码0.6秒）；后续仅改流程文档/源码副本，不重编APK。

- 用户明确测试正常后push并打包0.1.5。固定[PR #3](https://github.com/leezzou/tanuki/pull/3) head `c95fa9fcc80a78489a1bc2d7fc5710dfbe30857a`，merge-base为最新main `a2a591331100e94b8425d7b7c9d31019f459827c`，ahead6/behind0；远端Git引用复核一致。列表缓存曾返回旧PR #2，匿名API失败，改用独立PR页面与Git refs确认。保持0.1.4的上传/AI只读修复，默认版本改0.1.5/code7，高于公开清单与已安装最高6。
- 新增真实触摸回归先复现 **浏览器与DSH底部按钮均被原生浮动导航拦截、误切页（2失败，23.891秒）**。修复仅给两个AndroidView预留bottomInset，含导航、间隙与系统区域；键盘/全屏/宽屏无浮动导航时为零。保留同一WebView，不改官方DSH、完整截图开关或权限；旧网页透显断言改为安全视口实际绘制，两项真实触摸断言保持。
- 原候选release/AndroidTest、compat debug、release JVM/lint构建4分39秒成功；修复后同命令再构建 **3分12秒成功**。最终 **release JVM97项，0失败/错误/跳过；release lint0错误/71警告/1Hint**。宿主发布18项（5.679秒）、工作流12项（28.474秒）、公开边界19项（14.171秒）通过，49项不与JVM或设备混计。
- 最终实际APK `com.tanuki.modern.workspacecheck`，0.1.5/code7、ARM64、min26/target34、non-debuggable，**10,370,132字节**；SHA-256 `a21a8ae304c08857b1881f17668f442cab461d14c3c283208ecd5e4aa12dbdf2`，开发证书 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。app/test包名与instrumentation target一致，同证书覆盖；两provider独立/非导出，DEX父类与各自路径资源核对。依赖相对main无变化；APK内62项许可资产逐字节一致、79条记录/78个坐标、Kyant两NOTICE及共享Apache全文匹配。
- 复用 `Pixel_7_API_34_arm64 / emulator-5572`、ARM64/API34/WebView159，确认无安装/真实任务，仅合成数据。旧0.1.4/6覆盖前种私有标记 **1项通过，4.938秒**，没有在覆盖后重种。原失败候选上15个真实HTTP页面的草稿/文档保留、只读RPC、关闭13页恢复与按需加载 **生命周期2项通过8.521秒**；该数字不代替最终APK结果。
- 最终APK重点 **4项通过，48.044秒**：浏览器/DSH的fixed-bottom输入焦点和两个按钮接受真实屏幕触摸且不误切页、紧凑标题/地址同行/安全视口实际像素、DSH全屏同文档/真实像素。关联53项首跑 **51通过/2失败，318.326秒**，两项地址栏旧测试把20dp箭头仍按文字颜色数量检测，7/8色误判。仅适配测试为箭头形状/对比/留白、真实URL与按钮状态同时收敛；原真实触摸导航、首焦全选/再次点击光标断言保留。test-only编译35/42/39秒；前两次复测因子节点未合并disabled语义（2失败，12.292秒）及祖先缓存未刷新（1通过/1失败，11.617秒），改为有界新树/祖先refresh轮询后，最终 **2项通过20.282秒**。产品APK字节始终不变，按方法集合去重 **57个不同用例最终全部通过**，无跳过/host timeout/仪器中断；覆盖前私有标记读回原值后清理，不重种。
- 独立APK检查最初误把apkanalyzer数值资源ID当作XML名称，改读APK资源表后通过；一次source误用zsh改bash，未改APK。交接初次刷新因PR压缩STATE标题后未插入当前记录链接失败，补链接后通过；不计作产品失败/成功。候选APK公开扫描0发现，旧0.1.4六附件SHA全部通过且不改写。8张专用合成截图视觉检查：底部控件完整露出、紧凑工具实际绘制、全屏页面满幅无空白、普通/暗色200%地址栏可达；键盘截图导航在IME上方，输入可用，不宣称导航必定收起；不据截图宣称旧系统栏边界已修复。公开边界19项在交接清理后复跑通过（1.332秒），不重复累计49项宿主数量。
- 未验：MIUI真机、真实账号/模型、完整Ubuntu/Node/DSH运行环境跨APK升级、弱网/低API、长期后台及大量复杂标签资源压力、安装授权期限进程死亡恢复。既有浏览器退出全屏systemBars断言未修复且不在本轮通过范围；API Key保存继续暂停。15个小页面回归不能证明任意数量复杂标签的资源安全。

### v0.1.5 可复现的57项选择

配置JDK17/SDK后实际构建命令：

```bash
source scripts/env.sh
./gradlew -PtestBuildType=release -PdevelopmentReleaseSigning=true \
  -PtanukiVersionName=0.1.5 -PtanukiVersionCode=7 \
  -I scripts/workspace-verification.init.gradle \
  :app:assembleModernRelease :app:assembleCompatDebug :app:assembleModernReleaseAndroidTest \
  :app:testModernReleaseUnitTest :app:lintModernRelease
```

核对APK身份后，明确serial、串行 `adb -s emulator-5572 install -r <app.apk>` 与配套AndroidTest；按下列选择拆为4项重点与53项关联：`adb -s emulator-5572 shell am instrument -w -r -e class <逗号分隔的类或类#方法> -e verifyUpgradeMarker true com.tanuki.modern.workspacecheck.test/androidx.test.runner.AndroidJUnitRunner`。标记验证只在关联组执行；旧包先种，再覆盖、读原值并清理，不直接复跑删除后的标记。

整类：`DshFileUploadTest`（5）、`AgentBrowserControlTest`（14）、`WebAuthenticationTest`（2）、`DshViewportTest`（1）、`BlobDownloadTest`（1）、`ApkInstallAccessTest`（5）、`FilesBrowserPresentationTest`（5）、`ApplicationUpdateNativeTest`（3）、`ApplicationUpdatePresentationTest`（1）、`BrowserTabLifecycleTest`（2）。另选以下方法，完整类名前缀为 `com.tanuki.`：

```text
BrowserWorkspacePresentationTest#compactChromeSharesTitleRowAndWebViewportClearsNavigation
BrowserWorkspacePresentationTest#fixedBottomHttpControlsAcceptRealTouchesWithoutChangingNavigation
BrowserWorkspacePresentationTest#revokedAiKeepsDshBrowserEntryAndUserPageVisible
BrowserWorkspacePresentationTest#defaultConnectionAndTouchTakeoverSurviveBackgroundAndRecreation
BrowserWorkspacePresentationTest#tabOverviewCanCreateBeyondTwelveWithAnAccessibleBadge
BrowserWorkspacePresentationTest#multiPageOverviewBookmarksAndHistoryUseNativeControls
BrowserWorkspacePresentationTest#emptyBrowserCanExitFullscreenWithoutFocusingAMissingAddressField
BrowserWorkspacePresentationTest#displayModesSearchFindZoomCaptureAndDownloadAreUsable
BrowserOverlayPresentationTest#revokedAiCanStillOpenAndViewBrowserOverlay
BrowserOverlayPresentationTest#fullDocumentAndAiZoomAreIndependentOfSmallWindow
BrowserOverlayPresentationTest#displayDensityKeepsCollapsedControlsReachableAndNotificationStopIsFinal
SetupPresentationTest#glassNavigationBlursPixelsAndKeepsTabsClickable
SetupPresentationTest#glassNavigationDragPreviewsThenCommitsOnceAndCancels
SetupPresentationTest#glassNavigationNullBackdropDragsInRtlAndKeepsLargeTabsClickable
SetupPresentationTest#actualDshWebViewSurvivesFullscreenWithoutReload
SetupPresentationTest#fixedBottomDshControlsAcceptRealTouchesWithoutChangingNavigation
SetupPresentationTest#filesListExtendsBehindNavigationAndScrollsTailClearWithoutReloadingDsh
SetupPresentationTest#setupGuideFirstStepSourceVersionAndLocalThemeScreenshots
```

## 历史：PR #3 合并验证（2026-11-04，debug）

- 将main/a2a5913合入浏览器分支，保留上传、AI查看/操作分离和浏览器布局/浮动导航/标签改动。双debug、AndroidTest、97 JVM通过；modern lint0错误/71警告/1提示。
- `Tanuki_UI_API36` / `emulator-5554`，独立 `com.tanuki.modern.uipolish.workspacecheck`、0.1.4/6，**22项全过，256.2秒**：查看/操作/私有页/30秒/15页恢复5，上传5，完整浏览器4，小窗/整页2，地址栏2，DSH/文件列表/导航拖动4；无失败、跳过或超时。正常开发包未覆盖；本节为作者历史验证，不计作0.1.5本轮结果。
- 作者独立init只将debug后缀改为 `.uipolish.workspacecheck`，WebView133；工作流12项、公开边界19项通过。删交接后曾因记录文字未变而印章拒绝，补实际删除说明后通过；Studio普通包仅编译/核验，未安装。
- 合并前浏览器分支d3aedc6保留三笔提交：e724ef5紧凑地址/首焦全选、68d9487全页浮动、d3aedc6图标角标/取消12页截断。0.1.3组合版本先有97 JVM/15项布局回归；标签调整后另有97 JVM/11项专项，lint均0/71/1，不与本轮累计。包含文件编辑复制、下载/截图、授权/重建、实际像素和文档保留，文件尾部用合成列表验证。
- 旧布局/12页限制曾被新增回归先复现；漏Column导入、实验IME接口、输入节点标签屏蔽选择范围、查找滚过目标、误点同名标题和无障碍点击不算真实弹窗手势均已修正后复测，不把中间失败或重复运行计入通过数。源码与当前设备用例保留，旧过程记录删除。
- 真机/低API/真实DSH任务/长期及大量标签压力未验；既有退出全屏系统栏断言仍排除，API Key保存继续暂停。本轮不是正式release验收。

## 历史：v0.1.4/6 release 验证（2026-11-04）

- 用户确认0.1.4并授权push；main同步用户squash合并PR #2后的dbd30f9，tree与已测PR37dcedd一致，保留上传和AI只读修复。版本code6高于当前设备/本地及公开清单最高5；公开v0.1.3/5与v0.1.2/4已核对。
- 新增已测APK固定字节封装参数，推送后不重新编译：`--verified-apk`与`--verified-apk-sha256`成对验证，临时固定副本、实际APK身份/签名与发布副本均复验。默认构建和production重新签名路径保留；18项宿主回归通过，含错误hash/身份拒绝、原路径被覆盖仍保留已测字节。工作流12、公开边界19也通过，共49项宿主测试。
- 重新采集实际79个运行归档（78唯一坐标），SHA全部匹配，新增60 POM/42上游许可文本；原有5项缺POM许可声明范围保留。最终APK内79项Markdown清单、Kyant两NOTICE与共享Apache全文逐字节核对；JSON仅在源码归档，未把不存在的APK JSON条目当成实际内容。
- 0.1.4/6 release与AndroidTest、release JVM/lint单一Gradle构建 **4分17秒成功**。**release JVM97项通过，0失败/错误/跳过；release lint0错误/71警告/1Hint**。本节是本轮新跑，与下方0.1.3 debug结果分开。
- 实际候选为com.tanuki.modern.workspacecheck、ARM64、min26/target34、non-debuggable、开发证书371dc301…不变；**10,361,052字节**，SHA-256 `97c738c68e6f3ecf96a106db2edc8f5423f0a73ef90a12532c572d809bd989a6`。更新/浏览器provider组件独立且非导出。独立检查最初错误假设APK含JSON清单，按Gradle实际MD资产路径核对后通过，没有修改APK。
- 同一Pixel_7_API_34_arm64/emulator-5572、API34/WebView159，确认无真实任务后同包同证书覆盖。覆盖前旧debug包种合成文件1项通过（2.951秒）；实际release带 `verifyUpgradeMarker=true` 跑 **44项全部通过，249.955秒**，包含下节41项选择与ApplicationUpdateNativeTest整类3项。原值读回并删除标记，不重新创建冒充保留；无失败/跳过/超时/仪器中断。
- 这是开发签名release，不是正式密钥或真机验收；真实MIUI/模型/账号、完整Ubuntu环境升级及既有全屏退出断言仍未全面验。API Key保存暂停。最终封装复用上述已测SHA，输出0.1.4六附件；旧发布目录不改写。封装入口见[发布流程](RELEASE.md)。候选APK公开扫描0发现；测试HTML尾随空格已清理，不改已测APK。上游POM的CRLF/许可末空行保留，格式例外仅限原始许可资料，不豁免公开边界扫描。

## 历史：PR #2、上传与 AI 只读组合验证（2026-11-04）

- 固定PR #2 head `37dcedd38766745e226b5133e2a1556a32990963`，基于f02d60b，两个提交本地整合于 `codex/pr2-upload-browser`。main/origin/main未推送；用户先等本地结果，再自行在GitHub合并。组合包包含本轮未提交的上传与浏览器修复，不等于原PR已经包含这两项修复。
- 用户明确“选文件确定后无附件”：旧WebView `parseResult` 在真实159内核对ClipData-only结果返回0个，新解析保留2个；经MainActivity、真实DOM、Blob Worker XHR上传后服务器收到两份完整UTF-8原字节。真实系统SAF单选、取消/重载/销毁、来源及私有URI拒绝共5项通过。content/file直接访问仍关闭，没有改官方DSH包。
- 用户进一步明确AI查看不需要操作权。合法当前连接可读普通当前页的tabs/snapshot/screenshot，人接管时也可读；所有点击/输入/滚动/导航/标签变更仍需操作权，读取不偷偷切页。OAuth临时页读写拒绝，元数据不泄漏；新增3项控制回归和2项原生可见回归均通过。
- 最后组合Gradle构建 **2分23秒成功**：modern/compat debug、modern AndroidTest、**JVM97项，0失败/错误/跳过**；modern lint **0错误/71警告/1Hint**。Gradle8.13、AGP8.13.2、Kotlin2.3.10、compileSdk36，target34/min26；前一组合构建9分59秒和实际79项依赖解析属于中间结果，未替代最后版本。
- 同一专用 `Pixel_7_API_34_arm64 / emulator-5572`、API34/WebView159，核对包名/target/证书后覆盖安装实际debug APK。**两组41项全过**：核心23项139.877秒，关联18项133.720秒；无失败/跳过/host timeout/仪器中断。含上传5、控制13、原生撤权可读2、玻璃导航3、认证2、viewport1、Blob1、安装器5、文件5、DSH全屏1、整页/缩放/小窗1、通知/density1、接管/后台/重建1。
- 第一轮原上传3项失败（7.309秒）仅证明MIME/非法URI/重复回调缺口，直接文件字节本身成功，因此撤回初始contentAccess根因判断。SDK缺失4分33秒及Maven DNS1分53秒失败已补环境/重试解决，不计产品通过。最初组合上传5项均失败（56.671秒），定位为loadDataWithBaseURL无历史URL的测试页与新原生origin校验不符；只改夹具为真实HTTP/loadUrl、焦点首帧与触屏坐标，最终5项通过，未放宽产品校验。
- 宿主工作流12、发布12、公开边界19项本轮通过。测试APK为 `com.tanuki.modern.workspacecheck`、0.1.3/5 debug，开发证书不变；**14,110,161字节**，SHA-256 `fa2765471e57bcc9b1889d9b7f85afc6f2d04a8fede773d810b506460cf01b5b`，副本在builds/workspacecheck/pr2-upload-browser。既有releases/0.1.3六附件校验通过，未改写。
- 真实MIUI/账号/模型、完整Ubuntu/Node/DSH会话及低API渲染未本轮验收；Compat仅编译。既有退出全屏systemBars断言未修复、未计入41项。API Key保存继续暂停。可复现选择见下节，数据边界见[SECURITY](SECURITY.md)。

### PR #2 组合41项的可复现选择

整类：`DshFileUploadTest`、`AgentBrowserControlTest`、`WebAuthenticationTest`、`DshViewportTest`、`BlobDownloadTest`、`ApkInstallAccessTest`、`FilesBrowserPresentationTest`。另选如下方法（完整类名前缀为 `com.tanuki.`）：

```text
BrowserWorkspacePresentationTest#revokedAiKeepsDshBrowserEntryAndUserPageVisible
BrowserWorkspacePresentationTest#defaultConnectionAndTouchTakeoverSurviveBackgroundAndRecreation
BrowserOverlayPresentationTest#revokedAiCanStillOpenAndViewBrowserOverlay
BrowserOverlayPresentationTest#fullDocumentAndAiZoomAreIndependentOfSmallWindow
BrowserOverlayPresentationTest#displayDensityKeepsCollapsedControlsReachableAndNotificationStopIsFinal
SetupPresentationTest#glassNavigationBlursPixelsAndKeepsTabsClickable
SetupPresentationTest#glassNavigationDragPreviewsThenCommitsOnceAndCancels
SetupPresentationTest#glassNavigationNullBackdropDragsInRtlAndKeepsLargeTabsClickable
SetupPresentationTest#actualDshWebViewSurvivesFullscreenWithoutReload
```

官方固定0.2.1-rc.5上传/UI公开包经仓库锁SHA-512核对：File/Blob走Blob Worker XHR原字节上传；只有显式ReadableStream走stream fetch，勿为普通附件引入HTTP流兼容改写。上述用例是合成HTTP页面、MediaStore文件和RPC，不是实际模型任务。

## 历史：PR #2 作者的同步后验证（2026-11-04，独立历史）

作者在隔离包/API36 `Tanuki_UI_API36 / emulator-5554` 跑97项JVM、modern lint0错误/71警告/1提示，双debug/AndroidTest编译及8项仪器回归通过：Setup的三个glassNavigation和actualDshWebView全屏，以及ApkInstallAccess的预读授权、旧VIEW无预授权、旧URI/到期回收、外部URI拒绝四项。SDK36可空applicationInfo按flags=0拒绝处理，syncNotices使用Sync防旧资产残留；曾因重复Apache资产构建失败，改为共享全文/保留NOTICE后通过。不是本机API34或本轮0.1.4的新跑结果；低API、真机/真实环境仍未验。

## 历史：液态玻璃分支验证（2026-11-03，整合 main 前）

- Kotlin2.3.10/Compose1.10.3/Backdrop1.0.6 下，双debug/AndroidTest、97项JVM通过；双lint各0错误/70警告/1提示，31项宿主回归通过。API36隔离包的10项E2E通过，覆盖真实拖动/取消/RTL/键盘、大字体、原生采样对齐、WebView切页/全屏及设置/文件/更新界面。
- 当时版本0.1.1/3，不代表本轮0.1.3/5重测；低API/真机/真实环境未验收。归档必要结论后移除重复过程交接，后续结果见[测试现状](TEST-REPORT.md)。

## 历史：v0.1.3/5 安装器URI修复与发布包验证（2026-11-03）

- 用户在「设置→应用更新」安装时报MIUI FileProvider未导出拒绝，要求修复推送后生成0.1.3。0.1.2/code4中间包已归档，最终版本0.1.3/code5，保持原workspacecheck包名/开发证书。
- 先补安装器回归，再实现ClipData与系统处理器单文件只读授权、最多10分钟及下次操作/失败回收、进程重建按原期限恢复计时。实际系统确认页仍复现Permission Denial，定位原两个authority共用同一provider组件；改为独立更新/浏览器子类后通过，未开放provider或扩大目录。
- 新类尚未实现时编译29秒失败；实现后有一处ActivityInfo空值编译问题，修正后debug构建/JVM/lint通过（2分35秒）。首次设备5项中4通过，实际安装器确认页等待失败，单例重跑同样失败；系统日志证明获取provider被拒绝。拆分provider后debug构建2分20秒通过，**5项安装器回归全过，4.723秒**。实际系统安装器已读出APK标签并显示确认页，随后取消，没有自动安装。
- 权限回归核对Android真实安装器UID与普通匹配App UID、ClipData/单文件只读、拒绝写入/持久/前缀授权标记、旧文件与到期回收、非更新URI拒绝、不同provider组件及非导出状态。修复后97项JVM全过，debug lint0错误/53警告/1提示；发布12/公开边界19项宿主复跑通过。
- 修复先推送为6cfda3e，再构建最终0.1.3/code5候选release：构建102.7秒、原生对应源码17.4秒，实际APK为com.tanuki.modern.workspacecheck、ARM64、min26/target34、non-debuggable，开发证书不变。APK **7,926,865字节**，SHA-256 `dbbb4cf2647ed00f6a44423c61a4cdaef76bad730a61a3aaced48f1f8d18b6df`；两个文件provider组件独立、均非导出且仅允许URI授权，清单/签名/字节/源码与原生附件校验和公开边界扫描通过。
- 最终release AndroidTest、release JVM及release lint单一Gradle构建1分52秒成功；**release JVM97项全过、0失败/错误/跳过，release lint0错误/53警告/1提示**。同一AVD实际覆盖发布APK及配套test，携带 `verifyUpgradeMarker=true` 运行11类选定 **34项全过，171.826秒**，包含新增5项、真实系统确认页、浏览器/文件/进度相关既有范围与覆盖前合成文件原值；无跳过、host timeout、ADB断线或仪器中断。
- 独立APK核对最初误把合并manifest的provider总数断言为2，忽略AndroidX Startup，改成按updates/browserfiles两个authority筛选后通过；这是核验脚本误判，未修改APK或放宽两者独立/非导出/只读边界。最终收尾文档统一提交后刷新源码与6附件清单，APK必须维持上述已测SHA。
- 这些为AOSP ARM64 API34合成回归；MIUI真机、完整Linux环境、真实账号/模型仍未测，进程死亡后的期限恢复未实际杀进程验证。既有全屏退出断言未在本轮修复或纳入34项通过。完整步骤见[应用更新说明](APP-UPDATES.md)。

## 历史：v0.1.2/4 中间包验证（2026-11-03，已归档不交付）

- 用户确认0.1.2并要求先推送本地源码、再打包。主目录已从67216c9快进至用户合并PR后的ece5c82，合并代码树与已测PR 5e08178一致；仅将版本默认值/源码包名更新至0.1.2/4，补齐实际58项依赖及上游许可、同步交接/发布文档，无业务行为修复。
- 公开v0.1.1/v0.1.0附件的GitHub SHA-256与本地旧APK一致，apkanalyzer实际code为3/1，设备与撤回候选均为3；新版code4单调递增，沿用原workspacecheck开发证书。
- 本轮主目录新跑：发布12项、工作流12项、公开边界19项宿主回归通过；97项JVM无失败/错误/跳过，debug lint 0错误/53警告/1提示，Gradle 1分57秒成功。112个本地Markdown链接与diff格式检查通过。
- 准备提交681f55a先推送后，release构建109.1秒、原生对应源码12.8秒成功，实际0.1.2/4、non-debuggable、ARM64、SDK26/34、同开发证书与6附件校验通过；release AndroidTest构建43秒。实际APK覆盖后29项选定回归全部通过（153.118秒），包含覆盖前私有合成标记读回；这组此前未覆盖实际系统安装器读文件，因此不能代替后续URI修复验证。6附件原字节归档到archive/0.1.2-code4-before-uri-fix，旧16归档文件不改，归档索引共22文件。

## 历史：PR #1 界面改版隔离验证（2026-11-03，验证时尚未合并）

- 固定 [PR #1](https://github.com/leezzou/tanuki/pull/1) head `5e081784707352f35564890ad139b50580190661`，共同祖先为用户最新 main `67216c9561294e196d5e678c381b09888bc61e30`，ahead2/behind0；收尾远端引用仍一致。两个独立 worktree 的产品代码均保持各自提交原样，主工作区只记验证文档，不自动合并。
- PR 的 `verify-workspace.sh` 构建阶段通过（3分37秒）：Modern debug app/test、97项 JVM（0失败/错误/跳过）、lint 0错误/53 warnings/1 information。另顺序编译 Compat debug 通过（1分9秒），未安装不同包名或在旧 Android 上运行。
- 复用 `Pixel_7_API_34_arm64` / `emulator-5572`，ARM64 API34、WebView159.0.8133.0、专用合成数据，无安装/真实任务。实际 APK 包名、test target、开发证书一致后同包覆盖；覆盖前、PR安装后及main/PR相互覆盖后的合成文件读回均通过。最终保留 PR debug v0.1.1/3，不是前轮发布 APK 或正式签名包。
- PR 首轮受影响 UI 组22项：21通过/1失败（177.584秒）；文件/浏览器安全扩展组40项：39通过/1失败（160.375秒）。覆盖玻璃背景像素模糊/点击/RTL/200%字体、安装步骤与进度、文件编辑复制多选、设置与更新入口、浏览器多页/收藏/历史/搜索/下载、触摸暂停/30秒归还/显式撤销、整页尾部截图/AI缩放/小窗、WebView保留、合成外链/认证与Blob等；未运行依赖已装运行环境的两个用例，不把未选择算通过。
- 失败对照：`BrowserWorkspacePresentationTest#newBrowserPageDefaultsToWindowsAndFullscreenKeepsTheDocument` 在第227行退出全屏后的 `systemBars()` 可见性等待失败，PR 单独复跑仍失败（23.799秒），main 同点失败；根因未修复，不宣称该功能已通过。`BrowserOverlayPresentationTest#displayDensityKeepsCollapsedControlsReachableAndNotificationStopIsFinal` 第303行通知动作缺失，main也同点失败（两项对照共30.540秒）。设备通知权限原为关闭，正常点击原生入口和系统「允许」后，此通知用例 main 1项通过（11.635秒）、PR与合成文件复测2项通过（11.619秒）。通知授权保留，density恢复420。
- 因此本次62个不同设备用例累计61通过、1项既有全屏断言失败；复跑不重复计数，首轮失败不抹掉，无host timeout/仪器中断/跳过。未发现本 PR 新增功能回归；真机、真实账号/模型、完整 Ubuntu/Node/DSH、旧 Android 渲染与正式签名未验收，API Key保存仍暂停。
- 流程缺口：PR原样 `check-handoff.py` 会报 Stale handoff；本地测试副本按规则新增本会话交接并刷新后才执行构建，未关闭检查。合并后须同步测试记录、刷新内容指纹；本次没有提交/推送这些文档。当时交接280指纹文件、源码283条目（含原生源码），公开扫描0发现、SHA通过；源码只含main及验证文档，不交付未合并PR APK，0.1.1发布附件未改。
- 后续用户已在 GitHub squash 合并为 `ece5c82`，0.1.2打包轮快进到该提交；其代码树与上述固定PR一致。同步与新包结果另见[测试现状](TEST-REPORT.md)，不将本节结果冒充新release重跑。

## 历史：产物分类验证（2026-11-03，非产品重测）

- `scripts/test-app-release.py` 12项、`test-agent-workflow.py` 12项、`test-publication.py` 19项均通过；源码分类、原生附件白名单、发布收集、模拟正式签名分类输出和清单相对路径有回归覆盖。两个签名/原生打包 shell 语法检查通过。
- 迁移前记录22个产物文件的SHA-256，分类后逐一确认所有原始内容仍存在，未删除旧APK/源码；当前发布只保留0.1.1，旧0.1.2移入撤回归档。归档清单含16个文件。
- 新增路径回归初始1失败/3错误；实现后通过。首次工作流回归有1项合成fixture缺少新说明文件，补fixture及实际分类输出断言后12项通过，未放宽签名路径保护。
- 本次只改宿主脚本/文档和移动产物，未重编APK、未运行Gradle/JVM/lint/模拟器、未操作正式密钥或重新执行原生源码打包；下述97项JVM与20项设备是前轮结果，不计为本轮新跑。原交接在0.1.2提交前清理，本节保留实际验证与失败，分类和归档校验入口见[取包说明](ARTIFACTS.md)。

## 历史：v0.1.1 产品验证：登录、空闲权限与应用更新（2026-11-03）

目标 `com.tanuki.modern.workspacecheck`、`versionName=0.1.1` / `versionCode=3`，开发证书。代码改动先加回归再实现；不是生产证书/真实登录/模型任务的正式验收。

| 范围 | 本轮实际结果 | 限制 |
|---|---|---|
| JVM | **97 项通过，失败/错误/跳过 0** | OAuth 入口/一次票据、空闲/显式撤销、Feed/清单/数字与 URL、字节/文件链接/哈希/APK 身份等；不是设备系统安装链 |
| modern debug 与 AndroidTest 构建 | 成功；明确核对包名、versionName/code、ARM64 和实际开发证书 | `-I scripts/workspace-verification.init.gradle -PtestBuildType=debug -PdevelopmentReleaseSigning=true`，无并行 flavor 构建 |
| debug lint | **0 error，53 warnings，1 information** | 包含 UsableSpace25、RequiresFeature9、GradleDependency7、交互/可访问性等提示；未宣称全部既有或清零 |
| 宿主脚本 | 发布准备 9、工作流12、公开边界回归19，均通过；签名 shell 语法通过 | 合成 badging/证书数据，不读取正式密钥 |
| 复用模拟器 debug | `Pixel_7_API_34_arm64` / `emulator-5572`、ARM64 API34/WebView159，**6 类19 项最终全套通过** | `AgentBrowserControlTest`、`AppBrandingTest`、`ApplicationUpdateNativeTest`、`ApplicationUpdatePresentationTest`、`BrowserIntegrationTest`、`WebAuthenticationTest`；不是前轮14类54项全部重跑 |
| 官方登录合成路径 | 官方平台授权、预开空白异步弹窗/主框架导航、一次性精确 callback、来源/端口/重放拒绝通过 | 未使用真实 DeepSeek 账号；管理 token/Cookie 不复制至网页或日志 |
| 浏览器30秒 | 设备真实等待验证临时接管归还；交互复位/显式撤销不恢复等通过 | 后台本身不撤销；不改变进程级整页绘制设置 |
| 实际点击更新检查 | 原生设置显示「暂无可用于应用内更新的 Release；发布时需附带 app-update.json」 | 公开 Atom/附件，**不调用 GitHub API/不需要 token**；无本版清单时不假装有更新 |
| 原生覆盖文件基线 | 单独 Native 3 项通过，种合成私有文件；发布 APK 覆盖后加 `verifyUpgradeMarker=true` 读回原值并清理合成文件 | 未重新创建冒充保留；不是完整 Linux/真实用户数据覆盖测试 |
| 版本更正前 0.1.2 non-debuggable 包/覆盖后回归 | 一键准备脚本成功，实际 APK 包名/0.1.2/3/SDK26/34/ARM64/开发证书核对；同一 AVD `adb install -r` 后 **6 类20项通过，104.346秒** | 发布目录真实 APK 被测试；没有生产签名/Release 上传，AndroidTest 与 debug 同证书 |

最终用户更正为 v0.1.1 后重新执行 release/AndroidTest 构建、97 项 JVM 与 debug lint，结果仍如上表；`prepare-app-release.py --version 0.1.1 --version-code 3 --channel workspacecheck` 成功。覆盖前重新种合成私有文件，安装最终 0.1.1 APK 后加 `verifyUpgradeMarker=true` 跑同6类，**OK (20 tests)，113.154秒**，无失败/跳过/ADB断线，开发证书不变。发布打包另修正 ZIP 版本根目录与扁平目录原生 SHA 清单各一项，9 项宿主回归通过。

首轮18项中合成 WebView 等待超时1项，及 UI fixture 未等异步回调/小窗遮挡，均保留为失败尝试；补阶段诊断、正确等待/关测试小窗后最终19项通过。原生测试曾引用错误字段/参数次序导致编译失败，修正后完整构建通过。没有把跳过或主机超时计为通过。当前真实账号、真机、完整 Linux 环境覆盖/弱网首次安装、未来更高 Release 系统授权安装链仍待验；API Key 保存继续暂停。

v0.1.1收尾98个本地链接、Git候选/索引各278文件、源码/实际APK公开扫描0发现；外部SHA及独立解压交接通过（277指纹文件、ZIP280条目）。曾遇package service未就绪、SDK环境/检查参数与SHA工作目录误用，等服务就绪并修正调用后通过；合成userinfo等价改写消除误报，没有放宽校验。OAuth辅助调查仅为公开协议静态核对，Node路径失败不计独立通过。产物和数据边界见[应用内更新](APP-UPDATES.md)、[发布说明](RELEASE.md)。以下全部是前轮/历史范围，不计为本轮新跑。

## 历史：文档维护验证：提交前清理交接（2026-11-03，历史）

- 仅修改工作规则、发布/接手文档和交接引用，清理 4 份已被替代的过程记录；未修改产品代码。
- 新跑 `python3 scripts/test-publication.py` 19 项、`python3 scripts/test-agent-workflow.py` 12 项，均通过；本地 Markdown 链接检查通过。该检查不自动判定自然语言交接是否过期。
- 没有重跑 JVM、lint、AndroidTest 或设备任务；此前发布咨询已成功构建标准 modern unsigned APK（`com.tanuki.modern`、仅 ARM64、非 debuggable、未签名），不是本次清理的新构建，也不是可直接安装的正式包。
- 保留以下修复轮与前轮的原始验证范围；交接印章/源码包最新校验结果见[当前状态](AGENT-STATE.md)。没有新的 Git 提交/推送、tag 或 Release。

## 历史：产品修复验证：DSH 解析与弱网恢复（2026-11-03）

| 范围 | 实际结果 | 边界 |
|---|---|---|
| 预置官方依赖锁 | 686 个非根记录均为官方 HTTPS/SHA-512，文件摘要与 APK 固定值一致 | 从官方 registry 新生成的公开元数据，不含依赖实现，不等于发布者签名 |
| 产品依赖图脚本宿主验证 | 模拟 Linux/ARM64/glibc、Node 24.22.2/26.13.0/22.23.4，各选中 560 个归档且不改锁；3 个错误来源/入口校验/缺失必需依赖样本被拒绝 | 使用宿主 npm 11.19.0；不是 Android、PRoot、下载或实际安装测试 |
| JVM | 64 项通过，失败/错误/跳过均 0 | 新增锁缓存、默认预置锁、隔离/损坏/符号链接/失败重试与命令到期/外部取消回归；不代表手机性能 |
| Android 编译/组装 | modern debug、开发签名 release、debug AndroidTest 均成功 | 单一 init/flavor 串行 Gradle，完整构建/JVM/双 lint 4m7s；更新声明后再次组装，不安装 |
| lint | debug/release 各 0 错误、49 警告、1 提示 | 非零警告，不能当全量产品验收 |
| APK 身份与签名 | debug/release 均为 `com.tanuki.modern.workspacecheck`、Tanuki、仅 ARM64；debug=true/release=false；AndroidTest target 同目标；apksigner verify 成功 | 继续使用原开发证书，不是正式签名；未覆盖安装 |
| 公开边界与宿主回归 | Git 工作树/索引扫描 0 发现；19 项上传脱敏、12 项交接/保护回归均通过；两 APK 与重建原生对应源码归档各 0 发现 | 规则有范围限制，不扫描用户数据、真实凭据或设备 |
| 源码/迁移 | 258 个参与交接指纹的公开文件；源码 ZIP 261 个文件（含两份原生附件），0 发现；源码/双 APK/原生外部清单全部匹配；独立目录交接/边界/宿主锁/19+12 项回归通过 | 不依赖原工程路径/Git/私有缓存；结果回填后再次打包核对，不计为 Android 设备通过 |

```bash
python3 scripts/test-dsh-lock.py
source scripts/env.sh
export ANDROID_SDK_ROOT="$TANUKI_SDK"
./gradlew --no-daemon -PtestBuildType=debug -PdevelopmentReleaseSigning=true \
  -I scripts/workspace-verification.init.gradle \
  :app:assembleModernDebug :app:assembleModernRelease :app:assembleModernDebugAndroidTest \
  :app:testModernDebugUnitTest :app:lintModernDebug :app:lintModernRelease
python3 scripts/test-publication.py
python3 scripts/test-agent-workflow.py
python3 scripts/check-publication.py --git
```

- 最终 debug APK SHA-256：`f98d770eb3d0ad51b302e0deab8c456a22fd5f1d92beb0371b26c03cf551c0c5`。
- 最终 release APK SHA-256：`360f4b2408ccc31712e72cc291153f6b2150adee2ab1d38112ee562f7e211d77`。
- 开发证书 SHA-256 继续为 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。
- 初次 JVM 执行 64 项中有 1 项测试断言失败：协程栈恢复可复制 `CancellationException`，不能要求对象引用相同；已改为检查取消消息与只调用一次，随后 64 项全部通过。没有将首次失败计为通过。
- 仪器回归已补「已安装/缓存锁复用、无元数据解析、现有程序不变」断言，但本次**仅编译**。默认版确实可跳过元数据解析已由产品逻辑/宿主依赖图和 JVM 回归核对；手机弱网提速幅度、完整候选安装、超时后重试和覆盖升级尚未设备验证。
- 没有延长原请求/总命令超时、绕过完整性、删除已安装组件或用户数据，也没有安装、签名迁移、发布、提交或推送。设备待验还包括实际npm缓存写入、同版本修复/其他版本更新、归档中断重试、清缓存后预置锁恢复、实际Node/npm切换重检及无可信锁版本解析回退；不承诺弱网提速倍数。

## 历史：前一轮构建/迁移记录（2026-11-02，非本轮重跑）

以下保留前轮的实际范围与散列供对照，**旧 APK 散列不能用于本轮修复包**。

| 范围 | 实际结果 | 边界 |
|---|---|---|
| 上传与脱敏宿主回归 | 19项通过 | 工作树/索引、凭据/身份、品牌/路径、归档预算/元数据、Kotlin字符串与必要署名等合成样本 |
| 工程宿主回归 | 12项通过 | 交接/迁移、模拟设备保护、debug/release包名隔离、正式密钥相对路径/符号链接边界；不调用真实ADB/Gradle或密钥 |
| Android编译/组装 | modern debug、release、debug AndroidTest均成功 | 单一Gradle流程，4m7s；没有执行设备instrumentation |
| JVM | 45项通过，失败/错误/跳过均0 | `testModernDebugUnitTest`；不代表Android/guest/模型实测 |
| lint debug及release | 各0错误、49警告、1提示 | 存量警告保留，非零警告，不是全量产品验收 |
| APK | 两包标签Tanuki、目标均`com.tanuki.modern.workspacecheck`、仅ARM64；debug=true、release=false | `aapt dump badging`核验；test target同目标；不安装 |
| 签名/公开边界 | 两包apksigner verify成功，使用同一开发证书；各自递归扫描0发现 | 不是正式证书；自动规则有范围限制 |
| 原生源码 | 对应源码归档生成成功，公开边界/归档扫描0发现 | 固定PRoot提交与talloc SHA256；不等于设备运行通过 |
| 默认release签名 | signingReport：modern/compat的Config/Store均null | 未传开发签名属性，默认unsigned；compat未编译 |
| 源码/迁移 | 251个源码文件（含2个对应源码附件），ZIP递归扫描0发现，外部散列均匹配；独立目录交接/边界及19+12项宿主回归通过 | 不依赖原工程路径/Git/私有缓存；TAR元数据已归一化 |

```bash
python3 scripts/test-agent-workflow.py
python3 scripts/test-publication.py
source scripts/env.sh
./gradlew -PtestBuildType=debug -PdevelopmentReleaseSigning=true \
  -I scripts/workspace-verification.init.gradle \
  :app:assembleModernDebug :app:assembleModernRelease :app:assembleModernDebugAndroidTest \
  :app:testModernDebugUnitTest :app:lintModernDebug :app:lintModernRelease
```

`AppBrandingTest.applicationAndLauncherAreNamedTanuki`检查应用标识、应用类、资源和Launcher标签；本轮仅编译，不在设备执行。release开发签名仅由显式属性启用，默认release留给维护者正式签名；[RELEASE](RELEASE.md)列明方式。

开发证书SHA256：`371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`。

- debug APK SHA256：`bb9184a657b94f3718353a68c3c68a189639ca2637be3ddc082045584829c7e8`
- release APK SHA256：`df929e811749c10dd0274c4f2485a710d6eccf61a8a20b6a2cc63d18c453ad9c`

Git与源码边界扫描通过，249个Git候选、0索引、0发现；公开归档/迁移范围保留于本节表格，原已完成交接在0.1.2提交前清理。原生依赖官网首次下载HTTP504，使用官方FTP镜像且SHA256完全匹配后重试成功；未放宽TLS/哈希。

发布扫描不覆盖所有自定义秘密、姓名、图片像素或Git历史，第三方必要许可署名保留。原始日志不上传，不以本机缓存作为交接依据。

## 历史：参考范围（不是本轮设备结果）

既有版本曾通过14类54项API34 ARM64/WebView159设备回归，覆盖浏览器权限、多页、整页尾部截图、缩放/小窗、文件/容量、安装/基础工具和rootfs恢复；使用合成页面，不调用模型或真实微信登录。本轮受影响范围未设备重跑，不能将参考数字算作当前APK通过。

原生运行路线曾验证普通UID、non-debuggable环境下的Ubuntu/Node/PTY/HTTP/DNS/HTTPS及官方DSH；不是跨设备、完整模型任务或安全沙箱证明。

## 历史：未测与限制

- 本轮未执行长期生命周期、真实guest文件/进程所有权与真实Skill/模型任务；桌面标识、通知停止和浏览器/文件的合成范围以本版首节为准。
- 本轮已做合成整页尾部像素、AI缩放和小窗回归；跨机型/旋转、既有退出全屏断言仍须单独验收，不能只查DOM。
- 真实模型/微信登录、HyperOS、长期后台、跨WebView/Android、16KB page-size、全部更新/恢复和正式签名未全面验收。
- 快照仍拒绝超出既有白名单的绝对软链接，包括只读工具/下载挂载；未扩展该安全范围或实测相应场景。
- 当前登录跳转仅合成回归已验证，真实账号仍未验收；API Key 保存继续暂停。跳过、超时、失败或 ADB 断开不计通过。

设备测试先确认专用数据、无apt/dpkg/真实任务并明确选择设备；本轮对同包同证书APK覆盖安装，不卸载、清数据或恢复快照。依赖完整运行环境的用例仍需另满足前置条件，见[上手指南](AGENT-QUICKSTART.md)。
