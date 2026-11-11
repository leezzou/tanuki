# 首次启动引导

本文件说明 Tanuki 首次启动的四步引导、版本门控、环境门槛、真实准备进度和权限边界。状态判断放在纯 JVM 层，Compose 只负责展示与转发用户动作。

## 1. 目标与原则

- 新用户第一次打开时先知道“这是什么、下一步做什么”，而不是直接落在功能页。
- 每一步只做一件事：欢迎、准备环境、选择界面、完成。
- 四步都可跳过；跳过后首页保留可关闭的“完成设置”提示条，点进去回到第一个未完成步骤。
- 只在真正首次启动时自动展开。大版本升级只给一次可关闭提示，不自动重播整套引导。
- 环境结论、准备阶段和权限计划都是纯函数，可在 JVM 上回归，不依赖设备。

## 2. 四步内容

| 步骤 | 主内容 | 底部动作 |
|---|---|---|
| 欢迎 | 一句话说明可在本机运行，也可连接远程电脑；不出现技术术语 | 继续 / 跳过 |
| 准备环境 | 内存、可用存储、架构、系统版本检测；不满足给具体建议；“一键准备”显示真实阶段进度；失败给中文原因与重试 | 继续（最低要求不满足时禁用）/ 跳过 |
| 选择界面 | 官方网页界面与原生界面二选一，一句话说明差别；提示可稍后在设置修改 | 继续 / 跳过 |
| 完成 | “发第一条消息”“连接远程电脑”两个入口 | 完成 / 稍后再说 |

顶部固定四段进度点，并带无障碍描述（第几步、共几步、是否完成）。界面切换的实际入口为“设置 → 引擎与更新 → 网页与兼容性 → 使用手机界面”；引导说明与此导航层级一致。

## 3. 首次、升级与恢复

记录保存在独立 SharedPreferences 文件 `onboarding` 中，不随 DSH 数据或运行环境清理丢失。

| 记录状态 | 冷启动行为 |
|---|---|
| 空记录（真正首次） | 自动从欢迎页展开 |
| 已打开但未完成，未跳过 | 自动从第一个未完成步骤继续（进程被杀也不丢步骤） |
| 已跳过、未完成 | 不自动展开；首页显示“完成设置”提示条 |
| 已完成、同一版本线 | 不提示 |
| 已完成、产品版本线升级 | 只显示一次“已更新到新版本”提示；点“继续”才打开引导 |
| 记录结构版本不匹配 | 按空记录处理，避免把损坏数据误判为完成 |
| 从旧版本升级安装（首次记录为空） | 直接标记为当前版本已完成，不自动展开；仍可从设置打开 |

升级安装通过系统记录的 `lastUpdateTime` 与 `firstInstallTime` 区分：首次安装两者相同；覆盖安装时间更晚。这样新用户会看到完整引导，老用户更新后不会突然被四步流程拦住。

版本线规则：当前是 `0.x`，`0.1 → 0.2` 视为大版本，`0.1.12 → 0.1.13` 不提示；进入 `1.x` 后只比较主版本号。版本号无法解析时不提示。

关键字段：`last-seen-version`（第一次打开即写入）、`completed-version`、`completed-steps`、`skipped`/`skipped-at`、`banner-dismissed`、`upgrade-notice-seen`。

## 4. 环境门槛

| 项目 | 最低 | 建议 | 不满足时 |
|---|---|---|---|
| 可用存储 | 1.5 GB（与运行时检查一致） | 4 GB | 阻止继续，提示清理或转移文件 |
| 运行内存 | 2 GB | 4 GB | 低于最低值阻止继续，并提示改用远程电脑；偏低只警告 |
| 设备架构 | arm64-v8a | — | 阻止本机准备，提示连接远程电脑 |
| 系统版本 | Android 8.0（API 26） | — | 阻止，提示升级系统或换设备 |

“继续”只在存在阻止项时禁用；“跳过”始终可用。环境页提供“重新检测”，清理存储后无需重启应用。

## 5. “一键准备”的真实进度

复用现有的初始化链路（Ubuntu → 基础工具 → Node.js → pnpm → 官方 DSH → 健康检查），不新建第二套安装器。

- **下载**：使用下载器上报的 `received/total` 计算百分比。
- **解压**：归档总量未知，不显示百分比；显示不定进度和实际已写入 MB。该写入量由 `SafeTar.extract` 的可选回调上报，按 32 MB 节流，避免每写一块就重组整页。
- **校验**：显示当前校验/启动实验阶段，不伪造进度。
- **补齐组件**：apt 上报 `dlstatus`/`pmstatus` 时才显示对应百分比。
- **取消**：保留完整下载，重试可复用。
- **失败**：把网络、空间、校验、权限、取消等底层错误翻译成中文可执行原因，并提供“重试”。未知错误只保留首行短文本，不暴露堆栈或内部路径。

## 6. 权限

| 动作 | 申请时机 | 权限 |
|---|---|---|
| 进入欢迎/选择界面/完成 | 不申请 | 无 |
| 点“一键准备” | 此时才申请，用于前台安装进度；拒绝不阻止安装 | 通知（Android 13+） |
| 发第一条消息、连接远程电脑 | 不申请 | 仅普通网络权限 |
| 文件页真正读写共享存储 | 原有文件流程按需申请 | 存储 |

当前版本没有使用附近设备/蓝牙能力，因此清单不声明，引导也不申请。将来接入时再在 `OnboardingPermissions` 增加动作，避免一次性弹出全部权限。

## 7. 代码位置与可测边界

| 文件 | 职责 |
|---|---|
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingModel.kt` | 步骤、记录、版本门控、状态机、首页提示条（纯 JVM） |
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingEnvironment.kt` | 设备画像、门槛判断、建议文案（纯 JVM） |
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingPrepare.kt` | 准备阶段映射、失败原因、权限计划（纯 JVM） |
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingStore.kt` | SharedPreferences 与真实设备读取（Android 适配） |
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingController.kt` | 状态机与存储、设备信息的薄接线 |
| `app/src/main/kotlin/com/tanuki/ui/OnboardingScreen.kt` | 四步界面、判断结果展示、提示条 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 整屏门控、首页提示条、设置回看入口 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 宿主回调、按需权限、完成后的页面跳转 |
| `app/src/main/kotlin/com/tanuki/storage/SafeTar.kt` | 可选解压写入量回调（默认关闭，不影响既有调用） |

JVM 回归：`OnboardingFlowTest`、`OnboardingEnvironmentTest`、`OnboardingPrepareTest`。

```bash
./gradlew -PtestBuildType=debug -I scripts/workspace-verification.init.gradle \
  -x buildNativeRuntime :app:testModernDebugUnitTest --tests 'com.tanuki.Onboarding*'
```

## 8. 已知未验证

- 未在设备/模拟器上实际点击四步、返回键、提示条关闭与深色/大字体/TalkBack。
- 未实际走完一次真实网络下载、解压和 apt 补齐；解压写入量回调与失败翻译只有 JVM 逻辑回归。
- 未验证升级安装后 `versionName` 变化的真实现场；版本门控只有 JVM 回归。
- 权限弹窗的厂商差异、Android 13 以下通知行为未上设备验证。
