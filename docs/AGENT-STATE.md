# Tanuki 当前状态

当前交接：[文件复制界面稳定](handoffs/20261110-0330-copy-flicker.md)（android_agent-40，分支 `ao/copy-flicker/root`，基线 `main=0555c3c0`，PR [#35](https://github.com/leezzou/tanuki/pull/35) 待审；不合并、不发布）。上一记录：[0.1.10 Pre-release 发布准备](handoffs/20261109-2310-release-0.1.10.md)（android_agent-32，最终 `main=9e7edbb9`，9 个 PR 全部合并；0.1.10/code12 候选已生成）。并行与历史记录保留在 `docs/handoffs/`。

### 文件复制界面稳定（android_agent-40，PR [#35](https://github.com/leezzou/tanuki/pull/35) 待审）

- 分支 `ao/copy-flicker/root`，基线 `origin/main=0555c3c0`；PR [#35](https://github.com/leezzou/tanuki/pull/35)，独立 worktree，未合并、未发布、未触发 AO Review。
- 根因：`WorkspaceStorage.importFiles` 每 64 KiB 调 `onProgress`，`MainActivity` 在 `setContent` 根部 collect 传输流，`TanukiUiModel.transfers` 每个分块变化都让整个 `TanukiUi`（含文件列表）重组；导入 `track` 未传总量，只能显示不定进度；`WorkspaceFilesPage` 在重新筛选/排序期间把 `shown` 置空，Lazy 列表项数归零使滚动回顶部；`busy/sorting` 的进度条直接插入推动列表。
- 修复：`FileTransfers` 集中合并中间进度（160ms）、总量变化/最终帧立即发布、同总量丢弃回退分块、新增协作取消；导入先查询 `DISPLAY_NAME`/`SIZE`，只有全部大小有效才给真实总量；进度流只由 `OperationBanner` collect；`FileListPresentation` 同目录刷新保留旧条目、换目录清空、内容相等复用条目列表；`LazyListState`/`LazyGridState` 按目录保存并支持旋转恢复；固定 4dp 进度槽。
- 已验证（本轮新跑）：`compileModernDebugKotlin` BUILD SUCCESSFUL；完整 `testModernDebugUnitTest` 与 `lintModernDebug` BUILD SUCCESSFUL，550 tests / 0 failures / 0 errors / 0 skipped（main 535 + 新增 15）；`compileModernDebugAndroidTestKotlin` BUILD SUCCESSFUL；新增仪器测试 `FilesBrowserPresentationTest.sameDirectoryRefreshWithNewFilesKeepsScrollPosition` 仅编译、未运行。
- 待验证：真实 SAF 导入、大文件/多文件/取消 UI、旋转恢复、真实系统文档选择器、真机；设备由独立 QA 独占。

## 0.1.10 Pre-release（最终发布准备，本地候选）

- 版本 `versionName=0.1.10`、`versionCode=12`、`channel=workspacecheck`（开发证书、non-debuggable、ARM64、minSdk26/targetSdk34）；最终基线 `main=9e7edbb929cbde4e3560e94a7c157803cac11e95`，分支 `ao/xlsx-preview/release-0.1.10`。
- 本版包含 9 个已合并 PR：PR #21 设备温控、#22 DSH 通知、#23 XLSX、#24 PDF、#25 失败分类、#26 原生界面切换、#27 冷启动、#28 DOCX、#29 远程主机。
- 0.1.9 已发布 Pre-release：tag `v0.1.9`=`5c680b9`（code11），APK SHA-256 `210b6ec2119e8dd6ee08ec3d2be07889f1fa36720c6e15dd24d74a1d25a49dc8`、10,506,550 字节、证书 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`；0.1.9 附件不回写。
- 最终验证（本轮）：`compileModernDebugKotlin`、完整 `testModernDebugUnitTest` 与 `lintModernDebug` BUILD SUCCESSFUL，**535 tests / 0 failures / 0 errors / 0 skipped**。
- 正式候选 APK（本地）：`artifacts/releases/0.1.10/Tanuki-0.1.10-workspacecheck-arm64-release.apk`，SHA-256 `66cacc393fe5e7633df2079dd278ef21dd2f7731eb6f977f30a97f2638e1563d`、`11,485,978` 字节、证书 `371dc301...`；`app-update.json`、源码 ZIP、原生对应源码与校验在 `artifacts/releases/0.1.10/`。
- 发布动作：本地单提交后 push `main`，再创建 `v0.1.10` tag 与 GitHub **Pre-release**，上传完整 6 附件；不覆盖 0.1.9 及更早附件。

- `artifacts/releases/0.1.8/` 六附件齐全。W1 只读复核 APK：`com.tanuki.modern.workspacecheck`、`versionName=0.1.8`、`versionCode=10`、minSdk26、targetSdk34、arm64-v8a；开发证书 SHA-256 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`；APK 10,479,850 字节，SHA-256 `eb695a12778652075e0324c65486534dab98848f073daa14ea631291b69b146e`。公开 Release 的 `app-update.json` 与 APK 均返回 HTTP 200。
- 0.1.8 已有独立 QA 会话 android_agent-10 的首轮设备冒烟：`emulator-5572` = `Pixel_7_API_34_arm64`（API34/ARM64，WebView `159.0.8133.0`）；`install -r` 从 0.1.7/code9 升级到 0.1.8/code10（约 1.57s，保留数据），设备 base.apk 与发布件 SHA-256 一致；冷启动 `LaunchState COLD`、`TotalTime 1616ms`；首启停在 Ubuntu 安装页 1/4，未执行安装；应用内浏览器加载 GitHub Release 页，WebView 正常；logcat 无 FATAL/ANR。
- 该冒烟只覆盖安装/启动/基础 UI/WebView；Ubuntu/Node/基础工具/DSH 安装、真实模型任务、底部导航点击、真机/MIUI/低 API 均未验证，不能写成 0.1.8 已验收。0.1.7 及更早的 104 JVM、54 设备方法等仍是历史记录，未在本轮复跑。
- main CI 以 PR #13 合并后的 Actions 结果为准；Android job 使用 `-x buildNativeRuntime` 且仓库不跟踪 `jniLibs`，CI APK 无原生 `.so`，不能用于设备验收。

### 远程主机直连 SSH 切片（android_agent-34，PR #29 待审）

- 基线 `2f3fac9301c7c3484df85e99091e8f63dddc0a48`；分支 `ao/remote-host/remote-ssh` 在独立 `/tmp` clone 创建并推送，PR [#29](https://github.com/leezzou/tanuki/pull/29) base `main`，未触发 AO Review、未合并。
- 新增 `RemoteHostConfig`/`RemoteHostMachine`：host（IPv4/IPv6/域名）、SSH 端口、用户、密码或 OpenSSH/PEM 私钥（可带口令）、远程 DSH 端口、可选 token；校验不通过给出中文错误。远程 origin 固定 `http://127.0.0.1:<SSH 本地转发端口>`，本地/远程端点分别保存，远程优先、断开/失败自动回退本地；本地 DSH 停止不清除仍存活的远程端点。
- SSH：`com.github.mwiede:jsch:2.28.8`（纯 Java、无传递依赖、Revised BSD），只做直连认证与 `direct-tcpip` 本地转发；本切片 RSA/ECDSA，Ed25519 需后续 BouncyCastle。首次连接先展示 `SHA256:` 主机指纹，确认前不发送认证信息；之后指纹不一致直接拒绝。
- 安全：密码/私钥/口令/token 只经 AndroidKeyStore AES-256-GCM 加密后写入应用私有 SharedPreferences；Keystore 失败不降级明文；`toString()`/错误信息不含凭据；远程 token 只拼到 loopback 启动 URL 经隧道发送，不写日志、不导出。
- UI：设置 → 远程主机支持测试连接、保存并连接、断开/清除、首次指纹确认；测试连接的确认对话框显示实际 SHA256 指纹；DSH 页显示远程主机/隧道/指纹，失败显示中文错误并回退。远程连接期间本地运行时保持原状；中继/WebRTC、远程 DSH 启停、多主机、自动重连未实现并标注后续。
- Review 修复：Keystore 加密由 provider 生成 IV；管理页 Cookie 按逻辑主机隔离（本地/各远程分别保存，切换前清空并恢复，失败时页面置 RENDER_ERROR）；认证校验与 token 重试只绑定活动端点；设备测试夹具移除 origin/authenticatedUrl 反射。
- 已验证：`compileModernDebugKotlin` BUILD SUCCESSFUL；`assembleModernDebug -x buildNativeRuntime` BUILD SUCCESSFUL（D8 正常打包 JSch）；远程分支完整 `testModernDebugUnitTest` 229 tests / 0 failures / 0 errors / 0 skipped（新增 `RemoteHostTest` 24 项）；合入 `main=5fd1ab4`（含安全模式与 XLSX）后完整 JVM 289/0/0/0；合入冷启动/PDF/原生界面/温控/DOCX后完整 JVM 535/0/0/0；`lintModernDebug` 0 errors / 95 warnings / 1 hint；依赖清单 91 构件并刷新 JSch 许可。
- 待验证：真实 Android 设备到真实 sshd、真实 Keystore/远程 DSH token、弱网/断线、设备 UI、Ed25519、Android 仪器/真机；未安装设备、未更新 0.1.8 附件。

### 后台更新检查（android_agent-24，PR #19 待审）

- 分支 `ao/workmanager-updates/background-update-check`，已 rebase 到 `origin/main=9b32051`；独立 `/tmp` clone 验证/提交，未使用共享 worktree 的 `.git` 写操作。
- WorkManager 依赖 `androidx.work:work-runtime-ktx:2.9.1`；两个唯一周期任务：APK `tanuki-apk-update-check` 每 30 分钟、DSH `dsh-engine-update-check` 每 24 小时，均要求联网。
- APK 只读稳定 Release `app-update.json` 并与 `BuildConfig.VERSION_CODE` 比较；DSH 先读 npmmirror，失败/结构不完整回退官方 npm，连接与读取超时各 12 秒；均只提示，不自动下载/安装，不改 DSH 下载逻辑。
- 状态分离：`apk-update-check.json` 与 `dsh-update-check.json` 各自保存 `lastCheckedAt/latest/available/lastError/notifiedVersion`；不复用 `AppUpdateState` 或 `RuntimeManager` 的 `UpdateState`。
- 前台：APK 更新卡片和 DSH 版本卡片显示“有更新：`<version>`”徽标；手动检查保留，仅在检查完成事件同步独立后台快照（不重复联网），开始时间早于后台结果的旧结果不覆盖较新快照；APK 判定与恢复统一按 `versionCode`；DSH 快照在仓库恢复及实际安装版本变化时只按当前安装身份重算 `installedVersion/available`，保留远端 latest 与通知去重。后台：`updates` 通知渠道按版本去重；Android 13+ 检查 `POST_NOTIFICATIONS`，手动检查入口顺带申请；失败静默记录。
- 已验证：`compileModernDebugKotlin` BUILD SUCCESSFUL；`testModernDebugUnitTest` BUILD SUCCESSFUL，174 tests / 0 failures / 0 errors / 0 skipped；新增 `BackgroundUpdateChecksTest` 23 项。
- 待验证：设备温控/省电策略的真机温控、低电/低内存、WorkManager 真实调度与重启恢复、通知、设置页 UI、PDF 降级像素；PDF 原生阅读 PR #24 的独立 Review、真机大/加密/损坏 PDF 与手势/内存/视口裁剪验收；真实 WorkManager 调度/重启恢复、通知权限与展示、真实 GitHub/npmmirror/npm 请求、设备 UI；lint/assemble/仪器未跑。独立 Codex High Review、合并与发布未授权/未完成。
### DSH 安装提速与国内镜像（android_agent-23，PR #20 待审）

- 分支 `ao/dsh-install-2/install-resilience`，已 rebase 到 `origin/main=6136451`（含 #19 更新检查）；保留 #20 的下载/镜像/并发改动与 #19 的更新检查接线，未重做 P1。
- 锁快速路径：saved/installed/bundled 锁按 version+SRI+platform 命中后先做完整依赖图校验，通过即安装；Node/npm 升级不再使锁缓存失效。全部候选不可用才解析元数据。
- 无锁解析：npmmirror 优先、官方兜底；npm `fetch-timeout=15000ms`，命令上限 180s；镜像生成的锁把 `resolved` 归一化回官方 registry，SRI 不变。
- 下载：自动模式国内镜像优先、官方最后；401/403/404/410 快速换源，429/408/425/5xx 在单请求内 500ms/1000ms 指数退避后降级源；单请求 connect/read 30s，Range/206 续传，临时文件 SHA-512 通过后原子 rename。
- 并发：默认 8 路，连续成功后升至 16；每 host 信号量上限 8；已校验归档跨重试保留。
- 已验证（本轮新跑）：`compileModernDebugKotlin` BUILD SUCCESSFUL；rebase 后完整 `testModernDebugUnitTest` 205 tests / 0 failures / 0 errors / 0 skipped；目标 Npm/Download 回归 61 项通过，`NpmAdaptiveWorkerPoolTest` 升降档 2/2。
- 待验证：真实 Android 设备/PRoot 首次 DSH 安装、弱网与镜像 404/429 注入、4000 包端到端吞吐；未跑 lint/assemble/仪器/真机。

### DSH 冷启动优化（android_agent-27，PR #27 已合并为 `b9eeb75`）
### DSH 冷启动优化（android_agent-27，PR #27 已合并为 b9eeb75）

- 分支 `ao/cold-start/root`，基线 `main=2f3fac9301c7c3484df85e99091e8f63dddc0a48`，独立 worktree；已合并 `origin/main=c2f1b44`（安全模式 PR #25、XLSX PR #23、聊天通知 PR #22），解决 `RuntimeManager.start()` 与文档/印章冲突。
- 启动期只读路径缓存：宿主把 `assets/dsh-startup/path-cache.cjs` 复制到应用私有目录并绑定 `/opt/tanuki-startup`，仅在绑定成功时给 DSH `web` 的 node 注入 `--require`；只缓存 `/opt/node`、`/opt/engines`、`/opt/pnpm` 与受控支持目录的 `realpathSync`/`realpathSync.native`/`existsSync` 和只读 `package.json`；含 `.`/`..`/空段的路径直接交原生，避免符号链接后 `..` 被词法折叠串项；缺失路径/非只读目录不缓存；官方 `dsh web:` 地址行或默认 30 秒超时后卸载，之后直通 `node:fs`；`TANUKI_DSH_PATH_CACHE=0|false|off|no` 关闭；缓存异常逐调用回退原生；两个配置经 `DshStartupConfig` 校验后显式传入 guest，保留 `env -i` 清理边界。
- 首帧前提前启动：`MainActivity.onCreate()` 在 `setContent` 前请求，复用既有前台服务与 `RuntimeManager.start()`；仅 `READY + STOPPED + 无 job/child + 非 stopping + 无 RECOVERY_REQUIRED` 且为 Launcher `MAIN + LAUNCHER` 冷启动；`Snapshot.operationActive` 与独立更新忙碌信号排除 STAGING/TESTING/RESTORING；每进程一次；服务端 `START_EARLY` 再校验；设置 → 运行与后台新增“启动应用时自动运行 DSH”（默认开启，可关闭）。
- 已验证（合并后本轮新跑）：`-x buildNativeRuntime` 下 `compileModernDebugKotlin`、完整 `testModernDebugUnitTest` 与 `lintModernDebug` BUILD SUCCESSFUL；306 tests / 0 failures / 0 errors / 0 skipped（含 `EarlyRuntimeStartTest` 17 项、`DshStartupConfigTest` 6 项，以及 main 的安全模式/聊天通知/XLSX 回归）；`node scripts/test-dsh-path-cache.mjs` 10/10 场景 PASS。
- 合并冲突已解决；推送后以新 head 的 Host/Android CI 为准；待独立 Review 与合并授权。
- 待验证：真实 Android/PRoot 内 DSH 启动与冷启动计时、首帧提前启动时序、设置开关 UI、assemble、真机。
### DSH 聊天通知（android_agent-29，PR #22 已合并为 c2f1b44）

- 事件来源：官方 `/api/remote.mux` 的 `$events` 转发流；页面 DocumentStartScript 旁路现有 WebSocket 监听，原生只接收 `api-session/status`、`api-session/error`、`approval/request`、`user-questions/request` 的最小字段。
- 分类与抑制：`status true→false` 为回合完成，`error` 为错误，`approval` 为权限请求，`question` 为等待确认；Activity 前台、DSH 标签可见且 `[data-conversation-session]` 等于事件会话时不提醒。
- 去重：session + DSH 版本 + event；`approval/question` 用官方 `eventId`，`status/error` 用 `pageId+seq`，回合完成另由 `ChatTurnState` 保证一次。
- 入口：`dsh-chat` 渠道、每会话通知（跨进程稳定 tag + 固定 id）、group/summary、Android 13 `POST_NOTIFICATIONS` 请求、应用内总开关、系统/渠道设置入口与“上次未提醒”状态；正文固定最小四类文案，不含会话内容。
- Review 修复：新事件不再被 `setOnlyAlertOnce` 压掉，摘要静默；PendingIntent 用会话级 data/action 区分，不再依赖进程内 1000 计数；点击通知先展开收起侧栏、清除非空搜索（官方清除按钮优先，输入框兜底）、展开工作区分组，确认 `data-conversation-session` 实际选中后才成功，失败 Toast。
- 点击：通知 extra → 切到 DSH 页 → `window.__tanukiOpenSession` 展开侧栏、清除搜索过滤、展开分组并点击对应行；最多重试 15 次，确认实际选中才成功，失败 Toast 且停留在 DSH 页。
- 验证：`compileModernDebugKotlin`、完整 `testModernDebugUnitTest`（223 tests / 0 failures / 0 errors / 0 skipped）与 `lintModernDebug` BUILD SUCCESSFUL；新增 `ChatNotificationPolicyTest` 10 项、`ChatNotificationRoutingTest` 4 项、`DshChatProtocolTest` 4 项；`node scripts/test-chat-session-open.mjs`（收起侧栏 + 非空搜索 + 其他会话）与注入脚本 5 类合成帧模拟通过。
- 已验证：PR #22 的 Host checks 与 Android build+JVM+lint 均 pass；Review 修复提交 `21cc811` 与搜索过滤修复提交 `462d266` 再次全绿（首轮既有 `NpmAdaptiveWorkerPoolTest` 并发时序失败，重跑通过，未改该测试），全部 Review thread 已 resolve。
- 待验证：真实设备/模拟器的 Android 13 权限弹窗、通知展示/group/summary、点击切会话、后台/息屏送达、ROM 通知/电池/自启动限制、真实 DSH 页面事件；独立 Review、合并与发布未授权/未完成。

### PDF 原生阅读体验打磨（android_agent-35，PR #24 已合并为 0ba11da）

- 分支 `ao/pdf-reading/native-reading`，基线 `main=2f3fac9301c7c3484df85e99091e8f63dddc0a48`，已 rebase 到 `origin/main=b9eeb75`。独立 `/tmp` clone 提交与推送，不合并、不触发 AO Review。
- 阅读器改为单 `PdfRenderer` 会话 + 连续纵向 `LazyColumn`；同屏可见页全部优先加载，再在可见范围外预加载相邻页；按字节 LRU 缓存（堆的 1/8，24–64 MiB），单页渲染受长边 2048、4,000,000 像素与「缓存预算 ÷ (渲染计划页 + 1 在途) ÷ 4」限制，受保护页总字节不超预算；正在组合中绘制的位图延迟回收，离开组合/关闭文档时回收。
- 交互：适应宽度/适应页面切换、双指 1–5x、双击 1x/2.5x、放大后横向平移、页码/总页数、跳页对话框（支持 `12`、`12/50`、`第12页`）；纵向滚动始终可用。放大时按缩放补上下边缘留白，首尾页文字在 1–5× 均可触达；纵向补偿按未缩放列表坐标计算，并延迟到新留白完成测量后执行；放大层由未缩放的阅读视口 `clipToBounds()` 裁剪。
- 错误与限制：512 MiB 文件打开上限、加密/损坏/无页面/内存不足中文提示、单页渲染失败可重试；仍为只读预览，不接 WebView/外部 App，不做编辑/搜索/文本选择。
- 已验证（本轮新跑）：`compileModernDebugKotlin` BUILD SUCCESSFUL；完整 `testModernDebugUnitTest` 365 tests / 0 failures / 0 errors / 0 skipped，`lintModernDebug` 通过；新增 59 项 PDF 策略测试，`ZoomPolicyTest` 6 项与 `PdfRenderPolicyTest` 4 项不回归。Codex P2（缩放首尾可达、非 1× 纵向补偿、同屏 >3 页可见页加载、渲染集合内存预算、固定视口裁剪、分配前驻留预留、预留失败原子性）已修复并补回归。
- 待验证：真机/模拟器大文档连续滚动与内存曲线、捏合/双击/平移手感、视口裁剪截图、5x 清晰度、真实加密/损坏 PDF、深色主题；assemble/Android 仪器未跑。PR 与 head 以 GitHub 页面为准。
### 设备温控与省电策略（android_agent-30，PR #21 已合并为 f5f3bf3）

- 分支 `ao/thermal-policy/device-condition-policy`，已合并 `origin/main=d9b4aa2` 解决冲突；在 `/tmp` 独立 clone 验证/提交；未使用共享 worktree 的 `.git` 写操作。PR [#21](https://github.com/leezzou/tanuki/pull/21) 已创建，head 以 PR 页面为准；未合并、未触发 Review。
- 新增纯策略 `DeviceConditionPolicy` 与 Android 采样/持久化 `DeviceConditionMonitor`：状态 `NORMAL/WARM/HOT/CRITICAL/LOW_BATTERY`，来源为 `PowerManager` thermal status、电池电量/充电和系统低内存；热状态按进入阈值升级、按更低退出边界并持续 30 秒降级，低电用进入/退出阈值（默认 20%/25%）迟滞，低内存有 60 秒解除迟滞。
- 降级：后台更新检查在 HOT/CRITICAL/LOW_BATTERY 由被阻塞的具体 Worker 登记 APK/DSH 待办，恢复后只补跑实际登记过的检查，WorkManager 确认入队且待办代次未变化后才清除（无 Worker 跳过时不发请求，失败保留重试，确认期间重新登记的新待办不被误删，代次计数确认后仍单调递增不复用）；DSH 依赖下载并发按 16/8/4/2/4 降档，自适应池只让多出的 worker 在完成当前归档后退休；日志展示 80/60/40/20/40 行；PDF 预览长边 2048/1600/1280/1024/1280、像素 4M/2.5M/1.6M/1M/1.6M。DSH 任务和前台交互始终 `RUN`，策略不调用取消。
- 兼容：保留原两个唯一周期任务、联网约束、前台服务与唤醒锁；设置入口 `设置 · 运行与后台` 提供总开关与 10/15/20/25/30% 低电阈值；主线程/电池/热回调只向后台合并队列投递刷新请求，采样与写盘在后台串行完成，设置通过单一后台写入器串行保存；冷启动 Worker/设置通过一次性屏障等待 restore 完成，避免默认状态覆盖磁盘待办；状态与实际被跳过的检查写 `files/state/device-condition.json`，设置写 `device-condition-settings.json`，进程重启先恢复再校正。
- 已验证（本轮新跑）：`compileModernDebugKotlin` BUILD SUCCESSFUL；`DeviceConditionPolicyTest` 25 项、`DeviceConditionConfigWriterTest` 1 项、`DeviceConditionRefreshQueueTest` 2 项、`DeviceConditionInitializationTest` 2 项、`NpmDownloadPolicyTest` 7 项、`NpmAdaptiveWorkerPoolTest` 2 项全过；完整 JVM 结果见 [测试报告](TEST-REPORT.md)。
- 待验证：真机/模拟器温控回调、电池广播、低内存、WorkManager 真实调度与重启恢复、通知、设置页 UI、PDF 降级实际像素、assemble/仪器；独立 Review、合并与发布未授权/未完成。
- 状态表、工作矩阵与接入细节见 [设备温控与省电策略](DEVICE-CONDITION-POLICY.md)。

## Phase 2 交付状态

### W1 文档门禁（已合并）

- PR #10 已合并为 `5b01ddf`：恢复 `AGENT-STATE`/`TEST-REPORT`/交接印章，修复 20 个断链，新增 `docs/INTERFACES.md`；历史测试逐节标注“历史”，不改产品代码。

### W2 AO worktree 构建环境（已合并）

- PR #11 已合并为 `9e3e316`。新增 `scripts/ao-worktree-env.sh`、`scripts/test-ao-worktree-env.sh`、`docs/AO-WORKTREE-BUILD.md`；per-worktree `GRADLE_USER_HOME`/`ANDROID_USER_HOME`、只读复用缓存。
- 已验证（W2 当时）：`compileModernDebugKotlin` 与 `testModernDebugUnitTest` BUILD SUCCESSFUL；`test-ao-worktree-env.sh` PASS。未跑完整 `verify-workspace.sh`、lint、设备/仪器、发布签名。

### W3 CI 与 RPC 契约（已合并）

- PR #12 已合并为 `a9aa507`。新增 `.github/workflows/verify.yml`、`scripts/check-rpc-contract.py`、Browser/Phone 客户端回归、`docs/CI.md`；`.github` 进入公开源码包。CI 无 secrets/设备/发布。
- 已验证（W3 当时）：Host/Android 双绿；本轮再次以公开 run 42163963031 核对为 success。

### W4 设备验收门禁（已合并，PR #13/#15）

- PR #13 已合并为 `b2a018d`。新增 `scripts/device-preflight.py`：APK SHA-256/包名/versionCode/versionName、应用与测试签名一致、arm64-v8a、`libproot.so`/`libproot-loader.so`/`libsupervisor.so`、设备 API/ABI、WebView ≥123（可配）、apt/dpkg/Node/PRoot/supervisor 进程、活动安装会话；`--phase post` 校验 install 后版本；证据写私有 `.build/logs/device-preflight-*.json`。
- `scripts/device-lock.py`：基于 `flock` 的 per-serial 进程锁；`verify-workspace.sh` 设备入口经锁重新执行自身，覆盖构建→pre/post 门禁→`install -r`→仪器全程；同 serial 第二任务退出 3，不同 serial 独立；锁 fd 传给子任务，父进程 SIGKILL 后仍由存活子任务持有。
- `scripts/verify-workspace.sh` 设备路径已接入 pre/post 门禁，并新增 `--expect-apk-sha256`、`--expect-cert-sha256`、`--min-webview-major` 参数；失败不安装。
- 已验证（PR #13/#15）：宿主 mock/并发回归 19 tests OK（76.760s，含复审 P1 修复：测试 APK 无版本字段/独立版本、空活动段不误报、API34 Active/Child Session）；`bash -n verify-workspace.sh`、`device-preflight.py --help`、`device-lock.py --help` 通过；未在真实 APK/设备上执行门禁、锁、构建、安装或仪器。PR #15 已合并为 `14d2884`，补充测试 APK 无版本字段、空活动段与 API34 会话前缀回归。

### W5 研发治理与合并前门禁（PR #14，待独立 Review）

- 规范：`AGENTS.md` 只保留通用入口/角色/合并授权，细节归入 [ORCHESTRATOR-RULES](ORCHESTRATOR-RULES.md)、[QA-GATES](QA-GATES.md)、[LESSONS](LESSONS.md)、[code-standards](code-standards.md)；[INTERFACES](INTERFACES.md) 现有契约未改。
- 门禁：只读 `scripts/check-development-gates.py`，接口 `--pr <ref> --session <AO worker id> [--qa-evidence <json>]`；通过 `gh` 读取 PR 身份/最新 head/全部 check，通过 `ao review ls` 读取绑定当前 head 的 review，通过 `ao project get` 核对独立 reviewer；空/排队/失败/跳过 checks、旧 SHA review、自评、跨仓库 PR、未知范围和缺失 QA 证据均拒绝。
- 回归：`scripts/test-development-gates.py` 28 项离线通过（假 `gh`/`ao`，无网络/设备）；已接入 `.github/workflows/verify.yml` 的 Host job，Android CI 边界未改。
- 角色：Orchestrator、Worker、Android QA、规范维护由 DSH/DeepSeek V4.1 Flash 承担；Reviewer 为 Codex/GPT High，只做独立 Review。当前 `ao project get android_agent` 的 orchestrator/worker 均为 `deepseek-harness`，模型 `opencode-go/deepseek-v4.1-flash`，与本轮实际运行一致。
- PR #14 已由 android_agent-13 rebase 到 `origin/main=14d2884` 后重新运行交接/门禁/公开检查/源码包；android_agent-14 先修复 `test-agent-workflow.py` 中锁标记首行假设（循环等待至多 30 秒，未出现仍失败），再按 review 修正 QA 门禁：必需 QA 路径优先于 Markdown 豁免且后缀豁免仅限根目录，`app/src/main/assets/{browser,phone}/SKILL.md` 补拒绝回归；并在 AO 查询与 QA 校验后、输出 PASS 前再次核对 `state,isDraft,headRefOid`。目标用例 10/10、完整 19 项与门禁 28 项通过。独立 Review、合并和发布未授权/未完成。最终 head 的 Host/Android CI 以 PR 页面为准。

### F1 文件查看器内置预览（PR #16 已合并为 41609cb）

- 分支 `ao/tanuki-android-dsh/workspace-file-preview`，基线 `cff09a3`，PR [#16](https://github.com/leezzou/tanuki/pull/16)，已合并为 `41609cb`。初始代码提交 `dd3807b`。
- `WorkspaceFileManager.FileKind` 增加 `PDF`/`DOCX`/`DOC`；`.pdf`、`.docx`、`.doc` 由纯分类函数映射，`.doc` 在界面明确提示“暂不支持内置预览，可用其他应用打开”，不冒充 DOCX。
- PDF：`ParcelFileDescriptor` + `PdfRenderer` 逐页渲染，上一页/下一页、页码、共享缩放平移；加密/损坏/无页中文提示；渲染在 IO 线程，Bitmap 及时回收，长边 ≤2048、总像素 ≤4,000,000。
- DOCX：`ZipFile` + `XmlPullParser` 只读 `word/document.xml`，用 `nextToken()` 处理 `w:p/w:t/w:br/w:cr/w:tab`，拒绝 `DOCDECL`，未定义实体返回中文错误，保留标准实体/数字字符引用/CDATA；限制归档、条目数、总解压、正文和文本长度；界面提示复杂排版/页眉页脚/图片不会显示。
- 图片与 PDF 共用 `Zoomable`/`ZoomPolicy`：1.0–5.0x、围绕手指缩放、平移边界、双击 1x/2.5x、切换文件/页面重置；`WorkspaceVideoPlayer.kt` 未改。
- 验证：`compileModernDebugKotlin` 与 `testModernDebugUnitTest` BUILD SUCCESSFUL；126 JVM，0 失败/错误/跳过（本轮新增分类、DOCX、缩放、渲染上限回归）。未跑 lint/assemble/仪器/真机。

### 视频全屏允许旋转（android_agent-17，PR #17 待 Review）

- `VideoFullscreenPolicy`（新增，纯 Kotlin）：`FULL_SENSOR = 10`（等价 `ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR`），`enter()` 返回 10 并只记录首次进入前的方向，重复进入不覆盖，`exit()` 返回原方向后清空，未进入返回 `null`。全屏期间允许竖屏/横屏随设备旋转，不强制横屏。
- `WorkspaceVideoPlayer`：进入视频全屏记录 `hostActivity.requestedOrientation` 并请求 `FULL_SENSOR`，退出全屏/返回/Composable 销毁时恢复；非视频全屏与找不到 Activity 时不改方向；播放器仍复用同一 `VideoView`/`MediaPlayer`，播放状态、位置、倍速逻辑未改；`WorkspaceFileViewer.kt` 视频调用签名未改。
- 证据：PR [#17](https://github.com/leezzou/tanuki/pull/17)（base `main`，分支 `ao/video-fullscreen-rotatio/full-sensor`）；已 rebase 到 `origin/main` `41609cb`，功能提交 `8ce9882`，head 与 CI 以 PR 页面为准；rebase 后本地 132 tests / 0 failures，`VideoFullscreenPolicyTest` 6/6。未做设备旋转验收、未触发独立 Review、未合并/发布。

### DSH 已安装版本管理（android_agent-25，待 PR/Review）

- 新增纯 Kotlin `DshVersions`：扫描 `engines/<version>`，要求目录、`.installed`、`node_modules/@deepseek-ai/dsh/lib/bin.js` 及中间目录均未被符号链接重定向；输出版本、实际占用、安装时间、当前/备用、`lockVerified` 与校验状态。`verified` 要求依赖锁 SHA-256 与 `.installed` 一致且存在 `.web-verified`。
- `RuntimeComponents.installedDshDetails()` 提供完整清单；`switchDsh(version)` 在服务停止且无活动操作时，先校验基础环境、目标 Node 兼容性与目标依赖锁，并要求当前程序仍完整安装；再用 `DataSnapshots.create` 保存当前数据快照，最后只改写 `installed.json` 的 `dsh`。兼容性/锁/快照/写入失败均保留当前指针与旧程序目录；目标低于当前版本时返回“旧版切换需先恢复匹配的数据快照”，不绕过既有数据兼容规则。
- 新对话框 `DshVersionManagerDialog` 列出当前/备用、占用、安装时间、总占用；非当前版本可确认卸载（保留用户数据），当前版本禁用卸载；旧版切换按钮禁用并显示快照说明；TanukiUi/MainActivity 只加入口、回调与 `DSH_SWITCH` 前台服务动作。
- 本轮验证：`:app:compileModernDebugKotlin` 与 `:app:testModernDebugUnitTest` BUILD SUCCESSFUL；151 JVM / 0 失败/错误/跳过，新增 `DshVersionsTest` 7 项与 `DshSwitchTest` 12 项；`:app:lintModernDebug` BUILD SUCCESSFUL。未跑 assemble/仪器/设备；PR #18、CI、独立 Review 待办。

### DSH 运行失败分类与安全模式（android_agent-28，PR #25 已合并为 5fd1ab4）

- 分支 `ao/runtime-safe-mode/root`，PR [#25](https://github.com/leezzou/tanuki/pull/25)，功能提交 `b4a6ef5`（rebase 后 `dc0494b`），基线 `2f3fac9301c7c3484df85e99091e8f63dddc0a48`；在 `/tmp/tanuki-safe-mode` 独立 clone 验证/提交，不使用共享 worktree 的 `.git` 写操作。
- 新增纯 Kotlin `RuntimeFailurePolicy`：`LAUNCH_FAILED/STARTUP_FAILED/CRASHED_EARLY/CRASHED_LATE/KILLED/NO_SPACE` 分类；normal 下连续两次 `STARTUP_FAILED`/`CRASHED_EARLY` 进入安全模式；`rescue [--from-default-profile web]` 救援参数与独立 `safe-home`/`safe-dsh`；显式试用 normal 进入 2 分钟 `PROBATION`，失败回安全模式、稳定后转 normal；自动重启最多 3 次、2s/4s/8s 退避，`KILLED`/`NO_SPACE` 不自动重启；`state/failure-state.json` 持久化并支持进程重启恢复。
- `RuntimeManager` 记录启动模式与官方就绪时间，按证据分类失败；安全模式不加载用户插件/配置、不挂载浏览器/phone 工具，项目工作区仍为原 `/workspaces`，不删除用户数据；`RuntimeService` 新增 `RECOVERY_TRIAL`/`RECOVERY_SAFE`，自动重启等待期间保持前台；UI 显示失败类型/安全模式/试用并提供退出试用入口。
- 本轮验证：`/tmp` clone 中 `:app:compileModernDebugKotlin` BUILD SUCCESSFUL；rebase 到 `origin/main=f5debf5` 后 `:app:compileModernDebugKotlin`、完整 `:app:testModernDebugUnitTest` 与 `:app:lintModernDebug` BUILD SUCCESSFUL，265 tests / 0 failures / 0 errors / 0 skipped（40 个结果文件），新增 `RuntimeFailurePolicyTest` 32/32（rebase 前 DSH-only 237）。构建时因 Maven Central DNS 不稳定，仅用临时 `/tmp/mirror.init.gradle` 镜像，未提交、未改仓库配置。P1 复审修复：`timedOut` 在 noSpace/launch 之后优先判 `STARTUP_FAILED`，清理产生的 143/137 不再覆盖分类；RuntimeManager 在超时时不把清理退出码作为原始证据；新增 143/137、ready 行后 HTTP 超时与 ENOSPC 优先级回归。P2 复审修复：PROBATION 且上次服务活动时，进程重启一律回 SAFE，不用重开 App 的墙钟补足 2 分钟；只有存活稳定计时器提交 NORMAL。安全模式成功不提交 RESTORED_PENDING_CHECK，只有实际加载恢复 userdata 的 normal/probation 启动成功才标记 verified。P2 时间基准修复：日志就绪早于 HTTP 就绪时，`CRASHED_LATE` 仍可能落在实际 probation 窗口内；晚崩溃分支先判窗口，窗口内回 SAFE，不再直接转 NORMAL。PR #25 最新 head 的 push/PR CI 以 PR 页面为准；Android CI 不重建原生 `.so`，不能当设备验收。
- 待验证：真实设备/PRoot rescue、真实 DSH 崩溃/低内存/磁盘满、插件配置隔离、UI 实机/大字体/深色、lint/assemble/仪器；PR、CI、独立 Codex High Review、合并与发布未完成/未授权。

### XLSX 应用内原生预览（android_agent-32，PR #23 已合并为 f5debf5）

- 分支 `ao/xlsx-preview/xlsx-native-preview`（从 `ao/xlsx-preview/root` 创建），基线 `main=2f3fac9301c7c3484df85e99091e8f63dddc0a48`；独立 `/tmp` clone 提交/推送，PR [#23](https://github.com/leezzou/tanuki/pull/23) 已创建，未触发 AO Review。
- 新增 `XlsxWorkbookParser`：`ZipFile` + `XmlPullParser` 只读 workbook/rels/sharedStrings/styles/sheet，不依赖 LibreOffice/外部 App；支持多 sheet、行号/列标表头、冻结行/列、列宽/行高、合并单元格、基础字体/填充/四边边框、数字/百分比/日期/时间/布尔/错误值与公式缓存值、`date1904`。
- 新增 Compose 表格 `XlsxTablePreview`：sheet 切换、LazyColumn 行虚拟化、共享水平滚动、0.5–2.0x 按钮缩放、截断与保真提示；`WorkspaceFileManager.FileKind` 增加 `XLSX` 并接入 `WorkspaceFileViewer`。
- 安全上限：归档 64 MiB、条目 4096、总解压 256 MiB、单条目 32 MiB 且按实际解压字节复核；sheet 32、行 2000、列 128、单表 40000 单元格、全簿 120000 单元格、共享字符串 50000 条/400 万字符、单元格文本 2000 字/全簿 800 万字、XML 深度 64、合并区域 4096、合并数量 4096；合并生成的起点/占位单元格与普通单元格共享单表/全簿预算；标准实体/数字引用保留，冻结区按视口与缩放收缩，单元格字号按样式显示；拒绝 DOCDECL、未定义实体、`..`/绝对越界路径与外部工作表链接，损坏包给中文错误。
- 明确不支持：图表、宏、透视表、外部链接、图片、复杂条件格式、隐藏行列、主题色、富文本多字体与公式重算；不宣称完整 Excel。
- 本轮验证：`compileModernDebugKotlin` 与 `lintModernDebug` BUILD SUCCESSFUL；完整 `testModernDebugUnitTest` BUILD SUCCESSFUL，233 tests / 0 failures / 0 errors / 0 skipped；新增 XLSX 解析/格式化/策略回归与 `.xlsx` 分类回归。未跑 assemble、仪器与真机；真机大表滚动/缩放待验证。


### DOCX 应用内原生预览（android_agent-31，已 rebase 到 0ba11da，PR [#28](https://github.com/leezzou/tanuki/pull/28) 待审）

- 新增 `com.tanuki.storage.docx` 解析层：安全读取 ZIP，解析 `document.xml`、`styles.xml`、`numbering.xml`、`document.xml.rels` 与正文引用的 `word/media/` 图片；支持段落/标题/列表、粗体/斜体/下划线/字号/颜色、表格（列宽/跨列/底纹/表头）、DrawingML/VML 内嵌图片、纸张与页边距、显式分页。
- 新增 `ui/DocxLayout.kt` 纯 Kotlin 页面几何/分页/图片采样策略与 `ui/WorkspaceDocxPreview.kt` Compose 分页渲染；查看器 DOCX 分支改为 `DocxPreview`；`WorkspaceFileManager.readDocx()` 在既有 IO 锁内调用解析器；删除被替代的 `DocxTextExtractor`。
- 安全：归档 64 MiB、条目 4096、声明总解压 256 MiB、单条目 32 MiB、正文实际解压 16 MiB、辅助 XML 8 MiB、单图 16 MiB/总量 48 MiB/256 张、XML 元素 300k/深度 200/文字 2M、块 6000/单元格 20k；拒绝路径穿越/重复条目/DOCTYPE/未定义实体/不安全关系目标，外部关系不联网；图片解码长边 4096、单图 4M 像素、总 12M 像素。
- 保真边界：明确不支持复杂域、宏、嵌入对象、文本框/艺术字、页眉页脚、脚注尾注、批注修订、多栏、浮动图片精确位置、精确分页与跨页表头；为手机可读性正文按 1.9x 放大，分页为近似结果。详见 [DOCX-PREVIEW](DOCX-PREVIEW.md)。
- 已验证（rebase 后本轮新跑）：`compileModernDebugKotlin` BUILD SUCCESSFUL；完整 `testModernDebugUnitTest` BUILD SUCCESSFUL，435 tests / 0 failures / 0 errors / 0 skipped（main 365 - 被替换的 DOCX 纯文本测试 10 + 新增 80），其中新增 DOCX 回归 80 项（Reader 25、Security 22、Paginator 8、LayoutPolicy 25）。rebase 冲突已解决：`WorkspaceFileViewer` 同时保留 DOCX 与 XLSX 分支，`AGENT-STATE`/印章合并温控、XLSX、安全模式、聊天通知、冷启动、PDF 与原生界面记录；五轮 Review P1/P2 已修复（Bitmap 快照释放、复用段落布局与长段落有界分段、Long 列宽归一化、页首 gap、渲染预算截断提示、单元格预算/分段与超高行限制、宽屏居中留白不压窄纸张、超高单元格保留可见文本前缀、极小视口框架容纳整张纸）。
- 待验证：真实设备双指缩放/横向滚动/深色主题/超大文档性能、真实复杂 Word 样本、低 API/真机；lint/assemble/Android 仪器未跑；PR 创建与独立 Codex High Review 待办，不合并、不触发 AO Review。


### 原生界面与官方界面切换（android_agent-33，PR #26 已合并为 d9b4aa2）

- PR [#26](https://github.com/leezzou/tanuki/pull/26)，分支 `ao/ui-switch/native-ui`，已 rebase 到 `main=b9eeb75`；本次 rebase 将原提交压缩为单一提交，保留 main 的 PDF、cold-start、chat-notify、runtime-safe-mode 与 XLSX 文档内容。
- 官方 DSH `0.2.1-rc.5` 没有 REST 会话端点/SSE；复用 `GET /connect?token=...&interactions=none` 换 API Cookie，`POST /api/session/list|projections|page` 读会话与历史；`session/follow`、`session/control`、审批/提问 waterfall 只走 `/api/remote.mux` WebSocket，未实现。
- 默认官方；设置 `ui/dsh-ui` 为 `native` 才尝试原生。原生只显示 append-origin（`surfaceOp=append`）且 `source.kind=user` 的文本；列表失败回退官方并保留偏好；复用同一 DSH 进程/启动 token/WebView，不重启。
- 通知点击：原生偏好下目标会话交给原生控制器，冷启动/已加载均选中目标；原生列表失败时保留 pending，回退官方并等 WebView 连接后仍打开目标；点击当前会话也强制重读历史，且必须本次通知已发起刷新后才消费；官方模式仍走 WebView 路由。
- P1：`/connect` 网络 I/O 不持 monitor，`reset()` 只短暂同步并递增代次，旧请求不能发布 Cookie。P2：401 重试必须属于当前代次；过滤 `surfaceOp=replace` 的上下文替换副本与注入上下文。
- 已验证（rebase 到 `0ba11da` 后新跑）：`compileModernDebugKotlin` BUILD SUCCESSFUL；完整 `testModernDebugUnitTest` 393 tests / 0 failures / 0 errors / 0 skipped；`lintModernDebug` BUILD SUCCESSFUL；新增 `DshUiSwitchTest` 10 项、`DshNativeApiTest` 12 项、`DshNativeControllerTest` 6 项。
- 待验证：真实设备/PRoot DSH `/connect`/HTTP、WebSocket 实时流/审批/发送、服务重启 Cookie、低版本缺端点回退；未跑 assemble/仪器/真机。最终 head CI 与独立 Pi Review 待 PR 页面。

## 已实现、已验证与待验证

- 已验证（本轮 android_agent-34 新跑）：`compileModernDebugKotlin`、`assembleModernDebug -x buildNativeRuntime`、远程分支 `testModernDebugUnitTest` 229/0/0/0；合入 `main=5fd1ab4` 后完整 JVM 289/0/0/0（41 结果文件）；合入冷启动/PDF/原生界面/温控/DOCX后完整 JVM 535/0/0/0（65 结果文件）、`lintModernDebug` 0 errors/95 warnings；新增 `RemoteHostTest` 24 项与 `DshRemoteIsolationTest` 4 项。未跑设备/真实 SSH/仪器。
- 已验证（本轮 android_agent-31 新跑，merge `f5f3bf3` 后）：`compileModernDebugKotlin` BUILD SUCCESSFUL；完整 `testModernDebugUnitTest` BUILD SUCCESSFUL，494 tests / 0 failures / 0 errors / 0 skipped（main 424 - 旧 DOCX 文本测试 10 + 新增 80）；新增 DOCX 回归覆盖样式/列表/表格/图片/损坏包/zip bomb/超大文档/分页/图片预算/渲染预算。`lintModernDebug` 通过；未跑 assemble/仪器/真机。
- 已验证（本轮 android_agent-30 合并 main 后新跑）：完整 `testModernDebugUnitTest` 与 `lintModernDebug` 组合 BUILD SUCCESSFUL（2m17s，35 tasks），424 tests / 0 failures / 0 errors / 0 skipped（59 个结果文件）；新增 `DeviceConditionPolicyTest` 25 项、`DeviceConditionConfigWriterTest` 1 项、`DeviceConditionRefreshQueueTest` 2 项、`DeviceConditionInitializationTest` 2 项与下载外部上限 1 项；lint 0 Error。未跑 assemble/仪器/真机。
- 已验证（本轮 android_agent-33 新跑，rebase 到 `0ba11da`）：完整 `testModernDebugUnitTest` 与 `lintModernDebug` BUILD SUCCESSFUL；393 tests / 0 failures / 0 errors / 0 skipped；新增 `DshUiSwitchTest` 10 项、`DshNativeApiTest` 12 项、`DshNativeControllerTest` 6 项。
- 已验证（本轮 android_agent-35 新跑）：rebase 到 `origin/main=b9eeb75` 后 `compileModernDebugKotlin` BUILD SUCCESSFUL；完整 `testModernDebugUnitTest` 365 tests / 0 failures / 0 errors / 0 skipped、`lintModernDebug` 通过（新增 59 项 PDF 缓存/预加载/内存上限/跳页/视口/缩放可达性/预算联合/驻留预留策略测试）。
- 已验证（合并后本轮新跑）：`-x buildNativeRuntime` 下 `compileModernDebugKotlin`、完整 `testModernDebugUnitTest` 与 `lintModernDebug` BUILD SUCCESSFUL；306 tests / 0 failures / 0 errors / 0 skipped（含 `EarlyRuntimeStartTest` 17 项、`DshStartupConfigTest` 6 项，以及 main 的安全模式/聊天通知/XLSX 回归）；`node scripts/test-dsh-path-cache.mjs` 10/10 场景 PASS。未跑 assemble/设备/真实 PRoot DSH 启动。
- 已验证（PR #22 分支 android_agent-29 新跑）：`compileModernDebugKotlin`、完整 `testModernDebugUnitTest` 与 `lintModernDebug` BUILD SUCCESSFUL；223 tests / 0 failures / 0 errors / 0 skipped（既有 205 + 新增 18），新增 `ChatNotificationPolicyTest` 10 项、`ChatNotificationRoutingTest` 4 项与 `DshChatProtocolTest` 4 项；`node scripts/test-chat-session-open.mjs` 通过。未跑 assemble/仪器/真实设备/ROM。
- 已验证（本轮 android_agent-32 新跑）：`compileModernDebugKotlin` BUILD SUCCESSFUL；完整 `testModernDebugUnitTest` BUILD SUCCESSFUL，233 tests / 0 failures / 0 errors / 0 skipped；新增 XLSX 解析 18 项、格式化 4 项、缩放策略 5 项与 `.xlsx` 分类 1 项；`lintModernDebug` BUILD SUCCESSFUL。未跑 assemble/设备/仪器。
- 已验证（本轮 android_agent-28 新跑）：`/tmp` 独立 clone 中 `compileModernDebugKotlin` 与 rebase 到 `origin/main=f5debf5` 后完整 `testModernDebugUnitTest` BUILD SUCCESSFUL，265 tests / 0 failures / 0 errors / 0 skipped；新增 `RuntimeFailurePolicyTest` 32/32。未跑 lint/assemble/设备/仪器。
- 已验证（本轮 android_agent-32 新跑）：`compileModernDebugKotlin` BUILD SUCCESSFUL；完整 `testModernDebugUnitTest` BUILD SUCCESSFUL，233 tests / 0 failures / 0 errors / 0 skipped；新增 XLSX 解析 18 项、格式化 4 项、缩放策略 5 项与 `.xlsx` 分类 1 项；`lintModernDebug` BUILD SUCCESSFUL。未跑 assemble/设备/仪器。
- 已验证（android_agent-24 当时）：`compileModernDebugKotlin` BUILD SUCCESSFUL；`testModernDebugUnitTest` BUILD SUCCESSFUL，174 tests / 0 failures / 0 errors / 0 skipped，新增 `BackgroundUpdateChecksTest` 23 项全过。未跑 lint/assemble/设备/真实网络。
- 已验证（本轮 android_agent-17 新跑）：rebase 到 `41609cb` 后 `:app:testModernDebugUnitTest --rerun-tasks` BUILD SUCCESSFUL（53m 56s，28 tasks executed），132 tests / 0 failures / 0 errors / 0 skipped，`VideoFullscreenPolicyTest` 6 项全过；`test-development-gates.py` 28 项 OK；`check-handoff.py`、`check-publication.py --git`、`package-source.py` 通过。`cff09a3` 基线上 110 项与 `d5b83fe` 的 CI 4/4 属 rebase 前结果。
- 已验证（本轮 android_agent-23 新跑）：基线 `5c680b9` 上 `compileModernDebugKotlin` BUILD SUCCESSFUL；完整 `:app:testModernDebugUnitTest` 155 tests / 0 failures / 0 errors / 0 skipped；Npm/Download/OperationProgress 目标回归 61 项通过。`cff09a3`/`41609cb` 基线上的 110/132 项与旧 CI 属历史结果，不是本轮复跑。
- 已验证（android_agent-25 收尾新跑）：rebase 到 `origin/main=6136451` 后组合 #19+#20，`compileModernDebugKotlin` 与完整 JVM BUILD SUCCESSFUL，205 tests / 0 failures / 0 errors / 0 skipped；`NpmAdaptiveWorkerPoolTest` 2/2。
- 已验证（android_agent-17 历史）：rebase 到 `41609cb` 后 132 tests / 0 failures；`test-development-gates.py` 28 项 OK；`check-handoff.py`、`check-publication.py --git`、`package-source.py` 通过。
- 已验证（F1 本轮）：`compileModernDebugKotlin` 与 `testModernDebugUnitTest` BUILD SUCCESSFUL；126 JVM，0 失败/错误/跳过；新增分类、DOCX 合成 zip（多段/空段/DOCTYPE/未定义实体/数字引用/CDATA/超限/损坏）、缩放 clamp/边界/重置、PDF 渲染上限回归。真实命令与限制见 1647 交接。
- 已验证（PR #14 rebase 后复跑，历史）：`check-handoff.py --record ...` 刷新印章；`check-handoff.py` 当前；`test-development-gates.py` 28 项离线通过（含 app 内 SKILL.md 必须 QA、根目录 Markdown 豁免、AO 查询阶段 head/状态变化拒绝）；`test-agent-workflow.py` 19 项与目标 device-lock 用例 10/10 通过；`check-publication.py --git` 0 findings；`package-source.py` 生成源码包与校验清单（工作材料，不提交）。真实输出见 1415 交接记录。
- 已验证（#13/#15、W8 当时，历史）：宿主 mock/并发回归 19 tests OK（76.760s）；设备门禁脚本 `--help` 与 `bash -n` 通过；main/CI/设备 metadata 只读核对。
- 已验证（W5 前轮，历史）：24 项门禁离线回归；真实 `gh`/`ao` 只读 CLI 格式核对。
- 已验证（W2/W3/W1 前轮，历史）：worktree 构建引导、CI/RPC 契约、文档门禁恢复。
- 待验证：远程主机 PR #29 的真实 sshd/Keystore/token、Cookie 两主机隔离设备路径与 UI；DSH 聊天通知的 Android 13 权限/渠道/group/summary、点击切会话、后台/息屏与 MIUI/其他 ROM、真实 DSH WebSocket 事件；XLSX 真机大表/手势/深色；安全模式真机 rescue/崩溃/低内存/磁盘满；DSH 安装 PR #20、后台更新、视频全屏、F1、设备门禁、0.1.8 与真机/MIUI 仍按原记录待验证。
- 待验证：XLSX 原生预览的真机/模拟器双向滚动、冻结区手势、缩放可读性、大表性能、深色主题与真实 Excel/WPS 文件；DSH 聊天通知的真实设备/模拟器权限弹窗、通知展示/group/summary、点击切会话与后台/息屏送达；DSH 冷启动的真实 PRoot 启动/首帧时序与设置开关；PDF 原生阅读的大文档滚动/内存、加密/损坏样本与缩放手感；原生界面的真机/PRoot 切换、通知点击与回退；设备温控/低电/低内存的真机策略与 WorkManager 恢复；DSH 安全模式在真实设备/PRoot 的 rescue 启动、真实崩溃/低内存/磁盘满与插件隔离；DSH 安装 PR #20 的独立 Codex High Review、真实设备/弱网镜像切换与 4000 包规模验收；后台更新检查的真实 WorkManager 调度/通知/网络与设备徽标；视频全屏 PR #17 的设备旋转/恢复验收；F1 的设备手势/PDF/DOCX 验收；DOCX 原生预览的真实设备双指缩放/横向滚动/深色主题/超大文档与真实复杂 Word 样本；真实设备上的锁、pre/post 门禁与 14 类回归；0.1.8 的 Ubuntu/Node/DSH 安装、真实模型任务与真机/MIUI/低 API；底部导航点击。
- 用户暂停：API Key 保存；真实模型/账号验收；真机验收。


## 环境与阻塞

- 当前分支：DOCX 原生预览分支 `ao/docx-preview/root`，已 merge `main=f5f3bf3`；独立 `/tmp` clone 验证/提交，PR [#28](https://github.com/leezzou/tanuki/pull/28) 待审。
- 当前分支：温控分支 `ao/thermal-policy/device-condition-policy`，已合并 `origin/main=d9b4aa2` 解决冲突；在 `/tmp/tanuki-thermal-30` 独立 clone 验证/提交，未使用共享 worktree 的 `.git` 写操作。
- 当前分支：后台更新检查分支 `ao/workmanager-updates/background-update-check`（已 rebase 到 `origin/main=9b32051`，独立 `/tmp` clone 验证/提交）；视频任务分支 `ao/video-fullscreen-rotatio/full-sensor`（基于 `origin/main` `41609cb`，rebase 解决文档冲突后 `--force-with-lease` 推送）；F1 分支 `ao/tanuki-android-dsh/workspace-file-preview` 已随 PR #16 合并。两轮均在独立 `/tmp` clone 完成验证，未使用共享 worktree 的 `.git` 写操作。
- 当前分支：远程主机 `ao/remote-host/remote-ssh`（基线 `2f3fac9`，独立 `/tmp` clone 提交/推送，PR #29）；DSH 聊天通知 `ao/dsh-chat-notify/root`（PR #22）；冷启动 `ao/cold-start/root` 已合并 `b9eeb75`；安全模式/XLSX 已合入 main；DSH 安装 PR #20 已合并；视频/F1 已合并。以上均在独立 `/tmp` clone 验证/提交，不使用共享 worktree 的 `.git` 写操作。
- 当前分支：DSH 安装分支 `ao/dsh-install-2/install-resilience`，已 rebase 到 `origin/main=6136451`；在 `/tmp` 干净 clone 验证/提交，未使用共享 worktree 的 `.git` 写操作。视频/F1 分支已随 PR #17/#16 合并。
- 温控分支已随 PR #21 合并为 `f5f3bf3`；原生界面分支已随 PR #26 合并为 `d9b4aa2`；PDF 阅读分支已随 PR #24 合并为 `0ba11da`；冷启动分支已随 PR #27 合并为 `b9eeb75`；聊天通知分支已随 PR #22 合并为 `c2f1b44`；安全模式分支已随 PR #25 合并为 `5fd1ab4`；XLSX 预览分支已随 PR #23 合并为 `f5debf5`；真机 QA 另行授权。
- 设备：本轮未启动/停止、未安装、未跑仪器；0.1.8 冒烟证据来自 android_agent-10 的独立 QA 会话。
- 无当前产品修复阻塞；PR #21、PR #17 的独立 Review、CI 与合并由用户和编排器推进，Agent 不自行合并/发布。

## 下一步

1. 设备温控/省电 PR [#21](https://github.com/leezzou/tanuki/pull/21)：等待独立 Review 与合并；真机温控/低电量/恢复 QA 另行授权。
2. 远程主机 PR [#29](https://github.com/leezzou/tanuki/pull/29)：8 条 review 已修复并 resolve；复跑 CI 后由 Orchestrator 安排独立 Review；真实 sshd/Keystore/Cookie/UI QA 另行授权。
3. 原生界面 PR [#26](https://github.com/leezzou/tanuki/pull/26) 已合并 `d9b4aa2`：真机/PRoot DSH 原生界面验收另行授权。
4. DSH 聊天通知 PR [#22](https://github.com/leezzou/tanuki/pull/22)：复跑 JVM/Node/lint 与 CI 后安排独立 Review；设备 QA 另行授权。
5. 安全模式 `5fd1ab4`、XLSX `f5debf5`、PDF `0ba11da` 已合并：真机崩溃/低内存/磁盘满、大表/手势/缩放、PDF 连续滚动 QA 另行授权。
6. 若 DSH 升级调整 `$events` 或侧栏 `data-*`，先更新 `docs/INTERFACES.md` 与回归，再改实现。
7. PR #20 已合并 `2f3fac9`、PR #19 已合并 `6136451`：真实设备/弱网/WorkManager 验收另行授权。
8. 视频全屏 PR #17 与 F1 设备 QA、0.1.8 真实环境/模型验证另开授权；API Key 保存与真实模型/账号验收等待用户恢复。
1. DOCX 预览 PR [#28](https://github.com/leezzou/tanuki/pull/28)：已 merge `main=f5f3bf3`，等待当前完整 SHA 的独立 Codex High Review 与设备 QA（双指缩放、横向滚动、复杂 DOCX、超大文档）；不合并、不触发 AO Review。
2. 温控 PR [#21](https://github.com/leezzou/tanuki/pull/21) 已合并为 `f5f3bf3`：真机温控/低电/低内存、WorkManager 调度与重启恢复由独立 QA 另行授权。
3. 原生界面 PR [#26](https://github.com/leezzou/tanuki/pull/26) 已合并为 `d9b4aa2`：真机/PRoot 原生界面切换、通知点击与失败回退验收另行授权。
4. XLSX 预览 PR [#23](https://github.com/leezzou/tanuki/pull/23) 已合并为 `f5debf5`：真机大表/手势/深色主题 QA 另行授权。
5. DSH 安全模式 PR [#25](https://github.com/leezzou/tanuki/pull/25) 已合并为 `5fd1ab4`：真实设备/PRoot rescue、崩溃/低内存/磁盘满与插件隔离 QA 另行授权。
6. DSH 聊天通知 PR [#22](https://github.com/leezzou/tanuki/pull/22) 已合并为 `c2f1b44`：Android 13 权限/渠道/分组、点击切会话、后台息屏与 ROM QA 另行授权。
7. DSH 冷启动 PR [#27](https://github.com/leezzou/tanuki/pull/27) 已合并为 `b9eeb75`：真实 PRoot DSH 启动/首帧时序、设置开关与冷启动计时 QA 另行授权。
8. PDF 原生阅读 PR [#24](https://github.com/leezzou/tanuki/pull/24) 已合并为 `0ba11da`：大文档滚动/内存、加密/损坏样本与缩放手感 QA 另行授权。
9. PR #20：由 Orchestrator 触发当前完整 SHA 的独立 Codex High Review；真实设备/弱网镜像切换与 4000 包规模 QA 另行授权。
10. Review 意见逐条修复并重审；通过且用户明确授权后才合并，不代合、不直推 main。
11. 合并后在 main 复跑 `check-handoff.py`、`check-publication.py --git`、`test-development-gates.py` 与适用的 `verify-workspace.sh`。
12. PR #19 已合并为 `6136451`：后台更新检查的设备 QA 与真实 WorkManager/通知验证另行授权。
13. 视频全屏 PR #17 的设备旋转/恢复验收与 F1 设备 QA 仍按原记录执行；设备须无真实任务并记录 AVD/serial。
14. 0.1.8 的 Ubuntu/Node/DSH/真实模型验证另开授权任务；API Key 保存与真实模型/账号验收继续等待用户恢复。
