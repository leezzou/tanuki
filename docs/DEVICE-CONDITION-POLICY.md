# 设备温控与省电策略

本文记录 Tanuki 在温度、电量或内存压力下的降级规则、接入点和验证边界。源码是最终事实；本文与实现冲突时先查证并同时修正两者。

## 1. 目标与边界

- 设备偏热、过热、严重过热或低电时，降低**非关键后台工作**，包括后台更新检查、DSH 依赖下载并发、日志展示行数和内置 PDF 预览渲染上限。
- **不暂停、不取消正在执行的 DSH 任务和前台交互**。策略不调用 `RuntimeManager.cancel()`、不停止前台服务、不取消已开始的下载归档。
- 降级是渐进和可恢复的：下载池只让多出的 worker 在完成当前归档后退休；后台更新检查延后，状态恢复后由 WorkManager 补跑。
- 状态、延后工作和阈值持久化到应用私有目录，进程重启后先恢复再按新信号校正。

## 2. 设备状态

纯策略实现：`app/src/main/kotlin/com/tanuki/power/DeviceConditionPolicy.kt`。
Android 采样与持久化：`app/src/main/kotlin/com/tanuki/power/DeviceConditionMonitor.kt`。

| 状态 | 进入条件 | 退出条件 | 主要降级 |
|---|---|---|---|
| `NORMAL` | 热状态 `NONE`、电量健康、无内存压力、策略开启 | — | 全部按正常额度 |
| `WARM` | `PowerManager` 热状态 ≥ `LIGHT`，或系统 `lowMemory` | 热状态回到 `NONE` 且内存压力解除，分别持续 30 秒/60 秒 | 依赖下载 8 路、日志 60 行、PDF 预览 ≤1600px |
| `HOT` | 热状态 ≥ `MODERATE` | 回到更低退出边界并持续 30 秒：≤ `LIGHT` 降为 `WARM`，直接回到 `NONE` 则降为 `NORMAL` | 依赖下载 4 路、日志 40 行、PDF 预览 ≤1280px；延后后台更新检查 |
| `CRITICAL` | 热状态 ≥ `SEVERE`（含 `CRITICAL/EMERGENCY/SHUTDOWN`） | 回到更低退出边界并持续 30 秒：≤ `MODERATE` 降为 `HOT`，≤ `LIGHT` 降为 `WARM`，`NONE` 降为 `NORMAL` | 依赖下载 2 路、日志 20 行、PDF 预览 ≤1024px；延后后台更新检查 |
| `LOW_BATTERY` | 电量 ≤ 进入阈值且未充电 | 电量 ≥ 退出阈值（进入阈值 +5）或开始充电 | 依赖下载 4 路、日志 40 行、PDF 预览 ≤1280px；延后后台更新检查 |

- 主状态优先级：`CRITICAL > HOT > WARM > LOW_BATTERY > NORMAL`。过热与低电同时存在时保留 `HOT/CRITICAL`，原因集合同时包含 `LOW_BATTERY`，额度取更严格者。
- 低内存本身把组合状态抬到 `WARM`，原因标记为 `LOW_MEMORY`；热状态字段 `thermalCondition` 只保存热信号，避免内存恢复时被热迟滞额外拖住。
- 电量未知（`-1`）不触发低电；充电中即使电量很低也不触发低电。
- API < 29 没有 `PowerManager` thermal status，热状态按 `NONE` 处理；电量、充电和低内存仍生效。

## 3. 工作矩阵

| 工作类别 | NORMAL | WARM | HOT | CRITICAL | LOW_BATTERY |
|---|---|---|---|---|---|
| DSH 任务 / 前台交互 | 运行 | 运行 | 运行 | 运行 | 运行 |
| 用户主动 APK 下载 | 运行 | 运行（单路） | 运行（单路） | 运行（单路） | 运行（单路） |
| 后台更新检查 | 运行 | 运行 | 延后 | 延后 | 延后 |
| DSH 依赖下载并发 | 16 | 8 | 4 | 2 | 4 |
| 日志展示行数 | 80 | 60 | 40 | 20 | 40 |
| PDF 预览长边上限 | 2048 | 1600 | 1280 | 1024 | 1280 |
| PDF 预览像素上限 | 4,000,000 | 2,500,000 | 1,600,000 | 1,000,000 | 1,600,000 |

- 依赖下载额度是**上限**，不是强制目标；`NpmConcurrencyPolicy` 原有的 8→16 自适应增长、故障回落和单 host 8 路限流继续生效。
- 额度降低后，`NpmAdaptiveWorkerPool` 只让多出的 worker 在领取下一个归档前退出；当前归档继续完成，不会中断已开始的下载。
- 后台更新检查延后时 Worker 返回 `Result.success()`，不抛异常、不触发 WorkManager 重试轰炸；状态恢复后 `UpdateCheckScheduler.resumeDeferred()` 用唯一的一次性任务补跑，仓库的最小查询间隔继续阻止紧邻重复请求。
- PDF 预览降级只影响新渲染页；已显示页面不清空。图片、DOCX 和视频预览本轮未接入降级。

## 4. 接入点

| 位置 | 作用 |
|---|---|
| `TanukiApp` | 创建并启动 `DeviceConditionMonitor`，绑定后台工作恢复回调 |
| `DeviceConditionMonitor` | 监听 thermal status、电池广播、充电状态和系统低内存，每 30 秒兜底采样；读写状态与设置 |
| `UpdateCheckWorkers` | 两个周期 Worker 在开始前调用 `beginBackgroundWork()`；不允许时登记延后并直接成功返回 |
| `UpdateCheckScheduler` | 保留原周期任务；新增设备恢复后的唯一一次性补跑任务 |
| `NpmConcurrencyPolicy` | 新增 `capProvider` 外部上限；`current()` 取自适应目标与设备额度的较小值 |
| `NpmPackageDownloads` | 把当前设备状态的 `workerCap` 注入依赖下载策略 |
| `TanukiUiModel` / `MainActivity` | 订阅设备状态，传给状态卡片、设置页、日志展示和 PDF 预览 |
| `WorkspaceFileViewer` / `WorkspacePdfPreview` | 把预览长边和像素上限传到 `PdfRenderPolicy.targetSize()` |
| `RuntimeService` | 未改动：温控策略不停止前台服务、不取消 DSH 任务、不改唤醒锁流程 |

## 5. 开关与阈值

- 设置入口：`设置 · 运行与后台 · 温控与省电策略`。
- 总开关关闭后立即回到 `NORMAL` 额度，并恢复被延后的后台更新检查；不会触发任何 DSH 任务取消。
- 低电阈值提供 10%/15%/20%/25%/30% 五档，默认 20%；退出阈值固定为进入阈值 +5，减少阈值附近抖动。
- 代码级阈值集中在 `DeviceConditionConfig`：热等级、低电进入/退出、热/内存迟滞时间、下载并发、日志行数、预览尺寸/像素。构造时会校验单调性和取值范围。
- 设置持久化文件：`files/state/device-condition-settings.json`；运行状态：`files/state/device-condition.json`。

## 6. 进程重启恢复

- 启动时先读取上次状态（含主状态、热状态、低电标记、延后工作集合和迟滞起点），再注册监听并立即采样。
- 如果重启时设备已经恢复，策略仍按迟滞规则等待观察期结束；结束后把 `deferredWork` 中真正恢复的类别通过回调报告给 `UpdateCheckScheduler.resumeDeferred()`。
- 如果重启时设备仍热/低电，延后状态保持不变，不会因为进程重启而误跑后台网络检查。
- 状态写入使用 `Paths.atomic()`（`AtomicFile`），内容不变时不重复写盘。

## 7. 兼容性与安全边界

- **前台服务**：`RuntimeService` 的启动、停止、唤醒锁和通知逻辑未改；温控不会通过服务发送 `STOP` 或取消任务。
- **WorkManager**：保留两个唯一周期任务及联网约束；新增的补跑任务也是唯一命名 + `REPLACE`，不会堆积。
- **下载**：只限并发，不限单请求；Range/206 续传、SRI 校验、镜像切换和缓存复用不变。
- **用户可见性**：非正常状态在首页显示原因和降级额度；设置页显示当前状态、阈值和延后说明。
- **隐私**：只读取系统热状态、电量/充电和内存压力，不采集温度传感器原始值、位置或账号信息。

## 8. 验证与未验证

- 已验证：`compileModernDebugKotlin` 成功；`DeviceConditionPolicyTest` 16 项、`NpmDownloadPolicyTest` 7 项、`NpmAdaptiveWorkerPoolTest` 2 项通过；完整 JVM 套件结果见交接记录与 [TEST-REPORT](TEST-REPORT.md)。
- 未验证：真机/模拟器真实温控回调、电池广播、系统低内存、WorkManager 真实调度与重启恢复、Android 13+ 通知、设置页 UI、PDF 预览降级实际像素、lint/assemble/仪器测试。
- 真机温控需要独立 QA 会话在无真实任务的设备上加热/充电/低电注入后验证；本轮 JVM 结果不能替代设备验收。
