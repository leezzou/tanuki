# 设备温控与省电策略

本文记录 Tanuki 在温度、电量或内存压力下的降级规则、接入点和验证边界。源码是最终事实；本文与实现冲突时先查证并同时修正两者。

## 1. 目标与边界

- 设备偏热、过热、严重过热或低电时，降低**非关键后台工作**，包括后台更新检查、DSH 依赖下载并发、日志展示行数和内置 PDF 预览渲染上限。
- **不暂停、不取消正在执行的 DSH 任务和前台交互**。策略不调用 `RuntimeManager.cancel()`、不停止前台服务、不取消已开始的下载归档。
- 降级是渐进和可恢复的：下载池只让多出的 worker 在完成当前归档后退休；后台更新检查仅在 Worker 确认被状态阻塞时登记具体待办，状态恢复后只补跑实际被跳过的检查。
- 状态、实际被跳过的检查和阈值持久化到应用私有目录，进程重启后先恢复再按新信号校正。

## 2. 设备状态

纯策略实现：`app/src/main/kotlin/com/tanuki/power/DeviceConditionPolicy.kt`。
Android 采样与持久化：`app/src/main/kotlin/com/tanuki/power/DeviceConditionMonitor.kt`。

| 状态 | 进入条件 | 退出条件 | 主要降级 |
|---|---|---|---|
| `NORMAL` | 热状态 `NONE`、电量健康、无内存压力、策略开启 | — | 全部按正常额度 |
| `WARM` | `PowerManager` 热状态 ≥ `LIGHT`，或系统 `lowMemory` | 热状态回到 `NONE` 且内存压力解除，分别持续 30 秒/60 秒 | 依赖下载 8 路、日志 60 行、PDF 预览 ≤1600px |
| `HOT` | 热状态 ≥ `MODERATE` | 回到更低退出边界并持续 30 秒：≤ `LIGHT` 降为 `WARM`，直接回到 `NONE` 则降为 `NORMAL` | 依赖下载 4 路、日志 40 行、PDF 预览 ≤1280px；后台检查由 Worker 登记跳过 |
| `CRITICAL` | 热状态 ≥ `SEVERE`（含 `CRITICAL/EMERGENCY/SHUTDOWN`） | 回到更低退出边界并持续 30 秒：≤ `MODERATE` 降为 `HOT`，≤ `LIGHT` 降为 `WARM`，`NONE` 降为 `NORMAL` | 依赖下载 2 路、日志 20 行、PDF 预览 ≤1024px；后台检查由 Worker 登记跳过 |
| `LOW_BATTERY` | 电量 ≤ 进入阈值且未充电 | 电量 ≥ 退出阈值（进入阈值 +5）或开始充电 | 依赖下载 4 路、日志 40 行、PDF 预览 ≤1280px；后台检查由 Worker 登记跳过 |

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
- 后台更新检查被阻塞时，具体 Worker 调用 `beginBackgroundWork(APK_UPDATE_CHECK/DSH_UPDATE_CHECK)`：只登记自己这一项并返回 `Result.success()`，不抛异常、不触发 WorkManager 重试轰炸。恢复时 `UpdateCheckScheduler.resumeDeferred()` 只入队实际登记过的检查；没有 Worker 被跳过时不会发起任何补跑请求。
- 待办只有在 `resumeDeferred()` 确认 WorkManager 唯一任务已持久入队后才清除；入队失败或进程在该窗口结束都会保留待办，下一次采样继续重试，不会丢失。
- 每项待办带登记代次；确认只清除与本次入队时代次一致的项。已发放的最大代次单独保留，确认清除待办后也不会从 1 重用，因此“旧确认悬停、另一确认先清除、随后重新登记”的窗口同样不会误删新待办。
- 主线程、电池广播和 thermal 回调只通过 `DeviceConditionRefreshQueue.request()` 投递合并请求；采样、策略评估和 `AtomicFile` 写盘统一在后台 collector 执行。`updatedAtMillis` 不参与持久化比较，常规采样状态未变时不重复写盘。
- 冷启动时 `DeviceConditionInitialization` 建立一次性屏障：Worker 与设置入口先等待配置和 `restore()` 完成，再进入同一状态处理流程，避免 Worker 用默认状态覆盖磁盘待办，或 `restore()` 覆盖 Worker 刚登记的新待办。
- PDF 预览降级只影响新渲染页；已显示页面不清空。图片、DOCX 和视频预览本轮未接入降级。

## 4. 接入点

| 位置 | 作用 |
|---|---|
| `TanukiApp` | 创建并启动 `DeviceConditionMonitor`，绑定后台工作恢复回调 |
| `DeviceConditionMonitor` | 监听 thermal status、电池广播、充电状态和系统低内存；后台串行采样/保存，每 30 秒兜底刷新 |
| `DeviceConditionRefreshQueue` | 合并主线程/广播刷新请求，`request()` 只投递、不执行采样或写盘 |
| `DeviceConditionInitialization` | 一次性初始化屏障；Worker/设置等待 restore 完成后再评估和写盘 |
| `UpdateCheckWorkers` | 两个周期 Worker 分别调用 `beginBackgroundWork(APK_UPDATE_CHECK/DSH_UPDATE_CHECK)`；不允许时只登记自己并直接成功返回 |
| `UpdateCheckScheduler` | 保留原周期任务；恢复时只按实际登记的检查入队对应的唯一一次性任务 |
| `NpmConcurrencyPolicy` | 新增 `capProvider` 外部上限；`current()` 取自适应目标与设备额度的较小值 |
| `NpmPackageDownloads` | 把当前设备状态的 `workerCap` 注入依赖下载策略 |
| `TanukiUiModel` / `MainActivity` | 订阅设备状态，传给状态卡片、设置页、日志展示和 PDF 预览 |
| `WorkspaceFileViewer` / `WorkspacePdfPreview` | 把预览长边和像素上限传到 `PdfRenderPolicy.targetSize()` |
| `RuntimeService` | 未改动：温控策略不停止前台服务、不取消 DSH 任务、不改唤醒锁流程 |

## 5. 开关与阈值

- 设置入口：`设置 · 运行与后台 · 温控与省电策略`。
- 总开关关闭后立即回到 `NORMAL` 额度，并补跑实际登记过的后台检查；不会触发任何 DSH 任务取消。
- 低电阈值提供 10%/15%/20%/25%/30% 五档，默认 20%；退出阈值固定为进入阈值 +5，减少阈值附近抖动。
- 代码级阈值集中在 `DeviceConditionConfig`：热等级、低电进入/退出、热/内存迟滞时间、下载并发、日志行数、预览尺寸/像素。构造时会校验单调性和取值范围。
- 设置持久化文件：`files/state/device-condition-settings.json`；运行状态：`files/state/device-condition.json`。设置修改通过单一后台写入器串行处理：写锁内读取最新 `config` 后原子写入，避免连续选择阈值时旧快照后写覆盖；主线程不等待磁盘。

## 6. 进程重启恢复

- 启动时先读取上次状态（含主状态、热状态、低电标记、实际被跳过的检查集合和迟滞起点），再注册监听并立即采样。冷启动 Worker 与设置入口通过初始化屏障等待该过程完成。
- 如果重启时设备已经恢复，策略仍按迟滞规则等待观察期结束；结束后只把 `deferredChecks` 中实际登记过的 APK/DSH 检查报告给 `UpdateCheckScheduler.resumeDeferred()`。
- 只有调度器返回确认入队集合后，监控才按入队时记录的代次调用 `acknowledgeChecks()` 清除对应待办；若在“已恢复、尚未确认入队”窗口进程结束，重启后待办仍在并再次尝试补跑；确认期间重新登记的同类检查因代次变化而保留。
- 如果重启时设备仍热/低电，已登记的待办保持不变；没有 Worker 登记过时不会因为状态进入受限而凭空补跑。
- 状态写入使用 `Paths.atomic()`（`AtomicFile`），并排除每次采样都变化的 `updatedAtMillis`；内容不变时不重复写盘。

## 7. 兼容性与安全边界

- **前台服务**：`RuntimeService` 的启动、停止、唤醒锁和通知逻辑未改；温控不会通过服务发送 `STOP` 或取消任务。
- **WorkManager**：保留两个唯一周期任务及联网约束；补跑任务按 APK/DSH 分别唯一命名 + `REPLACE`，不会堆积，也不会在无实际待办时发起请求；只有 `Operation` 持久化结果确认成功且待办代次未变化才清除，失败或期间重新登记都保留待办。
- **下载**：只限并发，不限单请求；Range/206 续传、SRI 校验、镜像切换和缓存复用不变。
- **用户可见性**：非正常状态在首页显示原因和降级额度；设置页显示当前状态、阈值和延后说明。
- **隐私**：只读取系统热状态、电量/充电和内存压力，不采集温度传感器原始值、位置或账号信息。

## 8. 验证与未验证

- 已验证：`compileModernDebugKotlin` 成功；`DeviceConditionPolicyTest` 25 项、`DeviceConditionRefreshQueueTest` 2 项、`DeviceConditionInitializationTest` 2 项、`DeviceConditionConfigWriterTest` 1 项、`NpmDownloadPolicyTest` 7 项、`NpmAdaptiveWorkerPoolTest` 2 项通过；完整 JVM 套件 424 tests / 0 failures；`:app:lintModernDebug` 0 个 Error。
- 未验证：真机/模拟器真实温控回调、电池广播、系统低内存、WorkManager 真实调度与重启恢复、Android 13+ 通知、设置页 UI、PDF 预览降级实际像素、assemble/仪器测试。
- 真机温控需要独立 QA 会话在无真实任务的设备上加热/充电/低电注入后验证；本轮 JVM 结果不能替代设备验收。
