# 交接：20261109-1539-dsh-install-resilience

- Agent/会话标识：android_agent-23（DSH Worker）
- 时间：2026-11-09 15:39 CST（UTC+8）
- 状态：已完成（实现与 JVM 验证通过；PR [#20](https://github.com/leezzou/tanuki/pull/20) 待独立 Review）
- 前一记录：[视频全屏允许旋转（FULL_SENSOR）](20261106-1735-video-fullscreen-full-sensor.md)

> 每任Agent创建唯一记录，不覆盖前任。开始填写目标/范围；结束或中断前补齐证据与可执行下一步，并更新 `docs/AGENT-STATE.md`。只读Agent交回同格式文本，由主Agent保存。不要包含真实Key、Cookie、token、会话/备份或私有原始日志。

## 用户目标与范围

- 用户本次实际要求：解决 DSH 安装慢/易失败，接近 Ubuntu/Node 的“丝滑”，国内网络可用。
- 本次包含：锁快速路径；无锁时 npmmirror 优先、官方兜底的元数据解析；传递依赖走国内镜像并保持 SHA-512/SRI；镜像白名单与路径映射；404/410 快速换源、429/5xx 退避；自适应并发 8–16、单 host ≤8；30s 单请求超时、2 次指数退避、Range/206 续传、临时文件校验后原子提交；真实进度与中文错误；离线 JVM 回归。
- 本次不包含：Ubuntu/Node 运行时改造、协议或 RPC 变更、真实设备/Android 仪器验证、合并、发布、AO Review 触发。
- 用户改变或暂停的要求：无。保持既有 API Key 保存、真机验收暂停项。

## 当前状态

- 已实现：上述 DSH 依赖解析、镜像映射、下载重试/续传、并发与进度改动。
- 已验证：`compileModernDebugKotlin` 通过；完整 JVM 套件 155 tests / 0 failures / 0 errors / 0 skipped；目标 Npm/Download/OperationProgress 回归 61 项通过；`test-dsh-lock.py` 通过。
- 待验证：真实 Android 设备上的 DSH 首次安装/断网重试/镜像切换；真实 4000 包规模吞吐对比；独立 Review 与 CI。
- 当前目标包名、分支/无Git状态、是否有运行中任务：AO 会话 worktree（android_agent-23），分支 `ao/dsh-install-2/root`，基线 `5c680b99075b87953a74cf22cfaee89608d058c1`；PR [#20](https://github.com/leezzou/tanuki/pull/20)，源分支 `ao/dsh-install-2/install-resilience`，head 以 PR 页面为准。
- 关键事实与可定位源码/公共文档：`app/src/main/kotlin/com/tanuki/runtime/NpmPackageDownloads.kt`、`NpmDependencyLocks.kt`、`NpmSources.kt`、`NpmArchiveMapping.kt`、`NpmDownloadPolicy.kt`、`NpmLockNormalizer.kt`、`app/src/main/kotlin/com/tanuki/download/Downloader.kt`、`DownloadRetryPolicy.kt`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/runtime/NpmArchiveMapping.kt`（新增） | 唯一的官方锁路径→镜像白名单映射；拒绝任意 host/端口/参数/编码穿越；反向把华为/腾讯 registry 前缀还原为官方路径。 |
| `app/src/main/kotlin/com/tanuki/runtime/NpmDownloadPolicy.kt`（新增） | 8→16 自适应并发策略；每 host 8 路信号量；无锁元数据源顺序与 15s 单请求/180s 命令超时。 |
| `app/src/main/kotlin/com/tanuki/runtime/NpmLockNormalizer.kt`（新增） | 镜像解析出的 `resolved` 恢复为官方地址，SRI/版本不变；未知 host 直接拒绝。 |
| `app/src/main/kotlin/com/tanuki/download/DownloadRetryPolicy.kt`（新增） | 3 次尝试（2 次重试）、500/1000ms 指数退避、404/410 缺失、429/5xx 可重试分类。 |
| `app/src/main/kotlin/com/tanuki/download/Downloader.kt` | 429/5xx 分类退避；404/410 立即上抛；临时文件哈希通过后 `ATOMIC_MOVE` 提交；Range 解析改为可测试函数；保留 30s connect/read 与部分文件续传。 |
| `app/src/main/kotlin/com/tanuki/runtime/NpmDependencyLocks.kt` | 锁身份改为 version+integrity+platform（Node/npm 升级不再强制重新解析）；saved→installed→bundled 候选逐个校验，全部不可用才解析；重定向缓存返回 null；缓存写失败不阻断已验证安装。 |
| `app/src/main/kotlin/com/tanuki/runtime/NpmSources.kt` | 自动模式国内镜像优先、官方最后；手动选择仍优先用户来源；暴露 host 供单 host 限流。 |
| `app/src/main/kotlin/com/tanuki/runtime/NpmPackageDownloads.kt` | 元数据 npmmirror 优先、官方兜底并归一化锁；并发 8 起步按成功增长到 16；单 host ≤8；404/410 快速换源、429/5xx 退避降级；阶段拆分与中文可操作错误。 |
| `app/src/test/kotlin/com/tanuki/NpmArchiveMappingTest.kt`（新增） | 映射/非法 host/端口/参数/穿越/白名单回归。 |
| `app/src/test/kotlin/com/tanuki/NpmLockNormalizerTest.kt`（新增） | 镜像锁归一化、SRI/版本保持、未知 host/非 HTTPS 拒绝。 |
| `app/src/test/kotlin/com/tanuki/NpmDownloadPolicyTest.kt`（新增） | 退避值、状态分类、元数据源顺序/超时、并发 8–16、单 host 8。 |
| `app/src/test/kotlin/com/tanuki/DownloaderRetryTest.kt`（新增） | 429/503 重试与退避、404/410 不重试、重试耗尽、断点续传 Range。 |
| `app/src/test/kotlin/com/tanuki/DownloadRangeTest.kt`（新增） | Content-Range 合法/非法解析、坏 206 整包重下。 |
| `app/src/test/kotlin/com/tanuki/NpmDependencyLocksTest.kt` | 适配锁身份/候选列表；新增 saved 锁不兼容时回退 installed、不触发元数据；重定向缓存安全返回 null。 |
| `app/src/test/kotlin/com/tanuki/NpmUnknownLengthDownloadTest.kt` | 截断响应现在在 2 次退避后以 IOException 结束，更新断言。 |
| `docs/AGENT-STATE.md` | 增加本轮交付/验证/待验证状态。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew :app:compileModernDebugKotlin --console=plain` | 通过（BUILD SUCCESSFUL） | 首次编译暴露注释嵌套/构造器重载问题，修复后通过；未跑 lint/assemble。 |
| `./gradlew :app:testModernDebugUnitTest --tests 'com.tanuki.Npm*' --tests 'com.tanuki.Download*' --tests 'com.tanuki.OperationProgressTest'` | 通过（目标回归 61 tests，0 failed） | 覆盖映射、归一化、锁快速路径、退避、Range、并发、缓存命中；不联网、不装设备。 |
| `./gradlew :app:testModernDebugUnitTest --rerun-tasks` | 通过（BUILD SUCCESSFUL，28 tasks executed） | 31 个测试类，155 tests / 0 failures / 0 errors / 0 skipped；强制重跑，不联网、不装设备。并发用例改用 IO dispatcher，避免 2 核 CI 上 `Dispatchers.Default` 并行度不足导致抖动。 |
| `python3 scripts/test-dsh-lock.py` | 通过（PASS） | 模拟 linux/arm64/glibc 三个 Node 版本，560 archives；官方 HTTPS/SHA-512 条目 686，3 个负例；不下载、不装设备。 |
| `python3 scripts/check-handoff.py --record ...` / `check-handoff.py` | 通过 | 记录印章刷新；当前交接检查 468 public files。 |
| `python3 scripts/package-source.py` | 通过 | 提交前生成源码包与校验清单（469 public files）；本记录不写包自身哈希，工作材料不提交。 |

- 哪些是前一轮结果，哪些是本次新验证：本轮首次编译/目标 JVM 测试是新验证；完整 JVM、锁脚本、交接/源码包检查待补。前轮 132 tests 不作为本轮结果。
- 未测试、超时、失败和暂未复现的情况（不能抹掉）：未在 Android 设备/真实 PRoot 环境执行 DSH 安装；未做真实弱网/镜像 404/429 注入；未做 4000 包端到端吞吐基准；未跑 lint、assemble、仪器测试、真机/MIUI/低 API。

## 未完成与暂停项

- 未完成：PR #20 的 CI/独立 Review；真实设备/弱网 QA。完整 JVM、`test-dsh-lock.py`、交接/公开检查已完成。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收继续等待用户恢复；本轮未触碰。

## 下一步

1. 运行完整 `:app:testModernDebugUnitTest` 并记录真实 tests/failures；运行 `scripts/test-dsh-lock.py`、`check-handoff.py`、`check-publication.py --git`、`package-source.py`。
2. PR [#20](https://github.com/leezzou/tanuki/pull/20) 等待 CI 与独立 Codex High Review；不合并、不触发 AO Review。
3. Review 意见逐条修复后重审当前完整 SHA；真实设备/弱网 QA 另行授权后执行。

## 约束与风险

- 数据/设备/权限边界：未读取密钥/Cookie/用户数据；未启动/安装/清空设备；未改 Ubuntu/Node/协议；未合并/发布。
- 关键决策及理由：锁身份去掉 Node/npm 以避免升级触发元数据重解析，改由每次依赖图校验兜底；自动模式官方最后，手动选择保留用户意图；镜像只映射路径不改 SRI。
- 失败尝试与避免重复踩坑的结论：`Downloader` 第三个函数参数会改变既有尾随 lambda 绑定，必须保留 2 参重载；Kotlin 块注释可嵌套，注释中不要写未转义的 `/*`；华为/腾讯 registry 带路径前缀，反向归一化必须剥离。

## 产物与校验

- 新/旧APK、源码ZIP及校验文件，哪些确实更新：本轮不产出 APK；源码包在提交前按脚本刷新，工作材料不提交。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未签名、未发布。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：保留既有历史交接与验证记录；本轮新增记录，不改写前任记录。若后续确认旧记录已无独有未完成事项，再由当轮维护方迁移后清理。
- 文档或脚本变化后仍需刷新交接印章、重新生成源码ZIP；不要把源码ZIP自身哈希写回归档内形成循环。
