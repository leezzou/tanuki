# Tanuki 当前状态

当前交接：[设备温控与省电策略](handoffs/20261109-1802-device-condition-policy.md)（android_agent-30，独立 worktree，基线 `2f3fac9`）。并行记录：[DSH 已安装版本管理](handoffs/20261109-1539-dsh-installed-version-manager.md)（android_agent-25，独立 worktree，基线 `5c680b9`）、[后台更新检查（APK 30 分钟 / DSH 24 小时）](handoffs/20261109-1535-background-update-check.md)（android_agent-24；PR [#19](https://github.com/leezzou/tanuki/pull/19) 已合并为 `6136451`）与 [DSH 安装提速与国内镜像](handoffs/20261109-1539-dsh-install-resilience.md)（android_agent-23；PR [#20](https://github.com/leezzou/tanuki/pull/20) 待审）。上一记录 [视频全屏允许旋转（FULL_SENSOR）](handoffs/20261106-1735-video-fullscreen-full-sensor.md)（android_agent-17；PR [#17](https://github.com/leezzou/tanuki/pull/17) 已合并为 `5c680b9`）。[文件查看器内置预览](handoffs/20261106-1647-workspace-file-preview.md)（F1，android_agent-16；PR #16 已合并为 `41609cb`）、W5 的 [研发治理与合并前门禁](handoffs/20261106-1415-governance-gates.md)（PR #14 已合并为 `cff09a3`）与 W8 的 [Android QA 能力核查报告](handoffs/20261106-1357-android-qa-readiness-audit.md) 保留。本轮 android_agent-25 实现已安装 DSH 清单、非当前版本卸载、已安装版本切换与版本管理对话框；android_agent-23 实现 DSH 安装提速与国内镜像；均不改协议、AO 配置、既有安全/暂停项或 0.1.8 附件。

## 0.1.8 交付与仓库基线

- `main` 已前进到 `41609cb`（PR #16 文件预览合并提交；此前 `cff09a3` 为 PR #14 治理门禁合并提交）。`v0.1.8` tag 仍为 `c196bbf4d211604bc2076de450a8ed2d52ddde99`（PR #8 浏览器输入线程修复合并提交）。用户已发布 0.1.8；Agent 不代合 main、不创建 tag/Release。
- `artifacts/releases/0.1.8/` 六附件齐全。W1 只读复核 APK：`com.tanuki.modern.workspacecheck`、`versionName=0.1.8`、`versionCode=10`、minSdk26、targetSdk34、arm64-v8a；开发证书 SHA-256 `371dc301e7bb4d5cb2840510855f191eeb2f1ce4d058618361419409654cd84f`；APK 10,479,850 字节，SHA-256 `eb695a12778652075e0324c65486534dab98848f073daa14ea631291b69b146e`。公开 Release 的 `app-update.json` 与 APK 均返回 HTTP 200。
- 0.1.8 已有独立 QA 会话 android_agent-10 的首轮设备冒烟：`emulator-5572` = `Pixel_7_API_34_arm64`（API34/ARM64，WebView `159.0.8133.0`）；`install -r` 从 0.1.7/code9 升级到 0.1.8/code10（约 1.57s，保留数据），设备 base.apk 与发布件 SHA-256 一致；冷启动 `LaunchState COLD`、`TotalTime 1616ms`；首启停在 Ubuntu 安装页 1/4，未执行安装；应用内浏览器加载 GitHub Release 页，WebView 正常；logcat 无 FATAL/ANR。
- 该冒烟只覆盖安装/启动/基础 UI/WebView；Ubuntu/Node/基础工具/DSH 安装、真实模型任务、底部导航点击、真机/MIUI/低 API 均未验证，不能写成 0.1.8 已验收。0.1.7 及更早的 104 JVM、54 设备方法等仍是历史记录，未在本轮复跑。
- main CI 以 PR #13 合并后的 Actions 结果为准；Android job 使用 `-x buildNativeRuntime` 且仓库不跟踪 `jniLibs`，CI APK 无原生 `.so`，不能用于设备验收。

### 后台更新检查（android_agent-24，PR #19 待审）

- 分支 `ao/workmanager-updates/background-update-check`，已 rebase 到 `origin/main=9b32051`；独立 `/tmp` clone 验证/提交，未使用共享 worktree 的 `.git` 写操作。
- WorkManager 依赖 `androidx.work:work-runtime-ktx:2.9.1`；两个唯一周期任务：APK `tanuki-apk-update-check` 每 30 分钟、DSH `dsh-engine-update-check` 每 24 小时，均要求联网。
- APK 只读稳定 Release `app-update.json` 并与 `BuildConfig.VERSION_CODE` 比较；DSH 先读 npmmirror，失败/结构不完整回退官方 npm，连接与读取超时各 12 秒；均只提示，不自动下载/安装，不改 DSH 下载逻辑。
- 状态分离：`apk-update-check.json` 与 `dsh-update-check.json` 各自保存 `lastCheckedAt/latest/available/lastError/notifiedVersion`；不复用 `AppUpdateState` 或 `RuntimeManager` 的 `UpdateState`。
- 前台：APK 更新卡片和 DSH 版本卡片显示“有更新：`<version>`”徽标；手动检查保留，仅在检查完成事件同步独立后台快照（不重复联网），开始时间早于后台结果的旧结果不覆盖较新快照；APK 判定与恢复统一按 `versionCode`；DSH 快照在仓库恢复及实际安装版本变化时只按当前安装身份重算 `installedVersion/available`，保留远端 latest 与通知去重。后台：`updates` 通知渠道按版本去重；Android 13+ 检查 `POST_NOTIFICATIONS`，手动检查入口顺带申请；失败静默记录。
- 已验证：`compileModernDebugKotlin` BUILD SUCCESSFUL；`testModernDebugUnitTest` BUILD SUCCESSFUL，174 tests / 0 failures / 0 errors / 0 skipped；新增 `BackgroundUpdateChecksTest` 23 项。
- 待验证：真实 WorkManager 调度/重启恢复、通知权限与展示、真实 GitHub/npmmirror/npm 请求、设备 UI；lint/assemble/仪器未跑。独立 Codex High Review、合并与发布未授权/未完成。
### DSH 安装提速与国内镜像（android_agent-23，PR #20 待审）

- 分支 `ao/dsh-install-2/install-resilience`，已 rebase 到 `origin/main=6136451`（含 #19 更新检查）；保留 #20 的下载/镜像/并发改动与 #19 的更新检查接线，未重做 P1。
- 锁快速路径：saved/installed/bundled 锁按 version+SRI+platform 命中后先做完整依赖图校验，通过即安装；Node/npm 升级不再使锁缓存失效。全部候选不可用才解析元数据。
- 无锁解析：npmmirror 优先、官方兜底；npm `fetch-timeout=15000ms`，命令上限 180s；镜像生成的锁把 `resolved` 归一化回官方 registry，SRI 不变。
- 下载：自动模式国内镜像优先、官方最后；401/403/404/410 快速换源，429/408/425/5xx 在单请求内 500ms/1000ms 指数退避后降级源；单请求 connect/read 30s，Range/206 续传，临时文件 SHA-512 通过后原子 rename。
- 并发：默认 8 路，连续成功后升至 16；每 host 信号量上限 8；已校验归档跨重试保留。
- 已验证（本轮新跑）：`compileModernDebugKotlin` BUILD SUCCESSFUL；rebase 后完整 `testModernDebugUnitTest` 205 tests / 0 failures / 0 errors / 0 skipped；目标 Npm/Download 回归 61 项通过，`NpmAdaptiveWorkerPoolTest` 升降档 2/2。
- 待验证：真实 Android 设备/PRoot 首次 DSH 安装、弱网与镜像 404/429 注入、4000 包端到端吞吐；未跑 lint/assemble/仪器/真机。

### 设备温控与省电策略（android_agent-30，待 PR）

- 分支 `ao/thermal-policy/device-condition-policy`，基于 `main=2f3fac9`，在 `/tmp` 独立 clone 验证/提交；未使用共享 worktree 的 `.git` 写操作。
- 新增纯策略 `DeviceConditionPolicy` 与 Android 采样/持久化 `DeviceConditionMonitor`：状态 `NORMAL/WARM/HOT/CRITICAL/LOW_BATTERY`，来源为 `PowerManager` thermal status、电池电量/充电和系统低内存；热状态按进入阈值升级、按更低退出边界并持续 30 秒降级，低电用进入/退出阈值（默认 20%/25%）迟滞，低内存有 60 秒解除迟滞。
- 降级：后台更新检查在 HOT/CRITICAL/LOW_BATTERY 延后，恢复后由唯一一次性 WorkManager 任务补跑；DSH 依赖下载并发按 16/8/4/2/4 降档，自适应池只让多出的 worker 在完成当前归档后退休；日志展示 80/60/40/20/40 行；PDF 预览长边 2048/1600/1280/1024/1280、像素 4M/2.5M/1.6M/1M/1.6M。DSH 任务和前台交互始终 `RUN`，策略不调用取消。
- 兼容：保留原两个唯一周期任务、联网约束、前台服务与唤醒锁；设置入口 `设置 · 运行与后台` 提供总开关与 10/15/20/25/30% 低电阈值；状态与延后工作写 `files/state/device-condition.json`，设置写 `device-condition-settings.json`，进程重启先恢复再校正。
- 已验证（本轮新跑）：`compileModernDebugKotlin` BUILD SUCCESSFUL；`DeviceConditionPolicyTest` 16 项、`NpmDownloadPolicyTest` 7 项、`NpmAdaptiveWorkerPoolTest` 2 项全过；完整 JVM 结果见 [测试报告](TEST-REPORT.md)。
- 待验证：真机/模拟器温控回调、电池广播、低内存、WorkManager 真实调度与重启恢复、通知、设置页 UI、PDF 降级实际像素、lint/assemble/仪器；独立 Review、合并与发布未授权/未完成。
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

## 已实现、已验证与待验证

- 已验证（本轮 android_agent-30 新跑）：`compileModernDebugKotlin` BUILD SUCCESSFUL；完整 `testModernDebugUnitTest --rerun-tasks` BUILD SUCCESSFUL（3m13s），222 tests / 0 failures / 0 errors / 0 skipped，其中新增 `DeviceConditionPolicyTest` 16 项与下载外部上限 1 项；未跑 lint/assemble/仪器/真机。
- 已验证（android_agent-24 历史）：`compileModernDebugKotlin` BUILD SUCCESSFUL；`testModernDebugUnitTest` BUILD SUCCESSFUL，174 tests / 0 failures / 0 errors / 0 skipped，新增 `BackgroundUpdateChecksTest` 23 项全过。未跑 lint/assemble/设备/真实网络。
- 已验证（本轮 android_agent-17 新跑）：rebase 到 `41609cb` 后 `:app:testModernDebugUnitTest --rerun-tasks` BUILD SUCCESSFUL（53m 56s，28 tasks executed），132 tests / 0 failures / 0 errors / 0 skipped，`VideoFullscreenPolicyTest` 6 项全过；`test-development-gates.py` 28 项 OK；`check-handoff.py`、`check-publication.py --git`、`package-source.py` 通过。`cff09a3` 基线上 110 项与 `d5b83fe` 的 CI 4/4 属 rebase 前结果。
- 已验证（本轮 android_agent-23 新跑）：基线 `5c680b9` 上 `compileModernDebugKotlin` BUILD SUCCESSFUL；完整 `:app:testModernDebugUnitTest` 155 tests / 0 failures / 0 errors / 0 skipped；Npm/Download/OperationProgress 目标回归 61 项通过。`cff09a3`/`41609cb` 基线上的 110/132 项与旧 CI 属历史结果，不是本轮复跑。
- 已验证（android_agent-25 收尾新跑）：rebase 到 `origin/main=6136451` 后组合 #19+#20，`compileModernDebugKotlin` 与完整 JVM BUILD SUCCESSFUL，205 tests / 0 failures / 0 errors / 0 skipped；`NpmAdaptiveWorkerPoolTest` 2/2。
- 已验证（android_agent-17 历史）：rebase 到 `41609cb` 后 132 tests / 0 failures；`test-development-gates.py` 28 项 OK；`check-handoff.py`、`check-publication.py --git`、`package-source.py` 通过。
- 已验证（F1 本轮）：`compileModernDebugKotlin` 与 `testModernDebugUnitTest` BUILD SUCCESSFUL；126 JVM，0 失败/错误/跳过；新增分类、DOCX 合成 zip（多段/空段/DOCTYPE/未定义实体/数字引用/CDATA/超限/损坏）、缩放 clamp/边界/重置、PDF 渲染上限回归。真实命令与限制见 1647 交接。
- 已验证（PR #14 rebase 后复跑，历史）：`check-handoff.py --record ...` 刷新印章；`check-handoff.py` 当前；`test-development-gates.py` 28 项离线通过（含 app 内 SKILL.md 必须 QA、根目录 Markdown 豁免、AO 查询阶段 head/状态变化拒绝）；`test-agent-workflow.py` 19 项与目标 device-lock 用例 10/10 通过；`check-publication.py --git` 0 findings；`package-source.py` 生成源码包与校验清单（工作材料，不提交）。真实输出见 1415 交接记录。
- 已验证（#13/#15、W8 当时，历史）：宿主 mock/并发回归 19 tests OK（76.760s）；设备门禁脚本 `--help` 与 `bash -n` 通过；main/CI/设备 metadata 只读核对。
- 已验证（W5 前轮，历史）：24 项门禁离线回归；真实 `gh`/`ao` 只读 CLI 格式核对。
- 已验证（W2/W3/W1 前轮，历史）：worktree 构建引导、CI/RPC 契约、文档门禁恢复。
- 待验证：设备温控/省电策略的真机温控、低电/低内存注入、WorkManager 真实调度与重启恢复、设置页与通知；DSH 安装 PR #20 的独立 Codex High Review、真实设备/弱网镜像切换与 4000 包规模验收；后台更新检查的真实 WorkManager 调度/通知/网络与设备徽标；视频全屏 PR #17 的设备旋转/恢复验收；F1 的设备手势/PDF/DOCX 验收；真实设备上的锁、pre/post 门禁与 14 类回归；0.1.8 的 Ubuntu/Node/DSH 安装、真实模型任务与真机/MIUI/低 API；底部导航点击。
- 用户暂停：API Key 保存；真实模型/账号验收；真机验收。

## 环境与阻塞

- 当前分支：后台更新检查分支 `ao/workmanager-updates/background-update-check`（已 rebase 到 `origin/main=9b32051`，独立 `/tmp` clone 验证/提交）；视频任务分支 `ao/video-fullscreen-rotatio/full-sensor`（基于 `origin/main` `41609cb`，rebase 解决文档冲突后 `--force-with-lease` 推送）；F1 分支 `ao/tanuki-android-dsh/workspace-file-preview` 已随 PR #16 合并。两轮均在独立 `/tmp` clone 完成验证，未使用共享 worktree 的 `.git` 写操作。
- 当前分支：DSH 安装分支 `ao/dsh-install-2/install-resilience`，已 rebase 到 `origin/main=6136451`；在 `/tmp` 干净 clone 验证/提交，未使用共享 worktree 的 `.git` 写操作。视频/F1 分支已随 PR #17/#16 合并。温控分支 `ao/thermal-policy/device-condition-policy` 基于 `main=2f3fac9`，同样在 `/tmp` 独立 clone 验证/提交。
- 设备：本轮未启动/停止、未安装、未跑仪器；0.1.8 冒烟证据来自 android_agent-10 的独立 QA 会话。
- 无当前产品修复阻塞；PR #17 的独立 Review、CI 与合并由用户和编排器推进，Agent 不自行合并/发布。

## 下一步

1. 温控 PR：等待独立 Codex High Review 与真机 QA；不自行合并、不触发 Review、不发布。
2. PR #20：由 Orchestrator 触发当前完整 SHA 的独立 Codex High Review；真实设备/弱网镜像切换与 4000 包规模 QA 另行授权。
2. Review 意见逐条修复并重审；通过且用户明确授权后才合并，不代合、不直推 main。
3. 合并后在 main 复跑 `check-handoff.py`、`check-publication.py --git`、`test-development-gates.py` 与适用的 `verify-workspace.sh`。
4. PR #19 已合并为 `6136451`：后台更新检查的设备 QA 与真实 WorkManager/通知验证另行授权。
5. 视频全屏 PR #17 的设备旋转/恢复验收与 F1 设备 QA 仍按原记录执行；设备须无真实任务并记录 AVD/serial。
6. 0.1.8 的 Ubuntu/Node/DSH/真实模型验证另开授权任务；API Key 保存与真实模型/账号验收继续等待用户恢复。
