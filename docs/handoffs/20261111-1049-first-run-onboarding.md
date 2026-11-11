# 交接：20261111-1049-first-run-onboarding

- Agent/会话标识：android_agent-68（AO worker，分支 `ao/first-run-onboarding/root`）
- 时间：2026-11-11 10:49 +0800
- 状态：已完成（PR 候选，未合并、未发布）
- 前一记录：[0.1.12 发布准备](20261111-0325-release-0.1.12.md)

> 普通 PR 记录：未修改 `docs/AGENT-STATE.md` 与 `docs/handoffs/CURRENT.json`，共享状态留给集成维护方刷新。

## 用户目标与范围

- 用户本次实际要求：实现真正的首次启动引导，四步（欢迎、准备环境、选择界面、完成）；每步可跳过，跳过后首页保留可关闭的“完成设置”提示条并可回到未完成步骤；权限按需申请；状态逻辑放可测层并补 JVM 回归；PR 交付，不合并、不发布。
- 本次包含：首次/升级/跳过/部分完成的版本门控；内存/存储/架构/系统检测与建议；“一键准备”真实阶段进度；失败中文原因与重试；原生/官方界面选择；完成页两个入口；设置回看入口；解压真实写入量回调；JVM 回归与专题文档。
- 本次不包含：设备/模拟器 UI 验收；真实网络下载、真实 apt/解压/校验；通知权限弹窗的厂商差异；新特性文案/发布说明接入；合并、tag、Release、APK 打包。
- 用户改变或暂停的要求（需保留）：API Key 保存、真实模型/账号验收、真机验收仍暂停；未获授权不得合并或发布。

## 当前状态

- 已实现：上述引导流程、状态机、环境门槛、准备阶段映射、权限计划、首页提示条、设置回看、解压写入量上报。
- 已验证：JVM 全量测试与 lint 通过（见下）；定向引导测试 32 项通过。
- 待验证：设备 UI、真实安装链路、真实权限弹窗、升级安装现场、深色/大字体/TalkBack。
- 当前分支：`ao/first-run-onboarding/root`，基于 `origin/main=62e4c30b4a61636867cfc94736e44fa159f50b9f`；未提交前工作区包含本次改动，无运行中的安装/测试任务。
- 关键事实与可定位源码：`docs/ONBOARDING.md`；纯状态机 `app/src/main/kotlin/com/tanuki/onboarding/OnboardingModel.kt`；环境门槛 `OnboardingEnvironment.kt`；准备/失败/权限 `OnboardingPrepare.kt`；SharedPreferences 与设备读取 `OnboardingStore.kt`；控制器 `OnboardingController.kt`；界面 `app/src/main/kotlin/com/tanuki/ui/OnboardingScreen.kt`。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingModel.kt` | 新增步骤、记录、版本门控、状态机、升级安装默认完成记录、首页提示条；纯 JVM。 |
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingEnvironment.kt` | 新增设备画像与内存/存储/架构/系统门槛、建议文案和字节格式化；纯 JVM。 |
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingPrepare.kt` | 把运行时环境状态映射为下载/解压/校验/补齐阶段；未知总量不给百分比；失败原因中文化；按需权限计划。 |
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingStore.kt` | SharedPreferences 持久化（版本、完成步骤、跳过、提示条、升级提示）与真实设备读取。 |
| `app/src/main/kotlin/com/tanuki/onboarding/OnboardingController.kt` | 状态机、存储、设备信息的薄接线；避免组合期写状态；升级安装不自动展开。 |
| `app/src/main/kotlin/com/tanuki/ui/OnboardingScreen.kt` | 四步整屏 UI、顶部进度点、底部继续/跳过、环境结果、真实阶段进度、失败重试、界面选择、完成入口、首页提示条。 |
| `app/src/main/kotlin/com/tanuki/ui/TanukiUi.kt` | 引导整屏门控；首页提示条；完成页跳转请求；设置“重新查看引导”入口；远程设置直达。 |
| `app/src/main/kotlin/com/tanuki/MainActivity.kt` | 创建引导控制器；按需申请通知权限；准备/取消/重试/完成跳转；升级安装判定。 |
| `app/src/main/kotlin/com/tanuki/storage/SafeTar.kt` | `extract` 增加可选写入量回调；默认关闭，既有调用不变。 |
| `app/src/main/kotlin/com/tanuki/runtime/RuntimeManager.kt` | 解压 Ubuntu 时按 32 MB 节流上报真实写入量，不伪造百分比。 |
| `app/src/test/kotlin/com/tanuki/OnboardingFlowTest.kt` | 首次/非首次/升级安装/大版本提示/跳过/部分完成/提示条/返回键/完成态回归。 |
| `app/src/test/kotlin/com/tanuki/OnboardingEnvironmentTest.kt` | 满足、存储不足、存储偏低、内存不足/偏低、非 ARM64、旧系统与格式化回归。 |
| `app/src/test/kotlin/com/tanuki/OnboardingPrepareTest.kt` | 下载百分比、未知总量、解压/校验不定进度、apt 百分比、失败中文原因、取消复用、权限计划回归。 |
| `docs/ONBOARDING.md` | 新增专题文档：四步、版本规则、环境门槛、真实进度、权限、代码位置、未验证项。 |
| `README.md` | 增加首次启动引导文档入口。 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `source scripts/ao-worktree-env.sh && ./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest --tests 'com.tanuki.Onboarding*'` | 通过 | 32 个引导测试，0 失败；首轮曾因 0.x 版本线判断和取消文案断言失败，修正后重跑通过。 |
| `source scripts/ao-worktree-env.sh && ./gradlew --no-daemon -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime :app:testModernDebugUnitTest :app:lintModernDebug` | 通过（最终重跑 BUILD SUCCESSFUL，7m51s） | 111 个结果文件、1079 tests、0 failures / 0 errors / 0 skipped；lint 0 errors / 108 warnings / 1 hint。 |
| 测试结果统计脚本（解析 `app/build/test-results/testModernDebugUnitTest/TEST-*.xml`） | 通过 | `files=111 tests=1079 failures=0 errors=0 skipped=0`。 |
| `python3 scripts/check-handoff.py --pr --record docs/handoffs/20261111-1049-first-run-onboarding.md` | 通过 | 仅校验本记录；共享状态与印章允许落后。 |

- 哪些是前一轮结果，哪些是本次新验证：前一轮基线为 `main=62e4c30`；本次新增 JVM 回归与完整测试/lint 均为本次新执行。
- 未测试、超时、失败和暂未复现的情况（不能抹掉）：未在设备/模拟器点击四步；未实际下载/解压/apt；未验证通知权限弹窗、升级安装现场、深色/大字体/TalkBack；未执行 instrumentation 测试；未打 APK。

## 未完成与暂停项

- 未完成：设备/模拟器 UI 与真实安装链路验收；独立 Codex High 对当前 SHA 的审查；PR 创建与 CI（尚未提交/推送）。
- 外部阻塞：无。
- 用户暂停：API Key 保存、真实模型/账号验收、真机验收；合并与发布需用户明确授权。

## 下一步

1. 以当前分支提交并创建 PR（base `main`），PR 描述写明四步流程、状态与权限边界、JVM 验证结果和未验证项。
2. 由独立 Codex High 审查 PR 当前完整 SHA；由独立 QA 在测试设备上验证首启、跳过、提示条、环境不满足、真实准备与权限弹窗。
3. 通过审查与适用 QA 后，等待用户明确合并授权；不要自行合并、打 tag 或发布。

## 约束与风险

- 数据/设备/权限边界：不读取/输出真实 Key、Cookie、token、会话或私有日志；未执行 `pm clear`、卸载、wipe AVD 等破坏性操作；设备 QA 由独立 QA 负责，本轮未占用设备。
- 关键决策及理由：状态机与门槛放纯 JVM；解压总量未知时只用不定进度和真实写入量，不造百分比；升级安装通过 `lastUpdateTime`/`firstInstallTime` 判定，避免老用户被整套引导拦住；仅准备动作申请通知权限，存储仍在文件页按需申请，当前没有附近设备能力故不声明权限。
- 失败尝试与避免重复踩坑的结论：`0.1 → 0.2` 在原“主版本号”规则下不会触发提示，已改为 0.x 比较次版本号、1.x 起比较主版本号；取消态文案必须显式包含“复用”，避免测试与界面承诺不一致。

## 产物与校验

- 新/旧 APK、源码 ZIP 及校验文件，哪些确实更新：本轮未生成 APK、源码 ZIP 或发布附件；仅源码、测试和文档改动。
- 是否覆盖安装 / 签名范围 / 未发布事项：未安装、未覆盖、未签名、未发布。
- 提交/推送前清理的过期记录、删除理由和必要结论的保留位置：未删除历史交接；本次没有确认过期且属于本 PR 范围的记录。`docs/AGENT-STATE.md` 与 `CURRENT.json` 未修改。
- 普通 PR 不刷新共享状态/印章；集成维护方在批量合并后统一运行 `--integrate`。文档或脚本变化后重新生成源码 ZIP；不要把源码 ZIP 自身哈希写回归档内形成循环。
