# 交接：安装引导与启动链路诊断

- Agent/会话标识：android_agent-102
- 时间：2026-11-11 16:01 UTC 开始
- 状态：进行中；首轮调查已回报，已接收Astra阶段规格，后端第一批修复已实现待编译/JVM/lint验证
- 前一记录：[当前候选](20261111-2313-rebuild-0.1.14.md)

## 用户目标与范围

改善安装、下载、解包、Node/DSH 准备、健康检查、启动与失败重试的真实体验。先基于 main 3c56167b 独立诊断，回报证据与 UI/后端 owner 分工，再结合 Astra 的参考应用机制规格实施。用户明确允许推翻旧形状、导航和动效规则，颜色方向为 DeepSeek 蓝；本任务不改全局 palette 或聊天组件，不复制参考应用源码、资产或文案。

不占设备；不读取真实凭据，不调查或修改 API Key 保存；不放宽 TLS、哈希或审批，不破坏既有运行环境。不合并、发布或创建 tag。经独立 PR 交付，适用 compile/JVM/lint 与独立审查/QA 分别记录。

## 当前状态

- 已验证：起始 worktree 干净；`git rev-parse HEAD` 与 `git ls-remote origin refs/heads/main` 均为 `3c56167b94efeff47a20bd609b389708ba080ce5`。
- 当前分支：`ao/tanuki-startup/install-startup`；目标包 `com.tanuki.modern.workspacecheck`；未启动安装或设备任务。37/106授权的最终增量compile/定向JVM/lint已退出0（4m26s），96项全过，31项为新增；lint无Error/Fatal，117 Warning/2 Hint；已正式向106释放重Gradle时段，无新重任务。
- 已读取入口、安全、接口、编排、QA、经验、代码规范、交接和安装引导专题。共享状态记录早于实际 main，不作为最新 SHA 依据。
- 首轮摘要已通过 `ao report --checkpoint` 持久化。编排16:09明确授权有充分源码证据的后端问题直接实施；已确认UI owner为android_agent-105，向其发送字段语义。已读取android_agent-103的阶段规格S04/S05，要求真实阶段/单调耗时、避免重复解析、分离本地与公网健康，保留超时/救援/完整性；当前实现与之对齐。研究不是设备验收，QA-03现场根因仍缺Android阶段证据。
- 已实现：移除未采用的镜像锁完整预解析；本地/公网诊断分离；真实启动代次/阶段/耗时与采集失败传播；Application后台恢复及统一屏障；同事务工具检查复用；旧代次的进程登记、端点、HTTP、稳定性/退出与失败清理防护；MainActivity实时准备/完成门控与选定非默认DSH版本保留。
- 验证分层：最终10套件96项全过，含全部新增31项；产品/仪器源码编译及lint通过，最后MainActivity/回调防护已纳入。此前完整148套件/1428项有1个既有聊天测试失败，不能标全套通过，仍待104修正/集成复验。UI由105独立PR交付。下面首轮证据行号基于起始3c56167b，代码修改后以符号名重新定位。

### 首轮证据与根因边界

| 问题 | 源码证据 | 结论 / 安全修复方向 |
|---|---|---|
| 中断恢复可能阻塞首帧 | `TanukiApp.kt:66-72` 在 Application 主线程构造 RuntimeManager；`runtime/RuntimeManager.kt:104-141` 同步恢复/刷新；`runtime/RuntimeComponents.kt:292-330` 恢复会递归清理旧 Ubuntu/Node/DSH | 确定的主线程重磁盘风险。正常无事务启动没有这次递归，不能称每次必卡。异步恢复必须先设恢复屏障，所有启动/安装等待同一屏障，失败保持修复态，不能只把代码丢进 launch |
| 一键准备重复全工具检查 | `RuntimeManager.initialize:288` 调用 ensureUbuntuTools；`RuntimeComponents.ensureNode:409,415` 先查 Node 两库、再调 ensureUbuntuTools；`RuntimeRequirements.kt:13-41` 每次全检执行19项工具和2项库检查 | 确定重复 guest 调用，非 apt 必然重复下载。应在同一安装事务中消除冗余检查，仍保留候选 Node 实启及被更换 Ubuntu 后重检 |
| 无可信锁时先镜像再官方完整解析 | `NpmPackageDownloads.kt:106-138` 依次 resolveFrom(mirror)、resolveFrom(official)，最后始终 return trusted | 镜像候选不用于安装，额外完整解析不能减少官方请求。可直接解析官方并保留全图/入口/SRI 校验；默认预置锁命中时不走此分支。`NpmMetadataPolicy` 当前单请求15s、命令180s，旧专题中的60s/20min及四路下载已过时，后续一并校正文档 |
| 引导把 CHECKING 写成等待开始 | `OnboardingPrepare.kt:83-125` 无 CHECKING 分支；npm 检查解析环境/空间会真实发 CHECKING | 文案与 active 标记错误；`OnboardingScreen.kt:272` 仍由 snapshot.operationActive 兜底展示进度，已排除因此开放重复准备按钮的推测 |
| 阶段指示反复回跳 | `OnboardingScreen.kt:361-391` 按下载/解压/校验/补齐的 ordinal 点亮之前步骤；真实管线每个组件重复这四类操作 | 指示表达了不存在的单向总流程。应按真实组件及子阶段展示，不能把操作类别当整体里程碑；只显示可信阶段百分比 |
| 本地健康依赖公网 | `RuntimeManager.diagnostics:614-638` 的 set -e 脚本最后 curl HEAD npm，max-time30 | 本地运行通过仍可因断网失败。需明确区分本地健康与网络诊断并保留网络错误，不能把它静默忽略 |
| 启动 180 秒缺阶段证据 | `RuntimeManager.start:811-835` 准备工作不在180秒计时内；`849-856` 同一期限同时等待官方行与HTTP；`OperationProgressPresentation.kt:195-212` STARTING 强制通用文案 | 必须分别记录准备/进程创建/官方行/端点发布/HTTP就绪的真实耗时。collector 是独立 scope.async，等待循环不消费其失败；应核查并及时传播其终止，避免晚到超时掩盖原始原因 |
| 首次180秒后重试成功的真实原因未定位 | `20261111-1216-device-qa.md:78,120,220` 明确单次观察且曾做低磁盘/缓存测试；路径缓存历史也没有Android命中率/耗时证据 | 不能断言是 PRoot、缓存或本次UI改动。需要独立QA在保留环境采集冷/热、首次/重试的上述阶段与可用空间；不读真实Key、Cookie/token或私有原始日志 |

### 文件 owner 与共享契约建议

- 本102后端 owner：`runtime/RuntimeManager.kt`、`RuntimeComponents.kt`、`RuntimeRequirements.kt`、`NpmPackageDownloads.kt`、`State.kt`、`OperationProgress.kt`、`RuntimeFailurePolicy.kt`，以及涉及的下载/解包文件和回归。核心状态与恢复门禁只保留一个写入方。
- 安装引导 UI owner（编排已确认android_agent-105）：`onboarding/*`、`ui/OnboardingScreen.kt`、`ui/RuntimeComponents.kt`、`ui/OperationProgress.kt`、`ui/OperationProgressPresentation.kt`，以及最小 MainActivity/TanukiUi 接线；不改全局 palette 或聊天。
- 实施前冻结共享状态：组件身份、稳定阶段 ID、当前操作/启动代次、单调起始/阶段耗时、已知字节/总量、可取消/重试能力及原始失败分类。UI 消费状态，不再靠中文关键词猜测整体里程碑；没有总量/速度就明确未知，不保留固定“几分钟”ETA。
- 已接收参考应用机制阶段规格S04/S05；其来源矩阵仍由研究会话补齐，不复制参考源码/资产/文案。后端可选字段已发105，UI按label/真实状态消费，不按enum ordinal推导总进度。
- 新增 `Snapshot.startup: StartupProgress?`，包含attemptId、stage、startedAtMillis、stageStartedAtMillis、finishedAtMillis、completedStages与outcome；QUEUED/CHECKING/PNPM/WORKSPACE/COMPATIBILITY/TOOLS/PROCESS/OUTPUT/ENDPOINT/HTTP/READY是实际调用阶段，失败/取消冻结。另有独立 `Snapshot.networkDiagnostic`，只表达公网检查结果。
- 主线程恢复依赖已落实：RuntimeInitialization先占mutation，IO scope恢复，释放锁后共享完成信号；安装、提前/手动启动、UpdateManager全部5个入口和直接诊断/base/pnpm等待屏障；失败显示REPAIR/RECOVERY_REQUIRED，停止代次使等待启动失效。直接构造RuntimeManager的仪器fixture默认同步，Application显式选择后台；本地磁盘快照不覆盖期间远程状态。operation=runtime-recovery无可取消承诺，105负责展示。
- MainActivity仅改准备/完成回调与106请求的系统栏dark单行读取（改为当前TanukiDesign.tokens.isDark）；未修改104的onSent单点。INIT对完整元数据中的已安装非默认版本沿用该版本/锁；其他组件可补齐，不因默认版本保护误伤；缺少所选非默认程序时先拒绝并指向修复该版本，不假冒官方SRI、不换默认程序。损坏元数据继续拒绝。
- 保留的既有正确行为：下载完整缓存先于测速命中且重新验哈希；测速有12秒整批上限；续传绑定URL/ETag/Content-Range；npm逐包SHA-512；SafeTar空staging、路径/数量/大小/链接约束；启动和安装重操作本身运行于IO scope；EventLog已异步有界写盘，不能误报仍同步重写日志。
- npm缓存导入 `NpmPackageDownloads.kt:535-560` 串行逐包 cacache 校验写入，随后才 npm ci --offline；明确是本地I/O阶段，设备测量前不猜测并发提速收益。

## 本次改动

| 文件 | 改动与目的 |
|---|---|
| 本记录 | 唯一交接，记录目标/基线/调查/实现与未验边界 |
| runtime/NpmPackageDownloads.kt、NpmDownloadPolicy.kt | 无可信锁时只解析官方元数据，移除必定未采用的镜像候选；保留完整性与图验证 |
| runtime/RuntimeDiagnostics.kt、RuntimeManager.kt、State.kt | 本地检查与公网结果分开；初始化不等公网HEAD，手动检查保留错误和诊断记录 |
| runtime/StartupProgress.kt、RuntimeManager.kt、State.kt | 真实启动阶段单调计时、独立代次、失败/取消冻结，采集任务异常/结束立即传播 |
| RuntimeDiagnosticsTest、StartupProgressTest、RuntimeInitializationTest、RequiredToolsStatusTest、LocalPreparationTest、NpmDownloadPolicyTest | 31项新增合成回归与官方源策略，覆盖本地/网络、计时、恢复屏障、工具复用、完整准备与选定版本保留 |
| runtime/RuntimeInitialization.kt、TanukiApp.kt、UpdateManager.kt | 主线程恢复改为持锁后台执行，各环境入口等待同一屏障，测试默认同步兼容 |
| runtime/RuntimeComponents.kt、State.kt、RuntimeManager.kt | 同一独占初始化事务复用刚完成且Ubuntu标记匹配的工具检查，不跳过Node候选实启 |
| runtime/LocalPreparation.kt、MainActivity.kt | 准备/完成点击时重判全链状态；完整现有环境直接启动，不覆盖非默认版本；保留远程免本地准备路径；按106请求接实际深浅主题状态 |
| docs/INTERFACES.md、COMPONENTS.md | 先固定健康与启动观测契约，同步元数据超时/并发的实际实现 |

## 验证记录

| 实际命令 / 操作 | 环境与结果 | 证据与限制 |
|---|---|---|
| `git status --short --branch`、`git rev-parse HEAD`、`git ls-remote origin refs/heads/main` | Host 通过，起始工作区干净 | SHA 如上，不代表产品验证 |
| `git switch -c ao/tanuki-startup/install-startup` 及状态/HEAD 核对 | 首次沙箱拒绝共享 refs 锁；同命令一次性授权重试成功 | HEAD 未变；未修改共享 Git 配置 |
| 源码静态调查 | 首轮完成，仍需Android证据 | 首轮调查时未执行compile/JVM/lint；随后Host结果见下，不继承历史测试；Android仪器/真实安装启动未执行 |
| `node scripts/test-dsh-path-cache.mjs` | Host 通过，10场景 | 合成fixture，不证明Android/PRoot命中率或启动性能 |
| `python3 scripts/test-dsh-lock.py` | Host 通过，退出0 | 模拟linux/arm64/glibc的Node24.22.2/26.13.0/22.23.4各560归档、lock_unchanged=true；686项官方HTTPS/SHA-512与3负向场景；不下载、不操作设备 |
| `source scripts/ao-worktree-env.sh` 后运行Gradle compileModernDebugKotlin、compileModernDebugAndroidTestKotlin、testModernDebugUnitTest，过滤StartupProgressTest/RuntimeDiagnosticsTest/NpmDownloadPolicyTest/NpmDependencyLocksTest/NpmLockTrustTest/CommandDeadlineTest/RuntimeFailurePolicyTest | 编译通过；79项/2失败，退出1 | `--console=plain --max-workers=2 -PtestBuildType=debug -I scripts/workspace-verification.init.gradle -x buildNativeRuntime`；缓存准备366秒，Gradle16m48s；2失败为异常引用assertSame，协程栈恢复复制对象，已改验证类型/消息/原cause链，后续8项通过，不改产品语义 |
| 同参数串行完整compile/仪器源码compile/JVM/lint | compile通过；JVM1428项/148XML、1failure/0errors/0skipped，退出1（3m53s）；lint未执行 | 唯一失败未改动的DshComposerControllerTest.onSentReceivesSessionId，expected s1/actual null，测试等notice后直接检查回调变量存在竞态；证据已交104/106，不能标全套通过。Startup8、Diagnostics6、Initialization4、Tools2共20项全过 |
| 最终增量验证（--tests紧随JVM任务，lint置后） | 退出0（4m26s） | 产品/仪器Kotlin compile通过；10XML/96 tests/0failure/0error/0skip，含全部新增31项；lint117 Warning/2 Hint、无Error/Fatal；包含最终MainActivity/LocalPreparation11项和旧代次回调防护。时段已释放给106，未再重跑全套；独立review/AndroidQA仍未执行 |
| 增量第一次命令参数错误 | 配置阶段退出1（10s） | --tests放在lint后被当作lint参数，产品任务未执行；已更正同一时段重跑，不能算产品失败或通过 |
| `python3 scripts/package-source.py` + `python3 scripts/check-publication.py --git --archive artifacts/sources/Tanuki-0.1.7-source.zip` | 862公开文件，0 findings | 仅按脚本默认名生成内部源码检查候选，不是版本决策/发布附件；无原生二进制分发，native对应源码附件absent；文档定稿后再次刷新，实际SHA在外部SOURCE-SHA256SUMS，不在自身源码内自引用 |
| 工作区资源检查 | 仅部分完成 | 本worktree Gradle缓存约934MB；当时sources/native未创建导致du退出1；单独ps被沙箱拒绝，不绕过；不作为构建失败或性能结论 |
| 普通交接检查与 `git diff --check` | 当前已通过 | 只证明记录结构与diff格式，不证明产品行为 |

## 未完成与暂停项

- 后端Host候选完成：最终compile/96项定向JVM/lint、公开候选/交接检查通过，全部新增31项已过。独立PR以本记录同源提交交付，URL/SHA在PR元数据及本会话最终报告，不为追填自哈希另拆推送。
- 集成前仍需：104处理既有onSent聊天测试竞态并集成复验；当前PR完整SHA的独立审查和适用AndroidQA。不能因本地定向通过宣称完整产品/QA-03已验。
- 用户暂停：API Key 保存、真实账号/模型与真机；本任务明确不操作模拟器。
- 外部依赖：Astra阶段规格已收到；Android研究仍进行中，180秒现场原因需独立QA脱敏阶段证据。当前可继续Host实现验证，不以该依赖阻塞所有工作。

## 下一步

1. 104根因修复onSent回调测试竞态后，由106在集成版本复验完整JVM；本分支保留1428/1失败事实，不以重跑碰运气抹除。
2. 编排对独立PR当前完整SHA安排独立审查/适用AndroidQA，核对真实首次/重试各阶段、停止/新启动与晚到回调；不把Host结果当现场根因证明，不自行合并发布。
3. 后端满足门禁并由编排集成后，105适配已冻结的Snapshot.startup/networkDiagnostic与runtime-recovery不可取消展示；保留105/101的TEST-REPORT小节、104 MainActivity.onSent独立变化及106主题接线。

## 约束与风险

- 只调查公开源码和合成数据，不读取 App 私有目录或原始真实运行日志。
- 180 秒超时是结果，不能靠延长时限或隐藏错误宣称根因已解决；Host 结果不等于 Android 行为。
- 恢复与状态门禁不能未经调查直接异步化，避免安装、启动与恢复并发修改环境。

## 产物与校验

- 仅生成被Git忽略的内部源码检查ZIP与外部SHA清单，不作为用户交付/发布附件；未生成APK、未覆盖安装、未合并发布。源码以独立PR统一单次commit/push交付，author/committer使用指定leezzou身份；生成PR后由AO最终报告提供URL和完整SHA。
- 提交前已复核相关交接：20261111-1216-device-qa仍有QA-03首次180秒后重试成功的独有未完成证据，必须保留；其余motion/visual/rebuild记录属于其他PR的验证与集成范围，不做跨PR清理。本次没有可确认过期且属于本范围的记录，未删除记录；必要新结论已归入COMPONENTS/INTERFACES/TEST-REPORT及本记录。
- 普通 PR 不修改 AGENT-STATE.md 或 CURRENT.json，不刷新共享印章。
